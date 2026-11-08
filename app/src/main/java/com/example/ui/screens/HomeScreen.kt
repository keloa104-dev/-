package com.example.ui.screens

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.widget.Toast
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.data.PuckPreferences
import com.example.ui.screens.components.*
import com.example.util.DndSyncManager
import com.example.util.LockScreenHelper
import com.example.util.PuckKeepAliveManager

enum class SettingsHubCategory(val label: String, val icon: String) {
    ALL("All", "✦"),
    PUCK("Studio", "⚪"),
    SHORTCUTS("Shortcuts", "📋"),
    GESTURES("Gestures", "⚡"),
    SYSTEM("System", "🛡️"),
    THEME("Theme", "🎨")
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    prefs: PuckPreferences,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    var hasOverlayPermission by remember { mutableStateOf(Settings.canDrawOverlays(context)) }
    var hasWriteSettingsPermission by remember { mutableStateOf(Settings.System.canWrite(context)) }
    var isIgnoringBatteryOpt by remember { mutableStateOf(PuckKeepAliveManager.isBatteryOptimizationIgnored(context)) }
    var hasLockPermission by remember { mutableStateOf(LockScreenHelper.isLockPermissionGranted(context)) }
    var isAccessibilityActive by remember { mutableStateOf(LockScreenHelper.isAccessibilityServiceActive(context)) }
    var isDeviceAdminActive by remember { mutableStateOf(LockScreenHelper.isDeviceAdminActive(context)) }
    var hasDndPermission by remember { mutableStateOf(DndSyncManager.isNotificationPolicyAccessGranted(context)) }

    val isServiceEnabled by prefs.isServiceEnabledState.collectAsState()
    val puckColorInt by prefs.puckColorState.collectAsState()
    val puckSize by prefs.puckSizeState.collectAsState()
    val puckOpacity by prefs.puckOpacityState.collectAsState()
    val iconStyle by prefs.iconStyleState.collectAsState()
    val puckShape by prefs.puckShapeState.collectAsState()
    val puckStyle by prefs.puckStyleState.collectAsState()

    var activeCategory by remember { mutableStateOf(SettingsHubCategory.ALL) }
    val scrollState = rememberScrollState()

    fun refreshAllStates() {
        hasOverlayPermission = Settings.canDrawOverlays(context)
        hasWriteSettingsPermission = Settings.System.canWrite(context)
        isIgnoringBatteryOpt = PuckKeepAliveManager.isBatteryOptimizationIgnored(context)
        hasLockPermission = LockScreenHelper.isLockPermissionGranted(context)
        isAccessibilityActive = LockScreenHelper.isAccessibilityServiceActive(context)
        isDeviceAdminActive = LockScreenHelper.isDeviceAdminActive(context)
        hasDndPermission = DndSyncManager.isNotificationPolicyAccessGranted(context)
    }

