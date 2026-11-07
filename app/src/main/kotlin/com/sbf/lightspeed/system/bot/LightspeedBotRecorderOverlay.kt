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
import com.sbf.lightspeed.settings.DeckThemeManager
import com.sbf.lightspeed.settings.resolveCanvasPalette

/**
 * Native Full-Screen Overlay for recording per-window / per-app Bot actions and Smart UI Targets.
 * Scans the active screen for Burger Menus, Android 3-Dot Overflow Menus, and clickable UI nodes,
 * rendering tactical target brackets that lock onto screen elements when tapped.
 */
object LightspeedBotRecorderOverlay {

    private const val MAX_RECORDED_STEPS = 40
    private var windowManager: WindowManager? = null
    private var overlayView: View? = null
    val isShowing: Boolean get() = overlayView != null
    val isRecording: Boolean get() = isShowing && LightspeedBotEngine.isRecording
    private var controlPodView: View? = null
    private var recIndicatorView: TextView? = null
    private var windowBadgeView: TextView? = null
    private var saveButtonView: TextView? = null
    private var targetsCanvasView: SmartTargetsCanvasView? = null
    private val recordedSteps = mutableListOf<BotStep>()
    private var detectedTargets = listOf<LightspeedBotScreenScanner.DetectedUiTarget>()
    private var smartDetectEnabled: Boolean = true
    private var lastActionUptime: Long = 0L
    private var lastStepRecordedUptime: Long = 0L
    @Volatile
    private var isForwardingTouch: Boolean = false
    private var isScopeLocked: Boolean = false
    private var lockedScopeLabel: String = ""

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
            val palette = DeckThemeManager.getSnapshot(context).resolveCanvasPalette()
            tagBgPaint.color = palette.glassFillArgb
            for (t in detectedTargets) {
                val accent = if (t.isMenuOrNav) palette.primaryAccentArgb else 0xFFFF9E44.toInt()
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

        // Scan underlying screen elements and lock scope BEFORE adding our overlay window
        val (scopePkg, scopeWin) = LightspeedBotEngine.resolveActiveScope(service)
        LightspeedBotEngine.recordingScopePkg = scopePkg
        LightspeedBotEngine.recordingScopeWindow = scopeWin
        lockedScopeLabel = LightspeedBotEngine.getActiveScopeDisplayLabel(service)
        isScopeLocked = scopePkg != "global" && scopePkg.isNotBlank() && scopeWin.isNotBlank() && scopeWin != "Main"
        detectedTargets = LightspeedBotScreenScanner.scanScreenTargets(service)

        val density = service.resources.displayMetrics.density
        val screenW = service.resources.displayMetrics.widthPixels.toFloat().coerceAtLeast(1f)
        val screenH = service.resources.displayMetrics.heightPixels.toFloat().coerceAtLeast(1f)
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

        // 2. Native Floating Draggable Control Pod (Compact 2-Row Layout)
        val palette = DeckThemeManager.getSnapshot(service).resolveCanvasPalette()
        val controlPod = LinearLayout(service).apply {
            orientation = LinearLayout.VERTICAL
            background = GradientDrawable().apply {
                setColor(0xF412151C.toInt())
                cornerRadius = 14f * density
                setStroke((1.5f * density).toInt(), palette.glassBorderArgb)
            }
            setPadding((12f * density).toInt(), (8f * density).toInt(), (12f * density).toInt(), (8f * density).toInt())
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                elevation = 12f * density
            }
        }
        controlPodView = controlPod

