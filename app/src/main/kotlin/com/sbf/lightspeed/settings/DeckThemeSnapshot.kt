package com.sbf.lightspeed.settings

/**
 * Snapshot of visual parameters for a Flight Control Deck theme style.
 * Standalone data definition and presets (not wired to any screen yet).
 */
data class DeckThemeSnapshot(
    val styleId: String,
    val displayName: String,
    val panelColorArgb: Long,
    val tintArgb: Long,
    val panelAlpha: Float,
    val borderColorArgb: Long,
    val borderWidthDp: Float,
    val cornerRadiusDp: Float,
    val windowBlurRadiusPx: Int,
    val inAppBlurRadiusDp: Float,
    val dimAmount: Float,
    val glowColorArgb: Long,
    val glowStrength: Float,
    val highlightStrength: Float,
    val noiseStrength: Float,
    val shadowLayers: Int,
    val isProgressive: Boolean,
    val progressiveStartBlurPx: Int = windowBlurRadiusPx,
    val progressiveEndBlurPx: Int = windowBlurRadiusPx,
    val usesSystemDynamicColor: Boolean,
    val minApiForFullEffect: Int,
    val freeTier: Boolean
)

object DeckStylePresets {
    val PRISM = DeckThemeSnapshot(
        styleId = "prism",
        displayName = "Glass Lava",
        panelColorArgb = 0xFF0E1117L,
        tintArgb = 0xFFFFFFFFL,
        panelAlpha = 0.85f,
        borderColorArgb = 0x66FFFFFFL,
        borderWidthDp = 1.2f,
        cornerRadiusDp = 28f,
        windowBlurRadiusPx = 75,
        inAppBlurRadiusDp = 22f,
        dimAmount = 0.65f,
        glowColorArgb = 0x33FFFFFFL,
        glowStrength = 0.50f,
        highlightStrength = 0.65f,
        noiseStrength = 0.0f,
        shadowLayers = 1,
        isProgressive = false,
        progressiveStartBlurPx = 75,
        progressiveEndBlurPx = 75,
        usesSystemDynamicColor = false,
        minApiForFullEffect = 33,
        freeTier = true
    )

    val JELLY = DeckThemeSnapshot(
        styleId = "jelly",
        displayName = "Liquid Jelly",
        panelColorArgb = 0xFF281C54L,
        tintArgb = 0xFF7C4DFFL,
        panelAlpha = 0.80f,
        borderColorArgb = 0xFFB388FFL,
        borderWidthDp = 2.6f,
        cornerRadiusDp = 36f,
        windowBlurRadiusPx = 28,
        inAppBlurRadiusDp = 10f,
        dimAmount = 0.55f,
        glowColorArgb = 0xFF7C4DFFL,
        glowStrength = 0.70f,
        highlightStrength = 0.50f,
        noiseStrength = 0.0f,
        shadowLayers = 2,
        isProgressive = false,
        usesSystemDynamicColor = false,
        minApiForFullEffect = 31,
        freeTier = true
    )

    val FROSTED = DeckThemeSnapshot(
        styleId = "frosted",
        displayName = "Frosted Glass",
        panelColorArgb = 0xFF1E222AL,
        tintArgb = 0xFFCFD8DCL,
        panelAlpha = 0.65f,
        borderColorArgb = 0x99FFFFFFL,
        borderWidthDp = 1.4f,
        cornerRadiusDp = 28f,
        windowBlurRadiusPx = 70,
        inAppBlurRadiusDp = 20f,
        dimAmount = 0.50f,
        glowColorArgb = 0x00000000L,
        glowStrength = 0.0f,
        highlightStrength = 0.0f,
        noiseStrength = 0.0f,
        shadowLayers = 1,
        isProgressive = false,
        usesSystemDynamicColor = false,
        minApiForFullEffect = 31,
        freeTier = true
    )

