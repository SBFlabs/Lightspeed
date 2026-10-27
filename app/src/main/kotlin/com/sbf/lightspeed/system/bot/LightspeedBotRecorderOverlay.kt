package com.sbf.lightspeed.system.bot

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.SystemClock
import android.util.TypedValue
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import com.sbf.lightspeed.LightspeedAccessibilityService

/**
 * Native Full-Screen Overlay for recording per-window / per-app Bot actions and Smart UI Targets.
 * Scans the active screen for Burger Menus, Android 3-Dot Overflow Menus, and clickable UI nodes,
 * rendering tactical target brackets that lock onto screen elements when tapped.
 */
object LightspeedBotRecorderOverlay {

    private const val MAX_RECORDED_STEPS = 40
    private var windowManager: WindowManager? = null
    private var overlayView: View? = null
    private var stopButton: TextView? = null
    private var scopeSubtitleView: TextView? = null
    private var targetsCanvasView: SmartTargetsCanvasView? = null
    private val recordedSteps = mutableListOf<BotStep>()
    private var detectedTargets = listOf<LightspeedBotScreenScanner.DetectedUiTarget>()
    private var smartDetectEnabled: Boolean = true
    private var lastActionUptime: Long = 0L
    private var lastStepRecordedUptime: Long = 0L
    @Volatile
    private var isForwardingTouch: Boolean = false

