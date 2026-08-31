package com.sbf.lightspeed.system

import android.content.Context
import android.content.SharedPreferences

/**
 * Standardized SharedPreferences constants and extension accessors for Lightspeed.
 */
object LightspeedPreferences {
    const val PREFS_FILE = "default"

    // Status Bar Keys
    const val KEY_STATUSBAR_ENABLED = "pref_statusbar_enabled"
    const val KEY_STATUSBAR_SPAN = "pref_statusbar_span"
    const val KEY_STATUSBAR_THICKNESS = "pref_statusbar_thickness"
    const val KEY_STATUSBAR_OFFSET_X = "pref_statusbar_offset_x"
    const val KEY_STATUSBAR_OFFSET_Y = "pref_statusbar_offset_y"

    // Cockpit & Gears Keys
    const val KEY_GEAR_SETS_ORDER = "gear_sets_order"
    const val KEY_COCKPIT_LAUNCH_BEHAVIOR = "cockpit_launch_behavior"
    const val KEY_GEAR_PHYSICS_PROFILE = "pref_gear_physics_profile"
    const val KEY_GEAR_HAPTIC_STRENGTH = "pref_gear_haptic_strength"

    // Icon Pack & Pipeline Keys
    const val KEY_ACTIVE_ICON_PACK = "pref_active_icon_pack" // Legacy key for backward compatibility
    const val KEY_CATEGORY_CRUISE_ICON_PACK = "pref_category_cruise_icon_pack" // Meta-category glyph tokens
    const val KEY_CATEGORY_CRUISE_ICON_STYLE = "pref_category_cruise_icon_style" // Meta-category icon styling
    const val KEY_COCKPIT_ICON_PACK = "pref_cockpit_icon_pack" // Target app icon pack (default: "system")
    const val KEY_COCKPIT_ICON_RENDER_MODE = "pref_cockpit_icon_render_mode" // "pack_native", "adaptive_squircle", "original"

    // Symmetry & Coupling Keys
    const val KEY_SYMMETRY_GEOMETRY_MODE = "pref_symmetry_geometry_mode"
    const val KEY_SYMMETRY_GESTURE_MODE = "pref_symmetry_gesture_mode"
    const val KEY_SIDEBAR_RIGHT_LINK_FLANK = "pref_sidebar_right_link_flank_actions"
    const val KEY_SIDEBAR_LEFT_LINK_FLANK = "pref_sidebar_left_link_flank_actions"

    // Notch Calibration & Test Beacon Keys
    const val KEY_NOTCH_OFFSET_X = "pref_notch_offset_x"
    const val KEY_NOTCH_OFFSET_Y = "pref_notch_offset_y"
    const val KEY_NOTCH_EXPANSION_WIDTH = "pref_notch_expansion_width"
    const val KEY_NOTCH_PADDING_SNUGNESS = "pref_notch_padding_snugness"
    const val KEY_NOTCH_CAPSULE_LAYOUT = "pref_notch_capsule_layout" // "unified_right", "dual_wing", "unified_left"
    const val KEY_NOTCH_TEST_BEACON = "pref_notch_test_beacon"

    // Hardware Volume Button Gesture Keys
    const val KEY_VOL_GESTURES_ENABLED = "pref_key_vol_gestures_enabled"
    const val KEY_CLEAN_VOLUME_SUPPRESSION = "pref_clean_volume_suppression"
    const val KEY_VOLUME_SUPPRESSION_PROFILE = "pref_volume_suppression_profile" // "instant_reflex", "balanced_holds", "total_clean"
    const val KEY_KEY_HOLD_AUTO_REPEAT = "pref_key_hold_auto_repeat"
    const val KEY_KEY_REPEAT_INTERVAL_MS = "pref_key_repeat_interval_ms"
    const val KEY_OEM_SHIELD_COMPLETED = "pref_oem_shield_completed"
    const val KEY_OEM_PRESERVE_SCREENSHOT = "pref_oem_preserve_screenshot"
    const val KEY_OEM_PRESERVE_ACCESSIBILITY = "pref_oem_preserve_accessibility"

