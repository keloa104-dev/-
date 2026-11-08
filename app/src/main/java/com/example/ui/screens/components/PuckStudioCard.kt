package com.example.ui.screens.components

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.data.IconStyleUtils
import kotlin.math.roundToInt

@Composable
fun PuckStudioCard(
    currentColorInt: Int,
    currentSize: Int,
    currentOpacity: Int,
    currentIconStyle: String,
    currentShape: String,
    currentStyle: String,
    onColorSelected: (Int) -> Unit,
    onSizeChange: (Int) -> Unit,
    onOpacityChange: (Int) -> Unit,
    onIconStyleChange: (String) -> Unit,
    onShapeChange: (String) -> Unit,
    onStyleChange: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var isCharging by remember { mutableStateOf(false) }
    var selectedStudioTab by remember { mutableIntStateOf(0) }

    DisposableEffect(context) {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(c: Context?, intent: Intent?) {
                val status = intent?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1
                val plugged = intent?.getIntExtra(BatteryManager.EXTRA_PLUGGED, -1) ?: 0
                isCharging = (status == BatteryManager.BATTERY_STATUS_CHARGING ||
                        status == BatteryManager.BATTERY_STATUS_FULL ||
                        plugged > 0)
            }
        }
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_POWER_CONNECTED)
            addAction(Intent.ACTION_POWER_DISCONNECTED)
            addAction(Intent.ACTION_BATTERY_CHANGED)
        }
        val sticky = try { context.registerReceiver(receiver, filter) } catch (_: Exception) { null }
        sticky?.let { intent ->
            val status = intent.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
            val plugged = intent.getIntExtra(BatteryManager.EXTRA_PLUGGED, -1)
            isCharging = (status == BatteryManager.BATTERY_STATUS_CHARGING ||
                    status == BatteryManager.BATTERY_STATUS_FULL ||
                    plugged > 0)
        }
        onDispose {
            try { context.unregisterReceiver(receiver) } catch (_: Exception) {}
        }
    }

    val previewShape = when (currentShape) {
        "rounded_square" -> RoundedCornerShape(16.dp)
        "squircle" -> RoundedCornerShape(24.dp)
        else -> CircleShape
    }

    val previewBorder = if (currentStyle == "frosted_glass") {
        BorderStroke(2.5.dp, Color.White.copy(alpha = 0.85f))
    } else {
        BorderStroke(2.dp, Color.White.copy(alpha = 0.6f))
    }

    val previewBackgroundBrush = if (currentStyle == "frosted_glass") {
        Brush.radialGradient(
            listOf(
                Color(currentColorInt).copy(alpha = 0.70f),
                Color(0xFF2B1E19).copy(alpha = 0.85f)
            )
        )
    } else {
        Brush.linearGradient(
            listOf(
                Color(currentColorInt),
                Color(currentColorInt)
            )
        )
    }

    Card(
        modifier = modifier.fillMaxWidth(),
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
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Puck Studio",
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.Bold,
                        fontSize = 17.sp
                    ),
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    softWrap = false,
                    modifier = Modifier.basicMarquee()
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Stage
            Surface(
                shape = RoundedCornerShape(18.dp),
                color = MaterialTheme.colorScheme.surfaceContainerLowest,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.2f)),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(115.dp)
            ) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Box(
                        modifier = Modifier
                            .size(90.dp)
                            .background(
                                Brush.radialGradient(
                                    listOf(
                                        Color(currentColorInt).copy(alpha = 0.25f),
                                        Color.Transparent
                                    )
                                ),
                                CircleShape
                            )
                    )

                    Box(
                        modifier = Modifier
                            .size(currentSize.dp.coerceIn(40.dp, 80.dp))
                            .alpha(currentOpacity / 100f)
                            .background(previewBackgroundBrush, previewShape)
                            .border(previewBorder, previewShape)
                            .shadow(8.dp, previewShape),
                        contentAlignment = Alignment.Center
                    ) {
                        if (isCharging) {
                            Image(
                                painter = painterResource(id = R.drawable.ic_electric_clock),
                                contentDescription = "Clock",
                                modifier = Modifier.size((currentSize * 0.52f).dp),
                                colorFilter = androidx.compose.ui.graphics.ColorFilter.tint(Color.White)
                            )
                        } else {
                            Image(
                                painter = painterResource(id = IconStyleUtils.getIconRes(currentIconStyle)),
                                contentDescription = null,
                                modifier = Modifier.size((currentSize * 0.45f).dp.coerceIn(16.dp, 36.dp)),
                                colorFilter = androidx.compose.ui.graphics.ColorFilter.tint(Color.White)
                            )
                        }
                    }

                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.85f),
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(8.dp)
                    ) {
                        Text(
                            text = "${currentSize}dp • $currentOpacity% • ${if (isCharging) "⚡" else currentShape}",
                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                                .basicMarquee(),
                            maxLines = 1,
                            softWrap = false
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Sub-Tabs
            val tabTitles = listOf("🎨 Color", "📐 Shape", "📏 Scale", "✦ Emblem")
            TabRow(
                selectedTabIndex = selectedStudioTab,
                containerColor = Color.Transparent,
                contentColor = MaterialTheme.colorScheme.primary,
                divider = {},
                indicator = {}
            ) {
                tabTitles.forEachIndexed { index, title ->
                    val isSelected = selectedStudioTab == index
                    Surface(
                        onClick = { selectedStudioTab = index },
                        shape = RoundedCornerShape(10.dp),
                        color = if (isSelected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent,
                        modifier = Modifier
                            .padding(horizontal = 2.dp)
                            .height(34.dp)
                    ) {
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier.fillMaxSize()
                        ) {
                            Text(
                                text = title,
                                fontSize = 11.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                softWrap = false,
                                modifier = Modifier.basicMarquee()
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Tab Content
            AnimatedContent(
                targetState = selectedStudioTab,
                transitionSpec = { fadeIn() togetherWith fadeOut() },
                label = "studio_tab_content"
            ) { tab ->
                when (tab) {
                    0 -> ColorPaletteSection(
                        currentColorInt = currentColorInt,
                        onColorSelected = onColorSelected
                    )
                    1 -> ShapeAndStyleSection(
                        currentShape = currentShape,
                        currentStyle = currentStyle,
                        onShapeChange = onShapeChange,
                        onStyleChange = onStyleChange
                    )
                    2 -> DimensionsSection(
                        currentSize = currentSize,
                        currentOpacity = currentOpacity,
                        onSizeChange = onSizeChange,
                        onOpacityChange = onOpacityChange
                    )
                    3 -> EmblemGridSection(
                        currentIconStyle = currentIconStyle,
                        onIconStyleChange = onIconStyleChange
                    )
                }
            }
        }
    }
}

@Composable
private fun ColorPaletteSection(
    currentColorInt: Int,
    onColorSelected: (Int) -> Unit
) {
    val colorSwatches = listOf(
        0xFF795548.toInt(), // Earth Brown
        0xFF4E342E.toInt(), // Mocha
        0xFF1A73E8.toInt(), // Blue
        0xFFEA4335.toInt(), // Red
        0xFFFBBC04.toInt(), // Yellow
        0xFF34A853.toInt(), // Green
        0xFF00897B.toInt(), // Teal
        0xFF8E24AA.toInt(), // Purple
        0xFF202124.toInt(), // Dark Slate
        0xFFE65100.toInt(), // Orange
        0xFFFF8F00.toInt(), // Amber
        0xFFD81B60.toInt(), // Rose Pink
        0xFF3F51B5.toInt(), // Indigo
        0xFF00ACC1.toInt(), // Cyan
        0xFF558B2F.toInt(), // Olive Green
        0xFF607D8B.toInt(), // Steel Blue
        0xFF7E57C2.toInt()  // Lavender
    )

    LazyRow(
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        contentPadding = PaddingValues(vertical = 4.dp)
    ) {
        items(colorSwatches) { colorInt ->
            val isSelected = colorInt == currentColorInt
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .background(Color(colorInt), CircleShape)
                    .border(
                        width = if (isSelected) 3.dp else 1.dp,
                        color = if (isSelected) MaterialTheme.colorScheme.primary else Color.White.copy(alpha = 0.2f),
                        shape = CircleShape
                    )
                    .clickable { onColorSelected(colorInt) },
                contentAlignment = Alignment.Center
            ) {
                if (isSelected) {
                    Icon(
                        imageVector = Icons.Default.Check,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun ShapeAndStyleSection(
    currentShape: String,
    currentStyle: String,
    onShapeChange: (String) -> Unit,
    onStyleChange: (String) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        // Shapes
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth().testTag("segmented_puck_shape")
        ) {
            val shapes = listOf(
                Triple("circle", "Circle", "●"),
                Triple("rounded_square", "Square", "■"),
                Triple("squircle", "Squircle", "◆")
            )
            shapes.forEach { (shapeKey, shapeLabel, shapeIcon) ->
                val isSelected = currentShape == shapeKey
                Surface(
                    onClick = { onShapeChange(shapeKey) },
                    shape = RoundedCornerShape(12.dp),
                    color = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHighest,
                    border = BorderStroke(
                        1.dp,
                        if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.2f)
                    ),
                    modifier = Modifier.weight(1f).height(40.dp).testTag("chip_shape_$shapeKey")
                ) {
                    Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize().padding(horizontal = 4.dp)) {
                        Text(
                            text = "$shapeIcon $shapeLabel",
                            fontSize = 12.sp,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                            color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            softWrap = false,
                            modifier = Modifier.basicMarquee()
                        )
                    }
                }
            }
        }

        // Finishes
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth().testTag("segmented_puck_style")
        ) {
            val finishes = listOf(
                Pair("solid", "Solid"),
                Pair("frosted_glass", "✨ Frosted")
            )
            finishes.forEach { (styleKey, styleLabel) ->
                val isSelected = currentStyle == styleKey
                Surface(
                    onClick = { onStyleChange(styleKey) },
                    shape = RoundedCornerShape(12.dp),
                    color = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHighest,
                    border = BorderStroke(
                        1.dp,
                        if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.2f)
                    ),
                    modifier = Modifier.weight(1f).height(40.dp).testTag("chip_style_$styleKey")
                ) {
                    Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize().padding(horizontal = 4.dp)) {
                        Text(
                            text = styleLabel,
                            fontSize = 12.sp,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                            color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            softWrap = false,
                            modifier = Modifier.basicMarquee()
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun DimensionsSection(
    currentSize: Int,
    currentOpacity: Int,
    onSizeChange: (Int) -> Unit,
    onOpacityChange: (Int) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        // Size
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Size",
                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                maxLines = 1,
                softWrap = false
            )
            Surface(shape = RoundedCornerShape(6.dp), color = MaterialTheme.colorScheme.primaryContainer) {
                Text(
                    text = "${currentSize}dp",
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
            value = currentSize.toFloat(),
            onValueChange = { onSizeChange(it.roundToInt()) },
            valueRange = 36f..100f,
            modifier = Modifier.testTag("slider_puck_size")
        )

        // Opacity
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Opacity",
                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                maxLines = 1,
                softWrap = false
            )
            Surface(shape = RoundedCornerShape(6.dp), color = MaterialTheme.colorScheme.primaryContainer) {
                Text(
                    text = "$currentOpacity%",
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
            value = currentOpacity.toFloat(),
            onValueChange = { onOpacityChange(it.roundToInt()) },
            valueRange = 20f..100f,
            modifier = Modifier.testTag("slider_puck_opacity")
        )
    }
}

@Composable
private fun EmblemGridSection(
    currentIconStyle: String,
    onIconStyleChange: (String) -> Unit
) {
    LazyRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        contentPadding = PaddingValues(vertical = 4.dp)
    ) {
        items(IconStyleUtils.ALL_SHAPES) { item ->
            val isSelected = currentIconStyle == item.key
            Surface(
                onClick = { onIconStyleChange(item.key) },
                shape = RoundedCornerShape(12.dp),
                color = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHighest,
                border = BorderStroke(
                    1.dp,
                    if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.2f)
                ),
                modifier = Modifier.height(40.dp).testTag("chip_icon_${item.key}")
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Image(
                        painter = painterResource(id = item.resId),
                        contentDescription = item.label,
                        modifier = Modifier.size(18.dp),
                        colorFilter = androidx.compose.ui.graphics.ColorFilter.tint(
                            if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer
                            else MaterialTheme.colorScheme.onSurface
                        )
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = item.label,
                        fontSize = 12.sp,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                        color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        softWrap = false,
                        modifier = Modifier.basicMarquee()
                    )
                }
            }
        }
    }
}
