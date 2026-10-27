package com.dynorixz.smarthome.ui.device

import androidx.compose.ui.graphics.Color
import com.dynorixz.smarthome.domain.*
import kotlinx.serialization.json.*
import kotlin.math.abs
import kotlin.math.roundToInt

internal data class LightColorPreset(val name: String, val hue: Float, val saturation: Float = 100f)
internal val LIGHT_PALETTE = listOf(
    LightColorPreset("Красный", 0f), LightColorPreset("Оранжевый", 30f),
    LightColorPreset("Жёлтый", 60f), LightColorPreset("Зелёный", 120f),
    LightColorPreset("Бирюзовый", 180f), LightColorPreset("Синий", 240f),
    LightColorPreset("Фиолетовый", 280f), LightColorPreset("Розовый", 325f),
    LightColorPreset("Белый", 0f, 0f),
)

internal data class LightColor(val hue: Float, val saturation: Float, val value: Float) {
    val name: String get() = when {
        saturation < 10 -> "Белый"
        else -> LIGHT_PALETTE.filter { it.saturation > 0 }.minBy { hueDistance(hue, it.hue) }.name
    }
    fun matches(preset: LightColorPreset): Boolean = abs(saturation - preset.saturation) < 3 &&
        (preset.saturation == 0f || hueDistance(hue, preset.hue) < 3)
}

internal fun Capability.ColorSetting.lightColor(): LightColor? = when (instance) {
    "hsv" -> (value as? JsonObject)?.let {
        val h = it["h"].number() ?: return null
        val s = it["s"].number() ?: return null
        val v = it["v"].number() ?: return null
        LightColor(normalHue(h.toFloat()), s.toFloat().coerceIn(0f, 100f), v.toFloat().coerceIn(0f, 100f))
    }
    "rgb" -> (value as? JsonPrimitive)?.intOrNull?.takeIf { it in 0..0xFFFFFF }?.let {
        val channels = listOf((it shr 16) and 255, (it shr 8) and 255, it and 255)
        val high = channels.max().toFloat()
        LightColor(rgbToHue(it), if (high == 0f) 0f else (high - channels.min()) / high * 100f, high / 255f * 100f)
    }
    else -> null
}

internal fun Capability.ColorSetting.lightColorAction(hue: Float, saturation: Float, brightness: Double?): CapabilityAction {
    val h = normalHue(hue)
    val s = saturation.coerceIn(0f, 100f)
    // HSV includes brightness: changing hue must not silently set v=100.
    val v = (brightness?.takeIf { it.isFinite() } ?: lightColor()?.value?.toDouble() ?: 100.0).coerceIn(0.0, 100.0)
    val payload = if (colorModel == "hsv") JsonObject(mapOf(
        "h" to JsonPrimitive(h.roundToInt()), "s" to JsonPrimitive(s.roundToInt()), "v" to JsonPrimitive(v.roundToInt()),
    )) else JsonPrimitive(hsvToRgb(h, s / 100f))
    return CapabilityActionFactory.color(this, requireNotNull(colorModel), payload)
}

internal fun Capability.ColorSetting.currentKelvin(): Double? =
    if (instance == "temperature_k") value.number()?.takeIf { v -> temperatureRange?.let { v in it.min..it.max } == true } else null

internal data class WhiteLightPreset(val name: String, val kelvin: Double, val tint: Color)
internal fun ValueRange.whitePresets(): List<WhiteLightPreset> = listOf(
    WhiteLightPreset("Тёплый", 2700.0, Color(0xFFFFC17A)),
    WhiteLightPreset("Нейтральный", 4000.0, Color(0xFFFFEDCE)),
    WhiteLightPreset("Холодный", 6500.0, Color(0xFFB6D9FF)),
).map { it.copy(kelvin = snapDeviceValue(it.kelvin, this)) }.distinctBy { it.kelvin }

private fun JsonElement?.number(): Double? = (this as? JsonPrimitive)?.doubleOrNull?.takeIf { it.isFinite() }
private fun normalHue(hue: Float): Float = ((hue % 360f) + 360f) % 360f
private fun hueDistance(a: Float, b: Float): Float = abs(normalHue(a) - normalHue(b)).let { minOf(it, 360f - it) }
