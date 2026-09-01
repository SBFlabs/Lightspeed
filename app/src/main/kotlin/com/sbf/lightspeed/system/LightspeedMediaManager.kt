package com.sbf.lightspeed.system

import android.content.Context
import android.media.AudioManager
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.util.Log
import android.view.KeyEvent
import com.sbf.lightspeed.LightspeedAccessibilityService

/**
 * Tactical Media Playback & Interactive Timeline Scrubbing Engine for Lightspeed.
 *
 * Interfaces with active Android MediaSession/MediaController instances to query track
 * metadata (title, artist, position, duration) and execute seek/skip/scrub operations.
 * Provides zero-crash fallbacks via simulated fast-forward/rewind pulses and AudioManager
 * media key events when NotificationListenerService permission is ungranted.
 */
object LightspeedMediaManager {

    private const val TAG = "LightspeedMedia"

    data class MediaTrackInfo(
        val title: String,
        val artist: String,
        val album: String,
        val positionMs: Long,
        val durationMs: Long,
        val isPlaying: Boolean,
        val packageName: String?,
        val coverArtColor: Int? = null
    )

    private var cachedEstimatedPositionMs = 0L
    private var lastScrubTimestamp = 0L

    fun onNotificationListenerConnected() {
        Log.i(TAG, "Notification listener connected — full MediaSession control active")
    }

    private fun getMediaSessionManager(context: Context): MediaSessionManager? {
        return context.getSystemService(Context.MEDIA_SESSION_SERVICE) as? MediaSessionManager
    }

    fun getActiveControllers(context: Context): List<MediaController> {
        return try {
            val sessionManager = getMediaSessionManager(context) ?: return emptyList()
            val component = LightspeedNotificationListener.getComponentName(context)
            sessionManager.getActiveSessions(component)
        } catch (e: SecurityException) {
            // Notification access not granted
            emptyList()
        } catch (e: Exception) {
            Log.d(TAG, "Failed to retrieve active sessions: ${e.message}")
            emptyList()
        }
    }

    fun getPrimaryController(context: Context): MediaController? {
        val controllers = getActiveControllers(context)
        return controllers.firstOrNull { it.playbackState?.state == PlaybackState.STATE_PLAYING }
            ?: controllers.firstOrNull()
    }

    fun getActiveTrackInfo(context: Context): MediaTrackInfo {
        val controller = getPrimaryController(context)
        if (controller != null) {
            val metadata = controller.metadata
            val playbackState = controller.playbackState

            val rawTitle = metadata?.getString(MediaMetadata.METADATA_KEY_TITLE)
                ?: metadata?.getString(MediaMetadata.METADATA_KEY_DISPLAY_TITLE)
                ?: ""
            val rawArtist = metadata?.getString(MediaMetadata.METADATA_KEY_ARTIST)
                ?: metadata?.getString(MediaMetadata.METADATA_KEY_ALBUM_ARTIST)
                ?: metadata?.getString(MediaMetadata.METADATA_KEY_AUTHOR)
                ?: ""
            val album = metadata?.getString(MediaMetadata.METADATA_KEY_ALBUM) ?: ""

            fun cleanMeta(str: String): String {
                val t = str.trim()
                return if (t.equals("Now Playing", ignoreCase = true) ||
                    t.equals("Unknown Artist", ignoreCase = true) ||
                    t.equals("Unknown", ignoreCase = true) ||
                    t.equals("null", ignoreCase = true) ||
                    t.equals("Active Media Playback", ignoreCase = true)) "" else t
            }

            val title = cleanMeta(rawTitle)
            val artist = cleanMeta(rawArtist)
            val duration = metadata?.getLong(MediaMetadata.METADATA_KEY_DURATION) ?: 0L
            val position = playbackState?.position ?: 0L
            val isPlaying = playbackState?.state == PlaybackState.STATE_PLAYING

            val artBitmap = try {
                metadata?.getBitmap(MediaMetadata.METADATA_KEY_ART)
                    ?: metadata?.getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART)
                    ?: controller.metadata?.description?.iconBitmap
            } catch (_: Exception) { null }

            val coverArtColor = artBitmap?.let { AppIconColorExtractor.extractBitmapColor(it) }

            return MediaTrackInfo(
                title = title,
                artist = artist,
                album = album,
                positionMs = position.coerceAtLeast(0L),
                durationMs = duration.coerceAtLeast(0L),
                isPlaying = isPlaying,
                packageName = controller.packageName,
                coverArtColor = coverArtColor
            )
        }

