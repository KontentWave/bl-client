package com.example.myapplication.data.remote.model

data class ApiErrorEnvelope(
    val success: Boolean,
    val code: String,
    val message: String,
    val errors: Map<String, List<String>> = emptyMap(),
    val meta: ApiMeta = ApiMeta(),
)

