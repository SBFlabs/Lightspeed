package com.sbf.lightspeed

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import com.sbf.lightspeed.system.LightspeedLocaleEngine
import com.sbf.lightspeed.system.lsString
import kotlin.math.abs

private val sharedTipPrefixPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
    style = Paint.Style.FILL
    textAlign = Paint.Align.LEFT
}
private val sharedTipBodyPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
    style = Paint.Style.FILL
    textAlign = Paint.Align.LEFT
}

/**
 * Returns true when [text] is Arabic/RTL script so Canvas tip chrome renders right-to-left
 * regardless of the user's independent UI layout direction toggle.
 */
internal fun isRtlScriptText(text: String): Boolean {
    if (LightspeedLocaleEngine.state.primary == LightspeedLocaleEngine.AppLanguage.AR) return true
    for (ch in text) {
        val dir = Character.getDirectionality(ch)
        if (dir == Character.DIRECTIONALITY_RIGHT_TO_LEFT ||
            dir == Character.DIRECTIONALITY_RIGHT_TO_LEFT_ARABIC
        ) {
            return true
        }
        if (dir == Character.DIRECTIONALITY_LEFT_TO_RIGHT) return false
    }
    return false
}

/** Wraps RTL text segments in RLE/PDF so trailing punctuation (`.` / `…`) keeps RTL placement on Canvas. */
internal fun formatCanvasRunText(text: String, isRtl: Boolean): String =
    if (isRtl && text.isNotEmpty()) "\u202B$text\u202C" else text

internal fun resolveTipKickerLabel(): String =
    lsString(R.string.ck_hangar_tip_kicker).ifBlank { "TIP" }

internal fun measureLocalizedTipPrefixWidth(
    kicker: String,
    prefixPaint: Paint,
    isRtl: Boolean
): Float {
    return if (!isRtl) {
        prefixPaint.measureText("✦ $kicker: ")
    } else {
        val sp = prefixPaint.measureText(" ")
        val rtlKicker = formatCanvasRunText(kicker, isRtl = true)
        prefixPaint.measureText("✦") + sp +
            prefixPaint.measureText(rtlKicker) +
            prefixPaint.measureText(":") + sp
    }
}

internal fun drawLocalizedTipFirstLine(
    canvas: Canvas,
    kicker: String,
    bodyLine: String,
    isTruncatedWithEllipsis: Boolean,
    cx: Float,
    baselineY: Float,
    prefixPaint: Paint,
    bodyPaint: Paint,
    isRtl: Boolean
) {
    if (!isRtl) {
        val prefix = "✦ $kicker: "
        val renderedBody = if (isTruncatedWithEllipsis) "$bodyLine…" else bodyLine
        val prefixW = prefixPaint.measureText(prefix)
        val bodyW = bodyPaint.measureText(renderedBody)
        val startX = cx - (prefixW + bodyW) * 0.5f
        canvas.drawText(prefix, startX, baselineY, prefixPaint)
        canvas.drawText(renderedBody, startX + prefixW, baselineY, bodyPaint)
        return
    }

    val rtlKicker = formatCanvasRunText(kicker, isRtl = true)
    val rtlBody = formatCanvasRunText(bodyLine, isRtl = true)
    val sp = prefixPaint.measureText(" ")
    val starW = prefixPaint.measureText("✦")
    val kickerW = prefixPaint.measureText(rtlKicker)
    val colonW = prefixPaint.measureText(":")
    val prefixW = starW + sp + kickerW + colonW + sp
    val ellW = if (isTruncatedWithEllipsis) bodyPaint.measureText("…") else 0f
    val cleanBodyW = bodyPaint.measureText(rtlBody)
    val totalBodyW = cleanBodyW + ellW

    val leftEdge = cx - (prefixW + totalBodyW) * 0.5f
    val rightEdge = leftEdge + prefixW + totalBodyW

    val starX = rightEdge - starW
    val kickerX = starX - sp - kickerW
    val colonX = kickerX - colonW

    canvas.drawText("✦", starX, baselineY, prefixPaint)
    canvas.drawText(rtlKicker, kickerX, baselineY, prefixPaint)
    canvas.drawText(":", colonX, baselineY, prefixPaint)
    if (isTruncatedWithEllipsis) {
        canvas.drawText("…", leftEdge, baselineY, bodyPaint)
        canvas.drawText(rtlBody, leftEdge + ellW, baselineY, bodyPaint)
    } else {
        canvas.drawText(rtlBody, leftEdge, baselineY, bodyPaint)
    }
}

