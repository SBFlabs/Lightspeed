package com.sbf.lightspeed.system

import android.content.Context
import android.content.SharedPreferences
import com.sbf.lightspeed.LightspeedDataBridge
import org.json.JSONArray

/**
 * Typed getter and setter functions for Lightspeed avionics, gestures,
 * and hardware volume/power triggers.
 */
object LightspeedPreferencesAccessors : LightspeedPreferencesAccessorsContract {

    override fun getAppCategoryOverride(context: Context, pkg: String): String? {
        return context.defaultPrefs().getString(LightspeedPreferences.KEY_APP_CATEGORY_OVERRIDE_PREFIX + pkg, null)
    }

    override fun setAppCategoryOverride(context: Context, pkg: String, categoryKey: String) {
        context.defaultPrefs().edit().putString(LightspeedPreferences.KEY_APP_CATEGORY_OVERRIDE_PREFIX + pkg, categoryKey).apply()
    }

    override fun getCategoryOrder(context: Context): List<String> {
        val defaultKeys = LightspeedDataBridge.DRAWER_CATEGORIES.map { it.id }
        val validKeySet = defaultKeys.toSet()
        val raw = context.defaultPrefs().getString(LightspeedPreferences.KEY_CATEGORY_ORDER, null)
        if (raw.isNullOrBlank()) {
            return defaultKeys
        }
        val result = mutableListOf<String>()
        val seen = mutableSetOf<String>()
        for (item in raw.split(",")) {
            val key = item.trim()
            if (key in validKeySet && seen.add(key)) {
                result.add(key)
            }
        }
        for (key in defaultKeys) {
            if (seen.add(key)) {
                result.add(key)
            }
        }
        return result
    }

    override fun setCategoryOrder(context: Context, keys: List<String>) {
        val defaultKeys = LightspeedDataBridge.DRAWER_CATEGORIES.map { it.id }
        val validKeySet = defaultKeys.toSet()
        val result = mutableListOf<String>()
        val seen = mutableSetOf<String>()
        for (key in keys) {
            val trimmed = key.trim()
            if (trimmed in validKeySet && seen.add(trimmed)) {
                result.add(trimmed)
            }
        }
        for (key in defaultKeys) {
            if (seen.add(key)) {
                result.add(key)
            }
        }
        context.defaultPrefs().edit().putString(LightspeedPreferences.KEY_CATEGORY_ORDER, result.joinToString(",")).apply()
    }

    override fun isScrubRegionsLinked(context: Context, isLeft: Boolean): Boolean {
        val prefs = context.defaultPrefs()
        val key = if (isLeft) LightspeedPreferences.KEY_UNIFIED_SCRUB_REGIONS_LINKED_LEFT else LightspeedPreferences.KEY_UNIFIED_SCRUB_REGIONS_LINKED_RIGHT
        return prefs.getBoolean(key, prefs.getBoolean(LightspeedPreferences.KEY_UNIFIED_SCRUB_REGIONS_LINKED, false))
    }

    override fun setScrubRegionsLinked(context: Context, isLeft: Boolean, linked: Boolean) {
        val prefs = context.defaultPrefs()
        val key = if (isLeft) LightspeedPreferences.KEY_UNIFIED_SCRUB_REGIONS_LINKED_LEFT else LightspeedPreferences.KEY_UNIFIED_SCRUB_REGIONS_LINKED_RIGHT
        prefs.edit().putBoolean(key, linked).apply()
    }

    override fun getGestureUnifiedKey(isLeft: Boolean, gestureKey: String): String {
        val flank = if (isLeft) "left" else "right"
        return "pref_gesture_unified_${flank}_${gestureKey}"
    }

    override fun getDeflectorLongSwipeThresholdDp(context: Context, isLandscape: Boolean): Int {
        val prefs = context.defaultPrefs()
        return if (isLandscape) {
            prefs.getInt(LightspeedPreferences.KEY_DEFLECTOR_LONG_SWIPE_THRESHOLD_LANDSCAPE, LightspeedPreferences.DEFAULT_DEFLECTOR_LONG_SWIPE_THRESHOLD_LANDSCAPE).coerceIn(40, 320)
        } else {
            prefs.getInt(LightspeedPreferences.KEY_DEFLECTOR_LONG_SWIPE_THRESHOLD_PORTRAIT, LightspeedPreferences.DEFAULT_DEFLECTOR_LONG_SWIPE_THRESHOLD_PORTRAIT).coerceIn(40, 240)
        }
    }

