package com.sbf.lightspeed
import com.sbf.lightspeed.system.LightspeedPreferences
import com.sbf.lightspeed.system.OverlayGlowDelegate

import android.accessibilityservice.AccessibilityService
import android.animation.ValueAnimator
import android.content.Context
import android.content.SharedPreferences
import android.view.animation.DecelerateInterpolator
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.media.AudioManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.WindowManager
import android.view.accessibility.AccessibilityNodeInfo
import com.sbf.lightspeed.system.ActionDispatcher
import com.sbf.lightspeed.system.LightspeedHapticEngine
import com.sbf.lightspeed.system.LightspeedTimeoutEngine
import com.sbf.lightspeed.system.defaultPrefs
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.roundToInt

class LightspeedLeftWingOverlay(
    context: Context,
    private val service: AccessibilityService
) : View(context) {

    internal val prefs = service.defaultPrefs()

    internal val topTouchBounds = RectF()
    internal val centerTouchBounds = RectF()
    internal val bottomTouchBounds = RectF()

    private var topHeightPx = 0f
    private var centerHeightPx = 0f
    private var bottomHeightPx = 0f
    private var topTouchWidthPx = 0f
    private var centerTouchWidthPx = 0f
    internal var centerVisualWidthPx = 6f
    private var bottomTouchWidthPx = 0f
    private var centerYOffsetPx = 0f

    private val uiHandler = Handler(Looper.getMainLooper())
    private var pendingTapRunnable: Runnable? = null
    private var lastTapTime = 0L

    private val glowDelegate = OverlayGlowDelegate(this)
    val glowFraction: Float get() = glowDelegate.glowFraction

    fun triggerGlow(durationMs: Long = -1L) {
        glowDelegate.triggerGlow(context, durationMs)
    }

    private val prefListener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
        if (key != null && (key.startsWith("pref_sidebar_") || key.startsWith("pref_section_") || key.startsWith("pref_macro_action_") || key.startsWith("pref_symmetry_") || key.startsWith("pref_deflector_") || key.startsWith("pref_cockpit_"))) {
            post {
                updateMetricsDimensions()
                invalidate()
            }
        }
    }

    init {
        isClickable = true
        isFocusable = false
        prefs.registerOnSharedPreferenceChangeListener(prefListener)
        updateMetricsDimensions()
    }

    override fun onConfigurationChanged(newConfig: android.content.res.Configuration?) {
        super.onConfigurationChanged(newConfig)
        post {
            updateMetricsDimensions()
            invalidate()
        }
    }

    override fun onDetachedFromWindow() {
        glowDelegate.cancel()
        super.onDetachedFromWindow()
        prefs.unregisterOnSharedPreferenceChangeListener(prefListener)
        pendingTapRunnable?.let { uiHandler.removeCallbacks(it) }
        uiHandler.removeCallbacks(holdRunnable)
    }

    fun updateMetricsDimensions() {
        val wm = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
        val lp = layoutParams as? WindowManager.LayoutParams ?: return

        val displayMetrics = resources.displayMetrics
        val screenH = displayMetrics.heightPixels.toFloat()
        val density = displayMetrics.density
        val isLandscape = resources.configuration.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE
        val landscapeMode = prefs.getString(com.sbf.lightspeed.system.LightspeedPreferences.KEY_DEFLECTOR_LANDSCAPE_MODE, com.sbf.lightspeed.system.LightspeedPreferences.DEFLECTOR_LANDSCAPE_MODE_CUSTOM) ?: com.sbf.lightspeed.system.LightspeedPreferences.DEFLECTOR_LANDSCAPE_MODE_CUSTOM
        val isCustomLandscape = isLandscape && landscapeMode == com.sbf.lightspeed.system.LightspeedPreferences.DEFLECTOR_LANDSCAPE_MODE_CUSTOM
        val landscapeScale = if (!isLandscape) {
            1.0f
        } else when (landscapeMode) {
            com.sbf.lightspeed.system.LightspeedPreferences.DEFLECTOR_LANDSCAPE_MODE_FIXED -> 1.0f
            else -> 0.45f
        }
        val landscapeYScale = if (!isLandscape) {
            1.0f
        } else when (landscapeMode) {
            com.sbf.lightspeed.system.LightspeedPreferences.DEFLECTOR_LANDSCAPE_MODE_FIXED -> 1.0f
            else -> 0.5f
        }

        val geomMode = prefs.getString("pref_symmetry_geometry_mode", "independent") ?: "independent"
        val isMirroringRight = geomMode == "right"

        val centerPrefix = if (isMirroringRight) "pref_sidebar_center" else "pref_sidebar_left_center"
        val topPrefix = if (isMirroringRight) "pref_sidebar_top" else "pref_sidebar_left_top"
        val bottomPrefix = if (isMirroringRight) "pref_sidebar_bottom" else "pref_sidebar_left_bottom"

        val defaultCenterH = 70
        val defaultCenterY = 0
        val defaultTopH = 220
        val defaultBottomH = 250

        if (isCustomLandscape) {
            val defCenterH = (prefs.getInt("${centerPrefix}_height", defaultCenterH) * 0.45f).toInt().coerceAtLeast(30)
            val defCenterY = 0
            val defTopH = (prefs.getInt("${topPrefix}_height", defaultTopH) * 0.45f).toInt().coerceAtLeast(30)
            val defBottomH = (prefs.getInt("${bottomPrefix}_height", defaultBottomH) * 0.45f).toInt().coerceAtLeast(30)

            centerHeightPx = prefs.getInt("${centerPrefix}_height_landscape", defCenterH).toFloat() * density
            centerYOffsetPx = prefs.getInt("${centerPrefix}_y_offset_landscape", defCenterY).toFloat() * density
            topHeightPx = prefs.getInt("${topPrefix}_height_landscape", defTopH).toFloat() * density
            bottomHeightPx = prefs.getInt("${bottomPrefix}_height_landscape", defBottomH).toFloat() * density
        } else {
            centerHeightPx = prefs.getInt("${centerPrefix}_height", defaultCenterH).toFloat() * density * landscapeScale
            centerYOffsetPx = prefs.getInt("${centerPrefix}_y_offset", defaultCenterY).toFloat() * density * landscapeYScale
            topHeightPx = prefs.getInt("${topPrefix}_height", defaultTopH).toFloat() * density * landscapeScale
            bottomHeightPx = prefs.getInt("${bottomPrefix}_height", defaultBottomH).toFloat() * density * landscapeScale
        }

        centerTouchWidthPx = prefs.getInt("${centerPrefix}_touch_width", 10).toFloat() * density
        centerVisualWidthPx = prefs.getInt("${centerPrefix}_visual_width", 0).toFloat() * density

        topTouchWidthPx = prefs.getInt("${topPrefix}_touch_width", 10).toFloat() * density
        bottomTouchWidthPx = prefs.getInt("${bottomPrefix}_touch_width", 10).toFloat() * density

        val centerY = (screenH / 2f) + centerYOffsetPx
        val centerTop = centerY - (centerHeightPx / 2f)
        val centerBottom = centerY + (centerHeightPx / 2f)

        val topLimit = (centerTop - topHeightPx).coerceAtLeast(0f)
        val bottomLimit = (centerBottom + bottomHeightPx).coerceAtMost(screenH)

        val maxTouchW = maxOf(centerTouchWidthPx, topTouchWidthPx, bottomTouchWidthPx)
        val winHeight = (bottomLimit - topLimit).toInt().coerceAtLeast(100)
        val winWidth = maxTouchW.toInt().coerceAtLeast(1)
        val winY = topLimit.toInt()

        topTouchBounds.set(0f, 0f, topTouchWidthPx, topHeightPx)
        centerTouchBounds.set(0f, topHeightPx, centerTouchWidthPx, topHeightPx + centerHeightPx)
        bottomTouchBounds.set(0f, topHeightPx + centerHeightPx, bottomTouchWidthPx, topHeightPx + centerHeightPx + bottomHeightPx)

        if (lp.height != winHeight || lp.width != winWidth || lp.y != winY || lp.gravity != (Gravity.TOP or Gravity.START)) {
            lp.gravity = Gravity.TOP or Gravity.START
            lp.x = 0
            lp.y = winY
            lp.width = winWidth
            lp.height = winHeight
            try {
                wm.updateViewLayout(this, lp)
            } catch (_: Exception) {}
        }
    }

    internal var isCurrentlyTouched = false
    private var startRawX = 0f
    private var startRawY = 0f
    private var lastTouchRawX = 0f
    private var lastTouchRawY = 0f
    private var highestXReached = 0f
    private var lowestXReached = 0f
    private var highestYReached = 0f
    private var lowestYReached = 0f
    private var isScrubbing = false
    private var isHoldFired = false
    private var currentGesture = "NONE"
    internal var activeZoneKey = "LEFT_CENTER"
    private var scrubType = "none"

    private var initialScrubValue = 0
    private var initialScrubTouchY = 0f
    private var currentRawY = 0f
    private var activeScrubActionKey: String? = null
    private var hudTitle = ""
    private var hudValue = ""

    private val holdRunnable = Runnable {
        if (!isScrubbing) {
            val gestureKey = if (currentGesture == "NONE") "TAP" else currentGesture
            val (action, resolvedKey) = getEffectiveActionWithKey(activeZoneKey, gestureKey, true)
            if (action != "none") {
                isHoldFired = true
                if (action == "system:volume" || action == "system:brightness" || action == "system:screen_timeout" || action == "scrub:volume" || action == "scrub:brightness") {
                    isScrubbing = true
                    scrubType = action
                    activeScrubActionKey = resolvedKey
                    initialScrubTouchY = currentRawY
                    triggerHaptic(35, 180)
                    initScrubHud()
                    invalidate()
                } else {
                    triggerHaptic(40, 200)
                    performActionByName(action)
                }
            }
        }
    }

    private fun triggerHaptic(durationMs: Long = 25, amplitude: Int = 140) {
        LightspeedHapticEngine.vibrate(context, durationMs, amplitude)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        val x = event.x
        val y = event.y
        val rawX = event.rawX
        val rawY = event.rawY
        currentRawY = rawY

        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                startRawX = rawX
                startRawY = rawY
                lastTouchRawX = rawX
                lastTouchRawY = rawY
                highestXReached = rawX
                lowestXReached = rawX
                highestYReached = rawY
                lowestYReached = rawY
                isCurrentlyTouched = true
                invalidate()
                isScrubbing = false
                isHoldFired = false
                currentGesture = "NONE"

                activeZoneKey = when {
                    topTouchBounds.contains(x, y) -> "LEFT_TOP"
                    bottomTouchBounds.contains(x, y) -> "LEFT_BOTTOM"
                    else -> "LEFT_CENTER"
                }

                if (activeZoneKey == "LEFT_CENTER") {
                    (service as? LightspeedAccessibilityService)?.startCruiseFromLeft(rawX, rawY)
                    return true
                }

                val effectiveScrubZone = if (com.sbf.lightspeed.system.LightspeedPreferences.isScrubRegionsLinked(context, isLeft = true)) "LEFT_TOP" else activeZoneKey
                val scrubAction = prefs.getString("pref_macro_action_${effectiveScrubZone}_SCRUBBING", "none") ?: "none"
                scrubType = scrubAction

                uiHandler.removeCallbacks(holdRunnable)
                val holdDuration = com.sbf.lightspeed.system.LightspeedPreferences.getGestureHoldDurationMs(context)
                uiHandler.postDelayed(holdRunnable, holdDuration)
                return true
            }

            MotionEvent.ACTION_MOVE -> {
                if (activeZoneKey == "LEFT_CENTER") {
                    (service as? LightspeedAccessibilityService)?.forwardTouchEventToCruise(isLeft = true, event = event)
                    return true
                }

                val density = resources.displayMetrics.density
                val deltaX = rawX - startRawX
                val deltaY = rawY - startRawY

                if (rawX > highestXReached) highestXReached = rawX
                if (rawX < lowestXReached) lowestXReached = rawX
                if (rawY > highestYReached) highestYReached = rawY
                if (rawY < lowestYReached) lowestYReached = rawY

                val screenW = resources.displayMetrics.widthPixels.toFloat()
                val isLandscape = resources.configuration.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE
                val longSwipeThresholdDp = com.sbf.lightspeed.system.LightspeedPreferences.getDeflectorLongSwipeThresholdDp(context, isLandscape)
                val thresholdX_Scrub = (longSwipeThresholdDp.toFloat() * density).coerceAtMost(screenW * 0.45f)

                if (isScrubbing) {
                    uiHandler.removeCallbacks(holdRunnable)
                    val dy = rawY - initialScrubTouchY
                    handleScrubMotion(dy)
                    lastTouchRawX = rawX
                    lastTouchRawY = rawY
                    return true
                }

                val previousGesture = currentGesture

                // 1. Snappy Primary Direction Detection with 22dp threshold matching starboard
                if (currentGesture == "NONE") {
                    if (deltaX > (22f * density) && deltaX > abs(deltaY)) {
                        currentGesture = "SWIPE_RIGHT"
                    } else if (deltaY < (-25f * density) && abs(deltaY) > deltaX) {
                        currentGesture = "SWIPE_UP"
                    } else if (deltaY > (25f * density) && abs(deltaY) > deltaX) {
                        currentGesture = "SWIPE_DOWN"
                    }
                }

                // 2. Fluid 2-Step Compound & Rebound State Transitions
                when (currentGesture) {
                    "SWIPE_RIGHT" -> {
                        val effectiveScrubZone = if (com.sbf.lightspeed.system.LightspeedPreferences.isScrubRegionsLinked(context, isLeft = true)) "LEFT_TOP" else activeZoneKey
                        val assignedScrub = prefs.getString("pref_macro_action_${effectiveScrubZone}_SCRUBBING", "none") ?: "none"

                        if (assignedScrub != "none" && deltaX > thresholdX_Scrub) {
                            currentGesture = "SCRUBBING"
                            isScrubbing = true
                            scrubType = assignedScrub
                            activeScrubActionKey = "pref_macro_action_${effectiveScrubZone}_SCRUBBING"
                            uiHandler.removeCallbacks(holdRunnable)
                            initialScrubTouchY = rawY
                            triggerHaptic(65, 255)
                            initScrubHud()
                            invalidate()
                            return true
                        } else if (deltaY < (-25f * density)) {
                            currentGesture = "SWIPE_RIGHT_UP"
                        } else if (deltaY > (25f * density)) {
                            currentGesture = "SWIPE_RIGHT_DOWN"
                        } else {
                            val returnLeftDistance = highestXReached - rawX
                            if (returnLeftDistance > (18f * density)) {
                                currentGesture = "SWIPE_RIGHT_BACK"
                            }
                        }
                    }
                    "SWIPE_UP" -> {
                        if ((rawY - lowestYReached) > (22f * density)) {
                            currentGesture = "SWIPE_UP_DOWN"
                        } else if (deltaX > (25f * density)) {
                            currentGesture = "SWIPE_UP_RIGHT"
                        }
                    }
                    "SWIPE_DOWN" -> {
                        if ((highestYReached - rawY) > (22f * density)) {
                            currentGesture = "SWIPE_DOWN_UP"
                        } else if (deltaX > (25f * density)) {
                            currentGesture = "SWIPE_DOWN_RIGHT"
                        }
                    }
                }

                if (currentGesture != "NONE" && currentGesture != previousGesture) {
                    if (previousGesture == "NONE" && com.sbf.lightspeed.system.LightspeedPreferences.isDeflectorGlowOnGestureStep(context)) {
                        triggerGlow(500L)
                    }
                    triggerHaptic(18, 100)
                    if (!isHoldFired) {
                        uiHandler.removeCallbacks(holdRunnable)
                        val holdDuration = com.sbf.lightspeed.system.LightspeedPreferences.getGestureHoldDurationMs(context)
                        uiHandler.postDelayed(holdRunnable, holdDuration)
                    }
                }

                lastTouchRawX = rawX
                lastTouchRawY = rawY
                return true
            }

            MotionEvent.ACTION_UP -> {
                isCurrentlyTouched = false
                invalidate()
                uiHandler.removeCallbacks(holdRunnable)
                if (activeZoneKey == "LEFT_CENTER") {
                    (service as? LightspeedAccessibilityService)?.forwardTouchEventToCruise(isLeft = true, event = event)
                    return true
                }

                val density = resources.displayMetrics.density
                val maxExcursionX = highestXReached - startRawX
                val maxExcursionUp = startRawY - lowestYReached
                val maxExcursionDown = highestYReached - startRawY
                val maxExcursion = maxOf(maxExcursionX, maxExcursionUp, maxExcursionDown)

                if (isScrubbing || currentGesture == "SCRUBBING") {
                    isScrubbing = false
                    currentGesture = "NONE"
                    activeScrubActionKey = null
                    hudTitle = ""
                    hudValue = ""
                    LightspeedStatusBarOverlay.dismissActionHud(1200L)
                    invalidate()
                    return true
                }

                if (!isHoldFired) {
                    val isReturning = currentGesture == "SWIPE_RIGHT_BACK" || currentGesture == "SWIPE_UP_DOWN" || currentGesture == "SWIPE_DOWN_UP"

                    if (isReturning || (currentGesture != "NONE" && maxExcursion >= (18f * density))) {
                        val action = getEffectiveAction(activeZoneKey, currentGesture, false)
                        if (action != "none") {
                            triggerHaptic(25, 160)
                            performActionByName(action)
                        }
                    } else if (maxExcursion < (18f * density)) {
                        handleTap()
                    }
                }
                return true
            }

            MotionEvent.ACTION_CANCEL -> {
                isCurrentlyTouched = false
                invalidate()
                uiHandler.removeCallbacks(holdRunnable)
                if (activeZoneKey == "LEFT_CENTER") {
                    (service as? LightspeedAccessibilityService)?.forwardTouchEventToCruise(isLeft = true, event = event)
                    return true
                }
                if (isScrubbing || currentGesture == "SCRUBBING") {
                    LightspeedStatusBarOverlay.dismissActionHud(600L)
                }
                isScrubbing = false
                currentGesture = "NONE"
                activeScrubActionKey = null
                hudTitle = ""
                hudValue = ""
                invalidate()
                return true
            }
        }
        return true
    }

    private fun handleTap() {
        if (activeZoneKey == "LEFT_CENTER") {
            triggerHaptic(35, 180)
            (service as? LightspeedAccessibilityService)?.openCockpitFromLeft()
            return
        }
        val now = System.currentTimeMillis()
        if (now - lastTapTime < 320) {
            pendingTapRunnable?.let { uiHandler.removeCallbacks(it) }
            pendingTapRunnable = null
            lastTapTime = 0
            triggerHaptic(30, 180)
            val action = getEffectiveAction(activeZoneKey, "DOUBLE_TAP", false)
            if (action != "none") {
                performActionByName(action)
            }
        } else {
            lastTapTime = now
            pendingTapRunnable = Runnable {
                val action = getEffectiveAction(activeZoneKey, "TAP", false)
                if (action != "none") {
                    triggerHaptic(20, 120)
                    performActionByName(action)
                } else {
                    triggerHaptic(25, 120)
                    (service as? LightspeedAccessibilityService)?.triggerDeflectorsGlow() ?: triggerGlow()
                }
            }
            uiHandler.postDelayed(pendingTapRunnable!!, 320)
        }
    }

    private fun initScrubHud() {
        when (scrubType) {
            "system:volume", "scrub:volume" -> {
                val am = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager ?: return
                val res = CruiseScrubEngine.executeVolumeScrub(context, prefs, am, -1, 0)
                initialScrubValue = res.currentValue
                hudTitle = res.title
                hudValue = res.value
                dispatchScrubHud(res.title, res.value, res.stepIndex, res.totalSteps)
            }
            "system:brightness", "scrub:brightness" -> {
                val res = CruiseScrubEngine.executeBrightnessScrub(context, prefs, -1, 0)
                if (res != null) {
                    initialScrubValue = res.currentValue
                    hudTitle = res.title
                    hudValue = res.value
                    dispatchScrubHud(res.title, res.value, res.stepIndex, res.totalSteps)
                }
            }
            "system:screen_timeout" -> {
                val res = CruiseScrubEngine.executeTimeoutScrub(context, 0)
                initialScrubValue = res.currentValue
                hudTitle = res.title
                hudValue = res.value
                dispatchScrubHud(res.title, res.value, res.stepIndex, res.totalSteps)
            }
        }
    }

    private fun dispatchScrubHud(title: String, value: String, stepIndex: Int, totalSteps: Int) {
        val effectiveScrubZone = if (com.sbf.lightspeed.system.LightspeedPreferences.isScrubRegionsLinked(context, isLeft = true)) "LEFT_TOP" else activeZoneKey
        val fallbackKey = activeScrubActionKey ?: "pref_macro_action_${effectiveScrubZone}_SCRUBBING"
        CruiseScrubEngine.dispatchScrubHud(
            context = context,
            prefs = prefs,
            scrubType = scrubType,
            actionKey = fallbackKey,
            title = title,
            value = value,
            stepIndex = stepIndex,
            totalSteps = totalSteps
        )
    }

    private fun handleScrubMotion(dy: Float) {
        val density = resources.displayMetrics.density
        val stepDistance = 28f * density
        when (scrubType) {
            "system:volume", "scrub:volume" -> {
                val am = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager ?: return
                val volResolution = prefs.getInt(com.sbf.lightspeed.system.LightspeedPreferences.KEY_VOLUME_SCRUB_RESOLUTION, 100).coerceIn(5, 100)
                val stepOffset = ((-dy / (stepDistance * 1.2f)) * (100f / volResolution.toFloat())).toInt()
                val res = CruiseScrubEngine.executeVolumeScrub(context, prefs, am, initialScrubValue, stepOffset) {
                    triggerHaptic(18, 110)
                }
                hudTitle = res.title
                hudValue = res.value
                dispatchScrubHud(res.title, res.value, res.stepIndex, res.totalSteps)
                invalidate()
            }
            "system:brightness", "scrub:brightness" -> {
                val brightResolution = prefs.getInt(com.sbf.lightspeed.system.LightspeedPreferences.KEY_BRIGHTNESS_SCRUB_RESOLUTION, 32).coerceIn(10, 254)
                val brightStep = prefs.getInt(com.sbf.lightspeed.system.LightspeedPreferences.KEY_BRIGHTNESS_SCRUB_STEP, (255f / brightResolution).roundToInt().coerceIn(1, 32))
                val stepOffset = ((-dy / (stepDistance * 1.2f)) * (brightStep * 1.5f)).toInt()
                val res = CruiseScrubEngine.executeBrightnessScrub(context, prefs, initialScrubValue, stepOffset, stepMultiplier = 1f) {
                    triggerHaptic(14, 90)
                }
                if (res != null) {
                    hudTitle = res.title
                    hudValue = res.value
                    dispatchScrubHud(res.title, res.value, res.stepIndex, res.totalSteps)
                    invalidate()
                }
            }
            "system:screen_timeout" -> {
                val stepOffset = (-dy / (stepDistance * 1.5f)).toInt()
                val currentIdx = LightspeedTimeoutEngine.getCurrentTimeoutIndex(context)
                val targetIndex = (initialScrubValue + stepOffset).coerceIn(0, LightspeedTimeoutEngine.TIMEOUT_STEPS.lastIndex)
                if (targetIndex != currentIdx) {
                    val res = CruiseScrubEngine.executeTimeoutScrub(context, targetIndex - currentIdx) {
                        triggerHaptic(22, 140)
                    }
                    hudTitle = res.title
                    hudValue = res.value
                    dispatchScrubHud(res.title, res.value, res.stepIndex, res.totalSteps)
                    invalidate()
                }
            }
        }
    }

    override fun onDraw(canvas: Canvas) {
        handleDraw(canvas) { super.onDraw(canvas) }
    }

    private fun getEffectiveActionWithKey(zoneKey: String, gesture: String, isHold: Boolean): Pair<String, String> {
        val isFlankUnified = prefs.getBoolean("pref_sidebar_left_link_flank_actions", true)
        val gestMode = prefs.getString("pref_symmetry_gesture_mode", "independent") ?: "independent"
        val isMirroringRight = gestMode == "right"

        if (isMirroringRight) {
            val isRightUnified = prefs.getBoolean("pref_sidebar_right_link_flank_actions", true)
            val rightZone = when (zoneKey) {
                "LEFT_TOP", "LEFT_BOTTOM" -> if (isRightUnified) "UNIFIED" else if (zoneKey == "LEFT_TOP") "TOP" else "BOTTOM"
                else -> "CENTER"
            }
            val rightGesture = when (gesture) {
                "SWIPE_RIGHT" -> "SWIPE_LEFT"
                "SWIPE_RIGHT_UP" -> "SWIPE_LEFT_UP"
                "SWIPE_RIGHT_DOWN" -> "SWIPE_LEFT_DOWN"
                "SWIPE_RIGHT_BACK" -> "SWIPE_LEFT_BACK"
                "SWIPE_UP_RIGHT" -> "SWIPE_UP_LEFT"
                "SWIPE_DOWN_RIGHT" -> "SWIPE_DOWN_LEFT"
                else -> gesture
            }
            if (rightZone == "CENTER" && rightGesture == "SWIPE_LEFT") {
                return "lightspeed:cockpit_hangar" to "lightspeed:cockpit_hangar"
            }
            val rightGestureKey = if (isHold) "${rightGesture}_HOLD" else rightGesture
            val isRightGestureUnified = com.sbf.lightspeed.system.LightspeedPreferences.isGestureUnified(service, isLeft = false, rightGestureKey)
            val rightEffectiveZone = if (isRightUnified && isRightGestureUnified && (rightZone == "TOP" || rightZone == "BOTTOM")) "UNIFIED" else rightZone
            val key = if (isHold) "pref_macro_action_${rightEffectiveZone}_${rightGesture}_HOLD" else "pref_macro_action_${rightEffectiveZone}_$rightGesture"
            val explicit = prefs.getString(key, null)
            val action = if (!explicit.isNullOrEmpty() && explicit != "none") explicit else com.sbf.lightspeed.settings.LightspeedActionRegistry.getDefaultActionForGestureKey(key)
            return action to key
        } else {
            val gestureKey = if (isHold) "${gesture}_HOLD" else gesture
            val isGestureUnified = com.sbf.lightspeed.system.LightspeedPreferences.isGestureUnified(service, isLeft = true, gestureKey)
            val dynamicZone = if (isFlankUnified && isGestureUnified && (zoneKey == "LEFT_TOP" || zoneKey == "LEFT_BOTTOM")) "LEFT_UNIFIED" else zoneKey
            val key = if (isHold) "pref_macro_action_${dynamicZone}_${gesture}_HOLD" else "pref_macro_action_${dynamicZone}_$gesture"
            val explicit = prefs.getString(key, null)
            val action = if (!explicit.isNullOrEmpty() && explicit != "none") explicit else com.sbf.lightspeed.settings.LightspeedActionRegistry.getDefaultActionForGestureKey(key)
            return action to key
        }
    }

    private fun getEffectiveAction(zoneKey: String, gesture: String, isHold: Boolean): String {
        return getEffectiveActionWithKey(zoneKey, gesture, isHold).first
    }

    private fun performActionByName(actionKey: String) {
        if (actionKey == "lightspeed:cockpit_hangar") {
            LightspeedAccessibilityService.instance?.reopenCockpitHangar()
            return
        }
        ActionDispatcher.execute(service, actionKey) {
            scrollToTop()
        }
    }

    private fun scrollToTop() {
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
}
