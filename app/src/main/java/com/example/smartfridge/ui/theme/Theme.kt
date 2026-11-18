package com.example.smartfridge.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.ui.graphics.Color
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext

private val DarkColorScheme = darkColorScheme(
    primary = SteelPrimary,
    onPrimary = Color(0xFF17191D),
    primaryContainer = Color(0xFF343943),
    onPrimaryContainer = Color(0xFFE4E6EB),
    secondary = SteelSecondary,
    onSecondary = Color(0xFFF0F1F4),
    secondaryContainer = Color(0xFF30343C),
    onSecondaryContainer = Color(0xFFD5D8E0),
    tertiary = MutedAmber,
    onTertiary = Color(0xFF24180E),
    tertiaryContainer = Color(0xFF493323),
    onTertiaryContainer = Color(0xFFFFE2C5),
    background = GraphiteBackground,
    onBackground = GraphiteText,
    surface = GraphiteSurface,
    onSurface = GraphiteText,
    surfaceVariant = GraphiteSurfaceVariant,
    onSurfaceVariant = GraphiteMutedText,
    outline = Color(0xFF626875),
    outlineVariant = Color(0xFF3A3E47),
    error = Color(0xFFD58B82),
    onError = Color(0xFF2A1110)
)

private val LightColorScheme = lightColorScheme(
    primary = Color(0xFF348A69),
    onPrimary = Color.White,
    secondary = Color(0xFF6A9B83),
    tertiary = Color(0xFFD49B4E),
    background = Color(0xFFF7F8F4),
    surface = Color(0xFFFFFFFF),
    onBackground = Color(0xFF1F302A),
    onSurface = Color(0xFF1F302A)

    /* Other default colors to override
    background = Color(0xFFFFFBFE),
    surface = Color(0xFFFFFBFE),
    onPrimary = Color.White,
    onSecondary = Color.White,
    onTertiary = Color.White,
    onBackground = Color(0xFF1C1B1F),
    onSurface = Color(0xFF1C1B1F),
    */
)

@Composable
fun SmartFridgeTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    // Dynamic color is available on Android 12+
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
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