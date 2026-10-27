package com.dynorixz.smarthome.ui.home

import com.dynorixz.smarthome.domain.Capability
import com.dynorixz.smarthome.domain.Device
import com.dynorixz.smarthome.domain.DeviceProperty
import java.util.Locale
import com.dynorixz.smarthome.ui.device.sensorReadings
import com.dynorixz.smarthome.ui.device.primaryReading
import com.dynorixz.smarthome.ui.device.readableUnit
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.doubleOrNull

/** Local category photographs, not identification of a device's manufacturer or model. */
internal enum class GalleryArt {
    BULB, LAMP, SOCKET, VACUUM, HUMIDIFIER, FAN, TV, KETTLE, CAMERA, PURIFIER, SPEAKER, GENERIC,
}

internal fun Device.galleryArt(): GalleryArt {
    val category = type.removePrefix("devices.types.")
    return when {
        category.startsWith("light") -> if (
            listOf("ночник", "настольн", "торшер", "bedside", "desk lamp").any { it in name.lowercase() }
        ) GalleryArt.LAMP else GalleryArt.BULB
        category.startsWith("socket") -> GalleryArt.SOCKET
        category.startsWith("vacuum") -> GalleryArt.VACUUM
        category == "humidifier" -> GalleryArt.HUMIDIFIER
        category == "fan" -> GalleryArt.FAN
        category == "purifier" -> GalleryArt.PURIFIER
        category.startsWith("smart_speaker") -> GalleryArt.SPEAKER
        category == "tv" || category.startsWith("media_device") -> GalleryArt.TV
        category == "cooking.kettle" -> GalleryArt.KETTLE
        category.startsWith("camera") -> GalleryArt.CAMERA
        else -> GalleryArt.GENERIC
    }
}

internal fun Device.galleryPower(): Boolean? = capabilities.filterIsInstance<Capability.OnOff>()
    .firstOrNull()?.let { (it.value as? JsonPrimitive)?.booleanOrNull }

internal fun Device.galleryStatus(): String {
    if (!reachable) return "Недоступно"
    if (galleryPower() == false) return "Выключено"
    val ranges = capabilities.filterIsInstance<Capability.Range>()
    val brightness = ranges.firstOrNull { it.instance == "brightness" }
        ?.let { (it.value as? JsonPrimitive)?.doubleOrNull }
    if (brightness != null) return "Яркость ${brightness.galleryNumber()}%"
    val properties = properties.filterIsInstance<DeviceProperty.FloatValue>()
    val temperature = properties.firstOrNull { it.instance == "temperature" && it.value != null }
    val humidity = properties.firstOrNull { it.instance == "humidity" && it.value != null }
    if (temperature != null && humidity != null) {
        return "${temperature.value!!.galleryNumber()}° · ${humidity.value!!.galleryNumber()}%"
    }
    if (capabilities.isEmpty() || type.contains("sensor")) {
        sensorReadings().primaryReading()?.let { return it.spokenValue }
    }
    val range = ranges.firstOrNull { it.instance in setOf("temperature", "humidity", "volume", "open") }
    val rangeValue = (range?.value as? JsonPrimitive)?.doubleOrNull
    if (range != null && rangeValue != null) {
        val label = when (range.instance) {
            "volume" -> "Громкость "
            "open" -> "Открыто "
            else -> ""
        }
        return "$label${rangeValue.galleryNumber()}${range.unit.galleryUnit()}"
    }
    properties.firstOrNull { it.value != null }?.let {
        return "${it.value!!.galleryNumber()}${it.unit.galleryUnit()}"
    }
    properties@ for (property in this.properties.filterIsInstance<DeviceProperty.Event>()) {
        return when (property.value) {
            "opened", "open" -> "Открыто"
            "closed", "close" -> "Закрыто"
            "dry" -> "Сухо"
            "leak" -> "Протечка"
            "detected" -> "Обнаружено"
            "not_detected" -> "Не обнаружено"
            "low" -> "Низкий заряд"
            "normal" -> "В норме"
            else -> continue@properties
        }
    }
    return if (galleryPower() == true) "Включено" else "Открыть управление"
}

private fun Double.galleryNumber(): String =
    if (this % 1.0 == 0.0) toLong().toString() else String.format(Locale.getDefault(), "%.1f", this)

private fun String?.galleryUnit(): String = when (this) {
    "unit.percent" -> "%"
    "unit.temperature.celsius" -> "°C"
    "unit.temperature.kelvin" -> " K"
    "unit.watt" -> " Вт"
    "unit.kilowatt_hour" -> " кВт·ч"
    "unit.ppm" -> " ppm"
    "unit.volt" -> " В"
    "unit.ampere" -> " А"
    "unit.pressure.mmhg" -> " мм рт. ст."
    null -> ""
    else -> " " + readableUnit()
}
