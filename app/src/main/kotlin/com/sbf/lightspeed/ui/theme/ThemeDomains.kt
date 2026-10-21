package com.sbf.lightspeed.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

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

// Cockpit default palette (matching CockpitDialog / CockpitGearPicker dark tactical surface)
val CockpitPalette = DomainPalette(
    panel = Color(0xF012141A),
    accent = Color(0xFF00E5FF),
    onPanel = Color.White
)

// Cruise overlay palette (matching tactical cruise overlay HUD elements)
val CruisePalette = DomainPalette(
    panel = Color(0xCC070B12),
    accent = Color(0xFF00E676),
    onPanel = Color(0xFFF0F5FF)
)

val LocalDomainPalette = staticCompositionLocalOf { CockpitPalette }
