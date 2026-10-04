package com.example.myapplication.data

import com.example.myapplication.data.remote.ApiClientFactory
import com.example.myapplication.phone.PhoneNumberVectors
import com.example.myapplication.security.CanonicalPayloadFactory
import com.example.myapplication.security.EphemeralSignedRequestFactory
import com.google.gson.JsonParser
import java.security.Signature
import java.util.Base64
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class ReportNormalizationSigningTest {
    private val server = MockWebServer()
    private val signer = EphemeralSignedRequestFactory()
    private lateinit var reports: ReportRepository

    @Before fun setup() {
        server.start()
        reports = ReportRepositoryImpl(
            ApiClientFactory.createReportApi(server.url("/api/").toString()),
            signer,
            ApiClientFactory.gson(),
        )
    }

    @After fun teardown() { server.shutdown() }

    @Test
    fun equivalentFormatsSignAndTransmitIdenticalCanonicalNumbersWithRealSignatures() = runBlocking {
        val vectors = PhoneNumberVectors.load().filter { it.normalized != null }
        for (vector in vectors) {
            server.enqueue(MockResponse().setResponseCode(201).setBody(
                """{"success":true,"code":"report.created","data":{"client_hash":"${vector.hash}","reporter_hash":"synthetic","feature":"no_show","feature_label":"No-Show","unique_reporter_count":1,"level":"level_1","ready_for_sync":false},"meta":{}}""",
            ))
            val result = reports.submitReport(vector.raw, ReportingFeature.NO_SHOW) as ReportSubmissionResult.Success
            assertEquals(vector.hash, result.clientHash)
            assertEquals(1, result.uniqueReporterCount)
            assertFalse(result.readyForSync)
            val request = server.takeRequest()
            assertEquals("/api/reports", request.path)
            val body = JsonParser.parseString(request.body.readUtf8()).asJsonObject
            assertEquals(vector.normalized, body["client_phone_number"].asString)
            assertEquals("no_show", body["feature"].asString)
            assertEquals(signer.publicKeyPem, body["public_key"].asString)
            val payload = CanonicalPayloadFactory.createReport(
                body["client_phone_number"].asString, body["feature"].asString, body["public_key"].asString,
            )
            assertEquals(payload, signer.reportPayloads.last())
            val verifier = Signature.getInstance("SHA256withECDSA")
            verifier.initVerify(signer.keyPair.public)
            verifier.update(payload.toByteArray(Charsets.UTF_8))
            assertTrue(verifier.verify(Base64.getDecoder().decode(body["signature"].asString)))
        }
        assertEquals(vectors.size, server.requestCount)
        assertEquals(2, signer.reportPayloads.distinct().size)
    }

    @Test
    fun unsupportedInputNeverSignsOrSendsARequest() = runBlocking {
        for (vector in PhoneNumberVectors.load().filter { it.normalized == null }) {
            val result = reports.submitReport(vector.raw, ReportingFeature.NO_SHOW) as ReportSubmissionResult.Failure
            assertEquals("client_phone_number_invalid", result.code)
            assertFalse(result.retryable)
            assertFalse(result.fieldErrors["client_phone_number"].isNullOrEmpty())
        }
        assertTrue(signer.reportPayloads.isEmpty())
        assertEquals(0, server.requestCount)
    }

    @Test
    fun normalizedSignedReportPreservesTemporaryFailureWithoutAutomaticReplay() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(503).addHeader("Retry-After", "0").setBody(
            """{"success":false,"code":"abuse_protection_unavailable","message":"Unavailable","errors":[],"meta":{"retryable":true}}""",
        ))
        server.enqueue(MockResponse().setResponseCode(201))
        val result = reports.submitReport("0900 000 001", ReportingFeature.NO_SHOW) as ReportSubmissionResult.Failure
        assertEquals("abuse_protection_unavailable", result.code)
        assertTrue(result.retryable)
        assertEquals(1, server.requestCount)
        assertEquals(1, signer.reportPayloads.size)
    }
}
