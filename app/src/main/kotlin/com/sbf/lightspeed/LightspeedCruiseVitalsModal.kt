package com.sbf.lightspeed

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.drawable.Drawable
import android.os.SystemClock
import android.view.MotionEvent
import com.sbf.lightspeed.system.LightspeedPreferences
import com.sbf.lightspeed.system.defaultPrefs
import kotlin.concurrent.thread
import kotlin.math.hypot
import kotlin.math.max

private val VITALS_SPAN_OPTIONS = listOf(
    "1H" to 1L * 3_600_000L,
    "2H" to 2L * 3_600_000L,
    "3H" to 3L * 3_600_000L,
    "24H" to 24L * 3_600_000L,
    "7D" to 7L * 24L * 3_600_000L,
    "30D" to 30L * 24L * 3_600_000L
)

private val VITALS_TASK_TABS = listOf(
    "cpu" to "CPU & HEAT",
    "wakelocks" to "WAKELOCK",
    "ram" to "RAM & SWAP",
    "storage" to "STORAGE"
)

internal var vitalsActiveTabKey: String = "cpu"
private val vitalsTabPillRects = mutableMapOf<String, RectF>()

internal fun LightspeedCruiseOverlay.hasExternalStorageVolume(): Boolean {
    return vitalsStorageHasExternalApps || LightspeedCruiseHudPods.latestBatterySnapshot.externalStorage != null
}

internal fun LightspeedCruiseOverlay.openVitalsTaskManagerModal(
    initialTab: String = "cpu",
    storageVolumeFilter: String? = null
) {
    val prefs = context.defaultPrefs()
    vitalsStorageSortKey = prefs.getString(LightspeedPreferences.KEY_VITALS_STORAGE_SORT_KEY, "total") ?: "total"
    vitalsStorageSortReversed = prefs.getBoolean(LightspeedPreferences.KEY_VITALS_STORAGE_SORT_REVERSED, false)
    vitalsStorageShowSystemApps = prefs.getBoolean(LightspeedPreferences.KEY_VITALS_STORAGE_SHOW_SYSTEM, false)
    val requestedVolFilter = storageVolumeFilter
        ?: prefs.getString(LightspeedPreferences.KEY_VITALS_STORAGE_VOLUME_FILTER, "ALL")
        ?: "ALL"
    vitalsStorageVolumeFilter = if (hasExternalStorageVolume()) requestedVolFilter else "ALL"

    val snap = LightspeedCruiseHudPods.latestBatterySnapshot
    LightspeedVitalsHistoryStore.recordSampleIfDue(
        context = context,
        tempC = snap.tempC,
        ramUsedMb = (snap.usedRamGb * 1024.0).toInt(),
        swapUsedMb = (snap.swapUsedBytes / (1024L * 1024L)).toInt(),
        force = true
    )
    isVitalsModalOpen = true
    switchVitalsTaskTab(initialTab)
}

