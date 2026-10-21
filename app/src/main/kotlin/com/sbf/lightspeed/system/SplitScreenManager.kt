package com.sbf.lightspeed.system

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import android.widget.Toast
import com.sbf.lightspeed.LightspeedAccessibilityService

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
                        splitWithApp(context, resolvedComp, allowRecents = false)
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
                splitWithApp(context, component, allowRecents = false)
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

    fun splitPairWithApp(context: Context, component: String) {
        Log.w(TAG, "splitPairWithApp() invoked for component=$component")
        ElevatedTaskCloser.executor.execute {
            val pkg = if (component.contains("/")) component.substringBefore("/") else component
            val intent = context.packageManager.getLaunchIntentForPackage(pkg)?.apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            if (intent != null) {
                try {
                    context.startActivity(intent)
                } catch (e: Exception) {
                    logSwallowed(TAG, "splitPairWithApp:launchA", e)
                }
            }
            try {
                Thread.sleep(400L)
            } catch (_: InterruptedException) {}
            showSplitAppPicker(context)
        }
    }

    fun splitWithApp(context: Context, component: String, allowRecents: Boolean = true) {
        Log.w(TAG, "splitWithApp() invoked for component=$component allowRecents=$allowRecents")
        ElevatedTaskCloser.exemptHiddenApis()

        ElevatedTaskCloser.executor.execute {
            // a) read current top task via getTopForegroundTaskInfo(context) and remember its taskId (taskA)
            val taskInfoA = ElevatedTaskCloser.getTopForegroundTaskInfo(context)
            val taskA = taskInfoA.taskId
            val pkgA = taskInfoA.packageName
            Log.w(TAG, "splitWithApp step a: taskA=$taskA pkg=$pkgA")

            val frontPkg = pkgA ?: LightspeedAccessibilityService.lastAppPackage
            val assignedMethods = if (!frontPkg.isNullOrBlank()) {
                ActionMethods.getAssignedMethodsForApp(context, "system:split_screen", frontPkg)
            } else {
                emptySet()
            }
            val methods = if (!allowRecents) {
                setOf(ActionMethods.AUTO)
            } else if (assignedMethods.isNotEmpty()) {
                assignedMethods
            } else {
                ActionMethods.getSelected(context, "system:split_screen")
            }
            val auto = ActionMethods.AUTO in methods
            val a11yOnly = ShizukuGate.isAccessibilityOnly()
            val useShizuku = !a11yOnly && ElevatedTaskCloser.isShizukuActive && (auto || ActionMethods.SHIZUKU in methods)
            val useA11y = auto || ActionMethods.A11Y_ACTION in methods
            val useRecents = allowRecents && ((auto && a11yOnly) || ActionMethods.RECENTS_CLICK in methods)
            Log.w(TAG, "splitWithApp methods=$methods useShizuku=$useShizuku useA11y=$useA11y useRecents=$useRecents")

            if (!useShizuku && !useA11y && !useRecents) {
                Handler(Looper.getMainLooper()).post {
                    Toast.makeText(context, "Split isn't supported with the selected methods", Toast.LENGTH_SHORT).show()
                }
                return@execute
            }

            val targetBPackageName = if (component.contains("/")) {
                component.substringBefore("/")
            } else {
                component
            }

            if (a11yOnly) {
                var defaultLauncherPkg: String? = null
                try {
                    val homeIntent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
                    val resolveInfo = context.packageManager.resolveActivity(homeIntent, android.content.pm.PackageManager.MATCH_DEFAULT_ONLY)
                    defaultLauncherPkg = resolveInfo?.activityInfo?.packageName
                } catch (e: Exception) {
                    Log.w(TAG, "Failed to resolve default home launcher", e)
                }

                if (pkgA.isNullOrBlank() || (defaultLauncherPkg != null && pkgA == defaultLauncherPkg)) {
                    Handler(Looper.getMainLooper()).post {
                        Toast.makeText(context, "Open an app first to use split", Toast.LENGTH_SHORT).show()
                    }
                    return@execute
                }
            } else {
                if (!ElevatedTaskCloser.isSplittableTask(context, taskA, pkgA)) {
                    Handler(Looper.getMainLooper()).post {
                        Toast.makeText(context, "Open an app first to use split", Toast.LENGTH_SHORT).show()
                    }
                    return@execute
                }
            }

            // b) launch B
            var launchedB = false
            if (useShizuku && ShellArgGuard.isComponent(component)) {
                val startCmd = "am start -n '$component' -f 0x10000000"
                Log.w(TAG, "splitWithApp step b: launching B via $startCmd")
                val pB = ElevatedTaskCloser.execShizuku(startCmd)
                val startCode = pB?.waitForOrKill(5000L) ?: -1
                Log.w(TAG, "splitWithApp step b: am start exitCode=$startCode")
                if (startCode == 0) {
                    launchedB = true
                }
            }
            if (!launchedB) {
                try {
                    val launchIntent = if (component.contains("/")) {
                        val cn = android.content.ComponentName.unflattenFromString(component)
                        if (cn != null) Intent().setComponent(cn) else context.packageManager.getLaunchIntentForPackage(targetBPackageName)
                    } else {
                        context.packageManager.getLaunchIntentForPackage(targetBPackageName)
                    }
                    if (launchIntent != null) {
                        launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        context.startActivity(launchIntent)
                        launchedB = true
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "splitWithApp step b: startActivity failed", e)
                }
            }

            // c) poll getTopForegroundTaskInfo every 150ms for up to 2000ms until packageName equals B's package
            val startPoll = SystemClock.uptimeMillis()
            var taskInfoB: ForegroundTaskInfo? = null
            while (SystemClock.uptimeMillis() - startPoll < 2000L) {
                val info = ElevatedTaskCloser.getTopForegroundTaskInfo(context)
                if (info.packageName == targetBPackageName && (taskA == null || info.taskId != taskA)) {
                    taskInfoB = info
                    break
                }
                try {
                    Thread.sleep(150L)
                } catch (e: InterruptedException) {
                    logSwallowed(TAG, "splitWithApp:pollInterrupted", e)
                }
            }

            if (taskInfoB == null || (!a11yOnly && taskInfoB.taskId == null)) {
                Log.w(TAG, "splitWithApp step c failed: app B ($targetBPackageName) did not open or taskId equals taskA ($taskA)")
                Handler(Looper.getMainLooper()).post {
                    Toast.makeText(context, "Split failed: app did not open", Toast.LENGTH_SHORT).show()
                }
                return@execute
            }

            val bTaskId = taskInfoB.taskId
            val bPkg = taskInfoB.packageName ?: targetBPackageName
            Log.w(TAG, "splitWithApp step c: B opened with bTaskId=$bTaskId bPkg=$bPkg")

            if (!a11yOnly && !ElevatedTaskCloser.isSplittableTask(context, bTaskId, bPkg)) {
                Handler(Looper.getMainLooper()).post {
                    Toast.makeText(context, "Open an app first to use split", Toast.LENGTH_SHORT).show()
                }
                return@execute
            }

            // d) Mode order & fallback logic matching toggleSplitScreen
            if (a11yOnly) {
                val service = LightspeedAccessibilityService.instance
                Log.w(TAG, "A11Y-only splitWithApp: service=${service != null}, pkg=$bPkg, sdk=${Build.VERSION.SDK_INT}")
                if (service != null) {
                    val splitSuccess = if (useA11y) {
                        val ok = service.performGlobalAction(
                            android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_TOGGLE_SPLIT_SCREEN
                        )
                        Log.w(TAG, "A11Y-only splitWithApp: performGlobalAction returned $ok")
                        PopupDiagnostics.recordStep("split", "performGlobalAction_a11y_only", ran = true, returned = ok.toString(), verified = false)
                        ok
                    } else {
                        false
                    }

                    if (splitSuccess) {
                        Handler(Looper.getMainLooper()).post {
                            Toast.makeText(context, "Split: OK", Toast.LENGTH_SHORT).show()
                        }
                    } else if (useRecents && !bPkg.isNullOrBlank()) {
                        RecentsSplitFallback.splitViaRecents(context, service, bPkg)
                    } else {
                        Handler(Looper.getMainLooper()).post {
                            Toast.makeText(context, "Split isn't supported with the selected methods", Toast.LENGTH_SHORT).show()
                        }
                    }
                } else {
                    Log.w(TAG, "A11Y-only splitWithApp: service is null")
                    PopupDiagnostics.recordStep("split", "performGlobalAction_a11y_only", ran = false, returned = "AccessibilityService not connected", verified = false)
                    Handler(Looper.getMainLooper()).post {
                        Toast.makeText(context, "Split failed: AccessibilityService not connected", Toast.LENGTH_SHORT).show()
                    }
                }
                return@execute
            }

            val service = LightspeedAccessibilityService.instance
            var splitSuccess = false
            var lastFailureReason = "unknown"

            // 1. FIRST try Shizuku WMShell moveToSideStage (pos 1, 0)
            if (useShizuku && bTaskId != null && bTaskId > 0) {
                for (pos in intArrayOf(1, 0)) {
                    val wmCmd = "cmd statusbar wmshell-passthrough splitscreen moveToSideStage $bTaskId $pos 2>&1"
                    val pWm = ElevatedTaskCloser.execShizuku(wmCmd)
                    val textWm = pWm?.readTextOrKill(3000L).orEmpty()
                    val codeWm = pWm?.waitForOrKill(1000L) ?: -1
                    val returnedText = textWm.trim()

                    PopupDiagnostics.recordStep(
                        "split",
                        "wmshell_moveToSideStage",
                        ran = true,
                        returned = returnedText,
                        verified = false
                    )
                    Log.w(TAG, "splitWithApp step d (pos $pos): wmshell moveToSideStage code=$codeWm text=$returnedText for bTaskId=$bTaskId")

                    val failed = codeWm != 0 ||
                        textWm.contains("Invalid", ignoreCase = true) ||
                        textWm.contains("Error", ignoreCase = true)

                    if (!failed) {
                        val (verified, line) = ElevatedTaskCloser.verifyWindowModePolling(bTaskId, bPkg, "split")
                        PopupDiagnostics.updateStepVerification("split", "wmshell_moveToSideStage", verified)
                        if (verified) {
                            splitSuccess = true
                            Log.w(TAG, "splitWithApp step e: Split verified: $line")
                            break
                        } else {
                            lastFailureReason = line ?: "unverified in dumpsys"
                            Log.w(TAG, "splitWithApp step e: Split unverified: $line")
                        }
                    } else {
                        lastFailureReason = if (returnedText.isNotBlank()) returnedText else "cmd exit code $codeWm"
                    }
                }
            }

            // 2. Try service.performGlobalAction(GLOBAL_ACTION_TOGGLE_SPLIT_SCREEN)
            if (useA11y && !splitSuccess && Build.VERSION.SDK_INT >= Build.VERSION_CODES.N && service != null) {
                val globalResult = service.performGlobalAction(
                    android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_TOGGLE_SPLIT_SCREEN
                )
                Log.w(TAG, "performGlobalAction(GLOBAL_ACTION_TOGGLE_SPLIT_SCREEN) returned $globalResult for bPkg: $bPkg (bTaskId=$bTaskId)")
                PopupDiagnostics.recordSplitAttempt(globalResult, bPkg)
                PopupDiagnostics.recordStep(
                    "split",
                    "performGlobalAction",
                    ran = true,
                    returned = globalResult.toString(),
                    verified = false
                )

                if (globalResult) {
                    val (verified, line) = ElevatedTaskCloser.verifyWindowModePolling(bTaskId, bPkg, "split")
                    if (verified) {
                        splitSuccess = true
                        PopupDiagnostics.updateStepVerification("split", "performGlobalAction", true)
                        Log.w(TAG, "Split Screen verified via performGlobalAction: $line")
                    } else {
                        Log.w(TAG, "performGlobalAction returned true but split mode unverified: $line")
                        lastFailureReason = "global action unverified in dumpsys"
                    }
                } else {
                    lastFailureReason = "performGlobalAction returned false"
                }
            } else if (useA11y && !splitSuccess) {
                lastFailureReason = if (service == null) "AccessibilityService not connected" else "SDK < N"
                PopupDiagnostics.recordSplitAttempt(false, bPkg)
                PopupDiagnostics.recordStep(
                    "split",
                    "performGlobalAction",
                    ran = false,
                    returned = lastFailureReason,
                    verified = false
                )
            }

            // 3. Shizuku binder setTaskWindowingMode and CLI
            if (useShizuku && !splitSuccess && bTaskId != null && bTaskId > 0) {
                val atm = ElevatedTaskCloser.getActivityTaskManager()
                if (atm != null) {
                    try {
                        val setTaskWindowingModeMethod = atm.javaClass.methods.firstOrNull {
                            it.name == "setTaskWindowingMode" && it.parameterTypes.size == 3
                        }
                        if (setTaskWindowingModeMethod != null) {
                            setTaskWindowingModeMethod.invoke(atm, bTaskId, 3, true)
                            PopupDiagnostics.recordStep(
                                "split",
                                "shizuku_binder_setTaskWindowingMode",
                                ran = true,
                                returned = "invoked",
                                verified = false
                            )
                            val (verified, line) = ElevatedTaskCloser.verifyWindowModePolling(bTaskId, bPkg, "split")
                            if (verified) {
                                splitSuccess = true
                                PopupDiagnostics.updateStepVerification(
                                    "split",
                                    "shizuku_binder_setTaskWindowingMode",
                                    true
                                )
                                Log.w(TAG, "Split Screen verified via IActivityTaskManager.setTaskWindowingMode: $line")
                            } else {
                                Log.w(TAG, "IActivityTaskManager.setTaskWindowingMode unverified: $line")
                                lastFailureReason = "binder mode unverified"
                            }
                        }
                    } catch (e: Exception) {
                        logSwallowed(TAG, "setTaskWindowingMode:splitWithApp", e)
                        lastFailureReason = "binder error: ${e.message}"
                    }
                }

                if (!splitSuccess && ElevatedTaskCloser.isShizukuActive) {
                    val cmdTask = "am task set-windowing-mode $bTaskId 3"
                    val cmdStack = "am stack set-windowing-mode $bTaskId 3"
                    val p1 = ElevatedTaskCloser.execShizuku(cmdTask)
                    val code1 = p1?.waitForOrKill() ?: -1
                    PopupDiagnostics.recordStep(
                        "split",
                        "shizuku_cli_am_task",
                        ran = true,
                        returned = "exit $code1",
                        verified = false
                    )
                    if (code1 == 0) {
                        val (verified, line) = ElevatedTaskCloser.verifyWindowModePolling(bTaskId, bPkg, "split")
                        if (verified) {
                            splitSuccess = true
                            PopupDiagnostics.updateStepVerification("split", "shizuku_cli_am_task", true)
                            Log.w(TAG, "Split screen verified via Shizuku $cmdTask: $line")
                        }
                    }
                    if (!splitSuccess) {
                        val p2 = ElevatedTaskCloser.execShizuku(cmdStack)
                        val code2 = p2?.waitForOrKill() ?: -1
                        PopupDiagnostics.recordStep(
                            "split",
                            "shizuku_cli_am_stack",
                            ran = true,
                            returned = "exit $code2",
                            verified = false
                        )
                        if (code2 == 0) {
                            val (verified, line) = ElevatedTaskCloser.verifyWindowModePolling(bTaskId, bPkg, "split")
                            if (verified) {
                                splitSuccess = true
                                PopupDiagnostics.updateStepVerification("split", "shizuku_cli_am_stack", true)
                                Log.w(TAG, "Split screen verified via Shizuku $cmdStack: $line")
                            }
                        }
                        if (!splitSuccess) {
                            lastFailureReason = "CLI commands failed ($code1, $code2)"
                        }
                    }
                }
            }

            // 4. Recents fallback
            if (!splitSuccess && useRecents && !bPkg.isNullOrBlank() && service != null) {
                RecentsSplitFallback.splitViaRecents(context, service, bPkg)
                return@execute
            }

            // User feedback
            Handler(Looper.getMainLooper()).post {
                if (splitSuccess) {
                    Toast.makeText(context, "Split: OK", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(context, "Split failed: $lastFailureReason", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }
}
