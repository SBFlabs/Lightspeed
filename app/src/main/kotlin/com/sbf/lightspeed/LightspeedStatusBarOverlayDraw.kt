package com.sbf.lightspeed

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.os.Build
import android.os.SystemClock
import com.sbf.lightspeed.system.LightspeedHudRenderer

internal data class HorizonStream(
    val type: String, // "dl" or "media"
    val title: String,
    val subtitle: String,
    val progressFraction: Float,
    val iconColor: Int?,
    val coverArtColor: Int? = null,
    val isIndeterminate: Boolean = false,
    val lastUpdated: Long = System.currentTimeMillis(),
    val album: String = "",
    val positionMs: Long = 0L,
    val durationMs: Long = 0L
)

internal data class HorizonRailLayout(
    val railLeft: Float,
    val railRight: Float,
    val railSpanPxActual: Float,
    val lineYs: FloatArray,
    val thicknesses: FloatArray,
    val progressXs: FloatArray,
    val activeStreams: List<HorizonStream>,
    val primaryColor: Int,
    val sensorHeight: Float,
    val gapDp: Float
)

internal fun LightspeedStatusBarOverlay.handleDraw(canvas: Canvas, superCall: () -> Unit) {
    superCall()
    val d = resources.displayMetrics.density
    val screenW = resources.displayMetrics.widthPixels.toFloat()
    val h = height.toFloat()

    val orientation = resources.configuration.orientation
    val isLandscape = orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE

    val m3Primary = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        context.resources.getColor(android.R.color.system_accent1_300, context.theme)
    } else {
        Color.parseColor("#90CAF9")
    }

    // 1. Sensor Debug Area & Transparency Guard
    drawSensorDebugArea(canvas, d, screenW, m3Primary)

    // 2. Horizon Rail Telemetry Line & Micro-Text Renderer
    val streams = collectHorizonStreams(isLandscape)
    if (streams.isNotEmpty()) {
        val layout = computeRailLayout(streams, d, screenW, isLandscape, m3Primary)
        drawHorizonRail(canvas, layout, d, m3Primary)
        drawTelemetryTicker(canvas, layout, d, screenW, h, isLandscape)
    }

    val activeMedia = com.sbf.lightspeed.system.LightspeedNotificationListener.activeMediaTelemetry
    if (activeMedia?.isPlaying == true) {
        postInvalidateDelayed(1000)
    }

    // 3. Hardware Gear Set HUD Navigation & Transient Action HUD Renderer
    drawScrubHud(canvas, d, screenW, m3Primary)
}

// -------------------------------------------------------------------------------------------------
// 1. Sensor Area Sub-Renderer
// -------------------------------------------------------------------------------------------------
internal fun LightspeedStatusBarOverlay.drawSensorDebugArea(
    canvas: Canvas,
    d: Float,
    screenW: Float,
    m3Primary: Int
) {
    val isSensorEnabled = renderCache.isSensorEnabled
    val isExpanded = renderCache.isExpanded
    val isPreview  = renderCache.isPreview
    val isReview = isSensorEnabled && isPreview
    val transparencyPct = renderCache.transparencyPct

    val spanPref = renderCache.spanPref
    val spanPx = if (spanPref >= 1000) screenW else (spanPref * d).coerceIn(50f * d, screenW)
    val sensorOffsetX = renderCache.sensorOffsetX * d
    val thicknessDp = renderCache.thicknessDp
    val sensorHeight = thicknessDp * d
    val sensorOffsetY = (renderCache.sensorOffsetY * d).coerceAtLeast(0f)

    val sensorLeft = ((screenW - spanPx) / 2f) + sensorOffsetX
    val sensorRight = sensorLeft + spanPx
    val sensorTop = sensorOffsetY
    val sensorBottom = sensorTop + sensorHeight

    if (isReview) {
        debugPaint.style = Paint.Style.FILL
        debugPaint.color = Color.argb(120, Color.red(m3Primary), Color.green(m3Primary), Color.blue(m3Primary))
        canvas.drawRoundRect(RectF(sensorLeft, sensorTop, sensorRight, sensorBottom), 8f * d, 8f * d, debugPaint)

        debugPaint.style = Paint.Style.STROKE
        debugPaint.strokeWidth = 2f * d
        debugPaint.color = Color.WHITE
        canvas.drawRoundRect(RectF(sensorLeft + 1f * d, sensorTop + 1f * d, sensorRight - 1f * d, sensorBottom - 1f * d), 8f * d, 8f * d, debugPaint)

        hudTextPaint.textSize = 10f * d
        hudTextPaint.color = Color.WHITE
        canvas.drawText("✦ SENSOR AREA (TOP EDGE)", sensorLeft + (spanPx / 2f), sensorTop + (sensorHeight / 2f) + 3.5f * d, hudTextPaint)
    } else if (transparencyPct > 0) {
        val alpha = (transparencyPct * 2.55f).toInt().coerceIn(10, 255)
        debugPaint.style = Paint.Style.FILL
        debugPaint.color = Color.argb((alpha * 0.4f).toInt(), Color.red(m3Primary), Color.green(m3Primary), Color.blue(m3Primary))
        canvas.drawRoundRect(RectF(sensorLeft, sensorTop, sensorRight, sensorTop + 4f * d), 2f * d, 2f * d, debugPaint)

        debugPaint.color = Color.argb(alpha, Color.red(m3Primary), Color.green(m3Primary), Color.blue(m3Primary))
        canvas.drawRoundRect(RectF(sensorLeft, sensorTop, sensorRight, sensorTop + 2.5f * d), 1.5f * d, 1.5f * d, debugPaint)
    }
}

