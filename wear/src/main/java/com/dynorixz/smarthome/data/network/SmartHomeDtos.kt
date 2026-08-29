package com.dynorixz.smarthome.data.network

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull

@Serializable
data class UserInfoResponse(
    val status: String,
    @SerialName("request_id") val requestId: String? = null,
    val rooms: List<RoomDto> = emptyList(),
    val groups: List<GroupDto> = emptyList(),
    val devices: List<DeviceDto> = emptyList(),
    val scenarios: List<ScenarioDto> = emptyList(),
    val households: List<HouseholdDto> = emptyList(),
)

@Serializable
data class RoomDto(
    val id: String,
    val name: String,
    @SerialName("household_id") val householdId: String = "",
    val devices: List<String> = emptyList(),
)

@Serializable
data class HouseholdDto(val id: String, val name: String)

@Serializable
data class ScenarioDto(val id: String, val name: String)

@Serializable
data class DeviceDto(
    val id: String,
    val name: String,
    val aliases: List<String> = emptyList(),
    val type: String,
    @SerialName("external_id") val externalId: String? = null,
    @SerialName("skill_id") val skillId: String? = null,
    @SerialName("household_id") val householdId: String = "",
    val room: String? = null,
    val groups: List<String> = emptyList(),
    val capabilities: List<CapabilityDto> = emptyList(),
    val properties: List<PropertyDto> = emptyList(),
    val state: String? = null,
    @SerialName("error_code") val errorCode: String? = null,
)

@Serializable
data class GroupDto(
    val id: String,
    val name: String,
    val aliases: List<String> = emptyList(),
    @SerialName("household_id") val householdId: String = "",
    val type: String = "",
    val devices: List<String> = emptyList(),
    val capabilities: List<CapabilityDto> = emptyList(),
)

@Serializable
data class CapabilityDto(
    val type: String,
    val retrievable: Boolean = false,
    val reportable: Boolean = false,
    val parameters: JsonObject = JsonObject(emptyMap()),
    val state: CapabilityStateDto? = null,
    @SerialName("last_updated") val lastUpdated: Double? = null,
)

@Serializable
data class PropertyDto(
    val type: String,
    val retrievable: Boolean = false,
    val reportable: Boolean = false,
    val parameters: JsonObject = JsonObject(emptyMap()),
    val state: CapabilityStateDto? = null,
    @SerialName("last_updated") val lastUpdated: Double? = null,
)

@Serializable
data class CapabilityStateDto(
    val instance: String,
    val value: JsonElement = JsonPrimitive(""),
)

@Serializable
data class DeviceInfoResponse(
    val status: String,
    @SerialName("request_id") val requestId: String? = null,
    val id: String,
    val name: String = "",
    val type: String = "",
    @SerialName("household_id") val householdId: String = "",
    val room: String? = null,
    val groups: List<String> = emptyList(),
    val capabilities: List<CapabilityDto> = emptyList(),
    val properties: List<PropertyDto> = emptyList(),
    val state: String? = null,
    @SerialName("error_code") val errorCode: String? = null,
)

@Serializable
data class GroupDeviceInfoDto(
    val id: String,
    val name: String = "",
    val type: String = "",
)

@Serializable
data class GroupInfoResponse(
    val status: String,
    @SerialName("request_id") val requestId: String? = null,
    val id: String,
    val name: String = "",
    val type: String = "",
    @SerialName("household_id") val householdId: String = "",
    val state: String? = null,
    val devices: List<GroupDeviceInfoDto> = emptyList(),
    val capabilities: List<CapabilityDto> = emptyList(),
)

@Serializable
data class DeviceActionsRequest(val devices: List<DeviceActionsDto>)

@Serializable
data class DeviceActionsDto(val id: String, val actions: List<CapabilityActionDto>)

@Serializable
data class GroupActionsRequest(val actions: List<CapabilityActionDto>)

@Serializable
data class CapabilityActionDto(val type: String, val state: CapabilityStateDto)

@Serializable
data class ActionResponse(
    val status: String,
    @SerialName("request_id") val requestId: String? = null,
    val devices: List<DeviceActionResultDto> = emptyList(),
    val message: String? = null,
)

@Serializable
data class DeviceActionResultDto(
    val id: String,
    val capabilities: List<CapabilityActionResultDto> = emptyList(),
)

@Serializable
data class CapabilityActionResultDto(val type: String, val state: ActionStateDto)

@Serializable
data class ActionStateDto(
    val instance: String,
    @SerialName("action_result") val actionResult: ActionResultDto,
)

@Serializable
data class ActionResultDto(
    val status: String,
    @SerialName("error_code") val errorCode: String? = null,
    @SerialName("error_message") val errorMessage: String? = null,
)

@Serializable
data class ScenarioActionResponse(
    val status: String,
    @SerialName("request_id") val requestId: String? = null,
    val message: String? = null,
)

@Serializable
data class ApiErrorResponse(
    val status: String? = null,
    @SerialName("request_id") val requestId: String? = null,
    val message: String? = null,
)

