package com.example.myapplication.data.remote

import com.example.myapplication.data.remote.model.InitiateAuthRequest
import com.example.myapplication.data.remote.model.InitiateAuthSuccessEnvelope
import com.example.myapplication.data.remote.model.InitiateOwnerAuthRequest
import com.example.myapplication.data.remote.model.VerifyAuthRequest
import com.example.myapplication.data.remote.model.VerifyAuthSuccessEnvelope
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.Headers
import retrofit2.http.POST

interface AuthApi {
    @POST("auth/initiate")
    suspend fun initiateAuth(
        @Body request: InitiateAuthRequest,
    ): Response<InitiateAuthSuccessEnvelope>

    @Headers("Content-Type: application/json", "Accept: application/json")
    @POST("auth/owner/initiate")
    suspend fun initiateOwnerAuth(
        @Body request: InitiateOwnerAuthRequest,
    ): Response<InitiateAuthSuccessEnvelope>

    @POST("auth/verify")
    suspend fun verifyAuth(
        @Body request: VerifyAuthRequest,
    ): Response<VerifyAuthSuccessEnvelope>
}