// -------------------------------------------------------------------------------------------------
// Horizon Stream Collection & Layout Helpers
// -------------------------------------------------------------------------------------------------
private fun LightspeedStatusBarOverlay.collectHorizonStreams(
    isLandscape: Boolean
): List<HorizonStream> {
    val railOrientMode = renderCache.railOrientMode
    val isRailAllowedByOrientation = when (railOrientMode) {
        "landscape_only" -> isLandscape
        "portrait_only" -> !isLandscape
        else -> true
    }
    if (!isRailAllowedByOrientation) return emptyList()

    val dlRouting = renderCache.dlRouting
    val mediaRouting = renderCache.mediaRouting
    val media = com.sbf.lightspeed.system.LightspeedNotificationListener.activeMediaTelemetry
        ?: com.sbf.lightspeed.system.LightspeedMediaManager.getActiveTrackInfo(context).takeIf { it.title.isNotBlank() || it.isPlaying }?.let { info ->
            com.sbf.lightspeed.system.LightspeedNotificationListener.MediaTelemetry(
                title = info.title,
                artist = info.artist,
                isPlaying = info.isPlaying,
                positionMs = info.positionMs,
                durationMs = info.durationMs,
                packageName = info.packageName,
                iconColor = com.sbf.lightspeed.system.AppIconColorExtractor.extractColor(context, info.packageName, 0),
                coverArtColor = info.coverArtColor,
                album = info.album
            )
        }

    val streams = mutableListOf<HorizonStream>()
    val allDownloads = com.sbf.lightspeed.system.LightspeedNotificationListener.getActiveDownloadsList()
    val isDlRouteEnabled = (dlRouting == "top_line" || dlRouting == "both")
    val isMediaRouteEnabled = (mediaRouting == "top_line" || mediaRouting == "both")

    if (isDlRouteEnabled) {
        for (dl in allDownloads) {
            if (dl.progressFraction >= 0f || dl.isIndeterminate) {
                val pctStr = if (dl.progressFraction >= 0f) "${(dl.progressFraction * 100).toInt()}%" else "DOWNLOADING"
                streams.add(
                    HorizonStream(
                        type = "dl",
                        title = dl.title,
                        subtitle = pctStr,
                        progressFraction = if (dl.progressFraction >= 0f) dl.progressFraction.coerceIn(0f, 1f) else 1f,
                        iconColor = dl.iconColor,
                        coverArtColor = null,
                        isIndeterminate = dl.isIndeterminate,
                        lastUpdated = dl.lastUpdated
                    )
                )
            }
        }
    }

    if (isMediaRouteEnabled && media != null && media.isPlaying && (media.durationMs > 0 || media.title.isNotBlank())) {
        val prog = if (media.durationMs > 0) (media.positionMs.toFloat() / media.durationMs.toFloat()).coerceIn(0f, 1f) else 0.5f
        streams.add(
            HorizonStream(
                type = "media",
                title = media.title,
                subtitle = media.artist,
                progressFraction = prog,
                iconColor = media.iconColor,
                coverArtColor = media.coverArtColor,
                isIndeterminate = false,
                lastUpdated = System.currentTimeMillis(),
                album = media.album,
                positionMs = media.positionMs,
                durationMs = media.durationMs
            )
        )
    }

    val isRailPreviewActive = renderCache.isRailPreviewActive
    val maxRails = renderCache.maxRails

    if (streams.isEmpty() && isRailPreviewActive) {
        if (isDlRouteEnabled || (!isDlRouteEnabled && !isMediaRouteEnabled)) {
            streams.add(
                HorizonStream(
                    type = "dl",
                    title = "NIGHTLY_BUILD_V10.APK",
                    subtitle = "68%",
                    progressFraction = 0.68f,
                    iconColor = Color.parseColor("#00E5FF"),
                    coverArtColor = null
                )
            )
        }
        if (isMediaRouteEnabled || (!isDlRouteEnabled && !isMediaRouteEnabled)) {
            streams.add(
                HorizonStream(
                    type = "media",
                    title = "SYNTHWAVE HORIZON",
                    subtitle = "LIGHTSPEED SOUNDS",
                    progressFraction = 0.42f,
                    iconColor = Color.parseColor("#FF007F"),
                    coverArtColor = Color.parseColor("#FF007F"),
                    album = "RETROWAVE PODCAST EP. 42",
                    positionMs = 154000L,
                    durationMs = 360000L
                )
            )
        }
        if (maxRails >= 3) {
            streams.add(
                HorizonStream(
                    type = "dl",
                    title = "SYSTEM_CACHE_BACKUP.ZIP",
                    subtitle = "91%",
                    progressFraction = 0.91f,
                    iconColor = Color.parseColor("#00E676"),
                    coverArtColor = null
                )
            )
        }
    }

    val railPriority = renderCache.railPriority
    if (streams.size > 1) {
        when (railPriority) {
            "downloads_top" -> streams.sortBy { if (it.type == "dl") 0 else 1 }
            "media_top" -> streams.sortBy { if (it.type == "media") 0 else 1 }
            "most_recent" -> streams.sortByDescending { it.lastUpdated }
        }
    }

    return streams.take(maxRails)
}

