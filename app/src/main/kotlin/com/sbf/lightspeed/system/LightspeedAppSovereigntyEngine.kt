package com.sbf.lightspeed.system

import rikka.shizuku.Shizuku
import android.util.Log
import java.io.BufferedReader
import java.io.InputStreamReader

object LightspeedAppSovereigntyEngine {

    private const val TAG = "AppSovereignty"

    private fun runShizukuCmd(cmd: Array<String>): String? {
        if (!Shizuku.pingBinder()) {
            Log.w(TAG, "Shizuku pingBinder is false")
            return null
        }
        return try {
            val method = Shizuku::class.java.getDeclaredMethod(
                "newProcess",
                Array<String>::class.java,
                Array<String>::class.java,
                String::class.java
            )
            method.isAccessible = true
            val process = method.invoke(null, cmd, null, null) as? Process ?: return null
            val reader = BufferedReader(InputStreamReader(process.inputStream))
            val output = reader.readText()
            process.waitFor()
            output.trim()
        } catch (e: Throwable) {
            Log.e(TAG, "Error executing Shizuku command: ${cmd.joinToString(" ")}", e)
            null
        }
    }

    fun setAppMuted(pkg: String, isMuted: Boolean) {
        val mode = if (isMuted) "deny" else "allow"
        val res = runShizukuCmd(arrayOf("cmd", "appops", "set", pkg, "AUDIO_MEDIA_VOLUME", mode))
        Log.d(TAG, "setAppMuted($pkg, $mode) -> res: $res")
    }

    fun isAppMuted(pkg: String): Boolean {
        val output = runShizukuCmd(arrayOf("cmd", "appops", "get", pkg, "AUDIO_MEDIA_VOLUME")) ?: return false
        return output.contains("deny") || output.contains("ignore")
    }

    fun setAudioFocusLocked(pkg: String, isLocked: Boolean) {
        // When locked / sovereign: set TAKE_AUDIO_FOCUS to ignore so it NEVER pauses other apps
        val mode = if (isLocked) "ignore" else "allow"
        val res = runShizukuCmd(arrayOf("cmd", "appops", "set", pkg, "TAKE_AUDIO_FOCUS", mode))
        Log.d(TAG, "setAudioFocusLocked($pkg, $mode) -> res: $res")
    }

    fun isAudioFocusLocked(pkg: String): Boolean {
        val output = runShizukuCmd(arrayOf("cmd", "appops", "get", pkg, "TAKE_AUDIO_FOCUS")) ?: return false
        return output.contains("ignore") || output.contains("deny")
    }
}
