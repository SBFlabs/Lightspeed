package com.sbf.lightspeed.system.sonic

import android.content.ComponentName
import android.content.Context
import android.content.ServiceConnection
import android.os.Binder
import android.os.IBinder
import com.sbf.lightspeed.system.ElevatedTaskCloser
import com.sbf.lightspeed.system.LightspeedPreferences
import com.sbf.lightspeed.system.defaultPrefs
import com.sbf.lightspeed.system.logSwallowed
import rikka.shizuku.Shizuku

/**
 * Realtime Neural Voice Isolation & Dialogue Enhancement Client Engine.
 *
 * Connects to the privileged SonicIsolationUserService running under UID 2000 (shell).
 * Exposes 5 acoustic presets (Cinema, Podcast, Gaming, Max, Custom) and two controls:
 * 1. vocalBoostLevel (0.0f..1.0f): Amplifies neural dialogue (+8dB speech boost).
 * 2. backgroundMuffleLevel (0.0f..1.0f): Attenuates movie & video background residual.
 */
object SonicVoiceIsolationEngine {

    const val KEY_SONIC_PRESET = "pref_sonic_isolation_preset"
    const val PRESET_CINEMA = "cinema"
    const val PRESET_PODCAST = "podcast"
    const val PRESET_GAMING = "gaming"
    const val PRESET_MAX = "max"
    const val PRESET_CUSTOM = "custom"

    @Volatile var isEnabled: Boolean = false
        private set

    @Volatile var activePreset: String = PRESET_CINEMA
        private set

    @Volatile var vocalBoostLevel: Float = 0.60f
        private set

    @Volatile var backgroundMuffleLevel: Float = 0.65f
        private set

    @Volatile var userService: ISonicIsolationUserService? = null
        private set

