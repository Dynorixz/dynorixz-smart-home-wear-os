package com.dynorixz.smarthome.ui.device

import android.app.Application
import android.graphics.Bitmap
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.unit.dp
import androidx.wear.compose.foundation.lazy.rememberScalingLazyListState
import com.dynorixz.smarthome.data.local.ThemePreference
import com.dynorixz.smarthome.domain.*
import com.dynorixz.smarthome.ui.DynorixzTheme
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
class LightDeviceScreenTest {
    @get:Rule val compose = createComposeRule()

    @Test fun openingPaletteAndReturningDoesNotSendCommands() {
        var calls = 0
        show { LightDeviceScreen(lightFixture(), null, null, { _, _ -> calls++ }) }
        compose.onNodeWithText("Цвет").performClick()
        compose.onNodeWithContentDescription("Красный").assertHasClickAction()
        compose.onNodeWithContentDescription("К управлению светом").performClick()
        compose.onNodeWithText("Цвет").assertExists()
        compose.runOnIdle { assertEquals(0, calls) }
    }
    @Test fun paletteSendsHsvWithoutRaisingExistingBrightness() {
        var action: CapabilityAction? = null
        show { LightDeviceScreen(lightFixture(), "hsv", null, { a, _ -> action = a }, initialPage = LightPage.COLOR) }
        compose.onNodeWithContentDescription("Красный").performScrollTo().performClick()
        compose.runOnIdle {
            assertEquals("hsv", action?.instance)
            assertEquals(JsonObject(mapOf("h" to JsonPrimitive(0), "s" to JsonPrimitive(100), "v" to JsonPrimitive(89))), action?.value)
        }
    }
    @Test fun paletteAlsoSendsTheAdvertisedRgbModel() {
        var action: CapabilityAction? = null
        val device = lightFixture().copy(capabilities = listOf(colorFixture().copy(instance = "rgb", colorModel = "rgb", value = JsonPrimitive(0x125500))))
        show { LightDeviceScreen(device, null, null, { a, _ -> action = a }, initialPage = LightPage.COLOR) }
        compose.onNode(hasScrollToIndexAction()).performScrollToNode(hasContentDescription("Синий"))
        compose.onNodeWithContentDescription("Синий").performClick()
        compose.runOnIdle { assertEquals("rgb", action?.instance); assertEquals(JsonPrimitive(0x0000FF), action?.value) }
    }
    @Test fun powerToggleUsesTheRealBooleanState() {
        var action: CapabilityAction? = null
        show { LightDeviceScreen(lightFixture(), null, null, { a, _ -> action = a }) }
        compose.onNodeWithContentDescription("Выключить свет").performClick()
        compose.runOnIdle { assertEquals("on", action?.instance); assertEquals(JsonPrimitive(false), action?.value) }
    }
    @Test fun pendingPowerCannotBeSentTwice() {
        var calls = 0
        show { LightDeviceScreen(lightFixture(), "on", null, { _, _ -> calls++ }) }
        compose.runOnIdle { assertEquals(0, calls) }
        compose.onNodeWithContentDescription("Выключить свет").assertIsNotEnabled().performClick()
        compose.runOnIdle { assertEquals(0, calls) }
    }
    @Test fun brightnessStepperKeepsTheLatestDesiredValueWhilePending() {
        val values = mutableListOf<Double>()
        show { LightRangeCard("Яркость", 89.0, ValueRange(1.0, 100.0, 1.0), "%", 5.0, true, true, BRIGHTNESS_TRACK) {
            values += it
        } }
        compose.onNodeWithContentDescription("Увеличить: Яркость").performClick()
        compose.onNodeWithContentDescription("Увеличить: Яркость").performClick()
        compose.onNodeWithContentDescription("Увеличить: Яркость").performClick()
        compose.runOnIdle { assertEquals(listOf(94.0, 99.0, 100.0), values) }
    }
    @Test fun brightnessSliderRespectsPrecisionAndDebouncesCommands() {
        var action: CapabilityAction? = null
        var debounce: Long? = null
        show { LightDeviceScreen(lightFixture(), null, null, { a, d -> action = a; debounce = d }) }
        compose.onNodeWithContentDescription("Яркость: 89%").performScrollTo()
            .performSemanticsAction(SemanticsActions.SetProgress) { it(41.4f) }
        compose.runOnIdle { assertEquals(JsonPrimitive(41.0), action?.value); assertEquals(350L, debounce) }
    }
    @Test fun verticalScrollOverSliderDoesNotChangeTheLight() {
        var calls = 0
        show { LightDeviceScreen(lightFixture(), null, null, { _, _ -> calls++ }) }
        compose.onNodeWithContentDescription("Яркость: 89%").performScrollTo().performTouchInput {
            swipe(Offset(center.x, bottom - 3), Offset(center.x, top + 3), 500)
        }
        compose.runOnIdle { assertEquals(0, calls) }
    }
    @Test fun temperaturePresetOnlyUsesSupportedTemperature() {
        var action: CapabilityAction? = null
        val device = lightFixture().copy(capabilities = listOf(colorFixture().copy(temperatureRange = ValueRange(3000.0, 5000.0, 100.0))))
        show { LightDeviceScreen(device, null, null, { a, _ -> action = a }, initialPage = LightPage.WHITE) }
        compose.onNodeWithText("Тёплый").performScrollTo().performClick()
        compose.runOnIdle { assertEquals("temperature_k", action?.instance); assertEquals(JsonPrimitive(3000), action?.value) }
    }
    @Test fun customSaturationKeepsTheHueAndBrightnessAndSendsLastValue() {
        val commands = mutableListOf<Pair<CapabilityAction, Long>>()
        show { LightDeviceScreen(lightFixture(), "hsv", null, { a, d -> commands += a to d }, initialPage = LightPage.COLOR,
            pickerState = rememberScalingLazyListState(initialCenterItemIndex = 8)) }
        compose.onNodeWithContentDescription("Насыщенность: 100%").performSemanticsAction(SemanticsActions.SetProgress) { it(45f) }
        compose.onNodeWithContentDescription("Увеличить: Насыщенность").performClick()
        compose.runOnIdle {
            assertEquals(listOf(45, 50), commands.map { it.first.value.jsonObject["s"]!!.jsonPrimitive.int })
            assertTrue(commands.all { it.second == 350L && it.first.value.jsonObject["h"] == JsonPrimitive(240) && it.first.value.jsonObject["v"] == JsonPrimitive(89) })
        }
    }
    @Test fun exactWhiteTemperatureSendsKelvinInsteadOfAnRgbValue() {
        var action: CapabilityAction? = null
        show { LightDeviceScreen(lightFixture(), null, null, { a, _ -> action = a }, initialPage = LightPage.WHITE,
            whiteState = rememberScalingLazyListState(initialCenterItemIndex = 4)) }
        compose.onNodeWithContentDescription("Температура света: неизвестноK").performSemanticsAction(SemanticsActions.SetProgress) { it(3700f) }
        compose.runOnIdle { assertEquals("temperature_k", action?.instance); assertEquals(JsonPrimitive(3700), action?.value) }
    }
    @Test fun unavailableLightCannotSendPaletteOrBrightnessCommands() {
        var calls = 0
        show { LightDeviceScreen(lightFixture().copy(reachable = false), null, null, { _, _ -> calls++ }, initialPage = LightPage.COLOR) }
        compose.onNodeWithContentDescription("Красный").performScrollTo().assertIsNotEnabled().performClick()
        compose.runOnIdle { assertEquals(0, calls) }
    }
    @Test fun whiteOnlyLightDoesNotOfferUnsupportedColors() {
        val device = lightFixture().copy(capabilities = listOf(colorFixture().copy(instance = "temperature_k", colorModel = null, value = JsonPrimitive(4000))))
        show { LightDeviceScreen(device, null, null, { _, _ -> }) }
        compose.onNodeWithText("Цвет").assertDoesNotExist()
        compose.onNodeWithText("Белый").assertExists()
    }
    @Test fun renderOverview() = render("light-overview") { LightDeviceScreen(lightFixture(), null, null, { _, _ -> }) }
    @Test fun renderPalette() = render("light-palette") { LightDeviceScreen(lightFixture(), null, null, { _, _ -> }, initialPage = LightPage.COLOR) }
    @Test fun renderPaletteScrolled() = render("light-palette-scrolled") {
        LightDeviceScreen(lightFixture(), null, null, { _, _ -> }, initialPage = LightPage.COLOR,
            pickerState = rememberScalingLazyListState(initialCenterItemIndex = 4))
    }
    @Test fun renderWhitePresets() = render("light-white") { LightDeviceScreen(lightFixture(), null, null, { _, _ -> }, initialPage = LightPage.WHITE) }
    @Test fun renderBrightnessSlider() = render("light-brightness-scroll") {
        LightDeviceScreen(lightFixture(), null, null, { _, _ -> }, listState = rememberScalingLazyListState(initialCenterItemIndex = 2))
    }
    @Test fun renderWhiteTemperature() = render("light-kelvin") {
        val whiteDevice = lightFixture().copy(capabilities = listOf(colorFixture().copy(instance = "temperature_k", value = JsonPrimitive(4000))))
        LightDeviceScreen(whiteDevice, null, null, { _, _ -> }, initialPage = LightPage.WHITE,
            whiteState = rememberScalingLazyListState(initialCenterItemIndex = 4))
    }
    @Test fun renderCustomHue() = render("light-hue") {
        LightDeviceScreen(lightFixture(), null, null, { _, _ -> }, initialPage = LightPage.COLOR,
            pickerState = rememberScalingLazyListState(initialCenterItemIndex = 7))
    }
    @Test @Config(qualifiers = "w227dp-h227dp-round-xhdpi") fun renderLargeWatchLightTheme() = render("light-overview-227-light", 227, ThemePreference.LIGHT) {
        LightDeviceScreen(lightFixture(), null, null, { _, _ -> })
    }
    @Test fun renderLargeFontLongNameAndFullBrightness() {
        RuntimeEnvironment.setFontScale(1.3f)
        val device = lightFixture().copy(name = "Люстра в большой спальне", capabilities = lightFixture().capabilities.map {
            if (it is Capability.Range) it.copy(value = JsonPrimitive(100)) else it
        })
        render("light-large-font") { LightDeviceScreen(device, null, null, { _, _ -> }) }
    }

