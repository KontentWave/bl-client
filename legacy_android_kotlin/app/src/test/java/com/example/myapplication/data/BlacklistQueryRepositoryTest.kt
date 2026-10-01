package com.example.myapplication.data

import com.example.myapplication.data.remote.BlacklistApi
import com.example.myapplication.data.remote.model.ApiMeta
import com.example.myapplication.data.remote.model.ApiMetaAdapter
import com.example.myapplication.security.SignedRequestFactory
import com.example.myapplication.security.SignedRequestPayload
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

class BlacklistQueryRepositoryTest {
    private lateinit var server: MockWebServer
    private lateinit var repository: BlacklistQueryRepository

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()

        val gson = GsonBuilder()
            .registerTypeAdapter(ApiMeta::class.java, ApiMetaAdapter())
            .create()

        val blacklistApi = Retrofit.Builder()
            .baseUrl(server.url("/api/"))
            .addConverterFactory(GsonConverterFactory.create(gson))
            .build()
            .create(BlacklistApi::class.java)

        repository = BlacklistQueryRepositoryImpl(
            blacklistApi = blacklistApi,
            signedRequestFactory = object : SignedRequestFactory {
                override fun createVerifyRequest(challengeId: String): SignedRequestPayload =
                    error("Not used in blacklist query repository tests")

                override fun createReportRequest(clientPhoneNumber: String, feature: String): SignedRequestPayload =
                    error("Not used in blacklist query repository tests")

                override fun createBlacklistCheckRequest(targetHash: String): SignedRequestPayload {
                    assertEquals(
                        "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef",
                        targetHash,
                    )
                    return SignedRequestPayload(
                        publicKey = "-----BEGIN PUBLIC KEY-----\nabc\n-----END PUBLIC KEY-----",
                        signature = "base64-signature",
                    )
                }
            },
            gson = gson,
        )
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun checkTargetHash_returnsSuccessWithFeatureList() = runBlocking {
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody(
                    """
                    {
                      "success": true,
                      "code": "blacklist.checked",
                      "data": {
                        "target_hash": "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef",
                        "features": ["Aggressive", "No-Show"]
                      },
                      "meta": {}
                    }
                    """.trimIndent(),
                ),
        )

        val result = repository.checkTargetHash(
            "0123456789ABCDEF0123456789ABCDEF0123456789ABCDEF0123456789ABCDEF",
        )

        assertTrue(result is BlacklistQueryResult.Success)
        val success = result as BlacklistQueryResult.Success
        assertEquals(
            "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef",
            success.targetHash,
        )
        assertEquals(listOf("Aggressive", "No-Show"), success.features)

        val request = server.takeRequest()
        assertEquals("POST", request.method)
        assertEquals("/api/blacklist/check", request.path)
        val body = request.body.readUtf8()
        assertTrue(body.contains("\"target_hash\":\"0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef\""))
        assertTrue(body.contains("\"signature\":\"base64-signature\""))
    }

    @Test
    fun checkTargetHash_returnsUnauthorizedFailureFromErrorEnvelope() = runBlocking {
        server.enqueue(
            MockResponse()
                .setResponseCode(401)
                .setBody(
                    """
                    {
                      "success": false,
                      "code": "blacklist_query_unauthorized",
                      "message": "The request is missing or has an invalid hardware-bound authorization signature.",
                      "errors": {},
                      "meta": {
                        "retryable": false
                      }
                    }
                    """.trimIndent(),
                ),
        )

        val result = repository.checkTargetHash(
            "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef",
        )

        assertTrue(result is BlacklistQueryResult.Failure)
        val failure = result as BlacklistQueryResult.Failure
        assertEquals("blacklist_query_unauthorized", failure.code)
        assertEquals(
            "The request is missing or has an invalid hardware-bound authorization signature.",
            failure.message,
        )
        assertFalse(failure.retryable)
    }
}

