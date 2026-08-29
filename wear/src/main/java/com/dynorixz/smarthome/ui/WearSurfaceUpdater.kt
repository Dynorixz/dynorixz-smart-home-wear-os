package com.dynorixz.smarthome.ui

import android.content.ComponentName
import android.content.Context
import androidx.wear.tiles.TileService
import androidx.wear.watchface.complications.datasource.ComplicationDataSourceUpdateRequester
import com.dynorixz.smarthome.complication.HomeStatusComplicationService
import com.dynorixz.smarthome.complication.SmartHomeComplicationService
import com.dynorixz.smarthome.tile.LightsTileService
import com.dynorixz.smarthome.tile.SmartHomeTileService
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class WearSurfaceUpdater @Inject constructor(
    @param:ApplicationContext private val context: Context,
) {
    fun requestAll() {
        val tileUpdater = TileService.getUpdater(context)
        tileUpdater.requestUpdate(SmartHomeTileService::class.java)
        tileUpdater.requestUpdate(LightsTileService::class.java)
        listOf(SmartHomeComplicationService::class.java, HomeStatusComplicationService::class.java).forEach { service ->
            ComplicationDataSourceUpdateRequester.create(
                context,
                ComponentName(context, service),
            ).requestUpdateAll()
        }
    }
}
