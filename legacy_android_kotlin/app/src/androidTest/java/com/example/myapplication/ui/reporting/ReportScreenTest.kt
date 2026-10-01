package com.example.myapplication.ui.reporting

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import com.example.myapplication.data.ReportingFeature
import com.example.myapplication.ui.theme.BlacklistClientTheme
import org.junit.Rule
import org.junit.Test

class ReportScreenTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun initialState_showsInputFeatureOptionsAndSubmitButton() {
        composeTestRule.setContent {
            BlacklistClientTheme {
                ReportScreen(
                    uiState = ReportUiState(),
                    onClientPhoneNumberChanged = {},
                    onFeatureSelected = {},
                    onSubmitClick = {},
                    onBackClick = {},
                )
            }
        }

        composeTestRule.onNodeWithTag(ReportTestTags.CLIENT_PHONE_INPUT).assertIsDisplayed()
        composeTestRule.onNodeWithTag(ReportTestTags.FEATURE_OPTION_PREFIX + ReportingFeature.NO_SHOW.backendKey).assertIsDisplayed()
        composeTestRule.onNodeWithTag(ReportTestTags.SUBMIT_BUTTON).assertIsDisplayed()
        composeTestRule.onNodeWithTag(ReportTestTags.BACK_BUTTON).assertIsDisplayed()
    }

    @Test
    fun successState_showsAcceptedSummary() {
        composeTestRule.setContent {
            BlacklistClientTheme {
                ReportScreen(
                    uiState = ReportUiState(
                        clientPhoneNumber = "+421900123456",
                        selectedFeature = ReportingFeature.NO_SHOW,
                        lastSubmittedFeatureLabel = "No-Show",
                        lastSubmittedLevel = "level_1",
                        lastUniqueReporterCount = 1,
                        lastReadyForSync = false,
                    ),
                    onClientPhoneNumberChanged = {},
                    onFeatureSelected = {},
                    onSubmitClick = {},
                    onBackClick = {},
                )
            }
        }

        composeTestRule.onNodeWithTag(ReportTestTags.SUCCESS_CARD).assertIsDisplayed()
        composeTestRule.onNodeWithText("No-Show", substring = true).assertIsDisplayed()
        composeTestRule.onNodeWithText("level_1", substring = true).assertIsDisplayed()
    }
}

