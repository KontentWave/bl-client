package com.example.myapplication.data

import com.example.myapplication.data.remote.ApiClientFactory
import com.example.myapplication.phone.PhoneNumberVectors
import com.example.myapplication.security.CanonicalPayloadFactory
import com.example.myapplication.security.EphemeralSignedRequestFactory
import com.google.gson.Gson
import com.google.gson.JsonParser
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeNotNull
import org.junit.Test

class BackendReportInteropTest {
    @Test
    fun actualPhpBackendServicesVerifyClientWireRequestsAndRejectRawAndTamperedSignatures() = runBlocking {
        val backendRoot = System.getenv("CB02_BACKEND_ROOT")
        val scriptPath = System.getenv("CB02_INTEROP_SCRIPT")
        assumeNotNull(backendRoot, scriptPath)
        val signer = EphemeralSignedRequestFactory()
        val server = MockWebServer()
        server.start()
        val fixtures = mutableListOf<Map<String, Any>>()
        try {
            val reports = ReportRepositoryImpl(
                ApiClientFactory.createReportApi(server.url("/api/").toString()), signer, ApiClientFactory.gson(),
            )
            for (vector in PhoneNumberVectors.load().filter { it.normalized != null }) {
                server.enqueue(MockResponse().setResponseCode(422).setBody(
                    """{"success":false,"code":"duplicate_report","message":"Synthetic duplicate","errors":{},"meta":{}}""",
                ))
                val result = reports.submitReport(vector.raw, ReportingFeature.NO_SHOW) as ReportSubmissionResult.Failure
                assertEquals("duplicate_report", result.code)
                val request = JsonParser.parseString(server.takeRequest().body.readUtf8()).asJsonObject
                val hash = checkNotNull(vector.hash)
                fixtures += mapOf(
                    "raw" to vector.raw,
                    "normalized" to checkNotNull(vector.normalized),
                    "hash" to hash,
                    "request" to request,
                    "payload" to signer.reportPayloads.last(),
                    "raw_signature" to signer.createReportRequest(vector.raw, "no_show").signature,
                    "query" to signer.createBlacklistCheckRequest(hash),
                    "query_payload" to CanonicalPayloadFactory.createBlacklistCheck(hash, signer.publicKeyPem),
                )
            }
            assertEquals(fixtures.size, server.requestCount)
        } finally {
            server.shutdown()
        }

        val process = ProcessBuilder(
            "wsl.exe", "-d", System.getenv("CB02_WSL_DISTRO") ?: "Ubuntu-22.04",
            "--", "php", checkNotNull(scriptPath), checkNotNull(backendRoot),
        ).redirectErrorStream(true).start()
        try {
            process.outputStream.bufferedWriter(Charsets.UTF_8).use { it.write(Gson().toJson(fixtures)) }
            assertTrue("PHP interoperability process timed out", process.waitFor(30, TimeUnit.SECONDS))
            val output = process.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
            assertEquals(output, 0, process.exitValue())
            val totals = JsonParser.parseString(output).asJsonObject
            assertEquals(fixtures.size, totals["reports"].asInt)
            assertEquals(fixtures.size, totals["queries"].asInt)
        } finally {
            if (process.isAlive) process.destroyForcibly()
        }
    }
}
