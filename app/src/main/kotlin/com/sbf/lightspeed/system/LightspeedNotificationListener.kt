package com.sbf.lightspeed.system

import android.app.Notification
import android.content.ComponentName
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.drawable.BitmapDrawable
import android.media.session.MediaController
import android.media.session.PlaybackState
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import java.util.concurrent.ConcurrentHashMap

/**
 * Lightweight NotificationListenerService for querying active MediaSession, MediaController,
 * and intercepting live download/progress notifications for Status Bar & Notch HUD Telemetry.
 */
class LightspeedNotificationListener : NotificationListenerService() {

    data class DownloadTelemetry(
        val key: String,
        val title: String,
        val progress: Int,
        val max: Int,
        val progressFraction: Float,
        val isIndeterminate: Boolean,
        val packageName: String,
        val iconColor: Int? = null,
        val lastUpdated: Long = System.currentTimeMillis()
    )

    data class MediaTelemetry(
        val title: String,
        val artist: String,
        val isPlaying: Boolean,
        val positionMs: Long,
        val durationMs: Long,
        val packageName: String?,
        val iconColor: Int? = null
    )

    companion object {
        private const val TAG = "LightspeedNotif"

        var instance: LightspeedNotificationListener? = null
            private set

        val activeDownloads = ConcurrentHashMap<String, DownloadTelemetry>()
        var activeMediaTelemetry: MediaTelemetry? = null
            private set

        var onTelemetryChanged: (() -> Unit)? = null

        fun getComponentName(context: Context): ComponentName {
            return ComponentName(context, LightspeedNotificationListener::class.java)
        }

        fun getPrimaryDownload(): DownloadTelemetry? {
            return activeDownloads.values.maxByOrNull { it.lastUpdated }
        }

        fun updateMediaTelemetry(telemetry: MediaTelemetry?) {
            activeMediaTelemetry = telemetry
            onTelemetryChanged?.invoke()
        }
    }

    override fun onListenerConnected() {
        super.onListenerConnected()
        instance = this
        LightspeedMediaManager.onNotificationListenerConnected()
        scanExistingNotifications()
    }

    override fun onListenerDisconnected() {
        super.onListenerDisconnected()
        if (instance === this) {
            instance = null
        }
        activeDownloads.clear()
        onTelemetryChanged?.invoke()
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        if (sbn == null) return
        val notif = sbn.notification ?: return
        val extras = notif.extras ?: return

        val max = extras.getInt(Notification.EXTRA_PROGRESS_MAX, -1)
        val progress = extras.getInt(Notification.EXTRA_PROGRESS, -1)
        val indeterminate = extras.getBoolean(Notification.EXTRA_PROGRESS_INDETERMINATE, false)

        if (max > 0 || indeterminate) {
            val title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString()
                ?: extras.getCharSequence(Notification.EXTRA_SUB_TEXT)?.toString()
                ?: sbn.packageName
            val fraction = if (max > 0) (progress.toFloat() / max.toFloat()).coerceIn(0f, 1f) else -1f
            val iconColor = AppIconColorExtractor.extractColor(this, sbn.packageName, notif.color)

            val telemetry = DownloadTelemetry(
                key = sbn.key,
                title = title,
                progress = progress,
                max = max,
                progressFraction = fraction,
                isIndeterminate = indeterminate,
                packageName = sbn.packageName,
                iconColor = iconColor
            )
            activeDownloads[sbn.key] = telemetry
            onTelemetryChanged?.invoke()
        } else if (activeDownloads.containsKey(sbn.key)) {
            // If progress completed/removed
            activeDownloads.remove(sbn.key)
            onTelemetryChanged?.invoke()
        }

        // Query active media controller for track changes
        try {
            val info = LightspeedMediaManager.getActiveTrackInfo(this)
            val mediaIconColor = AppIconColorExtractor.extractColor(this, info.packageName, 0)
            activeMediaTelemetry = MediaTelemetry(
                title = info.title,
                artist = info.artist,
                isPlaying = info.isPlaying,
                positionMs = info.positionMs,
                durationMs = info.durationMs,
                packageName = info.packageName,
                iconColor = mediaIconColor
            )
            onTelemetryChanged?.invoke()
        } catch (_: Exception) {}
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification?) {
        if (sbn == null) return
        if (activeDownloads.remove(sbn.key) != null) {
            onTelemetryChanged?.invoke()
        }
    }

    private fun scanExistingNotifications() {
        try {
            val active = activeNotifications ?: return
            for (sbn in active) {
                onNotificationPosted(sbn)
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed scanning active notifications", e)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        if (instance === this) {
            instance = null
        }
    }
}

/**
 * High-performance, memory-cached color extraction engine for notification and app icons.
 * Samples vibrant dominant colors for the Horizon Rail and dynamic HUD telemetry.
 */
object AppIconColorExtractor {

    private val colorCache = ConcurrentHashMap<String, Int>()

    fun extractColor(context: Context, packageName: String?, explicitNotifColor: Int = 0): Int {
        if (explicitNotifColor != 0 && explicitNotifColor != Color.TRANSPARENT) {
            val alpha = Color.alpha(explicitNotifColor)
            if (alpha > 50) {
                return explicitNotifColor
            }
        }

        if (packageName.isNullOrBlank()) {
            return Color.parseColor("#00E5FF")
        }

        colorCache[packageName]?.let { return it }

        return try {
            val pm = context.packageManager
            val drawable = pm.getApplicationIcon(packageName)
            val bitmap = when (drawable) {
                is BitmapDrawable -> drawable.bitmap
                else -> {
                    val w = drawable.intrinsicWidth.coerceIn(32, 128)
                    val h = drawable.intrinsicHeight.coerceIn(32, 128)
                    val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
                    val canvas = Canvas(bmp)
                    drawable.setBounds(0, 0, w, h)
                    drawable.draw(canvas)
                    bmp
                }
            }

            val w = bitmap.width
            val h = bitmap.height
            var dominantColor = Color.parseColor("#00E5FF")
            var maxVibrancy = -1f
            val hsv = FloatArray(3)

            val stepX = (w / 6).coerceAtLeast(1)
            val stepY = (h / 6).coerceAtLeast(1)

            for (x in stepX until w - stepX step stepX) {
                for (y in stepY until h - stepY step stepY) {
                    val pixel = bitmap.getPixel(x, y)
                    if (Color.alpha(pixel) > 200) {
                        Color.colorToHSV(pixel, hsv)
                        val saturation = hsv[1]
                        val value = hsv[2]
                        val vibrancy = saturation * 0.7f + value * 0.3f
                        if (vibrancy > maxVibrancy && value > 0.3f) {
                            maxVibrancy = vibrancy
                            dominantColor = pixel
                        }
                    }
                }
            }

            colorCache[packageName] = dominantColor
            dominantColor
        } catch (_: Exception) {
            Color.parseColor("#00E5FF")
        }
    }

    fun clearCache() {
        colorCache.clear()
    }
}


