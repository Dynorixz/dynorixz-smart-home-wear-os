package com.dynorixz.smarthome.domain

import kotlin.test.assertEquals
import org.junit.Test

class LightColorMathTest {
    @Test fun primaryHuesConvertToRgb() {
        assertEquals(0xFF0000, hsvToRgb(0f))
        assertEquals(0x00FF00, hsvToRgb(120f))
        assertEquals(0x0000FF, hsvToRgb(240f))
    }

    @Test fun rgbRoundTripsToHue() {
        assertEquals(0f, rgbToHue(0xFF0000), 0.1f)
        assertEquals(120f, rgbToHue(0x00FF00), 0.1f)
        assertEquals(240f, rgbToHue(0x0000FF), 0.1f)
    }
}
