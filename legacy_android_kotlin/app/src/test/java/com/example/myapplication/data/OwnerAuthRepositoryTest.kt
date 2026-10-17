package com.example.myapplication.data

import com.example.myapplication.data.remote.ApiClientFactory
import com.example.myapplication.data.remote.AuthApi
import com.google.gson.JsonParser
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.SocketPolicy
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

/** All HTTP is loopback MockWebServer. No provider, real recipient or signing operation. */
class OwnerAuthRepositoryTest {
    private lateinit var server: MockWebServer
    private lateinit var repository: AuthRepository
    private val phone = "+12345678" // synthetic minimum-length contract fixture
    private val success = """{"success":true,"code":"auth.sms_initiated","data":{"challenge_id":"0d5f35ea-331d-4df1-b9d6-3df7ab7fdc7c","masked_phone_number":"masked","otp_expires_at":"2030-01-01T00:15:00Z"},"meta":{}}"""

    @Before fun setup() {
        server = MockWebServer().also { it.start() }
        val gson = ApiClientFactory.gson()
        val client = ApiClientFactory.createHttpClient(logHttp = false).newBuilder()
            .readTimeout(200, TimeUnit.MILLISECONDS).build()
        repository = AuthRepositoryImpl(
            Retrofit.Builder().baseUrl(server.url("/api/"))
                .client(client).addConverterFactory(GsonConverterFactory.create(gson))
                .build().create(AuthApi::class.java), gson,
        )
    }

    @After fun teardown() { server.shutdown() }

    private fun error(status: Int, code: String, errors: String = "{}", meta: String = "{\"retryable\":false}") {
        server.enqueue(MockResponse().setResponseCode(status).setBody(
            """{"success":false,"code":"$code","message":"Synthetic failure","errors":$errors,"meta":$meta}""",
        ))
    }

