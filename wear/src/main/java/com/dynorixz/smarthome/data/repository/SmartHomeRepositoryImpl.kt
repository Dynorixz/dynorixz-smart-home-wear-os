package com.dynorixz.smarthome.data.repository

import android.util.Log
import com.dynorixz.smarthome.data.local.SmartHomeCacheDao
import com.dynorixz.smarthome.data.local.SmartHomeCacheEntity
import com.dynorixz.smarthome.data.local.SessionInvalidator
import com.dynorixz.smarthome.data.mapper.SmartHomeMapper
import com.dynorixz.smarthome.data.network.ActionResponse
import com.dynorixz.smarthome.data.network.ApiErrorMapper
import com.dynorixz.smarthome.data.network.CapabilityActionDto
import com.dynorixz.smarthome.data.network.CapabilityStateDto
import com.dynorixz.smarthome.data.network.DeviceActionsDto
import com.dynorixz.smarthome.data.network.DeviceActionsRequest
import com.dynorixz.smarthome.data.network.DeviceDto
import com.dynorixz.smarthome.data.network.GroupActionsRequest
import com.dynorixz.smarthome.data.network.UserInfoResponse
import com.dynorixz.smarthome.data.network.YandexSmartHomeApi
import com.dynorixz.smarthome.data.network.toUserInfoResponse
import com.dynorixz.smarthome.domain.ActionFailure
import com.dynorixz.smarthome.domain.ActionOutcome
import com.dynorixz.smarthome.domain.AppError
import com.dynorixz.smarthome.domain.AppException
import com.dynorixz.smarthome.domain.CapabilityAction
import com.dynorixz.smarthome.domain.Device
import com.dynorixz.smarthome.domain.DeviceGroup
import com.dynorixz.smarthome.domain.SmartHome
import com.dynorixz.smarthome.domain.SmartHomeRepository
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.delay
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

