package com.example.service

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.app.Notification
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.SharedPreferences
import android.database.ContentObserver
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.hardware.camera2.CameraManager
import android.media.AudioManager
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.provider.Settings
import android.util.DisplayMetrics
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.VelocityTracker
import android.view.View
import android.view.WindowManager
import android.view.animation.AccelerateInterpolator
import android.view.animation.DecelerateInterpolator
import android.view.animation.PathInterpolator
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.PopupWindow
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.core.app.NotificationCompat
import androidx.core.graphics.ColorUtils
import com.example.MainActivity
import com.example.R
import com.example.SmartPuckApp
import com.example.data.PuckPreferences
import com.example.util.DndSyncManager
import com.example.util.LockScreenHelper
import com.example.util.PuckKeepAliveManager
import kotlin.math.roundToInt

class FloatingPuckService : Service() {

    companion object {
        const val ACTION_LOCK_SCREEN = "com.example.smartpuck.ACTION_LOCK_SCREEN"
    }

    private lateinit var windowManager: WindowManager
    private lateinit var puckView: View
    private lateinit var puckContainer: FrameLayout
    private lateinit var puckIcon: ImageView
    private lateinit var params: WindowManager.LayoutParams
    private lateinit var prefs: PuckPreferences
    private lateinit var audioManager: AudioManager
    private var vibrator: Vibrator? = null
    private var menuContentLayout: LinearLayout? = null

    private var initialX = 0
    private var initialY = 0
    private var initialTouchX = 0f
    private var initialTouchY = 0f
    private var isDragging = false
    private var isControlling = false
    private var controlMode = ControlMode.NONE
    private var screenWidth = 0
    private var screenHeight = 0
    private val handler = Handler(Looper.getMainLooper())
    private var wasFullscreen = false
    private var savedX = 0
    private var savedY = 0
    private var currentAnimator: ValueAnimator? = null
    private var velocityTracker: VelocityTracker? = null
    private var hudToast: Toast? = null
    private var isCharging = false

    // Continuous 120 FPS control tracking & Instant HUD
    private var startControlBrightness = 128
    private var startControlVolume = 10
    private var controlTouchX = 0f
    private var controlTouchY = 0f
    private var controlAnchorX = 0f
    private var controlAnchorY = 0f
    private var lockedAxis = LockedAxis.NONE
    private var minLimitHapticFired = false
    private var maxLimitHapticFired = false
    private var lastTargetBrightness = -1
    private var lastTargetVolume = -1
    private var hudOverlayView: View? = null
    private var hudGlyphView: TextView? = null
    private var hudBarFill: View? = null
    private var hudPercentView: TextView? = null
    private var isHudLockedHidden = true
    private var isFingerLifted = true
    private val hideHudRunnable = Runnable { dismissHudFast() }

    // Adaptive Attached Quick Settings Menu
    private var attachedMenuLayout: AttachedMenuLayout? = null
    private var menuParams: WindowManager.LayoutParams? = null
    private var isMenuShowing = false
    private var wasMenuOpenOnDown = false
    private var isPuckTouchedNow = false

    private var cameraManager: CameraManager? = null
    private var mainCameraId: String? = null
    private var isFlashlightOn = false

    private val torchCallback = object : CameraManager.TorchCallback() {
        override fun onTorchModeChanged(cameraId: String, enabled: Boolean) {
            super.onTorchModeChanged(cameraId, enabled)
            if (mainCameraId == null || cameraId == mainCameraId) {
                isFlashlightOn = enabled
            }
        }
        override fun onTorchModeUnavailable(cameraId: String) {
            super.onTorchModeUnavailable(cameraId)
            if (mainCameraId == null || cameraId == mainCameraId) {
                isFlashlightOn = false
            }
        }
    }

