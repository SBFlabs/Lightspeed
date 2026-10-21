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
import androidx.core.content.ContextCompat
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

    companion object {
        val DRAWER_CATEGORIES = listOf(
            CategoryNode("comm", "Communication"),
            CategoryNode("internet", "Internet"),
            CategoryNode("audio", "Audio"),
            CategoryNode("graphics", "Graphics & Video"),
            CategoryNode("games", "Games"),
            CategoryNode("finance", "Finance"),
            CategoryNode("learning", "Learning & Books"),
            CategoryNode("utilities", "Utilities"),
            CategoryNode("security", "Security & Privacy"),
            CategoryNode("productivity", "Productivity"),
            CategoryNode("stores", "App Stores"),
            CategoryNode("health", "Health & Fitness"),
            CategoryNode("settings", "Systems & Styles"),
            CategoryNode("other", "Other")
        )
    }

    private val categories = DRAWER_CATEGORIES

    private val appCache = ConcurrentHashMap<String, List<LaunchTarget>>()
    
    private val executor = Executors.newSingleThreadExecutor()

    @Volatile
    private var packageReceiver: BroadcastReceiver? = null

    init {
        // Pre-populate with default categories immediately so cold start has zero delay
        categories.forEach { appCache[it.id] = emptyList() }
        refreshCacheAsync()
        registerPackageReceiver()
    }

    fun getOrderedCategories(): List<CategoryNode> {
        val order = com.sbf.lightspeed.system.LightspeedPreferences.getCategoryOrder(context)
        val defaultMap = DRAWER_CATEGORIES.associateBy { it.id }
        return order.mapNotNull { defaultMap[it] }
    }

    fun getLiveCategories(): List<CategoryNode> {
        val ordered = getOrderedCategories()
        val active = ordered.filter { (appCache[it.id]?.isNotEmpty() == true) }
        return if (active.isNotEmpty()) active else ordered
    }

    fun getAppsForCategory(categoryId: String): List<LaunchTarget> {
        return appCache[categoryId] ?: emptyList()
    }

    fun getIcon(packageName: String): Drawable? {
        return com.sbf.lightspeed.system.LightspeedIconManager.getIconDrawable(context, packageName)
    }

    fun moveAppCategory(pkg: String, newCat: String) {
        var movedTarget: LaunchTarget? = null
        for ((cat, list) in appCache) {
            val target = list.firstOrNull { it.packageName == pkg }
            if (target != null) {
                movedTarget = target
                appCache[cat] = list.filter { it.packageName != pkg }
                break
            }
        }
        if (movedTarget != null) {
            val currentList = appCache[newCat] ?: emptyList()
            appCache[newCat] = (currentList + movedTarget).sortedBy { it.label.lowercase() }
        }
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

                val overrideCat = com.sbf.lightspeed.system.LightspeedPreferences.getAppCategoryOverride(context, pkg)
                val isValidCategory = overrideCat != null && categories.any { it.id == overrideCat }
                val assignedCat = if (isValidCategory) overrideCat!! else LightspeedAppCategorizer.categorize(appInfo, pkg, label)
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
        if (packageReceiver != null) return
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(ctx: Context?, intent: Intent?) {
                refreshCacheAsync()
            }
        }
        packageReceiver = receiver
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_PACKAGE_ADDED)
            addAction(Intent.ACTION_PACKAGE_REMOVED)
            addAction(Intent.ACTION_PACKAGE_REPLACED)
            addDataScheme("package")
        }
        ContextCompat.registerReceiver(context.applicationContext, receiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED)
    }

}
