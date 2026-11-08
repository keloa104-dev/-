package com.example.util

import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.database.ContentObserver
import android.media.AudioManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.util.Log
import androidx.core.content.ContextCompat
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Manages bidirectional synchronization of Do Not Disturb (DND) mode
 * between the Smart Puck application and the Android operating system.
 *
 * Ensures direct system-level control with zero redundant HUD overlays.
 */
object DndSyncManager {
    private const val TAG = "DndSyncManager"

    private val _isDndActive = MutableStateFlow(false)
    val isDndActive: StateFlow<Boolean> = _isDndActive.asStateFlow()

    private val _dndStatusDescription = MutableStateFlow("Off")
    val dndStatusDescription: StateFlow<String> = _dndStatusDescription.asStateFlow()

    private val _hasPermission = MutableStateFlow(false)
    val hasPermission: StateFlow<Boolean> = _hasPermission.asStateFlow()

    private var isInitialized = false
    private var appContext: Context? = null
    private var zenModeObserver: ContentObserver? = null
    private var dndReceiver: BroadcastReceiver? = null

    /**
     * Initializes global real-time synchronization with Android system DND state.
     * Listens to both system broadcast actions and the global zen_mode setting.
     */
    fun init(context: Context) {
        if (isInitialized) {
            refresh(context)
            return
        }
        val app = context.applicationContext
        appContext = app
        isInitialized = true

        // 1. Initial status read
        refresh(app)

        // 2. Global zen_mode ContentObserver for instantaneous Quick Settings synchronization
        try {
            val handler = Handler(Looper.getMainLooper())
            zenModeObserver = object : ContentObserver(handler) {
                override fun onChange(selfChange: Boolean) {
                    appContext?.let { refresh(it) }
                }
            }
            app.contentResolver.registerContentObserver(
                Settings.Global.getUriFor("zen_mode"),
                false,
                zenModeObserver!!
            )
        } catch (e: Exception) {
            Log.w(TAG, "Could not register zen_mode ContentObserver", e)
        }

        // 3. Broadcast receiver for interruption filter & permission changes
        try {
            dndReceiver = object : BroadcastReceiver() {
                override fun onReceive(c: Context?, intent: Intent?) {
                    val target = c ?: appContext ?: return
                    refresh(target)
                }
            }
            val filter = createDndIntentFilter()
            try {
                ContextCompat.registerReceiver(
                    app,
                    dndReceiver!!,
                    filter,
                    ContextCompat.RECEIVER_NOT_EXPORTED
                )
            } catch (_: Exception) {
                app.registerReceiver(dndReceiver, filter)
            }
        } catch (e: Exception) {
            Log.w(TAG, "Could not register DND BroadcastReceiver", e)
        }
    }

