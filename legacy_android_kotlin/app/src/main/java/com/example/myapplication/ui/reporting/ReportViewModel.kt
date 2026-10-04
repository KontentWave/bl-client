package com.example.myapplication.ui.reporting

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.myapplication.BuildConfig
import com.example.myapplication.data.ReportRepository
import com.example.myapplication.data.ReportRepositoryImpl
import com.example.myapplication.data.ReportSubmissionResult
import com.example.myapplication.data.ReportingFeature
import com.example.myapplication.data.remote.ApiClientFactory
import com.example.myapplication.security.SecuritySignedRequestFactory
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import com.example.myapplication.ui.RetryCooldown
import com.example.myapplication.session.SessionRecovery
import com.example.myapplication.phone.PhoneNumberNormalizer

class ReportViewModel(
    private val reportRepository: ReportRepository,
    private val nowMillis: () -> Long = { System.nanoTime() / 1_000_000 },
) : ViewModel() {
    private val _uiState = MutableStateFlow(ReportUiState())
    val uiState: StateFlow<ReportUiState> = _uiState.asStateFlow()
    private val cooldown = RetryCooldown(viewModelScope, nowMillis) { remaining ->
        _uiState.update { it.copy(retryAfterSeconds = remaining) }
    }

    fun onClientPhoneNumberChanged(value: String) {
        _uiState.update {
            it.copy(
                clientPhoneNumber = value,
                clientPhoneNumberError = null,
                generalError = null,
                isRetryableError = false,
                lastSubmittedFeatureLabel = null,
                lastSubmittedLevel = null,
                lastUniqueReporterCount = null,
                lastReadyForSync = null,
            )
        }
    }

    fun onFeatureSelected(feature: ReportingFeature) {
        _uiState.update {
            it.copy(
                selectedFeature = feature,
                featureError = null,
                generalError = null,
                isRetryableError = false,
                lastSubmittedFeatureLabel = null,
                lastSubmittedLevel = null,
                lastUniqueReporterCount = null,
                lastReadyForSync = null,
            )
        }
    }

    fun submitReport() {
        val currentState = uiState.value
        if (currentState.isSubmitting || cooldown.remainingSeconds > 0) return
        val normalizedClientPhoneNumber = PhoneNumberNormalizer.normalize(currentState.clientPhoneNumber)
        val selectedFeature = currentState.selectedFeature

        var hasValidationError = false
        if (normalizedClientPhoneNumber == null) {
            hasValidationError = true
            _uiState.update {
                it.copy(clientPhoneNumberError = if (currentState.clientPhoneNumber.isBlank()) {
                    "Client phone number is required."
                } else {
                    PhoneNumberNormalizer.INVALID_INPUT_MESSAGE
                })
            }
        }
        if (selectedFeature == null) {
            hasValidationError = true
            _uiState.update { it.copy(featureError = "Select a report feature.") }
        }
        if (hasValidationError || selectedFeature == null || normalizedClientPhoneNumber == null) {
            return
        }

        _uiState.update { it.copy(isSubmitting = true) }
        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    clientPhoneNumber = normalizedClientPhoneNumber,
                    isSubmitting = true,
                    clientPhoneNumberError = null,
                    featureError = null,
                    generalError = null,
                    isRetryableError = false,
                    lastSubmittedFeatureLabel = null,
                    lastSubmittedLevel = null,
                    lastUniqueReporterCount = null,
                    lastReadyForSync = null,
                )
            }

            when (val result = reportRepository.submitReport(normalizedClientPhoneNumber, selectedFeature)) {
                is ReportSubmissionResult.Success -> {
                    _uiState.update {
                        it.copy(
                            isSubmitting = false,
                            generalError = null,
                            isRetryableError = false,
                            lastSubmittedFeatureLabel = result.featureLabel,
                            lastSubmittedLevel = result.level,
                            lastUniqueReporterCount = result.uniqueReporterCount,
                            lastReadyForSync = result.readyForSync,
                        )
                    }
                }

                is ReportSubmissionResult.Failure -> {
                    cooldown.extend(result.retryAfterSeconds)
                    _uiState.update {
                        it.copy(
                            isSubmitting = false,
                            clientPhoneNumberError = result.fieldErrors["client_phone_number"]?.firstOrNull(),
                            featureError = result.fieldErrors["feature"]?.firstOrNull(),
                            generalError = result.message,
                            isRetryableError = result.retryable,
                        )
                    }
                }
            }
        }
    }

    fun reset() {
        if (uiState.value.isSubmitting) return
        _uiState.value = ReportUiState(retryAfterSeconds = cooldown.remainingSeconds)
    }

    companion object {
        fun factory(sessionRecovery: SessionRecovery? = null): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    val reportApi = ApiClientFactory.createReportApi(BuildConfig.API_BASE_URL)
                    val repository = ReportRepositoryImpl(
                        reportApi = reportApi,
                        signedRequestFactory = SecuritySignedRequestFactory(),
                        gson = ApiClientFactory.gson(),
                        sessionRecovery = sessionRecovery,
                    )
                    return ReportViewModel(repository) as T
                }
            }
    }
}
