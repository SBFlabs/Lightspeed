package com.sbf.lightspeed

import android.app.DownloadManager
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.graphics.*
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.text.TextPaint
import android.text.TextUtils
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import com.sbf.lightspeed.system.LightspeedHapticEngine
import com.sbf.lightspeed.system.LightspeedMediaManager
import com.sbf.lightspeed.system.LightspeedNotificationListener
import com.sbf.lightspeed.system.LightspeedPreferences
import com.sbf.lightspeed.system.defaultPrefs
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.max

/**
 * Dedicated, unclipped Camera Cutout Notch Pill overlay window for Lightspeed.
 *
 * Implements Dynamic Notch Geometry wrapping the physical camera cutout,
 * a Dual-Wing layout with a physical camera exclusion dead-zone,
 * a Title Marquee & Truncation engine, and an interactive liquid-glass Mini-Player card.
 */
class LightspeedNotchOverlay(context: Context) : View(context) {

    private val prefs = context.defaultPrefs()
    private val mainHandler = Handler(Looper.getMainLooper())

    private val telemetryPillFillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }

    private val telemetryPillRimPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
    }

    private val hudTextPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textAlign = Paint.Align.LEFT
        isFakeBoldText = true
    }

    private val glyphTextPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textAlign = Paint.Align.CENTER
        isFakeBoldText = true
    }

    private val subTextPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.LTGRAY
        textAlign = Paint.Align.LEFT
    }

    private val progressBarBgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = Color.argb(60, 255, 255, 255)
    }

    private val progressBarFillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }

    private val buttonBgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = Color.argb(80, 255, 255, 255)
    }

    // Marquee State Tracking
    private var currentTextKey = ""
    private var marqueeScrollOffset = 0f
    private var marqueeCompletedLoops = 0
    private var lastMarqueeFrameTime = 0L

    // Expansion State & Bounds
    var isExpanded = false
        private set

    val collapsedPillBounds = RectF()
    private val expandedCardBounds = RectF()

    // Transport button touch targets
    private val btnPrevBounds = RectF()
    private val btnPlayPauseBounds = RectF()
    private val btnNextBounds = RectF()
    private val btnCloseBounds = RectF()

    // Touch interaction tracking
    private var touchDownX = 0f
    private var touchDownY = 0f
    private var touchDownTime = 0L
    private var isLongPressDispatched = false

    private val longPressRunnable = Runnable {
        isLongPressDispatched = true
        LightspeedHapticEngine.tick(context)
        performLongPressAction()
    }

    private val autoCollapseRunnable = Runnable {
        if (isExpanded) {
            collapseCard()
        }
    }

    private val prefListener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
        if (key != null && (key.startsWith("pref_notch_") || key.startsWith("pref_telemetry_") || key == "pref_statusbar_enabled")) {
            currentTextKey = ""
            marqueeScrollOffset = 0f
            marqueeCompletedLoops = 0
            postInvalidate()
        }
    }

    init {
        isClickable = true
        isFocusable = false
        prefs.registerOnSharedPreferenceChangeListener(prefListener)

        LightspeedNotificationListener.onTelemetryChanged = {
            mainHandler.post {
                postInvalidate()
            }
        }
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        prefs.unregisterOnSharedPreferenceChangeListener(prefListener)
        LightspeedNotificationListener.onTelemetryChanged = null
        mainHandler.removeCallbacksAndMessages(null)
    }

    fun updateNotchMetrics() {
        postInvalidate()
    }

    fun expandCard() {
        if (isExpanded) return
        isExpanded = true
        LightspeedHapticEngine.tick(context)
        resetAutoCollapseTimer()
        notifyWindowLayoutChanged()
        postInvalidate()
    }

    fun collapseCard() {
        if (!isExpanded) return
        isExpanded = false
        mainHandler.removeCallbacks(autoCollapseRunnable)
        LightspeedHapticEngine.tick(context)
        notifyWindowLayoutChanged()
        postInvalidate()
    }

    private fun resetAutoCollapseTimer() {
        mainHandler.removeCallbacks(autoCollapseRunnable)
        mainHandler.postDelayed(autoCollapseRunnable, 4000L)
    }

    private fun notifyWindowLayoutChanged() {
        mainHandler.post {
            val d = resources.displayMetrics.density
            val screenW = resources.displayMetrics.widthPixels
            if (isExpanded) {
                val cardW = (320 * d).toInt().coerceAtMost(screenW - (16 * d).toInt())
                val cardH = (120 * d).toInt()
                val targetX = (screenW - cardW) / 2
                val targetY = collapsedPillBounds.top.toInt().coerceAtLeast(0)
                LightspeedAccessibilityService.instance?.updateNotchWindowBounds(true, targetX, targetY, cardW, cardH)
            } else {
                val pillW = (collapsedPillBounds.width()).toInt().coerceAtLeast((120 * d).toInt())
                val pillH = (collapsedPillBounds.height()).toInt().coerceAtLeast((36 * d).toInt())
                val targetX = collapsedPillBounds.left.toInt().coerceAtLeast(0)
                val targetY = collapsedPillBounds.top.toInt().coerceAtLeast(0)
                LightspeedAccessibilityService.instance?.updateNotchWindowBounds(false, targetX, targetY, pillW, pillH)
            }
        }
    }

    private fun performLongPressAction() {
        val primaryDl = LightspeedNotificationListener.getPrimaryDownload()
        val media = LightspeedNotificationListener.activeMediaTelemetry

        if (media != null && media.packageName != null) {
            try {
                val intent = context.packageManager.getLaunchIntentForPackage(media.packageName)
                if (intent != null) {
                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    context.startActivity(intent)
                    return
                }
            } catch (_: Exception) {}
        }

        if (primaryDl != null) {
            try {
                val intent = (primaryDl.packageName?.let { context.packageManager.getLaunchIntentForPackage(it) }
                    ?: Intent(DownloadManager.ACTION_VIEW_DOWNLOADS)).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(intent)
            } catch (_: Exception) {}
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        val x = event.x
        val y = event.y

        if (!isExpanded) {
            // Collapsed Pill Touch Detection
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    touchDownX = x
                    touchDownY = y
                    touchDownTime = SystemClock.uptimeMillis()
                    isLongPressDispatched = false
                    mainHandler.removeCallbacks(longPressRunnable)
                    mainHandler.postDelayed(longPressRunnable, ViewConfiguration.getLongPressTimeout().toLong())
                    return true
                }
                MotionEvent.ACTION_MOVE -> {
                    if (hypot(x - touchDownX, y - touchDownY) > 15f * resources.displayMetrics.density) {
                        mainHandler.removeCallbacks(longPressRunnable)
                    }
                    return true
                }
                MotionEvent.ACTION_UP -> {
                    mainHandler.removeCallbacks(longPressRunnable)
                    if (!isLongPressDispatched) {
                        val duration = SystemClock.uptimeMillis() - touchDownTime
                        if (duration < ViewConfiguration.getLongPressTimeout()) {
                            expandCard()
                        }
                    }
                    return true
                }
                MotionEvent.ACTION_CANCEL -> {
                    mainHandler.removeCallbacks(longPressRunnable)
                    return true
                }
            }
        } else {
            // Expanded Card Touch Detection
            resetAutoCollapseTimer()

            if (event.action == MotionEvent.ACTION_UP) {
                if (btnCloseBounds.contains(x, y)) {
                    collapseCard()
                    return true
                }
                if (btnPrevBounds.contains(x, y)) {
                    LightspeedHapticEngine.tick(context)
                    LightspeedMediaManager.previous(context)
                    return true
                }
                if (btnPlayPauseBounds.contains(x, y)) {
                    LightspeedHapticEngine.tick(context)
                    LightspeedMediaManager.playPause(context)
                    return true
                }
                if (btnNextBounds.contains(x, y)) {
                    LightspeedHapticEngine.tick(context)
                    LightspeedMediaManager.next(context)
                    return true
                }
                if (!expandedCardBounds.contains(x, y)) {
                    collapseCard()
                    return true
                }
            }
            return true
        }

        return super.onTouchEvent(event)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        val d = resources.displayMetrics.density
        val w = width.toFloat()
        if (w <= 0f) return

        val m3Primary = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            context.resources.getColor(android.R.color.system_accent1_300, context.theme)
        } else {
            Color.parseColor("#90CAF9")
        }

        val dlRouting = prefs.getString(LightspeedPreferences.KEY_TELEMETRY_DOWNLOADS_ROUTING, "notch_pill") ?: "notch_pill"
        val mediaRouting = prefs.getString(LightspeedPreferences.KEY_TELEMETRY_MEDIA_ROUTING, "none") ?: "none"
        val primaryDl = LightspeedNotificationListener.getPrimaryDownload()
        val media = LightspeedNotificationListener.activeMediaTelemetry

        val isTestBeacon = prefs.getBoolean(LightspeedPreferences.KEY_NOTCH_TEST_BEACON, false)
        val showDlPill = (dlRouting == "notch_pill" || dlRouting == "both") && primaryDl != null
        val showMediaPill = (mediaRouting == "notch_pill" || mediaRouting == "both") && media != null && media.isPlaying

        if (!isTestBeacon && !showDlPill && !showMediaPill && !isExpanded) {
            return
        }

        // Notch Calibration Parameters
        val offsetX = prefs.getInt(LightspeedPreferences.KEY_NOTCH_OFFSET_X, 0) * d
        val offsetY = prefs.getInt(LightspeedPreferences.KEY_NOTCH_OFFSET_Y, 0) * d
        val expansionW = prefs.getInt(LightspeedPreferences.KEY_NOTCH_EXPANSION_WIDTH, 0) * d

        val cutout = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) rootWindowInsets?.displayCutout else null
        val topCutoutRect = cutout?.boundingRectTop ?: cutout?.boundingRects?.firstOrNull { it.top == 0 }

        val cutoutW = (topCutoutRect?.width()?.toFloat() ?: (28f * d)).coerceAtLeast(24f * d)
        val cutoutH = (topCutoutRect?.height()?.toFloat() ?: (28f * d)).coerceAtLeast(24f * d)
        val cutoutCenterX = (if (topCutoutRect != null && topCutoutRect.width() > 0) topCutoutRect.exactCenterX() else w / 2f) + offsetX
        val cutoutTopY = (if (topCutoutRect != null) topCutoutRect.top.toFloat() else 0f) + offsetY

        val cutoutLeft = cutoutCenterX - cutoutW / 2f
        val cutoutRight = cutoutCenterX + cutoutW / 2f

        // Dynamic Height Binding: max(cutout.height() + 12dp, 36dp)
        val pillH = max(cutoutH + 12f * d, 36f * d)
        val pillCy = cutoutTopY + (cutoutH / 2f).coerceAtLeast(14f * d)
        val pillTop = (pillCy - pillH / 2f).coerceAtLeast(0f)
        val pillBottom = pillTop + pillH

        if (isExpanded) {
            // =========================================================================
            // INTERACTIVE EXPANDED LIQUID-GLASS MINI-PLAYER / TELEMETRY CARD
            // =========================================================================
            val cardW = (320f * d).coerceAtMost(w - 16f * d)
            val cardH = 116f * d
            val cardLeft = (w - cardW) / 2f
            val cardTop = pillTop
            expandedCardBounds.set(cardLeft, cardTop, cardLeft + cardW, cardTop + cardH)

            // Liquid-Glass M3 Card Background & Glow
            telemetryPillFillPaint.color = Color.argb(240, 14, 18, 28)
            canvas.drawRoundRect(expandedCardBounds, 22f * d, 22f * d, telemetryPillFillPaint)

            telemetryPillRimPaint.strokeWidth = 1.4f * d
            telemetryPillRimPaint.color = Color.argb(180, Color.red(m3Primary), Color.green(m3Primary), Color.blue(m3Primary))
            canvas.drawRoundRect(expandedCardBounds, 22f * d, 22f * d, telemetryPillRimPaint)

            // Close Button [✕] at Top Right
            val closeSize = 22f * d
            btnCloseBounds.set(expandedCardBounds.right - closeSize - 12f * d, expandedCardBounds.top + 10f * d, expandedCardBounds.right - 12f * d, expandedCardBounds.top + 10f * d + closeSize)
            canvas.drawCircle(btnCloseBounds.centerX(), btnCloseBounds.centerY(), closeSize / 2f, buttonBgPaint)
            glyphTextPaint.textSize = 11f * d
            glyphTextPaint.color = Color.WHITE
            canvas.drawText("✕", btnCloseBounds.centerX(), btnCloseBounds.centerY() + 4f * d, glyphTextPaint)

            if (showMediaPill || media != null) {
                val med = media ?: LightspeedNotificationListener.activeMediaTelemetry
                val title = med?.title ?: "Active Media"
                val artist = med?.artist ?: "Now Playing"
                val isPlaying = med?.isPlaying ?: true

                // Header Badge
                hudTextPaint.textSize = 9.5f * d
                hudTextPaint.color = m3Primary
                canvas.drawText("♫ NOW PLAYING", cardLeft + 16f * d, cardTop + 20f * d, hudTextPaint)

                // Track Title (Truncated if wide)
                hudTextPaint.textSize = 13.5f * d
                hudTextPaint.color = Color.WHITE
                val maxTitleW = cardW - 60f * d
                val ellipsizedTitle = TextUtils.ellipsize(title, hudTextPaint, maxTitleW, TextUtils.TruncateAt.END).toString()
                canvas.drawText(ellipsizedTitle, cardLeft + 16f * d, cardTop + 38f * d, hudTextPaint)

                // Artist
                subTextPaint.textSize = 11f * d
                subTextPaint.color = Color.argb(200, 255, 255, 255)
                val ellipsizedArtist = TextUtils.ellipsize(artist, subTextPaint, maxTitleW, TextUtils.TruncateAt.END).toString()
                canvas.drawText(ellipsizedArtist, cardLeft + 16f * d, cardTop + 54f * d, subTextPaint)

                // Progress Bar & Duration
                val progY = cardTop + 68f * d
                val progW = cardW - 32f * d
                val progRect = RectF(cardLeft + 16f * d, progY, cardLeft + 16f * d + progW, progY + 3.5f * d)
                canvas.drawRoundRect(progRect, 2f * d, 2f * d, progressBarBgPaint)

                val fraction = if ((med?.durationMs ?: 0L) > 0L) {
                    (med!!.positionMs.toFloat() / med.durationMs.toFloat()).coerceIn(0f, 1f)
                } else 0.4f

                val fillRect = RectF(progRect.left, progRect.top, progRect.left + progW * fraction, progRect.bottom)
                progressBarFillPaint.color = m3Primary
                canvas.drawRoundRect(fillRect, 2f * d, 2f * d, progressBarFillPaint)

                // Transport Controls: [ ⏮ ] [ ⏯ ] [ ⏭ ]
                val btnY = cardTop + 92f * d
                val btnR = 14f * d
                val cX = cardLeft + cardW / 2f

                btnPrevBounds.set(cX - 45f * d - btnR, btnY - btnR, cX - 45f * d + btnR, btnY + btnR)
                btnPlayPauseBounds.set(cX - btnR - 2f * d, btnY - btnR - 2f * d, cX + btnR + 2f * d, btnY + btnR + 2f * d)
                btnNextBounds.set(cX + 45f * d - btnR, btnY - btnR, cX + 45f * d + btnR, btnY + btnR)

                canvas.drawCircle(btnPrevBounds.centerX(), btnPrevBounds.centerY(), btnR, buttonBgPaint)
                canvas.drawCircle(btnPlayPauseBounds.centerX(), btnPlayPauseBounds.centerY(), btnR + 2f * d, buttonBgPaint)
                canvas.drawCircle(btnNextBounds.centerX(), btnNextBounds.centerY(), btnR, buttonBgPaint)

                glyphTextPaint.textSize = 12f * d
                canvas.drawText("⏮", btnPrevBounds.centerX(), btnPrevBounds.centerY() + 4f * d, glyphTextPaint)
                canvas.drawText(if (isPlaying) "⏸" else "▶", btnPlayPauseBounds.centerX(), btnPlayPauseBounds.centerY() + 4.5f * d, glyphTextPaint)
                canvas.drawText("⏭", btnNextBounds.centerX(), btnNextBounds.centerY() + 4f * d, glyphTextPaint)
            } else if (showDlPill && primaryDl != null) {
                // Header Badge
                hudTextPaint.textSize = 9.5f * d
                hudTextPaint.color = m3Primary
                canvas.drawText("⬇ DOWNLOADING", cardLeft + 16f * d, cardTop + 22f * d, hudTextPaint)

                // File Name
                hudTextPaint.textSize = 13.5f * d
                hudTextPaint.color = Color.WHITE
                val maxTitleW = cardW - 60f * d
                val ellipsizedTitle = TextUtils.ellipsize(primaryDl.title, hudTextPaint, maxTitleW, TextUtils.TruncateAt.END).toString()
                canvas.drawText(ellipsizedTitle, cardLeft + 16f * d, cardTop + 42f * d, hudTextPaint)

                // Percentage / Status Text
                val pctStr = if (primaryDl.isIndeterminate) "Downloading in progress…" else "${(primaryDl.progressFraction * 100).toInt()}% • Active"
                subTextPaint.textSize = 11.5f * d
                subTextPaint.color = Color.argb(200, 255, 255, 255)
                canvas.drawText(pctStr, cardLeft + 16f * d, cardTop + 60f * d, subTextPaint)

                // Animated Progress Bar
                val progY = cardTop + 78f * d
                val progW = cardW - 32f * d
                val progRect = RectF(cardLeft + 16f * d, progY, cardLeft + 16f * d + progW, progY + 4f * d)
                canvas.drawRoundRect(progRect, 2f * d, 2f * d, progressBarBgPaint)

                val fraction = if (primaryDl.isIndeterminate) {
                    ((SystemClock.uptimeMillis() % 1500L) / 1500f)
                } else primaryDl.progressFraction.coerceIn(0f, 1f)

                val fillRect = RectF(progRect.left, progRect.top, progRect.left + progW * fraction, progRect.bottom)
                progressBarFillPaint.color = m3Primary
                canvas.drawRoundRect(fillRect, 2f * d, 2f * d, progressBarFillPaint)
            }
            return
        }

        // =========================================================================
        // COLLAPSED DUAL-WING NOTCH PILL RENDERING
        // =========================================================================
        val scrollMode = prefs.getString(LightspeedPreferences.KEY_NOTCH_TEXT_SCROLL_MODE, "loop_2x") ?: "loop_2x"
        val truncateAnchor = prefs.getString(LightspeedPreferences.KEY_NOTCH_TEXT_TRUNCATE_ANCHOR, "tail") ?: "tail"

        // Layout Dimensions for Dual-Wing
        val leftWingW = 32f * d
        val rightWingBaseW = if (isTestBeacon) 110f * d else if (showDlPill) 100f * d else 135f * d
        val rightWingW = (rightWingBaseW + expansionW).coerceIn(70f * d, 240f * d)

        val pillLeft = cutoutLeft - leftWingW - 6f * d
        val pillRight = cutoutRight + 6f * d + rightWingW + 8f * d
        collapsedPillBounds.set(pillLeft, pillTop, pillRight, pillBottom)

        // 1. Draw Liquid-Glass Capsule Pill Background
        telemetryPillFillPaint.color = Color.argb(235, 14, 18, 28)
        canvas.drawRoundRect(collapsedPillBounds, pillH / 2f, pillH / 2f, telemetryPillFillPaint)

        // 2. Draw Capsule Rim Border
        telemetryPillRimPaint.strokeWidth = 1.3f * d
        telemetryPillRimPaint.color = Color.argb(160, Color.red(m3Primary), Color.green(m3Primary), Color.blue(m3Primary))
        canvas.drawRoundRect(collapsedPillBounds, pillH / 2f, pillH / 2f, telemetryPillRimPaint)

        // 3. Left Wing (Telemetry Icon): centered in Left Wing zone [pillLeft to cutoutLeft]
        val leftWingCenterX = (pillLeft + cutoutLeft) / 2f
        glyphTextPaint.textSize = 10.5f * d
        glyphTextPaint.color = m3Primary

        val iconGlyph = when {
            isTestBeacon -> "✦"
            showDlPill -> "⬇"
            showMediaPill -> "♫"
            else -> "✦"
        }
        canvas.drawText(iconGlyph, leftWingCenterX, pillCy + (3.8f * d), glyphTextPaint)

        // 4. Center Exclusion Dead-Zone: [cutoutLeft to cutoutRight] -> ZERO text rendered!

        // 5. Right Wing (Title / Marquee): Left-aligned from [cutoutRight + 6dp] to [pillRight - 8dp]
        val rightWingLeft = cutoutRight + 6f * d
        val rightWingRight = pillRight - 8f * d
        val rightWingAvailableW = (rightWingRight - rightWingLeft).coerceAtLeast(20f * d)

        val rawTitleText = when {
            isTestBeacon -> "ALIGNMENT BEACON ✦"
            showDlPill -> {
                if (primaryDl!!.isIndeterminate) "DOWNLOADING…" else "${(primaryDl.progressFraction * 100).toInt()}% • ${primaryDl.title}"
            }
            showMediaPill -> media!!.title
            else -> ""
        }

        if (rawTitleText.isBlank()) return

        hudTextPaint.textSize = 10f * d
        hudTextPaint.color = if (isTestBeacon) m3Primary else Color.WHITE

        val textW = hudTextPaint.measureText(rawTitleText)
        val separator = "   •   "
        val separatorW = hudTextPaint.measureText(separator)
        val cycleLength = textW + separatorW

        if (textW <= rightWingAvailableW) {
            // Text fits cleanly without marquee
            canvas.drawText(rawTitleText, rightWingLeft, pillCy + (3.6f * d), hudTextPaint)
        } else {
            // Text overflows available Right Wing space -> Execute Marquee Engine
            if (rawTitleText != currentTextKey) {
                currentTextKey = rawTitleText
                marqueeScrollOffset = 0f
                marqueeCompletedLoops = 0
                lastMarqueeFrameTime = SystemClock.uptimeMillis()
            }

            val now = SystemClock.uptimeMillis()
            val dt = if (lastMarqueeFrameTime > 0L) (now - lastMarqueeFrameTime).coerceIn(0L, 100L) / 1000f else 0.016f
            lastMarqueeFrameTime = now

            val targetLoops = when (scrollMode) {
                "loop_1x" -> 1
                "loop_2x" -> 2
                "static" -> 0
                else -> Int.MAX_VALUE // "infinite"
            }

            val isLoopingActive = marqueeCompletedLoops < targetLoops && scrollMode != "static"

            if (isLoopingActive) {
                // Scroll ticker
                val scrollSpeed = 36f * d // px per second
                marqueeScrollOffset += scrollSpeed * dt
                if (marqueeScrollOffset >= cycleLength) {
                    marqueeCompletedLoops++
                    if (marqueeCompletedLoops < targetLoops) {
                        marqueeScrollOffset -= cycleLength
                    } else {
                        marqueeScrollOffset = 0f
                    }
                }

                // Render clipped dual-cycle marquee
                canvas.save()
                canvas.clipRect(rightWingLeft, pillTop, rightWingRight, pillBottom)

                val x1 = rightWingLeft - marqueeScrollOffset
                val x2 = x1 + cycleLength

                canvas.drawText(rawTitleText, x1, pillCy + (3.6f * d), hudTextPaint)
                canvas.drawText(separator + rawTitleText, x1 + textW, pillCy + (3.6f * d), hudTextPaint)
                canvas.restore()

                postInvalidateOnAnimation()
            } else {
                // Settled state or static: apply selected Truncate Anchor
                val truncateAt = when (truncateAnchor) {
                    "head" -> TextUtils.TruncateAt.START
                    "core" -> TextUtils.TruncateAt.MIDDLE
                    else -> TextUtils.TruncateAt.END // "tail"
                }

                val ellipsizedText = TextUtils.ellipsize(rawTitleText, hudTextPaint, rightWingAvailableW, truncateAt).toString()
                canvas.drawText(ellipsizedText, rightWingLeft, pillCy + (3.6f * d), hudTextPaint)
            }
        }
    }
}
