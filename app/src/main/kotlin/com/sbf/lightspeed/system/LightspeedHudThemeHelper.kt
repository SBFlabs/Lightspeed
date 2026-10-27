package com.sbf.lightspeed.system

import android.graphics.Color
import com.sbf.lightspeed.settings.DeckThemeManager
import com.sbf.lightspeed.settings.DeckThemeSnapshot

data class HudThemeSpec(
    val panelColor: Int,
    val panelAlpha: Float,
    val tintColor: Int,
    val borderColor: Int,
    val borderWidthPx: Float,
    val cornerRadiusPx: Float,
    val glowColor: Int,
    val glowStrength: Float,
    val highlightStrength: Float,
    val isCentralTheme: Boolean
)

object LightspeedHudThemeHelper {
    fun resolveSpec(useCentralTheme: Boolean, primaryColor: Int, density: Float): HudThemeSpec {
        if (!useCentralTheme) {
            val r = Color.red(primaryColor)
            val g = Color.green(primaryColor)
            val b = Color.blue(primaryColor)
            return HudThemeSpec(
                panelColor = Color.rgb((16 + r * 0.08f).toInt().coerceIn(0, 255), (20 + g * 0.08f).toInt().coerceIn(0, 255), (32 + b * 0.08f).toInt().coerceIn(0, 255)),
                panelAlpha = 0.92f,
                tintColor = primaryColor,
                borderColor = Color.argb(140, r, g, b),
                borderWidthPx = 1.4f * density,
                cornerRadiusPx = 10f * density,
                glowColor = primaryColor,
                glowStrength = 0.25f,
                highlightStrength = 0.70f,
                isCentralTheme = false
            )
        }
        val snapshot: DeckThemeSnapshot = DeckThemeManager.snapshotFlow.value
        val rawPanel = snapshot.panelColorArgb.toInt()
        val rawTint = snapshot.tintArgb.toInt()
        val rawBorder = snapshot.borderColorArgb.toInt()
        val rawGlow = snapshot.glowColorArgb.toInt()

        // Ensure rich, solid panel colors (never transparent)
        val pR = Color.red(rawPanel)
        val pG = Color.green(rawPanel)
        val pB = Color.blue(rawPanel)
        val panelOpaque = Color.rgb(pR, pG, pB)

        // HUD backplane requires minimum 88%..95% opacity for outdoor & overlay legibility
        val safeAlpha = (snapshot.panelAlpha * 0.25f + 0.70f).coerceIn(0.88f, 0.95f)

        // Ensure legible tint contrast for title & values
        val tR = Color.red(rawTint)
        val tG = Color.green(rawTint)
        val tB = Color.blue(rawTint)
        val lum = (0.299f * tR + 0.587f * tG + 0.114f * tB) / 255f
        val safeTint = if (lum < 0.45f) Color.WHITE else rawTint

        val corner = (snapshot.cornerRadiusDp * 0.45f * density).coerceIn(8f * density, 22f * density)
        val stroke = (snapshot.borderWidthDp * density).coerceIn(1.2f * density, 3.0f * density)
        val safeBorder = if (Color.alpha(rawBorder) < 90) Color.argb(160, tR, tG, tB) else rawBorder

        return HudThemeSpec(
            panelColor = panelOpaque,
            panelAlpha = safeAlpha,
            tintColor = safeTint,
            borderColor = safeBorder,
            borderWidthPx = stroke,
            cornerRadiusPx = corner,
            glowColor = if (rawGlow == 0) safeTint else rawGlow,
            glowStrength = snapshot.glowStrength.coerceAtLeast(0.20f),
            highlightStrength = snapshot.highlightStrength.coerceAtLeast(0.50f),
            isCentralTheme = true
        )
    }
}
