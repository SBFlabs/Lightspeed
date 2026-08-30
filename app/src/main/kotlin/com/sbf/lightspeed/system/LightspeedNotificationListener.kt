package com.sbf.lightspeed.system

import android.app.Notification
import android.content.ComponentName
import android.content.Context
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
        val lastUpdated: Long = System.currentTimeMillis()
    )

    data class MediaTelemetry(
        val title: String,
        val artist: String,
        val isPlaying: Boolean,
        val positionMs: Long,
        val durationMs: Long,
        val packageName: String?
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

            val telemetry = DownloadTelemetry(
                key = sbn.key,
                title = title,
                progress = progress,
                max = max,
                progressFraction = fraction,
                isIndeterminate = indeterminate,
                packageName = sbn.packageName
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
            activeMediaTelemetry = MediaTelemetry(
                title = info.title,
                artist = info.artist,
                isPlaying = info.isPlaying,
                positionMs = info.positionMs,
                durationMs = info.durationMs,
                packageName = info.packageName
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

