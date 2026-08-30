package com.sbf.lightspeed.system

import android.content.Context
import android.graphics.Canvas
import android.graphics.RectF
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.view.MotionEvent
import android.view.View

/**
 * Interactive Liquid Glass Media Timeline Scrubber Floating HUD Overlay.
 *
 * Provides real-time touch timeline scrubbing across the active media session,
 * displaying track title, artist, live position / duration, and glowing seek pip with haptic feedback.
 */
class LightspeedMediaScrubberOverlay(
    context: Context,
    private val onDismissRequest: () -> Unit
) : View(context) {

    private val handler = Handler(Looper.getMainLooper())
    private var trackInfo = LightspeedMediaManager.getActiveTrackInfo(context)

    private var isSeeking = false
    private var seekFraction = -1f
    private var lastHapticStep = -1

    private val dismissRunnable = Runnable {
        onDismissRequest()
    }

    private val dynamicPrimary: Int
        get() {
            return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                context.getColor(android.R.color.system_accent1_300)
            } else {
                0xFF80D8FF.toInt()
            }
        }

    init {
        isClickable = true
        isFocusable = false
        resetDismissTimer()
    }

    fun updateTrackInfo() {
        trackInfo = LightspeedMediaManager.getActiveTrackInfo(context)
        resetDismissTimer()
        postInvalidate()
    }

    private fun resetDismissTimer(delayMs: Long = 3500L) {
        handler.removeCallbacks(dismissRunnable)
        handler.postDelayed(dismissRunnable, delayMs)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val density = resources.displayMetrics.density
        val cx = width / 2f
        val topY = (10f * density)

        LightspeedHudRenderer.drawMediaScrubberHud(
            canvas = canvas,
            title = trackInfo.title,
            artist = trackInfo.artist,
            positionMs = trackInfo.positionMs,
            durationMs = trackInfo.durationMs,
            cx = cx,
            topY = topY,
            primaryColor = dynamicPrimary,
            d = density,
            isSeeking = isSeeking,
            seekFraction = seekFraction
        )
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        val density = resources.displayMetrics.density
        val cx = width / 2f
        val topY = 10f * density
        val bounds = LightspeedHudRenderer.getMediaScrubberBarBounds(cx, topY, density)
        val touchPadBounds = RectF(bounds.left - 20f * density, bounds.top - 20f * density, bounds.right + 20f * density, bounds.bottom + 25f * density)

        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                if (touchPadBounds.contains(event.x, event.y)) {
                    isSeeking = true
                    updateSeekPosition(event.x, bounds)
                    resetDismissTimer(5000L)
                    return true
                }
            }
            MotionEvent.ACTION_MOVE -> {
                if (isSeeking) {
                    updateSeekPosition(event.x, bounds)
                    resetDismissTimer(5000L)
                    return true
                }
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                if (isSeeking) {
                    if (seekFraction >= 0f && trackInfo.durationMs > 0L) {
                        val targetMs = (seekFraction * trackInfo.durationMs).toLong()
                        LightspeedMediaManager.seekTo(context, targetMs)
                        LightspeedHapticEngine.click(context)
                    }
                    isSeeking = false
                    seekFraction = -1f
                    lastHapticStep = -1
                    postInvalidate()
                    resetDismissTimer(2500L)
                    return true
                }
            }
        }
        return super.onTouchEvent(event)
    }

    private fun updateSeekPosition(touchX: Float, bounds: RectF) {
        val barW = bounds.width().coerceAtLeast(1f)
        val fraction = ((touchX - bounds.left) / barW).coerceIn(0f, 1f)
        seekFraction = fraction

        if (trackInfo.durationMs > 0L) {
            val previewMs = (fraction * trackInfo.durationMs).toLong()
            val currentStep = (previewMs / 5000L).toInt() // 5-second haptic notches
            if (currentStep != lastHapticStep) {
                lastHapticStep = currentStep
                LightspeedHapticEngine.scrubTick(context)
            }
        }
        postInvalidate()
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        handler.removeCallbacks(dismissRunnable)
    }
}
