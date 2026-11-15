package com.sbf.lightspeed.system

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.os.IInterface
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import android.view.InputDevice
import android.view.KeyCharacterMap
import android.view.KeyEvent
import android.view.MotionEvent
import android.widget.Toast
import com.sbf.lightspeed.system.sonic.SonicVoiceIsolationEngine
import rikka.shizuku.Shizuku
import rikka.shizuku.ShizukuBinderWrapper
import rikka.shizuku.SystemServiceHelper
import java.io.ByteArrayInputStream
import java.io.InputStream
import java.io.OutputStream
import java.lang.reflect.Constructor
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
        exemptHiddenApis()
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

    @Volatile private var cachedRemoteProcessCtor: Constructor<*>? = null
    @Volatile private var cachedShizukuAsInterface: Method? = null
    @Volatile private var cachedServiceNewProcess: Method? = null
    @Volatile private var cachedLegacyNewProcess: Method? = null

    private fun isStreamingCommand(argv: Array<out String>): Boolean {
        return argv.any { arg ->
            arg.contains("lsinputd") ||
                arg.contains("getevent") ||
                arg.contains("nohup ")
        }
    }

    private fun execViaBoundUserService(argv: Array<out String>): Process? {
        if (!SonicVoiceIsolationEngine.isBound) return null
        val svc = SonicVoiceIsolationEngine.userService ?: return null
        if (svc.asBinder()?.isBinderAlive == false) return null
        return try {
            val bundle = svc.execCommand(Array(argv.size) { argv[it] }, 8000L) ?: return null
            val exitCode = bundle.getInt("exitCode", -1)
            val stdout = bundle.getString("stdout").orEmpty()
            val stderr = bundle.getString("stderr").orEmpty()
            CompletedShizukuProcess(exitCode, stdout, stderr)
        } catch (e: Exception) {
            Log.w(TAG, "UserService execCommand fallback failed", e)
            null
        }
    }

    private fun execViaShizukuServiceBinder(argv: Array<out String>): Process? {
        return try {
            val binder = Shizuku.getBinder() ?: return null
            val asInterface = cachedShizukuAsInterface ?: Class.forName("moe.shizuku.server.IShizukuService\$Stub")
                .getMethod("asInterface", IBinder::class.java)
                .also { cachedShizukuAsInterface = it }
            val service = asInterface.invoke(null, binder) ?: return null
            val newProcMethod = cachedServiceNewProcess ?: service.javaClass.getMethod(
                "newProcess",
                Array<String>::class.java,
                Array<String>::class.java,
                String::class.java
            ).also { cachedServiceNewProcess = it }
            val remoteProc = newProcMethod.invoke(service, argv, null, null) ?: return null
            val ctor = cachedRemoteProcessCtor ?: Class.forName("rikka.shizuku.ShizukuRemoteProcess")
                .getDeclaredConstructor(Class.forName("moe.shizuku.server.IRemoteProcess"))
                .apply { isAccessible = true }
                .also { cachedRemoteProcessCtor = it }
            ctor.newInstance(remoteProc) as? Process
        } catch (_: Exception) {
            null
        }
    }

    private fun execViaLegacyReflection(argv: Array<out String>): Process? {
        return try {
            val m = cachedLegacyNewProcess ?: Shizuku::class.java.getDeclaredMethod(
                "newProcess",
                Array<String>::class.java,
                Array<String>::class.java,
                String::class.java
            ).apply { isAccessible = true }.also { cachedLegacyNewProcess = it }
            m.invoke(null, argv, null, null) as? Process
        } catch (e: Exception) {
            Log.e(TAG, "execShizuku reflection fallback error", e)
            null
        }
    }

    fun execShizuku(cmd: String): Process? {
        return execShizukuArgv("sh", "-c", cmd)
    }

    fun execShizukuArgv(vararg args: String): Process? {
        if (!ElevatedTaskCloser.isShizukuActive) return null
        val streaming = isStreamingCommand(args)
        if (!streaming) {
            execViaBoundUserService(args)?.let { return it }
        }
        execViaShizukuServiceBinder(args)?.let { return it }
        execViaLegacyReflection(args)?.let { return it }
        return if (streaming) null else execViaBoundUserService(args)
    }

    fun injectKeyEvent(keyCode: Int): Boolean {
        val (inputManager, injectMethod) = getCachedInputManager() ?: return false
        return try {
            val now = SystemClock.uptimeMillis()
            val down = KeyEvent(
                now, now, KeyEvent.ACTION_DOWN, keyCode, 0, 0,
                KeyCharacterMap.VIRTUAL_KEYBOARD, 0, KeyEvent.FLAG_FROM_SYSTEM,
                InputDevice.SOURCE_KEYBOARD
            )
            val up = KeyEvent(
                now, now + 10, KeyEvent.ACTION_UP, keyCode, 0, 0,
                KeyCharacterMap.VIRTUAL_KEYBOARD, 0, KeyEvent.FLAG_FROM_SYSTEM,
                InputDevice.SOURCE_KEYBOARD
            )
            // mode 0 = INJECT_INPUT_EVENT_MODE_ASYNC
            injectMethod.invoke(inputManager, down, 0)
            injectMethod.invoke(inputManager, up, 0)
            true
        } catch (e: Exception) {
            Log.w(TAG, "Direct key injection failed for keyCode=$keyCode", e)
            invalidateInputManagerCache()
            false
        }
    }

    fun injectBack(context: Context? = null): Boolean {
        val powerProc = LightspeedPowerKeyEngine.activeProc
        if (powerProc != null) {
            PowerGrabHelper.sendBack(powerProc)
            return true
        }
        return injectKeyEvent(KeyEvent.KEYCODE_BACK)
    }

    @Volatile
    private var cachedInputManager: Any? = null
    @Volatile
    private var cachedInjectMethod: Method? = null

    private fun invalidateInputManagerCache() {
        synchronized(this) {
            cachedInputManager = null
            cachedInjectMethod = null
        }
    }

    private fun getCachedInputManager(): Pair<Any, Method>? {
        if (!ElevatedTaskCloser.isShizukuActive) return null
        val mgr = cachedInputManager
        val method = cachedInjectMethod
        if (mgr != null && method != null) {
            val alive = (mgr as? IInterface)?.asBinder()?.isBinderAlive != false
            if (alive) return mgr to method
        }

        synchronized(this) {
            val m2 = cachedInputManager
            val meth2 = cachedInjectMethod
            if (m2 != null && meth2 != null) {
                val alive = (m2 as? IInterface)?.asBinder()?.isBinderAlive != false
                if (alive) return m2 to meth2
            }

            exemptHiddenApis()
            try {
                val rawBinder = SystemServiceHelper.getSystemService("input")
                    ?: SystemServiceHelper.getSystemService(Context.INPUT_SERVICE)
                    ?: return null
                val wrapped = ShizukuBinderWrapper(rawBinder)
                val stubClass = Class.forName("android.hardware.input.IInputManager\$Stub")
                val asInterface = stubClass.getMethod("asInterface", IBinder::class.java)
                val inputManager = asInterface.invoke(null, wrapped) ?: return null
                val inject = inputManager.javaClass.methods.firstOrNull { it.name == "injectInputEvent" } ?: return null
                cachedInputManager = inputManager
                cachedInjectMethod = inject
                return inputManager to inject
            } catch (e: Exception) {
                Log.w(TAG, "Failed to initialize IInputManager proxy", e)
                return null
            }
        }
    }

    fun injectPointerDown(downTime: Long, x: Float, y: Float): Boolean {
        val (inputManager, injectMethod) = getCachedInputManager() ?: return false
        val event = MotionEvent.obtain(downTime, downTime, MotionEvent.ACTION_DOWN, x, y, 0).apply {
            source = InputDevice.SOURCE_TOUCHSCREEN
        }
        return try {
            injectMethod.invoke(inputManager, event, 0)
            true
        } catch (e: Exception) {
            Log.w(TAG, "injectPointerDown failed", e)
            invalidateInputManagerCache()
            false
        } finally {
            event.recycle()
        }
    }

    fun injectPointerMove(downTime: Long, eventTime: Long, x: Float, y: Float): Boolean {
        val (inputManager, injectMethod) = getCachedInputManager() ?: return false
        val event = MotionEvent.obtain(downTime, eventTime, MotionEvent.ACTION_MOVE, x, y, 0).apply {
            source = InputDevice.SOURCE_TOUCHSCREEN
        }
        return try {
            injectMethod.invoke(inputManager, event, 0)
            true
        } catch (e: Exception) {
            Log.w(TAG, "injectPointerMove failed", e)
            invalidateInputManagerCache()
            false
        } finally {
            event.recycle()
        }
    }

    fun injectPointerUp(downTime: Long, eventTime: Long, x: Float, y: Float): Boolean {
        val (inputManager, injectMethod) = getCachedInputManager() ?: return false
        val event = MotionEvent.obtain(downTime, eventTime, MotionEvent.ACTION_UP, x, y, 0).apply {
            source = InputDevice.SOURCE_TOUCHSCREEN
        }
        return try {
            injectMethod.invoke(inputManager, event, 0)
            true
        } catch (e: Exception) {
            Log.w(TAG, "injectPointerUp failed", e)
            invalidateInputManagerCache()
            false
        } finally {
            event.recycle()
        }
    }

    fun injectTapEvent(x: Float, y: Float): Boolean {
        val (inputManager, injectMethod) = getCachedInputManager() ?: return false
        val now = SystemClock.uptimeMillis()
        val down = MotionEvent.obtain(now, now, MotionEvent.ACTION_DOWN, x, y, 0).apply {
            source = InputDevice.SOURCE_TOUCHSCREEN
        }
        val up = MotionEvent.obtain(now, now + 10, MotionEvent.ACTION_UP, x, y, 0).apply {
            source = InputDevice.SOURCE_TOUCHSCREEN
        }
        return try {
            injectMethod.invoke(inputManager, down, 0)
            injectMethod.invoke(inputManager, up, 0)
            true
        } catch (e: Exception) {
            Log.w(TAG, "Direct tap injection failed", e)
            invalidateInputManagerCache()
            false
        } finally {
            down.recycle()
            up.recycle()
        }
    }

    private fun triggerAssistViaStatusBarBinder(): Boolean {
        if (!ElevatedTaskCloser.isShizukuActive) return false
        exemptHiddenApis()
        return try {
            val rawBinder = SystemServiceHelper.getSystemService("statusbar")
                ?: SystemServiceHelper.getSystemService(Context.STATUS_BAR_SERVICE)
                ?: return false
            val wrapped = ShizukuBinderWrapper(rawBinder)
            val stubClass = Class.forName("com.android.internal.statusbar.IStatusBarService\$Stub")
            val statusBarService = stubClass.getMethod("asInterface", IBinder::class.java)
                .invoke(null, wrapped) ?: return false
            val startAssist = statusBarService.javaClass.methods.firstOrNull {
                it.name == "startAssist" && it.parameterTypes.size == 1 && it.parameterTypes[0] == Bundle::class.java
            } ?: return false
            startAssist.invoke(statusBarService, Bundle())
            true
        } catch (e: Exception) {
            logSwallowed(TAG, "circleToSearch.statusBarBinder", e)
            false
        }
    }

    private fun triggerAssistViaVoiceInteractionBinder(): Boolean {
        if (!ElevatedTaskCloser.isShizukuActive) return false
        exemptHiddenApis()
        return try {
            val rawBinder = SystemServiceHelper.getSystemService("voiceinteraction") ?: return false
            val wrapped = ShizukuBinderWrapper(rawBinder)
            val stubClass = Class.forName("com.android.internal.app.IVoiceInteractionManagerService\$Stub")
            val viService = stubClass.getMethod("asInterface", IBinder::class.java)
                .invoke(null, wrapped) ?: return false
            val showMethod = viService.javaClass.methods.firstOrNull {
                it.name == "showSessionFromSession" && it.parameterTypes.size == 4
            }
            if (showMethod != null) {
                val res = showMethod.invoke(viService, null, Bundle(), 0, null) as? Boolean
                if (res == true) return true
            }
            false
        } catch (e: Exception) {
            logSwallowed(TAG, "circleToSearch.voiceInteractionBinder", e)
            false
        }
    }

    fun triggerCircleToSearch(context: Context) {
        var handled = false
        if (ElevatedTaskCloser.isShizukuActive) {
            handled = triggerAssistViaStatusBarBinder() ||
                triggerAssistViaVoiceInteractionBinder() ||
                injectKeyEvent(KeyEvent.KEYCODE_ASSIST)
        }
        if (!handled) {
            try {
                val intent = Intent(Intent.ACTION_VOICE_COMMAND).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(intent)
                handled = true
            } catch (_: Exception) {
                try {
                    val gIntent = context.packageManager.getLaunchIntentForPackage("com.google.android.googlequicksearchbox")
                    if (gIntent != null) {
                        gIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        context.startActivity(gIntent)
                    }
                } catch (e: Exception) {
                    logSwallowed(TAG, "circleToSearch.fallback", e)
                }
            }
        }
    }

    fun toastShizukuNeeded(context: Context, feature: String) {
        Handler(Looper.getMainLooper()).post {
            val msg = if (!Shizuku.pingBinder()) "$feature needs Shizuku, which isn't running. Start it, then try again." else "$feature needs Shizuku: authorize Lightspeed in your Shizuku app or fork"
            Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
        }
    }

    private class CompletedShizukuProcess(
        private val code: Int,
        stdout: String,
        stderr: String
    ) : Process() {
        private val outStream = ByteArrayInputStream(stdout.toByteArray(Charsets.UTF_8))
        private val errStream = ByteArrayInputStream(stderr.toByteArray(Charsets.UTF_8))
        private val sinkStream = object : OutputStream() {
            override fun write(b: Int) {}
        }

        override fun getOutputStream(): OutputStream = sinkStream
        override fun getInputStream(): InputStream = outStream
        override fun getErrorStream(): InputStream = errStream
        override fun waitFor(): Int = code
        override fun exitValue(): Int = code
        override fun destroy() {}
    }
}
