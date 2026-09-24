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
        var dlRouting: String = "notch_pill",
        var mediaRouting: String = "none",
        var railSpanPref: Int = 1080,
        var railAlign: String = "center",
        var railOffsetX: Int = 0,
        var railOffsetY: Int = 0,
        var isStatusBarGuard: Boolean = true,
        var safePaddingLeftDp: Float = 0f,
        var safePaddingRightDp: Float = 0f,
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
            railAlign = prefs.getString("pref_horizon_rail_align", "center") ?: "center",
            railOffsetX = prefs.getInt(LightspeedPreferences.KEY_HORIZON_RAIL_OFFSET_X, 0),
            railOffsetY = prefs.getInt(LightspeedPreferences.KEY_HORIZON_RAIL_OFFSET_Y, 0),
            isStatusBarGuard = prefs.getBoolean(LightspeedPreferences.KEY_HORIZON_RAIL_STATUS_BAR_GUARD, true),
            safePaddingLeftDp = prefs.getInt(LightspeedPreferences.KEY_HORIZON_RAIL_SAFE_PADDING_LEFT, 0).toFloat(),
            safePaddingRightDp = prefs.getInt(LightspeedPreferences.KEY_HORIZON_RAIL_SAFE_PADDING_RIGHT, 0).toFloat(),
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

    data class TransientHudState(
        val title: String,
        val value: String,
        val stepIndex: Int,
        val totalSteps: Int,
        val style: String
    )

    internal var currentTransientHudState: TransientHudState? = null
    internal var transientDismissRunnable: Runnable? = null

    fun displayTransientHud(
        title: String,
        value: String,
        stepIndex: Int = -1,
        totalSteps: Int = 0,
        durationMs: Long = 1800L,
        style: String = "canopy_droppod"
    ) {
        uiHandler.post {
            val wasHidden = visibility != View.VISIBLE
            if (wasHidden) {
                visibility = View.VISIBLE
            }
            transientDismissRunnable?.let { uiHandler.removeCallbacks(it) }
            transientDismissRunnable = null
            currentTransientHudState = TransientHudState(title, value, stepIndex, totalSteps, style)
            expandForHud()
            postInvalidate()

            if (durationMs > 0L) {
                val runnable = Runnable {
                    currentTransientHudState = null
                    if (com.sbf.lightspeed.system.LightspeedKeyEngine.currentNavState?.isActive != true) {
                        restoreWindowLayout()
                    }
                    if (wasHidden) {
                        LightspeedAccessibilityService.instance?.updateOverlaysVisibility()
                    }
                    postInvalidate()
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
                currentTransientHudState = null
                if (com.sbf.lightspeed.system.LightspeedKeyEngine.currentNavState?.isActive != true) {
                    restoreWindowLayout()
                }
                if (wasHidden) {
                    LightspeedAccessibilityService.instance?.updateOverlaysVisibility()
                }
                postInvalidate()
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
                    expandForHud()
                } else {
                    if (currentTransientHudState == null) {
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
        transientDismissRunnable?.let { uiHandler.removeCallbacks(it) }
        currentTransientHudState = null
        prefs.unregisterOnSharedPreferenceChangeListener(prefListener)
        com.sbf.lightspeed.system.LightspeedNotificationListener.unregisterTelemetryListener(telemetryListener)
        com.sbf.lightspeed.system.LightspeedKeyEngine.onNavStateListener = null
    }

    fun isHudActive(): Boolean = (currentTransientHudState != null) || (com.sbf.lightspeed.system.LightspeedKeyEngine.currentNavState?.isActive == true)

    internal fun expandForHud() {
        val wm = context.getSystemService(Context.WINDOW_SERVICE) as? WindowManager ?: return
        val lp = layoutParams as? WindowManager.LayoutParams ?: return
        val d = resources.displayMetrics.density
        val screenW = resources.displayMetrics.widthPixels
        val targetH = (260 * d).toInt()
        if (lp.height != targetH || lp.width != screenW) {
            lp.x = 0
            lp.y = 0
            lp.width = screenW
            lp.height = targetH
            lp.gravity = Gravity.TOP or Gravity.START
            try { wm.updateViewLayout(this, lp) } catch (_: Exception) {}
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
        lp.gravity = Gravity.TOP or Gravity.START
        try { wm.updateViewLayout(this, lp) } catch (_: Exception) {}
    }

    override fun onDraw(canvas: Canvas) {
        handleDraw(canvas) { super.onDraw(canvas) }
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

    internal fun collectScrollableNodes(node: AccessibilityNodeInfo, list: MutableList<AccessibilityNodeInfo>) {
        val hasScrollAction = node.actionList.any { action ->
            action.id == AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD ||
            (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && action.id == AccessibilityNodeInfo.AccessibilityAction.ACTION_SCROLL_UP.id) ||
            (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N && action.id == AccessibilityNodeInfo.AccessibilityAction.ACTION_SCROLL_TO_POSITION.id)
        }

        if (node.isScrollable || hasScrollAction) {
            val copy = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                AccessibilityNodeInfo(node)
            } else {
                @Suppress("DEPRECATION")
                AccessibilityNodeInfo.obtain(node)
            }
            list.add(copy)
        }

        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            collectScrollableNodes(child, list)
        }
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
            style: String = "canopy_droppod"
        ): Boolean {
            val inst = activeInstance ?: return false
            inst.displayTransientHud(title, value, stepIndex, totalSteps, durationMs, style)
            return true
        }

        fun dismissActionHud(delayMs: Long = 1200L): Boolean {
            val inst = activeInstance ?: return false
            inst.dismissTransientHud(delayMs)
            return true
        }
    }
}
