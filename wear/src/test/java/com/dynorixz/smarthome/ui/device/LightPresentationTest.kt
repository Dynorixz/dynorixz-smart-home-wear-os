package com.dynorixz.smarthome.ui.device

import com.dynorixz.smarthome.domain.ValueRange
import kotlinx.serialization.json.*
import kotlin.test.*
import org.junit.Test

class LightPresentationTest {
    @Test fun hsvValueIsPreservedIncludingZero() {
        val cap = colorFixture().copy(value = JsonObject(mapOf("h" to JsonPrimitive(0), "s" to JsonPrimitive(70), "v" to JsonPrimitive(0))))
        assertEquals(JsonPrimitive(0), cap.lightColorAction(200f, 80f, null).value.jsonObject["v"])
    }
    @Test fun brightnessRangeTakesPriorityOverOldHsvState() {
        assertEquals(JsonPrimitive(89), colorFixture().lightColorAction(120f, 100f, 89.0).value.jsonObject["v"])
    }
    @Test fun switchingFromWhiteUsesKnownBrightness() {
        val cap = colorFixture().copy(instance = "temperature_k", value = JsonPrimitive(4000))
        assertEquals(JsonPrimitive(42), cap.lightColorAction(120f, 100f, 42.0).value.jsonObject["v"])
    }
    @Test fun whitePaletteHasZeroSaturation() {
        val preset = LIGHT_PALETTE.single { it.name == "Белый" }
        assertEquals(JsonPrimitive(0), colorFixture().lightColorAction(preset.hue, preset.saturation, null).value.jsonObject["s"])
    }
    @Test fun rgbUsesChannelsForSaturationAndHue() {
        val parsed = colorFixture().copy(instance = "rgb", colorModel = "rgb", value = JsonPrimitive(0xFF8080)).lightColor()!!
        assertEquals(0f, parsed.hue, .1f); assertEquals(49.8f, parsed.saturation, .1f); assertEquals(100f, parsed.value)
    }
    @Test fun noColorIsGuessedForMissingOrWhiteModeState() {
        assertNull(colorFixture().copy(value = null).lightColor())
        assertNull(colorFixture().copy(instance = "temperature_k", value = JsonPrimitive(4000)).lightColor())
    }
    @Test fun warmPresetsAreClampedAndDeduplicatedForNarrowRanges() {
        assertEquals(listOf(3000.0, 3100.0), ValueRange(3000.0, 3100.0, 100.0).whitePresets().map { it.kelvin })
    }
    @Test fun wrappedHueSelectsRedAndWhiteMatchesAnyHue() {
        assertEquals("Красный", LightColor(359f, 100f, 50f).name)
        assertTrue(LightColor(250f, 0f, 30f).matches(LIGHT_PALETTE.last()))
    }
}
