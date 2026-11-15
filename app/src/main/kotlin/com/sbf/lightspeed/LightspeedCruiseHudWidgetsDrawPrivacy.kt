package com.sbf.lightspeed

import android.appwidget.AppWidgetManager
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import com.sbf.lightspeed.system.LightspeedPreferences
import com.sbf.lightspeed.system.NightlyFeatures
import java.util.Locale

private val inspectorFontCache = java.util.concurrent.ConcurrentHashMap<String, Typeface>()
private val widgetLabelByIdCache = java.util.concurrent.ConcurrentHashMap<Int, String>()
private val hackerMonoTypeface by lazy { Typeface.create(Typeface.MONOSPACE, Typeface.BOLD) }
private val hackerGlyphs = arrayOf("0", "1", "2", "3", "4", "5", "6", "7", "8", "9", "0", "4", "7", "9", "A", "E", "F")

internal fun resolveDeviceFontTypeface(fontKey: String, isBold: Boolean): Typeface? {
    if (fontKey.isBlank() || fontKey == "system_default") return null
    val cacheKey = "$fontKey|$isBold"
    return inspectorFontCache.getOrPut(cacheKey) {
        try {
            val base = if (fontKey.startsWith("/")) {
                Typeface.createFromFile(fontKey)
            } else {
                Typeface.create(fontKey, if (isBold) Typeface.BOLD else Typeface.NORMAL)
            }
            if (isBold) Typeface.create(base, Typeface.BOLD) else base
        } catch (_: Exception) {
            if (isBold) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
        }
    }
}

private fun resolveInspectorTypeface(fontFamily: Int, fontKey: String, isBold: Boolean): Typeface {
    resolveDeviceFontTypeface(fontKey, isBold)?.let { return it }
    return when (fontFamily) {
        1 -> if (isBold) Typeface.create(Typeface.MONOSPACE, Typeface.BOLD) else Typeface.MONOSPACE
        2 -> if (isBold) Typeface.create(Typeface.SERIF, Typeface.BOLD) else Typeface.SERIF
        3 -> Typeface.create("sans-serif-condensed", if (isBold) Typeface.BOLD else Typeface.NORMAL)
        else -> if (isBold) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
    }
}

