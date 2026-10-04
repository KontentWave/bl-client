package com.example.myapplication

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.myapplication.shield.ShieldPermissionChecker
import com.example.myapplication.shield.ShieldLiveStatusStore
import com.example.myapplication.ui.home.HomeScreen
import com.example.myapplication.ui.onboarding.OnboardingScreen
import com.example.myapplication.ui.onboarding.OnboardingViewModel
import com.example.myapplication.ui.query.BlacklistQueryScreen
import com.example.myapplication.ui.query.BlacklistQueryViewModel
import com.example.myapplication.ui.reporting.ReportScreen
import com.example.myapplication.ui.reporting.ReportViewModel
import com.example.myapplication.ui.theme.BlacklistClientTheme
import androidx.lifecycle.ViewModelProvider
import com.example.myapplication.session.SessionRecoveryProvider

data class MainActivityDependencies(
    val onboardingFactory: ViewModelProvider.Factory,
    val reportFactory: ViewModelProvider.Factory,
    val queryFactory: ViewModelProvider.Factory,
    val refreshRecovery: () -> Unit,
)

open class MainActivity : ComponentActivity() {
    private val dependencies by lazy { createDependencies() }

    protected open fun createDependencies(): MainActivityDependencies {
        val recovery = SessionRecoveryProvider.get(applicationContext)
        return MainActivityDependencies(
            OnboardingViewModel.factory(recovery),
            ReportViewModel.factory(recovery),
            BlacklistQueryViewModel.factory(recovery),
            recovery::refresh,
        )
    }

    private val onboardingViewModel by viewModels<OnboardingViewModel> {
        dependencies.onboardingFactory
    }
    private val reportViewModel by viewModels<ReportViewModel> {
        dependencies.reportFactory
    }
    private val blacklistQueryViewModel by viewModels<BlacklistQueryViewModel> {
        dependencies.queryFactory
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val context = LocalContext.current
            val lifecycleOwner = LocalLifecycleOwner.current
            val uiState by onboardingViewModel.uiState.collectAsState()
            val reportUiState by reportViewModel.uiState.collectAsState()
            val blacklistQueryUiState by blacklistQueryViewModel.uiState.collectAsState()
            val shieldPermissionChecker = remember { ShieldPermissionChecker() }
            val shieldLiveStatusStore = remember { ShieldLiveStatusStore(context) }
            var shieldReadiness by remember {
                mutableStateOf(shieldPermissionChecker.getReadiness(context))
            }
            var shieldLiveStatus by remember {
                mutableStateOf(shieldLiveStatusStore.read())
            }
            val shieldPhonePermissionsLauncher = rememberLauncherForActivityResult(
                contract = ActivityResultContracts.RequestMultiplePermissions(),
            ) {
                shieldReadiness = shieldPermissionChecker.getReadiness(context)
                shieldLiveStatus = shieldLiveStatusStore.read()
            }

            BlacklistClientTheme {
                var verifiedDestination by remember { mutableStateOf(VerifiedDestination.HOME) }

                LaunchedEffect(uiState.isVerified) {
                    if (!uiState.isVerified) {
                        verifiedDestination = VerifiedDestination.HOME
                    }
                }

                DisposableEffect(lifecycleOwner, context, uiState.isVerified) {
                    val observer = LifecycleEventObserver { _, event ->
                        if (event == Lifecycle.Event.ON_RESUME) {
                            dependencies.refreshRecovery()
                            shieldReadiness = shieldPermissionChecker.getReadiness(context)
                            shieldLiveStatus = shieldLiveStatusStore.read()
                        }
                    }

                    lifecycleOwner.lifecycle.addObserver(observer)
                    onDispose {
                        lifecycleOwner.lifecycle.removeObserver(observer)
                    }
                }

                Surface(modifier = Modifier.fillMaxSize()) {
                    if (uiState.isVerified) {
                        when (verifiedDestination) {
                            VerifiedDestination.HOME -> {
                                HomeScreen(
                                    maskedPhoneNumber = uiState.maskedPhoneNumber.orEmpty(),
                                    verifiedAt = uiState.verifiedAt.orEmpty(),
                                    isSessionRestored = uiState.isSessionRestored,
                                    recoveryMessage = uiState.generalError,
                                    shieldReadiness = shieldReadiness,
                                    shieldLiveStatus = shieldLiveStatus,
                                    onOpenReportingClick = {
                                        verifiedDestination = VerifiedDestination.REPORTING
                                    },
                                    onOpenQueryClick = {
                                        verifiedDestination = VerifiedDestination.QUERY
                                    },
                                    onRequestShieldPhonePermissionsClick = {
                                        shieldPhonePermissionsLauncher.launch(
                                            arrayOf(
                                                Manifest.permission.READ_PHONE_STATE,
                                                Manifest.permission.READ_CALL_LOG,
                                            )
                                        )
                                    },
                                    onOpenOverlayPermissionClick = {
                                        startActivity(
                                            Intent(
                                                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                                                Uri.parse("package:${context.packageName}"),
                                            )
                                        )
                                    },
                                    onRefreshShieldStatusClick = {
                                        shieldReadiness = shieldPermissionChecker.getReadiness(context)
                                        shieldLiveStatus = shieldLiveStatusStore.read()
                                    },
                                    onRestartOnboardingClick = {
                                        verifiedDestination = VerifiedDestination.HOME
                                        reportViewModel.reset()
                                        blacklistQueryViewModel.reset()
                                        onboardingViewModel.restartOnboarding()
                                    },
                                )
                            }

                            VerifiedDestination.REPORTING -> {
                                ReportScreen(
                                    uiState = reportUiState,
                                    onClientPhoneNumberChanged = reportViewModel::onClientPhoneNumberChanged,
                                    onFeatureSelected = reportViewModel::onFeatureSelected,
                                    onSubmitClick = reportViewModel::submitReport,
                                    onBackClick = {
                                        verifiedDestination = VerifiedDestination.HOME
                                    },
                                )
                            }

                            VerifiedDestination.QUERY -> {
                                BlacklistQueryScreen(
                                    uiState = blacklistQueryUiState,
                                    onTargetHashChanged = blacklistQueryViewModel::onTargetHashChanged,
                                    onSubmitClick = blacklistQueryViewModel::submitQuery,
                                    onBackClick = {
                                        verifiedDestination = VerifiedDestination.HOME
                                    },
                                )
                            }
                        }
                    } else {
                        OnboardingScreen(
                            uiState = uiState,
                            onAdUrlChanged = onboardingViewModel::onAdUrlChanged,
                            onStartVerificationClick = onboardingViewModel::submitAdUrl,
                            onOtpChanged = onboardingViewModel::onOtpChanged,
                            onVerifyOtpClick = onboardingViewModel::submitOtp,
                            onStartNewChallengeClick = onboardingViewModel::startNewChallenge,
                        )
                    }
                }
            }
        }
    }

    private enum class VerifiedDestination {
        HOME,
        REPORTING,
        QUERY,
    }
}
