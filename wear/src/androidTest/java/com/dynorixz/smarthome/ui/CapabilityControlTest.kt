package com.dynorixz.smarthome.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.dynorixz.smarthome.domain.Capability
import com.dynorixz.smarthome.domain.CapabilityAction
import com.dynorixz.smarthome.domain.Scenario
import com.dynorixz.smarthome.domain.ValueRange
import kotlin.test.assertEquals
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Rule
import org.junit.Test

class CapabilityControlTest {
    @get:Rule val compose = createComposeRule()

    @Test
    fun onOffControlEmitsRealActionModel() {
        var emitted: CapabilityAction? = null
        compose.setContent {
            DynorixzTheme(dynamicColor = false) {
                CapabilityControl(
                    Capability.OnOff("on", true, false, JsonPrimitive(false), null),
                    pending = false,
                    execute = { action, _ -> emitted = action },
                )
            }
        }

        compose.onNodeWithText("Выключено").assertIsDisplayed().performClick()
        compose.runOnIdle {
            assertEquals(Capability.ON_OFF, emitted?.type)
            assertEquals(true, emitted?.value?.jsonPrimitive?.boolean)
        }
    }

    @Test
    fun brightnessExposesWearFriendlyValue() {
        compose.setContent {
            DynorixzTheme(dynamicColor = false) {
                CapabilityControl(
                    Capability.Range(
                        instance = "brightness",
                        unit = "unit.percent",
                        randomAccess = true,
                        range = ValueRange(0.0, 100.0, 1.0),
                        retrievable = true,
                        reportable = false,
                        value = JsonPrimitive(42),
                        lastUpdatedSeconds = null,
                    ),
                    pending = false,
                    execute = { _, _ -> },
                )
            }
        }
        compose.onNodeWithText("Яркость").assertIsDisplayed()
        compose.onNodeWithContentDescription("Значение 42 %").assertIsDisplayed()
    }

    @Test
    fun colorTemperatureExposesWearFriendlyValue() {
        compose.setContent {
            DynorixzTheme(dynamicColor = false) {
                CapabilityControl(
                    Capability.ColorSetting(
                        instance = "temperature_k",
                        colorModel = null,
                        temperatureRange = ValueRange(2700.0, 6500.0, 100.0),
                        scenes = emptyList(),
                        retrievable = true,
                        reportable = false,
                        value = JsonPrimitive(3000),
                        lastUpdatedSeconds = null,
                    ),
                    pending = false,
                    execute = { _, _ -> },
                )
            }
        }
        compose.onNodeWithContentDescription("Значение 3000 K").assertIsDisplayed()
    }

    @Test
    fun modeControlEmitsAction() {
        var mode: CapabilityAction? = null
        compose.setContent {
            DynorixzTheme(dynamicColor = false) {
                CapabilityControl(
                    Capability.Mode(
                        instance = "fan_speed",
                        modes = listOf("low", "high"),
                        retrievable = true,
                        reportable = false,
                        value = JsonPrimitive("low"),
                        lastUpdatedSeconds = null,
                    ),
                    pending = false,
                    execute = { action, _ -> mode = action },
                )
            }
        }
        compose.onNodeWithText("Высокий").performClick()
        compose.runOnIdle { assertEquals(JsonPrimitive("high"), mode?.value) }
    }

    @Test
    fun scenarioControlRunsScenario() {
        var scenarioRuns = 0
        compose.setContent {
            DynorixzTheme(dynamicColor = false) {
                ScenarioActionButton(
                    scenario = Scenario("s1", "Спокойной ночи"),
                    enabled = true,
                    onExecute = { scenarioRuns++ },
                    onFavorite = {},
                )
            }
        }
        compose.onNodeWithText("Спокойной ночи").performClick()
        compose.runOnIdle { assertEquals(1, scenarioRuns) }
    }

    @Test
    fun offlineStateIsExplicit() {
        compose.setContent {
            DynorixzTheme(dynamicColor = false) { OfflineNotice() }
        }
        compose.onNodeWithText(
            "Нет подключения · показываем последнее известное состояние",
        ).assertIsDisplayed()
    }
}
