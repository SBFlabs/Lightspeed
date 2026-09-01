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
                    expandForHud()
                } else {
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
    private var furthestX = 0f
    private var isHoldFired = false
    private var isSecondTapInSequence = false
    private var currentGesture = "NONE"
    private var isHorizontalEngaged = false

    private fun expandForHud() {
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
        val gestureKey = if (currentGesture != "NONE") currentGesture else if (isSecondTapInSequence) "DOUBLE_TAP" else "TAP"
        val actionKey = "pref_macro_action_STATUSBAR_${gestureKey}_HOLD"
        val action = prefs.getString(actionKey, "none") ?: "none"
        if (action != "none") {
            isHoldFired = true
            triggerHaptic(40, 200)
            performActionByName(action)
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

                // Downward Pull: Cancel pending gestures immediately to pass control seamlessly to Android notification shade
                if (rawDy > threshold * 0.5f && rawDy > abs(rawDx) * 0.8f) {
                    uiHandler.removeCallbacks(holdRunnable)
                    pendingTapRunnable?.let { uiHandler.removeCallbacks(it) }
                    pendingTapRunnable = null
                    isHorizontalEngaged = false
                    currentGesture = "NONE"
                    return false
                }

                if (dist > threshold * 0.35f && !isHoldFired && !isHorizontalEngaged) {
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
                isHoldFired = false
                isSecondTapInSequence = false
                currentGesture = "NONE"
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
            canvas.drawText("✦ SENSOR AREA (TOP EDGE)", w / 2f, (h / 2f) + 3.5f * d, hudTextPaint)
        } else if (transparencyPct > 0) {
            val alpha = (transparencyPct * 2.55f).toInt().coerceIn(10, 255)
            debugPaint.style = Paint.Style.FILL
            debugPaint.color = Color.argb((alpha * 0.4f).toInt(), Color.red(m3Primary), Color.green(m3Primary), Color.blue(m3Primary))
            canvas.drawRoundRect(RectF(0f, 0f, w, 4f * d), 2f * d, 2f * d, debugPaint)

            debugPaint.color = Color.argb(alpha, Color.red(m3Primary), Color.green(m3Primary), Color.blue(m3Primary))
            canvas.drawRoundRect(RectF(0f, 0f, w, 2.5f * d), 1.5f * d, 1.5f * d, debugPaint)
        }

        // 1. Horizon Rail Telemetry Line Renderer
        val dlRouting = prefs.getString(com.sbf.lightspeed.system.LightspeedPreferences.KEY_TELEMETRY_DOWNLOADS_ROUTING, "notch_pill") ?: "notch_pill"
        val mediaRouting = prefs.getString(com.sbf.lightspeed.system.LightspeedPreferences.KEY_TELEMETRY_MEDIA_ROUTING, "none") ?: "none"
        val primaryDl = com.sbf.lightspeed.system.LightspeedNotificationListener.getPrimaryDownload()
        val media = com.sbf.lightspeed.system.LightspeedNotificationListener.activeMediaTelemetry

        val railThicknessDp = prefs.getInt("pref_horizon_rail_thickness", 3).coerceIn(1, 8)
        val railGlowPct = prefs.getInt("pref_horizon_rail_glow", 80).coerceIn(0, 100)
        val railTrackOpacityPct = prefs.getInt("pref_horizon_rail_track_opacity", 20).coerceIn(0, 100)
        val isDynamicColor = prefs.getBoolean("pref_horizon_rail_dynamic_color", true)
        val railColor = if (isDynamicColor) m3Primary else Color.parseColor("#00E5FF")

        val lineY = (railThicknessDp * d) / 2f

        val activeProgress = when {
            (dlRouting == "top_line" || dlRouting == "both") && primaryDl != null && primaryDl.progressFraction >= 0f -> primaryDl.progressFraction.coerceIn(0f, 1f)
            (mediaRouting == "top_line" || mediaRouting == "both") && media != null && media.isPlaying && media.durationMs > 0 -> (media.positionMs.toFloat() / media.durationMs.toFloat()).coerceIn(0f, 1f)
            else -> -1f
        }

        if (activeProgress >= 0f) {
            // Background inactive track rail
            if (railTrackOpacityPct > 0) {
                val trackAlpha = (railTrackOpacityPct * 2.55f).toInt().coerceIn(10, 255)
                telemetryGlowPaint.strokeWidth = railThicknessDp * d
                telemetryGlowPaint.color = Color.argb(trackAlpha, Color.red(railColor), Color.green(railColor), Color.blue(railColor))
                canvas.drawLine(0f, lineY, w, lineY, telemetryGlowPaint)
            }

            // Glow Radiance stroke
            if (railGlowPct > 0) {
                val glowAlpha = (railGlowPct * 1.5f).toInt().coerceIn(10, 200)
                telemetryGlowPaint.strokeWidth = (railThicknessDp + 2.5f) * d
                telemetryGlowPaint.color = Color.argb(glowAlpha, Color.red(railColor), Color.green(railColor), Color.blue(railColor))
                canvas.drawLine(0f, lineY, w * activeProgress, lineY, telemetryGlowPaint)
            }

            // Core crisp progress line
            telemetryGlowPaint.strokeWidth = railThicknessDp * d
            telemetryGlowPaint.color = railColor
            canvas.drawLine(0f, lineY, w * activeProgress, lineY, telemetryGlowPaint)
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
