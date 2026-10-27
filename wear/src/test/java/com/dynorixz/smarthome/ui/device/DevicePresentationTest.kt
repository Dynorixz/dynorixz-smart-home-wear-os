package com.dynorixz.smarthome.ui.device

import com.dynorixz.smarthome.domain.*
import com.dynorixz.smarthome.ui.home.galleryStatus
import kotlin.test.*
import org.junit.Test
import kotlinx.serialization.json.JsonPrimitive

class DevicePresentationTest {
    @Test fun openingAndMotionUseRussianStates() {
        assertEquals("Закрыто", event("open", "closed").reading()?.value)
        assertEquals("Открыто", event("open", "opened").reading()?.value)
        assertEquals("Есть движение", event("motion", "detected").reading()?.value)
        assertEquals("Нет движения", event("motion", "not_detected").reading()?.value)
    }
    @Test fun leakAndBatteryHaveContextSpecificValues() {
        assertEquals("Сухо", event("water_leak", "dry").reading()?.value)
        assertEquals(ReadingTone.ALERT, event("water_leak", "leak").reading()?.tone)
        assertEquals("В норме", event("battery_level", "normal").reading()?.value)
        assertEquals("Осушение", "dry".controlLabel())
    }
    @Test fun missingSignalHasNoMadeUpZeroOrUnit() {
        val signal = numeric("signal_level", "unit.percent", null).reading()!!
        assertEquals("Уровень сигнала", signal.title)
        assertEquals("Нет данных", signal.spokenValue)
        assertFalse(signal.available)
        assertNull(signal.fraction)
    }
    @Test fun unitsAndDecimalSeparatorAreReadable() {
        assertEquals("4 лк", numeric("illumination", "unit.illumination.lux", 4.0).reading()?.spokenValue)
        assertEquals("20,5 °C", numeric("temperature", "unit.temperature.celsius", 20.5).reading()?.spokenValue)
        assertEquals("35 мкг/м³", numeric("pm2.5_density", "unit.density.mcg_m3", 35.0).reading()?.spokenValue)
    }
    @Test fun batteryDoesNotReplaceTheMainSensorReading() {
        val device = sensorFixture("Окно", listOf(numeric("battery_level", "unit.percent", 100.0), event("open", "closed")))
        assertEquals("open", device.sensorReadings().primaryReading()?.instance)
        assertEquals("Закрыто", device.galleryStatus())
    }
    @Test fun unknownPrimaryStateDoesNotPretendToBeClosedOrNormal() {
        val device = sensorFixture("Окно", listOf(event("open", "new_future_state"), numeric("battery_level", "unit.percent", 100.0)))
        assertEquals("Неизвестное состояние", device.sensorReadings().primaryReading()?.value)
        assertFalse(device.sensorReadings().primaryReading()!!.available)
    }
    @Test fun numericZeroIsARealReadingAndInvalidNumberIsUnavailable() {
        assertEquals("0 лк", numeric("illumination", "unit.illumination.lux", 0.0).reading()?.spokenValue)
        assertFalse(numeric("temperature", "unit.temperature.celsius", Double.NaN).reading()!!.available)
    }
    @Test fun sensorIsNotListedAsPoweredOff() {
        val sensor = sensorFixture("Датчик", listOf(event("open", "closed")))
        val off = sensor.copy(id = "socket", capabilities = listOf(Capability.OnOff("on", true, true, JsonPrimitive(false), null)))
        assertEquals(listOf(off), filterCatalogDevices(listOf(sensor, off), "", CatalogFilter.OFF, null, null))
    }
    @Test fun precisionIsAppliedRelativeToMinimumAndClamped() {
        val range = ValueRange(16.0, 30.0, .5)
        assertEquals(20.5, snapDeviceValue(20.6, range))
        assertEquals(30.0, snapDeviceValue(100.0, range))
        assertEquals(16.0, snapDeviceValue(-5.0, range))
        assertEquals(3.0, snapDeviceValue(3.1, ValueRange(1.0, 9.0, 2.0)))
    }
    private fun event(instance: String, value: String?) = DeviceProperty.Event(instance, value, emptyList(), true, true, null)
    private fun numeric(instance: String, unit: String, value: Double?) = DeviceProperty.FloatValue(instance, unit, value, true, true, null)
}

internal fun sensorFixture(name: String = "Окно в спальне", properties: List<DeviceProperty> = listOf(
    DeviceProperty.FloatValue("battery_level", "unit.percent", 100.0, true, true, null),
    DeviceProperty.Event("open", "closed", listOf("opened", "closed"), true, true, null),
    DeviceProperty.FloatValue("signal_level", "unit.percent", null, true, true, null),
)) = Device("sensor", name, "devices.types.sensor.open", null, "h", emptyList(), emptyList(), properties, true)
