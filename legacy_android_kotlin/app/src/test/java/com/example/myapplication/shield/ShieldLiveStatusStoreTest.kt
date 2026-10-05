package com.example.myapplication.shield

import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import javax.xml.parsers.DocumentBuilderFactory
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class ShieldLiveStatusStoreTest {
    @Test
    fun everyStageDiscardsSensitiveFieldsAndArbitraryMessages() {
        val legacy = FakeLegacy()
        val store = ShieldLiveStatusStore(ShieldLiveStatusMemory(legacy))
        for (stage in ShieldLiveStage.entries) {
            store.record(detail(stage))
            val read = store.read()
            assertEquals(stage, read.stage)
            assertRedacted(read)
            assertEquals(ShieldOverlayState.Shown, read.overlayState)
            assertEquals(123L, read.updatedAtEpochMillis)
        }
        assertTrue(legacy.disk.isEmpty())
        assertEquals(1, legacy.attempts)
    }

    @Test
    fun cleanupIsIdempotentAndOnlyTouchesDedicatedDiagnosticStorage() {
        val unrelated = mutableMapOf(
            "device_binding_recovery" to "synthetic recovery",
            "device_key" to "untouched",
            "server_binding" to "untouched",
            "challenge" to "untouched",
            "cooldown" to "untouched",
        )
        val before = unrelated.toMap()
        val legacy = FakeLegacy()
        val memory = ShieldLiveStatusMemory(legacy)
        repeat(10) {
            val store = ShieldLiveStatusStore(memory)
            store.record(detail())
            store.read()
        }
        assertEquals(1, legacy.attempts)
        assertTrue(legacy.disk.isEmpty())
        assertEquals(before, unrelated)
    }

    @Test
    fun failedDurableCleanupNeverReadsLegacyOrClaimsSuccess() {
        val legacy = FakeLegacy().apply { succeeds = false }
        var failures = 0
        val store = ShieldLiveStatusStore(ShieldLiveStatusMemory(legacy) { failures++ })
        val fresh = store.read()
        assertEquals(ShieldLiveStage.Idle, fresh.stage)
        assertTrue(fresh.legacyCleanupFailed)
        assertRedacted(fresh)
        store.record(detail(ShieldLiveStage.QueryFailed))
        val failed = store.read()
        assertEquals(ShieldLiveStage.QueryFailed, failed.stage)
        assertTrue(failed.legacyCleanupFailed)
        assertTrue(legacy.disk.isNotEmpty())
        assertEquals(legacy.attempts, failures)
    }

    @Test
    fun retryRequiresDurableSuccessEvenWhenFailedCommitAlreadyClearedMemoryCache() {
        val legacy = FakeLegacy().apply { succeeds = false }
        val memory = ShieldLiveStatusMemory(legacy)
        val store = ShieldLiveStatusStore(memory)
        assertTrue(legacy.cache.isEmpty())
        assertTrue(legacy.disk.isNotEmpty())
        assertTrue(store.read().legacyCleanupFailed)
        legacy.succeeds = true
        assertFalse(store.read().legacyCleanupFailed)
        assertTrue(legacy.disk.isEmpty())
        val attempts = legacy.attempts
        ShieldLiveStatusStore(memory).record(detail())
        assertEquals(attempts, legacy.attempts)
    }

    @Test
    fun recordAndRepeatedInitializationRetryCleanupWithoutRestoringDetails() {
        val legacy = FakeLegacy().apply { succeeds = false }
        val memory = ShieldLiveStatusMemory(legacy)
        val receiver = ShieldLiveStatusStore(memory)
        receiver.record(detail())
        legacy.succeeds = true
        val activity = ShieldLiveStatusStore(memory)
        assertFalse(activity.read().legacyCleanupFailed)
        assertEquals(ShieldLiveStage.MatchFound, activity.read().stage)
        assertRedacted(activity.read())
        assertTrue(legacy.disk.isEmpty())
    }

    @Test
    fun receiverBeforeActivitySharesProcessOwnerWithoutReinitializingStatus() {
        val owner = ShieldLiveStatusOwner()
        val legacy = FakeLegacy()
        val receiver = ShieldLiveStatusStore(owner.get { ShieldLiveStatusMemory(legacy) })
        receiver.record(detail())
        val activity = ShieldLiveStatusStore(owner.get { error("Must share process memory") })
        assertEquals(receiver.read(), activity.read())
        assertEquals(ShieldLiveStage.MatchFound, activity.read().stage)
        assertRedacted(activity.read())
    }

    @Test
    fun activityBeforeReceiverSharesSameOwner() {
        val owner = ShieldLiveStatusOwner()
        val activity = ShieldLiveStatusStore(owner.get { ShieldLiveStatusMemory(FakeLegacy()) })
        val receiver = ShieldLiveStatusStore(owner.get { error("Must share process memory") })
        receiver.record(detail(ShieldLiveStage.MissingIncomingNumber))
        assertEquals(ShieldLiveStage.MissingIncomingNumber, activity.read().stage)
    }

    @Test
    fun freshProcessDoesNotRestoreEvenWhenLegacyCleanupFails() {
        val legacy = FakeLegacy().apply { succeeds = false }
        val previous = ShieldLiveStatusStore(ShieldLiveStatusOwner().get { ShieldLiveStatusMemory(legacy) })
        previous.record(detail())
        val fresh = ShieldLiveStatusStore(ShieldLiveStatusOwner().get { ShieldLiveStatusMemory(legacy) })
        assertEquals(ShieldLiveStage.Idle, fresh.read().stage)
        assertNull(fresh.read().updatedAtEpochMillis)
        assertTrue(fresh.read().legacyCleanupFailed)
        assertRedacted(fresh.read())
    }

    @Test
    fun concurrentInitializationCleanupAndUpdatesCannotPersistStatus() {
        val owner = ShieldLiveStatusOwner()
        val legacy = FakeLegacy()
        val started = CountDownLatch(1)
        val proceed = CountDownLatch(1)
        val workers = Executors.newFixedThreadPool(3)
        try {
            val first = workers.submit {
                ShieldLiveStatusStore(owner.get {
                    ShieldLiveStatusMemory(legacyDiagnostics = {
                        started.countDown()
                        check(proceed.await(5, TimeUnit.SECONDS))
                        legacy.clear()
                    })
                }).record(detail())
            }
            assertTrue(started.await(5, TimeUnit.SECONDS))
            val second = workers.submit {
                ShieldLiveStatusStore(owner.get { error("Already owned") }).record(detail(ShieldLiveStage.QueryFailed))
            }
            val third = workers.submit {
                ShieldLiveStatusStore(owner.get { error("Already owned") }).read()
            }
            proceed.countDown()
            first.get(5, TimeUnit.SECONDS)
            second.get(5, TimeUnit.SECONDS)
            third.get(5, TimeUnit.SECONDS)
            assertTrue(legacy.disk.isEmpty())
            assertEquals(1, legacy.attempts)
            assertRedacted(ShieldLiveStatusStore(owner.get { error("Already owned") }).read())
        } finally {
            proceed.countDown()
            workers.shutdownNow()
        }
    }

    @Test
    fun deletionAdapterOnlyTargetsDedicatedLegacyPreferenceName() {
        val calls = mutableListOf<String>()
        val adapter = preferenceAdapter(calls) { true }
        assertTrue(adapter.clear())
        assertTrue(adapter.clear())
        assertEquals(listOf("shield_live_status", "shield_live_status"), calls)
    }

    @Test
    fun deletionAdapterPropagatesFailedDeletionResult() {
        val calls = mutableListOf<String>()
        assertFalse(preferenceAdapter(calls) { false }.clear())
        assertEquals(listOf("shield_live_status"), calls)
    }

    @Test
    fun deletionAdapterSurfacesSecurityDenialWithoutExceptionText() {
        assertFalse(preferenceAdapter(mutableListOf()) { throw SecurityException("synthetic caller detail") }.clear())
    }

    @Test
    fun ringingWarningKeepsDetailsButStatusAndEndedCallDoNot() = runTest {
        val store = ShieldLiveStatusStore(ShieldLiveStatusMemory(FakeLegacy()))
        val warnings = mutableListOf<Triple<String, List<String>, String?>>()
        var dismissals = 0
        var queries = 0
        val coordinator = IncomingCallCoordinator(
            scope = this,
            workerScope = backgroundScope,
            lookup = { queries++; detail() },
            statusSink = store,
            presenter = object : ShieldWarningPresenter {
                override fun showWarning(normalizedNumber: String, features: List<String>, targetHash: String?): ShieldOverlayPresentation {
                    warnings += Triple(normalizedNumber, features, targetHash)
                    return ShieldOverlayPresentation(ShieldOverlayState.Shown, "synthetic overlay message")
                }
                override fun dismissWarning(): ShieldOverlayPresentation {
                    dismissals++
                    return ShieldOverlayPresentation(ShieldOverlayState.Dismissed)
                }
            },
            elapsedMillis = { testScheduler.currentTime },
        )
        coordinator.onPhoneState(IncomingCallState.Ringing, NUMBER)
        advanceUntilIdle()
        assertEquals(listOf(Triple(NUMBER, listOf("Synthetic feature"), "synthetic hash")), warnings)
        assertEquals(ShieldLiveStage.MatchFound, store.read().stage)
        assertRedacted(store.read())
        coordinator.onPhoneState(IncomingCallState.Offhook)
        assertEquals(ShieldLiveStage.Idle, store.read().stage)
        assertRedacted(store.read())
        advanceUntilIdle()
        assertEquals(1, queries)
        assertEquals(2, dismissals)
    }

    @Test
    fun backupAndBothExtractionSectionsExcludeDiagnosticsAndRecovery() {
        fun excludedPaths(file: String, section: String): Set<String> {
            val document = DocumentBuilderFactory.newInstance().newDocumentBuilder()
                .parse(File("src\\main\\res\\xml\\$file"))
            val root = document.getElementsByTagName(section).item(0)
            val children = root.childNodes
            return (0 until children.length).mapNotNull { index ->
                val node = children.item(index)
                if (node.nodeName == "exclude" && node.attributes.getNamedItem("domain").nodeValue == "sharedpref") {
                    node.attributes.getNamedItem("path").nodeValue
                } else null
            }.toSet()
        }
        val expected = setOf("shield_live_status.xml", "shield_live_status.xml.bak", "device_binding_recovery.xml")
        assertEquals(expected, excludedPaths("backup_rules.xml", "full-backup-content"))
        assertEquals(expected, excludedPaths("data_extraction_rules.xml", "cloud-backup"))
        assertEquals(expected, excludedPaths("data_extraction_rules.xml", "device-transfer"))
        val manifest = File("src\\main\\AndroidManifest.xml").readText()
        assertTrue(manifest.contains("android:fullBackupContent=\"@xml/backup_rules\""))
        assertTrue(manifest.contains("android:dataExtractionRules=\"@xml/data_extraction_rules\""))
    }

    private fun preferenceAdapter(
        calls: MutableList<String>,
        commit: () -> Boolean,
    ): LegacyShieldDiagnosticsDeletion = LegacyShieldDiagnosticsDeletion { name ->
        calls += name
        commit()
    }

    private class FakeLegacy : LegacyShieldDiagnostics {
        val disk = mutableMapOf<String, Any>(
            "raw_incoming_number" to NUMBER,
            "normalized_number" to NUMBER,
            "target_hash" to "synthetic hash",
            "features" to "Synthetic feature",
            "error_message" to "synthetic backend caller detail",
            "overlay_message" to "synthetic exception caller detail",
            "stage" to "NoMatch",
            "updated_at" to 123L,
        )
        val cache = disk.toMutableMap()
        var succeeds = true
        var attempts = 0

        override fun clear(): Boolean {
            attempts++
            cache.clear()
            if (succeeds) disk.clear()
            return succeeds
        }
    }

    private fun assertRedacted(status: ShieldLiveStatus) {
        assertNull(status.rawIncomingNumber)
        assertNull(status.normalizedNumber)
        assertNull(status.targetHash)
        assertTrue(status.features.isEmpty())
        assertNull(status.errorMessage)
        assertNull(status.overlayMessage)
    }

    private fun detail(stage: ShieldLiveStage = ShieldLiveStage.MatchFound) = ShieldLiveStatus(
        stage = stage,
        rawIncomingNumber = NUMBER,
        normalizedNumber = NUMBER,
        targetHash = "synthetic hash",
        features = listOf("Synthetic feature"),
        errorMessage = "synthetic backend caller detail",
        overlayMessage = "synthetic exception caller detail",
        overlayState = ShieldOverlayState.Shown,
        updatedAtEpochMillis = 123L,
    )

    private companion object {
        const val NUMBER = "+421900000001"
    }
}
