package com.sbf.lightspeed

import android.graphics.Color
import com.sbf.lightspeed.system.AppIconColorExtractor
import com.sbf.lightspeed.system.LightspeedMediaManager
import com.sbf.lightspeed.system.LightspeedNotificationListener

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

internal fun LightspeedStatusBarOverlay.collectHorizonStreams(
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
                        progressFraction = if (dl.isIndeterminate) 0f else dl.progressFraction.coerceIn(0f, 1f),
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

internal fun LightspeedStatusBarOverlay.resolveStreamColor(
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
