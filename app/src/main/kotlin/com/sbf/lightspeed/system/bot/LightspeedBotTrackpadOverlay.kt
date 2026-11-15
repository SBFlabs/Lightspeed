package com.sbf.lightspeed.system.bot

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.graphics.Canvas
import android.graphics.PixelFormat
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.*
import android.widget.Toast
import com.sbf.lightspeed.LightspeedAccessibilityService
import com.sbf.lightspeed.settings.DeckThemeManager
import com.sbf.lightspeed.settings.resolveCanvasPalette
import com.sbf.lightspeed.system.*
import com.sbf.lightspeed.system.display.createFullDisplayOverlayParams
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlin.math.*

/**
 * Cybernetic Half-Circle Trackpad & 3-Segment Macro Clicker Overlay (< 380 lines).
 *
 * Trackpad Surface:
 * - Move finger -> 120Hz smooth cursor movement
 * - Short Tap (< 250ms) -> Inject tap at cursor (cursorX, cursorY)
 *
 * 3 Macro Segments (B1, B2, B3):
 * - Short Tap (< 400ms) -> Play recorded macro for that button
 * - Long Press (>= 450ms) -> Record macro for that button
 * - Tap + Long Press (tap then hold within 380ms) -> Open Macro Editor for that button
 */
object LightspeedBotTrackpadOverlay {

    var cursorX = 540f
    var cursorY = 1100f
    var isCursorActive = false
    var activeFlankIsLeft = false
    var isDomeHoldActive = false
    @Volatile
    var isInjectingPassThrough = false
        private set

    private val restoreTouchabilityRunnable = Runnable {
        setFlankPassThroughTouchable(false)
    }

    private fun isPointInViewScreenBounds(view: View?, rawX: Float, rawY: Float): Boolean {
        val v = view ?: return false
        if (!v.isAttachedToWindow || v.visibility != View.VISIBLE || v.width <= 0 || v.height <= 0) return false
        val loc = IntArray(2)
        v.getLocationOnScreen(loc)
        val left = loc[0].toFloat() - 8f
        val top = loc[1].toFloat() - 8f
        val right = left + v.width + 16f
        val bottom = top + v.height + 16f
        return rawX in left..right && rawY in top..bottom
    }

    fun isPointOverTrackpadWindow(rawX: Float, rawY: Float): Boolean {
        return isPointInViewScreenBounds(leftFlankView, rawX, rawY) ||
            isPointInViewScreenBounds(rightFlankView, rawX, rawY)
    }

    private fun setFlankPassThroughTouchable(passThrough: Boolean) {
        isInjectingPassThrough = passThrough
        val wm = windowManager ?: return
        leftFlankView?.let { v ->
            if (v.isAttachedToWindow) {
                try { wm.updateViewLayout(v, v.createLayoutParams(passThrough)) } catch (_: Exception) {}
            }
        }
        rightFlankView?.let { v ->
            if (v.isAttachedToWindow) {
                try { wm.updateViewLayout(v, v.createLayoutParams(passThrough)) } catch (_: Exception) {}
            }
        }
    }

    /**
     * Temporarily makes trackpad overlay windows pass-through (FLAG_NOT_TOUCHABLE) so injected
     * cursor taps, long-presses, and swipes pass straight through to the app underneath without
     * ever triggering TrackpadFlankView in a loop.
     */
    fun performWithPassThrough(
        x: Float,
        y: Float,
        endX: Float = x,
        endY: Float = y,
        holdMs: Long = 90L,
        action: () -> Unit
    ) {
        mainHandler.post {
            mainHandler.removeCallbacks(restoreTouchabilityRunnable)
            val overlapsTrackpad = isPointOverTrackpadWindow(x, y) || isPointOverTrackpadWindow(endX, endY)
            setFlankPassThroughTouchable(true)
            val preDelayMs = if (overlapsTrackpad) 28L else 0L
            if (preDelayMs > 0L) {
                mainHandler.postDelayed({
                    try {
                        action()
                    } finally {
                        mainHandler.postDelayed(restoreTouchabilityRunnable, holdMs.coerceAtLeast(60L))
                    }
                }, preDelayMs)
            } else {
                try {
                    action()
                } finally {
                    mainHandler.postDelayed(restoreTouchabilityRunnable, holdMs.coerceAtLeast(60L))
                }
            }
        }
    }

    private var cursorCanvasView: View? = null
    internal var leftFlankView: TrackpadFlankView? = null
    internal var rightFlankView: TrackpadFlankView? = null
    internal var windowManager: WindowManager? = null
    internal val mainHandler = Handler(Looper.getMainLooper())
    internal val trackpadScope = CoroutineScope(Dispatchers.Default + SupervisorJob())

