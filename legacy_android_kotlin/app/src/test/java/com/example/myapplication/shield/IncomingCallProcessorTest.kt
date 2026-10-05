package com.example.myapplication.shield

import com.example.myapplication.data.BlacklistQueryRepository
import com.example.myapplication.data.BlacklistQueryResult
import com.example.myapplication.phone.PhoneNumberVectors
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class IncomingCallProcessorTest {
    @Test
    fun supportedFormatsQueryExactlyTheGoldenHash() = runBlocking {
        val queried = mutableListOf<String>()
        val processor = processor {
            queried += it
            BlacklistQueryResult.Success(it, emptyList())
        }
        for (vector in PhoneNumberVectors.load().filter { it.normalized != null }) {
            val count = queried.size
            val status = processor.lookup(vector.raw)
            assertEquals(count + 1, queried.size)
            assertEquals(vector.hash, queried.last())
            assertEquals(vector.normalized, status.normalizedNumber)
            assertEquals(ShieldLiveStage.NoMatch, status.stage)
        }
    }

    @Test
    fun unsupportedAndUnavailableNumbersNeverQuery() = runBlocking {
        val processor = processor { error("Unsupported input must not query") }
        assertEquals(ShieldLiveStage.MissingIncomingNumber, processor.lookup(null).stage)
        for (vector in PhoneNumberVectors.load().filter { it.normalized == null }) {
            assertEquals(
                if (vector.raw.isBlank()) ShieldLiveStage.MissingIncomingNumber else ShieldLiveStage.NormalizationFailed,
                processor.lookup(vector.raw).stage,
            )
        }
    }

    @Test
    fun unavailableDoesNotClaimHiddenCallerId() = runBlocking {
        val status = processor { error("Must not query") }.lookup(null)
        assertTrue(status.errorMessage.orEmpty().contains("companion"))
        assertTrue(status.errorMessage.orEmpty().contains("not checked"))
    }

    @Test
    fun matchReturnsFeaturesWithoutPresentingAnOverlay() = runBlocking {
        val status = processor { BlacklistQueryResult.Success(it, listOf("Aggressive")) }
            .lookup("+421900000001")
        assertEquals(ShieldLiveStage.MatchFound, status.stage)
        assertEquals(listOf("Aggressive"), status.features)
        assertEquals(ShieldOverlayState.None, status.overlayState)
    }

    @Test
    fun failureIsNotNoMatchAndDoesNotRetry() = runBlocking {
        var queries = 0
        val status = processor {
            queries++
            BlacklistQueryResult.Failure("network_unavailable", "Not checked", retryable = true)
        }.lookup("+421900000001")
        assertEquals(1, queries)
        assertEquals(ShieldLiveStage.QueryFailed, status.stage)
        assertEquals("Not checked", status.errorMessage)
    }

    @Test(expected = CancellationException::class)
    fun cancellationPropagates() = runBlocking {
        processor { throw CancellationException("Cancelled") }.lookup("+421900000001")
        Unit
    }

    private fun processor(check: suspend (String) -> BlacklistQueryResult) = IncomingCallProcessor(
        object : BlacklistQueryRepository {
            override suspend fun checkTargetHash(targetHash: String) = check(targetHash)
        },
    )
}
