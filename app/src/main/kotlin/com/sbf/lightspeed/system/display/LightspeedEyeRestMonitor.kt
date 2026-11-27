package com.sbf.lightspeed.system.display

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import com.sbf.lightspeed.LightspeedAccessibilityService
import com.sbf.lightspeed.system.LightspeedPreferences
import com.sbf.lightspeed.system.NightlyFeatures
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
        val appCtx = context.applicationContext ?: context
        if (!NightlyFeatures.isEyeRestTimerEnabled) {
            stopEyeRestMonitoring()
            return
        }
        val prefs = appCtx.defaultPrefs()
        if (prefs.getBoolean(LightspeedPreferences.KEY_EYE_REST_REMINDER_ENABLED, false)) {
            val interval = prefs.getInt(LightspeedPreferences.KEY_EYE_REST_INTERVAL_MIN, 20)
            isScreenInteractive = true
            screenOnStartMs = SystemClock.elapsedRealtime()
            startEyeRestMonitoring(appCtx, interval)
        }
    }

    fun onScreenStateChanged(context: Context, isScreenOn: Boolean) {
        val appCtx = context.applicationContext ?: context
        if (!NightlyFeatures.isEyeRestTimerEnabled) {
            stopEyeRestMonitoring()
            return
        }
        val prefs = appCtx.defaultPrefs()
        if (!prefs.getBoolean(LightspeedPreferences.KEY_EYE_REST_REMINDER_ENABLED, false)) {
            stopEyeRestMonitoring()
            return
        }

        val intervalMin = prefs.getInt(LightspeedPreferences.KEY_EYE_REST_INTERVAL_MIN, 20)
        if (isScreenOn) {
            isScreenInteractive = true
            screenOnStartMs = SystemClock.elapsedRealtime()
            startEyeRestMonitoring(appCtx, intervalMin)
        } else {
            isScreenInteractive = false
            stopEyeRestMonitoring()
        }
    }

    fun onIntervalChanged(context: Context, intervalMin: Int) {
        val appCtx = context.applicationContext ?: context
        if (!NightlyFeatures.isEyeRestTimerEnabled) {
            stopEyeRestMonitoring()
            return
        }
        if (!appCtx.defaultPrefs().getBoolean(LightspeedPreferences.KEY_EYE_REST_REMINDER_ENABLED, false)) {
            stopEyeRestMonitoring()
            return
        }
        if (screenOnStartMs == 0L) {
            screenOnStartMs = SystemClock.elapsedRealtime()
        }
        isScreenInteractive = true
        startEyeRestMonitoring(appCtx, intervalMin)
    }

    fun triggerReminder(context: Context) {
        val appCtx = context.applicationContext ?: context
        if (!NightlyFeatures.isEyeRestTimerEnabled) {
            stopEyeRestMonitoring()
            return
        }
        val prefs = appCtx.defaultPrefs()
        val strength = prefs.getString(LightspeedPreferences.KEY_EYE_REST_STRENGTH, "fullscreen") ?: "fullscreen"
        when (strength) {
            "floating" -> {
                LightspeedEyeRestOverlay.showFloating(appCtx) {
                    resetTimer(appCtx)
                }
            }
            "notification" -> {
                LightspeedEyeRestOverlay.dispatchNotification(appCtx)
                LightspeedAccessibilityService.instance?.triggerDeflectorsGlow(800L)
                resetTimer(appCtx)
            }
            else -> {
                LightspeedEyeRestOverlay.showFullscreen(
                    context = appCtx,
                    onDismiss = { resetTimer(appCtx) },
                    onSnooze = { snoozeTimer(appCtx, 5) }
                )
            }
        }
    }

    private fun resetTimer(context: Context) {
        val appCtx = context.applicationContext ?: context
        val prefs = appCtx.defaultPrefs()
        val intervalMin = prefs.getInt(LightspeedPreferences.KEY_EYE_REST_INTERVAL_MIN, 20)
        screenOnStartMs = SystemClock.elapsedRealtime()
        if (isScreenInteractive) {
            startEyeRestMonitoring(appCtx, intervalMin)
        }
    }

    private fun snoozeTimer(context: Context, snoozeMin: Int) {
        val appCtx = context.applicationContext ?: context
        screenOnStartMs = SystemClock.elapsedRealtime()
        startEyeRestMonitoring(appCtx, snoozeMin)
    }

    private fun startEyeRestMonitoring(context: Context, intervalMin: Int) {
        stopEyeRestMonitoring()
        if (intervalMin <= 0) return

        val appCtx = context.applicationContext ?: context
        val intervalMs = intervalMin * 60 * 1000L
        val runnable = object : Runnable {
            override fun run() {
                if (!isScreenInteractive) return
                val elapsed = SystemClock.elapsedRealtime() - screenOnStartMs
                if (elapsed >= intervalMs) {
                    triggerReminder(appCtx)
                } else {
                    mainHandler.postDelayed(this, 10_000L)
                }
            }
        }
        reminderRunnable = runnable
        mainHandler.postDelayed(runnable, 10_000L)
    }

    fun stopEyeRestMonitoring() {
        reminderRunnable?.let { mainHandler.removeCallbacks(it) }
        reminderRunnable = null
    }
}
