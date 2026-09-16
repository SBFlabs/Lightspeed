package com.sbf.lightspeed.system

import android.content.Context
import android.os.SystemClock
import android.util.Log
import android.view.KeyEvent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Dedicated engine for hardware Power button gestures (Single, Double, Hold, Tap-then-Hold).
 * Supports Shizuku getevent listening, kernel wake lock management, and OEM assistant disambiguation.
 */
object LightspeedPowerKeyEngine {

    const val LONG_PRESS_TIMEOUT_MS = 400L
    const val POWER_SEQUENCE_TIMEOUT_MS = 450L

    private var powerScope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    // Live Key Press & Trigger States (Power Button Engine)
    private var isPowerPressed = false
    private var isPowerHoldFired = false
    private var isPowerPressHoldFired = false
    private var lastPowerReleaseTime = 0L
    var wasScreenInteractiveAtDown = true

    private var powerHoldJob: Job? = null
    private var powerPressHoldJob: Job? = null
    private var powerSinglePressJob: Job? = null
    private var powerWakeLock: android.os.PowerManager.WakeLock? = null

    private var shizukuMonitorJob: Job? = null
    private var shizukuProcess: Process? = null

    private fun acquirePowerScreenWakeLock(context: Context, durationMs: Long = 3000L) {
        try {
            val pm = context.getSystemService(Context.POWER_SERVICE) as? android.os.PowerManager
            if (powerWakeLock == null) {
                @Suppress("DEPRECATION")
                powerWakeLock = pm?.newWakeLock(
                    android.os.PowerManager.SCREEN_BRIGHT_WAKE_LOCK or
                            android.os.PowerManager.ACQUIRE_CAUSES_WAKEUP,
                    "Lightspeed:PowerFailsafeWakeLock"
                )
                powerWakeLock?.setReferenceCounted(false)
            }
            powerWakeLock?.acquire(durationMs)
        } catch (_: Exception) {}
    }

    private fun releasePowerScreenWakeLock() {
        try {
            if (powerWakeLock?.isHeld == true) {
                powerWakeLock?.release()
            }
        } catch (_: Exception) {}
    }

    fun onPowerGestureHandled() {
        powerSinglePressJob?.cancel()
        powerSinglePressJob = null
        powerPressHoldJob?.cancel()
        powerPressHoldJob = null
        powerHoldJob?.cancel()
        powerHoldJob = null
        lastPowerReleaseTime = 0L
        releasePowerScreenWakeLock()
    }

    @Suppress("DEPRECATION")
    fun isAssistantActiveOrPending(context: Context): Boolean {
        try {
            val km = context.getSystemService(Context.KEYGUARD_SERVICE) as? android.app.KeyguardManager
            if (km?.isKeyguardLocked == true) {
                val am = context.getSystemService(Context.ACTIVITY_SERVICE) as? android.app.ActivityManager
                val topTasks = am?.getRunningTasks(1)
                val topPkg = topTasks?.firstOrNull()?.topActivity?.packageName?.lowercase()
                if (topPkg != null && (topPkg.contains("assistant") || topPkg.contains("googlequicksearchbox") || topPkg.contains("gemini"))) {
                    return true
                }
            }
        } catch (_: Exception) {}
        return false
    }

    fun isSinglePressUnlocked(context: Context): Boolean {
        val prefs = context.defaultPrefs()
        return prefs.getBoolean(LightspeedPreferences.KEY_POWER_SINGLE_PRESS_UNLOCKED, false)
    }

    fun isPowerEnabled(context: Context): Boolean {
        val prefs = context.defaultPrefs()
        return prefs.getBoolean(LightspeedPreferences.KEY_POWER_GESTURES_ENABLED, true)
    }

