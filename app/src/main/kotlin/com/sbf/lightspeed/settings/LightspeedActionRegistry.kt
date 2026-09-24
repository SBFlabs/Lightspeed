package com.sbf.lightspeed.settings

import com.sbf.lightspeed.system.defaultPrefs
import com.sbf.lightspeed.system.LightspeedPreferences
import android.content.Context
import android.content.Intent
import android.content.pm.LauncherApps
import android.content.pm.PackageManager
import android.os.Build
import android.os.Process
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import com.sbf.lightspeed.system.ElevatedTaskCloser
import com.sbf.lightspeed.system.LightspeedIconManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Registry for system, application, and shortcut launch action tokens.
 * Handles background indexing, icon pre-warming, and dynamic label resolution.
 */
object LightspeedActionRegistry {
    var isIndexed by mutableStateOf(false)
    val allTokens = mutableStateListOf<String>()
    val labelCache = mutableStateMapOf<String, String>()
    val iconBitmapCache = java.util.concurrent.ConcurrentHashMap<String, android.graphics.Bitmap>()

    fun getIconBitmap(context: Context, pkg: String, useCache: Boolean = true): android.graphics.Bitmap? {
        return LightspeedIconManager.getIconBitmap(context, pkg, useCache)
    }

    fun getBaseTokens(): List<String> = listOf(
        "none",
        // 1. Navigation & Multitasking
        "action_enter_gearset_nav",
        "system:previous_app",
        "system:close_app",
        "system:recents",
        "system:home",
        "system:back",
        "system:split_screen",
        "system:popup_window",

        // 2. Hardware & System Controls
        "system:flashlight",
        "system:screenshot",
        "system:lock_screen",
        "system:notifications",
        "system:quick_settings",
        "system:scroll_to_top",
        "system:auto_rotate_toggle",
        "system:gravity_reset",
        "system:gravity_override_portrait",
        "system:gravity_override_sensor_portrait",
        "system:gravity_override_landscape",
        "system:gravity_override_360",

        // 3. Gesture Scrubbers & Sliders
        "system:screen_timeout",
        "system:volume",
        "system:brightness",

        // 4. Media & Playback Actions
        "system:media_play_pause",
        "system:media_next",
        "system:media_prev",
        "system:media_skip_forward",
        "system:media_skip_backward",
        "system:media_scrubber",
        "system:media_stop",

        // 5. Ambient Dashboards & Tactical Quick Action Tools
        "system:refueling_bay",
        "system:core_cooling",
        "system:perimeter_watchdog",
        "system:core_watchdog",
        "system:central_command",
        "system:omniscient_audio"
    )

    fun initializeSync(context: Context) {
        if (allTokens.isEmpty()) {
            val base = getBaseTokens()
            allTokens.addAll(base)
            base.forEach { labelCache[it] = resolveDynamicTokenLabel(context, it) }

            // Instant Phase 0: Fast synchronous load of launcher apps (< 30ms)
            try {
                val pm = context.packageManager
                val mainIntent = Intent(Intent.ACTION_MAIN, null).apply { addCategory(Intent.CATEGORY_LAUNCHER) }
                val resolvedApps = pm.queryIntentActivities(mainIntent, 0)
                val appTokens = mutableListOf<String>()
                resolvedApps.forEach { resolveInfo ->
                    val pkg = resolveInfo.activityInfo.packageName
                    val appToken = "app:$pkg"
                    appTokens.add(appToken)
                    val appLabel = pm.getApplicationLabel(resolveInfo.activityInfo.applicationInfo).toString()
                    labelCache[appToken] = appLabel
                }
                allTokens.addAll(appTokens.distinct())
            } catch (_: Exception) {}
        }
    }

