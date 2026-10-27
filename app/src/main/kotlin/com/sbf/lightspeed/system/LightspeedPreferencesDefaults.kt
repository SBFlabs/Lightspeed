package com.sbf.lightspeed.system

import android.content.Context

/**
 * Initializes default SharedPreferences configuration for Lightspeed avionics,
 * gestures, deflectors, status bar, and refueling bay on first app launch.
 */
object LightspeedPreferencesDefaults {

    fun initializeDefaults(context: Context) {
        val prefs = context.defaultPrefs()
        if (prefs.getBoolean(LightspeedPreferences.KEY_DEFAULTS_INITIALIZED, false)) return

        val editor = prefs.edit()
        editor.putBoolean(LightspeedPreferences.KEY_DEFAULTS_INITIALIZED, true)

        // 1. Tab Accordion Display Profiles & Blueprint Keys (Infinix Profile Defaults)
        editor.putString(LightspeedPreferences.KEY_TAB_ACCORDION_MODE_0, "custom_pinned")
        editor.putString(LightspeedPreferences.KEY_TAB_ACCORDION_MODE_1, "custom_pinned")
        editor.putString(LightspeedPreferences.KEY_TAB_ACCORDION_MODE_2, "custom_pinned")
        editor.putString(LightspeedPreferences.KEY_TAB_PINNED_ACCORDION_0, "left_unified_gestures")
        editor.putString(LightspeedPreferences.KEY_TAB_PINNED_ACCORDION_1, "config_vault")
        editor.putString(LightspeedPreferences.KEY_TAB_PINNED_ACCORDION_2, "unified_gestures")
        editor.putString(LightspeedPreferences.KEY_SUB_PINNED_UNIFIED, "gestures")
        editor.putString(LightspeedPreferences.KEY_SUB_PINNED_LEFT_UNIFIED, "gestures")
        editor.putString("pref_sub_pinned_unified_2", "gestures")

        editor.putString(LightspeedPreferences.KEY_TAB_SECTION_ORDER_0, "left_center,left_unified,left_trackpad")
        editor.putString(LightspeedPreferences.KEY_TAB_SECTION_ORDER_1, "sensor_deck,telemetry_indicators,tactical_hardware,system_overrides,config_vault,experimental_labs")
        editor.putString("pref_tab_order_1", "sensor_deck,telemetry_indicators,tactical_hardware,system_overrides,config_vault,experimental_labs")
        editor.putString(LightspeedPreferences.KEY_TAB_SECTION_ORDER_2, "center,unified,trackpad")
        editor.putString(LightspeedPreferences.KEY_RAIL_DOWNLOADS_ROUTING, "top_line")
        editor.putString(LightspeedPreferences.KEY_RAIL_MEDIA_ROUTING, "none")

        // Deflectors Unified (Linked) by default
        editor.putBoolean(LightspeedPreferences.KEY_SIDEBAR_RIGHT_LINK_FLANK, true)
        editor.putBoolean(LightspeedPreferences.KEY_SIDEBAR_LEFT_LINK_FLANK, true)
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
        editor.putBoolean(LightspeedPreferences.KEY_SUB_POWER_EXPANDED, false)
        editor.putBoolean(LightspeedPreferences.KEY_VOL_GESTURES_ENABLED, false)
        editor.putString(LightspeedPreferences.KEY_VOLUME_SUPPRESSION_PROFILE, LightspeedPreferences.DEFAULT_VOLUME_SUPPRESSION_PROFILE)
        editor.putBoolean(LightspeedPreferences.KEY_POWER_GESTURES_ENABLED, false)
        editor.putBoolean(LightspeedPreferences.KEY_STATUSBAR_SWIPE_DOWN_NOTIFICATIONS, false)

        editor.putInt(LightspeedPreferences.KEY_DEFLECTOR_LONG_SWIPE_THRESHOLD_PORTRAIT, LightspeedPreferences.DEFAULT_DEFLECTOR_LONG_SWIPE_THRESHOLD_PORTRAIT)
        editor.putInt(LightspeedPreferences.KEY_DEFLECTOR_LONG_SWIPE_THRESHOLD_LANDSCAPE, LightspeedPreferences.DEFAULT_DEFLECTOR_LONG_SWIPE_THRESHOLD_LANDSCAPE)

        editor.putBoolean("pref_section_backup_expanded", false)
        editor.putBoolean("pref_section_experimental_labs_expanded", true)

        // Scrub regions unlinked by default (separate deflector controls: Upper = Brightness, Lower = Volume)
        editor.putBoolean(LightspeedPreferences.KEY_UNIFIED_SCRUB_REGIONS_LINKED, false)
        editor.putBoolean(LightspeedPreferences.KEY_UNIFIED_SCRUB_REGIONS_LINKED_RIGHT, false)
        editor.putBoolean(LightspeedPreferences.KEY_UNIFIED_SCRUB_REGIONS_LINKED_LEFT, false)

        // Scrubbing Presets (Infinix Profile)
        editor.putInt("pref_volume_scrub_resolution", 32)
        editor.putInt("pref_volume_scrub_step", 3)
        editor.putInt("pref_brightness_scrub_resolution", 70)
        editor.putInt("pref_brightness_scrub_step", 3)

        // 2. Left Deflector Geometry Defaults
        editor.putInt("pref_sidebar_left_center_height", 70)
        editor.putInt("pref_sidebar_left_center_y_offset", 0)
        editor.putInt("pref_sidebar_left_center_x_offset", 0)
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
        editor.putInt("pref_sidebar_center_x_offset", 0)
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
        editor.putString(LightspeedPreferences.KEY_DEFLECTOR_PILL_STYLE, "razor_edge")
        editor.putString(LightspeedPreferences.KEY_DEFLECTOR_LEFT_PILL_STYLE, "razor_edge")
        editor.putString(LightspeedPreferences.KEY_DEFLECTOR_RIGHT_PILL_STYLE, "razor_edge")
        editor.putBoolean(LightspeedPreferences.KEY_DEFLECTOR_LEFT_GLOW_ENABLED, false)
        editor.putBoolean(LightspeedPreferences.KEY_DEFLECTOR_RIGHT_GLOW_ENABLED, true)
        editor.putBoolean(LightspeedPreferences.KEY_DEFLECTOR_USE_M3_COLOR, false)
        editor.putString(LightspeedPreferences.KEY_DEFLECTOR_LANDSCAPE_MODE, LightspeedPreferences.DEFLECTOR_LANDSCAPE_MODE_CUSTOM)
        editor.putString(LightspeedPreferences.KEY_ORIENTATION_OVERRIDE_EXPIRATION, LightspeedPreferences.DEFAULT_ORIENTATION_OVERRIDE_EXPIRATION)

        // 4. Status Bar (Sensor Area) - enabled by default matching Infinix profile
        editor.putBoolean(LightspeedPreferences.KEY_STATUSBAR_ENABLED, true)
        editor.putInt("pref_statusbar_height", 35)
        editor.putInt("pref_statusbar_span", 200)
        editor.putInt("pref_statusbar_offset_x", 180)
        editor.putInt("pref_statusbar_sensitivity", 25)
        editor.putInt("pref_statusbar_thickness", 35)
        editor.putInt("pref_statusbar_transparency", 0)

        // Landscape / Tablet defaults
        editor.putInt(LightspeedPreferences.KEY_STATUSBAR_SPAN_LANDSCAPE, 320)
        editor.putInt(LightspeedPreferences.KEY_STATUSBAR_THICKNESS_LANDSCAPE, 35)
        editor.putInt(LightspeedPreferences.KEY_STATUSBAR_OFFSET_X_LANDSCAPE, 300)

        // Status Bar Actions (Infinix Profile)
        editor.putString("pref_macro_action_STATUSBAR_TAP", "system:scroll_to_top")
        editor.putString("pref_macro_action_STATUSBAR_TAP_HOLD", "system:gravity_override_360")
        editor.putString("pref_macro_action_STATUSBAR_SWIPE_DOWN", "system:notifications")
        editor.putString("pref_macro_action_STATUSBAR_SWIPE_RIGHT", "system:core_watchdog")
        editor.putString("pref_macro_action_STATUSBAR_SWIPE_LEFT", "system:perimeter_watchdog")
        editor.putString("pref_macro_action_STATUSBAR_SWIPE_RIGHT_HOLD", "system:popup_window")
        editor.putString("pref_macro_action_STATUSBAR_SWIPE_LEFT_HOLD", "system:split_screen")

        // HUD & Scrubber Defaults (Infinix Profile)
        editor.putBoolean(LightspeedPreferences.KEY_HUD_BRIGHTNESS_ENABLED, true)
        editor.putBoolean(LightspeedPreferences.KEY_HUD_VOLUME_ENABLED, true)
        editor.putBoolean(LightspeedPreferences.KEY_VOLUME_SHOW_NATIVE_SLIDER, false)
        editor.putString(LightspeedPreferences.KEY_HUD_STYLE_DEFAULT, "canopy_droppod")
        editor.putString(LightspeedPreferences.KEY_POPUP_STYLE, LightspeedPreferences.POPUP_STYLE_NATIVE)

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
