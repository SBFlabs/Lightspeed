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
 *
 * Performance: All LinearGradient / RadialGradient shaders are cached as private fields
 * and rebuilt only when their geometry or color inputs change. This eliminates per-frame
 * heap allocations that caused GC pressure and jank during 120Hz gesture scrubbing.
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

    private val gaugeFillStrokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
    }

    private val chamferedPath = Path()

    /**
     * Executes drawing lambda with a temporary shader, guaranteeing atomic restoration
     * via try/finally so subsequent render passes are never contaminated by dirty paint state.
     */
    private inline fun Paint.withShader(tempShader: Shader?, block: (Paint) -> Unit) {
        val prev = this.shader
        this.shader = tempShader
        try {
            block(this)
        } finally {
            this.shader = prev
        }
    }

    /**
     * Resets shared paint shader state before entering any HUD draw pass.
     */
    private fun resetSharedPaints() {
        glassFillPaint.shader = null
        glassRimPaint.shader = null
        specularPaint.shader = null
        gaugeFillPaint.shader = null
    }

    // ---------------------------------------------------------------------------
    // Shader cache — one slot per distinct gradient usage site.
    // Key encodes all inputs that affect the gradient; shader is rebuilt only on miss.
    // ---------------------------------------------------------------------------

    // Glass Card — shared backplane, rim, and specular shader caches
    private var cardGlassKey: Long = Long.MIN_VALUE
    private var cardGlassShader: LinearGradient? = null

    private var cardRimKey: Long = Long.MIN_VALUE
    private var cardRimShader: LinearGradient? = null

    private var cardSpecKey: Long = Long.MIN_VALUE
    private var cardSpecShader: LinearGradient? = null

    // Canopy Drop-Pod — fill track
    private var canopyFillKey: Long = Long.MIN_VALUE
    private var canopyFillShader: LinearGradient? = null

    // Cockpit Reticle — glass backplane
    private var reticleGlassKey: Long = Long.MIN_VALUE
    private var reticleGlassShader: LinearGradient? = null

    /** Packs four floats + color into a Long (lossy but collision-free for plausible geometry). */
    private fun gradKey4f(x0: Float, y0: Float, x1: Float, y1: Float, color: Int): Long =
        (x0.toBits().toLong() xor (x1.toBits().toLong() shl 16)) xor
        (y0.toBits().toLong() xor (y1.toBits().toLong() shl 16)) xor color.toLong()

    // ---------------------------------------------------------------------------

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

    /**
     * Draws a standardized liquid glass card backplane, refractive rim, and optional specular top shimmer line.
     */
    private fun drawGlassCard(
        canvas: Canvas,
        rect: RectF,
        chamfer: Float,
        primaryColor: Int,
        d: Float,
        withSpecularShimmer: Boolean = true,
        rimWidth: Float = 1.4f
    ): Path {
        val r = Color.red(primaryColor)
        val g = Color.green(primaryColor)
        val b = Color.blue(primaryColor)

        // 1. Frosted Liquid Glass Backplane — cached shader
        val glassKey = gradKey4f(rect.left, rect.top, rect.left, rect.bottom, primaryColor)
        if (glassKey != cardGlassKey) {
            cardGlassShader = LinearGradient(
                rect.left, rect.top, rect.left, rect.bottom,
                intArrayOf(
                    Color.argb(215, (16 + r * 0.08f).toInt().coerceIn(0, 255), (20 + g * 0.08f).toInt().coerceIn(0, 255), (32 + b * 0.08f).toInt().coerceIn(0, 255)),
                    Color.argb(238, (10 + r * 0.04f).toInt().coerceIn(0, 255), (14 + g * 0.04f).toInt().coerceIn(0, 255), (24 + b * 0.04f).toInt().coerceIn(0, 255))
                ),
                floatArrayOf(0f, 1f),
                Shader.TileMode.CLAMP
            )
            cardGlassKey = glassKey
        }
        val path = createChamferedPath(rect, chamfer)
        glassFillPaint.withShader(cardGlassShader) { p ->
            canvas.drawPath(path, p)
        }

        // 2. Liquid Glass Refractive Rim — cached shader
        val rimKey = gradKey4f(rect.left, rect.top, rect.right, rect.bottom, primaryColor)
        if (rimKey != cardRimKey) {
            cardRimShader = LinearGradient(
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
            cardRimKey = rimKey
        }
        glassRimPaint.strokeWidth = rimWidth * d
        glassRimPaint.withShader(cardRimShader) { p ->
            canvas.drawPath(path, p)
        }

        // 3. Specular Light Shimmer across top chamfer — cached shader
        if (withSpecularShimmer) {
            val specKey = gradKey4f(rect.left + chamfer, rect.top, rect.right - chamfer, rect.top, 0xFFFFFF)
            if (specKey != cardSpecKey) {
                cardSpecShader = LinearGradient(
                    rect.left + chamfer, rect.top, rect.right - chamfer, rect.top,
                    intArrayOf(
                        Color.argb(10, 255, 255, 255),
                        Color.argb(190, 255, 255, 255),
                        Color.argb(10, 255, 255, 255)
                    ),
                    null,
                    Shader.TileMode.CLAMP
                )
                cardSpecKey = specKey
            }
            specularPaint.strokeWidth = 1.6f * d
            specularPaint.withShader(cardSpecShader) { p ->
                canvas.drawLine(rect.left + chamfer, rect.top + 0.8f * d, rect.right - chamfer, rect.top + 0.8f * d, p)
            }
        }

        return path
    }

    /**
     * Main dispatch method for rendering gesture scrubbing HUDs.
     */
    fun renderHud(
        canvas: Canvas,
        style: String, // "canopy_droppod", "cockpit_reticle", "edge_blade", "quantum_horizon", "tachyon_dial"
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
        headerTextPaint.textAlign = Paint.Align.CENTER
        valueTextPaint.textAlign = Paint.Align.CENTER
        subTextPaint.textAlign = Paint.Align.CENTER

        when (style) {
            "cockpit_reticle" -> drawCockpitReticle(
                canvas, title, value, stepIndex, totalSteps, centerX, centerY, primaryColor, density
            )
            "edge_blade" -> drawEdgeBlade(
                canvas, title, value, stepIndex, totalSteps, centerX, centerY, primaryColor, density, isLeftFlank
            )
            "quantum_horizon" -> drawQuantumHorizon(
                canvas, title, value, stepIndex, totalSteps, centerX, topY, primaryColor, density
            )
            "tachyon_dial" -> drawTachyonDial(
                canvas, title, value, stepIndex, totalSteps, centerX, centerY, primaryColor, density
            )
            "canopy_droppod" -> drawCanopyDropPod(
                canvas, title, value, stepIndex, totalSteps, centerX, topY, primaryColor, density
            )
            else -> drawCanopyDropPod(
                canvas, title, value, stepIndex, totalSteps, centerX, topY, primaryColor, density
            )
        }

        headerTextPaint.textAlign = Paint.Align.CENTER
        valueTextPaint.textAlign = Paint.Align.CENTER
        subTextPaint.textAlign = Paint.Align.CENTER
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
        resetSharedPaints()
        drawGlassCard(canvas, rect, chamfer, primaryColor, d, withSpecularShimmer = true)

        val r = Color.red(primaryColor)
        val g = Color.green(primaryColor)
        val b = Color.blue(primaryColor)

        // 4. Header Telemetry (Material 3 Dynamic Accent)
        headerTextPaint.color = primaryColor
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

        // 5. Value Readout (Crisp White Glow)
        valueTextPaint.textSize = 19f * d
        valueTextPaint.textAlign = Paint.Align.CENTER
        canvas.drawText("[ $value ]", cx, topY + (41f * d), valueTextPaint)

        // 6. 7-Segment Liquid Glass Quantum Gauge / Continuous Precision Track
        if (hasGauge) {
            val maxGaugeW = cardW - (36f * d)
            val gaugeY = topY + (52f * d)

            if (totalSteps <= 16) {
                // Tactical Segmented Blocks
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
            } else {
                // Continuous High-Precision Liquid Glass Track (for fine resolutions / 100+ steps)
                val trackH = 5.5f * d
                val trackLeft = cx - (maxGaugeW / 2f)
                val trackRight = cx + (maxGaugeW / 2f)
                val trackRect = RectF(trackLeft, gaugeY, trackRight, gaugeY + trackH)

                gaugeEmptyPaint.color = Color.argb(35, 255, 255, 255)
                canvas.drawRoundRect(trackRect, 2.5f * d, 2.5f * d, gaugeEmptyPaint)

                val progress = (stepIndex.toFloat() / (totalSteps - 1).coerceAtLeast(1)).coerceIn(0f, 1f)
                val fillW = maxGaugeW * progress
                if (fillW > 0f) {
                    val fillRect = RectF(trackLeft, gaugeY, trackLeft + fillW, gaugeY + trackH)
                    // Cached fill gradient — key encodes fillW + color
                    val fillKey = gradKey4f(trackLeft, gaugeY, trackLeft + fillW, gaugeY, primaryColor)
                    if (fillKey != canopyFillKey) {
                        canopyFillShader = LinearGradient(
                            trackLeft, gaugeY, trackLeft + fillW, gaugeY,
                            intArrayOf(Color.argb(180, r, g, b), primaryColor),
                            null,
                            Shader.TileMode.CLAMP
                        )
                        canopyFillKey = fillKey
                    }
                    gaugeFillPaint.withShader(canopyFillShader) { p ->
                        canvas.drawRoundRect(fillRect, 2.5f * d, 2.5f * d, p)
                    }

                    // Collimator Pip Marker
                    val pipX = trackLeft + fillW
                    specularPaint.color = Color.WHITE
                    specularPaint.strokeWidth = 2f * d
                    canvas.drawLine(pipX, gaugeY - (2.5f * d), pipX, gaugeY + trackH + (2.5f * d), specularPaint)
                }
            }

            // Sub-readout tier
            subTextPaint.color = Color.argb(175, 210, 225, 245)
            subTextPaint.textSize = 9f * d
            val label = if (totalSteps <= 20) "STEP ${stepIndex + 1} OF $totalSteps" else "OUTPUT INTENSITY: $value"
            canvas.drawText(label, cx, topY + (75f * d), subTextPaint)
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
        resetSharedPaints()
        val r = Color.red(primaryColor)
        val g = Color.green(primaryColor)
        val b = Color.blue(primaryColor)

        // 1. Translucent Frosted Glass Reticle Plate — cached shader
        val glassKey = gradKey4f(cx - radius, cy - radius, cx + radius, cy + radius, primaryColor)
        if (glassKey != reticleGlassKey) {
            reticleGlassShader = LinearGradient(
                cx - radius, cy - radius, cx + radius, cy + radius,
                intArrayOf(
                    Color.argb(220, (18 + r * 0.08f).toInt().coerceIn(0, 255), (22 + g * 0.08f).toInt().coerceIn(0, 255), (34 + b * 0.08f).toInt().coerceIn(0, 255)),
                    Color.argb(240, (10 + r * 0.04f).toInt().coerceIn(0, 255), (14 + g * 0.04f).toInt().coerceIn(0, 255), (24 + b * 0.04f).toInt().coerceIn(0, 255))
                ),
                null,
                Shader.TileMode.CLAMP
            )
            reticleGlassKey = glassKey
        }
        glassFillPaint.withShader(reticleGlassShader) { p ->
            canvas.drawCircle(cx, cy, radius + (14f * d), p)
        }

        // 2. Liquid Glass Outer Rim
        glassRimPaint.strokeWidth = 1.3f * d
        glassRimPaint.color = Color.argb(90, 255, 255, 255)
        canvas.drawCircle(cx, cy, radius + (14f * d), glassRimPaint)

        // 3. Orbital Arc & Step Ticks
        if (hasGauge && totalSteps > 1) {
            val startAngle = 140.0
            val sweep = 260.0
            val arcRect = RectF(cx - radius, cy - radius, cx + radius, cy + radius)
            glassRimPaint.strokeWidth = 1.5f * d
            glassRimPaint.color = Color.argb(60, 255, 255, 255)
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
                    specularPaint.color = if (isCurrent) primaryColor else if (isPassed) Color.argb(200, r, g, b) else Color.argb(70, 255, 255, 255)
                    canvas.drawLine(x1, y1, x2, y2, specularPaint)
                }
            } else {
                val progress = (stepIndex.toFloat() / (totalSteps - 1).coerceAtLeast(1)).coerceIn(0f, 1f)
                val currentSweep = (sweep * progress).toFloat()
                if (currentSweep > 0f) {
                    specularPaint.strokeWidth = 2.8f * d
                    specularPaint.color = primaryColor
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
        headerTextPaint.color = primaryColor
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
        resetSharedPaints()
        drawGlassCard(canvas, rect, chamfer, primaryColor, d, withSpecularShimmer = false, rimWidth = 1.3f)

        val r = Color.red(primaryColor)
        val g = Color.green(primaryColor)
        val b = Color.blue(primaryColor)

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
                    val rungRect = RectF(ladderX, rY, ladderX + rungW, rY + rungH)
                    if (i <= stepIndex) {
                        gaugeFillPaint.color = if (i == stepIndex) primaryColor else Color.argb(195, r, g, b)
                        canvas.drawRoundRect(rungRect, 1.5f * d, 1.5f * d, gaugeFillPaint)
                    } else {
                        gaugeEmptyPaint.color = Color.argb(35, 255, 255, 255)
                        canvas.drawRoundRect(rungRect, 1.5f * d, 1.5f * d, gaugeEmptyPaint)
                    }
                }
            } else {
                val trackH = cardH - (24f * d)
                val trackY = cy - (trackH / 2f)
                val trackRect = RectF(ladderX, trackY, ladderX + rungW, trackY + trackH)
                gaugeEmptyPaint.color = Color.argb(35, 255, 255, 255)
                canvas.drawRoundRect(trackRect, rungW / 2f, rungW / 2f, gaugeEmptyPaint)

                val fraction = (stepIndex.toFloat() / (totalSteps - 1).coerceAtLeast(1)).coerceIn(0f, 1f)
                val fillH = trackH * fraction
                if (fillH > 0f) {
                    val fillTop = trackY + trackH - fillH
                    val fillRect = RectF(ladderX, fillTop, ladderX + rungW, trackY + trackH)
                    gaugeFillPaint.color = primaryColor
                    canvas.drawRoundRect(fillRect, rungW / 2f, rungW / 2f, gaugeFillPaint)

                    gaugeFillPaint.color = Color.WHITE
                    canvas.drawCircle(ladderX + rungW / 2f, fillTop, 3.5f * d, gaugeFillPaint)
                }
            }
        }

        // 4. Telemetry Text
        val textCx = if (isLeftFlank) rect.centerX() + (6f * d) else rect.centerX() - (6f * d)
        headerTextPaint.color = primaryColor
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

    /**
     * Style 4: Quantum Synthetic Horizon (Liquid Glass HUD)
     * Aircraft/starship artificial horizon collimator with pitch ladder hashes, swept wings,
     * glowing waterline level bar, and digital flight telemetry.
     */
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
        LightspeedHudHorizonRenderer.drawQuantumHorizon(
            canvas, title, value, stepIndex, totalSteps, cx, topY, primaryColor, d
        )
    }

    /**
     * Style 5: Tachyon Orbital Radar (Liquid Glass HUD)
     * High-tech circular tactical scanner with 360° azimuth degree hashes, concentric range rings,
     * sweeping orbital energy arc, target acquisition crosshairs, and digital telemetry.
     */
    fun drawTachyonDial(
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
        LightspeedHudDialRenderer.drawTachyonDial(canvas, title, value, stepIndex, totalSteps, cx, cy, primaryColor, d)
    }

    /**
     * Formats milliseconds into clean MM:SS timestamp string.
     */
    fun formatTime(ms: Long): String = LightspeedMediaScrubberOverlay.formatTime(ms)

    /**
     * Returns touch bounding box for the interactive seekbar.
     */
    fun getMediaScrubberBarBounds(cx: Float, topY: Float, d: Float): RectF =
        LightspeedMediaScrubberOverlay.getMediaScrubberBarBounds(cx, topY, d)

    /**
     * Style 6: Interactive Liquid Glass Media Timeline Scrubber HUD.
     * Displays current track title, artist, live position / duration, and glowing seekbar.
     */
    fun drawMediaScrubberHud(
        canvas: Canvas,
        title: String,
        artist: String,
        positionMs: Long,
        durationMs: Long,
        cx: Float,
        topY: Float,
        primaryColor: Int,
        d: Float,
        isSeeking: Boolean = false,
        seekFraction: Float = -1f
    ) {
        LightspeedMediaScrubberOverlay.drawMediaScrubberHud(
            canvas, title, artist, positionMs, durationMs, cx, topY, primaryColor, d, isSeeking, seekFraction
        )
    }
}
