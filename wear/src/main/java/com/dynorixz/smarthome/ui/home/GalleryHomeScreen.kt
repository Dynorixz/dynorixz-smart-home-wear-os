@file:OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)

package com.dynorixz.smarthome.ui.home

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Devices
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.GridView
import androidx.compose.material.icons.rounded.MeetingRoom
import androidx.compose.material.icons.rounded.PowerSettingsNew
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.foundation.lazy.ScalingLazyListState
import androidx.wear.compose.foundation.lazy.items
import androidx.wear.compose.material3.CircularProgressIndicator
import androidx.wear.compose.material3.Icon
import androidx.wear.compose.material3.IconButton
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.LocalContentColor
import androidx.wear.compose.material3.Text
import androidx.wear.compose.material3.TimeText
import androidx.wear.compose.material3.TimeTextDefaults
import androidx.wear.compose.material3.timeTextCurvedText
import com.dynorixz.smarthome.R
import com.dynorixz.smarthome.data.local.FavoriteKind
import com.dynorixz.smarthome.domain.Capability
import com.dynorixz.smarthome.domain.Device
import com.dynorixz.smarthome.domain.Scenario

/** A third home layout. All state and commands are supplied by the existing repository flow. */
@Composable
internal fun GalleryHomeScreen(
    state: HomeUiState,
    listState: ScalingLazyListState,
    modifier: Modifier = Modifier,
    onSettings: () -> Unit,
    onDevices: () -> Unit,
    onRooms: () -> Unit,
    onScenarios: () -> Unit,
    onGroups: () -> Unit,
    onDevice: (Device) -> Unit,
    onFavorite: (Device) -> Unit,
    onToggle: (Device) -> Unit,
    onScenario: (Scenario) -> Unit,
    onRefresh: () -> Unit,
) {
    val home = state.home
    val favoriteOrder = remember(state.favorites) {
        state.favorites.filter { it.kind == FavoriteKind.DEVICE }.map { it.id }
    }
    val devices = remember(home?.devices, favoriteOrder) {
        home?.devices.orEmpty().sortedWith(
            compareBy<Device> { favoriteOrder.indexOf(it.id).takeIf { index -> index >= 0 } ?: Int.MAX_VALUE }
                .thenBy { it.name.lowercase() },
        )
    }
    val rooms = remember(home?.rooms) { home?.rooms.orEmpty().associateBy { it.id } }
    val available = !state.offline && !state.refreshing
    val fontScale = LocalDensity.current.fontScale.coerceAtLeast(1f)
    CompositionLocalProvider(LocalContentColor provides MaterialTheme.colorScheme.onBackground) {
        val clockStyle = TimeTextDefaults.timeTextStyle(color = MaterialTheme.colorScheme.onBackground)
        Box(modifier.fillMaxSize()) {
            ScalingLazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = 14.dp, bottom = 36.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                item(key = "gallery-header") {
                    Row(
                        Modifier.fillMaxWidth().height((40 * fontScale).dp).padding(start = 20.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text("Мой дом", fontSize = 20.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(Modifier.size(4.dp).background(
                                    if (state.offline) Color(0xFFFFBB79) else MaterialTheme.colorScheme.primary,
                                    CircleShape,
                                ))
                                Spacer(Modifier.width(5.dp))
                                Text(
                                    when {
                                        state.offline -> "Нет сети"
                                        state.refreshing -> "Обновляем…"
                                        home == null -> "Ваши устройства"
                                        home.isStale -> "Сохранённые данные"
                                        else -> "Включено ${devices.count { it.galleryPower() == true }} из ${devices.size}"
                                    },
                                    fontSize = 10.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                        }
                        IconButton(onClick = onSettings, modifier = Modifier.size(48.dp)) {
                            Icon(Icons.Rounded.Settings, "Настройки", Modifier.size(19.dp))
                        }
                    }
                }
                if (state.loading && home == null) {
                    item(key = "gallery-loading") {
                        Box(Modifier.fillMaxWidth().height(96.dp), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator(Modifier.size(28.dp))
                        }
                    }
                }
                if (state.offline && home != null) {
                    item(key = "gallery-offline") {
                        GalleryNotice("Последнее известное состояние", Color(0xFFFFBB79))
                    }
                }
                state.error?.let { error ->
                    item(key = "gallery-error") { GalleryNotice(error, MaterialTheme.colorScheme.error) }
                }
                state.success?.let { message ->
                    item(key = "gallery-success") { GalleryNotice("✓ $message", MaterialTheme.colorScheme.primary) }
                }
                items(devices, key = { "gallery-device:${it.id}" }) { device ->
                    GalleryDeviceCard(
                        device = device,
                        room = rooms[device.roomId]?.name,
                        favorite = device.id in favoriteOrder,
                        controlsEnabled = available,
                        onOpen = { onDevice(device) },
                        onFavorite = { onFavorite(device) },
                        onToggle = { onToggle(device) },
                    )
                }
                if (devices.isEmpty() && !state.loading) {
                    item(key = "gallery-empty") {
                        Column(
                            Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 16.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Icon(Icons.Rounded.Devices, null, Modifier.size(32.dp), tint = MaterialTheme.colorScheme.primary)
                            Text("Здесь будет ваш дом", style = MaterialTheme.typography.titleSmall, textAlign = TextAlign.Center)
                            Text("Обновите список устройств", style = MaterialTheme.typography.bodySmall, textAlign = TextAlign.Center)
                        }
                    }
                }
                if (home != null) {
                    val favoriteScenarios = if (state.settings.showScenarios) {
                        state.favorites.filter { it.kind == FavoriteKind.SCENARIO }
                            .mapNotNull { ref -> home.scenarios.firstOrNull { it.id == ref.id } }
                    } else emptyList()
                    if (favoriteScenarios.isNotEmpty()) {
                        item(key = "gallery-scenario-heading") {
                            Text("Ваши сценарии", style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 6.dp))
                        }
                        items(favoriteScenarios, key = { "gallery-scenario:${it.id}" }) { scenario ->
                            GalleryShortcut(
                                scenario.name, "Запустить", Icons.Rounded.AutoAwesome,
                                enabled = available,
                                onClick = { onScenario(scenario) },
                            )
                        }
                    }
                    if (state.settings.showRooms && home.rooms.isNotEmpty()) {
                        item(key = "gallery-rooms") {
                            GalleryShortcut("Комнаты", "${home.rooms.size}", Icons.Rounded.MeetingRoom, onClick = onRooms)
                        }
                    }
                    if (state.settings.showScenarios && home.scenarios.isNotEmpty()) {
                        item(key = "gallery-scenarios") {
                            GalleryShortcut("Все сценарии", "${home.scenarios.size}", Icons.Rounded.AutoAwesome, onClick = onScenarios)
                        }
                    }
                    if (home.groups.isNotEmpty()) {
                        item(key = "gallery-groups") {
                            GalleryShortcut("Группы", "${home.groups.size}", Icons.Rounded.GridView, onClick = onGroups)
                        }
                    }
                    item(key = "gallery-devices") {
                        GalleryShortcut("Все устройства", "Поиск и фильтры", Icons.Rounded.Devices, onClick = onDevices)
                    }
                }
                item(key = "gallery-refresh") {
                    GalleryShortcut("Обновить", null, Icons.Rounded.Refresh, enabled = available, onClick = onRefresh)
                }
                if (devices.isNotEmpty()) {
                    item(key = "gallery-hint") {
                        Text(
                            "Удерживайте карточку,\nчтобы изменить избранное",
                            fontSize = 10.sp,
                            lineHeight = 14.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                        )
                    }
                }
            }
            TimeText { time -> timeTextCurvedText(time, style = clockStyle) }
        }
    }
}

