package com.example.myapplication.session

import com.example.myapplication.data.remote.LifecycleApi
import com.example.myapplication.security.CanonicalPayloadFactory
import com.example.myapplication.security.PublicKeyPemEncoder
import com.example.myapplication.security.SignedRequestFactory
import com.example.myapplication.security.SignedRequestPayload
import com.example.myapplication.security.EphemeralSignedRequestFactory
import com.example.myapplication.data.remote.ApiClientFactory
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import java.security.KeyPairGenerator
import java.time.Instant
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.ExperimentalCoroutinesApi
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.*
import org.junit.Test
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import java.security.Signature
import java.util.Base64
import retrofit2.Response

@OptIn(ExperimentalCoroutinesApi::class)
class RenewalCoordinatorTest {
    private val key = KeyPairGenerator.getInstance("EC").apply { initialize(256) }.generateKeyPair().public
    private val pem = PublicKeyPemEncoder.toPem(key)
    private val store = MemoryBindingStore()
    private val verified = "2030-01-01T00:00:00Z"
    private var now = Instant.parse(verified).toEpochMilli()
    private val id = "11111111-1111-4111-8111-111111111111"
    private var requests = 0
    private var signings = 0
    private var ids = 0
    private val recovery = SessionRecovery(store, { ExistingKey.Available(key) }, "backend", { now })
    private val lifecycle = EnrollmentLifecycle(id, "ad", "active", 1, verified, null,
        "2030-01-02T00:00:00Z", null, null)
    private val signer = object : SignedRequestFactory {
        override fun createVerifyRequest(challengeId: String): SignedRequestPayload = error("No automatic OTP")
        override fun createReportRequest(clientPhoneNumber: String, feature: String): SignedRequestPayload = error("No replay")
        override fun createBlacklistCheckRequest(targetHash: String): SignedRequestPayload = error("No query probe")
        override fun createLifecycleRequest(requestId: String, enrollmentId: String?, nextRenewalAt: String?): SignedRequestPayload {
            signings++
            return SignedRequestPayload(pem, "synthetic")
        }
    }
    private var handler: suspend (JsonObject) -> Response<JsonObject> = { request ->
        success(request, if (request.has("enrollment_id")) "auth.renewed" else "auth.status", renewed())
    }
    private val api = object : LifecycleApi {
        override suspend fun status(request: JsonObject) = handler(request).also { requests++ }
        override suspend fun renew(request: JsonObject) = handler(request).also { requests++ }
    }
    private fun coordinator() = RenewalCoordinator(recovery, api, signer, { now },
        { "22222222-2222-4222-8222-${(++ids).toString().padStart(12, '0')}" })
    private fun renewed() = lifecycle.copy(version = 2, lastRenewedAt = Instant.ofEpochMilli(now).toString(),
        nextRenewalAt = Instant.ofEpochMilli(now + RenewalCoordinator.DAY).toString())
    private fun success(request: JsonObject, code: String, state: EnrollmentLifecycle): Response<JsonObject> =
        Response.success(JsonParser.parseString(
            """{"success":true,"code":"$code","data":{"request_id":${request.get("request_id")},"server_time":"${Instant.ofEpochMilli(now)}","lifecycle":${state.json()}},"meta":{}}""",
        ).asJsonObject)
    private fun failure(code: String = "auth.ad_status_unavailable", status: Int = 503, retry: Long = 900,
        state: EnrollmentLifecycle? = null, request: JsonObject? = null): Response<JsonObject> =
        Response.error(status, (
            """{"success":false,"code":"$code","message":"Synthetic failure","errors":{"authorization":["Synthetic failure"]},"meta":{"retryable":${status == 503 || status == 429},"retry_after":$retry""" +
                (if (state != null) ""","request_id":${request!!.get("request_id")},"server_time":"${Instant.ofEpochMilli(now)}","lifecycle":${state.json()}""" else "") +
                "}}").toResponseBody("application/json".toMediaType()))
    private fun enrolled() { recovery.recordVerified(pem, verified, lifecycle) }
    private fun due() { now += RenewalCoordinator.DAY }

