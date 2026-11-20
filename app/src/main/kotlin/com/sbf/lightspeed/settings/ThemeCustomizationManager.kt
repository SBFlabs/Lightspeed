package com.sbf.lightspeed.settings

import android.content.Context
import com.sbf.lightspeed.system.defaultPrefs
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Manages persisted user adjustments to each of the 12 Flight Control Deck styles.
 * Parameters:
 * - blurRadius: Window / background blur radius in px
 * - opacity: Surface substrate alpha (0.05 to 1.0)
 * - cornerRadius: Pill / card corner radius in dp
 * - borderWidth: Outer rim border stroke width in dp
 * - glowStrength: Ambient neon / bloom halo intensity (0.0 to 1.0)
 * - highlightStrength: Specular gleam & gloss intensity (0.0 to 1.0)
 * - customTint: Optional custom ARGB hex color override
 */
object ThemeCustomizationManager {

    data class ThemeCustomization(
        val styleId: String,
        val blurRadius: Int,
        val opacity: Float,
        val backdropOpacity: Float,
        val cornerRadius: Float,
        val borderWidth: Float,
        val glowStrength: Float,
        val highlightStrength: Float,
        val customTintArgb: Long?
    ) {
        fun toSnapshot(base: DeckThemeSnapshot): DeckThemeSnapshot {
            val effectiveTint = if (base.styleId == DeckStylePresets.ADAPTIVE.styleId) base.tintArgb else (customTintArgb ?: base.tintArgb)
            val effectiveGlow = if (customTintArgb != null && base.styleId in setOf("jelly", "depth", "neon", "prism", "cyberpunk", "hologram")) {
                customTintArgb
            } else base.glowColorArgb

            val effectiveBorder = if (customTintArgb != null && base.styleId in setOf("jelly", "depth")) {
                val r = (customTintArgb shr 16 and 0xFF).toInt()
                val g = (customTintArgb shr 8 and 0xFF).toInt()
                val b = (customTintArgb and 0xFF).toInt()
                val brightR = (r * 0.65f + 255 * 0.35f).toInt().coerceIn(0, 255)
                val brightG = (g * 0.65f + 255 * 0.35f).toInt().coerceIn(0, 255)
                val brightB = (b * 0.65f + 255 * 0.35f).toInt().coerceIn(0, 255)
                (0xB3L shl 24) or (brightR.toLong() shl 16) or (brightG.toLong() shl 8) or brightB.toLong()
            } else base.borderColorArgb

            val effectivePanel = if (customTintArgb != null && base.styleId in setOf("jelly", "depth")) {
                val r = (customTintArgb shr 16 and 0xFF).toInt()
                val g = (customTintArgb shr 8 and 0xFF).toInt()
                val b = (customTintArgb and 0xFF).toInt()
                val baseR = (14 * 0.85f + r * 0.15f).toInt().coerceIn(0, 255)
                val baseG = (16 * 0.85f + g * 0.15f).toInt().coerceIn(0, 255)
                val baseB = (24 * 0.85f + b * 0.15f).toInt().coerceIn(0, 255)
                (0xFFL shl 24) or (baseR.toLong() shl 16) or (baseG.toLong() shl 8) or baseB.toLong()
            } else base.panelColorArgb

            return base.copy(
                windowBlurRadiusPx = blurRadius,
                panelAlpha = opacity,
                dimAmount = backdropOpacity,
                cornerRadiusDp = cornerRadius,
                borderWidthDp = borderWidth,
                glowStrength = glowStrength,
                highlightStrength = highlightStrength,
                tintArgb = effectiveTint,
                glowColorArgb = effectiveGlow,
                borderColorArgb = effectiveBorder,
                panelColorArgb = effectivePanel
            )
        }

        fun toParamMap(): Map<String, Float> = mapOf(
            "blur" to blurRadius.toFloat(),
            "opacity" to opacity,
            "backdrop_opacity" to backdropOpacity,
            "corner" to cornerRadius,
            "border" to borderWidth,
            "glow" to glowStrength,
            "highlight" to highlightStrength,
            "tint" to (customTintArgb?.toFloat() ?: -1f)
        )

        companion object {
            fun fromSnapshot(snapshot: DeckThemeSnapshot): ThemeCustomization {
                return ThemeCustomization(
                    styleId = snapshot.styleId,
                    blurRadius = snapshot.windowBlurRadiusPx,
                    opacity = snapshot.panelAlpha,
                    backdropOpacity = snapshot.dimAmount,
                    cornerRadius = snapshot.cornerRadiusDp,
                    borderWidth = snapshot.borderWidthDp,
                    glowStrength = snapshot.glowStrength,
                    highlightStrength = snapshot.highlightStrength,
                    customTintArgb = null
                )
            }

            fun fromParamMap(styleId: String, map: Map<String, Float>, base: DeckThemeSnapshot): ThemeCustomization {
                val tintVal = map["tint"]?.toLong()
                return ThemeCustomization(
                    styleId = styleId,
                    blurRadius = map["blur"]?.toInt() ?: base.windowBlurRadiusPx,
                    opacity = map["opacity"] ?: base.panelAlpha,
                    backdropOpacity = map["backdrop_opacity"] ?: base.dimAmount,
                    cornerRadius = map["corner"] ?: base.cornerRadiusDp,
                    borderWidth = map["border"] ?: base.borderWidthDp,
                    glowStrength = map["glow"] ?: base.glowStrength,
                    highlightStrength = map["highlight"] ?: base.highlightStrength,
                    customTintArgb = if (tintVal != null && tintVal >= 0) tintVal else null
                )
            }
        }
    }

