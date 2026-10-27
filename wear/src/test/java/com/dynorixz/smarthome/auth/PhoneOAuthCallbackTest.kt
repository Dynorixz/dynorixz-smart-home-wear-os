package com.dynorixz.smarthome.auth

import android.app.Application
import android.net.Uri
import com.dynorixz.smarthome.data.local.PendingOAuth
import com.dynorixz.smarthome.domain.AppException
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class PhoneOAuthCallbackTest {
    private val redirect = "https://wear.googleapis.com/3p_auth/com.dynorixz.smarthome"
    private val pending = PendingOAuth("expected-state", "verifier", "device", 1_000, redirect)

    @Test fun systemCompanionSchemePreservesCodeAndState() {
        val response = Uri.parse("com.google.android.wear:/3p_auth/com.dynorixz.smarthome?code=test-code&state=expected-state")
        val normalized = PhoneOAuthCallback.normalize(response, redirect)
        assertEquals("test-code", normalized.getQueryParameter("code"))
        assertEquals("expected-state", normalized.getQueryParameter("state"))
        YandexOAuthSession.validateCallback(normalized, pending, 2_000)
        // An external intent does not get access to the system normalization path.
        assertFailsWith<AppException> { YandexOAuthSession.validateCallback(response, pending, 2_000) }
    }

    @Test fun httpsResponseRemainsUnchanged() {
        val response = Uri.parse("$redirect?code=test-code&state=expected-state")
        assertEquals(response, PhoneOAuthCallback.normalize(response, redirect))
    }

    @Test fun galaxyWearableResponseKeepsTheOriginalState() {
        val response = Uri.parse("com.samsung.wearable.watchuniteplugin:/3p_auth/com.dynorixz.smarthome?code=test-code&cid=phone-session&state=expected-state")
        val normalized = PhoneOAuthCallback.normalize(response, redirect)
        assertEquals("phone-session", normalized.getQueryParameter("cid"))
        YandexOAuthSession.validateCallback(normalized, pending, 2_000)
    }

    @Test fun wrongPackageAndWebOriginAreRejected() {
        for (value in listOf(
            "com.google.android.wear:/3p_auth/another.package?code=code&state=expected-state",
            "https://example.com/3p_auth/com.dynorixz.smarthome?code=code&state=expected-state",
            "http://wear.googleapis.com/3p_auth/com.dynorixz.smarthome?code=code&state=expected-state",
        )) assertFailsWith<AppException> { PhoneOAuthCallback.normalize(Uri.parse(value), redirect) }
    }

    @Test fun normalizationDoesNotBypassStateOrDuplicateChecks() {
        for (query in listOf("state=wrong", "state=expected-state&state=expected-state", "")) {
            val response = Uri.parse("com.google.android.wear:/3p_auth/com.dynorixz.smarthome?code=code&$query")
            val normalized = PhoneOAuthCallback.normalize(response, redirect)
            assertFailsWith<AppException> { YandexOAuthSession.validateCallback(normalized, pending, 2_000) }
        }
    }
}
