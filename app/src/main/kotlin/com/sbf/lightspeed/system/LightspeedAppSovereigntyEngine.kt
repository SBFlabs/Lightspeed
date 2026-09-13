package com.sbf.lightspeed.system

import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioManager
import android.media.AudioPlaybackConfiguration
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.util.Log
import android.widget.Toast
import rikka.shizuku.Shizuku
import rikka.shizuku.ShizukuBinderWrapper
import rikka.shizuku.SystemServiceHelper
import java.lang.reflect.Method
import java.util.concurrent.ConcurrentHashMap

/**
 * Sovereign Audio & App Sovereignty Engine
 *
 * Provides:
 * 1. MultiSound / Stealth Audio: allows apps to play concurrently without pausing each other
 *    via native Android/Transsion IAudioService.setMultiAudioFocusEnabled(true)
 * 2. True Hardware Per-App Volume & Muting: controls individual app volume via IPlayer.setVolume(float)
 * 3. Dynamic Audio Track Re-anchoring: monitors playback state to keep custom volumes locked
 */
object LightspeedAppSovereigntyEngine {

    private const val TAG = "AppSovereignty"

    private val appVolumeMap = ConcurrentHashMap<String, Float>()
    private val stealthLockedApps = ConcurrentHashMap.newKeySet<String>()

    @Volatile
    private var playbackCallbackRegistered = false
    private val mainHandler = Handler(Looper.getMainLooper())

    val isShizukuReady: Boolean
        get() = try {
            Shizuku.pingBinder() && Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
        } catch (_: Exception) { false }

    fun ensureShizukuPermission(context: Context? = null): Boolean {
        if (!Shizuku.pingBinder()) {
            Log.w(TAG, "Shizuku server is not running")
            context?.showToast("Shizuku is not running")
            return false
        }
        if (Shizuku.checkSelfPermission() != PackageManager.PERMISSION_GRANTED) {
            Log.w(TAG, "Shizuku permission not granted")
            try {
                Shizuku.requestPermission(ElevatedTaskCloser.SHIZUKU_REQ_CODE)
                context?.showToast("Please authorize Lightspeed in Shizuku")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to request Shizuku permission", e)
            }
            return false
        }

        // Initialize audio system once Shizuku is ready
        initAudioEngine(context)
        return true
    }

    /**
     * Obtains the privileged IAudioService interface through ShizukuBinderWrapper.
     */
    private fun getPrivilegedAudioService(): Any? {
        return try {
            val rawBinder = SystemServiceHelper.getSystemService("audio")
                ?: SystemServiceHelper.getSystemService(Context.AUDIO_SERVICE)
                ?: return null
            val wrapped = ShizukuBinderWrapper(rawBinder)
            val stubClass = Class.forName("android.media.IAudioService\$Stub")
            val asInterface = stubClass.getMethod("asInterface", IBinder::class.java)
            asInterface.invoke(null, wrapped)
        } catch (e: Throwable) {
            Log.e(TAG, "Failed to get privileged IAudioService", e)
            null
        }
    }

    /**
     * Initializes the sovereignty engine: enables Multi Audio Focus and registers track monitor.
     */
    fun initAudioEngine(context: Context?) {
        try {
            val audioService = getPrivilegedAudioService()
            if (audioService != null) {
                try {
                    val m = audioService.javaClass.getMethod("setMultiAudioFocusEnabled", Boolean::class.javaPrimitiveType)
                    m.invoke(audioService, true)
                    Log.i(TAG, "Native Multi Audio Focus enabled successfully")
                } catch (e: Exception) {
                    Log.w(TAG, "setMultiAudioFocusEnabled method invocation error", e)
                }
            }

            if (context != null && !playbackCallbackRegistered) {
                registerPlaybackMonitor(context)
            }
        } catch (e: Throwable) {
            Log.e(TAG, "initAudioEngine error", e)
        }
    }

    private fun registerPlaybackMonitor(context: Context) {
        try {
            val am = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager ?: return
            am.registerAudioPlaybackCallback(object : AudioManager.AudioPlaybackCallback() {
                override fun onPlaybackConfigChanged(configs: MutableList<AudioPlaybackConfiguration>?) {
                    reapplyVolumes(context, configs)
                }
            }, mainHandler)
            playbackCallbackRegistered = true
            Log.i(TAG, "AudioPlaybackCallback registered")
        } catch (e: Throwable) {
            Log.w(TAG, "Failed to register AudioPlaybackCallback", e)
        }
    }

    private fun reapplyVolumes(context: Context, configs: List<AudioPlaybackConfiguration>?) {
        if (configs.isNullOrEmpty()) return
        val pm = context.packageManager
        for (config in configs) {
            try {
                val uid = try {
                    val getClientUid = config.javaClass.getMethod("getClientUid")
                    getClientUid.invoke(config) as? Int
                } catch (_: Exception) { null } ?: continue

                val packages = pm.getPackagesForUid(uid) ?: continue
                for (pkg in packages) {
                    val targetVol = appVolumeMap[pkg] ?: continue
                    applyVolumeToConfig(config, targetVol)
                }
            } catch (_: Exception) {}
        }
    }

