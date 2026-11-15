package com.sbf.lightspeed.system.display

import android.app.ActivityManager
import android.app.KeyguardManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.RectF
import android.graphics.Typeface
import android.os.BatteryManager
import android.os.Handler
import android.os.Looper
import android.view.Choreographer
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import com.sbf.lightspeed.LightspeedAccessibilityService
import com.sbf.lightspeed.settings.DeckThemeManager
import com.sbf.lightspeed.system.LightspeedHapticEngine
import com.sbf.lightspeed.system.LightspeedPreferences
import com.sbf.lightspeed.system.defaultPrefs
import com.sbf.lightspeed.system.logSwallowed
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Per-App Tactical Telemetry Micro-Visor Overlay (FPS, CPU, TEMP, RAM, BAT, WATT).
 *
 * Strictly mounts only when a user-selected target app/game is in the foreground.
 * Detaches completely from WindowManager and stops all sampling when outside target apps.
 * Includes a left tactile 6-dot drag grip for effortless repositioning on screen.
 */
object LightspeedTelemetryHudOverlay {

    private const val TAG = "TelemetryHudOverlay"
    private val mainHandler = Handler(Looper.getMainLooper())
    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())

    private var visorView: TelemetryMicroVisorView? = null
    private var windowParams: WindowManager.LayoutParams? = null
    private var pollJob: Job? = null
    @Volatile private var currentForegroundPkg: String? = null

    private data class TelemetrySegment(
        val text: String,
        val colorArgb: Int
    )

    private const val COLOR_TEXT_PRIMARY = 0xFFF2F6FA.toInt()
    private const val COLOR_STATUS_OK = 0xFF00E676.toInt()
    private const val COLOR_STATUS_WARN = 0xFFFFB300.toInt()
    private const val COLOR_STATUS_DANGER = 0xFFFF5252.toInt()
    private const val COLOR_SURFACE_BG = 0xCC0A0E14.toInt()

    fun init(context: Context) {
        val fg = LightspeedAccessibilityService.lastAppPackage
        onForegroundPackageChanged(context, fg)
    }

    fun onForegroundPackageChanged(context: Context, pkg: String?) {
        if (!pkg.isNullOrBlank() && pkg != "com.android.systemui" && pkg != context.packageName) {
            currentForegroundPkg = pkg
        } else if (pkg == null) {
            currentForegroundPkg = null
        }
        mainHandler.post { evaluateVisibility(context) }
    }

    fun refreshState(context: Context) {
        mainHandler.post {
            evaluateVisibility(context)
            visorView?.invalidate()
        }
    }

    fun resetPosition(context: Context) {
        context.defaultPrefs().edit()
            .putInt(LightspeedPreferences.KEY_TELEMETRY_HUD_POS_X, -1)
            .putInt(LightspeedPreferences.KEY_TELEMETRY_HUD_POS_Y, 12)
            .apply()
        mainHandler.post {
            val view = visorView ?: return@post
            val params = windowParams ?: return@post
            val service = LightspeedAccessibilityService.instance ?: return@post
            val wm = service.getSystemService(Context.WINDOW_SERVICE) as? WindowManager ?: return@post
            val dm = service.resources.displayMetrics
            params.x = ((dm.widthPixels - view.measuredWidth.coerceAtLeast((180 * dm.density).toInt())) / 2).coerceAtLeast(8)
            params.y = (12 * dm.density).roundToInt()
            try {
                wm.updateViewLayout(view, params)
            } catch (e: Exception) {
                logSwallowed(TAG, "resetPosition", e)
            }
        }
    }

    fun refreshDisplayModeVote(context: Context) {
        mainHandler.post {
            val view = visorView ?: return@post
            val params = windowParams ?: return@post
            val service = LightspeedAccessibilityService.instance ?: return@post
            val wm = service.getSystemService(Context.WINDOW_SERVICE) as? WindowManager ?: return@post
            applyPreferredModeToParams(context, params)
            try {
                wm.updateViewLayout(view, params)
            } catch (e: Exception) {
                logSwallowed(TAG, "refreshDisplayModeVote", e)
            }
        }
    }

    private fun applyPreferredModeToParams(context: Context, params: WindowManager.LayoutParams) {
        val prefs = context.defaultPrefs()
        val floorMode = prefs.getString(
            LightspeedPreferences.KEY_OVERDRIVE_FLOOR_MODE,
            LightspeedPreferences.OVERDRIVE_FLOOR_ADAPTIVE
        ) ?: LightspeedPreferences.OVERDRIVE_FLOOR_ADAPTIVE
        val targetHz = LightspeedRefreshRateEngine.getActiveTargetHz(context)
        if (floorMode == LightspeedPreferences.OVERDRIVE_FLOOR_LOCKED && targetHz > 0) {
            val mode = LightspeedRefreshRateEngine.findDisplayModeForHz(context, targetHz)
            if (mode != null) {
                params.preferredDisplayModeId = mode.modeId
                params.preferredRefreshRate = mode.refreshRate
                return
            }
        }
        params.preferredDisplayModeId = 0
        params.preferredRefreshRate = 0f
    }

    private fun evaluateVisibility(context: Context) {
        val service = LightspeedAccessibilityService.instance
        if (service == null || !LightspeedPreferences.isMasterFlightArmed(service)) {
            detachOverlay()
            return
        }

        val kg = service.getSystemService(Context.KEYGUARD_SERVICE) as? KeyguardManager
        if (kg?.isKeyguardLocked == true) {
            detachOverlay()
            return
        }

        val prefs = service.defaultPrefs()
        val enabled = prefs.getBoolean(LightspeedPreferences.KEY_TELEMETRY_HUD_ENABLED, false)
        if (!enabled) {
            detachOverlay()
            return
        }

        val allowedPkgs = prefs.getStringSet(LightspeedPreferences.KEY_TELEMETRY_HUD_PACKAGES, emptySet()) ?: emptySet()
        val activePkg = currentForegroundPkg ?: LightspeedAccessibilityService.lastAppPackage
        if (allowedPkgs.isEmpty() || activePkg.isNullOrBlank() || activePkg !in allowedPkgs) {
            detachOverlay()
            return
        }

        attachOverlayIfNeeded(service)
    }

    private fun attachOverlayIfNeeded(service: LightspeedAccessibilityService) {
        if (visorView != null) return
        val wm = service.getSystemService(Context.WINDOW_SERVICE) as? WindowManager ?: return
        val prefs = service.defaultPrefs()
        val dm = service.resources.displayMetrics

        val view = TelemetryMicroVisorView(service)
        val savedX = prefs.getInt(LightspeedPreferences.KEY_TELEMETRY_HUD_POS_X, -1)
        val savedY = prefs.getInt(LightspeedPreferences.KEY_TELEMETRY_HUD_POS_Y, (12 * dm.density).roundToInt())
        val initialX = if (savedX >= 0) savedX else ((dm.widthPixels - (210 * dm.density).roundToInt()) / 2).coerceAtLeast(8)

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = initialX
            y = savedY.coerceAtLeast(0)
        }
        applyPreferredModeToParams(service, params)

        try {
            wm.addView(view, params)
            visorView = view
            windowParams = params
            view.startFpsCounter()
            startPolling(service)
        } catch (e: Exception) {
            logSwallowed(TAG, "attachOverlayIfNeeded", e)
        }
    }

    fun detachOverlay() {
        pollJob?.cancel()
        pollJob = null
        val view = visorView ?: return
        view.stopFpsCounter()
        visorView = null
        windowParams = null
        val service = LightspeedAccessibilityService.instance ?: return
        val wm = service.getSystemService(Context.WINDOW_SERVICE) as? WindowManager ?: return
        try {
            wm.removeView(view)
        } catch (e: Exception) {
            logSwallowed(TAG, "detachOverlay", e)
        }
    }

    private fun startPolling(context: Context) {
        pollJob?.cancel()
        pollJob = scope.launch {
            while (isActive && visorView != null) {
                val segments = collectSegments(context)
                withContext(Dispatchers.Main) {
                    visorView?.updateSegments(segments)
                }
                delay(1000L)
            }
        }
    }

    private fun resolveOpaqueAccentArgb(context: Context): Int {
        val raw = DeckThemeManager.getSnapshot(context).effectiveTintArgb.toInt()
        val rgb = raw and 0x00FFFFFF
        return if (rgb == 0) 0xFF00E5FF.toInt() else (rgb or 0xFF000000.toInt())
    }

    private fun collectSegments(context: Context): List<TelemetrySegment> {
        val prefs = context.defaultPrefs()
        val accentArgb = resolveOpaqueAccentArgb(context)
        val segments = ArrayList<TelemetrySegment>(6)

        val showFps = prefs.getBoolean(LightspeedPreferences.KEY_TELEMETRY_HUD_SHOW_FPS, true)
        val showCpu = prefs.getBoolean(LightspeedPreferences.KEY_TELEMETRY_HUD_SHOW_CPU, true)
        val showTemp = prefs.getBoolean(LightspeedPreferences.KEY_TELEMETRY_HUD_SHOW_TEMP, true)
        val showRam = prefs.getBoolean(LightspeedPreferences.KEY_TELEMETRY_HUD_SHOW_RAM, false)
        val showBat = prefs.getBoolean(LightspeedPreferences.KEY_TELEMETRY_HUD_SHOW_BAT, true)
        val showWatt = prefs.getBoolean(LightspeedPreferences.KEY_TELEMETRY_HUD_SHOW_WATT, true)

        if (visorView?.isCompactFpsOnly == true) {
            val fps = visorView?.getCurrentFps(context) ?: LightspeedRefreshRateEngine.getLiveDisplayHz(context)
            segments.add(TelemetrySegment("$fps FPS", accentArgb))
            return segments
        }

        if (showFps) {
            val fps = visorView?.getCurrentFps(context) ?: LightspeedRefreshRateEngine.getLiveDisplayHz(context)
            segments.add(TelemetrySegment("$fps FPS", accentArgb))
        }

        if (showCpu) {
            val cpuPct = readCpuClockLoadPercent()
            val cpuColor = when {
                cpuPct >= 88 -> COLOR_STATUS_DANGER
                cpuPct >= 72 -> COLOR_STATUS_WARN
                else -> COLOR_TEXT_PRIMARY
            }
            segments.add(TelemetrySegment("CPU $cpuPct%", cpuColor))
        }

        val batteryIntent = try {
            context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        } catch (_: Exception) { null }

        if (showTemp && batteryIntent != null) {
            val rawTemp = batteryIntent.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, 0)
            if (rawTemp > 0) {
                val tempC = (rawTemp / 10f).roundToInt()
                val tempColor = when {
                    tempC >= 44 -> COLOR_STATUS_DANGER
                    tempC >= 40 -> COLOR_STATUS_WARN
                    else -> COLOR_TEXT_PRIMARY
                }
                segments.add(TelemetrySegment("${tempC}°C", tempColor))
            }
        }

        if (showRam) {
            val am = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
            if (am != null) {
                val memInfo = ActivityManager.MemoryInfo()
                am.getMemoryInfo(memInfo)
                if (memInfo.totalMem > 0) {
                    val usedPct = (((memInfo.totalMem - memInfo.availMem) * 100L) / memInfo.totalMem).toInt().coerceIn(1, 99)
                    val ramColor = if (usedPct >= 85) COLOR_STATUS_WARN else COLOR_TEXT_PRIMARY
                    segments.add(TelemetrySegment("RAM $usedPct%", ramColor))
                }
            }
        }

        if ((showBat || showWatt) && batteryIntent != null) {
            val level = batteryIntent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
            val scale = batteryIntent.getIntExtra(BatteryManager.EXTRA_SCALE, 100).coerceAtLeast(1)
            val pct = ((level * 100f) / scale).roundToInt().coerceIn(0, 100)
            val status = batteryIntent.getIntExtra(BatteryManager.EXTRA_STATUS, BatteryManager.BATTERY_STATUS_UNKNOWN)
            val isCharging = status == BatteryManager.BATTERY_STATUS_CHARGING || status == BatteryManager.BATTERY_STATUS_FULL
            val voltageMv = batteryIntent.getIntExtra(BatteryManager.EXTRA_VOLTAGE, 3850).let {
                if (it in 1..20) it * 1000 else if (it <= 0) 3850 else it
            }

            if (showBat && level >= 0) {
                val batColor = when {
                    isCharging -> COLOR_STATUS_OK
                    pct <= 10 -> COLOR_STATUS_DANGER
                    pct <= 20 -> COLOR_STATUS_WARN
                    else -> COLOR_TEXT_PRIMARY
                }
                val label = if (isCharging) "CHG $pct%" else "$pct%"
                segments.add(TelemetrySegment(label, batColor))
            }

            if (showWatt || isCharging) {
                val watts = readBatteryWatts(context, voltageMv)
                if (watts > 0.05f) {
                    val prefix = if (isCharging) "+" else ""
                    val wattColor = if (isCharging) COLOR_STATUS_OK else COLOR_TEXT_PRIMARY
                    val formatted = String.format(Locale.US, "%s%.1fW", prefix, watts)
                    segments.add(TelemetrySegment(formatted, wattColor))
                }
            }
        }

        if (segments.isEmpty()) {
            val hz = LightspeedRefreshRateEngine.getLiveDisplayHz(context)
            segments.add(TelemetrySegment("$hz HZ", accentArgb))
        }
        return segments
    }

    private fun readBatteryWatts(context: Context, voltageMv: Int): Float {
        return try {
            val bm = context.getSystemService(Context.BATTERY_SERVICE) as? BatteryManager ?: return 0f
            val rawCurrent = bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CURRENT_NOW)
            if (rawCurrent == 0 || rawCurrent == Int.MIN_VALUE) return 0f
            val absCurrent = abs(rawCurrent).toFloat()
            // Android OEMs report CURRENT_NOW either in microamperes (µA) or milliamperes (mA)
            val amps = if (absCurrent > 15_000f) absCurrent / 1_000_000f else absCurrent / 1_000f
            val volts = voltageMv / 1000f
            (amps * volts).coerceIn(0f, 240f)
        } catch (_: Exception) {
            0f
        }
    }

    private fun readCpuClockLoadPercent(): Int {
        return try {
            var weightedLoadSum = 0.0
            var totalCapacityKhz = 0.0
            for (i in 0 until 12) {
                val curFile = File("/sys/devices/system/cpu/cpu$i/cpufreq/scaling_cur_freq")
                val maxFile = File("/sys/devices/system/cpu/cpu$i/cpufreq/cpuinfo_max_freq")
                val minFile = File("/sys/devices/system/cpu/cpu$i/cpufreq/cpuinfo_min_freq")
                if (!curFile.exists() || !maxFile.exists()) continue
                val curKhz = curFile.readText().trim().toLongOrNull() ?: continue
                val maxKhz = maxFile.readText().trim().toLongOrNull()?.coerceAtLeast(1L) ?: continue
                val minKhz = if (minFile.exists()) {
                    minFile.readText().trim().toLongOrNull()?.coerceIn(0L, maxKhz - 1L) ?: 0L
                } else 0L
                val spanKhz = (maxKhz - minKhz).coerceAtLeast(1L)
                val normRatio = ((curKhz - minKhz).toDouble() / spanKhz.toDouble()).coerceIn(0.0, 1.0)
                weightedLoadSum += normRatio * maxKhz.toDouble()
                totalCapacityKhz += maxKhz.toDouble()
            }
            if (totalCapacityKhz > 0.0) {
                ((weightedLoadSum / totalCapacityKhz) * 100.0).roundToInt().coerceIn(0, 100)
            } else {
                0
            }
        } catch (_: Exception) {
            0
        }
    }

    private class TelemetryMicroVisorView(context: Context) : View(context), Choreographer.FrameCallback {

        private val density = resources.displayMetrics.density

        private var segments: List<TelemetrySegment> = emptyList()
        private var lastMeasuredTextWidth = 0f
        @Volatile var isCompactFpsOnly = false
        private var frameCount = 0
        private var lastSampleTimeNs = 0L
        @Volatile private var measuredFps = 0
        private var fpsCounterRunning = false

        private var downRawX = 0f
        private var downRawY = 0f
        private var downLocalX = 0f
        private var initialWinX = 0
        private var initialWinY = 0
        private var isDragging = false

        private val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
            color = COLOR_SURFACE_BG
        }
        private val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = 1f * density
        }
        private val dotPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
        }
        private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
            textSize = 10f * density
        }
        private val sepPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            typeface = Typeface.create(Typeface.MONOSPACE, Typeface.NORMAL)
            textSize = 9.5f * density
            color = 0x66FFFFFF
        }
        private val bgRect = RectF()
        private val separatorStr = " | "

        fun startFpsCounter() {
            if (fpsCounterRunning) return
            fpsCounterRunning = true
            frameCount = 0
            lastSampleTimeNs = 0L
            Choreographer.getInstance().postFrameCallback(this)
        }

        fun stopFpsCounter() {
            fpsCounterRunning = false
            Choreographer.getInstance().removeFrameCallback(this)
        }

        override fun doFrame(frameTimeNanos: Long) {
            if (!fpsCounterRunning) return
            if (lastSampleTimeNs == 0L) {
                lastSampleTimeNs = frameTimeNanos
                frameCount = 0
            } else {
                frameCount++
                val elapsedNs = frameTimeNanos - lastSampleTimeNs
                if (elapsedNs >= 500_000_000L) {
                    val rawFps = ((frameCount * 1_000_000_000L).toDouble() / elapsedNs.toDouble()).roundToInt()
                    val maxHz = LightspeedRefreshRateEngine.getLiveDisplayHz(context)
                    measuredFps = rawFps.coerceIn(1, maxHz)
                    frameCount = 0
                    lastSampleTimeNs = frameTimeNanos
                }
            }
            Choreographer.getInstance().postFrameCallback(this)
        }

        fun getCurrentFps(ctx: Context): Int {
            val hz = LightspeedRefreshRateEngine.getLiveDisplayHz(ctx)
            val m = measuredFps
            return if (m <= 0) hz else m.coerceAtMost(hz)
        }

        fun updateSegments(newSegments: List<TelemetrySegment>) {
            val oldCount = segments.size
            segments = newSegments
            var newTextW = 0f
            val sepW = sepPaint.measureText(separatorStr)
            newSegments.forEachIndexed { idx, seg ->
                newTextW += textPaint.measureText(seg.text)
                if (idx < newSegments.lastIndex) newTextW += sepW
            }
            if (newSegments.size != oldCount || abs(newTextW - lastMeasuredTextWidth) > 2f * density) {
                lastMeasuredTextWidth = newTextW
                requestLayout()
            }
            invalidate()
        }

        override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
            val handleWidth = 18f * density
            val padRight = 9f * density
            val sepW = sepPaint.measureText(separatorStr)
            var textTotalW = 0f
            segments.forEachIndexed { idx, seg ->
                textTotalW += textPaint.measureText(seg.text)
                if (idx < segments.lastIndex) textTotalW += sepW
            }
            lastMeasuredTextWidth = textTotalW
            val totalW = (handleWidth + textTotalW + padRight).roundToInt().coerceAtLeast((68 * density).roundToInt())
            val totalH = (22f * density).roundToInt()
            setMeasuredDimension(totalW, totalH)
        }

        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)
            val w = width.toFloat()
            val h = height.toFloat()
            val radius = h / 2f
            val inset = 0.6f * density
            bgRect.set(inset, inset, w - inset, h - inset)

            val tintArgb = resolveOpaqueAccentArgb(context)
            borderPaint.color = (tintArgb and 0x00FFFFFF) or 0x77000000

            canvas.drawRoundRect(bgRect, radius, radius, bgPaint)
            canvas.drawRoundRect(bgRect, radius, radius, borderPaint)

            // Draw 6-dot tactile grip/compact-toggle handle on the left (2 columns x 3 rows)
            dotPaint.color = (tintArgb and 0x00FFFFFF) or 0xCC000000.toInt()
            val dotR = 1.15f * density
            val col1X = 7.5f * density
            val col2X = 11.5f * density
            val centerY = h / 2f
            val rowStep = 3.6f * density
            for (r in -1..1) {
                val dy = centerY + r * rowStep
                canvas.drawCircle(col1X, dy, dotR, dotPaint)
                canvas.drawCircle(col2X, dy, dotR, dotPaint)
            }

            // Draw telemetry segments
            val fm = textPaint.fontMetrics
            val baselineY = h / 2f - (fm.ascent + fm.descent) / 2f
            var curX = 17.5f * density
            val sepW = sepPaint.measureText(separatorStr)

            segments.forEachIndexed { idx, seg ->
                textPaint.color = seg.colorArgb or 0xFF000000.toInt()
                canvas.drawText(seg.text, curX, baselineY, textPaint)
                curX += textPaint.measureText(seg.text)
                if (idx < segments.lastIndex) {
                    canvas.drawText(separatorStr, curX, baselineY, sepPaint)
                    curX += sepW
                }
            }
        }

        override fun onTouchEvent(event: MotionEvent): Boolean {
            val params = windowParams ?: return false
            val service = LightspeedAccessibilityService.instance ?: return false
            val wm = service.getSystemService(Context.WINDOW_SERVICE) as? WindowManager ?: return false
            val dm = resources.displayMetrics

            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    downRawX = event.rawX
                    downRawY = event.rawY
                    downLocalX = event.x
                    initialWinX = params.x
                    initialWinY = params.y
                    isDragging = false
                    return true
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = event.rawX - downRawX
                    val dy = event.rawY - downRawY
                    if (!isDragging && (abs(dx) > 4f * density || abs(dy) > 4f * density)) {
                        isDragging = true
                        LightspeedHapticEngine.tick(context)
                    }
                    if (isDragging) {
                        val maxX = (dm.widthPixels - width).coerceAtLeast(0)
                        val maxY = (dm.heightPixels - height).coerceAtLeast(0)
                        params.x = (initialWinX + dx.roundToInt()).coerceIn(0, maxX)
                        params.y = (initialWinY + dy.roundToInt()).coerceIn(0, maxY)
                        try {
                            wm.updateViewLayout(this, params)
                        } catch (_: Exception) {}
                    }
                    return true
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    if (isDragging) {
                        context.defaultPrefs().edit()
                            .putInt(LightspeedPreferences.KEY_TELEMETRY_HUD_POS_X, params.x)
                            .putInt(LightspeedPreferences.KEY_TELEMETRY_HUD_POS_Y, params.y)
                            .apply()
                        isDragging = false
                    } else if (event.actionMasked == MotionEvent.ACTION_UP && downLocalX <= 22f * density) {
                        // Tapping the 6-dot handle toggles Compact FPS-Only Mini-Badge Mode vs Expanded Strip
                        isCompactFpsOnly = !isCompactFpsOnly
                        LightspeedHapticEngine.tick(context)
                        updateSegments(collectSegments(context))
                    }
                    return true
                }
            }
            return super.onTouchEvent(event)
        }
    }
}
