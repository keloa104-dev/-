package com.example.ui.screens.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.PuckPreferences

@Composable
fun SystemPermissionsCard(
    prefs: PuckPreferences,
    hasOverlayPermission: Boolean,
    isIgnoringBatteryOpt: Boolean,
    hasLockPermission: Boolean,
    isAccessibilityActive: Boolean,
    isDeviceAdminActive: Boolean,
    hasDndPermission: Boolean,
    onRequestOverlay: () -> Unit,
    onRequestIgnoreBatteryOpt: () -> Unit,
    onRequestAccessibility: () -> Unit,
    onRequestDeviceAdmin: () -> Unit,
    onRequestDndPermission: () -> Unit,
    onTestLockScreen: () -> Unit = {},
    onOpenAutoStart: () -> Unit,
    modifier: Modifier = Modifier
) {
    var autoStart by remember { mutableStateOf(prefs.autoStart) }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .testTag("card_system_action_permissions"),
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer
        ),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            // Header
            Text(
                text = "System Engine",
                style = MaterialTheme.typography.titleMedium.copy(
                    fontWeight = FontWeight.Bold,
                    fontSize = 17.sp
                ),
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                softWrap = false,
                modifier = Modifier.basicMarquee()
            )

            Spacer(modifier = Modifier.height(12.dp))

            // Inset Grouped Permission Stack
            Surface(
                shape = RoundedCornerShape(18.dp),
                color = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.6f),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.2f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    // Row 1: Overlay
                    PermissionRow(
                        icon = Icons.Default.Layers,
                        title = "Overlay Window",
                        subtitle = "Display on top",
                        isGranted = hasOverlayPermission,
                        actionText = "Grant",
                        onAction = onRequestOverlay,
                        testTag = "btn_appear_on_top"
                    )

                    HorizontalDivider(modifier = Modifier.padding(horizontal = 14.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.2f))

                    // Row 2: Background Work Engine
                    PermissionRow(
                        icon = Icons.Default.BatterySaver,
                        title = "Background Run",
                        subtitle = if (isIgnoringBatteryOpt || prefs.isBatteryOptPermanentlyGranted) "Active" else "Watchdog setup",
                        isGranted = isIgnoringBatteryOpt || prefs.isBatteryOptPermanentlyGranted,
                        actionText = "BOOT",
                        onAction = {
                            prefs.isBatteryOptPermanentlyGranted = true
                            onRequestIgnoreBatteryOpt()
                            onOpenAutoStart()
                        },
                        testTag = "btn_keep_running_background"
                    )

                    HorizontalDivider(modifier = Modifier.padding(horizontal = 14.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.2f))

                    // Row 3: Screen Lock & Screenshot Engine
                    LockPermissionRow(
                        isAccessibilityActive = isAccessibilityActive,
                        hasLockPermission = hasLockPermission,
                        isDeviceAdminActive = isDeviceAdminActive,
                        onRequestAccessibility = onRequestAccessibility,
                        onRequestDeviceAdmin = onRequestDeviceAdmin,
                        onTestLockScreen = onTestLockScreen
                    )

                    HorizontalDivider(modifier = Modifier.padding(horizontal = 14.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.2f))

                    // Row 4: Do Not Disturb Policy
                    PermissionRow(
                        icon = Icons.Default.DoNotDisturbOn,
                        title = "DND Sync",
                        subtitle = "System silence",
                        isGranted = hasDndPermission,
                        actionText = "Grant",
                        onAction = onRequestDndPermission,
                        testTag = "btn_req_dnd"
                    )

                    HorizontalDivider(modifier = Modifier.padding(horizontal = 14.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.2f))

                    // Row 5: Start App on Device Boot
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 10.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.weight(1f)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(34.dp)
                                    .background(
                                        if (autoStart) Color(0xFF10B981).copy(alpha = 0.15f)
                                        else MaterialTheme.colorScheme.surfaceVariant,
                                        RoundedCornerShape(10.dp)
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.PowerSettingsNew,
                                    contentDescription = null,
                                    tint = if (autoStart) Color(0xFF10B981) else MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(10.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Boot Auto-Start",
                                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                                    maxLines = 1,
                                    softWrap = false,
                                    modifier = Modifier.basicMarquee()
                                )
                                Text(
                                    text = "Start at boot",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    softWrap = false,
                                    modifier = Modifier.basicMarquee()
                                )
                            }
                        }

                        Switch(
                            checked = autoStart,
                            onCheckedChange = {
                                autoStart = it
                                prefs.autoStart = it
                            },
                            modifier = Modifier.testTag("switch_boot_autostart")
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun GrantedCheckBadge(
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .size(width = 36.dp, height = 30.dp)
            .background(
                color = Color(0xFF10B981).copy(alpha = 0.15f),
                shape = RoundedCornerShape(8.dp)
            )
            .clickable(onClick = onClick)
    ) {
        Text(
            text = "✓",
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            color = Color(0xFF059669)
        )
    }
}

