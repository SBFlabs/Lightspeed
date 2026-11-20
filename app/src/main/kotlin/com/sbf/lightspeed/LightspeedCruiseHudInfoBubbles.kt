package com.sbf.lightspeed

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Typeface
import com.sbf.lightspeed.settings.DeckThemeManager

private val bubbleArrowPath = Path()

internal fun LightspeedCruiseOverlay.drawScreenTimeInfoBubble(canvas: Canvas) {
    if (!isScreenTimeInfoBubbleOpen) return
    val dp = resources.displayMetrics.density
    val anchor = screenTimeInfoBubbleRect
    if (anchor.isEmpty) return

    val w = width.toFloat()
    val bubbleW = (236f * dp).coerceAtMost(w - 24f * dp)
    val bubbleH = 68f * dp

    val bubbleLeft = (anchor.right - bubbleW).coerceIn(12f * dp, w - bubbleW - 12f * dp)
    val bubbleTop = anchor.bottom + 5f * dp
    val bounds = RectF(bubbleLeft, bubbleTop, bubbleLeft + bubbleW, bubbleTop + bubbleH)

    val snapshot = DeckThemeManager.getSnapshot(context)
    val tintColor = categoryAccentOverride() ?: snapshot.tintArgb.toInt()
    val accent = ensureHighContrastColor(tintColor)

    val bubblePaint = Paint(Paint.ANTI_ALIAS_FLAG)

    // Glass Card Surface
    bubblePaint.style = Paint.Style.FILL
    bubblePaint.color = Color.argb(245, 12, 16, 26)
    canvas.drawRoundRect(bounds, 8f * dp, 8f * dp, bubblePaint)

    // Tactical Accent Border
    bubblePaint.style = Paint.Style.STROKE
    bubblePaint.strokeWidth = 1.2f * dp
    bubblePaint.color = accent
    canvas.drawRoundRect(bounds, 8f * dp, 8f * dp, bubblePaint)

    // Pointer Triangle pointing up to info badge
    val arrowX = anchor.centerX().coerceIn(bounds.left + 12f * dp, bounds.right - 12f * dp)
    bubbleArrowPath.reset()
    bubbleArrowPath.moveTo(arrowX - 5f * dp, bounds.top)
    bubbleArrowPath.lineTo(arrowX, bounds.top - 5f * dp)
    bubbleArrowPath.lineTo(arrowX + 5f * dp, bounds.top)
    bubbleArrowPath.close()

    bubblePaint.style = Paint.Style.FILL
    bubblePaint.color = Color.argb(245, 12, 16, 26)
    canvas.drawPath(bubbleArrowPath, bubblePaint)

    bubblePaint.style = Paint.Style.STROKE
    bubblePaint.strokeWidth = 1.2f * dp
    bubblePaint.color = accent
    canvas.drawLine(arrowX - 5f * dp, bounds.top, arrowX, bounds.top - 5f * dp, bubblePaint)
    canvas.drawLine(arrowX, bounds.top - 5f * dp, arrowX + 5f * dp, bounds.top, bubblePaint)

    // Text Contents
    val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.LEFT
        typeface = Typeface.DEFAULT_BOLD
        textSize = 8.8f * dp
        color = accent
    }
    canvas.drawText("◈ SCREEN TIME TELEMETRY", bounds.left + 10f * dp, bounds.top + 16f * dp, textPaint)

    textPaint.typeface = Typeface.DEFAULT
    textPaint.textSize = 8.0f * dp
    textPaint.color = Color.WHITE
    canvas.drawText("• DAY: Today from 00:00 to now", bounds.left + 10f * dp, bounds.top + 31f * dp, textPaint)
    canvas.drawText("• WK / MO / YR: Rolling 7 / 30 / 365 days window", bounds.left + 10f * dp, bounds.top + 45f * dp, textPaint)

    textPaint.textSize = 7.2f * dp
    textPaint.color = Color.argb(160, 200, 220, 255)
    canvas.drawText("✦ Tap anywhere to dismiss", bounds.left + 10f * dp, bounds.top + 59f * dp, textPaint)
}

private fun wrapHudBubbleLines(text: String, paint: Paint, maxW: Float): List<String> {
    val out = ArrayList<String>()
    var cur = StringBuilder()
    for (word in text.split(' ')) {
        val cand = if (cur.isEmpty()) word else "$cur $word"
        if (paint.measureText(cand) <= maxW || cur.isEmpty()) {
            cur = StringBuilder(cand)
        } else {
            out.add(cur.toString())
            cur = StringBuilder(word)
        }
    }
    if (cur.isNotEmpty()) out.add(cur.toString())
    return out
}

