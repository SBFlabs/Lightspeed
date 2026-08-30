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

    // Icon Pack Keys
    const val KEY_ACTIVE_ICON_PACK = "pref_active_icon_pack"

    // Symmetry & Coupling Keys
    const val KEY_SYMMETRY_GEOMETRY_MODE = "pref_symmetry_geometry_mode"
    const val KEY_SYMMETRY_GESTURE_MODE = "pref_symmetry_gesture_mode"
    const val KEY_SIDEBAR_RIGHT_LINK_FLANK = "pref_sidebar_right_link_flank_actions"
    const val KEY_SIDEBAR_LEFT_LINK_FLANK = "pref_sidebar_left_link_flank_actions"

    // Hardware Volume Button Gesture Keys
    const val KEY_VOL_GESTURES_ENABLED = "pref_key_vol_gestures_enabled"
    const val KEY_CLEAN_VOLUME_SUPPRESSION = "pref_clean_volume_suppression"
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

    // Back Tap Gesture Keys
    const val KEY_BACK_TAP_ENABLED = "pref_back_tap_enabled"
    const val KEY_BACK_TAP_SCOPE = "pref_back_tap_scope"
    const val KEY_BACK_TAP_DOUBLE = "pref_back_tap_double"
    const val KEY_BACK_TAP_TRIPLE = "pref_back_tap_triple"

    // Status Bar & Notch Telemetry Keys
    const val KEY_TELEMETRY_DOWNLOADS_ROUTING = "pref_telemetry_downloads_routing"
    const val KEY_TELEMETRY_MEDIA_ROUTING = "pref_telemetry_media_routing"

    // Media Actions Keys
    const val KEY_MEDIA_SKIP_SECONDS = "pref_media_skip_seconds"
}

/**
 * Extension function on Context to get the default Lightspeed SharedPreferences instance.
 */
fun Context.defaultPrefs(): SharedPreferences =
    getSharedPreferences(LightspeedPreferences.PREFS_FILE, Context.MODE_PRIVATE)
