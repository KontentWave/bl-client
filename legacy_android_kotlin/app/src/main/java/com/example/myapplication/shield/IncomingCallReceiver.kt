package com.example.myapplication.shield

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.telephony.TelephonyManager
import android.os.SystemClock
import kotlinx.coroutines.Job

class IncomingCallReceiver : BroadcastReceiver() {
    @Suppress("DEPRECATION")
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != TelephonyManager.ACTION_PHONE_STATE_CHANGED) {
            return
        }

        val receivedAtMillis = SystemClock.elapsedRealtime()
        val state = when (intent.getStringExtra(TelephonyManager.EXTRA_STATE)) {
            TelephonyManager.EXTRA_STATE_RINGING -> IncomingCallState.Ringing
            TelephonyManager.EXTRA_STATE_OFFHOOK -> IncomingCallState.Offhook
            TelephonyManager.EXTRA_STATE_IDLE -> IncomingCallState.Idle
            else -> return
        }
        val pendingResult = goAsync()
        var work: Job? = null
        try {
            val rawIncomingNumber = intent.getStringExtra(TelephonyManager.EXTRA_INCOMING_NUMBER)
            work = IncomingCallCoordinatorProvider.get(context)
                .onPhoneState(state, rawIncomingNumber, receivedAtMillis)
        } finally {
            finishBroadcastWhenComplete(work) { pendingResult.finish() }
        }
    }
}
