package com.sbf.lightspeed.system

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.SystemClock
import android.util.Log
import com.sbf.lightspeed.LightspeedAccessibilityService

object LightspeedCrashSentinel {
    private const val TAG = "LightspeedCrashSentinel"
    const val CHANNEL_ID = "lightspeed_sentinel_channel"
    const val ACTION_REVIVE = "com.sbf.lightspeed.action.REVIVE_SENTINEL"
    const val EXTRA_CRASH_REASON = "extra_crash_reason"

    fun init(context: Context) {
        val defaultHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            try {
                handleUncaughtException(context.applicationContext, thread, throwable)
            } catch (e: Exception) {
                Log.e(TAG, "Error in crash sentinel handler", e)
            } finally {
                defaultHandler?.uncaughtException(thread, throwable)
            }
        }
    }

    private fun handleUncaughtException(context: Context, thread: Thread, throwable: Throwable) {
        val prefs = context.defaultPrefs()
        val errorMsg = throwable.message ?: throwable.javaClass.simpleName
        val timestamp = System.currentTimeMillis()

        // Full trace only goes to logcat (stripped by R8 in release via -assumenosideeffects)
        Log.e(TAG, "FATAL ANOMALY on thread ${thread.name}: $errorMsg", throwable)

        // 1. Record sanitized crash telemetry in persistent preferences.
        // Stores only the exception type + top 8 frames to avoid writing a full
        // internal class-path dump into a user-readable SharedPreferences store.
        val sanitizedTrace = buildSanitizedTrace(throwable)
        prefs.edit()
            .putLong("key_last_crash_timestamp", timestamp)
            .putString("key_last_crash_message", errorMsg)
            .putString("key_last_crash_stack", sanitizedTrace)
            .putBoolean("key_has_unreported_crash", true)
            .apply()

        // 2. Check if the user intentionally stopped the service
        val isIntentionallyStopped = prefs.getBoolean("pref_service_intentionally_stopped", false)
        if (isIntentionallyStopped) {
            Log.i(TAG, "Service was intentionally stopped by user. Skipping revival.")
            return
        }

        // 3. Check if Crash Sentinel revival is enabled by user in Core Watchdog
        val isCrashSentinelEnabled = prefs.getBoolean(LightspeedPreferences.KEY_CRASH_SENTINEL_ENABLED, true)
        if (!isCrashSentinelEnabled) {
            Log.i(TAG, "Crash Sentinel revival is disabled in Core Watchdog. Skipping revival.")
            return
        }

        // 4. Schedule Revival via AlarmManager (1 second later)
        try {
            val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager
            val intent = Intent(context, LightspeedRevivalReceiver::class.java).apply {
                action = ACTION_REVIVE
                putExtra(EXTRA_CRASH_REASON, errorMsg)
            }
            val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            } else {
                PendingIntent.FLAG_UPDATE_CURRENT
            }
            val pendingIntent = PendingIntent.getBroadcast(context, 9110, intent, flags)

            val triggerAt = SystemClock.elapsedRealtime() + 1000L
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                alarmManager?.setAndAllowWhileIdle(AlarmManager.ELAPSED_REALTIME_WAKEUP, triggerAt, pendingIntent)
            } else {
                alarmManager?.set(AlarmManager.ELAPSED_REALTIME_WAKEUP, triggerAt, pendingIntent)
            }
            Log.i(TAG, "Resuscitation alarm dispatched for 1000ms")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to schedule revival alarm", e)
        }
    }

     fun createNotificationChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Lightspeed Sentinel Telemetry",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Dispatches crash alerts and autonomous revival telemetry"
                enableVibration(true)
            }
            nm?.createNotificationChannel(channel)
        }
    }

    /**
     * Produces a compact crash summary for persistent storage.
     * Retains the exception type + first 8 stack frames only.
     * Remaining frames are replaced with a count to avoid storing full
     * internal package paths in a user-readable SharedPreferences file.
     */
    private fun buildSanitizedTrace(throwable: Throwable): String {
        val sb = StringBuilder()
        var current: Throwable? = throwable
        var depth = 0
        while (current != null && depth < 3) {
            if (depth > 0) sb.append("\nCaused by: ")
            sb.append(current.javaClass.name)
            if (!current.message.isNullOrBlank()) sb.append(": ").append(current.message)
            val frames = current.stackTrace
            val kept = frames.take(8)
            kept.forEach { frame -> sb.append("\n  at ").append(frame.toString()) }
            val remaining = frames.size - kept.size
            if (remaining > 0) sb.append("\n  ... $remaining more frames")
            current = current.cause
            depth++
        }
        return sb.toString().take(2000)
    }
}