        // Row 1: ⠿ (Drag Handle) + ● REC (N) + 🔒 <Window ID>
        val row1 = LinearLayout(service).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }

        val dragHandle = TextView(service).apply {
            text = "⠿"
            setTextColor(0xFF94A3B8.toInt())
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
            paint.isFakeBoldText = true
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, 0, (6f * density).toInt(), 0)
        }

        val recIndicator = TextView(service).apply {
            text = "● REC (0)"
            setTextColor(0xFFFF4444.toInt())
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 10.5f)
            paint.isFakeBoldText = true
            isSingleLine = true
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, 0, (6f * density).toInt(), 0)
        }
        recIndicatorView = recIndicator

        val windowBadge = TextView(service).apply {
            text = "🔒 $lockedScopeLabel"
            setTextColor(palette.primaryAccentArgb)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 9.5f)
            paint.isFakeBoldText = true
            isSingleLine = true
            ellipsize = android.text.TextUtils.TruncateAt.END
            gravity = Gravity.CENTER_VERTICAL or Gravity.END
        }
        windowBadgeView = windowBadge

        row1.addView(dragHandle, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ))
        row1.addView(recIndicator, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ))
        row1.addView(windowBadge, LinearLayout.LayoutParams(
            0,
            LinearLayout.LayoutParams.WRAP_CONTENT,
            1f
        ))

        // Row 2: Three equal-height non-wrapping pills: [ ⌖ UI: ON/OFF ], [ ⏹ SAVE ], and [ ✕ CANCEL ]
        val row2 = LinearLayout(service).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }

        val detectBg = GradientDrawable().apply {
            setColor(palette.glassFillArgb)
            cornerRadius = 8f * density
            setStroke((1.2f * density).toInt(), palette.primaryAccentArgb)
        }
        val detectBtn = TextView(service).apply {
            background = detectBg
            text = if (smartDetectEnabled) "⌖ UI: ON" else "⌖ UI: OFF"
            setTextColor(if (smartDetectEnabled) palette.primaryAccentArgb else 0xFF94A3B8.toInt())
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 10f)
            paint.isFakeBoldText = true
            isSingleLine = true
            gravity = Gravity.CENTER
            setPadding((4f * density).toInt(), 0, (4f * density).toInt(), 0)
        }

        val saveBg = GradientDrawable().apply {
            setColor(0xEE121418.toInt())
            cornerRadius = 8f * density
            setStroke((1.4f * density).toInt(), 0xFFFF4444.toInt())
        }
        val saveBtn = TextView(service).apply {
            background = saveBg
            text = "⏹ SAVE"
            setTextColor(0xFFFF5555.toInt())
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 10f)
            paint.isFakeBoldText = true
            isSingleLine = true
            gravity = Gravity.CENTER
            setPadding((4f * density).toInt(), 0, (4f * density).toInt(), 0)
        }
        saveButtonView = saveBtn

        val cancelBg = GradientDrawable().apply {
            setColor(palette.glassFillArgb)
            cornerRadius = 8f * density
            setStroke((1.2f * density).toInt(), palette.glassBorderArgb)
        }
        val cancelBtn = TextView(service).apply {
            background = cancelBg
            text = "✕ CANCEL"
            setTextColor(Color.WHITE)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 10f)
            paint.isFakeBoldText = true
            isSingleLine = true
            gravity = Gravity.CENTER
            setPadding((4f * density).toInt(), 0, (4f * density).toInt(), 0)
        }

        val btnH = (32f * density).toInt()
        val btnGap = (5f * density).toInt()

        row2.addView(detectBtn, LinearLayout.LayoutParams(0, btnH, 1f).apply { marginEnd = btnGap })
        row2.addView(saveBtn, LinearLayout.LayoutParams(0, btnH, 1f).apply { marginEnd = btnGap })
        row2.addView(cancelBtn, LinearLayout.LayoutParams(0, btnH, 1f))

        controlPod.addView(row1, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ))
        controlPod.addView(row2, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply { topMargin = (7f * density).toInt() })

        val podWidth = (320f * density).toInt().coerceAtMost((screenW - 20f * density).toInt())
        val podParams = FrameLayout.LayoutParams(
            podWidth,
            FrameLayout.LayoutParams.WRAP_CONTENT
        ).apply {
            gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
            topMargin = (44f * density).toInt()
        }
        root.addView(controlPod, podParams)

        var downX = 0f
        var downY = 0f
        var downTime = 0L

        var isDraggingPod = false
        var podTouchStartX = 0f
        var podTouchStartY = 0f
        var podInitialTranslationX = 0f
        var podInitialTranslationY = 0f
        var hasMovedPod = false

        root.setOnTouchListener { _, event ->
            if (isForwardingTouch) return@setOnTouchListener true

            val podRect = Rect()
            controlPod.getGlobalVisibleRect(podRect)
            val rx = event.rawX.toInt()
            val ry = event.rawY.toInt()

            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    if (podRect.contains(rx, ry)) {
                        isDraggingPod = true
                        podTouchStartX = event.rawX
                        podTouchStartY = event.rawY
                        podInitialTranslationX = controlPod.translationX
                        podInitialTranslationY = controlPod.translationY
                        hasMovedPod = false
                        return@setOnTouchListener true
                    }
                    isDraggingPod = false
                    downX = event.rawX
                    downY = event.rawY
                    downTime = SystemClock.uptimeMillis()
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    if (isDraggingPod) {
                        val dx = event.rawX - podTouchStartX
                        val dy = event.rawY - podTouchStartY
                        if (!hasMovedPod && (Math.hypot(dx.toDouble(), dy.toDouble()) > 6f * density)) {
                            hasMovedPod = true
                        }
                        if (hasMovedPod) {
                            val minX = -(controlPod.left).toFloat()
                            val maxX = (screenW - controlPod.right).toFloat()
                            val minY = -(controlPod.top).toFloat()
                            val maxY = (screenH - controlPod.bottom).toFloat()
                            controlPod.translationX = (podInitialTranslationX + dx).coerceIn(minX, maxX)
                            controlPod.translationY = (podInitialTranslationY + dy).coerceIn(minY, maxY)
                        }
                        return@setOnTouchListener true
                    }
                    true
                }
                MotionEvent.ACTION_UP -> {
                    if (isDraggingPod) {
                        isDraggingPod = false
                        if (!hasMovedPod) {
                            val saveRect = Rect()
                            saveBtn.getGlobalVisibleRect(saveRect)
                            val detectRect = Rect()
                            detectBtn.getGlobalVisibleRect(detectRect)
                            val cancelRect = Rect()
                            cancelBtn.getGlobalVisibleRect(cancelRect)

                            when {
                                saveRect.contains(rx, ry) -> {
                                    LightspeedBotEngine.stopRecording(context, recordedSteps.toList())
                                }
                                detectRect.contains(rx, ry) -> {
                                    smartDetectEnabled = !smartDetectEnabled
                                    detectBtn.text = if (smartDetectEnabled) "⌖ UI: ON" else "⌖ UI: OFF"
                                    detectBtn.setTextColor(if (smartDetectEnabled) palette.primaryAccentArgb else 0xFF94A3B8.toInt())
                                    targetsCanvasView?.invalidate()
                                }
                                cancelRect.contains(rx, ry) -> {
                                    LightspeedBotEngine.stopRecording(context, null)
                                }
                            }
                        }
                        return@setOnTouchListener true
                    }

                    val now = SystemClock.uptimeMillis()
                    if (now - lastStepRecordedUptime < 80L) {
                        return@setOnTouchListener true
                    }
                    if (recordedSteps.size >= MAX_RECORDED_STEPS) {
                        LightspeedBotEngine.stopRecording(context, recordedSteps.toList())
                        return@setOnTouchListener true
                    }

                    val upX = event.rawX
                    val upY = event.rawY
                    val duration = (now - downTime).coerceIn(40L, 1200L)
                    val delay = if (recordedSteps.isEmpty()) 0L else (now - lastActionUptime).coerceIn(120L, 5000L)
                    lastActionUptime = now
                    lastStepRecordedUptime = now

                    val dx = kotlin.math.abs(upX - downX)
                    val dy = kotlin.math.abs(upY - downY)
                    val isSwipe = dx > 30f || dy > 30f

                    passGestureAndRecordStep(
                        service = service,
                        root = root,
                        botId = botId,
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
                MotionEvent.ACTION_CANCEL -> {
                    isDraggingPod = false
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
        controlPodView = null
        recIndicatorView = null
        windowBadgeView = null
        saveButtonView = null
        targetsCanvasView = null
        isScopeLocked = false
        lockedScopeLabel = ""
        LightspeedBotTrackpadOverlay.refreshMacroButtonsState()
    }

    /**
     * Resynchronizes screen UI target detection when navigation drawers, dialogs,
     * or context menus open without aborting active macro recording.
     */
    fun onWindowStateChanged(service: LightspeedAccessibilityService) {
        if (!isShowing) return
        try {
            detectedTargets = LightspeedBotScreenScanner.scanScreenTargets(service)
            targetsCanvasView?.invalidate()
        } catch (_: Exception) {}
    }

    @SuppressLint("SetTextI18n")
    private fun passGestureAndRecordStep(
        service: LightspeedAccessibilityService,
        root: View,
        botId: Int,
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
            val rootNode = LightspeedBotScreenScanner.resolveUnderlyingAppWindow(service)?.root ?: service.rootInActiveWindow
            val hitNode = try {
                LightspeedBotScreenScanner.findDeepestNodeAt(rootNode, downX, downY)
            } catch (_: Exception) {
                null
            }

            // Prefer interactive leaf widget (Button, ImageButton, ImageView, TextView) over parent layout containers
            val effectiveTarget = if (matchedTarget != null && LightspeedBotScreenScanner.isLayoutContainer(matchedTarget.className) &&
                hitNode != null && !LightspeedBotScreenScanner.isLayoutContainer(hitNode.className?.toString())
            ) {
                null
            } else {
                matchedTarget
            }

            val targetNode = if (effectiveTarget == null) hitNode else null
            val viewId = effectiveTarget?.viewIdResourceName ?: targetNode?.viewIdResourceName
            val text = effectiveTarget?.text ?: targetNode?.text?.toString()
            val contentDesc = effectiveTarget?.contentDescription ?: targetNode?.contentDescription?.toString()
            val cls = effectiveTarget?.className ?: targetNode?.className?.toString()

            // Only lock scope on Step 1 if not already locked! NEVER overwrite once locked.
            val tappedPkg = targetNode?.packageName?.toString() ?: effectiveTarget?.packageName
            val ownPkg = service.packageName
            if (!isScopeLocked && !tappedPkg.isNullOrBlank() && tappedPkg != ownPkg && tappedPkg != "com.android.systemui" &&
                !com.sbf.lightspeed.system.LightspeedForegroundTracker.isInputMethodPackage(service, tappedPkg)
            ) {
                LightspeedBotEngine.recordingScopePkg = tappedPkg
                val winId = LightspeedBotScreenScanner.resolveActiveWindowId(service)
                LightspeedBotEngine.recordingScopeWindow = winId
                lockedScopeLabel = LightspeedBotEngine.getActiveScopeDisplayLabel(service)
                isScopeLocked = true
                windowBadgeView?.text = "🔒 $lockedScopeLabel"
            }

            val pkg = LightspeedBotEngine.recordingScopePkg
            val winId = LightspeedBotEngine.recordingScopeWindow
            val tapX = effectiveTarget?.bounds?.centerX()?.toFloat() ?: downX
            val tapY = effectiveTarget?.bounds?.centerY()?.toFloat() ?: downY

            val hasElementMeta = !viewId.isNullOrBlank() || !contentDesc.isNullOrBlank() || !text.isNullOrBlank()
            val isElementBound = !isSwipe && hasElementMeta

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
                    packageName = pkg,
                    useElementTarget = false,
                    windowId = winId
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
                    packageName = pkg,
                    useElementTarget = isElementBound,
                    windowId = winId
                )
            }

            recordedSteps.add(step)
            recIndicatorView?.text = "● REC (${recordedSteps.size})"
            saveButtonView?.text = "⏹ SAVE (${recordedSteps.size})"

            if (isSwipe) {
                LightspeedBotEngine.injectSwipeOrDispatch(service, downX, downY, upX, upY, duration)
            } else {
                LightspeedBotEngine.injectTapOrDispatch(service, tapX, tapY)
            }

            // Immediately restore touch interception within 80ms for multi-step recording
            val settleMs = (if (isSwipe) duration else 30L) + 50L
            root.postDelayed({
                if (overlayView === root) {
                    // Re-scan active window for new Burger Menu / Dialog / Menu items before restoring touchable overlay
                    detectedTargets = LightspeedBotScreenScanner.scanScreenTargets(service)
                    targetsCanvasView?.invalidate()
                    try {
                        lp.flags = lp.flags and WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE.inv()
                        wm.updateViewLayout(root, lp)
                    } catch (_: Exception) {}
                }
                lastStepRecordedUptime = SystemClock.uptimeMillis()
                isForwardingTouch = false
            }, settleMs)
        }, 35L)
    }
}
