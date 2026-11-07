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

    val actionValue = if (isFromLeft) {
        val isLeftUnified = prefs.getBoolean(LightspeedPreferences.KEY_SIDEBAR_LEFT_LINK_FLANK_ACTIONS, true)
        val leftGesture = when (gesture.name) {
            "SWIPE_LEFT" -> "SWIPE_RIGHT"
            "SWIPE_LEFT_UP" -> "SWIPE_RIGHT_UP"
            "SWIPE_LEFT_DOWN" -> "SWIPE_RIGHT_DOWN"
            "SWIPE_LEFT_BACK" -> "SWIPE_RIGHT_BACK"
            "SWIPE_UP_LEFT" -> "SWIPE_UP_RIGHT"
            "SWIPE_DOWN_LEFT" -> "SWIPE_DOWN_RIGHT"
            else -> gesture.name
        }
        val isLeftGestureUnified = LightspeedPreferences.isGestureUnified(context, isLeft = true, leftGesture)
        val leftZone = if (isLeftUnified && isLeftGestureUnified) "LEFT_UNIFIED" else "LEFT_$zoneName"
        val explicitAction = prefs.getString("pref_macro_action_${leftZone}_$leftGesture", null)
        if (!explicitAction.isNullOrEmpty()) {
            explicitAction
        } else {
            // Mirror right deflector or fallback to intuitive defaults
            val isRightGestureUnified = LightspeedPreferences.isGestureUnified(context, isLeft = false, gesture.name)
            val rightZone = if (isFlankUnified && isRightGestureUnified) "UNIFIED" else zoneName
            val rightAction = prefs.getString("pref_macro_action_${rightZone}_${gesture.name}", null)
            if (!rightAction.isNullOrEmpty()) {
                rightAction
            } else {
                getDefaultMacroAction(leftGesture, isLeft = true)
            }
        }
    } else {
        val isGestureUnified = LightspeedPreferences.isGestureUnified(context, isLeft = false, gesture.name)
        val dynamicZone = if (isFlankUnified && isGestureUnified) "UNIFIED" else zoneName
        val actionKey = "pref_macro_action_${dynamicZone}_${gesture.name}"
        val explicit = prefs.getString(actionKey, null)
        if (!explicit.isNullOrEmpty()) {
            explicit
        } else {
            getDefaultMacroAction(gesture.name, isLeft = false)
        }
    }

    triggerDeflectorHaptic(isClick = true)

    // Disengage overlay window so incoming system window (Recents/Home/App) gets immediate focus
    
    ActionDispatcher.execute(service ?: context, actionValue)
    uiHandler.postDelayed({ dismissOverlay() }, 60L)
}
