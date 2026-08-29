package com.dynorixz.smarthome.data.repository

import android.net.Uri
import android.os.Build
import android.util.Base64
import androidx.core.net.toUri
import com.dynorixz.smarthome.BuildConfig
import com.dynorixz.smarthome.data.local.AuthToken
import com.dynorixz.smarthome.data.local.PendingOAuth
import com.dynorixz.smarthome.data.local.SecureTokenStore
import com.dynorixz.smarthome.data.local.TokenState
import com.dynorixz.smarthome.data.local.UserPreferences
import com.dynorixz.smarthome.data.network.ApiErrorMapper
import com.dynorixz.smarthome.data.network.YandexOAuthApi
import com.dynorixz.smarthome.data.network.YandexSmartHomeApi
import com.dynorixz.smarthome.data.network.toUserInfoResponse
import com.dynorixz.smarthome.domain.AppError
import com.dynorixz.smarthome.domain.AppException
import com.dynorixz.smarthome.domain.AuthRepository
import com.dynorixz.smarthome.domain.OAuthLaunchRequest
import com.dynorixz.smarthome.domain.SmartHomeRepository
import java.security.MessageDigest
import java.security.SecureRandom
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.CancellationException

@Singleton
class AuthRepositoryImpl @Inject constructor(
    private val tokenStore: SecureTokenStore,
    private val preferences: UserPreferences,
    private val oauthApi: YandexOAuthApi,
    private val smartHomeApi: YandexSmartHomeApi,
    private val smartHomeRepository: SmartHomeRepository,
    private val errorMapper: ApiErrorMapper,
) : AuthRepository {
    override val tokenState: StateFlow<TokenState> = tokenStore.tokenState

    override suspend fun beginLogin(): OAuthLaunchRequest {
        if (BuildConfig.YANDEX_CLIENT_ID == "YOUR_CLIENT_ID") {
            throw AppException(AppError.OAuth("В local.properties не задан YANDEX_CLIENT_ID"))
        }
        val state = randomUrlSafe(24)
        val verifier = randomUrlSafe(64)
        val challenge = Base64.encodeToString(
            MessageDigest.getInstance("SHA-256").digest(verifier.encodeToByteArray()),
            Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING,
        )
        val deviceId = preferences.getOrCreateDeviceId()
        tokenStore.savePending(PendingOAuth(state, verifier, deviceId, System.currentTimeMillis()))
        val uri = "https://oauth.yandex.com/authorize".toUri().buildUpon()
            .appendQueryParameter("response_type", "code")
            .appendQueryParameter("client_id", BuildConfig.YANDEX_CLIENT_ID)
            .appendQueryParameter("redirect_uri", BuildConfig.OAUTH_REDIRECT_URI)
            .appendQueryParameter("scope", "iot:view iot:control")
            .appendQueryParameter("state", state)
            .appendQueryParameter("code_challenge", challenge)
            .appendQueryParameter("code_challenge_method", "S256")
            .appendQueryParameter("device_id", deviceId)
            .appendQueryParameter("device_name", "Wear OS ${Build.MODEL}")
            .build()
        return OAuthLaunchRequest(uri)
    }

    override suspend fun handleCallback(uri: Uri) {
        val pending = tokenStore.pendingOAuth()
            ?: throw AppException(AppError.OAuth("Сессия входа не найдена"))
        if (System.currentTimeMillis() - pending.createdAtMillis > OAUTH_TIMEOUT_MS) {
            tokenStore.clearPending()
            throw AppException(AppError.OAuth("Сессия входа истекла"))
        }
        if (uri.getQueryParameter("state") != pending.state) {
            throw AppException(AppError.OAuth("Проверка безопасности OAuth не пройдена"))
        }
        val oauthError = uri.getQueryParameter("error")
        if (oauthError != null) {
            tokenStore.clearPending()
            throw AppException(AppError.OAuth(uri.getQueryParameter("error_description") ?: oauthError))
        }
        val code = uri.getQueryParameter("code")
            ?: throw AppException(AppError.OAuth("Яндекс не вернул код авторизации"))
        try {
            val response = oauthApi.exchangeCode(
                code = code,
                clientId = BuildConfig.YANDEX_CLIENT_ID,
                codeVerifier = pending.codeVerifier,
                deviceId = pending.deviceId,
                deviceName = "Wear OS ${Build.MODEL}",
            )
            val expiresAt = response.expiresIn?.let { System.currentTimeMillis() + it * 1_000 }
            tokenStore.saveToken(AuthToken(response.accessToken, response.refreshToken, expiresAt))
            val validation = smartHomeApi.getUserInfo().toUserInfoResponse()
            if (validation.status != "ok") throw AppException(AppError.Unauthorized(validation.requestId))
        } catch (error: Throwable) {
            if (error is CancellationException) throw error
            tokenStore.clear()
            throw errorMapper.map(error)
        }
    }

    override suspend fun logout() {
        tokenStore.clear()
        smartHomeRepository.clearCache()
    }

    private fun randomUrlSafe(byteCount: Int): String {
        val bytes = ByteArray(byteCount).also(SecureRandom()::nextBytes)
        return Base64.encodeToString(bytes, Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING)
    }

    private companion object { const val OAUTH_TIMEOUT_MS = 10 * 60 * 1_000L }
}
