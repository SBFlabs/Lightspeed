package com.sbf.lightspeed

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.Shader
import android.graphics.Typeface
import kotlin.math.cos
import kotlin.math.sin

/**
 * Dedicated renderer for the holographic cockpit reactor core and hyperdrive warp surge animation.
 */
internal class LightspeedReactorRenderer {

    private val elementPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG)

    // Holographic Reactor Core — radial backplane shader cache
    private var reactorGradKey: Long = Long.MIN_VALUE
    private var reactorGradShader: RadialGradient? = null

    private fun reactorKey(cx: Float, cy: Float, radius: Float, isActive: Boolean, color: Int): Long =
        (cx.toBits().toLong() xor (cy.toBits().toLong() shl 16)) xor
        (radius.toBits().toLong() shl 8) xor color.toLong() xor (if (isActive) 1L else 0L)

    // Warp launch state — encapsulated with explicit control methods
    var isWarpLaunching: Boolean = false
        private set
    var warpStartTime: Long = 0L
        private set
    var warpFocalPointX: Float = 0f
        private set
    var warpFocalPointY: Float = 0f
        private set

    fun startWarp(focalX: Float, focalY: Float) {
        isWarpLaunching = true
        warpStartTime = System.currentTimeMillis()
        warpFocalPointX = focalX
        warpFocalPointY = focalY
    }

    fun cancelWarp() {
        isWarpLaunching = false
    }

    fun drawHyperdriveWarpSurge(canvas: Canvas, m3Primary: Int, density: Float) {
        if (!isWarpLaunching) return
        val elapsed = (System.currentTimeMillis() - warpStartTime).toFloat()
        val progress = (elapsed / 130f).coerceIn(0f, 1f)
        val easeProgress = progress * progress

        val cx = warpFocalPointX
        val cy = warpFocalPointY

        val shockwaveR = easeProgress * 340f * density
        val shockAlpha = ((1f - progress) * 255).toInt().coerceIn(0, 255)

        elementPaint.style = Paint.Style.STROKE
        elementPaint.strokeWidth = (4.0f * (1f - progress) + 1.0f) * density
        elementPaint.color = Color.argb(shockAlpha, Color.red(m3Primary), Color.green(m3Primary), Color.blue(m3Primary))
        canvas.drawCircle(cx, cy, shockwaveR, elementPaint)

        elementPaint.strokeWidth = 1.4f * density
        elementPaint.color = Color.argb((shockAlpha * 0.75f).toInt(), 0, 229, 255)
        canvas.drawCircle(cx, cy, shockwaveR * 0.84f, elementPaint)

        val lineCount = 36
        val angleStep = (2 * Math.PI) / lineCount
        elementPaint.style = Paint.Style.STROKE

        for (i in 0 until lineCount) {
            val angle = i * angleStep
            val rStart = (easeProgress * 35f * density) + (i % 4) * 8f * density
            val streakLength = (easeProgress * 230f * density) + (i % 3) * 35f * density
            val rEnd = rStart + streakLength

            val x1 = cx + (rStart * cos(angle)).toFloat()
            val y1 = cy + (rStart * sin(angle)).toFloat()
            val x2 = cx + (rEnd * cos(angle)).toFloat()
            val y2 = cy + (rEnd * sin(angle)).toFloat()

            val lineAlpha = ((1f - progress) * (180 + (i * 13) % 75)).toInt().coerceIn(0, 255)
            elementPaint.strokeWidth = if (i % 3 == 0) 2.4f * density else 1.2f * density
            elementPaint.color = when (i % 3) {
                0 -> Color.argb(lineAlpha, 255, 255, 255)
                1 -> Color.argb(lineAlpha, Color.red(m3Primary), Color.green(m3Primary), Color.blue(m3Primary))
                else -> Color.argb(lineAlpha, 0, 229, 255)
            }
            canvas.drawLine(x1, y1, x2, y2, elementPaint)
        }

        val flashRadius = (1f - progress) * 50f * density
        val flashAlpha = ((1f - progress) * 230).toInt().coerceIn(0, 255)
        elementPaint.style = Paint.Style.FILL
        elementPaint.color = Color.argb(flashAlpha, 255, 255, 255)
        canvas.drawCircle(cx, cy, flashRadius, elementPaint)
    }

    fun drawHolographicReactorCore(
        canvas: Canvas,
        cx: Float,
        cy: Float,
        coreRadius: Float,
        isActive: Boolean,
        m3Primary: Int,
        density: Float
    ) {
        elementPaint.style = Paint.Style.FILL
        // Cached RadialGradient — rebuilt only when geometry or active state changes
        val rKey = reactorKey(cx, cy, coreRadius, isActive, m3Primary)
        if (rKey != reactorGradKey) {
            reactorGradShader = RadialGradient(
                cx, cy, coreRadius,
                intArrayOf(
                    if (isActive) m3Primary else Color.argb(180, 35, 40, 55),
                    if (isActive) Color.argb(220, 20, 25, 38) else Color.argb(240, 12, 14, 20)
                ),
                floatArrayOf(0.0f, 1.0f),
                Shader.TileMode.CLAMP
            )
            reactorGradKey = rKey
        }
        elementPaint.shader = reactorGradShader
        canvas.drawCircle(cx, cy, coreRadius, elementPaint)
        elementPaint.shader = null

        elementPaint.style = Paint.Style.STROKE
        elementPaint.strokeWidth = if (isActive) 3f * density else 1.8f * density
        elementPaint.color = if (isActive) Color.WHITE else Color.argb(80, 200, 220, 255)
        canvas.drawCircle(cx, cy, coreRadius, elementPaint)

        val rotAngle = (System.currentTimeMillis() % 10000L) / 10000f * 360f
        val innerR = coreRadius * 0.68f
        elementPaint.strokeWidth = 1.5f * density
        elementPaint.color = if (isActive) Color.WHITE else Color.argb(120, 200, 220, 255)
        canvas.drawCircle(cx, cy, innerR, elementPaint)

        for (i in 0..3) {
            val a = Math.toRadians((rotAngle + i * 90.0))
            val nx1 = cx + (innerR - 6f * density) * cos(a).toFloat()
            val ny1 = cy + (innerR - 6f * density) * sin(a).toFloat()
            val nx2 = cx + (innerR + 6f * density) * cos(a).toFloat()
            val ny2 = cy + (innerR + 6f * density) * sin(a).toFloat()
            canvas.drawLine(nx1, ny1, nx2, ny2, elementPaint)
        }

        textPaint.textAlign = Paint.Align.CENTER
        textPaint.textSize = 10f * density
        textPaint.typeface = Typeface.DEFAULT_BOLD
        textPaint.color = Color.WHITE
        canvas.drawText("COCKPIT", cx, cy - 2f * density, textPaint)

        textPaint.textSize = 7.5f * density
        textPaint.color = if (isActive) Color.WHITE else Color.argb(160, 200, 220, 255)
        canvas.drawText("HANGAR", cx, cy + 10f * density, textPaint)
    }
}
