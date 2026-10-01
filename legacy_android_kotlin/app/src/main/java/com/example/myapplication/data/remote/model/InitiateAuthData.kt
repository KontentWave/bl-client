package com.example.myapplication.data.remote.model

import com.google.gson.annotations.SerializedName

data class InitiateAuthData(
    @SerializedName("challenge_id")
    val challengeId: String,
    @SerializedName("masked_phone_number")
    val maskedPhoneNumber: String,
    @SerializedName("otp_expires_at")
    val otpExpiresAt: String,
)

