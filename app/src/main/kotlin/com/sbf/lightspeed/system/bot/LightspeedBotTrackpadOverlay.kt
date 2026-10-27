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
import com.sbf.lightspeed.system.*
import com.sbf.lightspeed.system.display.createFullDisplayOverlayParams
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

    private var cursorCanvasView: View? = null
    private var leftFlankView: TrackpadFlankView? = null
    private var rightFlankView: TrackpadFlankView? = null
    private var windowManager: WindowManager? = null
    internal val mainHandler = Handler(Looper.getMainLooper())

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
            val cursorView = CursorCanvasView(service)
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

    fun hide() {
        val wm = windowManager ?: return
        cursorCanvasView?.let { try { wm.removeView(it) } catch (_: Exception) {} ; cursorCanvasView = null }
        leftFlankView?.let { try { wm.removeView(it) } catch (_: Exception) {} ; leftFlankView = null }
        rightFlankView?.let { try { wm.removeView(it) } catch (_: Exception) {} ; rightFlankView = null }
        isCursorActive = false
    }

    internal fun invalidateCursor() { cursorCanvasView?.postInvalidateOnAnimation() }

    internal fun resolveBotId(isLeft: Boolean, armIndex: Int): Int {
        val base = if (isLeft) 0 else 3
        return (base + armIndex.coerceIn(1, 3)).coerceIn(1, LightspeedBotContract.MAX_BOT_MACROS)
    }

    internal fun executeArmShortTap(context: Context, isLeft: Boolean, armIndex: Int) {
        val botId = resolveBotId(isLeft, armIndex)
        LightspeedHapticEngine.click(context)
        if (LightspeedBotEngine.isLooping) {
            LightspeedBotEngine.stopContinuousLoop()
            invalidateCursor()
            return
        }
        if (LightspeedBotEngine.isReplaying) {
            LightspeedBotEngine.stopReplay()
            return
        }
        val steps = LightspeedBotEngine.getMacroSteps(context, botId)
        if (steps.isNotEmpty()) {
            LightspeedBotEngine.playBot(context, botId)
        } else {
            Toast.makeText(
                context,
                "Button B$armIndex: Long-press to record • Tap+Hold to edit",
                Toast.LENGTH_SHORT
            ).show()
        }
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
        onRetract()
        context.startActivity(Intent(context, BotMacroEditorDialog::class.java).apply {
            putExtra(BotMacroEditorDialog.EXTRA_BOT_ID, botId)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        })
    }

    private class CursorCanvasView(context: Context) : View(context) {
        private val prefs = context.defaultPrefs()
        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)
            if (!isCursorActive) return
            val cursorShape = prefs.getString(LightspeedBotContract.KEY_BOT_CURSOR_SHAPE, "crosshair") ?: "crosshair"
            LightspeedBotTrackpadDraw.drawTacticalCursor(
                canvas = canvas,
                cursorX = cursorX,
                cursorY = cursorY,
                isLooping = LightspeedBotEngine.isLooping,
                loopCount = LightspeedBotEngine.loopIterationCount,
                theme = DeckThemeManager.getSnapshot(context),
                density = resources.displayMetrics.density,
                cursorShape = cursorShape
            )
        }
    }

    @SuppressLint("ViewConstructor")
    internal class TrackpadFlankView(context: Context, val isLeft: Boolean) : View(context) {
        private val prefs = context.defaultPrefs()
        private val density = resources.displayMetrics.density

        private var isDeployed = false
        private var deployProgress = 0f
        private var deployedOnThisTouch = false
        private var touchDownUptime = 0L
        private var downRawX = 0f
        private var downRawY = 0f
        private var lastTouchX = 0f
        private var lastTouchY = 0f
        private var thumbX = 0f
        private var thumbY = 0f
        private var hoveredArm: Int? = null
        private var holdFiredForCurrentTouch = false
        private var isSecondPressEdit = false
        private var pendingTapArm: Int? = null
        private var pendingTapUptime = 0L

        private var domeLongPressFired = false
        private var isDragging = false
        private var isDragCandidate = false
        private var dragStartX = 0f
        private var dragStartY = 0f
        private var lastDomeTapUpUptime = 0L

        private fun getScreenBounds(): Pair<Float, Float> {
            val wm = context.getSystemService(Context.WINDOW_SERVICE) as? WindowManager
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && wm != null) {
                val bounds = wm.maximumWindowMetrics.bounds
                return Pair(bounds.width().toFloat(), bounds.height().toFloat())
            }
            val dm = android.util.DisplayMetrics()
            @Suppress("DEPRECATION")
            wm?.defaultDisplay?.getRealMetrics(dm)
            val w = if (dm.widthPixels > 0) dm.widthPixels.toFloat() else resources.displayMetrics.widthPixels.toFloat()
            val h = if (dm.heightPixels > 0) dm.heightPixels.toFloat() else resources.displayMetrics.heightPixels.toFloat()
            return Pair(w, h)
        }

        private val lingerRunnable = Runnable { retract() }

        private val domeHoldRunnable = Runnable {
            if (hoveredArm == null && isDeployed) {
                domeLongPressFired = true
                LightspeedHapticEngine.heavyClick(context)
                LightspeedBotEngine.injectLongPressOrDispatch(context, cursorX, cursorY, 650L)
                isDragging = true
                dragStartX = cursorX
                dragStartY = cursorY
            }
        }

        private val pendingTapExecuteRunnable = Runnable {
            val arm = pendingTapArm
            pendingTapArm = null
            if (arm != null) {
                executeArmShortTap(context, isLeft, arm)
            }
        }

        private val armHoldRunnable = Runnable {
            val arm = hoveredArm ?: return@Runnable
            holdFiredForCurrentTouch = true
            mainHandler.removeCallbacks(pendingTapExecuteRunnable)
            pendingTapArm = null
            if (isSecondPressEdit) {
                isSecondPressEdit = false
                executeArmTapHoldEdit(context, isLeft, arm) { retract() }
            } else {
                executeArmLongPressRecord(context, isLeft, arm) { retract() }
            }
        }

        private fun getDpadRadiusPx(): Float {
            val dp = if (isLeft) prefs.getInt(LightspeedBotContract.KEY_BOT_TRACKPAD_RADIUS_LEFT, 72)
            else prefs.getInt(LightspeedBotContract.KEY_BOT_TRACKPAD_RADIUS_RIGHT, 72)
            return dp * density
        }

        private fun getArmReachPx(): Float {
            val dp = if (isLeft) prefs.getInt(LightspeedBotContract.KEY_BOT_TRACKPAD_ARM_REACH_LEFT, 52)
            else prefs.getInt(LightspeedBotContract.KEY_BOT_TRACKPAD_ARM_REACH_RIGHT, 52)
            return dp * density
        }

        private fun getStyle(): String {
            val key = if (isLeft) LightspeedBotContract.KEY_BOT_TRACKPAD_STYLE_LEFT else LightspeedBotContract.KEY_BOT_TRACKPAD_STYLE_RIGHT
            return prefs.getString(key, "classic_arc") ?: "classic_arc"
        }

        private fun getOrientation(): String {
            val key = if (isLeft) LightspeedBotContract.KEY_BOT_TRACKPAD_ORIENTATION_LEFT else LightspeedBotContract.KEY_BOT_TRACKPAD_ORIENTATION_RIGHT
            return prefs.getString(key, "arc") ?: "arc"
        }

        fun createLayoutParams(): WindowManager.LayoutParams {
            val dm = resources.displayMetrics
            val isLandscape = resources.configuration.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE
            val landscapeMode = prefs.getString(
                LightspeedPreferences.KEY_DEFLECTOR_LANDSCAPE_MODE,
                LightspeedPreferences.DEFLECTOR_LANDSCAPE_MODE_CUSTOM
            ) ?: LightspeedPreferences.DEFLECTOR_LANDSCAPE_MODE_CUSTOM

            val basePosYKey = if (isLeft) LightspeedBotContract.KEY_BOT_TRACKPAD_POS_Y_LEFT else LightspeedBotContract.KEY_BOT_TRACKPAD_POS_Y_RIGHT
            val baseHeightKey = if (isLeft) LightspeedBotContract.KEY_BOT_TRACKPAD_HEIGHT_LEFT else LightspeedBotContract.KEY_BOT_TRACKPAD_HEIGHT_RIGHT
            val portraitPosY = prefs.getInt(basePosYKey, 78)
            val portraitTriggerDp = prefs.getInt(baseHeightKey, 120)

            val posY: Int
            val triggerH: Int
            if (!isLandscape) {
                posY = portraitPosY
                triggerH = (portraitTriggerDp * density).toInt()
            } else when (landscapeMode) {
                LightspeedPreferences.DEFLECTOR_LANDSCAPE_MODE_FIXED -> {
                    posY = portraitPosY
                    triggerH = (portraitTriggerDp * density).toInt()
                }
                LightspeedPreferences.DEFLECTOR_LANDSCAPE_MODE_CUSTOM -> {
                    val defLandscapeH = (portraitTriggerDp * 0.55f).toInt().coerceAtLeast(48)
                    posY = prefs.getInt("${basePosYKey}_landscape", portraitPosY)
                    triggerH = (prefs.getInt("${baseHeightKey}_landscape", defLandscapeH) * density).toInt().coerceAtLeast((36f * density).toInt())
                }
                else -> {
                    posY = portraitPosY
                    triggerH = (portraitTriggerDp * density * 0.55f).toInt().coerceAtLeast((36f * density).toInt())
                }
            }

            val dpadRadiusDp = if (isLeft) prefs.getInt(LightspeedBotContract.KEY_BOT_TRACKPAD_RADIUS_LEFT, 72)
            else prefs.getInt(LightspeedBotContract.KEY_BOT_TRACKPAD_RADIUS_RIGHT, 72)
            val armReachDp = if (isLeft) prefs.getInt(LightspeedBotContract.KEY_BOT_TRACKPAD_ARM_REACH_LEFT, 52)
            else prefs.getInt(LightspeedBotContract.KEY_BOT_TRACKPAD_ARM_REACH_RIGHT, 52)
            val touchWidthDp = if (isLeft) prefs.getInt(LightspeedBotContract.KEY_BOT_TRACKPAD_TOUCH_WIDTH_LEFT, 14)
            else prefs.getInt(LightspeedBotContract.KEY_BOT_TRACKPAD_TOUCH_WIDTH_RIGHT, 14)

            val totalRadiusPx = (dpadRadiusDp + armReachDp + 28f) * density
            val targetCenterY = dm.heightPixels * (posY / 100f)
            val safeDpadCenterY = targetCenterY.coerceIn(
                totalRadiusPx.coerceAtMost(dm.heightPixels / 2f),
                (dm.heightPixels - totalRadiusPx).coerceAtLeast(dm.heightPixels / 2f)
            )

            val w = if (isDeployed) ((dpadRadiusDp + armReachDp + 44f) * density).toInt() else (touchWidthDp * density).toInt()
            val h = if (isDeployed) (totalRadiusPx * 2f).toInt() else triggerH
            val yPos = if (isDeployed) {
                (safeDpadCenterY - totalRadiusPx).toInt().coerceAtLeast(0)
            } else {
                ((dm.heightPixels * (posY / 100f)) - (triggerH / 2f)).toInt().coerceIn(0, (dm.heightPixels - triggerH).coerceAtLeast(0))
            }

            return WindowManager.LayoutParams(
                w, h, WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                PixelFormat.TRANSLUCENT
            ).apply {
                gravity = Gravity.TOP or (if (isLeft) Gravity.START else Gravity.END)
                x = 0; y = yPos
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
                }
            }
        }

        private fun deploy() {
            if (isDeployed) return
            isDeployed = true; isCursorActive = true; deployProgress = 1f
            mainHandler.removeCallbacks(lingerRunnable)
            if (isAttachedToWindow) {
                try {
                    windowManager?.updateViewLayout(this, createLayoutParams())
                } catch (e: Exception) {
                    logSwallowed("LightspeedBotTrackpadOverlay", "deploy.updateViewLayout", e)
                }
            }
            invalidate(); invalidateCursor()
            LightspeedHapticEngine.click(context)
        }

        internal fun retractInternal() = retract()

        internal fun extendLingerIfDeployed() {
            if (isDeployed) {
                isCursorActive = true
                armLingerTimer()
                invalidate()
                invalidateCursor()
            }
        }

        private fun retract() {
            if (!isDeployed) return
            mainHandler.removeCallbacks(armHoldRunnable)
            isDeployed = false; isCursorActive = false; deployProgress = 0f; hoveredArm = null; isSecondPressEdit = false
            if (isAttachedToWindow) {
                try {
                    windowManager?.updateViewLayout(this, createLayoutParams())
                } catch (e: Exception) {
                    logSwallowed("LightspeedBotTrackpadOverlay", "retract.updateViewLayout", e)
                }
            }
            invalidate(); invalidateCursor()
        }

        private fun armLingerTimer() {
            val ms = prefs.getInt(LightspeedBotContract.KEY_BOT_TRACKPAD_LINGER_MS, 1800).toLong()
            mainHandler.removeCallbacks(lingerRunnable)
            mainHandler.postDelayed(lingerRunnable, ms)
        }

        @SuppressLint("ClickableViewAccessibility")
        override fun onTouchEvent(event: MotionEvent): Boolean {
            val now = SystemClock.uptimeMillis()
            val wF = width.toFloat()
            val centerY = height / 2f
            val radiusPx = getDpadRadiusPx()
            val armReachPx = getArmReachPx()
            val orientation = getOrientation()
            val armCount = 3

            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    touchDownUptime = now
                    downRawX = event.rawX; downRawY = event.rawY
                    lastTouchX = event.rawX; lastTouchY = event.rawY
                    holdFiredForCurrentTouch = false
                    domeLongPressFired = false
                    isSecondPressEdit = false
                    isDragging = false
                    isDragCandidate = false
                    mainHandler.removeCallbacks(lingerRunnable)
                    mainHandler.removeCallbacks(armHoldRunnable)
                    mainHandler.removeCallbacks(domeHoldRunnable)
                    if (!isDeployed) {
                        deployedOnThisTouch = true
                        hoveredArm = null
                        deploy()
                    } else {
                        deployedOnThisTouch = false
                        hoveredArm = LightspeedBotTrackpadDraw.hitTestArm(
                            event.x, event.y, armCount, isLeft, centerY, radiusPx, deployProgress, wF, density, armReachPx, orientation
                        )
                        if (hoveredArm != null) {
                            if (pendingTapArm == hoveredArm && (now - pendingTapUptime) <= 550L) {
                                // Second press within 550ms -> Tap + Hold (or Double-Tap) to Edit!
                                mainHandler.removeCallbacks(pendingTapExecuteRunnable)
                                pendingTapArm = null
                                isSecondPressEdit = true
                                mainHandler.postDelayed(armHoldRunnable, 360L)
                            } else {
                                mainHandler.postDelayed(armHoldRunnable, 450L)
                            }
                        } else {
                            // Touch is on the trackpad dome
                            val isDoubleTapCandidate = (now - lastDomeTapUpUptime) <= 350L
                            if (isDoubleTapCandidate) {
                                isDragCandidate = true
                                dragStartX = cursorX
                                dragStartY = cursorY
                            }
                            mainHandler.postDelayed(domeHoldRunnable, 450L)
                        }
                        invalidate()
                    }
                    return true
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = event.rawX - lastTouchX; val dy = event.rawY - lastTouchY
                    lastTouchX = event.rawX; lastTouchY = event.rawY
                    val totalMove = hypot((event.rawX - downRawX).toDouble(), (event.rawY - downRawY).toDouble()).toFloat()

                    if (!isDeployed) {
                        deployedOnThisTouch = true
                        deploy()
                    }
                    if (isDeployed) {
                        mainHandler.removeCallbacks(lingerRunnable)
                        if (hoveredArm != null && totalMove > (18f * density)) {
                            mainHandler.removeCallbacks(armHoldRunnable)
                            hoveredArm = null
                            isSecondPressEdit = false
                        }
                        if (hoveredArm == null) {
                            if (totalMove > (12f * density) && !domeLongPressFired) {
                                mainHandler.removeCallbacks(domeHoldRunnable)
                            }
                            if (isDragCandidate && totalMove > (8f * density)) {
                                isDragging = true
                            }
                            if (!holdFiredForCurrentTouch) {
                                val (screenW, screenH) = getScreenBounds()
                                val sensitivity = prefs.getFloat(LightspeedBotContract.KEY_BOT_TRACKPAD_SENSITIVITY, 1.3f)
                                val speed = hypot(dx.toDouble(), dy.toDouble()).toFloat()
                                val accel = 1f + (speed / (30f * density)).coerceIn(0f, 2.5f)
                                cursorX = (cursorX + (dx * accel * sensitivity)).coerceIn(0f, screenW)
                                cursorY = (cursorY + (dy * accel * sensitivity)).coerceIn(0f, screenH)
                                thumbX = event.x.coerceIn(0f, wF); thumbY = event.y.coerceIn(0f, height.toFloat())
                                invalidate(); invalidateCursor()
                            }
                        }
                    }
                    return true
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    mainHandler.removeCallbacks(armHoldRunnable)
                    mainHandler.removeCallbacks(domeHoldRunnable)
                    if (isDeployed && !holdFiredForCurrentTouch) {
                        if (!deployedOnThisTouch) {
                            val totalMove = hypot((event.rawX - downRawX).toDouble(), (event.rawY - downRawY).toDouble()).toFloat()
                            val holdDuration = now - touchDownUptime
                            val arm = hoveredArm ?: LightspeedBotTrackpadDraw.hitTestArm(
                                event.x, event.y, armCount, isLeft, centerY, radiusPx, deployProgress, wF, density, armReachPx, orientation
                            )
                            if (arm != null && totalMove <= (22f * density)) {
                                if (isSecondPressEdit) {
                                    isSecondPressEdit = false
                                    executeArmTapHoldEdit(context, isLeft, arm) { retract() }
                                } else {
                                    pendingTapArm = arm
                                    pendingTapUptime = now
                                    mainHandler.removeCallbacks(pendingTapExecuteRunnable)
                                    mainHandler.postDelayed(pendingTapExecuteRunnable, 360L)
                                }
                            } else if (arm == null) {
                                if (isDragging) {
                                    // Drag & Move finished
                                    val dragDist = hypot((cursorX - dragStartX).toDouble(), (cursorY - dragStartY).toDouble()).toFloat()
                                    if (dragDist > (10f * density)) {
                                        LightspeedHapticEngine.click(context)
                                        val swipeDuration = (now - touchDownUptime).coerceIn(120L, 800L)
                                        LightspeedBotEngine.injectSwipeOrDispatch(context, dragStartX, dragStartY, cursorX, cursorY, swipeDuration)
                                    }
                                    isDragging = false
                                    isDragCandidate = false
                                    lastDomeTapUpUptime = 0L
                                } else if (domeLongPressFired) {
                                    // Long press already injected on hold timer
                                    domeLongPressFired = false
                                    lastDomeTapUpUptime = 0L
                                } else if ((now - lastDomeTapUpUptime) <= 350L && totalMove <= (12f * density) && holdDuration < 280L) {
                                    // Double Tap on dome
                                    LightspeedHapticEngine.click(context)
                                    LightspeedBotEngine.injectDoubleTapOrDispatch(context, cursorX, cursorY)
                                    lastDomeTapUpUptime = 0L
                                } else if (totalMove <= (12f * density) && holdDuration < 280L) {
                                    // Single Tap on dome
                                    LightspeedHapticEngine.click(context)
                                    LightspeedBotEngine.injectTapOrDispatch(context, cursorX, cursorY)
                                    lastDomeTapUpUptime = now
                                }
                            }
                        }
                        deployedOnThisTouch = false
                        isSecondPressEdit = false
                        isDragging = false
                        isDragCandidate = false
                        domeLongPressFired = false
                        hoveredArm = null
                        invalidate(); armLingerTimer()
                    } else {
                        deployedOnThisTouch = false
                        isSecondPressEdit = false
                        isDragging = false
                        isDragCandidate = false
                        domeLongPressFired = false
                        hoveredArm = null
                        invalidate()
                    }
                    return true
                }
            }
            return super.onTouchEvent(event)
        }

        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)
            val theme = DeckThemeManager.getSnapshot(context)
            val centerY = height / 2f
            val wF = width.toFloat()

            if (!isDeployed) {
                val preview = if (isLeft) prefs.getBoolean(LightspeedBotContract.KEY_BOT_TRACKPAD_PREVIEW_LEFT, false)
                else prefs.getBoolean(LightspeedBotContract.KEY_BOT_TRACKPAD_PREVIEW_RIGHT, false)
                val triggerOpacityPct = if (isLeft) prefs.getInt(LightspeedBotContract.KEY_BOT_TRACKPAD_TRIGGER_OPACITY_LEFT, 50)
                else prefs.getInt(LightspeedBotContract.KEY_BOT_TRACKPAD_TRIGGER_OPACITY_RIGHT, 50)
                val alphaFactor = (triggerOpacityPct / 100f).coerceIn(0f, 1f)

                if (alphaFactor <= 0f && !preview) return

                val p = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG)
                if (preview) {
                    p.style = android.graphics.Paint.Style.FILL
                    p.color = theme.tintArgb.toInt()
                    p.alpha = ((140 * alphaFactor.coerceAtLeast(0.25f))).toInt().coerceIn(0, 255)
                    canvas.drawRoundRect(android.graphics.RectF(0f, 0f, wF, height.toFloat()), 4f * density, 4f * density, p)
                    p.style = android.graphics.Paint.Style.STROKE
                    p.strokeWidth = 2f * density
                    p.color = theme.borderColorArgb.toInt()
                    p.alpha = ((240 * alphaFactor.coerceAtLeast(0.25f))).toInt().coerceIn(0, 255)
                    canvas.drawRoundRect(android.graphics.RectF(1f * density, 1f * density, wF - (1f * density), height.toFloat() - (1f * density)), 4f * density, 4f * density, p)
                } else {
                    p.style = android.graphics.Paint.Style.FILL
                    p.color = theme.borderColorArgb.toInt()
                    p.alpha = ((65 * alphaFactor)).toInt().coerceIn(0, 255)
                    val rect = if (isLeft) android.graphics.RectF(0f, 0f, 6f * density, height.toFloat())
                    else android.graphics.RectF(wF - (6f * density), 0f, wF, height.toFloat())
                    canvas.drawRoundRect(rect, 4f * density, 4f * density, p)
                }
                return
            }

            val radiusPx = getDpadRadiusPx()
            val armReachPx = getArmReachPx()
            val style = getStyle()
            val orientation = getOrientation()
            val armCount = 3

            val dpadOpacityPct = if (isLeft) prefs.getInt(LightspeedBotContract.KEY_BOT_TRACKPAD_DPAD_OPACITY_LEFT, 100)
            else prefs.getInt(LightspeedBotContract.KEY_BOT_TRACKPAD_DPAD_OPACITY_RIGHT, 100)
            val dpadAlphaFactor = (dpadOpacityPct / 100f).coerceIn(0.05f, 1f)

            val armsOpacityPct = if (isLeft) prefs.getInt(LightspeedBotContract.KEY_BOT_TRACKPAD_ARMS_OPACITY_LEFT, 100)
            else prefs.getInt(LightspeedBotContract.KEY_BOT_TRACKPAD_ARMS_OPACITY_RIGHT, 100)
            val armsAlphaFactor = (armsOpacityPct / 100f).coerceIn(0.05f, 1f)

            val botIds = (1..3).map { resolveBotId(isLeft, it) }
            val hasStepsFlags = botIds.map { LightspeedBotEngine.getMacroSteps(context, it).isNotEmpty() }

            LightspeedBotTrackpadDraw.drawHalfCircleTrackpad(
                canvas = canvas, isLeft = isLeft, centerY = centerY, radiusPx = radiusPx,
                deployProgress = deployProgress,
                thumbX = if (thumbX > 0f) thumbX else (if (isLeft) 28f * density else wF - (28f * density)),
                thumbY = if (thumbY > 0f) thumbY else centerY,
                wF = wF, theme = theme, density = density,
                alphaFactor = dpadAlphaFactor,
                style = style,
                orientation = orientation
            )

            LightspeedBotTrackpadDraw.drawRobotArms(
                canvas = canvas, isLeft = isLeft, centerY = centerY, radiusPx = radiusPx,
                deployProgress = deployProgress, armCount = armCount, hoveredArm = hoveredArm,
                activeBotIds = botIds, hasStepsFlags = hasStepsFlags,
                isRecording = LightspeedBotEngine.isRecording,
                recordingBotId = LightspeedBotEngine.activeRecordingBotId,
                wF = wF, theme = theme, density = density, armReachPx = armReachPx,
                alphaFactor = armsAlphaFactor,
                style = style,
                orientation = orientation
            )
        }
    }
}
