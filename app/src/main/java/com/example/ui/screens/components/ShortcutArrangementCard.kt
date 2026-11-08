package com.example.ui.screens.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.PuckPreferences

data class ShortcutMeta(
    val title: String,
    val subtitle: String,
    val icon: ImageVector,
    val color: Color
)

fun getShortcutMeta(key: String): ShortcutMeta {
    return when (key) {
        "flashlight" -> ShortcutMeta("Flashlight", "Torch", Icons.Default.FlashlightOn, Color(0xFFF59E0B))
        "screenshot" -> ShortcutMeta("Screenshot", "Capture", Icons.Default.CameraAlt, Color(0xFF06B6D4))
        "dnd" -> ShortcutMeta("Do Not Disturb", "Silence", Icons.Default.DoNotDisturbOn, Color(0xFF8B5CF6))
        "audio" -> ShortcutMeta("Audio Profile", "Volume", Icons.Default.VolumeUp, Color(0xFF3B82F6))
        "brightness" -> ShortcutMeta("Brightness", "Display", Icons.Default.WbSunny, Color(0xFFEAB308))
        "auto_rotate" -> ShortcutMeta("Auto-Rotate", "Rotation", Icons.Default.ScreenRotation, Color(0xFF14B8A6))
        "smart_puck_app" -> ShortcutMeta("Settings", "App", Icons.Default.Settings, Color(0xFFF97316))
        "lock_screen" -> ShortcutMeta("Lock Screen", "Security", Icons.Default.Lock, Color(0xFF10B981))
        else -> ShortcutMeta(key, "Action", Icons.Default.TouchApp, Color(0xFF64748B))
    }
}

@Composable
fun ShortcutArrangementCard(
    prefs: PuckPreferences,
    modifier: Modifier = Modifier
) {
    val menuOrder by prefs.menuOrderState.collectAsState()
    val haptic = LocalHapticFeedback.current

    Card(
        modifier = modifier
            .fillMaxWidth()
            .testTag("card_menu_order"),
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
            // Header with Reset action
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Shortcuts Order",
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.Bold,
                        fontSize = 17.sp
                    ),
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    softWrap = false,
                    modifier = Modifier.basicMarquee()
                )

                TextButton(
                    onClick = {
                        prefs.resetMenuOrder()
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    },
                    modifier = Modifier.testTag("btn_reset_menu_order")
                ) {
                    Icon(
                        imageVector = Icons.Default.RestartAlt,
                        contentDescription = "Reset",
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Reset", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Clean list of shortcuts
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                menuOrder.forEachIndexed { index, itemKey ->
                    val meta = getShortcutMeta(itemKey)
                    val isFirst = index == 0
                    val isLast = index == menuOrder.size - 1

                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.7f),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.15f)),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("reorder_row_$itemKey")
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // Position Pill
                            Surface(
                                shape = CircleShape,
                                color = MaterialTheme.colorScheme.surfaceVariant,
                                modifier = Modifier.size(24.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Text(
                                        text = "${index + 1}",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.width(10.dp))

                            // Colored Icon Pill
                            Box(
                                modifier = Modifier
                                    .size(34.dp)
                                    .background(meta.color.copy(alpha = 0.15f), RoundedCornerShape(10.dp)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = meta.icon,
                                    contentDescription = null,
                                    tint = meta.color,
                                    modifier = Modifier.size(18.dp)
                                )
                            }

                            Spacer(modifier = Modifier.width(10.dp))

                            // Single line non-wrapping text with basicMarquee
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = meta.title,
                                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                                    color = MaterialTheme.colorScheme.onSurface,
                                    maxLines = 1,
                                    softWrap = false,
                                    modifier = Modifier.basicMarquee()
                                )
                                Text(
                                    text = meta.subtitle,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    softWrap = false,
                                    modifier = Modifier.basicMarquee()
                                )
                            }

                            // Stepper Buttons
                            Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                                IconButton(
                                    onClick = {
                                        if (!isFirst) {
                                            prefs.moveMenuItem(index, index - 1)
                                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                        }
                                    },
                                    enabled = !isFirst,
                                    modifier = Modifier.size(32.dp).testTag("btn_move_up_$itemKey")
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.KeyboardArrowUp,
                                        contentDescription = "Up",
                                        tint = if (!isFirst) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }

                                IconButton(
                                    onClick = {
                                        if (!isLast) {
                                            prefs.moveMenuItem(index, index + 1)
                                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                        }
                                    },
                                    enabled = !isLast,
                                    modifier = Modifier.size(32.dp).testTag("btn_move_down_$itemKey")
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.KeyboardArrowDown,
                                        contentDescription = "Down",
                                        tint = if (!isLast) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
