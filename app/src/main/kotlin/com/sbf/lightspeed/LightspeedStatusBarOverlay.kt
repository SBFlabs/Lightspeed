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
        val railThicknessDp = prefs.getInt(com.sbf.lightspeed.system.LightspeedPreferences.KEY_HORIZON_RAIL_THICKNESS, 2).coerceIn(1, 6)
        val maxRails = prefs.getInt(com.sbf.lightspeed.system.LightspeedPreferences.KEY_HORIZON_RAIL_MAX_COUNT, 2).coerceIn(1, 3)
        val isRailText = prefs.getBoolean(com.sbf.lightspeed.system.LightspeedPreferences.KEY_HORIZON_RAIL_TEXT_ENABLED, true)
        val railOffsetY = prefs.getInt(com.sbf.lightspeed.system.LightspeedPreferences.KEY_HORIZON_RAIL_OFFSET_Y, 0)
        val textOffsetY = prefs.getInt(com.sbf.lightspeed.system.LightspeedPreferences.KEY_HORIZON_RAIL_TEXT_OFFSET_Y, 0)
        val textPos = prefs.getString(com.sbf.lightspeed.system.LightspeedPreferences.KEY_HORIZON_RAIL_TEXT_POSITION, "below") ?: "below"

        val baseRailHeightDp = railOffsetY + (railThicknessDp * maxRails) + 8
        val textHeightDp = if (isRailText) {
            if (textPos == "below_statusbar") {
                sensorThicknessDp + textOffsetY + 24
            } else {
                baseRailHeightDp + textOffsetY + 24
            }
        } else baseRailHeightDp

        val isSensorEnabled = prefs.getBoolean("pref_statusbar_enabled", true)
        val effectiveHeightDp = if (isSensorEnabled) maxOf(sensorThicknessDp, textHeightDp) else textHeightDp

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

        val orientation = resources.configuration.orientation
        val isLandscape = orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE

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
        val railOrientMode = prefs.getString(com.sbf.lightspeed.system.LightspeedPreferences.KEY_HORIZON_RAIL_ORIENTATION_MODE, "both") ?: "both"
        val isRailAllowedByOrientation = when (railOrientMode) {
            "landscape_only" -> isLandscape
            "portrait_only" -> !isLandscape
            else -> true
        }

        val dlRouting = prefs.getString(com.sbf.lightspeed.system.LightspeedPreferences.KEY_TELEMETRY_DOWNLOADS_ROUTING, "notch_pill") ?: "notch_pill"
        val mediaRouting = prefs.getString(com.sbf.lightspeed.system.LightspeedPreferences.KEY_TELEMETRY_MEDIA_ROUTING, "none") ?: "none"
        val primaryDl = com.sbf.lightspeed.system.LightspeedNotificationListener.getPrimaryDownload()
        val media = com.sbf.lightspeed.system.LightspeedNotificationListener.activeMediaTelemetry

        val screenWidthDp = (screenW / d).toInt()
        val railSpanPref = prefs.getInt(com.sbf.lightspeed.system.LightspeedPreferences.KEY_HORIZON_RAIL_SPAN, screenWidthDp).coerceIn(50, screenWidthDp)
        val railSpanPx = if (railSpanPref >= screenWidthDp - 5) screenW else (railSpanPref * d).coerceIn(50f * d, screenW)
        val railAlign = prefs.getString(com.sbf.lightspeed.system.LightspeedPreferences.KEY_HORIZON_RAIL_ALIGN, "center") ?: "center"
        val railOffsetX = prefs.getInt(com.sbf.lightspeed.system.LightspeedPreferences.KEY_HORIZON_RAIL_OFFSET_X, 0) * d
        val railOffsetY = prefs.getInt(com.sbf.lightspeed.system.LightspeedPreferences.KEY_HORIZON_RAIL_OFFSET_Y, 0).toFloat() * d

        // Status Bar Collision Guard & Safe Margin Insets
        val isStatusBarGuard = prefs.getBoolean(com.sbf.lightspeed.system.LightspeedPreferences.KEY_HORIZON_RAIL_STATUS_BAR_GUARD, true)
        val safePaddingLeftDp = prefs.getInt(com.sbf.lightspeed.system.LightspeedPreferences.KEY_HORIZON_RAIL_SAFE_PADDING_LEFT, 0).toFloat()
        val safePaddingRightDp = prefs.getInt(com.sbf.lightspeed.system.LightspeedPreferences.KEY_HORIZON_RAIL_SAFE_PADDING_RIGHT, 0).toFloat()

        val autoLeftClearance = if (isLandscape && isStatusBarGuard) 42f * d else 0f
        val autoRightClearance = if (isLandscape && isStatusBarGuard) 58f * d else 0f

        val safeInsetLeft = (safePaddingLeftDp * d) + autoLeftClearance
        val safeInsetRight = (safePaddingRightDp * d) + autoRightClearance

        val availableScreenLeft = safeInsetLeft
        val availableScreenRight = (screenW - safeInsetRight).coerceAtLeast(availableScreenLeft + 20f * d)

        val baseRailLeft = when (railAlign) {
            "left" -> (availableScreenLeft + railOffsetX).coerceIn(availableScreenLeft, (availableScreenRight - railSpanPx).coerceAtLeast(availableScreenLeft))
            "right" -> (availableScreenRight - railSpanPx + railOffsetX).coerceIn(availableScreenLeft, (availableScreenRight - railSpanPx).coerceAtLeast(availableScreenLeft))
            else -> (((screenW - railSpanPx) / 2f) + railOffsetX).coerceIn(availableScreenLeft, (availableScreenRight - railSpanPx).coerceAtLeast(availableScreenLeft))
        }
        val railLeft = baseRailLeft.coerceIn(availableScreenLeft, (availableScreenRight - 20f * d).coerceAtLeast(availableScreenLeft))
        val railRight = (railLeft + railSpanPx).coerceAtMost(availableScreenRight)
        val railSpanPxActual = (railRight - railLeft).coerceAtLeast(20f * d)

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
            val lastUpdated: Long = System.currentTimeMillis(),
            val album: String = "",
            val positionMs: Long = 0L,
            val durationMs: Long = 0L
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

        // If no real downloads or media are active, but Horizon Rail preview is active:
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

        if (activeStreams.isNotEmpty() && isRailAllowedByOrientation) {
            fun resolveStreamColor(index: Int, stream: HorizonStream): Int {
                val baseColor = when (colorMode) {
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
                if (index == 0) return baseColor
                // Optical depth & luminance stepping for same-color or multi-stream stacking
                val factor = when (index) {
                    1 -> 0.82f // 82% luminance for tier 2
                    else -> 0.68f // 68% luminance for tier 3
                }
                val r = (Color.red(baseColor) * factor).toInt().coerceIn(0, 255)
                val g = (Color.green(baseColor) * factor).toInt().coerceIn(0, 255)
                val b = (Color.blue(baseColor) * factor).toInt().coerceIn(0, 255)
                return Color.argb(Color.alpha(baseColor), r, g, b)
            }

            // --- Micro-Text Telemetry Ticker Setup ---
            val isTextEnabled = prefs.getBoolean(com.sbf.lightspeed.system.LightspeedPreferences.KEY_HORIZON_RAIL_TEXT_ENABLED, true)
            val textOrientMode = prefs.getString(com.sbf.lightspeed.system.LightspeedPreferences.KEY_HORIZON_RAIL_TEXT_ORIENTATION_MODE, "both") ?: "both"
            val isTextAllowedByOrientation = when (textOrientMode) {
                "landscape_only" -> isLandscape
                "portrait_only" -> !isLandscape
                else -> true
            }

            val primaryStream = activeStreams[0]
            val cleanTitle = primaryStream.title.trim().uppercase()
            val cleanSub = primaryStream.subtitle.trim().uppercase()
            val cleanAlbum = primaryStream.album.trim().uppercase()

            val metadataMode = prefs.getString(com.sbf.lightspeed.system.LightspeedPreferences.KEY_HORIZON_RAIL_TEXT_METADATA_MODE, "adaptive") ?: "adaptive"
            val isShowTimestamp = prefs.getBoolean(com.sbf.lightspeed.system.LightspeedPreferences.KEY_HORIZON_RAIL_TEXT_SHOW_TIMESTAMP, false)

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
                else -> isLandscape // "adaptive": Full in landscape, Title in portrait
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

            val hasMicroText = isTextEnabled && isTextAllowedByOrientation && tickerText.isNotBlank()
            val textSizeDp = prefs.getInt(com.sbf.lightspeed.system.LightspeedPreferences.KEY_HORIZON_RAIL_TEXT_SIZE, 9).coerceIn(7, 16).toFloat()
            val primaryColor = resolveStreamColor(0, primaryStream)
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

            val textPos = prefs.getString(com.sbf.lightspeed.system.LightspeedPreferences.KEY_HORIZON_RAIL_TEXT_POSITION, "below") ?: "below"
            val textOffsetY = prefs.getInt(com.sbf.lightspeed.system.LightspeedPreferences.KEY_HORIZON_RAIL_TEXT_OFFSET_Y, 0).toFloat() * d
            val fontMetrics = microTextPaint.fontMetrics
            val textBaselineOffset = -fontMetrics.ascent
            val stackSpacingDp = prefs.getInt(com.sbf.lightspeed.system.LightspeedPreferences.KEY_HORIZON_RAIL_STACK_SPACING, 0).coerceIn(0, 6).toFloat()
            val isDropShadow = prefs.getBoolean(com.sbf.lightspeed.system.LightspeedPreferences.KEY_HORIZON_RAIL_DROP_SHADOW, true)
            val isTerminalCaps = prefs.getBoolean(com.sbf.lightspeed.system.LightspeedPreferences.KEY_HORIZON_RAIL_TERMINAL_CAPS, true)
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

            // Position micro-text ticker relative to rail or status bar
            val textY = when (textPos) {
                "above" -> (lineYs[0] - (thicknesses[0] * d / 2f) - (1.5f * d) - fontMetrics.descent) + textOffsetY
                "embedded" -> (lineYs[0] - (fontMetrics.ascent + fontMetrics.descent) / 2f) + textOffsetY
                "below_statusbar" -> (sensorHeight + (2f * d) + textBaselineOffset) + textOffsetY
                else -> (lineYs[0] + (thicknesses[0] * d / 2f) + (1.5f * d) + textBaselineOffset) + textOffsetY
            }

            // 0. Ambient Drop Shadow & Contrast Trench (separates same-color rails from matching wallpaper)
            if (isDropShadow) {
                val shadowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    style = Paint.Style.STROKE
                    strokeCap = Paint.Cap.ROUND
                }
                activeStreams.forEachIndexed { index, stream ->
                    val thickness = thicknesses[index]
                    val lineY = lineYs[index]
                    val progX = progressXs[index]

                    // Soft ambient shadow under inactive track
                    if (railTrackOpacityPct > 0) {
                        shadowPaint.strokeWidth = (thickness + 2.2f) * d
                        shadowPaint.color = Color.argb(75, 0, 0, 0)
                        canvas.drawLine(railLeft, lineY + (1.0f * d), railRight, lineY + (1.0f * d), shadowPaint)
                    }

                    // Deep contrast drop-shadow under active progress
                    if (stream.progressFraction > 0f) {
                        shadowPaint.strokeWidth = (thickness + 3.0f) * d
                        shadowPaint.color = Color.argb(135, 4, 6, 10)
                        canvas.drawLine(railLeft, lineY + (1.3f * d), progX, lineY + (1.3f * d), shadowPaint)
                    }
                }
            }

            // 1. Draw Horizon Rail Lines (Tracks, Glow, and Progress)
            activeStreams.forEachIndexed { index, stream ->
                val col = resolveStreamColor(index, stream)
                val thickness = thicknesses[index]
                val lineY = lineYs[index]
                val progressX = progressXs[index]

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
                    canvas.drawLine(railLeft, lineY, progressX, lineY, telemetryGlowPaint)
                }

                telemetryGlowPaint.strokeWidth = thickness * d
                telemetryGlowPaint.color = col
                canvas.drawLine(railLeft, lineY, progressX, lineY, telemetryGlowPaint)
            }

            // 1b. Inter-Rail Contact Micro-Grooves (Separates zero-gap touching rails of the same color)
            if (gapDp <= 1f && activeStreams.size > 1) {
                val seamPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    style = Paint.Style.STROKE
                    strokeCap = Paint.Cap.BUTT
                    strokeWidth = (0.75f * d).coerceAtLeast(1f)
                    color = Color.argb(165, 4, 6, 10)
                }
                for (i in 0 until activeStreams.size - 1) {
                    val topBottom = lineYs[i] + (thicknesses[i] * d / 2f)
                    val bottomTop = lineYs[i + 1] - (thicknesses[i + 1] * d / 2f)
                    val seamY = (topBottom + bottomTop) / 2f
                    val maxActiveX = maxOf(progressXs[i], progressXs[i + 1])
                    if (maxActiveX > railLeft) {
                        canvas.drawLine(railLeft, seamY, maxActiveX, seamY, seamPaint)
                    }
                }
            }

            // 1c. Tactical Terminal Progress Head Caps (Precision end markers for differing progress amounts)
            if (isTerminalCaps) {
                val capPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    style = Paint.Style.STROKE
                    strokeCap = Paint.Cap.ROUND
                }
                activeStreams.forEachIndexed { index, stream ->
                    val progressX = progressXs[index]
                    val thickness = thicknesses[index]
                    val lineY = lineYs[index]

                    if (stream.progressFraction in 0.01f..0.99f) {
                        val halfCapH = ((thickness * d) / 2f) + (1.2f * d)
                        // High-contrast dark collar
                        capPaint.strokeWidth = (1.8f * d).coerceAtLeast(2f)
                        capPaint.color = Color.argb(220, 6, 8, 14)
                        canvas.drawLine(progressX, lineY - halfCapH, progressX, lineY + halfCapH, capPaint)

                        // Specular highlight pip
                        capPaint.strokeWidth = (1.0f * d).coerceAtLeast(1f)
                        capPaint.color = Color.argb(240, 255, 255, 255)
                        canvas.drawLine(progressX - 0.75f * d, lineY - halfCapH * 0.7f, progressX - 0.75f * d, lineY + halfCapH * 0.7f, capPaint)
                    }
                }
            }

            // 2. Draw Hero Micro-Text Ticker (Exclusively for Rail #0)
            if (hasMicroText) {
                val speedDp = prefs.getInt(com.sbf.lightspeed.system.LightspeedPreferences.KEY_HORIZON_RAIL_TEXT_SPEED, 20).coerceIn(10, 80).toFloat()
                val speedPx = speedDp * d
                val marqueeAnimMode = prefs.getString(com.sbf.lightspeed.system.LightspeedPreferences.KEY_HORIZON_RAIL_MARQUEE_ANIM_MODE, "continuous_wrap") ?: "continuous_wrap"
                val marqueeDirection = prefs.getString(com.sbf.lightspeed.system.LightspeedPreferences.KEY_HORIZON_RAIL_MARQUEE_DIRECTION, "rtl") ?: "rtl"
                val marqueeScope = prefs.getString(com.sbf.lightspeed.system.LightspeedPreferences.KEY_HORIZON_RAIL_MARQUEE_SCOPE, "both_wings") ?: "both_wings"

                val isAvoidCutout = prefs.getBoolean(com.sbf.lightspeed.system.LightspeedPreferences.KEY_HORIZON_RAIL_AVOID_CUTOUT, false)
                val wingGapDp = prefs.getInt(com.sbf.lightspeed.system.LightspeedPreferences.KEY_HORIZON_RAIL_CUTOUT_PADDING, 4).coerceIn(0, 16).toFloat()
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
                val cutoutWidthDp = com.sbf.lightspeed.system.LightspeedPreferences.getEffectiveCutoutWidth(prefs, defaultCutoutWidthDp).coerceIn(0, 72)
                val effectiveCutoutWidthPx = cutoutWidthDp.toFloat() * d

                val notchOffsetX = com.sbf.lightspeed.system.LightspeedPreferences.getEffectiveCutoutOffsetX(prefs).toFloat() * d
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
                        canvas.clipRect(clipLeft, 0f, clipRight, h.toFloat())
                        drawTacticalText(safeText, startX.coerceIn(clipLeft, (clipRight - microTextPaint.measureText(safeText)).coerceAtLeast(clipLeft)), y)
                        canvas.restore()
                        return
                    }

                    // Text Overflows -> Dynamic Marquee
                    val now = SystemClock.uptimeMillis()
                    val speedPxPerMs = speedPx / 1000f

                    canvas.save()
                    canvas.clipRect(clipLeft, 0f, clipRight, h.toFloat())

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
                        // Continuous Wrap Loop
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
                val hasCutout = !isCutoutZeroed && (isCenteredCutout || (cutoutRight > railLeft && cutoutLeft < railRight))

                if (isAvoidCutout && hasCutout) {
                    // Dual-Wing Symmetrical Cutout Split
                    val (wingLeftText, wingRightText) = if (leftLabel.isNotBlank() && rightLabel.isNotBlank() && marqueeScope != "unified") {
                        Pair(leftLabel, rightLabel)
                    } else {
                        // If only one label is available OR user selected unified stream, split cleanly around the middle across the camera cutout
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

                    val leftClipL = railLeft
                    val leftClipR = (cutoutLeft - wingGap).coerceAtLeast(leftClipL)
                    val rightClipL = (cutoutRight + wingGap).coerceAtMost(railRight)
                    val rightClipR = railRight

                    val forceStaticLeft = (marqueeScope == "right_wing_only")
                    val forceStaticRight = (marqueeScope == "left_wing_only")

                    if (wingLeftText.isNotBlank()) {
                        renderMarqueeText(wingLeftText, leftClipL, leftClipR, textY, alignTo = "right", forceStatic = forceStaticLeft)
                    }
                    if (wingRightText.isNotBlank()) {
                        renderMarqueeText(wingRightText, rightClipL, rightClipR, textY, alignTo = "left", forceStatic = forceStaticRight)
                    }
                } else {
                    // Unified text block: perfectly centered or aligned with full marquee support
                    renderMarqueeText(tickerText, railLeft, railRight, textY, alignTo = railAlign, forceStatic = false)
                }
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
