package com.sbf.lightspeed

import android.graphics.RectF

enum class CruiseLayer { HIDDEN, NEUTRAL, CATEGORY, GRID, STICKY_PIN, FAVORITES_GEARS, COCKPIT_HANGAR }
enum class TouchZone { NONE, TOP_EDGE, CENTER_CRUISE, BOTTOM_EDGE }

enum class MacroGesture {
    NONE,
    SWIPE_UP, SWIPE_UP_HOLD,
    SWIPE_DOWN, SWIPE_DOWN_HOLD,
    SWIPE_LEFT, SWIPE_LEFT_HOLD,
    SWIPE_UP_DOWN, SWIPE_UP_DOWN_HOLD,
    SWIPE_DOWN_UP, SWIPE_DOWN_UP_HOLD,
    SWIPE_UP_LEFT, SWIPE_UP_LEFT_HOLD,
    SWIPE_DOWN_LEFT, SWIPE_DOWN_LEFT_HOLD,
    SWIPE_LEFT_BACK, SWIPE_LEFT_BACK_HOLD,
    SWIPE_LEFT_UP, SWIPE_LEFT_UP_HOLD,
    SWIPE_LEFT_DOWN, SWIPE_LEFT_DOWN_HOLD,
    SCRUBBING
}

enum class SensorGesture {
    NONE,
    TAP, DOUBLE_TAP, LONG_PRESS,
    SWIPE_LEFT, SWIPE_RIGHT, SWIPE_DOWN, SWIPE_UP,
    SWIPE_LEFT_BACK, SWIPE_RIGHT_BACK
}

data class PlacedItem(val app: LightspeedDataBridge.LaunchTarget, val bounds: RectF)

internal object CruiseOverlayConstants {
    const val TAG = "LightspeedCruiseOverlay"
    const val LONG_PRESS_THRESHOLD_MS = 400L
    const val NEUTRAL_TO_CATEGORY_DELAY_MS = 140L
}
