package com.example.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

private val DarkColorScheme = darkColorScheme(
    primary = BrownPrimaryLight,
    onPrimary = Color(0xFF261A17),
    primaryContainer = BrownPrimaryDark,
    onPrimaryContainer = Color(0xFFEFEBE9),
    secondary = BrownSecondary,
    onSecondary = Color.White,
    tertiary = BrownTertiary,
    background = BrownDarkBackground,
    surface = BrownDarkSurface,
    surfaceVariant = BrownDarkSurfaceVariant,
    onBackground = BrownDarkOnSurface,
    onSurface = BrownDarkOnSurface
)

private val LightColorScheme = lightColorScheme(
    primary = BrownPrimary,
    onPrimary = Color.White,
    primaryContainer = BrownLightContainer,
    onPrimaryContainer = BrownLightOnContainer,
    secondary = BrownSecondary,
    onSecondary = Color.White,
    tertiary = BrownTertiary,
    background = BrownLightBackground,
    surface = BrownLightSurface,
    surfaceVariant = BrownLightSurfaceVariant,
    onBackground = Color(0xFF2C1D1A),
    onSurface = Color(0xFF2C1D1A)
)

@Composable
fun SmartPuckTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit,
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}

