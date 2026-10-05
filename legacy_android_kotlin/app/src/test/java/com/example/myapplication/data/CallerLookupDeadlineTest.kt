package com.example.myapplication.data

import com.example.myapplication.data.remote.ApiClientFactory
import com.example.myapplication.data.remote.BlacklistApi
import com.example.myapplication.security.SignedRequestFactory
import com.example.myapplication.security.SignedRequestPayload
import com.example.myapplication.shield.IncomingCallCoordinator
import com.example.myapplication.shield.IncomingCallProcessor
import com.example.myapplication.shield.IncomingCallState
import com.example.myapplication.shield.ShieldLiveStage
import com.example.myapplication.shield.ShieldLiveStatus
import com.example.myapplication.shield.ShieldLiveStatusSink
import com.example.myapplication.shield.ShieldOverlayPresentation
import com.example.myapplication.shield.ShieldOverlayState
import com.example.myapplication.shield.ShieldWarningPresenter
import com.example.myapplication.shield.finishBroadcastWhenComplete
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.withTimeout
import okhttp3.Call
import okhttp3.EventListener
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.SocketPolicy
import org.junit.Assert.*
import org.junit.Test
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

class CallerLookupDeadlineTest {
    @Test
    fun slowLocalApiIsCancelledByOverallBudgetWithoutNoMatchOrRetry() = runBlocking {
        val server = MockWebServer()
        val workers = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        server.start()
        try {
            server.enqueue(MockResponse().setSocketPolicy(SocketPolicy.NO_RESPONSE))
            val repository = repository(ApiClientFactory.createBlacklistApi(server.url("/api/").toString()))
            val statuses = mutableListOf<ShieldLiveStatus>()
            var finishes = 0
            val coordinator = IncomingCallCoordinator(
                scope = this,
                workerScope = workers,
                lookup = { IncomingCallProcessor(repository).lookup(it) },
                statusSink = ShieldLiveStatusSink { statuses += it },
                presenter = object : ShieldWarningPresenter {
                    override fun showWarning(normalizedNumber: String, features: List<String>, targetHash: String?): ShieldOverlayPresentation =
                        error("A timed-out lookup must not show a warning")
                    override fun dismissWarning() = ShieldOverlayPresentation(ShieldOverlayState.None)
                },
                elapsedMillis = { TimeUnit.NANOSECONDS.toMillis(System.nanoTime()) },
            )
            val started = System.nanoTime()
            val work = coordinator.onPhoneState(IncomingCallState.Ringing, "+421900000001")
            finishBroadcastWhenComplete(work) { finishes++ }
            withTimeout(8_000) { requireNotNull(work).join() }
            val elapsed = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - started)
            assertEquals(ShieldLiveStage.QueryFailed, statuses.last().stage)
            assertTrue(statuses.last().errorMessage.orEmpty().contains("not checked"))
            assertEquals(1, finishes)
            assertTrue("The actual 5s policy must not wait for the 20s read timeout: $elapsed", elapsed < 8_000)
            assertEquals(1, server.requestCount)
        } finally {
            workers.cancel()
            server.shutdown()
        }
    }

    @Test
    fun retrofitCoroutineCancellationCancelsTheActualOkHttpCall() = runBlocking {
        val server = MockWebServer()
        val cancelled = CountDownLatch(1)
        val client = OkHttpClient.Builder()
            .retryOnConnectionFailure(false)
            .eventListener(object : EventListener() {
                override fun canceled(call: Call) { cancelled.countDown() }
            })
            .build()
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        server.start()
        try {
            server.enqueue(MockResponse().setSocketPolicy(SocketPolicy.NO_RESPONSE))
            val api = Retrofit.Builder()
                .baseUrl(server.url("/api/"))
                .client(client)
                .addConverterFactory(GsonConverterFactory.create(ApiClientFactory.gson()))
                .build()
                .create(BlacklistApi::class.java)
            val work = scope.async { repository(api).checkTargetHash(HASH) }
            assertNotNull(server.takeRequest(3, TimeUnit.SECONDS))
            work.cancel()
            try {
                work.await()
                fail("Cancellation must propagate")
            } catch (_: CancellationException) {
                assertTrue(cancelled.await(3, TimeUnit.SECONDS))
            }
            assertEquals(1, server.requestCount)
        } finally {
            scope.cancel()
            client.connectionPool.evictAll()
            client.dispatcher.executorService.shutdown()
            server.shutdown()
        }
    }

    @Test
    fun cancelledSigningPreparationIsInterruptedAndDoesNotSend() = runBlocking {
        val server = MockWebServer()
        val started = CountDownLatch(1)
        val interrupted = CountDownLatch(1)
        val signed = AtomicBoolean(false)
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        server.start()
        try {
            val factory = object : SyntheticSignedRequestFactory() {
                override fun createBlacklistCheckRequest(targetHash: String): SignedRequestPayload {
                    started.countDown()
                    try {
                        CountDownLatch(1).await()
                    } catch (exception: InterruptedException) {
                        interrupted.countDown()
                        throw exception
                    }
                    signed.set(true)
                    return super.createBlacklistCheckRequest(targetHash)
                }
            }
            val work = scope.async {
                repository(ApiClientFactory.createBlacklistApi(server.url("/api/").toString()), factory)
                    .checkTargetHash(HASH)
            }
            assertTrue(started.await(3, TimeUnit.SECONDS))
            work.cancel()
            try {
                work.await()
                fail("Preparation cancellation must propagate")
            } catch (_: CancellationException) {
                assertTrue(interrupted.await(3, TimeUnit.SECONDS))
            }
            assertFalse(signed.get())
            assertEquals(0, server.requestCount)
        } finally {
            scope.cancel()
            server.shutdown()
        }
    }

    @Test
    fun unreachableLoopbackApiFailsWithoutNoMatchOrReplay() = runBlocking {
        val server = MockWebServer()
        server.start()
        val baseUrl = server.url("/api/").toString()
        server.shutdown()
        val result = withTimeoutOrNull(1_000) { repository(ApiClientFactory.createBlacklistApi(baseUrl)).checkTargetHash(HASH) }
        assertTrue(result is BlacklistQueryResult.Failure)
        assertEquals("network_unavailable", (result as BlacklistQueryResult.Failure).code)
    }

    private fun repository(
        api: BlacklistApi,
        factory: SignedRequestFactory = SyntheticSignedRequestFactory(),
    ) = BlacklistQueryRepositoryImpl(api, factory, ApiClientFactory.gson())

    private open class SyntheticSignedRequestFactory : SignedRequestFactory {
        override fun createVerifyRequest(challengeId: String): SignedRequestPayload = error("No OTP work")
        override fun createReportRequest(clientPhoneNumber: String, feature: String): SignedRequestPayload = error("No report work")
        override fun createBlacklistCheckRequest(targetHash: String) = SignedRequestPayload(
            publicKey = "synthetic-public-only-fixture",
            signature = "synthetic-signature",
        )
    }

    companion object {
        private const val HASH = "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef"
    }
}
