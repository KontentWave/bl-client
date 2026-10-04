package com.example.myapplication.ui.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.myapplication.R
import com.example.myapplication.shield.ShieldReadiness
import com.example.myapplication.shield.ShieldReadinessEvaluator
import com.example.myapplication.shield.ShieldOverlayState
import com.example.myapplication.shield.ShieldLiveStage
import com.example.myapplication.shield.ShieldLiveStatus
import com.example.myapplication.shield.ShieldRequirement
import com.example.myapplication.shield.ShieldStatus
import com.example.myapplication.ui.theme.BlacklistClientTheme

@Composable
fun HomeScreen(
    maskedPhoneNumber: String,
    verifiedAt: String,
    shieldReadiness: ShieldReadiness,
    shieldLiveStatus: ShieldLiveStatus,
    onOpenReportingClick: () -> Unit,
    onOpenQueryClick: () -> Unit,
    onRequestShieldPhonePermissionsClick: () -> Unit,
    onOpenOverlayPermissionClick: () -> Unit,
    onRefreshShieldStatusClick: () -> Unit,
    onRestartOnboardingClick: () -> Unit,
    modifier: Modifier = Modifier,
    isSessionRestored: Boolean = false,
    recoveryMessage: String? = null,
) {
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
                text = stringResource(if (isSessionRestored) R.string.home_recovered_workspace_title else R.string.home_title),
                style = MaterialTheme.typography.headlineMedium,
            )
            Text(
                text = stringResource(R.string.home_body),
                style = MaterialTheme.typography.bodyLarge,
            )
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag(HomeTestTags.VERIFIED_SUMMARY_CARD)
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
                        text = stringResource(if (isSessionRestored) R.string.home_recovered_title else R.string.home_verified_title),
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Text(
                        text = if (isSessionRestored) stringResource(R.string.home_recovered_body)
                            else stringResource(R.string.home_verified_body, maskedPhoneNumber),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Text(
                        text = stringResource(R.string.home_verified_at, verifiedAt),
                        style = MaterialTheme.typography.bodySmall,
                    )
                    if (recoveryMessage != null) {
                        Text(text = recoveryMessage, color = MaterialTheme.colorScheme.error)
                    }
                    TextButton(
                        onClick = onRestartOnboardingClick,
                        modifier = Modifier.testTag(HomeTestTags.RESTART_ONBOARDING_BUTTON),
                    ) {
                        Text(text = stringResource(R.string.home_restart_onboarding))
                    }
                }
            }
            ShieldStatusCard(
                shieldReadiness = shieldReadiness,
                onRequestShieldPhonePermissionsClick = onRequestShieldPhonePermissionsClick,
                onOpenOverlayPermissionClick = onOpenOverlayPermissionClick,
                onRefreshShieldStatusClick = onRefreshShieldStatusClick,
            )
            ShieldLiveStatusCard(shieldLiveStatus = shieldLiveStatus)
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag(HomeTestTags.REPORTING_SLICE_CARD),
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        text = stringResource(R.string.home_reporting_title),
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Text(
                        text = stringResource(R.string.home_reporting_body),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    TextButton(
                        onClick = onOpenReportingClick,
                        modifier = Modifier.testTag(HomeTestTags.OPEN_REPORTING_BUTTON),
                    ) {
                        Text(text = stringResource(R.string.home_open_reporting))
                    }
                }
            }
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag(HomeTestTags.QUERY_SLICE_CARD),
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        text = stringResource(R.string.home_query_title),
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Text(
                        text = stringResource(R.string.home_query_body),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    TextButton(
                        onClick = onOpenQueryClick,
                        modifier = Modifier.testTag(HomeTestTags.OPEN_QUERY_BUTTON),
                    ) {
                        Text(text = stringResource(R.string.home_open_query))
                    }
                }
            }
        }
    }
}

@Composable
private fun ShieldLiveStatusCard(
    shieldLiveStatus: ShieldLiveStatus,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag(HomeTestTags.SHIELD_LIVE_STATUS_CARD),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = stringResource(R.string.home_shield_live_title),
                style = MaterialTheme.typography.titleMedium,
            )
            Text(
                text = stringResource(stageTitleRes(shieldLiveStatus.stage)),
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.testTag(HomeTestTags.SHIELD_LIVE_STATUS_TITLE),
            )
            Text(
                text = formatShieldLiveStatusBody(shieldLiveStatus),
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.testTag(HomeTestTags.SHIELD_LIVE_STATUS_BODY),
            )
        }
    }
}

