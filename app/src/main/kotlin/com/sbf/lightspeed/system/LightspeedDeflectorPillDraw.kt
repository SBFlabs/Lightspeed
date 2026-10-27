package com.sbf.lightspeed.system

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF

/**
 * Dedicated renderer for Central Pill bezel styles without per-frame allocations.
 */
internal object LightspeedDeflectorPillDraw {

    private val cornerRadiiLeft = FloatArray(8)
    private val cornerRadiiRight = FloatArray(8)

    fun getRadiiLeft(r: Float): FloatArray {
        cornerRadiiLeft[0] = 0f; cornerRadiiLeft[1] = 0f
        cornerRadiiLeft[2] = r;  cornerRadiiLeft[3] = r
        cornerRadiiLeft[4] = r;  cornerRadiiLeft[5] = r
        cornerRadiiLeft[6] = 0f; cornerRadiiLeft[7] = 0f
        return cornerRadiiLeft
    }

    fun getRadiiRight(r: Float): FloatArray {
        cornerRadiiRight[0] = r;  cornerRadiiRight[1] = r
        cornerRadiiRight[2] = 0f; cornerRadiiRight[3] = 0f
        cornerRadiiRight[4] = 0f; cornerRadiiRight[5] = 0f
        cornerRadiiRight[6] = r;  cornerRadiiRight[7] = r
        return cornerRadiiRight
    }

