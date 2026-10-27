package com.dynorixz.smarthome.auth

import android.app.Application
import android.net.Uri
import android.util.Base64
import androidx.wear.phone.interactions.authentication.OAuthRequest
import com.dynorixz.smarthome.BuildConfig
import com.dynorixz.smarthome.data.local.PendingOAuth
import com.dynorixz.smarthome.domain.AppException
import java.security.MessageDigest
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue
import kotlinx.serialization.json.Json
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class YandexOAuthSessionTest {
    private val context get() = RuntimeEnvironment.getApplication()

    @Test fun phoneUsesSystemCompanionRedirectAndPkce() {
        val session = YandexOAuthSession.prepare(context, "client", "device", true, 1_000)
        val uri = session.uri
        assertEquals("https://wear.googleapis.com/3p_auth/${context.packageName}", session.pending.redirectUri)
        assertEquals(session.pending.redirectUri, uri.getQueryParameter("redirect_uri"))
        assertEquals("client", uri.getQueryParameter("client_id"))
        assertEquals("device", uri.getQueryParameter("device_id"))
        assertEquals("iot:view iot:control", uri.getQueryParameter("scope"))
        assertEquals(session.pending.state, uri.getQueryParameter("state"))
        val challenge = Base64.encodeToString(
            MessageDigest.getInstance("SHA-256").digest(session.pending.codeVerifier.encodeToByteArray()),
            Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING,
        )
        assertEquals(challenge, uri.getQueryParameter("code_challenge"))
        assertEquals("S256", uri.getQueryParameter("code_challenge_method"))
        assertEquals("code", uri.getQueryParameter("response_type"))
        assertEquals(uri, OAuthRequest.Builder(context).setAuthProviderUrl(uri).build().requestUrl)
        assertFalse(uri.toString().contains(session.pending.codeVerifier))
        assertTrue(session.pending.codeVerifier.length in 43..128)
    }

    @Test fun retriesHaveIndependentStateAndVerifier() {
        val first = YandexOAuthSession.prepare(context, "client", "device", true)
        val second = YandexOAuthSession.prepare(context, "client", "device", true)
        assertNotEquals(first.pending.state, second.pending.state)
        assertNotEquals(first.pending.codeVerifier, second.pending.codeVerifier)
    }

    @Test fun watchBrowserKeepsLegacyCallback() {
        val session = YandexOAuthSession.prepare(context, "client", "device", false)
        assertEquals(BuildConfig.OAUTH_REDIRECT_URI, session.pending.redirectUri)
    }

    @Test fun validCallbackCanReturnToWatch() {
        val session = YandexOAuthSession.prepare(context, "client", "device", true, 1_000)
        val uri = Uri.parse(session.pending.redirectUri).buildUpon()
            .appendQueryParameter("state", session.pending.state).appendQueryParameter("code", "test-code").build()
        YandexOAuthSession.validateCallback(uri, session.pending, 2_000)
    }

    @Test fun wrongOriginStateExpiredAndAmbiguousCallbacksAreRejected() {
        val pending = PendingOAuth("state", "verifier", "device", 1_000, "https://wear.googleapis.com/3p_auth/com.dynorixz.smarthome")
        val base = pending.redirectUri
        for (callback in listOf(
            "https://example.com/3p_auth/com.dynorixz.smarthome?state=state&code=code",
            "$base/other?state=state&code=code",
            "$base?state=wrong&code=code",
            "$base?state=state&state=state&code=code",
            "$base?state=state&code=code&code=other",
            "dynorixzsmarthome://oauth/callback?state=state&code=code",
        )) assertFailsWith<AppException> { YandexOAuthSession.validateCallback(Uri.parse(callback), pending, 2_000) }
        assertFailsWith<AppException> {
            YandexOAuthSession.validateCallback(Uri.parse("$base?state=state&code=code"), pending, 1_001 + YandexOAuthSession.TIMEOUT_MS)
        }
    }

    @Test fun oldPendingSessionRemainsReadable() {
        val old = """{"state":"state","codeVerifier":"verifier","deviceId":"device","createdAtMillis":1000}"""
        assertEquals(BuildConfig.OAUTH_REDIRECT_URI, Json.decodeFromString<PendingOAuth>(old).redirectUri)
    }
}
