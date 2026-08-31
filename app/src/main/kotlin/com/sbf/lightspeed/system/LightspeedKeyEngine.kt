package com.sbf.lightspeed.system

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.media.AudioManager
import android.os.SystemClock
import android.util.Log
import android.view.KeyEvent
import com.sbf.lightspeed.LightspeedAccessibilityService
import com.sbf.lightspeed.settings.resolveDynamicTokenLabel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Low-latency, customizable hardware volume button gesture engine for Lightspeed.
 *
 * Supports Zero-Lag or Clean Suppression, Tap-then-Hold sequences, OEM Shield
 * accessibility bypasses, and interactive Hardware Gear Set HUD Navigation.
 */
object LightspeedKeyEngine {

    const val LONG_PRESS_TIMEOUT_MS = 400L
    const val SEQUENCE_TIMEOUT_MS = 300L
    const val POWER_SEQUENCE_TIMEOUT_MS = 450L
    const val ACCESSIBILITY_SHORTCUT_TIMEOUT_MS = 1500L
    const val HUD_NAV_INACTIVITY_TIMEOUT_MS = 5000L

    enum class VolumeTriggerSlot(
        val prefKey: String,
        val title: String,
        val description: String
    ) {
        VOL_UP_LONG_PRESS(
            LightspeedPreferences.KEY_VOL_UP_LONG_PRESS,
            "Volume Up Long Press",
            "Hold Volume Up for ~400ms"
        ),
        VOL_DOWN_LONG_PRESS(
            LightspeedPreferences.KEY_VOL_DOWN_LONG_PRESS,
            "Volume Down Long Press",
            "Hold Volume Down for ~400ms"
        ),
        CHORD_DOWN_HOLD_UP_TAP(
            LightspeedPreferences.KEY_CHORD_DOWN_HOLD_UP_TAP,
            "Hold Vol Down + Tap Vol Up",
            "Hold Volume Down, tap Volume Up"
        ),
        CHORD_UP_HOLD_DOWN_TAP(
            LightspeedPreferences.KEY_CHORD_UP_HOLD_DOWN_TAP,
            "Hold Vol Up + Tap Vol Down",
            "Hold Volume Up, tap Volume Down"
        ),
        SEQ_UP_THEN_DOWN(
            LightspeedPreferences.KEY_SEQ_UP_THEN_DOWN,
            "Sequence: Vol Up → Vol Down",
            "Tap Volume Up, then tap Volume Down within 300ms"
        ),
        SEQ_DOWN_THEN_UP(
            LightspeedPreferences.KEY_SEQ_DOWN_THEN_UP,
            "Sequence: Vol Down → Vol Up",
            "Tap Volume Down, then tap Volume Up within 300ms"
        ),
        SEQ_DOWN_TAP_THEN_UP_HOLD(
            LightspeedPreferences.KEY_SEQ_DOWN_TAP_THEN_UP_HOLD,
            "Tap Vol Down → Hold Vol Up",
            "Tap Vol Down, then press & hold Vol Up within 300ms for ~400ms"
        ),
        SEQ_UP_TAP_THEN_DOWN_HOLD(
            LightspeedPreferences.KEY_SEQ_UP_TAP_THEN_DOWN_HOLD,
            "Tap Vol Up → Hold Vol Down",
            "Tap Vol Up, then press & hold Vol Down within 300ms for ~400ms"
        )
    }

    enum class PowerTriggerSlot(
        val prefKey: String,
        val title: String,
        val description: String
    ) {
        POWER_SINGLE_PRESS(
            LightspeedPreferences.KEY_POWER_SINGLE_PRESS,
            "Power Single Press",
            "Tap Power button once"
        ),
        POWER_DOUBLE_PRESS(
            LightspeedPreferences.KEY_POWER_DOUBLE_PRESS,
            "Power Double Press",
            "Double-tap Power button within 300ms"
        ),
        POWER_HOLD(
            LightspeedPreferences.KEY_POWER_HOLD,
            "Power Button Hold (Long Press)",
            "Hold Power button for ~400ms"
        ),
        POWER_PRESS_THEN_HOLD(
            LightspeedPreferences.KEY_POWER_PRESS_THEN_HOLD,
            "Power Tap-then-Hold",
            "Tap Power button, then immediately press & hold for ~400ms"
        )
    }

