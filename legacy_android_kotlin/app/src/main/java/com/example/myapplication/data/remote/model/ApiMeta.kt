package com.example.myapplication.data.remote.model

import com.google.gson.annotations.SerializedName

data class ApiMeta(
    val retryable: Boolean? = null,
    @SerializedName("upstream_status")
    val upstreamStatus: Int? = null,
)

