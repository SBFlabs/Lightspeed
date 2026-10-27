package com.sbf.lightspeed.system

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.os.Build
import android.os.SystemClock
import android.view.InputDevice
import android.view.MotionEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.view.accessibility.AccessibilityWindowInfo
import com.sbf.lightspeed.LightspeedAccessibilityService

/**
 * Real-time Scroll Scrubbing Engine.
 *
 * Smoothly scrolls foreground applications using Shizuku IInputManager
 * MotionEvent.ACTION_SCROLL with AXIS_VSCROLL, falling back to real-time
 * AccessibilityNodeInfo step scrolling.
 */
object LightspeedScrollScrubEngine {
    private const val TAG = "LightspeedScrollScrub"

    fun executeScrollScrub(context: Context, steps: Int): Boolean {
        if (steps == 0) return false
        val scrollDelta = steps.toFloat() * -1.0f // Upward scrub scrolls down (negative delta in Android scroll axis)

        if (ElevatedTaskCloser.isShizukuActive) {
            val injected = injectScrollAxis(scrollDelta)
            if (injected) return true
        }

        return executeAccessibilityStepScroll(context, steps > 0)
    }

    private fun injectScrollAxis(axisVScrollDelta: Float): Boolean {
        return try {
            val stubClass = Class.forName("android.hardware.input.IInputManager\$Stub")
            val rawBinder = rikka.shizuku.SystemServiceHelper.getSystemService("input")
                ?: rikka.shizuku.SystemServiceHelper.getSystemService(Context.INPUT_SERVICE)
                ?: return false
            val wrapped = rikka.shizuku.ShizukuBinderWrapper(rawBinder)
            val asInterface = stubClass.getMethod("asInterface", android.os.IBinder::class.java)
            val inputManager = asInterface.invoke(null, wrapped) ?: return false
            val injectMethod = inputManager.javaClass.methods.firstOrNull { it.name == "injectInputEvent" } ?: return false

            val now = SystemClock.uptimeMillis()
            val service = LightspeedAccessibilityService.instance
            val dm = service?.resources?.displayMetrics
            val cx = (dm?.widthPixels?.toFloat() ?: 1080f) * 0.5f
            val cy = (dm?.heightPixels?.toFloat() ?: 2400f) * 0.5f

            val properties = MotionEvent.PointerProperties().apply {
                id = 0
                toolType = MotionEvent.TOOL_TYPE_MOUSE
            }
            val coords = MotionEvent.PointerCoords().apply {
                x = cx
                y = cy
                setAxisValue(MotionEvent.AXIS_VSCROLL, axisVScrollDelta)
            }

            val event = MotionEvent.obtain(
                now, now, MotionEvent.ACTION_SCROLL, 1,
                arrayOf(properties), arrayOf(coords),
                0, 0, 1.0f, 1.0f, 0, 0,
                InputDevice.SOURCE_MOUSE or InputDevice.SOURCE_ROTARY_ENCODER, 0
            )

            val success = injectMethod.invoke(inputManager, event, 0) as? Boolean ?: true
            event.recycle()
            success
        } catch (e: Exception) {
            logSwallowed(TAG, "injectScrollAxis", e)
            false
        }
    }

    private fun executeAccessibilityStepScroll(context: Context, scrollUp: Boolean): Boolean {
        val service = (context as? AccessibilityService) ?: LightspeedAccessibilityService.instance ?: return false
        val windows = service.windows
        val scrollableNodes = mutableListOf<AccessibilityNodeInfo>()

        if (!windows.isNullOrEmpty()) {
            for (window in windows) {
                if (window.type == AccessibilityWindowInfo.TYPE_APPLICATION || window.isActive) {
                    val root = window.root ?: continue
                    LightspeedScrollHelper.collectScrollableNodes(root, scrollableNodes, toBottom = !scrollUp)
                }
            }
        }
        if (scrollableNodes.isEmpty()) {
            service.rootInActiveWindow?.let { root ->
                LightspeedScrollHelper.collectScrollableNodes(root, scrollableNodes, toBottom = !scrollUp)
            }
        }

        for (node in scrollableNodes) {
            try {
                val actionId = if (!scrollUp) {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) AccessibilityNodeInfo.AccessibilityAction.ACTION_SCROLL_DOWN.id
                    else AccessibilityNodeInfo.ACTION_SCROLL_FORWARD
                } else {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) AccessibilityNodeInfo.AccessibilityAction.ACTION_SCROLL_UP.id
                    else AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD
                }
                val moved = node.performAction(actionId)
                if (moved) return true
            } catch (e: Exception) {
                logSwallowed(TAG, "executeAccessibilityStepScroll", e)
            }
        }
        return false
    }
}