internal fun drawCenteredBodyLine(
    canvas: Canvas,
    lineText: String,
    isTruncatedWithEllipsis: Boolean,
    cx: Float,
    baselineY: Float,
    bodyPaint: Paint,
    isRtl: Boolean
) {
    if (!isRtl) {
        val rendered = if (isTruncatedWithEllipsis) "$lineText…" else lineText
        val lineW = bodyPaint.measureText(rendered)
        canvas.drawText(rendered, cx - lineW * 0.5f, baselineY, bodyPaint)
        return
    }
    val rtlLine = formatCanvasRunText(lineText, isRtl = true)
    val ellW = if (isTruncatedWithEllipsis) bodyPaint.measureText("…") else 0f
    val bodyW = bodyPaint.measureText(rtlLine)
    val leftEdge = cx - (ellW + bodyW) * 0.5f
    if (isTruncatedWithEllipsis) {
        canvas.drawText("…", leftEdge, baselineY, bodyPaint)
        canvas.drawText(rtlLine, leftEdge + ellW, baselineY, bodyPaint)
    } else {
        canvas.drawText(rtlLine, leftEdge, baselineY, bodyPaint)
    }
}

internal fun drawInlineGhostTipSingleLine(
    canvas: Canvas,
    tipBody: String,
    cx: Float,
    baselineY: Float,
    maxWidth: Float,
    density: Float,
    baseTextSize: Float,
    accentColor: Int,
    boldTypeface: Typeface,
    regularTypeface: Typeface,
    alphaFactor: Float = 1f,
    minTextSize: Float = (9.4f * density).coerceAtMost(baseTextSize),
    maxLines: Int = 1
) {
    val cleanBody = tipBody.trim()
    if (cleanBody.isEmpty() || maxWidth <= 0f) return
    val clampedAlpha = alphaFactor.coerceIn(0f, 1f)
    if (clampedAlpha <= 0.01f) return

    val kicker = resolveTipKickerLabel()
    val isRtl = isRtlScriptText(cleanBody) || isRtlScriptText(kicker)
    sharedTipPrefixPaint.typeface = boldTypeface
    sharedTipBodyPaint.typeface = regularTypeface

    val effectiveMinSize = minTextSize.coerceIn(7.8f * density, baseTextSize)
    sharedTipPrefixPaint.textSize = baseTextSize
    sharedTipBodyPaint.textSize = baseTextSize

    val basePrefixW = measureLocalizedTipPrefixWidth(kicker, sharedTipPrefixPaint, isRtl)
    val rawTotalW = basePrefixW + sharedTipBodyPaint.measureText(formatCanvasRunText(cleanBody, isRtl))

    val shadowColor = Color.argb((225 * clampedAlpha).toInt().coerceIn(0, 255), 0, 0, 0)
    sharedTipPrefixPaint.color = Color.argb(
        (238 * clampedAlpha).toInt().coerceIn(0, 255),
        Color.red(accentColor),
        Color.green(accentColor),
        Color.blue(accentColor)
    )
    sharedTipBodyPaint.color = Color.argb((235 * clampedAlpha).toInt().coerceIn(0, 255), 235, 243, 255)

    try {
        sharedTipPrefixPaint.setShadowLayer(5.5f * density, 0f, 1.5f * density, shadowColor)
        sharedTipBodyPaint.setShadowLayer(5.5f * density, 0f, 1.5f * density, shadowColor)

        if (maxLines >= 2 && rawTotalW > maxWidth) {
            val singleFitSize = if (rawTotalW > 0f) baseTextSize * (maxWidth / rawTotalW) else baseTextSize
            if (singleFitSize < baseTextSize * 0.90f) {
                var wrapSize = baseTextSize
                sharedTipPrefixPaint.textSize = wrapSize
                sharedTipBodyPaint.textSize = wrapSize
                var wrapped = wrapBalancedGhostTipLines(
                    kicker = kicker,
                    body = cleanBody,
                    prefixPaint = sharedTipPrefixPaint,
                    bodyPaint = sharedTipBodyPaint,
                    maxWidth = maxWidth,
                    isRtl = isRtl
                )
                while (wrapped.size > 2 && wrapSize > effectiveMinSize) {
                    wrapSize = (wrapSize - 0.5f * density).coerceAtLeast(effectiveMinSize)
                    sharedTipPrefixPaint.textSize = wrapSize
                    sharedTipBodyPaint.textSize = wrapSize
                    wrapped = wrapBalancedGhostTipLines(
                        kicker = kicker,
                        body = cleanBody,
                        prefixPaint = sharedTipPrefixPaint,
                        bodyPaint = sharedTipBodyPaint,
                        maxWidth = maxWidth,
                        isRtl = isRtl
                    )
                }
                if (wrapped.size >= 2) {
                    val line0 = wrapped[0]
                    var line1 = wrapped.drop(1).joinToString(" ")
                    var line1Truncated = false
                    if (sharedTipBodyPaint.measureText(formatCanvasRunText(line1, isRtl)) > maxWidth) {
                        val ellW = sharedTipBodyPaint.measureText("…")
                        val targetW = (maxWidth - ellW).coerceAtLeast(16f * density)
                        while (line1.length > 1 && sharedTipBodyPaint.measureText(formatCanvasRunText(line1, isRtl)) > targetW) {
                            line1 = line1.dropLast(1).trimEnd()
                        }
                        line1Truncated = line1.isNotEmpty()
                    }
                    val fm = sharedTipBodyPaint.fontMetrics
                    val lineH = (fm.descent - fm.ascent) * 1.22f
                    val line0BaselineY = baselineY - lineH
                    drawLocalizedTipFirstLine(
                        canvas = canvas,
                        kicker = kicker,
                        bodyLine = line0,
                        isTruncatedWithEllipsis = false,
                        cx = cx,
                        baselineY = line0BaselineY,
                        prefixPaint = sharedTipPrefixPaint,
                        bodyPaint = sharedTipBodyPaint,
                        isRtl = isRtl
                    )
                    drawCenteredBodyLine(
                        canvas = canvas,
                        lineText = line1,
                        isTruncatedWithEllipsis = line1Truncated,
                        cx = cx,
                        baselineY = baselineY,
                        bodyPaint = sharedTipBodyPaint,
                        isRtl = isRtl
                    )
                    return
                }
            }
        }

        val fittedSize = if (rawTotalW > maxWidth && rawTotalW > 0f) {
            (baseTextSize * (maxWidth / rawTotalW)).coerceIn(effectiveMinSize, baseTextSize)
        } else {
            baseTextSize
        }
        sharedTipPrefixPaint.textSize = fittedSize
        sharedTipBodyPaint.textSize = fittedSize

        val prefixW = measureLocalizedTipPrefixWidth(kicker, sharedTipPrefixPaint, isRtl)
        var renderedBody = cleanBody
        var isTruncated = false
        if (prefixW + sharedTipBodyPaint.measureText(formatCanvasRunText(renderedBody, isRtl)) > maxWidth) {
            val ellW = sharedTipBodyPaint.measureText("…")
            val availBodyW = (maxWidth - prefixW - ellW).coerceAtLeast(24f * density)
            while (renderedBody.length > 1 && sharedTipBodyPaint.measureText(formatCanvasRunText(renderedBody, isRtl)) > availBodyW) {
                renderedBody = renderedBody.dropLast(1).trimEnd()
            }
            isTruncated = renderedBody.isNotEmpty()
        }

        drawLocalizedTipFirstLine(
            canvas = canvas,
            kicker = kicker,
            bodyLine = renderedBody,
            isTruncatedWithEllipsis = isTruncated,
            cx = cx,
            baselineY = baselineY,
            prefixPaint = sharedTipPrefixPaint,
            bodyPaint = sharedTipBodyPaint,
            isRtl = isRtl
        )
    } finally {
        sharedTipPrefixPaint.clearShadowLayer()
        sharedTipBodyPaint.clearShadowLayer()
    }
}

