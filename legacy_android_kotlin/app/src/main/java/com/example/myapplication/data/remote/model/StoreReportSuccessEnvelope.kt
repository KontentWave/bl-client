package com.example.myapplication.data.remote.model

data class StoreReportSuccessEnvelope(
    val success: Boolean,
    val code: String,
    val data: StoreReportData,
    val meta: ApiMeta = ApiMeta(),
)

