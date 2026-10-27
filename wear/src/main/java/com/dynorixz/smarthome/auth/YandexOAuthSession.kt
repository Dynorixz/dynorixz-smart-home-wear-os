package com.dynorixz.smarthome.auth

import android.content.Context
import android.net.Uri
import android.os.Build
import android.util.Base64
import androidx.wear.phone.interactions.authentication.OAuthRequest
import com.dynorixz.smarthome.BuildConfig
import com.dynorixz.smarthome.data.local.PendingOAuth
import com.dynorixz.smarthome.domain.AppError
import com.dynorixz.smarthome.domain.AppException
import java.security.MessageDigest
import java.security.SecureRandom

internal data class PreparedOAuth(val uri: Uri, val pending: PendingOAuth)

internal object YandexOAuthSession {
    const val TIMEOUT_MS = 10 * 60 * 1_000L

    fun prepare(
        context: Context,
        clientId: String,
        deviceId: String,
        onPhone: Boolean,
        nowMillis: Long = System.currentTimeMillis(),
    ): PreparedOAuth {
        val state = randomUrlSafe(24)
        val verifier = randomUrlSafe(64)
        val challenge = encode(MessageDigest.getInstance("SHA-256").digest(verifier.encodeToByteArray()))
        val builder = Uri.parse("https://oauth.yandex.com/authorize").buildUpon()
            .appendQueryParameter("response_type", "code")
            .appendQueryParameter("client_id", clientId)
            .appendQueryParameter("scope", "iot:view iot:control")
            .appendQueryParameter("state", state)
            .appendQueryParameter("code_challenge", challenge)
            .appendQueryParameter("code_challenge_method", "S256")
            .appendQueryParameter("device_id", deviceId)
            .appendQueryParameter("device_name", "Wear OS ${Build.MODEL}")
        val uri = if (onPhone) {
            // The system companion handles this HTTPS redirect; no Home phone APK is involved.
            OAuthRequest.Builder(context).setAuthProviderUrl(builder.build()).build().requestUrl
        } else {
            builder.appendQueryParameter("redirect_uri", BuildConfig.OAUTH_REDIRECT_URI).build()
        }
        return PreparedOAuth(
            uri,
            PendingOAuth(state, verifier, deviceId, nowMillis, requireNotNull(uri.getQueryParameter("redirect_uri"))),
        )
    }

    fun validateCallback(uri: Uri, pending: PendingOAuth, nowMillis: Long = System.currentTimeMillis()) {
        if (nowMillis - pending.createdAtMillis !in 0..TIMEOUT_MS) {
            throw AppException(AppError.OAuth("Сессия входа истекла. Начните вход заново"))
        }
        val expected = Uri.parse(pending.redirectUri)
        if (uri.scheme != expected.scheme || uri.host != expected.host || uri.port != expected.port ||
            uri.encodedPath != expected.encodedPath || uri.userInfo != null ||
            uri.getQueryParameters("state").size != 1 || uri.getQueryParameter("state") != pending.state ||
            uri.getQueryParameters("code").size > 1 || uri.getQueryParameters("error").size > 1
        ) {
            throw AppException(AppError.OAuth("Проверка безопасности OAuth не пройдена. Повторите вход"))
        }
    }

    private fun randomUrlSafe(byteCount: Int) = encode(ByteArray(byteCount).also(SecureRandom()::nextBytes))
    private fun encode(bytes: ByteArray) =
        Base64.encodeToString(bytes, Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING)
}
