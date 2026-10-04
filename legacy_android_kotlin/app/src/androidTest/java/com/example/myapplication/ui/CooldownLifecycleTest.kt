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
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.example.myapplication.data.BlacklistQueryRepository
import com.example.myapplication.data.BlacklistQueryResult
import com.example.myapplication.ui.query.BlacklistQueryScreen
import com.example.myapplication.ui.query.BlacklistQueryTestTags
import com.example.myapplication.ui.query.BlacklistQueryViewModel
import com.example.myapplication.ui.theme.BlacklistClientTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Rule
import org.junit.Test

/** Blank ComponentActivity only: no MainActivity, production factories, network or keystore. */
class CooldownLifecycleTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    @Test fun countdownSurvivesNavigationAndActivityRecreationWithoutReplay() {
        var requests = 0
        var now = 0L
        val visible = mutableStateOf(true)
        val repository = object : BlacklistQueryRepository {
            override suspend fun checkTargetHash(targetHash: String): BlacklistQueryResult {
                requests++
                return BlacklistQueryResult.Failure(
                    "rate_limited", "Wait", retryable = true, retryAfterSeconds = 10,
                )
            }
        }
        val factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T =
                BlacklistQueryViewModel(repository) { now } as T
        }
        lateinit var retained: BlacklistQueryViewModel
        var attached = false
        fun render(activity: ComponentActivity) {
            val vm = ViewModelProvider(activity, factory)[BlacklistQueryViewModel::class.java]
            if (attached) assertSame(retained, vm) else { retained = vm; attached = true }
            activity.setContent {
                BlacklistClientTheme {
                    if (visible.value) {
                        BlacklistQueryScreen(vm.uiState.collectAsState().value,
                            vm::onTargetHashChanged, vm::submitQuery, { visible.value = false })
                    } else Text("Isolated destination")
                }
            }
        }
        compose.activityRule.scenario.onActivity { activity ->
            render(activity)
            retained.onTargetHashChanged("a".repeat(64))
            retained.submitQuery()
        }
        compose.waitUntil(5000) { retained.uiState.value.retryAfterSeconds == 10L }
        compose.onNodeWithTag(BlacklistQueryTestTags.SUBMIT_BUTTON).assertIsNotEnabled()
        compose.runOnIdle { visible.value = false; now = 1000 }
        compose.onNodeWithText("Isolated destination").assertExists()
        compose.runOnIdle { visible.value = true }
        compose.onNodeWithTag(BlacklistQueryTestTags.SUBMIT_BUTTON).assertIsNotEnabled()

        compose.activityRule.scenario.recreate()
        // The fixture deliberately reattaches content to the recreated blank Activity.
        // The real retained ViewModelStore must supply the same VM, not a copied state.
        compose.activityRule.scenario.onActivity { render(it) }
        compose.waitUntil(5000) { retained.uiState.value.retryAfterSeconds == 9L }
        compose.onNodeWithTag(BlacklistQueryTestTags.SUBMIT_BUTTON).assertIsNotEnabled()
        compose.runOnIdle { assertEquals(1, requests); now = 10000 }
        compose.waitUntil(5000) { retained.uiState.value.retryAfterSeconds == 0L }
        compose.onNodeWithTag(BlacklistQueryTestTags.SUBMIT_BUTTON).assertIsEnabled()
        compose.runOnIdle { assertEquals(1, requests) }
        compose.onNodeWithTag(BlacklistQueryTestTags.SUBMIT_BUTTON).performScrollTo().performClick()
        compose.waitUntil(5000) { requests == 2 }
        compose.onNodeWithTag(BlacklistQueryTestTags.SUBMIT_BUTTON).assertIsNotEnabled()
    }
}
