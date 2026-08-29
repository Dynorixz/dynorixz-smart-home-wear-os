package com.dynorixz.smarthome.tile

import android.content.Intent
import android.os.Bundle
import android.content.ComponentName
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.core.net.toUri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.lifecycleScope
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.material3.Button
import androidx.wear.compose.material3.CircularProgressIndicator
import androidx.wear.compose.material3.Text
import androidx.wear.compose.material3.TimeText
import androidx.wear.tiles.TileService
import androidx.wear.watchface.complications.datasource.ComplicationDataSourceUpdateRequester
import com.dynorixz.smarthome.MainActivity
import com.dynorixz.smarthome.complication.HomeStatusComplicationService
import com.dynorixz.smarthome.complication.SmartHomeComplicationService
import com.dynorixz.smarthome.data.local.FavoriteKind
import com.dynorixz.smarthome.data.local.FavoriteRef
import com.dynorixz.smarthome.data.local.UserPreferences
import com.dynorixz.smarthome.domain.Capability
import com.dynorixz.smarthome.domain.CapabilityActionFactory
import com.dynorixz.smarthome.domain.SmartHomeRepository
import com.dynorixz.smarthome.ui.DynorixzTheme
import com.dynorixz.smarthome.ui.userMessage
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.jsonPrimitive

@AndroidEntryPoint
class TileActionActivity : ComponentActivity() {
    @Inject lateinit var repository: SmartHomeRepository
    @Inject lateinit var preferences: UserPreferences
    private var status by mutableStateOf("Выполняем…")
    private var finished by mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            DynorixzTheme {
                Box(Modifier.fillMaxSize()) {
                    ScalingLazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 40.dp),
                    ) {
                        item { Text(status) }
                        if (!finished) item { CircularProgressIndicator() }
                        if (finished) item { Button(onClick = ::finish, label = { Text("Готово") }) }
                    }
                    TimeText()
                }
            }
        }
        lifecycleScope.launch { executeFromIntent() }
    }

    private suspend fun executeFromIntent() {
        val kind = intent.getStringExtra(EXTRA_KIND)?.let { runCatching { FavoriteKind.valueOf(it) }.getOrNull() }
        val id = intent.getStringExtra(EXTRA_ID)
        if (kind == null || id == null) {
            status = "Действие не выбрано"
            finished = true
            return
        }
        val requested = FavoriteRef(kind, id)
        var succeeded = false
        runCatching {
            val home = repository.observeHome().first() ?: repository.refreshHome()
            val allowed = preferences.tileFavorites.first() + listOfNotNull(preferences.complicationFavorite.first())
            val allowedLight = intent.getStringExtra(EXTRA_SOURCE) == SOURCE_LIGHTS &&
                kind == FavoriteKind.DEVICE &&
                home.devices.any { it.id == id && "light" in it.type.removePrefix("devices.types.") }
            if (requested !in allowed && !allowedLight) error("Действие больше не доступно")
            when (kind) {
                FavoriteKind.DEVICE -> {
                    val device = home.devices.first { it.id == id }
                    val onOff = device.capabilities.filterIsInstance<Capability.OnOff>().firstOrNull()
                    if (onOff == null) {
                        startActivity(Intent(this, MainActivity::class.java).apply {
                            data = "dynorixzsmarthome://open/device/${android.net.Uri.encode(id)}".toUri()
                        })
                        "Открываем управление"
                    } else {
                        val current = onOff.value?.jsonPrimitive?.booleanOrNull == true
                        repository.executeDeviceAction(id, CapabilityActionFactory.boolean(onOff, !current))
                        if (current) "Устройство выключено" else "Устройство включено"
                    }
                }
                FavoriteKind.GROUP -> {
                    val group = home.groups.first { it.id == id }
                    val onOff = group.capabilities.filterIsInstance<Capability.OnOff>().firstOrNull()
                    if (onOff == null) {
                        startActivity(Intent(this, MainActivity::class.java).apply {
                            data = "dynorixzsmarthome://open/group/${android.net.Uri.encode(id)}".toUri()
                        })
                        "Открываем управление"
                    } else {
                        val current = onOff.value?.jsonPrimitive?.booleanOrNull == true
                        repository.executeGroupAction(id, CapabilityActionFactory.boolean(onOff, !current))
                        if (current) "Группа выключена" else "Группа включена"
                    }
                }
                FavoriteKind.SCENARIO -> {
                    repository.executeScenario(id)
                    "Сценарий запущен"
                }
            }
        }.onSuccess {
            status = "✓ $it"
            succeeded = true
            requestSurfaceUpdates()
        }
            .onFailure { status = it.userMessage() }
        finished = true
        if (succeeded) {
            delay(700)
            finish()
        }
    }

    private fun requestSurfaceUpdates() {
        TileService.getUpdater(this).requestUpdate(SmartHomeTileService::class.java)
        TileService.getUpdater(this).requestUpdate(LightsTileService::class.java)
        listOf(SmartHomeComplicationService::class.java, HomeStatusComplicationService::class.java).forEach { service ->
            ComplicationDataSourceUpdateRequester.create(this, ComponentName(this, service)).requestUpdateAll()
        }
    }

    companion object {
        const val EXTRA_KIND = "favorite_kind"
        const val EXTRA_ID = "favorite_id"
        const val EXTRA_SOURCE = "source"
        const val SOURCE_LIGHTS = "lights_tile"
        fun extras(ref: FavoriteRef) = mapOf(EXTRA_KIND to ref.kind.name, EXTRA_ID to ref.id)
    }
}