    private val prefChangeListener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
        handler.post {
            applyPuckStyle()
            if (key == "menu_order" && isMenuShowing) {
                refreshMenuOrderInRealTime()
            }
        }
    }

    private enum class ControlMode { NONE, BRIGHTNESS, VOLUME }
    private enum class LockedAxis { NONE, HORIZONTAL, VERTICAL }

    override fun onCreate() {
        super.onCreate()
        prefs = PuckPreferences(this)
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        audioManager = getSystemService(AUDIO_SERVICE) as AudioManager
        cameraManager = getSystemService(Context.CAMERA_SERVICE) as? CameraManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            try {
                mainCameraId = cameraManager?.cameraIdList?.firstOrNull()
                cameraManager?.registerTorchCallback(torchCallback, handler)
            } catch (_: Exception) {}
        }
        vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            (getSystemService(VIBRATOR_MANAGER_SERVICE) as VibratorManager).defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            getSystemService(VIBRATOR_SERVICE) as Vibrator
        }

        val sp = getSharedPreferences("smart_puck_prefs", Context.MODE_PRIVATE)
        sp.registerOnSharedPreferenceChangeListener(prefChangeListener)

        updateScreenDimensions()
        startForegroundSafely()
        addPuckView()
        ensureHudOverlayCreated()
        registerReceivers()
        startFullscreenChecker()
        PuckKeepAliveManager.scheduleKeepAliveWatchdog(this)
    }

    private fun startForegroundSafely() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                startForeground(
                    1,
                    buildNotification(),
                    android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
                )
            } else {
                startForeground(1, buildNotification())
            }
        } catch (_: Exception) {
            try {
                startForeground(1, buildNotification())
            } catch (_: Exception) {}
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_LOCK_SCREEN) {
            lockScreen()
            return START_STICKY
        }
        startForegroundSafely()
        ensurePuckAttached()
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        super.onDestroy()
        val sp = getSharedPreferences("smart_puck_prefs", Context.MODE_PRIVATE)
        sp.unregisterOnSharedPreferenceChangeListener(prefChangeListener)

        handler.removeCallbacksAndMessages(null)
        removeHudOverlay()
        removeMenuOverlay()
        currentAnimator?.cancel()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            try { cameraManager?.unregisterTorchCallback(torchCallback) } catch (_: Exception) {}
        }
        if (::puckView.isInitialized) {
            try { windowManager.removeView(puckView) } catch (_: Exception) {}
        }
        try { unregisterReceiver(configReceiver) } catch (_: Exception) {}
        try { unregisterReceiver(volumeReceiver) } catch (_: Exception) {}
        try { unregisterReceiver(batteryReceiver) } catch (_: Exception) {}
        try { contentResolver.unregisterContentObserver(brightnessObserver) } catch (_: Exception) {}

        // Indestructible Auto-restart service if enabled when destroyed by OS/inactivity
        if (::prefs.isInitialized && prefs.isServiceEnabled) {
            PuckKeepAliveManager.scheduleKeepAliveWatchdog(applicationContext)
            try {
                val restartIntent = Intent(applicationContext, FloatingPuckService::class.java)
                val pendingIntent = PendingIntent.getService(
                    applicationContext,
                    1001,
                    restartIntent,
                    PendingIntent.FLAG_ONE_SHOT or PendingIntent.FLAG_IMMUTABLE
                )
                val alarmManager = getSystemService(Context.ALARM_SERVICE) as? android.app.AlarmManager
                alarmManager?.set(
                    android.app.AlarmManager.RTC_WAKEUP,
                    System.currentTimeMillis() + 500,
                    pendingIntent
                )
            } catch (_: Exception) {}
        }
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        super.onTaskRemoved(rootIntent)
        if (::prefs.isInitialized && prefs.isServiceEnabled) {
            PuckKeepAliveManager.scheduleKeepAliveWatchdog(applicationContext)
            try {
                val restartIntent = Intent(applicationContext, FloatingPuckService::class.java)
                val pendingIntent = PendingIntent.getService(
                    applicationContext,
                    1002,
                    restartIntent,
                    PendingIntent.FLAG_ONE_SHOT or PendingIntent.FLAG_IMMUTABLE
                )
                val alarmManager = getSystemService(Context.ALARM_SERVICE) as? android.app.AlarmManager
                alarmManager?.set(
                    android.app.AlarmManager.RTC_WAKEUP,
                    System.currentTimeMillis() + 500,
                    pendingIntent
                )
            } catch (_: Exception) {}
        }
    }

    private fun updateScreenDimensions() {
        val metrics = resources.displayMetrics
        screenWidth = metrics.widthPixels
        screenHeight = metrics.heightPixels
    }

    private fun buildNotification(): Notification {
        val appIntent = Intent(this, MainActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        }
        val appPendingIntent = PendingIntent.getActivity(
            this, 0, appIntent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val lockIntent = Intent(this, FloatingPuckService::class.java).apply {
            action = ACTION_LOCK_SCREEN
        }
        val lockPendingIntent = PendingIntent.getService(
            this, 1, lockIntent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        return NotificationCompat.Builder(this, SmartPuckApp.CHANNEL_ID)
            .setContentTitle("Smart Puck Active")
            .setContentText("Continuous background overlay running • Tap for options")
            .setSmallIcon(android.R.drawable.ic_menu_preferences)
            .setContentIntent(appPendingIntent)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .addAction(android.R.drawable.ic_lock_power_off, "Lock Screen", lockPendingIntent)
            .addAction(android.R.drawable.ic_menu_manage, "Open App", appPendingIntent)
            .setSound(null)
            .setVibrate(null)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .build()
    }

    private fun addPuckView() {
        puckContainer = FrameLayout(this)
        puckIcon = ImageView(this)
        val initialSizePx = dpToPx(prefs.puckSize)
        val initialIconSizePx = (initialSizePx * 0.52f).toInt()
        val iconParams = FrameLayout.LayoutParams(
            initialIconSizePx,
            initialIconSizePx
        ).apply {
            gravity = Gravity.CENTER
        }
        puckIcon.scaleType = ImageView.ScaleType.FIT_CENTER
        puckContainer.addView(puckIcon, iconParams)
        puckView = puckContainer

        val sizePx = dpToPx(prefs.puckSize)
        params = WindowManager.LayoutParams(
            sizePx,
            sizePx,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            else
                @Suppress("DEPRECATION")
                WindowManager.LayoutParams.TYPE_PHONE,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        )
        params.gravity = Gravity.CENTER

        val lastX = prefs.lastPuckX
        val lastY = prefs.lastPuckY
        if (lastX != -1 && lastY != -1) {
            params.x = lastX - screenWidth / 2
            params.y = lastY - screenHeight / 2
        } else {
            params.x = screenWidth / 2 - sizePx / 2 - dpToPx(8)
            params.y = 0
        }

        val (initSafeX, initSafeY) = clampAndSanitizePosition(params.x, params.y)
        params.x = initSafeX
        params.y = initSafeY
        savedX = initSafeX
        savedY = initSafeY

        applyPuckStyle()

        try {
            windowManager.addView(puckView, params)
        } catch (e: Exception) {
            e.printStackTrace()
        }

        var longPressTriggered = false
        val longPressHandler = Handler(Looper.getMainLooper())
        val longPressRunnable = Runnable {
            if (isFingerLifted) return@Runnable
            longPressTriggered = true
            isControlling = true
            isDragging = false
            prepareControl(initialTouchX, initialTouchY)
        }

        puckContainer.setOnTouchListener { _, event ->
            // Absolute Rejection of Post-Lift Movement Events:
            // Freeze on Lift: The moment the finger is lifted, completely discard any incoming ACTION_MOVE events.
            if (isFingerLifted && event.action == MotionEvent.ACTION_MOVE) {
                return@setOnTouchListener true
            }

            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    // Only a brand-new ACTION_DOWN resets the finger lifted flag and unlocks HUD
                    isFingerLifted = false
                    isHudLockedHidden = false
                    isPuckTouchedNow = true
                    val menuOpenAtDown = isMenuShowing || (attachedMenuLayout != null)
                    wasMenuOpenOnDown = menuOpenAtDown
                    if (menuOpenAtDown) {
                        hidePuckMenu()
                    }
                    handler.removeCallbacks(hideHudRunnable)
                    dismissHudFast()
                    currentAnimator?.cancel()
                    initialX = params.x
                    initialY = params.y
                    initialTouchX = event.rawX
                    initialTouchY = event.rawY
                    isDragging = false
                    isControlling = false
                    controlMode = ControlMode.NONE
                    lockedAxis = LockedAxis.NONE
                    longPressTriggered = false
                    minLimitHapticFired = false
                    maxLimitHapticFired = false
                    puckContainer.scaleX = 1.0f
                    puckContainer.scaleY = 1.0f
                    velocityTracker?.recycle()
                    velocityTracker = VelocityTracker.obtain()
                    velocityTracker?.addMovement(event)
                    longPressHandler.postDelayed(longPressRunnable, 350)
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    // Reject all movement if finger lifted or HUD locked
                    if (isFingerLifted || isHudLockedHidden && isControlling) {
                        return@setOnTouchListener true
                    }

                    velocityTracker?.addMovement(event)
                    val deltaX = event.rawX - initialTouchX
                    val deltaY = event.rawY - initialTouchY

                    // Auto-Dismiss Menu When Moving the Puck:
                    // If moved more than 4 pixels, immediately close menu in single frame and enter Drag Mode
                    if (!isControlling && (Math.abs(deltaX) > 4 || Math.abs(deltaY) > 4)) {
                        hidePuckMenu()
                        longPressHandler.removeCallbacks(longPressRunnable)
                        isDragging = true
                    }

                    if (isControlling) {
                        handleControl(event.rawX, event.rawY)
                    } else if (isDragging) {
                        hidePuckMenu()
                        params.x = initialX + deltaX.toInt()
                        params.y = initialY + deltaY.toInt()
                        try {
                            windowManager.updateViewLayout(puckView, params)
                        } catch (_: Exception) {}
                    }
                    true
                }
                MotionEvent.ACTION_UP -> {
                    // Freeze on Lift: Set finger lifted flag immediately before any other logic runs
                    isFingerLifted = true
                    isHudLockedHidden = true
                    isPuckTouchedNow = false
                    dismissHudFast()
                    longPressHandler.removeCallbacks(longPressRunnable)
                    velocityTracker?.addMovement(event)
                    velocityTracker?.computeCurrentVelocity(1000)
                    val velX = velocityTracker?.xVelocity ?: 0f
                    velocityTracker?.recycle()
                    velocityTracker = null

                    if (!isDragging && !isControlling && !longPressTriggered) {
                        if (!wasMenuOpenOnDown) {
                            showPuckMenu()
                        }
                    }
                    if (isDragging) {
                        snapToEdgeSmart(initialX, event.rawX - initialTouchX, velX)
                    }
                    wasMenuOpenOnDown = false
                    isDragging = false
                    isControlling = false
                    controlMode = ControlMode.NONE
                    lockedAxis = LockedAxis.NONE
                    minLimitHapticFired = false
                    maxLimitHapticFired = false
                    puckContainer.scaleX = 1.0f
                    puckContainer.scaleY = 1.0f
                    true
                }
                MotionEvent.ACTION_CANCEL -> {
                    // Freeze on Lift: Set finger lifted flag immediately before any other logic runs
                    isFingerLifted = true
                    isHudLockedHidden = true
                    isPuckTouchedNow = false
                    wasMenuOpenOnDown = false
                    dismissHudFast()
                    longPressHandler.removeCallbacks(longPressRunnable)
                    velocityTracker?.recycle()
                    velocityTracker = null
                    if (isDragging) {
                        snapToEdge()
                    }
                    isDragging = false
                    isControlling = false
                    controlMode = ControlMode.NONE
                    lockedAxis = LockedAxis.NONE
                    minLimitHapticFired = false
                    maxLimitHapticFired = false
                    puckContainer.scaleX = 1.0f
                    puckContainer.scaleY = 1.0f
                    true
                }
                else -> false
            }
        }
    }

    private fun applyPuckStyle() {
        if (!::puckContainer.isInitialized) return
        val sizePx = dpToPx(prefs.puckSize)
        if (puckContainer.layoutParams != null) {
            puckContainer.layoutParams.width = sizePx
            puckContainer.layoutParams.height = sizePx
        }
        if (::params.isInitialized) {
            params.width = sizePx
            params.height = sizePx
            if (::puckView.isInitialized && puckView.isAttachedToWindow) {
                try {
                    windowManager.updateViewLayout(puckView, params)
                } catch (_: Exception) {}
            }
        }
        puckContainer.alpha = prefs.puckOpacity / 100f

        val shape = GradientDrawable()
        val shapeType = prefs.puckShape
        val isFrosted = prefs.puckStyle == "frosted_glass"

        val baseColor = if (isFrosted) {
            ColorUtils.setAlphaComponent(prefs.puckColor, 175)
        } else {
            prefs.puckColor
        }

        when (shapeType) {
            "rounded_square" -> {
                shape.shape = GradientDrawable.RECTANGLE
                shape.cornerRadius = sizePx * 0.28f
            }
            "squircle" -> {
                shape.shape = GradientDrawable.RECTANGLE
                shape.cornerRadius = sizePx * 0.40f
            }
            else -> {
                shape.shape = GradientDrawable.OVAL
            }
        }
        shape.setColor(baseColor)
        val strokeColor = if (isFrosted) 0xB8FFFFFF.toInt() else 0x90FFFFFF.toInt()
        val strokeWidth = if (isFrosted) dpToPx(2.5f) else dpToPx(2)
        shape.setStroke(strokeWidth, strokeColor)
        puckContainer.background = shape
        puckContainer.scaleX = 1.0f
        puckContainer.scaleY = 1.0f

        val iconSizePx = (sizePx * 0.52f).toInt()
        if (::puckIcon.isInitialized && puckIcon.layoutParams != null) {
            puckIcon.layoutParams.width = iconSizePx
            puckIcon.layoutParams.height = iconSizePx
            puckIcon.requestLayout()
        }

        if (isCharging) {
            puckIcon.setImageResource(R.drawable.ic_electric_clock)
            puckIcon.setColorFilter(Color.WHITE)
            puckIcon.alpha = 1.0f
        } else {
            val iconRes = com.example.data.IconStyleUtils.getIconRes(prefs.iconStyle)
            puckIcon.setImageResource(iconRes)
            puckIcon.setColorFilter(Color.WHITE)
            puckIcon.alpha = 0.85f
        }
    }

    private fun prepareControl(rawX: Float, rawY: Float) {
        controlTouchX = rawX
        controlTouchY = rawY
        controlAnchorX = rawX
        controlAnchorY = rawY
        lockedAxis = LockedAxis.NONE
        controlMode = ControlMode.NONE
        minLimitHapticFired = false
        maxLimitHapticFired = false

        startControlBrightness = try {
            Settings.System.getInt(contentResolver, Settings.System.SCREEN_BRIGHTNESS)
        } catch (_: Exception) { 128 }
        startControlVolume = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
        lastTargetBrightness = startControlBrightness
        lastTargetVolume = startControlVolume

        // Initial long-press activation tactile feedback
        vibrate()
        puckContainer.scaleX = 1.0f
        puckContainer.scaleY = 1.0f
    }

    private fun handleControl(rawX: Float, rawY: Float) {
        if (isFingerLifted || isHudLockedHidden) return

        // Step 1: Pixel-level strict axis determination on first movement
        if (lockedAxis == LockedAxis.NONE) {
            val deltaX = rawX - controlAnchorX
            val deltaY = rawY - controlAnchorY
            val absX = Math.abs(deltaX)
            val absY = Math.abs(deltaY)
            val thresholdPx = prefs.axisLockThresholdPx.coerceIn(2, 10).toFloat()

            // Analyze first few pixels of movement against strict threshold
            if (absX >= thresholdPx || absY >= thresholdPx) {
                // Strict pixel comparison: whichever axis has greater displacement wins (even by a single pixel)
                lockedAxis = if (absX > absY) LockedAxis.HORIZONTAL else LockedAxis.VERTICAL

                // Determine parameter based on locked axis and swapAxes preference
                val isSwapped = prefs.swapAxes
                controlMode = when (lockedAxis) {
                    LockedAxis.HORIZONTAL -> if (isSwapped) ControlMode.VOLUME else ControlMode.BRIGHTNESS
                    LockedAxis.VERTICAL -> if (isSwapped) ControlMode.BRIGHTNESS else ControlMode.VOLUME
                    LockedAxis.NONE -> ControlMode.NONE
                }

                // Lock the touch coordinate origin to this precise moment
                controlTouchX = rawX
                controlTouchY = rawY

                // Visual confirmation: display HUD showing only the locked parameter with NO directional symbols or arrows
                if (controlMode == ControlMode.VOLUME) {
                    val maxVol = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
                    val percent = if (maxVol > 0) (startControlVolume * 100 / maxVol).coerceIn(0, 100) else 0
                    val icon = if (startControlVolume <= 0) "🔇" else "🔊"
                    showCompactPuckHUD(icon, percent, autoHide = false)
                } else if (controlMode == ControlMode.BRIGHTNESS) {
                    val percent = (startControlBrightness * 100 / 255).coerceIn(0, 100)
                    showCompactPuckHUD("☀️", percent, autoHide = false)
                }
            }
            return
        }

        // Step 2: Axis is permanently locked for the duration of this gesture.
        // Any movement or drift along the ignored axis is 100% ignored.
        val sensitivityFactor = (prefs.sensitivity / 50f)
        val dragSpan = dpToPx(240).toFloat()

        // Direction mapping:
        // Horizontal: dragging Right increases, Left decreases
        // Vertical: dragging Up increases, Down decreases
        val deltaPixels = when (lockedAxis) {
            LockedAxis.HORIZONTAL -> (rawX - controlTouchX)
            LockedAxis.VERTICAL -> -(rawY - controlTouchY)
            LockedAxis.NONE -> 0f
        }

        if (prefs.smoothControl) {
            val fraction = (deltaPixels / dragSpan) * sensitivityFactor
            when (controlMode) {
                ControlMode.BRIGHTNESS -> {
                    val targetBrightness = (startControlBrightness + fraction * 255f).roundToInt().coerceIn(1, 255)
                    if (targetBrightness != lastTargetBrightness) {
                        lastTargetBrightness = targetBrightness
                        try {
                            Settings.System.putInt(contentResolver, Settings.System.SCREEN_BRIGHTNESS, targetBrightness)
                        } catch (_: Exception) {}
                        val percent = (targetBrightness * 100 / 255).coerceIn(0, 100)
                        showCompactPuckHUD("☀️", percent, autoHide = false)
                    }

                    // Haptic feedback ONLY on reaching absolute limits
                    if (targetBrightness >= 255) {
                        if (!maxLimitHapticFired) {
                            maxLimitHapticFired = true
                            vibrate()
                        }
                    } else if (targetBrightness <= 1) {
                        if (!minLimitHapticFired) {
                            minLimitHapticFired = true
                            vibrate()
                        }
                    } else {
                        maxLimitHapticFired = false
                        minLimitHapticFired = false
                    }
                }
                ControlMode.VOLUME -> {
                    val maxVol = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
                    val targetVol = (startControlVolume + fraction * maxVol).roundToInt().coerceIn(0, maxVol)
                    if (targetVol != lastTargetVolume) {
                        lastTargetVolume = targetVol
                        audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, targetVol, 0)
                        val percent = if (maxVol > 0) (targetVol * 100 / maxVol).coerceIn(0, 100) else 0
                        val icon = if (targetVol <= 0) "🔇" else "🔊"
                        showCompactPuckHUD(icon, percent, autoHide = false)
                    }

                    // Haptic feedback ONLY on reaching absolute limits
                    if (targetVol >= maxVol) {
                        if (!maxLimitHapticFired) {
                            maxLimitHapticFired = true
                            vibrate()
                        }
                    } else if (targetVol <= 0) {
                        if (!minLimitHapticFired) {
                            minLimitHapticFired = true
                            vibrate()
                        }
                    } else {
                        maxLimitHapticFired = false
                        minLimitHapticFired = false
                    }
                }
                else -> {}
            }
        } else {
            // Step-based control mode (5% steps)
            val stepSpan = dpToPx(28).toFloat() / sensitivityFactor
            val steps = (deltaPixels / stepSpan).toInt()
            if (Math.abs(steps) >= 1) {
                when (controlMode) {
                    ControlMode.BRIGHTNESS -> {
                        adjustBrightness(steps * 13)
                        if (lockedAxis == LockedAxis.HORIZONTAL) controlTouchX = rawX
                        else controlTouchY = rawY
                    }
                    ControlMode.VOLUME -> {
                        adjustVolume(steps)
                        if (lockedAxis == LockedAxis.HORIZONTAL) controlTouchX = rawX
                        else controlTouchY = rawY
                    }
                    else -> {}
                }
            }
        }
    }

    private fun adjustBrightness(delta: Int) {
        if (selectedMenuOptionId == "max_brightness" || selectedMenuOptionId == "night_brightness") {
            selectedMenuOptionId = null
        }
        try {
            val current = Settings.System.getInt(contentResolver, Settings.System.SCREEN_BRIGHTNESS)
            val newValue = (current + delta).coerceIn(1, 255)
            Settings.System.putInt(contentResolver, Settings.System.SCREEN_BRIGHTNESS, newValue)
            val percent = (newValue * 100 / 255)
            showCompactPuckHUD("☀️", percent, autoHide = !isControlling)
            if (newValue >= 255 && !maxLimitHapticFired) {
                maxLimitHapticFired = true
                vibrate()
            } else if (newValue <= 1 && !minLimitHapticFired) {
                minLimitHapticFired = true
                vibrate()
            } else if (newValue in 2..254) {
                minLimitHapticFired = false
                maxLimitHapticFired = false
            }
        } catch (_: Exception) {
            showSmoothHUD("⚠️ Grant Modify System Settings Permission")
        }
    }

    private fun adjustVolume(delta: Int) {
        if (selectedMenuOptionId == "silent") {
            selectedMenuOptionId = null
        }
        val current = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
        val max = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
        val newValue = (current + delta).coerceIn(0, max)
        audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, newValue, 0)
        val percent = if (max > 0) (newValue * 100 / max).coerceIn(0, 100) else 0
        val icon = if (newValue <= 0) "🔇" else "🔊"
        showCompactPuckHUD(icon, percent, autoHide = !isControlling)
        if (newValue >= max && !maxLimitHapticFired) {
            maxLimitHapticFired = true
            vibrate()
        } else if (newValue <= 0 && !minLimitHapticFired) {
            minLimitHapticFired = true
            vibrate()
        } else if (newValue in 1 until max) {
            minLimitHapticFired = false
            maxLimitHapticFired = false
        }
    }

    private fun formatVolumePreview(volume: Int, maxVolume: Int): String {
        val percent = if (maxVolume > 0) (volume * 100 / maxVolume).coerceIn(0, 100) else 0
        val glyph = if (volume <= 0 || percent <= 0) "🔇" else "🔊"
        return "$glyph $percent%"
    }

    private fun ensureHudOverlayCreated() {
        if (hudOverlayView != null) return

        val hudWidthPx = dpToPx(160)
        val hudHeightPx = dpToPx(36)
        val trackWidthPx = dpToPx(50)

        val pill = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutDirection = View.LAYOUT_DIRECTION_LTR
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dpToPx(12), 0, dpToPx(12), 0)
            val bg = GradientDrawable().apply {
                setColor(0xEE251A15.toInt())
                cornerRadius = dpToPx(18).toFloat()
                // Constant color border (no level-based color changes, completely stable)
                setStroke(dpToPx(1.2f), 0x45FFFFFF.toInt())
            }
            background = bg
            layoutParams = LinearLayout.LayoutParams(hudWidthPx, hudHeightPx)
            visibility = View.GONE
        }

        val iconTv = TextView(this).apply {
            textSize = 15f
            gravity = Gravity.CENTER
            layoutDirection = View.LAYOUT_DIRECTION_LTR
            val p = LinearLayout.LayoutParams(dpToPx(24), LinearLayout.LayoutParams.MATCH_PARENT)
            layoutParams = p
        }
        pill.addView(iconTv)

        val track = FrameLayout(this).apply {
            val p = LinearLayout.LayoutParams(trackWidthPx, dpToPx(4)).apply {
                setMargins(dpToPx(8), 0, dpToPx(8), 0)
            }
            layoutParams = p
            background = GradientDrawable().apply {
                setColor(0x358C7A6B.toInt())
                cornerRadius = dpToPx(2).toFloat()
            }
        }
        val fill = View(this).apply {
            layoutParams = FrameLayout.LayoutParams(0, FrameLayout.LayoutParams.MATCH_PARENT)
            background = GradientDrawable().apply {
                setColor(0xFFF0E3D5.toInt())
                cornerRadius = dpToPx(2).toFloat()
            }
        }
        track.addView(fill)
        pill.addView(track)

        val percentTv = TextView(this).apply {
            textSize = 13f
            setTextColor(0xFFFFFFFF.toInt())
            typeface = Typeface.create("sans-serif-medium", Typeface.BOLD)
            gravity = Gravity.CENTER
            includeFontPadding = false
            maxLines = 1
            isSingleLine = true
            layoutDirection = View.LAYOUT_DIRECTION_LTR
            textDirection = View.TEXT_DIRECTION_LTR
            val p = LinearLayout.LayoutParams(dpToPx(46), LinearLayout.LayoutParams.MATCH_PARENT)
            layoutParams = p
        }
        pill.addView(percentTv)

        val overlayParams = WindowManager.LayoutParams(
            hudWidthPx,
            hudHeightPx,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            else
                @Suppress("DEPRECATION")
                WindowManager.LayoutParams.TYPE_PHONE,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
            y = dpToPx(54)
        }

        try {
            windowManager.addView(pill, overlayParams)
            hudOverlayView = pill
            hudGlyphView = iconTv
            hudBarFill = fill
            hudPercentView = percentTv
        } catch (_: Exception) {}
    }

    private fun showCompactPuckHUD(iconGlyph: String, percent: Int, autoHide: Boolean = false) {
        if (isFingerLifted || isHudLockedHidden) {
            // Absolute prohibition: HUD can ONLY ever be shown when finger is actively placed down on the puck
            return
        }
        handler.removeCallbacks(hideHudRunnable)
        ensureHudOverlayCreated()

        val trackWidthPx = dpToPx(50)
        hudOverlayView?.let { pill ->
            pill.visibility = View.VISIBLE
            pill.alpha = 1.0f

            hudGlyphView?.text = iconGlyph
            val clamped = percent.coerceIn(0, 100)
            hudPercentView?.text = "$clamped%"
            hudPercentView?.requestLayout()
            val targetWidth = (trackWidthPx * (clamped / 100f)).toInt()
            hudBarFill?.layoutParams?.width = targetWidth
            hudBarFill?.requestLayout()
        }
    }

    private fun dismissHudFast() {
        handler.removeCallbacks(hideHudRunnable)
        hudOverlayView?.let { view ->
            view.visibility = View.GONE
            view.alpha = 0f
        }
    }

    private fun showSmoothHUD(text: String, isMaxVolume: Boolean = false) {
        val percentMatch = Regex("(\\d+)%").find(text)?.groupValues?.get(1)?.toIntOrNull()
        val glyph = when {
            text.contains("☀️") -> "☀️"
            text.contains("🌑") -> "🌑"
            text.contains("🔇") -> "🔇"
            text.contains("🔊") -> "🔊"
            text.contains("🔔") -> "🔔"
            text.contains("🔒") -> "🔒"
            text.contains("📸") -> "📸"
            else -> "⚡"
        }
        val percent = percentMatch ?: if (text.contains("Night") || text.contains("Silent") || text.contains("Muted")) 0 else 100
        showCompactPuckHUD(glyph, percent, autoHide = !isControlling)
    }

    private fun showHUD(text: String) {
        showSmoothHUD(text)
    }

    private fun removeHudOverlay() {
        hudOverlayView?.let { view ->
            try { windowManager.removeView(view) } catch (_: Exception) {}
            hudOverlayView = null
            hudGlyphView = null
            hudBarFill = null
            hudPercentView = null
        }
    }

    private fun clampAndSanitizePosition(x: Int, y: Int): Pair<Int, Int> {
        updateScreenDimensions()
        val puckW = if (::puckContainer.isInitialized && puckContainer.width > 0) puckContainer.width else dpToPx(prefs.puckSize)
        val puckH = if (::puckContainer.isInitialized && puckContainer.height > 0) puckContainer.height else dpToPx(prefs.puckSize)
        val margin = dpToPx(6)

        val maxAbsX = ((screenWidth - puckW) / 2 - margin).coerceAtLeast(0)
        val maxAbsY = ((screenHeight - puckH) / 2 - margin).coerceAtLeast(0)

        val clampedX = x.coerceIn(-maxAbsX, maxAbsX)
        val clampedY = y.coerceIn(-maxAbsY, maxAbsY)
        return Pair(clampedX, clampedY)
    }

    private fun snapToEdge() {
        val (validX, validY) = clampAndSanitizePosition(params.x, params.y)
        val puckW = if (::puckContainer.isInitialized && puckContainer.width > 0) puckContainer.width else dpToPx(prefs.puckSize)
        val margin = dpToPx(6)
        val maxAbsX = ((screenWidth - puckW) / 2 - margin).coerceAtLeast(0)

        val targetX = if (validX < 0) -maxAbsX else maxAbsX
        savedX = targetX
        savedY = validY

        animatePuckTo(targetX, validY)

        prefs.lastPuckX = targetX + screenWidth / 2
        prefs.lastPuckY = validY + screenHeight / 2
        prefs.wasRightSide = targetX >= 0
    }

    private fun snapToEdgeSmart(startPosX: Int, deltaX: Float, velX: Float) {
        val (validX, validY) = clampAndSanitizePosition(params.x, params.y)
        val puckW = if (::puckContainer.isInitialized && puckContainer.width > 0) puckContainer.width else dpToPx(prefs.puckSize)
        val margin = dpToPx(6)
        val maxAbsX = ((screenWidth - puckW) / 2 - margin).coerceAtLeast(0)

        // Sensitivity: 10% (requires longer drag) to 100% (switches sides with very small drag)
        val s = prefs.sensitivity.coerceIn(10, 100)
        val factor = (s - 10f) / 90f // 0.0 to 1.0
        val minThresholdPx = dpToPx(28).toFloat()
        val maxThresholdPx = (maxAbsX * 0.70f).coerceAtLeast(dpToPx(120).toFloat())
        val dynamicThreshold = maxThresholdPx - factor * (maxThresholdPx - minThresholdPx)

        val isStartedOnRight = startPosX >= 0
        val flingVelocityThreshold = 350f

        val shouldSwitchToOppositeSide = if (isStartedOnRight) {
            // Started on right side: user drags left or flings left
            (-deltaX >= dynamicThreshold) || (validX <= 0) || (velX < -flingVelocityThreshold)
        } else {
            // Started on left side: user drags right or flings right
            (deltaX >= dynamicThreshold) || (validX >= 0) || (velX > flingVelocityThreshold)
        }

        val targetX = if (isStartedOnRight) {
            if (shouldSwitchToOppositeSide) -maxAbsX else maxAbsX
        } else {
            if (shouldSwitchToOppositeSide) maxAbsX else -maxAbsX
        }

        savedX = targetX
        savedY = validY

        animatePuckTo(targetX, validY)

        prefs.lastPuckX = targetX + screenWidth / 2
        prefs.lastPuckY = validY + screenHeight / 2
        prefs.wasRightSide = targetX >= 0
    }

    private fun animatePuckTo(targetX: Int, targetY: Int) {
        currentAnimator?.cancel()
        val startX = params.x
        val startY = params.y
        val dx = (targetX - startX).toFloat()
        val dy = (targetY - startY).toFloat()
        val distance = Math.hypot(dx.toDouble(), dy.toDouble()).toFloat()

        // Scaled fluid duration: between 220ms and 360ms based on distance
        val durationMs = (200L + (distance / screenWidth.coerceAtLeast(1080) * 160L)).toLong().coerceIn(220L, 360L)

        val animator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = durationMs
            // Decelerate with natural gentle cubic easing: fast departure, soft glide docking
            interpolator = PathInterpolator(0.20f, 0.95f, 0.35f, 1.0f)
            addUpdateListener {
                val fraction = it.animatedValue as Float
                params.x = (startX + (targetX - startX) * fraction).toInt()
                params.y = (startY + (targetY - startY) * fraction).toInt()
                try {
                    windowManager.updateViewLayout(puckView, params)
                } catch (_: Exception) {}
                if (isMenuShowing) {
                    updateMenuAttachment(animate = false)
                }
            }
            addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                    currentAnimator = null
                    if (::puckContainer.isInitialized) {
                        puckContainer.scaleX = 1.0f
                        puckContainer.scaleY = 1.0f
                    }
                    if (isMenuShowing) {
                        updateMenuAttachment(animate = false)
                    }
                }
            })
        }
        animator.start()
        currentAnimator = animator
    }

    private fun setFlashlight(enable: Boolean) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            try {
                val camId = mainCameraId ?: cameraManager?.cameraIdList?.firstOrNull()
                if (camId != null) {
                    mainCameraId = camId
                    cameraManager?.setTorchMode(camId, enable)
                    isFlashlightOn = enable
                }
            } catch (_: Exception) {}
        }
    }

    private fun setAutoRotate(enable: Boolean) {
        try {
            Settings.System.putInt(
                contentResolver,
                Settings.System.ACCELEROMETER_ROTATION,
                if (enable) 1 else 0
            )
        } catch (_: Exception) {
            requestWriteSettingsPermission()
        }
    }

    private fun lockScreen() {
        val isServiceEnabledInOS = LockScreenHelper.isLockPermissionGranted(this)
        if (isServiceEnabledInOS) {
            LockScreenHelper.performLock(this)
            showHUD("🔒 Screen Locked")
        } else {
            showHUD("⚠️ Enable Accessibility in Settings")
            Toast.makeText(this, "Enable 'Smart Puck Accessibility Lock' in Accessibility settings", Toast.LENGTH_SHORT).show()
        }
    }

    private fun takeInstantScreenshot() {
        val isServiceEnabledInOS = LockScreenHelper.isAccessibilityServiceActive(this)
        if (!isServiceEnabledInOS) {
            showHUD("⚠️ Enable Accessibility in Settings")
            Toast.makeText(this, "Enable 'Smart Puck Accessibility Lock' in Accessibility settings", Toast.LENGTH_SHORT).show()
            return
        }

        // 1. Instantly hide puck before capturing
        if (::puckContainer.isInitialized) {
            puckContainer.visibility = View.INVISIBLE
        }

        // 2. Micro-delay so the screen renders completely clean without puck or menu
        handler.postDelayed({
            LockScreenHelper.performScreenshot(this)
            // 3. Restore puck visibility cleanly after screen capture completes
            handler.postDelayed({
                if (::puckContainer.isInitialized) {
                    puckContainer.visibility = View.VISIBLE
                    puckContainer.alpha = prefs.puckOpacity / 100f
                }
            }, 750)
        }, 150)
    }

    private fun openMainSettings() {
        val intent = Intent(this, MainActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        startActivity(intent)
    }

    private var selectedMenuOptionId: String? = null

    private fun showPuckMenu() {
        if (isMenuShowing) {
            hidePuckMenu()
            return
        }

        val context = this
        updateScreenDimensions()

        val puckLoc = IntArray(2)
        puckContainer.getLocationOnScreen(puckLoc)
        val puckLeft = puckLoc[0]
        val puckTop = puckLoc[1]
        val puckW = if (puckContainer.width > 0) puckContainer.width else dpToPx(prefs.puckSize)
        val puckH = if (puckContainer.height > 0) puckContainer.height else dpToPx(prefs.puckSize)
        val puckRight = puckLeft + puckW
        val puckBottom = puckTop + puckH
        val puckCenterX = puckLeft + puckW / 2
        val puckCenterY = puckTop + puckH / 2

        val statusBarMargin = dpToPx(38)
        val navBarMargin = dpToPx(48)
        val screenEdgeMargin = dpToPx(8)
        val arrowLength = dpToPx(12)
        val puckGap = dpToPx(4) // Close proximity while strictly detached (no contact with ball)

        // Single-column card dimensions: compact, elegant, displays ~4.5 rows to invite smooth scrolling
        val idealCardW = dpToPx(270).coerceAtMost(screenWidth - dpToPx(24))
        val idealCardH = dpToPx(270)
        val maxAllowedH = (screenHeight - statusBarMargin - navBarMargin).coerceAtLeast(dpToPx(160))
        val actualCardH = idealCardH.coerceAtMost(maxAllowedH)

        val side: ConnectorSide
        val windowX: Int
        val windowY: Int
        val totalW: Int
        val totalH: Int
        val localOffset: Float

        val isTopZone = (puckCenterY < screenHeight * 0.35f)
        val isBottomZone = (puckCenterY > screenHeight * 0.65f)
        val isRightSide = (puckCenterX >= screenWidth / 2)

        if (isTopZone) {
            // Button near top: menu opens BELOW the puck, detached arrow on TOP edge
            side = ConnectorSide.TOP
            totalW = idealCardW
            totalH = actualCardH + arrowLength
            windowY = (puckBottom + puckGap).coerceAtLeast(statusBarMargin)
            windowX = if (isRightSide) {
                (puckRight - totalW).coerceIn(screenEdgeMargin, screenWidth - screenEdgeMargin - totalW)
            } else {
                puckLeft.coerceIn(screenEdgeMargin, screenWidth - screenEdgeMargin - totalW)
            }
            localOffset = (puckCenterX - windowX).toFloat()
        } else if (isBottomZone) {
            // Button near bottom: menu opens ABOVE the puck, detached arrow on BOTTOM edge
            side = ConnectorSide.BOTTOM
            totalW = idealCardW
            totalH = actualCardH + arrowLength
            windowY = (puckTop - totalH - puckGap).coerceAtMost(screenHeight - navBarMargin - totalH)
            windowX = if (isRightSide) {
                (puckRight - totalW).coerceIn(screenEdgeMargin, screenWidth - screenEdgeMargin - totalW)
            } else {
                puckLeft.coerceIn(screenEdgeMargin, screenWidth - screenEdgeMargin - totalW)
            }
            localOffset = (puckCenterX - windowX).toFloat()
        } else {
            // Button in the middle zone:
            if (isRightSide) {
                // Button in middle-right: menu opens to the LEFT of puck, detached arrow on RIGHT edge
                side = ConnectorSide.RIGHT
                totalW = idealCardW + arrowLength
                totalH = actualCardH
                windowX = (puckLeft - totalW - puckGap).coerceAtLeast(screenEdgeMargin)
                val desiredY = puckCenterY - totalH / 2
                windowY = desiredY.coerceIn(statusBarMargin, (screenHeight - navBarMargin - totalH).coerceAtLeast(statusBarMargin))
                localOffset = (puckCenterY - windowY).toFloat()
            } else {
                // Button in middle-left: menu opens to the RIGHT of puck, detached arrow on LEFT edge
                side = ConnectorSide.LEFT
                totalW = idealCardW + arrowLength
                totalH = actualCardH
                windowX = (puckRight + puckGap).coerceAtMost(screenWidth - screenEdgeMargin - totalW)
                val desiredY = puckCenterY - totalH / 2
                windowY = desiredY.coerceIn(statusBarMargin, (screenHeight - navBarMargin - totalH).coerceAtLeast(statusBarMargin))
                localOffset = (puckCenterY - windowY).toFloat()
            }
        }

        val layout = AttachedMenuLayout(context)
        layout.onOutsideTouch = {
            hidePuckMenu()
        }
        layout.updateGeometry(side, idealCardW, actualCardH, arrowLength, localOffset)

        // Single-Column Scrollable List
        val contentLayout = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dpToPx(4), dpToPx(6), dpToPx(4), dpToPx(6))
        }

        // Helpers for modern row styling
        fun createLeadingContainer(isDrawable: Boolean, resId: Int = 0, emoji: String = "", isActive: Boolean = false): FrameLayout {
            val frame = FrameLayout(context).apply {
                background = GradientDrawable().apply {
                    setColor(0xFF2A1E18.toInt()) // Deep cocoa background
                    cornerRadius = dpToPx(10).toFloat()
                }
                layoutParams = LinearLayout.LayoutParams(dpToPx(34), dpToPx(34))
            }
            if (isDrawable) {
                val iv = ImageView(context).apply {
                    setImageResource(resId)
                    val p = dpToPx(7)
                    setPadding(p, p, p, p)
                    scaleType = ImageView.ScaleType.FIT_CENTER
                    val tint = if (isActive) 0xFFE5BC82.toInt() else 0xFFD6C5B6.toInt()
                    setColorFilter(tint)
                    layoutParams = FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT)
                }
                frame.addView(iv)
            } else {
                val tv = TextView(context).apply {
                    text = emoji
                    textSize = 16f
                    gravity = Gravity.CENTER
                    setTextColor(if (isActive) 0xFFE5BC82.toInt() else 0xFFD6C5B6.toInt())
                    layoutParams = FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT)
                }
                frame.addView(tv)
            }
            return frame
        }

        fun createTextColumn(primary: String, secondary: String = ""): LinearLayout {
            return LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER_VERTICAL
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
                    setMargins(dpToPx(12), 0, dpToPx(8), 0)
                }
                val tvTitle = TextView(context).apply {
                    text = primary
                    textSize = 14.5f
                    setTextColor(0xFFF5EBE1.toInt())
                    typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
                    maxLines = 1
                }
                addView(tvTitle)
                if (secondary.isNotEmpty()) {
                    val tvSub = TextView(context).apply {
                        text = secondary
                        textSize = 11f
                        setTextColor(0xFFA69385.toInt())
                        typeface = Typeface.create("sans-serif", Typeface.NORMAL)
                        maxLines = 1
                    }
                    addView(tvSub)
                }
            }
        }

        fun createToggleSwitch(initialOn: Boolean, onToggle: (Boolean) -> Unit): FrameLayout {
            val trackW = dpToPx(42)
            val trackH = dpToPx(22)
            val thumbS = dpToPx(18)
            val maxSlide = dpToPx(20)

            var isOn = initialOn

            val trackBg = GradientDrawable().apply {
                setColor(if (isOn) 0xFF7D593E.toInt() else 0xFF3A2B23.toInt())
                cornerRadius = dpToPx(11).toFloat()
            }
            val track = FrameLayout(context).apply {
                background = trackBg
                layoutParams = LinearLayout.LayoutParams(trackW, trackH)
            }

            val thumbBg = GradientDrawable().apply {
                setColor(if (isOn) 0xFFF8EFE6.toInt() else 0xFF8E7C6E.toInt())
                cornerRadius = dpToPx(9).toFloat()
            }
            val thumb = View(context).apply {
                background = thumbBg
                val p = FrameLayout.LayoutParams(thumbS, thumbS).apply {
                    gravity = Gravity.CENTER_VERTICAL or Gravity.START
                    setMargins(dpToPx(2), 0, dpToPx(2), 0)
                }
                layoutParams = p
                translationX = if (isOn) maxSlide.toFloat() else 0f
            }
            track.addView(thumb)

            track.setOnClickListener {
                isOn = !isOn
                trackBg.setColor(if (isOn) 0xFF7D593E.toInt() else 0xFF3A2B23.toInt())
                thumbBg.setColor(if (isOn) 0xFFF8EFE6.toInt() else 0xFF8E7C6E.toInt())
                thumb.animate().translationX(if (isOn) maxSlide.toFloat() else 0f).setDuration(140).start()
                onToggle(isOn)
            }
            return track
        }

        fun createSegmentedChips(
            opt1: String,
            opt2: String,
            selectedIndex: Int,
            onSelect: (Int) -> Unit
        ): LinearLayout {
            val container = LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
            }
            fun buildChip(idx: Int, label: String): FrameLayout {
                val isSel = (idx == selectedIndex)
                val bg = GradientDrawable().apply {
                    setColor(if (isSel) 0xFF543E32.toInt() else 0xFF251A14.toInt())
                    cornerRadius = dpToPx(12).toFloat()
                }
                val chip = FrameLayout(context).apply {
                    background = bg
                    val p = LinearLayout.LayoutParams(dpToPx(54), dpToPx(26)).apply {
                        if (idx > 0) setMargins(dpToPx(4), 0, 0, 0)
                    }
                    layoutParams = p
                }
                val tv = TextView(context).apply {
                    text = label
                    textSize = 11f
                    typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
                    gravity = Gravity.CENTER
                    setTextColor(if (isSel) 0xFFF8EFE6.toInt() else 0xFF8E7C6E.toInt())
                    layoutParams = FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT)
                }
                chip.addView(tv)
                chip.setOnClickListener { onSelect(idx) }
                return chip
            }
            container.addView(buildChip(0, opt1))
            container.addView(buildChip(1, opt2))
            return container
        }

        fun createActionButton(label: String, onClick: () -> Unit): FrameLayout {
            val frame = FrameLayout(context).apply {
                background = GradientDrawable().apply {
                    setColor(0xFF33251E.toInt())
                    cornerRadius = dpToPx(12).toFloat()
                    setStroke(dpToPx(1), 0xFF46352C.toInt())
                }
                layoutParams = LinearLayout.LayoutParams(dpToPx(62), dpToPx(26))
            }
            val tv = TextView(context).apply {
                text = label
                textSize = 11f
                typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
                gravity = Gravity.CENTER
                setTextColor(0xFFE8DCD1.toInt())
                layoutParams = FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT)
            }
            frame.addView(tv)
            frame.setOnClickListener { onClick() }
            return frame
        }

        fun createRowContainer(): LinearLayout {
            return LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(dpToPx(14), dpToPx(2), dpToPx(14), dpToPx(2))
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    dpToPx(52)
                )
            }
        }

        fun createDivider(): View {
            return View(context).apply {
                val p = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    dpToPx(1)
                ).apply {
                    setMargins(dpToPx(58), 0, dpToPx(12), 0)
                }
                layoutParams = p
                setBackgroundColor(0xFF30231D.toInt())
            }
        }

        // --- ROW BUILDERS IN EXACT REQUESTED ORDER ---
        // 1. Flashlight — toggle switch only
        val rowFlashlight = createRowContainer().apply {
            val icon = createLeadingContainer(false, emoji = "🔦", isActive = isFlashlightOn)
            val text = createTextColumn("Flashlight", if (isFlashlightOn) "On" else "Off")
            val toggle = createToggleSwitch(isFlashlightOn) { isOn ->
                vibrate()
                setFlashlight(isOn)
                val iv = (icon.getChildAt(0) as? TextView)
                iv?.setTextColor(if (isOn) 0xFFE5BC82.toInt() else 0xFFD6C5B6.toInt())
                val sub = (text.getChildAt(1) as? TextView)
                sub?.text = if (isOn) "On" else "Off"
            }
            addView(icon)
            addView(text)
            addView(toggle)
        }

        // 2. Screenshot — action button only
        val rowScreenshot = createRowContainer().apply {
            val icon = createLeadingContainer(true, resId = R.drawable.ic_puck_screenshot)
            val text = createTextColumn("Screenshot", "Fast clean capture")
            val action = createActionButton("Capture") {
                vibrate()
                hidePuckMenu()
                takeInstantScreenshot()
            }
            addView(icon)
            addView(text)
            addView(action)
        }

        // 3. Do Not Disturb — toggle switch only
        val rowDnd = createRowContainer().apply {
            var isDnd = DndSyncManager.checkIsDndActive(context)
            val icon = createLeadingContainer(false, emoji = "🌙", isActive = isDnd)
            val text = createTextColumn("Do Not Disturb", if (isDnd) "Total Silence" else "Off")
            val toggle = createToggleSwitch(isDnd) { enable ->
                vibrate()
                val hasPerm = DndSyncManager.isNotificationPolicyAccessGranted(context)
                if (!hasPerm) {
                    requestDndPermission()
                } else {
                    setDndMode(enable)
                    val iv = (icon.getChildAt(0) as? TextView)
                    iv?.setTextColor(if (enable) 0xFFE5BC82.toInt() else 0xFFD6C5B6.toInt())
                    val sub = (text.getChildAt(1) as? TextView)
                    sub?.text = if (enable) "Total Silence" else "Off"
                }
            }
            addView(icon)
            addView(text)
            addView(toggle)
        }

        // 4. Audio Profile — compact Silent and Normal selection controls
        val rowAudio = createRowContainer().apply {
            val curRinger = audioManager.ringerMode
            val curIdx = when (curRinger) {
                AudioManager.RINGER_MODE_SILENT -> 0
                AudioManager.RINGER_MODE_NORMAL -> 1
                else -> -1
            }
            val icon = createLeadingContainer(false, emoji = "🔔", isActive = (curIdx == 0))
            val text = createTextColumn("Audio Profile", if (curIdx == 0) "Silent" else "Normal")
            val chips = createSegmentedChips("Silent", "Normal", curIdx) { idx ->
                vibrate()
                if (idx == 0) {
                    try {
                        audioManager.ringerMode = AudioManager.RINGER_MODE_SILENT
                    } catch (_: SecurityException) {
                        requestDndPermission()
                    }
                } else {
                    try {
                        audioManager.ringerMode = AudioManager.RINGER_MODE_NORMAL
                    } catch (_: SecurityException) {
                        requestDndPermission()
                    }
                }
                hidePuckMenu()
            }
            addView(icon)
            addView(text)
            addView(chips)
        }

        // 5. Brightness — compact Sun and Night presets
        val rowBrightness = createRowContainer().apply {
            val curB = try {
                Settings.System.getInt(contentResolver, Settings.System.SCREEN_BRIGHTNESS)
            } catch (_: Exception) { -1 }
            val bIdx = when {
                curB >= 240 -> 0
                curB in 1..25 -> 1
                else -> -1
            }
            val icon = createLeadingContainer(false, emoji = "☀️", isActive = true)
            val text = createTextColumn("Brightness", if (bIdx == 0) "Max 100%" else if (bIdx == 1) "Night 5%" else "Preset")
            val chips = createSegmentedChips("Max", "Night", bIdx) { idx ->
                vibrate()
                if (idx == 0) {
                    try {
                        Settings.System.putInt(contentResolver, Settings.System.SCREEN_BRIGHTNESS, 255)
                        showHUD("☀️ Max Brightness (100%)")
                    } catch (_: SecurityException) {
                        requestWriteSettingsPermission()
                    }
                } else {
                    try {
                        Settings.System.putInt(contentResolver, Settings.System.SCREEN_BRIGHTNESS, 12)
                        showHUD("🌑 Night Brightness (5%)")
                    } catch (_: SecurityException) {
                        requestWriteSettingsPermission()
                    }
                }
                hidePuckMenu()
            }
            addView(icon)
            addView(text)
            addView(chips)
        }

        // 6. Auto-Rotate — toggle switch only
        val rowAutoRotate = createRowContainer().apply {
            val isRotate = try {
                Settings.System.getInt(contentResolver, Settings.System.ACCELEROMETER_ROTATION, 0) == 1
            } catch (_: Exception) { false }
            val icon = createLeadingContainer(false, emoji = "🔄", isActive = isRotate)
            val text = createTextColumn("Auto-Rotate", if (isRotate) "On" else "Locked")
            val toggle = createToggleSwitch(isRotate) { enable ->
                vibrate()
                setAutoRotate(enable)
                val iv = (icon.getChildAt(0) as? TextView)
                iv?.setTextColor(if (enable) 0xFFE5BC82.toInt() else 0xFFD6C5B6.toInt())
                val sub = (text.getChildAt(1) as? TextView)
                sub?.text = if (enable) "On" else "Locked"
            }
            addView(icon)
            addView(text)
            addView(toggle)
        }

        // 7. Smart Puck App — action button only
        val rowApp = createRowContainer().apply {
            val icon = createLeadingContainer(false, emoji = "⚙️")
            val text = createTextColumn("Smart Puck App", "Open settings")
            val action = createActionButton("Open") {
                vibrate()
                hidePuckMenu()
                openMainSettings()
            }
            addView(icon)
            addView(text)
            addView(action)
        }

        // 8. Lock Screen — action button only
        val rowLock = createRowContainer().apply {
            val icon = createLeadingContainer(true, resId = R.drawable.ic_puck_lock)
            val text = createTextColumn("Lock Screen", "Instant device lock")
            val action = createActionButton("Lock") {
                vibrate()
                hidePuckMenu()
                lockScreen()
            }
            addView(icon)
            addView(text)
            addView(action)
        }

        rowFlashlight.tag = "flashlight"
        rowScreenshot.tag = "screenshot"
        rowDnd.tag = "dnd"
        rowAudio.tag = "audio"
        rowBrightness.tag = "brightness"
        rowAutoRotate.tag = "auto_rotate"
        rowApp.tag = "smart_puck_app"
        rowLock.tag = "lock_screen"

        val rowsMap = mapOf(
            "flashlight" to rowFlashlight,
            "screenshot" to rowScreenshot,
            "dnd" to rowDnd,
            "audio" to rowAudio,
            "brightness" to rowBrightness,
            "auto_rotate" to rowAutoRotate,
            "smart_puck_app" to rowApp,
            "lock_screen" to rowLock
        )

        // Read real-time dynamic menu order from preferences
        val menuOrder = prefs.menuOrder
        val orderedRows = menuOrder.mapNotNull { rowsMap[it] }
        val remaining = rowsMap.filterKeys { !menuOrder.contains(it) }.values
        val allRows = orderedRows + remaining

        for (i in allRows.indices) {
            contentLayout.addView(allRows[i])
            if (i < allRows.size - 1) {
                contentLayout.addView(createDivider())
            }
        }
        menuContentLayout = contentLayout

        // Native vertical scroll without any visible scrollbar
        val scrollView = ScrollView(context).apply {
            isVerticalScrollBarEnabled = false
            isHorizontalScrollBarEnabled = false
            overScrollMode = View.OVER_SCROLL_NEVER
            scrollBarSize = 0
            addView(contentLayout)
        }

        layout.addView(scrollView)

        val wParams = WindowManager.LayoutParams(
            totalW,
            totalH,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            else
                WindowManager.LayoutParams.TYPE_PHONE,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                    WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = windowX
            y = windowY
        }

        // Pivot towards connector arrow tip for smooth expansion
        layout.pivotX = when (side) {
            ConnectorSide.TOP -> localOffset
            ConnectorSide.BOTTOM -> localOffset
            ConnectorSide.RIGHT -> totalW.toFloat()
            ConnectorSide.LEFT -> 0f
        }
        layout.pivotY = when (side) {
            ConnectorSide.TOP -> 0f
            ConnectorSide.BOTTOM -> totalH.toFloat()
            ConnectorSide.RIGHT -> localOffset
            ConnectorSide.LEFT -> localOffset
        }
        layout.alpha = 0f
        layout.scaleX = 0.88f
        layout.scaleY = 0.88f

        try {
            windowManager.addView(layout, wParams)
            attachedMenuLayout = layout
            menuParams = wParams
            isMenuShowing = true

            layout.animate()
                .alpha(1f)
                .scaleX(1f)
                .scaleY(1f)
                .setDuration(180)
                .setInterpolator(DecelerateInterpolator(1.8f))
                .start()
        } catch (_: Exception) {}
    }

    private fun hidePuckMenu() {
        if (!isMenuShowing && attachedMenuLayout == null) return
        isMenuShowing = false
        val view = attachedMenuLayout
        attachedMenuLayout = null
        menuParams = null
        menuContentLayout = null
        if (view != null) {
            try {
                view.animate()?.cancel()
                view.visibility = View.GONE
                windowManager.removeViewImmediate(view)
            } catch (_: Exception) {
                try {
                    windowManager.removeView(view)
                } catch (_: Exception) {}
            }
        }
    }

    private fun refreshMenuOrderInRealTime() {
        val content = menuContentLayout ?: return
        if (!isMenuShowing) return
        val currentRows = mutableMapOf<String, View>()
        for (i in 0 until content.childCount) {
            val child = content.getChildAt(i)
            val tag = child.tag as? String
            if (tag != null) {
                currentRows[tag] = child
            }
        }
        if (currentRows.isEmpty()) return

        content.removeAllViews()
        val order = prefs.menuOrder
        val orderedRows = order.mapNotNull { currentRows[it] }
        val remaining = currentRows.filterKeys { !order.contains(it) }.values
        val all = orderedRows + remaining

        for (i in all.indices) {
            content.addView(all[i])
            if (i < all.size - 1) {
                val divider = View(this).apply {
                    val p = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        dpToPx(1)
                    ).apply {
                        setMargins(dpToPx(58), 0, dpToPx(12), 0)
                    }
                    layoutParams = p
                    setBackgroundColor(0xFF30231D.toInt())
                }
                content.addView(divider)
            }
        }
    }

    private fun updateMenuAttachment(animate: Boolean = false) {
        val layout = attachedMenuLayout ?: return
        val params = menuParams ?: return
        if (!isMenuShowing) return

        updateScreenDimensions()

        val puckLoc = IntArray(2)
        puckContainer.getLocationOnScreen(puckLoc)
        val puckLeft = puckLoc[0]
        val puckTop = puckLoc[1]
        val puckW = if (puckContainer.width > 0) puckContainer.width else dpToPx(prefs.puckSize)
        val puckH = if (puckContainer.height > 0) puckContainer.height else dpToPx(prefs.puckSize)
        val puckRight = puckLeft + puckW
        val puckBottom = puckTop + puckH
        val puckCenterX = puckLeft + puckW / 2
        val puckCenterY = puckTop + puckH / 2

        val statusBarMargin = dpToPx(38)
        val navBarMargin = dpToPx(48)
        val screenEdgeMargin = dpToPx(8)
        val arrowLength = dpToPx(12)
        val puckGap = dpToPx(4) // Close proximity while strictly detached (no contact with ball)

        val idealCardW = dpToPx(270).coerceAtMost(screenWidth - dpToPx(24))
        val idealCardH = dpToPx(270)
        val maxAllowedH = (screenHeight - statusBarMargin - navBarMargin).coerceAtLeast(dpToPx(160))
        val actualCardH = idealCardH.coerceAtMost(maxAllowedH)

        val side: ConnectorSide
        val windowX: Int
        val windowY: Int
        val totalW: Int
        val totalH: Int
        val localOffset: Float

        val isTopZone = (puckCenterY < screenHeight * 0.35f)
        val isBottomZone = (puckCenterY > screenHeight * 0.65f)
        val isRightSide = (puckCenterX >= screenWidth / 2)

        if (isTopZone) {
            side = ConnectorSide.TOP
            totalW = idealCardW
            totalH = actualCardH + arrowLength
            windowY = (puckBottom + puckGap).coerceAtLeast(statusBarMargin)
            windowX = if (isRightSide) {
                (puckRight - totalW).coerceIn(screenEdgeMargin, screenWidth - screenEdgeMargin - totalW)
            } else {
                puckLeft.coerceIn(screenEdgeMargin, screenWidth - screenEdgeMargin - totalW)
            }
            localOffset = (puckCenterX - windowX).toFloat()
        } else if (isBottomZone) {
            side = ConnectorSide.BOTTOM
            totalW = idealCardW
            totalH = actualCardH + arrowLength
            windowY = (puckTop - totalH - puckGap).coerceAtMost(screenHeight - navBarMargin - totalH)
            windowX = if (isRightSide) {
                (puckRight - totalW).coerceIn(screenEdgeMargin, screenWidth - screenEdgeMargin - totalW)
            } else {
                puckLeft.coerceIn(screenEdgeMargin, screenWidth - screenEdgeMargin - totalW)
            }
            localOffset = (puckCenterX - windowX).toFloat()
        } else {
            if (isRightSide) {
                side = ConnectorSide.RIGHT
                totalW = idealCardW + arrowLength
                totalH = actualCardH
                windowX = (puckLeft - totalW - puckGap).coerceAtLeast(screenEdgeMargin)
                val desiredY = puckCenterY - totalH / 2
                windowY = desiredY.coerceIn(statusBarMargin, (screenHeight - navBarMargin - totalH).coerceAtLeast(statusBarMargin))
                localOffset = (puckCenterY - windowY).toFloat()
            } else {
                side = ConnectorSide.LEFT
                totalW = idealCardW + arrowLength
                totalH = actualCardH
                windowX = (puckRight + puckGap).coerceAtMost(screenWidth - screenEdgeMargin - totalW)
                val desiredY = puckCenterY - totalH / 2
                windowY = desiredY.coerceIn(statusBarMargin, (screenHeight - navBarMargin - totalH).coerceAtLeast(statusBarMargin))
                localOffset = (puckCenterY - windowY).toFloat()
            }
        }

        params.x = windowX
        params.y = windowY
        params.width = totalW
        params.height = totalH

        try {
            windowManager.updateViewLayout(layout, params)
        } catch (_: Exception) {}

        layout.updateGeometry(side, idealCardW, actualCardH, arrowLength, localOffset)
    }

    private fun removeMenuOverlay() {
        attachedMenuLayout?.let { view ->
            try {
                windowManager.removeView(view)
            } catch (_: Exception) {}
            attachedMenuLayout = null
            menuParams = null
        }
    }

    private fun requestDndPermission() {
        DndSyncManager.openDndSettings(this)
    }

    private fun setDndMode(enable: Boolean) {
        val hasPerm = DndSyncManager.isNotificationPolicyAccessGranted(this)
        if (!hasPerm) {
            showHUD("⚠️ DND permission required")
            Toast.makeText(this, "Enable 'Do Not Disturb' access in Settings", Toast.LENGTH_SHORT).show()
            return
        }
        // Directly control system-level DND mode silently and instantly.
        // No custom preview, popup, or visual overlay is shown; the native Android
        // status bar and Quick Settings tile provide the native confirmation.
        DndSyncManager.setDndActive(this, enable)
    }

    private fun requestWriteSettingsPermission() {
        showHUD("⚠️ Grant Modify System Settings Permission")
        Toast.makeText(this, "Enable 'Modify system settings' in Settings", Toast.LENGTH_SHORT).show()
    }

    private fun vibrate() {
        val strength = prefs.vibrationStrength
        if (strength <= 0) return
        val duration = (8 + strength * 0.4).toLong()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val effect = VibrationEffect.createOneShot(duration, strength.coerceIn(1, 255))
            vibrator?.vibrate(effect)
        } else {
            @Suppress("DEPRECATION")
            vibrator?.vibrate(duration)
        }
    }

    private fun dpToPx(dp: Int): Int {
        return (dp * resources.displayMetrics.density).toInt()
    }

    private fun dpToPx(dp: Float): Int {
        return (dp * resources.displayMetrics.density).toInt()
    }

    private fun registerReceivers() {
        val filter = IntentFilter()
        filter.addAction(Intent.ACTION_CONFIGURATION_CHANGED)
        registerReceiver(configReceiver, filter)

        val volFilter = IntentFilter().apply {
            addAction("android.media.VOLUME_CHANGED_ACTION")
            addAction(AudioManager.RINGER_MODE_CHANGED_ACTION)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                addAction(NotificationManager.ACTION_INTERRUPTION_FILTER_CHANGED)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                addAction(NotificationManager.ACTION_NOTIFICATION_POLICY_ACCESS_GRANTED_CHANGED)
            }
        }
        try {
            registerReceiver(volumeReceiver, volFilter)
        } catch (_: Exception) {}

        try {
            contentResolver.registerContentObserver(
                Settings.System.getUriFor(Settings.System.SCREEN_BRIGHTNESS),
                false,
                brightnessObserver
            )
        } catch (_: Exception) {}

        val batFilter = IntentFilter().apply {
            addAction(Intent.ACTION_POWER_CONNECTED)
            addAction(Intent.ACTION_POWER_DISCONNECTED)
            addAction(Intent.ACTION_BATTERY_CHANGED)
        }
        try {
            val sticky = registerReceiver(batteryReceiver, batFilter)
            sticky?.let { intent ->
                val status = intent.getIntExtra(android.os.BatteryManager.EXTRA_STATUS, -1)
                val plugged = intent.getIntExtra(android.os.BatteryManager.EXTRA_PLUGGED, -1)
                isCharging = (status == android.os.BatteryManager.BATTERY_STATUS_CHARGING ||
                              status == android.os.BatteryManager.BATTERY_STATUS_FULL ||
                              plugged > 0)
                applyPuckStyle()
            }
        } catch (_: Exception) {}
    }

    private val batteryReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            val action = intent?.action ?: return
            if (action == Intent.ACTION_POWER_CONNECTED) {
                isCharging = true
                handler.post { applyPuckStyle() }
            } else if (action == Intent.ACTION_POWER_DISCONNECTED) {
                isCharging = false
                handler.post { applyPuckStyle() }
            } else if (action == Intent.ACTION_BATTERY_CHANGED) {
                val status = intent.getIntExtra(android.os.BatteryManager.EXTRA_STATUS, -1)
                val plugged = intent.getIntExtra(android.os.BatteryManager.EXTRA_PLUGGED, -1)
                val nowCharging = (status == android.os.BatteryManager.BATTERY_STATUS_CHARGING ||
                                   status == android.os.BatteryManager.BATTERY_STATUS_FULL ||
                                   plugged > 0)
                if (isCharging != nowCharging) {
                    isCharging = nowCharging
                    handler.post { applyPuckStyle() }
                }
            }
        }
    }

    private val brightnessObserver = object : ContentObserver(Handler(Looper.getMainLooper())) {
        override fun onChange(selfChange: Boolean) {
            super.onChange(selfChange)
            val current = try {
                Settings.System.getInt(contentResolver, Settings.System.SCREEN_BRIGHTNESS)
            } catch (_: Exception) { -1 }

            if (selectedMenuOptionId == "max_brightness" && current != 255) {
                selectedMenuOptionId = null
            } else if (selectedMenuOptionId == "night_brightness" && current != 10) {
                selectedMenuOptionId = null
            }
        }
    }

    private val volumeReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            val action = intent?.action ?: return
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M &&
                (action == NotificationManager.ACTION_INTERRUPTION_FILTER_CHANGED ||
                 (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P && action == NotificationManager.ACTION_NOTIFICATION_POLICY_ACCESS_GRANTED_CHANGED))) {
                DndSyncManager.refresh(this@FloatingPuckService)
                if (selectedMenuOptionId == "dnd") {
                    val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                    if (nm.currentInterruptionFilter == NotificationManager.INTERRUPTION_FILTER_ALL) {
                        selectedMenuOptionId = null
                    }
                }
            }
            if (action == "android.media.VOLUME_CHANGED_ACTION" || action == AudioManager.RINGER_MODE_CHANGED_ACTION) {
                if (selectedMenuOptionId == "silent") {
                    val currentRinger = audioManager.ringerMode
                    if (currentRinger != AudioManager.RINGER_MODE_SILENT) {
                        selectedMenuOptionId = null
                    } else if (action == "android.media.VOLUME_CHANGED_ACTION") {
                        selectedMenuOptionId = null
                    }
                }
                if (action == "android.media.VOLUME_CHANGED_ACTION" && !isControlling) {
                    val cur = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
                    val max = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
                    val isMax = (cur >= max)
                    showSmoothHUD(formatVolumePreview(cur, max), isMaxVolume = isMax)
                }
            }
        }
    }

    private val configReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == Intent.ACTION_CONFIGURATION_CHANGED) {
                updateScreenDimensions()
                if (::params.isInitialized && ::puckView.isInitialized && puckView.isAttachedToWindow) {
                    val (safeX, safeY) = clampAndSanitizePosition(params.x, params.y)
                    params.x = safeX
                    params.y = safeY
                    savedX = safeX
                    savedY = safeY
                    try {
                        windowManager.updateViewLayout(puckView, params)
                    } catch (_: Exception) {}
                    if (isMenuShowing) {
                        updateMenuAttachment(animate = false)
                    }
                }
            }
        }
    }

    private fun startFullscreenChecker() {
        handler.postDelayed(object : Runnable {
            override fun run() {
                checkFullscreenState()
                ensurePuckAttached()
                handler.postDelayed(this, 1000)
            }
        }, 1000)
    }

    private fun ensurePuckAttached() {
        if (::prefs.isInitialized && prefs.isServiceEnabled && ::puckView.isInitialized) {
            if (!puckView.isAttachedToWindow) {
                try {
                    windowManager.addView(puckView, params)
                } catch (_: Exception) {}
            }
        }
    }

    private fun checkFullscreenState() {
        val display = windowManager.defaultDisplay
        val realMetrics = DisplayMetrics()
        val metrics = DisplayMetrics()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            display.getRealMetrics(realMetrics)
            display.getMetrics(metrics)
        } else {
            @Suppress("DEPRECATION")
            display.getRealMetrics(realMetrics)
            @Suppress("DEPRECATION")
            display.getMetrics(metrics)
        }

        val threshold = dpToPx(24)
        val isFullscreenNow = metrics.heightPixels >= realMetrics.heightPixels - threshold

        if (isFullscreenNow != wasFullscreen) {
            wasFullscreen = isFullscreenNow
            updateScreenDimensions()

            val (clampedX, clampedY) = clampAndSanitizePosition(params.x, params.y)
            val puckW = if (::puckContainer.isInitialized && puckContainer.width > 0) puckContainer.width else dpToPx(prefs.puckSize)
            val margin = dpToPx(6)
            val maxAbsX = ((screenWidth - puckW) / 2 - margin).coerceAtLeast(0)
            val sideX = if (clampedX < 0) -maxAbsX else maxAbsX

            savedX = sideX
            savedY = clampedY
            animatePuckTo(sideX, clampedY)
        }
    }
}