/** Cruise HUD widget renderers: Unified Focused App Inspector, Android widget placeholder, and chip header. */
internal fun LightspeedCruiseOverlay.drawFocusedAppInspectorWidget(
    canvas: Canvas,
    bounds: RectF,
    catId: String,
    catLabel: String,
    accent: Int,
    density: Float,
    alpha: Float,
    slotIndex: Int = 1,
    titleFontSizeSp: Int = 0,
    subtitleFontSizeSp: Int = 0,
    fontFamily: Int = 0,
    fontKey: String = ""
) {
    val focused = activeItem
    val padX = 12f * density
    val titleTypeface = resolveInspectorTypeface(fontFamily, fontKey, isBold = true)
    val subTypeface = if (fontKey.isBlank() && fontFamily == 0) Typeface.MONOSPACE else resolveInspectorTypeface(fontFamily, fontKey, isBold = false)
    val shadowColor = Color.argb((215 * alpha).toInt().coerceIn(0, 255), 0, 0, 0)

    if (focused != null) {
        val telemetry = LightspeedCruiseHudPods.focusedAppTelemetry(context, focused.packageName, focused.label)
        val iconSize = (36f * density).coerceAtMost(bounds.height() * 0.52f)
        val iconLeft = bounds.left + padX
        val iconTop = bounds.top + 8f * density
        val iconRect = RectF(iconLeft, iconTop, iconLeft + iconSize, iconTop + iconSize)

        val icon = applicationIconCache[focused.packageName]
        if (icon != null) {
            icon.setBounds(
                iconRect.left.toInt(),
                iconRect.top.toInt(),
                iconRect.right.toInt(),
                iconRect.bottom.toInt()
            )
            icon.alpha = (255 * alpha).toInt().coerceIn(0, 255)
            icon.draw(canvas)
            icon.alpha = 255
        } else {
            highlightPaint.style = Paint.Style.FILL
            highlightPaint.color = Color.argb((45 * alpha).toInt(), Color.red(accent), Color.green(accent), Color.blue(accent))
            canvas.drawRoundRect(iconRect, 8f * density, 8f * density, highlightPaint)
            textPaint.textAlign = Paint.Align.CENTER
            textPaint.typeface = titleTypeface
            textPaint.textSize = iconSize * 0.45f
            textPaint.color = accent
            textPaint.alpha = (255 * alpha).toInt().coerceIn(0, 255)
            val fm = textPaint.fontMetrics
            canvas.drawText(
                focused.label.firstOrNull()?.uppercase() ?: "?",
                iconRect.centerX(),
                iconRect.centerY() - ((fm.descent + fm.ascent) / 2f),
                textPaint
            )
        }

        val textLeft = iconRect.right + 10f * density
        val maxTextW = (bounds.right - padX - textLeft).coerceAtLeast(40f * density)
        val titleSizePx = (if (titleFontSizeSp > 0) titleFontSizeSp.toFloat() else 13.0f) * density
        val subSizePx = (if (subtitleFontSizeSp > 0) subtitleFontSizeSp.toFloat() else 8.8f) * density

        // App Name
        textPaint.setShadowLayer(4f * density, 0f, 1.5f * density, shadowColor)
        textPaint.textAlign = Paint.Align.LEFT
        textPaint.typeface = titleTypeface
        textPaint.textSize = titleSizePx
        textPaint.color = Color.WHITE
        textPaint.alpha = (255 * alpha).toInt().coerceIn(0, 255)
        val titleStr = ellipsizeText(focused.label.uppercase(), textPaint, maxTextW)
        val titleY = iconTop + titleSizePx + 1f * density
        canvas.drawText(titleStr, textLeft, titleY, textPaint)

        // Package Name underneath
        textPaint.typeface = subTypeface
        textPaint.textSize = subSizePx
        textPaint.color = accent
        textPaint.alpha = (225 * alpha).toInt().coerceIn(0, 255)
        val pkgStr = ellipsizeText(focused.packageName, textPaint, maxTextW)
        val subY = titleY + subSizePx + 3.5f * density
        canvas.drawText(pkgStr, textLeft, subY, textPaint)
        textPaint.clearShadowLayer()

        // Vector Permission Pills + Privacy Score + [!] Info + Screen Time + Tappable ALERTS Pill
        val pillH = 15.5f * density
        val pillY = (maxOf(iconRect.bottom, subY) + 8f * density).coerceAtMost(bounds.bottom - pillH - 4f * density)
        var curX = bounds.left + padX
        val pillGap = 4f * density
        val alphaInt = (255 * alpha).toInt().coerceIn(0, 255)

        val score = telemetry.profile.score
        val scoreColor = LightspeedPrivacyProfiler.scoreColor(score, alphaInt)

        // Privacy Score badge
        textPaint.textAlign = Paint.Align.LEFT
        textPaint.typeface = Typeface.DEFAULT_BOLD
        textPaint.textSize = 8.4f * density
        textPaint.color = scoreColor
        val rawScore = LightspeedPrivacyProfiler.formatScore(score)
        val scoreStr = LightspeedCruiseHudPods.flipDigitsIfActive(rawScore, seedOffset = 71)
        val scoreW = textPaint.measureText(scoreStr)
        val fm = textPaint.fontMetrics
        val pillTextY = pillY + pillH * 0.5f - ((fm.descent + fm.ascent) / 2f)
        canvas.drawText(scoreStr, curX, pillTextY, textPaint)
        curX += scoreW + 4f * density

        // [ ! ] Info Button next to Privacy Score
        val infoSize = 14f * density
        val infoRect = RectF(curX, pillY + (pillH - infoSize) * 0.5f, curX + infoSize, pillY + (pillH + infoSize) * 0.5f)
        privacyScoreInfoRect.set(infoRect)
        highlightPaint.style = Paint.Style.FILL
        highlightPaint.color = Color.argb((55 * alpha).toInt(), Color.red(accent), Color.green(accent), Color.blue(accent))
        canvas.drawRoundRect(infoRect, 4f * density, 4f * density, highlightPaint)
        highlightPaint.style = Paint.Style.STROKE
        highlightPaint.strokeWidth = 0.9f * density
        highlightPaint.color = Color.argb((130 * alpha).toInt(), Color.red(accent), Color.green(accent), Color.blue(accent))
        canvas.drawRoundRect(infoRect, 4f * density, 4f * density, highlightPaint)
        textPaint.textAlign = Paint.Align.CENTER
        textPaint.textSize = 8.4f * density
        textPaint.color = Color.argb(alphaInt, 255, 255, 255)
        canvas.drawText("!", infoRect.centerX(), infoRect.centerY() - ((fm.descent + fm.ascent) / 2f), textPaint)
        curX += infoSize + 5f * density

        // Right-side ALERTS pill + TODAY screen time
        val rawScrnStr = telemetry.screenTimeTodayMs?.let { LightspeedCruiseHudPods.formatDurationCompact(it) } ?: "--"
        val scrnStr = LightspeedCruiseHudPods.flipDigitsIfActive(rawScrnStr, seedOffset = 72)
        val alertLabel = LightspeedCruiseHudPods.flipDigitsIfActive("${telemetry.alertCount} ALERTS", seedOffset = 73)
        textPaint.typeface = Typeface.DEFAULT_BOLD
        textPaint.textSize = 7.6f * density
        val estAlertPillW = 19f * density + textPaint.measureText(alertLabel)
        val alertPillLeft = (bounds.right - padX - estAlertPillW).coerceAtLeast(curX + 10f * density)
        val alertRect = LightspeedHudVectorIcons.drawVectorPill(
            canvas = canvas,
            left = alertPillLeft,
            top = pillY,
            height = pillH,
            label = alertLabel,
            iconKind = "ALERTS",
            pillColor = accent,
            bgAlpha = (45 * alpha).toInt(),
            borderAlpha = (115 * alpha).toInt(),
            textAlpha = alphaInt,
            density = density,
            textPaint = textPaint
        )
        hudAlertsPillRect.set(alertRect)

        // Draw TODAY screen time to the left of ALERTS if space permits
        textPaint.textAlign = Paint.Align.RIGHT
        textPaint.typeface = Typeface.MONOSPACE
        textPaint.textSize = 7.8f * density
        val todayText = "TODAY $scrnStr"
        val todayW = textPaint.measureText(todayText)
        val pillsRightLimit = if (alertRect.left - todayW - 8f * density > curX + 36f * density) {
            textPaint.color = Color.argb((215 * alpha).toInt(), 215, 228, 248)
            canvas.drawText(todayText, alertRect.left - 6f * density, pillTextY, textPaint)
            alertRect.left - todayW - 10f * density
        } else {
            alertRect.left - 4f * density
        }

        // Network pill (NET / OFFLINE / BLOCKED)
        val netKind = when {
            telemetry.firewallBlocked -> "BLOCKED"
            telemetry.profile.hasInternet -> "NET"
            else -> "OFFLINE"
        }
        val netColor = when (netKind) {
            "BLOCKED" -> Color.rgb(239, 68, 68)
            "OFFLINE" -> Color.rgb(34, 197, 94)
            else -> accent
        }
        val netRect = LightspeedHudVectorIcons.drawVectorPill(
            canvas = canvas,
            left = curX,
            top = pillY,
            height = pillH,
            label = netKind,
            iconKind = netKind,
            pillColor = netColor,
            bgAlpha = (42 * alpha).toInt(),
            borderAlpha = (110 * alpha).toInt(),
            textAlpha = alphaInt,
            density = density,
            textPaint = textPaint
        )
        hudFirewallPillRect.set(netRect)
        curX = netRect.right + pillGap

        // Permission pills (CAM, MIC, LOC, CNT, STRG) fitting within pillsRightLimit
        for (flag in telemetry.profile.flags) {
            if (flag == "NET") continue
            textPaint.textSize = 7.6f * density
            val estW = 19f * density + textPaint.measureText(flag)
            if (curX + estW > pillsRightLimit) break
            val pRect = LightspeedHudVectorIcons.drawVectorPill(
                canvas = canvas,
                left = curX,
                top = pillY,
                height = pillH,
                label = flag,
                iconKind = flag,
                pillColor = accent,
                bgAlpha = (36 * alpha).toInt(),
                borderAlpha = (95 * alpha).toInt(),
                textAlpha = alphaInt,
                density = density,
                textPaint = textPaint
            )
            curX = pRect.right + pillGap
        }
    } else {
        privacyScoreInfoRect.setEmpty()
        hudAlertsPillRect.setEmpty()
        hudFirewallPillRect.setEmpty()
        // Pinned / Unfocused state: cleanly display Sector Number out of total sectors + App Count + Sector Name
        val currentSector = (activeCatIndex + 1).coerceAtLeast(1).toString().padStart(2, '0')
        val totalSectors = cachedCategories.size.coerceAtLeast(1).toString().padStart(2, '0')
        val appCount = if (currentLayer != CruiseLayer.CATEGORY && cachedApps.isNotEmpty()) {
            cachedApps.size
        } else {
            categoryAppsCache[catId]?.size ?: dataBridge.getAppsForCategory(catId).also { categoryAppsCache[catId] = it }.size
        }
        val sectorHeader = "SECTOR $currentSector / $totalSectors · $appCount ${if (appCount == 1) "APP" else "APPS"}"
        val sectorName = catLabel.uppercase()

        val isCompact = bounds.height() < 64f * density
        val defaultTitleSp = if (isCompact) 15.0f else 18.0f
        val defaultSubSp = if (isCompact) 10.0f else 11.2f
        val titleSizePx = (if (titleFontSizeSp > 0) titleFontSizeSp.toFloat() else defaultTitleSp) * density
        val subSizePx = (if (subtitleFontSizeSp > 0) subtitleFontSizeSp.toFloat() else defaultSubSp) * density

        val line1Y = bounds.centerY() - (titleSizePx * 0.35f)
        val line2Y = line1Y + titleSizePx + 3f * density

        textPaint.setShadowLayer(4.5f * density, 0f, 1.5f * density, shadowColor)
        textPaint.textAlign = Paint.Align.CENTER
        textPaint.typeface = subTypeface
        textPaint.textSize = subSizePx
        textPaint.color = accent
        textPaint.alpha = (240 * alpha).toInt().coerceIn(0, 255)
        canvas.drawText(sectorHeader, bounds.centerX(), line1Y, textPaint)

        textPaint.typeface = titleTypeface
        textPaint.textSize = titleSizePx
        textPaint.color = Color.WHITE
        textPaint.alpha = (255 * alpha).toInt().coerceIn(0, 255)
        canvas.drawText(sectorName, bounds.centerX(), line2Y, textPaint)
        textPaint.clearShadowLayer()
    }
}

