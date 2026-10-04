package com.example.myapplication.testing

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.example.myapplication.MainActivity
import com.example.myapplication.MainActivityDependencies
import com.example.myapplication.data.*
import com.example.myapplication.security.PublicKeyPemEncoder
import com.example.myapplication.security.SignedRequestFactory
import com.example.myapplication.security.SignedRequestPayload
import com.example.myapplication.session.AndroidBindingMetadataStore
import com.example.myapplication.session.ExistingKey
import com.example.myapplication.session.SessionRecovery
import com.example.myapplication.ui.onboarding.OnboardingViewModel
import com.example.myapplication.ui.query.BlacklistQueryViewModel
import com.example.myapplication.ui.reporting.ReportViewModel

/** Debug-only production navigation harness. No network client, keystore or private key. */
class RecoveryHarnessActivity : MainActivity() {
    lateinit var recovery: SessionRecovery
        private set
    var rejectQuery = false

    override fun createDependencies(): MainActivityDependencies {
        if (intent.getBooleanExtra("seed", false)) RecoveryHarnessFixtures.seed(this)
        recovery = RecoveryHarnessFixtures.sharedRecovery(this)
        val signer = object : SignedRequestFactory {
            override fun createVerifyRequest(challengeId: String) = SignedRequestPayload(RecoveryHarnessFixtures.pem, "synthetic")
            override fun createReportRequest(clientPhoneNumber: String, feature: String) = error("Fake repository does not sign")
            override fun createBlacklistCheckRequest(targetHash: String) = error("Fake repository does not sign")
        }
        val auth = object : AuthRepository {
            override suspend fun initiateAuth(adUrl: String): InitiateAuthResult {
                RecoveryHarnessFixtures.count(this@RecoveryHarnessActivity, "initiate")
                return InitiateAuthResult.Success("synthetic", "masked", "2030-01-01T00:15:00Z")
            }
            override suspend fun verifyAuth(challengeId: String, otp: String, publicKey: String, signature: String): VerifyAuthResult {
                RecoveryHarnessFixtures.count(this@RecoveryHarnessActivity, "verify")
                return VerifyAuthResult.Success(challengeId, "masked", "2030-01-01T00:00:00Z")
            }
        }
        val reports = object : ReportRepository {
            override suspend fun submitReport(clientPhoneNumber: String, feature: ReportingFeature): ReportSubmissionResult {
                RecoveryHarnessFixtures.count(this@RecoveryHarnessActivity, "report")
                return ReportSubmissionResult.Failure("synthetic_unavailable", "Synthetic fixture unavailable", retryable = true)
            }
        }
        val queries = object : BlacklistQueryRepository {
            override suspend fun checkTargetHash(targetHash: String): BlacklistQueryResult {
                RecoveryHarnessFixtures.count(this@RecoveryHarnessActivity, "query")
                if (rejectQuery) recovery.authorizationRejected(RecoveryHarnessFixtures.pem)
                return BlacklistQueryResult.Failure(
                    if (rejectQuery) "blacklist_query_unauthorized" else "synthetic_unavailable",
                    "Synthetic query failure", retryable = !rejectQuery,
                )
            }
        }
        return MainActivityDependencies(
            factory { OnboardingViewModel(auth, signer, diagnosticsEnabled = false, sessionRecovery = recovery) },
            factory { ReportViewModel(reports) },
            factory { BlacklistQueryViewModel(queries) },
            recovery::refresh,
        )
    }

    private fun factory(create: () -> ViewModel) = object : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T = create() as T
    }
}

object RecoveryHarnessFixtures {
    const val STORE = "cb01_fixture_recovery"
    const val COUNTERS = "cb01_fixture_requests"
    val pem = """
        -----BEGIN PUBLIC KEY-----
        MFkwEwYHKoZIzj0CAQYIKoZIzj0DAQcDQgAETsqqw6aY9+urntILLtAYO4b23nM1pruE2aiqfMk9pujJTHxGoraHWT84vfZEpg8dbV/AUv4BcqebBN2bXE6oqw==
        -----END PUBLIC KEY-----
    """.trimIndent()
    private val publicKey = PublicKeyPemEncoder.fromPem(pem)
    private var shared: SessionRecovery? = null

    fun recovery(context: Context) = SessionRecovery(
        AndroidBindingMetadataStore(context, STORE), { ExistingKey.Available(publicKey) }, "synthetic-offline-harness",
    )

    fun sharedRecovery(context: Context): SessionRecovery =
        shared ?: recovery(context).also { shared = it }

    fun seed(context: Context) {
        clear(context)
        recovery(context).recordVerified(pem, "2030-01-01T00:00:00Z")
    }

    fun clear(context: Context) {
        shared = null
        check(AndroidBindingMetadataStore(context, STORE).clear())
        check(context.getSharedPreferences(COUNTERS, Context.MODE_PRIVATE).edit().clear().commit())
    }

    fun count(context: Context, command: String) {
        val preferences = context.getSharedPreferences(COUNTERS, Context.MODE_PRIVATE)
        check(preferences.edit().putInt(command, preferences.getInt(command, 0) + 1).commit())
    }

    fun requests(context: Context, command: String): Int =
        context.getSharedPreferences(COUNTERS, Context.MODE_PRIVATE).getInt(command, 0)
}
