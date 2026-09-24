package com.sbf.lightspeed.system

import android.content.Context
import android.content.SharedPreferences

typealias LiquidGlassConfig = LightspeedDeckStyleManager.LiquidGlassConfig

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
    const val KEY_DEFLECTOR_GLOW_ENABLED = "pref_deflector_glow_enabled" // Boolean, default true (master fallback)
    const val KEY_DEFLECTOR_LEFT_GLOW_ENABLED = "pref_deflector_left_glow_enabled" // Boolean, default true
    const val KEY_DEFLECTOR_RIGHT_GLOW_ENABLED = "pref_deflector_right_glow_enabled" // Boolean, default true
    const val KEY_DEFLECTOR_USE_M3_COLOR = "pref_deflector_use_m3_color" // Boolean, default true
    const val KEY_DEFLECTOR_GLOW_STYLE = "pref_deflector_glow_style" // "progressive_frost", "material_shade", "crimson_reactor", "cyber_plasma"
    const val KEY_DEFLECTOR_LEFT_PILL_STYLE = "pref_deflector_left_pill_style"
    const val KEY_DEFLECTOR_RIGHT_PILL_STYLE = "pref_deflector_right_pill_style"
    const val KEY_DEFLECTOR_PILL_STYLE = "pref_deflector_pill_style" // "anchored_glow", "floating_smart_pill", "neon_core", "razor_edge", "kinetic_elastic", "hollow_ghost"
    const val KEY_DEFLECTOR_GLOW_ON_GESTURE_STEP = "pref_deflector_glow_on_gesture_step" // Boolean, default true
    const val KEY_DEFLECTOR_GLOW_DURATION = "pref_deflector_glow_duration" // "800ms", "1500ms", "2200ms"
    const val KEY_CENTRAL_COMMAND_LONG_PRESS_ACTION = "pref_central_command_long_press_action" // "toggle_master_flight", "toggle_all_deflectors", "toggle_left_deflector", "toggle_right_deflector"

    // Status Bar Keys
    const val KEY_STATUSBAR_ENABLED = "pref_statusbar_enabled"
    const val KEY_STATUSBAR_SPAN = "pref_statusbar_span"
    const val KEY_STATUSBAR_SPAN_LANDSCAPE = "pref_statusbar_span_landscape"
    const val KEY_STATUSBAR_THICKNESS = "pref_statusbar_thickness"
    const val KEY_STATUSBAR_THICKNESS_LANDSCAPE = "pref_statusbar_thickness_landscape"
    const val KEY_STATUSBAR_OFFSET_X = "pref_statusbar_offset_x"
    const val KEY_STATUSBAR_OFFSET_X_LANDSCAPE = "pref_statusbar_offset_x_landscape"
    const val KEY_STATUSBAR_OFFSET_Y = "pref_statusbar_offset_y"
    const val KEY_STATUSBAR_SENSITIVITY = "pref_statusbar_sensitivity"
    const val KEY_STATUSBAR_TRANSPARENCY = "pref_statusbar_transparency"

    // Cockpit & Gears Keys
    const val KEY_GEAR_SETS_ORDER = "gear_sets_order"
    const val KEY_COCKPIT_LAUNCH_BEHAVIOR = "cockpit_launch_behavior"
    const val KEY_GEAR_PHYSICS_PROFILE = "pref_gear_physics_profile"
    const val KEY_GEAR_HAPTIC_STRENGTH = "pref_gear_haptic_strength"
    const val KEY_LAST_ACTIVE_SET_INDEX = "last_active_set_index"
    const val KEY_LAST_ACTIVE_SET_INDEX_LEFT = "last_active_set_index_left"
    const val KEY_LAST_ACTIVE_SET_INDEX_RIGHT = "last_active_set_index_right"

    // Icon Pack & Pipeline Keys
    const val KEY_ACTIVE_ICON_PACK = "pref_active_icon_pack" // Legacy key for backward compatibility
    const val KEY_CATEGORY_CRUISE_ICON_PACK = "pref_category_cruise_icon_pack" // Meta-category glyph tokens
    const val KEY_CATEGORY_CRUISE_ICON_STYLE = "pref_category_cruise_icon_style" // Meta-category icon styling
    const val KEY_COCKPIT_ICON_PACK = "pref_cockpit_icon_pack" // Target app icon pack (default: "system")
    const val KEY_COCKPIT_ICON_RENDER_MODE = "pref_cockpit_icon_render_mode" // "pack_native", "adaptive_squircle", "original"

    // Cockpit Reticle Targeting Orientation Keys
    const val KEY_COCKPIT_RETICLE_ORIENTATION = "pref_cockpit_reticle_orientation"
    const val RETICLE_ORIENTATION_CLASSIC_180 = "classic_180"
    const val RETICLE_ORIENTATION_MIRRORED_FLANK = "mirrored_flank"

    // Deflector Landscape Geometry Keys
    const val KEY_DEFLECTOR_LANDSCAPE_MODE = "pref_deflector_landscape_mode" // "auto", "custom", "fixed"
    const val DEFLECTOR_LANDSCAPE_MODE_AUTO = "auto"
    const val DEFLECTOR_LANDSCAPE_MODE_CUSTOM = "custom"
    const val DEFLECTOR_LANDSCAPE_MODE_FIXED = "fixed"
    const val KEY_DEFLECTOR_LANDSCAPE_SCALE = "pref_deflector_landscape_scale" // Int percent (20-100), default: 45
    const val KEY_DEFLECTOR_LANDSCAPE_Y_SCALE = "pref_deflector_landscape_y_scale" // Int percent (0-100), default: 50

    // Symmetry & Coupling Keys
    const val KEY_SYMMETRY_GEOMETRY_MODE = "pref_symmetry_geometry_mode"
    const val KEY_SYMMETRY_GESTURE_MODE = "pref_symmetry_gesture_mode"
    const val KEY_SIDEBAR_RIGHT_LINK_FLANK = "pref_sidebar_right_link_flank_actions"
    const val KEY_SIDEBAR_LEFT_LINK_FLANK = "pref_sidebar_left_link_flank_actions"
    const val KEY_SIDEBAR_RIGHT_LINK_FLANK_ACTIONS = KEY_SIDEBAR_RIGHT_LINK_FLANK
    const val KEY_SIDEBAR_LEFT_LINK_FLANK_ACTIONS = KEY_SIDEBAR_LEFT_LINK_FLANK
    const val KEY_SIDEBAR_LINK_EDGES = "pref_sidebar_link_edges"
    const val KEY_SIDEBAR_LEFT_PREVIEW = "pref_sidebar_left_preview"
    const val KEY_SIDEBAR_RIGHT_PREVIEW = "pref_sidebar_preview"
    const val KEY_STATUSBAR_PREVIEW = "pref_statusbar_preview"
    const val KEY_SIDEBAR_CENTER_TRANSPARENCY = "pref_sidebar_center_transparency"
    const val KEY_SIDEBAR_LEFT_CENTER_TRANSPARENCY = "pref_sidebar_left_center_transparency"
    const val KEY_SIDEBAR_TOP_TRANSPARENCY = "pref_sidebar_top_transparency"
    const val KEY_SIDEBAR_LEFT_TOP_TRANSPARENCY = "pref_sidebar_left_top_transparency"
    const val KEY_SIDEBAR_BOTTOM_TRANSPARENCY = "pref_sidebar_bottom_transparency"
    const val KEY_SIDEBAR_LEFT_BOTTOM_TRANSPARENCY = "pref_sidebar_left_bottom_transparency"
    const val KEY_UNIFIED_SCRUB_REGIONS_LINKED = "pref_unified_scrub_regions_linked"
    const val KEY_UNIFIED_SCRUB_REGIONS_LINKED_RIGHT = "pref_unified_scrub_regions_linked_right"
    const val KEY_UNIFIED_SCRUB_REGIONS_LINKED_LEFT = "pref_unified_scrub_regions_linked_left"

    fun isScrubRegionsLinked(context: Context, isLeft: Boolean): Boolean {
        val prefs = context.defaultPrefs()
        val key = if (isLeft) KEY_UNIFIED_SCRUB_REGIONS_LINKED_LEFT else KEY_UNIFIED_SCRUB_REGIONS_LINKED_RIGHT
        return prefs.getBoolean(key, prefs.getBoolean(KEY_UNIFIED_SCRUB_REGIONS_LINKED, false))
    }

    fun setScrubRegionsLinked(context: Context, isLeft: Boolean, linked: Boolean) {
        val prefs = context.defaultPrefs()
        val key = if (isLeft) KEY_UNIFIED_SCRUB_REGIONS_LINKED_LEFT else KEY_UNIFIED_SCRUB_REGIONS_LINKED_RIGHT
        prefs.edit().putBoolean(key, linked).apply()
    }

    fun getGestureUnifiedKey(isLeft: Boolean, gestureKey: String): String {
        val flank = if (isLeft) "left" else "right"
        return "pref_gesture_unified_${flank}_${gestureKey}"
    }

    const val KEY_DEFLECTOR_LONG_SWIPE_THRESHOLD_PORTRAIT = "pref_deflector_long_swipe_threshold_portrait"
    const val KEY_DEFLECTOR_LONG_SWIPE_THRESHOLD_LANDSCAPE = "pref_deflector_long_swipe_threshold_landscape"
    const val DEFAULT_DEFLECTOR_LONG_SWIPE_THRESHOLD_PORTRAIT = 90
    const val DEFAULT_DEFLECTOR_LONG_SWIPE_THRESHOLD_LANDSCAPE = 130

    fun getDeflectorLongSwipeThresholdDp(context: Context, isLandscape: Boolean): Int {
        val prefs = context.defaultPrefs()
        return if (isLandscape) {
            prefs.getInt(KEY_DEFLECTOR_LONG_SWIPE_THRESHOLD_LANDSCAPE, DEFAULT_DEFLECTOR_LONG_SWIPE_THRESHOLD_LANDSCAPE).coerceIn(40, 320)
        } else {
            prefs.getInt(KEY_DEFLECTOR_LONG_SWIPE_THRESHOLD_PORTRAIT, DEFAULT_DEFLECTOR_LONG_SWIPE_THRESHOLD_PORTRAIT).coerceIn(40, 240)
        }
    }

    fun isGestureUnified(context: Context, isLeft: Boolean, gestureKey: String): Boolean {
        val prefs = context.defaultPrefs()
        val key = getGestureUnifiedKey(isLeft, gestureKey)
        val defaultVal = !gestureKey.equals("TAP_HOLD", ignoreCase = true) && !gestureKey.equals("HOLD", ignoreCase = true) && !gestureKey.contains("SCRUB", ignoreCase = true)
        return prefs.getBoolean(key, defaultVal)
    }

    fun setGestureUnified(context: Context, isLeft: Boolean, gestureKey: String, unified: Boolean) {
        val prefs = context.defaultPrefs()
        val key = getGestureUnifiedKey(isLeft, gestureKey)
        prefs.edit().putBoolean(key, unified).apply()
    }

    // Notch Calibration & Test Beacon Keys
    const val KEY_NOTCH_OFFSET_X = "pref_notch_offset_x"
    const val KEY_NOTCH_OFFSET_Y = "pref_notch_offset_y"
    const val KEY_NOTCH_EXPANSION_WIDTH = "pref_notch_expansion_width"
    const val KEY_NOTCH_CUTOUT_WIDTH = "pref_notch_cutout_width"
    const val KEY_NOTCH_PADDING_SNUGNESS = "pref_notch_padding_snugness"
    const val KEY_NOTCH_CAPSULE_LAYOUT = "pref_notch_capsule_layout" // "unified_right", "dual_wing", "unified_left"
    const val KEY_NOTCH_TEST_BEACON = "pref_notch_test_beacon"

    // Hardware Volume Button Gesture Keys
    const val KEY_VOL_GESTURES_ENABLED = "pref_key_vol_gestures_enabled"
    const val KEY_CLEAN_VOLUME_SUPPRESSION = "pref_clean_volume_suppression"
    const val KEY_VOLUME_SUPPRESSION_PROFILE = "pref_volume_suppression_profile" // "instant_reflex", "balanced_holds", "total_clean"
    const val DEFAULT_VOLUME_SUPPRESSION_PROFILE = "balanced_holds"
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

    // Avionics & HUD Scrubber Controls (Brightness & Volume)
    const val KEY_HUD_BRIGHTNESS_ENABLED = "pref_hud_brightness_enabled"
    const val KEY_HUD_VOLUME_ENABLED = "pref_hud_volume_enabled"
    const val KEY_VOLUME_SHOW_NATIVE_SLIDER = "pref_volume_show_native_slider"
    const val KEY_BRIGHTNESS_SCRUB_STEP = "pref_brightness_scrub_step"
    const val KEY_BRIGHTNESS_SCRUB_RESOLUTION = "pref_brightness_scrub_resolution"
    const val KEY_VOLUME_SCRUB_STEP = "pref_volume_scrub_step"
    const val KEY_VOLUME_SCRUB_RESOLUTION = "pref_volume_scrub_resolution"
    const val KEY_HUD_STYLE_DEFAULT = "pref_macro_hud_style_default"
    const val DEFAULT_HUD_STYLE = "canopy_droppod"

    /**
     * Resolves the configured HUD style for a gesture and action.
     * Priority:
     * 1. Action-specific gesture key: "pref_macro_hud_style_<GESTURE>_<ACTION>"
     * 2. Base gesture key: "pref_macro_hud_style_<GESTURE>"
     * 3. Global default HUD style: "pref_macro_hud_style_default"
     * 4. Fallback: "canopy_droppod"
     */
    fun resolveHudStyle(prefs: SharedPreferences, gestureActionKey: String?, actionToken: String? = null): String {
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
        return prefs.getString(KEY_HUD_STYLE_DEFAULT, DEFAULT_HUD_STYLE) ?: DEFAULT_HUD_STYLE
    }

    /**
     * Persists the chosen HUD style for a specific gesture and action without polluting the global default.
     * If gestureActionKey is null/blank, it updates the global default.
     */
    fun saveHudStyle(prefs: SharedPreferences, gestureActionKey: String?, actionToken: String?, style: String) {
        val editor = prefs.edit()
        if (!gestureActionKey.isNullOrBlank()) {
            val baseHudKey = gestureActionKey.replace("pref_macro_action_", "pref_macro_hud_style_")
            editor.putString(baseHudKey, style)
            if (!actionToken.isNullOrBlank()) {
                val cleanToken = actionToken.replace(":", "_")
                editor.putString("${baseHudKey}_$cleanToken", style)
            }
        } else {
            editor.putString(KEY_HUD_STYLE_DEFAULT, style)
        }
        editor.apply()
    }

    // Power Button Safety & Experimental Labs Keys
    const val KEY_POWER_SINGLE_PRESS_UNLOCKED = "pref_power_single_press_unlocked"
    const val KEY_SUPPRESS_DEEP_ACTIVITY_WARNING = "pref_suppress_deep_activity_warning"
    const val KEY_PICKER_PINNED_APPS = "pref_picker_pinned_apps"
    const val KEY_SYSTEM_ACCORDION_MODE = "pref_picker_system_accordion_mode"
    const val KEY_EXPANDED_SUBSECTIONS = "pref_picker_expanded_subsections"
    const val KEY_SECTION_EXPERIMENTAL_LABS_EXPANDED = "pref_section_experimental_labs_expanded"
    const val KEY_SECTION_SYNTHETIC_GRAVITY_EXPANDED = "pref_section_synthetic_gravity_expanded"
    const val KEY_SYNTHETIC_GRAVITY_ENABLED = "pref_synthetic_gravity_enabled"
    fun isSyntheticGravityEnabled(context: Context): Boolean =
        context.defaultPrefs().getBoolean(KEY_SYNTHETIC_GRAVITY_ENABLED, false)
    const val KEY_SECTION_SENSOR_DECK_EXPANDED = "pref_section_statusbar_expanded"
    const val KEY_SECTION_STATUSBAR_EXPANDED = KEY_SECTION_SENSOR_DECK_EXPANDED
    const val KEY_SECTION_LEFT_UNIFIED_EXPANDED = "pref_section_left_unified_expanded"
    const val KEY_SECTION_LEFT_TOP_EXPANDED = "pref_section_left_top_expanded"
    const val KEY_SECTION_LEFT_CENTER_EXPANDED = "pref_section_left_center_expanded"
    const val KEY_SECTION_LEFT_BOTTOM_EXPANDED = "pref_section_left_bottom_expanded"
    const val KEY_SECTION_TELEMETRY_EXPANDED = "pref_section_telemetry_expanded"
    const val KEY_SECTION_TACTICAL_HARDWARE_EXPANDED = "pref_section_tactical_hardware_expanded"
    const val KEY_CORE_COOLING_ENABLED = "pref_core_cooling_enabled"
    const val KEY_CORE_COOLING_SCHEDULE = "pref_core_cooling_schedule"
    const val KEY_CORE_COOLING_DAY_OF_WEEK = "pref_core_cooling_day_of_week"
    const val KEY_CORE_COOLING_HOUR = "pref_core_cooling_hour"
    const val KEY_CORE_COOLING_LAST_TRIGGER = "pref_core_cooling_last_trigger"
    const val KEY_INFINIX_STANDBY_DISMISSED = "pref_infinix_standby_dismissed"
    const val KEY_INFINIX_STANDBY_WARNING_DEMOTED = "pref_infinix_standby_warning_demoted"
    const val KEY_ORBITAL_CAPSULE_ENABLED = "pref_orbital_capsule_enabled"
    const val KEY_OMNISCIENT_AUDIO_DOCK_ENABLED = "pref_omniscient_audio_dock_enabled"
    const val KEY_STATUSBAR_SWIPE_DOWN_NOTIFICATIONS = "pref_statusbar_swipe_down_notifications"

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

    const val KEY_SUB_ORDER_UNIFIED = "pref_sub_order_unified"
    const val KEY_SUB_ORDER_LEFT_UNIFIED = "pref_sub_order_left_unified"
    const val KEY_SUB_PINNED_UNIFIED = "pref_sub_pinned_unified"
    const val KEY_SUB_PINNED_LEFT_UNIFIED = "pref_sub_pinned_left_unified"

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
    const val KEY_SUB_HORIZON_RAIL_GEOM = "pref_sub_horizon_rail_geom"
    const val KEY_SUB_HORIZON_RAIL_COLOR = "pref_sub_horizon_rail_color"
    const val KEY_SUB_HORIZON_RAIL_TEXT = "pref_sub_horizon_rail_text"
    const val KEY_SUB_HORIZON_RAIL_CUSTOM = "pref_sub_horizon_rail_custom"

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
    const val KEY_ORIENTATION_OVERRIDE_EXPIRATION = "pref_orientation_override_expiration" // "until_app_switch", "until_screen_off", "persistent", "disabled"
    const val ORIENTATION_OVERRIDE_EXPIRATION_UNTIL_APP_SWITCH = "until_app_switch"
    const val ORIENTATION_OVERRIDE_EXPIRATION_UNTIL_SCREEN_OFF = "until_screen_off"
    const val ORIENTATION_OVERRIDE_EXPIRATION_PERSISTENT = "persistent"
    const val ORIENTATION_OVERRIDE_EXPIRATION_DISABLED = "disabled"
    const val DEFAULT_ORIENTATION_OVERRIDE_EXPIRATION = ORIENTATION_OVERRIDE_EXPIRATION_PERSISTENT
    const val KEY_HIDE_ON_LOCKSCREEN_AND_DOCK = "pref_hide_on_lockscreen_and_dock"
    const val KEY_ORIENTATION_CONTEXT_GUARD_ENABLED = "pref_orientation_context_guard_enabled"
    const val KEY_SAVED_ACCEL_ROTATION = "pref_saved_accel_rotation"
    const val KEY_SAVED_USER_ROTATION = "pref_saved_user_rotation"
    const val KEY_ATTITUDE_BUCKET_STRICT_PORTRAIT = "pref_attitude_bucket_strict_portrait"
    const val KEY_ATTITUDE_BUCKET_SENSOR_PORTRAIT = "pref_attitude_bucket_sensor_portrait"
    const val KEY_ATTITUDE_BUCKET_SENSOR_LANDSCAPE = "pref_attitude_bucket_sensor_landscape"
    const val KEY_ATTITUDE_BUCKET_SENSOR_360 = "pref_attitude_bucket_sensor_360"
    const val KEY_ACCESSIBILITY_SENTINEL_ENABLED = "pref_accessibility_sentinel_enabled"
    const val KEY_CRASH_SENTINEL_ENABLED = "pref_crash_sentinel_enabled"
    const val KEY_PERIMETER_PROTECTED_SERVICES = "pref_perimeter_protected_services"
    const val KEY_PINNED_ACCESSIBILITY_SERVICES = "pref_pinned_accessibility_services"
    const val KEY_PINNED_BATTERY_EXEMPT_APPS = "pref_pinned_battery_exempt_apps"
    const val KEY_DECK_GLASS_STYLE = "pref_deck_glass_style" // "liquid", "frost", "obsidian"
    const val KEY_DECK_BACKDROP_STYLE = "pref_deck_backdrop_style" // "cosmic", "void", "frost_veil", "clear"
    const val KEY_LIQUID_GLASS_BLUR = "pref_liquid_glass_blur" // Int: 0..120 px
    const val KEY_LIQUID_GLASS_OPACITY = "pref_liquid_glass_opacity" // Float: 0.10f..0.90f
    const val KEY_LIQUID_GLASS_FROST = "pref_liquid_glass_frost" // Float: 0.00f..0.20f
    const val KEY_LIQUID_GLASS_GLARE = "pref_liquid_glass_glare" // Float: 0.00f..1.00f
    const val KEY_LIQUID_GLASS_RIM = "pref_liquid_glass_rim" // Float: 0.00f..1.00f
    const val KEY_LIQUID_GLASS_CAUSTIC = "pref_liquid_glass_caustic" // Boolean
    const val KEY_LIQUID_GLASS_PROGRESSIVE = "pref_liquid_glass_progressive" // Boolean
    const val KEY_SECTION_WATCHDOGS_EXPANDED = "pref_section_watchdogs_expanded"

    // Refueling Bay Keys
    const val KEY_REFUELING_BAY_TRIGGER = "pref_refueling_bay_trigger" // "disabled", "charging_screen_off", "charging_dock_landscape", "screen_timeout", "screensaver_only"
    const val KEY_REFUELING_BATTERY_STYLE = "pref_refueling_battery_style" // "halo", "reactor_ticks", "dual_wings", "tachometer"
    const val KEY_REFUELING_SLEEP_TIMEOUT = "pref_refueling_sleep_timeout" // "5s", "15s", "30s", "60s", "120s", "300s", "never"
    const val KEY_REFUELING_PIXEL_SHIFT = "pref_refueling_pixel_shift"
    const val KEY_REFUELING_AS_LOCKSCREEN = "pref_refueling_as_lockscreen" // Boolean
    const val KEY_REFUELING_WIDGET_ID = "pref_refueling_widget_id" // Legacy single ID
    const val KEY_REFUELING_WIDGET_IDS = "pref_refueling_widget_ids" // JSON array string of widget IDs
    const val KEY_REFUELING_STACK_PROFILE = "pref_refueling_stack_profile" // JSON array string of Stack widget IDs
    const val KEY_REFUELING_GRID_PROFILE = "pref_refueling_grid_profile" // JSON array string of Grid widget IDs (fallback)
    const val KEY_REFUELING_GRID_PORTRAIT_PROFILE = "pref_refueling_grid_portrait_profile" // JSON array string for Portrait
    const val KEY_REFUELING_GRID_LANDSCAPE_PROFILE = "pref_refueling_grid_landscape_profile" // JSON array string for Landscape
    const val KEY_REFUELING_WIDGET_LAYOUT = "pref_refueling_widget_layout" // "smart_stack", "adaptive_grid"
    const val KEY_REFUELING_STACK_REMEMBER_PAGE = "pref_refueling_stack_remember_page" // Boolean, defaults to false
    const val KEY_REFUELING_STACK_LAST_PAGE = "refueling_stack_memory" // Int page index

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

    fun getRefuelingGridWidgetIds(context: Context, isLandscape: Boolean = false): List<Int> {
        val prefs = context.defaultPrefs()
        val key = if (isLandscape) KEY_REFUELING_GRID_LANDSCAPE_PROFILE else KEY_REFUELING_GRID_PORTRAIT_PROFILE
        val jsonStr = prefs.getString(key, null)
            ?: prefs.getString(KEY_REFUELING_GRID_PROFILE, null)
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
                if (list.isNotEmpty()) return list
            } catch (_: Exception) {}
        }
        val legacyId = prefs.getInt(KEY_REFUELING_WIDGET_ID, -1)
        return if (legacyId != -1) listOf(legacyId) else emptyList()
    }

    fun saveRefuelingGridWidgetIds(context: Context, ids: List<Int>, isLandscape: Boolean = false) {
        val prefs = context.defaultPrefs()
        val jsonArray = org.json.JSONArray()
        ids.forEach { jsonArray.put(it) }
        val key = if (isLandscape) KEY_REFUELING_GRID_LANDSCAPE_PROFILE else KEY_REFUELING_GRID_PORTRAIT_PROFILE
        prefs.edit()
            .putString(key, jsonArray.toString())
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

    fun isRefuelingStackMemoryEnabled(context: Context): Boolean =
        context.defaultPrefs().getBoolean(KEY_REFUELING_STACK_REMEMBER_PAGE, false)

    fun setRefuelingStackMemoryEnabled(context: Context, enabled: Boolean) {
        context.defaultPrefs().edit().putBoolean(KEY_REFUELING_STACK_REMEMBER_PAGE, enabled).apply()
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

    fun isDeflectorGlowEnabled(context: Context): Boolean =
        context.defaultPrefs().getBoolean(KEY_DEFLECTOR_GLOW_ENABLED, true)

    fun setDeflectorGlowEnabled(context: Context, enabled: Boolean) {
        context.defaultPrefs().edit().putBoolean(KEY_DEFLECTOR_GLOW_ENABLED, enabled).apply()
    }

    fun isLeftDeflectorGlowEnabled(context: Context): Boolean =
        context.defaultPrefs().getBoolean(KEY_DEFLECTOR_LEFT_GLOW_ENABLED, false)

    fun setLeftDeflectorGlowEnabled(context: Context, enabled: Boolean) {
        context.defaultPrefs().edit().putBoolean(KEY_DEFLECTOR_LEFT_GLOW_ENABLED, enabled).apply()
    }

    fun isRightDeflectorGlowEnabled(context: Context): Boolean =
        context.defaultPrefs().getBoolean(KEY_DEFLECTOR_RIGHT_GLOW_ENABLED, context.defaultPrefs().getBoolean(KEY_DEFLECTOR_GLOW_ENABLED, true))

    fun setRightDeflectorGlowEnabled(context: Context, enabled: Boolean) {
        context.defaultPrefs().edit().putBoolean(KEY_DEFLECTOR_RIGHT_GLOW_ENABLED, enabled).apply()
    }

    fun isDeflectorUseM3Color(context: Context): Boolean =
        context.defaultPrefs().getBoolean(KEY_DEFLECTOR_USE_M3_COLOR, false)

    fun setDeflectorUseM3Color(context: Context, enabled: Boolean) {
        context.defaultPrefs().edit().putBoolean(KEY_DEFLECTOR_USE_M3_COLOR, enabled).apply()
    }

    fun getDeflectorGlowStyle(context: Context): String =
        context.defaultPrefs().getString(KEY_DEFLECTOR_GLOW_STYLE, "progressive_frost") ?: "progressive_frost"

        fun getDeflectorPillStyle(context: Context, isLeft: Boolean): String {
        val key = if (isLeft) KEY_DEFLECTOR_LEFT_PILL_STYLE else KEY_DEFLECTOR_RIGHT_PILL_STYLE
        val fallback = context.defaultPrefs().getString(KEY_DEFLECTOR_PILL_STYLE, "razor_edge") ?: "razor_edge"
        return context.defaultPrefs().getString(key, fallback) ?: fallback
    }

    fun setDeflectorPillStyle(context: Context, isLeft: Boolean, style: String) {
        val key = if (isLeft) KEY_DEFLECTOR_LEFT_PILL_STYLE else KEY_DEFLECTOR_RIGHT_PILL_STYLE
        context.defaultPrefs().edit().putString(key, style).apply()
    }

    fun setDeflectorGlowStyle(context: Context, style: String) {
        context.defaultPrefs().edit().putString(KEY_DEFLECTOR_GLOW_STYLE, style).apply()
    }

    fun isDeflectorGlowOnGestureStep(context: Context): Boolean =
        context.defaultPrefs().getBoolean(KEY_DEFLECTOR_GLOW_ON_GESTURE_STEP, true)

    fun setDeflectorGlowOnGestureStep(context: Context, enabled: Boolean) {
        context.defaultPrefs().edit().putBoolean(KEY_DEFLECTOR_GLOW_ON_GESTURE_STEP, enabled).apply()
    }

    fun getDeflectorGlowDurationMs(context: Context): Long {
        val pref = context.defaultPrefs().getString(KEY_DEFLECTOR_GLOW_DURATION, "1500ms") ?: "1500ms"
        return when (pref) {
            "800ms" -> 800L
            "2200ms" -> 2200L
            else -> 1500L
        }
    }

    fun isFlightNotificationEnabled(context: Context): Boolean =
        context.defaultPrefs().getBoolean(KEY_FLIGHT_NOTIFICATION_ENABLED, false)

    fun setFlightNotificationEnabled(context: Context, enabled: Boolean) {
        context.defaultPrefs().edit().putBoolean(KEY_FLIGHT_NOTIFICATION_ENABLED, enabled).apply()
    }

    fun getCentralCommandLongPressAction(context: Context): String =
        context.defaultPrefs().getString(KEY_CENTRAL_COMMAND_LONG_PRESS_ACTION, "toggle_master_flight") ?: "toggle_master_flight"

    fun setCentralCommandLongPressAction(context: Context, action: String) {
        context.defaultPrefs().edit().putString(KEY_CENTRAL_COMMAND_LONG_PRESS_ACTION, action).apply()
    }

    fun getPerimeterProtectedServices(context: Context): Set<String> =
        context.defaultPrefs().getStringSet(KEY_PERIMETER_PROTECTED_SERVICES, emptySet()) ?: emptySet()

    fun setPerimeterProtectedServices(context: Context, services: Set<String>) {
        context.defaultPrefs().edit().putStringSet(KEY_PERIMETER_PROTECTED_SERVICES, services).apply()
    }

    fun togglePerimeterProtectedService(context: Context, componentId: String, protect: Boolean) {
        val current = getPerimeterProtectedServices(context).toMutableSet()
        if (protect) {
            current.add(componentId)
        } else {
            current.remove(componentId)
        }
        setPerimeterProtectedServices(context, current)
    }

    fun togglePerimeterProtectedService(context: Context, componentId: String): Boolean {
        val current = getPerimeterProtectedServices(context).toMutableSet()
        val isNowProtected = if (current.contains(componentId)) {
            current.remove(componentId)
            false
        } else {
            current.add(componentId)
            true
        }
        setPerimeterProtectedServices(context, current)
        return isNowProtected
    }

    fun getPinnedAccessibilityServices(context: Context): Set<String> =
        context.defaultPrefs().getStringSet(KEY_PINNED_ACCESSIBILITY_SERVICES, emptySet()) ?: emptySet()

    fun setPinnedAccessibilityServices(context: Context, services: Set<String>) {
        context.defaultPrefs().edit().putStringSet(KEY_PINNED_ACCESSIBILITY_SERVICES, services).apply()
    }

    fun togglePinnedAccessibilityService(context: Context, componentId: String): Boolean {
        val current = getPinnedAccessibilityServices(context).toMutableSet()
        val isNowPinned = if (current.contains(componentId)) {
            current.remove(componentId)
            false
        } else {
            current.add(componentId)
            true
        }
        setPinnedAccessibilityServices(context, current)
        return isNowPinned
    }

    fun getPinnedBatteryExemptApps(context: Context): Set<String> =
        context.defaultPrefs().getStringSet(KEY_PINNED_BATTERY_EXEMPT_APPS, emptySet()) ?: emptySet()

    fun setPinnedBatteryExemptApps(context: Context, apps: Set<String>) {
        context.defaultPrefs().edit().putStringSet(KEY_PINNED_BATTERY_EXEMPT_APPS, apps).apply()
    }

    fun togglePinnedBatteryExemptApp(context: Context, packageName: String): Boolean {
        val current = getPinnedBatteryExemptApps(context).toMutableSet()
        val isNowPinned = if (current.contains(packageName)) {
            current.remove(packageName)
            false
        } else {
            current.add(packageName)
            true
        }
        setPinnedBatteryExemptApps(context, current)
        return isNowPinned
    }

    val deckGlassStyleFlow get() = LightspeedDeckStyleManager.deckGlassStyleFlow
    fun getDeckGlassStyle(context: Context) = LightspeedDeckStyleManager.getDeckGlassStyle(context)
    fun setDeckGlassStyle(context: Context, style: String) = LightspeedDeckStyleManager.setDeckGlassStyle(context, style)
    fun refreshDeckGlassStyle(context: Context) = LightspeedDeckStyleManager.refreshDeckGlassStyle(context)

    val deckBackdropStyleFlow get() = LightspeedDeckStyleManager.deckBackdropStyleFlow
    fun getDeckBackdropStyle(context: Context) = LightspeedDeckStyleManager.getDeckBackdropStyle(context)
    fun setDeckBackdropStyle(context: Context, style: String) = LightspeedDeckStyleManager.setDeckBackdropStyle(context, style)
    fun refreshDeckBackdropStyle(context: Context) = LightspeedDeckStyleManager.refreshDeckBackdropStyle(context)

    val liquidGlassConfigFlow get() = LightspeedDeckStyleManager.liquidGlassConfigFlow
    fun getLiquidGlassConfig(context: Context) = LightspeedDeckStyleManager.getLiquidGlassConfig(context)
    fun setLiquidGlassConfig(context: Context, config: LiquidGlassConfig) = LightspeedDeckStyleManager.setLiquidGlassConfig(context, config)
    fun resetLiquidGlassConfig(context: Context) = LightspeedDeckStyleManager.resetLiquidGlassConfig(context)
    fun refreshLiquidGlassConfig(context: Context) = LightspeedDeckStyleManager.refreshLiquidGlassConfig(context)

    fun isHudBrightnessEnabled(context: Context): Boolean =
        context.defaultPrefs().getBoolean(KEY_HUD_BRIGHTNESS_ENABLED, true)

    fun isHudVolumeEnabled(context: Context): Boolean =
        context.defaultPrefs().getBoolean(KEY_HUD_VOLUME_ENABLED, true)

    fun isVolumeShowNativeSlider(context: Context): Boolean =
        context.defaultPrefs().getBoolean(KEY_VOLUME_SHOW_NATIVE_SLIDER, false)

    fun getBrightnessScrubStep(context: Context): Int =
        context.defaultPrefs().getInt(KEY_BRIGHTNESS_SCRUB_STEP, 8)

    fun getVolumeScrubStep(context: Context): Int =
        context.defaultPrefs().getInt(KEY_VOLUME_SCRUB_STEP, 1)

    fun getVolumeScrubResolution(context: Context): Int =
        context.defaultPrefs().getInt(KEY_VOLUME_SCRUB_RESOLUTION, 100).coerceIn(5, 100)

    fun getMediaSkipSeconds(context: Context): Int =
        context.defaultPrefs().getInt(KEY_MEDIA_SKIP_SECONDS, 10)

    fun getCockpitReticleOrientation(context: Context): String =
        context.defaultPrefs().getString(KEY_COCKPIT_RETICLE_ORIENTATION, RETICLE_ORIENTATION_CLASSIC_180) ?: RETICLE_ORIENTATION_CLASSIC_180

    fun setCockpitReticleOrientation(context: Context, orientation: String) {
        context.defaultPrefs().edit().putString(KEY_COCKPIT_RETICLE_ORIENTATION, orientation).apply()
    }

    fun getDeflectorLandscapeMode(context: Context): String =
        context.defaultPrefs().getString(KEY_DEFLECTOR_LANDSCAPE_MODE, DEFLECTOR_LANDSCAPE_MODE_CUSTOM) ?: DEFLECTOR_LANDSCAPE_MODE_CUSTOM

    fun setDeflectorLandscapeMode(context: Context, mode: String) {
        context.defaultPrefs().edit().putString(KEY_DEFLECTOR_LANDSCAPE_MODE, mode).apply()
    }

    fun getDeflectorLandscapeScale(context: Context): Int =
        context.defaultPrefs().getInt(KEY_DEFLECTOR_LANDSCAPE_SCALE, 45)

    fun setDeflectorLandscapeScale(context: Context, scale: Int) {
        context.defaultPrefs().edit().putInt(KEY_DEFLECTOR_LANDSCAPE_SCALE, scale).apply()
    }

    const val KEY_GESTURE_HOLD_DURATION_MS = "pref_gesture_hold_duration_ms"
    const val DEFAULT_GESTURE_HOLD_DURATION_MS = 300

    const val KEY_GRAVITY_ENFORCEMENT_ENGINE = "pref_gravity_enforcement_engine"
    const val GRAVITY_ENGINE_DUAL_HYBRID = "dual_hybrid"
    const val GRAVITY_ENGINE_SYSTEM_SETTINGS = "system_settings"
    const val GRAVITY_ENGINE_WINDOW_ANCHOR = "window_anchor"

    fun getGestureHoldDurationMs(context: Context, gesturePrefKey: String? = null): Long {
        val prefs = context.defaultPrefs()
        val global = prefs.getInt(KEY_GESTURE_HOLD_DURATION_MS, DEFAULT_GESTURE_HOLD_DURATION_MS)
        if (gesturePrefKey != null) {
            val key = if (gesturePrefKey.startsWith("pref_gesture_hold_duration_")) gesturePrefKey else "pref_gesture_hold_duration_$gesturePrefKey"
            return prefs.getInt(key, global).toLong()
        }
        return global.toLong()
    }

    fun getGravityEnforcementEngine(context: Context): String =
        context.defaultPrefs().getString(KEY_GRAVITY_ENFORCEMENT_ENGINE, GRAVITY_ENGINE_DUAL_HYBRID) ?: GRAVITY_ENGINE_DUAL_HYBRID

    fun getDeflectorLandscapeYScale(context: Context): Int =
        context.defaultPrefs().getInt(KEY_DEFLECTOR_LANDSCAPE_Y_SCALE, 50)

    fun setDeflectorLandscapeYScale(context: Context, scale: Int) {
        context.defaultPrefs().edit().putInt(KEY_DEFLECTOR_LANDSCAPE_Y_SCALE, scale).apply()
    }

    const val KEY_DEFAULTS_INITIALIZED = "pref_defaults_initialized_v3"

    fun isFlankUnified(context: Context, isLeft: Boolean): Boolean {
        val key = if (isLeft) KEY_SIDEBAR_LEFT_LINK_FLANK else KEY_SIDEBAR_RIGHT_LINK_FLANK
        return context.defaultPrefs().getBoolean(key, true)
    }

    fun setFlankUnified(context: Context, isLeft: Boolean, unified: Boolean) {
        val key = if (isLeft) KEY_SIDEBAR_LEFT_LINK_FLANK else KEY_SIDEBAR_RIGHT_LINK_FLANK
        context.defaultPrefs().edit().putBoolean(key, unified).apply()
    }

    fun initializeDefaults(context: Context) {
        val prefs = context.defaultPrefs()
        if (prefs.getBoolean(KEY_DEFAULTS_INITIALIZED, false)) return

        val editor = prefs.edit()
        editor.putBoolean(KEY_DEFAULTS_INITIALIZED, true)

        // 1. Tab Accordion Display Profiles & Blueprint Keys (Infinix Profile Defaults)
        editor.putString(KEY_TAB_ACCORDION_MODE_0, "custom_pinned")
        editor.putString(KEY_TAB_ACCORDION_MODE_1, "custom_pinned")
        editor.putString(KEY_TAB_ACCORDION_MODE_2, "custom_pinned")
        editor.putString(KEY_TAB_PINNED_ACCORDION_0, "left_unified_gestures")
        editor.putString(KEY_TAB_PINNED_ACCORDION_1, "config_vault")
        editor.putString(KEY_TAB_PINNED_ACCORDION_2, "unified_gestures")
        editor.putString(KEY_SUB_PINNED_UNIFIED, "gestures")
        editor.putString(KEY_SUB_PINNED_LEFT_UNIFIED, "gestures")
        editor.putString("pref_sub_pinned_unified_2", "gestures")

        editor.putString(KEY_TAB_SECTION_ORDER_0, "left_center,left_unified")
        editor.putString(KEY_TAB_SECTION_ORDER_1, "sensor_deck,telemetry_indicators,tactical_hardware,system_overrides,config_vault,experimental_labs")
        editor.putString("pref_tab_order_1", "sensor_deck,telemetry_indicators,tactical_hardware,system_overrides,config_vault,experimental_labs")
        editor.putString(KEY_TAB_SECTION_ORDER_2, "center,unified")
        editor.putString(KEY_RAIL_DOWNLOADS_ROUTING, "top_line")
        editor.putString(KEY_RAIL_MEDIA_ROUTING, "none")

        // Deflectors Unified (Linked) by default
        editor.putBoolean(KEY_SIDEBAR_RIGHT_LINK_FLANK, true)
        editor.putBoolean(KEY_SIDEBAR_LEFT_LINK_FLANK, true)
        editor.putBoolean("pref_section_unified_expanded", true)
        editor.putBoolean("pref_section_right_unified_expanded", true)
        editor.putBoolean("pref_section_left_unified_expanded", true)
        editor.putBoolean("pref_section_center_expanded", true)
        editor.putBoolean("pref_section_left_center_expanded", false)
        editor.putBoolean("pref_section_watchdogs_expanded", true)

        // Sub-section Expansion Defaults (Infinix Profile)
        editor.putBoolean("pref_sub_gestures_unified", true)
        editor.putBoolean("pref_sub_gestures_left_unified", true)
        editor.putBoolean("pref_sub_geo_unified", false)
        editor.putBoolean("pref_sub_geo_left_unified", false)
        editor.putBoolean("pref_sub_scrub_unified", false)
        editor.putBoolean("pref_sub_scrub_left_unified", false)
        editor.putBoolean("pref_sub_geo_center", true)
        editor.putBoolean("pref_sub_geo_left_center", true)

        editor.putBoolean("pref_section_statusbar_expanded", false)
        editor.putBoolean("pref_sub_gestures_statusbar", true)
        editor.putBoolean("pref_sub_geo_statusbar", false)
        editor.putBoolean("pref_sub_scrub_statusbar", false)

        editor.putBoolean("pref_section_tactical_hardware_expanded", false)
        editor.putBoolean("pref_sub_hulltap_expanded", false)
        editor.putBoolean("pref_sub_volume_expanded", false)
        editor.putBoolean("pref_sub_power_expanded", false)
        editor.putBoolean(KEY_VOL_GESTURES_ENABLED, false)
        editor.putString(KEY_VOLUME_SUPPRESSION_PROFILE, DEFAULT_VOLUME_SUPPRESSION_PROFILE)
        editor.putBoolean(KEY_POWER_GESTURES_ENABLED, false)
        editor.putBoolean(KEY_STATUSBAR_SWIPE_DOWN_NOTIFICATIONS, false)

        editor.putInt(KEY_DEFLECTOR_LONG_SWIPE_THRESHOLD_PORTRAIT, DEFAULT_DEFLECTOR_LONG_SWIPE_THRESHOLD_PORTRAIT)
        editor.putInt(KEY_DEFLECTOR_LONG_SWIPE_THRESHOLD_LANDSCAPE, DEFAULT_DEFLECTOR_LONG_SWIPE_THRESHOLD_LANDSCAPE)

        editor.putBoolean("pref_section_backup_expanded", false)
        editor.putBoolean("pref_section_experimental_labs_expanded", true)

        // Scrub regions unlinked by default (separate deflector controls: Upper = Brightness, Lower = Volume)
        editor.putBoolean(KEY_UNIFIED_SCRUB_REGIONS_LINKED, false)
        editor.putBoolean(KEY_UNIFIED_SCRUB_REGIONS_LINKED_RIGHT, false)
        editor.putBoolean(KEY_UNIFIED_SCRUB_REGIONS_LINKED_LEFT, false)

        // Scrubbing Presets (Infinix Profile)
        editor.putInt("pref_volume_scrub_resolution", 32)
        editor.putInt("pref_volume_scrub_step", 3)
        editor.putInt("pref_brightness_scrub_resolution", 70)
        editor.putInt("pref_brightness_scrub_step", 3)

        // 2. Left Deflector Geometry Defaults
        editor.putInt("pref_sidebar_left_center_height", 70)
        editor.putInt("pref_sidebar_left_center_y_offset", 0)
        editor.putInt("pref_sidebar_left_center_touch_width", 10)
        editor.putInt("pref_sidebar_left_center_visual_width", 0)
        editor.putInt("pref_sidebar_left_center_transparency", 0)
        editor.putInt("pref_sidebar_left_top_height", 220)
        editor.putInt("pref_sidebar_left_top_touch_width", 10)
        editor.putInt("pref_sidebar_left_top_visual_width", 0)
        editor.putInt("pref_sidebar_left_top_transparency", 0)
        editor.putInt("pref_sidebar_left_bottom_height", 250)
        editor.putInt("pref_sidebar_left_bottom_touch_width", 10)
        editor.putInt("pref_sidebar_left_bottom_visual_width", 0)
        editor.putInt("pref_sidebar_left_bottom_transparency", 0)

        // 3. Right Deflector Geometry Defaults
        editor.putInt("pref_sidebar_center_height", 70)
        editor.putInt("pref_sidebar_center_y_offset", 0)
        editor.putInt("pref_sidebar_center_touch_width", 10)
        editor.putInt("pref_sidebar_center_visual_width", 0)
        editor.putInt("pref_sidebar_center_transparency", 35)
        editor.putInt("pref_sidebar_top_height", 220)
        editor.putInt("pref_sidebar_top_touch_width", 10)
        editor.putInt("pref_sidebar_top_visual_width", 0)
        editor.putInt("pref_sidebar_bottom_height", 250)
        editor.putInt("pref_sidebar_bottom_touch_width", 10)
        editor.putInt("pref_sidebar_bottom_visual_width", 0)

        // Pill Geometry Style & Landscape Profile
        editor.putString(KEY_DEFLECTOR_PILL_STYLE, "razor_edge")
        editor.putString(KEY_DEFLECTOR_LEFT_PILL_STYLE, "razor_edge")
        editor.putString(KEY_DEFLECTOR_RIGHT_PILL_STYLE, "razor_edge")
        editor.putBoolean(KEY_DEFLECTOR_LEFT_GLOW_ENABLED, false)
        editor.putBoolean(KEY_DEFLECTOR_RIGHT_GLOW_ENABLED, true)
        editor.putBoolean(KEY_DEFLECTOR_USE_M3_COLOR, false)
        editor.putString(KEY_DEFLECTOR_LANDSCAPE_MODE, DEFLECTOR_LANDSCAPE_MODE_CUSTOM)
        editor.putString(KEY_ORIENTATION_OVERRIDE_EXPIRATION, DEFAULT_ORIENTATION_OVERRIDE_EXPIRATION)

        // 4. Status Bar (Sensor Area) - enabled by default matching Infinix profile
        editor.putBoolean(KEY_STATUSBAR_ENABLED, true)
        editor.putInt("pref_statusbar_height", 35)
        editor.putInt("pref_statusbar_span", 200)
        editor.putInt("pref_statusbar_offset_x", 180)
        editor.putInt("pref_statusbar_sensitivity", 25)
        editor.putInt("pref_statusbar_thickness", 35)
        editor.putInt("pref_statusbar_transparency", 0)

        // Landscape / Tablet defaults
        editor.putInt(KEY_STATUSBAR_SPAN_LANDSCAPE, 320)
        editor.putInt(KEY_STATUSBAR_THICKNESS_LANDSCAPE, 35)
        editor.putInt(KEY_STATUSBAR_OFFSET_X_LANDSCAPE, 300)

        // Status Bar Actions (Infinix Profile)
        editor.putString("pref_macro_action_STATUSBAR_TAP", "system:scroll_to_top")
        editor.putString("pref_macro_action_STATUSBAR_TAP_HOLD", "system:gravity_override_360")
        editor.putString("pref_macro_action_STATUSBAR_SWIPE_DOWN", "system:notifications")
        editor.putString("pref_macro_action_STATUSBAR_SWIPE_RIGHT", "system:core_watchdog")
        editor.putString("pref_macro_action_STATUSBAR_SWIPE_LEFT", "system:perimeter_watchdog")
        editor.putString("pref_macro_action_STATUSBAR_SWIPE_RIGHT_HOLD", "system:popup_window")
        editor.putString("pref_macro_action_STATUSBAR_SWIPE_LEFT_HOLD", "system:split_screen")

        // HUD & Scrubber Defaults (Infinix Profile)
        editor.putBoolean(KEY_HUD_BRIGHTNESS_ENABLED, true)
        editor.putBoolean(KEY_HUD_VOLUME_ENABLED, true)
        editor.putBoolean(KEY_VOLUME_SHOW_NATIVE_SLIDER, false)
        editor.putString(KEY_HUD_STYLE_DEFAULT, "canopy_droppod")

        // 5. Pre-assigned Default Actions (Unified Right)
        editor.putString("pref_macro_action_UNIFIED_SWIPE_LEFT", "system:back")
        editor.putString("pref_macro_action_UNIFIED_SWIPE_LEFT_HOLD", "system:close_app")
        editor.putString("pref_macro_action_UNIFIED_SWIPE_DOWN", "system:home")
        editor.putString("pref_macro_action_UNIFIED_SWIPE_UP", "system:recents")
        editor.putString("pref_macro_action_UNIFIED_SWIPE_UP_DOWN", "system:refueling_bay")
        editor.putString("pref_macro_action_UNIFIED_SWIPE_LEFT_DOWN", "system:notifications")
        editor.putString("pref_macro_action_UNIFIED_SWIPE_LEFT_DOWN_HOLD", "system:quick_settings")
        editor.putString("pref_macro_action_UNIFIED_SWIPE_LEFT_UP", "system:previous_app")
        editor.putString("pref_macro_action_UNIFIED_TAP_HOLD", "system:brightness")
        editor.putString("pref_macro_action_UNIFIED_SCRUBBING", "system:brightness")

        // Right Flank Severed / Dual Control Defaults
        editor.putString("pref_macro_action_TOP_SCRUBBING", "system:brightness")
        editor.putString("pref_macro_action_BOTTOM_SCRUBBING", "system:volume")
        editor.putString("pref_macro_action_TOP_TAP_HOLD", "system:volume")
        editor.putString("pref_macro_action_BOTTOM_TAP_HOLD", "system:brightness")
        editor.putString("pref_macro_action_TOP_SWIPE_LEFT", "system:back")
        editor.putString("pref_macro_action_BOTTOM_SWIPE_LEFT", "system:back")
        editor.putString("pref_macro_action_TOP_SWIPE_LEFT_HOLD", "system:close_app")
        editor.putString("pref_macro_action_BOTTOM_SWIPE_LEFT_HOLD", "system:close_app")
        editor.putString("pref_macro_action_TOP_SWIPE_DOWN", "system:home")
        editor.putString("pref_macro_action_BOTTOM_SWIPE_DOWN", "system:home")
        editor.putString("pref_macro_action_TOP_SWIPE_UP", "system:recents")
        editor.putString("pref_macro_action_BOTTOM_SWIPE_UP", "system:recents")
        editor.putString("pref_macro_action_TOP_SWIPE_LEFT_DOWN", "system:notifications")
        editor.putString("pref_macro_action_BOTTOM_SWIPE_LEFT_DOWN", "system:notifications")
        editor.putString("pref_macro_action_TOP_SWIPE_LEFT_DOWN_HOLD", "system:quick_settings")
        editor.putString("pref_macro_action_BOTTOM_SWIPE_LEFT_DOWN_HOLD", "system:quick_settings")
        editor.putString("pref_macro_action_TOP_SWIPE_LEFT_UP", "system:previous_app")
        editor.putString("pref_macro_action_BOTTOM_SWIPE_LEFT_UP", "system:previous_app")

        // Pre-assigned Default Actions (Unified Left)
        editor.putString("pref_macro_action_LEFT_UNIFIED_SWIPE_RIGHT", "system:back")
        editor.putString("pref_macro_action_LEFT_UNIFIED_SWIPE_RIGHT_HOLD", "system:close_app")
        editor.putString("pref_macro_action_LEFT_UNIFIED_SWIPE_UP", "system:gravity_override_360")
        editor.putString("pref_macro_action_LEFT_UNIFIED_SWIPE_UP_DOWN", "system:home")
        editor.putString("pref_macro_action_LEFT_UNIFIED_SWIPE_DOWN_UP", "system:recents")
        editor.putString("pref_macro_action_LEFT_UNIFIED_SWIPE_RIGHT_UP", "system:screenshot")
        editor.putString("pref_macro_action_LEFT_UNIFIED_SWIPE_RIGHT_DOWN", "system:notifications")
        editor.putString("pref_macro_action_LEFT_UNIFIED_SWIPE_RIGHT_DOWN_HOLD", "system:quick_settings")
        editor.putString("pref_macro_action_LEFT_UNIFIED_TAP_HOLD", "system:volume")
        editor.putString("pref_macro_action_LEFT_UNIFIED_SCRUBBING", "system:volume")

        // Left Flank Severed / Dual Control Defaults
        editor.putString("pref_macro_action_LEFT_TOP_SCRUBBING", "system:volume")
        editor.putString("pref_macro_action_LEFT_BOTTOM_SCRUBBING", "system:brightness")
        editor.putString("pref_macro_action_LEFT_TOP_TAP_HOLD", "system:brightness")
        editor.putString("pref_macro_action_LEFT_BOTTOM_TAP_HOLD", "system:volume")
        editor.putString("pref_macro_action_LEFT_TOP_SWIPE_RIGHT", "system:back")
        editor.putString("pref_macro_action_LEFT_BOTTOM_SWIPE_RIGHT", "system:back")
        editor.putString("pref_macro_action_LEFT_TOP_SWIPE_RIGHT_HOLD", "system:close_app")
        editor.putString("pref_macro_action_LEFT_BOTTOM_SWIPE_RIGHT_HOLD", "system:close_app")
        editor.putString("pref_macro_action_LEFT_TOP_SWIPE_UP", "system:gravity_override_360")
        editor.putString("pref_macro_action_LEFT_BOTTOM_SWIPE_UP", "system:gravity_override_360")
        editor.putString("pref_macro_action_LEFT_TOP_SWIPE_UP_DOWN", "system:home")
        editor.putString("pref_macro_action_LEFT_BOTTOM_SWIPE_UP_DOWN", "system:home")
        editor.putString("pref_macro_action_LEFT_TOP_SWIPE_DOWN_UP", "system:recents")
        editor.putString("pref_macro_action_LEFT_BOTTOM_SWIPE_DOWN_UP", "system:recents")
        editor.putString("pref_macro_action_LEFT_TOP_SWIPE_RIGHT_UP", "system:screenshot")
        editor.putString("pref_macro_action_LEFT_BOTTOM_SWIPE_RIGHT_UP", "system:screenshot")
        editor.putString("pref_macro_action_LEFT_TOP_SWIPE_RIGHT_DOWN", "system:notifications")
        editor.putString("pref_macro_action_LEFT_BOTTOM_SWIPE_RIGHT_DOWN", "system:notifications")
        editor.putString("pref_macro_action_LEFT_TOP_SWIPE_RIGHT_DOWN_HOLD", "system:quick_settings")
        editor.putString("pref_macro_action_LEFT_BOTTOM_SWIPE_RIGHT_DOWN_HOLD", "system:quick_settings")

        editor.apply()
    }
}

/**
 * Extension function on Context to get the default Lightspeed SharedPreferences instance.
 */
fun Context.defaultPrefs(): SharedPreferences =
    getSharedPreferences(LightspeedPreferences.PREFS_FILE, Context.MODE_PRIVATE)
