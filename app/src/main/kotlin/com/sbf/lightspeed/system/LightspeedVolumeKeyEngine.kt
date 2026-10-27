package com.sbf.lightspeed.system

import android.content.Context
import android.media.AudioManager
import android.os.SystemClock
import android.view.KeyEvent
import com.sbf.lightspeed.LightspeedStatusBarOverlay
import kotlin.math.roundToInt
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

object LightspeedVolumeKeyEngine {
    private var engineScope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    private var isVolUpPressed = false
    private var isVolDownPressed = false
    private var isVolUpLongPressed = false
    private var isVolDownLongPressed = false
    private var isVolUpUsedInChord = false
    private var isVolDownUsedInChord = false
    private var isSequenceFired = false
    private var isSeqTapHoldFired = false
    private var lastVolUpReleaseTime = 0L
    private var lastVolDownReleaseTime = 0L
    private var isAccessibilityBypassed = false

    private var volUpHoldJob: Job? = null
    private var volDownHoldJob: Job? = null
    private var seqTapHoldJob: Job? = null
    private var autoRepeatJob: Job? = null
    private var accessibilityShortcutJob: Job? = null

    private fun startAutoRepeat(context: Context, actionToken: String) {
        val prefs = context.defaultPrefs()
        if (!prefs.getBoolean(LightspeedPreferences.KEY_KEY_HOLD_AUTO_REPEAT, false)) return

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

    private fun handleScrubberRepeat(context: Context, slot: VolumeTriggerSlot, repeatCount: Int, isForward: Boolean) {
        val bound = LightspeedKeyEngine.getBoundAction(context, slot)
        if (bound == "system:media_scrubber" || bound == "system:media_skip_forward" || bound == "system:media_skip_backward") {
            if (repeatCount % 2 == 0) {
                LightspeedMediaManager.stepHardwareScrubber(context, isForward = isForward)
                LightspeedHapticEngine.scrubTick(context)
            }
        }
    }

    private fun checkSequenceTapHold(context: Context, diff: Long, isOppositePressed: Boolean, slot: VolumeTriggerSlot) {
        if (diff in 1..LightspeedKeyEngine.SEQUENCE_TIMEOUT_MS && !isOppositePressed) {
            val tapHoldAction = LightspeedKeyEngine.getBoundAction(context, slot)
            if (tapHoldAction != null) {
                seqTapHoldJob?.cancel()
                seqTapHoldJob = engineScope.launch {
                    delay(LightspeedKeyEngine.LONG_PRESS_TIMEOUT_MS)
                    isSeqTapHoldFired = true
                    lastVolDownReleaseTime = 0L
                    lastVolUpReleaseTime = 0L
                    LightspeedHapticEngine.heavyClick(context)
                    ActionDispatcher.dispatch(tapHoldAction, context)
                    startAutoRepeat(context, tapHoldAction)
                }
            }
        }
    }

    private fun checkSingleTapSequence(context: Context, diff: Long, isOppositePressed: Boolean, slot: VolumeTriggerSlot): Boolean {
        if (diff in 1..LightspeedKeyEngine.SEQUENCE_TIMEOUT_MS && !isOppositePressed) {
            lastVolDownReleaseTime = 0L
            lastVolUpReleaseTime = 0L
            val seqAction = LightspeedKeyEngine.getBoundAction(context, slot)
            if (seqAction != null) {
                isSequenceFired = true
                LightspeedHapticEngine.click(context)
                ActionDispatcher.dispatch(seqAction, context)
                return true
            }
        }
        return false
    }

    private fun performVolumeStep(context: Context, isUp: Boolean, isCustomHud: Boolean, cleanSuppression: Boolean): Boolean {
        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager ?: return false
        val direction = if (isUp) AudioManager.ADJUST_RAISE else AudioManager.ADJUST_LOWER
        val flags = if (!isCustomHud && cleanSuppression) AudioManager.FLAG_SHOW_UI else 0
        audioManager.adjustSuggestedStreamVolume(direction, AudioManager.USE_DEFAULT_STREAM_TYPE, flags)
        if (isCustomHud) {
            val prefs = context.defaultPrefs()
            val cur = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
            val max = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC).coerceAtLeast(1)
            val pct = ((cur.toFloat() / max.toFloat()) * 100).roundToInt().coerceIn(0, 100)
            val hudStyle = prefs.getString(LightspeedPreferences.KEY_HUD_STYLE_DEFAULT, LightspeedPreferences.DEFAULT_HUD_STYLE) ?: LightspeedPreferences.DEFAULT_HUD_STYLE
            LightspeedStatusBarOverlay.showActionHud(
                title = "MEDIA VOLUME",
                value = "$pct%",
                stepIndex = cur,
                totalSteps = max,
                durationMs = 1800L,
                style = hudStyle
            )
            return true
        } else if (cleanSuppression) {
            return true
        }
        return false
    }

