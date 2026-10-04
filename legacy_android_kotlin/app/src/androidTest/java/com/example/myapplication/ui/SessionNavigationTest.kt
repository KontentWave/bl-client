package com.example.myapplication.ui

import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.test.platform.app.InstrumentationRegistry
import androidx.lifecycle.ViewModelProvider
import com.example.myapplication.testing.RecoveryHarnessActivity
import com.example.myapplication.testing.RecoveryHarnessFixtures
import com.example.myapplication.ui.home.HomeTestTags
import com.example.myapplication.ui.onboarding.OnboardingTestTags
import com.example.myapplication.ui.onboarding.OnboardingViewModel
import com.example.myapplication.ui.query.BlacklistQueryTestTags
import com.example.myapplication.ui.reporting.ReportTestTags
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Rule
import org.junit.Test
import org.junit.rules.ExternalResource

class SessionNavigationTest {
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext
    @get:Rule(order = 0) val fixtures = object : ExternalResource() {
        override fun before() { RecoveryHarnessFixtures.seed(context) }
        override fun after() { RecoveryHarnessFixtures.clear(context) }
    }
    @get:Rule(order = 1) val compose = createAndroidComposeRule<RecoveryHarnessActivity>()

    @Test fun productionNavigationRestoresAndRecreatesWithoutRequestReplay() {
        lateinit var original: OnboardingViewModel
        compose.activityRule.scenario.onActivity {
            original = ViewModelProvider(it)[OnboardingViewModel::class.java]
        }
        compose.onNodeWithText("Device workspace recovered locally").assertExists()
        compose.onNodeWithTag(HomeTestTags.OPEN_REPORTING_BUTTON).performScrollTo().performClick()
        compose.onNodeWithTag(ReportTestTags.BACK_BUTTON).performScrollTo().performClick()
        compose.onNodeWithTag(HomeTestTags.OPEN_QUERY_BUTTON).performScrollTo().performClick()
        compose.onNodeWithTag(BlacklistQueryTestTags.TARGET_HASH_INPUT).assertExists()
        compose.activityRule.scenario.recreate()
        compose.activityRule.scenario.onActivity {
            assertSame(original, ViewModelProvider(it)[OnboardingViewModel::class.java])
        }
        // MainActivity reattaches its own production content; the test does not call setContent.
        compose.onNodeWithText("Device workspace recovered locally").assertExists()
        compose.onNodeWithTag(HomeTestTags.OPEN_QUERY_BUTTON).performScrollTo().performClick()
        compose.onNodeWithTag(BlacklistQueryTestTags.BACK_BUTTON).performScrollTo().performClick()
        compose.onNodeWithTag(HomeTestTags.RESTART_ONBOARDING_BUTTON).performScrollTo().performClick()
        compose.onNodeWithTag(OnboardingTestTags.AD_URL_INPUT).assertExists()
        for (command in listOf("initiate", "verify", "report", "query")) {
            assertEquals(command, 0, RecoveryHarnessFixtures.requests(context, command))
        }
    }

    @Test fun ordinaryQueryRejectionReturnsToOnboardingWithoutSms() {
        compose.activityRule.scenario.onActivity { it.rejectQuery = true }
        compose.onNodeWithTag(HomeTestTags.OPEN_QUERY_BUTTON).performScrollTo().performClick()
        compose.onNodeWithTag(BlacklistQueryTestTags.TARGET_HASH_INPUT).performTextInput("a".repeat(64))
        compose.onNodeWithTag(BlacklistQueryTestTags.SUBMIT_BUTTON).performScrollTo().performClick()
        compose.onNodeWithTag(OnboardingTestTags.AD_URL_INPUT).assertExists()
        compose.onNodeWithText("The server rejected device authorization.", substring = true).assertExists()
        assertEquals(1, RecoveryHarnessFixtures.requests(context, "query"))
        for (command in listOf("initiate", "verify", "report")) {
            assertEquals(command, 0, RecoveryHarnessFixtures.requests(context, command))
        }
    }
}
