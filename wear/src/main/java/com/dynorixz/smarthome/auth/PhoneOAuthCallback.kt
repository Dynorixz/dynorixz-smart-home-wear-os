package com.dynorixz.smarthome.auth

import android.net.Uri
import com.dynorixz.smarthome.domain.AppError
import com.dynorixz.smarthome.domain.AppException

/** Normalizes only responses delivered by the bound RemoteAuthClient, never exported deep links. */
internal object PhoneOAuthCallback {
    fun normalize(response: Uri, redirectUri: String): Uri {
        val expected = Uri.parse(redirectUri)
        if (!response.isHierarchical || response.encodedPath != expected.encodedPath || response.userInfo != null ||
            response.scheme == "http" ||
            (response.scheme == "https" && (response.host != expected.host || response.port !in listOf(-1, 443)))
        ) {
            throw AppException(AppError.OAuth("Телефон вернул неверный адрес входа. Повторите попытку"))
        }
        // The Wear web bridge returns through its companion's custom scheme. The request-bound
        // system callback supplies the result; state and PKCE are still checked by the repository.
        return expected.buildUpon()
            .encodedQuery(response.encodedQuery)
            .encodedFragment(response.encodedFragment)
            .build()
    }
}
