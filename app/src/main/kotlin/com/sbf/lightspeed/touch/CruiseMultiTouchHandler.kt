package com.sbf.lightspeed

import android.os.Build
import android.os.SystemClock
import android.view.MotionEvent
import android.view.ViewConfiguration
import kotlin.math.abs
import kotlin.math.hypot

/**
 * Coordinates held per-overlay instance to support zero-stutter multi-touch during active
 * Category Cruise without mutating [LightspeedCruiseOverlay] line budget.
 */
internal class CruiseMultiTouchState {
    var activeCruisePointerId: Int = MotionEvent.INVALID_POINTER_ID
    var secondaryPointerId: Int = MotionEvent.INVALID_POINTER_ID
    var secondaryWidgetTarget: ScrollableAppWidgetContainer? = null
    var secondaryDownTime: Long = 0L
    var secondaryDownX: Float = 0f
    var secondaryDownY: Float = 0f
    var secondaryLastX: Float = 0f
    var secondaryLastY: Float = 0f
    var secondarySplitMode: Boolean = false
    var secondaryWidgetTappedThisSession: Boolean = false
    var secondaryActionCommitted: Boolean = false

    val srcWinLoc = IntArray(2)
    val dstWinLoc = IntArray(2)
    val singleProps = Array(1) { MotionEvent.PointerProperties() }
    val singleCoords = Array(1) { MotionEvent.PointerCoords() }

    fun hasSecondaryActive(): Boolean =
        secondaryPointerId != MotionEvent.INVALID_POINTER_ID || secondarySplitMode

    fun reset() {
        activeCruisePointerId = MotionEvent.INVALID_POINTER_ID
        secondaryPointerId = MotionEvent.INVALID_POINTER_ID
        secondaryWidgetTarget = null
        secondaryDownTime = 0L
        secondaryDownX = 0f
        secondaryDownY = 0f
        secondaryLastX = 0f
        secondaryLastY = 0f
        secondarySplitMode = false
        secondaryWidgetTappedThisSession = false
        secondaryActionCommitted = false
    }
}

private fun LightspeedCruiseOverlay.computeSourceToOverlayOffset(fromLeftWing: Boolean): Pair<Float, Float> {
    if (!fromLeftWing) return 0f to 0f
    val leftView = service?.leftWingOverlayView ?: return 0f to 0f
    val st = cruiseMultiTouch
    leftView.getLocationOnScreen(st.srcWinLoc)
    getLocationOnScreen(st.dstWinLoc)
    return (st.srcWinLoc[0] - st.dstWinLoc[0]).toFloat() to (st.srcWinLoc[1] - st.dstWinLoc[1]).toFloat()
}

private fun LightspeedCruiseOverlay.extractPointerScreenXY(
    ev: MotionEvent,
    pointerIndex: Int,
    fromLeftWing: Boolean
): Pair<Float, Float> {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        return ev.getRawX(pointerIndex) to ev.getRawY(pointerIndex)
    }
    val st = cruiseMultiTouch
    val winView = if (fromLeftWing) service?.leftWingOverlayView else this
    if (winView != null) {
        winView.getLocationOnScreen(st.srcWinLoc)
        return (st.srcWinLoc[0] + ev.getX(pointerIndex)) to (st.srcWinLoc[1] + ev.getY(pointerIndex))
    }
    return ev.getX(pointerIndex) to ev.getY(pointerIndex)
}

/**
 * Extracts a single-pointer [MotionEvent] for a hosted widget container while preserving
 * 100% of batched historical samples (`MotionEvent.addBatch`) so nested `VelocityTracker`
 * and collection scrolling stay silky-smooth. Caller must `recycle()` the returned event.
 */
