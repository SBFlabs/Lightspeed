package com.sbf.lightspeed.system

import android.content.Context
import android.content.SharedPreferences

/**
 * Standardized SharedPreferences constants and extension accessors for Lightspeed.
 */
object LightspeedPreferences :
    LightspeedPreferencesAccessorsContract by LightspeedPreferencesAccessors,
    LightspeedPerimeterAccessorsContract by LightspeedPreferencesPerimeterAccessors,
    LightspeedPreferencesFlowsContract by LightspeedPreferencesFlows,
    LightspeedPreferencesRefuelingKeysContract,
    LightspeedPreferencesScrubKeysContract,
    LightspeedPreferencesHorizonRailContract,
    LightspeedPreferencesDisplayKeysContract {
    const val PREFS_FILE = "default"

    // Master Flight Deck & Deflector Keys
    const val KEY_ALLOW_EXTERNAL_AUTOMATION = "key_allow_external_automation"
    const val KEY_MASTER_FLIGHT_ARMED = "pref_master_flight_armed"
    const val KEY_FLIGHT_NOTIFICATION_ENABLED = "pref_flight_notification_enabled"
    const val KEY_DEFLECTOR_LEFT_ENABLED = "pref_deflector_left_enabled"
    const val KEY_DEFLECTOR_RIGHT_ENABLED = "pref_deflector_right_enabled"
    const val KEY_DEFLECTOR_DEFAULT_STATE = "pref_deflector_default_state"
    const val KEY_DEFLECTOR_GLOW_ENABLED = "pref_deflector_glow_enabled"
    const val KEY_DEFLECTOR_LEFT_GLOW_ENABLED = "pref_deflector_left_glow_enabled"
    const val KEY_DEFLECTOR_RIGHT_GLOW_ENABLED = "pref_deflector_right_glow_enabled"
    const val KEY_DEFLECTOR_USE_M3_COLOR = "pref_deflector_use_m3_color"
    const val KEY_DEFLECTOR_GLOW_STYLE = "pref_deflector_glow_style"
    const val KEY_DEFLECTOR_LEFT_PILL_STYLE = "pref_deflector_left_pill_style"
    const val KEY_DEFLECTOR_RIGHT_PILL_STYLE = "pref_deflector_right_pill_style"
    const val KEY_DEFLECTOR_PILL_STYLE = "pref_deflector_pill_style"
    const val KEY_DEFLECTOR_GLOW_ON_GESTURE_STEP = "pref_deflector_glow_on_gesture_step"
    const val KEY_DEFLECTOR_GLOW_DURATION = "pref_deflector_glow_duration"
    const val KEY_CENTRAL_COMMAND_LONG_PRESS_ACTION = "pref_central_command_long_press_action"

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
    const val KEY_GEAR_SHAPE = "pref_gear_shape"
    const val GEAR_SHAPE_CIRCLE = "circle"
    const val GEAR_SHAPE_SQUARE = "square"
    const val GEAR_SHAPE_SQUIRCLE = "squircle"
    const val GEAR_SHAPE_STAR = "star"
    const val GEAR_SHAPE_ORBITAL = "orbital"
    const val GEAR_SHAPE_DIAMOND = "diamond"
    const val GEAR_SHAPE_CRESCENT = "crescent"
    const val GEAR_SHAPE_TECH = "tech"
    const val KEY_GEAR_DPI_INDEPENDENT = "pref_gear_dpi_independent"
    const val KEY_GEAR_CUSTOM_DIAMETER_SCALE = "pref_gear_custom_diameter_scale"

    // Pop-up Window & Split Picker Keys
    const val KEY_POPUP_STYLE = "pref_popup_style"
    const val POPUP_STYLE_NATIVE = "native"
    const val POPUP_STYLE_OEM = "oem"
    const val KEY_SPLIT_WITH_APP_TARGET = "split_with_app_target"

    // App Category Override & Icon Pipeline Keys
    const val KEY_APP_CATEGORY_OVERRIDE_PREFIX = "pref_app_category_override_"
    const val KEY_CATEGORY_ORDER = "pref_category_order"
    const val KEY_ACTIVE_ICON_PACK = "pref_active_icon_pack"
    const val KEY_CATEGORY_CRUISE_ICON_PACK = "pref_category_cruise_icon_pack"
    const val KEY_CATEGORY_CRUISE_ICON_STYLE = "pref_category_cruise_icon_style"
    const val KEY_COCKPIT_ICON_PACK = "pref_cockpit_icon_pack"
    const val KEY_COCKPIT_ICON_RENDER_MODE = "pref_cockpit_icon_render_mode"

    // Cockpit Reticle Targeting Orientation & Landscape Geometry Keys
    const val KEY_GEAR_RETICLE_STYLE = "pref_gear_reticle_style"
    const val KEY_COCKPIT_RETICLE_ORIENTATION = "pref_cockpit_reticle_orientation"
    const val RETICLE_ORIENTATION_CLASSIC_180 = "classic_180"
    const val RETICLE_ORIENTATION_MIRRORED_FLANK = "mirrored_flank"
    const val KEY_DEFLECTOR_LANDSCAPE_MODE = "pref_deflector_landscape_mode"
    const val DEFLECTOR_LANDSCAPE_MODE_AUTO = "auto"
    const val DEFLECTOR_LANDSCAPE_MODE_CUSTOM = "custom"
    const val DEFLECTOR_LANDSCAPE_MODE_FIXED = "fixed"
    const val KEY_DEFLECTOR_LANDSCAPE_SCALE = "pref_deflector_landscape_scale"
    const val KEY_DEFLECTOR_LANDSCAPE_Y_SCALE = "pref_deflector_landscape_y_scale"

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

    const val KEY_DEFLECTOR_LONG_SWIPE_THRESHOLD_PORTRAIT = "pref_deflector_long_swipe_threshold_portrait"
    const val KEY_DEFLECTOR_LONG_SWIPE_THRESHOLD_LANDSCAPE = "pref_deflector_long_swipe_threshold_landscape"
    const val DEFAULT_DEFLECTOR_LONG_SWIPE_THRESHOLD_PORTRAIT = 90
    const val DEFAULT_DEFLECTOR_LONG_SWIPE_THRESHOLD_LANDSCAPE = 130

    // Notch Calibration & Test Beacon Keys
    const val KEY_NOTCH_OFFSET_X = "pref_notch_offset_x"
    const val KEY_NOTCH_OFFSET_Y = "pref_notch_offset_y"
    const val KEY_NOTCH_EXPANSION_WIDTH = "pref_notch_expansion_width"
    const val KEY_NOTCH_CUTOUT_WIDTH = "pref_notch_cutout_width"
    const val KEY_NOTCH_PADDING_SNUGNESS = "pref_notch_padding_snugness"
    const val KEY_NOTCH_CAPSULE_LAYOUT = "pref_notch_capsule_layout"
    const val KEY_NOTCH_TEST_BEACON = "pref_notch_test_beacon"
    const val KEY_SUB_CUTOUT_CALIB = "pref_sub_cutout_calib"
    const val KEY_SUB_NOTCH_CALIB = "pref_sub_notch_calib"

    // Hardware Volume Button Gesture Keys
    const val KEY_VOL_GESTURES_ENABLED = "pref_key_vol_gestures_enabled"
    const val KEY_CLEAN_VOLUME_SUPPRESSION = "pref_clean_volume_suppression"
    const val KEY_VOLUME_SUPPRESSION_PROFILE = "pref_volume_suppression_profile"
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

    // Power Button Engine Keys
    const val KEY_POWER_GESTURES_ENABLED = "pref_key_power_gestures_enabled"
    const val KEY_POWER_GRAB_HELPER = "pref_power_grab_helper"
    const val KEY_POWER_HOLD_TAKEOVER = "pref_key_power_hold_takeover"
    const val KEY_POWER_TAKEOVER_ACTIVE = "pref_key_power_takeover_active"
    const val KEY_POWER_TAKEOVER_SAVED_VALUE = "pref_key_power_takeover_saved"
    const val KEY_POWER_SINGLE_PRESS = "pref_key_power_single_press"
    const val KEY_POWER_DOUBLE_PRESS = "pref_key_power_double_press"
    const val KEY_POWER_HOLD = "pref_key_power_hold"
    const val KEY_POWER_PRESS_THEN_HOLD = "pref_key_power_press_then_hold"
    const val KEY_POWER_LONG_PRESS_ACTION = "pref_power_long_press_action"
    const val KEY_SUB_POWER_EXPANDED = "pref_sub_power_expanded"
    const val KEY_POWER_DOUBLE_PRESS_WINDOW = "pref_power_double_press_window"
    const val DEFAULT_POWER_DOUBLE_PRESS_WINDOW_MS = 450L

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
    const val ACTION_BATTERY_EXEMPTION = "system:battery_exemption"
    const val ACTION_TORCH = "system:torch"

    // Avionics & HUD Scrubber Controls
    const val KEY_HUD_BRIGHTNESS_ENABLED = "pref_hud_brightness_enabled"
    const val KEY_HUD_VOLUME_ENABLED = "pref_hud_volume_enabled"
    const val KEY_HUD_USE_CENTRAL_THEME = "pref_hud_use_central_theme"
    const val KEY_VOLUME_SHOW_NATIVE_SLIDER = "pref_volume_show_native_slider"
    const val KEY_BRIGHTNESS_SCRUB_STEP = "pref_brightness_scrub_step"
    const val KEY_BRIGHTNESS_SCRUB_RESOLUTION = "pref_brightness_scrub_resolution"
    const val KEY_VOLUME_SCRUB_STEP = "pref_volume_scrub_step"
    const val KEY_VOLUME_SCRUB_RESOLUTION = "pref_volume_scrub_resolution"
    const val KEY_HUD_STYLE_DEFAULT = "pref_macro_hud_style_default"
    const val DEFAULT_HUD_STYLE = "canopy_droppod"

    // Power Button Safety & Experimental Labs Keys
    const val KEY_POWER_SINGLE_PRESS_UNLOCKED = "pref_power_single_press_unlocked"
    const val KEY_SUPPRESS_DEEP_ACTIVITY_WARNING = "pref_suppress_deep_activity_warning"
    const val KEY_PICKER_PINNED_APPS = "pref_picker_pinned_apps"
    const val KEY_SYSTEM_ACCORDION_MODE = "pref_picker_system_accordion_mode"
    const val KEY_EXPANDED_SUBSECTIONS = "pref_picker_expanded_subsections"
    const val KEY_SECTION_EXPERIMENTAL_LABS_EXPANDED = "pref_section_experimental_labs_expanded"
    const val KEY_SECTION_SYNTHETIC_GRAVITY_EXPANDED = "pref_section_synthetic_gravity_expanded"
    const val KEY_SYNTHETIC_GRAVITY_ENABLED = "pref_synthetic_gravity_enabled"
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
    const val KEY_SUB_CORE_COOLING_LABS = "pref_sub_core_cooling_labs"
    const val KEY_INFINIX_STANDBY_DISMISSED = "pref_infinix_standby_dismissed"
    const val KEY_INFINIX_STANDBY_WARNING_DEMOTED = "pref_infinix_standby_warning_demoted"
    const val KEY_ORBITAL_CAPSULE_ENABLED = "pref_orbital_capsule_enabled"
    const val KEY_SONIC_DECK_ENABLED = "pref_omniscient_audio_dock_enabled"
    const val KEY_SONIC_DECK_LEFT_ENABLED = "pref_sonic_deck_left_enabled"
    const val KEY_SONIC_DECK_RIGHT_ENABLED = "pref_sonic_deck_right_enabled"
    const val KEY_SONIC_POWER_WARNING_DISMISSED = "pref_sonic_power_warning_dismissed"
    const val KEY_VISTA_DECK_ENABLED = "pref_vista_deck_enabled"
    const val KEY_VISTA_DECK_LEFT_ENABLED = "pref_vista_deck_left_enabled"
    const val KEY_VISTA_DECK_RIGHT_ENABLED = "pref_vista_deck_right_enabled"
    const val KEY_CUSTOM_VOLUME_HUD_ENABLED = "pref_custom_volume_hud_enabled"
    const val KEY_PER_APP_VOLUME_PREFIX = "pref_app_vol_"
    const val KEY_SONIC_VOCAL_BOOST = "pref_sonic_vocal_boost"
    const val KEY_SONIC_BACKGROUND_MUFFLE = "pref_sonic_background_muffle"
    const val KEY_SONIC_VOICE_ISOLATION_ENABLED = "pref_sonic_voice_isolation_enabled"
    const val KEY_SONIC_CHANNEL_ROUTING_PREFIX = "pref_sonic_channel_route_"
    const val KEY_SONIC_ALWAYS_AUDIO_FOCUS_PREFIX = "pref_sonic_audio_focus_"
    const val KEY_SONIC_PINNED_APPS = "pref_sonic_pinned_apps"
    const val KEY_SONIC_IGNORED_APPS = "pref_sonic_ignored_apps"

    // Display, Eye Care, Smart Pixels & Mindful Airlock
    const val KEY_EXTRA_DIM_ENABLED = "pref_extra_dim_enabled"
    const val KEY_EXTRA_DIM_LEVEL = "pref_extra_dim_level"
    const val KEY_EYE_CARE_ENABLED = "pref_eye_care_enabled"
    const val KEY_EYE_CARE_TEMP = "pref_eye_care_temp"
    const val KEY_EYE_CARE_MODE = "pref_eye_care_mode"
    const val KEY_EYE_REST_REMINDER_ENABLED = "pref_eye_rest_reminder_enabled"
    const val KEY_EYE_REST_INTERVAL_MIN = "pref_eye_rest_interval_min"
    const val KEY_EYE_REST_STRENGTH = "pref_eye_rest_strength"
    const val KEY_EYE_REST_TITLE = "pref_eye_rest_title"
    const val KEY_EYE_REST_SUBTITLE = "pref_eye_rest_subtitle"
    const val KEY_SMART_PIXELS_ENABLED = "pref_smart_pixels_enabled"
    const val KEY_SMART_PIXELS_PATTERN = "pref_smart_pixels_pattern"
    const val KEY_SMART_PIXELS_DISABLE_ON_CAST = "pref_smart_pixels_disable_on_cast"
    const val KEY_CONSCIOUS_AIRLOCK_ENABLED = "pref_conscious_airlock_enabled"
    const val KEY_CONSCIOUS_DELAY_PACKAGES = "pref_conscious_delay_packages"

    // Haptics Customization & Profiles
    const val KEY_HAPTIC_PROFILE = "pref_haptic_profile"
    const val KEY_CRUISE_HAPTICS = "pref_cruise_haptics"
    const val KEY_GESTURE_SCRUB_HAPTICS = "pref_gesture_scrub_haptics"
    const val KEY_HAPTIC_COOLDOWN_MS = "pref_haptic_cooldown_ms"
    const val DEFAULT_HAPTIC_COOLDOWN_MS = 45
    const val KEY_DEFLECTOR_HAPTICS = "pref_deflector_haptics"
    const val KEY_CENTRAL_COMMAND_HAPTICS = "pref_central_command_haptics"
    const val KEY_COCKPIT_HAPTICS = "pref_cockpit_haptics"
    const val KEY_COCKPIT_GEAR_JUMP_HAPTICS = "pref_cockpit_gear_jump_haptics"
    const val KEY_CRUISE_CATEGORY_HAPTICS = "pref_cruise_category_haptics"
    const val KEY_CRUISE_LAYER_JUMP_HAPTICS = "pref_cruise_layer_jump_haptics"
    const val KEY_CRUISE_APP_FOCUS_HAPTICS = "pref_cruise_app_focus_haptics"

    // Space Pods Tactical Floating Deck
    const val KEY_SPACE_PODS_ENABLED = "pref_space_pods_enabled"
    const val KEY_SPACE_POD_COUNT = "pref_space_pod_count"
    const val KEY_SPACE_POD_SIZE_DP = "pref_space_pod_size_dp"
    const val KEY_SPACE_POD_ALPHA = "pref_space_pod_alpha"
    const val KEY_SPACE_POD_SHAPE = "pref_space_pod_shape"
    const val KEY_SPACE_POD_AUTOCLICK_INTERVAL_MS = "pref_space_pod_autoclick_interval_ms"

    const val KEY_STATUSBAR_SWIPE_DOWN_NOTIFICATIONS = "pref_statusbar_swipe_down_notifications"

    // Tab Accordion Display Profiles & Blueprint Keys
    const val KEY_TAB_ACCORDION_MODE_0 = "pref_tab_accordion_mode_0"
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

    // Shared Hardware Cutout & Punch-Hole Calibration Keys
    const val KEY_HARDWARE_CUTOUT_WIDTH = "pref_hardware_cutout_width"
    const val KEY_HARDWARE_CUTOUT_OFFSET_X = "pref_hardware_cutout_offset_x"
    const val KEY_HARDWARE_CUTOUT_OFFSET_Y = "pref_hardware_cutout_offset_y"

    const val KEY_NOTCH_CAPSULE_ORIENTATION_MODE = "pref_notch_capsule_orientation_mode"
    const val KEY_NOTCH_MARQUEE_ENABLED = "pref_notch_marquee_enabled"
    const val KEY_NOTCH_MARQUEE_SPEED = "pref_notch_marquee_speed"
    const val KEY_NOTCH_MARQUEE_INITIAL_DELAY = "pref_notch_marquee_initial_delay"
    const val KEY_NOTCH_MAX_CAPSULE_WIDTH = "pref_notch_max_capsule_width"
    const val KEY_NOTCH_TEXT_SCROLL_MODE = "pref_notch_text_scroll_mode"
    const val KEY_NOTCH_TEXT_TRUNCATE_ANCHOR = "pref_notch_text_truncate_anchor"

    // Media Actions, Hold Duration & Gravity Engine Keys
    const val KEY_MEDIA_SKIP_SECONDS = "pref_media_skip_seconds"
    const val KEY_MEDIA_SKIP_FORWARD_SECONDS = "pref_media_skip_forward_seconds"
    const val KEY_MEDIA_SKIP_BACKWARD_SECONDS = "pref_media_skip_backward_seconds"
    const val KEY_GESTURE_HOLD_DURATION_MS = "pref_gesture_hold_duration_ms"
    const val DEFAULT_GESTURE_HOLD_DURATION_MS = 300
    const val KEY_GRAVITY_ENFORCEMENT_ENGINE = "pref_gravity_enforcement_engine"
    const val GRAVITY_ENGINE_DUAL_HYBRID = "dual_hybrid"
    const val GRAVITY_ENGINE_SYSTEM_SETTINGS = "system_settings"
    const val GRAVITY_ENGINE_WINDOW_ANCHOR = "window_anchor"
    const val KEY_DEFAULTS_INITIALIZED = "pref_defaults_initialized_v3"

    // Defaults Initialization Delegate
    fun initializeDefaults(context: Context) = LightspeedPreferencesDefaults.initializeDefaults(context)
}

/**
 * Extension function on Context to get the default Lightspeed SharedPreferences instance.
 */
fun Context.defaultPrefs(): SharedPreferences =
    getSharedPreferences(LightspeedPreferences.PREFS_FILE, Context.MODE_PRIVATE)