internal fun LightspeedCruiseOverlay.switchVitalsTaskTab(tabKey: String, forceRefresh: Boolean = false) {
    vitalsActiveTabKey = tabKey
    vitalsModalScrollOffset = 0f
    vitalsModalDraggingRail = false
    val scanToken = vitalsStorageScanToken.incrementAndGet()
    vitalsStorageScanning = false
    vitalsStorageNewlyResolvedMs.clear()
    vitalsStoragePrevSummarySum = -1L
    vitalsStorageDragY = -1f
    if (tabKey == "storage") {
        val hasUsageNow = com.sbf.lightspeed.system.LightspeedUsageStats.hasUsageAccess(context)
        val cached = if (forceRefresh) null else LightspeedStorageAnalyzer.getCachedIfFresh(hasUsageNow, vitalsStorageShowSystemApps)
        if (cached != null) {
            val deltaSynced = LightspeedStorageAnalyzer.consumeDeltaSyncedPackages()
            if (deltaSynced.isNotEmpty()) {
                val flashNow = SystemClock.uptimeMillis()
                for (p in deltaSynced) {
                    vitalsStorageNewlyResolvedMs[p] = flashNow
                }
            }
            vitalsAllStorageApps = cached.entries
            vitalsHasUsageAccessForStorage = cached.hasUsageAccess
            vitalsStorageHasExternalApps = cached.hasExternalApps
            if (!hasExternalStorageVolume() && vitalsStorageVolumeFilter != "ALL") {
                vitalsStorageVolumeFilter = "ALL"
            }
            vitalsStorageScanDone = cached.entries.size
            vitalsStorageScanTotal = cached.entries.size
            vitalsModalLoadingApps = false
            applyStorageFilterAndSort(persistPrefs = false)
            invalidate()
            LightspeedStorageAnalyzer.consumeLastInspectedPackage()?.let { inspectedPkg ->
                LightspeedStorageAnalyzer.refreshPackageInCacheAsync(context, inspectedPkg) { updated ->
                    post {
                        if (vitalsStorageScanToken.get() == scanToken && isVitalsModalOpen && vitalsActiveTabKey == "storage") {
                            vitalsAllStorageApps = updated.entries
                            vitalsHasUsageAccessForStorage = updated.hasUsageAccess
                            vitalsStorageHasExternalApps = updated.hasExternalApps
                            if (!hasExternalStorageVolume() && vitalsStorageVolumeFilter != "ALL") {
                                vitalsStorageVolumeFilter = "ALL"
                            }
                            vitalsStorageScanDone = updated.entries.size
                            vitalsStorageScanTotal = updated.entries.size
                            vitalsStorageNewlyResolvedMs[inspectedPkg] = SystemClock.uptimeMillis()
                            applyStorageFilterAndSort(
                                persistPrefs = false,
                                preserveScroll = vitalsModalScrollOffset > 0f
                            )
                            invalidate()
                        }
                    }
                }
            }
            return
        }
        vitalsAllStorageApps = emptyList()
        vitalsFilteredStorageApps = emptyList()
        vitalsStorageScanDone = 0
        vitalsStorageScanTotal = 0
        vitalsModalLoadingApps = true
        vitalsStorageScanning = true
        invalidate()
        val knownLabels = try {
            dataBridge.getAllApps().associate { it.packageName to it.label }
        } catch (_: Exception) {
            emptyMap()
        }
        val includeSystem = vitalsStorageShowSystemApps
        thread(name = "VitalsStorageQuery") {
            try {
                LightspeedStorageAnalyzer.queryInstalledAppStorageProgressive(
                    context = context,
                    knownLabels = knownLabels,
                    includeSystemApps = includeSystem,
                    isCancelled = {
                        vitalsStorageScanToken.get() != scanToken || !isVitalsModalOpen || vitalsActiveTabKey != "storage"
                    },
                    onBatchUpdate = { result, doneCount, totalCount, isFinished ->
                        val batchNow = SystemClock.uptimeMillis()
                        post {
                            if (vitalsStorageScanToken.get() == scanToken && vitalsActiveTabKey == "storage" && isVitalsModalOpen) {
                                for (pkg in result.resolvedPackages) {
                                    vitalsStorageNewlyResolvedMs[pkg] = batchNow
                                }
                                vitalsAllStorageApps = result.entries
                                vitalsHasUsageAccessForStorage = result.hasUsageAccess
                                vitalsStorageHasExternalApps = result.hasExternalApps
                                if (!hasExternalStorageVolume() && vitalsStorageVolumeFilter != "ALL") {
                                    vitalsStorageVolumeFilter = "ALL"
                                }
                                vitalsStorageScanDone = doneCount
                                vitalsStorageScanTotal = totalCount
                                vitalsStorageScanning = !isFinished
                                vitalsModalLoadingApps = false
                                applyStorageFilterAndSort(
                                    persistPrefs = false,
                                    preserveScroll = vitalsModalScrollOffset > 0f
                                )
                                invalidate()
                            }
                        }
                    }
                )
            } catch (e: Throwable) {
                android.util.Log.w("VitalsModal", "Storage query failed safely", e)
                post {
                    if (vitalsStorageScanToken.get() == scanToken && vitalsActiveTabKey == "storage") {
                        vitalsStorageScanning = false
                        vitalsModalLoadingApps = false
                        invalidate()
                    }
                }
            }
        }
    } else {
        vitalsModalLoadingApps = true
        vitalsCachedMemApps = emptyList()
        invalidate()
        thread(name = "VitalsTaskQuery") {
            val entries = try {
                when (tabKey) {
                    "cpu" -> LightspeedVitalsHistoryStore.queryRogueCpuApps(context)
                    "wakelocks" -> LightspeedVitalsHistoryStore.queryWakelockApps(context)
                    else -> LightspeedVitalsHistoryStore.queryRunningAppMemory(context)
                }
            } catch (e: Throwable) {
                android.util.Log.w("VitalsModal", "Task query failed safely", e)
                emptyList()
            }
            post {
                if (vitalsActiveTabKey == tabKey) {
                    vitalsCachedMemApps = entries
                    vitalsModalLoadingApps = false
                    invalidate()
                }
            }
        }
    }
}

internal fun LightspeedCruiseOverlay.closeVitalsTaskManagerModal() {
    vitalsStorageScanToken.incrementAndGet()
    vitalsStorageScanning = false
    vitalsStorageNewlyResolvedMs.clear()
    vitalsStoragePrevSummarySum = -1L
    vitalsStorageDragY = -1f
    vitalsRowIconLruCache.evictAll()
    isVitalsModalOpen = false
    vitalsModalDraggingRail = false
    invalidate()
}

