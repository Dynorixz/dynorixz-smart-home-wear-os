package com.dynorixz.smarthome.ui.device

import com.dynorixz.smarthome.domain.Device
import com.dynorixz.smarthome.domain.DeviceProperty
import java.util.Locale

internal enum class ReadingTone { CALM, WARM, ALERT, NEUTRAL }

internal data class SensorReading(
    val instance: String,
    val title: String,
    val value: String,
    val unit: String = "",
    val tone: ReadingTone = ReadingTone.NEUTRAL,
    val available: Boolean = true,
    val fraction: Float? = null,
    val updatedAtSeconds: Double? = null,
) {
    val spokenValue: String get() = if (unit.isBlank()) value else "$value $unit"
}

internal fun Device.sensorReadings(): List<SensorReading> = properties.mapNotNull { it.reading() }
    .sortedBy { when (it.instance) {
        "water_leak", "smoke", "gas" -> 0
        "open", "motion", "vibration" -> 1
        "temperature", "humidity", "co2_level" -> 2
        "battery_level", "signal_level", "signal_strength" -> 9
        else -> 4
    } }

internal fun List<SensorReading>.primaryReading(): SensorReading? =
    firstOrNull { it.instance !in setOf("battery_level", "signal_level", "signal_strength") } ?: firstOrNull()

internal fun DeviceProperty.reading(): SensorReading? = when (this) {
    is DeviceProperty.Unknown -> null
    is DeviceProperty.FloatValue -> {
        val number = value?.takeIf { it.isFinite() }
        SensorReading(instance, instance.controlLabel(), number?.deviceNumber() ?: "Нет данных",
            unit = if (number == null) "" else unit.readableUnit(),
            available = number != null,
            tone = when (instance) {
                "temperature", "illumination" -> ReadingTone.WARM
                "battery_level" -> if (number != null && number <= 15) ReadingTone.ALERT else ReadingTone.CALM
                else -> ReadingTone.CALM
            },
            fraction = if (unit == "unit.percent" && number != null && number in 0.0..100.0) (number / 100).toFloat() else null,
            updatedAtSeconds = lastUpdatedSeconds,
        )
    }
    is DeviceProperty.Event -> {
        val known = when (instance) {
            "open" -> when (value) { "opened", "open" -> "Открыто"; "closed", "close" -> "Закрыто"; else -> null }
            "motion" -> when (value) { "detected" -> "Есть движение"; "not_detected" -> "Нет движения"; else -> null }
            "water_leak" -> when (value) { "leak" -> "Протечка"; "dry" -> "Сухо"; else -> null }
            "smoke" -> when (value) { "detected", "high" -> "Обнаружен дым"; "not_detected", "normal" -> "Дыма нет"; else -> null }
            "gas" -> when (value) { "detected", "high" -> "Обнаружен газ"; "not_detected", "normal" -> "Газа нет"; else -> null }
            "battery_level" -> when (value) { "low" -> "Низкий заряд"; "normal" -> "В норме"; else -> null }
            "water_level", "food_level" -> when (value) { "empty" -> "Пусто"; "low" -> "Мало"; "normal" -> "В норме"; else -> null }
            "button" -> when (value) { "click" -> "Нажатие"; "double_click" -> "Двойное нажатие"; "long_press" -> "Удерживание"; else -> null }
            "vibration" -> when (value) { "vibration" -> "Вибрация"; "tilt" -> "Наклон"; "fall" -> "Падение"; else -> null }
            else -> null
        }
        val alert = (instance in setOf("water_leak", "smoke", "gas") && value in setOf("leak", "detected", "high")) ||
            (instance == "battery_level" && value == "low")
        SensorReading(instance, instance.controlLabel(), known ?: if (value == null) "Нет данных" else "Неизвестное состояние",
            tone = when { alert -> ReadingTone.ALERT; value == "opened" || value == "detected" -> ReadingTone.WARM; known != null -> ReadingTone.CALM; else -> ReadingTone.NEUTRAL },
            available = known != null, updatedAtSeconds = lastUpdatedSeconds,
        )
    }
}

internal fun Double.deviceNumber(): String = if (this % 1.0 == 0.0) toLong().toString()
    else String.format(Locale.forLanguageTag("ru-RU"), "%.1f", this)

