package com.example.myapplication.security

import android.util.Log
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyInfo
import android.security.keystore.KeyProperties
import com.example.myapplication.BuildConfig
import java.security.InvalidAlgorithmParameterException
import java.security.KeyFactory
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.security.MessageDigest
import java.security.PrivateKey
import java.security.ProviderException
import java.security.PublicKey
import java.security.Signature
import java.security.interfaces.ECPublicKey
import java.security.spec.ECGenParameterSpec

private const val ANDROID_KEYSTORE = "AndroidKeyStore"
private const val EC_CURVE = "secp256r1"
private const val SIGNATURE_ALGORITHM = "SHA256withECDSA"
private const val LOG_TAG = "SecurityManager"
const val DEFAULT_KEY_ALIAS = "phase1_device_key"

class DeviceHardwareSecurityUnavailableException(message: String, cause: Throwable? = null) :
    IllegalStateException(message, cause)

data class KeySecurityProfile(
    val isHardwareBacked: Boolean,
    val description: String,
)

fun interface KeySecurityInspector {
    fun inspect(privateKey: PrivateKey): KeySecurityProfile
}

object AndroidKeystoreKeySecurityInspector : KeySecurityInspector {
    override fun inspect(privateKey: PrivateKey): KeySecurityProfile {
        val keyFactory = KeyFactory.getInstance(privateKey.algorithm, ANDROID_KEYSTORE)
        val keyInfo = keyFactory.getKeySpec(privateKey, KeyInfo::class.java)
        return if (keyInfo.isInsideSecureHardware) {
            KeySecurityProfile(
                isHardwareBacked = true,
                description = "secure hardware (TEE/KeyMint or StrongBox)",
            )
        } else {
            KeySecurityProfile(
                isHardwareBacked = false,
                description = "software-only Android Keystore backing",
            )
        }
    }
}

