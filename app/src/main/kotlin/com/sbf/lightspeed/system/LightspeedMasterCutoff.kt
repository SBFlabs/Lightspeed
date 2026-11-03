package com.sbf.lightspeed.system

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper
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
 * 1. Cold Shutdown: Configurable checklist cessation of overlays, synthetic gravity locks,
 *    Extra Dim overlay (default ON), optional Eye Care filter (default OFF), Smart Pixels mesh,
 *    conscious delay airlocks, per-app audio modifications, Developer Options / USB Debugging,
 *    and Shizuku-Next / Shevery server broadcasts, followed by clean service dormancy.
 * 2. 1-Tap Resurrection: Instantly re-arms Lightspeed accessibility service via WRITE_SECURE_SETTINGS
 *    or Shizuku, restoring all stashed avionics.
 */
object LightspeedMasterCutoff {

    private const val TAG = "MasterCutoff"
    private const val KEY_STASHED_EXTRA_DIM = "pref_cloak_stashed_extra_dim"
    private const val KEY_STASHED_EXTRA_DIM_LEVEL = "pref_cloak_stashed_extra_dim_level"
    private const val KEY_STASHED_EYE_CARE = "pref_cloak_stashed_eye_care"
    private const val KEY_STASHED_EYE_CARE_TEMP = "pref_cloak_stashed_eye_care_temp"
    private const val KEY_STASHED_SMART_PIXELS = "pref_cloak_stashed_smart_pixels"

    fun isSystemArmed(context: Context): Boolean {
        return LightspeedPreferences.isMasterFlightArmed(context)
    }

    private fun dispatchShizukuForkBroadcasts(
        context: Context,
        stopServer: Boolean,
        forkMode: String,
        toggleWatchdog: Boolean,
        authToken: String
    ) {
        val includeShizukuNext = forkMode == "auto" || forkMode == "shizuku_next"
        val includeShevery = forkMode == "auto" || forkMode == "shevery"
        val hasValidToken = authToken.isNotEmpty() && ShellArgGuard.isToken(authToken)

        // 1. Shizuku-Next (moe.shizuku.privileged.api)
        if (includeShizukuNext) {
            if (toggleWatchdog) {
                val wdAction = if (stopServer) {
                    "moe.shizuku.privileged.api.WATCHDOG_OFF"
                } else {
                    "moe.shizuku.privileged.api.WATCHDOG_ON"
                }
                try {
                    context.sendBroadcast(Intent(wdAction).setPackage("moe.shizuku.privileged.api"))
                } catch (_: Exception) {}
                if (ElevatedTaskCloser.isShizukuActive) {
                    ElevatedTaskCloser.execShizukuArgv(
                        "/system/bin/am", "broadcast",
                        "-a", wdAction,
                        "-p", "moe.shizuku.privileged.api"
                    )
                }
            }

            val nextAction = if (stopServer) "moe.shizuku.privileged.api.STOP" else "moe.shizuku.privileged.api.START"
            val nextIntent = Intent(nextAction).setPackage("moe.shizuku.privileged.api").apply {
                if (hasValidToken) {
                    putExtra("auth", authToken)
                    putExtra("token", authToken)
                }
            }
            try { context.sendBroadcast(nextIntent) } catch (_: Exception) {}
            if (ElevatedTaskCloser.isShizukuActive) {
                if (hasValidToken) {
                    ElevatedTaskCloser.execShizukuArgv(
                        "/system/bin/am", "broadcast",
                        "-a", nextAction,
                        "-p", "moe.shizuku.privileged.api",
                        "--es", "auth", authToken
                    )
                } else {
                    ElevatedTaskCloser.execShizukuArgv(
                        "/system/bin/am", "broadcast",
                        "-a", nextAction,
                        "-p", "moe.shizuku.privileged.api"
                    )
                }
            }
        }

        // 2. Shevery (com.hamondev.shevery)
        if (includeShevery) {
            val sheveryAction = if (stopServer) {
                "com.hamondev.shevery.action.STOP_SERVER"
            } else {
                "com.hamondev.shevery.action.START_SERVER"
            }
            val sheveryReceiver = ComponentName(
                "com.hamondev.shevery",
                "com.hamondev.shevery.tasker.PluginReceiver"
            )
            val sheveryIntent = Intent(sheveryAction).apply {
                setPackage("com.hamondev.shevery")
                component = sheveryReceiver
                if (hasValidToken) {
                    putExtra("auth", authToken)
                }
            }
            try { context.sendBroadcast(sheveryIntent) } catch (_: Exception) {}
            if (ElevatedTaskCloser.isShizukuActive) {
                if (hasValidToken) {
                    ElevatedTaskCloser.execShizukuArgv(
                        "/system/bin/am", "broadcast",
                        "-a", sheveryAction,
                        "-p", "com.hamondev.shevery",
                        "-n", "com.hamondev.shevery/com.hamondev.shevery.tasker.PluginReceiver",
                        "-e", "auth", authToken
                    )
                } else {
                    ElevatedTaskCloser.execShizukuArgv(
                        "/system/bin/am", "broadcast",
                        "-a", sheveryAction,
                        "-p", "com.hamondev.shevery",
                        "-n", "com.hamondev.shevery/com.hamondev.shevery.tasker.PluginReceiver"
                    )
                }
            }
        }
    }

