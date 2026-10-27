package com.dynorixz.smarthome.ui.device

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.rotary.onRotaryScrollEvent
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.wear.compose.material3.*
import com.dynorixz.smarthome.domain.*
import kotlinx.serialization.json.*
import kotlin.math.round

@Composable
internal fun DeviceCapabilityPanel(
    capability: Capability,
    pending: Boolean,
    enabled: Boolean = true,
    execute: (CapabilityAction, Long) -> Unit,
) {
    when (capability) {
        is Capability.OnOff -> BooleanControl(capability, pending, enabled, execute)
        is Capability.Toggle -> BooleanControl(capability, pending, enabled, execute)
        is Capability.Range -> DeviceRangePanel(
            title = capability.instance.controlLabel(), instance = capability.instance,
            value = (capability.value as? JsonPrimitive)?.doubleOrNull,
            range = capability.range, unit = capability.unit.readableUnit(),
            enabled = enabled && capability.randomAccess, pending = pending,
        ) { execute(CapabilityActionFactory.range(capability, it), 350) }
        is Capability.Mode -> DeviceCard {
            PanelTitle(capability.instance.controlLabel(), capability.instance, pending)
            ChoiceRows(capability.modes, (capability.value as? JsonPrimitive)?.contentOrNull, enabled && !pending) {
                execute(CapabilityActionFactory.mode(capability, it), 0)
            }
        }
        is Capability.ColorSetting -> DeviceCard {
            PanelTitle("Цвет света", "brightness", pending)
            if (capability.colorModel in setOf("rgb", "hsv")) {
                palette.chunked(3).forEach { row ->
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                        row.forEach { (name, rgb, hue) ->
                            Box(Modifier.size(44.dp).clipPalette().clickable(enabled = enabled && !pending) {
                                val value = if (capability.colorModel == "rgb") JsonPrimitive(rgb and 0xFFFFFF)
                                    else JsonObject(mapOf("h" to JsonPrimitive(hue), "s" to JsonPrimitive(if ((rgb and 0xFFFFFF) == 0xFFFFFF) 0 else 100), "v" to JsonPrimitive(100)))
                                execute(CapabilityActionFactory.color(capability, capability.colorModel!!, value), 0)
                            }.semantics { contentDescription = name }, contentAlignment = Alignment.Center) {
                                Box(Modifier.size(30.dp).background(Color(rgb), CircleShape))
                            }
                        }
                    }
                }
            }
            capability.temperatureRange?.let { range ->
                DeviceRangeContent("temperature_k", if (capability.instance == "temperature_k") (capability.value as? JsonPrimitive)?.doubleOrNull else null,
                    range, "K", enabled, pending) {
                    execute(CapabilityActionFactory.color(capability, "temperature_k", JsonPrimitive(it.toInt())), 350)
                }
            }
            if (capability.scenes.isNotEmpty()) ChoiceRows(capability.scenes,
                if (capability.instance == "scene") (capability.value as? JsonPrimitive)?.contentOrNull else null, enabled && !pending) {
                execute(CapabilityActionFactory.color(capability, "scene", JsonPrimitive(it)), 0)
            }
        }
        is Capability.Unknown -> Unit
    }
}

private fun Modifier.clipPalette() = this.background(Color.Transparent, CircleShape)

@Composable
private fun BooleanControl(capability: Capability, pending: Boolean, enabled: Boolean, execute: (CapabilityAction, Long) -> Unit) {
    val on = (capability.value as? JsonPrimitive)?.booleanOrNull
    val title = if (capability is Capability.OnOff) "Питание" else capability.instance.controlLabel()
    val haptics = LocalHapticFeedback.current
    Button(
        onClick = {
            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
            execute(CapabilityActionFactory.boolean(capability, on != true), 0)
        }, enabled = enabled && !pending, modifier = Modifier.fillMaxWidth(),
        label = { Text(title, fontSize = 15.sp) },
        secondaryLabel = { Text(if (pending) "Отправляем…" else when (on) { true -> "Включено"; false -> "Выключено"; null -> "Состояние неизвестно" }) },
        icon = {
            if (pending) CircularProgressIndicator(Modifier.size(20.dp))
            else Icon(readingIcon(capability.instance), null, Modifier.size(24.dp))
        },
        colors = if (on == true) ButtonDefaults.buttonColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer,
            contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
        ) else ButtonDefaults.filledTonalButtonColors(),
    )
}