/**
 * Draws the live Cockpit Gear Set rotating tip with larger typography, luminance-lifted
 * accent prefix, and high-contrast shadow/body colors while avoiding ring overlap.
 */
internal fun drawLiveGearRotatingTip(
    canvas: Canvas,
    tipBody: String,
    cx: Float,
    topY: Float,
    maxBottomY: Float,
    maxWidth: Float,
    density: Float,
    baseTextSize: Float,
    accentColor: Int,
    boldTypeface: Typeface,
    regularTypeface: Typeface
) {
    val cleanBody = tipBody.trim()
    if (cleanBody.isEmpty() || maxWidth <= 0f) return

    val kicker = resolveTipKickerLabel()
    val isRtl = isRtlScriptText(cleanBody) || isRtlScriptText(kicker)
    sharedTipPrefixPaint.typeface = boldTypeface
    sharedTipBodyPaint.typeface = regularTypeface
    sharedTipPrefixPaint.textSize = baseTextSize
    sharedTipBodyPaint.textSize = baseTextSize

    val r = Color.red(accentColor)
    val g = Color.green(accentColor)
    val b = Color.blue(accentColor)
    val luminance = (0.299f * r + 0.587f * g + 0.114f * b) / 255f
    val lift = if (luminance < 0.45f) 0.40f else 0f
    val liftR = (r + (255 - r) * lift).toInt().coerceIn(0, 255)
    val liftG = (g + (255 - g) * lift).toInt().coerceIn(0, 255)
    val liftB = (b + (255 - b) * lift).toInt().coerceIn(0, 255)

    val shadowColor = Color.argb(235, 0, 0, 0)
    sharedTipPrefixPaint.color = Color.argb(248, liftR, liftG, liftB)
    sharedTipBodyPaint.color = Color.argb(242, 240, 246, 255)

    val prefixWFull = measureLocalizedTipPrefixWidth(kicker, sharedTipPrefixPaint, isRtl)
    val rawSingleW = prefixWFull + sharedTipBodyPaint.measureText(formatCanvasRunText(cleanBody, isRtl))
    val fmFull = sharedTipBodyPaint.fontMetrics
    val glyphH = fmFull.descent - fmFull.ascent
    val lineH = glyphH * 1.24f

    try {
        sharedTipPrefixPaint.setShadowLayer(6f * density, 0f, 1.5f * density, shadowColor)
        sharedTipBodyPaint.setShadowLayer(6f * density, 0f, 1.5f * density, shadowColor)

        if (rawSingleW <= maxWidth) {
            val baselineY = topY - fmFull.ascent
            drawLocalizedTipFirstLine(
                canvas = canvas,
                kicker = kicker,
                bodyLine = cleanBody,
                isTruncatedWithEllipsis = false,
                cx = cx,
                baselineY = baselineY,
                prefixPaint = sharedTipPrefixPaint,
                bodyPaint = sharedTipBodyPaint,
                isRtl = isRtl
            )
            return
        }

        val singleFitSize = if (rawSingleW > 0f) baseTextSize * (maxWidth / rawSingleW) else baseTextSize
        val twoLineAvailH = maxBottomY - topY
        val neededTwoLineH = (lineH + glyphH) + 6f * density

        if (singleFitSize < baseTextSize * 0.90f && twoLineAvailH >= neededTwoLineH) {
            val wrapped = wrapBalancedGhostTipLines(
                kicker = kicker,
                body = cleanBody,
                prefixPaint = sharedTipPrefixPaint,
                bodyPaint = sharedTipBodyPaint,
                maxWidth = maxWidth,
                isRtl = isRtl
            )
            if (wrapped.size in 1..2) {
                val firstBaselineY = topY - fmFull.ascent
                drawLocalizedTipFirstLine(
                    canvas = canvas,
                    kicker = kicker,
                    bodyLine = wrapped[0],
                    isTruncatedWithEllipsis = false,
                    cx = cx,
                    baselineY = firstBaselineY,
                    prefixPaint = sharedTipPrefixPaint,
                    bodyPaint = sharedTipBodyPaint,
                    isRtl = isRtl
                )
                if (wrapped.size == 2) {
                    drawCenteredBodyLine(
                        canvas = canvas,
                        lineText = wrapped[1],
                        isTruncatedWithEllipsis = false,
                        cx = cx,
                        baselineY = firstBaselineY + lineH,
                        bodyPaint = sharedTipBodyPaint,
                        isRtl = isRtl
                    )
                }
                return
            }
        }

        val minTextSize = (9.6f * density).coerceAtMost(baseTextSize)
        val fittedSize = singleFitSize.coerceIn(minTextSize, baseTextSize)
        sharedTipPrefixPaint.textSize = fittedSize
        sharedTipBodyPaint.textSize = fittedSize

        val prefixW = measureLocalizedTipPrefixWidth(kicker, sharedTipPrefixPaint, isRtl)
        var renderedBody = cleanBody
        var isTruncated = false
        if (prefixW + sharedTipBodyPaint.measureText(formatCanvasRunText(renderedBody, isRtl)) > maxWidth) {
            val ellW = sharedTipBodyPaint.measureText("…")
            val availBodyW = (maxWidth - prefixW - ellW).coerceAtLeast(24f * density)
            while (renderedBody.length > 1 && sharedTipBodyPaint.measureText(formatCanvasRunText(renderedBody, isRtl)) > availBodyW) {
                renderedBody = renderedBody.dropLast(1).trimEnd()
            }
            isTruncated = renderedBody.isNotEmpty()
        }
        val fmFitted = sharedTipBodyPaint.fontMetrics
        val baselineY = topY - fmFitted.ascent
        drawLocalizedTipFirstLine(
            canvas = canvas,
            kicker = kicker,
            bodyLine = renderedBody,
            isTruncatedWithEllipsis = isTruncated,
            cx = cx,
            baselineY = baselineY,
            prefixPaint = sharedTipPrefixPaint,
            bodyPaint = sharedTipBodyPaint,
            isRtl = isRtl
        )
    } finally {
        sharedTipPrefixPaint.clearShadowLayer()
        sharedTipBodyPaint.clearShadowLayer()
    }
}