@Singleton
class SmartHomeRepositoryImpl @Inject constructor(
    private val api: YandexSmartHomeApi,
    private val cacheDao: SmartHomeCacheDao,
    private val mapper: SmartHomeMapper,
    private val json: Json,
    private val errorMapper: ApiErrorMapper,
    private val sessionInvalidator: SessionInvalidator,
) : SmartHomeRepository {
    private val cacheMutex = Mutex()
    private val freshThisProcess = MutableStateFlow(false)

    override fun observeHome(): Flow<SmartHome?> = combine(cacheDao.observe(), freshThisProcess) { cached, fresh ->
        cached?.let {
            runCatching {
                mapper.toDomain(
                    json.decodeFromString<UserInfoResponse>(it.json),
                    it.updatedAtMillis,
                    stale = !fresh,
                )
            }.getOrNull()
        }
    }

    override suspend fun refreshHome(): SmartHome = apiCall {
        val response = api.getUserInfo().toUserInfoResponse()
        require(response.status == "ok")
        val now = System.currentTimeMillis()
        cacheDao.put(SmartHomeCacheEntity(json = json.encodeToString(response), updatedAtMillis = now))
        freshThisProcess.value = true
        logRequestId("user/info", response.requestId)
        mapper.toDomain(response, now, stale = false)
    }

    override suspend fun refreshDevice(deviceId: String): Device = apiCall {
        cacheMutex.withLock {
            val cached = cachedResponse() ?: throw AppException(AppError.InvalidResponse())
            val oldDto = cached.devices.firstOrNull { it.id == deviceId }
                ?: throw AppException(AppError.InvalidResponse(cached.requestId))
            val old = mapper.device(oldDto)
            val response = api.getDevice(deviceId)
            require(response.status == "ok")
            val refreshed = mapper.refreshedDevice(response, old)
            val replacement = oldDto.copy(
                name = response.name.ifBlank { oldDto.name },
                type = response.type.ifBlank { oldDto.type },
                householdId = response.householdId.ifBlank { oldDto.householdId },
                room = response.room ?: oldDto.room,
                groups = response.groups.ifEmpty { oldDto.groups },
                capabilities = response.capabilities,
                properties = response.properties,
                errorCode = response.errorCode,
            )
            val updated = cached.copy(devices = cached.devices.map { if (it.id == deviceId) replacement else it })
            cacheDao.put(SmartHomeCacheEntity(json = json.encodeToString(updated), updatedAtMillis = System.currentTimeMillis()))
            freshThisProcess.value = true
            logRequestId("devices/$deviceId", response.requestId)
            refreshed
        }
    }

    override suspend fun refreshGroup(groupId: String): DeviceGroup = apiCall {
        cacheMutex.withLock {
            val cached = cachedResponse() ?: throw AppException(AppError.InvalidResponse())
            val oldDto = cached.groups.firstOrNull { it.id == groupId }
                ?: throw AppException(AppError.InvalidResponse(cached.requestId))
            val response = api.getGroup(groupId)
            require(response.status == "ok")
            val replacement = oldDto.copy(
                name = response.name.ifBlank { oldDto.name },
                type = response.type.ifBlank { oldDto.type },
                householdId = response.householdId.ifBlank { oldDto.householdId },
                devices = response.devices.map { it.id }.ifEmpty { oldDto.devices },
                capabilities = response.capabilities,
            )
            val updated = cached.copy(groups = cached.groups.map { if (it.id == groupId) replacement else it })
            cacheDao.put(
                SmartHomeCacheEntity(
                    json = json.encodeToString(updated),
                    updatedAtMillis = System.currentTimeMillis(),
                ),
            )
            freshThisProcess.value = true
            logRequestId("groups/$groupId", response.requestId)
            mapper.group(replacement)
        }
    }

    override suspend fun executeDeviceAction(deviceId: String, action: CapabilityAction): ActionOutcome = apiCall {
        val response = api.executeDeviceActions(
            DeviceActionsRequest(listOf(DeviceActionsDto(deviceId, listOf(action.toDto())))),
        )
        val outcome = response.toOutcome()
        ensureSuccess(outcome)
        logRequestId("devices/actions", outcome.requestId)
        delay(RECONCILIATION_DELAY_MS)
        runCatching { refreshDevice(deviceId) }
            .onFailure { Log.w(TAG, "State reconciliation failed for $deviceId: ${it::class.simpleName}") }
        outcome
    }

    override suspend fun executeGroupAction(groupId: String, action: CapabilityAction): ActionOutcome = apiCall {
        val response = api.executeGroupActions(groupId, GroupActionsRequest(listOf(action.toDto())))
        val outcome = response.toOutcome()
        ensureSuccess(outcome)
        logRequestId("groups/$groupId/actions", outcome.requestId)
        delay(RECONCILIATION_DELAY_MS)
        runCatching { refreshGroup(groupId) }
            .onFailure { Log.w(TAG, "Group reconciliation failed for $groupId: ${it::class.simpleName}") }
        outcome
    }

    override suspend fun executeScenario(scenarioId: String): ActionOutcome = apiCall {
        val response = api.executeScenario(scenarioId)
        logRequestId("scenarios/$scenarioId/actions", response.requestId)
        if (response.status != "ok") throw AppException(AppError.InvalidResponse(response.requestId))
        ActionOutcome(response.requestId, successful = true, errors = emptyList())
    }

    override suspend fun clearCache() {
        cacheDao.clear()
        freshThisProcess.value = false
    }

    private suspend fun cachedResponse(): UserInfoResponse? = cacheDao.get()?.let {
        runCatching { json.decodeFromString<UserInfoResponse>(it.json) }.getOrNull()
    }

    private suspend fun <T> apiCall(block: suspend () -> T): T = try {
        block()
    } catch (error: Throwable) {
        if (error is CancellationException) throw error
        val mapped = errorMapper.map(error)
        if (mapped.error is AppError.Unauthorized) {
            sessionInvalidator.invalidate()
            cacheDao.clear()
            freshThisProcess.value = false
        }
        throw mapped
    }

    private fun CapabilityAction.toDto() = CapabilityActionDto(type, CapabilityStateDto(instance, value))

    private fun ActionResponse.toOutcome(): ActionOutcome {
        val failures = devices.flatMap { device ->
            device.capabilities.mapNotNull { capability ->
                capability.state.actionResult.takeIf { it.status != "DONE" }?.let {
                    ActionFailure(it.errorCode, it.errorMessage)
                }
            }
        }
        return ActionOutcome(requestId, status == "ok" && failures.isEmpty(), failures)
    }

    private fun ensureSuccess(outcome: ActionOutcome) {
        if (outcome.successful) return
        val offline = outcome.errors.any { it.code in OFFLINE_CODES }
        throw AppException(
            if (offline) AppError.DeviceOffline(outcome.requestId)
            else AppError.InvalidResponse(outcome.requestId),
        )
    }

    private fun logRequestId(endpoint: String, requestId: String?) {
        Log.i(TAG, "$endpoint request_id=${requestId ?: "missing"}")
    }

    private companion object {
        const val TAG = "DynorixzRepository"
        const val RECONCILIATION_DELAY_MS = 400L
        val OFFLINE_CODES = setOf("DEVICE_UNREACHABLE", "DEVICE_OFFLINE", "TIMEOUT")
    }
}