private fun obtainSinglePointerEvent(
    st: CruiseMultiTouchState,
    source: MotionEvent,
    pointerIndex: Int,
    action: Int,
    downTime: Long,
    shiftX: Float,
    shiftY: Float
): MotionEvent {
    source.getPointerProperties(pointerIndex, st.singleProps[0])
    st.singleProps[0].id = 0

    val coords = st.singleCoords[0]
    val histSize = if (action == MotionEvent.ACTION_MOVE) source.historySize else 0
    if (histSize > 0) {
        source.getHistoricalPointerCoords(pointerIndex, 0, coords)
        coords.x += shiftX
        coords.y += shiftY
        val out = MotionEvent.obtain(
            downTime,
            source.getHistoricalEventTime(0),
            action,
            1,
            st.singleProps,
            st.singleCoords,
            source.metaState,
            source.buttonState,
            source.xPrecision,
            source.yPrecision,
            source.deviceId,
            source.edgeFlags,
            source.source,
            source.flags
        )
        for (h in 1 until histSize) {
            source.getHistoricalPointerCoords(pointerIndex, h, coords)
            coords.x += shiftX
            coords.y += shiftY
            out.addBatch(source.getHistoricalEventTime(h), st.singleCoords, source.metaState)
        }
        source.getPointerCoords(pointerIndex, coords)
        coords.x += shiftX
        coords.y += shiftY
        out.addBatch(source.eventTime, st.singleCoords, source.metaState)
        return out
    }

    source.getPointerCoords(pointerIndex, coords)
    coords.x += shiftX
    coords.y += shiftY
    return MotionEvent.obtain(
        downTime,
        source.eventTime,
        action,
        1,
        st.singleProps,
        st.singleCoords,
        source.metaState,
        source.buttonState,
        source.xPrecision,
        source.yPrecision,
        source.deviceId,
        source.edgeFlags,
        source.source,
        source.flags
    )
}

private fun dispatchToHostedContainer(
    st: CruiseMultiTouchState,
    container: ScrollableAppWidgetContainer,
    source: MotionEvent,
    pointerIndex: Int,
    action: Int,
    downTime: Long,
    overlayOffsetX: Float,
    overlayOffsetY: Float
): Boolean {
    val shiftX = overlayOffsetX - container.left.toFloat() - container.translationX
    val shiftY = overlayOffsetY - container.top.toFloat() - container.translationY
    val childEv = obtainSinglePointerEvent(st, source, pointerIndex, action, downTime, shiftX, shiftY)
    return try {
        container.dispatchTouchEvent(childEv)
    } finally {
        childEv.recycle()
    }
}

/**
 * Handles native `FLAG_SPLIT_TOUCH` Finger 2 events arriving directly on `LightspeedCruiseOverlay`
 * while Finger 1 holds the Left Flank edge window. Returns null if the caller should fall through
 * to normal single-finger / widget dispatch.
 */
internal fun LightspeedCruiseOverlay.handleSplitSecondaryTouch(
    ev: MotionEvent,
    superCall: () -> Boolean
): Boolean? {
    val st = cruiseMultiTouch
    val layerInteractive = currentLayer == CruiseLayer.CATEGORY ||
        currentLayer == CruiseLayer.GRID ||
        currentLayer == CruiseLayer.STICKY_PIN

    if (widgetTouchTarget != null && !st.secondarySplitMode) {
        val handled = superCall()
        if (ev.actionMasked == MotionEvent.ACTION_UP || ev.actionMasked == MotionEvent.ACTION_CANCEL) {
            widgetTouchTarget = null
        }
        return handled
    }

    if (!layerInteractive) return null

    when (ev.actionMasked) {
        MotionEvent.ACTION_DOWN -> {
            val hitWidget = if (!isTouchOnCategoryCruiseTip(ev.x, ev.y)) findWidgetContainerAt(ev.x, ev.y) else null
            if (hitWidget != null) {
                widgetTouchTarget = hitWidget
                st.secondarySplitMode = false
                st.secondaryWidgetTappedThisSession = true
                return superCall()
            }
            st.secondarySplitMode = true
            st.secondaryDownTime = ev.downTime
            st.secondaryDownX = ev.x
            st.secondaryDownY = ev.y
            st.secondaryLastX = ev.x
            st.secondaryLastY = ev.y
            return true
        }
        MotionEvent.ACTION_MOVE -> {
            if (!st.secondarySplitMode) return null
            st.secondaryLastX = ev.x
            st.secondaryLastY = ev.y
            // Stationary Finger 1 + moving Finger 2 outside widgets needs zero overlay invalidation.
            return true
        }
        MotionEvent.ACTION_UP -> {
            if (!st.secondarySplitMode) return null
            st.secondarySplitMode = false
            val dp = resources.displayMetrics.density
            val maxSlop = maxOf(24f * dp, ViewConfiguration.get(context).scaledTouchSlop.toFloat())
            val dist = hypot((ev.x - st.secondaryDownX).toDouble(), (ev.y - st.secondaryDownY).toDouble()).toFloat()
            if (dist <= maxSlop) {
                tryExecuteSecondaryCruiseTap(ev.x, ev.y)
            }
            return true
        }
        MotionEvent.ACTION_CANCEL -> {
            if (!st.secondarySplitMode) return null
            st.secondarySplitMode = false
            return true
        }
    }
    return null
}