    private class SmartTargetsCanvasView(context: Context) : View(context) {
        private val density = context.resources.displayMetrics.density
        private val boxPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = 1.6f * density
        }
        private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
        }
        private val tagBgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
            color = 0xDD0B1326.toInt()
        }
        private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = 9.5f * density
            isFakeBoldText = true
            color = Color.WHITE
        }
        private val tmpRectF = RectF()

        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)
            if (!smartDetectEnabled) return
            for (t in detectedTargets) {
                val accent = if (t.isMenuOrNav) 0xFF00E5FF.toInt() else 0xFFFF9E44.toInt()
                tmpRectF.set(
                    t.bounds.left.toFloat(),
                    t.bounds.top.toFloat(),
                    t.bounds.right.toFloat(),
                    t.bounds.bottom.toFloat()
                )
                fillPaint.color = (accent and 0x00FFFFFF) or (0x1E shl 24)
                canvas.drawRoundRect(tmpRectF, 6f * density, 6f * density, fillPaint)

                boxPaint.color = accent
                canvas.drawRoundRect(tmpRectF, 6f * density, 6f * density, boxPaint)

                // Draw compact tactical label pill at top-left of target box
                val labelText = t.label
                val tw = textPaint.measureText(labelText)
                val padH = 5f * density
                val pillH = 14f * density
                val pillTop = (tmpRectF.top - pillH).coerceAtLeast(4f * density)
                val pillRight = (tmpRectF.left + tw + padH * 2f).coerceAtMost(width - 4f * density)
                tmpRectF.set(tmpRectF.left, pillTop, pillRight, pillTop + pillH)
                canvas.drawRoundRect(tmpRectF, 4f * density, 4f * density, tagBgPaint)
                canvas.drawRoundRect(tmpRectF, 4f * density, 4f * density, boxPaint)
                textPaint.color = accent
                canvas.drawText(labelText, tmpRectF.left + padH, pillTop + pillH - 3.5f * density, textPaint)
            }
        }
    }

    @SuppressLint("ClickableViewAccessibility", "SetTextI18n")
    fun show(context: Context, botId: Int) {
        val service = LightspeedAccessibilityService.instance ?: return
        val wm = service.getSystemService(Context.WINDOW_SERVICE) as? WindowManager ?: return
        windowManager = wm
        hide()

        recordedSteps.clear()
        val nowInit = SystemClock.uptimeMillis()
        lastActionUptime = nowInit
        lastStepRecordedUptime = 0L
        isForwardingTouch = false

        // Scan underlying screen elements BEFORE adding our overlay window
        val scopeLabel = LightspeedBotEngine.getActiveScopeDisplayLabel(service)
        detectedTargets = LightspeedBotScreenScanner.scanScreenTargets(service)

        val density = service.resources.displayMetrics.density
        val root = FrameLayout(service)

        // 0. Smart Target Highlight Canvas Layer
        val canvasView = SmartTargetsCanvasView(service)
        targetsCanvasView = canvasView
        root.addView(
            canvasView,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
        )

        // 1. Sleek translucent recording border around the entire display
        val borderDrawable = GradientDrawable().apply {
            setColor(Color.TRANSPARENT)
            setStroke((3f * density).toInt(), 0xCCFF3333.toInt())
        }
        root.background = borderDrawable

        // 2. Native Floating Top Header (Buttons + Per-Window Scope & Target Telemetry)
        val topHeader = LinearLayout(service).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
        }

        val topBar = LinearLayout(service).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
        }

        val pillBg = GradientDrawable().apply {
            setColor(0xEE121418.toInt())
            cornerRadius = 20f * density
            setStroke((1.5f * density).toInt(), 0xFFFF4444.toInt())
        }

        val btn = TextView(service).apply {
            background = pillBg
            text = "■ SAVE B$botId (0)"
            setTextColor(0xFFFF5555.toInt())
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 10.5f)
            paint.isFakeBoldText = true
            gravity = Gravity.CENTER
            setPadding((13f * density).toInt(), (8f * density).toInt(), (13f * density).toInt(), (8f * density).toInt())
        }
        stopButton = btn

        val detectBg = GradientDrawable().apply {
            setColor(0xEE0F1B29.toInt())
            cornerRadius = 20f * density
            setStroke((1.3f * density).toInt(), 0xFF00E5FF.toInt())
        }

        val detectBtn = TextView(service).apply {
            background = detectBg
            text = if (smartDetectEnabled) "⌖ UI DETECT: ON" else "⌖ UI DETECT: OFF"
            setTextColor(if (smartDetectEnabled) 0xFF00E5FF.toInt() else 0xFF94A3B8.toInt())
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 10.5f)
            paint.isFakeBoldText = true
            gravity = Gravity.CENTER
            setPadding((12f * density).toInt(), (8f * density).toInt(), (12f * density).toInt(), (8f * density).toInt())
        }

        val cancelBg = GradientDrawable().apply {
            setColor(0xEE1A1D24.toInt())
            cornerRadius = 20f * density
            setStroke((1.2f * density).toInt(), 0x99FFFFFF.toInt())
        }

        val cancelBtn = TextView(service).apply {
            background = cancelBg
            text = "✕ CANCEL"
            setTextColor(Color.WHITE)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 10.5f)
            paint.isFakeBoldText = true
            gravity = Gravity.CENTER
            setPadding((12f * density).toInt(), (8f * density).toInt(), (12f * density).toInt(), (8f * density).toInt())
        }

        topBar.addView(btn)
        topBar.addView(detectBtn, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply { marginStart = (6f * density).toInt() })
        topBar.addView(cancelBtn, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply { marginStart = (6f * density).toInt() })

        val scopeBadgeBg = GradientDrawable().apply {
            setColor(0xD80A0F1D.toInt())
            cornerRadius = 12f * density
            setStroke((1f * density).toInt(), 0x5500E5FF)
        }
        val scopeBadge = TextView(service).apply {
            background = scopeBadgeBg
            text = "◈ $scopeLabel • ${detectedTargets.size} UI TARGETS"
            setTextColor(0xFFBAE6FD.toInt())
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 9.5f)
            gravity = Gravity.CENTER
            setPadding((10f * density).toInt(), (4f * density).toInt(), (10f * density).toInt(), (4f * density).toInt())
        }
        scopeSubtitleView = scopeBadge

        topHeader.addView(topBar)
        topHeader.addView(scopeBadge, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply { topMargin = (5f * density).toInt() })

        val barParams = FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.WRAP_CONTENT,
            FrameLayout.LayoutParams.WRAP_CONTENT
        ).apply {
            gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
            topMargin = (42f * density).toInt()
        }
        root.addView(topHeader, barParams)

        var downX = 0f
        var downY = 0f
        var downTime = 0L

        root.setOnTouchListener { _, event ->
            if (isForwardingTouch) return@setOnTouchListener true

            val headerRect = Rect()
            topHeader.getGlobalVisibleRect(headerRect)
            if (headerRect.contains(event.rawX.toInt(), event.rawY.toInt())) {
                if (event.actionMasked == MotionEvent.ACTION_UP) {
                    val saveRect = Rect()
                    btn.getGlobalVisibleRect(saveRect)
                    val detectRect = Rect()
                    detectBtn.getGlobalVisibleRect(detectRect)
                    val cancelRect = Rect()
                    cancelBtn.getGlobalVisibleRect(cancelRect)
                    val rx = event.rawX.toInt()
                    val ry = event.rawY.toInt()
                    when {
                        saveRect.contains(rx, ry) -> LightspeedBotEngine.stopRecording(context, recordedSteps.toList())
                        detectRect.contains(rx, ry) -> {
                            smartDetectEnabled = !smartDetectEnabled
                            detectBtn.text = if (smartDetectEnabled) "⌖ UI DETECT: ON" else "⌖ UI DETECT: OFF"
                            detectBtn.setTextColor(if (smartDetectEnabled) 0xFF00E5FF.toInt() else 0xFF94A3B8.toInt())
                            canvasView.invalidate()
                        }
                        cancelRect.contains(rx, ry) -> LightspeedBotEngine.stopRecording(context, null)
                    }
                }
                return@setOnTouchListener true
            }

            val screenW = service.resources.displayMetrics.widthPixels.toFloat().coerceAtLeast(1f)
            val screenH = service.resources.displayMetrics.heightPixels.toFloat().coerceAtLeast(1f)

            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    downX = event.rawX
                    downY = event.rawY
                    downTime = SystemClock.uptimeMillis()
                    true
                }
                MotionEvent.ACTION_UP -> {
                    val now = SystemClock.uptimeMillis()
                    if (now - lastStepRecordedUptime < 240L) {
                        return@setOnTouchListener true
                    }
                    if (recordedSteps.size >= MAX_RECORDED_STEPS) {
                        LightspeedBotEngine.stopRecording(context, recordedSteps.toList())
                        return@setOnTouchListener true
                    }

                    val upX = event.rawX
                    val upY = event.rawY
                    val duration = (now - downTime).coerceIn(40L, 1200L)
                    val delay = (now - lastActionUptime).coerceIn(120L, 5000L)
                    lastActionUptime = now
                    lastStepRecordedUptime = now

                    val dx = kotlin.math.abs(upX - downX)
                    val dy = kotlin.math.abs(upY - downY)
                    val isSwipe = dx > 30f || dy > 30f

                    passGestureAndRecordStep(
                        service = service,
                        root = root,
                        botId = botId,
                        btn = btn,
                        isSwipe = isSwipe,
                        downX = downX,
                        downY = downY,
                        upX = upX,
                        upY = upY,
                        screenW = screenW,
                        screenH = screenH,
                        delay = delay,
                        duration = duration
                    )
                    true
                }
                else -> true
            }
        }

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                    WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        ).apply {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
            }
            gravity = Gravity.TOP or Gravity.START
        }

        try {
            wm.addView(root, params)
            overlayView = root
        } catch (_: Exception) {
            LightspeedBotEngine.stopRecording(context)
        }
    }

    fun hide() {
        isForwardingTouch = false
        overlayView?.let { v ->
            try { windowManager?.removeView(v) } catch (_: Exception) {}
            overlayView = null
        }
        stopButton = null
        scopeSubtitleView = null
        targetsCanvasView = null
    }

    @SuppressLint("SetTextI18n")
    private fun passGestureAndRecordStep(
        service: LightspeedAccessibilityService,
        root: View,
        botId: Int,
        btn: TextView,
        isSwipe: Boolean,
        downX: Float,
        downY: Float,
        upX: Float,
        upY: Float,
        screenW: Float,
        screenH: Float,
        delay: Long,
        duration: Long
    ) {
        val wm = windowManager ?: return
        val lp = root.layoutParams as? WindowManager.LayoutParams ?: return
        isForwardingTouch = true

        // Check pre-scanned smart UI target at touch point first
        val matchedTarget = if (!isSwipe && smartDetectEnabled) {
            LightspeedBotScreenScanner.findTargetAtPoint(detectedTargets, downX, downY)
        } else null

        try {
            lp.flags = lp.flags or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
            wm.updateViewLayout(root, lp)
        } catch (_: Exception) {
            isForwardingTouch = false
            return
        }

        root.postDelayed({
            if (overlayView !== root) {
                isForwardingTouch = false
                return@postDelayed
            }
            val node = if (matchedTarget == null) {
                try {
                    LightspeedBotEngine.findSmallestNodeAt(service.rootInActiveWindow, downX, downY)
                } catch (_: Exception) {
                    null
                }
            } else null

            val viewId = matchedTarget?.viewIdResourceName ?: node?.viewIdResourceName
            val text = matchedTarget?.text ?: node?.text?.toString()
            val contentDesc = matchedTarget?.contentDescription ?: node?.contentDescription?.toString()
            val cls = matchedTarget?.className ?: node?.className?.toString()
            val pkg = matchedTarget?.packageName ?: node?.packageName?.toString()
            val tapX = matchedTarget?.bounds?.centerX()?.toFloat() ?: downX
            val tapY = matchedTarget?.bounds?.centerY()?.toFloat() ?: downY

            val step = if (isSwipe) {
                BotStep(
                    stepType = BotStep.TYPE_SWIPE,
                    xRatio = downX / screenW,
                    yRatio = downY / screenH,
                    endXRatio = upX / screenW,
                    endYRatio = upY / screenH,
                    delayMs = delay,
                    durationMs = duration,
                    viewIdResourceName = viewId,
                    text = text,
                    contentDescription = contentDesc,
                    className = cls,
                    packageName = pkg
                )
            } else {
                BotStep(
                    stepType = BotStep.TYPE_TAP,
                    xRatio = tapX / screenW,
                    yRatio = tapY / screenH,
                    delayMs = delay,
                    durationMs = duration,
                    viewIdResourceName = viewId,
                    text = text,
                    contentDescription = contentDesc,
                    className = cls,
                    packageName = pkg
                )
            }

            recordedSteps.add(step)
            btn.text = "■ SAVE B$botId (${recordedSteps.size})"

            if (isSwipe) {
                LightspeedBotEngine.injectSwipeOrDispatch(service, downX, downY, upX, upY, duration)
            } else {
                LightspeedBotEngine.injectTapOrDispatch(service, tapX, tapY)
            }

            val settleMs = (if (isSwipe) duration else 60L) + 180L
            root.postDelayed({
                if (overlayView === root) {
                    // Re-scan active window for new Burger Menu / Dialog / Menu items before restoring touchable overlay
                    detectedTargets = LightspeedBotScreenScanner.scanScreenTargets(service)
                    val scopeLabel = LightspeedBotEngine.getActiveScopeDisplayLabel(service)
                    scopeSubtitleView?.text = "◈ $scopeLabel • ${detectedTargets.size} UI TARGETS"
                    targetsCanvasView?.invalidate()
                    try {
                        lp.flags = lp.flags and WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE.inv()
                        wm.updateViewLayout(root, lp)
                    } catch (_: Exception) {}
                }
                lastStepRecordedUptime = SystemClock.uptimeMillis()
                isForwardingTouch = false
            }, settleMs)
        }, 55L)
    }
}
