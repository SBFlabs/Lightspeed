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
) {
    /** Returns the user-facing theme name localized to the active app language. */
    fun localizedName(): String = when (styleId) {
        "material3" -> com.sbf.lightspeed.system.lsString(com.sbf.lightspeed.R.string.theme_name_material3)
        "prism" -> com.sbf.lightspeed.system.lsString(com.sbf.lightspeed.R.string.theme_name_prism)
        "jelly" -> com.sbf.lightspeed.system.lsString(com.sbf.lightspeed.R.string.theme_name_jelly)
        "frosted" -> com.sbf.lightspeed.system.lsString(com.sbf.lightspeed.R.string.theme_name_frosted)
        "blur" -> com.sbf.lightspeed.system.lsString(com.sbf.lightspeed.R.string.theme_name_blur)
        "progressive" -> com.sbf.lightspeed.system.lsString(com.sbf.lightspeed.R.string.theme_name_progressive)
        "glassmorphism" -> com.sbf.lightspeed.system.lsString(com.sbf.lightspeed.R.string.theme_name_glassmorphism)
        "neon" -> com.sbf.lightspeed.system.lsString(com.sbf.lightspeed.R.string.theme_name_neon)
        "adaptive" -> com.sbf.lightspeed.system.lsString(com.sbf.lightspeed.R.string.theme_name_adaptive)
        "acrylic" -> com.sbf.lightspeed.system.lsString(com.sbf.lightspeed.R.string.theme_name_acrylic)
        "gaussian" -> com.sbf.lightspeed.system.lsString(com.sbf.lightspeed.R.string.theme_name_gaussian)
        "depth" -> com.sbf.lightspeed.system.lsString(com.sbf.lightspeed.R.string.theme_name_depth)
        "dark" -> com.sbf.lightspeed.system.lsString(com.sbf.lightspeed.R.string.theme_name_dark)
        else -> displayName
    }

    /**
     * Resolves an accent/tint color guaranteed to have legible contrast against dark glass backgrounds.
     * When stealth themes ("blur", "dark") specify a near-black tint for the glass backplane,
     * this falls back to a bright silver specular accent (#CFD8DC) so text, icons, percentages,
     * and sliders remain crisp and visible.
     */
    val effectiveTintArgb: Long
        get() {
            val tR = ((tintArgb ushr 16) and 0xFF).toInt()
            val tG = ((tintArgb ushr 8) and 0xFF).toInt()
            val tB = (tintArgb and 0xFF).toInt()
            val tintLum = (tR * 299 + tG * 587 + tB * 114) / 1000
            return if (tintLum < 65) {
                0xFFCFD8DCL
            } else {
                tintArgb
            }
        }
}

