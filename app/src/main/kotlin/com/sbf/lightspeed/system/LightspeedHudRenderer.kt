package com.sbf.lightspeed.system

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Typeface
import kotlin.math.cos
import kotlin.math.sin

/**
 * Tactical Space Sci-Fi HUD Renderer for Lightspeed Cockpit & Overlays.
 * Renders high-tech telemetry HUDs with chamfered collimator frames,
 * 7-segment quantum bar gauges, orbital tachyon reticles, and laser guides.
 */
object LightspeedHudRenderer {

    private val bgFillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = Color.argb(238, 12, 16, 26) // Deep slate black
    }

    private val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
    }

    private val bracketPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.SQUARE
    }

    private val headerTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = Typeface.DEFAULT_BOLD
        textAlign = Paint.Align.CENTER
    }

    private val valueTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = Typeface.DEFAULT_BOLD
        textAlign = Paint.Align.CENTER
        color = Color.WHITE
    }

    private val subTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = Typeface.DEFAULT
        textAlign = Paint.Align.CENTER
    }

    private val gaugeFillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }

    private val gaugeEmptyPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
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
     * Style 1: Tactical Canopy Drop-Pod
     * Floats cleanly beneath the status bar cutout/icons with 45° chamfered visor & 7-segment quantum bar.
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
        val cardW = 310f * d
        val cardH = if (hasGauge) 82f * d else 62f * d
        val chamfer = 8f * d
        val rect = RectF(cx - cardW / 2f, topY, cx + cardW / 2f, topY + cardH)

        // 1. Chamfered Background & Laser Border
        val path = createChamferedPath(rect, chamfer)
        canvas.drawPath(path, bgFillPaint)

        borderPaint.strokeWidth = 1.5f * d
        borderPaint.color = primaryColor
        canvas.drawPath(path, borderPaint)

        // 2. Tactical Corner Tick Brackets
        val tickLen = 6f * d
        bracketPaint.strokeWidth = 2.5f * d
        bracketPaint.color = primaryColor

        // Top-left
        canvas.drawLine(rect.left + chamfer, rect.top, rect.left + chamfer + tickLen, rect.top, bracketPaint)
        canvas.drawLine(rect.left, rect.top + chamfer, rect.left, rect.top + chamfer + tickLen, bracketPaint)
        // Top-right
        canvas.drawLine(rect.right - chamfer, rect.top, rect.right - chamfer - tickLen, rect.top, bracketPaint)
        canvas.drawLine(rect.right, rect.top + chamfer, rect.right, rect.top + chamfer + tickLen, bracketPaint)
        // Bottom-left
        canvas.drawLine(rect.left + chamfer, rect.bottom, rect.left + chamfer + tickLen, rect.bottom, bracketPaint)
        canvas.drawLine(rect.left, rect.bottom - chamfer, rect.left, rect.bottom - chamfer - tickLen, bracketPaint)
        // Bottom-right
        canvas.drawLine(rect.right - chamfer, rect.bottom, rect.right - chamfer - tickLen, rect.bottom, bracketPaint)
        canvas.drawLine(rect.right, rect.bottom - chamfer, rect.right, rect.bottom - chamfer - tickLen, bracketPaint)

        // 3. Header Telemetry
        headerTextPaint.color = primaryColor
        headerTextPaint.textSize = 10.5f * d
        canvas.drawText("// $title", cx, topY + (17f * d), headerTextPaint)

        // 4. Value Readout
        valueTextPaint.textSize = 18f * d
        canvas.drawText("[ $value ]", cx, topY + (39f * d), valueTextPaint)

        // 5. 7-Segment Quantum Step Gauge
        if (hasGauge) {
            val segW = 28f * d
            val segH = 5f * d
            val segGap = 5f * d
            val totalGaugeW = (totalSteps * segW) + ((totalSteps - 1) * segGap)
            val startX = cx - (totalGaugeW / 2f)
            val gaugeY = topY + (50f * d)

            gaugeEmptyPaint.strokeWidth = 1f * d
            gaugeEmptyPaint.color = Color.argb(55, 255, 255, 255)

            for (i in 0 until totalSteps) {
                val segLeft = startX + (i * (segW + segGap))
                val segRect = RectF(segLeft, gaugeY, segLeft + segW, gaugeY + segH)
                if (i <= stepIndex) {
                    gaugeFillPaint.color = if (i == stepIndex) primaryColor else Color.argb(190, Color.red(primaryColor), Color.green(primaryColor), Color.blue(primaryColor))
                    canvas.drawRoundRect(segRect, 1.5f * d, 1.5f * d, gaugeFillPaint)
                } else {
                    canvas.drawRoundRect(segRect, 1.5f * d, 1.5f * d, gaugeEmptyPaint)
                }
            }

            // Sub-readout tier
            subTextPaint.color = Color.argb(175, 200, 225, 255)
            subTextPaint.textSize = 9f * d
            canvas.drawText("TIER ${stepIndex + 1} OF $totalSteps", cx, topY + (72f * d), subTextPaint)
        }
    }

    /**
     * Style 2: Holographic Cockpit Reticle
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
        val radius = 54f * d

        // 1. Translucent Reticle Plate
        bgFillPaint.color = Color.argb(235, 12, 16, 26)
        canvas.drawCircle(cx, cy, radius + (14f * d), bgFillPaint)

        borderPaint.strokeWidth = 1f * d
        borderPaint.color = Color.argb(60, Color.red(primaryColor), Color.green(primaryColor), Color.blue(primaryColor))
        canvas.drawCircle(cx, cy, radius + (14f * d), borderPaint)

        // 2. Orbital Arc & Step Ticks
        if (hasGauge && totalSteps > 1) {
            val startAngle = 140.0
            val sweep = 260.0
            val angleStep = sweep / (totalSteps - 1)

            val arcRect = RectF(cx - radius, cy - radius, cx + radius, cy + radius)
            borderPaint.strokeWidth = 1.5f * d
            borderPaint.color = Color.argb(80, 255, 255, 255)
            canvas.drawArc(arcRect, 140f, 260f, false, borderPaint)

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

                bracketPaint.strokeWidth = if (isCurrent) 3f * d else if (isPassed) 2f * d else 1.2f * d
                bracketPaint.color = if (isCurrent) primaryColor else if (isPassed) Color.argb(200, Color.red(primaryColor), Color.green(primaryColor), Color.blue(primaryColor)) else Color.argb(70, 255, 255, 255)
                canvas.drawLine(x1, y1, x2, y2, bracketPaint)
            }
        }

        // 3. Lateral Brackets
        val bW = radius + (18f * d)
        val bH = 16f * d
        bracketPaint.strokeWidth = 2f * d
        bracketPaint.color = primaryColor

        // Left bracket ⌜ ⌞
        canvas.drawLine(cx - bW, cy - bH, cx - bW + (8f * d), cy - bH, bracketPaint)
        canvas.drawLine(cx - bW, cy - bH, cx - bW, cy + bH, bracketPaint)
        canvas.drawLine(cx - bW, cy + bH, cx - bW + (8f * d), cy + bH, bracketPaint)

        // Right bracket ⌝ ⌟
        canvas.drawLine(cx + bW, cy - bH, cx + bW - (8f * d), cy - bH, bracketPaint)
        canvas.drawLine(cx + bW, cy - bH, cx + bW, cy + bH, bracketPaint)
        canvas.drawLine(cx + bW, cy + bH, cx + bW - (8f * d), cy + bH, bracketPaint)

        // 4. Center Telemetry
        headerTextPaint.color = primaryColor
        headerTextPaint.textSize = 9.5f * d
        canvas.drawText("// $title", cx, cy - (16f * d), headerTextPaint)

        valueTextPaint.textSize = 21f * d
        canvas.drawText(value, cx, cy + (7f * d), valueTextPaint)

        if (hasGauge) {
            subTextPaint.color = Color.argb(175, 200, 225, 255)
            subTextPaint.textSize = 8.5f * d
            canvas.drawText("TIER ${stepIndex + 1} / $totalSteps", cx, cy + (24f * d), subTextPaint)
        }
    }

    /**
     * Style 3: Dynamic Edge Blade
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
        val cardW = 230f * d
        val cardH = 74f * d
        val chamfer = 6f * d
        val cardX = if (isLeftFlank) (18f * d) else (cx * 2f - cardW - (18f * d))
        val rect = RectF(cardX, cy - cardH / 2f, cardX + cardW, cy + cardH / 2f)

        // 1. Chamfered Box
        val path = createChamferedPath(rect, chamfer)
        canvas.drawPath(path, bgFillPaint)

        borderPaint.strokeWidth = 1.5f * d
        borderPaint.color = primaryColor
        canvas.drawPath(path, borderPaint)

        // 2. Stepped Energy Ladder along the outer blade edge
        if (hasGauge) {
            val ladderX = if (isLeftFlank) rect.left + (8f * d) else rect.right - (12f * d)
            val rungH = 4.5f * d
            val rungW = 4f * d
            val rungGap = 4f * d
            val totalH = (totalSteps * rungH) + ((totalSteps - 1) * rungGap)
            val startY = cy - (totalH / 2f)

            for (i in 0 until totalSteps) {
                val rY = startY + (totalSteps - 1 - i) * (rungH + rungGap)
                val rungRect = RectF(ladderX, rY, ladderX + rungW, rY + rungH)
                if (i <= stepIndex) {
                    gaugeFillPaint.color = primaryColor
                    canvas.drawRoundRect(rungRect, 1f * d, 1f * d, gaugeFillPaint)
                } else {
                    gaugeEmptyPaint.strokeWidth = 1f * d
                    gaugeEmptyPaint.color = Color.argb(55, 255, 255, 255)
                    canvas.drawRoundRect(rungRect, 1f * d, 1f * d, gaugeEmptyPaint)
                }
            }
        }

        // 3. Telemetry Text
        val textCx = if (isLeftFlank) rect.centerX() + (6f * d) else rect.centerX() - (6f * d)
        headerTextPaint.color = primaryColor
        headerTextPaint.textSize = 9.5f * d
        canvas.drawText("// $title", textCx, rect.top + (18f * d), headerTextPaint)

        valueTextPaint.textSize = 17f * d
        canvas.drawText("[ $value ]", textCx, rect.top + (39f * d), valueTextPaint)

        if (hasGauge) {
            subTextPaint.color = Color.argb(175, 200, 225, 255)
            subTextPaint.textSize = 8.5f * d
            canvas.drawText("TIER ${stepIndex + 1} OF $totalSteps", textCx, rect.top + (57f * d), subTextPaint)
        }
    }
}
