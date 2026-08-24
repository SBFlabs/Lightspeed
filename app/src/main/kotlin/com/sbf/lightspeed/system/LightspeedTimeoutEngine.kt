package com.sbf.lightspeed.system

import android.content.Context
import android.provider.Settings
import android.util.Log

object LightspeedTimeoutEngine {
    private const val TAG = "LightspeedTimeout"

    val TIMEOUT_STEPS = listOf(
        15_000 to "15s",
        30_000 to "30s",
        60_000 to "1m",
        120_000 to "2m",
        300_000 to "5m",
        600_000 to "10m",
        1_800_000 to "30m"
    )

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

    fun cycleNext(context: Context): String {
        val currentIdx = getCurrentTimeoutIndex(context)
        val nextIdx = (currentIdx + 1) % TIMEOUT_STEPS.size
        val (_, label) = setStepIndex(context, nextIdx)
        return label
    }
}
