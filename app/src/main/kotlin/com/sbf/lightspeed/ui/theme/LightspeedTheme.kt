package com.sbf.lightspeed.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalContext
import com.sbf.lightspeed.settings.DeckStylePresets
import com.sbf.lightspeed.settings.DeckThemeManager
import com.sbf.lightspeed.system.LightspeedPreferences

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

private fun contrastRatio(a: Color, b: Color): Float {
    val la = a.copy(alpha = 1f).luminance()
    val lb = b.copy(alpha = 1f).luminance()
    val maxL = maxOf(la, lb)
    val minL = minOf(la, lb)
    return (maxL + 0.05f) / (minL + 0.05f)
}

private fun ensureContrast(color: Color, against: Color, minRatio: Float): Color {
    val opaque = color.copy(alpha = 1f)
    val opaqueAgainst = against.copy(alpha = 1f)
    if (contrastRatio(opaque, opaqueAgainst) >= minRatio) {
        return opaque
    }
    val target = if (opaqueAgainst.luminance() < 0.5f) Color.White else Color.Black
    for (step in 1..10) {
        val candidate = lerp(opaque, target, step / 10f)
        if (contrastRatio(candidate, opaqueAgainst) >= minRatio) {
            return candidate
        }
    }
    return target
}

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
    val savedThemeMode by LightspeedPreferences.themeModeFlow.collectAsState()
    val themeMode = savedThemeMode ?: remember { LightspeedPreferences.getThemeMode(context) }
    val isDark = forceDark || when (themeMode) {
        "light" -> false
        "dark" -> true
        else -> darkTheme
    }

    LaunchedEffect(context) {
        DeckThemeManager.getSnapshot(context)
    }
    val snapshot by DeckThemeManager.snapshotFlow.collectAsState()
    val isApi31Plus = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
    val useDynamic = snapshot.usesSystemDynamicColor &&
        isApi31Plus &&
        snapshot.styleId == DeckStylePresets.ADAPTIVE.styleId

    val baseColorScheme = when {
        dynamicColor && isApi31Plus -> {
            if (isDark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        isDark -> DarkDefaultColorScheme
        else -> LightDefaultColorScheme
    }

    val baseLsColors = if (isDark) DarkLightspeedColors else LightLightspeedColors

    val (colorScheme, lsColors) = remember(baseColorScheme, baseLsColors, snapshot, useDynamic) {
        if (useDynamic) {
            val panel = baseColorScheme.surfaceContainerHigh
            val onPanel = if (panel.luminance() > 0.5f) Color.Black else Color.White
            val alpha = snapshot.panelAlpha.coerceIn(0.55f, 0.92f)
            val ls = baseLsColors.copy(
                textPrimary = onPanel,
                textSecondary = onPanel.copy(alpha = 0.75f),
                textDisabled = onPanel.copy(alpha = 0.45f),
                dialogSurface = panel.copy(alpha = snapshot.panelAlpha.coerceIn(0.78f, 0.90f)),
                borderSubtle = baseColorScheme.primary.copy(alpha = 0.45f),
                accentInfo = baseColorScheme.primary
            )
            baseColorScheme.copy(
                surfaceContainer = baseColorScheme.surfaceContainer.copy(alpha = alpha),
                surfaceContainerHigh = baseColorScheme.surfaceContainerHigh.copy(alpha = alpha),
                surface = baseColorScheme.surface.copy(alpha = alpha)
            ) to ls
        } else {
            val panel = Color(snapshot.panelColorArgb).copy(alpha = 1f)
            val tint = Color(snapshot.tintArgb).copy(alpha = 1f)
            val styleTint = ensureContrast(tint, panel, 3.0f)
            val rawBorder = Color(snapshot.borderColorArgb)
            val borderBase = if (rawBorder.alpha > 0.05f) rawBorder else styleTint

            val panelLight = panel.luminance() > 0.5f
            val onSurfaceColor = if (panelLight) Color.Black else Color.White
            val onSurfaceVariantColor = onSurfaceColor.copy(alpha = 0.75f)

            val onPrimaryColor = if (styleTint.luminance() > 0.5f) Color.Black else Color.White
            val primaryContainerColor = lerp(panel, styleTint, 0.28f)
            val onPrimaryContainerColor = if (primaryContainerColor.luminance() > 0.5f) Color.Black else Color.White
            val secondaryColor = lerp(styleTint, onSurfaceColor, 0.25f)
            val onSecondaryColor = if (secondaryColor.luminance() > 0.5f) Color.Black else Color.White
            val rawTertiary = if (Color(snapshot.glowColorArgb).alpha > 0.05f) {
                Color(snapshot.glowColorArgb).copy(alpha = 1f)
            } else {
                lerp(tint, panel, 0.35f)
            }
            val tertiaryColor = ensureContrast(rawTertiary, panel, 3.0f)
            val onTertiaryColor = if (tertiaryColor.luminance() > 0.5f) Color.Black else Color.White

            val shiftTarget = if (panelLight) Color.Black else Color.White
            val alpha = snapshot.panelAlpha.coerceIn(0.55f, 0.92f)
            val cs = baseColorScheme.copy(
                primary = styleTint,
                onPrimary = onPrimaryColor,
                primaryContainer = primaryContainerColor,
                onPrimaryContainer = onPrimaryContainerColor,
                secondary = secondaryColor,
                onSecondary = onSecondaryColor,
                tertiary = tertiaryColor,
                onTertiary = onTertiaryColor,
                background = lerp(panel, Color.Black, 0.20f),
                onBackground = onSurfaceColor,
                surface = panel.copy(alpha = alpha),
                onSurface = onSurfaceColor,
                surfaceVariant = lerp(panel, shiftTarget, 0.08f),
                onSurfaceVariant = onSurfaceVariantColor,
                surfaceContainerLowest = lerp(panel, Color.Black, 0.25f),
                surfaceContainerLow = lerp(panel, shiftTarget, 0.04f).copy(alpha = alpha),
                surfaceContainer = lerp(panel, shiftTarget, 0.07f).copy(alpha = alpha),
                surfaceContainerHigh = lerp(panel, shiftTarget, 0.11f).copy(alpha = alpha),
                surfaceContainerHighest = lerp(panel, shiftTarget, 0.16f).copy(alpha = alpha),
                outline = borderBase.copy(alpha = 0.65f),
                outlineVariant = borderBase.copy(alpha = 0.30f)
            )

            val nonDynamicBaseLs = if (panelLight) LightLightspeedColors else DarkLightspeedColors
            val onPanel = if (panelLight) Color.Black else Color.White
            val dialogSubstrate = lerp(panel, styleTint, 0.08f).copy(alpha = snapshot.panelAlpha.coerceIn(0.78f, 0.90f))
            val ls = nonDynamicBaseLs.copy(
                textPrimary = onPanel,
                textSecondary = onPanel.copy(alpha = 0.75f),
                textDisabled = onPanel.copy(alpha = 0.45f),
                dialogSurface = dialogSubstrate,
                borderSubtle = borderBase.copy(alpha = 0.45f),
                accentInfo = styleTint
            )
            cs to ls
        }
    }

    MaterialTheme(colorScheme = colorScheme) {
        CompositionLocalProvider(
            LocalLightspeedColors provides lsColors,
            LocalDeckTheme provides snapshot
        ) {
            content()
        }
    }
}
