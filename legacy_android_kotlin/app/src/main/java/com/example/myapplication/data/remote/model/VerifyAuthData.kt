package com.example.myapplication.data.remote.model

import com.google.gson.annotations.SerializedName

data class VerifyAuthData(
    @SerializedName("challenge_id")
    val challengeId: String,
    @SerializedName("masked_phone_number")
    val maskedPhoneNumber: String,
    @SerializedName("verified_at")
    val verifiedAt: String,
)

