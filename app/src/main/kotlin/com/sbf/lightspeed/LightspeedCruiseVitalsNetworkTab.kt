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
import com.sbf.lightspeed.system.LightspeedUsageStats
import kotlin.concurrent.thread
import kotlin.math.max

/**
 * State holder for the App Hunter NETWORK tab inside [LightspeedCruiseOverlay]
 * so `LightspeedCruiseOverlay.kt` adds only a single field.
 */
internal class VitalsNetworkTabState {
    var spanKey: String = "TODAY"
    var transportFilter: String = "ALL" // ALL | WIFI | CELL
    var reversed: Boolean = false
    var hasUsageAccess: Boolean = true
    var isLoading: Boolean = false
    var allEntries: List<LightspeedNetworkUsageAnalyzer.AppNetworkUsageEntry> = emptyList()
    var filteredEntries: List<LightspeedNetworkUsageAnalyzer.AppNetworkUsageEntry> = emptyList()
    var deviceWifiRx: Long = 0L
    var deviceWifiTx: Long = 0L
    var deviceCellRx: Long = 0L
    var deviceCellTx: Long = 0L
    var lastQueriedMs: Long = 0L
    var rowHeightPx: Float = 0f

    val spanPillRects: MutableMap<String, RectF> = mutableMapOf()
    val transportPillRects: MutableMap<String, RectF> = mutableMapOf()
    val reversePillRect: RectF = RectF()
    val refreshPillRect: RectF = RectF()
    val permBannerRect: RectF = RectF()
    val appRowInfoRects: MutableMap<String, RectF> = mutableMapOf()
    val scanToken = java.util.concurrent.atomic.AtomicInteger(0)

    fun applyFilter(resetScroll: Boolean = true): Unit {
        filteredEntries = LightspeedNetworkUsageAnalyzer.filterAndSort(
            entries = allEntries,
            transportFilter = transportFilter,
            reversed = reversed
        )
    }

    fun deviceTotalForFilter(): Long = when (transportFilter) {
        "WIFI" -> deviceWifiRx + deviceWifiTx
        "CELL" -> deviceCellRx + deviceCellTx
        else -> deviceWifiRx + deviceWifiTx + deviceCellRx + deviceCellTx
    }
}

private val NETWORK_SIZE_TIERS_BYTES = listOf(
    1_073_741_824L to "1G+",
    524_288_000L to "500M",
    104_857_600L to "100M",
    52_428_800L to "50M",
    10_485_760L to "10M",
    1_048_576L to "1M",
    0L to "<1M"
)

private fun buildNetworkJumpMarkers(
    entries: List<LightspeedNetworkUsageAnalyzer.AppNetworkUsageEntry>,
    transportFilter: String,
    reversed: Boolean
): List<StorageJumpMarker> {
    if (entries.isEmpty() || reversed) return emptyList()
    val out = mutableListOf<StorageJumpMarker>()
    var upperBound = Long.MAX_VALUE
    for ((minBytes, label) in NETWORK_SIZE_TIERS_BYTES) {
        val idx = entries.indexOfFirst { entry ->
            val v = entry.totalForTransport(transportFilter)
            v >= minBytes && v < upperBound
        }
        if (idx >= 0 && out.none { it.rowIndex == idx }) {
            out.add(StorageJumpMarker(label = label, rowIndex = idx))
        }
        upperBound = minBytes
    }
    return out
}

