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
import com.example.myapplication.ui.onboarding.OnboardingMode
import com.example.myapplication.ui.onboarding.OnboardingViewModel
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
class OwnerOnboardingTest {
    private val scheduler = TestCoroutineScheduler()
    private val models = ViewModelStore()
    private val phone = "+12345678"
    private val instant = Instant.parse("2030-01-01T00:00:00Z")
    // Public-only existing fixture: no key generation, signing or key-store operations.
    private val pem = """
        -----BEGIN PUBLIC KEY-----
        MFkwEwYHKoZIzj0CAQYIKoZIzj0DAQcDQgAETsqqw6aY9+urntILLtAYO4b23nM1pruE2aiqfMk9pujJTHxGoraHWT84vfZEpg8dbV/AUv4BcqebBN2bXE6oqw==
        -----END PUBLIC KEY-----
    """.trimIndent()
    private val publicKey = PublicKeyPemEncoder.fromPem(pem)
    private val disk = MemoryBindingStore()
    private val signedChallenges = mutableListOf<String>()
    private val signer = object : SignedRequestFactory {
        override fun createVerifyRequest(challengeId: String): SignedRequestPayload {
            signedChallenges += challengeId
            return SignedRequestPayload(pem, "synthetic-signature")
        }
        override fun createReportRequest(clientPhoneNumber: String, feature: String): SignedRequestPayload = error("No report")
        override fun createBlacklistCheckRequest(targetHash: String): SignedRequestPayload = error("No query")
    }
    private class Fake : AuthRepository {
        val owners = mutableListOf<String>()
        val ads = mutableListOf<String>()
        val verifications = mutableListOf<List<String>>()
        var result: InitiateAuthResult = InitiateAuthResult.Success("original", "masked", "2030-01-01T00:15:00Z")
        var verify: VerifyAuthResult = VerifyAuthResult.Success("original", "masked", "2030-01-01T00:00:00Z")
        var pending: CompletableDeferred<InitiateAuthResult>? = null
        override suspend fun initiateOwnerAuth(phoneNumber: String): InitiateAuthResult {
            owners += phoneNumber
            return pending?.await() ?: result
        }
        override suspend fun initiateAuth(adUrl: String): InitiateAuthResult { ads += adUrl; return result }
        override suspend fun verifyAuth(challengeId: String, otp: String, publicKey: String, signature: String): VerifyAuthResult {
            verifications += listOf(challengeId, otp, publicKey, signature)
            return verify
        }
    }

    @Before fun setup() { Dispatchers.setMain(StandardTestDispatcher(scheduler)) }
    @After fun teardown() { models.clear(); Dispatchers.resetMain() }
    private fun recovery() = SessionRecovery(disk, { ExistingKey.Available(publicKey) }, "synthetic-owner-backend")
    private fun vm(fake: Fake, recovery: SessionRecovery? = null) = OnboardingViewModel(
        fake, signer, { scheduler.currentTime }, { instant.plusMillis(scheduler.currentTime) }, false, recovery,
    ).also { models.put("owner", it); scheduler.runCurrent() }
    private fun owner(fake: Fake, recovery: SessionRecovery? = null) = vm(fake, recovery).also {
        it.onModeChanged(OnboardingMode.OWNER_PHONE)
        it.onOwnerPhoneChanged(phone)
    }
    private fun initiate(vm: OnboardingViewModel) { vm.submitInitiation(); scheduler.runCurrent() }

