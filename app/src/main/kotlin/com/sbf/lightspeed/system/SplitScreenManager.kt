package com.sbf.lightspeed.system

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import android.widget.Toast

internal object SplitScreenManager {
    private val TAG = ElevatedTaskCloser.TAG

    @Volatile
    private var splitPickerListener: SharedPreferences.OnSharedPreferenceChangeListener? = null

    internal fun showSplitAppPicker(context: Context) {
        Log.w(TAG, "showSplitAppPicker() invoked")
        val prefs = context.defaultPrefs()
        prefs.edit().remove(LightspeedPreferences.KEY_SPLIT_WITH_APP_TARGET).apply()

        synchronized(this) {
            splitPickerListener?.let { oldListener ->
                Log.w(TAG, "Unregistering previous splitPickerListener")
                try {
                    prefs.unregisterOnSharedPreferenceChangeListener(oldListener)
                } catch (e: Exception) {
                    logSwallowed(TAG, "showSplitAppPicker:unregisterOld", e)
                }
                splitPickerListener = null
            }
        }

        var newListener: SharedPreferences.OnSharedPreferenceChangeListener? = null
        newListener = SharedPreferences.OnSharedPreferenceChangeListener { sharedPreferences, key ->
            if (key == LightspeedPreferences.KEY_SPLIT_WITH_APP_TARGET) {
                val token = sharedPreferences.getString(LightspeedPreferences.KEY_SPLIT_WITH_APP_TARGET, null)
                Log.w(TAG, "splitPickerListener triggered for key=$key token=$token")
                if (token.isNullOrBlank()) return@OnSharedPreferenceChangeListener

                synchronized(this@SplitScreenManager) {
                    if (splitPickerListener === newListener) {
                        try {
                            sharedPreferences.unregisterOnSharedPreferenceChangeListener(newListener)
                        } catch (e: Exception) {
                            logSwallowed(TAG, "showSplitAppPicker:unregisterTriggered", e)
                        }
                        splitPickerListener = null
                    }
                }
                sharedPreferences.edit().remove(LightspeedPreferences.KEY_SPLIT_WITH_APP_TARGET).apply()

                if (token.startsWith("system:") || token.startsWith("action_")) {
                    val resolvedComp = when (token) {
                        "system:refueling_bay" -> "com.sbf.lightspeed/com.sbf.lightspeed.LightspeedRefuelingActivity"
                        "system:perimeter_watchdog" -> "com.sbf.lightspeed/com.sbf.lightspeed.system.LightspeedPerimeterActivity"
                        "system:core_watchdog" -> "com.sbf.lightspeed/com.sbf.lightspeed.system.LightspeedCoreWatchdogActivity"
                        "system:battery_exemption" -> "com.sbf.lightspeed/com.sbf.lightspeed.system.LightspeedBatteryExemptionActivity"
                        else -> null
                    }
                    if (resolvedComp != null) {
                        Log.w(TAG, "Picked system activity token $token -> $resolvedComp, invoking splitWithApp")
                        splitWithApp(context, resolvedComp)
                        return@OnSharedPreferenceChangeListener
                    }
                    Log.w(TAG, "Picked token $token is an action, not an app")
                    Handler(Looper.getMainLooper()).post {
                        Toast.makeText(context, "Pick an app, not an action", Toast.LENGTH_SHORT).show()
                    }
                    return@OnSharedPreferenceChangeListener
                }

                if (token.startsWith("shortcut:")) {
                    Log.w(TAG, "Picked token $token is a shortcut, not an app")
                    Handler(Looper.getMainLooper()).post {
                        Toast.makeText(context, "Pick an app", Toast.LENGTH_SHORT).show()
                    }
                    return@OnSharedPreferenceChangeListener
                }

                val rawPkgOrComp = token.removePrefix("app:")

                val component: String? = if (rawPkgOrComp.contains("/")) {
                    rawPkgOrComp
                } else {
                    try {
                        context.packageManager.getLaunchIntentForPackage(rawPkgOrComp)?.component?.flattenToString()
                    } catch (e: Exception) {
                        Log.w(TAG, "Failed resolving launch intent component for $rawPkgOrComp", e)
                        null
                    }
                }

                if (component.isNullOrBlank()) {
                    Log.w(TAG, "Component resolution failed for token=$token")
                    Handler(Looper.getMainLooper()).post {
                        Toast.makeText(context, "Split failed: app has no launcher activity", Toast.LENGTH_SHORT).show()
                    }
                    return@OnSharedPreferenceChangeListener
                }

                Log.w(TAG, "Resolved component=$component, invoking splitWithApp")
                splitWithApp(context, component)
            }
        }

        synchronized(this) {
            splitPickerListener = newListener
            prefs.registerOnSharedPreferenceChangeListener(newListener)
        }

        val mainHandler = Handler(Looper.getMainLooper())
        mainHandler.postDelayed({
            synchronized(this@SplitScreenManager) {
                if (splitPickerListener === newListener) {
                    Log.w(TAG, "showSplitAppPicker 60s safety timeout reached; unregistering listener")
                    try {
                        prefs.unregisterOnSharedPreferenceChangeListener(newListener)
                    } catch (e: Exception) {
                        logSwallowed(TAG, "showSplitAppPicker:timeoutUnregister", e)
                    }
                    splitPickerListener = null
                }
            }
        }, 60000L)

        mainHandler.post {
            val ctxToUse = com.sbf.lightspeed.LightspeedAccessibilityService.instance ?: context
            Log.w(TAG, "Starting CockpitGearPickerActivity using context ${ctxToUse.javaClass.simpleName}")
            try {
                val intent = Intent(ctxToUse, com.sbf.lightspeed.CockpitGearPickerActivity::class.java).apply {
                    putExtra("SINGLE_SELECT_PREF_KEY", LightspeedPreferences.KEY_SPLIT_WITH_APP_TARGET)
                    putExtra("SINGLE_SELECT_TITLE", "Pick app for split")
                    putExtra("SPLIT_MODE", true)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                ctxToUse.startActivity(intent)
            } catch (e: Exception) {
                Log.w(TAG, "Failed to start CockpitGearPickerActivity for split picker", e)
            }
        }
    }

    fun splitWithApp(context: Context, component: String) {
        if (ShizukuGate.blockedByMode(context)) return
        Log.w(TAG, "splitWithApp() invoked for component=$component")
        ElevatedTaskCloser.exemptHiddenApis()

        ElevatedTaskCloser.executor.execute {
            val targetBPackageName = if (component.contains("/")) {
                component.substringBefore("/")
            } else {
                component
            }

            // a) read current top task via getTopForegroundTaskInfo(context) and remember its taskId (taskA)
            val taskInfoA = ElevatedTaskCloser.getTopForegroundTaskInfo(context)
            val taskA = taskInfoA.taskId
            val pkgA = taskInfoA.packageName
            Log.w(TAG, "splitWithApp step a: taskA=$taskA pkg=$pkgA")

            if (!ElevatedTaskCloser.isSplittableTask(context, taskA, pkgA)) {
                Handler(Looper.getMainLooper()).post {
                    Toast.makeText(context, "Open an app first to use split", Toast.LENGTH_SHORT).show()
                }
                return@execute
            }

            // b) launch B with execShizuku("am start -n <component> -f 0x10000000") and wait for it with waitForOrKill
            if (ShellArgGuard.isComponent(component)) {
                val startCmd = "am start -n '$component' -f 0x10000000"
                Log.w(TAG, "splitWithApp step b: launching B via $startCmd")
                val pB = ElevatedTaskCloser.execShizuku(startCmd)
                val startCode = pB.waitForOrKill(5000L)
                Log.w(TAG, "splitWithApp step b: am start exitCode=$startCode")
            } else {
                Log.w(TAG, "splitWithApp step b: invalid component name, skipping launch")
            }

            // c) poll getTopForegroundTaskInfo every 150ms for up to 2000ms until packageName equals B's package and taskId != taskA
            val startPoll = SystemClock.uptimeMillis()
            var taskInfoB: ForegroundTaskInfo? = null
            while (SystemClock.uptimeMillis() - startPoll < 2000L) {
                val info = ElevatedTaskCloser.getTopForegroundTaskInfo(context)
                if (info.packageName == targetBPackageName && info.taskId != null && info.taskId != taskA) {
                    taskInfoB = info
                    break
                }
                try {
                    Thread.sleep(150L)
                } catch (e: InterruptedException) {
                    logSwallowed(TAG, "splitWithApp:pollInterrupted", e)
                }
            }

            if (taskInfoB == null || taskInfoB.taskId == null) {
                Log.w(TAG, "splitWithApp step c failed: app B ($targetBPackageName) did not open or taskId equals taskA ($taskA)")
                Handler(Looper.getMainLooper()).post {
                    Toast.makeText(context, "Split failed: app did not open", Toast.LENGTH_SHORT).show()
                }
                return@execute
            }

            val bTaskId = taskInfoB.taskId!!
            val bPkg = taskInfoB.packageName ?: targetBPackageName
            Log.w(TAG, "splitWithApp step c: B opened with bTaskId=$bTaskId bPkg=$bPkg")

            if (!ElevatedTaskCloser.isSplittableTask(context, bTaskId, bPkg)) {
                Handler(Looper.getMainLooper()).post {
                    Toast.makeText(context, "Open an app first to use split", Toast.LENGTH_SHORT).show()
                }
                return@execute
            }

            // d) & e) wait 300ms, run moveToSideStage, verify with verifyWindowModePolling, retry steps d and e ONCE if unverified
            var lastReason = "unknown"
            var verifiedOk = false

            for (attempt in 1..2) {
                if (attempt == 2) {
                    Log.w(TAG, "splitWithApp step e: attempt 1 failed ($lastReason), waiting 500ms before retry")
                    try {
                        Thread.sleep(500L)
                    } catch (e: InterruptedException) {
                        logSwallowed(TAG, "splitWithApp:retry500", e)
                    }
                }

                try {
                    Thread.sleep(300L)
                } catch (e: InterruptedException) {
                    logSwallowed(TAG, "splitWithApp:wait300", e)
                }

                val wmCmd = "cmd statusbar wmshell-passthrough splitscreen moveToSideStage $bTaskId 1 2>&1"
                val pWm = ElevatedTaskCloser.execShizuku(wmCmd)
                val textWm = pWm.readTextOrKill(3000L).orEmpty()
                val codeWm = pWm.waitForOrKill(1000L)
                val returnedText = textWm.trim()

                PopupDiagnostics.recordStep(
                    "split",
                    "wmshell_moveToSideStage",
                    ran = true,
                    returned = returnedText,
                    verified = false
                )
                Log.w(TAG, "splitWithApp step d (attempt $attempt): wmshell moveToSideStage code=$codeWm text=$returnedText for bTaskId=$bTaskId")

                val cmdFailed = codeWm != 0 ||
                    textWm.contains("Invalid", ignoreCase = true) ||
                    textWm.contains("Error", ignoreCase = true)

                if (cmdFailed) {
                    lastReason = if (returnedText.isNotBlank()) returnedText else "cmd exit code $codeWm"
                    continue
                }

                val (verified, line) = ElevatedTaskCloser.verifyWindowModePolling(bTaskId, bPkg, "split")
                PopupDiagnostics.updateStepVerification("split", "wmshell_moveToSideStage", verified)

                if (verified) {
                    verifiedOk = true
                    Log.w(TAG, "splitWithApp step e (attempt $attempt): Split verified: $line")
                    break
                } else {
                    lastReason = line ?: "unverified mode in dumpsys"
                    Log.w(TAG, "splitWithApp step e (attempt $attempt): Split unverified: $line")
                }
            }

            // f) toast "Split: OK" only if verified, otherwise "Split failed: <reason>"
            Handler(Looper.getMainLooper()).post {
                if (verifiedOk) {
                    Toast.makeText(context, "Split: OK", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(context, "Split failed: $lastReason", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }
}
