package com.dynorixz.smarthome.data.mapper

import com.dynorixz.smarthome.data.network.CapabilityDto
import com.dynorixz.smarthome.data.network.DeviceDto
import com.dynorixz.smarthome.data.network.DeviceInfoResponse
import com.dynorixz.smarthome.data.network.GroupDto
import com.dynorixz.smarthome.data.network.PropertyDto
import com.dynorixz.smarthome.data.network.UserInfoResponse
import com.dynorixz.smarthome.domain.Capability
import com.dynorixz.smarthome.domain.Device
import com.dynorixz.smarthome.domain.DeviceGroup
import com.dynorixz.smarthome.domain.DeviceProperty
import com.dynorixz.smarthome.domain.Household
import com.dynorixz.smarthome.domain.Room
import com.dynorixz.smarthome.domain.Scenario
import com.dynorixz.smarthome.domain.SmartHome
import com.dynorixz.smarthome.domain.ValueRange
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

class SmartHomeMapper {
    fun toDomain(response: UserInfoResponse, updatedAtMillis: Long, stale: Boolean): SmartHome = SmartHome(
        rooms = response.rooms.map { Room(it.id, it.name, it.householdId, it.devices) },
        groups = response.groups.map(::group),
        devices = response.devices.map(::device),
        scenarios = response.scenarios.map { Scenario(it.id, it.name) },
        households = response.households.map { Household(it.id, it.name) },
        requestId = response.requestId,
        updatedAtMillis = updatedAtMillis,
        isStale = stale,
    )

    fun device(dto: DeviceDto): Device = Device(
        id = dto.id,
        name = dto.name,
        type = dto.type,
        roomId = dto.room,
        householdId = dto.householdId,
        groupIds = dto.groups,
        capabilities = dto.capabilities.map(::capability),
        properties = dto.properties.map(::property),
        reachable = dto.errorCode == null && dto.state != "offline",
    )

    fun refreshedDevice(response: DeviceInfoResponse, previous: Device): Device = Device(
        id = response.id,
        name = response.name.ifBlank { previous.name },
        type = response.type.ifBlank { previous.type },
        roomId = response.room ?: previous.roomId,
        householdId = response.householdId.ifBlank { previous.householdId },
        groupIds = response.groups.ifEmpty { previous.groupIds },
        capabilities = response.capabilities.map(::capability),
        properties = response.properties.map(::property),
        reachable = response.errorCode == null && response.state != "offline",
    )

    fun group(dto: GroupDto): DeviceGroup = DeviceGroup(
        id = dto.id,
        name = dto.name,
        type = dto.type,
        householdId = dto.householdId,
        deviceIds = dto.devices,
        capabilities = dto.capabilities.map(::capability),
    )

    fun capability(dto: CapabilityDto): Capability {
        val instance = dto.state?.instance
            ?: dto.parameters.string("instance")
            ?: if (dto.type == Capability.ON_OFF) "on" else "unknown"
        return when (dto.type) {
            Capability.ON_OFF -> Capability.OnOff(
                instance, dto.retrievable, dto.reportable, dto.state?.value, dto.lastUpdated,
            )
            Capability.RANGE -> {
                val rangeObject = dto.parameters["range"] as? JsonObject
                Capability.Range(
                    instance = instance,
                    unit = dto.parameters.string("unit"),
                    randomAccess = dto.parameters.boolean("random_access") ?: true,
                    range = ValueRange(
                        min = rangeObject?.number("min") ?: 0.0,
                        max = rangeObject?.number("max") ?: 100.0,
                        precision = rangeObject?.number("precision")?.takeIf { it > 0 } ?: 1.0,
                    ),
                    retrievable = dto.retrievable,
                    reportable = dto.reportable,
                    value = dto.state?.value,
                    lastUpdatedSeconds = dto.lastUpdated,
                )
            }
            Capability.MODE -> Capability.Mode(
                instance = instance,
                modes = dto.parameters.arrayStringsOrObjectValues("modes", "value"),
                retrievable = dto.retrievable,
                reportable = dto.reportable,
                value = dto.state?.value,
                lastUpdatedSeconds = dto.lastUpdated,
            )
            Capability.TOGGLE -> Capability.Toggle(
                instance, dto.retrievable, dto.reportable, dto.state?.value, dto.lastUpdated,
            )
            Capability.COLOR_SETTING -> {
                val temp = dto.parameters["temperature_k"] as? JsonObject
                val scenes = (dto.parameters["color_scene"] as? JsonObject)
                    ?.arrayStringsOrObjectValues("scenes", "id").orEmpty()
                Capability.ColorSetting(
                    instance = instance,
                    colorModel = dto.parameters.string("color_model"),
                    temperatureRange = temp?.let {
                        ValueRange(it.number("min") ?: 1700.0, it.number("max") ?: 6500.0, 100.0)
                    },
                    scenes = scenes,
                    retrievable = dto.retrievable,
                    reportable = dto.reportable,
                    value = dto.state?.value,
                    lastUpdatedSeconds = dto.lastUpdated,
                )
            }
            else -> Capability.Unknown(
                dto.type, instance, dto.retrievable, dto.reportable, dto.state?.value, dto.lastUpdated,
            )
        }
    }

    fun property(dto: PropertyDto): DeviceProperty {
        val instance = dto.state?.instance ?: dto.parameters.string("instance") ?: "unknown"
        return when (dto.type) {
            DeviceProperty.FLOAT -> DeviceProperty.FloatValue(
                instance = instance,
                unit = dto.parameters.string("unit"),
                value = dto.state?.value?.jsonPrimitive?.doubleOrNull,
                retrievable = dto.retrievable,
                reportable = dto.reportable,
                lastUpdatedSeconds = dto.lastUpdated,
            )
            DeviceProperty.EVENT -> DeviceProperty.Event(
                instance = instance,
                value = dto.state?.value?.jsonPrimitive?.contentOrNull,
                events = dto.parameters.arrayStringsOrObjectValues("events", "value"),
                retrievable = dto.retrievable,
                reportable = dto.reportable,
                lastUpdatedSeconds = dto.lastUpdated,
            )
            else -> DeviceProperty.Unknown(
                dto.type, instance, dto.state?.value, dto.retrievable, dto.reportable, dto.lastUpdated,
            )
        }
    }
}

private fun JsonObject.string(key: String): String? = this[key]?.jsonPrimitive?.contentOrNull
private fun JsonObject.number(key: String): Double? = this[key]?.jsonPrimitive?.doubleOrNull
private fun JsonObject.boolean(key: String): Boolean? = this[key]?.jsonPrimitive?.contentOrNull?.toBooleanStrictOrNull()

private fun JsonObject.arrayStringsOrObjectValues(key: String, nestedKey: String): List<String> =
    (this[key] as? JsonArray).orEmpty().mapNotNull { item: JsonElement ->
        runCatching { item.jsonPrimitive.contentOrNull }.getOrNull()
            ?: runCatching { item.jsonObject[nestedKey]?.jsonPrimitive?.contentOrNull }.getOrNull()
    }
