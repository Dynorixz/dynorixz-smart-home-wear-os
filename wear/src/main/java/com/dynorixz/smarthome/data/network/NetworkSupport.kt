package com.dynorixz.smarthome.data.network

import android.util.Log
import com.dynorixz.smarthome.data.local.TokenProvider
import com.dynorixz.smarthome.domain.AppError
import com.dynorixz.smarthome.domain.AppException
import java.io.IOException
import java.net.SocketTimeoutException
import kotlin.time.TimeSource
import kotlinx.serialization.json.Json
import okhttp3.Interceptor
import okhttp3.Response
import retrofit2.HttpException

class BearerTokenInterceptor(private val tokenProvider: TokenProvider) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val token = tokenProvider.currentAccessToken
            ?: throw AppException(AppError.Unauthorized())
        val request = chain.request().newBuilder()
            .header("Authorization", "Bearer $token")
            .build()
        return chain.proceed(request)
    }
}

class SafeNetworkLoggingInterceptor : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val mark = TimeSource.Monotonic.markNow()
        return try {
            val response = chain.proceed(request)
            val requestId = response.header("X-Request-Id") ?: response.header("X-Request-ID")
            Log.i(TAG, "${request.method} ${request.url.encodedPath} -> ${response.code} in ${mark.elapsedNow().inWholeMilliseconds}ms request_id=${requestId ?: "body"}")
            response
        } catch (error: Throwable) {
            Log.w(TAG, "${request.method} ${request.url.encodedPath} failed in ${mark.elapsedNow().inWholeMilliseconds}ms: ${error::class.simpleName}")
            throw error
        }
    }

    private companion object { const val TAG = "DynorixzNetwork" }
}

class ApiErrorMapper(private val json: Json) {
    fun map(throwable: Throwable): AppException {
        if (throwable is AppException) return throwable
        if (throwable is SocketTimeoutException) return AppException(AppError.Timeout(), throwable)
        if (throwable is IOException) return AppException(AppError.Network(), throwable)
        if (throwable is HttpException) {
            val diagnosticId = runCatching {
                throwable.response()?.errorBody()?.string()?.let {
                    json.decodeFromString<ApiErrorResponse>(it).requestId
                }
            }.getOrNull()
            val error = when (throwable.code()) {
                401 -> AppError.Unauthorized(diagnosticId)
                403 -> AppError.Forbidden(diagnosticId)
                408 -> AppError.Timeout(diagnosticId)
                429 -> AppError.RateLimit(diagnosticId)
                in 500..599 -> AppError.Server(diagnosticId)
                else -> AppError.InvalidResponse(diagnosticId)
            }
            return AppException(error, throwable)
        }
        return AppException(AppError.InvalidResponse(), throwable)
    }
}

