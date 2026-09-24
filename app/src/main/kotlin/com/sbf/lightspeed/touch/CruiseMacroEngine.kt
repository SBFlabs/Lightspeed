package com.sbf.lightspeed

import android.util.Log
import com.sbf.lightspeed.system.ActionDispatcher
import com.sbf.lightspeed.system.LightspeedPreferences

private fun getDefaultMacroAction(gestureName: String, isLeft: Boolean): String {
    val prefix = if (isLeft) "pref_macro_action_LEFT_UNIFIED_" else "pref_macro_action_UNIFIED_"
    val defaultVal = com.sbf.lightspeed.settings.LightspeedActionRegistry.getDefaultActionForGestureKey(prefix + gestureName)
    if (defaultVal != "none") return defaultVal
    return when (gestureName) {
        "SWIPE_LEFT", "SWIPE_RIGHT" -> "system:back"
        "SWIPE_UP" -> if (isLeft) "system:gravity_override_360" else "system:recents"
        "SWIPE_DOWN" -> if (isLeft) "system:auto_rotate_toggle" else "system:home"
        "SWIPE_LEFT_BACK", "SWIPE_RIGHT_BACK" -> "system:previous_app"
        "TAP" -> "action_enter_gearset_nav"
        "SWIPE_LEFT_HOLD", "SWIPE_RIGHT_HOLD" -> "system:close_app"
        "TAP_HOLD" -> if (isLeft) "system:volume" else "system:brightness"
        else -> "none"
    }
}

internal fun LightspeedCruiseOverlay.executeMacroAction(zone: TouchZone, gesture: MacroGesture) {
    val zoneName = if (zone == TouchZone.TOP_EDGE) "TOP" else "BOTTOM"
    val prefs = prefs()
    val isFlankUnified = prefs.getBoolean(LightspeedPreferences.KEY_SIDEBAR_RIGHT_LINK_FLANK_ACTIONS, true)
    val gestMode = prefs.getString("pref_symmetry_gesture_mode", "independent") ?: "independent"
    val isFromLeft = isOpenedFromLeftFlank || gestMode == "left"

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
        if (!explicitAction.isNullOrEmpty() && explicitAction != "none") {
            explicitAction
        } else {
            // Mirror right deflector or fallback to intuitive defaults
            val isRightGestureUnified = LightspeedPreferences.isGestureUnified(context, isLeft = false, gesture.name)
            val rightZone = if (isFlankUnified && isRightGestureUnified) "UNIFIED" else zoneName
            val rightAction = prefs.getString("pref_macro_action_${rightZone}_${gesture.name}", null)
            if (!rightAction.isNullOrEmpty() && rightAction != "none") {
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
        if (!explicit.isNullOrEmpty() && explicit != "none") {
            explicit
        } else {
            getDefaultMacroAction(gesture.name, isLeft = false)
        }
    }

    Log.d("GestureEngine", "Target Vector: [$zoneName] -> Action Value: $actionValue")
    triggerHardwareHaptic(35, 160)

    // Disengage overlay window so incoming system window (Recents/Home/App) gets immediate focus
    dismissOverlay()

    ActionDispatcher.execute(service ?: context, actionValue)
}
