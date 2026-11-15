package com.sbf.lightspeed

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import com.sbf.lightspeed.settings.DeckThemeManager
import com.sbf.lightspeed.system.LightspeedPreferences
import com.sbf.lightspeed.system.LightspeedUsageStats
import com.sbf.lightspeed.system.defaultPrefs
import java.text.NumberFormat
import java.util.Locale
import kotlin.math.roundToInt

private val cockpitMonoBold: Typeface by lazy { Typeface.create(Typeface.MONOSPACE, Typeface.BOLD) }

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
    val showSpan = (bMode == "apps")
    val bSpanLabel = if (showSpan) {
        LightspeedCruiseHudPods.BATTERY_SPAN_OPTIONS.firstOrNull { it.first == bSpan }?.second ?: "7-DAY"
    } else null
    val snap = LightspeedCruiseHudPods.latestBatterySnapshot
    val snapReady = LightspeedCruiseHudPods.hasBatterySnapshot

    // Top Header with Main Widget Caption
    drawWidgetChipHeader(
        canvas = canvas,
        bounds = bounds,
        widgetTitle = "SYSTEM VITALS",
        primaryChip = "VIEW: $bModeLabel",
        secondaryChip = if (bSpanLabel != null) "SPAN: $bSpanLabel" else null,
        accent = accent,
        density = density,
        alpha = alpha,
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
            textPaint.typeface = cockpitMonoBold
            textPaint.textSize = 9.4f * density
            textPaint.color = accent
            canvas.drawText(if (snap.isCharging) "CHARGING" else "CELL CHARGE", leftColX + 14f * density, bounds.top + 40f * density, textPaint)

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
                "HEALTH: ${snap.healthLabel}"
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
            canvas.drawText(clipTextToWidth("7D AVG $avg7dStr", rightColMaxW), rightLeft, bounds.top + 77.5f * density, textPaint)

            // 2x2 Real Battery Telemetry Grid in lower card space
            val gridTop = bounds.top + 84f * density
            val gridBot = bounds.bottom - 8f * density
            if (gridBot - gridTop >= 36f * density) {
                val cellW = ((rightRight - leftColX) - 7f * density) / 2f
                val cellH = ((gridBot - gridTop) - 5f * density) / 2f
                val thermalState = vitalsTempStateLabel(snap.tempC)
                val compactThermalLabel = when (thermalState) {
                    "NORMAL" -> "TEMP (NORM)"
                    "OPTIMAL" -> "TEMP (OPT)"
                    else -> "TEMP ($thermalState)"
                }
                val gridCells = listOf(
                    Triple("PEAK/MIN", LightspeedCruiseHudPods.flipDigitsIfActive(snap.chgPeakMinStr, seedOffset = 7), false),
                    Triple("DRAW", LightspeedCruiseHudPods.flipDigitsIfActive("${snap.wattageStr}·${snap.voltageMv}mV", seedOffset = 8), false),
                    Triple(compactThermalLabel, LightspeedCruiseHudPods.flipDigitsIfActive(tempStr, seedOffset = 9), true),
                    Triple("EST. LEFT", LightspeedCruiseHudPods.flipDigitsIfActive(snap.estRemainingStr, seedOffset = 10), false)
                )
                vitalsTempGaugeRect.setEmpty()
                for ((idx, cell) in gridCells.withIndex()) {
                    val col = idx % 2
                    val row = idx / 2
                    val cLeft = leftColX + col * (cellW + 7f * density)
                    val cTop = gridTop + row * (cellH + 5f * density)
                    val cRect = RectF(cLeft, cTop, cLeft + cellW, cTop + cellH)
                    if (cell.third) vitalsTempGaugeRect.set(cRect)

                    highlightPaint.style = Paint.Style.FILL
                    highlightPaint.color = Color.argb((34 * alpha).toInt(), 255, 255, 255)
                    canvas.drawRoundRect(cRect, 6f * density, 6f * density, highlightPaint)
                    highlightPaint.style = Paint.Style.STROKE
                    highlightPaint.strokeWidth = 0.9f * density
                    highlightPaint.color = Color.argb((75 * alpha).toInt(), Color.red(accent), Color.green(accent), Color.blue(accent))
                    canvas.drawRoundRect(cRect, 6f * density, 6f * density, highlightPaint)

                    // Single centered row: compact label on left, bold telemetry readout on right (zero vertical overlap)
                    val centerBaselineY = cRect.centerY() + 3.1f * density
                    textPaint.textAlign = Paint.Align.LEFT
                    textPaint.typeface = cockpitMonoBold
                    textPaint.textSize = 7.4f * density
                    textPaint.color = accent
                    canvas.drawText(cell.first, cLeft + 6f * density, centerBaselineY, textPaint)

                    textPaint.textAlign = Paint.Align.RIGHT
                    textPaint.typeface = cockpitMonoBold
                    textPaint.textSize = 8.6f * density
                    textPaint.color = Color.WHITE
                    canvas.drawText(cell.second, cRect.right - 6f * density, centerBaselineY, textPaint)
                }
            } else {
                vitalsTempGaugeRect.setEmpty()
                textPaint.textAlign = Paint.Align.CENTER
                textPaint.typeface = cockpitMonoBold
                textPaint.textSize = 9.0f * density
                textPaint.color = Color.argb(195, 200, 220, 245)
                canvas.drawText("LIVE HARDWARE BATTERY TELEMETRY", bounds.centerX(), bounds.bottom - 8f * density, textPaint)
            }
        }
        "apps" -> {
            // Spacecraft Power Consumption Ranking by Apps
            val mLeft = bounds.left + 13f * density
            val mRight = bounds.right - 13f * density

            drawCockpitVectorIcon(canvas, "APPS", mLeft + 5f * density, bounds.top + 36.5f * density, 8.5f * density, accent, density)
            textPaint.textAlign = Paint.Align.LEFT
            textPaint.typeface = cockpitMonoBold
            textPaint.textSize = 9.4f * density
            textPaint.color = accent
            canvas.drawText("TOP POWER CONSUMERS (% DRAIN)", mLeft + 14f * density, bounds.top + 40f * density, textPaint)

            val listTop = bounds.top + 47f * density
            val listBottom = bounds.bottom - 8f * density
            val listH = (listBottom - listTop).coerceAtLeast(20f * density)
            val rowStep = 21.5f * density

            val usageMap = LightspeedCruiseHudPods.getUsageMap(if (bSpan == "today") "1d" else "7d")
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
                textPaint.typeface = cockpitMonoBold
                textPaint.textSize = 10.2f * density
                textPaint.color = Color.argb(150, 200, 220, 245)
                canvas.drawText("NO POWER USAGE LOGGED", mLeft, listTop + 16f * density, textPaint)
            } else {
                canvas.save()
                canvas.clipRect(mLeft - 2f * density, bounds.top + 45f * density, mRight + 2f * density, listBottom)
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
                    textPaint.typeface = cockpitMonoBold
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

                if (batteryAppsMaxScroll > 0f) {
                    val sbW = 2.5f * density
                    val sbX = mRight + 5f * density
                    val sbRatio = (listH / totalListH).coerceIn(0.15f, 0.8f)
                    val thumbH = listH * sbRatio
                    val thumbY = listTop + ((batteryAppsScrollOffset / batteryAppsMaxScroll) * (listH - thumbH))
                    highlightPaint.style = Paint.Style.FILL
                    highlightPaint.color = Color.argb(60, 255, 255, 255)
                    canvas.drawRoundRect(RectF(sbX, listTop, sbX + sbW, listBottom), 1.5f * density, 1.5f * density, highlightPaint)
                    highlightPaint.color = accent
                    canvas.drawRoundRect(RectF(sbX, thumbY, sbX + sbW, thumbY + thumbH), 1.5f * density, 1.5f * density, highlightPaint)
                }
            }
        }
        "system" -> {
            // Dual-segment cockpit bars: CPU Awake vs Deep Sleep, Screen On Today, Cellular (Down+Up), Wi-Fi (Down+Up)
            val mLeft = bounds.left + 13f * density
            val mRight = bounds.right - 13f * density

            val sleepPct = (100 - snap.awakePct).coerceIn(0, 100)
            val hasUsage = LightspeedUsageStats.hasUsageAccess(context)
            val scrnStr = if (hasUsage) LightspeedCruiseHudPods.formatDurationCompact(snap.screenTodayMs) else "NO ACCESS"
            val scrnRatio = if (hasUsage) (snap.screenTodayMs.toFloat() / (12f * 3_600_000f)).coerceIn(0.03f, 1f) else 0f

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
            textPaint.typeface = cockpitMonoBold
            textPaint.textSize = 9.0f * density
            textPaint.color = Color.WHITE
            val awakeLabel = "CPU AWAKE vs SLEEP [HUNT]"
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
            textPaint.color = cyanColor
            canvas.drawText(if (snapReady) "${snap.awakePct}% / $sleepPct%" else "-- / --", mRight, rY, textPaint)
            drawCockpitDualSegmentBar(
                canvas, mLeft, rY + 3.5f * density, mRight, rY + 8.5f * density,
                snap.awakePct / 100f, sleepPct / 100f, amberColor, cyanColor, density
            )
            rY += rowStep

            // Row 2: SCREEN ON TODAY
            drawCockpitVectorIcon(canvas, "SCREEN", mLeft + 5f * density, rY - 3.5f * density, 8.5f * density, accent, density)
            textPaint.textAlign = Paint.Align.LEFT
            textPaint.color = Color.WHITE
            canvas.drawText("SCREEN ON TODAY", mLeft + 14f * density, rY, textPaint)
            textPaint.textAlign = Paint.Align.RIGHT
            textPaint.color = accent
            canvas.drawText(scrnStr, mRight, rY, textPaint)
            drawCockpitSegmentedBar(
                canvas, mLeft, rY + 3.5f * density, mRight, rY + 8.5f * density,
                scrnRatio, 14, accent, Color.argb(40, 255, 255, 255), density
            )
            rY += rowStep

            // Row 3: CELLULAR TRAFFIC (DOWN + UP)
            drawCockpitVectorIcon(canvas, "CELL", mLeft + 5f * density, rY - 3.5f * density, 8.5f * density, cyanColor, density)
            textPaint.textAlign = Paint.Align.LEFT
            textPaint.color = Color.WHITE
            canvas.drawText("CELLULAR (DOWN + UP)", mLeft + 14f * density, rY, textPaint)
            textPaint.textAlign = Paint.Align.RIGHT
            textPaint.color = cyanColor
            canvas.drawText(mobValStr, mRight, rY, textPaint)
            drawCockpitDualSegmentBar(
                canvas, mLeft, rY + 3.5f * density, mRight, rY + 8.5f * density,
                mobDownRatio, mobUpRatio, cyanColor, accent, density
            )
            rY += rowStep

            // Row 4: WI-FI TRAFFIC (DOWN + UP)
            drawCockpitVectorIcon(canvas, "WIFI", mLeft + 5f * density, rY - 3.5f * density, 8.5f * density, emeraldColor, density)
            textPaint.textAlign = Paint.Align.LEFT
            textPaint.color = Color.WHITE
            canvas.drawText("WI-FI (DOWN + UP)", mLeft + 14f * density, rY, textPaint)
            textPaint.textAlign = Paint.Align.RIGHT
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
                val bayRect = RectF(mLeft, bayTop, mRight, bayBottom)
                vitalsIntStorageBarRect.set(bayRect)

                val bayH = bayRect.height()
                if (bayH >= 28f * density) {
                    highlightPaint.style = Paint.Style.FILL
                    highlightPaint.color = Color.argb((26 * alpha).toInt(), 255, 255, 255)
                    canvas.drawRoundRect(bayRect, 6f * density, 6f * density, highlightPaint)
                    highlightPaint.style = Paint.Style.STROKE
                    highlightPaint.strokeWidth = 0.8f * density
                    highlightPaint.color = Color.argb((75 * alpha).toInt(), Color.red(intColor), Color.green(intColor), Color.blue(intColor))
                    canvas.drawRoundRect(bayRect, 6f * density, 6f * density, highlightPaint)
                }

                val padIn = 7f * density
                val inLeft = bayRect.left + padIn
                val inRight = bayRect.right - padIn
                val textY = bayRect.top + (bayH * 0.44f).coerceAtLeast(13.5f * density)

                textPaint.textAlign = Paint.Align.RIGHT
                textPaint.typeface = cockpitMonoBold
                textPaint.textSize = 11.4f * density
                textPaint.color = intColor
                val rightStr = if (snapReady) "$intFree FREE · $intUsedPct%" else "LOADING..."
                val rightW = textPaint.measureText(rightStr)
                canvas.drawText(rightStr, inRight, textY, textPaint)

                textPaint.textAlign = Paint.Align.LEFT
                textPaint.typeface = cockpitMonoBold
                textPaint.textSize = 11.2f * density
                textPaint.color = Color.WHITE
                val leftStr = clipTextToWidth(
                    if (snapReady) "INTERNAL: $intUsed/$intTot" else "INTERNAL STORAGE",
                    (inRight - rightW - inLeft - 6f * density).coerceAtLeast(40f * density)
                )
                canvas.drawText(leftStr, inLeft, textY, textPaint)

                val barTop = textY + 5f * density
                val barBottom = (barTop + 8.0f * density).coerceAtMost(bayRect.bottom - 4f * density)
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

/** Emerald Green (<75%), Amber (75..89%), Crimson Red (>=90%). */
private fun vitalsPctColor(pct: Int, alpha: Float): Int {
    val a = (255 * alpha).toInt().coerceIn(0, 255)
    return when {
        pct >= 90 -> Color.argb(a, 239, 68, 68)
        pct >= 75 -> Color.argb(a, 245, 158, 11)
        else -> Color.argb(a, 34, 197, 94)
    }
}

private fun vitalsTempStateLabel(tempC: Int): String = when {
    tempC >= 44 -> "HOT"
    tempC >= 38 -> "WARM"
    tempC >= 33 -> "NORMAL"
    else -> "COOL"
}

private fun LightspeedCruiseOverlay.drawVitalsThermometerGauge(
    canvas: Canvas,
    cx: Float,
    top: Float,
    height: Float,
    fraction: Float,
    color: Int,
    valueText: String,
    stateLabel: String,
    density: Float
) {
    val barW = 10f * density
    val barLeft = cx - 22f * density
    val barTop = top + 2f * density
    val barBot = top + height - 2f * density
    val segCount = 6
    val segGap = 2f * density
    val segH = ((barBot - barTop) - (segCount - 1) * segGap) / segCount
    val activeSegs = (fraction * segCount).roundToInt().coerceIn(1, segCount)

    for (i in 0 until segCount) {
        val segIdxFromBot = segCount - 1 - i
        val sTop = barTop + i * (segH + segGap)
        val sRect = RectF(barLeft, sTop, barLeft + barW, sTop + segH)
        highlightPaint.style = Paint.Style.FILL
        highlightPaint.color = if (segIdxFromBot < activeSegs) color else Color.argb(38, 255, 255, 255)
        canvas.drawRoundRect(sRect, 2f * density, 2f * density, highlightPaint)
    }

    val textX = barLeft + barW + 6f * density
    val barCenterY = top + height * 0.5f
    textPaint.textAlign = Paint.Align.LEFT
    textPaint.typeface = cockpitMonoBold
    textPaint.textSize = 12.2f * density
    textPaint.color = Color.WHITE
    val valFm = textPaint.fontMetrics
    canvas.drawText(valueText, textX, barCenterY - 2f * density - valFm.descent * 0.25f, textPaint)

    textPaint.textSize = 8.8f * density
    textPaint.color = color
    val stFm = textPaint.fontMetrics
    canvas.drawText(stateLabel, textX, barCenterY + 2f * density - stFm.ascent * 0.85f, textPaint)
}

/** Emerald Green (<38°C), Amber (38..43°C), Crimson Red (>=44°C). */
private fun vitalsTempColor(tempC: Int, alpha: Float): Int {
    val a = (255 * alpha).toInt().coerceIn(0, 255)
    return when {
        tempC >= 44 -> Color.argb(a, 239, 68, 68)
        tempC >= 38 -> Color.argb(a, 245, 158, 11)
        else -> Color.argb(a, 34, 197, 94)
    }
}

private fun LightspeedCruiseOverlay.drawVitalsArcGauge(
    canvas: Canvas,
    cx: Float,
    top: Float,
    diameter: Float,
    fraction: Float,
    color: Int,
    valueText: String,
    ringTitle: String,
    subLabel: String,
    density: Float
) {
    val arcRect = RectF(cx - diameter / 2f, top, cx + diameter / 2f, top + diameter)
    val strokeW = 4.2f * density

    highlightPaint.style = Paint.Style.STROKE
    highlightPaint.strokeCap = Paint.Cap.ROUND
    highlightPaint.strokeWidth = strokeW
    highlightPaint.color = Color.argb(45, 255, 255, 255)
    canvas.drawArc(arcRect, 150f, 240f, false, highlightPaint)

    if (fraction > 0f) {
        highlightPaint.color = color
        canvas.drawArc(arcRect, 150f, 240f * fraction.coerceIn(0.02f, 1f), false, highlightPaint)
    }
    highlightPaint.strokeCap = Paint.Cap.BUTT

    textPaint.textAlign = Paint.Align.CENTER
    textPaint.typeface = cockpitMonoBold
    textPaint.textSize = (diameter * 0.27f).coerceAtLeast(9.5f * density)
    textPaint.color = Color.WHITE
    val fm = textPaint.fontMetrics
    canvas.drawText(valueText, cx, arcRect.centerY() - ((fm.descent + fm.ascent) / 2f) - 1f * density, textPaint)

    // Ring title ("RAM" / "SWAP") inside the bottom opening of the 240° arc
    textPaint.typeface = cockpitMonoBold
    textPaint.textSize = (diameter * 0.19f).coerceAtLeast(8.2f * density)
    textPaint.color = color
    val rFm = textPaint.fontMetrics
    canvas.drawText(ringTitle, cx, arcRect.bottom - 1.5f * density - rFm.descent * 0.35f, textPaint)

    // Used / Total numbers cleanly underneath the dial
    textPaint.typeface = cockpitMonoBold
    textPaint.textSize = 9.0f * density
    textPaint.color = Color.argb(230, 215, 230, 250)
    canvas.drawText(subLabel, cx, arcRect.bottom + 10.5f * density, textPaint)
}

private fun LightspeedCruiseOverlay.drawStorageProgressBar(
    canvas: Canvas,
    left: Float,
    right: Float,
    labelY: Float,
    label: String,
    pct: Int,
    barColor: Int,
    density: Float
) {
    textPaint.textAlign = Paint.Align.RIGHT
    textPaint.typeface = cockpitMonoBold
    textPaint.textSize = 10.0f * density
    textPaint.color = barColor
    val pctStr = "$pct%"
    val pctW = textPaint.measureText(pctStr)
    canvas.drawText(pctStr, right, labelY, textPaint)

    textPaint.textAlign = Paint.Align.LEFT
    textPaint.typeface = cockpitMonoBold
    textPaint.textSize = 9.8f * density
    textPaint.color = Color.WHITE
    canvas.drawText(clipTextToWidth(label, (right - pctW - left - 6f * density).coerceAtLeast(40f * density)), left, labelY, textPaint)

    val barTop = labelY + 3f * density
    drawCockpitSegmentedBar(
        canvas = canvas,
        left = left,
        top = barTop,
        right = right,
        bottom = barTop + 5.8f * density,
        ratio = pct / 100f,
        segments = 16,
        fillColor = barColor,
        bgColor = Color.argb(42, 255, 255, 255),
        density = density
    )
}

internal fun LightspeedCruiseOverlay.drawCockpitSegmentedBar(
    canvas: Canvas,
    left: Float,
    top: Float,
    right: Float,
    bottom: Float,
    ratio: Float,
    segments: Int,
    fillColor: Int,
    bgColor: Int,
    density: Float
) {
    val totalW = (right - left).coerceAtLeast(20f * density)
    val gap = 2f * density
    val segCount = segments.coerceAtLeast(4)
    val segW = ((totalW - gap * (segCount - 1)) / segCount).coerceAtLeast(2f * density)
    val filledSegs = if (ratio <= 0f) 0 else (ratio.coerceIn(0f, 1f) * segCount).roundToInt().coerceIn(1, segCount)
    highlightPaint.style = Paint.Style.FILL
    for (i in 0 until segCount) {
        val sx = left + i * (segW + gap)
        highlightPaint.color = if (i < filledSegs) fillColor else bgColor
        canvas.drawRoundRect(RectF(sx, top, sx + segW, bottom), 1.5f * density, 1.5f * density, highlightPaint)
    }
}

internal fun LightspeedCruiseOverlay.drawCockpitDualSegmentBar(
    canvas: Canvas,
    left: Float,
    top: Float,
    right: Float,
    bottom: Float,
    firstRatio: Float,
    secondRatio: Float,
    firstColor: Int,
    secondColor: Int,
    density: Float
) {
    val totalW = (right - left).coerceAtLeast(20f * density)
    val segments = 16
    val gap = 2f * density
    val segW = ((totalW - gap * (segments - 1)) / segments).coerceAtLeast(2f * density)
    val sumRatio = (firstRatio + secondRatio).coerceAtLeast(0f)
    val firstSegs = if (sumRatio <= 0f) 0 else ((firstRatio / sumRatio) * segments).roundToInt().coerceIn(0, segments)
    highlightPaint.style = Paint.Style.FILL
    for (i in 0 until segments) {
        val sx = left + i * (segW + gap)
        highlightPaint.color = when {
            sumRatio <= 0f -> Color.argb(38, 255, 255, 255)
            i < firstSegs -> firstColor
            else -> secondColor
        }
        canvas.drawRoundRect(RectF(sx, top, sx + segW, bottom), 1.5f * density, 1.5f * density, highlightPaint)
    }
}

internal fun LightspeedCruiseOverlay.drawCockpitVectorIcon(
    canvas: Canvas,
    kind: String,
    cx: Float,
    cy: Float,
    size: Float,
    color: Int,
    density: Float
) {
    val r = size * 0.48f
    highlightPaint.style = Paint.Style.STROKE
    highlightPaint.strokeWidth = 1.25f * density
    highlightPaint.color = color
    when (kind) {
        "BATTERY" -> {
            canvas.drawRoundRect(RectF(cx - r * 0.85f, cy - r * 0.52f, cx + r * 0.72f, cy + r * 0.52f), 1.5f * density, 1.5f * density, highlightPaint)
            highlightPaint.style = Paint.Style.FILL
            canvas.drawRect(cx + r * 0.72f, cy - r * 0.24f, cx + r, cy + r * 0.24f, highlightPaint)
            canvas.drawRect(cx - r * 0.55f, cy - r * 0.25f, cx + r * 0.35f, cy + r * 0.25f, highlightPaint)
        }
        "BOLT" -> {
            val p = android.graphics.Path().apply {
                moveTo(cx + r * 0.15f, cy - r)
                lineTo(cx - r * 0.55f, cy + r * 0.08f)
                lineTo(cx + r * 0.05f, cy + r * 0.08f)
                lineTo(cx - r * 0.15f, cy + r)
                lineTo(cx + r * 0.55f, cy - r * 0.08f)
                lineTo(cx - r * 0.05f, cy - r * 0.08f)
                close()
            }
            highlightPaint.style = Paint.Style.FILL
            canvas.drawPath(p, highlightPaint)
        }
        "HEART" -> {
            val p = android.graphics.Path().apply {
                moveTo(cx, cy + r * 0.85f)
                lineTo(cx - r * 0.85f, cy - r * 0.05f)
                lineTo(cx - r * 0.45f, cy - r * 0.65f)
                lineTo(cx, cy - r * 0.25f)
                lineTo(cx + r * 0.45f, cy - r * 0.65f)
                lineTo(cx + r * 0.85f, cy - r * 0.05f)
                close()
            }
            canvas.drawPath(p, highlightPaint)
        }
        "CPU" -> {
            canvas.drawRect(cx - r * 0.55f, cy - r * 0.55f, cx + r * 0.55f, cy + r * 0.55f, highlightPaint)
            canvas.drawLine(cx - r, cy - r * 0.25f, cx - r * 0.55f, cy - r * 0.25f, highlightPaint)
            canvas.drawLine(cx - r, cy + r * 0.25f, cx - r * 0.55f, cy + r * 0.25f, highlightPaint)
            canvas.drawLine(cx + r * 0.55f, cy - r * 0.25f, cx + r, cy - r * 0.25f, highlightPaint)
            canvas.drawLine(cx + r * 0.55f, cy + r * 0.25f, cx + r, cy + r * 0.25f, highlightPaint)
        }
        "SCREEN" -> {
            canvas.drawRoundRect(RectF(cx - r * 0.85f, cy - r * 0.62f, cx + r * 0.85f, cy + r * 0.42f), 1.5f * density, 1.5f * density, highlightPaint)
            canvas.drawLine(cx - r * 0.35f, cy + r * 0.78f, cx + r * 0.35f, cy + r * 0.78f, highlightPaint)
        }
        "CELL" -> {
            highlightPaint.style = Paint.Style.FILL
            canvas.drawRect(cx - r * 0.75f, cy + r * 0.2f, cx - r * 0.45f, cy + r * 0.8f, highlightPaint)
            canvas.drawRect(cx - r * 0.15f, cy - r * 0.2f, cx + r * 0.15f, cy + r * 0.8f, highlightPaint)
            canvas.drawRect(cx + r * 0.45f, cy - r * 0.7f, cx + r * 0.75f, cy + r * 0.8f, highlightPaint)
        }
        "WIFI" -> {
            canvas.drawArc(RectF(cx - r, cy - r * 0.6f, cx + r, cy + r * 1.4f), 225f, 90f, false, highlightPaint)
            canvas.drawArc(RectF(cx - r * 0.6f, cy - r * 0.2f, cx + r * 0.6f, cy + r), 225f, 90f, false, highlightPaint)
            highlightPaint.style = Paint.Style.FILL
            canvas.drawCircle(cx, cy + r * 0.65f, 1.3f * density, highlightPaint)
        }
        else -> {
            val sq = r * 0.38f
            canvas.drawRect(cx - r * 0.75f, cy - r * 0.75f, cx - r * 0.75f + sq * 1.3f, cy - r * 0.75f + sq * 1.3f, highlightPaint)
            canvas.drawRect(cx + r * 0.15f, cy - r * 0.75f, cx + r * 0.15f + sq * 1.3f, cy - r * 0.75f + sq * 1.3f, highlightPaint)
            canvas.drawRect(cx - r * 0.75f, cy + r * 0.15f, cx - r * 0.75f + sq * 1.3f, cy + r * 0.15f + sq * 1.3f, highlightPaint)
            canvas.drawRect(cx + r * 0.15f, cy + r * 0.15f, cx + r * 0.15f + sq * 1.3f, cy + r * 0.15f + sq * 1.3f, highlightPaint)
        }
    }
}
