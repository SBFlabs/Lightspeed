package com.sbf.lightspeed

import android.content.Intent
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.net.Uri
import android.os.SystemClock
import android.provider.Settings
import com.sbf.lightspeed.system.LightspeedPreferences
import com.sbf.lightspeed.system.defaultPrefs
import kotlin.math.max

private const val STORAGE_FLASH_DURATION_MS = 1400L

internal fun LightspeedCruiseOverlay.applyStorageFilterAndSort(
    persistPrefs: Boolean = false,
    preserveScroll: Boolean = false
) {
    if (persistPrefs) {
        context.defaultPrefs().edit()
            .putString(LightspeedPreferences.KEY_VITALS_STORAGE_SORT_KEY, vitalsStorageSortKey)
            .putBoolean(LightspeedPreferences.KEY_VITALS_STORAGE_SORT_REVERSED, vitalsStorageSortReversed)
            .putString(LightspeedPreferences.KEY_VITALS_STORAGE_VOLUME_FILTER, vitalsStorageVolumeFilter)
            .putBoolean(LightspeedPreferences.KEY_VITALS_STORAGE_SHOW_SYSTEM, vitalsStorageShowSystemApps)
            .apply()
    }
    vitalsFilteredStorageApps = LightspeedStorageAnalyzer.filterAndSort(
        entries = vitalsAllStorageApps,
        volumeFilter = vitalsStorageVolumeFilter,
        sortKey = vitalsStorageSortKey,
        reversed = vitalsStorageSortReversed
    )
    if (!preserveScroll) {
        vitalsModalScrollOffset = 0f
    }
}

internal fun LightspeedCruiseOverlay.scheduleVitalsStorageScanTickerIfNeeded() {
    if (vitalsStorageTickerActive || !isVitalsModalOpen || vitalsActiveTabKey != "storage") return
    val now = SystemClock.uptimeMillis()
    val hasActiveFlash = now < vitalsStorageSumFlashUntilMs ||
        vitalsStorageNewlyResolvedMs.values.any { now - it < STORAGE_FLASH_DURATION_MS }
    if (!vitalsStorageScanning && !hasActiveFlash) return

    vitalsStorageTickerActive = true
    postDelayed({
        vitalsStorageTickerActive = false
        val innerNow = SystemClock.uptimeMillis()
        vitalsStorageNewlyResolvedMs.entries.removeAll { innerNow - it.value >= STORAGE_FLASH_DURATION_MS }
        if (isVitalsModalOpen && vitalsActiveTabKey == "storage") {
            invalidate()
        }
    }, 110L)
}

internal fun LightspeedCruiseOverlay.scrollStorageFromRailY(touchY: Float) {
    if (vitalsModalMaxScroll <= 0f || vitalsFastRailRect.height() <= 1f) return
    vitalsStorageDragY = touchY.coerceIn(vitalsFastRailRect.top, vitalsFastRailRect.bottom)
    val markers = vitalsStorageJumpMarkers
    val rowH = vitalsStorageRowH
    val targetScroll = if (markers.isNotEmpty() && rowH > 1f) {
        mapRailYToScroll(touchY, vitalsFastRailRect.top, vitalsFastRailRect.bottom, markers, rowH, vitalsModalMaxScroll)
    } else {
        val frac = ((touchY - vitalsFastRailRect.top) / vitalsFastRailRect.height()).coerceIn(0f, 1f)
        frac * vitalsModalMaxScroll
    }
    vitalsModalScrollOffset = targetScroll.coerceIn(0f, vitalsModalMaxScroll)
    invalidate()
}

private fun mapRailYToScroll(
    touchY: Float,
    railTop: Float,
    railBot: Float,
    markers: List<StorageJumpMarker>,
    rowH: Float,
    maxScroll: Float
): Float {
    val y = touchY.coerceIn(railTop, railBot)
    val first = markers.first()
    if (y <= first.yCenter) {
        val span = (first.yCenter - railTop).coerceAtLeast(1f)
        val firstScroll = (first.rowIndex * rowH).coerceIn(0f, maxScroll)
        return ((y - railTop) / span) * firstScroll
    }
    for (i in 0 until markers.lastIndex) {
        val a = markers[i]
        val b = markers[i + 1]
        if (y <= b.yCenter) {
            val span = (b.yCenter - a.yCenter).coerceAtLeast(1f)
            val sA = (a.rowIndex * rowH).coerceIn(0f, maxScroll)
            val sB = (b.rowIndex * rowH).coerceIn(0f, maxScroll)
            return sA + ((y - a.yCenter) / span) * (sB - sA)
        }
    }
    val last = markers.last()
    val lastScroll = (last.rowIndex * rowH).coerceIn(0f, maxScroll)
    val span = (railBot - last.yCenter).coerceAtLeast(1f)
    return lastScroll + ((y - last.yCenter) / span) * (maxScroll - lastScroll)
}

private fun mapScrollToRailY(
    scroll: Float,
    railTop: Float,
    railBot: Float,
    markers: List<StorageJumpMarker>,
    rowH: Float,
    maxScroll: Float
): Float {
    if (markers.isEmpty() || maxScroll <= 0f || rowH <= 1f) {
        val frac = if (maxScroll > 0f) (scroll / maxScroll).coerceIn(0f, 1f) else 0f
        return railTop + frac * (railBot - railTop)
    }
    val s = scroll.coerceIn(0f, maxScroll)
    val first = markers.first()
    val firstScroll = (first.rowIndex * rowH).coerceIn(0f, maxScroll)
    if (s <= firstScroll) {
        val frac = if (firstScroll > 0f) s / firstScroll else 1f
        return railTop + frac * (first.yCenter - railTop)
    }
    for (i in 0 until markers.lastIndex) {
        val a = markers[i]
        val b = markers[i + 1]
        val sA = (a.rowIndex * rowH).coerceIn(0f, maxScroll)
        val sB = (b.rowIndex * rowH).coerceIn(0f, maxScroll)
        if (s <= sB) {
            val frac = if (sB > sA) (s - sA) / (sB - sA) else 0f
            return a.yCenter + frac * (b.yCenter - a.yCenter)
        }
    }
    val last = markers.last()
    val lastScroll = (last.rowIndex * rowH).coerceIn(0f, maxScroll)
    val span = (maxScroll - lastScroll).coerceAtLeast(1f)
    return last.yCenter + ((s - lastScroll) / span) * (railBot - last.yCenter)
}

