package com.sbf.lightspeed

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import kotlin.math.roundToInt

/**
 * Geometric Canvas Path vector icons & boundary feathering helpers for the Category Cruise HUD
 * and App Long-Press Menu. Strictly zero keyboard Unicode/emojis.
 */
object LightspeedHudVectorIcons {

    const val DEFAULT_EDGE_FEATHER_RAMP_DP = 20f
    private const val EDGE_FEATHER_EPSILON = 0.004f

    private val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }

    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }

    private val ditherOutPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        xfermode = PorterDuffXfermode(PorterDuff.Mode.DST_OUT)
    }

    private val featherOutPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        xfermode = PorterDuffXfermode(PorterDuff.Mode.DST_OUT)
    }

    private val tmpRect = RectF()
    private val tmpPath = Path()

    /** Crisp 4-point spark star without relying on system Unicode font glyphs. */
    fun drawVectorDiamondStar(canvas: Canvas, cx: Float, cy: Float, r: Float, color: Int) {
        fillPaint.color = color
        tmpPath.reset()
        tmpPath.moveTo(cx, cy - r)
        tmpPath.quadTo(cx + r * 0.22f, cy - r * 0.22f, cx + r, cy)
        tmpPath.quadTo(cx + r * 0.22f, cy + r * 0.22f, cx, cy + r)
        tmpPath.quadTo(cx - r * 0.22f, cy + r * 0.22f, cx - r, cy)
        tmpPath.quadTo(cx - r * 0.22f, cy - r * 0.22f, cx, cy - r)
        tmpPath.close()
        canvas.drawPath(tmpPath, fillPaint)
    }

    /** Crisp symmetric close X cross drawn geometrically. */
    fun drawVectorClose(canvas: Canvas, cx: Float, cy: Float, r: Float, color: Int, strokeW: Float) {
        strokePaint.color = color
        strokePaint.strokeWidth = strokeW
        canvas.drawLine(cx - r, cy - r, cx + r, cy + r, strokePaint)
        canvas.drawLine(cx + r, cy - r, cx - r, cy + r, strokePaint)
    }

    /** Crisp circular refresh arrow drawn geometrically (replaces ↻ glyph). */
    fun drawVectorRefresh(canvas: Canvas, cx: Float, cy: Float, r: Float, color: Int, strokeW: Float) {
        strokePaint.color = color
        strokePaint.strokeWidth = strokeW
        tmpRect.set(cx - r, cy - r, cx + r, cy + r)
        canvas.drawArc(tmpRect, -45f, 285f, false, strokePaint)
        val tipRad = Math.toRadians(-45.0)
        val tipX = cx + (kotlin.math.cos(tipRad) * r).toFloat()
        val tipY = cy + (kotlin.math.sin(tipRad) * r).toFloat()
        val arm = r * 0.48f
        canvas.drawLine(tipX, tipY, tipX - arm * 0.95f, tipY - arm * 0.1f, strokePaint)
        canvas.drawLine(tipX, tipY, tipX + arm * 0.1f, tipY - arm * 0.95f, strokePaint)
    }

    /** Crisp clock face vector icon (replaces ◷ glyph). */
    fun drawVectorClock(canvas: Canvas, cx: Float, cy: Float, r: Float, color: Int, strokeW: Float) {
        strokePaint.color = color
        strokePaint.strokeWidth = strokeW
        canvas.drawCircle(cx, cy, r, strokePaint)
        canvas.drawLine(cx, cy, cx, cy - r * 0.56f, strokePaint)
        canvas.drawLine(cx, cy, cx + r * 0.45f, cy, strokePaint)
    }

    /**
     * Geometric arrow or chevron vector (0=DOWN, 1=UP, 2=RIGHT, 3=LEFT).
     * When [chevronOnly] is true, draws a clean open chevron; otherwise a full shafted arrow or triangle.
     */
    fun drawVectorArrow(
        canvas: Canvas,
        cx: Float,
        cy: Float,
        r: Float,
        direction: Int,
        color: Int,
        strokeW: Float,
        chevronOnly: Boolean = false,
        filledTriangle: Boolean = false
    ) {
        if (filledTriangle) {
            fillPaint.color = color
            tmpPath.reset()
            when (direction) {
                0 -> { // DOWN
                    tmpPath.moveTo(cx - r * 0.8f, cy - r * 0.45f)
                    tmpPath.lineTo(cx + r * 0.8f, cy - r * 0.45f)
                    tmpPath.lineTo(cx, cy + r * 0.65f)
                }
                1 -> { // UP
                    tmpPath.moveTo(cx - r * 0.8f, cy + r * 0.45f)
                    tmpPath.lineTo(cx + r * 0.8f, cy + r * 0.45f)
                    tmpPath.lineTo(cx, cy - r * 0.65f)
                }
                3 -> { // LEFT
                    tmpPath.moveTo(cx + r * 0.55f, cy - r * 0.8f)
                    tmpPath.lineTo(cx + r * 0.55f, cy + r * 0.8f)
                    tmpPath.lineTo(cx - r * 0.65f, cy)
                }
                else -> { // RIGHT
                    tmpPath.moveTo(cx - r * 0.55f, cy - r * 0.8f)
                    tmpPath.lineTo(cx - r * 0.55f, cy + r * 0.8f)
                    tmpPath.lineTo(cx + r * 0.65f, cy)
                }
            }
            tmpPath.close()
            canvas.drawPath(tmpPath, fillPaint)
            return
        }

        strokePaint.color = color
        strokePaint.strokeWidth = strokeW
        when (direction) {
            0 -> { // DOWN
                if (!chevronOnly) canvas.drawLine(cx, cy - r * 0.85f, cx, cy + r * 0.72f, strokePaint)
                canvas.drawLine(cx - r * 0.58f, cy + r * 0.15f, cx, cy + r * 0.78f, strokePaint)
                canvas.drawLine(cx + r * 0.58f, cy + r * 0.15f, cx, cy + r * 0.78f, strokePaint)
            }
            1 -> { // UP
                if (!chevronOnly) canvas.drawLine(cx, cy + r * 0.85f, cx, cy - r * 0.72f, strokePaint)
                canvas.drawLine(cx - r * 0.58f, cy - r * 0.15f, cx, cy - r * 0.78f, strokePaint)
                canvas.drawLine(cx + r * 0.58f, cy - r * 0.15f, cx, cy - r * 0.78f, strokePaint)
            }
            3 -> { // LEFT
                if (!chevronOnly) canvas.drawLine(cx + r * 0.85f, cy, cx - r * 0.72f, cy, strokePaint)
                canvas.drawLine(cx - r * 0.15f, cy - r * 0.58f, cx - r * 0.78f, cy, strokePaint)
                canvas.drawLine(cx - r * 0.15f, cy + r * 0.58f, cx - r * 0.78f, cy, strokePaint)
            }
            else -> { // RIGHT
                if (!chevronOnly) canvas.drawLine(cx - r * 0.85f, cy, cx + r * 0.72f, cy, strokePaint)
                val tipX = if (chevronOnly) cx + r * 0.45f else cx + r * 0.78f
                val backX = if (chevronOnly) cx - r * 0.35f else cx + r * 0.15f
                canvas.drawLine(backX, cy - r * 0.58f, tipX, cy, strokePaint)
                canvas.drawLine(backX, cy + r * 0.58f, tipX, cy, strokePaint)
            }
        }
    }

    /**
     * Maps distance in pixels from a scrollable edge (0px..[rampDp]) to a smooth [0f..1f] fade strength.
     */
    fun computeEdgeFeatherStrength(
        edgeDistancePx: Float,
        density: Float,
        rampDp: Float = DEFAULT_EDGE_FEATHER_RAMP_DP
    ): Float {
        if (edgeDistancePx <= 0f) return 0f
        val rampPx = (rampDp * density).coerceAtLeast(1f)
        return (edgeDistancePx / rampPx).coerceIn(0f, 1f)
    }

    /**
     * Convenience wrapper that computes smooth progressive top/bottom alpha ramps from
     * [scrollOffset] and [maxScroll] before delegating to [applyVerticalEdgeFeathering].
     */
    fun applyScrollEdgeFeathering(
        canvas: Canvas,
        left: Float,
        top: Float,
        right: Float,
        bottom: Float,
        scrollOffset: Float,
        maxScroll: Float,
        featherTopPx: Float,
        featherBottomPx: Float,
        density: Float,
        seed: Int = 1337,
        rampDp: Float = DEFAULT_EDGE_FEATHER_RAMP_DP
    ) {
        if (maxScroll <= 0.5f) return
        val topAlpha = computeEdgeFeatherStrength(scrollOffset, density, rampDp)
        val bottomAlpha = computeEdgeFeatherStrength(maxScroll - scrollOffset, density, rampDp)
        if (topAlpha <= EDGE_FEATHER_EPSILON && bottomAlpha <= EDGE_FEATHER_EPSILON) return
        applyVerticalEdgeFeathering(
            canvas = canvas,
            left = left,
            top = top,
            right = right,
            bottom = bottom,
            featherTopPx = featherTopPx,
            featherBottomPx = featherBottomPx,
            density = density,
            seed = seed,
            topAlpha = topAlpha,
            bottomAlpha = bottomAlpha
        )
    }

    /**
     * Applies PorterDuff.Mode.DST_OUT vertical alpha-gradient feathering AND a subtle
     * procedural pixel-dither / stardust diffusion band at the top and/or bottom of a hardware layer.
     */
    fun applyVerticalEdgeFeathering(
        canvas: Canvas,
        left: Float,
        top: Float,
        right: Float,
        bottom: Float,
        featherTopPx: Float,
        featherBottomPx: Float,
        density: Float,
        seed: Int = 1337,
        topAlpha: Float = 1f,
        bottomAlpha: Float = 1f
    ) {
        val w = right - left
        val h = bottom - top
        if (w <= 4f || h <= 4f) return

        val topStrength = topAlpha.coerceIn(0f, 1f)
        val botStrength = bottomAlpha.coerceIn(0f, 1f)

        // 1. Top edge vertical fade + stardust dither
        if (featherTopPx > 2f && topStrength > EDGE_FEATHER_EPSILON && featherTopPx < h * 0.6f) {
            val fTop = top
            val fBot = top + featherTopPx
            val maxA = (255f * topStrength).roundToInt().coerceIn(0, 255)
            val midA = (140f * topStrength).roundToInt().coerceIn(0, 255)
            featherOutPaint.shader = LinearGradient(
                left, fTop, left, fBot,
                intArrayOf(Color.argb(maxA, 0, 0, 0), Color.argb(midA, 0, 0, 0), Color.TRANSPARENT),
                floatArrayOf(0f, 0.42f, 1f),
                Shader.TileMode.CLAMP
            )
            canvas.drawRect(left, fTop, right, fBot, featherOutPaint)
            featherOutPaint.shader = null
            drawProceduralDitherBand(canvas, left, fTop, right, fBot, density, fadeOutUp = true, seed = seed, strength = topStrength)
        }

        // 2. Bottom edge vertical fade + stardust dither
        if (featherBottomPx > 2f && botStrength > EDGE_FEATHER_EPSILON && featherBottomPx < h * 0.6f) {
            val fTop = bottom - featherBottomPx
            val fBot = bottom
            val maxA = (255f * botStrength).roundToInt().coerceIn(0, 255)
            val midA = (140f * botStrength).roundToInt().coerceIn(0, 255)
            featherOutPaint.shader = LinearGradient(
                left, fTop, left, fBot,
                intArrayOf(Color.TRANSPARENT, Color.argb(midA, 0, 0, 0), Color.argb(maxA, 0, 0, 0)),
                floatArrayOf(0f, 0.58f, 1f),
                Shader.TileMode.CLAMP
            )
            canvas.drawRect(left, fTop, right, fBot, featherOutPaint)
            featherOutPaint.shader = null
            drawProceduralDitherBand(canvas, left, fTop, right, fBot, density, fadeOutUp = false, seed = seed + 97, strength = botStrength)
        }
    }

    private fun drawProceduralDitherBand(
        canvas: Canvas,
        left: Float,
        top: Float,
        right: Float,
        bottom: Float,
        density: Float,
        fadeOutUp: Boolean,
        seed: Int,
        strength: Float = 1f
    ) {
        val w = right - left
        val bandH = (bottom - top).coerceAtLeast(1f)
        val dotCount = ((w / (6f * density)).toInt() * 3).coerceIn(24, 160)
        var s = seed * 1103515245 + 12345
        for (i in 0 until dotCount) {
            s = s * 1103515245 + 12345
            val rx = ((s ushr 16) and 0x7FFF) / 32767f
            s = s * 1103515245 + 12345
            val ry = ((s ushr 16) and 0x7FFF) / 32767f
            val px = left + rx * w
            val py = top + ry * bandH
            val edgeProximity = if (fadeOutUp) (1f - ry) else ry
            val alpha = (edgeProximity * 165f * strength).toInt().coerceIn(0, 185)
            if (alpha <= 0) continue
            ditherOutPaint.color = Color.argb(alpha, 0, 0, 0)
            val r = (0.75f + (i % 3) * 0.45f) * density
            canvas.drawCircle(px, py, r, ditherOutPaint)
        }
    }

    /** 4-point geometric star beacon (replaces ✦). */
    fun drawVectorStarBeacon(canvas: Canvas, cx: Float, cy: Float, radius: Float, color: Int, alpha: Int = 255) {
        fillPaint.color = color
        fillPaint.alpha = alpha.coerceIn(0, 255)
        val inner = radius * 0.28f
        val path = Path().apply {
            moveTo(cx, cy - radius)
            lineTo(cx + inner, cy - inner)
            lineTo(cx + radius, cy)
            lineTo(cx + inner, cy + inner)
            lineTo(cx, cy + radius)
            lineTo(cx - inner, cy + inner)
            lineTo(cx - radius, cy)
            lineTo(cx - inner, cy - inner)
            close()
        }
        canvas.drawPath(path, fillPaint)
    }

    /** Geometric diamond emblem (replaces ◈ / ◆). */
    fun drawVectorDiamond(canvas: Canvas, cx: Float, cy: Float, radius: Float, color: Int, alpha: Int = 255) {
        fillPaint.color = color
        fillPaint.alpha = alpha.coerceIn(0, 255)
        val path = Path().apply {
            moveTo(cx, cy - radius)
            lineTo(cx + radius * 0.85f, cy)
            lineTo(cx, cy + radius)
            lineTo(cx - radius * 0.85f, cy)
            close()
        }
        canvas.drawPath(path, fillPaint)
    }

    /** Globe icon for NET. */
    fun drawVectorGlobe(canvas: Canvas, cx: Float, cy: Float, r: Float, color: Int, strokeW: Float) {
        strokePaint.color = color
        strokePaint.strokeWidth = strokeW
        canvas.drawCircle(cx, cy, r, strokePaint)
        canvas.drawOval(RectF(cx - r * 0.46f, cy - r, cx + r * 0.46f, cy + r), strokePaint)
        canvas.drawLine(cx - r, cy, cx + r, cy, strokePaint)
    }

    /** Shield icon for OFFLINE / BLOCKED. */
    fun drawVectorShield(canvas: Canvas, cx: Float, cy: Float, r: Float, color: Int, strokeW: Float, slashed: Boolean = false) {
        strokePaint.color = color
        strokePaint.strokeWidth = strokeW
        val path = Path().apply {
            moveTo(cx, cy - r)
            lineTo(cx + r * 0.85f, cy - r * 0.55f)
            lineTo(cx + r * 0.72f, cy + r * 0.25f)
            quadTo(cx + r * 0.4f, cy + r * 0.78f, cx, cy + r)
            quadTo(cx - r * 0.4f, cy + r * 0.78f, cx - r * 0.72f, cy + r * 0.25f)
            lineTo(cx - r * 0.85f, cy - r * 0.55f)
            close()
        }
        canvas.drawPath(path, strokePaint)
        if (slashed) {
            canvas.drawLine(cx - r * 0.65f, cy + r * 0.65f, cx + r * 0.65f, cy - r * 0.65f, strokePaint)
        }
    }

    /** Camera icon for CAM. */
    fun drawVectorCamera(canvas: Canvas, cx: Float, cy: Float, r: Float, color: Int, strokeW: Float) {
        strokePaint.color = color
        strokePaint.strokeWidth = strokeW
        val body = RectF(cx - r, cy - r * 0.55f, cx + r, cy + r * 0.75f)
        canvas.drawRoundRect(body, r * 0.25f, r * 0.25f, strokePaint)
        val bump = Path().apply {
            moveTo(cx - r * 0.45f, cy - r * 0.55f)
            lineTo(cx - r * 0.25f, cy - r * 0.88f)
            lineTo(cx + r * 0.25f, cy - r * 0.88f)
            lineTo(cx + r * 0.45f, cy - r * 0.55f)
        }
        canvas.drawPath(bump, strokePaint)
        canvas.drawCircle(cx, cy + r * 0.1f, r * 0.34f, strokePaint)
    }

    /** Microphone icon for MIC. */
    fun drawVectorMic(canvas: Canvas, cx: Float, cy: Float, r: Float, color: Int, strokeW: Float) {
        strokePaint.color = color
        strokePaint.strokeWidth = strokeW
        val capsule = RectF(cx - r * 0.36f, cy - r * 0.95f, cx + r * 0.36f, cy + r * 0.18f)
        canvas.drawRoundRect(capsule, r * 0.36f, r * 0.36f, strokePaint)
        val arcRect = RectF(cx - r * 0.68f, cy - r * 0.35f, cx + r * 0.68f, cy + r * 0.58f)
        canvas.drawArc(arcRect, 0f, 180f, false, strokePaint)
        canvas.drawLine(cx, cy + r * 0.58f, cx, cy + r * 0.95f, strokePaint)
        canvas.drawLine(cx - r * 0.42f, cy + r * 0.95f, cx + r * 0.42f, cy + r * 0.95f, strokePaint)
    }

    /** Location Pin icon for LOC. */
    fun drawVectorPin(canvas: Canvas, cx: Float, cy: Float, r: Float, color: Int, strokeW: Float) {
        strokePaint.color = color
        strokePaint.strokeWidth = strokeW
        val path = Path().apply {
            moveTo(cx, cy + r)
            quadTo(cx - r * 0.8f, cy + r * 0.15f, cx - r * 0.7f, cy - r * 0.28f)
            arcTo(RectF(cx - r * 0.7f, cy - r * 0.95f, cx + r * 0.7f, cy + r * 0.45f), 180f, 180f, false)
            quadTo(cx + r * 0.8f, cy + r * 0.15f, cx, cy + r)
            close()
        }
        canvas.drawPath(path, strokePaint)
        canvas.drawCircle(cx, cy - r * 0.25f, r * 0.24f, strokePaint)
    }

    /** Contacts icon for CNT. */
    fun drawVectorContacts(canvas: Canvas, cx: Float, cy: Float, r: Float, color: Int, strokeW: Float) {
        strokePaint.color = color
        strokePaint.strokeWidth = strokeW
        canvas.drawCircle(cx, cy - r * 0.38f, r * 0.34f, strokePaint)
        val shoulders = RectF(cx - r * 0.78f, cy + r * 0.12f, cx + r * 0.78f, cy + r * 1.15f)
        canvas.drawArc(shoulders, 180f, 180f, false, strokePaint)
    }

    /** Storage icon for STRG. */
    fun drawVectorStorage(canvas: Canvas, cx: Float, cy: Float, r: Float, color: Int, strokeW: Float) {
        strokePaint.color = color
        strokePaint.strokeWidth = strokeW
        val box = RectF(cx - r * 0.85f, cy - r * 0.6f, cx + r * 0.85f, cy + r * 0.6f)
        canvas.drawRoundRect(box, r * 0.2f, r * 0.2f, strokePaint)
        canvas.drawLine(cx - r * 0.85f, cy + r * 0.05f, cx + r * 0.85f, cy + r * 0.05f, strokePaint)
        fillPaint.color = color
        fillPaint.alpha = Color.alpha(color)
        canvas.drawCircle(cx + r * 0.45f, cy + r * 0.32f, strokeW * 0.9f, fillPaint)
    }

    /** Bell icon for ALERTS. */
    fun drawVectorBell(canvas: Canvas, cx: Float, cy: Float, r: Float, color: Int, strokeW: Float) {
        strokePaint.color = color
        strokePaint.strokeWidth = strokeW
        val dome = Path().apply {
            moveTo(cx - r * 0.72f, cy + r * 0.42f)
            lineTo(cx - r * 0.56f, cy - r * 0.18f)
            arcTo(RectF(cx - r * 0.56f, cy - r * 0.85f, cx + r * 0.56f, cy + r * 0.25f), 180f, 180f, false)
            lineTo(cx + r * 0.72f, cy + r * 0.42f)
            close()
        }
        canvas.drawPath(dome, strokePaint)
        canvas.drawArc(RectF(cx - r * 0.22f, cy + r * 0.42f, cx + r * 0.22f, cy + r * 0.86f), 0f, 180f, false, strokePaint)
    }

    /** Thermometer icon for TEMP. */
    fun drawVectorThermometer(canvas: Canvas, cx: Float, cy: Float, r: Float, color: Int, strokeW: Float) {
        strokePaint.color = color
        strokePaint.strokeWidth = strokeW
        val stem = RectF(cx - r * 0.22f, cy - r * 0.95f, cx + r * 0.22f, cy + r * 0.35f)
        canvas.drawRoundRect(stem, r * 0.22f, r * 0.22f, strokePaint)
        fillPaint.color = color
        fillPaint.alpha = Color.alpha(color)
        canvas.drawCircle(cx, cy + r * 0.55f, r * 0.42f, fillPaint)
    }

    /** Draws a compact pill with a custom vector icon + short label. Returns its bounding RectF. */
    fun drawVectorPill(
        canvas: Canvas,
        left: Float,
        top: Float,
        height: Float,
        label: String,
        iconKind: String,
        pillColor: Int,
        bgAlpha: Int,
        borderAlpha: Int,
        textAlpha: Int,
        density: Float,
        textPaint: Paint
    ): RectF {
        textPaint.typeface = Typeface.DEFAULT_BOLD
        textPaint.textSize = 7.6f * density
        textPaint.textAlign = Paint.Align.LEFT
        val textW = textPaint.measureText(label)
        val iconSize = 8.5f * density
        val padH = 5.5f * density
        val gap = 3.5f * density
        val pillW = padH + iconSize + gap + textW + padH
        val rect = RectF(left, top, left + pillW, top + height)

        fillPaint.color = Color.argb(bgAlpha, Color.red(pillColor), Color.green(pillColor), Color.blue(pillColor))
        canvas.drawRoundRect(rect, height * 0.45f, height * 0.45f, fillPaint)

        strokePaint.color = Color.argb(borderAlpha, Color.red(pillColor), Color.green(pillColor), Color.blue(pillColor))
        strokePaint.strokeWidth = 0.9f * density
        canvas.drawRoundRect(rect, height * 0.45f, height * 0.45f, strokePaint)

        val iconColor = Color.argb(textAlpha, Color.red(pillColor), Color.green(pillColor), Color.blue(pillColor))
        val iconCx = left + padH + iconSize * 0.5f
        val iconCy = rect.centerY()
        val iconR = iconSize * 0.44f
        val iconStroke = 1.15f * density

        when (iconKind) {
            "NET" -> drawVectorGlobe(canvas, iconCx, iconCy, iconR, iconColor, iconStroke)
            "OFFLINE" -> drawVectorShield(canvas, iconCx, iconCy, iconR, iconColor, iconStroke, slashed = false)
            "BLOCKED" -> drawVectorShield(canvas, iconCx, iconCy, iconR, iconColor, iconStroke, slashed = true)
            "CAM" -> drawVectorCamera(canvas, iconCx, iconCy, iconR, iconColor, iconStroke)
            "MIC" -> drawVectorMic(canvas, iconCx, iconCy, iconR, iconColor, iconStroke)
            "LOC", "LOC+" -> drawVectorPin(canvas, iconCx, iconCy, iconR, iconColor, iconStroke)
            "CNT" -> drawVectorContacts(canvas, iconCx, iconCy, iconR, iconColor, iconStroke)
            "STRG" -> drawVectorStorage(canvas, iconCx, iconCy, iconR, iconColor, iconStroke)
            "ALERTS" -> drawVectorBell(canvas, iconCx, iconCy, iconR, iconColor, iconStroke)
            else -> drawVectorDiamond(canvas, iconCx, iconCy, iconR * 0.8f, iconColor, textAlpha)
        }

        val fm = textPaint.fontMetrics
        val textY = rect.centerY() - ((fm.descent + fm.ascent) / 2f)
        textPaint.color = Color.argb(textAlpha, 240, 246, 255)
        canvas.drawText(label, left + padH + iconSize + gap, textY, textPaint)
        return rect
    }

    /** Built-in Vector Icon Pack entries (id to human-readable name). */
    val BUILTIN_VECTOR_PACK: List<Pair<String, String>> = listOf(
        "comm" to "Communication (Chat Bubble)",
        "internet" to "Internet (Globe)",
        "audio" to "Audio (Headphones)",
        "graphics" to "Graphics & Video (Film Play)",
        "games" to "Games (Gamepad)",
        "finance" to "Finance (Wallet)",
        "learning" to "Learning & Books (Book)",
        "utilities" to "Utilities (Wrench)",
        "security" to "Security & Privacy (Shield)",
        "productivity" to "Productivity (Briefcase)",
        "stores" to "App Stores (Shopping Bag)",
        "health" to "Health & Fitness (Heart Pulse)",
        "settings" to "Systems & Styles (Gear)",
        "other" to "Other (Spark / Alien)"
    )

    /** Draws the custom geometric Canvas Path vector icon for a category ID. */
    fun drawCategoryVectorIcon(
        canvas: Canvas,
        categoryId: String,
        cx: Float,
        cy: Float,
        r: Float,
        color: Int,
        alpha: Int = 255,
        strokeWidth: Float = (r * 0.24f).coerceAtLeast(1.2f)
    ) {
        val c = Color.argb(alpha.coerceIn(0, 255), Color.red(color), Color.green(color), Color.blue(color))
        strokePaint.color = c
        strokePaint.strokeWidth = strokeWidth
        fillPaint.color = c
        when (categoryId) {
            "internet" -> drawVectorGlobe(canvas, cx, cy, r, c, strokeWidth)
            "security" -> drawVectorShield(canvas, cx, cy, r, c, strokeWidth, slashed = false)
            "comm" -> drawVectorChatBubble(canvas, cx, cy, r, c, strokeWidth)
            "games" -> drawVectorGamepad(canvas, cx, cy, r, c, strokeWidth)
            "stores" -> drawVectorShoppingBag(canvas, cx, cy, r, c, strokeWidth)
            "audio" -> drawVectorHeadphones(canvas, cx, cy, r, c, strokeWidth)
            "graphics" -> drawVectorFilmPlay(canvas, cx, cy, r, c, strokeWidth)
            "health" -> drawVectorHeartPulse(canvas, cx, cy, r, c, strokeWidth)
            "learning" -> drawVectorBook(canvas, cx, cy, r, c, strokeWidth)
            "utilities" -> drawVectorWrench(canvas, cx, cy, r, c, strokeWidth)
            "settings" -> drawVectorGear(canvas, cx, cy, r, c, strokeWidth)
            "finance" -> drawVectorWallet(canvas, cx, cy, r, c, strokeWidth)
            "productivity" -> drawVectorBriefcase(canvas, cx, cy, r, c, strokeWidth)
            else -> drawVectorAlienSpark(canvas, cx, cy, r, c, strokeWidth)
        }
    }

    /** Renders a built-in vector icon into a crisp square Bitmap for the Icon Picker. */
    fun renderVectorPackBitmap(categoryId: String, sizePx: Int = 128, color: Int = Color.WHITE): android.graphics.Bitmap {
        val bmp = android.graphics.Bitmap.createBitmap(sizePx, sizePx, android.graphics.Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        drawCategoryVectorIcon(
            canvas = c,
            categoryId = categoryId,
            cx = sizePx * 0.5f,
            cy = sizePx * 0.5f,
            r = sizePx * 0.34f,
            color = color,
            alpha = 255,
            strokeWidth = (sizePx * 0.065f).coerceAtLeast(3f)
        )
        return bmp
    }

    private fun drawVectorChatBubble(canvas: Canvas, cx: Float, cy: Float, r: Float, color: Int, strokeW: Float) {
        strokePaint.color = color
        strokePaint.strokeWidth = strokeW
        val p = Path().apply {
            moveTo(cx - r * 0.9f, cy - r * 0.55f)
            lineTo(cx + r * 0.9f, cy - r * 0.55f)
            lineTo(cx + r * 0.9f, cy + r * 0.45f)
            lineTo(cx + r * 0.1f, cy + r * 0.45f)
            lineTo(cx - r * 0.45f, cy + r * 0.9f)
            lineTo(cx - r * 0.45f, cy + r * 0.45f)
            lineTo(cx - r * 0.9f, cy + r * 0.45f)
            close()
        }
        canvas.drawPath(p, strokePaint)
    }

    private fun drawVectorGamepad(canvas: Canvas, cx: Float, cy: Float, r: Float, color: Int, strokeW: Float) {
        strokePaint.color = color
        strokePaint.strokeWidth = strokeW
        val p = Path().apply {
            moveTo(cx - r * 0.55f, cy - r * 0.45f)
            lineTo(cx + r * 0.55f, cy - r * 0.45f)
            lineTo(cx + r * 0.95f, cy + r * 0.45f)
            lineTo(cx + r * 0.55f, cy + r * 0.65f)
            lineTo(cx + r * 0.25f, cy + r * 0.25f)
            lineTo(cx - r * 0.25f, cy + r * 0.25f)
            lineTo(cx - r * 0.55f, cy + r * 0.65f)
            lineTo(cx - r * 0.95f, cy + r * 0.45f)
            close()
        }
        canvas.drawPath(p, strokePaint)
        canvas.drawLine(cx - r * 0.55f, cy - r * 0.05f, cx - r * 0.25f, cy - r * 0.05f, strokePaint)
        canvas.drawLine(cx - r * 0.4f, cy - r * 0.2f, cx - r * 0.4f, cy + r * 0.1f, strokePaint)
        fillPaint.color = color
        canvas.drawCircle(cx + r * 0.42f, cy - r * 0.1f, r * 0.11f, fillPaint)
    }

    private fun drawVectorShoppingBag(canvas: Canvas, cx: Float, cy: Float, r: Float, color: Int, strokeW: Float) {
        strokePaint.color = color
        strokePaint.strokeWidth = strokeW
        canvas.drawRect(cx - r * 0.72f, cy - r * 0.35f, cx + r * 0.72f, cy + r * 0.85f, strokePaint)
        val handle = RectF(cx - r * 0.38f, cy - r * 0.85f, cx + r * 0.38f, cy - r * 0.1f)
        canvas.drawArc(handle, 180f, 180f, false, strokePaint)
    }

    private fun drawVectorHeadphones(canvas: Canvas, cx: Float, cy: Float, r: Float, color: Int, strokeW: Float) {
        strokePaint.color = color
        strokePaint.strokeWidth = strokeW
        val arch = RectF(cx - r * 0.82f, cy - r * 0.82f, cx + r * 0.82f, cy + r * 0.65f)
        canvas.drawArc(arch, 180f, 180f, false, strokePaint)
        canvas.drawRoundRect(RectF(cx - r * 0.92f, cy - r * 0.05f, cx - r * 0.52f, cy + r * 0.68f), r * 0.18f, r * 0.18f, strokePaint)
        canvas.drawRoundRect(RectF(cx + r * 0.52f, cy - r * 0.05f, cx + r * 0.92f, cy + r * 0.68f), r * 0.18f, r * 0.18f, strokePaint)
    }

    private fun drawVectorFilmPlay(canvas: Canvas, cx: Float, cy: Float, r: Float, color: Int, strokeW: Float) {
        strokePaint.color = color
        strokePaint.strokeWidth = strokeW
        canvas.drawRoundRect(RectF(cx - r * 0.88f, cy - r * 0.68f, cx + r * 0.88f, cy + r * 0.68f), r * 0.18f, r * 0.18f, strokePaint)
        fillPaint.color = color
        val play = Path().apply {
            moveTo(cx - r * 0.25f, cy - r * 0.34f)
            lineTo(cx + r * 0.42f, cy)
            lineTo(cx - r * 0.25f, cy + r * 0.34f)
            close()
        }
        canvas.drawPath(play, fillPaint)
    }

    private fun drawVectorHeartPulse(canvas: Canvas, cx: Float, cy: Float, r: Float, color: Int, strokeW: Float) {
        strokePaint.color = color
        strokePaint.strokeWidth = strokeW
        val heart = Path().apply {
            moveTo(cx, cy + r * 0.82f)
            lineTo(cx - r * 0.86f, cy - r * 0.05f)
            quadTo(cx - r * 0.86f, cy - r * 0.78f, cx, cy - r * 0.32f)
            quadTo(cx + r * 0.86f, cy - r * 0.78f, cx + r * 0.86f, cy - r * 0.05f)
            close()
        }
        canvas.drawPath(heart, strokePaint)
        val pulse = Path().apply {
            moveTo(cx - r * 0.55f, cy + r * 0.05f)
            lineTo(cx - r * 0.2f, cy + r * 0.05f)
            lineTo(cx - r * 0.05f, cy - r * 0.22f)
            lineTo(cx + r * 0.12f, cy + r * 0.26f)
            lineTo(cx + r * 0.28f, cy + r * 0.05f)
            lineTo(cx + r * 0.55f, cy + r * 0.05f)
        }
        canvas.drawPath(pulse, strokePaint)
    }

    private fun drawVectorBook(canvas: Canvas, cx: Float, cy: Float, r: Float, color: Int, strokeW: Float) {
        strokePaint.color = color
        strokePaint.strokeWidth = strokeW
        val p = Path().apply {
            moveTo(cx, cy - r * 0.55f)
            lineTo(cx - r * 0.88f, cy - r * 0.72f)
            lineTo(cx - r * 0.88f, cy + r * 0.55f)
            lineTo(cx, cy + r * 0.72f)
            lineTo(cx + r * 0.88f, cy + r * 0.55f)
            lineTo(cx + r * 0.88f, cy - r * 0.72f)
            close()
        }
        canvas.drawPath(p, strokePaint)
        canvas.drawLine(cx, cy - r * 0.55f, cx, cy + r * 0.72f, strokePaint)
    }

    private fun drawVectorWrench(canvas: Canvas, cx: Float, cy: Float, r: Float, color: Int, strokeW: Float) {
        strokePaint.color = color
        strokePaint.strokeWidth = strokeW
        canvas.drawLine(cx - r * 0.72f, cy + r * 0.72f, cx + r * 0.28f, cy - r * 0.28f, strokePaint)
        val head = RectF(cx - r * 0.02f, cy - r * 0.88f, cx + r * 0.88f, cy + r * 0.02f)
        canvas.drawArc(head, -20f, 270f, false, strokePaint)
    }

    private fun drawVectorGear(canvas: Canvas, cx: Float, cy: Float, r: Float, color: Int, strokeW: Float) {
        strokePaint.color = color
        strokePaint.strokeWidth = strokeW
        canvas.drawCircle(cx, cy, r * 0.42f, strokePaint)
        for (i in 0 until 6) {
            val angle = i * (Math.PI / 3.0)
            val x1 = cx + (kotlin.math.cos(angle) * r * 0.56f).toFloat()
            val y1 = cy + (kotlin.math.sin(angle) * r * 0.56f).toFloat()
            val x2 = cx + (kotlin.math.cos(angle) * r * 0.92f).toFloat()
            val y2 = cy + (kotlin.math.sin(angle) * r * 0.92f).toFloat()
            canvas.drawLine(x1, y1, x2, y2, strokePaint)
        }
    }

    private fun drawVectorWallet(canvas: Canvas, cx: Float, cy: Float, r: Float, color: Int, strokeW: Float) {
        strokePaint.color = color
        strokePaint.strokeWidth = strokeW
        canvas.drawRoundRect(RectF(cx - r * 0.88f, cy - r * 0.58f, cx + r * 0.88f, cy + r * 0.65f), r * 0.18f, r * 0.18f, strokePaint)
        canvas.drawRoundRect(RectF(cx + r * 0.15f, cy - r * 0.18f, cx + r * 0.88f, cy + r * 0.25f), r * 0.12f, r * 0.12f, strokePaint)
        fillPaint.color = color
        canvas.drawCircle(cx + r * 0.42f, cy + r * 0.04f, r * 0.09f, fillPaint)
    }

    private fun drawVectorBriefcase(canvas: Canvas, cx: Float, cy: Float, r: Float, color: Int, strokeW: Float) {
        strokePaint.color = color
        strokePaint.strokeWidth = strokeW
        canvas.drawRoundRect(RectF(cx - r * 0.88f, cy - r * 0.42f, cx + r * 0.88f, cy + r * 0.72f), r * 0.16f, r * 0.16f, strokePaint)
        canvas.drawRect(cx - r * 0.34f, cy - r * 0.76f, cx + r * 0.34f, cy - r * 0.42f, strokePaint)
        canvas.drawLine(cx - r * 0.88f, cy + r * 0.05f, cx + r * 0.88f, cy + r * 0.05f, strokePaint)
    }

    private fun drawVectorAlienSpark(canvas: Canvas, cx: Float, cy: Float, r: Float, color: Int, strokeW: Float) {
        strokePaint.color = color
        strokePaint.strokeWidth = strokeW
        val p = Path().apply {
            moveTo(cx, cy - r)
            quadTo(cx + r * 0.2f, cy - r * 0.2f, cx + r, cy)
            quadTo(cx + r * 0.2f, cy + r * 0.2f, cx, cy + r)
            quadTo(cx - r * 0.2f, cy + r * 0.2f, cx - r, cy)
            quadTo(cx - r * 0.2f, cy - r * 0.2f, cx, cy - r)
            close()
        }
        canvas.drawPath(p, strokePaint)
    }
}
