package com.sbf.lightspeed.settings

import android.content.Context
import android.net.Uri
import com.sbf.lightspeed.system.LightspeedLanguageEngine
import com.sbf.lightspeed.system.LightspeedPreferences
import com.sbf.lightspeed.system.LightspeedShortcutManager
import com.sbf.lightspeed.system.LightspeedVocabulary
import com.sbf.lightspeed.system.defaultPrefs

object ActionTokenResolver {

    /**
     * Maps action token aliases and legacy identifiers to their canonical representation.
     */
    fun canonicalToken(raw: String?): String = when (raw) {
        null, ""                                               -> "none"
        "action_enter_gearset_nav", "ACTION_ENTER_GEARSET_NAV" -> "system:gearset_nav"
        "ACTION_PREVIOUS_APP", "previous_app"                  -> "system:previous_app"
        "ACTION_SPLIT_SCREEN", "split_screen"                  -> "system:split_screen"
        "popup_window", "system:popup_window", "system:freeform" -> "system:freeform"
        "ACTION_FLASHLIGHT", "flashlight", "system:torch"      -> "system:flashlight"
        "ACTION_SCREENSHOT", "screenshot"                      -> "system:screenshot"
        "ACTION_LOCK_SCREEN", "lock_screen"                    -> "system:lock_screen"
        "ACTION_POWER_MENU", "power_menu"                      -> "system:power_menu"
        "system:battery_whitelist", "system:battery_exemption", "battery_exemption", "ACTION_BATTERY_EXEMPTION" -> "system:battery_exemption"
        "ACTION_CLOSE_APP", "close_app"                        -> "system:close_app"
        "ACTION_SCROLL_TO_TOP", "scroll_to_top"                -> "system:scroll_to_top"
        "ACTION_SCROLL_TO_BOTTOM", "scroll_to_bottom"          -> "system:scroll_to_bottom"
        "ACTION_SCROLL_SCRUB", "scroll_scrub", "scrub:scroll"   -> "system:scroll_scrub"
        "ACTION_HOME", "home"                                  -> "system:home"
        "ACTION_BACK", "back"                                  -> "system:back"
        "ACTION_RECENTS", "recents"                            -> "system:recents"
        "ACTION_NOTIFICATIONS", "notifications"                -> "system:notifications"
        "ACTION_QUICK_SETTINGS", "quick_settings"              -> "system:quick_settings"
        "ACTION_SCREEN_TIMEOUT", "screen_timeout"              -> "system:screen_timeout"
        "ACTION_MEDIA_PLAY_PAUSE", "media_play_pause"          -> "system:media_play_pause"
        "ACTION_MEDIA_NEXT", "media_next"                      -> "system:media_next"
        "ACTION_MEDIA_PREV", "media_prev"                      -> "system:media_prev"
        "ACTION_MEDIA_SKIP_FORWARD", "media_skip_forward"      -> "system:media_skip_forward"
        "ACTION_MEDIA_SKIP_BACKWARD", "media_skip_backward"    -> "system:media_skip_backward"
        "ACTION_MEDIA_SCRUBBER", "media_scrubber"              -> "system:media_scrubber"
        "ACTION_MEDIA_STOP", "media_stop"                      -> "system:media_stop"
        "ACTION_REFUELING_BAY", "refueling_bay"                -> "system:refueling_bay"
        "ACTION_GLOW_DEFLECTORS", "glow_deflectors"            -> "system:glow_deflectors"
        "system:audio_dock", "system:sonic_deck"               -> "system:omniscient_audio"
        "system:display_dock", "system:vista_deck", "vista_deck" -> "system:vista_deck"
        "ACTION_TACTICAL_FLYOUT", "action_quick_flyout", "system:quick_flyout" -> "system:tactical_flyout"
        "ACTION_LENS", "google_lens"                           -> "system:lens"
        "ACTION_QR_SCANNER", "qr_scanner"                      -> "system:qr_scanner"
        "ACTION_CHATGPT", "chatgpt"                            -> "system:chatgpt"
        "ACTION_CLAUDE", "claude"                              -> "system:claude"
        "ACTION_GEMINI", "gemini"                              -> "system:gemini"
        "ACTION_CAMERA_PHOTO", "camera_photo", "system:camera" -> "system:camera_photo"
        "ACTION_CAMERA_VIDEO", "camera_video"                  -> "system:camera_video"
        "ACTION_CORE_COOLING", "core_cooling"                  -> "system:core_cooling"
        "system:gravity_reset", "ACTION_GRAVITY_RESET"         -> "system:gravity_reset"
        "system:auto_rotate_toggle", "system:toggle_auto_rotate", "auto_rotate_toggle",
        "system:gravity_toggle_master", "system:orientation_toggle", "orientation_toggle" -> "system:auto_rotate_toggle"
        "system:gravity_override_360", "system:orientation_sensor_360", "orientation_sensor_360" -> "system:gravity_override_360"
        "system:gravity_override_landscape"                    -> "system:gravity_override_landscape"
        "system:gravity_override_sensor_portrait", "system:orientation_sensor_portrait", "orientation_sensor_portrait" -> "system:gravity_override_sensor_portrait"
        "system:central_command", "central_command"             -> "system:central_command"
        else -> raw ?: "none"
    }