@Composable
private fun ShieldStatusCard(
    shieldReadiness: ShieldReadiness,
    onRequestShieldPhonePermissionsClick: () -> Unit,
    onOpenOverlayPermissionClick: () -> Unit,
    onRefreshShieldStatusClick: () -> Unit,
) {
    val cardColor = when (shieldReadiness.status) {
        ShieldStatus.Active -> MaterialTheme.colorScheme.primaryContainer
        ShieldStatus.ActionRequired -> MaterialTheme.colorScheme.tertiaryContainer
        ShieldStatus.Blocked -> MaterialTheme.colorScheme.errorContainer
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag(HomeTestTags.SHIELD_STATUS_CARD)
            .semantics {
                liveRegion = LiveRegionMode.Polite
            },
        colors = CardDefaults.cardColors(containerColor = cardColor),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            val statusBodyText = when (shieldReadiness.status) {
                ShieldStatus.Active -> stringResource(R.string.home_shield_status_active_body)
                ShieldStatus.ActionRequired -> stringResource(
                    R.string.home_shield_status_action_required_body,
                    formatMissingRequirements(shieldReadiness.missingRequirements),
                )
                ShieldStatus.Blocked -> stringResource(
                    R.string.home_shield_status_blocked_body,
                    formatMissingRequirements(shieldReadiness.missingRequirements),
                )
            }

            Text(
                text = stringResource(R.string.home_shield_title),
                style = MaterialTheme.typography.titleMedium,
            )
            Text(
                text = stringResource(statusTitleRes(shieldReadiness.status)),
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.testTag(HomeTestTags.SHIELD_STATUS_TITLE),
            )
            Text(
                text = statusBodyText,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.testTag(HomeTestTags.SHIELD_STATUS_BODY),
            )

            if (shieldReadiness.shouldRequestPhonePermissions) {
                Button(
                    onClick = onRequestShieldPhonePermissionsClick,
                    modifier = Modifier.testTag(HomeTestTags.REQUEST_SHIELD_PHONE_PERMISSIONS_BUTTON),
                ) {
                    Text(text = stringResource(R.string.home_shield_request_phone_permissions))
                }
            }

            if (shieldReadiness.shouldOpenOverlaySettings) {
                Button(
                    onClick = onOpenOverlayPermissionClick,
                    modifier = Modifier.testTag(HomeTestTags.OPEN_OVERLAY_PERMISSION_BUTTON),
                ) {
                    Text(text = stringResource(R.string.home_shield_open_overlay_settings))
                }
            }

            TextButton(
                onClick = onRefreshShieldStatusClick,
                modifier = Modifier.testTag(HomeTestTags.REFRESH_SHIELD_STATUS_BUTTON),
            ) {
                Text(text = stringResource(R.string.home_shield_refresh_status))
            }
        }
    }
}

@Composable
private fun formatMissingRequirements(missingRequirements: List<ShieldRequirement>): String {
    val context = LocalContext.current

    if (missingRequirements.isEmpty()) {
        return stringResource(R.string.home_shield_missing_requirements_none)
    }

    return missingRequirements.joinToString(separator = ", ") { requirement ->
        context.getString(
            when (requirement) {
                ShieldRequirement.PhoneState -> R.string.home_shield_requirement_phone_state
                ShieldRequirement.CallLog -> R.string.home_shield_requirement_call_log
                ShieldRequirement.Overlay -> R.string.home_shield_requirement_overlay
            }
        )
    }
}

private fun statusTitleRes(status: ShieldStatus): Int = when (status) {
    ShieldStatus.Active -> R.string.home_shield_status_active_title
    ShieldStatus.ActionRequired -> R.string.home_shield_status_action_required_title
    ShieldStatus.Blocked -> R.string.home_shield_status_blocked_title
}

private fun stageTitleRes(stage: ShieldLiveStage): Int = when (stage) {
    ShieldLiveStage.Idle -> R.string.home_shield_live_stage_idle_title
    ShieldLiveStage.RingingDetected -> R.string.home_shield_live_stage_ringing_title
    ShieldLiveStage.MissingIncomingNumber -> R.string.home_shield_live_stage_missing_number_title
    ShieldLiveStage.NormalizationFailed -> R.string.home_shield_live_stage_normalization_failed_title
    ShieldLiveStage.Querying -> R.string.home_shield_live_stage_querying_title
    ShieldLiveStage.NoMatch -> R.string.home_shield_live_stage_no_match_title
    ShieldLiveStage.MatchFound -> R.string.home_shield_live_stage_match_found_title
    ShieldLiveStage.QueryFailed -> R.string.home_shield_live_stage_query_failed_title
}

