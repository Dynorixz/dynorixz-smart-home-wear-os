package com.dynorixz.smarthome.data.network

import kotlinx.serialization.json.JsonObject
import retrofit2.http.Body
import retrofit2.http.Field
import retrofit2.http.FormUrlEncoded
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path

interface YandexSmartHomeApi {
    @GET("v1.0/user/info")
    suspend fun getUserInfo(): JsonObject

    @GET("v1.0/devices/{deviceId}")
    suspend fun getDevice(@Path("deviceId") deviceId: String): DeviceInfoResponse

    @POST("v1.0/devices/actions")
    suspend fun executeDeviceActions(@Body request: DeviceActionsRequest): ActionResponse

    @GET("v1.0/groups/{groupId}")
    suspend fun getGroup(@Path("groupId") groupId: String): GroupInfoResponse

    @POST("v1.0/groups/{groupId}/actions")
    suspend fun executeGroupActions(
        @Path("groupId") groupId: String,
        @Body request: GroupActionsRequest,
    ): ActionResponse

    @POST("v1.0/scenarios/{scenarioId}/actions")
    suspend fun executeScenario(@Path("scenarioId") scenarioId: String): ScenarioActionResponse
}

interface YandexOAuthApi {
    @FormUrlEncoded
    @POST("token")
    suspend fun exchangeCode(
        @Field("grant_type") grantType: String = "authorization_code",
        @Field("code") code: String,
        @Field("client_id") clientId: String,
        @Field("code_verifier") codeVerifier: String,
        @Field("device_id") deviceId: String,
        @Field("device_name") deviceName: String,
    ): OAuthTokenResponse
}
