package com.sbf.lightspeed.ui.theme

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import com.sbf.lightspeed.settings.DeckThemeSnapshot
import com.sbf.lightspeed.settings.resolveCanvasPalette

enum class ThemeDomain {
    COMMAND,
    COCKPIT,
    CRUISE
}

@Immutable
data class DomainPalette(
    val panel: Color,
    val accent: Color,
    val onPanel: Color
)

val CockpitPalette = DomainPalette(
    panel = Color(0xF012141A),
    accent = Color(0xFF00E5FF),
    onPanel = Color.White
)

val CruisePalette = DomainPalette(
    panel = Color(0xCC070B12),
    accent = Color(0xFF00E676),
    onPanel = Color(0xFFF0F5FF)
)

/**
 * Derives a [DomainPalette] dynamically from an active [DeckThemeSnapshot].
 */
fun DeckThemeSnapshot.resolveDomainPalette(
    domain: ThemeDomain,
    context: Context? = null,
    colorMode: String = "hybrid_fusion"
): DomainPalette {
    val canvasPal = resolveCanvasPalette(context, colorMode)
    val basePanel = Color(if (domain == ThemeDomain.CRUISE) canvasPal.panelOuterArgb else canvasPal.panelInnerArgb)
    return DomainPalette(
        panel = basePanel,
        accent = Color(canvasPal.primaryAccentArgb),
        onPanel = if (domain == ThemeDomain.CRUISE) Color(0xFFF0F5FF) else Color.White
    )
}

@Composable
fun rememberDomainPalette(
    domain: ThemeDomain,
    colorMode: String = "hybrid_fusion"
): DomainPalette {
    val snapshot = LocalDeckTheme.current
    val context = LocalContext.current
    return remember(snapshot, domain, colorMode) {
        snapshot.resolveDomainPalette(domain, context, colorMode)
    }
}

val LocalDomainPalette = staticCompositionLocalOf { CockpitPalette }
