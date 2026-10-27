package com.dynorixz.smarthome.complication

import android.app.Application
import androidx.wear.watchface.complications.data.ComplicationType
import com.dynorixz.smarthome.data.local.FavoriteKind
import com.dynorixz.smarthome.data.local.FavoriteRef
import com.dynorixz.smarthome.data.local.UserPreferences
import com.dynorixz.smarthome.domain.*
import com.dynorixz.smarthome.tile.QuickActionViewModel
import android.content.Intent
import androidx.lifecycle.SavedStateHandle
import com.dynorixz.smarthome.tile.TileActionActivity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.*
import kotlinx.serialization.json.JsonPrimitive
import kotlin.test.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class ShortcutTest {
    private val deviceRef = FavoriteRef(FavoriteKind.DEVICE, "lamp")

    @Test fun eachSlotRetainsItsOwnActionAndLegacySelectionIsOnlyFallback() = runTest {
        val preferences = UserPreferences(RuntimeEnvironment.getApplication())
        preferences.setComplicationFavorite(deviceRef)
        val lamp = ComplicationChoice(deviceRef, ShortcutAction.ON)
        val scene = ComplicationChoice.default(FavoriteRef(FavoriteKind.SCENARIO, "evening"))
        preferences.setComplicationChoice(1201, lamp)
        preferences.setComplicationChoice(1202, scene)
        preferences.setComplicationFavorite(FavoriteRef(FavoriteKind.DEVICE, "other"))
        assertEquals(lamp, preferences.complicationChoice(1201).first())
        assertEquals(scene, preferences.complicationChoice(1202).first())
        assertEquals("other", preferences.complicationChoice(1203).first()?.ref?.id)
    }

    @Test fun toggleReadsFreshPowerInsteadOfCachedHome() = runTest {
        val repo = FakeRepository()
        ShortcutExecutor(repo).execute(ComplicationChoice(deviceRef, ShortcutAction.TOGGLE))
        assertEquals(listOf("refresh-device", "device-action"), repo.events)
        assertEquals(JsonPrimitive(false), repo.lastAction?.value)
    }

    @Test fun explicitOnNeverBecomesToggleAndWorksWithUnknownState() = runTest {
        val repo = FakeRepository().apply { freshValue = null }
        val executor = ShortcutExecutor(repo)
        assertFailsWith<AppException> { executor.execute(ComplicationChoice(deviceRef, ShortcutAction.TOGGLE)) }
        assertNull(repo.lastAction)
        executor.execute(ComplicationChoice(deviceRef, ShortcutAction.ON))
        assertEquals(JsonPrimitive(true), repo.lastAction?.value)
        executor.execute(ComplicationChoice(deviceRef, ShortcutAction.OFF))
        assertEquals(JsonPrimitive(false), repo.lastAction?.value)
    }

    @Test fun offlineDeviceSendsNoCommand() = runTest {
        val repo = FakeRepository().apply { reachable = false }
        assertFailsWith<AppException> { ShortcutExecutor(repo).execute(ComplicationChoice.default(deviceRef)) }
        assertNull(repo.lastAction)
    }

    @Test fun scenarioExecutesWithoutPowerCommand() = runTest {
        val repo = FakeRepository()
        ShortcutExecutor(repo).execute(ComplicationChoice.default(FavoriteRef(FavoriteKind.SCENARIO, "evening")))
        assertEquals(listOf("scenario:evening"), repo.events)
        assertNull(repo.lastAction)
    }

    @Test fun groupAlsoUsesFreshPower() = runTest {
        val repo = FakeRepository()
        ShortcutExecutor(repo).execute(ComplicationChoice.default(FavoriteRef(FavoriteKind.GROUP, "lights")))
        assertEquals(listOf("refresh-group", "group-action"), repo.events)
        assertEquals(JsonPrimitive(false), repo.lastAction?.value)
    }

    @Test fun restoredActionScreenDoesNotResendCommand() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        try {
            val repo = FakeRepository()
            val model = QuickActionViewModel(repo, UserPreferences(RuntimeEnvironment.getApplication()), ShortcutExecutor(repo), SavedStateHandle(mapOf("started" to true)))
            model.start(Intent().putExtra(TileActionActivity.EXTRA_KIND, "SCENARIO").putExtra(TileActionActivity.EXTRA_ID, "evening"))
            advanceUntilIdle()
            assertTrue(model.state.value.finished)
            assertTrue(repo.events.isEmpty())
        } finally { Dispatchers.resetMain() }
    }

    @Test fun repeatedLaunchDoesNotSendTwoScenarioCommands() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        try {
            val repo = FakeRepository()
            val preferences = UserPreferences(RuntimeEnvironment.getApplication())
            val choice = ComplicationChoice.default(FavoriteRef(FavoriteKind.SCENARIO, "evening"))
            preferences.setComplicationChoice(888, choice)
            val model = QuickActionViewModel(repo, preferences, ShortcutExecutor(repo), SavedStateHandle())
            val intent = Intent().putExtra(TileActionActivity.EXTRA_KIND, "SCENARIO")
                .putExtra(TileActionActivity.EXTRA_ID, "evening")
                .putExtra(TileActionActivity.EXTRA_SOURCE, TileActionActivity.SOURCE_COMPLICATION)
                .putExtra(TileActionActivity.EXTRA_COMPLICATION_ID, 888)
            model.start(intent)
            model.start(intent)
            assertTrue(model.state.first { it.finished }.succeeded)
            assertEquals(listOf("scenario:evening"), repo.events)
        } finally { Dispatchers.resetMain() }
    }

    @Test fun tapIntentsStayIndependentForTwoSlotsWithTheSameDevice() {
        val service = Robolectric.buildService(SmartHomeComplicationService::class.java).create().get()
        val presentation = ComplicationPresentation("Вкл", "Лампа", "Лампа")
        val first = service.buildData(ComplicationType.SHORT_TEXT, presentation, ComplicationChoice(deviceRef, ShortcutAction.ON), 51).tapAction
        val second = service.buildData(ComplicationType.SHORT_TEXT, presentation, ComplicationChoice(deviceRef, ShortcutAction.OFF), 52).tapAction
        assertNotNull(first)
        assertNotNull(second)
        assertNotEquals(first, second)
        assertEquals(51, org.robolectric.Shadows.shadowOf(first).savedIntent.getIntExtra(TileActionActivity.EXTRA_COMPLICATION_ID, -1))
        assertEquals(52, org.robolectric.Shadows.shadowOf(second).savedIntent.getIntExtra(TileActionActivity.EXTRA_COMPLICATION_ID, -1))
    }

    @Test fun smallCircularSlotsHaveValidPreviews() {
        val service = Robolectric.buildService(SmartHomeComplicationService::class.java).create().get()
        listOf(ComplicationType.SHORT_TEXT, ComplicationType.SMALL_IMAGE, ComplicationType.MONOCHROMATIC_IMAGE, ComplicationType.LONG_TEXT, ComplicationType.RANGED_VALUE).forEach { type ->
            assertEquals(type, service.getPreviewData(type)?.type)
        }
    }

    @Test fun invalidSavedActionCannotBecomeScenarioOrPowerCommand() {
        assertNull(ComplicationChoice.decode("ON|SCENARIO|evening"))
        assertNull(ComplicationChoice.decode("SCENARIO|DEVICE|lamp"))
        assertNull(ComplicationChoice.decode("UNKNOWN|DEVICE|lamp"))
    }
}