private fun LightspeedStatusBarOverlay.resolveStreamColor(
    index: Int,
    stream: HorizonStream,
    colorMode: String,
    m3Primary: Int
): Int {
    val baseColor = when (colorMode) {
        "cover_art" -> if (stream.type == "media") (stream.coverArtColor ?: stream.iconColor ?: m3Primary) else (stream.iconColor ?: m3Primary)
        "app_icon" -> stream.iconColor ?: m3Primary
        "material3" -> m3Primary
        "inverted" -> {
            val isNight = (resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK) == android.content.res.Configuration.UI_MODE_NIGHT_YES
            if (isNight) Color.WHITE else Color.BLACK
        }
        "custom" -> {
            val hex = renderCache.customColorHex
            try { Color.parseColor(hex) } catch (_: Exception) { Color.parseColor("#00E5FF") }
        }
        else -> m3Primary
    }
    if (index == 0) return baseColor
    val factor = when (index) {
        1 -> 0.82f
        else -> 0.68f
    }
    val r = (Color.red(baseColor) * factor).toInt().coerceIn(0, 255)
    val g = (Color.green(baseColor) * factor).toInt().coerceIn(0, 255)
    val b = (Color.blue(baseColor) * factor).toInt().coerceIn(0, 255)
    return Color.argb(Color.alpha(baseColor), r, g, b)
}

private fun LightspeedStatusBarOverlay.computeRailLayout(
    activeStreams: List<HorizonStream>,
    d: Float,
    screenW: Float,
    isLandscape: Boolean,
    m3Primary: Int
): HorizonRailLayout {
    val screenWidthDp = (screenW / d).toInt()
    val portraitDimDp = (minOf(resources.displayMetrics.widthPixels, resources.displayMetrics.heightPixels) / d).toInt()
    val railSpanPref = renderCache.railSpanPref
    val isFullWidth = railSpanPref >= 999 || railSpanPref >= (screenWidthDp - 15) || railSpanPref >= (portraitDimDp - 15)

    val safePaddingLeftDp = renderCache.safePaddingLeftDp
    val safePaddingRightDp = renderCache.safePaddingRightDp
    val safeInsetLeft = safePaddingLeftDp * d
    val safeInsetRight = safePaddingRightDp * d

    val (railLeft, railRight) = if (isFullWidth) {
        Pair(safeInsetLeft, screenW - safeInsetRight)
    } else {
        val railSpanPx = (railSpanPref * d).coerceIn(50f * d, screenW)
        val railAlign = renderCache.railAlign
        val railOffsetX = renderCache.railOffsetX * d
        val availableScreenLeft = safeInsetLeft
        val availableScreenRight = (screenW - safeInsetRight).coerceAtLeast(availableScreenLeft + 20f * d)

        val baseRailLeft = when (railAlign) {
            "left" -> (availableScreenLeft + railOffsetX).coerceIn(availableScreenLeft, (availableScreenRight - railSpanPx).coerceAtLeast(availableScreenLeft))
            "right" -> (availableScreenRight - railSpanPx + railOffsetX).coerceIn(availableScreenLeft, (availableScreenRight - railSpanPx).coerceAtLeast(availableScreenLeft))
            else -> (((screenW - railSpanPx) / 2f) + railOffsetX).coerceIn(availableScreenLeft, (availableScreenRight - railSpanPx).coerceAtLeast(availableScreenLeft))
        }
        val left = baseRailLeft.coerceIn(availableScreenLeft, (availableScreenRight - 20f * d).coerceAtLeast(availableScreenLeft))
        val right = (left + railSpanPx).coerceAtMost(availableScreenRight)
        Pair(left, right)
    }
    val railSpanPxActual = (railRight - railLeft).coerceAtLeast(20f * d)

    val railOffsetY = renderCache.railOffsetY.toFloat() * d
    val railThicknessDp = renderCache.railThicknessDp
    val stackSpacingDp = renderCache.stackSpacingDp
    val gapDp = stackSpacingDp

    val thicknesses = FloatArray(activeStreams.size) { i ->
        if (i == 0) railThicknessDp.toFloat() else (railThicknessDp * (1f - i * 0.15f)).coerceAtLeast(1.5f)
    }
    val lineYs = FloatArray(activeStreams.size)

    lineYs[0] = railOffsetY + ((thicknesses[0] * d) / 2f)
    for (i in 1 until activeStreams.size) {
        lineYs[i] = lineYs[i - 1] + ((thicknesses[i - 1] * d) / 2f) + (gapDp * d) + ((thicknesses[i] * d) / 2f)
    }

    val progressXs = FloatArray(activeStreams.size) { i ->
        railLeft + (railSpanPxActual * activeStreams[i].progressFraction)
    }

    val colorMode = renderCache.colorMode
    val primaryColor = if (activeStreams.isNotEmpty()) resolveStreamColor(0, activeStreams[0], colorMode, m3Primary) else m3Primary

    val thicknessDp = renderCache.thicknessDp
    val sensorHeight = thicknessDp * d

    return HorizonRailLayout(
        railLeft = railLeft,
        railRight = railRight,
        railSpanPxActual = railSpanPxActual,
        lineYs = lineYs,
        thicknesses = thicknesses,
        progressXs = progressXs,
        activeStreams = activeStreams,
        primaryColor = primaryColor,
        sensorHeight = sensorHeight,
        gapDp = gapDp
    )
}