/**
 * Handles same-window multi-touch (`ACTION_POINTER_DOWN` / `ACTION_MOVE` / `ACTION_POINTER_UP`)
 * on Right Flank cruise (or OEM Left Flank fallback where split touch isn't separated).
 */
internal fun LightspeedCruiseOverlay.handleSameWindowMultiTouch(
    ev: MotionEvent,
    fromLeftWing: Boolean = isDispatchingFromLeftFlankView,
    superCall: (() -> Boolean)? = null
): Boolean {
    val st = cruiseMultiTouch
    if (st.activeCruisePointerId == MotionEvent.INVALID_POINTER_ID && ev.pointerCount > 0) {
        st.activeCruisePointerId = ev.getPointerId(0)
    }

    val (offX, offY) = computeSourceToOverlayOffset(fromLeftWing)
    val layerInteractive = currentLayer == CruiseLayer.CATEGORY ||
        currentLayer == CruiseLayer.GRID ||
        currentLayer == CruiseLayer.STICKY_PIN

    when (ev.actionMasked) {
        MotionEvent.ACTION_POINTER_DOWN -> {
            if (layerInteractive && st.secondaryPointerId == MotionEvent.INVALID_POINTER_ID) {
                val pIdx = ev.actionIndex
                val pId = ev.getPointerId(pIdx)
                if (pId != st.activeCruisePointerId) {
                    val p2x = ev.getX(pIdx) + offX
                    val p2y = ev.getY(pIdx) + offY
                    st.secondaryPointerId = pId
                    st.secondaryDownTime = ev.eventTime
                    st.secondaryDownX = p2x
                    st.secondaryDownY = p2y
                    st.secondaryLastX = p2x
                    st.secondaryLastY = p2y

                    val hitWidget = if (!isTouchOnCategoryCruiseTip(p2x, p2y)) findWidgetContainerAt(p2x, p2y) else null
                    if (hitWidget != null) {
                        st.secondaryWidgetTarget = hitWidget
                        st.secondaryWidgetTappedThisSession = true
                        dispatchToHostedContainer(st, hitWidget, ev, pIdx, MotionEvent.ACTION_DOWN, st.secondaryDownTime, offX, offY)
                    } else {
                        st.secondaryWidgetTarget = null
                    }
                }
            }
            return true
        }

        MotionEvent.ACTION_MOVE -> {
            val secIdx = if (st.secondaryPointerId != MotionEvent.INVALID_POINTER_ID) {
                ev.findPointerIndex(st.secondaryPointerId)
            } else -1
            if (secIdx >= 0) {
                st.secondaryLastX = ev.getX(secIdx) + offX
                st.secondaryLastY = ev.getY(secIdx) + offY
                st.secondaryWidgetTarget?.let { target ->
                    dispatchToHostedContainer(st, target, ev, secIdx, MotionEvent.ACTION_MOVE, st.secondaryDownTime, offX, offY)
                }
            }

            // Evaluate primary cruise pointer ONLY when it actually moves (>= 0.5px), avoiding stutter.
            val p1Idx = ev.findPointerIndex(st.activeCruisePointerId)
            if (p1Idx >= 0) {
                val (p1RawX, p1RawY) = extractPointerScreenXY(ev, p1Idx, fromLeftWing)
                val p1Delta = hypot((p1RawX - lastTouchRawX).toDouble(), (p1RawY - lastTouchRawY).toDouble())
                if (p1Delta >= 0.5) {
                    applyPrimaryCruisePointerMove(p1RawX, p1RawY)
                }
            }
            return true
        }

        MotionEvent.ACTION_POINTER_UP -> {
            val pIdx = ev.actionIndex
            val pId = ev.getPointerId(pIdx)
            if (pId == st.secondaryPointerId) {
                val p2x = ev.getX(pIdx) + offX
                val p2y = ev.getY(pIdx) + offY
                val target = st.secondaryWidgetTarget
                st.secondaryWidgetTarget = null
                st.secondaryPointerId = MotionEvent.INVALID_POINTER_ID
                if (target != null) {
                    dispatchToHostedContainer(st, target, ev, pIdx, MotionEvent.ACTION_UP, st.secondaryDownTime, offX, offY)
                } else if (layerInteractive) {
                    val dp = resources.displayMetrics.density
                    val maxSlop = maxOf(24f * dp, ViewConfiguration.get(context).scaledTouchSlop.toFloat())
                    val dist = hypot((p2x - st.secondaryDownX).toDouble(), (p2y - st.secondaryDownY).toDouble()).toFloat()
                    if (dist <= maxSlop) {
                        tryExecuteSecondaryCruiseTap(p2x, p2y)
                    }
                }
                return true
            } else if (pId == st.activeCruisePointerId) {
                st.activeCruisePointerId = MotionEvent.INVALID_POINTER_ID
                val activeTarget = st.secondaryWidgetTarget
                if (activeTarget != null && ev.pointerCount > 1) {
                    // Promote Finger 2's widget interaction into pinned mode cleanly without cutting off drag/scroll.
                    isStickyPinned = true
                    currentLayer = CruiseLayer.GRID
                    isCruising = false
                    isCurrentlyTouched = false
                    currentActiveZone = TouchZone.NONE
                    updateMetricsDimensions()
                    invalidate()
                } else {
                    val secIdx = if (st.secondaryPointerId != MotionEvent.INVALID_POINTER_ID) {
                        ev.findPointerIndex(st.secondaryPointerId)
                    } else -1
                    if (secIdx >= 0 && layerInteractive) {
                        val p2x = ev.getX(secIdx) + offX
                        val p2y = ev.getY(secIdx) + offY
                        val dp = resources.displayMetrics.density
                        val maxSlop = maxOf(24f * dp, ViewConfiguration.get(context).scaledTouchSlop.toFloat())
                        val dist = hypot((p2x - st.secondaryDownX).toDouble(), (p2y - st.secondaryDownY).toDouble()).toFloat()
                        st.secondaryPointerId = MotionEvent.INVALID_POINTER_ID
                        if (dist <= maxSlop && tryExecuteSecondaryCruiseTap(p2x, p2y)) {
                            return true
                        }
                    }
                    st.secondaryPointerId = MotionEvent.INVALID_POINTER_ID
                    st.secondaryWidgetTarget = null
                    if (st.secondaryActionCommitted) {
                        isCruising = false
                        isCurrentlyTouched = false
                    } else if (st.secondaryWidgetTappedThisSession && !isStickyPinned) {
                        dismissOverlay()
                    }
                }
                return true
            }
            return true
        }

        MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
            val secIdx = if (st.secondaryPointerId != MotionEvent.INVALID_POINTER_ID) {
                ev.findPointerIndex(st.secondaryPointerId)
            } else -1
            st.secondaryWidgetTarget?.let { target ->
                if (secIdx >= 0) {
                    val upAction = if (ev.actionMasked == MotionEvent.ACTION_CANCEL) {
                        MotionEvent.ACTION_CANCEL
                    } else {
                        MotionEvent.ACTION_UP
                    }
                    dispatchToHostedContainer(st, target, ev, secIdx, upAction, st.secondaryDownTime, offX, offY)
                }
            }
            st.secondaryWidgetTarget = null
            st.secondaryPointerId = MotionEvent.INVALID_POINTER_ID
            st.activeCruisePointerId = MotionEvent.INVALID_POINTER_ID

            if (st.secondaryActionCommitted || st.secondaryWidgetTappedThisSession) {
                isCurrentlyTouched = false
                if (!st.secondaryActionCommitted && !isStickyPinned) {
                    dismissOverlay()
                }
                return true
            }
            return if (fromLeftWing) {
                false // Let caller complete standard UP/CANCEL sequence if needed
            } else {
                (superCall?.invoke() ?: onTouchEvent(ev)).also {
                    isCurrentlyTouched = false
                }
            }
        }
    }
    return true
}