    const val KEY_VOL_UP_LONG_PRESS = "pref_key_vol_up_long_press"
    const val KEY_VOL_DOWN_LONG_PRESS = "pref_key_vol_down_long_press"
    const val KEY_CHORD_DOWN_HOLD_UP_TAP = "pref_key_chord_down_hold_up_tap"
    const val KEY_CHORD_UP_HOLD_DOWN_TAP = "pref_key_chord_up_hold_down_tap"
    const val KEY_CHORD_DOWN_HOLD_UP_HOLD = "pref_key_chord_down_hold_up_hold"
    const val KEY_CHORD_UP_HOLD_DOWN_HOLD = "pref_key_chord_up_hold_down_hold"
    const val KEY_SEQ_UP_THEN_DOWN = "pref_key_seq_up_then_down"
    const val KEY_SEQ_DOWN_THEN_UP = "pref_key_seq_down_then_up"
    const val KEY_SEQ_DOWN_TAP_THEN_UP_HOLD = "pref_key_seq_down_tap_then_up_hold"
    const val KEY_SEQ_UP_TAP_THEN_DOWN_HOLD = "pref_key_seq_up_tap_then_down_hold"

    // Power Button Assist Remap Key
    const val KEY_POWER_LONG_PRESS_ACTION = "pref_power_long_press_action"

    // Back Tap Gesture Keys
    const val KEY_BACK_TAP_ENABLED = "pref_back_tap_enabled"
    const val KEY_BACK_TAP_SCOPE = "pref_back_tap_scope"
    const val KEY_BACK_TAP_THRESHOLD = "pref_back_tap_threshold"
    const val KEY_BACK_TAP_DOUBLE = "pref_back_tap_double"
    const val KEY_BACK_TAP_TRIPLE = "pref_back_tap_triple"

    // Status Bar & Notch Telemetry Keys
    const val KEY_TELEMETRY_DOWNLOADS_ROUTING = "pref_telemetry_downloads_routing"
    const val KEY_TELEMETRY_MEDIA_ROUTING = "pref_telemetry_media_routing"
    const val KEY_NOTCH_TEXT_SCROLL_MODE = "pref_notch_text_scroll_mode" // "infinite", "loop_1x", "loop_2x", "static"
    const val KEY_NOTCH_TEXT_TRUNCATE_ANCHOR = "pref_notch_text_truncate_anchor" // "tail", "head", "core"

    // Orientation & Display Suppression Keys
    const val KEY_ORIENTATION_OVERLAY_POLICY = "pref_orientation_overlay_policy" // "adaptive", "portrait_only"
    const val KEY_HIDE_ON_LOCKSCREEN_AND_DOCK = "pref_hide_on_lockscreen_and_dock"
    const val KEY_ORIENTATION_CONTEXT_GUARD_ENABLED = "pref_orientation_context_guard_enabled"
    const val KEY_SAVED_ACCEL_ROTATION = "pref_saved_accel_rotation"
    const val KEY_SAVED_USER_ROTATION = "pref_saved_user_rotation"

    // Refueling Bay Keys
    const val KEY_REFUELING_BAY_TRIGGER = "pref_refueling_bay_trigger" // "disabled", "charging_screen_off", "charging_dock_landscape", "screensaver_only"
    const val KEY_REFUELING_BATTERY_STYLE = "pref_refueling_battery_style" // "halo", "reactor_ticks", "dual_wings", "tachometer"
    const val KEY_REFUELING_SLEEP_TIMEOUT = "pref_refueling_sleep_timeout" // "30s", "60s", "3m", "5m", "never"
    const val KEY_REFUELING_AS_LOCKSCREEN = "pref_refueling_as_lockscreen" // Boolean
    const val KEY_REFUELING_WIDGET_ID = "pref_refueling_widget_id" // Legacy single ID
    const val KEY_REFUELING_WIDGET_IDS = "pref_refueling_widget_ids" // JSON array string of widget IDs
    const val KEY_REFUELING_STACK_PROFILE = "pref_refueling_stack_profile" // JSON array string of Stack widget IDs
    const val KEY_REFUELING_GRID_PROFILE = "pref_refueling_grid_profile" // JSON array string of Grid widget IDs
    const val KEY_REFUELING_WIDGET_LAYOUT = "pref_refueling_widget_layout" // "smart_stack", "adaptive_grid"

