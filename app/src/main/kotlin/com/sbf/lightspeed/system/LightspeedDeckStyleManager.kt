package com.sbf.lightspeed.system

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

object LightspeedDeckStyleManager {

    private val _deckGlassStyleFlow = MutableStateFlow<String?>(null)
    val deckGlassStyleFlow: StateFlow<String?> = _deckGlassStyleFlow

    fun getDeckGlassStyle(context: Context): String {
        val cached = _deckGlassStyleFlow.value
        if (cached != null) {
            if (cached == "obsidian" && !LightspeedInfinityManager.isUnlocked(context)) {
                return "liquid"
            }
            return cached
        }
        val style = context.defaultPrefs().getString(LightspeedPreferences.KEY_DECK_GLASS_STYLE, "liquid") ?: "liquid"
        val effective = if (style == "obsidian" && !LightspeedInfinityManager.isUnlocked(context)) "liquid" else style
        _deckGlassStyleFlow.value = effective
        return effective
    }

    fun setDeckGlassStyle(context: Context, style: String) {
        val effective = if (style == "obsidian" && !LightspeedInfinityManager.isUnlocked(context)) "liquid" else style
        context.defaultPrefs().edit().putString(LightspeedPreferences.KEY_DECK_GLASS_STYLE, effective).apply()
        _deckGlassStyleFlow.value = effective
    }

    fun refreshDeckGlassStyle(context: Context) {
        val style = context.defaultPrefs().getString(LightspeedPreferences.KEY_DECK_GLASS_STYLE, "liquid") ?: "liquid"
        val effective = if (style == "obsidian" && !LightspeedInfinityManager.isUnlocked(context)) "liquid" else style
        _deckGlassStyleFlow.value = effective
    }

    private val _deckBackdropStyleFlow = MutableStateFlow<String?>(null)
    val deckBackdropStyleFlow: StateFlow<String?> = _deckBackdropStyleFlow

    fun getDeckBackdropStyle(context: Context): String {
        val cached = _deckBackdropStyleFlow.value
        if (cached != null) return cached
        val style = context.defaultPrefs().getString(LightspeedPreferences.KEY_DECK_BACKDROP_STYLE, "cosmic") ?: "cosmic"
        _deckBackdropStyleFlow.value = style
        return style
    }

    fun setDeckBackdropStyle(context: Context, style: String) {
        context.defaultPrefs().edit().putString(LightspeedPreferences.KEY_DECK_BACKDROP_STYLE, style).apply()
        _deckBackdropStyleFlow.value = style
    }

    fun refreshDeckBackdropStyle(context: Context) {
        val style = context.defaultPrefs().getString(LightspeedPreferences.KEY_DECK_BACKDROP_STYLE, "cosmic") ?: "cosmic"
        _deckBackdropStyleFlow.value = style
    }

    data class LiquidGlassConfig(
        val blurRadius: Int = 75,
        val opacity: Float = 0.85f,
        val frostNoise: Float = 0.05f,
        val glareIntensity: Float = 0.65f,
        val rimIntensity: Float = 0.70f,
        val causticShimmer: Boolean = true,
        val progressiveDepth: Boolean = true
    ) {
        companion object {
            val PRESET_DEFAULT = LiquidGlassConfig(
                blurRadius = 75,
                opacity = 0.85f,
                frostNoise = 0.05f,
                glareIntensity = 0.65f,
                rimIntensity = 0.70f,
                causticShimmer = true,
                progressiveDepth = true
            )
            val PRESET_CRYSTAL = LiquidGlassConfig(
                blurRadius = 95,
                opacity = 0.22f,
                frostNoise = 0.01f,
                glareIntensity = 0.85f,
                rimIntensity = 0.80f,
                causticShimmer = true,
                progressiveDepth = true
            )
            val PRESET_FROSTED = LiquidGlassConfig(
                blurRadius = 60,
                opacity = 0.60f,
                frostNoise = 0.12f,
                glareIntensity = 0.40f,
                rimIntensity = 0.45f,
                causticShimmer = false,
                progressiveDepth = true
            )
            val PRESET_OBSIDIAN = LiquidGlassConfig(
                blurRadius = 45,
                opacity = 0.82f,
                frostNoise = 0.02f,
                glareIntensity = 0.30f,
                rimIntensity = 0.35f,
                causticShimmer = false,
                progressiveDepth = false
            )
        }
    }

    private val _liquidGlassConfigFlow = MutableStateFlow<LiquidGlassConfig?>(null)
    val liquidGlassConfigFlow: StateFlow<LiquidGlassConfig?> = _liquidGlassConfigFlow

    fun getLiquidGlassConfig(context: Context): LiquidGlassConfig {
        val cached = _liquidGlassConfigFlow.value
        if (cached != null) return cached
        val prefs = context.defaultPrefs()
        val config = LiquidGlassConfig(
            blurRadius = prefs.getInt(LightspeedPreferences.KEY_LIQUID_GLASS_BLUR, 75),
            opacity = prefs.getFloat(LightspeedPreferences.KEY_LIQUID_GLASS_OPACITY, 0.85f),
            frostNoise = prefs.getFloat(LightspeedPreferences.KEY_LIQUID_GLASS_FROST, 0.05f),
            glareIntensity = prefs.getFloat(LightspeedPreferences.KEY_LIQUID_GLASS_GLARE, 0.65f),
            rimIntensity = prefs.getFloat(LightspeedPreferences.KEY_LIQUID_GLASS_RIM, 0.70f),
            causticShimmer = prefs.getBoolean(LightspeedPreferences.KEY_LIQUID_GLASS_CAUSTIC, true),
            progressiveDepth = prefs.getBoolean(LightspeedPreferences.KEY_LIQUID_GLASS_PROGRESSIVE, true)
        )
        _liquidGlassConfigFlow.value = config
        return config
    }

    fun setLiquidGlassConfig(context: Context, config: LiquidGlassConfig) {
        context.defaultPrefs().edit()
            .putInt(LightspeedPreferences.KEY_LIQUID_GLASS_BLUR, config.blurRadius)
            .putFloat(LightspeedPreferences.KEY_LIQUID_GLASS_OPACITY, config.opacity)
            .putFloat(LightspeedPreferences.KEY_LIQUID_GLASS_FROST, config.frostNoise)
            .putFloat(LightspeedPreferences.KEY_LIQUID_GLASS_GLARE, config.glareIntensity)
            .putFloat(LightspeedPreferences.KEY_LIQUID_GLASS_RIM, config.rimIntensity)
            .putBoolean(LightspeedPreferences.KEY_LIQUID_GLASS_CAUSTIC, config.causticShimmer)
            .putBoolean(LightspeedPreferences.KEY_LIQUID_GLASS_PROGRESSIVE, config.progressiveDepth)
            .apply()
        _liquidGlassConfigFlow.value = config
    }

    fun resetLiquidGlassConfig(context: Context) {
        setLiquidGlassConfig(context, LiquidGlassConfig.PRESET_DEFAULT)
    }

    fun refreshLiquidGlassConfig(context: Context) {
        _liquidGlassConfigFlow.value = null
        getLiquidGlassConfig(context)
    }
}
