package com.example.myapplication.ui

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** Retained by its activity-owned ViewModel; no callbacks here ever submit a request. */
class RetryCooldown(
    private val scope: CoroutineScope,
    private val nowMillis: () -> Long = { System.nanoTime() / 1_000_000 },
    private val onTick: (Long) -> Unit,
) {
    private var deadline = Long.MIN_VALUE
    private var ticker: Job? = null

    val remainingSeconds: Long
        get() = if (deadline <= nowMillis()) 0 else (deadline - nowMillis() + 999) / 1000

    fun extend(seconds: Long?) {
        if (seconds == null) return
        deadline = maxOf(deadline, nowMillis() + seconds.coerceIn(1, 86_400) * 1000)
        onTick(remainingSeconds)
        ticker?.cancel()
        ticker = scope.launch {
            while (remainingSeconds > 0) {
                delay(250)
                onTick(remainingSeconds)
            }
        }
    }
}