    override fun isGestureUnified(context: Context, isLeft: Boolean, gestureKey: String): Boolean {
        val prefs = context.defaultPrefs()
        val key = getGestureUnifiedKey(isLeft, gestureKey)
        val defaultVal = !gestureKey.equals("TAP_HOLD", ignoreCase = true) && !gestureKey.equals("HOLD", ignoreCase = true) && !gestureKey.contains("SCRUB", ignoreCase = true)
        return prefs.getBoolean(key, defaultVal)
    }

    override fun setGestureUnified(context: Context, isLeft: Boolean, gestureKey: String, unified: Boolean) {
        val prefs = context.defaultPrefs()
        val key = getGestureUnifiedKey(isLeft, gestureKey)
        prefs.edit().putBoolean(key, unified).apply()
    }

    override fun resolveHudStyle(prefs: SharedPreferences, gestureActionKey: String?, actionToken: String?): String {
        if (!gestureActionKey.isNullOrBlank() && !actionToken.isNullOrBlank()) {
            val cleanToken = actionToken.replace(":", "_")
            val baseHudKey = gestureActionKey.replace("pref_macro_action_", "pref_macro_hud_style_")
            val specificActionKey = "${baseHudKey}_$cleanToken"
            val styleForAction = prefs.getString(specificActionKey, null)
            if (!styleForAction.isNullOrBlank()) return styleForAction
        }
        if (!gestureActionKey.isNullOrBlank()) {
            val baseHudKey = gestureActionKey.replace("pref_macro_action_", "pref_macro_hud_style_")
            val styleForGesture = prefs.getString(baseHudKey, null)
            if (!styleForGesture.isNullOrBlank()) return styleForGesture
        }
        return prefs.getString(LightspeedPreferences.KEY_HUD_STYLE_DEFAULT, LightspeedPreferences.DEFAULT_HUD_STYLE) ?: LightspeedPreferences.DEFAULT_HUD_STYLE
    }

    override fun saveHudStyle(prefs: SharedPreferences, gestureActionKey: String?, actionToken: String?, style: String) {
        val editor = prefs.edit()
        if (!gestureActionKey.isNullOrBlank()) {
            val baseHudKey = gestureActionKey.replace("pref_macro_action_", "pref_macro_hud_style_")
            editor.putString(baseHudKey, style)
            if (!actionToken.isNullOrBlank()) {
                val cleanToken = actionToken.replace(":", "_")
                editor.putString("${baseHudKey}_$cleanToken", style)
            }
        } else {
            editor.putString(LightspeedPreferences.KEY_HUD_STYLE_DEFAULT, style)
        }
        editor.apply()
    }

    override fun getEffectiveCutoutOffsetX(prefs: SharedPreferences): Int {
        if (prefs.contains(LightspeedPreferences.KEY_HARDWARE_CUTOUT_OFFSET_X)) {
            return prefs.getInt(LightspeedPreferences.KEY_HARDWARE_CUTOUT_OFFSET_X, 0)
        }
        val legacyNotch = prefs.getInt(LightspeedPreferences.KEY_NOTCH_OFFSET_X, 0)
        if (legacyNotch != 0) return legacyNotch
        return prefs.getInt(LightspeedPreferences.KEY_HORIZON_RAIL_CUTOUT_OFFSET_X, 0)
    }

    override fun getEffectiveCutoutOffsetY(prefs: SharedPreferences): Int {
        if (prefs.contains(LightspeedPreferences.KEY_HARDWARE_CUTOUT_OFFSET_Y)) {
            return prefs.getInt(LightspeedPreferences.KEY_HARDWARE_CUTOUT_OFFSET_Y, 0)
        }
        return prefs.getInt(LightspeedPreferences.KEY_NOTCH_OFFSET_Y, 0)
    }

    override fun getManualNativeRotationPolicy(context: Context): String =
        context.defaultPrefs().getString(LightspeedPreferences.KEY_MANUAL_NATIVE_ROTATION_POLICY, LightspeedPreferences.MANUAL_NATIVE_POLICY_BUCKET_WINS) ?: LightspeedPreferences.MANUAL_NATIVE_POLICY_BUCKET_WINS

    override fun getCallDeflectorMode(context: Context): String =
        context.defaultPrefs().getString(LightspeedPreferences.KEY_CALL_DEFLECTOR_MODE, "keep_active") ?: "keep_active"

    override fun getPinnedGearLeft(context: Context): Boolean = context.defaultPrefs().getBoolean(LightspeedPreferences.KEY_PINNED_GEAR_LEFT, false)

