package com.sbf.lightspeed.settings

import android.content.Context
import android.net.Uri
import com.sbf.lightspeed.R
import com.sbf.lightspeed.system.LightspeedLanguageEngine
import com.sbf.lightspeed.system.LightspeedPreferences
import com.sbf.lightspeed.system.LightspeedShortcutManager
import com.sbf.lightspeed.system.LightspeedVocabulary
import com.sbf.lightspeed.system.defaultPrefs
import com.sbf.lightspeed.system.lsString

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
        "ACTION_DIRECT_CALL", "direct_call", "action:direct_call" -> "system:direct_call"
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
        "system:audio_dock", "system:omniscient_audio", "system:sonic_deck" -> "system:sonic_deck"
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
        "system:shields_down" -> "system:shields_down"
        "action:pass_through_touch", "pass_through_touch", "system:pass_through_touch" -> "system:pass_through_touch"
        "system:native_wifi_settings", "native_wifi_settings" -> "system:native_wifi_settings"
        "system:native_bluetooth_settings", "native_bluetooth_settings" -> "system:native_bluetooth_settings"
        "system:native_battery_settings", "native_battery_settings" -> "system:native_battery_settings"
        "system:native_digital_wellbeing", "native_digital_wellbeing" -> "system:native_digital_wellbeing"
        "action:sonic_deck", "sonic_deck" -> "system:sonic_deck"
        "action:vista_deck", "vista_deck" -> "system:vista_deck"
        "action:circle_to_search", "circle_to_search", "system:circle_to_search" -> "system:circle_to_search"
        "action:assistant", "assistant", "system:assistant", "ACTION_ASSISTANT" -> "system:assistant"
        else -> raw ?: "none"
    }

    /**
     * Resolves the default pre-assigned action token for a given gesture preference key.
     */
    fun getDefaultActionForGestureKey(key: String): String =
        com.sbf.lightspeed.system.LightspeedPreferencesDefaults.getDefaultActionForGestureKey(key)

    /**
     * Resolves human-readable labels for system and app tokens.
     */
    fun resolveDynamicTokenLabel(context: Context, token: String): String {
        return when {
            token == "none" -> lsString(R.string.cc_common_none)
            token == "action_enter_gearset_nav" || token == "system:gearset_nav" -> lsString(R.string.act_gearset_nav)
            token == "system:previous_app" -> lsString(R.string.act_previous_app)
            token == "system:close_app" || token == "shizuku:close_app" -> lsString(R.string.act_close_app)
            token == "system:recents" -> lsString(R.string.act_recents)
            token == "system:home" -> lsString(R.string.act_home)
            token == "system:back" -> lsString(R.string.act_back)
            token == "system:split_screen" -> lsString(R.string.act_split_screen)
            token == "system:popup_window" || token == "system:freeform" -> lsString(R.string.act_popup_window)
            token == "system:circle_to_search" || token == "action:circle_to_search" -> lsString(R.string.act_circle_to_search)
            token == "system:assistant" || token == "action:assistant" -> lsString(R.string.act_assistant)

            token == "system:flashlight" -> lsString(R.string.act_flashlight)
            token == "system:direct_call" -> lsString(R.string.act_direct_call)
            token == "system:screenshot" -> lsString(R.string.act_screenshot)
            token == "system:lock_screen" -> lsString(R.string.act_lock_screen)
            token == "system:power_menu" -> lsString(R.string.act_power_menu)
            token == "system:notifications" -> lsString(R.string.act_notifications)
            token == "system:quick_settings" -> lsString(R.string.act_quick_settings)
            token == "system:scroll_to_top" -> lsString(R.string.act_scroll_to_top)
            token == "system:scroll_to_bottom" -> lsString(R.string.act_scroll_to_bottom)
            token == "system:auto_rotate_toggle" || token == "system:toggle_auto_rotate" || token == "system:gravity_toggle_master" || token == "ACTION_GRAVITY_TOGGLE_MASTER" || token == "system:orientation_toggle" || token == "ACTION_TOGGLE_ROTATION" -> {
                val behavior = LightspeedPreferences.getActionBehavior(context, "system:auto_rotate_toggle")
                when (behavior) {
                    "button" -> lsString(R.string.act_autorotate_on)
                    "button_off" -> lsString(R.string.act_autorotate_off)
                    else -> lsString(R.string.act_autorotate_toggle)
                }
            }
            token == "system:gravity_reset" || token == "ACTION_GRAVITY_RESET" -> lsString(R.string.act_gravity_reset)
            token == "system:gravity_override_360" || token == "ACTION_GRAVITY_OVERRIDE_360" || token == "system:orientation_sensor_360" -> {
                if (LightspeedPreferences.getActionBehavior(context, "system:gravity_override_360") == "button") lsString(R.string.act_gyro_360_btn) else lsString(R.string.act_gyro_360)
            }
            token == "system:gravity_override_landscape" || token == "ACTION_GRAVITY_OVERRIDE_LANDSCAPE" -> {
                if (LightspeedPreferences.getActionBehavior(context, "system:gravity_override_landscape") == "button") lsString(R.string.act_landscape_btn) else lsString(R.string.act_landscape)
            }
            token == "system:gravity_override_portrait" || token == "ACTION_GRAVITY_OVERRIDE_PORTRAIT" || token == "system:orientation_portrait" -> {
                if (LightspeedPreferences.getActionBehavior(context, "system:gravity_override_portrait") == "button") lsString(R.string.act_portrait_btn) else lsString(R.string.act_portrait)
            }
            token == "system:gravity_override_sensor_portrait" || token == "ACTION_GRAVITY_OVERRIDE_SENSOR_PORTRAIT" || token == "system:orientation_sensor_portrait" -> {
                if (LightspeedPreferences.getActionBehavior(context, "system:gravity_override_sensor_portrait") == "button") lsString(R.string.act_sensor_portrait_btn) else lsString(R.string.act_sensor_portrait)
            }

            token == "system:screen_timeout" -> lsString(R.string.act_screen_timeout)
            token == "system:volume" -> lsString(R.string.act_volume)
            token == "system:brightness" -> lsString(R.string.act_brightness)
            token == "system:scroll_scrub" -> lsString(R.string.act_scroll_scrub)

            token == "system:media_play_pause" -> lsString(R.string.act_media_play_pause)
            token == "system:media_next" -> lsString(R.string.act_media_next)
            token == "system:media_prev" -> lsString(R.string.act_media_prev)
            token == "system:media_skip_forward" -> {
                val skipSec = LightspeedPreferences.getMediaSkipSeconds(context.defaultPrefs(), forward = true)
                lsString(R.string.act_media_skip_fwd, skipSec)
            }
            token == "system:media_skip_backward" -> {
                val skipSec = LightspeedPreferences.getMediaSkipSeconds(context.defaultPrefs(), forward = false)
                lsString(R.string.act_media_skip_back, skipSec)
            }
            token == "system:media_scrubber" -> lsString(R.string.act_media_scrubber)
            token == "system:media_stop" -> lsString(R.string.act_media_stop)
            token == "system:refueling_bay" -> LightspeedLanguageEngine.resolve(LightspeedVocabulary.Key.REFUELING_BAY)
            token == "system:core_cooling" || token == "ACTION_CORE_COOLING" -> LightspeedLanguageEngine.resolve(LightspeedVocabulary.Key.CORE_COOLING)
            token == "system:perimeter_watchdog" -> LightspeedLanguageEngine.resolve(LightspeedVocabulary.Key.PERIMETER_DEFENSE)
            token == "system:core_watchdog" -> LightspeedLanguageEngine.resolve(LightspeedVocabulary.Key.CORE_WATCHDOG)
            token == "system:battery_exemption" -> LightspeedLanguageEngine.resolve(LightspeedVocabulary.Key.BATTERY_EXEMPTION)
            token == "system:native_wifi_settings" -> lsString(R.string.act_native_wifi)
            token == "system:native_bluetooth_settings" -> lsString(R.string.act_native_bluetooth)
            token == "system:native_battery_settings" -> lsString(R.string.act_native_battery)
            token == "system:native_digital_wellbeing" -> lsString(R.string.act_native_wellbeing)
            token == "system:central_command" -> lsString(R.string.act_central_command)
            token == "system:omniscient_audio" || token == "system:audio_dock" || token == "system:sonic_deck" -> LightspeedLanguageEngine.resolve(LightspeedVocabulary.Key.SONIC_DECK)
            token == "system:vista_deck" || token == "system:display_dock" || token == "action:vista_deck" -> LightspeedLanguageEngine.resolve(LightspeedVocabulary.Key.VISTA_DECK)
            token == "system:tactical_flyout" || token == "action:tactical_flyout" -> lsString(R.string.act_tactical_flyout)
            token == "system:shields_down" || token == "action:shields_down" || token == "action:cold_shutdown" -> lsString(R.string.act_shields_down)
            token == "system:pass_through_touch" || token == "action:pass_through_touch" -> lsString(R.string.act_pass_through)
            token == "system:smart_pixels_toggle" -> lsString(R.string.act_smart_pixels)
            token == "system:eye_care_toggle" -> lsString(R.string.act_eye_care)
            token == "system:extra_dim_toggle" -> lsString(R.string.act_extra_dim)
            token == "system:vocal_isolation_toggle" -> lsString(R.string.act_vocal_isolation)
            token == "system:volume_hud_show" -> lsString(R.string.act_volume_hud_show)
            token.startsWith("bot:macro_") -> {
                val num = token.removePrefix("bot:macro_")
                lsString(R.string.act_bot_macro, num)
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
