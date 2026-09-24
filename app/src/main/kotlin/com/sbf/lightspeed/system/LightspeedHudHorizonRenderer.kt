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
 * Dedicated renderer for Style 4: Quantum Synthetic Horizon (Liquid Glass HUD).
 * Aircraft/starship artificial horizon collimator with pitch ladder hashes, swept wings,
 * glowing waterline level bar, and digital flight telemetry.
 */
object LightspeedHudHorizonRenderer {

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

    private val chamferedPath = Path()

    // Quantum Horizon — cached shaders
    private var horizonGlassKey: Long = Long.MIN_VALUE
    private var horizonGlassShader: LinearGradient? = null

    private var horizonRimKey: Long = Long.MIN_VALUE
    private var horizonRimShader: LinearGradient? = null

    private var horizonFillKey: Long = Long.MIN_VALUE
    private var horizonFillShader: LinearGradient? = null

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
        specularPaint.shader = null
        gaugeFillPaint.shader = null
        gaugeEmptyPaint.shader = null
    }

    fun drawQuantumHorizon(
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
        val cardW = 324f * d
        val cardH = 88f * d
        val chamfer = 12f * d
        val rect = RectF(cx - cardW / 2f, topY, cx + cardW / 2f, topY + cardH)
        resetSharedPaints()

        val r = Color.red(primaryColor)
        val g = Color.green(primaryColor)
        val b = Color.blue(primaryColor)

        // 1. Frosted Liquid Glass Backplane — cached shader
        val glassKey = gradKey4f(rect.left, rect.top, rect.left, rect.bottom, primaryColor)
        if (glassKey != horizonGlassKey) {
            horizonGlassShader = LinearGradient(
                rect.left, rect.top, rect.left, rect.bottom,
                intArrayOf(
                    Color.argb(220, (14 + r * 0.08f).toInt().coerceIn(0, 255), (18 + g * 0.08f).toInt().coerceIn(0, 255), (28 + b * 0.08f).toInt().coerceIn(0, 255)),
                    Color.argb(242, (8 + r * 0.04f).toInt().coerceIn(0, 255), (12 + g * 0.04f).toInt().coerceIn(0, 255), (20 + b * 0.04f).toInt().coerceIn(0, 255))
                ),
                floatArrayOf(0f, 1f),
                Shader.TileMode.CLAMP
            )
            horizonGlassKey = glassKey
        }
        val path = createChamferedPath(rect, chamfer)
        glassFillPaint.withShader(horizonGlassShader) { p ->
            canvas.drawPath(path, p)
        }

        // 2. Liquid Glass Refractive Rim — cached shader
        val rimKey = gradKey4f(rect.left, rect.top, rect.right, rect.bottom, primaryColor)
        if (rimKey != horizonRimKey) {
            horizonRimShader = LinearGradient(
                rect.left, rect.top, rect.right, rect.bottom,
                intArrayOf(
                    Color.argb(170, 255, 255, 255),
                    Color.argb(90, r, g, b),
                    Color.argb(30, 255, 255, 255),
                    Color.argb(120, r, g, b)
                ),
                floatArrayOf(0f, 0.35f, 0.7f, 1f),
                Shader.TileMode.CLAMP
            )
            horizonRimKey = rimKey
        }
        glassRimPaint.strokeWidth = 1.4f * d
        glassRimPaint.withShader(horizonRimShader) { p ->
            canvas.drawPath(path, p)
        }

        // 3. Specular Light Glint along upper edge
        specularPaint.strokeWidth = 1.4f * d
        specularPaint.color = Color.argb(180, 255, 255, 255)
        canvas.drawLine(rect.left + chamfer, rect.top + 0.8f * d, rect.right - chamfer, rect.top + 0.8f * d, specularPaint)

        // 4. Swept Flight Wing Brackets (HUD Reticle Frame)
        val bracketY = rect.top + (38f * d)
        val wingSpan = 28f * d
        specularPaint.strokeWidth = 1.8f * d
        specularPaint.color = Color.argb(160, r, g, b)
        // Left Wing
        canvas.drawLine(rect.left + (16f * d), bracketY, rect.left + (16f * d) + wingSpan, bracketY, specularPaint)
        canvas.drawLine(rect.left + (16f * d), bracketY - (6f * d), rect.left + (16f * d), bracketY + (6f * d), specularPaint)
        // Right Wing
        canvas.drawLine(rect.right - (16f * d) - wingSpan, bracketY, rect.right - (16f * d), bracketY, specularPaint)
        canvas.drawLine(rect.right - (16f * d), bracketY - (6f * d), rect.right - (16f * d), bracketY + (6f * d), specularPaint)

        // Pitch Ladder Hashes (Subtle avionics grid)
        specularPaint.strokeWidth = 1f * d
        specularPaint.color = Color.argb(50, 255, 255, 255)
        canvas.drawLine(cx - (18f * d), bracketY - (10f * d), cx + (18f * d), bracketY - (10f * d), specularPaint)
        canvas.drawLine(cx - (18f * d), bracketY + (10f * d), cx + (18f * d), bracketY + (10f * d), specularPaint)

        // Monospace Status Readout (Top Right)
        subTextPaint.color = Color.argb(190, 220, 230, 245)
        subTextPaint.textSize = 9.5f * d
        subTextPaint.textAlign = Paint.Align.RIGHT
        val subLabel = if (hasGauge) {
            if (totalSteps <= 24) "STEP ${stepIndex + 1}/$totalSteps" else "LEVEL $value"
        } else "ACTIVE"
        val subW = subTextPaint.measureText(subLabel)
        canvas.drawText(subLabel, rect.right - (16f * d), rect.top + (18f * d), subTextPaint)

        // 5. Header Telemetry
        headerTextPaint.color = primaryColor
        headerTextPaint.textSize = 9.5f * d
        headerTextPaint.textAlign = Paint.Align.LEFT
        val maxHeaderW = rect.width() - (36f * d) - subW - (12f * d)
        val rawHeader = "✦ $title"
        val safeHeader = if (headerTextPaint.measureText(rawHeader) > maxHeaderW) {
            var t = title
            while (t.isNotEmpty() && headerTextPaint.measureText("✦ $t…") > maxHeaderW) {
                t = t.dropLast(1)
            }
            "✦ $t…"
        } else {
            rawHeader
        }
        canvas.drawText(safeHeader, rect.left + (16f * d), rect.top + (18f * d), headerTextPaint)

        // 6. Central Digital Readout
        valueTextPaint.textSize = 19f * d
        valueTextPaint.textAlign = Paint.Align.CENTER
        canvas.drawText(value, cx, rect.top + (44f * d), valueTextPaint)

        // 7. Synthetic Waterline Energy Bar
        if (hasGauge) {
            val barLeft = rect.left + (18f * d)
            val barRight = rect.right - (18f * d)
            val barW = barRight - barLeft
            val barY = rect.top + (66f * d)
            val barH = 5.5f * d
            val fraction = (stepIndex.toFloat() / (totalSteps - 1).coerceAtLeast(1)).coerceIn(0f, 1f)

            // Track background
            val trackRect = RectF(barLeft, barY, barRight, barY + barH)
            gaugeEmptyPaint.color = Color.argb(35, 255, 255, 255)
            canvas.drawRoundRect(trackRect, barH / 2f, barH / 2f, gaugeEmptyPaint)

            // Progress Fill — cached shader
            val fillW = barW * fraction
            if (fillW > 0f) {
                val fillRect = RectF(barLeft, barY, barLeft + fillW, barY + barH)
                val fillKey = gradKey4f(barLeft, barY, barLeft + fillW, barY, primaryColor)
                if (fillKey != horizonFillKey) {
                    horizonFillShader = LinearGradient(
                        barLeft, barY, barLeft + fillW, barY,
                        intArrayOf(Color.argb(180, r, g, b), primaryColor),
                        null,
                        Shader.TileMode.CLAMP
                    )
                    horizonFillKey = fillKey
                }
                gaugeFillPaint.withShader(horizonFillShader) { p ->
                    canvas.drawRoundRect(fillRect, barH / 2f, barH / 2f, p)
                }
            }

            // Artificial Horizon Collimator Pip
            val pipX = (barLeft + fillW).coerceIn(barLeft, barRight)
            val pipY = barY + barH / 2f

            gaugeFillPaint.color = Color.argb(90, r, g, b)
            canvas.drawCircle(pipX, pipY, 7.5f * d, gaugeFillPaint)

            gaugeFillPaint.color = Color.WHITE
            canvas.drawCircle(pipX, pipY, 4f * d, gaugeFillPaint)

            // Center Nadir/Zenith Hash
            specularPaint.strokeWidth = 1.2f * d
            specularPaint.color = Color.argb(120, 255, 255, 255)
            canvas.drawLine(cx, barY - (3f * d), cx, barY + barH + (3f * d), specularPaint)
        }

        headerTextPaint.textAlign = Paint.Align.CENTER
        subTextPaint.textAlign = Paint.Align.CENTER
        valueTextPaint.textAlign = Paint.Align.CENTER
    }
}
