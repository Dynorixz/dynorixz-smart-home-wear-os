package com.dynorixz.smarthome.tile

import com.dynorixz.smarthome.data.local.*
import com.dynorixz.smarthome.domain.*
import com.dynorixz.smarthome.ui.device.lightFixture
import com.dynorixz.smarthome.ui.device.sensorFixture
import kotlinx.serialization.json.JsonPrimitive
import kotlin.test.*
import org.junit.Test

class TilePresentationTest {
    @Test fun homeActionsKeepSelectionOrderAndAdvertiseTheRightAction() {
        val refs = listOf(FavoriteRef(FavoriteKind.SCENARIO, "night"), FavoriteRef(FavoriteKind.DEVICE, "light"), FavoriteRef(FavoriteKind.DEVICE, "sensor"))
        val model = tileModel(HomeTileKind.HOME, tileHome(), refs, now = 1_000_000)
        assertEquals(refs, model.items.map { it.ref })
        assertEquals(listOf(TileTap.SCENARIO, TileTap.TOGGLE, TileTap.OPEN), model.items.map { it.tap })
    }
    @Test fun lightsRespectExplicitSelectionInsteadOfTakingTheFirstDevice() {
        val refs = listOf(FavoriteRef(FavoriteKind.DEVICE, "lamp2"), FavoriteRef(FavoriteKind.DEVICE, "light"))
        val model = tileModel(HomeTileKind.LIGHTS, tileHome(), refs)
        assertEquals(refs, model.items.map { it.ref })
        assertTrue(model.items.all { it.source == HomeTileKind.LIGHTS })
    }
    @Test fun automaticLightsPrioritizeFavorites() {
        assertEquals("lamp2", tileModel(HomeTileKind.LIGHTS, tileHome(), favorites = listOf(FavoriteRef(FavoriteKind.DEVICE, "lamp2"))).items.first().ref.id)
    }
    @Test fun sensorsShowPhysicalValuesAndOpenInsteadOfSendingPower() {
        val item = tileModel(HomeTileKind.SENSORS, tileHome()).items.single()
        assertEquals("25,1 °C", item.reading)
        assertEquals("Влажность: 48 %", item.secondary)
        assertEquals(TileTap.OPEN, item.tap)
    }
    @Test fun staleAndMissingHomeAreExplicit() {
        assertEquals("кэш", tileModel(HomeTileKind.LIGHTS, tileHome().copy(isStale = true)).freshness)
        val model = tileModel(HomeTileKind.HOME, null, listOf(FavoriteRef(FavoriteKind.DEVICE, "light")))
        assertFalse(model.hasHome)
        assertTrue(model.items.isEmpty())
    }
    @Test fun removedActionsAreNotTurnedIntoAnUnrelatedDevice() {
        assertTrue(tileModel(HomeTileKind.HOME, tileHome(), listOf(FavoriteRef(FavoriteKind.DEVICE, "deleted"))).items.isEmpty())
    }
    @Test fun offlineLightOpensDetailsAndDoesNotAdvertiseAToggle() {
        val home = tileHome().let { it.copy(devices = listOf(it.devices.first().copy(reachable = false))) }
        val item = tileModel(HomeTileKind.LIGHTS, home).items.single()
        assertEquals(TileTap.OPEN, item.tap)
        assertEquals("Недоступно", item.status)
    }
    @Test fun unknownPowerIsNotCalledOff() {
        val light = lightFixture().copy(capabilities = listOf(Capability.OnOff("on", true, true, null, null)))
        val item = tileModel(HomeTileKind.LIGHTS, tileHome().copy(devices = listOf(light))).items.single()
        assertNotEquals("Выключено", item.status)
    }
}

internal fun tileHome() = SmartHome(emptyList(), emptyList(), listOf(lightFixture(), lightFixture().copy(id = "lamp2", name = "Торшер"),
    sensorFixture("Климат в спальне", listOf(DeviceProperty.FloatValue("temperature", "unit.temperature.celsius", 25.1, true, true, null),
        DeviceProperty.FloatValue("humidity", "unit.percent", 48.0, true, true, null)))), listOf(Scenario("night", "Доброй ночи")), emptyList(), null, 1_000_000, false)
