package com.sbf.lightspeed.system.sonic

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioManager
import android.media.AudioPlaybackConfiguration
import android.media.audiofx.LoudnessEnhancer
import android.media.session.MediaSessionManager
import android.os.Handler
import android.os.Looper
import com.sbf.lightspeed.LightspeedAccessibilityService
import com.sbf.lightspeed.system.LightspeedNotificationListener
import com.sbf.lightspeed.system.LightspeedPreferences
import com.sbf.lightspeed.system.defaultPrefs
import com.sbf.lightspeed.system.logSwallowed
import java.lang.reflect.Method
import java.util.concurrent.ConcurrentHashMap

/**
 * Clean-Room Per-App Volume & Hardware Loudness Boost Engine.
 *
 * Bridges with the privileged Shizuku UserService (UID 2000) so AOSP AudioPlaybackConfiguration
 * exposes real clientUid and IPlayer proxies (adapted from Volume++, MIT License), while also
 * inspecting multi-window / Picture-in-Picture / Pop-Up Accessibility windows and MediaSessions
 * so apps like Jellyfin in PiP or Pop-up View are always detected immediately.
 */
object SonicAppVolumeEngine {

    private data class PlayerSession(
        val piid: Int,
        val sessionId: Int,
        val clientUid: Int,
        val packageName: String,
        val playerProxy: Any?,
        var enhancer: LoudnessEnhancer? = null
    )

    private val activeSessions = ConcurrentHashMap<Int, PlayerSession>()
    private val privilegedActivePackages = ConcurrentHashMap.newKeySet<String>()
    private val packageVolumeCache = ConcurrentHashMap<String, Float>()
    private val mainHandler = Handler(Looper.getMainLooper())
    private var isCallbackRegistered = false
    private var playbackCallback: AudioManager.AudioPlaybackCallback? = null

    fun getAppVolume(context: Context, packageName: String): Float {
        packageVolumeCache[packageName]?.let { return it }
        val vol = context.defaultPrefs().getFloat(LightspeedPreferences.KEY_PER_APP_VOLUME_PREFIX + packageName, 1.0f).coerceIn(0.0f, 2.0f)
        packageVolumeCache[packageName] = vol
        return vol
    }

    fun setAppVolume(context: Context, packageName: String, volume: Float) {
        val clamped = volume.coerceIn(0.0f, 2.0f)
        packageVolumeCache[packageName] = clamped
        context.defaultPrefs().edit().putFloat(LightspeedPreferences.KEY_PER_APP_VOLUME_PREFIX + packageName, clamped).apply()
        SonicVoiceIsolationEngine.ensureBound(context)
        try {
            SonicVoiceIsolationEngine.userService?.setPackageVolume(packageName, clamped)
        } catch (e: Exception) {
            logSwallowed("SonicAppVolumeEngine", "setAppVolume:userService", e)
        }
        applyVolumeToPackage(packageName, clamped)
    }

    fun reapplyAllVolumesToUserService(context: Context) {
        val svc = SonicVoiceIsolationEngine.userService ?: return
        val custom = getAllCustomAppVolumes(context)
        for ((pkg, vol) in custom) {
            try {
                svc.setPackageVolume(pkg, vol)
            } catch (_: Exception) {}
        }
    }

