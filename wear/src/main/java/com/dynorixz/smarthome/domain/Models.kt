package com.dynorixz.smarthome.domain

import kotlinx.serialization.json.JsonElement

data class SmartHome(
    val rooms: List<Room>,
    val groups: List<DeviceGroup>,
    val devices: List<Device>,
    val scenarios: List<Scenario>,
    val households: List<Household>,
    val requestId: String?,
    val updatedAtMillis: Long,
    val isStale: Boolean,
)

data class Household(val id: String, val name: String)

data class Room(
    val id: String,
    val name: String,
    val householdId: String,
    val deviceIds: List<String>,
)

data class DeviceGroup(
    val id: String,
    val name: String,
    val type: String,
    val householdId: String,
    val deviceIds: List<String>,
    val capabilities: List<Capability>,
)

data class Scenario(val id: String, val name: String)

data class Device(
    val id: String,
    val name: String,
    val type: String,
    val roomId: String?,
    val householdId: String,
    val groupIds: List<String>,
    val capabilities: List<Capability>,
    val properties: List<DeviceProperty>,
    val reachable: Boolean,
)

data class ValueRange(val min: Double, val max: Double, val precision: Double)

sealed interface Capability {
    val type: String
    val instance: String
    val retrievable: Boolean
    val reportable: Boolean
    val value: JsonElement?
    val lastUpdatedSeconds: Double?

    data class OnOff(
        override val instance: String,
        override val retrievable: Boolean,
        override val reportable: Boolean,
        override val value: JsonElement?,
        override val lastUpdatedSeconds: Double?,
    ) : Capability { override val type = Capability.ON_OFF }

    data class Range(
        override val instance: String,
        val unit: String?,
        val randomAccess: Boolean,
        val range: ValueRange,
        override val retrievable: Boolean,
        override val reportable: Boolean,
        override val value: JsonElement?,
        override val lastUpdatedSeconds: Double?,
    ) : Capability { override val type = Capability.RANGE }

    data class Mode(
        override val instance: String,
        val modes: List<String>,
        override val retrievable: Boolean,
        override val reportable: Boolean,
        override val value: JsonElement?,
        override val lastUpdatedSeconds: Double?,
    ) : Capability { override val type = Capability.MODE }

    data class Toggle(
        override val instance: String,
        override val retrievable: Boolean,
        override val reportable: Boolean,
        override val value: JsonElement?,
        override val lastUpdatedSeconds: Double?,
    ) : Capability { override val type = Capability.TOGGLE }

    data class ColorSetting(
        override val instance: String,
        val colorModel: String?,
        val temperatureRange: ValueRange?,
        val scenes: List<String>,
        override val retrievable: Boolean,
        override val reportable: Boolean,
        override val value: JsonElement?,
        override val lastUpdatedSeconds: Double?,
    ) : Capability { override val type = Capability.COLOR_SETTING }

    data class Unknown(
        override val type: String,
        override val instance: String,
        override val retrievable: Boolean,
        override val reportable: Boolean,
        override val value: JsonElement?,
        override val lastUpdatedSeconds: Double?,
    ) : Capability

    companion object {
        const val ON_OFF = "devices.capabilities.on_off"
        const val RANGE = "devices.capabilities.range"
        const val MODE = "devices.capabilities.mode"
        const val TOGGLE = "devices.capabilities.toggle"
        const val COLOR_SETTING = "devices.capabilities.color_setting"
    }
}

sealed interface DeviceProperty {
    val type: String
    val instance: String
    val retrievable: Boolean
    val reportable: Boolean
    val lastUpdatedSeconds: Double?

    data class FloatValue(
        override val instance: String,
        val unit: String?,
        val value: Double?,
        override val retrievable: Boolean,
        override val reportable: Boolean,
        override val lastUpdatedSeconds: Double?,
    ) : DeviceProperty { override val type = DeviceProperty.FLOAT }

    data class Event(
        override val instance: String,
        val value: String?,
        val events: List<String>,
        override val retrievable: Boolean,
        override val reportable: Boolean,
        override val lastUpdatedSeconds: Double?,
    ) : DeviceProperty { override val type = DeviceProperty.EVENT }

    data class Unknown(
        override val type: String,
        override val instance: String,
        val rawValue: JsonElement?,
        override val retrievable: Boolean,
        override val reportable: Boolean,
        override val lastUpdatedSeconds: Double?,
    ) : DeviceProperty

    companion object {
        const val FLOAT = "devices.properties.float"
        const val EVENT = "devices.properties.event"
    }
}

data class CapabilityAction(
    val type: String,
    val instance: String,
    val value: JsonElement,
)

data class ActionOutcome(
    val requestId: String?,
    val successful: Boolean,
    val errors: List<ActionFailure>,
)

data class ActionFailure(val code: String?, val message: String?)

sealed interface AppError {
    val diagnosticId: String?
    data class Network(override val diagnosticId: String? = null) : AppError
    data class Unauthorized(override val diagnosticId: String? = null) : AppError
    data class Forbidden(override val diagnosticId: String? = null) : AppError
    data class DeviceOffline(override val diagnosticId: String? = null) : AppError
    data class UnsupportedCapability(override val diagnosticId: String? = null) : AppError
    data class RateLimit(override val diagnosticId: String? = null) : AppError
    data class Server(override val diagnosticId: String? = null) : AppError
    data class InvalidResponse(override val diagnosticId: String? = null) : AppError
    data class Timeout(override val diagnosticId: String? = null) : AppError
    data class OAuth(val description: String?, override val diagnosticId: String? = null) : AppError
}

class AppException(val error: AppError, cause: Throwable? = null) : RuntimeException(cause)
