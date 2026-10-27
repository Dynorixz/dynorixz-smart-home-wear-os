package com.dynorixz.smarthome.ui.auth

import android.net.Uri
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dynorixz.smarthome.auth.PhoneAuthorizationClient
import com.dynorixz.smarthome.auth.YandexOAuthSession
import com.dynorixz.smarthome.domain.AuthRepository
import com.dynorixz.smarthome.domain.AppException
import com.dynorixz.smarthome.ui.userMessage
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout

data class AuthUiState(val loading: Boolean = false, val error: String? = null)
sealed interface AuthEffect { data class Open(val uri: Uri) : AuthEffect }

@HiltViewModel
class AuthViewModel @Inject constructor(
    private val repository: AuthRepository,
    private val phoneAuthorization: PhoneAuthorizationClient,
) : ViewModel() {
    private val _state = MutableStateFlow(AuthUiState())
    val state: StateFlow<AuthUiState> = _state.asStateFlow()
    private val _effects = MutableSharedFlow<AuthEffect>()
    val effects: SharedFlow<AuthEffect> = _effects.asSharedFlow()
    private var loginJob: Job? = null

    fun login(onPhone: Boolean) {
        if (_state.value.loading) return
        loginJob = viewModelScope.launch {
            _state.value = AuthUiState(loading = true)
            try {
                val request = repository.beginLogin(onPhone)
                if (onPhone) {
                    val callback = withTimeout(YandexOAuthSession.TIMEOUT_MS) {
                        phoneAuthorization.authorize(request.uri)
                    }
                    Log.i("DynorixzOAuth", "Phone response received")
                    repository.handleCallback(callback)
                } else {
                    _effects.emit(AuthEffect.Open(request.uri))
                }
                _state.value = AuthUiState()
            } catch (_: TimeoutCancellationException) {
                repository.cancelLogin()
                _state.value = AuthUiState(error = "Время ожидания истекло. Нажмите «Войти» ещё раз")
            } catch (error: CancellationException) {
                Log.i("DynorixzOAuth", "Login cancelled")
                withContext(NonCancellable) { repository.cancelLogin() }
                throw error
            } catch (error: Throwable) {
                Log.w("DynorixzOAuth", "Login failed: ${error::class.simpleName}/${(error as? AppException)?.error?.let { it::class.simpleName }}")
                repository.cancelLogin()
                _state.value = AuthUiState(error = error.userMessage())
            }
        }
    }

    fun cancelLogin() {
        // Keep login disabled until cleanup finishes so it cannot clear the next session.
        viewModelScope.launch {
            loginJob?.cancel()
            loginJob?.join()
            _state.value = AuthUiState()
        }
    }

    fun browserUnavailable() {
        viewModelScope.launch {
            repository.cancelLogin()
            _state.value = AuthUiState(error = "На часах нет браузера. Используйте вход на телефоне")
        }
    }
}