    /**
     * Resolves the default pre-assigned action token for a given gesture preference key.
     */
    fun getDefaultActionForGestureKey(key: String): String {
        val cleanKey = key.substringAfterLast(":string/")

        return when {
            // STATUSBAR / SENSOR DECK DEFAULTS
            cleanKey == "pref_macro_action_STATUSBAR_DOUBLE_TAP" -> "system:gravity_reset"
            cleanKey == "pref_macro_action_STATUSBAR_DOUBLE_TAP_HOLD" -> "system:screen_timeout"
            cleanKey == "pref_macro_action_STATUSBAR_TAP" -> "system:scroll_to_top"
            cleanKey == "pref_macro_action_STATUSBAR_TAP_HOLD" -> "system:gravity_override_360"
            cleanKey == "pref_macro_action_STATUSBAR_SWIPE_RIGHT" -> "system:core_watchdog"
            cleanKey == "pref_macro_action_STATUSBAR_SWIPE_LEFT" -> "system:perimeter_watchdog"
            cleanKey == "pref_macro_action_STATUSBAR_SWIPE_RIGHT_HOLD" -> "system:popup_window"
            cleanKey == "pref_macro_action_STATUSBAR_SWIPE_LEFT_HOLD" -> "system:split_screen"
            cleanKey == "pref_macro_action_STATUSBAR_SWIPE_DOWN" -> "system:notifications"

            // LEFT FLANK DEFAULTS (LEFT_UNIFIED, LEFT_TOP, LEFT_BOTTOM)
            cleanKey.contains("LEFT_") -> when {
                cleanKey.endsWith("_SWIPE_RIGHT") -> "system:back"
                cleanKey.endsWith("_SWIPE_RIGHT_HOLD") -> "system:close_app"
                cleanKey.endsWith("_SWIPE_DOWN") -> "system:auto_rotate_toggle"
                cleanKey.endsWith("_SWIPE_DOWN_UP") -> "system:recents"
                cleanKey.endsWith("_SWIPE_UP") -> "system:gravity_override_360"
                cleanKey.endsWith("_SWIPE_UP_DOWN") -> "system:home"
                cleanKey.endsWith("_SWIPE_RIGHT_UP") -> "system:screenshot"
                cleanKey.endsWith("_SWIPE_RIGHT_DOWN") -> "system:notifications"
                cleanKey.endsWith("_SWIPE_RIGHT_DOWN_HOLD") -> "system:quick_settings"
                cleanKey.contains("BOTTOM") && (cleanKey.endsWith("_TAP_HOLD") || cleanKey.endsWith("_SCRUBBING")) -> "system:brightness"
                cleanKey.endsWith("_TAP_HOLD") -> "system:volume"
                cleanKey.endsWith("_SCRUBBING") -> "system:volume"
                else -> "none"
            }

            // RIGHT FLANK DEFAULTS (UNIFIED, TOP, BOTTOM)
            cleanKey.endsWith("_SWIPE_LEFT") -> "system:back"
            cleanKey.endsWith("_SWIPE_LEFT_HOLD") -> "system:close_app"
            cleanKey.endsWith("_SWIPE_DOWN") -> "system:home"
            cleanKey.endsWith("_SWIPE_UP") -> "system:recents"
            cleanKey.endsWith("_SWIPE_LEFT_DOWN") -> "system:notifications"
            cleanKey.endsWith("_SWIPE_LEFT_DOWN_HOLD") -> "system:quick_settings"
            cleanKey.endsWith("_SWIPE_LEFT_UP") -> "system:previous_app"
            cleanKey.endsWith("_SWIPE_UP_DOWN") -> "system:refueling_bay"
            cleanKey.contains("BOTTOM") && (cleanKey.endsWith("_TAP_HOLD") || cleanKey.endsWith("_SCRUBBING")) -> "system:volume"
            cleanKey.endsWith("_TAP_HOLD") -> "system:brightness"
            cleanKey.endsWith("_SCRUBBING") -> "system:brightness"

            else -> "none"
        }
    }

