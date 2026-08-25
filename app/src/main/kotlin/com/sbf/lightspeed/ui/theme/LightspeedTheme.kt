package com.sbf.lightspeed.ui.theme

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

private val DarkDefaultColorScheme = darkColorScheme(
    primary = Color(0xFF00E5FF),
    onPrimary = Color(0xFF00363D),
    primaryContainer = Color(0xFF004F58),
    onPrimaryContainer = Color(0xFF8CF4FF),
    secondary = Color(0xFF90CAF9),
    onSecondary = Color(0xFF003258),
    tertiary = Color(0xFFCE93D8),
    onTertiary = Color(0xFF381E48),
    background = Color(0xFF0F141C),
    onBackground = Color(0xFFE2E2E8),
    surface = Color(0xFF161B26),
    onSurface = Color(0xFFE2E2E8),
    surfaceVariant = Color(0xFF212836),
    onSurfaceVariant = Color(0xFFC3C7CF)
)

private val LightDefaultColorScheme = lightColorScheme(
    primary = Color(0xFF006874),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFF8CF4FF),
    onPrimaryContainer = Color(0xFF001F24),
    secondary = Color(0xFF1565C0),
    onSecondary = Color(0xFFFFFFFF),
    tertiary = Color(0xFF7B1FA2),
    onTertiary = Color(0xFFFFFFFF),
    background = Color(0xFFFBFDFE),
    onBackground = Color(0xFF191C1D),
    surface = Color(0xFFFBFDFE),
    onSurface = Color(0xFF191C1D),
    surfaceVariant = Color(0xFFDBE4E6),
    onSurfaceVariant = Color(0xFF3F484A)
)

/**
 * Unified Material 3 Expressive theme for Lightspeed.
 * Automatically utilizes Dynamic Color (Monet) on Android 12+ (API 31+) with deep space dark fallbacks.
 */
@Composable
fun LightspeedTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = true,
    forceDark: Boolean = false,
    content: @Composable () -> Unit
) {
    val context = LocalContext.current
    val isDark = forceDark || darkTheme

    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            if (isDark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        isDark -> DarkDefaultColorScheme
        else -> LightDefaultColorScheme
    }

    MaterialTheme(
        colorScheme = colorScheme,
        content = content
    )
}
