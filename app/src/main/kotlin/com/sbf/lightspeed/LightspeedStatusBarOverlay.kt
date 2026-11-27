package com.sbf.lightspeed

import com.sbf.lightspeed.system.logSwallowed
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
import com.sbf.lightspeed.system.LightspeedPreferences
import com.sbf.lightspeed.system.LightspeedTimeoutEngine
import com.sbf.lightspeed.system.defaultPrefs
import kotlin.math.abs
import kotlin.math.hypot

class LightspeedStatusBarOverlay(
    context: Context,
    internal val service: AccessibilityService
) : View(context) {

    internal val prefs = service.defaultPrefs()

    internal val debugPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#4DB6AC")
        style = Paint.Style.FILL
    }

    internal val hudPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#8000E5FF")
        strokeWidth = 4f
        style = Paint.Style.STROKE
    }

    internal val hudFillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#2600E5FF")
        style = Paint.Style.FILL
    }

    internal val hudTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textSize = 28f
        textAlign = Paint.Align.CENTER
        isFakeBoldText = true
    }

    data class StatusBarRenderCache(
        var isSensorEnabled: Boolean = true,
        var isExpanded: Boolean = true,
        var isPreview: Boolean = false,
        var transparencyPct: Int = 0,
        var spanPref: Int = 1080,
        var sensorOffsetX: Int = 0,
        var thicknessDp: Int = 48,
        var sensorOffsetY: Int = 0,
        var railOrientMode: String = "both",
        var dlRouting: String = "top_line",
        var mediaRouting: String = "none",
        var railSpanPref: Int = 1080,
        var railAlign: String = "center",
        var railOffsetX: Int = 0,
        var railOffsetY: Int = 0,
        var isStatusBarGuard: Boolean = true,
        var safePaddingLeftDp: Float = 0f,
        var safePaddingRightDp: Float = 0f,
        var textSafeLeftDp: Float = 42f,
        var textSafeRightDp: Float = 58f,
        var railThicknessDp: Int = 2,
        var railGlowPct: Int = 60,
        var railTrackOpacityPct: Int = 15,
        var colorMode: String = "cover_art",
        var maxRails: Int = 2,
        var isRailPreviewActive: Boolean = false,
        var railPriority: String = "downloads_top",
        var customColorHex: String = "#00E5FF",
        var isTextEnabled: Boolean = true,
        var textOrientMode: String = "both",
        var textCasing: String = "natural",
        var metadataMode: String = "adaptive",
        var isShowTimestamp: Boolean = false,
        var textSizeDp: Float = 9f,
        var fontSetting: String = "system_default",
        var isContrastShield: Boolean = true,
        var textPos: String = "below",
        var textOffsetY: Int = 0,
        var stackSpacingDp: Float = 0f,
        var isDropShadow: Boolean = true,
        var isTerminalCaps: Boolean = true,
        var speedDp: Float = 20f,
        var marqueeAnimMode: String = "continuous_wrap",
        var marqueeDirection: String = "rtl",
        var marqueeScope: String = "both_wings",
        var isAvoidCutout: Boolean = false,
        var wingGapDp: Float = 4f,
        var customCutoutWidth: Int = -1,
        var customCutoutOffsetX: Int = 0
    )

    internal var renderCache = StatusBarRenderCache()

    internal fun updateRenderCache() {
        val d = resources.displayMetrics.density
        val screenW = resources.displayMetrics.widthPixels.toFloat()
        val screenWidthDp = (screenW / d).toInt()
        val isLandscape = resources.configuration.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE
        val isSensorEnabled = prefs.getBoolean(LightspeedPreferences.KEY_STATUSBAR_ENABLED, false)
        renderCache = StatusBarRenderCache(
            isSensorEnabled = isSensorEnabled,
            isExpanded = prefs.getBoolean(LightspeedPreferences.KEY_SECTION_STATUSBAR_EXPANDED, true),
            isPreview = prefs.getBoolean(LightspeedPreferences.KEY_STATUSBAR_PREVIEW, false),
            transparencyPct = if (isSensorEnabled) prefs.getInt(LightspeedPreferences.KEY_STATUSBAR_TRANSPARENCY, 0) else 0,
            spanPref = if (isLandscape) prefs.getInt(LightspeedPreferences.KEY_STATUSBAR_SPAN_LANDSCAPE, 150) else prefs.getInt(LightspeedPreferences.KEY_STATUSBAR_SPAN, 200),
            sensorOffsetX = if (isLandscape) prefs.getInt(LightspeedPreferences.KEY_STATUSBAR_OFFSET_X_LANDSCAPE, 300) else prefs.getInt(LightspeedPreferences.KEY_STATUSBAR_OFFSET_X, 105),
            thicknessDp = (if (isLandscape) prefs.getInt(LightspeedPreferences.KEY_STATUSBAR_THICKNESS_LANDSCAPE, 35) else prefs.getInt(LightspeedPreferences.KEY_STATUSBAR_THICKNESS, 35)).coerceIn(10, 52),
            sensorOffsetY = prefs.getInt(LightspeedPreferences.KEY_STATUSBAR_OFFSET_Y, 0).coerceAtLeast(0),
            railOrientMode = prefs.getString(LightspeedPreferences.KEY_HORIZON_RAIL_ORIENTATION_MODE, "both") ?: "both",
            dlRouting = prefs.getString(LightspeedPreferences.KEY_RAIL_DOWNLOADS_ROUTING, "top_line") ?: "top_line",
            mediaRouting = prefs.getString(LightspeedPreferences.KEY_RAIL_MEDIA_ROUTING, "none") ?: "none",
            railSpanPref = prefs.getInt(LightspeedPreferences.KEY_HORIZON_RAIL_SPAN, 1080),
            railAlign = prefs.getString(LightspeedPreferences.KEY_HORIZON_RAIL_ALIGN, "center") ?: "center",
            railOffsetX = prefs.getInt(LightspeedPreferences.KEY_HORIZON_RAIL_OFFSET_X, 0),
            railOffsetY = prefs.getInt(LightspeedPreferences.KEY_HORIZON_RAIL_OFFSET_Y, 0),
            isStatusBarGuard = prefs.getBoolean(LightspeedPreferences.KEY_HORIZON_RAIL_STATUS_BAR_GUARD, true),
            safePaddingLeftDp = prefs.getInt(LightspeedPreferences.KEY_HORIZON_RAIL_SAFE_PADDING_LEFT, 0).toFloat(),
            safePaddingRightDp = prefs.getInt(LightspeedPreferences.KEY_HORIZON_RAIL_SAFE_PADDING_RIGHT, 0).toFloat(),
            textSafeLeftDp = prefs.getInt(LightspeedPreferences.KEY_HORIZON_RAIL_TEXT_SAFE_LEFT, 42).toFloat(),
            textSafeRightDp = prefs.getInt(LightspeedPreferences.KEY_HORIZON_RAIL_TEXT_SAFE_RIGHT, 58).toFloat(),
            railThicknessDp = prefs.getInt(LightspeedPreferences.KEY_HORIZON_RAIL_THICKNESS, 2).coerceIn(1, 6),
            railGlowPct = prefs.getInt(LightspeedPreferences.KEY_HORIZON_RAIL_GLOW, 60).coerceIn(0, 100),
            railTrackOpacityPct = prefs.getInt(LightspeedPreferences.KEY_HORIZON_RAIL_TRACK_OPACITY, 15).coerceIn(0, 100),
            colorMode = prefs.getString(LightspeedPreferences.KEY_HORIZON_RAIL_COLOR_MODE, "cover_art") ?: "cover_art",
            maxRails = prefs.getInt(LightspeedPreferences.KEY_HORIZON_RAIL_MAX_COUNT, 2).coerceIn(1, 3),
            isRailPreviewActive = prefs.getBoolean(LightspeedPreferences.KEY_HORIZON_RAIL_PREVIEW, false),
            railPriority = prefs.getString(LightspeedPreferences.KEY_HORIZON_RAIL_PRIORITY, "downloads_top") ?: "downloads_top",
            customColorHex = prefs.getString(LightspeedPreferences.KEY_HORIZON_RAIL_CUSTOM_COLOR, "#00E5FF") ?: "#00E5FF",
            isTextEnabled = prefs.getBoolean(LightspeedPreferences.KEY_HORIZON_RAIL_TEXT_ENABLED, true),
            textOrientMode = prefs.getString(LightspeedPreferences.KEY_HORIZON_RAIL_TEXT_ORIENTATION_MODE, "both") ?: "both",
            textCasing = prefs.getString(LightspeedPreferences.KEY_HORIZON_RAIL_TEXT_CASING, "natural") ?: "natural",
            metadataMode = prefs.getString(LightspeedPreferences.KEY_HORIZON_RAIL_TEXT_METADATA_MODE, "adaptive") ?: "adaptive",
            isShowTimestamp = prefs.getBoolean(LightspeedPreferences.KEY_HORIZON_RAIL_TEXT_SHOW_TIMESTAMP, false),
            textSizeDp = prefs.getInt(LightspeedPreferences.KEY_HORIZON_RAIL_TEXT_SIZE, 9).coerceIn(7, 16).toFloat(),
            fontSetting = prefs.getString(LightspeedPreferences.KEY_HORIZON_RAIL_TEXT_FONT, "system_default") ?: "system_default",
            isContrastShield = prefs.getBoolean(LightspeedPreferences.KEY_HORIZON_RAIL_CONTRAST_SHIELD, true),
            textPos = prefs.getString(LightspeedPreferences.KEY_HORIZON_RAIL_TEXT_POSITION, "below") ?: "below",
            textOffsetY = prefs.getInt(LightspeedPreferences.KEY_HORIZON_RAIL_TEXT_OFFSET_Y, 0),
            stackSpacingDp = prefs.getInt(LightspeedPreferences.KEY_HORIZON_RAIL_STACK_SPACING, 0).coerceIn(0, 6).toFloat(),
            isDropShadow = prefs.getBoolean(LightspeedPreferences.KEY_HORIZON_RAIL_DROP_SHADOW, true),
            isTerminalCaps = prefs.getBoolean(LightspeedPreferences.KEY_HORIZON_RAIL_TERMINAL_CAPS, true),
            speedDp = prefs.getInt(LightspeedPreferences.KEY_HORIZON_RAIL_TEXT_SPEED, 20).coerceIn(10, 80).toFloat(),
            marqueeAnimMode = prefs.getString(LightspeedPreferences.KEY_HORIZON_RAIL_MARQUEE_ANIM_MODE, "continuous_wrap") ?: "continuous_wrap",
            marqueeDirection = prefs.getString(LightspeedPreferences.KEY_HORIZON_RAIL_MARQUEE_DIRECTION, "rtl") ?: "rtl",
            marqueeScope = prefs.getString(LightspeedPreferences.KEY_HORIZON_RAIL_MARQUEE_SCOPE, "both_wings") ?: "both_wings",
            isAvoidCutout = prefs.getBoolean(LightspeedPreferences.KEY_HORIZON_RAIL_AVOID_CUTOUT, false),
            wingGapDp = prefs.getInt(LightspeedPreferences.KEY_HORIZON_RAIL_CUTOUT_PADDING, 4).coerceIn(0, 16).toFloat(),
            customCutoutWidth = prefs.getInt(LightspeedPreferences.KEY_NOTCH_CUTOUT_WIDTH, -1),
            customCutoutOffsetX = LightspeedPreferences.getEffectiveCutoutOffsetX(prefs)
        )
        val isMediaRoutingEnabled = renderCache.mediaRouting != "none"
        if (isMediaRoutingEnabled) {
            val pm = context.getSystemService(Context.POWER_SERVICE) as? android.os.PowerManager
            if (pm?.isInteractive == true && isAttachedToWindow) {
                uiHandler.removeCallbacks(horizonTickRunnable)
                uiHandler.postDelayed(horizonTickRunnable, 5000L)
            }
        } else {
            uiHandler.removeCallbacks(horizonTickRunnable)
        }
    }

    internal val prefListener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
        if (key != null && (key.startsWith("pref_statusbar_") || key.startsWith("pref_horizon_rail_") || key.startsWith("pref_rail_") || key.startsWith("pref_sub_") || key.startsWith("pref_section_") || key.startsWith("pref_macro_action_STATUSBAR") || key.startsWith("pref_telemetry_"))) {
            updateRenderCache()
            postInvalidate()
        }
    }

    internal val telemetryGlowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
    }

    internal val uiHandler = Handler(Looper.getMainLooper())
    internal val telemetryListener: () -> Unit = {
        postInvalidate()
    }

    private val horizonTickRunnable = object : Runnable {
        override fun run() {
            if (renderCache.mediaRouting == "none") return
            val pm = context.getSystemService(Context.POWER_SERVICE) as? android.os.PowerManager
            if (pm?.isInteractive == true && isAttachedToWindow) {
                com.sbf.lightspeed.system.LightspeedNotificationListener.instance?.refreshMediaTelemetry()
                postInvalidate()
                uiHandler.postDelayed(this, 5000L)
            }
        }
    }

    private val screenStateReceiver = object : android.content.BroadcastReceiver() {
        override fun onReceive(ctx: Context?, intent: android.content.Intent?) {
            when (intent?.action) {
                android.content.Intent.ACTION_SCREEN_ON -> {
                    uiHandler.removeCallbacks(horizonTickRunnable)
                    if (renderCache.mediaRouting != "none") {
                        uiHandler.postDelayed(horizonTickRunnable, 5000L)
                    }
                    postInvalidate()
                }
                android.content.Intent.ACTION_SCREEN_OFF -> {
                    uiHandler.removeCallbacks(horizonTickRunnable)
                }
            }
        }
    }
    private var isScreenReceiverRegistered = false

    data class TransientHudState(
        val title: String,
        val value: String,
        val stepIndex: Int,
        val totalSteps: Int,
        val style: String,
        val colorMode: String? = null
    )

    internal var currentTransientHudState: TransientHudState? = null
    internal var transientDismissRunnable: Runnable? = null
    internal var hudAlpha: Float = 0f
    private var hudFadeAnimator: android.animation.ValueAnimator? = null
    private var isFadingOutHud: Boolean = false
    internal var scrubContinuumProgress: Float = 0f
    internal var scrubContinuumTargetDeck: String? = null

    private fun animateHudAlpha(target: Float, durationMs: Long, onEnd: (() -> Unit)? = null) {
        hudFadeAnimator?.cancel()
        if (kotlin.math.abs(hudAlpha - target) < 0.01f) {
            hudAlpha = target
            postInvalidateOnAnimation()
            onEnd?.invoke()
            return
        }
        var cancelled = false
        val anim = android.animation.ValueAnimator.ofFloat(hudAlpha, target).apply {
            duration = durationMs
            interpolator = android.view.animation.DecelerateInterpolator()
            addUpdateListener { va ->
                hudAlpha = va.animatedValue as Float
                postInvalidateOnAnimation()
            }
            addListener(object : android.animation.AnimatorListenerAdapter() {
                override fun onAnimationCancel(animation: android.animation.Animator) {
                    cancelled = true
                }
                override fun onAnimationEnd(animation: android.animation.Animator) {
                    if (!cancelled) {
                        hudAlpha = target
                        onEnd?.invoke()
                    }
                }
            })
        }
        hudFadeAnimator = anim
        anim.start()
    }

    fun displayTransientHud(
        title: String,
        value: String,
        stepIndex: Int = -1,
        totalSteps: Int = 0,
        durationMs: Long = 1800L,
        style: String = "canopy_droppod",
        colorMode: String? = null
    ) {
        uiHandler.post {
            val wasHidden = visibility != View.VISIBLE
            val wasHudAlreadyVisible = currentTransientHudState != null && !isFadingOutHud
            if (wasHidden) {
                visibility = View.VISIBLE
            }
            transientDismissRunnable?.let { uiHandler.removeCallbacks(it) }
            transientDismissRunnable = null
            isFadingOutHud = false
            currentTransientHudState = TransientHudState(title, value, stepIndex, totalSteps, style, colorMode)
            expandForHud()
            val isShadeStyle = com.sbf.lightspeed.system.LightspeedHudRenderer.isFramelessShadeStyle(style)
            if (!isShadeStyle) {
                hudFadeAnimator?.cancel()
                hudAlpha = 1f
                postInvalidate()
            } else if (wasHudAlreadyVisible && hudAlpha >= 0.98f) {
                hudAlpha = 1f
                postInvalidate()
            } else {
                animateHudAlpha(1f, 90L)
            }

            if (durationMs > 0L) {
                val runnable = Runnable {
                    dismissTransientHud(0L)
                }
                transientDismissRunnable = runnable
                uiHandler.postDelayed(runnable, durationMs)
            }
        }
    }

    fun dismissTransientHud(delayMs: Long = 1200L) {
        uiHandler.post {
            val wasHidden = visibility != View.VISIBLE
            transientDismissRunnable?.let { uiHandler.removeCallbacks(it) }
            val runnable = Runnable {
                transientDismissRunnable = null
                scrubContinuumProgress = 0f
                scrubContinuumTargetDeck = null
                val activeHud = currentTransientHudState
                if (activeHud == null) {
                    if (com.sbf.lightspeed.system.LightspeedKeyEngine.currentNavState?.isActive != true) {
                        restoreWindowLayout()
                    }
                    return@Runnable
                }
                val isShadeStyle = com.sbf.lightspeed.system.LightspeedHudRenderer.isFramelessShadeStyle(activeHud.style)
                if (!isShadeStyle) {
                    isFadingOutHud = false
                    hudAlpha = 0f
                    currentTransientHudState = null
                    if (com.sbf.lightspeed.system.LightspeedKeyEngine.currentNavState?.isActive != true) {
                        restoreWindowLayout()
                    }
                    if (wasHidden) {
                        LightspeedAccessibilityService.instance?.updateOverlaysVisibility()
                    }
                    postInvalidate()
                    return@Runnable
                }
                isFadingOutHud = true
                animateHudAlpha(0f, 130L) {
                    if (!isFadingOutHud) return@animateHudAlpha
                    isFadingOutHud = false
                    currentTransientHudState = null
                    if (com.sbf.lightspeed.system.LightspeedKeyEngine.currentNavState?.isActive != true) {
                        restoreWindowLayout()
                    }
                    if (wasHidden) {
                        LightspeedAccessibilityService.instance?.updateOverlaysVisibility()
                    }
                    postInvalidate()
                }
            }
            transientDismissRunnable = runnable
            if (delayMs > 0L) {
                uiHandler.postDelayed(runnable, delayMs)
            } else {
                runnable.run()
            }
        }
    }

    init {
        activeInstance = this
        isClickable = false
        isFocusable = false
        prefs.registerOnSharedPreferenceChangeListener(prefListener)
        updateRenderCache()

        com.sbf.lightspeed.system.LightspeedNotificationListener.registerTelemetryListener(telemetryListener)
        com.sbf.lightspeed.system.LightspeedKeyEngine.onNavStateListener = { state ->
            uiHandler.post {
                if (state?.isActive == true) {
                    isFadingOutHud = false
                    expandForHud()
                    animateHudAlpha(1f, 95L)
                } else {
                    if (currentTransientHudState == null) {
                        hudFadeAnimator?.cancel()
                        hudAlpha = 0f
                        restoreWindowLayout()
                    }
                }
                postInvalidate()
            }
        }
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        updateRenderCache()
        if (!isScreenReceiverRegistered) {
            val filter = android.content.IntentFilter().apply {
                addAction(android.content.Intent.ACTION_SCREEN_ON)
                addAction(android.content.Intent.ACTION_SCREEN_OFF)
            }
            androidx.core.content.ContextCompat.registerReceiver(
                context,
                screenStateReceiver,
                filter,
                androidx.core.content.ContextCompat.RECEIVER_NOT_EXPORTED
            )
            isScreenReceiverRegistered = true
        }
        val pm = context.getSystemService(Context.POWER_SERVICE) as? android.os.PowerManager
        if (pm?.isInteractive == true && renderCache.mediaRouting != "none") {
            uiHandler.removeCallbacks(horizonTickRunnable)
            uiHandler.postDelayed(horizonTickRunnable, 5000L)
        }
    }

    override fun onConfigurationChanged(newConfig: android.content.res.Configuration?) {
        super.onConfigurationChanged(newConfig)
        updateRenderCache()
        postInvalidate()
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        updateRenderCache()
        postInvalidate()
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        if (activeInstance === this) {
            activeInstance = null
        }
        uiHandler.removeCallbacks(horizonTickRunnable)
        if (isScreenReceiverRegistered) {
            try { context.unregisterReceiver(screenStateReceiver) } catch (_: Exception) {}
            isScreenReceiverRegistered = false
        }
        transientDismissRunnable?.let { uiHandler.removeCallbacks(it) }
        hudFadeAnimator?.cancel()
        hudFadeAnimator = null
        currentTransientHudState = null
        scrubContinuumProgress = 0f
        scrubContinuumTargetDeck = null
        prefs.unregisterOnSharedPreferenceChangeListener(prefListener)
        com.sbf.lightspeed.system.LightspeedNotificationListener.unregisterTelemetryListener(telemetryListener)
        com.sbf.lightspeed.system.LightspeedKeyEngine.onNavStateListener = null
    }

    fun isHudActive(): Boolean = (currentTransientHudState != null) || (com.sbf.lightspeed.system.LightspeedKeyEngine.currentNavState?.isActive == true)

    internal fun expandForHud() {
        val wm = context.getSystemService(Context.WINDOW_SERVICE) as? WindowManager ?: return
        val lp = layoutParams as? WindowManager.LayoutParams ?: return
        val blurMode = com.sbf.lightspeed.settings.BackdropBlurModeManager.getMode(context)
        val isShadeStyle = com.sbf.lightspeed.system.LightspeedHudRenderer.isFramelessShadeStyle(currentTransientHudState?.style)
        if (!isShadeStyle &&
            com.sbf.lightspeed.settings.BackdropBlurModeManager.shouldUseScreenshotBlur(wm, blurMode) &&
            !com.sbf.lightspeed.settings.CentralCommandBackdropBlur.hasHudBackdrop() &&
            !com.sbf.lightspeed.settings.CentralCommandBackdropBlur.isHudCapturing
        ) {
            com.sbf.lightspeed.settings.CentralCommandBackdropBlur.prepareForHudOnly(wm) {
                postInvalidateOnAnimation()
            }
        }
        val d = resources.displayMetrics.density
        val screenW = resources.displayMetrics.widthPixels
        val targetH = (260 * d).toInt()
        var layoutDirty = lp.height != targetH || lp.width != screenW
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val useNative = !isShadeStyle && com.sbf.lightspeed.settings.BackdropBlurModeManager.shouldUseNativeBlur(wm, blurMode)
            val hasBlurFlag = (lp.flags and WindowManager.LayoutParams.FLAG_BLUR_BEHIND) != 0
            if (useNative && !hasBlurFlag) {
                lp.flags = lp.flags or WindowManager.LayoutParams.FLAG_BLUR_BEHIND
                lp.blurBehindRadius = com.sbf.lightspeed.settings.DeckThemeManager.snapshotFlow.value.windowBlurRadiusPx.coerceIn(0, 150)
                layoutDirty = true
            } else if (!useNative && hasBlurFlag) {
                lp.flags = lp.flags and WindowManager.LayoutParams.FLAG_BLUR_BEHIND.inv()
                lp.blurBehindRadius = 0
                layoutDirty = true
            }
        }
        if (layoutDirty) {
            lp.x = 0
            lp.y = 0
            lp.width = screenW
            lp.height = targetH
            @Suppress("RtlHardcoded")
            lp.gravity = Gravity.TOP or Gravity.LEFT
            try { wm.updateViewLayout(this, lp) } catch (e: Exception) { logSwallowed("LightspeedStatusBarOverlay", "expandForHud:325", e) }
        }
    }

    internal fun restoreWindowLayout() {
        if (isHudActive()) return
        val wm = context.getSystemService(Context.WINDOW_SERVICE) as? WindowManager ?: return
        val lp = layoutParams as? WindowManager.LayoutParams ?: return
        val d = resources.displayMetrics.density
        val screenWidthPx = resources.displayMetrics.widthPixels
        val sensorThicknessDp = renderCache.thicknessDp
        val railThicknessDp = renderCache.railThicknessDp
        val maxRails = renderCache.maxRails
        val isRailText = renderCache.isTextEnabled
        val railOffsetY = renderCache.railOffsetY
        val textOffsetY = renderCache.textOffsetY
        val textPos = renderCache.textPos

        val baseRailHeightDp = railOffsetY + (railThicknessDp * maxRails) + 8
        val textHeightDp = if (isRailText) {
            if (textPos == "below_statusbar") {
                sensorThicknessDp + textOffsetY + 24
            } else {
                baseRailHeightDp + textOffsetY + 24
            }
        } else baseRailHeightDp

        val isSensorEnabled = renderCache.isSensorEnabled
        val effectiveHeightDp = if (isSensorEnabled) maxOf(sensorThicknessDp, textHeightDp) else textHeightDp

        lp.width = screenWidthPx
        lp.height = (effectiveHeightDp * d).toInt()
        lp.x = 0
        lp.y = 0
        @Suppress("RtlHardcoded")
        lp.gravity = Gravity.TOP or Gravity.LEFT
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
            (lp.flags and WindowManager.LayoutParams.FLAG_BLUR_BEHIND) != 0
        ) {
            lp.flags = lp.flags and WindowManager.LayoutParams.FLAG_BLUR_BEHIND.inv()
            lp.blurBehindRadius = 0
        }
        try { wm.updateViewLayout(this, lp) } catch (e: Exception) { logSwallowed("LightspeedStatusBarOverlay", "restoreWindowLayout:360", e) }
        com.sbf.lightspeed.settings.CentralCommandBackdropBlur.onHudHidden()
    }

    override fun onDraw(canvas: Canvas) {
        handleDraw(canvas) { super.onDraw(canvas) }
    }

    fun performActionByName(actionKey: String) {
        ActionDispatcher.execute(service, actionKey)
    }

    fun scrollToTop() {
        com.sbf.lightspeed.system.LightspeedScrollHelper.scroll(service, toBottom = false)
    }

    internal fun collectScrollableNodes(node: AccessibilityNodeInfo, list: MutableList<AccessibilityNodeInfo>) {
        com.sbf.lightspeed.system.LightspeedScrollHelper.collectScrollableNodes(node, list, toBottom = false)
    }

    companion object {
        @Volatile
        var activeInstance: LightspeedStatusBarOverlay? = null

        fun showActionHud(
            title: String,
            value: String,
            stepIndex: Int = -1,
            totalSteps: Int = 0,
            durationMs: Long = 1800L,
            style: String = "canopy_droppod",
            colorMode: String? = null
        ): Boolean {
            LightspeedAccessibilityService.instance?.hideMediaScrubber()
            val inst = activeInstance ?: return false
            inst.displayTransientHud(title, value, stepIndex, totalSteps, durationMs, style, colorMode)
            return true
        }

        fun dismissActionHud(delayMs: Long = 1200L): Boolean {
            val inst = activeInstance ?: return false
            inst.dismissTransientHud(delayMs)
            return true
        }

        fun updateScrubContinuum(progress: Float, targetDeck: String?) {
            val inst = activeInstance ?: return
            val clamped = progress.coerceIn(0f, 1f)
            if (kotlin.math.abs(inst.scrubContinuumProgress - clamped) < 0.01f &&
                inst.scrubContinuumTargetDeck == targetDeck
            ) {
                return
            }
            inst.uiHandler.post {
                inst.scrubContinuumProgress = clamped
                inst.scrubContinuumTargetDeck = targetDeck
                inst.postInvalidateOnAnimation()
            }
        }

        fun hideActionHudNow() {
            val inst = activeInstance ?: return
            inst.uiHandler.post {
                inst.transientDismissRunnable?.let { inst.uiHandler.removeCallbacks(it) }
                inst.hudFadeAnimator?.cancel()
                inst.isFadingOutHud = false
                inst.hudAlpha = 0f
                inst.scrubContinuumProgress = 0f
                inst.scrubContinuumTargetDeck = null
                inst.currentTransientHudState = null
                inst.restoreWindowLayout()
                inst.invalidate()
            }
        }
    }
}