    fun getBoundPowerAction(context: Context, slot: PowerTriggerSlot): String? {
        val prefs = context.defaultPrefs()
        if (slot == PowerTriggerSlot.POWER_SINGLE_PRESS && !isSinglePressUnlocked(context)) {
            return null // Locked to native system sleep by default
        }
        val raw = prefs.getString(slot.prefKey, null)?.trim()
        if (!raw.isNullOrEmpty() && raw != "none") return raw
        if (slot == PowerTriggerSlot.POWER_HOLD) {
            val legacy = prefs.getString(LightspeedPreferences.KEY_POWER_LONG_PRESS_ACTION, null)?.trim()
            if (!legacy.isNullOrEmpty() && legacy != "none") return legacy
            return "system:tactical_flyout" // Default quick action for power hold
        }
        return null
    }

    fun handlePowerKeyEvent(context: Context, event: KeyEvent, now: Long): Boolean {
        if (!isPowerEnabled(context)) return false

        val singleAction = getBoundPowerAction(context, PowerTriggerSlot.POWER_SINGLE_PRESS)
        val doubleAction = getBoundPowerAction(context, PowerTriggerSlot.POWER_DOUBLE_PRESS)
        val holdAction = getBoundPowerAction(context, PowerTriggerSlot.POWER_HOLD)
        val pressHoldAction = getBoundPowerAction(context, PowerTriggerSlot.POWER_PRESS_THEN_HOLD)

        val hasAnyPowerAction = singleAction != null || doubleAction != null || holdAction != null || pressHoldAction != null
        if (!hasAnyPowerAction) return false

        val action = event.action

        if (action == KeyEvent.ACTION_DOWN) {
            if (event.repeatCount > 0) return true

            val pm = context.getSystemService(Context.POWER_SERVICE) as? android.os.PowerManager
            if (!isPowerPressed) {
                wasScreenInteractiveAtDown = pm?.isInteractive == true
            }
            isPowerPressed = true
            isPowerHoldFired = false
            isPowerPressHoldFired = false

            // 1. Check Press-then-Hold / Double-Press Trigger (Second press within sequence window)
            val diffPower = now - lastPowerReleaseTime
            if (lastPowerReleaseTime > 0L && diffPower <= POWER_SEQUENCE_TIMEOUT_MS) {
                powerSinglePressJob?.cancel()
                powerSinglePressJob = null
                acquirePowerScreenWakeLock(context, 3500L)
                if (pressHoldAction != null) {
                    powerPressHoldJob?.cancel()
                    powerPressHoldJob = powerScope.launch {
                        delay(LONG_PRESS_TIMEOUT_MS)
                        if (isAssistantActiveOrPending(context)) {
                            resetPowerState()
                            return@launch
                        }
                        isPowerPressHoldFired = true
                        lastPowerReleaseTime = 0L
                        acquirePowerScreenWakeLock(context, 3500L)
                        LightspeedHapticEngine.heavyClick(context)
                        ActionDispatcher.dispatch(pressHoldAction, context)
                    }
                    return true
                }
                return true
            }

            // 2. Standard Power Hold Trigger (First press hold)
            if (holdAction != null) {
                powerHoldJob?.cancel()
                powerHoldJob = powerScope.launch {
                    delay(LONG_PRESS_TIMEOUT_MS)
                    if (isAssistantActiveOrPending(context)) {
                        resetPowerState()
                        return@launch
                    }
                    isPowerHoldFired = true
                    acquirePowerScreenWakeLock(context, 3500L)
                    LightspeedHapticEngine.heavyClick(context)
                    ActionDispatcher.dispatch(holdAction, context)
                }
            }

            val hasMultiTapAction = doubleAction != null || pressHoldAction != null || singleAction != null || holdAction != null
            if (hasMultiTapAction) {
                acquirePowerScreenWakeLock(context, POWER_SEQUENCE_TIMEOUT_MS + 200L)
                return true
            }
            return false
        } else if (action == KeyEvent.ACTION_UP) {
            isPowerPressed = false
            powerHoldJob?.cancel()
            powerHoldJob = null
            powerPressHoldJob?.cancel()
            powerPressHoldJob = null

            if (isPowerPressHoldFired) {
                isPowerPressHoldFired = false
                releasePowerScreenWakeLock()
                return true
            }

            if (isPowerHoldFired) {
                isPowerHoldFired = false
                releasePowerScreenWakeLock()
                return true
            }

            // 3. Double Press Trigger Check (Quick second tap release)
            val diffPower = now - lastPowerReleaseTime
            if (lastPowerReleaseTime > 0L && diffPower <= POWER_SEQUENCE_TIMEOUT_MS) {
                powerSinglePressJob?.cancel()
                powerSinglePressJob = null
                lastPowerReleaseTime = 0L
                if (doubleAction != null) {
                    acquirePowerScreenWakeLock(context, 3500L)
                    LightspeedHapticEngine.click(context)
                    ActionDispatcher.dispatch(doubleAction, context)
                    return true
                } else {
                    releasePowerScreenWakeLock()
                    return true
                }
            }

            // 4. Single Press Disambiguation Trigger (Active when multi-tap power gestures are mapped)
            lastPowerReleaseTime = now
            val shouldDisambiguate = doubleAction != null || pressHoldAction != null || singleAction != null
            if (shouldDisambiguate) {
                acquirePowerScreenWakeLock(context, POWER_SEQUENCE_TIMEOUT_MS + 200L)
                powerSinglePressJob?.cancel()
                powerSinglePressJob = powerScope.launch {
                    delay(POWER_SEQUENCE_TIMEOUT_MS)
                    if (isPowerPressed || lastPowerReleaseTime == 0L) {
                        return@launch
                    }
                    lastPowerReleaseTime = 0L
                    if (singleAction != null) {
                        acquirePowerScreenWakeLock(context, 3500L)
                        LightspeedHapticEngine.click(context)
                        ActionDispatcher.dispatch(singleAction, context)
                    } else {
                        releasePowerScreenWakeLock()
                    }
                }
                return true
            } else {
                releasePowerScreenWakeLock()
                return false
            }
        }
        return false
    }

