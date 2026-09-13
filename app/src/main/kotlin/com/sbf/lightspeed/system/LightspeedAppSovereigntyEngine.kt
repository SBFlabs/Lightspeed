package com.sbf.lightspeed.system

import android.content.Context
import android.content.pm.PackageManager
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.widget.Toast
import rikka.shizuku.Shizuku

object LightspeedAppSovereigntyEngine {

    private const val TAG = "AppSovereignty"

    val isShizukuReady: Boolean
        get() = try {
            Shizuku.pingBinder() && Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
        } catch (_: Exception) { false }

    /**
     * Checks if Shizuku is ready. If not, requests permission and shows user-facing toast.
     * Returns true if ready.
     */
    fun ensureShizukuPermission(context: Context? = null): Boolean {
        if (!Shizuku.pingBinder()) {
            Log.w(TAG, "Shizuku server is not running")
            context?.showToast("Shizuku not running. Please start it from Shizuku app.")
            return false
        }
        if (Shizuku.checkSelfPermission() != PackageManager.PERMISSION_GRANTED) {
            Log.w(TAG, "Shizuku permission not granted — requesting...")
            try {
                Shizuku.requestPermission(ElevatedTaskCloser.SHIZUKU_REQ_CODE)
                context?.showToast("Please authorize Lightspeed in Shizuku prompt")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to request Shizuku permission", e)
            }
            return false
        }
        return true
    }

    /**
     * Runs an elevated shell command via Shizuku's proven execShizuku() pathway.
     * Returns the stdout output, or null on failure.
     */
    private fun runElevatedCmd(cmd: String, context: Context? = null): String? {
        if (!ensureShizukuPermission(context)) return null
        return try {
            val process = ElevatedTaskCloser.execShizuku(cmd) ?: run {
                Log.e(TAG, "execShizuku returned null for: $cmd")
                return null
            }
            val output = process.inputStream.bufferedReader().readText()
            val exitCode = process.waitFor()
            Log.i(TAG, "Ran [$cmd] exit=$exitCode output=${output.take(200)}")
            if (exitCode == 0) output.trim() else {
                val err = process.errorStream.bufferedReader().readText()
                Log.e(TAG, "Command failed exit=$exitCode stderr=${err.take(200)}")
                null
            }
        } catch (e: Throwable) {
            Log.e(TAG, "Error executing: $cmd", e)
            null
        }
    }

    // ────────────────────────────────────────────────
    // AUDIO_MEDIA_VOLUME — mutes app audio at system level
    // ────────────────────────────────────────────────

    fun setAppMuted(pkg: String, isMuted: Boolean, context: Context? = null) {
        val mode = if (isMuted) "deny" else "allow"
        val result = runElevatedCmd("cmd appops set $pkg AUDIO_MEDIA_VOLUME $mode", context)
        if (result != null) {
            Log.i(TAG, "setAppMuted($pkg) -> $mode ✓")
            context?.showToast("${pkg.label()}: ${if (isMuted) "🔇 Muted" else "🔊 Unmuted"}")
        } else {
            Log.e(TAG, "setAppMuted($pkg) -> FAILED")
            context?.showToast("⚠ Mute failed — is Shizuku authorized?")
        }
    }

    fun isAppMuted(pkg: String): Boolean {
        val output = runElevatedCmd("cmd appops get $pkg AUDIO_MEDIA_VOLUME") ?: return false
        return output.contains("deny") || output.contains("ignore")
    }

    // ────────────────────────────────────────────────
    // TAKE_AUDIO_FOCUS — prevents app from stealing focus from others
    // NOTE: Apply to OFFENDING apps (e.g. AnkiDroid) NOT the primary media app (e.g. Podium)
    //       Locking the primary app will prevent IT from starting playback.
    // ────────────────────────────────────────────────

    fun setAudioFocusLocked(pkg: String, isLocked: Boolean, context: Context? = null) {
        val mode = if (isLocked) "ignore" else "allow"
        val result = runElevatedCmd("cmd appops set $pkg TAKE_AUDIO_FOCUS $mode", context)
        if (result != null) {
            Log.i(TAG, "setAudioFocusLocked($pkg) -> $mode ✓")
            if (isLocked) {
                context?.showToast("${pkg.label()}: 🔒 Focus Lock ON — won't pause other apps")
            } else {
                context?.showToast("${pkg.label()}: 🔓 Focus Lock OFF — normal behaviour")
            }
        } else {
            Log.e(TAG, "setAudioFocusLocked($pkg) -> FAILED")
            context?.showToast("⚠ Focus lock failed — is Shizuku authorized?")
        }
    }

    fun isAudioFocusLocked(pkg: String): Boolean {
        val output = runElevatedCmd("cmd appops get $pkg TAKE_AUDIO_FOCUS") ?: return false
        return output.contains("ignore") || output.contains("deny")
    }

    // ────────────────────────────────────────────────
    // Helpers
    // ────────────────────────────────────────────────

    private fun String.label() = substringAfterLast('.')
        .replaceFirstChar { it.uppercaseChar() }

    private fun Context.showToast(msg: String) {
        Handler(Looper.getMainLooper()).post {
            Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
        }
    }
}
