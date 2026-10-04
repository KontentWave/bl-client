package com.example.myapplication.ui

import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.example.myapplication.data.*
import com.example.myapplication.security.SignedRequestFactory
import com.example.myapplication.security.SignedRequestPayload
import com.example.myapplication.ui.onboarding.OnboardingViewModel
import com.example.myapplication.ui.query.BlacklistQueryViewModel
import com.example.myapplication.ui.reporting.ReportViewModel
import java.time.Instant
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class RetryStateTest {
    private val scheduler = TestCoroutineScheduler()
    private val dispatcher = StandardTestDispatcher(scheduler)
    private val store = ViewModelStore()
    private val start = Instant.parse("2030-01-01T00:00:00Z")
    private val signer = object : SignedRequestFactory {
        override fun createVerifyRequest(challengeId: String) = SignedRequestPayload("fixture-key", "fixture-signature")
        override fun createReportRequest(clientPhoneNumber: String, feature: String) = SignedRequestPayload("fixture-key", "fixture-signature")
        override fun createBlacklistCheckRequest(targetHash: String) = SignedRequestPayload("fixture-key", "fixture-signature")
    }
    private class AuthFake : AuthRepository {
        var initiations = 0
        var verifications = 0
        var initiate: InitiateAuthResult = InitiateAuthResult.Success("original", "masked", "2030-01-01T00:15:00Z")
        var verify: VerifyAuthResult = VerifyAuthResult.Failure("rate_limited", "Wait", retryable = true, retryAfterSeconds = 4)
        override suspend fun initiateAuth(adUrl: String): InitiateAuthResult { initiations++; return initiate }
        override suspend fun verifyAuth(challengeId: String, otp: String, publicKey: String, signature: String): VerifyAuthResult { verifications++; return verify }
    }

    @Before fun setup() { Dispatchers.setMain(dispatcher) }
    @After fun teardown() { store.clear(); Dispatchers.resetMain() }
    private fun auth(fake: AuthFake): OnboardingViewModel = OnboardingViewModel(
        fake, signer, { scheduler.currentTime }, { start.plusMillis(scheduler.currentTime) }, false,
    ).also { store.put("auth", it); it.onAdUrlChanged("https://amaterky.sk/synthetic") }

    @Test fun blockedResendRetainsChallengeAndRequiresExplicitRetry() {
        val fake = AuthFake()
        val vm = auth(fake)
        vm.submitAdUrl(); scheduler.runCurrent()
        val original = vm.uiState.value
        fake.initiate = InitiateAuthResult.Failure("rate_limited", "Wait", retryable = true, retryAfterSeconds = 4)
        vm.startNewChallenge(); scheduler.runCurrent()
        assertEquals(original.challengeId, vm.uiState.value.challengeId)
        assertEquals(original.maskedPhoneNumber, vm.uiState.value.maskedPhoneNumber)
        assertEquals(original.otpExpiresAt, vm.uiState.value.otpExpiresAt)
        vm.onAdUrlChanged("https://www.amaterky.sk/other")
        vm.onOtpChanged("000000")
        vm.startNewChallenge(); vm.submitOtp(); scheduler.runCurrent()
        assertEquals(2, fake.initiations)
        assertEquals(0, fake.verifications)
        scheduler.advanceTimeBy(4000); scheduler.runCurrent()
        assertEquals(0L, vm.uiState.value.retryAfterSeconds)
        assertEquals(2, fake.initiations)
        fake.initiate = InitiateAuthResult.Success("replacement", "masked-new", "2030-01-01T00:20:00Z")
        vm.startNewChallenge(); scheduler.runCurrent()
        assertEquals(3, fake.initiations)
        assertEquals("replacement", vm.uiState.value.challengeId)
    }

    @Test fun verification429DoesNotInventExhaustionOrAutoRetry() {
        val fake = AuthFake()
        val vm = auth(fake)
        vm.submitAdUrl(); scheduler.runCurrent()
        vm.onOtpChanged("000000"); vm.submitOtp(); scheduler.runCurrent()
        assertEquals("original", vm.uiState.value.challengeId)
        assertFalse(vm.uiState.value.isChallengeLocked)
        vm.submitOtp(); scheduler.runCurrent()
        assertEquals(1, fake.verifications)
        scheduler.advanceTimeBy(4000); scheduler.runCurrent()
        assertEquals(1, fake.verifications)
        vm.submitOtp(); scheduler.runCurrent()
        assertEquals(2, fake.verifications)
    }

    @Test fun protectionUnavailablePreservesChallengeForBothAuthActions() {
        val fake = AuthFake()
        val vm = auth(fake)
        vm.submitAdUrl(); scheduler.runCurrent()
        fake.initiate = InitiateAuthResult.Failure("abuse_protection_unavailable", "Unavailable", retryable = true)
        vm.startNewChallenge(); scheduler.runCurrent()
        assertEquals("original", vm.uiState.value.challengeId)
        fake.verify = VerifyAuthResult.Failure("abuse_protection_unavailable", "Unavailable", retryable = true)
        vm.onOtpChanged("000000"); vm.submitOtp(); scheduler.runCurrent()
        assertFalse(vm.uiState.value.isChallengeLocked)
        scheduler.advanceTimeBy(10000); scheduler.runCurrent()
        assertEquals(2, fake.initiations)
        assertEquals(1, fake.verifications)
    }

    @Test fun expiredChallengeIsNotSubmitted() {
        val fake = AuthFake()
        val vm = auth(fake)
        vm.submitAdUrl(); scheduler.runCurrent()
        scheduler.advanceTimeBy(900001); scheduler.runCurrent()
        vm.onOtpChanged("000000"); vm.submitOtp(); scheduler.runCurrent()
        assertEquals(0, fake.verifications)
        assertTrue(vm.uiState.value.isChallengeLocked)
    }

    @Test fun duplicateAuthCommandsAreGuardedBeforeCoroutineStarts() {
        val fake = AuthFake()
        val vm = auth(fake)
        vm.submitAdUrl(); vm.submitAdUrl(); vm.startNewChallenge(); scheduler.runCurrent()
        assertEquals(1, fake.initiations)
        vm.onOtpChanged("000000"); vm.submitOtp(); vm.submitOtp(); scheduler.runCurrent()
        assertEquals(1, fake.verifications)
    }

    @Test fun ambiguousInitiationNeverSchedulesResend() {
        for (code in listOf("network_unavailable", "response_parse_failed", "sms_dispatch_failed")) {
            val fake = AuthFake().also { it.initiate = InitiateAuthResult.Failure(code, "Unknown", retryable = true) }
            val vm = auth(fake)
            vm.submitAdUrl(); scheduler.runCurrent()
            vm.onAdUrlChanged("https://amaterky.sk/edited")
            scheduler.advanceTimeBy(60000); scheduler.runCurrent()
            assertEquals(1, fake.initiations)
        }
    }

    @Test fun reportResetAndInputChangesDoNotBypassCooldown() {
        var calls = 0
        val fake = object : ReportRepository {
            override suspend fun submitReport(clientPhoneNumber: String, feature: ReportingFeature): ReportSubmissionResult {
                calls++; return ReportSubmissionResult.Failure("rate_limited", "Wait", retryable = true, retryAfterSeconds = 3)
            }
        }
        val vm = ReportViewModel(fake) { scheduler.currentTime }.also { store.put("report", it) }
        vm.onClientPhoneNumberChanged("0900 000 001"); vm.onFeatureSelected(ReportingFeature.NO_SHOW)
        vm.submitReport(); vm.submitReport(); scheduler.runCurrent()
        vm.reset()
        assertEquals(3L, vm.uiState.value.retryAfterSeconds)
        vm.onClientPhoneNumberChanged("+421900000002"); vm.onFeatureSelected(ReportingFeature.NO_SHOW)
        vm.submitReport(); scheduler.runCurrent(); assertEquals(1, calls)
        scheduler.advanceTimeBy(3000); scheduler.runCurrent(); assertEquals(1, calls)
        vm.submitReport(); scheduler.runCurrent(); assertEquals(2, calls)
    }

    @Test fun invalidReportInputDoesNotCallRepositoryOrReplayAfterCorrection() {
        var calls = 0
        val fake = object : ReportRepository {
            override suspend fun submitReport(clientPhoneNumber: String, feature: ReportingFeature): ReportSubmissionResult {
                calls++
                assertEquals("+421900000001", clientPhoneNumber)
                return ReportSubmissionResult.Failure("synthetic", "Synthetic response")
            }
        }
        val vm = ReportViewModel(fake).also { store.put("report", it) }
        vm.onFeatureSelected(ReportingFeature.NO_SHOW)
        for (vector in com.example.myapplication.phone.PhoneNumberVectors.load().filter { it.normalized == null }) {
            vm.onClientPhoneNumberChanged(vector.raw)
            vm.submitReport(); scheduler.runCurrent()
            assertNotNull(vm.uiState.value.clientPhoneNumberError)
            assertFalse(vm.uiState.value.isSubmitting)
        }
        vm.onClientPhoneNumberChanged("0900 000 001")
        scheduler.advanceTimeBy(60000); scheduler.runCurrent()
        assertEquals(0, calls)
        vm.submitReport(); vm.submitReport(); scheduler.runCurrent()
        assertEquals(1, calls)
        assertEquals("+421900000001", vm.uiState.value.clientPhoneNumber)
    }

    @Test fun queryResetAndInputChangesDoNotBypassCooldown() {
        var calls = 0
        val fake = object : BlacklistQueryRepository {
            override suspend fun checkTargetHash(targetHash: String): BlacklistQueryResult {
                calls++; return BlacklistQueryResult.Failure("rate_limited", "Wait", retryable = true, retryAfterSeconds = 3)
            }
        }
        val vm = BlacklistQueryViewModel(fake) { scheduler.currentTime }.also { store.put("query", it) }
        vm.onTargetHashChanged("a".repeat(64)); vm.submitQuery(); vm.submitQuery(); scheduler.runCurrent()
        vm.reset(); assertEquals(3L, vm.uiState.value.retryAfterSeconds)
        vm.onTargetHashChanged("b".repeat(64)); vm.submitQuery(); scheduler.runCurrent(); assertEquals(1, calls)
        scheduler.advanceTimeBy(3000); scheduler.runCurrent(); assertEquals(1, calls)
        vm.submitQuery(); scheduler.runCurrent(); assertEquals(2, calls)
    }

    @Test fun extendingCooldownNeverShortensKnownDeadline() {
        var remaining = 0L
        val cooldown = RetryCooldown(kotlinx.coroutines.CoroutineScope(dispatcher), { scheduler.currentTime }) { remaining = it }
        cooldown.extend(10); scheduler.runCurrent()
        scheduler.advanceTimeBy(1000); scheduler.runCurrent()
        cooldown.extend(1)
        assertEquals(9L, remaining)
        scheduler.advanceTimeBy(9000); scheduler.runCurrent()
        assertEquals(0L, remaining)
    }

    @Test fun newUiOwnerUsesSameRetainedViewModelAndDeadline() {
        val fake = AuthFake().also {
            it.initiate = InitiateAuthResult.Failure("rate_limited", "Wait", retryable = true, retryAfterSeconds = 10)
        }
        val factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T =
                OnboardingViewModel(fake, signer, { scheduler.currentTime }, { start }, false) as T
        }
        val first = ViewModelProvider(store, factory)[OnboardingViewModel::class.java]
        first.onAdUrlChanged("https://amaterky.sk/synthetic")
        first.submitAdUrl(); scheduler.runCurrent()
        scheduler.advanceTimeBy(1000); scheduler.runCurrent()
        // Activity configuration changes retain this store; navigation does not replace it.
        val returned = ViewModelProvider(store, factory)[OnboardingViewModel::class.java]
        assertSame(first, returned)
        assertEquals(9L, returned.uiState.value.retryAfterSeconds)
        returned.submitAdUrl(); scheduler.runCurrent()
        assertEquals(1, fake.initiations)
    }

    @Test fun blockedResendDoesNotUnlockAnExpiredChallenge() {
        val fake = AuthFake()
        val vm = auth(fake)
        vm.submitAdUrl(); scheduler.runCurrent()
        scheduler.advanceTimeBy(900001)
        vm.onOtpChanged("000000"); vm.submitOtp(); scheduler.runCurrent()
        assertTrue(vm.uiState.value.isChallengeLocked)
        fake.initiate = InitiateAuthResult.Failure("rate_limited", "Wait", retryable = true, retryAfterSeconds = 3)
        vm.startNewChallenge(); scheduler.runCurrent()
        assertTrue(vm.uiState.value.isChallengeLocked)
        assertEquals("original", vm.uiState.value.challengeId)
    }

    @Test fun urlsAreBoundedWithoutRewritingSupportedHosts() {
        val vm = auth(AuthFake())
        for (host in listOf("amaterky.sk", "www.amaterky.sk", "eurogirlsescort.com", "www.eurogirlsescort.com")) {
            val url = "https://$host/synthetic"
            vm.onAdUrlChanged(url); assertEquals(url, vm.uiState.value.adUrl)
        }
        val previous = vm.uiState.value.adUrl
        vm.onAdUrlChanged("a".repeat(2049))
        assertEquals(previous, vm.uiState.value.adUrl)
        assertNotNull(vm.uiState.value.adUrlError)
    }

    @Test fun retainedChallengeResendFieldErrorRemainsVisible() {
        val fake = AuthFake()
        val vm = auth(fake)
        vm.submitAdUrl(); scheduler.runCurrent()
        fake.initiate = InitiateAuthResult.Failure(
            "invalid_ad_url", "Invalid ad", mapOf("ad_url" to listOf("Ad is unavailable.")),
        )
        vm.startNewChallenge(); scheduler.runCurrent()
        assertEquals("original", vm.uiState.value.challengeId)
        assertEquals("Ad is unavailable.", vm.uiState.value.generalError)
    }

    @Test fun onlyAuthoritativeVerificationFailuresLockChallenge() {
        val fake = AuthFake()
        val vm = auth(fake)
        vm.submitAdUrl(); scheduler.runCurrent()
        vm.onOtpChanged("000000")
        for (code in listOf("response_parse_failed", "unexpected_error", "validation_failed")) {
            fake.verify = VerifyAuthResult.Failure(code, "Uncertain", responseMalformed = code == "response_parse_failed")
            vm.submitOtp(); scheduler.runCurrent()
            assertFalse("code=$code", vm.uiState.value.isChallengeLocked)
            assertEquals("original", vm.uiState.value.challengeId)
        }
        fake.verify = VerifyAuthResult.Failure("challenge_not_found", "Closed")
        vm.submitOtp(); scheduler.runCurrent()
        assertTrue(vm.uiState.value.isChallengeLocked)
    }

    @Test fun freshViewModelsNeverRestoreOrReplaySensitiveCommands() {
        // Models process-state loss, not an actual OS process kill. No saved-state persistence exists.
        for (phase in listOf("challenge", "throttled", "ambiguous", "verified")) {
            val fake = AuthFake()
            val vm = auth(fake)
            vm.submitAdUrl(); scheduler.runCurrent()
            vm.onOtpChanged("000000")
            when (phase) {
                "throttled" -> { vm.submitOtp(); scheduler.runCurrent() }
                "ambiguous" -> {
                    fake.initiate = InitiateAuthResult.Failure("sms_dispatch_failed", "Unknown", retryable = true)
                    vm.startNewChallenge(); scheduler.runCurrent()
                }
                "verified" -> {
                    fake.verify = VerifyAuthResult.Success("original", "masked", "2030-01-01T00:00:00Z")
                    vm.submitOtp(); scheduler.runCurrent()
                    assertTrue(vm.uiState.value.isVerified)
                }
            }
            val initiations = fake.initiations
            val verifications = fake.verifications
            store.clear()
            val restarted = OnboardingViewModel(fake, signer, { scheduler.currentTime }, { start }, false)
                .also { store.put("auth", it) }
            scheduler.advanceTimeBy(60000); scheduler.runCurrent()
            assertNull(restarted.uiState.value.challengeId)
            assertEquals("", restarted.uiState.value.otp)
            assertFalse(restarted.uiState.value.isVerified)
            assertEquals(0L, restarted.uiState.value.retryAfterSeconds)
            assertEquals(initiations, fake.initiations)
            assertEquals(verifications, fake.verifications)
            restarted.submitOtp(); restarted.submitAdUrl(); scheduler.runCurrent()
            assertEquals(initiations, fake.initiations)
            assertEquals(verifications, fake.verifications)
        }
    }
}