    @Test fun owner201SendsOnlyPhoneToRelativeEndpointWithJsonHeaders() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(201).setBody(success))
        val result = repository.initiateOwnerAuth(phone) as InitiateAuthResult.Success
        assertEquals("masked", result.maskedPhoneNumber)
        assertEquals("2030-01-01T00:15:00Z", result.otpExpiresAt)
        val request = server.takeRequest()
        assertEquals("POST", request.method)
        assertEquals("/api/auth/owner/initiate", request.path)
        assertEquals("application/json", request.getHeader("Accept"))
        assertTrue(request.getHeader("Content-Type")!!.startsWith("application/json"))
        val json = JsonParser.parseString(request.body.readUtf8()).asJsonObject
        assertEquals(setOf("phone_number"), json.keySet())
        assertEquals(phone, json["phone_number"].asString)
        assertEquals(1, server.requestCount)
    }

    @Test fun normalAdRouteAndBodyArePreserved() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(201).setBody(success))
        assertTrue(repository.initiateAuth(" https://portal.example.test/ad ") is InitiateAuthResult.Success)
        val request = server.takeRequest()
        assertEquals("/api/auth/initiate", request.path)
        val json = JsonParser.parseString(request.body.readUtf8()).asJsonObject
        assertEquals(setOf("ad_url"), json.keySet())
        assertEquals("https://portal.example.test/ad", json["ad_url"].asString)
    }

    @Test fun strictPhoneValidationDoesNotGuessNormalizeTruncateOrSend() = runBlocking {
        for (invalid in listOf("", "12345678", "+01234567", "+1234567", "+1234567890123456", " $phone", "$phone ", "+1234 5678", "0012345678", "+１２３４５６７８", "$phone\n")) {
            val result = repository.initiateOwnerAuth(invalid) as InitiateAuthResult.Failure
            assertEquals("validation_failed", result.code)
            assertNotNull(result.fieldErrors["phone_number"])
            assertFalse(result.retryable)
        }
        assertEquals(0, server.requestCount)
    }

    @Test fun acceptsMaximumCanonicalLengthUnmodified() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(201).setBody(success))
        val maximum = "+123456789012345"
        assertTrue(repository.initiateOwnerAuth(maximum) is InitiateAuthResult.Success)
        assertEquals(maximum, JsonParser.parseString(server.takeRequest().body.readUtf8()).asJsonObject["phone_number"].asString)
    }

    @Test fun disabled403AndMisconfigured503AreUnavailableWithoutRetryOrFallback() = runBlocking {
        for (status in listOf(403, 503)) {
            error(status, "owner_enrollment_unavailable")
            val result = repository.initiateOwnerAuth(phone) as InitiateAuthResult.Failure
            assertEquals("owner_enrollment_unavailable", result.code)
            assertFalse(result.retryable)
            assertFalse(result.responseMalformed)
            assertEquals("/api/auth/owner/initiate", server.takeRequest().path)
        }
        assertEquals(2, server.requestCount)
    }

    @Test fun validationAndSharedSmsProtectionErrorsKeepTheirSemantics() = runBlocking {
        error(422, "validation_failed", "{\"phone_number\":[\"Invalid synthetic phone\"]}")
        val invalid = repository.initiateOwnerAuth(phone) as InitiateAuthResult.Failure
        assertEquals(listOf("Invalid synthetic phone"), invalid.fieldErrors["phone_number"])
        for (code in listOf("abuse_protection_unavailable", "sms_dispatch_failed")) {
            error(503, code, meta = "{\"retryable\":true}")
            val result = repository.initiateOwnerAuth(phone) as InitiateAuthResult.Failure
            assertEquals(code, result.code)
            assertTrue(result.retryable)
        }
        assertEquals(3, server.requestCount)
    }

    @Test fun rateLimitPreservesRetryDelayWithoutReplay() = runBlocking {
        error(429, "rate_limited", meta = "{\"retryable\":true,\"retry_after\":7}")
        val result = repository.initiateOwnerAuth(phone) as InitiateAuthResult.Failure
        assertEquals("rate_limited", result.code)
        assertEquals(7L, result.retryAfterSeconds)
        assertTrue(result.retryable)
        assertEquals(1, server.requestCount)
    }

    @Test fun hostingHtml403IsNotOwnerIneligibility() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(403).setBody("<html>Forbidden</html>"))
        val result = repository.initiateOwnerAuth(phone) as InitiateAuthResult.Failure
        assertEquals("response_parse_failed", result.code)
        assertTrue(result.responseMalformed)
        assertFalse(result.message.contains(phone))
        assertEquals(1, server.requestCount)
    }

    @Test fun timeoutHasUnknownAcceptanceWithoutAutomaticResendOrAdFallback() = runBlocking {
        server.enqueue(MockResponse().setSocketPolicy(SocketPolicy.NO_RESPONSE))
        val result = repository.initiateOwnerAuth(phone) as InitiateAuthResult.Failure
        assertEquals("network_unavailable", result.code)
        assertTrue(result.message.contains("unknown"))
        assertEquals("/api/auth/owner/initiate", server.takeRequest().path)
        assertNull(server.takeRequest(300, TimeUnit.MILLISECONDS))
        assertEquals(1, server.requestCount)
    }

    @Test fun retryAfterZero503IsNotAutomaticallyReplayed() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(503).addHeader("Retry-After", "0").setBody(
            """{"success":false,"code":"sms_dispatch_failed","message":"Unknown","errors":{},"meta":{"retryable":true}}""",
        ))
        server.enqueue(MockResponse().setResponseCode(201).setBody(success))
        assertEquals("sms_dispatch_failed", (repository.initiateOwnerAuth(phone) as InitiateAuthResult.Failure).code)
        assertEquals(1, server.requestCount)
        assertTrue(repository.initiateOwnerAuth(phone) is InitiateAuthResult.Success) // explicit second command only
        assertEquals(2, server.requestCount)
    }

    @Test fun malformedOrWrongSuccessNeverProducesPendingEnrollment() = runBlocking {
        for ((status, body) in listOf(200 to success, 201 to success.replace("auth.sms_initiated", "unexpected"), 201 to "{broken", 201 to "{}")) {
            server.enqueue(MockResponse().setResponseCode(status).setBody(body))
            assertTrue(repository.initiateOwnerAuth(phone) is InitiateAuthResult.Failure)
        }
        assertEquals(4, server.requestCount)
    }

    @Test fun ownerChallengeUsesTheSameVerifyWireContract() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(201).setBody(success))
        val challenge = repository.initiateOwnerAuth(phone) as InitiateAuthResult.Success
        server.enqueue(MockResponse().setResponseCode(200).setBody(
            """{"success":true,"code":"auth.verified","data":{"challenge_id":"${challenge.challengeId}","masked_phone_number":"masked","verified_at":"2030-01-01T00:00:00Z"},"meta":{}}""",
        ))
        assertTrue(repository.verifyAuth(challenge.challengeId, "000000", "synthetic-public-key", "synthetic-signature") is VerifyAuthResult.Success)
        server.takeRequest()
        val request = server.takeRequest()
        assertEquals("/api/auth/verify", request.path)
        val json = JsonParser.parseString(request.body.readUtf8()).asJsonObject
        assertEquals(setOf("challenge_id", "otp", "public_key", "signature"), json.keySet())
        assertEquals(challenge.challengeId, json["challenge_id"].asString)
        assertEquals("000000", json["otp"].asString)
        assertEquals("synthetic-public-key", json["public_key"].asString)
        assertEquals("synthetic-signature", json["signature"].asString)
    }

    @Test fun verificationRequiresTheExistingAuthVerifiedCodeAndStatus() = runBlocking {
        for ((status, code) in listOf(200 to "auth.sms_initiated", 201 to "auth.verified")) {
            server.enqueue(MockResponse().setResponseCode(status).setBody(
                """{"success":true,"code":"$code","data":{"challenge_id":"synthetic","masked_phone_number":"masked","verified_at":"2030-01-01T00:00:00Z"},"meta":{}}""",
            ))
            assertTrue(repository.verifyAuth("synthetic", "000000", "synthetic-public", "synthetic-signature") is VerifyAuthResult.Failure)
        }
    }
}
