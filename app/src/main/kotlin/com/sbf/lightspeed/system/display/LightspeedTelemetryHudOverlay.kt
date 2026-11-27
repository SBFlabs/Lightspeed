package com.sbf.lightspeed.system.display

import android.annotation.SuppressLint
import android.app.KeyguardManager
import android.content.Context
import android.graphics.Canvas
import android.graphics.PixelFormat
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.Choreographer
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import com.sbf.lightspeed.LightspeedAccessibilityService
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
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.roundToInt

/**
 * Per-App Tactical Telemetry Micro-HUD (FPS, CPU, TEMP, RAM, BAT, WATT).
 *
 * Uses a 3-window architecture so the entire telemetry readout body (`visorView`) carries
 * `FLAG_NOT_TOUCHABLE` for 100% native OS pass-through (taps, swipes, holds, gaming controls),
 * while compact left & right transparent handle windows (`leftHandleView`, `rightHandleView`)
 * are dedicated purely to gripping & maneuvering — morphing into sculpted rocket nozzles
 * and firing Newtonian exhaust plumes opposite to drag direction across all 4 HUD styles.
 */
object LightspeedTelemetryHudOverlay {

    private const val TAG = "TelemetryHudOverlay"

    private val mainHandler = Handler(Looper.getMainLooper())
    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())

    private var visorView: TelemetryVisorView? = null
    private var visorParams: WindowManager.LayoutParams? = null
    private var leftHandleView: TelemetryHandleTouchView? = null
    private var leftHandleParams: WindowManager.LayoutParams? = null
    private var rightHandleView: TelemetryHandleTouchView? = null
    private var rightHandleParams: WindowManager.LayoutParams? = null

    private var coreX: Int = 0
    private var coreY: Int = 0

    private var pollJob: Job? = null
    @Volatile private var currentForegroundPkg: String? = null
    @Volatile private var previewUntilUptimeMs: Long = 0L

    private val previewTimeoutRunnable = Runnable {
        previewUntilUptimeMs = 0L
        LightspeedAccessibilityService.instance?.let { evaluateVisibility(it) }
    }

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
            visorView?.let { view ->
                val metrics = collectMetrics(context)
                view.updateMetrics(metrics)
                syncHandleWindowsToVisor(context)
            }
        }
    }

    fun triggerLivePreview(context: Context, durationMs: Long = 4500L) {
        previewUntilUptimeMs = SystemClock.uptimeMillis() + durationMs.coerceIn(1500L, 10_000L)
        mainHandler.removeCallbacks(previewTimeoutRunnable)
        mainHandler.postDelayed(previewTimeoutRunnable, durationMs.coerceIn(1500L, 10_000L) + 50L)
        refreshState(context)
    }

    fun onConfigurationChanged(context: Context) {
        mainHandler.post {
            evaluateVisibility(context)
            syncHandleWindowsToVisor(context)
        }
    }

    /** Computes a default top Y (px) comfortably below the Android status bar, camera cutout, and Lightspeed top sensor strip. */
    private fun computeDefaultSafeTopYPx(context: Context): Int {
        val dm = context.resources.displayMetrics
        val isLandscape = dm.widthPixels > dm.heightPixels
        val statusBarResId = context.resources.getIdentifier("status_bar_height", "dimen", "android")
        val statusBarResPx = if (statusBarResId > 0) context.resources.getDimensionPixelSize(statusBarResId) else 0
        val windowInsetTopPx = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
            try {
                val wm = context.getSystemService(Context.WINDOW_SERVICE) as? WindowManager
                val insets = wm?.currentWindowMetrics?.windowInsets
                insets?.getInsetsIgnoringVisibility(
                    android.view.WindowInsets.Type.statusBars() or android.view.WindowInsets.Type.displayCutout()
                )?.top ?: 0
            } catch (_: Exception) {
                0
            }
        } else {
            0
        }
        val minClearancePx = ((if (isLandscape) 14f else 34f) * dm.density).roundToInt()
        val topSystemClearance = maxOf(statusBarResPx, windowInsetTopPx, minClearancePx)
        val extraGapPx = ((if (isLandscape) 10f else 18f) * dm.density).roundToInt()
        return (topSystemClearance + extraGapPx).coerceIn(0, (dm.heightPixels * 0.35f).roundToInt().coerceAtLeast(1))
    }

    fun resetPosition(context: Context) {
        val safeY = computeDefaultSafeTopYPx(context)
        context.defaultPrefs().edit()
            .putInt(LightspeedPreferences.KEY_TELEMETRY_HUD_POS_X, -1)
            .putInt(LightspeedPreferences.KEY_TELEMETRY_HUD_POS_Y, safeY)
            .apply()
        mainHandler.post {
            val view = visorView
            val service = LightspeedAccessibilityService.instance
            if (view != null && service != null) {
                val dm = service.resources.displayMetrics
                val spec = view.currentSpec()
                coreX = ((dm.widthPixels - spec.coreWidthPx) / 2).coerceAtLeast(8)
                coreY = safeY.coerceIn(0, (dm.heightPixels - spec.coreHeightPx).coerceAtLeast(0))
                syncHandleWindowsToVisor(service)
            }
        }
        triggerLivePreview(context)
    }

    fun refreshDisplayModeVote(context: Context) {
        mainHandler.post {
            val view = visorView ?: return@post
            val params = visorParams ?: return@post
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
        val service = LightspeedAccessibilityService.instance ?: run {
            detachOverlay()
            return
        }
        val kg = service.getSystemService(Context.KEYGUARD_SERVICE) as? KeyguardManager
        if (kg?.isKeyguardLocked == true) {
            detachOverlay()
            return
        }

        val isPreview = SystemClock.uptimeMillis() < previewUntilUptimeMs
        if (!isPreview) {
            if (!LightspeedPreferences.isMasterFlightArmed(service)) {
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
        }

        attachOverlayIfNeeded(service)
    }

    private fun applyDisplayCutoutMode(params: WindowManager.LayoutParams) {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
            params.layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
        } else if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
            params.layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
        }
    }

    private fun createHandleParams(xPos: Int, yPos: Int, wPx: Int, hPx: Int): WindowManager.LayoutParams {
        return WindowManager.LayoutParams(
            wPx.coerceAtLeast(1),
            hPx.coerceAtLeast(1),
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        ).apply {
            @Suppress("RtlHardcoded")
            gravity = Gravity.TOP or Gravity.LEFT
            x = xPos
            y = yPos
            applyDisplayCutoutMode(this)
        }
    }

    private fun attachOverlayIfNeeded(service: LightspeedAccessibilityService) {
        if (visorView != null) return
        val wm = service.getSystemService(Context.WINDOW_SERVICE) as? WindowManager ?: return
        val prefs = service.defaultPrefs()
        val dm = service.resources.displayMetrics

        val view = TelemetryVisorView(service)
        val initialMetrics = collectMetrics(service)
        view.updateMetrics(initialMetrics)
        val spec = view.currentSpec()

        val defaultSafeY = computeDefaultSafeTopYPx(service)
        val savedX = prefs.getInt(LightspeedPreferences.KEY_TELEMETRY_HUD_POS_X, -1)
        val rawSavedY = prefs.getInt(LightspeedPreferences.KEY_TELEMETRY_HUD_POS_Y, -1)
        val resolvedY = if (rawSavedY < 0 || (savedX == -1 && rawSavedY <= (18 * dm.density).roundToInt())) {
            prefs.edit().putInt(LightspeedPreferences.KEY_TELEMETRY_HUD_POS_Y, defaultSafeY).apply()
            defaultSafeY
        } else {
            rawSavedY
        }
        coreX = if (savedX >= 0) savedX.coerceIn(0, (dm.widthPixels - spec.coreWidthPx).coerceAtLeast(0))
        else ((dm.widthPixels - spec.coreWidthPx) / 2).coerceAtLeast(8)
        coreY = resolvedY.coerceIn(0, (dm.heightPixels - spec.coreHeightPx).coerceAtLeast(0))

        val vParams = WindowManager.LayoutParams(
            spec.totalWidthPx,
            spec.totalHeightPx,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        ).apply {
            @Suppress("RtlHardcoded")
            gravity = Gravity.TOP or Gravity.LEFT
            x = coreX - spec.plumeMarginXPx
            y = coreY - spec.plumeMarginYPx
            applyDisplayCutoutMode(this)
        }
        applyPreferredModeToParams(service, vParams)

        val handleW = spec.handleWidthPx
        val padOutPx = (8f * dm.density).roundToInt()
        val padVertPx = (6f * dm.density).roundToInt()
        val touchW = handleW + padOutPx
        val touchH = spec.coreHeightPx + padVertPx * 2
        val leftView = TelemetryHandleTouchView(service, isLeft = true)
        val rightView = TelemetryHandleTouchView(service, isLeft = false)
        val lParams = createHandleParams(coreX - padOutPx, coreY - padVertPx, touchW, touchH)
        val rParams = createHandleParams(coreX + spec.coreWidthPx - handleW, coreY - padVertPx, touchW, touchH)

        try {
            wm.addView(view, vParams)
            visorView = view
            visorParams = vParams
            wm.addView(leftView, lParams)
            leftHandleView = leftView
            leftHandleParams = lParams
            wm.addView(rightView, rParams)
            rightHandleView = rightView
            rightHandleParams = rParams
            view.syncFrameLoop()
            startPolling(service)
        } catch (e: Exception) {
            logSwallowed(TAG, "attachOverlayIfNeeded", e)
            detachOverlay()
        }
    }

    fun detachOverlay() {
        mainHandler.removeCallbacks(previewTimeoutRunnable)
        pollJob?.cancel()
        pollJob = null
        val vView = visorView
        val lView = leftHandleView
        val rView = rightHandleView
        vView?.stopFrameLoop()
        visorView = null
        visorParams = null
        leftHandleView = null
        leftHandleParams = null
        rightHandleView = null
        rightHandleParams = null

        val fallbackWm = LightspeedAccessibilityService.instance?.getSystemService(Context.WINDOW_SERVICE) as? WindowManager
        listOfNotNull(vView, lView, rView).forEach { v ->
            try {
                val wm = (v.context.getSystemService(Context.WINDOW_SERVICE) as? WindowManager) ?: fallbackWm
                if (v.isAttachedToWindow) wm?.removeView(v)
            } catch (e: Exception) {
                logSwallowed(TAG, "detachOverlay", e)
            }
        }
    }

    private fun syncHandleWindowsToVisor(context: Context) {
        val vView = visorView ?: return
        val vParams = visorParams ?: return
        val service = LightspeedAccessibilityService.instance ?: return
        val wm = service.getSystemService(Context.WINDOW_SERVICE) as? WindowManager ?: return
        val dm = service.resources.displayMetrics
        val spec = vView.currentSpec()

        val maxX = (dm.widthPixels - spec.coreWidthPx).coerceAtLeast(0)
        val maxY = (dm.heightPixels - spec.coreHeightPx).coerceAtLeast(0)
        coreX = coreX.coerceIn(0, maxX)
        coreY = coreY.coerceIn(0, maxY)

        val targetVX = coreX - spec.plumeMarginXPx
        val targetVY = coreY - spec.plumeMarginYPx
        if (vParams.x != targetVX || vParams.y != targetVY || vParams.width != spec.totalWidthPx || vParams.height != spec.totalHeightPx) {
            vParams.x = targetVX
            vParams.y = targetVY
            vParams.width = spec.totalWidthPx
            vParams.height = spec.totalHeightPx
            try { wm.updateViewLayout(vView, vParams) } catch (_: Exception) {}
        }

        val effHandleW = spec.handleWidthPx
        val padOutPx = (8f * dm.density).roundToInt()
        val padVertPx = (6f * dm.density).roundToInt()
        val touchW = effHandleW + padOutPx
        val touchH = spec.coreHeightPx + padVertPx * 2
        val touchY = coreY - padVertPx
        leftHandleView?.let { lv ->
            val lp = leftHandleParams ?: return@let
            val targetLX = coreX - padOutPx
            if (lp.x != targetLX || lp.y != touchY || lp.width != touchW || lp.height != touchH) {
                lp.x = targetLX
                lp.y = touchY
                lp.width = touchW
                lp.height = touchH
                try { wm.updateViewLayout(lv, lp) } catch (_: Exception) {}
            }
        }
        rightHandleView?.let { rv ->
            val rp = rightHandleParams ?: return@let
            val targetRX = coreX + spec.coreWidthPx - effHandleW
            if (rp.x != targetRX || rp.y != touchY || rp.width != touchW || rp.height != touchH) {
                rp.x = targetRX
                rp.y = touchY
                rp.width = touchW
                rp.height = touchH
                try { wm.updateViewLayout(rv, rp) } catch (_: Exception) {}
            }
        }
    }

    private fun startPolling(context: Context) {
        pollJob?.cancel()
        pollJob = scope.launch {
            while (isActive && visorView != null) {
                val metrics = collectMetrics(context)
                withContext(Dispatchers.Main) {
                    visorView?.updateMetrics(metrics)
                    syncHandleWindowsToVisor(context)
                }
                delay(1000L)
            }
        }
    }

    private fun collectMetrics(context: Context): List<TelemetryMetricItem> {
        return LightspeedTelemetrySampler.collectMetrics(context) { ctx ->
            visorView?.getCurrentFps(ctx) ?: LightspeedRefreshRateEngine.getLiveDisplayHz(ctx).coerceAtLeast(60)
        }
    }

    private class TelemetryVisorView(context: Context) : View(context), Choreographer.FrameCallback {

        private val density = resources.displayMetrics.density
        private var metrics: List<TelemetryMetricItem> = emptyList()
        private var cachedSpec = LightspeedTelemetryHudRenderer.measureHud(context, activeStyleKey(), metrics, density)
        private var lastMeasureSig = ""
        private var pressState = HandlePressState.NONE

        // FPS sampling state
        private var frameCount = 0
        private var lastSampleTimeNs = 0L
        @Volatile private var measuredFps = 0
        private var frameLoopRunning = false

        // 120Hz Newtonian thruster physics state
        private var isHandleHeld = false
        private var lastFrameNs = 0L
        private var lastMoveUptimeMs = 0L
        private var morphProgress = 0f
        private var leftThrust = 0f
        private var rightThrust = 0f
        private var verticalGimbal = 0f
        private var targetLeftThrust = 0f
        private var targetRightThrust = 0f
        private var targetGimbalY = 0f
        private var flamePhase = 0f

        fun currentSpec(): HudMeasureSpec = cachedSpec

        private fun needsContinuousFrameLoop(): Boolean {
            if (isHandleHeld || morphProgress > 0f || leftThrust > 0f || rightThrust > 0f) return true
            val prefs = context.defaultPrefs()
            return prefs.getBoolean(LightspeedPreferences.KEY_TELEMETRY_HUD_COMPACT, false) ||
                prefs.getBoolean(LightspeedPreferences.KEY_TELEMETRY_HUD_SHOW_FPS, true)
        }

        fun syncFrameLoop() {
            if (needsContinuousFrameLoop()) startFrameLoop() else stopFrameLoop()
        }

        fun onHandleEngage(isLeft: Boolean) {
            pressState = if (isLeft) HandlePressState.LEFT else HandlePressState.RIGHT
            isHandleHeld = true
            lastMoveUptimeMs = SystemClock.uptimeMillis()
            targetLeftThrust = 0.14f
            targetRightThrust = 0.14f
            targetGimbalY = 0f
            startFrameLoop()
            invalidate()
        }

        fun onHandleMotion(vxDpS: Float, vyDpS: Float) {
            lastMoveUptimeMs = SystemClock.uptimeMillis()
            // Moving right (+vx) fires LEFT thruster; moving left (-vx) fires RIGHT thruster
            when {
                vxDpS > 12f -> {
                    targetLeftThrust = (vxDpS / 280f).coerceIn(0.24f, 1f)
                    targetRightThrust = 0.06f
                }
                vxDpS < -12f -> {
                    targetRightThrust = (-vxDpS / 280f).coerceIn(0.24f, 1f)
                    targetLeftThrust = 0.06f
                }
                abs(vyDpS) > 22f -> {
                    val vThrust = (abs(vyDpS) / 360f).coerceIn(0.22f, 0.72f)
                    targetLeftThrust = vThrust
                    targetRightThrust = vThrust
                }
            }
            // Moving UP (-vy) tilts flames DOWN (+gimbal); moving DOWN (+vy) tilts flames UP (-gimbal)
            targetGimbalY = (-vyDpS / 380f).coerceIn(-0.45f, 0.45f)
            startFrameLoop()
            invalidate()
        }

        fun onHandleRelease() {
            pressState = HandlePressState.NONE
            isHandleHeld = false
            targetLeftThrust = 0f
            targetRightThrust = 0f
            targetGimbalY = 0f
            invalidate()
        }

        fun startFrameLoop() {
            if (frameLoopRunning) return
            frameLoopRunning = true
            frameCount = 0
            lastSampleTimeNs = 0L
            lastFrameNs = 0L
            Choreographer.getInstance().postFrameCallback(this)
        }

        fun stopFrameLoop() {
            frameLoopRunning = false
            lastSampleTimeNs = 0L
            lastFrameNs = 0L
            Choreographer.getInstance().removeFrameCallback(this)
        }

        override fun doFrame(frameTimeNanos: Long) {
            if (!frameLoopRunning) return
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

            val dtSec = if (lastFrameNs == 0L) 0.016f else ((frameTimeNanos - lastFrameNs) / 1_000_000_000f).coerceIn(0.002f, 0.05f)
            lastFrameNs = frameTimeNanos

            if (isHandleHeld && (SystemClock.uptimeMillis() - lastMoveUptimeMs) > 55L) {
                targetLeftThrust = 0.10f
                targetRightThrust = 0.10f
                targetGimbalY = 0f
            }

            val targetMorph = if (isHandleHeld) 1f else 0f
            val morphStep = (dtSec * 7.5f).coerceIn(0f, 1f)
            val thrustStep = (dtSec * 12f).coerceIn(0f, 1f)
            morphProgress += (targetMorph - morphProgress) * morphStep
            leftThrust += (targetLeftThrust - leftThrust) * thrustStep
            rightThrust += (targetRightThrust - rightThrust) * thrustStep
            verticalGimbal += (targetGimbalY - verticalGimbal) * thrustStep

            if (morphProgress < 0.006f && !isHandleHeld) morphProgress = 0f
            if (leftThrust < 0.005f && targetLeftThrust == 0f) leftThrust = 0f
            if (rightThrust < 0.005f && targetRightThrust == 0f) rightThrust = 0f

            if (isHandleHeld || morphProgress > 0f || leftThrust > 0f || rightThrust > 0f) {
                flamePhase = (flamePhase + dtSec * 3.2f) % 62.8318f
                invalidate()
            }

            if (needsContinuousFrameLoop()) {
                Choreographer.getInstance().postFrameCallback(this)
            } else {
                stopFrameLoop()
            }
        }

        fun getCurrentFps(ctx: Context): Int {
            val hz = LightspeedRefreshRateEngine.getLiveDisplayHz(ctx)
            val m = measuredFps
            return if (m <= 0) hz else m.coerceAtMost(hz)
        }

        private fun activeStyleKey(): String {
            val raw = context.defaultPrefs().getString(
                LightspeedPreferences.KEY_TELEMETRY_HUD_STYLE,
                LightspeedPreferences.DEFAULT_TELEMETRY_HUD_STYLE
            )
            return LightspeedTelemetryHudRenderer.normalizeStyleKey(raw)
        }

        fun updateMetrics(newMetrics: List<TelemetryMetricItem>) {
            metrics = newMetrics
            val prefs = context.defaultPrefs()
            val style = activeStyleKey()
            val scalePct = prefs.getInt(LightspeedPreferences.KEY_TELEMETRY_HUD_SCALE, LightspeedPreferences.DEFAULT_TELEMETRY_HUD_SCALE)
            val fontKey = prefs.getString(LightspeedPreferences.KEY_TELEMETRY_HUD_FONT, LightspeedPreferences.DEFAULT_TELEMETRY_HUD_FONT) ?: ""
            cachedSpec = LightspeedTelemetryHudRenderer.measureHud(context, style, newMetrics, density)
            val sig = "$style|$scalePct|$fontKey|${cachedSpec.totalWidthPx}x${cachedSpec.totalHeightPx}"
            if (sig != lastMeasureSig) {
                lastMeasureSig = sig
                requestLayout()
            }
            syncFrameLoop()
            invalidate()
        }

        override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
            cachedSpec = LightspeedTelemetryHudRenderer.measureHud(context, activeStyleKey(), metrics, density)
            setMeasuredDimension(cachedSpec.totalWidthPx, cachedSpec.totalHeightPx)
        }

        override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
            super.onSizeChanged(w, h, oldw, oldh)
            post { syncHandleWindowsToVisor(context) }
        }

        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)
            LightspeedTelemetryHudRenderer.drawHud(
                canvas = canvas,
                context = context,
                styleKey = activeStyleKey(),
                items = metrics,
                spec = cachedSpec,
                pressState = pressState,
                thruster = ThrusterRenderState(
                    morph = morphProgress,
                    leftThrust = leftThrust,
                    rightThrust = rightThrust,
                    verticalGimbal = verticalGimbal,
                    flamePhase = flamePhase
                ),
                d = density
            )
        }
    }

    @SuppressLint("ViewConstructor")
    private class TelemetryHandleTouchView(
        context: Context,
        private val isLeft: Boolean
    ) : View(context) {

        private val density = resources.displayMetrics.density
        private var downRawX = 0f
        private var downRawY = 0f
        private var prevRawX = 0f
        private var prevRawY = 0f
        private var prevUptimeMs = 0L
        private var initialCoreX = 0
        private var initialCoreY = 0
        private var isDragging = false

        @SuppressLint("ClickableViewAccessibility")
        override fun onTouchEvent(event: MotionEvent): Boolean {
            val vView = visorView ?: return false
            val service = LightspeedAccessibilityService.instance ?: return false
            val dm = resources.displayMetrics

            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    downRawX = event.rawX
                    downRawY = event.rawY
                    prevRawX = event.rawX
                    prevRawY = event.rawY
                    prevUptimeMs = SystemClock.uptimeMillis()
                    initialCoreX = coreX
                    initialCoreY = coreY
                    isDragging = false
                    LightspeedHapticEngine.tick(context)
                    vView.onHandleEngage(isLeft)
                    return true
                }
                MotionEvent.ACTION_MOVE -> {
                    val nowMs = SystemClock.uptimeMillis()
                    val dtSec = ((nowMs - prevUptimeMs).coerceIn(4L, 80L)) / 1000f
                    val stepDx = event.rawX - prevRawX
                    val stepDy = event.rawY - prevRawY
                    prevRawX = event.rawX
                    prevRawY = event.rawY
                    prevUptimeMs = nowMs

                    val totalDx = event.rawX - downRawX
                    val totalDy = event.rawY - downRawY
                    if (!isDragging && hypot(totalDx.toDouble(), totalDy.toDouble()) > (3f * density)) {
                        isDragging = true
                    }
                    if (isDragging) {
                        val spec = vView.currentSpec()
                        val maxX = (dm.widthPixels - spec.coreWidthPx).coerceAtLeast(0)
                        val maxY = (dm.heightPixels - spec.coreHeightPx).coerceAtLeast(0)
                        coreX = (initialCoreX + totalDx.roundToInt()).coerceIn(0, maxX)
                        coreY = (initialCoreY + totalDy.roundToInt()).coerceIn(0, maxY)
                        syncHandleWindowsToVisor(service)
                    }
                    vView.onHandleMotion(
                        vxDpS = (stepDx / density) / dtSec,
                        vyDpS = (stepDy / density) / dtSec
                    )
                    return true
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    vView.onHandleRelease()
                    if (isDragging) {
                        context.defaultPrefs().edit()
                            .putInt(LightspeedPreferences.KEY_TELEMETRY_HUD_POS_X, coreX)
                            .putInt(LightspeedPreferences.KEY_TELEMETRY_HUD_POS_Y, coreY)
                            .apply()
                        isDragging = false
                    }
                    return true
                }
            }
            return super.onTouchEvent(event)
        }
    }
}
