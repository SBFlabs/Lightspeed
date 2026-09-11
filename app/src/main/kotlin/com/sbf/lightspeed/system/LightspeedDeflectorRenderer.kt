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
        useM3Color: Boolean = true
    ) {
        val d = density
        if (isReview) {
            drawReviewZone(canvas, topTouchBounds, Color.rgb(68, 138, 255), d)
            drawReviewZone(canvas, centerTouchBounds, m3Primary, d)
            drawReviewZone(canvas, bottomTouchBounds, Color.rgb(255, 171, 0), d)
            return
        }

        // =========================================================================
        // 1. CENTRAL PILL: Dedicated Morphing Glass Capsule Resting Flush on Screen Edge
        // =========================================================================
        if (isGlowEnabled) {
            val cy = centerTouchBounds.centerY()
            val zoneH = centerTouchBounds.height()
            val isTouched = isCurrentlyTouched && activeZoneIsCenter
            val morphFactor = if (isTouched) 1f else glowFraction.coerceIn(0f, 1f)

            // Resting geometry: sleek capsule flattened and resting toward the screen edge
            val restingW = minOf(centerTouchBounds.width(), 6f * d)
            val restingH = (zoneH * 0.40f).coerceIn(45f * d, 140f * d)

            // Morphing geometry: dynamically swells into the screen on touch / gesture
            val activeW = minOf(centerTouchBounds.width(), 26f * d)
            val activeH = (zoneH * 0.85f).coerceAtLeast(restingH)

            val pillW = restingW + (activeW - restingW) * morphFactor
            val pillH = restingH + (activeH - restingH) * morphFactor

            val restingAlpha = if (centerTransparency > 0) {
                (centerTransparency * 2.55f).toInt().coerceIn(0, 255)
            } else {
                (40 * 2.55f).toInt() // Aesthetic resting baseline glass presence
            }
            val finalAlpha = if (isTouched) 255 else maxOf(restingAlpha, (255 * glowFraction).toInt())

            if (finalAlpha > 0) {
                val pillTop = (cy - pillH / 2f).coerceAtLeast(centerTouchBounds.top + 2f * d)
                val pillBottom = (cy + pillH / 2f).coerceAtMost(centerTouchBounds.bottom - 2f * d)
                // Corner radius for inner corners facing toward screen center
                val cornerR = (pillW * 0.5f).coerceAtLeast(restingW * 0.5f).coerceAtMost((pillBottom - pillTop) / 2f)

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

                applyPillShading(isLeft, w, pillW, pillTop, pillBottom, finalAlpha, m3Primary, glowStyle, useM3Color, d)
                canvas.drawPath(bladePath, bladeFillPaint)
            }
        }

        // =========================================================================
        // 2. DEFLECTORS (Top & Bottom Flanks): Aerodynamic Edge Glow Only (Zero Pill / Zero Rim)
        // =========================================================================
        if (isCurrentlyTouched && (activeZoneIsTop || activeZoneIsBottom)) {
            val bounds = if (activeZoneIsTop) topTouchBounds else bottomTouchBounds
            val flankTop = bounds.top + 4f * d
            val flankBottom = bounds.bottom - 4f * d
            val flankCy = bounds.centerY()
            val glowDepth = minOf(bounds.width(), 32f * d)

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
                    else -> Triple(180, 215, 255)
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
        topLimit: Float,
        bottomLimit: Float,
        finalAlpha: Int,
        m3Primary: Int,
        glowStyle: String,
        useM3Color: Boolean,
        d: Float
    ) {
        val x0 = if (isLeft) 0f else w
        val x1 = if (isLeft) pillW else w - pillW

        val (baseR, baseG, baseB) = if (useM3Color) {
            Triple(Color.red(m3Primary), Color.green(m3Primary), Color.blue(m3Primary))
        } else {
            when (glowStyle) {
                "crimson_reactor" -> Triple(255, 36, 75)
                "cyber_plasma" -> Triple(0, 229, 255)
                else -> Triple(225, 240, 255) // Pristine crystalline frost
            }
        }

        when (glowStyle) {
            "material_shade" -> {
                bladeFillPaint.shader = LinearGradient(
                    x0, 0f, x1, 0f,
                    intArrayOf(
                        Color.argb(finalAlpha, baseR, baseG, baseB),
                        Color.argb((finalAlpha * 0.65f).toInt(), (baseR * 0.55f + 25).toInt().coerceIn(0, 255), (baseG * 0.55f + 25).toInt().coerceIn(0, 255), (baseB * 0.55f + 32).toInt().coerceIn(0, 255)),
                        Color.argb((finalAlpha * 0.25f).toInt(), 28, 30, 42),
                        Color.argb(0, 14, 16, 22)
                    ),
                    floatArrayOf(0.0f, 0.35f, 0.70f, 1.0f),
                    Shader.TileMode.CLAMP
                )
            }
            "crimson_reactor" -> {
                bladeFillPaint.shader = LinearGradient(
                    x0, 0f, x1, 0f,
                    intArrayOf(
                        Color.argb(finalAlpha, 255, 45, 80),
                        Color.argb((finalAlpha * 0.75f).toInt(), 255, 110, 30),
                        Color.argb((finalAlpha * 0.28f).toInt(), 180, 20, 45),
                        Color.argb(0, 100, 0, 20)
                    ),
                    floatArrayOf(0.0f, 0.30f, 0.70f, 1.0f),
                    Shader.TileMode.CLAMP
                )
            }
            "cyber_plasma" -> {
                bladeFillPaint.shader = LinearGradient(
                    x0, topLimit, x1, bottomLimit,
                    intArrayOf(
                        Color.argb(finalAlpha, 0, 229, 255),
                        Color.argb((finalAlpha * 0.75f).toInt(), baseR, baseG, baseB),
                        Color.argb((finalAlpha * 0.35f).toInt(), 255, 171, 0),
                        Color.argb(0, 255, 171, 0)
                    ),
                    floatArrayOf(0.0f, 0.40f, 0.75f, 1.0f),
                    Shader.TileMode.CLAMP
                )
            }
            else -> {
                // "progressive_frost" (Default) - Heavy progressive frosted glass diffusion with multi-stop blur curve
                bladeFillPaint.shader = LinearGradient(
                    x0, 0f, x1, 0f,
                    intArrayOf(
                        Color.argb(finalAlpha, baseR, baseG, baseB),
                        Color.argb((finalAlpha * 0.78f).toInt(), (baseR * 0.68f + 65).toInt().coerceIn(0, 255), (baseG * 0.68f + 70).toInt().coerceIn(0, 255), (baseB * 0.68f + 80).toInt().coerceIn(0, 255)),
                        Color.argb((finalAlpha * 0.40f).toInt(), (baseR * 0.35f + 40).toInt().coerceIn(0, 255), (baseG * 0.35f + 45).toInt().coerceIn(0, 255), (baseB * 0.35f + 60).toInt().coerceIn(0, 255)),
                        Color.argb((finalAlpha * 0.15f).toInt(), 255, 255, 255),
                        Color.argb(0, baseR, baseG, baseB)
                    ),
                    floatArrayOf(0.0f, 0.28f, 0.62f, 0.88f, 1.0f),
                    Shader.TileMode.CLAMP
                )
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
