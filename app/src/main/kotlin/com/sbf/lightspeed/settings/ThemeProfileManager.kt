package com.sbf.lightspeed.settings

import android.content.SharedPreferences

/**
 * Manages saved optical tuning profiles for all 12 Deck themes.
 * Stores presets in SharedPreferences using the key `pref_deck_theme_profiles_<styleId>`.
 * Each profile contains a label, styleId, and serialized key-value pairs of customized snapshot values.
 */
object ThemeProfileManager {

    data class ThemeProfile(
        val raw: String,
        val label: String,
        val styleId: String,
        val params: Map<String, Float>
    ) {
        val displayTitle: String get() = label
    }

    private fun prefKey(styleId: String) = "pref_deck_theme_profiles_$styleId"

    fun getProfiles(prefs: SharedPreferences, styleId: String): List<ThemeProfile> {
        val raw = prefs.getString(prefKey(styleId), null) ?: return emptyList()
        return raw.split(";;;").mapNotNull { entry ->
            val clean = entry.trim()
            if (clean.isEmpty()) null
            else parseProfile(clean, styleId)
        }
    }

    private fun parseProfile(rawEntry: String, fallbackStyleId: String): ThemeProfile? {
        val parts = rawEntry.split(":::=")
        if (parts.size < 2) return null
        val label = parts[0].trim()
        val dataStr = parts[1].trim()
        val paramMap = mutableMapOf<String, Float>()
        dataStr.split(",").forEach { kv ->
            val pair = kv.split("=")
            if (pair.size == 2) {
                pair[1].toFloatOrNull()?.let { v -> paramMap[pair[0].trim()] = v }
            }
        }
        return ThemeProfile(rawEntry, label, fallbackStyleId, paramMap)
    }

    fun saveProfile(
        prefs: SharedPreferences,
        styleId: String,
        label: String,
        params: Map<String, Float>
    ): Boolean {
        if (label.isBlank()) return false
        val current = getProfiles(prefs, styleId).toMutableList()
        if (current.any { it.label.equals(label.trim(), ignoreCase = true) }) {
            return false
        }
        val dataStr = params.entries.joinToString(",") { "${it.key}=${it.value}" }
        val rawEntry = "${label.trim()}:::=$dataStr"
        current.add(0, ThemeProfile(rawEntry, label.trim(), styleId, params))
        prefs.edit().putString(prefKey(styleId), current.joinToString(";;;") { it.raw }).apply()
        return true
    }

    fun deleteProfile(prefs: SharedPreferences, styleId: String, profile: ThemeProfile) {
        val current = getProfiles(prefs, styleId).toMutableList()
        current.removeAll { it.label == profile.label }
        prefs.edit().putString(prefKey(styleId), current.joinToString(";;;") { it.raw }).apply()
    }

    fun renameProfile(
        prefs: SharedPreferences,
        styleId: String,
        oldProfile: ThemeProfile,
        newLabel: String
    ): Boolean {
        if (newLabel.isBlank()) return false
        val current = getProfiles(prefs, styleId).toMutableList()
        val index = current.indexOfFirst { it.label == oldProfile.label }
        if (index == -1) return false
        val dataStr = oldProfile.params.entries.joinToString(",") { "${it.key}=${it.value}" }
        val newRaw = "${newLabel.trim()}:::=$dataStr"
        current[index] = ThemeProfile(newRaw, newLabel.trim(), styleId, oldProfile.params)
        prefs.edit().putString(prefKey(styleId), current.joinToString(";;;") { it.raw }).apply()
        return true
    }
}