    suspend fun ensureIndexed(context: Context) {
        initializeSync(context)
        if (isIndexed) return

        withContext(Dispatchers.IO) {
            val baseTokens = mutableListOf<String>()
            baseTokens.addAll(getBaseTokens())
            val temporaryLabels = mutableMapOf<String, String>()
            baseTokens.forEach { temporaryLabels[it] = resolveDynamicTokenLabel(context, it) }

            val shizukuShortcutsMap = mutableMapOf<String, MutableList<Pair<String, String>>>()
            if (ElevatedTaskCloser.isShizukuActive) {
                try {
                    val proc = ElevatedTaskCloser.execShizuku("dumpsys shortcut")
                    if (proc != null) {
                        val output = proc.inputStream.bufferedReader().readText()
                        proc.waitFor()

                        val packageSections = output.split(Regex("""(?m)^\s*Package:\s*""")).drop(1)
                        val idRegex = Regex("""id=([^,\r\n]+)""")
                        val labelRegex = Regex("""shortLabel=([^,\r\n]+)""")

                        for (sec in packageSections) {
                            val pkg = sec.lineSequence().firstOrNull()?.trim()?.split(Regex("""\s+"""))?.firstOrNull()?.trim() ?: ""
                            if (pkg.isBlank()) continue

                            val shortcutBlocks = sec.split("ShortcutInfo {").drop(1)
                            for (block in shortcutBlocks) {
                                val idMatch = idRegex.find(block)?.groupValues?.getOrNull(1)?.trim()
                                val labelMatch = labelRegex.find(block)?.groupValues?.getOrNull(1)?.trim()

                                if (!idMatch.isNullOrBlank()) {
                                    val cleanId = idMatch.removeSurrounding("\"")
                                    val cleanLabel = (labelMatch ?: cleanId).trim().removeSurrounding("\"")
                                    shizukuShortcutsMap.getOrPut(pkg) { mutableListOf() }
                                        .add(Pair(cleanId, cleanLabel))
                                }
                            }
                        }
                    }
                } catch (_: Exception) {}
            }

            try {
                val pm = context.packageManager
                val mainIntent = Intent(Intent.ACTION_MAIN, null).apply { addCategory(Intent.CATEGORY_LAUNCHER) }
                val resolvedApps = pm.queryIntentActivities(mainIntent, 0)

                resolvedApps.forEach { resolveInfo ->
                    val pkg = resolveInfo.activityInfo.packageName
                    val appToken = "app:$pkg"
                    baseTokens.add(appToken)
                    val appLabel = pm.getApplicationLabel(resolveInfo.activityInfo.applicationInfo).toString()
                    temporaryLabels[appToken] = appLabel

                    // Pre-warm icon cache on background worker thread
                    // getIconBitmap(context, pkg)

                    // 1. Exported Activity Deep Links
                    try {
                        val pkgInfo = pm.getPackageInfo(pkg, PackageManager.GET_ACTIVITIES)
                        pkgInfo.activities?.forEach { activityInfo ->
                            if (activityInfo.exported && activityInfo.name != resolveInfo.activityInfo.name) {
                                val actLabel = activityInfo.loadLabel(pm).toString().takeIf { it.isNotBlank() } ?: activityInfo.name.substringAfterLast(".")
                                val displayLabel = if (actLabel != appLabel) "$appLabel ($actLabel)" else "$appLabel (${activityInfo.name.substringAfterLast(".")})"
                                val activityToken = "shortcut:label=" + displayLabel + ";pkg=" + pkg + ";type=activity;activity=" + activityInfo.name
                                baseTokens.add(activityToken)
                                temporaryLabels[activityToken] = displayLabel
                            }
                        }
                    } catch (_: Exception) {}

                    // 2. ACTION_CREATE_SHORTCUT Plugins
                    try {
                        val shortcutIntent = Intent(Intent.ACTION_CREATE_SHORTCUT).setPackage(pkg)
                        pm.queryIntentActivities(shortcutIntent, 0).forEach { pluginInfo ->
                            val pluginLabel = pluginInfo.loadLabel(pm).toString().takeIf { it.isNotBlank() } ?: pluginInfo.activityInfo.name.substringAfterLast(".")
                            val pluginToken = "shortcut:label=" + pluginLabel + ";pkg=" + pkg + ";type=app_shortcut;activity=" + pluginInfo.activityInfo.name
                            baseTokens.add(pluginToken)
                            temporaryLabels[pluginToken] = pluginLabel
                        }
                    } catch (_: Exception) {}

                    // 3. LauncherApps & Shizuku Dynamic / Home Shortcuts (Android 7.1+)
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N_MR1) {
                        val existingIds = mutableSetOf<String>()
                        try {
                            val launcherApps = context.getSystemService(Context.LAUNCHER_APPS_SERVICE) as LauncherApps
                            val query = LauncherApps.ShortcutQuery().apply {
                                setPackage(pkg)
                                setQueryFlags(LauncherApps.ShortcutQuery.FLAG_MATCH_DYNAMIC or LauncherApps.ShortcutQuery.FLAG_MATCH_PINNED or LauncherApps.ShortcutQuery.FLAG_MATCH_MANIFEST)
                            }
                            launcherApps.getShortcuts(query, Process.myUserHandle())?.forEach { shortcut ->
                                val shortLabel = shortcut.shortLabel?.toString() ?: "Shortcut"
                                val displayLabel = "$appLabel ($shortLabel)"
                                val shortcutToken = "shortcut:label=" + displayLabel + ";pkg=" + pkg + ";type=home_shortcut;id=" + shortcut.id
                                baseTokens.add(shortcutToken)
                                temporaryLabels[shortcutToken] = displayLabel
                                existingIds.add(shortcut.id)
                            }
                        } catch (_: Exception) {}

                        // Add pre-parsed Shizuku shortcuts for THIS specific package that were not already found
                        shizukuShortcutsMap[pkg]?.forEach { (shortcutId, label) ->
                            if (!existingIds.contains(shortcutId)) {
                                val displayLabel = "$appLabel ($label)"
                                val shortcutToken = "shortcut:label=$displayLabel;pkg=$pkg;type=home_shortcut;id=$shortcutId"
                                baseTokens.add(shortcutToken)
                                temporaryLabels[shortcutToken] = displayLabel
                            }
                        }
                    }
                }

                // Also process any packages from Shizuku that might not have a main launcher activity
                val processedPkgs = resolvedApps.map { it.activityInfo.packageName }.toSet()
                shizukuShortcutsMap.forEach { (pkg, shortcuts) ->
                    if (!processedPkgs.contains(pkg)) {
                        val appLabel = try {
                            pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString()
                        } catch (_: Exception) { pkg.substringAfterLast(".") }
                        shortcuts.forEach { (shortcutId, label) ->
                            val displayLabel = "$appLabel ($label)"
                            val shortcutToken = "shortcut:label=$displayLabel;pkg=$pkg;type=home_shortcut;id=$shortcutId"
                            baseTokens.add(shortcutToken)
                            temporaryLabels[shortcutToken] = displayLabel
                        }
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }

            // 4. Harvest previously saved or custom configured shortcuts from preferences
            try {
                val prefs = context.defaultPrefs()
                prefs.all.values.forEach { value ->
                    val rawStr = (value as? String)?.trim() ?: return@forEach
                    val candidates = if (rawStr.contains(",")) rawStr.split(",") else listOf(rawStr)
                    for (candidate in candidates) {
                        val token = candidate.trim()
                        if (token.startsWith("shortcut:") && !baseTokens.contains(token)) {
                            baseTokens.add(token)
                            temporaryLabels[token] = resolveDynamicTokenLabel(context, token)
                        }
                    }
                }
            } catch (_: Exception) {}

            val distinctTokens = baseTokens.distinct()
            withContext(Dispatchers.Main) {
                allTokens.clear()
                allTokens.addAll(distinctTokens)
                labelCache.clear()
                labelCache.putAll(temporaryLabels)
                isIndexed = true
            }
        }
    }

    /**
     * Maps action token aliases and legacy identifiers to their canonical representation.
     */
    fun canonicalToken(raw: String?): String = when (raw) {
        null, ""                                               -> "none"
        "action_enter_gearset_nav", "ACTION_ENTER_GEARSET_NAV" -> "system:gearset_nav"
        "ACTION_PREVIOUS_APP", "previous_app"                  -> "system:previous_app"
        "ACTION_SPLIT_SCREEN", "split_screen"                  -> "system:split_screen"
        "popup_window", "system:freeform"                      -> "system:freeform"
        "ACTION_FLASHLIGHT", "flashlight", "system:torch"      -> "system:flashlight"
        "ACTION_SCREENSHOT", "screenshot"                      -> "system:screenshot"
        "ACTION_LOCK_SCREEN", "lock_screen"                    -> "system:lock_screen"
        "system:battery_whitelist"                             -> "system:battery_exemption"
        "ACTION_CLOSE_APP", "close_app"                        -> "system:close_app"
        "ACTION_SCROLL_TO_TOP", "scroll_to_top"                -> "system:scroll_to_top"
        "ACTION_HOME", "home"                                  -> "system:home"
        "ACTION_BACK", "back"                                  -> "system:back"
        "ACTION_RECENTS", "recents"                            -> "system:recents"
        "ACTION_NOTIFICATIONS", "notifications"                -> "system:notifications"
        "ACTION_QUICK_SETTINGS", "quick_settings"              -> "system:quick_settings"
        "ACTION_SCREEN_TIMEOUT", "screen_timeout"              -> "system:screen_timeout"
        "ACTION_MEDIA_PLAY_PAUSE", "media_play_pause"          -> "system:media_play_pause"
        "ACTION_MEDIA_NEXT", "media_next"                      -> "system:media_next"
        "ACTION_MEDIA_PREV", "media_prev"                      -> "system:media_prev"
        "ACTION_MEDIA_SKIP_FORWARD", "media_skip_forward"      -> "system:media_skip_forward"
        "ACTION_MEDIA_SKIP_BACKWARD", "media_skip_backward"    -> "system:media_skip_backward"
        "ACTION_MEDIA_SCRUBBER", "media_scrubber"              -> "system:media_scrubber"
        "ACTION_MEDIA_STOP", "media_stop"                      -> "system:media_stop"
        "ACTION_REFUELING_BAY", "refueling_bay"                -> "system:refueling_bay"
        "ACTION_GLOW_DEFLECTORS", "glow_deflectors"            -> "system:glow_deflectors"
        "system:audio_dock"                                    -> "system:omniscient_audio"
        "ACTION_TACTICAL_FLYOUT", "action_quick_flyout", "system:quick_flyout" -> "system:tactical_flyout"
        "ACTION_LENS", "google_lens"                           -> "system:lens"
        "ACTION_QR_SCANNER", "qr_scanner"                      -> "system:qr_scanner"
        "ACTION_CHATGPT", "chatgpt"                            -> "system:chatgpt"
        "ACTION_CLAUDE", "claude"                              -> "system:claude"
        "ACTION_GEMINI", "gemini"                              -> "system:gemini"
        "ACTION_CAMERA_PHOTO", "camera_photo", "system:camera" -> "system:camera_photo"
        "ACTION_CAMERA_VIDEO", "camera_video"                  -> "system:camera_video"
        "ACTION_CORE_COOLING", "core_cooling"                  -> "system:core_cooling"
        "system:gravity_reset", "ACTION_GRAVITY_RESET"         -> "system:gravity_reset"
        "system:auto_rotate_toggle", "system:toggle_auto_rotate", "auto_rotate_toggle",
        "system:gravity_toggle_master", "system:orientation_toggle", "orientation_toggle" -> "system:auto_rotate_toggle"
        "system:gravity_override_360", "system:orientation_sensor_360", "orientation_sensor_360" -> "system:gravity_override_360"
        "system:gravity_override_landscape"                    -> "system:gravity_override_landscape"
        "system:gravity_override_sensor_portrait", "system:orientation_sensor_portrait", "orientation_sensor_portrait" -> "system:gravity_override_sensor_portrait"
        "system:central_command", "central_command"             -> "system:central_command"
        else -> raw ?: "none"
    }

    /**
     * Resolves the default pre-assigned action token for a given gesture preference key
     * for first-time users or unconfigured gesture vectors.
     */
    fun getDefaultActionForGestureKey(key: String): String {
        val cleanKey = key.substringAfterLast(":string/")

        return when {
            // --- STATUSBAR / SENSOR DECK DEFAULTS ---
            cleanKey == "pref_macro_action_STATUSBAR_DOUBLE_TAP" -> "system:gravity_reset"
            cleanKey == "pref_macro_action_STATUSBAR_DOUBLE_TAP_HOLD" -> "system:screen_timeout"
            cleanKey == "pref_macro_action_STATUSBAR_TAP" -> "system:scroll_to_top"
            cleanKey == "pref_macro_action_STATUSBAR_TAP_HOLD" -> "system:gravity_override_360"
            cleanKey == "pref_macro_action_STATUSBAR_SWIPE_RIGHT" -> "system:core_watchdog"
            cleanKey == "pref_macro_action_STATUSBAR_SWIPE_LEFT" -> "system:perimeter_watchdog"
            cleanKey == "pref_macro_action_STATUSBAR_SWIPE_RIGHT_HOLD" -> "system:popup_window"
            cleanKey == "pref_macro_action_STATUSBAR_SWIPE_LEFT_HOLD" -> "system:split_screen"
            cleanKey == "pref_macro_action_STATUSBAR_SWIPE_DOWN" -> "system:notifications"

            // --- LEFT FLANK DEFAULTS (LEFT_UNIFIED, LEFT_TOP, LEFT_BOTTOM) ---
            cleanKey.contains("LEFT_") -> when {
                cleanKey.endsWith("_SWIPE_RIGHT") -> "system:back"
                cleanKey.endsWith("_SWIPE_RIGHT_HOLD") -> "system:close_app"
                cleanKey.endsWith("_SWIPE_DOWN") -> "system:auto_rotate_toggle"
                cleanKey.endsWith("_SWIPE_DOWN_UP") -> "system:recents"
                cleanKey.endsWith("_SWIPE_UP") -> "system:gravity_override_360"
                cleanKey.endsWith("_SWIPE_UP_DOWN") -> "system:home"
                cleanKey.endsWith("_SWIPE_RIGHT_UP") -> "system:screenshot"
                cleanKey.endsWith("_SWIPE_RIGHT_DOWN") -> "system:notifications"
                cleanKey.endsWith("_SWIPE_RIGHT_DOWN_HOLD") -> "system:quick_settings"
                cleanKey.contains("BOTTOM") && (cleanKey.endsWith("_TAP_HOLD") || cleanKey.endsWith("_SCRUBBING")) -> "system:brightness"
                cleanKey.endsWith("_TAP_HOLD") -> "system:volume"
                cleanKey.endsWith("_SCRUBBING") -> "system:volume"
                else -> "none"
            }

            // --- RIGHT FLANK DEFAULTS (UNIFIED, TOP, BOTTOM) ---
            cleanKey.endsWith("_SWIPE_LEFT") -> "system:back"
            cleanKey.endsWith("_SWIPE_LEFT_HOLD") -> "system:close_app"
            cleanKey.endsWith("_SWIPE_DOWN") -> "system:home"
            cleanKey.endsWith("_SWIPE_UP") -> "system:recents"
            cleanKey.endsWith("_SWIPE_LEFT_DOWN") -> "system:notifications"
            cleanKey.endsWith("_SWIPE_LEFT_DOWN_HOLD") -> "system:quick_settings"
            cleanKey.endsWith("_SWIPE_LEFT_UP") -> "system:previous_app"
            cleanKey.endsWith("_SWIPE_UP_DOWN") -> "system:refueling_bay"
            cleanKey.contains("BOTTOM") && (cleanKey.endsWith("_TAP_HOLD") || cleanKey.endsWith("_SCRUBBING")) -> "system:volume"
            cleanKey.endsWith("_TAP_HOLD") -> "system:brightness"
            cleanKey.endsWith("_SCRUBBING") -> "system:brightness"

            else -> "none"
        }
    }
}

/**
 * Resolves human-readable labels for system and app tokens.
 */
fun resolveDynamicTokenLabel(context: Context, token: String): String {
    return when {
        token == "none" -> "None"
        token == "action_enter_gearset_nav" || token == "system:gearset_nav" -> "Gear Set HUD Navigation Mode"
        token == "system:previous_app" -> "Switch to Previous App (Shizuku)"
        token == "system:close_app" || token == "shizuku:close_app" -> "Close App Gracefully & Remove from Recents (Shizuku)"
        token == "system:recents" -> "Recents Overview"
        token == "system:home" -> "Home"
        token == "system:back" -> "Back"
        token == "system:split_screen" -> "Split Screen"
        token == "system:popup_window" || token == "system:freeform" -> "Pop-up Window"

        token == "system:flashlight" -> "External Torch"
        token == "system:screenshot" -> "Take Screenshot"
        token == "system:lock_screen" -> "Lock Ship"
        token == "system:notifications" -> "Notification Shade"
        token == "system:quick_settings" -> "Quick Settings"
        token == "system:scroll_to_top" -> "Scroll to Top"
        token == "system:auto_rotate_toggle" || token == "system:toggle_auto_rotate" || token == "system:gravity_toggle_master" || token == "ACTION_GRAVITY_TOGGLE_MASTER" || token == "system:orientation_toggle" || token == "ACTION_TOGGLE_ROTATION" -> "Toggle Native Auto-Rotate"
        token == "system:gravity_reset" || token == "ACTION_GRAVITY_RESET" -> "Restore Default Gravity"
        token == "system:gravity_override_360" || token == "ACTION_GRAVITY_OVERRIDE_360" || token == "system:orientation_sensor_360" -> "Force Transient 360° Gyro"
        token == "system:gravity_override_landscape" || token == "ACTION_GRAVITY_OVERRIDE_LANDSCAPE" -> "Force Transient Landscape"
        token == "system:gravity_override_portrait" || token == "ACTION_GRAVITY_OVERRIDE_PORTRAIT" || token == "system:orientation_portrait" -> "Force Transient Portrait"
        token == "system:gravity_override_sensor_portrait" || token == "ACTION_GRAVITY_OVERRIDE_SENSOR_PORTRAIT" || token == "system:orientation_sensor_portrait" -> "Force Transient Sensor Portrait"

        token == "system:screen_timeout" -> "Ship Goes Dark"
        token == "system:volume" -> "Media Volume"
        token == "system:brightness" -> "Brightness Scrubber"

        token == "system:media_play_pause" -> "Media: Play / Pause"
        token == "system:media_next" -> "Media: Next Track"
        token == "system:media_prev" -> "Media: Previous Track"
        token == "system:media_skip_forward" -> {
            val skipSec = context.defaultPrefs().getInt(LightspeedPreferences.KEY_MEDIA_SKIP_SECONDS, 10)
            "Media: Skip Forward (${skipSec}s)"
        }
        token == "system:media_skip_backward" -> {
            val skipSec = context.defaultPrefs().getInt(LightspeedPreferences.KEY_MEDIA_SKIP_SECONDS, 10)
            "Media: Skip Backward (${skipSec}s)"
        }
        token == "system:media_scrubber" -> "Media: Timeline Scrubber (HUD)"
        token == "system:media_stop" -> "Media: Stop Playback"
        token == "system:refueling_bay" -> com.sbf.lightspeed.system.LightspeedLanguageEngine.resolve(com.sbf.lightspeed.system.LightspeedVocabulary.Key.REFUELING_BAY)
        token == "system:core_cooling" || token == "ACTION_CORE_COOLING" -> com.sbf.lightspeed.system.LightspeedLanguageEngine.resolve(com.sbf.lightspeed.system.LightspeedVocabulary.Key.CORE_COOLING)
        token == "system:perimeter_watchdog" -> com.sbf.lightspeed.system.LightspeedLanguageEngine.resolve(com.sbf.lightspeed.system.LightspeedVocabulary.Key.PERIMETER_DEFENSE)
        token == "system:core_watchdog" -> com.sbf.lightspeed.system.LightspeedLanguageEngine.resolve(com.sbf.lightspeed.system.LightspeedVocabulary.Key.CORE_WATCHDOG)
        token == "system:central_command" -> "Central Command (Cockpit Deck)"
        token == "system:omniscient_audio" || token == "system:audio_dock" -> "Omniscient Audio (Multi-App Sound Deck)"
        token.startsWith("app:") -> {
            val pkg = token.removePrefix("app:")
            try {
                val pm = context.packageManager
                pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString()
            } catch (_: Exception) {
                pkg.substringAfterLast(".")
            }
        }
        token.startsWith("shortcut:") -> {
            val resolved = com.sbf.lightspeed.system.LightspeedShortcutManager.resolveLabel(context, token)
            if (resolved.isNotBlank()) resolved else {
                val raw = token.substringAfter("label=").substringBefore(";")
                try { android.net.Uri.decode(raw) } catch (_: Exception) { raw }
            }
        }
        else -> token
    }
}

/**
 * Maps action token aliases and legacy identifiers to their canonical representation.
 */
fun canonicalToken(raw: String?): String = LightspeedActionRegistry.canonicalToken(raw)
