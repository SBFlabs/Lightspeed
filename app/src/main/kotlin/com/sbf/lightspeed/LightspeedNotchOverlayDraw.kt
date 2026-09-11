package com.sbf.lightspeed

import android.app.DownloadManager
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.graphics.*
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.text.TextPaint
import android.text.TextUtils
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import androidx.core.content.ContextCompat
import com.sbf.lightspeed.system.ActionDispatcher
import com.sbf.lightspeed.system.LightspeedHapticEngine
import com.sbf.lightspeed.system.LightspeedIconManager
import com.sbf.lightspeed.system.LightspeedMediaManager
import com.sbf.lightspeed.system.LightspeedNotificationListener
import com.sbf.lightspeed.system.LightspeedPreferences
import com.sbf.lightspeed.system.defaultPrefs
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.abs
import kotlin.math.hypot

internal fun LightspeedNotchOverlay.handleDraw(canvas: Canvas, superCall: () -> Unit) {
        superCall()

        val d = resources.displayMetrics.density
        val w = width.toFloat()
        val h = height.toFloat()
        if (w <= 1f || h <= 1f) return

        val m3Primary = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            context.resources.getColor(android.R.color.system_accent1_300, context.theme)
        } else {
            Color.parseColor("#90CAF9")
        }

        val dlRouting = prefs.getString(LightspeedPreferences.KEY_RAIL_DOWNLOADS_ROUTING, "notch_pill") ?: "notch_pill"
        val mediaRouting = prefs.getString(LightspeedPreferences.KEY_RAIL_MEDIA_ROUTING, "none") ?: "none"
        val primaryDl = LightspeedNotificationListener.getPrimaryDownload()
        val media = LightspeedNotificationListener.activeMediaTelemetry

        val isTestBeacon = prefs.getBoolean(LightspeedPreferences.KEY_NOTCH_TEST_BEACON, false)
        val showDlPill = (dlRouting == "notch_pill" || dlRouting == "both") && primaryDl != null
        val showMediaPill = (mediaRouting == "notch_pill" || mediaRouting == "both") && media != null && media.isPlaying

        if (!isTestBeacon && !showDlPill && !showMediaPill && !isExpanded) {
            return
        }

        if (isExpanded) {
            // =========================================================================
            // INTERACTIVE EXPANDED LIQUID-GLASS 2-PAGE CAPSULE CARD
            // =========================================================================
            expandedCardBounds.set(0f, 0f, w, h)

            // Liquid-Glass M3 Card Background & Glow
            telemetryPillFillPaint.color = Color.argb(240, 14, 18, 28)
            canvas.drawRoundRect(expandedCardBounds, 22f * d, 22f * d, telemetryPillFillPaint)

            telemetryPillRimPaint.strokeWidth = 1.4f * d
            telemetryPillRimPaint.color = Color.argb(180, Color.red(m3Primary), Color.green(m3Primary), Color.blue(m3Primary))
            canvas.drawRoundRect(expandedCardBounds, 22f * d, 22f * d, telemetryPillRimPaint)

            // Close Button [✕] at Top Right (Vector Drawable)
            val closeSize = 22f * d
            btnCloseBounds.set(w - closeSize - 12f * d, 10f * d, w - 12f * d, 10f * d + closeSize)
            buttonBgPaint.color = Color.argb(80, 255, 255, 255)
            canvas.drawCircle(btnCloseBounds.centerX(), btnCloseBounds.centerY(), closeSize / 2f, buttonBgPaint)
            val closeIconBounds = RectF(btnCloseBounds.centerX() - 5.5f * d, btnCloseBounds.centerY() - 5.5f * d, btnCloseBounds.centerX() + 5.5f * d, btnCloseBounds.centerY() + 5.5f * d)
            drawVector(canvas, R.drawable.ic_close, closeIconBounds, Color.WHITE)

            // Bottom Page Indicator Dots
            val dotY = h - 10f * d
            val cX = w / 2f
            val dot1W = if (expandedPageIndex == 0) 14f * d else 5f * d
            val dot2W = if (expandedPageIndex == 1) 14f * d else 5f * d
            val dotH = 4.5f * d
            val dotGap = 6f * d

            dot1Bounds.set(cX - dotGap / 2f - dot1W, dotY - dotH / 2f, cX - dotGap / 2f, dotY + dotH / 2f)
            dot2Bounds.set(cX + dotGap / 2f, dotY - dotH / 2f, cX + dotGap / 2f + dot2W, dotY + dotH / 2f)

            buttonBgPaint.color = if (expandedPageIndex == 0) m3Primary else Color.argb(100, 255, 255, 255)
            canvas.drawRoundRect(dot1Bounds, 2.5f * d, 2.5f * d, buttonBgPaint)

            buttonBgPaint.color = if (expandedPageIndex == 1) m3Primary else Color.argb(100, 255, 255, 255)
            canvas.drawRoundRect(dot2Bounds, 2.5f * d, 2.5f * d, buttonBgPaint)

            if (expandedPageIndex == 0) {
                // PAGE 1: Media Player / Download Telemetry
                if (showMediaPill || media != null) {
                    val med = media ?: LightspeedNotificationListener.activeMediaTelemetry
                    val title = med?.title ?: "Active Media"
                    val artist = med?.artist ?: "Now Playing"
                    val isPlaying = med?.isPlaying ?: true

                    val badgeIconBounds = RectF(16f * d, 13f * d, 25f * d, 22f * d)
                    drawVector(canvas, R.drawable.ic_music_note, badgeIconBounds, m3Primary)
                    hudTextPaint.textSize = 9.5f * d
                    hudTextPaint.color = m3Primary
                    canvas.drawText("NOW PLAYING", 28f * d, 21f * d, hudTextPaint)

                    hudTextPaint.textSize = 13.5f * d
                    hudTextPaint.color = Color.WHITE
                    val maxTitleW = w - 60f * d
                    val ellipsizedTitle = TextUtils.ellipsize(title, hudTextPaint, maxTitleW, TextUtils.TruncateAt.END).toString()
                    canvas.drawText(ellipsizedTitle, 16f * d, 38f * d, hudTextPaint)

                    subTextPaint.textSize = 11f * d
                    subTextPaint.color = Color.argb(200, 255, 255, 255)
                    val ellipsizedArtist = TextUtils.ellipsize(artist, subTextPaint, maxTitleW, TextUtils.TruncateAt.END).toString()
                    canvas.drawText(ellipsizedArtist, 16f * d, 53f * d, subTextPaint)

                    val progY = 65f * d
                    val progW = w - 32f * d
                    val progRect = RectF(16f * d, progY, 16f * d + progW, progY + 3.5f * d)
                    canvas.drawRoundRect(progRect, 2f * d, 2f * d, progressBarBgPaint)

                    val fraction = if ((med?.durationMs ?: 0L) > 0L) {
                        (med!!.positionMs.toFloat() / med.durationMs.toFloat()).coerceIn(0f, 1f)
                    } else 0.4f

                    val fillRect = RectF(progRect.left, progRect.top, progRect.left + progW * fraction, progRect.bottom)
                    progressBarFillPaint.color = m3Primary
                    canvas.drawRoundRect(fillRect, 2f * d, 2f * d, progressBarFillPaint)

                    val btnY = 86f * d
                    val btnR = 13f * d
                    val tcX = w / 2f

                    btnPrevBounds.set(tcX - 45f * d - btnR, btnY - btnR, tcX - 45f * d + btnR, btnY + btnR)
                    btnPlayPauseBounds.set(tcX - btnR - 2f * d, btnY - btnR - 2f * d, tcX + btnR + 2f * d, btnY + btnR + 2f * d)
                    btnNextBounds.set(tcX + 45f * d - btnR, btnY - btnR, tcX + 45f * d + btnR, btnY + btnR)

                    buttonBgPaint.color = Color.argb(80, 255, 255, 255)
                    canvas.drawCircle(btnPrevBounds.centerX(), btnPrevBounds.centerY(), btnR, buttonBgPaint)
                    canvas.drawCircle(btnPlayPauseBounds.centerX(), btnPlayPauseBounds.centerY(), btnR + 2f * d, buttonBgPaint)
                    canvas.drawCircle(btnNextBounds.centerX(), btnNextBounds.centerY(), btnR, buttonBgPaint)

                    val prevIconBounds = RectF(btnPrevBounds.centerX() - 6f * d, btnPrevBounds.centerY() - 6f * d, btnPrevBounds.centerX() + 6f * d, btnPrevBounds.centerY() + 6f * d)
                    drawVector(canvas, R.drawable.ic_skip_previous, prevIconBounds, Color.WHITE)

                    val playPauseR = 6.5f * d
                    val playPauseBounds = RectF(btnPlayPauseBounds.centerX() - playPauseR, btnPlayPauseBounds.centerY() - playPauseR, btnPlayPauseBounds.centerX() + playPauseR, btnPlayPauseBounds.centerY() + playPauseR)
                    drawVector(canvas, if (isPlaying) R.drawable.ic_pause else R.drawable.ic_play_arrow, playPauseBounds, Color.WHITE)

                    val nextIconBounds = RectF(btnNextBounds.centerX() - 6f * d, btnNextBounds.centerY() - 6f * d, btnNextBounds.centerX() + 6f * d, btnNextBounds.centerY() + 6f * d)
                    drawVector(canvas, R.drawable.ic_skip_next, nextIconBounds, Color.WHITE)
                } else if (showDlPill) {
                    val badgeIconBounds = RectF(16f * d, 14f * d, 25f * d, 23f * d)
                    drawVector(canvas, R.drawable.ic_arrow_downward, badgeIconBounds, m3Primary)
                    hudTextPaint.textSize = 9.5f * d
                    hudTextPaint.color = m3Primary
                    canvas.drawText("DOWNLOADING", 28f * d, 22f * d, hudTextPaint)

                    hudTextPaint.textSize = 13.5f * d
                    hudTextPaint.color = Color.WHITE
                    val maxTitleW = w - 60f * d
                    val ellipsizedTitle = TextUtils.ellipsize(primaryDl.title, hudTextPaint, maxTitleW, TextUtils.TruncateAt.END).toString()
                    canvas.drawText(ellipsizedTitle, 16f * d, 42f * d, hudTextPaint)

                    val pctStr = if (primaryDl.isIndeterminate) "Downloading in progress…" else "${(primaryDl.progressFraction * 100).toInt()}% • Active"
                    subTextPaint.textSize = 11.5f * d
                    subTextPaint.color = Color.argb(200, 255, 255, 255)
                    canvas.drawText(pctStr, 16f * d, 60f * d, subTextPaint)

                    val progY = 76f * d
                    val progW = w - 32f * d
                    val progRect = RectF(16f * d, progY, 16f * d + progW, progY + 4f * d)
                    canvas.drawRoundRect(progRect, 2f * d, 2f * d, progressBarBgPaint)

                    val fraction = if (primaryDl.isIndeterminate) {
                        ((SystemClock.uptimeMillis() % 1500L) / 1500f)
                    } else primaryDl.progressFraction.coerceIn(0f, 1f)

                    val fillRect = RectF(progRect.left, progRect.top, progRect.left + progW * fraction, progRect.bottom)
                    progressBarFillPaint.color = m3Primary
                    canvas.drawRoundRect(fillRect, 2f * d, 2f * d, progressBarFillPaint)
                } else {
                    val badgeIconBounds = RectF(16f * d, 14f * d, 25f * d, 23f * d)
                    drawVector(canvas, R.drawable.ic_beacon_sparkle, badgeIconBounds, m3Primary)
                    hudTextPaint.textSize = 9.5f * d
                    hudTextPaint.color = m3Primary
                    canvas.drawText("ORBITAL CAPSULE", 28f * d, 22f * d, hudTextPaint)

                    hudTextPaint.textSize = 13.5f * d
                    hudTextPaint.color = Color.WHITE
                    canvas.drawText("Lightspeed Telemetry Ready", 16f * d, 44f * d, hudTextPaint)

                    subTextPaint.textSize = 11f * d
                    subTextPaint.color = Color.argb(200, 255, 255, 255)
                    canvas.drawText("Swipe left for Quick Remote & Micro-Widgets", 16f * d, 64f * d, subTextPaint)
                }
            } else {
                // PAGE 2: Quick Remote
                val badgeIconBounds = RectF(16f * d, 13f * d, 26f * d, 23f * d)
                drawVector(canvas, R.drawable.ic_widgets, badgeIconBounds, m3Primary)
                hudTextPaint.textSize = 9.5f * d
                hudTextPaint.color = m3Primary
                canvas.drawText("ORBITAL REMOTE & MICRO-SLOT", 30f * d, 21f * d, hudTextPaint)

                val capsuleWidgetId = prefs.getInt(LightspeedPreferences.KEY_CAPSULE_WIDGET_ID, -1)
                val statusText = if (capsuleWidgetId != -1) "Widget Slot #$capsuleWidgetId Active • Swipe right for Media" else "Tactical Remote • Swipe right for Media"
                subTextPaint.textSize = 10.5f * d
                subTextPaint.color = Color.argb(200, 255, 255, 255)
                canvas.drawText(statusText, 16f * d, 36f * d, subTextPaint)

                val btnY = 58f * d
                val btnR = 14f * d
                val availableW = w - 32f * d
                val spacing = availableW / 5f

                val b1X = 16f * d + spacing * 0.5f
                val b2X = 16f * d + spacing * 1.5f
                val b3X = 16f * d + spacing * 2.5f
                val b4X = 16f * d + spacing * 3.5f
                val b5X = 16f * d + spacing * 4.5f

                btnRemoteFlashlightBounds.set(b1X - btnR, btnY - btnR, b1X + btnR, btnY + btnR)
                btnRemoteScreenshotBounds.set(b2X - btnR, btnY - btnR, b2X + btnR, btnY + btnR)
                btnRemoteLockBounds.set(b3X - btnR, btnY - btnR, b3X + btnR, btnY + btnR)
                btnRemoteTimeoutBounds.set(b4X - btnR, btnY - btnR, b4X + btnR, btnY + btnR)
                btnRemoteRefuelBounds.set(b5X - btnR, btnY - btnR, b5X + btnR, btnY + btnR)

                val buttons = listOf(
                    Pair(btnRemoteFlashlightBounds, R.drawable.ic_flashlight to "TORCH"),
                    Pair(btnRemoteScreenshotBounds, R.drawable.ic_screenshot to "CAPTURE"),
                    Pair(btnRemoteLockBounds, R.drawable.ic_lock to "LOCK"),
                    Pair(btnRemoteTimeoutBounds, R.drawable.ic_beacon_sparkle to "TIMER"),
                    Pair(btnRemoteRefuelBounds, R.drawable.ic_power to "REFUEL")
                )

                buttons.forEach { (bounds, iconAndLabel) ->
                    buttonBgPaint.color = Color.argb(80, 255, 255, 255)
                    canvas.drawCircle(bounds.centerX(), bounds.centerY(), btnR, buttonBgPaint)
                    val iconR = 6.5f * d
                    val iconBounds = RectF(bounds.centerX() - iconR, bounds.centerY() - iconR, bounds.centerX() + iconR, bounds.centerY() + iconR)
                    drawVector(canvas, iconAndLabel.first, iconBounds, Color.WHITE)

                    hudTextPaint.textSize = 7.5f * d
                    hudTextPaint.color = Color.argb(190, 255, 255, 255)
                    val labelW = hudTextPaint.measureText(iconAndLabel.second)
                    canvas.drawText(iconAndLabel.second, bounds.centerX() - labelW / 2f, bounds.bottom + 11f * d, hudTextPaint)
                }

                // Core Cooling Triple-Lock Interactive Bar
                val ccY = 95f * d
                val ccH = 19f * d
                val ccW = w - 32f * d
                btnCoreCoolingBounds.set(16f * d, ccY - ccH / 2f, 16f * d + ccW, ccY + ccH / 2f)

                val ccBg = when (coreCoolingStep) {
                    1 -> Color.argb(80, 255, 179, 0)
                    2 -> Color.argb(100, 255, 61, 0)
                    else -> Color.argb(45, 255, 255, 255)
                }
                val ccBorder = when (coreCoolingStep) {
                    1 -> Color.argb(220, 255, 179, 0)
                    2 -> Color.argb(255, 255, 61, 0)
                    else -> Color.argb(100, 255, 255, 255)
                }
                val ccTextColor = when (coreCoolingStep) {
                    1 -> Color.rgb(255, 179, 0)
                    2 -> Color.rgb(255, 61, 0)
                    else -> Color.WHITE
                }
                val ccLabel = when (coreCoolingStep) {
                    1 -> "⚠️ ARE YOU SURE? (TAP 2/3)"
                    2 -> "🚨 ARE YOU SURE SURE? (TAP 3/3)"
                    else -> "❄️ INITIATE CORE COOLING (REBOOT)"
                }

                buttonBgPaint.color = ccBg
                canvas.drawRoundRect(btnCoreCoolingBounds, 9.5f * d, 9.5f * d, buttonBgPaint)
                telemetryPillRimPaint.color = ccBorder
                telemetryPillRimPaint.strokeWidth = 1.1f * d
                canvas.drawRoundRect(btnCoreCoolingBounds, 9.5f * d, 9.5f * d, telemetryPillRimPaint)

                hudTextPaint.textSize = 8.5f * d
                hudTextPaint.color = ccTextColor
                val ccLabelW = hudTextPaint.measureText(ccLabel)
                val ccBaseline = btnCoreCoolingBounds.centerY() - (hudTextPaint.descent() + hudTextPaint.ascent()) / 2f
                canvas.drawText(ccLabel, btnCoreCoolingBounds.centerX() - ccLabelW / 2f, ccBaseline, hudTextPaint)
            }
            return
        }

        // =========================================================================
        // COLLAPSED CAPSULE NOTCH PILL RENDERING (3 MODES)
        // =========================================================================
        val orientation = resources.configuration.orientation
        val isLandscape = orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE
        val capsuleOrientMode = prefs.getString(LightspeedPreferences.KEY_NOTCH_CAPSULE_ORIENTATION_MODE, "both") ?: "both"
        val isAllowedByOrientation = when (capsuleOrientMode) {
            "portrait_only" -> !isLandscape
            "landscape_only" -> isLandscape
            else -> true
        }
        if (!isAllowedByOrientation) return

        val capsuleLayout = prefs.getString(LightspeedPreferences.KEY_NOTCH_CAPSULE_LAYOUT, "unified_right") ?: "unified_right"
        val scrollMode = prefs.getString(LightspeedPreferences.KEY_NOTCH_TEXT_SCROLL_MODE, "loop_2x") ?: "loop_2x"
        val truncateAnchor = prefs.getString(LightspeedPreferences.KEY_NOTCH_TEXT_TRUNCATE_ANCHOR, "tail") ?: "tail"
        val expansionW = prefs.getInt(LightspeedPreferences.KEY_NOTCH_EXPANSION_WIDTH, 0) * d

        val iconPad = 4.5f * d
        val textMargin = 6f * d
        val cornerRadius = h / 2f
        val iconSize = (h - 4f * d).coerceIn(14f * d, 26f * d)

        val rawTitleText = when {
            isTestBeacon -> "ALIGNMENT BEACON"
            showDlPill -> {
                if (primaryDl!!.isIndeterminate) "DOWNLOADING…" else "${(primaryDl.progressFraction * 100).toInt()}% • ${primaryDl.title}"
            }
            showMediaPill -> media!!.title
            else -> ""
        }

        if (rawTitleText.isBlank()) return

        hudTextPaint.textSize = (h * 0.38f).coerceIn(9f * d, 11f * d)
        hudTextPaint.color = if (isTestBeacon) m3Primary else Color.WHITE

        val textW = hudTextPaint.measureText(rawTitleText)
        val separator = "   •   "
        val separatorW = hudTextPaint.measureText(separator)
        val cycleLength = textW + separatorW

        val iconCenterX: Float
        val iconCenterY = h / 2f
        val textLeft: Float
        val textRight: Float

        when (capsuleLayout) {
            "dual_wing" -> {
                val leftWingW = (iconSize + 2 * iconPad).coerceAtLeast(26f * d)
                val rightWingBaseW = if (isTestBeacon) 110f * d else if (showDlPill) 100f * d else 135f * d
                val rightWingW = (rightWingBaseW + expansionW).coerceIn(60f * d, 240f * d)

                // Left Wing
                val leftWingBounds = RectF(0f, 0f, leftWingW, h)
                telemetryPillFillPaint.color = Color.argb(235, 14, 18, 28)
                canvas.drawRoundRect(leftWingBounds, cornerRadius, cornerRadius, telemetryPillFillPaint)
                telemetryPillRimPaint.strokeWidth = 1.3f * d
                telemetryPillRimPaint.color = Color.argb(160, Color.red(m3Primary), Color.green(m3Primary), Color.blue(m3Primary))
                canvas.drawRoundRect(leftWingBounds, cornerRadius, cornerRadius, telemetryPillRimPaint)

                // Right Wing
                val rightWingBounds = RectF(w - rightWingW, 0f, w, h)
                canvas.drawRoundRect(rightWingBounds, cornerRadius, cornerRadius, telemetryPillFillPaint)
                canvas.drawRoundRect(rightWingBounds, cornerRadius, cornerRadius, telemetryPillRimPaint)

                iconCenterX = leftWingW / 2f
                textLeft = w - rightWingW + iconPad + 2f * d
                textRight = w - iconPad
            }
            "unified_left" -> {
                val pillBounds = RectF(0f, 0f, w, h)
                telemetryPillFillPaint.color = Color.argb(235, 14, 18, 28)
                canvas.drawRoundRect(pillBounds, cornerRadius, cornerRadius, telemetryPillFillPaint)
                telemetryPillRimPaint.strokeWidth = 1.3f * d
                telemetryPillRimPaint.color = Color.argb(160, Color.red(m3Primary), Color.green(m3Primary), Color.blue(m3Primary))
                canvas.drawRoundRect(pillBounds, cornerRadius, cornerRadius, telemetryPillRimPaint)

                iconCenterX = iconPad + iconSize / 2f
                textLeft = iconPad + iconSize + textMargin
                textRight = w - iconPad
            }
            else -> {
                // "unified_right"
                val pillBounds = RectF(0f, 0f, w, h)
                telemetryPillFillPaint.color = Color.argb(235, 14, 18, 28)
                canvas.drawRoundRect(pillBounds, cornerRadius, cornerRadius, telemetryPillFillPaint)
                telemetryPillRimPaint.strokeWidth = 1.3f * d
                telemetryPillRimPaint.color = Color.argb(160, Color.red(m3Primary), Color.green(m3Primary), Color.blue(m3Primary))
                canvas.drawRoundRect(pillBounds, cornerRadius, cornerRadius, telemetryPillRimPaint)

                iconCenterX = iconPad + iconSize / 2f
                textLeft = iconPad + iconSize + textMargin
                textRight = w - iconPad
            }
        }

        // Draw Icon
        val iconSlotBounds = RectF(iconCenterX - iconSize / 2f, iconCenterY - iconSize / 2f, iconCenterX + iconSize / 2f, iconCenterY + iconSize / 2f)

        if (showMediaPill || (media != null && media.isPlaying)) {
            val pkg = media?.packageName ?: LightspeedMediaManager.getActiveTrackInfo(context).packageName
            val appIconBmp = if (!pkg.isNullOrBlank()) getCircularAppIcon(pkg, iconSize.toInt()) else null
            if (appIconBmp != null) {
                canvas.drawBitmap(appIconBmp, null, iconSlotBounds, iconBitmapPaint)
            } else {
                drawVector(canvas, R.drawable.ic_music_note, iconSlotBounds, m3Primary)
            }
        } else if (showDlPill) {
            val pkg = primaryDl.packageName
            val appIconBmp = if (!pkg.isNullOrBlank()) getCircularAppIcon(pkg, (iconSize - 3f * d).toInt()) else null

            val ringPadding = 1f * d
            val ringBounds = RectF(iconSlotBounds.left - ringPadding, iconSlotBounds.top - ringPadding, iconSlotBounds.right + ringPadding, iconSlotBounds.bottom + ringPadding)
            progressRingBgPaint.strokeWidth = 1.6f * d
            progressRingFillPaint.strokeWidth = 1.6f * d
            progressRingFillPaint.color = m3Primary
            canvas.drawOval(ringBounds, progressRingBgPaint)

            if (primaryDl.isIndeterminate) {
                val sweepAngle = 90f
                val startAngle = ((SystemClock.uptimeMillis() % 1200L) / 1200f) * 360f
                canvas.drawArc(ringBounds, startAngle, sweepAngle, false, progressRingFillPaint)
                postInvalidateOnAnimation()
            } else {
                val sweepAngle = (primaryDl.progressFraction.coerceIn(0f, 1f)) * 360f
                canvas.drawArc(ringBounds, -90f, sweepAngle, false, progressRingFillPaint)
            }

            if (appIconBmp != null) {
                val innerBounds = RectF(iconSlotBounds.left + 1.5f * d, iconSlotBounds.top + 1.5f * d, iconSlotBounds.right - 1.5f * d, iconSlotBounds.bottom - 1.5f * d)
                canvas.drawBitmap(appIconBmp, null, innerBounds, iconBitmapPaint)
            } else {
                val innerBounds = RectF(iconSlotBounds.left + 2f * d, iconSlotBounds.top + 2f * d, iconSlotBounds.right - 2f * d, iconSlotBounds.bottom - 2f * d)
                drawVector(canvas, R.drawable.ic_arrow_downward, innerBounds, m3Primary)
            }
        } else {
            drawVector(canvas, R.drawable.ic_beacon_sparkle, iconSlotBounds, m3Primary)
        }

        // Draw Text / Marquee
        val availableW = (textRight - textLeft).coerceAtLeast(10f * d)
        val textBaseline = h / 2f - (hudTextPaint.descent() + hudTextPaint.ascent()) / 2f

        if (textW <= availableW) {
            canvas.drawText(rawTitleText, textLeft, textBaseline, hudTextPaint)
        } else {
            if (rawTitleText != currentTextKey) {
                currentTextKey = rawTitleText
                marqueeScrollOffset = 0f
                marqueeCompletedLoops = 0
                lastMarqueeFrameTime = SystemClock.uptimeMillis()
            }

            val now = SystemClock.uptimeMillis()
            val dt = if (lastMarqueeFrameTime > 0L) (now - lastMarqueeFrameTime).coerceIn(0L, 100L) / 1000f else 0.016f
            lastMarqueeFrameTime = now

            val targetLoops = when (scrollMode) {
                "loop_1x" -> 1
                "loop_2x" -> 2
                "static" -> 0
                else -> Int.MAX_VALUE
            }

            val isLoopingActive = marqueeCompletedLoops < targetLoops && scrollMode != "static"

            if (isLoopingActive) {
                val scrollSpeed = (prefs.getInt(LightspeedPreferences.KEY_NOTCH_MARQUEE_SPEED, 30).toFloat()) * d
                marqueeScrollOffset += scrollSpeed * dt
                if (marqueeScrollOffset >= cycleLength) {
                    marqueeCompletedLoops++
                    if (marqueeCompletedLoops < targetLoops) {
                        marqueeScrollOffset -= cycleLength
                    } else {
                        marqueeScrollOffset = 0f
                    }
                }

                canvas.save()
                canvas.clipRect(textLeft, 0f, textRight, h)

                val x1 = textLeft - marqueeScrollOffset
                canvas.drawText(rawTitleText, x1, textBaseline, hudTextPaint)
                canvas.drawText(separator + rawTitleText, x1 + textW, textBaseline, hudTextPaint)
                canvas.restore()

                postInvalidateOnAnimation()
            } else {
                val truncateAt = when (truncateAnchor) {
                    "head" -> TextUtils.TruncateAt.START
                    "core" -> TextUtils.TruncateAt.MIDDLE
                    else -> TextUtils.TruncateAt.END
                }

                val ellipsizedText = TextUtils.ellipsize(rawTitleText, hudTextPaint, availableW, truncateAt).toString()
                canvas.drawText(ellipsizedText, textLeft, textBaseline, hudTextPaint)
            }
        }
}