    override fun getMediaSkipSeconds(prefs: SharedPreferences, forward: Boolean): Int {
        val specificKey = if (forward) LightspeedPreferences.KEY_MEDIA_SKIP_FORWARD_SECONDS else LightspeedPreferences.KEY_MEDIA_SKIP_BACKWARD_SECONDS
        return if (prefs.contains(specificKey)) {
            prefs.getInt(specificKey, 10)
        } else {
            prefs.getInt(LightspeedPreferences.KEY_MEDIA_SKIP_SECONDS, 10)
        }
    }

    override fun setMediaSkipSeconds(prefs: SharedPreferences, forward: Boolean, sec: Int) {
        val specificKey = if (forward) LightspeedPreferences.KEY_MEDIA_SKIP_FORWARD_SECONDS else LightspeedPreferences.KEY_MEDIA_SKIP_BACKWARD_SECONDS
        prefs.edit().putInt(specificKey, sec).apply()
    }

    override fun getRefuelingStackWidgetIds(context: Context): List<Int> {
        val prefs = context.defaultPrefs()
        val jsonStr = prefs.getString(LightspeedPreferences.KEY_REFUELING_STACK_PROFILE, null)
            ?: prefs.getString(LightspeedPreferences.KEY_REFUELING_WIDGET_IDS, null)
        if (!jsonStr.isNullOrBlank()) {
            try {
                val jsonArray = JSONArray(jsonStr)
                val list = mutableListOf<Int>()
                for (i in 0 until jsonArray.length()) {
                    val id = jsonArray.getInt(i)
                    if (id != -1 && !list.contains(id)) {
                        list.add(id)
                    }
                }
                return list
            } catch (e: Exception) { logSwallowed("LightspeedPreferencesAccessors", "getRefuelingStackWidgetIds", e) }
        }
        val legacyId = prefs.getInt(LightspeedPreferences.KEY_REFUELING_WIDGET_ID, -1)
        return if (legacyId != -1) listOf(legacyId) else emptyList()
    }

    override fun saveRefuelingStackWidgetIds(context: Context, ids: List<Int>) {
        val prefs = context.defaultPrefs()
        val jsonArray = JSONArray()
        ids.forEach { jsonArray.put(it) }
        prefs.edit()
            .putString(LightspeedPreferences.KEY_REFUELING_STACK_PROFILE, jsonArray.toString())
            .putString(LightspeedPreferences.KEY_REFUELING_WIDGET_IDS, jsonArray.toString())
            .putInt(LightspeedPreferences.KEY_REFUELING_WIDGET_ID, ids.firstOrNull() ?: -1)
            .apply()
    }

    override fun getRefuelingGridWidgetIds(context: Context, isLandscape: Boolean): List<Int> {
        val prefs = context.defaultPrefs()
        val key = if (isLandscape) LightspeedPreferences.KEY_REFUELING_GRID_LANDSCAPE_PROFILE else LightspeedPreferences.KEY_REFUELING_GRID_PORTRAIT_PROFILE
        val jsonStr = prefs.getString(key, null)
            ?: prefs.getString(LightspeedPreferences.KEY_REFUELING_GRID_PROFILE, null)
            ?: prefs.getString(LightspeedPreferences.KEY_REFUELING_WIDGET_IDS, null)
        if (!jsonStr.isNullOrBlank()) {
            try {
                val jsonArray = JSONArray(jsonStr)
                val list = mutableListOf<Int>()
                for (i in 0 until jsonArray.length()) {
                    val id = jsonArray.getInt(i)
                    if (id != -1 && !list.contains(id)) {
                        list.add(id)
                    }
                }
                if (list.isNotEmpty()) return list
            } catch (e: Exception) { logSwallowed("LightspeedPreferencesAccessors", "getRefuelingGridWidgetIds", e) }
        }
        val legacyId = prefs.getInt(LightspeedPreferences.KEY_REFUELING_WIDGET_ID, -1)
        return if (legacyId != -1) listOf(legacyId) else emptyList()
    }

    override fun saveRefuelingGridWidgetIds(context: Context, ids: List<Int>, isLandscape: Boolean) {
        val prefs = context.defaultPrefs()
        val jsonArray = JSONArray()
        ids.forEach { jsonArray.put(it) }
        val key = if (isLandscape) LightspeedPreferences.KEY_REFUELING_GRID_LANDSCAPE_PROFILE else LightspeedPreferences.KEY_REFUELING_GRID_PORTRAIT_PROFILE
        prefs.edit()
            .putString(key, jsonArray.toString())
            .putString(LightspeedPreferences.KEY_REFUELING_GRID_PROFILE, jsonArray.toString())
            .putString(LightspeedPreferences.KEY_REFUELING_WIDGET_IDS, jsonArray.toString())
            .putInt(LightspeedPreferences.KEY_REFUELING_WIDGET_ID, ids.firstOrNull() ?: -1)
            .apply()
    }