object DeckStylePresets {
    val MATERIAL_3 = DeckThemeSnapshot(
        styleId = "material3",
        displayName = "Material 3",
        panelColorArgb = 0xFF1D1B20L,
        tintArgb = 0xFFD0BCFFL,
        panelAlpha = 1.0f,
        borderColorArgb = 0xFF49454FL,
        borderWidthDp = 1.0f,
        cornerRadiusDp = 28f,
        windowBlurRadiusPx = 0,
        inAppBlurRadiusDp = 0f,
        dimAmount = 0.45f,
        glowColorArgb = 0x00000000L,
        glowStrength = 0.0f,
        highlightStrength = 0.0f,
        noiseStrength = 0.0f,
        shadowLayers = 1,
        isProgressive = false,
        usesSystemDynamicColor = true,
        minApiForFullEffect = 26,
        freeTier = true
    )

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
        panelColorArgb = 0xFF1C2532L,
        tintArgb = 0xFFE2F1F8L,
        panelAlpha = 0.62f,
        borderColorArgb = 0xBDFFFFFFL,
        borderWidthDp = 1.5f,
        cornerRadiusDp = 28f,
        windowBlurRadiusPx = 74,
        inAppBlurRadiusDp = 22f,
        dimAmount = 0.50f,
        glowColorArgb = 0x00000000L,
        glowStrength = 0.0f,
        highlightStrength = 0.48f,
        noiseStrength = 0.045f,
        shadowLayers = 1,
        isProgressive = false,
        usesSystemDynamicColor = false,
        minApiForFullEffect = 31,
        freeTier = false
    )

    val BLUR = DeckThemeSnapshot(
        styleId = "blur",
        displayName = "Dark Blur",
        panelColorArgb = 0xFF090E1AL,
        tintArgb = 0xFF64748BL,
        panelAlpha = 0.76f,
        borderColorArgb = 0x00000000L,
        borderWidthDp = 0.0f,
        cornerRadiusDp = 28f,
        windowBlurRadiusPx = 115,
        inAppBlurRadiusDp = 34f,
        dimAmount = 0.68f,
        glowColorArgb = 0x00000000L,
        glowStrength = 0.0f,
        highlightStrength = 0.14f,
        noiseStrength = 0.0f,
        shadowLayers = 1,
        isProgressive = false,
        usesSystemDynamicColor = false,
        minApiForFullEffect = 31,
        freeTier = false
    )

    val PROGRESSIVE = DeckThemeSnapshot(
        styleId = "progressive",
        displayName = "Progressive Blur",
        panelColorArgb = 0xFF0C1626L,
        tintArgb = 0xFF38BDF8L,
        panelAlpha = 0.78f,
        borderColorArgb = 0x805EEAD4L,
        borderWidthDp = 1.2f,
        cornerRadiusDp = 28f,
        windowBlurRadiusPx = 88,
        inAppBlurRadiusDp = 26f,
        dimAmount = 0.58f,
        glowColorArgb = 0xFF818CF8L,
        glowStrength = 0.38f,
        highlightStrength = 0.55f,
        noiseStrength = 0.0f,
        shadowLayers = 1,
        isProgressive = true,
        progressiveStartBlurPx = 10,
        progressiveEndBlurPx = 105,
        usesSystemDynamicColor = false,
        minApiForFullEffect = 33,
        freeTier = false
    )

    val GLASSMORPHISM = DeckThemeSnapshot(
        styleId = "glassmorphism",
        displayName = "Glassmorphism",
        panelColorArgb = 0xFF162032L,
        tintArgb = 0xFFFFFFFFL,
        panelAlpha = 0.44f,
        borderColorArgb = 0xD9FFFFFFL,
        borderWidthDp = 1.1f,
        cornerRadiusDp = 24f,
        windowBlurRadiusPx = 52,
        inAppBlurRadiusDp = 16f,
        dimAmount = 0.46f,
        glowColorArgb = 0x66A5F3FCL,
        glowStrength = 0.28f,
        highlightStrength = 0.72f,
        noiseStrength = 0.0f,
        shadowLayers = 1,
        isProgressive = false,
        usesSystemDynamicColor = false,
        minApiForFullEffect = 31,
        freeTier = false
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
        panelColorArgb = 0xFF171924L,
        tintArgb = 0xFFD8D4F2L,
        panelAlpha = 0.66f,
        borderColorArgb = 0x00000000L,
        borderWidthDp = 0.0f,
        cornerRadiusDp = 32f,
        windowBlurRadiusPx = 92,
        inAppBlurRadiusDp = 26f,
        dimAmount = 0.52f,
        glowColorArgb = 0x00000000L,
        glowStrength = 0.0f,
        highlightStrength = 0.20f,
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
        panelColorArgb = 0xFF04060AL,
        tintArgb = 0xFF455A64L,
        panelAlpha = 0.94f,
        borderColorArgb = 0x4D38BDF8L,
        borderWidthDp = 1.1f,
        cornerRadiusDp = 22f,
        windowBlurRadiusPx = 28,
        inAppBlurRadiusDp = 10f,
        dimAmount = 0.78f,
        glowColorArgb = 0x00000000L,
        glowStrength = 0.0f,
        highlightStrength = 0.22f,
        noiseStrength = 0.0f,
        shadowLayers = 1,
        isProgressive = false,
        usesSystemDynamicColor = false,
        minApiForFullEffect = 31,
        freeTier = false
    )

    val ALL: List<DeckThemeSnapshot> = listOf(
        MATERIAL_3,
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
        return ALL.find { it.styleId == styleId } ?: MATERIAL_3
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
) {
    val glassFillArgb: Int get() = panelInnerArgb
    val glassBorderArgb: Int get() = borderArgb
}

private val FALLBACK_CHROMATIC_ACCENT = 0xFF38BDF8.toInt()
private val FALLBACK_CHROMATIC_SECONDARY = 0xFFC084FC.toInt()

/**
 * Ensures an ARGB accent is chromatic and bright enough to contrast sharply against dark backdrops
 * and white labels (preventing Android Go neutral Monet or white Prism tints from washing out focus).
 */
