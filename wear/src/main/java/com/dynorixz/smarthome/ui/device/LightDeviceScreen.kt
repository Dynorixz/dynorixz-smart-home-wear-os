package com.dynorixz.smarthome.ui.device

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.rotary.onRotaryScrollEvent
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalDensity
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
import com.dynorixz.smarthome.domain.*
import kotlinx.serialization.json.*
import kotlin.math.abs

internal enum class LightPage { OVERVIEW, COLOR, WHITE }

@Composable
internal fun LightDeviceScreen(
    device: Device,
    pendingInstance: String?,
    error: String?,
    execute: (CapabilityAction, Long) -> Unit,
    refreshing: Boolean = false,
    onRefresh: () -> Unit = {},
    initialPage: LightPage = LightPage.OVERVIEW,
    listState: ScalingLazyListState = rememberScalingLazyListState(initialCenterItemIndex = 0),
    pickerState: ScalingLazyListState = rememberScalingLazyListState(initialCenterItemIndex = 0),
    whiteState: ScalingLazyListState = rememberScalingLazyListState(initialCenterItemIndex = 0),
) {
    val power = device.capabilities.filterIsInstance<Capability.OnOff>().firstOrNull()
    val brightness = device.capabilities.filterIsInstance<Capability.Range>().firstOrNull { it.instance == "brightness" }
    val color = device.capabilities.filterIsInstance<Capability.ColorSetting>().firstOrNull()
    val supportsColor = color?.colorModel in setOf("hsv", "rgb")
    var page by rememberSaveable(device.id) { mutableStateOf(initialPage) }
    var lastColor by rememberSaveable(device.id) { mutableStateOf(listOf(0f, 100f)) }
    val reportedColor = color?.lightColor()
    LaunchedEffect(reportedColor) { reportedColor?.let { lastColor = listOf(it.hue, it.saturation) } }
    val hue = reportedColor?.hue ?: lastColor[0]
    val saturation = reportedColor?.saturation ?: lastColor[1]
    val brightnessValue = (brightness?.value as? JsonPrimitive)?.doubleOrNull?.takeIf { it.isFinite() }
    val colorPending = pendingInstance in setOf("hsv", "rgb", "temperature_k", "scene")
    val haptics = LocalHapticFeedback.current
    fun chooseColor(h: Float, s: Float, debounce: Long) {
        val capability = color ?: return
        if (!device.reachable || !supportsColor) return
        lastColor = listOf(h, s)
        execute(capability.lightColorAction(h, s, brightnessValue), debounce)
    }
    fun open(next: LightPage) { page = next }
    BackHandler(page != LightPage.OVERVIEW) { page = LightPage.OVERVIEW }

    key(page) {
        DevicePage(when (page) { LightPage.OVERVIEW -> listState; LightPage.COLOR -> pickerState; LightPage.WHITE -> whiteState }, showScrollIndicator = true) {
            item(key = "heading") {
                if (page == LightPage.OVERVIEW) {
                    Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f).padding(end = 6.dp)) {
                            Text(device.name, fontSize = 16.sp, lineHeight = 19.sp, fontWeight = FontWeight.SemiBold,
                                maxLines = 2, overflow = TextOverflow.Ellipsis)
                            Text(when ((power?.value as? JsonPrimitive)?.booleanOrNull) {
                                true -> "Свет включён"; false -> "Свет выключен"; null -> "Управление светом"
                            }, fontSize = 10.sp, lineHeight = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        power?.let {
                            val isOn = (it.value as? JsonPrimitive)?.booleanOrNull
                            IconButton(
                                onClick = { haptics.performHapticFeedback(HapticFeedbackType.LongPress); execute(CapabilityActionFactory.boolean(it, isOn != true), 0) },
                                enabled = device.reachable && pendingInstance != it.instance,
                                modifier = Modifier.size(44.dp).background(
                                    if (isOn == true) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHigh, CircleShape)
                                    .semantics { contentDescription = if (isOn == true) "Выключить свет" else "Включить свет" },
                            ) {
                                if (pendingInstance == it.instance) CircularProgressIndicator(Modifier.size(19.dp))
                                else Icon(Icons.Rounded.PowerSettingsNew, null, Modifier.size(24.dp),
                                    tint = if (isOn == true) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface)
                            }
                        }
                    }
                } else {
                    Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = { page = LightPage.OVERVIEW }, modifier = Modifier.size(44.dp)) {
                            Icon(Icons.AutoMirrored.Rounded.ArrowBack, "К управлению светом", Modifier.size(22.dp))
                        }
                        Column(Modifier.weight(1f)) {
                            Text(if (page == LightPage.COLOR) "Цвет света" else "Белый свет", fontSize = 16.sp, lineHeight = 19.sp,
                                fontWeight = FontWeight.SemiBold, maxLines = 2)
                            if (page == LightPage.COLOR) Text(if (colorPending) "Отправляем…" else reportedColor?.name ?: "Выберите образец",
                                fontSize = 10.sp, lineHeight = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
            if (!device.reachable) item(key = "offline") { DeviceNotice("Нет связи с устройством", true) }
            error?.let { item(key = "error") { DeviceNotice(it, true) } }
            when (page) {
                LightPage.OVERVIEW -> {
                    if (supportsColor || color?.temperatureRange != null) item(key = "light-pickers") {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            if (supportsColor) LightPickerButton("Цвет", Modifier.weight(1f), null) { open(LightPage.COLOR) }
                            if (color?.temperatureRange != null) LightPickerButton("Белый", Modifier.weight(1f), Color(0xFFFFE6AC)) { open(LightPage.WHITE) }
                        }
                    }
                    brightness?.let { capability ->
                        item(key = "brightness") {
                            LightRangeCard("Яркость", brightnessValue, capability.range, "%", 5.0,
                                device.reachable && capability.randomAccess, pendingInstance == "brightness", BRIGHTNESS_TRACK, compact = true) {
                                execute(CapabilityActionFactory.range(capability, it), 350)
                            }
                        }
                    }
                    items(device.capabilities.filter { it !== power && it !== brightness && it !== color && it !is Capability.Unknown },
                        key = { "extra:${it.type}:${it.instance}" }) {
                        DeviceCapabilityPanel(it, pendingInstance == it.instance, device.reachable, execute)
                    }
                    if (color != null && color.scenes.isNotEmpty()) item(key = "scenes") {
                        DeviceCapabilityPanel(color.copy(colorModel = null, temperatureRange = null), colorPending, device.reachable, execute)
                    }
                }
                LightPage.COLOR -> if (color != null && supportsColor) {
                    items(LIGHT_PALETTE.chunked(2), key = { "palette:${it.first().name}" }) { row ->
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            row.forEach { preset ->
                                LightColorTile(preset, reportedColor?.matches(preset) == true, device.reachable, Modifier.weight(1f)) {
                                    haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    chooseColor(preset.hue, preset.saturation, 0)
                                }
                            }
                            if (row.size == 1) Spacer(Modifier.weight(1f))
                        }
                    }
                    item(key = "custom-title") { Text("Свой оттенок", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                    item(key = "hue") {
                        LightRangeCard("Оттенок", hue.toDouble(), ValueRange(0.0, 360.0, 1.0), "°", 5.0,
                            device.reachable, colorPending, HUE_TRACK) { chooseColor(it.toFloat(), saturation, 350) }
                    }
                    item(key = "saturation") {
                        LightRangeCard("Насыщенность", saturation.toDouble(), ValueRange(0.0, 100.0, 1.0), "%", 5.0,
                            device.reachable, colorPending, listOf(Color.White, Color.hsv(hue, 1f, 1f))) { chooseColor(hue, it.toFloat(), 350) }
                    }
                    item(key = "done") { Button(onClick = { page = LightPage.OVERVIEW }, modifier = Modifier.fillMaxWidth(), label = { Text("Готово") }) }
                }
                LightPage.WHITE -> color?.temperatureRange?.let { range ->
                    items(range.whitePresets(), key = { "white:${it.kelvin}" }) { preset ->
                        val chosen = color.currentKelvin()?.let { abs(it - preset.kelvin) < 1.0 } == true
                        Button(onClick = {
                            haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            execute(CapabilityActionFactory.color(color, "temperature_k", JsonPrimitive(preset.kelvin.toInt())), 0)
                        }, enabled = device.reachable, modifier = Modifier.fillMaxWidth().semantics { selected = chosen },
                            icon = { LightSwatch(preset.tint) }, label = { Text(preset.name) },
                            secondaryLabel = { Text("${preset.kelvin.deviceNumber()} K") },
                            colors = if (chosen) ButtonDefaults.buttonColors() else ButtonDefaults.filledTonalButtonColors())
                    }
                    item(key = "kelvin") {
                        LightRangeCard("Температура света", color.currentKelvin(), range, "K", 100.0,
                            device.reachable, colorPending, WHITE_TRACK) {
                            execute(CapabilityActionFactory.color(color, "temperature_k", JsonPrimitive(it.toInt())), 350)
                        }
                    }
                    item(key = "done") { Button(onClick = { page = LightPage.OVERVIEW }, modifier = Modifier.fillMaxWidth(), label = { Text("Готово") }) }
                }
            }
            if (page == LightPage.OVERVIEW) item(key = "refresh") {
                Button(onClick = onRefresh, enabled = !refreshing, modifier = Modifier.fillMaxWidth(),
                    label = { Text(if (refreshing) "Обновляем…" else "Обновить") },
                    icon = { Icon(Icons.Rounded.Refresh, null, Modifier.size(20.dp)) }, colors = ButtonDefaults.filledTonalButtonColors())
            }
        }
    }
}

@Composable
private fun LightSwatch(color: Color?) {
    Box(Modifier.size(30.dp).clip(CircleShape)
        .background(color?.let { Brush.linearGradient(listOf(it, it.copy(alpha = .65f))) }
            ?: Brush.sweepGradient(HUE_TRACK)).border(1.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = .25f), CircleShape))
}

@Composable
private fun LightPickerButton(title: String, modifier: Modifier, color: Color?, onClick: () -> Unit) {
    val largeFont = LocalDensity.current.fontScale > 1.1f
    val shape = RoundedCornerShape(18.dp)
    val tile = modifier.heightIn(min = if (largeFont) 62.dp else 48.dp).clip(shape).background(MaterialTheme.colorScheme.surfaceContainer)
        .clickable(role = Role.Button, onClick = onClick).padding(horizontal = 7.dp, vertical = 7.dp)
    val swatch: @Composable () -> Unit = {
        Box(Modifier.size(20.dp).clip(CircleShape).background(color?.let { Brush.linearGradient(listOf(it, it)) } ?: Brush.sweepGradient(HUE_TRACK)))
    }
    if (largeFont) Column(tile, horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        swatch()
        Spacer(Modifier.height(3.dp))
        Text(title, fontSize = 12.sp, lineHeight = 16.sp, textAlign = TextAlign.Center)
    } else Row(tile, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
        swatch()
        Spacer(Modifier.width(5.dp))
        Text(title, fontSize = 12.sp, lineHeight = 16.sp)
    }
}

@Composable
private fun LightColorTile(preset: LightColorPreset, chosen: Boolean, enabled: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val swatch = Color.hsv(preset.hue, preset.saturation / 100f, 1f)
    val shape = RoundedCornerShape(19.dp)
    Column(modifier.defaultMinSize(minHeight = 54.dp).clip(shape)
        .background(if (chosen) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainer)
        .border(if (chosen) 2.dp else .7.dp, if (chosen) MaterialTheme.colorScheme.primary else swatch.copy(alpha = .4f), shape)
        .clickable(enabled = enabled, role = Role.RadioButton, onClick = onClick)
        .semantics(mergeDescendants = true) { contentDescription = preset.name; selected = chosen; if (!enabled) disabled() }
        .padding(horizontal = 3.dp, vertical = 6.dp), horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(3.dp)) {
        Box(Modifier.size(23.dp).background(swatch, CircleShape), contentAlignment = Alignment.Center) {
            if (chosen) Icon(Icons.Rounded.Check, null, Modifier.size(17.dp), tint = if (swatch.luminance() < .45f) Color.White else Color.Black)
        }
        Text(preset.name, fontSize = 11.sp, lineHeight = 14.sp, textAlign = TextAlign.Center, maxLines = 2,
            color = if (chosen) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface)
    }
}

@Composable
internal fun LightRangeCard(
    title: String, value: Double?, range: ValueRange, unit: String, increment: Double,
    enabled: Boolean, pending: Boolean, colors: List<Color>, compact: Boolean = false, onChange: (Double) -> Unit,
) {
    val valid = range.min.isFinite() && range.max.isFinite() && range.max > range.min
    val interactive = enabled && valid
    var local by remember(title, range) { mutableStateOf(value?.takeIf { it.isFinite() }?.coerceIn(range.min, range.max)) }
    var dragging by remember { mutableStateOf(false) }
    LaunchedEffect(value, pending) { if (!dragging) local = value?.takeIf { it.isFinite() }?.coerceIn(range.min, range.max) }
    val onChangeNow by rememberUpdatedState(onChange)
    val current by rememberUpdatedState(local)
    val focus = remember { FocusRequester() }
    val haptics = LocalHapticFeedback.current
    fun update(raw: Double) {
        if (!interactive || !raw.isFinite()) return
        val next = snapDeviceValue(raw, range)
        if (next == local) return
        local = next
        onChangeNow(next)
    }
    val content: @Composable ColumnScope.() -> Unit = {
        if (!compact) Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(title, Modifier.weight(1f), fontSize = 12.sp, lineHeight = 16.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (pending) CircularProgressIndicator(Modifier.size(12.dp))
        }
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { local?.let { update(it - increment); haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove) } },
                enabled = interactive && local != null && local!! > range.min,
                modifier = Modifier.size(44.dp).background(MaterialTheme.colorScheme.surfaceContainerHigh, CircleShape)) {
                Icon(Icons.Rounded.Remove, "Уменьшить: $title", Modifier.size(23.dp))
            }
            Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                if (compact) Text("$title $unit", fontSize = 10.sp, lineHeight = 12.sp, textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(local?.deviceNumber() ?: "—", fontSize = if (unit == "K") 18.sp else 24.sp,
                    lineHeight = 27.sp, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center, maxLines = 1)
                if (!compact) Text(unit, fontSize = 10.sp, lineHeight = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            IconButton(onClick = { local?.let { update(it + increment); haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove) } },
                enabled = interactive && local != null && local!! < range.max,
                modifier = Modifier.size(44.dp).background(MaterialTheme.colorScheme.surfaceContainerHigh, CircleShape)) {
                Icon(Icons.Rounded.Add, "Увеличить: $title", Modifier.size(23.dp))
            }
        }
        val shown = local ?: range.min
        val fraction = if (valid) ((shown - range.min) / (range.max - range.min)).toFloat().coerceIn(0f, 1f) else 0f
        Canvas(Modifier.fillMaxWidth().height(44.dp)
            .focusRequester(focus).focusable(interactive)
            .onRotaryScrollEvent { if (interactive && current != null) update(current!! + if (it.verticalScrollPixels > 0) increment else -increment); interactive }
            .pointerInput(interactive, range) {
                if (interactive) detectTapGestures {
                    focus.requestFocus()
                    val inset = 12.dp.toPx()
                    update(range.min + (range.max - range.min) * ((it.x - inset) / (size.width - 2 * inset).coerceAtLeast(1f)).coerceIn(0f, 1f))
                }
            }
            .pointerInput(interactive, range) {
                if (interactive) detectHorizontalDragGestures(onDragStart = { dragging = true; focus.requestFocus() },
                    onDragEnd = { dragging = false }, onDragCancel = { dragging = false }) { change, _ ->
                    change.consume()
                    val inset = 12.dp.toPx()
                    update(range.min + (range.max - range.min) * ((change.position.x - inset) / (size.width - 2 * inset).coerceAtLeast(1f)).coerceIn(0f, 1f))
                }
            }
            .semantics {
                contentDescription = "$title: ${local?.deviceNumber() ?: "неизвестно"}$unit"
                if (!interactive) disabled()
                if (valid) {
                    progressBarRangeInfo = ProgressBarRangeInfo(shown.toFloat(), range.min.toFloat()..range.max.toFloat())
                    setProgress { if (!interactive || !it.isFinite()) false else { update(it.toDouble()); true } }
                }
            }) {
            val inset = 12.dp.toPx()
            val start = Offset(inset, size.height / 2)
            val end = Offset(size.width - inset, size.height / 2)
            drawLine(Brush.horizontalGradient(colors, start.x, end.x), start, end, 14.dp.toPx(), StrokeCap.Round, alpha = if (interactive) 1f else .4f)
            local?.let {
                val thumb = Offset(start.x + (end.x - start.x) * fraction, start.y)
                drawCircle(Color.Black.copy(alpha = .8f), 10.dp.toPx(), thumb)
                drawCircle(Color.White, 7.dp.toPx(), thumb)
            }
        }
        if (local == null) Text("Выберите значение на шкале", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
    if (compact) Column(Modifier.fillMaxWidth().padding(horizontal = 8.dp), content = content) else DeviceCard(content = content)
}

internal val BRIGHTNESS_TRACK = listOf(Color(0xFF575044), Color(0xFFFFD16A))
internal val WHITE_TRACK = listOf(Color(0xFFFFAD54), Color(0xFFFFEEC8), Color(0xFFA6D1FF))
internal val HUE_TRACK = listOf(Color.Red, Color.Yellow, Color.Green, Color.Cyan, Color.Blue, Color.Magenta, Color.Red)