    val BLUR = DeckThemeSnapshot(
        styleId = "blur",
        displayName = "Dark Blur",
        panelColorArgb = 0xFF0A0D14L,
        tintArgb = 0xFF101520L,
        panelAlpha = 0.90f,
        borderColorArgb = 0x00000000L,
        borderWidthDp = 0.0f,
        cornerRadiusDp = 28f,
        windowBlurRadiusPx = 100,
        inAppBlurRadiusDp = 30f,
        dimAmount = 0.70f,
        glowColorArgb = 0x00000000L,
        glowStrength = 0.0f,
        highlightStrength = 0.10f,
        noiseStrength = 0.0f,
        shadowLayers = 1,
        isProgressive = false,
        usesSystemDynamicColor = false,
        minApiForFullEffect = 31,
        freeTier = true
    )

    val PROGRESSIVE = DeckThemeSnapshot(
        styleId = "progressive",
        displayName = "Progressive Blur",
        panelColorArgb = 0xFF121622L,
        tintArgb = 0xFF40C4FFL,
        panelAlpha = 0.78f,
        borderColorArgb = 0x6680D8FFL,
        borderWidthDp = 1.1f,
        cornerRadiusDp = 28f,
        windowBlurRadiusPx = 85,
        inAppBlurRadiusDp = 24f,
        dimAmount = 0.60f,
        glowColorArgb = 0xFF40C4FFL,
        glowStrength = 0.30f,
        highlightStrength = 0.45f,
        noiseStrength = 0.0f,
        shadowLayers = 1,
        isProgressive = true,
        progressiveStartBlurPx = 18,
        progressiveEndBlurPx = 95,
        usesSystemDynamicColor = true,
        minApiForFullEffect = 33,
        freeTier = true
    )

    val GLASSMORPHISM = DeckThemeSnapshot(
        styleId = "glassmorphism",
        displayName = "Glassmorphism",
        panelColorArgb = 0xFF181E2CL,
        tintArgb = 0xFFFFFFFFL,
        panelAlpha = 0.55f,
        borderColorArgb = 0xB3FFFFFFL,
        borderWidthDp = 0.9f,
        cornerRadiusDp = 26f,
        windowBlurRadiusPx = 55,
        inAppBlurRadiusDp = 16f,
        dimAmount = 0.48f,
        glowColorArgb = 0xFFFFFFFFL,
        glowStrength = 0.20f,
        highlightStrength = 0.55f,
        noiseStrength = 0.0f,
        shadowLayers = 1,
        isProgressive = false,
        usesSystemDynamicColor = false,
        minApiForFullEffect = 31,
        freeTier = true
    )

    val NEON = DeckThemeSnapshot(
        styleId = "neon",
        displayName = "Neon Glass",
        panelColorArgb = 0xFF080A12L,
        tintArgb = 0xFF00E5FFL,
        panelAlpha = 0.84f,
        borderColorArgb = 0xFF00E5FFL,
        borderWidthDp = 1.8f,
        cornerRadiusDp = 28f,
        windowBlurRadiusPx = 30,
        inAppBlurRadiusDp = 10f,
        dimAmount = 0.72f,
        glowColorArgb = 0xFFE040FBL,
        glowStrength = 0.85f,
        highlightStrength = 0.40f,
        noiseStrength = 0.0f,
        shadowLayers = 2,
        isProgressive = false,
        usesSystemDynamicColor = false,
        minApiForFullEffect = 31,
        freeTier = false
    )

    val ADAPTIVE = DeckThemeSnapshot(
        styleId = "adaptive",
        displayName = "Adaptive Color",
        panelColorArgb = 0xFF141824L,
        tintArgb = 0xFF6366F1L,
        panelAlpha = 0.82f,
        borderColorArgb = 0xFF818CF8L,
        borderWidthDp = 1.2f,
        cornerRadiusDp = 28f,
        windowBlurRadiusPx = 60,
        inAppBlurRadiusDp = 18f,
        dimAmount = 0.60f,
        glowColorArgb = 0xFF6366F1L,
        glowStrength = 0.40f,
        highlightStrength = 0.35f,
        noiseStrength = 0.0f,
        shadowLayers = 1,
        isProgressive = false,
        usesSystemDynamicColor = true,
        minApiForFullEffect = 31,
        freeTier = false
    )

