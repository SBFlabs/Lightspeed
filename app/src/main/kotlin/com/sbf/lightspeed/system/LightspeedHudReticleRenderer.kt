package com.sbf.lightspeed.system

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import kotlin.math.cos
import kotlin.math.sin

/**
 * Dedicated renderer for Style 2: Holographic Cockpit Reticle (Prism HUD).
 * Projects in the focal cockpit area with tachyon circular orbital arc and collimator pips.
 */
object LightspeedHudReticleRenderer {

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

    private val arcRect = RectF()
    private var reticleGlassKey: Long = Long.MIN_VALUE
    private var reticleGlassShader: LinearGradient? = null

    private inline fun Paint.withShader(tempShader: Shader?, block: (Paint) -> Unit) {
        val prev = this.shader
        this.shader = tempShader
        try {
            block(this)
        } finally {
            this.shader = prev
        }
    }

    private fun gradKey4f(x0: Float, y0: Float, x1: Float, y1: Float, color: Int): Long =
        (x0.toBits().toLong() xor (x1.toBits().toLong() shl 16)) xor
        (y0.toBits().toLong() xor (y1.toBits().toLong() shl 16)) xor color.toLong()

    fun drawCockpitReticle(
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
        val radius = 56f * d
        val outerRadius = radius + (14f * d)

        glassFillPaint.shader = null
        glassRimPaint.shader = null
        glowPaint.shader = null
        specularPaint.shader = null

        val pR = Color.red(spec.panelColor)
        val pG = Color.green(spec.panelColor)
        val pB = Color.blue(spec.panelColor)
        val tR = Color.red(spec.tintColor)
        val tG = Color.green(spec.tintColor)
        val tB = Color.blue(spec.tintColor)
        val pA = (spec.panelAlpha * 255).toInt().coerceIn(210, 245)

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
            canvas.drawCircle(cx, cy, outerRadius, glowPaint)
        }

        // 1. Solid Frosted Base (Guarantees zero transparency)
        glassFillPaint.shader = null
        glassFillPaint.color = Color.argb(pA, (pR + tR * 0.08f).toInt().coerceIn(0, 255), (pG + tG * 0.08f).toInt().coerceIn(0, 255), (pB + tB * 0.08f).toInt().coerceIn(0, 255))
        canvas.drawCircle(cx, cy, radius, glassFillPaint)

        // 1b. Translucent Frosted Glass Reticle Plate — cached shader
        val glassKey = gradKey4f(cx - radius, cy - radius, cx + radius, cy + radius, spec.panelColor) xor spec.tintColor.toLong()
        if (glassKey != reticleGlassKey) {
            reticleGlassShader = LinearGradient(
                cx - radius, cy - radius, cx + radius, cy + radius,
                intArrayOf(
                    Color.argb(pA, (pR + tR * 0.10f).toInt().coerceIn(0, 255), (pG + tG * 0.10f).toInt().coerceIn(0, 255), (pB + tB * 0.10f).toInt().coerceIn(0, 255)),
                    Color.argb((pA * 1.05f).toInt().coerceIn(0, 255), pR, pG, pB)
                ),
                null,
                Shader.TileMode.CLAMP
            )
            reticleGlassKey = glassKey
        }
        glassFillPaint.withShader(reticleGlassShader) { p ->
            canvas.drawCircle(cx, cy, outerRadius, p)
        }

        // 2. Prism Outer Rim
        glassRimPaint.strokeWidth = spec.borderWidthPx
        glassRimPaint.color = spec.borderColor
        canvas.drawCircle(cx, cy, outerRadius, glassRimPaint)

        // 3. Orbital Arc & Step Ticks
        if (hasGauge && totalSteps > 1) {
            val startAngle = 140.0
            val sweep = 260.0
            arcRect.set(cx - radius, cy - radius, cx + radius, cy + radius)
            glassRimPaint.strokeWidth = 1.5f * d
            glassRimPaint.color = Color.argb((spec.highlightStrength * 80).toInt().coerceIn(0, 255), 255, 255, 255)
            canvas.drawArc(arcRect, 140f, 260f, false, glassRimPaint)

            if (totalSteps <= 24) {
                val angleStep = sweep / (totalSteps - 1)
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
                    specularPaint.color = if (isCurrent) spec.tintColor else if (isPassed) Color.argb(200, tR, tG, tB) else Color.argb(70, 255, 255, 255)
                    canvas.drawLine(x1, y1, x2, y2, specularPaint)
                }
            } else {
                val progress = (stepIndex.toFloat() / (totalSteps - 1).coerceAtLeast(1)).coerceIn(0f, 1f)
                val currentSweep = (sweep * progress).toFloat()
                if (currentSweep > 0f) {
                    specularPaint.strokeWidth = 2.8f * d
                    specularPaint.color = spec.tintColor
                    canvas.drawArc(arcRect, 140f, currentSweep, false, specularPaint)

                    val theta = Math.toRadians(startAngle + currentSweep)
                    val tipX = cx + (radius * cos(theta)).toFloat()
                    val tipY = cy + (radius * sin(theta)).toFloat()
                    gaugeFillPaint.color = Color.WHITE
                    canvas.drawCircle(tipX, tipY, 3.5f * d, gaugeFillPaint)
                }

                for (q in 0..4) {
                    val qTheta = Math.toRadians(startAngle + (sweep * (q / 4.0)))
                    val innerR = radius - (5f * d)
                    val outerR = radius + (5f * d)
                    val x1 = cx + (innerR * cos(qTheta)).toFloat()
                    val y1 = cy + (innerR * sin(qTheta)).toFloat()
                    val x2 = cx + (outerR * cos(qTheta)).toFloat()
                    val y2 = cy + (outerR * sin(qTheta)).toFloat()
                    specularPaint.strokeWidth = 1.4f * d
                    specularPaint.color = Color.argb(90, 255, 255, 255)
                    canvas.drawLine(x1, y1, x2, y2, specularPaint)
                }
            }
        }

        // 4. Center Telemetry
        headerTextPaint.color = spec.tintColor
        headerTextPaint.textSize = 10f * d
        headerTextPaint.textAlign = Paint.Align.CENTER
        val maxTitleW = (radius * 2f) + (16f * d)
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

        valueTextPaint.textSize = 21f * d
        valueTextPaint.textAlign = Paint.Align.CENTER
        canvas.drawText(value, cx, cy + (7f * d), valueTextPaint)

        if (hasGauge) {
            subTextPaint.color = Color.argb(175, 210, 225, 245)
            subTextPaint.textSize = 8.5f * d
            subTextPaint.textAlign = Paint.Align.CENTER
            val label = if (totalSteps <= 24) "STEP ${stepIndex + 1} / $totalSteps" else "LEVEL: $value"
            canvas.drawText(label, cx, cy + (24f * d), subTextPaint)
        }
    }
}