    /**
     * Resolves human-readable labels for system and app tokens.
     */
    fun resolveDynamicTokenLabel(context: Context, token: String): String {
        return when {
            token == "none" -> "None"
            token == "action_enter_gearset_nav" || token == "system:gearset_nav" -> "Gear Set HUD Navigation Mode"
            token == "system:previous_app" -> "Switch to Previous App (Shizuku)"
            token == "system:close_app" || token == "shizuku:close_app" -> "Close App Gracefully & Remove from Recents (Shizuku)"
            token == "system:recents" -> "Recents Overview"
            token == "system:home" -> "Home"
            token == "system:back" -> "Back"
            token == "system:split_screen" -> "Split Screen"
            token == "system:popup_window" || token == "system:freeform" -> "Pop-up Window"

            token == "system:flashlight" -> "External Torch"
            token == "system:screenshot" -> "Take Screenshot"
            token == "system:lock_screen" -> "Lock Ship"
            token == "system:power_menu" -> "Power Menu"
            token == "system:notifications" -> "Notification Shade"
            token == "system:quick_settings" -> "Quick Settings"
            token == "system:scroll_to_top" -> "Scroll to Top"
            token == "system:scroll_to_bottom" -> "Scroll to Bottom"
            token == "system:auto_rotate_toggle" || token == "system:toggle_auto_rotate" || token == "system:gravity_toggle_master" || token == "ACTION_GRAVITY_TOGGLE_MASTER" || token == "system:orientation_toggle" || token == "ACTION_TOGGLE_ROTATION" -> {
                val behavior = LightspeedPreferences.getActionBehavior(context, "system:auto_rotate_toggle")
                when (behavior) {
                    "button" -> "Native Auto-Rotate (One-Way ON)"
                    "button_off" -> "Native Auto-Rotate (One-Way OFF)"
                    else -> "Toggle Native Auto-Rotate"
                }
            }
            token == "system:gravity_reset" || token == "ACTION_GRAVITY_RESET" -> "Restore Default Gravity"
            token == "system:gravity_override_360" || token == "ACTION_GRAVITY_OVERRIDE_360" || token == "system:orientation_sensor_360" -> {
                if (LightspeedPreferences.getActionBehavior(context, "system:gravity_override_360") == "button") "Transient 360° Gyro (One-Way Button)" else "Transient 360° Gyro"
            }
            token == "system:gravity_override_landscape" || token == "ACTION_GRAVITY_OVERRIDE_LANDSCAPE" -> {
                if (LightspeedPreferences.getActionBehavior(context, "system:gravity_override_landscape") == "button") "Transient Landscape (One-Way Button)" else "Transient Landscape"
            }
            token == "system:gravity_override_portrait" || token == "ACTION_GRAVITY_OVERRIDE_PORTRAIT" || token == "system:orientation_portrait" -> {
                if (LightspeedPreferences.getActionBehavior(context, "system:gravity_override_portrait") == "button") "Transient Portrait 0° (One-Way Button)" else "Transient Portrait"
            }
            token == "system:gravity_override_sensor_portrait" || token == "ACTION_GRAVITY_OVERRIDE_SENSOR_PORTRAIT" || token == "system:orientation_sensor_portrait" -> {
                if (LightspeedPreferences.getActionBehavior(context, "system:gravity_override_sensor_portrait") == "button") "Transient Sensor Portrait (One-Way Button)" else "Transient Sensor Portrait"
            }

            token == "system:screen_timeout" -> "Ship Goes Dark"
            token == "system:volume" -> "Media Volume"
            token == "system:brightness" -> "Brightness Scrubber"
            token == "system:scroll_scrub" -> "Scroll Scrubbing"

            token == "system:media_play_pause" -> "Media: Play / Pause"
            token == "system:media_next" -> "Media: Next Track"
            token == "system:media_prev" -> "Media: Previous Track"
            token == "system:media_skip_forward" -> {
                val skipSec = LightspeedPreferences.getMediaSkipSeconds(context.defaultPrefs(), forward = true)
                "Media: Skip Forward (${skipSec}s)"
            }
            token == "system:media_skip_backward" -> {
                val skipSec = LightspeedPreferences.getMediaSkipSeconds(context.defaultPrefs(), forward = false)
                "Media: Skip Backward (${skipSec}s)"
            }
            token == "system:media_scrubber" -> "Media: Timeline Scrubber (HUD)"
            token == "system:media_stop" -> "Media: Stop Playback"
            token == "system:refueling_bay" -> LightspeedLanguageEngine.resolve(LightspeedVocabulary.Key.REFUELING_BAY)
            token == "system:core_cooling" || token == "ACTION_CORE_COOLING" -> LightspeedLanguageEngine.resolve(LightspeedVocabulary.Key.CORE_COOLING)
            token == "system:perimeter_watchdog" -> LightspeedLanguageEngine.resolve(LightspeedVocabulary.Key.PERIMETER_DEFENSE)
            token == "system:core_watchdog" -> LightspeedLanguageEngine.resolve(LightspeedVocabulary.Key.CORE_WATCHDOG)
            token == "system:battery_exemption" -> LightspeedLanguageEngine.resolve(LightspeedVocabulary.Key.BATTERY_EXEMPTION)
            token == "system:central_command" -> "Central Command (Cockpit Deck)"
            token == "system:omniscient_audio" || token == "system:audio_dock" || token == "system:sonic_deck" -> LightspeedLanguageEngine.resolve(LightspeedVocabulary.Key.SONIC_DECK)
            token == "system:vista_deck" || token == "system:display_dock" -> LightspeedLanguageEngine.resolve(LightspeedVocabulary.Key.VISTA_DECK)
            token == "system:smart_pixels_toggle" -> "OLED Smart Pixels (Toggle)"
            token == "system:eye_care_toggle" -> "Eye Care Blue Light (Toggle)"
            token == "system:extra_dim_toggle" -> "Extra Dim (Toggle)"
            token == "system:vocal_isolation_toggle" -> "Neural Vocal Isolation (Toggle)"
            token == "system:volume_hud_show" -> "Sonic Volume HUD (Show)"
            token.startsWith("bot:macro_") -> {
                val num = token.removePrefix("bot:macro_")
                "Bot Macro #$num"
            }
            token.startsWith("app:") -> {
                val pkg = token.removePrefix("app:")
                try {
                    val pm = context.packageManager
                    pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString()
                } catch (_: Exception) {
                    pkg.substringAfterLast(".")
                }
            }
            token.startsWith("shortcut:") -> {
                val resolved = LightspeedShortcutManager.resolveLabel(context, token)
                if (resolved.isNotBlank()) resolved else {
                    val raw = token.substringAfter("label=").substringBefore(";")
                    try { Uri.decode(raw) } catch (_: Exception) { raw }
                }
            }
            else -> token
        }
    }
}
