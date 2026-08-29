package com.dynorixz.smarthome.domain

import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive

object CapabilityActionFactory {
    fun boolean(capability: Capability, value: Boolean): CapabilityAction {
        require(capability is Capability.OnOff || capability is Capability.Toggle) {
            "Boolean action is unsupported for ${capability.type}"
        }
        return CapabilityAction(capability.type, capability.instance, JsonPrimitive(value))
    }

    fun range(capability: Capability.Range, value: Double): CapabilityAction {
        val normalized = ((value.coerceIn(capability.range.min, capability.range.max) - capability.range.min) /
            capability.range.precision).let { steps ->
            capability.range.min + kotlin.math.round(steps) * capability.range.precision
        }.coerceIn(capability.range.min, capability.range.max)
        return CapabilityAction(capability.type, capability.instance, JsonPrimitive(normalized))
    }

    fun mode(capability: Capability.Mode, value: String): CapabilityAction {
        require(value in capability.modes) { "Mode $value is not advertised by the device" }
        return CapabilityAction(capability.type, capability.instance, JsonPrimitive(value))
    }

    fun color(capability: Capability.ColorSetting, instance: String, value: JsonElement): CapabilityAction {
        val supported = instance == capability.instance ||
            (instance == "temperature_k" && capability.temperatureRange != null) ||
            (instance == "scene" && capability.scenes.isNotEmpty()) ||
            (instance in setOf("rgb", "hsv") && capability.colorModel == instance)
        require(supported) { "Color instance $instance is not advertised by the device" }
        return CapabilityAction(capability.type, instance, value)
    }
}

