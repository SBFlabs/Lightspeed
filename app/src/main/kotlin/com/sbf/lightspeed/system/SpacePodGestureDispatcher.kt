package com.sbf.lightspeed.system

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log

/**
 * High-cadence 4-gesture state machine for Space Pods across QS Tiles,
 * Home Screen Shortcuts, and 1x1 Widget Pods.
 *
 * Supported Gestures:
 * 1. CLICK: Single tap (dispatched if no second tap or hold within 320ms).
 * 2. DOUBLE_CLICK: Double tap within 320ms window.
 * 3. HOLD: Long press.
 * 4. CLICK_THEN_HOLD: Single tap followed by a long press within 650ms.
 */
object SpacePodGestureDispatcher {

    private const val TAG = "SpacePodDispatcher"
    private const val DOUBLE_CLICK_WINDOW_MS = 320L
    private const val CLICK_THEN_HOLD_WINDOW_MS = 650L

    const val GESTURE_CLICK = "click"
    const val GESTURE_DOUBLE_CLICK = "double_click"
    const val GESTURE_HOLD = "hold"
    const val GESTURE_CLICK_THEN_HOLD = "click_then_hold"

    private val mainHandler = Handler(Looper.getMainLooper())

    // Per-pod click state tracking
    private val lastClickTimestamp = LongArray(5) { 0L }
    private val pendingClickRunnable = arrayOfNulls<Runnable>(5)

    fun getActionPrefKey(podIndex: Int, gesture: String): String {
        val safeIndex = podIndex.coerceIn(1, 4)
        return "pref_space_pod_${safeIndex}_$gesture"
    }

    /**
     * Returns true if the user has explicitly configured a primary CLICK action for this Pod.
     */
    fun isPodConfigured(context: Context, podIndex: Int): Boolean {
        val prefs = context.defaultPrefs()
        val key = getActionPrefKey(podIndex, GESTURE_CLICK)
        return !prefs.getString(key, null).isNullOrBlank()
    }

    fun getActionToken(context: Context, podIndex: Int, gesture: String): String? {
        val prefs = context.defaultPrefs()
        val key = getActionPrefKey(podIndex, gesture)
        val saved = prefs.getString(key, null)
        if (!saved.isNullOrBlank()) return saved

        // Tactical default fallback mappings
        return when (gesture) {
            GESTURE_CLICK -> when (podIndex) {
                1 -> "system:notifications"
                2 -> "system:quick_settings"
                3 -> "system:screenshot"
                else -> "system:flashlight"
            }
            GESTURE_DOUBLE_CLICK -> when (podIndex) {
                1 -> "system:previous_app"
                2 -> "system:split_screen"
                3 -> "system:freeform"
                else -> "system:tactical_flyout"
            }
            GESTURE_HOLD -> when (podIndex) {
                1 -> "system:power_menu"
                2 -> "system:lock_screen"
                3 -> "system:volume_deck"
                else -> "system:open_central_command"
            }
            GESTURE_CLICK_THEN_HOLD -> "system:open_central_command"
            else -> null
        }
    }

    /**
     * Dispatches a tap event on a Space Pod.
     * Evaluates single click vs double click using a 320ms window.
     */
    fun onPodTapped(context: Context, podIndex: Int) {
        val idx = podIndex.coerceIn(1, 4)
        val now = SystemClock.uptimeMillis()
        val diff = now - lastClickTimestamp[idx]

        // Check for double click within 320ms
        if (diff in 1..DOUBLE_CLICK_WINDOW_MS) {
            pendingClickRunnable[idx]?.let { mainHandler.removeCallbacks(it) }
            pendingClickRunnable[idx] = null
            lastClickTimestamp[idx] = 0L
            dispatchGesture(context, idx, GESTURE_DOUBLE_CLICK)
            return
        }

        // First click: record uptime and arm single-click timer
        lastClickTimestamp[idx] = now
        pendingClickRunnable[idx]?.let { mainHandler.removeCallbacks(it) }

        val runnable = Runnable {
            pendingClickRunnable[idx] = null
            dispatchGesture(context, idx, GESTURE_CLICK)
        }
        pendingClickRunnable[idx] = runnable
        mainHandler.postDelayed(runnable, DOUBLE_CLICK_WINDOW_MS)
    }

    /**
     * Dispatches a long-press (hold) event on a Space Pod.
     * Checks if a previous tap occurred within 650ms to trigger CLICK_THEN_HOLD.
     */
    fun onPodLongPressed(context: Context, podIndex: Int) {
        val idx = podIndex.coerceIn(1, 4)
        val now = SystemClock.uptimeMillis()
        val diff = now - lastClickTimestamp[idx]

        // Cancel any pending single-click execution
        pendingClickRunnable[idx]?.let { mainHandler.removeCallbacks(it) }
        pendingClickRunnable[idx] = null

        val isClickThenHold = diff in 1..CLICK_THEN_HOLD_WINDOW_MS
        lastClickTimestamp[idx] = 0L

        if (isClickThenHold) {
            dispatchGesture(context, idx, GESTURE_CLICK_THEN_HOLD)
        } else {
            dispatchGesture(context, idx, GESTURE_HOLD)
        }
    }

    /**
     * Executes the configured action for this pod and gesture.
     */
    fun dispatchGesture(context: Context, podIndex: Int, gesture: String) {
        val token = getActionToken(context, podIndex, gesture)
        Log.i(TAG, "Dispatching Pod $podIndex Gesture '$gesture' -> Token: $token")
        if (!token.isNullOrBlank() && token != "none") {
            ActionDispatcher.executeDirect(context, token)
        }
    }
}
