package com.sbf.lightspeed.touch

import android.content.Context
import android.graphics.RectF
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import com.sbf.lightspeed.LightspeedAccessibilityService
import com.sbf.lightspeed.updateMetricsDimensions
import com.sbf.lightspeed.system.LightspeedPreferences
import com.sbf.lightspeed.system.bot.LightspeedBotContract
import com.sbf.lightspeed.system.bot.LightspeedBotTrackpadOverlay
import com.sbf.lightspeed.system.defaultPrefs
import kotlin.math.max
import kotlin.math.min

/**
 * Shared edge arbiter coordinating Deflector flank gestures and Cybernetic Trackpad deployment
 * through a single owning edge overlay window per side instead of stacking separate strips.
 */
object LightspeedEdgeArbiter {

    private var latchedLeftToTrackpad = false
    private var latchedRightToTrackpad = false

    private fun computeDeflectorVerticalSpan(context: Context, isLeft: Boolean): Pair<Float, Float>? {
        val svc = LightspeedAccessibilityService.instance ?: return null
        val screenH = context.resources.displayMetrics.heightPixels.toFloat()
        return if (isLeft) {
            val wing = svc.leftWingOverlayView ?: return null
            if (!wing.isAttachedToWindow || wing.visibility != View.VISIBLE || wing.isLockscreenPillOnly) return null
            if (wing.bottomTouchBounds.bottom <= wing.topTouchBounds.top) return null
            val winY = (wing.layoutParams as? WindowManager.LayoutParams)?.y?.toFloat() ?: 0f
            val top = (winY + wing.topTouchBounds.top).coerceIn(0f, screenH)
            val bottom = (winY + wing.bottomTouchBounds.bottom).coerceIn(top, screenH)
            top to bottom
        } else {
            val cruise = svc.overlayView ?: return null
            if (!cruise.isAttachedToWindow || cruise.visibility != View.VISIBLE || cruise.isLockscreenPillOnly) return null
            if (cruise.bottomTouchBounds.bottom <= cruise.topTouchBounds.top) return null
            val winY = (cruise.layoutParams as? WindowManager.LayoutParams)?.y?.toFloat() ?: 0f
            val top = (winY + cruise.topTouchBounds.top).coerceIn(0f, screenH)
            val bottom = (winY + cruise.bottomTouchBounds.bottom).coerceIn(top, screenH)
            top to bottom
        }
    }

    fun isDeflectorHandlingEdge(context: Context, isLeft: Boolean): Boolean {
        if (!LightspeedPreferences.isMasterFlightArmed(context)) return false
        val enabled = if (isLeft) {
            LightspeedPreferences.isLeftDeflectorEnabled(context)
        } else {
            LightspeedPreferences.isRightDeflectorEnabled(context)
        }
        if (!enabled) return false
        val svc = LightspeedAccessibilityService.instance ?: return false
        val deflectorActive = if (isLeft) {
            val wing = svc.leftWingOverlayView
            wing != null && wing.isAttachedToWindow && wing.visibility == View.VISIBLE && !wing.isLockscreenPillOnly
        } else {
            val cruise = svc.overlayView
            cruise != null && cruise.isAttachedToWindow && cruise.visibility == View.VISIBLE && !cruise.isLockscreenPillOnly
        }
        if (!deflectorActive) return false
        val tp = computeTrackpadTriggerBoundsOnScreen(context, isLeft) ?: return true
        val span = computeDeflectorVerticalSpan(context, isLeft) ?: return true
        return tp.bottom >= span.first - 4f && tp.top <= span.second + 4f
    }

