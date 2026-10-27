package com.sbf.lightspeed.system

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.LauncherApps
import android.content.pm.ShortcutInfo
import android.os.Build
import android.os.Process
import android.util.Log
import android.widget.Toast

data class DeepShortcutEntry(
    val id: String,
    val packageName: String,
    val shortLabel: String,
    val longLabel: String,
    val rawShortcut: ShortcutInfo? = null
)

/**
 * Deep App Shortcuts, LauncherApps Queries & Tactical Pinning Engine
 */
object LightspeedDeepShortcutHelper {

    private const val TAG = "DeepShortcuts"

    fun queryAppDeepShortcuts(context: Context, packageName: String): List<DeepShortcutEntry> {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N_MR1) return emptyList()
        return try {
            val launcherApps = context.getSystemService(Context.LAUNCHER_APPS_SERVICE) as? LauncherApps ?: return emptyList()
            if (!launcherApps.hasShortcutHostPermission()) {
                Log.d(TAG, "No shortcut host permission; querying via Shizuku dumpsys")
                return queryViaShizuku(packageName)
            }
            val query = LauncherApps.ShortcutQuery().apply {
                setPackage(packageName)
                setQueryFlags(
                    LauncherApps.ShortcutQuery.FLAG_MATCH_DYNAMIC or
                            LauncherApps.ShortcutQuery.FLAG_MATCH_MANIFEST or
                            LauncherApps.ShortcutQuery.FLAG_MATCH_PINNED
                )
            }
            val shortcuts = launcherApps.getShortcuts(query, Process.myUserHandle()) ?: emptyList()
            shortcuts.map { info ->
                DeepShortcutEntry(
                    id = info.id,
                    packageName = info.`package`,
                    shortLabel = info.shortLabel?.toString() ?: info.id,
                    longLabel = info.longLabel?.toString() ?: info.shortLabel?.toString() ?: info.id,
                    rawShortcut = info
                )
            }
        } catch (e: Exception) {
            logSwallowed(TAG, "queryAppDeepShortcuts", e)
            emptyList()
        }
    }

    private fun queryViaShizuku(packageName: String): List<DeepShortcutEntry> {
        if (!ElevatedTaskCloser.isShizukuActive) return emptyList()
        return try {
            val p = ElevatedTaskCloser.execShizuku("dumpsys shortcut --package $packageName") ?: return emptyList()
            val text = p.inputStream.bufferedReader().readText()
            val list = mutableListOf<DeepShortcutEntry>()
            for (line in text.lines()) {
                val trimmed = line.trim()
                if (trimmed.startsWith("Shortcut{") || trimmed.contains("id=")) {
                    val id = trimmed.substringAfter("id=").substringBefore(" ").trim('"', '}')
                    if (id.isNotBlank() && !id.contains("/")) {
                        list.add(DeepShortcutEntry(id, packageName, id, id))
                    }
                }
            }
            list.distinctBy { it.id }
        } catch (e: Exception) {
            logSwallowed(TAG, "queryViaShizuku", e)
            emptyList()
        }
    }

    fun launchDeepShortcut(context: Context, packageName: String, shortcutId: String) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N_MR1) {
            val launcherApps = context.getSystemService(Context.LAUNCHER_APPS_SERVICE) as? LauncherApps
            if (launcherApps?.hasShortcutHostPermission() == true) {
                try {
                    launcherApps.startShortcut(packageName, shortcutId, null, null, Process.myUserHandle())
                    return
                } catch (e: Exception) {
                    logSwallowed(TAG, "startShortcut", e)
                }
            }
        }
        if (ElevatedTaskCloser.isShizukuActive) {
            ElevatedTaskCloser.execShizuku("cmd shortcut start-shortcut --package $packageName --id '$shortcutId'")
        } else {
            Toast.makeText(context, "Shortcut requires default launcher or Shizuku permission", Toast.LENGTH_SHORT).show()
        }
    }

    fun pinToCockpitGear(context: Context, token: String, ringIndex: Int = 0) {
        try {
            val prefs = context.defaultPrefs()
            val isLeft = false
            val setsList = CockpitGearRepository.getGearSetsOrder(context, isLeft)
            val setId = setsList.firstOrNull() ?: "0"
            val key = "gear_set_${setId}_ring_${ringIndex}_packages"
            val current = prefs.getString(key, null) ?: ""
            val updated = if (current.isEmpty()) token else "$current,$token"
            prefs.edit().putString(key, updated).apply()
            CockpitGearRepository.clearCache()
            Toast.makeText(context, "Pinned to Cockpit Gear", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Toast.makeText(context, "Pinning to Gear failed: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    fun pinToSpacePod(context: Context, podIndex: Int, actionToken: String) {
        val prefs = context.defaultPrefs()
        prefs.edit().putString(SpacePodGestureDispatcher.getActionPrefKey(podIndex, SpacePodGestureDispatcher.GESTURE_CLICK), actionToken).apply()
        Toast.makeText(context, "Assigned to Space Pod $podIndex Tap", Toast.LENGTH_SHORT).show()
    }

    fun pinToHomeScreen(context: Context, info: ShortcutInfo?) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && info != null) {
            val shortcutManager = context.getSystemService(android.content.pm.ShortcutManager::class.java)
            if (shortcutManager?.isRequestPinShortcutSupported == true) {
                shortcutManager.requestPinShortcut(info, null)
                Toast.makeText(context, "Requested Home Screen Pin", Toast.LENGTH_SHORT).show()
                return
            }
        }
        Toast.makeText(context, "Home screen pinning not supported by current launcher", Toast.LENGTH_SHORT).show()
    }
}
