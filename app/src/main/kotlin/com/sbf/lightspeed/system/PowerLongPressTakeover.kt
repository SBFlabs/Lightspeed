package com.sbf.lightspeed.system

import android.content.Context
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Dormant "power hold takeover" manager.
 * Overrides global setting power_button_long_press to 0 when enabled and active,
 * and restores original value when disabled or disengaged.
 */
object PowerLongPressTakeover {
    private const val TAG = "PowerLongPressTakeover"
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    fun sync(context: Context) {
        scope.launch { syncBlocking(context.applicationContext) }
    }

    fun release(context: Context) {
        scope.launch { restoreIfActive(context.applicationContext) }
    }

    @Synchronized
    private fun syncBlocking(ctx: Context) {
        val prefs = ctx.defaultPrefs()
        val desired = prefs.getBoolean(LightspeedPreferences.KEY_POWER_HOLD_TAKEOVER, false) &&
                LightspeedPowerKeyEngine.isPowerEnabled(ctx)
        val active = prefs.getBoolean(LightspeedPreferences.KEY_POWER_TAKEOVER_ACTIVE, false)
        val hasSavedValue = prefs.contains(LightspeedPreferences.KEY_POWER_TAKEOVER_SAVED_VALUE)

        if (desired) {
            if (!ElevatedTaskCloser.isShizukuActive) return

            if (!active) {
                val current = readCurrent()
                if (current == null) {
                    logSwallowed(TAG, "syncBlocking:readCurrent", IllegalStateException("readCurrent returned null"))
                    return
                }
                prefs.edit()
                    .putString(LightspeedPreferences.KEY_POWER_TAKEOVER_SAVED_VALUE, current)
                    .putBoolean(LightspeedPreferences.KEY_POWER_TAKEOVER_ACTIVE, true)
                    .commit()

                val exitCode = ElevatedTaskCloser.execShizuku("settings put global power_button_long_press 0").waitForOrKill()
                if (exitCode != 0) {
                    restoreIfActive(ctx)
                    return
                }

                val readBack = readCurrent()
                if (readBack != "0") {
                    restoreIfActive(ctx)
                    return
                }
            } else {
                val current = readCurrent()
                if (current != "0") {
                    ElevatedTaskCloser.execShizuku("settings put global power_button_long_press 0").waitForOrKill()
                }
            }
        } else if (active || hasSavedValue) {
            restoreIfActive(ctx)
        }
    }

    @Synchronized
    private fun restoreIfActive(ctx: Context) {
        val prefs = ctx.defaultPrefs()
        val active = prefs.getBoolean(LightspeedPreferences.KEY_POWER_TAKEOVER_ACTIVE, false)
        val hasSavedValue = prefs.contains(LightspeedPreferences.KEY_POWER_TAKEOVER_SAVED_VALUE)
        if (!active && !hasSavedValue) return

        if (!ElevatedTaskCloser.isShizukuActive) {
            Log.w(TAG, "Restore attempted for power_button_long_press but Shizuku is unavailable. Retaining saved value for retry.")
            return
        }

        val saved = prefs.getString(LightspeedPreferences.KEY_POWER_TAKEOVER_SAVED_VALUE, null)
        Log.i(TAG, "Attempting restore for power_button_long_press (saved value: $saved)")

        val command = if (saved != null && saved != "null" && saved.matches(Regex("^[0-9]+$"))) {
            "settings put global power_button_long_press $saved"
        } else {
            "settings delete global power_button_long_press"
        }

        val exitCode = ElevatedTaskCloser.execShizuku(command).waitForOrKill()
        if (exitCode == 0) {
            Log.i(TAG, "Successfully restored power_button_long_press setting (command: $command)")
            prefs.edit()
                .putBoolean(LightspeedPreferences.KEY_POWER_TAKEOVER_ACTIVE, false)
                .remove(LightspeedPreferences.KEY_POWER_TAKEOVER_SAVED_VALUE)
                .commit()
        } else {
            Log.w(TAG, "Failed to restore power_button_long_press setting via Shizuku (exit code: $exitCode). Retaining saved value for retry.")
        }
    }

    private fun readCurrent(): String? {
        val raw = ElevatedTaskCloser.execShizuku("settings get global power_button_long_press 2>&1")
            .readTextOrKill()
            ?.trim()
        return if (raw != null && (raw.matches(Regex("^[0-9]+$")) || raw == "null")) {
            raw
        } else {
            null
        }
    }
}
