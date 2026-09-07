package com.sbf.lightspeed.system

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Shader

/**
 * Unified Kinetic Deflector Renderer for Lightspeed Port & Starboard Wings.
 *
 * Renders an aerodynamic continuous curved blade silhouette with a pronounced central bell,
 * replacing disconnected rectangular blocks with heavy progressive frosted glass diffusion,
 * specular edge refraction, and configurable aesthetic finishes (Progressive Frost,
 * Material Surface Shade, Crimson Reactor, Cyber Plasma).
 */
object LightspeedDeflectorRenderer {

    private val bladeFillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }

    private val specularRimPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
    }

    private val reviewStrokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        color = Color.WHITE
    }

    private val reviewFillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }

    private val bladePath = Path()
    private val rimPath = Path()

    fun drawDeflectorWing(
        canvas: Canvas,
        isLeft: Boolean,
        density: Float,
        w: Float,
        h: Float,
        topTouchBounds: RectF,
        centerTouchBounds: RectF,
        bottomTouchBounds: RectF,
        isCurrentlyTouched: Boolean,
        activeZoneIsCenter: Boolean,
        activeZoneIsTop: Boolean,
        activeZoneIsBottom: Boolean,
        glowFraction: Float,
        centerTransparency: Int,
        topTransparency: Int,
        bottomTransparency: Int,
        isReview: Boolean,
        m3Primary: Int,
        glowStyle: String
    ) {
        val d = density
        if (isReview) {
            // Zone Review Mode for manual layout and size tuning in Central Command
            drawReviewZone(canvas, topTouchBounds, Color.rgb(68, 138, 255), d)
            drawReviewZone(canvas, centerTouchBounds, m3Primary, d)
            drawReviewZone(canvas, bottomTouchBounds, Color.rgb(255, 171, 0), d)
            return
        }

        val topLimit = topTouchBounds.top
        val centerTop = centerTouchBounds.top
        val centerBottom = centerTouchBounds.bottom
        val bottomLimit = bottomTouchBounds.bottom

        val maxRestingTransparency = maxOf(centerTransparency, topTransparency, bottomTransparency)
        val isAnyTouched = isCurrentlyTouched && (activeZoneIsCenter || activeZoneIsTop || activeZoneIsBottom)

        if (maxRestingTransparency <= 0 && !isAnyTouched && glowFraction <= 0f) {
            return
        }

        val restingAlpha = (maxRestingTransparency * 2.55f).toInt().coerceIn(0, 255)
        val glowAlpha = (255 * glowFraction).toInt()
        val finalAlpha = if (isAnyTouched) 255 else maxOf(restingAlpha, glowAlpha)

        val restingFlankW = minOf(topTouchBounds.width(), 4f * d)
        val restingCenterW = minOf(centerTouchBounds.width(), 6f * d)
        val glowFlankW = minOf(topTouchBounds.width(), 16f * d)
        val glowCenterW = minOf(centerTouchBounds.width(), 28f * d)

        val topFlankW = if (activeZoneIsTop) 12f * d else restingFlankW + (glowFlankW - restingFlankW) * glowFraction
        val bottomFlankW = if (activeZoneIsBottom) 12f * d else restingFlankW + (glowFlankW - restingFlankW) * glowFraction
        val effectiveCenterW = if (activeZoneIsCenter) 16f * d else restingCenterW + (glowCenterW - restingCenterW) * glowFraction

        val maxW = maxOf(effectiveCenterW, topFlankW, bottomFlankW)
        if (maxW <= 0f) return

        bladePath.reset()
        rimPath.reset()

        val cornerR = 4f * d
        val transitionH = 14f * d

        if (isLeft) {
            // Port Wing (Left Flank, Bezel at x = 0)
            val topY = topLimit + 2f * d
            val bottomY = bottomLimit - 2f * d

            bladePath.moveTo(0f, topY)
            bladePath.lineTo(0f, bottomY)
            bladePath.quadTo(0f, bottomY + cornerR, bottomFlankW * 0.5f, bottomY)

            rimPath.moveTo(bottomFlankW, bottomY - cornerR)
            bladePath.lineTo(bottomFlankW, bottomY - cornerR)

            val bottomCurveStart = (centerBottom + transitionH).coerceAtMost(bottomY - cornerR)
            val bottomCurveEnd = (centerBottom - transitionH * 0.5f).coerceAtLeast(centerTop + transitionH)

            bladePath.lineTo(bottomFlankW, bottomCurveStart)
            rimPath.lineTo(bottomFlankW, bottomCurveStart)

            // Smooth bezier expansion into thicker central bell
            bladePath.cubicTo(
                bottomFlankW, centerBottom + 4f * d,
                effectiveCenterW, centerBottom,
                effectiveCenterW, bottomCurveEnd
            )
            rimPath.cubicTo(
                bottomFlankW, centerBottom + 4f * d,
                effectiveCenterW, centerBottom,
                effectiveCenterW, bottomCurveEnd
            )

            val topCurveEnd = (centerTop + transitionH * 0.5f).coerceAtLeast(bottomCurveEnd)
            val topCurveStart = (centerTop - transitionH).coerceAtLeast(topY + cornerR)

            bladePath.lineTo(effectiveCenterW, topCurveEnd)
            rimPath.lineTo(effectiveCenterW, topCurveEnd)

            // Smooth bezier taper from central bell into upper flank
            bladePath.cubicTo(
                effectiveCenterW, centerTop,
                topFlankW, centerTop - 4f * d,
                topFlankW, topCurveStart
            )
            rimPath.cubicTo(
                effectiveCenterW, centerTop,
                topFlankW, centerTop - 4f * d,
                topFlankW, topCurveStart
            )

            bladePath.lineTo(topFlankW, topY + cornerR)
            rimPath.lineTo(topFlankW, topY + cornerR)

            bladePath.quadTo(0f, topY - cornerR, 0f, topY)
            bladePath.close()
        } else {
            // Starboard Wing (Right Flank, Bezel at x = w)
            val topY = topLimit + 2f * d
            val bottomY = bottomLimit - 2f * d

            bladePath.moveTo(w, topY)
            bladePath.lineTo(w, bottomY)
            bladePath.quadTo(w, bottomY + cornerR, w - bottomFlankW * 0.5f, bottomY)

            rimPath.moveTo(w - bottomFlankW, bottomY - cornerR)
            bladePath.lineTo(w - bottomFlankW, bottomY - cornerR)

            val bottomCurveStart = (centerBottom + transitionH).coerceAtMost(bottomY - cornerR)
            val bottomCurveEnd = (centerBottom - transitionH * 0.5f).coerceAtLeast(centerTop + transitionH)

            bladePath.lineTo(w - bottomFlankW, bottomCurveStart)
            rimPath.lineTo(w - bottomFlankW, bottomCurveStart)

            // Smooth bezier expansion into thicker central bell
            bladePath.cubicTo(
                w - bottomFlankW, centerBottom + 4f * d,
                w - effectiveCenterW, centerBottom,
                w - effectiveCenterW, bottomCurveEnd
            )
            rimPath.cubicTo(
                w - bottomFlankW, centerBottom + 4f * d,
                w - effectiveCenterW, centerBottom,
                w - effectiveCenterW, bottomCurveEnd
            )

            val topCurveEnd = (centerTop + transitionH * 0.5f).coerceAtLeast(bottomCurveEnd)
            val topCurveStart = (centerTop - transitionH).coerceAtLeast(topY + cornerR)

            bladePath.lineTo(w - effectiveCenterW, topCurveEnd)
            rimPath.lineTo(w - effectiveCenterW, topCurveEnd)

            // Smooth bezier taper from central bell into upper flank
            bladePath.cubicTo(
                w - effectiveCenterW, centerTop,
                w - topFlankW, centerTop - 4f * d,
                w - topFlankW, topCurveStart
            )
            rimPath.cubicTo(
                w - effectiveCenterW, centerTop,
                w - topFlankW, centerTop - 4f * d,
                w - topFlankW, topCurveStart
            )

            bladePath.lineTo(w - topFlankW, topY + cornerR)
            rimPath.lineTo(w - topFlankW, topY + cornerR)

            bladePath.quadTo(w, topY - cornerR, w, topY)
            bladePath.close()
        }

        // Apply progressive gradient and specular rim shading
        applyShading(isLeft, w, maxW, topLimit, bottomLimit, finalAlpha, m3Primary, glowStyle, d)

        canvas.drawPath(bladePath, bladeFillPaint)

        // Draw crisp luminous specular rim line along the curved contour
        specularRimPaint.strokeWidth = if (glowFraction > 0f) 1.8f * d else 1.2f * d
        canvas.drawPath(rimPath, specularRimPaint)
    }

    private fun applyShading(
        isLeft: Boolean,
        w: Float,
        maxW: Float,
        topLimit: Float,
        bottomLimit: Float,
        finalAlpha: Int,
        m3Primary: Int,
        glowStyle: String,
        d: Float
    ) {
        val x0 = if (isLeft) 0f else w
        val x1 = if (isLeft) maxW else w - maxW

        when (glowStyle) {
            "material_shade" -> {
                val pr = Color.red(m3Primary)
                val pg = Color.green(m3Primary)
                val pb = Color.blue(m3Primary)
                bladeFillPaint.shader = LinearGradient(
                    x0, 0f, x1, 0f,
                    intArrayOf(
                        Color.argb(finalAlpha, pr, pg, pb),
                        Color.argb((finalAlpha * 0.70f).toInt(), (pr * 0.45f + 25).toInt(), (pg * 0.45f + 25).toInt(), (pb * 0.45f + 32).toInt()),
                        Color.argb((finalAlpha * 0.25f).toInt(), 32, 28, 40),
                        Color.argb(0, 18, 16, 24)
                    ),
                    floatArrayOf(0.0f, 0.35f, 0.70f, 1.0f),
                    Shader.TileMode.CLAMP
                )
                specularRimPaint.color = Color.argb((finalAlpha * 0.95f).toInt(), pr, pg, pb)
            }
            "crimson_reactor" -> {
                bladeFillPaint.shader = LinearGradient(
                    x0, 0f, x1, 0f,
                    intArrayOf(
                        Color.argb(finalAlpha, 255, 36, 75),
                        Color.argb((finalAlpha * 0.75f).toInt(), 255, 95, 20),
                        Color.argb((finalAlpha * 0.25f).toInt(), 180, 20, 45),
                        Color.argb(0, 120, 0, 25)
                    ),
                    floatArrayOf(0.0f, 0.35f, 0.70f, 1.0f),
                    Shader.TileMode.CLAMP
                )
                specularRimPaint.color = Color.argb((finalAlpha * 0.95f).toInt(), 255, 225, 215)
            }
            "cyber_plasma" -> {
                bladeFillPaint.shader = LinearGradient(
                    x0, topLimit, x1, bottomLimit,
                    intArrayOf(
                        Color.argb(finalAlpha, 0, 229, 255),
                        Color.argb((finalAlpha * 0.75f).toInt(), Color.red(m3Primary), Color.green(m3Primary), Color.blue(m3Primary)),
                        Color.argb((finalAlpha * 0.40f).toInt(), 255, 171, 0),
                        Color.argb(0, 255, 171, 0)
                    ),
                    floatArrayOf(0.0f, 0.40f, 0.75f, 1.0f),
                    Shader.TileMode.CLAMP
                )
                specularRimPaint.color = Color.argb((finalAlpha * 0.95f).toInt(), 255, 255, 255)
            }
            else -> {
                // "progressive_frost" (Default) - Heavy progressive frosted glass diffusion
                bladeFillPaint.shader = LinearGradient(
                    x0, 0f, x1, 0f,
                    intArrayOf(
                        Color.argb(finalAlpha, 235, 245, 255),
                        Color.argb((finalAlpha * 0.72f).toInt(), 140, 195, 255),
                        Color.argb((finalAlpha * 0.28f).toInt(), 70, 150, 245),
                        Color.argb(0, 30, 100, 220)
                    ),
                    floatArrayOf(0.0f, 0.35f, 0.70f, 1.0f),
                    Shader.TileMode.CLAMP
                )
                specularRimPaint.color = Color.argb((finalAlpha * 0.98f).toInt(), 255, 255, 255)
            }
        }
    }

    private fun drawReviewZone(canvas: Canvas, bounds: RectF, color: Int, d: Float) {
        reviewFillPaint.color = Color.argb(120, Color.red(color), Color.green(color), Color.blue(color))
        canvas.drawRoundRect(bounds, 6f * d, 6f * d, reviewFillPaint)

        reviewStrokePaint.strokeWidth = 2f * d
        canvas.drawRoundRect(bounds, 6f * d, 6f * d, reviewStrokePaint)
    }
}
