package com.sbf.lightspeed.system.sonic

import android.content.Context
import android.media.AudioDeviceInfo
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioPlaybackConfiguration
import android.media.audiofx.LoudnessEnhancer
import android.os.IBinder
import android.os.Process
import androidx.annotation.Keep
import com.sbf.lightspeed.system.logSwallowed
import java.util.concurrent.ConcurrentHashMap

/**
 * Privileged Shizuku UserService running in UID 2000 (`com.android.shell`).
 * Holds CAPTURE_AUDIO_OUTPUT and MODIFY_AUDIO_ROUTING permissions.
 *
 * Orchestrates:
 * 1. Real-time neural voice isolation (from android-realtime-voice-isolation, MIT License).
 * 2. Privileged per-app audio output device routing via AOSP AudioPolicy ROUTE_FLAG_RENDER (1)
 *    and RULE_MATCH_UID (4) chunked at 20 UIDs per mix (adapted from SplitAudio, MIT License).
 * 3. Privileged per-app volume & LoudnessEnhancer control via UID 2000 IPlayer proxies
 *    (adapted from Volume++, MIT License).
 */
class SonicIsolationUserService() : ISonicIsolationUserService.Stub() {

    private val mixer = IsolationIntensityMixer()
    private var pipeline: SonicPrivilegedAudioPipeline? = null
    private var shellContext: Context? = null
    private var appContext: Context? = null
    private val activeRoutedUids = ConcurrentHashMap<Int, String>()
    private var activeRoutingPolicy: Any? = null
    private val packageVolumes = ConcurrentHashMap<String, Float>()
    private val sessionEnhancers = ConcurrentHashMap<Int, LoudnessEnhancer>()

    @Keep
    constructor(context: Context) : this() {
        initContexts(context, context.packageName)
    }

    companion object {
        private const val RULE_MATCH_UID = 4
        private const val ROUTE_FLAG_RENDER = 1
        private const val MAX_UIDS_PER_MIX = 20
    }

    private var playbackCallback: AudioManager.AudioPlaybackCallback? = null

    private fun initContexts(baseCtx: Context, appPackageName: String) {
        try {
            ShellAttributionContext.installShellAttribution(baseCtx)
            val shellCtx = ShellAttributionContext(baseCtx)
            shellContext = shellCtx
            appContext = if (baseCtx.packageName != "com.android.shell" && baseCtx.packageName.isNotBlank()) {
                baseCtx
            } else if (appPackageName.isNotBlank() && appPackageName != "com.android.shell") {
                try {
                    baseCtx.createPackageContext(
                        appPackageName,
                        Context.CONTEXT_INCLUDE_CODE or Context.CONTEXT_IGNORE_SECURITY
                    )
                } catch (_: Exception) {
                    baseCtx
                }
            } else {
                baseCtx
            }
            ensurePlaybackCallback()
        } catch (e: Exception) {
            logSwallowed("SonicIsolationUserService", "initContexts", e)
        }
    }

    private fun ensurePlaybackCallback() {
        if (playbackCallback != null) return
        val ctx = shellContext ?: getSystemContext()
        val audioManager = ctx.getSystemService(Context.AUDIO_SERVICE) as? AudioManager ?: return
        val cb = object : AudioManager.AudioPlaybackCallback() {
            override fun onPlaybackConfigChanged(configs: List<AudioPlaybackConfiguration>) {
                reapplyAllPackageVolumes()
            }
        }
        try {
            audioManager.registerAudioPlaybackCallback(cb, null)
            playbackCallback = cb
        } catch (e: Exception) {
            logSwallowed("SonicIsolationUserService", "ensurePlaybackCallback", e)
        }
    }

    private fun reapplyAllPackageVolumes() {
        if (packageVolumes.isEmpty()) return
        val ctx = shellContext ?: getSystemContext()
        val pm = ctx.packageManager
        val audioManager = ctx.getSystemService(Context.AUDIO_SERVICE) as? AudioManager ?: return
        try {
            val configs = audioManager.activePlaybackConfigurations
            for (config in configs) {
                val uid = try {
                    config.javaClass.getMethod("getClientUid").invoke(config) as? Int ?: -1
                } catch (_: Exception) { -1 }
                if (uid > 10000) {
                    val pkgs = try { pm.getPackagesForUid(uid) } catch (_: Exception) { null }
                    val pkg = pkgs?.firstOrNull() ?: continue
                    val vol = packageVolumes[pkg]
                    if (vol != null && vol != 1.0f) {
                        applyConfigVolume(config, vol)
                    }
                }
            }
        } catch (e: Exception) {
            logSwallowed("SonicIsolationUserService", "reapplyAllPackageVolumes", e)
        }
    }

