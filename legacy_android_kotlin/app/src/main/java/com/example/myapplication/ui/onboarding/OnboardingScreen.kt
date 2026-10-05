package com.example.myapplication.ui.onboarding

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.TextButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.myapplication.R
import com.example.myapplication.ui.CooldownNotice
import com.example.myapplication.ui.theme.BlacklistClientTheme

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OnboardingScreen(
    uiState: OnboardingUiState,
    onAdUrlChanged: (String) -> Unit,
    onStartVerificationClick: () -> Unit,
    onOtpChanged: (String) -> Unit,
    onVerifyOtpClick: () -> Unit,
    onStartNewChallengeClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val adUrlInputDescription = stringResource(R.string.cd_ad_url_input)
    val startVerificationDescription = stringResource(R.string.cd_start_verification_button)
    val otpInputDescription = stringResource(R.string.cd_otp_input)
    val verifyOtpDescription = stringResource(R.string.cd_verify_otp_button)
    val retryableErrorHint = stringResource(R.string.retryable_error_hint)
    val challengeLockedHint = stringResource(R.string.challenge_locked_hint)

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
                text = stringResource(R.string.onboarding_title),
                style = MaterialTheme.typography.headlineMedium,
            )
            Text(
                text = stringResource(R.string.onboarding_body),
                style = MaterialTheme.typography.bodyLarge,
            )
            if (!uiState.hasInitiatedChallenge && !uiState.isVerified) {
                OutlinedTextField(
                    value = uiState.adUrl,
                    onValueChange = onAdUrlChanged,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag(OnboardingTestTags.AD_URL_INPUT)
                        .semantics {
                            contentDescription = adUrlInputDescription
                        },
                    isError = uiState.adUrlError != null,
                    label = { Text(text = stringResource(R.string.ad_url_label)) },
                    placeholder = { Text(text = stringResource(R.string.ad_url_placeholder)) },
                    supportingText = {
                        Text(
                            text = uiState.adUrlError ?: stringResource(R.string.ad_url_supporting_text),
                        )
                    },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                )
            }
            uiState.generalError?.let { errorMessage ->
                Text(
                    text = errorMessage,
                    modifier = Modifier
                        .testTag(OnboardingTestTags.ERROR_REGION)
                        .semantics {
                            liveRegion = LiveRegionMode.Assertive
                        },
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            if (uiState.generalError != null && uiState.isRetryableError) {
                Text(
                    text = retryableErrorHint,
                    modifier = Modifier.semantics {
                        liveRegion = LiveRegionMode.Polite
                    },
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            if (uiState.isChallengeLocked) {
                Text(
                    text = challengeLockedHint,
                    modifier = Modifier.semantics {
                        liveRegion = LiveRegionMode.Assertive
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }
            CooldownNotice(uiState.retryAfterSeconds)
            if (!uiState.hasInitiatedChallenge && !uiState.isVerified) {
                Button(
                    onClick = onStartVerificationClick,
                    enabled = uiState.adUrl.isNotBlank() && !uiState.isSubmitting && !uiState.isVerifying && uiState.retryAfterSeconds == 0L,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag(OnboardingTestTags.START_VERIFICATION_BUTTON)
                        .semantics {
                            contentDescription = startVerificationDescription
                        },
                ) {
                    if (uiState.isSubmitting) {
                        CircularProgressIndicator(
                            modifier = Modifier.padding(end = 12.dp),
                            strokeWidth = 2.dp,
                        )
                        Text(text = stringResource(R.string.start_verification_loading))
                    } else {
                        Text(text = stringResource(R.string.start_verification))
                    }
                }
            }
            if (uiState.hasInitiatedChallenge) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag(OnboardingTestTags.OTP_CARD)
                        .semantics {
                            liveRegion = LiveRegionMode.Polite
                        },
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
                            text = stringResource(R.string.challenge_ready_title),
                            style = MaterialTheme.typography.titleMedium,
                        )
                        Text(
                            text = stringResource(
                                R.string.challenge_ready_body,
                                uiState.maskedPhoneNumber.orEmpty(),
                            ),
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        Text(
                            text = stringResource(
                                R.string.challenge_expires_at,
                                uiState.otpExpiresAt.orEmpty(),
                            ),
                            style = MaterialTheme.typography.bodySmall,
                        )
                        Text(
                            text = stringResource(R.string.challenge_next_step_hint),
                            style = MaterialTheme.typography.bodySmall,
                        )
                        uiState.challengeRetryGuidance?.let { guidance ->
                            Text(
                                text = guidance,
                                modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                        OutlinedTextField(
                            value = uiState.otp,
                            onValueChange = onOtpChanged,
                            enabled = !uiState.isVerifying && !uiState.isSubmitting && !uiState.isChallengeLocked,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag(OnboardingTestTags.OTP_INPUT)
                                .semantics {
                                    contentDescription = otpInputDescription
                                },
                            isError = uiState.otpError != null,
                            label = { Text(text = stringResource(R.string.otp_label)) },
                            placeholder = { Text(text = stringResource(R.string.otp_placeholder)) },
                            supportingText = {
                                Text(
                                    text = uiState.otpError ?: stringResource(R.string.otp_supporting_text),
                                    modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                                )
                            },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                        )
                        Button(
                            onClick = onVerifyOtpClick,
                            enabled = uiState.otp.length == 6 && !uiState.isVerifying && !uiState.isSubmitting && !uiState.isChallengeLocked && uiState.retryAfterSeconds == 0L,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag(OnboardingTestTags.VERIFY_OTP_BUTTON)
                                .semantics {
                                    contentDescription = verifyOtpDescription
                                },
                        ) {
                            if (uiState.isVerifying) {
                                CircularProgressIndicator(
                                    modifier = Modifier.padding(end = 12.dp),
                                    strokeWidth = 2.dp,
                                )
                                Text(text = stringResource(R.string.verify_otp_loading))
                            } else {
                                Text(text = stringResource(R.string.verify_otp_button))
                            }
                        }
                        TextButton(
                            onClick = onStartNewChallengeClick,
                            enabled = !uiState.isSubmitting && !uiState.isVerifying && uiState.retryAfterSeconds == 0L,
                            modifier = Modifier.fillMaxWidth().testTag(OnboardingTestTags.NEW_CHALLENGE_BUTTON),
                        ) {
                            Text(text = stringResource(R.string.start_new_challenge))
                        }
                    }
                }
            }
            uiState.debugSummary?.let { debugSummary ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                    ),
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Text(
                            text = "Debug diagnostics",
                            style = MaterialTheme.typography.titleMedium,
                        )
                        Text(
                            text = debugSummary,
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }
            }
            if (uiState.isVerified) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag(OnboardingTestTags.VERIFIED_CARD)
                        .semantics {
                            liveRegion = LiveRegionMode.Assertive
                        },
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer,
                    ),
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Text(
                            text = stringResource(R.string.verification_success_title),
                            style = MaterialTheme.typography.titleMedium,
                        )
                        Text(
                            text = stringResource(
                                R.string.verification_success_body,
                                uiState.maskedPhoneNumber.orEmpty(),
                            ),
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        Text(
                            text = stringResource(
                                R.string.verification_verified_at,
                                uiState.verifiedAt.orEmpty(),
                            ),
                            style = MaterialTheme.typography.bodySmall,
                        )
                        Text(
                            text = stringResource(R.string.verification_success_hint),
                            style = MaterialTheme.typography.bodySmall,
                            textAlign = TextAlign.Start,
                        )
                    }
                }
            }
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        text = stringResource(R.string.security_foundation_title),
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Text(
                        text = stringResource(R.string.security_foundation_body),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Text(
                        text = stringResource(R.string.slice_status),
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun OnboardingScreenPreview() {
    BlacklistClientTheme {
        OnboardingScreen(
            uiState = OnboardingUiState(
                adUrl = "https://example.com/escort/miriam",
                otp = "123456",
                maskedPhoneNumber = "+421***456",
                otpExpiresAt = "2026-04-01T12:15:00+00:00",
                challengeId = "0d5f35ea-331d-4df1-b9d6-3df7ab7fdc7c",
            ),
            onAdUrlChanged = {},
            onStartVerificationClick = {},
            onOtpChanged = {},
            onVerifyOtpClick = {},
            onStartNewChallengeClick = {},
        )
    }
}

