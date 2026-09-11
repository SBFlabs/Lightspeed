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

    internal val prefListener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
        if (key != null && (key.startsWith("pref_statusbar_") || key.startsWith("pref_horizon_rail_") || key.startsWith("pref_sub_") || key.startsWith("pref_section_") || key.startsWith("pref_macro_action_STATUSBAR") || key.startsWith("pref_telemetry_"))) {
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
        val sensorThicknessDp = prefs.getInt("pref_statusbar_thickness", 48).coerceIn(20, 52)
        val railThicknessDp = prefs.getInt(com.sbf.lightspeed.system.LightspeedPreferences.KEY_HORIZON_RAIL_THICKNESS, 2).coerceIn(1, 6)
        val maxRails = prefs.getInt(com.sbf.lightspeed.system.LightspeedPreferences.KEY_HORIZON_RAIL_MAX_COUNT, 2).coerceIn(1, 3)
        val isRailText = prefs.getBoolean(com.sbf.lightspeed.system.LightspeedPreferences.KEY_HORIZON_RAIL_TEXT_ENABLED, true)
        val railOffsetY = prefs.getInt(com.sbf.lightspeed.system.LightspeedPreferences.KEY_HORIZON_RAIL_OFFSET_Y, 0)
        val textOffsetY = prefs.getInt(com.sbf.lightspeed.system.LightspeedPreferences.KEY_HORIZON_RAIL_TEXT_OFFSET_Y, 0)
        val textPos = prefs.getString(com.sbf.lightspeed.system.LightspeedPreferences.KEY_HORIZON_RAIL_TEXT_POSITION, "below") ?: "below"

        val baseRailHeightDp = railOffsetY + (railThicknessDp * maxRails) + 8
        val textHeightDp = if (isRailText) {
            if (textPos == "below_statusbar") {
                sensorThicknessDp + textOffsetY + 24
            } else {
                baseRailHeightDp + textOffsetY + 24
            }
        } else baseRailHeightDp

        val isSensorEnabled = prefs.getBoolean("pref_statusbar_enabled", true)
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
            list.add(AccessibilityNodeInfo.obtain(node))
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
