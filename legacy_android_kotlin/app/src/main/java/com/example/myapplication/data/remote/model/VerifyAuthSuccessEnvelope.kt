package com.example.myapplication.data.remote.model

data class VerifyAuthSuccessEnvelope(
    val success: Boolean,
    val code: String,
    val data: VerifyAuthData,
    val meta: ApiMeta = ApiMeta(),
)

