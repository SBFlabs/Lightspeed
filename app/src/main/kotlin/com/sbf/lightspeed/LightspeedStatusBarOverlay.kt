package com.sbf.lightspeed

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.content.SharedPreferences
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.media.AudioManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
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

class LightspeedStatusBarOverlay(
    context: Context,
    private val service: AccessibilityService
) : View(context) {

    private val prefs = service.defaultPrefs()

    private val debugPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#4DB6AC")
        style = Paint.Style.FILL
    }

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

    private val prefListener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
        if (key != null && (key.startsWith("pref_statusbar_") || key.startsWith("pref_sub_") || key.startsWith("pref_section_") || key.startsWith("pref_macro_action_STATUSBAR") || key.startsWith("pref_telemetry_"))) {
            postInvalidate()
        }
    }

    private val telemetryGlowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
    }

    private val uiHandler = Handler(Looper.getMainLooper())
    private var pendingTapRunnable: Runnable? = null
    private var lastTapTime = 0L

    init {
        isClickable = true
        isFocusable = false
        prefs.registerOnSharedPreferenceChangeListener(prefListener)

        com.sbf.lightspeed.system.LightspeedNotificationListener.onTelemetryChanged = {
            postInvalidate()
        }
        com.sbf.lightspeed.system.LightspeedKeyEngine.onNavStateListener = { state ->
            uiHandler.post {
                if (state?.isActive == true) {
                    expandForScrubbing()
                } else if (!isScrubbing) {
                    restoreWindowLayout()
                }
                postInvalidate()
            }
        }
    }

    override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) {
        super.onLayout(changed, left, top, right, bottom)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && width > 0 && height > 0) {
            systemGestureExclusionRects = listOf(android.graphics.Rect(0, 0, width, height))
        }
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        prefs.unregisterOnSharedPreferenceChangeListener(prefListener)
        com.sbf.lightspeed.system.LightspeedNotificationListener.onTelemetryChanged = null
        com.sbf.lightspeed.system.LightspeedKeyEngine.onNavStateListener = null
        pendingTapRunnable?.let { uiHandler.removeCallbacks(it) }
        uiHandler.removeCallbacks(holdRunnable)
    }

    private var startRawX = 0f
    private var startRawY = 0f
    private var startX = 0f
    private var startY = 0f
    private var furthestX = 0f   // tracks peak travel for rebound detection (mirrors lowestXReached / highestXReached on flanks)
    private var isScrubbing = false
    private var isHoldFired = false
    private var isSecondTapInSequence = false
    private var currentGesture = "NONE"
    private var scrubType = "none"

    // 2-Step Scrubbing State
    private var isHorizontalEngaged = false
    private var isTwoStepDownwardScrub = false
    private var initialScrubValue = 0
    private var currentTimeoutStep = 0
    private var scrubAnchorX = 0f
    private var scrubAnchorY = 0f
    private var hudTitle = ""
    private var hudValue = ""

    private fun expandForScrubbing() {
        val wm = context.getSystemService(Context.WINDOW_SERVICE) as? WindowManager ?: return
        val lp = layoutParams as? WindowManager.LayoutParams ?: return
        val d = resources.displayMetrics.density
        val screenW = resources.displayMetrics.widthPixels
        lp.x = 0
        lp.y = 0
        lp.width = screenW
        lp.height = (260 * d).toInt()
        lp.gravity = Gravity.TOP or Gravity.START
        try { wm.updateViewLayout(this, lp) } catch (_: Exception) {}
    }

    private fun restoreWindowLayout() {
        val wm = context.getSystemService(Context.WINDOW_SERVICE) as? WindowManager ?: return
        val lp = layoutParams as? WindowManager.LayoutParams ?: return
        val d = resources.displayMetrics.density
        val screenWidthPx = resources.displayMetrics.widthPixels
        val spanPref = prefs.getInt("pref_statusbar_span", 1080)
        val spanPx = if (spanPref >= 1000) screenWidthPx else (spanPref * d).toInt().coerceIn((50 * d).toInt(), screenWidthPx)
        val thicknessDp = prefs.getInt("pref_statusbar_thickness", 48).coerceIn(20, 52)
        val heightPx = (thicknessDp * d).toInt()
        val offsetX = (prefs.getInt("pref_statusbar_offset_x", 0) * d).toInt()
        val offsetY = (prefs.getInt("pref_statusbar_offset_y", 0) * d).toInt()
        lp.width = spanPx
        lp.height = heightPx
        lp.x = offsetX
        lp.y = offsetY
        lp.gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
        try { wm.updateViewLayout(this, lp) } catch (_: Exception) {}
    }

    private val holdRunnable = Runnable {
        if (!isScrubbing) {
            val gestureKey = if (currentGesture != "NONE") currentGesture else if (isSecondTapInSequence) "DOUBLE_TAP" else "TAP"
            val actionKey = "pref_macro_action_STATUSBAR_${gestureKey}_HOLD"
            var action = prefs.getString(actionKey, "none") ?: "none"
            if (action == "none") {
                action = prefs.getString("pref_macro_action_STATUSBAR_SCRUBBING", "system:screen_timeout") ?: "system:screen_timeout"
            }
            if (action != "none") {
                isHoldFired = true
                if (action == "system:volume" || action == "system:brightness" || action == "system:screen_timeout" || action == "system:scroll_to_top") {
                    scrubType = action
                    isScrubbing = true
                    scrubAnchorX = startRawX
                    scrubAnchorY = startRawY
                    triggerHaptic(35, 180)
                    initScrubSession()
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
        val density = resources.displayMetrics.density
        val sensPref = prefs.getInt("pref_statusbar_sensitivity", 40)
        val threshold = (sensPref * 0.5f * density).coerceAtLeast(10f * density)

        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                val nowDown = SystemClock.uptimeMillis()
                if (nowDown - lastTapTime < 280L) {
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
                isScrubbing = false
                isHoldFired = false
                isHorizontalEngaged = false
                isTwoStepDownwardScrub = false
                currentGesture = "NONE"
                scrubAnchorX = event.rawX
                scrubAnchorY = event.rawY

                scrubType = prefs.getString("pref_macro_action_STATUSBAR_SCRUBBING", "system:screen_timeout") ?: "system:screen_timeout"

                uiHandler.postDelayed(holdRunnable, 360L)
                return true
            }

            MotionEvent.ACTION_MOVE -> {
                val rawDx = event.rawX - startRawX
                val rawDy = event.rawY - startRawY
                val dist = hypot(rawDx, rawDy)

                if (dist > threshold * 0.35f && !isHoldFired && !isScrubbing && !isHorizontalEngaged) {
                    uiHandler.removeCallbacks(holdRunnable)
                }

                if (isScrubbing) {
                    handleScrubMove(event.rawX, event.rawY)
                    invalidate()
                    return true
                }

                // Direct Pull-Down Scrub or Horizontal Scrubbing Engagement
                if (!isScrubbing) {
                    if (rawDy > threshold * 0.7f && rawDy > abs(rawDx) * 0.7f) {
                        isScrubbing = true
                        isTwoStepDownwardScrub = true
                        scrubAnchorX = event.rawX
                        scrubAnchorY = event.rawY
                        triggerHaptic(30, 180)
                        initScrubSession()
                        invalidate()
                        return true
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
                }

                if (isHorizontalEngaged && !isScrubbing) {
                    if (currentGesture == "SWIPE_RIGHT" && event.x > furthestX) furthestX = event.x
                    if (currentGesture == "SWIPE_LEFT"  && event.x < furthestX) furthestX = event.x

                    val reboundThreshold = 18f * density
                    if (currentGesture == "SWIPE_RIGHT" && (furthestX - event.x) > reboundThreshold) {
                        currentGesture = "SWIPE_RIGHT_BACK"
                    } else if (currentGesture == "SWIPE_LEFT" && (event.x - furthestX) > reboundThreshold) {
                        currentGesture = "SWIPE_LEFT_BACK"
                    }
                    if (rawDy > threshold * 0.7f) {
                        isScrubbing = true
                        isTwoStepDownwardScrub = true
                        scrubAnchorX = event.rawX
                        scrubAnchorY = event.rawY
                        triggerHaptic(30, 180)
                        initScrubSession()
                        invalidate()
                    }
                }
                return true
            }

            MotionEvent.ACTION_UP -> {
                uiHandler.removeCallbacks(holdRunnable)
                val dx = event.x - startX
                val dy = event.y - startY
                val dist = hypot(dx, dy)

                if (isScrubbing) {
                    isScrubbing = false
                    isTwoStepDownwardScrub = false
                    isSecondTapInSequence = false
                    hudTitle = ""
                    hudValue = ""
                    restoreWindowLayout()
                    triggerHaptic(28, 170)
                    invalidate()
                    return true
                }

                if (isHoldFired) {
                    isSecondTapInSequence = false
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
                isScrubbing = false
                isTwoStepDownwardScrub = false
                isHoldFired = false
                isSecondTapInSequence = false
                currentGesture = "NONE"
                restoreWindowLayout()
                invalidate()
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
                uiHandler.postDelayed(tapTask, 240L)
            } else {
                if (singleTapAction != "none") {
                    triggerHaptic(20, 120)
                    performActionByName(singleTapAction)
                }
            }
        }
    }

    private fun initScrubSession() {
        expandForScrubbing()
        when (scrubType) {
            "system:screen_timeout" -> {
                initialScrubValue = LightspeedTimeoutEngine.getCurrentTimeoutIndex(context)
                currentTimeoutStep = initialScrubValue
                val label = LightspeedTimeoutEngine.TIMEOUT_STEPS[initialScrubValue].second
                hudTitle = "SHIP GOES DARK IN"
                hudValue = label
            }
            "system:volume" -> {
                val am = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
                val cur = am?.getStreamVolume(AudioManager.STREAM_MUSIC) ?: 0
                val max = am?.getStreamMaxVolume(AudioManager.STREAM_MUSIC) ?: 15
                initialScrubValue = cur
                hudTitle = "MEDIA VOLUME"
                hudValue = "$cur / $max"
            }
            "system:brightness" -> {
                val cur = try {
                    Settings.System.getInt(context.contentResolver, Settings.System.SCREEN_BRIGHTNESS, 128)
                } catch (_: Exception) { 128 }
                initialScrubValue = cur
                hudTitle = "BRIGHTNESS"
                hudValue = "${(cur * 100 / 255)}%"
            }
            "system:scroll_to_top" -> {
                hudTitle = "SCROLL TO TOP"
                hudValue = "SNAPPING"
                scrollToTop()
            }
        }
    }

    private fun handleScrubMove(rawX: Float, rawY: Float) {
        val density = resources.displayMetrics.density
        val stepDistance = 24f * density

        val dx = rawX - scrubAnchorX
        val dy = rawY - scrubAnchorY
        val primaryDelta = if (abs(dx) >= abs(dy)) dx else dy

        when (scrubType) {
            "system:screen_timeout" -> {
                val stepOffset = (primaryDelta / stepDistance).toInt()
                val targetIndex = (initialScrubValue + stepOffset).coerceIn(0, LightspeedTimeoutEngine.TIMEOUT_STEPS.lastIndex)
                if (targetIndex != currentTimeoutStep) {
                    currentTimeoutStep = targetIndex
                    val (_, label) = LightspeedTimeoutEngine.setStepIndex(context, targetIndex)
                    hudValue = label
                    hudTitle = "SHIP GOES DARK IN"
                    triggerHaptic(18, 120)
                }
            }
            "system:volume" -> {
                val am = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager ?: return
                val max = am.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
                val stepOffset = (primaryDelta / (stepDistance * 1.2f)).toInt()
                val targetVol = (initialScrubValue + stepOffset).coerceIn(0, max)
                am.setStreamVolume(AudioManager.STREAM_MUSIC, targetVol, AudioManager.FLAG_SHOW_UI)
                hudValue = "$targetVol / $max"
            }
            "system:brightness" -> {
                if (Settings.System.canWrite(context)) {
                    val stepOffset = ((primaryDelta / (stepDistance * 1.5f)) * 15).toInt()
                    val target = (initialScrubValue + stepOffset).coerceIn(10, 255)
                    try {
                        Settings.System.putInt(context.contentResolver, Settings.System.SCREEN_BRIGHTNESS, target)
                        hudValue = "${(target * 100 / 255)}%"
                    } catch (_: Exception) {}
                }
            }
        }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val isExpanded = prefs.getBoolean("pref_section_statusbar_expanded", true)
        val isPreview  = prefs.getBoolean("pref_statusbar_preview", false)
        val isReview = isExpanded && isPreview
        val transparencyPct = prefs.getInt("pref_statusbar_transparency", 0)
        val d = resources.displayMetrics.density
        val screenW = resources.displayMetrics.widthPixels.toFloat()
        val w = width.toFloat()
        val h = height.toFloat()

        val m3Primary = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            context.resources.getColor(android.R.color.system_accent1_300, context.theme)
        } else {
            Color.parseColor("#90CAF9")
        }

        if (isReview) {
            // Live Preview — matching deflector active review styling
            debugPaint.style = Paint.Style.FILL
            debugPaint.color = Color.argb(120, Color.red(m3Primary), Color.green(m3Primary), Color.blue(m3Primary))
            canvas.drawRoundRect(RectF(0f, 0f, w, h), 8f * d, 8f * d, debugPaint)

            debugPaint.style = Paint.Style.STROKE
            debugPaint.strokeWidth = 2f * d
            debugPaint.color = Color.WHITE
            canvas.drawRoundRect(RectF(1f * d, 1f * d, w - 1f * d, h - 1f * d), 8f * d, 8f * d, debugPaint)

            // Center Telemetry Label
            hudTextPaint.textSize = 10f * d
            hudTextPaint.color = Color.WHITE
            canvas.drawText("✦ SENSOR DECK & HORIZON RAIL", w / 2f, (h / 2f) + 3.5f * d, hudTextPaint)
        } else if (transparencyPct > 0) {
            val alpha = (transparencyPct * 2.55f).toInt().coerceIn(10, 255)
            debugPaint.style = Paint.Style.FILL
            debugPaint.color = Color.argb((alpha * 0.4f).toInt(), Color.red(m3Primary), Color.green(m3Primary), Color.blue(m3Primary))
            canvas.drawRoundRect(RectF(0f, 0f, w, 4f * d), 2f * d, 2f * d, debugPaint)

            debugPaint.color = Color.argb(alpha, Color.red(m3Primary), Color.green(m3Primary), Color.blue(m3Primary))
            canvas.drawRoundRect(RectF(0f, 0f, w, 2.5f * d), 1.5f * d, 1.5f * d, debugPaint)
        }

        // 1. Top-Edge Line Telemetry Renderer
        val dlRouting = prefs.getString(com.sbf.lightspeed.system.LightspeedPreferences.KEY_TELEMETRY_DOWNLOADS_ROUTING, "notch_pill") ?: "notch_pill"
        val mediaRouting = prefs.getString(com.sbf.lightspeed.system.LightspeedPreferences.KEY_TELEMETRY_MEDIA_ROUTING, "none") ?: "none"
        val primaryDl = com.sbf.lightspeed.system.LightspeedNotificationListener.getPrimaryDownload()
        val media = com.sbf.lightspeed.system.LightspeedNotificationListener.activeMediaTelemetry

        if ((dlRouting == "top_line" || dlRouting == "both") && primaryDl != null && primaryDl.progressFraction >= 0f) {
            val prog = primaryDl.progressFraction.coerceIn(0f, 1f)
            telemetryGlowPaint.strokeWidth = 3.5f * d
            telemetryGlowPaint.color = Color.argb(100, Color.red(m3Primary), Color.green(m3Primary), Color.blue(m3Primary))
            canvas.drawLine(0f, 1.5f * d, w * prog, 1.5f * d, telemetryGlowPaint)
            telemetryGlowPaint.strokeWidth = 2f * d
            telemetryGlowPaint.color = m3Primary
            canvas.drawLine(0f, 1.5f * d, w * prog, 1.5f * d, telemetryGlowPaint)
        } else if ((mediaRouting == "top_line" || mediaRouting == "both") && media != null && media.isPlaying && media.durationMs > 0) {
            val prog = (media.positionMs.toFloat() / media.durationMs.toFloat()).coerceIn(0f, 1f)
            telemetryGlowPaint.strokeWidth = 3.5f * d
            telemetryGlowPaint.color = Color.argb(100, Color.red(m3Primary), Color.green(m3Primary), Color.blue(m3Primary))
            canvas.drawLine(0f, 1.5f * d, w * prog, 1.5f * d, telemetryGlowPaint)
            telemetryGlowPaint.strokeWidth = 2f * d
            telemetryGlowPaint.color = m3Primary
            canvas.drawLine(0f, 1.5f * d, w * prog, 1.5f * d, telemetryGlowPaint)
        }

        // 2. Hardware Gear Set HUD Navigation Renderer
        val navState = com.sbf.lightspeed.system.LightspeedKeyEngine.currentNavState
        if (navState != null && navState.isActive) {
            val topY = (prefs.getInt("pref_statusbar_thickness", 48).coerceIn(20, 52) * d) + (8f * d)
            LightspeedHudRenderer.renderHud(
                canvas = canvas,
                style = "canopy_droppod",
                title = "GEAR SET NAV: ${navState.setName}",
                value = navState.currentLabel,
                stepIndex = navState.currentIndex,
                totalSteps = navState.totalCount,
                centerX = screenW / 2f,
                centerY = (h / 2f).coerceAtLeast(topY + 40f * d),
                topY = topY,
                primaryColor = m3Primary,
                density = d
            )
        }

        // 3. Gesture Scrubbing HUD Renderer
        if (isScrubbing && hudTitle.isNotEmpty()) {
            val hudStyle = prefs.getString("pref_macro_hud_style_STATUSBAR_SCRUBBING", null)
                ?: prefs.getString("pref_macro_hud_style_default", "canopy_droppod") ?: "canopy_droppod"
            val totalSteps = if (scrubType == "system:screen_timeout") LightspeedTimeoutEngine.TIMEOUT_STEPS.size else 0
            val stepIdx = if (scrubType == "system:screen_timeout") currentTimeoutStep else -1
            val topY = (prefs.getInt("pref_statusbar_thickness", 48).coerceIn(20, 52) * d) + (8f * d)

            LightspeedHudRenderer.renderHud(
                canvas = canvas,
                style = hudStyle,
                title = hudTitle,
                value = hudValue,
                stepIndex = stepIdx,
                totalSteps = totalSteps,
                centerX = screenW / 2f,
                centerY = (h / 2f).coerceAtLeast(topY + 40f * d),
                topY = topY,
                primaryColor = m3Primary,
                density = d
            )
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
}
