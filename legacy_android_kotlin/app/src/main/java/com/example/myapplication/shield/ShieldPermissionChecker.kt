package com.example.myapplication.shield

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.provider.Settings
import androidx.core.content.ContextCompat

class ShieldPermissionChecker {
    fun getReadiness(context: Context): ShieldReadiness = ShieldReadinessEvaluator.evaluate(
        isPhoneStatePermissionGranted = isPermissionGranted(context, Manifest.permission.READ_PHONE_STATE),
        isCallLogPermissionGranted = isPermissionGranted(context, Manifest.permission.READ_CALL_LOG),
        isOverlayPermissionGranted = Settings.canDrawOverlays(context),
    )

    private fun isPermissionGranted(context: Context, permission: String): Boolean =
        ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
}
