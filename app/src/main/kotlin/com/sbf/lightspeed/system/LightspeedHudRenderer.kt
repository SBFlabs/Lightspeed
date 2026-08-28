package com.sbf.lightspeed.system

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import kotlin.math.cos
import kotlin.math.sin

/**
 * Material 3 Expressive Liquid Glass HUD Engine for Lightspeed Cockpit & Overlays.
 * Renders tactical telemetry with translucent liquid glass surfaces,
 * specular edge refraction, dynamic Monet palettes, and 7-segment quantum gauges.
 */
object LightspeedHudRenderer {

    private val glassFillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }

    private val glassRimPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
    }

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

    private val gaugeFillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }

    private val gaugeEmptyPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }

    private val gaugeEmptyStrokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
    }

    private fun createChamferedPath(rect: RectF, chamfer: Float): Path {
        val path = Path()
        path.moveTo(rect.left + chamfer, rect.top)
        path.lineTo(rect.right - chamfer, rect.top)
        path.lineTo(rect.right, rect.top + chamfer)
        path.lineTo(rect.right, rect.bottom - chamfer)
        path.lineTo(rect.right - chamfer, rect.bottom)
        path.lineTo(rect.left + chamfer, rect.bottom)
        path.lineTo(rect.left, rect.bottom - chamfer)
        path.lineTo(rect.left, rect.top + chamfer)
        path.close()
        return path
    }

    /**
     * Main dispatch method for rendering gesture scrubbing HUDs.
     */
    fun renderHud(
        canvas: Canvas,
        style: String, // "canopy_droppod", "cockpit_reticle", "edge_blade"
        title: String,
        value: String,
        stepIndex: Int,
        totalSteps: Int,
        centerX: Float,
        centerY: Float,
        topY: Float,
        primaryColor: Int,
        density: Float,
        isLeftFlank: Boolean = false
    ) {
        when (style) {
            "cockpit_reticle" -> drawCockpitReticle(
                canvas, title, value, stepIndex, totalSteps, centerX, centerY, primaryColor, density
            )
            "edge_blade" -> drawEdgeBlade(
                canvas, title, value, stepIndex, totalSteps, centerX, centerY, primaryColor, density, isLeftFlank
            )
            "canopy_droppod" -> drawCanopyDropPod(
                canvas, title, value, stepIndex, totalSteps, centerX, topY, primaryColor, density
            )
            else -> drawCanopyDropPod(
                canvas, title, value, stepIndex, totalSteps, centerX, topY, primaryColor, density
            )
        }
    }

    /**
     * Style 1: Tactical Canopy Drop-Pod (Liquid Glass)
     * Floats cleanly beneath the status bar cutout/icons with 45° chamfered glass visor & 7-segment quantum bar.
     */
    fun drawCanopyDropPod(
        canvas: Canvas,
        title: String,
        value: String,
        stepIndex: Int,
        totalSteps: Int,
        cx: Float,
        topY: Float,
        primaryColor: Int,
        d: Float
    ) {
        val hasGauge = totalSteps > 0 && stepIndex >= 0
        val cardW = 316f * d
        val cardH = if (hasGauge) 86f * d else 64f * d
        val chamfer = 10f * d
        val rect = RectF(cx - cardW / 2f, topY, cx + cardW / 2f, topY + cardH)

        // 1. Frosted Liquid Glass Backplane
        val r = Color.red(primaryColor)
        val g = Color.green(primaryColor)
        val b = Color.blue(primaryColor)

        val glassGrad = LinearGradient(
            rect.left, rect.top, rect.left, rect.bottom,
            intArrayOf(
                Color.argb(215, (16 + r * 0.08f).toInt().coerceIn(0, 255), (20 + g * 0.08f).toInt().coerceIn(0, 255), (32 + b * 0.08f).toInt().coerceIn(0, 255)),
                Color.argb(238, (10 + r * 0.04f).toInt().coerceIn(0, 255), (14 + g * 0.04f).toInt().coerceIn(0, 255), (24 + b * 0.04f).toInt().coerceIn(0, 255))
            ),
            floatArrayOf(0f, 1f),
            Shader.TileMode.CLAMP
        )
        glassFillPaint.shader = glassGrad
        val path = createChamferedPath(rect, chamfer)
        canvas.drawPath(path, glassFillPaint)
        glassFillPaint.shader = null

        // 2. Liquid Glass Refractive Rim
        val rimGrad = LinearGradient(
            rect.left, rect.top, rect.right, rect.bottom,
            intArrayOf(
                Color.argb(160, 255, 255, 255),
                Color.argb(90, r, g, b),
                Color.argb(35, 255, 255, 255),
                Color.argb(110, r, g, b)
            ),
            floatArrayOf(0f, 0.35f, 0.7f, 1f),
            Shader.TileMode.CLAMP
        )
        glassRimPaint.strokeWidth = 1.4f * d
        glassRimPaint.shader = rimGrad
        canvas.drawPath(path, glassRimPaint)
        glassRimPaint.shader = null

        // 3. Specular Light Shimmer across top chamfer
        val specGrad = LinearGradient(
            rect.left + chamfer, rect.top, rect.right - chamfer, rect.top,
            intArrayOf(
                Color.argb(10, 255, 255, 255),
                Color.argb(190, 255, 255, 255),
                Color.argb(10, 255, 255, 255)
            ),
            null,
            Shader.TileMode.CLAMP
        )
        specularPaint.strokeWidth = 1.6f * d
        specularPaint.shader = specGrad
        canvas.drawLine(rect.left + chamfer, rect.top + 0.8f * d, rect.right - chamfer, rect.top + 0.8f * d, specularPaint)
        specularPaint.shader = null

        // 4. Header Telemetry (Material 3 Dynamic Accent)
        headerTextPaint.color = primaryColor
        headerTextPaint.textSize = 10.5f * d
        canvas.drawText("✦ $title", cx, topY + (18f * d), headerTextPaint)

        // 5. Value Readout (Crisp White Glow)
        valueTextPaint.textSize = 19f * d
        canvas.drawText("[ $value ]", cx, topY + (41f * d), valueTextPaint)

        // 6. 7-Segment Liquid Glass Quantum Gauge
        if (hasGauge) {
            val segW = 28f * d
            val segH = 5.5f * d
            val segGap = 5.5f * d
            val totalGaugeW = (totalSteps * segW) + ((totalSteps - 1) * segGap)
            val startX = cx - (totalGaugeW / 2f)
            val gaugeY = topY + (52f * d)

            gaugeEmptyPaint.color = Color.argb(35, 255, 255, 255)
            gaugeEmptyStrokePaint.strokeWidth = 1f * d
            gaugeEmptyStrokePaint.color = Color.argb(45, 255, 255, 255)

            for (i in 0 until totalSteps) {
                val segLeft = startX + (i * (segW + segGap))
                val segRect = RectF(segLeft, gaugeY, segLeft + segW, gaugeY + segH)
                if (i <= stepIndex) {
                    val isPeak = (i == stepIndex)
                    gaugeFillPaint.color = if (isPeak) primaryColor else Color.argb(195, r, g, b)
                    canvas.drawRoundRect(segRect, 2.5f * d, 2.5f * d, gaugeFillPaint)

                    // Capsule Specular Glass Glint
                    specularPaint.strokeWidth = 1f * d
                    specularPaint.color = if (isPeak) Color.WHITE else Color.argb(160, 255, 255, 255)
                    canvas.drawLine(segRect.left + 3f * d, segRect.top + 1f * d, segRect.right - 3f * d, segRect.top + 1f * d, specularPaint)
                } else {
                    canvas.drawRoundRect(segRect, 2.5f * d, 2.5f * d, gaugeEmptyPaint)
                    canvas.drawRoundRect(segRect, 2.5f * d, 2.5f * d, gaugeEmptyStrokePaint)
                }
            }

            // Sub-readout tier
            subTextPaint.color = Color.argb(175, 210, 225, 245)
            subTextPaint.textSize = 9f * d
            canvas.drawText("STEP ${stepIndex + 1} OF $totalSteps", cx, topY + (75f * d), subTextPaint)
        }
    }

    /**
     * Style 2: Holographic Cockpit Reticle (Liquid Glass)
     * Projects in the focal cockpit area with tachyon circular orbital arc and collimator pips.
     */
    fun drawCockpitReticle(
        canvas: Canvas,
        title: String,
        value: String,
        stepIndex: Int,
        totalSteps: Int,
        cx: Float,
        cy: Float,
        primaryColor: Int,
        d: Float
    ) {
        val hasGauge = totalSteps > 0 && stepIndex >= 0
        val radius = 56f * d
        val r = Color.red(primaryColor)
        val g = Color.green(primaryColor)
        val b = Color.blue(primaryColor)

        // 1. Translucent Frosted Glass Reticle Plate
        val glassGrad = LinearGradient(
            cx - radius, cy - radius, cx + radius, cy + radius,
            intArrayOf(
                Color.argb(220, (18 + r * 0.08f).toInt().coerceIn(0, 255), (22 + g * 0.08f).toInt().coerceIn(0, 255), (34 + b * 0.08f).toInt().coerceIn(0, 255)),
                Color.argb(240, (10 + r * 0.04f).toInt().coerceIn(0, 255), (14 + g * 0.04f).toInt().coerceIn(0, 255), (24 + b * 0.04f).toInt().coerceIn(0, 255))
            ),
            null,
            Shader.TileMode.CLAMP
        )
        glassFillPaint.shader = glassGrad
        canvas.drawCircle(cx, cy, radius + (14f * d), glassFillPaint)
        glassFillPaint.shader = null

        // 2. Liquid Glass Outer Rim
        glassRimPaint.strokeWidth = 1.3f * d
        glassRimPaint.color = Color.argb(90, 255, 255, 255)
        canvas.drawCircle(cx, cy, radius + (14f * d), glassRimPaint)

        // 3. Orbital Arc & Step Ticks
        if (hasGauge && totalSteps > 1) {
            val startAngle = 140.0
            val sweep = 260.0
            val angleStep = sweep / (totalSteps - 1)

            val arcRect = RectF(cx - radius, cy - radius, cx + radius, cy + radius)
            glassRimPaint.strokeWidth = 1.5f * d
            glassRimPaint.color = Color.argb(60, 255, 255, 255)
            canvas.drawArc(arcRect, 140f, 260f, false, glassRimPaint)

            for (i in 0 until totalSteps) {
                val theta = Math.toRadians(startAngle + (i * angleStep))
                val isCurrent = (i == stepIndex)
                val isPassed = (i < stepIndex)
                val innerR = radius - (if (isCurrent) 8f * d else 4f * d)
                val outerR = radius + (if (isCurrent) 8f * d else 4f * d)

                val x1 = cx + (innerR * cos(theta)).toFloat()
                val y1 = cy + (innerR * sin(theta)).toFloat()
                val x2 = cx + (outerR * cos(theta)).toFloat()
                val y2 = cy + (outerR * sin(theta)).toFloat()

                specularPaint.strokeWidth = if (isCurrent) 3f * d else if (isPassed) 2f * d else 1.2f * d
                specularPaint.color = if (isCurrent) primaryColor else if (isPassed) Color.argb(200, r, g, b) else Color.argb(70, 255, 255, 255)
                canvas.drawLine(x1, y1, x2, y2, specularPaint)
            }
        }

        // 4. Center Telemetry
        headerTextPaint.color = primaryColor
        headerTextPaint.textSize = 10f * d
        canvas.drawText("✦ $title", cx, cy - (16f * d), headerTextPaint)

        valueTextPaint.textSize = 21f * d
        canvas.drawText(value, cx, cy + (7f * d), valueTextPaint)

        if (hasGauge) {
            subTextPaint.color = Color.argb(175, 210, 225, 245)
            subTextPaint.textSize = 8.5f * d
            canvas.drawText("STEP ${stepIndex + 1} / $totalSteps", cx, cy + (24f * d), subTextPaint)
        }
    }

    /**
     * Style 3: Dynamic Edge Blade (Liquid Glass)
     * Anchored alongside the active gesture swipe flank as a vertical energy ladder.
     */
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
        isLeftFlank: Boolean
    ) {
        val hasGauge = totalSteps > 0 && stepIndex >= 0
        val cardW = 236f * d
        val cardH = 78f * d
        val chamfer = 8f * d
        val cardX = if (isLeftFlank) (18f * d) else (cx * 2f - cardW - (18f * d))
        val rect = RectF(cardX, cy - cardH / 2f, cardX + cardW, cy + cardH / 2f)

        val r = Color.red(primaryColor)
        val g = Color.green(primaryColor)
        val b = Color.blue(primaryColor)

        // 1. Frosted Liquid Glass Box
        val glassGrad = LinearGradient(
            rect.left, rect.top, rect.right, rect.bottom,
            intArrayOf(
                Color.argb(215, (16 + r * 0.08f).toInt().coerceIn(0, 255), (20 + g * 0.08f).toInt().coerceIn(0, 255), (32 + b * 0.08f).toInt().coerceIn(0, 255)),
                Color.argb(238, (10 + r * 0.04f).toInt().coerceIn(0, 255), (14 + g * 0.04f).toInt().coerceIn(0, 255), (24 + b * 0.04f).toInt().coerceIn(0, 255))
            ),
            null,
            Shader.TileMode.CLAMP
        )
        glassFillPaint.shader = glassGrad
        val path = createChamferedPath(rect, chamfer)
        canvas.drawPath(path, glassFillPaint)
        glassFillPaint.shader = null

        // 2. Liquid Glass Rim
        glassRimPaint.strokeWidth = 1.3f * d
        glassRimPaint.color = Color.argb(100, 255, 255, 255)
        canvas.drawPath(path, glassRimPaint)

        // 3. Stepped Energy Ladder along the outer blade edge
        if (hasGauge) {
            val ladderX = if (isLeftFlank) rect.left + (8f * d) else rect.right - (13f * d)
            val rungH = 4.5f * d
            val rungW = 5f * d
            val rungGap = 4f * d
            val totalH = (totalSteps * rungH) + ((totalSteps - 1) * rungGap)
            val startY = cy - (totalH / 2f)

            for (i in 0 until totalSteps) {
                val rY = startY + (totalSteps - 1 - i) * (rungH + rungGap)
                val rungRect = RectF(ladderX, rY, ladderX + rungW, rY + rungH)
                if (i <= stepIndex) {
                    gaugeFillPaint.color = if (i == stepIndex) primaryColor else Color.argb(195, r, g, b)
                    canvas.drawRoundRect(rungRect, 1.5f * d, 1.5f * d, gaugeFillPaint)
                } else {
                    gaugeEmptyPaint.color = Color.argb(35, 255, 255, 255)
                    canvas.drawRoundRect(rungRect, 1.5f * d, 1.5f * d, gaugeEmptyPaint)
                }
            }
        }

        // 4. Telemetry Text
        val textCx = if (isLeftFlank) rect.centerX() + (6f * d) else rect.centerX() - (6f * d)
        headerTextPaint.color = primaryColor
        headerTextPaint.textSize = 9.5f * d
        canvas.drawText("✦ $title", textCx, rect.top + (20f * d), headerTextPaint)

        valueTextPaint.textSize = 17f * d
        canvas.drawText("[ $value ]", textCx, rect.top + (42f * d), valueTextPaint)

        if (hasGauge) {
            subTextPaint.color = Color.argb(175, 210, 225, 245)
            subTextPaint.textSize = 8.5f * d
            canvas.drawText("STEP ${stepIndex + 1} OF $totalSteps", textCx, rect.top + (61f * d), subTextPaint)
        }
    }
}
