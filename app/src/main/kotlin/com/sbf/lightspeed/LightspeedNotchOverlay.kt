package com.sbf.lightspeed

import android.content.Context
import android.content.SharedPreferences
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.os.Build
import android.view.View
import com.sbf.lightspeed.system.LightspeedNotificationListener
import com.sbf.lightspeed.system.LightspeedPreferences
import com.sbf.lightspeed.system.defaultPrefs

/**
 * Dedicated, unclipped Camera Cutout Notch Pill overlay window for Lightspeed.
 *
 * Runs with FLAG_NOT_TOUCHABLE and independent vertical height expansion to guarantee
 * zero touch interference with apps and 100% unclipped liquid-glass rendering across all display cutouts.
 */
class LightspeedNotchOverlay(context: Context) : View(context) {

    private val prefs = context.defaultPrefs()

    private val telemetryPillFillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }

    private val telemetryPillRimPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
    }

    private val hudTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textAlign = Paint.Align.CENTER
        isFakeBoldText = true
    }

    private val prefListener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
        if (key != null && (key.startsWith("pref_notch_") || key.startsWith("pref_telemetry_") || key == "pref_statusbar_enabled")) {
            postInvalidate()
        }
    }

    init {
        isClickable = false
        isFocusable = false
        prefs.registerOnSharedPreferenceChangeListener(prefListener)

        LightspeedNotificationListener.onTelemetryChanged = {
            postInvalidate()
        }
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        prefs.unregisterOnSharedPreferenceChangeListener(prefListener)
        LightspeedNotificationListener.onTelemetryChanged = null
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        val d = resources.displayMetrics.density
        val w = width.toFloat()
        if (w <= 0f) return

        val m3Primary = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            context.resources.getColor(android.R.color.system_accent1_300, context.theme)
        } else {
            Color.parseColor("#90CAF9")
        }

        val dlRouting = prefs.getString(LightspeedPreferences.KEY_TELEMETRY_DOWNLOADS_ROUTING, "notch_pill") ?: "notch_pill"
        val mediaRouting = prefs.getString(LightspeedPreferences.KEY_TELEMETRY_MEDIA_ROUTING, "none") ?: "none"
        val primaryDl = LightspeedNotificationListener.getPrimaryDownload()
        val media = LightspeedNotificationListener.activeMediaTelemetry

        val isTestBeacon = prefs.getBoolean(LightspeedPreferences.KEY_NOTCH_TEST_BEACON, false)
        val showDlPill = (dlRouting == "notch_pill" || dlRouting == "both") && primaryDl != null
        val showMediaPill = (mediaRouting == "notch_pill" || mediaRouting == "both") && media != null && media.isPlaying

        if (!isTestBeacon && !showDlPill && !showMediaPill) {
            return
        }

        // Notch Calibration Parameters
        val offsetX = prefs.getInt(LightspeedPreferences.KEY_NOTCH_OFFSET_X, 0) * d
        val offsetY = prefs.getInt(LightspeedPreferences.KEY_NOTCH_OFFSET_Y, 0) * d
        val expansionW = prefs.getInt(LightspeedPreferences.KEY_NOTCH_EXPANSION_WIDTH, 0) * d

        val cutout = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) rootWindowInsets?.displayCutout else null
        val topCutoutRect = cutout?.boundingRectTop ?: cutout?.boundingRects?.firstOrNull { it.top == 0 }
        val notchCenterX = (if (topCutoutRect != null && topCutoutRect.width() > 0) topCutoutRect.exactCenterX() else w / 2f) + offsetX
        val notchTopY = (if (topCutoutRect != null) topCutoutRect.top.toFloat() else 0f) + offsetY
        val notchHeight = if (topCutoutRect != null && topCutoutRect.height() > 0) topCutoutRect.height().toFloat() else 28f * d
        val pillCy = notchTopY + (notchHeight / 2f).coerceAtLeast(14f * d)

        if (isTestBeacon) {
            // Live Alignment Test Beacon
            val pillW = 140f * d + expansionW
            val pillH = 28f * d
            val pillRect = RectF(notchCenterX - pillW / 2f, pillCy - pillH / 2f, notchCenterX + pillW / 2f, pillCy + pillH / 2f)

            telemetryPillFillPaint.color = Color.argb(235, 14, 18, 28)
            canvas.drawRoundRect(pillRect, pillH / 2f, pillH / 2f, telemetryPillFillPaint)

            telemetryPillRimPaint.strokeWidth = 1.5f * d
            telemetryPillRimPaint.color = Color.argb(210, Color.red(m3Primary), Color.green(m3Primary), Color.blue(m3Primary))
            canvas.drawRoundRect(pillRect, pillH / 2f, pillH / 2f, telemetryPillRimPaint)

            hudTextPaint.textSize = 10f * d
            hudTextPaint.color = m3Primary
            canvas.drawText("✦ TEST BEACON ✦", notchCenterX, pillCy + (3.5f * d), hudTextPaint)
        } else if (showDlPill) {
            val pillW = 126f * d + expansionW
            val pillH = 26f * d
            val pillRect = RectF(notchCenterX - pillW / 2f, pillCy - pillH / 2f, notchCenterX + pillW / 2f, pillCy + pillH / 2f)

            telemetryPillFillPaint.color = Color.argb(225, 14, 18, 28)
            canvas.drawRoundRect(pillRect, pillH / 2f, pillH / 2f, telemetryPillFillPaint)

            telemetryPillRimPaint.strokeWidth = 1.2f * d
            telemetryPillRimPaint.color = Color.argb(140, Color.red(m3Primary), Color.green(m3Primary), Color.blue(m3Primary))
            canvas.drawRoundRect(pillRect, pillH / 2f, pillH / 2f, telemetryPillRimPaint)

            hudTextPaint.textSize = 10f * d
            hudTextPaint.color = m3Primary
            val pctText = if (primaryDl!!.isIndeterminate) "⬇ DOWNLOADING…" else "⬇ ${(primaryDl.progressFraction * 100).toInt()}%"
            canvas.drawText(pctText, notchCenterX, pillCy + (3.5f * d), hudTextPaint)
        } else if (showMediaPill) {
            val pillW = 156f * d + expansionW
            val pillH = 26f * d
            val pillRect = RectF(notchCenterX - pillW / 2f, pillCy - pillH / 2f, notchCenterX + pillW / 2f, pillCy + pillH / 2f)

            telemetryPillFillPaint.color = Color.argb(225, 14, 18, 28)
            canvas.drawRoundRect(pillRect, pillH / 2f, pillH / 2f, telemetryPillFillPaint)

            telemetryPillRimPaint.strokeWidth = 1.2f * d
            telemetryPillRimPaint.color = Color.argb(140, Color.red(m3Primary), Color.green(m3Primary), Color.blue(m3Primary))
            canvas.drawRoundRect(pillRect, pillH / 2f, pillH / 2f, telemetryPillRimPaint)

            hudTextPaint.textSize = 9.5f * d
            hudTextPaint.color = Color.WHITE
            val cleanTitle = if (media.title.length > 18) media.title.take(16) + "…" else media.title
            canvas.drawText("♫ $cleanTitle", notchCenterX, pillCy + (3.5f * d), hudTextPaint)
        }
    }
}
