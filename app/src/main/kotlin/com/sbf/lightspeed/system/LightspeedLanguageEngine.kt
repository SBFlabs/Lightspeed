package com.sbf.lightspeed.system

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * LightspeedLanguageEngine — the global terminology resolver for the Lightspeed app.
 *
 * Three modes control how every UI label is presented to the user:
 *
 *  ┌─────────────┬──────────────────────────────────────────────────────────┐
 *  │ VESSEL_LORE │ Full spaceship immersion. "Deflectors", "HUD Strip",     │
 *  │             │ "Orbital Capsule", "Horizon Rail", etc.                  │
 *  ├─────────────┼──────────────────────────────────────────────────────────┤
 *  │ CO_PILOT    │ Bilingual bridge. Vessel term as headline, plain-English  │
 *  │             │ subtitle so newcomers can orient themselves.              │
 *  ├─────────────┼──────────────────────────────────────────────────────────┤
 *  │ CLEAR_COMMS │ Plain Android language. "Gesture Sidebars", "Status Bar  │
 *  │             │ Strip", "Dynamic Island HUD", "Progress Rail", etc.      │
 *  └─────────────┴──────────────────────────────────────────────────────────┘
 *
 * In the future, additional real-language locales (Arabic, Chinese, …) are
 * intended to slot in here as additional LanguageMode values, each backed by
 * their own vocabulary table in LightspeedVocabulary.
 *
 * The engine is a singleton. Call [init] once from your Application or the
 * first Activity/Service that starts. After that, [resolve] is safe to call
 * from any thread or composable.
 */
object LightspeedLanguageEngine {

    // ─── Persistence key ────────────────────────────────────────────────────
    const val KEY_LANGUAGE_MODE = "pref_language_mode"

    // ─── Mode enum ──────────────────────────────────────────────────────────
    enum class LanguageMode(val prefValue: String) {
        VESSEL_LORE("vessel_lore"),
        CO_PILOT("co_pilot"),
        CLEAR_COMMS("clear_comms");

        companion object {
            fun fromPref(value: String?): LanguageMode =
                entries.firstOrNull { it.prefValue == value } ?: VESSEL_LORE
        }
    }

    // ─── Observable state ───────────────────────────────────────────────────
    private val _mode = MutableStateFlow(LanguageMode.VESSEL_LORE)
    val modeFlow: StateFlow<LanguageMode> get() = _mode

    val mode: LanguageMode get() = _mode.value

    // ─── Lifecycle ──────────────────────────────────────────────────────────
    fun init(context: Context) {
        val prefs = context.defaultPrefs()
        val saved = prefs.getString(KEY_LANGUAGE_MODE, null)
        _mode.value = LanguageMode.fromPref(saved)
    }

    fun setMode(context: Context, newMode: LanguageMode) {
        _mode.value = newMode
        context.defaultPrefs().edit()
            .putString(KEY_LANGUAGE_MODE, newMode.prefValue)
            .apply()
    }

    // ─── Resolution API ─────────────────────────────────────────────────────

    /**
     * Resolve a vocabulary key to the display string for the current [LanguageMode].
     *
     * For CO_PILOT mode, returns the vessel term as the primary label.
     * The plain-language subtitle is available via [resolveSubtitle].
     *
     * Example:
     *   resolve(LightspeedVocabulary.Key.DEFLECTORS)
     *   // VESSEL_LORE  → "Left & Right Deflectors"
     *   // CO_PILOT     → "Left & Right Deflectors"  (subtitle = "Gesture Sidebars")
     *   // CLEAR_COMMS  → "Left & Right Gesture Sidebars"
     */
    fun resolve(key: LightspeedVocabulary.Key, mode: LanguageMode = _mode.value): String =
        when (mode) {
            LanguageMode.VESSEL_LORE -> LightspeedVocabulary.vessel(key)
            LanguageMode.CO_PILOT    -> LightspeedVocabulary.vessel(key)   // headline = vessel
            LanguageMode.CLEAR_COMMS -> LightspeedVocabulary.clear(key)
        }

    /**
     * Returns the secondary plain-language subtitle for CO_PILOT mode.
     * Returns null in other modes so callers can conditionally render it.
     */
    fun resolveSubtitle(key: LightspeedVocabulary.Key, mode: LanguageMode = _mode.value): String? =
        if (mode == LanguageMode.CO_PILOT) LightspeedVocabulary.clear(key) else null

    /**
     * Resolve both the primary label and optional subtitle in one call.
     */
    fun resolvePair(key: LightspeedVocabulary.Key, mode: LanguageMode = _mode.value): Pair<String, String?> =
        resolve(key, mode) to resolveSubtitle(key, mode)
}
