package com.sbf.lightspeed.system

import android.content.Context
import android.content.res.Configuration
import android.content.res.Resources
import androidx.annotation.StringRes
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap

/**
 * LightspeedLocaleEngine — real-world language state for the app (English, Portuguese, Spanish)
 * plus the optional Real-World Bilingual pair (Primary + Secondary).
 *
 * This is independent from [LightspeedLanguageEngine], which only decides the *vocabulary*
 * style (Space Lore / Clear Comms / Bilingual vocabulary).
 *
 * Design notes:
 *  • State is Compose snapshot state, so any composable that resolves a string through
 *    [primaryString] / [secondaryFor] recomposes instantly when the language changes —
 *    no Activity recreation, no flicker, 100% offline.
 *  • Strings come from per-language [Resources] built once through
 *    `createConfigurationContext(...)` and cached in [resourcesCache].
 *  • [registry] lets headers find the secondary-language twin of a string that was resolved
 *    for the primary language (text -> string resource id).
 */
object LightspeedLocaleEngine {

    const val KEY_UI_LANGUAGE = "pref_ui_language"
    const val KEY_UI_BILINGUAL_ENABLED = "pref_ui_bilingual_enabled"
    const val KEY_UI_SECONDARY_LANGUAGE = "pref_ui_secondary_language"

    /** Tone of the compact language badge. New scripts (e.g. Arabic) can pick their own tone. */
    enum class BadgeTone { NEUTRAL, GREEN }

    /**
     * Supported real-world languages. To add a language (e.g. Arabic):
     *  1. add `values-ar/strings.xml`,
     *  2. add an entry here (`badge = "ع"`, `tone = BadgeTone.GREEN`),
     *  3. nothing else — the picker, slider and badges are driven by [entries].
     */
    enum class AppLanguage(
        val code: String,
        val badge: String,
        val tone: BadgeTone,
        val englishName: String,
        val nativeName: String?,
        val locale: Locale,
        val morphGlyphs: List<String>
    ) {
        EN("en", "EN", BadgeTone.NEUTRAL, "English", null, Locale.ENGLISH, listOf("A", "E", "W", "Z")),
        PT("pt", "PT", BadgeTone.NEUTRAL, "Portuguese", "Português · BR", Locale("pt", "BR"), listOf("Ç", "Ã", "Õ", "Ê")),
        ES("es", "ES", BadgeTone.NEUTRAL, "Spanish", "Español", Locale("es"), listOf("Ñ", "Á", "É", "¿"));

        /** `Portuguese (Português · BR)` — native script in brackets when it differs. */
        val label: String get() = if (nativeName == null) englishName else "$englishName ($nativeName)"

        companion object {
            fun fromCode(code: String?): AppLanguage? = entries.firstOrNull { it.code == code }

            /** Maps the device locale to a supported language; anything unknown opens in English. */
            fun fromSystemLocale(locale: Locale = Locale.getDefault()): AppLanguage =
                entries.firstOrNull { it.locale.language == locale.language } ?: EN
        }
    }

    data class LocaleState(
        val primary: AppLanguage,
        val bilingual: Boolean,
        val secondary: AppLanguage
    ) {
        /** True only when bilingual is on AND the pair is two different languages. */
        val secondaryActive: Boolean get() = bilingual && secondary != primary
    }

    private var appContext: Context? = null
    private var _state by mutableStateOf(LocaleState(AppLanguage.EN, false, AppLanguage.PT))
    private val resourcesCache = ConcurrentHashMap<String, Resources>()
    private val registry = ConcurrentHashMap<String, Int>()

    val state: LocaleState get() = _state

    /** GitHub README matching the primary language (`EN` falls back to the root `README.md`). */
    fun readmeUrl(): String {
        val file = when (_state.primary) {
            AppLanguage.PT -> "README.pt-BR.md"
            AppLanguage.ES -> "README.es.md"
            else -> return "https://github.com/SBFlabs/Lightspeed#readme"
        }
        return "https://github.com/SBFlabs/Lightspeed/blob/master/$file"
    }

    // ─── Lifecycle ──────────────────────────────────────────────────────────

    /**
     * Loads the saved language. On the very first launch (nothing saved yet) the device locale is
     * detected so `pt-BR` and `es` phones open in their native language automatically.
     */
    fun init(context: Context) {
        val ctx = context.applicationContext
        appContext = ctx
        val prefs = ctx.defaultPrefs()
        val saved = AppLanguage.fromCode(prefs.getString(KEY_UI_LANGUAGE, null))
        val primary = saved ?: AppLanguage.fromSystemLocale().also {
            prefs.edit().putString(KEY_UI_LANGUAGE, it.code).apply()
        }
        val secondary = AppLanguage.fromCode(prefs.getString(KEY_UI_SECONDARY_LANGUAGE, null))
            ?: defaultSecondaryFor(primary)
        val next = LocaleState(primary, prefs.getBoolean(KEY_UI_BILINGUAL_ENABLED, false), secondary)
        if (next != _state) {
            registry.clear()
            _state = next
        }
    }

    // ─── Mutation ───────────────────────────────────────────────────────────

