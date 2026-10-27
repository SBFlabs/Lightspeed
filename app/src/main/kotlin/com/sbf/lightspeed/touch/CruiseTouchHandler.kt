package com.sbf.lightspeed

import com.sbf.lightspeed.system.logSwallowed
import android.media.AudioManager
import android.provider.Settings
import android.view.MotionEvent
import android.view.ViewConfiguration
import com.sbf.lightspeed.system.ActionDispatcher
import com.sbf.lightspeed.system.LightspeedPreferences
import com.sbf.lightspeed.system.LightspeedTimeoutEngine
import com.sbf.lightspeed.system.SonicDeckManager
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
                !isLockscreenPillOnly && topTouchBounds.contains(x, y) -> TouchZone.TOP_EDGE
                !isLockscreenPillOnly && bottomTouchBounds.contains(x, y) -> TouchZone.BOTTOM_EDGE
                else -> TouchZone.NONE
            }

            if (isLockscreenPillOnly && currentActiveZone != TouchZone.CENTER_CRUISE) {
                isCurrentlyTouched = false; invalidate()
                return false
            }

            isOpenedFromLeftFlank = isDispatchingFromLeftFlankView
            if (currentActiveZone == TouchZone.CENTER_CRUISE) {
                startCruiseFromFlank(isLeft = isOpenedFromLeftFlank, startRawX = rawX, startRawY = rawY)
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
                if (processMacroGestureMove(rawX, rawY, density)) return true
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
                            val cx = width / 2f
                            val cy = height / 2f
                            val unitScale = com.sbf.lightspeed.system.CockpitGimbalConfigCache.getUnitScale(context)
                            val customDiameterScale = com.sbf.lightspeed.system.CockpitGimbalConfigCache.getCustomDiameterScale(context)
                            val dGimbal = (unitScale / 2.6f) * customDiameterScale
                            val rad0 = 310f * dGimbal
                            val rad1 = 190f * dGimbal
                            val currentTrackRadius = if (activeGearRing == 0) rad0 else rad1
                            val gearShape = com.sbf.lightspeed.system.CockpitGimbalConfigCache.getGearShape(context)
                            val targetAngleRad = Math.toRadians(targetFocusAngle.toDouble())
                            val rotRad = Math.toRadians(gearRingRotations[activeGearRing].toDouble())
                            val (targetedX, targetedY) = DeepSpaceGearGeometry.pointOnGearPerimeter(cx, cy, currentTrackRadius, targetAngleRad - rotRad, rotRad, gearShape)

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
                    persistActiveGearSetIndex()
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
