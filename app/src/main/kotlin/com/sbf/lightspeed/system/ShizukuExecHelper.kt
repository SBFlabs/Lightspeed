package com.sbf.lightspeed.system

import android.app.Activity
import android.content.Context
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import android.view.InputDevice
import android.view.KeyEvent
import android.view.MotionEvent
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

    fun injectKeyEvent(keyCode: Int): Boolean {
        if (!ElevatedTaskCloser.isShizukuActive) return false
        return try {
            val rawBinder = SystemServiceHelper.getSystemService("input")
                ?: SystemServiceHelper.getSystemService(Context.INPUT_SERVICE)
                ?: return false
            val wrapped = ShizukuBinderWrapper(rawBinder)
            val stubClass = Class.forName("android.hardware.input.IInputManager\$Stub")
            val asInterface = stubClass.getMethod("asInterface", IBinder::class.java)
            val inputManager = asInterface.invoke(null, wrapped) ?: return false

            val injectMethod = inputManager.javaClass.methods.firstOrNull { it.name == "injectInputEvent" }
            if (injectMethod != null) {
                val now = SystemClock.uptimeMillis()
                val down = KeyEvent(now, now, KeyEvent.ACTION_DOWN, keyCode, 0)
                val up = KeyEvent(now, now, KeyEvent.ACTION_UP, keyCode, 0)
                // mode 0 = INJECT_INPUT_EVENT_MODE_ASYNC
                injectMethod.invoke(inputManager, down, 0)
                injectMethod.invoke(inputManager, up, 0)
                true
            } else {
                execShizuku("input keyevent $keyCode") != null
            }
        } catch (e: Exception) {
            Log.w(TAG, "Direct input injection failed, falling back to shell", e)
            execShizuku("input keyevent $keyCode") != null
        }
    }

    fun injectBack(context: Context? = null): Boolean {
        val powerProc = LightspeedPowerKeyEngine.activeProc
        if (powerProc != null) {
            PowerGrabHelper.sendBack(powerProc)
            return true
        }
        if (ElevatedTaskCloser.isShizukuActive) {
            try {
                val rawBinder = SystemServiceHelper.getSystemService("input")
                    ?: SystemServiceHelper.getSystemService(Context.INPUT_SERVICE)
                if (rawBinder != null) {
                    val wrapped = ShizukuBinderWrapper(rawBinder)
                    val stubClass = Class.forName("android.hardware.input.IInputManager\$Stub")
                    val asInterface = stubClass.getMethod("asInterface", IBinder::class.java)
                    val inputManager = asInterface.invoke(null, wrapped)
                    val injectMethod = inputManager?.javaClass?.methods?.firstOrNull { it.name == "injectInputEvent" }
                    if (injectMethod != null) {
                        val now = SystemClock.uptimeMillis()
                        val down = KeyEvent(
                            now, now, KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_BACK, 0, 0,
                            android.view.KeyCharacterMap.VIRTUAL_KEYBOARD, 0, 0,
                            InputDevice.SOURCE_KEYBOARD
                        )
                        val up = KeyEvent(
                            now, now + 15, KeyEvent.ACTION_UP, KeyEvent.KEYCODE_BACK, 0, 0,
                            android.view.KeyCharacterMap.VIRTUAL_KEYBOARD, 0, 0,
                            InputDevice.SOURCE_KEYBOARD
                        )
                        injectMethod.invoke(inputManager, down, 0)
                        injectMethod.invoke(inputManager, up, 0)
                        return true
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "Direct back input injection failed, falling back to shell", e)
            }
            return execShizuku("input keyevent 4") != null
        }
        return false
    }

    fun injectTapEvent(x: Float, y: Float): Boolean {
        if (!ElevatedTaskCloser.isShizukuActive) return false
        return try {
            val rawBinder = SystemServiceHelper.getSystemService("input")
                ?: SystemServiceHelper.getSystemService(Context.INPUT_SERVICE)
                ?: return false
            val wrapped = ShizukuBinderWrapper(rawBinder)
            val stubClass = Class.forName("android.hardware.input.IInputManager\$Stub")
            val asInterface = stubClass.getMethod("asInterface", IBinder::class.java)
            val inputManager = asInterface.invoke(null, wrapped) ?: return false

            val injectMethod = inputManager.javaClass.methods.firstOrNull { it.name == "injectInputEvent" }
            if (injectMethod != null) {
                val now = SystemClock.uptimeMillis()
                val down = MotionEvent.obtain(now, now, MotionEvent.ACTION_DOWN, x, y, 0).apply {
                    source = InputDevice.SOURCE_TOUCHSCREEN
                }
                val up = MotionEvent.obtain(now, now + 10, MotionEvent.ACTION_UP, x, y, 0).apply {
                    source = InputDevice.SOURCE_TOUCHSCREEN
                }
                injectMethod.invoke(inputManager, down, 0)
                injectMethod.invoke(inputManager, up, 0)
                down.recycle()
                up.recycle()
                true
            } else {
                execShizuku("input tap ${x.toInt()} ${y.toInt()}") != null
            }
        } catch (e: Exception) {
            Log.w(TAG, "Direct tap injection failed", e)
            execShizuku("input tap ${x.toInt()} ${y.toInt()}") != null
        }
    }

    fun toastShizukuNeeded(context: Context, feature: String) {
        Handler(Looper.getMainLooper()).post {
            val msg = if (!Shizuku.pingBinder()) "$feature needs Shizuku, which isn't running. Start it, then try again." else "$feature needs Shizuku: authorize Lightspeed in your Shizuku app or fork"
            Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
        }
    }
}
