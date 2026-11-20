package com.sbf.lightspeed.system

import android.graphics.Color
import com.sbf.lightspeed.settings.DeckThemeManager
import com.sbf.lightspeed.settings.DeckThemeSnapshot

data class HudThemeSpec(
    val panelColor: Int,
    val panelAlpha: Float,
    val blurRadiusPx: Float,
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
    fun resolveSpec(
        useCentralTheme: Boolean,
        primaryColor: Int,
        density: Float,
        colorMode: String = if (useCentralTheme) LightspeedPreferences.COCKPIT_COLOR_MODE_THEME_12 else LightspeedPreferences.COCKPIT_COLOR_MODE_M3_DYNAMIC
    ): HudThemeSpec {
        val r = Color.red(primaryColor)
        val g = Color.green(primaryColor)
        val b = Color.blue(primaryColor)
        if (!useCentralTheme || colorMode == LightspeedPreferences.COCKPIT_COLOR_MODE_M3_DYNAMIC) {
            return HudThemeSpec(
                panelColor = Color.rgb((16 + r * 0.12f).toInt().coerceIn(0, 255), (20 + g * 0.12f).toInt().coerceIn(0, 255), (32 + b * 0.12f).toInt().coerceIn(0, 255)),
                panelAlpha = 0.92f,
                blurRadiusPx = 0f,
                tintColor = primaryColor,
                borderColor = Color.argb(175, r, g, b),
                borderWidthPx = 1.5f * density,
                cornerRadiusPx = 12f * density,
                glowColor = primaryColor,
                glowStrength = 0.30f,
                highlightStrength = 0.70f,
                isCentralTheme = false
            )
        }
        val snapshot: DeckThemeSnapshot = DeckThemeManager.snapshotFlow.value
        val rawPanel = snapshot.panelColorArgb.toInt()
        val rawTint = snapshot.tintArgb.toInt()
        val rawBorder = snapshot.borderColorArgb.toInt()
        val rawGlow = snapshot.glowColorArgb.toInt()

        val pR = Color.red(rawPanel)
        val pG = Color.green(rawPanel)
        val pB = Color.blue(rawPanel)
        val panelOpaque = Color.rgb(pR, pG, pB)

        // Enforce a crisp minimum frosted screenshot blur floor on Deflector Scrubber HUDs
        // whenever Screenshot or Automatic fallback blur is active, even if a theme slider is 0
        // (e.g. Material 3 on Samsung One UI). Avoid multiplying by screen density because
        // CentralCommandBackdropBlur.getBlurredCanvasBitmap already scales radiusPx to 1/4-res buckets;
        // 30f keeps frosted background structure legible instead of dissolving into flat mud on 3x screens.
        val rawBlurPx = snapshot.windowBlurRadiusPx.toFloat().coerceAtLeast(0f)
        val blurMode = com.sbf.lightspeed.settings.BackdropBlurModeManager.modeFlow.value
        val isScreenshotBlurActive = blurMode == com.sbf.lightspeed.settings.BackdropBlurMode.SCREENSHOT ||
            (blurMode == com.sbf.lightspeed.settings.BackdropBlurMode.AUTOMATIC &&
                !com.sbf.lightspeed.settings.CentralCommandActivity.isCrossWindowBlurActive)
        val blurPx = if (isScreenshotBlurActive) rawBlurPx.coerceAtLeast(30f) else rawBlurPx
        val minAlpha = if (blurPx <= 0f) 0.20f else 0.14f
        val isOpaqueMaterial3Fallback = blurPx > 0f && isScreenshotBlurActive &&
            snapshot.styleId == com.sbf.lightspeed.settings.DeckStylePresets.MATERIAL_3.styleId
        // When screenshot blur is active over opaque Material 3 (panelAlpha == 1.0f), cap the overlay
        // substrate alpha to 0.30f so the frosted backdrop stays distinctly translucent over dark apps.
        val maxAlpha = if (isOpaqueMaterial3Fallback) 0.30f else 1.0f
        val safeAlpha = snapshot.panelAlpha.coerceIn(minAlpha, maxAlpha)

        // Ensure legible, full-opacity tint contrast for icons, progress bars, headers & values
        val tR = Color.red(rawTint)
        val tG = Color.green(rawTint)
        val tB = Color.blue(rawTint)
        val opaqueTint = Color.rgb(tR, tG, tB)
        val lum = (0.299f * tR + 0.587f * tG + 0.114f * tB) / 255f
        val safeTint = if (lum < 0.45f) Color.WHITE else opaqueTint

        val corner = (snapshot.cornerRadiusDp * 0.45f * density).coerceIn(0f, 32f * density)
        val minStroke = if (safeAlpha < 0.34f || blurPx <= 0f) 1.15f * density else 0.75f * density
        val stroke = (snapshot.borderWidthDp * density).coerceIn(minStroke, 6f * density)
        val safeBorder = if (Color.alpha(rawBorder) < 130) Color.argb(175, tR, tG, tB) else rawBorder
        val safeHighlight = if (isOpaqueMaterial3Fallback) {
            snapshot.highlightStrength.coerceAtLeast(0.42f).coerceIn(0f, 1.5f)
        } else {
            snapshot.highlightStrength.coerceIn(0f, 1.5f)
        }

        if (colorMode == LightspeedPreferences.COCKPIT_COLOR_MODE_HYBRID_FUSION) {
            val fusedPanel = Color.rgb(
                (pR * 0.78f + r * 0.22f).toInt().coerceIn(0, 255),
                (pG * 0.78f + g * 0.22f).toInt().coerceIn(0, 255),
                (pB * 0.78f + b * 0.22f).toInt().coerceIn(0, 255)
            )
            val bR = Color.red(safeBorder)
            val bG = Color.green(safeBorder)
            val bB = Color.blue(safeBorder)
            val fusedBorder = Color.argb(
                Color.alpha(safeBorder).coerceAtLeast(185),
                (bR * 0.40f + r * 0.60f).toInt().coerceIn(0, 255),
                (bG * 0.40f + g * 0.60f).toInt().coerceIn(0, 255),
                (bB * 0.40f + b * 0.60f).toInt().coerceIn(0, 255)
            )
            return HudThemeSpec(
                panelColor = fusedPanel,
                panelAlpha = safeAlpha,
                blurRadiusPx = blurPx,
                tintColor = primaryColor,
                borderColor = fusedBorder,
                borderWidthPx = stroke,
                cornerRadiusPx = corner,
                glowColor = primaryColor,
                glowStrength = snapshot.glowStrength.coerceIn(0f, 1.5f),
                highlightStrength = safeHighlight,
                isCentralTheme = true
            )
        }

        return HudThemeSpec(
            panelColor = panelOpaque,
            panelAlpha = safeAlpha,
            blurRadiusPx = blurPx,
            tintColor = safeTint,
            borderColor = safeBorder,
            borderWidthPx = stroke,
            cornerRadiusPx = corner,
            glowColor = if (Color.alpha(rawGlow) == 0) safeTint else rawGlow,
            glowStrength = snapshot.glowStrength.coerceIn(0f, 1.5f),
            highlightStrength = safeHighlight,
            isCentralTheme = true
        )
    }
}