    LaunchedEffect(Unit) {
        refreshAllStates()
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                refreshAllStates()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    DisposableEffect(context) {
        val dndReceiver = object : BroadcastReceiver() {
            override fun onReceive(c: Context?, intent: Intent?) {
                if (c != null) {
                    hasDndPermission = DndSyncManager.isNotificationPolicyAccessGranted(c)
                }
            }
        }

        val syncFilter = IntentFilter().apply {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                addAction(android.app.NotificationManager.ACTION_INTERRUPTION_FILTER_CHANGED)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                addAction(android.app.NotificationManager.ACTION_NOTIFICATION_POLICY_ACCESS_GRANTED_CHANGED)
            }
        }

        try {
            ContextCompat.registerReceiver(
                context,
                dndReceiver,
                syncFilter,
                ContextCompat.RECEIVER_NOT_EXPORTED
            )
        } catch (_: Exception) {
            try {
                context.registerReceiver(dndReceiver, syncFilter)
            } catch (_: Exception) {}
        }

        onDispose {
            try { context.unregisterReceiver(dndReceiver) } catch (_: Exception) {}
        }
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        contentWindowInsets = WindowInsets.safeDrawing
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(scrollState)
                .padding(bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Modern Top Bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    Surface(
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.primaryContainer,
                        modifier = Modifier.size(34.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Default.Adjust,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Smart Puck",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onBackground,
                            maxLines = 1,
                            softWrap = false,
                            modifier = Modifier.basicMarquee()
                        )
                        Text(
                            text = if (isServiceEnabled && hasOverlayPermission) "Active" else "Offline",
                            style = MaterialTheme.typography.labelSmall,
                            color = if (isServiceEnabled && hasOverlayPermission) Color(0xFF10B981) else MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            softWrap = false,
                            modifier = Modifier.basicMarquee()
                        )
                    }
                }

                IconButton(
                    onClick = {
                        refreshAllStates()
                        Toast.makeText(context, "Refreshed", Toast.LENGTH_SHORT).show()
                    },
                    modifier = Modifier.testTag("btn_refresh_permissions")
                ) {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = "Refresh",
                        tint = MaterialTheme.colorScheme.onBackground,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            // Category Filter Bar
            LazyRow(
                contentPadding = PaddingValues(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                items(SettingsHubCategory.values()) { category ->
                    val isSelected = activeCategory == category
                    FilterChip(
                        selected = isSelected,
                        onClick = { activeCategory = category },
                        label = {
                            Text(
                                text = "${category.icon} ${category.label}",
                                maxLines = 1,
                                softWrap = false,
                                modifier = Modifier.basicMarquee()
                            )
                        },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                            selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
                        ),
                        border = FilterChipDefaults.filterChipBorder(
                            enabled = true,
                            selected = isSelected,
                            borderColor = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)
                        ),
                        modifier = Modifier.height(34.dp)
                    )
                }
            }

            // Compact DND Banner
            if (!hasDndPermission && (activeCategory == SettingsHubCategory.ALL || activeCategory == SettingsHubCategory.SYSTEM)) {
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.7f),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(
                                imageVector = Icons.Default.DoNotDisturbOn,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onErrorContainer,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "DND Sync Access Required",
                                style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Medium),
                                color = MaterialTheme.colorScheme.onErrorContainer,
                                maxLines = 1,
                                softWrap = false,
                                modifier = Modifier.basicMarquee()
                            )
                        }
                        TextButton(
                            onClick = { DndSyncManager.openDndSettings(context) },
                            modifier = Modifier.testTag("btn_grant_dnd_onboarding")
                        ) {
                            Text("Grant", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onErrorContainer, maxLines = 1, softWrap = false)
                        }
                    }
                }
            }

            // SECTION 1: Master Service Switch
            if (activeCategory == SettingsHubCategory.ALL || activeCategory == SettingsHubCategory.PUCK || activeCategory == SettingsHubCategory.SYSTEM) {
                Box(modifier = Modifier.padding(horizontal = 16.dp)) {
                    HeroStatusCard(
                        isServiceEnabled = isServiceEnabled,
                        hasOverlayPermission = hasOverlayPermission,
                        hasWriteSettingsPermission = hasWriteSettingsPermission,
                        hasDndPermission = hasDndPermission,
                        hasLockPermission = hasLockPermission,
                        isIgnoringBatteryOpt = isIgnoringBatteryOpt,
                        onToggleService = { enabled ->
                            if (enabled && !Settings.canDrawOverlays(context)) {
                                requestOverlayPermission(context)
                                Toast.makeText(context, "Grant Overlay permission", Toast.LENGTH_LONG).show()
                            } else {
                                prefs.isServiceEnabled = enabled
                                if (enabled) {
                                    PuckKeepAliveManager.startPuckService(context)
                                } else {
                                    PuckKeepAliveManager.stopPuckService(context)
                                }
                            }
                        },
                        onRequestOverlay = { requestOverlayPermission(context) },
                        onRequestWriteSettings = { requestWriteSettingsPermission(context) },
                        onRequestDnd = { DndSyncManager.openDndSettings(context) },
                        onRequestLock = { LockScreenHelper.requestAccessibilityPermission(context) },
                        onRequestBattery = {
                            prefs.isBatteryOptPermanentlyGranted = true
                            PuckKeepAliveManager.requestIgnoreBatteryOptimizations(context)
                        }
                    )
                }
            }

            // SECTION 2: Puck Appearance & Customization (Puck Studio)
            if (activeCategory == SettingsHubCategory.ALL || activeCategory == SettingsHubCategory.PUCK) {
                Box(modifier = Modifier.padding(horizontal = 16.dp)) {
                    PuckStudioCard(
                        currentColorInt = puckColorInt,
                        currentSize = puckSize,
                        currentOpacity = puckOpacity,
                        currentIconStyle = iconStyle,
                        currentShape = puckShape,
                        currentStyle = puckStyle,
                        onColorSelected = { prefs.puckColor = it },
                        onSizeChange = { prefs.puckSize = it },
                        onOpacityChange = { prefs.puckOpacity = it },
                        onIconStyleChange = { prefs.iconStyle = it },
                        onShapeChange = { prefs.puckShape = it },
                        onStyleChange = { prefs.puckStyle = it }
                    )
                }
            }

            // SECTION 3: Shortcut Menu Order
            if (activeCategory == SettingsHubCategory.ALL || activeCategory == SettingsHubCategory.SHORTCUTS) {
                Box(modifier = Modifier.padding(horizontal = 16.dp)) {
                    ShortcutArrangementCard(prefs = prefs)
                }
            }

            // SECTION 4: Gesture Controls
            if (activeCategory == SettingsHubCategory.ALL || activeCategory == SettingsHubCategory.GESTURES) {
                Box(modifier = Modifier.padding(horizontal = 16.dp)) {
                    GestureEngineCard(prefs = prefs)
                }
            }

            // SECTION 5: System Engine
            if (activeCategory == SettingsHubCategory.ALL || activeCategory == SettingsHubCategory.SYSTEM) {
                Box(modifier = Modifier.padding(horizontal = 16.dp)) {
                    SystemPermissionsCard(
                        prefs = prefs,
                        hasOverlayPermission = hasOverlayPermission,
                        isIgnoringBatteryOpt = isIgnoringBatteryOpt,
                        hasLockPermission = hasLockPermission,
                        isAccessibilityActive = isAccessibilityActive,
                        isDeviceAdminActive = isDeviceAdminActive,
                        hasDndPermission = hasDndPermission,
                        onRequestOverlay = { requestOverlayPermission(context) },
                        onRequestIgnoreBatteryOpt = {
                            prefs.isBatteryOptPermanentlyGranted = true
                            PuckKeepAliveManager.requestIgnoreBatteryOptimizations(context)
                        },
                        onRequestAccessibility = { LockScreenHelper.requestAccessibilityPermission(context) },
                        onRequestDeviceAdmin = { LockScreenHelper.requestDeviceAdminPermission(context) },
                        onRequestDndPermission = { DndSyncManager.openDndSettings(context) },
                        onTestLockScreen = {
                            val locked = LockScreenHelper.performLock(context)
                            if (locked) {
                                Toast.makeText(context, "Screen Locked", Toast.LENGTH_SHORT).show()
                            } else {
                                Toast.makeText(context, "Enable Lock Permission in Settings", Toast.LENGTH_LONG).show()
                            }
                        },
                        onOpenAutoStart = {
                            prefs.isBatteryOptPermanentlyGranted = true
                            PuckKeepAliveManager.openAutoStartSettings(context)
                        }
                    )
                }
            }

            // SECTION 6: Interface Theme
            if (activeCategory == SettingsHubCategory.ALL || activeCategory == SettingsHubCategory.THEME) {
                Box(modifier = Modifier.padding(horizontal = 16.dp)) {
                    AppThemeSelectorCard(prefs = prefs)
                }
            }
        }
    }
}

private fun requestOverlayPermission(context: Context) {
    val intent = Intent(
        Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
        Uri.parse("package:${context.packageName}")
    ).apply {
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    context.startActivity(intent)
}

private fun requestWriteSettingsPermission(context: Context) {
    val intent = Intent(
        Settings.ACTION_MANAGE_WRITE_SETTINGS,
        Uri.parse("package:${context.packageName}")
    ).apply {
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    context.startActivity(intent)
}
