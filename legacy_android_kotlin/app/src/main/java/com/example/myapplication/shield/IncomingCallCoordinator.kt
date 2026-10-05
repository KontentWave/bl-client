package com.example.myapplication.shield

import com.example.myapplication.phone.PhoneNumberNormalizer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

enum class IncomingCallState { Ringing, Offhook, Idle }

// Events, presentation and status publication are confined to the main dispatcher.
// PHONE_STATE has no reliable call ID: one observed ringing session owns one current number.
class IncomingCallCoordinator(
    private val scope: CoroutineScope,
    private val workerScope: CoroutineScope,
    private val lookup: suspend (String) -> ShieldLiveStatus,
    private val statusSink: ShieldLiveStatusSink,
    private val presenter: ShieldWarningPresenter,
    private val elapsedMillis: () -> Long,
    private val wallMillis: () -> Long = System::currentTimeMillis,
    private val lookupBudgetMillis: Long = LOOKUP_BUDGET_MILLIS,
) {
    private var ringing = false
    private var generation = 0L
    private var normalizedNumber: String? = null
    private var lastUnavailableInput: String? = null
    private var unavailableRecorded = false
    private var lookupJob: Job? = null

    init {
        require(lookupBudgetMillis > 0)
    }

    fun onPhoneState(
        state: IncomingCallState,
        rawNumber: String? = null,
        receivedAtMillis: Long = elapsedMillis(),
    ): Job? {
        if (state != IncomingCallState.Ringing) {
            ringing = false
            invalidateLookup()
            normalizedNumber = null
            lastUnavailableInput = null
            unavailableRecorded = false
            publish(ShieldLiveStatus(stage = ShieldLiveStage.Idle), presenter.dismissWarning())
            return null
        }

        if (!ringing) {
            ringing = true
            normalizedNumber = null
            unavailableRecorded = false
        }
        val number = rawNumber?.let { CallerNumberNormalizer().normalize(it) }
        if (number == null) {
            // A blank or malformed companion cannot overwrite an already usable caller.
            if (normalizedNumber != null) return null
            if (!unavailableRecorded || lastUnavailableInput != rawNumber) {
                unavailableRecorded = true
                lastUnavailableInput = rawNumber
                publish(
                    ShieldLiveStatus(
                        stage = if (rawNumber.isNullOrBlank()) {
                            ShieldLiveStage.MissingIncomingNumber
                        } else {
                            ShieldLiveStage.NormalizationFailed
                        },
                        rawIncomingNumber = rawNumber,
                        errorMessage = if (rawNumber.isNullOrBlank()) {
                            IncomingCallProcessor.MISSING_NUMBER_MESSAGE
                        } else {
                            PhoneNumberNormalizer.INVALID_INPUT_MESSAGE
                        },
                    ),
                    presenter.dismissWarning(),
                )
            }
            return null
        }
        if (number == normalizedNumber) return null

        invalidateLookup()
        normalizedNumber = number
        lastUnavailableInput = null
        val token = generation
        val initialPresentation = presenter.dismissWarning()
        publish(
            ShieldLiveStatus(
                stage = ShieldLiveStage.Querying,
                rawIncomingNumber = rawNumber,
                normalizedNumber = number,
            ),
            initialPresentation,
        )
        val deadline = receivedAtMillis + lookupBudgetMillis
        return scope.launch {
            val remaining = deadline - elapsedMillis()
            // Separate worker ownership lets the receiver finish even if an OEM key operation
            // ignores interruption. A cancelled worker can never publish a result.
            val worker = if (remaining > 0) workerScope.async { lookup(requireNotNull(rawNumber)) } else null
            val result = try {
                if (worker == null) null else withTimeoutOrNull(remaining) { worker.await() }
            } finally {
                worker?.cancel()
            }
            if (!ringing || token != generation) return@launch
            val currentResult = if (result == null || elapsedMillis() >= deadline) {
                ShieldLiveStatus(
                    stage = ShieldLiveStage.QueryFailed,
                    rawIncomingNumber = rawNumber,
                    normalizedNumber = number,
                    errorMessage = "Caller lookup deadline expired; this call was not checked. No automatic retry.",
                    retryable = false,
                )
            } else result
            val presentation = if (currentResult.stage == ShieldLiveStage.MatchFound) {
                presenter.showWarning(number, currentResult.features, currentResult.targetHash)
            } else initialPresentation
            publish(currentResult, presentation)
        }.also { job ->
            lookupJob = job
            job.invokeOnCompletion {
                if (lookupJob === job) lookupJob = null
            }
        }
    }

    private fun invalidateLookup() {
        generation++
        lookupJob?.cancel()
        lookupJob = null
    }

    private fun publish(status: ShieldLiveStatus, presentation: ShieldOverlayPresentation) {
        statusSink.record(
            status.copy(
                overlayState = presentation.state,
                overlayMessage = presentation.message,
                updatedAtEpochMillis = wallMillis(),
            ),
        )
    }

    companion object {
        const val LOOKUP_BUDGET_MILLIS = 5_000L
    }
}

internal fun finishBroadcastWhenComplete(work: Job?, finish: () -> Unit) {
    if (work == null) finish() else work.invokeOnCompletion { finish() }
}