    override fun initialize(appPackageName: String) {
        val systemContext = getSystemContext()
        initContexts(systemContext, appPackageName)
    }

    override fun startPipeline() {
        if (shellContext == null) {
            initialize("com.sbf.lightspeed.nightly")
        }
        val shell = shellContext ?: return
        val app = appContext ?: shell
        ShellAttributionContext.installShellAttribution(shell)
        synchronized(this) {
            if (pipeline == null) {
                pipeline = SonicPrivilegedAudioPipeline(shell, app, mixer) {
                    activeRoutedUids.keys.toSet()
                }
            }
            pipeline?.start()
        }
    }

    override fun stopPipeline() {
        synchronized(this) {
            pipeline?.stop()
            pipeline = null
        }
    }

    override fun isPipelineRunning(): Boolean = pipeline?.isRunning() == true

    override fun setVocalBoostLevel(level: Float) {
        mixer.vocalBoostLevel = level
    }

    override fun setBackgroundMuffleLevel(level: Float) {
        mixer.backgroundMuffleLevel = level
    }

    override fun getActiveAudioPackages(): MutableList<String> {
        val result = linkedSetOf<String>()
        val ctx = shellContext ?: getSystemContext()
        val pm = ctx.packageManager
        val audioManager = ctx.getSystemService(Context.AUDIO_SERVICE) as? AudioManager ?: return result.toMutableList()
        try {
            val configs: List<AudioPlaybackConfiguration> = audioManager.activePlaybackConfigurations
            for (config in configs) {
                val uid = try {
                    config.javaClass.getMethod("getClientUid").invoke(config) as? Int ?: -1
                } catch (_: Exception) { -1 }
                if (uid > 10000) {
                    val pkgs = try { pm.getPackagesForUid(uid) } catch (_: Exception) { null }
                    val pkg = pkgs?.firstOrNull()
                    if (!pkg.isNullOrBlank()) {
                        result.add(pkg)
                        val savedVol = packageVolumes[pkg]
                        if (savedVol != null && savedVol != 1.0f) {
                            applyConfigVolume(config, savedVol)
                        }
                    }
                }
            }
        } catch (e: Exception) {
            logSwallowed("SonicIsolationUserService", "getActiveAudioPackages", e)
        }
        return result.toMutableList()
    }

    override fun setPackageVolume(packageName: String, volume: Float): Boolean {
        val clamped = volume.coerceIn(0.0f, 2.0f)
        packageVolumes[packageName] = clamped
        val ctx = shellContext ?: getSystemContext()
        val pm = ctx.packageManager
        val audioManager = ctx.getSystemService(Context.AUDIO_SERVICE) as? AudioManager ?: return false
        var applied = false
        try {
            val targetUid = try { pm.getApplicationInfo(packageName, 0).uid } catch (_: Exception) { -1 }
            val configs = audioManager.activePlaybackConfigurations
            for (config in configs) {
                val uid = try {
                    config.javaClass.getMethod("getClientUid").invoke(config) as? Int ?: -1
                } catch (_: Exception) { -1 }
                val pkgs = if (uid > 10000) try { pm.getPackagesForUid(uid) } catch (_: Exception) { null } else null
                if (uid == targetUid || pkgs?.contains(packageName) == true) {
                    if (applyConfigVolume(config, clamped)) {
                        applied = true
                    }
                }
            }
        } catch (e: Exception) {
            logSwallowed("SonicIsolationUserService", "setPackageVolume", e)
        }
        return applied
    }

    override fun resetAllPackageVolumes() {
        packageVolumes.clear()
        val ctx = shellContext ?: getSystemContext()
        val audioManager = ctx.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
        try {
            audioManager?.activePlaybackConfigurations?.forEach { config ->
                applyConfigVolume(config, 1.0f)
            }
        } catch (_: Exception) {}
        sessionEnhancers.values.forEach {
            try { it.enabled = false; it.release() } catch (_: Exception) {}
        }
        sessionEnhancers.clear()
    }

