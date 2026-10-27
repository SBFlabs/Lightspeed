package com.sbf.lightspeed.system.sonic

import android.content.Context
import android.content.pm.PackageManager
import com.sbf.lightspeed.system.ElevatedTaskCloser
import com.sbf.lightspeed.system.LightspeedAppSovereigntyEngine
import com.sbf.lightspeed.system.LightspeedPreferences
import com.sbf.lightspeed.system.defaultPrefs
import com.sbf.lightspeed.system.logSwallowed

/**
 * Multi-Channel Audio Routing & Audio Focus Sovereignty Engine.
 *
 * Routes per-package audio to Speaker, Bluetooth, or Wired Headphones using
 * privileged Shizuku UserService (UID 2000 / shell with MODIFY_AUDIO_ROUTING).
 * Integrates with LightspeedAppSovereigntyEngine for non-ducking multi-audio playback.
 */
object SonicChannelRouter {

    const val ROUTE_DEFAULT = "default"
    const val ROUTE_SPEAKER = "speaker"
    const val ROUTE_BLUETOOTH = "bt"
    const val ROUTE_WIRED = "wired"

    private var appContext: Context? = null

    fun getAppRoute(context: Context, packageName: String): String {
        return context.defaultPrefs().getString(
            LightspeedPreferences.KEY_SONIC_CHANNEL_ROUTING_PREFIX + packageName,
            ROUTE_DEFAULT
        ) ?: ROUTE_DEFAULT
    }

    fun setAppRoute(context: Context, packageName: String, route: String) {
        val appCtx = context.applicationContext ?: context
        appContext = appCtx
        val prefs = appCtx.defaultPrefs()
        val key = LightspeedPreferences.KEY_SONIC_CHANNEL_ROUTING_PREFIX + packageName
        if (route == ROUTE_DEFAULT) {
            prefs.edit().remove(key).apply()
        } else {
            prefs.edit().putString(key, route).apply()
        }
        applyRouteForPackage(appCtx, packageName, route)
    }

    fun isAlwaysOnAudioFocus(context: Context, packageName: String): Boolean {
        return context.defaultPrefs().getBoolean(
            LightspeedPreferences.KEY_SONIC_ALWAYS_AUDIO_FOCUS_PREFIX + packageName,
            false
        )
    }

    fun setAlwaysOnAudioFocus(context: Context, packageName: String, enabled: Boolean) {
        val appCtx = context.applicationContext ?: context
        appCtx.defaultPrefs().edit().putBoolean(
            LightspeedPreferences.KEY_SONIC_ALWAYS_AUDIO_FOCUS_PREFIX + packageName,
            enabled
        ).apply()

        // Sync with unified LightspeedAppSovereigntyEngine
        LightspeedAppSovereigntyEngine.setAppStealthLocked(packageName, enabled, appCtx)

        if (ElevatedTaskCloser.isShizukuActive) {
            // AppOps TAKE_AUDIO_FOCUS: "ignore" isolates the app from audio focus ducking/pausing; "allow" restores default behavior
            val mode = if (enabled) "ignore" else "allow"
            val quoted = "'" + packageName.replace("'", "'\\''") + "'"
            val uid = try {
                appCtx.packageManager.getApplicationInfo(packageName, 0).uid
            } catch (_: Exception) {
                -1
            }
            val uidCmd = if (uid > 0) "cmd appops set --uid $uid TAKE_AUDIO_FOCUS $mode; " else ""
            val multiFocusCmd = if (enabled) "; settings put system multi_audio_focus_enabled 1; settings put global multi_audio_focus_enabled 1" else ""
            ElevatedTaskCloser.execShizuku("${uidCmd}cmd appops set $quoted TAKE_AUDIO_FOCUS $mode$multiFocusCmd")
            try {
                SonicVoiceIsolationEngine.userService?.setMultiAudioFocus(enabled)
            } catch (_: Exception) {}
        }
    }

    fun getPinnedApps(context: Context): Set<String> {
        val raw = context.defaultPrefs().getString(LightspeedPreferences.KEY_SONIC_PINNED_APPS, "") ?: ""
        return raw.split(",").map { it.trim() }.filter { it.isNotEmpty() }.toSet()
    }

    fun togglePinApp(context: Context, packageName: String): Boolean {
        val appCtx = context.applicationContext ?: context
        val current = getPinnedApps(appCtx).toMutableSet()
        val nowPinned = if (current.contains(packageName)) {
            current.remove(packageName)
            false
        } else {
            current.add(packageName)
            // Un-ignore if pinning
            val ignored = getIgnoredApps(appCtx).toMutableSet()
            if (ignored.remove(packageName)) {
                appCtx.defaultPrefs().edit()
                    .putString(LightspeedPreferences.KEY_SONIC_IGNORED_APPS, ignored.joinToString(","))
                    .apply()
            }
            true
        }
        appCtx.defaultPrefs().edit()
            .putString(LightspeedPreferences.KEY_SONIC_PINNED_APPS, current.joinToString(","))
            .apply()
        return nowPinned
    }

