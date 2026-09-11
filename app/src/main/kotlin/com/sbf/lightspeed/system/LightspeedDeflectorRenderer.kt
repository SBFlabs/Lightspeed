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

    private val causticPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
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
    private val causticPath = Path()

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
        touchY: Float? = null
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
            val isTouched = isCurrentlyTouched && activeZoneIsCenter
            val morphFactor = if (isTouched) 1f else glowFraction.coerceIn(0f, 1f)

            // Resting geometry: sleek capsule flattened and resting toward the screen edge
            val restingW = minOf(centerTouchBounds.width(), 6.5f * d)
            val restingH = (centerTouchBounds.height() * 0.38f).coerceIn(44f * d, 130f * d)

            // Morphing geometry: dynamically swells into the screen on touch / gesture
            val activeW = minOf(centerTouchBounds.width(), 32f * d).coerceAtLeast(24f * d)
            val activeH = (centerTouchBounds.height() * 0.75f).coerceAtLeast(restingH)

            val pillW = restingW + (activeW - restingW) * morphFactor
            val pillH = restingH + (activeH - restingH) * morphFactor

            val restingAlpha = if (centerTransparency > 0) {
                (centerTransparency * 2.55f).toInt().coerceIn(15, 100)
            } else {
                38 // Apple Liquid Glass resting opacity (~15%)
            }
            val activeAlpha = 82 // Apple Liquid Glass active opacity (~32% - crystal clear translucency!)
            val effectiveAlpha = if (isTouched) activeAlpha else maxOf(restingAlpha, (activeAlpha * glowFraction).toInt())

            if (effectiveAlpha > 0) {
                val cy = if (isTouched && touchY != null && touchY > 0f) {
                    val minY = if (h > centerTouchBounds.height() * 1.5f) (40f * d + pillH / 2f) else (centerTouchBounds.top + pillH / 2f)
                    val maxY = if (h > centerTouchBounds.height() * 1.5f) (h - 40f * d - pillH / 2f) else (centerTouchBounds.bottom - pillH / 2f)
                    touchY.coerceIn(minY, maxY)
                } else {
                    centerTouchBounds.centerY()
                }

                val pillTop = (cy - pillH / 2f).coerceAtLeast(2f * d)
                val pillBottom = (cy + pillH / 2f).coerceAtMost(h - 2f * d)
                // Corner radius for inner corners facing toward screen center (outer corners flush at 0)
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

                // 1. Core Apple Liquid Glass Translucency
                applyPillShading(isLeft, w, pillW, pillTop, pillBottom, effectiveAlpha, m3Primary, glowStyle, useM3Color, d)
                canvas.drawPath(bladePath, bladeFillPaint)

                // 2. Soft Optical Refraction Caustic (Dual-layer light lens)
                val causticInset = 1.0f * d
                val causticR = (cornerR - causticInset).coerceAtLeast(1f * d).coerceAtMost((pillBottom - pillTop - 2 * causticInset) / 2f)
                causticPath.reset()
                if (isLeft) {
                    causticPath.moveTo(0f, pillTop + causticInset)
                    causticPath.lineTo((pillW - cornerR).coerceAtLeast(0f), pillTop + causticInset)
                    causticPath.arcTo(RectF(pillW - 2 * causticR - causticInset, pillTop + causticInset, pillW - causticInset, pillTop + 2 * causticR + causticInset), -90f, 90f, false)
                    causticPath.lineTo(pillW - causticInset, pillBottom - cornerR)
                    causticPath.arcTo(RectF(pillW - 2 * causticR - causticInset, pillBottom - 2 * causticR - causticInset, pillW - causticInset, pillBottom - causticInset), 0f, 90f, false)
                    causticPath.lineTo(0f, pillBottom - causticInset)
                } else {
                    causticPath.moveTo(w, pillTop + causticInset)
                    causticPath.lineTo((w - pillW + cornerR).coerceAtMost(w), pillTop + causticInset)
                    causticPath.arcTo(RectF(w - pillW + causticInset, pillTop + causticInset, w - pillW + 2 * causticR + causticInset, pillTop + 2 * causticR + causticInset), -90f, -90f, false)
                    causticPath.lineTo(w - pillW + causticInset, pillBottom - cornerR)
                    causticPath.arcTo(RectF(w - pillW + causticInset, pillBottom - 2 * causticR - causticInset, w - pillW + 2 * causticR + causticInset, pillBottom - causticInset), 180f, -90f, false)
                    causticPath.lineTo(w, pillBottom - causticInset)
                }

                // Pass A: Diffuse Caustic Light Bloom (soft optical refraction scatter)
                causticPaint.strokeWidth = if (morphFactor > 0f) 3.5f * d else 2.0f * d
                causticPaint.color = Color.argb((effectiveAlpha * 0.28f).toInt(), 255, 255, 255)
                canvas.drawPath(causticPath, causticPaint)

                // Pass B: Sharp Specular Caustic Crest (inner curved boundary meniscus)
                causticPaint.strokeWidth = if (morphFactor > 0f) 1.2f * d else 0.9f * d
                causticPaint.color = Color.argb((effectiveAlpha * 0.80f).toInt(), 255, 255, 255)
                canvas.drawPath(causticPath, causticPaint)

                // Pass C: Subtle Chromatic Prism Dispersion Glint
                causticPaint.strokeWidth = 0.8f * d
                causticPaint.color = Color.argb((effectiveAlpha * 0.35f).toInt(), 180, 235, 255)
                canvas.drawPath(causticPath, causticPaint)

                // 3. Bezel Contact Grounding (Subtle bezel shadow giving tangible depth)
                val bezelX = if (isLeft) 0.5f * d else w - 0.5f * d
                causticPaint.strokeWidth = 1.0f * d
                causticPaint.color = Color.argb((effectiveAlpha * 0.35f).toInt(), 0, 0, 0)
                canvas.drawLine(bezelX, pillTop, bezelX, pillBottom, causticPaint)
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
        effectiveAlpha: Int,
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
                "crimson_reactor" -> Triple(255, 45, 80)
                "cyber_plasma" -> Triple(0, 229, 255)
                else -> Triple(180, 215, 255)
            }
        }

        when (glowStyle) {
            "crimson_reactor" -> {
                bladeFillPaint.shader = LinearGradient(
                    x0, 0f, x1, 0f,
                    intArrayOf(
                        Color.argb(effectiveAlpha, 255, 140, 160),
                        Color.argb((effectiveAlpha * 0.65f).toInt(), 255, 60, 90),
                        Color.argb((effectiveAlpha * 0.30f).toInt(), 180, 20, 45),
                        Color.argb((effectiveAlpha * 0.08f).toInt(), 120, 10, 30),
                        Color.argb(0, 80, 0, 20)
                    ),
                    floatArrayOf(0.0f, 0.20f, 0.55f, 0.82f, 1.0f),
                    Shader.TileMode.CLAMP
                )
            }
            "cyber_plasma" -> {
                bladeFillPaint.shader = LinearGradient(
                    x0, 0f, x1, 0f,
                    intArrayOf(
                        Color.argb(effectiveAlpha, 180, 245, 255),
                        Color.argb((effectiveAlpha * 0.65f).toInt(), 0, 229, 255),
                        Color.argb((effectiveAlpha * 0.30f).toInt(), 0, 150, 220),
                        Color.argb((effectiveAlpha * 0.08f).toInt(), 0, 80, 160),
                        Color.argb(0, 0, 40, 100)
                    ),
                    floatArrayOf(0.0f, 0.20f, 0.55f, 0.82f, 1.0f),
                    Shader.TileMode.CLAMP
                )
            }
            else -> {
                // "progressive_frost" (Default) - Apple Liquid Glass Crystalline Translucency
                bladeFillPaint.shader = LinearGradient(
                    x0, 0f, x1, 0f,
                    intArrayOf(
                        Color.argb(effectiveAlpha, 255, 255, 255), // Outer bezel reflection glint
                        Color.argb((effectiveAlpha * 0.60f).toInt(), 220, 235, 255), // Translucent ice sheen
                        Color.argb((effectiveAlpha * 0.32f).toInt(), (baseR * 0.4f + 25).toInt().coerceIn(0, 255), (baseG * 0.4f + 30).toInt().coerceIn(0, 255), (baseB * 0.4f + 45).toInt().coerceIn(0, 255)), // Liquid substrate
                        Color.argb((effectiveAlpha * 0.10f).toInt(), baseR, baseG, baseB), // Soft edge fade
                        Color.argb(0, baseR, baseG, baseB)
                    ),
                    floatArrayOf(0.0f, 0.22f, 0.58f, 0.85f, 1.0f),
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
