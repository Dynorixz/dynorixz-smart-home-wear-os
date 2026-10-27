package com.dynorixz.smarthome.ui.home

import android.app.Application
import android.graphics.Bitmap
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.longClick
import androidx.compose.ui.unit.dp
import androidx.wear.compose.foundation.lazy.rememberScalingLazyListState
import com.dynorixz.smarthome.data.local.AppSettings
import com.dynorixz.smarthome.data.local.FavoriteKind
import com.dynorixz.smarthome.data.local.FavoriteRef
import com.dynorixz.smarthome.data.local.ThemePreference
import com.dynorixz.smarthome.domain.Capability
import com.dynorixz.smarthome.domain.Device
import com.dynorixz.smarthome.domain.Room
import com.dynorixz.smarthome.domain.SmartHome
import com.dynorixz.smarthome.ui.DynorixzTheme
import java.io.File
import kotlin.test.assertEquals
import kotlinx.serialization.json.JsonPrimitive
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class, qualifiers = "w192dp-h192dp-round-xhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class GalleryHomeScreenTest {
    @get:Rule val compose = createComposeRule()

    @Test
    fun powerActionDoesNotOpenDevice() {
        var opens = 0
        var toggles = 0
        compose.setContent {
            DynorixzTheme(dynamicColor = false, theme = ThemePreference.DARK) {
                GalleryDeviceCard(
                    fixtureDevice(), "Спальня", true, true,
                    onOpen = { opens++ }, onFavorite = {}, onToggle = { toggles++ },
                )
            }
        }
        compose.onNodeWithContentDescription("Выключить: Лампа").performClick()
        compose.runOnIdle {
            assertEquals(1, toggles)
            assertEquals(0, opens)
        }
        compose.onNodeWithText("Лампа").performClick()
        compose.runOnIdle { assertEquals(1, opens) }
    }

    @Test
    fun offlinePowerControlIsDisabled() {
        compose.setContent {
            DynorixzTheme(dynamicColor = false) {
                GalleryDeviceCard(fixtureDevice(), null, false, false, {}, {}, {})
            }
        }
        compose.onNodeWithContentDescription("Выключить: Лампа").assertIsNotEnabled()
    }

    @Test
    fun unreachablePowerControlIsDisabled() {
        compose.setContent {
            DynorixzTheme(dynamicColor = false) {
                GalleryDeviceCard(fixtureDevice().copy(reachable = false), null, false, true, {}, {}, {})
            }
        }
        compose.onNodeWithContentDescription("Выключить: Лампа").assertIsNotEnabled()
    }

    @Test
    fun holdingCardChangesFavoriteWithoutSendingCommand() {
        var favorites = 0
        var commands = 0
        compose.setContent {
            DynorixzTheme(dynamicColor = false) {
                GalleryDeviceCard(fixtureDevice(), null, false, true, {}, { favorites++ }, { commands++ })
            }
        }
        compose.onNodeWithText("Лампа").performTouchInput { longClick() }
        compose.runOnIdle {
            assertEquals(1, favorites)
            assertEquals(0, commands)
        }
    }

    @Test
    fun renderSmallRoundHome() = renderHome("gallery-home-192")

    @Test
    @Config(qualifiers = "w227dp-h227dp-round-xhdpi")
    fun renderLargeRoundHome() = renderHome("gallery-home-227", size = 227)

    @Test
    fun renderLightTheme() = renderHome("gallery-home-light", theme = ThemePreference.LIGHT)

    @Test
    fun renderLargeFont() {
        RuntimeEnvironment.setFontScale(1.3f)
        renderHome(
            "gallery-large-font", firstItem = 1,
            device = fixtureDevice().copy(name = "Освещение спальни"),
        )
    }

    @Test
    fun renderLongNameAndOfflineState() = renderHome(
        "gallery-offline-long-name", offline = true,
        device = fixtureDevice().copy(name = "Очень длинное название устройства в спальне", reachable = false),
        firstItem = 2,
    )

    @Test
    fun renderSpeaker() = renderHome(
        "gallery-speaker", device = fixtureDevice().copy(type = "devices.types.smart_speaker", name = "Станция"),
        firstItem = 1,
    )

    private fun renderHome(
        name: String,
        size: Int = 192,
        theme: ThemePreference = ThemePreference.DARK,
        offline: Boolean = false,
        device: Device = fixtureDevice(),
        firstItem: Int = 0,
    ) {
        val home = SmartHome(
            rooms = listOf(Room("r", "Спальня", "h", listOf(device.id))),
            groups = emptyList(), devices = listOf(device), scenarios = emptyList(), households = emptyList(),
            requestId = null, updatedAtMillis = 0L, isStale = offline,
        )
        compose.setContent {
            Box(Modifier.size(size.dp)) {
                DynorixzTheme(dynamicColor = false, theme = theme) {
                    GalleryHomeScreen(
                        state = HomeUiState(
                            loading = false, home = home, offline = offline,
                            favorites = listOf(FavoriteRef(FavoriteKind.DEVICE, device.id)),
                            settings = AppSettings(theme = theme),
                        ),
                        listState = rememberScalingLazyListState(initialCenterItemIndex = firstItem),
                        onSettings = {}, onDevices = {}, onRooms = {}, onScenarios = {}, onGroups = {},
                        onDevice = {}, onFavorite = {}, onToggle = {}, onScenario = {}, onRefresh = {},
                    )
                }
            }
        }
        compose.waitForIdle()
        val output = File("build/gallery-screenshots/$name.png").apply { parentFile?.mkdirs() }
        output.outputStream().use {
            compose.onRoot().captureToImage().asAndroidBitmap().compress(Bitmap.CompressFormat.PNG, 100, it)
        }
    }
}

internal fun fixtureDevice() = Device(
    id = "d", name = "Лампа", type = "devices.types.light", roomId = "r", householdId = "h",
    groupIds = emptyList(),
    capabilities = listOf(Capability.OnOff("on", true, false, JsonPrimitive(true), null)),
    properties = emptyList(), reachable = true,
)