    @Test fun owner201OnlyCreatesPendingOtpAndVerificationUsesExistingSignerAndRecovery() {
        val fake = Fake()
        val vm = owner(fake, recovery())
        initiate(vm)
        assertTrue(vm.uiState.value.hasInitiatedChallenge)
        assertFalse(vm.uiState.value.isVerified)
        assertEquals("original", vm.uiState.value.challengeId)
        assertEquals(listOf(phone), fake.owners)
        assertTrue(fake.ads.isEmpty())
        assertTrue(disk.values.isEmpty())
        assertTrue(signedChallenges.isEmpty())
        vm.onOtpChanged("000000"); vm.submitOtp(); scheduler.runCurrent()
        assertEquals(listOf(listOf("original", "000000", pem, "synthetic-signature")), fake.verifications)
        assertEquals(listOf("original"), signedChallenges)
        assertTrue(vm.uiState.value.isVerified)
        assertEquals(setOf("version", "backend_scope", "key_fingerprint", "verified_at"), disk.values.keys)
        assertEquals(SessionRecovery.keyFingerprint(publicKey), disk.values["key_fingerprint"])
        assertFalse(disk.values.values.contains(phone))
        val saved = disk.values
        models.clear()
        val restored = vm(fake, recovery())
        scheduler.advanceTimeBy(60000); scheduler.runCurrent()
        assertTrue(restored.uiState.value.isVerified)
        assertTrue(restored.uiState.value.isSessionRestored)
        assertEquals(saved, disk.values)
        assertEquals("", restored.uiState.value.ownerPhoneNumber)
        assertNull(restored.uiState.value.challengeId)
        assertEquals(1, fake.owners.size)
        assertEquals(1, fake.verifications.size)
    }

    @Test fun normalAdModeRemainsDefaultAndSwitchingInputsNeverInitiates() {
        val fake = Fake()
        val vm = vm(fake)
        assertEquals(OnboardingMode.AD_URL, vm.uiState.value.mode)
        vm.onAdUrlChanged("https://portal.example.test/ad")
        vm.onModeChanged(OnboardingMode.OWNER_PHONE); vm.onOwnerPhoneChanged(phone)
        vm.submitAdUrl() // ad-only compatibility entry point cannot silently change owner mode
        scheduler.runCurrent()
        assertTrue(fake.ads.isEmpty()); assertTrue(fake.owners.isEmpty())
        vm.onModeChanged(OnboardingMode.AD_URL)
        initiate(vm)
        assertEquals(listOf("https://portal.example.test/ad"), fake.ads)
        assertTrue(fake.owners.isEmpty())
    }

    @Test fun invalidPhonesNeverReachRepositoryAndOverlengthDoesNotSubmitRetainedValidValue() {
        val fake = Fake()
        val vm = owner(fake)
        for (invalid in listOf("", "12345678", "+01234567", "+1234567", "+1234 5678", " $phone", "$phone ", "+１２３４５６７８", "+1234567890123456")) {
            vm.onOwnerPhoneChanged(invalid); initiate(vm)
            assertNotNull(vm.uiState.value.ownerPhoneError)
            assertFalse(vm.uiState.value.isSubmitting)
        }
        assertTrue(fake.owners.isEmpty())
        vm.onOwnerPhoneChanged(phone)
        vm.onOwnerPhoneChanged("+1234567890123456")
        assertEquals(phone, vm.uiState.value.ownerPhoneNumber)
        initiate(vm)
        vm.onModeChanged(OnboardingMode.AD_URL); vm.onModeChanged(OnboardingMode.OWNER_PHONE)
        initiate(vm)
        initiate(vm)
        assertTrue(fake.owners.isEmpty())
        vm.onOwnerPhoneChanged("+123456789012345")
        initiate(vm)
        assertEquals(listOf("+123456789012345"), fake.owners)
    }