@Composable
private fun PermissionRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    isGranted: Boolean,
    actionText: String,
    onAction: () -> Unit,
    testTag: String = ""
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.weight(1f)
        ) {
            Box(
                modifier = Modifier
                    .size(34.dp)
                    .background(
                        if (isGranted) Color(0xFF10B981).copy(alpha = 0.15f)
                        else Color(0xFFF59E0B).copy(alpha = 0.15f),
                        RoundedCornerShape(10.dp)
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = if (isGranted) Color(0xFF10B981) else Color(0xFFF59E0B),
                    modifier = Modifier.size(18.dp)
                )
            }
            Spacer(modifier = Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                    maxLines = 1,
                    softWrap = false,
                    modifier = Modifier.basicMarquee()
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    softWrap = false,
                    modifier = Modifier.basicMarquee()
                )
            }
        }

        Spacer(modifier = Modifier.width(8.dp))

        if (isGranted) {
            GrantedCheckBadge(onClick = onAction)
        } else {
            Button(
                onClick = onAction,
                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                modifier = Modifier.height(30.dp).testTag(testTag)
            ) {
                Text(actionText, fontSize = 11.sp, fontWeight = FontWeight.Bold, maxLines = 1, softWrap = false)
            }
        }
    }
}

@Composable
private fun LockPermissionRow(
    isAccessibilityActive: Boolean,
    hasLockPermission: Boolean,
    isDeviceAdminActive: Boolean,
    onRequestAccessibility: () -> Unit,
    onRequestDeviceAdmin: () -> Unit,
    onTestLockScreen: () -> Unit = {}
) {
    val isAnyActive = isAccessibilityActive || hasLockPermission

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp, vertical = 10.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f)
            ) {
                Box(
                    modifier = Modifier
                        .size(34.dp)
                        .background(
                            if (isAnyActive) Color(0xFF10B981).copy(alpha = 0.15f)
                            else Color(0xFFF59E0B).copy(alpha = 0.15f),
                            RoundedCornerShape(10.dp)
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Lock,
                        contentDescription = null,
                        tint = if (isAnyActive) Color(0xFF10B981) else Color(0xFFF59E0B),
                        modifier = Modifier.size(18.dp)
                    )
                }
                Spacer(modifier = Modifier.width(10.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Lock & Screenshot",
                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                        maxLines = 1,
                        softWrap = false,
                        modifier = Modifier.basicMarquee()
                    )
                    Text(
                        text = if (isAnyActive) "Active" else "Accessibility required",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        softWrap = false,
                        modifier = Modifier.basicMarquee()
                    )
                }
            }

            Spacer(modifier = Modifier.width(8.dp))

            if (isAnyActive) {
                GrantedCheckBadge(onClick = onRequestAccessibility)
            }
        }

        // Only show permission request buttons if neither accessibility nor lock permission is granted
        if (!isAnyActive) {
            Spacer(modifier = Modifier.height(6.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Button(
                    onClick = onRequestAccessibility,
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                    modifier = Modifier.weight(1f).height(30.dp).testTag("btn_accessibility_lock")
                ) {
                    Text("Accessibility", fontSize = 11.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, softWrap = false)
                }

                if (!isDeviceAdminActive) {
                    OutlinedButton(
                        onClick = onRequestDeviceAdmin,
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                        modifier = Modifier.weight(1f).height(30.dp).testTag("btn_lock_screen_permission")
                    ) {
                        Text("Admin", fontSize = 11.sp, maxLines = 1, softWrap = false)
                    }
                }
            }
        }
    }
}
