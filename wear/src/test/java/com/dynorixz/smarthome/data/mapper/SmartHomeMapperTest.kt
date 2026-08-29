package com.dynorixz.smarthome.data.mapper

import com.dynorixz.smarthome.data.network.CapabilityDto
import com.dynorixz.smarthome.data.network.CapabilityStateDto
import com.dynorixz.smarthome.data.network.DeviceDto
import com.dynorixz.smarthome.data.network.PropertyDto
import com.dynorixz.smarthome.data.network.GroupInfoResponse
import com.dynorixz.smarthome.domain.Capability
import com.dynorixz.smarthome.domain.DeviceProperty
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import org.junit.Test
import kotlinx.serialization.decodeFromString

class SmartHomeMapperTest {
    private val mapper = SmartHomeMapper()
    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun `maps descriptors without using device name`() {
        val dto = DeviceDto(
            id = "d1",
            name = "Любое имя",
            type = "devices.types.light",
            capabilities = listOf(
                CapabilityDto(
                    type = Capability.RANGE,
                    retrievable = true,
                    parameters = json.parseToJsonElement(
                        """{"instance":"brightness","unit":"unit.percent","random_access":true,"range":{"min":1,"max":100,"precision":1}}""",
                    ).jsonObject,
                    state = CapabilityStateDto("brightness", JsonPrimitive(74)),
                ),
            ),
            properties = listOf(
                PropertyDto(
                    type = DeviceProperty.FLOAT,
                    retrievable = true,
                    parameters = JsonObject(mapOf("instance" to JsonPrimitive("temperature"))),
                    state = CapabilityStateDto("temperature", JsonPrimitive(22.4)),
                ),
            ),
        )

        val device = mapper.device(dto)
        val range = assertIs<Capability.Range>(device.capabilities.single())
        assertEquals("brightness", range.instance)
        assertEquals(1.0, range.range.min)
        assertEquals(100.0, range.range.max)
        assertEquals(22.4, assertIs<DeviceProperty.FloatValue>(device.properties.single()).value)
    }

    @Test
    fun `unknown capability is preserved and cannot crash mapping`() {
        val capability = mapper.capability(
            CapabilityDto(
                type = "devices.capabilities.future_feature",
                state = CapabilityStateDto("future", JsonPrimitive("value")),
            ),
        )
        assertIs<Capability.Unknown>(capability)
        assertEquals("future", capability.instance)
    }

    @Test
    fun `official offline state marks device unreachable`() {
        val device = mapper.device(
            DeviceDto(
                id = "sensor-1",
                name = "Датчик",
                type = "devices.types.sensor",
                state = "offline",
            ),
        )
        assertEquals(false, device.reachable)
    }

    @Test
    fun `group status response decodes device objects`() {
        val response = json.decodeFromString<GroupInfoResponse>(
            """{"status":"ok","request_id":"g-request","id":"g1","name":"Люстра","type":"devices.types.light","state":"split","capabilities":[],"devices":[{"id":"d1","name":"Лампа 1","type":"devices.types.light"}]}""",
        )
        assertEquals("d1", response.devices.single().id)
        assertEquals("split", response.state)
    }
}
