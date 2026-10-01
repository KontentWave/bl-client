package com.example.myapplication.security

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PublicKeyPemEncoderTest {
    private val samplePem = """
        -----BEGIN PUBLIC KEY-----
        MFkwEwYHKoZIzj0CAQYIKoZIzj0DAQcDQgAE4Q0hKDpkowmNR9vKp4epPXolhUk3
        jpYVGEgIk6du6lXknHXROXH8KimzBAKgwUfaUvsR3aPacB1JFz6laI3vfA==
        -----END PUBLIC KEY-----
    """.trimIndent()

    @Test
    fun toPem_roundTripsKnownEcPublicKeyWithoutChangingContent() {
        val publicKey = PublicKeyPemEncoder.fromPem(samplePem)

        val encoded = PublicKeyPemEncoder.toPem(publicKey)

        assertEquals(samplePem, encoded)
    }

    @Test
    fun toPem_doesNotIntroduceLeadingSpacesOnInternalLines() {
        val publicKey = PublicKeyPemEncoder.fromPem(samplePem)

        val encodedLines = PublicKeyPemEncoder.toPem(publicKey).lines()

        assertEquals(4, encodedLines.size)
        assertEquals("-----BEGIN PUBLIC KEY-----", encodedLines.first())
        assertEquals("-----END PUBLIC KEY-----", encodedLines.last())
        assertTrue(encodedLines.drop(1).dropLast(1).all { it.isNotBlank() })
        assertFalse(encodedLines.drop(1).any { it.startsWith(" ") || it.startsWith("\t") })
    }
}

