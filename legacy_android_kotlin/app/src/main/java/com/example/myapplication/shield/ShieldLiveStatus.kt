package com.example.myapplication.shield

import android.content.Context
import androidx.core.content.edit

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

class ShieldLiveStatusStore(context: Context) : ShieldLiveStatusSink {
    private val sharedPreferences = context.applicationContext.getSharedPreferences(
        PREFERENCES_NAME,
        Context.MODE_PRIVATE,
    )

    fun read(): ShieldLiveStatus = ShieldLiveStatus(
        stage = sharedPreferences.getString(KEY_STAGE, null)
            ?.let { stored -> ShieldLiveStage.entries.firstOrNull { it.name == stored } }
            ?: ShieldLiveStage.Idle,
        rawIncomingNumber = sharedPreferences.getString(KEY_RAW_INCOMING_NUMBER, null),
        normalizedNumber = sharedPreferences.getString(KEY_NORMALIZED_NUMBER, null),
        targetHash = sharedPreferences.getString(KEY_TARGET_HASH, null),
        features = sharedPreferences.getString(KEY_FEATURES, null)
            ?.takeIf { it.isNotBlank() }
            ?.split(FEATURE_SEPARATOR)
            ?: emptyList(),
        errorMessage = sharedPreferences.getString(KEY_ERROR_MESSAGE, null),
        retryable = sharedPreferences.getBoolean(KEY_RETRYABLE, false),
        overlayState = sharedPreferences.getString(KEY_OVERLAY_STATE, null)
            ?.let { stored -> ShieldOverlayState.entries.firstOrNull { it.name == stored } }
            ?: ShieldOverlayState.None,
        overlayMessage = sharedPreferences.getString(KEY_OVERLAY_MESSAGE, null),
        updatedAtEpochMillis = sharedPreferences.takeIf { it.contains(KEY_UPDATED_AT) }
            ?.getLong(KEY_UPDATED_AT, 0L)
            ?.takeIf { it > 0L },
    )

    override fun record(status: ShieldLiveStatus) {
        sharedPreferences.edit {
            putString(KEY_STAGE, status.stage.name)
            putString(KEY_RAW_INCOMING_NUMBER, status.rawIncomingNumber)
            putString(KEY_NORMALIZED_NUMBER, status.normalizedNumber)
            putString(KEY_TARGET_HASH, status.targetHash)
            putString(KEY_FEATURES, status.features.joinToString(FEATURE_SEPARATOR))
            putString(KEY_ERROR_MESSAGE, status.errorMessage)
            putBoolean(KEY_RETRYABLE, status.retryable)
            putString(KEY_OVERLAY_STATE, status.overlayState.name)
            putString(KEY_OVERLAY_MESSAGE, status.overlayMessage)
            putLong(KEY_UPDATED_AT, status.updatedAtEpochMillis ?: 0L)
        }
    }

    companion object {
        private const val PREFERENCES_NAME = "shield_live_status"
        private const val FEATURE_SEPARATOR = "||"
        private const val KEY_STAGE = "stage"
        private const val KEY_RAW_INCOMING_NUMBER = "raw_incoming_number"
        private const val KEY_NORMALIZED_NUMBER = "normalized_number"
        private const val KEY_TARGET_HASH = "target_hash"
        private const val KEY_FEATURES = "features"
        private const val KEY_ERROR_MESSAGE = "error_message"
        private const val KEY_RETRYABLE = "retryable"
        private const val KEY_OVERLAY_STATE = "overlay_state"
        private const val KEY_OVERLAY_MESSAGE = "overlay_message"
        private const val KEY_UPDATED_AT = "updated_at"
    }
}