    fun getIgnoredApps(context: Context): Set<String> {
        val raw = context.defaultPrefs().getString(LightspeedPreferences.KEY_SONIC_IGNORED_APPS, "") ?: ""
        return raw.split(",").map { it.trim() }.filter { it.isNotEmpty() }.toSet()
    }

    fun toggleIgnoreApp(context: Context, packageName: String): Boolean {
        val appCtx = context.applicationContext ?: context
        val current = getIgnoredApps(appCtx).toMutableSet()
        val nowIgnored = if (current.contains(packageName)) {
            current.remove(packageName)
            false
        } else {
            current.add(packageName)
            // Un-pin and reset overrides when ignoring
            val pinned = getPinnedApps(appCtx).toMutableSet()
            if (pinned.remove(packageName)) {
                appCtx.defaultPrefs().edit()
                    .putString(LightspeedPreferences.KEY_SONIC_PINNED_APPS, pinned.joinToString(","))
                    .apply()
            }
            SonicAppVolumeEngine.setAppVolume(appCtx, packageName, 1.0f)
            setAppRoute(appCtx, packageName, ROUTE_DEFAULT)
            setAlwaysOnAudioFocus(appCtx, packageName, false)
            true
        }
        appCtx.defaultPrefs().edit()
            .putString(LightspeedPreferences.KEY_SONIC_IGNORED_APPS, current.joinToString(","))
            .apply()
        return nowIgnored
    }

    fun applyRoutingRules(context: Context) {
        val appCtx = context.applicationContext ?: context
        appContext = appCtx
        SonicVoiceIsolationEngine.initialize(appCtx)
        SonicVoiceIsolationEngine.ensureBound(appCtx)
        reapplyPrivilegedRoutes(appCtx)
    }

    fun reapplyPrivilegedRoutes(context: Context? = null) {
        val ctx = context?.applicationContext ?: appContext ?: return
        appContext = ctx
        val userSvc = SonicVoiceIsolationEngine.userService ?: return
        val pm = ctx.packageManager
        val allRoutes = ctx.defaultPrefs().all.filterKeys {
            it.startsWith(LightspeedPreferences.KEY_SONIC_CHANNEL_ROUTING_PREFIX)
        }

        if (allRoutes.isEmpty()) {
            try {
                userSvc.clearAllAppRoutes()
            } catch (_: Exception) {}
            return
        }

        for ((prefKey, routeVal) in allRoutes) {
            val pkg = prefKey.removePrefix(LightspeedPreferences.KEY_SONIC_CHANNEL_ROUTING_PREFIX)
            val route = routeVal.toString()
            try {
                val uid = pm.getApplicationInfo(pkg, 0).uid
                userSvc.setAppRoute(uid, route)
            } catch (e: Exception) {
                logSwallowed("SonicChannelRouter", "reapply:$pkg", e)
            }
        }
    }

    private fun applyRouteForPackage(context: Context, packageName: String, route: String) {
        val userSvc = SonicVoiceIsolationEngine.userService
        if (userSvc == null) {
            SonicVoiceIsolationEngine.ensureBound(context)
            return
        }
        try {
            val uid = context.packageManager.getApplicationInfo(packageName, 0).uid
            if (route == ROUTE_DEFAULT) {
                userSvc.clearAppRoute(uid)
            } else {
                userSvc.setAppRoute(uid, route)
            }
        } catch (e: Exception) {
            logSwallowed("SonicChannelRouter", "applyRoute:$packageName", e)
        }
    }

    fun resetAllRouting(context: Context) {
        val appCtx = context.applicationContext ?: context
        val editor = appCtx.defaultPrefs().edit()
        val all = appCtx.defaultPrefs().all
        for (k in all.keys) {
            if (k.startsWith(LightspeedPreferences.KEY_SONIC_CHANNEL_ROUTING_PREFIX)) {
                editor.remove(k)
            }
        }
        editor.apply()
        try {
            SonicVoiceIsolationEngine.userService?.clearAllAppRoutes()
        } catch (_: Exception) {}
    }

    fun unregisterPolicy(audioManager: android.media.AudioManager? = null) {
        teardown()
    }

    fun teardown(context: Context? = null) {
        try {
            SonicVoiceIsolationEngine.userService?.clearAllAppRoutes()
        } catch (_: Exception) {}
    }
}