internal fun LightspeedCruiseOverlay.drawBatterySpanInfoBubble(canvas: Canvas) {
    if (!isBatterySpanInfoBubbleOpen) return
    val dp = resources.displayMetrics.density
    val anchor = batterySpanInfoBubbleRect
    if (anchor.isEmpty) return

    val w = width.toFloat()
    val bubbleW = (264f * dp).coerceAtMost(w - 24f * dp)
    val padX = 11f * dp
    val maxW = bubbleW - padX * 2f

    val snapshot = DeckThemeManager.getSnapshot(context)
    val tintColor = categoryAccentOverride() ?: snapshot.tintArgb.toInt()
    val accent = ensureHighContrastColor(tintColor)
    val isPartial = LightspeedCruiseHudPods.isSpanPartial(batterySpanInfoKey)
    val borderAccent = if (isPartial) Color.rgb(245, 158, 11) else accent

    val availMs = LightspeedCruiseHudPods.getAvailableUsageSpanMs(batterySpanInfoKey)
    val availHours = (availMs / 3_600_000L).coerceAtLeast(0L)
    val availCompact = if (availHours >= 24L) {
        "${availHours}h (${availHours / 24L}d ${availHours % 24L}h)"
    } else if (availMs > 0L) {
        LightspeedCruiseHudPods.formatDurationCompact(availMs)
    } else {
        "collecting..."
    }

    val isSystemView = (batterySpanInfoMode == "system")
    val heading = when {
        isSystemView && isPartial -> "◈ SYSTEM · PARTIAL 7D SPAN"
        isSystemView -> "◈ SYSTEM VITALS · SPAN GUIDE"
        isPartial -> "◈ APPS · PARTIAL SPAN NOTICE"
        else -> "◈ APPS · SPAN COVERAGE"
    }

    val paragraphs: List<String> = if (isSystemView) {
        val screenLine = when (batterySpanInfoKey) {
            "today" -> "• SCREEN ON (TODAY): Active screen time since 00:00 midnight via Android Usage Stats."
            else -> if (isPartial) {
                "• SCREEN ON (7-DAY): Only $availCompact of 7 days is available from Android Usage Stats so far."
            } else {
                "• SCREEN ON (7-DAY): Full 7-day rolling screen time from Android Usage Stats."
            }
        }
        listOf(
            "• CPU AWAKE, CELLULAR & WI-FI: Hardware counters measured since last device reboot [BOOT].",
            screenLine
        )
    } else {
        when (batterySpanInfoKey) {
            "7d" -> if (isPartial) {
                listOf(
                    "• Ranks apps by foreground screen-time power estimates via Android Usage Stats.",
                    "• Partial 7-DAY span: Only $availCompact of 7 days is available from Android Usage Stats."
                )
            } else {
                listOf(
                    "• Ranks apps by foreground screen-time power estimates via Android Usage Stats.",
                    "• Full 7-day rolling window available."
                )
            }
            "last_charge" -> if (isPartial) {
                listOf(
                    "• Ranks apps by foreground screen-time power estimates via Android Usage Stats.",
                    "• Since boot (until next unplug): No unplug recorded yet — showing $availCompact since boot."
                )
            } else {
                listOf(
                    "• Ranks apps by foreground screen-time power estimates via Android Usage Stats.",
                    "• LAST CHG: Measured since your last unplug ($availCompact ago). Resets automatically on next unplug."
                )
            }
            else -> listOf(
                "• Ranks apps by foreground screen-time power estimates via Android Usage Stats.",
                "• TODAY: Measured from 00:00 midnight to now."
            )
        }
    }

    val bodyPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.LEFT
        typeface = Typeface.DEFAULT
        textSize = 8.4f * dp
        color = Color.WHITE
    }
    val wrappedLines = ArrayList<String>()
    for (p in paragraphs) {
        wrappedLines.addAll(wrapHudBubbleLines(p, bodyPaint, maxW))
    }

    val lineStep = 11.8f * dp
    val bubbleH = (24f * dp) + (wrappedLines.size * lineStep) + (20f * dp)
    val bubbleLeft = (anchor.right - bubbleW).coerceIn(12f * dp, w - bubbleW - 12f * dp)
    val bubbleTop = anchor.bottom + 5f * dp
    val bounds = RectF(bubbleLeft, bubbleTop, bubbleLeft + bubbleW, bubbleTop + bubbleH)

    val bubblePaint = Paint(Paint.ANTI_ALIAS_FLAG)
    bubblePaint.style = Paint.Style.FILL
    bubblePaint.color = Color.argb(248, 12, 16, 26)
    canvas.drawRoundRect(bounds, 8f * dp, 8f * dp, bubblePaint)

    bubblePaint.style = Paint.Style.STROKE
    bubblePaint.strokeWidth = 1.2f * dp
    bubblePaint.color = borderAccent
    canvas.drawRoundRect(bounds, 8f * dp, 8f * dp, bubblePaint)

    val arrowX = anchor.centerX().coerceIn(bounds.left + 12f * dp, bounds.right - 12f * dp)
    bubbleArrowPath.reset()
    bubbleArrowPath.moveTo(arrowX - 5f * dp, bounds.top)
    bubbleArrowPath.lineTo(arrowX, bounds.top - 5f * dp)
    bubbleArrowPath.lineTo(arrowX + 5f * dp, bounds.top)
    bubbleArrowPath.close()
    bubblePaint.style = Paint.Style.FILL
    bubblePaint.color = Color.argb(248, 12, 16, 26)
    canvas.drawPath(bubbleArrowPath, bubblePaint)
    bubblePaint.style = Paint.Style.STROKE
    bubblePaint.color = borderAccent
    canvas.drawLine(arrowX - 5f * dp, bounds.top, arrowX, bounds.top - 5f * dp, bubblePaint)
    canvas.drawLine(arrowX, bounds.top - 5f * dp, arrowX + 5f * dp, bounds.top, bubblePaint)

    val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.LEFT
        typeface = Typeface.DEFAULT_BOLD
        textSize = 9.0f * dp
        color = borderAccent
    }
    canvas.drawText(heading, bounds.left + padX, bounds.top + 16f * dp, titlePaint)

    var curY = bounds.top + 31f * dp
    for (ln in wrappedLines) {
        canvas.drawText(ln, bounds.left + padX, curY, bodyPaint)
        curY += lineStep
    }

    bodyPaint.textSize = 7.4f * dp
    bodyPaint.color = Color.argb(165, 200, 220, 255)
    canvas.drawText("✦ Tap anywhere to dismiss", bounds.left + padX, bounds.bottom - 7f * dp, bodyPaint)
}
