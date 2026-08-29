package com.dynorixz.smarthome.data.repository

import com.dynorixz.smarthome.data.local.SmartHomeCacheDao
import com.dynorixz.smarthome.data.local.SmartHomeCacheEntity
import com.dynorixz.smarthome.data.local.SessionInvalidator
import com.dynorixz.smarthome.data.mapper.SmartHomeMapper
import com.dynorixz.smarthome.data.network.ApiErrorMapper
import com.dynorixz.smarthome.data.network.YandexSmartHomeApi
import com.dynorixz.smarthome.domain.CapabilityAction
import com.dynorixz.smarthome.domain.AppError
import com.dynorixz.smarthome.domain.AppException
import kotlin.test.assertFailsWith
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import org.junit.After
import org.junit.Before
import org.junit.Test
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory

class SmartHomeRepositoryTest {
    private lateinit var server: MockWebServer
    private val json = Json { ignoreUnknownKeys = true; explicitNulls = false; encodeDefaults = true }

    @Before fun start() { server = MockWebServer(); server.start() }
    @After fun stop() { server.shutdown() }

    @Test
    fun `loads real response and posts documented device action body`() = runTest {
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse = when {
                request.path == "/v1.0/user/info" -> ok(USER_INFO)
                request.path == "/v1.0/devices/d1" -> ok(DEVICE_INFO)
                request.path == "/v1.0/devices/actions" -> ok(ACTION_RESULT)
                else -> MockResponse().setResponseCode(404)
            }
        }
        val api = Retrofit.Builder()
            .baseUrl(server.url("/"))
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build().create(YandexSmartHomeApi::class.java)
        val dao = FakeDao()
        val repository = SmartHomeRepositoryImpl(
            api, dao, SmartHomeMapper(), json, ApiErrorMapper(json), object : SessionInvalidator {
                override suspend fun invalidate() = Unit
            },
        )

        assertEquals("Лампа", repository.refreshHome().devices.single().name)
        val outcome = repository.executeDeviceAction(
            "d1",
            CapabilityAction("devices.capabilities.on_off", "on", JsonPrimitive(true)),
        )
        assertTrue(outcome.successful)

        val requests = generateSequence { server.takeRequest() }.take(3).toList()
        val action = requests.first { it.path == "/v1.0/devices/actions" }
        val body = action.body.readUtf8()
        assertTrue(body.contains("\"id\":\"d1\""))
        assertTrue(body.contains("\"instance\":\"on\""))
        assertTrue(body.contains("\"value\":true"))
    }

    @Test
    fun `scenario uses documented endpoint`() = runTest {
        server.enqueue(ok("""{"status":"ok","request_id":"scenario-1"}"""))
        val repository = repository(FakeDao())

        val result = repository.executeScenario("good-night")

        assertTrue(result.successful)
        assertEquals("scenario-1", result.requestId)
        val request = server.takeRequest()
        assertEquals("POST", request.method)
        assertEquals("/v1.0/scenarios/good-night/actions", request.path)
    }

    @Test
    fun `unauthorized response invalidates session and cache`() = runTest {
        server.enqueue(
            MockResponse().setResponseCode(401)
                .setHeader("Content-Type", "application/json")
                .setBody("""{"status":"error","request_id":"auth-1"}"""),
        )
        val dao = FakeDao().apply {
            put(SmartHomeCacheEntity(json = USER_INFO, updatedAtMillis = 1L))
        }
        var invalidated = false
        val repository = repository(dao) { invalidated = true }

        val failure = assertFailsWith<AppException> { repository.refreshHome() }

        assertTrue(failure.error is AppError.Unauthorized)
        assertTrue(invalidated)
        assertEquals(null, dao.get())
    }

    @Test
    fun `device offline action becomes typed error`() = runTest {
        server.enqueue(ok(OFFLINE_ACTION_RESULT))
        val repository = repository(FakeDao())

        val failure = assertFailsWith<AppException> {
            repository.executeDeviceAction(
                "d1",
                CapabilityAction("devices.capabilities.on_off", "on", JsonPrimitive(true)),
            )
        }

        assertTrue(failure.error is AppError.DeviceOffline)
    }

    private fun repository(
        dao: FakeDao,
        invalidate: suspend () -> Unit = {},
    ): SmartHomeRepositoryImpl {
        val api = Retrofit.Builder()
            .baseUrl(server.url("/"))
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build().create(YandexSmartHomeApi::class.java)
        return SmartHomeRepositoryImpl(
            api, dao, SmartHomeMapper(), json, ApiErrorMapper(json), object : SessionInvalidator {
                override suspend fun invalidate() = invalidate()
            },
        )
    }

    private fun ok(body: String) = MockResponse().setResponseCode(200)
        .setHeader("Content-Type", "application/json").setBody(body)

    private class FakeDao : SmartHomeCacheDao {
        private val state = MutableStateFlow<SmartHomeCacheEntity?>(null)
        override fun observe(): Flow<SmartHomeCacheEntity?> = state
        override suspend fun get(): SmartHomeCacheEntity? = state.value
        override suspend fun put(entity: SmartHomeCacheEntity) { state.value = entity }
        override suspend fun clear() { state.value = null }
    }

    private companion object {
        const val USER_INFO = """{"status":"ok","request_id":"r1","rooms":[],"groups":[],"scenarios":[],"households":[],"devices":[{"id":"d1","name":"Лампа","type":"devices.types.light","capabilities":[{"type":"devices.capabilities.on_off","retrievable":true,"state":{"instance":"on","value":false}}],"properties":[]}]}"""
        const val DEVICE_INFO = """{"status":"ok","request_id":"r3","id":"d1","name":"Лампа","type":"devices.types.light","capabilities":[{"type":"devices.capabilities.on_off","retrievable":true,"state":{"instance":"on","value":true}}],"properties":[]}"""
        const val ACTION_RESULT = """{"status":"ok","request_id":"r2","devices":[{"id":"d1","capabilities":[{"type":"devices.capabilities.on_off","state":{"instance":"on","action_result":{"status":"DONE"}}}]}]}"""
        const val OFFLINE_ACTION_RESULT = """{"status":"ok","request_id":"r4","devices":[{"id":"d1","capabilities":[{"type":"devices.capabilities.on_off","state":{"instance":"on","action_result":{"status":"ERROR","error_code":"DEVICE_UNREACHABLE","error_message":"offline"}}}]}]}"""
    }
}
