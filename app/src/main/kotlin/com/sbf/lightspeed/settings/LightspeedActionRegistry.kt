package com.sbf.lightspeed.settings

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
import com.sbf.lightspeed.system.readTextOrKill
import com.sbf.lightspeed.system.logSwallowed
import com.sbf.lightspeed.system.defaultPrefs
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
        "system:power_menu",
        "system:notifications",
        "system:quick_settings",
        "system:scroll_to_top",
        "system:scroll_to_bottom",
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
        "system:scroll_scrub",

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
        "system:battery_exemption",
        "system:central_command",
        "system:omniscient_audio",

        // 6. Display Wellness, Vista & Sonic Actions
        "system:smart_pixels_toggle",
        "system:eye_care_toggle",
        "system:extra_dim_toggle",
        "system:vocal_isolation_toggle",
        "system:volume_hud_show",
        "system:vista_deck",

        // 7. Tactical Bot Macros (Clean-Room Gesture Bot)
        "bot:macro_1",
        "bot:macro_2",
        "bot:macro_3",
        "bot:macro_4",
        "bot:macro_5",
        "bot:macro_6",
        "bot:macro_7",
        "bot:macro_8",
        "bot:macro_9",
        "bot:macro_10"
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
            } catch (e: Exception) { logSwallowed("LightspeedActionRegistry.kt", "initializeSync:108", e) }
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
                        val output = proc.readTextOrKill(10000L).orEmpty()

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
                } catch (e: Exception) { logSwallowed("LightspeedActionRegistry.kt", "ensureIndexed:151", e) }
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
                    } catch (e: Exception) { logSwallowed("LightspeedActionRegistry.kt", "ensureIndexed:181", e) }

                    // 2. ACTION_CREATE_SHORTCUT Plugins
                    try {
                        val shortcutIntent = Intent(Intent.ACTION_CREATE_SHORTCUT).setPackage(pkg)
                        pm.queryIntentActivities(shortcutIntent, 0).forEach { pluginInfo ->
                            val pluginLabel = pluginInfo.loadLabel(pm).toString().takeIf { it.isNotBlank() } ?: pluginInfo.activityInfo.name.substringAfterLast(".")
                            val pluginToken = "shortcut:label=" + pluginLabel + ";pkg=" + pkg + ";type=app_shortcut;activity=" + pluginInfo.activityInfo.name
                            baseTokens.add(pluginToken)
                            temporaryLabels[pluginToken] = pluginLabel
                        }
                    } catch (e: Exception) { logSwallowed("LightspeedActionRegistry.kt", "ensureIndexed:192", e) }

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
                        } catch (e: Exception) { logSwallowed("LightspeedActionRegistry.kt", "ensureIndexed:211", e) }

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
            } catch (e: Exception) { logSwallowed("LightspeedActionRegistry.kt", "ensureIndexed:258", e) }

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

    fun canonicalToken(raw: String?): String = ActionTokenResolver.canonicalToken(raw)

    fun getDefaultActionForGestureKey(key: String): String = ActionTokenResolver.getDefaultActionForGestureKey(key)
}

fun resolveDynamicTokenLabel(context: Context, token: String): String =
    ActionTokenResolver.resolveDynamicTokenLabel(context, token)

fun canonicalToken(raw: String?): String = ActionTokenResolver.canonicalToken(raw)
