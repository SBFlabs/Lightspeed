package com.sbf.lightspeed.system

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.media.AudioManager
import android.provider.Settings
import com.sbf.lightspeed.LightspeedAccessibilityService
import com.sbf.lightspeed.LightspeedStatusBarOverlay
import com.sbf.lightspeed.system.display.LightspeedEyeCareEngine
import com.sbf.lightspeed.system.display.LightspeedSmartPixelsOverlay
import com.sbf.lightspeed.system.sonic.SonicVoiceIsolationEngine

object ActionDispatcherSonicDisplay {
    private const val TAG = "ActionDispatcherSonicDisplay"

    fun handleSonicOrDisplay(context: Context, token: String, service: AccessibilityService?): Boolean {
        when (token) {
            "system:omniscient_audio", "system:sonic_deck", "system:audio_dock" -> {
                val svc = service ?: LightspeedAccessibilityService.instance
                svc?.let { SonicDeckManager.show(it) }
                return true
            }
            "system:vista_deck", "system:display_dock" -> {
                val svc = service ?: LightspeedAccessibilityService.instance
                svc?.let { VistaDeckManager.show(it) }
                return true
            }
            "system:volume" -> {
                val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
                val showNative = LightspeedPreferences.isVolumeShowNativeSlider(context)
                val flags = if (showNative) AudioManager.FLAG_SHOW_UI else 0
                audioManager?.adjustSuggestedStreamVolume(
                    AudioManager.ADJUST_SAME,
                    AudioManager.USE_DEFAULT_STREAM_TYPE,
                    flags
                )
                if (LightspeedPreferences.isHudVolumeEnabled(context)) {
                    val cur = audioManager?.getStreamVolume(AudioManager.STREAM_MUSIC) ?: 0
                    val max = audioManager?.getStreamMaxVolume(AudioManager.STREAM_MUSIC) ?: 15
                    val pct = kotlin.math.round(cur * 100f / max.coerceAtLeast(1)).toInt().coerceIn(0, 100)
                    val volResolution = LightspeedPreferences.getVolumeScrubResolution(context)
                    LightspeedStatusBarOverlay.showActionHud(
                        title = "MEDIA VOLUME",
                        value = "$pct%",
                        stepIndex = (pct * volResolution / 100).coerceIn(0, volResolution),
                        totalSteps = volResolution
                    )
                }
                return true
            }
            "system:brightness" -> {
                if (Settings.System.canWrite(context)) {
                    val current = com.sbf.lightspeed.system.display.LightspeedDisplayWellnessResetEngine.getSystemBrightness(context)
                    val steps = listOf(1, 51, 102, 153, 204, 255)
                    val next = steps.firstOrNull { it > current + 10 } ?: steps.first()
                    try {
                        com.sbf.lightspeed.system.display.LightspeedDisplayWellnessResetEngine.setSystemBrightness(context, next)
                        if (LightspeedPreferences.isHudBrightnessEnabled(context)) {
                            LightspeedStatusBarOverlay.showActionHud(
                                title = "BRIGHTNESS",
                                value = "${(next * 100 / 255)}%",
                                stepIndex = (next * 10 / 255),
                                totalSteps = 10
                            )
                        }
                    } catch (e: Exception) {
                        logSwallowed(TAG, "brightness:execute", e)
                    }
                } else {
                    LightspeedTimeoutEngine.requestWriteSettingsPermission(context)
                }
                return true
            }
            "system:smart_pixels_toggle" -> {
                val prefs = context.defaultPrefs()
                val current = prefs.getBoolean(LightspeedPreferences.KEY_SMART_PIXELS_ENABLED, false)
                val pattern = prefs.getInt(LightspeedPreferences.KEY_SMART_PIXELS_PATTERN, 25)
                LightspeedSmartPixelsOverlay.setEnabled(context, !current, pattern)
                return true
            }
            "system:eye_care_toggle" -> {
                val prefs = context.defaultPrefs()
                val current = prefs.getBoolean(LightspeedPreferences.KEY_EYE_CARE_ENABLED, false)
                val temp = prefs.getInt(LightspeedPreferences.KEY_EYE_CARE_TEMP, 3200)
                LightspeedEyeCareEngine.applyEyeCare(context, !current, temp)
                return true
            }
            "system:extra_dim_toggle" -> {
                val prefs = context.defaultPrefs()
                val current = prefs.getBoolean(LightspeedPreferences.KEY_EXTRA_DIM_ENABLED, false)
                val level = prefs.getInt(LightspeedPreferences.KEY_EXTRA_DIM_LEVEL, 40)
                LightspeedEyeCareEngine.applyExtraDim(context, !current, level)
                return true
            }
            "system:vocal_isolation_toggle" -> {
                val current = SonicVoiceIsolationEngine.isEnabled
                SonicVoiceIsolationEngine.setEnabled(context, !current)
                return true
            }
            "system:volume_hud_show" -> {
                val svc = service ?: LightspeedAccessibilityService.instance
                svc?.let { SonicDeckManager.show(it) }
                return true
            }
            else -> return false
        }
    }
}
