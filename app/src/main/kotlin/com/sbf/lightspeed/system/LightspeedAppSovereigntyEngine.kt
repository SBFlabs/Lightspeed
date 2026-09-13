package com.sbf.lightspeed.system

import android.content.Context
import android.content.pm.PackageManager
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.widget.Toast
import rikka.shizuku.Shizuku
import java.io.BufferedReader
import java.io.InputStreamReader

object LightspeedAppSovereigntyEngine {

    private const val TAG = "AppSovereignty"

    val isShizukuReady: Boolean
        get() = try {
            Shizuku.pingBinder() && Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
        } catch (_: Exception) { false }

    fun ensureShizukuPermission(context: Context? = null): Boolean {
        if (!Shizuku.pingBinder()) {
            context?.let {
                Handler(Looper.getMainLooper()).post {
                    Toast.makeText(it, "Shizuku is not running on device", Toast.LENGTH_SHORT).show()
                }
            }
            return false
        }
        if (Shizuku.checkSelfPermission() != PackageManager.PERMISSION_GRANTED) {
            try {
                Shizuku.requestPermission(ElevatedTaskCloser.SHIZUKU_REQ_CODE)
                context?.let {
                    Handler(Looper.getMainLooper()).post {
                        Toast.makeText(it, "Please authorize Lightspeed in Shizuku prompt", Toast.LENGTH_LONG).show()
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to request Shizuku permission", e)
            }
            return false
        }
        return true
    }

    private fun runElevatedCmd(cmd: String, context: Context? = null): String? {
        if (!ensureShizukuPermission(context)) {
            Log.w(TAG, "Cannot run elevated command: Shizuku not ready")
            return null
        }
        return try {
            val m = Shizuku::class.java.getDeclaredMethod(
                "newProcess",
                Array<String>::class.java,
                Array<String>::class.java,
                String::class.java
            ).apply { isAccessible = true }
            val process = m.invoke(null, arrayOf("sh", "-c", cmd), null, null) as? Process ?: return null
            val reader = BufferedReader(InputStreamReader(process.inputStream))
            val output = reader.readText()
            process.waitFor()
            Log.i(TAG, "Ran: $cmd -> Output: $output")
            output.trim()
        } catch (e: Throwable) {
            Log.e(TAG, "Error executing Shizuku command: $cmd", e)
            null
        }
    }

    fun setAppMuted(pkg: String, isMuted: Boolean, context: Context? = null) {
        val mode = if (isMuted) "deny" else "allow"
        val cmd = "cmd appops set $pkg AUDIO_MEDIA_VOLUME $mode"
        runElevatedCmd(cmd, context)
        context?.let {
            Handler(Looper.getMainLooper()).post {
                val label = pkg.substringAfterLast('.')
                val status = if (isMuted) "Muted" else "Unmuted"
                Toast.makeText(it, "$label: $status", Toast.LENGTH_SHORT).show()
            }
        }
    }

    fun isAppMuted(pkg: String): Boolean {
        val output = runElevatedCmd("cmd appops get $pkg AUDIO_MEDIA_VOLUME") ?: return false
        return output.contains("deny") || output.contains("ignore")
    }

    fun setAudioFocusLocked(pkg: String, isLocked: Boolean, context: Context? = null) {
        val mode = if (isLocked) "ignore" else "allow"
        val cmd = "cmd appops set $pkg TAKE_AUDIO_FOCUS $mode"
        runElevatedCmd(cmd, context)
        context?.let {
            Handler(Looper.getMainLooper()).post {
                val label = pkg.substringAfterLast('.')
                val status = if (isLocked) "Focus Locked (Won't pause other apps)" else "Focus Unlocked (Normal)"
                Toast.makeText(it, "$label: $status", Toast.LENGTH_SHORT).show()
            }
        }
    }

    fun isAudioFocusLocked(pkg: String): Boolean {
        val output = runElevatedCmd("cmd appops get $pkg TAKE_AUDIO_FOCUS") ?: return false
        return output.contains("ignore") || output.contains("deny")
    }
}