internal fun LightspeedCruiseOverlay.loadVitalsNetworkTabAsync(forceRefresh: Boolean = false) {
    val st = vitalsNetworkState
    val span = st.spanKey
    val token = st.scanToken.incrementAndGet()
    val hasAccessNow = LightspeedUsageStats.hasUsageAccess(context)
    if (!hasAccessNow) {
        st.hasUsageAccess = false
        st.isLoading = false
        st.allEntries = emptyList()
        st.filteredEntries = emptyList()
        invalidate()
        return
    }
    if (!forceRefresh) {
        LightspeedNetworkUsageAnalyzer.getCachedSnapshotIfFresh(span)?.let { cached ->
            st.hasUsageAccess = cached.hasUsageAccess
            st.deviceWifiRx = cached.deviceWifiRx
            st.deviceWifiTx = cached.deviceWifiTx
            st.deviceCellRx = cached.deviceCellRx
            st.deviceCellTx = cached.deviceCellTx
            st.allEntries = cached.entries
            st.lastQueriedMs = cached.queriedAtMs
            st.isLoading = false
            st.applyFilter()
            invalidate()
            return
        }
    }

    st.isLoading = true
    st.hasUsageAccess = true
    invalidate()
    val knownLabels = try {
        dataBridge.getAllApps().associate { it.packageName to it.label }
    } catch (_: Exception) {
        emptyMap()
    }
    val iconPx = (28f * resources.displayMetrics.density).toInt().coerceIn(24, 96)
    thread(name = "VitalsNetworkQuery") {
        val snap = try {
            LightspeedNetworkUsageAnalyzer.queryAppNetworkUsage(
                context = context,
                spanKey = span,
                knownLabels = knownLabels,
                forceRefresh = forceRefresh
            )
        } catch (_: Throwable) {
            LightspeedNetworkUsageAnalyzer.NetworkUsageSnapshot(
                spanKey = span,
                hasUsageAccess = LightspeedUsageStats.hasUsageAccess(context),
                deviceWifiRx = 0L,
                deviceWifiTx = 0L,
                deviceCellRx = 0L,
                deviceCellTx = 0L,
                entries = emptyList(),
                queriedAtMs = System.currentTimeMillis()
            )
        }
        for (entry in snap.entries.take(20)) {
            if (!entry.isSystemSpecial) {
                getVitalsRowBitmap(entry.packageName, iconPx)
            }
        }
        post {
            if (st.scanToken.get() != token || !isVitalsModalOpen || vitalsActiveTabKey != "network") {
                return@post
            }
            st.hasUsageAccess = snap.hasUsageAccess
            st.deviceWifiRx = snap.deviceWifiRx
            st.deviceWifiTx = snap.deviceWifiTx
            st.deviceCellRx = snap.deviceCellRx
            st.deviceCellTx = snap.deviceCellTx
            st.allEntries = snap.entries
            st.lastQueriedMs = snap.queriedAtMs
            st.isLoading = false
            st.applyFilter()
            vitalsModalScrollOffset = 0f
            invalidate()
        }
    }
}