    @Test fun ownerUnavailableAndAmbiguousFailuresNeverRetryFallbackOrEnroll() {
        for (failure in listOf(
            InitiateAuthResult.Failure("owner_enrollment_unavailable", "Unavailable"),
            InitiateAuthResult.Failure("network_unavailable", "Acceptance unknown", retryable = true),
            InitiateAuthResult.Failure("response_parse_failed", "Unknown", responseMalformed = true),
            InitiateAuthResult.Failure("sms_dispatch_failed", "Unknown", retryable = true),
            InitiateAuthResult.Failure("abuse_protection_unavailable", "Unavailable", retryable = true),
        )) {
            val fake = Fake().also { it.result = failure }
            val vm = owner(fake)
            initiate(vm)
            scheduler.advanceTimeBy(60000); scheduler.runCurrent()
            assertFalse(vm.uiState.value.isVerified)
            assertFalse(vm.uiState.value.hasInitiatedChallenge)
            assertEquals(listOf(phone), fake.owners)
            assertTrue(fake.ads.isEmpty()); assertTrue(fake.verifications.isEmpty())
            if (failure.code == "owner_enrollment_unavailable") assertFalse(vm.uiState.value.isRetryableError)
        }
    }

    @Test fun serverValidationUsesPhoneFieldWithoutTouchingAdInput() {
        val fake = Fake().also { it.result = InitiateAuthResult.Failure(
            "validation_failed", "Invalid", mapOf("phone_number" to listOf("Synthetic invalid phone")),
        ) }
        val vm = owner(fake)
        initiate(vm)
        assertEquals("Synthetic invalid phone", vm.uiState.value.ownerPhoneError)
        assertNull(vm.uiState.value.adUrlError)
        assertFalse(vm.uiState.value.isVerified)
    }

    @Test fun explicitOwnerResendRespectsCooldownAndRetainsOldChallengeOnUnknownAcceptance() {
        val fake = Fake()
        val vm = owner(fake)
        initiate(vm)
        vm.onModeChanged(OnboardingMode.AD_URL)
        vm.onOwnerPhoneChanged("+123456789")
        assertEquals(OnboardingMode.OWNER_PHONE, vm.uiState.value.mode)
        assertEquals(phone, vm.uiState.value.ownerPhoneNumber)
        fake.result = InitiateAuthResult.Failure("rate_limited", "Wait", retryable = true, retryAfterSeconds = 4)
        vm.startNewChallenge(); scheduler.runCurrent()
        assertEquals("original", vm.uiState.value.challengeId)
        vm.startNewChallenge(); scheduler.advanceTimeBy(4000); scheduler.runCurrent()
        assertEquals(2, fake.owners.size)
        assertEquals(0L, vm.uiState.value.retryAfterSeconds)
        fake.result = InitiateAuthResult.Failure("sms_dispatch_failed", "Unknown", retryable = true)
        vm.startNewChallenge(); scheduler.runCurrent()
        assertEquals("original", vm.uiState.value.challengeId)
        assertTrue(vm.uiState.value.challengeRetryGuidance!!.contains("unknown"))
        fake.result = InitiateAuthResult.Success("replacement", "masked", "2030-01-01T00:20:00Z")
        vm.startNewChallenge(); vm.startNewChallenge(); scheduler.runCurrent()
        assertEquals("replacement", vm.uiState.value.challengeId)
        assertEquals(listOf(phone, phone, phone, phone), fake.owners)
        assertTrue(fake.ads.isEmpty())
    }

    @Test fun initialRateLimitAppliesAcrossModeChangesAndNeverAutomaticallySends() {
        val fake = Fake().also { it.result = InitiateAuthResult.Failure("rate_limited", "Wait", retryAfterSeconds = 4) }
        val vm = owner(fake)
        initiate(vm)
        vm.onModeChanged(OnboardingMode.AD_URL); vm.onAdUrlChanged("https://portal.example.test/ad")
        vm.submitInitiation(); scheduler.runCurrent()
        assertTrue(fake.ads.isEmpty())
        vm.onModeChanged(OnboardingMode.OWNER_PHONE)
        scheduler.advanceTimeBy(4000); scheduler.runCurrent()
        assertEquals(1, fake.owners.size)
        vm.submitInitiation(); scheduler.runCurrent()
        assertEquals(2, fake.owners.size)
    }