internal fun LightspeedCruiseOverlay.getVitalsRowIcon(packageName: String): Drawable? {
    applicationIconCache[packageName]?.let { return it }
    vitalsRowIconLruCache.get(packageName)?.let { return it }
    return try {
        context.packageManager.getApplicationIcon(packageName).also {
            vitalsRowIconLruCache.put(packageName, it)
        }
    } catch (_: Exception) {
        null
    }
}

internal fun LightspeedCruiseOverlay.drawVitalsTaskManagerModal(
    canvas: Canvas,
    density: Float,
    accent: Int
) {
    val dp = resources.displayMetrics.density
    val screenW = width.toFloat()
    val screenH = height.toFloat()

    // Scrim
    highlightPaint.style = Paint.Style.FILL
    highlightPaint.color = Color.argb(180, 0, 0, 0)
    canvas.drawRect(0f, 0f, screenW, screenH, highlightPaint)

    val modalW = (screenW - 16f * dp).coerceAtMost(460f * dp)
    val modalH = (screenH * 0.86f).coerceAtMost(screenH - 34f * dp)
    val left = (screenW - modalW) * 0.5f
    val top = (screenH - modalH) * 0.5f
    vitalsModalRect.set(left, top, left + modalW, top + modalH)

    highlightPaint.style = Paint.Style.FILL
    highlightPaint.color = Color.argb(248, 11, 15, 24)
    canvas.drawRoundRect(vitalsModalRect, 14f * dp, 14f * dp, highlightPaint)
    highlightPaint.style = Paint.Style.STROKE
    highlightPaint.strokeWidth = 1.4f * dp
    highlightPaint.color = Color.argb(185, Color.red(accent), Color.green(accent), Color.blue(accent))
    canvas.drawRoundRect(vitalsModalRect, 14f * dp, 14f * dp, highlightPaint)

    val padX = 12f * dp
    val fm = textPaint.fontMetrics

    // Header + °C/°F pill + Close button
    textPaint.textAlign = Paint.Align.LEFT
    textPaint.typeface = Typeface.DEFAULT_BOLD
    textPaint.textSize = 12.6f * dp
    textPaint.color = Color.WHITE
    canvas.drawText("CULPRIT HUNTER", left + padX, top + 24f * dp, textPaint)

    val closeSize = 26f * dp
    vitalsCloseRect.set(
        vitalsModalRect.right - closeSize - 10f * dp,
        top + 8f * dp,
        vitalsModalRect.right - 10f * dp,
        top + 8f * dp + closeSize
    )
    highlightPaint.style = Paint.Style.FILL
    highlightPaint.color = Color.argb(120, 40, 50, 70)
    canvas.drawRoundRect(vitalsCloseRect, 6f * dp, 6f * dp, highlightPaint)
    LightspeedHudVectorIcons.drawVectorClose(
        canvas = canvas,
        cx = vitalsCloseRect.centerX(),
        cy = vitalsCloseRect.centerY(),
        r = 4.6f * dp,
        color = Color.WHITE,
        strokeW = 1.6f * dp
    )

    val unitLabel = if (LightspeedCruiseHudPods.isTempFahrenheit(context)) "°F" else "°C"
    vitalsTempUnitToggleRect.set(vitalsCloseRect.left - 34f * dp, top + 8f * dp, vitalsCloseRect.left - 6f * dp, top + 8f * dp + closeSize)
    highlightPaint.style = Paint.Style.FILL
    highlightPaint.color = Color.argb(55, Color.red(accent), Color.green(accent), Color.blue(accent))
    canvas.drawRoundRect(vitalsTempUnitToggleRect, 6f * dp, 6f * dp, highlightPaint)
    textPaint.textSize = 10.6f * dp
    textPaint.textAlign = Paint.Align.CENTER
    textPaint.color = accent
    canvas.drawText(unitLabel, vitalsTempUnitToggleRect.centerX(), vitalsTempUnitToggleRect.centerY() - ((fm.descent + fm.ascent) / 2f), textPaint)
    textPaint.textAlign = Paint.Align.LEFT

    // 4-Mode Switcher Tabs right beneath Header: [ CPU & HEAT ] [ WAKELOCK ] [ RAM & SWAP ] [ STORAGE ]
    vitalsTabPillRects.clear()
    val tabTop = top + 39f * dp
    val tabH = 26f * dp
    val tabGap = 4f * dp
    val tabW = (modalW - padX * 2f - tabGap * (VITALS_TASK_TABS.size - 1)) / VITALS_TASK_TABS.size
    for ((idx, tab) in VITALS_TASK_TABS.withIndex()) {
        val (tKey, tLabel) = tab
        val isSel = (vitalsActiveTabKey == tKey)
        val tLeft = left + padX + idx * (tabW + tabGap)
        val tRect = RectF(tLeft, tabTop, tLeft + tabW, tabTop + tabH)
        vitalsTabPillRects[tKey] = tRect

        highlightPaint.style = Paint.Style.FILL
        highlightPaint.color = if (isSel) {
            Color.argb(205, Color.red(accent), Color.green(accent), Color.blue(accent))
        } else {
            Color.argb(38, 255, 255, 255)
        }
        canvas.drawRoundRect(tRect, 6f * dp, 6f * dp, highlightPaint)
        highlightPaint.style = Paint.Style.STROKE
        highlightPaint.strokeWidth = 0.85f * dp
        highlightPaint.color = if (isSel) accent else Color.argb(65, 255, 255, 255)
        canvas.drawRoundRect(tRect, 6f * dp, 6f * dp, highlightPaint)

        textPaint.textAlign = Paint.Align.CENTER
        textPaint.typeface = Typeface.DEFAULT_BOLD
        textPaint.textSize = (tabW / 10.2f).coerceIn(8.6f * dp, 9.8f * dp)
        textPaint.color = if (isSel) Color.BLACK else Color.WHITE
        val tFm = textPaint.fontMetrics
        canvas.drawText(tLabel, tRect.centerX(), tRect.centerY() - ((tFm.descent + tFm.ascent) / 2f), textPaint)
    }

    if (vitalsActiveTabKey == "storage") {
        vitalsSpanPillRects.clear()
        drawVitalsStorageTabContent(
            canvas = canvas,
            startTop = tabTop + tabH + 4f * dp,
            padX = padX,
            modalW = modalW,
            accent = accent,
            dp = dp
        )
        return
    }

    vitalsStorageVolumePillRects.clear()
    vitalsStorageSortPillRects.clear()
    vitalsStorageAppInfoRects.clear()
    vitalsStorageReversePillRect.setEmpty()
    vitalsStorageRefreshPillRect.setEmpty()
    vitalsStoragePermBannerRect.setEmpty()
    vitalsStorageChangeBannerRect.setEmpty()

    // Rolling Time-Span Pills: 1H, 2H, 3H, 24H, 7D, 30D (with ! indicator if span > recorded history)
    val availableSpanMs = LightspeedVitalsHistoryStore.getAvailableHistorySpanMs(context)
    vitalsSpanPillRects.clear()
    val spanTop = tabTop + tabH + 7f * dp
    val spanH = 24f * dp
    val spanGap = 4f * dp
    val spanW = (modalW - padX * 2f - spanGap * (VITALS_SPAN_OPTIONS.size - 1)) / VITALS_SPAN_OPTIONS.size
    for ((idx, opt) in VITALS_SPAN_OPTIONS.withIndex()) {
        val (key, optWindowMs) = opt
        val isPartial = optWindowMs > (availableSpanMs + 15L * 60_000L)
        val isSel = (vitalsModalSpanKey == key)
        val pLeft = left + padX + idx * (spanW + spanGap)
        val pRect = RectF(pLeft, spanTop, pLeft + spanW, spanTop + spanH)
        vitalsSpanPillRects[key] = pRect

        highlightPaint.style = Paint.Style.FILL
        highlightPaint.color = if (isSel) Color.argb(195, Color.red(accent), Color.green(accent), Color.blue(accent)) else Color.argb(38, 255, 255, 255)
        canvas.drawRoundRect(pRect, 5f * dp, 5f * dp, highlightPaint)
        highlightPaint.style = Paint.Style.STROKE
        highlightPaint.strokeWidth = 0.8f * dp
        highlightPaint.color = if (isSel) accent else if (isPartial) Color.argb(130, 245, 158, 11) else Color.argb(65, 255, 255, 255)
        canvas.drawRoundRect(pRect, 5f * dp, 5f * dp, highlightPaint)

        val pillText = if (isPartial) "$key !" else key
        textPaint.textAlign = Paint.Align.CENTER
        textPaint.typeface = Typeface.DEFAULT_BOLD
        textPaint.textSize = 9.4f * dp
        textPaint.color = if (isSel) Color.BLACK else if (isPartial) Color.rgb(245, 158, 11) else Color.WHITE
        val pFm = textPaint.fontMetrics
        canvas.drawText(pillText, pRect.centerX(), pRect.centerY() - ((pFm.descent + pFm.ascent) / 2f), textPaint)
    }

    // Telemetry History Card (Temp + RAM/Swap Sparkline + Correlated Active Apps)
    val windowMs = VITALS_SPAN_OPTIONS.firstOrNull { it.first == vitalsModalSpanKey }?.second ?: (3L * 3_600_000L)
    val isSelectedSpanPartial = windowMs > (availableSpanMs + 15L * 60_000L)
    val history = LightspeedVitalsHistoryStore.getSamplesInWindow(context, windowMs)

    val chartTop = spanTop + spanH + 8f * dp
    val chartH = 78f * dp
    val chartRect = RectF(left + padX, chartTop, vitalsModalRect.right - padX, chartTop + chartH)
    highlightPaint.style = Paint.Style.FILL
    highlightPaint.color = Color.argb(26, 255, 255, 255)
    canvas.drawRoundRect(chartRect, 8f * dp, 8f * dp, highlightPaint)

    val peakTempC = history.maxOfOrNull { it.tempC } ?: LightspeedCruiseHudPods.latestBatterySnapshot.tempC
    val avgRamMb = if (history.isNotEmpty()) history.map { it.ramUsedMb }.average().toInt() else (LightspeedCruiseHudPods.latestBatterySnapshot.usedRamGb * 1024).toInt()
    val topFgApps = history.map { it.foregroundPkg }.filter { it.isNotBlank() }
        .groupingBy { it }.eachCount().entries.sortedByDescending { it.value }.take(3)
        .joinToString(", ") { it.key.substringAfterLast('.').uppercase() }
        .ifBlank { "CURRENT SESSION" }

    textPaint.textAlign = Paint.Align.LEFT
    textPaint.typeface = Typeface.DEFAULT_BOLD
    textPaint.textSize = 9.0f * dp
    textPaint.color = Color.rgb(245, 158, 11)
    canvas.drawText("PEAK TEMP: ${LightspeedCruiseHudPods.formatTemp(context, peakTempC)}", chartRect.left + 8f * dp, chartRect.top + 13.5f * dp, textPaint)

    textPaint.textAlign = Paint.Align.RIGHT
    if (isSelectedSpanPartial) {
        val availStr = LightspeedCruiseHudPods.formatDurationCompact(availableSpanMs.coerceAtLeast(60_000L))
        textPaint.color = Color.rgb(245, 158, 11)
        canvas.drawText("! $availStr OF $vitalsModalSpanKey · ${avgRamMb}MB", chartRect.right - 8f * dp, chartRect.top + 13.5f * dp, textPaint)
    } else {
        textPaint.color = Color.rgb(34, 211, 238)
        canvas.drawText("AVG RAM: ${avgRamMb}MB · ${history.size} PTS", chartRect.right - 8f * dp, chartRect.top + 13.5f * dp, textPaint)
    }

    // Sparklines
    val gLeft = chartRect.left + 8f * dp
    val gRight = chartRect.right - 8f * dp
    val gTop = chartRect.top + 18f * dp
    val gBot = chartRect.bottom - 16f * dp
    if (history.size >= 2) {
        val minT = (history.minOf { it.tempC } - 2).toFloat()
        val maxT = (history.maxOf { it.tempC } + 2).toFloat().coerceAtLeast(minT + 4f)
        val totalRamMb = (LightspeedCruiseHudPods.latestBatterySnapshot.totalRamGb * 1024.0).toFloat().coerceAtLeast(1024f)

        val tempPath = Path()
        val ramPath = Path()
        for (i in history.indices) {
            val s = history[i]
            val px = gLeft + (i.toFloat() / (history.size - 1)) * (gRight - gLeft)
            val ty = gBot - (((s.tempC - minT) / (maxT - minT)).coerceIn(0f, 1f)) * (gBot - gTop)
            val ry = gBot - ((s.ramUsedMb / totalRamMb).coerceIn(0f, 1f)) * (gBot - gTop)
            if (i == 0) {
                tempPath.moveTo(px, ty)
                ramPath.moveTo(px, ry)
            } else {
                tempPath.lineTo(px, ty)
                ramPath.lineTo(px, ry)
            }
        }
        highlightPaint.style = Paint.Style.STROKE
        highlightPaint.strokeWidth = 1.5f * dp
        highlightPaint.color = Color.rgb(245, 158, 11)
        canvas.drawPath(tempPath, highlightPaint)
        highlightPaint.color = Color.rgb(34, 211, 238)
        canvas.drawPath(ramPath, highlightPaint)
    } else {
        textPaint.textAlign = Paint.Align.CENTER
        textPaint.typeface = Typeface.DEFAULT
        textPaint.textSize = 9.8f * dp
        textPaint.color = Color.argb(165, 190, 205, 225)
        canvas.drawText("COLLECTING ROLLING SAMPLES...", chartRect.centerX(), (gTop + gBot) * 0.5f + 3f * dp, textPaint)
    }

    textPaint.textAlign = Paint.Align.LEFT
    textPaint.typeface = Typeface.MONOSPACE
    textPaint.textSize = 8.6f * dp
    textPaint.color = Color.argb(215, 200, 220, 245)
    canvas.drawText(clipTextToWidth("ACTIVE: $topFgApps", chartRect.width() - 16f * dp), chartRect.left + 8f * dp, chartRect.bottom - 5f * dp, textPaint)

    val railW = 14f * dp
    val listLeft = left + padX
    val listRight = vitalsModalRect.right - padX - railW - 4f * dp
    val listTop = chartRect.bottom + 7f * dp
    val listBottom = vitalsModalRect.bottom - 12f * dp
    val listH = (listBottom - listTop).coerceAtLeast(40f * dp)

    val rowH = 48f * dp
    val totalContentH = vitalsCachedMemApps.size * rowH
    vitalsModalMaxScroll = max(0f, totalContentH - listH)
    vitalsModalScrollOffset = vitalsModalScrollOffset.coerceIn(0f, vitalsModalMaxScroll)

    // Fast-Scroller Rail
    vitalsFastRailRect.set(listRight + 4f * dp, listTop, vitalsModalRect.right - padX, listBottom)
    highlightPaint.style = Paint.Style.FILL
    highlightPaint.color = Color.argb(35, 255, 255, 255)
    canvas.drawRoundRect(vitalsFastRailRect, 4f * dp, 4f * dp, highlightPaint)
    if (vitalsModalMaxScroll > 0f) {
        val thumbH = (listH * (listH / totalContentH)).coerceIn(22f * dp, listH * 0.6f)
        val thumbFrac = (vitalsModalScrollOffset / vitalsModalMaxScroll).coerceIn(0f, 1f)
        val thumbTop = listTop + thumbFrac * (listH - thumbH)
        val thumbRect = RectF(vitalsFastRailRect.left + 1.5f * dp, thumbTop, vitalsFastRailRect.right - 1.5f * dp, thumbTop + thumbH)
        highlightPaint.color = accent
        canvas.drawRoundRect(thumbRect, 3f * dp, 3f * dp, highlightPaint)
    }

    vitalsForceStopRects.clear()
    canvas.save()
    canvas.clipRect(listLeft, listTop, listRight, listBottom)
    if (vitalsModalLoadingApps && vitalsCachedMemApps.isEmpty()) {
        val scanMsg = when (vitalsActiveTabKey) {
            "cpu" -> "HUNTING ROGUE BACKGROUND CPU & HEAT..."
            "wakelocks" -> "SCANNING ACTIVE PARTIAL WAKELOCKS..."
            else -> "SCANNING PROCESS MEMORY..."
        }
        drawVitalsLoadingRadar(
            canvas = canvas,
            centerX = (listLeft + listRight) * 0.5f,
            centerY = listTop + (listBottom - listTop) * 0.42f,
            accent = accent,
            scanMsg = scanMsg,
            textPaint = textPaint,
            highlightPaint = highlightPaint
        )
    } else {
        var curY = listTop - vitalsModalScrollOffset
        for (entry in vitalsCachedMemApps) {
            val rRect = RectF(listLeft, curY, listRight, curY + rowH - 5f * dp)
            if (rRect.bottom >= listTop && rRect.top <= listBottom) {
                highlightPaint.style = Paint.Style.FILL
                highlightPaint.color = if (entry.isHighRisk) {
                    Color.argb(46, 245, 158, 11)
                } else {
                    Color.argb(24, 255, 255, 255)
                }
                canvas.drawRoundRect(rRect, 7f * dp, 7f * dp, highlightPaint)
                if (entry.isHighRisk) {
                    highlightPaint.style = Paint.Style.STROKE
                    highlightPaint.strokeWidth = 0.85f * dp
                    highlightPaint.color = Color.argb(135, 245, 158, 11)
                    canvas.drawRoundRect(rRect, 7f * dp, 7f * dp, highlightPaint)
                }

                val iconSize = 26f * dp
                val iconTop = rRect.centerY() - iconSize * 0.5f
                val iconLeft = rRect.left + 8f * dp
                getVitalsRowIcon(entry.packageName)?.let { icon ->
                    icon.setBounds(iconLeft.toInt(), iconTop.toInt(), (iconLeft + iconSize).toInt(), (iconTop + iconSize).toInt())
                    icon.draw(canvas)
                }

                // 1-Tap Force Stop Pill
                val stopW = 50f * dp
                val stopH = 22f * dp
                val stopRect = RectF(rRect.right - stopW - 8f * dp, rRect.centerY() - stopH * 0.5f, rRect.right - 8f * dp, rRect.centerY() + stopH * 0.5f)
                vitalsForceStopRects[entry.packageName] = RectF(stopRect)
                val stopColor = Color.rgb(239, 68, 68)
                highlightPaint.style = Paint.Style.FILL
                highlightPaint.color = Color.argb(45, 239, 68, 68)
                canvas.drawRoundRect(stopRect, 5.5f * dp, 5.5f * dp, highlightPaint)
                highlightPaint.style = Paint.Style.STROKE
                highlightPaint.strokeWidth = 0.85f * dp
                highlightPaint.color = stopColor
                canvas.drawRoundRect(stopRect, 5.5f * dp, 5.5f * dp, highlightPaint)
                textPaint.textAlign = Paint.Align.CENTER
                textPaint.typeface = Typeface.DEFAULT_BOLD
                textPaint.textSize = 9.2f * dp
                textPaint.color = stopColor
                val sFm = textPaint.fontMetrics
                canvas.drawText("STOP", stopRect.centerX(), stopRect.centerY() - ((sFm.descent + sFm.ascent) / 2f), textPaint)

                val txtLeft = iconLeft + iconSize + 8f * dp
                val maxTxtW = (stopRect.left - txtLeft - 6f * dp).coerceAtLeast(40f * dp)
                textPaint.textAlign = Paint.Align.LEFT
                textPaint.typeface = Typeface.DEFAULT_BOLD
                textPaint.textSize = 11.8f * dp
                textPaint.color = Color.WHITE
                canvas.drawText(clipTextToWidth(entry.label, maxTxtW), txtLeft, rRect.top + 17.5f * dp, textPaint)

                textPaint.typeface = Typeface.MONOSPACE
                textPaint.textSize = 9.4f * dp
                textPaint.color = if (entry.isHighRisk) Color.rgb(245, 158, 11) else Color.rgb(34, 211, 238)
                val subStr = entry.subtitleOverride ?: if (entry.swapMb > 0.1f) {
                    String.format(java.util.Locale.US, "RAM: %.0f MB · SWAP: %.0f MB", entry.ramMb, entry.swapMb)
                } else {
                    String.format(java.util.Locale.US, "RAM: %.0f MB", entry.ramMb)
                }
                canvas.drawText(clipTextToWidth(subStr, maxTxtW), txtLeft, rRect.top + 34.5f * dp, textPaint)
            }
            curY += rowH
        }
    }
    canvas.restore()
}

