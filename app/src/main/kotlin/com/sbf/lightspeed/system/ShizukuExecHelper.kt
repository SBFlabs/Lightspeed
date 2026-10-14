package com.sbf.lightspeed.system

import android.app.Activity
import android.content.Context
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.util.Log
import android.widget.Toast
import rikka.shizuku.Shizuku
import rikka.shizuku.ShizukuBinderWrapper
import rikka.shizuku.SystemServiceHelper
import java.lang.reflect.Method

internal object ShizukuExecHelper {
    private val TAG = ElevatedTaskCloser.TAG
    @Volatile private var hiddenApiExempted = false

    fun requestPermission(activity: Activity) {
        if (Shizuku.pingBinder() && !ElevatedTaskCloser.isShizukuActive) {
            ShizukuGate.requestIfAllowed()
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
        if (!ElevatedTaskCloser.isShizukuActive) return null
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
        if (ShizukuGate.isAccessibilityOnly()) return null
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
        if (ShizukuGate.isAccessibilityOnly()) return null
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

    fun toastShizukuNeeded(context: Context, feature: String) {
        Handler(Looper.getMainLooper()).post {
            val msg = if (!Shizuku.pingBinder()) "$feature needs Shizuku, which isn't running. Start it, then try again." else "$feature needs Shizuku: authorize Lightspeed in your Shizuku app or fork"
            Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
        }
    }
}
