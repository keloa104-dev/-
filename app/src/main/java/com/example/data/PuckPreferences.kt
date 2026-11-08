package com.example.data

import android.content.Context
import android.content.SharedPreferences
import android.graphics.Color
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class PuckPreferences(context: Context) {
    private val prefs: SharedPreferences =
        context.getSharedPreferences("smart_puck_prefs", Context.MODE_PRIVATE)

    private val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, _ ->
        notifyState()
    }

    init {
        prefs.registerOnSharedPreferenceChangeListener(listener)
    }

    // State flows for Compose reactive updates
    private val _isServiceEnabledState = MutableStateFlow(isServiceEnabled)
    val isServiceEnabledState: StateFlow<Boolean> = _isServiceEnabledState.asStateFlow()

    private val _puckColorState = MutableStateFlow(puckColor)
    val puckColorState: StateFlow<Int> = _puckColorState.asStateFlow()

    private val _puckSizeState = MutableStateFlow(puckSize)
    val puckSizeState: StateFlow<Int> = _puckSizeState.asStateFlow()

    private val _puckOpacityState = MutableStateFlow(puckOpacity)
    val puckOpacityState: StateFlow<Int> = _puckOpacityState.asStateFlow()

    private val _iconStyleState = MutableStateFlow(iconStyle)
    val iconStyleState: StateFlow<String> = _iconStyleState.asStateFlow()

    private val _appThemeState = MutableStateFlow(appTheme)
    val appThemeState: StateFlow<String> = _appThemeState.asStateFlow()

    private val _menuOrderState = MutableStateFlow(menuOrder)
    val menuOrderState: StateFlow<List<String>> = _menuOrderState.asStateFlow()

    private val _puckShapeState = MutableStateFlow(puckShape)
    val puckShapeState: StateFlow<String> = _puckShapeState.asStateFlow()

    private val _puckStyleState = MutableStateFlow(puckStyle)
    val puckStyleState: StateFlow<String> = _puckStyleState.asStateFlow()

    private val _swapAxesState = MutableStateFlow(swapAxes)
    val swapAxesState: StateFlow<Boolean> = _swapAxesState.asStateFlow()

    private val _axisLockThresholdPxState = MutableStateFlow(axisLockThresholdPx)
    val axisLockThresholdPxState: StateFlow<Int> = _axisLockThresholdPxState.asStateFlow()

    private fun notifyState() {
        _isServiceEnabledState.value = isServiceEnabled
        _puckColorState.value = puckColor
        _puckSizeState.value = puckSize
        _puckOpacityState.value = puckOpacity
        _iconStyleState.value = iconStyle
        _appThemeState.value = appTheme
        _menuOrderState.value = menuOrder
        _puckShapeState.value = puckShape
        _puckStyleState.value = puckStyle
        _swapAxesState.value = swapAxes
        _axisLockThresholdPxState.value = axisLockThresholdPx
    }

    var isServiceEnabled: Boolean
        get() = prefs.getBoolean("service_enabled", false)
        set(value) = prefs.edit().putBoolean("service_enabled", value).apply()

    var autoStart: Boolean
        get() = prefs.getBoolean("auto_start", false)
        set(value) = prefs.edit().putBoolean("auto_start", value).apply()

    var isBatteryOptPermanentlyGranted: Boolean
        get() = prefs.getBoolean("perm_battery_opt_granted", false)
        set(value) = prefs.edit().putBoolean("perm_battery_opt_granted", value).apply()

    var hasShownInitialPrompt: Boolean
        get() = prefs.getBoolean("has_shown_initial_prompt", false)
        set(value) = prefs.edit().putBoolean("has_shown_initial_prompt", value).apply()

    var swapSides: Boolean
        get() = prefs.getBoolean("swap_sides", false)
        set(value) = prefs.edit().putBoolean("swap_sides", value).apply()

    var swapAxes: Boolean
        get() = prefs.getBoolean("swap_control_axes", false)
        set(value) = prefs.edit().putBoolean("swap_control_axes", value).apply()

    var axisLockThresholdPx: Int
        get() = prefs.getInt("axis_lock_threshold_px", 4)
        set(value) = prefs.edit().putInt("axis_lock_threshold_px", value.coerceIn(2, 16)).apply()

    var horizontalBrightness: Boolean
        get() = prefs.getBoolean("horizontal_brightness", true)
        set(value) = prefs.edit().putBoolean("horizontal_brightness", value).apply()

    var horizontalVolume: Boolean
        get() = prefs.getBoolean("horizontal_volume", true)
        set(value) = prefs.edit().putBoolean("horizontal_volume", value).apply()

    var puckSize: Int
        get() = prefs.getInt("puck_size", 60)
        set(value) = prefs.edit().putInt("puck_size", value.coerceIn(36, 100)).apply()

    var puckOpacity: Int
        get() = prefs.getInt("puck_opacity", 85)
        set(value) = prefs.edit().putInt("puck_opacity", value.coerceIn(20, 100)).apply()

    var puckColor: Int
        get() = prefs.getInt("puck_color", Color.parseColor("#795548")) // Android 17 Earth Brown default
        set(value) = prefs.edit().putInt("puck_color", value).apply()

    var smoothControl: Boolean
        get() = prefs.getBoolean("smooth_control", true)
        set(value) = prefs.edit().putBoolean("smooth_control", value).apply()

    var iconStyle: String
        get() = prefs.getString("icon_style", "orbit") ?: "orbit"
        set(value) = prefs.edit().putString("icon_style", value).apply()

    var appTheme: String
        get() = prefs.getString("app_theme", "system") ?: "system"
        set(value) = prefs.edit().putString("app_theme", value).apply()

    var sensitivity: Int
        get() = prefs.getInt("sensitivity", 60)
        set(value) = prefs.edit().putInt("sensitivity", value.coerceIn(10, 100)).apply()

    var vibrationStrength: Int
        get() = prefs.getInt("vibration_strength", 50)
        set(value) = prefs.edit().putInt("vibration_strength", value.coerceIn(0, 100)).apply()

    var lastPuckX: Int
        get() = prefs.getInt("last_x", -1)
        set(value) = prefs.edit().putInt("last_x", value).apply()

    var lastPuckY: Int
        get() = prefs.getInt("last_y", -1)
        set(value) = prefs.edit().putInt("last_y", value).apply()

    var wasRightSide: Boolean
        get() = prefs.getBoolean("was_right_side", true)
        set(value) = prefs.edit().putBoolean("was_right_side", value).apply()

    var menuOrder: List<String>
        get() {
            val raw = prefs.getString("menu_order", null)
            if (raw.isNullOrBlank()) return DEFAULT_MENU_ORDER
            val items = raw.split(",").map { it.trim() }.filter { it.isNotEmpty() }
            val merged = items.filter { DEFAULT_MENU_ORDER.contains(it) }.toMutableList()
            DEFAULT_MENU_ORDER.forEach { if (!merged.contains(it)) merged.add(it) }
            return merged
        }
        set(value) {
            val valid = value.filter { DEFAULT_MENU_ORDER.contains(it) }
            prefs.edit().putString("menu_order", valid.joinToString(",")).apply()
        }

    fun moveMenuItem(fromIndex: Int, toIndex: Int) {
        val current = menuOrder.toMutableList()
        if (fromIndex in current.indices && toIndex in current.indices && fromIndex != toIndex) {
            val item = current.removeAt(fromIndex)
            current.add(toIndex, item)
            menuOrder = current
        }
    }

    fun resetMenuOrder() {
        menuOrder = DEFAULT_MENU_ORDER
    }

    var puckShape: String
        get() = prefs.getString("puck_shape", "circle") ?: "circle"
        set(value) = prefs.edit().putString("puck_shape", value).apply()

    var puckStyle: String
        get() = prefs.getString("puck_style", "solid") ?: "solid"
        set(value) = prefs.edit().putString("puck_style", value).apply()

    companion object {
        val DEFAULT_MENU_ORDER = listOf(
            "flashlight",
            "screenshot",
            "dnd",
            "audio",
            "brightness",
            "auto_rotate",
            "smart_puck_app",
            "lock_screen"
        )
    }
}
