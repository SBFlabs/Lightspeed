package com.sbf.lightspeed

import android.media.AudioManager
import android.provider.Settings
import android.view.MotionEvent
import android.view.ViewConfiguration
import com.sbf.lightspeed.system.ActionDispatcher
import com.sbf.lightspeed.system.LightspeedPreferences
import com.sbf.lightspeed.system.LightspeedTimeoutEngine
import com.sbf.lightspeed.system.OmniscientAudioDockManager
import kotlin.math.abs
import kotlin.math.hypot

/**
 * Handles the main cruise-mode and macro-gesture touch event dispatch
 * for the center / edge zones of [LightspeedCruiseOverlay].
 *
 * Called by [handleTouchEvent] after the COCKPIT_HANGAR and STICKY_PIN
 * guards have been evaluated and both returned false.
 *
 * @return true if the event was consumed; false to call superCall()
 */
internal fun LightspeedCruiseOverlay.handleCruiseTouch(
    event: MotionEvent,
    superCall: () -> Boolean
): Boolean {
    val x = event.x; val y = event.y
    val rawX = event.rawX; val rawY = event.rawY

    when (event.actionMasked) {
        MotionEvent.ACTION_DOWN -> {
            touchDownRawX = rawX; touchDownRawY = rawY
            lastTouchRawX = rawX; lastTouchRawY = rawY
            touchDownTime = System.currentTimeMillis()
            gestureStartX = rawX; gestureStartY = rawY

            trackingStateLocked = false
            categoryScrubbingEngaged = false
            maxVerticalDisplacement = 0f
            isCurrentlyTouched = true; invalidate()
            isHoldFired = false
            initialLeftSweepDistance = 0f
            lowestXReached = rawX
            highestYReached = rawY
            lowestYReached = rawY
            aggregateScrubAccumulator = 0f
            isScrubEntranceHapticFired = false
            currentDetectedGesture = MacroGesture.NONE
            overScrollBoundaryAccumulator = 0f
            settingsCategoryAppended = false

            currentActiveZone = when {
                centerTouchBounds.contains(x, y) -> TouchZone.CENTER_CRUISE
                topTouchBounds.contains(x, y) -> TouchZone.TOP_EDGE
                bottomTouchBounds.contains(x, y) -> TouchZone.BOTTOM_EDGE
                else -> TouchZone.NONE
            }

            if (currentActiveZone == TouchZone.CENTER_CRUISE) {
                startCruiseFromFlank(isLeft = false, startRawX = rawX, startRawY = rawY)
            } else if (currentActiveZone != TouchZone.NONE) {
                macroTrackingActive = true
                val holdDuration = com.sbf.lightspeed.system.LightspeedPreferences.getGestureHoldDurationMs(context)
                uiHandler.postDelayed(holdTimerRunnable, holdDuration)
            }
            return true
        }
        MotionEvent.ACTION_MOVE -> {
            val density = resources.displayMetrics.density // Convert hardcoded pixels to device-agnostic DP
            if (isCruising) {
                val deltaX = if (isOpenedFromLeftFlank) (rawX - touchDownRawX) else (touchDownRawX - rawX)
                val deltaY = abs(rawY - touchDownRawY)
                if (deltaY > maxVerticalDisplacement) {
                    maxVerticalDisplacement = deltaY
                }

                // 1. Direct Lateral Swipe from rest -> Open Gears / Cockpit immediately
                // Zero category flicker because NEUTRAL never painted categories on screen!
                if (currentLayer == CruiseLayer.NEUTRAL &&
                    !categoryScrubbingEngaged && deltaX > (14f * density) && deltaX > (deltaY * 1.1f)) {
                    uiHandler.removeCallbacks(neutralToCategoryRunnable)
                    val cPrefs = prefs()
                    val setsList = getGearSetsOrder(isOpenedFromLeftFlank)
                    val launchBehavior = getFlankLaunchBehavior(isOpenedFromLeftFlank)
                    val lastActiveKey = if (isOpenedFromLeftFlank) "last_active_set_index_left" else "last_active_set_index_right"
                    if (launchBehavior == "last") {
                        val lastIndex = cPrefs.getInt(lastActiveKey, cPrefs.getInt("last_active_set_index", 0))
                        activeGearSetIndex = if (lastIndex in setsList.indices) lastIndex else 0
                    } else {
                        activeGearSetIndex = 0
                    }
                    currentLayer = CruiseLayer.FAVORITES_GEARS
                    triggerHardwareHaptic(30, 180)
                }

                // 2. Deliberate vertical movement -> Engage Category 3D Cylinder
                if (currentLayer == CruiseLayer.NEUTRAL && maxVerticalDisplacement > (14f * density)) {
                    uiHandler.removeCallbacks(neutralToCategoryRunnable)
                    categoryScrubbingEngaged = true
                    currentLayer = CruiseLayer.CATEGORY
                    entranceStartTime = System.currentTimeMillis()
                }

                if (currentLayer == CruiseLayer.FAVORITES_GEARS) {
                    processGyroscopeTouchPhysics(rawX, rawY)
                } else if (currentLayer == CruiseLayer.CATEGORY || currentLayer == CruiseLayer.GRID || currentLayer == CruiseLayer.STICKY_PIN) {
                    evaluateSpatialMetrics(rawX, rawY, rawX, rawY)
                }
                lastTouchRawX = rawX
                lastTouchRawY = rawY
                invalidate()
            } else if (macroTrackingActive) {
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
                val thresholdY_Compound = 60f

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
                                    val currentBrightness = try {
                                        Settings.System.getInt(context.contentResolver, Settings.System.SCREEN_BRIGHTNESS)
                                    } catch (_: Exception) { 128 }
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
                        uiHandler.removeCallbacks(holdTimerRunnable) // PORTAL LINE LOCK: Suppress any hold actions immediately
                        if (!isScrubEntranceHapticFired) {
                            triggerHardwareHaptic(65, 255) // Chunky high-inertia hardware pop (50ms)
                            isScrubEntranceHapticFired = true
                        }
                        
                        val isVolume = activeHoldScrubAction == "scrub:volume" || activeHoldScrubAction == "system:volume"
                        val isBrightness = activeHoldScrubAction == "scrub:brightness" || activeHoldScrubAction == "system:brightness"
                        val isAudioDockEnabled = prefs().getBoolean(LightspeedPreferences.KEY_OMNISCIENT_AUDIO_DOCK_ENABLED, false)

                        val horizontalPull = kotlin.math.abs(rawX - scrubStartX)
                        if (horizontalPull > 80f * density && ((isVolume && isAudioDockEnabled) || isBrightness)) {
                            currentDetectedGesture = MacroGesture.NONE
                            macroTrackingActive = false // Portal Line Lock: Prevent re-triggering until finger lifts
                            isCruising = false
                            LightspeedStatusBarOverlay.dismissActionHud(0L)
                            if (isVolume) {
                                service?.let { OmniscientAudioDockManager.show(it) }
                            } else {
                                val actionStr = "com.sbf.lightspeed.OMNISCIENT_DISPLAY"
                                val intent = android.content.Intent(actionStr).apply {
                                    setPackage(context.packageName)
                                    addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK or android.content.Intent.FLAG_ACTIVITY_MULTIPLE_TASK or android.content.Intent.FLAG_ACTIVITY_NO_ANIMATION)
                                }
                                try { context.startActivity(intent) } catch (_: Exception) {}
                            }
                            return true
                        }
                        
                        val dx = rawX - lastTouchRawX
                        val dy = rawY - lastTouchRawY
                        // Axis Lock: Lightspeed side deflectors ALWAYS use Y-axis.
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
            }
            return true
        }
        MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
            isCurrentlyTouched = false
            isScrubEntranceHapticFired = false
            uiHandler.removeCallbacks(holdTimerRunnable)
            stopGearAutoRepeat()
            if (isCruising) {
                if (currentLayer == CruiseLayer.FAVORITES_GEARS) {
                    val packages = getAppsForActiveGear(activeGearSetIndex, activeGearRing)
                    if (packages.isNotEmpty() && activeGearRing in 0..1) {
                        val itemCount = packages.size
                        val currentRotation = gearRingRotations[activeGearRing]
                        
                        var targetedPackage: String? = null
                        var minAngleDiff = Float.MAX_VALUE
                        val isFlankAware = prefs().getString(com.sbf.lightspeed.system.LightspeedPreferences.KEY_COCKPIT_RETICLE_ORIENTATION, com.sbf.lightspeed.system.LightspeedPreferences.RETICLE_ORIENTATION_CLASSIC_180) == com.sbf.lightspeed.system.LightspeedPreferences.RETICLE_ORIENTATION_MIRRORED_FLANK
                        val targetFocusAngle = if (isFlankAware && isOpenedFromLeftFlank) 0f else 180f
                        
                        for (i in packages.indices) {
                            val itemAngle = (currentRotation + i * (360f / itemCount)) % 360f
                            val normalizedAngle = if (itemAngle < 0) itemAngle + 360f else itemAngle
                            val rawDiff = abs(normalizedAngle - targetFocusAngle)
                            val diff = if (rawDiff > 180f) 360f - rawDiff else rawDiff
                            if (diff < minAngleDiff) {
                                minAngleDiff = diff
                                targetedPackage = packages[i]
                            }
                        }
                        
                        if (targetedPackage != null) {
                            val density = resources.displayMetrics.density
                            val cx = width / 2f
                            val cy = height / 2f
                            val rad0 = 310f * (density / 2.6f).coerceAtLeast(0.9f)
                            val rad1 = 190f * (density / 2.6f).coerceAtLeast(0.9f)
                            val currentTrackRadius = if (activeGearRing == 0) rad0 else rad1
                            val targetedX = if (isFlankAware && isOpenedFromLeftFlank) cx + currentTrackRadius else cx - currentTrackRadius
                            val targetedY = cy

                            triggerHyperdriveWarpLaunch(targetedX, targetedY) {
                                ActionDispatcher.execute(service ?: context, targetedPackage)
                            }
                            return true
                        }
                    } else if (activeGearRing == 2) {
                        triggerHardwareHaptic(50, 220)
                        persistActiveGearSetIndex()
                        
                        // Align hangar bay profile rail to center the active gear set
                        val setsList = getGearSetsOrder(isOpenedFromLeftFlank)
                        val d = resources.displayMetrics.density
                        val deckW = width * 0.88f
                        val bayCount = setsList.size + 1
                        val slotW = (88f * d).coerceAtLeast(deckW / bayCount.coerceAtMost(4))
                        val totalBayRailW = bayCount * slotW
                        val maxScroll = (totalBayRailW - deckW).coerceAtLeast(0f)
                        if (totalBayRailW > deckW) {
                            val targetOffset = (deckW / 2f) - ((activeGearSetIndex + 0.5f) * slotW)
                            hangarBayScrollOffset = targetOffset.coerceIn(-maxScroll, 0f)
                        } else {
                            hangarBayScrollOffset = 0f
                        }
                        
                        currentLayer = CruiseLayer.COCKPIT_HANGAR
                        invalidate()
                        return true
                    }
                    currentLayer = CruiseLayer.HIDDEN
                    isCruising = false
                    service?.updateWindowLayout(false) // CRITICAL DIRECTIVE: Disengage overlay window touch-trap immediately
                    updateMetricsDimensions()
                    invalidate()
                    return true
                }

                uiHandler.removeCallbacks(neutralToCategoryRunnable)
                if (currentLayer == CruiseLayer.STICKY_PIN) {
                    isStickyPinned = true; currentLayer = CruiseLayer.GRID
                    updateMetricsDimensions(); invalidate()
                } else if (currentLayer == CruiseLayer.CATEGORY && activeCatIndex in cachedCategories.indices && cachedCategories[activeCatIndex].id == "launcher_settings_virtual_id") {
                    launchLauncherSettings()
                } else if (currentLayer == CruiseLayer.NEUTRAL) {
                    val duration = System.currentTimeMillis() - touchDownTime
                    val dist = hypot((rawX - touchDownRawX).toDouble(), (rawY - touchDownRawY).toDouble()).toFloat()
                    dismissOverlay()
                    if (duration < 350 && dist < (20f * resources.displayMetrics.density)) {
                        triggerHardwareHaptic(25, 120)
                        service?.triggerDeflectorsGlow() ?: triggerGlow()
                    }
                } else {
                    val launchTarget = activeItem
                    if (launchTarget != null) {
                        val placed = placedAppsList.find { it.app == launchTarget }
                        val focalX = placed?.bounds?.centerX() ?: (width / 2f)
                        val focalY = placed?.bounds?.centerY() ?: (height / 2f)
                        triggerHyperdriveWarpLaunch(focalX, focalY) {
                            executeLaunch(launchTarget)
                        }
                    } else {
                        dismissOverlay()
                    }
                }
                isCruising = false
                categoryScrubbingEngaged = false
                maxVerticalDisplacement = 0f
            } else if (macroTrackingActive) {
                macroTrackingActive = false
                if (!isHoldFired && currentDetectedGesture != MacroGesture.NONE && currentDetectedGesture != MacroGesture.SCRUBBING) {
                    executeMacroAction(currentActiveZone, currentDetectedGesture)
                } else if (!isHoldFired && currentDetectedGesture == MacroGesture.NONE) {
                    val duration = System.currentTimeMillis() - touchDownTime
                    val dist = hypot((rawX - touchDownRawX).toDouble(), (rawY - touchDownRawY).toDouble()).toFloat()
                    if (duration < 350 && dist < (20f * resources.displayMetrics.density)) {
                        triggerHardwareHaptic(25, 120)
                        service?.triggerDeflectorsGlow() ?: triggerGlow()
                    }
                }
            }
            isHoldFired = false
            if (currentDetectedGesture == MacroGesture.SCRUBBING) {
                LightspeedStatusBarOverlay.dismissActionHud(1200L)
            }
            activeHoldScrubAction = null
            activeHoldScrubActionKey = null
            activeScrubVolumePct = -1
            activeScrubBrightness = -1
            scrubHudTitle = ""
            scrubHudValue = ""
            invalidate()
            currentActiveZone = TouchZone.NONE
            return true
        }
    }
    return superCall()
}
