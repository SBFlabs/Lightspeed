package com.sbf.lightspeed

import com.sbf.lightspeed.system.lsString

import com.sbf.lightspeed.R

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import com.sbf.lightspeed.system.LightspeedPreferences
import com.sbf.lightspeed.system.LightspeedUsageStats
import java.util.Calendar

private val stScratchRect = RectF()
private val stCountdownPillRect = RectF()
private val stCountdownClipPath = android.graphics.Path()

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
        widgetTitle = com.sbf.lightspeed.system.lsString(R.string.hud_screen_time).ifBlank { "SCREEN TIME" },
        primaryChip = "SCOPE: $scopeLabel",
        secondaryChip = lsString(R.string.hud_span_chip, spanLabel),
        accent = accent,
        density = density,
        alpha = alpha,
        showInfoBadge = true,
        slotIndex = slotIndex
    )

    val wellbeingBtnRect = vitalsRectForSlot(screenTimeWellbeingBtnRects, slotIndex)
    if (!LightspeedUsageStats.hasUsageAccess(context)) {
        wellbeingBtnRect.setEmpty()
        textPaint.textAlign = Paint.Align.CENTER
        textPaint.typeface = overlayTypeface(isBold = true)
        textPaint.textSize = 12.5f * density
        textPaint.color = Color.WHITE
        canvas.drawText(com.sbf.lightspeed.system.lsString(R.string.hud_st_telemetry).ifBlank { "SCREEN TIME TELEMETRY" }, bounds.centerX(), bounds.centerY() - 4f * density, textPaint)
        textPaint.typeface = overlayTypeface(isBold = false)
        textPaint.textSize = 10f * density
        textPaint.color = accent
        canvas.drawText(com.sbf.lightspeed.system.lsString(R.string.hud_st_grant).ifBlank { "TAP CARD TO GRANT USAGE STATS ACCESS" }, bounds.centerX(), bounds.centerY() + 14f * density, textPaint)
        return
    }

    val ignoredPkgs = LightspeedCruiseHudPods.getScreenTimeIgnoredPackages(context)
    val usageMap = LightspeedCruiseHudPods.getUsageMap(span)
    val totalMs = when (scope) {
        "sec" -> cachedApps.filterNot { it.packageName in ignoredPkgs }.sumOf { usageMap[it.packageName] ?: 0L }
        else -> usageMap.entries.filterNot { it.key in ignoredPkgs }.sumOf { it.value }
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
    textPaint.typeface = overlayTypeface(isBold = true)
    textPaint.textSize = 9.6f * density
    textPaint.color = accent
    canvas.drawText(clipTextToWidth(com.sbf.lightspeed.system.lsString(R.string.hud_st_total_time).ifBlank { "TOTAL TIME" }, leftColMaxW), leftColX, bounds.top + 40f * density, textPaint)

    val baseHeroSize = 18.5f * density
    textPaint.textSize = baseHeroSize
    val heroMeasuredW = textPaint.measureText(totalStr)
    if (heroMeasuredW > leftColMaxW && heroMeasuredW > 0f) {
        textPaint.textSize = (baseHeroSize * (leftColMaxW / heroMeasuredW)).coerceAtLeast(11.5f * density)
    }
    textPaint.color = Color.WHITE
    canvas.drawText(totalStr, leftColX, bounds.top + 60f * density, textPaint)

    textPaint.typeface = overlayTypeface(isBold = false)
    textPaint.textSize = 9.5f * density
    textPaint.color = Color.argb(215, 200, 220, 245)
    val scopeDesc = if (scope == "sec") lsString(R.string.hud_this_sector) else lsString(R.string.hud_total_device)
    canvas.drawText(clipTextToWidth("$scopeDesc · $spanLabel", leftColMaxW), leftColX, bounds.top + 74f * density, textPaint)

    // Calendar progression gauges (DAY / WK / MO / YR) + Ignore Apps button in left 1/3 column
    val calNow = Calendar.getInstance()
    val dayRatio = ((calNow.get(Calendar.HOUR_OF_DAY) * 60f + calNow.get(Calendar.MINUTE)) / (24f * 60f)).coerceIn(0.02f, 1f)
    val wkMode = LightspeedCruiseHudPods.getWeekCycleMode(context)
    val wkRatio = LightspeedCruiseHudPods.computeWeekProgressRatio(calNow, wkMode)
    val daysInMoNow = calNow.getActualMaximum(Calendar.DAY_OF_MONTH).coerceAtLeast(28)
    val moRatio = (((calNow.get(Calendar.DAY_OF_MONTH) - 1) * 24f + calNow.get(Calendar.HOUR_OF_DAY)) / (daysInMoNow * 24f)).coerceIn(0.02f, 1f)
    val daysInYrNow = calNow.getActualMaximum(Calendar.DAY_OF_YEAR).coerceAtLeast(365)
    val yrRatio = (((calNow.get(Calendar.DAY_OF_YEAR) - 1) * 24f + calNow.get(Calendar.HOUR_OF_DAY)) / (daysInYrNow * 24f)).coerceIn(0.02f, 1f)

    val leftBotLimit = bounds.bottom - 33f * density
    val ignoreBtnH = 16.5f * density
    val ignoreBtnRect = vitalsRectForSlot(screenTimeIgnoreBtnRects, slotIndex)
    val wkBarRect = vitalsRectForSlot(screenTimeWkBarRects, slotIndex)
    wkBarRect.setEmpty()
    val progressStartTop = bounds.top + 80f * density
    val availLeftH = leftBotLimit - progressStartTop
    if (availLeftH >= 20f * density) {
        val btnTop = leftBotLimit - ignoreBtnH
        ignoreBtnRect.set(leftColX, btnTop, leftColX + leftColMaxW, leftBotLimit)
        highlightPaint.style = Paint.Style.FILL
        highlightPaint.color = Color.argb((38 * alpha).toInt(), Color.red(accent), Color.green(accent), Color.blue(accent))
        canvas.drawRoundRect(ignoreBtnRect, 4.5f * density, 4.5f * density, highlightPaint)
        highlightPaint.style = Paint.Style.STROKE
        highlightPaint.strokeWidth = 0.8f * density
        highlightPaint.color = Color.argb((95 * alpha).toInt(), Color.red(accent), Color.green(accent), Color.blue(accent))
        canvas.drawRoundRect(ignoreBtnRect, 4.5f * density, 4.5f * density, highlightPaint)

        val ignoreLabel = if (ignoredPkgs.isEmpty()) "IGNORE APPS" else "IGNORED (${ignoredPkgs.size})"
        textPaint.textAlign = Paint.Align.CENTER
        textPaint.typeface = overlayTypeface(isBold = true)
        textPaint.textSize = 7.4f * density
        textPaint.color = accent
        val igFm = textPaint.fontMetrics
        canvas.drawText(
            clipTextToWidth(ignoreLabel, leftColMaxW - 6f * density),
            ignoreBtnRect.centerX(),
            ignoreBtnRect.centerY() - ((igFm.descent + igFm.ascent) / 2f),
            textPaint
        )

        val gaugesAvailH = (btnTop - 4f * density) - progressStartTop
        val gauges = if (gaugesAvailH >= 46f * density) {
            listOf("DAY" to dayRatio, "WK" to wkRatio, "MO" to moRatio, "YR" to yrRatio)
        } else if (gaugesAvailH >= 32f * density) {
            listOf("WK" to wkRatio, "MO" to moRatio, "YR" to yrRatio)
        } else if (gaugesAvailH >= 18f * density) {
            listOf("MO" to moRatio, "YR" to yrRatio)
        } else {
            emptyList()
        }
        if (gauges.isNotEmpty()) {
            val rowStep = (gaugesAvailH / gauges.size).coerceAtMost(15f * density)
            val labelW = 22f * density
            val pctW = 22f * density
            val gBarLeft = leftColX + labelW
            val gBarRight = (leftColX + leftColMaxW - pctW).coerceAtLeast(gBarLeft + 14f * density)
            val gBarH = 3.6f * density
            for ((idx, pair) in gauges.withIndex()) {
                val (gLabel, ratio) = pair
                val cy = progressStartTop + idx * rowStep + rowStep * 0.5f
                if (gLabel == "WK") {
                    wkBarRect.set(leftColX, cy - rowStep * 0.5f, leftColX + leftColMaxW, cy + rowStep * 0.5f)
                }
                textPaint.textAlign = Paint.Align.LEFT
                textPaint.typeface = Typeface.MONOSPACE
                textPaint.textSize = 7.2f * density
                textPaint.color = Color.argb(210, 200, 220, 245)
                val gFm = textPaint.fontMetrics
                val baseY = cy - ((gFm.descent + gFm.ascent) / 2f)
                canvas.drawText(gLabel, leftColX, baseY, textPaint)

                val bTop = cy - gBarH * 0.5f
                highlightPaint.style = Paint.Style.FILL
                highlightPaint.color = Color.argb(42, 255, 255, 255)
                stScratchRect.set(gBarLeft, bTop, gBarRight, bTop + gBarH)
                canvas.drawRoundRect(stScratchRect, 1.8f * density, 1.8f * density, highlightPaint)
                highlightPaint.color = accent
                val fillW = ((gBarRight - gBarLeft) * ratio).coerceAtLeast(2.5f * density)
                stScratchRect.set(gBarLeft, bTop, gBarLeft + fillW, bTop + gBarH)
                canvas.drawRoundRect(stScratchRect, 1.8f * density, 1.8f * density, highlightPaint)

                textPaint.textAlign = Paint.Align.RIGHT
                textPaint.textSize = 7.0f * density
                textPaint.color = accent
                canvas.drawText("${(ratio * 100f).toInt()}%", leftColX + leftColMaxW, baseY, textPaint)
            }
        }
    } else {
        ignoreBtnRect.setEmpty()
    }

    // Right Column: Scrollable apps breakdown with proportional bars
    val rightColRight = bounds.right - 14f * density
    val barW = rightColRight - rightColLeft

    val candidateApps = if (scope == "sec") cachedApps else dataBridge.getAllApps()
    val allScopedApps = candidateApps
        .filterNot { it.packageName in ignoredPkgs }
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

    val wbPillW = 60f * density
    val wbPillH = 12.5f * density
    val wbPillTop = bounds.top + 30.5f * density
    val wbPillLeft = rightColRight - wbPillW
    wellbeingBtnRect.set(wbPillLeft, wbPillTop, rightColRight, wbPillTop + wbPillH)
    highlightPaint.style = Paint.Style.FILL
    highlightPaint.color = Color.argb((34 * alpha).toInt(), Color.red(accent), Color.green(accent), Color.blue(accent))
    canvas.drawRoundRect(wellbeingBtnRect, 4f * density, 4f * density, highlightPaint)
    highlightPaint.style = Paint.Style.STROKE
    highlightPaint.strokeWidth = 0.7f * density
    highlightPaint.color = Color.argb((95 * alpha).toInt(), Color.red(accent), Color.green(accent), Color.blue(accent))
    canvas.drawRoundRect(wellbeingBtnRect, 4f * density, 4f * density, highlightPaint)
    textPaint.textAlign = Paint.Align.CENTER
    textPaint.typeface = Typeface.MONOSPACE
    textPaint.textSize = 7.0f * density
    textPaint.color = accent
    val wbFm = textPaint.fontMetrics
    canvas.drawText("WELLBEING ↗", wellbeingBtnRect.centerX(), wellbeingBtnRect.centerY() - ((wbFm.descent + wbFm.ascent) / 2f), textPaint)

    val listHeader = if (allScopedApps.size > 2) "APPS (${allScopedApps.size}) · SCROLL" else "TOP APPS"
    textPaint.textAlign = Paint.Align.LEFT
    textPaint.typeface = overlayTypeface(isBold = true)
    textPaint.textSize = 9.6f * density
    textPaint.color = accent
    val maxListHdrW = (wbPillLeft - rightColLeft - 6f * density).coerceAtLeast(36f * density)
    canvas.drawText(clipTextToWidth(listHeader, maxListHdrW), rightColLeft, bounds.top + 40f * density, textPaint)

    if (allScopedApps.isEmpty()) {
        textPaint.textAlign = Paint.Align.LEFT
        textPaint.typeface = overlayTypeface(isBold = false)
        textPaint.textSize = 10.4f * density
        textPaint.color = Color.argb(150, 200, 220, 245)
        canvas.drawText(com.sbf.lightspeed.system.lsString(R.string.hud_st_no_usage).ifBlank { "NO USAGE LOGGED" }, rightColLeft, bounds.top + 60f * density, textPaint)
    } else {
        val listLayerCount = canvas.saveLayer(rightColLeft - 4f * density, listTop - 1f * density, rightColRight + 4f * density, listBottom, null)
        canvas.clipRect(rightColLeft - 4f * density, listTop - 1f * density, rightColRight + 4f * density, listBottom)
        canvas.save()
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
            textPaint.typeface = Typeface.MONOSPACE
            textPaint.textSize = 10.6f * density
            textPaint.color = accent
            val durW = textPaint.measureText(appDurStr)
            canvas.drawText(appDurStr, rightColRight, rowY, textPaint)

            textPaint.textAlign = Paint.Align.LEFT
            textPaint.typeface = overlayTypeface(isBold = true)
            textPaint.textSize = 11.0f * density
            textPaint.color = Color.WHITE
            val maxNameW = (rightColRight - durW - rightColLeft - 6f * density).coerceAtLeast(28f * density)
            canvas.drawText(clipTextToWidth(appItem.label, maxNameW), rightColLeft, rowY, textPaint)

            // Progress bar
            val barRatio = if (totalMs > 0L) (ms.toFloat() / totalMs).coerceIn(0.04f, 1f) else 0.04f
            val barTop = rowY + 3.5f * density
            highlightPaint.style = Paint.Style.FILL
            highlightPaint.color = Color.argb(40, 255, 255, 255)
            stScratchRect.set(rightColLeft, barTop, rightColRight, barTop + 4.2f * density)
            canvas.drawRoundRect(stScratchRect, 2f * density, 2f * density, highlightPaint)
            highlightPaint.color = accent
            stScratchRect.set(rightColLeft, barTop, rightColLeft + (barW * barRatio), barTop + 4.2f * density)
            canvas.drawRoundRect(stScratchRect, 2f * density, 2f * density, highlightPaint)

            rowY += itemH
        }
        canvas.restore()
        LightspeedHudVectorIcons.applyScrollEdgeFeathering(
            canvas = canvas,
            left = rightColLeft - 4f * density,
            top = listTop - 1f * density,
            right = rightColRight + 4f * density,
            bottom = listBottom,
            scrollOffset = screenTimeAppsScrollOffset,
            maxScroll = screenTimeAppsMaxScroll,
            featherTopPx = 14f * density,
            featherBottomPx = 16f * density,
            density = density,
            seed = 7171
        )
        canvas.restoreToCount(listLayerCount)

        // Sleek scrollbar indicator when apps exceed visible space
        if (screenTimeAppsMaxScroll > 0f) {
            val sbW = 2.5f * density
            val sbX = rightColRight + 6f * density
            val sbRatio = (listH / totalAppsH).coerceIn(0.15f, 0.8f)
            val thumbH = listH * sbRatio
            val thumbY = listTop + ((screenTimeAppsScrollOffset / screenTimeAppsMaxScroll) * (listH - thumbH))
            highlightPaint.style = Paint.Style.FILL
            highlightPaint.color = Color.argb(60, 255, 255, 255)
            stScratchRect.set(sbX, listTop, sbX + sbW, listBottom)
            canvas.drawRoundRect(stScratchRect, 1.5f * density, 1.5f * density, highlightPaint)
            highlightPaint.color = accent
            stScratchRect.set(sbX, thumbY, sbX + sbW, thumbY + thumbH)
            canvas.drawRoundRect(stScratchRect, 1.5f * density, 1.5f * density, highlightPaint)
        }
    }

    // Bottom Target Countdown Pill (supports Manual + Next Calendar Event, Unified or Per-Widget)
    val now = System.currentTimeMillis()
    val resolvedCountdown = com.sbf.lightspeed.system.LightspeedCalendarCountdownEngine.resolveDisplayCountdownDetails(
        context = context,
        catId = catId,
        slotIndex = slotIndex
    )
    val cdTitle = resolvedCountdown?.title ?: "TARGET"
    val cdTargetMs = resolvedCountdown?.targetMs ?: 0L
    val cdProgress = resolvedCountdown?.progressFraction ?: 0f
    val hasActiveCountdown = resolvedCountdown != null && cdTargetMs > now

    val pillH = 23f * density
    val pillY = bounds.bottom - pillH - 4f * density
    val pillW = bounds.width() - 18f * density
    val pillRect = stCountdownPillRect.apply {
        set(bounds.centerX() - pillW / 2f, pillY, bounds.centerX() + pillW / 2f, pillY + pillH)
    }
    val pillCorner = 4f * density

    // Base pill track
    highlightPaint.style = Paint.Style.FILL
    highlightPaint.color = Color.argb((28 * alpha).toInt(), Color.red(accent), Color.green(accent), Color.blue(accent))
    canvas.drawRoundRect(pillRect, pillCorner, pillCorner, highlightPaint)

    // Subtle theme-matched horizontal progress fill bar behind the event title & remaining time (manual targets only)
    if (hasActiveCountdown && !resolvedCountdown.isCalendarEvent && cdProgress > 0.005f) {
        val fillRight = pillRect.left + (pillRect.width() * cdProgress.coerceIn(0f, 1f))
        stCountdownClipPath.reset()
        stCountdownClipPath.addRoundRect(pillRect, pillCorner, pillCorner, android.graphics.Path.Direction.CW)
        canvas.save()
        canvas.clipPath(stCountdownClipPath)
        highlightPaint.style = Paint.Style.FILL
        highlightPaint.color = Color.argb((74 * alpha).toInt(), Color.red(accent), Color.green(accent), Color.blue(accent))
        canvas.drawRect(pillRect.left, pillRect.top, fillRight, pillRect.bottom, highlightPaint)
        if (cdProgress < 0.995f) {
            highlightPaint.color = Color.argb((140 * alpha).toInt(), Color.red(accent), Color.green(accent), Color.blue(accent))
            canvas.drawRect((fillRight - 1.2f * density).coerceAtLeast(pillRect.left), pillRect.top, fillRight, pillRect.bottom, highlightPaint)
        }
        canvas.restore()
    }

    highlightPaint.style = Paint.Style.STROKE
    highlightPaint.strokeWidth = 0.8f * density
    highlightPaint.color = Color.argb((85 * alpha).toInt(), Color.red(accent), Color.green(accent), Color.blue(accent))
    canvas.drawRoundRect(pillRect, pillCorner, pillCorner, highlightPaint)

    val leftPad = 8f * density
    val rightPad = 8f * density
    val clockR = 4.2f * density
    val clockCx = pillRect.left + leftPad + clockR
    val chevR = 3.6f * density
    val chevCx = pillRect.right - rightPad - chevR * 0.6f

    LightspeedHudVectorIcons.drawVectorClock(
        canvas = canvas,
        cx = clockCx,
        cy = pillRect.centerY(),
        r = clockR,
        color = accent,
        strokeW = 1.3f * density
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

    val textLeft = clockCx + clockR + 5f * density
    val textRight = chevCx - chevR - 5f * density
    if (hasActiveCountdown) {
        val diffMs = cdTargetMs - now
        val days = diffMs / (1000L * 60 * 60 * 24)
        val hrs = (diffMs / (1000L * 60 * 60)) % 24
        val mins = (diffMs / (1000L * 60)) % 60
        val countStr = "${days}d ${hrs}h ${mins}m"

        textPaint.textAlign = Paint.Align.RIGHT
        textPaint.typeface = Typeface.MONOSPACE
        textPaint.textSize = 10.2f * density
        textPaint.color = Color.WHITE
        val cFm = textPaint.fontMetrics
        val countW = textPaint.measureText(countStr)
        canvas.drawText(countStr, textRight, pillRect.centerY() - ((cFm.descent + cFm.ascent) / 2f), textPaint)

        textPaint.textAlign = Paint.Align.LEFT
        textPaint.typeface = overlayTypeface(isBold = true)
        textPaint.textSize = 9.6f * density
        textPaint.color = accent
        val tFm = textPaint.fontMetrics
        val maxTitleW = (textRight - countW - textLeft - 8f * density).coerceAtLeast(36f * density)
        canvas.drawText(
            clipTextToWidth(cdTitle.uppercase(), maxTitleW),
            textLeft,
            pillRect.centerY() - ((tFm.descent + tFm.ascent) / 2f),
            textPaint
        )
    } else {
        textPaint.textAlign = Paint.Align.LEFT
        textPaint.typeface = overlayTypeface(isBold = true)
        textPaint.textSize = 9.6f * density
        textPaint.color = accent
        val tFm = textPaint.fontMetrics
        canvas.drawText(
            clipTextToWidth("SET TARGET COUNTDOWN", (textRight - textLeft).coerceAtLeast(48f * density)),
            textLeft,
            pillRect.centerY() - ((tFm.descent + tFm.ascent) / 2f),
            textPaint
        )
    }
}