// -------------------------------------------------------------------------------------------------
// 2. Horizon Rail Sub-Renderer (Tracks, Glow, Progress & Terminals)
// -------------------------------------------------------------------------------------------------
internal fun LightspeedStatusBarOverlay.drawHorizonRail(
    canvas: Canvas,
    layout: HorizonRailLayout,
    d: Float,
    m3Primary: Int
) {
    val isDropShadow = renderCache.isDropShadow
    val railTrackOpacityPct = renderCache.railTrackOpacityPct
    val railGlowPct = renderCache.railGlowPct
    val colorMode = renderCache.colorMode
    val isTerminalCaps = renderCache.isTerminalCaps
    val activeStreams = layout.activeStreams

    // Ambient Drop Shadow & Contrast Trench
    if (isDropShadow) {
        val shadowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeCap = Paint.Cap.ROUND
        }
        activeStreams.forEachIndexed { index, stream ->
            val thickness = layout.thicknesses[index]
            val lineY = layout.lineYs[index]
            val progX = layout.progressXs[index]

            if (railTrackOpacityPct > 0) {
                shadowPaint.strokeWidth = (thickness + 2.2f) * d
                shadowPaint.color = Color.argb(75, 0, 0, 0)
                canvas.drawLine(layout.railLeft, lineY + (1.0f * d), layout.railRight, lineY + (1.0f * d), shadowPaint)
            }

            if (stream.progressFraction > 0f) {
                shadowPaint.strokeWidth = (thickness + 3.0f) * d
                shadowPaint.color = Color.argb(135, 4, 6, 10)
                canvas.drawLine(layout.railLeft, lineY + (1.3f * d), progX, lineY + (1.3f * d), shadowPaint)
            }
        }
    }

    // Tracks, Glow, and Progress
    activeStreams.forEachIndexed { index, stream ->
        val col = resolveStreamColor(index, stream, colorMode, m3Primary)
        val thickness = layout.thicknesses[index]
        val lineY = layout.lineYs[index]
        val progressX = layout.progressXs[index]

        if (railTrackOpacityPct > 0) {
            val trackAlpha = (railTrackOpacityPct * 2.55f).toInt().coerceIn(10, 255)
            telemetryGlowPaint.strokeWidth = thickness * d
            telemetryGlowPaint.color = Color.argb(trackAlpha, Color.red(col), Color.green(col), Color.blue(col))
            canvas.drawLine(layout.railLeft, lineY, layout.railRight, lineY, telemetryGlowPaint)
        }

        if (railGlowPct > 0) {
            val glowAlpha = ((railGlowPct * 1.5f) / (1f + index * 0.3f)).toInt().coerceIn(10, 200)
            telemetryGlowPaint.strokeWidth = (thickness + 2.5f - index * 0.5f) * d
            telemetryGlowPaint.color = Color.argb(glowAlpha, Color.red(col), Color.green(col), Color.blue(col))
            canvas.drawLine(layout.railLeft, lineY, progressX, lineY, telemetryGlowPaint)
        }

        telemetryGlowPaint.strokeWidth = thickness * d
        telemetryGlowPaint.color = col
        canvas.drawLine(layout.railLeft, lineY, progressX, lineY, telemetryGlowPaint)
    }

    // Inter-Rail Contact Micro-Grooves
    if (layout.gapDp <= 1f && activeStreams.size > 1) {
        val seamPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeCap = Paint.Cap.BUTT
            strokeWidth = (0.75f * d).coerceAtLeast(1f)
            color = Color.argb(165, 4, 6, 10)
        }
        for (i in 0 until activeStreams.size - 1) {
            val topBottom = layout.lineYs[i] + (layout.thicknesses[i] * d / 2f)
            val bottomTop = layout.lineYs[i + 1] - (layout.thicknesses[i + 1] * d / 2f)
            val seamY = (topBottom + bottomTop) / 2f
            val maxActiveX = maxOf(layout.progressXs[i], layout.progressXs[i + 1])
            if (maxActiveX > layout.railLeft) {
                canvas.drawLine(layout.railLeft, seamY, maxActiveX, seamY, seamPaint)
            }
        }
    }

    // Tactical Terminal Progress Head Caps
    if (isTerminalCaps) {
        val capPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeCap = Paint.Cap.ROUND
        }
        activeStreams.forEachIndexed { index, stream ->
            val progressX = layout.progressXs[index]
            val thickness = layout.thicknesses[index]
            val lineY = layout.lineYs[index]

            if (stream.progressFraction in 0.01f..0.99f) {
                val halfCapH = ((thickness * d) / 2f) + (1.2f * d)
                capPaint.strokeWidth = (1.8f * d).coerceAtLeast(2f)
                capPaint.color = Color.argb(220, 6, 8, 14)
                canvas.drawLine(progressX, lineY - halfCapH, progressX, lineY + halfCapH, capPaint)

                capPaint.strokeWidth = (1.0f * d).coerceAtLeast(1f)
                capPaint.color = Color.argb(240, 255, 255, 255)
                canvas.drawLine(progressX - 0.75f * d, lineY - halfCapH * 0.7f, progressX - 0.75f * d, lineY + halfCapH * 0.7f, capPaint)
            }
        }
    }
}