@Composable
internal fun DeviceRangePanel(title: String, instance: String, value: Double?, range: ValueRange, unit: String, enabled: Boolean, pending: Boolean, onChange: (Double) -> Unit) {
    DeviceCard {
        PanelTitle(title, instance, pending)
        DeviceRangeContent(instance, value, range, unit, enabled, pending, onChange)
    }
}

internal fun snapDeviceValue(raw: Double, range: ValueRange): Double {
    val step = range.precision.takeIf { it.isFinite() && it > 0 } ?: 1.0
    return (range.min + round((raw - range.min) / step) * step).coerceIn(range.min, range.max)
}

@Composable
private fun DeviceRangeContent(instance: String, value: Double?, range: ValueRange, unit: String, enabled: Boolean, pending: Boolean, onChange: (Double) -> Unit) {
    val validRange = range.min.isFinite() && range.max.isFinite() && range.max > range.min
    val interactive = enabled && validRange
    val step = range.precision.takeIf { it.isFinite() && it > 0 } ?: 1.0
    var localValue by remember(instance, range) { mutableStateOf(value?.takeIf { it.isFinite() }) }
    var dragging by remember { mutableStateOf(false) }
    LaunchedEffect(value, pending) { if (!dragging) localValue = value?.takeIf { it.isFinite() } }
    val onChangeNow by rememberUpdatedState(onChange)
    val localNow by rememberUpdatedState(localValue)
    val haptics = LocalHapticFeedback.current
    val focus = remember { FocusRequester() }
    fun update(raw: Double) {
        if (!interactive || !raw.isFinite()) return
        val next = snapDeviceValue(raw, range)
        if (next == localValue) return
        localValue = next
        onChangeNow(next)
    }
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(2.dp)) {
        IconButton(onClick = { localValue?.let { update(it - step); haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove) } },
            enabled = interactive && localValue != null && localValue!! > range.min,
            modifier = Modifier.size(44.dp).background(MaterialTheme.colorScheme.surfaceContainerHigh, CircleShape)) {
            Icon(Icons.Rounded.Remove, "Уменьшить: ${instance.controlLabel()}", Modifier.size(23.dp))
        }
        Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(localValue?.deviceNumber() ?: "—", fontSize = 23.sp, lineHeight = 28.sp, fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center, maxLines = 1)
            if (unit.isNotBlank()) Text(unit, fontSize = 11.sp, lineHeight = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        IconButton(onClick = { localValue?.let { update(it + step); haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove) } },
            enabled = interactive && localValue != null && localValue!! < range.max,
            modifier = Modifier.size(44.dp).background(MaterialTheme.colorScheme.surfaceContainerHigh, CircleShape)) {
            Icon(Icons.Rounded.Add, "Увеличить: ${instance.controlLabel()}", Modifier.size(23.dp))
        }
    }
    val accent = MaterialTheme.colorScheme.primary
    val track = MaterialTheme.colorScheme.surfaceContainerHigh
    val shown = localValue ?: range.min
    val fraction = if (validRange) ((shown - range.min) / (range.max - range.min)).coerceIn(0.0, 1.0).toFloat() else 0f
    Canvas(Modifier.fillMaxWidth().height(38.dp).focusRequester(focus).focusable(interactive)
        .onRotaryScrollEvent {
            if (interactive && localNow != null) update(localNow!! + if (it.verticalScrollPixels > 0) step else -step)
            interactive
        }
        .pointerInput(interactive, range) {
            if (interactive) detectTapGestures {
                focus.requestFocus()
                val inset = 10.dp.toPx()
                update(range.min + (range.max - range.min) * ((it.x - inset) / (size.width - 2 * inset).coerceAtLeast(1f)).coerceIn(0f, 1f))
            }
        }
        .pointerInput(interactive, range) {
            if (interactive) detectHorizontalDragGestures(
                onDragStart = { dragging = true; focus.requestFocus() },
                onDragEnd = { dragging = false }, onDragCancel = { dragging = false },
            ) { change, _ ->
                change.consume()
                val inset = 10.dp.toPx()
                update(range.min + (range.max - range.min) * ((change.position.x - inset) / (size.width - 2 * inset).coerceAtLeast(1f)).coerceIn(0f, 1f))
            }
        }
        .semantics {
            contentDescription = "Значение ${localValue?.deviceNumber() ?: "неизвестно"}${if (unit.isNotBlank()) " $unit" else ""}"
            if (!interactive) disabled()
            if (validRange) {
                progressBarRangeInfo = ProgressBarRangeInfo(shown.toFloat().coerceIn(range.min.toFloat(), range.max.toFloat()), range.min.toFloat()..range.max.toFloat())
                setProgress { if (!interactive || !it.isFinite()) false else { update(it.toDouble()); true } }
            }
        }) {
        val inset = 10.dp.toPx()
        val start = Offset(inset, size.height / 2)
        val end = Offset(size.width - inset, size.height / 2)
        drawLine(track, start, end, 9.dp.toPx(), StrokeCap.Round)
        val thumb = Offset(inset + (size.width - 2 * inset) * fraction, start.y)
        if (localValue != null) drawLine(accent.copy(alpha = if (interactive) 1f else .4f), start, thumb, 9.dp.toPx(), StrokeCap.Round)
        if (localValue != null) drawCircle(accent, 7.dp.toPx(), thumb)
    }
    if (!validRange || localValue == null) Text(if (validRange) "Выберите значение на шкале" else "Диапазон недоступен", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

@Composable
private fun PanelTitle(title: String, instance: String, pending: Boolean) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(7.dp)) {
        Icon(readingIcon(instance), null, Modifier.size(19.dp), tint = MaterialTheme.colorScheme.primary)
        Text(title, Modifier.weight(1f), fontSize = 12.sp, lineHeight = 16.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (pending) CircularProgressIndicator(Modifier.size(12.dp))
    }
}