private class FakeRepository : SmartHomeRepository {
    var freshValue: Boolean? = true
    var reachable = true
    var lastAction: CapabilityAction? = null
    val events = mutableListOf<String>()
    private fun power(value: Boolean?) = Capability.OnOff("on", true, true, value?.let(::JsonPrimitive), null)
    private fun device(value: Boolean?) = Device("lamp", "Лампа", "devices.types.light", null, "home", emptyList(), listOf(power(value)), emptyList(), reachable)
    private val home get() = SmartHome(emptyList(), emptyList(), listOf(device(false)), listOf(Scenario("evening", "Вечер")), emptyList(), null, 0, false)
    override fun observeHome() = flowOf(home)
    override suspend fun refreshHome() = home
    override suspend fun refreshDevice(deviceId: String): Device { events += "refresh-device"; return device(freshValue) }
    override suspend fun refreshGroup(groupId: String): DeviceGroup { events += "refresh-group"; return DeviceGroup(groupId, "Свет", "light", "home", emptyList(), listOf(power(freshValue))) }
    override suspend fun executeDeviceAction(deviceId: String, action: CapabilityAction): ActionOutcome { events += "device-action"; lastAction = action; return success() }
    override suspend fun executeGroupAction(groupId: String, action: CapabilityAction): ActionOutcome { events += "group-action"; lastAction = action; return success() }
    override suspend fun executeScenario(scenarioId: String): ActionOutcome { events += "scenario:$scenarioId"; return success() }
    override suspend fun clearCache() {}
    private fun success() = ActionOutcome(null, true, emptyList())
}