@Serializable
data class OAuthTokenResponse(
    @SerialName("access_token") val accessToken: String,
    @SerialName("token_type") val tokenType: String = "bearer",
    @SerialName("expires_in") val expiresIn: Long? = null,
    @SerialName("refresh_token") val refreshToken: String? = null,
)

/**
 * The public API can contain device data supplied by many different providers.
 * Decode the top-level home response defensively so one malformed or newly-added
 * optional field cannot prevent the user from signing in or seeing other devices.
 */
fun JsonObject.toUserInfoResponse(): UserInfoResponse = UserInfoResponse(
    status = text("status").orEmpty(),
    requestId = text("request_id"),
    rooms = objects("rooms").mapNotNull { item ->
        val id = item.text("id")?.takeIf(String::isNotBlank) ?: return@mapNotNull null
        RoomDto(
            id = id,
            name = item.text("name").orEmpty(),
            householdId = item.text("household_id").orEmpty(),
            devices = item.strings("devices"),
        )
    },
    groups = objects("groups").mapNotNull { item ->
        val id = item.text("id")?.takeIf(String::isNotBlank) ?: return@mapNotNull null
        GroupDto(
            id = id,
            name = item.text("name").orEmpty(),
            aliases = item.strings("aliases"),
            householdId = item.text("household_id").orEmpty(),
            type = item.text("type").orEmpty(),
            devices = item.strings("devices"),
            capabilities = item.objects("capabilities").mapNotNull(::capabilityDto),
        )
    },
    devices = objects("devices").mapNotNull { item ->
        val id = item.text("id")?.takeIf(String::isNotBlank) ?: return@mapNotNull null
        DeviceDto(
            id = id,
            name = item.text("name").orEmpty(),
            aliases = item.strings("aliases"),
            type = item.text("type") ?: "devices.types.other",
            externalId = item.text("external_id"),
            skillId = item.text("skill_id"),
            householdId = item.text("household_id").orEmpty(),
            room = item.text("room"),
            groups = item.strings("groups"),
            capabilities = item.objects("capabilities").mapNotNull(::capabilityDto),
            properties = item.objects("properties").mapNotNull(::propertyDto),
            state = item.text("state"),
            errorCode = item.text("error_code"),
        )
    },
    scenarios = objects("scenarios").mapNotNull { item ->
        val id = item.text("id")?.takeIf(String::isNotBlank) ?: return@mapNotNull null
        ScenarioDto(id = id, name = item.text("name").orEmpty())
    },
    households = objects("households").mapNotNull { item ->
        val id = item.text("id")?.takeIf(String::isNotBlank) ?: return@mapNotNull null
        HouseholdDto(id = id, name = item.text("name").orEmpty())
    },
)

private fun capabilityDto(item: JsonObject): CapabilityDto? {
    val type = item.text("type")?.takeIf(String::isNotBlank) ?: return null
    val parameters = item.objectOrEmpty("parameters")
    return CapabilityDto(
        type = type,
        retrievable = item.boolean("retrievable") ?: false,
        reportable = item.boolean("reportable") ?: false,
        parameters = parameters,
        state = item.state(parameters, if (type.endsWith(".on_off")) "on" else "unknown"),
        lastUpdated = item.number("last_updated"),
    )
}

private fun propertyDto(item: JsonObject): PropertyDto? {
    val type = item.text("type")?.takeIf(String::isNotBlank) ?: return null
    val parameters = item.objectOrEmpty("parameters")
    return PropertyDto(
        type = type,
        retrievable = item.boolean("retrievable") ?: false,
        reportable = item.boolean("reportable") ?: false,
        parameters = parameters,
        state = item.state(parameters, "unknown"),
        lastUpdated = item.number("last_updated"),
    )
}

private fun JsonObject.state(parameters: JsonObject, fallbackInstance: String): CapabilityStateDto? {
    val state = this["state"] as? JsonObject ?: return null
    return CapabilityStateDto(
        instance = state.text("instance") ?: parameters.text("instance") ?: fallbackInstance,
        value = state["value"] ?: JsonNull,
    )
}

private fun JsonObject.text(key: String): String? =
    (this[key] as? JsonPrimitive)?.contentOrNull

private fun JsonObject.number(key: String): Double? =
    (this[key] as? JsonPrimitive)?.doubleOrNull

private fun JsonObject.boolean(key: String): Boolean? =
    (this[key] as? JsonPrimitive)?.booleanOrNull

private fun JsonObject.objectOrEmpty(key: String): JsonObject =
    this[key] as? JsonObject ?: JsonObject(emptyMap())

private fun JsonObject.objects(key: String): List<JsonObject> =
    (this[key] as? JsonArray).orEmpty().mapNotNull { it as? JsonObject }

private fun JsonObject.strings(key: String): List<String> =
    (this[key] as? JsonArray).orEmpty().mapNotNull { (it as? JsonPrimitive)?.contentOrNull }
