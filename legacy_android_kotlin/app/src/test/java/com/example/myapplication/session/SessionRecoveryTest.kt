package com.example.myapplication.session

import androidx.lifecycle.ViewModelStore
import com.example.myapplication.data.AuthRepository
import com.example.myapplication.data.InitiateAuthResult
import com.example.myapplication.data.VerifyAuthResult
import com.example.myapplication.security.PublicKeyPemEncoder
import com.example.myapplication.security.SignedRequestFactory
import com.example.myapplication.security.SignedRequestPayload
import com.example.myapplication.ui.onboarding.OnboardingViewModel
import java.security.KeyPairGenerator
import java.time.Instant
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestCoroutineScheduler
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class MemoryBindingStore : BindingMetadataStore {
    var values: Map<String, *> = emptyMap<String, String>()
    var writable = true
    override fun read() = values
    override fun write(values: Map<String, String>): Boolean {
        if (writable) this.values = values.toMap()
        return writable
    }
    override fun clear(): Boolean {
        if (writable) values = emptyMap<String, String>()
        return writable
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
class SessionRecoveryTest {
    private val publicKey = KeyPairGenerator.getInstance("EC").apply { initialize(256) }.generateKeyPair().public
    private val pem = PublicKeyPemEncoder.toPem(publicKey)
    private val time = "2030-01-01T00:00:00Z"
    private val store = MemoryBindingStore()
    private var key: ExistingKey = ExistingKey.Available(publicKey)
    private var lookups = 0
    private val scheduler = TestCoroutineScheduler()
    private val viewModels = ViewModelStore()
    private var initiations = 0
    private var verifications = 0
    private val auth = object : AuthRepository {
        override suspend fun initiateAuth(adUrl: String): InitiateAuthResult {
            initiations++
            return InitiateAuthResult.Success("synthetic-challenge", "masked", "2030-01-01T00:15:00Z")
        }
        override suspend fun verifyAuth(challengeId: String, otp: String, publicKey: String, signature: String): VerifyAuthResult {
            verifications++
            return VerifyAuthResult.Success(challengeId, "masked", time)
        }
    }
    private val signer = object : SignedRequestFactory {
        override fun createVerifyRequest(challengeId: String) = SignedRequestPayload(pem, "synthetic")
        override fun createReportRequest(clientPhoneNumber: String, feature: String): SignedRequestPayload =
            error("Restoration must not submit a report")
        override fun createBlacklistCheckRequest(targetHash: String): SignedRequestPayload =
            error("Restoration must not submit a query")
    }

    @Before fun setup() { Dispatchers.setMain(StandardTestDispatcher(scheduler)) }
    @After fun teardown() { viewModels.clear(); Dispatchers.resetMain() }
    private fun recovery(scope: String = "synthetic-backend") = SessionRecovery(store, { lookups++; key }, scope)
    private fun seeded(): SessionRecovery = recovery().also { it.recordVerified(pem, time) }
    private fun vm(recovery: SessionRecovery) = OnboardingViewModel(
        auth, signer, { scheduler.currentTime }, { Instant.parse(time) }, false, recovery,
    ).also { viewModels.put("onboarding", it) }

    @Test fun successfulVerificationRecordsOnlyKeyBoundNonSecretMetadata() {
        val vm = vm(recovery())
        vm.onAdUrlChanged("https://portal.example.test/synthetic")
        vm.submitAdUrl(); scheduler.runCurrent()
        vm.onOtpChanged("000000"); vm.submitOtp(); scheduler.runCurrent()
        assertTrue(vm.uiState.value.isVerified)
        assertFalse(vm.uiState.value.isSessionRestored)
        assertEquals("", vm.uiState.value.otp)
        assertEquals(setOf("version", "backend_scope", "key_fingerprint", "verified_at"), store.values.keys)
        assertEquals(SessionRecovery.keyFingerprint(publicKey), store.values["key_fingerprint"])
        assertEquals(time, store.values["verified_at"])
        assertFalse(store.values.values.contains(pem))
        assertEquals(1, initiations)
        assertEquals(1, verifications)
    }

    @Test fun freshSessionAndViewModelRestoreMatchingKeyWithoutCommandsOrSensitiveState() {
        seeded()
        val vm = vm(recovery())
        scheduler.advanceTimeBy(60000); scheduler.runCurrent()
        assertTrue(vm.uiState.value.isVerified)
        assertTrue(vm.uiState.value.isSessionRestored)
        assertNull(vm.uiState.value.challengeId)
        assertNull(vm.uiState.value.maskedPhoneNumber)
        assertEquals("", vm.uiState.value.adUrl)
        assertEquals("", vm.uiState.value.otp)
        assertEquals(0L, vm.uiState.value.retryAfterSeconds)
        assertEquals(0, initiations)
        assertEquals(0, verifications)
    }

    @Test fun missingKeyClearsHintWithoutCreatingOrReplacingAKey() {
        seeded(); key = ExistingKey.Missing
        val restored = recovery()
        assertNull(restored.state.value.metadata)
        assertTrue(store.values.isEmpty())
        assertEquals(ExistingKey.Missing, key)
        assertNotNull(restored.state.value.message)
    }

    @Test fun changedKeyClearsHint() {
        seeded()
        key = ExistingKey.Available(KeyPairGenerator.getInstance("EC").apply { initialize(256) }.generateKeyPair().public)
        assertNull(recovery().state.value.metadata)
        assertTrue(store.values.isEmpty())
    }

    @Test fun invalidatedKeyClearsHintWithoutReplacingKey() {
        seeded(); key = ExistingKey.Invalid
        assertNull(recovery().state.value.metadata)
        assertTrue(store.values.isEmpty())
        assertEquals(ExistingKey.Invalid, key)
    }

    @Test fun unavailableKeyRetainsMetadataAndExplicitLocalRefreshCanRecover() {
        seeded(); val saved = store.values
        key = ExistingKey.Unavailable
        val restored = recovery()
        val vm = vm(restored)
        scheduler.runCurrent()
        assertFalse(vm.uiState.value.isVerified)
        assertEquals(saved, store.values)
        key = ExistingKey.Available(publicKey)
        restored.refresh(); scheduler.runCurrent()
        assertTrue(vm.uiState.value.isVerified)
        assertTrue(vm.uiState.value.isSessionRestored)
        assertNull(vm.uiState.value.generalError)
        assertEquals(0, initiations + verifications)
    }

    @Test fun incompleteOrMalformedMetadataIsRejectedBeforeKeyAccess() {
        val valid = seeded().let { store.values.toMap() }
        for (invalid in listOf(
            valid - "key_fingerprint", valid - "verified_at", valid - "version", valid - "backend_scope",
            valid + ("version" to "2"), valid + ("version" to 1),
            valid + ("key_fingerprint" to "not-a-hash"),
            valid + ("verified_at" to "invalid"), valid + ("verified_at" to 123),
        )) {
            store.values = invalid; lookups = 0
            assertNull(recovery().state.value.metadata)
            assertEquals(0, lookups)
            assertTrue(store.values.isEmpty())
        }
    }

    @Test fun absentMetadataDoesNotLookUpOrCreateKey() {
        assertNull(recovery().state.value.metadata)
        assertEquals(0, lookups)
    }

    @Test fun differentBackendCannotReuseHint() {
        seeded()
        assertNull(recovery("other-backend").state.value.metadata)
        assertTrue(store.values.isEmpty())
    }

    @Test fun rejectionPropagatesToAlreadyOwnedOnboardingViewModel() {
        val recovery = seeded()
        val vm = vm(recovery); scheduler.runCurrent()
        recovery.authorizationRejected(pem); scheduler.runCurrent()
        assertFalse(vm.uiState.value.isVerified)
        assertTrue(store.values.isEmpty())
        assertTrue(vm.uiState.value.generalError!!.contains("server rejected"))
        recovery.refresh(); scheduler.runCurrent()
        assertFalse(vm.uiState.value.isVerified)
        assertEquals(0, initiations + verifications)
    }

    @Test fun staleDifferentlyKeyedRejectionDoesNotDiscardCurrentHint() {
        val recovery = seeded()
        val other = KeyPairGenerator.getInstance("EC").apply { initialize(256) }.generateKeyPair().public
        recovery.authorizationRejected(PublicKeyPemEncoder.toPem(other))
        assertNotNull(recovery.state.value.metadata)
        assertFalse(store.values.isEmpty())
    }

    @Test fun failedMetadataRemovalNeverReauthorizesOnResumeInSameProcess() {
        val recovery = seeded(); store.writable = false
        recovery.authorizationRejected(pem)
        recovery.refresh()
        assertNull(recovery.state.value.metadata)
        assertTrue(recovery.state.value.message!!.contains("could not be removed"))
    }

    @Test fun persistenceFailureIsVisibleAndDoesNotPretendRestartRecovery() {
        store.writable = false
        val recovery = seeded()
        assertNotNull(recovery.state.value.metadata)
        assertTrue(recovery.state.value.message!!.contains("could not be saved"))
        assertNull(recovery().state.value.metadata)
    }

    @Test fun rejectionClearsLiveBindingAfterFailedPersistenceAndSuccessfulCleanup() {
        store.writable = false
        val recovery = seeded()
        val vm = vm(recovery); scheduler.runCurrent()
        assertTrue(vm.uiState.value.isVerified)
        store.writable = true
        recovery.refresh()
        assertTrue(store.values.isEmpty())
        recovery.authorizationRejected(pem); scheduler.runCurrent()
        assertNull(recovery.state.value.metadata)
        assertFalse(vm.uiState.value.isVerified)
        assertTrue(vm.uiState.value.generalError!!.contains("server rejected"))
        assertEquals(0, initiations + verifications)
    }

    @Test fun oldPersistedIdentityCannotDiscardNewerUnsavedLiveBinding() {
        val recovery = seeded()
        val newerKey = KeyPairGenerator.getInstance("EC").apply { initialize(256) }.generateKeyPair().public
        key = ExistingKey.Available(newerKey)
        store.writable = false
        recovery.recordVerified(PublicKeyPemEncoder.toPem(newerKey), time)
        recovery.authorizationRejected(pem)
        assertEquals(SessionRecovery.keyFingerprint(newerKey), recovery.state.value.metadata?.keyFingerprint)
        assertEquals(SessionRecovery.keyFingerprint(publicKey), store.values["key_fingerprint"])
    }

    @Test fun changedOrUnavailableKeyAtVerificationDoesNotSaveHint() {
        val changed = ExistingKey.Available(KeyPairGenerator.getInstance("EC").apply { initialize(256) }.generateKeyPair().public)
        for (unusable in listOf(ExistingKey.Missing, ExistingKey.Invalid, ExistingKey.Unavailable, changed)) {
            key = unusable
            val recovery = seeded()
            assertNull(recovery.state.value.metadata)
            assertTrue(store.values.isEmpty())
            assertNotNull(recovery.state.value.message)
        }
    }

    @Test fun explicitRestartOpensOnboardingWithoutSmsOrKeyReset() {
        val vm = vm(seeded()); scheduler.runCurrent()
        vm.restartOnboarding(); scheduler.runCurrent()
        assertFalse(vm.uiState.value.isVerified)
        assertNull(vm.uiState.value.challengeId)
        assertTrue(store.values.isEmpty())
        assertEquals(ExistingKey.Available(publicKey), key)
        assertEquals(0, initiations + verifications)
    }

    @Test fun invalidVerificationTimestampIsNotPersisted() {
        val recovery = recovery()
        recovery.recordVerified(pem, "not-a-time")
        assertNull(recovery.state.value.metadata)
        assertTrue(store.values.isEmpty())
        assertNotNull(recovery.state.value.message)
    }
}
