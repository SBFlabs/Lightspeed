package com.sbf.lightspeed.system

import rikka.shizuku.Shizuku
import android.util.Log

object LightspeedAppSovereigntyEngine {

    fun setAppMuted(pkg: String, isMuted: Boolean) {
        val mode = if (isMuted) "deny" else "allow"
        val cmd = arrayOf("cmd", "appops", "set", pkg, "AUDIO_MEDIA_VOLUME", mode)
        
        if (Shizuku.pingBinder()) {
            try {
                val process = Shizuku.newProcess(cmd, null, null)
                process.waitFor()
                Log.d("AppSovereignty", "Set $pkg AUDIO_MEDIA_VOLUME to $mode")
            
    fun isAppMuted(pkg: String): Boolean {
        if (!Shizuku.pingBinder()) return false
        try {
            val process = Shizuku.newProcess(arrayOf("cmd", "appops", "get", pkg, "AUDIO_MEDIA_VOLUME"), null, null)
            val reader = java.io.BufferedReader(java.io.InputStreamReader(process.inputStream))
            val output = reader.readText()
            process.waitFor()
            return output.contains("deny")
        } catch (e: Exception) {
            return false
        }
    }
} catch (e: Exception) {
                Log.e("AppSovereignty", "Failed to set appops via Shizuku", e)
            
    fun isAppMuted(pkg: String): Boolean {
        if (!Shizuku.pingBinder()) return false
        try {
            val process = Shizuku.newProcess(arrayOf("cmd", "appops", "get", pkg, "AUDIO_MEDIA_VOLUME"), null, null)
            val reader = java.io.BufferedReader(java.io.InputStreamReader(process.inputStream))
            val output = reader.readText()
            process.waitFor()
            return output.contains("deny")
        } catch (e: Exception) {
            return false
        }
    }
}
        
    fun isAppMuted(pkg: String): Boolean {
        if (!Shizuku.pingBinder()) return false
        try {
            val process = Shizuku.newProcess(arrayOf("cmd", "appops", "get", pkg, "AUDIO_MEDIA_VOLUME"), null, null)
            val reader = java.io.BufferedReader(java.io.InputStreamReader(process.inputStream))
            val output = reader.readText()
            process.waitFor()
            return output.contains("deny")
        } catch (e: Exception) {
            return false
        }
    }
} else {
            Log.w("AppSovereignty", "Shizuku binder is not available")
        
    fun isAppMuted(pkg: String): Boolean {
        if (!Shizuku.pingBinder()) return false
        try {
            val process = Shizuku.newProcess(arrayOf("cmd", "appops", "get", pkg, "AUDIO_MEDIA_VOLUME"), null, null)
            val reader = java.io.BufferedReader(java.io.InputStreamReader(process.inputStream))
            val output = reader.readText()
            process.waitFor()
            return output.contains("deny")
        } catch (e: Exception) {
            return false
        }
    }
}
    
    fun isAppMuted(pkg: String): Boolean {
        if (!Shizuku.pingBinder()) return false
        try {
            val process = Shizuku.newProcess(arrayOf("cmd", "appops", "get", pkg, "AUDIO_MEDIA_VOLUME"), null, null)
            val reader = java.io.BufferedReader(java.io.InputStreamReader(process.inputStream))
            val output = reader.readText()
            process.waitFor()
            return output.contains("deny")
        } catch (e: Exception) {
            return false
        }
    }
}

    fun isAppMuted(pkg: String): Boolean {
        if (!Shizuku.pingBinder()) return false
        try {
            val process = Shizuku.newProcess(arrayOf("cmd", "appops", "get", pkg, "AUDIO_MEDIA_VOLUME"), null, null)
            val reader = java.io.BufferedReader(java.io.InputStreamReader(process.inputStream))
            val output = reader.readText()
            process.waitFor()
            return output.contains("deny")
        } catch (e: Exception) {
            return false
        }
    }
}
