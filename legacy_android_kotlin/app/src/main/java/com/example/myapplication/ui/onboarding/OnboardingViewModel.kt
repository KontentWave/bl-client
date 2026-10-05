package com.example.myapplication.ui.onboarding

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.myapplication.BuildConfig
import com.example.myapplication.data.AuthRepository
import com.example.myapplication.data.AuthRepositoryImpl
import com.example.myapplication.data.InitiateAuthResult
import com.example.myapplication.data.VerifyAuthResult
import com.example.myapplication.data.remote.ApiClientFactory
import com.example.myapplication.security.CanonicalPayloadFactory
import com.example.myapplication.security.PublicKeyPemEncoder
import com.example.myapplication.security.SecuritySignedRequestFactory
import com.example.myapplication.security.SignedRequestFactory
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import android.util.Base64
import java.security.MessageDigest
import java.security.Signature
import java.time.Instant
import java.time.format.DateTimeParseException
import com.example.myapplication.ui.RetryCooldown
import com.example.myapplication.session.SessionRecovery

class OnboardingViewModel(
    private val authRepository: AuthRepository,
    private val signedRequestFactory: SignedRequestFactory,
    private val nowMillis: () -> Long = { System.nanoTime() / 1_000_000 },
    private val nowInstant: () -> Instant = { Instant.now() },
    private val diagnosticsEnabled: Boolean = BuildConfig.DEBUG,
    private val sessionRecovery: SessionRecovery? = null,
) : ViewModel() {
    private val _uiState = MutableStateFlow(OnboardingUiState(
        verifiedAt = sessionRecovery?.state?.value?.metadata?.verifiedAt,
        isSessionRestored = sessionRecovery?.state?.value?.restored == true,
        generalError = sessionRecovery?.state?.value?.message,
    ))
    val uiState: StateFlow<OnboardingUiState> = _uiState.asStateFlow()
    private val cooldown = RetryCooldown(viewModelScope, nowMillis) { remaining ->
        _uiState.update { it.copy(retryAfterSeconds = remaining) }
    }

    init {
        if (sessionRecovery != null) {
            viewModelScope.launch {
                sessionRecovery.state.collect { recovery ->
                    _uiState.update {
                        it.copy(
                            verifiedAt = recovery.metadata?.verifiedAt,
                            isSessionRestored = recovery.restored,
                            generalError = recovery.message,
                            maskedPhoneNumber = if (recovery.metadata == null) null else it.maskedPhoneNumber,
                        )
                    }
                }
            }
        }
    }

    fun onAdUrlChanged(value: String) {
        if (value.length > 2048) {
            _uiState.update { it.copy(adUrlError = "Ad URL must not exceed 2048 characters.") }
            return
        }
        _uiState.update {
            it.copy(
                adUrl = value,
                adUrlError = null,
                otpError = null,
                generalError = null,
                isRetryableError = false,
                debugSummary = null,
            )
        }
    }

    fun onOtpChanged(value: String) {
        if (uiState.value.isSubmitting || uiState.value.isVerifying || uiState.value.isChallengeLocked) return
        _uiState.update {
            it.copy(
                otp = value.filter { it in '0'..'9' }.take(6),
                otpError = null,
                generalError = null,
                isRetryableError = false,
            )
        }
    }

    fun submitAdUrl() {
        if (uiState.value.isSubmitting || uiState.value.isVerifying || cooldown.remainingSeconds > 0) return
        if (uiState.value.adUrlError == "Ad URL must not exceed 2048 characters.") return
        val currentAdUrl = uiState.value.adUrl.trim()
        if (currentAdUrl.isBlank() || currentAdUrl.length > 2048) {
            _uiState.update {
                it.copy(adUrlError = "Escort ad URL is required.")
            }
            return
        }

        _uiState.update { it.copy(isSubmitting = true) }
        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    adUrl = currentAdUrl,
                    isSubmitting = true,
                    adUrlError = null,
                    otpError = null,
                    generalError = null,
                    isRetryableError = false,
                    verifiedAt = null,
                    debugSummary = null,
                )
            }

            when (val result = authRepository.initiateAuth(currentAdUrl)) {
                is InitiateAuthResult.Success -> {
                    _uiState.update {
                        it.copy(
                            isSubmitting = false,
                            challengeId = result.challengeId,
                            maskedPhoneNumber = result.maskedPhoneNumber,
                            otpExpiresAt = result.otpExpiresAt,
                            adUrlError = null,
                            otp = "",
                            otpError = null,
                            generalError = null,
                            isRetryableError = false,
                            isChallengeLocked = false,
                            challengeRetryGuidance = null,
                            verifiedAt = null,
                            debugSummary = null,
                        )
                    }
                }

                is InitiateAuthResult.Failure -> {
                    cooldown.extend(result.retryAfterSeconds)
                    val fieldError = result.fieldErrors["ad_url"]?.firstOrNull()
                    _uiState.update {
                        it.copy(
                            isSubmitting = false,
                            adUrlError = fieldError,
                            otpError = null,
                            generalError = if (fieldError != null && it.hasInitiatedChallenge) fieldError
                                else if (fieldError == null) result.message +
                                if (result.code == "sms_dispatch_failed" || result.code == "response_parse_failed")
                                    " SMS delivery may have occurred. Wait before a manual resend." else "" else null,
                            isRetryableError = result.retryable,
                            challengeRetryGuidance = if (it.hasInitiatedChallenge && !it.isChallengeLocked)
                                "The retained challenge's server status is unknown. You can try its OTP manually before the recorded expiry, or request a new challenge when allowed."
                                else it.challengeRetryGuidance,
                            // Rejections and unavailability do not supply a replacement challenge.
                            challengeId = it.challengeId,
                            maskedPhoneNumber = it.maskedPhoneNumber,
                            otpExpiresAt = it.otpExpiresAt,
                            verifiedAt = null,
                            debugSummary = null,
                        )
                    }
                }
            }
        }
    }

    fun submitOtp() {
        val currentState = uiState.value
        if (currentState.isSubmitting || currentState.isVerifying || currentState.isVerified || cooldown.remainingSeconds > 0) return
        val challengeId = currentState.challengeId
        if (challengeId.isNullOrBlank()) {
            _uiState.update {
                it.copy(generalError = "Start a new SMS challenge before entering an OTP.")
            }
            return
        }

        if (currentState.isChallengeLocked) {
            _uiState.update {
                it.copy(generalError = "Verification is blocked for this challenge. Request a new challenge when allowed; signature or key failures may require support.")
            }
            return
        }

        val expiry = try {
            currentState.otpExpiresAt?.let(Instant::parse)
        } catch (_: DateTimeParseException) {
            null
        }
        if (expiry == null || nowInstant().isAfter(expiry)) {
            _uiState.update {
                it.copy(
                    isChallengeLocked = true,
                    challengeRetryGuidance = null,
                    generalError = if (expiry == null)
                        "The challenge expiry is unavailable. Verification was not sent. Request a new challenge when allowed."
                        else "The recorded challenge expiry has passed. Verification was not sent. Request a new challenge when allowed.",
                )
            }
            return
        }

        val currentOtp = currentState.otp.trim()
        if (currentOtp.length != 6) {
            _uiState.update {
                it.copy(otpError = "Enter the 6-digit OTP sent by SMS.")
            }
            return
        }

        _uiState.update { it.copy(isVerifying = true) }
        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    otp = currentOtp,
                    isVerifying = true,
                    otpError = null,
                    generalError = null,
                    isRetryableError = false,
                )
            }

            val signedPayload = runCatching {
                signedRequestFactory.createVerifyRequest(challengeId)
            }.getOrElse { exception ->
                _uiState.update {
                    it.copy(
                        isVerifying = false,
                        generalError = exception.message
                            ?: "Unable to prepare the device signature for verification.",
                        isRetryableError = false,
                        isChallengeLocked = true,
                        challengeRetryGuidance = null,
                    )
                }
                return@launch
            }

            val debugSummary = createDebugSummary(
                challengeId = challengeId,
                otp = currentOtp,
                publicKey = signedPayload.publicKey,
                signature = signedPayload.signature,
            )

            _uiState.update {
                it.copy(debugSummary = debugSummary)
            }

            when (
                val result = authRepository.verifyAuth(
                    challengeId = challengeId,
                    otp = currentOtp,
                    publicKey = signedPayload.publicKey,
                    signature = signedPayload.signature,
                )
            ) {
                is VerifyAuthResult.Success -> {
                    sessionRecovery?.recordVerified(signedPayload.publicKey, result.verifiedAt)
                    _uiState.update {
                        it.copy(
                            isVerifying = false,
                            otp = "",
                            otpError = null,
                            generalError = sessionRecovery?.state?.value?.message,
                            isRetryableError = false,
                            isChallengeLocked = false,
                            challengeRetryGuidance = null,
                            challengeId = result.challengeId,
                            maskedPhoneNumber = result.maskedPhoneNumber,
                            verifiedAt = if (sessionRecovery == null) result.verifiedAt
                                else sessionRecovery.state.value.metadata?.verifiedAt,
                            isSessionRestored = false,
                            debugSummary = debugSummary,
                        )
                    }
                }

                is VerifyAuthResult.Failure -> {
                    cooldown.extend(result.retryAfterSeconds)
                    val otpFieldError = result.fieldErrors["otp"]?.firstOrNull()
                    val signatureFieldError = result.fieldErrors["signature"]?.firstOrNull()
                    val challengeFieldError = result.fieldErrors["challenge_id"]?.firstOrNull()
                    // The combined OTP code cannot distinguish a typo from server-side expiry.
                    // retryable:false rejects this request, not a corrected manual submission.
                    val terminalFailure = !result.responseMalformed && result.code in setOf(
                        "signature_invalid", "challenge_not_found",
                    )
                    _uiState.update {
                        it.copy(
                            isVerifying = false,
                            otpError = otpFieldError,
                            generalError = signatureFieldError
                                ?: challengeFieldError
                                ?: if (otpFieldError == null) result.message else null,
                            isRetryableError = result.retryable,
                            isChallengeLocked = terminalFailure,
                            challengeRetryGuidance = if (terminalFailure) null
                                else if (!result.responseMalformed && result.code == "otp_invalid_or_expired")
                                    "The OTP may be incorrect or expired; the server has not confirmed which. Correct it and tap Verify OTP to try this challenge again before the recorded expiry. Each submission may count toward the server attempt limit."
                                else "The challenge's server status is unknown. Retry verification manually before the recorded expiry when allowed. Each submission may count toward the server attempt limit; a new SMS requires a separate action.",
                            debugSummary = debugSummary,
                        )
                    }
                }
            }
        }
    }

    fun startNewChallenge() {
        // Explicit resend: keep the old challenge until the server returns a new one.
        submitAdUrl()
    }

    fun restartOnboarding() {
        if (uiState.value.isSubmitting || uiState.value.isVerifying) return
        sessionRecovery?.forgetLocalHint()
        _uiState.value = OnboardingUiState(retryAfterSeconds = cooldown.remainingSeconds)
    }

    private fun createDebugSummary(
        challengeId: String,
        otp: String,
        publicKey: String,
        signature: String,
    ): String? {
        if (!diagnosticsEnabled) {
            return null
        }

        val canonicalPayload = CanonicalPayloadFactory.create(challengeId, publicKey)
        val signatureBytes = Base64.decode(signature, Base64.DEFAULT)
        val reparsedPemVerification = runCatching {
            val reparsedPublicKey = PublicKeyPemEncoder.fromPem(publicKey)
            val verifier = Signature.getInstance("SHA256withECDSA")
            verifier.initVerify(reparsedPublicKey)
            verifier.update(canonicalPayload.toByteArray(Charsets.UTF_8))
            verifier.verify(signatureBytes)
        }
        return buildString {
            appendLine("Debug build: ${BuildConfig.DEBUG}")
            appendLine("App ID: ${BuildConfig.APPLICATION_ID}")
            appendLine("API base URL: ${BuildConfig.API_BASE_URL}")
            appendLine("Challenge ID: $challengeId")
            appendLine("OTP length: ${otp.length}")
            appendLine("Public key SHA-256: ${publicKey.sha256()}")
            appendLine("Canonical payload SHA-256: ${canonicalPayload.sha256()}")
            appendLine("Canonical payload length: ${canonicalPayload.length}")
            appendLine("Signature SHA-256: ${signature.sha256()}")
            appendLine("Signature bytes: ${signatureBytes.size}")
            appendLine("Signature first byte: ${signatureBytes.firstOrNull()?.let { "%02x".format(it) } ?: "n/a"}")
            appendLine("Canonical payload JSON:")
            appendLine(canonicalPayload)
            append(
                reparsedPemVerification.fold(
                    onSuccess = { "PEM reparse self-verify: $it" },
                    onFailure = { "PEM reparse self-verify error: ${it::class.java.simpleName}: ${it.message}" },
                ),
            )
        }
    }

    private fun String.sha256(): String =
        MessageDigest.getInstance("SHA-256")
            .digest(toByteArray(Charsets.UTF_8))
            .joinToString(separator = "") { eachByte -> "%02x".format(eachByte) }

    companion object {
        fun factory(sessionRecovery: SessionRecovery? = null): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    val authApi = ApiClientFactory.createAuthApi(BuildConfig.API_BASE_URL)
                    val repository = AuthRepositoryImpl(authApi, ApiClientFactory.gson())
                    val signedRequestFactory = SecuritySignedRequestFactory()
                    return OnboardingViewModel(repository, signedRequestFactory, sessionRecovery = sessionRecovery) as T
                }
            }
    }
}
