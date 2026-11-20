package com.sbf.lightspeed

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.view.MotionEvent
import com.sbf.lightspeed.system.LightspeedNotificationListener
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.hypot
import kotlin.math.max

private val WINDOW_OPTIONS = listOf(
    "24H" to 24L * 3_600_000L,
    "2D" to 2L * 24L * 3_600_000L,
    "7D" to 7L * 24L * 3_600_000L,
    "30D" to 30L * 24L * 3_600_000L,
    "1Y" to 365L * 24L * 3_600_000L
)

private val timeFmt = SimpleDateFormat("MM/dd HH:mm", Locale.US)
private val hourFmt = SimpleDateFormat("HH:mm", Locale.US)
private val dayKeyFmt = SimpleDateFormat("yyyyMMdd", Locale.US)

internal fun LightspeedCruiseOverlay.openAlertsHistoryModal(preselectPkg: String? = null) {
    LightspeedNotificationListener.ensureAlertsLoaded(context)
    isAlertsHistoryModalOpen = true
    alertsHistorySelectedPkg = preselectPkg
    alertsHistoryScrollOffset = 0f
    alertsHistoryDraggingRail = false
    invalidate()
}

internal fun LightspeedCruiseOverlay.closeAlertsHistoryModal() {
    isAlertsHistoryModalOpen = false
    alertsHistoryDraggingRail = false
    invalidate()
}