    // Notch Orbital Capsule Keys
    const val KEY_CAPSULE_WIDGET_ID = "pref_capsule_widget_id"

    // Media Actions Keys
    const val KEY_MEDIA_SKIP_SECONDS = "pref_media_skip_seconds"

    fun getRefuelingWidgetIds(context: Context): List<Int> {
        val prefs = context.defaultPrefs()
        val mode = prefs.getString(KEY_REFUELING_WIDGET_LAYOUT, "smart_stack") ?: "smart_stack"
        return if (mode == "smart_stack") {
            getRefuelingStackWidgetIds(context)
        } else {
            getRefuelingGridWidgetIds(context)
        }
    }

    fun getRefuelingStackWidgetIds(context: Context): List<Int> {
        val prefs = context.defaultPrefs()
        val jsonStr = prefs.getString(KEY_REFUELING_STACK_PROFILE, null)
            ?: prefs.getString(KEY_REFUELING_WIDGET_IDS, null)
        if (!jsonStr.isNullOrBlank()) {
            try {
                val jsonArray = org.json.JSONArray(jsonStr)
                val list = mutableListOf<Int>()
                for (i in 0 until jsonArray.length()) {
                    val id = jsonArray.getInt(i)
                    if (id != -1 && !list.contains(id)) {
                        list.add(id)
                    }
                }
                return list
            } catch (_: Exception) {}
        }
        val legacyId = prefs.getInt(KEY_REFUELING_WIDGET_ID, -1)
        return if (legacyId != -1) listOf(legacyId) else emptyList()
    }

    fun saveRefuelingStackWidgetIds(context: Context, ids: List<Int>) {
        val prefs = context.defaultPrefs()
        val jsonArray = org.json.JSONArray()
        ids.forEach { jsonArray.put(it) }
        prefs.edit()
            .putString(KEY_REFUELING_STACK_PROFILE, jsonArray.toString())
            .putString(KEY_REFUELING_WIDGET_IDS, jsonArray.toString())
            .putInt(KEY_REFUELING_WIDGET_ID, ids.firstOrNull() ?: -1)
            .apply()
    }

    fun getRefuelingGridWidgetIds(context: Context): List<Int> {
        val prefs = context.defaultPrefs()
        val jsonStr = prefs.getString(KEY_REFUELING_GRID_PROFILE, null)
            ?: prefs.getString(KEY_REFUELING_WIDGET_IDS, null)
        if (!jsonStr.isNullOrBlank()) {
            try {
                val jsonArray = org.json.JSONArray(jsonStr)
                val list = mutableListOf<Int>()
                for (i in 0 until jsonArray.length()) {
                    val id = jsonArray.getInt(i)
                    if (id != -1 && !list.contains(id)) {
                        list.add(id)
                    }
                }
                return list
            } catch (_: Exception) {}
        }
        val legacyId = prefs.getInt(KEY_REFUELING_WIDGET_ID, -1)
        return if (legacyId != -1) listOf(legacyId) else emptyList()
    }

    fun saveRefuelingGridWidgetIds(context: Context, ids: List<Int>) {
        val prefs = context.defaultPrefs()
        val jsonArray = org.json.JSONArray()
        ids.forEach { jsonArray.put(it) }
        prefs.edit()
            .putString(KEY_REFUELING_GRID_PROFILE, jsonArray.toString())
            .putString(KEY_REFUELING_WIDGET_IDS, jsonArray.toString())
            .putInt(KEY_REFUELING_WIDGET_ID, ids.firstOrNull() ?: -1)
            .apply()
    }

    fun saveRefuelingWidgetIds(context: Context, ids: List<Int>) {
        val prefs = context.defaultPrefs()
        val mode = prefs.getString(KEY_REFUELING_WIDGET_LAYOUT, "smart_stack") ?: "smart_stack"
        if (mode == "smart_stack") {
            saveRefuelingStackWidgetIds(context, ids)
        } else {
            saveRefuelingGridWidgetIds(context, ids)
        }
    }
}

/**
 * Extension function on Context to get the default Lightspeed SharedPreferences instance.
 */
fun Context.defaultPrefs(): SharedPreferences =
    getSharedPreferences(LightspeedPreferences.PREFS_FILE, Context.MODE_PRIVATE)
