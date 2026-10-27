@file:OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
package com.dynorixz.smarthome.ui.device

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.wear.compose.foundation.lazy.ScalingLazyListState
import androidx.wear.compose.foundation.lazy.items
import androidx.wear.compose.foundation.lazy.rememberScalingLazyListState
import androidx.wear.compose.material3.*
import com.dynorixz.smarthome.data.local.FavoriteKind
import com.dynorixz.smarthome.domain.Device
import com.dynorixz.smarthome.ui.home.HomeUiState
import com.dynorixz.smarthome.ui.home.drawable
import com.dynorixz.smarthome.ui.home.galleryArt
import com.dynorixz.smarthome.ui.home.galleryPower
import com.dynorixz.smarthome.ui.home.galleryStatus

internal enum class CatalogFilter(val title: String) { ALL("Все"), ON("Включены"), OFF("Выключены"), OFFLINE("Нет связи") }

internal fun filterCatalogDevices(devices: List<Device>, query: String, filter: CatalogFilter, roomId: String?, type: String?): List<Device> = devices.filter {
    it.name.contains(query.trim(), ignoreCase = true) && (roomId == null || it.roomId == roomId) && (type == null || it.typeLabel() == type) &&
        when (filter) {
            CatalogFilter.ALL -> true
            CatalogFilter.ON -> it.galleryPower() == true
            CatalogFilter.OFF -> it.galleryPower() == false
            CatalogFilter.OFFLINE -> !it.reachable
        }
}

@Composable
internal fun DeviceCatalogScreen(
    state: HomeUiState,
    onDevice: (Device) -> Unit,
    onFavorite: (Device) -> Unit,
    onToggle: (Device) -> Unit,
    onRefresh: () -> Unit,
    listState: ScalingLazyListState = rememberScalingLazyListState(initialCenterItemIndex = 0),
) {
    var searchOpen by rememberSaveable { mutableStateOf(false) }
    var filtersOpen by rememberSaveable { mutableStateOf(false) }
    var query by rememberSaveable { mutableStateOf("") }
    var filter by rememberSaveable { mutableStateOf(CatalogFilter.ALL) }
    var roomIndex by rememberSaveable { mutableIntStateOf(0) }
    var typeIndex by rememberSaveable { mutableIntStateOf(0) }
    val home = state.home
    val rooms = listOf(null to "Все комнаты") + home?.rooms.orEmpty().map { it.id to it.name }
    val types = listOf<String?>(null) + home?.devices.orEmpty().map { it.typeLabel() }.distinct().sorted()
    val room = rooms[roomIndex % rooms.size]
    val type = types[typeIndex % types.size]
    val favorites = state.favorites.filter { it.kind == FavoriteKind.DEVICE }.map { it.id }
    val devices = filterCatalogDevices(home?.devices.orEmpty(), query, filter, room.first, type)
        .sortedWith(compareBy<Device> { favorites.indexOf(it.id).takeIf { i -> i >= 0 } ?: Int.MAX_VALUE }.thenBy { it.name.lowercase() })
    DevicePage(listState) {
        item(key = "catalog-header") {
            Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Приборы", fontSize = 16.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text("${devices.size} из ${home?.devices?.size ?: 0}", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                IconButton(onClick = { searchOpen = !searchOpen }, Modifier.size(44.dp)) {
                    Icon(Icons.Rounded.Search, "Найти устройство", Modifier.size(21.dp), tint = if (query.isNotBlank()) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface)
                }
                IconButton(onClick = { filtersOpen = !filtersOpen }, Modifier.size(44.dp)) {
                    Icon(Icons.Rounded.Tune, "Фильтры", Modifier.size(22.dp), tint = if (filter != CatalogFilter.ALL || room.first != null || type != null) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface)
                }
            }
        }
        if (searchOpen) item(key = "catalog-search") {
            BasicTextField(value = query, onValueChange = { query = it.take(60) }, singleLine = true,
                textStyle = MaterialTheme.typography.bodyMedium.copy(color = MaterialTheme.colorScheme.onSurface),
                modifier = Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surfaceContainer, RoundedCornerShape(18.dp))
                    .padding(14.dp).semantics { contentDescription = "Поиск устройств" },
                decorationBox = { input -> if (query.isEmpty()) Text("Название прибора", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp); input() })
        }
        if (filtersOpen) {
            item(key = "catalog-state") {
                DeviceCard {
                    Text("Состояние", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    CatalogFilter.entries.chunked(2).forEach { row ->
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                            row.forEach { choice ->
                                Button(onClick = { filter = choice }, modifier = Modifier.weight(1f), label = { Text(choice.title, fontSize = 11.sp) },
                                    colors = if (filter == choice) ButtonDefaults.buttonColors() else ButtonDefaults.filledTonalButtonColors())
                            }
                        }
                    }
                }
            }
            item(key = "catalog-room") { Button(onClick = { roomIndex++ }, enabled = rooms.size > 1, modifier = Modifier.fillMaxWidth(), label = { Text(room.second) }, secondaryLabel = { Text("Комната") }, colors = ButtonDefaults.filledTonalButtonColors()) }
            item(key = "catalog-type") { Button(onClick = { typeIndex++ }, enabled = types.size > 1, modifier = Modifier.fillMaxWidth(), label = { Text(type ?: "Все типы") }, secondaryLabel = { Text("Тип прибора") }, colors = ButtonDefaults.filledTonalButtonColors()) }
            item(key = "catalog-reset") { Button(onClick = { filter = CatalogFilter.ALL; roomIndex = 0; typeIndex = 0; query = "" }, modifier = Modifier.fillMaxWidth(), label = { Text("Сбросить фильтры") }, colors = ButtonDefaults.filledTonalButtonColors()) }
        }
        if (state.offline) item(key = "catalog-offline") { DeviceNotice("Нет сети · сохранённые данные", true) }
        state.error?.let { item(key = "catalog-error") { DeviceNotice(it, true) } }
        state.success?.let { item(key = "catalog-success") { DeviceNotice("✓ $it") } }
        if (state.loading && home == null) item { CircularProgressIndicator(Modifier.size(28.dp)) }
        items(devices, key = { "catalog:${it.id}" }) { device ->
            DeviceCatalogCard(device, home?.rooms?.firstOrNull { it.id == device.roomId }?.name,
                device.id in favorites, !state.offline && !state.refreshing,
                { onDevice(device) }, { onFavorite(device) }, { onToggle(device) })
        }
        if (devices.isEmpty() && !state.loading) item { DeviceNotice(if (query.isNotBlank() || filter != CatalogFilter.ALL || room.first != null || type != null) "Ничего не найдено\nПопробуйте другой фильтр" else "В доме пока нет устройств") }
        item(key = "catalog-refresh") {
            Button(onClick = onRefresh, enabled = !state.refreshing, modifier = Modifier.fillMaxWidth(),
                label = { Text(if (state.refreshing) "Обновляем…" else "Обновить список") }, icon = { Icon(Icons.Rounded.Refresh, null, Modifier.size(20.dp)) }, colors = ButtonDefaults.filledTonalButtonColors())
        }
    }
}

