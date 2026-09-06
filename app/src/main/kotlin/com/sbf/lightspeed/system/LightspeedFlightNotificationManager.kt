package com.sbf.lightspeed.system

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import com.sbf.lightspeed.R
import com.sbf.lightspeed.settings.SidebarSettingsActivity

/**
 * Manages the tactical ongoing notification in the Android notification drawer.
 * Provides 1-tap actions to toggle Master Flight Mode (Armed/Standby) and Flank Deflectors.
 */
object LightspeedFlightNotificationManager {

    const val CHANNEL_ID = "channel_flight_deck"
    const val NOTIFICATION_ID = 2001

    fun createChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Lightspeed Flight Controls",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Quick ongoing control deck to arm/disarm Lightspeed and deflectors"
                setShowBadge(false)
                enableVibration(false)
                enableLights(false)
            }
            nm?.createNotificationChannel(channel)
        }
    }

    fun update(context: Context) {
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return

        if (!LightspeedPreferences.isFlightNotificationEnabled(context)) {
            nm.cancel(NOTIFICATION_ID)
            return
        }

        createChannel(context)

        val isArmed = LightspeedPreferences.isMasterFlightArmed(context)
        val isLeftEnabled = LightspeedPreferences.isLeftDeflectorEnabled(context)
        val isRightEnabled = LightspeedPreferences.isRightDeflectorEnabled(context)

        val toggleFlightIntent = Intent(context, LightspeedAutomationReceiver::class.java).apply {
            action = LightspeedAutomationReceiver.ACTION_TOGGLE_FLIGHT_MODE
        }
        val pToggleFlight = PendingIntent.getBroadcast(
            context,
            101,
            toggleFlightIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val toggleLeftIntent = Intent(context, LightspeedAutomationReceiver::class.java).apply {
            action = LightspeedAutomationReceiver.ACTION_TOGGLE_LEFT_DEFLECTOR
        }
        val pToggleLeft = PendingIntent.getBroadcast(
            context,
            102,
            toggleLeftIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val toggleRightIntent = Intent(context, LightspeedAutomationReceiver::class.java).apply {
            action = LightspeedAutomationReceiver.ACTION_TOGGLE_RIGHT_DEFLECTOR
        }
        val pToggleRight = PendingIntent.getBroadcast(
            context,
            103,
            toggleRightIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val settingsIntent = Intent(context, SidebarSettingsActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        }
        val pSettings = PendingIntent.getActivity(
            context,
            104,
            settingsIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val statusSummary = buildString {
            append("Flight: ")
            append(if (isArmed) "ARMED ⚡" else "STANDBY ⏸")
            append("  |  L-Flank: ")
            append(if (isLeftEnabled) "ON" else "OFF")
            append("  |  R-Flank: ")
            append(if (isRightEnabled) "ON" else "OFF")
        }

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle("LIGHTSPEED FLIGHT DECK")
            .setContentText(statusSummary)
            .setOngoing(true)
            .setSilent(true)
            .setShowWhen(false)
            .setContentIntent(pSettings)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .addAction(
                0,
                if (isArmed) "⏸ STANDBY" else "⚡ ARM",
                pToggleFlight
            )
            .addAction(
                0,
                if (isLeftEnabled) "◀ L: OFF" else "◀ L: ON",
                pToggleLeft
            )
            .addAction(
                0,
                if (isRightEnabled) "R: OFF ▶" else "R: ON ▶",
                pToggleRight
            )
            .addAction(
                0,
                "⚙ COCKPIT",
                pSettings
            )
            .build()

        nm.notify(NOTIFICATION_ID, notification)
    }

    fun cancel(context: Context) {
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
        nm?.cancel(NOTIFICATION_ID)
    }
}
