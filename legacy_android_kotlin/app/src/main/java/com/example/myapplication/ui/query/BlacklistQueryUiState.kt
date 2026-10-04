package com.example.myapplication.ui.query

data class BlacklistQueryUiState(
    val targetHash: String = "",
    val isSubmitting: Boolean = false,
    val targetHashError: String? = null,
    val generalError: String? = null,
    val isRetryableError: Boolean = false,
    val retryAfterSeconds: Long = 0,
    val lastQueriedHash: String? = null,
    val features: List<String> = emptyList(),
) {
    val hasResult: Boolean
        get() = lastQueriedHash != null

    val hasFeatureMatches: Boolean
        get() = features.isNotEmpty()
}