internal fun LightspeedCruiseOverlay.drawAlertsHistoryModal(
    canvas: Canvas,
    density: Float,
    accent: Int
) {
    val dp = resources.displayMetrics.density
    val screenW = width.toFloat()
    val screenH = height.toFloat()

    // Scrim
    highlightPaint.style = Paint.Style.FILL
    highlightPaint.color = Color.argb(175, 0, 0, 0)
    canvas.drawRect(0f, 0f, screenW, screenH, highlightPaint)

    val modalW = (screenW - 24f * dp).coerceAtMost(420f * dp)
    val modalH = (screenH * 0.78f).coerceAtMost(screenH - 48f * dp)
    val left = (screenW - modalW) * 0.5f
    val top = (screenH - modalH) * 0.5f
    alertsHistoryModalRect.set(left, top, left + modalW, top + modalH)

    // Modal surface
    highlightPaint.style = Paint.Style.FILL
    highlightPaint.color = Color.argb(246, 11, 15, 24)
    canvas.drawRoundRect(alertsHistoryModalRect, 14f * dp, 14f * dp, highlightPaint)
    highlightPaint.style = Paint.Style.STROKE
    highlightPaint.strokeWidth = 1.4f * dp
    highlightPaint.color = Color.argb(180, Color.red(accent), Color.green(accent), Color.blue(accent))
    canvas.drawRoundRect(alertsHistoryModalRect, 14f * dp, 14f * dp, highlightPaint)

    val padX = 12f * dp
    val headerCenterY = top + 20f * dp

    // Header + Bell Vector Icon + Close [X]
    LightspeedHudVectorIcons.drawVectorBell(canvas, left + padX + 7f * dp, headerCenterY, 6.5f * dp, accent, 1.4f * dp)
    textPaint.textAlign = Paint.Align.LEFT
    textPaint.typeface = Typeface.DEFAULT_BOLD
    textPaint.textSize = 11f * dp
    textPaint.color = Color.WHITE
    val titleFm = textPaint.fontMetrics
    canvas.drawText(
        "ALERTS & TOAST HISTORY",
        left + padX + 19f * dp,
        headerCenterY - ((titleFm.descent + titleFm.ascent) / 2f),
        textPaint
    )

    val closeSize = 22f * dp
    alertsHistoryCloseRect.set(
        alertsHistoryModalRect.right - closeSize - 10f * dp,
        headerCenterY - closeSize * 0.5f,
        alertsHistoryModalRect.right - 10f * dp,
        headerCenterY + closeSize * 0.5f
    )
    highlightPaint.style = Paint.Style.FILL
    highlightPaint.color = Color.argb(120, 40, 50, 70)
    canvas.drawRoundRect(alertsHistoryCloseRect, 5f * dp, 5f * dp, highlightPaint)
    textPaint.textAlign = Paint.Align.CENTER
    textPaint.textSize = 10.5f * dp
    textPaint.color = Color.WHITE
    val closeFm = textPaint.fontMetrics
    canvas.drawText("X", alertsHistoryCloseRect.centerX(), alertsHistoryCloseRect.centerY() - ((closeFm.descent + closeFm.ascent) / 2f), textPaint)

    // Time-Window Pills: 24H, 2D, 7D, 30D, 1Y
    alertsHistoryWindowPillRects.clear()
    val winTop = top + 38f * dp
    val winH = 21f * dp
    val winGap = 5f * dp
    val winW = (modalW - padX * 2f - winGap * (WINDOW_OPTIONS.size - 1)) / WINDOW_OPTIONS.size
    for ((idx, opt) in WINDOW_OPTIONS.withIndex()) {
        val (key, _) = opt
        val isSel = (alertsHistoryWindowKey == key)
        val pLeft = left + padX + idx * (winW + winGap)
        val pRect = RectF(pLeft, winTop, pLeft + winW, winTop + winH)
        alertsHistoryWindowPillRects[key] = pRect

        highlightPaint.style = Paint.Style.FILL
        highlightPaint.color = if (isSel) Color.argb(190, Color.red(accent), Color.green(accent), Color.blue(accent)) else Color.argb(38, 255, 255, 255)
        canvas.drawRoundRect(pRect, 5f * dp, 5f * dp, highlightPaint)
        highlightPaint.style = Paint.Style.STROKE
        highlightPaint.strokeWidth = 0.9f * dp
        highlightPaint.color = if (isSel) accent else Color.argb(65, 255, 255, 255)
        canvas.drawRoundRect(pRect, 5f * dp, 5f * dp, highlightPaint)

        textPaint.textAlign = Paint.Align.CENTER
        textPaint.typeface = Typeface.DEFAULT_BOLD
        textPaint.textSize = 8.4f * dp
        textPaint.color = if (isSel) Color.BLACK else Color.WHITE
        val winFm = textPaint.fontMetrics
        canvas.drawText(key, pRect.centerX(), pRect.centerY() - ((winFm.descent + winFm.ascent) / 2f), textPaint)
    }

    // Query window alerts (already squashed, plus defensive squash)
    val windowMs = WINDOW_OPTIONS.firstOrNull { it.first == alertsHistoryWindowKey }?.second ?: (24L * 3_600_000L)
    val windowAlerts = LightspeedNotificationListener.getAlertsInWindow(context, windowMs)

    val notifColor = Color.rgb(34, 211, 238) // Cyan for Notifications
    val toastColor = Color.rgb(245, 158, 11) // Amber/Gold for Toasts

    // Top row of App Icons with dual top (Cyan Notif) + bottom (Amber Toast) count badges
    alertsHistoryAppPillRects.clear()
    val appRowTop = winTop + winH + 11f * dp
    val appSlotSize = 32f * dp
    val appGap = 8f * dp
    val pkgCounts = windowAlerts.groupingBy { it.packageName }
        .fold(0) { acc, entry -> acc + entry.count }
        .entries.sortedByDescending { it.value }.take(8)
    var appX = left + padX
    for ((pkg, _) in pkgCounts) {
        if (appX + appSlotSize + 4f * dp > alertsHistoryModalRect.right - padX) break
        val aRect = RectF(appX, appRowTop, appX + appSlotSize, appRowTop + appSlotSize)
        alertsHistoryAppPillRects[pkg] = aRect
        val isSelPkg = (alertsHistorySelectedPkg == pkg)

        highlightPaint.style = Paint.Style.FILL
        highlightPaint.color = if (isSelPkg) Color.argb(80, Color.red(accent), Color.green(accent), Color.blue(accent)) else Color.argb(28, 255, 255, 255)
        canvas.drawRoundRect(aRect, 8f * dp, 8f * dp, highlightPaint)
        highlightPaint.style = Paint.Style.STROKE
        highlightPaint.strokeWidth = if (isSelPkg) 1.5f * dp else 0.8f * dp
        highlightPaint.color = if (isSelPkg) accent else Color.argb(55, 255, 255, 255)
        canvas.drawRoundRect(aRect, 8f * dp, 8f * dp, highlightPaint)

        val icon = applicationIconCache[pkg] ?: try {
            context.packageManager.getApplicationIcon(pkg).also { applicationIconCache[pkg] = it }
        } catch (_: Exception) {
            null
        }
        if (icon != null) {
            val ins = (5f * dp).toInt()
            icon.setBounds((aRect.left + ins).toInt(), (aRect.top + ins).toInt(), (aRect.right - ins).toInt(), (aRect.bottom - ins).toInt())
            icon.draw(canvas)
        } else {
            textPaint.textAlign = Paint.Align.CENTER
            textPaint.typeface = Typeface.DEFAULT_BOLD
            textPaint.textSize = 11f * dp
            textPaint.color = Color.WHITE
            val letterFm = textPaint.fontMetrics
            canvas.drawText(pkg.substringAfterLast('.').take(1).uppercase(), aRect.centerX(), aRect.centerY() - ((letterFm.descent + letterFm.ascent) / 2f), textPaint)
        }

        val notifCount = windowAlerts.filter { it.packageName == pkg && it.kind == "NOTIF" }.sumOf { it.count }
        val toastCount = windowAlerts.filter { it.packageName == pkg && it.kind == "TOAST" }.sumOf { it.count }
        val badgeH = 11.5f * dp

        // Top badge (Cyan): Notifications
        if (notifCount > 0) {
            val nStr = if (notifCount > 99) "99+" else notifCount.toString()
            textPaint.typeface = Typeface.DEFAULT_BOLD
            textPaint.textSize = 6.8f * dp
            val nW = max(13f * dp, textPaint.measureText(nStr) + 6f * dp)
            val nRight = (aRect.right + 3f * dp).coerceAtMost(alertsHistoryModalRect.right - 4f * dp)
            val nRect = RectF(nRight - nW, aRect.top - 4f * dp, nRight, aRect.top - 4f * dp + badgeH)
            highlightPaint.style = Paint.Style.FILL
            highlightPaint.color = notifColor
            canvas.drawRoundRect(nRect, 5.5f * dp, 5.5f * dp, highlightPaint)
            textPaint.textAlign = Paint.Align.CENTER
            textPaint.color = Color.BLACK
            val nFm = textPaint.fontMetrics
            canvas.drawText(nStr, nRect.centerX(), nRect.centerY() - ((nFm.descent + nFm.ascent) / 2f), textPaint)
        }

        // Bottom badge (Amber): Toasts
        if (toastCount > 0) {
            val tStr = if (toastCount > 99) "99+" else toastCount.toString()
            textPaint.typeface = Typeface.DEFAULT_BOLD
            textPaint.textSize = 6.8f * dp
            val tW = max(13f * dp, textPaint.measureText(tStr) + 6f * dp)
            val tRight = (aRect.right + 3f * dp).coerceAtMost(alertsHistoryModalRect.right - 4f * dp)
            val tRect = RectF(tRight - tW, aRect.bottom - badgeH + 4f * dp, tRight, aRect.bottom + 4f * dp)
            highlightPaint.style = Paint.Style.FILL
            highlightPaint.color = toastColor
            canvas.drawRoundRect(tRect, 5.5f * dp, 5.5f * dp, highlightPaint)
            textPaint.textAlign = Paint.Align.CENTER
            textPaint.color = Color.BLACK
            val tFm = textPaint.fontMetrics
            canvas.drawText(tStr, tRect.centerX(), tRect.centerY() - ((tFm.descent + tFm.ascent) / 2f), textPaint)
        }

        appX += appSlotSize + appGap
    }

    val filtered = if (alertsHistorySelectedPkg != null) {
        windowAlerts.filter { it.packageName == alertsHistorySelectedPkg }
    } else {
        windowAlerts
    }
    val notifs = filtered.filter { it.kind == "NOTIF" }
    val toasts = filtered.filter { it.kind == "TOAST" }

    // Dual-Column Headers: NOTIFICATIONS (left, Cyan) | TOASTS (right, Amber)
    val railW = 14f * dp
    val listLeft = left + padX
    val listRight = alertsHistoryModalRect.right - padX - railW - 4f * dp
    val colGap = 8f * dp
    val colW = (listRight - listLeft - colGap) * 0.5f
    val col1Left = listLeft
    val col2Left = listLeft + colW + colGap
    val colHdrY = appRowTop + (if (pkgCounts.isNotEmpty()) appSlotSize + 17f * dp else 8f * dp)

    textPaint.textAlign = Paint.Align.LEFT
    textPaint.typeface = Typeface.DEFAULT_BOLD
    textPaint.textSize = 8.4f * dp
    textPaint.color = notifColor
    canvas.drawText("NOTIFICATIONS (${notifs.size})", col1Left, colHdrY, textPaint)
    textPaint.color = toastColor
    canvas.drawText("TOASTS (${toasts.size})", col2Left, colHdrY, textPaint)

    val listTop = colHdrY + 6f * dp
    val listBottom = alertsHistoryModalRect.bottom - 12f * dp
    val listH = (listBottom - listTop).coerceAtLeast(40f * dp)

    val rowH = 42f * dp
    val maxRows = max(notifs.size, toasts.size)
    val totalContentH = maxRows * rowH
    alertsHistoryMaxScroll = max(0f, totalContentH - listH)
    alertsHistoryScrollOffset = alertsHistoryScrollOffset.coerceIn(0f, alertsHistoryMaxScroll)

    // Fast-Scroller Rail on right edge
    alertsHistoryFastRailRect.set(listRight + 4f * dp, listTop, alertsHistoryModalRect.right - padX, listBottom)
    highlightPaint.style = Paint.Style.FILL
    highlightPaint.color = Color.argb(35, 255, 255, 255)
    canvas.drawRoundRect(alertsHistoryFastRailRect, 4f * dp, 4f * dp, highlightPaint)
    if (alertsHistoryMaxScroll > 0f) {
        val thumbH = (listH * (listH / totalContentH)).coerceIn(22f * dp, listH * 0.6f)
        val thumbFrac = (alertsHistoryScrollOffset / alertsHistoryMaxScroll).coerceIn(0f, 1f)
        val thumbTop = listTop + thumbFrac * (listH - thumbH)
        val thumbRect = RectF(alertsHistoryFastRailRect.left + 1.5f * dp, thumbTop, alertsHistoryFastRailRect.right - 1.5f * dp, thumbTop + thumbH)
        highlightPaint.color = accent
        canvas.drawRoundRect(thumbRect, 3f * dp, 3f * dp, highlightPaint)
    }

    // Center divider between columns
    highlightPaint.style = Paint.Style.STROKE
    highlightPaint.strokeWidth = 0.8f * dp
    highlightPaint.color = Color.argb(55, 255, 255, 255)
    val midDivX = col1Left + colW + colGap * 0.5f
    canvas.drawLine(midDivX, listTop, midDivX, listBottom, highlightPaint)

    // Scrollable dual-column list
    val listLayerCount = canvas.saveLayer(listLeft, listTop, listRight, listBottom, null)
    canvas.clipRect(listLeft, listTop, listRight, listBottom)
    canvas.save()
    canvas.translate(0f, -alertsHistoryScrollOffset)

    fun drawColumnEntries(entries: List<LightspeedNotificationListener.RecentAlertEntry>, cLeft: Float, kindColor: Int) {
        if (entries.isEmpty()) {
            textPaint.textAlign = Paint.Align.CENTER
            textPaint.typeface = Typeface.DEFAULT
            textPaint.textSize = 8.4f * dp
            textPaint.color = Color.argb(130, 180, 195, 215)
            canvas.drawText("NONE", cLeft + colW * 0.5f, listTop + 26f * dp, textPaint)
            return
        }
        for ((idx, entry) in entries.withIndex()) {
            val rTop = listTop + idx * rowH
            val rBot = rTop + rowH - 4f * dp
            if (rBot < listTop + alertsHistoryScrollOffset - 20f || rTop > listBottom + alertsHistoryScrollOffset + 20f) continue

            val cardRect = RectF(cLeft, rTop, cLeft + colW, rBot)
            highlightPaint.style = Paint.Style.FILL
            highlightPaint.color = Color.argb(24, Color.red(kindColor), Color.green(kindColor), Color.blue(kindColor))
            canvas.drawRoundRect(cardRect, 5f * dp, 5f * dp, highlightPaint)
            // Left color accent bar
            highlightPaint.color = kindColor
            canvas.drawRoundRect(RectF(cLeft, rTop + 3f * dp, cLeft + 2.5f * dp, rBot - 3f * dp), 1.5f * dp, 1.5f * dp, highlightPaint)

            val shortPkg = entry.packageName.substringAfterLast('.').uppercase()
            val timeStr = if (entry.count > 1 && entry.firstTimestampMs < entry.timestampMs) {
                val firstDate = Date(entry.firstTimestampMs)
                val lastDate = Date(entry.timestampMs)
                val sameDay = dayKeyFmt.format(firstDate) == dayKeyFmt.format(lastDate)
                val firstStr = timeFmt.format(firstDate)
                val lastStr = if (sameDay) hourFmt.format(lastDate) else timeFmt.format(lastDate)
                "$firstStr→$lastStr"
            } else {
                timeFmt.format(Date(entry.timestampMs))
            }

            // Package name (left) + ×N repeat badge (if count > 1)
            textPaint.textAlign = Paint.Align.LEFT
            textPaint.typeface = Typeface.DEFAULT_BOLD
            textPaint.textSize = 7.4f * dp
            textPaint.color = kindColor
            val pkgLabel = shortPkg.take(if (entry.count > 1) 8 else 11)
            canvas.drawText(pkgLabel, cLeft + 6f * dp, rTop + 11f * dp, textPaint)

            if (entry.count > 1) {
                val pkgW = textPaint.measureText(pkgLabel)
                val countStr = "×${entry.count}"
                textPaint.textSize = 6.6f * dp
                val cW = max(16f * dp, textPaint.measureText(countStr) + 6f * dp)
                val cLeftX = cLeft + 8f * dp + pkgW
                val cRect = RectF(cLeftX, rTop + 3.5f * dp, cLeftX + cW, rTop + 13.5f * dp)
                highlightPaint.style = Paint.Style.FILL
                highlightPaint.color = kindColor
                canvas.drawRoundRect(cRect, 4f * dp, 4f * dp, highlightPaint)
                textPaint.textAlign = Paint.Align.CENTER
                textPaint.color = Color.BLACK
                val cFm = textPaint.fontMetrics
                canvas.drawText(countStr, cRect.centerX(), cRect.centerY() - ((cFm.descent + cFm.ascent) / 2f), textPaint)
            }

            // Time or firstTime → lastTime on second line right, or top-right if single
            if (entry.count > 1 && entry.firstTimestampMs < entry.timestampMs) {
                textPaint.textAlign = Paint.Align.LEFT
                textPaint.typeface = Typeface.MONOSPACE
                textPaint.textSize = 6.3f * dp
                textPaint.color = Color.argb(185, 190, 205, 225)
                canvas.drawText(timeStr, cLeft + 6f * dp, rTop + 21f * dp, textPaint)

                textPaint.textAlign = Paint.Align.LEFT
                textPaint.typeface = Typeface.DEFAULT
                textPaint.textSize = 7.6f * dp
                textPaint.color = Color.argb(235, 235, 242, 255)
                val maxChars = ((colW - 11f * dp) / (4.3f * dp)).toInt().coerceAtLeast(10)
                val msg = if (entry.summary.length > maxChars) entry.summary.take(maxChars - 2) + ".." else entry.summary
                canvas.drawText(msg, cLeft + 6f * dp, rTop + 32f * dp, textPaint)
            } else {
                textPaint.textAlign = Paint.Align.RIGHT
                textPaint.typeface = Typeface.MONOSPACE
                textPaint.textSize = 6.6f * dp
                textPaint.color = Color.argb(170, 190, 205, 225)
                canvas.drawText(timeStr, cLeft + colW - 5f * dp, rTop + 11f * dp, textPaint)

                textPaint.textAlign = Paint.Align.LEFT
                textPaint.typeface = Typeface.DEFAULT
                textPaint.textSize = 7.8f * dp
                textPaint.color = Color.argb(235, 235, 242, 255)
                val maxChars = ((colW - 11f * dp) / (4.4f * dp)).toInt().coerceAtLeast(10)
                val msg = if (entry.summary.length > maxChars) entry.summary.take(maxChars - 2) + ".." else entry.summary
                canvas.drawText(msg, cLeft + 6f * dp, rTop + 26f * dp, textPaint)
            }
        }
    }

    drawColumnEntries(notifs, col1Left, notifColor)
    drawColumnEntries(toasts, col2Left, toastColor)
    canvas.restore()
    LightspeedHudVectorIcons.applyVerticalEdgeFeathering(
        canvas = canvas,
        left = listLeft,
        top = listTop,
        right = listRight,
        bottom = listBottom,
        featherTopPx = if (alertsHistoryScrollOffset > 0.5f) 16f * dp else 0f,
        featherBottomPx = if (alertsHistoryScrollOffset < alertsHistoryMaxScroll - 0.5f) 18f * dp else 0f,
        density = dp,
        seed = 8404
    )
    canvas.restoreToCount(listLayerCount)
}

