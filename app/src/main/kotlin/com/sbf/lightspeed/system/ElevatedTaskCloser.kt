package com.sbf.lightspeed.system

import android.app.Activity
import android.content.ComponentName
import com.sbf.lightspeed.LightspeedAccessibilityService
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import android.widget.Toast
import rikka.shizuku.Shizuku
import rikka.shizuku.ShizukuBinderWrapper
import rikka.shizuku.SystemServiceHelper
import java.io.BufferedReader
import java.io.InputStreamReader
import java.lang.reflect.Method
import java.util.Locale

typealias ForegroundTaskInfo = TaskInspector.ForegroundTaskInfo

object ElevatedTaskCloser {
    internal const val TAG = "ElevatedTaskCloser"
    const val SHIZUKU_REQ_CODE = 9001

    internal val executor = java.util.concurrent.Executors.newSingleThreadExecutor()

    val isShizukuActive: Boolean
        get() {
            if (ShizukuGate.isAccessibilityOnly()) return false
            return try {
                Shizuku.pingBinder() && Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
            } catch (_: Exception) { false }
        }

    fun requestPermission(activity: Activity) { ShizukuExecHelper.requestPermission(activity) }

    fun exemptHiddenApis() { ShizukuExecHelper.exemptHiddenApis() }

    internal fun getActivityTaskManager(): Any? = ShizukuExecHelper.getActivityTaskManager()

    fun execShizuku(cmd: String): Process? = ShizukuExecHelper.execShizuku(cmd)

    fun execShizukuArgv(vararg args: String): Process? = ShizukuExecHelper.execShizukuArgv(*args)

    fun closeTopApp(context: Context) {
        TopAppCloser.closeTopApp(context)
    }

    fun toastShizukuNeeded(context: Context, feature: String) { ShizukuExecHelper.toastShizukuNeeded(context, feature) }

    internal fun isSystem(pkg: String): Boolean = TaskInspector.isSystem(pkg)

    fun switchToPreviousApp(context: Context) {
        AppSwitcherHelper.switchToPreviousApp(context)
    }

    internal fun getTopForegroundTaskInfo(context: Context): ForegroundTaskInfo =
        TaskInspector.getTopForegroundTaskInfo(context)

    internal fun getTaskLinesFromDumpsys(): List<String> =
        TaskInspector.getTaskLinesFromDumpsys()

    internal fun isSplittableTask(context: Context, taskId: Int?, pkg: String?): Boolean =
        TaskInspector.isSplittableTask(context, taskId, pkg)

    internal fun isTaskInSplit(taskId: Int): Boolean =
        TaskInspector.isTaskInSplit(taskId)

    internal fun getSplitGroupTaskIds(taskId: Int): Set<Int> =
        TaskInspector.getSplitGroupTaskIds(taskId)

    internal fun verifyWindowMode(
        taskId: Int?,
        packageName: String?,
        expectedMode: String
    ): Pair<Boolean, String?> = TaskInspector.verifyWindowMode(taskId, packageName, expectedMode)

    internal fun verifyWindowModePolling(
        taskId: Int?,
        packageName: String?,
        expectedMode: String,
        maxWaitMs: Long = 500L,
        stepMs: Long = 150L
    ): Pair<Boolean, String?> = TaskInspector.verifyWindowModePolling(taskId, packageName, expectedMode, maxWaitMs, stepMs)

