package com.sbf.lightspeed.system

import android.content.Context
import android.content.SharedPreferences

/**
 * Standardized SharedPreferences constants and extension accessors for Lightspeed.
 */
object LightspeedPreferences {
    const val PREFS_FILE = "default"

    // Master Flight Deck & Deflector Keys
    const val KEY_MASTER_FLIGHT_ARMED = "pref_master_flight_armed"
    const val KEY_FLIGHT_NOTIFICATION_ENABLED = "pref_flight_notification_enabled"
    const val KEY_DEFLECTOR_LEFT_ENABLED = "pref_deflector_left_enabled"
    const val KEY_DEFLECTOR_RIGHT_ENABLED = "pref_deflector_right_enabled"
    const val KEY_DEFLECTOR_DEFAULT_STATE = "pref_deflector_default_state" // "always_armed", "standby_by_default"

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

    // Power Button Engine Keys (4 Hardware Trigger States)
    const val KEY_POWER_GESTURES_ENABLED = "pref_key_power_gestures_enabled"
    const val KEY_POWER_SINGLE_PRESS = "pref_key_power_single_press"
    const val KEY_POWER_DOUBLE_PRESS = "pref_key_power_double_press"
    const val KEY_POWER_HOLD = "pref_key_power_hold"
    const val KEY_POWER_PRESS_THEN_HOLD = "pref_key_power_press_then_hold"
    const val KEY_POWER_LONG_PRESS_ACTION = "pref_power_long_press_action" // Legacy compatibility alias

    // Action Tokens
    const val ACTION_TACTICAL_FLYOUT = "system:tactical_flyout"
    const val ACTION_TACTICAL_AUDIO = "system:tactical_audio"
    const val ACTION_LENS = "system:lens"
    const val ACTION_QR_SCANNER = "system:qr_scanner"
    const val ACTION_CHATGPT = "system:chatgpt"
    const val ACTION_CLAUDE = "system:claude"
    const val ACTION_GEMINI = "system:gemini"
    const val ACTION_CAMERA_PHOTO = "system:camera_photo"
    const val ACTION_CAMERA_VIDEO = "system:camera_video"
    const val ACTION_FOLAX = "system:folax"
    const val ACTION_CORE_COOLING = "system:core_cooling"
    const val ACTION_TORCH = "system:torch"

    // Power Button Safety & Experimental Labs Keys
    const val KEY_POWER_SINGLE_PRESS_UNLOCKED = "pref_power_single_press_unlocked"
    const val KEY_SUPPRESS_DEEP_ACTIVITY_WARNING = "pref_suppress_deep_activity_warning"
    const val KEY_PICKER_PINNED_APPS = "pref_picker_pinned_apps"
    const val KEY_SECTION_EXPERIMENTAL_LABS_EXPANDED = "pref_section_experimental_labs_expanded"
    const val KEY_CORE_COOLING_ENABLED = "pref_core_cooling_enabled"
    const val KEY_CORE_COOLING_SCHEDULE = "pref_core_cooling_schedule"
    const val KEY_CORE_COOLING_DAY_OF_WEEK = "pref_core_cooling_day_of_week"
    const val KEY_CORE_COOLING_HOUR = "pref_core_cooling_hour"
    const val KEY_CORE_COOLING_LAST_TRIGGER = "pref_core_cooling_last_trigger"
    const val KEY_INFINIX_STANDBY_DISMISSED = "pref_infinix_standby_dismissed"

    // Tab Accordion Display Profiles & Blueprint Keys
    const val KEY_TAB_ACCORDION_MODE_0 = "pref_tab_accordion_mode_0" // "all_expanded", "all_collapsed", "solo", "custom_pinned"
    const val KEY_TAB_PINNED_ACCORDION_0 = "pref_tab_pinned_accordion_0"
    const val KEY_TAB_SECTION_ORDER_0 = "pref_tab_section_order_0"

    const val KEY_TAB_ACCORDION_MODE_1 = "pref_tab_accordion_mode_1"
    const val KEY_TAB_PINNED_ACCORDION_1 = "pref_tab_pinned_accordion_1"
    const val KEY_TAB_SECTION_ORDER_1 = "pref_tab_section_order_1"

