package com.example.service

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.os.Build
import android.view.accessibility.AccessibilityEvent

class PuckAccessibilityService : AccessibilityService() {

    companion object {
        @Volatile
        var instance: PuckAccessibilityService? = null
            private set

        fun lockScreen(): Boolean {
            val service = instance
            if (service != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                return service.performGlobalAction(GLOBAL_ACTION_LOCK_SCREEN)
            }
            return false
        }

        fun takeScreenshot(): Boolean {
            val service = instance
            if (service != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                return service.performGlobalAction(GLOBAL_ACTION_TAKE_SCREENSHOT)
            }
            return false
        }

        fun isRunning(): Boolean = instance != null
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
    }

    override fun onUnbind(intent: Intent?): Boolean {
        instance = null
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        instance = null
        super.onDestroy()
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        // No event interception required for global action dispatch
    }

    override fun onInterrupt() {}
}