internal fun LightspeedCruiseOverlay.applyPrimaryCruisePointerMove(rawX: Float, rawY: Float) {
    val density = resources.displayMetrics.density
    val deltaX = if (isOpenedFromLeftFlank) (rawX - touchDownRawX) else (touchDownRawX - rawX)
    val deltaY = abs(rawY - touchDownRawY)
    if (deltaY > maxVerticalDisplacement) {
        maxVerticalDisplacement = deltaY
    }

    if (currentLayer == CruiseLayer.NEUTRAL &&
        !categoryScrubbingEngaged && deltaX > (14f * density) && deltaX >= (deltaY * 0.85f)
    ) {
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
        triggerDeflectorHaptic(isClick = true)
    }

    if (currentLayer == CruiseLayer.NEUTRAL && maxVerticalDisplacement > (14f * density)) {
        uiHandler.removeCallbacks(neutralToCategoryRunnable)
        categoryScrubbingEngaged = true
        currentLayer = CruiseLayer.CATEGORY
        entranceStartTime = SystemClock.uptimeMillis()
    }

    if (currentLayer == CruiseLayer.FAVORITES_GEARS) {
        processGyroscopeTouchPhysics(rawX, rawY)
    } else if (currentLayer == CruiseLayer.CATEGORY || currentLayer == CruiseLayer.GRID || currentLayer == CruiseLayer.STICKY_PIN) {
        if (currentLayer == CruiseLayer.CATEGORY) {
            val dx = rawX - lastTouchRawX
            val dy = rawY - lastTouchRawY
            val isInward = if (isOpenedFromLeftFlank) (dx > 0f) else (dx < 0f)
            if (isInward && abs(dx) > (abs(dy) * 1.15f)) {
                isCategoryVectorLocked = true
            } else if (abs(dy) > (abs(dx) * 1.05f)) {
                isCategoryVectorLocked = false
            }
        }
        evaluateSpatialMetrics(rawX, rawY, rawX, rawY)
    }

    lastTouchRawX = rawX
    lastTouchRawY = rawY
    invalidate()
}