@Composable
internal fun GalleryDeviceCard(
    device: Device,
    room: String?,
    favorite: Boolean,
    controlsEnabled: Boolean,
    onOpen: () -> Unit,
    onFavorite: () -> Unit,
    onToggle: () -> Unit,
) {
    val art = device.galleryArt()
    val power = device.galleryPower()
    val hasPower = device.capabilities.any { it is Capability.OnOff }
    val accent = art.accent()
    val lit = device.reachable && power != false
    val tint by animateColorAsState(
        if (lit) accent else Color(0xFF8D96A7), tween(250), label = "galleryTint",
    )
    val haptics = LocalHapticFeedback.current
    val shape = RoundedCornerShape(26.dp)
    val status = device.galleryStatus()
    val fontScale = LocalDensity.current.fontScale.coerceAtLeast(1f)
    val titleTop = if (fontScale > 1.1f) 18f + 12f * fontScale else 20f
    val footerTop = maxOf(66f, titleTop + 40f * fontScale + 6f)
    Box(
        Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(Brush.linearGradient(listOf(
                if (lit) Color(0xFF25262E) else Color(0xFF1C2028),
                if (lit) Color(0xFF181C25) else Color(0xFF14181E),
            )))
            .border(0.7.dp, tint.copy(alpha = if (lit) .30f else .13f), shape)
            .combinedClickable(
                role = Role.Button,
                onClickLabel = "Управление: ${device.name}",
                onLongClickLabel = if (favorite) "Убрать из избранного" else "Добавить в избранное",
                onClick = onOpen,
                onLongClick = {
                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                    onFavorite()
                },
            )
            .semantics { stateDescription = status },
    ) {
        // Radial light is static apart from a short state transition; no idle animation on a watch.
        Box(
            Modifier.align(Alignment.TopEnd).size(116.dp)
                .background(Brush.radialGradient(listOf(tint.copy(alpha = .22f), Color.Transparent))),
        )
        Row(
            Modifier.fillMaxWidth().padding(start = 15.dp, top = 12.dp, end = 13.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                room ?: "Устройство",
                Modifier.weight(1f).padding(end = 8.dp),
                color = Color(0xFFB9C0CF),
                fontSize = 10.sp,
                lineHeight = 12.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (favorite) Icon(Icons.Rounded.Favorite, null, Modifier.size(11.dp), tint = tint)
        }
        Row(
            Modifier.fillMaxWidth().padding(start = 15.dp, end = 5.dp, top = titleTop.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                device.name,
                Modifier.weight(1f).padding(end = 2.dp),
                fontSize = 17.sp,
                lineHeight = 20.sp,
                fontWeight = FontWeight.SemiBold,
                color = Color(0xFFF6F7FB),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            val image = art.drawable()
            if (image != null) {
                Image(
                    painterResource(image), null,
                    Modifier.size(52.dp).alpha(if (device.reachable) 1f else .45f),
                    contentScale = ContentScale.Fit,
                )
            } else {
                Box(Modifier.size(52.dp), contentAlignment = Alignment.Center) {
                    Icon(Icons.Rounded.Devices, null, Modifier.size(34.dp), tint = tint)
                }
            }
        }
        Row(
            Modifier.fillMaxWidth().padding(start = 15.dp, end = 20.dp, top = footerTop.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f).padding(end = 3.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(4.dp).background(if (device.reachable) tint else Color(0xFF9298A4), CircleShape))
                    Spacer(Modifier.width(5.dp))
                    Text(
                        status,
                        color = if (device.reachable) tint else Color(0xFFACB2BF),
                        fontSize = 11.sp,
                        lineHeight = 14.sp,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            IconButton(
                onClick = {
                    if (hasPower) {
                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                        onToggle()
                    } else onOpen()
                },
                enabled = if (hasPower) controlsEnabled && device.reachable else true,
                modifier = Modifier.size(48.dp).alpha(if (hasPower && (!controlsEnabled || !device.reachable)) .4f else 1f),
            ) {
                Box(
                    Modifier.size(38.dp)
                        .background(if (power == true && device.reachable) tint else Color(0xFF303642), CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        if (hasPower) Icons.Rounded.PowerSettingsNew else Icons.Rounded.ChevronRight,
                        contentDescription = when {
                            !hasPower -> "Открыть: ${device.name}"
                            power == true -> "Выключить: ${device.name}"
                            else -> "Включить: ${device.name}"
                        },
                        modifier = Modifier.size(20.dp),
                        tint = if (power == true && device.reachable) Color(0xFF162025) else Color(0xFFE5E9F1),
                    )
                }
            }
        }
    }
}

@Composable
private fun GalleryShortcut(
    title: String,
    subtitle: String?,
    icon: ImageVector,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    Row(
        Modifier.fillMaxWidth()
            .clip(RoundedCornerShape(22.dp))
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .alpha(if (enabled) 1f else .45f)
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(9.dp),
    ) {
        Box(Modifier.size(30.dp), contentAlignment = Alignment.Center) {
            Icon(icon, null, Modifier.size(19.dp), tint = MaterialTheme.colorScheme.primary)
        }
        Column(Modifier.weight(1f)) {
            Text(title, fontSize = 13.sp, lineHeight = 16.sp, fontWeight = FontWeight.Medium, maxLines = 2, overflow = TextOverflow.Ellipsis)
            if (subtitle != null) Text(subtitle, fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Icon(Icons.Rounded.ChevronRight, null, Modifier.size(14.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun GalleryNotice(message: String, color: Color) {
    Text(
        message,
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp))
            .background(color.copy(alpha = .12f)).padding(horizontal = 12.dp, vertical = 10.dp),
        color = color,
        style = MaterialTheme.typography.bodySmall,
        textAlign = TextAlign.Center,
    )
}

private fun GalleryArt.accent(): Color = when (this) {
    GalleryArt.BULB, GalleryArt.LAMP -> Color(0xFFFFCE83)
    GalleryArt.SPEAKER, GalleryArt.TV -> Color(0xFFC6AFFA)
    GalleryArt.VACUUM, GalleryArt.SOCKET -> Color(0xFF92E0CB)
    GalleryArt.HUMIDIFIER, GalleryArt.PURIFIER, GalleryArt.FAN -> Color(0xFF9ED8F6)
    GalleryArt.KETTLE -> Color(0xFFFFB996)
    GalleryArt.CAMERA, GalleryArt.GENERIC -> Color(0xFFB4C7EB)
}

internal fun GalleryArt.drawable(): Int? = when (this) {
    GalleryArt.BULB -> R.drawable.gallery_bulb
    GalleryArt.LAMP -> R.drawable.gallery_lamp
    GalleryArt.SOCKET -> R.drawable.gallery_socket
    GalleryArt.VACUUM -> R.drawable.gallery_vacuum
    GalleryArt.HUMIDIFIER -> R.drawable.gallery_humidifier
    GalleryArt.FAN -> R.drawable.gallery_fan
    GalleryArt.TV -> R.drawable.gallery_tv
    GalleryArt.KETTLE -> R.drawable.gallery_kettle
    GalleryArt.CAMERA -> R.drawable.gallery_camera
    GalleryArt.PURIFIER -> R.drawable.gallery_purifier
    GalleryArt.SPEAKER -> R.drawable.gallery_speaker
    GalleryArt.GENERIC -> null
}
