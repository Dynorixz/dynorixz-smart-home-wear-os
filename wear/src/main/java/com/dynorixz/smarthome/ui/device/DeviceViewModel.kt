package com.dynorixz.smarthome.ui.device

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dynorixz.smarthome.domain.Capability
import com.dynorixz.smarthome.domain.CapabilityAction
import com.dynorixz.smarthome.domain.Device
import com.dynorixz.smarthome.domain.SmartHomeRepository
import com.dynorixz.smarthome.ui.userMessage
import com.dynorixz.smarthome.ui.runSuspendCatching
import com.dynorixz.smarthome.ui.WearSurfaceUpdater
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed interface DeviceUiState {
    data object Loading : DeviceUiState
    data class Content(
        val device: Device,
        val pendingInstance: String? = null,
        val error: String? = null,
    ) : DeviceUiState
    data class Error(val message: String) : DeviceUiState
}

@HiltViewModel
class DeviceViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val repository: SmartHomeRepository,
    private val surfaceUpdater: WearSurfaceUpdater,
) : ViewModel() {
    private val deviceId: String = checkNotNull(savedStateHandle["deviceId"])
    private val _state = MutableStateFlow<DeviceUiState>(DeviceUiState.Loading)
    val state: StateFlow<DeviceUiState> = _state.asStateFlow()
    private var debounceJob: Job? = null
    private var commandJob: Job? = null
    private val stagedCommands = linkedMapOf<String, PendingCommand>()
    private val readyCommands = linkedMapOf<String, PendingCommand>()
    private var reconciledDevice: Device? = null
    private var confirmedDevice: Device? = null

    init {
        viewModelScope.launch {
            repository.observeHome().collect { home ->
                val current = _state.value as? DeviceUiState.Content
                home?.devices?.firstOrNull { it.id == deviceId }?.let { device ->
                    if (!hasPendingCommands()) {
                        confirmedDevice = device
                        _state.value = DeviceUiState.Content(device)
                    } else {
                        reconciledDevice = device
                    }
                }
            }
        }
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            runSuspendCatching { repository.refreshDevice(deviceId) }
                .onSuccess {
                    confirmedDevice = it
                    if (!hasPendingCommands()) _state.value = DeviceUiState.Content(it)
                    else reconciledDevice = it
                }
                .onFailure { error ->
                    if (_state.value !is DeviceUiState.Content) _state.value = DeviceUiState.Error(error.userMessage())
                }
        }
    }

    fun execute(action: CapabilityAction, debounceMillis: Long = 0) {
        val content = _state.value as? DeviceUiState.Content ?: return
        val original = content.device
        val optimistic = original.copy(
            capabilities = original.capabilities.map {
                if (
                    it.type == action.type &&
                    (it.instance == action.instance || it is Capability.ColorSetting)
                ) {
                    it.withAction(action)
                } else {
                    it
                }
            },
        )
        _state.value = DeviceUiState.Content(optimistic, pendingInstance = action.instance)
        if (confirmedDevice == null) confirmedDevice = original
        stagedCommands[action.key] = PendingCommand(action, optimistic)
        debounceJob?.cancel()
        debounceJob = viewModelScope.launch {
            if (debounceMillis > 0) delay(debounceMillis)
            readyCommands.putAll(stagedCommands)
            stagedCommands.clear()
            drainCommands()
        }
    }

    private fun drainCommands() {
        if (commandJob?.isActive == true) return
        commandJob = viewModelScope.launch {
            while (readyCommands.isNotEmpty()) {
                val entry = readyCommands.entries.first()
                val pending = entry.value
                readyCommands.remove(entry.key)
                val result = runSuspendCatching { repository.executeDeviceAction(deviceId, pending.action) }
                if (result.isFailure) {
                    val fallback = confirmedDevice ?: pending.optimistic
                    stagedCommands.clear()
                    readyCommands.clear()
                    reconciledDevice = null
                    _state.value = DeviceUiState.Content(fallback, error = result.exceptionOrNull()!!.userMessage())
                    commandJob = null
                    return@launch
                }
                confirmedDevice = reconciledDevice ?: pending.optimistic
                reconciledDevice = null
                surfaceUpdater.requestAll()
            }
            if (stagedCommands.isEmpty()) {
                _state.value = DeviceUiState.Content(
                    reconciledDevice ?: confirmedDevice ?: (_state.value as DeviceUiState.Content).device,
                )
                reconciledDevice = null
            }
            commandJob = null
            if (readyCommands.isNotEmpty()) drainCommands()
        }
    }

    private fun hasPendingCommands(): Boolean =
        debounceJob?.isActive == true || commandJob?.isActive == true ||
            stagedCommands.isNotEmpty() || readyCommands.isNotEmpty()

    private data class PendingCommand(val action: CapabilityAction, val optimistic: Device)
}

private val CapabilityAction.key: String get() = "$type:$instance"

private fun Capability.withAction(action: CapabilityAction): Capability = when (this) {
    is Capability.OnOff -> copy(value = action.value)
    is Capability.Range -> copy(value = action.value)
    is Capability.Mode -> copy(value = action.value)
    is Capability.Toggle -> copy(value = action.value)
    is Capability.ColorSetting -> copy(instance = action.instance, value = action.value)
    is Capability.Unknown -> this
}
