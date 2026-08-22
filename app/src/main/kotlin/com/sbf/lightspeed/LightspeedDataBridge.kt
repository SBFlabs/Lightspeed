package com.sbf.lightspeed

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.content.pm.ResolveInfo
import android.graphics.drawable.Drawable
import android.os.Build
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors

class LightspeedDataBridge(private val context: Context) {

    data class CategoryNode(val id: String, val label: String)

    data class LaunchTarget(
        val label: String,
        val packageName: String,
        val activityName: String,
        val isWidget: Boolean = false,
        val spanX: Int = 0,
        val spanY: Int = 0,
        val instanceId: Int = 0
    )

    private val categories = listOf(
        CategoryNode("comm", "Communication"),
        CategoryNode("internet", "Internet"),
        CategoryNode("audio", "Audio"),
        CategoryNode("graphics", "Graphics & Video"),
        CategoryNode("games", "Games"),
        CategoryNode("utilities", "Utilities"),
        CategoryNode("settings", "Settings"),
        CategoryNode("other", "Other")
    )

    private val appCache = ConcurrentHashMap<String, List<LaunchTarget>>()
    private val iconCache = ConcurrentHashMap<String, Drawable>()
    private val executor = Executors.newSingleThreadExecutor()

    init {
        // Pre-populate with default categories immediately so cold start has zero delay
        categories.forEach { appCache[it.id] = emptyList() }
        refreshCacheAsync()
        registerPackageReceiver()
    }

    fun getLiveCategories(): List<CategoryNode> {
        val active = categories.filter { (appCache[it.id]?.isNotEmpty() == true) }
        return if (active.isNotEmpty()) active else categories
    }

    fun getAppsForCategory(categoryId: String): List<LaunchTarget> {
        return appCache[categoryId] ?: emptyList()
    }

    fun getIcon(packageName: String): Drawable? {
        val cached = iconCache[packageName]
        if (cached != null) return cached
        return try {
            val pm = context.packageManager
            val drawable = pm.getApplicationIcon(packageName)
            iconCache[packageName] = drawable
            drawable
        } catch (_: Exception) { null }
    }

    fun refreshCacheAsync(onComplete: (() -> Unit)? = null) {
        executor.execute {
            val pm = context.packageManager
            val mainIntent = Intent(Intent.ACTION_MAIN, null).apply {
                addCategory(Intent.CATEGORY_LAUNCHER)
            }
            val resolveList: List<ResolveInfo> = pm.queryIntentActivities(mainIntent, 0)
            val newCache = mutableMapOf<String, MutableList<LaunchTarget>>()
            categories.forEach { newCache[it.id] = mutableListOf() }

            for (resolveInfo in resolveList) {
                val appInfo = resolveInfo.activityInfo.applicationInfo
                val pkg = resolveInfo.activityInfo.packageName
                val actv = resolveInfo.activityInfo.name
                val label = resolveInfo.loadLabel(pm)?.toString() ?: pkg

                // Background decode and cache icon
                try {
                    val icon = resolveInfo.loadIcon(pm)
                    if (icon != null) {
                        iconCache[pkg] = icon
                    }
                } catch (e: Exception) {}

                val assignedCat = classifyApp(appInfo, pkg, label)
                val targetList = newCache[assignedCat] ?: newCache.getOrPut("other") { mutableListOf() }
                targetList.add(LaunchTarget(label, pkg, actv))
            }

            newCache.forEach { (cat, list) ->
                appCache[cat] = list.sortedBy { it.label.lowercase() }
            }
            onComplete?.invoke()
        }
    }

    private fun registerPackageReceiver() {
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_PACKAGE_ADDED)
            addAction(Intent.ACTION_PACKAGE_REMOVED)
            addAction(Intent.ACTION_PACKAGE_REPLACED)
            addDataScheme("package")
        }
        context.registerReceiver(object : BroadcastReceiver() {
            override fun onReceive(ctx: Context?, intent: Intent?) {
                refreshCacheAsync()
            }
        }, filter)
    }

    private fun classifyApp(appInfo: ApplicationInfo, pkg: String, label: String): String {
        val p = pkg.lowercase()
        val l = label.lowercase()

        if (p.contains("settings") || p == "com.android.settings" || l.contains("settings")) return "settings"

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            when (appInfo.category) {
                ApplicationInfo.CATEGORY_GAME -> return "games"
                ApplicationInfo.CATEGORY_AUDIO -> return "audio"
                ApplicationInfo.CATEGORY_VIDEO, ApplicationInfo.CATEGORY_IMAGE -> return "graphics"
                ApplicationInfo.CATEGORY_SOCIAL, ApplicationInfo.CATEGORY_NEWS -> return "comm"
                ApplicationInfo.CATEGORY_MAPS -> return "internet"
                ApplicationInfo.CATEGORY_PRODUCTIVITY, ApplicationInfo.CATEGORY_ACCESSIBILITY -> return "utilities"
            }
        }

        return when {
            (appInfo.flags and ApplicationInfo.FLAG_IS_GAME) != 0 || p.contains("game") || l.contains("game") -> "games"
            p.contains("whatsapp") || p.contains("telegram") || p.contains("messaging") ||
            p.contains("dialer") || p.contains("contacts") || p.contains("discord") ||
            p.contains("mail") || p.contains("gmail") || p.contains("messages") ||
            p.contains("signal") || p.contains("sms") || p.contains("chat") -> "comm"
            p.contains("browser") || p.contains("chrome") || p.contains("firefox") ||
            p.contains("cromite") || p.contains("weblibre") || p.contains("opera") ||
            p.contains("brave") || p.contains("duckduckgo") || p.contains("edge") ||
            p.contains("map") || p.contains("navigation") -> "internet"
            p.contains("music") || p.contains("audio") || p.contains("spotify") ||
            p.contains("sound") || p.contains("radio") || p.contains("podcast") ||
            p.contains("mpv") || p.contains("player") || p.contains("recorder") -> "audio"
            p.contains("camera") || p.contains("gallery") || p.contains("photo") ||
            p.contains("video") || p.contains("youtube") || p.contains("vance") ||
            p.contains("editor") || p.contains("canvas") || p.contains("draw") -> "graphics"
            p.contains("calc") || p.contains("termux") || p.contains("file") ||
            p.contains("clock") || p.contains("alarm") || p.contains("calendar") ||
            p.contains("note") || p.contains("authenticator") || p.contains("bitwarden") ||
            p.contains("vault") || p.contains("anki") || p.contains("reader") ||
            p.contains("pdf") || p.contains("tool") -> "utilities"
            else -> "other"
        }
    }
}
