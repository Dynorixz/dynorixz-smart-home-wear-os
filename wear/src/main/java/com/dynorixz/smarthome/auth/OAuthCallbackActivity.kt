package com.dynorixz.smarthome.auth

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.lifecycle.lifecycleScope
import com.dynorixz.smarthome.MainActivity
import com.dynorixz.smarthome.domain.AppError
import com.dynorixz.smarthome.domain.AppException
import com.dynorixz.smarthome.domain.AuthRepository
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.launch

@AndroidEntryPoint
class OAuthCallbackActivity : ComponentActivity() {
    @Inject lateinit var authRepository: AuthRepository

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        process(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        process(intent)
    }

    private fun process(source: Intent) {
        val uri = source.data ?: return finish()
        lifecycleScope.launch {
            runCatching { authRepository.handleCallback(uri) }
                .onFailure { error ->
                    Log.w(TAG, "OAuth callback failed: ${error.diagnosticName()}")
                    Toast.makeText(
                        this@OAuthCallbackActivity,
                        error.userMessage(),
                        Toast.LENGTH_LONG,
                    ).show()
                }
            startActivity(Intent(this@OAuthCallbackActivity, MainActivity::class.java))
            finish()
        }
    }

    private fun Throwable.userMessage(): String = when (val appError = (this as? AppException)?.error) {
        is AppError.OAuth -> appError.description ?: "Яндекс не завершил авторизацию"
        is AppError.Network -> "Нет соединения с интернетом"
        is AppError.Timeout -> "Яндекс не ответил вовремя"
        is AppError.Unauthorized -> "Яндекс отклонил авторизацию"
        else -> "Не удалось завершить вход. Попробуйте ещё раз."
    }

    private fun Throwable.diagnosticName(): String =
        buildString {
            append((this@diagnosticName as? AppException)?.error?.let { it::class.simpleName })
            val root = this@diagnosticName.cause
            if (root != null) append(" cause=").append(root::class.simpleName)
        }

    private companion object {
        const val TAG = "DynorixzOAuth"
    }
}
