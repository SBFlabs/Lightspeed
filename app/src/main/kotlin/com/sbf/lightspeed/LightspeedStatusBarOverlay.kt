package com.sbf.lightspeed

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.content.SharedPreferences
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.media.AudioManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.os.VibrationEffect
import android.os.Vibrator
import android.provider.Settings
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.view.accessibility.AccessibilityNodeInfo
import com.sbf.lightspeed.system.ActionDispatcher
import com.sbf.lightspeed.system.LightspeedHapticEngine
import com.sbf.lightspeed.system.LightspeedHudRenderer
import com.sbf.lightspeed.system.LightspeedTimeoutEngine
import com.sbf.lightspeed.system.defaultPrefs
import kotlin.math.abs
import kotlin.math.hypot

class LightspeedStatusBarOverlay(
    context: Context,
    private val service: AccessibilityService
) : View(context) {

    private val prefs = service.defaultPrefs()

    private val debugPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#4DB6AC")
        style = Paint.Style.FILL
    }

    private val hudPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#8000E5FF")
        strokeWidth = 4f
        style = Paint.Style.STROKE
    }

    private val hudFillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#2600E5FF")
        style = Paint.Style.FILL
    }

    private val hudTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textSize = 28f
        textAlign = Paint.Align.CENTER
        isFakeBoldText = true
    }

    private val prefListener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
        if (key != null && (key.startsWith("pref_statusbar_") || key.startsWith("pref_horizon_rail_") || key.startsWith("pref_sub_") || key.startsWith("pref_section_") || key.startsWith("pref_macro_action_STATUSBAR") || key.startsWith("pref_telemetry_"))) {
            postInvalidate()
        }
    }

    private val telemetryGlowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
    }

    private val uiHandler = Handler(Looper.getMainLooper())
    init {
        isClickable = false
        isFocusable = false
        prefs.registerOnSharedPreferenceChangeListener(prefListener)

        com.sbf.lightspeed.system.LightspeedNotificationListener.onTelemetryChanged = {
            postInvalidate()
        }
        com.sbf.lightspeed.system.LightspeedKeyEngine.onNavStateListener = { state ->
            uiHandler.post {
                if (state?.isActive == true) {
                    expandForHud()
                } else {
                    restoreWindowLayout()
                }
                postInvalidate()
            }
        }
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        prefs.unregisterOnSharedPreferenceChangeListener(prefListener)
        com.sbf.lightspeed.system.LightspeedNotificationListener.onTelemetryChanged = null
        com.sbf.lightspeed.system.LightspeedKeyEngine.onNavStateListener = null
    }

    private fun expandForHud() {
        val wm = context.getSystemService(Context.WINDOW_SERVICE) as? WindowManager ?: return
        val lp = layoutParams as? WindowManager.LayoutParams ?: return
        val d = resources.displayMetrics.density
        val screenW = resources.displayMetrics.widthPixels
        lp.x = 0
        lp.y = 0
        lp.width = screenW
        lp.height = (260 * d).toInt()
        lp.gravity = Gravity.TOP or Gravity.START
        try { wm.updateViewLayout(this, lp) } catch (_: Exception) {}
    }

    private fun restoreWindowLayout() {
        val wm = context.getSystemService(Context.WINDOW_SERVICE) as? WindowManager ?: return
        val lp = layoutParams as? WindowManager.LayoutParams ?: return
        val d = resources.displayMetrics.density
        val screenWidthPx = resources.displayMetrics.widthPixels
        val sensorThicknessDp = prefs.getInt("pref_statusbar_thickness", 48).coerceIn(20, 52)
        val railThicknessDp = prefs.getInt(com.sbf.lightspeed.system.LightspeedPreferences.KEY_HORIZON_RAIL_THICKNESS, 3).coerceIn(1, 8)
        val isRailText = prefs.getBoolean(com.sbf.lightspeed.system.LightspeedPreferences.KEY_HORIZON_RAIL_TEXT_ENABLED, true)
        val railNeededHeightDp = if (isRailText) (railThicknessDp + 22) else (railThicknessDp + 6)
        val effectiveHeightDp = maxOf(sensorThicknessDp, railNeededHeightDp)

        lp.width = screenWidthPx
        lp.height = (effectiveHeightDp * d).toInt()
        lp.x = 0
        lp.y = 0
        lp.gravity = Gravity.TOP or Gravity.START
        try { wm.updateViewLayout(this, lp) } catch (_: Exception) {}
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val isSensorEnabled = prefs.getBoolean("pref_statusbar_enabled", true)
        val isExpanded = prefs.getBoolean("pref_section_statusbar_expanded", true)
        val isPreview  = prefs.getBoolean("pref_statusbar_preview", false)
        val isReview = isSensorEnabled && isExpanded && isPreview
        val transparencyPct = if (isSensorEnabled) prefs.getInt("pref_statusbar_transparency", 0) else 0
        val d = resources.displayMetrics.density
        val screenW = resources.displayMetrics.widthPixels.toFloat()
        val w = width.toFloat()
        val h = height.toFloat()

        val m3Primary = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            context.resources.getColor(android.R.color.system_accent1_300, context.theme)
        } else {
            Color.parseColor("#90CAF9")
        }

        // Sensor Area Geometry
        val spanPref = prefs.getInt("pref_statusbar_span", 1080)
        val spanPx = if (spanPref >= 1000) screenW else (spanPref * d).coerceIn(50f * d, screenW)
        val sensorOffsetX = prefs.getInt("pref_statusbar_offset_x", 0) * d
        val thicknessDp = prefs.getInt("pref_statusbar_thickness", 48).coerceIn(20, 52)
        val sensorHeight = thicknessDp * d
        val sensorOffsetY = (prefs.getInt("pref_statusbar_offset_y", 0) * d).coerceAtLeast(0f)

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

            // Center Telemetry Label
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

        // 1. Horizon Rail Telemetry Line & Micro-Text Renderer
        val dlRouting = prefs.getString(com.sbf.lightspeed.system.LightspeedPreferences.KEY_TELEMETRY_DOWNLOADS_ROUTING, "notch_pill") ?: "notch_pill"
        val mediaRouting = prefs.getString(com.sbf.lightspeed.system.LightspeedPreferences.KEY_TELEMETRY_MEDIA_ROUTING, "none") ?: "none"
        val primaryDl = com.sbf.lightspeed.system.LightspeedNotificationListener.getPrimaryDownload()
        val media = com.sbf.lightspeed.system.LightspeedNotificationListener.activeMediaTelemetry

        val screenWidthDp = (screenW / d).toInt()
        val railSpanPref = prefs.getInt(com.sbf.lightspeed.system.LightspeedPreferences.KEY_HORIZON_RAIL_SPAN, screenWidthDp).coerceIn(50, screenWidthDp)
        val railSpanPx = if (railSpanPref >= screenWidthDp - 5) screenW else (railSpanPref * d).coerceIn(50f * d, screenW)
        val railAlign = prefs.getString(com.sbf.lightspeed.system.LightspeedPreferences.KEY_HORIZON_RAIL_ALIGN, "center") ?: "center"
        val railOffsetX = prefs.getInt(com.sbf.lightspeed.system.LightspeedPreferences.KEY_HORIZON_RAIL_OFFSET_X, 0) * d

        val railLeft = when (railAlign) {
            "left" -> (0f + railOffsetX).coerceIn(0f, screenW - railSpanPx)
            "right" -> (screenW - railSpanPx + railOffsetX).coerceIn(0f, screenW - railSpanPx)
            else -> (((screenW - railSpanPx) / 2f) + railOffsetX).coerceIn(0f, screenW - railSpanPx)
        }
        val railRight = railLeft + railSpanPx

        val railThicknessDp = prefs.getInt(com.sbf.lightspeed.system.LightspeedPreferences.KEY_HORIZON_RAIL_THICKNESS, 2).coerceIn(1, 6)
        val railGlowPct = prefs.getInt(com.sbf.lightspeed.system.LightspeedPreferences.KEY_HORIZON_RAIL_GLOW, 60).coerceIn(0, 100)
        val railTrackOpacityPct = prefs.getInt(com.sbf.lightspeed.system.LightspeedPreferences.KEY_HORIZON_RAIL_TRACK_OPACITY, 15).coerceIn(0, 100)
        val colorMode = prefs.getString(com.sbf.lightspeed.system.LightspeedPreferences.KEY_HORIZON_RAIL_COLOR_MODE, "cover_art") ?: "cover_art"
        val maxRails = prefs.getInt(com.sbf.lightspeed.system.LightspeedPreferences.KEY_HORIZON_RAIL_MAX_COUNT, 2).coerceIn(1, 3)

        val isRailPreviewActive = prefs.getBoolean(com.sbf.lightspeed.system.LightspeedPreferences.KEY_HORIZON_RAIL_PREVIEW, false)

        data class HorizonStream(
            val type: String, // "dl" or "media"
            val title: String,
            val subtitle: String,
            val progressFraction: Float,
            val iconColor: Int?,
            val coverArtColor: Int? = null,
            val isIndeterminate: Boolean = false,
            val lastUpdated: Long = System.currentTimeMillis()
        )

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

        if (isMediaRouteEnabled && media != null && media.isPlaying && media.durationMs > 0) {
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
                    lastUpdated = System.currentTimeMillis()
                )
            )
        }

        // If no real downloads or media are active, but Horizon Rail preview is active:
        if (streams.isEmpty() && isRailPreviewActive) {
            if (isDlRouteEnabled || (!isDlRouteEnabled && !isMediaRouteEnabled)) {
                streams.add(
                    HorizonStream(
                        type = "dl",
                        title = "NIGHTLY_BUILD_V10.APK",
                        subtitle = "68",
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
                        coverArtColor = Color.parseColor("#FF007F")
                    )
                )
            }
            if (maxRails >= 3) {
                streams.add(
                    HorizonStream(
                        type = "dl",
                        title = "SYSTEM_CACHE_BACKUP.ZIP",
                        subtitle = "91",
                        progressFraction = 0.91f,
                        iconColor = Color.parseColor("#00E676"),
                        coverArtColor = null
                    )
                )
            }
        }

        // Apply pinning / priority to order the streams
        val railPriority = prefs.getString(com.sbf.lightspeed.system.LightspeedPreferences.KEY_HORIZON_RAIL_PRIORITY, "downloads_top") ?: "downloads_top"
        if (streams.size > 1) {
            when (railPriority) {
                "downloads_top" -> streams.sortBy { if (it.type == "dl") 0 else 1 }
                "media_top" -> streams.sortBy { if (it.type == "media") 0 else 1 }
                "most_recent" -> streams.sortByDescending { it.lastUpdated }
            }
        }

        val activeStreams = streams.take(maxRails)

        if (activeStreams.isNotEmpty()) {
            fun resolveStreamColor(stream: HorizonStream): Int {
                return when (colorMode) {
                    "cover_art" -> if (stream.type == "media") (stream.coverArtColor ?: stream.iconColor ?: m3Primary) else (stream.iconColor ?: m3Primary)
                    "app_icon" -> stream.iconColor ?: m3Primary
                    "material3" -> m3Primary
                    "inverted" -> {
                        val isNight = (resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK) == android.content.res.Configuration.UI_MODE_NIGHT_YES
                        if (isNight) Color.WHITE else Color.BLACK
                    }
                    "custom" -> {
                        val hex = prefs.getString(com.sbf.lightspeed.system.LightspeedPreferences.KEY_HORIZON_RAIL_CUSTOM_COLOR, "#00E5FF") ?: "#00E5FF"
                        try { Color.parseColor(hex) } catch (_: Exception) { Color.parseColor("#00E5FF") }
                    }
                    else -> m3Primary
                }
            }

            // --- Micro-Text Telemetry Ticker (Attached Directly to Rail #0 Pinned Stream) ---
            val isTextEnabled = prefs.getBoolean(com.sbf.lightspeed.system.LightspeedPreferences.KEY_HORIZON_RAIL_TEXT_ENABLED, true)
            val primaryStream = activeStreams[0]
            val cleanTitle = primaryStream.title.trim().uppercase()
            val cleanSub = primaryStream.subtitle.trim().uppercase()

            val leftLabel = if (primaryStream.type == "dl") {
                if (cleanTitle.isNotBlank()) "⬇ $cleanTitle" else "⬇ DOWNLOADING"
            } else {
                if (cleanTitle.isNotBlank()) cleanTitle else cleanSub
            }

            val rightLabel = if (primaryStream.type == "dl") {
                cleanSub.replace("%", "").trim()
            } else {
                if (cleanSub.isNotBlank() && !cleanSub.equals(cleanTitle, ignoreCase = true) && !cleanSub.equals("NOW PLAYING", ignoreCase = true)) {
                    cleanSub
                } else {
                    ""
                }
            }

            val tickerText = when {
                leftLabel.isNotBlank() && rightLabel.isNotBlank() -> if (primaryStream.type == "dl") "$leftLabel  •  $rightLabel" else "$leftLabel — $rightLabel"
                leftLabel.isNotBlank() -> leftLabel
                else -> rightLabel
            }

            val hasMicroText = isTextEnabled && tickerText.isNotBlank()
            val textSizeDp = prefs.getInt(com.sbf.lightspeed.system.LightspeedPreferences.KEY_HORIZON_RAIL_TEXT_SIZE, 9).coerceIn(7, 16).toFloat()
            val primaryColor = resolveStreamColor(primaryStream)
            val tacticalTypeface = android.graphics.Typeface.create("sans-serif-condensed", android.graphics.Typeface.BOLD)

            val microTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                textSize = textSizeDp * d
                typeface = tacticalTypeface
                letterSpacing = 0.05f
                color = if (colorMode == "inverted") primaryColor else Color.WHITE
                style = Paint.Style.FILL
            }

            val isContrastShield = prefs.getBoolean(com.sbf.lightspeed.system.LightspeedPreferences.KEY_HORIZON_RAIL_CONTRAST_SHIELD, true)
            val microTextOutlinePaint = if (isContrastShield) {
                Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    textSize = textSizeDp * d
                    typeface = tacticalTypeface
                    letterSpacing = 0.05f
                    color = Color.argb(220, 10, 14, 20)
                    style = Paint.Style.STROKE
                    strokeWidth = (textSizeDp * 0.16f * d).coerceIn(1.0f * d, 1.8f * d)
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

            val textPos = prefs.getString(com.sbf.lightspeed.system.LightspeedPreferences.KEY_HORIZON_RAIL_TEXT_POSITION, "below") ?: "below"
            val fontMetrics = microTextPaint.fontMetrics
            val textBaselineOffset = -fontMetrics.ascent
            val gapDp = 2.5f

            val thicknesses = FloatArray(activeStreams.size) { i ->
                if (i == 0) railThicknessDp.toFloat() else (railThicknessDp * (1f - i * 0.15f)).coerceAtLeast(1.5f)
            }
            val lineYs = FloatArray(activeStreams.size)
            var textY = 0f

            if (hasMicroText) {
                when (textPos) {
                    "above" -> {
                        val textBlockH = (textSizeDp + 3f) * d
                        textY = textBaselineOffset + (1f * d)
                        lineYs[0] = textBlockH + ((thicknesses[0] * d) / 2f)
                        for (i in 1 until activeStreams.size) {
                            lineYs[i] = lineYs[i - 1] + ((thicknesses[i - 1] * d) / 2f) + (gapDp * d) + ((thicknesses[i] * d) / 2f)
                        }
                    }
                    "embedded" -> {
                        lineYs[0] = (thicknesses[0] * d) / 2f
                        textY = lineYs[0] - (fontMetrics.ascent + fontMetrics.descent) / 2f
                        for (i in 1 until activeStreams.size) {
                            lineYs[i] = lineYs[i - 1] + ((thicknesses[i - 1] * d) / 2f) + (gapDp * d) + ((thicknesses[i] * d) / 2f)
                        }
                    }
                    else -> { // "below" (Default: directly under Rail #0 hero stream)
                        lineYs[0] = (thicknesses[0] * d) / 2f
                        textY = (thicknesses[0] * d) + (1.5f * d) + textBaselineOffset
                        val heroBottom = (thicknesses[0] * d) + (1.5f * d) + (textSizeDp * d) + (2f * d)
                        if (activeStreams.size > 1) {
                            lineYs[1] = heroBottom + ((thicknesses[1] * d) / 2f)
                        }
                        for (i in 2 until activeStreams.size) {
                            lineYs[i] = lineYs[i - 1] + ((thicknesses[i - 1] * d) / 2f) + (gapDp * d) + ((thicknesses[i] * d) / 2f)
                        }
                    }
                }
            } else {
                lineYs[0] = (thicknesses[0] * d) / 2f
                for (i in 1 until activeStreams.size) {
                    lineYs[i] = lineYs[i - 1] + ((thicknesses[i - 1] * d) / 2f) + (gapDp * d) + ((thicknesses[i] * d) / 2f)
                }
            }

            // 1. Draw Horizon Rail Lines (Tracks, Glow, and Progress)
            activeStreams.forEachIndexed { index, stream ->
                val col = resolveStreamColor(stream)
                val thickness = thicknesses[index]
                val lineY = lineYs[index]

                if (railTrackOpacityPct > 0) {
                    val trackAlpha = (railTrackOpacityPct * 2.55f).toInt().coerceIn(10, 255)
                    telemetryGlowPaint.strokeWidth = thickness * d
                    telemetryGlowPaint.color = Color.argb(trackAlpha, Color.red(col), Color.green(col), Color.blue(col))
                    canvas.drawLine(railLeft, lineY, railRight, lineY, telemetryGlowPaint)
                }

                if (railGlowPct > 0) {
                    val glowAlpha = ((railGlowPct * 1.5f) / (1f + index * 0.3f)).toInt().coerceIn(10, 200)
                    telemetryGlowPaint.strokeWidth = (thickness + 2.5f - index * 0.5f) * d
                    telemetryGlowPaint.color = Color.argb(glowAlpha, Color.red(col), Color.green(col), Color.blue(col))
                    val progressX = railLeft + (railSpanPx * stream.progressFraction)
                    canvas.drawLine(railLeft, lineY, progressX, lineY, telemetryGlowPaint)
                }

                telemetryGlowPaint.strokeWidth = thickness * d
                telemetryGlowPaint.color = col
                val progressX = railLeft + (railSpanPx * stream.progressFraction)
                canvas.drawLine(railLeft, lineY, progressX, lineY, telemetryGlowPaint)
            }

            // 2. Draw Hero Micro-Text Ticker (Exclusively for Rail #0)
            if (hasMicroText) {
                val textWidth = microTextPaint.measureText(tickerText)
                val speedDp = prefs.getInt(com.sbf.lightspeed.system.LightspeedPreferences.KEY_HORIZON_RAIL_TEXT_SPEED, 20).coerceIn(10, 60).toFloat()
                val speedPx = speedDp * d

                val isAvoidCutout = prefs.getBoolean(com.sbf.lightspeed.system.LightspeedPreferences.KEY_HORIZON_RAIL_AVOID_CUTOUT, false)
                val wingGapDp = prefs.getInt(com.sbf.lightspeed.system.LightspeedPreferences.KEY_HORIZON_RAIL_CUTOUT_PADDING, 4).coerceIn(0, 16).toFloat()
                val wingGap = wingGapDp * d

                val rawCutout = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) rootWindowInsets?.displayCutout else null
                val topCutout = (rawCutout?.boundingRectTop ?: rawCutout?.boundingRects?.firstOrNull { it.top == 0 })?.also {
                    if (it.width() > 0) LightspeedNotchOverlay.cachedCutoutRect = it
                }

                val defaultCutoutWidthDp = (topCutout?.width()?.toFloat()?.div(d) ?: 24f).coerceIn(14f, 32f).toInt()
                val cutoutWidthDp = prefs.getInt(com.sbf.lightspeed.system.LightspeedPreferences.KEY_HORIZON_RAIL_CUTOUT_WIDTH, defaultCutoutWidthDp).coerceIn(8, 36).toFloat()
                val cutoutWidthPx = cutoutWidthDp * d

                val notchOffsetX = (prefs.getInt(com.sbf.lightspeed.system.LightspeedPreferences.KEY_HORIZON_RAIL_CUTOUT_OFFSET_X, 0).takeIf { it != 0 }
                    ?: prefs.getInt(com.sbf.lightspeed.system.LightspeedPreferences.KEY_NOTCH_OFFSET_X, 0)).toFloat() * d
                val detectedCenterX = if (topCutout != null && topCutout.width() > 0) topCutout.exactCenterX() else screenW / 2f
                val cutoutCenterX = detectedCenterX + notchOffsetX

                val cutoutLeft = cutoutCenterX - (cutoutWidthPx / 2f)
                val cutoutRight = cutoutCenterX + (cutoutWidthPx / 2f)
                val isCenteredCutout = cutoutCenterX in (screenW * 0.30f)..(screenW * 0.70f)

                canvas.save()
                canvas.clipRect(railLeft, 0f, railRight, h.toFloat())

                // Static vs Scrolling Ticker Layout
                if (textWidth <= railSpanPx) {
                    val (leftPart, rightPart) = if (leftLabel.isNotBlank() && rightLabel.isNotBlank()) {
                        Pair(leftLabel, rightLabel)
                    } else {
                        val src = leftLabel.ifBlank { rightLabel }.ifBlank { tickerText }.trim()
                        val mid = src.length / 2
                        var bestBreak = -1
                        var minDiff = Int.MAX_VALUE
                        for (i in src.indices) {
                            if (src[i] == ' ' || src[i] == '-' || src[i] == '_' || src[i] == '•' || src[i] == '—') {
                                val diff = kotlin.math.abs(i - mid)
                                if (diff < minDiff) {
                                    minDiff = diff
                                    bestBreak = i
                                }
                            }
                        }
                        if (bestBreak in 1 until src.length - 1) {
                            Pair(src.substring(0, bestBreak).trim(), src.substring(bestBreak + 1).trim())
                        } else if (src.length > 2) {
                            val splitPt = (src.length / 2).coerceIn(1, src.length - 1)
                            Pair(src.substring(0, splitPt).trim(), src.substring(splitPt).trim())
                        } else {
                            Pair(src, "")
                        }
                    }

                    if (isAvoidCutout && isCenteredCutout && leftPart.isNotBlank() && rightPart.isNotBlank()) {
                        // Dual-Wing Symmetrical Cutout Split (Left = Left Wing, Right = Right Wing)
                        val availLeft = (cutoutLeft - railLeft - wingGap).coerceAtLeast(0f)
                        val availRight = (railRight - cutoutRight - wingGap).coerceAtLeast(0f)

                        val leftW = microTextPaint.measureText(leftPart)
                        val rightW = microTextPaint.measureText(rightPart)

                        val finalLeftLabel = if (leftW > availLeft) {
                            android.text.TextUtils.ellipsize(leftPart, android.text.TextPaint(microTextPaint), availLeft, android.text.TextUtils.TruncateAt.END).toString()
                        } else leftPart
                        val finalLeftW = microTextPaint.measureText(finalLeftLabel)

                        val finalRightLabel = if (rightW > availRight) {
                            android.text.TextUtils.ellipsize(rightPart, android.text.TextPaint(microTextPaint), availRight, android.text.TextUtils.TruncateAt.END).toString()
                        } else rightPart
                        val finalRightW = microTextPaint.measureText(finalRightLabel)

                        val leftX = (cutoutLeft - wingGap - finalLeftW).coerceAtLeast(railLeft)
                        val rightX = (cutoutRight + wingGap).coerceAtMost(railRight - finalRightW)

                        drawTacticalText(finalLeftLabel, leftX, textY)
                        drawTacticalText(finalRightLabel, rightX, textY)
                    } else {
                        // Unified text block: perfectly centered or aligned based on user preference
                        val startX = when (railAlign) {
                            "left" -> (railLeft + 6f * d).coerceIn(railLeft, railRight - textWidth)
                            "right" -> (railRight - textWidth - 6f * d).coerceIn(railLeft, railRight - textWidth)
                            else -> railLeft + (railSpanPx - textWidth) / 2f
                        }
                        drawTacticalText(tickerText, startX, textY)
                    }
                } else {
                    // Continuous scrolling Marquee (when text is longer than the rail)
                    val totalCycleDistance = textWidth + 60f * d
                    val cycleDurationMs = ((totalCycleDistance / speedPx) * 1000f).toLong().coerceAtLeast(1000L)
                    val elapsedMs = SystemClock.uptimeMillis() % cycleDurationMs
                    val offset = (elapsedMs.toFloat() / cycleDurationMs.toFloat()) * totalCycleDistance
                    val textX = railLeft + railSpanPx - offset

                    drawTacticalText(tickerText, textX, textY)
                    if (textX + textWidth < railRight) {
                        drawTacticalText(tickerText, textX + totalCycleDistance, textY)
                    }
                    postInvalidateOnAnimation()
                }
                canvas.restore()
            }
        }

        // 2. Hardware Gear Set HUD Navigation Renderer
        val navState = com.sbf.lightspeed.system.LightspeedKeyEngine.currentNavState
        if (navState != null && navState.isActive) {
            val topY = (prefs.getInt("pref_statusbar_thickness", 48).coerceIn(20, 52) * d) + (8f * d)
            LightspeedHudRenderer.renderHud(
                canvas = canvas,
                style = "canopy_droppod",
                title = "GEAR SET NAV: ${navState.setName}",
                value = navState.currentLabel,
                stepIndex = navState.currentIndex,
                totalSteps = navState.totalCount,
                centerX = screenW / 2f,
                centerY = (h / 2f).coerceAtLeast(topY + 40f * d),
                topY = topY,
                primaryColor = m3Primary,
                density = d
            )
        }
    }

    fun performActionByName(actionKey: String) {
        ActionDispatcher.execute(service, actionKey) {
            scrollToTop()
        }
    }

    fun scrollToTop() {
        val scrollableNodes = mutableListOf<AccessibilityNodeInfo>()
        val windows = service.windows

        if (!windows.isNullOrEmpty()) {
            for (window in windows) {
                if (window.type == android.view.accessibility.AccessibilityWindowInfo.TYPE_APPLICATION || window.isActive) {
                    val root = window.root ?: continue
                    collectScrollableNodes(root, scrollableNodes)
                }
            }
        }

        if (scrollableNodes.isEmpty()) {
            service.rootInActiveWindow?.let { root ->
                collectScrollableNodes(root, scrollableNodes)
            }
        }

        if (scrollableNodes.isEmpty()) return
        val targetNodes = scrollableNodes.reversed()

        for (node in targetNodes) {
            try {
                val bundle = Bundle().apply {
                    putInt(AccessibilityNodeInfo.ACTION_ARGUMENT_ROW_INT, 0)
                    putInt(AccessibilityNodeInfo.ACTION_ARGUMENT_COLUMN_INT, 0)
                }

                var success = false
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                    success = node.performAction(
                        AccessibilityNodeInfo.AccessibilityAction.ACTION_SCROLL_TO_POSITION.id,
                        bundle
                    )
                }

                if (!success) {
                    var passes = 0
                    while (passes < 25) {
                        val moved = (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R &&
                                        node.performAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_PAGE_UP.id)) ||
                                    (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q &&
                                        node.performAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_SCROLL_UP.id)) ||
                                    node.performAction(AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD)
                        if (!moved) break
                        passes++
                    }
                }
            } catch (_: Exception) {}
        }
    }

    private fun collectScrollableNodes(node: AccessibilityNodeInfo, list: MutableList<AccessibilityNodeInfo>) {
        val hasScrollAction = node.actionList.any { action ->
            action.id == AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD ||
            (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && action.id == AccessibilityNodeInfo.AccessibilityAction.ACTION_SCROLL_UP.id) ||
            (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N && action.id == AccessibilityNodeInfo.AccessibilityAction.ACTION_SCROLL_TO_POSITION.id)
        }

        if (node.isScrollable || hasScrollAction) {
            list.add(AccessibilityNodeInfo.obtain(node))
        }

        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            collectScrollableNodes(child, list)
        }
    }
}
