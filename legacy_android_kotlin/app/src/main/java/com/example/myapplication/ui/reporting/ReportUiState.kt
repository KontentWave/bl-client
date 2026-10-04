package com.example.myapplication.ui.reporting

import com.example.myapplication.data.ReportingFeature

data class ReportUiState(
    val clientPhoneNumber: String = "",
    val selectedFeature: ReportingFeature? = null,
    val availableFeatures: List<ReportingFeature> = ReportingFeature.all(),
    val isSubmitting: Boolean = false,
    val clientPhoneNumberError: String? = null,
    val featureError: String? = null,
    val generalError: String? = null,
    val isRetryableError: Boolean = false,
    val retryAfterSeconds: Long = 0,
    val lastSubmittedFeatureLabel: String? = null,
    val lastSubmittedLevel: String? = null,
    val lastUniqueReporterCount: Int? = null,
    val lastReadyForSync: Boolean? = null,
) {
    val hasSubmissionSummary: Boolean
        get() = lastSubmittedFeatureLabel != null && lastSubmittedLevel != null && lastUniqueReporterCount != null && lastReadyForSync != null
}

