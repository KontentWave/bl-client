package com.example.myapplication.data.remote.model

import com.google.gson.annotations.SerializedName

data class StoreReportData(
    @SerializedName("client_hash")
    val clientHash: String,
    @SerializedName("reporter_hash")
    val reporterHash: String,
    val feature: String,
    @SerializedName("feature_label")
    val featureLabel: String,
    @SerializedName("unique_reporter_count")
    val uniqueReporterCount: Int,
    val level: String,
    @SerializedName("ready_for_sync")
    val readyForSync: Boolean,
)