    @Test fun inFlightCommandsCannotChangeModePhoneOrReplay() {
        val fake = Fake().also { it.pending = CompletableDeferred() }
        val vm = owner(fake)
        vm.submitInitiation(); vm.submitInitiation(); vm.startNewChallenge()
        vm.onModeChanged(OnboardingMode.AD_URL); vm.onOwnerPhoneChanged("+123456789")
        scheduler.runCurrent()
        assertTrue(vm.uiState.value.isSubmitting)
        assertEquals(OnboardingMode.OWNER_PHONE, vm.uiState.value.mode)
        assertEquals(phone, vm.uiState.value.ownerPhoneNumber)
        assertEquals(1, fake.owners.size)
        fake.pending!!.complete(fake.result); scheduler.runCurrent()
        assertFalse(vm.uiState.value.isSubmitting)
        assertTrue(fake.ads.isEmpty())
    }

    @Test fun pendingOwnerStateSurvivesRetainedModelButFreshModelDoesNotReplayOrPersistPhone() {
        val fake = Fake()
        val factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T = OnboardingViewModel(
                fake, signer, { scheduler.currentTime }, { instant }, false,
            ) as T
        }
        val original = ViewModelProvider(models, factory)[OnboardingViewModel::class.java]
        original.onModeChanged(OnboardingMode.OWNER_PHONE); original.onOwnerPhoneChanged(phone)
        initiate(original)
        val retained = ViewModelProvider(models, factory)[OnboardingViewModel::class.java]
        assertSame(original, retained)
        assertEquals(OnboardingMode.OWNER_PHONE, retained.uiState.value.mode)
        assertEquals("original", retained.uiState.value.challengeId)
        models.clear()
        val fresh = ViewModelProvider(models, factory)[OnboardingViewModel::class.java]
        scheduler.advanceTimeBy(60000); scheduler.runCurrent()
        assertNull(fresh.uiState.value.challengeId)
        assertEquals("", fresh.uiState.value.ownerPhoneNumber)
        assertFalse(fresh.uiState.value.isVerified)
        assertTrue(disk.values.isEmpty())
        assertEquals(1, fake.owners.size)
        assertTrue(fake.ads.isEmpty())
    }

    @Test fun ownerWrongOtpUsesSameChallengeAndDoesNotSaveBindingUntilVerified() {
        val fake = Fake().also { it.verify = VerifyAuthResult.Failure("otp_invalid_or_expired", "Invalid or expired") }
        val vm = owner(fake, recovery())
        initiate(vm)
        vm.onOtpChanged("000000"); vm.submitOtp(); scheduler.runCurrent()
        assertFalse(vm.uiState.value.isVerified)
        assertTrue(disk.values.isEmpty())
        assertEquals("original", vm.uiState.value.challengeId)
        fake.verify = VerifyAuthResult.Success("original", "masked", instant.toString())
        vm.onOtpChanged("123456"); vm.submitOtp(); scheduler.runCurrent()
        assertTrue(vm.uiState.value.isVerified)
        assertEquals(listOf("original", "original"), signedChallenges)
        assertEquals(1, fake.owners.size)
    }

    @Test fun failedOwnerResendFieldErrorRemainsVisibleButDoesNotPermanentlyLockResend() {
        val fake = Fake()
        val vm = owner(fake)
        initiate(vm)
        fake.result = InitiateAuthResult.Failure("validation_failed", "Invalid", mapOf("phone_number" to listOf("Synthetic rejection")))
        vm.startNewChallenge(); scheduler.runCurrent()
        assertEquals("Synthetic rejection", vm.uiState.value.generalError)
        assertEquals("original", vm.uiState.value.challengeId)
        fake.result = InitiateAuthResult.Success("replacement", "masked", "2030-01-01T00:20:00Z")
        vm.startNewChallenge(); scheduler.runCurrent()
        assertEquals("replacement", vm.uiState.value.challengeId)
        assertEquals(listOf(phone, phone, phone), fake.owners)
    }
}