// -------------------------------------------------------------------------------------------------
// 3. Telemetry Ticker Sub-Renderer
// -------------------------------------------------------------------------------------------------
internal fun LightspeedStatusBarOverlay.drawTelemetryTicker(
    canvas: Canvas,
    layout: HorizonRailLayout,
    d: Float,
    screenW: Float,
    h: Float,
    isLandscape: Boolean
) {
    val isTextEnabled = renderCache.isTextEnabled
    val textOrientMode = renderCache.textOrientMode
    val isTextAllowedByOrientation = when (textOrientMode) {
        "landscape_only" -> isLandscape
        "portrait_only" -> !isLandscape
        else -> true
    }
    if (!isTextEnabled || !isTextAllowedByOrientation) return

    val primaryStream = layout.activeStreams[0]
    val textCasing = renderCache.textCasing

    fun applyCasing(str: String): String {
        val trimmed = str.trim()
        if (trimmed.isEmpty()) return ""
        return when (textCasing) {
            "all_caps" -> trimmed.uppercase()
            "title_case" -> trimmed.split(" ").joinToString(" ") { word ->
                word.lowercase().replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }
            }
            else -> trimmed
        }
    }

    val cleanTitle = applyCasing(primaryStream.title)
    val cleanSub = applyCasing(primaryStream.subtitle)
    val cleanAlbum = applyCasing(primaryStream.album)

    val metadataMode = renderCache.metadataMode
    val isShowTimestamp = renderCache.isShowTimestamp

    fun formatDuration(ms: Long): String {
        if (ms <= 0L) return ""
        val totalSec = ms / 1000
        val min = totalSec / 60
        val sec = totalSec % 60
        return "%d:%02d".format(min, sec)
    }

    val timestampStr = if (primaryStream.type == "media" && (isShowTimestamp || (metadataMode == "adaptive" && isLandscape))) {
        val pos = formatDuration(primaryStream.positionMs)
        val dur = formatDuration(primaryStream.durationMs)
        if (pos.isNotBlank() && dur.isNotBlank()) "$pos / $dur" else pos
    } else ""

    val isRichMode = when (metadataMode) {
        "full" -> true
        "title_only" -> false
        else -> isLandscape
    }

    val leftLabel: String
    val rightLabel: String

    if (primaryStream.type == "dl") {
        leftLabel = if (cleanTitle.isNotBlank()) "⬇ $cleanTitle" else "⬇ DOWNLOADING"
        rightLabel = if (cleanSub.isNotBlank()) cleanSub.replace("%", "").trim() + "%" else ""
    } else {
        leftLabel = cleanTitle.ifBlank { cleanSub }.ifBlank { "NOW PLAYING" }
        if (isRichMode) {
            val siders = mutableListOf<String>()
            if (cleanSub.isNotBlank() && !cleanSub.equals(cleanTitle, ignoreCase = true) && !cleanSub.equals("NOW PLAYING", ignoreCase = true)) {
                siders.add(cleanSub)
            }
            if (cleanAlbum.isNotBlank() && !cleanAlbum.equals(cleanTitle, ignoreCase = true) && !cleanAlbum.equals(cleanSub, ignoreCase = true)) {
                siders.add(cleanAlbum)
            }
            if (timestampStr.isNotBlank()) {
                siders.add(timestampStr)
            }
            rightLabel = siders.joinToString("  •  ")
        } else {
            rightLabel = if (cleanSub.isNotBlank() && !cleanSub.equals(cleanTitle, ignoreCase = true) && !cleanSub.equals("NOW PLAYING", ignoreCase = true)) {
                cleanSub
            } else {
                ""
            }
        }
    }

    val tickerText = when {
        leftLabel.isNotBlank() && rightLabel.isNotBlank() -> if (primaryStream.type == "dl") "$leftLabel  •  $rightLabel" else "$leftLabel — $rightLabel"
        leftLabel.isNotBlank() -> leftLabel
        else -> rightLabel
    }

    if (tickerText.isBlank()) return

    val textSizeDp = renderCache.textSizeDp
    val fontSetting = renderCache.fontSetting
    val tacticalTypeface: android.graphics.Typeface = when {
        fontSetting == "system_default" -> android.graphics.Typeface.DEFAULT_BOLD
        fontSetting.startsWith("/") -> {
            try {
                android.graphics.Typeface.createFromFile(java.io.File(fontSetting))
            } catch (_: Exception) {
                android.graphics.Typeface.DEFAULT_BOLD
            }
        }
        else -> {
            try {
                android.graphics.Typeface.create(fontSetting, android.graphics.Typeface.BOLD)
            } catch (_: Exception) {
                android.graphics.Typeface.DEFAULT_BOLD
            }
        }
    }

    val colorMode = renderCache.colorMode
    val microTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = textSizeDp * d
        typeface = tacticalTypeface
        letterSpacing = 0.05f
        color = if (colorMode == "inverted") layout.primaryColor else Color.WHITE
        style = Paint.Style.FILL
    }

    val isContrastShield = renderCache.isContrastShield
    val microTextOutlinePaint = if (isContrastShield) {
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = textSizeDp * d
            typeface = tacticalTypeface
            letterSpacing = 0.05f
            color = Color.argb(235, 6, 8, 14)
            style = Paint.Style.STROKE
            strokeWidth = (textSizeDp * 0.22f * d).coerceIn(1.5f * d, 3.2f * d)
            strokeJoin = Paint.Join.ROUND
            strokeCap = Paint.Cap.ROUND
        }
    } else null

    fun drawTacticalText(text: String, x: Float, y: Float) {
        if (text.isBlank()) return
        if (microTextOutlinePaint != null) {
            canvas.drawText(text, x, y, microTextOutlinePaint)
        }
        canvas.drawText(text, x, y, microTextPaint)
    }

    val textPos = renderCache.textPos
    val textOffsetY = renderCache.textOffsetY.toFloat() * d
    val fontMetrics = microTextPaint.fontMetrics
    val textBaselineOffset = -fontMetrics.ascent

    val textY = when (textPos) {
        "above" -> (layout.lineYs[0] - (layout.thicknesses[0] * d / 2f) - (1.5f * d) - fontMetrics.descent) + textOffsetY
        "embedded" -> (layout.lineYs[0] - (fontMetrics.ascent + fontMetrics.descent) / 2f) + textOffsetY
        "below_statusbar" -> (layout.sensorHeight + (2f * d) + textBaselineOffset) + textOffsetY
        else -> (layout.lineYs[0] + (layout.thicknesses[0] * d / 2f) + (1.5f * d) + textBaselineOffset) + textOffsetY
    }

    val speedDp = renderCache.speedDp
    val speedPx = speedDp * d
    val marqueeAnimMode = renderCache.marqueeAnimMode
    val marqueeDirection = renderCache.marqueeDirection
    val marqueeScope = renderCache.marqueeScope

    val isAvoidCutout = renderCache.isAvoidCutout
    val wingGapDp = renderCache.wingGapDp
    val wingGap = wingGapDp * d

    val rawCutout = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) rootWindowInsets?.displayCutout else null
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P && rawCutout != null) {
        val found = rawCutout.boundingRectTop ?: rawCutout.boundingRects.firstOrNull { it.top == 0 }
        if (found != null && found.width() > 0) {
            LightspeedNotchOverlay.cachedCutoutRect = found
        }
    }
    val topCutout = LightspeedNotchOverlay.cachedCutoutRect
    val defaultCutoutWidthDp = (topCutout?.width()?.toFloat()?.div(d) ?: 20f).toInt().coerceIn(14, 48)
    val cutoutWidthDp = (if (renderCache.customCutoutWidth > 0) renderCache.customCutoutWidth else defaultCutoutWidthDp).coerceIn(0, 72)
    val effectiveCutoutWidthPx = cutoutWidthDp.toFloat() * d

    val notchOffsetX = renderCache.customCutoutOffsetX.toFloat() * d
    val detectedCenterX = if (topCutout != null && topCutout.width() > 0) topCutout.exactCenterX() else screenW / 2f
    val cutoutCenterX = detectedCenterX + notchOffsetX

    val cutoutLeft = cutoutCenterX - (effectiveCutoutWidthPx / 2f)
    val cutoutRight = cutoutCenterX + (effectiveCutoutWidthPx / 2f)
    val isCenteredCutout = cutoutCenterX in (screenW * 0.20f)..(screenW * 0.80f)

    fun renderMarqueeText(
        text: String,
        clipLeft: Float,
        clipRight: Float,
        y: Float,
        alignTo: String = "left",
        forceStatic: Boolean = false
    ) {
        if (text.isBlank()) return
        val availW = (clipRight - clipLeft).coerceAtLeast(0f)
        if (availW <= 4f * d) return

        val textW = microTextPaint.measureText(text)

        if (textW <= availW || forceStatic) {
            val startX = when (alignTo) {
                "right" -> clipRight - textW
                "center" -> clipLeft + (availW - textW) / 2f
                else -> clipLeft
            }
            val safeText = if (textW > availW) {
                android.text.TextUtils.ellipsize(text, android.text.TextPaint(microTextPaint), availW, android.text.TextUtils.TruncateAt.END).toString()
            } else text

            canvas.save()
            canvas.clipRect(clipLeft, 0f, clipRight, h)
            drawTacticalText(safeText, startX.coerceIn(clipLeft, (clipRight - microTextPaint.measureText(safeText)).coerceAtLeast(clipLeft)), y)
            canvas.restore()
            return
        }

        val now = SystemClock.uptimeMillis()
        val speedPxPerMs = speedPx / 1000f

        canvas.save()
        canvas.clipRect(clipLeft, 0f, clipRight, h)

        if (marqueeAnimMode == "bounce") {
            val overflowPx = textW - availW
            val travelDist = overflowPx + (6f * d)
            val pauseMs = 1200L
            val travelDurationMs = ((travelDist / speedPxPerMs).toLong()).coerceIn(800L, 10000L)
            val totalCycleMs = (pauseMs * 2) + (travelDurationMs * 2)
            val cycleTime = now % totalCycleMs

            val offset = when {
                cycleTime < pauseMs -> 0f
                cycleTime < pauseMs + travelDurationMs -> {
                    val progress = (cycleTime - pauseMs).toFloat() / travelDurationMs.toFloat()
                    val eased = (1f - kotlin.math.cos(progress * Math.PI.toFloat())) / 2f
                    eased * travelDist
                }
                cycleTime < (2 * pauseMs) + travelDurationMs -> travelDist
                else -> {
                    val progress = (cycleTime - (2 * pauseMs + travelDurationMs)).toFloat() / travelDurationMs.toFloat()
                    val eased = (1f - kotlin.math.cos(progress * Math.PI.toFloat())) / 2f
                    travelDist * (1f - eased)
                }
            }

            val baseStartX = if (alignTo == "right") (clipRight - textW) else clipLeft
            val startX = if (marqueeDirection == "ltr") {
                baseStartX + offset
            } else {
                baseStartX - offset
            }
            drawTacticalText(text, startX, y)
        } else {
            val gapPx = (30f * d).coerceAtLeast(20f)
            val cycleDist = textW + gapPx
            val cycleDurationMs = ((cycleDist / speedPxPerMs).toLong()).coerceIn(1000L, 30000L)
            val elapsedMs = now % cycleDurationMs
            val rawOffset = (elapsedMs.toFloat() / cycleDurationMs.toFloat()) * cycleDist

            if (marqueeDirection == "ltr") {
                var x = clipLeft - textW + rawOffset
                while (x < clipRight) {
                    if (x + textW > clipLeft) {
                        drawTacticalText(text, x, y)
                    }
                    x += cycleDist
                }
            } else {
                var x = clipRight - rawOffset
                while (x + textW > clipLeft) {
                    if (x < clipRight) {
                        drawTacticalText(text, x, y)
                    }
                    x -= cycleDist
                }
                var forwardX = clipRight - rawOffset + cycleDist
                while (forwardX < clipRight) {
                    drawTacticalText(text, forwardX, y)
                    forwardX += cycleDist
                }
            }
        }

        canvas.restore()
        postInvalidateOnAnimation()
    }

    val isCutoutZeroed = (cutoutWidthDp == 0 && wingGapDp == 0f)
    val hasCutout = !isCutoutZeroed && (isCenteredCutout || (cutoutRight > layout.railLeft && cutoutLeft < layout.railRight))

    val isStatusBarGuard = renderCache.isStatusBarGuard
    val tickerClipLeft = if (isLandscape && isStatusBarGuard) {
        maxOf(layout.railLeft, (renderCache.safePaddingLeftDp + 42f) * d)
    } else {
        layout.railLeft
    }
    val tickerClipRight = if (isLandscape && isStatusBarGuard) {
        minOf(layout.railRight, screenW - ((renderCache.safePaddingRightDp + 58f) * d))
    } else {
        layout.railRight
    }

    if (isAvoidCutout && hasCutout) {
        val (wingLeftText, wingRightText) = if (leftLabel.isNotBlank() && rightLabel.isNotBlank() && marqueeScope != "unified") {
            Pair(leftLabel, rightLabel)
        } else {
            val fullText = if (leftLabel.isNotBlank() && rightLabel.isNotBlank()) {
                "$leftLabel  •  $rightLabel"
            } else {
                leftLabel.ifBlank { rightLabel }.ifBlank { tickerText }.trim()
            }

            val mid = fullText.length / 2
            var bestBreak = -1
            var minDiff = Int.MAX_VALUE
            for (i in fullText.indices) {
                if (fullText[i] == ' ' || fullText[i] == '-' || fullText[i] == '_' || fullText[i] == '•' || fullText[i] == '—' || fullText[i] == ':') {
                    val diff = kotlin.math.abs(i - mid)
                    if (diff < minDiff) {
                        minDiff = diff
                        bestBreak = i
                    }
                }
            }
            if (bestBreak in 1 until fullText.length - 1) {
                Pair(fullText.substring(0, bestBreak).trim(), fullText.substring(bestBreak + 1).trim())
            } else if (fullText.length > 2) {
                val splitPt = (fullText.length / 2).coerceIn(1, fullText.length - 1)
                Pair(fullText.substring(0, splitPt).trim(), fullText.substring(splitPt).trim())
            } else {
                Pair(fullText, "")
            }
        }

        val leftClipL = tickerClipLeft
        val leftClipR = (cutoutLeft - wingGap).coerceAtLeast(leftClipL)
        val rightClipL = (cutoutRight + wingGap).coerceAtMost(tickerClipRight)
        val rightClipR = tickerClipRight

        val forceStaticLeft = (marqueeScope == "right_wing_only")
        val forceStaticRight = (marqueeScope == "left_wing_only")

        if (wingLeftText.isNotBlank()) {
            renderMarqueeText(wingLeftText, leftClipL, leftClipR, textY, alignTo = "right", forceStatic = forceStaticLeft)
        }
        if (wingRightText.isNotBlank()) {
            renderMarqueeText(wingRightText, rightClipL, rightClipR, textY, alignTo = "left", forceStatic = forceStaticRight)
        }
    } else {
        renderMarqueeText(tickerText, tickerClipLeft, tickerClipRight, textY, alignTo = renderCache.railAlign, forceStatic = false)
    }
}