    fun init(context: Context) = reload(context)

    fun reload(context: Context) {
        val service = LightspeedAccessibilityService.instance ?: return
        val wm = service.getSystemService(Context.WINDOW_SERVICE) as? WindowManager ?: return
        windowManager = wm

        val prefs = service.defaultPrefs()
        val labsEnabled = LightspeedBotContract.isLabsEnabled(prefs)
        val leftEnabled = labsEnabled && prefs.getBoolean(LightspeedBotContract.KEY_BOT_TRACKPAD_LEFT_ENABLED, false)
        val rightEnabled = labsEnabled && prefs.getBoolean(LightspeedBotContract.KEY_BOT_TRACKPAD_RIGHT_ENABLED, false)
        if (!leftEnabled && !rightEnabled) {
            hide()
            return
        }

        // Preserve existing views in-place so switching themes/styles via the cursor never drops the trackpad or cursor
        if (cursorCanvasView == null || cursorCanvasView?.isAttachedToWindow == false) {
            cursorCanvasView?.let { try { wm.removeView(it) } catch (_: Exception) {} }
            val cursorView = CursorCanvasView(service).apply {
                visibility = if (isCursorActive) View.VISIBLE else View.GONE
            }
            try {
                wm.addView(cursorView, createFullDisplayOverlayParams(service))
                cursorCanvasView = cursorView
            } catch (_: Exception) {}
        }

        if (leftEnabled) {
            if (leftFlankView == null || leftFlankView?.isAttachedToWindow == false) {
                leftFlankView?.let { try { wm.removeView(it) } catch (_: Exception) {} }
                try {
                    val leftView = TrackpadFlankView(service, isLeft = true)
                    wm.addView(leftView, leftView.createLayoutParams())
                    leftFlankView = leftView
                } catch (_: Exception) {}
            } else {
                leftFlankView?.extendLingerIfDeployed()
            }
        } else {
            leftFlankView?.let { try { wm.removeView(it) } catch (_: Exception) {} ; leftFlankView = null }
        }

        if (rightEnabled) {
            if (rightFlankView == null || rightFlankView?.isAttachedToWindow == false) {
                rightFlankView?.let { try { wm.removeView(it) } catch (_: Exception) {} }
                try {
                    val rightView = TrackpadFlankView(service, isLeft = false)
                    wm.addView(rightView, rightView.createLayoutParams())
                    rightFlankView = rightView
                } catch (_: Exception) {}
            } else {
                rightFlankView?.extendLingerIfDeployed()
            }
        } else {
            rightFlankView?.let { try { wm.removeView(it) } catch (_: Exception) {} ; rightFlankView = null }
        }

        resyncGeometryAndVisibility(service)
        invalidateCursor()
    }

    fun resyncGeometryAndVisibility(service: LightspeedAccessibilityService? = LightspeedAccessibilityService.instance) {
        val svc = service ?: return
        val wm = windowManager ?: (svc.getSystemService(Context.WINDOW_SERVICE) as? WindowManager) ?: return
        val prefs = svc.defaultPrefs()
        val isFlightArmed = LightspeedPreferences.isMasterFlightArmed(svc)
        val policy = prefs.getString(LightspeedPreferences.KEY_ORIENTATION_OVERLAY_POLICY, "adaptive") ?: "adaptive"
        val isLandscape = svc.resources.configuration.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE
        val suppressedByOrientation = (policy == "portrait_only" && isLandscape)
        val vis = if (isFlightArmed && !suppressedByOrientation) View.VISIBLE else View.GONE

        leftFlankView?.let { v ->
            if (!isFlightArmed || suppressedByOrientation) v.retractInternal()
            v.visibility = vis
            if (v.isAttachedToWindow) {
                try { wm.updateViewLayout(v, v.createLayoutParams()) } catch (_: Exception) {}
            }
            v.postInvalidate()
        }
        rightFlankView?.let { v ->
            if (!isFlightArmed || suppressedByOrientation) v.retractInternal()
            v.visibility = vis
            if (v.isAttachedToWindow) {
                try { wm.updateViewLayout(v, v.createLayoutParams()) } catch (_: Exception) {}
            }
            v.postInvalidate()
        }
        if (!isFlightArmed || suppressedByOrientation) {
            isCursorActive = false
            invalidateCursor()
        }
    }

    fun setSuspendedForDialog(suspended: Boolean) {
        val vis = if (suspended) View.GONE else View.VISIBLE
        cursorCanvasView?.visibility = if (suspended || !isCursorActive) View.GONE else View.VISIBLE
        leftFlankView?.let { v ->
            if (suspended) v.retractInternal()
            v.visibility = vis
            v.postInvalidate()
        }
        rightFlankView?.let { v ->
            if (suspended) v.retractInternal()
            v.visibility = vis
            v.postInvalidate()
        }
        if (suspended) {
            isCursorActive = false
            invalidateCursor()
        }
    }

