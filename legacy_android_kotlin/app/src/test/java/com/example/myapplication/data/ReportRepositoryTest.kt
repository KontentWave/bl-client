package com.example.myapplication.data

import com.example.myapplication.data.remote.ReportApi
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

class ReportRepositoryTest {
    private lateinit var server: MockWebServer
    private lateinit var repository: ReportRepository

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()

        val gson = GsonBuilder()
            .registerTypeAdapter(ApiMeta::class.java, ApiMetaAdapter())
            .create()

        val reportApi = Retrofit.Builder()
            .baseUrl(server.url("/api/"))
            .addConverterFactory(GsonConverterFactory.create(gson))
            .build()
            .create(ReportApi::class.java)

        repository = ReportRepositoryImpl(
            reportApi = reportApi,
            signedRequestFactory = object : SignedRequestFactory {
                override fun createVerifyRequest(challengeId: String): SignedRequestPayload =
                    error("Not used in report repository tests")

                override fun createReportRequest(clientPhoneNumber: String, feature: String): SignedRequestPayload {
                    assertEquals("+421900123456", clientPhoneNumber)
                    assertEquals("no_show", feature)
                    return SignedRequestPayload(
                        publicKey = "-----BEGIN PUBLIC KEY-----\nabc\n-----END PUBLIC KEY-----",
                        signature = "base64-signature",
                    )
                }

                override fun createBlacklistCheckRequest(targetHash: String): SignedRequestPayload =
                    error("Not used in report repository tests")
            },
            gson = gson,
        )
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun submitReport_returnsSuccessWithLevelMetadata() = runBlocking {
        server.enqueue(
            MockResponse()
                .setResponseCode(201)
                .setBody(
                    """
                    {
                      "success": true,
                      "code": "report.created",
                      "data": {
                        "client_hash": "client-hash-123",
                        "reporter_hash": "reporter-hash-456",
                        "feature": "no_show",
                        "feature_label": "No-Show",
                        "unique_reporter_count": 1,
                        "level": "level_1",
                        "ready_for_sync": false
                      },
                      "meta": {}
                    }
                    """.trimIndent(),
                ),
        )

        val result = repository.submitReport(
            clientPhoneNumber = " +421900123456 ",
            feature = ReportingFeature.NO_SHOW,
        )

        assertTrue(result is ReportSubmissionResult.Success)
        val success = result as ReportSubmissionResult.Success
        assertEquals("client-hash-123", success.clientHash)
        assertEquals("reporter-hash-456", success.reporterHash)
        assertEquals("No-Show", success.featureLabel)
        assertEquals(1, success.uniqueReporterCount)
        assertEquals("level_1", success.level)
        assertFalse(success.readyForSync)

        val request = server.takeRequest()
        assertEquals("POST", request.method)
        assertEquals("/api/reports", request.path)
        val body = request.body.readUtf8()
        assertTrue(body.contains("\"client_phone_number\":\"+421900123456\""))
        assertTrue(body.contains("\"feature\":\"no_show\""))
        assertTrue(body.contains("\"signature\":\"base64-signature\""))
    }

    @Test
    fun submitReport_returnsDuplicateFailureFromErrorEnvelope() = runBlocking {
        server.enqueue(
            MockResponse()
                .setResponseCode(422)
                .setBody(
                    """
                    {
                      "success": false,
                      "code": "duplicate_report",
                      "message": "You have already reported this client for this feature.",
                      "errors": {
                        "feature": ["You have already reported this client for this feature."]
                      },
                      "meta": {
                        "retryable": false
                      }
                    }
                    """.trimIndent(),
                ),
        )

        val result = repository.submitReport(
            clientPhoneNumber = "+421900123456",
            feature = ReportingFeature.NO_SHOW,
        )

        assertTrue(result is ReportSubmissionResult.Failure)
        val failure = result as ReportSubmissionResult.Failure
        assertEquals("duplicate_report", failure.code)
        assertEquals("You have already reported this client for this feature.", failure.message)
        assertEquals(
            "You have already reported this client for this feature.",
            failure.fieldErrors["feature"]?.first(),
        )
        assertFalse(failure.retryable)
    }
}

