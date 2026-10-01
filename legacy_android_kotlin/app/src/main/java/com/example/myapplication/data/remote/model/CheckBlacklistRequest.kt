package com.example.myapplication.data.remote.model

import com.google.gson.annotations.SerializedName

data class CheckBlacklistRequest(
    @SerializedName("target_hash")
    val targetHash: String,
    @SerializedName("public_key")
    val publicKey: String,
    val signature: String,
)

