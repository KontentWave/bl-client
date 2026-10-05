package com.example.myapplication.data

import com.example.myapplication.data.remote.ApiClientFactory
import com.example.myapplication.security.SignedRequestFactory
import com.example.myapplication.security.SignedRequestPayload
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.SocketPolicy
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class BetaCompatibilityTest {
    private lateinit var server: MockWebServer
    private lateinit var auth: AuthRepository
    private lateinit var reports: ReportRepository
    private lateinit var queries: BlacklistQueryRepository
    private val key = "-----BEGIN PUBLIC KEY-----\nfixture\n-----END PUBLIC KEY-----"
    private val signature = "fixture-signature"

    @Before fun setup() {
        server = MockWebServer().also { it.start() }
        val url = server.url("/api/").toString()
        val signer = object : SignedRequestFactory {
            override fun createVerifyRequest(challengeId: String) = SignedRequestPayload(key, signature)
            override fun createReportRequest(clientPhoneNumber: String, feature: String) = SignedRequestPayload(key, signature)
            override fun createBlacklistCheckRequest(targetHash: String) = SignedRequestPayload(key, signature)
        }
        auth = AuthRepositoryImpl(ApiClientFactory.createAuthApi(url), ApiClientFactory.gson())
        reports = ReportRepositoryImpl(ApiClientFactory.createReportApi(url), signer, ApiClientFactory.gson())
        queries = BlacklistQueryRepositoryImpl(ApiClientFactory.createBlacklistApi(url), signer, ApiClientFactory.gson())
    }

    @After fun teardown() { server.shutdown() }

    private fun body(errors: String = "[]", meta: String = "{\"retryable\":true,\"retry_after\":60}", code: String = "rate_limited") =
        """{"success":false,"code":"$code","message":"Please wait.","errors":$errors,"meta":$meta}"""

    private fun enqueue(status: Int = 429, errors: String = "[]", meta: String = "{\"retryable\":true,\"retry_after\":60}", header: String? = "60", code: String = "rate_limited") {
        val response = MockResponse().setResponseCode(status).setBody(body(errors, meta, code))
        if (header != null) response.addHeader("Retry-After", header)
        server.enqueue(response)
    }

    private suspend fun call(endpoint: Int): ApiFailure = when (endpoint) {
        0 -> (auth.initiateAuth("https://amaterky.sk/synthetic") as InitiateAuthResult.Failure).let {
            ApiFailure(it.code, it.message, it.fieldErrors, it.retryable, it.retryAfterSeconds, it.responseMalformed)
        }
        1 -> (auth.verifyAuth("synthetic-challenge", "000000", key, signature) as VerifyAuthResult.Failure).let {
            ApiFailure(it.code, it.message, it.fieldErrors, it.retryable, it.retryAfterSeconds, it.responseMalformed)
        }
        2 -> (reports.submitReport("+421900000001", ReportingFeature.NO_SHOW) as ReportSubmissionResult.Failure).let {
            ApiFailure(it.code, it.message, it.fieldErrors, it.retryable, it.retryAfterSeconds, it.responseMalformed)
        }
        else -> (queries.checkTargetHash("a".repeat(64)) as BlacklistQueryResult.Failure).let {
            ApiFailure(it.code, it.message, it.fieldErrors, it.retryable, it.retryAfterSeconds, it.responseMalformed)
        }
    }

    @Test fun allEndpointsAcceptBothEmptyErrorForms() = runBlocking {
        for (endpoint in 0..3) for (errors in listOf("[]", "{}")) {
            enqueue(errors = errors)
            val result = call(endpoint)
            assertEquals("rate_limited", result.code)
            assertTrue(result.retryable)
            assertEquals(60L, result.retryAfterSeconds)
            assertFalse(result.responseMalformed)
            assertTrue(result.fieldErrors.isEmpty())
        }
        assertEquals(8, server.requestCount)
    }

    @Test fun allEndpointsPreservePopulatedFieldErrors() = runBlocking {
        for (endpoint in 0..3) {
            enqueue(errors = "{\"ad_url\":[\"Invalid ad.\"],\"otp\":[\"Invalid OTP.\"]}")
            val result = call(endpoint)
            assertEquals(listOf("Invalid ad."), result.fieldErrors["ad_url"])
            assertEquals(listOf("Invalid OTP."), result.fieldErrors["otp"])
            assertFalse(result.responseMalformed)
        }
    }

    @Test fun allEndpointsHandleProtectionUnavailableWithoutRetries() = runBlocking {
        for (endpoint in 0..3) {
            enqueue(status = 503, code = "abuse_protection_unavailable", meta = "{\"retryable\":true}", header = null)
            val result = call(endpoint)
            assertEquals("abuse_protection_unavailable", result.code)
            assertTrue(result.retryable)
            assertNull(result.retryAfterSeconds)
            assertFalse(result.responseMalformed)
        }
        assertEquals(4, server.requestCount)
    }

    @Test fun metadataPreferredAndHeaderIsFallback() = runBlocking {
        enqueue(meta = "{\"retryable\":true,\"retry_after\":7}", header = "90")
        assertEquals(7L, call(0).retryAfterSeconds)
        enqueue(meta = "{\"retryable\":true}", header = "17")
        assertEquals(17L, call(1).retryAfterSeconds)
    }

    @Test fun invalidMetadataFallsBackWithoutCoercionOrOverflow() = runBlocking {
        for (value in listOf("0", "-1", "1.5", "1e2", "\"20\"", "true", "{}", "[]", "null", "999999999999999999999999")) {
            enqueue(meta = "{\"retryable\":true,\"retry_after\":$value}", header = "23")
            assertEquals("value=$value", 23L, call(0).retryAfterSeconds)
        }
    }

    @Test fun invalidHeaderUsesConservativeFallback() = runBlocking {
        for (header in listOf(null, "0", "-2", "garbage", "1.5", "999999999999999999999999", "Wed, 01 Jan 2030 00:00:00 GMT")) {
            enqueue(meta = "{}", header = header)
            assertEquals(60L, call(0).retryAfterSeconds)
        }
    }

    @Test fun positiveDelaysAreVariableAndBounded() = runBlocking {
        for ((value, expected) in listOf("1" to 1L, "3600" to 3600L, "999999" to 86400L)) {
            enqueue(meta = "{\"retry_after\":$value}")
            assertEquals(expected, call(0).retryAfterSeconds)
        }
        enqueue(meta = "{}", header = "999999")
        assertEquals(86400L, call(0).retryAfterSeconds)
    }

    @Test fun malformedErrorsAreNotSilentlyDiscarded() = runBlocking {
        for (errors in listOf("[\"error\"]", "false", "{\"otp\":\"wrong\"}", "{\"otp\":[42]}")) {
            enqueue(errors = errors)
            val result = call(0)
            assertTrue(result.responseMalformed)
            assertEquals("rate_limited", result.code)
            assertEquals(60L, result.retryAfterSeconds)
        }
        enqueue(status = 422, errors = "[1]", code = "validation_failed")
        assertEquals("response_parse_failed", call(0).code)
    }

    @Test fun malformed429StillRetainsHttpCooldown() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(429).addHeader("Retry-After", "11").setBody("not json"))
        val result = call(0)
        assertEquals("rate_limited", result.code)
        assertTrue(result.responseMalformed)
        assertEquals(11L, result.retryAfterSeconds)
    }

    @Test fun ambiguousInitiationIsNeverReplayedByProductionHttpClient() = runBlocking {
        server.enqueue(MockResponse().setSocketPolicy(SocketPolicy.DISCONNECT_AFTER_REQUEST))
        assertEquals("network_unavailable", call(0).code)
        assertEquals(1, server.requestCount)
        assertNotNull(server.takeRequest(100, java.util.concurrent.TimeUnit.MILLISECONDS))
        assertNull(server.takeRequest(100, java.util.concurrent.TimeUnit.MILLISECONDS))
    }

    @Test fun initiationReadTimeoutIsNotReplayed() = runBlocking {
        // Exercise the actual production read timeout, not a fake repository result.
        server.enqueue(MockResponse().setSocketPolicy(SocketPolicy.NO_RESPONSE))
        assertEquals("network_unavailable", call(0).code)
        assertEquals(1, server.requestCount)
        assertNotNull(server.takeRequest(100, java.util.concurrent.TimeUnit.MILLISECONDS))
        assertNull(server.takeRequest(100, java.util.concurrent.TimeUnit.MILLISECONDS))
    }

    @Test fun malformedSuccessAndSmsFailureDoNotResend() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(201).setBody("broken json"))
        assertEquals("response_parse_failed", call(0).code)
        enqueue(status = 503, code = "sms_dispatch_failed", meta = "{\"retryable\":true}")
        assertEquals("sms_dispatch_failed", call(0).code)
        assertEquals(2, server.requestCount)
    }

    @Test fun zeroDelay503DoesNotTriggerOkHttpFollowUp() = runBlocking {
        enqueue(status = 503, code = "sms_dispatch_failed", meta = "{\"retryable\":true}", header = "0")
        enqueue(status = 503, code = "unexpected_replay")
        assertEquals("sms_dispatch_failed", call(0).code)
        assertEquals(1, server.requestCount)
    }

    @Test fun generatedP256PemAndSignatureFitServerBounds() {
        val generator = java.security.KeyPairGenerator.getInstance("EC")
        generator.initialize(java.security.spec.ECGenParameterSpec("secp256r1"))
        val pair = generator.generateKeyPair()
        val pem = com.example.myapplication.security.PublicKeyPemEncoder.toPem(pair.public)
        val payload = com.example.myapplication.security.CanonicalPayloadFactory.createVerify("synthetic", pem)
        val signer = java.security.Signature.getInstance("SHA256withECDSA")
        signer.initSign(pair.private)
        signer.update(payload.toByteArray(Charsets.UTF_8))
        val encoded = java.util.Base64.getEncoder().encodeToString(signer.sign())
        assertTrue(pem.length <= 8192)
        assertTrue(encoded.length <= 4096)
    }

    @Test fun correctedOtpIsOneExplicitWireRequestAgainstSameChallengeWithoutInitiation() = runBlocking {
        enqueue(status = 422, code = "otp_invalid_or_expired", errors = """{"otp":["Invalid or expired."]}""", meta = """{"retryable":false}""", header = null)
        val rejected = auth.verifyAuth("synthetic-challenge", "000000", key, signature) as VerifyAuthResult.Failure
        assertEquals("otp_invalid_or_expired", rejected.code)
        assertFalse(rejected.retryable)
        assertFalse(rejected.responseMalformed)
        assertEquals(listOf("Invalid or expired."), rejected.fieldErrors["otp"])
        assertEquals(1, server.requestCount)
        server.enqueue(MockResponse().setBody(
            """{"success":true,"code":"auth.verified","data":{"challenge_id":"synthetic-challenge","masked_phone_number":"masked","verified_at":"2030-01-01T00:00:00Z"},"meta":{}}""",
        ))
        assertTrue(auth.verifyAuth("synthetic-challenge", "123456", key, signature) is VerifyAuthResult.Success)
        for (otp in listOf("000000", "123456")) {
            val request = server.takeRequest()
            assertEquals("/api/auth/verify", request.path)
            val body = com.google.gson.JsonParser.parseString(request.body.readUtf8()).asJsonObject
            assertEquals("synthetic-challenge", body["challenge_id"].asString)
            assertEquals(otp, body["otp"].asString)
            assertEquals(key, body["public_key"].asString)
            assertEquals(signature, body["signature"].asString)
        }
        assertEquals(2, server.requestCount)
        assertNull(server.takeRequest(100, java.util.concurrent.TimeUnit.MILLISECONDS))
    }

    @Test fun malformedTerminalEnvelopeDoesNotEstablishChallengeClosure() = runBlocking {
        for (code in listOf("signature_invalid", "challenge_not_found", "otp_invalid_or_expired")) {
            enqueue(status = 422, code = code, errors = """{"otp":[42]}""", meta = """{"retryable":false}""", header = null)
            val result = call(1)
            assertTrue(result.responseMalformed)
            assertEquals("response_parse_failed", result.code)
        }
        assertEquals(3, server.requestCount)
    }

    @Test fun supportedCanonicalUrlsAreSentUnchanged() = runBlocking {
        for (host in listOf("amaterky.sk", "www.amaterky.sk", "eurogirlsescort.com", "www.eurogirlsescort.com")) {
            enqueue(status = 422, code = "invalid_ad_url")
            val url = "https://$host/synthetic-ad"
            auth.initiateAuth(url)
            assertTrue(server.takeRequest().body.readUtf8().contains("\"ad_url\":\"$url\""))
        }
        assertTrue(key.length <= 8192)
        assertTrue(signature.length <= 4096)
    }
}
