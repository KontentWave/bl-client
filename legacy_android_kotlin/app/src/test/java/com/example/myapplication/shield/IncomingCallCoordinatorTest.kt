package com.example.myapplication.shield

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import org.junit.Assert.*
import org.junit.Test

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class IncomingCallCoordinatorTest {
    @Test
    fun numberlessThenNumberedCompanionQueriesOnce() = runTest {
        val fixture = fixture()
        assertNull(fixture.event(null))
        assertEquals(ShieldLiveStage.MissingIncomingNumber, fixture.last.stage)
        fixture.event(NUMBER)
        advanceUntilIdle()
        assertEquals(1, fixture.queries)
        assertEquals(ShieldLiveStage.MatchFound, fixture.last.stage)
        assertEquals(1, fixture.shown.size)
    }

    @Test
    fun numberedThenNumberlessCompanionPreservesCompletedWarning() = runTest {
        val fixture = fixture()
        fixture.event(NUMBER)
        advanceUntilIdle()
        val status = fixture.last
        val dismissals = fixture.dismissals
        fixture.event(null)
        fixture.event("")
        fixture.event("private")
        assertEquals(status, fixture.last)
        assertEquals(dismissals, fixture.dismissals)
        assertEquals(1, fixture.queries)
    }

    @Test
    fun companionsAndDuplicatesDuringLookupDoNotResetDeadlineOrCancel() = runTest {
        val result = CompletableDeferred<ShieldLiveStatus>()
        val fixture = fixture { result.await() }
        val job = fixture.event(NUMBER)
        runCurrent()
        advanceTimeBy(4_000)
        assertNull(fixture.event(null))
        assertNull(fixture.event("0900 000 001"))
        advanceTimeBy(1_000)
        runCurrent()
        assertTrue(requireNotNull(job).isCompleted)
        assertEquals(1, fixture.queries)
        assertEquals(ShieldLiveStage.QueryFailed, fixture.last.stage)
    }

    @Test
    fun numberlessOnlyIsNotCheckedWithoutQueryOrHiddenClaim() = runTest {
        val fixture = fixture()
        fixture.event(null)
        fixture.event(null)
        advanceUntilIdle()
        assertEquals(0, fixture.queries)
        assertEquals(ShieldLiveStage.MissingIncomingNumber, fixture.last.stage)
        assertTrue(fixture.last.errorMessage.orEmpty().contains("companion"))
        assertEquals(1, fixture.statuses.size)
    }

    @Test
    fun invalidInputNeverQueriesAndCanBeUpdatedByValidNumber() = runTest {
        val fixture = fixture()
        fixture.event("900000001")
        assertEquals(ShieldLiveStage.NormalizationFailed, fixture.last.stage)
        assertEquals(0, fixture.queries)
        fixture.event(NUMBER)
        advanceUntilIdle()
        assertEquals(1, fixture.queries)
        assertEquals(ShieldLiveStage.MatchFound, fixture.last.stage)
    }

    @Test
    fun equivalentNumberFormatsDeduplicateAfterSuccess() = runTest {
        val fixture = fixture()
        fixture.event(NUMBER)
        advanceUntilIdle()
        fixture.event("00421 900-000-001")
        fixture.event("421900000001")
        advanceUntilIdle()
        assertEquals(1, fixture.queries)
        assertEquals(1, fixture.shown.size)
    }

    @Test
    fun differentValidNumberSupersedesAndDismissesOldWarning() = runTest {
        val fixture = fixture()
        fixture.event(NUMBER)
        advanceUntilIdle()
        val dismissals = fixture.dismissals
        fixture.event(NEXT_NUMBER)
        assertEquals(dismissals + 1, fixture.dismissals)
        advanceUntilIdle()
        assertEquals(listOf(NUMBER, NEXT_NUMBER), fixture.shown)
        assertEquals(NEXT_NUMBER, fixture.last.normalizedNumber)
    }

    @Test
    fun answerDismissesAndIdleDoesNotReplay() = runTest {
        val fixture = fixture()
        fixture.event(NUMBER)
        advanceUntilIdle()
        fixture.transition(IncomingCallState.Offhook)
        assertEquals(ShieldLiveStage.Idle, fixture.last.stage)
        assertEquals(ShieldOverlayState.Dismissed, fixture.last.overlayState)
        fixture.transition(IncomingCallState.Idle)
        advanceUntilIdle()
        assertEquals(1, fixture.queries)
        assertEquals(1, fixture.shown.size)
    }

    @Test
    fun rejectDismissesImmediately() = runTest {
        val fixture = fixture()
        fixture.event(NUMBER)
        advanceUntilIdle()
        fixture.transition(IncomingCallState.Idle)
        assertEquals(ShieldLiveStage.Idle, fixture.last.stage)
        assertEquals(ShieldOverlayState.Dismissed, fixture.last.overlayState)
    }

    @Test
    fun sameNumberAfterIdleIsANewCall() = runTest {
        val fixture = fixture()
        fixture.event(NUMBER)
        advanceUntilIdle()
        fixture.transition(IncomingCallState.Idle)
        fixture.event(null)
        fixture.event(NUMBER)
        advanceUntilIdle()
        assertEquals(2, fixture.queries)
        assertEquals(listOf(NUMBER, NUMBER), fixture.shown)
    }

    @Test
    fun offhookCancelsLookupAndFinishesPendingResult() = runTest {
        assertTransitionCancels(IncomingCallState.Offhook)
    }

    @Test
    fun idleCancelsLookupAndFinishesPendingResult() = runTest {
        assertTransitionCancels(IncomingCallState.Idle)
    }

    @Test
    fun oldLateSuccessCannotOverwriteNewCall() = runTest {
        assertLateResultIgnored(match(NUMBER), endCall = false)
    }

    @Test
    fun oldLateFailureCannotOverwriteNewCall() = runTest {
        assertLateResultIgnored(ShieldLiveStatus(stage = ShieldLiveStage.QueryFailed), endCall = false)
    }

    @Test
    fun oldLateSuccessCannotDisplayAfterEnd() = runTest {
        assertLateResultIgnored(match(NUMBER), endCall = true)
    }

    @Test
    fun oldLateFailureCannotOverwriteEndedStatus() = runTest {
        assertLateResultIgnored(ShieldLiveStatus(stage = ShieldLiveStage.QueryFailed), endCall = true)
    }

    @Test
    fun deadlineFinishesPendingCancelsWorkerAndNeverRetries() = runTest {
        var cancelled = false
        val fixture = fixture {
            try {
                awaitCancellation()
            } finally {
                cancelled = true
            }
        }
        var finishes = 0
        val job = fixture.event(NUMBER)
        finishBroadcastWhenComplete(job) { finishes++ }
        runCurrent()
        advanceTimeBy(4_999)
        runCurrent()
        assertEquals(0, finishes)
        advanceTimeBy(1)
        runCurrent()
        assertEquals(1, finishes)
        assertTrue(cancelled)
        assertEquals(ShieldLiveStage.QueryFailed, fixture.last.stage)
        assertTrue(fixture.last.errorMessage.orEmpty().contains("not checked"))
        assertFalse(fixture.last.retryable)
        assertTrue(fixture.shown.isEmpty())
        fixture.event(NUMBER)
        advanceTimeBy(60_000)
        runCurrent()
        assertEquals(1, fixture.queries)
        assertEquals(1, finishes)
    }

    @Test
    fun preparationUsesTheSameDeadlineAndDispatchDelayReducesBudget() = runTest {
        val fixture = fixture {
            delay(4_000) // Simulated key/hash preparation, before network work.
            match(it)
        }
        advanceTimeBy(2_000)
        val job = fixture.coordinator.onPhoneState(IncomingCallState.Ringing, NUMBER, receivedAtMillis = 0)
        runCurrent()
        advanceTimeBy(3_000)
        runCurrent()
        assertTrue(requireNotNull(job).isCompleted)
        assertEquals(ShieldLiveStage.QueryFailed, fixture.last.stage)
        assertTrue(fixture.shown.isEmpty())
    }

    @Test
    fun nonCooperativeLateSuccessAfterTimeoutCannotChangeFailedStatus() = runTest {
        val release = CompletableDeferred<Unit>()
        val fixture = fixture {
            withContext(NonCancellable) { release.await() }
            match(it)
        }
        var finishes = 0
        finishBroadcastWhenComplete(fixture.event(NUMBER)) { finishes++ }
        runCurrent()
        advanceTimeBy(5_000)
        runCurrent()
        val timedOut = fixture.last
        assertEquals(ShieldLiveStage.QueryFailed, timedOut.stage)
        assertEquals(1, finishes)
        release.complete(Unit)
        advanceUntilIdle()
        assertEquals(timedOut, fixture.last)
        assertTrue(fixture.shown.isEmpty())
        assertEquals(1, finishes)
    }

    @Test
    fun alreadyExpiredBudgetDoesNotStartPreparationOrQuery() = runTest {
        val fixture = fixture()
        advanceTimeBy(5_000)
        fixture.coordinator.onPhoneState(IncomingCallState.Ringing, NUMBER, receivedAtMillis = 0)
        runCurrent()
        assertEquals(0, fixture.queries)
        assertEquals(ShieldLiveStage.QueryFailed, fixture.last.stage)
    }

    @Test
    fun pendingCompletionRunsExactlyOnceForImmediateSuccessFailureAndCancelledJob() = runTest {
        var finishes = 0
        finishBroadcastWhenComplete(null) { finishes++ }
        val completed = Job().apply { complete() }
        finishBroadcastWhenComplete(completed) { finishes++ }
        val failed = Job().apply { completeExceptionally(IllegalStateException("Synthetic failure")) }
        finishBroadcastWhenComplete(failed) { finishes++ }
        val cancelled = Job().apply { cancel(CancellationException("Synthetic cancellation")) }
        finishBroadcastWhenComplete(cancelled) { finishes++ }
        runCurrent()
        assertEquals(4, finishes)
    }

    @Test
    fun failureDoesNotRetryOnCompanionOrDuplicate() = runTest {
        val fixture = fixture { ShieldLiveStatus(stage = ShieldLiveStage.QueryFailed, errorMessage = "Unavailable") }
        var finishes = 0
        finishBroadcastWhenComplete(fixture.event(NUMBER)) { finishes++ }
        advanceUntilIdle()
        fixture.event(null)
        fixture.event(NUMBER)
        advanceUntilIdle()
        assertEquals(1, fixture.queries)
        assertEquals(1, finishes)
        assertEquals(ShieldLiveStage.QueryFailed, fixture.last.stage)
    }

    private suspend fun TestScope.assertTransitionCancels(state: IncomingCallState) {
        var cancelled = false
        val fixture = fixture {
            try { awaitCancellation() } finally { cancelled = true }
        }
        var finishes = 0
        finishBroadcastWhenComplete(fixture.event(NUMBER)) { finishes++ }
        runCurrent()
        fixture.transition(state)
        runCurrent()
        assertTrue(cancelled)
        assertEquals(1, finishes)
        assertEquals(ShieldLiveStage.Idle, fixture.last.stage)
        assertTrue(fixture.shown.isEmpty())
    }

    private suspend fun TestScope.assertLateResultIgnored(oldResult: ShieldLiveStatus, endCall: Boolean) {
        val release = CompletableDeferred<Unit>()
        var first = true
        val fixture = fixture {
            if (first) {
                first = false
                // Models a dependency which does not cooperate with cancellation.
                withContext(NonCancellable) { release.await() }
                oldResult
            } else match(it)
        }
        var finishes = 0
        finishBroadcastWhenComplete(fixture.event(NUMBER)) { finishes++ }
        runCurrent()
        if (endCall) fixture.transition(IncomingCallState.Idle) else fixture.event(NEXT_NUMBER)
        runCurrent()
        assertEquals(1, finishes)
        val current = fixture.last
        release.complete(Unit)
        advanceUntilIdle()
        assertEquals(current, fixture.last)
        assertEquals(if (endCall) emptyList<String>() else listOf(NEXT_NUMBER), fixture.shown)
    }

    private fun TestScope.fixture(
        lookup: suspend (String) -> ShieldLiveStatus = { match(it) },
    ): Fixture = Fixture(this, lookup)

    private class Fixture(scope: TestScope, lookup: suspend (String) -> ShieldLiveStatus) {
        val statuses = mutableListOf<ShieldLiveStatus>()
        val shown = mutableListOf<String>()
        var dismissals = 0
        var queries = 0
        val last get() = statuses.last()
        val coordinator = IncomingCallCoordinator(
            scope = scope,
            workerScope = scope.backgroundScope,
            lookup = { queries++; lookup(it) },
            statusSink = ShieldLiveStatusSink { statuses += it },
            presenter = object : ShieldWarningPresenter {
                override fun showWarning(normalizedNumber: String, features: List<String>, targetHash: String?): ShieldOverlayPresentation {
                    shown += normalizedNumber
                    return ShieldOverlayPresentation(ShieldOverlayState.Shown)
                }
                override fun dismissWarning(): ShieldOverlayPresentation {
                    dismissals++
                    return ShieldOverlayPresentation(ShieldOverlayState.Dismissed)
                }
            },
            elapsedMillis = { scope.testScheduler.currentTime },
            wallMillis = { scope.testScheduler.currentTime + 1 },
        )
        fun event(number: String?) = coordinator.onPhoneState(IncomingCallState.Ringing, number)
        fun transition(state: IncomingCallState) = coordinator.onPhoneState(state)
    }

    companion object {
        private const val NUMBER = "+421900000001"
        private const val NEXT_NUMBER = "+421900000002"
        private fun match(number: String) = ShieldLiveStatus(
            stage = ShieldLiveStage.MatchFound,
            normalizedNumber = number,
            features = listOf("Aggressive"),
        )
    }
}
