package com.example.myapplication.data

interface ReportRepository {
    suspend fun submitReport(
        clientPhoneNumber: String,
        feature: ReportingFeature,
    ): ReportSubmissionResult
}

sealed interface ReportSubmissionResult {
    data class Success(
        val clientHash: String,
        val reporterHash: String,
        val feature: String,
        val featureLabel: String,
        val uniqueReporterCount: Int,
        val level: String,
        val readyForSync: Boolean,
    ) : ReportSubmissionResult

    data class Failure(
        val code: String,
        val message: String,
        val fieldErrors: Map<String, List<String>> = emptyMap(),
        val retryable: Boolean = false,
    ) : ReportSubmissionResult
}