    const val KEY_TAB_ACCORDION_MODE_2 = "pref_tab_accordion_mode_2"
    const val KEY_TAB_PINNED_ACCORDION_2 = "pref_tab_pinned_accordion_2"
    const val KEY_TAB_SECTION_ORDER_2 = "pref_tab_section_order_2"

    // Back Tap Gesture Keys
    const val KEY_BACK_TAP_ENABLED = "pref_back_tap_enabled"
    const val KEY_BACK_TAP_SCOPE = "pref_back_tap_scope"
    const val KEY_BACK_TAP_THRESHOLD = "pref_back_tap_threshold"
    const val KEY_BACK_TAP_DOUBLE = "pref_back_tap_double"
    const val KEY_BACK_TAP_TRIPLE = "pref_back_tap_triple"

    // Status Bar & Notch Telemetry Keys
    const val KEY_RAIL_DOWNLOADS_ROUTING = "pref_rail_downloads_routing"
    const val KEY_RAIL_MEDIA_ROUTING = "pref_rail_media_routing"
    const val KEY_HORIZON_RAIL_ORIENTATION_MODE = "pref_horizon_rail_orientation_mode" // "both", "landscape_only", "portrait_only"
    const val KEY_HORIZON_RAIL_SPAN = "pref_horizon_rail_span"
    const val KEY_HORIZON_RAIL_ALIGN = "pref_horizon_rail_align"
    const val KEY_HORIZON_RAIL_OFFSET_X = "pref_horizon_rail_offset_x"
    const val KEY_HORIZON_RAIL_OFFSET_Y = "pref_horizon_rail_offset_y"
    const val KEY_HORIZON_RAIL_SAFE_PADDING_LEFT = "pref_horizon_rail_safe_padding_left"
    const val KEY_HORIZON_RAIL_SAFE_PADDING_RIGHT = "pref_horizon_rail_safe_padding_right"
    const val KEY_HORIZON_RAIL_STATUS_BAR_GUARD = "pref_horizon_rail_status_bar_guard"
    const val KEY_HORIZON_RAIL_THICKNESS = "pref_horizon_rail_thickness"
    const val KEY_HORIZON_RAIL_STACK_SPACING = "pref_horizon_rail_stack_spacing" // inter-rail gap: 0 to 6dp (0 = laminated stack)
    const val KEY_HORIZON_RAIL_DROP_SHADOW = "pref_horizon_rail_drop_shadow" // contrast ambient shadow trench
    const val KEY_HORIZON_RAIL_TERMINAL_CAPS = "pref_horizon_rail_terminal_caps" // high-contrast progress head markers
    const val KEY_HORIZON_RAIL_GLOW = "pref_horizon_rail_glow"
    const val KEY_HORIZON_RAIL_TRACK_OPACITY = "pref_horizon_rail_track_opacity"
    const val KEY_HORIZON_RAIL_COLOR_MODE = "pref_horizon_rail_color_mode" // "cover_art", "app_icon", "material3", "inverted", "custom"
    const val KEY_HORIZON_RAIL_CUSTOM_COLOR = "pref_horizon_rail_custom_color"
    const val KEY_HORIZON_RAIL_TEXT_ENABLED = "pref_horizon_rail_text_enabled"
    const val KEY_HORIZON_RAIL_TEXT_ORIENTATION_MODE = "pref_horizon_rail_text_orientation_mode" // "both", "landscape_only", "portrait_only"
    const val KEY_HORIZON_RAIL_TEXT_METADATA_MODE = "pref_horizon_rail_text_metadata_mode" // "adaptive", "full", "title_only"
    const val KEY_HORIZON_RAIL_TEXT_SHOW_TIMESTAMP = "pref_horizon_rail_text_show_timestamp"
    const val KEY_HORIZON_RAIL_TEXT_CASING = "pref_horizon_rail_text_casing" // "natural", "all_caps", "title_case"
    const val KEY_HORIZON_RAIL_TEXT_FONT = "pref_horizon_rail_text_font" // "system_default", "sans-serif-condensed", "monospace", etc.
    const val KEY_HORIZON_RAIL_TEXT_SIZE = "pref_horizon_rail_text_size"
    const val KEY_HORIZON_RAIL_TEXT_POSITION = "pref_horizon_rail_text_position" // "below", "above", "embedded", "below_statusbar"
    const val KEY_HORIZON_RAIL_TEXT_OFFSET_Y = "pref_horizon_rail_text_offset_y"
    const val KEY_HORIZON_RAIL_TEXT_SPEED = "pref_horizon_rail_text_speed"
    const val KEY_HORIZON_RAIL_MARQUEE_SCOPE = "pref_horizon_rail_marquee_scope" // "both_wings", "right_wing_only", "left_wing_only", "unified"
    const val KEY_HORIZON_RAIL_MARQUEE_ANIM_MODE = "pref_horizon_rail_marquee_anim_mode" // "continuous_wrap", "bounce"
    const val KEY_HORIZON_RAIL_MARQUEE_DIRECTION = "pref_horizon_rail_marquee_direction" // "rtl", "ltr"
    const val KEY_HORIZON_RAIL_PRIORITY = "pref_horizon_rail_priority" // "downloads_top", "media_top", "most_recent"
    const val KEY_HORIZON_RAIL_MAX_COUNT = "pref_horizon_rail_max_count"
    const val KEY_HORIZON_RAIL_AVOID_CUTOUT = "pref_horizon_rail_avoid_cutout"
    const val KEY_HORIZON_RAIL_CUTOUT_PADDING = "pref_horizon_rail_cutout_padding"
    const val KEY_HORIZON_RAIL_CUTOUT_WIDTH = "pref_horizon_rail_cutout_width"
    const val KEY_HORIZON_RAIL_CUTOUT_OFFSET_X = "pref_horizon_rail_cutout_offset_x"
    const val KEY_HORIZON_RAIL_CONTRAST_SHIELD = "pref_horizon_rail_contrast_shield"
    const val KEY_HORIZON_RAIL_PREVIEW = "pref_horizon_rail_preview"

