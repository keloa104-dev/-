package com.example.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.example.data.PuckPreferences
import com.example.util.PuckKeepAliveManager

class PuckKeepAliveReceiver : BroadcastReceiver() {

    companion object {
        const val ACTION_HEARTBEAT_CHECK = "com.example.smartpuck.ACTION_HEARTBEAT_CHECK"
    }

    override fun onReceive(context: Context, intent: Intent?) {
        val prefs = PuckPreferences(context)
        if (prefs.isServiceEnabled) {
            // Revive and verify service execution
            PuckKeepAliveManager.ensureServiceAlive(context)
            // Schedule the next watchdog heartbeat
            PuckKeepAliveManager.scheduleKeepAliveWatchdog(context)
        }
    }
}
