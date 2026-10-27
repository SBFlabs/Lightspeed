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
    private val pillRect = RectF()
    private val auraRect = RectF()

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
            LightspeedDeflectorPillDraw.drawPill(
                canvas = canvas,
                isLeft = isLeft,
                d = d,
                w = w,
                centerTouchBounds = centerTouchBounds,
                isTouched = isTouched,
                glowFraction = glowFraction,
                centerTransparency = centerTransparency,
                m3Primary = m3Primary,
                glowStyle = glowStyle,
                useM3Color = useM3Color,
                pillStyle = pillStyle,
                touchY = touchY,
                visualWidthPx = visualWidthPx,
                bladePath = bladePath,
                bladeFillPaint = bladeFillPaint,
                reviewStrokePaint = reviewStrokePaint,
                pillRect = pillRect,
                auraRect = auraRect,
                applyPillShading = ::applyPillShading
            )
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
