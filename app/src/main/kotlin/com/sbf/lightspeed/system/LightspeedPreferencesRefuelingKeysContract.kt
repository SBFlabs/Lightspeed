package com.sbf.lightspeed.system

/**
 * SharedPreferences keys for Refueling Bay stack, grid, and sleep configurations.
 */
interface LightspeedPreferencesRefuelingKeysContract {
    val KEY_REFUELING_BAY_TRIGGER: String get() = "pref_refueling_bay_trigger"
    val KEY_REFUELING_DISMISS_BEHAVIOR: String get() = "pref_refueling_dismiss_behavior"
    val KEY_REFUELING_BATTERY_STYLE: String get() = "pref_refueling_battery_style"
    val KEY_REFUELING_SLEEP_TIMEOUT: String get() = "pref_refueling_sleep_timeout"
    val KEY_REFUELING_SLEEP_ACTION: String get() = "pref_refueling_sleep_action"
    val KEY_REFUELING_SLEEP_BLACKOUT: String get() = "pref_bay_sleep_blackout"
    val KEY_REFUELING_SPLIT_RATIO_PORTRAIT: String get() = "pref_refueling_split_ratio_portrait"
    val KEY_REFUELING_SPLIT_RATIO_LANDSCAPE: String get() = "pref_refueling_split_ratio_landscape"
    val KEY_REFUELING_PIXEL_SHIFT: String get() = "pref_refueling_pixel_shift"
    val KEY_REFUELING_AS_LOCKSCREEN: String get() = "pref_refueling_as_lockscreen"
    val KEY_REFUELING_WIDGET_ID: String get() = "pref_refueling_widget_id"
    val KEY_REFUELING_WIDGET_IDS: String get() = "pref_refueling_widget_ids"
    val KEY_REFUELING_STACK_PROFILE: String get() = "pref_refueling_stack_profile"
    val KEY_REFUELING_GRID_PROFILE: String get() = "pref_refueling_grid_profile"
    val KEY_REFUELING_GRID_PORTRAIT_PROFILE: String get() = "pref_refueling_grid_portrait_profile"
    val KEY_REFUELING_GRID_LANDSCAPE_PROFILE: String get() = "pref_refueling_grid_landscape_profile"
    val KEY_REFUELING_WIDGET_LAYOUT: String get() = "pref_refueling_widget_layout"
    val KEY_REFUELING_STACK_REMEMBER_PAGE: String get() = "pref_refueling_stack_remember_page"
    val KEY_REFUELING_STACK_LAST_PAGE: String get() = "refueling_stack_memory"
    val KEY_REFUELING_CLOCK_FORMAT: String get() = "pref_refueling_clock_format"
    val KEY_REFUELING_DATE_FORMAT: String get() = "pref_refueling_date_format"
    val KEY_REFUELING_CLOCK_FONT: String get() = "pref_refueling_clock_font"
    val KEY_REFUELING_SHOW_ALARM: String get() = "pref_refueling_show_alarm"
    val KEY_CAPSULE_WIDGET_ID: String get() = "pref_capsule_widget_id"
}