    @Test fun noRenewalBeforeDueAndSameKeyRenewalAtEquality() = runBlocking {
        enrolled()
        val coordinator = coordinator()
        coordinator.runEligible()
        now += RenewalCoordinator.DAY - 1
        coordinator.runEligible()
        assertEquals(0, requests)
        now++
        coordinator.runEligible()
        assertEquals(1, requests)
        assertEquals(verified, recovery.state.value.metadata!!.verifiedAt)
        assertEquals(renewed(), recovery.state.value.metadata!!.lifecycle)
        assertEquals(1, signings)
        coordinator.runEligible()
        assertEquals(1, requests)
    }

    @Test fun overdueCollapsesMissedDaysIntoOneRequest() = runBlocking {
        enrolled(); now += 5 * RenewalCoordinator.DAY
        coordinator().runEligible()
        assertEquals(1, requests)
        assertEquals(now + RenewalCoordinator.DAY, coordinator().nextEligibleAt())
    }

    @Test fun delayedVerificationDeliveryDoesNotRestartTheServerRenewalInterval() = runBlocking {
        now += 2 * RenewalCoordinator.DAY
        enrolled()
        assertEquals(Instant.parse(lifecycle.nextRenewalAt).toEpochMilli(), coordinator().nextEligibleAt())
        recovery.refresh()
        assertEquals(lifecycle, recovery.state.value.metadata?.lifecycle)
        val restored = SessionRecovery(store, { ExistingKey.Available(key) }, "backend", { now })
        assertEquals(lifecycle, restored.state.value.metadata?.lifecycle)
        coordinator().runEligible()
        assertEquals(1, requests)
        assertEquals(verified, recovery.state.value.metadata!!.verifiedAt)
    }

    @Test fun unrelatedRateLimitStatusDoesNotAuthorizeAutomaticRetries() = runBlocking {
        enrolled(); due()
        handler = { failure("blacklist_query_unauthorized", 429) }
        coordinator().runEligible()
        assertEquals(Long.MAX_VALUE, coordinator().nextEligibleAt())
        now += RenewalCoordinator.DAY
        coordinator().runEligible()
        assertEquals(1, requests)
        assertEquals(lifecycle, recovery.state.value.metadata!!.lifecycle)
    }

    @Test fun malformedCycleSnapshotsRetainStateAndConsumeTheBoundedFailureLadder() = runBlocking {
        for (wrongKind in listOf(true, false)) {
            enrolled()
            now = Instant.parse(verified).toEpochMilli() + RenewalCoordinator.DAY
            handler = { request ->
                val snapshot = if (wrongKind) lifecycle.copy(kind = "phone_only", version = 2,
                    nextRenewalAt = null) else renewed()
                val root = JsonParser.parseString(failure("auth.renewal_cycle_changed", 409,
                    state = snapshot, request = request).errorBody()!!.string()).asJsonObject
                if (!wrongKind) root.getAsJsonObject("meta").addProperty("server_time", "2029-12-31T23:59:59Z")
                Response.error(409, root.toString().toResponseBody("application/json".toMediaType()))
            }
            coordinator().runEligible()
            assertEquals(lifecycle, recovery.state.value.metadata!!.lifecycle)
            val attempt = recovery.state.value.metadata!!.renewalAttempt!!
            assertEquals(1, attempt.failures)
            assertNull(attempt.requestId)
            assertEquals(now + RenewalCoordinator.FIFTEEN_MINUTES, coordinator().nextEligibleAt())
        }
    }

