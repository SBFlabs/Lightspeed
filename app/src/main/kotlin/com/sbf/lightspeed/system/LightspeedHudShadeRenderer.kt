package com.sbf.lightspeed.system

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * 2nd Action-Specific Signature HUD Renderer (`signature_sonic_shade`, `signature_solar_shade`,
 * `signature_chrono_shade`, `signature_thruster_shade`, `signature_media_shade`).
 *
 * Frameless open-air elements (zero card, box, circle container, or outer backdrop) with per-element
 * heavy condensing black alpha-shades that fall off smoothly outward and crisp dark contours so
 * every glyph, number, and slider step stays legible over any background.
 */
object LightspeedHudShadeRenderer {

    private val shadeShader = RadialGradient(
        0f,
        0f,
        1f,
        intArrayOf(
            Color.argb(242, 0, 0, 0),
            Color.argb(202, 0, 0, 0),
            Color.argb(112, 0, 0, 0),
            Color.argb(28, 0, 0, 0),
            Color.argb(0, 0, 0, 0)
        ),
        floatArrayOf(0f, 0.28f, 0.56f, 0.80f, 1f),
        Shader.TileMode.CLAMP
    )
    private val shadeMatrix = Matrix()
    private val shadePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        shader = shadeShader
    }

    private val stepContourPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
        color = Color.argb(235, 0, 0, 0)
    }
    private val stepFillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }
    private val lineStrokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
    }

    private val titleStrokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = Typeface.create("sans-serif-medium", Typeface.BOLD)
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
        color = Color.argb(238, 0, 0, 0)
        textAlign = Paint.Align.LEFT
    }
    private val titleFillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = Typeface.create("sans-serif-medium", Typeface.BOLD)
        style = Paint.Style.FILL
        color = Color.argb(250, 242, 247, 255)
        textAlign = Paint.Align.LEFT
    }

    private val valueStrokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = Typeface.create("sans-serif-black", Typeface.BOLD)
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
        color = Color.argb(245, 0, 0, 0)
        textAlign = Paint.Align.RIGHT
    }
    private val valueFillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = Typeface.create("sans-serif-black", Typeface.BOLD)
        style = Paint.Style.FILL
        color = Color.WHITE
        textAlign = Paint.Align.RIGHT
    }

    private val stepRect = RectF()

    private fun drawElementShadeCloud(
        canvas: Canvas,
        cx: Float,
        cy: Float,
        rx: Float,
        ry: Float
    ) {
        val safeRx = rx.coerceAtLeast(2f)
        val safeRy = ry.coerceAtLeast(2f)
        shadeMatrix.reset()
        shadeMatrix.setScale(safeRx, safeRy)
        shadeMatrix.postTranslate(cx, cy)
        shadeShader.setLocalMatrix(shadeMatrix)
        canvas.drawOval(cx - safeRx, cy - safeRy, cx + safeRx, cy + safeRy, shadePaint)
    }

    fun drawActionShadeSignatureHud(
        canvas: Canvas,
        styleKey: String,
        title: String,
        value: String,
        stepIndex: Int,
        totalSteps: Int,
        cx: Float,
        topY: Float,
        primaryColor: Int,
        d: Float,
        useCentralTheme: Boolean = true,
        colorMode: String = if (useCentralTheme) LightspeedPreferences.COCKPIT_COLOR_MODE_THEME_12 else LightspeedPreferences.COCKPIT_COLOR_MODE_M3_DYNAMIC
    ) {
        val spec = LightspeedHudThemeHelper.resolveSpec(useCentralTheme, primaryColor, d, colorMode)
        val hasGauge = totalSteps > 0 && stepIndex >= 0
        val fraction = if (hasGauge) {
            (stepIndex.toFloat() / (totalSteps - 1).coerceAtLeast(1)).coerceIn(0f, 1f)
        } else 0.65f

        val spanW = 286f * d
        val leftX = cx - spanW * 0.5f
        val rightX = cx + spanW * 0.5f

        val iconCx = leftX + 20f * d
        val iconCy = topY + 33f * d
        val contentLeft = iconCx + 28f * d
        val contentRight = rightX - 4f * d
        val cleanVal = value.removePrefix("[").removeSuffix("]").trim()

        // 1. Floating Sculpted Icon (tight radial shade + 4-dir black silhouette + bright tint icon)
        drawElementShadeCloud(canvas, iconCx, iconCy, 25f * d, 25f * d)
        val effectiveTitle = when (styleKey) {
            "signature_sonic_shade" -> if (title.isBlank()) "SONIC EQUALIZER" else title
            "signature_solar_shade" -> if (title.isBlank()) "SOLAR LUX" else title
            "signature_chrono_shade" -> if (title.isBlank()) "CHRONO SLEEP" else title
            "signature_thruster_shade" -> if (title.isBlank()) "KINETIC VECTOR" else title
            "signature_media_shade" -> if (title.isBlank()) "MEDIA TIMELINE" else title
            else -> title
        }
        val shadowTint = Color.argb(230, 0, 0, 0)
        val off = 1.4f * d
        LightspeedHudModernRenderer.drawSculptedSymbolIcon(canvas, effectiveTitle, value, fraction, iconCx - off, iconCy, 20f * d, shadowTint, d)
        LightspeedHudModernRenderer.drawSculptedSymbolIcon(canvas, effectiveTitle, value, fraction, iconCx + off, iconCy, 20f * d, shadowTint, d)
        LightspeedHudModernRenderer.drawSculptedSymbolIcon(canvas, effectiveTitle, value, fraction, iconCx, iconCy - off, 20f * d, shadowTint, d)
        LightspeedHudModernRenderer.drawSculptedSymbolIcon(canvas, effectiveTitle, value, fraction, iconCx, iconCy + off, 20f * d, shadowTint, d)
        LightspeedHudModernRenderer.drawSculptedSymbolIcon(canvas, effectiveTitle, value, fraction, iconCx, iconCy, 20f * d, spec.tintColor, d)

        // 2. Floating Value Readout Numbers (Right Cluster)
        val textBaselineY = topY + 24f * d
        valueStrokePaint.textSize = 15.5f * d
        valueStrokePaint.strokeWidth = 4.0f * d
        valueFillPaint.textSize = 15.5f * d
        val valW = valueFillPaint.measureText(cleanVal)
        drawElementShadeCloud(
            canvas = canvas,
            cx = contentRight - valW * 0.5f,
            cy = textBaselineY - 5.5f * d,
            rx = valW * 0.5f + 17f * d,
            ry = 16.5f * d
        )
        canvas.drawText(cleanVal, contentRight, textBaselineY, valueStrokePaint)
        canvas.drawText(cleanVal, contentRight, textBaselineY, valueFillPaint)

        // 3. Floating Telemetry Sub-Label Letters (Left Cluster)
        val subTag = when (styleKey) {
            "signature_sonic_shade" -> {
                val db = if (fraction <= 0.01f) "-INF dB" else String.format(java.util.Locale.US, "%+.1f dB", (fraction - 0.8f) * 35f)
                "EQ SPECTRUM · $db"
            }
            "signature_solar_shade" -> {
                if (title.equals("EXTRA DIM", ignoreCase = true)) {
                    val dimPct = cleanVal.substringBefore('%').filter { it.isDigit() }.toIntOrNull()
                        ?: (stepIndex.coerceAtLeast(0) * 10).coerceIn(0, 100)
                    "EXTRA DIM · -${dimPct}%"
                } else {
                    val nits = (10 + (fraction * fraction * 990f).roundToInt()).coerceIn(5, 1000)
                    "PHOTOMETER · ~${nits} NITS"
                }
            }
            "signature_chrono_shade" -> "SLEEP HORIZON · STEP ${stepIndex.coerceAtLeast(0) + 1}/${totalSteps.coerceAtLeast(1)}"
            "signature_media_shade" -> {
                val cleanTitle = title.removePrefix("MEDIA · ").trim()
                if (cleanTitle.isNotBlank() && !cleanTitle.equals("MEDIA TIMELINE", ignoreCase = true)) cleanTitle else "MEDIA TIMELINE"
            }
            else -> "THRUSTER · VELOCITY VECTOR"
        }
        titleStrokePaint.textSize = 10f * d
        titleStrokePaint.strokeWidth = 3.2f * d
        titleFillPaint.textSize = 10f * d
        val maxSubW = (contentRight - valW - contentLeft - 10f * d).coerceAtLeast(36f * d)
        val safeSub = LightspeedHudModernRenderer.ellipsizeText(subTag, titleFillPaint, maxSubW)
        val subW = titleFillPaint.measureText(safeSub)
        drawElementShadeCloud(
            canvas = canvas,
            cx = contentLeft + subW * 0.5f,
            cy = textBaselineY - 4.5f * d,
            rx = subW * 0.5f + 15f * d,
            ry = 13.5f * d
        )
        canvas.drawText(safeSub, contentLeft, textBaselineY - 1f * d, titleStrokePaint)
        canvas.drawText(safeSub, contentLeft, textBaselineY - 1f * d, titleFillPaint)

        // 4. Floating Per-Step Visualizer (Pass 1: per-step condensing shade + contour; Pass 2: bright step fills)
        val vizTop = topY + 34f * d
        val vizBottom = topY + 57f * d
        val vizW = (contentRight - contentLeft).coerceAtLeast(40f * d)
        val vizH = (vizBottom - vizTop).coerceAtLeast(10f * d)
        val dimSilver = Color.argb(155, 232, 240, 252)
        val litColor = spec.tintColor

        when (styleKey) {
            "signature_sonic_shade" -> {
                val bars = 18
                val gap = 3.0f * d
                val barW = ((vizW - gap * (bars - 1)) / bars).coerceAtLeast(2.2f * d)
                val activeBars = (fraction * bars).roundToInt()
                stepContourPaint.strokeWidth = 1.8f * d
                for (i in 0 until bars) {
                    val wave = 0.35f + 0.65f * abs(sin(i * 0.55 + fraction * 6.0)).toFloat()
                    val bh = (vizH * wave).coerceIn(3.5f * d, vizH)
                    val bx = contentLeft + i * (barW + gap)
                    val by = vizBottom - bh
                    drawElementShadeCloud(canvas, bx + barW * 0.5f, by + bh * 0.5f, barW * 2.4f + 4.5f * d, bh * 0.5f + 8.5f * d)
                    stepRect.set(bx, by, bx + barW, vizBottom)
                    canvas.drawRoundRect(stepRect, barW * 0.5f, barW * 0.5f, stepContourPaint)
                }
                for (i in 0 until bars) {
                    val wave = 0.35f + 0.65f * abs(sin(i * 0.55 + fraction * 6.0)).toFloat()
                    val bh = (vizH * wave).coerceIn(3.5f * d, vizH)
                    val bx = contentLeft + i * (barW + gap)
                    val by = vizBottom - bh
                    stepRect.set(bx, by, bx + barW, vizBottom)
                    stepFillPaint.color = when {
                        i == activeBars - 1 && activeBars > 0 -> Color.WHITE
                        i < activeBars -> litColor
                        else -> dimSilver
                    }
                    canvas.drawRoundRect(stepRect, barW * 0.5f, barW * 0.5f, stepFillPaint)
                }
            }
            "signature_solar_shade" -> {
                val segments = 12
                val gap = 3.6f * d
                val segW = ((vizW - gap * (segments - 1)) / segments).coerceAtLeast(3f * d)
                val activePos = fraction * segments
                val activeSegs = activePos.roundToInt()
                val cy = (vizTop + vizBottom) * 0.5f
                val maxHalfH = (vizH * 0.5f).coerceAtLeast(4f * d)
                stepContourPaint.strokeWidth = 1.8f * d
                for (i in 0 until segments) {
                    val sx = contentLeft + i * (segW + gap)
                    val isActive = i < activeSegs
                    val distFromHead = abs((i + 0.5f) - activePos)
                    val headFlare = (1f - (distFromHead / 3.2f)).coerceIn(0f, 1f)
                    val wave = 0.52f + 0.34f * abs(sin(i * 0.65 + fraction * 7.5)).toFloat() + (0.28f * headFlare)
                    val halfH = if (isActive) (maxHalfH * wave).coerceIn(3f * d, maxHalfH) else (maxHalfH * 0.42f).coerceAtLeast(2.2f * d)
                    drawElementShadeCloud(canvas, sx + segW * 0.5f, cy, segW * 1.55f + 4.5f * d, halfH + 8.5f * d)
                    stepRect.set(sx, cy - halfH, sx + segW, cy + halfH)
                    canvas.drawRoundRect(stepRect, segW * 0.45f, segW * 0.45f, stepContourPaint)
                }
                for (i in 0 until segments) {
                    val sx = contentLeft + i * (segW + gap)
                    val isActive = i < activeSegs
                    val distFromHead = abs((i + 0.5f) - activePos)
                    val headFlare = (1f - (distFromHead / 3.2f)).coerceIn(0f, 1f)
                    val wave = 0.52f + 0.34f * abs(sin(i * 0.65 + fraction * 7.5)).toFloat() + (0.28f * headFlare)
                    val halfH = if (isActive) (maxHalfH * wave).coerceIn(3f * d, maxHalfH) else (maxHalfH * 0.42f).coerceAtLeast(2.2f * d)
                    stepRect.set(sx, cy - halfH, sx + segW, cy + halfH)
                    stepFillPaint.color = if (isActive) {
                        if (i == activeSegs - 1) Color.WHITE else litColor
                    } else dimSilver
                    canvas.drawRoundRect(stepRect, segW * 0.45f, segW * 0.45f, stepFillPaint)
                }
            }
            "signature_chrono_shade" -> {
                val steps = totalSteps.coerceIn(4, 10)
                val lineY = (vizTop + vizBottom) * 0.5f
                val activeX = contentLeft + vizW * fraction
                for (i in 0 until steps) {
                    val tx = contentLeft + (vizW * i.toFloat() / (steps - 1).coerceAtLeast(1))
                    val nodeR = if (i == stepIndex) 5.2f * d else 3.0f * d
                    drawElementShadeCloud(canvas, tx, lineY, nodeR + 9.5f * d, nodeR + 9.5f * d)
                }
                lineStrokePaint.strokeWidth = 4.2f * d
                lineStrokePaint.color = Color.argb(230, 0, 0, 0)
                canvas.drawLine(contentLeft, lineY, contentRight, lineY, lineStrokePaint)
                lineStrokePaint.strokeWidth = 2.0f * d
                lineStrokePaint.color = dimSilver
                canvas.drawLine(contentLeft, lineY, contentRight, lineY, lineStrokePaint)
                lineStrokePaint.strokeWidth = 2.4f * d
                lineStrokePaint.color = litColor
                canvas.drawLine(contentLeft, lineY, activeX, lineY, lineStrokePaint)

                stepContourPaint.strokeWidth = 2.0f * d
                for (i in 0 until steps) {
                    val tx = contentLeft + (vizW * i.toFloat() / (steps - 1).coerceAtLeast(1))
                    val nodeR = if (i == stepIndex) 5.2f * d else 3.0f * d
                    canvas.drawCircle(tx, lineY, nodeR, stepContourPaint)
                }
                for (i in 0 until steps) {
                    val tx = contentLeft + (vizW * i.toFloat() / (steps - 1).coerceAtLeast(1))
                    val nodeR = if (i == stepIndex) 5.2f * d else 3.0f * d
                    stepFillPaint.color = when {
                        i == stepIndex -> Color.WHITE
                        i < stepIndex -> litColor
                        else -> dimSilver
                    }
                    canvas.drawCircle(tx, lineY, nodeR, stepFillPaint)
                }
            }
            "signature_thruster_shade" -> {
                val halfSteps = 5
                val totalBars = halfSteps * 2 + 1
                val gap = 3.8f * d
                val barW = ((vizW - gap * (totalBars - 1)) / totalBars).coerceAtLeast(3.5f * d)
                val cy = (vizTop + vizBottom) * 0.5f
                val isUp = value.contains("UP", ignoreCase = true) || value.contains("▲")
                stepContourPaint.strokeWidth = 1.8f * d
                for (i in 0 until totalBars) {
                    val dist = abs(i - halfSteps)
                    val halfH = (3.5f + (halfSteps - dist) * 1.15f) * d
                    val bx = contentLeft + i * (barW + gap)
                    drawElementShadeCloud(canvas, bx + barW * 0.5f, cy, barW * 1.6f + 4.5f * d, halfH + 8.5f * d)
                    stepRect.set(bx, cy - halfH, bx + barW, cy + halfH)
                    canvas.drawRoundRect(stepRect, barW * 0.5f, barW * 0.5f, stepContourPaint)
                }
                for (i in 0 until totalBars) {
                    val dist = abs(i - halfSteps)
                    val halfH = (3.5f + (halfSteps - dist) * 1.15f) * d
                    val bx = contentLeft + i * (barW + gap)
                    val isLit = (i == halfSteps) || (isUp && i > halfSteps) || (!isUp && i < halfSteps)
                    stepRect.set(bx, cy - halfH, bx + barW, cy + halfH)
                    stepFillPaint.color = when {
                        i == halfSteps -> Color.WHITE
                        isLit -> litColor
                        else -> dimSilver
                    }
                    canvas.drawRoundRect(stepRect, barW * 0.5f, barW * 0.5f, stepFillPaint)
                }
            }
            else -> {
                // signature_media_shade: stepped timeline capsules + floating playhead pip
                val steps = 16
                val gap = 3.0f * d
                val segW = ((vizW - gap * (steps - 1)) / steps).coerceAtLeast(3f * d)
                val cy = (vizTop + vizBottom) * 0.5f
                val activeStep = (fraction * (steps - 1)).roundToInt().coerceIn(0, steps - 1)
                stepContourPaint.strokeWidth = 1.8f * d
                for (i in 0 until steps) {
                    val sx = contentLeft + i * (segW + gap)
                    val halfH = if (i == activeStep) 7.5f * d else 3.4f * d
                    drawElementShadeCloud(canvas, sx + segW * 0.5f, cy, segW * 1.55f + 4.5f * d, halfH + 8.5f * d)
                    stepRect.set(sx, cy - halfH, sx + segW, cy + halfH)
                    canvas.drawRoundRect(stepRect, segW * 0.45f, segW * 0.45f, stepContourPaint)
                }
                for (i in 0 until steps) {
                    val sx = contentLeft + i * (segW + gap)
                    val halfH = if (i == activeStep) 7.5f * d else 3.4f * d
                    stepRect.set(sx, cy - halfH, sx + segW, cy + halfH)
                    stepFillPaint.color = when {
                        i == activeStep -> Color.WHITE
                        i < activeStep -> litColor
                        else -> dimSilver
                    }
                    canvas.drawRoundRect(stepRect, segW * 0.45f, segW * 0.45f, stepFillPaint)
                }
            }
        }
    }
}
