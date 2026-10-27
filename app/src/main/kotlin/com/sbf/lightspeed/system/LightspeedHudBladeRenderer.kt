package com.sbf.lightspeed.system

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface

/**
 * Dedicated renderer for Style 3: Dynamic Edge Blade (Prism HUD).
 * Anchored alongside the active gesture swipe flank as a vertical energy ladder.
 */
object LightspeedHudBladeRenderer {

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

    private val ladderCardRect = RectF()
    private val rungRect = RectF()
    private val trackRect = RectF()
    private val fillRect = RectF()

    fun drawEdgeBlade(
        canvas: Canvas,
        title: String,
        value: String,
        stepIndex: Int,
        totalSteps: Int,
        cx: Float,
        cy: Float,
        primaryColor: Int,
        d: Float,
        isLeftFlank: Boolean,
        useCentralTheme: Boolean = true
    ) {
        val spec = LightspeedHudThemeHelper.resolveSpec(useCentralTheme, primaryColor, d)
        val hasGauge = totalSteps > 0 && stepIndex >= 0
        val cardW = 236f * d
        val cardH = 78f * d
        val chamfer = 8f * d
        val cardX = if (isLeftFlank) (18f * d) else (cx * 2f - cardW - (18f * d))
        ladderCardRect.set(cardX, cy - cardH / 2f, cardX + cardW, cy + cardH / 2f)
        val rect = ladderCardRect

        LightspeedHudRenderer.drawGlassCard(
            canvas, rect, chamfer, primaryColor, d,
            withSpecularShimmer = false,
            rimWidth = 1.3f,
            useCentralTheme = useCentralTheme
        )

        val tR = Color.red(spec.tintColor)
        val tG = Color.green(spec.tintColor)
        val tB = Color.blue(spec.tintColor)

        // 3. Stepped Energy Ladder along the outer blade edge
        if (hasGauge) {
            val ladderX = if (isLeftFlank) rect.left + (8f * d) else rect.right - (13f * d)
            val rungW = 5f * d
            if (totalSteps <= 16) {
                val rungH = 4.5f * d
                val rungGap = 4f * d
                val totalH = (totalSteps * rungH) + ((totalSteps - 1) * rungGap)
                val startY = cy - (totalH / 2f)

                for (i in 0 until totalSteps) {
                    val rY = startY + (totalSteps - 1 - i) * (rungH + rungGap)
                    rungRect.set(ladderX, rY, ladderX + rungW, rY + rungH)
                    if (i <= stepIndex) {
                        gaugeFillPaint.color = if (i == stepIndex) spec.tintColor else Color.argb(195, tR, tG, tB)
                        canvas.drawRoundRect(rungRect, 1.5f * d, 1.5f * d, gaugeFillPaint)
                    } else {
                        gaugeEmptyPaint.color = Color.argb(35, 255, 255, 255)
                        canvas.drawRoundRect(rungRect, 1.5f * d, 1.5f * d, gaugeEmptyPaint)
                    }
                }
            } else {
                val trackH = cardH - (24f * d)
                val trackY = cy - (trackH / 2f)
                trackRect.set(ladderX, trackY, ladderX + rungW, trackY + trackH)
                gaugeEmptyPaint.color = Color.argb(35, 255, 255, 255)
                canvas.drawRoundRect(trackRect, rungW / 2f, rungW / 2f, gaugeEmptyPaint)

                val fraction = (stepIndex.toFloat() / (totalSteps - 1).coerceAtLeast(1)).coerceIn(0f, 1f)
                val fillH = trackH * fraction
                if (fillH > 0f) {
                    val fillTop = trackY + trackH - fillH
                    fillRect.set(ladderX, fillTop, ladderX + rungW, trackY + trackH)
                    gaugeFillPaint.color = spec.tintColor
                    canvas.drawRoundRect(fillRect, rungW / 2f, rungW / 2f, gaugeFillPaint)

                    gaugeFillPaint.color = Color.WHITE
                    canvas.drawCircle(ladderX + rungW / 2f, fillTop, 3.5f * d, gaugeFillPaint)
                }
            }
        }

        // 4. Telemetry Text
        val textCx = if (isLeftFlank) rect.centerX() + (6f * d) else rect.centerX() - (6f * d)
        headerTextPaint.color = spec.tintColor
        headerTextPaint.textSize = 9.5f * d
        headerTextPaint.textAlign = Paint.Align.CENTER
        val maxTitleW = cardW - (32f * d)
        val safeTitle = if (headerTextPaint.measureText("✦ $title") > maxTitleW) {
            var t = title
            while (t.isNotEmpty() && headerTextPaint.measureText("✦ $t…") > maxTitleW) {
                t = t.dropLast(1)
            }
            "✦ $t…"
        } else {
            "✦ $title"
        }
        canvas.drawText(safeTitle, textCx, rect.top + (20f * d), headerTextPaint)

        valueTextPaint.textSize = 17f * d
        valueTextPaint.textAlign = Paint.Align.CENTER
        canvas.drawText("[ $value ]", textCx, rect.top + (42f * d), valueTextPaint)

        if (hasGauge) {
            subTextPaint.color = Color.argb(175, 210, 225, 245)
            subTextPaint.textSize = 8.5f * d
            subTextPaint.textAlign = Paint.Align.CENTER
            val label = if (totalSteps <= 24) "STEP ${stepIndex + 1} OF $totalSteps" else "LEVEL: $value"
            canvas.drawText(label, textCx, rect.top + (61f * d), subTextPaint)
        }
    }
}