    /**
     * Fast, non-blocking collection of active / visible / PiP / MediaSession packages.
     */
    fun getImmediateVisibleAndMediaPackages(context: Context): List<String> {
        val result = linkedSetOf<String>()
        val ownPkg = context.packageName
        val ignored = setOf(ownPkg, "com.android.systemui", "android")

        // 1. All visible Accessibility windows (includes Picture-in-Picture, Pop-Up / Freeform, Split-Screen, and Foreground)
        try {
            val a11y = LightspeedAccessibilityService.instance
            val windows = a11y?.windows
            if (!windows.isNullOrEmpty()) {
                for (win in windows) {
                    val pkg = win.root?.packageName?.toString()
                    if (!pkg.isNullOrBlank() && pkg !in ignored && !pkg.contains("launcher")) {
                        result.add(pkg)
                    }
                }
            }
            val rootPkg = a11y?.rootInActiveWindow?.packageName?.toString()
            if (!rootPkg.isNullOrBlank() && rootPkg !in ignored && !rootPkg.contains("launcher")) {
                result.add(rootPkg)
            }
            val lastApp = LightspeedAccessibilityService.lastAppPackage
            if (!lastApp.isNullOrBlank() && lastApp !in ignored && !lastApp.contains("launcher")) {
                result.add(lastApp)
            }
        } catch (_: Exception) {}

        // 2. Active MediaSession packages (catches Jellyfin, VLC, mpv, Spotify, YouTube in PiP or background)
        try {
            val msm = context.getSystemService(Context.MEDIA_SESSION_SERVICE) as? MediaSessionManager
            if (msm != null) {
                val cn = ComponentName(context, LightspeedNotificationListener::class.java)
                val controllers = msm.getActiveSessions(cn)
                for (ctrl in controllers) {
                    val pkg = ctrl.packageName
                    if (!pkg.isNullOrBlank() && pkg !in ignored) {
                        result.add(pkg)
                    }
                }
            }
        } catch (_: Exception) {}

        // 3. Privileged & cached audio sessions
        for (pkg in privilegedActivePackages) {
            if (pkg !in ignored) result.add(pkg)
        }
        for (session in activeSessions.values) {
            if (session.packageName.isNotBlank() && session.packageName !in ignored) {
                result.add(session.packageName)
            }
        }

        return result.toList()
    }

    fun getActivePlayingPackages(): Set<String> {
        val set = linkedSetOf<String>()
        set.addAll(privilegedActivePackages)
        activeSessions.values.mapTo(set) { it.packageName }
        set.remove("")
        return set
    }

    fun queryPrivilegedAudioPackages(context: Context): List<String> {
        SonicVoiceIsolationEngine.ensureBound(context)
        val svc = SonicVoiceIsolationEngine.userService ?: return emptyList()
        return try {
            val list = svc.activeAudioPackages ?: emptyList()
            privilegedActivePackages.clear()
            privilegedActivePackages.addAll(list)
            list
        } catch (e: Exception) {
            logSwallowed("SonicAppVolumeEngine", "queryPrivilegedAudioPackages", e)
            emptyList()
        }
    }

    fun getAllCustomAppVolumes(context: Context): Map<String, Float> {
        val prefs = context.defaultPrefs().all
        val prefix = LightspeedPreferences.KEY_PER_APP_VOLUME_PREFIX
        val result = mutableMapOf<String, Float>()
        for ((k, v) in prefs) {
            if (k.startsWith(prefix) && v is Float) {
                result[k.removePrefix(prefix)] = v
            }
        }
        return result
    }

    fun startMonitoring(context: Context) {
        SonicVoiceIsolationEngine.ensureBound(context)
        ensurePlaybackCallback(context)
        refreshActiveAudioPlayers(context)
    }

    fun stopMonitoring() {
        playbackCallback = null
        isCallbackRegistered = false
        resetAllAppVolumesToUnity()
    }

    fun resetAllAppVolumesToUnity() {
        try {
            SonicVoiceIsolationEngine.userService?.resetAllPackageVolumes()
        } catch (_: Exception) {}
        for ((_, session) in activeSessions) {
            try {
                session.enhancer?.apply {
                    enabled = false
                    release()
                }
                session.enhancer = null
                invokePlayerProxyVolume(session.playerProxy, 1.0f)
            } catch (e: Exception) {
                logSwallowed("SonicAppVolumeEngine", "resetAll", e)
            }
        }
        activeSessions.clear()
        packageVolumeCache.clear()
    }

