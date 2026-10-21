package com.sbf.lightspeed.settings

import android.content.Context
import com.sbf.lightspeed.system.LightspeedDeckStyleManager
import com.sbf.lightspeed.system.LightspeedPreferences
import com.sbf.lightspeed.system.defaultPrefs
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Single source of truth for the selected Flight Control Deck style (pref_deck_style).
 *
 * Not used by any panel yet. When pref_deck_style is missing (fresh install, existing install,
 * or an old backup without the key) it is derived once from the legacy pref_deck_glass_style
 * and written back. Legacy keys are never removed, so rollback stays possible.
 *
 * Legacy backdrop style (pref_deck_backdrop_style) is intentionally NOT part of the snapshot yet.
 * Planned mapping: cosmic/void/frost_veil/clear stay a separate backdrop layer read through
 * LightspeedDeckStyleManager until panels adopt the snapshot.
 */
object DeckThemeManager {

    private const val LEGACY_LIQUID = "liquid"
    private const val LEGACY_FROST = "frost"
    private const val LEGACY_OBSIDIAN = "obsidian"

    private val _styleIdFlow = MutableStateFlow(DeckStylePresets.PRISM.styleId)
    private val _snapshotFlow = MutableStateFlow(DeckStylePresets.PRISM)
    private var loaded = false
    private var previewStyleId: String? = null

    /** Current style id (a DeckStylePresets id). */
    val styleIdFlow: StateFlow<String> = _styleIdFlow

    /** Current snapshot; for Prism it carries the owner's Prism calibration values. */
    val snapshotFlow: StateFlow<DeckThemeSnapshot> = _snapshotFlow

    /** Legacy glass-style value to style id. Anything unknown becomes prism. */
    fun deriveFromLegacy(legacyGlassStyle: String?): String = when (legacyGlassStyle) {
        LEGACY_FROST -> DeckStylePresets.FROSTED.styleId
        LEGACY_OBSIDIAN -> DeckStylePresets.DARK.styleId
        else -> DeckStylePresets.PRISM.styleId // "liquid" and anything else
    }

    /** Returns the current style id, deriving and persisting it from the legacy key if missing. */
    @Synchronized
    fun getStyleId(context: Context): String {
        if (!loaded) refresh(context)
        return _styleIdFlow.value
    }

    /** Returns the persistent style id saved in preferences (ignoring any in-memory preview override). */
    @Synchronized
    fun getCommittedStyleId(context: Context): String {
        if (!loaded) refresh(context)
        val prefs = context.defaultPrefs()
        val stored = prefs.getString(LightspeedPreferences.KEY_DECK_STYLE, null)
        val knownIds = DeckStylePresets.ALL.map { it.styleId }
        return if (stored != null && stored in knownIds) {
            stored
        } else {
            deriveFromLegacy(prefs.getString(LightspeedPreferences.KEY_DECK_GLASS_STYLE, null))
        }
    }

    /** Current snapshot (see [getStyleId] for lazy loading). */
    @Synchronized
    fun getSnapshot(context: Context): DeckThemeSnapshot {
        if (!loaded) refresh(context)
        return _snapshotFlow.value
    }

    /** Re-reads preferences (call after a backup import or reset). Runs the lazy derivation if needed. */
    @Synchronized
    fun refresh(context: Context) {
        previewStyleId = null
        val prefs = context.defaultPrefs()
        val stored = prefs.getString(LightspeedPreferences.KEY_DECK_STYLE, null)
        val knownIds = DeckStylePresets.ALL.map { it.styleId }
        val id = if (stored != null && stored in knownIds) {
            stored
        } else {
            val derived = deriveFromLegacy(prefs.getString(LightspeedPreferences.KEY_DECK_GLASS_STYLE, null))
            prefs.edit().putString(LightspeedPreferences.KEY_DECK_STYLE, derived).apply()
            derived
        }
        publish(context, id)
        loaded = true
    }

    /**
     * Temporarily sets an in-memory preview style without writing to persistent preferences.
     * Updates [styleIdFlow] and [snapshotFlow] so any active surface immediately renders the preview.
     */
    @Synchronized
    fun setPreviewOverride(context: Context, styleId: String) {
        if (DeckStylePresets.ALL.none { it.styleId == styleId }) return
        if (!loaded) refresh(context)
        previewStyleId = styleId
        publish(context, styleId)
    }

    /**
     * Clears the in-memory preview override and reverts [styleIdFlow] and [snapshotFlow]
     * to the committed preference state.
     */
    @Synchronized
    fun clearPreviewOverride(context: Context) {
        if (previewStyleId == null) return
        previewStyleId = null
        val id = getCommittedStyleId(context)
        publish(context, id)
    }

    /**
     * Selects a style. Writes pref_deck_style; for prism, frosted and dark it also writes the
     * matching legacy glass-style value so the old UI stays consistent. Other styles leave
     * the legacy keys unchanged. Unknown ids are ignored.
     */
    @Synchronized
    fun setStyle(context: Context, styleId: String) {
        if (DeckStylePresets.ALL.none { it.styleId == styleId }) return
        previewStyleId = null
        context.defaultPrefs().edit().putString(LightspeedPreferences.KEY_DECK_STYLE, styleId).apply()
        when (styleId) {
            DeckStylePresets.PRISM.styleId -> LightspeedDeckStyleManager.setDeckGlassStyle(context, LEGACY_LIQUID)
            DeckStylePresets.FROSTED.styleId -> LightspeedDeckStyleManager.setDeckGlassStyle(context, LEGACY_FROST)
            DeckStylePresets.DARK.styleId -> LightspeedDeckStyleManager.setDeckGlassStyle(context, LEGACY_OBSIDIAN)
        }
        publish(context, styleId)
        loaded = true
    }

    private fun publish(context: Context, id: String) {
        val basePreset = DeckStylePresets.resolve(id)
        _styleIdFlow.value = basePreset.styleId
        val custom = ThemeCustomizationManager.getCustomization(context, basePreset.styleId)
        val snapshotWithCustom = custom.toSnapshot(basePreset)
        _snapshotFlow.value = if (basePreset.styleId == DeckStylePresets.PRISM.styleId) {
            mergePrismCalibration(context, snapshotWithCustom)
        } else {
            snapshotWithCustom
        }
    }

    /**
     * Overlays the owner's current Prism calibration (legacy Prism settings) on the preset
     * only when no user customization is saved. When a user customization exists, panelAlpha,
     * windowBlurRadiusPx, progressiveEndBlurPx and tintArgb come from the saved customization.
     */
    private fun mergePrismCalibration(context: Context, preset: DeckThemeSnapshot): DeckThemeSnapshot {
        val hasCustomization = context.defaultPrefs().contains("pref_deck_theme_custom_${preset.styleId}_blur")
        if (hasCustomization) {
            return preset.copy(
                progressiveStartBlurPx = minOf(preset.progressiveStartBlurPx, preset.windowBlurRadiusPx),
                progressiveEndBlurPx = preset.windowBlurRadiusPx
            )
        }
        val cfg = LightspeedDeckStyleManager.getLiquidGlassConfig(context)
        return preset.copy(
            panelAlpha = cfg.opacity,
            windowBlurRadiusPx = cfg.blurRadius,
            progressiveStartBlurPx = minOf(preset.progressiveStartBlurPx, cfg.blurRadius),
            progressiveEndBlurPx = cfg.blurRadius,
            tintArgb = if (cfg.useCustomColor) cfg.customColor else preset.tintArgb
        )
    }
}
