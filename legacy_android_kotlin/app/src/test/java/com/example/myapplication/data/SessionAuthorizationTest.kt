package com.example.myapplication.data

import androidx.lifecycle.ViewModelStore
import com.example.myapplication.data.remote.ApiClientFactory
import com.example.myapplication.security.PublicKeyPemEncoder
import com.example.myapplication.security.SignedRequestFactory
import com.example.myapplication.security.SignedRequestPayload
import com.example.myapplication.session.ExistingKey
import com.example.myapplication.session.MemoryBindingStore
import com.example.myapplication.session.SessionRecovery
import com.example.myapplication.ui.onboarding.OnboardingViewModel
import java.security.KeyPairGenerator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestCoroutineScheduler
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SessionAuthorizationTest {
    private val server = MockWebServer()
    private val key = KeyPairGenerator.getInstance("EC").apply { initialize(256) }.generateKeyPair().public
    private val pem = PublicKeyPemEncoder.toPem(key)
    private val store = MemoryBindingStore()
    private val recovery = SessionRecovery(store, { ExistingKey.Available(key) }, "synthetic-backend")
    private val scheduler = TestCoroutineScheduler()
    private val viewModels = ViewModelStore()
    private val signer = object : SignedRequestFactory {
        override fun createVerifyRequest(challengeId: String) = SignedRequestPayload(pem, "synthetic")
        override fun createReportRequest(clientPhoneNumber: String, feature: String) = SignedRequestPayload(pem, "synthetic")
        override fun createBlacklistCheckRequest(targetHash: String) = SignedRequestPayload(pem, "synthetic")
    }
    @Before fun setup() {
        Dispatchers.setMain(StandardTestDispatcher(scheduler))
        server.start()
        recovery.recordVerified(pem, "2030-01-01T00:00:00Z")
    }
    @After fun teardown() {
        viewModels.clear()
        Dispatchers.resetMain()
        server.shutdown()
    }
    private fun error(code: String, status: Int, errors: String = "[]", message: String = "Synthetic failure") = MockResponse()
        .setResponseCode(status).setBody(
            """{"success":false,"code":"$code","message":"$message","errors":$errors,"meta":{"retryable":${status == 503}}}""",
        )
    private fun queryRejection(status: Int, errors: String = """{"authorization":["The blacklist query could not be authorized."]}""") =
        error("blacklist_query_unauthorized", status, errors, "The blacklist query could not be authorized.")
    private fun reports() = ReportRepositoryImpl(
        ApiClientFactory.createReportApi(server.url("/api/").toString()), signer, ApiClientFactory.gson(), recovery,
    )
    private fun queries() = BlacklistQueryRepositoryImpl(
        ApiClientFactory.createBlacklistApi(server.url("/api/").toString()), signer, ApiClientFactory.gson(), recovery,
    )

    @Test fun reportBindingRejectionClearsRecoveryWithoutReplay() = runBlocking {
        server.enqueue(error("device_not_bound", 403))
        val result = reports().submitReport("+421900000001", ReportingFeature.NO_SHOW)
        assertEquals("device_not_bound", (result as ReportSubmissionResult.Failure).code)
        assertNull(recovery.state.value.metadata)
        assertTrue(store.values.isEmpty())
        assertEquals(1, server.requestCount)
    }

    @Test fun queryAuthorizationRejectionClearsHintButDoesNotAssertBindingRevocation() = runBlocking {
        val auth = object : AuthRepository {
            override suspend fun initiateOwnerAuth(phoneNumber: String): InitiateAuthResult =
                error("Rejection must not initiate owner enrollment")
            override suspend fun initiateAuth(adUrl: String): InitiateAuthResult =
                error("Rejection must not initiate SMS")
            override suspend fun verifyAuth(challengeId: String, otp: String, publicKey: String, signature: String): VerifyAuthResult =
                error("Rejection must not verify automatically")
        }
        val onboarding = OnboardingViewModel(auth, signer, diagnosticsEnabled = false, sessionRecovery = recovery)
        viewModels.put("onboarding", onboarding)
        scheduler.runCurrent()
        assertTrue(onboarding.uiState.value.isVerified)
        server.enqueue(queryRejection(401))
        val result = queries().checkTargetHash("a".repeat(64))
        scheduler.runCurrent()
        val failure = result as BlacklistQueryResult.Failure
        assertEquals("blacklist_query_unauthorized", failure.code)
        assertEquals(mapOf("authorization" to listOf("The blacklist query could not be authorized.")), failure.fieldErrors)
        assertFalse(failure.retryable)
        assertFalse(failure.responseMalformed)
        assertNull(recovery.state.value.metadata)
        assertTrue(store.values.isEmpty())
        assertTrue(recovery.state.value.message!!.contains("authorization"))
        assertFalse(onboarding.uiState.value.isVerified)
        assertNull(onboarding.uiState.value.challengeId)
        assertTrue(onboarding.uiState.value.generalError!!.contains("server rejected"))
        recovery.refresh()
        scheduler.runCurrent()
        assertFalse(onboarding.uiState.value.isVerified)
        assertEquals(1, server.requestCount)
        assertTrue(server.takeRequest().body.readUtf8().contains(ApiClientFactory.gson().toJson(pem)))
    }

    @Test fun incomingCallerAuthorizationRejectionClearsRecoveryWithoutReplay() = runBlocking {
        server.enqueue(queryRejection(401))
        val result = com.example.myapplication.shield.IncomingCallProcessor(queries())
            .lookup("+421900000001")
        assertEquals(com.example.myapplication.shield.ShieldLiveStage.QueryFailed, result.stage)
        assertEquals("The blacklist query could not be authorized.", result.errorMessage)
        assertFalse(result.retryable)
        assertEquals(com.example.myapplication.shield.ShieldOverlayState.None, result.overlayState)
        assertNull(recovery.state.value.metadata)
        assertTrue(store.values.isEmpty())
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

    @Test fun malformedReportRejectionAndUnexpectedStatusDoNotEraseHint() = runBlocking {
        val saved = store.values.toMap()
        for (response in listOf(
            error("device_not_bound", 403, errors = "[1]"), error("device_not_bound", 503),
            error("device_not_bound", 401), error("signature_invalid", 403), error("rate_limited", 429),
        )) {
            server.enqueue(response)
            reports().submitReport("+421900000001", ReportingFeature.NO_SHOW)
            assertNotNull(recovery.state.value.metadata)
            assertEquals(saved, store.values)
        }
        assertEquals(5, server.requestCount)
    }

    @Test fun malformedQueryRejectionAndUnrelatedStatusOrCodeRetainHint() = runBlocking {
        val saved = store.values.toMap()
        val responses = listOf(
            queryRejection(401, errors = "[1]"),
            queryRejection(401, errors = """{"authorization":"Invalid signature"}"""),
            queryRejection(401, errors = """{"authorization":[1]}"""),
            MockResponse().setResponseCode(401).setBody("<html>Unauthorized</html>"),
            MockResponse().setResponseCode(401),
            MockResponse().setResponseCode(401).setBody(
                """{"success":true,"code":"blacklist_query_unauthorized","message":"Unauthorized"}""",
            ),
            MockResponse().setResponseCode(401).setBody(
                """{"success":false,"code":"blacklist_query_unauthorized","errors":{}}""",
            ),
            MockResponse().setResponseCode(401).setBody(
                """{"success":false,"code":"blacklist_query_unauthorized","message":"Unauthorized","meta":{"retryable":"false"}}""",
            ),
            error("signature_invalid", 401),
            error("device_not_bound", 401),
            queryRejection(403),
            queryRejection(429),
            queryRejection(503),
        )
        for ((index, response) in responses.withIndex()) {
            server.enqueue(response)
            assertTrue(queries().checkTargetHash("a".repeat(64)) is BlacklistQueryResult.Failure)
            assertNotNull("Response $index", recovery.state.value.metadata)
            assertEquals("Response $index", saved, store.values)
            assertEquals(index + 1, server.requestCount)
        }
    }

    @Test fun queryRejectionStillAcceptsEmptyLaravelErrors() = runBlocking {
        for ((index, errors) in listOf("[]", "{}").withIndex()) {
            recovery.recordVerified(pem, "2030-01-01T00:00:00Z")
            server.enqueue(queryRejection(401, errors))
            val failure = queries().checkTargetHash("a".repeat(64)) as BlacklistQueryResult.Failure
            assertFalse(failure.responseMalformed)
            assertNull(recovery.state.value.metadata)
            assertTrue(store.values.isEmpty())
            assertEquals(index + 1, server.requestCount)
        }
    }

    @Test fun transportFailureRetainsRecovery() = runBlocking {
        val saved = store.values.toMap()
        val url = server.url("/api/").toString()
        server.shutdown()
        val repository = BlacklistQueryRepositoryImpl(
            ApiClientFactory.createBlacklistApi(url), signer, ApiClientFactory.gson(), recovery,
        )
        val result = repository.checkTargetHash("a".repeat(64)) as BlacklistQueryResult.Failure
        assertEquals("network_unavailable", result.code)
        assertNotNull(recovery.state.value.metadata)
        assertEquals(saved, store.values)
    }
}
