package com.sbf.lightspeed

import android.accessibilityservice.AccessibilityService
import android.app.KeyguardManager
import android.content.Context
import android.media.AudioManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.provider.Settings
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.accessibility.AccessibilityNodeInfo
import android.view.accessibility.AccessibilityWindowInfo
import com.sbf.lightspeed.system.ActionDispatcher
import com.sbf.lightspeed.system.LightspeedHapticEngine
import com.sbf.lightspeed.system.LightspeedTimeoutEngine
import com.sbf.lightspeed.system.defaultPrefs
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.roundToInt

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
    private var currentGesture: SensorGesture = SensorGesture.NONE
    private var isHorizontalEngaged = false
    private var isDownwardPullFired = false
    private var lastTapTime = 0L
    private var pendingTapRunnable: Runnable? = null

    private fun isNotificationShadeOrQsActive(): Boolean {
        val lsService = service as? LightspeedAccessibilityService
        if (lsService?.isNotificationShadeActive == true) {
            return true
        }

        val km = service.getSystemService(Context.KEYGUARD_SERVICE) as? KeyguardManager
        val isLocked = km?.isKeyguardLocked == true

        try {
            val root = service.rootInActiveWindow
            if (root != null) {
                val pkg = root.packageName?.toString()
                if (Build.VERSION.SDK_INT < Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                    @Suppress("DEPRECATION")
                    root.recycle()
                }
                if (pkg == "com.android.systemui" && !isLocked) {
                    return true
                }
            }
        } catch (_: Exception) {}

        try {
            val windows = service.windows
            if (!windows.isNullOrEmpty()) {
                for (w in windows) {
                    val title = w.title?.toString() ?: ""
                    val isShade = title.contains("Notification", ignoreCase = true) ||
                            title.contains("Shade", ignoreCase = true) ||
                            title.contains("QuickSettings", ignoreCase = true) ||
                            title.contains("Quick Settings", ignoreCase = true)
                    if (isShade && (w.isActive || w.isFocused)) {
                        return true
                    }
                    if (w.type == AccessibilityWindowInfo.TYPE_SYSTEM && (w.isActive || w.isFocused)) {
                        val root = w.root
                        val pkg = root?.packageName?.toString()
                        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                            @Suppress("DEPRECATION")
                            root?.recycle()
                        }
                        if (pkg == "com.android.systemui" && !isLocked) {
                            return true
                        }
                    }
                }
            }
        } catch (_: Exception) {}

        return false
    }

    // Scrubbing State (Hold-to-Scrub & Long-Sweep Scrub)
    private var isScrubbing = false
    private var activeScrubType = "none"
    private var activeScrubActionKey: String? = null
    private var lastScrubRawX = 0f
    private var lastScrubRawY = 0f
    private var latestRawX = 0f
    private var latestRawY = 0f
    private var scrubAccumulator = 0f
    private var scrubCurrentValue = 0
    private var scrubHudTitle = ""
    private var scrubHudValue = ""

    private fun isScrubAction(action: String): Boolean {
        return action in listOf(
            "system:screen_timeout", "ACTION_SCREEN_TIMEOUT", "screen_timeout",
            "system:volume", "ACTION_VOLUME", "volume", "scrub:volume",
            "system:brightness", "ACTION_BRIGHTNESS", "brightness", "scrub:brightness"
        )
    }

    private fun normalizeScrubAction(action: String): String {
        return when (action) {
            "system:screen_timeout", "ACTION_SCREEN_TIMEOUT", "screen_timeout" -> "system:screen_timeout"
            "system:volume", "ACTION_VOLUME", "volume", "scrub:volume" -> "system:volume"
            "system:brightness", "ACTION_BRIGHTNESS", "brightness", "scrub:brightness" -> "system:brightness"
            else -> action
        }
    }

    private fun initScrubStateAndHud(scrubType: String) {
        val fallbackKey = activeScrubActionKey ?: "pref_macro_action_STATUSBAR"

        when (scrubType) {
            "system:screen_timeout" -> {
                val res = CruiseScrubEngine.executeTimeoutScrub(context, 0)
                scrubCurrentValue = res.currentValue
                scrubHudTitle = res.title
                scrubHudValue = res.value
                CruiseScrubEngine.dispatchScrubHud(context, prefs, scrubType, fallbackKey, res.title, res.value, res.stepIndex, res.totalSteps)
            }
            "system:volume" -> {
                val am = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
                if (am != null) {
                    val res = CruiseScrubEngine.executeVolumeScrub(context, prefs, am, -1, 0)
                    scrubCurrentValue = res.currentValue
                    scrubHudTitle = res.title
                    scrubHudValue = res.value
                    CruiseScrubEngine.dispatchScrubHud(context, prefs, scrubType, fallbackKey, res.title, res.value, res.stepIndex, res.totalSteps)
                }
            }
            "system:brightness" -> {
                val res = CruiseScrubEngine.executeBrightnessScrub(context, prefs, -1, 0)
                if (res != null) {
                    scrubCurrentValue = res.currentValue
                    scrubHudTitle = res.title
                    scrubHudValue = res.value
                    CruiseScrubEngine.dispatchScrubHud(context, prefs, scrubType, fallbackKey, res.title, res.value, res.stepIndex, res.totalSteps)
                }
            }
        }
    }

    private fun handleScrubMotion(rawDx: Float, rawDy: Float) {
        // Axis Lock: Status Bar Sensor Deck ALWAYS uses X-axis.
        val delta = rawDx
        scrubAccumulator += delta

        val density = resources.displayMetrics.density
        val stepThreshold = 26f * density

        if (abs(scrubAccumulator) >= stepThreshold) {
            val steps = (scrubAccumulator / stepThreshold).toInt()
            scrubAccumulator %= stepThreshold

            val fallbackKey = activeScrubActionKey ?: "pref_macro_action_STATUSBAR"

            when (activeScrubType) {
                "system:screen_timeout" -> {
                    val res = CruiseScrubEngine.executeTimeoutScrub(context, steps) {
                        triggerHaptic(20, 140)
                    }
                    scrubCurrentValue = res.currentValue
                    scrubHudValue = res.value
                    CruiseScrubEngine.dispatchScrubHud(context, prefs, activeScrubType, fallbackKey, res.title, res.value, res.stepIndex, res.totalSteps)
                }
                "system:volume" -> {
                    val am = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
                    if (am != null) {
                        val res = CruiseScrubEngine.executeVolumeScrub(context, prefs, am, scrubCurrentValue, steps) {
                            triggerHaptic(18, 110)
                        }
                        scrubCurrentValue = res.currentValue
                        scrubHudValue = res.value
                        CruiseScrubEngine.dispatchScrubHud(context, prefs, activeScrubType, fallbackKey, res.title, res.value, res.stepIndex, res.totalSteps)
                    }
                }
                "system:brightness" -> {
                    val res = CruiseScrubEngine.executeBrightnessScrub(context, prefs, scrubCurrentValue, steps, stepMultiplier = 1.5f) {
                        triggerHaptic(14, 90)
                    }
                    if (res != null) {
                        scrubCurrentValue = res.currentValue
                        scrubHudValue = res.value
                        CruiseScrubEngine.dispatchScrubHud(context, prefs, activeScrubType, fallbackKey, res.title, res.value, res.stepIndex, res.totalSteps)
                    }
                }
            }
        }
    }

    private val holdRunnable = Runnable {
        val gestureKey = if (currentGesture != SensorGesture.NONE) currentGesture.name else if (isSecondTapInSequence) "DOUBLE_TAP" else "TAP"
        val holdActionKey = "pref_macro_action_STATUSBAR_${gestureKey}_HOLD"
        val defaultHold = com.sbf.lightspeed.settings.LightspeedActionRegistry.getDefaultActionForGestureKey(holdActionKey)
        val configuredHold = prefs.getString(holdActionKey, defaultHold) ?: defaultHold
        val (action, resolvedKey) = if (configuredHold != "none") {
            configuredHold to holdActionKey
        } else {
            "none" to null
        }

        if (action != "none") {
            if (isScrubAction(action)) {
                isHoldFired = true
                isScrubbing = true
                activeScrubType = normalizeScrubAction(action)
                activeScrubActionKey = resolvedKey
                lastScrubRawX = latestRawX
                lastScrubRawY = latestRawY
                scrubAccumulator = 0f
                triggerHaptic(35, 180)
                initScrubStateAndHud(activeScrubType)
            } else {
                isHoldFired = true
                triggerHaptic(40, 200)
                performActionByName(action)
            }
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
        if (isScrubbing) {
            isScrubbing = false
            activeScrubActionKey = null
            LightspeedStatusBarOverlay.dismissActionHud(0L)
        }
    }

    private fun triggerHaptic(durationMs: Long = 25, amplitude: Int = 140) {
        LightspeedHapticEngine.vibrate(context, durationMs, amplitude)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        val isSensorEnabled = prefs.getBoolean("pref_statusbar_enabled", false)
        if (!isSensorEnabled) return false
        if (isNotificationShadeOrQsActive()) return false

        val density = resources.displayMetrics.density
        val sensPref = prefs.getInt("pref_statusbar_sensitivity", 25)
        val threshold = (sensPref * 0.5f * density).coerceAtLeast(10f * density)

        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                isDownwardPullFired = false
                latestRawX = event.rawX
                latestRawY = event.rawY
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
                isScrubbing = false
                activeScrubType = "none"
                activeScrubActionKey = null
                scrubAccumulator = 0f
                lastScrubRawX = event.rawX
                lastScrubRawY = event.rawY
                isHorizontalEngaged = false
                currentGesture = SensorGesture.NONE

                val holdDuration = com.sbf.lightspeed.system.LightspeedPreferences.getGestureHoldDurationMs(context)
                uiHandler.postDelayed(holdRunnable, holdDuration)
                return true
            }

            MotionEvent.ACTION_MOVE -> {
                latestRawX = event.rawX
                latestRawY = event.rawY

                if (isScrubbing) {
                    val rawDx = event.rawX - lastScrubRawX
                    val rawDy = event.rawY - lastScrubRawY
                    
                    val isVolume = activeScrubType == "system:volume"
                    val isBrightness = activeScrubType == "system:brightness"
                    val isAudioDockEnabled = prefs.getBoolean(com.sbf.lightspeed.system.LightspeedPreferences.KEY_OMNISCIENT_AUDIO_DOCK_ENABLED, false)
                    val verticalPull = kotlin.math.abs(event.rawY - startY)
                    
                    if (verticalPull > 80f * density && ((isVolume && isAudioDockEnabled) || isBrightness)) {
                        isScrubbing = false
                        activeScrubType = "none"
                        activeScrubActionKey = null
                        com.sbf.lightspeed.LightspeedStatusBarOverlay.dismissActionHud(0L)
                        if (isVolume) {
                            com.sbf.lightspeed.system.OmniscientAudioDockManager.show(service)
                        } else {
                            val intent = android.content.Intent("com.sbf.lightspeed.OMNISCIENT_DISPLAY").apply {
                                setPackage(context.packageName)
                                addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK or android.content.Intent.FLAG_ACTIVITY_MULTIPLE_TASK or android.content.Intent.FLAG_ACTIVITY_NO_ANIMATION)
                            }
                            try { context.startActivity(intent) } catch (e: Exception) {}
                        }
                        return true
                    }

                    handleScrubMotion(rawDx, rawDy)
                    lastScrubRawX = event.rawX
                    lastScrubRawY = event.rawY
                    return true
                }

                val rawDx = event.rawX - startRawX
                val rawDy = event.rawY - startRawY
                val dist = hypot(rawDx, rawDy)

                // Downward Pull: Expand Android notification shade if enabled
                val isSwipeDownNotificationsEnabled = prefs.getBoolean(
                    com.sbf.lightspeed.system.LightspeedPreferences.KEY_STATUSBAR_SWIPE_DOWN_NOTIFICATIONS,
                    false
                )
                if (isSwipeDownNotificationsEnabled && !isDownwardPullFired && rawDy > threshold * 0.5f && rawDy > abs(rawDx) * 0.8f) {
                    if (!isNotificationShadeOrQsActive()) {
                        isDownwardPullFired = true
                        uiHandler.removeCallbacks(holdRunnable)
                        pendingTapRunnable?.let { uiHandler.removeCallbacks(it) }
                        pendingTapRunnable = null
                        isHorizontalEngaged = false
                        currentGesture = SensorGesture.SWIPE_DOWN
                        triggerHaptic(25, 140)
                        service.performGlobalAction(AccessibilityService.GLOBAL_ACTION_NOTIFICATIONS)
                    }
                    return true
                }

                val touchSlop = ViewConfiguration.get(context).scaledTouchSlop.toFloat()
                val cancelDist = maxOf(threshold * 0.8f, touchSlop * 1.5f)
                if (dist > cancelDist && !isHoldFired && !isHorizontalEngaged) {
                    uiHandler.removeCallbacks(holdRunnable)
                }

                if (!isHorizontalEngaged && abs(rawDx) > threshold * 0.7f) {
                    isHorizontalEngaged = true
                    currentGesture = if (rawDx > 0) SensorGesture.SWIPE_RIGHT else SensorGesture.SWIPE_LEFT
                    furthestX = event.x
                    if (!isHoldFired) {
                        uiHandler.removeCallbacks(holdRunnable)
                        val holdDuration = com.sbf.lightspeed.system.LightspeedPreferences.getGestureHoldDurationMs(context)
                        uiHandler.postDelayed(holdRunnable, holdDuration)
                    }
                }

                if (isHorizontalEngaged) {
                    if (currentGesture == SensorGesture.SWIPE_RIGHT && event.x > furthestX) furthestX = event.x
                    if (currentGesture == SensorGesture.SWIPE_LEFT  && event.x < furthestX) furthestX = event.x

                    val reboundThreshold = 18f * density
                    if (currentGesture == SensorGesture.SWIPE_RIGHT && (furthestX - event.x) > reboundThreshold) {
                        currentGesture = SensorGesture.SWIPE_RIGHT_BACK
                    } else if (currentGesture == SensorGesture.SWIPE_LEFT && (event.x - furthestX) > reboundThreshold) {
                        currentGesture = SensorGesture.SWIPE_LEFT_BACK
                    }
                }
                return true
            }

            MotionEvent.ACTION_UP -> {
                uiHandler.removeCallbacks(holdRunnable)
                isDownwardPullFired = false
                if (isScrubbing) {
                    isScrubbing = false
                    activeScrubType = "none"
                    activeScrubActionKey = null
                    isHoldFired = false
                    isSecondTapInSequence = false
                    lastTapTime = 0L
                    currentGesture = SensorGesture.NONE
                    LightspeedStatusBarOverlay.dismissActionHud(1200L)
                    return true
                }

                if (currentGesture == SensorGesture.SWIPE_DOWN) {
                    currentGesture = SensorGesture.NONE
                    isSecondTapInSequence = false
                    lastTapTime = 0L
                    return true
                }

                val dx = event.x - startX
                val dy = event.y - startY
                val dist = hypot(dx, dy)

                if (isHoldFired) {
                    isHoldFired = false
                    isSecondTapInSequence = false
                    lastTapTime = 0L
                    return true
                }

                if (currentGesture != SensorGesture.NONE) {
                    isSecondTapInSequence = false
                    val actionKey = "pref_macro_action_STATUSBAR_${currentGesture.name}"
                    val defaultGestureAction = com.sbf.lightspeed.settings.LightspeedActionRegistry.getDefaultActionForGestureKey(actionKey)
                    val action = prefs.getString(actionKey, defaultGestureAction) ?: defaultGestureAction
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
                isDownwardPullFired = false
                if (isScrubbing) {
                    isScrubbing = false
                    activeScrubType = "none"
                    activeScrubActionKey = null
                    LightspeedStatusBarOverlay.dismissActionHud(600L)
                }
                isHoldFired = false
                isSecondTapInSequence = false
                currentGesture = SensorGesture.NONE
                return true
            }
        }
        return super.onTouchEvent(event)
    }

    private fun handleTapSequence() {
        if (isSecondTapInSequence) {
            isSecondTapInSequence = false
            lastTapTime = 0L
            val defaultDoubleTap = com.sbf.lightspeed.settings.LightspeedActionRegistry.getDefaultActionForGestureKey("pref_macro_action_STATUSBAR_DOUBLE_TAP")
            val doubleTapAction = prefs.getString("pref_macro_action_STATUSBAR_DOUBLE_TAP", defaultDoubleTap) ?: defaultDoubleTap
            if (doubleTapAction != "none") {
                triggerHaptic(30, 180)
                performActionByName(doubleTapAction)
            }
        } else {
            val defaultDoubleTap = com.sbf.lightspeed.settings.LightspeedActionRegistry.getDefaultActionForGestureKey("pref_macro_action_STATUSBAR_DOUBLE_TAP")
            val doubleTapAction = prefs.getString("pref_macro_action_STATUSBAR_DOUBLE_TAP", defaultDoubleTap) ?: defaultDoubleTap
            val defaultDoubleTapHold = com.sbf.lightspeed.settings.LightspeedActionRegistry.getDefaultActionForGestureKey("pref_macro_action_STATUSBAR_DOUBLE_TAP_HOLD")
            val doubleTapHoldAction = prefs.getString("pref_macro_action_STATUSBAR_DOUBLE_TAP_HOLD", defaultDoubleTapHold) ?: defaultDoubleTapHold
            val defaultSingleTap = com.sbf.lightspeed.settings.LightspeedActionRegistry.getDefaultActionForGestureKey("pref_macro_action_STATUSBAR_TAP")
            val singleTapAction = prefs.getString("pref_macro_action_STATUSBAR_TAP", defaultSingleTap) ?: defaultSingleTap

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
            val copy = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                AccessibilityNodeInfo(node)
            } else {
                @Suppress("DEPRECATION")
                AccessibilityNodeInfo.obtain(node)
            }
            list.add(copy)
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
