package com.example.myapplication.shield

import com.example.myapplication.data.BlacklistQueryRepository
import com.example.myapplication.data.BlacklistQueryResult
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class IncomingCallProcessorTest {
    @Test
    fun supportedFormatsQueryExactlyTheGoldenHashOncePerExplicitCallEvent() = runBlocking {
        val queried = mutableListOf<String>()
        val repository = object : BlacklistQueryRepository {
            override suspend fun checkTargetHash(targetHash: String): BlacklistQueryResult {
                queried += targetHash
                return BlacklistQueryResult.Success(targetHash, emptyList())
            }
        }
        val sink = RecordingShieldLiveStatusSink()
        val processor = IncomingCallProcessor(
            repository, CallerNumberNormalizer(), CallerNumberHasher(), sink, RecordingShieldWarningPresenter(),
        )
        for (vector in com.example.myapplication.phone.PhoneNumberVectors.load().filter { it.normalized != null }) {
            val count = queried.size
            processor.processIncomingNumber(vector.raw)
            assertEquals(count + 1, queried.size)
            assertEquals(vector.hash, queried.last())
            assertEquals(vector.normalized, sink.last().normalizedNumber)
            assertEquals(ShieldLiveStage.NoMatch, sink.last().stage)
        }
    }

    @Test
    fun unsupportedAndUnavailableNumbersNeverQuery() = runBlocking {
        val repository = object : BlacklistQueryRepository {
            override suspend fun checkTargetHash(targetHash: String): BlacklistQueryResult =
                error("Unsupported input must not query")
        }
        val sink = RecordingShieldLiveStatusSink()
        val processor = IncomingCallProcessor(
            repository, CallerNumberNormalizer(), CallerNumberHasher(), sink, RecordingShieldWarningPresenter(),
        )
        processor.processIncomingNumber(null)
        assertEquals(ShieldLiveStage.MissingIncomingNumber, sink.last().stage)
        for (vector in com.example.myapplication.phone.PhoneNumberVectors.load().filter { it.normalized == null }) {
            processor.processIncomingNumber(vector.raw)
            assertEquals(
                if (vector.raw.isBlank()) ShieldLiveStage.MissingIncomingNumber else ShieldLiveStage.NormalizationFailed,
                sink.last().stage,
            )
        }
    }

    @Test
    fun processIncomingNumber_recordsMissingNumberWhenAndroidDoesNotProvideCallerId() = runBlocking {
        val sink = RecordingShieldLiveStatusSink()
        val processor = IncomingCallProcessor(
            blacklistQueryRepository = FakeBlacklistQueryRepository(BlacklistQueryResult.Success("unused", emptyList())),
            callerNumberNormalizer = CallerNumberNormalizer(),
            callerNumberHasher = CallerNumberHasher(),
            shieldLiveStatusSink = sink,
            shieldWarningPresenter = RecordingShieldWarningPresenter(
                dismissPresentation = ShieldOverlayPresentation(ShieldOverlayState.None),
            ),
        )

        processor.processIncomingNumber(null)

        assertEquals(ShieldLiveStage.MissingIncomingNumber, sink.last().stage)
        assertTrue(sink.last().errorMessage.orEmpty().contains("did not provide"))
    }

    @Test
    fun processIncomingNumber_recordsNoMatchForSuccessfulEmptyResult() = runBlocking {
        val sink = RecordingShieldLiveStatusSink()
        val processor = IncomingCallProcessor(
            blacklistQueryRepository = FakeBlacklistQueryRepository(
                BlacklistQueryResult.Success(
                    targetHash = "target-hash",
                    features = emptyList(),
                )
            ),
            callerNumberNormalizer = CallerNumberNormalizer(),
            callerNumberHasher = CallerNumberHasher(),
            shieldLiveStatusSink = sink,
            shieldWarningPresenter = RecordingShieldWarningPresenter(
                dismissPresentation = ShieldOverlayPresentation(
                    state = ShieldOverlayState.Dismissed,
                    message = "Any previous overlay warning was dismissed.",
                ),
            ),
        )

        processor.processIncomingNumber("0903 223 183")

        assertEquals(ShieldLiveStage.NoMatch, sink.last().stage)
        assertEquals("+421903223183", sink.last().normalizedNumber)
        assertEquals("target-hash", sink.last().targetHash)
        assertEquals(ShieldOverlayState.Dismissed, sink.last().overlayState)
    }

    @Test
    fun processIncomingNumber_recordsMatchFoundWhenFeaturesAreReturned() = runBlocking {
        val sink = RecordingShieldLiveStatusSink()
        val processor = IncomingCallProcessor(
            blacklistQueryRepository = FakeBlacklistQueryRepository(
                BlacklistQueryResult.Success(
                    targetHash = "target-hash",
                    features = listOf("Aggressive", "No-Show"),
                )
            ),
            callerNumberNormalizer = CallerNumberNormalizer(),
            callerNumberHasher = CallerNumberHasher(),
            shieldLiveStatusSink = sink,
            shieldWarningPresenter = RecordingShieldWarningPresenter(
                showPresentation = ShieldOverlayPresentation(
                    state = ShieldOverlayState.Shown,
                    message = "Overlay warning shown.",
                ),
            ),
        )

        processor.processIncomingNumber("+421903223183")

        assertEquals(ShieldLiveStage.MatchFound, sink.last().stage)
        assertEquals(listOf("Aggressive", "No-Show"), sink.last().features)
        assertEquals(ShieldOverlayState.Shown, sink.last().overlayState)
    }

    private class FakeBlacklistQueryRepository(
        private val result: BlacklistQueryResult,
    ) : BlacklistQueryRepository {
        override suspend fun checkTargetHash(targetHash: String): BlacklistQueryResult = result
    }

    private class RecordingShieldLiveStatusSink : ShieldLiveStatusSink {
        private val statuses = mutableListOf<ShieldLiveStatus>()

        override fun record(status: ShieldLiveStatus) {
            statuses += status
        }

        fun last(): ShieldLiveStatus = statuses.last()
    }

    private class RecordingShieldWarningPresenter(
        private val showPresentation: ShieldOverlayPresentation = ShieldOverlayPresentation(ShieldOverlayState.None),
        private val dismissPresentation: ShieldOverlayPresentation = ShieldOverlayPresentation(ShieldOverlayState.None),
    ) : ShieldWarningPresenter {
        override suspend fun showWarning(
            normalizedNumber: String,
            features: List<String>,
            targetHash: String?,
        ): ShieldOverlayPresentation = showPresentation

        override suspend fun dismissWarning(): ShieldOverlayPresentation = dismissPresentation
    }
}
