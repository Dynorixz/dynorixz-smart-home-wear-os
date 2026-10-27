package com.dynorixz.smarthome.ui.device

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material.icons.automirrored.rounded.DirectionsRun
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import androidx.compose.material.icons.automirrored.rounded.VolumeOff
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.foundation.ScrollInfoProvider
import androidx.wear.compose.foundation.lazy.ScalingLazyListState
import androidx.wear.compose.foundation.lazy.items
import androidx.wear.compose.foundation.lazy.rememberScalingLazyListState
import androidx.wear.compose.material3.*
import com.dynorixz.smarthome.domain.Capability
import com.dynorixz.smarthome.domain.CapabilityAction
import com.dynorixz.smarthome.domain.Device
import com.dynorixz.smarthome.ui.home.drawable
import com.dynorixz.smarthome.ui.home.galleryArt

@Composable
internal fun DeviceDetailScreen(
    device: Device,
    pendingInstance: String?,
    error: String?,
    refreshing: Boolean,
    execute: (CapabilityAction, Long) -> Unit,
    onRefresh: () -> Unit,
    listState: ScalingLazyListState = rememberScalingLazyListState(initialCenterItemIndex = 0),
) {
    val readings = device.sensorReadings()
    val controls = device.capabilities.filterNot { it is Capability.Unknown }
    val sensor = controls.isEmpty()
    val primary = readings.primaryReading()
    DevicePage(listState) {
        item(key = "device-title") { DeviceHeading(device) }
        if (!device.reachable) item(key = "offline") { DeviceNotice("Устройство недоступно\nПоследние известные данные", true) }
        error?.let { item(key = "error") { DeviceNotice(it, true) } }
        if (sensor && primary != null) item(key = "primary-reading") { SensorReadingCard(primary, hero = true) }
        items(controls, key = { "control:${it.type}:${it.instance}" }) { capability ->
            DeviceCapabilityPanel(capability, pendingInstance == capability.instance, device.reachable, execute)
        }
        if (!sensor && readings.isNotEmpty()) item(key = "readings-title") {
            Text("Показания датчиков", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        items(if (sensor) readings.filterNot { it === primary } else readings, key = { "reading:${it.instance}" }) {
            SensorReadingCard(it)
        }
        if (controls.isEmpty() && readings.isEmpty()) item {
            DeviceNotice("Устройство пока не передало показания и доступные команды")
        }
        item(key = "refresh") {
            Button(
                onClick = onRefresh, enabled = !refreshing,
                modifier = Modifier.fillMaxWidth(),
                icon = { Icon(Icons.Rounded.Refresh, null, Modifier.size(20.dp)) },
                label = { Text(if (refreshing) "Обновляем…" else "Обновить") },
                colors = ButtonDefaults.filledTonalButtonColors(),
            )
        }
    }
}

@Composable
internal fun DevicePage(
    state: ScalingLazyListState = rememberScalingLazyListState(initialCenterItemIndex = 0),
    showScrollIndicator: Boolean = false,
    content: androidx.wear.compose.foundation.lazy.ScalingLazyListScope.() -> Unit,
) {
    Box(Modifier.fillMaxSize()) {
        ScalingLazyColumn(
            state = state, modifier = Modifier.fillMaxSize(), autoCentering = null,
            contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = 32.dp, bottom = 54.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            horizontalAlignment = Alignment.CenterHorizontally, content = content,
        )
        val clockStyle = TimeTextDefaults.timeTextStyle(color = MaterialTheme.colorScheme.onBackground)
        TimeText(modifier = Modifier.scrollAway(ScrollInfoProvider(state)) {
            if (state.isScrollInProgress) ScreenStage.Scrolling else ScreenStage.Idle
        }) { time -> timeTextCurvedText(time, style = clockStyle) }
        if (showScrollIndicator) ScrollIndicator(state, Modifier.align(Alignment.CenterEnd))
    }
}

@Composable
internal fun DeviceHeading(device: Device) {
    if (device.galleryArt().drawable() == null && device.properties.isNotEmpty()) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 6.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(device.name, fontSize = 16.sp, lineHeight = 20.sp, fontWeight = FontWeight.SemiBold,
                maxLines = 2, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center)
        }
        return
    }
    Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        val art = device.galleryArt().drawable()
        if (art != null) Image(painterResource(art), null, Modifier.size(38.dp), contentScale = ContentScale.Fit)
        else Icon(readingIcon(device.sensorReadings().firstOrNull()?.instance ?: "on"), null, Modifier.size(30.dp), tint = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.width(9.dp))
        Column(Modifier.weight(1f)) {
            Text(device.typeLabel(), color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 10.sp, lineHeight = 13.sp)
            Text(device.name, fontSize = 17.sp, lineHeight = 20.sp, fontWeight = FontWeight.SemiBold, maxLines = 3, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
internal fun DeviceCard(
    accent: Color = MaterialTheme.colorScheme.primary,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    val base = MaterialTheme.colorScheme.surfaceContainer
    val shape = RoundedCornerShape(25.dp)
    Column(
        modifier.fillMaxWidth().clip(shape)
            .background(Brush.linearGradient(listOf(accent.copy(alpha = .09f).compositeOver(base), base)))
            .border(.7.dp, accent.copy(alpha = .23f), shape).padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp), content = content,
    )
}

@Composable
internal fun SensorReadingCard(reading: SensorReading, hero: Boolean = false) {
    val accent = readingAccent(reading.tone)
    DeviceCard(accent, Modifier.semantics(mergeDescendants = true) { contentDescription = "${reading.title}: ${reading.spokenValue}" }) {
        if (hero) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                Icon(readingIcon(reading.instance), null, Modifier.size(20.dp), tint = accent)
                Text(reading.title, fontSize = 12.sp, lineHeight = 15.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
                Text(reading.value, fontSize = if (reading.unit.isNotBlank()) 36.sp else 23.sp,
                    lineHeight = if (reading.unit.isNotBlank()) 40.sp else 27.sp,
                    fontWeight = FontWeight.SemiBold, color = if (reading.available) accent else MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center, modifier = Modifier.weight(1f, fill = false))
                if (reading.unit.isNotBlank()) Text(reading.unit, Modifier.padding(start = 5.dp), fontSize = 16.sp, color = accent)
            }
        } else {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Box(Modifier.size(34.dp).background(accent.copy(alpha = .13f), CircleShape), contentAlignment = Alignment.Center) {
                    Icon(readingIcon(reading.instance), null, Modifier.size(20.dp), tint = accent)
                }
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Text(reading.title, fontSize = 11.sp, lineHeight = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(reading.spokenValue, fontSize = if (reading.available) 21.sp else 14.sp, lineHeight = 25.sp, fontWeight = FontWeight.Medium)
                }
            }
        }
        reading.fraction?.let { fraction ->
            Box(Modifier.fillMaxWidth().height(4.dp).clip(CircleShape).background(accent.copy(alpha = .14f))) {
                if (fraction > 0f) Box(Modifier.fillMaxWidth(fraction).fillMaxHeight().background(accent))
            }
        }
        if (!reading.available && hero) Text("Показание недоступно", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        reading.updatedAtSeconds?.takeIf { it > 0 && it.isFinite() }?.let { updated ->
            val age = ((System.currentTimeMillis() / 1000.0 - updated) / 60).toLong().coerceAtLeast(0)
            Text(when { age < 1 -> "Обновлено только что"; age < 60 -> "Обновлено $age мин назад"; age < 1440 -> "Обновлено ${age / 60} ч назад"; else -> "Обновлено ${age / 1440} дн назад" },
                fontSize = 9.sp, lineHeight = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
internal fun DeviceNotice(text: String, error: Boolean = false) {
    Text(text, Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 3.dp), fontSize = 12.sp,
        lineHeight = 16.sp, textAlign = TextAlign.Center,
        color = if (error) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant)
}

@Composable
internal fun readingAccent(tone: ReadingTone): Color = when (tone) {
    ReadingTone.CALM -> MaterialTheme.colorScheme.primary
    ReadingTone.WARM -> if (MaterialTheme.colorScheme.background.luminance() < .5f) Color(0xFFFFCF88) else Color(0xFF875300)
    ReadingTone.ALERT -> MaterialTheme.colorScheme.error
    ReadingTone.NEUTRAL -> MaterialTheme.colorScheme.secondary
}

internal fun readingIcon(instance: String): ImageVector = when (instance) {
    "temperature", "temperature_k", "heat" -> Icons.Rounded.Thermostat
    "humidity", "water_level" -> Icons.Rounded.WaterDrop
    "battery_level" -> Icons.Rounded.BatteryFull
    "illumination", "brightness" -> Icons.Rounded.LightMode
    "open" -> Icons.Rounded.SensorDoor
    "motion", "vibration" -> Icons.AutoMirrored.Rounded.DirectionsRun
    "signal_level", "signal_strength" -> Icons.Rounded.SignalCellularAlt
    "volume" -> Icons.AutoMirrored.Rounded.VolumeUp
    "mute" -> Icons.AutoMirrored.Rounded.VolumeOff
    "water_leak" -> Icons.Rounded.WaterDamage
    "smoke", "gas" -> Icons.Rounded.WarningAmber
    "co2_level", "fan_speed", "pm1_density", "pm2.5_density", "pm10_density" -> Icons.Rounded.Air
    "on" -> Icons.Rounded.PowerSettingsNew
    else -> Icons.Rounded.Sensors
}