    fun computeTrackpadTriggerBoundsOnScreen(context: Context, isLeft: Boolean): RectF? {
        if (LightspeedBotTrackpadOverlay.isSuppressedByOverlayHost() ||
            LightspeedBotTrackpadOverlay.isPlaybackPassThroughActive ||
            !LightspeedPreferences.isMasterFlightArmed(context)
        ) {
            return null
        }
        val prefs = context.defaultPrefs()
        if (!LightspeedBotContract.isLabsEnabled(prefs)) return null
        val sideEnabled = if (isLeft) {
            prefs.getBoolean(LightspeedBotContract.KEY_BOT_TRACKPAD_LEFT_ENABLED, false)
        } else {
            prefs.getBoolean(LightspeedBotContract.KEY_BOT_TRACKPAD_RIGHT_ENABLED, false)
        }
        if (!sideEnabled) return null

        val policy = prefs.getString(LightspeedPreferences.KEY_ORIENTATION_OVERLAY_POLICY, "adaptive") ?: "adaptive"
        val dm = context.resources.displayMetrics
        val isLandscape = context.resources.configuration.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE
        if (policy == "portrait_only" && isLandscape) return null

        val density = dm.density
        val landscapeMode = prefs.getString(
            LightspeedPreferences.KEY_DEFLECTOR_LANDSCAPE_MODE,
            LightspeedPreferences.DEFLECTOR_LANDSCAPE_MODE_CUSTOM
        ) ?: LightspeedPreferences.DEFLECTOR_LANDSCAPE_MODE_CUSTOM

        val basePosYKey = if (isLeft) LightspeedBotContract.KEY_BOT_TRACKPAD_POS_Y_LEFT else LightspeedBotContract.KEY_BOT_TRACKPAD_POS_Y_RIGHT
        val baseHeightKey = if (isLeft) LightspeedBotContract.KEY_BOT_TRACKPAD_HEIGHT_LEFT else LightspeedBotContract.KEY_BOT_TRACKPAD_HEIGHT_RIGHT
        val portraitPosY = prefs.getInt(basePosYKey, 78)
        val portraitTriggerDp = prefs.getInt(baseHeightKey, 120)

        val posY: Int
        val triggerH: Float
        if (!isLandscape) {
            posY = portraitPosY
            triggerH = portraitTriggerDp * density
        } else when (landscapeMode) {
            LightspeedPreferences.DEFLECTOR_LANDSCAPE_MODE_FIXED -> {
                posY = portraitPosY
                triggerH = portraitTriggerDp * density
            }
            LightspeedPreferences.DEFLECTOR_LANDSCAPE_MODE_CUSTOM -> {
                val defLandscapeH = (portraitTriggerDp * 0.55f).toInt().coerceAtLeast(48)
                posY = prefs.getInt("${basePosYKey}_landscape", portraitPosY)
                triggerH = prefs.getInt("${baseHeightKey}_landscape", defLandscapeH).toFloat().coerceAtLeast(36f) * density
            }
            else -> {
                posY = portraitPosY
                triggerH = (portraitTriggerDp * density * 0.55f).coerceAtLeast(36f * density)
            }
        }

        val touchWidthDp = if (isLeft) {
            prefs.getInt(LightspeedBotContract.KEY_BOT_TRACKPAD_TOUCH_WIDTH_LEFT, 14)
        } else {
            prefs.getInt(LightspeedBotContract.KEY_BOT_TRACKPAD_TOUCH_WIDTH_RIGHT, 14)
        }
        val touchW = (touchWidthDp * density).coerceAtLeast(1f)
        val screenW = dm.widthPixels.toFloat()
        val screenH = dm.heightPixels.toFloat()
        val top = ((screenH * (posY / 100f)) - (triggerH * 0.5f)).coerceIn(0f, (screenH - triggerH).coerceAtLeast(0f))
        val bottom = (top + triggerH).coerceAtMost(screenH)
        return if (isLeft) {
            RectF(0f, top, touchW, bottom)
        } else {
            RectF(screenW - touchW, top, screenW, bottom)
        }
    }

    /**
     * Computes unioned (top, bottom, maxWidthPx) for the single active edge window when a
     * contiguous Trackpad trigger band overlaps or adjoins Deflector top/bottom, keeping
     * width bounded to [deflectorMaxWidthPx] so Deflector never creates edge dead-zones.
     */
    fun computeSharedEdgeWindowBounds(
        context: Context,
        isLeft: Boolean,
        deflectorTop: Float,
        deflectorBottom: Float,
        deflectorMaxWidthPx: Float
    ): Triple<Float, Float, Float> {
        val tp = computeTrackpadTriggerBoundsOnScreen(context, isLeft)
            ?: return Triple(deflectorTop, deflectorBottom, deflectorMaxWidthPx)
        val isContiguous = tp.bottom >= deflectorTop - 4f && tp.top <= deflectorBottom + 4f
        if (!isContiguous) {
            return Triple(deflectorTop, deflectorBottom, deflectorMaxWidthPx)
        }
        return Triple(
            min(deflectorTop, tp.top),
            max(deflectorBottom, tp.bottom),
            deflectorMaxWidthPx
        )
    }

    fun routeEdgeTouch(
        context: Context,
        isLeft: Boolean,
        centerScreenTop: Float,
        centerScreenBottom: Float,
        event: MotionEvent
    ): Boolean {
        val action = event.actionMasked
        val currentlyLatched = if (isLeft) latchedLeftToTrackpad else latchedRightToTrackpad

        if (action == MotionEvent.ACTION_DOWN) {
            if (isLeft) latchedLeftToTrackpad = false else latchedRightToTrackpad = false
            if (!LightspeedBotTrackpadOverlay.hasLiveRetractedFlankView(isLeft)) {
                return false
            }
            val tpBounds = computeTrackpadTriggerBoundsOnScreen(context, isLeft)
            if (tpBounds != null && tpBounds.contains(event.rawX, event.rawY)) {
                val centerCoversTouch = event.rawY in centerScreenTop..centerScreenBottom
                val trackpadCentric = tpBounds.centerY() in centerScreenTop..centerScreenBottom
                if (!centerCoversTouch || trackpadCentric) {
                    if (isLeft) latchedLeftToTrackpad = true else latchedRightToTrackpad = true
                    return LightspeedBotTrackpadOverlay.dispatchArbiterTouch(isLeft, event)
                }
            }
            return false
        }

        if (currentlyLatched) {
            val handled = LightspeedBotTrackpadOverlay.dispatchArbiterTouch(isLeft, event)
            if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL) {
                if (isLeft) latchedLeftToTrackpad = false else latchedRightToTrackpad = false
            }
            return handled
        }

        return false
    }

    fun notifyGeometryChanged() {
        val svc = LightspeedAccessibilityService.instance ?: return
        svc.leftWingOverlayView?.post {
            svc.leftWingOverlayView?.updateMetricsDimensions()
            svc.leftWingOverlayView?.invalidate()
        }
        svc.overlayView?.post {
            if (svc.overlayView?.currentLayer == com.sbf.lightspeed.CruiseLayer.HIDDEN) {
                svc.overlayView?.updateMetricsDimensions()
                svc.overlayView?.invalidate()
            }
        }
    }
}
