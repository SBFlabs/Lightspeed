package com.sbf.lightspeed

import android.accessibilityservice.AccessibilityService
import android.view.MotionEvent
import kotlin.math.abs

class LightspeedGestureEngine(private val service: AccessibilityService) {

    private var downX = 0f
    private var downY = 0f
    private var downTime = 0L

    private val swipeThreshold = 120f
    private val timeThreshold = 400L // Macro gesture time constraint ms

    fun processEdgeTouch(event: MotionEvent) {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                downX = event.x
                downY = event.y
                downTime = System.currentTimeMillis()
            }
            MotionEvent.ACTION_UP -> {
                val deltaX = event.x - downX
                val deltaY = event.y - downY
                val elapsedTime = System.currentTimeMillis() - downTime

                if (elapsedTime <= timeThreshold) {
                    evaluateSwipePatterns(deltaX, deltaY)
                }
            }
        }
    }

    private fun evaluateSwipePatterns(dX: Float, dY: Float) {
        if (abs(dX) > abs(dY)) {
            // Horizontal Macro Action
            if (dX < -swipeThreshold) {
                // Swipe Left: Invoke Overview Workspace Layout
                service.performGlobalAction(AccessibilityService.GLOBAL_ACTION_RECENTS)
            }
        } else {
            // Vertical Macro Actions
            if (dY < -swipeThreshold) {
                // Swipe Up: Return directly to Default Home Launcher Canvas
                service.performGlobalAction(AccessibilityService.GLOBAL_ACTION_HOME)
            } else if (dY > swipeThreshold) {
                // Swipe Down: Deploy System Notification Drawer Dropdown
                service.performGlobalAction(AccessibilityService.GLOBAL_ACTION_NOTIFICATIONS)
            }
        }
    }
}