    fun drawPill(
        canvas: Canvas,
        isLeft: Boolean,
        d: Float,
        w: Float,
        centerTouchBounds: RectF,
        isTouched: Boolean,
        glowFraction: Float,
        centerTransparency: Int,
        m3Primary: Int,
        glowStyle: String,
        useM3Color: Boolean,
        pillStyle: String,
        touchY: Float?,
        visualWidthPx: Float,
        bladePath: Path,
        bladeFillPaint: Paint,
        reviewStrokePaint: Paint,
        pillRect: RectF,
        auraRect: RectF,
        applyPillShading: (isLeft: Boolean, w: Float, pillW: Float, effectiveAlpha: Int, m3Primary: Int, glowStyle: String, useM3Color: Boolean) -> Unit
    ) {
        val baseW = if (visualWidthPx > 0f) visualWidthPx else minOf(centerTouchBounds.width(), 6f * d)
        val pillTop = centerTouchBounds.top
        val pillBottom = centerTouchBounds.bottom
        val pillH = pillBottom - pillTop

        val restingAlpha = (centerTransparency * 2.55f).toInt().coerceIn(0, 255)
        val activeAlpha = 210
        val effectiveAlpha = if (isTouched) activeAlpha else maxOf(restingAlpha, (activeAlpha * glowFraction).toInt())

        if (effectiveAlpha <= 0 || pillH <= 0) return

        bladePath.reset()
        val (baseR, baseG, baseB) = if (useM3Color) {
            Triple(Color.red(m3Primary), Color.green(m3Primary), Color.blue(m3Primary))
        } else {
            when (glowStyle) {
                "crimson_reactor" -> Triple(255, 45, 80)
                "cyber_plasma" -> Triple(0, 229, 255)
                else -> Triple(120, 160, 255)
            }
        }

        when (pillStyle) {
            "anchored_glow" -> {
                val pillW = baseW
                val cornerR = (pillW * 0.5f).coerceAtMost(pillH / 2f)
                if (isLeft) {
                    pillRect.set(0f, pillTop, pillW, pillBottom)
                    bladePath.addRoundRect(pillRect, getRadiiLeft(cornerR), Path.Direction.CW)
                } else {
                    pillRect.set(w - pillW, pillTop, w, pillBottom)
                    bladePath.addRoundRect(pillRect, getRadiiRight(cornerR), Path.Direction.CW)
                }
                applyPillShading(isLeft, w, pillW, effectiveAlpha, m3Primary, glowStyle, useM3Color)
                canvas.drawPath(bladePath, bladeFillPaint)
            }

            "floating_smart_pill" -> {
                val pillW = maxOf(baseW, 4f * d)
                val floatGap = 3f * d
                val cornerR = pillW / 2f
                if (isLeft) {
                    pillRect.set(floatGap, pillTop, floatGap + pillW, pillBottom)
                } else {
                    pillRect.set(w - floatGap - pillW, pillTop, w - floatGap, pillBottom)
                }
                bladeFillPaint.shader = null
                val fillAlpha = (effectiveAlpha * 0.45f).toInt().coerceIn(0, 255)
                bladeFillPaint.color = Color.argb(fillAlpha, baseR, baseG, baseB)
                canvas.drawRoundRect(pillRect, cornerR, cornerR, bladeFillPaint)
            }

            "neon_core" -> {
                val coreW = 1.5f * d
                val floatGap = 2f * d
                val coreTop = pillTop + pillH * 0.1f
                val coreBottom = pillBottom - pillH * 0.1f

                if (isLeft) {
                    pillRect.set(floatGap, coreTop, floatGap + coreW, coreBottom)
                    auraRect.set(0f, pillTop, 8f * d, pillBottom)
                } else {
                    pillRect.set(w - floatGap - coreW, coreTop, w - floatGap, coreBottom)
                    auraRect.set(w - 8f * d, pillTop, w, pillBottom)
                }

                val auraW = 8f * d
                applyPillShading(isLeft, w, auraW, (effectiveAlpha * 0.5f).toInt(), m3Primary, glowStyle, useM3Color)

                if (isLeft) {
                    bladePath.addRoundRect(auraRect, getRadiiLeft(auraW), Path.Direction.CW)
                } else {
                    bladePath.addRoundRect(auraRect, getRadiiRight(auraW), Path.Direction.CW)
                }
                canvas.drawPath(bladePath, bladeFillPaint)

                bladeFillPaint.shader = null
                bladeFillPaint.color = Color.WHITE
                canvas.drawRoundRect(pillRect, coreW / 2f, coreW / 2f, bladeFillPaint)
            }

            "razor_edge" -> {
                val pillW = 1.2f * d
                val floatGap = 1.5f * d
                val cornerR = pillW / 2f
                if (isLeft) {
                    pillRect.set(floatGap, pillTop, floatGap + pillW, pillBottom)
                } else {
                    pillRect.set(w - floatGap - pillW, pillTop, w - floatGap, pillBottom)
                }
                bladeFillPaint.shader = null
                val fillAlpha = (effectiveAlpha * 0.85f).toInt().coerceIn(0, 255)
                bladeFillPaint.color = Color.argb(fillAlpha, baseR, baseG, baseB)
                canvas.drawRoundRect(pillRect, cornerR, cornerR, bladeFillPaint)
            }

            "kinetic_elastic" -> {
                val pillW = maxOf(baseW, 3f * d)
                val floatGap = 3f * d
                val cornerR = pillW / 2f
                if (isLeft) {
                    pillRect.set(floatGap, pillTop, floatGap + pillW, pillBottom)
                } else {
                    pillRect.set(w - floatGap - pillW, pillTop, w - floatGap, pillBottom)
                }
                bladeFillPaint.shader = null
                val fillAlpha = (effectiveAlpha * 0.6f).toInt().coerceIn(0, 255)
                bladeFillPaint.color = Color.argb(fillAlpha, baseR, baseG, baseB)

                if (isTouched && touchY != null) {
                    val bulgeAmount = 6f * d
                    val tY = touchY.coerceIn(pillTop, pillBottom)
                    val ctrlOffset = 30f * d

                    if (isLeft) {
                        bladePath.moveTo(floatGap, pillTop)
                        bladePath.lineTo(floatGap + pillW, pillTop)
                        bladePath.cubicTo(
                            floatGap + pillW, tY - ctrlOffset,
                            floatGap + pillW + bulgeAmount, tY,
                            floatGap + pillW, tY + ctrlOffset
                        )
                        bladePath.lineTo(floatGap + pillW, pillBottom)
                        bladePath.lineTo(floatGap, pillBottom)
                        bladePath.close()
                    } else {
                        bladePath.moveTo(w - floatGap, pillTop)
                        bladePath.lineTo(w - floatGap - pillW, pillTop)
                        bladePath.cubicTo(
                            w - floatGap - pillW, tY - ctrlOffset,
                            w - floatGap - pillW - bulgeAmount, tY,
                            w - floatGap - pillW, tY + ctrlOffset
                        )
                        bladePath.lineTo(w - floatGap - pillW, pillBottom)
                        bladePath.lineTo(w - floatGap, pillBottom)
                        bladePath.close()
                    }
                    canvas.drawPath(bladePath, bladeFillPaint)
                } else {
                    canvas.drawRoundRect(pillRect, cornerR, cornerR, bladeFillPaint)
                }
            }

            "hollow_ghost" -> {
                val pillW = maxOf(baseW, 4f * d)
                val floatGap = 3f * d
                val cornerR = pillW / 2f
                if (isLeft) {
                    pillRect.set(floatGap, pillTop, floatGap + pillW, pillBottom)
                } else {
                    pillRect.set(w - floatGap - pillW, pillTop, w - floatGap, pillBottom)
                }
                reviewStrokePaint.color = Color.argb(effectiveAlpha, baseR, baseG, baseB)
                reviewStrokePaint.strokeWidth = 1.2f * d
                canvas.drawRoundRect(pillRect, cornerR, cornerR, reviewStrokePaint)
                reviewStrokePaint.color = Color.WHITE
            }
        }
    }
}
