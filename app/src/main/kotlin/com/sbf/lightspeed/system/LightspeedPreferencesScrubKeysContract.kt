package com.sbf.lightspeed.system

/**
 * SharedPreferences keys for HUD Volume, Video Brightness Takeover, and Auto-Reset.
 */
interface LightspeedPreferencesScrubKeysContract {
    val KEY_VOL_HUD_PER_APP_COLLAPSED: String get() = "pref_vol_hud_per_app_collapsed"
    val KEY_BRIGHTNESS_VIDEO_TAKEOVER: String get() = "pref_brightness_video_takeover"
    val KEY_BRIGHTNESS_AUTO_RESET_ENABLED: String get() = "pref_brightness_auto_reset_enabled"
    val KEY_BRIGHTNESS_AUTO_RESET_TRIGGERS: String get() = "pref_brightness_auto_reset_triggers"
    val KEY_BRIGHTNESS_AUTO_RESET_MODE: String get() = "pref_brightness_auto_reset_mode"

    val AUTO_RESET_OFF: String get() = "off"
    val AUTO_RESET_SCREEN_OFF: String get() = "screen_off"
    val AUTO_RESET_APP_SWITCH: String get() = "app_switch"
    val AUTO_RESET_RECENTS_CLOSE: String get() = "recents_close"

    val KEY_BRIGHTNESS_SCRUB_TOP_EXTREME: String get() = "pref_brightness_scrub_top_extreme"
    val KEY_BRIGHTNESS_SCRUB_BOTTOM_EXTREME: String get() = "pref_brightness_scrub_bottom_extreme"

    val SCRUB_EXTREME_NONE: String get() = "none"
    val SCRUB_EXTREME_EXTRA_DIM: String get() = "extra_dim"
    val SCRUB_EXTREME_SMART_PIXELS: String get() = "smart_pixels"
    val SCRUB_EXTREME_EYE_CARE: String get() = "eye_care"
}
