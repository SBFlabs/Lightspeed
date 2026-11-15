package com.sbf.lightspeed

import android.view.MotionEvent
import com.sbf.lightspeed.system.defaultPrefs

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

    if (isScreenTimeInfoBubbleOpen) {
        if (event.actionMasked == MotionEvent.ACTION_UP) {
            isScreenTimeInfoBubbleOpen = false
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

    if (isStickyPinned || isHudEditMode || isHudTuneModalOpen || currentLayer == CruiseLayer.STICKY_PIN) {
        return handleStickyPinTouch(event)
    }

    return handleCruiseTouch(event, superCall)
}
