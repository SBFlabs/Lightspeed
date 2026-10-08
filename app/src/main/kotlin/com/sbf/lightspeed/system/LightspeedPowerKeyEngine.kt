package com.sbf.lightspeed.system

import android.content.Context
import android.os.PowerManager
import android.os.SystemClock
import android.util.Log
import android.view.KeyEvent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

enum class DaemonMode {
    FORWARD,
    SWALLOW
}

/**
 * Dedicated engine for hardware Power button gestures (Single, Double, Hold, Tap-then-Hold).
 * Supports Shizuku getevent listening, kernel wake lock management, and OEM assistant disambiguation.
 */
object LightspeedPowerKeyEngine {

    const val LONG_PRESS_TIMEOUT_MS = 400L
    const val POWER_SEQUENCE_TIMEOUT_MS = 450L

    @Volatile
    var currentDaemonMode: DaemonMode = DaemonMode.FORWARD

    @Volatile
    var wasPressForwarded: Boolean = false

    @Volatile
    private var powerScope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    // Live Key Press & Trigger States (Power Button Engine)
    private var isPowerPressed = false
    private var isPowerHoldFired = false
    private var isPowerPressHoldFired = false
    private var lastPowerReleaseTime = 0L
    var wasScreenInteractiveAtDown = true
    var onPassthroughPress: (() -> Unit)? = null
    var onPassthroughDoubleTap: (() -> Unit)? = null
    var onPassthroughHoldStart: (() -> Unit)? = null
    var onPassthroughHoldEnd: (() -> Unit)? = null
    private var powerPressDownTime = 0L

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
        } catch (e: Exception) { logSwallowed("LightspeedPowerKeyEngine", "acquirePowerScreenWakeLock", e) }
    }

    private fun releasePowerScreenWakeLock() {
        try {
            if (powerWakeLock?.isHeld == true) {
                powerWakeLock?.release()
            }
        } catch (e: Exception) { logSwallowed("LightspeedPowerKeyEngine", "releasePowerScreenWakeLock", e) }
    }

    private fun isLockAction(action: String?): Boolean = action == "system:lock_screen"

    fun sendDaemonMode(mode: DaemonMode) {
        currentDaemonMode = mode
        val proc = shizukuProcess ?: return
        val cmd = if (mode == DaemonMode.SWALLOW) "MODE swallow" else "MODE forward"
        PowerGrabHelper.sendCommand(proc, cmd)
    }

    fun onScreenOff(context: Context) {
        resetPowerState()
        sendDaemonMode(DaemonMode.FORWARD)
    }

    fun onScreenOn(context: Context) {
        resetPowerState()
        sendDaemonMode(DaemonMode.SWALLOW)
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

    // Detects an active assistant window via the accessibility root only.
    fun isAssistantActiveOrPending(context: Context): Boolean {
        try {
            // Check active accessibility window root first (zero-deprecation, 100% reliable)
            val activePkg = com.sbf.lightspeed.LightspeedAccessibilityService.instance
                ?.rootInActiveWindow?.packageName?.toString()?.lowercase()
            if (activePkg != null && (activePkg.contains("assistant") || activePkg.contains("googlequicksearchbox") || activePkg.contains("gemini"))) {
                return true
            }
        } catch (e: Exception) { logSwallowed("LightspeedPowerKeyEngine", "isAssistantActiveOrPending", e) }
        return false
    }

    fun isSinglePressUnlocked(context: Context): Boolean {
        val prefs = context.defaultPrefs()
        return prefs.getBoolean(LightspeedPreferences.KEY_POWER_SINGLE_PRESS_UNLOCKED, false)
    }

    fun isPowerEnabled(context: Context): Boolean {
        val prefs = context.defaultPrefs()
        return prefs.getBoolean(LightspeedPreferences.KEY_POWER_GESTURES_ENABLED, false)
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

    fun getDoublePressWindowMs(context: Context): Long {
        val prefs = context.defaultPrefs()
        return prefs.getLong(LightspeedPreferences.KEY_POWER_DOUBLE_PRESS_WINDOW, LightspeedPreferences.DEFAULT_POWER_DOUBLE_PRESS_WINDOW_MS)
    }

    fun handlePowerKeyEvent(context: Context, event: KeyEvent, now: Long): Boolean {
        if (!isPowerEnabled(context)) return false

        val doublePressWindowMs = getDoublePressWindowMs(context)
        val singleAction = getBoundPowerAction(context, PowerTriggerSlot.POWER_SINGLE_PRESS)
        val doubleAction = getBoundPowerAction(context, PowerTriggerSlot.POWER_DOUBLE_PRESS)
        val holdAction = getBoundPowerAction(context, PowerTriggerSlot.POWER_HOLD)
        val pressHoldAction = getBoundPowerAction(context, PowerTriggerSlot.POWER_PRESS_THEN_HOLD)

        val hasAnyPowerAction = singleAction != null || doubleAction != null || holdAction != null || pressHoldAction != null
        if (!hasAnyPowerAction) return false

        val action = event.action

        if (action == KeyEvent.ACTION_DOWN) {
            if (!isPowerPressed) {
                wasPressForwarded = (currentDaemonMode == DaemonMode.FORWARD)
                powerPressDownTime = now
            }
            isPowerPressed = true

            if (wasPressForwarded) {
                return false
            }
            if (event.repeatCount > 0) return true

            isPowerHoldFired = false
            isPowerPressHoldFired = false

            // 1. Check Press-then-Hold / Double-Press Trigger (Second press within sequence window)
            val diffPower = now - lastPowerReleaseTime
            if (lastPowerReleaseTime > 0L && diffPower <= doublePressWindowMs) {
                powerSinglePressJob?.cancel()
                powerSinglePressJob = null
                acquirePowerScreenWakeLock(context, 3500L)
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
                    if (pressHoldAction != null) {
                        LightspeedHapticEngine.heavyClick(context)
                        ActionDispatcher.dispatch(pressHoldAction, context)
                        if (isLockAction(pressHoldAction)) {
                            onScreenOff(context)
                        }
                    } else {
                        onPassthroughHoldStart?.invoke()
                    }
                }
                return true
            }

            // 2. Standard Power Hold Trigger (First press hold)
            powerHoldJob?.cancel()
            powerHoldJob = powerScope.launch {
                delay(LONG_PRESS_TIMEOUT_MS)
                if (isAssistantActiveOrPending(context)) {
                    resetPowerState()
                    return@launch
                }
                isPowerHoldFired = true
                acquirePowerScreenWakeLock(context, 3500L)
                if (holdAction != null) {
                    LightspeedHapticEngine.heavyClick(context)
                    ActionDispatcher.dispatch(holdAction, context)
                    if (isLockAction(holdAction)) {
                        onScreenOff(context)
                    }
                } else {
                    onPassthroughHoldStart?.invoke()
                }
            }

            val hasMultiTapAction = doubleAction != null || pressHoldAction != null || singleAction != null || holdAction != null
            if (hasMultiTapAction) {
                acquirePowerScreenWakeLock(context, doublePressWindowMs + 200L)
                return true
            }
            return false
        } else if (action == KeyEvent.ACTION_UP) {
            if (wasPressForwarded) {
                isPowerPressed = false
                return false
            }

            isPowerPressed = false
            powerHoldJob?.cancel()
            powerHoldJob = null
            powerPressHoldJob?.cancel()
            powerPressHoldJob = null

            if (isPowerPressHoldFired) {
                isPowerPressHoldFired = false
                releasePowerScreenWakeLock()
                if (pressHoldAction == null) {
                    powerScope.launch {
                        delay(LONG_PRESS_TIMEOUT_MS)
                        onPassthroughHoldEnd?.invoke()
                    }
                }
                return true
            }

            if (isPowerHoldFired) {
                isPowerHoldFired = false
                releasePowerScreenWakeLock()
                if (holdAction == null) {
                    powerScope.launch {
                        delay(LONG_PRESS_TIMEOUT_MS)
                        onPassthroughHoldEnd?.invoke()
                    }
                }
                return true
            }

            // 3. Double Press Trigger Check (Quick second tap release)
            val diffPower = now - lastPowerReleaseTime
            if (lastPowerReleaseTime > 0L && diffPower <= doublePressWindowMs) {
                powerSinglePressJob?.cancel()
                powerSinglePressJob = null
                lastPowerReleaseTime = 0L
                if (doubleAction != null) {
                    acquirePowerScreenWakeLock(context, 3500L)
                    LightspeedHapticEngine.click(context)
                    ActionDispatcher.dispatch(doubleAction, context)
                    if (isLockAction(doubleAction)) {
                        onScreenOff(context)
                    }
                    return true
                } else {
                    onPassthroughDoubleTap?.invoke()
                    releasePowerScreenWakeLock()
                    return true
                }
            }

            // 4. Single Press Disambiguation Trigger (Active when multi-tap power gestures are mapped)
            lastPowerReleaseTime = now
            val shouldDisambiguate = doubleAction != null || pressHoldAction != null || singleAction != null
            if (shouldDisambiguate) {
                acquirePowerScreenWakeLock(context, doublePressWindowMs + 200L)
                powerSinglePressJob?.cancel()
                powerSinglePressJob = powerScope.launch {
                    delay(doublePressWindowMs)
                    if (isPowerPressed || lastPowerReleaseTime == 0L) {
                        return@launch
                    }
                    lastPowerReleaseTime = 0L
                    if (singleAction != null) {
                        acquirePowerScreenWakeLock(context, 3500L)
                        LightspeedHapticEngine.click(context)
                        ActionDispatcher.dispatch(singleAction, context)
                        if (isLockAction(singleAction)) {
                            onScreenOff(context)
                        }
                    } else {
                        onPassthroughPress?.invoke()
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
        wasPressForwarded = false
        lastPowerReleaseTime = 0L
        releasePowerScreenWakeLock()
    }

    fun startShizukuPowerMonitor(context: Context) {
        if (!isPowerEnabled(context) || !ElevatedTaskCloser.isShizukuActive) {
            stopShizukuPowerMonitor()
            resetPowerState()
            return
        }
        if (shizukuMonitorJob?.isActive == true) return

        val prefs = context.defaultPrefs()
        val isGrabHelperEnabled = prefs.getBoolean(LightspeedPreferences.KEY_POWER_GRAB_HELPER, false)

        if (isGrabHelperEnabled) {
            shizukuMonitorJob = powerScope.launch(Dispatchers.IO) {
                var backoffMs = 1000L
                try {
                    while (isActive && isPowerEnabled(context) && ElevatedTaskCloser.isShizukuActive) {
                        try {
                            shizukuProcess?.destroy()
                        } catch (e: Exception) {
                            logSwallowed("LightspeedPowerKeyEngine", "startShizukuPowerMonitor:destroy", e)
                        }
                        shizukuProcess = null

                        val proc = PowerGrabHelper.start(context)
                        if (proc == null) {
                            if (!isActive || !isPowerEnabled(context) || !ElevatedTaskCloser.isShizukuActive) break
                            withContext(Dispatchers.Main) { resetPowerState() }
                            delay(backoffMs)
                            backoffMs = (backoffMs * 2).coerceAtMost(10000L)
                            continue
                        }

                        shizukuProcess = proc
                        val pm = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
                        val initialInteractive = pm?.isInteractive == true
                        val initialMode = if (initialInteractive) DaemonMode.SWALLOW else DaemonMode.FORWARD
                        withContext(Dispatchers.Main) {
                            sendDaemonMode(initialMode)
                        }

                        onPassthroughPress = {
                            if (!wasPressForwarded) {
                                PowerGrabHelper.sendCommand(proc, "TAP")
                            }
                        }
                        onPassthroughDoubleTap = {
                            powerScope.launch(Dispatchers.IO) {
                                if (!wasPressForwarded) {
                                    PowerGrabHelper.sendCommand(proc, "TAP")
                                    delay(80L)
                                    PowerGrabHelper.sendCommand(proc, "TAP")
                                }
                            }
                        }
                        onPassthroughHoldStart = { PowerGrabHelper.sendDown(proc) }
                        onPassthroughHoldEnd = { PowerGrabHelper.sendUp(proc) }

                        try {
                            val reader = proc.inputStream.bufferedReader()
                            while (isActive && isPowerEnabled(context) && ElevatedTaskCloser.isShizukuActive) {
                                val line = reader.readLine() ?: break
                                backoffMs = 1000L
                                val trimmed = line.trim()
                                if (!trimmed.startsWith("P ")) continue

                                val isDown = trimmed.contains("DOWN")
                                val isUp = trimmed.contains("UP")
                                if (isDown || isUp) {
                                    withContext(Dispatchers.Main) {
                                        if (isDown) {
                                            LightspeedKeyEngine.onKeyEvent(context, KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_POWER))
                                        } else if (isUp) {
                                            val consumed = LightspeedKeyEngine.onKeyEvent(context, KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_POWER))
                                            if (!consumed && !wasPressForwarded) {
                                                PowerGrabHelper.sendCommand(proc, "TAP")
                                            }
                                        }
                                    }
                                }
                            }
                        } catch (e: Exception) {
                            Log.d("LightspeedKeyEngine", "Shizuku power grab monitor read error: ${e.message}")
                        } finally {
                            PowerGrabHelper.sendCommand(proc, "QUIT")
                            try {
                                proc.destroy()
                            } catch (e: Exception) {
                                logSwallowed("LightspeedPowerKeyEngine", "startShizukuPowerMonitor:procDestroy", e)
                            }
                            if (shizukuProcess == proc) {
                                shizukuProcess = null
                            }
                        }

                        if (!isActive || !isPowerEnabled(context) || !ElevatedTaskCloser.isShizukuActive) break

                        withContext(Dispatchers.Main) { resetPowerState() }
                        delay(backoffMs)
                        backoffMs = (backoffMs * 2).coerceAtMost(10000L)
                    }
                } finally {
                    onPassthroughPress = null
                    onPassthroughDoubleTap = null
                    onPassthroughHoldStart = null
                    onPassthroughHoldEnd = null
                }
            }
            return
        }

        shizukuMonitorJob = powerScope.launch(Dispatchers.IO) {
            var backoffMs = 1000L
            while (isActive && isPowerEnabled(context) && ElevatedTaskCloser.isShizukuActive) {
                try {
                    shizukuProcess?.destroy()
                } catch (e: Exception) {
                    logSwallowed("LightspeedPowerKeyEngine", "startShizukuPowerMonitor:destroy", e)
                }
                shizukuProcess = null

                val devicePath = PowerGrabHelper.findPowerDevice()
                val isPowerKeyOnly = true
                val isNarrowPath = devicePath != null && devicePath.matches(Regex("^/dev/input/event[0-9]+$"))
                val cmd = if (isPowerKeyOnly && isNarrowPath) {
                    Log.i("LightspeedPowerKeyEngine", "startShizukuPowerMonitor: using narrowed command getevent -l $devicePath")
                    "getevent -l $devicePath 2>&1"
                } else {
                    Log.i("LightspeedPowerKeyEngine", "startShizukuPowerMonitor: using full command getevent -l")
                    "getevent -l 2>&1"
                }

                val proc = ElevatedTaskCloser.execShizuku(cmd)
                if (proc == null) {
                    if (!isActive || !isPowerEnabled(context) || !ElevatedTaskCloser.isShizukuActive) break
                    withContext(Dispatchers.Main) { resetPowerState() }
                    delay(backoffMs)
                    backoffMs = (backoffMs * 2).coerceAtMost(10000L)
                    continue
                }

                shizukuProcess = proc
                try {
                    val reader = proc.inputStream.bufferedReader()
                    while (isActive && isPowerEnabled(context) && ElevatedTaskCloser.isShizukuActive) {
                        val line = reader.readLine() ?: break
                        backoffMs = 1000L
                        if (line.contains("KEY_POWER", ignoreCase = true)) {
                            val trimmed = line.trim()
                            val isDown = trimmed.endsWith("DOWN", ignoreCase = true)
                            val isUp = trimmed.endsWith("UP", ignoreCase = true)
                            if (isDown || isUp) {
                                withContext(Dispatchers.Main) {
                                    if (isDown) {
                                        LightspeedKeyEngine.onKeyEvent(context, KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_POWER))
                                    } else if (isUp) {
                                        LightspeedKeyEngine.onKeyEvent(context, KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_POWER))
                                    }
                                }
                            }
                        }
                    }
                } catch (e: Exception) {
                    Log.d("LightspeedKeyEngine", "Shizuku power monitor read error: ${e.message}")
                } finally {
                    try {
                        proc.destroy()
                    } catch (e: Exception) {
                        logSwallowed("LightspeedPowerKeyEngine", "startShizukuPowerMonitor:procDestroy", e)
                    }
                    if (shizukuProcess == proc) {
                        shizukuProcess = null
                    }
                }

                if (!isActive || !isPowerEnabled(context) || !ElevatedTaskCloser.isShizukuActive) break

                withContext(Dispatchers.Main) { resetPowerState() }
                delay(backoffMs)
                backoffMs = (backoffMs * 2).coerceAtMost(10000L)
            }
        }
    }

    fun stopShizukuPowerMonitor() {
        shizukuMonitorJob?.cancel()
        shizukuMonitorJob = null
        onPassthroughPress = null
        onPassthroughDoubleTap = null
        onPassthroughHoldStart = null
        onPassthroughHoldEnd = null
        try {
            shizukuProcess?.destroy()
            shizukuProcess = null
        } catch (e: Exception) { logSwallowed("LightspeedPowerKeyEngine", "stopShizukuPowerMonitor", e) }
        powerScope.cancel()
        powerScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    }
}