    data class HudNavState(
        val isActive: Boolean,
        val setName: String,
        val currentToken: String,
        val currentLabel: String,
        val currentIndex: Int,
        val totalCount: Int
    )

    var currentNavState: HudNavState? = null
        private set
    var onNavStateListener: ((HudNavState?) -> Unit)? = null

    private val engineScope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    // Live Key Press States (Volume)
    private var isVolUpPressed = false
    private var isVolDownPressed = false

    // Long Press States (Volume)
    private var isVolUpLongPressed = false
    private var isVolDownLongPressed = false

    // Chord States (Volume)
    private var isVolUpUsedInChord = false
    private var isVolDownUsedInChord = false

    // Sequence & Tap-Then-Hold States (Volume)
    private var isSequenceFired = false
    private var isSeqTapHoldFired = false
    private var lastVolUpReleaseTime = 0L
    private var lastVolDownReleaseTime = 0L

    // Live Key Press & Trigger States (Power Button Engine)
    private var isPowerPressed = false
    private var isPowerHoldFired = false
    private var isPowerPressHoldFired = false
    private var lastPowerReleaseTime = 0L
    private var wasScreenInteractiveAtDown = true

    // OEM Accessibility Shortcut State
    private var isAccessibilityBypassed = false

    // Hardware HUD Navigation States
    var isHudNavActive = false
        private set
    private var navItems: List<String> = emptyList()
    private var navIndex = 0
    private var navSetName = ""
    private var isVolUpNavHoldFired = false
    private var isVolDownNavHoldFired = false

    // Coroutine Jobs (Volume & Nav)
    private var volUpHoldJob: Job? = null
    private var volDownHoldJob: Job? = null
    private var seqTapHoldJob: Job? = null
    private var autoRepeatJob: Job? = null
    private var accessibilityShortcutJob: Job? = null
    private var hudNavInactivityJob: Job? = null

    // Coroutine Jobs (Power Button Engine)
    private var powerHoldJob: Job? = null
    private var powerPressHoldJob: Job? = null
    private var powerSinglePressJob: Job? = null
    private var powerWakeLock: android.os.PowerManager.WakeLock? = null

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

    fun isEnabled(context: Context): Boolean {
        val prefs = context.defaultPrefs()
        return prefs.getBoolean(LightspeedPreferences.KEY_VOL_GESTURES_ENABLED, true)
    }

    fun getSuppressionProfile(context: Context): String {
        val prefs = context.defaultPrefs()
        val profile = prefs.getString(LightspeedPreferences.KEY_VOLUME_SUPPRESSION_PROFILE, null)
        if (!profile.isNullOrEmpty()) return profile
        val legacyClean = prefs.getBoolean(LightspeedPreferences.KEY_CLEAN_VOLUME_SUPPRESSION, false)
        return if (legacyClean) "balanced_holds" else "instant_reflex"
    }

    fun isCleanSuppression(context: Context): Boolean {
        val profile = getSuppressionProfile(context)
        return profile == "balanced_holds" || profile == "total_clean"
    }

    fun getBoundAction(context: Context, slot: VolumeTriggerSlot): String? {
        val prefs = context.defaultPrefs()
        val raw = prefs.getString(slot.prefKey, null)?.trim()
        return if (raw.isNullOrEmpty() || raw == "none") null else raw
    }

    private fun startAutoRepeat(context: Context, actionToken: String) {
        val prefs = context.defaultPrefs()
        val autoRepeatEnabled = prefs.getBoolean(LightspeedPreferences.KEY_KEY_HOLD_AUTO_REPEAT, false)
        if (!autoRepeatEnabled) return

        val intervalMs = prefs.getInt(LightspeedPreferences.KEY_KEY_REPEAT_INTERVAL_MS, 200).toLong().coerceIn(80L, 1000L)
        autoRepeatJob?.cancel()
        autoRepeatJob = engineScope.launch {
            while (true) {
                delay(intervalMs)
                LightspeedHapticEngine.scrubTick(context)
                ActionDispatcher.dispatch(actionToken, context)
            }
        }
    }

    private fun stopAutoRepeat() {
        autoRepeatJob?.cancel()
        autoRepeatJob = null
    }

