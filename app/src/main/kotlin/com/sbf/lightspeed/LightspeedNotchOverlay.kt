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
import androidx.core.content.ContextCompat
import com.sbf.lightspeed.system.LightspeedHapticEngine
import com.sbf.lightspeed.system.LightspeedIconManager
import com.sbf.lightspeed.system.LightspeedMediaManager
import com.sbf.lightspeed.system.LightspeedNotificationListener
import com.sbf.lightspeed.system.LightspeedPreferences
import com.sbf.lightspeed.system.defaultPrefs
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.abs
import kotlin.math.hypot

/**
 * Dedicated, unclipped Camera Cutout Notch Pill overlay window for Lightspeed.
 *
 * Implements Dynamic Notch Geometry wrapping the physical camera cutout with configurable
 * Snugness Padding, multiple Capsule Layout Modes ("Unified Right", "Dual-Wing Bridge", "Unified Left"),
 * high-resolution circular application icons and VectorDrawables (zero raw text characters),
 * Title Marquee & Truncation engine, and an interactive liquid-glass Mini-Player card.
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

    private val progressRingBgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        color = Color.argb(60, 255, 255, 255)
    }

    private val progressRingFillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
    }

    private val buttonBgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = Color.argb(80, 255, 255, 255)
    }

    private val iconBitmapPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val circularIconCache = ConcurrentHashMap<String, Bitmap>()

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
    private val lastReportedBounds = RectF()

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
            circularIconCache.clear()
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
        circularIconCache.clear()
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
                val pillW = (collapsedPillBounds.width()).toInt().coerceAtLeast((24 * d).toInt())
                val pillH = (collapsedPillBounds.height()).toInt().coerceAtLeast((16 * d).toInt())
                val targetX = collapsedPillBounds.left.toInt().coerceAtLeast(0)
                val targetY = collapsedPillBounds.top.toInt().coerceAtLeast(0)
                LightspeedAccessibilityService.instance?.updateNotchWindowBounds(false, targetX, targetY, pillW, pillH)
            }
        }
    }

    private fun getCircularAppIcon(packageName: String, sizePx: Int): Bitmap? {
        if (packageName.isBlank() || sizePx <= 0) return null
        val cacheKey = "$packageName:$sizePx"
        circularIconCache[cacheKey]?.let { return it }

        val rawDrawable = try {
            context.packageManager.getApplicationIcon(packageName)
        } catch (_: Exception) {
            LightspeedIconManager.getIconDrawable(context, "app:$packageName")
        } ?: return null

        return try {
            val bitmap = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bitmap)
            val path = Path().apply {
                addCircle(sizePx / 2f, sizePx / 2f, sizePx / 2f, Path.Direction.CW)
            }
            canvas.clipPath(path)
            rawDrawable.setBounds(0, 0, sizePx, sizePx)
            rawDrawable.draw(canvas)
            circularIconCache[cacheKey] = bitmap
            bitmap
        } catch (_: Exception) {
            null
        }
    }

    private fun drawVector(canvas: Canvas, resId: Int, bounds: RectF, tintColor: Int? = null) {
        val drawable = ContextCompat.getDrawable(context, resId)?.mutate() ?: return
        if (tintColor != null) {
            drawable.setTint(tintColor)
        }
        drawable.setBounds(
            bounds.left.toInt(),
            bounds.top.toInt(),
            bounds.right.toInt(),
            bounds.bottom.toInt()
        )
        drawable.draw(canvas)
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
        val snugnessDp = prefs.getInt(LightspeedPreferences.KEY_NOTCH_PADDING_SNUGNESS, 2)
        val snugnessPx = snugnessDp * d
        val capsuleLayout = prefs.getString(LightspeedPreferences.KEY_NOTCH_CAPSULE_LAYOUT, "unified_right") ?: "unified_right"

        val cutout = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) rootWindowInsets?.displayCutout else null
        val topCutoutRect = cutout?.boundingRectTop ?: cutout?.boundingRects?.firstOrNull { it.top == 0 }

        val cutoutW = (topCutoutRect?.width()?.toFloat() ?: (28f * d)).coerceAtLeast(16f * d)
        val cutoutH = (topCutoutRect?.height()?.toFloat() ?: (28f * d)).coerceAtLeast(16f * d)
        val cutoutCenterX = (if (topCutoutRect != null && topCutoutRect.width() > 0) topCutoutRect.exactCenterX() else w / 2f) + offsetX
        val cutoutTopY = (if (topCutoutRect != null) topCutoutRect.top.toFloat() else 0f) + offsetY

        val cutoutLeft = cutoutCenterX - cutoutW / 2f
        val cutoutRight = cutoutCenterX + cutoutW / 2f
        val cutoutCy = cutoutTopY + cutoutH / 2f

        // Dynamic Symmetrical Height: cutoutH + (2 * snugnessPx)
        val pillH = cutoutH + (2f * snugnessPx)
        val pillCy = cutoutCy
        val pillTop = (pillCy - pillH / 2f).coerceAtLeast(0f)
        val pillBottom = pillTop + pillH
        val cornerRadius = pillH / 2f

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

            // Close Button [✕] at Top Right (Vector Drawable)
            val closeSize = 22f * d
            btnCloseBounds.set(expandedCardBounds.right - closeSize - 12f * d, expandedCardBounds.top + 10f * d, expandedCardBounds.right - 12f * d, expandedCardBounds.top + 10f * d + closeSize)
            canvas.drawCircle(btnCloseBounds.centerX(), btnCloseBounds.centerY(), closeSize / 2f, buttonBgPaint)
            val closeIconBounds = RectF(btnCloseBounds.centerX() - 5.5f * d, btnCloseBounds.centerY() - 5.5f * d, btnCloseBounds.centerX() + 5.5f * d, btnCloseBounds.centerY() + 5.5f * d)
            drawVector(canvas, R.drawable.ic_close, closeIconBounds, Color.WHITE)

            if (showMediaPill || media != null) {
                val med = media ?: LightspeedNotificationListener.activeMediaTelemetry
                val title = med?.title ?: "Active Media"
                val artist = med?.artist ?: "Now Playing"
                val isPlaying = med?.isPlaying ?: true

                // Header Badge with Music Vector
                val badgeIconBounds = RectF(cardLeft + 16f * d, cardTop + 13f * d, cardLeft + 25f * d, cardTop + 22f * d)
                drawVector(canvas, R.drawable.ic_music_note, badgeIconBounds, m3Primary)
                hudTextPaint.textSize = 9.5f * d
                hudTextPaint.color = m3Primary
                canvas.drawText("NOW PLAYING", cardLeft + 28f * d, cardTop + 21f * d, hudTextPaint)

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

                // Transport Controls: [ ⏮ ] [ ⏯ ] [ ⏭ ] (Vector Drawables)
                val btnY = cardTop + 92f * d
                val btnR = 14f * d
                val cX = cardLeft + cardW / 2f

                btnPrevBounds.set(cX - 45f * d - btnR, btnY - btnR, cX - 45f * d + btnR, btnY + btnR)
                btnPlayPauseBounds.set(cX - btnR - 2f * d, btnY - btnR - 2f * d, cX + btnR + 2f * d, btnY + btnR + 2f * d)
                btnNextBounds.set(cX + 45f * d - btnR, btnY - btnR, cX + 45f * d + btnR, btnY + btnR)

                canvas.drawCircle(btnPrevBounds.centerX(), btnPrevBounds.centerY(), btnR, buttonBgPaint)
                canvas.drawCircle(btnPlayPauseBounds.centerX(), btnPlayPauseBounds.centerY(), btnR + 2f * d, buttonBgPaint)
                canvas.drawCircle(btnNextBounds.centerX(), btnNextBounds.centerY(), btnR, buttonBgPaint)

                val prevIconBounds = RectF(btnPrevBounds.centerX() - 6f * d, btnPrevBounds.centerY() - 6f * d, btnPrevBounds.centerX() + 6f * d, btnPrevBounds.centerY() + 6f * d)
                drawVector(canvas, R.drawable.ic_skip_previous, prevIconBounds, Color.WHITE)

                val playPauseR = 7f * d
                val playPauseBounds = RectF(btnPlayPauseBounds.centerX() - playPauseR, btnPlayPauseBounds.centerY() - playPauseR, btnPlayPauseBounds.centerX() + playPauseR, btnPlayPauseBounds.centerY() + playPauseR)
                drawVector(canvas, if (isPlaying) R.drawable.ic_pause else R.drawable.ic_play_arrow, playPauseBounds, Color.WHITE)

                val nextIconBounds = RectF(btnNextBounds.centerX() - 6f * d, btnNextBounds.centerY() - 6f * d, btnNextBounds.centerX() + 6f * d, btnNextBounds.centerY() + 6f * d)
                drawVector(canvas, R.drawable.ic_skip_next, nextIconBounds, Color.WHITE)
            } else if (showDlPill) {
                // Header Badge with Download Vector
                val badgeIconBounds = RectF(cardLeft + 16f * d, cardTop + 14f * d, cardLeft + 25f * d, cardTop + 23f * d)
                drawVector(canvas, R.drawable.ic_arrow_downward, badgeIconBounds, m3Primary)
                hudTextPaint.textSize = 9.5f * d
                hudTextPaint.color = m3Primary
                canvas.drawText("DOWNLOADING", cardLeft + 28f * d, cardTop + 22f * d, hudTextPaint)

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
        // COLLAPSED CAPSULE NOTCH PILL RENDERING (3 MODES)
        // =========================================================================
        val scrollMode = prefs.getString(LightspeedPreferences.KEY_NOTCH_TEXT_SCROLL_MODE, "loop_2x") ?: "loop_2x"
        val truncateAnchor = prefs.getString(LightspeedPreferences.KEY_NOTCH_TEXT_TRUNCATE_ANCHOR, "tail") ?: "tail"

        val gap = 6f * d
        val iconPad = 4.5f * d
        val textMargin = 6f * d
        val iconSize = (pillH - 4f * d).coerceIn(14f * d, 26f * d)

        val rawTitleText = when {
            isTestBeacon -> "ALIGNMENT BEACON"
            showDlPill -> {
                if (primaryDl!!.isIndeterminate) "DOWNLOADING…" else "${(primaryDl.progressFraction * 100).toInt()}% • ${primaryDl.title}"
            }
            showMediaPill -> media!!.title
            else -> ""
        }

        if (rawTitleText.isBlank()) return

        hudTextPaint.textSize = (pillH * 0.38f).coerceIn(9f * d, 11f * d)
        hudTextPaint.color = if (isTestBeacon) m3Primary else Color.WHITE

        val textW = hudTextPaint.measureText(rawTitleText)
        val separator = "   •   "
        val separatorW = hudTextPaint.measureText(separator)
        val cycleLength = textW + separatorW

        // Layout mode calculations
        val iconCenterX: Float
        val iconCenterY = pillCy
        val textLeft: Float
        val textRight: Float

        when (capsuleLayout) {
            "dual_wing" -> {
                // Dual-Wing Bridge: Left Wing (Icon), Center Camera Exclusion, Right Wing (Text)
                val leftWingW = (iconSize + 2 * iconPad).coerceAtLeast(26f * d)
                val rightWingBaseW = if (isTestBeacon) 110f * d else if (showDlPill) 100f * d else 135f * d
                val rightWingW = (rightWingBaseW + expansionW).coerceIn(60f * d, 240f * d)

                val pillLeft = cutoutLeft - gap - leftWingW
                val pillRight = cutoutRight + gap + rightWingW + 8f * d
                collapsedPillBounds.set(pillLeft, pillTop, pillRight, pillBottom)

                iconCenterX = (pillLeft + (cutoutLeft - gap)) / 2f
                textLeft = cutoutRight + gap
                textRight = pillRight - 8f * d
            }
            "unified_left" -> {
                // Unified Left: Single capsule to the left of the cutout
                val baseTextW = if (isTestBeacon) 115f * d else if (showDlPill) 105f * d else 130f * d
                val textCapacityW = (baseTextW + expansionW).coerceIn(60f * d, 240f * d)
                val pillW = iconPad + iconSize + textMargin + textCapacityW + iconPad

                val pillRight = cutoutLeft - gap
                val pillLeft = pillRight - pillW
                collapsedPillBounds.set(pillLeft, pillTop, pillRight, pillBottom)

                iconCenterX = pillLeft + iconPad + iconSize / 2f
                textLeft = pillLeft + iconPad + iconSize + textMargin
                textRight = pillRight - iconPad
            }
            else -> {
                // "unified_right" [Default]: Single compact capsule immediately to right of cutout
                val baseTextW = if (isTestBeacon) 115f * d else if (showDlPill) 105f * d else 130f * d
                val textCapacityW = (baseTextW + expansionW).coerceIn(60f * d, 240f * d)
                val pillW = iconPad + iconSize + textMargin + textCapacityW + iconPad

                val pillLeft = cutoutRight + gap
                val pillRight = pillLeft + pillW
                collapsedPillBounds.set(pillLeft, pillTop, pillRight, pillBottom)

                iconCenterX = pillLeft + iconPad + iconSize / 2f
                textLeft = pillLeft + iconPad + iconSize + textMargin
                textRight = pillRight - iconPad
            }
        }

        // Notify layout manager if bounds changed
        if (abs(lastReportedBounds.left - collapsedPillBounds.left) > 1f ||
            abs(lastReportedBounds.top - collapsedPillBounds.top) > 1f ||
            abs(lastReportedBounds.right - collapsedPillBounds.right) > 1f ||
            abs(lastReportedBounds.bottom - collapsedPillBounds.bottom) > 1f) {
            lastReportedBounds.set(collapsedPillBounds)
            notifyWindowLayoutChanged()
        }

        // 1. Draw Liquid-Glass Capsule Pill Background & Border
        telemetryPillFillPaint.color = Color.argb(235, 14, 18, 28)
        canvas.drawRoundRect(collapsedPillBounds, cornerRadius, cornerRadius, telemetryPillFillPaint)

        telemetryPillRimPaint.strokeWidth = 1.3f * d
        telemetryPillRimPaint.color = Color.argb(160, Color.red(m3Primary), Color.green(m3Primary), Color.blue(m3Primary))
        canvas.drawRoundRect(collapsedPillBounds, cornerRadius, cornerRadius, telemetryPillRimPaint)

        // 2. Draw Native Application Icon / Vector Drawable in Icon Slot
        val iconSlotBounds = RectF(iconCenterX - iconSize / 2f, iconCenterY - iconSize / 2f, iconCenterX + iconSize / 2f, iconCenterY + iconSize / 2f)

        if (showMediaPill || (media != null && media.isPlaying)) {
            val pkg = media?.packageName ?: LightspeedMediaManager.getActiveTrackInfo(context).packageName
            val appIconBmp = if (!pkg.isNullOrBlank()) getCircularAppIcon(pkg, iconSize.toInt()) else null
            if (appIconBmp != null) {
                canvas.drawBitmap(appIconBmp, null, iconSlotBounds, iconBitmapPaint)
            } else {
                drawVector(canvas, R.drawable.ic_music_note, iconSlotBounds, m3Primary)
            }
        } else if (showDlPill) {
            val pkg = primaryDl.packageName
            val appIconBmp = if (!pkg.isNullOrBlank()) getCircularAppIcon(pkg, (iconSize - 3f * d).toInt()) else null

            // Circular download progress ring
            val ringPadding = 1f * d
            val ringBounds = RectF(iconSlotBounds.left - ringPadding, iconSlotBounds.top - ringPadding, iconSlotBounds.right + ringPadding, iconSlotBounds.bottom + ringPadding)
            progressRingBgPaint.strokeWidth = 1.6f * d
            progressRingFillPaint.strokeWidth = 1.6f * d
            progressRingFillPaint.color = m3Primary
            canvas.drawOval(ringBounds, progressRingBgPaint)

            if (primaryDl.isIndeterminate) {
                val sweepAngle = 90f
                val startAngle = ((SystemClock.uptimeMillis() % 1200L) / 1200f) * 360f
                canvas.drawArc(ringBounds, startAngle, sweepAngle, false, progressRingFillPaint)
                postInvalidateOnAnimation()
            } else {
                val sweepAngle = (primaryDl.progressFraction.coerceIn(0f, 1f)) * 360f
                canvas.drawArc(ringBounds, -90f, sweepAngle, false, progressRingFillPaint)
            }

            if (appIconBmp != null) {
                val innerBounds = RectF(iconSlotBounds.left + 1.5f * d, iconSlotBounds.top + 1.5f * d, iconSlotBounds.right - 1.5f * d, iconSlotBounds.bottom - 1.5f * d)
                canvas.drawBitmap(appIconBmp, null, innerBounds, iconBitmapPaint)
            } else {
                val innerBounds = RectF(iconSlotBounds.left + 2f * d, iconSlotBounds.top + 2f * d, iconSlotBounds.right - 2f * d, iconSlotBounds.bottom - 2f * d)
                drawVector(canvas, R.drawable.ic_arrow_downward, innerBounds, m3Primary)
            }
        } else {
            // Test Beacon / Default
            drawVector(canvas, R.drawable.ic_beacon_sparkle, iconSlotBounds, m3Primary)
        }

        // 3. Draw Title / Marquee Text
        val availableW = (textRight - textLeft).coerceAtLeast(10f * d)
        val textBaseline = pillCy - (hudTextPaint.descent() + hudTextPaint.ascent()) / 2f

        if (textW <= availableW) {
            // Text fits cleanly
            canvas.drawText(rawTitleText, textLeft, textBaseline, hudTextPaint)
        } else {
            // Text overflows -> Run Marquee Engine
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

                canvas.save()
                canvas.clipRect(textLeft, pillTop, textRight, pillBottom)

                val x1 = textLeft - marqueeScrollOffset
                canvas.drawText(rawTitleText, x1, textBaseline, hudTextPaint)
                canvas.drawText(separator + rawTitleText, x1 + textW, textBaseline, hudTextPaint)
                canvas.restore()

                postInvalidateOnAnimation()
            } else {
                val truncateAt = when (truncateAnchor) {
                    "head" -> TextUtils.TruncateAt.START
                    "core" -> TextUtils.TruncateAt.MIDDLE
                    else -> TextUtils.TruncateAt.END // "tail"
                }

                val ellipsizedText = TextUtils.ellipsize(rawTitleText, hudTextPaint, availableW, truncateAt).toString()
                canvas.drawText(ellipsizedText, textLeft, textBaseline, hudTextPaint)
            }
        }
    }
}
