package com.dynorixz.smarthome.ui.device

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.wear.compose.material3.Button
import androidx.wear.compose.material3.ButtonDefaults
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.Text
import androidx.wear.compose.material3.TimeText
import com.dynorixz.smarthome.domain.Capability
import com.dynorixz.smarthome.domain.CapabilityAction
import com.dynorixz.smarthome.domain.CapabilityActionFactory
import com.dynorixz.smarthome.domain.Device
import com.dynorixz.smarthome.domain.ValueRange
import com.dynorixz.smarthome.domain.hsvToRgb
import com.dynorixz.smarthome.domain.rgbToHue
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.floatOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

private enum class LightControl(val title: String) {
    BRIGHTNESS("Яркость"),
    TEMPERATURE("Тон"),
    COLOR("Цвет"),
}

@Composable
internal fun LightDeviceScreen(
    device: Device,
    pendingInstance: String?,
    error: String?,
    execute: (CapabilityAction, Long) -> Unit,
) {
    val power = device.capabilities.filterIsInstance<Capability.OnOff>().firstOrNull()
    val brightness = device.capabilities.filterIsInstance<Capability.Range>()
        .firstOrNull { it.instance == "brightness" }
    val color = device.capabilities.filterIsInstance<Capability.ColorSetting>().firstOrNull()
    val controls = remember(brightness, color) {
        buildList {
            if (brightness != null) add(LightControl.BRIGHTNESS)
            if (color?.temperatureRange != null) add(LightControl.TEMPERATURE)
            if (color?.colorModel in setOf("hsv", "rgb")) add(LightControl.COLOR)
        }
    }
    var selectedName by rememberSaveable(device.id) {
        mutableStateOf(controls.firstOrNull()?.name ?: LightControl.BRIGHTNESS.name)
    }
    val selected = LightControl.entries.firstOrNull { it.name == selectedName && it in controls }
        ?: controls.firstOrNull()
        ?: LightControl.BRIGHTNESS

    var brightnessValue by rememberSaveable(device.id) {
        mutableFloatStateOf(brightness.currentValue().toFloat())
    }
    val temperatureRange = color?.temperatureRange
    var temperatureValue by rememberSaveable(device.id) {
        mutableFloatStateOf(color.currentTemperature().toFloat())
    }
    var hueValue by rememberSaveable(device.id) { mutableFloatStateOf(color.currentHue()) }
    var saturationValue by rememberSaveable(device.id) { mutableFloatStateOf(color.currentSaturation()) }

    LaunchedEffect(brightness?.value, pendingInstance) {
        if (pendingInstance != "brightness") brightnessValue = brightness.currentValue().toFloat()
    }
    LaunchedEffect(color?.instance, color?.value, pendingInstance) {
        if (pendingInstance == null) {
            if (color?.instance == "temperature_k") temperatureValue = color.currentTemperature().toFloat()
            if (color?.instance in setOf("hsv", "rgb")) {
                hueValue = color.currentHue()
                saturationValue = color.currentSaturation()
            }
        }
    }

    fun setBrightness(fraction: Float) {
        val capability = brightness ?: return
        val span = (capability.range.max - capability.range.min).takeIf { it > 0.0 } ?: 1.0
        val raw = capability.range.min + span * fraction.coerceIn(0f, 1f)
        val step = max(capability.range.precision, span / 100.0)
        val next = (capability.range.min + ((raw - capability.range.min) / step).roundToInt() * step)
            .coerceIn(capability.range.min, capability.range.max)
            .toFloat()
        brightnessValue = next
        execute(CapabilityActionFactory.range(capability, next.toDouble()), 300)
    }

    fun setTemperature(fraction: Float) {
        val capability = color ?: return
        val range = temperatureRange ?: return
        val span = (range.max - range.min).takeIf { it > 0.0 } ?: 1.0
        val step = max(range.precision, 50.0)
        val raw = range.min + span * fraction.coerceIn(0f, 1f)
        val next = (range.min + ((raw - range.min) / step).roundToInt() * step)
            .coerceIn(range.min, range.max)
            .toFloat()
        temperatureValue = next
        execute(
            CapabilityActionFactory.color(capability, "temperature_k", JsonPrimitive(next.roundToInt())),
            300,
        )
    }

    fun setColor(hue: Float, saturation: Float) {
        val capability = color ?: return
        val model = capability.colorModel ?: return
        hueValue = (hue + 360f) % 360f
        saturationValue = saturation.coerceIn(0f, 100f)
        val payload = if (model == "hsv") {
            JsonObject(
                mapOf(
                    "h" to JsonPrimitive(hueValue.roundToInt()),
                    "s" to JsonPrimitive(saturationValue.roundToInt()),
                    "v" to JsonPrimitive(100),
                ),
            )
        } else {
            JsonPrimitive(hsvToRgb(hueValue, saturationValue / 100f))
        }
        execute(CapabilityActionFactory.color(capability, model, payload), 300)
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
    ) {
        Column(
            modifier = Modifier.fillMaxSize().padding(horizontal = 26.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(Modifier.height(34.dp))
            Text(
                text = device.name,
                style = MaterialTheme.typography.titleMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(5.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                controls.forEach { control ->
                    LightModeChip(
                        text = control.title,
                        selected = control == selected,
                        modifier = Modifier.weight(1f),
                    ) { selectedName = control.name }
                }
            }
            Box(
                modifier = Modifier.weight(1f).fillMaxWidth(),
                contentAlignment = Alignment.Center,
            ) {
                AnimatedContent(
                    targetState = selected,
                    transitionSpec = {
                        (fadeIn(tween(180, delayMillis = 35)) +
                            scaleIn(tween(220, easing = FastOutSlowInEasing), initialScale = 0.94f))
                            .togetherWith(
                                fadeOut(tween(110)) +
                                    scaleOut(tween(150, easing = FastOutSlowInEasing), targetScale = 0.97f),
                            )
                    },
                    contentAlignment = Alignment.Center,
                    label = "light control transition",
                ) { control ->
                    when (control) {
                        LightControl.BRIGHTNESS -> BrightnessControl(
                            value = brightnessValue,
                            range = brightness?.range,
                            onChange = ::setBrightness,
                        )
                        LightControl.TEMPERATURE -> TemperatureControl(
                            value = temperatureValue,
                            range = temperatureRange,
                            onChange = ::setTemperature,
                        )
                        LightControl.COLOR -> AdvancedColorControl(
                            hue = hueValue,
                            saturation = saturationValue,
                            onChange = ::setColor,
                        )
                    }
                }
            }
            error?.let {
                Text(
                    text = it,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 1,
                )
            }
            if (power != null) {
                val isOn = power.value?.jsonPrimitive?.booleanOrNull == true
                Button(
                    onClick = { execute(CapabilityActionFactory.boolean(power, !isOn), 0) },
                    enabled = device.reachable && pendingInstance != power.instance,
                    modifier = Modifier.width(164.dp).height(42.dp),
                    label = { Text(if (isOn) "Выключить" else "Включить") },
                    colors = if (isOn) ButtonDefaults.buttonColors() else ButtonDefaults.filledTonalButtonColors(),
                )
            }
            Spacer(Modifier.height(9.dp))
        }
        TimeText()
    }
}

@Composable
private fun LightModeChip(text: String, selected: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val targetContainer = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainer
    val targetContent = if (selected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
    val container by animateColorAsState(targetContainer, tween(220), label = "light chip background")
    val content by animateColorAsState(targetContent, tween(220), label = "light chip content")
    Box(
        modifier = modifier
            .height(34.dp)
            .background(container, CircleShape)
            .clickable(onClick = onClick)
            .padding(horizontal = 4.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, color = content, style = MaterialTheme.typography.labelSmall, maxLines = 1)
    }
}

@Composable
private fun BrightnessControl(
    value: Float,
    range: ValueRange?,
    onChange: (Float) -> Unit,
) {
    val min = range?.min?.toFloat() ?: 0f
    val max = range?.max?.toFloat() ?: 100f
    val fraction = ((value - min) / (max - min).coerceAtLeast(1f)).coerceIn(0f, 1f)
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text("${value.roundToInt()}%", style = MaterialTheme.typography.displaySmall)
        Spacer(Modifier.height(6.dp))
        TouchSlider(
            fraction = fraction,
            colors = listOf(Color(0xFF384046), Color(0xFFFFD166)),
            description = "Яркость ${value.roundToInt()} процентов",
            onChange = onChange,
        )
        Spacer(Modifier.height(6.dp))
        Text("Коснитесь шкалы", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun TemperatureControl(
    value: Float,
    range: ValueRange?,
    onChange: (Float) -> Unit,
) {
    val min = range?.min?.toFloat() ?: 1700f
    val max = range?.max?.toFloat() ?: 6500f
    val fraction = ((value - min) / (max - min).coerceAtLeast(1f)).coerceIn(0f, 1f)
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text("${value.roundToInt()} K", style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.height(8.dp))
        TouchSlider(
            fraction = fraction,
            colors = listOf(Color(0xFFFF8A3D), Color(0xFFFFE8B0), Color(0xFF8EC5FF)),
            description = "Тон света ${value.roundToInt()} кельвин",
            onChange = onChange,
        )
        Spacer(Modifier.height(6.dp))
        Text("Тёплый  •  Нейтральный  •  Холодный", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun TouchSlider(
    fraction: Float,
    colors: List<Color>,
    description: String,
    onChange: (Float) -> Unit,
) {
    fun androidx.compose.ui.input.pointer.PointerInputScope.update(x: Float) {
        onChange((x / size.width.toFloat()).coerceIn(0f, 1f))
    }
    Canvas(
        Modifier
            .fillMaxWidth()
            .height(48.dp)
            .pointerInput(onChange) { detectTapGestures { update(it.x) } }
            .pointerInput(onChange) {
                detectDragGestures { change, _ ->
                    change.consume()
                    update(change.position.x)
                }
            }
            .semantics { contentDescription = description },
    ) {
        val inset = 14.dp.toPx()
        val y = size.height / 2f
        val start = Offset(inset, y)
        val end = Offset(size.width - inset, y)
        drawLine(
            brush = Brush.horizontalGradient(colors),
            start = start,
            end = end,
            strokeWidth = 14.dp.toPx(),
            cap = StrokeCap.Round,
        )
        val x = start.x + (end.x - start.x) * fraction.coerceIn(0f, 1f)
        drawCircle(Color.Black.copy(alpha = 0.55f), 10.dp.toPx(), Offset(x, y))
        drawCircle(Color.White, 8.dp.toPx(), Offset(x, y), style = Stroke(2.dp.toPx()))
    }
}

@Composable
private fun AdvancedColorControl(
    hue: Float,
    saturation: Float,
    onChange: (Float, Float) -> Unit,
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        ColorWheel(
            hue = hue,
            saturation = saturation,
            onChange = onChange,
            modifier = Modifier.size(142.dp),
        )
        Spacer(Modifier.height(4.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(9.dp)) {
            COLOR_PRESETS.forEach { preset ->
                Box(
                    Modifier
                        .size(25.dp)
                        .background(Color.hsv(preset.hue, preset.saturation / 100f, 1f), CircleShape)
                        .clickable { onChange(preset.hue, preset.saturation) }
                        .semantics { contentDescription = preset.name },
                )
            }
        }
    }
}

@Composable
private fun ColorWheel(
    hue: Float,
    saturation: Float,
    onChange: (Float, Float) -> Unit,
    modifier: Modifier = Modifier,
) {
    fun androidx.compose.ui.input.pointer.PointerInputScope.update(position: Offset) {
        val center = Offset(size.width / 2f, size.height / 2f)
        val dx = position.x - center.x
        val dy = position.y - center.y
        val radius = size.width.coerceAtMost(size.height) / 2f
        val distance = sqrt(dx * dx + dy * dy).coerceAtMost(radius)
        val selectedHue = ((atan2(dy, dx) * 180f / PI.toFloat()) + 360f) % 360f
        onChange(selectedHue, distance / radius * 100f)
    }
    Canvas(
        modifier
            .pointerInput(onChange) { detectTapGestures { update(it) } }
            .pointerInput(onChange) {
                detectDragGestures { change, _ ->
                    change.consume()
                    update(change.position)
                }
            }
            .semantics { contentDescription = "Цветовой круг: оттенок ${hue.roundToInt()}, насыщенность ${saturation.roundToInt()} процентов" },
    ) {
        val wheel = Brush.sweepGradient(
            listOf(Color.Red, Color.Yellow, Color.Green, Color.Cyan, Color.Blue, Color.Magenta, Color.Red),
            center = center,
        )
        drawCircle(wheel)
        drawCircle(Brush.radialGradient(listOf(Color.White, Color.Transparent), center = center))
        val radians = hue * PI.toFloat() / 180f
        val radius = size.minDimension / 2f * (saturation / 100f).coerceIn(0f, 1f)
        val thumb = Offset(center.x + cos(radians) * radius, center.y + sin(radians) * radius)
        drawCircle(Color.Black.copy(alpha = 0.55f), 8.dp.toPx(), thumb)
        drawCircle(Color.White, 7.dp.toPx(), thumb, style = Stroke(2.dp.toPx()))
    }
}

private data class ColorPreset(val name: String, val hue: Float, val saturation: Float)

private val COLOR_PRESETS = listOf(
    ColorPreset("Красный", 0f, 100f),
    ColorPreset("Оранжевый", 30f, 100f),
    ColorPreset("Зелёный", 120f, 85f),
    ColorPreset("Голубой", 190f, 90f),
    ColorPreset("Синий", 230f, 90f),
    ColorPreset("Фиолетовый", 285f, 85f),
)

private fun Capability.Range?.currentValue(): Double = this?.value?.jsonPrimitive?.doubleOrNull
    ?.coerceIn(range.min, range.max)
    ?: this?.range?.min
    ?: 0.0

private fun Capability.ColorSetting?.currentTemperature(): Double {
    val range = this?.temperatureRange ?: return 4600.0
    val current = if (instance == "temperature_k") value?.jsonPrimitive?.doubleOrNull else null
    return (current ?: ((range.min + range.max) / 2.0)).coerceIn(range.min, range.max)
}

private fun Capability.ColorSetting?.currentHue(): Float {
    if (this == null) return 0f
    return when (instance) {
        "hsv" -> (value as? JsonObject)?.get("h")?.jsonPrimitive?.floatOrNull ?: 0f
        "rgb" -> value?.jsonPrimitive?.intOrNull?.let(::rgbToHue) ?: 0f
        else -> 0f
    }.let { (it + 360f) % 360f }
}

private fun Capability.ColorSetting?.currentSaturation(): Float {
    if (this == null) return 100f
    return when (instance) {
        "hsv" -> (value as? JsonObject)?.get("s")?.jsonPrimitive?.floatOrNull ?: 100f
        "rgb" -> value?.jsonPrimitive?.intOrNull?.let(::rgbSaturation) ?: 100f
        else -> 100f
    }.coerceIn(0f, 100f)
}

private fun rgbSaturation(rgb: Int): Float {
    val red = ((rgb shr 16) and 0xFF) / 255f
    val green = ((rgb shr 8) and 0xFF) / 255f
    val blue = (rgb and 0xFF) / 255f
    val high = max(red, max(green, blue))
    val low = minOf(red, green, blue)
    return if (high == 0f) 0f else ((high - low) / high * 100f)
}