internal fun LightspeedCruiseOverlay.handleVitalsTaskManagerModalTouch(event: MotionEvent): Boolean {
    val x = event.x
    val y = event.y

    fun scrollFromRailY(touchY: Float) {
        if (vitalsModalMaxScroll <= 0f || vitalsFastRailRect.height() <= 1f) return
        if (vitalsActiveTabKey == "storage") {
            scrollStorageFromRailY(touchY)
            return
        }
        val frac = ((touchY - vitalsFastRailRect.top) / vitalsFastRailRect.height()).coerceIn(0f, 1f)
        vitalsModalScrollOffset = frac * vitalsModalMaxScroll
        invalidate()
    }

    when (event.actionMasked) {
        MotionEvent.ACTION_DOWN -> {
            touchDownX = x; touchDownY = y; lastTouchY = y
            if (vitalsCloseRect.contains(x, y) || !vitalsModalRect.contains(x, y)) {
                triggerHardwareHaptic(18, 80)
                closeVitalsTaskManagerModal()
                return true
            }
            val railSlop = if (vitalsActiveTabKey == "storage") 5f * resources.displayMetrics.density else 0f
            if (!vitalsFastRailRect.isEmpty &&
                x >= vitalsFastRailRect.left - railSlop &&
                x <= vitalsFastRailRect.right + railSlop &&
                y >= vitalsFastRailRect.top &&
                y <= vitalsFastRailRect.bottom
            ) {
                vitalsModalDraggingRail = true
                triggerHardwareHaptic(14, 70)
                scrollFromRailY(y)
                return true
            }
            return true
        }
        MotionEvent.ACTION_MOVE -> {
            if (vitalsModalDraggingRail) {
                scrollFromRailY(y)
                return true
            }
            val dy = y - lastTouchY
            lastTouchY = y
            if (vitalsModalMaxScroll > 0f) {
                vitalsModalScrollOffset = (vitalsModalScrollOffset - dy).coerceIn(0f, vitalsModalMaxScroll)
                invalidate()
            }
            return true
        }
        MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
            if (vitalsModalDraggingRail) {
                vitalsModalDraggingRail = false
                vitalsStorageDragY = -1f
                invalidate()
                return true
            }
            if (hypot((x - touchDownX).toDouble(), (y - touchDownY).toDouble()) < 16f) {
                if (vitalsTempUnitToggleRect.contains(x, y)) {
                    triggerHardwareHaptic(16, 80)
                    LightspeedCruiseHudPods.toggleTempUnit(context)
                    invalidate()
                    return true
                }
                for ((key, rect) in vitalsSpanPillRects) {
                    if (rect.contains(x, y)) {
                        triggerHardwareHaptic(16, 80)
                        vitalsModalSpanKey = key
                        invalidate()
                        return true
                    }
                }
                for ((tKey, rect) in vitalsTabPillRects) {
                    if (rect.contains(x, y)) {
                        triggerHardwareHaptic(18, 90)
                        switchVitalsTaskTab(tKey)
                        return true
                    }
                }
                if (vitalsActiveTabKey == "storage" && handleVitalsStorageTabTouch(x, y)) {
                    return true
                }
                for ((pkg, rect) in vitalsForceStopRects) {
                    if (rect.contains(x, y)) {
                        val label = vitalsCachedMemApps.firstOrNull { it.packageName == pkg }?.label ?: pkg
                        triggerHardwareHaptic(24, 120)
                        thread(name = "VitalsForceStop") {
                            val stopped = LightspeedVitalsHistoryStore.forceStopApp(
                                context = context,
                                packageName = pkg,
                                appLabel = label,
                                onBeforeStartSettings = {
                                    closeVitalsTaskManagerModal()
                                    dismissOverlay()
                                }
                            )
                            val updated = if (stopped) {
                                vitalsCachedMemApps.filterNot { it.packageName == pkg }
                            } else {
                                vitalsCachedMemApps
                            }
                            post {
                                vitalsCachedMemApps = updated
                                invalidate()
                            }
                        }
                        return true
                    }
                }
            }
            return true
        }
    }
    return true
}

