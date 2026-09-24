package com.sbf.lightspeed.system

import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioManager
import android.media.AudioPlaybackConfiguration
import android.os.Build
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
 * 1. Non-Ducking / MultiSound Playback: allows selected apps to keep playing continuously
 *    without pausing or reducing volume when other applications play audio at the same time
 *    (parity with mpv-android "Ignore audio focus" setting) via Shizuku & setMultiAudioFocusEnabled(true)
 * 2. Smart Phone Call Ducking: gracefully ducks non-ducking media to 20% during voice/phone calls
 * 3. True Hardware Per-App Volume & Muting: controls individual app volume via IPlayer.setVolume(float)
 * 4. Dynamic Audio Track Re-anchoring: monitors playback state to keep custom volumes locked
 */
object LightspeedAppSovereigntyEngine {

    private const val TAG = "AppSovereignty"
    private const val PREF_STEALTH_LOCKED_APPS = "stealth_locked_audio_apps"

    private val appVolumeMap = ConcurrentHashMap<String, Float>()
    private val stealthLockedApps = ConcurrentHashMap.newKeySet<String>()

    @Volatile
    private var playbackCallbackRegistered = false
    @Volatile
    private var modeListenerRegistered = false
    @Volatile
    private var isCallDuckingActive = false

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
     * Initializes the sovereignty engine: applies multi-audio focus if apps are locked,
     * registers track monitor, and hooks phone call audio mode listener for smart ducking.
     */
    fun initAudioEngine(context: Context?) {
        try {
            val ctx = context ?: return
            val locked = getStealthLockedApps(ctx)
            stealthLockedApps.clear()
            stealthLockedApps.addAll(locked)

            if (locked.isNotEmpty()) {
                setMultiAudioFocus(true)
                for (pkg in locked) {
                    armAppSovereignty(pkg)
                }
            } else {
                setMultiAudioFocus(false)
            }

            if (!playbackCallbackRegistered) {
                registerPlaybackMonitor(ctx)
            }
            if (!modeListenerRegistered) {
                registerModeListener(ctx)
            }
        } catch (e: Throwable) {
            Log.e(TAG, "initAudioEngine error", e)
        }
    }

    fun setMultiAudioFocus(enabled: Boolean): Boolean {
        return try {
            val audioService = getPrivilegedAudioService() ?: return false
            val m = audioService.javaClass.getMethod("setMultiAudioFocusEnabled", Boolean::class.javaPrimitiveType)
            m.invoke(audioService, enabled)
            Log.i(TAG, "Native Multi Audio Focus set to: $enabled")
            true
        } catch (e: Exception) {
            Log.w(TAG, "setMultiAudioFocus error", e)
            false
        }
    }

    fun armAppSovereignty(pkg: String) {
        try {
            ElevatedTaskCloser.execShizuku(
                "cmd appops set $pkg TAKE_AUDIO_FOCUS allow; " +
                "cmd appops set $pkg CONTROL_AUDIO allow; " +
                "cmd appops set $pkg CONTROL_AUDIO_PARTIAL allow; " +
                "dumpsys deviceidle whitelist +$pkg"
            )
            Log.i(TAG, "armAppSovereignty($pkg) applied")
        } catch (e: Throwable) {
            Log.w(TAG, "armAppSovereignty($pkg) error", e)
        }
    }

    fun disarmAppSovereignty(pkg: String) {
        try {
            ElevatedTaskCloser.execShizuku(
                "cmd appops set $pkg TAKE_AUDIO_FOCUS default; " +
                "cmd appops set $pkg CONTROL_AUDIO default; " +
                "cmd appops set $pkg CONTROL_AUDIO_PARTIAL default"
            )
            Log.i(TAG, "disarmAppSovereignty($pkg) applied")
        } catch (e: Throwable) {
            Log.w(TAG, "disarmAppSovereignty($pkg) error", e)
        }
    }