// -------------------------------------------------------------------------------------------------
// 4. Scrub / Navigation HUD Sub-Renderer
// -------------------------------------------------------------------------------------------------
internal fun LightspeedStatusBarOverlay.drawScrubHud(
    canvas: Canvas,
    d: Float,
    screenW: Float,
    m3Primary: Int
) {
    val navState = com.sbf.lightspeed.system.LightspeedKeyEngine.currentNavState
    if (navState != null && navState.isActive) {
        val topY = (renderCache.thicknessDp * d) + (8f * d)
        val hudCenterY = topY + (72f * d)
        LightspeedHudRenderer.renderHud(
            canvas = canvas,
            style = "canopy_droppod",
            title = "GEAR SET NAV: ${navState.setName}",
            value = navState.currentLabel,
            stepIndex = navState.currentIndex,
            totalSteps = navState.totalCount,
            centerX = screenW / 2f,
            centerY = hudCenterY,
            topY = topY,
            primaryColor = m3Primary,
            density = d
        )
    } else {
        val transientHud = currentTransientHudState
        if (transientHud != null) {
            val topY = (renderCache.thicknessDp * d) + (8f * d)
            val hudCenterY = topY + (72f * d)
            LightspeedHudRenderer.renderHud(
                canvas = canvas,
                style = transientHud.style,
                title = transientHud.title,
                value = transientHud.value,
                stepIndex = transientHud.stepIndex,
                totalSteps = transientHud.totalSteps,
                centerX = screenW / 2f,
                centerY = hudCenterY,
                topY = topY,
                primaryColor = m3Primary,
                density = d
            )
        }
    }
}
