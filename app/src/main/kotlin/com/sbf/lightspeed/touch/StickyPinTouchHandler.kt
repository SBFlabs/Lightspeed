package com.sbf.lightspeed

import android.view.MotionEvent
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.max

/**
 * Handles touch when the overlay is in sticky-pinned grid mode.
 *
 * @return true if the event was consumed.
 */
internal fun LightspeedCruiseOverlay.handleStickyPinTouch(event: MotionEvent): Boolean {
    val x = event.x; val y = event.y
    val hF = height.toFloat()
    val gridTopLimit = hF * 0.15f
    val gridBottomLimit = hF * 0.94f
    val gridHeightScope = gridBottomLimit - gridTopLimit
    val maxScroll = max(0f, totalGridContentHeight - gridHeightScope)

    when (event.actionMasked) {
        MotionEvent.ACTION_DOWN -> {
            touchDownX = x; touchDownY = y; lastTouchY = y
            activeItem = null
            if (y in gridTopLimit..gridBottomLimit) {
                val absoluteY = y + viewportScrollOffset
                for (placedItem in placedAppsList) {
                    if (placedItem.bounds.contains(x, absoluteY)) { activeItem = placedItem.app; break }
                }
            }
            invalidate(); return true
        }
        MotionEvent.ACTION_MOVE -> {
            val deltaY = y - lastTouchY
            lastTouchY = y
            if (abs(y - touchDownY) > 16f || abs(x - touchDownX) > 16f) activeItem = null
            if (maxScroll > 0f) {
                viewportScrollOffset = (viewportScrollOffset - deltaY).coerceIn(0f, maxScroll)
                invalidate()
            }
            return true
        }
        MotionEvent.ACTION_UP -> {
            if (hypot((x - touchDownX).toDouble(), (y - touchDownY).toDouble()) < 16f) {
                if (y in gridTopLimit..gridBottomLimit) activeItem?.let { executeLaunch(it); dismissOverlay() } ?: dismissOverlay()
                else dismissOverlay()
            }
            activeItem = null; invalidate(); return true
        }
        MotionEvent.ACTION_CANCEL -> { isCurrentlyTouched = false; activeItem = null; invalidate(); return true }
    }
    return true
}
