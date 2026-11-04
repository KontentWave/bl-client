package com.example.myapplication.data.remote

import com.google.gson.JsonObject
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.POST

interface LifecycleApi {
    @POST("auth/status")
    suspend fun status(@Body request: JsonObject): Response<JsonObject>

    @POST("auth/renew")
    suspend fun renew(@Body request: JsonObject): Response<JsonObject>
}
