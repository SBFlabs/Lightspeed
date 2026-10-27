package com.sbf.lightspeed.system.display

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import com.sbf.lightspeed.LightspeedAccessibilityService
import com.sbf.lightspeed.system.LightspeedPreferences
import com.sbf.lightspeed.system.defaultPrefs

/**
 * 20-20-20 Rule Screen-On Wellness Monitoring & Reminder Dispatcher.
 */
object LightspeedEyeRestMonitor {

    private val mainHandler = Handler(Looper.getMainLooper())
    private var screenOnStartMs: Long = 0L
    private var reminderRunnable: Runnable? = null
    private var isScreenInteractive: Boolean = false

    fun init(context: Context) {
        val prefs = context.defaultPrefs()
        if (prefs.getBoolean(LightspeedPreferences.KEY_EYE_REST_REMINDER_ENABLED, false)) {
            val interval = prefs.getInt(LightspeedPreferences.KEY_EYE_REST_INTERVAL_MIN, 20)
            isScreenInteractive = true
            screenOnStartMs = SystemClock.elapsedRealtime()
            startEyeRestMonitoring(context, interval)
        }
    }

    fun onScreenStateChanged(context: Context, isScreenOn: Boolean) {
        val prefs = context.defaultPrefs()
        if (!prefs.getBoolean(LightspeedPreferences.KEY_EYE_REST_REMINDER_ENABLED, false)) {
            stopEyeRestMonitoring()
            return
        }

        val intervalMin = prefs.getInt(LightspeedPreferences.KEY_EYE_REST_INTERVAL_MIN, 20)
        if (isScreenOn) {
            isScreenInteractive = true
            screenOnStartMs = SystemClock.elapsedRealtime()
            startEyeRestMonitoring(context, intervalMin)
        } else {
            isScreenInteractive = false
            stopEyeRestMonitoring()
        }
    }

    fun triggerReminder(context: Context) {
        val prefs = context.defaultPrefs()
        val strength = prefs.getString(LightspeedPreferences.KEY_EYE_REST_STRENGTH, "fullscreen") ?: "fullscreen"
        when (strength) {
            "floating" -> {
                LightspeedEyeRestOverlay.showFloating(context) {
                    resetTimer(context)
                }
            }
            "notification" -> {
                LightspeedEyeRestOverlay.dispatchNotification(context)
                val service = (context as? LightspeedAccessibilityService) ?: LightspeedAccessibilityService.instance
                service?.triggerDeflectorsGlow(800L)
                resetTimer(context)
            }
            else -> {
                LightspeedEyeRestOverlay.showFullscreen(
                    context = context,
                    onDismiss = { resetTimer(context) },
                    onSnooze = { snoozeTimer(context, 5) }
                )
            }
        }
    }

    private fun resetTimer(context: Context) {
        val prefs = context.defaultPrefs()
        val intervalMin = prefs.getInt(LightspeedPreferences.KEY_EYE_REST_INTERVAL_MIN, 20)
        screenOnStartMs = SystemClock.elapsedRealtime()
        if (isScreenInteractive) {
            startEyeRestMonitoring(context, intervalMin)
        }
    }

    private fun snoozeTimer(context: Context, snoozeMin: Int) {
        screenOnStartMs = SystemClock.elapsedRealtime()
        startEyeRestMonitoring(context, snoozeMin)
    }

    private fun startEyeRestMonitoring(context: Context, intervalMin: Int) {
        stopEyeRestMonitoring()
        if (intervalMin <= 0) return

        val intervalMs = intervalMin * 60 * 1000L
        val runnable = object : Runnable {
            override fun run() {
                if (!isScreenInteractive) return
                val elapsed = SystemClock.elapsedRealtime() - screenOnStartMs
                if (elapsed >= intervalMs) {
                    triggerReminder(context)
                } else {
                    mainHandler.postDelayed(this, 10_000L)
                }
            }
        }
        reminderRunnable = runnable
        mainHandler.postDelayed(runnable, 10_000L)
    }

    private fun stopEyeRestMonitoring() {
        reminderRunnable?.let { mainHandler.removeCallbacks(it) }
        reminderRunnable = null
    }
}
