package com.dynorixz.smarthome.data.local

import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.junit.Test

class AuthTokenTest {
    @Test
    fun `token expiry includes safety window`() {
        val now = 1_000_000L
        assertTrue(AuthToken("secret", expiresAtMillis = now + 20_000).isExpired(now))
        assertFalse(AuthToken("secret", expiresAtMillis = now + 60_000).isExpired(now))
        assertFalse(AuthToken("secret").isExpired(now))
    }
}

