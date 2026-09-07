package com.sbf.lightspeed.system

import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build
import android.service.quicksettings.TileService
import android.widget.Toast
import com.sbf.lightspeed.LightspeedAccessibilityService

/**
 * Universal Broadcast Receiver for external automation tools (Tasker, MacroDroid, Termux, ADB)
 * and internal quick actions (Notification buttons, Home Shortcuts).
 */
class LightspeedAutomationReceiver : BroadcastReceiver() {

    companion object {
        const val ACTION_TOGGLE_FLIGHT_MODE = "com.sbf.lightspeed.action.TOGGLE_FLIGHT_MODE"
        const val ACTION_SET_FLIGHT_MODE = "com.sbf.lightspeed.action.SET_FLIGHT_MODE"
        const val ACTION_TOGGLE_DEFLECTORS = "com.sbf.lightspeed.action.TOGGLE_DEFLECTORS"
        const val ACTION_TOGGLE_LEFT_DEFLECTOR = "com.sbf.lightspeed.action.TOGGLE_LEFT_DEFLECTOR"
        const val ACTION_TOGGLE_RIGHT_DEFLECTOR = "com.sbf.lightspeed.action.TOGGLE_RIGHT_DEFLECTOR"
        const val ACTION_SET_DEFLECTOR = "com.sbf.lightspeed.action.SET_DEFLECTOR"
        const val ACTION_GRAVITY_OVERRIDE_360 = "com.sbf.lightspeed.action.GRAVITY_OVERRIDE_360"
        const val ACTION_TOGGLE_AUTO_ROTATE = "com.sbf.lightspeed.action.TOGGLE_AUTO_ROTATE"
        const val ACTION_RESET_GRAVITY = "com.sbf.lightspeed.action.RESET_GRAVITY"

        const val EXTRA_ARMED = "armed"
        const val EXTRA_FLANK = "flank" // "left", "right", "both"
        const val EXTRA_STATE = "state" // boolean
    }

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return
        var feedbackMessage: String? = null

        when (action) {
            ACTION_GRAVITY_OVERRIDE_360 -> {
                LightspeedOrientationManager.overrideTransient360(context)
                return
            }
            ACTION_TOGGLE_AUTO_ROTATE -> {
                LightspeedOrientationManager.toggleNativeAutoRotate(context)
                return
            }
            ACTION_RESET_GRAVITY -> {
                LightspeedOrientationManager.resetGravity(context)
                return
            }
            ACTION_TOGGLE_FLIGHT_MODE -> {
                val current = LightspeedPreferences.isMasterFlightArmed(context)
                val newArmed = !current
                LightspeedPreferences.setMasterFlightArmed(context, newArmed)
                feedbackMessage = if (newArmed) "// FLIGHT DECK // Armed ⚡" else "// FLIGHT DECK // Standby ⏸"
            }

            ACTION_SET_FLIGHT_MODE -> {
                val armed = when {
                    intent.hasExtra(EXTRA_ARMED) -> intent.getBooleanExtra(EXTRA_ARMED, true)
                    intent.getStringExtra("mode")?.equals("standby", ignoreCase = true) == true -> false
                    intent.getStringExtra("mode")?.equals("armed", ignoreCase = true) == true -> true
                    else -> true
                }
                LightspeedPreferences.setMasterFlightArmed(context, armed)
                feedbackMessage = if (armed) "// FLIGHT DECK // Armed ⚡" else "// FLIGHT DECK // Standby ⏸"
            }

            ACTION_TOGGLE_DEFLECTORS -> {
                val left = LightspeedPreferences.isLeftDeflectorEnabled(context)
                val right = LightspeedPreferences.isRightDeflectorEnabled(context)
                val newState = !(left || right)
                LightspeedPreferences.setLeftDeflectorEnabled(context, newState)
                LightspeedPreferences.setRightDeflectorEnabled(context, newState)
                feedbackMessage = if (newState) "// DEFLECTORS // Flanks Activated" else "// DEFLECTORS // Flanks Muted"
            }

            ACTION_TOGGLE_LEFT_DEFLECTOR -> {
                val newLeft = !LightspeedPreferences.isLeftDeflectorEnabled(context)
                LightspeedPreferences.setLeftDeflectorEnabled(context, newLeft)
                feedbackMessage = if (newLeft) "// DEFLECTOR // Left Flank ON" else "// DEFLECTOR // Left Flank OFF"
            }

            ACTION_TOGGLE_RIGHT_DEFLECTOR -> {
                val newRight = !LightspeedPreferences.isRightDeflectorEnabled(context)
                LightspeedPreferences.setRightDeflectorEnabled(context, newRight)
                feedbackMessage = if (newRight) "// DEFLECTOR // Right Flank ON" else "// DEFLECTOR // Right Flank OFF"
            }

            ACTION_SET_DEFLECTOR -> {
                val flank = intent.getStringExtra(EXTRA_FLANK)?.lowercase() ?: "both"
                val state = intent.getBooleanExtra(EXTRA_STATE, true)
                when (flank) {
                    "left" -> {
                        LightspeedPreferences.setLeftDeflectorEnabled(context, state)
                        feedbackMessage = if (state) "// DEFLECTOR // Left Flank ON" else "// DEFLECTOR // Left Flank OFF"
                    }
                    "right" -> {
                        LightspeedPreferences.setRightDeflectorEnabled(context, state)
                        feedbackMessage = if (state) "// DEFLECTOR // Right Flank ON" else "// DEFLECTOR // Right Flank OFF"
                    }
                    else -> {
                        LightspeedPreferences.setLeftDeflectorEnabled(context, state)
                        LightspeedPreferences.setRightDeflectorEnabled(context, state)
                        feedbackMessage = if (state) "// DEFLECTORS // Both Flanks ON" else "// DEFLECTORS // Both Flanks OFF"
                    }
                }
            }
        }

        if (feedbackMessage != null) {
            LightspeedHapticEngine.tick(context)
            Toast.makeText(context, feedbackMessage, Toast.LENGTH_SHORT).show()

            // Hot reload overlay visibility
            LightspeedAccessibilityService.instance?.updateOverlaysVisibility()

            // Update persistent flight notification
            LightspeedFlightNotificationManager.update(context)

            // Request QS Tile update
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                try {
                    TileService.requestListeningState(
                        context,
                        ComponentName(context, "com.sbf.lightspeed.settings.SidebarTileService")
                    )
                    TileService.requestListeningState(
                        context,
                        ComponentName(context, "com.sbf.lightspeed.settings.DeflectorTileService")
                    )
                } catch (_: Exception) {}
            }
        }
    }
}
