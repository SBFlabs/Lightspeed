package com.sbf.lightspeed

import android.content.Context
import android.content.SharedPreferences
import android.media.AudioManager
import android.provider.Settings
import android.util.Log
import com.sbf.lightspeed.system.LightspeedPreferences
import com.sbf.lightspeed.system.LightspeedTimeoutEngine
import kotlin.math.abs
import kotlin.math.roundToInt

data class ScrubResult(
    val title: String,
    val value: String,
    val currentValue: Int,
    val stepIndex: Int,
    val totalSteps: Int
)

object CruiseScrubEngine {

    fun dispatchScrubHud(
        context: Context,
        prefs: SharedPreferences,
        scrubType: String,
        actionKey: String?,
        title: String,
        value: String,
        stepIndex: Int,
        totalSteps: Int
    ) {
        val isBrightness = scrubType == "scrub:brightness" || scrubType == "system:brightness" || title == "BRIGHTNESS"
        val isVolume = scrubType == "scrub:volume" || scrubType == "system:volume" || title == "MEDIA VOLUME"
        val showHud = when {
            isBrightness -> prefs.getBoolean(LightspeedPreferences.KEY_HUD_BRIGHTNESS_ENABLED, true)
            isVolume -> prefs.getBoolean(LightspeedPreferences.KEY_HUD_VOLUME_ENABLED, true)
            else -> true
        }
        if (showHud) {
            val fallbackKey = actionKey ?: "pref_macro_action_SCRUBBING"
            val hudStyle = LightspeedPreferences.resolveHudStyle(prefs, fallbackKey, scrubType)

            LightspeedStatusBarOverlay.showActionHud(
                title = title,
                value = value,
                stepIndex = stepIndex,
                totalSteps = totalSteps,
                durationMs = 0L,
                style = hudStyle
            )
        }
    }

    fun executeVolumeScrub(
        context: Context,
        prefs: SharedPreferences,
        audioManager: AudioManager,
        currentPct: Int,
        steps: Int,
        onHaptic: (() -> Unit)? = null
    ): ScrubResult {
        val volResolution = prefs.getInt(LightspeedPreferences.KEY_VOLUME_SCRUB_RESOLUTION, 100).coerceIn(5, 100)
        val volStep = prefs.getInt(LightspeedPreferences.KEY_VOLUME_SCRUB_STEP, (100f / volResolution).roundToInt().coerceIn(1, 20))
        val maxVol = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
        val curPct = if (currentPct < 0) {
            val curVol = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
            kotlin.math.round(curVol * 100f / maxVol.coerceAtLeast(1)).toInt().coerceIn(0, 100)
        } else currentPct
        val targetPct = (curPct + (steps * volStep)).coerceIn(0, 100)
        val targetStreamVol = kotlin.math.round(targetPct * maxVol / 100f).toInt().coerceIn(0, maxVol)
        val currentVol = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
        val showNativeUi = prefs.getBoolean(LightspeedPreferences.KEY_VOLUME_SHOW_NATIVE_SLIDER, false)
        val flags = if (showNativeUi) AudioManager.FLAG_SHOW_UI else 0
        if (targetStreamVol != currentVol) {
            audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, targetStreamVol, flags)
            onHaptic?.invoke()
        }
        return ScrubResult(
            title = "MEDIA VOLUME",
            value = "$targetPct%",
            currentValue = targetPct,
            stepIndex = (targetPct * volResolution / 100).coerceIn(0, volResolution),
            totalSteps = volResolution
        )
    }

    fun executeBrightnessScrub(
        context: Context,
        prefs: SharedPreferences,
        currentBrightness: Int,
        steps: Int,
        stepMultiplier: Float = 1f,
        onHaptic: (() -> Unit)? = null
    ): ScrubResult? {
        if (!Settings.System.canWrite(context)) {
            LightspeedTimeoutEngine.requestWriteSettingsPermission(context)
            return null
        }
        val brightResolution = prefs.getInt(LightspeedPreferences.KEY_BRIGHTNESS_SCRUB_RESOLUTION, 32).coerceIn(10, 254)
        val brightStep = prefs.getInt(LightspeedPreferences.KEY_BRIGHTNESS_SCRUB_STEP, (255f / brightResolution).roundToInt().coerceIn(1, 32))
        val curBright = if (currentBrightness < 0) {
            try {
                Settings.System.getInt(context.contentResolver, Settings.System.SCREEN_BRIGHTNESS)
            } catch (_: Exception) { 128 }
        } else currentBrightness
        val targetBrightness = (curBright + (steps * brightStep * stepMultiplier).toInt()).coerceIn(0, 255)
        try {
            Settings.System.putInt(context.contentResolver, Settings.System.SCREEN_BRIGHTNESS_MODE, Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL)
            val curSysBright = Settings.System.getInt(context.contentResolver, Settings.System.SCREEN_BRIGHTNESS)
            if (abs(targetBrightness - curSysBright) >= 1) {
                Settings.System.putInt(context.contentResolver, Settings.System.SCREEN_BRIGHTNESS, targetBrightness)
                onHaptic?.invoke()
            }
        } catch (e: Exception) {
            Log.e("CruiseScrubEngine", "System write failure", e)
        }
        return ScrubResult(
            title = "BRIGHTNESS",
            value = "${(targetBrightness * 100 / 255)}%",
            currentValue = targetBrightness,
            stepIndex = (targetBrightness * brightResolution / 255),
            totalSteps = brightResolution
        )
    }

    fun executeTimeoutScrub(
        context: Context,
        steps: Int,
        onHaptic: (() -> Unit)? = null
    ): ScrubResult {
        val curIdx = LightspeedTimeoutEngine.getCurrentTimeoutIndex(context)
        val targetIndex = (curIdx + steps).coerceIn(0, LightspeedTimeoutEngine.TIMEOUT_STEPS.lastIndex)
        val stepResult = LightspeedTimeoutEngine.setStepIndex(context, targetIndex)
        val label = stepResult.second
        if (targetIndex != curIdx) {
            onHaptic?.invoke()
        }
        return ScrubResult(
            title = "SHIP GOES DARK IN",
            value = label,
            currentValue = targetIndex,
            stepIndex = targetIndex,
            totalSteps = LightspeedTimeoutEngine.TIMEOUT_STEPS.size
        )
    }
}

