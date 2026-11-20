package com.sbf.lightspeed

import com.sbf.lightspeed.system.lsString

import com.sbf.lightspeed.R

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import com.sbf.lightspeed.system.LightspeedUsageStats
import java.text.NumberFormat
import java.util.Locale
import kotlin.math.roundToInt

private val vitalsDrawTempRect = RectF()

/** Cruise HUD Battery/System vitals widget renderer (Screen Time extracted to LightspeedCruiseHudDrawScreenTime.kt). */
internal fun LightspeedCruiseOverlay.drawBatterySystemHudWidget(
    canvas: Canvas,
    bounds: RectF,
    catId: String,
    accent: Int,
    density: Float,
    alpha: Float,
    slotIndex: Int = 1
) {
    val bMode = LightspeedCruiseHudPods.getBatteryMode(context, catId, slotIndex)
    val bSpan = LightspeedCruiseHudPods.getBatterySpan(context, catId, slotIndex)
    val bModeLabel = LightspeedCruiseHudPods.BATTERY_MODE_OPTIONS.firstOrNull { it.first == bMode }?.second ?: "BATTERY"
    val showSpan = (bMode == "apps" || bMode == "system")
    val effectiveSpanKey = if (bMode == "system") {
        if (bSpan == "today") "today" else "7d"
    } else {
        bSpan
    }
    val isSpanPartial = showSpan && LightspeedCruiseHudPods.isSpanPartial(effectiveSpanKey)
    val bSpanLabel = when {
        !showSpan -> null
        bMode == "system" -> if (bSpan == "today") "TODAY" else "7-DAY"
        else -> LightspeedCruiseHudPods.BATTERY_SPAN_OPTIONS.firstOrNull { it.first == bSpan }?.second ?: "7-DAY"
    }
    val snap = LightspeedCruiseHudPods.latestBatterySnapshot
    val snapReady = LightspeedCruiseHudPods.hasBatterySnapshot

    // Top Header with Main Widget Caption
    drawWidgetChipHeader(
        canvas = canvas,
        bounds = bounds,
        widgetTitle = com.sbf.lightspeed.system.lsString(R.string.hud_system_vitals).ifBlank { "SYSTEM VITALS" },
        primaryChip = lsString(R.string.hud_view_chip, bModeLabel),
        secondaryChip = if (bSpanLabel != null) {
            if (isSpanPartial) "${lsString(R.string.hud_span_chip, bSpanLabel)} !" else lsString(R.string.hud_span_chip, bSpanLabel)
        } else null,
        accent = accent,
        density = density,
        alpha = alpha,
        showInfoBadge = showSpan,
        infoBadgeWarn = isSpanPartial,
        slotIndex = slotIndex
    )

    val vitalsTempGaugeRect = vitalsRectForSlot(vitalsTempGaugeRects, slotIndex)
    val vitalsRamGaugeRect = vitalsRectForSlot(vitalsRamGaugeRects, slotIndex)
    val vitalsSwapGaugeRect = vitalsRectForSlot(vitalsSwapGaugeRects, slotIndex)
    val vitalsIntStorageBarRect = vitalsRectForSlot(vitalsIntStorageBarRects, slotIndex)
    val vitalsExtStorageBarRect = vitalsRectForSlot(vitalsExtStorageBarRects, slotIndex)

    if (bMode != "vitals" && bMode != "chart") {
        vitalsTempGaugeRect.setEmpty()
    }
    if (bMode != "vitals") {
        vitalsRamGaugeRect.setEmpty()
        vitalsSwapGaugeRect.setEmpty()
        vitalsIntStorageBarRect.setEmpty()
        vitalsExtStorageBarRect.setEmpty()
    }

    when (bMode) {
        "chart" -> {
            // Spacecraft-grade battery telemetry + capacity + health/cycles + peak/min charge + 2x2 grid
            val leftColX = bounds.left + 13f * density
            val rightRight = bounds.right - 13f * density
            val batColor = when {
                snap.levelPct <= 15 -> Color.argb((255 * alpha).toInt(), 239, 68, 68)
                snap.levelPct <= 30 -> Color.argb((255 * alpha).toInt(), 245, 158, 11)
                else -> Color.argb((255 * alpha).toInt(), 34, 197, 94)
            }
            val tempStr = LightspeedCruiseHudPods.formatTemp(context, snap.tempC)
            val intFmt = NumberFormat.getIntegerInstance(Locale.US)
            val capStr = when {
                snap.chargeMah > 0 && snap.designCapacityMah > 0 ->
                    "${intFmt.format(snap.chargeMah)} / ${intFmt.format(snap.designCapacityMah)} mAh"
                snap.designCapacityMah > 0 ->
                    "-- / ${intFmt.format(snap.designCapacityMah)} mAh"
                snap.chargeMah > 0 ->
                    "${intFmt.format(snap.chargeMah)} mAh"
                else -> "CAPACITY N/A"
            }

            // Top Left: Battery Icon + Label + Hero % + mAh Capacity
            drawCockpitVectorIcon(canvas, if (snap.isCharging) "BOLT" else "BATTERY", leftColX + 5f * density, bounds.top + 36.5f * density, 9f * density, accent, density)
            textPaint.textAlign = Paint.Align.LEFT
            textPaint.typeface = overlayTypeface(isBold = true, fallback = cockpitMonoBold)
            textPaint.textSize = 9.4f * density
            textPaint.color = accent
            val batStateLbl = if (snap.isCharging) {
                com.sbf.lightspeed.system.lsString(R.string.hud_bat_charging).ifBlank { "CHARGING" }
            } else {
                com.sbf.lightspeed.system.lsString(R.string.hud_bat_cell).ifBlank { "CELL CHARGE" }
            }
            canvas.drawText(batStateLbl, leftColX + 14f * density, bounds.top + 40f * density, textPaint)

            textPaint.typeface = cockpitMonoBold
            textPaint.textSize = 22f * density
            textPaint.color = batColor
            val batPctText = if (snapReady) LightspeedCruiseHudPods.flipDigitsIfActive("${snap.levelPct}%", seedOffset = 2) else "--%"
            canvas.drawText(batPctText, leftColX, bounds.top + 62f * density, textPaint)

            val rightLeft = bounds.left + (bounds.width() * 0.46f)
            val leftColMaxW = (rightLeft - leftColX - 8f * density).coerceAtLeast(36f * density)
            val rightColMaxW = (rightRight - rightLeft - 4f * density).coerceAtLeast(40f * density)

            textPaint.typeface = cockpitMonoBold
            textPaint.textSize = 9.2f * density
            textPaint.color = Color.WHITE
            canvas.drawText(
                clipTextToWidth(LightspeedCruiseHudPods.flipDigitsIfActive(capStr, seedOffset = 3), leftColMaxW),
                leftColX,
                bounds.top + 76f * density,
                textPaint
            )

            // Top Right: Segmented Charge Bar + Health/Cycle Count + Live Wattage/Voltage
            val healthCycleStr = if (snap.cycleCount > 0) {
                "${snap.healthLabel} · ${LightspeedCruiseHudPods.flipDigitsIfActive("${snap.cycleCount}", seedOffset = 4)} CYC"
            } else {
                lsString(R.string.hud_health_prefix, if (snap.healthLabel.equals("GOOD", ignoreCase = true)) lsString(R.string.hud_health_good) else snap.healthLabel)
            }
            drawCockpitVectorIcon(canvas, "HEART", rightLeft + 5f * density, bounds.top + 36.5f * density, 8.5f * density, batColor, density)
            textPaint.textAlign = Paint.Align.LEFT
            textPaint.typeface = cockpitMonoBold
            textPaint.textSize = 9.2f * density
            textPaint.color = Color.WHITE
            canvas.drawText(
                clipTextToWidth(healthCycleStr, (rightColMaxW - 14f * density).coerceAtLeast(24f * density)),
                rightLeft + 14f * density,
                bounds.top + 40f * density,
                textPaint
            )

            val barTop = bounds.top + 45f * density
            val barBot = barTop + 8f * density
            drawCockpitSegmentedBar(
                canvas = canvas,
                left = rightLeft,
                top = barTop,
                right = rightRight,
                bottom = barBot,
                ratio = (snap.levelPct / 100f).coerceIn(0.02f, 1f),
                segments = 12,
                fillColor = batColor,
                bgColor = Color.argb(40, 255, 255, 255),
                density = density
            )

            textPaint.textAlign = Paint.Align.LEFT
            textPaint.typeface = cockpitMonoBold
            textPaint.textSize = 9.2f * density
            textPaint.color = Color.argb(230, 215, 232, 255)
            val liveWattVoltStr = LightspeedCruiseHudPods.flipDigitsIfActive(
                "${snap.wattageStr} · ${intFmt.format(snap.voltageMv)}mV",
                seedOffset = 5
            )
            canvas.drawText(clipTextToWidth(liveWattVoltStr, rightColMaxW), rightLeft, bounds.top + 65.5f * density, textPaint)

            textPaint.typeface = Typeface.MONOSPACE
            textPaint.textSize = 8.6f * density
            textPaint.color = Color.argb(200, 195, 215, 240)
            val avg7dStr = LightspeedCruiseHudPods.flipDigitsIfActive(
                String.format(Locale.US, "%.1fh/D", snap.sevenDayDailyAvgHrs),
                seedOffset = 6
            )
            canvas.drawText(clipTextToWidth(lsString(R.string.hud_7d_avg, avg7dStr), rightColMaxW), rightLeft, bounds.top + 77.5f * density, textPaint)

            // 2x2 Real Battery Telemetry Grid in lower card space
            val gridTop = bounds.top + 84f * density
            val gridBot = bounds.bottom - 8f * density
            if (gridBot - gridTop >= 36f * density) {
                val cellW = ((rightRight - leftColX) - 7f * density) / 2f
                val cellH = ((gridBot - gridTop) - 5f * density) / 2f
                val thermalState = vitalsTempStateLabel(snap.tempC)
                val compactThermalLabel = when (thermalState) {
                    "NORMAL" -> lsString(R.string.hud_temp_norm)
                    "OPTIMAL" -> lsString(R.string.hud_temp_opt)
                    else -> "TEMP ($thermalState)"
                }
                val gridCells = listOf(
                    Triple(lsString(R.string.hud_peak_min), LightspeedCruiseHudPods.flipDigitsIfActive(snap.chgPeakMinStr, seedOffset = 7), false),
                    Triple(lsString(R.string.hud_draw), LightspeedCruiseHudPods.flipDigitsIfActive("${snap.wattageStr}·${snap.voltageMv}mV", seedOffset = 8), false),
                    Triple(compactThermalLabel, LightspeedCruiseHudPods.flipDigitsIfActive(tempStr, seedOffset = 9), true),
                    Triple(lsString(R.string.hud_est_left), LightspeedCruiseHudPods.flipDigitsIfActive(snap.estRemainingStr, seedOffset = 10), false)
                )
                vitalsTempGaugeRect.setEmpty()
                for ((idx, cell) in gridCells.withIndex()) {
                    val col = idx % 2
                    val row = idx / 2
                    val cLeft = leftColX + col * (cellW + 7f * density)
                    val cTop = gridTop + row * (cellH + 5f * density)
                    vitalsDrawTempRect.set(cLeft, cTop, cLeft + cellW, cTop + cellH)
                    if (cell.third) vitalsTempGaugeRect.set(vitalsDrawTempRect)

                    highlightPaint.style = Paint.Style.FILL
                    highlightPaint.color = Color.argb((34 * alpha).toInt(), 255, 255, 255)
                    canvas.drawRoundRect(vitalsDrawTempRect, 6f * density, 6f * density, highlightPaint)
                    highlightPaint.style = Paint.Style.STROKE
                    highlightPaint.strokeWidth = 0.9f * density
                    highlightPaint.color = Color.argb((75 * alpha).toInt(), Color.red(accent), Color.green(accent), Color.blue(accent))
                    canvas.drawRoundRect(vitalsDrawTempRect, 6f * density, 6f * density, highlightPaint)

                    // Single centered row: compact label on left, bold telemetry readout on right (zero vertical overlap)
                    val centerBaselineY = vitalsDrawTempRect.centerY() + 3.1f * density
                    textPaint.textAlign = Paint.Align.LEFT
                    textPaint.typeface = overlayTypeface(isBold = true, fallback = cockpitMonoBold)
                    textPaint.textSize = 7.4f * density
                    textPaint.color = accent
                    canvas.drawText(cell.first, cLeft + 6f * density, centerBaselineY, textPaint)

                    textPaint.textAlign = Paint.Align.RIGHT
                    textPaint.typeface = cockpitMonoBold
                    textPaint.textSize = 8.6f * density
                    textPaint.color = Color.WHITE
                    canvas.drawText(cell.second, vitalsDrawTempRect.right - 6f * density, centerBaselineY, textPaint)
                }
            } else {
                vitalsTempGaugeRect.setEmpty()
                textPaint.textAlign = Paint.Align.CENTER
                textPaint.typeface = overlayTypeface(isBold = true, fallback = cockpitMonoBold)
                textPaint.textSize = 9.0f * density
                textPaint.color = Color.argb(195, 200, 220, 245)
                canvas.drawText(com.sbf.lightspeed.system.lsString(R.string.hud_bat_live_footer).ifBlank { "LIVE HARDWARE BATTERY TELEMETRY" }, bounds.centerX(), bounds.bottom - 8f * density, textPaint)
            }
        }
        "apps" -> {
            // Spacecraft Power Consumption Ranking by Apps
            val mLeft = bounds.left + 13f * density
            val mRight = bounds.right - 13f * density

            drawCockpitVectorIcon(canvas, "APPS", mLeft + 5f * density, bounds.top + 36.5f * density, 8.5f * density, accent, density)
            textPaint.textAlign = Paint.Align.LEFT
            textPaint.typeface = overlayTypeface(isBold = true, fallback = cockpitMonoBold)
            textPaint.textSize = 9.4f * density
            textPaint.color = accent
            canvas.drawText(com.sbf.lightspeed.system.lsString(R.string.hud_bat_consumers).ifBlank { "TOP POWER CONSUMERS (% DRAIN)" }, mLeft + 14f * density, bounds.top + 40f * density, textPaint)

            val showUnplugFallbackNote = (bSpan == "last_charge" && !LightspeedCruiseHudPods.hasObservedRealUnplug)
            if (showUnplugFallbackNote) {
                textPaint.textAlign = Paint.Align.RIGHT
                textPaint.typeface = Typeface.MONOSPACE
                textPaint.textSize = 7.4f * density
                textPaint.color = Color.rgb(245, 158, 11)
                canvas.drawText(com.sbf.lightspeed.system.lsString(R.string.hud_bat_since_boot).ifBlank { "Since boot (until next unplug)" }, mRight, bounds.top + 40f * density, textPaint)
            }

            val listTop = bounds.top + 47f * density
            val listBottom = bounds.bottom - 8f * density
            val listH = (listBottom - listTop).coerceAtLeast(20f * density)
            val rowStep = 21.5f * density

            val usageKey = when (bSpan) {
                "today" -> "1d"
                "last_charge" -> "last_charge"
                else -> "7d"
            }
            val usageMap = LightspeedCruiseHudPods.getUsageMap(usageKey)
            val totalUsage = usageMap.values.sum().coerceAtLeast(1L)
            val allPowerApps = dataBridge.getAllApps()
                .map { it to (usageMap[it.packageName] ?: 0L) }
                .filter { it.second > 0L }
                .sortedByDescending { it.second }

            val totalListH = allPowerApps.size * rowStep
            val batteryAppsMaxScroll = kotlin.math.max(0f, totalListH - listH)
            val batteryAppsScrollOffset = getBatteryAppsScrollOffset(slotIndex).coerceIn(0f, batteryAppsMaxScroll)
            setBatteryAppsMaxScroll(slotIndex, batteryAppsMaxScroll)
            setBatteryAppsScrollOffset(slotIndex, batteryAppsScrollOffset)

            if (allPowerApps.isEmpty()) {
                textPaint.textAlign = Paint.Align.LEFT
                textPaint.typeface = overlayTypeface(isBold = true, fallback = cockpitMonoBold)
                textPaint.textSize = 10.2f * density
                textPaint.color = Color.argb(150, 200, 220, 245)
                canvas.drawText(com.sbf.lightspeed.system.lsString(R.string.hud_bat_no_usage).ifBlank { "NO POWER USAGE LOGGED" }, mLeft, listTop + 16f * density, textPaint)
            } else {
                val batLayerCount = canvas.saveLayer(mLeft - 2f * density, bounds.top + 45f * density, mRight + 2f * density, listBottom, null)
                canvas.clipRect(mLeft - 2f * density, bounds.top + 45f * density, mRight + 2f * density, listBottom)
                canvas.save()
                canvas.translate(0f, -batteryAppsScrollOffset)

                var rY = listTop + 11.5f * density
                var pIdx = 0
                for ((appItem, ms) in allPowerApps) {
                    val pct = ((ms * 100f) / totalUsage).roundToInt().coerceIn(1, 99)
                    val durStr = LightspeedCruiseHudPods.flipDigitsIfActive(
                        LightspeedCruiseHudPods.formatDurationCompact(ms),
                        seedOffset = 30 + pIdx
                    )
                    val pctStr = LightspeedCruiseHudPods.flipDigitsIfActive("$pct%", seedOffset = 50 + pIdx)
                    pIdx++

                    textPaint.textAlign = Paint.Align.RIGHT
                    textPaint.typeface = cockpitMonoBold
                    textPaint.textSize = 11.4f * density
                    textPaint.color = accent
                    val pctW = textPaint.measureText(pctStr)
                    canvas.drawText(pctStr, mRight, rY, textPaint)

                    val rightLabel = " ($durStr)"
                    textPaint.textAlign = Paint.Align.LEFT
                    textPaint.typeface = overlayTypeface(isBold = true, fallback = cockpitMonoBold)
                    textPaint.textSize = 11.2f * density
                    textPaint.color = Color.WHITE
                    val availTitleW = (mRight - pctW - mLeft - 8f * density).coerceAtLeast(36f * density)
                    val rightLabelW = textPaint.measureText(rightLabel)
                    val clippedName = clipTextToWidth(appItem.label, (availTitleW - rightLabelW).coerceAtLeast(24f * density))
                    canvas.drawText("$clippedName$rightLabel", mLeft, rY, textPaint)

                    val bTop = rY + 3.5f * density
                    drawCockpitSegmentedBar(
                        canvas = canvas,
                        left = mLeft,
                        top = bTop,
                        right = mRight,
                        bottom = bTop + 4.5f * density,
                        ratio = pct / 100f,
                        segments = 14,
                        fillColor = accent,
                        bgColor = Color.argb(40, 255, 255, 255),
                        density = density
                    )
                    rY += rowStep
                }
                canvas.restore()
                LightspeedHudVectorIcons.applyVerticalEdgeFeathering(
                    canvas = canvas,
                    left = mLeft - 2f * density,
                    top = bounds.top + 45f * density,
                    right = mRight + 2f * density,
                    bottom = listBottom,
                    featherTopPx = if (batteryAppsScrollOffset > 0.5f) 14f * density else 0f,
                    featherBottomPx = if (batteryAppsScrollOffset < batteryAppsMaxScroll - 0.5f) 16f * density else 0f,
                    density = density,
                    seed = 7272
                )
                canvas.restoreToCount(batLayerCount)

                if (batteryAppsMaxScroll > 0f) {
                    val sbW = 2.5f * density
                    val sbX = mRight + 5f * density
                    val sbRatio = (listH / totalListH).coerceIn(0.15f, 0.8f)
                    val thumbH = listH * sbRatio
                    val thumbY = listTop + ((batteryAppsScrollOffset / batteryAppsMaxScroll) * (listH - thumbH))
                    highlightPaint.style = Paint.Style.FILL
                    highlightPaint.color = Color.argb(60, 255, 255, 255)
                    vitalsDrawTempRect.set(sbX, listTop, sbX + sbW, listBottom)
                    canvas.drawRoundRect(vitalsDrawTempRect, 1.5f * density, 1.5f * density, highlightPaint)
                    highlightPaint.color = accent
                    vitalsDrawTempRect.set(sbX, thumbY, sbX + sbW, thumbY + thumbH)
                    canvas.drawRoundRect(vitalsDrawTempRect, 1.5f * density, 1.5f * density, highlightPaint)
                }
            }
        }
        "system" -> {
            // Dual-segment cockpit bars: CPU Awake vs Deep Sleep (boot), Screen On (Span), Cellular & Wi-Fi (since reboot)
            val mLeft = bounds.left + 13f * density
            val mRight = bounds.right - 13f * density

            val sleepPct = (100 - snap.awakePct).coerceIn(0, 100)
            val hasUsage = LightspeedUsageStats.hasUsageAccess(context)
            val isTodaySpan = (bSpan == "today")
            val scrnMs = if (isTodaySpan) snap.screenTodayMs else snap.screenSevenDayMs
            val scrnMaxRefMs = if (isTodaySpan) 12f * 3_600_000f else 84f * 3_600_000f
            val scrnTitle = if (isTodaySpan) "SCREEN ON (TODAY)" else "SCREEN ON (7-DAY)"
            val scrnStr = if (hasUsage) LightspeedCruiseHudPods.formatDurationCompact(scrnMs) else "NO ACCESS"
            val scrnRatio = if (hasUsage) (scrnMs.toFloat() / scrnMaxRefMs).coerceIn(0.03f, 1f) else 0f

            val mobDown = snap.mobileRxBytes
            val mobUp = snap.mobileTxBytes
            val mobTot = ((if (mobDown > 0L) mobDown else 0L) + (if (mobUp > 0L) mobUp else 0L)).coerceAtLeast(1L)
            val mobValStr = if (snap.mobileBytes >= 0L) {
                "↓${LightspeedCruiseHudPods.formatStorageSize(mobDown.coerceAtLeast(0L))} + ↑${LightspeedCruiseHudPods.formatStorageSize(mobUp.coerceAtLeast(0L))}"
            } else "N/A"
            val mobDownRatio = if (snap.mobileBytes > 0L) (mobDown.toFloat() / mobTot).coerceIn(0.04f, 0.96f) else 0f
            val mobUpRatio = if (snap.mobileBytes > 0L) (mobUp.toFloat() / mobTot).coerceIn(0.04f, 0.96f) else 0f

            val wifiDown = snap.wifiRxBytes
            val wifiUp = snap.wifiTxBytes
            val wifiTot = ((if (wifiDown > 0L) wifiDown else 0L) + (if (wifiUp > 0L) wifiUp else 0L)).coerceAtLeast(1L)
            val wifiValStr = if (snap.wifiBytes >= 0L) {
                "↓${LightspeedCruiseHudPods.formatStorageSize(wifiDown.coerceAtLeast(0L))} + ↑${LightspeedCruiseHudPods.formatStorageSize(wifiUp.coerceAtLeast(0L))}"
            } else "N/A"
            val wifiDownRatio = if (snap.wifiBytes > 0L) (wifiDown.toFloat() / wifiTot).coerceIn(0.04f, 0.96f) else 0f
            val wifiUpRatio = if (snap.wifiBytes > 0L) (wifiUp.toFloat() / wifiTot).coerceIn(0.04f, 0.96f) else 0f

            val cyanColor = Color.rgb(34, 211, 238)
            val emeraldColor = Color.rgb(34, 197, 94)
            val amberColor = Color.rgb(245, 158, 11)

            val availH = (bounds.bottom - 8f * density) - (bounds.top + 38f * density)
            val rowStep = (availH / 4f).coerceIn(19f * density, 26f * density)
            var rY = bounds.top + 43f * density

            // Row 1: CPU AWAKE vs DEEP SLEEP (Tap to open Wakelock & Rogue CPU Hunter)
            vitalsTempGaugeRect.set(mLeft, rY - 10f * density, mRight, rY + 11f * density)
            drawCockpitVectorIcon(canvas, "CPU", mLeft + 5f * density, rY - 3.5f * density, 8.5f * density, accent, density)
            textPaint.textAlign = Paint.Align.LEFT
            textPaint.typeface = overlayTypeface(isBold = true, fallback = cockpitMonoBold)
            textPaint.textSize = 9.0f * density
            textPaint.color = Color.WHITE
            val awakeLabel = "CPU AWAKE vs SLEEP [BOOT · HUNT]"
            val awakeTextX = mLeft + 14f * density
            canvas.drawText(awakeLabel, awakeTextX, rY, textPaint)
            LightspeedHudVectorIcons.drawVectorArrow(
                canvas = canvas,
                cx = awakeTextX + textPaint.measureText(awakeLabel) + 6f * density,
                cy = rY - 3.0f * density,
                r = 3.0f * density,
                direction = 2,
                color = accent,
                strokeW = 1.2f * density,
                filledTriangle = true
            )
            textPaint.textAlign = Paint.Align.RIGHT
            textPaint.typeface = cockpitMonoBold
            textPaint.color = cyanColor
            canvas.drawText(if (snapReady) "${snap.awakePct}% / $sleepPct%" else "-- / --", mRight, rY, textPaint)
            drawCockpitDualSegmentBar(
                canvas, mLeft, rY + 3.5f * density, mRight, rY + 8.5f * density,
                snap.awakePct / 100f, sleepPct / 100f, amberColor, cyanColor, density
            )
            rY += rowStep

            // Row 2: SCREEN ON (follows SPAN: TODAY / 7-DAY)
            drawCockpitVectorIcon(canvas, "SCREEN", mLeft + 5f * density, rY - 3.5f * density, 8.5f * density, accent, density)
            textPaint.textAlign = Paint.Align.LEFT
            textPaint.typeface = overlayTypeface(isBold = true, fallback = cockpitMonoBold)
            textPaint.color = Color.WHITE
            canvas.drawText(scrnTitle, mLeft + 14f * density, rY, textPaint)
            textPaint.textAlign = Paint.Align.RIGHT
            textPaint.typeface = cockpitMonoBold
            textPaint.color = accent
            canvas.drawText(scrnStr, mRight, rY, textPaint)
            drawCockpitSegmentedBar(
                canvas, mLeft, rY + 3.5f * density, mRight, rY + 8.5f * density,
                scrnRatio, 14, accent, Color.argb(40, 255, 255, 255), density
            )
            rY += rowStep

            // Row 3: CELLULAR TRAFFIC (DOWN + UP, SINCE REBOOT)
            drawCockpitVectorIcon(canvas, "CELL", mLeft + 5f * density, rY - 3.5f * density, 8.5f * density, cyanColor, density)
            textPaint.textAlign = Paint.Align.LEFT
            textPaint.typeface = overlayTypeface(isBold = true, fallback = cockpitMonoBold)
            textPaint.color = Color.WHITE
            canvas.drawText(com.sbf.lightspeed.system.lsString(R.string.hud_sys_cellular).ifBlank { "CELLULAR [BOOT] (↓ + ↑)" }, mLeft + 14f * density, rY, textPaint)
            textPaint.textAlign = Paint.Align.RIGHT
            textPaint.typeface = cockpitMonoBold
            textPaint.color = cyanColor
            canvas.drawText(mobValStr, mRight, rY, textPaint)
            drawCockpitDualSegmentBar(
                canvas, mLeft, rY + 3.5f * density, mRight, rY + 8.5f * density,
                mobDownRatio, mobUpRatio, cyanColor, accent, density
            )
            rY += rowStep

            // Row 4: WI-FI TRAFFIC (DOWN + UP, SINCE REBOOT)
            drawCockpitVectorIcon(canvas, "WIFI", mLeft + 5f * density, rY - 3.5f * density, 8.5f * density, emeraldColor, density)
            textPaint.textAlign = Paint.Align.LEFT
            textPaint.typeface = overlayTypeface(isBold = true, fallback = cockpitMonoBold)
            textPaint.color = Color.WHITE
            canvas.drawText(com.sbf.lightspeed.system.lsString(R.string.hud_sys_wifi).ifBlank { "WI-FI [BOOT] (↓ + ↑)" }, mLeft + 14f * density, rY, textPaint)
            textPaint.textAlign = Paint.Align.RIGHT
            textPaint.typeface = cockpitMonoBold
            textPaint.color = emeraldColor
            canvas.drawText(wifiValStr, mRight, rY, textPaint)
            drawCockpitDualSegmentBar(
                canvas, mLeft, rY + 3.5f * density, mRight, rY + 8.5f * density,
                wifiDownRatio, wifiUpRatio, emeraldColor, accent, density
            )
        }
        else -> {
            // Hardware Vitals: Arc gauges (Temp, RAM, Swap) + Horizontal progress bars (Internal, SD/OTG)
            val mLeft = bounds.left + 14f * density
            val mRight = bounds.right - 14f * density
            val availW = (mRight - mLeft).coerceAtLeast(60f * density)

            val gaugeDiameter = (40f * density).coerceAtMost((bounds.height() - 68f * density).coerceAtLeast(28f * density))
            val gaugeTop = bounds.top + 36f * density
            val colW = availW / 3f

            // 1. Vertical Thermometer / Segmented Thermal Bar + State Label (tap to toggle °C / °F)
            val tempColor = vitalsTempColor(snap.tempC, alpha)
            val tempFrac = ((snap.tempC - 15f) / 40f).coerceIn(0.08f, 1f)
            val tempState = vitalsTempStateLabel(snap.tempC)
            val tempDisplay = LightspeedCruiseHudPods.formatTemp(context, snap.tempC)
            val thermCx = mLeft + colW * 0.5f
            vitalsTempGaugeRect.set(thermCx - colW * 0.48f, gaugeTop - 2f * density, thermCx + colW * 0.48f, gaugeTop + gaugeDiameter + 14f * density)
            drawVitalsThermometerGauge(
                canvas = canvas,
                cx = thermCx,
                top = gaugeTop,
                height = gaugeDiameter,
                fraction = tempFrac,
                color = tempColor,
                valueText = tempDisplay,
                stateLabel = tempState,
                density = density
            )

            // 2. RAM Arc Gauge
            val ramColor = vitalsPctColor(snap.ramPct, alpha)
            val ramFrac = (snap.ramPct / 100f).coerceIn(0.03f, 1f)
            val ramSub = if (snapReady) String.format(Locale.US, "%.1f / %.1f GB", snap.usedRamGb, snap.totalRamGb) else "-- / -- GB"
            val ramCx = mLeft + colW * 1.5f
            vitalsRamGaugeRect.set(ramCx - colW * 0.48f, gaugeTop - 2f * density, ramCx + colW * 0.48f, gaugeTop + gaugeDiameter + 14f * density)
            drawVitalsArcGauge(
                canvas = canvas,
                cx = ramCx,
                top = gaugeTop,
                diameter = gaugeDiameter,
                fraction = if (snapReady) ramFrac else 0f,
                color = ramColor,
                valueText = if (snapReady) "${snap.ramPct}%" else "--%",
                ringTitle = "RAM",
                subLabel = ramSub,
                density = density
            )

            // 3. Swap / ZRAM Arc Gauge
            val swapPct = if (snap.swapTotalBytes > 0L) {
                ((snap.swapUsedBytes * 100L) / snap.swapTotalBytes).toInt().coerceIn(0, 100)
            } else 0
            val swapColor = vitalsPctColor(swapPct, alpha)
            val swapFrac = if (snap.swapTotalBytes > 0L) (swapPct / 100f).coerceIn(0.03f, 1f) else 0f
            val swapSub = if (snap.swapTotalBytes > 0L) {
                "${LightspeedCruiseHudPods.formatStorageSize(snap.swapUsedBytes)} / ${LightspeedCruiseHudPods.formatStorageSize(snap.swapTotalBytes)}"
            } else {
                "N/A"
            }
            val swapCx = mLeft + colW * 2.5f
            vitalsSwapGaugeRect.set(swapCx - colW * 0.48f, gaugeTop - 2f * density, swapCx + colW * 0.48f, gaugeTop + gaugeDiameter + 14f * density)
            drawVitalsArcGauge(
                canvas = canvas,
                cx = swapCx,
                top = gaugeTop,
                diameter = gaugeDiameter,
                fraction = swapFrac,
                color = swapColor,
                valueText = "$swapPct%",
                ringTitle = "SWAP",
                subLabel = swapSub,
                density = density
            )

            // Horizontal Storage Progress Bars (Internal + mounted SD/OTG simultaneously, or Expanded Internal)
            val intTot = LightspeedCruiseHudPods.formatStorageSize(snap.totalIntBytes, snap.totalIntGb)
            val intUsed = LightspeedCruiseHudPods.formatStorageSize(snap.usedIntBytes, snap.usedIntGb)
            val intFree = LightspeedCruiseHudPods.formatStorageSize(snap.freeIntBytes, snap.freeIntGb)
            val intUsedPct = if (snap.totalIntBytes > 0L) ((snap.usedIntBytes * 100L) / snap.totalIntBytes).toInt().coerceIn(0, 100)
                else if (snap.totalIntGb > 0) ((snap.usedIntGb * 100) / snap.totalIntGb).coerceIn(0, 100) else 50
            val intColor = vitalsPctColor(intUsedPct, alpha)

            val ext = snap.externalStorage
            var barSectionY = gaugeTop + gaugeDiameter + 17f * density
            if (ext != null) {
                vitalsIntStorageBarRect.set(mLeft, barSectionY - 12f * density, mRight, barSectionY + 10f * density)
                drawStorageProgressBar(
                    canvas = canvas,
                    left = mLeft,
                    right = mRight,
                    labelY = barSectionY,
                    label = if (snapReady) "INT: $intUsed/$intTot ($intFree FREE)" else "INT: LOADING...",
                    pct = if (snapReady) intUsedPct else 0,
                    barColor = intColor,
                    density = density
                )

                barSectionY += 20f * density
                val extTot = LightspeedCruiseHudPods.formatStorageSize(ext.totalBytes, ext.totalGb)
                val extUsed = LightspeedCruiseHudPods.formatStorageSize(ext.usedBytes, ext.usedGb)
                val extFree = LightspeedCruiseHudPods.formatStorageSize(ext.freeBytes, ext.freeGb)
                val extUsedPct = if (ext.totalBytes > 0L) ((ext.usedBytes * 100L) / ext.totalBytes).toInt().coerceIn(0, 100)
                    else if (ext.totalGb > 0) ((ext.usedGb * 100) / ext.totalGb).coerceIn(0, 100) else 0
                val extColor = vitalsPctColor(extUsedPct, alpha)
                vitalsExtStorageBarRect.set(mLeft, barSectionY - 12f * density, mRight, barSectionY + 10f * density)
                drawStorageProgressBar(
                    canvas = canvas,
                    left = mLeft,
                    right = mRight,
                    labelY = barSectionY,
                    label = "${ext.label}: $extUsed/$extTot ($extFree FREE)",
                    pct = extUsedPct,
                    barColor = extColor,
                    density = density
                )
            } else {
                vitalsExtStorageBarRect.setEmpty()
                val bayTop = gaugeTop + gaugeDiameter + 14.5f * density
                val bayBottom = (bounds.bottom - 7f * density).coerceAtLeast(bayTop + 28f * density)
                vitalsIntStorageBarRect.set(mLeft, bayTop, mRight, bayBottom)

                val bayH = vitalsIntStorageBarRect.height()
                if (bayH >= 28f * density) {
                    highlightPaint.style = Paint.Style.FILL
                    highlightPaint.color = Color.argb((26 * alpha).toInt(), 255, 255, 255)
                    canvas.drawRoundRect(vitalsIntStorageBarRect, 6f * density, 6f * density, highlightPaint)
                    highlightPaint.style = Paint.Style.STROKE
                    highlightPaint.strokeWidth = 0.8f * density
                    highlightPaint.color = Color.argb((75 * alpha).toInt(), Color.red(intColor), Color.green(intColor), Color.blue(intColor))
                    canvas.drawRoundRect(vitalsIntStorageBarRect, 6f * density, 6f * density, highlightPaint)
                }

                val padIn = 7f * density
                val inLeft = vitalsIntStorageBarRect.left + padIn
                val inRight = vitalsIntStorageBarRect.right - padIn
                val textY = vitalsIntStorageBarRect.top + (bayH * 0.44f).coerceAtLeast(13.5f * density)

                textPaint.textAlign = Paint.Align.RIGHT
                textPaint.typeface = cockpitMonoBold
                textPaint.textSize = 11.4f * density
                textPaint.color = intColor
                val rightStr = if (snapReady) "$intFree FREE · $intUsedPct%" else "LOADING..."
                val rightW = textPaint.measureText(rightStr)
                canvas.drawText(rightStr, inRight, textY, textPaint)

                textPaint.textAlign = Paint.Align.LEFT
                textPaint.typeface = overlayTypeface(isBold = true, fallback = cockpitMonoBold)
                textPaint.textSize = 11.2f * density
                textPaint.color = Color.WHITE
                val leftStr = clipTextToWidth(
                    if (snapReady) "INTERNAL: $intUsed/$intTot" else "INTERNAL STORAGE",
                    (inRight - rightW - inLeft - 6f * density).coerceAtLeast(40f * density)
                )
                canvas.drawText(leftStr, inLeft, textY, textPaint)

                val barTop = textY + 5f * density
                val barBottom = (barTop + 8.0f * density).coerceAtMost(vitalsIntStorageBarRect.bottom - 4f * density)
                drawCockpitSegmentedBar(
                    canvas = canvas,
                    left = inLeft,
                    top = barTop,
                    right = inRight,
                    bottom = barBottom,
                    ratio = if (snapReady) intUsedPct / 100f else 0f,
                    segments = 18,
                    fillColor = intColor,
                    bgColor = Color.argb(44, 255, 255, 255),
                    density = density
                )
            }
        }
    }
}