    /**
     * Intercepts and processes hardware volume and power key events routed from LightspeedAccessibilityService.
     */
    fun onKeyEvent(context: Context, event: KeyEvent): Boolean {
        val keyCode = event.keyCode
        val action = event.action
        val now = SystemClock.uptimeMillis()

        // =========================================================================
        // MODE C: POWER BUTTON ENGINE (4 HARDWARE TRIGGER STATES)
        // =========================================================================
        if (keyCode == KeyEvent.KEYCODE_POWER) {
            if (!isPowerEnabled(context)) return false

            val singleAction = getBoundPowerAction(context, PowerTriggerSlot.POWER_SINGLE_PRESS)
            val doubleAction = getBoundPowerAction(context, PowerTriggerSlot.POWER_DOUBLE_PRESS)
            val holdAction = getBoundPowerAction(context, PowerTriggerSlot.POWER_HOLD)
            val pressHoldAction = getBoundPowerAction(context, PowerTriggerSlot.POWER_PRESS_THEN_HOLD)

            val hasAnyPowerAction = singleAction != null || doubleAction != null || holdAction != null || pressHoldAction != null
            if (!hasAnyPowerAction) return false

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
                        powerPressHoldJob = engineScope.launch {
                            delay(LONG_PRESS_TIMEOUT_MS)
                            if (isAssistantActiveOrPending(context)) {
                                reset()
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
                    powerHoldJob = engineScope.launch {
                        delay(LONG_PRESS_TIMEOUT_MS)
                        if (isAssistantActiveOrPending(context)) {
                            reset()
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
                    powerSinglePressJob = engineScope.launch {
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
                            if (wasScreenInteractiveAtDown) {
                                LightspeedAccessibilityService.instance?.performGlobalAction(
                                    AccessibilityService.GLOBAL_ACTION_LOCK_SCREEN
                                )
                            }
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

        if (keyCode != KeyEvent.KEYCODE_VOLUME_UP && keyCode != KeyEvent.KEYCODE_VOLUME_DOWN) {
            return false
        }

        if (!isEnabled(context) && !isHudNavActive) {
            return false
        }

        val cleanSuppression = isCleanSuppression(context)
        val prefs = context.defaultPrefs()
        val preserveAccessibility = prefs.getBoolean(LightspeedPreferences.KEY_OEM_PRESERVE_ACCESSIBILITY, true)

        // =========================================================================
        // MODE A: HARDWARE GEAR SET HUD NAVIGATION MODE (100% Volume Consumption)
        // =========================================================================
        if (isHudNavActive) {
            resetNavInactivityTimer()

            when (keyCode) {
                KeyEvent.KEYCODE_VOLUME_UP -> {
                    if (action == KeyEvent.ACTION_DOWN) {
                        if (event.repeatCount > 0) return true
                        isVolUpNavHoldFired = false
                        volUpHoldJob?.cancel()
                        volUpHoldJob = engineScope.launch {
                            delay(LONG_PRESS_TIMEOUT_MS)
                            isVolUpNavHoldFired = true
                            LightspeedHapticEngine.heavyClick(context)
                            navLaunch(context)
                        }
                        return true
                    } else if (action == KeyEvent.ACTION_UP) {
                        volUpHoldJob?.cancel()
                        volUpHoldJob = null
                        if (!isVolUpNavHoldFired) {
                            navigatePrevious(context)
                        }
                        isVolUpNavHoldFired = false
                        return true
                    }
                }
                KeyEvent.KEYCODE_VOLUME_DOWN -> {
                    if (action == KeyEvent.ACTION_DOWN) {
                        if (event.repeatCount > 0) return true
                        isVolDownNavHoldFired = false
                        volDownHoldJob?.cancel()
                        volDownHoldJob = engineScope.launch {
                            delay(LONG_PRESS_TIMEOUT_MS)
                            isVolDownNavHoldFired = true
                            LightspeedHapticEngine.heavyClick(context)
                            exitHudNav()
                        }
                        return true
                    } else if (action == KeyEvent.ACTION_UP) {
                        volDownHoldJob?.cancel()
                        volDownHoldJob = null
                        if (!isVolDownNavHoldFired) {
                            navigateNext(context)
                        }
                        isVolDownNavHoldFired = false
                        return true
                    }
                }
            }
            return true
        }

        // =========================================================================
        // MODE B: STANDARD HARDWARE GESTURES & CHORDS
        // =========================================================================
        when (keyCode) {
            KeyEvent.KEYCODE_VOLUME_UP -> {
                if (action == KeyEvent.ACTION_DOWN) {
                    if (event.repeatCount > 0) {
                        if (isVolUpLongPressed) {
                            val bound = getBoundAction(context, VolumeTriggerSlot.VOL_UP_LONG_PRESS)
                            if (bound == "system:media_scrubber" || bound == "system:media_skip_forward" || bound == "system:media_skip_backward") {
                                if (event.repeatCount % 2 == 0) {
                                    LightspeedMediaManager.stepHardwareScrubber(context, isForward = true)
                                    LightspeedHapticEngine.scrubTick(context)
                                }
                            }
                        }
                        return isVolUpLongPressed || isSequenceFired || isSeqTapHoldFired || isVolUpUsedInChord
                    }

                    isVolUpPressed = true
                    isVolUpLongPressed = false
                    isSeqTapHoldFired = false

                    // Check OEM Accessibility Shortcut simultaneous hold bypass
                    if (isVolDownPressed && preserveAccessibility) {
                        accessibilityShortcutJob?.cancel()
                        accessibilityShortcutJob = engineScope.launch {
                            delay(ACCESSIBILITY_SHORTCUT_TIMEOUT_MS)
                            isAccessibilityBypassed = true
                        }
                    }

                    if (isAccessibilityBypassed) {
                        return false
                    }

                    // 1. Sequence Tap-Then-Hold Check: SEQ_DOWN_TAP_THEN_UP_HOLD
                    val diffDown = now - lastVolDownReleaseTime
                    if (lastVolDownReleaseTime > 0L && diffDown <= SEQUENCE_TIMEOUT_MS && !isVolDownPressed) {
                        val tapHoldAction = getBoundAction(context, VolumeTriggerSlot.SEQ_DOWN_TAP_THEN_UP_HOLD)
                        if (tapHoldAction != null) {
                            seqTapHoldJob?.cancel()
                            seqTapHoldJob = engineScope.launch {
                                delay(LONG_PRESS_TIMEOUT_MS)
                                isSeqTapHoldFired = true
                                lastVolDownReleaseTime = 0L
                                LightspeedHapticEngine.heavyClick(context)
                                ActionDispatcher.dispatch(tapHoldAction, context)
                                startAutoRepeat(context, tapHoldAction)
                            }
                        }
                    }

                    // 2. Chord Check: Vol Down is held down
                    if (isVolDownPressed) {
                        isVolDownUsedInChord = true
                        isVolUpUsedInChord = true
                        volDownHoldJob?.cancel()
                        volDownHoldJob = null
                        volUpHoldJob?.cancel()
                        volUpHoldJob = null
                        stopAutoRepeat()
                        return true
                    }

                    // 3. Single Key Long Press Timer
                    isVolUpUsedInChord = false
                    isSequenceFired = false
                    volUpHoldJob?.cancel()

                    val longPressAction = getBoundAction(context, VolumeTriggerSlot.VOL_UP_LONG_PRESS)
                    if (longPressAction != null) {
                        volUpHoldJob = engineScope.launch {
                            delay(LONG_PRESS_TIMEOUT_MS)
                            isVolUpLongPressed = true
                            LightspeedHapticEngine.heavyClick(context)
                            ActionDispatcher.dispatch(longPressAction, context)
                            startAutoRepeat(context, longPressAction)
                        }
                    }

                    return cleanSuppression
                } else if (action == KeyEvent.ACTION_UP) {
                    isVolUpPressed = false
                    volUpHoldJob?.cancel()
                    volUpHoldJob = null
                    seqTapHoldJob?.cancel()
                    seqTapHoldJob = null
                    stopAutoRepeat()
                    accessibilityShortcutJob?.cancel()
                    accessibilityShortcutJob = null

                    if (isAccessibilityBypassed) {
                        isAccessibilityBypassed = false
                        return false
                    }

                    if (isSeqTapHoldFired) {
                        isSeqTapHoldFired = false
                        return true
                    }

                    if (isSequenceFired) {
                        isSequenceFired = false
                        return true
                    }

                    if (isVolUpLongPressed) {
                        isVolUpLongPressed = false
                        return true
                    }

                    if (isVolUpUsedInChord) {
                        val chordTapAction = getBoundAction(context, VolumeTriggerSlot.CHORD_DOWN_HOLD_UP_TAP)
                        if (chordTapAction != null) {
                            LightspeedHapticEngine.click(context)
                            ActionDispatcher.dispatch(chordTapAction, context)
                        }
                        isVolUpUsedInChord = false
                        return true
                    }

                    // Check Single Tap Sequence SEQ_DOWN_THEN_UP
                    val diffDown = now - lastVolDownReleaseTime
                    if (lastVolDownReleaseTime > 0L && diffDown <= SEQUENCE_TIMEOUT_MS && !isVolDownPressed) {
                        lastVolDownReleaseTime = 0L
                        val seqAction = getBoundAction(context, VolumeTriggerSlot.SEQ_DOWN_THEN_UP)
                        if (seqAction != null) {
                            isSequenceFired = true
                            LightspeedHapticEngine.click(context)
                            ActionDispatcher.dispatch(seqAction, context)
                            return true
                        }
                    }

                    lastVolUpReleaseTime = now

                    // Clean Hold Suppression Manual Volume Step
                    if (cleanSuppression) {
                        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
                        audioManager?.adjustSuggestedStreamVolume(
                            AudioManager.ADJUST_RAISE,
                            AudioManager.USE_DEFAULT_STREAM_TYPE,
                            AudioManager.FLAG_SHOW_UI
                        )
                        return true
                    }

                    return false
                }
            }

            KeyEvent.KEYCODE_VOLUME_DOWN -> {
                if (action == KeyEvent.ACTION_DOWN) {
                    if (event.repeatCount > 0) {
                        if (isVolDownLongPressed) {
                            val bound = getBoundAction(context, VolumeTriggerSlot.VOL_DOWN_LONG_PRESS)
                            if (bound == "system:media_scrubber" || bound == "system:media_skip_forward" || bound == "system:media_skip_backward") {
                                if (event.repeatCount % 2 == 0) {
                                    LightspeedMediaManager.stepHardwareScrubber(context, isForward = false)
                                    LightspeedHapticEngine.scrubTick(context)
                                }
                            }
                        }
                        return isVolDownLongPressed || isSequenceFired || isSeqTapHoldFired || isVolDownUsedInChord
                    }

                    isVolDownPressed = true
                    isVolDownLongPressed = false
                    isSeqTapHoldFired = false

                    // Check OEM Accessibility Shortcut simultaneous hold bypass
                    if (isVolUpPressed && preserveAccessibility) {
                        accessibilityShortcutJob?.cancel()
                        accessibilityShortcutJob = engineScope.launch {
                            delay(ACCESSIBILITY_SHORTCUT_TIMEOUT_MS)
                            isAccessibilityBypassed = true
                        }
                    }

                    if (isAccessibilityBypassed) {
                        return false
                    }

                    // 1. Sequence Tap-Then-Hold Check: SEQ_UP_TAP_THEN_DOWN_HOLD
                    val diffUp = now - lastVolUpReleaseTime
                    if (lastVolUpReleaseTime > 0L && diffUp <= SEQUENCE_TIMEOUT_MS && !isVolUpPressed) {
                        val tapHoldAction = getBoundAction(context, VolumeTriggerSlot.SEQ_UP_TAP_THEN_DOWN_HOLD)
                        if (tapHoldAction != null) {
                            seqTapHoldJob?.cancel()
                            seqTapHoldJob = engineScope.launch {
                                delay(LONG_PRESS_TIMEOUT_MS)
                                isSeqTapHoldFired = true
                                lastVolUpReleaseTime = 0L
                                LightspeedHapticEngine.heavyClick(context)
                                ActionDispatcher.dispatch(tapHoldAction, context)
                                startAutoRepeat(context, tapHoldAction)
                            }
                        }
                    }

                    // 2. Chord Check: Vol Up is held down
                    if (isVolUpPressed) {
                        isVolUpUsedInChord = true
                        isVolDownUsedInChord = true
                        volUpHoldJob?.cancel()
                        volUpHoldJob = null
                        volDownHoldJob?.cancel()
                        volDownHoldJob = null
                        stopAutoRepeat()
                        return true
                    }

                    // 3. Single Key Long Press Timer
                    isVolDownUsedInChord = false
                    isSequenceFired = false
                    volDownHoldJob?.cancel()

                    val longPressAction = getBoundAction(context, VolumeTriggerSlot.VOL_DOWN_LONG_PRESS)
                    if (longPressAction != null) {
                        volDownHoldJob = engineScope.launch {
                            delay(LONG_PRESS_TIMEOUT_MS)
                            isVolDownLongPressed = true
                            LightspeedHapticEngine.heavyClick(context)
                            ActionDispatcher.dispatch(longPressAction, context)
                            startAutoRepeat(context, longPressAction)
                        }
                    }

                    return cleanSuppression
                } else if (action == KeyEvent.ACTION_UP) {
                    isVolDownPressed = false
                    volDownHoldJob?.cancel()
                    volDownHoldJob = null
                    seqTapHoldJob?.cancel()
                    seqTapHoldJob = null
                    stopAutoRepeat()
                    accessibilityShortcutJob?.cancel()
                    accessibilityShortcutJob = null

                    if (isAccessibilityBypassed) {
                        isAccessibilityBypassed = false
                        return false
                    }

                    if (isSeqTapHoldFired) {
                        isSeqTapHoldFired = false
                        return true
                    }

                    if (isSequenceFired) {
                        isSequenceFired = false
                        return true
                    }

                    if (isVolDownLongPressed) {
                        isVolDownLongPressed = false
                        return true
                    }

                    if (isVolDownUsedInChord) {
                        val chordTapAction = getBoundAction(context, VolumeTriggerSlot.CHORD_UP_HOLD_DOWN_TAP)
                        if (chordTapAction != null) {
                            LightspeedHapticEngine.click(context)
                            ActionDispatcher.dispatch(chordTapAction, context)
                        }
                        isVolDownUsedInChord = false
                        return true
                    }

                    // Check Single Tap Sequence SEQ_UP_THEN_DOWN
                    val diffUp = now - lastVolUpReleaseTime
                    if (lastVolUpReleaseTime > 0L && diffUp <= SEQUENCE_TIMEOUT_MS && !isVolUpPressed) {
                        lastVolUpReleaseTime = 0L
                        val seqAction = getBoundAction(context, VolumeTriggerSlot.SEQ_UP_THEN_DOWN)
                        if (seqAction != null) {
                            isSequenceFired = true
                            LightspeedHapticEngine.click(context)
                            ActionDispatcher.dispatch(seqAction, context)
                            return true
                        }
                    }

                    lastVolDownReleaseTime = now

                    // Clean Hold Suppression Manual Volume Step
                    if (cleanSuppression) {
                        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
                        audioManager?.adjustSuggestedStreamVolume(
                            AudioManager.ADJUST_LOWER,
                            AudioManager.USE_DEFAULT_STREAM_TYPE,
                            AudioManager.FLAG_SHOW_UI
                        )
                        return true
                    }

                    return false
                }
            }
        }

        return false
    }

    /**
     * Starts Hardware Gear Set HUD Navigation Mode.
     */
    fun startHudNav(context: Context) {
        val prefs = context.defaultPrefs()
        val activeSetIndex = prefs.getInt("last_active_set_index", 0)
        val setsOrderStr = prefs.getString("gear_sets_order", "0,1,2,3") ?: "0,1,2,3"
        val setIds = setsOrderStr.split(",").map { it.trim() }.filter { it.isNotEmpty() }
        val currentSetId = setIds.getOrNull(activeSetIndex) ?: setIds.firstOrNull() ?: "0"

        val rawName = prefs.getString("gear_set_${currentSetId}_name", "") ?: ""
        navSetName = if (rawName.isEmpty() || rawName in listOf("SET A", "SET B", "SET C", "SET D", "SET")) {
            when (currentSetId) {
                "0" -> "POWER USER ANDROID"
                "1" -> "MY APP STORES"
                "2" -> "UTILITIES SECTOR"
                "3" -> "ENTERTAINMENT DECK"
                else -> "GEAR SET ${activeSetIndex + 1}"
            }
        } else rawName

        val r0 = prefs.getString("gear_set_${currentSetId}_ring_0_packages", "") ?: ""
        val r1 = prefs.getString("gear_set_${currentSetId}_ring_1_packages", "") ?: ""
        val combined = (r0.split(",") + r1.split(","))
            .map { it.trim() }
            .filter { it.isNotEmpty() && it != "none" }
            .distinct()

        navItems = if (combined.isNotEmpty()) combined else listOf("system:recents", "system:previous_app", "system:screenshot", "system:flashlight")
        navIndex = 0
        isHudNavActive = true

        publishHudNavState(context)
        resetNavInactivityTimer()
        LightspeedHapticEngine.heavyClick(context)
    }

    /**
     * Exits Hardware Gear Set HUD Navigation Mode.
     */
    fun exitHudNav() {
        isHudNavActive = false
        hudNavInactivityJob?.cancel()
        hudNavInactivityJob = null
        currentNavState = null
        onNavStateListener?.invoke(null)
    }

    private fun resetNavInactivityTimer() {
        hudNavInactivityJob?.cancel()
        hudNavInactivityJob = engineScope.launch {
            delay(HUD_NAV_INACTIVITY_TIMEOUT_MS)
            exitHudNav()
        }
    }

    private fun navigatePrevious(context: Context) {
        if (navItems.isEmpty()) return
        navIndex = (navIndex - 1 + navItems.size) % navItems.size
        publishHudNavState(context)
        LightspeedHapticEngine.click(context)
    }

    private fun navigateNext(context: Context) {
        if (navItems.isEmpty()) return
        navIndex = (navIndex + 1) % navItems.size
        publishHudNavState(context)
        LightspeedHapticEngine.click(context)
    }

    private fun navLaunch(context: Context) {
        val token = navItems.getOrNull(navIndex)
        exitHudNav()
        if (!token.isNullOrBlank()) {
            ActionDispatcher.dispatch(token, context)
        }
    }

    private fun publishHudNavState(context: Context) {
        val token = navItems.getOrNull(navIndex) ?: "none"
        val label = if (token.startsWith("shortcut:")) {
            LightspeedShortcutManager.resolveLabel(context, token)
        } else {
            resolveDynamicTokenLabel(context, token)
        }

        val state = HudNavState(
            isActive = true,
            setName = navSetName,
            currentToken = token,
            currentLabel = label,
            currentIndex = navIndex,
            totalCount = navItems.size
        )
        currentNavState = state
        onNavStateListener?.invoke(state)
    }

    /**
     * Resets all internal state machines, release timers, and pending coroutine hold jobs.
     */
    fun reset() {
        volUpHoldJob?.cancel()
        volUpHoldJob = null
        volDownHoldJob?.cancel()
        volDownHoldJob = null
        seqTapHoldJob?.cancel()
        seqTapHoldJob = null
        autoRepeatJob?.cancel()
        autoRepeatJob = null
        accessibilityShortcutJob?.cancel()
        accessibilityShortcutJob = null
        hudNavInactivityJob?.cancel()
        hudNavInactivityJob = null
        powerHoldJob?.cancel()
        powerHoldJob = null
        powerPressHoldJob?.cancel()
        powerPressHoldJob = null
        powerSinglePressJob?.cancel()
        powerSinglePressJob = null

        isVolUpPressed = false
        isVolDownPressed = false
        isVolUpLongPressed = false
        isVolDownLongPressed = false
        isVolUpUsedInChord = false
        isVolDownUsedInChord = false
        isSequenceFired = false
        isSeqTapHoldFired = false
        isPowerPressed = false
        isPowerHoldFired = false
        isPowerPressHoldFired = false
        wasScreenInteractiveAtDown = true
        isAccessibilityBypassed = false
        isHudNavActive = false
        currentNavState = null
        lastVolUpReleaseTime = 0L
        lastVolDownReleaseTime = 0L
        lastPowerReleaseTime = 0L
        releasePowerScreenWakeLock()
    }

    private var shizukuMonitorJob: Job? = null
    private var shizukuProcess: Process? = null

    fun startShizukuPowerMonitor(context: Context) {
        if (!isPowerEnabled(context) || !ElevatedTaskCloser.isShizukuActive) {
            stopShizukuPowerMonitor()
            return
        }
        if (shizukuMonitorJob?.isActive == true) return

        shizukuMonitorJob = engineScope.launch(Dispatchers.IO) {
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
                                onKeyEvent(context, KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_POWER))
                            } else if (isUp) {
                                onKeyEvent(context, KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_POWER))
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