    fun setSystemArmed(context: Context, armed: Boolean) {
        LightspeedPreferences.setMasterFlightArmed(context, armed)
        val prefs = context.defaultPrefs()

        val disableExtraDim = prefs.getBoolean(LightspeedPreferences.KEY_CLOAK_DISABLE_EXTRA_DIM, true)
        val disableEyeCare = prefs.getBoolean(LightspeedPreferences.KEY_CLOAK_DISABLE_EYE_CARE, false)
        val disableSmartPixels = prefs.getBoolean(LightspeedPreferences.KEY_CLOAK_DISABLE_SMART_PIXELS, true)
        val suspendSonicDeck = prefs.getBoolean(LightspeedPreferences.KEY_CLOAK_SUSPEND_SONIC_DECK, true)
        val stopShizuku = prefs.getBoolean(LightspeedPreferences.KEY_CLOAK_STOP_SHIZUKU, false)
        val shizukuFork = prefs.getString(LightspeedPreferences.KEY_CLOAK_SHIZUKU_FORK, "auto") ?: "auto"
        val toggleWatchdog = prefs.getBoolean(LightspeedPreferences.KEY_CLOAK_SHIZUKU_WATCHDOG_TOGGLE, true)
        val shizukuToken = prefs.getString(LightspeedPreferences.KEY_CLOAK_SHIZUKU_TOGGLE_TOKEN, "")?.trim().orEmpty()

        if (!armed) {
            // Cold Shutdown Sequence
            Log.i(TAG, "Initiating Lightspeed Master Reactor Cold Shutdown...")

            // 0. Mark intentionally stopped so watchdogs remain dormant during testing & banking cloaking
            prefs.edit().putBoolean("pref_service_intentionally_stopped", true).apply()

            // 1. Release synthetic gravity & forced orientation
            try {
                LightspeedOrientationEngine.releaseForcedOrientation(context)
            } catch (e: Exception) { logSwallowed(TAG, "cutoff:gravity", e) }

            // 2a. Extra Dim Overlay (Lightspeed feature — ON by default in Cloak checklist)
            try {
                if (disableExtraDim) {
                    val wasExtraDim = prefs.getBoolean(LightspeedPreferences.KEY_EXTRA_DIM_ENABLED, false)
                    val curLevel = prefs.getInt(LightspeedPreferences.KEY_EXTRA_DIM_LEVEL, 40)
                    prefs.edit()
                        .putBoolean(KEY_STASHED_EXTRA_DIM, wasExtraDim)
                        .putInt(KEY_STASHED_EXTRA_DIM_LEVEL, curLevel)
                        .apply()
                    if (wasExtraDim) {
                        LightspeedEyeCareEngine.applyExtraDim(context, false, curLevel)
                    }
                }
            } catch (e: Exception) { logSwallowed(TAG, "cutoff:extradim", e) }

            // 2b. Eye Care / Blue Light Filter (Native hardware matrix — OFF by default in Cloak checklist)
            try {
                if (disableEyeCare) {
                    val wasEyeCare = prefs.getBoolean(LightspeedPreferences.KEY_EYE_CARE_ENABLED, false)
                    val curTemp = prefs.getInt(LightspeedPreferences.KEY_EYE_CARE_TEMP, 3400)
                    prefs.edit()
                        .putBoolean(KEY_STASHED_EYE_CARE, wasEyeCare)
                        .putInt(KEY_STASHED_EYE_CARE_TEMP, curTemp)
                        .apply()
                    if (wasEyeCare) {
                        LightspeedEyeCareEngine.applyEyeCare(context, false, curTemp)
                    }
                    if (ElevatedTaskCloser.isShizukuActive) {
                        ElevatedTaskCloser.execShizuku("service call SurfaceFlinger 1015 i32 0")
                    }
                }
            } catch (e: Exception) { logSwallowed(TAG, "cutoff:eyecare", e) }

            // 3. Smart Pixels & Video Override (Default ON in Cloak checklist)
            try {
                if (disableSmartPixels) {
                    val wasSmartPixels = prefs.getBoolean(LightspeedPreferences.KEY_SMART_PIXELS_ENABLED, false)
                    prefs.edit().putBoolean(KEY_STASHED_SMART_PIXELS, wasSmartPixels).apply()
                    if (wasSmartPixels) {
                        LightspeedSmartPixelsOverlay.setEnabled(context, false)
                    }
                    LightspeedDisplayWellnessResetEngine.clearVideoOverride(context)
                }
            } catch (e: Exception) { logSwallowed(TAG, "cutoff:smartpixels", e) }

            // 4. Restore Native Android Edge Back Gestures to 1.0
            try {
                if (ElevatedTaskCloser.isShizukuActive) {
                    ElevatedTaskCloser.execShizuku("settings put secure back_gesture_inset_scale_left 1.0 && settings put secure back_gesture_inset_scale_right 1.0")
                } else {
                    Settings.Secure.putFloat(context.contentResolver, "back_gesture_inset_scale_left", 1.0f)
                    Settings.Secure.putFloat(context.contentResolver, "back_gesture_inset_scale_right", 1.0f)
                }
            } catch (e: Exception) { logSwallowed(TAG, "cutoff:back_gesture", e) }

            // 5. Conscious Airlock, Space Pods & Bot Overlays
            try {
                LightspeedConsciousAirlockOverlay.dismiss()
                LightspeedSpacePodOverlay.hide()
                com.sbf.lightspeed.system.bot.LightspeedBotTrackpadOverlay.hide()
                com.sbf.lightspeed.system.bot.LightspeedBotRecorderOverlay.hide()
                com.sbf.lightspeed.system.bot.LightspeedBotEngine.stopContinuousLoop()
            } catch (e: Exception) { logSwallowed(TAG, "cutoff:airlock", e) }

            // 6. Sonic & Vista Decks
            try {
                SonicDeckManager.dismiss()
                VistaDeckManager.dismiss()
                if (suspendSonicDeck) {
                    SonicDeckManager.onMasterToggle(context, false)
                    if (ElevatedTaskCloser.isShizukuActive) {
                        ElevatedTaskCloser.execShizukuArgv("/system/bin/pkill", "-f", "${context.packageName}:sonic_service")
                    }
                }
            } catch (e: Exception) { logSwallowed(TAG, "cutoff:sonic", e) }

            // 7. Dismiss all active overlays and cloak accessibility services (Lightspeed + user cloaked services)
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

                val ownPkgPrefix = "${context.packageName}/"
                val remainingServices = currentList.filter { !servicesToDisable.contains(it) && !it.startsWith(ownPkgPrefix) }
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

            // 8. Cloak Matrix: Hide Developer Options, USB Debugging, and Stop Shizuku Fork (executed last so Shizuku can assist above steps first)
            try {
                if (stopShizuku || shizukuToken.isNotEmpty()) {
                    dispatchShizukuForkBroadcasts(
                        context = context,
                        stopServer = true,
                        forkMode = shizukuFork,
                        toggleWatchdog = toggleWatchdog,
                        authToken = shizukuToken
                    )
                }
                if (prefs.getBoolean(LightspeedPreferences.KEY_CLOAK_HIDE_DEV_OPTIONS, false)) {
                    val curDev = Settings.Global.getInt(context.contentResolver, Settings.Global.DEVELOPMENT_SETTINGS_ENABLED, 0)
                    prefs.edit().putInt("pref_backup_dev_options", curDev).apply()
                    try { Settings.Global.putInt(context.contentResolver, Settings.Global.DEVELOPMENT_SETTINGS_ENABLED, 0) } catch (_: Exception) {}
                    if (ElevatedTaskCloser.isShizukuActive) {
                        ElevatedTaskCloser.execShizuku("settings put global development_settings_enabled 0")
                    }
                }
                if (prefs.getBoolean(LightspeedPreferences.KEY_CLOAK_HIDE_USB_DEBUGGING, false)) {
                    val curAdb = Settings.Global.getInt(context.contentResolver, Settings.Global.ADB_ENABLED, 0)
                    prefs.edit().putInt("pref_backup_adb_enabled", curAdb).apply()
                    try { Settings.Global.putInt(context.contentResolver, Settings.Global.ADB_ENABLED, 0) } catch (_: Exception) {}
                    if (ElevatedTaskCloser.isShizukuActive) {
                        ElevatedTaskCloser.execShizuku("settings put global adb_enabled 0")
                    }
                }
            } catch (e: Exception) { logSwallowed(TAG, "cutoff:dev_and_adb", e) }

        } else {
            // One-Tap Resurrection Sequence
            Log.i(TAG, "Resurrecting Lightspeed Core...")

            // 0. Reset intentionally stopped flag
            prefs.edit().putBoolean("pref_service_intentionally_stopped", false).apply()

            // 1. Restore Developer Options & USB Debugging FIRST (via WRITE_SECURE_SETTINGS) so Shizuku-Next / Shevery can start
            try {
                if (prefs.getBoolean(LightspeedPreferences.KEY_CLOAK_HIDE_DEV_OPTIONS, false)) {
                    val savedDev = prefs.getInt("pref_backup_dev_options", 1)
                    if (savedDev == 1) {
                        try { Settings.Global.putInt(context.contentResolver, Settings.Global.DEVELOPMENT_SETTINGS_ENABLED, 1) } catch (_: Exception) {}
                        if (ElevatedTaskCloser.isShizukuActive) {
                            ElevatedTaskCloser.execShizuku("settings put global development_settings_enabled 1")
                        }
                    }
                }
                if (prefs.getBoolean(LightspeedPreferences.KEY_CLOAK_HIDE_USB_DEBUGGING, false)) {
                    val savedAdb = prefs.getInt("pref_backup_adb_enabled", 1)
                    if (savedAdb == 1) {
                        try { Settings.Global.putInt(context.contentResolver, Settings.Global.ADB_ENABLED, 1) } catch (_: Exception) {}
                        if (ElevatedTaskCloser.isShizukuActive) {
                            ElevatedTaskCloser.execShizuku("settings put global adb_enabled 1")
                        }
                    }
                }
                if (stopShizuku || shizukuToken.isNotEmpty()) {
                    val appCtx = context.applicationContext
                    Handler(Looper.getMainLooper()).postDelayed({
                        dispatchShizukuForkBroadcasts(
                            context = appCtx,
                            stopServer = false,
                            forkMode = shizukuFork,
                            toggleWatchdog = toggleWatchdog,
                            authToken = shizukuToken
                        )
                    }, 150L)
                }
            } catch (e: Exception) { logSwallowed(TAG, "resurrect:dev_and_adb", e) }

            // 2. Restore Deflector Edge Back Gesture Suppression Scale
            try {
                val savedScale = prefs.getFloat("sys_override_edge_gesture", 0.0f)
                if (ElevatedTaskCloser.isShizukuActive) {
                    ElevatedTaskCloser.execShizuku("settings put secure back_gesture_inset_scale_left $savedScale && settings put secure back_gesture_inset_scale_right $savedScale")
                } else {
                    Settings.Secure.putFloat(context.contentResolver, "back_gesture_inset_scale_left", savedScale)
                    Settings.Secure.putFloat(context.contentResolver, "back_gesture_inset_scale_right", savedScale)
                }
            } catch (e: Exception) { logSwallowed(TAG, "resurrect:back_gesture", e) }

            // 3. Re-enable Lightspeed + previously active cloaked services
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

            // 4. Restore stashed Extra Dim, Eye Care, Smart Pixels, and Sonic Deck
            try {
                if (disableExtraDim && prefs.getBoolean(KEY_STASHED_EXTRA_DIM, false)) {
                    val savedLevel = prefs.getInt(KEY_STASHED_EXTRA_DIM_LEVEL, 40)
                    LightspeedEyeCareEngine.applyExtraDim(context, true, savedLevel)
                    prefs.edit().putBoolean(KEY_STASHED_EXTRA_DIM, false).apply()
                }
                if (disableEyeCare && prefs.getBoolean(KEY_STASHED_EYE_CARE, false)) {
                    val savedTemp = prefs.getInt(KEY_STASHED_EYE_CARE_TEMP, 3400)
                    LightspeedEyeCareEngine.applyEyeCare(context, true, savedTemp)
                    prefs.edit().putBoolean(KEY_STASHED_EYE_CARE, false).apply()
                }
                if (disableSmartPixels && prefs.getBoolean(KEY_STASHED_SMART_PIXELS, false)) {
                    LightspeedSmartPixelsOverlay.setEnabled(context, true)
                    prefs.edit().putBoolean(KEY_STASHED_SMART_PIXELS, false).apply()
                }
                if (suspendSonicDeck && prefs.getBoolean(LightspeedPreferences.KEY_SONIC_DECK_ENABLED, false)) {
                    SonicDeckManager.onMasterToggle(context, true)
                }
            } catch (e: Exception) { logSwallowed(TAG, "resurrect:wellness_sonic", e) }

            LightspeedAccessibilityService.instance?.let { svc ->
                svc.updateOverlaysVisibility()
                svc.reloadPreferences()
            }
        }
    }
}
