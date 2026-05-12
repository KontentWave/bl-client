package expo.modules.cryptovault

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyInfo
import android.security.keystore.KeyProperties
import java.security.InvalidAlgorithmParameterException
import java.security.KeyFactory
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.security.PrivateKey
import java.security.ProviderException
import java.security.PublicKey
import java.security.Signature
import java.security.interfaces.ECPublicKey
import java.security.spec.ECGenParameterSpec

private const val ANDROID_KEYSTORE = "AndroidKeyStore"
private const val EC_CURVE = "secp256r1"
private const val SIGNATURE_ALGORITHM = "SHA256withECDSA"

data class CryptoVaultKeyPairStatus(
    val keyAlias: String,
    val publicKeyPem: String,
    val isHardwareBacked: Boolean,
    val description: String,
)

class AndroidKeystoreCryptoVault(
    private val keyAlias: String,
    private val requireHardwareBacked: Boolean = true,
) {
    fun ensureKeyPair(): CryptoVaultKeyPairStatus {
        if (!hasKeyPair()) {
            generateKeyPair()
        }

        val publicKey = getPublicKeyOrNull()
            ?: error("Public key for alias '$keyAlias' was not found.")
        val securityProfile = inspectSecurityProfile()

        if (requireHardwareBacked && !securityProfile.isHardwareBacked) {
            deleteKeyPair()
            throw IllegalStateException(
                "A hardware-backed Android Keystore key is required. Detected ${securityProfile.description}.",
            )
        }

        return CryptoVaultKeyPairStatus(
            keyAlias = keyAlias,
            publicKeyPem = PublicKeyPemEncoder.toPem(publicKey),
            isHardwareBacked = securityProfile.isHardwareBacked,
            description = securityProfile.description,
        )
    }

    fun getPublicKeyPem(): String {
        ensureKeyPair()
        return PublicKeyPemEncoder.toPem(
            getPublicKeyOrNull() ?: error("Public key for alias '$keyAlias' was not found."),
        )
    }

    fun signPayload(canonicalJson: String): String {
        ensureKeyPair()
        val signature = Signature.getInstance(SIGNATURE_ALGORITHM)
        signature.initSign(getPrivateKey())
        signature.update(canonicalJson.toByteArray(Charsets.UTF_8))
        return SignatureEncoder.toBase64(signature.sign())
    }

    fun deleteKeyPair() {
        val keyStore = keyStore()
        if (keyStore.containsAlias(keyAlias)) {
            keyStore.deleteEntry(keyAlias)
        }
    }

    private fun inspectSecurityProfile(): KeySecurityProfile {
        val privateKey = getPrivateKeyOrNull()
            ?: error("Private key for alias '$keyAlias' was not found.")
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
            throw IllegalStateException(
                "Hardware-backed Android Keystore key generation failed on this device.",
                exception,
            )
        } catch (exception: InvalidAlgorithmParameterException) {
            throw IllegalStateException(
                "Android Keystore key generation parameters were rejected.",
                exception,
            )
        }
    }

    private fun hasKeyPair(): Boolean = keyStore().containsAlias(keyAlias)

    private fun getPublicKeyOrNull(): PublicKey? =
        keyStore().getCertificate(keyAlias)?.publicKey

    private fun getPrivateKey(): PrivateKey =
        getPrivateKeyOrNull() ?: error("Private key for alias '$keyAlias' was not found.")

    private fun getPrivateKeyOrNull(): PrivateKey? =
        keyStore().getKey(keyAlias, null) as? PrivateKey

    private fun keyStore(): KeyStore =
        KeyStore.getInstance(ANDROID_KEYSTORE).apply {
            load(null)
        }
}

data class KeySecurityProfile(
    val isHardwareBacked: Boolean,
    val description: String,
)