    /** Single-language mode: picks [language] as primary and turns Bilingual off. */
    fun selectSingle(context: Context, language: AppLanguage) {
        val prev = _state
        val secondary = if (prev.secondary == language) defaultSecondaryFor(language) else prev.secondary
        commit(context, LocaleState(language, false, secondary))
    }

    /** Turns Real-World Bilingual on, keeping the saved pair (or choosing a sensible one). */
    fun selectBilingual(context: Context) {
        val prev = _state
        val secondary = if (prev.secondary == prev.primary) defaultSecondaryFor(prev.primary) else prev.secondary
        commit(context, LocaleState(prev.primary, true, secondary))
    }

    /** Turns Real-World Bilingual off while keeping the current Primary language. */
    fun disableBilingual(context: Context) {
        val prev = _state
        if (!prev.bilingual) return
        commit(context, prev.copy(bilingual = false))
    }

    /** Sets the Primary language of the pair without touching the bilingual flag. */
    fun setPrimary(context: Context, language: AppLanguage) {
        val prev = _state
        val secondary = if (prev.secondary == language) defaultSecondaryFor(language) else prev.secondary
        commit(context, prev.copy(primary = language, secondary = secondary))
    }

    /** Sets the Secondary language of the pair; swaps if it collides with the primary. */
    fun setSecondary(context: Context, language: AppLanguage) {
        val prev = _state
        if (language == prev.primary) {
            commit(context, prev.copy(primary = prev.secondary, secondary = language))
        } else {
            commit(context, prev.copy(secondary = language))
        }
    }

    private fun commit(context: Context, next: LocaleState) {
        if (next == _state) return
        context.defaultPrefs().edit()
            .putString(KEY_UI_LANGUAGE, next.primary.code)
            .putBoolean(KEY_UI_BILINGUAL_ENABLED, next.bilingual)
            .putString(KEY_UI_SECONDARY_LANGUAGE, next.secondary.code)
            .apply()
        if (next.primary != _state.primary) registry.clear()
        _state = next
        CockpitGearRepository.clearCache()
        com.sbf.lightspeed.settings.LightspeedActionRegistry.refreshLocalizedLabels(context)
        com.sbf.lightspeed.LightspeedCruiseOverlay.instance?.postInvalidate()
    }

    private fun defaultSecondaryFor(primary: AppLanguage): AppLanguage {
        val device = AppLanguage.fromSystemLocale()
        if (device != primary) return device
        return AppLanguage.entries.first { it != primary }
    }

    // ─── Picker helpers (Bilingual pinned at index 0, then languages alphabetically) ──────────

    /** Number of picker entries: Bilingual + every [AppLanguage]. */
    val pickerCount: Int get() = AppLanguage.entries.size + 1

    /** Index of the active picker entry: 0 = Bilingual, 1.. = [AppLanguage.entries]. */
    fun pickerIndex(): Int = if (_state.bilingual) 0 else 1 + _state.primary.ordinal

    fun selectPickerIndex(context: Context, index: Int) {
        if (index <= 0) selectBilingual(context) else selectSingle(context, AppLanguage.entries[(index - 1).coerceAtMost(AppLanguage.entries.size - 1)])
    }

    // ─── Resolution ─────────────────────────────────────────────────────────

    /** Cached [Resources] for [language], built through `createConfigurationContext`. */
    fun resourcesFor(language: AppLanguage): Resources? {
        val ctx = appContext ?: return null
        return resourcesCache.getOrPut(language.code) {
            val config = Configuration(ctx.resources.configuration).apply { setLocale(language.locale) }
            ctx.createConfigurationContext(config).resources
        }
    }

    /** Resolves [id] in the primary language and remembers it so [secondaryFor] can find its twin. */
    fun primaryString(@StringRes id: Int, vararg args: Any): String {
        val res = resourcesFor(_state.primary) ?: return ""
        val text = if (args.isEmpty()) res.getString(id) else res.getString(id, *args)
        if (args.isEmpty()) registry[text] = id
        return text
    }

    /** Resolves [id] in an explicit [language] (used for the secondary line and language-neutral labels). */
    fun stringIn(language: AppLanguage, @StringRes id: Int): String =
        resourcesFor(language)?.getString(id).orEmpty()

    /**
     * Secondary-language twin of a header that was resolved for the primary language.
     * Returns null when Bilingual is off, the text is unknown, or both languages read the same.
     */
    fun secondaryFor(primaryText: String): String? {
        val st = _state
        if (!st.secondaryActive) return null
        val id = registry[primaryText] ?: return null
        val secondary = stringIn(st.secondary, id)
        return if (secondary.isBlank() || secondary.equals(primaryText, ignoreCase = true)) null else secondary
    }
}

/** Primary-language string for [id]; safe to call from any composable (state reads recompose on change). */
fun lsString(@StringRes id: Int): String = LightspeedLocaleEngine.primaryString(id)

/** Primary-language formatted string for [id]. */
fun lsString(@StringRes id: Int, vararg args: Any): String = LightspeedLocaleEngine.primaryString(id, *args)
