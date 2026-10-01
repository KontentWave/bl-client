package com.example.myapplication.data.remote.model

data class InitiateAuthSuccessEnvelope(
    val success: Boolean,
    val code: String,
    val data: InitiateAuthData,
    val meta: ApiMeta = ApiMeta(),
)

