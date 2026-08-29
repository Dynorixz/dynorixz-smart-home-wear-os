package com.dynorixz.smarthome.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dynorixz.smarthome.data.local.FavoriteKind
import com.dynorixz.smarthome.data.local.FavoriteRef
import com.dynorixz.smarthome.data.local.AppSettings
import com.dynorixz.smarthome.data.local.UserPreferences
import com.dynorixz.smarthome.data.network.NetworkMonitor
import com.dynorixz.smarthome.domain.CapabilityAction
import com.dynorixz.smarthome.domain.SmartHome
import com.dynorixz.smarthome.domain.SmartHomeRepository
import com.dynorixz.smarthome.ui.userMessage
import com.dynorixz.smarthome.ui.runSuspendCatching
import com.dynorixz.smarthome.ui.WearSurfaceUpdater
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class HomeUiState(
    val loading: Boolean = true,
    val home: SmartHome? = null,
    val favorites: List<FavoriteRef> = emptyList(),
    val offline: Boolean = false,
    val refreshing: Boolean = false,
    val error: String? = null,
    val success: String? = null,
    val settings: AppSettings = AppSettings(),
)

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val repository: SmartHomeRepository,
    private val preferences: UserPreferences,
    private val networkMonitor: NetworkMonitor,
    private val surfaceUpdater: WearSurfaceUpdater,
) : ViewModel() {
    private val operation = MutableStateFlow(OperationState())
    private var autoRefreshJob: Job? = null

    val state: StateFlow<HomeUiState> = combine(
        repository.observeHome(), preferences.favorites, networkMonitor.connected, operation, preferences.settings,
    ) { home, favorites, connected, op, settings ->
        HomeUiState(
            loading = home == null && op.refreshing,
            home = home,
            favorites = favorites,
            offline = !connected,
            refreshing = op.refreshing,
            error = op.error,
            success = op.success,
            settings = settings,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HomeUiState())

    init { refresh() }

    fun refresh() {
        if (operation.value.refreshing) return
        viewModelScope.launch {
            operation.value = OperationState(refreshing = true)
            runSuspendCatching { repository.refreshHome() }
                .onSuccess {
                    operation.value = OperationState()
                    surfaceUpdater.requestAll()
                }
                .onFailure { operation.value = OperationState(error = it.userMessage()) }
        }
    }

    fun toggleFavorite(ref: FavoriteRef) {
        viewModelScope.launch { preferences.toggleFavorite(ref) }
    }

    fun executeDevice(deviceId: String, action: CapabilityAction) = runOperation {
        repository.executeDeviceAction(deviceId, action)
        "Команда выполнена"
    }

    fun executeGroup(groupId: String, action: CapabilityAction) = runOperation {
        repository.executeGroupAction(groupId, action)
        "Группа обновлена"
    }

    fun refreshGroup(groupId: String) {
        if (operation.value.refreshing) return
        viewModelScope.launch {
            operation.value = OperationState(refreshing = true)
            runSuspendCatching { repository.refreshGroup(groupId) }
                .onSuccess {
                    operation.value = OperationState()
                    surfaceUpdater.requestAll()
                }
                .onFailure { operation.value = OperationState(error = it.userMessage()) }
        }
    }

    fun executeScenario(scenarioId: String) = runOperation {
        repository.executeScenario(scenarioId)
        "Сценарий запущен"
    }

    fun clearMessage() { operation.value = OperationState() }

    fun startAutoRefresh() {
        if (autoRefreshJob?.isActive == true) return
        autoRefreshJob = viewModelScope.launch {
            while (true) {
                val minutes = preferences.settings.first().syncIntervalMinutes
                delay(minutes * 60_000L)
                if (networkMonitor.connected.value) refresh()
            }
        }
    }

    fun stopAutoRefresh() {
        autoRefreshJob?.cancel()
        autoRefreshJob = null
    }

    private fun runOperation(block: suspend () -> String) {
        viewModelScope.launch {
            operation.value = OperationState(refreshing = true)
            runSuspendCatching { block() }
                .onSuccess {
                    operation.value = OperationState(success = it)
                    surfaceUpdater.requestAll()
                }
                .onFailure { operation.value = OperationState(error = it.userMessage()) }
        }
    }

    private data class OperationState(
        val refreshing: Boolean = false,
        val error: String? = null,
        val success: String? = null,
    )
}