    @Volatile var isBound = false
        private set
    @Volatile private var isBinding = false
    @Volatile private var bindingStartTime = 0L
    private val bindLock = Any()
    private val clientToken = Binder()
    private var appContext: Context? = null

    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            synchronized(bindLock) {
                isBinding = false
                val binder = ISonicIsolationUserService.Stub.asInterface(service)
                userService = binder
                isBound = true
                try {
                    service?.linkToDeath({
                        synchronized(bindLock) {
                            userService = null
                            isBound = false
                            isBinding = false
                        }
                    }, 0)
                } catch (_: Exception) {}
                try {
                    binder.registerClientToken(clientToken)
                    appContext?.let { ctx ->
                        binder.initialize(ctx.packageName)
                        binder.setVocalBoostLevel(vocalBoostLevel)
                        binder.setBackgroundMuffleLevel(backgroundMuffleLevel)
                        if (isEnabled) {
                            binder.startPipeline()
                        }
                        SonicChannelRouter.reapplyPrivilegedRoutes(ctx)
                        SonicAppVolumeEngine.reapplyAllVolumesToUserService(ctx)
                    }
                } catch (e: Exception) {
                    logSwallowed("SonicVoiceIsolationEngine", "onServiceConnected", e)
                }
            }
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            synchronized(bindLock) {
                userService = null
                isBound = false
                isBinding = false
            }
        }
    }

    fun initialize(context: Context) {
        val appCtx = context.applicationContext ?: context
        appContext = appCtx
        val prefs = appCtx.defaultPrefs()
        activePreset = prefs.getString(KEY_SONIC_PRESET, PRESET_CINEMA) ?: PRESET_CINEMA
        var savedBoost = prefs.getFloat(LightspeedPreferences.KEY_SONIC_VOCAL_BOOST, 0.60f)
        var savedMuffle = prefs.getFloat(LightspeedPreferences.KEY_SONIC_BACKGROUND_MUFFLE, 0.65f)
        if (savedBoost <= 0.001f && savedMuffle <= 0.001f) {
            savedBoost = 0.60f
            savedMuffle = 0.65f
        }
        vocalBoostLevel = savedBoost
        backgroundMuffleLevel = savedMuffle
        val shouldEnable = prefs.getBoolean(LightspeedPreferences.KEY_SONIC_VOICE_ISOLATION_ENABLED, false)

        ensureBound(appCtx)
        if (shouldEnable) {
            start(appCtx)
        }
    }

    fun ensureBound(context: Context) {
        val appCtx = context.applicationContext ?: context
        appContext = appCtx
        val now = System.currentTimeMillis()
        if (isBinding && now - bindingStartTime > 5000L) {
            isBinding = false
        }
        if (isBound && userService?.asBinder()?.isBinderAlive == false) {
            userService = null
            isBound = false
        }
        if (isBound && userService != null) return
        if (isBinding) return
        if (!ElevatedTaskCloser.isShizukuActive) return
        // Sonic Deck master switch off -> never spawn the privileged service process (saves ~100-150 MB).
        if (!appCtx.defaultPrefs().getBoolean(LightspeedPreferences.KEY_SONIC_DECK_ENABLED, false)) return

        synchronized(bindLock) {
            if (isBound && userService != null) return
            if (isBinding && now - bindingStartTime <= 5000L) return
            isBinding = true
            bindingStartTime = System.currentTimeMillis()

            try {
                val args = Shizuku.UserServiceArgs(
                    ComponentName(appCtx.packageName, SonicIsolationUserService::class.java.name)
                )
                    .daemon(false)
                    .processNameSuffix("sonic_service")
                    .debuggable(false)
                    .version(getServiceVersion(appCtx))

                Shizuku.bindUserService(args, serviceConnection)
            } catch (e: Exception) {
                isBinding = false
                logSwallowed("SonicVoiceIsolationEngine", "bindUserService", e)
            }
        }
    }

    private fun getServiceVersion(context: Context): Int {
        return try {
            @Suppress("DEPRECATION")
            context.packageManager.getPackageInfo(context.packageName, 0).versionCode
        } catch (_: Exception) {
            8
        }
    }

    fun applyPreset(context: Context, preset: String) {
        activePreset = preset
        val (boost, muffle) = when (preset) {
            PRESET_CINEMA -> 0.60f to 0.65f
            PRESET_PODCAST -> 0.75f to 0.85f
            PRESET_GAMING -> 0.50f to 0.50f
            PRESET_MAX -> 1.00f to 1.00f
            else -> vocalBoostLevel to backgroundMuffleLevel
        }
        context.defaultPrefs().edit().putString(KEY_SONIC_PRESET, preset).apply()
        if (preset != PRESET_CUSTOM) {
            setVocalBoost(context, boost, updatePresetToCustom = false)
            setBackgroundMuffle(context, muffle, updatePresetToCustom = false)
        }
    }

    @Synchronized
    fun setEnabled(context: Context, enable: Boolean): Boolean = if (enable) start(context) else { stop(context); true }

    @Synchronized
    fun start(context: Context): Boolean {
        val appCtx = context.applicationContext ?: context
        appContext = appCtx
        isEnabled = true
        if (vocalBoostLevel <= 0.001f && backgroundMuffleLevel <= 0.001f) {
            applyPreset(appCtx, PRESET_CINEMA)
        }
        appCtx.defaultPrefs().edit().putBoolean(LightspeedPreferences.KEY_SONIC_VOICE_ISOLATION_ENABLED, true).apply()

        ensureBound(appCtx)
        return try {
            userService?.let { svc ->
                svc.initialize(appCtx.packageName)
                svc.setVocalBoostLevel(vocalBoostLevel)
                svc.setBackgroundMuffleLevel(backgroundMuffleLevel)
                svc.startPipeline()
            }
            true
        } catch (e: Exception) {
            logSwallowed("SonicVoiceIsolationEngine", "startPipeline", e)
            isEnabled = false
            appCtx.defaultPrefs().edit().putBoolean(LightspeedPreferences.KEY_SONIC_VOICE_ISOLATION_ENABLED, false).apply()
            false
        }
    }

    @Synchronized
    fun stop(context: Context? = null) {
        isEnabled = false
        context?.defaultPrefs()?.edit()?.putBoolean(LightspeedPreferences.KEY_SONIC_VOICE_ISOLATION_ENABLED, false)?.apply()
        try {
            userService?.stopPipeline()
        } catch (e: Exception) {
            logSwallowed("SonicVoiceIsolationEngine", "stopPipeline", e)
        }
    }

    fun setVocalBoost(context: Context, level: Float, updatePresetToCustom: Boolean = true) {
        vocalBoostLevel = level.coerceIn(0.0f, 1.0f)
        val editor = context.defaultPrefs().edit().putFloat(LightspeedPreferences.KEY_SONIC_VOCAL_BOOST, vocalBoostLevel)
        if (updatePresetToCustom) {
            activePreset = PRESET_CUSTOM
            editor.putString(KEY_SONIC_PRESET, PRESET_CUSTOM)
        }
        editor.apply()
        try {
            userService?.setVocalBoostLevel(vocalBoostLevel)
        } catch (e: Exception) {
            logSwallowed("SonicVoiceIsolationEngine", "setVocalBoost", e)
        }
    }

    fun setBackgroundMuffle(context: Context, level: Float, updatePresetToCustom: Boolean = true) {
        backgroundMuffleLevel = level.coerceIn(0.0f, 1.0f)
        val editor = context.defaultPrefs().edit().putFloat(LightspeedPreferences.KEY_SONIC_BACKGROUND_MUFFLE, backgroundMuffleLevel)
        if (updatePresetToCustom) {
            activePreset = PRESET_CUSTOM
            editor.putString(KEY_SONIC_PRESET, PRESET_CUSTOM)
        }
        editor.apply()
        try {
            userService?.setBackgroundMuffleLevel(backgroundMuffleLevel)
        } catch (e: Exception) {
            logSwallowed("SonicVoiceIsolationEngine", "setBackgroundMuffle", e)
        }
    }

    fun teardown(context: Context? = null) {
        stop(context)
        synchronized(bindLock) {
            if (isBound || isBinding) {
                try {
                    userService?.destroy()
                } catch (_: Exception) {}
                try {
                    val appCtx = context?.applicationContext ?: appContext
                    if (appCtx != null) {
                        val args = Shizuku.UserServiceArgs(
                            ComponentName(appCtx.packageName, SonicIsolationUserService::class.java.name)
                        )
                            .daemon(false)
                            .processNameSuffix("sonic_service")
                            .debuggable(false)
                            .version(getServiceVersion(appCtx))
                        Shizuku.unbindUserService(args, serviceConnection, true)
                    }
                } catch (e: Exception) {
                    logSwallowed("SonicVoiceIsolationEngine", "unbindUserService", e)
                }
                isBound = false
                isBinding = false
                userService = null
            }
        }
    }
}
