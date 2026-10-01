package com.example.myapplication.ui.query

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import com.example.myapplication.ui.theme.BlacklistClientTheme
import org.junit.Rule
import org.junit.Test

class BlacklistQueryScreenTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun initialState_showsInputAndSubmitButton() {
        composeTestRule.setContent {
            BlacklistClientTheme {
                BlacklistQueryScreen(
                    uiState = BlacklistQueryUiState(),
                    onTargetHashChanged = {},
                    onSubmitClick = {},
                    onBackClick = {},
                )
            }
        }

        composeTestRule.onNodeWithTag(BlacklistQueryTestTags.TARGET_HASH_INPUT).assertIsDisplayed()
        composeTestRule.onNodeWithTag(BlacklistQueryTestTags.SUBMIT_BUTTON).assertIsDisplayed()
        composeTestRule.onNodeWithTag(BlacklistQueryTestTags.BACK_BUTTON).assertIsDisplayed()
    }

    @Test
    fun successState_showsFeatureMatches() {
        composeTestRule.setContent {
            BlacklistClientTheme {
                BlacklistQueryScreen(
                    uiState = BlacklistQueryUiState(
                        targetHash = "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef",
                        lastQueriedHash = "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef",
                        features = listOf("Aggressive", "No-Show"),
                    ),
                    onTargetHashChanged = {},
                    onSubmitClick = {},
                    onBackClick = {},
                )
            }
        }

        composeTestRule.onNodeWithTag(BlacklistQueryTestTags.RESULTS_CARD).assertIsDisplayed()
        composeTestRule.onNodeWithText("Aggressive", substring = true).assertIsDisplayed()
        composeTestRule.onNodeWithText("No-Show", substring = true).assertIsDisplayed()
    }
}

