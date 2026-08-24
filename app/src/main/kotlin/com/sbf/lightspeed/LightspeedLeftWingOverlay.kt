package com.sbf.lightspeed

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.content.SharedPreferences
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
import com.sbf.lightspeed.system.LightspeedTimeoutEngine
import kotlin.math.abs
import kotlin.math.hypot

class LightspeedLeftWingOverlay(
    context: Context,
    private val service: AccessibilityService
) : View(context) {

    private val prefs = service.getSharedPreferences("default", Context.MODE_PRIVATE)

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

    private val prefListener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
        if (key != null && (key.startsWith("pref_sidebar_") || key.startsWith("pref_section_") || key.startsWith("pref_macro_action_") || key.startsWith("pref_symmetry_"))) {
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

    // 2-Step Inward Scrubbing
    private var isHorizontalEngaged = false
    private var isTwoStepDownwardScrub = false
    private var initialScrubValue = 0
    private var scrubAccumulator = 0f
    private var hudTitle = ""
    private var hudValue = ""

    private val holdRunnable = Runnable {
        if (!isScrubbing && currentGesture != "NONE") {
            isHoldFired = true
            triggerHaptic(40, 200)
            val action = getEffectiveAction(activeZoneKey, currentGesture, true)
            if (action != "none") {
                performActionByName(action)
            }
        }
    }

    private fun triggerHaptic(durationMs: Long = 25, amplitude: Int = 140) {
        try {
            val vibrator = context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
            if (vibrator != null && vibrator.hasVibrator()) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    vibrator.vibrate(VibrationEffect.createOneShot(durationMs, amplitude))
                } else {
                    @Suppress("DEPRECATION")
                    vibrator.vibrate(durationMs)
                }
            }
        } catch (_: Exception) {}
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        val x = event.x
        val y = event.y
        val rawX = event.rawX
        val rawY = event.rawY

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

                val scrubAction = prefs.getString("pref_macro_action_${activeZoneKey}_SCRUBBING", "none") ?: "none"
                scrubType = scrubAction

                uiHandler.removeCallbacks(holdRunnable)
                uiHandler.postDelayed(holdRunnable, 420)
                return true
            }

            MotionEvent.ACTION_MOVE -> {
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

                if (scrubType != "none" && !isTwoStepDownwardScrub) {
                    if (dx > 45f && abs(dx) > abs(dy) * 1.3f) {
                        isHorizontalEngaged = true
                    }
                    if (isHorizontalEngaged && dy > 35f && abs(dy) > abs(dx) * 0.8f) {
                        isTwoStepDownwardScrub = true
                        isScrubbing = true
                        uiHandler.removeCallbacks(holdRunnable)
                        triggerHaptic(30, 180)

                        when (scrubType) {
                            "system:volume" -> {
                                val am = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
                                initialScrubValue = am?.getStreamVolume(AudioManager.STREAM_MUSIC) ?: 0
                                hudTitle = "MEDIA VOLUME"
                            }
                            "system:brightness" -> {
                                initialScrubValue = try {
                                    Settings.System.getInt(context.contentResolver, Settings.System.SCREEN_BRIGHTNESS)
                                } catch (_: Exception) { 128 }
                                hudTitle = "BRIGHTNESS"
                            }
                            "system:screen_timeout" -> {
                                hudTitle = "SCREEN TIMEOUT"
                                hudValue = LightspeedTimeoutEngine.getCurrentFormatted(context)
                            }
                        }
                        invalidate()
                    }
                }

                if (isTwoStepDownwardScrub) {
                    handleScrubMotion(dy)
                    return true
                }

                if (totalDist > 25f) {
                    val prev = currentGesture
                    currentGesture = determineGesture(dx, dy)
                    if (currentGesture != "NONE" && currentGesture != prev) {
                        triggerHaptic(18, 100)
                    }
                }
                return true
            }

            MotionEvent.ACTION_UP -> {
                uiHandler.removeCallbacks(holdRunnable)
                val totalDist = hypot((rawX - startRawX).toDouble(), (rawY - startRawY).toDouble()).toFloat()

                if (isTwoStepDownwardScrub) {
                    isTwoStepDownwardScrub = false
                    isScrubbing = false
                    hudTitle = ""
                    invalidate()
                    return true
                }

                if (!isHoldFired) {
                    if (totalDist < 18f) {
                        handleTap()
                    } else if (currentGesture != "NONE") {
                        val action = getEffectiveAction(activeZoneKey, currentGesture, false)
                        if (action != "none") {
                            triggerHaptic(25, 160)
                            performActionByName(action)
                        }
                    }
                }
                return true
            }

            MotionEvent.ACTION_CANCEL -> {
                uiHandler.removeCallbacks(holdRunnable)
                isTwoStepDownwardScrub = false
                isScrubbing = false
                hudTitle = ""
                invalidate()
                return true
            }
        }
        return true
    }

    private fun determineGesture(dx: Float, dy: Float): String {
        if (initialDominantAxis == "Y") {
            if (lowestYReached < startRawY - 30f) { // Started moving UP
                if (dx > 35f) return "SWIPE_UP_RIGHT"
                if ((rawY - lowestYReached) > 50f) return "SWIPE_UP_DOWN"
                if (dy < -45f) return "SWIPE_UP"
            } else if (highestYReached > startRawY + 30f) { // Started moving DOWN
                if (dx > 35f) return "SWIPE_DOWN_RIGHT"
                if ((highestYReached - rawY) > 50f) return "SWIPE_DOWN_UP"
                if (dy > 45f) return "SWIPE_DOWN"
            }
        } else if (initialDominantAxis == "X") {
            if (highestXReached > startRawX + 30f) {
                if (dy < -35f) return "SWIPE_RIGHT_UP"
                if (dy > 35f) return "SWIPE_RIGHT_DOWN"
                if ((highestXReached - rawX) > 25f) return "SWIPE_RIGHT_BACK"
                if (dx > 45f) return "SWIPE_RIGHT"
            }
        }

        return when {
            dx > 40f && dy < -35f -> "SWIPE_RIGHT_UP"
            dx > 40f && dy > 35f -> "SWIPE_RIGHT_DOWN"
            dy < -35f && dx > 35f -> "SWIPE_UP_RIGHT"
            dy > 35f && dx > 35f -> "SWIPE_DOWN_RIGHT"
            dx > 45f -> "SWIPE_RIGHT"
            dy < -45f -> "SWIPE_UP"
            dy > 45f -> "SWIPE_DOWN"
            else -> "NONE"
        }
    }

    private fun handleTap() {
        val now = System.currentTimeMillis()
        if (now - lastTapTime < 320) {
            pendingTapRunnable?.let { uiHandler.removeCallbacks(it) }
            pendingTapRunnable = null
            lastTapTime = 0
            triggerHaptic(30, 180)
            val action = prefs.getString("pref_macro_action_${activeZoneKey}_DOUBLE_TAP", "none") ?: "none"
            if (action != "none") {
                performActionByName(action)
            }
        } else {
            lastTapTime = now
            pendingTapRunnable = Runnable {
                val action = prefs.getString("pref_macro_action_${activeZoneKey}_TAP", "none") ?: "none"
                if (action != "none") {
                    triggerHaptic(20, 120)
                    performActionByName(action)
                }
            }
            uiHandler.postDelayed(pendingTapRunnable!!, 320)
        }
    }

    private fun handleScrubMotion(dy: Float) {
        val delta = dy - 35f
        when (scrubType) {
            "system:volume" -> {
                val am = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager ?: return
                val maxVol = am.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
                val change = (delta / 45f).toInt()
                val target = (initialScrubValue - change).coerceIn(0, maxVol)
                am.setStreamVolume(AudioManager.STREAM_MUSIC, target, 0)
                hudValue = "$target / $maxVol"
                invalidate()
            }
            "system:brightness" -> {
                val change = (delta * 1.5f).toInt()
                val target = (initialScrubValue - change).coerceIn(5, 255)
                try {
                    Settings.System.putInt(context.contentResolver, Settings.System.SCREEN_BRIGHTNESS, target)
                    hudValue = "${(target * 100 / 255)}%"
                    invalidate()
                } catch (_: Exception) {}
            }
            "system:screen_timeout" -> {
                if (abs(delta - scrubAccumulator) > 70f) {
                    scrubAccumulator = delta
                    triggerHaptic(20, 120)
                    hudValue = LightspeedTimeoutEngine.cycleNext(context)
                    invalidate()
                }
            }
        }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val d = resources.displayMetrics.density

        val isTopExpanded = prefs.getBoolean("pref_section_left_top_expanded", false)
        val isCenterExpanded = prefs.getBoolean("pref_section_left_center_expanded", false)
        val isBottomExpanded = prefs.getBoolean("pref_section_left_bottom_expanded", false)
        val isPreview = prefs.getBoolean("pref_sidebar_left_preview", false)

        val geomMode = prefs.getString("pref_symmetry_geometry_mode", "independent") ?: "independent"
        val isMirroringRight = geomMode == "right"

        val centerTransparency = if (isMirroringRight) prefs.getInt("pref_sidebar_center_transparency", 0) else prefs.getInt("pref_sidebar_left_center_transparency", 0)
        val topTransparency = if (isMirroringRight) prefs.getInt("pref_sidebar_top_transparency", 0) else prefs.getInt("pref_sidebar_left_top_transparency", 0)
        val bottomTransparency = if (isMirroringRight) prefs.getInt("pref_sidebar_bottom_transparency", 0) else prefs.getInt("pref_sidebar_left_bottom_transparency", 0)

        fun drawWingBlade(bounds: RectF, color: Int, transparencyPct: Int, isExpanded: Boolean) {
            val isReview = isExpanded || (isPreview && isExpanded)
            val effectivePct = if (isReview) 100 else transparencyPct
            if (effectivePct <= 0 && !isReview) return

            val alpha = (effectivePct * 2.55f).toInt().coerceIn(40, 255)

            if (isReview) {
                // Active Review Mode: Full Zone Highlight + Crisp White Outline
                highlightPaint.style = Paint.Style.FILL
                highlightPaint.color = Color.argb(120, Color.red(color), Color.green(color), Color.blue(color))
                canvas.drawRoundRect(bounds, 6f * d, 6f * d, highlightPaint)

                highlightPaint.style = Paint.Style.STROKE
                highlightPaint.strokeWidth = 2f * d
                highlightPaint.color = Color.WHITE
                canvas.drawRoundRect(bounds, 6f * d, 6f * d, highlightPaint)
            } else {
                // Resting Mode: Tactical Glowing Laser Blade on the Left Edge
                val bladeW = minOf(bounds.width(), 6f * d)
                val bladeRect = RectF(0f, bounds.top + 2f * d, bladeW, bounds.bottom - 2f * d)

                highlightPaint.style = Paint.Style.FILL
                highlightPaint.color = Color.argb(alpha, Color.red(color), Color.green(color), Color.blue(color))
                canvas.drawRoundRect(bladeRect, 3f * d, 3f * d, highlightPaint)

                highlightPaint.style = Paint.Style.STROKE
                highlightPaint.strokeWidth = 1.2f * d
                highlightPaint.color = Color.argb((alpha * 0.9f).toInt(), 255, 255, 255)
                canvas.drawLine(bladeRect.right, bladeRect.top + 4f * d, bladeRect.right, bladeRect.bottom - 4f * d, highlightPaint)
            }
        }

        val m3Primary = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            context.resources.getColor(android.R.color.system_accent1_600, context.theme)
        } else {
            Color.parseColor("#6750A4")
        }

        val upperColor = Color.rgb(68, 138, 255)
        val coreColor = m3Primary
        val lowerColor = Color.rgb(255, 171, 0)

        drawWingBlade(topTouchBounds, upperColor, topTransparency, isTopExpanded)
        drawWingBlade(centerTouchBounds, coreColor, centerTransparency, isCenterExpanded)
        drawWingBlade(bottomTouchBounds, lowerColor, bottomTransparency, isBottomExpanded)

        if (isScrubbing && hudTitle.isNotEmpty()) {
            val cx = width / 2f
            val cy = height / 2f
            val rectW = 280f
            val rectH = 56f
            val rect = RectF(cx - rectW / 2f, cy - rectH / 2f, cx + rectW / 2f, cy + rectH / 2f)

            canvas.drawRoundRect(rect, 14f, 14f, hudFillPaint)
            canvas.drawRoundRect(rect, 14f, 14f, hudPaint)
            canvas.drawText("$hudTitle: $hudValue", cx, cy + 9f, hudTextPaint)
        }
    }

    private fun getEffectiveAction(zoneKey: String, gesture: String, isHold: Boolean): String {
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
                return "lightspeed:cockpit_hangar"
            }
            val key = if (isHold) "pref_macro_action_${rightZone}_${rightGesture}_HOLD" else "pref_macro_action_${rightZone}_$rightGesture"
            return prefs.getString(key, "none") ?: "none"
        } else {
            val dynamicZone = if (isFlankUnified && (zoneKey == "LEFT_TOP" || zoneKey == "LEFT_BOTTOM")) "LEFT_UNIFIED" else zoneKey
            val key = if (isHold) "pref_macro_action_${dynamicZone}_${gesture}_HOLD" else "pref_macro_action_${dynamicZone}_$gesture"
            return prefs.getString(key, "none") ?: "none"
        }
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
