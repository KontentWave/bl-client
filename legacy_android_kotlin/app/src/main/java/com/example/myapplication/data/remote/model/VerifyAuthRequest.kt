package com.example.myapplication.data.remote.model

import com.google.gson.annotations.SerializedName

data class VerifyAuthRequest(
    @SerializedName("challenge_id")
    val challengeId: String,
    val otp: String,
    @SerializedName("public_key")
    val publicKey: String,
    val signature: String,
)