    @Test fun processRecreationPreservesDueAndNoAutomaticOtp() = runBlocking {
        enrolled()
        val restored = SessionRecovery(store, { ExistingKey.Available(key) }, "backend", { now })
        val coordinator = RenewalCoordinator(restored, api, signer, { now })
        coordinator.runEligible()
        assertEquals(0, requests)
        due(); coordinator.runEligible()
        assertEquals(1, requests)
        assertEquals(verified, restored.state.value.metadata!!.verifiedAt)
    }

    @Test fun failedPortalRetainsStateAndHonorsLongerRetryAfterAcrossRecreation() = runBlocking {
        enrolled(); due()
        handler = { failure(retry = 7200) }
        coordinator().runEligible()
        assertEquals(lifecycle, recovery.state.value.metadata!!.lifecycle)
        assertEquals(now + 7_200_000, coordinator().nextEligibleAt())
        recovery.refresh()
        now += 7_199_999
        coordinator().runEligible()
        assertEquals(1, requests)
        now++; coordinator().runEligible()
        assertEquals(2, requests)
    }

    @Test fun retryLadderAndFourAttemptWindowCannotBeBypassedByRepeatedTriggers() = runBlocking {
        enrolled(); due(); handler = { failure() }
        val first = now
        val coordinator = coordinator()
        coordinator.runEligible()
        assertEquals(first + 900_000, coordinator.nextEligibleAt())
        now = coordinator.nextEligibleAt()!!; coordinator.runEligible()
        assertEquals(now + 3_600_000, coordinator.nextEligibleAt())
        now = coordinator.nextEligibleAt()!!; coordinator.runEligible()
        assertEquals(now + 21_600_000, coordinator.nextEligibleAt())
        now = coordinator.nextEligibleAt()!!; coordinator.runEligible()
        assertEquals(first + RenewalCoordinator.DAY, coordinator.nextEligibleAt())
        repeat(5) { coordinator.runEligible() }
        assertEquals(4, requests)
    }

    @Test fun authoritativeOffboardingIsDurableAndCannotReactivateWithoutExplicitVerification() = runBlocking {
        enrolled(); due()
        handler = { failure("device_offboarded", 403, state = lifecycle.copy(
            state = "offboarded", version = 2, nextRenewalAt = null,
            offboardedAt = Instant.ofEpochMilli(now).toString(), reason = "ad_disabled"), request = it) }
        coordinator().runEligible()
        assertNull(recovery.state.value.metadata)
        assertTrue(store.values["lifecycle"].toString().contains("offboarded"))
        recovery.refresh(); coordinator().runEligible()
        assertEquals(1, requests)
        recovery.recordVerified(pem, Instant.ofEpochMilli(now).toString(),
            lifecycle.copy(enrollmentId = "33333333-3333-4333-8333-333333333333",
                verifiedAt = Instant.ofEpochMilli(now).toString(), nextRenewalAt = Instant.ofEpochMilli(now + RenewalCoordinator.DAY).toString()))
        assertNotNull(recovery.state.value.metadata)
    }

    @Test fun newerSameKeyEnrollmentRejectsStaleRenewal() = runBlocking {
        enrolled(); due()
        handler = { request ->
            val old = renewed()
            recovery.recordVerified(pem, verified, lifecycle.copy(enrollmentId = "33333333-3333-4333-8333-333333333333"))
            success(request, "auth.renewed", old)
        }
        coordinator().runEligible()
        assertEquals("33333333-3333-4333-8333-333333333333", recovery.state.value.metadata!!.lifecycle!!.enrollmentId)
    }

    @Test fun differentBackendNeverBootstrapsOrRenews() = runBlocking {
        enrolled()
        val foreign = SessionRecovery(store, { error("Must not inspect foreign key") }, "different-backend", { now })
        RenewalCoordinator(foreign, api, signer, { now }).runEligible()
        assertEquals(0, requests)
        assertEquals("backend", store.values["backend_scope"])
    }

    @Test fun failedPendingWriteSendsNothing() = runBlocking {
        enrolled(); due(); store.writable = false
        coordinator().runEligible()
        assertEquals(0, requests)
        assertEquals(0, signings)
        assertTrue(recovery.state.value.message!!.contains("could not be saved"))
    }