/**
 * Draws an animated tactical radar sweep inside the Vitals / Storage loading canvas
 * and schedules the next VSync frame while background sampling is active.
 */
internal fun LightspeedCruiseOverlay.drawVitalsLoadingRadar(
    canvas: Canvas,
    centerX: Float,
    centerY: Float,
    accent: Int,
    scanMsg: String,
    textPaint: Paint,
    highlightPaint: Paint
) {
    val dp = resources.displayMetrics.density
    val r = 24f * dp
    val nowMs = android.os.SystemClock.uptimeMillis()
    val sweepDeg = ((nowMs % 1500L) / 1500f) * 360f
    val ar = Color.red(accent)
    val ag = Color.green(accent)
    val ab = Color.blue(accent)

    highlightPaint.shader = null
    highlightPaint.style = Paint.Style.STROKE
    highlightPaint.strokeWidth = 1.2f * dp
    highlightPaint.color = Color.argb(105, ar, ag, ab)
    canvas.drawCircle(centerX, centerY, r, highlightPaint)
    highlightPaint.strokeWidth = 0.8f * dp
    highlightPaint.color = Color.argb(65, ar, ag, ab)
    canvas.drawCircle(centerX, centerY, r * 0.62f, highlightPaint)
    canvas.drawLine(centerX - r, centerY, centerX + r, centerY, highlightPaint)
    canvas.drawLine(centerX, centerY - r, centerX, centerY + r, highlightPaint)

    highlightPaint.style = Paint.Style.FILL
    highlightPaint.color = Color.argb(56, ar, ag, ab)
    canvas.drawArc(
        centerX - r,
        centerY - r,
        centerX + r,
        centerY + r,
        sweepDeg - 48f,
        48f,
        true,
        highlightPaint
    )

    val rad = Math.toRadians(sweepDeg.toDouble())
    highlightPaint.style = Paint.Style.STROKE
    highlightPaint.strokeWidth = 1.7f * dp
    highlightPaint.color = Color.argb(235, ar, ag, ab)
    canvas.drawLine(
        centerX,
        centerY,
        centerX + (kotlin.math.cos(rad) * r).toFloat(),
        centerY + (kotlin.math.sin(rad) * r).toFloat(),
        highlightPaint
    )

    val blips = arrayOf(42f to 0.56f, 168f to 0.78f, 284f to 0.44f)
    highlightPaint.style = Paint.Style.FILL
    for ((deg, dist) in blips) {
        val bRad = Math.toRadians(deg.toDouble())
        val diff = ((sweepDeg - deg) % 360f + 360f) % 360f
        val pulse = if (diff < 110f) (1f - diff / 110f) else 0.14f
        highlightPaint.color = Color.argb((60 + pulse * 195).toInt().coerceIn(0, 255), ar, ag, ab)
        canvas.drawCircle(
            centerX + (kotlin.math.cos(bRad) * r * dist).toFloat(),
            centerY + (kotlin.math.sin(bRad) * r * dist).toFloat(),
            2.2f * dp * (0.85f + pulse * 0.4f),
            highlightPaint
        )
    }

    textPaint.textAlign = Paint.Align.CENTER
    textPaint.typeface = Typeface.MONOSPACE
    textPaint.textSize = 8.4f * dp
    textPaint.color = Color.argb(210, ar, ag, ab)
    canvas.drawText(scanMsg, centerX, centerY + r + 18f * dp, textPaint)
    postInvalidateOnAnimation()
}
