package com.dynorixz.smarthome.complication

import android.content.ComponentName
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.foundation.lazy.items
import androidx.wear.compose.material3.Button
import androidx.wear.compose.material3.ButtonDefaults
import androidx.wear.compose.material3.CircularProgressIndicator
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.Text
import androidx.wear.compose.material3.TimeText
import androidx.wear.compose.material3.TimeTextDefaults
import androidx.wear.compose.material3.timeTextCurvedText
import androidx.wear.watchface.complications.datasource.ComplicationDataSourceService
import androidx.wear.watchface.complications.datasource.ComplicationDataSourceUpdateRequester
import com.dynorixz.smarthome.MainActivity
import com.dynorixz.smarthome.data.local.FavoriteKind
import com.dynorixz.smarthome.data.local.FavoriteRef
import com.dynorixz.smarthome.data.local.ThemePreference
import com.dynorixz.smarthome.data.local.UserPreferences
import com.dynorixz.smarthome.domain.Capability
import com.dynorixz.smarthome.domain.SmartHome
import com.dynorixz.smarthome.domain.SmartHomeRepository
import com.dynorixz.smarthome.ui.DynorixzTheme
import com.dynorixz.smarthome.ui.userMessage
import dagger.hilt.android.AndroidEntryPoint
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch

internal data class ShortcutCandidate(val ref: FavoriteRef, val name: String, val hasPower: Boolean)

@HiltViewModel
class ComplicationConfigViewModel @Inject constructor(
    private val repository: SmartHomeRepository,
    private val preferences: UserPreferences,
) : ViewModel() {
    val home = MutableStateFlow<SmartHome?>(null)
    val error = MutableStateFlow<String?>(null)
    val saved = MutableStateFlow(false)
    internal var selected by mutableStateOf<ShortcutCandidate?>(null)
        internal set
    var saving by mutableStateOf(false)
        private set

    init { load() }

    fun load() = viewModelScope.launch {
        error.value = null
        try { home.value = repository.refreshHome() }
        catch (e: CancellationException) { throw e }
        catch (e: Exception) { error.value = e.userMessage() }
    }

    fun save(id: Int, choice: ComplicationChoice) {
        if (saving || id < 0) return
        saving = true
        viewModelScope.launch {
            try {
                preferences.setComplicationChoice(id, choice)
                saved.value = true
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) { error.value = e.userMessage(); saving = false }
        }
    }
}

@AndroidEntryPoint
class ComplicationConfigActivity : ComponentActivity() {
    private val model: ComplicationConfigViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setResult(RESULT_CANCELED)
        val id = intent.getIntExtra(ComplicationDataSourceService.EXTRA_CONFIG_COMPLICATION_ID, -1)
        if (id < 0) { finish(); return }
        setContent {
            val home by model.home.collectAsStateWithLifecycle()
            val error by model.error.collectAsStateWithLifecycle()
            val saved by model.saved.collectAsStateWithLifecycle()
            androidx.compose.runtime.LaunchedEffect(saved) {
                if (saved) {
                    ComplicationDataSourceUpdateRequester.create(
                        this@ComplicationConfigActivity,
                        ComponentName(this@ComplicationConfigActivity, SmartHomeComplicationService::class.java),
                    ).requestUpdate(id)
                    setResult(RESULT_OK)
                    finish()
                }
            }
            DynorixzTheme(theme = ThemePreference.DARK, dynamicColor = false) {
                Box(Modifier.fillMaxSize()) {
                    ScalingLazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 40.dp),
                    ) {
                        item { Text(model.selected?.name ?: "Ярлык на циферблате", color = MaterialTheme.colorScheme.onBackground) }
                        if (error != null) {
                            item { Text(error.orEmpty(), color = MaterialTheme.colorScheme.error) }
                            item { Button(onClick = { model.load() }, label = { Text("Повторить") }) }
                            item { Button(onClick = {
                                startActivity(Intent(this@ComplicationConfigActivity, MainActivity::class.java))
                            }, label = { Text("Открыть Home") }) }
                        } else if (home == null || model.saving) {
                            item { CircularProgressIndicator() }
                        } else if (model.selected != null) {
                            val selected = requireNotNull(model.selected)
                            item { Text("Действие по нажатию", color = MaterialTheme.colorScheme.onBackground) }
                            val actions = if (selected.hasPower) listOf(ShortcutAction.TOGGLE, ShortcutAction.ON, ShortcutAction.OFF, ShortcutAction.OPEN)
                                else listOf(ShortcutAction.OPEN)
                            items(actions) { action ->
                                Button(
                                    onClick = { model.save(id, ComplicationChoice(selected.ref, action)) },
                                    modifier = Modifier.fillMaxWidth(), label = { Text(action.label) },
                                )
                            }
                            item { Button(onClick = { model.selected = null }, label = { Text("Назад к выбору") }, colors = ButtonDefaults.filledTonalButtonColors()) }
                        } else {
                            item { Text("У каждого кружка — своё действие", color = MaterialTheme.colorScheme.onBackground, style = MaterialTheme.typography.bodySmall) }
                            val candidates = buildList {
                                home?.devices?.forEach { add(ShortcutCandidate(FavoriteRef(FavoriteKind.DEVICE, it.id), it.name, it.capabilities.any { c -> c is Capability.OnOff })) }
                                home?.groups?.forEach { add(ShortcutCandidate(FavoriteRef(FavoriteKind.GROUP, it.id), it.name, it.capabilities.any { c -> c is Capability.OnOff })) }
                                home?.scenarios?.forEach { add(ShortcutCandidate(FavoriteRef(FavoriteKind.SCENARIO, it.id), it.name, false)) }
                            }
                            if (candidates.isEmpty()) item { Text("В доме пока нет устройств и сценариев", color = MaterialTheme.colorScheme.onBackground) }
                            items(candidates, key = { "${it.ref.kind}:${it.ref.id}" }) { candidate ->
                                Button(
                                    onClick = {
                                        if (candidate.ref.kind == FavoriteKind.SCENARIO) model.save(id, ComplicationChoice.default(candidate.ref))
                                        else model.selected = candidate
                                    },
                                    modifier = Modifier.fillMaxWidth(),
                                    label = { Text(candidate.name) },
                                    secondaryLabel = { Text(when (candidate.ref.kind) {
                                        FavoriteKind.DEVICE -> "Прибор · выбрать действие"
                                        FavoriteKind.GROUP -> "Группа · выбрать действие"
                                        FavoriteKind.SCENARIO -> "Сценарий · запуск одним нажатием"
                                    }) },
                                    colors = ButtonDefaults.filledTonalButtonColors(),
                                )
                            }
                        }
                    }
                    val clockStyle = TimeTextDefaults.timeTextStyle(color = MaterialTheme.colorScheme.onBackground)
                    TimeText { time -> timeTextCurvedText(time, style = clockStyle) }
                }
            }
        }
    }
}
