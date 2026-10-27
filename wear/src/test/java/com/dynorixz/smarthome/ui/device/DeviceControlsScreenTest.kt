package com.dynorixz.smarthome.ui.device

import android.app.Application
import android.graphics.Bitmap
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.*
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.unit.dp
import androidx.wear.compose.foundation.lazy.rememberScalingLazyListState
import com.dynorixz.smarthome.data.local.ThemePreference
import com.dynorixz.smarthome.domain.*
import com.dynorixz.smarthome.ui.DynorixzTheme
import com.dynorixz.smarthome.ui.home.HomeUiState
import java.io.File
import kotlin.test.*
import kotlinx.serialization.json.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class, qualifiers = "w192dp-h192dp-round-xhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class DeviceControlsScreenTest {
    @get:Rule val compose = createComposeRule()

    @Test fun thermometerPlusUsesHalfDegreeStep() {
        val values = mutableListOf<Double>()
        show { DeviceCapabilityPanel(thermostat(), false) { action, _ -> values += action.value.jsonPrimitive.double } }
        compose.onNodeWithContentDescription("Увеличить: Температура").performClick()
        compose.onNodeWithContentDescription("Увеличить: Температура").performClick()
        compose.runOnIdle { assertEquals(listOf(21.0, 21.5), values) }
    }
    @Test fun powerAndMuteControlsSendRealBooleanCommands() {
        var emitted: CapabilityAction? = null
        show { DeviceCapabilityPanel(Capability.Toggle("mute", true, true, JsonPrimitive(false), null), false) { action, _ -> emitted = action } }
        compose.onNodeWithText("Без звука").performClick()
        compose.runOnIdle {
            assertEquals(Capability.TOGGLE, emitted?.type)
            assertEquals("mute", emitted?.instance)
            assertEquals(JsonPrimitive(true), emitted?.value)
        }
    }
    @Test fun modeSelectionSendsOnlyTheSelectedSupportedMode() {
        var emitted: CapabilityAction? = null
        show { DeviceCapabilityPanel(Capability.Mode("fan_speed", listOf("low", "high"), true, true, JsonPrimitive("low"), null), false) { action, _ -> emitted = action } }
        compose.onNodeWithText("Высокий").performClick()
        compose.runOnIdle { assertEquals(Capability.MODE, emitted?.type); assertEquals(JsonPrimitive("high"), emitted?.value) }
    }
    @Test fun pendingRangeStillAcceptsTheLastDesiredValueForDebouncing() {
        val commands = mutableListOf<Pair<CapabilityAction, Long>>()
        show { DeviceCapabilityPanel(thermostat(), true) { action, debounce -> commands += action to debounce } }
        compose.onNodeWithContentDescription("Увеличить: Температура").performClick()
        compose.onNodeWithContentDescription("Уменьшить: Температура").performClick()
        compose.runOnIdle { assertEquals(listOf(21.0, 20.5), commands.map { it.first.value.jsonPrimitive.double }); assertTrue(commands.all { it.second == 350L }) }
    }
    @Test fun unavailableDeviceCannotBeControlled() {
        var calls = 0
        show { DeviceCapabilityPanel(thermostat(), false, false) { _, _ -> calls++ } }
        compose.onNodeWithContentDescription("Увеличить: Температура").assertIsNotEnabled()
        compose.onNodeWithContentDescription("Увеличить: Температура").performClick()
        compose.runOnIdle { assertEquals(0, calls) }
    }
    @Test fun sliderAccessibilityUsesTheDocumentedRangeAndPrecision() {
        var action: CapabilityAction? = null
        show { DeviceCapabilityPanel(thermostat(), false) { value, _ -> action = value } }
        compose.onNodeWithContentDescription("Значение 20,5 °C").performSemanticsAction(SemanticsActions.SetProgress) { it(22.1f) }
        compose.runOnIdle { assertEquals(JsonPrimitive(22.0), action?.value) }
    }
    @Test fun sensorCardsAreReadableInformationRatherThanDisabledButtons() {
        show { SensorReadingCard(sensorFixture().sensorReadings().primaryReading()!!, hero = true) }
        compose.onNodeWithContentDescription("Открытие: Закрыто").assertIsEnabled().assertHasNoClickAction()
    }
    @Test fun catalogPowerDoesNotOpenTheDevice() {
        var opens = 0
        var commands = 0
        val device = sensorFixture().copy(name = "Розетка", type = "devices.types.socket", properties = emptyList(), capabilities = listOf(Capability.OnOff("on", true, true, JsonPrimitive(true), null)))
        show { DeviceCatalogCard(device, null, false, true, { opens++ }, {}, { commands++ }) }
        compose.onNodeWithContentDescription("Выключить: Розетка").performClick()
        compose.runOnIdle { assertEquals(1, commands); assertEquals(0, opens) }
    }
    @Test fun renderOpeningSensor() = render("sensor-opening") { DeviceDetailScreen(sensorFixture(), null, null, false, { _, _ -> }, {}) }
    @Test fun renderMotionSensor() = render("sensor-motion") { DeviceDetailScreen(motion(), null, null, false, { _, _ -> }, {}) }
    @Test fun renderBatteryAndMissingSignal() = render("sensor-secondary") {
        DeviceDetailScreen(sensorFixture(), null, null, false, { _, _ -> }, {}, rememberScalingLazyListState(initialCenterItemIndex = 3))
    }
    @Test fun renderClimateSensorLightTheme() = render("sensor-climate-light", theme = ThemePreference.LIGHT) {
        DeviceDetailScreen(sensorFixture("Климат в комнате", listOf(
            DeviceProperty.FloatValue("temperature", "unit.temperature.celsius", 20.5, true, true, null),
            DeviceProperty.FloatValue("humidity", "unit.percent", 48.0, true, true, null),
        )), null, null, false, { _, _ -> }, {})
    }
    @Test fun renderTemperatureControls() = render("control-thermostat") { DevicePage { item { DeviceCapabilityPanel(thermostat(), false) { _, _ -> } } } }
    @Test fun renderSpeakerControls() = render("control-speaker") {
        DevicePage {
            item { DeviceCapabilityPanel(thermostat().copy(instance = "volume", unit = "unit.percent", range = ValueRange(0.0, 100.0, 1.0), value = JsonPrimitive(35)), false) { _, _ -> } }
            item { DeviceCapabilityPanel(Capability.Toggle("mute", true, true, JsonPrimitive(false), null), false) { _, _ -> } }
        }
    }
    @Test @Config(qualifiers = "w227dp-h227dp-round-xhdpi") fun renderLargeWatch() = render("sensor-opening-227", size = 227) { DeviceDetailScreen(sensorFixture(), null, null, false, { _, _ -> }, {}) }
    @Test fun renderLargeFontAndLongName() {
        RuntimeEnvironment.setFontScale(1.3f)
        render("sensor-large-font") { DeviceDetailScreen(sensorFixture("Датчик открытия окна в большой спальне"), null, null, false, { _, _ -> }, {}) }
    }
    @Test fun renderCatalog() = render("device-catalog") {
        DeviceCatalogScreen(HomeUiState(loading = false, home = SmartHome(emptyList(), emptyList(), listOf(sensorFixture(), motion()), emptyList(), emptyList(), null, 0, false)), {}, {}, {}, {})
    }

    private fun show(content: @Composable () -> Unit) {
        compose.setContent { DynorixzTheme(false, ThemePreference.DARK, content) }
    }
    private fun render(name: String, size: Int = 192, theme: ThemePreference = ThemePreference.DARK, content: @Composable () -> Unit) {
        compose.setContent { Box(Modifier.size(size.dp).clip(CircleShape)) { DynorixzTheme(false, theme, content) } }
        compose.waitForIdle()
        val file = File("build/controls-screenshots/$name.png").apply { parentFile?.mkdirs() }
        file.outputStream().use { compose.onRoot().captureToImage().asAndroidBitmap().compress(Bitmap.CompressFormat.PNG, 100, it) }
    }
    private fun thermostat() = Capability.Range("temperature", "unit.temperature.celsius", true, ValueRange(16.0, 30.0, .5), true, true, JsonPrimitive(20.5), null)
    private fun motion() = sensorFixture("Датчик движения", listOf(
        DeviceProperty.Event("motion", "detected", listOf("detected", "not_detected"), true, true, null),
        DeviceProperty.FloatValue("illumination", "unit.illumination.lux", 4.0, true, true, null),
        DeviceProperty.FloatValue("battery_level", "unit.percent", 100.0, true, true, null),
    )).copy(id = "motion", type = "devices.types.sensor.motion")
}
