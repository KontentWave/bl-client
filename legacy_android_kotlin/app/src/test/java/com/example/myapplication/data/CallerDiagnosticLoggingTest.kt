package com.example.myapplication.data

import com.example.myapplication.data.remote.ApiClientFactory
import com.example.myapplication.data.remote.model.CheckBlacklistRequest
import java.util.concurrent.CopyOnWriteArrayList
import java.util.logging.Handler
import java.util.logging.Level
import java.util.logging.LogRecord
import java.util.logging.Logger
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CallerDiagnosticLoggingTest {
    @Test
    fun releaseTransportOmitsHttpDiagnosticsWithoutChangingRequestPolicy() {
        val client = ApiClientFactory.createHttpClient(diagnosticsEnabled = false)
        assertTrue(client.interceptors.none { it is HttpLoggingInterceptor })
        assertEquals(false, client.retryOnConnectionFailure)
        assertEquals(false, client.followRedirects)
        assertEquals(false, client.followSslRedirects)
        assertEquals(15000, client.connectTimeoutMillis)
        assertEquals(20000, client.readTimeoutMillis)
        assertEquals(20000, client.writeTimeoutMillis)
        assertEquals(1, client.networkInterceptors.size)
    }

    @Test
    fun debugHttpDiagnosticsRemainOptInAndQueryAlwaysOmitsThem() {
        val debugClient = ApiClientFactory.createHttpClient(diagnosticsEnabled = true)
        assertEquals(
            HttpLoggingInterceptor.Level.BASIC,
            debugClient.interceptors.filterIsInstance<HttpLoggingInterceptor>().single().level,
        )
        for (debug in listOf(false, true)) {
            val queryClient = ApiClientFactory.createHttpClient(logHttp = false, diagnosticsEnabled = debug)
            assertTrue(queryClient.interceptors.none { it is HttpLoggingInterceptor })
        }
    }

    @Test
    fun queryHttpDoesNotLogBackendReasonOrCallerPayload() = runBlocking {
        val messages = CopyOnWriteArrayList<String>()
        val logger = Logger.getLogger(OkHttpClient::class.java.name)
        val oldLevel = logger.level
        val handler = object : Handler() {
            override fun publish(record: LogRecord) { messages += record.message }
            override fun flush() = Unit
            override fun close() = Unit
        }
        logger.level = Level.ALL
        logger.addHandler(handler)
        try {
            MockWebServer().use { server ->
                server.enqueue(
                    MockResponse()
                        .setStatus("HTTP/1.1 500 synthetic-private-caller-reason")
                        .setBody("""{"message":"synthetic-private-caller-message"}"""),
                )
                val response = ApiClientFactory.createBlacklistApi(server.url("/api/").toString())
                    .checkBlacklist(CheckBlacklistRequest("synthetic hash", "synthetic public key", "synthetic signature"))
                assertEquals(500, response.code())
                assertEquals(1, server.requestCount)
                assertTrue("Caller lookup HTTP diagnostics must be disabled: $messages", messages.isEmpty())
            }
        } finally {
            logger.removeHandler(handler)
            logger.level = oldLevel
        }
    }
}