internal fun LightspeedCruiseOverlay.drawVitalsStorageTabContent(
    canvas: Canvas,
    startTop: Float,
    padX: Float,
    modalW: Float,
    accent: Int,
    dp: Float
) {
    val nowMs = SystemClock.uptimeMillis()
    val ar = Color.red(accent)
    val ag = Color.green(accent)
    val ab = Color.blue(accent)

    vitalsStorageVolumePillRects.clear()
    vitalsStorageSystemPillRect.setEmpty()
    vitalsStorageSortPillRects.clear()
    vitalsStorageAppInfoRects.clear()
    vitalsForceStopRects.clear()
    vitalsStorageJumpMarkers.clear()

    // Row 1: Summary Total + [ALL | INT | SD] + Refresh [↻]
    val volRowTop = startTop + 4f * dp
    val volRowH = 26f * dp
    val totalSumBytes = vitalsFilteredStorageApps.sumOf { it.totalBytes.coerceAtLeast(0L) }
    if (vitalsStorageScanning && vitalsStoragePrevSummarySum in 0L until totalSumBytes) {
        vitalsStorageSumFlashUntilMs = nowMs + 700L
    }
    vitalsStoragePrevSummarySum = totalSumBytes
    val isSumFlashing = nowMs < vitalsStorageSumFlashUntilMs
    val cacheAge = if (vitalsStorageScanning) null else LightspeedStorageAnalyzer.getCacheAgeLabel()
    val baseSummary = "${vitalsFilteredStorageApps.size} APPS · ${LightspeedStorageAnalyzer.formatBytesCompact(totalSumBytes)}"
    val summaryText = if (cacheAge != null) "$baseSummary · $cacheAge" else baseSummary

    val pkgChanges = LightspeedStorageAnalyzer.getPendingPackageChangesSummary()
    val hasChanges = pkgChanges.count > 0
    val isRedUrgent = pkgChanges.count >= 3 || pkgChanges.removedCount > 0 || pkgChanges.addedCount > 0
    val (sr, sg, sb) = when {
        isRedUrgent -> Triple(239, 68, 68)
        hasChanges -> Triple(245, 158, 11)
        else -> Triple(ar, ag, ab)
    }
    val statusAccent = if (hasChanges) Color.rgb(sr, sg, sb) else accent

    val showVolPills = hasExternalStorageVolume()
    val refreshW = if (hasChanges) 42f * dp else 28f * dp
    val sysW = 34f * dp
    val volPillW = 32f * dp
    val volPillGap = 3f * dp
    val volGroupW = if (showVolPills) {
        LightspeedStorageAnalyzer.VOLUME_OPTIONS.size * volPillW +
            (LightspeedStorageAnalyzer.VOLUME_OPTIONS.size - 1) * volPillGap
    } else {
        0f
    }
    val right = vitalsModalRect.right
    val left = vitalsModalRect.left
    val refreshLeft = right - padX - refreshW
    val sysLeft = refreshLeft - 4f * dp - sysW
    val volGroupLeft = if (showVolPills) sysLeft - 4f * dp - volGroupW else sysLeft

    vitalsStorageRefreshPillRect.set(refreshLeft, volRowTop, refreshLeft + refreshW, volRowTop + volRowH)
    highlightPaint.style = Paint.Style.FILL
    highlightPaint.color = Color.argb(if (hasChanges) 72 else 46, sr, sg, sb)
    canvas.drawRoundRect(vitalsStorageRefreshPillRect, 6f * dp, 6f * dp, highlightPaint)
    highlightPaint.style = Paint.Style.STROKE
    highlightPaint.strokeWidth = if (hasChanges) 1.15f * dp else 0.9f * dp
    highlightPaint.color = Color.argb(if (hasChanges) 235 else 165, sr, sg, sb)
    canvas.drawRoundRect(vitalsStorageRefreshPillRect, 6f * dp, 6f * dp, highlightPaint)
    var fm = textPaint.fontMetrics
    if (hasChanges) {
        textPaint.textAlign = Paint.Align.LEFT
        textPaint.typeface = Typeface.DEFAULT_BOLD
        textPaint.textSize = 10.8f * dp
        textPaint.color = statusAccent
        fm = textPaint.fontMetrics
        val cntLabel = "${pkgChanges.count}"
        val cntW = textPaint.measureText(cntLabel)
        val iconR = 4.6f * dp
        val gap = 4.0f * dp
        val startX = vitalsStorageRefreshPillRect.centerX() - (cntW + gap + iconR * 2f) * 0.5f
        canvas.drawText(cntLabel, startX, vitalsStorageRefreshPillRect.centerY() - ((fm.descent + fm.ascent) / 2f), textPaint)
        LightspeedHudVectorIcons.drawVectorRefresh(
            canvas = canvas,
            cx = startX + cntW + gap + iconR,
            cy = vitalsStorageRefreshPillRect.centerY(),
            r = iconR,
            color = statusAccent,
            strokeW = 1.45f * dp
        )
    } else {
        LightspeedHudVectorIcons.drawVectorRefresh(
            canvas = canvas,
            cx = vitalsStorageRefreshPillRect.centerX(),
            cy = vitalsStorageRefreshPillRect.centerY(),
            r = 5.2f * dp,
            color = statusAccent,
            strokeW = 1.55f * dp
        )
    }

    // [SYS] System Apps filter pill (default OFF to keep scans fast and focused on user apps)
    vitalsStorageSystemPillRect.set(sysLeft, volRowTop, sysLeft + sysW, volRowTop + volRowH)
    highlightPaint.style = Paint.Style.FILL
    highlightPaint.color = if (vitalsStorageShowSystemApps) Color.argb(215, ar, ag, ab) else Color.argb(38, 255, 255, 255)
    canvas.drawRoundRect(vitalsStorageSystemPillRect, 6f * dp, 6f * dp, highlightPaint)
    highlightPaint.style = Paint.Style.STROKE
    highlightPaint.strokeWidth = 0.85f * dp
    highlightPaint.color = if (vitalsStorageShowSystemApps) accent else Color.argb(72, 255, 255, 255)
    canvas.drawRoundRect(vitalsStorageSystemPillRect, 6f * dp, 6f * dp, highlightPaint)
    textPaint.textAlign = Paint.Align.CENTER
    textPaint.typeface = Typeface.DEFAULT_BOLD
    textPaint.textSize = 9.2f * dp
    textPaint.color = if (vitalsStorageShowSystemApps) Color.BLACK else Color.WHITE
    fm = textPaint.fontMetrics
    canvas.drawText("SYS", vitalsStorageSystemPillRect.centerX(), vitalsStorageSystemPillRect.centerY() - ((fm.descent + fm.ascent) / 2f), textPaint)

    val maxSummaryW = (volGroupLeft - (left + padX) - 6f * dp).coerceAtLeast(48f * dp)
    textPaint.textAlign = Paint.Align.LEFT
    textPaint.typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
    textPaint.textSize = 10.6f * dp
    textPaint.color = if (isSumFlashing) Color.rgb(74, 222, 128) else Color.rgb(34, 211, 238)
    fm = textPaint.fontMetrics
    val clippedSummary = clipTextToWidth(summaryText, maxSummaryW)
    canvas.drawText(clippedSummary, left + padX, volRowTop + volRowH * 0.5f - ((fm.descent + fm.ascent) / 2f), textPaint)

    if (showVolPills) {
        for ((idx, opt) in LightspeedStorageAnalyzer.VOLUME_OPTIONS.withIndex()) {
            val (vKey, vLabel) = opt
            val isSel = (vitalsStorageVolumeFilter == vKey)
            val pLeft = volGroupLeft + idx * (volPillW + volPillGap)
            val pRect = RectF(pLeft, volRowTop, pLeft + volPillW, volRowTop + volRowH)
            vitalsStorageVolumePillRects[vKey] = pRect

            highlightPaint.style = Paint.Style.FILL
            highlightPaint.color = if (isSel) Color.argb(215, ar, ag, ab) else Color.argb(38, 255, 255, 255)
            canvas.drawRoundRect(pRect, 6f * dp, 6f * dp, highlightPaint)
            highlightPaint.style = Paint.Style.STROKE
            highlightPaint.strokeWidth = 0.85f * dp
            highlightPaint.color = if (isSel) accent else Color.argb(72, 255, 255, 255)
            canvas.drawRoundRect(pRect, 6f * dp, 6f * dp, highlightPaint)

            textPaint.textAlign = Paint.Align.CENTER
            textPaint.typeface = Typeface.DEFAULT_BOLD
            textPaint.textSize = 9.4f * dp
            textPaint.color = if (isSel) Color.BLACK else Color.WHITE
            fm = textPaint.fontMetrics
            canvas.drawText(vLabel, pRect.centerX(), pRect.centerY() - ((fm.descent + fm.ascent) / 2f), textPaint)
        }
    }

    // Row 2: Sort Selector Pills [TOTAL | APP | DATA | CACHE | UPD | INST] + Reverse Toggle [↓ / ↑]
    val sortRowTop = volRowTop + volRowH + 6f * dp
    val sortRowH = 25f * dp
    val revW = 30f * dp
    val sortGap = 3.5f * dp
    val sortAvailW = (modalW - padX * 2f - revW - sortGap).coerceAtLeast(120f * dp)
    val sortPillW = (sortAvailW - sortGap * (LightspeedStorageAnalyzer.SORT_OPTIONS.size - 1)) / LightspeedStorageAnalyzer.SORT_OPTIONS.size

    for ((idx, opt) in LightspeedStorageAnalyzer.SORT_OPTIONS.withIndex()) {
        val (sKey, sLabel) = opt
        val isSel = (vitalsStorageSortKey == sKey)
        val sLeft = left + padX + idx * (sortPillW + sortGap)
        val sRect = RectF(sLeft, sortRowTop, sLeft + sortPillW, sortRowTop + sortRowH)
        vitalsStorageSortPillRects[sKey] = sRect

        highlightPaint.style = Paint.Style.FILL
        highlightPaint.color = if (isSel) Color.argb(215, ar, ag, ab) else Color.argb(36, 255, 255, 255)
        canvas.drawRoundRect(sRect, 5f * dp, 5f * dp, highlightPaint)
        highlightPaint.style = Paint.Style.STROKE
        highlightPaint.strokeWidth = 0.8f * dp
        highlightPaint.color = if (isSel) accent else Color.argb(65, 255, 255, 255)
        canvas.drawRoundRect(sRect, 5f * dp, 5f * dp, highlightPaint)

        textPaint.textAlign = Paint.Align.CENTER
        textPaint.typeface = Typeface.DEFAULT_BOLD
        textPaint.textSize = (sortPillW / 5.4f).coerceIn(8.2f * dp, 9.6f * dp)
        textPaint.color = if (isSel) Color.BLACK else Color.WHITE
        fm = textPaint.fontMetrics
        canvas.drawText(sLabel, sRect.centerX(), sRect.centerY() - ((fm.descent + fm.ascent) / 2f), textPaint)
    }

    val revLeft = right - padX - revW
    vitalsStorageReversePillRect.set(revLeft, sortRowTop, revLeft + revW, sortRowTop + sortRowH)
    highlightPaint.style = Paint.Style.FILL
    highlightPaint.color = Color.argb(58, ar, ag, ab)
    canvas.drawRoundRect(vitalsStorageReversePillRect, 5f * dp, 5f * dp, highlightPaint)
    highlightPaint.style = Paint.Style.STROKE
    highlightPaint.strokeWidth = 0.9f * dp
    highlightPaint.color = accent
    canvas.drawRoundRect(vitalsStorageReversePillRect, 5f * dp, 5f * dp, highlightPaint)
    LightspeedHudVectorIcons.drawVectorArrow(
        canvas = canvas,
        cx = vitalsStorageReversePillRect.centerX(),
        cy = vitalsStorageReversePillRect.centerY(),
        r = 5.0f * dp,
        direction = if (vitalsStorageSortReversed) 1 else 0,
        color = Color.WHITE,
        strokeW = 1.6f * dp
    )

    var curListTop = sortRowTop + sortRowH + 7f * dp

    // Unmistakable Live Scanning Progress Telemetry Strip
    val animStep = ((nowMs / 280L) % 4L).toInt()
    val animDots = when (animStep) {
        0 -> ""
        1 -> "."
        2 -> ".."
        else -> "..."
    }
    if (vitalsStorageScanning && vitalsStorageScanTotal > 0) {
        val scanBannerH = 26f * dp
        val scanRect = RectF(left + padX, curListTop, right - padX, curListTop + scanBannerH)
        highlightPaint.style = Paint.Style.FILL
        highlightPaint.color = Color.argb(40, 34, 211, 238)
        canvas.drawRoundRect(scanRect, 6f * dp, 6f * dp, highlightPaint)
        highlightPaint.style = Paint.Style.STROKE
        highlightPaint.strokeWidth = 0.85f * dp
        highlightPaint.color = Color.argb(145, 34, 211, 238)
        canvas.drawRoundRect(scanRect, 6f * dp, 6f * dp, highlightPaint)

        val dotPulseAlpha = if ((nowMs / 320L) % 2L == 0L) 255 else 115
        highlightPaint.style = Paint.Style.FILL
        highlightPaint.color = Color.argb(dotPulseAlpha, 74, 222, 128)
        canvas.drawCircle(scanRect.left + 11f * dp, scanRect.top + 10f * dp, 3.5f * dp, highlightPaint)

        val scanPct = ((vitalsStorageScanDone * 100f) / vitalsStorageScanTotal.coerceAtLeast(1)).toInt().coerceIn(0, 100)
        textPaint.textAlign = Paint.Align.LEFT
        textPaint.typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
        textPaint.textSize = 9.5f * dp
        textPaint.color = Color.rgb(34, 211, 238)
        canvas.drawText(
            "SCANNING DATA & CACHE · $vitalsStorageScanDone/${vitalsStorageScanTotal} ($scanPct%)$animDots",
            scanRect.left + 19f * dp,
            scanRect.top + 13.5f * dp,
            textPaint
        )

        val barLeft = scanRect.left + 8f * dp
        val barRight = scanRect.right - 8f * dp
        val barTop = scanRect.bottom - 7.5f * dp
        val barBot = scanRect.bottom - 3.5f * dp
        val frac = (vitalsStorageScanDone.toFloat() / vitalsStorageScanTotal.toFloat()).coerceIn(0.03f, 1f)
        highlightPaint.color = Color.argb(48, 255, 255, 255)
        canvas.drawRoundRect(RectF(barLeft, barTop, barRight, barBot), 2f * dp, 2f * dp, highlightPaint)
        highlightPaint.color = Color.rgb(34, 211, 238)
        val fillRight = barLeft + (barRight - barLeft) * frac
        canvas.drawRoundRect(RectF(barLeft, barTop, fillRight, barBot), 2f * dp, 2f * dp, highlightPaint)
        curListTop += scanBannerH + 6f * dp
    }

    // Usage Access Prompt Banner when Usage Stats is denied
    if (!vitalsHasUsageAccessForStorage) {
        val bannerH = 26f * dp
        vitalsStoragePermBannerRect.set(left + padX, curListTop, right - padX, curListTop + bannerH)
        highlightPaint.style = Paint.Style.FILL
        highlightPaint.color = Color.argb(46, 245, 158, 11)
        canvas.drawRoundRect(vitalsStoragePermBannerRect, 6f * dp, 6f * dp, highlightPaint)
        highlightPaint.style = Paint.Style.STROKE
        highlightPaint.strokeWidth = 0.85f * dp
        highlightPaint.color = Color.rgb(245, 158, 11)
        canvas.drawRoundRect(vitalsStoragePermBannerRect, 6f * dp, 6f * dp, highlightPaint)
        textPaint.textAlign = Paint.Align.CENTER
        textPaint.typeface = Typeface.DEFAULT_BOLD
        textPaint.textSize = 9.0f * dp
        textPaint.color = Color.rgb(245, 158, 11)
        fm = textPaint.fontMetrics
        canvas.drawText(
            clipTextToWidth("! TAP FOR USAGE ACCESS (SHOWS DATA & CACHE SIZES)", vitalsStoragePermBannerRect.width() - 10f * dp),
            vitalsStoragePermBannerRect.centerX(),
            vitalsStoragePermBannerRect.centerY() - ((fm.descent + fm.ascent) / 2f),
            textPaint
        )
        curListTop += bannerH + 6f * dp
    } else {
        vitalsStoragePermBannerRect.setEmpty()
    }

    if (!vitalsStorageScanning && hasChanges) {
        val chgH = 23f * dp
        vitalsStorageChangeBannerRect.set(left + padX, curListTop, right - padX, curListTop + chgH)
        highlightPaint.style = Paint.Style.FILL
        highlightPaint.color = Color.argb(44, sr, sg, sb)
        canvas.drawRoundRect(vitalsStorageChangeBannerRect, 6f * dp, 6f * dp, highlightPaint)
        highlightPaint.style = Paint.Style.STROKE
        highlightPaint.strokeWidth = 0.85f * dp
        highlightPaint.color = statusAccent
        canvas.drawRoundRect(vitalsStorageChangeBannerRect, 6f * dp, 6f * dp, highlightPaint)

        val parts = mutableListOf<String>()
        if (pkgChanges.updatedCount > 0) parts.add("${pkgChanges.updatedCount} UPD")
        if (pkgChanges.addedCount > 0) parts.add("${pkgChanges.addedCount} NEW")
        if (pkgChanges.removedCount > 0) parts.add("${pkgChanges.removedCount} REM")
        val detail = parts.joinToString(" · ").ifEmpty { "${pkgChanges.count} CHANGED" }
        val chgMsg = if (pkgChanges.deltaSyncedCount > 0 && pkgChanges.deltaSyncedCount >= pkgChanges.count) {
            "$detail (LIVE DELTA SYNCED) · TAP HERE FOR FULL RESCAN"
        } else {
            "$detail SINCE LAST SCAN · TAP TO REFRESH"
        }
        textPaint.textAlign = Paint.Align.CENTER
        textPaint.typeface = Typeface.DEFAULT_BOLD
        textPaint.textSize = 9.2f * dp
        textPaint.color = statusAccent
        fm = textPaint.fontMetrics
        canvas.drawText(
            clipTextToWidth(chgMsg, vitalsStorageChangeBannerRect.width() - 12f * dp),
            vitalsStorageChangeBannerRect.centerX(),
            vitalsStorageChangeBannerRect.centerY() - ((fm.descent + fm.ascent) / 2f),
            textPaint
        )
        curListTop += chgH + 6f * dp
    } else {
        vitalsStorageChangeBannerRect.setEmpty()
    }

    // List + Size-Tier Quick-Jump Rail
    val railW = 34f * dp
    val railGap = 5f * dp
    val listLeft = left + padX
    val listRight = right - padX - railW - railGap
    val listBottom = vitalsModalRect.bottom - 10f * dp
    val listH = (listBottom - curListTop).coerceAtLeast(48f * dp)

    val rowH = 58f * dp
    vitalsStorageRowH = rowH
    val totalContentH = vitalsFilteredStorageApps.size * rowH
    vitalsModalMaxScroll = max(0f, totalContentH - listH)
    vitalsModalScrollOffset = vitalsModalScrollOffset.coerceIn(0f, vitalsModalMaxScroll)

    val railLeft = listRight + railGap
    vitalsFastRailRect.set(railLeft, curListTop, right - padX, listBottom)
    highlightPaint.style = Paint.Style.FILL
    highlightPaint.color = Color.argb(32, 255, 255, 255)
    canvas.drawRoundRect(vitalsFastRailRect, 7f * dp, 7f * dp, highlightPaint)
    highlightPaint.style = Paint.Style.STROKE
    highlightPaint.strokeWidth = 0.7f * dp
    highlightPaint.color = Color.argb(55, 255, 255, 255)
    canvas.drawRoundRect(vitalsFastRailRect, 7f * dp, 7f * dp, highlightPaint)

    if (vitalsModalMaxScroll > 0f) {
        drawSizeTierJumpRail(canvas, accent, dp)
    }

    canvas.save()
    canvas.clipRect(listLeft, curListTop, listRight, listBottom)
    if (vitalsModalLoadingApps && vitalsAllStorageApps.isEmpty()) {
        drawVitalsLoadingRadar(
            canvas = canvas,
            centerX = (listLeft + listRight) * 0.5f,
            centerY = curListTop + (listBottom - curListTop) * 0.42f,
            accent = accent,
            scanMsg = "SCANNING INSTALLED APP STORAGE...",
            textPaint = textPaint,
            highlightPaint = highlightPaint
        )
    } else if (vitalsFilteredStorageApps.isEmpty()) {
        textPaint.textAlign = Paint.Align.CENTER
        textPaint.typeface = Typeface.DEFAULT_BOLD
        textPaint.textSize = 11.2f * dp
        textPaint.color = Color.rgb(245, 158, 11)
        val emptyTitle = if (vitalsStorageVolumeFilter == "SD") {
            "NO ANDROID APPS ON SD VOLUME"
        } else {
            "NO MATCHING APPS IN THIS VOLUME"
        }
        canvas.drawText(emptyTitle, (listLeft + listRight) * 0.5f, curListTop + 36f * dp, textPaint)
        if (vitalsStorageVolumeFilter == "SD") {
            textPaint.typeface = Typeface.DEFAULT
            textPaint.textSize = 9.8f * dp
            textPaint.color = Color.argb(185, 200, 220, 245)
            canvas.drawText(
                "Tap ALL or INT above for installed apps",
                (listLeft + listRight) * 0.5f,
                curListTop + 56f * dp,
                textPaint
            )
        }
    } else {
        var curY = curListTop - vitalsModalScrollOffset
        for (entry in vitalsFilteredStorageApps) {
            val rRect = RectF(listLeft, curY, listRight, curY + rowH - 5f * dp)
            if (rRect.bottom >= curListTop && rRect.top <= listBottom) {
                val resolvedAt = vitalsStorageNewlyResolvedMs[entry.packageName]
                val flashFraction = if (resolvedAt != null) {
                    (1f - (nowMs - resolvedAt).toFloat() / STORAGE_FLASH_DURATION_MS.toFloat()).coerceIn(0f, 1f)
                } else 0f

                highlightPaint.style = Paint.Style.FILL
                highlightPaint.color = if (flashFraction > 0f) {
                    Color.argb((28 + (38 * flashFraction).toInt()), 34, 211, 238)
                } else {
                    Color.argb(26, 255, 255, 255)
                }
                canvas.drawRoundRect(rRect, 7f * dp, 7f * dp, highlightPaint)
                if (flashFraction > 0f) {
                    highlightPaint.style = Paint.Style.STROKE
                    highlightPaint.strokeWidth = 1.0f * dp
                    highlightPaint.color = Color.argb((180 * flashFraction).toInt(), 74, 222, 128)
                    canvas.drawRoundRect(rRect, 7f * dp, 7f * dp, highlightPaint)
                }

                val iconSize = 28f * dp
                val iconTop = rRect.centerY() - iconSize * 0.5f
                val iconLeft = rRect.left + 8f * dp
                getVitalsRowIcon(entry.packageName)?.let { icon ->
                    icon.setBounds(iconLeft.toInt(), iconTop.toInt(), (iconLeft + iconSize).toInt(), (iconTop + iconSize).toInt())
                    icon.draw(canvas)
                }

                // Right action pill: [INFO]
                val infoW = 46f * dp
                val infoH = 24f * dp
                val infoRect = RectF(
                    rRect.right - infoW - 7f * dp,
                    rRect.centerY() - infoH * 0.5f,
                    rRect.right - 7f * dp,
                    rRect.centerY() + infoH * 0.5f
                )
                vitalsStorageAppInfoRects[entry.packageName] = RectF(infoRect)
                highlightPaint.style = Paint.Style.FILL
                highlightPaint.color = Color.argb(40, ar, ag, ab)
                canvas.drawRoundRect(infoRect, 5.5f * dp, 5.5f * dp, highlightPaint)
                highlightPaint.style = Paint.Style.STROKE
                highlightPaint.strokeWidth = 0.85f * dp
                highlightPaint.color = accent
                canvas.drawRoundRect(infoRect, 5.5f * dp, 5.5f * dp, highlightPaint)
                textPaint.textAlign = Paint.Align.CENTER
                textPaint.typeface = Typeface.DEFAULT_BOLD
                textPaint.textSize = 9.4f * dp
                textPaint.color = accent
                fm = textPaint.fontMetrics
                canvas.drawText("INFO", infoRect.centerX(), infoRect.centerY() - ((fm.descent + fm.ascent) / 2f), textPaint)

                // Line 1 Right: Primary Metric
                val primaryMetric = when (vitalsStorageSortKey) {
                    "app" -> "APP ${LightspeedStorageAnalyzer.formatBytesCompact(entry.appBytes)}"
                    "data" -> if (entry.isStatsResolved) "DATA ${LightspeedStorageAnalyzer.formatBytesCompact(entry.userDataBytes)}" else "DATA$animDots"
                    "cache" -> if (entry.isStatsResolved) "CACH ${LightspeedStorageAnalyzer.formatBytesCompact(entry.cacheBytes)}" else "CACH$animDots"
                    "updated" -> LightspeedStorageAnalyzer.formatDateShort(entry.lastUpdateTime)
                    "installed" -> LightspeedStorageAnalyzer.formatDateShort(entry.firstInstallTime)
                    else -> {
                        val sizeStr = LightspeedStorageAnalyzer.formatBytesCompact(entry.totalBytes)
                        if (entry.isStatsResolved) sizeStr else "~$sizeStr$animDots"
                    }
                }
                textPaint.textAlign = Paint.Align.RIGHT
                textPaint.typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
                textPaint.textSize = 11.8f * dp
                textPaint.color = when {
                    !entry.isStatsResolved -> Color.rgb(245, 180, 60)
                    flashFraction > 0.2f -> Color.rgb(74, 222, 128)
                    else -> accent
                }
                val metricX = infoRect.left - 7f * dp
                val metricW = textPaint.measureText(primaryMetric)
                canvas.drawText(primaryMetric, metricX, rRect.top + 19.5f * dp, textPaint)

                // Line 1 Left: App Name
                val txtLeft = iconLeft + iconSize + 8f * dp
                val maxTitleW = (metricX - metricW - txtLeft - 6f * dp).coerceAtLeast(36f * dp)
                textPaint.textAlign = Paint.Align.LEFT
                textPaint.typeface = Typeface.DEFAULT_BOLD
                textPaint.textSize = 12.6f * dp
                textPaint.color = Color.WHITE
                canvas.drawText(clipTextToWidth(entry.label, maxTitleW), txtLeft, rRect.top + 19.5f * dp, textPaint)

                // Line 2: Storage & Date Breakdown
                val volSuffix = if (entry.isOnExternalStorage) " · SD" else if (showVolPills) " · INT" else ""
                val sysSuffix = if (entry.isSystemApp) " · SYS" else ""
                val detailStr = if (vitalsStorageSortKey == "updated" || vitalsStorageSortKey == "installed") {
                    "UPD ${LightspeedStorageAnalyzer.formatDateShort(entry.lastUpdateTime)} · INST ${LightspeedStorageAnalyzer.formatDateShort(entry.firstInstallTime)} · ${LightspeedStorageAnalyzer.formatBytesCompact(entry.totalBytes)}$volSuffix$sysSuffix"
                } else if (!entry.isStatsResolved) {
                    "APK ${LightspeedStorageAnalyzer.formatBytesCompact(entry.appBytes)} · SIZING DATA/CACHE$animDots$volSuffix$sysSuffix"
                } else {
                    "APK ${LightspeedStorageAnalyzer.formatBytesCompact(entry.appBytes)} · DAT ${LightspeedStorageAnalyzer.formatBytesCompact(entry.userDataBytes)} · CAC ${LightspeedStorageAnalyzer.formatBytesCompact(entry.cacheBytes)}$volSuffix$sysSuffix"
                }
                textPaint.typeface = Typeface.MONOSPACE
                textPaint.textSize = 9.6f * dp
                textPaint.color = if (!entry.isStatsResolved) {
                    Color.rgb(245, 185, 75)
                } else {
                    Color.argb(215, 200, 220, 245)
                }
                val maxDetailW = (infoRect.left - txtLeft - 7f * dp).coerceAtLeast(48f * dp)
                canvas.drawText(clipTextToWidth(detailStr, maxDetailW), txtLeft, rRect.top + 38.5f * dp, textPaint)
            }
            curY += rowH
        }
    }
    canvas.restore()

    // Floating Size-Tier Scrub Callout Bubble while dragging the rail
    if (vitalsModalDraggingRail && vitalsFilteredStorageApps.isNotEmpty()) {
        drawScrubLoupeBubble(canvas, listRight, accent, dp)
    }

    scheduleVitalsStorageScanTickerIfNeeded()
}

