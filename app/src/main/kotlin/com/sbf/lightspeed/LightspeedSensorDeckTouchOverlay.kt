package com.sbf.lightspeed

import android.accessibilityservice.AccessibilityService
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
    private var currentGesture = "NONE"
    private var isHorizontalEngaged = false
    private var lastTapTime = 0L
    private var pendingTapRunnable: Runnable? = null

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
        val fallbackKey = activeScrubActionKey ?: "pref_macro_action_STATUSBAR_SCRUBBING"
        val hudStyle = com.sbf.lightspeed.system.LightspeedPreferences.resolveHudStyle(prefs, fallbackKey, scrubType)

        when (scrubType) {
            "system:screen_timeout" -> {
                scrubCurrentValue = LightspeedTimeoutEngine.getCurrentTimeoutIndex(context)
                scrubHudTitle = "SHIP GOES DARK IN"
                scrubHudValue = LightspeedTimeoutEngine.TIMEOUT_STEPS[scrubCurrentValue].second
                LightspeedStatusBarOverlay.showActionHud(
                    title = scrubHudTitle,
                    value = scrubHudValue,
                    stepIndex = scrubCurrentValue,
                    totalSteps = LightspeedTimeoutEngine.TIMEOUT_STEPS.size,
                    durationMs = 0L,
                    style = hudStyle
                )
            }
            "system:volume" -> {
                val am = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
                scrubCurrentValue = am?.getStreamVolume(AudioManager.STREAM_MUSIC) ?: 0
                val maxVol = am?.getStreamMaxVolume(AudioManager.STREAM_MUSIC) ?: 15
                scrubHudTitle = "MEDIA VOLUME"
                scrubHudValue = "$scrubCurrentValue / $maxVol"
                if (prefs.getBoolean(com.sbf.lightspeed.system.LightspeedPreferences.KEY_HUD_VOLUME_ENABLED, true)) {
                    LightspeedStatusBarOverlay.showActionHud(
                        title = scrubHudTitle,
                        value = scrubHudValue,
                        stepIndex = scrubCurrentValue,
                        totalSteps = maxVol,
                        durationMs = 0L,
                        style = hudStyle
                    )
                }
            }
            "system:brightness" -> {
                scrubCurrentValue = try {
                    Settings.System.getInt(context.contentResolver, Settings.System.SCREEN_BRIGHTNESS)
                } catch (_: Exception) { 128 }
                val brightResolution = prefs.getInt(com.sbf.lightspeed.system.LightspeedPreferences.KEY_BRIGHTNESS_SCRUB_RESOLUTION, 32).coerceIn(10, 254)
                scrubHudTitle = "BRIGHTNESS"
                scrubHudValue = "${(scrubCurrentValue * 100 / 255)}%"
                if (prefs.getBoolean(com.sbf.lightspeed.system.LightspeedPreferences.KEY_HUD_BRIGHTNESS_ENABLED, true)) {
                    LightspeedStatusBarOverlay.showActionHud(
                        title = scrubHudTitle,
                        value = scrubHudValue,
                        stepIndex = (scrubCurrentValue * brightResolution / 255),
                        totalSteps = brightResolution,
                        durationMs = 0L,
                        style = hudStyle
                    )
                }
            }
        }
    }

    private fun handleScrubMotion(rawDx: Float, rawDy: Float) {
        // Allow scrubbing both sideways (rawDx: right is +) and up/down (-rawDy: up is +) based on dominant movement
        val delta = if (abs(rawDx) >= abs(rawDy)) rawDx else -rawDy
        scrubAccumulator += delta

        val density = resources.displayMetrics.density
        val stepThreshold = 26f * density

        if (abs(scrubAccumulator) >= stepThreshold) {
            val steps = (scrubAccumulator / stepThreshold).toInt()
            scrubAccumulator %= stepThreshold

            val fallbackKey = activeScrubActionKey ?: "pref_macro_action_STATUSBAR_SCRUBBING"
            val hudStyle = com.sbf.lightspeed.system.LightspeedPreferences.resolveHudStyle(prefs, fallbackKey, activeScrubType)

            when (activeScrubType) {
                "system:screen_timeout" -> {
                    val curIdx = LightspeedTimeoutEngine.getCurrentTimeoutIndex(context)
                    val targetIndex = (curIdx + steps).coerceIn(0, LightspeedTimeoutEngine.TIMEOUT_STEPS.lastIndex)
                    if (targetIndex != curIdx) {
                        val (_, label) = LightspeedTimeoutEngine.setStepIndex(context, targetIndex)
                        scrubCurrentValue = targetIndex
                        scrubHudValue = label
                        triggerHaptic(20, 140)
                    }
                    LightspeedStatusBarOverlay.showActionHud(
                        title = "SHIP GOES DARK IN",
                        value = LightspeedTimeoutEngine.TIMEOUT_STEPS[scrubCurrentValue].second,
                        stepIndex = scrubCurrentValue,
                        totalSteps = LightspeedTimeoutEngine.TIMEOUT_STEPS.size,
                        durationMs = 0L,
                        style = hudStyle
                    )
                }
                "system:volume" -> {
                    val am = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
                    if (am != null) {
                        val maxVol = am.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
                        val curVol = am.getStreamVolume(AudioManager.STREAM_MUSIC)
                        val volStep = prefs.getInt(com.sbf.lightspeed.system.LightspeedPreferences.KEY_VOLUME_SCRUB_STEP, 1)
                        val targetVol = (curVol + (steps * volStep)).coerceIn(0, maxVol)
                        val showNativeUi = prefs.getBoolean(com.sbf.lightspeed.system.LightspeedPreferences.KEY_VOLUME_SHOW_NATIVE_SLIDER, false)
                        val flags = if (showNativeUi) AudioManager.FLAG_SHOW_UI else 0
                        if (targetVol != curVol) {
                            am.setStreamVolume(AudioManager.STREAM_MUSIC, targetVol, flags)
                            triggerHaptic(18, 110)
                        }
                        scrubCurrentValue = targetVol
                        scrubHudValue = "$targetVol / $maxVol"
                        if (prefs.getBoolean(com.sbf.lightspeed.system.LightspeedPreferences.KEY_HUD_VOLUME_ENABLED, true)) {
                            LightspeedStatusBarOverlay.showActionHud(
                                title = "MEDIA VOLUME",
                                value = scrubHudValue,
                                stepIndex = targetVol,
                                totalSteps = maxVol,
                                durationMs = 0L,
                                style = hudStyle
                            )
                        }
                    }
                }
                "system:brightness" -> {
                    if (Settings.System.canWrite(context)) {
                        val curBrightness = try {
                            Settings.System.getInt(context.contentResolver, Settings.System.SCREEN_BRIGHTNESS)
                        } catch (_: Exception) { 128 }
                        val brightResolution = prefs.getInt(com.sbf.lightspeed.system.LightspeedPreferences.KEY_BRIGHTNESS_SCRUB_RESOLUTION, 32).coerceIn(10, 254)
                        val brightStep = prefs.getInt(com.sbf.lightspeed.system.LightspeedPreferences.KEY_BRIGHTNESS_SCRUB_STEP, (255f / brightResolution).roundToInt().coerceIn(1, 32))
                        val targetBrightness = (curBrightness + (steps * brightStep * 1.5f).toInt()).coerceIn(0, 255)
                        if (abs(targetBrightness - curBrightness) >= 1) {
                            try {
                                Settings.System.putInt(context.contentResolver, Settings.System.SCREEN_BRIGHTNESS, targetBrightness)
                                triggerHaptic(14, 90)
                            } catch (_: Exception) {}
                        }
                        scrubCurrentValue = targetBrightness
                        scrubHudValue = "${(targetBrightness * 100 / 255)}%"
                        if (prefs.getBoolean(com.sbf.lightspeed.system.LightspeedPreferences.KEY_HUD_BRIGHTNESS_ENABLED, true)) {
                            LightspeedStatusBarOverlay.showActionHud(
                                title = "BRIGHTNESS",
                                value = scrubHudValue,
                                stepIndex = (targetBrightness * brightResolution / 255),
                                totalSteps = brightResolution,
                                durationMs = 0L,
                                style = hudStyle
                            )
                        }
                    } else {
                        LightspeedTimeoutEngine.requestWriteSettingsPermission(context)
                    }
                }
            }
        }
    }

    private val holdRunnable = Runnable {
        val gestureKey = if (currentGesture != "NONE") currentGesture else if (isSecondTapInSequence) "DOUBLE_TAP" else "TAP"
        val holdActionKey = "pref_macro_action_STATUSBAR_${gestureKey}_HOLD"
        val configuredHold = prefs.getString(holdActionKey, "none") ?: "none"
        val (action, resolvedKey) = if (configuredHold != "none") {
            configuredHold to holdActionKey
        } else {
            val baseAction = prefs.getString("pref_macro_action_STATUSBAR_$gestureKey", "none") ?: "none"
            if (isScrubAction(baseAction)) baseAction to "pref_macro_action_STATUSBAR_$gestureKey" else "none" to null
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
        val isSensorEnabled = prefs.getBoolean("pref_statusbar_enabled", true)
        if (!isSensorEnabled) return false

        val density = resources.displayMetrics.density
        val sensPref = prefs.getInt("pref_statusbar_sensitivity", 40)
        val threshold = (sensPref * 0.5f * density).coerceAtLeast(10f * density)

        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
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
                currentGesture = "NONE"

                uiHandler.postDelayed(holdRunnable, 360L)
                return true
            }

            MotionEvent.ACTION_MOVE -> {
                latestRawX = event.rawX
                latestRawY = event.rawY

                if (isScrubbing) {
                    val rawDx = event.rawX - lastScrubRawX
                    val rawDy = event.rawY - lastScrubRawY
                    handleScrubMotion(rawDx, rawDy)
                    lastScrubRawX = event.rawX
                    lastScrubRawY = event.rawY
                    return true
                }

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
                    // Check Long Sweep Scrubbing (continuous swipe past threshold)
                    val assignedScrub = prefs.getString("pref_macro_action_STATUSBAR_SCRUBBING", "none")
                    val swipeAction = prefs.getString("pref_macro_action_STATUSBAR_$currentGesture", "none")
                    val (effectiveScrub, resolvedKey) = when {
                        assignedScrub != null && assignedScrub != "none" && isScrubAction(assignedScrub) -> assignedScrub to "pref_macro_action_STATUSBAR_SCRUBBING"
                        isScrubAction(swipeAction ?: "") -> (swipeAction ?: "") to "pref_macro_action_STATUSBAR_$currentGesture"
                        else -> null to null
                    }
                    if (effectiveScrub != null && abs(rawDx) > threshold * 1.8f && !isScrubbing) {
                        isScrubbing = true
                        isHoldFired = true
                        uiHandler.removeCallbacks(holdRunnable)
                        activeScrubType = normalizeScrubAction(effectiveScrub)
                        activeScrubActionKey = resolvedKey
                        lastScrubRawX = event.rawX
                        lastScrubRawY = event.rawY
                        scrubAccumulator = 0f
                        triggerHaptic(35, 180)
                        initScrubStateAndHud(activeScrubType)
                        return true
                    }

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
                if (isScrubbing) {
                    isScrubbing = false
                    activeScrubType = "none"
                    activeScrubActionKey = null
                    isHoldFired = false
                    isSecondTapInSequence = false
                    lastTapTime = 0L
                    currentGesture = "NONE"
                    LightspeedStatusBarOverlay.dismissActionHud(1200L)
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
                if (isScrubbing) {
                    isScrubbing = false
                    activeScrubType = "none"
                    activeScrubActionKey = null
                    LightspeedStatusBarOverlay.dismissActionHud(600L)
                }
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
