package com.example.myapplication.ui

import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import com.example.myapplication.R
import com.example.myapplication.data.ReportingFeature
import com.example.myapplication.ui.onboarding.*
import com.example.myapplication.ui.query.*
import com.example.myapplication.ui.reporting.*
import com.example.myapplication.ui.theme.BlacklistClientTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

/** Render-only fixtures: never instantiate the production networking or keystore factories. */
class CooldownScreenTest {
    @get:Rule val compose = createComposeRule()

    @Test fun authButtonsStayDisabledUntilExplicitRetry() {
        val state = mutableStateOf(OnboardingUiState(
            adUrl = "https://amaterky.sk/synthetic", challengeId = "synthetic", maskedPhoneNumber = "masked",
            otp = "000000", otpExpiresAt = "2030-01-01T00:15:00Z", retryAfterSeconds = 3,
        ))
        var requests = 0
        compose.setContent {
            BlacklistClientTheme {
                OnboardingScreen(state.value, {}, { requests++ }, {}, { requests++ }, { requests++ })
            }
        }
        compose.onNodeWithTag("retry_cooldown").assertExists()
        compose.onNodeWithTag(OnboardingTestTags.VERIFY_OTP_BUTTON).assertIsNotEnabled()
        val resendText = androidx.test.platform.app.InstrumentationRegistry.getInstrumentation()
            .targetContext.getString(R.string.start_new_challenge)
        compose.onNodeWithText(resendText).assertIsNotEnabled()
        compose.runOnIdle { state.value = state.value.copy(retryAfterSeconds = 0) }
        compose.onNodeWithTag(OnboardingTestTags.VERIFY_OTP_BUTTON).assertIsEnabled()
        compose.onNodeWithText(resendText).assertIsEnabled()
        compose.runOnIdle { assertEquals(0, requests) }
        compose.onNodeWithTag(OnboardingTestTags.VERIFY_OTP_BUTTON).performScrollTo().performClick()
        compose.runOnIdle { assertEquals(1, requests) }
    }

    @Test fun reportCountdownRecompositionNeverSubmits() {
        val state = mutableStateOf(ReportUiState(selectedFeature = ReportingFeature.NO_SHOW, retryAfterSeconds = 3))
        var requests = 0
        compose.setContent { BlacklistClientTheme { ReportScreen(state.value, {}, {}, { requests++ }, {}) } }
        compose.onNodeWithTag(ReportTestTags.SUBMIT_BUTTON).assertIsNotEnabled()
        compose.runOnIdle { state.value = state.value.copy(retryAfterSeconds = 2) }
        compose.onNodeWithText("Please wait 2 seconds before trying again. Nothing will be sent automatically.").assertExists()
        compose.runOnIdle { state.value = state.value.copy(retryAfterSeconds = 0); assertEquals(0, requests) }
        compose.onNodeWithTag(ReportTestTags.SUBMIT_BUTTON).assertIsEnabled()
    }

    @Test fun queryCountdownRecompositionNeverSubmits() {
        val state = mutableStateOf(BlacklistQueryUiState(targetHash = "a".repeat(64), retryAfterSeconds = 3))
        var requests = 0
        compose.setContent { BlacklistClientTheme { BlacklistQueryScreen(state.value, {}, { requests++ }, {}) } }
        compose.onNodeWithTag(BlacklistQueryTestTags.SUBMIT_BUTTON).assertIsNotEnabled()
        compose.runOnIdle { state.value = state.value.copy(retryAfterSeconds = 0); assertEquals(0, requests) }
        compose.onNodeWithTag(BlacklistQueryTestTags.SUBMIT_BUTTON).assertIsEnabled()
    }

    @Test fun initiationCooldownRequiresAnExplicitClickAfterExpiry() {
        val state = mutableStateOf(OnboardingUiState(adUrl = "https://amaterky.sk/synthetic", retryAfterSeconds = 3))
        var requests = 0
        compose.setContent {
            BlacklistClientTheme { OnboardingScreen(state.value, {}, { requests++ }, {}, {}, {}) }
        }
        compose.onNodeWithTag(OnboardingTestTags.START_VERIFICATION_BUTTON).assertIsNotEnabled()
        compose.runOnIdle { state.value = state.value.copy(retryAfterSeconds = 0) }
        compose.onNodeWithTag(OnboardingTestTags.START_VERIFICATION_BUTTON).assertIsEnabled()
        compose.runOnIdle { assertEquals(0, requests) }
        compose.onNodeWithTag(OnboardingTestTags.START_VERIFICATION_BUTTON).performScrollTo().performClick()
        compose.runOnIdle { assertEquals(1, requests) }
    }
}