fun ensureChromaticAccentColor(argb: Int, fallbackArgb: Int = FALLBACK_CHROMATIC_ACCENT): Int {
    val hsv = FloatArray(3)
    android.graphics.Color.colorToHSV(argb or (0xFF shl 24), hsv)
    if (hsv[1] < 0.22f) {
        android.graphics.Color.colorToHSV(fallbackArgb or (0xFF shl 24), hsv)
        if (hsv[1] < 0.22f) return FALLBACK_CHROMATIC_ACCENT
    }
    hsv[1] = hsv[1].coerceAtLeast(0.42f)
    hsv[2] = hsv[2].coerceIn(0.76f, 0.98f)
    return android.graphics.Color.HSVToColor(0xFF, hsv)
}

/**
 * Resolves a vivid wallpaper/Monet accent pair with zero storage permissions.
 * Falls back from system Monet tokens (S+) -> WallpaperManager.getWallpaperColors (API 27+,
 * which recovers true wallpaper colors on Android Go builds where Monet resources stay neutral)
 * -> chromatic 12-theme tint -> luminous cyan fallback.
 */
internal fun resolveVibrantDynamicAccentPair(
    context: android.content.Context?,
    themeTintArgb: Long
): Pair<Int, Int> {
    val hsv = FloatArray(3)
    var primary: Int? = null
    var secondary: Int? = null

    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S && context != null) {
        val accentTokens = intArrayOf(
            android.R.color.system_accent1_300,
            android.R.color.system_accent1_400,
            android.R.color.system_accent1_500,
            android.R.color.system_accent1_200
        )
        for (resId in accentTokens) {
            try {
                val c = context.getColor(resId) or (0xFF shl 24)
                android.graphics.Color.colorToHSV(c, hsv)
                if (hsv[1] >= 0.22f) {
                    hsv[2] = hsv[2].coerceIn(0.76f, 0.98f)
                    primary = android.graphics.Color.HSVToColor(0xFF, hsv)
                    break
                }
            } catch (_: Exception) {}
        }
        val secTokens = intArrayOf(
            android.R.color.system_accent3_300,
            android.R.color.system_accent3_400,
            android.R.color.system_accent2_300
        )
        for (resId in secTokens) {
            try {
                val c = context.getColor(resId) or (0xFF shl 24)
                android.graphics.Color.colorToHSV(c, hsv)
                if (hsv[1] >= 0.20f) {
                    hsv[2] = hsv[2].coerceIn(0.74f, 0.98f)
                    secondary = android.graphics.Color.HSVToColor(0xFF, hsv)
                    break
                }
            } catch (_: Exception) {}
        }
    }

    if ((primary == null || secondary == null) &&
        android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O_MR1 &&
        context != null
    ) {
        try {
            val wm = android.app.WallpaperManager.getInstance(context)
            val wpColors = wm?.getWallpaperColors(android.app.WallpaperManager.FLAG_SYSTEM)
            if (wpColors != null) {
                val candidates = listOfNotNull(
                    wpColors.primaryColor?.toArgb(),
                    wpColors.secondaryColor?.toArgb(),
                    wpColors.tertiaryColor?.toArgb()
                )
                for (raw in candidates) {
                    val opaque = raw or (0xFF shl 24)
                    android.graphics.Color.colorToHSV(opaque, hsv)
                    if (hsv[1] >= 0.20f) {
                        hsv[1] = hsv[1].coerceAtLeast(0.45f)
                        hsv[2] = hsv[2].coerceIn(0.76f, 0.98f)
                        val boosted = android.graphics.Color.HSVToColor(0xFF, hsv)
                        if (primary == null) {
                            primary = boosted
                        } else if (secondary == null && boosted != primary) {
                            secondary = boosted
                        }
                    }
                }
            }
        } catch (_: Exception) {}
    }

    val themeOpaque = (themeTintArgb.toInt() and 0x00FFFFFF) or (0xFF shl 24)
    val resolvedPrimary = primary ?: ensureChromaticAccentColor(themeOpaque, FALLBACK_CHROMATIC_ACCENT)
    val resolvedSecondary = secondary ?: ensureChromaticAccentColor(resolvedPrimary, FALLBACK_CHROMATIC_SECONDARY)
    return resolvedPrimary to resolvedSecondary
}

