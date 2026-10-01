package com.example.myapplication.data.remote.model

import com.google.gson.annotations.SerializedName

data class StoreReportRequest(
    @SerializedName("client_phone_number")
    val clientPhoneNumber: String,
    val feature: String,
    @SerializedName("public_key")
    val publicKey: String,
    val signature: String,
)

