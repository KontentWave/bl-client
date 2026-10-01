package com.example.myapplication.data.remote

import com.example.myapplication.data.remote.model.CheckBlacklistRequest
import com.example.myapplication.data.remote.model.CheckBlacklistSuccessEnvelope
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.POST

interface BlacklistApi {
    @POST("blacklist/check")
    suspend fun checkBlacklist(
        @Body request: CheckBlacklistRequest,
    ): Response<CheckBlacklistSuccessEnvelope>
}

