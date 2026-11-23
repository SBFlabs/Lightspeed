package com.sbf.lightspeed.system.bot

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.content.Context
import android.graphics.Path
import android.graphics.Point
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.DisplayMetrics
import android.util.Log
import android.view.WindowManager
import com.sbf.lightspeed.system.logSwallowed
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.coroutines.resume
import kotlin.math.hypot

/**
 * Validated, non-overlapping Accessibility gesture dispatcher and coordinate bounds helper
 * for Cybernetic Trackpad freeform taps, swipes, and sequential multi-step macro playback.
 */
object LightspeedBotGestureDispatcher {

    private const val TAG = "LightspeedBotGesture"
    private const val MIN_TAP_DURATION_MS = 35L
    private const val MAX_TAP_DURATION_MS = 2500L
    private const val MIN_SWIPE_DURATION_MS = 60L
    private const val MAX_SWIPE_DURATION_MS = 5000L
    private const val GESTURE_TIMEOUT_BUFFER_MS = 2000L

    private val mainHandler = Handler(Looper.getMainLooper())

    /**
     * Resolves full physical display pixel bounds (matching raw screen coordinates across
     * status bar, cutouts, and navigation bar) for both recording normalization and playback.
     */
    @Suppress("DEPRECATION")
    fun resolveDisplayBounds(context: Context): Pair<Int, Int> {
        val wm = context.getSystemService(Context.WINDOW_SERVICE) as? WindowManager
        if (wm != null) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                try {
                    val b = wm.maximumWindowMetrics.bounds
                    if (b.width() > 0 && b.height() > 0) {
                        return b.width() to b.height()
                    }
                } catch (_: Throwable) {}
            }
            try {
                val dm = DisplayMetrics()
                wm.defaultDisplay?.getRealMetrics(dm)
                if (dm.widthPixels > 0 && dm.heightPixels > 0) {
                    return dm.widthPixels to dm.heightPixels
                }
            } catch (_: Throwable) {}
            try {
                val p = Point()
                wm.defaultDisplay?.getRealSize(p)
                if (p.x > 0 && p.y > 0) {
                    return p.x to p.y
                }
            } catch (_: Throwable) {}
        }
        val fallback = context.resources.displayMetrics
        return fallback.widthPixels.coerceAtLeast(1) to fallback.heightPixels.coerceAtLeast(1)
    }

    private fun clampCoord(value: Float, maxBound: Int): Float {
        if (!value.isFinite()) return (maxBound / 2f).coerceAtLeast(1f)
        return value.coerceIn(1f, (maxBound - 1f).coerceAtLeast(1f))
    }

    fun clampTapDuration(durationMs: Long): Long {
        val maxAllowed = GestureDescription.getMaxGestureDuration().coerceAtLeast(MIN_TAP_DURATION_MS)
        return durationMs.coerceIn(MIN_TAP_DURATION_MS, minOf(MAX_TAP_DURATION_MS, maxAllowed))
    }

    fun clampSwipeDuration(durationMs: Long): Long {
        val maxAllowed = GestureDescription.getMaxGestureDuration().coerceAtLeast(MIN_SWIPE_DURATION_MS)
        return durationMs.coerceIn(MIN_SWIPE_DURATION_MS, minOf(MAX_SWIPE_DURATION_MS, maxAllowed))
    }

    fun buildTapGesture(x: Float, y: Float, durationMs: Long, screenW: Int, screenH: Int): GestureDescription {
        val cx = clampCoord(x, screenW)
        val cy = clampCoord(y, screenH)
        val dur = clampTapDuration(durationMs)
        val path = Path().apply { moveTo(cx, cy) }
        val stroke = GestureDescription.StrokeDescription(path, 0L, dur)
        return GestureDescription.Builder().addStroke(stroke).build()
    }

    fun buildSwipeGesture(
        startX: Float,
        startY: Float,
        endX: Float,
        endY: Float,
        durationMs: Long,
        screenW: Int,
        screenH: Int
    ): GestureDescription {
        val sx = clampCoord(startX, screenW)
        val sy = clampCoord(startY, screenH)
        var ex = clampCoord(endX, screenW)
        val ey = clampCoord(endY, screenH)
        if (hypot((ex - sx).toDouble(), (ey - sy).toDouble()) < 1.0) {
            ex = if (sx + 2f < screenW) sx + 2f else (sx - 2f).coerceAtLeast(1f)
        }
        val dur = clampSwipeDuration(durationMs)
        val path = Path().apply {
            moveTo(sx, sy)
            lineTo(ex, ey)
        }
        val stroke = GestureDescription.StrokeDescription(path, 0L, dur)
        return GestureDescription.Builder().addStroke(stroke).build()
    }

    /**
     * Dispatches [gesture] via AccessibilityService and suspends until [GestureResultCallback]
     * reports onCompleted or onCancelled, guaranteeing sequential macro steps never overlap.
     */
    suspend fun dispatchGestureAwait(
        service: AccessibilityService,
        gesture: GestureDescription,
        label: String,
        expectedDurationMs: Long
    ): Boolean {
        val timeoutMs = expectedDurationMs.coerceAtLeast(100L) + GESTURE_TIMEOUT_BUFFER_MS
        val result = withTimeoutOrNull(timeoutMs) {
            suspendCancellableCoroutine<Boolean> { cont ->
                val settled = AtomicBoolean(false)
                val callback = object : AccessibilityService.GestureResultCallback() {
                    override fun onCompleted(gestureDescription: GestureDescription?) {
                        Log.i(TAG, "dispatchGesture onCompleted: $label")
                        if (settled.compareAndSet(false, true) && cont.isActive) {
                            cont.resume(true)
                        }
                    }

                    override fun onCancelled(gestureDescription: GestureDescription?) {
                        Log.w(TAG, "dispatchGesture onCancelled: $label")
                        if (settled.compareAndSet(false, true) && cont.isActive) {
                            cont.resume(false)
                        }
                    }
                }
                val launchDispatch = {
                    try {
                        val accepted = service.dispatchGesture(gesture, callback, mainHandler)
                        if (!accepted) {
                            Log.w(TAG, "dispatchGesture rejected immediately: $label")
                            if (settled.compareAndSet(false, true) && cont.isActive) {
                                cont.resume(false)
                            }
                        }
                    } catch (t: Throwable) {
                        Log.e(TAG, "dispatchGesture failed: $label", t)
                        if (settled.compareAndSet(false, true) && cont.isActive) {
                            cont.resume(false)
                        }
                    }
                }
                if (Looper.myLooper() == Looper.getMainLooper()) {
                    launchDispatch()
                } else {
                    mainHandler.post { launchDispatch() }
                }
            }
        }
        if (result == null) {
            Log.w(TAG, "dispatchGesture timed out after ${timeoutMs}ms: $label")
            return false
        }
        return result
    }

    fun dispatchGestureAsync(
        service: AccessibilityService,
        gesture: GestureDescription,
        label: String,
        expectedDurationMs: Long,
        onFinished: ((Boolean) -> Unit)? = null
    ) {
        val settled = AtomicBoolean(false)
        val timeoutMs = expectedDurationMs.coerceAtLeast(100L) + GESTURE_TIMEOUT_BUFFER_MS
        val timeoutRunnable = Runnable {
            if (settled.compareAndSet(false, true)) {
                Log.w(TAG, "dispatchGestureAsync timed out after ${timeoutMs}ms: $label")
                onFinished?.invoke(false)
            }
        }
        val callback = object : AccessibilityService.GestureResultCallback() {
            override fun onCompleted(gestureDescription: GestureDescription?) {
                mainHandler.removeCallbacks(timeoutRunnable)
                Log.i(TAG, "dispatchGesture onCompleted: $label")
                if (settled.compareAndSet(false, true)) {
                    onFinished?.invoke(true)
                }
            }

            override fun onCancelled(gestureDescription: GestureDescription?) {
                mainHandler.removeCallbacks(timeoutRunnable)
                Log.w(TAG, "dispatchGesture onCancelled: $label")
                if (settled.compareAndSet(false, true)) {
                    onFinished?.invoke(false)
                }
            }
        }
        val launchDispatch = {
            mainHandler.postDelayed(timeoutRunnable, timeoutMs)
            try {
                val accepted = service.dispatchGesture(gesture, callback, mainHandler)
                if (!accepted) {
                    mainHandler.removeCallbacks(timeoutRunnable)
                    Log.w(TAG, "dispatchGesture rejected immediately: $label")
                    if (settled.compareAndSet(false, true)) {
                        onFinished?.invoke(false)
                    }
                }
            } catch (t: Throwable) {
                mainHandler.removeCallbacks(timeoutRunnable)
                Log.e(TAG, "dispatchGesture failed: $label", t)
                if (settled.compareAndSet(false, true)) {
                    onFinished?.invoke(false)
                }
            }
        }
        if (Looper.myLooper() == Looper.getMainLooper()) {
            launchDispatch()
        } else {
            mainHandler.post { launchDispatch() }
        }
    }

    suspend fun dispatchTapAwait(
        service: AccessibilityService,
        x: Float,
        y: Float,
        durationMs: Long,
        screenW: Int,
        screenH: Int,
        label: String
    ): Boolean {
        val dur = clampTapDuration(durationMs)
        val gesture = buildTapGesture(x, y, dur, screenW, screenH)
        return dispatchGestureAwait(service, gesture, label, dur)
    }

    suspend fun dispatchSwipeAwait(
        service: AccessibilityService,
        startX: Float,
        startY: Float,
        endX: Float,
        endY: Float,
        durationMs: Long,
        screenW: Int,
        screenH: Int,
        label: String
    ): Boolean {
        val dur = clampSwipeDuration(durationMs)
        val gesture = buildSwipeGesture(startX, startY, endX, endY, dur, screenW, screenH)
        return dispatchGestureAwait(service, gesture, label, dur)
    }

    fun dispatchTapAsync(
        service: AccessibilityService,
        x: Float,
        y: Float,
        durationMs: Long,
        screenW: Int,
        screenH: Int,
        label: String,
        onFinished: ((Boolean) -> Unit)? = null
    ) {
        val dur = clampTapDuration(durationMs)
        val gesture = buildTapGesture(x, y, dur, screenW, screenH)
        dispatchGestureAsync(service, gesture, label, dur, onFinished)
    }

    fun dispatchSwipeAsync(
        service: AccessibilityService,
        startX: Float,
        startY: Float,
        endX: Float,
        endY: Float,
        durationMs: Long,
        screenW: Int,
        screenH: Int,
        label: String,
        onFinished: ((Boolean) -> Unit)? = null
    ) {
        val dur = clampSwipeDuration(durationMs)
        val gesture = buildSwipeGesture(startX, startY, endX, endY, dur, screenW, screenH)
        dispatchGestureAsync(service, gesture, label, dur, onFinished)
    }

    fun dispatchHoldAndDragAsync(
        service: AccessibilityService,
        startX: Float,
        startY: Float,
        endX: Float,
        endY: Float,
        dragDuration: Long,
        holdBeforeDragMs: Long,
        screenW: Int,
        screenH: Int
    ) {
        val sx = clampCoord(startX, screenW)
        val sy = clampCoord(startY, screenH)
        val ex = clampCoord(endX, screenW)
        val ey = clampCoord(endY, screenH)
        val pathHold = Path().apply { moveTo(sx, sy) }
        val pathDrag = Path().apply {
            moveTo(sx, sy)
            lineTo(ex, ey)
        }
        val clampedDragDuration = dragDuration.coerceIn(150L, 1000L)
        val clampedHoldDuration = holdBeforeDragMs.coerceIn(300L, 800L)
        val holdStroke = GestureDescription.StrokeDescription(pathHold, 0L, clampedHoldDuration, true)
        val gestureHold = GestureDescription.Builder().addStroke(holdStroke).build()

        service.dispatchGesture(gestureHold, object : AccessibilityService.GestureResultCallback() {
            override fun onCompleted(gestureDescription: GestureDescription?) {
                try {
                    val dragStroke = holdStroke.continueStroke(pathDrag, 0L, clampedDragDuration, false)
                    val gestureDrag = GestureDescription.Builder().addStroke(dragStroke).build()
                    dispatchGestureAsync(service, gestureDrag, "HoldAndDragStroke", clampedDragDuration)
                } catch (e: Exception) {
                    logSwallowed(TAG, "continueStroke holdAndDrag failed", e)
                    dispatchSwipeAsync(service, sx, sy, ex, ey, clampedDragDuration, screenW, screenH, "HoldAndDragFallback")
                }
            }

            override fun onCancelled(gestureDescription: GestureDescription?) {
                Log.w(TAG, "HoldAndDrag hold stroke cancelled")
            }
        }, mainHandler)
    }
}