    val ACRYLIC = DeckThemeSnapshot(
        styleId = "acrylic",
        displayName = "Acrylic",
        panelColorArgb = 0xFF1A1E28L,
        tintArgb = 0xFF90A4AEL,
        panelAlpha = 0.76f,
        borderColorArgb = 0x55FFFFFFL,
        borderWidthDp = 1.0f,
        cornerRadiusDp = 24f,
        windowBlurRadiusPx = 60,
        inAppBlurRadiusDp = 18f,
        dimAmount = 0.55f,
        glowColorArgb = 0x00000000L,
        glowStrength = 0.0f,
        highlightStrength = 0.25f,
        noiseStrength = 0.08f,
        shadowLayers = 1,
        isProgressive = false,
        usesSystemDynamicColor = false,
        minApiForFullEffect = 31,
        freeTier = false
    )

    val GAUSSIAN = DeckThemeSnapshot(
        styleId = "gaussian",
        displayName = "Gaussian Blur",
        panelColorArgb = 0xFF161A22L,
        tintArgb = 0xFFE0E6EDL,
        panelAlpha = 0.70f,
        borderColorArgb = 0x00000000L,
        borderWidthDp = 0.0f,
        cornerRadiusDp = 28f,
        windowBlurRadiusPx = 65,
        inAppBlurRadiusDp = 20f,
        dimAmount = 0.50f,
        glowColorArgb = 0x00000000L,
        glowStrength = 0.0f,
        highlightStrength = 0.15f,
        noiseStrength = 0.0f,
        shadowLayers = 1,
        isProgressive = false,
        usesSystemDynamicColor = false,
        minApiForFullEffect = 31,
        freeTier = false
    )

    val DEPTH = DeckThemeSnapshot(
        styleId = "depth",
        displayName = "Depth Layering",
        panelColorArgb = 0xFF101520L,
        tintArgb = 0xFF5C6BC0L,
        panelAlpha = 0.86f,
        borderColorArgb = 0x667986CBL,
        borderWidthDp = 1.2f,
        cornerRadiusDp = 28f,
        windowBlurRadiusPx = 32,
        inAppBlurRadiusDp = 10f,
        dimAmount = 0.65f,
        glowColorArgb = 0xFF3949ABL,
        glowStrength = 0.35f,
        highlightStrength = 0.30f,
        noiseStrength = 0.0f,
        shadowLayers = 3,
        isProgressive = false,
        usesSystemDynamicColor = false,
        minApiForFullEffect = 31,
        freeTier = false
    )

    val DARK = DeckThemeSnapshot(
        styleId = "dark",
        displayName = "Dark Mode Glass",
        panelColorArgb = 0xFF05070BL,
        tintArgb = 0xFF263238L,
        panelAlpha = 0.92f,
        borderColorArgb = 0x26FFFFFFL,
        borderWidthDp = 1.0f,
        cornerRadiusDp = 28f,
        windowBlurRadiusPx = 40,
        inAppBlurRadiusDp = 12f,
        dimAmount = 0.78f,
        glowColorArgb = 0x00000000L,
        glowStrength = 0.0f,
        highlightStrength = 0.12f,
        noiseStrength = 0.0f,
        shadowLayers = 1,
        isProgressive = false,
        usesSystemDynamicColor = false,
        minApiForFullEffect = 31,
        freeTier = false
    )

    val ALL: List<DeckThemeSnapshot> = listOf(
        PRISM,
        JELLY,
        FROSTED,
        BLUR,
        PROGRESSIVE,
        GLASSMORPHISM,
        NEON,
        ADAPTIVE,
        ACRYLIC,
        GAUSSIAN,
        DEPTH,
        DARK
    )

    fun resolve(styleId: String): DeckThemeSnapshot {
        return ALL.find { it.styleId == styleId } ?: PRISM
    }
}

/**
 * Rich, high-contrast 12-theme palette resolved specifically for Canvas overlays
 * (Cockpit Gear Rings, Cockpit Hangar Deck, and Cybernetic Trackpad + 3 Buttons).
 * Ensures every single one of the 12 themes has its unmistakable signature surface fill,
 * dual-tone gradient, neon/glass border, and accent glow.
 */
data class CanvasThemePalette(
    val panelInnerArgb: Int,
    val panelOuterArgb: Int,
    val primaryAccentArgb: Int,
    val secondaryAccentArgb: Int,
    val borderArgb: Int
)

