package com.example.myapplication.ui.query

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.myapplication.data.BlacklistQueryRepository
import com.example.myapplication.data.BlacklistQueryRepositoryProvider
import com.example.myapplication.data.BlacklistQueryResult
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class BlacklistQueryViewModel(
    private val blacklistQueryRepository: BlacklistQueryRepository,
) : ViewModel() {
    private val _uiState = MutableStateFlow(BlacklistQueryUiState())
    val uiState: StateFlow<BlacklistQueryUiState> = _uiState.asStateFlow()

    fun onTargetHashChanged(value: String) {
        _uiState.update {
            it.copy(
                targetHash = value,
                targetHashError = null,
                generalError = null,
                isRetryableError = false,
                lastQueriedHash = null,
                features = emptyList(),
            )
        }
    }

    fun submitQuery() {
        val normalizedTargetHash = uiState.value.targetHash.trim().lowercase()
        if (!normalizedTargetHash.matches(TARGET_HASH_REGEX)) {
            _uiState.update {
                it.copy(
                    targetHash = normalizedTargetHash,
                    targetHashError = "Enter a valid 64-character SHA-256 target hash.",
                )
            }
            return
        }

        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    targetHash = normalizedTargetHash,
                    isSubmitting = true,
                    targetHashError = null,
                    generalError = null,
                    isRetryableError = false,
                    lastQueriedHash = null,
                    features = emptyList(),
                )
            }

            when (val result = blacklistQueryRepository.checkTargetHash(normalizedTargetHash)) {
                is BlacklistQueryResult.Success -> {
                    _uiState.update {
                        it.copy(
                            isSubmitting = false,
                            lastQueriedHash = result.targetHash,
                            features = result.features,
                        )
                    }
                }

                is BlacklistQueryResult.Failure -> {
                    _uiState.update {
                        it.copy(
                            isSubmitting = false,
                            targetHashError = result.fieldErrors["target_hash"]?.firstOrNull(),
                            generalError = result.message,
                            isRetryableError = result.retryable,
                        )
                    }
                }
            }
        }
    }

    fun reset() {
        _uiState.value = BlacklistQueryUiState()
    }

    companion object {
        private val TARGET_HASH_REGEX = Regex("^[0-9a-f]{64}$")

        fun factory(): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    val repository = BlacklistQueryRepositoryProvider.create()
                    return BlacklistQueryViewModel(repository) as T
                }
            }
    }
}

