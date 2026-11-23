package com.sbf.lightspeed

import android.view.MotionEvent
import android.view.ViewConfiguration
import kotlin.math.hypot

/**
 * Main touch event dispatcher for [LightspeedCruiseOverlay].
 *
 * Routing priority (short-circuits on first match):
 *  1. COCKPIT_HANGAR layer  -> [handleCockpitHangarTouch]
 *  2. Sticky-pinned grid    -> [handleStickyPinTouch]
 *  3. Cruise / macro zones  -> [handleCruiseTouch]
 */
internal fun LightspeedCruiseOverlay.handleTouchEvent(
    event: MotionEvent,
    superCall: () -> Boolean
): Boolean {
    val y = event.y
    currentTouchY = if (event.actionMasked == MotionEvent.ACTION_UP ||
        event.actionMasked == MotionEvent.ACTION_CANCEL) -1f else y

    if (allTipsOverlaySurface != null) {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                allTipsState.dismissArmed = true
                allTipsState.touchDownX = event.x
                allTipsState.touchDownY = event.y
                allTipsState.lastTouchY = event.y
                allTipsState.dragged = false
            }
            MotionEvent.ACTION_MOVE -> {
                if (allTipsState.dismissArmed && allTipsState.maxScroll > 0f) {
                    val dy = event.y - allTipsState.lastTouchY
                    allTipsState.lastTouchY = event.y
                    if (!allTipsState.dragged &&
                        hypot((event.x - allTipsState.touchDownX).toDouble(), (event.y - allTipsState.touchDownY).toDouble()) > ViewConfiguration.get(context).scaledTouchSlop
                    ) {
                        allTipsState.dragged = true
                    }
                    if (allTipsState.dragged) {
                        allTipsState.scrollOffset = (allTipsState.scrollOffset - dy).coerceIn(0f, allTipsState.maxScroll)
                        invalidate()
                    }
                }
            }
            MotionEvent.ACTION_UP -> {
                if (allTipsState.dismissArmed) {
                    val wasDrag = allTipsState.dragged
                    allTipsState.dismissArmed = false
                    allTipsState.dragged = false
                    if (!wasDrag) {
                        allTipsOverlaySurface = null
                        allTipsState.reset()
                        triggerHardwareHaptic(14, 70)
                        invalidate()
                    }
                }
            }
            MotionEvent.ACTION_CANCEL -> {
                allTipsState.dismissArmed = false
                allTipsState.dragged = false
            }
        }
        return true
    }

    if (isScreenTimeInfoBubbleOpen || isBatterySpanInfoBubbleOpen) {
        if (event.actionMasked == MotionEvent.ACTION_UP) {
            isScreenTimeInfoBubbleOpen = false
            isBatterySpanInfoBubbleOpen = false
            triggerHardwareHaptic(14, 70)
            invalidate()
        }
        return true
    }

    if (isCountdownModalOpen) {
        return handleCountdownModalTouch(event)
    }

    if (currentLayer == CruiseLayer.COCKPIT_HANGAR) {
        return handleCockpitHangarTouch(event)
    }

    if ((!isCruising && (isStickyPinned || currentLayer == CruiseLayer.STICKY_PIN)) || isHudEditMode || isHudTuneModalOpen) {
        return handleStickyPinTouch(event)
    }

    return handleCruiseTouch(event, superCall)
}
