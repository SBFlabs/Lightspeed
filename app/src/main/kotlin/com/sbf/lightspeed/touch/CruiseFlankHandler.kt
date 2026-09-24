package com.sbf.lightspeed

import android.view.MotionEvent
import com.sbf.lightspeed.system.ActionDispatcher
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.hypot

internal fun LightspeedCruiseOverlay.performStartCruiseFromFlank(isLeft: Boolean, startRawX: Float, startRawY: Float) {
    isOpenedFromLeftFlank = isLeft
    touchDownRawX = startRawX
    touchDownRawY = startRawY
    lastTouchRawX = startRawX
    lastTouchRawY = startRawY
    touchDownTime = System.currentTimeMillis()
    gestureStartX = startRawX
    gestureStartY = startRawY

    trackingStateLocked = false
    initialLeftSweepDistance = 0f
    lowestXReached = startRawX
    highestYReached = startRawY
    lowestYReached = startRawY
    aggregateScrubAccumulator = 0f
    isScrubEntranceHapticFired = false
    currentDetectedGesture = MacroGesture.NONE
    overScrollBoundaryAccumulator = 0f
    settingsCategoryAppended = false

    currentActiveZone = TouchZone.CENTER_CRUISE
    isCruising = true
    isCurrentlyTouched = true
    categoryScrubbingEngaged = false
    maxVerticalDisplacement = 0f
    categoryVisualOffset = 0f
    entranceStartTime = System.currentTimeMillis()
    lastLoadedCategoryId = null
    snapAnimator?.cancel()
    categoryAppsCache.clear(); categoryGridCache.clear()

    val cPrefs = prefs()
    val setsList = getGearSetsOrder(isLeft)
    val launchBehavior = getFlankLaunchBehavior(isLeft)
    val lastActiveKey = if (isLeft) "last_active_set_index_left" else "last_active_set_index_right"
    if (launchBehavior == "last") {
        val lastIndex = cPrefs.getInt(lastActiveKey, cPrefs.getInt("last_active_set_index", 0))
        activeGearSetIndex = if (lastIndex in setsList.indices) lastIndex else 0
    } else {
        activeGearSetIndex = 0
    }

    currentLayer = CruiseLayer.NEUTRAL
    updateMetricsDimensions()
    placedAppsList.clear(); cachedApps = emptyList()
    service?.updateWindowLayout(true)

    cachedCategories = dataBridge.getLiveCategories()
    if (cachedCategories.isNotEmpty()) {
        val hF = resources.displayMetrics.heightPixels.toFloat()
        val startYArea = hF * 0.25f
        val endYArea = hF * 0.75f
        val usableHeight = endYArea - startYArea
        val normY = ((startRawY - startYArea) / usableHeight).coerceIn(0f, 1f)
        initialCatIndex = floor(normY * cachedCategories.size).toInt().coerceIn(0, cachedCategories.size - 1)
        activeCatIndex = initialCatIndex
        val catLineH = usableHeight / cachedCategories.size
        categoryVisualOffset = (hF / 2f) - (startYArea + (activeCatIndex * catLineH) + (catLineH / 2f))
    }
    uiHandler.removeCallbacks(neutralToCategoryRunnable)
    uiHandler.postDelayed(neutralToCategoryRunnable, CruiseOverlayConstants.NEUTRAL_TO_CATEGORY_DELAY_MS)
    invalidate()
}

internal fun LightspeedCruiseOverlay.performFlankTouchEvent(isLeft: Boolean, event: MotionEvent): Boolean {
    val rawX = event.rawX
    val rawY = event.rawY
    when (event.actionMasked) {
        MotionEvent.ACTION_DOWN -> {
            startCruiseFromFlank(isLeft, rawX, rawY)
            return true
        }
        MotionEvent.ACTION_MOVE -> {
            val density = resources.displayMetrics.density
            if (isCruising) {
                val deltaX = if (isOpenedFromLeftFlank) (rawX - touchDownRawX) else (touchDownRawX - rawX)
                val deltaY = abs(rawY - touchDownRawY)
                if (deltaY > maxVerticalDisplacement) {
                    maxVerticalDisplacement = deltaY
                }

                // 1. Direct Lateral Inward Swipe -> Open Gears
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
            }
            return true
        }
        MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
            isCurrentlyTouched = false
            currentActiveZone = TouchZone.NONE
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
                    service?.updateWindowLayout(false)
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
            }
            invalidate()
            return true
        }
    }
    return false
}

