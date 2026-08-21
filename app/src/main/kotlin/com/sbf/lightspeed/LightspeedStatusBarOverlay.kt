package com.sbf.lightspeed

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.content.SharedPreferences
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PixelFormat
import android.os.Build
import android.os.Bundle
import android.view.GestureDetector
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.view.accessibility.AccessibilityNodeInfo
import com.sbf.lightspeed.system.ActionDispatcher
import com.sbf.lightspeed.system.ElevatedTaskCloser
import kotlin.math.abs

class LightspeedStatusBarOverlay(
    context: Context,
    private val service: AccessibilityService
) : View(context) {

    private val prefs = service.getSharedPreferences("default", Context.MODE_PRIVATE)

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#4DB6AC")
        style = Paint.Style.FILL
    }

    private val prefListener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
        if (key != null && key.startsWith("pref_statusbar_")) {
            postInvalidate()
        }
    }

    init {
        prefs.registerOnSharedPreferenceChangeListener(prefListener)
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        prefs.unregisterOnSharedPreferenceChangeListener(prefListener)
    }

    private var startX = 0f
    private var startY = 0f
    private var isScrubbing = false

    private val gestureDetector = GestureDetector(context, object : GestureDetector.SimpleOnGestureListener() {
        override fun onSingleTapConfirmed(e: MotionEvent): Boolean {
            val action = prefs.getString("pref_macro_action_STATUSBAR_TAP", "system:scroll_to_top") ?: "system:scroll_to_top"
            performActionByName(action)
            return true
        }

        override fun onDoubleTap(e: MotionEvent): Boolean {
            val action = prefs.getString("pref_macro_action_STATUSBAR_DOUBLE_TAP", "system:scroll_to_top") ?: "system:scroll_to_top"
            performActionByName(action)
            return true
        }
    })

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (gestureDetector.onTouchEvent(event)) return true

        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                startX = event.x
                startY = event.y
                isScrubbing = false
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                val dx = event.x - startX
                val dy = event.y - startY

                if (abs(dy) > 25f && abs(dy) > abs(dx)) {
                    isScrubbing = true
                    val scrubAction = prefs.getString("pref_macro_action_STATUSBAR_SCRUBBING", "none") ?: "none"
                    performActionByName(scrubAction)
                }
                return true
            }
            MotionEvent.ACTION_UP -> {
                val dx = event.x - startX
                val dy = event.y - startY

                if (!isScrubbing) {
                    if (abs(dx) > abs(dy) && dx > 15f) {
                        val action = prefs.getString("pref_macro_action_STATUSBAR_SWIPE_RIGHT", "none") ?: "none"
                        performActionByName(action)
                        return true
                    } else if (abs(dx) > abs(dy) && dx < -15f) {
                        val action = prefs.getString("pref_macro_action_STATUSBAR_SWIPE_LEFT", "none") ?: "none"
                        performActionByName(action)
                        return true
                    } else if (dy > 15f && abs(dy) > abs(dx)) {
                        val action = prefs.getString("pref_macro_action_STATUSBAR_SWIPE_DOWN", "system:notifications") ?: "system:notifications"
                        performActionByName(action)
                        return true
                    }
                }
            }
        }
        return super.onTouchEvent(event)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val previewEnabled = prefs.getBoolean("pref_statusbar_preview", false)
        val transparencyPct = prefs.getInt("pref_statusbar_transparency", 0)

        val alpha = if (previewEnabled) 160 else (transparencyPct * 2.55f).toInt().coerceIn(0, 255)

        if (alpha > 0) {
            paint.alpha = alpha
            canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), paint)
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
                // Prioritize the active application window
                if (window.type == android.view.accessibility.AccessibilityWindowInfo.TYPE_APPLICATION || window.isActive) {
                    val root = window.root ?: continue
                    collectScrollableNodes(root, scrollableNodes)
                    root.recycle()
                }
            }
        }

        if (scrollableNodes.isEmpty()) {
            service.rootInActiveWindow?.let { root ->
                collectScrollableNodes(root, scrollableNodes)
                root.recycle()
            }
        }

        if (scrollableNodes.isEmpty()) return

        // 1. Process innermost child scroll containers first
        val targetNodes = scrollableNodes.reversed()

        for (node in targetNodes) {
            try {
                // Phase 1: Direct coordinate snap (RecyclerView / Compose LazyColumn)
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

                // Phase 2: Rapid page flings for WebViews & custom layout managers
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
            } catch (_: Exception) {
            } finally {
                try { node.recycle() } catch (_: Exception) {}
            }
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
            child.recycle()
        }
    }
}
