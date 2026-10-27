package com.sbf.lightspeed.system

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface

/**
 * Material 3 Expressive Prism HUD Engine for Lightspeed Cockpit & Overlays.
 * Upgraded with the 12-theme optical glass engine for tactile scrubbing telemetry.
 */
object LightspeedHudRenderer {

    private val glassFillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val glassRimPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val glowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
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
    private val gaugeFillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val gaugeEmptyPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val gaugeEmptyStrokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }

    private val chamferedPath = Path()
    private val canopyCardRect = RectF()
    private val segRect = RectF()
    private val gaugeTrackRect = RectF()
    private val gaugeFillRect = RectF()

    private var cardGlassKey: Long = Long.MIN_VALUE
    private var cardGlassShader: LinearGradient? = null
    private var cardRimKey: Long = Long.MIN_VALUE
    private var cardRimShader: LinearGradient? = null
    private var cardSpecKey: Long = Long.MIN_VALUE
    private var cardSpecShader: LinearGradient? = null

    private inline fun Paint.withShader(tempShader: Shader?, block: (Paint) -> Unit) {
        val prev = this.shader
        this.shader = tempShader
        try { block(this) } finally { this.shader = prev }
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

    private fun resetSharedPaints() {
        glassFillPaint.shader = null
        glassRimPaint.shader = null
        glowPaint.shader = null
        specularPaint.shader = null
        gaugeFillPaint.shader = null
        gaugeEmptyPaint.shader = null
        gaugeEmptyStrokePaint.shader = null
    }

    fun drawGlassCard(
        canvas: Canvas,
        rect: RectF,
        chamfer: Float,
        primaryColor: Int,
        d: Float,
        withSpecularShimmer: Boolean = true,
        rimWidth: Float = 1.4f,
        useCentralTheme: Boolean = true
    ): Path {
        val spec = LightspeedHudThemeHelper.resolveSpec(useCentralTheme, primaryColor, d)
        val finalChamfer = if (useCentralTheme) spec.cornerRadiusPx else chamfer
        val path = createChamferedPath(rect, finalChamfer)

        // Outer Glow (Central Command Engine)
        if (spec.glowStrength > 0.05f) {
            val glowColor = spec.glowColor
            glowPaint.color = Color.argb(
                (spec.glowStrength * 120).toInt().coerceIn(0, 255),
                Color.red(glowColor),
                Color.green(glowColor),
                Color.blue(glowColor)
            )
            glowPaint.strokeWidth = spec.borderWidthPx * (1.5f + spec.glowStrength * 1.5f)
            canvas.drawPath(path, glowPaint)
        }

        val pR = Color.red(spec.panelColor)
        val pG = Color.green(spec.panelColor)
        val pB = Color.blue(spec.panelColor)
        val tR = Color.red(spec.tintColor)
        val tG = Color.green(spec.tintColor)
        val tB = Color.blue(spec.tintColor)
        val pA = (spec.panelAlpha * 255).toInt().coerceIn(210, 245)

        // 1. Solid Frosted Backplane (Base fill guarantees 100% legibility & zero transparency)
        glassFillPaint.shader = null
        glassFillPaint.color = Color.argb(pA, (pR + tR * 0.08f).toInt().coerceIn(0, 255), (pG + tG * 0.08f).toInt().coerceIn(0, 255), (pB + tB * 0.08f).toInt().coerceIn(0, 255))
        canvas.drawPath(path, glassFillPaint)

        // 1b. Optical Tint Gradient Infusion
        val glassKey = gradKey4f(rect.left, rect.top, rect.left, rect.bottom, spec.panelColor) xor spec.tintColor.toLong()
        if (glassKey != cardGlassKey) {
            cardGlassShader = LinearGradient(
                rect.left, rect.top, rect.left, rect.bottom,
                intArrayOf(
                    Color.argb(pA, (pR + tR * 0.12f).toInt().coerceIn(0, 255), (pG + tG * 0.12f).toInt().coerceIn(0, 255), (pB + tB * 0.12f).toInt().coerceIn(0, 255)),
                    Color.argb((pA * 1.05f).toInt().coerceIn(0, 255), pR, pG, pB)
                ),
                floatArrayOf(0f, 1f),
                Shader.TileMode.CLAMP
            )
            cardGlassKey = glassKey
        }
        glassFillPaint.withShader(cardGlassShader) { p ->
            canvas.drawPath(path, p)
        }

        // 2. Refractive Rim
        val bColor = spec.borderColor
        val rimKey = gradKey4f(rect.left, rect.top, rect.right, rect.bottom, bColor) xor spec.tintColor.toLong()
        if (rimKey != cardRimKey) {
            val bA = Color.alpha(bColor).coerceAtLeast(100)
            cardRimShader = LinearGradient(
                rect.left, rect.top, rect.right, rect.bottom,
                intArrayOf(
                    Color.argb(160, 255, 255, 255),
                    Color.argb(bA, Color.red(bColor), Color.green(bColor), Color.blue(bColor)),
                    Color.argb(35, 255, 255, 255),
                    Color.argb(bA, tR, tG, tB)
                ),
                floatArrayOf(0f, 0.35f, 0.7f, 1f),
                Shader.TileMode.CLAMP
            )
            cardRimKey = rimKey
        }
        glassRimPaint.strokeWidth = if (useCentralTheme) spec.borderWidthPx else (rimWidth * d)
        glassRimPaint.withShader(cardRimShader) { p ->
            canvas.drawPath(path, p)
        }

        // 3. Specular Shimmer across top chamfer
        if (withSpecularShimmer) {
            val specKey = gradKey4f(rect.left + finalChamfer, rect.top, rect.right - finalChamfer, rect.top, 0xFFFFFF)
            if (specKey != cardSpecKey) {
                cardSpecShader = LinearGradient(
                    rect.left + finalChamfer, rect.top, rect.right - finalChamfer, rect.top,
                    intArrayOf(
                        Color.argb(10, 255, 255, 255),
                        Color.argb((spec.highlightStrength * 220).toInt().coerceIn(0, 255), 255, 255, 255),
                        Color.argb(10, 255, 255, 255)
                    ),
                    null,
                    Shader.TileMode.CLAMP
                )
                cardSpecKey = specKey
            }
            specularPaint.strokeWidth = 1.6f * d
            specularPaint.withShader(cardSpecShader) { p ->
                canvas.drawLine(rect.left + finalChamfer, rect.top + 0.8f * d, rect.right - finalChamfer, rect.top + 0.8f * d, p)
            }
        }
        return path
    }

    fun renderHud(
        canvas: Canvas,
        style: String,
        title: String,
        value: String,
        stepIndex: Int,
        totalSteps: Int,
        centerX: Float,
        centerY: Float,
        topY: Float,
        primaryColor: Int,
        density: Float,
        isLeftFlank: Boolean = false,
        useCentralTheme: Boolean = true
    ) {
        headerTextPaint.textAlign = Paint.Align.CENTER
        valueTextPaint.textAlign = Paint.Align.CENTER
        subTextPaint.textAlign = Paint.Align.CENTER

        when (style) {
            "cockpit_reticle" -> LightspeedHudReticleRenderer.drawCockpitReticle(
                canvas, title, value, stepIndex, totalSteps, centerX, centerY, primaryColor, density, useCentralTheme
            )
            "edge_blade" -> LightspeedHudBladeRenderer.drawEdgeBlade(
                canvas, title, value, stepIndex, totalSteps, centerX, centerY, primaryColor, density, isLeftFlank, useCentralTheme
            )
            "quantum_horizon" -> LightspeedHudHorizonRenderer.drawQuantumHorizon(
                canvas, title, value, stepIndex, totalSteps, centerX, topY, primaryColor, density, useCentralTheme
            )
            "tachyon_dial" -> LightspeedHudDialRenderer.drawTachyonDial(
                canvas, title, value, stepIndex, totalSteps, centerX, centerY, primaryColor, density, useCentralTheme
            )
            else -> drawCanopyDropPod(
                canvas, title, value, stepIndex, totalSteps, centerX, topY, primaryColor, density, useCentralTheme
            )
        }
    }

    fun drawCanopyDropPod(
        canvas: Canvas,
        title: String,
        value: String,
        stepIndex: Int,
        totalSteps: Int,
        cx: Float,
        topY: Float,
        primaryColor: Int,
        d: Float,
        useCentralTheme: Boolean = true
    ) {
        val spec = LightspeedHudThemeHelper.resolveSpec(useCentralTheme, primaryColor, d)
        val hasGauge = totalSteps > 0 && stepIndex >= 0
        val cardW = 316f * d
        val cardH = if (hasGauge) 86f * d else 64f * d
        val chamfer = 10f * d
        canopyCardRect.set(cx - cardW / 2f, topY, cx + cardW / 2f, topY + cardH)
        val rect = canopyCardRect
        resetSharedPaints()
        drawGlassCard(canvas, rect, chamfer, primaryColor, d, withSpecularShimmer = true, useCentralTheme = useCentralTheme)

        val tR = Color.red(spec.tintColor)
        val tG = Color.green(spec.tintColor)
        val tB = Color.blue(spec.tintColor)

        // 4. Header Telemetry
        headerTextPaint.color = spec.tintColor
        headerTextPaint.textSize = 10.5f * d
        headerTextPaint.textAlign = Paint.Align.CENTER
        val maxTitleW = cardW - (36f * d)
        val safeTitle = if (headerTextPaint.measureText("✦ $title") > maxTitleW) {
            var t = title
            while (t.isNotEmpty() && headerTextPaint.measureText("✦ $t…") > maxTitleW) {
                t = t.dropLast(1)
            }
            "✦ $t…"
        } else {
            "✦ $title"
        }
        canvas.drawText(safeTitle, cx, topY + (18f * d), headerTextPaint)

        // 5. Value Readout
        valueTextPaint.textSize = 19f * d
        valueTextPaint.textAlign = Paint.Align.CENTER
        canvas.drawText("[ $value ]", cx, topY + (41f * d), valueTextPaint)

        // 6. Gauge
        if (hasGauge) {
            val maxGaugeW = cardW - (36f * d)
            val gaugeY = topY + (52f * d)

            if (totalSteps <= 16) {
                val segGap = (4.5f * d).coerceAtMost(maxGaugeW / (totalSteps * 3f))
                val segW = ((maxGaugeW - ((totalSteps - 1) * segGap)) / totalSteps).coerceIn(4f * d, 26f * d)
                val totalGaugeW = (totalSteps * segW) + ((totalSteps - 1) * segGap)
                val startX = cx - (totalGaugeW / 2f)
                val segH = 5.5f * d

                gaugeEmptyPaint.color = Color.argb(35, 255, 255, 255)
                gaugeEmptyStrokePaint.strokeWidth = 1f * d
                gaugeEmptyStrokePaint.color = Color.argb(45, 255, 255, 255)

                for (i in 0 until totalSteps) {
                    val segLeft = startX + (i * (segW + segGap))
                    segRect.set(segLeft, gaugeY, segLeft + segW, gaugeY + segH)
                    if (i <= stepIndex) {
                        val isPeak = (i == stepIndex)
                        gaugeFillPaint.color = if (isPeak) spec.tintColor else Color.argb(195, tR, tG, tB)
                        canvas.drawRoundRect(segRect, 2.5f * d, 2.5f * d, gaugeFillPaint)
                    } else {
                        canvas.drawRoundRect(segRect, 2.5f * d, 2.5f * d, gaugeEmptyPaint)
                        canvas.drawRoundRect(segRect, 2.5f * d, 2.5f * d, gaugeEmptyStrokePaint)
                    }
                }
            } else {
                val trackH = 6f * d
                gaugeTrackRect.set(cx - maxGaugeW / 2f, gaugeY, cx + maxGaugeW / 2f, gaugeY + trackH)
                gaugeEmptyPaint.color = Color.argb(35, 255, 255, 255)
                canvas.drawRoundRect(gaugeTrackRect, 3f * d, 3f * d, gaugeEmptyPaint)

                val fraction = (stepIndex.toFloat() / (totalSteps - 1).coerceAtLeast(1)).coerceIn(0f, 1f)
                val fillW = maxGaugeW * fraction
                if (fillW > 0f) {
                    gaugeFillRect.set(cx - maxGaugeW / 2f, gaugeY, (cx - maxGaugeW / 2f) + fillW, gaugeY + trackH)
                    gaugeFillPaint.color = spec.tintColor
                    canvas.drawRoundRect(gaugeFillRect, 3f * d, 3f * d, gaugeFillPaint)
                }
            }
        }
    }

    fun formatTime(ms: Long): String = LightspeedMediaScrubberOverlay.formatTime(ms)
    fun getMediaScrubberBarBounds(cx: Float, topY: Float, d: Float): RectF =
        LightspeedMediaScrubberOverlay.getMediaScrubberBarBounds(cx, topY, d)
    fun drawMediaScrubberHud(
        canvas: Canvas, title: String, artist: String, positionMs: Long, durationMs: Long,
        cx: Float, topY: Float, primaryColor: Int, d: Float, isSeeking: Boolean = false, seekFraction: Float = -1f
    ) {
        LightspeedMediaScrubberOverlay.drawMediaScrubberHud(
            canvas, title, artist, positionMs, durationMs, cx, topY, primaryColor, d, isSeeking, seekFraction
        )
    }
}