    private fun registerModeListener(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            try {
                val am = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager ?: return
                am.addOnModeChangedListener(context.mainExecutor, AudioManager.OnModeChangedListener { mode ->
                    handleAudioModeChanged(context, mode)
                })
                modeListenerRegistered = true
                Log.i(TAG, "OnModeChangedListener registered for smart call ducking")
            } catch (e: Throwable) {
                Log.w(TAG, "Failed registering OnModeChangedListener", e)
            }
        }
    }

    private fun handleAudioModeChanged(context: Context, mode: Int) {
        val inCall = mode == AudioManager.MODE_IN_CALL ||
                     mode == AudioManager.MODE_IN_COMMUNICATION ||
                     mode == AudioManager.MODE_RINGTONE
        if (inCall == isCallDuckingActive) return
        isCallDuckingActive = inCall
        Log.i(TAG, "Audio mode changed to $mode (inCall=$inCall), adjusting sovereign media")

        val am = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager ?: return
        reapplyVolumes(context, am.activePlaybackConfigurations)
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
                    val baseVol = appVolumeMap[pkg] ?: 1.0f
                    val effectiveVol = if (isCallDuckingActive && isAppStealthLocked(pkg, context)) {
                        (baseVol * 0.20f).coerceIn(0f, 1f)
                    } else {
                        baseVol
                    }
                    applyVolumeToConfig(config, effectiveVol)
                }
            } catch (_: Exception) {}
        }
    }

    private fun applyVolumeToConfig(config: Any, volume: Float): Boolean {
        val clamped = volume.coerceIn(0f, 1f)
        // Pipeline 1: Modern Android 13+ PlayerProxy (Volume++ architecture)
        try {
            val getPlayerProxy = config.javaClass.getMethod("getPlayerProxy")
            val proxy = getPlayerProxy.invoke(config)
            if (proxy != null) {
                val setVolume = proxy.javaClass.getMethod("setVolume", Float::class.javaPrimitiveType)
                setVolume.invoke(proxy, clamped)
                return true
            }
        } catch (_: Throwable) {}

        // Pipeline 2: Legacy / Direct IPlayer binder stub fallback
        return try {
            val getIPlayer = config.javaClass.getDeclaredMethod("getIPlayer").apply { isAccessible = true }
            val iPlayer = getIPlayer.invoke(config) ?: return false
            val setVolume = iPlayer.javaClass.getMethod("setVolume", Float::class.javaPrimitiveType)
            setVolume.invoke(iPlayer, clamped)
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
    // NON-DUCKING / CONTINUOUS AUDIO PLAYBACK
    // Allows selected app to keep playing continuously alongside any other app
    // (Parity with mpv-android "Ignore audio focus" setting)
    // ────────────────────────────────────────────────

    fun getStealthLockedApps(context: Context?): Set<String> {
        if (context == null) return stealthLockedApps
        val set = context.defaultPrefs().getStringSet(PREF_STEALTH_LOCKED_APPS, null)
        if (set != null) {
            stealthLockedApps.clear()
            stealthLockedApps.addAll(set)
            return set
        }
        return stealthLockedApps
    }

    fun setAppStealthLocked(pkg: String, isLocked: Boolean, context: Context? = null) {
        val ctx = context
        if (ctx != null) {
            val prefs = ctx.defaultPrefs()
            val current = getStealthLockedApps(ctx).toMutableSet()
            if (isLocked) {
                current.add(pkg)
                prefs.edit().putStringSet(PREF_STEALTH_LOCKED_APPS, current).apply()
                stealthLockedApps.add(pkg)
                armAppSovereignty(pkg)
                setMultiAudioFocus(true)
                ctx.showToast("${pkg.label()}: 🔒 Non-ducking Playback ON (won't pause or lower volume)")
            } else {
                current.remove(pkg)
                prefs.edit().putStringSet(PREF_STEALTH_LOCKED_APPS, current).apply()
                stealthLockedApps.remove(pkg)
                disarmAppSovereignty(pkg)
                if (current.isEmpty()) {
                    setMultiAudioFocus(false)
                }
                ctx.showToast("${pkg.label()}: 🔓 Standard audio focus restored")
            }
        } else {
            if (isLocked) {
                stealthLockedApps.add(pkg)
                armAppSovereignty(pkg)
                setMultiAudioFocus(true)
            } else {
                stealthLockedApps.remove(pkg)
                disarmAppSovereignty(pkg)
                if (stealthLockedApps.isEmpty()) {
                    setMultiAudioFocus(false)
                }
            }
        }
        Log.i(TAG, "setAppStealthLocked($pkg, $isLocked)")
    }

    fun isAppStealthLocked(pkg: String, context: Context? = null): Boolean {
        return if (context != null) {
            getStealthLockedApps(context).contains(pkg)
        } else {
            stealthLockedApps.contains(pkg)
        }
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
