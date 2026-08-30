package com.sbf.lightspeed.system

import android.content.Context
import android.os.SystemClock
import android.view.KeyEvent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Low-latency, customizable hardware volume button gesture engine for Lightspeed.
 *
 * Provides non-debounced, zero-latency passthrough for standard single clicks and
 * hardware system shortcuts (e.g. Power + Vol Down screenshot) while detecting
 * extended hold triggers, chords, and multi-key sequences using lightweight coroutines.
 */
object LightspeedKeyEngine {

    const val LONG_PRESS_TIMEOUT_MS = 400L
    const val SEQUENCE_TIMEOUT_MS = 250L

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
        CHORD_DOWN_HOLD_UP_HOLD(
            LightspeedPreferences.KEY_CHORD_DOWN_HOLD_UP_HOLD,
            "Hold Vol Down + Hold Vol Up",
            "Hold Volume Down, hold Volume Up for ~400ms"
        ),
        CHORD_UP_HOLD_DOWN_HOLD(
            LightspeedPreferences.KEY_CHORD_UP_HOLD_DOWN_HOLD,
            "Hold Vol Up + Hold Vol Down",
            "Hold Volume Up, hold Volume Down for ~400ms"
        ),
        SEQ_UP_THEN_DOWN(
            LightspeedPreferences.KEY_SEQ_UP_THEN_DOWN,
            "Sequence: Vol Up → Vol Down",
            "Tap Volume Up, then tap Volume Down within 250ms"
        ),
        SEQ_DOWN_THEN_UP(
            LightspeedPreferences.KEY_SEQ_DOWN_THEN_UP,
            "Sequence: Vol Down → Vol Up",
            "Tap Volume Down, then tap Volume Up within 250ms"
        )
    }

    private val engineScope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    // Live Key Press States
    private var isVolUpPressed = false
    private var isVolDownPressed = false

    // Long Press States
    private var isVolUpLongPressed = false
    private var isVolDownLongPressed = false

    // Chord States
    private var isVolUpUsedInChord = false
    private var isVolDownUsedInChord = false
    private var isChordHoldFired = false

    // Sequence States
    private var isSequenceFired = false
    private var lastVolUpReleaseTime = 0L
    private var lastVolDownReleaseTime = 0L

    // Coroutine Hold Timer Jobs
    private var volUpHoldJob: Job? = null
    private var volDownHoldJob: Job? = null
    private var chordHoldJob: Job? = null

    fun isEnabled(context: Context): Boolean {
        val prefs = context.defaultPrefs()
        return prefs.getBoolean(LightspeedPreferences.KEY_VOL_GESTURES_ENABLED, true)
    }

    fun getBoundAction(context: Context, slot: VolumeTriggerSlot): String? {
        val prefs = context.defaultPrefs()
        val raw = prefs.getString(slot.prefKey, null)?.trim()
        return if (raw.isNullOrEmpty() || raw == "none") null else raw
    }

    /**
     * Intercepts and processes hardware volume key events routed from LightspeedAccessibilityService.
     *
     * @param context Context (AccessibilityService instance)
     * @param event The KeyEvent received
     * @return true if consumed (gesture matched and action executed); false to allow native OS passthrough
     */
    fun onKeyEvent(context: Context, event: KeyEvent): Boolean {
        val keyCode = event.keyCode
        if (keyCode != KeyEvent.KEYCODE_VOLUME_UP && keyCode != KeyEvent.KEYCODE_VOLUME_DOWN) {
            return false
        }

        if (!isEnabled(context)) {
            return false
        }

        val action = event.action
        val now = SystemClock.uptimeMillis()

        when (keyCode) {
            KeyEvent.KEYCODE_VOLUME_UP -> {
                if (action == KeyEvent.ACTION_DOWN) {
                    // Suppress repeat events once gesture or chord has engaged, and support continuous hardware scrubbing
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
                        return isVolUpLongPressed || isChordHoldFired || isSequenceFired || isVolUpUsedInChord
                    }

                    isVolUpPressed = true
                    isVolUpLongPressed = false
                    isChordHoldFired = false

                    // 1. Sequence Trigger Check: SEQ_DOWN_THEN_UP
                    val diffDown = now - lastVolDownReleaseTime
                    if (lastVolDownReleaseTime > 0L && diffDown <= SEQUENCE_TIMEOUT_MS && !isVolDownPressed) {
                        lastVolDownReleaseTime = 0L
                        val boundAction = getBoundAction(context, VolumeTriggerSlot.SEQ_DOWN_THEN_UP)
                        if (boundAction != null) {
                            isSequenceFired = true
                            LightspeedHapticEngine.click(context)
                            ActionDispatcher.dispatch(boundAction, context)
                            return true
                        }
                    }

                    // 2. Chord Trigger Check: Vol Down is held down
                    if (isVolDownPressed) {
                        isVolDownUsedInChord = true
                        isVolUpUsedInChord = true
                        volDownHoldJob?.cancel()
                        volDownHoldJob = null
                        volUpHoldJob?.cancel()
                        volUpHoldJob = null

                        val chordHoldAction = getBoundAction(context, VolumeTriggerSlot.CHORD_DOWN_HOLD_UP_HOLD)
                        if (chordHoldAction != null) {
                            chordHoldJob?.cancel()
                            chordHoldJob = engineScope.launch {
                                delay(LONG_PRESS_TIMEOUT_MS)
                                isChordHoldFired = true
                                LightspeedHapticEngine.heavyClick(context)
                                ActionDispatcher.dispatch(chordHoldAction, context)
                            }
                        }
                        return true
                    }

                    // 3. Single Key Press: Start long press timer
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
                        }
                    }

                    // Pass through DOWN event so native volume adjustment has 0ms latency
                    return false
                } else if (action == KeyEvent.ACTION_UP) {
                    isVolUpPressed = false
                    volUpHoldJob?.cancel()
                    volUpHoldJob = null
                    chordHoldJob?.cancel()
                    chordHoldJob = null

                    if (isSequenceFired) {
                        isSequenceFired = false
                        return true
                    }

                    if (isVolUpLongPressed) {
                        isVolUpLongPressed = false
                        return true
                    }

                    if (isChordHoldFired) {
                        isChordHoldFired = false
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

                    // Record release timestamp for upcoming sequence window
                    lastVolUpReleaseTime = now
                    return false
                }
            }

            KeyEvent.KEYCODE_VOLUME_DOWN -> {
                if (action == KeyEvent.ACTION_DOWN) {
                    // Suppress repeat events once gesture or chord has engaged, and support continuous hardware scrubbing
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
                        return isVolDownLongPressed || isChordHoldFired || isSequenceFired || isVolDownUsedInChord
                    }

                    isVolDownPressed = true
                    isVolDownLongPressed = false
                    isChordHoldFired = false

                    // 1. Sequence Trigger Check: SEQ_UP_THEN_DOWN
                    val diffUp = now - lastVolUpReleaseTime
                    if (lastVolUpReleaseTime > 0L && diffUp <= SEQUENCE_TIMEOUT_MS && !isVolUpPressed) {
                        lastVolUpReleaseTime = 0L
                        val boundAction = getBoundAction(context, VolumeTriggerSlot.SEQ_UP_THEN_DOWN)
                        if (boundAction != null) {
                            isSequenceFired = true
                            LightspeedHapticEngine.click(context)
                            ActionDispatcher.dispatch(boundAction, context)
                            return true
                        }
                    }

                    // 2. Chord Trigger Check: Vol Up is held down
                    if (isVolUpPressed) {
                        isVolUpUsedInChord = true
                        isVolDownUsedInChord = true
                        volUpHoldJob?.cancel()
                        volUpHoldJob = null
                        volDownHoldJob?.cancel()
                        volDownHoldJob = null

                        val chordHoldAction = getBoundAction(context, VolumeTriggerSlot.CHORD_UP_HOLD_DOWN_HOLD)
                        if (chordHoldAction != null) {
                            chordHoldJob?.cancel()
                            chordHoldJob = engineScope.launch {
                                delay(LONG_PRESS_TIMEOUT_MS)
                                isChordHoldFired = true
                                LightspeedHapticEngine.heavyClick(context)
                                ActionDispatcher.dispatch(chordHoldAction, context)
                            }
                        }
                        return true
                    }

                    // 3. Single Key Press: Start long press timer
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
                        }
                    }

                    // Pass through DOWN event so screenshot (Power + Vol Down) and native volume down work with 0ms latency
                    return false
                } else if (action == KeyEvent.ACTION_UP) {
                    isVolDownPressed = false
                    volDownHoldJob?.cancel()
                    volDownHoldJob = null
                    chordHoldJob?.cancel()
                    chordHoldJob = null

                    if (isSequenceFired) {
                        isSequenceFired = false
                        return true
                    }

                    if (isVolDownLongPressed) {
                        isVolDownLongPressed = false
                        return true
                    }

                    if (isChordHoldFired) {
                        isChordHoldFired = false
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

                    // Record release timestamp for upcoming sequence window
                    lastVolDownReleaseTime = now
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
        volUpHoldJob?.cancel()
        volUpHoldJob = null
        volDownHoldJob?.cancel()
        volDownHoldJob = null
        chordHoldJob?.cancel()
        chordHoldJob = null

        isVolUpPressed = false
        isVolDownPressed = false
        isVolUpLongPressed = false
        isVolDownLongPressed = false
        isVolUpUsedInChord = false
        isVolDownUsedInChord = false
        isChordHoldFired = false
        isSequenceFired = false
        lastVolUpReleaseTime = 0L
        lastVolDownReleaseTime = 0L
    }
}