fun DeckThemeSnapshot.resolveCanvasPalette(context: android.content.Context? = null): CanvasThemePalette {
    if (styleId == "adaptive" && usesSystemDynamicColor &&
        android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S && context != null
    ) {
        try {
            val p = context.getColor(android.R.color.system_accent1_300)
            val s = context.getColor(android.R.color.system_accent2_300)
            val bgIn = context.getColor(android.R.color.system_neutral1_800)
            val bgOut = context.getColor(android.R.color.system_neutral1_900)
            return CanvasThemePalette(
                panelInnerArgb = (bgIn and 0x00FFFFFF) or (0xE0 shl 24),
                panelOuterArgb = (bgOut and 0x00FFFFFF) or (0xEE shl 24),
                primaryAccentArgb = p or (0xFF shl 24),
                secondaryAccentArgb = s or (0xFF shl 24),
                borderArgb = p or (0xFF shl 24)
            )
        } catch (_: Exception) {}
    }

    val pR = ((panelColorArgb shr 16) and 0xFF).toInt()
    val pG = ((panelColorArgb shr 8) and 0xFF).toInt()
    val pB = (panelColorArgb and 0xFF).toInt()

    val tR = ((tintArgb shr 16) and 0xFF).toInt()
    val tG = ((tintArgb shr 8) and 0xFF).toInt()
    val tB = (tintArgb and 0xFF).toInt()

    // Blend tint into panelColor using the exact same intensity curve as DeckOpticalCacheBuilder
    val tintMix = when (styleId) {
        "jelly" -> 0.36f
        "prism" -> 0.10f
        "glassmorphism" -> 0.16f
        "frosted" -> 0.22f
        "gaussian" -> 0.20f
        "progressive" -> 0.22f
        "adaptive" -> 0.26f
        "depth" -> 0.24f
        "acrylic" -> 0.20f
        "neon" -> 0.18f
        else -> 0.12f
    }

    val inR = (pR * (1f - tintMix) + tR * tintMix).toInt().coerceIn(0, 255)
    val inG = (pG * (1f - tintMix) + tG * tintMix).toInt().coerceIn(0, 255)
    val inB = (pB * (1f - tintMix) + tB * tintMix).toInt().coerceIn(0, 255)

    val outAlpha = (panelAlpha.coerceIn(0.55f, 0.94f) * 255f).toInt()
    val inAlpha = ((panelAlpha * 0.92f).coerceIn(0.48f, 0.90f) * 255f).toInt()

    val panelInner = (inAlpha shl 24) or (inR shl 16) or (inG shl 8) or inB
    val panelOuter = (outAlpha shl 24) or (pR shl 16) or (pG shl 8) or pB

    // Ensure dark-tinted stealth themes ("blur", "dark") still have a clean silver-white specular accent
    val tintLum = (tR * 299 + tG * 587 + tB * 114) / 1000
    val primaryAccent = if (tintLum < 45) {
        0xFFCFD8DC.toInt()
    } else {
        (0xFF shl 24) or (tR shl 16) or (tG shl 8) or tB
    }

    val glowAlpha = ((glowColorArgb ushr 24) and 0xFF).toInt()
    val secondaryAccent = if (glowAlpha > 0 && (glowColorArgb and 0x00FFFFFFL) != 0L) {
        (0xFF shl 24) or (glowColorArgb.toInt() and 0x00FFFFFF)
    } else {
        primaryAccent
    }

    val borderAlpha = ((borderColorArgb ushr 24) and 0xFF).toInt()
    val resolvedBorder = if (borderAlpha > 0 && (borderColorArgb and 0x00FFFFFFL) != 0L) {
        (0xFF shl 24) or (borderColorArgb.toInt() and 0x00FFFFFF)
    } else {
        primaryAccent
    }

    return CanvasThemePalette(
        panelInnerArgb = panelInner,
        panelOuterArgb = panelOuter,
        primaryAccentArgb = primaryAccent,
        secondaryAccentArgb = secondaryAccent,
        borderArgb = resolvedBorder
    )
}
