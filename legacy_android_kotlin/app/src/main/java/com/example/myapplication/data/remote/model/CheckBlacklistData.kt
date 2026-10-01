package com.example.myapplication.data.remote.model

import com.google.gson.annotations.SerializedName

data class CheckBlacklistData(
    @SerializedName("target_hash")
    val targetHash: String,
    val features: List<String>,
)

