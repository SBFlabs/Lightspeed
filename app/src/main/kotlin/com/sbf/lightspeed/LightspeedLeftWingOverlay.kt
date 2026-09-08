package com.sbf.lightspeed

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
import android.os.VibrationEffect
import android.os.Vibrator
import android.provider.Settings
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.view.accessibility.AccessibilityNodeInfo
import com.sbf.lightspeed.system.ActionDispatcher
import com.sbf.lightspeed.system.LightspeedHapticEngine
import com.sbf.lightspeed.system.LightspeedHudRenderer
import com.sbf.lightspeed.system.LightspeedTimeoutEngine
import com.sbf.lightspeed.system.defaultPrefs
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.roundToInt

class LightspeedLeftWingOverlay(
    context: Context,
    private val service: AccessibilityService
) : View(context) {

    private val prefs = service.defaultPrefs()

    private val highlightPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val hudPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#8000E5FF")
        strokeWidth = 4f
        style = Paint.Style.STROKE
    }
    private val hudFillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#2600E5FF")
        style = Paint.Style.FILL
    }
    private val hudTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textSize = 28f
        textAlign = Paint.Align.CENTER
        isFakeBoldText = true
    }

    private val topTouchBounds = RectF()
    private val centerTouchBounds = RectF()
    private val bottomTouchBounds = RectF()

    private var topHeightPx = 0f
    private var centerHeightPx = 0f
    private var bottomHeightPx = 0f
    private var topTouchWidthPx = 0f
    private var centerTouchWidthPx = 0f
    private var bottomTouchWidthPx = 0f
    private var centerYOffsetPx = 0f

    private val uiHandler = Handler(Looper.getMainLooper())
    private var pendingTapRunnable: Runnable? = null
    private var lastTapTime = 0L

    private var glowFraction: Float = 0f
    private var glowAnimator: ValueAnimator? = null

    fun triggerGlow(durationMs: Long = -1L) {
        post {
            val effectiveDuration = if (durationMs > 0L) durationMs else com.sbf.lightspeed.system.LightspeedPreferences.getDeflectorGlowDurationMs(context)
            glowAnimator?.cancel()
            glowAnimator = ValueAnimator.ofFloat(1f, 0f).apply {
                duration = effectiveDuration
                interpolator = DecelerateInterpolator()
                addUpdateListener { anim ->
                    glowFraction = anim.animatedValue as Float
                    invalidate()
                }
                start()
            }
        }
    }

    private val prefListener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
        if (key != null && (key.startsWith("pref_sidebar_") || key.startsWith("pref_section_") || key.startsWith("pref_macro_action_") || key.startsWith("pref_symmetry_") || key.startsWith("pref_deflector_"))) {
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

    override fun onDetachedFromWindow() {
        glowAnimator?.cancel()
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

        val geomMode = prefs.getString("pref_symmetry_geometry_mode", "independent") ?: "independent"
        val isMirroringRight = geomMode == "right"

        val centerPrefix = if (isMirroringRight) "pref_sidebar_center" else "pref_sidebar_left_center"
        val topPrefix = if (isMirroringRight) "pref_sidebar_top" else "pref_sidebar_left_top"
        val bottomPrefix = if (isMirroringRight) "pref_sidebar_bottom" else "pref_sidebar_left_bottom"

        centerHeightPx = prefs.getInt("${centerPrefix}_height", 400).toFloat() * density
        centerYOffsetPx = prefs.getInt("${centerPrefix}_y_offset", 0).toFloat() * density
        centerTouchWidthPx = prefs.getInt("${centerPrefix}_touch_width", 40).toFloat() * density

        topHeightPx = prefs.getInt("${topPrefix}_height", 200).toFloat() * density
        topTouchWidthPx = prefs.getInt("${topPrefix}_touch_width", 40).toFloat() * density

        bottomHeightPx = prefs.getInt("${bottomPrefix}_height", 200).toFloat() * density
        bottomTouchWidthPx = prefs.getInt("${bottomPrefix}_touch_width", 40).toFloat() * density

        val centerY = (screenH / 2f) + centerYOffsetPx
        val centerTop = centerY - (centerHeightPx / 2f)
        val centerBottom = centerY + (centerHeightPx / 2f)

        val topLimit = (centerTop - topHeightPx).coerceAtLeast(0f)
        val bottomLimit = (centerBottom + bottomHeightPx).coerceAtMost(screenH)

        val maxTouchW = maxOf(centerTouchWidthPx, topTouchWidthPx, bottomTouchWidthPx)
        val winHeight = (bottomLimit - topLimit).toInt().coerceAtLeast(100)
        val winWidth = maxTouchW.toInt().coerceAtLeast((10f * density).toInt())
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

    private var isCurrentlyTouched = false
    private var startRawX = 0f
    private var startRawY = 0f
    private var startX = 0f
    private var startY = 0f
    private var highestXReached = 0f
    private var lowestXReached = 0f
    private var highestYReached = 0f
    private var lowestYReached = 0f
    private var initialDominantAxis = "NONE"
    private var isScrubbing = false
    private var isHoldFired = false
    private var currentGesture = "NONE"
    private var activeZoneKey = "LEFT_CENTER"
    private var scrubType = "none"

    // 2-Step Inward Scrubbing & Hold-to-Scrub
    private var isHorizontalEngaged = false
    private var isTwoStepDownwardScrub = false
    private var initialScrubValue = 0
    private var initialScrubTouchY = 0f
    private var currentRawY = 0f
    private var scrubAccumulator = 0f
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
                    scrubAccumulator = 0f
                    initialScrubTouchY = currentRawY
                    triggerHaptic(35, 180)
                    when (scrubType) {
                        "system:volume", "scrub:volume" -> {
                            val am = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
                            initialScrubValue = am?.getStreamVolume(AudioManager.STREAM_MUSIC) ?: 0
                            val max = am?.getStreamMaxVolume(AudioManager.STREAM_MUSIC) ?: 15
                            hudTitle = "MEDIA VOLUME"
                            hudValue = "$initialScrubValue / $max"
                            dispatchScrubHud(hudTitle, hudValue, initialScrubValue, max)
                        }
                        "system:brightness", "scrub:brightness" -> {
                            initialScrubValue = try {
                                Settings.System.getInt(context.contentResolver, Settings.System.SCREEN_BRIGHTNESS)
                            } catch (_: Exception) { 128 }
                            val brightResolution = prefs.getInt(com.sbf.lightspeed.system.LightspeedPreferences.KEY_BRIGHTNESS_SCRUB_RESOLUTION, 32).coerceIn(10, 254)
                            hudTitle = "BRIGHTNESS"
                            hudValue = "${(initialScrubValue * 100 / 255)}%"
                            dispatchScrubHud(hudTitle, hudValue, (initialScrubValue * brightResolution / 255), brightResolution)
                        }
                        "system:screen_timeout" -> {
                            initialScrubValue = LightspeedTimeoutEngine.getCurrentTimeoutIndex(context)
                            hudTitle = "SHIP GOES DARK IN"
                            hudValue = LightspeedTimeoutEngine.TIMEOUT_STEPS[initialScrubValue].second
                            dispatchScrubHud(hudTitle, hudValue, initialScrubValue, LightspeedTimeoutEngine.TIMEOUT_STEPS.size)
                        }
                    }
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
                startX = x
                startY = y
                highestXReached = rawX
                lowestXReached = rawX
                highestYReached = rawY
                lowestYReached = rawY
                initialDominantAxis = "NONE"
                isCurrentlyTouched = true
                invalidate()
                isScrubbing = false
                isHoldFired = false
                currentGesture = "NONE"
                isHorizontalEngaged = false
                isTwoStepDownwardScrub = false
                scrubAccumulator = 0f

                activeZoneKey = when {
                    topTouchBounds.contains(x, y) -> "LEFT_TOP"
                    bottomTouchBounds.contains(x, y) -> "LEFT_BOTTOM"
                    else -> "LEFT_CENTER"
                }

                if (activeZoneKey == "LEFT_CENTER") {
                    (service as? LightspeedAccessibilityService)?.startCruiseFromLeft(rawX, rawY)
                    return true
                }

                val scrubAction = prefs.getString("pref_macro_action_${activeZoneKey}_SCRUBBING", "none") ?: "none"
                scrubType = scrubAction

                uiHandler.removeCallbacks(holdRunnable)
                uiHandler.postDelayed(holdRunnable, 420)
                return true
            }

            MotionEvent.ACTION_MOVE -> {
                if (activeZoneKey == "LEFT_CENTER") {
                    (service as? LightspeedAccessibilityService)?.forwardTouchEventToCruise(isLeft = true, event = event)
                    return true
                }

                val dx = rawX - startRawX
                val dy = rawY - startRawY
                val totalDist = hypot(dx.toDouble(), dy.toDouble()).toFloat()

                if (rawX > highestXReached) highestXReached = rawX
                if (rawX < lowestXReached) lowestXReached = rawX
                if (rawY > highestYReached) highestYReached = rawY
                if (rawY < lowestYReached) lowestYReached = rawY

                if (initialDominantAxis == "NONE") {
                    if (abs(dy) > 25f && abs(dy) > abs(dx) * 1.2f) {
                        initialDominantAxis = "Y"
                    } else if (dx > 25f && dx > abs(dy) * 1.2f) {
                        initialDominantAxis = "X"
                    }
                }

                if (isScrubbing) {
                    val scrubDy = if (isTwoStepDownwardScrub) dy else (rawY - initialScrubTouchY)
                    handleScrubMotion(scrubDy)
                    return true
                }

                if (scrubType != "none" && !isTwoStepDownwardScrub) {
                    if (dx > 45f && abs(dx) > abs(dy) * 1.3f) {
                        isHorizontalEngaged = true
                    }
                    if (isHorizontalEngaged && dy > 35f && abs(dy) > abs(dx) * 0.8f) {
                        isTwoStepDownwardScrub = true
                        isScrubbing = true
                        activeScrubActionKey = "pref_macro_action_${activeZoneKey}_SCRUBBING"
                        uiHandler.removeCallbacks(holdRunnable)
                        triggerHaptic(30, 180)

                        when (scrubType) {
                            "system:volume", "scrub:volume" -> {
                                val am = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
                                initialScrubValue = am?.getStreamVolume(AudioManager.STREAM_MUSIC) ?: 0
                                val max = am?.getStreamMaxVolume(AudioManager.STREAM_MUSIC) ?: 15
                                hudTitle = "MEDIA VOLUME"
                                hudValue = "$initialScrubValue / $max"
                                dispatchScrubHud(hudTitle, hudValue, initialScrubValue, max)
                            }
                            "system:brightness", "scrub:brightness" -> {
                                initialScrubValue = try {
                                    Settings.System.getInt(context.contentResolver, Settings.System.SCREEN_BRIGHTNESS)
                                } catch (_: Exception) { 128 }
                                val brightResolution = prefs.getInt(com.sbf.lightspeed.system.LightspeedPreferences.KEY_BRIGHTNESS_SCRUB_RESOLUTION, 32).coerceIn(10, 254)
                                hudTitle = "BRIGHTNESS"
                                hudValue = "${(initialScrubValue * 100 / 255)}%"
                                dispatchScrubHud(hudTitle, hudValue, (initialScrubValue * brightResolution / 255), brightResolution)
                            }
                            "system:screen_timeout" -> {
                                initialScrubValue = LightspeedTimeoutEngine.getCurrentTimeoutIndex(context)
                                hudTitle = "SHIP GOES DARK IN"
                                hudValue = LightspeedTimeoutEngine.TIMEOUT_STEPS[initialScrubValue].second
                                dispatchScrubHud(hudTitle, hudValue, initialScrubValue, LightspeedTimeoutEngine.TIMEOUT_STEPS.size)
                            }
                        }
                        invalidate()
                        return true
                    }
                }

                val density = resources.displayMetrics.density
                val maxExcursionX = highestXReached - startRawX
                val maxExcursionUp = startRawY - lowestYReached
                val maxExcursionDown = highestYReached - startRawY
                val excursion = maxOf(maxExcursionX, maxExcursionUp, maxExcursionDown)

                if (excursion > (18f * density)) {
                    val prev = currentGesture
                    currentGesture = determineGesture(dx, dy, rawX, rawY)
                    if (currentGesture != "NONE" && currentGesture != prev) {
                        if (prev == "NONE" && com.sbf.lightspeed.system.LightspeedPreferences.isDeflectorGlowOnGestureStep(context)) {
                            triggerGlow(500L)
                        }
                        triggerHaptic(18, 100)
                        if (!isHoldFired) {
                            uiHandler.removeCallbacks(holdRunnable)
                            uiHandler.postDelayed(holdRunnable, 400L)
                        }
                    }
                }
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

                if (isScrubbing) {
                    isTwoStepDownwardScrub = false
                    isScrubbing = false
                    activeScrubActionKey = null
                    hudTitle = ""
                    hudValue = ""
                    LightspeedStatusBarOverlay.dismissActionHud(1200L)
                    invalidate()
                    return true
                }

                if (!isHoldFired) {
                    val finalGesture = if (currentGesture != "NONE") currentGesture else determineGesture(rawX - startRawX, rawY - startRawY, rawX, rawY)
                    val isReturning = finalGesture == "SWIPE_RIGHT_BACK" || finalGesture == "SWIPE_UP_DOWN" || finalGesture == "SWIPE_DOWN_UP"
                    
                    if (isReturning || (finalGesture != "NONE" && maxExcursion >= (18f * density))) {
                        val action = getEffectiveAction(activeZoneKey, finalGesture, false)
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
                if (isScrubbing) {
                    LightspeedStatusBarOverlay.dismissActionHud(600L)
                }
                isTwoStepDownwardScrub = false
                isScrubbing = false
                activeScrubActionKey = null
                hudTitle = ""
                hudValue = ""
                invalidate()
                return true
            }
        }
        return true
    }

    private fun determineGesture(dx: Float, dy: Float, rawX: Float, rawY: Float): String {
        val density = resources.displayMetrics.density
        val maxExcursionX = highestXReached - startRawX
        val maxExcursionUp = startRawY - lowestYReached
        val maxExcursionDown = highestYReached - startRawY

        // 1. Returning Gestures (highest priority on reversal detection)
        if (maxExcursionX > (22f * density) && (highestXReached - rawX) > (16f * density)) {
            return "SWIPE_RIGHT_BACK"
        }
        if (maxExcursionUp > (25f * density) && (rawY - lowestYReached) > (20f * density)) {
            return "SWIPE_UP_DOWN"
        }
        if (maxExcursionDown > (25f * density) && (highestYReached - rawY) > (20f * density)) {
            return "SWIPE_DOWN_UP"
        }

        // 2. Compound Diagonal Gestures
        if (initialDominantAxis == "Y") {
            if (lowestYReached < startRawY - (25f * density)) { // Moved UP
                if (dx > (25f * density)) return "SWIPE_UP_RIGHT"
                if (dy < (-35f * density)) return "SWIPE_UP"
            } else if (highestYReached > startRawY + (25f * density)) { // Moved DOWN
                if (dx > (25f * density)) return "SWIPE_DOWN_RIGHT"
                if (dy > (35f * density)) return "SWIPE_DOWN"
            }
        } else if (initialDominantAxis == "X") {
            if (highestXReached > startRawX + (25f * density)) {
                if (dy < (-25f * density)) return "SWIPE_RIGHT_UP"
                if (dy > (25f * density)) return "SWIPE_RIGHT_DOWN"
                if (dx > (35f * density)) return "SWIPE_RIGHT"
            }
        }

        return when {
            dx > (32f * density) && dy < (-25f * density) -> "SWIPE_RIGHT_UP"
            dx > (32f * density) && dy > (25f * density) -> "SWIPE_RIGHT_DOWN"
            dy < (-25f * density) && dx > (25f * density) -> "SWIPE_UP_RIGHT"
            dy > (25f * density) && dx > (25f * density) -> "SWIPE_DOWN_RIGHT"
            dx > (32f * density) -> "SWIPE_RIGHT"
            dy < (-32f * density) -> "SWIPE_UP"
            dy > (32f * density) -> "SWIPE_DOWN"
            else -> "NONE"
        }
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

    private fun dispatchScrubHud(title: String, value: String, stepIndex: Int, totalSteps: Int) {
        val isBrightness = scrubType == "system:brightness" || scrubType == "scrub:brightness" || title == "BRIGHTNESS"
        val isVolume = scrubType == "system:volume" || scrubType == "scrub:volume" || title == "MEDIA VOLUME"
        val showHud = when {
            isBrightness -> prefs.getBoolean(com.sbf.lightspeed.system.LightspeedPreferences.KEY_HUD_BRIGHTNESS_ENABLED, true)
            isVolume -> prefs.getBoolean(com.sbf.lightspeed.system.LightspeedPreferences.KEY_HUD_VOLUME_ENABLED, true)
            else -> true
        }
        if (showHud) {
            val fallbackKey = activeScrubActionKey ?: "pref_macro_action_${activeZoneKey}_SCRUBBING"
            val hudStyle = com.sbf.lightspeed.system.LightspeedPreferences.resolveHudStyle(prefs, fallbackKey, scrubType)

            LightspeedStatusBarOverlay.showActionHud(
                title = title,
                value = value,
                stepIndex = stepIndex,
                totalSteps = totalSteps,
                durationMs = 0L,
                style = hudStyle
            )
        }
    }

    private fun handleScrubMotion(dy: Float) {
        val density = resources.displayMetrics.density
        val stepDistance = 28f * density
        when (scrubType) {
            "system:volume", "scrub:volume" -> {
                val am = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager ?: return
                val maxVol = am.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
                val volStep = prefs.getInt(com.sbf.lightspeed.system.LightspeedPreferences.KEY_VOLUME_SCRUB_STEP, 1)
                val stepOffset = (-dy / stepDistance).toInt() * volStep
                val targetVol = (initialScrubValue + stepOffset).coerceIn(0, maxVol)
                val currentVol = am.getStreamVolume(AudioManager.STREAM_MUSIC)
                val showNativeUi = prefs.getBoolean(com.sbf.lightspeed.system.LightspeedPreferences.KEY_VOLUME_SHOW_NATIVE_SLIDER, false)
                val flags = if (showNativeUi) AudioManager.FLAG_SHOW_UI else 0
                if (targetVol != currentVol) {
                    am.setStreamVolume(AudioManager.STREAM_MUSIC, targetVol, flags)
                    triggerHaptic(18, 110)
                }
                hudTitle = "MEDIA VOLUME"
                hudValue = "$targetVol / $maxVol"
                dispatchScrubHud(hudTitle, hudValue, targetVol, maxVol)
                invalidate()
            }
            "system:brightness", "scrub:brightness" -> {
                if (Settings.System.canWrite(context)) {
                    val brightResolution = prefs.getInt(com.sbf.lightspeed.system.LightspeedPreferences.KEY_BRIGHTNESS_SCRUB_RESOLUTION, 32).coerceIn(10, 254)
                    val brightStep = prefs.getInt(com.sbf.lightspeed.system.LightspeedPreferences.KEY_BRIGHTNESS_SCRUB_STEP, (255f / brightResolution).roundToInt().coerceIn(1, 32))
                    val stepOffset = ((-dy / (stepDistance * 1.2f)) * (brightStep * 1.5f)).toInt()
                    val target = (initialScrubValue + stepOffset).coerceIn(0, 255)
                    try {
                        val currentBrightness = Settings.System.getInt(context.contentResolver, Settings.System.SCREEN_BRIGHTNESS)
                        if (abs(target - currentBrightness) > 2) {
                            Settings.System.putInt(context.contentResolver, Settings.System.SCREEN_BRIGHTNESS, target)
                            triggerHaptic(14, 90)
                        }
                        hudTitle = "BRIGHTNESS"
                        hudValue = "${(target * 100 / 255)}%"
                        dispatchScrubHud(hudTitle, hudValue, (target * brightResolution / 255), brightResolution)
                        invalidate()
                    } catch (_: Exception) {}
                } else {
                    LightspeedTimeoutEngine.requestWriteSettingsPermission(context)
                }
            }
            "system:screen_timeout" -> {
                val stepOffset = (-dy / (stepDistance * 1.5f)).toInt()
                val targetIndex = (initialScrubValue + stepOffset).coerceIn(0, LightspeedTimeoutEngine.TIMEOUT_STEPS.lastIndex)
                val currentIdx = LightspeedTimeoutEngine.getCurrentTimeoutIndex(context)
                if (targetIndex != currentIdx) {
                    val (_, label) = LightspeedTimeoutEngine.setStepIndex(context, targetIndex)
                    hudTitle = "SHIP GOES DARK IN"
                    hudValue = label
                    dispatchScrubHud(hudTitle, hudValue, targetIndex, LightspeedTimeoutEngine.TIMEOUT_STEPS.size)
                    triggerHaptic(22, 140)
                    invalidate()
                }
            }
        }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val d = resources.displayMetrics.density

        val isFlankUnified = prefs.getBoolean("pref_sidebar_left_link_flank_actions", false)
        val isUnifiedExpanded = prefs.getBoolean("pref_section_left_unified_expanded", false)
        val isTopExpanded = if (isFlankUnified) isUnifiedExpanded else prefs.getBoolean("pref_section_left_top_expanded", false)
        val isCenterExpanded = prefs.getBoolean("pref_section_left_center_expanded", false)
        val isBottomExpanded = if (isFlankUnified) isUnifiedExpanded else prefs.getBoolean("pref_section_left_bottom_expanded", false)
        val isPreview = prefs.getBoolean("pref_sidebar_left_preview", false)

        val geomMode = prefs.getString("pref_symmetry_geometry_mode", "independent") ?: "independent"
        val isMirroringRight = geomMode == "right"

        val centerTransparency = if (isMirroringRight) prefs.getInt("pref_sidebar_center_transparency", 0) else prefs.getInt("pref_sidebar_left_center_transparency", 0)
        val topTransparency = if (isMirroringRight) prefs.getInt("pref_sidebar_top_transparency", 0) else prefs.getInt("pref_sidebar_left_top_transparency", 0)
        val bottomTransparency = if (isMirroringRight) prefs.getInt("pref_sidebar_bottom_transparency", 0) else prefs.getInt("pref_sidebar_left_bottom_transparency", 0)

        val glowStyle = prefs.getString(com.sbf.lightspeed.system.LightspeedPreferences.KEY_DEFLECTOR_GLOW_STYLE, "progressive_frost") ?: "progressive_frost"

        val m3Primary = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            context.resources.getColor(android.R.color.system_accent1_600, context.theme)
        } else {
            Color.parseColor("#6750A4")
        }

        com.sbf.lightspeed.system.LightspeedDeflectorRenderer.drawDeflectorWing(
            canvas = canvas,
            isLeft = true,
            density = d,
            w = width.toFloat(),
            h = height.toFloat(),
            topTouchBounds = topTouchBounds,
            centerTouchBounds = centerTouchBounds,
            bottomTouchBounds = bottomTouchBounds,
            isCurrentlyTouched = isCurrentlyTouched,
            activeZoneIsCenter = activeZoneKey == "LEFT_CENTER",
            activeZoneIsTop = activeZoneKey == "LEFT_TOP",
            activeZoneIsBottom = activeZoneKey == "LEFT_BOTTOM",
            glowFraction = glowFraction,
            centerTransparency = centerTransparency,
            topTransparency = topTransparency,
            bottomTransparency = bottomTransparency,
            isReview = isPreview && (isTopExpanded || isCenterExpanded || isBottomExpanded),
            m3Primary = m3Primary,
            glowStyle = glowStyle
        )
    }

    private fun getEffectiveActionWithKey(zoneKey: String, gesture: String, isHold: Boolean): Pair<String, String> {
        val isFlankUnified = prefs.getBoolean("pref_sidebar_left_link_flank_actions", false)
        val gestMode = prefs.getString("pref_symmetry_gesture_mode", "independent") ?: "independent"
        val isMirroringRight = gestMode == "right"

        if (isMirroringRight) {
            val isRightUnified = prefs.getBoolean("pref_sidebar_right_link_flank_actions", false)
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
            val key = if (isHold) "pref_macro_action_${rightZone}_${rightGesture}_HOLD" else "pref_macro_action_${rightZone}_$rightGesture"
            return (prefs.getString(key, "none") ?: "none") to key
        } else {
            val dynamicZone = if (isFlankUnified && (zoneKey == "LEFT_TOP" || zoneKey == "LEFT_BOTTOM")) "LEFT_UNIFIED" else zoneKey
            val key = if (isHold) "pref_macro_action_${dynamicZone}_${gesture}_HOLD" else "pref_macro_action_${dynamicZone}_$gesture"
            return (prefs.getString(key, "none") ?: "none") to key
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
            list.add(AccessibilityNodeInfo.obtain(node))
        }

        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            collectScrollableNodes(child, list)
        }
    }
}
