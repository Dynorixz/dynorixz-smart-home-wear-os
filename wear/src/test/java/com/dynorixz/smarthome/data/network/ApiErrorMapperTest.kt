package com.dynorixz.smarthome.data.network

import com.dynorixz.smarthome.domain.AppError
import java.io.IOException
import java.net.SocketTimeoutException
import kotlin.test.assertIs
import kotlinx.serialization.json.Json
import org.junit.Test

class ApiErrorMapperTest {
    private val mapper = ApiErrorMapper(Json { ignoreUnknownKeys = true })

    @Test fun `maps network error`() {
        assertIs<AppError.Network>(mapper.map(IOException()).error)
    }

    @Test fun `maps timeout`() {
        assertIs<AppError.Timeout>(mapper.map(SocketTimeoutException()).error)
    }
}