internal fun LightspeedCruiseOverlay.drawVitalsNetworkTabContent(
    canvas: Canvas,
    startTop: Float,
    padX: Float,
    modalW: Float,
    accent: Int,
    dp: Float
) {
    val st = vitalsNetworkState
    val hasCellular = LightspeedNetworkUsageAnalyzer.hasCellularCapability(context)
    if (!hasCellular && st.transportFilter == "CELL") {
        st.transportFilter = "ALL"
        st.applyFilter()
    }
    val transportOptions = LightspeedNetworkUsageAnalyzer.transportOptionsFor(hasCellular)
    val ar = Color.red(accent)
    val ag = Color.green(accent)
    val ab = Color.blue(accent)
    val left = vitalsModalRect.left
    val right = vitalsModalRect.right

    st.spanPillRects.clear()
    st.transportPillRects.clear()
    st.appRowInfoRects.clear()
    vitalsStorageJumpMarkers.clear()

    // Row 1: Summary total + [ALL | WI-FI | CELL] (or [ALL | WI-FI] on Wi-Fi-only devices) + Refresh [↻]
    val row1Top = startTop + 4f * dp
    val row1H = 26f * dp
    val refreshW = 28f * dp
    val trPillW = 42f * dp
    val trGap = 3.5f * dp
    val trCount = transportOptions.size
    val trGroupW = trCount * trPillW + (trCount - 1) * trGap
    val refreshLeft = right - padX - refreshW
    val trGroupLeft = refreshLeft - 5f * dp - trGroupW

    st.refreshPillRect.set(refreshLeft, row1Top, refreshLeft + refreshW, row1Top + row1H)
    highlightPaint.style = Paint.Style.FILL
    highlightPaint.color = Color.argb(46, ar, ag, ab)
    canvas.drawRoundRect(st.refreshPillRect, 6f * dp, 6f * dp, highlightPaint)
    highlightPaint.style = Paint.Style.STROKE
    highlightPaint.strokeWidth = 0.9f * dp
    highlightPaint.color = Color.argb(165, ar, ag, ab)
    canvas.drawRoundRect(st.refreshPillRect, 6f * dp, 6f * dp, highlightPaint)
    LightspeedHudVectorIcons.drawVectorRefresh(
        canvas = canvas,
        cx = st.refreshPillRect.centerX(),
        cy = st.refreshPillRect.centerY(),
        r = 5.2f * dp,
        color = accent,
        strokeW = 1.55f * dp
    )

    var fm = textPaint.fontMetrics
    for ((idx, opt) in transportOptions.withIndex()) {
        val (tKey, tLabel) = opt
        val isSel = (st.transportFilter == tKey)
        val pLeft = trGroupLeft + idx * (trPillW + trGap)
        val pRect = RectF(pLeft, row1Top, pLeft + trPillW, row1Top + row1H)
        st.transportPillRects[tKey] = pRect

        highlightPaint.style = Paint.Style.FILL
        highlightPaint.color = if (isSel) Color.argb(215, ar, ag, ab) else Color.argb(36, 255, 255, 255)
        canvas.drawRoundRect(pRect, 6f * dp, 6f * dp, highlightPaint)
        highlightPaint.style = Paint.Style.STROKE
        highlightPaint.strokeWidth = 0.85f * dp
        highlightPaint.color = if (isSel) accent else Color.argb(65, 255, 255, 255)
        canvas.drawRoundRect(pRect, 6f * dp, 6f * dp, highlightPaint)

        textPaint.textAlign = Paint.Align.CENTER
        textPaint.typeface = Typeface.DEFAULT_BOLD
        textPaint.textSize = 9.0f * dp
        textPaint.color = if (isSel) Color.BLACK else Color.WHITE
        fm = textPaint.fontMetrics
        canvas.drawText(tLabel, pRect.centerX(), pRect.centerY() - ((fm.descent + fm.ascent) / 2f), textPaint)
    }

    val displayedTotal = st.filteredEntries.sumOf { it.totalForTransport(st.transportFilter) }
    val devTotal = maxOf(st.deviceTotalForFilter(), displayedTotal)
    val summaryText = "${st.filteredEntries.size} APPS · ${LightspeedCruiseHudPods.formatTrafficSize(devTotal)}"
    val maxSummaryW = (trGroupLeft - (left + padX) - 6f * dp).coerceAtLeast(48f * dp)
    textPaint.textAlign = Paint.Align.LEFT
    textPaint.typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
    textPaint.textSize = 10.6f * dp
    textPaint.color = Color.rgb(34, 211, 238)
    fm = textPaint.fontMetrics
    canvas.drawText(clipTextToWidth(summaryText, maxSummaryW), left + padX, row1Top + row1H * 0.5f - ((fm.descent + fm.ascent) / 2f), textPaint)

    // Row 2: Time Span Pills [1H | 3H | TODAY | 3D | 7D | 30D] + Reverse arrow [↓ / ↑]
    val row2Top = row1Top + row1H + 6f * dp
    val row2H = 25f * dp
    val revW = 30f * dp
    val spanGap = 3.5f * dp
    val spanAvailW = (modalW - padX * 2f - revW - spanGap).coerceAtLeast(120f * dp)
    val spanCount = LightspeedNetworkUsageAnalyzer.SPAN_OPTIONS.size
    val spanPillW = (spanAvailW - spanGap * (spanCount - 1)) / spanCount
    val spanTextSize = (spanPillW / 5.4f).coerceIn(7.6f * dp, 9.0f * dp)

    for ((idx, opt) in LightspeedNetworkUsageAnalyzer.SPAN_OPTIONS.withIndex()) {
        val (sKey, sLabel) = opt
        val isSel = (st.spanKey == sKey)
        val sLeft = left + padX + idx * (spanPillW + spanGap)
        val sRect = RectF(sLeft, row2Top, sLeft + spanPillW, row2Top + row2H)
        st.spanPillRects[sKey] = sRect

        highlightPaint.style = Paint.Style.FILL
        highlightPaint.color = if (isSel) Color.argb(215, ar, ag, ab) else Color.argb(36, 255, 255, 255)
        canvas.drawRoundRect(sRect, 5f * dp, 5f * dp, highlightPaint)
        highlightPaint.style = Paint.Style.STROKE
        highlightPaint.strokeWidth = 0.8f * dp
        highlightPaint.color = if (isSel) accent else Color.argb(65, 255, 255, 255)
        canvas.drawRoundRect(sRect, 5f * dp, 5f * dp, highlightPaint)

        textPaint.textAlign = Paint.Align.CENTER
        textPaint.typeface = Typeface.DEFAULT_BOLD
        textPaint.textSize = spanTextSize
        textPaint.color = if (isSel) Color.BLACK else Color.WHITE
        fm = textPaint.fontMetrics
        canvas.drawText(sLabel, sRect.centerX(), sRect.centerY() - ((fm.descent + fm.ascent) / 2f), textPaint)
    }

    val revLeft = right - padX - revW
    st.reversePillRect.set(revLeft, row2Top, revLeft + revW, row2Top + row2H)
    highlightPaint.style = Paint.Style.FILL
    highlightPaint.color = Color.argb(58, ar, ag, ab)
    canvas.drawRoundRect(st.reversePillRect, 5f * dp, 5f * dp, highlightPaint)
    highlightPaint.style = Paint.Style.STROKE
    highlightPaint.strokeWidth = 0.9f * dp
    highlightPaint.color = accent
    canvas.drawRoundRect(st.reversePillRect, 5f * dp, 5f * dp, highlightPaint)
    LightspeedHudVectorIcons.drawVectorArrow(
        canvas = canvas,
        cx = st.reversePillRect.centerX(),
        cy = st.reversePillRect.centerY(),
        r = 5.0f * dp,
        direction = if (st.reversed) 1 else 0,
        color = Color.WHITE,
        strokeW = 1.6f * dp
    )

    var curListTop = row2Top + row2H + 7f * dp

    if (!st.hasUsageAccess) {
        val bannerH = 26f * dp
        st.permBannerRect.set(left + padX, curListTop, right - padX, curListTop + bannerH)
        highlightPaint.style = Paint.Style.FILL
        highlightPaint.color = Color.argb(46, 245, 158, 11)
        canvas.drawRoundRect(st.permBannerRect, 6f * dp, 6f * dp, highlightPaint)
        highlightPaint.style = Paint.Style.STROKE
        highlightPaint.strokeWidth = 0.85f * dp
        highlightPaint.color = Color.rgb(245, 158, 11)
        canvas.drawRoundRect(st.permBannerRect, 6f * dp, 6f * dp, highlightPaint)
        textPaint.textAlign = Paint.Align.CENTER
        textPaint.typeface = Typeface.DEFAULT_BOLD
        textPaint.textSize = 9.0f * dp
        textPaint.color = Color.rgb(245, 158, 11)
        fm = textPaint.fontMetrics
        val permBannerText = if (hasCellular) {
            "! TAP FOR USAGE ACCESS (READS WI-FI & CELL USAGE)"
        } else {
            "! TAP FOR USAGE ACCESS (READS WI-FI USAGE)"
        }
        canvas.drawText(
            clipTextToWidth(permBannerText, st.permBannerRect.width() - 10f * dp),
            st.permBannerRect.centerX(),
            st.permBannerRect.centerY() - ((fm.descent + fm.ascent) / 2f),
            textPaint
        )
        curListTop += bannerH + 6f * dp
    } else {
        st.permBannerRect.setEmpty()
    }

    val railW = 34f * dp
    val railGap = 5f * dp
    val listLeft = left + padX
    val listRight = right - padX - railW - railGap
    val listBottom = vitalsModalRect.bottom - 10f * dp
    val listH = (listBottom - curListTop).coerceAtLeast(48f * dp)
    val rowH = 52f * dp
    st.rowHeightPx = rowH
    vitalsStorageRowH = rowH

    val items = st.filteredEntries
    val totalContentH = items.size * rowH
    vitalsModalMaxScroll = max(0f, totalContentH - listH)
    vitalsModalScrollOffset = vitalsModalScrollOffset.coerceIn(0f, vitalsModalMaxScroll)

    vitalsFastRailRect.set(listRight + railGap, curListTop, right - padX, listBottom)
    val rawMarkers = buildNetworkJumpMarkers(items, st.transportFilter, st.reversed)
    vitalsStorageJumpMarkers.addAll(
        drawVitalsNumberedJumpRail(
            canvas = canvas,
            railRect = vitalsFastRailRect,
            rawMarkers = rawMarkers,
            scrollOffset = vitalsModalScrollOffset,
            maxScroll = vitalsModalMaxScroll,
            rowH = rowH,
            itemCount = items.size,
            accent = accent,
            dp = dp,
            highlightPaint = highlightPaint,
            textPaint = textPaint
        )
    )

    val listLayerCount = canvas.saveLayer(listLeft, curListTop, listRight, listBottom, null)
    canvas.clipRect(listLeft, curListTop, listRight, listBottom)
    if (st.isLoading && items.isEmpty()) {
        drawVitalsLoadingRadar(
            canvas = canvas,
            centerX = (listLeft + listRight) * 0.5f,
            centerY = curListTop + (listBottom - curListTop) * 0.42f,
            accent = accent,
            scanMsg = "QUERYING NETWORK STATS (${st.spanKey})...",
            textPaint = textPaint,
            highlightPaint = highlightPaint
        )
    } else if (items.isEmpty()) {
        textPaint.textAlign = Paint.Align.CENTER
        textPaint.typeface = Typeface.DEFAULT_BOLD
        textPaint.textSize = 11.2f * dp
        textPaint.color = Color.argb(190, 200, 220, 245)
        canvas.drawText(
            if (st.hasUsageAccess) "NO DATA USAGE IN ${st.spanKey}" else "USAGE ACCESS REQUIRED",
            (listLeft + listRight) * 0.5f,
            curListTop + 42f * dp,
            textPaint
        )
    } else {
        val iconPx = (26f * dp).toInt().coerceIn(24, 96)
        val iconDest = RectF()
        val bmpPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
        val maxAppBytes = items.maxOfOrNull { it.totalForTransport(st.transportFilter) }?.coerceAtLeast(1L) ?: 1L
        val cyanColor = Color.rgb(34, 211, 238)
        val emeraldColor = Color.rgb(34, 197, 94)

        var curY = curListTop - vitalsModalScrollOffset
        for (entry in items) {
            val rRect = RectF(listLeft, curY, listRight, curY + rowH - 5f * dp)
            if (rRect.bottom >= curListTop && rRect.top <= listBottom) {
                highlightPaint.style = Paint.Style.FILL
                highlightPaint.color = Color.argb(24, 255, 255, 255)
                canvas.drawRoundRect(rRect, 7f * dp, 7f * dp, highlightPaint)

                val iconSize = 26f * dp
                val iconTop = rRect.centerY() - iconSize * 0.5f
                val iconLeft = rRect.left + 8f * dp
                val bmp = if (!entry.isSystemSpecial) getVitalsRowBitmap(entry.packageName, iconPx) else null
                if (bmp != null && !bmp.isRecycled) {
                    iconDest.set(iconLeft, iconTop, iconLeft + iconSize, iconTop + iconSize)
                    canvas.drawBitmap(bmp, null, iconDest, bmpPaint)
                } else {
                    highlightPaint.style = Paint.Style.FILL
                    highlightPaint.color = Color.argb(42, ar, ag, ab)
                    canvas.drawCircle(iconLeft + iconSize * 0.5f, rRect.centerY(), iconSize * 0.42f, highlightPaint)
                }

                if (!entry.isSystemSpecial) {
                    st.appRowInfoRects[entry.packageName] = RectF(rRect)
                }

                val entryTotal = entry.totalForTransport(st.transportFilter)
                val entryRx = entry.rxForTransport(st.transportFilter)
                val entryTx = entry.txForTransport(st.transportFilter)
                val totalStr = LightspeedCruiseHudPods.formatTrafficSize(entryTotal)
                val metricColor = when {
                    !hasCellular -> emeraldColor
                    st.transportFilter == "WIFI" -> emeraldColor
                    st.transportFilter == "CELL" -> cyanColor
                    else -> if (entry.wifiTotalBytes >= entry.cellTotalBytes) emeraldColor else cyanColor
                }

                textPaint.textAlign = Paint.Align.RIGHT
                textPaint.typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
                textPaint.textSize = 11.4f * dp
                textPaint.color = metricColor
                val metricX = rRect.right - 8f * dp
                val metricW = textPaint.measureText(totalStr)
                canvas.drawText(totalStr, metricX, rRect.top + 17.5f * dp, textPaint)

                val txtLeft = iconLeft + iconSize + 8f * dp
                val maxTitleW = (metricX - metricW - txtLeft - 6f * dp).coerceAtLeast(36f * dp)
                textPaint.textAlign = Paint.Align.LEFT
                textPaint.typeface = Typeface.DEFAULT_BOLD
                textPaint.textSize = 11.8f * dp
                textPaint.color = Color.WHITE
                canvas.drawText(clipTextToWidth(entry.label, maxTitleW), txtLeft, rRect.top + 17.5f * dp, textPaint)

                val downUp = "↓${LightspeedCruiseHudPods.formatTrafficSize(entryRx)} · ↑${LightspeedCruiseHudPods.formatTrafficSize(entryTx)}"
                val subText = if (hasCellular && st.transportFilter == "ALL") {
                    "$downUp · W:${LightspeedCruiseHudPods.formatTrafficSize(entry.wifiTotalBytes)} · C:${LightspeedCruiseHudPods.formatTrafficSize(entry.cellTotalBytes)}"
                } else {
                    downUp
                }
                val txtRight = rRect.right - 8f * dp
                val maxSubW = (txtRight - txtLeft).coerceAtLeast(48f * dp)
                textPaint.typeface = Typeface.MONOSPACE
                textPaint.textSize = 8.9f * dp
                textPaint.color = Color.argb(215, 200, 220, 245)
                canvas.drawText(clipTextToWidth(subText, maxSubW), txtLeft, rRect.top + 31.5f * dp, textPaint)

                val barRatio = (entryTotal.toFloat() / maxAppBytes.toFloat()).coerceIn(0.02f, 1f)
                val barTop = rRect.top + 36.5f * dp
                val barBot = barTop + 3.8f * dp
                highlightPaint.style = Paint.Style.FILL
                highlightPaint.color = Color.argb(38, 255, 255, 255)
                canvas.drawRoundRect(RectF(txtLeft, barTop, txtRight, barBot), 2f * dp, 2f * dp, highlightPaint)
                highlightPaint.color = metricColor
                canvas.drawRoundRect(RectF(txtLeft, barTop, txtLeft + ((txtRight - txtLeft) * barRatio).coerceAtLeast(3f * dp), barBot), 2f * dp, 2f * dp, highlightPaint)
            }
            curY += rowH
        }
    }
    LightspeedHudVectorIcons.applyScrollEdgeFeathering(
        canvas = canvas,
        left = listLeft,
        top = curListTop,
        right = listRight,
        bottom = listBottom,
        scrollOffset = vitalsModalScrollOffset,
        maxScroll = vitalsModalMaxScroll,
        featherTopPx = 18f * dp,
        featherBottomPx = 20f * dp,
        density = dp,
        seed = 8303
    )
    canvas.restoreToCount(listLayerCount)

    if (vitalsModalDraggingRail && items.isNotEmpty()) {
        val idx = (vitalsModalScrollOffset / rowH.coerceAtLeast(1f)).toInt().coerceIn(0, items.lastIndex)
        val app = items[idx]
        drawVitalsScrubLoupeBubble(
            canvas = canvas,
            listRight = listRight,
            railRect = vitalsFastRailRect,
            dragY = vitalsStorageDragY,
            primaryText = LightspeedCruiseHudPods.formatTrafficSize(app.totalForTransport(st.transportFilter)),
            secondaryLabel = app.label,
            accent = accent,
            dp = dp
        )
    }

    if (vitalsInfoOpen) {
        drawVitalsInfoCard(
            canvas = canvas,
            left = left + padX,
            top = row2Top,
            right = right - padX,
            bottom = listBottom,
            tabKey = "network",
            accent = accent,
            dp = dp
        )
    }
}

