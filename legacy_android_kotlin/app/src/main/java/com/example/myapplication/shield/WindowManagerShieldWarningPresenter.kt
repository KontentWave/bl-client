package com.example.myapplication.shield

import android.content.Context
import android.graphics.PixelFormat
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.util.Log
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.widget.Button
import android.widget.TextView
import com.example.myapplication.R
import java.lang.ref.WeakReference

class WindowManagerShieldWarningPresenter(
    context: Context,
) : ShieldWarningPresenter {
    private val appContext = context.applicationContext

    override fun showWarning(
        normalizedNumber: String,
        features: List<String>,
        targetHash: String?,
    ): ShieldOverlayPresentation {
        check(Looper.myLooper() == Looper.getMainLooper())
        if (!Settings.canDrawOverlays(appContext)) {
            return ShieldOverlayPresentation(
                state = ShieldOverlayState.SkippedPermission,
                message = appContext.getString(R.string.shield_overlay_missing_permission_message),
            )
        }

        val windowManager = appContext.getSystemService(Context.WINDOW_SERVICE) as? WindowManager
            ?: return ShieldOverlayPresentation(
                state = ShieldOverlayState.Failed,
                message = appContext.getString(R.string.shield_overlay_window_service_unavailable),
            )

        val dismissal = dismissCurrentOverlay(windowManager)
        if (dismissal.state == ShieldOverlayState.Failed) return dismissal
        return try {
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
                if (currentOverlayView?.get() === overlayView && overlayView.isAttachedToWindow) {
                    overlayView.sendAccessibilityEvent(AccessibilityEvent.TYPE_ANNOUNCEMENT)
                    overlayView.announceForAccessibility(announcement)
                }
            }
            ShieldOverlayPresentation(
                state = ShieldOverlayState.Shown,
                message = appContext.getString(R.string.shield_overlay_shown_message),
            )
        } catch (_: WindowManager.BadTokenException) {
            failedPresentation()
        } catch (_: WindowManager.InvalidDisplayException) {
            failedPresentation()
        } catch (_: SecurityException) {
            failedPresentation()
        } catch (_: IllegalArgumentException) {
            failedPresentation()
        }
    }

    override fun dismissWarning(): ShieldOverlayPresentation {
        check(Looper.myLooper() == Looper.getMainLooper())
        val windowManager = appContext.getSystemService(Context.WINDOW_SERVICE) as? WindowManager
        if (currentOverlayView?.get() == null) {
            return ShieldOverlayPresentation(state = ShieldOverlayState.None)
        }
        if (windowManager == null) return failedPresentation()
        return dismissCurrentOverlay(windowManager)
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

    private fun dismissCurrentOverlay(windowManager: WindowManager): ShieldOverlayPresentation {
        val existingView = currentOverlayView?.get()
            ?: return ShieldOverlayPresentation(ShieldOverlayState.None)
        try {
            windowManager.removeView(existingView)
        } catch (_: IllegalArgumentException) {
            // Android may already have detached the view; do not claim a live warning.
            Log.w(TAG, "Shield warning view was already detached.")
        } catch (_: SecurityException) {
            return failedPresentation()
        }
        currentOverlayView = null
        return ShieldOverlayPresentation(
            state = ShieldOverlayState.Dismissed,
            message = appContext.getString(R.string.shield_overlay_dismissed_message),
        )
    }

    private fun failedPresentation(): ShieldOverlayPresentation {
        Log.w(TAG, "Shield warning window operation failed.")
        return ShieldOverlayPresentation(
            state = ShieldOverlayState.Failed,
            message = appContext.getString(R.string.shield_overlay_failed_message),
        )
    }

    private companion object {
        const val TAG = "ShieldWarning"
        var currentOverlayView: WeakReference<View>? = null
    }
}
