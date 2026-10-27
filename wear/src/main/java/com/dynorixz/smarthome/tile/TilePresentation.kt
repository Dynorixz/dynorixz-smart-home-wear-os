package com.dynorixz.smarthome.tile

import com.dynorixz.smarthome.R
import com.dynorixz.smarthome.data.local.FavoriteKind
import com.dynorixz.smarthome.data.local.FavoriteRef
import com.dynorixz.smarthome.data.local.HomeTileKind
import com.dynorixz.smarthome.domain.*
import com.dynorixz.smarthome.ui.device.*
import com.dynorixz.smarthome.ui.home.drawable
import com.dynorixz.smarthome.ui.home.galleryArt
import com.dynorixz.smarthome.ui.home.galleryPower
import com.dynorixz.smarthome.ui.home.galleryStatus
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull

internal enum class TileTap { OPEN, TOGGLE, SCENARIO }
internal data class HomeTileItem(
    val ref: FavoriteRef, val title: String, val status: String, val image: Int,
    val tap: TileTap, val active: Boolean = false, val alert: Boolean = false,
    val reading: String? = null, val secondary: String? = null,
    val source: HomeTileKind = HomeTileKind.HOME,
) {
    val description: String get() = "$title. $status. ${when (tap) { TileTap.OPEN -> "Открыть показания и управление"; TileTap.TOGGLE -> "Переключить питание"; TileTap.SCENARIO -> "Запустить сценарий" }}"
}
internal data class HomeTileModel(
    val kind: HomeTileKind, val title: String, val freshness: String,
    val items: List<HomeTileItem>, val hasHome: Boolean,
)

internal fun tileDevices(kind: HomeTileKind, home: SmartHome, selected: List<FavoriteRef>, favorites: List<FavoriteRef>): List<Device> {
    val available = home.devices.filter { when (kind) {
        HomeTileKind.LIGHTS -> "light" in it.type.removePrefix("devices.types.")
        HomeTileKind.SENSORS -> it.properties.isNotEmpty() || "sensor" in it.type
        HomeTileKind.HOME -> true
    } }
    return if (selected.isNotEmpty()) selected.mapNotNull { ref -> available.firstOrNull { ref.kind == FavoriteKind.DEVICE && it.id == ref.id } }.distinctBy { it.id }.take(kind.limit)
    else available.sortedBy { device -> favorites.indexOf(FavoriteRef(FavoriteKind.DEVICE, device.id)).takeIf { it >= 0 } ?: Int.MAX_VALUE }.take(kind.limit)
}

internal fun tileModel(kind: HomeTileKind, home: SmartHome?, selected: List<FavoriteRef> = emptyList(), favorites: List<FavoriteRef> = emptyList(), now: Long = System.currentTimeMillis()): HomeTileModel {
    val items = if (home == null) emptyList() else when (kind) {
        HomeTileKind.HOME -> selected.distinct().take(kind.limit).mapNotNull { ref -> when (ref.kind) {
            FavoriteKind.DEVICE -> home.devices.firstOrNull { it.id == ref.id }?.tileItem(false)
            FavoriteKind.GROUP -> home.groups.firstOrNull { it.id == ref.id }?.let { group ->
                val power = group.capabilities.filterIsInstance<Capability.OnOff>().firstOrNull()
                val on = (power?.value as? JsonPrimitive)?.booleanOrNull
                HomeTileItem(ref, group.name, when (on) { true -> "Включено"; false -> "Выключено"; null -> "Открыть группу" },
                    R.drawable.ic_shortcut_power, if (power == null) TileTap.OPEN else TileTap.TOGGLE, on == true)
            }
            FavoriteKind.SCENARIO -> home.scenarios.firstOrNull { it.id == ref.id }?.let {
                HomeTileItem(ref, it.name, "Запустить", R.drawable.ic_shortcut_scenario, TileTap.SCENARIO)
            }
        } }
        else -> tileDevices(kind, home, selected, favorites).map { it.tileItem(kind == HomeTileKind.SENSORS) }
    }
    val age = home?.let { ((now - it.updatedAtMillis).coerceAtLeast(0) / 60_000) }
    val freshness = when {
        home == null -> "Войдите в Home"
        home.isStale -> "кэш"
        age == null || age < 1 -> "сейчас"
        age < 60 -> "${age}м"
        age < 1440 -> "${age / 60}ч"
        else -> "${age / 1440}д"
    }
    return HomeTileModel(kind, when (kind) { HomeTileKind.HOME -> "Умный дом"; HomeTileKind.LIGHTS -> "Свет"; HomeTileKind.SENSORS -> "Датчики" }, freshness, items.map { it.copy(source = kind) }, home != null)
}

private fun Device.tileItem(sensor: Boolean): HomeTileItem {
    val readings = sensorReadings()
    val primary = readings.primaryReading()
    val power = capabilities.filterIsInstance<Capability.OnOff>().firstOrNull()
    val status = if (!reachable) "Недоступно" else if (sensor) primary?.spokenValue ?: "Нет данных" else galleryStatus()
    return HomeTileItem(FavoriteRef(FavoriteKind.DEVICE, id), name, status,
        if (sensor) R.drawable.ic_tile_sensor else galleryArt().drawable() ?: if (properties.isNotEmpty()) R.drawable.ic_tile_sensor else R.drawable.ic_shortcut_power,
        if (sensor || power == null || !reachable) TileTap.OPEN else TileTap.TOGGLE,
        active = galleryPower() == true, alert = !reachable || primary?.tone == ReadingTone.ALERT,
        reading = if (sensor) if (!reachable) "Нет связи" else primary?.spokenValue ?: "Нет данных" else null,
        secondary = if (sensor) readings.firstOrNull { it !== primary && it.available && it.instance !in setOf("signal_level", "signal_strength") }?.let { "${it.title}: ${it.spokenValue}" } else null)
}
