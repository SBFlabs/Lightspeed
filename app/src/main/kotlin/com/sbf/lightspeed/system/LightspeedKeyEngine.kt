package com.sbf.lightspeed.system

import android.content.Context
import android.view.KeyEvent

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

    fun isEnabled(context: Context): Boolean {
        val prefs = context.defaultPrefs()
        return prefs.getBoolean(LightspeedPreferences.KEY_VOL_GESTURES_ENABLED, false)
    }

    fun getSuppressionProfile(context: Context): String {
        val prefs = context.defaultPrefs()
        val profile = prefs.getString(LightspeedPreferences.KEY_VOLUME_SUPPRESSION_PROFILE, null)
        if (!profile.isNullOrEmpty()) return profile
        return LightspeedPreferences.DEFAULT_VOLUME_SUPPRESSION_PROFILE
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

    /**
     * Intercepts and processes hardware volume and power key events routed from LightspeedAccessibilityService.
     */
    fun onKeyEvent(context: Context, event: KeyEvent): Boolean {
        val keyCode = event.keyCode

        // MODE C: POWER BUTTON ENGINE (Delegated)
        if (keyCode == KeyEvent.KEYCODE_POWER) {
            val now = android.os.SystemClock.uptimeMillis()
            return LightspeedPowerKeyEngine.handlePowerKeyEvent(context, event, now)
        }

        if (keyCode != KeyEvent.KEYCODE_VOLUME_UP && keyCode != KeyEvent.KEYCODE_VOLUME_DOWN) {
            return false
        }

        val prefs = context.defaultPrefs()
        val isCustomHud = prefs.getBoolean(LightspeedPreferences.KEY_CUSTOM_VOLUME_HUD_ENABLED, false)

        if (!isEnabled(context) && !isHudNavActive && !isCustomHud) {
            return false
        }

        // MODE A: HARDWARE GEAR SET HUD NAVIGATION MODE (Delegated)
        if (isHudNavActive) {
            return LightspeedKeyHudNav.handleHudNavKeyEvent(context, event)
        }

        // MODE B: STANDARD HARDWARE GESTURES & CHORDS
        return LightspeedVolumeKeyEngine.handleVolumeKeyEvent(context, event)
    }

    /**
     * Resets all internal state machines, release timers, and pending coroutine hold jobs.
     */
    fun reset() {
        LightspeedVolumeKeyEngine.reset()
        LightspeedKeyHudNav.reset()
        LightspeedPowerKeyEngine.resetPowerState()
    }
}