private fun ellipsizeText(text: String, paint: Paint, maxWidth: Float): String {
    if (paint.measureText(text) <= maxWidth) return text
    var end = text.length
    while (end > 1 && paint.measureText(text.substring(0, end) + "..") > maxWidth) {
        end--
    }
    return text.substring(0, end) + ".."
}

internal fun LightspeedCruiseOverlay.drawAndroidWidgetCardPlaceholder(
    canvas: Canvas,
    bounds: RectF,
    catId: String,
    catLabel: String,
    accent: Int,
    density: Float,
    alpha: Float,
    slotIndex: Int = 1,
    boundWidgetId: Int = -1
) {
    val idKey = if (slotIndex == 2) LightspeedPreferences.KEY_CATEGORY_HUD_SLOT2_WIDGET_ID_PREFIX + catId else LightspeedPreferences.KEY_CATEGORY_HUD_WIDGET_ID_PREFIX + catId
    val prefWidgetId = prefs().getInt(idKey, -1)
    val widgetId = if (boundWidgetId > 0) boundWidgetId else prefWidgetId

    if (widgetId <= 0) {
        textPaint.textAlign = Paint.Align.CENTER
        textPaint.typeface = Typeface.DEFAULT_BOLD
        textPaint.textSize = 12f * density
        textPaint.color = Color.WHITE
        textPaint.alpha = (255 * alpha).toInt().coerceIn(0, 255)
        val slotTag = if (slotIndex == 2) "SLOT 2" else "SLOT 1"
        canvas.drawText("${catLabel.uppercase()} · ANDROID WIDGET [$slotTag]", bounds.centerX(), bounds.centerY() - 7f * density, textPaint)

        textPaint.typeface = Typeface.DEFAULT
        textPaint.textSize = 9.5f * density
        textPaint.color = accent
        textPaint.alpha = (230 * alpha).toInt().coerceIn(0, 255)
        canvas.drawText("TAP TO BIND ANDROID WIDGET (DUOLINGO, CALENDAR, CLOCK)", bounds.centerX(), bounds.centerY() + 11f * density, textPaint)
        return
    }

    if (isAppWidgetReadyAndVisible(widgetId)) return

    // Hacker-style cascading columns of rotating digits while 3rd-party AppWidget settles/loads
    val now = android.os.SystemClock.uptimeMillis()
    val widgetOwnLabel = if (widgetId > 0) {
        widgetLabelByIdCache[widgetId]?.ifEmpty { null } ?: run {
            val resolved = try {
                android.appwidget.AppWidgetManager.getInstance(context)
                    .getAppWidgetInfo(widgetId)
                    ?.loadLabel(context.packageManager)
                    ?.toString()
                    ?.trim()
                    .orEmpty()
            } catch (_: Exception) {
                ""
            }
            if (resolved.isNotEmpty()) {
                widgetLabelByIdCache[widgetId] = resolved
                resolved
            } else {
                null
            }
        }
    } else null
    val labelKey = if (slotIndex == 2) LightspeedPreferences.KEY_CATEGORY_HUD_SLOT2_WIDGET_LABEL_PREFIX + catId
    else LightspeedPreferences.KEY_CATEGORY_HUD_WIDGET_LABEL_PREFIX + catId
    val rawLabel = widgetOwnLabel ?: prefs().getString(labelKey, null)?.takeIf { it.isNotBlank() } ?: catLabel
    val displayTitle = rawLabel.uppercase(Locale.US).take(22)

    val inset = 4f * density
    val saveCount = canvas.save()
    canvas.clipRect(bounds.left + inset, bounds.top + inset, bounds.right - inset, bounds.bottom - inset)
    try {
        val innerPad = 10f * density
        val usableW = (bounds.width() - 2f * innerPad).coerceAtLeast(24f * density)
        val colStep = 14f * density
        val colCount = (usableW / colStep).toInt().coerceIn(4, 26)
        val actualStep = usableW / colCount
        val startX = bounds.left + innerPad + actualStep * 0.5f

        val rowStep = 12.5f * density
        val rowCount = (bounds.height() / rowStep).toInt().coerceIn(2, 16) + 1
        val cycleSpan = rowCount + 5

        val accR = Color.red(accent)
        val accG = Color.green(accent)
        val accB = Color.blue(accent)

        textPaint.textAlign = Paint.Align.CENTER
        textPaint.typeface = hackerMonoTypeface
        textPaint.textSize = 10.5f * density

        for (c in 0 until colCount) {
            val speedRowsPerSec = 3.6f + ((c * 37 + widgetId * 13) and 0x0F) * 0.42f
            val offsetRows = ((c * 53 + widgetId * 19) % 97) * 0.37f
            val streamPos = (now / 1000f) * speedRowsPerSec + offsetRows
            val headStep = kotlin.math.floor(streamPos).toInt()
            val fracY = streamPos - kotlin.math.floor(streamPos)
            val headRow = ((headStep % cycleSpan) + cycleSpan) % cycleSpan
            val trailLen = 4 + ((c * 11 + widgetId) and 0x03)
            val flipBucket = (now / (60L + (c and 0x03) * 22L)).toInt()
            val colX = startX + c * actualStep

            for (r in -1..rowCount) {
                val y = bounds.top + (r + fracY) * rowStep + 9f * density
                if (y < bounds.top - rowStep || y > bounds.bottom + rowStep) continue

                val rowMod = ((r % cycleSpan) + cycleSpan) % cycleSpan
                val distBehind = ((headRow - rowMod) + cycleSpan) % cycleSpan
                val glyphIdx = ((c * 31 + r * 17 + flipBucket + widgetId) and 0x7FFFFFFF) % hackerGlyphs.size
                val glyph = hackerGlyphs[glyphIdx]

                when {
                    distBehind == 0 -> {
                        textPaint.color = Color.WHITE
                        textPaint.alpha = (250 * alpha).toInt().coerceIn(0, 255)
                    }
                    distBehind <= trailLen -> {
                        val fade = 1f - (distBehind.toFloat() / (trailLen + 1f))
                        textPaint.color = accent
                        textPaint.alpha = ((45f + 195f * fade) * alpha).toInt().coerceIn(0, 255)
                    }
                    else -> {
                        textPaint.color = Color.argb(255, accR, accG, accB)
                        textPaint.alpha = (26 * alpha).toInt().coerceIn(0, 255)
                    }
                }
                canvas.drawText(glyph, colX, y, textPaint)
            }
        }

        // Frosted floating pill identifying the loading widget cleanly over the digit streams
        textPaint.typeface = Typeface.DEFAULT_BOLD
        textPaint.textSize = 10.5f * density
        var pillTitle = displayTitle
        val maxTitleW = (bounds.width() * 0.84f - 22f * density).coerceAtLeast(16f * density)
        while (pillTitle.length > 3 && textPaint.measureText(pillTitle) > maxTitleW) {
            pillTitle = pillTitle.dropLast(1)
        }
        val titleWidth = textPaint.measureText(pillTitle)
        val pillHalfW = (titleWidth * 0.5f + 16f * density).coerceAtMost(bounds.width() * 0.46f)
        val pillHalfH = 12f * density
        val pLeft = bounds.centerX() - pillHalfW
        val pRight = bounds.centerX() + pillHalfW
        val pTop = bounds.centerY() - pillHalfH
        val pBottom = bounds.centerY() + pillHalfH

        highlightPaint.style = Paint.Style.FILL
        highlightPaint.color = Color.argb((215 * alpha).toInt().coerceIn(0, 255), 8, 12, 20)
        canvas.drawRoundRect(pLeft, pTop, pRight, pBottom, 8f * density, 8f * density, highlightPaint)

        highlightPaint.style = Paint.Style.STROKE
        highlightPaint.strokeWidth = 0.9f * density
        highlightPaint.color = Color.argb((120 * alpha).toInt().coerceIn(0, 255), accR, accG, accB)
        canvas.drawRoundRect(pLeft, pTop, pRight, pBottom, 8f * density, 8f * density, highlightPaint)

        val pulse = 0.5f + 0.5f * kotlin.math.sin((now % 700L) / 700f * 6.2831855f)
        highlightPaint.style = Paint.Style.FILL
        highlightPaint.color = Color.argb((255 * alpha * pulse).toInt().coerceIn(40, 255), accR, accG, accB)
        canvas.drawCircle(pLeft + 8.5f * density, bounds.centerY(), 2.5f * density, highlightPaint)

        textPaint.textAlign = Paint.Align.CENTER
        textPaint.color = Color.WHITE
        textPaint.alpha = (240 * alpha).toInt().coerceIn(0, 255)
        canvas.drawText(pillTitle, bounds.centerX() + 4f * density, bounds.centerY() + 3.8f * density, textPaint)
    } finally {
        canvas.restoreToCount(saveCount)
    }
}

