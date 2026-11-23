package com.sbf.lightspeed

import com.sbf.lightspeed.system.logSwallowed
import android.media.AudioManager
import android.provider.Settings
import android.os.Build
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
            pendingHoldReleaseScrollAction = null
            categoryTipRotatedForSession = false

            touchDownRawX = rawX; touchDownRawY = rawY
            lastTouchRawX = rawX; lastTouchRawY = rawY
            touchDownTime = System.currentTimeMillis()
            gestureStartX = rawX; gestureStartY = rawY

            trackingStateLocked = false
            categoryScrubbingEngaged = false
            isCategoryVectorLocked = false
            gridEntryRawX = rawX
            gridEntryRawY = rawY
            gridDeadZoneLatched = false
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
            settingsRevealHapticFired = false
            categoryFilteredAnchorY = rawY
            overScrollAnchorRawY = -1f

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
                startCruiseFromFlank(
                    isLeft = isOpenedFromLeftFlank,
                    startRawX = rawX,
                    startRawY = rawY,
                    pointerId = event.getPointerId(0)
                )
            } else if (currentActiveZone != TouchZone.NONE) {
                com.sbf.lightspeed.settings.CentralCommandBackdropBlur.prewarmForGesture(context)
                macroTrackingActive = true
                val holdDuration = com.sbf.lightspeed.system.LightspeedPreferences.getGestureHoldDurationMs(context)
                uiHandler.postDelayed(holdTimerRunnable, holdDuration)
            }
            return true
        }
        MotionEvent.ACTION_MOVE -> {
            val density = resources.displayMetrics.density // Convert hardcoded pixels to device-agnostic DP
            if (isCruising) {
                val pIdx = if (cruiseMultiTouch.activeCruisePointerId != MotionEvent.INVALID_POINTER_ID) {
                    event.findPointerIndex(cruiseMultiTouch.activeCruisePointerId).let { if (it >= 0) it else 0 }
                } else 0
                val p1RawX = if (pIdx == 0) rawX else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) event.getRawX(pIdx) else rawX
                val p1RawY = if (pIdx == 0) rawY else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) event.getRawY(pIdx) else rawY
                val p1MoveDist = hypot((p1RawX - lastTouchRawX).toDouble(), (p1RawY - lastTouchRawY).toDouble()).toFloat()
                if (p1MoveDist < 0.5f &&
                    (currentLayer == CruiseLayer.CATEGORY || currentLayer == CruiseLayer.GRID || currentLayer == CruiseLayer.STICKY_PIN)
                ) {
                    return true
                }
                if (!cruiseMultiTouch.hasSecondaryActive() &&
                    cruiseMultiTouch.secondaryWidgetTappedThisSession &&
                    p1MoveDist >= (6f * density)
                ) {
                    cruiseMultiTouch.secondaryWidgetTappedThisSession = false
                }
                applyPrimaryCruisePointerMove(p1RawX, p1RawY)
            } else if (macroTrackingActive) {
                if (processMacroGestureMove(rawX, rawY, density)) return true
            }
            return true
        }
        MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
            val isCancel = event.actionMasked == MotionEvent.ACTION_CANCEL
            return finishCruiseTouchSequence(rawX, rawY, isCancel = isCancel)
        }
    }
    return superCall()
}

private fun LightspeedCruiseOverlay.finishCruiseTouchSequence(
    rawX: Float,
    rawY: Float,
    isCancel: Boolean
): Boolean {
    isCurrentlyTouched = false
    isScrubEntranceHapticFired = false
    uiHandler.removeCallbacks(holdTimerRunnable)
    stopGearAutoRepeat()
    val deferredScrollAction = if (isCancel) null else pendingHoldReleaseScrollAction
    pendingHoldReleaseScrollAction = null

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
                    val geom = resolveLiveGearGeometry()
                    val currentTrackRadius = if (activeGearRing == 0) geom.rad0 else geom.rad1
                    val gearShape = com.sbf.lightspeed.system.CockpitGimbalConfigCache.getGearShape(context)
                    val targetAngleRad = Math.toRadians(targetFocusAngle.toDouble())
                    val rotRad = Math.toRadians(gearRingRotations[activeGearRing].toDouble())
                    val (targetedX, targetedY) = DeepSpaceGearGeometry.pointOnGearPerimeter(geom.cx, geom.cy, currentTrackRadius, targetAngleRad - rotRad, rotRad, gearShape)

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
        if (cruiseMultiTouch.secondaryActionCommitted) {
            isCruising = false
            categoryScrubbingEngaged = false
            maxVerticalDisplacement = 0f
            cruiseMultiTouch.reset()
        } else if (cruiseMultiTouch.secondaryWidgetTappedThisSession) {
            dismissOverlay()
        } else if (currentLayer == CruiseLayer.STICKY_PIN) {
            isStickyPinned = true; currentLayer = CruiseLayer.GRID
            updateMetricsDimensions(); invalidate(); postInvalidateOnAnimation()
        } else if (currentLayer == CruiseLayer.CATEGORY && activeCatIndex in cachedCategories.indices && cachedCategories[activeCatIndex].id == "launcher_settings_virtual_id") {
            launchLauncherSettings()
        } else if (currentLayer == CruiseLayer.NEUTRAL) {
            val duration = System.currentTimeMillis() - touchDownTime
            val dist = hypot((rawX - touchDownRawX).toDouble(), (rawY - touchDownRawY).toDouble()).toFloat()
            dismissOverlay()
            if (duration < 350 && dist < (20f * resources.displayMetrics.density)) {
                com.sbf.lightspeed.system.LightspeedHapticEngine.deflectorTap(context)
                service?.triggerDeflectorsGlow() ?: triggerGlow()
            }
        } else {
            val launchTarget = activeItem
            if (launchTarget != null) {
                val placed = placedAppsList.find { it.app == launchTarget }
                val (focalX, focalY) = resolvePlacedItemIconCenter(placed)
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
            val name = currentDetectedGesture.name
            if (name.endsWith("_BACK") || name == "SWIPE_UP_DOWN" || name == "SWIPE_DOWN_UP") {
                com.sbf.lightspeed.system.LightspeedHapticEngine.deflectorRebound(context)
            } else if (name.contains("_UP") || name.contains("_DOWN") || name.contains("_LEFT") || name.contains("_RIGHT")) {
                com.sbf.lightspeed.system.LightspeedHapticEngine.deflectorTwoStep(context)
            } else {
                com.sbf.lightspeed.system.LightspeedHapticEngine.deflectorSingleSwipe(context)
            }
            executeMacroAction(currentActiveZone, currentDetectedGesture)
        } else if (!isHoldFired && currentDetectedGesture == MacroGesture.NONE) {
            val duration = System.currentTimeMillis() - touchDownTime
            val dist = hypot((rawX - touchDownRawX).toDouble(), (rawY - touchDownRawY).toDouble()).toFloat()
            if (duration < 350 && dist < (20f * resources.displayMetrics.density)) {
                com.sbf.lightspeed.system.LightspeedHapticEngine.deflectorTap(context)
                service?.triggerDeflectorsGlow() ?: triggerGlow()
            }
        }
    }
    if (isHoldFired && deferredScrollAction != null && !isCancel) {
        uiHandler.postDelayed({
            ActionDispatcher.execute(service ?: context, deferredScrollAction)
        }, 35L)
    }
    isHoldFired = false
    if (currentDetectedGesture == MacroGesture.SCRUBBING) {
        LightspeedStatusBarOverlay.dismissActionHud(0L)
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
