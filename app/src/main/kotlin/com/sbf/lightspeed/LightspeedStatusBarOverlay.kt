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
import android.view.MotionEvent
import android.view.View
import android.view.accessibility.AccessibilityNodeInfo
import com.sbf.lightspeed.system.ActionDispatcher
import com.sbf.lightspeed.system.LightspeedHapticEngine
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
        if (key != null && (key.startsWith("pref_statusbar_") || key.startsWith("pref_section_") || key.startsWith("pref_macro_action_STATUSBAR"))) {
            postInvalidate()
        }
    }

    private val uiHandler = Handler(Looper.getMainLooper())
    private var pendingTapRunnable: Runnable? = null
    private var lastTapTime = 0L

    init {
        isClickable = true
        isFocusable = false
        prefs.registerOnSharedPreferenceChangeListener(prefListener)
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
        pendingTapRunnable?.let { uiHandler.removeCallbacks(it) }
        uiHandler.removeCallbacks(holdRunnable)
    }

    private var startRawX = 0f
    private var startRawY = 0f
    private var startX = 0f
    private var startY = 0f
    private var isScrubbing = false
    private var isHoldFired = false
    private var currentGesture = "NONE"
    private var scrubType = "none"

    // 2-Step Scrubbing State
    private var isHorizontalEngaged = false
    private var isTwoStepDownwardScrub = false
    private var initialScrubValue = 0
    private var scrubAccumulator = 0f
    private var hudTitle = ""
    private var hudValue = ""

    private val holdRunnable = Runnable {
        if (!isScrubbing) {
            val gestureKey = if (currentGesture == "NONE") "TAP" else currentGesture
            val actionKey = "pref_macro_action_STATUSBAR_${gestureKey}_HOLD"
            val action = prefs.getString(actionKey, "none") ?: "none"
            if (action != "none") {
                isHoldFired = true
                if (action == "system:volume" || action == "system:brightness" || action == "system:screen_timeout" || action == "system:scroll_to_top") {
                    scrubType = action
                    isScrubbing = true
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
                startRawX = event.rawX
                startRawY = event.rawY
                startX = event.x
                startY = event.y
                isScrubbing = false
                isHoldFired = false
                isHorizontalEngaged = false
                isTwoStepDownwardScrub = false
                currentGesture = "NONE"
                scrubAccumulator = 0f

                scrubType = prefs.getString("pref_macro_action_STATUSBAR_SCRUBBING", "none") ?: "none"
                if (scrubType == "none") {
                    scrubType = "system:screen_timeout"
                }

                uiHandler.postDelayed(holdRunnable, 450L)
                return true
            }

            MotionEvent.ACTION_MOVE -> {
                val dx = event.x - startX
                val dy = event.y - startY
                val dist = hypot(dx, dy)

                if (dist > threshold * 0.4f && !isHoldFired && !isScrubbing) {
                    uiHandler.removeCallbacks(holdRunnable)
                }

                if (isScrubbing) {
                    handleScrubMove(dy)
                    invalidate()
                    return true
                }

                if (!isScrubbing && !isHorizontalEngaged) {
                    if (abs(dx) > threshold * 0.7f && abs(dx) > abs(dy) * 1.3f) {
                        isHorizontalEngaged = true
                        currentGesture = if (dx > 0) "SWIPE_RIGHT" else "SWIPE_LEFT"
                    }
                }

                if (isHorizontalEngaged && !isScrubbing) {
                    // Rebound detection: finger reversed past origin → upgrade to *_BACK
                    if (currentGesture == "SWIPE_RIGHT" && dx < -(threshold * 0.4f)) {
                        currentGesture = "SWIPE_RIGHT_BACK"
                    } else if (currentGesture == "SWIPE_LEFT" && dx > threshold * 0.4f) {
                        currentGesture = "SWIPE_LEFT_BACK"
                    }
                    if (dy > threshold * 0.9f) {
                        isScrubbing = true
                        isTwoStepDownwardScrub = true
                        triggerHaptic(30, 180)
                        initScrubSession()
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
                    hudTitle = ""
                    hudValue = ""
                    invalidate()
                    return true
                }

                if (isHoldFired) return true

                if (currentGesture != "NONE") {
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
                return true
            }

            MotionEvent.ACTION_CANCEL -> {
                uiHandler.removeCallbacks(holdRunnable)
                isScrubbing = false
                isTwoStepDownwardScrub = false
                isHoldFired = false
                currentGesture = "NONE"
                invalidate()
                return true
            }
        }
        return super.onTouchEvent(event)
    }

    private fun handleTapSequence() {
        val doubleTapAction = prefs.getString("pref_macro_action_STATUSBAR_DOUBLE_TAP", "none") ?: "none"
        val singleTapAction = prefs.getString("pref_macro_action_STATUSBAR_TAP", "system:scroll_to_top") ?: "system:scroll_to_top"
        val now = SystemClock.uptimeMillis()

        if (doubleTapAction != "none") {
            if (now - lastTapTime < 240L) {
                pendingTapRunnable?.let { uiHandler.removeCallbacks(it) }
                pendingTapRunnable = null
                lastTapTime = 0L
                triggerHaptic(30, 180)
                performActionByName(doubleTapAction)
            } else {
                lastTapTime = now
                val tapTask = Runnable {
                    triggerHaptic(20, 120)
                    performActionByName(singleTapAction)
                    pendingTapRunnable = null
                }
                pendingTapRunnable = tapTask
                uiHandler.postDelayed(tapTask, 240L)
            }
        } else {
            triggerHaptic(20, 120)
            performActionByName(singleTapAction)
        }
    }

    private fun initScrubSession() {
        when (scrubType) {
            "system:screen_timeout" -> {
                initialScrubValue = LightspeedTimeoutEngine.getCurrentTimeoutIndex(context)
                hudTitle = "SCREEN TIMEOUT"
                hudValue = LightspeedTimeoutEngine.TIMEOUT_STEPS[initialScrubValue].second
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

    private fun handleScrubMove(dy: Float) {
        val density = resources.displayMetrics.density
        val stepDistance = 24f * density

        when (scrubType) {
            "system:screen_timeout" -> {
                val stepOffset = (dy / stepDistance).toInt()
                val targetIndex = (initialScrubValue + stepOffset).coerceIn(0, LightspeedTimeoutEngine.TIMEOUT_STEPS.lastIndex)
                val (_, label) = LightspeedTimeoutEngine.setStepIndex(context, targetIndex)
                if (hudValue != label) {
                    hudValue = label
                    triggerHaptic(18, 110)
                }
            }
            "system:volume" -> {
                val am = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager ?: return
                val max = am.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
                val stepOffset = (dy / (stepDistance * 1.2f)).toInt()
                val targetVol = (initialScrubValue + stepOffset).coerceIn(0, max)
                am.setStreamVolume(AudioManager.STREAM_MUSIC, targetVol, AudioManager.FLAG_SHOW_UI)
                hudValue = "$targetVol / $max"
            }
            "system:brightness" -> {
                if (Settings.System.canWrite(context)) {
                    val stepOffset = ((dy / (stepDistance * 1.5f)) * 10).toInt()
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
        val previewEnabled = prefs.getBoolean("pref_statusbar_preview", false) ||
                prefs.getBoolean("pref_section_statusbar_expanded", false)
        val transparencyPct = prefs.getInt("pref_statusbar_transparency", 0)
        val d = resources.displayMetrics.density
        val w = width.toFloat()
        val h = height.toFloat()

        if (previewEnabled) {
            // Holographic Cyan Canopy Live Preview
            debugPaint.style = Paint.Style.FILL
            debugPaint.color = Color.argb(120, 0, 229, 255)
            canvas.drawRoundRect(RectF(0f, 0f, w, h), 8f * d, 8f * d, debugPaint)

            debugPaint.style = Paint.Style.STROKE
            debugPaint.strokeWidth = 2f * d
            debugPaint.color = Color.WHITE
            canvas.drawRoundRect(RectF(1f * d, 1f * d, w - 1f * d, h - 1f * d), 8f * d, 8f * d, debugPaint)

            // Center Telemetry Label
            hudTextPaint.textSize = 10f * d
            hudTextPaint.color = Color.WHITE
            canvas.drawText("✦ OVERHEAD CANOPY HUD", w / 2f, (h / 2f) + 3.5f * d, hudTextPaint)
        } else if (transparencyPct > 0) {
            val alpha = (transparencyPct * 2.55f).toInt().coerceIn(10, 255)
            debugPaint.style = Paint.Style.FILL
            debugPaint.color = Color.argb((alpha * 0.4f).toInt(), 0, 229, 255)
            canvas.drawRoundRect(RectF(0f, 0f, w, 4f * d), 2f * d, 2f * d, debugPaint)

            debugPaint.color = Color.argb(alpha, 0, 229, 255)
            canvas.drawRoundRect(RectF(0f, 0f, w, 2.5f * d), 1.5f * d, 1.5f * d, debugPaint)
        }

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
