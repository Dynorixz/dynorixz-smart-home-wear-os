package com.dynorixz.smarthome.tile

import android.content.ComponentName
import android.util.Log
import androidx.wear.protolayout.ActionBuilders.launchAction
import androidx.wear.protolayout.ActionBuilders.stringExtra
import androidx.wear.protolayout.DimensionBuilders.dp
import androidx.wear.protolayout.DimensionBuilders.expand
import androidx.wear.protolayout.DimensionBuilders.weight
import androidx.wear.protolayout.LayoutElementBuilders.Column
import androidx.wear.protolayout.LayoutElementBuilders.LayoutElement
import androidx.wear.protolayout.LayoutElementBuilders.Spacer
import androidx.wear.protolayout.TimelineBuilders.Timeline
import androidx.wear.protolayout.material3.ButtonDefaults.filledButtonColors
import androidx.wear.protolayout.material3.ButtonDefaults.filledTonalButtonColors
import androidx.wear.protolayout.material3.MaterialScope
import androidx.wear.protolayout.material3.Typography
import androidx.wear.protolayout.material3.buttonGroup
import androidx.wear.protolayout.material3.primaryLayout
import androidx.wear.protolayout.material3.text
import androidx.wear.protolayout.material3.textButton
import androidx.wear.protolayout.modifiers.LayoutModifier
import androidx.wear.protolayout.modifiers.clickable
import androidx.wear.protolayout.modifiers.contentDescription
import androidx.wear.protolayout.types.layoutString
import androidx.wear.tiles.Material3TileService
import androidx.wear.tiles.RequestBuilders.TileRequest
import androidx.wear.tiles.TileBuilders.Tile
import com.dynorixz.smarthome.data.local.FavoriteKind
import com.dynorixz.smarthome.domain.Capability
import com.dynorixz.smarthome.domain.Device
import com.dynorixz.smarthome.domain.SmartHomeRepository
import com.dynorixz.smarthome.di.wearSurfaceDependencies
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.jsonPrimitive

class LightsTileService : Material3TileService(allowDynamicTheme = true) {
    private val repository: SmartHomeRepository by lazy(LazyThreadSafetyMode.NONE) {
        wearSurfaceDependencies().repository()
    }

    override suspend fun MaterialScope.tileResponse(requestParams: TileRequest): Tile = runCatching {
        // Tile requests have a strict deadline. Render cached state immediately;
        // network refreshes are requested by the app after normal sync/actions.
        val home = withTimeoutOrNull(1_500) { repository.observeHome().first() }
        buildTile(home?.devices.orEmpty().filter(Device::isLight).take(3))
    }.getOrElse { error ->
        Log.e(TAG, "Unable to render lights tile", error)
        buildTile(emptyList(), "Откройте приложение")
    }

    private fun MaterialScope.buildTile(
        lights: List<Device>,
        emptyText: String = "Нет световых устройств",
    ): Tile {
        val enabled = lights.count(Device::isOn)
        return Tile.Builder()
            .setFreshnessIntervalMillis(300_000)
            .setTileTimeline(
                Timeline.fromLayoutElement(
                    primaryLayout(
                        titleSlot = { text("Свет · $enabled/${lights.size}".layoutString) },
                        mainSlot = {
                            if (lights.isEmpty()) {
                                text(emptyText.layoutString)
                            } else {
                                lightsGrid(lights)
                            }
                        },
                    ),
                ),
            )
            .build()
    }

    /** A two-row layout keeps touch targets large and follows the round display edge. */
    private fun MaterialScope.lightsGrid(lights: List<Device>): LayoutElement {
        if (lights.size <= 2) {
            return lightButtonGroup(lights, expand())
        }

        return Column.Builder()
            .setWidth(expand())
            .setHeight(expand())
            .addContent(lightButtonGroup(lights.take(2), weight(1f)))
            .addContent(Spacer.Builder().setHeight(dp(6f)).build())
            .addContent(lightButtonGroup(lights.drop(2), weight(1f)))
            .build()
    }

    private fun MaterialScope.lightButtonGroup(
        lights: List<Device>,
        height: androidx.wear.protolayout.DimensionBuilders.ContainerDimension,
    ): LayoutElement = buttonGroup(
        width = expand(),
        height = height,
    ) {
        lights.forEach { device ->
            buttonGroupItem {
                lightButton(
                    device = device,
                    maxLabelLength = if (lights.size > 1) 9 else 18,
                )
            }
        }
    }

    private fun MaterialScope.lightButton(
        device: Device,
        maxLabelLength: Int,
    ): LayoutElement {
        val isOn = device.isOn()
        val label = device.name.compactTileLabel(maxLabelLength)
        return textButton(
            width = expand(),
            height = expand(),
            colors = if (isOn) filledButtonColors() else filledTonalButtonColors(),
            modifier = LayoutModifier.contentDescription(
                "$label, ${if (isOn) "включено" else "выключено"}",
            ),
            onClick = clickable(
                action = launchAction(
                    ComponentName(
                        this@LightsTileService,
                        TileActionActivity::class.java,
                    ),
                    mapOf(
                        TileActionActivity.EXTRA_KIND to stringExtra(FavoriteKind.DEVICE.name),
                        TileActionActivity.EXTRA_ID to stringExtra(device.id),
                        TileActionActivity.EXTRA_SOURCE to stringExtra(TileActionActivity.SOURCE_LIGHTS),
                    ),
                ),
            ),
            labelContent = {
                text(
                    label.layoutString,
                    typography = Typography.LABEL_MEDIUM,
                    maxLines = 1,
                )
            },
        )
    }

    companion object { private const val TAG = "LightsTile" }
}

private fun Device.isLight(): Boolean = "light" in type.removePrefix("devices.types.")

private fun Device.isOn(): Boolean = capabilities.filterIsInstance<Capability.OnOff>()
    .firstOrNull()?.value?.jsonPrimitive?.booleanOrNull == true

private fun String.compactTileLabel(maxLength: Int = 13): String =
    trim().ifEmpty { "Свет" }.let { label ->
        if (label.length <= maxLength) label else label.take(maxLength - 1).trimEnd() + "…"
    }