    fun handleVolumeKeyEvent(context: Context, event: KeyEvent): Boolean {
        val keyCode = event.keyCode
        val action = event.action
        val now = SystemClock.uptimeMillis()

        val prefs = context.defaultPrefs()
        val isCustomHud = prefs.getBoolean(LightspeedPreferences.KEY_CUSTOM_VOLUME_HUD_ENABLED, false)
        val cleanSuppression = LightspeedKeyEngine.isCleanSuppression(context)
        val preserveAccessibility = prefs.getBoolean(LightspeedPreferences.KEY_OEM_PRESERVE_ACCESSIBILITY, true)

        return when (keyCode) {
            KeyEvent.KEYCODE_VOLUME_UP -> handleVolumeUp(context, action, event, now, isCustomHud, cleanSuppression, preserveAccessibility)
            KeyEvent.KEYCODE_VOLUME_DOWN -> handleVolumeDown(context, action, event, now, isCustomHud, cleanSuppression, preserveAccessibility)
            else -> false
        }
    }

    private fun handleVolumeUp(context: Context, action: Int, event: KeyEvent, now: Long, isCustomHud: Boolean, cleanSuppression: Boolean, preserveAccessibility: Boolean): Boolean {
        if (action == KeyEvent.ACTION_DOWN) {
            if (event.repeatCount > 0) {
                if (isVolUpLongPressed) handleScrubberRepeat(context, VolumeTriggerSlot.VOL_UP_LONG_PRESS, event.repeatCount, isForward = true)
                return isVolUpLongPressed || isSequenceFired || isSeqTapHoldFired || isVolUpUsedInChord
            }

            isVolUpPressed = true
            isVolUpLongPressed = false
            isSeqTapHoldFired = false

            if (isVolDownPressed && preserveAccessibility) {
                accessibilityShortcutJob?.cancel()
                accessibilityShortcutJob = engineScope.launch {
                    delay(LightspeedKeyEngine.ACCESSIBILITY_SHORTCUT_TIMEOUT_MS)
                    isAccessibilityBypassed = true
                }
            }
            if (isAccessibilityBypassed) return false

            val diffDown = now - lastVolDownReleaseTime
            checkSequenceTapHold(context, diffDown, isVolDownPressed, VolumeTriggerSlot.SEQ_DOWN_TAP_THEN_UP_HOLD)

            if (isVolDownPressed) {
                isVolDownUsedInChord = true
                isVolUpUsedInChord = true
                volDownHoldJob?.cancel(); volDownHoldJob = null
                volUpHoldJob?.cancel(); volUpHoldJob = null
                stopAutoRepeat()
                return true
            }

            isVolUpUsedInChord = false
            isSequenceFired = false
            volUpHoldJob?.cancel()

            val longPressAction = LightspeedKeyEngine.getBoundAction(context, VolumeTriggerSlot.VOL_UP_LONG_PRESS)
            if (longPressAction != null) {
                volUpHoldJob = engineScope.launch {
                    delay(LightspeedKeyEngine.LONG_PRESS_TIMEOUT_MS)
                    isVolUpLongPressed = true
                    LightspeedHapticEngine.heavyClick(context)
                    ActionDispatcher.dispatch(longPressAction, context)
                    startAutoRepeat(context, longPressAction)
                }
            }
            return cleanSuppression
        } else if (action == KeyEvent.ACTION_UP) {
            isVolUpPressed = false
            volUpHoldJob?.cancel(); volUpHoldJob = null
            seqTapHoldJob?.cancel(); seqTapHoldJob = null
            stopAutoRepeat()
            accessibilityShortcutJob?.cancel(); accessibilityShortcutJob = null

            if (isAccessibilityBypassed) { isAccessibilityBypassed = false; return false }
            if (isSeqTapHoldFired) { isSeqTapHoldFired = false; return true }
            if (isSequenceFired) { isSequenceFired = false; return true }
            if (isVolUpLongPressed) { isVolUpLongPressed = false; return true }

            if (isVolUpUsedInChord) {
                val chordAction = LightspeedKeyEngine.getBoundAction(context, VolumeTriggerSlot.CHORD_DOWN_HOLD_UP_TAP)
                if (chordAction != null) {
                    LightspeedHapticEngine.click(context)
                    ActionDispatcher.dispatch(chordAction, context)
                }
                isVolUpUsedInChord = false
                return true
            }

            val diffDown = now - lastVolDownReleaseTime
            if (checkSingleTapSequence(context, diffDown, isVolDownPressed, VolumeTriggerSlot.SEQ_DOWN_THEN_UP)) return true

            lastVolUpReleaseTime = now
            return performVolumeStep(context, isUp = true, isCustomHud = isCustomHud, cleanSuppression = cleanSuppression)
        }
        return false
    }