    private fun applyVolumeToConfig(config: Any, volume: Float): Boolean {
        return try {
            val getIPlayer = config.javaClass.getDeclaredMethod("getIPlayer").apply { isAccessible = true }
            val iPlayer = getIPlayer.invoke(config) ?: return false
            val setVolume = iPlayer.javaClass.getMethod("setVolume", Float::class.javaPrimitiveType)
            setVolume.invoke(iPlayer, volume)
            true
        } catch (e: Throwable) {
            Log.w(TAG, "applyVolumeToConfig error", e)
            false
        }
    }

    // ────────────────────────────────────────────────
    // PER-APP VOLUME & MUTING
    // ────────────────────────────────────────────────

    fun setAppVolume(pkg: String, volume: Float, context: Context? = null) {
        val clamped = volume.coerceIn(0.0f, 1.0f)
        appVolumeMap[pkg] = clamped
        Log.i(TAG, "setAppVolume($pkg, $clamped)")

        val ctx = context ?: return
        val pm = ctx.packageManager
        var appliedCount = 0

        try {
            val audioService = getPrivilegedAudioService()
            val configs: List<*>? = if (audioService != null) {
                val getConfigs = audioService.javaClass.getMethod("getActivePlaybackConfigurations")
                getConfigs.invoke(audioService) as? List<*>
            } else {
                val am = ctx.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
                am?.activePlaybackConfigurations
            }

            if (configs != null) {
                for (config in configs) {
                    if (config == null) continue
                    val uid = try {
                        val getClientUid = config.javaClass.getMethod("getClientUid")
                        getClientUid.invoke(config) as? Int
                    } catch (_: Exception) { null } ?: continue

                    val packages = pm.getPackagesForUid(uid) ?: continue
                    if (pkg in packages) {
                        if (applyVolumeToConfig(config, clamped)) {
                            appliedCount++
                        }
                    }
                }
            }
        } catch (e: Throwable) {
            Log.e(TAG, "Error applying volume to active tracks", e)
        }

        // Also ensure Transsion zerosound setting is synced for full hardware silence when muted
        if (clamped <= 0.05f) {
            runTranssionZeroSound(pkg, true)
        } else {
            runTranssionZeroSound(pkg, false)
        }

        Log.i(TAG, "Volume $clamped applied to $appliedCount active track(s) for $pkg")
    }

    fun getAppVolume(pkg: String): Float {
        return appVolumeMap[pkg] ?: 1.0f
    }

    fun setAppMuted(pkg: String, isMuted: Boolean, context: Context? = null) {
        if (isMuted) {
            val current = appVolumeMap[pkg] ?: 1.0f
            if (current > 0.05f) {
                appVolumeMap["${pkg}_prev"] = current
            }
            setAppVolume(pkg, 0.0f, context)
            context?.showToast("${pkg.label()}: 🔇 Muted")
        } else {
            val prev = appVolumeMap["${pkg}_prev"] ?: 0.8f
            setAppVolume(pkg, prev, context)
            context?.showToast("${pkg.label()}: 🔊 Unmuted")
        }
    }

    fun isAppMuted(pkg: String): Boolean {
        val vol = appVolumeMap[pkg] ?: return false
        return vol <= 0.05f
    }

    // ────────────────────────────────────────────────
    // MULTISOUND / STEALTH AUDIO LOCK
    // Allows selected app to keep playing continuously alongside any other app
    // ────────────────────────────────────────────────

    fun setAppStealthLocked(pkg: String, isLocked: Boolean, context: Context? = null) {
        if (isLocked) {
            stealthLockedApps.add(pkg)
            // Ensure global multi audio focus is active
            initAudioEngine(context)
            context?.showToast("${pkg.label()}: 🔒 Stealth Play ON — plays simultaneously with any other app")
        } else {
            stealthLockedApps.remove(pkg)
            context?.showToast("${pkg.label()}: 🔓 Normal focus restored")
        }
        Log.i(TAG, "setAppStealthLocked($pkg, $isLocked)")
    }

    fun isAppStealthLocked(pkg: String): Boolean {
        return stealthLockedApps.contains(pkg)
    }

    // Backward compatibility aliases for UI callsites
    fun isAudioFocusLocked(pkg: String): Boolean = isAppStealthLocked(pkg)
    fun setAudioFocusLocked(pkg: String, isLocked: Boolean, context: Context? = null) =
        setAppStealthLocked(pkg, isLocked, context)

    private fun runTranssionZeroSound(pkg: String, enable: Boolean) {
        try {
            val process = ElevatedTaskCloser.execShizuku("settings get global audio.zerosound.applist") ?: return
            val current = process.inputStream.bufferedReader().readText().trim()
            process.waitFor()

            val list = current.split(",").map { it.trim() }.filter { it.isNotEmpty() }.toMutableSet()
            if (enable) {
                list.add(pkg)
            } else {
                list.remove(pkg)
            }
            val updated = list.joinToString(",")
            ElevatedTaskCloser.execShizuku("settings put global audio.zerosound.applist '$updated'")?.waitFor()
        } catch (_: Exception) {}
    }

    private fun String.label() = substringAfterLast('.')
        .replaceFirstChar { it.uppercaseChar() }

    private fun Context.showToast(msg: String) {
        mainHandler.post {
            Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
        }
    }
}
