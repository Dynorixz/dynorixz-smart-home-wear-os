package com.dynorixz.smarthome.tile

import android.util.Log
import androidx.wear.protolayout.TimelineBuilders.Timeline
import androidx.wear.protolayout.material3.MaterialScope
import androidx.wear.tiles.Material3TileService
import androidx.wear.tiles.RequestBuilders.TileRequest
import androidx.wear.tiles.TileBuilders.Tile
import androidx.wear.tiles.TileService
import androidx.wear.tiles.EventBuilders.TileInteractionEvent
import androidx.concurrent.futures.CallbackToFutureAdapter
import com.google.common.util.concurrent.ListenableFuture
import com.dynorixz.smarthome.data.local.HomeTileKind
import com.dynorixz.smarthome.di.wearSurfaceDependencies
import com.dynorixz.smarthome.domain.AppException
import com.dynorixz.smarthome.domain.AppError
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull

class SmartHomeTileService : HomeTileService(HomeTileKind.HOME)
class LightsTileService : HomeTileService(HomeTileKind.LIGHTS)
class SensorsTileService : HomeTileService(HomeTileKind.SENSORS)

abstract class HomeTileService(private val kind: HomeTileKind) : Material3TileService(allowDynamicTheme = false) {
    private val dependencies by lazy(LazyThreadSafetyMode.NONE) { wearSurfaceDependencies() }
    private val refreshScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var refreshJob: Job? = null
    private var lastAttemptMillis = 0L

    override fun onRecentInteractionEventsAsync(events: List<TileInteractionEvent>): ListenableFuture<Void> {
        if (events.none { it.eventType == TileInteractionEvent.ENTER } ||
            refreshJob?.isActive == true || System.currentTimeMillis() - lastAttemptMillis < 30_000
        ) return super.onRecentInteractionEventsAsync(events)
        return CallbackToFutureAdapter.getFuture { completer ->
          refreshJob = refreshScope.launch {
            var changed = false
            try {
                withTimeout(6_000) {
                    val home = dependencies.repository().observeHome().first() ?: return@withTimeout
                    if (System.currentTimeMillis() - home.updatedAtMillis < 60_000) return@withTimeout
                    lastAttemptMillis = System.currentTimeMillis()
                    dependencies.repository().refreshHome()
                    changed = true
                }
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                Log.w("DynorixzTile", "Tile refresh deferred: ${error::class.simpleName}")
                changed = error is AppException && error.error is AppError.Unauthorized
            } finally {
                completer.set(null)
            }
            if (changed) {
                val updater = TileService.getUpdater(this@HomeTileService)
                updater.requestUpdate(SmartHomeTileService::class.java)
                updater.requestUpdate(LightsTileService::class.java)
                updater.requestUpdate(SensorsTileService::class.java)
            }
          }
          "Refresh ${kind.name} Tile snapshot"
        }
    }

    override fun onDestroy() {
        refreshScope.cancel()
        super.onDestroy()
    }

    override suspend fun MaterialScope.tileResponse(requestParams: TileRequest): Tile {
        val model = try {
            // Read the local snapshot within the Tile deadline. Taps use the normal,
            // authenticated action executor; rendering never starts a network request.
            withTimeoutOrNull(1_500) {
                val preferences = dependencies.preferences()
                tileModel(kind, dependencies.repository().observeHome().first(),
                    preferences.tileSelections.first()[kind].orEmpty(), preferences.favorites.first())
            } ?: tileModel(kind, null)
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            Log.w("DynorixzTile", "Tile snapshot unavailable: ${error::class.simpleName}")
            tileModel(kind, null)
        }
        return Tile.Builder().setFreshnessIntervalMillis(300_000)
            .setTileTimeline(Timeline.fromLayoutElement(homeTileLayout(model))).build()
    }
}
