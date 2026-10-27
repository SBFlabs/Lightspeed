package com.sbf.lightspeed.system

import android.content.Context
import android.media.AudioAttributes
import android.os.Build
import android.os.SystemClock
import android.os.VibrationAttributes
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.util.Log

/**
 * High-performance centralized tactile and haptic feedback engine for Lightspeed.
 * Provides unified, low-latency haptic pulses with granular domain profiles
 * and hardware-agnostic tactile throttling.
 */
object LightspeedHapticEngine {
    private const val TAG = "LightspeedHaptics"
    @Volatile private var lastTickUptimeMs: Long = 0L

    val STANDARD_PROFILES = listOf(
        "off" to "Off",
        "soft_low_freq" to "Soft (Low-Freq)",
        "tactical" to "Crisp (Standard)",
        "heavy" to "Heavy (Mechanical)"
    )

    val EXTENDED_PROFILES = listOf(
        "off" to "Off",
        "jumps_only" to "Transitions Only",
        "soft_low_freq" to "Soft (Low-Freq)",
        "tactical" to "Crisp (Standard)",
        "heavy" to "Heavy (Mechanical)"
    )

    private fun getVibrator(context: Context): Vibrator? {
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val vm = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
                vm?.defaultVibrator ?: context.getSystemService(Vibrator::class.java)
            } else {
                context.getSystemService(Vibrator::class.java)
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to obtain Vibrator service", e)
            null
        }
    }

    /**
     * Gets the configured minimum interval in ms between consecutive ticks.
     */
    fun getCooldownMs(context: Context): Long {
        return try {
            context.defaultPrefs().getInt(
                LightspeedPreferences.KEY_HAPTIC_COOLDOWN_MS,
                LightspeedPreferences.DEFAULT_HAPTIC_COOLDOWN_MS
            ).coerceIn(0, 200).toLong()
        } catch (_: Exception) {
            LightspeedPreferences.DEFAULT_HAPTIC_COOLDOWN_MS.toLong()
        }
    }

    /**
     * Enforces the user's cooldown threshold between ticks.
     */
    private fun shouldThrottle(context: Context): Boolean {
        val cooldown = getCooldownMs(context)
        val now = SystemClock.uptimeMillis()
        if (cooldown > 0L && (now - lastTickUptimeMs) < cooldown) {
            return true
        }
        lastTickUptimeMs = now
        return false
    }

    /**
     * Executes a single vibration pulse with specified duration and amplitude.
     */
    fun vibrate(context: Context, durationMs: Long = 25L, amplitude: Int = 140) {
        val profile = context.defaultPrefs().getString(LightspeedPreferences.KEY_HAPTIC_PROFILE, "tactical") ?: "tactical"
        if (profile == "off") return

        try {
            val vibrator = getVibrator(context) ?: return
            if (!vibrator.hasVibrator()) return

            val clampedAmp = amplitude.coerceIn(1, 255)
            val hasAmp = vibrator.hasAmplitudeControl()
            // On devices without amplitude control (e.g. tablets with ERM motors), ensure sufficient duration to overcome inertia
            val effectiveDuration = if (!hasAmp) durationMs.coerceAtLeast(30L) else durationMs

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                val effect = if (!hasAmp) {
                    if (durationMs <= 25L) VibrationEffect.createPredefined(VibrationEffect.EFFECT_CLICK)
                    else VibrationEffect.createPredefined(VibrationEffect.EFFECT_HEAVY_CLICK)
                } else if (durationMs <= 20L) {
                    VibrationEffect.createPredefined(VibrationEffect.EFFECT_TICK)
                } else if (durationMs <= 35L) {
                    VibrationEffect.createPredefined(VibrationEffect.EFFECT_CLICK)
                } else if (durationMs <= 60L) {
                    VibrationEffect.createPredefined(VibrationEffect.EFFECT_HEAVY_CLICK)
                } else {
                    VibrationEffect.createOneShot(effectiveDuration, clampedAmp)
                }
                val attrs = VibrationAttributes.Builder()
                    .setUsage(VibrationAttributes.USAGE_HARDWARE_FEEDBACK)
                    .build()
                try {
                    vibrator.vibrate(effect, attrs)
                } catch (_: Exception) {
                    vibrator.vibrate(VibrationEffect.createOneShot(effectiveDuration, clampedAmp), attrs)
                }
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val effect = if (!hasAmp) {
                    if (durationMs <= 25L) VibrationEffect.createPredefined(VibrationEffect.EFFECT_CLICK)
                    else VibrationEffect.createPredefined(VibrationEffect.EFFECT_HEAVY_CLICK)
                } else if (durationMs <= 20L) {
                    VibrationEffect.createPredefined(VibrationEffect.EFFECT_TICK)
                } else if (durationMs <= 35L) {
                    VibrationEffect.createPredefined(VibrationEffect.EFFECT_CLICK)
                } else if (durationMs <= 60L) {
                    VibrationEffect.createPredefined(VibrationEffect.EFFECT_HEAVY_CLICK)
                } else {
                    VibrationEffect.createOneShot(effectiveDuration, clampedAmp)
                }
                val audioAttrs = AudioAttributes.Builder()
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
                    .build()
                @Suppress("DEPRECATION")
                try {
                    vibrator.vibrate(effect, audioAttrs)
                } catch (_: Exception) {
                    vibrator.vibrate(VibrationEffect.createOneShot(effectiveDuration, clampedAmp), audioAttrs)
                }
            } else {
                vibrator.vibrate(VibrationEffect.createOneShot(effectiveDuration, clampedAmp))
            }
        } catch (e: Exception) {
            Log.w(TAG, "Haptic pulse failed", e)
        }
    }

    /**
     * Direct vibration bypassing predefined effects to produce soft low-frequency pulses.
     */
    private fun vibrateDirect(context: Context, durationMs: Long, amplitude: Int) {
        try {
            val vibrator = getVibrator(context) ?: return
            if (!vibrator.hasVibrator()) return
            val clampedAmp = amplitude.coerceIn(1, 255)
            val effectiveDuration = if (!vibrator.hasAmplitudeControl()) durationMs.coerceAtLeast(30L) else durationMs
            val effect = VibrationEffect.createOneShot(effectiveDuration, clampedAmp)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                val attrs = VibrationAttributes.Builder().setUsage(VibrationAttributes.USAGE_HARDWARE_FEEDBACK).build()
                vibrator.vibrate(effect, attrs)
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val audioAttrs = AudioAttributes.Builder()
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION).build()
                @Suppress("DEPRECATION")
                vibrator.vibrate(effect, audioAttrs)
            } else {
                vibrator.vibrate(effect)
            }
        } catch (_: Exception) {}
    }

    fun pulseTick(context: Context, profile: String) {
        if (profile.startsWith("custom_")) {
            val pct = profile.removePrefix("custom_").toIntOrNull()?.coerceIn(0, 100) ?: 50
            if (pct <= 0) return
            val duration = (10L + (18L * pct / 100f)).toLong()
            val amp = (40 + (160 * pct / 100f)).toInt()
            vibrateDirect(context, durationMs = duration, amplitude = amp)
            return
        }
        when (profile) {
            "soft_low_freq", "soft" -> vibrateDirect(context, durationMs = 12L, amplitude = 65)
            "heavy" -> vibrate(context, durationMs = 24L, amplitude = 180)
            else -> vibrate(context, durationMs = 15L, amplitude = 110)
        }
    }

    fun pulseClick(context: Context, profile: String) {
        if (profile.startsWith("custom_")) {
            val pct = profile.removePrefix("custom_").toIntOrNull()?.coerceIn(0, 100) ?: 50
            if (pct <= 0) return
            val duration = (15L + (25L * pct / 100f)).toLong()
            val amp = (60 + (195 * pct / 100f)).toInt()
            vibrateDirect(context, durationMs = duration, amplitude = amp)
            return
        }
        when (profile) {
            "soft_low_freq", "soft" -> vibrateDirect(context, durationMs = 18L, amplitude = 90)
            "heavy" -> vibrate(context, durationMs = 38L, amplitude = 220)
            else -> vibrate(context, durationMs = 28L, amplitude = 170)
        }
    }

    // ==========================================
    // 1. SYSTEM DOMAIN (KEY_HAPTIC_PROFILE)
    // ==========================================

    fun tick(context: Context) {
        val profile = context.defaultPrefs().getString(LightspeedPreferences.KEY_HAPTIC_PROFILE, "tactical") ?: "tactical"
        if (profile == "off" || profile == "jumps_only") return
        if (shouldThrottle(context)) return
        pulseTick(context, profile)
    }

    fun click(context: Context) {
        val profile = context.defaultPrefs().getString(LightspeedPreferences.KEY_HAPTIC_PROFILE, "tactical") ?: "tactical"
        if (profile == "off") return
        pulseClick(context, profile)
    }

    fun heavyClick(context: Context) {
        val profile = context.defaultPrefs().getString(LightspeedPreferences.KEY_HAPTIC_PROFILE, "tactical") ?: "tactical"
        if (profile == "off") return
        when (profile) {
            "soft_low_freq", "soft" -> vibrateDirect(context, durationMs = 28L, amplitude = 130)
            "heavy" -> vibrate(context, durationMs = 55L, amplitude = 255)
            else -> vibrate(context, durationMs = 45L, amplitude = 220)
        }
    }

    // ==========================================
    // 2. SCRUB DOMAIN (KEY_GESTURE_SCRUB_HAPTICS)
    // ==========================================

    fun scrubTick(context: Context) {
        val prefs = context.defaultPrefs()
        val scrubPref = prefs.getString(LightspeedPreferences.KEY_GESTURE_SCRUB_HAPTICS, "") ?: ""
        val effective = if (scrubPref.isNotEmpty()) scrubPref else (prefs.getString(LightspeedPreferences.KEY_HAPTIC_PROFILE, "tactical") ?: "tactical")

        if (effective == "off" || effective == "jumps_only") return
        if (shouldThrottle(context)) return
        pulseTick(context, effective)
    }

    fun scrubJump(context: Context) {
        val prefs = context.defaultPrefs()
        val scrubPref = prefs.getString(LightspeedPreferences.KEY_GESTURE_SCRUB_HAPTICS, "") ?: ""
        val effective = if (scrubPref.isNotEmpty()) scrubPref else (prefs.getString(LightspeedPreferences.KEY_HAPTIC_PROFILE, "tactical") ?: "tactical")
        if (effective == "off") return
        pulseClick(context, effective)
    }

    // ==========================================
    // 3. DEFLECTOR DOMAIN (KEY_DEFLECTOR_HAPTICS)
    // ==========================================

    fun deflectorTick(context: Context) {
        val prefs = context.defaultPrefs()
        val deflectorPref = prefs.getString(LightspeedPreferences.KEY_DEFLECTOR_HAPTICS, "") ?: ""
        val effective = if (deflectorPref.isNotEmpty()) deflectorPref else (prefs.getString(LightspeedPreferences.KEY_HAPTIC_PROFILE, "tactical") ?: "tactical")

        if (effective == "off" || effective == "jumps_only") return
        if (shouldThrottle(context)) return
        pulseTick(context, effective)
    }

    fun deflectorClick(context: Context) {
        val prefs = context.defaultPrefs()
        val deflectorPref = prefs.getString(LightspeedPreferences.KEY_DEFLECTOR_HAPTICS, "") ?: ""
        val effective = if (deflectorPref.isNotEmpty()) deflectorPref else (prefs.getString(LightspeedPreferences.KEY_HAPTIC_PROFILE, "tactical") ?: "tactical")
        if (effective == "off") return
        pulseClick(context, effective)
    }

    // ==========================================
    // 4. CENTRAL COMMAND DOMAIN (KEY_CENTRAL_COMMAND_HAPTICS)
    // ==========================================

    fun centralCommandTick(context: Context) {
        val prefs = context.defaultPrefs()
        val ccPref = prefs.getString(LightspeedPreferences.KEY_CENTRAL_COMMAND_HAPTICS, "") ?: ""
        val effective = if (ccPref.isNotEmpty()) ccPref else (prefs.getString(LightspeedPreferences.KEY_HAPTIC_PROFILE, "tactical") ?: "tactical")

        if (effective == "off" || effective == "jumps_only") return
        if (shouldThrottle(context)) return
        pulseTick(context, effective)
    }

    fun centralCommandClick(context: Context) {
        val prefs = context.defaultPrefs()
        val ccPref = prefs.getString(LightspeedPreferences.KEY_CENTRAL_COMMAND_HAPTICS, "") ?: ""
        val effective = if (ccPref.isNotEmpty()) ccPref else (prefs.getString(LightspeedPreferences.KEY_HAPTIC_PROFILE, "tactical") ?: "tactical")
        if (effective == "off") return
        pulseClick(context, effective)
    }

    // ==========================================
    // 5. COCKPIT DOMAIN (KEY_GEAR_HAPTIC_STRENGTH / KEY_COCKPIT_HAPTICS)
    // ==========================================

    fun cockpitTick(context: Context) {
        val prefs = context.defaultPrefs()
        val cockpitPref = prefs.getString(LightspeedPreferences.KEY_COCKPIT_HAPTICS, "")?.takeIf { it.isNotEmpty() }
            ?: prefs.getString(LightspeedPreferences.KEY_GEAR_HAPTIC_STRENGTH, "")?.takeIf { it.isNotEmpty() }
            ?: (prefs.getString(LightspeedPreferences.KEY_HAPTIC_PROFILE, "tactical") ?: "tactical")

        if (cockpitPref == "off" || cockpitPref == "jumps_only") return
        if (shouldThrottle(context)) return
        pulseTick(context, cockpitPref)
    }

    fun cockpitClick(context: Context) {
        val prefs = context.defaultPrefs()
        val cockpitPref = prefs.getString(LightspeedPreferences.KEY_COCKPIT_HAPTICS, "")?.takeIf { it.isNotEmpty() }
            ?: prefs.getString(LightspeedPreferences.KEY_GEAR_HAPTIC_STRENGTH, "")?.takeIf { it.isNotEmpty() }
            ?: (prefs.getString(LightspeedPreferences.KEY_HAPTIC_PROFILE, "tactical") ?: "tactical")
        if (cockpitPref == "off") return
        pulseClick(context, cockpitPref)
    }

    fun cockpitGearJump(context: Context) {
        val prefs = context.defaultPrefs()
        val jumpPref = prefs.getString(LightspeedPreferences.KEY_COCKPIT_GEAR_JUMP_HAPTICS, "")?.takeIf { it.isNotEmpty() }
            ?: prefs.getString(LightspeedPreferences.KEY_COCKPIT_HAPTICS, "")?.takeIf { it.isNotEmpty() }
            ?: (prefs.getString(LightspeedPreferences.KEY_HAPTIC_PROFILE, "tactical") ?: "tactical")
        if (jumpPref == "off") return
        pulseClick(context, jumpPref)
    }

    // ==========================================
    // 6. CATEGORY CRUISE DOMAIN
    // ==========================================

    fun cruiseCategoryTick(context: Context) {
        val prefs = context.defaultPrefs()
        val pref = prefs.getString(LightspeedPreferences.KEY_CRUISE_CATEGORY_HAPTICS, "")?.takeIf { it.isNotEmpty() }
            ?: (prefs.getString(LightspeedPreferences.KEY_CRUISE_HAPTICS, "full") ?: "full")
        val normalized = when (pref) {
            "soft" -> "soft_low_freq"
            "full" -> "tactical"
            else -> pref
        }
        if (normalized == "off" || normalized == "jumps_only") return
        if (shouldThrottle(context)) return
        pulseTick(context, normalized)
    }

    fun cruiseLayerJump(context: Context) {
        val prefs = context.defaultPrefs()
        val pref = prefs.getString(LightspeedPreferences.KEY_CRUISE_LAYER_JUMP_HAPTICS, "")?.takeIf { it.isNotEmpty() }
            ?: (prefs.getString(LightspeedPreferences.KEY_CRUISE_HAPTICS, "full") ?: "full")
        val normalized = when (pref) {
            "soft" -> "soft_low_freq"
            "full" -> "tactical"
            else -> pref
        }
        if (normalized == "off") return
        pulseClick(context, normalized)
    }

    fun cruiseAppFocusTick(context: Context) {
        val prefs = context.defaultPrefs()
        val pref = prefs.getString(LightspeedPreferences.KEY_CRUISE_APP_FOCUS_HAPTICS, "")?.takeIf { it.isNotEmpty() }
            ?: (prefs.getString(LightspeedPreferences.KEY_CRUISE_HAPTICS, "full") ?: "full")
        val normalized = when (pref) {
            "soft" -> "soft_low_freq"
            "full" -> "tactical"
            else -> pref
        }
        if (normalized == "off" || normalized == "jumps_only") return
        if (shouldThrottle(context)) return
        pulseTick(context, normalized)
    }

    fun cruiseTick(context: Context) {
        val prefs = context.defaultPrefs()
        val cruisePref = prefs.getString(LightspeedPreferences.KEY_CRUISE_HAPTICS, "full") ?: "full"
        val normalized = when (cruisePref) {
            "soft" -> "soft_low_freq"
            "full" -> "tactical"
            else -> cruisePref
        }

        if (normalized == "off" || normalized == "jumps_only") return
        if (shouldThrottle(context)) return
        pulseTick(context, normalized)
    }

    fun cruiseJump(context: Context) {
        val prefs = context.defaultPrefs()
        val cruisePref = prefs.getString(LightspeedPreferences.KEY_CRUISE_HAPTICS, "full") ?: "full"
        val normalized = when (cruisePref) {
            "soft" -> "soft_low_freq"
            "full" -> "tactical"
            else -> cruisePref
        }
        if (normalized == "off") return
        pulseClick(context, normalized)
    }

    // ==========================================
    // ALERTS & SIGNALS
    // ==========================================

    fun alert(context: Context) {
        vibrate(context, durationMs = 60L, amplitude = 255)
    }

    fun triggerWarning(context: Context) {
        vibrate(context, durationMs = 50L, amplitude = 255)
    }

    fun triggerConfirmation(context: Context) {
        vibrate(context, durationMs = 45L, amplitude = 220)
    }
}
