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
        if (key != null && (key.startsWith("pref_statusbar_") || key.startsWith("pref_horizon_rail_") || key.startsWith("pref_sub_") || key.startsWith("pref_section_") || key.startsWith("pref_macro_action_STATUSBAR") || key.startsWith("pref_telemetry_"))) {
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
        val sensorThicknessDp = prefs.getInt("pref_statusbar_thickness", 48).coerceIn(20, 52)
        val railThicknessDp = prefs.getInt(com.sbf.lightspeed.system.LightspeedPreferences.KEY_HORIZON_RAIL_THICKNESS, 3).coerceIn(1, 8)
        val isRailText = prefs.getBoolean(com.sbf.lightspeed.system.LightspeedPreferences.KEY_HORIZON_RAIL_TEXT_ENABLED, true)
        val railNeededHeightDp = if (isRailText) (railThicknessDp + 22) else (railThicknessDp + 6)
        val effectiveHeightDp = maxOf(sensorThicknessDp, railNeededHeightDp)

        lp.width = screenWidthPx
        lp.height = (effectiveHeightDp * d).toInt()
        lp.x = 0
        lp.y = 0
        lp.gravity = Gravity.TOP or Gravity.START
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
        val isSensorEnabled = prefs.getBoolean("pref_statusbar_enabled", true)
        if (!isSensorEnabled) return false

        val density = resources.displayMetrics.density
        val screenW = resources.displayMetrics.widthPixels.toFloat()
        val spanPref = prefs.getInt("pref_statusbar_span", 1080)
        val spanPx = if (spanPref >= 1000) screenW else (spanPref * density).coerceIn(50f * density, screenW)
        val sensorOffsetX = prefs.getInt("pref_statusbar_offset_x", 0) * density
        val thicknessDp = prefs.getInt("pref_statusbar_thickness", 48).coerceIn(20, 52)
        val sensorHeight = thicknessDp * density
        val sensorOffsetY = (prefs.getInt("pref_statusbar_offset_y", 0) * density).coerceAtLeast(0f)

        val sensorLeft = ((screenW - spanPx) / 2f) + sensorOffsetX
        val sensorRight = sensorLeft + spanPx
        val sensorTop = sensorOffsetY
        val sensorBottom = sensorTop + sensorHeight

        // Only capture touch if it lands inside the Sensor Area bounding rectangle
        if (event.actionMasked == MotionEvent.ACTION_DOWN) {
            if (event.x < sensorLeft || event.x > sensorRight || event.y < sensorTop || event.y > sensorBottom) {
                return false
            }
        }

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
        val isSensorEnabled = prefs.getBoolean("pref_statusbar_enabled", true)
        val isExpanded = prefs.getBoolean("pref_section_statusbar_expanded", true)
        val isPreview  = prefs.getBoolean("pref_statusbar_preview", false)
        val isReview = isSensorEnabled && isExpanded && isPreview
        val transparencyPct = if (isSensorEnabled) prefs.getInt("pref_statusbar_transparency", 0) else 0
        val d = resources.displayMetrics.density
        val screenW = resources.displayMetrics.widthPixels.toFloat()
        val w = width.toFloat()
        val h = height.toFloat()

        val m3Primary = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            context.resources.getColor(android.R.color.system_accent1_300, context.theme)
        } else {
            Color.parseColor("#90CAF9")
        }

        // Sensor Area Geometry
        val spanPref = prefs.getInt("pref_statusbar_span", 1080)
        val spanPx = if (spanPref >= 1000) screenW else (spanPref * d).coerceIn(50f * d, screenW)
        val sensorOffsetX = prefs.getInt("pref_statusbar_offset_x", 0) * d
        val thicknessDp = prefs.getInt("pref_statusbar_thickness", 48).coerceIn(20, 52)
        val sensorHeight = thicknessDp * d
        val sensorOffsetY = (prefs.getInt("pref_statusbar_offset_y", 0) * d).coerceAtLeast(0f)

        val sensorLeft = ((screenW - spanPx) / 2f) + sensorOffsetX
        val sensorRight = sensorLeft + spanPx
        val sensorTop = sensorOffsetY
        val sensorBottom = sensorTop + sensorHeight

        if (isReview) {
            debugPaint.style = Paint.Style.FILL
            debugPaint.color = Color.argb(120, Color.red(m3Primary), Color.green(m3Primary), Color.blue(m3Primary))
            canvas.drawRoundRect(RectF(sensorLeft, sensorTop, sensorRight, sensorBottom), 8f * d, 8f * d, debugPaint)

            debugPaint.style = Paint.Style.STROKE
            debugPaint.strokeWidth = 2f * d
            debugPaint.color = Color.WHITE
            canvas.drawRoundRect(RectF(sensorLeft + 1f * d, sensorTop + 1f * d, sensorRight - 1f * d, sensorBottom - 1f * d), 8f * d, 8f * d, debugPaint)

            // Center Telemetry Label
            hudTextPaint.textSize = 10f * d
            hudTextPaint.color = Color.WHITE
            canvas.drawText("✦ SENSOR AREA (TOP EDGE)", sensorLeft + (spanPx / 2f), sensorTop + (sensorHeight / 2f) + 3.5f * d, hudTextPaint)
        } else if (transparencyPct > 0) {
            val alpha = (transparencyPct * 2.55f).toInt().coerceIn(10, 255)
            debugPaint.style = Paint.Style.FILL
            debugPaint.color = Color.argb((alpha * 0.4f).toInt(), Color.red(m3Primary), Color.green(m3Primary), Color.blue(m3Primary))
            canvas.drawRoundRect(RectF(sensorLeft, sensorTop, sensorRight, sensorTop + 4f * d), 2f * d, 2f * d, debugPaint)

            debugPaint.color = Color.argb(alpha, Color.red(m3Primary), Color.green(m3Primary), Color.blue(m3Primary))
            canvas.drawRoundRect(RectF(sensorLeft, sensorTop, sensorRight, sensorTop + 2.5f * d), 1.5f * d, 1.5f * d, debugPaint)
        }

        // 1. Horizon Rail Telemetry Line & Micro-Text Renderer
        val dlRouting = prefs.getString(com.sbf.lightspeed.system.LightspeedPreferences.KEY_TELEMETRY_DOWNLOADS_ROUTING, "notch_pill") ?: "notch_pill"
        val mediaRouting = prefs.getString(com.sbf.lightspeed.system.LightspeedPreferences.KEY_TELEMETRY_MEDIA_ROUTING, "none") ?: "none"
        val primaryDl = com.sbf.lightspeed.system.LightspeedNotificationListener.getPrimaryDownload()
        val media = com.sbf.lightspeed.system.LightspeedNotificationListener.activeMediaTelemetry

        val screenWidthDp = (screenW / d).toInt()
        val railSpanPref = prefs.getInt(com.sbf.lightspeed.system.LightspeedPreferences.KEY_HORIZON_RAIL_SPAN, screenWidthDp).coerceIn(50, screenWidthDp)
        val railSpanPx = if (railSpanPref >= screenWidthDp - 5) screenW else (railSpanPref * d).coerceIn(50f * d, screenW)
        val railAlign = prefs.getString(com.sbf.lightspeed.system.LightspeedPreferences.KEY_HORIZON_RAIL_ALIGN, "center") ?: "center"
        val railOffsetX = prefs.getInt(com.sbf.lightspeed.system.LightspeedPreferences.KEY_HORIZON_RAIL_OFFSET_X, 0) * d

        val railLeft = when (railAlign) {
            "left" -> (0f + railOffsetX).coerceIn(0f, screenW - railSpanPx)
            "right" -> (screenW - railSpanPx + railOffsetX).coerceIn(0f, screenW - railSpanPx)
            else -> (((screenW - railSpanPx) / 2f) + railOffsetX).coerceIn(0f, screenW - railSpanPx)
        }
        val railRight = railLeft + railSpanPx

        val railThicknessDp = prefs.getInt(com.sbf.lightspeed.system.LightspeedPreferences.KEY_HORIZON_RAIL_THICKNESS, 3).coerceIn(1, 8)
        val railGlowPct = prefs.getInt(com.sbf.lightspeed.system.LightspeedPreferences.KEY_HORIZON_RAIL_GLOW, 80).coerceIn(0, 100)
        val railTrackOpacityPct = prefs.getInt(com.sbf.lightspeed.system.LightspeedPreferences.KEY_HORIZON_RAIL_TRACK_OPACITY, 20).coerceIn(0, 100)

        // Color Resolution (4 modes: app_icon, material3, inverted, custom)
        val colorMode = prefs.getString(com.sbf.lightspeed.system.LightspeedPreferences.KEY_HORIZON_RAIL_COLOR_MODE, "app_icon") ?: "app_icon"
        val railColor = when (colorMode) {
            "app_icon" -> {
                val iconCol = when {
                    (dlRouting == "top_line" || dlRouting == "both") && primaryDl?.iconColor != null -> primaryDl.iconColor
                    (mediaRouting == "top_line" || mediaRouting == "both") && media?.iconColor != null -> media.iconColor
                    else -> null
                }
                iconCol ?: m3Primary
            }
            "material3" -> m3Primary
            "inverted" -> {
                val isNight = (resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK) == android.content.res.Configuration.UI_MODE_NIGHT_YES
                if (isNight) Color.WHITE else Color.BLACK
            }
            "custom" -> {
                val hex = prefs.getString(com.sbf.lightspeed.system.LightspeedPreferences.KEY_HORIZON_RAIL_CUSTOM_COLOR, "#00E5FF") ?: "#00E5FF"
                try { Color.parseColor(hex) } catch (_: Exception) { Color.parseColor("#00E5FF") }
            }
            else -> m3Primary
        }

        val lineY = (railThicknessDp * d) / 2f

        val isDlActive = (dlRouting == "top_line" || dlRouting == "both") && primaryDl != null && (primaryDl.progressFraction >= 0f || primaryDl.isIndeterminate)
        val isMediaActive = (mediaRouting == "top_line" || mediaRouting == "both") && media != null && media.isPlaying && media.durationMs > 0

        val activeProgress = when {
            isDlActive && primaryDl!!.progressFraction >= 0f -> primaryDl.progressFraction.coerceIn(0f, 1f)
            isDlActive && primaryDl!!.isIndeterminate -> 1f
            isMediaActive -> (media!!.positionMs.toFloat() / media.durationMs.toFloat()).coerceIn(0f, 1f)
            else -> -1f
        }

        if (activeProgress >= 0f) {
            // Background inactive track rail
            if (railTrackOpacityPct > 0) {
                val trackAlpha = (railTrackOpacityPct * 2.55f).toInt().coerceIn(10, 255)
                telemetryGlowPaint.strokeWidth = railThicknessDp * d
                telemetryGlowPaint.color = Color.argb(trackAlpha, Color.red(railColor), Color.green(railColor), Color.blue(railColor))
                canvas.drawLine(railLeft, lineY, railRight, lineY, telemetryGlowPaint)
            }

            // Glow Radiance stroke
            if (railGlowPct > 0) {
                val glowAlpha = (railGlowPct * 1.5f).toInt().coerceIn(10, 200)
                telemetryGlowPaint.strokeWidth = (railThicknessDp + 2.5f) * d
                telemetryGlowPaint.color = Color.argb(glowAlpha, Color.red(railColor), Color.green(railColor), Color.blue(railColor))
                val progressX = railLeft + (railSpanPx * activeProgress)
                canvas.drawLine(railLeft, lineY, progressX, lineY, telemetryGlowPaint)
            }

            // Core crisp progress line
            telemetryGlowPaint.strokeWidth = railThicknessDp * d
            telemetryGlowPaint.color = railColor
            val progressX = railLeft + (railSpanPx * activeProgress)
            canvas.drawLine(railLeft, lineY, progressX, lineY, telemetryGlowPaint)

            // Micro-Text Telemetry Ticker through the rail span
            val isTextEnabled = prefs.getBoolean(com.sbf.lightspeed.system.LightspeedPreferences.KEY_HORIZON_RAIL_TEXT_ENABLED, true)
            if (isTextEnabled) {
                val tickerText = when {
                    isDlActive && primaryDl != null -> {
                        val pctStr = if (primaryDl.progressFraction >= 0f) "${(primaryDl.progressFraction * 100).toInt()}%" else "DOWNLOADING"
                        "⬇ ${primaryDl.title.uppercase()}  •  $pctStr"
                    }
                    isMediaActive && media != null -> {
                        "♫ ${media.title.uppercase()} — ${media.artist.uppercase()}"
                    }
                    else -> null
                }

                if (!tickerText.isNullOrBlank()) {
                    val textSizeDp = prefs.getInt(com.sbf.lightspeed.system.LightspeedPreferences.KEY_HORIZON_RAIL_TEXT_SIZE, 8).coerceIn(6, 12).toFloat()
                    val microTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                        textSize = textSizeDp * d
                        typeface = android.graphics.Typeface.create(android.graphics.Typeface.MONOSPACE, android.graphics.Typeface.BOLD)
                        letterSpacing = 0.06f
                        color = if (colorMode == "inverted") railColor else Color.WHITE
                        setShadowLayer(2f * d, 0f, 0f, Color.BLACK)
                    }

                    val textPos = prefs.getString(com.sbf.lightspeed.system.LightspeedPreferences.KEY_HORIZON_RAIL_TEXT_POSITION, "below") ?: "below"
                    val textY = when (textPos) {
                        "above" -> (lineY - (railThicknessDp * d / 2f) - 1f * d).coerceAtLeast(textSizeDp * d)
                        "embedded" -> lineY + (textSizeDp * d * 0.35f)
                        else -> lineY + (railThicknessDp * d / 2f) + (textSizeDp * d) + 1f * d
                    }

                    val textWidth = microTextPaint.measureText(tickerText)
                    val speedDp = prefs.getInt(com.sbf.lightspeed.system.LightspeedPreferences.KEY_HORIZON_RAIL_TEXT_SPEED, 25).coerceIn(10, 80).toFloat()
                    val speedPx = speedDp * d

                    canvas.save()
                    canvas.clipRect(railLeft, 0f, railRight, h)

                    if (textWidth <= railSpanPx) {
                        // Fits inside rail span: center text
                        val startX = railLeft + (railSpanPx - textWidth) / 2f
                        canvas.drawText(tickerText, startX, textY, microTextPaint)
                    } else {
                        // Overflow: smooth continuous marquee scroll across the span
                        val totalCycleDistance = textWidth + 60f * d
                        val cycleDurationMs = ((totalCycleDistance / speedPx) * 1000f).toLong().coerceAtLeast(1000L)
                        val elapsedMs = SystemClock.uptimeMillis() % cycleDurationMs
                        val offset = (elapsedMs.toFloat() / cycleDurationMs.toFloat()) * totalCycleDistance
                        val textX = railLeft + railSpanPx - offset

                        canvas.drawText(tickerText, textX, textY, microTextPaint)
                        // Draw looping second instance if gap appears
                        if (textX + textWidth < railRight) {
                            canvas.drawText(tickerText, textX + totalCycleDistance, textY, microTextPaint)
                        }
                        postInvalidateOnAnimation()
                    }
                    canvas.restore()
                }
            }
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
