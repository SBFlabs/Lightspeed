package com.sbf.lightspeed

import com.sbf.lightspeed.system.logSwallowed
import android.content.Intent
import android.view.MotionEvent
import com.sbf.lightspeed.system.LightspeedPreferences

internal fun LightspeedCruiseOverlay.persistActiveGearSetIndex() {
    com.sbf.lightspeed.system.CockpitGearRepository.persistActiveGearSetIndex(context, activeGearSetIndex, isOpenedFromLeftFlank)
}

internal fun LightspeedCruiseOverlay.executeLaunch(target: LightspeedDataBridge.LaunchTarget) {
    com.sbf.lightspeed.system.LightspeedShortcutManager.launchApp(context, target.packageName)
}

internal fun LightspeedCruiseOverlay.resolveCleanAppLabel(itemToken: String): String {
    return com.sbf.lightspeed.system.LightspeedShortcutManager.resolveLabel(context, itemToken)
}

internal fun LightspeedCruiseOverlay.launchLauncherSettings() {
    dismissOverlay()
    uiHandler.postDelayed({
        try {
            val settingsIntent = Intent(context, MainActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS)
            }
            context.startActivity(settingsIntent, com.sbf.lightspeed.system.LightspeedShortcutManager.createLaunchBundle(context))
        } catch (_: Exception) {
            val alternativeIntent = context.packageManager.getLaunchIntentForPackage(context.packageName)?.apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS)
            }
            alternativeIntent?.let { context.startActivity(it, com.sbf.lightspeed.system.LightspeedShortcutManager.createLaunchBundle(context)) }
        }
    }, 45L)
}

