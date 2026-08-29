package com.dynorixz.smarthome.ui.auth

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dynorixz.smarthome.domain.AuthRepository
import com.dynorixz.smarthome.ui.userMessage
import com.dynorixz.smarthome.ui.runSuspendCatching
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class AuthUiState(val loading: Boolean = false, val error: String? = null)
sealed interface AuthEffect { data class Open(val uri: Uri, val onPhone: Boolean) : AuthEffect }

@HiltViewModel
class AuthViewModel @Inject constructor(private val repository: AuthRepository) : ViewModel() {
    private val _state = MutableStateFlow(AuthUiState())
    val state: StateFlow<AuthUiState> = _state.asStateFlow()
    private val _effects = MutableSharedFlow<AuthEffect>()
    val effects: SharedFlow<AuthEffect> = _effects.asSharedFlow()

    fun login(onPhone: Boolean) {
        if (_state.value.loading) return
        viewModelScope.launch {
            _state.value = AuthUiState(loading = true)
            runSuspendCatching { repository.beginLogin() }
                .onSuccess {
                    _state.value = AuthUiState()
                    _effects.emit(AuthEffect.Open(it.uri, onPhone))
                }
                .onFailure { _state.value = AuthUiState(error = it.userMessage()) }
        }
    }
}
