package com.sbf.lightspeed.system

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

typealias LiquidGlassConfig = LightspeedDeckStyleManager.LiquidGlassConfig

/**
 * Reactive StateFlow state holders and styling accessors for Theme mode,
 * Deck Glass styles, Deck Backdrop styles, and Liquid Glass optics.
 */
object LightspeedPreferencesFlows : LightspeedPreferencesFlowsContract {

    private val _themeModeFlow = MutableStateFlow<String?>(null)
    override val themeModeFlow: StateFlow<String?> get() = _themeModeFlow

    fun sanitizeThemeMode(mode: String?): String = when (mode) {
        "light" -> "light"
        "dark" -> "dark"
        else -> "auto"
    }

    override fun getThemeMode(context: Context): String {
        val cached = _themeModeFlow.value
        if (cached != null) return cached
        val mode = sanitizeThemeMode(context.defaultPrefs().getString(LightspeedPreferences.KEY_THEME_MODE, "auto"))
        _themeModeFlow.value = mode
        return mode
    }

    override fun setThemeMode(context: Context, mode: String) {
        if (mode != "auto" && mode != "light" && mode != "dark") return
        context.defaultPrefs().edit().putString(LightspeedPreferences.KEY_THEME_MODE, mode).apply()
        _themeModeFlow.value = mode
    }

    override fun refreshThemeMode(context: Context) {
        _themeModeFlow.value = sanitizeThemeMode(context.defaultPrefs().getString(LightspeedPreferences.KEY_THEME_MODE, "auto"))
    }

    override val deckGlassStyleFlow: StateFlow<String?> get() = LightspeedDeckStyleManager.deckGlassStyleFlow
    override fun getDeckGlassStyle(context: Context): String = LightspeedDeckStyleManager.getDeckGlassStyle(context)
    override fun setDeckGlassStyle(context: Context, style: String) = LightspeedDeckStyleManager.setDeckGlassStyle(context, style)
    override fun refreshDeckGlassStyle(context: Context) = LightspeedDeckStyleManager.refreshDeckGlassStyle(context)

    override val deckBackdropStyleFlow: StateFlow<String?> get() = LightspeedDeckStyleManager.deckBackdropStyleFlow
    override fun getDeckBackdropStyle(context: Context): String = LightspeedDeckStyleManager.getDeckBackdropStyle(context)
    override fun setDeckBackdropStyle(context: Context, style: String) = LightspeedDeckStyleManager.setDeckBackdropStyle(context, style)
    override fun refreshDeckBackdropStyle(context: Context) = LightspeedDeckStyleManager.refreshDeckBackdropStyle(context)

    override val liquidGlassConfigFlow: StateFlow<LiquidGlassConfig?> get() = LightspeedDeckStyleManager.liquidGlassConfigFlow
    override fun getLiquidGlassConfig(context: Context): LiquidGlassConfig = LightspeedDeckStyleManager.getLiquidGlassConfig(context)
    override fun setLiquidGlassConfig(context: Context, config: LiquidGlassConfig) = LightspeedDeckStyleManager.setLiquidGlassConfig(context, config)
    override fun refreshLiquidGlassConfig(context: Context) = LightspeedDeckStyleManager.refreshLiquidGlassConfig(context)
}
