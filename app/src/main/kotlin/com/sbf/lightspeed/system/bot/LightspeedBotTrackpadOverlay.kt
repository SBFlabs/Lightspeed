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
import com.sbf.lightspeed.settings.CentralCommandActivity
import com.sbf.lightspeed.settings.CentralCommandBackdropBlur
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
    @Volatile
    var isSuspendedForDialog = false
        private set
    @Volatile
    internal var isLaunchingMacroEditor = false
        private set
    @Volatile
    internal var isPendingRecordingLaunch = false
        private set
    @Volatile
    internal var isPlaybackPassThroughActive = false
        private set

    internal fun isSuppressedByOverlayHost(): Boolean {
        return isSuspendedForDialog ||
            isLaunchingMacroEditor ||
            isPendingRecordingLaunch ||
            LightspeedBotRecorderOverlay.isShowing
    }

    fun setPendingRecordingLaunch(pending: Boolean) {
        isPendingRecordingLaunch = pending
        if (pending) {
            removeOverlayViewsOnMainThread()
        }
    }

    private val restoreTouchabilityRunnable = Runnable {
        if (!isPlaybackPassThroughActive) {
            setFlankPassThroughTouchable(false)
        }
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
        val effectivePassThrough = passThrough || isPlaybackPassThroughActive
        isInjectingPassThrough = effectivePassThrough
        val wm = windowManager ?: return
        leftFlankView?.let { v ->
            if (v.isAttachedToWindow) {
                try { wm.updateViewLayout(v, v.createLayoutParams(effectivePassThrough)) } catch (_: Exception) {}
            }
        }
        rightFlankView?.let { v ->
            if (v.isAttachedToWindow) {
                try { wm.updateViewLayout(v, v.createLayoutParams(effectivePassThrough)) } catch (_: Exception) {}
            }
        }
    }

    fun beginPlaybackPassThrough() {
        val action = {
            isPlaybackPassThroughActive = true
            mainHandler.removeCallbacks(restoreTouchabilityRunnable)
            leftFlankView?.let {
                it.cancelPendingTaps()
                it.cancelLinger()
                it.retractInternal()
            }
            rightFlankView?.let {
                it.cancelPendingTaps()
                it.cancelLinger()
                it.retractInternal()
            }
            isCursorActive = false
            isDomeHoldActive = false
            invalidateCursor()
            setFlankPassThroughTouchable(true)
        }
        if (Looper.myLooper() == Looper.getMainLooper()) action() else mainHandler.post(action)
    }

    fun endPlaybackPassThrough() {
        val action = {
            isPlaybackPassThroughActive = false
            if (!isSuppressedByOverlayHost()) {
                setFlankPassThroughTouchable(false)
            } else {
                isInjectingPassThrough = false
            }
        }
        if (Looper.myLooper() == Looper.getMainLooper()) action() else mainHandler.post(action)
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
        val runBlock: () -> Unit = {
            if (isPlaybackPassThroughActive) {
                action()
            } else {
                mainHandler.removeCallbacks(restoreTouchabilityRunnable)
                val overlapsTrackpad = isPointOverTrackpadWindow(x, y) || isPointOverTrackpadWindow(endX, endY)
                setFlankPassThroughTouchable(true)
                val preDelayMs = if (overlapsTrackpad) 35L else 0L
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
        if (Looper.myLooper() == Looper.getMainLooper()) runBlock() else mainHandler.post(runBlock)
    }

    private var cursorCanvasView: View? = null
    internal var leftFlankView: TrackpadFlankView? = null
    internal var rightFlankView: TrackpadFlankView? = null
    internal var windowManager: WindowManager? = null
    internal val mainHandler = Handler(Looper.getMainLooper())
    internal val trackpadScope = CoroutineScope(Dispatchers.Default + SupervisorJob())

    internal val TRACKPAD_TIP_RES_IDS = intArrayOf(
        com.sbf.lightspeed.R.string.tp_tip_1,
        com.sbf.lightspeed.R.string.tp_tip_2,
        com.sbf.lightspeed.R.string.tp_tip_3
    )
    internal var trackpadTipIndex: Int = -1

    internal fun advanceTrackpadTip() {
        trackpadTipIndex = (trackpadTipIndex + 1) % TRACKPAD_TIP_RES_IDS.size
    }

    fun init(context: Context) = reload(context)

    fun reload(context: Context) {
        val service = LightspeedAccessibilityService.instance ?: return
        val wm = service.getSystemService(Context.WINDOW_SERVICE) as? WindowManager ?: return
        windowManager = wm
        if (isSuppressedByOverlayHost()) {
            removeOverlayViewsOnMainThread()
            return
        }

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
        if (isSuppressedByOverlayHost()) {
            removeOverlayViewsOnMainThread()
            return
        }
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

    private fun removeOverlayViewsOnMainThread() {
        val detach = {
            mainHandler.removeCallbacks(restoreTouchabilityRunnable)
            val wm = windowManager
            leftFlankView?.let { v ->
                v.cancelPendingTaps()
                v.cancelLinger()
                v.retractInternal()
                v.visibility = View.GONE
                try { wm?.removeView(v) } catch (_: Exception) {}
            }
            leftFlankView = null
            rightFlankView?.let { v ->
                v.cancelPendingTaps()
                v.cancelLinger()
                v.retractInternal()
                v.visibility = View.GONE
                try { wm?.removeView(v) } catch (_: Exception) {}
            }
            rightFlankView = null
            cursorCanvasView?.let { c ->
                c.visibility = View.GONE
                try { wm?.removeView(c) } catch (_: Exception) {}
            }
            cursorCanvasView = null
            isCursorActive = false
            isDomeHoldActive = false
        }
        if (Looper.myLooper() == Looper.getMainLooper()) detach() else mainHandler.post(detach)
    }

    fun setSuspendedForDialog(suspended: Boolean) {
        if (suspended) {
            isSuspendedForDialog = true
            removeOverlayViewsOnMainThread()
        } else {
            isSuspendedForDialog = false
            if (!isSuppressedByOverlayHost()) {
                val restore = {
                    if (!isSuppressedByOverlayHost()) {
                        LightspeedAccessibilityService.instance?.let { reload(it) }
                        refreshMacroButtonsState()
                    }
                }
                if (Looper.myLooper() == Looper.getMainLooper()) restore() else mainHandler.post(restore)
            }
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
        if (isSuppressedByOverlayHost()) {
            isCursorActive = false
            cursorCanvasView?.visibility = View.GONE
            return
        }
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

    fun onMacroCleared(botId: Int) {
        val clearAction = {
            leftFlankView?.clearCachedFlagForBot(botId)
            rightFlankView?.clearCachedFlagForBot(botId)
            refreshMacroButtonsState()
        }
        if (Looper.myLooper() == Looper.getMainLooper()) clearAction() else mainHandler.post(clearAction)
    }

    fun onMacroStateChanged(botId: Int) {
        if (Looper.myLooper() == Looper.getMainLooper()) {
            refreshMacroButtonsState()
        } else {
            mainHandler.post { refreshMacroButtonsState() }
        }
    }

    internal fun resolveBotId(isLeft: Boolean, armIndex: Int): Int {
        val base = if (isLeft) 0 else 3
        return (base + armIndex.coerceIn(1, 3)).coerceIn(1, LightspeedBotContract.MAX_BOT_MACROS)
    }

    internal fun executeArmShortTap(context: Context, isLeft: Boolean, armIndex: Int) {
        val botId = resolveBotId(isLeft, armIndex)
        LightspeedHapticEngine.click(context)
        if (LightspeedBotEngine.isLooping) {
            (if (isLeft) leftFlankView else rightFlankView)?.extendLingerIfDeployed()
            LightspeedBotEngine.stopContinuousLoop()
            invalidateCursor()
            return
        }
        if (LightspeedBotEngine.isReplaying) {
            (if (isLeft) leftFlankView else rightFlankView)?.extendLingerIfDeployed()
            LightspeedBotEngine.stopReplay()
            return
        }
        leftFlankView?.retractInternal()
        rightFlankView?.retractInternal()
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
        if (isLaunchingMacroEditor) return
        val botId = resolveBotId(isLeft, armIndex)
        LightspeedHapticEngine.heavyClick(context)
        val (pkg, win) = LightspeedBotEngine.resolveActiveScope(context)
        isLaunchingMacroEditor = true
        setSuspendedForDialog(true)
        onRetract()
        DeckThemeManager.getSnapshot(context)
        val intent = Intent(context, BotMacroEditorDialog::class.java).apply {
            putExtra(BotMacroEditorDialog.EXTRA_BOT_ID, botId)
            putExtra(BotMacroEditorDialog.EXTRA_TARGET_PACKAGE, pkg)
            putExtra(BotMacroEditorDialog.EXTRA_TARGET_WINDOW_ID, win)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        }
        val wm = windowManager ?: (context.getSystemService(Context.WINDOW_SERVICE) as? WindowManager)
        CentralCommandBackdropBlur.prepareForCentralCommand(
            windowManager = wm,
            forceRecapture = true,
            settleDelayMs = 110L
        ) {
            mainHandler.post {
                isLaunchingMacroEditor = false
                try {
                    context.startActivity(intent)
                    mainHandler.postDelayed({
                        if (!BotMacroEditorDialog.isActive &&
                            isSuspendedForDialog &&
                            !isLaunchingMacroEditor &&
                            !isPendingRecordingLaunch &&
                            !LightspeedBotRecorderOverlay.isShowing
                        ) {
                            setSuspendedForDialog(false)
                        }
                    }, 2500L)
                } catch (e: Exception) {
                    logSwallowed("LightspeedBotTrackpadOverlay", "executeArmTapHoldEdit.startActivity", e)
                    setSuspendedForDialog(false)
                    if (!CentralCommandActivity.isActive) {
                        CentralCommandBackdropBlur.onWindowHidden(false)
                    }
                }
            }
        }
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
            if (!LightspeedBotEngine.isLooping && !LightspeedBotEngine.isReplaying && width > 0 && height > 0) {
                val density = resources.displayMetrics.density
                val insets = rootWindowInsets
                val (topInset, bottomInset) = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && insets != null) {
                    val sys = insets.getInsetsIgnoringVisibility(
                        WindowInsets.Type.statusBars() or WindowInsets.Type.displayCutout()
                    )
                    val nav = insets.getInsetsIgnoringVisibility(WindowInsets.Type.navigationBars())
                    sys.top.toFloat().coerceAtLeast(24f * density) to nav.bottom.toFloat()
                } else {
                    @Suppress("DEPRECATION")
                    val b = insets?.systemWindowInsetBottom?.toFloat() ?: 0f
                    (28f * density) to b
                }
                val tipIdx = trackpadTipIndex.coerceAtLeast(0) % TRACKPAD_TIP_RES_IDS.size
                val tipBody = lsString(TRACKPAD_TIP_RES_IDS[tipIdx]).trim()
                if (tipBody.isNotEmpty()) {
                    val palette = DeckThemeManager.getSnapshot(context).resolveCanvasPalette(context, colorMode)
                    val baselineY = if (cursorY < topInset + 56f * density) {
                        height.toFloat() - bottomInset - 18f * density
                    } else {
                        topInset + 18f * density
                    }
                    com.sbf.lightspeed.drawInlineGhostTipSingleLine(
                        canvas = canvas,
                        tipBody = tipBody,
                        cx = width * 0.5f,
                        baselineY = baselineY,
                        maxWidth = (width.toFloat() - 28f * density).coerceAtLeast(140f * density),
                        density = density,
                        baseTextSize = (if (width > height) 10.2f else 11.0f) * density,
                        accentColor = palette.primaryAccentArgb,
                        boldTypeface = DeviceFontScanner.resolveGlobalOverlayTypeface(context, isBold = true),
                        regularTypeface = DeviceFontScanner.resolveGlobalOverlayTypeface(context, isBold = false),
                        alphaFactor = 0.96f
                    )
                }
            }
        }
    }

}
