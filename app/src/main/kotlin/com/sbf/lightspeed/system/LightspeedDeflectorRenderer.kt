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

    // Cached Shaders — rebuilt only when geometry/color inputs change
    private var bladeGlowKey: Long = Long.MIN_VALUE
    private var cachedBladeGlowShader: LinearGradient? = null

    private var pillShadingKey: Long = Long.MIN_VALUE
    private var cachedPillShadingShader: LinearGradient? = null

    private fun bladeGlowKey(x0: Float, x1: Float, r: Int, g: Int, b: Int): Long =
        (x0.toBits().toLong() xor (x1.toBits().toLong() shl 16)) xor
        ((r.toLong() shl 16) or (g.toLong() shl 8) or b.toLong())

    private fun pillKey(x0: Float, x1: Float, alpha: Int, r: Int, g: Int, b: Int): Long =
        (x0.toBits().toLong() xor (x1.toBits().toLong() shl 16)) xor
        ((alpha.toLong() shl 24) or (r.toLong() shl 16) or (g.toLong() shl 8) or b.toLong())

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
        pillStyle: String = "anchored_glow",
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
        // 1. CENTRAL PILL
        // =========================================================================
        if (isGlowEnabled) {
            val isTouched = isCurrentlyTouched && activeZoneIsCenter

            // Base dimensions
            val baseW = if (visualWidthPx > 0f) visualWidthPx else minOf(centerTouchBounds.width(), 6f * d)
            val pillTop = centerTouchBounds.top
            val pillBottom = centerTouchBounds.bottom
            val pillH = pillBottom - pillTop

            val restingAlpha = (centerTransparency * 2.55f).toInt().coerceIn(0, 255)
            val activeAlpha = 210
            val effectiveAlpha = if (isTouched) activeAlpha else maxOf(restingAlpha, (activeAlpha * glowFraction).toInt())

            if (effectiveAlpha > 0 && pillH > 0) {
                bladePath.reset()
                
                val (baseR, baseG, baseB) = if (useM3Color) {
                    Triple(Color.red(m3Primary), Color.green(m3Primary), Color.blue(m3Primary))
                } else {
                    when (glowStyle) {
                        "crimson_reactor" -> Triple(255, 45, 80)
                        "cyber_plasma" -> Triple(0, 229, 255)
                        else -> Triple(120, 160, 255)
                    }
                }
                
                when (pillStyle) {
                    "anchored_glow" -> {
                        val pillW = baseW
                        val cornerR = (pillW * 0.5f).coerceAtMost(pillH / 2f)
                        if (isLeft) {
                            val rect = RectF(0f, pillTop, pillW, pillBottom)
                            val radii = floatArrayOf(0f, 0f, cornerR, cornerR, cornerR, cornerR, 0f, 0f)
                            bladePath.addRoundRect(rect, radii, Path.Direction.CW)
                        } else {
                            val rect = RectF(w - pillW, pillTop, w, pillBottom)
                            val radii = floatArrayOf(cornerR, cornerR, 0f, 0f, 0f, 0f, cornerR, cornerR)
                            bladePath.addRoundRect(rect, radii, Path.Direction.CW)
                        }
                        applyPillShading(isLeft, w, pillW, effectiveAlpha, m3Primary, glowStyle, useM3Color)
                        canvas.drawPath(bladePath, bladeFillPaint)
                    }
                    
                    "floating_smart_pill" -> {
                        val pillW = maxOf(baseW, 4f * d)
                        val floatGap = 3f * d
                        val cornerR = pillW / 2f
                        
                        val rect = if (isLeft) {
                            RectF(floatGap, pillTop, floatGap + pillW, pillBottom)
                        } else {
                            RectF(w - floatGap - pillW, pillTop, w - floatGap, pillBottom)
                        }
                        
                        // Solid semi-transparent fill
                        bladeFillPaint.shader = null
                        val fillAlpha = (effectiveAlpha * 0.45f).toInt().coerceIn(0, 255)
                        bladeFillPaint.color = Color.argb(fillAlpha, baseR, baseG, baseB)
                        canvas.drawRoundRect(rect, cornerR, cornerR, bladeFillPaint)
                    }
                    
                    "neon_core" -> {
                        val coreW = 1.5f * d
                        val floatGap = 2f * d
                        val coreTop = pillTop + pillH * 0.1f
                        val coreBottom = pillBottom - pillH * 0.1f
                        
                        val rect = if (isLeft) {
                            RectF(floatGap, coreTop, floatGap + coreW, coreBottom)
                        } else {
                            RectF(w - floatGap - coreW, coreTop, w - floatGap, coreBottom)
                        }
                        
                        // Aura
                        val auraW = 8f * d
                        val auraRect = if (isLeft) {
                            RectF(0f, pillTop, auraW, pillBottom)
                        } else {
                            RectF(w - auraW, pillTop, w, pillBottom)
                        }
                        applyPillShading(isLeft, w, auraW, (effectiveAlpha * 0.5f).toInt(), m3Primary, glowStyle, useM3Color)
                        
                        // Draw Aura
                        if (isLeft) {
                            val radii = floatArrayOf(0f, 0f, auraW, auraW, auraW, auraW, 0f, 0f)
                            bladePath.addRoundRect(auraRect, radii, Path.Direction.CW)
                        } else {
                            val radii = floatArrayOf(auraW, auraW, 0f, 0f, 0f, 0f, auraW, auraW)
                            bladePath.addRoundRect(auraRect, radii, Path.Direction.CW)
                        }
                        canvas.drawPath(bladePath, bladeFillPaint)
                        
                        // Draw Solid Core
                        bladeFillPaint.shader = null
                        bladeFillPaint.color = Color.WHITE
                        canvas.drawRoundRect(rect, coreW/2f, coreW/2f, bladeFillPaint)
                    }
                    
                    "razor_edge" -> {
                        val pillW = 1.2f * d
                        val floatGap = 1.5f * d
                        val cornerR = pillW / 2f
                        
                        val rect = if (isLeft) {
                            RectF(floatGap, pillTop, floatGap + pillW, pillBottom)
                        } else {
                            RectF(w - floatGap - pillW, pillTop, w - floatGap, pillBottom)
                        }
                        
                        bladeFillPaint.shader = null
                        val fillAlpha = (effectiveAlpha * 0.85f).toInt().coerceIn(0, 255)
                        bladeFillPaint.color = Color.argb(fillAlpha, baseR, baseG, baseB)
                        canvas.drawRoundRect(rect, cornerR, cornerR, bladeFillPaint)
                    }
                    
                    "kinetic_elastic" -> {
                        val pillW = maxOf(baseW, 3f * d)
                        val floatGap = 3f * d
                        val cornerR = pillW / 2f
                        
                        val rect = if (isLeft) {
                            RectF(floatGap, pillTop, floatGap + pillW, pillBottom)
                        } else {
                            RectF(w - floatGap - pillW, pillTop, w - floatGap, pillBottom)
                        }
                        
                        bladeFillPaint.shader = null
                        val fillAlpha = (effectiveAlpha * 0.6f).toInt().coerceIn(0, 255)
                        bladeFillPaint.color = Color.argb(fillAlpha, baseR, baseG, baseB)
                        
                        if (isTouched && touchY != null) {
                            // Bulge towards the touch point
                            val bulgeAmount = 6f * d
                            
                            val tY = touchY.coerceIn(pillTop, pillBottom)
                            val ctrlOffset = 30f * d
                            
                            if (isLeft) {
                                bladePath.moveTo(floatGap, pillTop)
                                bladePath.lineTo(floatGap + pillW, pillTop)
                                bladePath.cubicTo(
                                    floatGap + pillW, tY - ctrlOffset,
                                    floatGap + pillW + bulgeAmount, tY,
                                    floatGap + pillW, tY + ctrlOffset
                                )
                                bladePath.lineTo(floatGap + pillW, pillBottom)
                                bladePath.lineTo(floatGap, pillBottom)
                                bladePath.close()
                            } else {
                                bladePath.moveTo(w - floatGap, pillTop)
                                bladePath.lineTo(w - floatGap - pillW, pillTop)
                                bladePath.cubicTo(
                                    w - floatGap - pillW, tY - ctrlOffset,
                                    w - floatGap - pillW - bulgeAmount, tY,
                                    w - floatGap - pillW, tY + ctrlOffset
                                )
                                bladePath.lineTo(w - floatGap - pillW, pillBottom)
                                bladePath.lineTo(w - floatGap, pillBottom)
                                bladePath.close()
                            }
                            canvas.drawPath(bladePath, bladeFillPaint)
                        } else {
                            canvas.drawRoundRect(rect, cornerR, cornerR, bladeFillPaint)
                        }
                    }
                    
                    "hollow_ghost" -> {
                        val pillW = maxOf(baseW, 4f * d)
                        val floatGap = 3f * d
                        val cornerR = pillW / 2f
                        
                        val rect = if (isLeft) {
                            RectF(floatGap, pillTop, floatGap + pillW, pillBottom)
                        } else {
                            RectF(w - floatGap - pillW, pillTop, w - floatGap, pillBottom)
                        }
                        
                        reviewStrokePaint.color = Color.argb(effectiveAlpha, baseR, baseG, baseB)
                        reviewStrokePaint.strokeWidth = 1.2f * d
                        canvas.drawRoundRect(rect, cornerR, cornerR, reviewStrokePaint)
                        reviewStrokePaint.color = Color.WHITE // reset
                    }
                }
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

            val gKey = bladeGlowKey(x0, x1, baseR, baseG, baseB)
            if (gKey != bladeGlowKey) {
                cachedBladeGlowShader = LinearGradient(
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
                bladeGlowKey = gKey
            }
            bladeFillPaint.shader = cachedBladeGlowShader
            canvas.drawPath(bladePath, bladeFillPaint)
            bladeFillPaint.shader = null
        }
        bladeFillPaint.shader = null
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

        val pKey = pillKey(x0, x1, effectiveAlpha, baseR, baseG, baseB)
        if (pKey != pillShadingKey) {
            cachedPillShadingShader = LinearGradient(
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
            pillShadingKey = pKey
        }
        bladeFillPaint.shader = cachedPillShadingShader
    }

    private fun drawReviewZone(canvas: Canvas, bounds: RectF, color: Int, d: Float) {
        reviewFillPaint.color = Color.argb(120, Color.red(color), Color.green(color), Color.blue(color))
        canvas.drawRoundRect(bounds, 6f * d, 6f * d, reviewFillPaint)

        reviewStrokePaint.strokeWidth = 2f * d
        canvas.drawRoundRect(bounds, 6f * d, 6f * d, reviewStrokePaint)
    }
}
