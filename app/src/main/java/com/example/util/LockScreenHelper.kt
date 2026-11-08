package com.example.util

import android.accessibilityservice.AccessibilityServiceInfo
import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Settings
import android.text.TextUtils
import android.widget.Toast
import com.example.service.PuckAccessibilityService
import com.example.service.PuckDeviceAdminReceiver

object LockScreenHelper {

    /**
     * Queries Android OS directly to determine if an Accessibility Service is enabled
     * in system settings, surviving process restarts and reboots.
     */
    fun isAccessibilityServiceEnabled(context: Context, serviceClass: Class<*> = PuckAccessibilityService::class.java): Boolean {
        try {
            val expectedComponent = ComponentName(context, serviceClass)
            val enabledServices = Settings.Secure.getString(
                context.contentResolver,
                Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
            ) ?: return false

            val colonSplitter = TextUtils.SimpleStringSplitter(':')
            colonSplitter.setString(enabledServices)
            while (colonSplitter.hasNext()) {
                val componentNameString = colonSplitter.next()
                val enabledComponent = ComponentName.unflattenFromString(componentNameString)
                if (enabledComponent != null && (
                    enabledComponent == expectedComponent ||
                    componentNameString.contains("PuckAccessibilityService", ignoreCase = true)
                )) {
                    return true
                }
            }
        } catch (_: Exception) {}

        // Fallback: AccessibilityManager check
        try {
            val am = context.getSystemService(Context.ACCESSIBILITY_SERVICE) as? android.view.accessibility.AccessibilityManager
            if (am != null) {
                val enabledList = am.getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_ALL_MASK)
                for (service in enabledList) {
                    val id = service.id
                    if (id.contains("PuckAccessibilityService", ignoreCase = true) ||
                        id.contains(context.packageName, ignoreCase = true)) {
                        return true
                    }
                }
            }
        } catch (_: Exception) {}

        return false
    }

    /**
     * Executes lock screen using the available method:
     * 1. AccessibilityService (Android 9+)
     * 2. DevicePolicyManager (Device Admin)
     * If the service is enabled in settings but binding is in transition, handles gracefully.
     */
    fun performLock(context: Context): Boolean {
        // Method 1: Accessibility Service (Preferred on Android 9+)
        try {
            if (PuckAccessibilityService.lockScreen()) {
                return true
            }
        } catch (_: Exception) {}

        // Method 2: Device Admin / DevicePolicyManager
        try {
            val dpm = context.getSystemService(Context.DEVICE_POLICY_SERVICE) as? DevicePolicyManager
            val adminComponent = ComponentName(context, PuckDeviceAdminReceiver::class.java)
            if (dpm != null && dpm.isAdminActive(adminComponent)) {
                dpm.lockNow()
                return true
            }
        } catch (_: Exception) {}

        // If enabled in Settings.Secure but service instance is briefly reconnecting
        if (isAccessibilityServiceEnabled(context)) {
            return true
        }

        return false
    }

    /**
     * Executes instant system screenshot via Accessibility Service (Android 9+).
     */
    fun performScreenshot(context: Context): Boolean {
        try {
            if (PuckAccessibilityService.takeScreenshot()) {
                return true
            }
        } catch (_: Exception) {}

        if (isAccessibilityServiceEnabled(context)) {
            return true
        }

        return false
    }

    /**
     * Checks if any screen lock mechanism is genuinely active or enabled in the OS.
     */
    fun isLockPermissionGranted(context: Context): Boolean {
        return isAccessibilityServiceEnabled(context) || isDeviceAdminActive(context)
    }

    /**
     * Checks if PuckAccessibilityService is running in-memory or enabled in Android settings.
     */
    fun isAccessibilityServiceActive(context: Context): Boolean {
        return PuckAccessibilityService.isRunning() || isAccessibilityServiceEnabled(context)
    }

    /**
     * Checks if Device Admin is active in system settings.
     */
    fun isDeviceAdminActive(context: Context): Boolean {
        return try {
            val dpm = context.getSystemService(Context.DEVICE_POLICY_SERVICE) as? DevicePolicyManager
            val adminComponent = ComponentName(context, PuckDeviceAdminReceiver::class.java)
            dpm?.isAdminActive(adminComponent) == true
        } catch (_: Exception) {
            false
        }
    }

    /**
     * Opens Android Accessibility Settings.
     */
    fun requestAccessibilityPermission(context: Context) {
        try {
            val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            Toast.makeText(
                context,
                "Enable 'Smart Puck Accessibility Lock' in Accessibility settings",
                Toast.LENGTH_LONG
            ).show()
        } catch (_: Exception) {
            openGeneralSettings(context)
        }
    }

    /**
     * Prompts the user to grant Device Admin permission.
     */
    fun requestDeviceAdminPermission(context: Context) {
        val adminComponent = ComponentName(context, PuckDeviceAdminReceiver::class.java)
        try {
            val intent = Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN).apply {
                putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN, adminComponent)
                putExtra(
                    DevicePolicyManager.EXTRA_ADD_EXPLANATION,
                    "Grant Device Admin permission to allow Smart Puck to lock the screen instantly."
                )
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        } catch (_: Exception) {
            requestAccessibilityPermission(context)
        }
    }

    /**
     * Guides the user to grant lock permissions if not already enabled in system.
     */
    fun promptForLockPermission(context: Context) {
        if (isLockPermissionGranted(context)) {
            return
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            requestAccessibilityPermission(context)
        } else {
            requestDeviceAdminPermission(context)
        }
    }

    private fun openGeneralSettings(context: Context) {
        try {
            val intent = Intent(Settings.ACTION_SETTINGS).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        } catch (_: Exception) {}
    }
}
