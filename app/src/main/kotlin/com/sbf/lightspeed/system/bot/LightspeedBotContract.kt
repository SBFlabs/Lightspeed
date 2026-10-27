package com.sbf.lightspeed.system.bot

/**
 * Constants and Preference Keys for Lightspeed Clean-Room Bots and Cybernetic Trackpad.
 */
object LightspeedBotContract {
    const val PREF_BOT_MACRO_PREFIX = "pref_bot_macro_"

    // Master / Flank Enables
    const val KEY_BOT_TRACKPAD_LABS_ENABLED = "pref_bot_trackpad_labs_enabled"
    const val KEY_BOT_TRACKPAD_ENABLED = "pref_bot_trackpad_enabled"
    const val KEY_BOT_TRACKPAD_LEFT_ENABLED = "pref_bot_trackpad_left_enabled"
    const val KEY_BOT_TRACKPAD_RIGHT_ENABLED = "pref_bot_trackpad_right_enabled"

    fun isLabsEnabled(prefs: android.content.SharedPreferences): Boolean {
        val defaultVal = prefs.getBoolean(KEY_BOT_TRACKPAD_LEFT_ENABLED, false) ||
                prefs.getBoolean(KEY_BOT_TRACKPAD_RIGHT_ENABLED, false)
        return prefs.getBoolean(KEY_BOT_TRACKPAD_LABS_ENABLED, defaultVal)
    }
    const val KEY_BOT_TRACKPAD_PREVIEW_LEFT = "pref_bot_trackpad_preview_left"
    const val KEY_BOT_TRACKPAD_PREVIEW_RIGHT = "pref_bot_trackpad_preview_right"

    // Flank Geometry & Positioning
    const val KEY_BOT_TRACKPAD_POS_Y_LEFT = "pref_bot_trackpad_pos_y_left" // 10..90 percent (default 78)
    const val KEY_BOT_TRACKPAD_POS_Y_RIGHT = "pref_bot_trackpad_pos_y_right" // 10..90 percent (default 78)
    const val KEY_BOT_TRACKPAD_HEIGHT_LEFT = "pref_bot_trackpad_height_left" // 60..320 dp (default 120)
    const val KEY_BOT_TRACKPAD_HEIGHT_RIGHT = "pref_bot_trackpad_height_right" // 60..320 dp (default 120)
    const val KEY_BOT_TRACKPAD_TOUCH_WIDTH_LEFT = "pref_bot_trackpad_touch_width_left" // 8..32 dp (default 14)
    const val KEY_BOT_TRACKPAD_TOUCH_WIDTH_RIGHT = "pref_bot_trackpad_touch_width_right" // 8..32 dp (default 14)
    const val KEY_BOT_TRACKPAD_TRIGGER_OPACITY_LEFT = "pref_bot_trackpad_trigger_opacity_left" // 0..100 percent (default 50)
    const val KEY_BOT_TRACKPAD_TRIGGER_OPACITY_RIGHT = "pref_bot_trackpad_trigger_opacity_right" // 0..100 percent (default 50)

    // Robot Arms & Half-Circle D-Pad
    const val KEY_BOT_TRACKPAD_ARMS_LEFT = "pref_bot_trackpad_arms_left" // 1..3 (default 3)
    const val KEY_BOT_TRACKPAD_ARMS_RIGHT = "pref_bot_trackpad_arms_right" // 1..3 (default 3)
    const val KEY_BOT_TRACKPAD_STYLE_LEFT = "pref_bot_trackpad_style_left" // style id
    const val KEY_BOT_TRACKPAD_STYLE_RIGHT = "pref_bot_trackpad_style_right" // style id
    const val KEY_BOT_TRACKPAD_ORIENTATION_LEFT = "pref_bot_trackpad_orientation_left" // "arc" or "edge"
    const val KEY_BOT_TRACKPAD_ORIENTATION_RIGHT = "pref_bot_trackpad_orientation_right" // "arc" or "edge"
    const val KEY_BOT_TRACKPAD_RADIUS_LEFT = "pref_bot_trackpad_radius_left" // 50..110 dp (default 72)
    const val KEY_BOT_TRACKPAD_RADIUS_RIGHT = "pref_bot_trackpad_radius_right" // 50..110 dp (default 72)
    const val KEY_BOT_TRACKPAD_ARM_REACH_LEFT = "pref_bot_trackpad_arm_reach_left" // 36..80 dp (default 52)
    const val KEY_BOT_TRACKPAD_ARM_REACH_RIGHT = "pref_bot_trackpad_arm_reach_right" // 36..80 dp (default 52)
    const val KEY_BOT_TRACKPAD_DPAD_OPACITY_LEFT = "pref_bot_trackpad_dpad_opacity_left" // 10..100 percent (default 100)
    const val KEY_BOT_TRACKPAD_DPAD_OPACITY_RIGHT = "pref_bot_trackpad_dpad_opacity_right" // 10..100 percent (default 100)
    const val KEY_BOT_TRACKPAD_ARMS_OPACITY_LEFT = "pref_bot_trackpad_arms_opacity_left" // 10..100 percent (default 100)
    const val KEY_BOT_TRACKPAD_ARMS_OPACITY_RIGHT = "pref_bot_trackpad_arms_opacity_right" // 10..100 percent (default 100)
    const val KEY_BOT_TRACKPAD_RADIUS_DP = "pref_bot_trackpad_radius_dp" // legacy fallback (default 72dp)
    const val KEY_BOT_TRACKPAD_LINGER_MS = "pref_bot_trackpad_linger_ms" // default 1800ms (800..3500)
    const val KEY_BOT_TRACKPAD_SENSITIVITY = "pref_bot_trackpad_sensitivity" // default 1.3f (0.5..2.5)
    const val KEY_BOT_CURSOR_SHAPE = "pref_bot_cursor_shape" // "crosshair", "pointer", "ring", "diamond", "dot"

    // Active Bot & Automation
    const val KEY_BOT_TRACKPAD_ACTIVE_MACRO = "pref_bot_trackpad_active_macro"
    const val KEY_BOT_ACTIVE_LOOP_CADENCE_MS = "pref_bot_active_loop_cadence_ms"
    const val KEY_BOT_TRACKPAD_SIZE_DP = "pref_bot_trackpad_size_dp"

    const val MAX_BOT_MACROS = 10
}
