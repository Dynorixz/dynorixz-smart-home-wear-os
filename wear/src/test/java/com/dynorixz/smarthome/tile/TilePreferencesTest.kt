package com.dynorixz.smarthome.tile

import android.app.Application
import com.dynorixz.smarthome.data.local.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlin.test.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class TilePreferencesTest {
    @Test fun selectionsPreserveOrderAndRemainIndependent() = runTest {
        val prefs = UserPreferences(RuntimeEnvironment.getApplication())
        val home = listOf(FavoriteRef(FavoriteKind.SCENARIO, "z"), FavoriteRef(FavoriteKind.DEVICE, "a"))
        val lights = listOf(FavoriteRef(FavoriteKind.DEVICE, "lamp2"), FavoriteRef(FavoriteKind.DEVICE, "lamp1"))
        prefs.setTileFavorites(home)
        prefs.setTileSelection(HomeTileKind.LIGHTS, lights)
        prefs.setTileSelection(HomeTileKind.SENSORS, listOf(FavoriteRef(FavoriteKind.DEVICE, "climate")))
        assertEquals(home, prefs.tileFavorites.first())
        assertEquals(lights, prefs.tileSelections.first()[HomeTileKind.LIGHTS])
        assertEquals("climate", prefs.tileSelections.first()[HomeTileKind.SENSORS]!!.single().id)
    }
    @Test fun duplicateAndUnsupportedReferencesDoNotConsumeTheSensorLimit() = runTest {
        val prefs = UserPreferences(RuntimeEnvironment.getApplication())
        val refs = listOf(FavoriteRef(FavoriteKind.SCENARIO, "night"), FavoriteRef(FavoriteKind.DEVICE, "s1"),
            FavoriteRef(FavoriteKind.DEVICE, "s1"), FavoriteRef(FavoriteKind.DEVICE, "s2"), FavoriteRef(FavoriteKind.DEVICE, "s3"))
        prefs.setTileSelection(HomeTileKind.SENSORS, refs)
        assertEquals(listOf("s1", "s2"), prefs.tileSelections.first()[HomeTileKind.SENSORS]!!.map { it.id })
    }
}
