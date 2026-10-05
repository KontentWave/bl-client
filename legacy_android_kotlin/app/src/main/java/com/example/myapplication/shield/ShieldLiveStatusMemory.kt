package com.example.myapplication.shield

internal fun interface LegacyShieldDiagnostics {
    fun clear(): Boolean
}

internal class ShieldLiveStatusOwner {
    private var memory: ShieldLiveStatusMemory? = null

    @Synchronized
    fun get(create: () -> ShieldLiveStatusMemory): ShieldLiveStatusMemory =
        memory ?: create().also { memory = it }
}

internal class ShieldLiveStatusMemory(
    private val legacyDiagnostics: LegacyShieldDiagnostics,
    private val onCleanupFailure: () -> Unit = {},
) : ShieldLiveStatusSink {
    private var cleanupConfirmed = false
    private var status = ShieldLiveStatus()

    @Synchronized
    fun initialize() {
        ensureCleanup()
    }

    @Synchronized
    fun read(): ShieldLiveStatus {
        ensureCleanup()
        return status.copy(legacyCleanupFailed = !cleanupConfirmed)
    }

    @Synchronized
    override fun record(status: ShieldLiveStatus) {
        ensureCleanup()
        // Home keeps snapshots between refresh hooks; never hand it caller details.
        this.status = ShieldLiveStatus(
            stage = status.stage,
            retryable = status.retryable,
            overlayState = status.overlayState,
            updatedAtEpochMillis = status.updatedAtEpochMillis,
        )
    }

    private fun ensureCleanup() {
        if (cleanupConfirmed) return
        cleanupConfirmed = legacyDiagnostics.clear()
        if (!cleanupConfirmed) onCleanupFailure()
    }
}
