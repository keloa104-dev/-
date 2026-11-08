package com.example.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.example.data.PuckPreferences
import com.example.util.PuckKeepAliveManager

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return
        val prefs = PuckPreferences(context)

        val isBootAction = action == Intent.ACTION_BOOT_COMPLETED ||
                action == "android.intent.action.QUICKBOOT_POWERON" ||
                action == "com.htc.intent.action.QUICKBOOT_POWERON" ||
                action == Intent.ACTION_LOCKED_BOOT_COMPLETED ||
                action == Intent.ACTION_MY_PACKAGE_REPLACED ||
                action == Intent.ACTION_USER_PRESENT

        if (isBootAction) {
            if ((prefs.autoStart || action == Intent.ACTION_MY_PACKAGE_REPLACED) && prefs.isServiceEnabled) {
                PuckKeepAliveManager.startPuckService(context)
            }
        }
    }
}
