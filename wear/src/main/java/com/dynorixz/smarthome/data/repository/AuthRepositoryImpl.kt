package com.dynorixz.smarthome.data.repository

import android.content.Context
import android.net.Uri
import android.os.Build
import android.util.Log
import com.dynorixz.smarthome.BuildConfig
import com.dynorixz.smarthome.auth.YandexOAuthSession
import com.dynorixz.smarthome.data.local.AuthToken
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
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.CancellationException

@Singleton
class AuthRepositoryImpl @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val tokenStore: SecureTokenStore,
    private val preferences: UserPreferences,
    private val oauthApi: YandexOAuthApi,
    private val smartHomeApi: YandexSmartHomeApi,
    private val smartHomeRepository: SmartHomeRepository,
    private val errorMapper: ApiErrorMapper,
) : AuthRepository {
    override val tokenState: StateFlow<TokenState> = tokenStore.tokenState

    override suspend fun beginLogin(onPhone: Boolean): OAuthLaunchRequest {
        if (BuildConfig.YANDEX_CLIENT_ID == "YOUR_CLIENT_ID") {
            throw AppException(AppError.OAuth("В local.properties не задан YANDEX_CLIENT_ID"))
        }
        tokenStore.awaitLoaded()
        val deviceId = preferences.getOrCreateDeviceId()
        val session = YandexOAuthSession.prepare(context, BuildConfig.YANDEX_CLIENT_ID, deviceId, onPhone)
        tokenStore.savePending(session.pending)
        return OAuthLaunchRequest(session.uri)
    }

    override suspend fun handleCallback(uri: Uri) {
        tokenStore.awaitLoaded()
        val pending = tokenStore.pendingOAuth()
            ?: throw AppException(AppError.OAuth("Сессия входа не найдена"))
        Log.i("DynorixzOAuth", "Callback metadata: hasCode=${uri.getQueryParameter("code") != null}, hasState=${uri.getQueryParameter("state") != null}, stateMatches=${uri.getQueryParameter("state") == pending.state}")
        if (System.currentTimeMillis() - pending.createdAtMillis > YandexOAuthSession.TIMEOUT_MS) {
            tokenStore.clearPending()
            throw AppException(AppError.OAuth("Сессия входа истекла"))
        }
        YandexOAuthSession.validateCallback(uri, pending)
        Log.i("DynorixzOAuth", "Callback validated")
        tokenStore.clearPending()
        val oauthError = uri.getQueryParameter("error")
        if (oauthError != null) {
            throw AppException(AppError.OAuth(uri.getQueryParameter("error_description") ?: oauthError))
        }
        val code = uri.getQueryParameter("code")?.takeIf { it.isNotBlank() }
            ?: throw AppException(AppError.OAuth("Яндекс не вернул код авторизации"))
        try {
            val response = oauthApi.exchangeCode(
                code = code,
                clientId = BuildConfig.YANDEX_CLIENT_ID,
                codeVerifier = pending.codeVerifier,
                deviceId = pending.deviceId,
                deviceName = "Wear OS ${Build.MODEL}",
            )
            Log.i("DynorixzOAuth", "Code exchanged")
            val expiresAt = response.expiresIn?.let { System.currentTimeMillis() + it * 1_000 }
            tokenStore.saveToken(AuthToken(response.accessToken, response.refreshToken, expiresAt))
            val validation = smartHomeApi.getUserInfo().toUserInfoResponse()
            if (validation.status != "ok") throw AppException(AppError.Unauthorized(validation.requestId))
            Log.i("DynorixzOAuth", "Home access validated")
        } catch (error: Throwable) {
            if (error is CancellationException) throw error
            tokenStore.clear()
            throw errorMapper.map(error)
        }
    }

    override suspend fun cancelLogin() = tokenStore.clearPending()

    override suspend fun logout() {
        tokenStore.clear()
        smartHomeRepository.clearCache()
    }

}