    // Shared Hardware Cutout & Punch-Hole Calibration Keys
    const val KEY_HARDWARE_CUTOUT_WIDTH = "pref_hardware_cutout_width" // shared physical hole width (0 to 60dp)
    const val KEY_HARDWARE_CUTOUT_OFFSET_X = "pref_hardware_cutout_offset_x" // shared horizontal alignment (-30 to +30dp)
    const val KEY_HARDWARE_CUTOUT_OFFSET_Y = "pref_hardware_cutout_offset_y" // shared vertical alignment (-30 to +30dp)

    fun getEffectiveCutoutWidth(prefs: android.content.SharedPreferences, defaultDp: Int): Int {
        if (prefs.contains(KEY_HARDWARE_CUTOUT_WIDTH)) {
            return prefs.getInt(KEY_HARDWARE_CUTOUT_WIDTH, defaultDp)
        }
        return prefs.getInt(KEY_HORIZON_RAIL_CUTOUT_WIDTH, defaultDp)
    }

    fun getEffectiveCutoutOffsetX(prefs: android.content.SharedPreferences): Int {
        if (prefs.contains(KEY_HARDWARE_CUTOUT_OFFSET_X)) {
            return prefs.getInt(KEY_HARDWARE_CUTOUT_OFFSET_X, 0)
        }
        val legacyNotch = prefs.getInt(KEY_NOTCH_OFFSET_X, 0)
        if (legacyNotch != 0) return legacyNotch
        return prefs.getInt(KEY_HORIZON_RAIL_CUTOUT_OFFSET_X, 0)
    }

    fun getEffectiveCutoutOffsetY(prefs: android.content.SharedPreferences): Int {
        if (prefs.contains(KEY_HARDWARE_CUTOUT_OFFSET_Y)) {
            return prefs.getInt(KEY_HARDWARE_CUTOUT_OFFSET_Y, 0)
        }
        return prefs.getInt(KEY_NOTCH_OFFSET_Y, 0)
    }
    const val KEY_NOTCH_CAPSULE_ORIENTATION_MODE = "pref_notch_capsule_orientation_mode" // "both", "portrait_only", "landscape_only"
    const val KEY_NOTCH_MARQUEE_ENABLED = "pref_notch_marquee_enabled"
    const val KEY_NOTCH_MARQUEE_SPEED = "pref_notch_marquee_speed"
    const val KEY_NOTCH_MARQUEE_INITIAL_DELAY = "pref_notch_marquee_initial_delay"
    const val KEY_NOTCH_MAX_CAPSULE_WIDTH = "pref_notch_max_capsule_width"
    const val KEY_NOTCH_TEXT_SCROLL_MODE = "pref_notch_text_scroll_mode" // "infinite", "loop_1x", "loop_2x", "static"
    const val KEY_NOTCH_TEXT_TRUNCATE_ANCHOR = "pref_notch_text_truncate_anchor" // "tail", "head", "core"

