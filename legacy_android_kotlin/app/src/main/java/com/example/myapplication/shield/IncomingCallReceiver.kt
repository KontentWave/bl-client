package com.example.myapplication.shield

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.telephony.TelephonyManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class IncomingCallReceiver : BroadcastReceiver() {
    @Suppress("DEPRECATION")
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != TelephonyManager.ACTION_PHONE_STATE_CHANGED) {
            return
        }

        val phoneState = intent.getStringExtra(TelephonyManager.EXTRA_STATE)
        if (phoneState != TelephonyManager.EXTRA_STATE_RINGING) {
            return
        }

        val pendingResult = goAsync()
        val appContext = context.applicationContext
        val rawIncomingNumber = intent.getStringExtra(TelephonyManager.EXTRA_INCOMING_NUMBER)

        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                IncomingCallProcessor.create(appContext).processIncomingNumber(rawIncomingNumber)
            } finally {
                pendingResult.finish()
            }
        }
    }
}

