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
        val stackTrace = Log.getStackTraceString(throwable)
        val timestamp = System.currentTimeMillis()

        Log.e(TAG, "FATAL ANOMALY on thread ${thread.name}: $errorMsg\n$stackTrace")

        // 1. Record Crash Telemetry in persistent preferences
        prefs.edit()
            .putLong("key_last_crash_timestamp", timestamp)
            .putString("key_last_crash_message", errorMsg)
            .putString("key_last_crash_stack", stackTrace.take(4000))
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
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                if (alarmManager?.canScheduleExactAlarms() == true) {
                    alarmManager.setExactAndAllowWhileIdle(AlarmManager.ELAPSED_REALTIME_WAKEUP, triggerAt, pendingIntent)
                } else {
                    alarmManager?.setAndAllowWhileIdle(AlarmManager.ELAPSED_REALTIME_WAKEUP, triggerAt, pendingIntent)
                }
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                alarmManager?.setExactAndAllowWhileIdle(AlarmManager.ELAPSED_REALTIME_WAKEUP, triggerAt, pendingIntent)
            } else {
                alarmManager?.setExact(AlarmManager.ELAPSED_REALTIME_WAKEUP, triggerAt, pendingIntent)
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
}
