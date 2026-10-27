package com.sbf.lightspeed

import android.media.AudioManager
import android.provider.Settings
import com.sbf.lightspeed.system.LightspeedPreferences
import com.sbf.lightspeed.system.LightspeedTimeoutEngine
import com.sbf.lightspeed.system.SonicDeckManager
import com.sbf.lightspeed.system.logSwallowed
import kotlin.math.abs

internal fun LightspeedCruiseOverlay.processMacroGestureMove(rawX: Float, rawY: Float, density: Float): Boolean {
    if (isHoldFired && currentDetectedGesture != MacroGesture.SCRUBBING) {
        return true
    }
    val previousGesture = currentDetectedGesture
    val deltaX = rawX - gestureStartX
    val deltaY = rawY - gestureStartY

    if (rawX < lowestXReached) lowestXReached = rawX
    if (rawY > highestYReached) highestYReached = rawY
    if (rawY < lowestYReached) lowestYReached = rawY

    val screenW = resources.displayMetrics.widthPixels.toFloat()
    val isLandscape = resources.configuration.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE
    val longSwipeThresholdDp = LightspeedPreferences.getDeflectorLongSwipeThresholdDp(context, isLandscape)
    val thresholdX_Scrub = (longSwipeThresholdDp.toFloat() * density).coerceAtMost(screenW * 0.45f)

    if (currentDetectedGesture == MacroGesture.NONE) {
        if (abs(deltaX) > (22f * density) && abs(deltaX) > abs(deltaY)) {
            currentDetectedGesture = MacroGesture.SWIPE_LEFT
            initialLeftSweepDistance = abs(deltaX)
        } else if (deltaY < (-25f * density) && abs(deltaY) > abs(deltaX)) {
            currentDetectedGesture = MacroGesture.SWIPE_UP
        } else if (deltaY > (25f * density) && abs(deltaY) > abs(deltaX)) {
            currentDetectedGesture = MacroGesture.SWIPE_DOWN
        }
    }

    when (currentDetectedGesture) {
        MacroGesture.SWIPE_LEFT -> {
            val zoneName = if (currentActiveZone == TouchZone.TOP_EDGE) "TOP" else "BOTTOM"
            val isScrubLinked = LightspeedPreferences.isScrubRegionsLinked(context, isOpenedFromLeftFlank)
            val dynamicZone = if (isScrubLinked || prefs().getBoolean("pref_sidebar_link_gestures", false)) "TOP" else zoneName
            val assignedScrub = prefs().getString("pref_macro_action_${dynamicZone}_SCRUBBING", "none")

            if (assignedScrub != "none" && assignedScrub != null && abs(deltaX) > thresholdX_Scrub) {
                currentDetectedGesture = MacroGesture.SCRUBBING
                scrubStartX = rawX
                activeHoldScrubAction = assignedScrub
                activeHoldScrubActionKey = "pref_macro_action_${dynamicZone}_SCRUBBING"
                uiHandler.removeCallbacks(holdTimerRunnable)
                aggregateScrubAccumulator = 0f
                if (!isScrubEntranceHapticFired) {
                    triggerHardwareHaptic(65, 255)
                    isScrubEntranceHapticFired = true
                }
                when (assignedScrub) {
                    "system:volume", "scrub:volume" -> {
                        val currentVol = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
                        val maxVol = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
                        val volResolution = prefs().getInt(LightspeedPreferences.KEY_VOLUME_SCRUB_RESOLUTION, 100).coerceIn(5, 100)
                        val pct = kotlin.math.round(currentVol * 100f / maxVol.coerceAtLeast(1)).toInt().coerceIn(0, 100)
                        activeScrubVolumePct = pct
                        scrubHudTitle = "MEDIA VOLUME"
                        scrubHudValue = "$pct%"
                        dispatchScrubHud(scrubHudTitle, scrubHudValue, (pct * volResolution / 100).coerceIn(0, volResolution), volResolution)
                    }
                    "system:brightness", "scrub:brightness" -> {
                        val currentBrightness = com.sbf.lightspeed.system.display.LightspeedDisplayWellnessResetEngine.getSystemBrightness(context)
                        activeScrubBrightness = currentBrightness
                        val brightResolution = prefs().getInt(LightspeedPreferences.KEY_BRIGHTNESS_SCRUB_RESOLUTION, 32).coerceIn(10, 254)
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
                    "system:scroll_scrub", "scrub:scroll" -> {
                        scrubHudTitle = "SCROLL SCRUB"
                        scrubHudValue = "ACTIVE"
                        dispatchScrubHud(scrubHudTitle, scrubHudValue, 1, 1)
                    }
                }
                invalidate()
            } else if (deltaY < (-25f * density)) {
                currentDetectedGesture = MacroGesture.SWIPE_LEFT_UP
            } else if (deltaY > (25f * density)) {
                currentDetectedGesture = MacroGesture.SWIPE_LEFT_DOWN
            } else {
                val currentReturnRightDistance = rawX - lowestXReached
                if (currentReturnRightDistance > (18f * density)) {
                    currentDetectedGesture = MacroGesture.SWIPE_LEFT_BACK
                }
            }
        }
        MacroGesture.SWIPE_UP -> {
            if ((rawY - lowestYReached) > (22f * density)) {
                currentDetectedGesture = MacroGesture.SWIPE_UP_DOWN
            } else if (deltaX < (-25f * density)) {
                currentDetectedGesture = MacroGesture.SWIPE_UP_LEFT
            }
        }
        MacroGesture.SWIPE_DOWN -> {
            if ((highestYReached - rawY) > (22f * density)) {
                currentDetectedGesture = MacroGesture.SWIPE_DOWN_UP
            } else if (deltaX < (-25f * density)) {
                currentDetectedGesture = MacroGesture.SWIPE_DOWN_LEFT
            }
        }
        MacroGesture.SCRUBBING -> {
            uiHandler.removeCallbacks(holdTimerRunnable)
            if (!isScrubEntranceHapticFired) {
                triggerHardwareHaptic(65, 255)
                isScrubEntranceHapticFired = true
            }

            val isVolume = activeHoldScrubAction == "scrub:volume" || activeHoldScrubAction == "system:volume"
            val isBrightness = activeHoldScrubAction == "scrub:brightness" || activeHoldScrubAction == "system:brightness"
            val isAudioDockEnabled = prefs().getBoolean(LightspeedPreferences.KEY_SONIC_DECK_ENABLED, true) &&
                prefs().getBoolean(LightspeedPreferences.KEY_SONIC_DECK_RIGHT_ENABLED, true)
            val isVistaDockEnabled = prefs().getBoolean(LightspeedPreferences.KEY_VISTA_DECK_ENABLED, true) &&
                prefs().getBoolean(LightspeedPreferences.KEY_VISTA_DECK_RIGHT_ENABLED, true)

            val horizontalPull = kotlin.math.abs(rawX - scrubStartX)
            if (horizontalPull > 80f * density && ((isVolume && isAudioDockEnabled) || (isBrightness && isVistaDockEnabled))) {
                currentDetectedGesture = MacroGesture.NONE
                macroTrackingActive = false
                isCruising = false
                dismissOverlay()
                LightspeedStatusBarOverlay.dismissActionHud(0L)
                if (isVolume) {
                    service?.let { SonicDeckManager.show(it) }
                } else {
                    service?.let { com.sbf.lightspeed.system.VistaDeckManager.show(it) }
                }
                return true
            }

            val dy = rawY - lastTouchRawY
            val pixelDelta = -dy
            executeLinearScrubTrack(currentActiveZone, pixelDelta)
        }
        else -> {}
    }

    if (currentDetectedGesture != MacroGesture.SCRUBBING && !currentDetectedGesture.name.endsWith("_HOLD")) {
        if (currentDetectedGesture != previousGesture) {
            if (previousGesture == MacroGesture.NONE) {
                if (LightspeedPreferences.isDeflectorGlowOnGestureStep(context)) {
                    triggerGlow(500L)
                }
            }
            triggerHardwareHaptic(18, 100)
            resetHoldTimer()
        }
    }

    lastTouchRawX = rawX
    lastTouchRawY = rawY
    return false
}
