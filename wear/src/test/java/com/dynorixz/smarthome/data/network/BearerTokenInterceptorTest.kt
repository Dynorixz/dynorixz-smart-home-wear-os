package com.dynorixz.smarthome.data.network

import com.dynorixz.smarthome.data.local.TokenProvider
import com.dynorixz.smarthome.domain.AppError
import kotlin.test.*
import kotlinx.coroutines.delay
import kotlinx.serialization.json.Json
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.OkHttpClient
import okhttp3.Request
import org.junit.Test

class BearerTokenInterceptorTest {
    @Test fun coldStartWaitsForStoredTokenBeforeSendingRequest() {
        val provider = object : TokenProvider {
            override val currentAccessToken: String? = null
            override suspend fun awaitAccessToken(): String { delay(25); return "loaded-test-token" }
        }
        val server = MockWebServer()
        server.start()
        try {
            server.enqueue(MockResponse().setResponseCode(200).setBody("ok"))
            val client = OkHttpClient.Builder().addInterceptor(BearerTokenInterceptor(provider)).build()
            client.newCall(Request.Builder().url(server.url("/info")).build()).execute().use { assertEquals(200, it.code) }
            assertEquals("Bearer loaded-test-token", server.takeRequest().headers["Authorization"])
        } finally { server.shutdown() }
    }

    @Test fun signedOutIsReportedAsUnauthorizedWithoutSendingRequest() {
        val provider = object : TokenProvider { override val currentAccessToken: String? = null }
        val client = OkHttpClient.Builder().addInterceptor(BearerTokenInterceptor(provider)).build()
        val error = assertFailsWith<MissingAuthenticationException> {
            client.newCall(Request.Builder().url("https://example.invalid/info").build()).execute()
        }
        assertIs<AppError.Unauthorized>(ApiErrorMapper(Json).map(error).error)
    }
}