fun DeckThemeSnapshot.resolveCanvasPalette(
    context: android.content.Context? = null,
    colorMode: String = "theme_12"
): CanvasThemePalette {
    val isSPlus = android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S && context != null

    // Full Material 3 Dynamic Wallpaper Mode ("m3_dynamic")
    if (colorMode == "m3_dynamic") {
        val (p, s) = resolveVibrantDynamicAccentPair(context, effectiveTintArgb)
        if (isSPlus) {
            try {
                val bgIn = context.getColor(android.R.color.system_neutral1_800)
                val bgOut = context.getColor(android.R.color.system_neutral1_900)
                val bdrRaw = context.getColor(android.R.color.system_accent1_200)
                val bdrHsv = FloatArray(3)
                android.graphics.Color.colorToHSV(bdrRaw or (0xFF shl 24), bdrHsv)
                val bdr = if (bdrHsv[1] >= 0.18f) (bdrRaw or (0xFF shl 24)) else p
                return CanvasThemePalette(
                    panelInnerArgb = (bgIn and 0x00FFFFFF) or (0xE4 shl 24),
                    panelOuterArgb = (bgOut and 0x00FFFFFF) or (0xF0 shl 24),
                    primaryAccentArgb = p,
                    secondaryAccentArgb = s,
                    borderArgb = bdr
                )
            } catch (_: Exception) {}
        }
        return CanvasThemePalette(
            panelInnerArgb = 0xE41A2234.toInt(),
            panelOuterArgb = 0xF0101522.toInt(),
            primaryAccentArgb = p,
            secondaryAccentArgb = s,
            borderArgb = p
        )
    }

    if ((styleId == "adaptive" || styleId == "material3") && usesSystemDynamicColor && context != null) {
        val (p, s) = resolveVibrantDynamicAccentPair(context, effectiveTintArgb)
        if (isSPlus) {
            try {
                val bgIn = context.getColor(android.R.color.system_neutral1_800)
                val bgOut = context.getColor(android.R.color.system_neutral1_900)
                return CanvasThemePalette(
                    panelInnerArgb = (bgIn and 0x00FFFFFF) or (0xE0 shl 24),
                    panelOuterArgb = (bgOut and 0x00FFFFFF) or (0xEE shl 24),
                    primaryAccentArgb = p,
                    secondaryAccentArgb = s,
                    borderArgb = p
                )
            } catch (_: Exception) {}
        }
        return CanvasThemePalette(
            panelInnerArgb = 0xE01A2234.toInt(),
            panelOuterArgb = 0xEE101522.toInt(),
            primaryAccentArgb = p,
            secondaryAccentArgb = s,
            borderArgb = p
        )
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
        "material3" -> 0.14f
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

    val basePalette = CanvasThemePalette(
        panelInnerArgb = panelInner,
        panelOuterArgb = panelOuter,
        primaryAccentArgb = primaryAccent,
        secondaryAccentArgb = secondaryAccent,
        borderArgb = resolvedBorder
    )

    // Hybrid Fusion Mode ("hybrid_fusion"): Genius Merge of 12-Theme Optical Glass + M3 Dynamic Neon Seams
    if (colorMode == "hybrid_fusion") {
        val (m3Accent, m3Tertiary) = resolveVibrantDynamicAccentPair(context, effectiveTintArgb)
        val m3R = (m3Accent shr 16) and 0xFF
        val m3G = (m3Accent shr 8) and 0xFF
        val m3B = m3Accent and 0xFF

        val fusedInR = (inR * 0.78f + m3R * 0.22f).toInt().coerceIn(0, 255)
        val fusedInG = (inG * 0.78f + m3G * 0.22f).toInt().coerceIn(0, 255)
        val fusedInB = (inB * 0.78f + m3B * 0.22f).toInt().coerceIn(0, 255)
        val fusedInner = (inAlpha shl 24) or (fusedInR shl 16) or (fusedInG shl 8) or fusedInB

        val bR = (resolvedBorder shr 16) and 0xFF
        val bG = (resolvedBorder shr 8) and 0xFF
        val bB = resolvedBorder and 0xFF
        val fusedBorderR = (bR * 0.40f + m3R * 0.60f).toInt().coerceIn(0, 255)
        val fusedBorderG = (bG * 0.40f + m3G * 0.60f).toInt().coerceIn(0, 255)
        val fusedBorderB = (bB * 0.40f + m3B * 0.60f).toInt().coerceIn(0, 255)
        val fusedBorder = (0xFF shl 24) or (fusedBorderR shl 16) or (fusedBorderG shl 8) or fusedBorderB

        return CanvasThemePalette(
            panelInnerArgb = fusedInner,
            panelOuterArgb = panelOuter,
            primaryAccentArgb = m3Accent,
            secondaryAccentArgb = if (primaryAccent != 0xFFFFFFFF.toInt()) primaryAccent else m3Tertiary,
            borderArgb = fusedBorder
        )
    }

    return basePalette
}