@Composable
private fun formatShieldLiveStatusBody(shieldLiveStatus: ShieldLiveStatus): String = when (shieldLiveStatus.stage) {
    ShieldLiveStage.Idle -> stringResource(R.string.home_shield_live_stage_idle_body)
    ShieldLiveStage.RingingDetected -> appendOverlayOutcome(
        base = stringResource(
        R.string.home_shield_live_stage_ringing_body,
        shieldLiveStatus.rawIncomingNumber ?: stringResource(R.string.home_shield_live_unknown_value),
        ),
        shieldLiveStatus = shieldLiveStatus,
    )
    ShieldLiveStage.MissingIncomingNumber -> appendOverlayOutcome(
        base = stringResource(
        R.string.home_shield_live_stage_missing_number_body,
        shieldLiveStatus.errorMessage ?: stringResource(R.string.home_shield_live_unknown_value),
        ),
        shieldLiveStatus = shieldLiveStatus,
    )
    ShieldLiveStage.NormalizationFailed -> appendOverlayOutcome(
        base = stringResource(
        R.string.home_shield_live_stage_normalization_failed_body,
        shieldLiveStatus.rawIncomingNumber ?: stringResource(R.string.home_shield_live_unknown_value),
        shieldLiveStatus.errorMessage ?: stringResource(R.string.home_shield_live_unknown_value),
        ),
        shieldLiveStatus = shieldLiveStatus,
    )
    ShieldLiveStage.Querying -> appendOverlayOutcome(
        base = stringResource(
        R.string.home_shield_live_stage_querying_body,
        shieldLiveStatus.normalizedNumber ?: stringResource(R.string.home_shield_live_unknown_value),
        shieldLiveStatus.targetHash ?: stringResource(R.string.home_shield_live_unknown_value),
        ),
        shieldLiveStatus = shieldLiveStatus,
    )
    ShieldLiveStage.NoMatch -> appendOverlayOutcome(
        base = stringResource(
        R.string.home_shield_live_stage_no_match_body,
        shieldLiveStatus.normalizedNumber ?: stringResource(R.string.home_shield_live_unknown_value),
        shieldLiveStatus.targetHash ?: stringResource(R.string.home_shield_live_unknown_value),
        ),
        shieldLiveStatus = shieldLiveStatus,
    )
    ShieldLiveStage.MatchFound -> appendOverlayOutcome(
        base = stringResource(
        R.string.home_shield_live_stage_match_found_body,
        shieldLiveStatus.normalizedNumber ?: stringResource(R.string.home_shield_live_unknown_value),
        shieldLiveStatus.features.joinToString().ifBlank { stringResource(R.string.home_shield_live_unknown_value) },
        ),
        shieldLiveStatus = shieldLiveStatus,
    )
    ShieldLiveStage.QueryFailed -> appendOverlayOutcome(
        base = stringResource(
        R.string.home_shield_live_stage_query_failed_body,
        shieldLiveStatus.targetHash ?: stringResource(R.string.home_shield_live_unknown_value),
        shieldLiveStatus.errorMessage ?: stringResource(R.string.home_shield_live_unknown_value),
        ),
        shieldLiveStatus = shieldLiveStatus,
    )
}

@Composable
private fun appendOverlayOutcome(
    base: String,
    shieldLiveStatus: ShieldLiveStatus,
): String {
    val overlaySummary = formatOverlayOutcome(shieldLiveStatus)
    return if (overlaySummary == null) {
        base
    } else {
        "$base\n\n$overlaySummary"
    }
}

@Composable
private fun formatOverlayOutcome(shieldLiveStatus: ShieldLiveStatus): String? = when (shieldLiveStatus.overlayState) {
    ShieldOverlayState.None -> null
    ShieldOverlayState.Shown -> stringResource(
        R.string.home_shield_overlay_state_shown,
        shieldLiveStatus.overlayMessage ?: stringResource(R.string.home_shield_live_unknown_value),
    )
    ShieldOverlayState.Dismissed -> stringResource(
        R.string.home_shield_overlay_state_dismissed,
        shieldLiveStatus.overlayMessage ?: stringResource(R.string.home_shield_live_unknown_value),
    )
    ShieldOverlayState.SkippedPermission -> stringResource(
        R.string.home_shield_overlay_state_skipped_permission,
        shieldLiveStatus.overlayMessage ?: stringResource(R.string.home_shield_live_unknown_value),
    )
    ShieldOverlayState.Failed -> stringResource(
        R.string.home_shield_overlay_state_failed,
        shieldLiveStatus.overlayMessage ?: stringResource(R.string.home_shield_live_unknown_value),
    )
}

@Preview(showBackground = true)
@Composable
private fun HomeScreenPreview() {
    BlacklistClientTheme {
        HomeScreen(
            maskedPhoneNumber = "+421***456",
            verifiedAt = "2026-04-07T13:48:23+00:00",
            shieldReadiness = ShieldReadinessEvaluator.evaluate(
                isPhoneStatePermissionGranted = true,
                isCallLogPermissionGranted = false,
                isOverlayPermissionGranted = false,
            ),
            shieldLiveStatus = ShieldLiveStatus(
                stage = ShieldLiveStage.MatchFound,
                rawIncomingNumber = "+421903223183",
                normalizedNumber = "+421903223183",
                targetHash = "8f1c9c1d...",
                features = listOf("Aggressive"),
                overlayState = ShieldOverlayState.Shown,
                overlayMessage = "Overlay warning shown.",
            ),
            onOpenReportingClick = {},
            onOpenQueryClick = {},
            onRequestShieldPhonePermissionsClick = {},
            onOpenOverlayPermissionClick = {},
            onRefreshShieldStatusClick = {},
            onRestartOnboardingClick = {},
        )
    }
}
