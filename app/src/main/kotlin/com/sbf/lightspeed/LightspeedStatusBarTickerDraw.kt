package com.sbf.lightspeed

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.os.Build
import android.os.SystemClock

internal fun LightspeedStatusBarOverlay.drawTelemetryTicker(
    canvas: Canvas,
    layout: HorizonRailLayout,
    d: Float,
    screenW: Float,
    h: Float,
    isLandscape: Boolean
) {
    val isTextEnabled = renderCache.isTextEnabled
    val textOrientMode = renderCache.textOrientMode
    val isTextAllowedByOrientation = when (textOrientMode) {
        "landscape_only" -> isLandscape
        "portrait_only" -> !isLandscape
        else -> true
    }
    if (!isTextEnabled || !isTextAllowedByOrientation) return

    val primaryStream = layout.activeStreams[0]
    val textCasing = renderCache.textCasing

    fun applyCasing(str: String): String {
        val trimmed = str.trim()
        if (trimmed.isEmpty()) return ""
        return when (textCasing) {
            "all_caps" -> trimmed.uppercase()
            "title_case" -> trimmed.split(" ").joinToString(" ") { word ->
                word.lowercase().replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }
            }
            else -> trimmed
        }
    }

    val cleanTitle = applyCasing(primaryStream.title)
    val cleanSub = applyCasing(primaryStream.subtitle)
    val cleanAlbum = applyCasing(primaryStream.album)

    val metadataMode = renderCache.metadataMode
    val isShowTimestamp = renderCache.isShowTimestamp

    fun formatDuration(ms: Long): String {
        if (ms <= 0L) return ""
        val totalSec = ms / 1000
        val min = totalSec / 60
        val sec = totalSec % 60
        return "%d:%02d".format(min, sec)
    }

    val timestampStr = if (primaryStream.type == "media" && (isShowTimestamp || (metadataMode == "adaptive" && isLandscape))) {
        val pos = formatDuration(primaryStream.positionMs)
        val dur = formatDuration(primaryStream.durationMs)
        if (pos.isNotBlank() && dur.isNotBlank()) "$pos / $dur" else pos
    } else ""

    val isRichMode = when (metadataMode) {
        "full" -> true
        "title_only" -> false
        else -> isLandscape
    }

    val leftLabel: String
    val rightLabel: String

    if (primaryStream.type == "dl") {
        leftLabel = if (cleanTitle.isNotBlank()) "⬇ $cleanTitle" else "⬇ DOWNLOADING"
        rightLabel = if (cleanSub.isNotBlank()) cleanSub.replace("%", "").trim() + "%" else ""
    } else {
        leftLabel = cleanTitle.ifBlank { cleanSub }.ifBlank { "NOW PLAYING" }
        if (isRichMode) {
            val siders = mutableListOf<String>()
            if (cleanSub.isNotBlank() && !cleanSub.equals(cleanTitle, ignoreCase = true) && !cleanSub.equals("NOW PLAYING", ignoreCase = true)) {
                siders.add(cleanSub)
            }
            if (cleanAlbum.isNotBlank() && !cleanAlbum.equals(cleanTitle, ignoreCase = true) && !cleanAlbum.equals(cleanSub, ignoreCase = true)) {
                siders.add(cleanAlbum)
            }
            if (timestampStr.isNotBlank()) {
                siders.add(timestampStr)
            }
            rightLabel = siders.joinToString("  •  ")
        } else {
            rightLabel = if (cleanSub.isNotBlank() && !cleanSub.equals(cleanTitle, ignoreCase = true) && !cleanSub.equals("NOW PLAYING", ignoreCase = true)) {
                cleanSub
            } else {
                ""
            }
        }
    }

    val tickerText = when {
        leftLabel.isNotBlank() && rightLabel.isNotBlank() -> if (primaryStream.type == "dl") "$leftLabel  •  $rightLabel" else "$leftLabel — $rightLabel"
        leftLabel.isNotBlank() -> leftLabel
        else -> rightLabel
    }

    if (tickerText.isBlank()) return

    val textSizeDp = renderCache.textSizeDp
    val fontSetting = renderCache.fontSetting
    val tacticalTypeface: Typeface = when {
        fontSetting == "system_default" -> Typeface.DEFAULT_BOLD
        fontSetting.startsWith("/") -> {
            try {
                Typeface.createFromFile(java.io.File(fontSetting))
            } catch (_: Exception) {
                Typeface.DEFAULT_BOLD
            }
        }
        else -> {
            try {
                Typeface.create(fontSetting, Typeface.BOLD)
            } catch (_: Exception) {
                Typeface.DEFAULT_BOLD
            }
        }
    }

    val colorMode = renderCache.colorMode
    val microTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = textSizeDp * d
        typeface = tacticalTypeface
        letterSpacing = 0.05f
        color = if (colorMode == "inverted") layout.primaryColor else Color.WHITE
        style = Paint.Style.FILL
    }

    val isContrastShield = renderCache.isContrastShield
    val microTextOutlinePaint = if (isContrastShield) {
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = textSizeDp * d
            typeface = tacticalTypeface
            letterSpacing = 0.05f
            color = Color.argb(235, 6, 8, 14)
            style = Paint.Style.STROKE
            strokeWidth = (textSizeDp * 0.22f * d).coerceIn(1.5f * d, 3.2f * d)
            strokeJoin = Paint.Join.ROUND
            strokeCap = Paint.Cap.ROUND
        }
    } else null

    fun drawTacticalText(text: String, x: Float, y: Float) {
        if (text.isBlank()) return
        if (microTextOutlinePaint != null) {
            canvas.drawText(text, x, y, microTextOutlinePaint)
        }
        canvas.drawText(text, x, y, microTextPaint)
    }

    val textPos = renderCache.textPos
    val textOffsetY = renderCache.textOffsetY.toFloat() * d
    val fontMetrics = microTextPaint.fontMetrics
    val textBaselineOffset = -fontMetrics.ascent

    val textY = when (textPos) {
        "above" -> (layout.lineYs[0] - (layout.thicknesses[0] * d / 2f) - (1.5f * d) - fontMetrics.descent) + textOffsetY
        "embedded" -> (layout.lineYs[0] - (fontMetrics.ascent + fontMetrics.descent) / 2f) + textOffsetY
        "below_statusbar" -> (layout.sensorHeight + (2f * d) + textBaselineOffset) + textOffsetY
        else -> (layout.lineYs[0] + (layout.thicknesses[0] * d / 2f) + (1.5f * d) + textBaselineOffset) + textOffsetY
    }

    val speedDp = renderCache.speedDp
    val speedPx = speedDp * d
    val marqueeAnimMode = renderCache.marqueeAnimMode
    val marqueeDirection = renderCache.marqueeDirection
    val marqueeScope = renderCache.marqueeScope

    val isAvoidCutout = renderCache.isAvoidCutout
    val wingGapDp = renderCache.wingGapDp
    val wingGap = wingGapDp * d

    val rawCutout = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) rootWindowInsets?.displayCutout else null
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P && rawCutout != null) {
        val found = rawCutout.boundingRectTop ?: rawCutout.boundingRects.firstOrNull { it.top == 0 }
        if (found != null && found.width() > 0) {
            LightspeedNotchOverlay.cachedCutoutRect = found
        }
    }
    val topCutout = LightspeedNotchOverlay.cachedCutoutRect
    val defaultCutoutWidthDp = (topCutout?.width()?.toFloat()?.div(d) ?: 20f).toInt().coerceIn(14, 48)
    val cutoutWidthDp = (if (renderCache.customCutoutWidth > 0) renderCache.customCutoutWidth else defaultCutoutWidthDp).coerceIn(0, 72)
    val effectiveCutoutWidthPx = cutoutWidthDp.toFloat() * d

    val notchOffsetX = renderCache.customCutoutOffsetX.toFloat() * d
    val detectedCenterX = if (topCutout != null && topCutout.width() > 0) topCutout.exactCenterX() else screenW / 2f
    val cutoutCenterX = detectedCenterX + notchOffsetX

    val cutoutLeft = cutoutCenterX - (effectiveCutoutWidthPx / 2f)
    val cutoutRight = cutoutCenterX + (effectiveCutoutWidthPx / 2f)
    val isCenteredCutout = cutoutCenterX in (screenW * 0.20f)..(screenW * 0.80f)

    fun renderMarqueeText(
        text: String,
        clipLeft: Float,
        clipRight: Float,
        y: Float,
        alignTo: String = "left",
        forceStatic: Boolean = false
    ) {
        if (text.isBlank()) return
        val availW = (clipRight - clipLeft).coerceAtLeast(0f)
        if (availW <= 4f * d) return

        val textW = microTextPaint.measureText(text)

        if (textW <= availW || forceStatic) {
            val startX = when (alignTo) {
                "right" -> clipRight - textW
                "center" -> clipLeft + (availW - textW) / 2f
                else -> clipLeft
            }
            val safeText = if (textW > availW) {
                android.text.TextUtils.ellipsize(text, android.text.TextPaint(microTextPaint), availW, android.text.TextUtils.TruncateAt.END).toString()
            } else text

            canvas.save()
            canvas.clipRect(clipLeft, 0f, clipRight, h)
            drawTacticalText(safeText, startX.coerceIn(clipLeft, (clipRight - microTextPaint.measureText(safeText)).coerceAtLeast(clipLeft)), y)
            canvas.restore()
            return
        }

        val now = SystemClock.uptimeMillis()
        val speedPxPerMs = speedPx / 1000f

        canvas.save()
        canvas.clipRect(clipLeft, 0f, clipRight, h)

        if (marqueeAnimMode == "bounce") {
            val overflowPx = textW - availW
            val travelDist = overflowPx + (6f * d)
            val pauseMs = 1200L
            val travelDurationMs = ((travelDist / speedPxPerMs).toLong()).coerceIn(800L, 10000L)
            val totalCycleMs = (pauseMs * 2) + (travelDurationMs * 2)
            val cycleTime = now % totalCycleMs

            val offset = when {
                cycleTime < pauseMs -> 0f
                cycleTime < pauseMs + travelDurationMs -> {
                    val progress = (cycleTime - pauseMs).toFloat() / travelDurationMs.toFloat()
                    val eased = (1f - kotlin.math.cos(progress * Math.PI.toFloat())) / 2f
                    eased * travelDist
                }
                cycleTime < (2 * pauseMs) + travelDurationMs -> travelDist
                else -> {
                    val progress = (cycleTime - (2 * pauseMs + travelDurationMs)).toFloat() / travelDurationMs.toFloat()
                    val eased = (1f - kotlin.math.cos(progress * Math.PI.toFloat())) / 2f
                    travelDist * (1f - eased)
                }
            }

            val baseStartX = if (alignTo == "right") (clipRight - textW) else clipLeft
            val startX = if (marqueeDirection == "ltr") {
                baseStartX + offset
            } else {
                baseStartX - offset
            }
            drawTacticalText(text, startX, y)
        } else {
            val gapPx = (30f * d).coerceAtLeast(20f)
            val cycleDist = textW + gapPx
            val cycleDurationMs = ((cycleDist / speedPxPerMs).toLong()).coerceIn(1000L, 30000L)
            val elapsedMs = now % cycleDurationMs
            val rawOffset = (elapsedMs.toFloat() / cycleDurationMs.toFloat()) * cycleDist

            if (marqueeDirection == "ltr") {
                var x = clipLeft - textW + rawOffset
                while (x < clipRight) {
                    if (x + textW > clipLeft) {
                        drawTacticalText(text, x, y)
                    }
                    x += cycleDist
                }
            } else {
                var x = clipRight - rawOffset
                while (x + textW > clipLeft) {
                    if (x < clipRight) {
                        drawTacticalText(text, x, y)
                    }
                    x -= cycleDist
                }
                var forwardX = clipRight - rawOffset + cycleDist
                while (forwardX < clipRight) {
                    drawTacticalText(text, forwardX, y)
                    forwardX += cycleDist
                }
            }
        }

        canvas.restore()
        postInvalidateOnAnimation()
    }

    val isCutoutZeroed = (cutoutWidthDp == 0 && wingGapDp == 0f)
    val hasCutout = !isCutoutZeroed && (isCenteredCutout || (cutoutRight > layout.railLeft && cutoutLeft < layout.railRight))

    val isStatusBarGuard = renderCache.isStatusBarGuard
    val tickerClipLeft = if (isLandscape && isStatusBarGuard) {
        maxOf(layout.railLeft, (renderCache.safePaddingLeftDp + 42f) * d)
    } else {
        layout.railLeft
    }
    val tickerClipRight = if (isLandscape && isStatusBarGuard) {
        minOf(layout.railRight, screenW - ((renderCache.safePaddingRightDp + 58f) * d))
    } else {
        layout.railRight
    }

    if (isAvoidCutout && hasCutout) {
        val (wingLeftText, wingRightText) = if (leftLabel.isNotBlank() && rightLabel.isNotBlank() && marqueeScope != "unified") {
            Pair(leftLabel, rightLabel)
        } else {
            val fullText = if (leftLabel.isNotBlank() && rightLabel.isNotBlank()) {
                "$leftLabel  •  $rightLabel"
            } else {
                leftLabel.ifBlank { rightLabel }.ifBlank { tickerText }.trim()
            }

            val mid = fullText.length / 2
            var bestBreak = -1
            var minDiff = Int.MAX_VALUE
            for (i in fullText.indices) {
                if (fullText[i] == ' ' || fullText[i] == '-' || fullText[i] == '_' || fullText[i] == '•' || fullText[i] == '—' || fullText[i] == ':') {
                    val diff = kotlin.math.abs(i - mid)
                    if (diff < minDiff) {
                        minDiff = diff
                        bestBreak = i
                    }
                }
            }
            if (bestBreak in 1 until fullText.length - 1) {
                Pair(fullText.substring(0, bestBreak).trim(), fullText.substring(bestBreak + 1).trim())
            } else if (fullText.length > 2) {
                val splitPt = (fullText.length / 2).coerceIn(1, fullText.length - 1)
                Pair(fullText.substring(0, splitPt).trim(), fullText.substring(splitPt).trim())
            } else {
                Pair(fullText, "")
            }
        }

        val leftClipL = tickerClipLeft
        val leftClipR = (cutoutLeft - wingGap).coerceAtLeast(leftClipL)
        val rightClipL = (cutoutRight + wingGap).coerceAtMost(tickerClipRight)
        val rightClipR = tickerClipRight

        val forceStaticLeft = (marqueeScope == "right_wing_only")
        val forceStaticRight = (marqueeScope == "left_wing_only")

        if (wingLeftText.isNotBlank()) {
            renderMarqueeText(wingLeftText, leftClipL, leftClipR, textY, alignTo = "right", forceStatic = forceStaticLeft)
        }
        if (wingRightText.isNotBlank()) {
            renderMarqueeText(wingRightText, rightClipL, rightClipR, textY, alignTo = "left", forceStatic = forceStaticRight)
        }
    } else {
        renderMarqueeText(tickerText, tickerClipLeft, tickerClipRight, textY, alignTo = renderCache.railAlign, forceStatic = false)
    }
}
