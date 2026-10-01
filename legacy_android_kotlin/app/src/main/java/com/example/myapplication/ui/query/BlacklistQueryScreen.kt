package com.example.myapplication.ui.query

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
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
import com.example.myapplication.ui.theme.BlacklistClientTheme

@Composable
fun BlacklistQueryScreen(
    uiState: BlacklistQueryUiState,
    onTargetHashChanged: (String) -> Unit,
    onSubmitClick: () -> Unit,
    onBackClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val targetHashInputDescription = stringResource(R.string.cd_blacklist_target_hash_input)

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
                text = stringResource(R.string.blacklist_query_title),
                style = MaterialTheme.typography.headlineMedium,
            )
            Text(
                text = stringResource(R.string.blacklist_query_body),
                style = MaterialTheme.typography.bodyLarge,
            )
            TextButton(
                onClick = onBackClick,
                modifier = Modifier.testTag(BlacklistQueryTestTags.BACK_BUTTON),
            ) {
                Text(text = stringResource(R.string.blacklist_query_back_button))
            }
            OutlinedTextField(
                value = uiState.targetHash,
                onValueChange = onTargetHashChanged,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag(BlacklistQueryTestTags.TARGET_HASH_INPUT)
                    .semantics {
                        contentDescription = targetHashInputDescription
                    },
                label = { Text(text = stringResource(R.string.blacklist_query_target_hash_label)) },
                placeholder = { Text(text = stringResource(R.string.blacklist_query_target_hash_placeholder)) },
                supportingText = {
                    Text(
                        text = uiState.targetHashError
                            ?: stringResource(R.string.blacklist_query_target_hash_supporting_text),
                    )
                },
                isError = uiState.targetHashError != null,
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Ascii),
            )
            uiState.generalError?.let { errorMessage ->
                Text(
                    text = errorMessage,
                    modifier = Modifier
                        .testTag(BlacklistQueryTestTags.ERROR_REGION)
                        .semantics { liveRegion = LiveRegionMode.Assertive },
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            Button(
                onClick = onSubmitClick,
                enabled = !uiState.isSubmitting,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag(BlacklistQueryTestTags.SUBMIT_BUTTON),
            ) {
                if (uiState.isSubmitting) {
                    CircularProgressIndicator(
                        modifier = Modifier.padding(end = 12.dp),
                        strokeWidth = 2.dp,
                    )
                    Text(text = stringResource(R.string.blacklist_query_submit_loading))
                } else {
                    Text(text = stringResource(R.string.blacklist_query_submit_button))
                }
            }
            if (uiState.hasResult) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag(BlacklistQueryTestTags.RESULTS_CARD)
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
                            text = stringResource(R.string.blacklist_query_results_title),
                            style = MaterialTheme.typography.titleMedium,
                        )
                        Text(
                            text = stringResource(
                                R.string.blacklist_query_results_hash,
                                uiState.lastQueriedHash.orEmpty(),
                            ),
                            style = MaterialTheme.typography.bodySmall,
                        )
                        if (uiState.hasFeatureMatches) {
                            Text(
                                text = stringResource(R.string.blacklist_query_matches_title),
                                style = MaterialTheme.typography.bodyMedium,
                            )
                            uiState.features.forEach { feature ->
                                Text(
                                    text = "• $feature",
                                    style = MaterialTheme.typography.bodyMedium,
                                )
                            }
                        } else {
                            Text(
                                text = stringResource(R.string.blacklist_query_no_matches),
                                style = MaterialTheme.typography.bodyMedium,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun BlacklistQueryScreenPreview() {
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