    /**
     * Checks if Do Not Disturb (Notification Policy) access is granted in Android OS.
     */
    fun isNotificationPolicyAccessGranted(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
            nm?.isNotificationPolicyAccessGranted == true
        } else {
            true
        }
    }

    /**
     * Launches the system Do Not Disturb / Notification Policy access settings.
     */
    fun openDndSettings(context: Context) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                val intent = Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(intent)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to open notification policy access settings", e)
            try {
                val intent = Intent(Settings.ACTION_SETTINGS).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(intent)
            } catch (_: Exception) {}
        }
    }

    /**
     * Queries the Android system to determine whether DND is currently active.
     * Evaluates NotificationManager interruption filter, Settings.Global zen_mode,
     * and audio ringer mode for comprehensive compatibility.
     */
    fun checkIsDndActive(context: Context): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
            val filter = try {
                nm?.currentInterruptionFilter ?: NotificationManager.INTERRUPTION_FILTER_ALL
            } catch (_: Exception) {
                NotificationManager.INTERRUPTION_FILTER_ALL
            }
            if (filter != NotificationManager.INTERRUPTION_FILTER_ALL &&
                filter != NotificationManager.INTERRUPTION_FILTER_UNKNOWN) {
                return true
            }

            // Real-time check of system Global ZEN_MODE
            try {
                val zenMode = Settings.Global.getInt(context.contentResolver, "zen_mode", 0)
                if (zenMode > 0) return true
            } catch (_: Exception) {}

            return false
        } else {
            val am = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
            return am?.ringerMode == AudioManager.RINGER_MODE_SILENT
        }
    }

    /**
     * Returns a human-friendly string representing the current DND filter mode.
     */
    fun getDndDescription(context: Context): String {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
            val filter = try {
                nm?.currentInterruptionFilter ?: NotificationManager.INTERRUPTION_FILTER_ALL
            } catch (_: Exception) {
                NotificationManager.INTERRUPTION_FILTER_ALL
            }
            return when (filter) {
                NotificationManager.INTERRUPTION_FILTER_PRIORITY -> "Priority Only"
                NotificationManager.INTERRUPTION_FILTER_ALARMS -> "Alarms Only"
                NotificationManager.INTERRUPTION_FILTER_NONE -> "Total Silence"
                NotificationManager.INTERRUPTION_FILTER_ALL -> {
                    // Check zen_mode in case OEM updates zen_mode before filter
                    val zenMode = try {
                        Settings.Global.getInt(context.contentResolver, "zen_mode", 0)
                    } catch (_: Exception) { 0 }
                    when (zenMode) {
                        1 -> "Priority Only"
                        2 -> "Total Silence"
                        3 -> "Alarms Only"
                        else -> "Off"
                    }
                }
                else -> if (checkIsDndActive(context)) "Active" else "Off"
            }
        } else {
            return if (checkIsDndActive(context)) "Active (Silent)" else "Off"
        }
    }

    /**
     * Enables or disables Do Not Disturb mode directly in the Android system.
     * Changes immediately propagate to system Quick Settings and the status bar.
     * Operates completely silently without generating any extra UI elements or overlays.
     */
    fun setDndActive(context: Context, enable: Boolean): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
                ?: return false
            if (!nm.isNotificationPolicyAccessGranted) {
                Log.w(TAG, "Notification policy access not granted; opening settings")
                openDndSettings(context)
                return false
            }
            return try {
                val targetFilter = if (enable) {
                    NotificationManager.INTERRUPTION_FILTER_NONE
                } else {
                    NotificationManager.INTERRUPTION_FILTER_ALL
                }
                try {
                    nm.setInterruptionFilter(targetFilter)
                } catch (e: SecurityException) {
                    if (enable) {
                        nm.setInterruptionFilter(NotificationManager.INTERRUPTION_FILTER_PRIORITY)
                    } else {
                        throw e
                    }
                }
                // Synchronously update local cache so UI reacts in 0ms
                _hasPermission.value = true
                _isDndActive.value = enable
                _dndStatusDescription.value = if (enable) "Total Silence" else "Off"
                // Delay background verification refresh to allow OS IPC to complete
                Handler(Looper.getMainLooper()).postDelayed({
                    appContext?.let { refresh(it) }
                }, 200)
                true
            } catch (e: Exception) {
                Log.e(TAG, "Failed to set interruption filter", e)
                false
            }
        } else {
            val am = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager ?: return false
            am.ringerMode = if (enable) AudioManager.RINGER_MODE_SILENT else AudioManager.RINGER_MODE_NORMAL
            refresh(context)
            return true
        }
    }

    /**
     * Refreshes the cached state from the Android OS.
     */
    fun refresh(context: Context) {
        val granted = isNotificationPolicyAccessGranted(context)
        val active = checkIsDndActive(context)
        val desc = getDndDescription(context)

        _hasPermission.value = granted
        _isDndActive.value = active
        _dndStatusDescription.value = desc
    }

    /**
     * Creates an IntentFilter configured to catch real-time DND and ringer mode changes.
     */
    fun createDndIntentFilter(): IntentFilter {
        return IntentFilter().apply {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                addAction(NotificationManager.ACTION_INTERRUPTION_FILTER_CHANGED)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                addAction(NotificationManager.ACTION_NOTIFICATION_POLICY_ACCESS_GRANTED_CHANGED)
            }
            addAction(AudioManager.RINGER_MODE_CHANGED_ACTION)
        }
    }
}