internal fun LightspeedCruiseOverlay.dispatchScrubHud(title: String, value: String, stepIndex: Int, totalSteps: Int) {
    val prefs = prefs()
    val isFlankUnified = prefs.getBoolean("pref_sidebar_right_link_flank_actions", true)
    val dynamicZone = if (isFlankUnified) "UNIFIED" else (if (currentActiveZone == TouchZone.TOP_EDGE) "TOP" else "BOTTOM")
    val fallbackKey = activeHoldScrubActionKey ?: "pref_macro_action_${dynamicZone}_SCRUBBING"
    CruiseScrubEngine.dispatchScrubHud(
        context = context,
        prefs = prefs,
        scrubType = activeHoldScrubAction ?: "",
        actionKey = fallbackKey,
        title = title,
        value = value,
        stepIndex = stepIndex,
        totalSteps = totalSteps
    )
}

internal fun LightspeedCruiseOverlay.executeLinearScrubTrack(zone: TouchZone, pixelDelta: Float) {
    val zoneName = if (zone == TouchZone.TOP_EDGE) "TOP" else "BOTTOM"
    val prefs = prefs()
    val isScrubLinked = LightspeedPreferences.isScrubRegionsLinked(context, isOpenedFromLeftFlank)
    val dynamicZone = if (isScrubLinked || prefs.getBoolean("pref_sidebar_link_gestures", false)) "TOP" else zoneName
    val assignedScrub = activeHoldScrubAction ?: (prefs.getString("pref_macro_action_${dynamicZone}_SCRUBBING", "none") ?: "none")

    if (assignedScrub == "none") return

    aggregateScrubAccumulator += pixelDelta
    val sensitivityThreshold = 28f

    if (abs(aggregateScrubAccumulator) >= sensitivityThreshold) {
        val steps = (aggregateScrubAccumulator / sensitivityThreshold).toInt()
        aggregateScrubAccumulator %= sensitivityThreshold
        if (steps != 0) {
            triggerHardwareHaptic(18, 110)
        }

        when (assignedScrub) {
            "scrub:volume", "system:volume" -> {
                val res = CruiseScrubEngine.executeVolumeScrub(context, prefs, audioManager, activeScrubVolumePct, steps)
                activeScrubVolumePct = res.currentValue
                scrubHudTitle = res.title
                scrubHudValue = res.value
                dispatchScrubHud(res.title, res.value, res.stepIndex, res.totalSteps)
                invalidate()
            }
            "scrub:brightness", "system:brightness" -> {
                val res = CruiseScrubEngine.executeBrightnessScrub(context, prefs, activeScrubBrightness, steps)
                if (res != null) {
                    activeScrubBrightness = res.currentValue
                    scrubHudTitle = res.title
                    scrubHudValue = res.value
                    dispatchScrubHud(res.title, res.value, res.stepIndex, res.totalSteps)
                    invalidate()
                }
            }
            "system:screen_timeout" -> {
                val res = CruiseScrubEngine.executeTimeoutScrub(context, steps) {
                    triggerHardwareHaptic(22, 140)
                }
                if (scrubHudValue != res.value) {
                    scrubHudTitle = res.title
                    scrubHudValue = res.value
                    dispatchScrubHud(res.title, res.value, res.stepIndex, res.totalSteps)
                    invalidate()
                }
            }
        }
    }
}