    override fun isRefuelingStackMemoryEnabled(context: Context): Boolean =
        context.defaultPrefs().getBoolean(LightspeedPreferences.KEY_REFUELING_STACK_REMEMBER_PAGE, false)

    override fun setRefuelingStackMemoryEnabled(context: Context, enabled: Boolean) {
        context.defaultPrefs().edit().putBoolean(LightspeedPreferences.KEY_REFUELING_STACK_REMEMBER_PAGE, enabled).apply()
    }

    override fun isMasterFlightArmed(context: Context): Boolean =
        context.defaultPrefs().getBoolean(LightspeedPreferences.KEY_MASTER_FLIGHT_ARMED, true)

    override fun setMasterFlightArmed(context: Context, armed: Boolean) {
        context.defaultPrefs().edit().putBoolean(LightspeedPreferences.KEY_MASTER_FLIGHT_ARMED, armed).apply()
    }

    override fun isLeftDeflectorEnabled(context: Context): Boolean =
        context.defaultPrefs().getBoolean(LightspeedPreferences.KEY_DEFLECTOR_LEFT_ENABLED, true)

    override fun setLeftDeflectorEnabled(context: Context, enabled: Boolean) {
        context.defaultPrefs().edit().putBoolean(LightspeedPreferences.KEY_DEFLECTOR_LEFT_ENABLED, enabled).apply()
    }

    override fun getActionBehavior(context: Context, token: String, default: String): String {
        return context.defaultPrefs().getString("${LightspeedPreferences.KEY_ACTION_BEHAVIOR_PREFIX}${token}", default) ?: default
    }

    override fun setActionBehavior(context: Context, token: String, behavior: String) {
        context.defaultPrefs().edit().putString("${LightspeedPreferences.KEY_ACTION_BEHAVIOR_PREFIX}${token}", behavior).apply()
    }

    override fun isRightDeflectorEnabled(context: Context): Boolean =
        context.defaultPrefs().getBoolean(LightspeedPreferences.KEY_DEFLECTOR_RIGHT_ENABLED, true)

    override fun setRightDeflectorEnabled(context: Context, enabled: Boolean) {
        context.defaultPrefs().edit().putBoolean(LightspeedPreferences.KEY_DEFLECTOR_RIGHT_ENABLED, enabled).apply()
    }

    override fun isLeftDeflectorGlowEnabled(context: Context): Boolean =
        context.defaultPrefs().getBoolean(LightspeedPreferences.KEY_DEFLECTOR_LEFT_GLOW_ENABLED, false)

    override fun setLeftDeflectorGlowEnabled(context: Context, enabled: Boolean) {
        context.defaultPrefs().edit().putBoolean(LightspeedPreferences.KEY_DEFLECTOR_LEFT_GLOW_ENABLED, enabled).apply()
    }

    override fun isRightDeflectorGlowEnabled(context: Context): Boolean =
        context.defaultPrefs().getBoolean(LightspeedPreferences.KEY_DEFLECTOR_RIGHT_GLOW_ENABLED, context.defaultPrefs().getBoolean(LightspeedPreferences.KEY_DEFLECTOR_GLOW_ENABLED, true))

    override fun setRightDeflectorGlowEnabled(context: Context, enabled: Boolean) {
        context.defaultPrefs().edit().putBoolean(LightspeedPreferences.KEY_DEFLECTOR_RIGHT_GLOW_ENABLED, enabled).apply()
    }

    override fun isDeflectorUseM3Color(context: Context): Boolean =
        context.defaultPrefs().getBoolean(LightspeedPreferences.KEY_DEFLECTOR_USE_M3_COLOR, false)

    override fun setDeflectorUseM3Color(context: Context, enabled: Boolean) {
        context.defaultPrefs().edit().putBoolean(LightspeedPreferences.KEY_DEFLECTOR_USE_M3_COLOR, enabled).apply()
    }

    override fun getDeflectorGlowStyle(context: Context): String =
        context.defaultPrefs().getString(LightspeedPreferences.KEY_DEFLECTOR_GLOW_STYLE, "progressive_frost") ?: "progressive_frost"

