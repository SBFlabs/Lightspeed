package com.sbf.lightspeed.system

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import kotlin.math.roundToInt

/**
 * Action-Specific Signature HUD Renderers (`signature_sonic_eq`, `signature_solar_lux`,
 * `signature_chrono_dial`, `signature_kinetic_thruster`).
 */
object LightspeedHudSignatureRenderer {

    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val rimPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val titleTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = Typeface.create("sans-serif-medium", Typeface.BOLD)
    }
    private val valueTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = Typeface.create("sans-serif-black", Typeface.BOLD)
        color = Color.WHITE
    }

    private val cardRect = RectF()
    private val fillRect = RectF()

    fun drawActionSignatureHud(
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

        val cardW = 292f * d
        val cardH = 68f * d
        val radius = 20f * d
        cardRect.set(cx - cardW / 2f, topY, cx + cardW / 2f, topY + cardH)
        LightspeedHudModernRenderer.drawModernGlassCapsule(canvas, cardRect, radius, spec, d)

        val tR = Color.red(spec.tintColor)
        val tG = Color.green(spec.tintColor)
        val tB = Color.blue(spec.tintColor)

        val badgeR = 20f * d
        val badgeCx = cardRect.left + 14f * d + badgeR
        val badgeCy = cardRect.centerY()
        fillPaint.shader = null
        fillPaint.color = Color.argb(46, tR, tG, tB)
        canvas.drawCircle(badgeCx, badgeCy, badgeR, fillPaint)
        rimPaint.strokeWidth = 1.1f * d
        rimPaint.color = Color.argb(150, tR, tG, tB)
        canvas.drawCircle(badgeCx, badgeCy, badgeR, rimPaint)

        val effectiveTitle = when (styleKey) {
            "signature_sonic_eq" -> if (title.isBlank()) "SONIC EQUALIZER" else title
            "signature_solar_lux" -> if (title.isBlank()) "SOLAR LUX" else title
            "signature_chrono_dial" -> if (title.isBlank()) "CHRONO SLEEP" else title
            "signature_kinetic_thruster" -> if (title.isBlank()) "KINETIC VECTOR" else title
            else -> title
        }
        LightspeedHudModernRenderer.drawSculptedSymbolIcon(
            canvas = canvas,
            title = effectiveTitle,
            value = value,
            fraction = fraction,
            cx = badgeCx,
            cy = badgeCy,
            sizePx = 19f * d,
            color = spec.tintColor,
            d = d
        )

        val contentLeft = badgeCx + badgeR + 12f * d
        val contentRight = cardRect.right - 14f * d
        val cleanVal = value.removePrefix("[").removeSuffix("]").trim()

        // Top row: Sub-telemetry + Primary Value
        valueTextPaint.textSize = 14.5f * d
        valueTextPaint.textAlign = Paint.Align.RIGHT
        val textBaselineY = cardRect.top + 26f * d
        canvas.drawText(cleanVal, contentRight, textBaselineY, valueTextPaint)

        val subTag = when (styleKey) {
            "signature_sonic_eq" -> {
                val db = if (fraction <= 0.01f) "-INF dB" else String.format(java.util.Locale.US, "%+.1f dB", (fraction - 0.8f) * 35f)
                "EQ SPECTRUM · $db"
            }
            "signature_solar_lux" -> {
                val nits = (10 + (fraction * fraction * 990f).roundToInt()).coerceIn(5, 1000)
                "PHOTOMETER · ~${nits} NITS"
            }
            "signature_chrono_dial" -> "SLEEP HORIZON · STEP ${stepIndex.coerceAtLeast(0) + 1}/${totalSteps.coerceAtLeast(1)}"
            else -> "THRUSTER · VELOCITY VECTOR"
        }
        titleTextPaint.color = Color.argb(215, 225, 236, 252)
        titleTextPaint.textSize = 9.5f * d
        titleTextPaint.textAlign = Paint.Align.LEFT
        val valW = valueTextPaint.measureText(cleanVal)
        val safeSub = LightspeedHudModernRenderer.ellipsizeText(
            subTag,
            titleTextPaint,
            (contentRight - valW - contentLeft - 8f * d).coerceAtLeast(36f * d)
        )
        canvas.drawText(safeSub, contentLeft, textBaselineY - 1f * d, titleTextPaint)

        // Bottom row: Action-Specific Custom Visualizer
        val vizTop = cardRect.top + 35f * d
        val vizBottom = cardRect.bottom - 11f * d
        val vizW = (contentRight - contentLeft).coerceAtLeast(40f * d)
        val vizH = (vizBottom - vizTop).coerceAtLeast(10f * d)

        when (styleKey) {
            "signature_sonic_eq" -> {
                val bars = 18
                val gap = 2.8f * d
                val barW = ((vizW - gap * (bars - 1)) / bars).coerceAtLeast(2f * d)
                val activeBars = (fraction * bars).roundToInt()
                for (i in 0 until bars) {
                    val wave = 0.35f + 0.65f * kotlin.math.abs(kotlin.math.sin((i * 0.55 + fraction * 6.0))).toFloat()
                    val bh = (vizH * wave).coerceIn(3.5f * d, vizH)
                    val bx = contentLeft + i * (barW + gap)
                    val by = vizBottom - bh
                    fillRect.set(bx, by, bx + barW, vizBottom)
                    fillPaint.shader = null
                    fillPaint.color = if (i < activeBars) spec.tintColor else Color.argb(48, 255, 255, 255)
                    canvas.drawRoundRect(fillRect, barW / 2f, barW / 2f, fillPaint)
                }
            }
            "signature_solar_lux" -> {
                val segments = 12
                val gap = 3.2f * d
                val segW = ((vizW - gap * (segments - 1)) / segments).coerceAtLeast(3f * d)
                val activeSegs = (fraction * segments).roundToInt()
                for (i in 0 until segments) {
                    val sx = contentLeft + i * (segW + gap)
                    fillRect.set(sx, vizTop + 3f * d, sx + segW, vizBottom - 2f * d)
                    fillPaint.shader = null
                    fillPaint.color = if (i < activeSegs) spec.tintColor else Color.argb(45, 255, 255, 255)
                    canvas.drawRoundRect(fillRect, 3f * d, 3f * d, fillPaint)
                }
            }
            "signature_chrono_dial" -> {
                val steps = totalSteps.coerceIn(4, 10)
                val lineY = (vizTop + vizBottom) * 0.5f
                rimPaint.color = Color.argb(60, 255, 255, 255)
                rimPaint.strokeWidth = 2.2f * d
                canvas.drawLine(contentLeft, lineY, contentRight, lineY, rimPaint)
                val activeX = contentLeft + vizW * fraction
                rimPaint.color = spec.tintColor
                rimPaint.strokeWidth = 2.6f * d
                canvas.drawLine(contentLeft, lineY, activeX, lineY, rimPaint)
                for (i in 0 until steps) {
                    val tx = contentLeft + (vizW * i.toFloat() / (steps - 1).coerceAtLeast(1))
                    val reached = i <= stepIndex
                    fillPaint.shader = null
                    fillPaint.color = if (reached) spec.tintColor else Color.argb(85, 255, 255, 255)
                    canvas.drawCircle(tx, lineY, if (i == stepIndex) 4.5f * d else 2.6f * d, fillPaint)
                }
            }
            else -> {
                // signature_kinetic_thruster
                val midX = contentLeft + vizW * 0.5f
                val barY = (vizTop + vizBottom) * 0.5f
                fillRect.set(contentLeft, barY - 4f * d, contentRight, barY + 4f * d)
                fillPaint.shader = null
                fillPaint.color = Color.argb(50, 255, 255, 255)
                canvas.drawRoundRect(fillRect, 4f * d, 4f * d, fillPaint)
                val isUp = value.contains("UP", ignoreCase = true) || value.contains("▲")
                val wingSpan = vizW * 0.46f
                if (isUp) {
                    fillRect.set(midX, barY - 4f * d, midX + wingSpan, barY + 4f * d)
                } else {
                    fillRect.set(midX - wingSpan, barY - 4f * d, midX, barY + 4f * d)
                }
                fillPaint.shader = null
                fillPaint.color = spec.tintColor
                canvas.drawRoundRect(fillRect, 4f * d, 4f * d, fillPaint)
                fillPaint.color = Color.WHITE
                canvas.drawCircle(midX, barY, 4.5f * d, fillPaint)
            }
        }
    }
}