        return MediaTrackInfo(
            title = "",
            artist = "",
            album = "",
            positionMs = cachedEstimatedPositionMs,
            durationMs = 0L,
            isPlaying = false,
            packageName = null,
            coverArtColor = null
        )
    }

    fun playPause(context: Context) {
        val controller = getPrimaryController(context)
        if (controller != null) {
            val isPlaying = controller.playbackState?.state == PlaybackState.STATE_PLAYING
            if (isPlaying) {
                controller.transportControls.pause()
            } else {
                controller.transportControls.play()
            }
        } else {
            sendMediaKeyEvent(context, KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE)
        }
    }

    fun next(context: Context) {
        val controller = getPrimaryController(context)
        if (controller != null) {
            controller.transportControls.skipToNext()
        } else {
            sendMediaKeyEvent(context, KeyEvent.KEYCODE_MEDIA_NEXT)
        }
    }

    fun previous(context: Context) {
        val controller = getPrimaryController(context)
        if (controller != null) {
            controller.transportControls.skipToPrevious()
        } else {
            sendMediaKeyEvent(context, KeyEvent.KEYCODE_MEDIA_PREVIOUS)
        }
    }

    fun skipForward(context: Context, seconds: Int? = null) {
        val skipSec = seconds ?: context.defaultPrefs().getInt(LightspeedPreferences.KEY_MEDIA_SKIP_SECONDS, 10)
        scrubRelative(context, skipSec * 1000L)
    }

    fun skipBackward(context: Context, seconds: Int? = null) {
        val skipSec = seconds ?: context.defaultPrefs().getInt(LightspeedPreferences.KEY_MEDIA_SKIP_SECONDS, 10)
        scrubRelative(context, -(skipSec * 1000L))
    }

    fun seekTo(context: Context, positionMs: Long) {
        val controller = getPrimaryController(context)
        if (controller != null) {
            val duration = controller.metadata?.getLong(MediaMetadata.METADATA_KEY_DURATION) ?: 0L
            val target = if (duration > 0L) positionMs.coerceIn(0L, duration) else positionMs.coerceAtLeast(0L)
            try {
                controller.transportControls.seekTo(target)
                cachedEstimatedPositionMs = target
                return
            } catch (e: Exception) {
                Log.w(TAG, "seekTo failed on controller", e)
            }
        }

        cachedEstimatedPositionMs = positionMs.coerceAtLeast(0L)
    }

    fun scrubRelative(context: Context, deltaMs: Long) {
        val controller = getPrimaryController(context)
        if (controller != null) {
            val curPos = controller.playbackState?.position ?: cachedEstimatedPositionMs
            val duration = controller.metadata?.getLong(MediaMetadata.METADATA_KEY_DURATION) ?: 0L
            val maxPos = if (duration > 0L) duration else Long.MAX_VALUE
            val target = (curPos + deltaMs).coerceIn(0L, maxPos)
            try {
                controller.transportControls.seekTo(target)
                cachedEstimatedPositionMs = target
                return
            } catch (e: Exception) {
                Log.w(TAG, "scrubRelative seekTo failed", e)
            }
        }

        // Fallback: Continuous simulated fast-forward / rewind pulses
        if (deltaMs > 0) {
            sendMediaKeyEvent(context, KeyEvent.KEYCODE_MEDIA_FAST_FORWARD)
        } else if (deltaMs < 0) {
            sendMediaKeyEvent(context, KeyEvent.KEYCODE_MEDIA_REWIND)
        }
    }

    fun stop(context: Context) {
        val controller = getPrimaryController(context)
        if (controller != null) {
            controller.transportControls.stop()
        } else {
            sendMediaKeyEvent(context, KeyEvent.KEYCODE_MEDIA_STOP)
        }
    }

    fun showScrubber(context: Context) {
        LightspeedAccessibilityService.instance?.showMediaScrubber()
    }

    fun stepHardwareScrubber(context: Context, isForward: Boolean) {
        val deltaMs = if (isForward) 2500L else -2500L
        scrubRelative(context, deltaMs)
        LightspeedAccessibilityService.instance?.updateMediaScrubberProgress()
    }

    fun sendMediaKeyEvent(context: Context, keyCode: Int) {
        try {
            val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
            val down = KeyEvent(KeyEvent.ACTION_DOWN, keyCode)
            val up = KeyEvent(KeyEvent.ACTION_UP, keyCode)
            audioManager?.dispatchMediaKeyEvent(down)
            audioManager?.dispatchMediaKeyEvent(up)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to dispatch media key event $keyCode", e)
        }
    }
}
