package com.sbf.lightspeed

import com.sbf.lightspeed.system.ActionDispatcher
import com.sbf.lightspeed.system.LightspeedPreferences
import com.sbf.lightspeed.system.LightspeedPreferencesDefaults

private fun getDefaultMacroAction(gestureName: String, isLeft: Boolean): String {
    val prefix = if (isLeft) "pref_macro_action_LEFT_UNIFIED_" else "pref_macro_action_UNIFIED_"
    val defaultVal = LightspeedPreferencesDefaults.getDefaultActionForGestureKey(prefix + gestureName)
    if (defaultVal != "none") return defaultVal
    return when (gestureName) {
        "SWIPE_LEFT_BACK", "SWIPE_RIGHT_BACK" -> "system:previous_app"
        else -> "none"
    }
}

internal fun LightspeedCruiseOverlay.executeMacroAction(zone: TouchZone, gesture: MacroGesture) {
    val zoneName = if (zone == TouchZone.TOP_EDGE) "TOP" else "BOTTOM"
    val prefs = prefs()
    val isFlankUnified = prefs.getBoolean(LightspeedPreferences.KEY_SIDEBAR_RIGHT_LINK_FLANK_ACTIONS, true)
    val gestMode = prefs.getString("pref_symmetry_gesture_mode", "independent") ?: "independent"
    val isFromLeft = when (gestMode) {
        "left" -> true
        "right" -> false
        else -> isOpenedFromLeftFlank
    }

    val gestureKey = if (gesture == MacroGesture.NONE) "TAP_HOLD" else gesture.name

    val actionValue = if (isFromLeft) {
        val isLeftUnified = prefs.getBoolean(LightspeedPreferences.KEY_SIDEBAR_LEFT_LINK_FLANK_ACTIONS, true)
        val leftGesture = when (gestureKey) {
            "SWIPE_LEFT" -> "SWIPE_RIGHT"
            "SWIPE_LEFT_UP" -> "SWIPE_RIGHT_UP"
            "SWIPE_LEFT_DOWN" -> "SWIPE_RIGHT_DOWN"
            "SWIPE_LEFT_BACK" -> "SWIPE_RIGHT_BACK"
            "SWIPE_UP_LEFT" -> "SWIPE_UP_RIGHT"
            "SWIPE_DOWN_LEFT" -> "SWIPE_DOWN_RIGHT"
            "SWIPE_LEFT_HOLD" -> "SWIPE_RIGHT_HOLD"
            "SWIPE_LEFT_UP_HOLD" -> "SWIPE_RIGHT_UP_HOLD"
            "SWIPE_LEFT_DOWN_HOLD" -> "SWIPE_RIGHT_DOWN_HOLD"
            "SWIPE_LEFT_BACK_HOLD" -> "SWIPE_RIGHT_BACK_HOLD"
            "SWIPE_UP_LEFT_HOLD" -> "SWIPE_UP_RIGHT_HOLD"
            "SWIPE_DOWN_LEFT_HOLD" -> "SWIPE_DOWN_RIGHT_HOLD"
            else -> gestureKey
        }
        val isLeftGestureUnified = LightspeedPreferences.isGestureUnified(context, isLeft = true, leftGesture)
        val leftZone = if (isLeftUnified && isLeftGestureUnified) "LEFT_UNIFIED" else "LEFT_$zoneName"
        val explicitAction = prefs.getString("pref_macro_action_${leftZone}_$leftGesture", null)
        if (!explicitAction.isNullOrEmpty() && explicitAction != "none") {
            explicitAction
        } else {
            // Mirror right deflector or fallback to intuitive defaults
            val isRightGestureUnified = LightspeedPreferences.isGestureUnified(context, isLeft = false, gestureKey)
            val rightZone = if (isFlankUnified && isRightGestureUnified) "UNIFIED" else zoneName
            val rightAction = prefs.getString("pref_macro_action_${rightZone}_$gestureKey", null)
            if (!rightAction.isNullOrEmpty() && rightAction != "none") {
                rightAction
            } else {
                getDefaultMacroAction(leftGesture, isLeft = true)
            }
        }
    } else {
        val isGestureUnified = LightspeedPreferences.isGestureUnified(context, isLeft = false, gestureKey)
        val dynamicZone = if (isFlankUnified && isGestureUnified) "UNIFIED" else zoneName
        val actionKey = "pref_macro_action_${dynamicZone}_$gestureKey"
        val explicit = prefs.getString(actionKey, null)
        if (!explicit.isNullOrEmpty() && explicit != "none") {
            explicit
        } else {
            getDefaultMacroAction(gestureKey, isLeft = false)
        }
    }

    triggerDeflectorHaptic(isClick = true)

    // Disengage overlay window so incoming system window (Recents/Home/App) gets immediate focus
    
    ActionDispatcher.execute(service ?: context, actionValue)
    uiHandler.postDelayed({ dismissOverlay() }, 60L)
}