@Composable
private fun ChoiceRows(values: List<String>, selected: String?, enabled: Boolean, onChoice: (String) -> Unit) {
    values.chunked(2).forEach { row ->
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            row.forEach { mode ->
                val chosen = selected == mode
                Box(Modifier.weight(1f).defaultMinSize(minHeight = 44.dp)
                    .background(if (chosen) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHigh, RoundedCornerShape(15.dp))
                    .clickable(enabled = enabled, role = Role.RadioButton) { onChoice(mode) }
                    .semantics { this.selected = chosen; if (!enabled) disabled() }
                    .padding(horizontal = 5.dp, vertical = 10.dp), contentAlignment = Alignment.Center) {
                    Text(mode.controlLabel(), fontSize = 12.sp, lineHeight = 15.sp, textAlign = TextAlign.Center,
                        color = if (chosen) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface)
                }
            }
            if (row.size == 1) Spacer(Modifier.weight(1f))
        }
    }
}

private data class PaletteColor(val name: String, val rgb: Int, val hue: Int)
private val palette = listOf(PaletteColor("Тёплый", 0xFFFFB36B.toInt(), 30), PaletteColor("Красный", 0xFFFF6B6B.toInt(), 0),
    PaletteColor("Зелёный", 0xFF6DD58C.toInt(), 130), PaletteColor("Синий", 0xFF64A8FF.toInt(), 220), PaletteColor("Белый", 0xFFFFFFFF.toInt(), 0))
