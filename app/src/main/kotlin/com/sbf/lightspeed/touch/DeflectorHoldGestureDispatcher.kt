package com.sbf.lightspeed

import android.content.Intent
import android.media.AudioManager
import android.provider.Settings
import com.sbf.lightspeed.settings.LightspeedActionRegistry
import com.sbf.lightspeed.system.LightspeedPreferences
import com.sbf.lightspeed.system.LightspeedTimeoutEngine
import com.sbf.lightspeed.system.bot.LightspeedBotEngine
import kotlin.math.round

internal fun LightspeedCruiseOverlay.showInfinityPrompt() {
    val intent = Intent(context, CockpitDialogActivity::class.java).apply {
        action = CockpitDialogActivity.ACTION_SHOW_INFINITY
        putExtra(CockpitDialogActivity.EXTRA_SET_INDEX, activeGearSetIndex.coerceAtLeast(0))
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS)
    }
    dismissOverlay()
    context.startActivity(intent)
}

/**
 * Resolves and dispatches hold gesture logic when hold timer expires.
 */
internal fun LightspeedCruiseOverlay.resolveAndDispatchHoldGesture(zone: TouchZone) {
    if (macroTrackingActive) {
        val hostGesture = currentDetectedGesture
        val holdEquivalent = when (hostGesture) {
            MacroGesture.NONE -> MacroGesture.NONE
            MacroGesture.SWIPE_UP -> MacroGesture.SWIPE_UP_HOLD
            MacroGesture.SWIPE_DOWN -> MacroGesture.SWIPE_DOWN_HOLD
            MacroGesture.SWIPE_LEFT -> MacroGesture.SWIPE_LEFT_HOLD
            MacroGesture.SWIPE_UP_DOWN -> MacroGesture.SWIPE_UP_DOWN_HOLD
            MacroGesture.SWIPE_DOWN_UP -> MacroGesture.SWIPE_DOWN_UP_HOLD
            MacroGesture.SWIPE_UP_LEFT -> MacroGesture.SWIPE_UP_LEFT_HOLD
            MacroGesture.SWIPE_DOWN_LEFT -> MacroGesture.SWIPE_DOWN_LEFT_HOLD
            MacroGesture.SWIPE_LEFT_BACK -> MacroGesture.SWIPE_LEFT_BACK_HOLD
            MacroGesture.SWIPE_LEFT_UP -> MacroGesture.SWIPE_LEFT_UP_HOLD
            MacroGesture.SWIPE_LEFT_DOWN -> MacroGesture.SWIPE_LEFT_DOWN_HOLD
            else -> hostGesture
        }

        val gestureKey = if (hostGesture == MacroGesture.NONE) "TAP_HOLD" else holdEquivalent.name
        val zoneName = if (zone == TouchZone.TOP_EDGE) "TOP" else "BOTTOM"
        val prefs = prefs()
        val isFlankUnified = prefs.getBoolean(LightspeedPreferences.KEY_SIDEBAR_RIGHT_LINK_FLANK_ACTIONS, true)
        val gestMode = prefs.getString(LightspeedPreferences.KEY_SYMMETRY_GESTURE_MODE, "independent") ?: "independent"
        val isMirroringLeft = gestMode == "left"

        val actionKey = if (isMirroringLeft) {
            val isLeftUnified = prefs.getBoolean(LightspeedPreferences.KEY_SIDEBAR_LEFT_LINK_FLANK_ACTIONS, true)
            val leftGesture = when (gestureKey) {
                "SWIPE_LEFT_HOLD" -> "SWIPE_RIGHT_HOLD"
                "SWIPE_LEFT_UP_HOLD" -> "SWIPE_RIGHT_UP_HOLD"
                "SWIPE_LEFT_DOWN_HOLD" -> "SWIPE_RIGHT_DOWN_HOLD"
                "SWIPE_LEFT_BACK_HOLD" -> "SWIPE_RIGHT_BACK_HOLD"
                "SWIPE_UP_LEFT_HOLD" -> "SWIPE_UP_RIGHT_HOLD"
                "SWIPE_DOWN_LEFT_HOLD" -> "SWIPE_DOWN_RIGHT_HOLD"
                else -> gestureKey
            }
            val isLeftGestureUnified = LightspeedPreferences.isGestureUnified(context, isLeft = true, leftGesture)
            val leftZone = if (isLeftUnified && isLeftGestureUnified) "LEFT_UNIFIED" else "LEFT_$zoneName"
            "pref_macro_action_${leftZone}_$leftGesture"
        } else {
            val isGestureUnified = LightspeedPreferences.isGestureUnified(context, isLeft = false, gestureKey)
            val dynamicZone = if (isFlankUnified && isGestureUnified) "UNIFIED" else zoneName
            "pref_macro_action_${dynamicZone}_$gestureKey"
        }
        val explicit = prefs.getString(actionKey, null)
        val actionValue = if (!explicit.isNullOrEmpty() && explicit != "none") {
            explicit
        } else {
            LightspeedActionRegistry.getDefaultActionForGestureKey(actionKey)
        }

        // Gesture Bot Flow: Swipe & Hold on a zone bound to Bot Macro starts Bot Recording Mode
        if (actionValue.startsWith("bot:macro_")) {
            isHoldFired = true
            val botId = actionValue.removePrefix("bot:macro_").toIntOrNull() ?: 1
            triggerHardwareHaptic(40, 200)
            dismissOverlay()
            LightspeedBotEngine.startRecording(context, botId)
            macroTrackingActive = false
            return
        }

        if (actionValue == "system:volume" || actionValue == "system:brightness" || actionValue == "system:screen_timeout" || actionValue == "scrub:volume" || actionValue == "scrub:brightness") {
            isHoldFired = true
            currentDetectedGesture = MacroGesture.SCRUBBING
            activeHoldScrubAction = actionValue
            activeHoldScrubActionKey = actionKey
            aggregateScrubAccumulator = 0f
            scrubStartX = lastTouchRawX
            isScrubEntranceHapticFired = true
            triggerHardwareHaptic(35, 180)
            when (actionValue) {
                "system:volume", "scrub:volume" -> {
                    val currentVol = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
                    val maxVol = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
                    val volResolution = prefs.getInt(LightspeedPreferences.KEY_VOLUME_SCRUB_RESOLUTION, 100).coerceIn(5, 100)
                    val pct = round(currentVol * 100f / maxVol.coerceAtLeast(1)).toInt().coerceIn(0, 100)
                    activeScrubVolumePct = pct
                    scrubHudTitle = "MEDIA VOLUME"
                    scrubHudValue = "$pct%"
                    dispatchScrubHud(scrubHudTitle, scrubHudValue, (pct * volResolution / 100).coerceIn(0, volResolution), volResolution)
                }
                "system:brightness", "scrub:brightness" -> {
                    val currentBrightness = com.sbf.lightspeed.system.display.LightspeedDisplayWellnessResetEngine.getSystemBrightness(context)
                    val brightResolution = prefs.getInt(LightspeedPreferences.KEY_BRIGHTNESS_SCRUB_RESOLUTION, 32).coerceIn(10, 254)
                    scrubHudTitle = "BRIGHTNESS"
                    scrubHudValue = "${(currentBrightness * 100 / 255)}%"
                    dispatchScrubHud(scrubHudTitle, scrubHudValue, (currentBrightness * brightResolution / 255), brightResolution)
                }
                "system:screen_timeout" -> {
                    scrubHudTitle = "SHIP GOES DARK IN"
                    val idx = LightspeedTimeoutEngine.getCurrentTimeoutIndex(context)
                    scrubHudValue = LightspeedTimeoutEngine.TIMEOUT_STEPS[idx].second
                    dispatchScrubHud(scrubHudTitle, scrubHudValue, idx, LightspeedTimeoutEngine.TIMEOUT_STEPS.size)
                }
            }
            invalidate()
        } else if (actionValue != "none") {
            isHoldFired = true
            if (holdEquivalent != hostGesture) {
                currentDetectedGesture = holdEquivalent
            }
            executeMacroAction(zone, holdEquivalent)
            triggerHardwareHaptic(35, 160)
            macroTrackingActive = false
        }
    }
}
