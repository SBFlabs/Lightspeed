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
 * Modern Minimal Symbol HUD Renderers (`modern_symbol_pill`, `modern_floating_orb`, `modern_slim_slider`).
 *
 * Combines 100%-opacity 12-Theme screenshot blur and frosted glass optics with sculpted,
 * filled geometric vector iconography (Speaker + acoustic waves, Sunburst, Crescent Moon,
 * Chrono Watch, Dual Seek Triangles, Kinetic Chevrons, and Isometric Layers).
 */
object LightspeedHudModernRenderer {

    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val rimPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val glowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val iconFillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val iconStrokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }
    private val titleTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = Typeface.create("sans-serif-medium", Typeface.BOLD)
    }
    private val valueTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = Typeface.create("sans-serif-black", Typeface.BOLD)
        color = Color.WHITE
    }

    private val cardRect = RectF()
    private val trackRect = RectF()
    private val fillRect = RectF()
    private val arcRect = RectF()
    private val cardPath = Path()
    private val fillClipPath = Path()
    private val iconPathA = Path()
    private val iconPathB = Path()

    /**
     * Style 6: Modern Symbol Pill (`modern_symbol_pill`)
     * Sleek stadium capsule with left tinted circular symbol badge, fluid pill progress bar,
     * clean title, and bold tabular value readout.
     */
    fun drawModernSymbolPill(
        canvas: Canvas,
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
        } else 1f

        val cardW = 276f * d
        val cardH = 56f * d
        val radius = cardH / 2f
        cardRect.set(cx - cardW / 2f, topY, cx + cardW / 2f, topY + cardH)

        drawModernGlassCapsule(canvas, cardRect, radius, spec, d)

        val tR = Color.red(spec.tintColor)
        val tG = Color.green(spec.tintColor)
        val tB = Color.blue(spec.tintColor)

        // 1. Left Circular Symbol Well
        val badgeR = 18.5f * d
        val badgeCx = cardRect.left + (10f * d) + badgeR
        val badgeCy = cardRect.centerY()
        fillPaint.shader = null
        fillPaint.color = Color.argb(46, tR, tG, tB)
        canvas.drawCircle(badgeCx, badgeCy, badgeR, fillPaint)
        rimPaint.shader = null
        rimPaint.strokeWidth = 1f * d
        rimPaint.color = Color.argb(110, tR, tG, tB)
        canvas.drawCircle(badgeCx, badgeCy, badgeR, rimPaint)

        drawSculptedSymbolIcon(
            canvas = canvas,
            title = title,
            value = value,
            fraction = fraction,
            cx = badgeCx,
            cy = badgeCy,
            sizePx = 19f * d,
            color = spec.tintColor,
            d = d
        )

        // 2. Right Content Area (Title + Value on top, Fluid Pill Bar on bottom)
        val contentLeft = badgeCx + badgeR + (12f * d)
        val contentRight = cardRect.right - (16f * d)
        val contentW = (contentRight - contentLeft).coerceAtLeast(40f * d)

        // Value Readout (Top-Right)
        valueTextPaint.textSize = 14.5f * d
        valueTextPaint.textAlign = Paint.Align.RIGHT
        val cleanVal = value.removePrefix("[").removeSuffix("]").trim()
        val valW = valueTextPaint.measureText(cleanVal)
        val textBaselineY = if (hasGauge) cardRect.top + (24f * d) else cardRect.centerY() + (5f * d)
        canvas.drawText(cleanVal, contentRight, textBaselineY, valueTextPaint)

        // Title Readout (Top-Left)
        titleTextPaint.color = Color.argb(225, 235, 242, 255)
        titleTextPaint.textSize = 10.5f * d
        titleTextPaint.textAlign = Paint.Align.LEFT
        val maxTitleW = (contentW - valW - (8f * d)).coerceAtLeast(30f * d)
        val safeTitle = ellipsizeText(title, titleTextPaint, maxTitleW)
        canvas.drawText(safeTitle, contentLeft, textBaselineY - (1f * d), titleTextPaint)

        // 3. Smooth Fluid Pill Progress Bar
        if (hasGauge) {
            val barH = 7f * d
            val barTop = cardRect.top + (34.5f * d)
            trackRect.set(contentLeft, barTop, contentRight, barTop + barH)
            fillPaint.shader = null
            fillPaint.color = Color.argb(42, 255, 255, 255)
            canvas.drawRoundRect(trackRect, barH / 2f, barH / 2f, fillPaint)

            val fillW = (contentW * fraction).coerceAtLeast(if (fraction > 0f) barH else 0f)
            if (fillW > 0f) {
                fillRect.set(contentLeft, barTop, contentLeft + fillW, barTop + barH)
                fillPaint.shader = LinearGradient(
                    fillRect.left, fillRect.top, fillRect.right, fillRect.top,
                    Color.argb(215, tR, tG, tB),
                    spec.tintColor,
                    Shader.TileMode.CLAMP
                )
                canvas.drawRoundRect(fillRect, barH / 2f, barH / 2f, fillPaint)
                fillPaint.shader = null

                // Subtle white specular thumb dot inside the tip of the pill bar
                val thumbCx = (fillRect.right - barH / 2f).coerceAtLeast(contentLeft + barH / 2f)
                fillPaint.color = Color.WHITE
                canvas.drawCircle(thumbCx, barTop + barH / 2f, 2.2f * d, fillPaint)
            }
        }
    }

    /**
     * Style 7: Modern Symbol Orb (`modern_floating_orb`)
     * Compact circular glass button with smooth perimeter progress arc, sculpted center-top
     * symbol badge, and bold readout underneath.
     */
    fun drawModernFloatingOrb(
        canvas: Canvas,
        title: String,
        value: String,
        stepIndex: Int,
        totalSteps: Int,
        cx: Float,
        cy: Float,
        primaryColor: Int,
        d: Float,
        useCentralTheme: Boolean = true,
        colorMode: String = if (useCentralTheme) LightspeedPreferences.COCKPIT_COLOR_MODE_THEME_12 else LightspeedPreferences.COCKPIT_COLOR_MODE_M3_DYNAMIC
    ) {
        val spec = LightspeedHudThemeHelper.resolveSpec(useCentralTheme, primaryColor, d, colorMode)
        val hasGauge = totalSteps > 0 && stepIndex >= 0
        val fraction = if (hasGauge) {
            (stepIndex.toFloat() / (totalSteps - 1).coerceAtLeast(1)).coerceIn(0f, 1f)
        } else 1f

        val orbR = 58f * d
        val pR = Color.red(spec.panelColor)
        val pG = Color.green(spec.panelColor)
        val pB = Color.blue(spec.panelColor)
        val tR = Color.red(spec.tintColor)
        val tG = Color.green(spec.tintColor)
        val tB = Color.blue(spec.tintColor)
        val pA = if (spec.isCentralTheme) {
            (spec.panelAlpha * 255).toInt().coerceIn(38, 250)
        } else {
            (spec.panelAlpha * 255).toInt().coerceIn(210, 245)
        }

        if (spec.glowStrength > 0.05f) {
            val gC = spec.glowColor
            glowPaint.color = Color.argb(
                (spec.glowStrength * 115).toInt().coerceIn(0, 255),
                Color.red(gC), Color.green(gC), Color.blue(gC)
            )
            glowPaint.strokeWidth = spec.borderWidthPx * (1.4f + spec.glowStrength * 1.4f)
            canvas.drawCircle(cx, cy, orbR, glowPaint)
        }

        // 0. Clipped 100% Screenshot Blur Layer
        LightspeedHudRenderer.drawHudBackdropLayer(canvas, null, cx, cy, orbR, spec)

        // 1. Frosted Glass Circle
        fillPaint.shader = LinearGradient(
            cx, cy - orbR, cx, cy + orbR,
            Color.argb(pA, (pR + tR * 0.12f).toInt().coerceIn(0, 255), (pG + tG * 0.12f).toInt().coerceIn(0, 255), (pB + tB * 0.12f).toInt().coerceIn(0, 255)),
            Color.argb((pA * 1.05f).toInt().coerceIn(0, 255), pR, pG, pB),
            Shader.TileMode.CLAMP
        )
        canvas.drawCircle(cx, cy, orbR, fillPaint)
        fillPaint.shader = null

        // 2. Refractive Outer Rim
        rimPaint.shader = null
        rimPaint.strokeWidth = spec.borderWidthPx.coerceAtLeast(1.1f * d)
        rimPaint.color = spec.borderColor
        canvas.drawCircle(cx, cy, orbR, rimPaint)

        // 3. Smooth Perimeter Progress Ring
        if (hasGauge) {
            val ringR = orbR - (6.5f * d)
            arcRect.set(cx - ringR, cy - ringR, cx + ringR, cy + ringR)
            iconStrokePaint.strokeWidth = 4.2f * d
            iconStrokePaint.color = Color.argb(38, 255, 255, 255)
            canvas.drawArc(arcRect, 135f, 270f, false, iconStrokePaint)

            val sweep = 270f * fraction
            if (sweep > 0.5f) {
                iconStrokePaint.color = spec.tintColor
                canvas.drawArc(arcRect, 135f, sweep, false, iconStrokePaint)
            }
        }

        // 4. Sculpted Symbol in Soft Tinted Well (Upper Center)
        val wellR = 17.5f * d
        val wellCy = cy - (13f * d)
        fillPaint.color = Color.argb(42, tR, tG, tB)
        canvas.drawCircle(cx, wellCy, wellR, fillPaint)
        drawSculptedSymbolIcon(
            canvas = canvas,
            title = title,
            value = value,
            fraction = fraction,
            cx = cx,
            cy = wellCy,
            sizePx = 18.5f * d,
            color = spec.tintColor,
            d = d
        )

        // 5. Bold Value Readout & Micro Label (Lower Center)
        val cleanVal = value.removePrefix("[").removeSuffix("]").trim()
        valueTextPaint.textSize = 16.5f * d
        valueTextPaint.textAlign = Paint.Align.CENTER
        canvas.drawText(cleanVal, cx, cy + (22f * d), valueTextPaint)

        titleTextPaint.color = Color.argb(185, 225, 235, 250)
        titleTextPaint.textSize = 8.2f * d
        titleTextPaint.textAlign = Paint.Align.CENTER
        val shortLabel = ellipsizeText(shortCategoryTitle(title), titleTextPaint, orbR * 1.25f)
        canvas.drawText(shortLabel, cx, cy + (34.5f * d), titleTextPaint)
    }

    /**
     * Style 8: Modern Glass Slider (`modern_slim_slider`)
     * Full-capsule liquid fill slider where the entire glass pill fills horizontally
     * behind the sculpted vector symbol and bold readout.
     */
    fun drawModernSlimSlider(
        canvas: Canvas,
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
        } else 1f

        val cardW = 264f * d
        val cardH = 48f * d
        val radius = cardH / 2f
        cardRect.set(cx - cardW / 2f, topY, cx + cardW / 2f, topY + cardH)

        drawModernGlassCapsule(canvas, cardRect, radius, spec, d)

        val tR = Color.red(spec.tintColor)
        val tG = Color.green(spec.tintColor)
        val tB = Color.blue(spec.tintColor)

        // Full-Capsule Interior Liquid Fill clipped to stadium pill
        if (hasGauge && fraction > 0f) {
            val fillW = (cardW * fraction).coerceIn(0f, cardW)
            fillClipPath.rewind()
            fillClipPath.addRoundRect(cardRect, radius, radius, Path.Direction.CW)
            canvas.save()
            canvas.clipPath(fillClipPath)
            fillRect.set(cardRect.left, cardRect.top, cardRect.left + fillW, cardRect.bottom)
            fillPaint.shader = LinearGradient(
                fillRect.left, fillRect.top, fillRect.right, fillRect.top,
                Color.argb(65, tR, tG, tB),
                Color.argb(145, tR, tG, tB),
                Shader.TileMode.CLAMP
            )
            canvas.drawRect(fillRect, fillPaint)
            fillPaint.shader = null
            if (fraction in 0.03f..0.97f) {
                rimPaint.strokeWidth = 1.5f * d
                rimPaint.color = Color.argb(190, 255, 255, 255)
                canvas.drawLine(fillRect.right, cardRect.top + 4f * d, fillRect.right, cardRect.bottom - 4f * d, rimPaint)
            }
            canvas.restore()
        }

        // Left Sculpted Symbol
        val iconCx = cardRect.left + (25f * d)
        val iconCy = cardRect.centerY()
        drawSculptedSymbolIcon(
            canvas = canvas,
            title = title,
            value = value,
            fraction = fraction,
            cx = iconCx,
            cy = iconCy,
            sizePx = 18.5f * d,
            color = Color.WHITE,
            d = d
        )

        // Right Bold Value Readout
        val cleanVal = value.removePrefix("[").removeSuffix("]").trim()
        valueTextPaint.textSize = 15f * d
        valueTextPaint.textAlign = Paint.Align.RIGHT
        val valRight = cardRect.right - (18f * d)
        val valW = valueTextPaint.measureText(cleanVal)
        val textY = cardRect.centerY() + (5.2f * d)
        canvas.drawText(cleanVal, valRight, textY, valueTextPaint)

        // Center-Left Title
        titleTextPaint.color = Color.argb(235, 245, 248, 255)
        titleTextPaint.textSize = 11f * d
        titleTextPaint.textAlign = Paint.Align.LEFT
        val titleLeft = iconCx + (16f * d)
        val maxTitleW = (valRight - valW - titleLeft - (10f * d)).coerceAtLeast(30f * d)
        val safeTitle = ellipsizeText(title, titleTextPaint, maxTitleW)
        canvas.drawText(safeTitle, titleLeft, textY - (0.8f * d), titleTextPaint)
    }

    internal fun drawModernGlassCapsule(
        canvas: Canvas,
        rect: RectF,
        radius: Float,
        spec: HudThemeSpec,
        d: Float
    ) {
        cardPath.rewind()
        cardPath.addRoundRect(rect, radius, radius, Path.Direction.CW)

        if (spec.glowStrength > 0.05f) {
            val gC = spec.glowColor
            glowPaint.color = Color.argb(
                (spec.glowStrength * 115).toInt().coerceIn(0, 255),
                Color.red(gC), Color.green(gC), Color.blue(gC)
            )
            glowPaint.strokeWidth = spec.borderWidthPx * (1.4f + spec.glowStrength * 1.4f)
            canvas.drawPath(cardPath, glowPaint)
        }

        // 0. 100%-Opacity Clipped Screenshot Blur Layer
        LightspeedHudRenderer.drawHudBackdropLayer(canvas, cardPath, 0f, 0f, 0f, spec)

        // 1. 12-Theme Frosted Glass Backplane
        val pR = Color.red(spec.panelColor)
        val pG = Color.green(spec.panelColor)
        val pB = Color.blue(spec.panelColor)
        val tR = Color.red(spec.tintColor)
        val tG = Color.green(spec.tintColor)
        val tB = Color.blue(spec.tintColor)
        val pA = if (spec.isCentralTheme) {
            (spec.panelAlpha * 255).toInt().coerceIn(38, 250)
        } else {
            (spec.panelAlpha * 255).toInt().coerceIn(210, 245)
        }

        fillPaint.shader = LinearGradient(
            rect.left, rect.top, rect.left, rect.bottom,
            Color.argb(pA, (pR + tR * 0.12f).toInt().coerceIn(0, 255), (pG + tG * 0.12f).toInt().coerceIn(0, 255), (pB + tB * 0.12f).toInt().coerceIn(0, 255)),
            Color.argb((pA * 1.05f).toInt().coerceIn(0, 255), pR, pG, pB),
            Shader.TileMode.CLAMP
        )
        canvas.drawPath(cardPath, fillPaint)
        fillPaint.shader = null

        // 2. Refractive Capsule Border
        rimPaint.shader = LinearGradient(
            rect.left, rect.top, rect.right, rect.bottom,
            intArrayOf(
                Color.argb(175, 255, 255, 255),
                spec.borderColor,
                Color.argb(65, 255, 255, 255),
                Color.argb(Color.alpha(spec.borderColor).coerceAtLeast(130), tR, tG, tB)
            ),
            floatArrayOf(0f, 0.35f, 0.7f, 1f),
            Shader.TileMode.CLAMP
        )
        rimPaint.strokeWidth = spec.borderWidthPx.coerceAtLeast(1.1f * d)
        canvas.drawPath(cardPath, rimPaint)
        rimPaint.shader = null
    }

    /**
     * Draws sculpted, filled geometric vector symbols (SF-Symbols / Material Rounded quality).
     */
    internal fun drawSculptedSymbolIcon(
        canvas: Canvas,
        title: String,
        value: String,
        fraction: Float,
        cx: Float,
        cy: Float,
        sizePx: Float,
        color: Int,
        d: Float
    ) {
        val upper = title.uppercase()
        val r = sizePx * 0.5f
        iconFillPaint.color = color
        iconStrokePaint.color = color

        when {
            upper.contains("VOLUME") || upper.contains("AUDIO") || upper.contains("SOUND") -> {
                // Sculpted Filled Speaker Horn + Dynamic Acoustic Waves
                val spkLeft = cx - r * 0.78f
                val spkMid = cx - r * 0.42f
                val spkRight = cx - r * 0.02f
                val bodyHalfH = r * 0.28f
                val hornHalfH = r * 0.64f

                iconPathA.rewind()
                iconPathA.moveTo(spkLeft, cy - bodyHalfH)
                iconPathA.lineTo(spkMid, cy - bodyHalfH)
                iconPathA.lineTo(spkRight, cy - hornHalfH)
                iconPathA.lineTo(spkRight, cy + hornHalfH)
                iconPathA.lineTo(spkMid, cy + bodyHalfH)
                iconPathA.lineTo(spkLeft, cy + bodyHalfH)
                iconPathA.close()
                canvas.drawPath(iconPathA, iconFillPaint)

                val isMuted = fraction <= 0.01f || value.startsWith("0%")
                if (isMuted) {
                    iconStrokePaint.strokeWidth = 1.9f * d
                    val x0 = cx + r * 0.20f
                    val x1 = cx + r * 0.68f
                    val y0 = cy - r * 0.24f
                    val y1 = cy + r * 0.24f
                    canvas.drawLine(x0, y0, x1, y1, iconStrokePaint)
                    canvas.drawLine(x0, y1, x1, y0, iconStrokePaint)
                } else {
                    iconStrokePaint.strokeWidth = 1.8f * d
                    val w1 = r * 0.34f
                    arcRect.set(spkRight - w1, cy - w1, spkRight + w1, cy + w1)
                    canvas.drawArc(arcRect, -42f, 84f, false, iconStrokePaint)
                    if (fraction > 0.33f) {
                        val w2 = r * 0.64f
                        arcRect.set(spkRight - w2, cy - w2, spkRight + w2, cy + w2)
                        canvas.drawArc(arcRect, -45f, 90f, false, iconStrokePaint)
                    }
                }
            }

            upper.contains("DIM") || upper.contains("SUB-ZERO") || upper.contains("EYE CARE") || upper.contains("PIXEL") -> {
                // Sculpted Crescent Moon via Path Difference + Star Glint
                val moonR = r * 0.68f
                iconPathA.rewind()
                iconPathA.addCircle(cx - r * 0.05f, cy, moonR, Path.Direction.CW)
                iconPathB.rewind()
                iconPathB.addCircle(cx + r * 0.30f, cy - r * 0.22f, moonR * 0.82f, Path.Direction.CW)
                iconPathA.op(iconPathB, Path.Op.DIFFERENCE)
                canvas.drawPath(iconPathA, iconFillPaint)

                // Star glint
                canvas.drawCircle(cx + r * 0.48f, cy - r * 0.38f, 1.5f * d, iconFillPaint)
            }

            upper.contains("BRIGHT") || upper.contains("LUX") || upper.contains("BOOST") -> {
                // Filled Sun Core + 8 Precision Rounded Rays
                val coreR = r * 0.38f
                canvas.drawCircle(cx, cy, coreR, iconFillPaint)
                iconStrokePaint.strokeWidth = 1.8f * d
                val rayInner = r * 0.58f
                val rayOuter = r * (0.78f + 0.16f * fraction)
                for (i in 0 until 8) {
                    val rad = Math.toRadians(i * 45.0)
                    val c = cos(rad).toFloat()
                    val s = sin(rad).toFloat()
                    canvas.drawLine(
                        cx + rayInner * c, cy + rayInner * s,
                        cx + rayOuter * c, cy + rayOuter * s,
                        iconStrokePaint
                    )
                }
            }

            upper.contains("TIMEOUT") || upper.contains("DARK") || upper.contains("SLEEP") || upper.contains("AWAKE") -> {
                // Chrono Watch Face + Hands + Top Crown
                val faceR = r * 0.68f
                iconStrokePaint.strokeWidth = 1.9f * d
                canvas.drawCircle(cx, cy + r * 0.06f, faceR, iconStrokePaint)
                // Top chrono button
                canvas.drawLine(cx - r * 0.22f, cy - r * 0.82f, cx + r * 0.22f, cy - r * 0.82f, iconStrokePaint)
                // Hands
                canvas.drawLine(cx, cy + r * 0.06f, cx, cy - r * 0.32f, iconStrokePaint)
                canvas.drawLine(cx, cy + r * 0.06f, cx + r * 0.28f, cy + r * 0.22f, iconStrokePaint)
            }

            upper.contains("SCROLL") -> {
                // Dual Bold Rounded Kinetic Chevrons
                iconStrokePaint.strokeWidth = 2.1f * d
                val isUp = value.contains("UP") || !value.contains("DOWN")
                val dir = if (isUp) -1f else 1f
                val spanX = r * 0.52f
                val stepY = r * 0.30f
                // First chevron
                iconPathA.rewind()
                iconPathA.moveTo(cx - spanX, cy - dir * (stepY * 0.2f))
                iconPathA.lineTo(cx, cy + dir * (stepY * 1.1f))
                iconPathA.lineTo(cx + spanX, cy - dir * (stepY * 0.2f))
                canvas.drawPath(iconPathA, iconStrokePaint)
                // Second chevron
                iconPathA.rewind()
                iconPathA.moveTo(cx - spanX, cy - dir * (stepY * 1.3f))
                iconPathA.lineTo(cx, cy)
                iconPathA.lineTo(cx + spanX, cy - dir * (stepY * 1.3f))
                canvas.drawPath(iconPathA, iconStrokePaint)
            }

            upper.contains("MEDIA") || upper.contains("TRACK") || upper.contains("SKIP") -> {
                // Dual Sculpted Fast-Forward Play Triangles
                val triH = r * 0.54f
                val triW = r * 0.52f
                iconPathA.rewind()
                iconPathA.moveTo(cx - r * 0.58f, cy - triH)
                iconPathA.lineTo(cx - r * 0.58f + triW, cy)
                iconPathA.lineTo(cx - r * 0.58f, cy + triH)
                iconPathA.close()
                canvas.drawPath(iconPathA, iconFillPaint)

                iconPathA.rewind()
                iconPathA.moveTo(cx + r * 0.02f, cy - triH)
                iconPathA.lineTo(cx + r * 0.02f + triW, cy)
                iconPathA.lineTo(cx + r * 0.02f, cy + triH)
                iconPathA.close()
                canvas.drawPath(iconPathA, iconFillPaint)
            }

            else -> {
                // Sculpted Isometric Stacked Layers (Gear Set / System Action)
                val w = r * 0.72f
                val h = r * 0.38f
                iconPathA.rewind()
                iconPathA.moveTo(cx, cy - r * 0.62f)
                iconPathA.lineTo(cx + w, cy - r * 0.62f + h)
                iconPathA.lineTo(cx, cy - r * 0.62f + h * 2f)
                iconPathA.lineTo(cx - w, cy - r * 0.62f + h)
                iconPathA.close()
                canvas.drawPath(iconPathA, iconFillPaint)

                iconStrokePaint.strokeWidth = 1.9f * d
                iconPathB.rewind()
                iconPathB.moveTo(cx - w, cy + r * 0.14f)
                iconPathB.lineTo(cx, cy + r * 0.14f + h)
                iconPathB.lineTo(cx + w, cy + r * 0.14f)
                canvas.drawPath(iconPathB, iconStrokePaint)
            }
        }
    }

    private fun shortCategoryTitle(title: String): String {
        val u = title.uppercase()
        return when {
            u.contains("VOLUME") -> "VOLUME"
            u.contains("BRIGHT") -> "BRIGHTNESS"
            u.contains("DARK") || u.contains("TIMEOUT") -> "TIMEOUT"
            u.contains("SCROLL") -> "SCROLL"
            u.contains("GEAR") -> "GEAR SET"
            else -> title
        }
    }

    internal fun ellipsizeText(text: String, paint: Paint, maxWidth: Float): String {
        if (paint.measureText(text) <= maxWidth) return text
        var t = text
        while (t.length > 1 && paint.measureText("$t…") > maxWidth) {
            t = t.dropLast(1)
        }
        return "$t…"
    }
}
