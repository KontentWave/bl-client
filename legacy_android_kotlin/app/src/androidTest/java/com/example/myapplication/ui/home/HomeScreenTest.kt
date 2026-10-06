package com.example.myapplication.ui.home

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollTo
import com.example.myapplication.shield.ShieldReadinessEvaluator
import com.example.myapplication.shield.ShieldLiveStage
import com.example.myapplication.shield.ShieldLiveStatus
import com.example.myapplication.ui.theme.BlacklistClientTheme
import org.junit.Rule
import org.junit.Test

class HomeScreenTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun diagnosticSnapshotIsRedactedAndCleanupFailureDoesNotManufactureNoMatch() {
        val status = mutableStateOf(
            ShieldLiveStatus(
                stage = ShieldLiveStage.QueryFailed,
                rawIncomingNumber = "+421900000001",
                normalizedNumber = "+421900000001",
                targetHash = "synthetic-sensitive-hash",
                features = listOf("synthetic-sensitive-feature"),
                errorMessage = "synthetic-sensitive-backend-message",
                overlayMessage = "synthetic-sensitive-exception-message",
                legacyCleanupFailed = true,
            ),
        )
        composeTestRule.setContent {
            BlacklistClientTheme {
                HomeScreen(
                    maskedPhoneNumber = "",
                    verifiedAt = "synthetic verification",
                    shieldReadiness = ShieldReadinessEvaluator.evaluate(true, true, true),
                    shieldLiveStatus = status.value,
                    onOpenReportingClick = {},
                    onOpenQueryClick = {},
                    onRequestShieldPhonePermissionsClick = {},
                    onOpenOverlayPermissionClick = {},
                    onRefreshShieldStatusClick = {},
                    onRestartOnboardingClick = {},
                )
            }
        }
        composeTestRule.onNodeWithTag(HomeTestTags.SHIELD_LIVE_STATUS_BODY).assertTextEquals(
            "Caller lookup failed or exceeded its deadline. This call was not checked. No automatic retry.",
        )
        composeTestRule.onNodeWithText("Legacy caller diagnostic removal", substring = true).assertExists()
        for (sensitive in listOf("+421900000001", "synthetic-sensitive")) {
            composeTestRule.onNodeWithText(sensitive, substring = true).assertDoesNotExist()
        }
        composeTestRule.onNodeWithText("No Level 2 match returned").assertDoesNotExist()
        composeTestRule.runOnIdle { status.value = ShieldLiveStatus() }
        composeTestRule.onNodeWithTag(HomeTestTags.SHIELD_LIVE_STATUS_BODY).assertTextEquals(
            "No ringing call is currently tracked in this process. No checked result is restored after restart.",
        )
        composeTestRule.onNodeWithText("Legacy caller diagnostic removal", substring = true).assertDoesNotExist()
    }

    @Test
    fun verifiedHome_showsSummaryAndNextSliceCards() {
        composeTestRule.setContent {
            BlacklistClientTheme {
                HomeScreen(
                    maskedPhoneNumber = "+421***456",
                    verifiedAt = "2026-04-07T13:48:23+00:00",
                    shieldReadiness = ShieldReadinessEvaluator.evaluate(
                        isPhoneStatePermissionGranted = true,
                        isCallLogPermissionGranted = true,
                        isOverlayPermissionGranted = true,
                    ),
                    shieldLiveStatus = ShieldLiveStatus(
                        stage = ShieldLiveStage.NoMatch,
                        rawIncomingNumber = "+421903223183",
                        normalizedNumber = "+421903223183",
                        targetHash = "abc123",
                    ),
                    onOpenReportingClick = {},
                    onOpenQueryClick = {},
                    onRequestShieldPhonePermissionsClick = {},
                    onOpenOverlayPermissionClick = {},
                    onRefreshShieldStatusClick = {},
                    onRestartOnboardingClick = {},
                )
            }
        }

        composeTestRule.onNodeWithTag(HomeTestTags.VERIFIED_SUMMARY_CARD).performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithTag(HomeTestTags.SHIELD_STATUS_CARD).performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithTag(HomeTestTags.SHIELD_STATUS_TITLE).performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithText("Shield active").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithTag(HomeTestTags.SHIELD_LIVE_STATUS_CARD).performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithTag(HomeTestTags.SHIELD_LIVE_STATUS_TITLE).performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithText("No Level 2 match returned").performScrollTo().assertIsDisplayed()
        composeTestRule.onAllNodesWithTag(HomeTestTags.REQUEST_SHIELD_PHONE_PERMISSIONS_BUTTON).assertCountEquals(0)
        composeTestRule.onAllNodesWithTag(HomeTestTags.OPEN_OVERLAY_PERMISSION_BUTTON).assertCountEquals(0)
        composeTestRule.onNodeWithTag(HomeTestTags.REFRESH_SHIELD_STATUS_BUTTON).performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithTag(HomeTestTags.REPORTING_SLICE_CARD).performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithTag(HomeTestTags.OPEN_REPORTING_BUTTON).performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithTag(HomeTestTags.QUERY_SLICE_CARD).performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithTag(HomeTestTags.OPEN_QUERY_BUTTON).performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithTag(HomeTestTags.RESTART_ONBOARDING_BUTTON).performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithText("+421***456", substring = true).performScrollTo().assertIsDisplayed()
    }

    @Test
    fun verifiedHome_showsShieldPermissionActionsWhenMissing() {
        composeTestRule.setContent {
            BlacklistClientTheme {
                HomeScreen(
                    maskedPhoneNumber = "+421***456",
                    verifiedAt = "2026-04-07T13:48:23+00:00",
                    shieldReadiness = ShieldReadinessEvaluator.evaluate(
                        isPhoneStatePermissionGranted = false,
                        isCallLogPermissionGranted = false,
                        isOverlayPermissionGranted = false,
                    ),
                    shieldLiveStatus = ShieldLiveStatus(
                        stage = ShieldLiveStage.Idle,
                    ),
                    onOpenReportingClick = {},
                    onOpenQueryClick = {},
                    onRequestShieldPhonePermissionsClick = {},
                    onOpenOverlayPermissionClick = {},
                    onRefreshShieldStatusClick = {},
                    onRestartOnboardingClick = {},
                )
            }
        }

        composeTestRule.onNodeWithText("Shield inactive").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithTag(HomeTestTags.REQUEST_SHIELD_PHONE_PERMISSIONS_BUTTON).performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithTag(HomeTestTags.OPEN_OVERLAY_PERMISSION_BUTTON).performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithTag(HomeTestTags.REFRESH_SHIELD_STATUS_BUTTON).performScrollTo().assertIsDisplayed()
    }
}
