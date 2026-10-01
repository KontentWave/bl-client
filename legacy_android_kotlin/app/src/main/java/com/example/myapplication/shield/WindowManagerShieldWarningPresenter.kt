package com.example.myapplication.shield

import android.content.Context
import android.graphics.PixelFormat
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.widget.Button
import android.widget.TextView
import com.example.myapplication.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.lang.ref.WeakReference

class WindowManagerShieldWarningPresenter(
    context: Context,
) : ShieldWarningPresenter {
    private val appContext = context.applicationContext

    override suspend fun showWarning(
        normalizedNumber: String,
        features: List<String>,
        targetHash: String?,
    ): ShieldOverlayPresentation = withContext(Dispatchers.Main) {
        if (!Settings.canDrawOverlays(appContext)) {
            return@withContext ShieldOverlayPresentation(
                state = ShieldOverlayState.SkippedPermission,
                message = appContext.getString(R.string.shield_overlay_missing_permission_message),
            )
        }

        val windowManager = appContext.getSystemService(Context.WINDOW_SERVICE) as? WindowManager
            ?: return@withContext ShieldOverlayPresentation(
                state = ShieldOverlayState.Failed,
                message = appContext.getString(R.string.shield_overlay_window_service_unavailable),
            )

        runCatching {
            dismissCurrentOverlay(windowManager)

            val overlayView = LayoutInflater.from(appContext)
                .inflate(R.layout.shield_warning_overlay, null)

            val titleView = overlayView.findViewById<TextView>(R.id.shieldOverlayTitle)
            val bodyView = overlayView.findViewById<TextView>(R.id.shieldOverlayBody)
            val closeButton = overlayView.findViewById<Button>(R.id.shieldOverlayDismissButton)

            val featureLabels = features.joinToString(separator = ", ")
            titleView.text = appContext.getString(R.string.shield_overlay_title)
            bodyView.text = appContext.getString(
                R.string.shield_overlay_body,
                normalizedNumber,
                featureLabels,
                targetHash ?: appContext.getString(R.string.home_shield_live_unknown_value),
            )

            val announcement = appContext.getString(
                R.string.shield_overlay_announcement,
                featureLabels,
            )
            overlayView.contentDescription = announcement
            overlayView.importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_YES
            closeButton.setOnClickListener {
                dismissCurrentOverlay(windowManager)
            }

            windowManager.addView(overlayView, overlayLayoutParams())
            currentOverlayView = WeakReference(overlayView)
            @Suppress("DEPRECATION")
            Handler(Looper.getMainLooper()).post {
                overlayView.sendAccessibilityEvent(AccessibilityEvent.TYPE_ANNOUNCEMENT)
                overlayView.announceForAccessibility(announcement)
            }
        }.fold(
            onSuccess = {
                ShieldOverlayPresentation(
                    state = ShieldOverlayState.Shown,
                    message = appContext.getString(R.string.shield_overlay_shown_message),
                )
            },
            onFailure = { throwable ->
                ShieldOverlayPresentation(
                    state = ShieldOverlayState.Failed,
                    message = throwable.message ?: appContext.getString(R.string.shield_overlay_failed_message),
                )
            },
        )
    }

    override suspend fun dismissWarning(): ShieldOverlayPresentation = withContext(Dispatchers.Main) {
        val windowManager = appContext.getSystemService(Context.WINDOW_SERVICE) as? WindowManager
        val overlayView = currentOverlayView?.get()
        if (windowManager == null || overlayView == null) {
            return@withContext ShieldOverlayPresentation(state = ShieldOverlayState.None)
        }

        dismissCurrentOverlay(windowManager)
        ShieldOverlayPresentation(
            state = ShieldOverlayState.Dismissed,
            message = appContext.getString(R.string.shield_overlay_dismissed_message),
        )
    }

    @Suppress("DEPRECATION")
    private fun overlayLayoutParams(): WindowManager.LayoutParams = WindowManager.LayoutParams(
        WindowManager.LayoutParams.MATCH_PARENT,
        WindowManager.LayoutParams.WRAP_CONTENT,
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            WindowManager.LayoutParams.TYPE_PHONE
        },
        WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
            WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
        PixelFormat.TRANSLUCENT,
    ).apply {
        gravity = Gravity.TOP
        y = 48
    }

    private fun dismissCurrentOverlay(windowManager: WindowManager) {
        currentOverlayView?.get()?.let { existingView ->
            runCatching {
                windowManager.removeView(existingView)
            }
        }
        currentOverlayView = null
    }

    private companion object {
        var currentOverlayView: WeakReference<View>? = null
    }
}