    fun toggleSplitScreen(context: Context) {
        Log.w(TAG, "toggleSplitScreen() invoked")
        exemptHiddenApis()

        executor.execute {
            // Delay call ~150ms after gesture ends so Lightspeed's touch overlay isn't the focused window
            try {
                Thread.sleep(150)
            } catch (e: InterruptedException) { logSwallowed("ElevatedTaskCloser", "toggleSplitScreen:550", e) }

            val taskInfo = getTopForegroundTaskInfo(context)
            val targetTaskId = taskInfo.taskId
            val targetPkg = taskInfo.packageName

            val frontPkg = targetPkg ?: LightspeedAccessibilityService.lastAppPackage
            val assignedMethods = if (!frontPkg.isNullOrBlank()) {
                ActionMethods.getAssignedMethodsForApp(context, "system:split_screen", frontPkg)
            } else {
                emptySet()
            }
            val methods = if (assignedMethods.isNotEmpty()) assignedMethods else ActionMethods.getSelected(context, "system:split_screen")
            val auto = ActionMethods.AUTO in methods
            val a11yOnly = ShizukuGate.isAccessibilityOnly()
            val useShizuku = !a11yOnly && isShizukuActive && (auto || ActionMethods.SHIZUKU in methods)
            val useA11y = auto || ActionMethods.A11Y_ACTION in methods
            val useRecents = (auto && a11yOnly) || ActionMethods.RECENTS_CLICK in methods
            Log.w(TAG, "split methods=$methods useShizuku=$useShizuku useA11y=$useA11y useRecents=$useRecents")

            if (targetTaskId != null && targetTaskId > 0 && isTaskInSplit(targetTaskId)) {
                Log.w(TAG, "Already in split mode for targetTaskId=$targetTaskId, targetPkg=$targetPkg")

                var component: String? = null
                var componentSource = "none"

                val taskLines = getTaskLinesFromDumpsys()
                val matchedLine = taskLines.firstOrNull {
                    it.contains("#$targetTaskId ") || it.contains("#$targetTaskId}") || Regex("""(?<![A-Za-z0-9])id=$targetTaskId""").containsMatchIn(it)
                }
                if (matchedLine != null) {
                    val matchComp = Regex("""I=([a-zA-Z0-9_.]+/[a-zA-Z0-9_.]+)""").find(matchedLine)
                        ?: Regex("""realActivity=([a-zA-Z0-9_.]+/[a-zA-Z0-9_.]+)""").find(matchedLine)
                        ?: Regex("""topActivity=([a-zA-Z0-9_.]+/[a-zA-Z0-9_.]+)""").find(matchedLine)
                    if (matchComp != null) {
                        component = matchComp.groupValues[1]
                        componentSource = "dumpsys task line"
                    }
                }

                if (component.isNullOrBlank() && !targetPkg.isNullOrBlank()) {
                    try {
                        val launchIntent = context.packageManager.getLaunchIntentForPackage(targetPkg)
                        component = launchIntent?.component?.flattenToShortString()
                        if (!component.isNullOrBlank()) {
                            componentSource = "packageManager launch intent"
                        }
                    } catch (e: Exception) {
                        Log.w(TAG, "Failed to resolve launch intent for $targetPkg", e)
                    }
                }

                Log.w(TAG, "Resolved component for fullscreen: $component (source: $componentSource)")

                var splitDissolved = false
                if (!component.isNullOrBlank() && ShellArgGuard.isComponent(component)) {
                    val amCmd = "am start -n '$component' -f 0x10020000"
                    val p = execShizuku("$amCmd 2>&1")
                    val textOutput = p.readTextOrKill(3000L).orEmpty()
                    val exitCode = p.waitForOrKill(1000L)
                    Log.w(TAG, "Executed am start command: '$amCmd' -> exitCode=$exitCode, output=$textOutput")

                    val start = SystemClock.uptimeMillis()
                    while (SystemClock.uptimeMillis() - start < 1200L) {
                        if (!isTaskInSplit(targetTaskId)) {
                            splitDissolved = true
                            break
                        }
                        try { Thread.sleep(150L) } catch (_: InterruptedException) {}
                    }
                    if (!splitDissolved) {
                        splitDissolved = !isTaskInSplit(targetTaskId)
                    }
                } else {
                    Log.w(TAG, "Cannot launch fullscreen: component is invalid or blank")
                }

                Log.w(TAG, "Verification result: splitDissolved=$splitDissolved for targetTaskId=$targetTaskId")

                Handler(Looper.getMainLooper()).post {
                    val msg = if (splitDissolved) "Fullscreen" else "Could not exit split"
                    Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                }
                return@execute
            }

            if (!ShizukuGate.isAccessibilityOnly() && !isSplittableTask(context, targetTaskId, targetPkg)) {
                Handler(Looper.getMainLooper()).post {
                    Toast.makeText(context, "Open an app first to use split", Toast.LENGTH_SHORT).show()
                }
                return@execute
            }

            if (ShizukuGate.isAccessibilityOnly()) {
                var defaultLauncherPkg: String? = null
                try {
                    val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
                    val resolveInfo = context.packageManager.resolveActivity(intent, PackageManager.MATCH_DEFAULT_ONLY)
                    defaultLauncherPkg = resolveInfo?.activityInfo?.packageName
                } catch (e: Exception) {
                    Log.w(TAG, "Failed to resolve default home launcher", e)
                }

                if (targetPkg.isNullOrBlank() || (defaultLauncherPkg != null && targetPkg == defaultLauncherPkg)) {
                    Handler(Looper.getMainLooper()).post {
                        Toast.makeText(context, "Open an app first to use split", Toast.LENGTH_SHORT).show()
                    }
                    return@execute
                }

                val service = com.sbf.lightspeed.LightspeedAccessibilityService.instance
                Log.w(TAG, "A11Y-only split: service=${service != null}, pkg=$targetPkg, sdk=${Build.VERSION.SDK_INT}")
                if (service != null) {
                    val splitSuccess = if (useA11y) {
                        val ok = service.performGlobalAction(
                            android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_TOGGLE_SPLIT_SCREEN
                        )
                        Log.w(TAG, "A11Y-only split: performGlobalAction returned $ok")
                        PopupDiagnostics.recordStep("split", "performGlobalAction_a11y_only", ran = true, returned = ok.toString(), verified = false)
                        ok
                    } else {
                        false
                    }

                    if (splitSuccess) {
                        Handler(Looper.getMainLooper()).post {
                            Toast.makeText(context, "Split: OK", Toast.LENGTH_SHORT).show()
                        }
                    } else if (useRecents && !targetPkg.isNullOrBlank()) {
                        RecentsSplitFallback.splitViaRecents(context, service, targetPkg)
                    } else {
                        Handler(Looper.getMainLooper()).post {
                            Toast.makeText(context, "Split isn't supported with the selected methods", Toast.LENGTH_SHORT).show()
                        }
                    }
                } else {
                    Log.w(TAG, "A11Y-only split: service is null")
                    PopupDiagnostics.recordStep("split", "performGlobalAction_a11y_only", ran = false, returned = "AccessibilityService not connected", verified = false)
                    Handler(Looper.getMainLooper()).post {
                        Toast.makeText(context, "Split failed: AccessibilityService not connected", Toast.LENGTH_SHORT).show()
                    }
                }
                return@execute
            }

            val service = com.sbf.lightspeed.LightspeedAccessibilityService.instance
            var splitSuccess = false
            var lastFailureReason = "unknown"

            // 1. FIRST try Shizuku WMShell moveToSideStage (proven on Android 12+ / WMShell / Transsion)
            if (useShizuku && targetTaskId != null && targetTaskId > 0) {
                for (pos in intArrayOf(1, 0)) {
                    val wmCmd = "cmd statusbar wmshell-passthrough splitscreen moveToSideStage $targetTaskId $pos 2>&1"
                    val pWm = execShizuku(wmCmd)
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
                    Log.w(TAG, "wmshell moveToSideStage executed (pos $pos): code=$codeWm text=$returnedText for taskId=$targetTaskId")

                    val failed = codeWm != 0 ||
                        textWm.contains("Invalid", ignoreCase = true) ||
                        textWm.contains("Error", ignoreCase = true)

                    if (!failed) {
                        val (verified, line) = verifyWindowModePolling(targetTaskId, targetPkg, "split", maxWaitMs = 1200L, stepMs = 200L)
                        PopupDiagnostics.updateStepVerification("split", "wmshell_moveToSideStage", verified)
                        if (verified) {
                            splitSuccess = true
                            Log.w(TAG, "Split Screen verified via wmshell moveToSideStage: $line")
                        } else {
                            Log.w(TAG, "wmshell moveToSideStage executed but unverified in dumpsys; launching single-app split picker")
                            showSplitAppPicker(context)
                            return@execute
                        }
                        break
                    }
                }
            }

            // 2. Try service.performGlobalAction(GLOBAL_ACTION_TOGGLE_SPLIT_SCREEN)
            if (useA11y && !splitSuccess && Build.VERSION.SDK_INT >= Build.VERSION_CODES.N && service != null) {
                val globalResult = service.performGlobalAction(
                    android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_TOGGLE_SPLIT_SCREEN
                )
                Log.w(
                    TAG,
                    "performGlobalAction(GLOBAL_ACTION_TOGGLE_SPLIT_SCREEN) returned $globalResult, foreground package: $targetPkg (taskId=$targetTaskId)"
                )
                PopupDiagnostics.recordSplitAttempt(globalResult, targetPkg)
                PopupDiagnostics.recordStep(
                    "split",
                    "performGlobalAction",
                    ran = true,
                    returned = globalResult.toString(),
                    verified = false
                )

                if (globalResult) {
                    val (verified, line) = verifyWindowModePolling(targetTaskId, targetPkg, "split")
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
                PopupDiagnostics.recordSplitAttempt(false, targetPkg)
                PopupDiagnostics.recordStep(
                    "split",
                    "performGlobalAction",
                    ran = false,
                    returned = lastFailureReason,
                    verified = false
                )
            }

            // 3. Only then try Shizuku setTaskWindowingMode and the CLI.
            if (useShizuku && !splitSuccess && targetTaskId != null && targetTaskId > 0) {
                val atm = getActivityTaskManager()
                if (atm != null) {
                    try {
                        val setTaskWindowingModeMethod = atm.javaClass.methods.firstOrNull {
                            it.name == "setTaskWindowingMode" && it.parameterTypes.size == 3
                        }
                        if (setTaskWindowingModeMethod != null) {
                            setTaskWindowingModeMethod.invoke(atm, targetTaskId, 3, true)
                            PopupDiagnostics.recordStep(
                                "split",
                                "shizuku_binder_setTaskWindowingMode",
                                ran = true,
                                returned = "invoked",
                                verified = false
                            )
                            val (verified, line) = verifyWindowModePolling(targetTaskId, targetPkg, "split")
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
                        Log.w(TAG, "setTaskWindowingMode split_screen binder failed", e)
                        PopupDiagnostics.recordStep(
                            "split",
                            "shizuku_binder_setTaskWindowingMode",
                            ran = true,
                            returned = "error: ${e.message}",
                            verified = false
                        )
                        lastFailureReason = "binder error: ${e.message}"
                    }
                }

                if (!splitSuccess) {
                    val cmdTask = "am task set-windowing-mode $targetTaskId 3"
                    val cmdStack = "am stack set-windowing-mode $targetTaskId 3"
                    if (isShizukuActive) {
                        val p1 = execShizuku(cmdTask)
                        val code1 = p1.waitForOrKill()
                        PopupDiagnostics.recordStep(
                            "split",
                            "shizuku_cli_am_task",
                            ran = true,
                            returned = "exit $code1",
                            verified = false
                        )
                        if (code1 == 0) {
                            val (verified, line) = verifyWindowModePolling(targetTaskId, targetPkg, "split")
                            if (verified) {
                                splitSuccess = true
                                PopupDiagnostics.updateStepVerification("split", "shizuku_cli_am_task", true)
                                Log.w(TAG, "Split screen verified via Shizuku $cmdTask: $line")
                            }
                        }
                        if (!splitSuccess) {
                            val p2 = execShizuku(cmdStack)
                            val code2 = p2.waitForOrKill()
                            PopupDiagnostics.recordStep(
                                "split",
                                "shizuku_cli_am_stack",
                                ran = true,
                                returned = "exit $code2",
                                verified = false
                            )
                            if (code2 == 0) {
                                val (verified, line) = verifyWindowModePolling(targetTaskId, targetPkg, "split")
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
            }

            if (!splitSuccess && useRecents && !targetPkg.isNullOrBlank() && service != null) {
                RecentsSplitFallback.splitViaRecents(context, service, targetPkg)
                return@execute
            }

            // 4. User feedback: Truthful Toast, do NOT fall back to Recents!
            Handler(Looper.getMainLooper()).post {
                if (splitSuccess) {
                    Toast.makeText(context, "Split: OK", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(context, "Split failed: $lastFailureReason", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    fun splitWithApp(context: Context, component: String, allowRecents: Boolean = true) {
        SplitScreenManager.splitWithApp(context, component, allowRecents)
    }

    fun splitPairWithApp(context: Context, component: String) {
        SplitScreenManager.splitPairWithApp(context, component)
    }

    private fun showSplitAppPicker(context: Context) {
        SplitScreenManager.showSplitAppPicker(context)
    }

    @Deprecated("Use launchInPopup(context, style) instead")
    fun launchInFreeform(context: Context) {
        PopupLauncher.launchInFreeform(context)
    }

    fun launchInPopup(context: Context, style: String = LightspeedPreferences.POPUP_STYLE_NATIVE) {
        PopupLauncher.launchInPopup(context, style)
    }

    fun launchAppInPopup(context: Context, component: String, allowRecents: Boolean = true) {
        PopupLauncher.launchAppInPopup(context, component, allowRecents = allowRecents)
    }
}

