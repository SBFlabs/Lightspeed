package com.sbf.lightspeed

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.os.SystemClock
import com.sbf.lightspeed.system.LightspeedPreferences
import com.sbf.lightspeed.system.defaultPrefs
import kotlin.concurrent.thread
import kotlin.math.max

private val vitalsBitmapPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
private val vitalsIconDestRect = RectF()
private val vitalsRowTempRect = RectF()
private val vitalsStopTempRect = RectF()
private val vitalsInfoBtnTempRect = RectF()
private val vitalsMonoBoldTypeface: Typeface by lazy { Typeface.create(Typeface.MONOSPACE, Typeface.BOLD) }

private val VITALS_TASK_TABS = listOf(
    "cpu" to "CPU & HEAT",
    "wakelocks" to "WAKELOCK",
    "ram" to "RAM & SWAP",
    "storage" to "STORAGE"
)

internal var vitalsActiveTabKey: String = "cpu"
internal val vitalsTabPillRects = mutableMapOf<String, RectF>()

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
    vitalsInfoOpen = false
    vitalsModalScrollOffset = 0f
    vitalsModalScroller.forceFinished(true)
    vitalsModalDraggingRail = false
    vitalsChartCache = null
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
            val iconPx = (26f * resources.displayMetrics.density).toInt().coerceIn(24, 96)
            for (pkg in entries.take(20).map { it.packageName }) {
                decodeVitalsBitmapIfNeeded(pkg, iconPx)
            }
            post {
                if (vitalsActiveTabKey == tabKey) {
                    vitalsCachedMemApps = sortVitalsTaskEntries(
                        entries = entries,
                        tabKey = tabKey,
                        ramBySwap = vitalsRamSortBySwap,
                        cpuSortMode = vitalsCpuSortMode,
                        wakeSortMode = vitalsWakeSortMode
                    )
                    vitalsModalLoadingApps = false
                    invalidate()
                    if (entries.size > 20) {
                        preloadRemainingVitalsIconsAsync(entries.drop(20).map { it.packageName }, iconPx, tabKey)
                    }
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
    vitalsModalScroller.forceFinished(true)
    vitalsVelocityTracker?.recycle()
    vitalsVelocityTracker = null
    vitalsChartCache = null
    vitalsRowIconLruCache.evictAll()
    vitalsRowIconInFlight.clear()
    isVitalsModalOpen = false
    vitalsInfoOpen = false
    vitalsModalDraggingRail = false
    invalidate()
}

private fun LightspeedCruiseOverlay.drawableToScaledBitmap(drawable: Drawable, sizePx: Int): Bitmap? {
    return try {
        if (drawable is BitmapDrawable && drawable.bitmap != null && !drawable.bitmap.isRecycled) {
            val src = drawable.bitmap
            if (src.width <= sizePx * 2 && src.height <= sizePx * 2) return src
            return Bitmap.createScaledBitmap(src, sizePx, sizePx, true)
        }
        val bmp = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        drawable.setBounds(0, 0, sizePx, sizePx)
        drawable.draw(c)
        bmp
    } catch (_: Exception) {
        null
    }
}

private fun LightspeedCruiseOverlay.decodeVitalsBitmapIfNeeded(packageName: String, sizePx: Int): Bitmap? {
    vitalsRowIconLruCache.get(packageName)?.let { return it }
    applicationIconCache[packageName]?.let { cachedDrawable ->
        drawableToScaledBitmap(cachedDrawable, sizePx)?.let { bmp ->
            vitalsRowIconLruCache.put(packageName, bmp)
            return bmp
        }
    }
    return try {
        val d = context.packageManager.getApplicationIcon(packageName)
        drawableToScaledBitmap(d, sizePx)?.also { bmp ->
            vitalsRowIconLruCache.put(packageName, bmp)
        }
    } catch (_: Exception) {
        null
    }
}

private fun LightspeedCruiseOverlay.preloadRemainingVitalsIconsAsync(
    packages: List<String>,
    sizePx: Int,
    expectedTabKey: String
) {
    if (packages.isEmpty()) return
    thread(name = "VitalsIconPreload") {
        var batch = 0
        for (pkg in packages) {
            if (!isVitalsModalOpen || vitalsActiveTabKey != expectedTabKey) return@thread
            if (vitalsRowIconLruCache.get(pkg) == null) {
                decodeVitalsBitmapIfNeeded(pkg, sizePx)
                batch++
                if (batch % 12 == 0) postInvalidate()
            }
        }
        if (batch > 0 && isVitalsModalOpen && vitalsActiveTabKey == expectedTabKey) {
            postInvalidate()
        }
    }
}

internal fun LightspeedCruiseOverlay.getVitalsRowBitmap(packageName: String, sizePx: Int): Bitmap? {
    vitalsRowIconLruCache.get(packageName)?.let { return it }
    if (vitalsRowIconInFlight.add(packageName)) {
        val activeTab = vitalsActiveTabKey
        thread(name = "VitalsRowIconOne") {
            try {
                decodeVitalsBitmapIfNeeded(packageName, sizePx)
            } finally {
                vitalsRowIconInFlight.remove(packageName)
                if (isVitalsModalOpen && vitalsActiveTabKey == activeTab) {
                    postInvalidate()
                }
            }
        }
    }
    return null
}

internal fun LightspeedCruiseOverlay.drawVitalsTaskManagerModal(
    canvas: Canvas,
    density: Float,
    accent: Int
) {
    if (vitalsModalScroller.computeScrollOffset()) {
        vitalsModalScrollOffset = vitalsModalScroller.currY.toFloat().coerceAtLeast(0f)
        postInvalidateOnAnimation()
    }

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

    // "!" explainer button (CPU & HEAT / WAKELOCK / RAM & SWAP); Storage has its own UI
    if (vitalsActiveTabKey == "storage") {
        vitalsInfoButtonRect.setEmpty()
        vitalsInfoOpen = false
    } else {
        vitalsInfoBtnTempRect.set(
            vitalsTempUnitToggleRect.left - 6f * dp - closeSize,
            vitalsTempUnitToggleRect.top,
            vitalsTempUnitToggleRect.left - 6f * dp,
            vitalsTempUnitToggleRect.bottom
        )
        drawVitalsInfoButton(
            canvas = canvas,
            rect = vitalsInfoBtnTempRect,
            accent = accent,
            dp = dp
        )
    }

    // 4-Mode Switcher Tabs right beneath Header: [ CPU & HEAT ] [ WAKELOCK ] [ RAM & SWAP ] [ STORAGE ]
    val tabTop = top + 39f * dp
    val tabH = 26f * dp
    val tabGap = 4f * dp
    val tabW = (modalW - padX * 2f - tabGap * (VITALS_TASK_TABS.size - 1)) / VITALS_TASK_TABS.size
    for ((idx, tab) in VITALS_TASK_TABS.withIndex()) {
        val (tKey, tLabel) = tab
        val isSel = (vitalsActiveTabKey == tKey)
        val tLeft = left + padX + idx * (tabW + tabGap)
        val tRect = vitalsTabPillRects.getOrPut(tKey) { RectF() }
        tRect.set(tLeft, tabTop, tLeft + tabW, tabTop + tabH)

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
    vitalsTaskSortPillRects.clear()
    vitalsStorageJumpMarkers.clear()
    vitalsStorageReversePillRect.setEmpty()
    vitalsStorageRefreshPillRect.setEmpty()
    vitalsStoragePermBannerRect.setEmpty()
    vitalsStorageChangeBannerRect.setEmpty()

    val chartBottom = drawVitalsTelemetryHistorySection(
        canvas = canvas,
        left = left,
        padX = padX,
        modalW = modalW,
        tabTop = tabTop,
        tabH = tabH,
        accent = accent,
        dp = dp
    )

    val sortBarTop = chartBottom + 6f * dp
    val sortBarH = 22f * dp
    drawVitalsTaskSortBar(
        canvas = canvas,
        left = left + padX,
        right = vitalsModalRect.right - padX,
        top = sortBarTop,
        height = sortBarH,
        itemCount = vitalsCachedMemApps.size,
        accent = accent,
        dp = dp
    )

    val railW = 34f * dp
    val railGap = 5f * dp
    val listLeft = left + padX
    val listRight = vitalsModalRect.right - padX - railW - railGap
    val listTop = sortBarTop + sortBarH + 5f * dp
    val listBottom = vitalsModalRect.bottom - 12f * dp
    val listH = (listBottom - listTop).coerceAtLeast(40f * dp)

    val rowH = 48f * dp
    vitalsStorageRowH = rowH
    val totalContentH = vitalsCachedMemApps.size * rowH
    vitalsModalMaxScroll = max(0f, totalContentH - listH)
    vitalsModalScrollOffset = vitalsModalScrollOffset.coerceIn(0f, vitalsModalMaxScroll)

    // Numbered Quick-Jump Fast Rail (matches Storage rail UX across RAM, CPU & Wakelock)
    vitalsFastRailRect.set(listRight + railGap, listTop, vitalsModalRect.right - padX, listBottom)
    val rawTaskMarkers = buildVitalsTaskJumpMarkers(
        entries = vitalsCachedMemApps,
        tabKey = vitalsActiveTabKey,
        ramBySwap = vitalsRamSortBySwap,
        cpuSortMode = vitalsCpuSortMode,
        wakeSortMode = vitalsWakeSortMode
    )
    vitalsStorageJumpMarkers.addAll(
        drawVitalsNumberedJumpRail(
            canvas = canvas,
            railRect = vitalsFastRailRect,
            rawMarkers = rawTaskMarkers,
            scrollOffset = vitalsModalScrollOffset,
            maxScroll = vitalsModalMaxScroll,
            rowH = rowH,
            itemCount = vitalsCachedMemApps.size,
            accent = accent,
            dp = dp,
            highlightPaint = highlightPaint,
            textPaint = textPaint
        )
    )

    vitalsForceStopRects.clear()
    val listLayerCount = canvas.saveLayer(listLeft, listTop, listRight, listBottom, null)
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
        val rRect = vitalsRowTempRect
        val stopRect = vitalsStopTempRect
        val iconSizePx = (26f * dp).toInt().coerceIn(24, 96)
        for (entry in vitalsCachedMemApps) {
            rRect.set(listLeft, curY, listRight, curY + rowH - 5f * dp)
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
                val rowBmp = getVitalsRowBitmap(entry.packageName, iconSizePx)
                if (rowBmp != null && !rowBmp.isRecycled) {
                    vitalsIconDestRect.set(iconLeft, iconTop, iconLeft + iconSize, iconTop + iconSize)
                    canvas.drawBitmap(rowBmp, null, vitalsIconDestRect, vitalsBitmapPaint)
                } else {
                    highlightPaint.style = Paint.Style.FILL
                    highlightPaint.color = Color.argb(38, 255, 255, 255)
                    canvas.drawCircle(iconLeft + iconSize * 0.5f, rRect.centerY(), iconSize * 0.42f, highlightPaint)
                }

                // 1-Tap Force Stop Pill
                val stopW = 46f * dp
                val stopH = 22f * dp
                stopRect.set(rRect.right - stopW - 7f * dp, rRect.centerY() - stopH * 0.5f, rRect.right - 7f * dp, rRect.centerY() + stopH * 0.5f)
                vitalsForceStopRects.getOrPut(entry.packageName) { RectF() }.set(stopRect)
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
                textPaint.textSize = 9.0f * dp
                textPaint.color = stopColor
                val sFm = textPaint.fontMetrics
                canvas.drawText("STOP", stopRect.centerX(), stopRect.centerY() - ((sFm.descent + sFm.ascent) / 2f), textPaint)

                // Line 1 Right: Primary Metric matching current tab & sort mode
                val primaryMetric = formatVitalsTaskPrimaryMetric(
                    entry = entry,
                    tabKey = vitalsActiveTabKey,
                    ramBySwap = vitalsRamSortBySwap,
                    cpuSortMode = vitalsCpuSortMode
                )
                textPaint.textAlign = Paint.Align.RIGHT
                textPaint.typeface = vitalsMonoBoldTypeface
                textPaint.textSize = 11.2f * dp
                textPaint.color = if (entry.isHighRisk) Color.rgb(245, 158, 11) else accent
                val metricX = stopRect.left - 7f * dp
                val metricW = textPaint.measureText(primaryMetric)
                canvas.drawText(primaryMetric, metricX, rRect.top + 17.5f * dp, textPaint)

                val txtLeft = iconLeft + iconSize + 8f * dp
                val maxTitleW = (metricX - metricW - txtLeft - 6f * dp).coerceAtLeast(36f * dp)
                textPaint.textAlign = Paint.Align.LEFT
                textPaint.typeface = Typeface.DEFAULT_BOLD
                textPaint.textSize = 11.8f * dp
                textPaint.color = Color.WHITE
                canvas.drawText(clipTextToWidth(entry.label, maxTitleW), txtLeft, rRect.top + 17.5f * dp, textPaint)

                val maxSubW = (stopRect.left - txtLeft - 7f * dp).coerceAtLeast(40f * dp)
                textPaint.typeface = Typeface.MONOSPACE
                textPaint.textSize = 9.2f * dp
                textPaint.color = if (entry.isHighRisk) Color.rgb(245, 158, 11) else Color.rgb(34, 211, 238)
                val subStr = entry.subtitleOverride ?: if (entry.swapMb > 0.1f) {
                    String.format(java.util.Locale.US, "RAM: %.0f MB · SWAP: %.0f MB", entry.ramMb, entry.swapMb)
                } else {
                    String.format(java.util.Locale.US, "RAM: %.0f MB", entry.ramMb)
                }
                canvas.drawText(clipTextToWidth(subStr, maxSubW), txtLeft, rRect.top + 34.5f * dp, textPaint)
            }
            curY += rowH
        }
    }
    LightspeedHudVectorIcons.applyVerticalEdgeFeathering(
        canvas = canvas,
        left = listLeft,
        top = listTop,
        right = listRight,
        bottom = listBottom,
        featherTopPx = if (vitalsModalScrollOffset > 0.5f) 18f * dp else 0f,
        featherBottomPx = if (vitalsModalScrollOffset < vitalsModalMaxScroll - 0.5f) 20f * dp else 0f,
        density = dp,
        seed = 8101
    )
    canvas.restoreToCount(listLayerCount)

    if (vitalsModalDraggingRail && vitalsCachedMemApps.isNotEmpty()) {
        val idx = (vitalsModalScrollOffset / rowH.coerceAtLeast(1f)).toInt().coerceIn(0, vitalsCachedMemApps.lastIndex)
        val app = vitalsCachedMemApps[idx]
        val loupeMetric = formatVitalsTaskLoupeMetric(
            entry = app,
            tabKey = vitalsActiveTabKey,
            ramBySwap = vitalsRamSortBySwap,
            cpuSortMode = vitalsCpuSortMode
        )
        drawVitalsScrubLoupeBubble(
            canvas = canvas,
            listRight = listRight,
            railRect = vitalsFastRailRect,
            dragY = vitalsStorageDragY,
            primaryText = loupeMetric,
            secondaryLabel = app.label,
            accent = accent,
            dp = dp
        )
    }

    if (vitalsInfoOpen) {
        drawVitalsInfoCard(
            canvas = canvas,
            left = left + padX,
            top = sortBarTop,
            right = vitalsModalRect.right - padX,
            bottom = listBottom,
            tabKey = vitalsActiveTabKey,
            accent = accent,
            dp = dp
        )
    }
}
