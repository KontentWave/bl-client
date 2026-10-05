package com.example.myapplication.ui

import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.Text
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextReplacement
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.example.myapplication.data.AuthRepository
import com.example.myapplication.data.InitiateAuthResult
import com.example.myapplication.data.VerifyAuthResult
import com.example.myapplication.security.SignedRequestFactory
import com.example.myapplication.security.SignedRequestPayload
import com.example.myapplication.ui.onboarding.OnboardingScreen
import com.example.myapplication.ui.onboarding.OnboardingTestTags
import com.example.myapplication.ui.onboarding.OnboardingViewModel
import com.example.myapplication.ui.theme.BlacklistClientTheme
import java.time.Instant
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

/** Blank Activity, synthetic time and fakes only; never uses production networking or keys. */
class OtpCorrectionLifecycleTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    @Test fun correctionSurvivesNavigationAndRecreationAndCooldownNeverReplaysCommands() {
        var initiations = 0
        val submissions = mutableListOf<Pair<String, String>>()
        var now = 0L
        val visible = mutableStateOf(true)
        val repository = object : AuthRepository {
            override suspend fun initiateAuth(adUrl: String): InitiateAuthResult {
                initiations++
                return InitiateAuthResult.Success("synthetic-challenge", "masked", "2030-01-01T00:15:00Z")
            }
            override suspend fun verifyAuth(challengeId: String, otp: String, publicKey: String, signature: String): VerifyAuthResult {
                submissions.add(challengeId to otp)
                return when (submissions.size) {
                    1 -> VerifyAuthResult.Failure(
                        "otp_invalid_or_expired", "Invalid or expired.",
                        mapOf("otp" to listOf("Invalid or expired.")),
                    )
                    2 -> VerifyAuthResult.Failure("rate_limited", "Wait", retryable = true, retryAfterSeconds = 4)
                    else -> VerifyAuthResult.Success(challengeId, "masked", "2030-01-01T00:00:04Z")
                }
            }
        }
        val signer = object : SignedRequestFactory {
            override fun createVerifyRequest(challengeId: String) = SignedRequestPayload("synthetic-public", "synthetic-signature")
            override fun createReportRequest(clientPhoneNumber: String, feature: String): SignedRequestPayload = error("No report")
            override fun createBlacklistCheckRequest(targetHash: String): SignedRequestPayload = error("No query")
        }
        val factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T = OnboardingViewModel(
                repository, signer, { now }, { Instant.parse("2030-01-01T00:00:00Z").plusMillis(now) }, false,
            ) as T
        }
        lateinit var retained: OnboardingViewModel
        var attached = false
        fun render(activity: ComponentActivity) {
            val vm = ViewModelProvider(activity, factory)[OnboardingViewModel::class.java]
            if (attached) assertSame(retained, vm) else { retained = vm; attached = true }
            activity.setContent {
                BlacklistClientTheme {
                    if (visible.value) OnboardingScreen(
                        vm.uiState.collectAsState().value, vm::onAdUrlChanged, vm::submitAdUrl,
                        vm::onOtpChanged, vm::submitOtp, vm::startNewChallenge,
                    ) else Text("Isolated destination")
                }
            }
        }
        compose.activityRule.scenario.onActivity {
            render(it)
            retained.onAdUrlChanged("https://amaterky.sk/synthetic")
            retained.submitAdUrl()
        }
        compose.waitUntil(5000) { retained.uiState.value.hasInitiatedChallenge }
        compose.onNodeWithTag(OnboardingTestTags.OTP_INPUT).performScrollTo().performTextReplacement("000000")
        compose.onNodeWithTag(OnboardingTestTags.VERIFY_OTP_BUTTON).performScrollTo().performClick()
        compose.waitUntil(5000) { retained.uiState.value.otpError != null }
        compose.onNodeWithTag(OnboardingTestTags.VERIFY_OTP_BUTTON).assertIsEnabled()
        compose.onNodeWithTag(OnboardingTestTags.OTP_INPUT).performScrollTo().performTextReplacement("123456")
        compose.runOnIdle { visible.value = false }
        compose.onNodeWithText("Isolated destination").assertExists()
        compose.activityRule.scenario.recreate()
        compose.activityRule.scenario.onActivity { render(it) }
        compose.runOnIdle {
            assertEquals("synthetic-challenge", retained.uiState.value.challengeId)
            assertEquals("masked", retained.uiState.value.maskedPhoneNumber)
            assertEquals("2030-01-01T00:15:00Z", retained.uiState.value.otpExpiresAt)
            assertEquals("123456", retained.uiState.value.otp)
            assertNotNull(retained.uiState.value.challengeRetryGuidance)
            assertEquals(1, initiations)
            assertEquals(listOf("synthetic-challenge" to "000000"), submissions)
            visible.value = true
        }
        compose.onNodeWithTag(OnboardingTestTags.VERIFY_OTP_BUTTON).performScrollTo().performClick()
        compose.waitUntil(5000) { retained.uiState.value.retryAfterSeconds == 4L }
        compose.onNodeWithTag(OnboardingTestTags.VERIFY_OTP_BUTTON).assertIsNotEnabled()
        compose.onNodeWithTag(OnboardingTestTags.NEW_CHALLENGE_BUTTON).assertIsNotEnabled()
        compose.onNodeWithTag(OnboardingTestTags.OTP_INPUT).performScrollTo().performTextReplacement("654321")
        compose.runOnIdle { now = 4000 }
        compose.waitUntil(5000) { retained.uiState.value.retryAfterSeconds == 0L }
        compose.onNodeWithTag(OnboardingTestTags.VERIFY_OTP_BUTTON).assertIsEnabled()
        compose.runOnIdle { assertEquals(2, submissions.size); assertEquals(1, initiations) }
        compose.onNodeWithTag(OnboardingTestTags.VERIFY_OTP_BUTTON).performScrollTo().performClick()
        compose.waitUntil(5000) { retained.uiState.value.isVerified }
        compose.runOnIdle {
            assertEquals(listOf("synthetic-challenge" to "000000", "synthetic-challenge" to "123456", "synthetic-challenge" to "654321"), submissions)
            assertEquals(1, initiations)
        }
    }
}
