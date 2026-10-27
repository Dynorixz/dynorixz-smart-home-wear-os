package com.dynorixz.smarthome.ui.device

import android.app.Application
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModelStore
import com.dynorixz.smarthome.domain.*
import com.dynorixz.smarthome.ui.WearSurfaceUpdater
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.test.*
import kotlinx.serialization.json.JsonPrimitive
import kotlin.test.*
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class DeviceRefreshTest {
    private val store = ViewModelStore()
    @Before fun setup() { Dispatchers.setMain(StandardTestDispatcher()) }
    @After fun teardown() { store.clear(); Dispatchers.resetMain() }

    @Test fun freshSensorUpdateRemovesAnOlderRefreshWarning() = runTest {
        val repo = SensorRepository()
        val vm = model(repo)
        runCurrent()
        assertNotNull((vm.state.value as DeviceUiState.Content).error)
        repo.home.value = sensorHome(25.1, stale = false)
        runCurrent()
        val content = vm.state.value as DeviceUiState.Content
        assertNull(content.error)
        assertEquals(25.1, (content.device.properties.single() as DeviceProperty.FloatValue).value)
    }
    @Test fun unchangedCacheDoesNotEraseARealFailure() = runTest {
        val repo = SensorRepository()
        val vm = model(repo)
        runCurrent()
        repo.home.value = sensorHome(25.0, stale = false).copy(updatedAtMillis = 200)
        runCurrent()
        assertNotNull((vm.state.value as DeviceUiState.Content).error)
    }
    @Test fun staleDataDoesNotEraseTheRefreshWarning() = runTest {
        val repo = SensorRepository()
        val vm = model(repo)
        runCurrent()
        repo.home.value = sensorHome(25.1, stale = true)
        runCurrent()
        assertNotNull((vm.state.value as DeviceUiState.Content).error)
    }
    @Test fun aCommandFailureIsNotClearedBySensorUpdates() = runTest {
        val repo = SensorRepository().apply { failRefresh = false }
        val vm = model(repo)
        runCurrent()
        vm.execute(CapabilityAction(Capability.ON_OFF, "on", JsonPrimitive(true)))
        runCurrent()
        assertNotNull((vm.state.value as DeviceUiState.Content).error)
        repo.home.value = sensorHome(25.1, stale = false)
        runCurrent()
        assertNotNull((vm.state.value as DeviceUiState.Content).error)
    }
    @Test fun aLateFailedRequestDoesNotReplaceNewerLiveReadingsWithAnError() = runTest {
        val gate = CompletableDeferred<Device>()
        val repo = SensorRepository().apply { refreshGate = gate }
        val vm = model(repo)
        runCurrent()
        repo.home.value = sensorHome(25.1, stale = false)
        runCurrent()
        gate.completeExceptionally(AppException(AppError.InvalidResponse()))
        runCurrent()
        assertNull((vm.state.value as DeviceUiState.Content).error)
    }
    @Test fun successfulRetryClearsTheWarningEvenWhenTheValueIsUnchanged() = runTest {
        val repo = SensorRepository()
        val vm = model(repo)
        runCurrent()
        assertNotNull((vm.state.value as DeviceUiState.Content).error)
        repo.failRefresh = false
        vm.refresh()
        runCurrent()
        assertNull((vm.state.value as DeviceUiState.Content).error)
    }

    private fun model(repo: SensorRepository) = DeviceViewModel(SavedStateHandle(mapOf("deviceId" to "sensor")), repo,
        WearSurfaceUpdater(RuntimeEnvironment.getApplication())).also { store.put("device", it) }
    private class SensorRepository : SmartHomeRepository {
        val home = MutableStateFlow<SmartHome?>(sensorHome(25.0, true))
        var failRefresh = true
        var refreshGate: CompletableDeferred<Device>? = null
        override fun observeHome(): Flow<SmartHome?> = home
        override suspend fun refreshHome() = home.value!!
        override suspend fun refreshDevice(deviceId: String): Device = refreshGate?.await()
            ?: if (failRefresh) throw AppException(AppError.InvalidResponse()) else home.value!!.devices.single()
        override suspend fun executeDeviceAction(deviceId: String, action: CapabilityAction): ActionOutcome = throw AppException(AppError.DeviceOffline())
        override suspend fun refreshGroup(groupId: String): DeviceGroup = error("unused")
        override suspend fun executeGroupAction(groupId: String, action: CapabilityAction): ActionOutcome = error("unused")
        override suspend fun executeScenario(scenarioId: String): ActionOutcome = error("unused")
        override suspend fun clearCache() = Unit
    }
}

private fun sensorHome(temperature: Double, stale: Boolean): SmartHome = SmartHome(emptyList(), emptyList(),
    listOf(sensorFixture("Климат", listOf(DeviceProperty.FloatValue("temperature", "unit.temperature.celsius", temperature, true, true, null)))
        .copy(capabilities = listOf(Capability.OnOff("on", true, true, JsonPrimitive(false), null)))), emptyList(), emptyList(), null, 100, stale)
