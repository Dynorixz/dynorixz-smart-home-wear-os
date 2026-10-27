package com.dynorixz.smarthome.ui.home

import com.dynorixz.smarthome.domain.DeviceProperty
import kotlin.test.assertEquals
import org.junit.Test

class GalleryDevicePresentationTest {
    @Test
    fun unavailableStateWinsOverCachedPower() {
        assertEquals("Недоступно", fixtureDevice().copy(reachable = false).galleryStatus())
    }

    @Test
    fun unknownTypeDoesNotPretendToBeAKnownProduct() {
        assertEquals(GalleryArt.GENERIC, fixtureDevice().copy(type = "devices.types.future").galleryArt())
    }

    @Test
    fun sensorReadingReplacesGenericOpenLabel() {
        val sensor = fixtureDevice().copy(
            type = "devices.types.sensor", capabilities = emptyList(),
            properties = listOf(
                DeviceProperty.FloatValue("temperature", "unit.temperature.celsius", 23.0, true, false, null),
                DeviceProperty.FloatValue("humidity", "unit.percent", 45.0, true, false, null),
            ),
        )
        assertEquals("23° · 45%", sensor.galleryStatus())
    }
}
