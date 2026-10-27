package com.sbf.lightspeed.system

import android.content.Context
import android.provider.Settings
import android.util.Log
import com.sbf.lightspeed.LightspeedAccessibilityService
import com.sbf.lightspeed.system.display.LightspeedConsciousAirlockOverlay
import com.sbf.lightspeed.system.display.LightspeedDisplayWellnessResetEngine
import com.sbf.lightspeed.system.display.LightspeedEyeCareEngine
import com.sbf.lightspeed.system.display.LightspeedSmartPixelsOverlay
import com.sbf.lightspeed.system.sonic.SonicAppVolumeEngine
import com.sbf.lightspeed.system.sonic.SonicChannelRouter
import com.sbf.lightspeed.system.sonic.SonicVoiceIsolationEngine

/**
 * Single Master Reactor Cutoff & 1-Tap Cold Shutdown / Resurrection Orchestrator
 *
 * Provides:
 * 1. Cold Shutdown: Full cessation of all overlays, synthetic gravity locks, eye care/dimming filters,
 *    Smart Pixels mesh, conscious delay airlocks, and per-app audio modifications, followed by clean
 *    service dormancy.
 * 2. 1-Tap Resurrection: Instantly re-arms Lightspeed accessibility service via WRITE_SECURE_SETTINGS
 *    or Shizuku, restoring all avionics.
 */
object LightspeedMasterCutoff {

    private const val TAG = "MasterCutoff"

    fun isSystemArmed(context: Context): Boolean {
        return LightspeedPreferences.isMasterFlightArmed(context)
    }

