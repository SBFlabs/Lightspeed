package com.sbf.lightspeed.system

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import kotlin.math.cos
import kotlin.math.sin

/**
 * Dedicated renderer for the Tachyon Dial tactical HUD style.
 * High-tech circular scanner with 360° azimuth degree hashes, concentric range rings,
 * sweeping orbital energy arc, target acquisition crosshairs, and digital telemetry.
 */
object LightspeedHudDialRenderer {

    private val glassFillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }

    private val glassRimPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
    }

    private val glowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
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

    private val gaugeEmptyStrokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
    }

    private val gaugeFillStrokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
    }

    // Tachyon Dial — radial backplane shader cache
    private var tachyonDialKey: Long = Long.MIN_VALUE
    private var tachyonDialShader: RadialGradient? = null

    private inline fun Paint.withShader(tempShader: Shader?, block: (Paint) -> Unit) {
        val prev = this.shader
        this.shader = tempShader
        try {
            block(this)
        } finally {
            this.shader = prev
        }
    }

    private fun gradKey2f(x0: Float, y0: Float, color: Int): Long =
        (x0.toBits().toLong() and 0xFFFFFFFFL) or
        (y0.toBits().toLong() shl 32) xor color.toLong()

    fun drawTachyonDial(
        canvas: Canvas,
        title: String,
        value: String,
        stepIndex: Int,
        totalSteps: Int,
        cx: Float,
        cy: Float,
        primaryColor: Int,
        d: Float,
        useCentralTheme: Boolean = true
    ) {
        val spec = LightspeedHudThemeHelper.resolveSpec(useCentralTheme, primaryColor, d)
        val hasGauge = totalSteps > 0 && stepIndex >= 0
        val radius = 64f * d
        glassFillPaint.shader = null
        glassRimPaint.shader = null
        specularPaint.shader = null
        gaugeFillPaint.shader = null

        val pR = Color.red(spec.panelColor)
        val pG = Color.green(spec.panelColor)
        val pB = Color.blue(spec.panelColor)
        val tR = Color.red(spec.tintColor)
        val tG = Color.green(spec.tintColor)
        val tB = Color.blue(spec.tintColor)
        val pA = (spec.panelAlpha * 255).toInt().coerceIn(210, 245)
        val r = Color.red(primaryColor)
        val g = Color.green(primaryColor)
        val b = Color.blue(primaryColor)

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
            canvas.drawCircle(cx, cy, radius, glowPaint)
        }

        // 1. Solid Frosted Base (Guarantees zero transparency)
        glassFillPaint.shader = null
        glassFillPaint.color = Color.argb(pA, (pR + tR * 0.08f).toInt().coerceIn(0, 255), (pG + tG * 0.08f).toInt().coerceIn(0, 255), (pB + tB * 0.08f).toInt().coerceIn(0, 255))
        canvas.drawCircle(cx, cy, radius, glassFillPaint)

        // 1b. Frosted Backplane — cached RadialGradient
        val dialKey = gradKey2f(cx, cy, spec.panelColor) xor (spec.tintColor.toLong() shl 16) xor java.lang.Float.floatToIntBits(radius).toLong()
        if (dialKey != tachyonDialKey) {
            tachyonDialShader = RadialGradient(
                cx, cy, radius,
                intArrayOf(
                    Color.argb(pA, (pR + tR * 0.12f).toInt().coerceIn(0, 255), (pG + tG * 0.12f).toInt().coerceIn(0, 255), (pB + tB * 0.12f).toInt().coerceIn(0, 255)),
                    Color.argb((pA * 1.05f).toInt().coerceIn(0, 255), pR, pG, pB)
                ),
                floatArrayOf(0f, 1f),
                Shader.TileMode.CLAMP
            )
            tachyonDialKey = dialKey
        }
        glassFillPaint.withShader(tachyonDialShader) { p ->
            canvas.drawCircle(cx, cy, radius, p)
        }

        // 2. Concentric Radar Range Rings
        specularPaint.style = Paint.Style.STROKE
        specularPaint.strokeWidth = 1.2f * d
        specularPaint.color = Color.argb((spec.highlightStrength * 60).toInt().coerceIn(0, 255), 255, 255, 255)
        canvas.drawCircle(cx, cy, radius * 0.45f, specularPaint)
        canvas.drawCircle(cx, cy, radius * 0.72f, specularPaint)

        // Outer Refractive Rim
        glassRimPaint.strokeWidth = spec.borderWidthPx
        glassRimPaint.color = spec.borderColor
        canvas.drawCircle(cx, cy, radius, glassRimPaint)

        // 3. Azimuth Degree Graduation Marks (12 ticks around 360°)
        for (i in 0 until 12) {
            val angleRad = Math.toRadians((i * 30.0) - 90.0)
            val isCardinal = (i % 3 == 0)
            val tickInner = if (isCardinal) radius - (9f * d) else radius - (5f * d)
            val tickOuter = radius - (2f * d)

            val x1 = cx + (tickInner * cos(angleRad)).toFloat()
            val y1 = cy + (tickInner * sin(angleRad)).toFloat()
            val x2 = cx + (tickOuter * cos(angleRad)).toFloat()
            val y2 = cy + (tickOuter * sin(angleRad)).toFloat()

            specularPaint.strokeWidth = if (isCardinal) 1.6f * d else 1.0f * d
            specularPaint.color = if (isCardinal) primaryColor else Color.argb(60, 255, 255, 255)
            canvas.drawLine(x1, y1, x2, y2, specularPaint)
        }

        // 4. Sweeping Orbital Energy Arc
        if (hasGauge) {
            val arcRadius = radius - (6f * d)
            val arcRect = RectF(cx - arcRadius, cy - arcRadius, cx + arcRadius, cy + arcRadius)
            val fraction = (stepIndex.toFloat() / (totalSteps - 1).coerceAtLeast(1)).coerceIn(0f, 1f)

            // Start from 135° (bottom left) and sweep 270° clockwise to 45° (bottom right)
            val startAngle = 135f
            val totalSweep = 270f
            val activeSweep = totalSweep * fraction

            // Inactive orbital track
            gaugeEmptyStrokePaint.strokeWidth = 3.5f * d
            gaugeEmptyStrokePaint.color = Color.argb(35, 255, 255, 255)
            canvas.drawArc(arcRect, startAngle, totalSweep, false, gaugeEmptyStrokePaint)

            // Active glowing energy arc
            if (activeSweep > 0f) {
                gaugeFillStrokePaint.strokeWidth = 3.5f * d
                gaugeFillStrokePaint.color = primaryColor
                canvas.drawArc(arcRect, startAngle, activeSweep, false, gaugeFillStrokePaint)

                // Tachyon orbital lock bead at current position
                val tipAngleRad = Math.toRadians((startAngle + activeSweep).toDouble())
                val beadX = cx + (arcRadius * cos(tipAngleRad)).toFloat()
                val beadY = cy + (arcRadius * sin(tipAngleRad)).toFloat()

                gaugeFillPaint.color = Color.argb(90, r, g, b)
                canvas.drawCircle(beadX, beadY, 7f * d, gaugeFillPaint)

                gaugeFillPaint.color = Color.WHITE
                canvas.drawCircle(beadX, beadY, 3.5f * d, gaugeFillPaint)
            }
        }

        // 5. Tactical Center Crosshairs (Target Acquisition)
        specularPaint.strokeWidth = 1f * d
        specularPaint.color = Color.argb(45, 255, 255, 255)
        canvas.drawLine(cx - (14f * d), cy, cx - (5f * d), cy, specularPaint)
        canvas.drawLine(cx + (5f * d), cy, cx + (14f * d), cy, specularPaint)
        canvas.drawLine(cx, cy - (14f * d), cx, cy - (5f * d), specularPaint)
        canvas.drawLine(cx, cy + (5f * d), cx, cy + (14f * d), specularPaint)

        // 6. Monospace Telemetry Readout
        headerTextPaint.color = primaryColor
        headerTextPaint.textSize = 9f * d
        headerTextPaint.textAlign = Paint.Align.CENTER
        val maxTitleW = (radius * 2f) - (14f * d)
        val safeTitle = if (headerTextPaint.measureText("✦ $title") > maxTitleW) {
            var t = title
            while (t.isNotEmpty() && headerTextPaint.measureText("✦ $t…") > maxTitleW) {
                t = t.dropLast(1)
            }
            "✦ $t…"
        } else {
            "✦ $title"
        }
        canvas.drawText(safeTitle, cx, cy - (16f * d), headerTextPaint)

        valueTextPaint.textSize = 19f * d
        valueTextPaint.textAlign = Paint.Align.CENTER
        canvas.drawText(value, cx, cy + (6f * d), valueTextPaint)

        if (hasGauge) {
            subTextPaint.color = Color.argb(175, 210, 225, 245)
            subTextPaint.textSize = 8.5f * d
            subTextPaint.textAlign = Paint.Align.CENTER
            val label = if (totalSteps <= 24) "STEP ${stepIndex + 1} / $totalSteps" else "LEVEL: $value"
            canvas.drawText(label, cx, cy + (22f * d), subTextPaint)
        }
    }
}