    private fun applyConfigVolume(config: AudioPlaybackConfiguration, volume: Float): Boolean {
        return try {
            val proxy = config.javaClass.getMethod("getPlayerProxy").invoke(config) ?: return false
            val setVol = proxy.javaClass.getMethod("setVolume", java.lang.Float.TYPE)
            val sessionId = try { config.javaClass.getMethod("getSessionId").invoke(config) as? Int ?: 0 } catch (_: Exception) { 0 }
            if (volume <= 1.0f) {
                sessionEnhancers.remove(sessionId)?.let {
                    try { it.enabled = false; it.release() } catch (_: Exception) {}
                }
                setVol.invoke(proxy, volume)
            } else {
                setVol.invoke(proxy, 1.0f)
                if (sessionId > 0) {
                    val gainMb = ((volume - 1.0f) * 1000f).toInt().coerceIn(0, 1000)
                    val enhancer = sessionEnhancers.getOrPut(sessionId) { LoudnessEnhancer(sessionId) }
                    enhancer.setTargetGain(gainMb)
                    enhancer.enabled = gainMb > 0
                }
            }
            true
        } catch (e: Exception) {
            logSwallowed("SonicIsolationUserService", "applyConfigVolume", e)
            false
        }
    }

    @Synchronized
    override fun setAppRoute(uid: Int, route: String) {
        if (route.isBlank() || route == SonicChannelRouter.ROUTE_DEFAULT) {
            activeRoutedUids.remove(uid)
        } else {
            activeRoutedUids[uid] = route
        }
        rebuildRoutingPolicy()
        updatePipelineExclusions()
    }

    @Synchronized
    override fun clearAppRoute(uid: Int) {
        if (activeRoutedUids.remove(uid) != null) {
            rebuildRoutingPolicy()
            updatePipelineExclusions()
        }
    }

    @Synchronized
    override fun clearAllAppRoutes() {
        activeRoutedUids.clear()
        unregisterRoutingPolicy()
        updatePipelineExclusions()
    }

    private fun updatePipelineExclusions() {
        synchronized(this) {
            if (pipeline?.isRunning() == true) {
                pipeline?.stop()
                pipeline?.start()
            }
        }
    }

    private var clientDeathRecipient: IBinder.DeathRecipient? = null

    override fun registerClientToken(token: IBinder?) {
        if (token == null) return
        try {
            clientDeathRecipient?.let { old ->
                try { token.unlinkToDeath(old, 0) } catch (_: Exception) {}
            }
            val recipient = IBinder.DeathRecipient {
                // Lightspeed client died or was force-stopped: terminate daemon immediately
                destroy()
            }
            token.linkToDeath(recipient, 0)
            clientDeathRecipient = recipient
        } catch (e: Exception) {
            logSwallowed("SonicIsolationUserService", "registerClientToken", e)
        }
    }

    override fun setMultiAudioFocus(enabled: Boolean) {
        try {
            val asBinder = Class.forName("android.os.ServiceManager")
                .getMethod("getService", String::class.java)
                .invoke(null, "audio") as? IBinder ?: return
            val asInterface = Class.forName("android.media.IAudioService\$Stub")
                .getMethod("asInterface", IBinder::class.java)
                .invoke(null, asBinder) ?: return
            val method = asInterface.javaClass.methods.firstOrNull {
                it.name == "setMultiAudioFocusEnabled" && it.parameterTypes.size == 1 && it.parameterTypes[0] == Boolean::class.javaPrimitiveType
            } ?: asInterface.javaClass.declaredMethods.firstOrNull {
                it.name == "setMultiAudioFocusEnabled" && it.parameterTypes.size == 1
            }?.apply { isAccessible = true }
            method?.invoke(asInterface, enabled)
        } catch (e: Exception) {
            logSwallowed("SonicIsolationUserService", "setMultiAudioFocus", e)
        }
    }

    override fun destroy() {
        stopPipeline()
        clearAllAppRoutes()
        resetAllPackageVolumes()
        try {
            val ctx = shellContext ?: getSystemContext()
            playbackCallback?.let {
                val audioManager = ctx.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
                audioManager?.unregisterAudioPlaybackCallback(it)
            }
        } catch (_: Exception) {}
        playbackCallback = null
        try {
            Process.killProcess(Process.myPid())
        } catch (_: Exception) {}
        System.exit(0)
    }