    private val _customizationsFlow = MutableStateFlow<Map<String, ThemeCustomization>>(emptyMap())
    val customizationsFlow: StateFlow<Map<String, ThemeCustomization>> = _customizationsFlow

    private fun prefix(styleId: String) = "pref_deck_theme_custom_$styleId"

    fun getCustomization(context: Context, styleId: String): ThemeCustomization {
        val base = DeckStylePresets.resolve(styleId)
        val prefs = context.defaultPrefs()
        val p = prefix(styleId)
        if (!prefs.contains("${p}_blur") && !prefs.contains("${p}_backdrop_opacity")) {
            return ThemeCustomization.fromSnapshot(base)
        }
        val blur = prefs.getInt("${p}_blur", base.windowBlurRadiusPx)
        val opacity = prefs.getFloat("${p}_opacity", base.panelAlpha)
        val backdropOpacity = prefs.getFloat("${p}_backdrop_opacity", base.dimAmount)
        val corner = prefs.getFloat("${p}_corner", base.cornerRadiusDp)
        val border = prefs.getFloat("${p}_border", base.borderWidthDp)
        val glow = prefs.getFloat("${p}_glow", base.glowStrength)
        val highlight = prefs.getFloat("${p}_highlight", base.highlightStrength)
        val tint = if (prefs.contains("${p}_tint")) prefs.getLong("${p}_tint", base.tintArgb) else null

        return ThemeCustomization(
            styleId = styleId,
            blurRadius = blur,
            opacity = opacity,
            backdropOpacity = backdropOpacity,
            cornerRadius = corner,
            borderWidth = border,
            glowStrength = glow,
            highlightStrength = highlight,
            customTintArgb = tint
        )
    }

    fun saveCustomization(context: Context, custom: ThemeCustomization) {
        val p = prefix(custom.styleId)
        val editor = context.defaultPrefs().edit()
            .putInt("${p}_blur", custom.blurRadius)
            .putFloat("${p}_opacity", custom.opacity)
            .putFloat("${p}_backdrop_opacity", custom.backdropOpacity)
            .putFloat("${p}_corner", custom.cornerRadius)
            .putFloat("${p}_border", custom.borderWidth)
            .putFloat("${p}_glow", custom.glowStrength)
            .putFloat("${p}_highlight", custom.highlightStrength)

        if (custom.customTintArgb != null) {
            editor.putLong("${p}_tint", custom.customTintArgb)
        } else {
            editor.remove("${p}_tint")
        }
        editor.apply()

        val updated = _customizationsFlow.value.toMutableMap()
        updated[custom.styleId] = custom
        _customizationsFlow.value = updated

        // Force DeckThemeManager snapshot refresh
        DeckThemeManager.refresh(context)
    }

    fun resetToDefaults(context: Context, styleId: String) {
        val p = prefix(styleId)
        context.defaultPrefs().edit()
            .remove("${p}_blur")
            .remove("${p}_opacity")
            .remove("${p}_backdrop_opacity")
            .remove("${p}_corner")
            .remove("${p}_border")
            .remove("${p}_glow")
            .remove("${p}_highlight")
            .remove("${p}_tint")
            .apply()

        val updated = _customizationsFlow.value.toMutableMap()
        updated.remove(styleId)
        _customizationsFlow.value = updated

        DeckThemeManager.refresh(context)
    }

    fun resetAllToDefaults(context: Context) {
        val prefs = context.defaultPrefs()
        val editor = prefs.edit()
        for (preset in DeckStylePresets.ALL) {
            val p = prefix(preset.styleId)
            editor.remove("${p}_blur")
                .remove("${p}_opacity")
                .remove("${p}_corner")
                .remove("${p}_border")
                .remove("${p}_glow")
                .remove("${p}_highlight")
                .remove("${p}_tint")
        }
        editor.apply()
        _customizationsFlow.value = emptyMap()
        DeckThemeManager.refresh(context)
    }
}