    fun resetPowerState() {
        powerHoldJob?.cancel()
        powerHoldJob = null
        powerPressHoldJob?.cancel()
        powerPressHoldJob = null
        powerSinglePressJob?.cancel()
        powerSinglePressJob = null
        isPowerPressed = false
        isPowerHoldFired = false
        isPowerPressHoldFired = false
        wasScreenInteractiveAtDown = true
        lastPowerReleaseTime = 0L
        releasePowerScreenWakeLock()
    }

    fun startShizukuPowerMonitor(context: Context) {
        if (!isPowerEnabled(context) || !ElevatedTaskCloser.isShizukuActive) {
            stopShizukuPowerMonitor()
            return
        }
        if (shizukuMonitorJob?.isActive == true) return

        shizukuMonitorJob = powerScope.launch(Dispatchers.IO) {
            try {
                val proc = ElevatedTaskCloser.execShizuku("getevent -l") ?: return@launch
                shizukuProcess = proc
                val reader = proc.inputStream.bufferedReader()
                while (isActive) {
                    val line = reader.readLine() ?: break
                    val lower = line.lowercase()
                    if (lower.contains("key_power") || lower.contains(" 0074 ") || lower.contains("key_wakeup")) {
                        val isDown = lower.contains("down") || lower.contains(" 00000001") || lower.contains(" 1")
                        val isUp = lower.contains("up") || lower.contains(" 00000000") || lower.contains(" 0")
                        withContext(Dispatchers.Main) {
                            if (isDown) {
                                LightspeedKeyEngine.onKeyEvent(context, KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_POWER))
                            } else if (isUp) {
                                LightspeedKeyEngine.onKeyEvent(context, KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_POWER))
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                Log.d("LightspeedKeyEngine", "Shizuku power monitor stopped: ${e.message}")
            }
        }
    }

    fun stopShizukuPowerMonitor() {
        shizukuMonitorJob?.cancel()
        shizukuMonitorJob = null
        try {
            shizukuProcess?.destroy()
            shizukuProcess = null
        } catch (_: Exception) {}
    }
}
