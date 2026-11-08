package com.example

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build

class SmartPuckApp : Application() {
    companion object {
        const val CHANNEL_ID = "smart_puck_channel"
        lateinit var instance: SmartPuckApp
            private set
    }

    override fun onCreate() {
        super.onCreate()
        instance = this
        createNotificationChannel()
        com.example.util.DndSyncManager.init(this)
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Smart Puck Control Service",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Running floating puck control overlay in background"
                setShowBadge(false)
                setSound(null, null)
                enableVibration(false)
                enableLights(false)
                lockscreenVisibility = android.app.Notification.VISIBILITY_SECRET
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }
}
