package com.sbf.lightspeed.system

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Shader

/**
 * Unified Kinetic Deflector & Central Pill Renderer.
 *
 * Renders a dedicated, aesthetically refined Central Pill anchored flush to the bezel
 * that dynamically morphs and expands on touch and movement, rendered with deep multi-stop
 * progressive frosted glass diffusion, luminous specular rim highlights, and optical core pip.
 *
 * Flank zones (Top & Bottom) have zero idle presence, appearing with subtle on-demand
 * aerodynamic edge guides strictly while actively touched.
 */
object LightspeedDeflectorRenderer {

    private val bladeFillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }

    private val reviewStrokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        color = Color.WHITE
    }

    private val reviewFillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }

    private val bladePath = Path()

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
        glowStyle: String,
        isGlowEnabled: Boolean = true,
        useM3Color: Boolean = true,
        touchY: Float? = null,
        visualWidthPx: Float = 0f
    ) {
        val d = density
        if (isReview) {
            drawReviewZone(canvas, topTouchBounds, Color.rgb(68, 138, 255), d)
            drawReviewZone(canvas, centerTouchBounds, m3Primary, d)
            drawReviewZone(canvas, bottomTouchBounds, Color.rgb(255, 171, 0), d)
            return
        }

        // =========================================================================
        // 1. CENTRAL PILL: Clean, Static Bezel-Anchored Pill (Zero Outlines, Zero White)
        // =========================================================================
        if (isGlowEnabled) {
            val isTouched = isCurrentlyTouched && activeZoneIsCenter

            // Static geometry: anchored flush against the bezel at configured dimensions
            val pillW = if (visualWidthPx > 0f) visualWidthPx else minOf(centerTouchBounds.width(), 6f * d)
            val pillTop = centerTouchBounds.top
            val pillBottom = centerTouchBounds.bottom

            val restingAlpha = if (centerTransparency > 0) {
                (centerTransparency * 2.55f).toInt().coerceIn(15, 255)
            } else {
                75 // Clean resting baseline presence
            }
            val activeAlpha = 210 // Clean responsive active glow
            val effectiveAlpha = if (isTouched) activeAlpha else maxOf(restingAlpha, (activeAlpha * glowFraction).toInt())

            if (effectiveAlpha > 0 && pillBottom > pillTop) {
                // Corner radius for inner corners facing toward screen center (outer corners flush at 0)
                val cornerR = (pillW * 0.5f).coerceAtMost((pillBottom - pillTop) / 2f)

                bladePath.reset()

                if (isLeft) {
                    val rect = RectF(0f, pillTop, pillW, pillBottom)
                    // Top-Left: 0, Top-Right: cornerR, Bottom-Right: cornerR, Bottom-Left: 0
                    val radii = floatArrayOf(
                        0f, 0f,
                        cornerR, cornerR,
                        cornerR, cornerR,
                        0f, 0f
                    )
                    bladePath.addRoundRect(rect, radii, Path.Direction.CW)
                } else {
                    val rect = RectF(w - pillW, pillTop, w, pillBottom)
                    // Top-Left: cornerR, Top-Right: 0, Bottom-Right: 0, Bottom-Left: cornerR
                    val radii = floatArrayOf(
                        cornerR, cornerR,
                        0f, 0f,
                        0f, 0f,
                        cornerR, cornerR
                    )
                    bladePath.addRoundRect(rect, radii, Path.Direction.CW)
                }

                // Core Clean Shading: pure M3 theme color, zero white, zero strokes
                applyPillShading(isLeft, w, pillW, effectiveAlpha, m3Primary, glowStyle, useM3Color)
                canvas.drawPath(bladePath, bladeFillPaint)
            }
        }

        // =========================================================================
        // 2. DEFLECTORS (Top & Bottom Flanks): Aerodynamic Edge Glow Only (Zero Pill / Zero Rim)
        // =========================================================================
        if (isGlowEnabled && isCurrentlyTouched && (activeZoneIsTop || activeZoneIsBottom)) {
            val bounds = if (activeZoneIsTop) topTouchBounds else bottomTouchBounds
            val flankTop = bounds.top + 4f * d
            val flankBottom = bounds.bottom - 4f * d
            val flankCy = bounds.centerY()
            val glowDepth = minOf(bounds.width(), 28f * d)

            bladePath.reset()
            if (isLeft) {
                bladePath.moveTo(0f, flankTop)
                bladePath.quadTo(glowDepth, flankCy, 0f, flankBottom)
                bladePath.close()
            } else {
                bladePath.moveTo(w, flankTop)
                bladePath.quadTo(w - glowDepth, flankCy, w, flankBottom)
                bladePath.close()
            }

            val x0 = if (isLeft) 0f else w
            val x1 = if (isLeft) glowDepth else w - glowDepth
            val (baseR, baseG, baseB) = if (useM3Color) {
                Triple(Color.red(m3Primary), Color.green(m3Primary), Color.blue(m3Primary))
            } else {
                when (glowStyle) {
                    "crimson_reactor" -> Triple(255, 45, 80)
                    "cyber_plasma" -> Triple(0, 229, 255)
                    else -> Triple(120, 160, 255)
                }
            }

            bladeFillPaint.shader = LinearGradient(
                x0, 0f, x1, 0f,
                intArrayOf(
                    Color.argb(190, baseR, baseG, baseB),
                    Color.argb(110, baseR, baseG, baseB),
                    Color.argb(35, baseR, baseG, baseB),
                    Color.argb(0, baseR, baseG, baseB)
                ),
                floatArrayOf(0.0f, 0.35f, 0.70f, 1.0f),
                Shader.TileMode.CLAMP
            )
            canvas.drawPath(bladePath, bladeFillPaint)
        }
    }

    private fun applyPillShading(
        isLeft: Boolean,
        w: Float,
        pillW: Float,
        effectiveAlpha: Int,
        m3Primary: Int,
        glowStyle: String,
        useM3Color: Boolean
    ) {
        val x0 = if (isLeft) 0f else w
        val x1 = if (isLeft) pillW else w - pillW

        val (baseR, baseG, baseB) = if (useM3Color) {
            Triple(Color.red(m3Primary), Color.green(m3Primary), Color.blue(m3Primary))
        } else {
            when (glowStyle) {
                "crimson_reactor" -> Triple(255, 45, 80)
                "cyber_plasma" -> Triple(0, 229, 255)
                else -> Triple(120, 160, 255)
            }
        }

        bladeFillPaint.shader = LinearGradient(
            x0, 0f, x1, 0f,
            intArrayOf(
                Color.argb(effectiveAlpha, baseR, baseG, baseB),
                Color.argb((effectiveAlpha * 0.70f).toInt(), baseR, baseG, baseB),
                Color.argb((effectiveAlpha * 0.35f).toInt(), baseR, baseG, baseB),
                Color.argb((effectiveAlpha * 0.08f).toInt(), baseR, baseG, baseB),
                Color.argb(0, baseR, baseG, baseB)
            ),
            floatArrayOf(0.0f, 0.30f, 0.65f, 0.90f, 1.0f),
            Shader.TileMode.CLAMP
        )
    }

    private fun drawReviewZone(canvas: Canvas, bounds: RectF, color: Int, d: Float) {
        reviewFillPaint.color = Color.argb(120, Color.red(color), Color.green(color), Color.blue(color))
        canvas.drawRoundRect(bounds, 6f * d, 6f * d, reviewFillPaint)

        reviewStrokePaint.strokeWidth = 2f * d
        canvas.drawRoundRect(bounds, 6f * d, 6f * d, reviewStrokePaint)
    }
}
