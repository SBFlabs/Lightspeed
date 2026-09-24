package com.sbf.lightspeed.system

import android.content.Context
import android.graphics.*
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

        drawMediaScrubberHud(
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
        val bounds = getMediaScrubberBarBounds(cx, topY, density)
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

    companion object {
        private val glassFillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
        }

        private val glassRimPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
        }

        private val specularPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeCap = Paint.Cap.ROUND
        }

        private val headerTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            typeface = Typeface.create("sans-serif-medium", Typeface.BOLD)
            textAlign = Paint.Align.CENTER
        }

        private val valueTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            typeface = Typeface.create("sans-serif-black", Typeface.BOLD)
            textAlign = Paint.Align.CENTER
            color = Color.WHITE
        }

        private val subTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            typeface = Typeface.create("sans-serif", Typeface.NORMAL)
            textAlign = Paint.Align.CENTER
        }

        private val gaugeFillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
        }

        private val gaugeEmptyPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
        }

        private val chamferedPath = Path()

        private var mediaGlassKey: Long = Long.MIN_VALUE
        private var mediaGlassShader: LinearGradient? = null
        private var mediaRimKey: Long = Long.MIN_VALUE
        private var mediaRimShader: LinearGradient? = null
        private var mediaSpecKey: Long = Long.MIN_VALUE
        private var mediaSpecShader: LinearGradient? = null
        private var mediaFillKey: Long = Long.MIN_VALUE
        private var mediaFillShader: LinearGradient? = null

        private inline fun Paint.withShader(tempShader: Shader?, block: (Paint) -> Unit) {
            val prev = this.shader
            this.shader = tempShader
            try {
                block(this)
            } finally {
                this.shader = prev
            }
        }

        private fun resetSharedPaints() {
            glassFillPaint.shader = null
            glassRimPaint.shader = null
            specularPaint.shader = null
            gaugeFillPaint.shader = null
        }

        private fun gradKey4f(x0: Float, y0: Float, x1: Float, y1: Float, color: Int): Long =
            (x0.toBits().toLong() xor (x1.toBits().toLong() shl 16)) xor
            (y0.toBits().toLong() xor (y1.toBits().toLong() shl 16)) xor color.toLong()

        private fun createChamferedPath(rect: RectF, chamfer: Float): Path {
            chamferedPath.rewind()
            chamferedPath.moveTo(rect.left + chamfer, rect.top)
            chamferedPath.lineTo(rect.right - chamfer, rect.top)
            chamferedPath.lineTo(rect.right, rect.top + chamfer)
            chamferedPath.lineTo(rect.right, rect.bottom - chamfer)
            chamferedPath.lineTo(rect.right - chamfer, rect.bottom)
            chamferedPath.lineTo(rect.left + chamfer, rect.bottom)
            chamferedPath.lineTo(rect.left, rect.bottom - chamfer)
            chamferedPath.lineTo(rect.left, rect.top + chamfer)
            chamferedPath.close()
            return chamferedPath
        }

        fun formatTime(ms: Long): String {
            val totalSeconds = (ms / 1000).coerceAtLeast(0)
            val minutes = totalSeconds / 60
            val seconds = totalSeconds % 60
            return String.format("%02d:%02d", minutes, seconds)
        }

        fun getMediaScrubberBarBounds(cx: Float, topY: Float, d: Float): RectF {
            val cardW = 330f * d
            val rectLeft = cx - cardW / 2f
            val rectRight = cx + cardW / 2f
            val barLeft = rectLeft + (16f * d)
            val barRight = rectRight - (16f * d)
            val barY = topY + (72f * d)
            val barH = 14f * d
            return RectF(barLeft, barY - (4f * d), barRight, barY + barH)
        }

        fun drawMediaScrubberHud(
            canvas: Canvas,
            title: String,
            artist: String,
            positionMs: Long,
            durationMs: Long,
            cx: Float,
            topY: Float,
            primaryColor: Int,
            d: Float,
            isSeeking: Boolean = false,
            seekFraction: Float = -1f
        ) {
            resetSharedPaints()
            val cardW = 330f * d
            val cardH = 96f * d
            val chamfer = 12f * d
            val rect = RectF(cx - cardW / 2f, topY, cx + cardW / 2f, topY + cardH)

            val r = Color.red(primaryColor)
            val g = Color.green(primaryColor)
            val b = Color.blue(primaryColor)

            // 1. Frosted Liquid Glass Backplane — cached shader
            val glassKey = gradKey4f(rect.left, rect.top, rect.left, rect.bottom, primaryColor)
            if (glassKey != mediaGlassKey) {
                mediaGlassShader = LinearGradient(
                    rect.left, rect.top, rect.left, rect.bottom,
                    intArrayOf(
                        Color.argb(225, (16 + r * 0.08f).toInt().coerceIn(0, 255), (20 + g * 0.08f).toInt().coerceIn(0, 255), (32 + b * 0.08f).toInt().coerceIn(0, 255)),
                        Color.argb(245, (10 + r * 0.04f).toInt().coerceIn(0, 255), (14 + g * 0.04f).toInt().coerceIn(0, 255), (24 + b * 0.04f).toInt().coerceIn(0, 255))
                    ),
                    floatArrayOf(0f, 1f),
                    Shader.TileMode.CLAMP
                )
                mediaGlassKey = glassKey
            }
            val path = createChamferedPath(rect, chamfer)
            glassFillPaint.withShader(mediaGlassShader) {
                canvas.drawPath(path, it)
            }

            // 2. Liquid Glass Refractive Rim — cached shader
            val rimKey = gradKey4f(rect.left, rect.top, rect.right, rect.bottom, primaryColor)
            if (rimKey != mediaRimKey) {
                mediaRimShader = LinearGradient(
                    rect.left, rect.top, rect.right, rect.bottom,
                    intArrayOf(
                        Color.argb(160, 255, 255, 255),
                        Color.argb(100, r, g, b),
                        Color.argb(35, 255, 255, 255),
                        Color.argb(120, r, g, b)
                    ),
                    floatArrayOf(0f, 0.35f, 0.7f, 1f),
                    Shader.TileMode.CLAMP
                )
                mediaRimKey = rimKey
            }
            glassRimPaint.strokeWidth = 1.4f * d
            glassRimPaint.withShader(mediaRimShader) {
                canvas.drawPath(path, it)
            }

            // 3. Specular Light Shimmer across top — cached shader
            val specKey = gradKey4f(rect.left + chamfer, rect.top, rect.right - chamfer, rect.top, 0xFFFFFF)
            if (specKey != mediaSpecKey) {
                mediaSpecShader = LinearGradient(
                    rect.left + chamfer, rect.top, rect.right - chamfer, rect.top,
                    intArrayOf(
                        Color.argb(10, 255, 255, 255),
                        Color.argb(200, 255, 255, 255),
                        Color.argb(10, 255, 255, 255)
                    ),
                    null,
                    Shader.TileMode.CLAMP
                )
                mediaSpecKey = specKey
            }
            specularPaint.strokeWidth = 1.6f * d
            specularPaint.withShader(mediaSpecShader) {
                canvas.drawLine(rect.left + chamfer, rect.top + 0.8f * d, rect.right - chamfer, rect.top + 0.8f * d, it)
            }

            // 4. Header Telemetry
            headerTextPaint.color = primaryColor
            headerTextPaint.textSize = 10f * d
            headerTextPaint.textAlign = Paint.Align.LEFT
            canvas.drawText("✦ MEDIA TIMELINE SCRUBBER", rect.left + (16f * d), topY + (19f * d), headerTextPaint)

            // 5. Time Readout (Top Right)
            val posText = if (isSeeking && seekFraction >= 0f) {
                val previewMs = (seekFraction * durationMs.coerceAtLeast(1L)).toLong()
                formatTime(previewMs)
            } else {
                formatTime(positionMs)
            }
            val durText = formatTime(durationMs)
            val timeDisplay = if (durationMs > 0L) "$posText / $durText" else posText
            subTextPaint.color = Color.argb(210, 255, 255, 255)
            subTextPaint.textSize = 10.5f * d
            subTextPaint.textAlign = Paint.Align.RIGHT
            canvas.drawText(timeDisplay, rect.right - (16f * d), topY + (19f * d), subTextPaint)

            // 6. Track Title & Artist
            valueTextPaint.textSize = 13.5f * d
            valueTextPaint.textAlign = Paint.Align.LEFT
            val cleanTitle = if (title.length > 28) title.take(26) + "…" else title
            canvas.drawText(cleanTitle, rect.left + (16f * d), topY + (41f * d), valueTextPaint)

            if (artist.isNotBlank()) {
                subTextPaint.color = Color.argb(180, (150 + r * 0.4f).toInt().coerceIn(0, 255), (180 + g * 0.3f).toInt().coerceIn(0, 255), 255)
                subTextPaint.textSize = 10.5f * d
                subTextPaint.textAlign = Paint.Align.LEFT
                val cleanArtist = if (artist.length > 34) artist.take(32) + "…" else artist
                canvas.drawText(cleanArtist, rect.left + (16f * d), topY + (56f * d), subTextPaint)
            }

            // 7. Interactive Quantum Seekbar Track
            val barLeft = rect.left + (16f * d)
            val barRight = rect.right - (16f * d)
            val barW = barRight - barLeft
            val barY = topY + (74f * d)
            val barH = 5f * d

            val fraction = when {
                seekFraction >= 0f -> seekFraction.coerceIn(0f, 1f)
                durationMs > 0L -> (positionMs.toFloat() / durationMs.toFloat()).coerceIn(0f, 1f)
                else -> 0f
            }

            // Track Background
            val trackRect = RectF(barLeft, barY, barRight, barY + barH)
            gaugeEmptyPaint.color = Color.argb(45, 255, 255, 255)
            canvas.drawRoundRect(trackRect, barH / 2f, barH / 2f, gaugeEmptyPaint)

            // Track Progress — cached shader (key encodes fillW + color)
            val progressW = barW * fraction
            if (progressW > 0f) {
                val progressRect = RectF(barLeft, barY, barLeft + progressW, barY + barH)
                val fillKey = gradKey4f(barLeft, barY, barLeft + progressW, barY, primaryColor)
                if (fillKey != mediaFillKey) {
                    mediaFillShader = LinearGradient(
                        barLeft, barY, barLeft + progressW, barY,
                        intArrayOf(Color.argb(200, r, g, b), primaryColor),
                        null,
                        Shader.TileMode.CLAMP
                    )
                    mediaFillKey = fillKey
                }
                gaugeFillPaint.withShader(mediaFillShader) {
                    canvas.drawRoundRect(progressRect, barH / 2f, barH / 2f, it)
                }
            }

            // Scrubber Thumb Pip
            val thumbX = (barLeft + progressW).coerceIn(barLeft, barRight)
            val thumbY = barY + (barH / 2f)

            if (isSeeking) {
                gaugeEmptyPaint.color = Color.argb(80, r, g, b)
                canvas.drawCircle(thumbX, thumbY, 11f * d, gaugeEmptyPaint)
            }

            specularPaint.color = primaryColor
            specularPaint.strokeWidth = 2f * d
            canvas.drawCircle(thumbX, thumbY, 7f * d, specularPaint)

            gaugeFillPaint.color = Color.WHITE
            canvas.drawCircle(thumbX, thumbY, 5f * d, gaugeFillPaint)

            headerTextPaint.textAlign = Paint.Align.CENTER
            valueTextPaint.textAlign = Paint.Align.CENTER
            subTextPaint.textAlign = Paint.Align.CENTER
        }
    }
}
