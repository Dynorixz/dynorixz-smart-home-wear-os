package com.dynorixz.smarthome.domain

import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.double
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Test

class CapabilityActionFactoryTest {
    @Test
    fun `range action clamps and snaps to advertised precision`() {
        val capability = Capability.Range(
            instance = "temperature",
            unit = "unit.temperature.celsius",
            randomAccess = true,
            range = ValueRange(16.0, 30.0, 0.5),
            retrievable = true,
            reportable = false,
            value = JsonPrimitive(20.0),
            lastUpdatedSeconds = null,
        )

        assertEquals(22.5, CapabilityActionFactory.range(capability, 22.26).value.jsonPrimitive.double)
        assertEquals(30.0, CapabilityActionFactory.range(capability, 99.0).value.jsonPrimitive.double)
    }

    @Test
    fun `mode action rejects a value absent from descriptor`() {
        val capability = Capability.Mode(
            "thermostat", listOf("auto", "cool"), true, false, JsonPrimitive("auto"), null,
        )
        assertFailsWith<IllegalArgumentException> { CapabilityActionFactory.mode(capability, "heat") }
    }
}
