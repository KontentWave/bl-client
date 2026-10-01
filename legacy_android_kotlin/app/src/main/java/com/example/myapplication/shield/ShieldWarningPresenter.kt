package com.example.myapplication.shield

interface ShieldWarningPresenter {
    suspend fun showWarning(
        normalizedNumber: String,
        features: List<String>,
        targetHash: String?,
    ): ShieldOverlayPresentation

    suspend fun dismissWarning(): ShieldOverlayPresentation
}

