package com.dynorixz.smarthome.tile

import android.content.ComponentName
import android.util.Log
import androidx.wear.protolayout.ActionBuilders.launchAction
import androidx.wear.protolayout.ActionBuilders.stringExtra
import androidx.wear.protolayout.DimensionBuilders.expand
import androidx.wear.protolayout.TimelineBuilders.Timeline
import androidx.wear.protolayout.material3.ButtonDefaults.filledTonalButtonColors
import androidx.wear.protolayout.material3.MaterialScope
import androidx.wear.protolayout.material3.Typography
import androidx.wear.protolayout.material3.buttonGroup
import androidx.wear.protolayout.material3.primaryLayout
import androidx.wear.protolayout.material3.text
import androidx.wear.protolayout.material3.textButton
import androidx.wear.protolayout.material3.textEdgeButton
import androidx.wear.protolayout.modifiers.clickable
import androidx.wear.protolayout.types.layoutString
import androidx.wear.tiles.Material3TileService
import androidx.wear.tiles.RequestBuilders.TileRequest
import androidx.wear.tiles.TileBuilders.Tile
import com.dynorixz.smarthome.data.local.FavoriteKind
import com.dynorixz.smarthome.data.local.FavoriteRef
import com.dynorixz.smarthome.data.local.UserPreferences
import com.dynorixz.smarthome.MainActivity
import com.dynorixz.smarthome.di.wearSurfaceDependencies
import com.dynorixz.smarthome.domain.SmartHome
import com.dynorixz.smarthome.domain.SmartHomeRepository
import com.dynorixz.smarthome.domain.Capability
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.jsonPrimitive

class SmartHomeTileService : Material3TileService(allowDynamicTheme = true) {
    private val dependencies by lazy(LazyThreadSafetyMode.NONE) { wearSurfaceDependencies() }
    private val preferences: UserPreferences get() = dependencies.preferences()
    private val repository: SmartHomeRepository get() = dependencies.repository()

    override suspend fun MaterialScope.tileResponse(requestParams: TileRequest): Tile = runCatching {
        val snapshot = withTimeoutOrNull(1_500) {
            preferences.tileFavorites.first().take(3) to repository.observeHome().first()
        }
        val refs = snapshot?.first.orEmpty()
        val home = snapshot?.second
        buildTile(refs, home)
    }.getOrElse { error ->
        Log.e(TAG, "Unable to render favorites tile", error)
        buildTile(emptyList(), null, "Откройте приложение")
    }

    private fun MaterialScope.buildTile(
        refs: List<FavoriteRef>,
        home: SmartHome?,
        emptyText: String = "Выберите действия в настройках",
    ): Tile = Tile.Builder()
        .setFreshnessIntervalMillis(300_000)
        .setTileTimeline(Timeline.fromLayoutElement(tileLayout(refs, home, emptyText)))
        .build()

    private fun MaterialScope.tileLayout(
        refs: List<FavoriteRef>,
        home: SmartHome?,
        emptyText: String,
    ) = primaryLayout(
        titleSlot = { text("Умный дом".layoutString) },
        mainSlot = {
            if (refs.isEmpty()) {
                textButton(
                    width = expand(),
                    height = expand(),
                    colors = filledTonalButtonColors(),
                    onClick = tileSettingsClick(),
                    labelContent = {
                        text(
                            if (emptyText.startsWith("Выберите")) {
                                "＋ Добавить".layoutString
                            } else {
                                "Открыть".layoutString
                            },
                        )
                    },
                )
            } else {
                buttonGroup(width = expand(), height = expand()) {
                    refs.forEach { ref ->
                        buttonGroupItem {
                            textButton(
                                width = expand(),
                                height = expand(),
                                onClick = clickable(
                                    action = launchAction(
                                        ComponentName(
                                            this@SmartHomeTileService,
                                            TileActionActivity::class.java,
                                        ),
                                        mapOf(
                                            TileActionActivity.EXTRA_KIND to stringExtra(ref.kind.name),
                                            TileActionActivity.EXTRA_ID to stringExtra(ref.id),
                                        ),
                                    ),
                                ),
                                labelContent = {
                                    text(
                                        ref.tileLabel(home).compactTileLabel().layoutString,
                                        typography = Typography.LABEL_MEDIUM,
                                        maxLines = 1,
                                    )
                                },
                            )
                        }
                    }
                }
            }
        },
        bottomSlot = if (refs.isEmpty()) {
            null
        } else {
            {
                textEdgeButton(
                    onClick = tileSettingsClick(),
                    labelContent = { text("Изменить".layoutString) },
                )
            }
        },
    )

    private fun tileSettingsClick() = clickable(
        action = launchAction(
            ComponentName(this, MainActivity::class.java),
            mapOf(
                MainActivity.EXTRA_DESTINATION to stringExtra(MainActivity.DESTINATION_TILE_SETTINGS),
            ),
        ),
    )

    companion object { private const val TAG = "SmartHomeTile" }
}

private fun FavoriteRef.name(home: SmartHome?): String = when (kind) {
    FavoriteKind.DEVICE -> home?.devices?.firstOrNull { it.id == id }?.name
    FavoriteKind.GROUP -> home?.groups?.firstOrNull { it.id == id }?.name
    FavoriteKind.SCENARIO -> home?.scenarios?.firstOrNull { it.id == id }?.name
} ?: "Действие"

private fun FavoriteRef.tileLabel(home: SmartHome?): String = when (kind) {
    FavoriteKind.DEVICE -> home?.devices?.firstOrNull { it.id == id }?.let { device ->
        val on = device.capabilities.filterIsInstance<Capability.OnOff>()
            .firstOrNull()?.value?.jsonPrimitive?.booleanOrNull
        "${when { !device.reachable -> "×"; on == true -> "●"; on == false -> "○"; else -> "›" }} ${device.name}"
    }
    FavoriteKind.GROUP -> home?.groups?.firstOrNull { it.id == id }?.let { group ->
        val on = group.capabilities.filterIsInstance<Capability.OnOff>()
            .firstOrNull()?.value?.jsonPrimitive?.booleanOrNull
        "${if (on == true) "●" else "○"} ${group.name}"
    }
    FavoriteKind.SCENARIO -> home?.scenarios?.firstOrNull { it.id == id }?.let { "▶ ${it.name}" }
} ?: name(home)

private fun String.compactTileLabel(maxLength: Int = 10): String =
    trim().ifEmpty { "Действие" }.let { label ->
        if (label.length <= maxLength) label else label.take(maxLength - 1).trimEnd() + "…"
    }