private fun LightspeedCruiseOverlay.drawSizeTierJumpRail(
    canvas: Canvas,
    accent: Int,
    dp: Float
) {
    val railTop = vitalsFastRailRect.top + 10f * dp
    val railBot = vitalsFastRailRect.bottom - 10f * dp
    val usableH = (railBot - railTop).coerceAtLeast(24f * dp)
    val rowH = vitalsStorageRowH.coerceAtLeast(1f)
    val maxScroll = vitalsModalMaxScroll.coerceAtLeast(1f)

    val minGap = 15.5f * dp
    val maxFitting = ((usableH / minGap).toInt() + 1).coerceAtLeast(2)
    val rawMarkers = LightspeedStorageAnalyzer.buildJumpMarkers(
        vitalsFilteredStorageApps,
        vitalsStorageSortKey,
        vitalsStorageSortReversed
    )

    val thinned = if (rawMarkers.size > maxFitting) {
        val picked = mutableListOf<StorageJumpMarker>()
        for (i in 0 until maxFitting) {
            val srcIdx = ((i.toFloat() / (maxFitting - 1)) * rawMarkers.lastIndex).toInt().coerceIn(0, rawMarkers.lastIndex)
            val m = rawMarkers[srcIdx]
            if (picked.none { it.rowIndex == m.rowIndex }) picked.add(m)
        }
        picked
    } else {
        rawMarkers
    }

    val laidOut = mutableListOf<StorageJumpMarker>()
    for ((i, m) in thinned.withIndex()) {
        val natY = railTop + ((m.rowIndex * rowH).coerceIn(0f, maxScroll) / maxScroll) * usableH
        val minY = if (laidOut.isEmpty()) railTop else laidOut.last().yCenter + minGap
        val remAfter = (thinned.lastIndex - i)
        val maxY = railBot - remAfter * minGap
        val finalY = natY.coerceAtLeast(minY).coerceAtMost(maxY.coerceAtLeast(minY))
        if (finalY <= railBot + 1f) {
            laidOut.add(m.copy(yCenter = finalY))
        }
    }
    vitalsStorageJumpMarkers.addAll(laidOut)

    val topVisibleIdx = ((vitalsModalScrollOffset + rowH * 0.3f) / rowH).toInt().coerceIn(0, (vitalsFilteredStorageApps.size - 1).coerceAtLeast(0))
    val activeMarkerIdx = laidOut.indexOfLast { it.rowIndex <= topVisibleIdx }.coerceAtLeast(0)

    // Scroll thumb track dot / indicator along rail
    val thumbY = mapScrollToRailY(vitalsModalScrollOffset, vitalsFastRailRect.top + 4f * dp, vitalsFastRailRect.bottom - 4f * dp, laidOut, rowH, maxScroll)
    highlightPaint.style = Paint.Style.FILL
    highlightPaint.color = Color.argb(65, Color.red(accent), Color.green(accent), Color.blue(accent))
    val thumbPill = RectF(vitalsFastRailRect.left + 2f * dp, (thumbY - 7.5f * dp).coerceAtLeast(vitalsFastRailRect.top + 1f * dp), vitalsFastRailRect.right - 2f * dp, (thumbY + 7.5f * dp).coerceAtMost(vitalsFastRailRect.bottom - 1f * dp))
    canvas.drawRoundRect(thumbPill, 4f * dp, 4f * dp, highlightPaint)

    textPaint.textAlign = Paint.Align.CENTER
    val fm = textPaint.fontMetrics
    for ((idx, marker) in laidOut.withIndex()) {
        val isAct = (idx == activeMarkerIdx)
        if (isAct) {
            val pillRect = RectF(vitalsFastRailRect.left + 1.5f * dp, marker.yCenter - 6.5f * dp, vitalsFastRailRect.right - 1.5f * dp, marker.yCenter + 6.5f * dp)
            highlightPaint.style = Paint.Style.FILL
            highlightPaint.color = Color.argb(220, Color.red(accent), Color.green(accent), Color.blue(accent))
            canvas.drawRoundRect(pillRect, 4f * dp, 4f * dp, highlightPaint)
        }
        textPaint.typeface = Typeface.DEFAULT_BOLD
        textPaint.textSize = if (isAct) 8.4f * dp else 7.8f * dp
        textPaint.color = if (isAct) Color.BLACK else Color.argb(205, 215, 232, 250)
        canvas.drawText(marker.label, vitalsFastRailRect.centerX(), marker.yCenter - ((fm.descent + fm.ascent) / 2f), textPaint)
    }
}

