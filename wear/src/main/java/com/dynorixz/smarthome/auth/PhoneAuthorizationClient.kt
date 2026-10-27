package com.dynorixz.smarthome.auth

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.core.content.ContextCompat
import androidx.wear.phone.interactions.authentication.OAuthRequest
import androidx.wear.phone.interactions.authentication.OAuthResponse
import androidx.wear.phone.interactions.authentication.RemoteAuthClient
import com.dynorixz.smarthome.domain.AppError
import com.dynorixz.smarthome.domain.AppException
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext

class PhoneAuthorizationClient @Inject constructor(
    @param:ApplicationContext private val context: Context,
) {
    suspend fun authorize(uri: Uri): Uri = withContext(Dispatchers.Main.immediate) {
        val request = OAuthRequest.Builder(context).setAuthProviderUrl(uri).build()
        val client = RemoteAuthClient.create(context)
        try {
            suspendCancellableCoroutine { continuation ->
                client.sendAuthorizationRequest(
                    request,
                    ContextCompat.getMainExecutor(context),
                    object : RemoteAuthClient.Callback() {
                        override fun onAuthorizationResponse(request: OAuthRequest, response: OAuthResponse) {
                            if (!continuation.isActive) return
                            val callback = response.responseUrl
                            if (callback != null && callback.isHierarchical) {
                                val expected = Uri.parse(request.redirectUrl)
                                val expectedState = request.requestUrl.getQueryParameter("state")
                                Log.i("DynorixzOAuth", "Phone callback metadata: scheme=${callback.scheme}, host=${callback.host}, pathMatches=${callback.encodedPath == expected.encodedPath}, stateCount=${callback.getQueryParameters("state").size}, stateMatches=${callback.getQueryParameter("state") == expectedState}, queryKeys=${callback.queryParameterNames}, hasFragment=${callback.fragment != null}")
                            } else if (callback != null) {
                                Log.i("DynorixzOAuth", "Phone callback metadata: scheme=${callback.scheme}, hierarchical=false")
                            }
                            if (callback != null) {
                                try {
                                    continuation.resume(PhoneOAuthCallback.normalize(callback, request.redirectUrl))
                                } catch (error: Exception) {
                                    continuation.resumeWithException(error)
                                }
                            }
                            else continuation.resumeWithException(failure("Телефон не вернул результат входа"))
                        }

                        override fun onAuthorizationError(request: OAuthRequest, errorCode: Int) {
                            if (!continuation.isActive) return
                            val message = when (errorCode) {
                                RemoteAuthClient.ERROR_PHONE_UNAVAILABLE ->
                                    "Подключите телефон к часам и откройте Galaxy Wearable или Wear OS. Затем повторите вход"
                                RemoteAuthClient.ERROR_UNSUPPORTED ->
                                    "Телефон не поддерживает этот способ входа. Обновите приложение часов на телефоне или войдите на часах"
                                else -> "Не удалось открыть вход на телефоне. Повторите попытку"
                            }
                            continuation.resumeWithException(failure(message))
                        }
                    },
                )
            }
        } finally {
            // Runs on Main on completion, timeout, cancellation and ViewModel destruction.
            client.close()
        }
    }

    private fun failure(message: String) = AppException(AppError.OAuth(message))
}
