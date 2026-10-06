package com.sbf.lightspeed.system

import android.app.Activity
import android.content.ComponentName
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

    @Volatile
    private var hiddenApiExempted = false

    @Volatile
    private var splitPickerListener: SharedPreferences.OnSharedPreferenceChangeListener? = null

    internal val executor = java.util.concurrent.Executors.newSingleThreadExecutor()

    val isShizukuActive: Boolean
        get() = try {
            Shizuku.pingBinder() && Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
        } catch (_: Exception) { false }

    fun requestPermission(activity: Activity) {
        if (Shizuku.pingBinder() && !isShizukuActive) {
            Shizuku.requestPermission(SHIZUKU_REQ_CODE)
        }
    }

    fun exemptHiddenApis() {
        if (hiddenApiExempted) return
        try {
            val forNameMethod = Class::class.java.getDeclaredMethod("forName", String::class.java)
            val getDeclaredMethod = Class::class.java.getDeclaredMethod(
                "getDeclaredMethod",
                String::class.java,
                arrayOf<Class<*>>().javaClass
            )

            val vmRuntimeClass = forNameMethod.invoke(null, "dalvik.system.VMRuntime") as Class<*>
            val getRuntimeMethod = getDeclaredMethod.invoke(vmRuntimeClass, "getRuntime", null) as Method
            val setHiddenApiExemptionsMethod = getDeclaredMethod.invoke(
                vmRuntimeClass,
                "setHiddenApiExemptions",
                arrayOf(arrayOf<String>().javaClass)
            ) as Method

            val vmRuntime = getRuntimeMethod.invoke(null)
            setHiddenApiExemptionsMethod.invoke(vmRuntime, arrayOf("L"))
            hiddenApiExempted = true
            Log.i(TAG, "Hidden API exemptions applied successfully")
        } catch (e: Exception) {
            Log.w(TAG, "Hidden API exemption failed", e)
        }
    }

    internal fun getActivityTaskManager(): Any? {
        if (!isShizukuActive) return null
        return try {
            val rawBinder = SystemServiceHelper.getSystemService("activity_task")
                ?: SystemServiceHelper.getSystemService(Context.ACTIVITY_SERVICE)
            if (rawBinder != null) {
                val wrapped = ShizukuBinderWrapper(rawBinder)
                val stubClass = Class.forName("android.app.IActivityTaskManager\$Stub")
                val asInterface = stubClass.getMethod("asInterface", IBinder::class.java)
                asInterface.invoke(null, wrapped)
            } else {
                null
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to get IActivityTaskManager via Shizuku", e)
            null
        }
    }

    fun execShizuku(cmd: String): Process? {
        return try {
            val m = Shizuku::class.java.getDeclaredMethod(
                "newProcess",
                Array<String>::class.java,
                Array<String>::class.java,
                String::class.java
            ).apply { isAccessible = true }
            m.invoke(null, arrayOf("sh", "-c", cmd), null, null) as? Process
        } catch (e: Exception) {
            Log.e(TAG, "execShizuku error", e)
            null
        }
    }

    fun execShizukuArgv(vararg args: String): Process? {
        return try {
            val m = Shizuku::class.java.getDeclaredMethod(
                "newProcess",
                Array<String>::class.java,
                Array<String>::class.java,
                String::class.java
            ).apply { isAccessible = true }
            m.invoke(null, args, null, null) as? Process
        } catch (e: Exception) {
            Log.e(TAG, "execShizukuArgv error", e)
            null
        }
    }

    fun closeTopApp(context: Context) {
        Log.i(TAG, "closeTopApp() invoked")
        exemptHiddenApis()

        if (isShizukuActive && closeViaShizuku(context)) {
            Log.i(TAG, "Task dismissed and evicted from Recents via Shizuku")
            com.sbf.lightspeed.LightspeedAccessibilityService.instance?.scheduleGeometryResync()
            return
        }
        Handler(Looper.getMainLooper()).post {
            val msg = if (!Shizuku.pingBinder()) "Shizuku not running" else "Authorize Lightspeed in Shevery"
            Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
        }
    }

    private fun closeViaShizuku(context: Context): Boolean {
        return try {
            val myPkg = context.packageName

            var targetTaskId: Int? = null
            var targetPkg: String? = null
            var atm: Any? = null

            // 2. Ultra-fast target resolution via direct IActivityTaskManager Binder (<2ms)
            try {
                atm = getActivityTaskManager()

                    if (atm != null) {
                        val getRecentTasksMethod = atm.javaClass.methods.firstOrNull { it.name == "getRecentTasks" }
                        if (getRecentTasksMethod != null) {
                            val paramsCount = getRecentTasksMethod.parameterTypes.size
                            val rawResult = when (paramsCount) {
                                2 -> getRecentTasksMethod.invoke(atm, 5, 0x0002 /* RECENT_IGNORE_UNAVAILABLE */)
                                3 -> getRecentTasksMethod.invoke(atm, 5, 0x0002, 0)
                                else -> null
                            }
                            if (rawResult != null) {
                                val getListMethod = rawResult.javaClass.getMethod("getList")
                                val recentTasksList = getListMethod.invoke(rawResult) as? List<*>
                                if (!recentTasksList.isNullOrEmpty()) {
                                    for (item in recentTasksList) {
                                        if (item == null) continue
                                        val tId = item.javaClass.getField("taskId").getInt(item)
                                        val baseIntentField = try { item.javaClass.getField("baseIntent") } catch (_: Exception) { null }
                                        val intent = baseIntentField?.get(item) as? Intent
                                        val pkg = intent?.component?.packageName ?: intent?.`package`
                                        if (pkg != null && !isSystem(pkg) && pkg != myPkg && tId > 0) {
                                            targetTaskId = tId
                                            targetPkg = pkg
                                            break
                                        }
                                    }
                                }
                            }
                        }
                    }
            } catch (e: Exception) {
                Log.d(TAG, "Fast binder task lookup failed", e)
            }

            // Fallback 1: Lightweight dumpsys activity top (reads only top frame in ~30ms)
            if (targetTaskId == null && targetPkg == null) {
                val actProcess = execShizuku("dumpsys activity top 2>&1 | grep -E 'TASK|ACTIVITY' | head -n 4")
                actProcess?.let { p ->
                    val text = p.readTextOrKill().orEmpty()
                    for (line in text.lineSequence()) {
                        val tm = Regex("""TASK\s+.*?(?<![A-Za-z0-9])id=(\d+)""").find(line)
                        if (tm != null && targetTaskId == null) {
                            targetTaskId = tm.groupValues[1].toIntOrNull()
                        }
                        val am = Regex("""ACTIVITY\s+([a-zA-Z0-9_.]+)/""").find(line)
                        if (am != null && targetPkg == null) {
                            val pkg = am.groupValues[1]
                            if (pkg != myPkg && !isSystem(pkg)) {
                                targetPkg = pkg
                            }
                        }
                    }
                }
            }

            if (targetTaskId == null && targetPkg == null) {
                Log.i(TAG, "No foreground non-system app found to evict")
                return true
            }

            Log.i(TAG, "Targeting focused app: $targetPkg (TaskId: $targetTaskId)")

            var evicted = false

            // 3. Ultra-fast direct Binder task eviction (<2ms)
            if (targetTaskId != null && targetTaskId > 0 && atm != null) {
                try {
                    val removeTask = atm.javaClass.getMethod("removeTask", Int::class.javaPrimitiveType)
                    val res = removeTask.invoke(atm, targetTaskId) as? Boolean ?: true
                    Log.i(TAG, "IActivityTaskManager.removeTask($targetTaskId) executed: $res")
                    evicted = true
                } catch (e: Exception) {
                    Log.d(TAG, "Direct removeTask binder failed", e)
                }
            }

            // 4. Secondary fallback: CLI remove
            if (!evicted && targetTaskId != null && targetTaskId > 0) {
                try {
                    execShizuku("am task remove $targetTaskId 2>/dev/null || am stack remove $targetTaskId 2>/dev/null")
                    evicted = true
                } catch (e: Exception) { logSwallowed(TAG, "closeViaShizuku:223", e) }
            }

            // 5. Ultimate fallback: force-stop
            if (!evicted && targetPkg != null) {
                try {
                    execShizuku("am force-stop $targetPkg")
                } catch (e: Exception) { logSwallowed(TAG, "closeViaShizuku:230", e) }
            }

            true
        } catch (e: Exception) {
            Log.e(TAG, "closeViaShizuku error", e)
            false
        }
    }

    internal fun isSystem(pkg: String): Boolean = TaskInspector.isSystem(pkg)

    fun switchToPreviousApp(context: Context) {
        Log.i(TAG, "switchToPreviousApp() invoked")
        exemptHiddenApis()

        if (isShizukuActive && switchViaShizuku(context)) {
            Log.i(TAG, "Task switched via Shizuku IActivityTaskManager")
            return
        }

        switchViaAccessibility(context)
    }

    private fun switchViaShizuku(context: Context): Boolean {
        return try {
            val myPkg = context.packageName
            val atm = getActivityTaskManager()

            if (atm != null) {
                    var recentTasksList: List<*>? = null
                    try {
                        val getRecentTasksMethod = atm.javaClass.methods.firstOrNull { it.name == "getRecentTasks" }
                        if (getRecentTasksMethod != null) {
                            val paramsCount = getRecentTasksMethod.parameterTypes.size
                            val rawResult = when (paramsCount) {
                                2 -> getRecentTasksMethod.invoke(atm, 10, 0x0002 /* RECENT_IGNORE_UNAVAILABLE */)
                                3 -> getRecentTasksMethod.invoke(atm, 10, 0x0002, 0)
                                else -> null
                            }
                            if (rawResult != null) {
                                val getListMethod = rawResult.javaClass.getMethod("getList")
                                recentTasksList = getListMethod.invoke(rawResult) as? List<*>
                            }
                        }
                    } catch (e: Exception) {
                        Log.d(TAG, "getRecentTasks reflection failed", e)
                    }

                    if (!recentTasksList.isNullOrEmpty()) {
                        val currentTaskItem = recentTasksList[0]
                        val currentTaskId = if (currentTaskItem != null) {
                            try { currentTaskItem.javaClass.getField("taskId").getInt(currentTaskItem) } catch (_: Exception) { -1 }
                        } else -1
                        val splitGroup = if (currentTaskId > 0) getSplitGroupTaskIds(currentTaskId) else emptySet()

                        var targetTaskId: Int? = null
                        for (i in 1 until recentTasksList.size) {
                            val taskInfo = recentTasksList[i] ?: continue
                            val taskIdField = taskInfo.javaClass.getField("taskId")
                            val tId = taskIdField.getInt(taskInfo)

                            if (tId in splitGroup) {
                                Log.w(TAG, "Skipping task $tId as split sibling of $currentTaskId")
                                continue
                            }

                            val baseIntentField = try { taskInfo.javaClass.getField("baseIntent") } catch (_: Exception) { null }
                            val intent = baseIntentField?.get(taskInfo) as? Intent
                            val pkg = intent?.component?.packageName ?: intent?.`package`

                            if (pkg != null && !isSystem(pkg) && pkg != myPkg && tId > 0) {
                                targetTaskId = tId
                                break
                            } else if (tId > 0 && targetTaskId == null) {
                                targetTaskId = tId
                            }
                        }

                        if (targetTaskId != null && targetTaskId > 0) {
                            val startActivityFromRecentsMethod = atm.javaClass.methods.firstOrNull { it.name == "startActivityFromRecents" }
                            if (startActivityFromRecentsMethod != null) {
                                val pCount = startActivityFromRecentsMethod.parameterTypes.size
                                when (pCount) {
                                    2 -> startActivityFromRecentsMethod.invoke(atm, targetTaskId, null)
                                    3 -> startActivityFromRecentsMethod.invoke(atm, targetTaskId, null, null)
                                }
                                return true
                            }

                            val moveTaskToFrontMethod = atm.javaClass.methods.firstOrNull { it.name == "moveTaskToFront" }
                            if (moveTaskToFrontMethod != null) {
                                val pCount = moveTaskToFrontMethod.parameterTypes.size
                                when (pCount) {
                                    2 -> moveTaskToFrontMethod.invoke(atm, targetTaskId, 0)
                                    3 -> moveTaskToFrontMethod.invoke(atm, targetTaskId, 0, null)
                                }
                                return true
                            }
                        }
                    }
                }

            val proc = execShizuku("input keyevent KEYCODE_APP_SWITCH && sleep 0.04 && input keyevent KEYCODE_APP_SWITCH")
            proc.waitForOrKill()
            true
        } catch (e: Exception) {
            Log.e(TAG, "switchViaShizuku error", e)
            false
        }
    }

    private fun switchViaAccessibility(context: Context) {
        val service = com.sbf.lightspeed.LightspeedAccessibilityService.instance
        if (service != null) {
            service.performGlobalAction(android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_RECENTS)
            Handler(Looper.getMainLooper()).postDelayed({
                service.performGlobalAction(android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_RECENTS)
            }, 80L)
        }
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

            if (!isSplittableTask(context, targetTaskId, targetPkg)) {
                Handler(Looper.getMainLooper()).post {
                    Toast.makeText(context, "Open an app first to use split", Toast.LENGTH_SHORT).show()
                }
                return@execute
            }

            val service = com.sbf.lightspeed.LightspeedAccessibilityService.instance
            var splitSuccess = false
            var lastFailureReason = "unknown"

            // 1. FIRST try Shizuku WMShell moveToSideStage (proven on Android 12+ / WMShell / Transsion)
            if (isShizukuActive && targetTaskId != null && targetTaskId > 0) {
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
            if (!splitSuccess && Build.VERSION.SDK_INT >= Build.VERSION_CODES.N && service != null) {
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
            } else if (!splitSuccess) {
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
            if (!splitSuccess && targetTaskId != null && targetTaskId > 0) {
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

    fun splitWithApp(context: Context, component: String) {
        Log.w(TAG, "splitWithApp() invoked for component=$component")
        exemptHiddenApis()

        executor.execute {
            val targetBPackageName = if (component.contains("/")) {
                component.substringBefore("/")
            } else {
                component
            }

            // a) read current top task via getTopForegroundTaskInfo(context) and remember its taskId (taskA)
            val taskInfoA = getTopForegroundTaskInfo(context)
            val taskA = taskInfoA.taskId
            val pkgA = taskInfoA.packageName
            Log.w(TAG, "splitWithApp step a: taskA=$taskA pkg=$pkgA")

            if (!isSplittableTask(context, taskA, pkgA)) {
                Handler(Looper.getMainLooper()).post {
                    Toast.makeText(context, "Open an app first to use split", Toast.LENGTH_SHORT).show()
                }
                return@execute
            }

            // b) launch B with execShizuku("am start -n <component> -f 0x10000000") and wait for it with waitForOrKill
            if (ShellArgGuard.isComponent(component)) {
                val startCmd = "am start -n '$component' -f 0x10000000"
                Log.w(TAG, "splitWithApp step b: launching B via $startCmd")
                val pB = execShizuku(startCmd)
                val startCode = pB.waitForOrKill(5000L)
                Log.w(TAG, "splitWithApp step b: am start exitCode=$startCode")
            } else {
                Log.w(TAG, "splitWithApp step b: invalid component name, skipping launch")
            }

            // c) poll getTopForegroundTaskInfo every 150ms for up to 2000ms until packageName equals B's package and taskId != taskA
            val startPoll = SystemClock.uptimeMillis()
            var taskInfoB: ForegroundTaskInfo? = null
            while (SystemClock.uptimeMillis() - startPoll < 2000L) {
                val info = getTopForegroundTaskInfo(context)
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

            if (!isSplittableTask(context, bTaskId, bPkg)) {
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
                Log.w(TAG, "splitWithApp step d (attempt $attempt): wmshell moveToSideStage code=$codeWm text=$returnedText for bTaskId=$bTaskId")

                val cmdFailed = codeWm != 0 ||
                    textWm.contains("Invalid", ignoreCase = true) ||
                    textWm.contains("Error", ignoreCase = true)

                if (cmdFailed) {
                    lastReason = if (returnedText.isNotBlank()) returnedText else "cmd exit code $codeWm"
                    continue
                }

                val (verified, line) = verifyWindowModePolling(bTaskId, bPkg, "split")
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

    private fun showSplitAppPicker(context: Context) {
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

                synchronized(this@ElevatedTaskCloser) {
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
            synchronized(this@ElevatedTaskCloser) {
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

    @Deprecated("Use launchInPopup(context, style) instead")
    fun launchInFreeform(context: Context) {
        PopupLauncher.launchInFreeform(context)
    }

    fun launchInPopup(context: Context, style: String = LightspeedPreferences.POPUP_STYLE_NATIVE) {
        PopupLauncher.launchInPopup(context, style)
    }
}

