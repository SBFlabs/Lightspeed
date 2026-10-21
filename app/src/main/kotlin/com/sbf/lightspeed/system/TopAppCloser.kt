package com.sbf.lightspeed.system

import android.content.Context
import com.sbf.lightspeed.LightspeedAccessibilityService
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.widget.Toast
import rikka.shizuku.Shizuku

internal object TopAppCloser {
    private val TAG = ElevatedTaskCloser.TAG

    fun closeTopApp(context: Context) {
        Log.i(TAG, "closeTopApp() invoked")
        ElevatedTaskCloser.exemptHiddenApis()

        val frontPkg = LightspeedAccessibilityService.lastAppPackage
            ?: ElevatedTaskCloser.getTopForegroundTaskInfo(context).packageName

        val assignedMethods = if (!frontPkg.isNullOrBlank()) {
            ActionMethods.getAssignedMethodsForApp(context, "system:close_app", frontPkg)
        } else {
            emptySet()
        }

        val methods = if (assignedMethods.isNotEmpty()) assignedMethods else ActionMethods.getSelected(context, "system:close_app")
        val auto = ActionMethods.AUTO in methods
        val a11yOnly = ShizukuGate.isAccessibilityOnly()
        val useShizuku = !a11yOnly && ElevatedTaskCloser.isShizukuActive && (auto || ActionMethods.SHIZUKU in methods)
        val useRecents = auto || ActionMethods.RECENTS_DISMISS in methods
        Log.w(TAG, "close methods=$methods useShizuku=$useShizuku useRecents=$useRecents")

        if (useShizuku && closeViaShizuku(context)) {
            Log.i(TAG, "Task dismissed and evicted from Recents via Shizuku")
            com.sbf.lightspeed.LightspeedAccessibilityService.instance?.scheduleGeometryResync()
            return
        }

        if (useRecents) {
            val service = com.sbf.lightspeed.LightspeedAccessibilityService.instance
            if (service != null) {
                RecentsFallbackCloser.closeViaRecentsFallback(context, service)
            } else {
                Handler(Looper.getMainLooper()).post {
                    val msg = if (ShizukuGate.isAccessibilityOnly()) "This needs Shizuku. Turn off \"Accessibility Service only\" in System Override." else if (!Shizuku.pingBinder()) "Shizuku isn't running. Start it, then try again." else "Authorize Lightspeed in your Shizuku app or fork"
                    Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                }
            }
        } else {
            Handler(Looper.getMainLooper()).post {
                Toast.makeText(context, "Close isn't supported with the selected methods", Toast.LENGTH_SHORT).show()
            }
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
                atm = ElevatedTaskCloser.getActivityTaskManager()

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
                                        if (pkg != null && !ElevatedTaskCloser.isSystem(pkg) && pkg != myPkg && tId > 0) {
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
                val actProcess = ElevatedTaskCloser.execShizuku("dumpsys activity top 2>&1 | grep -E 'TASK|ACTIVITY' | head -n 4")
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
                            if (pkg != myPkg && !ElevatedTaskCloser.isSystem(pkg)) {
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
                    val res = removeTask.invoke(atm, targetTaskId) as? Boolean
                    Log.i(TAG, "IActivityTaskManager.removeTask($targetTaskId) executed: $res")
                    evicted = (res == true)
                } catch (e: Exception) {
                    Log.d(TAG, "Direct removeTask binder failed", e)
                }
            }

            // 4. Secondary fallback: CLI remove (waits for the result)
            if (!evicted && targetTaskId != null && targetTaskId > 0) {
                try {
                    val cliProc = ElevatedTaskCloser.execShizuku("am task remove $targetTaskId 2>/dev/null || am stack remove $targetTaskId 2>/dev/null")
                    val cliCode = cliProc.waitForOrKill(3000L)
                    evicted = (cliCode == 0)
                    Log.i(TAG, "CLI task remove exit code: $cliCode")
                } catch (e: Exception) { logSwallowed(TAG, "closeViaShizuku:223", e) }
            }

            // 5. Ultimate fallback: force-stop (only when no task id was found, as before)
            var forceStopped = false
            if (!evicted && targetPkg != null && (targetTaskId == null || targetTaskId <= 0)) {
                try {
                    val fsProc = ElevatedTaskCloser.execShizuku("am force-stop $targetPkg")
                    val fsCode = fsProc.waitForOrKill(3000L)
                    forceStopped = (fsCode == 0)
                    Log.i(TAG, "force-stop exit code: $fsCode")
                } catch (e: Exception) { logSwallowed(TAG, "closeViaShizuku:230", e) }
            }

            evicted || forceStopped
        } catch (e: Exception) {
            Log.e(TAG, "closeViaShizuku error", e)
            false
        }
    }
}
