package com.sbf.lightspeed.system

import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import com.sbf.lightspeed.LightspeedAccessibilityService
import com.sbf.lightspeed.MainActivity
import com.sbf.lightspeed.R

class LightspeedRevivalReceiver : BroadcastReceiver() {
    companion object {
        private const val TAG = "LightspeedRevival"
        private const val NOTIFICATION_ID = 8842
    }

    override fun onReceive(context: Context, intent: Intent?) {
        val prefs = context.defaultPrefs()
        val errorMsg = intent?.getStringExtra(LightspeedCrashSentinel.EXTRA_CRASH_REASON)
            ?: prefs.getString("key_last_crash_message", "Unexpected Process Termination")
            ?: "Unexpected Process Termination"

        Log.i(TAG, "Revival Receiver invoked. Reason: $errorMsg")

        // 1. User Choice Honor: If user intentionally disabled service, do NOT revive!
        val isIntentionallyStopped = prefs.getBoolean("pref_service_intentionally_stopped", false)
        if (isIntentionallyStopped) {
            Log.i(TAG, "Service is marked intentionally stopped by user. Revival aborted.")
            return
        }

        // Also verify whether user turned off the service in system accessibility settings
        if (!LightspeedWatchdogEngine.isAccessibilityServiceEnabled(context)) {
            Log.i(TAG, "Service is not enabled in secure settings (manually toggled off). Revival aborted.")
            return
        }

        // 2. Check if service is already active
        if (LightspeedAccessibilityService.instance != null) {
            Log.i(TAG, "Service is already online.")
            return
        }

        // 3. Attempt revival via Shizuku / Root privileged interface
        var revived = false
        if (ElevatedTaskCloser.isShizukuActive || ElevatedTaskCloser.isRootActive) {
            revived = LightspeedWatchdogEngine.reviveAccessibilityService(context)
            Log.i(TAG, "Autonomous revival command dispatched: $revived")
        }

        // 4. Send Telemetry Notification / Message to user
        sendTelemetryNotification(context, errorMsg, revived)
    }

    private fun sendTelemetryNotification(context: Context, errorReason: String, revived: Boolean) {
        try {
            LightspeedCrashSentinel.createNotificationChannel(context)
            val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return

            val openIntent = Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
            val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            } else {
                PendingIntent.FLAG_UPDATE_CURRENT
            }
            val pendingIntent = PendingIntent.getActivity(context, 0, openIntent, flags)

            val title = if (revived) {
                "⚡ LIGHTSPEED // CORE REVIVED"
            } else {
                "⚠️ LIGHTSPEED // CORE ANOMALY DETECTED"
            }

            val body = if (revived) {
                "Autonomous revival successful after anomaly: $errorReason"
            } else {
                "Interrupted by anomaly: $errorReason. Tap to launch & restore."
            }

            val builder = NotificationCompat.Builder(context, LightspeedCrashSentinel.CHANNEL_ID)
                .setSmallIcon(R.mipmap.ic_launcher)
                .setContentTitle(title)
                .setContentText(body)
                .setStyle(NotificationCompat.BigTextStyle().bigText("$body\n\nFlight Recorder telemetry logged."))
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setAutoCancel(true)
                .setContentIntent(pendingIntent)

            nm.notify(NOTIFICATION_ID, builder.build())
        } catch (e: Exception) {
            Log.e(TAG, "Failed to dispatch revival notification", e)
        }
    }
}