private fun LightspeedCruiseOverlay.drawScrubLoupeBubble(
    canvas: Canvas,
    listRight: Float,
    accent: Int,
    dp: Float
) {
    val rowH = vitalsStorageRowH.coerceAtLeast(1f)
    val idx = (vitalsModalScrollOffset / rowH).toInt().coerceIn(0, vitalsFilteredStorageApps.lastIndex)
    val app = vitalsFilteredStorageApps[idx]
    val metricStr = when (vitalsStorageSortKey) {
        "updated" -> LightspeedStorageAnalyzer.formatDateShort(app.lastUpdateTime)
        "installed" -> LightspeedStorageAnalyzer.formatDateShort(app.firstInstallTime)
        else -> LightspeedStorageAnalyzer.formatBytesCompact(LightspeedStorageAnalyzer.metricBytesForSort(app, vitalsStorageSortKey))
    }
    val bubbleW = 148f * dp
    val bubbleH = 38f * dp
    val cy = (if (vitalsStorageDragY >= 0f) vitalsStorageDragY else vitalsFastRailRect.centerY())
        .coerceIn(vitalsFastRailRect.top + bubbleH * 0.5f, vitalsFastRailRect.bottom - bubbleH * 0.5f)
    val bRect = RectF(listRight - bubbleW - 4f * dp, cy - bubbleH * 0.5f, listRight - 4f * dp, cy + bubbleH * 0.5f)

    highlightPaint.style = Paint.Style.FILL
    highlightPaint.color = Color.argb(242, 10, 14, 24)
    canvas.drawRoundRect(bRect, 8f * dp, 8f * dp, highlightPaint)
    highlightPaint.style = Paint.Style.STROKE
    highlightPaint.strokeWidth = 1.2f * dp
    highlightPaint.color = accent
    canvas.drawRoundRect(bRect, 8f * dp, 8f * dp, highlightPaint)

    textPaint.textAlign = Paint.Align.CENTER
    textPaint.typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
    textPaint.textSize = 13.0f * dp
    textPaint.color = accent
    canvas.drawText(metricStr, bRect.centerX(), bRect.top + 16.5f * dp, textPaint)

    textPaint.typeface = Typeface.DEFAULT_BOLD
    textPaint.textSize = 9.8f * dp
    textPaint.color = Color.WHITE
    canvas.drawText(clipTextToWidth(app.label, bubbleW - 14f * dp), bRect.centerX(), bRect.top + 31f * dp, textPaint)
}

