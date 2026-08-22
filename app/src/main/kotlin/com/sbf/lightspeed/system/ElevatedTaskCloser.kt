package com.sbf.lightspeed.system

import android.app.Activity
import android.content.Context
import android.content.pm.PackageManager
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.util.Log
import android.widget.Toast
import rikka.shizuku.Shizuku
import rikka.shizuku.ShizukuBinderWrapper
import rikka.shizuku.SystemServiceHelper
import java.io.BufferedReader
import java.io.InputStreamReader
import java.lang.reflect.Method

object ElevatedTaskCloser {
    private const val TAG = "ElevatedTaskCloser"
    const val SHIZUKU_REQ_CODE = 9001

    @Volatile
    private var hiddenApiExempted = false

    val isShizukuActive: Boolean
        get() = try {
            Shizuku.pingBinder() && Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
        } catch (_: Exception) { false }

    val isRootActive: Boolean
        get() = try {
            Runtime.getRuntime().exec(arrayOf("su", "-c", "id")).waitFor() == 0
        } catch (_: Exception) { false }

    fun requestPermission(activity: Activity) {
        if (Shizuku.pingBinder() && !isShizukuActive) {
            Shizuku.requestPermission(SHIZUKU_REQ_CODE)
        }
    }

    private fun exemptHiddenApis() {
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

    fun closeTopApp(context: Context) {
        Log.i(TAG, "closeTopApp() invoked")
        exemptHiddenApis()

        if (isShizukuActive && closeViaShizuku(context)) {
            Log.i(TAG, "Task dismissed and evicted from Recents via Shizuku")
            return
        }
        if (isRootActive && closeViaRoot(context)) {
            Log.i(TAG, "Task dismissed via Root")
            return
        }
        Handler(Looper.getMainLooper()).post {
            val msg = if (!Shizuku.pingBinder()) "Shizuku not running" else "Authorize Lightspeed in Shevery"
            Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
        }
    }

    private fun closeViaShizuku(context: Context): Boolean {
        return try {
            val process = execShizuku("dumpsys window displays | grep -E 'mFocusedApp|mCurrentFocus'") ?: return false
            val reader = BufferedReader(InputStreamReader(process.inputStream))
            var targetTaskId: Int? = null
            var targetPkg: String? = null
            val myPkg = context.packageName

            val focusedAppRegex = Regex("""ActivityRecord\{[^\}]*\s([a-zA-Z0-9_.]+)/[^\s\}]+\s+t(\d+)""", RegexOption.IGNORE_CASE)
            val currentFocusRegex = Regex("""Window\{[^\}]*\s+([a-zA-Z0-9_.]+)/""", RegexOption.IGNORE_CASE)

            reader.useLines { lines ->
                for (line in lines) {
                    val fam = focusedAppRegex.find(line)
                    if (fam != null) {
                        val (pkg, id) = fam.destructured
                        if (pkg != myPkg && !isSystem(pkg)) {
                            targetPkg = pkg
                            targetTaskId = id.toIntOrNull()
                            break
                        }
                    }
                    val cfm = currentFocusRegex.find(line)
                    if (cfm != null && targetPkg == null) {
                        val pkg = cfm.groupValues[1]
                        if (pkg != myPkg && !isSystem(pkg)) {
                            targetPkg = pkg
                        }
                    }
                }
            }
            process.waitFor()

            if (targetPkg == null) {
                Log.i(TAG, "No non-system window in focus (Home/Launcher active). Aborting.")
                return false
            }

            Log.i(TAG, "Targeting focused app: $targetPkg (TaskId: $targetTaskId)")

            // 1. Evict task record from Recents stack via IActivityTaskManager Binder (identical to swiping away in Recents)
            var evicted = false
            if (targetTaskId != null && targetTaskId > 0) {
                try {
                    val rawBinder = SystemServiceHelper.getSystemService("activity_task")
                        ?: SystemServiceHelper.getSystemService(Context.ACTIVITY_SERVICE)
                    if (rawBinder != null) {
                        val wrapped = ShizukuBinderWrapper(rawBinder)
                        val stubClass = Class.forName("android.app.IActivityTaskManager\$Stub")
                        val asInterface = stubClass.getMethod("asInterface", IBinder::class.java)
                        val atm = asInterface.invoke(null, wrapped)
                        if (atm != null) {
                            val removeTask = atm.javaClass.getMethod("removeTask", Int::class.javaPrimitiveType)
                            val res = removeTask.invoke(atm, targetTaskId) as? Boolean ?: true
                            Log.i(TAG, "IActivityTaskManager.removeTask($targetTaskId) invoked successfully. Result: $res")
                            evicted = true
                        }
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Binder removeTask invocation error", e)
                }
            }

            // 2. If binder removal was not possible, fallback to clean force-stop
            if (!evicted) {
                val killProcess = execShizuku("am force-stop " + targetPkg)
                killProcess?.waitFor()
                Log.i(TAG, "Fallback force-stop executed for $targetPkg")
            }

            true
        } catch (e: Exception) {
            Log.e(TAG, "closeViaShizuku error", e)
            false
        }
    }

    private fun isSystem(pkg: String): Boolean {
        val p = pkg.lowercase()
        return p.contains("launcher") || p.contains("quickstep") || p.contains("systemui")
    }

    private fun closeViaRoot(context: Context): Boolean {
        return try {
            val cmd = "dumpsys window displays | grep -oE 'mFocusedApp=ActivityRecord\\{[^}]* [^/]+/' | head -1 | cut -d ' ' -f 2 | tr -d '/' | xargs -r am force-stop"
            Runtime.getRuntime().exec(arrayOf("su", "-c", cmd)).waitFor() == 0
        } catch (_: Exception) { false }
    }
}