    @Test fun wrongRequestIdMalformedAndUnrelatedErrorsNeverAdvanceOrOffboard() = runBlocking {
        for (kind in 0..3) {
            enrolled(); due()
            handler = { request -> when (kind) {
                0 -> success(JsonObject().apply { addProperty("request_id", "99999999-9999-4999-8999-999999999999") }, "auth.renewed", renewed())
                1 -> Response.success(JsonObject())
                2 -> failure("signature_invalid", 401)
                else -> failure("device_offboarded", 503)
            } }
            coordinator().runEligible()
            assertEquals(lifecycle, recovery.state.value.metadata!!.lifecycle)
        }
    }

    @Test fun phoneOnlyAndLegacyStatusAreExplicitExemptionsWithoutPortalRenewal() = runBlocking {
        for (kind in listOf("phone_only", "legacy")) {
            recovery.recordVerified(pem, verified)
            handler = { request -> success(request, "auth.status",
                lifecycle.copy(kind = kind, nextRenewalAt = null)) }
            coordinator().runEligible()
            assertEquals(kind, recovery.state.value.metadata!!.lifecycle!!.kind)
            due(); coordinator().runEligible()
        }
        assertEquals(2, requests)
    }

    @Test fun concurrentTriggersDeduplicateWithoutWaiting() = runBlocking {
        enrolled(); due()
        val started = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        handler = { request -> started.complete(Unit); release.await(); success(request, "auth.renewed", renewed()) }
        val coordinator = coordinator()
        val one = async { coordinator.runEligible() }
        started.await()
        coordinator.runEligible()
        release.complete(Unit); one.await()
        assertEquals(1, requests)
    }

    @Test fun interruptedOperationRetainsPendingIdentityForBoundedResume() = runBlocking {
        enrolled(); due()
        handler = { throw kotlinx.coroutines.CancellationException("Interrupted") }
        try { coordinator().runEligible() } catch (_: kotlinx.coroutines.CancellationException) { }
        val pending = recovery.state.value.metadata!!.renewalAttempt!!
        assertNotNull(pending.requestId)
        recovery.refresh(); now = pending.eligibleAt
        handler = { request ->
            assertEquals(pending.requestId, request.get("request_id").asString)
            success(request, "auth.renewed", renewed())
        }
        coordinator().runEligible()
        assertEquals(1, requests)
    }

    @Test fun canonicalLifecyclePayloadDomainSeparatesAndEscapesPhpNulls() {
        assertEquals("""{"operation":"auth.renew","enrollment_id":"e","request_id":"r","next_renewal_at":null,"public_key":"a\/b\nc"}""",
            CanonicalPayloadFactory.createRenewal("e", "r", null, "a/b\nc"))
        assertEquals("""{"operation":"auth.status","request_id":"r","public_key":"key"}""",
            CanonicalPayloadFactory.createLifecycleStatus("r", "key"))
    }

    @Test fun failedSuccessWritePreservesPendingCycleAndHistoricalDeadline() = runBlocking {
        enrolled(); due()
        handler = { request -> store.writable = false; success(request, "auth.renewed", renewed()) }
        coordinator().runEligible()
        assertEquals(lifecycle, recovery.state.value.metadata!!.lifecycle)
        assertNotNull(recovery.state.value.metadata!!.renewalAttempt!!.requestId)
        assertTrue(recovery.state.value.message!!.contains("could not be saved"))
    }

    @Test fun terminalWriteFailureCannotClearForeignBackendHintOnRefresh() {
        recovery.recordVerified(pem, verified)
        val foreignHint = store.values.toMap()
        val current = SessionRecovery(store, { ExistingKey.Available(key) }, "new-backend", { now })
        store.writable = false
        current.recordVerified(pem, verified, lifecycle)
        current.applyLifecycle(current.ticket()!!, pem, lifecycle.copy(state = "offboarded", version = 2,
            nextRenewalAt = null, offboardedAt = "2030-01-02T00:00:00Z", reason = "contact_changed"))
        assertTrue(current.state.value.offboarded)
        store.writable = true
        current.refresh()
        assertEquals(foreignHint, store.values)
        assertNull(current.state.value.metadata)
    }

