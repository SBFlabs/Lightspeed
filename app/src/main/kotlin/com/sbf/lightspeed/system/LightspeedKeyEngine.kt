package com.sbf.lightspeed.system

import android.content.Context
import android.media.AudioManager
import android.os.SystemClock
import android.view.KeyEvent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Low-latency, customizable hardware volume button gesture engine for Lightspeed.
 *
 * Supports Zero-Lag or Clean Suppression, Tap-then-Hold sequences, OEM Shield
 * accessibility bypasses, and interactive Hardware Gear Set HUD Navigation.
 */
object LightspeedKeyEngine {

    const val LONG_PRESS_TIMEOUT_MS = 400L
    const val SEQUENCE_TIMEOUT_MS = 300L
    const val ACCESSIBILITY_SHORTCUT_TIMEOUT_MS = 1500L

    // Forwarding for HUD navigation state
    val isHudNavActive: Boolean get() = LightspeedKeyHudNav.isHudNavActive
    val currentNavState: HudNavState? get() = LightspeedKeyHudNav.currentNavState

    var onNavStateListener: ((HudNavState?) -> Unit)?
        get() = LightspeedKeyHudNav.onNavStateListener
        set(value) { LightspeedKeyHudNav.onNavStateListener = value }

    fun startHudNav(context: Context) = LightspeedKeyHudNav.startHudNav(context)
    fun exitHudNav() = LightspeedKeyHudNav.exitHudNav()

    // Forwarding for Power button gestures
    val wasScreenInteractiveAtDown: Boolean
        get() = LightspeedPowerKeyEngine.wasScreenInteractiveAtDown

    fun onPowerGestureHandled() = LightspeedPowerKeyEngine.onPowerGestureHandled()
    fun isAssistantActiveOrPending(context: Context): Boolean = LightspeedPowerKeyEngine.isAssistantActiveOrPending(context)
    fun isSinglePressUnlocked(context: Context): Boolean = LightspeedPowerKeyEngine.isSinglePressUnlocked(context)
    fun isPowerEnabled(context: Context): Boolean = LightspeedPowerKeyEngine.isPowerEnabled(context)
    fun getBoundPowerAction(context: Context, slot: PowerTriggerSlot): String? =
        LightspeedPowerKeyEngine.getBoundPowerAction(context, slot)
    fun startShizukuPowerMonitor(context: Context) = LightspeedPowerKeyEngine.startShizukuPowerMonitor(context)
    fun stopShizukuPowerMonitor() = LightspeedPowerKeyEngine.stopShizukuPowerMonitor()

    private var engineScope = CoroutineScope(Dispatchers.Main + SupervisorJob())

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

    // OEM Accessibility Shortcut State
    private var isAccessibilityBypassed = false

    // Coroutine Jobs (Volume)
    private var volUpHoldJob: Job? = null
    private var volDownHoldJob: Job? = null
    private var seqTapHoldJob: Job? = null
    private var autoRepeatJob: Job? = null
    private var accessibilityShortcutJob: Job? = null

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
        // MODE C: POWER BUTTON ENGINE (Delegated)
        // =========================================================================
        if (keyCode == KeyEvent.KEYCODE_POWER) {
            return LightspeedPowerKeyEngine.handlePowerKeyEvent(context, event, now)
        }

        if (keyCode != KeyEvent.KEYCODE_VOLUME_UP && keyCode != KeyEvent.KEYCODE_VOLUME_DOWN) {
            return false
        }

        if (!isEnabled(context) && !isHudNavActive) {
            return false
        }

        // =========================================================================
        // MODE A: HARDWARE GEAR SET HUD NAVIGATION MODE (Delegated)
        // =========================================================================
        if (isHudNavActive) {
            return LightspeedKeyHudNav.handleHudNavKeyEvent(context, event)
        }

        val cleanSuppression = isCleanSuppression(context)
        val prefs = context.defaultPrefs()
        val preserveAccessibility = prefs.getBoolean(LightspeedPreferences.KEY_OEM_PRESERVE_ACCESSIBILITY, true)

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
     * Resets all internal state machines, release timers, and pending coroutine hold jobs.
     */
    fun reset() {
        engineScope.cancel()
        engineScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
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

        isVolUpPressed = false
        isVolDownPressed = false
        isVolUpLongPressed = false
        isVolDownLongPressed = false
        isVolUpUsedInChord = false
        isVolDownUsedInChord = false
        isSequenceFired = false
        isSeqTapHoldFired = false
        isAccessibilityBypassed = false
        lastVolUpReleaseTime = 0L
        lastVolDownReleaseTime = 0L

        LightspeedKeyHudNav.reset()
        LightspeedPowerKeyEngine.resetPowerState()
    }
}
