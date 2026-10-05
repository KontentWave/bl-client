package com.example.myapplication.ui.onboarding

data class OnboardingUiState(
    val adUrl: String = "",
    val otp: String = "",
    val isSubmitting: Boolean = false,
    val isVerifying: Boolean = false,
    val adUrlError: String? = null,
    val otpError: String? = null,
    val generalError: String? = null,
    val isRetryableError: Boolean = false,
    val retryAfterSeconds: Long = 0,
    val challengeId: String? = null,
    val maskedPhoneNumber: String? = null,
    val otpExpiresAt: String? = null,
    val verifiedAt: String? = null,
    val isSessionRestored: Boolean = false,
    val isChallengeLocked: Boolean = false,
    val challengeRetryGuidance: String? = null,
    val debugSummary: String? = null,
) {
    val hasInitiatedChallenge: Boolean
        get() = !challengeId.isNullOrBlank() && verifiedAt == null

    val isVerified: Boolean
        get() = !verifiedAt.isNullOrBlank()
}
