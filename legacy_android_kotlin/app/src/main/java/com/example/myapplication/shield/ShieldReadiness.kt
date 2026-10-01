package com.example.myapplication.shield

data class ShieldReadiness(
    val isPhoneStatePermissionGranted: Boolean,
    val isCallLogPermissionGranted: Boolean,
    val isOverlayPermissionGranted: Boolean,
) {
    val missingRequirements: List<ShieldRequirement>
        get() = buildList {
            if (!isPhoneStatePermissionGranted) {
                add(ShieldRequirement.PhoneState)
            }
            if (!isCallLogPermissionGranted) {
                add(ShieldRequirement.CallLog)
            }
            if (!isOverlayPermissionGranted) {
                add(ShieldRequirement.Overlay)
            }
        }

    val status: ShieldStatus
        get() = when {
            missingRequirements.isEmpty() -> ShieldStatus.Active
            missingRequirements.size == ShieldRequirement.entries.size -> ShieldStatus.Blocked
            else -> ShieldStatus.ActionRequired
        }

    val isFullyActive: Boolean
        get() = missingRequirements.isEmpty()

    val shouldRequestPhonePermissions: Boolean
        get() = !isPhoneStatePermissionGranted || !isCallLogPermissionGranted

    val shouldOpenOverlaySettings: Boolean
        get() = !isOverlayPermissionGranted
}

enum class ShieldStatus {
    Active,
    ActionRequired,
    Blocked,
}

enum class ShieldRequirement {
    PhoneState,
    CallLog,
    Overlay,
}

object ShieldReadinessEvaluator {
    fun evaluate(
        isPhoneStatePermissionGranted: Boolean,
        isCallLogPermissionGranted: Boolean,
        isOverlayPermissionGranted: Boolean,
    ): ShieldReadiness = ShieldReadiness(
        isPhoneStatePermissionGranted = isPhoneStatePermissionGranted,
        isCallLogPermissionGranted = isCallLogPermissionGranted,
        isOverlayPermissionGranted = isOverlayPermissionGranted,
    )
}