internal fun LightspeedCruiseOverlay.processGyroscopeTouchPhysics(rawX: Float, rawY: Float) {
    val density = resources.displayMetrics.density
    val deltaX = if (isOpenedFromLeftFlank) (rawX - touchDownRawX) else (touchDownRawX - rawX)
    val deltaY = rawY - lastTouchRawY

    // Abort Track: Closing down gear layer if thumb slides back to bezel origin
    if (deltaX < (10f * density)) {
        currentLayer = CruiseLayer.HIDDEN
        isCruising = false
        service?.updateWindowLayout(false) // CRITICAL DIRECTIVE: Disengage overlay window touch-trap immediately
        updateMetricsDimensions()
        triggerHardwareHaptic(15, 80)
        invalidate()
        return
    }

    // Depth Axis Calibration (X-Axis Ring Selection)
    val outerThreshold = 50f * density
    val innerThreshold = 120f * density
    val cubeFlipThreshold = 220f * density

    val previousRing = activeGearRing
    activeGearRing = when {
        deltaX < outerThreshold -> 0
        deltaX < innerThreshold -> 1
        else -> 2
    }

    if (previousRing != activeGearRing && activeGearRing != 2) {
        triggerHardwareHaptic(20, 120) // Micro haptic tick on ring crossing
    }

    // Modular 3D Cube Rotation Gate (Over-Swipe Carousel with Auto-Repeat Hold)
    if (deltaX > cubeFlipThreshold) {
        if (!isCubeRotationFired) {
            isCubeRotationFired = true
            activeGearSetIndex = (activeGearSetIndex + 1) % totalGearSetsCount
            persistActiveGearSetIndex()
            triggerHardwareHaptic(55, 255) // Solid mechanical locking thud
            // Start continuous hold cycling with a 450ms initial safety gate
            uiHandler.removeCallbacks(gearAutoRepeatRunnable)
            uiHandler.postDelayed(gearAutoRepeatRunnable, 450L)
        }
    } else if (deltaX < innerThreshold + (25f * density)) {
        if (isCubeRotationFired) {
            isCubeRotationFired = false
            stopGearAutoRepeat()
        }
    } else if (deltaX <= cubeFlipThreshold) {
        stopGearAutoRepeat()
    }

    // Rotational Axis Crank Math: Scale angular velocity by true kinematic arc-length and flight physics profile
    val prefs = prefs()
    val physicsProfile = prefs.getString("pref_gear_physics_profile", "magnetic") ?: "magnetic"
    val physicsMultiplier = when (physicsProfile) {
        "fluid" -> 1.45f
        "heavy" -> 0.72f
        else -> 1.0f
    }

    val baseDegreesPerDp = 1.35f * physicsMultiplier
    val dyInDp = deltaY / density

    if (activeGearRing in 0..1) {
        val angularDeltaDeg = dyInDp * baseDegreesPerDp
        gearRingRotations[activeGearRing] += angularDeltaDeg

        // Physical Mechanical Cog Notch Haptics as icons cross the 180° focus reticle
        val packages = getAppsForActiveGear(activeGearSetIndex, activeGearRing)
        if (packages.isNotEmpty()) {
            val itemCount = packages.size
            val currentRot = gearRingRotations[activeGearRing]
            var closestIdx = 0
            var minDiff = Float.MAX_VALUE
            val isFlankAware = prefs().getString(com.sbf.lightspeed.system.LightspeedPreferences.KEY_COCKPIT_RETICLE_ORIENTATION, com.sbf.lightspeed.system.LightspeedPreferences.RETICLE_ORIENTATION_CLASSIC_180) == com.sbf.lightspeed.system.LightspeedPreferences.RETICLE_ORIENTATION_MIRRORED_FLANK
            val targetFocusAngle = if (isFlankAware && isOpenedFromLeftFlank) 0f else 180f
            for (i in packages.indices) {
                val itemAngle = (currentRot + i * (360f / itemCount)) % 360f
                val norm = if (itemAngle < 0) itemAngle + 360f else itemAngle
                val rawDiff = abs(norm - targetFocusAngle)
                val diff = if (rawDiff > 180f) 360f - rawDiff else rawDiff
                if (diff < minDiff) {
                    minDiff = diff
                    closestIdx = i
                }
            }
            if (closestIdx != lastTargetedIndex[activeGearRing]) {
                lastTargetedIndex[activeGearRing] = closestIdx
                triggerGearCogHaptic()
            }
        }
    }

    lastTouchRawX = rawX
    lastTouchRawY = rawY
    invalidate()
}
