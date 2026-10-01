package com.example.myapplication.shield

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ShieldReadinessEvaluatorTest {
    @Test
    fun evaluate_returnsActiveWhenAllRequirementsAreGranted() {
        val readiness = ShieldReadinessEvaluator.evaluate(
            isPhoneStatePermissionGranted = true,
            isCallLogPermissionGranted = true,
            isOverlayPermissionGranted = true,
        )

        assertEquals(ShieldStatus.Active, readiness.status)
        assertTrue(readiness.isFullyActive)
        assertTrue(readiness.missingRequirements.isEmpty())
        assertFalse(readiness.shouldRequestPhonePermissions)
        assertFalse(readiness.shouldOpenOverlaySettings)
    }

    @Test
    fun evaluate_returnsActionRequiredWhenOnlySomeRequirementsAreMissing() {
        val readiness = ShieldReadinessEvaluator.evaluate(
            isPhoneStatePermissionGranted = true,
            isCallLogPermissionGranted = false,
            isOverlayPermissionGranted = true,
        )

        assertEquals(ShieldStatus.ActionRequired, readiness.status)
        assertFalse(readiness.isFullyActive)
        assertEquals(listOf(ShieldRequirement.CallLog), readiness.missingRequirements)
        assertTrue(readiness.shouldRequestPhonePermissions)
        assertFalse(readiness.shouldOpenOverlaySettings)
    }

    @Test
    fun evaluate_returnsBlockedWhenAllRequirementsAreMissing() {
        val readiness = ShieldReadinessEvaluator.evaluate(
            isPhoneStatePermissionGranted = false,
            isCallLogPermissionGranted = false,
            isOverlayPermissionGranted = false,
        )

        assertEquals(ShieldStatus.Blocked, readiness.status)
        assertFalse(readiness.isFullyActive)
        assertEquals(
            listOf(
                ShieldRequirement.PhoneState,
                ShieldRequirement.CallLog,
                ShieldRequirement.Overlay,
            ),
            readiness.missingRequirements,
        )
        assertTrue(readiness.shouldRequestPhonePermissions)
        assertTrue(readiness.shouldOpenOverlaySettings)
    }
}