    override fun getDeflectorPillStyle(context: Context, isLeft: Boolean): String {
        val key = if (isLeft) LightspeedPreferences.KEY_DEFLECTOR_LEFT_PILL_STYLE else LightspeedPreferences.KEY_DEFLECTOR_RIGHT_PILL_STYLE
        val fallback = context.defaultPrefs().getString(LightspeedPreferences.KEY_DEFLECTOR_PILL_STYLE, "razor_edge") ?: "razor_edge"
        return context.defaultPrefs().getString(key, fallback) ?: fallback
    }

    override fun setDeflectorPillStyle(context: Context, isLeft: Boolean, style: String) {
        val key = if (isLeft) LightspeedPreferences.KEY_DEFLECTOR_LEFT_PILL_STYLE else LightspeedPreferences.KEY_DEFLECTOR_RIGHT_PILL_STYLE
        context.defaultPrefs().edit().putString(key, style).apply()
    }

    override fun setDeflectorGlowStyle(context: Context, style: String) {
        context.defaultPrefs().edit().putString(LightspeedPreferences.KEY_DEFLECTOR_GLOW_STYLE, style).apply()
    }

    override fun isDeflectorGlowOnGestureStep(context: Context): Boolean =
        context.defaultPrefs().getBoolean(LightspeedPreferences.KEY_DEFLECTOR_GLOW_ON_GESTURE_STEP, true)

    override fun setDeflectorGlowOnGestureStep(context: Context, enabled: Boolean) {
        context.defaultPrefs().edit().putBoolean(LightspeedPreferences.KEY_DEFLECTOR_GLOW_ON_GESTURE_STEP, enabled).apply()
    }

    override fun getDeflectorGlowDurationMs(context: Context): Long {
        val pref = context.defaultPrefs().getString(LightspeedPreferences.KEY_DEFLECTOR_GLOW_DURATION, "1500ms") ?: "1500ms"
        return when (pref) {
            "800ms" -> 800L
            "2200ms" -> 2200L
            else -> 1500L
        }
    }

    override fun isFlightNotificationEnabled(context: Context): Boolean =
        context.defaultPrefs().getBoolean(LightspeedPreferences.KEY_FLIGHT_NOTIFICATION_ENABLED, false)

    override fun setFlightNotificationEnabled(context: Context, enabled: Boolean) {
        context.defaultPrefs().edit().putBoolean(LightspeedPreferences.KEY_FLIGHT_NOTIFICATION_ENABLED, enabled).apply()
    }

    override fun getCentralCommandLongPressAction(context: Context): String =
        context.defaultPrefs().getString(LightspeedPreferences.KEY_CENTRAL_COMMAND_LONG_PRESS_ACTION, "toggle_master_flight") ?: "toggle_master_flight"

    override fun setCentralCommandLongPressAction(context: Context, action: String) {
        context.defaultPrefs().edit().putString(LightspeedPreferences.KEY_CENTRAL_COMMAND_LONG_PRESS_ACTION, action).apply()
    }

    override fun isHudBrightnessEnabled(context: Context): Boolean =
        context.defaultPrefs().getBoolean(LightspeedPreferences.KEY_HUD_BRIGHTNESS_ENABLED, true)

    override fun isHudVolumeEnabled(context: Context): Boolean =
        context.defaultPrefs().getBoolean(LightspeedPreferences.KEY_HUD_VOLUME_ENABLED, true)

    override fun isVolumeShowNativeSlider(context: Context): Boolean =
        context.defaultPrefs().getBoolean(LightspeedPreferences.KEY_VOLUME_SHOW_NATIVE_SLIDER, false)

    override fun getVolumeScrubResolution(context: Context): Int =
        context.defaultPrefs().getInt(LightspeedPreferences.KEY_VOLUME_SCRUB_RESOLUTION, 100).coerceIn(5, 100)

    override fun getGestureHoldDurationMs(context: Context, gesturePrefKey: String?): Long {
        val prefs = context.defaultPrefs()
        val global = prefs.getInt(LightspeedPreferences.KEY_GESTURE_HOLD_DURATION_MS, LightspeedPreferences.DEFAULT_GESTURE_HOLD_DURATION_MS)
        if (gesturePrefKey != null) {
            val key = if (gesturePrefKey.startsWith("pref_gesture_hold_duration_")) gesturePrefKey else "pref_gesture_hold_duration_$gesturePrefKey"
            return prefs.getInt(key, global).toLong()
        }
        return global.toLong()
    }

    override fun getGravityEnforcementEngine(context: Context): String =
        context.defaultPrefs().getString(LightspeedPreferences.KEY_GRAVITY_ENFORCEMENT_ENGINE, LightspeedPreferences.GRAVITY_ENGINE_DUAL_HYBRID) ?: LightspeedPreferences.GRAVITY_ENGINE_DUAL_HYBRID
}
