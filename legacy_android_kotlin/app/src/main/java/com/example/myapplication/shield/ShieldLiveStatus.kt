package com.example.myapplication.shield

import android.content.Context
import android.util.Log

data class ShieldLiveStatus(
    val stage: ShieldLiveStage = ShieldLiveStage.Idle,
    val rawIncomingNumber: String? = null,
    val normalizedNumber: String? = null,
    val targetHash: String? = null,
    val features: List<String> = emptyList(),
    val errorMessage: String? = null,
    val retryable: Boolean = false,
    val overlayState: ShieldOverlayState = ShieldOverlayState.None,
    val overlayMessage: String? = null,
    val updatedAtEpochMillis: Long? = null,
    val legacyCleanupFailed: Boolean = false,
)

enum class ShieldLiveStage {
    Idle,
    RingingDetected,
    MissingIncomingNumber,
    NormalizationFailed,
    Querying,
    NoMatch,
    MatchFound,
    QueryFailed,
}

enum class ShieldOverlayState {
    None,
    Shown,
    Dismissed,
    SkippedPermission,
    Failed,
}

data class ShieldOverlayPresentation(
    val state: ShieldOverlayState,
    val message: String? = null,
)

fun interface ShieldLiveStatusSink {
    fun record(status: ShieldLiveStatus)
}

class ShieldLiveStatusStore internal constructor(
    private val memory: ShieldLiveStatusMemory,
) : ShieldLiveStatusSink {
    constructor(context: Context) : this(
        processOwner.get {
            val appContext = context.applicationContext
            ShieldLiveStatusMemory(
                legacyDiagnostics = LegacyShieldDiagnosticsDeletion(appContext::deleteSharedPreferences),
                onCleanupFailure = {
                    Log.w("ShieldStatus", "Legacy caller diagnostic cleanup was not durably confirmed.")
                },
            )
        },
    )

    init {
        memory.initialize()
    }

    fun read(): ShieldLiveStatus = memory.read()

    override fun record(status: ShieldLiveStatus) = memory.record(status)

    private companion object {
        val processOwner = ShieldLiveStatusOwner()
    }
}

internal class LegacyShieldDiagnosticsDeletion(
    private val deletePreferences: (String) -> Boolean,
) : LegacyShieldDiagnostics {
    override fun clear(): Boolean = try {
        // API 24+ deletes the dedicated XML and its .bak, including cached preferences.
        deletePreferences("shield_live_status")
    } catch (_: SecurityException) {
        false
    }
}