    fun setSystemArmed(context: Context, armed: Boolean) {
        LightspeedPreferences.setMasterFlightArmed(context, armed)
        val prefs = context.defaultPrefs()

        if (!armed) {
            // Cold Shutdown Sequence
            Log.i(TAG, "Initiating Lightspeed Master Reactor Cold Shutdown...")

            // 0. Mark intentionally stopped so watchdogs remain dormant during testing & banking cloaking
            prefs.edit().putBoolean("pref_service_intentionally_stopped", true).apply()

            // 1. Release synthetic gravity & forced orientation
            try {
                LightspeedOrientationEngine.releaseForcedOrientation(context)
            } catch (e: Exception) { logSwallowed(TAG, "cutoff:gravity", e) }

            // 2. Disable Eye Care overlay, Extra Dim & SurfaceFlinger matrix (native Android Night Light is untouched)
            try {
                LightspeedEyeCareEngine.applyExtraDim(context, false, 0)
                LightspeedEyeCareEngine.applyEyeCare(context, false, 0)
                if (ElevatedTaskCloser.isShizukuActive) {
                    ElevatedTaskCloser.execShizuku("service call SurfaceFlinger 1015 i32 0")
                }
            } catch (e: Exception) { logSwallowed(TAG, "cutoff:eyecare", e) }

            // 3. Clear Video Override overlay
            try {
                LightspeedDisplayWellnessResetEngine.clearVideoOverride(context)
            } catch (e: Exception) { logSwallowed(TAG, "cutoff:video_override", e) }

            // 4. Restore Native Android Edge Back Gestures to 1.0
            try {
                if (ElevatedTaskCloser.isShizukuActive) {
                    ElevatedTaskCloser.execShizuku("settings put secure back_gesture_inset_scale_left 1.0 && settings put secure back_gesture_inset_scale_right 1.0")
                } else {
                    Settings.Secure.putFloat(context.contentResolver, "back_gesture_inset_scale_left", 1.0f)
                    Settings.Secure.putFloat(context.contentResolver, "back_gesture_inset_scale_right", 1.0f)
                }
            } catch (e: Exception) { logSwallowed(TAG, "cutoff:back_gesture", e) }

            // 5. Smart Pixels
            try {
                LightspeedSmartPixelsOverlay.setEnabled(context, false)
            } catch (e: Exception) { logSwallowed(TAG, "cutoff:smartpixels", e) }

            // 6. Conscious Airlock, Space Pods & Bot Overlays
            try {
                LightspeedConsciousAirlockOverlay.dismiss()
                LightspeedSpacePodOverlay.hide()
                com.sbf.lightspeed.system.bot.LightspeedBotTrackpadOverlay.hide()
                com.sbf.lightspeed.system.bot.LightspeedBotRecorderOverlay.hide()
                com.sbf.lightspeed.system.bot.LightspeedBotEngine.stopContinuousLoop()
            } catch (e: Exception) { logSwallowed(TAG, "cutoff:airlock", e) }

            // 7. Sonic & Vista Decks
            try {
                SonicAppVolumeEngine.resetAllAppVolumesToUnity()
                SonicVoiceIsolationEngine.teardown(context)
                SonicChannelRouter.unregisterPolicy()
                SonicDeckManager.dismiss()
                VistaDeckManager.dismiss()
            } catch (e: Exception) { logSwallowed(TAG, "cutoff:sonic", e) }

            // 8. Dismiss all active overlays and cloak accessibility services (Lightspeed + user cloaked services)
            try {
                val service = LightspeedAccessibilityService.instance
                service?.updateOverlaysVisibility()

                val cloakedSet = LightspeedPreferences.getCloakedAccessibilityServices(context)
                val targetComponent = "${context.packageName}/${LightspeedAccessibilityService::class.java.name}"
                val servicesToDisable = cloakedSet + targetComponent

                val cr = context.contentResolver
                val currentServices = Settings.Secure.getString(cr, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES).orEmpty()
                val currentList = currentServices.split(":").map { it.trim() }.filter { it.isNotEmpty() }

                // Backup which cloaked services were actually active so we only resurrect those
                val activeCloakedToBackup = currentList.filter { cloakedSet.contains(it) }.toSet()
                prefs.edit().putStringSet(LightspeedPreferences.KEY_ACTIVE_CLOAKED_SERVICES_BACKUP, activeCloakedToBackup).apply()

                val remainingServices = currentList.filter { !servicesToDisable.contains(it) && !it.contains(context.packageName) }
                    .joinToString(":")

                var disabled = false
                try {
                    Settings.Secure.putString(cr, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES, remainingServices)
                    disabled = true
                } catch (_: Exception) {}

                if (!disabled && ElevatedTaskCloser.isShizukuActive) {
                    ElevatedTaskCloser.execShizuku("settings put secure enabled_accessibility_services '$remainingServices'")
                    disabled = true
                }

                if (!disabled) {
                    service?.disableSelf()
                }
            } catch (e: Exception) { logSwallowed(TAG, "cutoff:a11y", e) }

        } else {
            // One-Tap Resurrection Sequence
            Log.i(TAG, "Resurrecting Lightspeed Core...")

            // 0. Reset intentionally stopped flag
            prefs.edit().putBoolean("pref_service_intentionally_stopped", false).apply()

            // 1. Restore Deflector Edge Back Gesture Suppression Scale
            try {
                val savedScale = prefs.getFloat("sys_override_edge_gesture", 0.0f)
                if (ElevatedTaskCloser.isShizukuActive) {
                    ElevatedTaskCloser.execShizuku("settings put secure back_gesture_inset_scale_left $savedScale && settings put secure back_gesture_inset_scale_right $savedScale")
                } else {
                    Settings.Secure.putFloat(context.contentResolver, "back_gesture_inset_scale_left", savedScale)
                    Settings.Secure.putFloat(context.contentResolver, "back_gesture_inset_scale_right", savedScale)
                }
            } catch (e: Exception) { logSwallowed(TAG, "resurrect:back_gesture", e) }

            // 2. Re-enable Lightspeed + previously active cloaked services
            val targetComponent = "${context.packageName}/${LightspeedAccessibilityService::class.java.name}"
            val activeCloakedBackup = prefs.getStringSet(LightspeedPreferences.KEY_ACTIVE_CLOAKED_SERVICES_BACKUP, emptySet()) ?: emptySet()
            val servicesToRestore = (activeCloakedBackup + targetComponent)

            val cr = context.contentResolver
            val currentServices = Settings.Secure.getString(cr, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES).orEmpty()
            val currentList = currentServices.split(":").map { it.trim() }.filter { it.isNotEmpty() }.toMutableSet()
            currentList.addAll(servicesToRestore)
            val newServices = currentList.joinToString(":")

            var reEnabled = false
            try {
                Settings.Secure.putString(cr, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES, newServices)
                Settings.Secure.putInt(cr, Settings.Secure.ACCESSIBILITY_ENABLED, 1)
                reEnabled = true
            } catch (_: Exception) {}

            if (!reEnabled && ElevatedTaskCloser.isShizukuActive) {
                ElevatedTaskCloser.execShizuku("settings put secure enabled_accessibility_services '$newServices'")
                ElevatedTaskCloser.execShizuku("settings put secure accessibility_enabled 1")
            }

            // Restore active states
            LightspeedAccessibilityService.instance?.updateOverlaysVisibility()
            LightspeedFlightNotificationManager.update(context)
        }
    }
}
