package com.sbf.lightspeed

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Typeface
import com.sbf.lightspeed.settings.DeckThemeManager
import com.sbf.lightspeed.system.ElevatedTaskCloser
import com.sbf.lightspeed.system.lsString

private val bubbleArrowPath = Path()

internal fun LightspeedCruiseOverlay.drawScreenTimeInfoBubble(canvas: Canvas) {
    if (!isScreenTimeInfoBubbleOpen) return
    val dp = resources.displayMetrics.density
    val anchor = screenTimeInfoBubbleRect
    if (anchor.isEmpty) return

    val w = width.toFloat()
    val bubbleW = (252f * dp).coerceAtMost(w - 24f * dp)
    val padX = 10f * dp
    val maxW = bubbleW - padX * 2f

    val bodyPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.LEFT
        typeface = Typeface.DEFAULT
        textSize = 8.0f * dp
        color = Color.WHITE
    }
    val wkFull = LightspeedCruiseHudPods.weekCycleFullLabel(LightspeedCruiseHudPods.getWeekCycleMode(context))
    val paragraphs = listOf(
        "• DAY: Today from 00:00 to now",
        "• WK / MO / YR: Rolling 7 / 30 / 365 days window",
        lsString(R.string.hud_st_info_wk_mode, wkFull)
    )
    val wrappedLines = ArrayList<String>()
    for (p in paragraphs) {
        wrappedLines.addAll(wrapHudBubbleLines(p, bodyPaint, maxW))
    }

    val lineStep = 12.2f * dp
    val bubbleH = ((24f * dp) + (wrappedLines.size * lineStep) + (20f * dp)).coerceAtLeast(68f * dp)
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
    val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.LEFT
        typeface = Typeface.DEFAULT_BOLD
        textSize = 8.8f * dp
        color = accent
    }
    canvas.drawText("◈ SCREEN TIME TELEMETRY", bounds.left + padX, bounds.top + 16f * dp, titlePaint)

    var curY = bounds.top + 31f * dp
    for (ln in wrappedLines) {
        canvas.drawText(ln, bounds.left + padX, curY, bodyPaint)
        curY += lineStep
    }

    bodyPaint.textSize = 7.2f * dp
    bodyPaint.color = Color.argb(160, 200, 220, 255)
    canvas.drawText("✦ Tap anywhere to dismiss", bounds.left + padX, bounds.bottom - 7f * dp, bodyPaint)
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
    val isSystemView = (batterySpanInfoMode == "system")
    val isPartial = if (isSystemView) {
        LightspeedCruiseHudPods.isSpanPartial(batterySpanInfoKey)
    } else {
        ElevatedTaskCloser.isShizukuActive &&
            LightspeedShizukuBatteryDrainStore.isSpanPartial(context, batterySpanInfoKey)
    }
    val borderAccent = if (isPartial) Color.rgb(245, 158, 11) else accent

    val availMs = if (isSystemView) {
        LightspeedCruiseHudPods.getAvailableUsageSpanMs(batterySpanInfoKey)
    } else {
        LightspeedShizukuBatteryDrainStore.getAvailableSpanMs(context)
    }
    val availHours = (availMs / 3_600_000L).coerceAtLeast(0L)
    val availCompact = if (availHours >= 24L) {
        "${availHours}h (${availHours / 24L}d ${availHours % 24L}h)"
    } else if (availMs > 0L) {
        LightspeedCruiseHudPods.formatDurationCompact(availMs)
    } else {
        "collecting..."
    }

    val heading = when {
        isSystemView && isPartial -> "◈ SYSTEM · PARTIAL ${batterySpanInfoKey.uppercase()} SPAN"
        isSystemView -> "◈ SYSTEM VITALS · SPAN GUIDE"
        isPartial -> lsString(R.string.hud_apps_info_heading_partial).ifBlank { "◈ APPS · PARTIAL 7D DRAIN" }
        else -> lsString(R.string.hud_apps_info_heading).ifBlank { "◈ APPS · UNPLUGGED POWER DRAIN" }
    }

    val paragraphs: List<String> = if (isSystemView) {
        val hasCellular = LightspeedNetworkUsageAnalyzer.hasCellularCapability(context)
        val screenLine = when (batterySpanInfoKey) {
            "1h" -> "• SCREEN ON (1H): Foreground screen time over the last 60 minutes via Usage Stats."
            "3h" -> "• SCREEN ON (3H): Foreground screen time over the last 3 hours via Usage Stats."
            "today" -> "• SCREEN ON (TODAY): Active screen time since 00:00 midnight via Android Usage Stats."
            "3d" -> if (isPartial) {
                "• SCREEN ON (3-DAY): Only $availCompact of 3 days is available from Android Usage Stats so far."
            } else {
                "• SCREEN ON (3-DAY): Full 3-day rolling screen time from Android Usage Stats."
            }
            "30d" -> if (isPartial) {
                "• SCREEN ON (30-DAY): Only $availCompact of 30 days is available from Android Usage Stats so far."
            } else {
                "• SCREEN ON (30-DAY): Full 30-day rolling screen time from Android Usage Stats."
            }
            else -> if (isPartial) {
                "• SCREEN ON (7-DAY): Only $availCompact of 7 days is available from Android Usage Stats so far."
            } else {
                "• SCREEN ON (7-DAY): Full 7-day rolling screen time from Android Usage Stats."
            }
        }
        val netLine = if (hasCellular) {
            "• CELLULAR & WI-FI: Real Android NetworkStatsManager totals for 1H / 3H / TODAY / 3D / 7D / 30D (falls back to [BOOT] counters if Usage Access is off)."
        } else {
            "• WI-FI: Real Android NetworkStatsManager totals for 1H / 3H / TODAY / 3D / 7D / 30D (falls back to [BOOT] counters if Usage Access is off)."
        }
        val tapLine = if (hasCellular) {
            "• TAP CELLULAR OR WI-FI ROWS to open App Hunter's per-app Network breakdown."
        } else {
            "• TAP THE WI-FI ROW to open App Hunter's per-app Network breakdown."
        }
        listOf(netLine, screenLine, tapLine)
    } else {
        val spanLine = when (batterySpanInfoKey) {
            "last_charge" -> lsString(R.string.hud_apps_info_span_last_chg)
            "today" -> lsString(R.string.hud_apps_info_span_today)
            else -> if (isPartial) {
                lsString(R.string.hud_apps_info_span_7d_partial, availCompact)
            } else {
                lsString(R.string.hud_apps_info_span_7d_full)
            }
        }
        listOf(
            lsString(R.string.hud_apps_info_shizuku_line),
            spanLine
        )
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
