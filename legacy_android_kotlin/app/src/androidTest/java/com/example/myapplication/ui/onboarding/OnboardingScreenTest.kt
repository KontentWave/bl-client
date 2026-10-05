package com.example.myapplication.ui.onboarding

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.SemanticsProperties
import com.example.myapplication.ui.theme.BlacklistClientTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class OnboardingScreenTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    @Test fun ambiguousOtpErrorOffersAccessibleCorrectionWithoutClaimingValidity() {
        composeTestRule.setContent {
            BlacklistClientTheme {
                OnboardingScreen(
                    OnboardingUiState(
                        challengeId = "synthetic", maskedPhoneNumber = "masked",
                        otpExpiresAt = "2030-01-01T00:15:00Z", otp = "000000",
                        otpError = "Invalid or expired.",
                        challengeRetryGuidance = "Server status is unknown. Correct the OTP and retry manually.",
                    ), {}, {}, {}, {}, {},
                )
            }
        }
        composeTestRule.onNodeWithTag(OnboardingTestTags.OTP_INPUT).assertIsEnabled()
        composeTestRule.onNodeWithTag(OnboardingTestTags.VERIFY_OTP_BUTTON).assertIsEnabled()
        composeTestRule.onNodeWithText("Server status is unknown. Correct the OTP and retry manually.")
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.LiveRegion, LiveRegionMode.Polite))
        composeTestRule.onNodeWithText("Invalid or expired.", useUnmergedTree = true)
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.LiveRegion, LiveRegionMode.Polite))
    }

    @Test fun terminalChallengeBlocksOtpButLeavesExplicitNewChallengeAvailable() {
        composeTestRule.setContent {
            BlacklistClientTheme {
                OnboardingScreen(
                    OnboardingUiState(
                        challengeId = "synthetic", otp = "000000", isChallengeLocked = true,
                    ), {}, {}, {}, {}, {},
                )
            }
        }
        composeTestRule.onNodeWithTag(OnboardingTestTags.OTP_INPUT).assertIsNotEnabled()
        composeTestRule.onNodeWithTag(OnboardingTestTags.VERIFY_OTP_BUTTON).assertIsNotEnabled()
        composeTestRule.onNodeWithTag(OnboardingTestTags.NEW_CHALLENGE_BUTTON).assertIsEnabled()
        composeTestRule.onNodeWithText("Verification is blocked for this challenge.", substring = true).assertExists()
    }

    @Test
    fun initialState_showsOnlyAdUrlStep() {
        composeTestRule.setContent {
            BlacklistClientTheme {
                OnboardingScreen(
                    uiState = OnboardingUiState(),
                    onAdUrlChanged = {},
                    onStartVerificationClick = {},
                    onOtpChanged = {},
                    onVerifyOtpClick = {},
                    onStartNewChallengeClick = {},
                )
            }
        }

        assertEquals(1, composeTestRule.onAllNodesWithTag(OnboardingTestTags.AD_URL_INPUT).fetchSemanticsNodes().size)
        assertEquals(1, composeTestRule.onAllNodesWithTag(OnboardingTestTags.START_VERIFICATION_BUTTON).fetchSemanticsNodes().size)
        assertTrue(composeTestRule.onAllNodesWithTag(OnboardingTestTags.OTP_CARD).fetchSemanticsNodes().isEmpty())
        assertTrue(composeTestRule.onAllNodesWithTag(OnboardingTestTags.OTP_INPUT).fetchSemanticsNodes().isEmpty())
        assertTrue(composeTestRule.onAllNodesWithTag(OnboardingTestTags.VERIFIED_CARD).fetchSemanticsNodes().isEmpty())
    }

    @Test
    fun initiatedChallenge_showsOtpStepAndHidesAdUrlInput() {
        composeTestRule.setContent {
            BlacklistClientTheme {
                OnboardingScreen(
                    uiState = OnboardingUiState(
                        adUrl = "https://example.com/ad/1",
                        challengeId = "challenge-123",
                        maskedPhoneNumber = "+421***456",
                        otpExpiresAt = "2026-04-01T12:15:00+00:00",
                    ),
                    onAdUrlChanged = {},
                    onStartVerificationClick = {},
                    onOtpChanged = {},
                    onVerifyOtpClick = {},
                    onStartNewChallengeClick = {},
                )
            }
        }

        assertTrue(composeTestRule.onAllNodesWithTag(OnboardingTestTags.AD_URL_INPUT).fetchSemanticsNodes().isEmpty())
        assertEquals(1, composeTestRule.onAllNodesWithTag(OnboardingTestTags.OTP_CARD).fetchSemanticsNodes().size)
        assertEquals(1, composeTestRule.onAllNodesWithTag(OnboardingTestTags.OTP_INPUT).fetchSemanticsNodes().size)
        assertEquals(1, composeTestRule.onAllNodesWithTag(OnboardingTestTags.VERIFY_OTP_BUTTON).fetchSemanticsNodes().size)
        assertEquals(1, composeTestRule.onAllNodesWithText("+421***456", substring = true).fetchSemanticsNodes().size)
    }

    @Test
    fun verifiedState_showsVerifiedCard() {
        composeTestRule.setContent {
            BlacklistClientTheme {
                OnboardingScreen(
                    uiState = OnboardingUiState(
                        adUrl = "https://example.com/ad/1",
                        maskedPhoneNumber = "+421***456",
                        verifiedAt = "2026-03-31T12:10:00+00:00",
                    ),
                    onAdUrlChanged = {},
                    onStartVerificationClick = {},
                    onOtpChanged = {},
                    onVerifyOtpClick = {},
                    onStartNewChallengeClick = {},
                )
            }
        }

        assertEquals(1, composeTestRule.onAllNodesWithTag(OnboardingTestTags.VERIFIED_CARD).fetchSemanticsNodes().size)
        assertTrue(composeTestRule.onAllNodesWithTag(OnboardingTestTags.OTP_CARD).fetchSemanticsNodes().isEmpty())
        assertTrue(composeTestRule.onAllNodesWithTag(OnboardingTestTags.AD_URL_INPUT).fetchSemanticsNodes().isEmpty())
    }
}

