package com.example.myapplication.ui.home

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
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

        composeTestRule.onNodeWithTag(HomeTestTags.VERIFIED_SUMMARY_CARD).assertIsDisplayed()
        composeTestRule.onNodeWithTag(HomeTestTags.SHIELD_STATUS_CARD).assertIsDisplayed()
        composeTestRule.onNodeWithTag(HomeTestTags.SHIELD_STATUS_TITLE).assertIsDisplayed()
        composeTestRule.onNodeWithText("Shield active").assertIsDisplayed()
        composeTestRule.onNodeWithTag(HomeTestTags.SHIELD_LIVE_STATUS_CARD).assertIsDisplayed()
        composeTestRule.onNodeWithTag(HomeTestTags.SHIELD_LIVE_STATUS_TITLE).assertIsDisplayed()
        composeTestRule.onNodeWithText("No Level 2 match returned").assertIsDisplayed()
        composeTestRule.onAllNodesWithTag(HomeTestTags.REQUEST_SHIELD_PHONE_PERMISSIONS_BUTTON).assertCountEquals(0)
        composeTestRule.onAllNodesWithTag(HomeTestTags.OPEN_OVERLAY_PERMISSION_BUTTON).assertCountEquals(0)
        composeTestRule.onNodeWithTag(HomeTestTags.REFRESH_SHIELD_STATUS_BUTTON).assertIsDisplayed()
        composeTestRule.onNodeWithTag(HomeTestTags.REPORTING_SLICE_CARD).assertIsDisplayed()
        composeTestRule.onNodeWithTag(HomeTestTags.OPEN_REPORTING_BUTTON).assertIsDisplayed()
        composeTestRule.onNodeWithTag(HomeTestTags.QUERY_SLICE_CARD).assertIsDisplayed()
        composeTestRule.onNodeWithTag(HomeTestTags.OPEN_QUERY_BUTTON).assertIsDisplayed()
        composeTestRule.onNodeWithTag(HomeTestTags.RESTART_ONBOARDING_BUTTON).assertIsDisplayed()
        composeTestRule.onNodeWithText("+421***456", substring = true).assertIsDisplayed()
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

        composeTestRule.onNodeWithText("Shield inactive").assertIsDisplayed()
        composeTestRule.onNodeWithTag(HomeTestTags.REQUEST_SHIELD_PHONE_PERMISSIONS_BUTTON).assertIsDisplayed()
        composeTestRule.onNodeWithTag(HomeTestTags.OPEN_OVERLAY_PERMISSION_BUTTON).assertIsDisplayed()
        composeTestRule.onNodeWithTag(HomeTestTags.REFRESH_SHIELD_STATUS_BUTTON).assertIsDisplayed()
    }
}