@Composable
internal fun DeviceCatalogCard(device: Device, room: String?, favorite: Boolean, enabled: Boolean, onOpen: () -> Unit, onFavorite: () -> Unit, onToggle: () -> Unit) {
    val shape = RoundedCornerShape(24.dp)
    val accent = MaterialTheme.colorScheme.primary
    val power = device.galleryPower()
    val hasPower = device.capabilities.any { it is com.dynorixz.smarthome.domain.Capability.OnOff }
    val art = device.galleryArt().drawable()
    Column(Modifier.fillMaxWidth().clip(shape)
        .background(Brush.linearGradient(listOf(MaterialTheme.colorScheme.surfaceContainerHigh, MaterialTheme.colorScheme.surfaceContainer)))
        .border(.7.dp, accent.copy(alpha = .15f), shape)
        .combinedClickable(onClick = onOpen, onLongClick = onFavorite, role = Role.Button,
            onLongClickLabel = if (favorite) "Убрать из избранного" else "Добавить в избранное")
        .padding(start = 13.dp, end = 9.dp, top = 12.dp, bottom = if (hasPower) 3.dp else 13.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(room ?: device.typeLabel(), fontSize = 10.sp, lineHeight = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(device.name, fontSize = 16.sp, lineHeight = 19.sp, fontWeight = FontWeight.SemiBold, maxLines = 3, overflow = TextOverflow.Ellipsis)
                if (!hasPower) Text(device.galleryStatus(), fontSize = 13.sp, lineHeight = 17.sp, color = if (device.reachable) accent else MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Box(Modifier.size(40.dp), contentAlignment = Alignment.Center) {
                if (art != null) Image(painterResource(art), null, Modifier.size(40.dp), contentScale = ContentScale.Fit)
                else Icon(readingIcon(device.sensorReadings().primaryReading()?.instance ?: "on"), null, Modifier.size(28.dp), tint = accent)
                if (favorite) Icon(Icons.Rounded.Favorite, null, Modifier.size(10.dp).align(Alignment.TopEnd), tint = accent)
            }
        }
        if (hasPower) Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(device.galleryStatus(), Modifier.weight(1f), fontSize = 12.sp, lineHeight = 15.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            IconButton(onClick = onToggle, enabled = enabled && device.reachable, modifier = Modifier.size(44.dp)) {
                Icon(Icons.Rounded.PowerSettingsNew, "${if (power == true) "Выключить" else "Включить"}: ${device.name}", Modifier.size(23.dp), tint = if (power == true) accent else MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}
