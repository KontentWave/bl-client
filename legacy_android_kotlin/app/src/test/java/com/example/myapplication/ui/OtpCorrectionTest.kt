package com.example.myapplication.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import com.example.myapplication.data.AuthRepository
import com.example.myapplication.data.InitiateAuthResult
import com.example.myapplication.data.VerifyAuthResult
import com.example.myapplication.security.PublicKeyPemEncoder
import com.example.myapplication.security.SignedRequestFactory
import com.example.myapplication.security.SignedRequestPayload
import com.example.myapplication.session.ExistingKey
import com.example.myapplication.session.MemoryBindingStore
import com.example.myapplication.session.SessionRecovery
import com.example.myapplication.ui.onboarding.OnboardingViewModel
import java.security.KeyPairGenerator
import java.time.Instant
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class OtpCorrectionTest {
    private val scheduler = TestCoroutineScheduler()
    private val store = ViewModelStore()
    private val start = Instant.parse("2030-01-01T00:00:00Z")
    private val expiry = "2030-01-01T00:15:00Z"
    private val publicKey = KeyPairGenerator.getInstance("EC").apply { initialize(256) }.generateKeyPair().public
    private val pem = PublicKeyPemEncoder.toPem(publicKey)
    private val bindingStore = MemoryBindingStore()
    private var signedChallenges = mutableListOf<String>()
    private val signer = object : SignedRequestFactory {
        override fun createVerifyRequest(challengeId: String): SignedRequestPayload {
            signedChallenges.add(challengeId)
            return SignedRequestPayload(pem, "synthetic-signature")
        }
        override fun createReportRequest(clientPhoneNumber: String, feature: String): SignedRequestPayload = error("No report")
        override fun createBlacklistCheckRequest(targetHash: String): SignedRequestPayload = error("No query")
    }
    private inner class AuthFake : AuthRepository {
        var initiations = 0
        val submissions = mutableListOf<Pair<String, String>>()
        var initiate: InitiateAuthResult = InitiateAuthResult.Success("original", "masked", expiry)
        var verify: VerifyAuthResult = VerifyAuthResult.Failure(
            "otp_invalid_or_expired", "The provided OTP is invalid or expired.",
            mapOf("otp" to listOf("The provided OTP is invalid or expired.")), retryable = false,
        )
        var pending: CompletableDeferred<VerifyAuthResult>? = null
        override suspend fun initiateAuth(adUrl: String): InitiateAuthResult { initiations++; return initiate }
        override suspend fun verifyAuth(challengeId: String, otp: String, publicKey: String, signature: String): VerifyAuthResult {
            submissions.add(challengeId to otp)
            assertEquals(pem, publicKey)
            return pending?.await() ?: verify
        }
    }

    @Before fun setup() { Dispatchers.setMain(StandardTestDispatcher(scheduler)) }
    @After fun teardown() { store.clear(); Dispatchers.resetMain() }
    private fun vm(fake: AuthFake, recovery: SessionRecovery? = null): OnboardingViewModel =
        OnboardingViewModel(fake, signer, { scheduler.currentTime }, { start.plusMillis(scheduler.currentTime) }, false, recovery)
            .also { store.put("auth", it) }
    private fun initiated(fake: AuthFake, recovery: SessionRecovery? = null) = vm(fake, recovery).also {
        scheduler.runCurrent()
        it.onAdUrlChanged("https://amaterky.sk/synthetic")
        it.submitAdUrl(); scheduler.runCurrent()
        it.onOtpChanged("000000")
    }
    private fun assertRetained(vm: OnboardingViewModel) {
        assertEquals("original", vm.uiState.value.challengeId)
        assertEquals("masked", vm.uiState.value.maskedPhoneNumber)
        assertEquals(expiry, vm.uiState.value.otpExpiresAt)
    }

    @Test fun wrongThenCorrectedOtpUsesSameChallengeAndRecordsKeyBoundRecoveryOnlyOnSuccess() {
        val fake = AuthFake()
        val recovery = SessionRecovery(bindingStore, { ExistingKey.Available(publicKey) }, "synthetic-backend")
        val vm = initiated(fake, recovery)
        vm.submitOtp(); vm.submitOtp(); scheduler.runCurrent()
        assertRetained(vm)
        assertFalse(vm.uiState.value.isChallengeLocked)
        assertFalse(vm.uiState.value.isRetryableError)
        assertNotNull(vm.uiState.value.otpError)
        assertTrue(bindingStore.values.isEmpty())
        assertTrue(vm.uiState.value.challengeRetryGuidance!!.contains("incorrect or expired"))
        val guidance = vm.uiState.value.challengeRetryGuidance
        vm.onOtpChanged("123456"); scheduler.advanceTimeBy(1000); scheduler.runCurrent()
        assertNull(vm.uiState.value.otpError)
        assertEquals(guidance, vm.uiState.value.challengeRetryGuidance)
        assertEquals(listOf("original" to "000000"), fake.submissions)
        fake.verify = VerifyAuthResult.Success("original", "masked", start.toString())
        vm.submitOtp(); vm.submitOtp(); scheduler.runCurrent()
        assertEquals(listOf("original" to "000000", "original" to "123456"), fake.submissions)
        assertEquals(listOf("original", "original"), signedChallenges)
        assertEquals(1, fake.initiations)
        assertTrue(vm.uiState.value.isVerified)
        assertEquals("", vm.uiState.value.otp)
        assertNull(vm.uiState.value.challengeRetryGuidance)
        assertEquals(setOf("version", "backend_scope", "key_fingerprint", "verified_at"), bindingStore.values.keys)
        assertEquals(SessionRecovery.keyFingerprint(publicKey), bindingStore.values["key_fingerprint"])
        vm.onOtpChanged("654321"); vm.submitOtp(); scheduler.runCurrent()
        assertEquals(2, fake.submissions.size)
    }

    @Test fun expiryAfterAmbiguousOtpFailurePreventsCorrectedSubmissionWithoutResend() {
        val fake = AuthFake()
        val vm = initiated(fake)
        vm.submitOtp(); scheduler.runCurrent()
        scheduler.advanceTimeBy(900001); scheduler.runCurrent()
        vm.onOtpChanged("123456"); vm.submitOtp(); scheduler.runCurrent()
        assertTrue(vm.uiState.value.isChallengeLocked)
        assertTrue(vm.uiState.value.generalError!!.contains("recorded challenge expiry"))
        assertRetained(vm)
        assertEquals(1, fake.submissions.size)
        assertEquals(1, fake.initiations)
    }

    @Test fun recordedExpiryBoundaryMatchesStrictlyAfterBackendRule() {
        val fake = AuthFake()
        val vm = initiated(fake)
        scheduler.advanceTimeBy(900000); scheduler.runCurrent()
        vm.submitOtp(); scheduler.runCurrent()
        assertEquals(1, fake.submissions.size)
        scheduler.advanceTimeBy(1); vm.submitOtp(); scheduler.runCurrent()
        assertEquals(1, fake.submissions.size)
        assertTrue(vm.uiState.value.isChallengeLocked)
    }

    @Test fun missingOrMalformedExpiryCannotEnableUnboundedVerification() {
        for (invalid in listOf("", "not-an-instant")) {
            val fake = AuthFake().also { it.initiate = InitiateAuthResult.Success("original", "masked", invalid) }
            val vm = initiated(fake)
            vm.submitOtp(); scheduler.runCurrent()
            assertTrue(vm.uiState.value.isChallengeLocked)
            assertTrue(vm.uiState.value.generalError!!.contains("expiry is unavailable"))
            assertTrue(fake.submissions.isEmpty())
            assertTrue(signedChallenges.isEmpty())
            assertEquals(1, fake.initiations)
        }
    }

    @Test fun authoritativeMissingChallengeAndSignatureRejectionStillBlockCorrection() {
        for (code in listOf("challenge_not_found", "signature_invalid")) {
            val fake = AuthFake().also { it.verify = VerifyAuthResult.Failure(code, "Rejected") }
            val vm = initiated(fake)
            vm.submitOtp(); scheduler.runCurrent()
            assertTrue(vm.uiState.value.isChallengeLocked)
            assertNull(vm.uiState.value.challengeRetryGuidance)
            vm.onOtpChanged("123456"); vm.submitOtp(); scheduler.runCurrent()
            assertEquals(1, fake.submissions.size)
            assertEquals(1, fake.initiations)
            assertRetained(vm)
        }
    }

    @Test fun temporaryUnknownAndMalformedFailuresRetainChallengeForExplicitRetryOnly() {
        for (failure in listOf(
            VerifyAuthResult.Failure("network_unavailable", "Unavailable", retryable = true),
            VerifyAuthResult.Failure("abuse_protection_unavailable", "Unavailable", retryable = true),
            VerifyAuthResult.Failure("http_500", "Unavailable", retryable = true),
            VerifyAuthResult.Failure("response_parse_failed", "Unknown", responseMalformed = true),
            VerifyAuthResult.Failure("signature_invalid", "Malformed", responseMalformed = true),
            VerifyAuthResult.Failure("unexpected_empty_body", "Unknown"),
            VerifyAuthResult.Failure("validation_failed", "Rejected", mapOf("otp" to listOf("Invalid OTP"))),
            VerifyAuthResult.Failure("unknown_future_code", "Unknown"),
        )) {
            val fake = AuthFake().also { it.verify = failure }
            val vm = initiated(fake)
            vm.submitOtp(); scheduler.runCurrent()
            assertRetained(vm)
            assertFalse(vm.uiState.value.isChallengeLocked)
            assertTrue(vm.uiState.value.challengeRetryGuidance!!.contains("status is unknown"))
            vm.onOtpChanged("123456"); scheduler.advanceTimeBy(1000); scheduler.runCurrent()
            assertEquals(1, fake.submissions.size)
            vm.submitOtp(); scheduler.runCurrent()
            assertEquals(2, fake.submissions.size)
            assertEquals(1, fake.initiations)
        }
    }

    @Test fun genericAndMalformed429GateBothCommandsWithoutInventingExhaustion() {
        for (malformed in listOf(false, true)) {
            val fake = AuthFake().also {
                it.verify = VerifyAuthResult.Failure("rate_limited", "Wait", retryable = true, retryAfterSeconds = 4, responseMalformed = malformed)
            }
            val vm = initiated(fake)
            vm.submitOtp(); scheduler.runCurrent()
            assertRetained(vm)
            assertFalse(vm.uiState.value.isChallengeLocked)
            vm.onOtpChanged("123456"); vm.submitOtp(); vm.startNewChallenge(); scheduler.runCurrent()
            assertEquals(1, fake.submissions.size)
            assertEquals(1, fake.initiations)
            scheduler.advanceTimeBy(4000); scheduler.runCurrent()
            assertEquals(0L, vm.uiState.value.retryAfterSeconds)
            assertEquals(1, fake.submissions.size)
            fake.verify = VerifyAuthResult.Failure("otp_invalid_or_expired", "Rejected")
            vm.submitOtp(); scheduler.runCurrent()
            assertEquals(2, fake.submissions.size)
            assertEquals(1, fake.initiations)
        }
    }

    @Test fun cooldownThatOutlastsChallengeNeverEnablesExpiredRetryButExplicitResendCanReplaceIt() {
        val fake = AuthFake().also {
            it.verify = VerifyAuthResult.Failure("rate_limited", "Wait", retryable = true, retryAfterSeconds = 901)
        }
        val vm = initiated(fake)
        vm.submitOtp(); scheduler.runCurrent()
        scheduler.advanceTimeBy(901000); scheduler.runCurrent()
        assertEquals(1, fake.submissions.size)
        vm.submitOtp(); scheduler.runCurrent()
        assertTrue(vm.uiState.value.isChallengeLocked)
        assertEquals(1, fake.submissions.size)
        fake.initiate = InitiateAuthResult.Success("replacement", "masked-new", "2030-01-01T00:30:00Z")
        vm.startNewChallenge(); vm.startNewChallenge(); scheduler.runCurrent()
        assertEquals(2, fake.initiations)
        assertEquals("replacement", vm.uiState.value.challengeId)
        assertFalse(vm.uiState.value.isChallengeLocked)
        assertEquals("", vm.uiState.value.otp)
        assertNull(vm.uiState.value.challengeRetryGuidance)
    }

    @Test fun inFlightEditsResendsAndDuplicateVerificationCannotMutateOrSubmitAnotherCommand() {
        val fake = AuthFake().also { it.pending = CompletableDeferred() }
        val vm = initiated(fake)
        vm.submitOtp(); vm.onOtpChanged("123456"); vm.submitOtp(); vm.startNewChallenge()
        scheduler.runCurrent()
        assertTrue(vm.uiState.value.isVerifying)
        vm.onOtpChanged("654321"); vm.submitOtp(); vm.startNewChallenge(); vm.restartOnboarding()
        scheduler.runCurrent()
        assertEquals("000000", vm.uiState.value.otp)
        assertEquals(listOf("original" to "000000"), fake.submissions)
        assertEquals(1, fake.initiations)
        fake.pending!!.complete(fake.verify); scheduler.runCurrent()
        vm.onOtpChanged("123456"); scheduler.runCurrent()
        assertEquals("123456", vm.uiState.value.otp)
        assertEquals(1, fake.submissions.size)
    }

    @Test fun otpValidationDoesNotSendOrReplayAndUsesAsciiDigitsOnly() {
        val fake = AuthFake()
        val vm = initiated(fake)
        vm.onOtpChanged("١٢٣４５６"); vm.submitOtp(); scheduler.runCurrent()
        assertEquals("", vm.uiState.value.otp)
        assertNotNull(vm.uiState.value.otpError)
        vm.onOtpChanged("12a34-567"); scheduler.runCurrent()
        assertEquals("123456", vm.uiState.value.otp)
        assertTrue(fake.submissions.isEmpty())
        vm.submitOtp(); scheduler.runCurrent()
        assertEquals(listOf("original" to "123456"), fake.submissions)
    }

    @Test fun retainedViewModelKeepsCorrectionAndCooldownButFreshProcessModelLosesThemWithoutReplay() {
        val fake = AuthFake()
        val factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T = vm(fake) as T
        }
        val original = ViewModelProvider(store, factory)[OnboardingViewModel::class.java]
        original.onAdUrlChanged("https://amaterky.sk/synthetic")
        original.submitAdUrl(); scheduler.runCurrent()
        original.onOtpChanged("000000"); original.submitOtp(); scheduler.runCurrent()
        original.onOtpChanged("123456")
        val returned = ViewModelProvider(store, factory)[OnboardingViewModel::class.java]
        assertSame(original, returned)
        assertRetained(returned)
        assertEquals("123456", returned.uiState.value.otp)
        assertNotNull(returned.uiState.value.challengeRetryGuidance)
        fake.verify = VerifyAuthResult.Failure("rate_limited", "Wait", retryAfterSeconds = 4)
        returned.submitOtp(); scheduler.runCurrent()
        assertEquals(4L, returned.uiState.value.retryAfterSeconds)
        store.clear()
        val fresh = vm(fake)
        scheduler.advanceTimeBy(10000); scheduler.runCurrent()
        assertNull(fresh.uiState.value.challengeId)
        assertNull(fresh.uiState.value.maskedPhoneNumber)
        assertNull(fresh.uiState.value.otpExpiresAt)
        assertNull(fresh.uiState.value.challengeRetryGuidance)
        assertEquals("", fresh.uiState.value.otp)
        assertEquals(0L, fresh.uiState.value.retryAfterSeconds)
        assertEquals(1, fake.initiations)
        assertEquals(2, fake.submissions.size)
    }

    @Test fun failedExplicitResendRetainsCorrectionStateWithoutClaimingRemoteValidity() {
        val fake = AuthFake()
        val vm = initiated(fake)
        vm.submitOtp(); scheduler.runCurrent()
        vm.onOtpChanged("123456")
        fake.initiate = InitiateAuthResult.Failure("sms_dispatch_failed", "Unknown", retryable = true)
        vm.startNewChallenge(); scheduler.runCurrent()
        assertRetained(vm)
        assertEquals("123456", vm.uiState.value.otp)
        assertTrue(vm.uiState.value.challengeRetryGuidance!!.contains("status is unknown"))
        assertEquals(2, fake.initiations)
        assertEquals(1, fake.submissions.size)
    }
}
