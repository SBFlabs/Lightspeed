package com.sbf.lightspeed.system

import android.content.ComponentName
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

        val service = LightspeedAccessibilityService.instance
        val activeWindowPkg = try { service?.rootInActiveWindow?.packageName?.toString() } catch (_: Exception) { null }
        if (!activeWindowPkg.isNullOrBlank() && TaskInspector.isLauncherOrSystem(context, activeWindowPkg)) {
            Log.i(TAG, "Current active window is launcher/system ($activeWindowPkg), nothing to close")
            Handler(Looper.getMainLooper()).post {
                Toast.makeText(context, "Nothing to close here", Toast.LENGTH_SHORT).show()
            }
            return
        }

        val fgInfo = ElevatedTaskCloser.getTopForegroundTaskInfo(context)
        val frontPkg = fgInfo.packageName ?: LightspeedAccessibilityService.lastAppPackage

        if (!frontPkg.isNullOrBlank() && TaskInspector.isLauncherOrSystem(context, frontPkg)) {
            Log.i(TAG, "Foreground is launcher/system ($frontPkg), nothing to close")
            Handler(Looper.getMainLooper()).post {
                Toast.makeText(context, "Nothing to close here", Toast.LENGTH_SHORT).show()
            }
            return
        }

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

        if (useShizuku) {
            val closedPkgHolder = arrayOfNulls<String>(1)
            if (closeViaShizuku(context, closedPkgHolder)) {
                Log.i(TAG, "Task dismissed and evicted from Recents via Shizuku: ${closedPkgHolder[0]}")
                com.sbf.lightspeed.system.display.LightspeedDisplayWellnessResetEngine.onAppClosedFromRecents(context, closedPkgHolder[0])
                com.sbf.lightspeed.LightspeedAccessibilityService.instance?.scheduleGeometryResync()
                return
            }
        }

        if (useRecents) {
            val service = com.sbf.lightspeed.LightspeedAccessibilityService.instance
            if (service != null) {
                com.sbf.lightspeed.system.display.LightspeedDisplayWellnessResetEngine.onAppClosedFromRecents(context)
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

    /**
     * Closes [targetPkg] specifically and removes its task from Recents so Android naturally
     * reveals whatever app or window was underneath prior to opening [targetPkg]. Uses Shizuku
     * direct task eviction when active, otherwise falls back to Accessibility Recents dismissal
     * (invoking [onFallbackHome] only if eviction cannot succeed).
     */
    fun closePackageWithReturn(
        context: Context,
        targetPkg: String,
        onFallbackHome: () -> Unit
    ) {
        val myPkg = context.packageName
        if (targetPkg.isBlank() || targetPkg == myPkg || TaskInspector.isLauncherOrSystem(context, targetPkg)) {
            onFallbackHome()
            return
        }
        val a11yOnly = ShizukuGate.isAccessibilityOnly()
        val useShizuku = !a11yOnly && ElevatedTaskCloser.isShizukuActive
        if (useShizuku) {
            ElevatedTaskCloser.executor.execute {
                val evicted = closeSpecificPackageViaShizuku(context, targetPkg)
                if (evicted) {
                    com.sbf.lightspeed.system.display.LightspeedDisplayWellnessResetEngine.onAppClosedFromRecents(context, targetPkg)
                    com.sbf.lightspeed.LightspeedAccessibilityService.instance?.scheduleGeometryResync()
                } else {
                    Handler(Looper.getMainLooper()).post(onFallbackHome)
                }
            }
            return
        }
        val service = com.sbf.lightspeed.LightspeedAccessibilityService.instance
        if (service != null) {
            com.sbf.lightspeed.system.display.LightspeedDisplayWellnessResetEngine.onAppClosedFromRecents(context, targetPkg)
            RecentsFallbackCloser.closeViaRecentsFallback(
                context = context,
                service = service,
                targetPkgOverride = targetPkg,
                returnBackAfterDismiss = true,
                onFailed = { Handler(Looper.getMainLooper()).post(onFallbackHome) }
            )
        } else {
            onFallbackHome()
        }
    }

    private fun extractTaskPackage(item: Any?): String? {
        if (item == null) return null
        val baseIntent = try { item.javaClass.getField("baseIntent").get(item) as? Intent } catch (_: Exception) { null }
        val topActivity = try { item.javaClass.getField("topActivity").get(item) as? ComponentName } catch (_: Exception) { null }
        val realActivity = try { item.javaClass.getField("realActivity").get(item) as? ComponentName } catch (_: Exception) { null }
        val resolvedComp = realActivity ?: topActivity ?: baseIntent?.component
        return resolvedComp?.packageName ?: baseIntent?.`package`
    }

    private fun evictTaskViaShizuku(
        targetPkg: String,
        targetTaskId: Int?,
        atm: Any?
    ): Boolean {
        var evicted = false
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
        if (!evicted && targetTaskId != null && targetTaskId > 0) {
            try {
                val cliProc = ElevatedTaskCloser.execShizuku("am task remove $targetTaskId 2>/dev/null || am stack remove $targetTaskId 2>/dev/null")
                val cliCode = cliProc.waitForOrKill(3000L)
                evicted = (cliCode == 0)
                Log.i(TAG, "CLI task remove exit code: $cliCode")
            } catch (e: Exception) { logSwallowed(TAG, "closeViaShizuku:223", e) }
        }
        var forceStopped = false
        if (!evicted && (targetTaskId == null || targetTaskId <= 0)) {
            try {
                val fsProc = ElevatedTaskCloser.execShizuku("am force-stop $targetPkg")
                val fsCode = fsProc.waitForOrKill(3000L)
                forceStopped = (fsCode == 0)
                Log.i(TAG, "force-stop exit code: $fsCode")
            } catch (e: Exception) { logSwallowed(TAG, "closeViaShizuku:230", e) }
        }
        return evicted || forceStopped
    }

    private fun closeSpecificPackageViaShizuku(context: Context, packageName: String): Boolean {
        return try {
            ElevatedTaskCloser.exemptHiddenApis()
            val atm = ElevatedTaskCloser.getActivityTaskManager()
            var targetTaskId: Int? = null
            if (atm != null) {
                val getRecentTasksMethod = atm.javaClass.methods.firstOrNull { it.name == "getRecentTasks" }
                if (getRecentTasksMethod != null) {
                    val rawResult = when (getRecentTasksMethod.parameterTypes.size) {
                        2 -> getRecentTasksMethod.invoke(atm, 12, 0x0002)
                        3 -> getRecentTasksMethod.invoke(atm, 12, 0x0002, 0)
                        else -> null
                    }
                    val recentTasks = rawResult?.let { res ->
                        res.javaClass.getMethod("getList").invoke(res) as? List<*>
                    }.orEmpty()
                    val matched = recentTasks.firstOrNull { extractTaskPackage(it) == packageName }
                    targetTaskId = try { matched?.javaClass?.getField("taskId")?.getInt(matched) } catch (_: Exception) { null }
                }
            }
            evictTaskViaShizuku(packageName, targetTaskId, atm)
        } catch (e: Exception) {
            Log.e(TAG, "closeSpecificPackageViaShizuku error", e)
            false
        }
    }

    private fun closeViaShizuku(context: Context, outPkg: Array<String?>? = null): Boolean {
        return try {
            val myPkg = context.packageName

            var targetTaskId: Int? = null
            var targetPkg: String? = null
            var atm: Any? = null

            // 1. Ultra-fast target resolution via direct IActivityTaskManager Binder (<2ms)
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
                                val topItem = recentTasksList[0]
                                if (TaskInspector.isLauncherTask(context, topItem)) {
                                    Log.i(TAG, "Top recent task is launcher/system, user is on home screen")
                                    Handler(Looper.getMainLooper()).post {
                                        Toast.makeText(context, "Nothing to close here", Toast.LENGTH_SHORT).show()
                                    }
                                    return true
                                }
                                val tId = topItem?.javaClass?.getField("taskId")?.getInt(topItem)
                                val baseIntentField = try { topItem?.javaClass?.getField("baseIntent") } catch (_: Exception) { null }
                                val intent = baseIntentField?.get(topItem) as? Intent
                                val topActivityField = try { topItem?.javaClass?.getField("topActivity") } catch (_: Exception) { null }
                                val topActivity = topActivityField?.get(topItem) as? ComponentName
                                val realActivityField = try { topItem?.javaClass?.getField("realActivity") } catch (_: Exception) { null }
                                val realActivity = realActivityField?.get(topItem) as? ComponentName
                                val resolvedComp = realActivity ?: topActivity ?: intent?.component
                                val pkg = resolvedComp?.packageName ?: intent?.`package`

                                if (pkg != null && !TaskInspector.isLauncherOrSystem(context, pkg) && pkg != myPkg && tId != null && tId > 0) {
                                    targetTaskId = tId
                                    targetPkg = pkg
                                }
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                Log.d(TAG, "Fast binder task lookup failed", e)
            }

            // Fallback 1: Lightweight atomic topResumedActivity dump (~30ms)
            if (targetTaskId == null && targetPkg == null) {
                val actProcess = ElevatedTaskCloser.execShizuku("dumpsys activity activities 2>&1 | grep -E -m 1 'topResumedActivity|mResumedActivity' || dumpsys window 2>&1 | grep -m 1 'mFocusedApp=ActivityRecord'")
                actProcess?.let { p ->
                    val text = p.readTextOrKill().orEmpty()
                    for (line in text.lineSequence()) {
                        val parsed = TaskInspector.parseResumedActivityRecord(line)
                        if (parsed != null) {
                            val (pkg, _, tId) = parsed
                            if (TaskInspector.isLauncherOrSystem(context, pkg) || pkg == myPkg) {
                                Log.i(TAG, "topResumedActivity is launcher/system ($pkg), nothing to close")
                                Handler(Looper.getMainLooper()).post {
                                    Toast.makeText(context, "Nothing to close here", Toast.LENGTH_SHORT).show()
                                }
                                return true
                            }
                            targetTaskId = tId
                            targetPkg = pkg
                            break
                        }
                    }
                }
            }

            if (targetTaskId == null && targetPkg == null) {
                Log.i(TAG, "No foreground non-system app found to evict")
                Handler(Looper.getMainLooper()).post {
                    Toast.makeText(context, "Nothing to close here", Toast.LENGTH_SHORT).show()
                }
                return true
            }

            // HARD SAFETY LOCK: Never ever evict a launcher, system UI, or Lightspeed!
            if (targetPkg == null || TaskInspector.isLauncherOrSystem(context, targetPkg) || targetPkg == myPkg) {
                Log.w(TAG, "Safety lock prevented evicting protected package: $targetPkg (TaskId: $targetTaskId)")
                Handler(Looper.getMainLooper()).post {
                    Toast.makeText(context, "Nothing to close here", Toast.LENGTH_SHORT).show()
                }
                return true
            }

            Log.i(TAG, "Targeting focused app: $targetPkg (TaskId: $targetTaskId)")
            val success = evictTaskViaShizuku(targetPkg, targetTaskId, atm)
            if (success && outPkg != null && outPkg.isNotEmpty()) {
                outPkg[0] = targetPkg
            }
            success
        } catch (e: Exception) {
            Log.e(TAG, "closeViaShizuku error", e)
            false
        }
    }
}
