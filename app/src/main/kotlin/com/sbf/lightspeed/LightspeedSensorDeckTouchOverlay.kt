package com.sbf.lightspeed

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.accessibility.AccessibilityNodeInfo
import com.sbf.lightspeed.system.ActionDispatcher
import com.sbf.lightspeed.system.LightspeedHapticEngine
import com.sbf.lightspeed.system.defaultPrefs
import kotlin.math.abs
import kotlin.math.hypot

/**
 * Dedicated, isolated touch target overlay window for Sensor Area (Top-Edge) Gestures.
 *
 * Sized and positioned on WindowManager to match the exact user-configured Sensor Area geometry
 * (sensorLeft, sensorTop, spanPx, sensorHeight).
 *
 * All touches landing outside this exact rectangle physically miss this window, ensuring 100%
 * pass-through for the Android notification shade, status bar icons, Horizon Rails, and underlying apps.
 */
class LightspeedSensorDeckTouchOverlay(
    context: Context,
    private val service: AccessibilityService
) : View(context) {

    private val prefs = service.defaultPrefs()
    private val uiHandler = Handler(Looper.getMainLooper())

    private var startRawX = 0f
    private var startRawY = 0f
    private var startX = 0f
    private var startY = 0f
    private var furthestX = 0f
    private var isHoldFired = false
    private var isSecondTapInSequence = false
    private var currentGesture = "NONE"
    private var isHorizontalEngaged = false
    private var lastTapTime = 0L
    private var pendingTapRunnable: Runnable? = null

    private val holdRunnable = Runnable {
        val gestureKey = if (currentGesture != "NONE") currentGesture else if (isSecondTapInSequence) "DOUBLE_TAP" else "TAP"
        val actionKey = "pref_macro_action_STATUSBAR_${gestureKey}_HOLD"
        val action = prefs.getString(actionKey, "none") ?: "none"
        if (action != "none") {
            isHoldFired = true
            triggerHaptic(40, 200)
            performActionByName(action)
        }
    }

    init {
        isClickable = true
        isFocusable = false
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        pendingTapRunnable?.let { uiHandler.removeCallbacks(it) }
        uiHandler.removeCallbacks(holdRunnable)
    }

    private fun triggerHaptic(durationMs: Long = 25, amplitude: Int = 140) {
        LightspeedHapticEngine.vibrate(context, durationMs, amplitude)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        val isSensorEnabled = prefs.getBoolean("pref_statusbar_enabled", true)
        if (!isSensorEnabled) return false

        val density = resources.displayMetrics.density
        val sensPref = prefs.getInt("pref_statusbar_sensitivity", 40)
        val threshold = (sensPref * 0.5f * density).coerceAtLeast(10f * density)

        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                val nowDown = SystemClock.uptimeMillis()
                if (nowDown - lastTapTime < DOUBLE_TAP_TIMEOUT_MS) {
                    isSecondTapInSequence = true
                    pendingTapRunnable?.let { uiHandler.removeCallbacks(it) }
                    pendingTapRunnable = null
                    lastTapTime = 0L
                } else {
                    isSecondTapInSequence = false
                    pendingTapRunnable?.let { uiHandler.removeCallbacks(it) }
                    pendingTapRunnable = null
                }
                startRawX = event.rawX
                startRawY = event.rawY
                startX = event.x
                startY = event.y
                furthestX = event.x
                isHoldFired = false
                isHorizontalEngaged = false
                currentGesture = "NONE"

                uiHandler.postDelayed(holdRunnable, 360L)
                return true
            }

            MotionEvent.ACTION_MOVE -> {
                val rawDx = event.rawX - startRawX
                val rawDy = event.rawY - startRawY
                val dist = hypot(rawDx, rawDy)

                // Downward Pull: Immediately expand Android notification shade and cancel pending sensor gestures
                if (rawDy > threshold * 0.5f && rawDy > abs(rawDx) * 0.8f) {
                    uiHandler.removeCallbacks(holdRunnable)
                    pendingTapRunnable?.let { uiHandler.removeCallbacks(it) }
                    pendingTapRunnable = null
                    isHorizontalEngaged = false
                    currentGesture = "NONE"
                    service.performGlobalAction(AccessibilityService.GLOBAL_ACTION_NOTIFICATIONS)
                    return false
                }

                val touchSlop = ViewConfiguration.get(context).scaledTouchSlop.toFloat()
                val cancelDist = maxOf(threshold * 0.8f, touchSlop * 1.5f)
                if (dist > cancelDist && !isHoldFired && !isHorizontalEngaged) {
                    uiHandler.removeCallbacks(holdRunnable)
                }

                if (!isHorizontalEngaged && abs(rawDx) > threshold * 0.7f) {
                    isHorizontalEngaged = true
                    currentGesture = if (rawDx > 0) "SWIPE_RIGHT" else "SWIPE_LEFT"
                    furthestX = event.x
                    if (!isHoldFired) {
                        uiHandler.removeCallbacks(holdRunnable)
                        uiHandler.postDelayed(holdRunnable, 360L)
                    }
                }

                if (isHorizontalEngaged) {
                    if (currentGesture == "SWIPE_RIGHT" && event.x > furthestX) furthestX = event.x
                    if (currentGesture == "SWIPE_LEFT"  && event.x < furthestX) furthestX = event.x

                    val reboundThreshold = 18f * density
                    if (currentGesture == "SWIPE_RIGHT" && (furthestX - event.x) > reboundThreshold) {
                        currentGesture = "SWIPE_RIGHT_BACK"
                    } else if (currentGesture == "SWIPE_LEFT" && (event.x - furthestX) > reboundThreshold) {
                        currentGesture = "SWIPE_LEFT_BACK"
                    }
                }
                return true
            }

            MotionEvent.ACTION_UP -> {
                uiHandler.removeCallbacks(holdRunnable)
                val dx = event.x - startX
                val dy = event.y - startY
                val dist = hypot(dx, dy)

                if (isHoldFired) {
                    isHoldFired = false
                    isSecondTapInSequence = false
                    lastTapTime = 0L
                    return true
                }

                if (currentGesture != "NONE") {
                    isSecondTapInSequence = false
                    val actionKey = "pref_macro_action_STATUSBAR_$currentGesture"
                    val action = prefs.getString(actionKey, "none") ?: "none"
                    if (action != "none") {
                        triggerHaptic(30, 160)
                        performActionByName(action)
                        return true
                    }
                } else if (dist < threshold) {
                    handleTapSequence()
                    return true
                }
                isSecondTapInSequence = false
                return true
            }

            MotionEvent.ACTION_CANCEL -> {
                uiHandler.removeCallbacks(holdRunnable)
                pendingTapRunnable?.let { uiHandler.removeCallbacks(it) }
                pendingTapRunnable = null
                isHoldFired = false
                isSecondTapInSequence = false
                currentGesture = "NONE"
                return true
            }
        }
        return super.onTouchEvent(event)
    }

    private fun handleTapSequence() {
        if (isSecondTapInSequence) {
            isSecondTapInSequence = false
            lastTapTime = 0L
            val doubleTapAction = prefs.getString("pref_macro_action_STATUSBAR_DOUBLE_TAP", "none") ?: "none"
            if (doubleTapAction != "none") {
                triggerHaptic(30, 180)
                performActionByName(doubleTapAction)
            }
        } else {
            val doubleTapAction = prefs.getString("pref_macro_action_STATUSBAR_DOUBLE_TAP", "none") ?: "none"
            val doubleTapHoldAction = prefs.getString("pref_macro_action_STATUSBAR_DOUBLE_TAP_HOLD", "none") ?: "none"
            val singleTapAction = prefs.getString("pref_macro_action_STATUSBAR_TAP", "system:scroll_to_top") ?: "system:scroll_to_top"

            val hasDoubleTapAction = (doubleTapAction != "none" || doubleTapHoldAction != "none")
            if (hasDoubleTapAction) {
                lastTapTime = SystemClock.uptimeMillis()
                val tapTask = Runnable {
                    if (singleTapAction != "none") {
                        triggerHaptic(20, 120)
                        performActionByName(singleTapAction)
                    }
                    pendingTapRunnable = null
                    lastTapTime = 0L
                }
                pendingTapRunnable = tapTask
                uiHandler.postDelayed(tapTask, DOUBLE_TAP_TIMEOUT_MS)
            } else {
                if (singleTapAction != "none") {
                    triggerHaptic(20, 120)
                    performActionByName(singleTapAction)
                }
            }
        }
    }

    fun performActionByName(actionKey: String) {
        ActionDispatcher.execute(service, actionKey) {
            scrollToTop()
        }
    }

    fun scrollToTop() {
        val scrollableNodes = mutableListOf<AccessibilityNodeInfo>()
        val windows = service.windows

        if (!windows.isNullOrEmpty()) {
            for (window in windows) {
                if (window.type == android.view.accessibility.AccessibilityWindowInfo.TYPE_APPLICATION || window.isActive) {
                    val root = window.root ?: continue
                    collectScrollableNodes(root, scrollableNodes)
                }
            }
        }

        if (scrollableNodes.isEmpty()) {
            service.rootInActiveWindow?.let { root ->
                collectScrollableNodes(root, scrollableNodes)
            }
        }

        if (scrollableNodes.isEmpty()) return
        val targetNodes = scrollableNodes.reversed()

        for (node in targetNodes) {
            try {
                val bundle = Bundle().apply {
                    putInt(AccessibilityNodeInfo.ACTION_ARGUMENT_ROW_INT, 0)
                    putInt(AccessibilityNodeInfo.ACTION_ARGUMENT_COLUMN_INT, 0)
                }

                var success = false
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                    success = node.performAction(
                        AccessibilityNodeInfo.AccessibilityAction.ACTION_SCROLL_TO_POSITION.id,
                        bundle
                    )
                }

                if (!success) {
                    var passes = 0
                    while (passes < 25) {
                        val moved = (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R &&
                                        node.performAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_PAGE_UP.id)) ||
                                    (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q &&
                                        node.performAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_SCROLL_UP.id)) ||
                                    node.performAction(AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD)
                        if (!moved) break
                        passes++
                    }
                }
            } catch (_: Exception) {}
        }
    }

    private fun collectScrollableNodes(node: AccessibilityNodeInfo, list: MutableList<AccessibilityNodeInfo>) {
        val hasScrollAction = node.actionList.any { action ->
            action.id == AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD ||
            (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && action.id == AccessibilityNodeInfo.AccessibilityAction.ACTION_SCROLL_UP.id) ||
            (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N && action.id == AccessibilityNodeInfo.AccessibilityAction.ACTION_SCROLL_TO_POSITION.id)
        }

        if (node.isScrollable || hasScrollAction) {
            list.add(AccessibilityNodeInfo.obtain(node))
        }

        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            collectScrollableNodes(child, list)
        }
    }

    companion object {
        private const val DOUBLE_TAP_TIMEOUT_MS = 340L
    }
}
