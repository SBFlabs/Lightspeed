package com.sbf.lightspeed.settings

import com.sbf.lightspeed.system.defaultPrefs
import android.content.Context
import android.content.Intent
import android.content.pm.LauncherApps
import android.content.pm.PackageManager
import android.os.Build
import android.os.Process
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
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

    fun getIconBitmap(context: Context, pkg: String): android.graphics.Bitmap? {
        return LightspeedIconManager.getIconBitmap(context, pkg)
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
        "system:tactical_flyout",
        "system:lens",
        "system:qr_scanner",
        "system:camera_photo",
        "system:camera_video",
        "system:chatgpt",
        "system:claude",
        "system:gemini",
        "system:folax",
        "system:core_cooling",
        "system:glow_deflectors"
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

                        val shortcutBlocks = output.split("ShortcutInfo {").drop(1)
                        val idRegex = Regex("""id=([^\r\n, ]+)""")
                        val pkgRegex = Regex("""packageName=([^\r\n, ]+)""")
                        val labelRegex = Regex("""shortLabel=([^,\r\n]+)""")

                        for (block in shortcutBlocks) {
                            val pkgMatch = pkgRegex.find(block)?.groupValues?.getOrNull(1)?.trim()
                            val idMatch = idRegex.find(block)?.groupValues?.getOrNull(1)?.trim()
                            val labelMatch = labelRegex.find(block)?.groupValues?.getOrNull(1)?.trim()

                            if (!pkgMatch.isNullOrBlank() && !idMatch.isNullOrBlank()) {
                                val cleanLabel = (labelMatch ?: idMatch).trim().removeSurrounding("\"")
                                shizukuShortcutsMap.getOrPut(pkgMatch) { mutableListOf() }
                                    .add(Pair(idMatch, cleanLabel))
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
                    getIconBitmap(context, pkg)

                    // 1. Exported Activity Deep Links
                    try {
                        val pkgInfo = pm.getPackageInfo(pkg, PackageManager.GET_ACTIVITIES)
                        pkgInfo.activities?.forEach { activityInfo ->
                            if (activityInfo.exported && activityInfo.name != resolveInfo.activityInfo.name) {
                                val actLabel = activityInfo.loadLabel(pm).toString().takeIf { it.isNotBlank() } ?: activityInfo.name.substringAfterLast(".")
                                val displayLabel = if (actLabel == appLabel) "$actLabel (${activityInfo.name.substringAfterLast(".")})" else actLabel
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
                        var foundShortcuts = false
                        try {
                            val launcherApps = context.getSystemService(Context.LAUNCHER_APPS_SERVICE) as LauncherApps
                            val query = LauncherApps.ShortcutQuery().apply {
                                setPackage(pkg)
                                setQueryFlags(LauncherApps.ShortcutQuery.FLAG_MATCH_DYNAMIC or LauncherApps.ShortcutQuery.FLAG_MATCH_PINNED or LauncherApps.ShortcutQuery.FLAG_MATCH_MANIFEST)
                            }
                            launcherApps.getShortcuts(query, Process.myUserHandle())?.forEach { shortcut ->
                                val label = shortcut.shortLabel?.toString() ?: "Shortcut"
                                val shortcutToken = "shortcut:label=" + label + ";pkg=" + pkg + ";type=home_shortcut;id=" + shortcut.id
                                baseTokens.add(shortcutToken)
                                temporaryLabels[shortcutToken] = label
                                foundShortcuts = true
                            }
                        } catch (_: Exception) {}

                        // If not the default launcher, use pre-parsed Shizuku shortcuts for THIS specific package
                        if (!foundShortcuts) {
                            shizukuShortcutsMap[pkg]?.forEach { (shortcutId, label) ->
                                val shortcutToken = "shortcut:label=$label;pkg=$pkg;type=home_shortcut;id=$shortcutId"
                                baseTokens.add(shortcutToken)
                                temporaryLabels[shortcutToken] = label
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }

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
}

/**
 * Resolves human-readable labels for system and app tokens.
 */
fun resolveDynamicTokenLabel(context: Context, token: String): String {
    return when {
        token == "none" -> "None"
        token == "action_enter_gearset_nav" || token == "system:gearset_nav" -> "Gear Set HUD Navigation Mode"
        token == "system:previous_app" -> "Switch to Previous App"
        token == "system:close_app" || token == "shizuku:close_app" -> "Close App (Remove from Recents)"
        token == "system:recents" -> "Recents Overview"
        token == "system:home" -> "Home"
        token == "system:back" -> "Back"
        token == "system:split_screen" -> "Split Screen"
        token == "system:popup_window" || token == "system:freeform" -> "Pop-up Window"

        token == "system:flashlight" -> "Flashlight / Torch"
        token == "system:screenshot" -> "Take Screenshot"
        token == "system:lock_screen" -> "Lock Screen / Sleep"
        token == "system:notifications" -> "Notification Shade"
        token == "system:quick_settings" -> "Quick Settings"
        token == "system:scroll_to_top" -> "Scroll to Top"
        token == "system:auto_rotate_toggle" || token == "system:toggle_auto_rotate" || token == "system:gravity_toggle_master" || token == "ACTION_GRAVITY_TOGGLE_MASTER" || token == "system:orientation_toggle" || token == "ACTION_TOGGLE_ROTATION" -> "Toggle Native Auto-Rotate"
        token == "system:gravity_reset" || token == "ACTION_GRAVITY_RESET" -> "Restore Default Gravity"
        token == "system:gravity_override_360" || token == "ACTION_GRAVITY_OVERRIDE_360" || token == "system:orientation_sensor_360" -> "Force Transient 360° Gyro"
        token == "system:gravity_override_landscape" || token == "ACTION_GRAVITY_OVERRIDE_LANDSCAPE" -> "Force Transient Landscape"
        token == "system:gravity_override_portrait" || token == "ACTION_GRAVITY_OVERRIDE_PORTRAIT" || token == "system:orientation_portrait" || token == "system:orientation_sensor_portrait" -> "Force Transient Portrait"

        token == "system:screen_timeout" -> "Screen Timeout (Ship Goes Dark)"
        token == "system:volume" -> "Volume (Media Scrubber)"
        token == "system:brightness" -> "Brightness Scrubber"

        token == "system:media_play_pause" -> "Media: Play / Pause"
        token == "system:media_next" -> "Media: Next Track"
        token == "system:media_prev" -> "Media: Previous Track"
        token == "system:media_skip_forward" -> {
            val skipSec = context.defaultPrefs().getInt("pref_media_skip_seconds", 10)
            "Media: Skip Forward (${skipSec}s)"
        }
        token == "system:media_skip_backward" -> {
            val skipSec = context.defaultPrefs().getInt("pref_media_skip_seconds", 10)
            "Media: Skip Backward (${skipSec}s)"
        }
        token == "system:media_scrubber" -> "Media: Timeline Scrubber (HUD)"
        token == "system:media_stop" -> "Media: Stop Playback"
        token == "system:refueling_bay" -> "Refueling Bay (Cryo Charging Dashboard)"
        token == "system:tactical_flyout" || token == "action_quick_flyout" || token == "ACTION_TACTICAL_FLYOUT" -> "Tactical Quick Action Flyout"
        token == "system:lens" || token == "ACTION_LENS" -> "Google Lens (Visual Search)"
        token == "system:qr_scanner" || token == "ACTION_QR_SCANNER" -> "QR Scanner (Optical Scan)"
        token == "system:camera_photo" || token == "ACTION_CAMERA_PHOTO" || token == "camera_photo" -> "Camera (Photo Mode)"
        token == "system:camera_video" || token == "ACTION_CAMERA_VIDEO" || token == "camera_video" -> "Camera (Video Mode)"
        token == "system:chatgpt" || token == "ACTION_CHATGPT" -> "ChatGPT (AI Assistant)"
        token == "system:claude" || token == "ACTION_CLAUDE" -> "Claude (AI Assistant)"
        token == "system:gemini" || token == "ACTION_GEMINI" -> "Gemini (Google AI)"
        token == "system:folax" || token == "ACTION_FOLAX" -> "Folax (Transsion AI)"
        token == "system:core_cooling" || token == "ACTION_CORE_COOLING" -> "Core Cooling (Reboot System)"
        token == "system:glow_deflectors" || token == "ACTION_GLOW_DEFLECTORS" || token == "glow_deflectors" -> "Glow Deflector Wings (Tactical Shields)"
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
            val raw = token.substringAfter("label=").substringBefore(";")
            try { android.net.Uri.decode(raw) } catch (_: Exception) { raw }
        }
        else -> token
    }
}