internal fun LightspeedCruiseOverlay.handleVitalsNetworkTabTouch(x: Float, y: Float): Boolean {
    val st = vitalsNetworkState
    if (!st.refreshPillRect.isEmpty && st.refreshPillRect.contains(x, y)) {
        triggerHardwareHaptic(18, 90)
        loadVitalsNetworkTabAsync(forceRefresh = true)
        return true
    }
    for ((tKey, rect) in st.transportPillRects) {
        if (rect.contains(x, y)) {
            triggerHardwareHaptic(16, 80)
            st.transportFilter = tKey
            st.applyFilter()
            vitalsModalScroller.forceFinished(true)
            vitalsModalScrollOffset = 0f
            invalidate()
            return true
        }
    }
    for ((sKey, rect) in st.spanPillRects) {
        if (rect.contains(x, y)) {
            triggerHardwareHaptic(16, 80)
            if (st.spanKey != sKey) {
                st.spanKey = sKey
                loadVitalsNetworkTabAsync(forceRefresh = false)
            }
            return true
        }
    }
    if (!st.reversePillRect.isEmpty && st.reversePillRect.contains(x, y)) {
        triggerHardwareHaptic(16, 80)
        st.reversed = !st.reversed
        st.applyFilter()
        vitalsModalScroller.forceFinished(true)
        vitalsModalScrollOffset = 0f
        invalidate()
        return true
    }
    if (!st.permBannerRect.isEmpty && st.permBannerRect.contains(x, y)) {
        triggerHardwareHaptic(20, 100)
        closeVitalsTaskManagerModal()
        dismissOverlay()
        try {
            context.startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            })
        } catch (_: Exception) {}
        return true
    }
    for ((pkg, rect) in st.appRowInfoRects) {
        if (rect.contains(x, y)) {
            triggerHardwareHaptic(20, 100)
            closeVitalsTaskManagerModal()
            dismissOverlay()
            try {
                context.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                    data = Uri.parse("package:$pkg")
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                })
            } catch (_: Exception) {}
            return true
        }
    }
    return false
}
