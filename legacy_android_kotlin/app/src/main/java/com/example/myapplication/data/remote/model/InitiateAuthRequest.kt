package com.example.myapplication.data.remote.model

import com.google.gson.annotations.SerializedName

data class InitiateAuthRequest(
    @SerializedName("ad_url")
    val adUrl: String,
)

