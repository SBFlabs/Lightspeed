package com.sbf.lightspeed.system

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.SystemClock
import android.provider.Settings
import android.util.Log
import android.widget.Toast
import com.sbf.lightspeed.LightspeedStatusBarOverlay

object LightspeedTimeoutEngine {
    private const val TAG = "LightspeedTimeout"
    private var lastPermissionPromptTime = 0L

    val TIMEOUT_STEPS = listOf(
        15_000 to "15s",
        30_000 to "30s",
        60_000 to "1m",
        120_000 to "2m",
        300_000 to "5m",
        600_000 to "10m",
        1_800_000 to "30m"
    )

    fun hasWriteSettingsPermission(context: Context): Boolean {
        return Settings.System.canWrite(context)
    }

    fun requestWriteSettingsPermission(context: Context) {
        val now = SystemClock.uptimeMillis()
        if (now - lastPermissionPromptTime < 3000L) return
        lastPermissionPromptTime = now
        try {
            val intent = Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS).apply {
                data = Uri.parse("package:${context.packageName}")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            Toast.makeText(
                context,
                "Lightspeed requires 'Modify system settings' permission to change screen timeout",
                Toast.LENGTH_LONG
            ).show()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to launch ACTION_MANAGE_WRITE_SETTINGS", e)
        }
    }

    fun getCurrentTimeoutMs(context: Context): Int {
        return try {
            Settings.System.getInt(context.contentResolver, Settings.System.SCREEN_OFF_TIMEOUT, 60_000)
        } catch (_: Exception) {
            60_000
        }
    }

    fun getCurrentTimeoutIndex(context: Context): Int {
        val current = getCurrentTimeoutMs(context)
        val idx = TIMEOUT_STEPS.indexOfFirst { it.first >= current }
        return if (idx >= 0) idx else TIMEOUT_STEPS.lastIndex
    }

    fun formatTimeout(timeoutMs: Int): String {
        val match = TIMEOUT_STEPS.find { it.first == timeoutMs }
        if (match != null) return match.second
        return when {
            timeoutMs < 60_000 -> "${timeoutMs / 1000}s"
            else -> "${timeoutMs / 60_000}m"
        }
    }

    fun setStepIndex(context: Context, index: Int): Pair<Int, String> {
        val safeIndex = index.coerceIn(0, TIMEOUT_STEPS.lastIndex)
        val (timeoutMs, label) = TIMEOUT_STEPS[safeIndex]
        return try {
            if (Settings.System.canWrite(context)) {
                Settings.System.putInt(context.contentResolver, Settings.System.SCREEN_OFF_TIMEOUT, timeoutMs)
                Log.i(TAG, "Screen timeout set to $label ($timeoutMs ms)")
            } else {
                Log.w(TAG, "WRITE_SETTINGS permission not granted")
                requestWriteSettingsPermission(context)
            }
            Pair(safeIndex, label)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to set screen timeout", e)
            Pair(safeIndex, label)
        }
    }

    fun getCurrentFormatted(context: Context): String {
        return formatTimeout(getCurrentTimeoutMs(context))
    }

    fun showTimeoutHud(context: Context, stepIndex: Int, label: String) {
        val shown = LightspeedStatusBarOverlay.showActionHud(
            title = "SHIP GOES DARK IN",
            value = label,
            stepIndex = stepIndex,
            totalSteps = TIMEOUT_STEPS.size
        )
        if (!shown) {
            Toast.makeText(context, "Ship goes dark in: $label", Toast.LENGTH_SHORT).show()
        }
    }

    fun cycleNext(context: Context): String {
        if (!hasWriteSettingsPermission(context)) {
            requestWriteSettingsPermission(context)
            return getCurrentFormatted(context)
        }
        val currentIdx = getCurrentTimeoutIndex(context)
        val nextIdx = (currentIdx + 1) % TIMEOUT_STEPS.size
        val (_, label) = setStepIndex(context, nextIdx)
        showTimeoutHud(context, nextIdx, label)
        return label
    }
}