    @Test fun staleSameKeyAuthorizationRejectionCannotEraseExplicitNewGeneration() {
        enrolled()
        val old = recovery.ticket()!!
        recovery.recordVerified(pem, verified, lifecycle.copy(enrollmentId = "33333333-3333-4333-8333-333333333333"))
        recovery.authorizationRejected(pem, old)
        assertNotNull(recovery.state.value.metadata)
        assertEquals("33333333-3333-4333-8333-333333333333", recovery.state.value.metadata!!.lifecycle!!.enrollmentId)
    }

    @Test fun malformedPersistedRetryRecordDoesNotPermitEarlyRequest() {
        enrolled()
        store.values = store.values + ("renewal_attempt" to "{}")
        recovery.refresh()
        assertNull(recovery.state.value.metadata)
        assertTrue(recovery.state.value.message!!.contains("invalid"))
    }

    @Test fun equalVersionChangedStateCannotOffboardCurrentEnrollment() {
        enrolled()
        assertFalse(recovery.applyLifecycle(recovery.ticket()!!, pem, lifecycle.copy(state = "offboarded",
            nextRenewalAt = null, offboardedAt = "2030-01-02T00:00:00Z", reason = "ad_disabled")))
        assertNotNull(recovery.state.value.metadata)
    }

    @Test fun differentSigningKeyNeverReachesNetwork() = runBlocking {
        enrolled(); due()
        val different = EphemeralSignedRequestFactory()
        RenewalCoordinator(recovery, api, different, { now }).runEligible()
        assertEquals(0, requests)
    }

    @Test fun backgroundJobSchedulesDueAndRunsOnlyWithNetwork() = runTest {
        enrolled()
        var online = false
        val scheduled = mutableListOf<Long?>()
        val execution = RenewalExecution(recovery, coordinator(), backgroundScope,
            { scheduled += it; true }, { online }, { now })
        runCurrent()
        assertEquals(listOf(now + RenewalCoordinator.DAY), scheduled)
        due()
        execution.execute()!!.join()
        assertEquals(0, requests)
        online = true
        execution.execute()!!.join()
        assertEquals(1, requests)
        assertEquals(now + RenewalCoordinator.DAY, scheduled.last())
    }

    @Test fun foregroundResumeAndNetworkReturnCatchUpWithoutWaitingForAnotherDay() = runTest {
        enrolled(); due()
        var online = false
        val received = CompletableDeferred<Unit>()
        handler = { request -> received.complete(Unit); success(request, "auth.renewed", renewed()) }
        val execution = RenewalExecution(recovery, coordinator(), backgroundScope,
            { true }, { online }, { now })
        execution.foreground(true)
        runCurrent()
        assertEquals(0, requests)
        online = true
        execution.networkChanged()
        received.await()
        runCurrent()
        assertEquals(1, requests)
        execution.foreground(false)
    }

    @Test fun backgroundOffboardingCancelsScheduleAndDoesNotInvokeEnrollment() = runTest {
        enrolled(); due()
        val scheduled = mutableListOf<Long?>()
        handler = { failure("device_offboarded", 403, state = lifecycle.copy(
            state = "offboarded", version = 2, nextRenewalAt = null,
            offboardedAt = Instant.ofEpochMilli(now).toString(), reason = "contact_changed"), request = it) }
        val execution = RenewalExecution(recovery, coordinator(), backgroundScope,
            { scheduled += it; true }, { true }, { now })
        execution.execute()!!.join()
        assertNull(scheduled.last())
        assertTrue(recovery.state.value.offboarded)
        assertEquals(1, signings)
    }

