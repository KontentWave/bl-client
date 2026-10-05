package com.example.myapplication.data

import com.example.myapplication.data.remote.AuthApi
import com.example.myapplication.data.remote.model.ApiMeta
import com.example.myapplication.data.remote.model.ApiMetaAdapter
import com.google.gson.GsonBuilder
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

class AuthRepositoryTest {
    private lateinit var server: MockWebServer
    private lateinit var repository: AuthRepository

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()

        val gson = GsonBuilder()
            .registerTypeAdapter(ApiMeta::class.java, ApiMetaAdapter())
            .create()

        val authApi = Retrofit.Builder()
            .baseUrl(server.url("/api/"))
            .addConverterFactory(GsonConverterFactory.create(gson))
            .build()
            .create(AuthApi::class.java)

        repository = AuthRepositoryImpl(authApi = authApi, gson = gson)
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun initiateAuth_returnsSuccessWithChallengeMetadata() = runBlocking {
        server.enqueue(
            MockResponse()
                .setResponseCode(201)
                .setBody(
                    """
                    {
                      "success": true,
                      "code": "auth.sms_initiated",
                      "data": {
                        "challenge_id": "0d5f35ea-331d-4df1-b9d6-3df7ab7fdc7c",
                        "masked_phone_number": "+421***456",
                        "otp_expires_at": "2026-04-01T12:15:00+00:00"
                      },
                      "meta": {}
                    }
                    """.trimIndent(),
                ),
        )

        val result = repository.initiateAuth(" https://example.com/ad/123 ")

        assertTrue(result is InitiateAuthResult.Success)
        val success = result as InitiateAuthResult.Success
        assertEquals("0d5f35ea-331d-4df1-b9d6-3df7ab7fdc7c", success.challengeId)
        assertEquals("+421***456", success.maskedPhoneNumber)
        assertEquals("2026-04-01T12:15:00+00:00", success.otpExpiresAt)

        val request = server.takeRequest()
        assertEquals("POST", request.method)
        assertEquals("/api/auth/initiate", request.path)
        assertTrue(request.body.readUtf8().contains("\"ad_url\":\"https://example.com/ad/123\""))
    }

    @Test
    fun initiateAuth_returnsFieldErrorAndRetryabilityFromErrorEnvelope() = runBlocking {
        server.enqueue(
            MockResponse()
                .setResponseCode(400)
                .setBody(
                    """
                    {
                      "success": false,
                      "code": "phone_extraction_failed",
                      "message": "The system could not extract a valid phone number from the supplied ad.",
                      "errors": {
                        "ad_url": ["The system could not extract a valid phone number from the supplied ad."]
                      },
                      "meta": {
                        "retryable": false
                      }
                    }
                    """.trimIndent(),
                ),
        )

        val result = repository.initiateAuth("https://example.com/bad-ad")

        assertTrue(result is InitiateAuthResult.Failure)
        val failure = result as InitiateAuthResult.Failure
        assertEquals("phone_extraction_failed", failure.code)
        assertEquals(
            "The system could not extract a valid phone number from the supplied ad.",
            failure.fieldErrors["ad_url"]?.first(),
        )
        assertFalse(failure.retryable)
    }

    @Test
    fun initiateAuth_returnsSuccessWhenBackendSendsMetaAsEmptyArray() = runBlocking {
        server.enqueue(
            MockResponse()
                .setResponseCode(201)
                .setBody(
                    """
                    {
                      "success": true,
                      "code": "auth.sms_initiated",
                      "data": {
                        "challenge_id": "016016f4-cd29-4841-ba24-43c830fe92a2",
                        "masked_phone_number": "+421***456",
                        "otp_expires_at": "2026-04-04T18:10:05+00:00"
                      },
                      "meta": []
                    }
                    """.trimIndent(),
                ),
        )

        val result = repository.initiateAuth("https://portal.example.test/escort/miriam")

        assertTrue(result is InitiateAuthResult.Success)
        val success = result as InitiateAuthResult.Success
        assertEquals("016016f4-cd29-4841-ba24-43c830fe92a2", success.challengeId)
        assertEquals("+421***456", success.maskedPhoneNumber)
        assertEquals("2026-04-04T18:10:05+00:00", success.otpExpiresAt)
    }

    @Test
    fun verifyAuth_returnsSuccessWithVerificationMetadata() = runBlocking {
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody(
                    """
                    {
                      "success": true,
                      "code": "auth.verified",
                      "data": {
                        "challenge_id": "0d5f35ea-331d-4df1-b9d6-3df7ab7fdc7c",
                        "masked_phone_number": "+421***456",
                        "verified_at": "2026-03-31T12:10:00+00:00"
                      },
                      "meta": {}
                    }
                    """.trimIndent(),
                ),
        )

        val result = repository.verifyAuth(
            challengeId = "0d5f35ea-331d-4df1-b9d6-3df7ab7fdc7c",
            otp = "123456",
            publicKey = "-----BEGIN PUBLIC KEY-----\nabc\n-----END PUBLIC KEY-----",
            signature = "base64-signature",
        )

        assertTrue(result is VerifyAuthResult.Success)
        val success = result as VerifyAuthResult.Success
        assertEquals("0d5f35ea-331d-4df1-b9d6-3df7ab7fdc7c", success.challengeId)
        assertEquals("+421***456", success.maskedPhoneNumber)
        assertEquals("2026-03-31T12:10:00+00:00", success.verifiedAt)

        val request = server.takeRequest()
        assertEquals("POST", request.method)
        assertEquals("/api/auth/verify", request.path)
        val body = request.body.readUtf8()
        assertTrue(body.contains("\"challenge_id\":\"0d5f35ea-331d-4df1-b9d6-3df7ab7fdc7c\""))
        assertTrue(body.contains("\"otp\":\"123456\""))
        assertTrue(body.contains("\"signature\":\"base64-signature\""))
    }

    @Test
    fun verifyAuth_preservesCombinedOtpErrorAndNonRetryableRequestMetadata() = runBlocking {
        server.enqueue(
            MockResponse()
                .setResponseCode(422)
                .setBody(
                    """
                    {
                      "success": false,
                      "code": "otp_invalid_or_expired",
                      "message": "The provided OTP is invalid or expired.",
                      "errors": {
                        "otp": ["The provided OTP is invalid or expired."]
                      },
                      "meta": {
                        "retryable": false
                      }
                    }
                    """.trimIndent(),
                ),
        )

        val result = repository.verifyAuth(
            challengeId = "0d5f35ea-331d-4df1-b9d6-3df7ab7fdc7c",
            otp = "654321",
            publicKey = "-----BEGIN PUBLIC KEY-----\nabc\n-----END PUBLIC KEY-----",
            signature = "base64-signature",
        )

        assertTrue(result is VerifyAuthResult.Failure)
        val failure = result as VerifyAuthResult.Failure
        assertEquals("otp_invalid_or_expired", failure.code)
        assertEquals("The provided OTP is invalid or expired.", failure.fieldErrors["otp"]?.first())
        assertFalse(failure.retryable)
    }
}
