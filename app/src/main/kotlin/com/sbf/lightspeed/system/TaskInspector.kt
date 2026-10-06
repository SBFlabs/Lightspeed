package com.sbf.lightspeed.system

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.SystemClock
import android.util.Log
import java.io.BufferedReader
import java.io.InputStreamReader

object TaskInspector {
    private const val TAG = ElevatedTaskCloser.TAG

    fun isSystem(pkg: String): Boolean {
        val p = pkg.lowercase()
        return p.contains("launcher") || p.contains("quickstep") || p.contains("systemui")
    }

    data class ForegroundTaskInfo(
        val taskId: Int? = null,
        val packageName: String? = null,
        val componentName: String? = null
    )

    fun getTopForegroundTaskInfo(context: Context): ForegroundTaskInfo {
        val myPkg = context.packageName
        var targetTaskId: Int? = null
        var targetPkg: String? = null
        var targetComponent: String? = null

        if (ElevatedTaskCloser.isShizukuActive) {
            try {
                val atm = ElevatedTaskCloser.getActivityTaskManager()
                if (atm != null) {
                    val getRecentTasksMethod = atm.javaClass.methods.firstOrNull { it.name == "getRecentTasks" }
                    if (getRecentTasksMethod != null) {
                        val paramsCount = getRecentTasksMethod.parameterTypes.size
                        val rawResult = when (paramsCount) {
                            2 -> getRecentTasksMethod.invoke(atm, 5, 0x0002)
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
                                    val baseIntentField = try { item.javaClass.getField("baseIntent") } catch (e: Exception) {
                                        Log.w(TAG, "No baseIntent field in RecentTaskInfo", e)
                                        null
                                    }
                                    val intent = baseIntentField?.get(item) as? Intent
                                    val topActivityField = try { item.javaClass.getField("topActivity") } catch (e: Exception) {
                                        Log.w(TAG, "No topActivity field in RecentTaskInfo", e)
                                        null
                                    }
                                    val topActivity = topActivityField?.get(item) as? ComponentName
                                    val realActivityField = try { item.javaClass.getField("realActivity") } catch (e: Exception) {
                                        Log.w(TAG, "No realActivity field in RecentTaskInfo", e)
                                        null
                                    }
                                    val realActivity = realActivityField?.get(item) as? ComponentName

                                    val resolvedComp = realActivity ?: topActivity ?: intent?.component
                                    val pkg = resolvedComp?.packageName ?: intent?.`package`

                                    if (pkg != null && !isSystem(pkg) && pkg != myPkg && tId > 0) {
                                        targetTaskId = tId
                                        targetPkg = pkg
                                        targetComponent = resolvedComp?.flattenToShortString()
                                            ?: context.packageManager.getLaunchIntentForPackage(pkg)?.component?.flattenToShortString()
                                        break
                                    }
                                }
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "Fast binder task lookup failed", e)
            }
        }

        if (targetTaskId == null && ElevatedTaskCloser.isShizukuActive) {
            try {
                val process = ElevatedTaskCloser.execShizuku("dumpsys activity top 2>&1 | grep -E 'TASK|ACTIVITY|mResumedActivity|topResumedActivity'")
                process?.let { p ->
                    val text = p.readTextOrKill().orEmpty()
                    for (line in text.lineSequence()) {
                        val tm = Regex("""TASK\s+.*?(?<![A-Za-z0-9])id=(\d+)""").find(line)
                        if (tm != null && targetTaskId == null) {
                            targetTaskId = tm.groupValues[1].toIntOrNull()
                        }
                        val am = Regex("""(ACTIVITY|mResumedActivity|topResumedActivity)\s+([a-zA-Z0-9_.]+)/([a-zA-Z0-9_.]+)""").find(line)
                        if (am != null && targetPkg == null) {
                            val pkg = am.groupValues[2]
                            val act = am.groupValues[3]
                            if (pkg != myPkg && !isSystem(pkg)) {
                                targetPkg = pkg
                                targetComponent = "$pkg/$act"
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "dumpsys activity top lookup failed", e)
            }
        }

        if (targetPkg != null && targetComponent == null) {
            try {
                val launchIntent = context.packageManager.getLaunchIntentForPackage(targetPkg)
                targetComponent = launchIntent?.component?.flattenToShortString()
            } catch (e: Exception) {
                Log.w(TAG, "Failed to resolve launch intent component for $targetPkg", e)
            }
        }

        return ForegroundTaskInfo(targetTaskId, targetPkg, targetComponent)
    }

    fun getTaskLinesFromDumpsys(): List<String> {
        val process = ElevatedTaskCloser.execShizuku("dumpsys activity activities 2>&1") ?: return emptyList()
        val lines = mutableListOf<String>()
        try {
            BufferedReader(InputStreamReader(process.inputStream)).useLines { seq ->
                for (line in seq) {
                    if (line.contains("Task{")) {
                        lines.add(line.trim())
                        if (lines.size >= 40) break
                    }
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed reading dumpsys activity activities", e)
        } finally {
            try { process.destroy() } catch (e: Exception) { logSwallowed(TAG, "getTaskLinesFromDumpsys:495", e) }
        }
        return lines
    }

    fun isSplittableTask(context: Context, taskId: Int?, pkg: String?): Boolean {
        if (taskId == null || taskId <= 0) {
            Log.w(TAG, "isSplittableTask refusal: taskId is invalid (taskId=$taskId, pkg=$pkg)")
            return false
        }
        if (pkg == "com.android.systemui") {
            Log.w(TAG, "isSplittableTask refusal: package is SystemUI (taskId=$taskId, pkg=$pkg)")
            return false
        }

        try {
            val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
            val resolveInfo = context.packageManager.resolveActivity(intent, PackageManager.MATCH_DEFAULT_ONLY)
            val defaultLauncherPkg = resolveInfo?.activityInfo?.packageName
            if (!pkg.isNullOrBlank() && defaultLauncherPkg != null && pkg == defaultLauncherPkg) {
                Log.w(TAG, "isSplittableTask refusal: package is default home launcher '$pkg' (taskId=$taskId)")
                return false
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to resolve default home launcher", e)
        }

        val taskLines = getTaskLinesFromDumpsys()
        val matchedLine = taskLines.firstOrNull {
            it.contains("#$taskId ") || it.contains("#$taskId}") || Regex("""(?<![A-Za-z0-9])id=$taskId""").containsMatchIn(it)
        }
        if (matchedLine != null && matchedLine.contains("type=home")) {
            Log.w(TAG, "isSplittableTask refusal: dumpsys line contains type=home '$matchedLine' (taskId=$taskId, pkg=$pkg)")
            return false
        }

        return true
    }

    fun isTaskInSplit(taskId: Int): Boolean {
        val taskLines = getTaskLinesFromDumpsys()
        val matchedLine = taskLines.firstOrNull {
            it.contains("#$taskId ") || it.contains("#$taskId}")
        }
        return matchedLine?.contains("mode=multi-window") == true
    }

    fun getSplitGroupTaskIds(taskId: Int): Set<Int> {
        val taskLines = getTaskLinesFromDumpsys()
        val matchedLine = taskLines.firstOrNull {
            it.contains("#$taskId ") || it.contains("#$taskId}")
        } ?: return emptySet()
        if (!matchedLine.contains("mode=multi-window")) return emptySet()

        val rootMatch = Regex("""rootTaskId=(\d+)""").find(matchedLine) ?: return emptySet()
        val rootId = rootMatch.groupValues[1]

        val result = mutableSetOf<Int>()
        result.add(taskId)
        for (line in taskLines) {
            if (line.contains("rootTaskId=$rootId") && line.contains("type=standard")) {
                val idMatch = Regex("""#(\d+)""").find(line)
                if (idMatch != null) {
                    idMatch.groupValues[1].toIntOrNull()?.let { result.add(it) }
                }
            }
        }
        return result
    }

    fun verifyWindowMode(
        taskId: Int?,
        packageName: String?,
        expectedMode: String
    ): Pair<Boolean, String?> {
        val taskLines = getTaskLinesFromDumpsys()
        if (taskLines.isEmpty()) {
            Log.w(TAG, "dumpsys activity activities returned no Task lines (Shizuku active=${ElevatedTaskCloser.isShizukuActive})")
            return Pair(false, "dumpsys returned no Task lines")
        }

        var matchedLine: String? = null
        if (taskId != null && taskId > 0) {
            matchedLine = taskLines.firstOrNull {
                it.contains("#$taskId ") || it.contains("#$taskId}") || Regex("""(?<![A-Za-z0-9])id=$taskId""").containsMatchIn(it)
            }
        }
        if (matchedLine == null && !packageName.isNullOrBlank()) {
            matchedLine = taskLines.firstOrNull { it.contains(packageName) }
        }

        val topTaskLine = taskLines.firstOrNull { !it.contains("type=home") } ?: taskLines.firstOrNull()
        val targetLine = matchedLine ?: topTaskLine

        if (targetLine != null) {
            Log.w(TAG, "Matched dumpsys Task line: $targetLine")
            PopupDiagnostics.lastTopTaskLine = targetLine

            val modeMatches = if (expectedMode == "freeform") {
                targetLine.contains("mode=freeform", ignoreCase = true)
            } else {
                targetLine.contains("mode=multi-window", ignoreCase = true) ||
                    targetLine.contains("(multi-window)", ignoreCase = true)
            }

            return if (modeMatches) {
                Pair(true, targetLine)
            } else {
                Log.w(TAG, "Task line does not contain expected mode ($expectedMode): $targetLine")
                Pair(false, "task not in $expectedMode: $targetLine")
            }
        }

        return Pair(false, "No target task found in dumpsys")
    }

    fun verifyWindowModePolling(
        taskId: Int?,
        packageName: String?,
        expectedMode: String,
        maxWaitMs: Long = 500L,
        stepMs: Long = 150L
    ): Pair<Boolean, String?> {
        val start = SystemClock.uptimeMillis()
        var lastResult: Pair<Boolean, String?> = Pair(false, null)
        while (SystemClock.uptimeMillis() - start < maxWaitMs) {
            val elapsed = SystemClock.uptimeMillis() - start
            val remaining = maxWaitMs - elapsed
            if (remaining <= 0) break
            val sleepTime = minOf(stepMs, remaining)
            try {
                Thread.sleep(sleepTime)
            } catch (_: InterruptedException) {
                return verifyWindowMode(taskId, packageName, expectedMode)
            }
            lastResult = verifyWindowMode(taskId, packageName, expectedMode)
            if (lastResult.first) {
                return lastResult
            }
        }
        return lastResult
    }
}
