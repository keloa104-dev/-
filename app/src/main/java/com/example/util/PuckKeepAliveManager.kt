package com.example.util

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import com.example.data.PuckPreferences
import com.example.service.FloatingPuckService
import com.example.service.PuckKeepAliveReceiver

object PuckKeepAliveManager {

    private const val ALARM_REQUEST_CODE = 9001
    private const val WATCHDOG_INTERVAL_MS = 5 * 60 * 1000L // 5 minutes periodic heartbeat

    /**
     * Checks if service should be running according to preferences, and starts it if needed.
     */
    fun startPuckService(context: Context) {
        val prefs = PuckPreferences(context)
        prefs.isServiceEnabled = true

        val intent = Intent(context, FloatingPuckService::class.java)
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        } catch (_: Exception) {}

        scheduleKeepAliveWatchdog(context)
    }

    /**
     * Stops the floating puck service and cancels watchdog alarms.
     */
    fun stopPuckService(context: Context) {
        val prefs = PuckPreferences(context)
        prefs.isServiceEnabled = false

        try {
            context.stopService(Intent(context, FloatingPuckService::class.java))
        } catch (_: Exception) {}

        cancelKeepAliveWatchdog(context)
    }

    /**
     * Ensures the FloatingPuckService is alive if enabled in user preferences.
     */
    fun ensureServiceAlive(context: Context) {
        val prefs = PuckPreferences(context)
        if (prefs.isServiceEnabled) {
            val intent = Intent(context, FloatingPuckService::class.java)
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(intent)
                } else {
                    context.startService(intent)
                }
            } catch (_: Exception) {}
        }
    }

    /**
     * Schedules periodic watchdog alarm to revive service if killed by OS low-memory or power saver.
     */
    fun scheduleKeepAliveWatchdog(context: Context) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
        val intent = Intent(context, PuckKeepAliveReceiver::class.java).apply {
            action = PuckKeepAliveReceiver.ACTION_HEARTBEAT_CHECK
        }
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            ALARM_REQUEST_CODE,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val triggerAt = System.currentTimeMillis() + WATCHDOG_INTERVAL_MS
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                alarmManager.setAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP,
                    triggerAt,
                    pendingIntent
                )
            } else {
                alarmManager.set(
                    AlarmManager.RTC_WAKEUP,
                    triggerAt,
                    pendingIntent
                )
            }
        } catch (_: Exception) {}
    }

    /**
     * Cancels the periodic watchdog alarm.
     */
    fun cancelKeepAliveWatchdog(context: Context) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
        val intent = Intent(context, PuckKeepAliveReceiver::class.java)
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            ALARM_REQUEST_CODE,
            intent,
            PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
        )
        if (pendingIntent != null) {
            alarmManager.cancel(pendingIntent)
            pendingIntent.cancel()
        }
    }

    /**
     * Checks if battery optimization is ignored for this app in Android OS.
     */
    fun isBatteryOptimizationIgnored(context: Context): Boolean {
        return try {
            val pm = context.getSystemService(Context.POWER_SERVICE) as PowerManager
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                pm.isIgnoringBatteryOptimizations(context.packageName)
            } else {
                true
            }
        } catch (_: Exception) {
            false
        }
    }

    /**
     * Requests battery optimization exemption so the app never sleeps in the background.
     */
    fun requestIgnoreBatteryOptimizations(context: Context) {
        val prefs = PuckPreferences(context)
        prefs.isBatteryOptPermanentlyGranted = true
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                val intent = Intent(
                    Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                    Uri.parse("package:${context.packageName}")
                ).apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }
                context.startActivity(intent)
            }
        } catch (_: Exception) {
            openAppBatterySettings(context)
        }
    }

    /**
     * Opens App Details or Battery settings for OEM optimization adjustments.
     */
    fun openAppBatterySettings(context: Context) {
        val prefs = PuckPreferences(context)
        prefs.isBatteryOptPermanentlyGranted = true
        try {
            val intent = Intent(
                Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                Uri.parse("package:${context.packageName}")
            ).apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }
            context.startActivity(intent)
        } catch (_: Exception) {}
    }

    /**
     * Opens OEM AutoStart / Background settings on popular devices (Xiaomi, Huawei, Oppo, Vivo, etc.).
     */
    fun openAutoStartSettings(context: Context) {
        val prefs = PuckPreferences(context)
        prefs.isBatteryOptPermanentlyGranted = true
        val intents = listOf(
            // Xiaomi / MIUI / HyperOS
            Intent().setComponent(
                android.content.ComponentName("com.miui.securitycenter", "com.miui.permcenter.autostart.AutoStartManagementActivity")
            ),
            // Huawei / Honor
            Intent().setComponent(
                android.content.ComponentName("com.huawei.systemmanager", "com.huawei.systemmanager.startupmgr.ui.StartupNormalAppListActivity")
            ),
            // Oppo / ColorOS
            Intent().setComponent(
                android.content.ComponentName("com.coloros.safecenter", "com.coloros.safecenter.permission.startup.StartupAppListActivity")
            ),
            // Vivo / FuntouchOS
            Intent().setComponent(
                android.content.ComponentName("com.iqoo.secure", "com.iqoo.secure.ui.phoneoptimize.AddWhiteListActivity")
            ),
            // Samsung
            Intent().setComponent(
                android.content.ComponentName("com.samsung.android.lool", "com.samsung.android.sm.ui.battery.BatteryActivity")
            )
        )

        for (intent in intents) {
            try {
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(intent)
                return
            } catch (_: Exception) {}
        }

        // Fallback to application settings
        openAppBatterySettings(context)
    }
}
