package com.dynorixz.smarthome.di

import android.content.Context
import androidx.room.Room
import com.dynorixz.smarthome.data.local.AppDatabase
import com.dynorixz.smarthome.data.local.SmartHomeCacheDao
import com.dynorixz.smarthome.data.local.SecureTokenStore
import com.dynorixz.smarthome.data.local.TokenProvider
import com.dynorixz.smarthome.data.local.SessionInvalidator
import com.dynorixz.smarthome.data.mapper.SmartHomeMapper
import com.dynorixz.smarthome.data.network.ApiErrorMapper
import com.dynorixz.smarthome.data.network.BearerTokenInterceptor
import com.dynorixz.smarthome.data.network.SafeNetworkLoggingInterceptor
import com.dynorixz.smarthome.data.network.YandexOAuthApi
import com.dynorixz.smarthome.data.network.YandexSmartHomeApi
import com.dynorixz.smarthome.data.repository.AuthRepositoryImpl
import com.dynorixz.smarthome.data.repository.SmartHomeRepositoryImpl
import com.dynorixz.smarthome.domain.AuthRepository
import com.dynorixz.smarthome.domain.SmartHomeRepository
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Named
import javax.inject.Singleton
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory

@Module
@InstallIn(SingletonComponent::class)
object AppModule {
    @Provides @Singleton
    fun json(): Json = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
        explicitNulls = false
        encodeDefaults = true
    }

    @Provides @Singleton
    fun database(@ApplicationContext context: Context): AppDatabase =
        Room.databaseBuilder(context, AppDatabase::class.java, "dynorixz.db").build()

    @Provides fun cacheDao(database: AppDatabase): SmartHomeCacheDao = database.smartHomeCacheDao()
    @Provides @Singleton fun mapper(): SmartHomeMapper = SmartHomeMapper()
    @Provides @Singleton fun errorMapper(json: Json): ApiErrorMapper = ApiErrorMapper(json)
    @Provides fun tokenProvider(store: SecureTokenStore): TokenProvider = store
    @Provides fun sessionInvalidator(store: SecureTokenStore): SessionInvalidator = store

    @Provides @Singleton @Named("smartHome")
    fun smartHomeClient(tokenProvider: TokenProvider): OkHttpClient = OkHttpClient.Builder()
        .addInterceptor(BearerTokenInterceptor(tokenProvider))
        .addInterceptor(SafeNetworkLoggingInterceptor())
        .build()

    @Provides @Singleton @Named("oauth")
    fun oauthClient(): OkHttpClient = OkHttpClient.Builder()
        .addInterceptor(SafeNetworkLoggingInterceptor())
        .build()

    @Provides @Singleton
    fun smartHomeApi(
        json: Json,
        @Named("smartHome") client: OkHttpClient,
    ): YandexSmartHomeApi = Retrofit.Builder()
        .baseUrl("https://api.iot.yandex.net/")
        .client(client)
        .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
        .build()
        .create(YandexSmartHomeApi::class.java)

    @Provides @Singleton
    fun oauthApi(
        json: Json,
        @Named("oauth") client: OkHttpClient,
    ): YandexOAuthApi = Retrofit.Builder()
        .baseUrl("https://oauth.yandex.com/")
        .client(client)
        .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
        .build()
        .create(YandexOAuthApi::class.java)
}

@Module
@InstallIn(SingletonComponent::class)
abstract class BindingsModule {
    @Binds abstract fun smartHomeRepository(impl: SmartHomeRepositoryImpl): SmartHomeRepository
    @Binds abstract fun authRepository(impl: AuthRepositoryImpl): AuthRepository
}
