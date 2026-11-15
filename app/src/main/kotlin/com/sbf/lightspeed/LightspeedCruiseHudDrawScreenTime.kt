package com.sbf.lightspeed

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import com.sbf.lightspeed.system.LightspeedPreferences
import com.sbf.lightspeed.system.LightspeedUsageStats
import java.util.Calendar

/** Cruise HUD Screen Time & 24h Countdown Timeline widget renderer (split from LightspeedCruiseHudWidgetsDrawVitals.kt). */
internal fun LightspeedCruiseOverlay.drawScreenTimeHudWidget(
    canvas: Canvas,
    bounds: RectF,
    catId: String,
    accent: Int,
    density: Float,
    alpha: Float,
    slotIndex: Int = 1
) {
    val span = LightspeedCruiseHudPods.getTimeSpan(context, catId, slotIndex)
    val scope = LightspeedCruiseHudPods.getTimeScope(context, catId, slotIndex)
    val spanLabel = LightspeedCruiseHudPods.TIME_SPAN_OPTIONS.firstOrNull { it.first == span }?.second ?: "DAY"
    val scopeLabel = LightspeedCruiseHudPods.TIME_SCOPE_OPTIONS.firstOrNull { it.first == scope }?.second ?: "DEVICE"

    // Top Header with Main Widget Caption & Info Badge
    drawWidgetChipHeader(
        canvas = canvas,
        bounds = bounds,
        widgetTitle = "SCREEN TIME",
        primaryChip = "SCOPE: $scopeLabel",
        secondaryChip = "SPAN: $spanLabel",
        accent = accent,
        density = density,
        alpha = alpha,
        showInfoBadge = true,
        slotIndex = slotIndex
    )

    if (!LightspeedUsageStats.hasUsageAccess(context)) {
        textPaint.textAlign = Paint.Align.CENTER
        textPaint.typeface = Typeface.DEFAULT_BOLD
        textPaint.textSize = 12.5f * density
        textPaint.color = Color.WHITE
        canvas.drawText("SCREEN TIME TELEMETRY", bounds.centerX(), bounds.centerY() - 4f * density, textPaint)
        textPaint.typeface = Typeface.DEFAULT
        textPaint.textSize = 10f * density
        textPaint.color = accent
        canvas.drawText("TAP CARD TO GRANT USAGE STATS ACCESS", bounds.centerX(), bounds.centerY() + 14f * density, textPaint)
        return
    }

    val usageMap = LightspeedCruiseHudPods.getUsageMap(span)
    val totalMs = when (scope) {
        "sec" -> cachedApps.sumOf { usageMap[it.packageName] ?: 0L }
        else -> usageMap.values.sum()
    }

    val totalStr = LightspeedCruiseHudPods.flipDigitsIfActive(
        LightspeedCruiseHudPods.formatDurationCompact(totalMs),
        seedOffset = 1
    )

    // Left Column: Sub-caption & Big Total (fitted cleanly before rightColLeft)
    val rightColLeft = bounds.left + (bounds.width() * 0.42f)
    val leftColX = bounds.left + 14f * density
    val leftColMaxW = (rightColLeft - leftColX - 8f * density).coerceAtLeast(36f * density)

    textPaint.textAlign = Paint.Align.LEFT
    textPaint.typeface = Typeface.DEFAULT_BOLD
    textPaint.textSize = 9.6f * density
    textPaint.color = accent
    canvas.drawText(clipTextToWidth("TOTAL TIME", leftColMaxW), leftColX, bounds.top + 40f * density, textPaint)

    val baseHeroSize = 18.5f * density
    textPaint.textSize = baseHeroSize
    val heroMeasuredW = textPaint.measureText(totalStr)
    if (heroMeasuredW > leftColMaxW && heroMeasuredW > 0f) {
        textPaint.textSize = (baseHeroSize * (leftColMaxW / heroMeasuredW)).coerceAtLeast(11.5f * density)
    }
    textPaint.color = Color.WHITE
    canvas.drawText(totalStr, leftColX, bounds.top + 60f * density, textPaint)

    textPaint.typeface = Typeface.DEFAULT
    textPaint.textSize = 9.5f * density
    textPaint.color = Color.argb(215, 200, 220, 245)
    val scopeDesc = if (scope == "sec") "THIS SECTOR" else "TOTAL DEVICE"
    canvas.drawText(clipTextToWidth("$scopeDesc · $spanLabel", leftColMaxW), leftColX, bounds.top + 74f * density, textPaint)

    // Right Column: Scrollable apps breakdown with proportional bars
    val rightColRight = bounds.right - 14f * density
    val barW = rightColRight - rightColLeft

    val candidateApps = if (scope == "sec") cachedApps else dataBridge.getAllApps()
    val allScopedApps = candidateApps
        .map { it to (usageMap[it.packageName] ?: 0L) }
        .filter { it.second > 0L }
        .sortedByDescending { it.second }

    val listTop = bounds.top + 47f * density
    val listBottom = bounds.bottom - 33f * density
    val listH = listBottom - listTop
    val itemH = 21.5f * density
    val totalAppsH = allScopedApps.size * itemH
    screenTimeAppsMaxScroll = kotlin.math.max(0f, totalAppsH - listH)
    screenTimeAppsScrollOffset = screenTimeAppsScrollOffset.coerceIn(0f, screenTimeAppsMaxScroll)

    val listHeader = if (allScopedApps.size > 2) "APPS (${allScopedApps.size}) · SCROLL" else "TOP APPS"
    textPaint.textAlign = Paint.Align.LEFT
    textPaint.typeface = Typeface.DEFAULT_BOLD
    textPaint.textSize = 9.6f * density
    textPaint.color = accent
    canvas.drawText(listHeader, rightColLeft, bounds.top + 40f * density, textPaint)

    if (allScopedApps.isEmpty()) {
        textPaint.textAlign = Paint.Align.LEFT
        textPaint.typeface = Typeface.DEFAULT
        textPaint.textSize = 10.4f * density
        textPaint.color = Color.argb(150, 200, 220, 245)
        canvas.drawText("NO USAGE LOGGED", rightColLeft, bounds.top + 60f * density, textPaint)
    } else {
        canvas.save()
        canvas.clipRect(rightColLeft - 4f * density, listTop - 1f * density, rightColRight + 4f * density, listBottom)
        canvas.translate(0f, -screenTimeAppsScrollOffset)

        var rowY = listTop + 11.5f * density
        var appIdx = 0
        for ((appItem, ms) in allScopedApps) {
            val appDurStr = LightspeedCruiseHudPods.flipDigitsIfActive(
                LightspeedCruiseHudPods.formatDurationCompact(ms),
                seedOffset = 10 + appIdx
            )
            appIdx++
            textPaint.textAlign = Paint.Align.RIGHT
            textPaint.typeface = Typeface.DEFAULT_BOLD
            textPaint.textSize = 10.6f * density
            textPaint.color = accent
            val durW = textPaint.measureText(appDurStr)
            canvas.drawText(appDurStr, rightColRight, rowY, textPaint)

            textPaint.textAlign = Paint.Align.LEFT
            textPaint.typeface = Typeface.DEFAULT_BOLD
            textPaint.textSize = 11.0f * density
            textPaint.color = Color.WHITE
            val maxNameW = (rightColRight - durW - rightColLeft - 6f * density).coerceAtLeast(28f * density)
            canvas.drawText(clipTextToWidth(appItem.label, maxNameW), rightColLeft, rowY, textPaint)

            // Progress bar
            val barRatio = if (totalMs > 0L) (ms.toFloat() / totalMs).coerceIn(0.04f, 1f) else 0.04f
            val barTop = rowY + 3.5f * density
            val barRectBg = RectF(rightColLeft, barTop, rightColRight, barTop + 4.2f * density)
            val barRectFill = RectF(rightColLeft, barTop, rightColLeft + (barW * barRatio), barTop + 4.2f * density)
            highlightPaint.style = Paint.Style.FILL
            highlightPaint.color = Color.argb(40, 255, 255, 255)
            canvas.drawRoundRect(barRectBg, 2f * density, 2f * density, highlightPaint)
            highlightPaint.color = accent
            canvas.drawRoundRect(barRectFill, 2f * density, 2f * density, highlightPaint)

            rowY += itemH
        }
        canvas.restore()

        // Sleek scrollbar indicator when apps exceed visible space
        if (screenTimeAppsMaxScroll > 0f) {
            val sbW = 2.5f * density
            val sbX = rightColRight + 6f * density
            val sbRatio = (listH / totalAppsH).coerceIn(0.15f, 0.8f)
            val thumbH = listH * sbRatio
            val thumbY = listTop + ((screenTimeAppsScrollOffset / screenTimeAppsMaxScroll) * (listH - thumbH))
            highlightPaint.style = Paint.Style.FILL
            highlightPaint.color = Color.argb(60, 255, 255, 255)
            canvas.drawRoundRect(RectF(sbX, listTop, sbX + sbW, listBottom), 1.5f * density, 1.5f * density, highlightPaint)
            highlightPaint.color = accent
            canvas.drawRoundRect(RectF(sbX, thumbY, sbX + sbW, thumbY + thumbH), 1.5f * density, 1.5f * density, highlightPaint)
        }
    }

    // Bottom Geometric Day / Week / Month / Year Timeline + Custom Target Countdown Pill
    val isCountdownEnabled = prefs().getBoolean(LightspeedPreferences.KEY_TIMELINE_COUNTDOWN_ENABLED, false)
    val cdTargetMs = prefs().getLong(LightspeedPreferences.KEY_TIMELINE_COUNTDOWN_TARGET_MS, 0L)
    val cdTitle = prefs().getString(LightspeedPreferences.KEY_TIMELINE_COUNTDOWN_TITLE, "")?.trim()?.ifBlank { "TARGET" } ?: "TARGET"
    val now = System.currentTimeMillis()
    val hasActiveCountdown = isCountdownEnabled && cdTargetMs > now

    val cal = Calendar.getInstance()
    val hrsLeftDay = (24 - cal.get(Calendar.HOUR_OF_DAY)).coerceAtLeast(0)
    val dow = cal.get(Calendar.DAY_OF_WEEK)
    val firstDow = cal.firstDayOfWeek
    val dayIndexInWeek = (dow - firstDow + 7) % 7
    val daysLeftWk = (6 - dayIndexInWeek).coerceAtLeast(0)
    val daysInMo = cal.getActualMaximum(Calendar.DAY_OF_MONTH).coerceAtLeast(28)
    val daysLeftMo = (daysInMo - cal.get(Calendar.DAY_OF_MONTH)).coerceAtLeast(0)
    val daysInYr = cal.getActualMaximum(Calendar.DAY_OF_YEAR).coerceAtLeast(365)
    val daysLeftYr = (daysInYr - cal.get(Calendar.DAY_OF_YEAR)).coerceAtLeast(0)
    val horizonsText = "D:${hrsLeftDay}h · W:${daysLeftWk}d · M:${daysLeftMo}d · Y:${daysLeftYr}d"

    val pillH = if (hasActiveCountdown) 26f * density else 23f * density
    val pillY = bounds.bottom - pillH - 4f * density
    val pillW = bounds.width() - 18f * density
    val pillRect = RectF(bounds.centerX() - pillW / 2f, pillY, bounds.centerX() + pillW / 2f, pillY + pillH)

    highlightPaint.style = Paint.Style.FILL
    highlightPaint.color = Color.argb((40 * alpha).toInt(), Color.red(accent), Color.green(accent), Color.blue(accent))
    canvas.drawRoundRect(pillRect, 4f * density, 4f * density, highlightPaint)

    highlightPaint.style = Paint.Style.STROKE
    highlightPaint.strokeWidth = 0.8f * density
    highlightPaint.color = Color.argb((85 * alpha).toInt(), Color.red(accent), Color.green(accent), Color.blue(accent))
    canvas.drawRoundRect(pillRect, 4f * density, 4f * density, highlightPaint)

    if (hasActiveCountdown) {
        val diffMs = cdTargetMs - now
        val days = diffMs / (1000L * 60 * 60 * 24)
        val hrs = (diffMs / (1000L * 60 * 60)) % 24
        val mins = (diffMs / (1000L * 60)) % 60

        LightspeedHudVectorIcons.drawVectorClock(
            canvas = canvas,
            cx = pillRect.left + 11.5f * density,
            cy = pillRect.top + 7.8f * density,
            r = 3.8f * density,
            color = accent,
            strokeW = 1.25f * density
        )
        textPaint.textAlign = Paint.Align.LEFT
        textPaint.typeface = Typeface.DEFAULT_BOLD
        textPaint.textSize = 8.4f * density
        textPaint.color = accent
        canvas.drawText("${cdTitle.take(12)}: ${days}d ${hrs}h ${mins}m", pillRect.left + 18f * density, pillRect.top + 10.5f * density, textPaint)

        LightspeedHudVectorIcons.drawVectorArrow(
            canvas = canvas,
            cx = pillRect.right - 9.5f * density,
            cy = pillRect.bottom - 7.8f * density,
            r = 3.4f * density,
            direction = 2,
            color = Color.argb(225, 255, 255, 255),
            strokeW = 1.3f * density,
            chevronOnly = true
        )
        textPaint.textAlign = Paint.Align.RIGHT
        textPaint.typeface = Typeface.MONOSPACE
        textPaint.textSize = 8.2f * density
        textPaint.color = Color.argb(225, 255, 255, 255)
        canvas.drawText(horizonsText, pillRect.right - 16f * density, pillRect.bottom - 5f * density, textPaint)
    } else {
        val labelSize = if (pillRect.width() < 230f * density) 9.5f * density else 10.2f * density
        val leftPad = 7f * density
        val rightPad = 7f * density
        val clockR = 4.2f * density
        val clockCx = pillRect.left + leftPad + clockR
        LightspeedHudVectorIcons.drawVectorClock(
            canvas = canvas,
            cx = clockCx,
            cy = pillRect.centerY(),
            r = clockR,
            color = accent,
            strokeW = 1.3f * density
        )
        textPaint.textAlign = Paint.Align.LEFT
        textPaint.typeface = Typeface.DEFAULT_BOLD
        textPaint.textSize = labelSize
        textPaint.color = accent
        val tlLabel = "TIMELINE"
        val tlTextLeft = clockCx + clockR + 4.5f * density
        val tlLabelW = (clockR * 2f + 4.5f * density) + textPaint.measureText(tlLabel)
        val tlFm = textPaint.fontMetrics
        canvas.drawText(
            tlLabel,
            tlTextLeft,
            pillRect.centerY() - ((tlFm.descent + tlFm.ascent) / 2f),
            textPaint
        )

        val chevR = 3.6f * density
        val chevCx = pillRect.right - rightPad - chevR * 0.6f
        textPaint.textAlign = Paint.Align.RIGHT
        textPaint.typeface = Typeface.MONOSPACE
        val baseHorizonsSize = 10.2f * density
        textPaint.textSize = baseHorizonsSize
        val horizonsDisplay = horizonsText
        val availRightW = (pillRect.width() - leftPad - rightPad - tlLabelW - 18f * density).coerceAtLeast(48f * density)
        val measuredRightW = textPaint.measureText(horizonsDisplay)
        if (measuredRightW > availRightW && measuredRightW > 0f) {
            textPaint.textSize = (baseHorizonsSize * (availRightW / measuredRightW)).coerceAtLeast(8.8f * density)
        }
        textPaint.color = Color.argb(235, 255, 255, 255)
        val hFm = textPaint.fontMetrics
        canvas.drawText(
            horizonsDisplay,
            chevCx - chevR - 3.5f * density,
            pillRect.centerY() - ((hFm.descent + hFm.ascent) / 2f),
            textPaint
        )
        LightspeedHudVectorIcons.drawVectorArrow(
            canvas = canvas,
            cx = chevCx,
            cy = pillRect.centerY(),
            r = chevR,
            direction = 2,
            color = Color.argb(235, 255, 255, 255),
            strokeW = 1.4f * density,
            chevronOnly = true
        )
    }
}