    @Test fun backgroundSchedulingFailureIsVisibleAndDoesNotLoop() = runTest {
        enrolled()
        var schedules = 0
        RenewalExecution(recovery, coordinator(), backgroundScope,
            { schedules++; false }, { true }, { now })
        runCurrent()
        assertEquals(1, schedules)
        assertTrue(recovery.state.value.message!!.contains("could not schedule"))
        assertEquals(0, requests)
    }

    @Test fun stillOpenForegroundTimerRenewsAtDueWithoutActivityReplay() = runTest {
        enrolled()
        val received = CompletableDeferred<Unit>()
        handler = { request -> received.complete(Unit); success(request, "auth.renewed", renewed()) }
        val execution = RenewalExecution(recovery, coordinator(), backgroundScope,
            { true }, { true }, { now })
        execution.foreground(true)
        runCurrent()
        assertEquals(0, requests)
        due()
        advanceTimeBy(RenewalCoordinator.DAY)
        runCurrent()
        received.await()
        runCurrent()
        assertEquals(1, requests)
        execution.foreground(false)
    }

    @Test fun staleEnrollmentGetsOneBoundedStatusReconciliationWithoutAdoptingNewEnrollment() = runBlocking {
        enrolled(); due()
        handler = { failure("auth.enrollment_changed", 409) }
        val coordinator = coordinator()
        coordinator.runEligible()
        assertTrue(recovery.state.value.metadata!!.renewalAttempt!!.statusRequired)
        coordinator.runEligible()
        assertEquals(1, requests)
        now = coordinator.nextEligibleAt()!!
        handler = { request ->
            assertFalse(request.has("enrollment_id"))
            success(request, "auth.status", lifecycle.copy(enrollmentId = "33333333-3333-4333-8333-333333333333"))
        }
        coordinator.runEligible()
        assertEquals(2, requests)
        assertNull(recovery.state.value.metadata)
        assertFalse(recovery.state.value.offboarded)
    }

    @Test fun retrofitRenewalUsesSameEcKeyExactWireAndValidatedEnvelope() = runBlocking {
        val server = MockWebServer()
        server.start()
        try {
            val signing = EphemeralSignedRequestFactory()
            val stored = MemoryBindingStore()
            val recovered = SessionRecovery(stored, { ExistingKey.Available(signing.keyPair.public) },
                server.url("/api/").toString(), { now })
            recovered.recordVerified(signing.publicKeyPem, verified, lifecycle)
            due()
            val requestId = "22222222-2222-4222-8222-222222222222"
            val request = JsonObject().apply { addProperty("request_id", requestId) }
            server.enqueue(MockResponse().setResponseCode(200).setBody(
                success(request, "auth.renewed", renewed()).body()!!.toString()))
            RenewalCoordinator(recovered, ApiClientFactory.createLifecycleApi(server.url("/api/").toString()),
                signing, { now }, { requestId }).runEligible()
            val wire = server.takeRequest()
            assertEquals("/api/auth/renew", wire.path)
            val json = JsonParser.parseString(wire.body.readUtf8()).asJsonObject
            assertEquals(setOf("enrollment_id", "request_id", "next_renewal_at", "public_key", "signature"), json.keySet())
            assertEquals(signing.publicKeyPem, json.get("public_key").asString)
            val verifier = Signature.getInstance("SHA256withECDSA")
            verifier.initVerify(signing.keyPair.public)
            verifier.update(CanonicalPayloadFactory.createRenewal(id, requestId, lifecycle.nextRenewalAt,
                signing.publicKeyPem).toByteArray(Charsets.UTF_8))
            assertTrue(verifier.verify(Base64.getDecoder().decode(json.get("signature").asString)))
            assertEquals(renewed(), recovered.state.value.metadata!!.lifecycle)
            assertEquals(1, server.requestCount)
        } finally { server.shutdown() }
    }
}
