package com.sbf.lightspeed.system

import android.app.Notification
import android.content.ComponentName
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.drawable.BitmapDrawable
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArraySet

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
        val iconColor: Int? = null,
        val coverArtColor: Int? = null,
        val album: String = ""
    )

    data class RecentAlertEntry(
        val kind: String, // "NOTIF" or "TOAST"
        val packageName: String,
        val summary: String,
        val timestampMs: Long = System.currentTimeMillis(),
        val firstTimestampMs: Long = timestampMs,
        val count: Int = 1
    )

    companion object {
        private const val TAG = "LightspeedNotif"

        var instance: LightspeedNotificationListener? = null
            private set

        val activeDownloads = ConcurrentHashMap<String, DownloadTelemetry>()
        val recentAlerts = java.util.concurrent.CopyOnWriteArrayList<RecentAlertEntry>()
        private const val MAX_ALERT_HISTORY = 1500
        private const val ALERT_RETENTION_MS = 365L * 24L * 3_600_000L
        private const val ALERTS_HISTORY_FILE = "alerts_history.json"
        @Volatile private var alertsLoaded = false
        var activeMediaTelemetry: MediaTelemetry? = null
            private set

        private fun squashAlerts(entries: List<RecentAlertEntry>): List<RecentAlertEntry> {
            val map = LinkedHashMap<Triple<String, String, String>, RecentAlertEntry>(entries.size)
            for (entry in entries) {
                val key = Triple(entry.kind, entry.packageName, entry.summary)
                val existing = map[key]
                if (existing == null) {
                    map[key] = entry
                } else {
                    map[key] = existing.copy(
                        timestampMs = maxOf(existing.timestampMs, entry.timestampMs),
                        firstTimestampMs = minOf(existing.firstTimestampMs, entry.firstTimestampMs),
                        count = existing.count + entry.count.coerceAtLeast(1)
                    )
                }
            }
            return map.values.sortedByDescending { it.timestampMs }
        }

        fun ensureAlertsLoaded(context: Context) {
            if (alertsLoaded) return
            var needsPersist = false
            synchronized(recentAlerts) {
                if (alertsLoaded) return
                alertsLoaded = true
                try {
                    val file = java.io.File(context.applicationContext.filesDir, ALERTS_HISTORY_FILE)
                    if (file.exists()) {
                        val arr = org.json.JSONArray(file.readText())
                        val cutoff = System.currentTimeMillis() - ALERT_RETENTION_MS
                        val loaded = mutableListOf<RecentAlertEntry>()
                        for (i in 0 until arr.length()) {
                            val obj = arr.optJSONObject(i) ?: continue
                            val ts = obj.optLong("ts", 0L)
                            if (ts < cutoff) continue
                            val firstTs = obj.optLong("firstTs", ts).let { if (it <= 0L) ts else minOf(it, ts) }
                            val cnt = obj.optInt("count", 1).coerceAtLeast(1)
                            loaded.add(
                                RecentAlertEntry(
                                    kind = obj.optString("kind", "NOTIF"),
                                    packageName = obj.optString("pkg", "android"),
                                    summary = obj.optString("msg", ""),
                                    timestampMs = ts,
                                    firstTimestampMs = firstTs,
                                    count = cnt
                                )
                            )
                        }
                        if (loaded.isNotEmpty()) {
                            val squashed = squashAlerts(loaded).take(MAX_ALERT_HISTORY)
                            needsPersist = squashed.size < loaded.size
                            recentAlerts.addAll(squashed)
                        }
                    }
                } catch (_: Exception) {}
            }
            if (needsPersist) persistAlertsAsync(context)
        }

        @Volatile
        private var lastAlertsPersistMs: Long = 0L
        @Volatile
        private var alertsPersistScheduled: Boolean = false
        private val persistDebounceHandler = android.os.Handler(android.os.Looper.getMainLooper())

        private fun persistAlertsAsync(context: Context, immediate: Boolean = false) {
            val appCtx = context.applicationContext ?: context
            val now = android.os.SystemClock.uptimeMillis()
            if (!immediate && lastAlertsPersistMs > 0L && (now - lastAlertsPersistMs) < 60_000L) {
                if (!alertsPersistScheduled) {
                    alertsPersistScheduled = true
                    val delay = (60_000L - (now - lastAlertsPersistMs)).coerceIn(5_000L, 60_000L)
                    persistDebounceHandler.postDelayed({
                        alertsPersistScheduled = false
                        persistAlertsNow(appCtx)
                    }, delay)
                }
                return
            }
            persistAlertsNow(appCtx)
        }

        private fun persistAlertsNow(appCtx: Context) {
            lastAlertsPersistMs = android.os.SystemClock.uptimeMillis()
            Thread {
                try {
                    val snapshot = synchronized(recentAlerts) { recentAlerts.take(MAX_ALERT_HISTORY) }
                    val arr = org.json.JSONArray()
                    for (entry in snapshot) {
                        arr.put(org.json.JSONObject().apply {
                            put("kind", entry.kind)
                            put("pkg", entry.packageName)
                            put("msg", entry.summary)
                            put("ts", entry.timestampMs)
                            put("firstTs", entry.firstTimestampMs)
                            put("count", entry.count)
                        })
                    }
                    val file = java.io.File(appCtx.filesDir, ALERTS_HISTORY_FILE)
                    file.writeText(arr.toString())
                } catch (_: Exception) {}
            }.start()
        }

        fun recordAlert(kind: String, packageName: String, summary: String, context: Context? = null) {
            if (context != null) ensureAlertsLoaded(context)
            val clean = summary.trim().replace("\n", " ").take(240)
            if (clean.isEmpty()) return
            val now = System.currentTimeMillis()
            synchronized(recentAlerts) {
                val idx = recentAlerts.indexOfFirst {
                    it.kind == kind && it.packageName == packageName && it.summary == clean
                }
                if (idx >= 0) {
                    val existing = recentAlerts.removeAt(idx)
                    recentAlerts.add(
                        0,
                        existing.copy(
                            timestampMs = maxOf(existing.timestampMs, now),
                            firstTimestampMs = minOf(existing.firstTimestampMs, now),
                            count = existing.count + 1
                        )
                    )
                } else {
                    recentAlerts.add(
                        0,
                        RecentAlertEntry(
                            kind = kind,
                            packageName = packageName,
                            summary = clean,
                            timestampMs = now,
                            firstTimestampMs = now,
                            count = 1
                        )
                    )
                }
                while (recentAlerts.size > MAX_ALERT_HISTORY) {
                    try { recentAlerts.removeAt(recentAlerts.lastIndex) } catch (_: Exception) { break }
                }
            }
            if (context != null) persistAlertsAsync(context)
        }

        fun getAlertsInWindow(context: Context, windowMs: Long): List<RecentAlertEntry> {
            ensureAlertsLoaded(context)
            val cutoff = System.currentTimeMillis() - windowMs
            return recentAlerts.filter { it.timestampMs >= cutoff }
        }

        private val telemetryListeners = CopyOnWriteArraySet<() -> Unit>()

        fun registerTelemetryListener(listener: () -> Unit) {
            telemetryListeners.add(listener)
        }

        fun unregisterTelemetryListener(listener: () -> Unit) {
            telemetryListeners.remove(listener)
        }

        fun notifyTelemetryChanged() {
            for (listener in telemetryListeners) {
                try {
                    listener.invoke()
                } catch (e: Exception) {
                    Log.e(TAG, "Error invoking telemetry listener", e)
                }
            }
        }

        @Deprecated("Use registerTelemetryListener/unregisterTelemetryListener instead")
        var onTelemetryChanged: (() -> Unit)? = null
            set(value) {
                field = value
                if (value != null) registerTelemetryListener(value)
            }

        fun getComponentName(context: Context): ComponentName {
            return ComponentName(context, LightspeedNotificationListener::class.java)
        }

        fun getPrimaryDownload(): DownloadTelemetry? {
            return activeDownloads.values.maxByOrNull { it.lastUpdated }
        }

        fun getActiveDownloadsList(): List<DownloadTelemetry> {
            return activeDownloads.values.sortedByDescending { it.lastUpdated }
        }
    }

    private var mediaSessionManager: MediaSessionManager? = null
    private val activeControllers = ConcurrentHashMap<MediaController, MediaController.Callback>()

    private val sessionsChangedListener = MediaSessionManager.OnActiveSessionsChangedListener { controllers ->
        updateMediaControllers(controllers)
    }

    override fun onListenerConnected() {
        super.onListenerConnected()
        instance = this
        LightspeedMediaManager.onNotificationListenerConnected()
        scanExistingNotifications()

        try {
            val mainHandler = android.os.Handler(android.os.Looper.getMainLooper())
            mediaSessionManager = getSystemService(Context.MEDIA_SESSION_SERVICE) as? MediaSessionManager
            val component = getComponentName(this)
            mediaSessionManager?.addOnActiveSessionsChangedListener(sessionsChangedListener, component, mainHandler)
            val controllers = mediaSessionManager?.getActiveSessions(component)
            updateMediaControllers(controllers)
        } catch (e: Exception) {
            Log.w(TAG, "Failed registering OnActiveSessionsChangedListener", e)
        }
    }

    override fun onListenerDisconnected() {
        super.onListenerDisconnected()
        if (instance === this) {
            instance = null
        }
        try {
            mediaSessionManager?.removeOnActiveSessionsChangedListener(sessionsChangedListener)
            activeControllers.forEach { (controller, callback) ->
                try { controller.unregisterCallback(callback) } catch (e: Exception) { logSwallowed("LightspeedNotificationListener", "onListenerDisconnected:132", e) }
            }
            activeControllers.clear()
        } catch (e: Exception) { logSwallowed(TAG, "onListenerDisconnected:135", e) }

        activeDownloads.clear()
        notifyTelemetryChanged()
    }

    private fun updateMediaControllers(controllers: List<MediaController>?) {
        activeControllers.forEach { (controller, callback) ->
            try { controller.unregisterCallback(callback) } catch (e: Exception) { logSwallowed("LightspeedNotificationListener", "updateMediaControllers:143", e) }
        }
        activeControllers.clear()

        controllers?.forEach { controller ->
            val callback = object : MediaController.Callback() {
                override fun onMetadataChanged(metadata: MediaMetadata?) {
                    refreshMediaTelemetry()
                }

                override fun onPlaybackStateChanged(state: PlaybackState?) {
                    refreshMediaTelemetry()
                }

                override fun onSessionDestroyed() {
                    refreshMediaTelemetry()
                }
            }
            val mainHandler = android.os.Handler(android.os.Looper.getMainLooper())
            try {
                controller.registerCallback(callback, mainHandler)
                activeControllers[controller] = callback
            } catch (e: Exception) { logSwallowed(TAG, "onSessionDestroyed:164", e) }
        }

        refreshMediaTelemetry()
    }

    fun refreshMediaTelemetry() {
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
                iconColor = mediaIconColor,
                coverArtColor = info.coverArtColor,
                album = info.album
            )
            notifyTelemetryChanged()
        } catch (e: Exception) { logSwallowed(TAG, "refreshMediaTelemetry:186", e) }
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        if (sbn == null) return
        val notif = sbn.notification ?: return
        val extras = notif.extras ?: return

        // Exclude media playback notifications from active downloads
        val isMediaNotification = notif.category == Notification.CATEGORY_TRANSPORT ||
            extras.getString(Notification.EXTRA_TEMPLATE)?.contains("MediaStyle") == true ||
            extras.containsKey(Notification.EXTRA_MEDIA_SESSION)
        if (isMediaNotification) {
            refreshMediaTelemetry()
            return
        }

        if (!sbn.isOngoing && sbn.packageName != packageName) {
            val notifTitle = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString().orEmpty()
            val notifText = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString().orEmpty()
            val combined = if (notifTitle.isNotBlank() && notifText.isNotBlank()) "$notifTitle: $notifText" else notifTitle.ifBlank { notifText }
            if (combined.isNotBlank()) {
                recordAlert("NOTIF", sbn.packageName, combined, this)
            }
        }

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
            notifyTelemetryChanged()
        } else if (activeDownloads.containsKey(sbn.key)) {
            // If progress completed/removed
            activeDownloads.remove(sbn.key)
            notifyTelemetryChanged()
        }

        refreshMediaTelemetry()
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification?) {
        if (sbn == null) return
        if (activeDownloads.remove(sbn.key) != null) {
            notifyTelemetryChanged()
        }
    }

    private fun scanExistingNotifications() {
        try {
            val activeNotifs = activeNotifications ?: return
            activeDownloads.clear()
            for (sbn in activeNotifs) {
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
        try {
            mediaSessionManager?.removeOnActiveSessionsChangedListener(sessionsChangedListener)
            activeControllers.forEach { (controller, callback) ->
                try { controller.unregisterCallback(callback) } catch (e: Exception) { logSwallowed("LightspeedNotificationListener", "onDestroy:262", e) }
            }
            activeControllers.clear()
        } catch (e: Exception) { logSwallowed(TAG, "onDestroy:265", e) }
    }
}

/**
 * High-performance, memory-cached color extraction engine for notification and app icons.
 * Samples vibrant dominant colors for the Horizon Rail and dynamic HUD telemetry.
 */
object AppIconColorExtractor {

    private val colorCache = ConcurrentHashMap<String, Int>()

    fun extractBitmapColor(bitmap: Bitmap): Int {
        val sample = if (maxOf(bitmap.width, bitmap.height) > 96) {
            val scale = 96f / maxOf(bitmap.width, bitmap.height).toFloat()
            Bitmap.createScaledBitmap(
                bitmap,
                (bitmap.width * scale).toInt().coerceAtLeast(1),
                (bitmap.height * scale).toInt().coerceAtLeast(1),
                true
            )
        } else {
            bitmap
        }

        val w = sample.width
        val h = sample.height
        var dominantColor = Color.parseColor("#00E5FF")
        var maxVibrancy = -1f
        val hsv = FloatArray(3)

        val stepX = (w / 6).coerceAtLeast(1)
        val stepY = (h / 6).coerceAtLeast(1)

        for (x in stepX until w - stepX step stepX) {
            for (y in stepY until h - stepY step stepY) {
                val pixel = sample.getPixel(x, y)
                if (Color.alpha(pixel) > 180) {
                    Color.colorToHSV(pixel, hsv)
                    val saturation = hsv[1]
                    val value = hsv[2]
                    val vibrancy = saturation * 0.7f + value * 0.3f
                    if (vibrancy > maxVibrancy && value > 0.25f) {
                        maxVibrancy = vibrancy
                        dominantColor = pixel
                    }
                }
            }
        }
        if (sample !== bitmap && !sample.isRecycled) {
            sample.recycle()
        }
        return dominantColor
    }

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

            val dominantColor = extractBitmapColor(bitmap)
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

