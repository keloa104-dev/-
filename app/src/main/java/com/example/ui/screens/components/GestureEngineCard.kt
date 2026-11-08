package com.example.ui.screens.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.PuckPreferences
import kotlin.math.roundToInt

@Composable
fun GestureEngineCard(
    prefs: PuckPreferences,
    modifier: Modifier = Modifier
) {
    var smoothControl by remember { mutableStateOf(prefs.smoothControl) }
    var swapAxes by remember { mutableStateOf(prefs.swapAxes) }
    var axisLockThreshold by remember { mutableIntStateOf(prefs.axisLockThresholdPx) }
    var sensitivity by remember { mutableIntStateOf(prefs.sensitivity) }
    var vibration by remember { mutableIntStateOf(prefs.vibrationStrength) }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .testTag("card_gesture_settings"),
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
                text = "Gesture Controls",
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

            // 2D Axis Mapping Card
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.7f),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.15f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Swap Axes",
                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                            maxLines = 1,
                            softWrap = false,
                            modifier = Modifier.basicMarquee()
                        )
                        Switch(
                            checked = swapAxes,
                            onCheckedChange = {
                                swapAxes = it
                                prefs.swapAxes = it
                            },
                            modifier = Modifier.testTag("switch_swap_axes")
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.surfaceContainer,
                            modifier = Modifier.weight(1f)
                        ) {
                            Column(
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text(
                                    text = "↔ Horizontal",
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    softWrap = false,
                                    modifier = Modifier.basicMarquee()
                                )
                                Text(
                                    text = if (!swapAxes) "☀️ Bright" else "🔊 Volume",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.primary,
                                    maxLines = 1,
                                    softWrap = false,
                                    modifier = Modifier.basicMarquee()
                                )
                            }
                        }

                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.surfaceContainer,
                            modifier = Modifier.weight(1f)
                        ) {
                            Column(
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text(
                                    text = "↕ Vertical",
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    softWrap = false,
                                    modifier = Modifier.basicMarquee()
                                )
                                Text(
                                    text = if (!swapAxes) "🔊 Volume" else "☀️ Bright",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.primary,
                                    maxLines = 1,
                                    softWrap = false,
                                    modifier = Modifier.basicMarquee()
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Deadzone Segmented Control
            Text(
                text = "Deadzone Lock",
                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                maxLines = 1,
                softWrap = false
            )
            Spacer(modifier = Modifier.height(6.dp))
            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.fillMaxWidth().testTag("segmented_axis_lock_threshold")
            ) {
                val thresholds = listOf(
                    Pair(2, "2px"),
                    Pair(4, "4px"),
                    Pair(8, "8px"),
                    Pair(12, "12px")
                )
                thresholds.forEach { (pxVal, label) ->
                    val isSelected = axisLockThreshold == pxVal
                    Surface(
                        onClick = {
                            axisLockThreshold = pxVal
                            prefs.axisLockThresholdPx = pxVal
                        },
                        shape = RoundedCornerShape(10.dp),
                        color = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHighest,
                        border = BorderStroke(
                            1.dp,
                            if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.2f)
                        ),
                        modifier = Modifier.weight(1f).height(36.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                            Text(
                                text = label,
                                fontSize = 11.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface,
                                maxLines = 1,
                                softWrap = false
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // 120 FPS Fluid Drag Switch
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.7f),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.15f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "120 FPS Smooth Drag",
                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                        maxLines = 1,
                        softWrap = false,
                        modifier = Modifier.basicMarquee()
                    )
                    Switch(
                        checked = smoothControl,
                        onCheckedChange = {
                            smoothControl = it
                            prefs.smoothControl = it
                        },
                        modifier = Modifier.testTag("switch_smooth_control")
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Sliders
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Sensitivity", style = MaterialTheme.typography.bodyMedium, maxLines = 1, softWrap = false)
                    Surface(shape = RoundedCornerShape(6.dp), color = MaterialTheme.colorScheme.primaryContainer) {
                        Text(
                            text = "$sensitivity%",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                            maxLines = 1,
                            softWrap = false
                        )
                    }
                }
                Slider(
                    value = sensitivity.toFloat(),
                    onValueChange = {
                        sensitivity = it.roundToInt()
                        prefs.sensitivity = sensitivity
                    },
                    valueRange = 10f..100f,
                    modifier = Modifier.testTag("slider_sensitivity")
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Haptics", style = MaterialTheme.typography.bodyMedium, maxLines = 1, softWrap = false)
                    Surface(shape = RoundedCornerShape(6.dp), color = MaterialTheme.colorScheme.primaryContainer) {
                        Text(
                            text = "$vibration%",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                            maxLines = 1,
                            softWrap = false
                        )
                    }
                }
                Slider(
                    value = vibration.toFloat(),
                    onValueChange = {
                        vibration = it.roundToInt()
                        prefs.vibrationStrength = vibration
                    },
                    valueRange = 0f..100f,
                    modifier = Modifier.testTag("slider_haptics")
                )
            }
        }
    }
}
