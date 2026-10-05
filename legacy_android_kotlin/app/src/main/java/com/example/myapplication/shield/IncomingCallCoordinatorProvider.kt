package com.example.myapplication.shield

import android.content.Context
import android.os.SystemClock
import com.example.myapplication.data.BlacklistQueryRepositoryProvider
import com.example.myapplication.session.SessionRecoveryProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

object IncomingCallCoordinatorProvider {
    private var coordinator: IncomingCallCoordinator? = null

    // Manifest receivers run on main; application ownership survives receiver instances.
    fun get(context: Context): IncomingCallCoordinator = coordinator ?: run {
        val appContext = context.applicationContext
        val processor by lazy {
            IncomingCallProcessor(
                BlacklistQueryRepositoryProvider.create(
                    sessionRecovery = SessionRecoveryProvider.get(appContext),
                ),
            )
        }
        IncomingCallCoordinator(
            scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate),
            workerScope = CoroutineScope(SupervisorJob() + Dispatchers.IO),
            lookup = { processor.lookup(it) },
            statusSink = ShieldLiveStatusStore(appContext),
            presenter = WindowManagerShieldWarningPresenter(appContext),
            elapsedMillis = SystemClock::elapsedRealtime,
        ).also { coordinator = it }
    }
}
