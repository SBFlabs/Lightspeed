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
import com.sbf.lightspeed.system.ActionDispatcher
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

    companion object {
        var cachedCutoutRect: Rect? = null
    }

    internal val prefs = context.defaultPrefs()
    internal val mainHandler = Handler(Looper.getMainLooper())

    internal val telemetryPillFillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }

    internal val telemetryPillRimPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
    }

    internal val hudTextPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textAlign = Paint.Align.LEFT
        isFakeBoldText = true
    }

    internal val subTextPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.LTGRAY
        textAlign = Paint.Align.LEFT
    }

    internal val progressBarBgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = Color.argb(60, 255, 255, 255)
    }

    internal val progressBarFillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }

    internal val progressRingBgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        color = Color.argb(60, 255, 255, 255)
    }

    internal val progressRingFillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
    }

    internal val buttonBgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = Color.argb(80, 255, 255, 255)
    }

    internal val iconBitmapPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    internal val circularIconCache = android.util.LruCache<String, Bitmap>(350)
    private val circleClipPath = Path()

    // Marquee State Tracking
    internal var currentTextKey = ""
    internal var marqueeScrollOffset = 0f
    internal var marqueeCompletedLoops = 0
    internal var lastMarqueeFrameTime = 0L

    // Expansion State & Bounds
    var isExpanded = false
        private set

    var expandedPageIndex = 0
        private set

    internal val expandedCardBounds = RectF()

    // Transport button touch targets
    internal val btnPrevBounds = RectF()
    internal val btnPlayPauseBounds = RectF()
    internal val btnNextBounds = RectF()
    internal val btnCloseBounds = RectF()

    // 2-Page Navigation & Quick Remote Targets
    internal val dot1Bounds = RectF()
    internal val dot2Bounds = RectF()
    internal val btnRemoteFlashlightBounds = RectF()
    internal val btnRemoteScreenshotBounds = RectF()
    internal val btnRemoteLockBounds = RectF()
    internal val btnRemoteTimeoutBounds = RectF()
    internal val btnRemoteRefuelBounds = RectF()
    internal val btnCoreCoolingBounds = RectF()

    // Core Cooling Triple-Lock State
    internal var coreCoolingStep = 0 // 0 = default, 1 = are you sure (amber), 2 = are you sure sure (red)
    internal val coreCoolingResetRunnable = Runnable {
        if (coreCoolingStep != 0) {
            coreCoolingStep = 0
            postInvalidate()
        }
    }

    // Touch interaction tracking
    internal var touchDownX = 0f
    internal var touchDownY = 0f
    internal var touchDownTime = 0L
    internal var isLongPressDispatched = false

    internal val longPressRunnable = Runnable {
        isLongPressDispatched = true
        LightspeedHapticEngine.tick(context)
        performLongPressAction()
    }

    internal val autoCollapseRunnable = Runnable {
        if (isExpanded) {
            collapseCard()
        }
    }

    internal val prefListener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
        if (key != null && (key.startsWith("pref_notch_") || key.startsWith("pref_telemetry_") || key == "pref_statusbar_enabled")) {
            currentTextKey = ""
            marqueeScrollOffset = 0f
            marqueeCompletedLoops = 0
            circularIconCache.evictAll()
            mainHandler.post {
                updateCapsuleLayout()
            }
        }
    }

    internal val telemetryListener: () -> Unit = {
        mainHandler.post {
            updateCapsuleLayout()
        }
    }

    init {
        isClickable = true
        isFocusable = false
        prefs.registerOnSharedPreferenceChangeListener(prefListener)

        LightspeedNotificationListener.registerTelemetryListener(telemetryListener)

        mainHandler.post {
            updateCapsuleLayout()
        }
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            val insets = rootWindowInsets?.displayCutout
            (insets?.boundingRectTop ?: insets?.boundingRects?.firstOrNull { it.top == 0 })?.let {
                if (it.width() > 0) cachedCutoutRect = it
            }
        }
        updateCapsuleLayout()
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        prefs.unregisterOnSharedPreferenceChangeListener(prefListener)
        LightspeedNotificationListener.unregisterTelemetryListener(telemetryListener)
        mainHandler.removeCallbacksAndMessages(null)
        circularIconCache.evictAll()
    }

    fun updateNotchMetrics() {
        updateCapsuleLayout()
    }

    fun updateCapsuleLayout() {
        val d = resources.displayMetrics.density
        val screenW = resources.displayMetrics.widthPixels.toFloat()

        val dlRouting = prefs.getString(LightspeedPreferences.KEY_RAIL_DOWNLOADS_ROUTING, "notch_pill") ?: "notch_pill"
        val mediaRouting = prefs.getString(LightspeedPreferences.KEY_RAIL_MEDIA_ROUTING, "none") ?: "none"
        val primaryDl = LightspeedNotificationListener.getPrimaryDownload()
        val media = LightspeedNotificationListener.activeMediaTelemetry

        val orientation = resources.configuration.orientation
        val isLandscape = orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE
        val capsuleOrientMode = prefs.getString(LightspeedPreferences.KEY_NOTCH_CAPSULE_ORIENTATION_MODE, "both") ?: "both"

        val isTestBeacon = prefs.getBoolean(LightspeedPreferences.KEY_NOTCH_TEST_BEACON, false)
        val showDlPill = (dlRouting == "notch_pill" || dlRouting == "both") && primaryDl != null
        val showMediaPill = (mediaRouting == "notch_pill" || mediaRouting == "both") && media != null && media.isPlaying
        val isPillActive = isTestBeacon || showDlPill || showMediaPill
        val isAllowedByOrientation = when (capsuleOrientMode) {
            "portrait_only" -> !isLandscape
            "landscape_only" -> isLandscape
            else -> true
        }

        if ((!isPillActive || !isAllowedByOrientation) && !isExpanded) {
            LightspeedAccessibilityService.instance?.updateNotchWindowBounds(false, 0, 0, 1, 1, isVisible = false)
            return
        }

        // Cutout Metrics
        val topCutout = cachedCutoutRect ?: if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            val insets = rootWindowInsets?.displayCutout
            (insets?.boundingRectTop ?: insets?.boundingRects?.firstOrNull { it.top == 0 })?.also {
                if (it.width() > 0) cachedCutoutRect = it
            }
        } else null

        val detectedCutoutWidthDp = (topCutout?.width()?.toFloat()?.div(d) ?: 20f).toInt().coerceIn(14, 48)
        val hardwareCutoutWidthDp = LightspeedPreferences.getEffectiveCutoutWidth(prefs, detectedCutoutWidthDp)
        val cutoutW = if (hardwareCutoutWidthDp > 0) (hardwareCutoutWidthDp.toFloat() * d) else (topCutout?.width()?.toFloat() ?: (20f * d)).coerceAtLeast(14f * d)
        val cutoutH = (topCutout?.height()?.toFloat() ?: (24f * d)).coerceIn(14f * d, 40f * d)
        val detectedCenterX = if (topCutout != null && topCutout.width() > 0) topCutout.exactCenterX() else (screenW / 2f)
        val detectedTopY = if (topCutout != null) topCutout.top.toFloat() else 0f

        // Shared Notch Calibration Parameters
        val offsetX = LightspeedPreferences.getEffectiveCutoutOffsetX(prefs).toFloat() * d
        val offsetY = LightspeedPreferences.getEffectiveCutoutOffsetY(prefs).toFloat() * d
        val expansionW = prefs.getInt(LightspeedPreferences.KEY_NOTCH_EXPANSION_WIDTH, 0) * d
        val snugnessDp = prefs.getInt(LightspeedPreferences.KEY_NOTCH_PADDING_SNUGNESS, 2)
        val snugnessPx = snugnessDp * d
        val capsuleLayout = prefs.getString(LightspeedPreferences.KEY_NOTCH_CAPSULE_LAYOUT, "unified_right") ?: "unified_right"

        val cutoutCenterX = detectedCenterX + offsetX
        val cutoutTopY = detectedTopY + offsetY
        val cutoutLeft = cutoutCenterX - cutoutW / 2f
        val cutoutRight = cutoutCenterX + cutoutW / 2f
        val cutoutCy = cutoutTopY + cutoutH / 2f

        val pillH = cutoutH + (2f * snugnessPx)
        val pillCy = cutoutCy
        val pillTop = (pillCy - pillH / 2f).coerceAtLeast(0f)

        if (isExpanded) {
            val cardW = (320f * d).coerceAtMost(screenW - 16f * d)
            val cardH = 126f * d
            val targetX = ((screenW - cardW) / 2f).toInt()
            val targetY = pillTop.toInt()
            LightspeedAccessibilityService.instance?.updateNotchWindowBounds(true, targetX, targetY, cardW.toInt(), cardH.toInt(), isVisible = true)
        } else {
            val gap = 6f * d
            val iconPad = 4.5f * d
            val textMargin = 6f * d
            val iconSize = (pillH - 4f * d).coerceIn(14f * d, 26f * d)

            val baseTextW = if (isTestBeacon) 115f * d else if (showDlPill) 105f * d else 130f * d
            val textCapacityW = (baseTextW + expansionW).coerceIn(60f * d, 240f * d)

            when (capsuleLayout) {
                "dual_wing" -> {
                    val leftWingW = (iconSize + 2 * iconPad).coerceAtLeast(26f * d)
                    val rightWingBaseW = if (isTestBeacon) 110f * d else if (showDlPill) 100f * d else 135f * d
                    val rightWingW = (rightWingBaseW + expansionW).coerceIn(60f * d, 240f * d)
                    val pillLeft = cutoutLeft - gap - leftWingW
                    val pillRight = cutoutRight + gap + rightWingW
                    val pillW = pillRight - pillLeft
                    LightspeedAccessibilityService.instance?.updateNotchWindowBounds(false, pillLeft.toInt(), pillTop.toInt(), pillW.toInt(), pillH.toInt(), isVisible = true)
                }
                "unified_left" -> {
                    val pillW = iconPad + iconSize + textMargin + textCapacityW + iconPad
                    val pillRight = cutoutLeft - gap
                    val pillLeft = pillRight - pillW
                    LightspeedAccessibilityService.instance?.updateNotchWindowBounds(false, pillLeft.toInt(), pillTop.toInt(), pillW.toInt(), pillH.toInt(), isVisible = true)
                }
                else -> {
                    // "unified_right"
                    val pillW = iconPad + iconSize + textMargin + textCapacityW + iconPad
                    val pillLeft = cutoutRight + gap
                    LightspeedAccessibilityService.instance?.updateNotchWindowBounds(false, pillLeft.toInt(), pillTop.toInt(), pillW.toInt(), pillH.toInt(), isVisible = true)
                }
            }
        }
        postInvalidate()
    }

    fun expandCard() {
        if (isExpanded) return
        isExpanded = true
        expandedPageIndex = 0
        coreCoolingStep = 0
        mainHandler.removeCallbacks(coreCoolingResetRunnable)
        LightspeedHapticEngine.tick(context)
        resetAutoCollapseTimer()
        updateCapsuleLayout()
    }

    fun collapseCard() {
        if (!isExpanded) return
        isExpanded = false
        coreCoolingStep = 0
        mainHandler.removeCallbacks(coreCoolingResetRunnable)
        mainHandler.removeCallbacks(autoCollapseRunnable)
        LightspeedHapticEngine.tick(context)
        updateCapsuleLayout()
    }

    internal fun resetAutoCollapseTimer() {
        mainHandler.removeCallbacks(autoCollapseRunnable)
        val timeout = if (expandedPageIndex == 1) 8000L else 4000L
        mainHandler.postDelayed(autoCollapseRunnable, timeout)
    }

    internal fun getCircularAppIcon(packageName: String, sizePx: Int): Bitmap? {
        if (packageName.isBlank() || sizePx <= 0) return null
        val cacheKey = "$packageName:$sizePx"
        circularIconCache.get(cacheKey)?.let { return it }

        val rawDrawable = try {
            context.packageManager.getApplicationIcon(packageName)
        } catch (_: Exception) {
            LightspeedIconManager.getIconDrawable(context, "app:$packageName")
        } ?: return null

        return try {
            val bitmap = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bitmap)
            synchronized(circleClipPath) {
                circleClipPath.rewind()
                circleClipPath.addCircle(sizePx / 2f, sizePx / 2f, sizePx / 2f, Path.Direction.CW)
                canvas.clipPath(circleClipPath)
            }
            rawDrawable.setBounds(0, 0, sizePx, sizePx)
            rawDrawable.draw(canvas)
            circularIconCache.put(cacheKey, bitmap)
            bitmap
        } catch (_: Exception) {
            null
        }
    }

    internal fun drawVector(canvas: Canvas, resId: Int, bounds: RectF, tintColor: Int? = null) {
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

    internal fun performLongPressAction() {
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
            // Expanded Card Touch Detection with 2-Page Horizontal Swipe Support
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    touchDownX = x
                    touchDownY = y
                    touchDownTime = SystemClock.uptimeMillis()
                    resetAutoCollapseTimer()
                    return true
                }
                MotionEvent.ACTION_MOVE -> {
                    return true
                }
                MotionEvent.ACTION_UP -> {
                    resetAutoCollapseTimer()
                    val dx = x - touchDownX
                    val dy = y - touchDownY

                    // Horizontal Swipe Carousel Navigation
                    if (abs(dx) > 28f * resources.displayMetrics.density && abs(dx) > abs(dy) * 1.1f) {
                        if (dx < 0 && expandedPageIndex == 0) {
                            expandedPageIndex = 1
                            LightspeedHapticEngine.tick(context)
                            resetAutoCollapseTimer()
                            postInvalidate()
                            return true
                        } else if (dx > 0 && expandedPageIndex == 1) {
                            expandedPageIndex = 0
                            LightspeedHapticEngine.tick(context)
                            resetAutoCollapseTimer()
                            postInvalidate()
                            return true
                        }
                    }

                    // Close Button [✕]
                    if (btnCloseBounds.contains(x, y)) {
                        collapseCard()
                        return true
                    }

                    // Page Indicator Dot Taps
                    if (dot1Bounds.contains(x, y)) {
                        if (expandedPageIndex != 0) {
                            expandedPageIndex = 0
                            LightspeedHapticEngine.tick(context)
                            resetAutoCollapseTimer()
                            postInvalidate()
                        }
                        return true
                    }
                    if (dot2Bounds.contains(x, y)) {
                        if (expandedPageIndex != 1) {
                            expandedPageIndex = 1
                            LightspeedHapticEngine.tick(context)
                            resetAutoCollapseTimer()
                            postInvalidate()
                        }
                        return true
                    }

                    if (expandedPageIndex == 0) {
                        // Page 1: Media Controls
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
                    } else {
                        // Page 2: Quick Remote Controls
                        if (btnRemoteFlashlightBounds.contains(x, y)) {
                            LightspeedHapticEngine.tick(context)
                            ActionDispatcher.execute(context, "system:flashlight")
                            resetAutoCollapseTimer()
                            return true
                        }
                        if (btnRemoteScreenshotBounds.contains(x, y)) {
                            LightspeedHapticEngine.tick(context)
                            ActionDispatcher.execute(context, "system:screenshot")
                            resetAutoCollapseTimer()
                            return true
                        }
                        if (btnRemoteLockBounds.contains(x, y)) {
                            LightspeedHapticEngine.tick(context)
                            ActionDispatcher.execute(context, "system:lock_screen")
                            resetAutoCollapseTimer()
                            return true
                        }
                        if (btnRemoteTimeoutBounds.contains(x, y)) {
                            LightspeedHapticEngine.tick(context)
                            ActionDispatcher.execute(context, "system:screen_timeout")
                            resetAutoCollapseTimer()
                            return true
                        }
                        if (btnRemoteRefuelBounds.contains(x, y)) {
                            LightspeedHapticEngine.tick(context)
                            ActionDispatcher.execute(context, "system:refueling_bay")
                            resetAutoCollapseTimer()
                            return true
                        }
                        if (btnCoreCoolingBounds.contains(x, y)) {
                            resetAutoCollapseTimer()
                            mainHandler.removeCallbacks(coreCoolingResetRunnable)
                            when (coreCoolingStep) {
                                0 -> {
                                    coreCoolingStep = 1
                                    LightspeedHapticEngine.click(context)
                                    mainHandler.postDelayed(coreCoolingResetRunnable, 5000L)
                                    postInvalidate()
                                }
                                1 -> {
                                    coreCoolingStep = 2
                                    LightspeedHapticEngine.click(context)
                                    mainHandler.postDelayed(coreCoolingResetRunnable, 5000L)
                                    postInvalidate()
                                }
                                2 -> {
                                    coreCoolingStep = 0
                                    LightspeedHapticEngine.heavyClick(context)
                                    collapseCard()
                                    com.sbf.lightspeed.system.LightspeedWatchdogEngine.executeCoreCoolingReboot(context)
                                }
                            }
                            return true
                        }
                    }

                    if (!expandedCardBounds.contains(x, y)) {
                        collapseCard()
                        return true
                    }
                    return true
                }
                MotionEvent.ACTION_CANCEL -> {
                    return true
                }
            }
            return true
        }

        return super.onTouchEvent(event)
    }

    override fun onDraw(canvas: Canvas) {
        handleDraw(canvas) { super.onDraw(canvas) }
    }
}