    private fun handleVolumeDown(context: Context, action: Int, event: KeyEvent, now: Long, isCustomHud: Boolean, cleanSuppression: Boolean, preserveAccessibility: Boolean): Boolean {
        if (action == KeyEvent.ACTION_DOWN) {
            if (event.repeatCount > 0) {
                if (isVolDownLongPressed) handleScrubberRepeat(context, VolumeTriggerSlot.VOL_DOWN_LONG_PRESS, event.repeatCount, isForward = false)
                return isVolDownLongPressed || isSequenceFired || isSeqTapHoldFired || isVolDownUsedInChord
            }

            isVolDownPressed = true
            isVolDownLongPressed = false
            isSeqTapHoldFired = false

            if (isVolUpPressed && preserveAccessibility) {
                accessibilityShortcutJob?.cancel()
                accessibilityShortcutJob = engineScope.launch {
                    delay(LightspeedKeyEngine.ACCESSIBILITY_SHORTCUT_TIMEOUT_MS)
                    isAccessibilityBypassed = true
                }
            }
            if (isAccessibilityBypassed) return false

            val diffUp = now - lastVolUpReleaseTime
            checkSequenceTapHold(context, diffUp, isVolUpPressed, VolumeTriggerSlot.SEQ_UP_TAP_THEN_DOWN_HOLD)

            if (isVolUpPressed) {
                isVolUpUsedInChord = true
                isVolDownUsedInChord = true
                volUpHoldJob?.cancel(); volUpHoldJob = null
                volDownHoldJob?.cancel(); volDownHoldJob = null
                stopAutoRepeat()
                return true
            }

            isVolDownUsedInChord = false
            isSequenceFired = false
            volDownHoldJob?.cancel()

            val longPressAction = LightspeedKeyEngine.getBoundAction(context, VolumeTriggerSlot.VOL_DOWN_LONG_PRESS)
            if (longPressAction != null) {
                volDownHoldJob = engineScope.launch {
                    delay(LightspeedKeyEngine.LONG_PRESS_TIMEOUT_MS)
                    isVolDownLongPressed = true
                    LightspeedHapticEngine.heavyClick(context)
                    ActionDispatcher.dispatch(longPressAction, context)
                    startAutoRepeat(context, longPressAction)
                }
            }
            return cleanSuppression
        } else if (action == KeyEvent.ACTION_UP) {
            isVolDownPressed = false
            volDownHoldJob?.cancel(); volDownHoldJob = null
            seqTapHoldJob?.cancel(); seqTapHoldJob = null
            stopAutoRepeat()
            accessibilityShortcutJob?.cancel(); accessibilityShortcutJob = null

            if (isAccessibilityBypassed) { isAccessibilityBypassed = false; return false }
            if (isSeqTapHoldFired) { isSeqTapHoldFired = false; return true }
            if (isSequenceFired) { isSequenceFired = false; return true }
            if (isVolDownLongPressed) { isVolDownLongPressed = false; return true }

            if (isVolDownUsedInChord) {
                val chordAction = LightspeedKeyEngine.getBoundAction(context, VolumeTriggerSlot.CHORD_UP_HOLD_DOWN_TAP)
                if (chordAction != null) {
                    LightspeedHapticEngine.click(context)
                    ActionDispatcher.dispatch(chordAction, context)
                }
                isVolDownUsedInChord = false
                return true
            }

            val diffUp = now - lastVolUpReleaseTime
            if (checkSingleTapSequence(context, diffUp, isVolUpPressed, VolumeTriggerSlot.SEQ_UP_THEN_DOWN)) return true

            lastVolDownReleaseTime = now
            return performVolumeStep(context, isUp = false, isCustomHud = isCustomHud, cleanSuppression = cleanSuppression)
        }
        return false
    }

    fun reset() {
        engineScope.cancel()
        engineScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
        volUpHoldJob?.cancel(); volUpHoldJob = null
        volDownHoldJob?.cancel(); volDownHoldJob = null
        seqTapHoldJob?.cancel(); seqTapHoldJob = null
        autoRepeatJob?.cancel(); autoRepeatJob = null
        accessibilityShortcutJob?.cancel(); accessibilityShortcutJob = null

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
    }
}