internal fun wrapBalancedGhostTipLines(
    kicker: String,
    body: String,
    prefixPaint: Paint,
    bodyPaint: Paint,
    maxWidth: Float,
    isRtl: Boolean
): List<String> {
    val clean = body.trim().replace(Regex("\\s+"), " ")
    if (clean.isEmpty()) return emptyList()

    val prefixW = measureLocalizedTipPrefixWidth(kicker, prefixPaint, isRtl)
    fun line0Width(s: String): Float = prefixW + bodyPaint.measureText(formatCanvasRunText(s, isRtl))
    fun bodyWidth(s: String): Float = bodyPaint.measureText(formatCanvasRunText(s, isRtl))

    if (line0Width(clean) <= maxWidth) {
        return listOf(clean)
    }

    val words = clean.split(' ').filter { it.isNotEmpty() }
    if (words.size <= 1) return listOf(clean)

    var bestTwoLines: List<String>? = null
    var bestTwoDiff = Float.MAX_VALUE
    for (splitIdx in 0 until words.lastIndex) {
        if (words.size >= 4 && splitIdx == words.lastIndex - 1) continue
        val l1 = words.subList(0, splitIdx + 1).joinToString(" ")
        val l2 = words.subList(splitIdx + 1, words.size).joinToString(" ")
        val w1 = line0Width(l1)
        val w2 = bodyWidth(l2)
        if (w1 <= maxWidth && w2 <= maxWidth) {
            val diff = abs(w1 - w2)
            if (diff < bestTwoDiff) {
                bestTwoDiff = diff
                bestTwoLines = listOf(l1, l2)
            }
        }
    }
    if (bestTwoLines != null) return bestTwoLines

    if (words.size >= 3) {
        var bestThreeLines: List<String>? = null
        var bestThreeSpread = Float.MAX_VALUE
        for (i in 0 until words.size - 2) {
            val l1 = words.subList(0, i + 1).joinToString(" ")
            val w1 = line0Width(l1)
            if (w1 > maxWidth) break
            for (j in (i + 1) until words.lastIndex) {
                if (words.size >= 5 && j == words.lastIndex - 1) continue
                val l2 = words.subList(i + 1, j + 1).joinToString(" ")
                val l3 = words.subList(j + 1, words.size).joinToString(" ")
                val w2 = bodyWidth(l2)
                val w3 = bodyWidth(l3)
                if (w2 <= maxWidth && w3 <= maxWidth) {
                    val spread = maxOf(w1, w2, w3) - minOf(w1, w2, w3)
                    if (spread < bestThreeSpread) {
                        bestThreeSpread = spread
                        bestThreeLines = listOf(l1, l2, l3)
                    }
                }
            }
        }
        if (bestThreeLines != null) return bestThreeLines
    }

    val lines = mutableListOf<String>()
    val current = StringBuilder()
    var wordIndex = 0
    while (wordIndex < words.size) {
        val word = words[wordIndex]
        val candidate = if (current.isEmpty()) word else "$current $word"
        val candidateW = if (lines.isEmpty()) line0Width(candidate) else bodyWidth(candidate)
        if (candidateW <= maxWidth || current.isEmpty()) {
            if (current.isNotEmpty()) current.append(' ')
            current.append(word)
            wordIndex++
        } else {
            lines.add(current.toString())
            current.setLength(0)
            if (lines.size == 2) {
                val remaining = words.subList(wordIndex, words.size).joinToString(" ")
                if (bodyWidth(remaining) <= maxWidth) {
                    lines.add(remaining)
                } else {
                    val ellW = bodyPaint.measureText("…")
                    val targetW = (maxWidth - ellW).coerceAtLeast(16f)
                    var trimmed = remaining
                    while (trimmed.length > 1 && bodyWidth(trimmed) > targetW) {
                        trimmed = trimmed.dropLast(1).trimEnd()
                    }
                    lines.add(trimmed)
                }
                return lines
            }
        }
    }
    if (current.isNotEmpty()) {
        lines.add(current.toString())
    }
    return lines
}
