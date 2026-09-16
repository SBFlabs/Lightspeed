package com.sbf.lightspeed.system

import android.content.Context
import android.media.AudioAttributes
import android.os.Build
import android.os.VibrationAttributes
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
                @Suppress("DEPRECATION")
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

            val clampedAmp = amplitude.coerceIn(1, 255)

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                val effect = if (durationMs <= 20L) {
                    VibrationEffect.createPredefined(VibrationEffect.EFFECT_TICK)
                } else if (durationMs <= 35L) {
                    VibrationEffect.createPredefined(VibrationEffect.EFFECT_CLICK)
                } else if (durationMs <= 60L) {
                    VibrationEffect.createPredefined(VibrationEffect.EFFECT_HEAVY_CLICK)
                } else {
                    VibrationEffect.createOneShot(durationMs, clampedAmp)
                }
                val attrs = VibrationAttributes.Builder()
                    .setUsage(VibrationAttributes.USAGE_TOUCH)
                    .build()
                try {
                    vibrator.vibrate(effect, attrs)
                } catch (_: Exception) {
                    vibrator.vibrate(VibrationEffect.createOneShot(durationMs, clampedAmp), attrs)
                }
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val effect = if (durationMs <= 20L) {
                    VibrationEffect.createPredefined(VibrationEffect.EFFECT_TICK)
                } else if (durationMs <= 35L) {
                    VibrationEffect.createPredefined(VibrationEffect.EFFECT_CLICK)
                } else if (durationMs <= 60L) {
                    VibrationEffect.createPredefined(VibrationEffect.EFFECT_HEAVY_CLICK)
                } else {
                    VibrationEffect.createOneShot(durationMs, clampedAmp)
                }
                val audioAttrs = AudioAttributes.Builder()
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
                    .build()
                @Suppress("DEPRECATION")
                try {
                    vibrator.vibrate(effect, audioAttrs)
                } catch (_: Exception) {
                    vibrator.vibrate(VibrationEffect.createOneShot(durationMs, clampedAmp), audioAttrs)
                }
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
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
        vibrate(context, durationMs = 15L, amplitude = 110)
    }

    /**
     * Standard tactile click for gesture recognition, button presses, and selection locks.
     */
    fun click(context: Context) {
        vibrate(context, durationMs = 28L, amplitude = 170)
    }

    /**
     * Heavy tactile pulse for macro actions, app launch breakthroughs, and hold gates.
     */
    fun heavyClick(context: Context) {
        vibrate(context, durationMs = 45L, amplitude = 220)
    }

    /**
     * Scrubbing milestone tick for volume, brightness, or timeout parameter adjustments.
     */
    fun scrubTick(context: Context) {
        vibrate(context, durationMs = 18L, amplitude = 150)
    }

    /**
     * Alarm haptic pulse when Eject mode is armed or critical threshold is breached.
     */
    fun alert(context: Context) {
        vibrate(context, durationMs = 60L, amplitude = 255)
    }
}