private fun LightspeedCruiseOverlay.tryExecuteSecondaryCruiseTap(x: Float, y: Float): Boolean {
    if (isTouchOnCategoryCruiseTip(x, y)) {
        openAllTipsOverlay(AllTipsSurface.CATEGORY_CRUISE, promoteFromCruise = true)
        return true
    }
    val st = cruiseMultiTouch
    // 1. Lightspeed built-in Cruise HUD pods / interactive cards (Screen Time, System Vitals, Inspector)
    if (handleTopHudStripTap(x, y)) {
        st.secondaryWidgetTappedThisSession = true
        if (isVitalsModalOpen || isAlertsHistoryModalOpen || isFirewallModalOpen || isCountdownModalOpen) {
            isStickyPinned = true
            isCruising = false
            currentLayer = CruiseLayer.GRID
            st.secondaryActionCommitted = true
            updateMetricsDimensions()
            invalidate()
        }
        return true
    }

    // 2. Direct app tap inside the live Cruise app grid (even before releasing Finger 1)
    if (currentLayer == CruiseLayer.GRID || currentLayer == CruiseLayer.STICKY_PIN) {
        val absoluteY = y + viewportScrollOffset
        val hit = placedAppsList.firstOrNull { !it.app.isWidget && it.bounds.contains(x, absoluteY) }
        if (hit != null) {
            st.secondaryActionCommitted = true
            isCruising = false
            categoryScrubbingEngaged = false
            st.activeCruisePointerId = MotionEvent.INVALID_POINTER_ID
            st.secondaryPointerId = MotionEvent.INVALID_POINTER_ID
            val (focalX, focalY) = resolvePlacedItemIconCenter(hit)
            triggerHyperdriveWarpLaunch(focalX, focalY) {
                executeLaunch(hit.app)
            }
            return true
        }
    }
    return false
}