internal fun LightspeedCruiseOverlay.drawWidgetChipHeader(
    canvas: Canvas,
    bounds: RectF,
    widgetTitle: String,
    primaryChip: String,
    secondaryChip: String? = null,
    accent: Int,
    density: Float,
    alpha: Float,
    showInfoBadge: Boolean = false,
    slotIndex: Int = -1
) {
    val chipH = 20f * density
    val chipY = bounds.top + 7f * density
    val chipPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        typeface = Typeface.DEFAULT_BOLD
        textSize = 9.0f * density
        color = Color.WHITE
    }
    val titleLeft = bounds.left + 14f * density
    val isCompactCard = bounds.width() < 275f * density
    val primaryLabel = if (isCompactCard && primaryChip.contains(": ")) {
        primaryChip.substringAfter(": ")
    } else {
        primaryChip
    }
    val secondaryLabel = secondaryChip?.let {
        if (isCompactCard && it.contains(": ")) it.substringAfter(": ") else it
    }

    var nextRight = bounds.right - 12f * density

    if (showInfoBadge) {
        val infoW = 20f * density
        val infoRect = RectF(nextRight - infoW, chipY, nextRight, chipY + chipH)
        screenTimeInfoBubbleRect.set(infoRect)
        if (slotIndex > 0) vitalsRectForSlot(hudChipInfoRects, slotIndex).set(infoRect)
        chipPaint.style = Paint.Style.FILL
        chipPaint.color = Color.argb((45 * alpha).toInt(), Color.red(accent), Color.green(accent), Color.blue(accent))
        canvas.drawRoundRect(infoRect, 5f * density, 5f * density, chipPaint)
        chipPaint.style = Paint.Style.STROKE
        chipPaint.strokeWidth = 1f * density
        chipPaint.color = Color.argb((110 * alpha).toInt(), Color.red(accent), Color.green(accent), Color.blue(accent))
        canvas.drawRoundRect(infoRect, 5f * density, 5f * density, chipPaint)

        labelPaint.color = Color.WHITE
        canvas.drawText("!", infoRect.centerX(), infoRect.centerY() + 3.2f * density, labelPaint)

        nextRight -= (infoW + 5f * density)
    } else if (slotIndex > 0) {
        vitalsRectForSlot(hudChipInfoRects, slotIndex).setEmpty()
    }

    val chipPad = 12f * density
    val primaryChipW = (labelPaint.measureText(primaryLabel) + chipPad).coerceIn(48f * density, 90f * density)
    val primaryRect = RectF(nextRight - primaryChipW, chipY, nextRight, chipY + chipH)
    if (slotIndex > 0) vitalsRectForSlot(hudChipPrimaryRects, slotIndex).set(primaryRect)
    chipPaint.style = Paint.Style.FILL
    chipPaint.color = Color.argb((45 * alpha).toInt(), Color.red(accent), Color.green(accent), Color.blue(accent))
    canvas.drawRoundRect(primaryRect, 5f * density, 5f * density, chipPaint)
    chipPaint.style = Paint.Style.STROKE
    chipPaint.strokeWidth = 0.8f * density
    chipPaint.color = Color.argb((90 * alpha).toInt(), Color.red(accent), Color.green(accent), Color.blue(accent))
    canvas.drawRoundRect(primaryRect, 5f * density, 5f * density, chipPaint)
    canvas.drawText(primaryLabel, primaryRect.centerX(), primaryRect.centerY() + 3.2f * density, labelPaint)

    nextRight = primaryRect.left - 5f * density

    var drewSecondary = false
    if (!secondaryLabel.isNullOrBlank()) {
        val secondaryChipW = (labelPaint.measureText(secondaryLabel) + chipPad).coerceIn(44f * density, 78f * density)
        if (nextRight - secondaryChipW - titleLeft >= 54f * density) {
            val secondaryRect = RectF(nextRight - secondaryChipW, chipY, nextRight, chipY + chipH)
            if (slotIndex > 0) vitalsRectForSlot(hudChipSecondaryRects, slotIndex).set(secondaryRect)
            chipPaint.style = Paint.Style.FILL
            chipPaint.color = Color.argb((40 * alpha).toInt(), 255, 255, 255)
            canvas.drawRoundRect(secondaryRect, 5f * density, 5f * density, chipPaint)
            chipPaint.style = Paint.Style.STROKE
            chipPaint.strokeWidth = 0.8f * density
            chipPaint.color = Color.argb((70 * alpha).toInt(), 255, 255, 255)
            canvas.drawRoundRect(secondaryRect, 5f * density, 5f * density, chipPaint)
            canvas.drawText(secondaryLabel, secondaryRect.centerX(), secondaryRect.centerY() + 3.2f * density, labelPaint)
            nextRight = secondaryRect.left - 6f * density
            drewSecondary = true
        }
    }
    if (!drewSecondary && slotIndex > 0) {
        vitalsRectForSlot(hudChipSecondaryRects, slotIndex).setEmpty()
    }

    val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.LEFT
        typeface = Typeface.DEFAULT_BOLD
        textSize = if (isCompactCard) 10.2f * density else 11.2f * density
        color = Color.WHITE
        this.alpha = (255 * alpha).toInt().coerceIn(0, 255)
    }
    val maxTitleW = (nextRight - titleLeft).coerceAtLeast(16f * density)
    val fittedTitle = android.text.TextUtils.ellipsize(
        widgetTitle,
        android.text.TextPaint(titlePaint),
        maxTitleW,
        android.text.TextUtils.TruncateAt.END
    ).toString()
    canvas.drawText(fittedTitle, titleLeft, chipY + 14f * density, titlePaint)
}

