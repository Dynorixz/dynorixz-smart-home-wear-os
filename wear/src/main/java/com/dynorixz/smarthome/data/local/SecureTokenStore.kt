package com.dynorixz.smarthome.data.local

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

private val Context.authDataStore by preferencesDataStore("secure_auth")

@Serializable
data class AuthToken(
    val accessToken: String,
    val refreshToken: String? = null,
    val expiresAtMillis: Long? = null,
) {
    fun isExpired(nowMillis: Long = System.currentTimeMillis()): Boolean =
        expiresAtMillis?.let { nowMillis >= it - 30_000 } ?: false
}

@Serializable
data class PendingOAuth(
    val state: String,
    val codeVerifier: String,
    val deviceId: String,
    val createdAtMillis: Long,
)

@Serializable
private data class SecureAuthPayload(
    val token: AuthToken? = null,
    val pendingOAuth: PendingOAuth? = null,
)

sealed interface TokenState {
    data object Loading : TokenState
    data object SignedOut : TokenState
    data class Authenticated(val token: AuthToken) : TokenState
}

interface TokenProvider {
    val currentAccessToken: String?
}

interface SessionInvalidator {
    suspend fun invalidate()
}

@Singleton
class SecureTokenStore @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val json: Json,
) : TokenProvider, SessionInvalidator {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val _tokenState = MutableStateFlow<TokenState>(TokenState.Loading)
    val tokenState: StateFlow<TokenState> = _tokenState.asStateFlow()
    @Volatile private var payload = SecureAuthPayload()

    override val currentAccessToken: String?
        get() = (tokenState.value as? TokenState.Authenticated)?.token?.accessToken

    init {
        scope.launch { load() }
    }

    suspend fun awaitLoaded(): TokenState {
        if (_tokenState.value !is TokenState.Loading) return _tokenState.value
        return tokenState.first { it !is TokenState.Loading }
    }

    suspend fun saveToken(token: AuthToken) {
        payload = payload.copy(token = token, pendingOAuth = null)
        persist(payload)
        _tokenState.value = TokenState.Authenticated(token)
    }

    suspend fun savePending(pending: PendingOAuth) {
        payload = payload.copy(pendingOAuth = pending)
        persist(payload)
    }

    fun pendingOAuth(): PendingOAuth? = payload.pendingOAuth

    suspend fun clearPending() {
        payload = payload.copy(pendingOAuth = null)
        persist(payload)
    }

    suspend fun clear() {
        payload = SecureAuthPayload()
        context.authDataStore.edit { it.remove(BLOB) }
        _tokenState.value = TokenState.SignedOut
    }

    override suspend fun invalidate() = clear()

    private suspend fun load() {
        val encoded = context.authDataStore.data.first()[BLOB]
        payload = if (encoded == null) SecureAuthPayload() else runCatching {
            json.decodeFromString<SecureAuthPayload>(decrypt(encoded))
        }.getOrElse {
            context.authDataStore.edit { prefs -> prefs.remove(BLOB) }
            SecureAuthPayload()
        }
        val token = payload.token
        _tokenState.value = if (token == null || token.isExpired()) TokenState.SignedOut
        else TokenState.Authenticated(token)
    }

    private suspend fun persist(value: SecureAuthPayload) {
        val encrypted = encrypt(json.encodeToString(value))
        context.authDataStore.edit { it[BLOB] = encrypted }
    }

    private fun encrypt(plainText: String): String {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, getOrCreateKey())
        val encrypted = cipher.doFinal(plainText.encodeToByteArray())
        val packed = cipher.iv + encrypted
        return Base64.encodeToString(packed, Base64.NO_WRAP)
    }

    private fun decrypt(encoded: String): String {
        val packed = Base64.decode(encoded, Base64.NO_WRAP)
        require(packed.size > IV_LENGTH)
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, getOrCreateKey(), GCMParameterSpec(128, packed.copyOfRange(0, IV_LENGTH)))
        return cipher.doFinal(packed.copyOfRange(IV_LENGTH, packed.size)).decodeToString()
    }

    private fun getOrCreateKey(): SecretKey {
        val keyStore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (keyStore.getKey(KEY_ALIAS, null) as? SecretKey)?.let { return it }
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore").run {
            init(
                KeyGenParameterSpec.Builder(
                    KEY_ALIAS,
                    KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
                )
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .setRandomizedEncryptionRequired(true)
                    .build(),
            )
            generateKey()
        }
    }

    private companion object {
        val BLOB = stringPreferencesKey("encrypted_payload")
        const val KEY_ALIAS = "dynorixz_yandex_auth_v1"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val IV_LENGTH = 12
    }
}
