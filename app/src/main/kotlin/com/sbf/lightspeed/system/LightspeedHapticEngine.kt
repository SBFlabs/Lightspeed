package com.sbf.lightspeed.system

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.util.Log

/**
 * High-performance centralized tactile and haptic feedback engine for Lightspeed.
 * Provides unified, low-latency haptic pulses across all overlay windows and UI controls.
 */
object LightspeedHapticEngine {
    private const val TAG = "LightspeedHaptics"

    private fun getVibrator(context: Context): Vibrator? {
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val vm = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
                vm?.defaultVibrator ?: (context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator)
            } else {
                @Suppress("DEPRECATION")
                context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to obtain Vibrator service", e)
            null
        }
    }

    /**
     * Executes a single vibration pulse with specified duration and amplitude.
     * @param context Android context
     * @param durationMs Duration in milliseconds (default 25ms)
     * @param amplitude Vibration strength 1..255 (default 140)
     */
    fun vibrate(context: Context, durationMs: Long = 25L, amplitude: Int = 140) {
        try {
            val vibrator = getVibrator(context) ?: return
            if (!vibrator.hasVibrator()) return

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val clampedAmp = amplitude.coerceIn(1, 255)
                vibrator.vibrate(VibrationEffect.createOneShot(durationMs, clampedAmp))
            } else {
                @Suppress("DEPRECATION")
                vibrator.vibrate(durationMs)
            }
        } catch (e: Exception) {
            Log.w(TAG, "Haptic pulse failed", e)
        }
    }

    /**
     * Subtle tactile tick for category scrubbing, ring rotation, and item highlighting.
     */
    fun tick(context: Context) {
        vibrate(context, durationMs = 15L, amplitude = 90)
    }

    /**
     * Standard tactile click for gesture recognition, button presses, and selection locks.
     */
    fun click(context: Context) {
        vibrate(context, durationMs = 30L, amplitude = 150)
    }

    /**
     * Heavy tactile pulse for macro actions, app launch breakthroughs, and hold gates.
     */
    fun heavyClick(context: Context) {
        vibrate(context, durationMs = 40L, amplitude = 210)
    }

    /**
     * Scrubbing milestone tick for volume, brightness, or timeout parameter adjustments.
     */
    fun scrubTick(context: Context) {
        vibrate(context, durationMs = 18L, amplitude = 130)
    }

    /**
     * Alarm haptic pulse when Eject mode is armed or critical threshold is breached.
     */
    fun alert(context: Context) {
        vibrate(context, durationMs = 50L, amplitude = 240)
    }
}