    // Orientation & Display Suppression Keys
    const val KEY_ORIENTATION_OVERLAY_POLICY = "pref_orientation_overlay_policy" // "adaptive", "portrait_only"
    const val KEY_HIDE_ON_LOCKSCREEN_AND_DOCK = "pref_hide_on_lockscreen_and_dock"
    const val KEY_ORIENTATION_CONTEXT_GUARD_ENABLED = "pref_orientation_context_guard_enabled"
    const val KEY_SAVED_ACCEL_ROTATION = "pref_saved_accel_rotation"
    const val KEY_SAVED_USER_ROTATION = "pref_saved_user_rotation"
    const val KEY_ATTITUDE_BUCKET_STRICT_PORTRAIT = "pref_attitude_bucket_strict_portrait"
    const val KEY_ATTITUDE_BUCKET_SENSOR_PORTRAIT = "pref_attitude_bucket_sensor_portrait"
    const val KEY_ATTITUDE_BUCKET_SENSOR_LANDSCAPE = "pref_attitude_bucket_sensor_landscape"
    const val KEY_ATTITUDE_BUCKET_SENSOR_360 = "pref_attitude_bucket_sensor_360"
    const val KEY_ACCESSIBILITY_SENTINEL_ENABLED = "pref_accessibility_sentinel_enabled"

    // Refueling Bay Keys
    const val KEY_REFUELING_BAY_TRIGGER = "pref_refueling_bay_trigger" // "disabled", "charging_screen_off", "charging_dock_landscape", "screen_timeout", "screensaver_only"
    const val KEY_REFUELING_BATTERY_STYLE = "pref_refueling_battery_style" // "halo", "reactor_ticks", "dual_wings", "tachometer"
    const val KEY_REFUELING_SLEEP_TIMEOUT = "pref_refueling_sleep_timeout" // "5s", "15s", "30s", "60s", "120s", "300s", "never"
    const val KEY_REFUELING_PIXEL_SHIFT = "pref_refueling_pixel_shift"
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

    fun isMasterFlightArmed(context: Context): Boolean =
        context.defaultPrefs().getBoolean(KEY_MASTER_FLIGHT_ARMED, true)

    fun setMasterFlightArmed(context: Context, armed: Boolean) {
        context.defaultPrefs().edit().putBoolean(KEY_MASTER_FLIGHT_ARMED, armed).apply()
    }

    fun isLeftDeflectorEnabled(context: Context): Boolean =
        context.defaultPrefs().getBoolean(KEY_DEFLECTOR_LEFT_ENABLED, true)

    fun setLeftDeflectorEnabled(context: Context, enabled: Boolean) {
        context.defaultPrefs().edit().putBoolean(KEY_DEFLECTOR_LEFT_ENABLED, enabled).apply()
    }

    fun isRightDeflectorEnabled(context: Context): Boolean =
        context.defaultPrefs().getBoolean(KEY_DEFLECTOR_RIGHT_ENABLED, true)

    fun setRightDeflectorEnabled(context: Context, enabled: Boolean) {
        context.defaultPrefs().edit().putBoolean(KEY_DEFLECTOR_RIGHT_ENABLED, enabled).apply()
    }

    fun isFlightNotificationEnabled(context: Context): Boolean =
        context.defaultPrefs().getBoolean(KEY_FLIGHT_NOTIFICATION_ENABLED, false)

    fun setFlightNotificationEnabled(context: Context, enabled: Boolean) {
        context.defaultPrefs().edit().putBoolean(KEY_FLIGHT_NOTIFICATION_ENABLED, enabled).apply()
    }
}

/**
 * Extension function on Context to get the default Lightspeed SharedPreferences instance.
 */
fun Context.defaultPrefs(): SharedPreferences =
    getSharedPreferences(LightspeedPreferences.PREFS_FILE, Context.MODE_PRIVATE)