class SecurityManager(
    private val keyAlias: String = DEFAULT_KEY_ALIAS,
    private val requireHardwareBacked: Boolean = true,
    private val keySecurityInspector: KeySecurityInspector = AndroidKeystoreKeySecurityInspector,
) {
    fun ensureKeyPair() {
        if (hasKeyPair() && hasAcceptableKeyPair()) {
            return
        }

        if (hasKeyPair()) {
            deleteKeyPair()
        }

        generateKeyPair()
        validateHardwareSecurity()
    }

    fun getPublicKey(): PublicKey {
        ensureKeyPair()
        return getPublicKeyOrNull()
            ?: error("Public key for alias '$keyAlias' was not found.")
    }

    fun getPublicKeyPem(): String = PublicKeyPemEncoder.toPem(getPublicKey())

    fun createSignedRequestPayload(
        canonicalPayload: String,
        normalizedPublicKey: String = PublicKeyPemEncoder.normalize(getPublicKeyPem()),
    ): SignedRequestPayload {
        ensureKeyPair()
        val signatureBytes = signWithCurrentKey(canonicalPayload.toByteArray(Charsets.UTF_8))
        return SignedRequestPayload(
            publicKey = normalizedPublicKey,
            signature = SignatureEncoder.toBase64(signatureBytes),
        )
    }

    fun createSignedVerifyPayload(challengeId: String): SignedVerifyPayload {
        ensureKeyPair()
        val publicKey = getPublicKeyOrNull() ?: error("Public key for alias '$keyAlias' was not found.")
        val normalizedPublicKey = PublicKeyPemEncoder.normalize(
            PublicKeyPemEncoder.toPem(publicKey),
        )
        val payload = CanonicalPayloadFactory.createVerify(challengeId, normalizedPublicKey)
        val signatureBytes = signWithCurrentKey(payload.toByteArray(Charsets.UTF_8))
        logVerifyDiagnostics(
            challengeId = challengeId,
            publicKey = publicKey,
            normalizedPublicKey = normalizedPublicKey,
            payload = payload,
            signatureBytes = signatureBytes,
        )
        return SignedVerifyPayload(
            publicKey = normalizedPublicKey,
            signature = SignatureEncoder.toBase64(signatureBytes),
        )
    }

    fun signCanonicalPayload(challengeId: String): String {
        return createSignedVerifyPayload(challengeId).signature
    }

    fun sign(payload: ByteArray): ByteArray {
        ensureKeyPair()
        return signWithCurrentKey(payload)
    }

    private fun signWithCurrentKey(payload: ByteArray): ByteArray {
        val signature = Signature.getInstance(SIGNATURE_ALGORITHM)
        signature.initSign(getPrivateKey())
        signature.update(payload)
        return signature.sign()
    }

    fun deleteKeyPair() {
        val keyStore = keyStore()
        if (keyStore.containsAlias(keyAlias)) {
            keyStore.deleteEntry(keyAlias)
        }
    }

    private fun hasKeyPair(): Boolean = keyStore().containsAlias(keyAlias)

    private fun hasAcceptableKeyPair(): Boolean {
        if (!requireHardwareBacked) {
            return true
        }

        val privateKey = getPrivateKeyOrNull() ?: return false
        return runCatching {
            keySecurityInspector.inspect(privateKey).isHardwareBacked
        }.getOrDefault(false)
    }

    private fun generateKeyPair() {
        val builder = KeyGenParameterSpec.Builder(
            keyAlias,
            KeyProperties.PURPOSE_SIGN or KeyProperties.PURPOSE_VERIFY,
        )
            .setAlgorithmParameterSpec(ECGenParameterSpec(EC_CURVE))
            .setDigests(KeyProperties.DIGEST_SHA256)
            .setUserAuthenticationRequired(false)

        try {
            val keyPairGenerator =
                KeyPairGenerator.getInstance(KeyProperties.KEY_ALGORITHM_EC, ANDROID_KEYSTORE)
            keyPairGenerator.initialize(builder.build())
            val generatedKeyPair = keyPairGenerator.generateKeyPair()
            require(generatedKeyPair.public is ECPublicKey) {
                "Expected an elliptic-curve public key from the Android Keystore."
            }
        } catch (exception: ProviderException) {
            if (requireHardwareBacked) {
                throw DeviceHardwareSecurityUnavailableException(
                    message = "Hardware-backed Android Keystore key generation failed on this device.",
                    cause = exception,
                )
            }
            throw exception
        } catch (exception: InvalidAlgorithmParameterException) {
            if (requireHardwareBacked) {
                throw DeviceHardwareSecurityUnavailableException(
                    message = "Android Keystore key generation parameters were rejected.",
                    cause = exception,
                )
            }
            throw exception
        }
    }

    private fun validateHardwareSecurity() {
        if (!requireHardwareBacked) {
            return
        }

        val privateKey = getPrivateKeyOrNull()
            ?: throw DeviceHardwareSecurityUnavailableException(
                message = "Private key for alias '$keyAlias' was not found after generation.",
            )

        val securityProfile = runCatching {
            keySecurityInspector.inspect(privateKey)
        }.getOrElse { exception ->
            deleteKeyPair()
            throw DeviceHardwareSecurityUnavailableException(
                message = "Unable to confirm that the Android Keystore key is backed by secure hardware.",
                cause = exception,
            )
        }

        if (!securityProfile.isHardwareBacked) {
            deleteKeyPair()
            throw DeviceHardwareSecurityUnavailableException(
                message = "A hardware-backed Android Keystore key is required. Detected ${securityProfile.description}.",
            )
        }
    }

    private fun getPrivateKey(): PrivateKey =
        getPrivateKeyOrNull() ?: error("Private key for alias '$keyAlias' was not found.")

    private fun getPublicKeyOrNull(): PublicKey? =
        keyStore().getCertificate(keyAlias)?.publicKey

    private fun getPrivateKeyOrNull(): PrivateKey? = keyStore().getKey(keyAlias, null) as? PrivateKey

    private fun logVerifyDiagnostics(
        challengeId: String,
        publicKey: PublicKey,
        normalizedPublicKey: String,
        payload: String,
        signatureBytes: ByteArray,
    ) {
        if (!BuildConfig.DEBUG) {
            return
        }

        val verifier = Signature.getInstance(SIGNATURE_ALGORITHM)
        verifier.initVerify(publicKey)
        verifier.update(payload.toByteArray(Charsets.UTF_8))
        val selfVerified = verifier.verify(signatureBytes)

        Log.d(
            LOG_TAG,
            "verify_diag challenge_id=$challengeId " +
                "public_key_sha256=${normalizedPublicKey.sha256()} " +
                "payload_sha256=${payload.sha256()} " +
                "signature_sha256=${signatureBytes.sha256()} " +
                "signature_bytes=${signatureBytes.size} " +
                "self_verify=$selfVerified",
        )
    }

    private fun String.sha256(): String =
        MessageDigest.getInstance("SHA-256")
            .digest(toByteArray(Charsets.UTF_8))
            .toHex()

    private fun ByteArray.sha256(): String =
        MessageDigest.getInstance("SHA-256")
            .digest(this)
            .toHex()

    private fun ByteArray.toHex(): String = joinToString(separator = "") { eachByte -> "%02x".format(eachByte) }

    private fun keyStore(): KeyStore =
        KeyStore.getInstance(ANDROID_KEYSTORE).apply {
            load(null)
        }
}