    private fun unregisterRoutingPolicy() {
        val policy = activeRoutingPolicy ?: return
        activeRoutingPolicy = null
        try {
            val policyClass = Class.forName("android.media.audiopolicy.AudioPolicy")
            try {
                val unregStatic = AudioManager::class.java.getDeclaredMethod("unregisterAudioPolicyAsyncStatic", policyClass)
                unregStatic.isAccessible = true
                unregStatic.invoke(null, policy)
            } catch (_: Exception) {
                val shell = shellContext ?: getSystemContext()
                val audioManager = shell.getSystemService(Context.AUDIO_SERVICE) as? AudioManager ?: return
                val unregMethod = AudioManager::class.java.getDeclaredMethod("unregisterAudioPolicyAsync", policyClass)
                unregMethod.isAccessible = true
                unregMethod.invoke(audioManager, policy)
            }
        } catch (e: Exception) {
            logSwallowed("SonicIsolationUserService", "unregisterRoutingPolicy", e)
        }
    }

    private fun rebuildRoutingPolicy() {
        unregisterRoutingPolicy()
        val shell = shellContext ?: getSystemContext()
        if (activeRoutedUids.isEmpty()) return

        val audioManager = shell.getSystemService(Context.AUDIO_SERVICE) as? AudioManager ?: return

        try {
            val policyClass = Class.forName("android.media.audiopolicy.AudioPolicy")
            val policyBuilderClass = Class.forName("android.media.audiopolicy.AudioPolicy\$Builder")
            val mixClass = Class.forName("android.media.audiopolicy.AudioMix")
            val mixBuilderClass = Class.forName("android.media.audiopolicy.AudioMix\$Builder")
            val ruleClass = Class.forName("android.media.audiopolicy.AudioMixingRule")
            val ruleBuilderClass = Class.forName("android.media.audiopolicy.AudioMixingRule\$Builder")

            val policyBuilder = try {
                policyBuilderClass.getConstructor(Context::class.java).newInstance(null as Context?)
            } catch (_: Exception) {
                policyBuilderClass.getConstructor(Context::class.java).newInstance(shell)
            }
            val addMixMethod = policyBuilderClass.getMethod("addMix", mixClass)
            val addMixRuleMethod = ruleBuilderClass.methods.firstOrNull {
                (it.name == "addMixRule" || it.name == "addMixRuleInternal") && it.parameterTypes.size == 2
            } ?: ruleBuilderClass.declaredMethods.firstOrNull {
                (it.name == "addMixRule" || it.name == "addMixRuleInternal") && it.parameterTypes.size == 2
            }?.apply { isAccessible = true } ?: return

            val buildRuleMethod = ruleBuilderClass.getMethod("build")

            val setFormatMethod = mixBuilderClass.getMethod("setFormat", AudioFormat::class.java)
            val setDeviceMethod = mixBuilderClass.methods.firstOrNull {
                it.name == "setDevice" && it.parameterTypes.size == 1 && it.parameterTypes[0] == AudioDeviceInfo::class.java
            } ?: mixBuilderClass.declaredMethods.firstOrNull {
                it.name == "setDevice" && it.parameterTypes.size == 1
            }?.apply { isAccessible = true } ?: return

            val setRouteFlagsMethod = mixBuilderClass.getMethod("setRouteFlags", Int::class.javaPrimitiveType)
            val buildMixMethod = mixBuilderClass.getMethod("build")

            val format = AudioFormat.Builder()
                .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                .setSampleRate(48000)
                .setChannelMask(AudioFormat.CHANNEL_OUT_STEREO)
                .build()

            var mixesAdded = 0
            val groupedByRoute = activeRoutedUids.entries.groupBy({ it.value }, { it.key })

            for ((route, uids) in groupedByRoute) {
                val targetDevice = findTargetDevice(audioManager, route) ?: continue
                for (chunk in uids.chunked(MAX_UIDS_PER_MIX)) {
                    val ruleBuilder = ruleBuilderClass.getDeclaredConstructor().newInstance()
                    for (uid in chunk) {
                        addMixRuleMethod.invoke(ruleBuilder, RULE_MATCH_UID, Integer.valueOf(uid))
                    }
                    val rule = buildRuleMethod.invoke(ruleBuilder)
                    val mixBuilder = mixBuilderClass.getConstructor(ruleClass).newInstance(rule)
                    setFormatMethod.invoke(mixBuilder, format)
                    setRouteFlagsMethod.invoke(mixBuilder, ROUTE_FLAG_RENDER)
                    setDeviceMethod.invoke(mixBuilder, targetDevice)
                    val mix = buildMixMethod.invoke(mixBuilder)
                    addMixMethod.invoke(policyBuilder, mix)
                    mixesAdded++
                }
            }

            if (mixesAdded == 0) return

            val policy = policyBuilderClass.getMethod("build").invoke(policyBuilder)
            val status = try {
                val regStatic = AudioManager::class.java.getDeclaredMethod("registerAudioPolicyStatic", policyClass)
                regStatic.isAccessible = true
                regStatic.invoke(null, policy) as? Int ?: -1
            } catch (_: Exception) {
                val regMethod = AudioManager::class.java.getDeclaredMethod("registerAudioPolicy", policyClass)
                regMethod.isAccessible = true
                regMethod.invoke(audioManager, policy) as? Int ?: -1
            }
            if (status == 0 /* AudioManager.SUCCESS */) {
                activeRoutingPolicy = policy
                setMultiAudioFocus(true)
            }
        } catch (e: Exception) {
            logSwallowed("SonicIsolationUserService", "rebuildRoutingPolicy", e)
        }
    }

