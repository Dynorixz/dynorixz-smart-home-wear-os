package com.dynorixz.smarthome.domain

import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

internal fun hsvToRgb(hue: Float, saturation: Float = 1f, value: Float = 1f): Int {
    val normalizedHue = ((hue % 360f) + 360f) % 360f
    val chroma = value * saturation
    val x = chroma * (1f - kotlin.math.abs((normalizedHue / 60f) % 2f - 1f))
    val m = value - chroma
    val (red, green, blue) = when {
        normalizedHue < 60f -> Triple(chroma, x, 0f)
        normalizedHue < 120f -> Triple(x, chroma, 0f)
        normalizedHue < 180f -> Triple(0f, chroma, x)
        normalizedHue < 240f -> Triple(0f, x, chroma)
        normalizedHue < 300f -> Triple(x, 0f, chroma)
        else -> Triple(chroma, 0f, x)
    }
    fun channel(component: Float) = ((component + m) * 255f).roundToInt().coerceIn(0, 255)
    return (channel(red) shl 16) or (channel(green) shl 8) or channel(blue)
}

internal fun rgbToHue(rgb: Int): Float {
    val red = ((rgb shr 16) and 0xFF) / 255f
    val green = ((rgb shr 8) and 0xFF) / 255f
    val blue = (rgb and 0xFF) / 255f
    val high = max(red, max(green, blue))
    val low = min(red, min(green, blue))
    val delta = high - low
    if (delta == 0f) return 0f
    val hue = when (high) {
        red -> 60f * (((green - blue) / delta) % 6f)
        green -> 60f * (((blue - red) / delta) + 2f)
        else -> 60f * (((red - green) / delta) + 4f)
    }
    return (hue + 360f) % 360f
}