    fun refreshActiveAudioPlayers(context: Context) {
        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager ?: return
        val pm = context.packageManager
        try {
            SonicVoiceIsolationEngine.userService?.activeAudioPackages?.let { pkgs ->
                privilegedActivePackages.clear()
                privilegedActivePackages.addAll(pkgs)
            }
        } catch (_: Exception) {}

        val configs = try {
            audioManager.activePlaybackConfigurations
        } catch (_: Exception) {
            emptyList()
        }

        val currentPiids = mutableSetOf<Int>()
        for (config in configs) {
            try {
                val piid = invokeMethod(config, "getPlayerInterfaceId") as? Int ?: config.hashCode()
                currentPiids.add(piid)
                if (!activeSessions.containsKey(piid)) {
                    val uid = invokeMethod(config, "getClientUid") as? Int ?: -1
                    val sessionId = invokeMethod(config, "getSessionId") as? Int ?: 0
                    val playerProxy = invokeMethod(config, "getPlayerProxy")
                    val pkgName = resolvePackageName(pm, uid)
                    if (pkgName != null) {
                        val session = PlayerSession(piid, sessionId, uid, pkgName, playerProxy)
                        activeSessions[piid] = session
                        val savedVol = getAppVolume(context, pkgName)
                        if (savedVol != 1.0f) {
                            applyVolumeToSession(session, savedVol)
                        }
                    }
                }
            } catch (e: Exception) {
                logSwallowed("SonicAppVolumeEngine", "refreshPlayers", e)
            }
        }

        val staleIterator = activeSessions.entries.iterator()
        while (staleIterator.hasNext()) {
            val entry = staleIterator.next()
            if (!currentPiids.contains(entry.key)) {
                entry.value.enhancer?.apply {
                    enabled = false
                    release()
                }
                entry.value.enhancer = null
                staleIterator.remove()
            }
        }
    }

    private fun applyVolumeToPackage(packageName: String, volume: Float) {
        for ((_, session) in activeSessions) {
            if (session.packageName == packageName) {
                applyVolumeToSession(session, volume)
            }
        }
    }

    private fun applyVolumeToSession(session: PlayerSession, volume: Float) {
        try {
            if (volume <= 1.0f) {
                session.enhancer?.apply {
                    enabled = false
                    release()
                }
                session.enhancer = null
                invokePlayerProxyVolume(session.playerProxy, volume)
            } else {
                invokePlayerProxyVolume(session.playerProxy, 1.0f)
                val boostGainMb = ((volume - 1.0f) * 1000f).toInt().coerceIn(0, 1000)
                if (session.sessionId > 0) {
                    val enhancer = session.enhancer ?: try {
                        LoudnessEnhancer(session.sessionId).also { session.enhancer = it }
                    } catch (_: Exception) {
                        null
                    }
                    enhancer?.apply {
                        setTargetGain(boostGainMb)
                        enabled = (boostGainMb > 0)
                    }
                }
            }
        } catch (e: Exception) {
            logSwallowed("SonicAppVolumeEngine", "applyVolumeToSession", e)
        }
    }

    private fun invokePlayerProxyVolume(proxy: Any?, volume: Float) {
        if (proxy == null) return
        try {
            val method = proxy.javaClass.getMethod("setVolume", java.lang.Float.TYPE)
            method.invoke(proxy, volume)
        } catch (e: Exception) {
            logSwallowed("SonicAppVolumeEngine", "invokeProxy", e)
        }
    }

    private fun ensurePlaybackCallback(context: Context) {
        if (isCallbackRegistered) return
        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager ?: return
        val cb = object : AudioManager.AudioPlaybackCallback() {
            override fun onPlaybackConfigChanged(configs: List<AudioPlaybackConfiguration>) {
                mainHandler.post { refreshActiveAudioPlayers(context) }
            }
        }
        playbackCallback = cb
        try {
            audioManager.registerAudioPlaybackCallback(cb, mainHandler)
            isCallbackRegistered = true
        } catch (e: Exception) {
            logSwallowed("SonicAppVolumeEngine", "registerCallback", e)
        }
    }

    private fun resolvePackageName(pm: PackageManager, uid: Int): String? {
        if (uid <= 10000) return null
        return try {
            pm.getPackagesForUid(uid)?.firstOrNull()
        } catch (_: Exception) {
            null
        }
    }

    private fun invokeMethod(target: Any, methodName: String): Any? {
        return try {
            val method: Method = target.javaClass.getMethod(methodName)
            method.invoke(target)
        } catch (_: Exception) {
            null
        }
    }
}