internal fun LightspeedCruiseOverlay.drawPrivacyScoreInfoBubble(canvas: Canvas) {
    val dp = resources.displayMetrics.density
    val snapshot = com.sbf.lightspeed.settings.DeckThemeManager.getSnapshot(context)
    val accent = ensureHighContrastColor(categoryAccentOverride() ?: snapshot.tintArgb.toInt())
    val screenW = width.toFloat()
    val bubbleW = (306f * dp).coerceAtMost(screenW - 28f * dp)
    val bubbleH = 140f * dp
    val anchor = if (isPinnedAppMenuOpen() && !pinnedMenuPrivacyInfoRect.isEmpty) {
        pinnedMenuPrivacyInfoRect
    } else {
        privacyScoreInfoRect
    }
    val left = if (!anchor.isEmpty) {
        (anchor.centerX() - bubbleW * 0.25f).coerceIn(14f * dp, screenW - bubbleW - 14f * dp)
    } else {
        (screenW - bubbleW) / 2f
    }
    val top = if (!anchor.isEmpty) {
        val belowTop = anchor.bottom + 8f * dp
        val maxBottomTop = height.toFloat() - bubbleH - 16f * dp
        if (belowTop <= maxBottomTop) {
            belowTop
        } else {
            (anchor.top - bubbleH - 8f * dp).coerceAtLeast(16f * dp)
        }
    } else {
        getSafeHudTopOffset() + 80f * dp
    }
    val rect = RectF(left, top, left + bubbleW, top + bubbleH)

    highlightPaint.style = Paint.Style.FILL
    highlightPaint.color = Color.argb(245, 12, 16, 26)
    canvas.drawRoundRect(rect, 12f * dp, 12f * dp, highlightPaint)
    highlightPaint.style = Paint.Style.STROKE
    highlightPaint.strokeWidth = 1.3f * dp
    highlightPaint.color = accent
    canvas.drawRoundRect(rect, 12f * dp, 12f * dp, highlightPaint)

    val inspectPkg = (if (isPinnedAppMenuOpen()) pinnedMenuApp else activeItem)?.packageName.orEmpty()
    val liveProfile = if (inspectPkg.isNotBlank()) {
        LightspeedCruiseHudPods.getAppPrivacyProfile(context, inspectPkg)
    } else {
        null
    }
    val liveSummaryLine = when {
        liveProfile == null -> "Counts GRANTED permissions only (denied = 0)."
        liveProfile.score < 0 -> "Package permission info unavailable."
        !liveProfile.hasInternet ->
            "Offline app (0.5x local risk) · ${liveProfile.grantedCount}/${liveProfile.requestedSensitiveCount} granted"
        liveProfile.isFirewallEnforced ->
            "Firewall enforced (0.5x risk) · ${liveProfile.grantedCount}/${liveProfile.requestedSensitiveCount} granted"
        else ->
            "Active grants: ${liveProfile.grantedCount}/${liveProfile.requestedSensitiveCount} sensitive (denied = 0)"
    }

    val pX = left + 14f * dp
    val maxLineW = (bubbleW - 28f * dp).coerceAtLeast(120f * dp)
    var cY = top + 20f * dp
    textPaint.textAlign = Paint.Align.LEFT
    textPaint.typeface = Typeface.DEFAULT_BOLD
    textPaint.textSize = 10.5f * dp
    textPaint.color = accent
    canvas.drawText("SMART PRIVACY SCORE", pX, cY, textPaint)

    cY += 15f * dp
    textPaint.typeface = Typeface.DEFAULT
    textPaint.textSize = 8.9f * dp
    textPaint.color = Color.WHITE
    canvas.drawText(ellipsizeText(liveSummaryLine, textPaint, maxLineW), pX, cY, textPaint)

    cY += 14f * dp
    textPaint.textSize = 8.2f * dp
    textPaint.color = Color.argb(205, 195, 212, 235)
    canvas.drawText(
        ellipsizeText("Starts 100 · Offline or blocked local risks count 0.5x", textPaint, maxLineW),
        pX,
        cY,
        textPaint
    )

    cY += 15f * dp
    textPaint.typeface = Typeface.MONOSPACE
    textPaint.textSize = 8.0f * dp
    textPaint.color = Color.argb(225, 210, 224, 245)
    canvas.drawText("-15 NET (-4 FW)  -25 LOC+  -15 LOC", pX, cY, textPaint)
    cY += 13f * dp
    canvas.drawText("-18 MIC/SMS  -15 CALL  -14 CNT  -12 CAM", pX, cY, textPaint)
    cY += 13f * dp
    canvas.drawText("-10 STRG/APPS  -8 CAL/BODY  -6 AD", pX, cY, textPaint)

    cY += 15f * dp
    textPaint.typeface = Typeface.DEFAULT
    textPaint.textSize = 8.4f * dp
    textPaint.color = Color.argb(195, 180, 200, 225)
    canvas.drawText("80+ High · 55-79 Moderate · <55 Broad Access", pX, cY, textPaint)

    cY += 15f * dp
    textPaint.textAlign = Paint.Align.CENTER
    textPaint.typeface = Typeface.DEFAULT_BOLD
    textPaint.textSize = 8.4f * dp
    textPaint.color = accent
    canvas.drawText("Tap anywhere to dismiss", rect.centerX(), cY, textPaint)
}
