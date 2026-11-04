package com.example.myapplication.data.remote.model

import com.google.gson.annotations.SerializedName
import com.google.gson.JsonObject

data class VerifyAuthData(
    @SerializedName("challenge_id")
    val challengeId: String,
    @SerializedName("masked_phone_number")
    val maskedPhoneNumber: String,
    @SerializedName("verified_at")
    val verifiedAt: String,
    val lifecycle: JsonObject? = null,
)