internal fun LightspeedCruiseOverlay.handleAlertsHistoryModalTouch(event: MotionEvent): Boolean {
    val x = event.x
    val y = event.y
    when (event.actionMasked) {
        MotionEvent.ACTION_DOWN -> {
            touchDownX = x; touchDownY = y; lastTouchY = y
            val expandedRail = RectF(alertsHistoryFastRailRect).apply { inset(-10f * resources.displayMetrics.density, 0f) }
            if (expandedRail.contains(x, y) && alertsHistoryMaxScroll > 0f) {
                alertsHistoryDraggingRail = true
                val frac = ((y - alertsHistoryFastRailRect.top) / alertsHistoryFastRailRect.height().coerceAtLeast(1f)).coerceIn(0f, 1f)
                alertsHistoryScrollOffset = frac * alertsHistoryMaxScroll
                invalidate()
            }
            return true
        }
        MotionEvent.ACTION_MOVE -> {
            if (alertsHistoryDraggingRail && alertsHistoryMaxScroll > 0f) {
                val frac = ((y - alertsHistoryFastRailRect.top) / alertsHistoryFastRailRect.height().coerceAtLeast(1f)).coerceIn(0f, 1f)
                alertsHistoryScrollOffset = frac * alertsHistoryMaxScroll
                invalidate()
                return true
            }
            val dy = y - lastTouchY
            lastTouchY = y
            if (alertsHistoryMaxScroll > 0f) {
                alertsHistoryScrollOffset = (alertsHistoryScrollOffset - dy).coerceIn(0f, alertsHistoryMaxScroll)
                invalidate()
            }
            return true
        }
        MotionEvent.ACTION_UP -> {
            if (alertsHistoryDraggingRail) {
                alertsHistoryDraggingRail = false
                invalidate()
                return true
            }
            val isTap = hypot((x - touchDownX).toDouble(), (y - touchDownY).toDouble()) < 18f
            if (isTap) {
                if (!alertsHistoryModalRect.contains(x, y) || alertsHistoryCloseRect.contains(x, y)) {
                    triggerHardwareHaptic(18, 80)
                    closeAlertsHistoryModal()
                    return true
                }
                for ((key, rect) in alertsHistoryWindowPillRects) {
                    if (rect.contains(x, y)) {
                        triggerHardwareHaptic(16, 80)
                        alertsHistoryWindowKey = key
                        alertsHistoryScrollOffset = 0f
                        invalidate()
                        return true
                    }
                }
                for ((pkg, rect) in alertsHistoryAppPillRects) {
                    if (rect.contains(x, y)) {
                        triggerHardwareHaptic(16, 80)
                        alertsHistorySelectedPkg = if (alertsHistorySelectedPkg == pkg) null else pkg
                        alertsHistoryScrollOffset = 0f
                        invalidate()
                        return true
                    }
                }
            }
            return true
        }
        MotionEvent.ACTION_CANCEL -> {
            alertsHistoryDraggingRail = false
            return true
        }
    }
    return true
}
