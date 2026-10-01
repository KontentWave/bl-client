package com.example.myapplication.security

import android.util.Base64
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.security.Signature
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class SecurityManagerInstrumentedTest {
    @Test
    fun ensureKeyPair_generatesPublicKeyAndSignatureWhenHardwareBackingIsOptional() {
        val keyAlias = "test_optional_${UUID.randomUUID()}"
        val securityManager = SecurityManager(
            keyAlias = keyAlias,
            requireHardwareBacked = false,
        )

        try {
            securityManager.ensureKeyPair()

            val publicKeyPem = securityManager.getPublicKeyPem()
            val signedPayload = securityManager.createSignedVerifyPayload("challenge-123")
            val payload = CanonicalPayloadFactory.create("challenge-123", signedPayload.publicKey)

            assertTrue(publicKeyPem.contains("BEGIN PUBLIC KEY"))
            assertEqualsNormalized(publicKeyPem, signedPayload.publicKey)
            assertTrue(signedPayload.signature.isNotBlank())
            assertTrue(
                verifySignature(
                    publicKey = securityManager.getPublicKey(),
                    payload = payload.toByteArray(Charsets.UTF_8),
                    signatureBase64 = signedPayload.signature,
                ),
            )
        } finally {
            securityManager.deleteKeyPair()
        }
    }

    @Test
    fun ensureKeyPair_throwsWhenGeneratedKeyIsNotHardwareBacked() {
        val keyAlias = "test_required_${UUID.randomUUID()}"
        val securityManager = SecurityManager(
            keyAlias = keyAlias,
            requireHardwareBacked = true,
            keySecurityInspector = KeySecurityInspector {
                KeySecurityProfile(
                    isHardwareBacked = false,
                    description = "software-only Android Keystore backing",
                )
            },
        )

        try {
            val thrown = runCatching {
                securityManager.ensureKeyPair()
            }.exceptionOrNull()

            assertTrue(thrown is DeviceHardwareSecurityUnavailableException)
            assertTrue(thrown?.message?.contains("hardware-backed", ignoreCase = true) == true)
        } finally {
            securityManager.deleteKeyPair()
        }
    }

    private fun assertEqualsNormalized(expectedPem: String, actualPem: String) {
        assertFalse(actualPem.contains("\r\n"))
        assertTrue(actualPem.isNotBlank())
        assertTrue(expectedPem.replace("\r\n", "\n").trim() == actualPem)
    }

    private fun verifySignature(
        publicKey: java.security.PublicKey,
        payload: ByteArray,
        signatureBase64: String,
    ): Boolean {
        val verifier = Signature.getInstance("SHA256withECDSA")
        verifier.initVerify(publicKey)
        verifier.update(payload)
        return verifier.verify(Base64.decode(signatureBase64, Base64.DEFAULT))
    }
}