internal fun LightspeedCruiseOverlay.clipTextToWidth(text: String, maxWidth: Float): String {
    if (textPaint.measureText(text) <= maxWidth) return text
    var s = text
    while (s.length > 3 && textPaint.measureText("$s..") > maxWidth) {
        s = s.dropLast(1)
    }
    return "$s.."
}

internal fun LightspeedCruiseOverlay.handleVitalsStorageTabTouch(x: Float, y: Float): Boolean {
    if ((!vitalsStorageRefreshPillRect.isEmpty && vitalsStorageRefreshPillRect.contains(x, y)) ||
        (!vitalsStorageChangeBannerRect.isEmpty && vitalsStorageChangeBannerRect.contains(x, y))
    ) {
        triggerHardwareHaptic(18, 90)
        LightspeedStorageAnalyzer.clearCache()
        switchVitalsTaskTab("storage", forceRefresh = true)
        return true
    }
    if (!vitalsStorageSystemPillRect.isEmpty && vitalsStorageSystemPillRect.contains(x, y)) {
        triggerHardwareHaptic(16, 80)
        vitalsStorageShowSystemApps = !vitalsStorageShowSystemApps
        context.defaultPrefs().edit()
            .putBoolean(LightspeedPreferences.KEY_VITALS_STORAGE_SHOW_SYSTEM, vitalsStorageShowSystemApps)
            .apply()
        switchVitalsTaskTab("storage", forceRefresh = false)
        return true
    }
    for ((vKey, rect) in vitalsStorageVolumePillRects) {
        if (rect.contains(x, y)) {
            triggerHardwareHaptic(16, 80)
            vitalsStorageVolumeFilter = vKey
            applyStorageFilterAndSort(persistPrefs = true)
            invalidate()
            return true
        }
    }
    for ((sKey, rect) in vitalsStorageSortPillRects) {
        if (rect.contains(x, y)) {
            triggerHardwareHaptic(16, 80)
            if (vitalsStorageSortKey == sKey) {
                vitalsStorageSortReversed = !vitalsStorageSortReversed
            } else {
                vitalsStorageSortKey = sKey
                vitalsStorageSortReversed = false
            }
            applyStorageFilterAndSort(persistPrefs = true)
            invalidate()
            return true
        }
    }
    if (!vitalsStorageReversePillRect.isEmpty && vitalsStorageReversePillRect.contains(x, y)) {
        triggerHardwareHaptic(16, 80)
        vitalsStorageSortReversed = !vitalsStorageSortReversed
        applyStorageFilterAndSort(persistPrefs = true)
        invalidate()
        return true
    }
    if (!vitalsStoragePermBannerRect.isEmpty && vitalsStoragePermBannerRect.contains(x, y)) {
        triggerHardwareHaptic(20, 100)
        closeVitalsTaskManagerModal()
        dismissOverlay()
        try {
            val intent = Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        } catch (_: Exception) {}
        return true
    }
    for ((pkg, rect) in vitalsStorageAppInfoRects) {
        if (rect.contains(x, y)) {
            triggerHardwareHaptic(20, 100)
            LightspeedStorageAnalyzer.markPackageInspected(pkg)
            closeVitalsTaskManagerModal()
            dismissOverlay()
            try {
                val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                    data = Uri.parse("package:$pkg")
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(intent)
            } catch (_: Exception) {}
            return true
        }
    }
    return false
}
