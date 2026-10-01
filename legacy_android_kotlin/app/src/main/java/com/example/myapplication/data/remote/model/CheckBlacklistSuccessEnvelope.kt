package com.example.myapplication.data.remote.model

data class CheckBlacklistSuccessEnvelope(
    val success: Boolean,
    val code: String,
    val data: CheckBlacklistData,
    val meta: ApiMeta = ApiMeta(),
)

