package com.example.ui.screens.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun HeroStatusCard(
    isServiceEnabled: Boolean,
    hasOverlayPermission: Boolean,
    hasWriteSettingsPermission: Boolean,
    hasDndPermission: Boolean,
    hasLockPermission: Boolean,
    isIgnoringBatteryOpt: Boolean,
    onToggleService: (Boolean) -> Unit,
    onRequestOverlay: () -> Unit,
    onRequestWriteSettings: () -> Unit,
    onRequestDnd: () -> Unit,
    onRequestLock: () -> Unit,
    onRequestBattery: () -> Unit,
    modifier: Modifier = Modifier
) {
    val isReady = isServiceEnabled && hasOverlayPermission
    val statusColor by animateColorAsState(
        targetValue = when {
            isReady -> Color(0xFF10B981)
            isServiceEnabled -> Color(0xFFF59E0B)
            else -> MaterialTheme.colorScheme.outline
        },
        label = "hero_status_color"
    )

    val infiniteTransition = rememberInfiniteTransition(label = "pulse_transition")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = if (isReady) 1.25f else 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse_scale"
    )

    Card(
        modifier = modifier
            .fillMaxWidth()
            .testTag("card_service_status"),
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer
        ),
        border = BorderStroke(
            1.dp,
            if (isReady) MaterialTheme.colorScheme.primary.copy(alpha = 0.35f)
            else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            // Master Service Row with Live Animated Indicator
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
                        modifier = Modifier.size(34.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Box(
                            modifier = Modifier
                                .size(24.dp)
                                .scale(pulseScale)
                                .background(
                                    color = statusColor.copy(alpha = if (isReady) 0.25f else 0.1f),
                                    shape = CircleShape
                                )
                        )
                        Box(
                            modifier = Modifier
                                .size(13.dp)
                                .background(color = statusColor, shape = CircleShape)
                        )
                    }

                    Spacer(modifier = Modifier.width(10.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = if (isReady) "Floating Puck"
                            else if (isServiceEnabled) "Setup Required"
                            else "Puck Disabled",
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.Bold,
                                fontSize = 16.sp
                            ),
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            softWrap = false,
                            modifier = Modifier.basicMarquee()
                        )
                        Text(
                            text = if (isReady) "Running"
                            else if (isServiceEnabled) "Grant Overlay"
                            else "Off",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            softWrap = false,
                            modifier = Modifier.basicMarquee()
                        )
                    }
                }

                Spacer(modifier = Modifier.width(8.dp))

                Switch(
                    checked = isServiceEnabled,
                    onCheckedChange = onToggleService,
                    modifier = Modifier.testTag("switch_master_puck_service")
                )
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Integration Badges Grid with Icons & Horizontal Marquee
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                HealthBadge(
                    icon = Icons.Default.Layers,
                    label = "Overlay",
                    isGranted = hasOverlayPermission,
                    onClick = onRequestOverlay,
                    modifier = Modifier.weight(1f).testTag("btn_grant_overlay")
                )
                HealthBadge(
                    icon = Icons.Default.Edit,
                    label = "Settings",
                    isGranted = hasWriteSettingsPermission,
                    onClick = onRequestWriteSettings,
                    modifier = Modifier.weight(1f).testTag("btn_grant_settings")
                )
                HealthBadge(
                    icon = Icons.Default.DoNotDisturbOn,
                    label = "DND",
                    isGranted = hasDndPermission,
                    onClick = onRequestDnd,
                    modifier = Modifier.weight(1f)
                )
                HealthBadge(
                    icon = Icons.Default.BatterySaver,
                    label = "Battery",
                    isGranted = isIgnoringBatteryOpt,
                    onClick = onRequestBattery,
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
private fun HealthBadge(
    icon: ImageVector,
    label: String,
    isGranted: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val bg = if (isGranted) Color(0xFF10B981).copy(alpha = 0.12f) else Color(0xFFF59E0B).copy(alpha = 0.16f)
    val textCol = if (isGranted) Color(0xFF059669) else Color(0xFFD97706)

    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(12.dp),
        color = bg,
        modifier = modifier.height(34.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = if (isGranted) Icons.Default.CheckCircle else icon,
                contentDescription = label,
                tint = textCol,
                modifier = Modifier.size(14.dp)
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                text = label,
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
                color = textCol,
                maxLines = 1,
                softWrap = false,
                modifier = Modifier.basicMarquee()
            )
        }
    }
}
