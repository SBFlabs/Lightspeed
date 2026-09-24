package com.sbf.lightspeed

import android.view.MotionEvent

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
    currentTouchY = if (event.action == MotionEvent.ACTION_UP ||
        event.action == MotionEvent.ACTION_CANCEL) -1f else y

    if (currentLayer == CruiseLayer.COCKPIT_HANGAR) {
        return handleCockpitHangarTouch(event)
    }

    if (isStickyPinned) {
        return handleStickyPinTouch(event)
    }

    return handleCruiseTouch(event, superCall)
}
