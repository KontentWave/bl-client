package com.example.myapplication.shield

import android.content.Context
import com.example.myapplication.data.BlacklistQueryRepository
import com.example.myapplication.data.BlacklistQueryRepositoryProvider
import com.example.myapplication.data.BlacklistQueryResult

class IncomingCallProcessor(
    private val blacklistQueryRepository: BlacklistQueryRepository,
    private val callerNumberNormalizer: CallerNumberNormalizer,
    private val callerNumberHasher: CallerNumberHasher,
    private val shieldLiveStatusSink: ShieldLiveStatusSink,
    private val shieldWarningPresenter: ShieldWarningPresenter,
) {
    suspend fun processIncomingNumber(rawIncomingNumber: String?) {
        val timestamp = System.currentTimeMillis()
        val initialOverlayPresentation = shieldWarningPresenter.dismissWarning()
        shieldLiveStatusSink.record(
            ShieldLiveStatus(
                stage = ShieldLiveStage.RingingDetected,
                rawIncomingNumber = rawIncomingNumber?.trim().orEmpty().ifBlank { null },
                overlayState = initialOverlayPresentation.state,
                overlayMessage = initialOverlayPresentation.message,
                updatedAtEpochMillis = timestamp,
            )
        )

        val nonBlankRawNumber = rawIncomingNumber?.trim()?.takeIf { it.isNotBlank() }
        if (nonBlankRawNumber == null) {
            shieldLiveStatusSink.record(
                ShieldLiveStatus(
                    stage = ShieldLiveStage.MissingIncomingNumber,
                    errorMessage = "Android did not provide an incoming caller number for this ringing event.",
                    overlayState = initialOverlayPresentation.state,
                    overlayMessage = initialOverlayPresentation.message,
                    updatedAtEpochMillis = timestamp,
                )
            )
            return
        }

        val normalizedNumber = callerNumberNormalizer.normalize(nonBlankRawNumber)
        if (normalizedNumber == null) {
            shieldLiveStatusSink.record(
                ShieldLiveStatus(
                    stage = ShieldLiveStage.NormalizationFailed,
                    rawIncomingNumber = nonBlankRawNumber,
                    errorMessage = "The incoming caller number could not be normalized to E.164.",
                    overlayState = initialOverlayPresentation.state,
                    overlayMessage = initialOverlayPresentation.message,
                    updatedAtEpochMillis = timestamp,
                )
            )
            return
        }

        val targetHash = callerNumberHasher.sha256(normalizedNumber)
        shieldLiveStatusSink.record(
            ShieldLiveStatus(
                stage = ShieldLiveStage.Querying,
                rawIncomingNumber = nonBlankRawNumber,
                normalizedNumber = normalizedNumber,
                targetHash = targetHash,
                overlayState = initialOverlayPresentation.state,
                overlayMessage = initialOverlayPresentation.message,
                updatedAtEpochMillis = timestamp,
            )
        )

        when (val result = blacklistQueryRepository.checkTargetHash(targetHash)) {
            is BlacklistQueryResult.Success -> {
                val overlayPresentation = if (result.features.isEmpty()) {
                    initialOverlayPresentation
                } else {
                    shieldWarningPresenter.showWarning(
                        normalizedNumber = normalizedNumber,
                        features = result.features,
                        targetHash = result.targetHash,
                    )
                }
                shieldLiveStatusSink.record(
                    ShieldLiveStatus(
                        stage = if (result.features.isEmpty()) ShieldLiveStage.NoMatch else ShieldLiveStage.MatchFound,
                        rawIncomingNumber = nonBlankRawNumber,
                        normalizedNumber = normalizedNumber,
                        targetHash = result.targetHash,
                        features = result.features,
                        overlayState = overlayPresentation.state,
                        overlayMessage = overlayPresentation.message,
                        updatedAtEpochMillis = System.currentTimeMillis(),
                    )
                )
            }

            is BlacklistQueryResult.Failure -> {
                shieldLiveStatusSink.record(
                    ShieldLiveStatus(
                        stage = ShieldLiveStage.QueryFailed,
                        rawIncomingNumber = nonBlankRawNumber,
                        normalizedNumber = normalizedNumber,
                        targetHash = targetHash,
                        errorMessage = result.message,
                        retryable = result.retryable,
                        overlayState = initialOverlayPresentation.state,
                        overlayMessage = initialOverlayPresentation.message,
                        updatedAtEpochMillis = System.currentTimeMillis(),
                    )
                )
            }
        }
    }

    companion object {
        fun create(context: Context): IncomingCallProcessor = IncomingCallProcessor(
            blacklistQueryRepository = BlacklistQueryRepositoryProvider.create(),
            callerNumberNormalizer = CallerNumberNormalizer(),
            callerNumberHasher = CallerNumberHasher(),
            shieldLiveStatusSink = ShieldLiveStatusStore(context),
            shieldWarningPresenter = WindowManagerShieldWarningPresenter(context),
        )
    }
}
