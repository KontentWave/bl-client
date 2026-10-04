package com.example.myapplication.security

import org.junit.Assert.assertEquals
import org.junit.Test

class CanonicalPayloadFactoryTest {
    @Test
    fun normalizedReport_matchesGoldenPhpJsonIncludingEscapes() {
        val number = com.example.myapplication.phone.PhoneNumberNormalizer.normalize("0900 000 001")
        assertEquals(
            "{\"client_phone_number\":\"+421900000001\",\"feature\":\"no_show\"," +
                "\"public_key\":\"pem\\/with\\\"quotes\\\"\\\\and\\n\\t\\u0001\"}",
            CanonicalPayloadFactory.createReport(checkNotNull(number), "no_show", "pem/with\"quotes\"\\and\n\t\u0001"),
        )
    }

    @Test
    fun create_returnsCompactJsonInStableFieldOrder() {
        val payload = CanonicalPayloadFactory.create(
            challengeId = "0d5f35ea-331d-4df1-b9d6-3df7ab7fdc7c",
            normalizedPublicKey = "-----BEGIN PUBLIC KEY-----\nline1\nline2\n-----END PUBLIC KEY-----",
        )

        assertEquals(
            "{" +
                "\"challenge_id\":\"0d5f35ea-331d-4df1-b9d6-3df7ab7fdc7c\"," +
                "\"public_key\":\"-----BEGIN PUBLIC KEY-----\\nline1\\nline2\\n-----END PUBLIC KEY-----\"" +
                "}",
            payload,
        )
    }

    @Test
    fun create_escapesQuotesAndBackslashes() {
        val payload = CanonicalPayloadFactory.create(
            challengeId = "challenge-\"quoted\"",
            normalizedPublicKey = "pem\\value",
        )

        assertEquals(
            "{" +
                "\"challenge_id\":\"challenge-\\\"quoted\\\"\"," +
                "\"public_key\":\"pem\\\\value\"" +
                "}",
            payload,
        )
    }

    @Test
    fun create_escapesForwardSlashesLikeLaravelJsonEncode() {
        val payload = CanonicalPayloadFactory.create(
            challengeId = "challenge-123",
            normalizedPublicKey = "pem/with/slashes",
        )

        assertEquals(
            "{" +
                "\"challenge_id\":\"challenge-123\"," +
                "\"public_key\":\"pem\\/with\\/slashes\"" +
                "}",
            payload,
        )
    }

    @Test
    fun createReport_returnsCompactJsonInStableFieldOrder() {
        val payload = CanonicalPayloadFactory.createReport(
            clientPhoneNumber = "+421900123456",
            feature = "non_payment",
            normalizedPublicKey = "-----BEGIN PUBLIC KEY-----\nline1\n-----END PUBLIC KEY-----",
        )

        assertEquals(
            "{" +
                "\"client_phone_number\":\"+421900123456\"," +
                "\"feature\":\"non_payment\"," +
                "\"public_key\":\"-----BEGIN PUBLIC KEY-----\\nline1\\n-----END PUBLIC KEY-----\"" +
                "}",
            payload,
        )
    }

    @Test
    fun createBlacklistCheck_returnsCompactJsonInStableFieldOrder() {
        val payload = CanonicalPayloadFactory.createBlacklistCheck(
            targetHash = "abc123def456",
            normalizedPublicKey = "pem/with/slashes",
        )

        assertEquals(
            "{" +
                "\"target_hash\":\"abc123def456\"," +
                "\"public_key\":\"pem\\/with\\/slashes\"" +
                "}",
            payload,
        )
    }
}