    private fun show(content: @Composable () -> Unit) = compose.setContent { DynorixzTheme(false, ThemePreference.DARK, content) }
    private fun render(name: String, size: Int = 192, theme: ThemePreference = ThemePreference.DARK, content: @Composable () -> Unit) {
        compose.setContent { Box(Modifier.size(size.dp).clip(CircleShape)) { DynorixzTheme(false, theme, content) } }
        compose.waitForIdle()
        val file = File("build/light-screenshots/$name.png").apply { parentFile?.mkdirs() }
        file.outputStream().use { compose.onRoot().captureToImage().asAndroidBitmap().compress(Bitmap.CompressFormat.PNG, 100, it) }
    }
}

internal fun lightFixture(): Device = sensorFixture().copy(id = "light", name = "Люстра", type = "devices.types.light", properties = emptyList(),
    capabilities = listOf(Capability.OnOff("on", true, true, JsonPrimitive(true), null),
        Capability.Range("brightness", "unit.percent", true, ValueRange(1.0, 100.0, 1.0), true, true, JsonPrimitive(89), null), colorFixture()))
internal fun colorFixture() = Capability.ColorSetting("hsv", "hsv", ValueRange(2000.0, 6500.0, 1.0), emptyList(), true, true,
    JsonObject(mapOf("h" to JsonPrimitive(240), "s" to JsonPrimitive(100), "v" to JsonPrimitive(37))), null)