internal fun String?.readableUnit(): String = when (this) {
    "unit.percent" -> "%"
    "unit.temperature.celsius" -> "°C"
    "unit.temperature.kelvin" -> "K"
    "unit.illumination.lux" -> "лк"
    "unit.pressure.mmhg" -> "мм рт. ст."
    "unit.pressure.pascal" -> "Па"
    "unit.pressure.atm" -> "атм"
    "unit.pressure.bar" -> "бар"
    "unit.ppm" -> "ppm"
    "unit.density.mcg_m3" -> "мкг/м³"
    "unit.watt" -> "Вт"
    "unit.kilowatt_hour" -> "кВт·ч"
    "unit.volt" -> "В"
    "unit.ampere" -> "А"
    "unit.cubic_meter" -> "м³"
    "unit.gigacalorie" -> "Гкал"
    null, "" -> ""
    else -> this // Preserve unknown units rather than guessing their physical meaning.
}

internal fun String.controlLabel(): String = when (this) {
    "on" -> "Питание"
    "brightness" -> "Яркость"
    "temperature", "temperature_k" -> "Температура"
    "volume" -> "Громкость"
    "channel" -> "Канал"
    "humidity" -> "Влажность"
    "open" -> "Открытие"
    "heat" -> "Нагрев"
    "fan_speed" -> "Скорость вентилятора"
    "thermostat" -> "Режим климата"
    "work_speed" -> "Скорость работы"
    "program" -> "Программа"
    "input_source" -> "Источник"
    "cleanup_mode" -> "Режим уборки"
    "tea_mode" -> "Вид чая"
    "coffee_mode" -> "Вид кофе"
    "mute" -> "Без звука"
    "oscillation", "swing" -> "Поворот"
    "backlight" -> "Подсветка"
    "ionization" -> "Ионизация"
    "pause" -> "Пауза"
    "keep_warm" -> "Поддержание тепла"
    "battery_level" -> "Батарея"
    "signal_level", "signal_strength" -> "Уровень сигнала"
    "pressure" -> "Давление"
    "co2_level" -> "Углекислый газ"
    "water_level" -> "Уровень воды"
    "food_level" -> "Уровень корма"
    "illumination" -> "Освещённость"
    "motion" -> "Движение"
    "smoke" -> "Дым"
    "gas" -> "Газ"
    "water_leak" -> "Протечка"
    "vibration" -> "Вибрация"
    "button" -> "Кнопка"
    "pm1_density" -> "Частицы PM1"
    "pm2.5_density" -> "Частицы PM2,5"
    "pm10_density" -> "Частицы PM10"
    "voltage" -> "Напряжение"
    "amperage" -> "Сила тока"
    "power" -> "Мощность"
    "electricity_meter" -> "Электроэнергия"
    "gas_meter" -> "Расход газа"
    "water_meter" -> "Расход воды"
    "heat_meter" -> "Теплоэнергия"
    "meter" -> "Показания счётчика"
    "low" -> "Низкий"
    "medium", "normal" -> "Средний"
    "high" -> "Высокий"
    "auto" -> "Авто"
    "cool" -> "Охлаждение"
    "heat_mode", "heating" -> "Обогрев"
    "fan_only" -> "Вентиляция"
    "dry" -> "Осушение"
    "eco" -> "Эко"
    "quiet", "silent" -> "Тихий"
    "turbo" -> "Турбо"
    "min" -> "Минимум"
    "max" -> "Максимум"
    else -> replace('_', ' ').replaceFirstChar(Char::uppercase)
}

internal fun Device.typeLabel(): String = when {
    type.contains("sensor") -> "Датчик"
    type.contains("light") -> "Освещение"
    type.contains("thermostat.ac") -> "Кондиционер"
    type.contains("thermostat") -> "Термостат"
    type.contains("socket") -> "Розетка"
    type.contains("switch") -> "Выключатель"
    type.contains("smart_speaker") -> "Умная колонка"
    type.contains("humidifier") -> "Увлажнитель"
    type.contains("purifier") -> "Очиститель воздуха"
    type.contains("vacuum") -> "Робот-пылесос"
    type.contains("kettle") -> "Чайник"
    type.contains("fan") -> "Вентилятор"
    type.contains("heater") -> "Обогреватель"
    type.contains("tv") || type.contains("media_device") -> "Мультимедиа"
    type.contains("camera") -> "Камера"
    type.contains("openable") -> "Шторы и жалюзи"
    else -> "Устройство"
}
