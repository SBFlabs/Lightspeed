package com.sbf.lightspeed

import android.content.Intent
import android.view.MotionEvent

internal fun LightspeedCruiseOverlay.persistActiveGearSetIndex() {
    com.sbf.lightspeed.system.CockpitGearRepository.persistActiveGearSetIndex(context, activeGearSetIndex, isOpenedFromLeftFlank)
}

internal fun LightspeedCruiseOverlay.executeLaunch(target: LightspeedDataBridge.LaunchTarget) {
    val launchIntent = context.packageManager.getLaunchIntentForPackage(target.packageName)
    if (launchIntent != null) {
        launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        try { context.startActivity(launchIntent) } catch (e: Exception) {}
    }
}

internal fun LightspeedCruiseOverlay.resolveCleanAppLabel(itemToken: String): String {
    return com.sbf.lightspeed.system.LightspeedShortcutManager.resolveLabel(context, itemToken)
}

internal fun LightspeedCruiseOverlay.launchLauncherSettings() {
    dismissOverlay()
    try {
        val settingsIntent = Intent(context, MainActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS)
        }
        context.startActivity(settingsIntent)
    } catch (_: Exception) {
        val alternativeIntent = context.packageManager.getLaunchIntentForPackage(context.packageName)?.apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS)
        }
        alternativeIntent?.let { context.startActivity(it) }
    }
}

internal fun LightspeedCruiseOverlay.dismissOverlay() {
    longPressHangarBayRunnable?.let { removeCallbacks(it) }
    longPressCogRunnable?.let { removeCallbacks(it) }
    longPressHangarBayRunnable = null
    longPressCogRunnable = null
    hasLongPressFired = false

    snapAnimator?.cancel()
    glowAnimator?.cancel()
    glowFraction = 0f
    isCurrentlyTouched = false
    currentTouchY = -1f
    currentActiveZone = TouchZone.NONE
    updatePillShaderUniforms(active = false)
    macroTrackingActive = false
    isDraggingHangarBays = false
    isSpinningHangarRing = false
    isTouchingFocusedCog = false
    isStickyPinned = false; isCruising = false; currentLayer = CruiseLayer.HIDDEN
    activeItem = null; activeCatIndex = -1; viewportScrollOffset = 0f; categoryVisualOffset = 0f
    placedAppsList.clear(); cachedApps = emptyList(); cachedCategories = emptyList()
    val cPrefs = prefs()
    if (cPrefs.getString("cockpit_launch_behavior", "default") != "last") {
        activeGearSetIndex = 0
    }
    service?.updateWindowLayout(false)
    updateMetricsDimensions()
    invalidate()
}

fun LightspeedCruiseOverlay.getGearSetsOrder(isLeft: Boolean): MutableList<String> {
    return com.sbf.lightspeed.system.CockpitGearRepository.getGearSetsOrder(context, isLeft)
}

fun LightspeedCruiseOverlay.saveGearSetsOrder(isLeft: Boolean, list: List<String>) {
    com.sbf.lightspeed.system.CockpitGearRepository.saveGearSetsOrder(context, isLeft, list)
}

fun LightspeedCruiseOverlay.getFlankLaunchBehavior(isLeft: Boolean): String {
    return com.sbf.lightspeed.system.CockpitGearRepository.getFlankLaunchBehavior(context, isLeft)
}

fun LightspeedCruiseOverlay.setFlankLaunchBehavior(isLeft: Boolean, behavior: String) {
    com.sbf.lightspeed.system.CockpitGearRepository.setFlankLaunchBehavior(context, isLeft, behavior)
}

fun LightspeedCruiseOverlay.startCruiseFromFlank(isLeft: Boolean, startRawX: Float, startRawY: Float) {
    performStartCruiseFromFlank(isLeft, startRawX, startRawY)
}

fun LightspeedCruiseOverlay.handleFlankTouchEvent(isLeft: Boolean, event: MotionEvent): Boolean {
    return performFlankTouchEvent(isLeft, event)
}

fun LightspeedCruiseOverlay.openHangarDirectly(setIndex: Int = -1) {
    val setsList = getGearSetsOrder(isOpenedFromLeftFlank)
    if (setIndex in setsList.indices) {
        activeGearSetIndex = setIndex
    }
    currentLayer = CruiseLayer.COCKPIT_HANGAR
    isCruising = false
    isStickyPinned = false
    service?.updateWindowLayout(true)
    updateMetricsDimensions()
    invalidate()
}

fun LightspeedCruiseOverlay.openHangarFromFlank(isLeftFlank: Boolean) {
    isOpenedFromLeftFlank = isLeftFlank
    val prefs = prefs()
    val setsList = getGearSetsOrder(isLeftFlank)
    val launchBehavior = getFlankLaunchBehavior(isLeftFlank)
    val lastActiveKey = if (isLeftFlank) "last_active_set_index_left" else "last_active_set_index_right"

    val targetIndex = if (launchBehavior == "last") {
        prefs.getInt(lastActiveKey, 0)
    } else {
        0
    }

    if (targetIndex in setsList.indices) {
        activeGearSetIndex = targetIndex
    } else {
        activeGearSetIndex = 0
    }

    currentLayer = CruiseLayer.COCKPIT_HANGAR
    isCruising = false
    isStickyPinned = false
    service?.updateWindowLayout(true)
    updateMetricsDimensions()
    invalidate()
}

internal fun LightspeedCruiseOverlay.getAppsForActiveGear(setIndex: Int, ringIndex: Int): List<String> {
    return com.sbf.lightspeed.system.CockpitGearRepository.getAppsForActiveGear(context, isOpenedFromLeftFlank, setIndex, ringIndex)
}

internal fun LightspeedCruiseOverlay.getGearSetNameById(setId: String): String {
    return com.sbf.lightspeed.system.CockpitGearRepository.getGearSetNameById(context, setId)
}

internal fun LightspeedCruiseOverlay.getGearSetNameByIndex(index: Int): String {
    return com.sbf.lightspeed.system.CockpitGearRepository.getGearSetNameByIndex(context, isOpenedFromLeftFlank, index)
}

internal fun LightspeedCruiseOverlay.triggerHyperdriveWarpLaunch(focalX: Float, focalY: Float, onLaunch: () -> Unit) {
    isCurrentlyTouched = false
    currentActiveZone = TouchZone.NONE
    glowAnimator?.cancel()
    glowFraction = 0f
    deepSpaceRenderer.isWarpLaunching = true
    deepSpaceRenderer.warpStartTime = System.currentTimeMillis()
    deepSpaceRenderer.warpFocalPointX = focalX
    deepSpaceRenderer.warpFocalPointY = focalY
    triggerHardwareHaptic(50, 255)
    invalidate()

    uiHandler.postDelayed({
        deepSpaceRenderer.isWarpLaunching = false
        dismissOverlay()
        onLaunch()
    }, 130L)
}
