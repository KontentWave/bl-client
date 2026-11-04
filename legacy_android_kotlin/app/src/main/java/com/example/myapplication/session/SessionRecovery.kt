package com.example.myapplication.session

import com.example.myapplication.security.PublicKeyPemEncoder
import com.example.myapplication.time.ServerTimestamp
import com.google.gson.JsonParser
import com.google.gson.JsonParseException
import java.security.MessageDigest
import java.security.PublicKey
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

sealed interface ExistingKey {
    data class Available(val publicKey: PublicKey) : ExistingKey
    data object Missing : ExistingKey
    data object Invalid : ExistingKey
    data object Unavailable : ExistingKey
}

fun interface ExistingKeyProvider {
    fun lookup(): ExistingKey
}

interface BindingMetadataStore {
    fun read(): Map<String, *>
    fun write(values: Map<String, String>): Boolean
    fun clear(): Boolean
}

data class BindingMetadata(
    val keyFingerprint: String,
    val verifiedAt: String,
    val lifecycle: EnrollmentLifecycle? = null,
    val renewalAttempt: RenewalAttempt? = null,
)

data class RecoveryState(
    val metadata: BindingMetadata? = null,
    val restored: Boolean = false,
    val message: String? = null,
    val offboarded: Boolean = false,
)

/** A local navigation hint, never a server session or permission to replay a request. */
class SessionRecovery(
    private val store: BindingMetadataStore,
    private val keys: ExistingKeyProvider,
    private val backendScope: String,
    private val nowMillis: () -> Long = System::currentTimeMillis,
) {
    private val mutableState = MutableStateFlow(RecoveryState())
    private var storageBlocked = false
    private var revision = 0L
    val state = mutableState.asStateFlow()

    init {
        refresh()
    }

    @Synchronized
    fun refresh() {
        if (storageBlocked) {
            storageBlocked = !store.clear()
            return
        }
        val values = store.read()
        if (values.isEmpty()) {
            // Preserve a rejection/storage message during resume, but never a verified hint.
            mutableState.value = mutableState.value.copy(metadata = null, restored = false)
            return
        }
        val fingerprint = values["key_fingerprint"] as? String
        val verifiedAt = values["verified_at"] as? String
        val savedScope = values["backend_scope"] as? String
        if (values["version"] !in setOf("1", "2") || savedScope.isNullOrBlank() ||
            fingerprint == null || !fingerprint.matches(Regex("[0-9a-f]{64}")) ||
            verifiedAt == null || !validTimestamp(verifiedAt)
        ) {
            discard("Saved device recovery metadata is invalid. Verify this device explicitly.")
            return
        }
        if (savedScope != backendScope) {
            mutableState.value = RecoveryState(message =
                "The API server has changed. Verify this device explicitly on this server. Previous recovery metadata and the device key were retained; no SMS was sent automatically.")
            return
        }
        val lifecycle = if (values["version"] == "2") try {
            val root = JsonParser.parseString(values["lifecycle"] as? String ?: "")
            if (!root.isJsonObject) throw JsonParseException("Invalid lifecycle record")
            EnrollmentLifecycle.parse(root.asJsonObject)
        } catch (_: JsonParseException) {
            discard("Saved enrollment lifecycle is invalid. Verify this device explicitly.")
            return
        } else null
        if (lifecycle != null && lifecycle.verifiedAt != verifiedAt) {
            discard("Saved enrollment timestamps are inconsistent. Verify this device explicitly.")
            return
        }
        val attempt = if (values["renewal_attempt"] != null) try {
            val root = JsonParser.parseString(values["renewal_attempt"] as? String ?: "")
            if (!root.isJsonObject) throw JsonParseException("Invalid renewal record")
            RenewalAttempt.parse(root.asJsonObject)
        } catch (_: JsonParseException) {
            discard("Saved renewal scheduling metadata is invalid. Verify this device explicitly.")
            return
        } else null
        if (lifecycle?.terminal == true) {
            mutableState.value = RecoveryState(message = "This enrollment was offboarded (${lifecycle.reason}). Re-enroll explicitly; the device key and community data were retained.", offboarded = true)
            return
        }
        when (val key = keys.lookup()) {
            ExistingKey.Missing -> discard("The previously bound device key is missing. Explicit verification is required.")
            ExistingKey.Invalid -> discard("The existing device key is invalid. No key was replaced. Device recovery requires support.")
            ExistingKey.Unavailable -> {
                mutableState.value = RecoveryState(message = "The device key is temporarily unavailable. Reopen the app to retry; saved recovery metadata was retained.")
            }
            is ExistingKey.Available -> {
                if (keyFingerprint(key.publicKey) != fingerprint) {
                    discard("The device key has changed. Verify this device explicitly; no key was replaced.")
                } else {
                    val previous = mutableState.value
                    mutableState.value = RecoveryState(
                        BindingMetadata(fingerprint, verifiedAt, lifecycle, attempt),
                        restored = previous.metadata?.keyFingerprint != fingerprint || previous.restored,
                        message = if (previous.metadata != null) previous.message else null,
                    )
                }
            }
        }
    }

    @Synchronized
    fun recordVerified(publicKeyPem: String, verifiedAt: String, lifecycle: EnrollmentLifecycle? = null) {
        revision++
        if (!validTimestamp(verifiedAt)) {
            mutableState.value = RecoveryState(message = "Verification returned invalid binding metadata. Recovery was not saved.")
            return
        }
        if (lifecycle != null && (lifecycle.verifiedAt != verifiedAt || lifecycle.terminal)) {
            mutableState.value = RecoveryState(message = "Verification returned inconsistent enrollment metadata. Recovery was not saved.")
            return
        }
        val submittedFingerprint = keyFingerprint(PublicKeyPemEncoder.fromPem(publicKeyPem))
        val key = keys.lookup()
        if (key !is ExistingKey.Available || keyFingerprint(key.publicKey) != submittedFingerprint) {
            mutableState.value = RecoveryState(message = "Verification completed, but the signing key is unavailable or changed. Recovery was not saved; no key was replaced.")
            return
        }
        val due = lifecycle?.takeIf { it.kind == "ad" }?.nextRenewalAt
            ?.let { ServerTimestamp.parse(it)!!.toEpochMilli() }
        val metadata = BindingMetadata(submittedFingerprint, verifiedAt, lifecycle,
            if (due != null) RenewalAttempt(
                minOf(nowMillis(), due), 0, 0, due,
            ) else null,
        )
        val saved = store.write(valuesFor(metadata))
        // A failed replacement must not schedule deletion of the previous host's hint.
        val savedScope = store.read()["backend_scope"] as? String
        storageBlocked = !saved && (savedScope == null || savedScope == backendScope)
        mutableState.value = RecoveryState(
            metadata = metadata,
            message = if (saved) null else "Device verification succeeded, but recovery metadata could not be saved. A restart may require verification.",
        )
    }

    @Synchronized
    fun authorizationRejected(publicKeyPem: String, ticket: RecoveryTicket? = null) {
        if (ticket != null && !matches(ticket, publicKeyPem)) return
        val fingerprint = keyFingerprint(PublicKeyPemEncoder.fromPem(publicKeyPem))
        val saved = store.read()
        // Prefer live identity if a newer verification could not be persisted.
        val currentFingerprint = mutableState.value.metadata?.keyFingerprint
            ?: saved.takeIf { it["backend_scope"] == backendScope }
                ?.get("key_fingerprint") as? String
        if (currentFingerprint == fingerprint) {
            revision++
            val message = "The server rejected device authorization. Verify explicitly or contact support; no SMS was sent automatically."
            if (saved["backend_scope"] is String && saved["backend_scope"] != backendScope) {
                storageBlocked = false
                mutableState.value = RecoveryState(message = message)
            } else {
                discard(message)
            }
        }
    }

    @Synchronized
    fun forgetLocalHint() {
        revision++
        val savedScope = store.read()["backend_scope"] as? String
        if (savedScope != null && savedScope != backendScope) {
            refresh()
            return
        }
        discard("Onboarding was opened explicitly. No SMS has been sent and the existing key/server binding was not reset.")
    }

    @Synchronized
    fun ticket(): RecoveryTicket? = mutableState.value.metadata?.let {
        RecoveryTicket(it.keyFingerprint, revision, it.lifecycle?.enrollmentId)
    }

    @Synchronized
    fun matches(ticket: RecoveryTicket, publicKeyPem: String): Boolean {
        val metadata = mutableState.value.metadata ?: return false
        return revision == ticket.revision && metadata.keyFingerprint == ticket.fingerprint &&
            metadata.lifecycle?.enrollmentId == ticket.enrollmentId &&
            keyFingerprint(PublicKeyPemEncoder.fromPem(publicKeyPem)) == ticket.fingerprint
    }

    @Synchronized
    fun recordAttempt(ticket: RecoveryTicket, attempt: RenewalAttempt): Boolean {
        val current = mutableState.value.metadata ?: return false
        if (revision != ticket.revision || current.keyFingerprint != ticket.fingerprint ||
            current.lifecycle?.enrollmentId != ticket.enrollmentId) return false
        val updated = current.copy(renewalAttempt = attempt)
        if (!store.write(valuesFor(updated))) {
            mutableState.value = mutableState.value.copy(message = "Renewal scheduling could not be saved. No request was sent; retry when storage is available.")
            return false
        }
        mutableState.value = mutableState.value.copy(metadata = updated)
        return true
    }

    @Synchronized
    fun applyLifecycle(ticket: RecoveryTicket, publicKeyPem: String, lifecycle: EnrollmentLifecycle,
        renewalAttempt: RenewalAttempt? = null, updateAttempt: Boolean = false): Boolean {
        if (!matches(ticket, publicKeyPem)) return false
        val current = mutableState.value.metadata ?: return false
        if (lifecycle.verifiedAt != current.verifiedAt ||
            (ticket.enrollmentId != null && lifecycle.enrollmentId != ticket.enrollmentId) ||
            (current.lifecycle != null && lifecycle.kind != current.lifecycle.kind) ||
            (current.lifecycle != null && (lifecycle.version < current.lifecycle.version ||
                (lifecycle.version == current.lifecycle.version && lifecycle != current.lifecycle)))) return false
        if (lifecycle.terminal) {
            revision++
            val saved = store.write(valuesFor(current.copy(lifecycle = lifecycle, renewalAttempt = null)))
            val savedScope = store.read()["backend_scope"] as? String
            storageBlocked = !saved && (savedScope == null || savedScope == backendScope)
            mutableState.value = RecoveryState(offboarded = true, message =
                "The server confirmed this enrollment is inactive (${lifecycle.reason}). Re-enroll explicitly; no SMS was sent and the device key/community data were retained." +
                    if (saved) "" else " Offboarding could not be saved; this process remains offboarded, but a restart may restore an old unconfirmed hint.")
            return true
        }
        val updated = current.copy(lifecycle = lifecycle,
            renewalAttempt = if (updateAttempt) renewalAttempt else current.renewalAttempt)
        if (!store.write(valuesFor(updated))) {
            mutableState.value = mutableState.value.copy(message = "Server lifecycle was received, but could not be saved. The prior deadline was retained.")
            return false
        }
        mutableState.value = mutableState.value.copy(metadata = updated, message = null)
        return true
    }

    @Synchronized
    fun renewalMessage(ticket: RecoveryTicket, message: String) {
        if (revision == ticket.revision && mutableState.value.metadata?.keyFingerprint == ticket.fingerprint) {
            mutableState.value = mutableState.value.copy(message = message)
        }
    }

    private fun valuesFor(metadata: BindingMetadata): Map<String, String> = buildMap {
        put("version", if (metadata.lifecycle == null) "1" else "2")
        put("backend_scope", backendScope)
        put("key_fingerprint", metadata.keyFingerprint)
        put("verified_at", metadata.verifiedAt)
        metadata.lifecycle?.let { put("lifecycle", it.json()) }
        metadata.renewalAttempt?.let { put("renewal_attempt", it.json()) }
    }

    private fun discard(message: String) {
        revision++
        val cleared = store.clear()
        storageBlocked = !cleared
        mutableState.value = RecoveryState(message = if (cleared) message else
            "$message Saved recovery metadata could not be removed.")
    }

    private fun validTimestamp(value: String): Boolean = ServerTimestamp.parse(value) != null

    companion object {
        fun keyFingerprint(key: PublicKey): String = MessageDigest.getInstance("SHA-256")
            .digest(key.encoded).joinToString("") { "%02x".format(it) }
    }
}
