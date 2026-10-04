package com.example.myapplication.data

import com.example.myapplication.data.remote.ApiClientFactory
import com.example.myapplication.security.PublicKeyPemEncoder
import com.example.myapplication.security.SignedRequestFactory
import com.example.myapplication.security.SignedRequestPayload
import com.example.myapplication.session.ExistingKey
import com.example.myapplication.session.MemoryBindingStore
import com.example.myapplication.session.SessionRecovery
import java.security.KeyPairGenerator
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class SessionAuthorizationTest {
    private val server = MockWebServer()
    private val key = KeyPairGenerator.getInstance("EC").apply { initialize(256) }.generateKeyPair().public
    private val pem = PublicKeyPemEncoder.toPem(key)
    private val store = MemoryBindingStore()
    private val recovery = SessionRecovery(store, { ExistingKey.Available(key) }, "synthetic-backend")
    private val signer = object : SignedRequestFactory {
        override fun createVerifyRequest(challengeId: String) = SignedRequestPayload(pem, "synthetic")
        override fun createReportRequest(clientPhoneNumber: String, feature: String) = SignedRequestPayload(pem, "synthetic")
        override fun createBlacklistCheckRequest(targetHash: String) = SignedRequestPayload(pem, "synthetic")
    }
    @Before fun setup() { server.start(); recovery.recordVerified(pem, "2030-01-01T00:00:00Z") }
    @After fun teardown() { server.shutdown() }
    private fun error(code: String, status: Int = 403, malformed: Boolean = false) = MockResponse()
        .setResponseCode(status).setBody(
            """{"success":false,"code":"$code","message":"Synthetic failure","errors":${if (malformed) "[1]" else "[]"},"meta":{"retryable":${status == 503}}}""",
        )
    private fun reports() = ReportRepositoryImpl(
        ApiClientFactory.createReportApi(server.url("/api/").toString()), signer, ApiClientFactory.gson(), recovery,
    )
    private fun queries() = BlacklistQueryRepositoryImpl(
        ApiClientFactory.createBlacklistApi(server.url("/api/").toString()), signer, ApiClientFactory.gson(), recovery,
    )

    @Test fun reportBindingRejectionClearsRecoveryWithoutReplay() = runBlocking {
        server.enqueue(error("device_not_bound"))
        val result = reports().submitReport("+421900000001", ReportingFeature.NO_SHOW)
        assertEquals("device_not_bound", (result as ReportSubmissionResult.Failure).code)
        assertNull(recovery.state.value.metadata)
        assertTrue(store.values.isEmpty())
        assertEquals(1, server.requestCount)
    }

    @Test fun queryAuthorizationRejectionClearsHintButDoesNotAssertBindingRevocation() = runBlocking {
        server.enqueue(error("blacklist_query_unauthorized"))
        val result = queries().checkTargetHash("a".repeat(64))
        assertEquals("blacklist_query_unauthorized", (result as BlacklistQueryResult.Failure).code)
        assertNull(recovery.state.value.metadata)
        assertTrue(recovery.state.value.message!!.contains("authorization"))
        assertEquals(1, server.requestCount)
    }

    @Test fun temporaryServerFailureRetainsHintForBothOperations() = runBlocking {
        val saved = store.values
        server.enqueue(error("abuse_protection_unavailable", 503))
        server.enqueue(error("abuse_protection_unavailable", 503))
        assertTrue((reports().submitReport("+421900000001", ReportingFeature.NO_SHOW) as ReportSubmissionResult.Failure).retryable)
        assertTrue((queries().checkTargetHash("a".repeat(64)) as BlacklistQueryResult.Failure).retryable)
        assertEquals(saved, store.values)
        assertNotNull(recovery.state.value.metadata)
        assertEquals(2, server.requestCount)
    }

    @Test fun malformedRejectionAndUnexpectedStatusDoNotEraseHint() = runBlocking {
        for (response in listOf(
            error("device_not_bound", malformed = true), error("device_not_bound", 503),
            error("signature_invalid"), error("rate_limited", 429),
        )) {
            server.enqueue(response)
            reports().submitReport("+421900000001", ReportingFeature.NO_SHOW)
            assertNotNull(recovery.state.value.metadata)
        }
        server.enqueue(error("blacklist_query_unauthorized", malformed = true))
        queries().checkTargetHash("a".repeat(64))
        assertNotNull(recovery.state.value.metadata)
        assertEquals(5, server.requestCount)
    }

    @Test fun transportFailureRetainsRecovery() = runBlocking {
        val url = server.url("/api/").toString()
        server.shutdown()
        val repository = BlacklistQueryRepositoryImpl(
            ApiClientFactory.createBlacklistApi(url), signer, ApiClientFactory.gson(), recovery,
        )
        val result = repository.checkTargetHash("a".repeat(64)) as BlacklistQueryResult.Failure
        assertEquals("network_unavailable", result.code)
        assertNotNull(recovery.state.value.metadata)
        assertFalse(store.values.isEmpty())
    }
}
