package com.dynorixz.smarthome.domain

import android.net.Uri
import com.dynorixz.smarthome.data.local.TokenState
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

interface SmartHomeRepository {
    fun observeHome(): Flow<SmartHome?>
    suspend fun refreshHome(): SmartHome
    suspend fun refreshDevice(deviceId: String): Device
    suspend fun refreshGroup(groupId: String): DeviceGroup
    suspend fun executeDeviceAction(deviceId: String, action: CapabilityAction): ActionOutcome
    suspend fun executeGroupAction(groupId: String, action: CapabilityAction): ActionOutcome
    suspend fun executeScenario(scenarioId: String): ActionOutcome
    suspend fun clearCache()
}

data class OAuthLaunchRequest(val uri: Uri)

interface AuthRepository {
    val tokenState: StateFlow<TokenState>
    suspend fun beginLogin(): OAuthLaunchRequest
    suspend fun handleCallback(uri: Uri)
    suspend fun logout()
}
