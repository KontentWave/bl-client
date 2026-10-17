package com.example.myapplication.data.remote.model

import com.google.gson.annotations.SerializedName

data class InitiateOwnerAuthRequest(
    @SerializedName("phone_number")
    val phoneNumber: String,
)
