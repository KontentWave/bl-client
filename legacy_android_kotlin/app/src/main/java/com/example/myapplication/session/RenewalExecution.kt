package com.example.myapplication.session

import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class RenewalExecution(
    private val recovery: SessionRecovery,
    private val coordinator: RenewalCoordinator,
    private val scope: CoroutineScope,
    private val scheduleBackground: (Long?) -> Boolean,
    private val networkAvailable: () -> Boolean,
    private val nowMillis: () -> Long = System::currentTimeMillis,
) {
    private val running = AtomicBoolean()
    private var foreground = false
    private var timer: Job? = null
    private var scheduledAt: Long? = null
    private var initialized = false

    init { scope.launch { recovery.state.collect { reschedule() } } }

    @Synchronized
    fun foreground(active: Boolean) {
        foreground = active
        if (active) coordinator.retryStorageOnForeground()
        reschedule(forceTimer = true)
    }

    @Synchronized
    fun networkChanged() { reschedule(forceTimer = true) }

    @Synchronized
    private fun reschedule(forceTimer: Boolean = false) {
        if (running.get()) return
        val eligible = coordinator.nextEligibleAt()?.takeUnless { it == Long.MAX_VALUE }
        if (!initialized || eligible != scheduledAt) {
            initialized = true
            scheduledAt = eligible
            if (!scheduleBackground(eligible)) {
                recovery.ticket()?.let { recovery.renewalMessage(it,
                    "Android could not schedule background renewal. Foreground catch-up remains available; no SMS was sent.") }
            }
            timer?.cancel()
            timer = null
        }
        if (!foreground || eligible == null || !networkAvailable()) {
            timer?.cancel()
            timer = null
        } else if (timer == null || forceTimer) {
            timer?.cancel()
            timer = scope.launch {
                delay((eligible - nowMillis()).coerceAtLeast(0))
                execute()
            }
        }
    }

    fun execute(finished: () -> Unit = {}): Job? {
        if (!running.compareAndSet(false, true)) return null
        val job = scope.launch {
            try {
                if (networkAvailable()) coordinator.runEligible()
            } finally {
                running.set(false)
                synchronized(this@RenewalExecution) {
                    initialized = false
                    timer = null
                    reschedule()
                }
                finished()
            }
        }
        job.invokeOnCompletion { if (job.isCancelled) running.set(false) }
        return job
    }
}
