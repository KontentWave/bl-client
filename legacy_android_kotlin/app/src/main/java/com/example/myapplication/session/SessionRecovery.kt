package com.example.myapplication.session

import com.example.myapplication.security.PublicKeyPemEncoder
import com.example.myapplication.time.ServerTimestamp
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

data class BindingMetadata(val keyFingerprint: String, val verifiedAt: String)

data class RecoveryState(
    val metadata: BindingMetadata? = null,
    val restored: Boolean = false,
    val message: String? = null,
)

/** A local navigation hint, never a server session or permission to replay a request. */
class SessionRecovery(
    private val store: BindingMetadataStore,
    private val keys: ExistingKeyProvider,
    private val backendScope: String,
) {
    private val mutableState = MutableStateFlow(RecoveryState())
    private var storageBlocked = false
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
        if (values["version"] != "1" || savedScope.isNullOrBlank() ||
            fingerprint == null || !fingerprint.matches(Regex("[0-9a-f]{64}")) ||
            verifiedAt == null || !validTimestamp(verifiedAt)
        ) {
            discard("Saved device recovery metadata is invalid. Verify this device explicitly.")
            return
        }
        if (savedScope != backendScope) {
            // A host change is not revocation. Retain the old hint, but never reuse it here.
            mutableState.value = RecoveryState(message =
                "The API server has changed. Verify this device explicitly on this server. Previous recovery metadata and the device key were retained; no SMS was sent automatically.")
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
                        BindingMetadata(fingerprint, verifiedAt),
                        restored = previous.metadata?.keyFingerprint != fingerprint || previous.restored,
                        message = if (previous.metadata != null) previous.message else null,
                    )
                }
            }
        }
    }

    @Synchronized
    fun recordVerified(publicKeyPem: String, verifiedAt: String) {
        if (!validTimestamp(verifiedAt)) {
            mutableState.value = RecoveryState(message = "Verification returned invalid binding metadata. Recovery was not saved.")
            return
        }
        val submittedFingerprint = keyFingerprint(PublicKeyPemEncoder.fromPem(publicKeyPem))
        val key = keys.lookup()
        if (key !is ExistingKey.Available || keyFingerprint(key.publicKey) != submittedFingerprint) {
            mutableState.value = RecoveryState(message = "Verification completed, but the signing key is unavailable or changed. Recovery was not saved; no key was replaced.")
            return
        }
        val saved = store.write(mapOf(
            "version" to "1",
            "backend_scope" to backendScope,
            "key_fingerprint" to submittedFingerprint,
            "verified_at" to verifiedAt,
        ))
        // A failed replacement must not schedule deletion of the previous host's hint.
        val savedScope = store.read()["backend_scope"] as? String
        storageBlocked = !saved && (savedScope == null || savedScope == backendScope)
        mutableState.value = RecoveryState(
            metadata = BindingMetadata(submittedFingerprint, verifiedAt),
            message = if (saved) null else "Device verification succeeded, but recovery metadata could not be saved. A restart may require verification.",
        )
    }

    @Synchronized
    fun authorizationRejected(publicKeyPem: String) {
        val fingerprint = keyFingerprint(PublicKeyPemEncoder.fromPem(publicKeyPem))
        val saved = store.read()
        // Prefer live identity if a newer verification could not be persisted.
        val currentFingerprint = mutableState.value.metadata?.keyFingerprint
            ?: saved.takeIf { it["backend_scope"] == backendScope }
                ?.get("key_fingerprint") as? String
        if (currentFingerprint == fingerprint) {
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
        val savedScope = store.read()["backend_scope"] as? String
        if (savedScope != null && savedScope != backendScope) {
            refresh()
            return
        }
        discard("Onboarding was opened explicitly. No SMS has been sent and the existing key/server binding was not reset.")
    }

    private fun discard(message: String) {
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
