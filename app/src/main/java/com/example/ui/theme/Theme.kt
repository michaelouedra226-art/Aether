package com.example.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private val SpotifyDarkColorScheme = darkColorScheme(
    primary = SpotifyGreen,
    onPrimary = Color.Black,
    primaryContainer = SpotifyGreenDark,
    onPrimaryContainer = Color.White,
    secondary = SpotifyGreenLight,
    onSecondary = Color.Black,
    secondaryContainer = SpotifySurfaceVariant,
    onSecondaryContainer = SpotifyGreenLight,
    tertiary = NeonCyan,
    onTertiary = Color.Black,
    background = SpotifyBlack,
    onBackground = TextPrimary,
    surface = SpotifyDarkSurface,
    onSurface = TextPrimary,
    surfaceVariant = SpotifySurfaceVariant,
    onSurfaceVariant = TextSecondary,
    outline = SpotifyBorder,
    outlineVariant = SpotifySurfaceHighlight,
    error = NeonMagenta,
    onError = Color.White
)

private val SpotifyOledColorScheme = darkColorScheme(
    primary = SpotifyGreen,
    onPrimary = Color.Black,
    primaryContainer = SpotifyGreenDark,
    onPrimaryContainer = Color.White,
    secondary = SpotifyGreenLight,
    onSecondary = Color.Black,
    secondaryContainer = SpotifySurfaceVariant,
    onSecondaryContainer = SpotifyGreenLight,
    tertiary = NeonCyan,
    onTertiary = Color.Black,
    background = SpotifyPureBlack,
    onBackground = TextPrimary,
    surface = SpotifyDarkSurface,
    onSurface = TextPrimary,
    surfaceVariant = SpotifySurfaceVariant,
    onSurfaceVariant = TextSecondary,
    outline = SpotifyBorder,
    outlineVariant = SpotifySurfaceHighlight,
    error = NeonMagenta,
    onError = Color.White
)

@Composable
fun AetherTheme(
    themePreference: String = "DARK_OLED", // DARK_OLED, CYBER_DARK, SYSTEM
    content: @Composable () -> Unit
) {
    val systemInDark = isSystemInDarkTheme()
    val isDark = when (themePreference) {
        "SYSTEM" -> systemInDark
        else -> true
    }

    val colorScheme = when (themePreference) {
        "DARK_OLED" -> SpotifyOledColorScheme
        else -> SpotifyDarkColorScheme
    }

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            val bg = colorScheme.background
            window.statusBarColor = bg.toArgb()
            window.navigationBarColor = bg.toArgb()
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !isDark
            WindowCompat.getInsetsController(window, view).isAppearanceLightNavigationBars = !isDark
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}

@Composable
fun MyApplicationTheme(
    themePreference: String = "DARK_OLED",
    content: @Composable () -> Unit
) {
    AetherTheme(themePreference = themePreference, content = content)
}
