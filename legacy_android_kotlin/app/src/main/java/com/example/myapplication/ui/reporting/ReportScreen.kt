package com.example.myapplication.ui.reporting

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.myapplication.R
import com.example.myapplication.ui.CooldownNotice
import com.example.myapplication.data.ReportingFeature
import com.example.myapplication.ui.theme.BlacklistClientTheme

@Composable
fun ReportScreen(
    uiState: ReportUiState,
    onClientPhoneNumberChanged: (String) -> Unit,
    onFeatureSelected: (ReportingFeature) -> Unit,
    onSubmitClick: () -> Unit,
    onBackClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val clientPhoneInputDescription = stringResource(R.string.cd_report_client_phone_input)

    Scaffold(modifier = modifier.fillMaxSize()) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(24.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(
                text = stringResource(R.string.report_title),
                style = MaterialTheme.typography.headlineMedium,
            )
            Text(
                text = stringResource(R.string.report_body),
                style = MaterialTheme.typography.bodyLarge,
            )
            TextButton(
                onClick = onBackClick,
                modifier = Modifier.testTag(ReportTestTags.BACK_BUTTON),
            ) {
                Text(text = stringResource(R.string.report_back_button))
            }
            OutlinedTextField(
                value = uiState.clientPhoneNumber,
                onValueChange = onClientPhoneNumberChanged,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag(ReportTestTags.CLIENT_PHONE_INPUT)
                    .semantics {
                        contentDescription = clientPhoneInputDescription
                    },
                label = { Text(text = stringResource(R.string.report_client_phone_label)) },
                placeholder = { Text(text = stringResource(R.string.report_client_phone_placeholder)) },
                supportingText = {
                    Text(
                        text = uiState.clientPhoneNumberError
                            ?: stringResource(R.string.report_client_phone_supporting_text),
                    )
                },
                isError = uiState.clientPhoneNumberError != null,
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
            )
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Text(
                        text = stringResource(R.string.report_feature_title),
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Text(
                        text = uiState.featureError ?: stringResource(R.string.report_feature_supporting_text),
                        color = if (uiState.featureError != null) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodySmall,
                    )
                    uiState.availableFeatures.forEach { feature ->
                        val selected = uiState.selectedFeature == feature
                        RowSelectableFeature(
                            label = feature.displayLabel,
                            selected = selected,
                            modifier = Modifier.testTag(ReportTestTags.FEATURE_OPTION_PREFIX + feature.backendKey),
                            onClick = { onFeatureSelected(feature) },
                        )
                    }
                }
            }
            uiState.generalError?.let { errorMessage ->
                Text(
                    text = errorMessage,
                    modifier = Modifier
                        .testTag(ReportTestTags.ERROR_REGION)
                        .semantics { liveRegion = LiveRegionMode.Assertive },
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            CooldownNotice(uiState.retryAfterSeconds)
            Button(
                onClick = onSubmitClick,
                enabled = !uiState.isSubmitting && uiState.retryAfterSeconds == 0L,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag(ReportTestTags.SUBMIT_BUTTON),
            ) {
                if (uiState.isSubmitting) {
                    CircularProgressIndicator(
                        modifier = Modifier.padding(end = 12.dp),
                        strokeWidth = 2.dp,
                    )
                    Text(text = stringResource(R.string.report_submit_loading))
                } else {
                    Text(text = stringResource(R.string.report_submit_button))
                }
            }
            if (uiState.hasSubmissionSummary) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag(ReportTestTags.SUCCESS_CARD)
                        .semantics { liveRegion = LiveRegionMode.Polite },
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.secondaryContainer,
                    ),
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Text(
                            text = stringResource(R.string.report_success_title),
                            style = MaterialTheme.typography.titleMedium,
                        )
                        Text(
                            text = stringResource(
                                R.string.report_success_body,
                                uiState.lastSubmittedFeatureLabel.orEmpty(),
                            ),
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        Text(
                            text = stringResource(
                                R.string.report_success_count,
                                uiState.lastUniqueReporterCount ?: 0,
                            ),
                            style = MaterialTheme.typography.bodySmall,
                        )
                        Text(
                            text = stringResource(
                                R.string.report_success_level,
                                uiState.lastSubmittedLevel.orEmpty(),
                            ),
                            style = MaterialTheme.typography.bodySmall,
                        )
                        Text(
                            text = stringResource(
                                R.string.report_success_sync,
                                if (uiState.lastReadyForSync == true) stringResource(R.string.report_sync_ready_yes) else stringResource(R.string.report_sync_ready_no),
                            ),
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun RowSelectableFeature(
    label: String,
    selected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    androidx.compose.foundation.layout.Row(
        modifier = modifier
            .fillMaxWidth()
            .selectable(
                selected = selected,
                onClick = onClick,
            ),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(
            selected = selected,
            onClick = null,
        )
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun ReportScreenPreview() {
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


