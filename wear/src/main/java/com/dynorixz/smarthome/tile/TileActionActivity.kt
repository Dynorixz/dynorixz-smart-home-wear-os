package com.dynorixz.smarthome.tile

import android.content.ComponentName
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.core.net.toUri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.material3.Button
import androidx.wear.compose.material3.CircularProgressIndicator
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.Text
import androidx.wear.compose.material3.TimeText
import androidx.wear.tiles.TileService
import androidx.wear.watchface.complications.datasource.ComplicationDataSourceUpdateRequester
import com.dynorixz.smarthome.MainActivity
import com.dynorixz.smarthome.complication.ComplicationChoice
import com.dynorixz.smarthome.complication.HomeStatusComplicationService
import com.dynorixz.smarthome.complication.ShortcutAction
import com.dynorixz.smarthome.complication.ShortcutExecutor
import com.dynorixz.smarthome.complication.SmartHomeComplicationService
import com.dynorixz.smarthome.data.local.FavoriteKind
import com.dynorixz.smarthome.data.local.FavoriteRef
import com.dynorixz.smarthome.data.local.HomeTileKind
import com.dynorixz.smarthome.data.local.ThemePreference
import com.dynorixz.smarthome.data.local.UserPreferences
import com.dynorixz.smarthome.domain.Capability
import com.dynorixz.smarthome.domain.SmartHomeRepository
import com.dynorixz.smarthome.ui.DynorixzTheme
import com.dynorixz.smarthome.ui.userMessage
import dagger.hilt.android.AndroidEntryPoint
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

data class ShortcutResult(
    val message: String = "Выполняем…",
    val finished: Boolean = false,
    val succeeded: Boolean = false,
    val openUri: String? = null,
)

@HiltViewModel
class QuickActionViewModel @Inject constructor(
    private val repository: SmartHomeRepository,
    private val preferences: UserPreferences,
    private val executor: ShortcutExecutor,
    private val savedState: SavedStateHandle,
) : ViewModel() {
    private var started = savedState.get<Boolean>("started") == true
    val state = MutableStateFlow(if (started) ShortcutResult("Команда уже отправлялась. Проверьте состояние в Home", finished = true) else ShortcutResult())

    fun start(intent: Intent) {
        if (started) return
        started = true
        savedState["started"] = true
        viewModelScope.launch {
            try {
                val kind = intent.getStringExtra(TileActionActivity.EXTRA_KIND)?.let { FavoriteKind.valueOf(it) }
                    ?: error("Действие не выбрано")
                val id = intent.getStringExtra(TileActionActivity.EXTRA_ID) ?: error("Действие не выбрано")
                val ref = FavoriteRef(kind, id)
                val source = intent.getStringExtra(TileActionActivity.EXTRA_SOURCE)
                val home = repository.observeHome().first() ?: repository.refreshHome()
                val choice = if (source == TileActionActivity.SOURCE_COMPLICATION) {
                    val instanceId = intent.getIntExtra(TileActionActivity.EXTRA_COMPLICATION_ID, -1)
                    require(instanceId >= 0)
                    preferences.complicationChoice(instanceId).first()?.also { require(it.ref == ref) }
                        ?: error("Настройте ярлык на циферблате заново")
                } else {
                    val allowed = preferences.tileFavorites.first() + listOfNotNull(preferences.complicationFavorite.first())
                    val allowedLight = source == TileActionActivity.SOURCE_LIGHTS && kind == FavoriteKind.DEVICE &&
                        tileDevices(HomeTileKind.LIGHTS, home, preferences.tileSelections.first()[HomeTileKind.LIGHTS].orEmpty(), preferences.favorites.first()).any { it.id == id }
                    require(ref in allowed || allowedLight)
                    val capabilities = when (kind) {
                        FavoriteKind.DEVICE -> home.devices.firstOrNull { it.id == id }?.capabilities
                        FavoriteKind.GROUP -> home.groups.firstOrNull { it.id == id }?.capabilities
                        FavoriteKind.SCENARIO -> null
                    }
                    if (kind != FavoriteKind.SCENARIO && capabilities?.any { it is Capability.OnOff } != true)
                        ComplicationChoice(ref, ShortcutAction.OPEN)
                    else ComplicationChoice.default(ref)
                }
                if (choice.action == ShortcutAction.OPEN) {
                    val path = if (kind == FavoriteKind.DEVICE) "device" else "group"
                    state.value = ShortcutResult("Открываем управление", finished = true, openUri = "dynorixzsmarthome://open/$path/${android.net.Uri.encode(id)}")
                } else {
                    state.value = ShortcutResult("✓ ${executor.execute(choice)}", finished = true, succeeded = true)
                }
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) { state.value = ShortcutResult(e.userMessage(), finished = true) }
        }
    }

    fun consumeOpen() { state.value = state.value.copy(openUri = null) }
}

@AndroidEntryPoint
class TileActionActivity : ComponentActivity() {
    private val model: QuickActionViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        model.start(intent)
        setContent {
            val state by model.state.collectAsStateWithLifecycle()
            LaunchedEffect(state.finished, state.openUri) {
                state.openUri?.let {
                    model.consumeOpen()
                    startActivity(Intent(this@TileActionActivity, MainActivity::class.java).apply { data = it.toUri() })
                    finish()
                }
                if (state.succeeded) {
                    requestSurfaceUpdates()
                    delay(900)
                    finish()
                }
            }
            DynorixzTheme(theme = ThemePreference.DARK, dynamicColor = false) {
                Box(Modifier.fillMaxSize()) {
                    ScalingLazyColumn(
                        modifier = Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        contentPadding = PaddingValues(horizontal = 18.dp, vertical = 40.dp),
                    ) {
                        item { Text(state.message, color = MaterialTheme.colorScheme.onBackground) }
                        if (!state.finished) item { CircularProgressIndicator() }
                        if (state.finished && !state.succeeded) item { Button(onClick = ::finish, label = { Text("Готово") }) }
                    }
                    TimeText()
                }
            }
        }
    }

    private fun requestSurfaceUpdates() {
        TileService.getUpdater(this).requestUpdate(SmartHomeTileService::class.java)
        TileService.getUpdater(this).requestUpdate(LightsTileService::class.java)
        TileService.getUpdater(this).requestUpdate(SensorsTileService::class.java)
        listOf(SmartHomeComplicationService::class.java, HomeStatusComplicationService::class.java).forEach { service ->
            ComplicationDataSourceUpdateRequester.create(this, ComponentName(this, service)).requestUpdateAll()
        }
    }

    companion object {
        const val EXTRA_KIND = "favorite_kind"
        const val EXTRA_ID = "favorite_id"
        const val EXTRA_SOURCE = "source"
        const val EXTRA_COMPLICATION_ID = "complication_instance_id"
        const val SOURCE_LIGHTS = "lights_tile"
        const val SOURCE_TILE = "home_tile"
        const val SOURCE_COMPLICATION = "complication"
        fun extras(ref: FavoriteRef) = mapOf(EXTRA_KIND to ref.kind.name, EXTRA_ID to ref.id)
    }
}