    fun hide() {
        val wm = windowManager ?: return
        cursorCanvasView?.let { try { wm.removeView(it) } catch (_: Exception) {} ; cursorCanvasView = null }
        leftFlankView?.let { try { wm.removeView(it) } catch (_: Exception) {} ; leftFlankView = null }
        rightFlankView?.let { try { wm.removeView(it) } catch (_: Exception) {} ; rightFlankView = null }
        isCursorActive = false
    }

    internal fun invalidateCursor() {
        val v = cursorCanvasView ?: return
        val targetVis = if (isCursorActive) View.VISIBLE else View.GONE
        if (v.visibility != targetVis) {
            v.visibility = targetVis
        }
        if (isCursorActive) {
            v.postInvalidateOnAnimation()
        }
    }

    fun refreshMacroButtonsState() {
        leftFlankView?.refreshHasStepsFlags()
        rightFlankView?.refreshHasStepsFlags()
    }

    internal fun resolveBotId(isLeft: Boolean, armIndex: Int): Int {
        val base = if (isLeft) 0 else 3
        return (base + armIndex.coerceIn(1, 3)).coerceIn(1, LightspeedBotContract.MAX_BOT_MACROS)
    }

    internal fun executeArmShortTap(context: Context, isLeft: Boolean, armIndex: Int) {
        val botId = resolveBotId(isLeft, armIndex)
        LightspeedHapticEngine.click(context)
        (if (isLeft) leftFlankView else rightFlankView)?.extendLingerIfDeployed()
        if (LightspeedBotEngine.isLooping) {
            LightspeedBotEngine.stopContinuousLoop()
            invalidateCursor()
            return
        }
        if (LightspeedBotEngine.isReplaying) {
            LightspeedBotEngine.stopReplay()
            return
        }
        LightspeedBotEngine.playBot(context, botId)
    }

    internal fun executeArmLongPressRecord(context: Context, isLeft: Boolean, armIndex: Int, onRetract: () -> Unit) {
        val botId = resolveBotId(isLeft, armIndex)
        LightspeedHapticEngine.heavyClick(context)
        onRetract()
        if (LightspeedBotEngine.isRecording) {
            LightspeedBotEngine.stopRecording(context)
        } else {
            LightspeedBotEngine.startRecording(context, botId)
        }
    }

    internal fun executeArmTapHoldEdit(context: Context, isLeft: Boolean, armIndex: Int, onRetract: () -> Unit) {
        val botId = resolveBotId(isLeft, armIndex)
        LightspeedHapticEngine.heavyClick(context)
        leftFlankView?.retractInternal()
        rightFlankView?.retractInternal()
        isCursorActive = false
        invalidateCursor()
        onRetract()
        val (pkg, win) = LightspeedBotEngine.resolveActiveScope(context)
        context.startActivity(Intent(context, BotMacroEditorDialog::class.java).apply {
            putExtra(BotMacroEditorDialog.EXTRA_BOT_ID, botId)
            putExtra(BotMacroEditorDialog.EXTRA_TARGET_PACKAGE, pkg)
            putExtra(BotMacroEditorDialog.EXTRA_TARGET_WINDOW_ID, win)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        })
    }

    private class CursorCanvasView(context: Context) : View(context) {
        private val prefs = context.defaultPrefs()
        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)
            if (!isCursorActive) return
            val cursorShape = prefs.getString(LightspeedBotContract.KEY_BOT_CURSOR_SHAPE, "crosshair") ?: "crosshair"
            val colorModeKey = if (activeFlankIsLeft) {
                LightspeedBotContract.KEY_BOT_TRACKPAD_COLOR_MODE_LEFT
            } else {
                LightspeedBotContract.KEY_BOT_TRACKPAD_COLOR_MODE_RIGHT
            }
            val colorMode = prefs.getString(colorModeKey, LightspeedBotContract.COLOR_MODE_HYBRID_FUSION)
                ?: LightspeedBotContract.COLOR_MODE_HYBRID_FUSION
            LightspeedBotTrackpadDraw.drawTacticalCursor(
                canvas = canvas,
                cursorX = cursorX,
                cursorY = cursorY,
                isLooping = LightspeedBotEngine.isLooping,
                loopCount = LightspeedBotEngine.loopIterationCount,
                theme = DeckThemeManager.getSnapshot(context),
                density = resources.displayMetrics.density,
                cursorShape = cursorShape,
                context = context,
                colorMode = colorMode,
                isHoldActive = isDomeHoldActive
            )
        }
    }

}
