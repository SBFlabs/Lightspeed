package com.sbf.lightspeed

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.Shader
import android.graphics.Typeface
import kotlin.math.cos
import kotlin.math.pow
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
    private val sparkPath = Path()

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

    fun drawHyperdriveWarpSurge(
        canvas: Canvas,
        m3Primary: Int,
        density: Float,
        style: String = "photon_burst"
    ) {
        if (!isWarpLaunching) return
        val elapsed = (System.currentTimeMillis() - warpStartTime).toFloat()
        val p = (elapsed / 130f).coerceIn(0f, 1f)
        val ease = 1f - (1f - p) * (1f - p)
        val fade = (1f - p).pow(1.35f).coerceIn(0f, 1f)

        val cx = warpFocalPointX
        val cy = warpFocalPointY
        val pR = Color.red(m3Primary)
        val pG = Color.green(m3Primary)
        val pB = Color.blue(m3Primary)

        when (style) {
            "clean_fade" -> drawMinimalPulseSpark(canvas, cx, cy, ease, fade, pR, pG, pB, density)
            "nova_bloom" -> drawHaloRippleSpark(canvas, cx, cy, ease, fade, pR, pG, pB, density)
            "quantum_cross" -> drawOpticalFlareSpark(canvas, cx, cy, ease, fade, pR, pG, pB, density)
            "ion_embers" -> drawEmberDriftSpark(canvas, cx, cy, p, ease, fade, pR, pG, pB, density)
            else -> drawPhotonBurstSpark(canvas, cx, cy, ease, fade, pR, pG, pB, density)
        }
        elementPaint.shader = null
    }

    private fun drawSoftRadialGlow(
        canvas: Canvas,
        cx: Float,
        cy: Float,
        radius: Float,
        centerColor: Int,
        midColor: Int
    ) {
        if (radius <= 1f) return
        elementPaint.style = Paint.Style.FILL
        elementPaint.shader = RadialGradient(
            cx, cy, radius,
            intArrayOf(centerColor, midColor, Color.TRANSPARENT),
            floatArrayOf(0f, 0.45f, 1f),
            Shader.TileMode.CLAMP
        )
        canvas.drawCircle(cx, cy, radius, elementPaint)
        elementPaint.shader = null
    }

    private fun drawTaperedPetal(
        canvas: Canvas,
        cx: Float,
        cy: Float,
        angleRad: Double,
        innerR: Float,
        outerR: Float,
        halfWidth: Float,
        color: Int
    ) {
        val cosA = cos(angleRad).toFloat()
        val sinA = sin(angleRad).toFloat()
        val midR = (innerR + outerR) * 0.46f
        val xStart = cx + cosA * innerR
        val yStart = cy + sinA * innerR
        val xEnd = cx + cosA * outerR
        val yEnd = cy + sinA * outerR
        val px = -sinA * halfWidth
        val py = cosA * halfWidth
        val xMid = cx + cosA * midR
        val yMid = cy + sinA * midR

        sparkPath.reset()
        sparkPath.moveTo(xStart, yStart)
        sparkPath.lineTo(xMid + px, yMid + py)
        sparkPath.lineTo(xEnd, yEnd)
        sparkPath.lineTo(xMid - px, yMid - py)
        sparkPath.close()

        elementPaint.style = Paint.Style.FILL
        elementPaint.shader = LinearGradient(
            xStart, yStart, xEnd, yEnd,
            intArrayOf(Color.TRANSPARENT, color, Color.TRANSPARENT),
            floatArrayOf(0f, 0.38f, 1f),
            Shader.TileMode.CLAMP
        )
        canvas.drawPath(sparkPath, elementPaint)
        elementPaint.shader = null
    }

    private fun drawPhotonBurstSpark(
        canvas: Canvas,
        cx: Float,
        cy: Float,
        ease: Float,
        fade: Float,
        pR: Int,
        pG: Int,
        pB: Int,
        density: Float
    ) {
        val bloomR = (30f + ease * 40f) * density
        drawSoftRadialGlow(
            canvas, cx, cy, bloomR,
            Color.argb((165 * fade).toInt(), 255, 255, 255),
            Color.argb((95 * fade).toInt(), pR, pG, pB)
        )
        val innerR = (6f + ease * 10f) * density
        val outerR = (26f + ease * 36f) * density
        val halfW = (2.6f * fade + 0.6f) * density
        for (i in 0 until 6) {
            val angle = i * (Math.PI / 3.0) + 0.25
            val petalColor = if (i % 2 == 0) {
                Color.argb((175 * fade).toInt(), 255, 255, 255)
            } else {
                Color.argb((165 * fade).toInt(), pR, pG, pB)
            }
            drawTaperedPetal(canvas, cx, cy, angle, innerR, outerR, halfW, petalColor)
        }
    }

    private fun drawHaloRippleSpark(
        canvas: Canvas,
        cx: Float,
        cy: Float,
        ease: Float,
        fade: Float,
        pR: Int,
        pG: Int,
        pB: Int,
        density: Float
    ) {
        val outerR = (24f + ease * 52f) * density
        if (outerR <= 2f) return
        elementPaint.style = Paint.Style.FILL
        elementPaint.shader = RadialGradient(
            cx, cy, outerR,
            intArrayOf(
                Color.argb((75 * fade).toInt(), 255, 255, 255),
                Color.TRANSPARENT,
                Color.argb((130 * fade).toInt(), pR, pG, pB),
                Color.argb((75 * fade).toInt(), 56, 189, 248),
                Color.TRANSPARENT
            ),
            floatArrayOf(0f, 0.34f, 0.66f, 0.84f, 1f),
            Shader.TileMode.CLAMP
        )
        canvas.drawCircle(cx, cy, outerR, elementPaint)
        elementPaint.shader = null
    }

    private fun drawOpticalFlareSpark(
        canvas: Canvas,
        cx: Float,
        cy: Float,
        ease: Float,
        fade: Float,
        pR: Int,
        pG: Int,
        pB: Int,
        density: Float
    ) {
        val coreR = (18f + ease * 18f) * density
        drawSoftRadialGlow(
            canvas, cx, cy, coreR,
            Color.argb((185 * fade).toInt(), 255, 255, 255),
            Color.argb((90 * fade).toInt(), pR, pG, pB)
        )
        val majorLen = (28f + ease * 42f) * density
        val minorLen = (20f + ease * 28f) * density
        val majorW = (2.4f * fade + 0.5f) * density
        val minorW = (1.6f * fade + 0.4f) * density
        val majorCol = Color.argb((195 * fade).toInt(), 255, 255, 255)
        val minorCol = Color.argb((160 * fade).toInt(), pR, pG, pB)
        drawTaperedPetal(canvas, cx, cy, 0.0, -majorLen, majorLen, majorW, majorCol)
        drawTaperedPetal(canvas, cx, cy, Math.PI / 2.0, -minorLen, minorLen, minorW, minorCol)
    }

    private fun drawEmberDriftSpark(
        canvas: Canvas,
        cx: Float,
        cy: Float,
        p: Float,
        ease: Float,
        fade: Float,
        pR: Int,
        pG: Int,
        pB: Int,
        density: Float
    ) {
        val coreR = (24f + ease * 18f) * density
        drawSoftRadialGlow(
            canvas, cx, cy, coreR,
            Color.argb((110 * fade).toInt(), pR, pG, pB),
            Color.argb((45 * fade).toInt(), 56, 189, 248)
        )
        elementPaint.style = Paint.Style.FILL
        val emberCount = 8
        for (i in 0 until emberCount) {
            val baseAngle = (i * (2.0 * Math.PI / emberCount)) + (p * 0.45)
            val dist = (10f + ease * (32f + (i % 3) * 10f)) * density
            val ex = cx + (cos(baseAngle) * dist).toFloat()
            val ey = cy + (sin(baseAngle) * dist).toFloat()
            val er = ((2.4f - p * 0.9f) + (i % 2) * 0.6f).coerceAtLeast(0.8f) * density
            elementPaint.color = if (i % 2 == 0) {
                Color.argb((85 * fade).toInt(), pR, pG, pB)
            } else {
                Color.argb((85 * fade).toInt(), 56, 189, 248)
            }
            canvas.drawCircle(ex, ey, er * 2.1f, elementPaint)
            elementPaint.color = Color.argb((220 * fade).toInt(), 255, 255, 255)
            canvas.drawCircle(ex, ey, er * 0.8f, elementPaint)
        }
    }

    private fun drawMinimalPulseSpark(
        canvas: Canvas,
        cx: Float,
        cy: Float,
        ease: Float,
        fade: Float,
        pR: Int,
        pG: Int,
        pB: Int,
        density: Float
    ) {
        val pulseR = (26f + ease * 20f) * density
        drawSoftRadialGlow(
            canvas, cx, cy, pulseR,
            Color.argb((135 * fade).toInt(), 255, 255, 255),
            Color.argb((75 * fade).toInt(), pR, pG, pB)
        )
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
