package com.sbf.lightspeed.system

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.util.Log
import android.widget.Toast
import androidx.core.content.ContextCompat

/**
 * Core Cooling Battery Saver Tuner & Charge Limit Sentinel.
 *
 * Configures Android battery_saver_constants (haptics, launch boost, animations, standby, night mode)
 * via WRITE_SECURE_SETTINGS / Shizuku, and monitors charge levels to fire
 * ACTION_CHARGE_LIMIT_REACHED and notify the user when the target charge limit is reached.
 */
object LightspeedBatterySentinel {
    private const val TAG = "LightspeedBatterySentinel"
    const val ACTION_CHARGE_LIMIT_REACHED = "com.sbf.lightspeed.ACTION_CHARGE_LIMIT_REACHED"

    const val PROFILE_LIGHT = "light"
    const val PROFILE_BALANCED = "balanced"
    const val PROFILE_CRYO = "cryo"

    private var isInitialized = false
    private var hasAlertedThisSession = false

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent?) {
            when (intent?.action) {
                Intent.ACTION_POWER_DISCONNECTED -> {
                    hasAlertedThisSession = false
                }
                Intent.ACTION_BATTERY_CHANGED -> {
                    val status = intent.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
                    val isCharging = status == BatteryManager.BATTERY_STATUS_CHARGING || status == BatteryManager.BATTERY_STATUS_FULL
                    val level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
                    val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, 100).coerceAtLeast(1)
                    val pct = (level * 100) / scale
                    checkChargeLevel(context, pct, isCharging)
                }
            }
        }
    }

    fun init(context: Context) {
        if (isInitialized) return
        val appCtx = context.applicationContext ?: context
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_BATTERY_CHANGED)
            addAction(Intent.ACTION_POWER_DISCONNECTED)
            addAction(Intent.ACTION_POWER_CONNECTED)
        }
        try {
            ContextCompat.registerReceiver(appCtx, receiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED)
            isInitialized = true
        } catch (e: Exception) {
            logSwallowed(TAG, "init", e)
        }
    }

    fun checkChargeLevel(context: Context, levelPct: Int, isCharging: Boolean) {
        if (!isCharging) {
            hasAlertedThisSession = false
            return
        }

        val prefs = context.defaultPrefs()
        val isSentinelEnabled = prefs.getBoolean(LightspeedPreferences.KEY_CHARGE_LIMIT_SENTINEL_ENABLED, false)
        if (!isSentinelEnabled) return

        val targetPct = prefs.getInt(LightspeedPreferences.KEY_CHARGE_LIMIT_PERCENTAGE, 80)
        if (levelPct >= targetPct && !hasAlertedThisSession) {
            hasAlertedThisSession = true
            Log.i(TAG, "Charge Limit reached: $levelPct% >= target $targetPct%")

            // 1. Fire system broadcast
            val broadcastIntent = Intent(ACTION_CHARGE_LIMIT_REACHED).apply {
                setPackage(context.packageName)
                putExtra("level", levelPct)
                putExtra("target", targetPct)
            }
            context.sendBroadcast(broadcastIntent)

            // 2. Trigger tactical haptics
            try {
                LightspeedHapticEngine.heavyClick(context)
            } catch (e: Exception) {
                logSwallowed(TAG, "haptic", e)
            }

            // 3. User notification / Toast
            Handler(Looper.getMainLooper()).post {
                Toast.makeText(
                    context,
                    "⚡ Charge Limit Sentinel: Battery reached $levelPct%! Unplug to protect battery health.",
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }

    /**
     * Preserves Lightspeed haptics in Battery Saver mode by setting vibration_disabled=false
     * in Settings.Global.battery_saver_constants.
     */
    fun setKeepHapticsAlive(context: Context, keepAlive: Boolean) {
        val current = getBatterySaverConstants(context)
        val map = parseConstants(current)
        map["vibration_disabled"] = (!keepAlive).toString()
        val newConstants = serializeConstants(map)
        putBatterySaverConstants(context, newConstants)
    }

    /**
     * Configures Android battery_saver_constants profile (Light, Balanced, Extreme Cryo).
     */
    fun applyProfile(context: Context, profile: String, keepHaptics: Boolean) {
        val current = getBatterySaverConstants(context)
        val map = parseConstants(current)

        map["vibration_disabled"] = (!keepHaptics).toString()
        when (profile) {
            PROFILE_LIGHT -> {
                map["animation_disabled"] = "false"
                map["launch_boost_disabled"] = "false"
                map["force_all_apps_standby"] = "false"
                map["night_mode"] = "0"
            }
            PROFILE_BALANCED -> {
                map["animation_disabled"] = "false"
                map["launch_boost_disabled"] = "true"
                map["force_all_apps_standby"] = "false"
                map["night_mode"] = "1"
            }
            PROFILE_CRYO -> {
                map["animation_disabled"] = "true"
                map["launch_boost_disabled"] = "true"
                map["force_all_apps_standby"] = "true"
                map["night_mode"] = "1"
            }
        }

        val newConstants = serializeConstants(map)
        putBatterySaverConstants(context, newConstants)
    }

    private fun getBatterySaverConstants(context: Context): String {
        return try {
            Settings.Global.getString(context.contentResolver, "battery_saver_constants") ?: ""
        } catch (_: Exception) {
            ""
        }
    }

    private fun putBatterySaverConstants(context: Context, value: String): Boolean {
        // Try direct Settings.Global first
        try {
            val success = Settings.Global.putString(context.contentResolver, "battery_saver_constants", value)
            if (success) return true
        } catch (_: Exception) {}

        // Fallback to Shizuku shell
        if (ElevatedTaskCloser.isShizukuActive) {
            try {
                val proc = ShizukuExecHelper.execShizuku("settings put global battery_saver_constants \"$value\"")
                return proc?.waitFor() == 0
            } catch (e: Exception) {
                logSwallowed(TAG, "putBatterySaverConstants.shizuku", e)
            }
        }
        return false
    }

    private fun parseConstants(constants: String): MutableMap<String, String> {
        val map = mutableMapOf<String, String>()
        if (constants.isBlank()) return map
        constants.split(",").forEach { pair ->
            val parts = pair.split("=")
            if (parts.size == 2) {
                map[parts[0].trim()] = parts[1].trim()
            }
        }
        return map
    }

    private fun serializeConstants(map: Map<String, String>): String {
        return map.entries.joinToString(",") { "${it.key}=${it.value}" }
    }
}