    private fun findTargetDevice(audioManager: AudioManager, route: String): AudioDeviceInfo? {
        val outputs: Array<AudioDeviceInfo> = try {
            val getDevicesStatic = AudioManager::class.java.getDeclaredMethod("getDevicesStatic", Int::class.javaPrimitiveType)
            getDevicesStatic.isAccessible = true
            @Suppress("UNCHECKED_CAST")
            getDevicesStatic.invoke(null, AudioManager.GET_DEVICES_OUTPUTS) as? Array<AudioDeviceInfo>
        } catch (_: Exception) {
            null
        } ?: try {
            audioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS)
        } catch (_: Exception) {
            emptyArray()
        } ?: emptyArray()

        return when (route) {
            SonicChannelRouter.ROUTE_SPEAKER -> outputs.firstOrNull {
                it.type == AudioDeviceInfo.TYPE_BUILTIN_SPEAKER ||
                it.type == 24 /* TYPE_BUILTIN_SPEAKER_SAFE */
            } ?: outputs.firstOrNull { it.type == AudioDeviceInfo.TYPE_BUILTIN_EARPIECE }
            SonicChannelRouter.ROUTE_BLUETOOTH -> outputs.firstOrNull {
                it.type == AudioDeviceInfo.TYPE_BLUETOOTH_A2DP ||
                it.type == 26 /* BLE_HEADSET */ ||
                it.type == 27 /* BLE_SPEAKER */ ||
                it.type == 30 /* BLE_BROADCAST */ ||
                it.type == 23 /* HEARING_AID */
            } ?: outputs.firstOrNull {
                it.type == AudioDeviceInfo.TYPE_BLUETOOTH_SCO
            }
            SonicChannelRouter.ROUTE_WIRED -> outputs.firstOrNull {
                it.type == AudioDeviceInfo.TYPE_WIRED_HEADSET ||
                it.type == AudioDeviceInfo.TYPE_WIRED_HEADPHONES ||
                it.type == AudioDeviceInfo.TYPE_USB_HEADSET ||
                it.type == AudioDeviceInfo.TYPE_USB_DEVICE ||
                it.type == 12 /* TYPE_USB_ACCESSORY */ ||
                it.type == 13 /* TYPE_DOCK */
            }
            else -> null
        }
    }

    private fun getSystemContext(): Context {
        return try {
            val activityThreadClass = Class.forName("android.app.ActivityThread")
            val currentThread = try {
                activityThreadClass.getMethod("currentActivityThread").invoke(null)
            } catch (_: Exception) { null } ?: activityThreadClass.getMethod("systemMain").invoke(null)
            val getSystemContextMethod = activityThreadClass.getMethod("getSystemContext")
            getSystemContextMethod.invoke(currentThread) as Context
        } catch (_: Exception) {
            val activityThreadClass = Class.forName("android.app.ActivityThread")
            val currentAppMethod = activityThreadClass.getMethod("currentApplication")
            currentAppMethod.invoke(null) as Context
        }
    }
}
