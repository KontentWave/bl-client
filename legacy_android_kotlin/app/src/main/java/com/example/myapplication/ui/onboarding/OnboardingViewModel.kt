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

class OnboardingViewModel(
    private val authRepository: AuthRepository,
    private val signedRequestFactory: SignedRequestFactory,
) : ViewModel() {
    private val _uiState = MutableStateFlow(OnboardingUiState())
    val uiState: StateFlow<OnboardingUiState> = _uiState.asStateFlow()

    fun onAdUrlChanged(value: String) {
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
        _uiState.update {
            it.copy(
                otp = value.filter(Char::isDigit).take(6),
                otpError = null,
                generalError = null,
                isRetryableError = false,
            )
        }
    }

    fun submitAdUrl() {
        val currentAdUrl = uiState.value.adUrl.trim()
        if (currentAdUrl.isBlank()) {
            _uiState.update {
                it.copy(adUrlError = "Escort ad URL is required.")
            }
            return
        }

        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    adUrl = currentAdUrl,
                    isSubmitting = true,
                    adUrlError = null,
                    otpError = null,
                    generalError = null,
                    isRetryableError = false,
                    isChallengeLocked = false,
                    verifiedAt = null,
                    otp = "",
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
                            verifiedAt = null,
                            debugSummary = null,
                        )
                    }
                }

                is InitiateAuthResult.Failure -> {
                    val fieldError = result.fieldErrors["ad_url"]?.firstOrNull()
                    _uiState.update {
                        it.copy(
                            isSubmitting = false,
                            adUrlError = fieldError,
                            otp = "",
                            otpError = null,
                            generalError = if (fieldError == null) result.message else null,
                            isRetryableError = result.retryable,
                            isChallengeLocked = false,
                            challengeId = null,
                            maskedPhoneNumber = null,
                            otpExpiresAt = null,
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
        val challengeId = currentState.challengeId
        if (challengeId.isNullOrBlank()) {
            _uiState.update {
                it.copy(generalError = "Start a new SMS challenge before entering an OTP.")
            }
            return
        }

        if (currentState.isChallengeLocked) {
            _uiState.update {
                it.copy(generalError = "This verification attempt is closed. Start a new SMS challenge.")
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
                    _uiState.update {
                        it.copy(
                            isVerifying = false,
                            otpError = null,
                            generalError = null,
                            isRetryableError = false,
                            isChallengeLocked = false,
                            challengeId = result.challengeId,
                            maskedPhoneNumber = result.maskedPhoneNumber,
                            verifiedAt = result.verifiedAt,
                            debugSummary = debugSummary,
                        )
                    }
                }

                is VerifyAuthResult.Failure -> {
                    val otpFieldError = result.fieldErrors["otp"]?.firstOrNull()
                    val signatureFieldError = result.fieldErrors["signature"]?.firstOrNull()
                    val challengeFieldError = result.fieldErrors["challenge_id"]?.firstOrNull()
                    val terminalFailure = !result.retryable
                    _uiState.update {
                        it.copy(
                            isVerifying = false,
                            otpError = otpFieldError,
                            generalError = signatureFieldError
                                ?: challengeFieldError
                                ?: if (otpFieldError == null) result.message else null,
                            isRetryableError = result.retryable,
                            isChallengeLocked = terminalFailure,
                            debugSummary = debugSummary,
                        )
                    }
                }
            }
        }
    }

    fun startNewChallenge() {
        _uiState.update {
            it.copy(
                otp = "",
                otpError = null,
                generalError = null,
                isRetryableError = false,
                challengeId = null,
                maskedPhoneNumber = null,
                otpExpiresAt = null,
                verifiedAt = null,
                isChallengeLocked = false,
                isSubmitting = false,
                isVerifying = false,
                debugSummary = null,
            )
        }
    }

    private fun createDebugSummary(
        challengeId: String,
        otp: String,
        publicKey: String,
        signature: String,
    ): String? {
        if (!BuildConfig.DEBUG) {
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
        fun factory(): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    val authApi = ApiClientFactory.createAuthApi(BuildConfig.API_BASE_URL)
                    val repository = AuthRepositoryImpl(authApi, ApiClientFactory.gson())
                    val signedRequestFactory = SecuritySignedRequestFactory()
                    return OnboardingViewModel(repository, signedRequestFactory) as T
                }
            }
    }
}

