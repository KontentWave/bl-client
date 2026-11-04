package com.example.myapplication.session

import android.app.job.JobInfo
import android.app.job.JobParameters
import android.app.job.JobScheduler
import android.app.job.JobService
import android.content.ComponentName
import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import com.example.myapplication.BuildConfig
import com.example.myapplication.data.remote.ApiClientFactory
import com.example.myapplication.security.SecuritySignedRequestFactory
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob

object AndroidRenewalRuntime {
    private const val JOB_ID = 24_001
    @Volatile private var instance: RenewalExecution? = null

    fun get(context: Context, recovery: SessionRecovery = SessionRecoveryProvider.get(context)): RenewalExecution =
        instance ?: synchronized(this) {
            instance ?: create(context.applicationContext, recovery).also { instance = it }
        }

    private fun create(context: Context, recovery: SessionRecovery): RenewalExecution {
        val scheduler = context.getSystemService(JobScheduler::class.java)
        val connectivity = context.getSystemService(ConnectivityManager::class.java)
        val execution = RenewalExecution(
            recovery,
            RenewalCoordinator(recovery, ApiClientFactory.createLifecycleApi(BuildConfig.API_BASE_URL),
                SecuritySignedRequestFactory()),
            CoroutineScope(SupervisorJob() + Dispatchers.IO),
            scheduleBackground = { eligible ->
                if (eligible == null) {
                    scheduler.cancel(JOB_ID)
                    true
                } else {
                    scheduler.schedule(JobInfo.Builder(JOB_ID, ComponentName(context, RenewalJobService::class.java))
                        .setPersisted(true)
                        .setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY)
                        .setMinimumLatency((eligible - System.currentTimeMillis()).coerceAtLeast(0))
                        .build()) == JobScheduler.RESULT_SUCCESS
                }
            },
            networkAvailable = {
                connectivity.activeNetwork?.let {
                    connectivity.getNetworkCapabilities(it)?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                } == true
            },
        )
        connectivity.registerDefaultNetworkCallback(object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) { execution.networkChanged() }
            override fun onLost(network: Network) { execution.networkChanged() }
            override fun onCapabilitiesChanged(network: Network, capabilities: NetworkCapabilities) {
                execution.networkChanged()
            }
        })
        return execution
    }
}

class RenewalJobService : JobService() {
    private var work: Job? = null

    override fun onStartJob(params: JobParameters): Boolean {
        work = AndroidRenewalRuntime.get(applicationContext).execute { jobFinished(params, false) }
        return work != null
    }

    override fun onStopJob(params: JobParameters): Boolean {
        work?.cancel()
        work = null
        return true
    }
}
