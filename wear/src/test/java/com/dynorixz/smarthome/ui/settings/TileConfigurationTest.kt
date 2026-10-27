package com.dynorixz.smarthome.ui.settings

import android.app.Application
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createComposeRule
import com.dynorixz.smarthome.data.local.*
import com.dynorixz.smarthome.tile.tileHome
import com.dynorixz.smarthome.ui.DynorixzTheme
import kotlin.test.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class, qualifiers = "w192dp-h192dp-round-xhdpi")
class TileConfigurationTest {
    @get:Rule val compose = createComposeRule()
    @Test fun lightsConfigurationOnlyOffersLightDevices() {
        compose.setContent { DynorixzTheme(false, ThemePreference.DARK) { TileConfigurationScreen(SettingsUiState(home = tileHome()), HomeTileKind.LIGHTS) { _, _ -> } } }
        compose.onNode(hasScrollToIndexAction()).performScrollToNode(hasText("Люстра"))
        compose.onNodeWithText("Люстра").assertExists()
        compose.onNodeWithText("Климат в спальне").assertDoesNotExist()
        compose.onNodeWithText("Доброй ночи").assertDoesNotExist()
    }
    @Test fun pickingTwoLightsKeepsBothAndTheOrderOfSelection() {
        var emitted: List<FavoriteRef> = emptyList()
        var kind: HomeTileKind? = null
        compose.setContent { DynorixzTheme(false, ThemePreference.DARK) { TileConfigurationScreen(SettingsUiState(home = tileHome()), HomeTileKind.LIGHTS) { k, refs -> kind = k; emitted = refs } } }
        compose.onNode(hasScrollToIndexAction()).performScrollToNode(hasText("Торшер"))
        compose.onNodeWithText("Торшер").performClick()
        compose.onNode(hasScrollToIndexAction()).performScrollToNode(hasText("Люстра"))
        compose.onNodeWithText("Люстра").performClick()
        compose.runOnIdle { assertEquals(HomeTileKind.LIGHTS, kind); assertEquals(listOf("lamp2", "light"), emitted.map { it.id }) }
    }
}
