package com.example.myapplication.shield

interface ShieldWarningPresenter {
    fun showWarning(
        normalizedNumber: String,
        features: List<String>,
        targetHash: String?,
    ): ShieldOverlayPresentation

    fun dismissWarning(): ShieldOverlayPresentation
}
