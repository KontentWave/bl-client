package com.example.myapplication.shield

import com.example.myapplication.data.BlacklistQueryRepository
import com.example.myapplication.data.BlacklistQueryResult
import com.example.myapplication.phone.PhoneNumberNormalizer
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive

class IncomingCallProcessor(
    private val blacklistQueryRepository: BlacklistQueryRepository,
    private val callerNumberNormalizer: CallerNumberNormalizer = CallerNumberNormalizer(),
    private val callerNumberHasher: CallerNumberHasher = CallerNumberHasher(),
) {
    // Preparation and repository work run in the coordinator's cancellable IO worker.
    suspend fun lookup(rawIncomingNumber: String?): ShieldLiveStatus {
        val normalizedNumber = rawIncomingNumber?.let(callerNumberNormalizer::normalize)
            ?: return ShieldLiveStatus(
                stage = if (rawIncomingNumber.isNullOrBlank()) {
                    ShieldLiveStage.MissingIncomingNumber
                } else {
                    ShieldLiveStage.NormalizationFailed
                },
                rawIncomingNumber = rawIncomingNumber,
                errorMessage = if (rawIncomingNumber.isNullOrBlank()) {
                    MISSING_NUMBER_MESSAGE
                } else {
                    PhoneNumberNormalizer.INVALID_INPUT_MESSAGE
                },
            )
        currentCoroutineContext().ensureActive()
        val targetHash = callerNumberHasher.sha256(normalizedNumber)
        currentCoroutineContext().ensureActive()
        val result = blacklistQueryRepository.checkTargetHash(targetHash)
        currentCoroutineContext().ensureActive()
        return when (result) {
            is BlacklistQueryResult.Success -> ShieldLiveStatus(
                stage = if (result.features.isEmpty()) ShieldLiveStage.NoMatch else ShieldLiveStage.MatchFound,
                rawIncomingNumber = rawIncomingNumber,
                normalizedNumber = normalizedNumber,
                targetHash = result.targetHash,
                features = result.features,
            )
            is BlacklistQueryResult.Failure -> ShieldLiveStatus(
                stage = ShieldLiveStage.QueryFailed,
                rawIncomingNumber = rawIncomingNumber,
                normalizedNumber = normalizedNumber,
                targetHash = targetHash,
                errorMessage = result.message,
                retryable = result.retryable,
            )
        }
    }

    companion object {
        const val MISSING_NUMBER_MESSAGE =
            "Android has not supplied a caller number; this call is not checked. A numbered companion broadcast may still arrive."
    }
}