internal fun LightspeedCruiseOverlay.dismissOverlay() {
    stopGearAutoRepeat()
    sparkPreviewCancelRunnable?.let { uiHandler.removeCallbacks(it) }
    sparkPreviewCancelRunnable = null
    deepSpaceRenderer.reactorRenderer.cancelWarp()
    uiHandler.removeCallbacks(hudLiveRefreshRunnable)
    uiHandler.removeCallbacks(neutralToCategoryRunnable)
    uiHandler.removeCallbacks(holdTimerRunnable)
    settingsRevealHapticFired = false
    pendingHoldReleaseScrollAction = null
    categoryTipRotatedForSession = false
    LightspeedCruiseHudPods.resetHudEntranceState()
    longPressHangarBayRunnable?.let { removeCallbacks(it) }
    longPressCogRunnable?.let { removeCallbacks(it) }
    longPressHangarBayRunnable = null
    longPressCogRunnable = null
    hasLongPressFired = false

    snapAnimator?.cancel()
    glowDelegate.cancel()
    isCurrentlyTouched = false
    currentTouchY = -1f
    currentActiveZone = TouchZone.NONE
    updatePillShaderUniforms(active = false)
    macroTrackingActive = false
    isDraggingHangarBays = false
    isSpinningHangarRing = false
    isTouchingFocusedCog = false
    isStickyPinned = false
    isCruising = false
    cruiseMultiTouch.reset()
    allTipsOverlaySurface = null
    allTipsState.reset()
    isTouchingHangarTip = false
    com.sbf.lightspeed.system.CockpitGimbalConfigCache.clearPreview()
    categoryCruiseTipHitRect.setEmpty()
    deepSpaceRenderer.hangarTipRect.setEmpty()
    categoryScrubbingEngaged = false
    maxVerticalDisplacement = 0f
    currentLayer = CruiseLayer.HIDDEN
    activeItem = null; activeCatIndex = -1; viewportScrollOffset = 0f; categoryVisualOffset = 0f; hudScrollOffset = 0f
    cachedHudBoundCatId = null
    categoryIconJob?.cancel()
    categoryIconJob = null
    applicationIconCache.clear()
    categoryAppsCache.clear()
    categoryGridCache.clear()
    categoryHeightCache.clear()
    lastLoadedCategoryId = null
    placedAppsList.clear(); cachedApps = emptyList(); cachedCategories = emptyList()
    uiHandler.removeCallbacks(widgetSettleRunnable)
    widgetSettlePending = false
    try { cruiseAppWidgetHost?.stopListening() } catch (_: Exception) {}
    cruiseAppWidgetHostView?.visibility = android.view.View.GONE
    cruiseAppWidgetHostView2?.visibility = android.view.View.GONE
    for ((_, view) in cruiseAppWidgetHostViews) {
        view.visibility = android.view.View.GONE
    }
    for ((_, container) in cruiseAppWidgetHostContainers) {
        container.visibility = android.view.View.GONE
    }
    // Trim warm off-screen widget views when hidden so large RemoteViews hierarchies don't linger in RAM
    val excessWarm = cruiseAppWidgetHostContainers.keys.toList().dropLast(1)
    for (id in excessWarm) {
        cruiseAppWidgetHostContainers.remove(id)?.let {
            it.visibility = android.view.View.GONE
            removeView(it)
        }
        cruiseAppWidgetHostViews.remove(id)
    }
    if (getFlankLaunchBehavior(isOpenedFromLeftFlank) != "last") {
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

fun LightspeedCruiseOverlay.startCruiseFromFlank(
    isLeft: Boolean,
    startRawX: Float,
    startRawY: Float,
    pointerId: Int = MotionEvent.INVALID_POINTER_ID
) {
    performStartCruiseFromFlank(isLeft, startRawX, startRawY, pointerId)
}

fun LightspeedCruiseOverlay.handleFlankTouchEvent(isLeft: Boolean, event: MotionEvent): Boolean {
    return performFlankTouchEvent(isLeft, event)
}

fun LightspeedCruiseOverlay.openHangarDirectly(setIndex: Int = -1) {
    com.sbf.lightspeed.system.CockpitGearRepository.prewarmGearCache(context)
    uiHandler.removeCallbacks(neutralToCategoryRunnable)
    uiHandler.removeCallbacks(holdTimerRunnable)
    categoryScrubbingEngaged = false
    maxVerticalDisplacement = 0f
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

fun LightspeedCruiseOverlay.openCategoryCruisePinned(categoryId: String) {
    uiHandler.removeCallbacks(neutralToCategoryRunnable)
    uiHandler.removeCallbacks(holdTimerRunnable)
    categoryScrubbingEngaged = false
    maxVerticalDisplacement = 0f
    cachedCategories = dataBridge.getLiveCategories()
    val idx = cachedCategories.indexOfFirst { it.id == categoryId }.let { if (it >= 0) it else 0 }
    activeCatIndex = idx
    currentLayer = CruiseLayer.STICKY_PIN
    isCruising = false
    isStickyPinned = true
    applicationIconCache.clear()
    categoryGridCache.clear()
    categoryHeightCache.clear()
    categoryAppsCache.clear()
    lastLoadedCategoryId = null
    service?.updateWindowLayout(true)
    updateMetricsDimensions()
    computeHudSlotBounds()
    loadActiveCategoryGrid()
    invalidate()
    // Re-sync once WindowManager finishes expanding the overlay from collapsed edge strip to full screen
    post {
        if (isStickyPinned || currentLayer == CruiseLayer.STICKY_PIN) {
            updateMetricsDimensions()
            refreshHudAndAppGridLayout()
            preloadActiveCategoryIconsAsync()
        }
    }
}

fun LightspeedCruiseOverlay.openHangarFromFlank(isLeftFlank: Boolean) {
    com.sbf.lightspeed.system.CockpitGearRepository.prewarmGearCache(context)
    uiHandler.removeCallbacks(neutralToCategoryRunnable)
    uiHandler.removeCallbacks(holdTimerRunnable)
    categoryScrubbingEngaged = false
    maxVerticalDisplacement = 0f
    isOpenedFromLeftFlank = isLeftFlank
    val prefs = prefs()
    val setsList = getGearSetsOrder(isLeftFlank)
    val launchBehavior = getFlankLaunchBehavior(isLeftFlank)
    val lastActiveKey = if (isLeftFlank) LightspeedPreferences.KEY_LAST_ACTIVE_SET_INDEX_LEFT else LightspeedPreferences.KEY_LAST_ACTIVE_SET_INDEX_RIGHT

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

internal fun LightspeedCruiseOverlay.resolvePlacedItemIconCenter(placed: PlacedItem?): Pair<Float, Float> {
    if (placed == null) return (width / 2f) to (height / 2f)
    val b = placed.bounds
    val hasInspector = activeCategoryHudItems.any { it.type == LightspeedCruiseHudPods.MODE_APP_AND_CAT }
    val hideGridLabels = hasInspector && currentLayer == CruiseLayer.GRID && !isStickyPinned
    val sz = minOf(b.width() * 0.48f, b.height() * 0.48f)
    val iconCenterYUnscrolled = if (placed.app.isWidget || hideGridLabels) {
        b.centerY()
    } else {
        b.centerY() - (sz * 0.12f)
    }
    return b.centerX() to (iconCenterYUnscrolled - viewportScrollOffset)
}

internal fun LightspeedCruiseOverlay.triggerHyperdriveWarpLaunch(focalX: Float, focalY: Float, onLaunch: () -> Unit) {
    isCurrentlyTouched = false
    currentActiveZone = TouchZone.NONE
    glowDelegate.cancel()
    deepSpaceRenderer.reactorRenderer.startWarp(focalX, focalY)
    triggerHardwareHaptic(50, 255)
    invalidate()
    postInvalidateOnAnimation()

    uiHandler.postDelayed({
        deepSpaceRenderer.reactorRenderer.cancelWarp()
        dismissOverlay()
        onLaunch()
    }, 130L)
}
