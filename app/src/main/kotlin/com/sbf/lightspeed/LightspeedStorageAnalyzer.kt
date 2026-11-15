package com.sbf.lightspeed

import android.app.usage.StorageStatsManager
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.os.Process
import android.os.SystemClock
import android.os.UserHandle
import android.os.storage.StorageManager
import com.sbf.lightspeed.system.LightspeedUsageStats
import java.io.File
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong

internal data class AppStorageEntry(
    val packageName: String,
    val label: String,
    val totalBytes: Long,
    val appBytes: Long,
    val userDataBytes: Long,
    val cacheBytes: Long,
    val firstInstallTime: Long,
    val lastUpdateTime: Long,
    val isOnExternalStorage: Boolean,
    val isStatsResolved: Boolean = true,
    val isSystemApp: Boolean = false
)

internal data class AppStorageQueryResult(
    val entries: List<AppStorageEntry>,
    val hasUsageAccess: Boolean,
    val hasExternalApps: Boolean,
    val resolvedPackages: Set<String> = emptySet(),
    val includeSystemApps: Boolean = false
)

internal data class StorageJumpMarker(
    val label: String,
    val rowIndex: Int,
    var yCenter: Float = 0f
)

internal object LightspeedStorageAnalyzer {
    private const val STORAGE_CACHE_TTL_MS = 180_000L
    private const val GB = 1024L * 1024L * 1024L
    private const val MB = 1024L * 1024L
    private const val KB = 1024L

    const val CHANGE_ADDED = "ADD"
    const val CHANGE_UPDATED = "UPD"
    const val CHANGE_REMOVED = "REM"

    data class PendingPackageChanges(
        val count: Int,
        val addedCount: Int,
        val updatedCount: Int,
        val removedCount: Int,
        val deltaSyncedCount: Int = 0
    )

    @Volatile
    private var cachedResult: AppStorageQueryResult? = null

    @Volatile
    private var cachedTimestampMs: Long = 0L

    @Volatile
    private var lastInspectedPackage: String? = null

    private val cacheLock = Any()
    private val deltaExecutor = Executors.newSingleThreadExecutor()
    private val pendingPackageChanges = java.util.concurrent.ConcurrentHashMap<String, String>()
    private val deltaSyncedPackages = java.util.Collections.synchronizedSet(hashSetOf<String>())

    fun onPackageChanged(packageName: String?, changeKind: String) {
        if (packageName.isNullOrBlank()) return
        synchronized(cacheLock) {
            val cached = cachedResult ?: return
            pendingPackageChanges[packageName] = changeKind
            if (changeKind == CHANGE_REMOVED) {
                deltaSyncedPackages.remove(packageName)
                val pruned = cached.entries.filterNot { it.packageName == packageName }
                if (pruned.size != cached.entries.size) {
                    cachedResult = cached.copy(
                        entries = pruned,
                        hasExternalApps = pruned.any { it.isOnExternalStorage }
                    )
                }
            }
        }
    }

    fun markPackageInspected(packageName: String) {
        if (packageName.isNotBlank()) {
            lastInspectedPackage = packageName
        }
    }

    fun consumeLastInspectedPackage(): String? {
        val pkg = lastInspectedPackage
        lastInspectedPackage = null
        return pkg
    }

    fun consumeDeltaSyncedPackages(): Set<String> {
        synchronized(deltaSyncedPackages) {
            if (deltaSyncedPackages.isEmpty()) return emptySet()
            val copy = deltaSyncedPackages.toSet()
            deltaSyncedPackages.clear()
            return copy
        }
    }

    fun getCacheAgeLabel(nowMs: Long = System.currentTimeMillis()): String? {
        if (cachedResult == null || cachedTimestampMs <= 0L) return null
        val mins = ((nowMs - cachedTimestampMs).coerceAtLeast(0L) / 60_000L).toInt()
        return if (mins == 0) "JUST NOW" else "${mins}m AGO"
    }

    fun refreshPackageInCacheAsync(
        context: Context,
        packageName: String,
        onUpdated: ((AppStorageQueryResult) -> Unit)? = null
    ) {
        if (packageName.isBlank() || cachedResult == null) return
        val appCtx = context.applicationContext ?: context
        deltaExecutor.execute {
            try {
                if (cachedResult == null) return@execute
                val pm = appCtx.packageManager
                val pkgInfo = try {
                    @Suppress("DEPRECATION")
                    pm.getPackageInfo(packageName, 0)
                } catch (_: Exception) {
                    null
                }
                if (pkgInfo == null) {
                    val pruned = synchronized(cacheLock) {
                        val current = cachedResult ?: return@execute
                        val nextEntries = current.entries.filterNot { it.packageName == packageName }
                        val nextResult = current.copy(
                            entries = nextEntries,
                            hasExternalApps = nextEntries.any { it.isOnExternalStorage }
                        )
                        cachedResult = nextResult
                        nextResult
                    }
                    onUpdated?.invoke(pruned)
                    return@execute
                }

                val hasUsage = LightspeedUsageStats.hasUsageAccess(appCtx)
                val ssm = if (hasUsage) {
                    appCtx.getSystemService(Context.STORAGE_STATS_SERVICE) as? StorageStatsManager
                } else {
                    null
                }
                val isLaunchable = try {
                    pm.getLaunchIntentForPackage(packageName) != null
                } catch (_: Exception) {
                    false
                }
                val candidate = buildCandidate(
                    pm = pm,
                    pkgInfo = pkgInfo,
                    isLaunchable = isLaunchable,
                    knownLabels = emptyMap(),
                    allowSystemFallback = true,
                    includeSystemApps = true
                ) ?: return@execute
                val (totalBytes, appBytes, userDataBytes, cacheBytes, resolved) = queryPackageStats(
                    ssm = ssm,
                    appInfo = candidate.appInfo,
                    packageName = candidate.packageName,
                    apkBytes = candidate.apkBytes,
                    userHandle = Process.myUserHandle()
                )
                val newEntry = AppStorageEntry(
                    packageName = candidate.packageName,
                    label = candidate.label,
                    totalBytes = totalBytes,
                    appBytes = appBytes,
                    userDataBytes = userDataBytes,
                    cacheBytes = cacheBytes,
                    firstInstallTime = candidate.firstInstallTime,
                    lastUpdateTime = candidate.lastUpdateTime,
                    isOnExternalStorage = candidate.isExternal,
                    isStatsResolved = resolved || ssm == null,
                    isSystemApp = candidate.isSystemApp
                )
                val updated = synchronized(cacheLock) {
                    val current = cachedResult ?: return@execute
                    val mutable = current.entries.toMutableList()
                    val existingIdx = mutable.indexOfFirst { it.packageName == packageName }
                    if (existingIdx >= 0) {
                        mutable[existingIdx] = newEntry
                    } else if (current.includeSystemApps || !candidate.isPureSystem) {
                        mutable.add(newEntry)
                    } else {
                        return@execute
                    }
                    val nextResult = current.copy(
                        entries = mutable,
                        hasUsageAccess = hasUsage,
                        hasExternalApps = mutable.any { it.isOnExternalStorage }
                    )
                    cachedResult = nextResult
                    nextResult
                }
                deltaSyncedPackages.add(packageName)
                onUpdated?.invoke(updated)
            } catch (_: Exception) {}
        }
    }

    fun getPendingPackageChangesSummary(): PendingPackageChanges {
        if (pendingPackageChanges.isEmpty()) {
            return PendingPackageChanges(0, 0, 0, 0, 0)
        }
        var added = 0
        var updated = 0
        var removed = 0
        var synced = 0
        for ((pkg, kind) in pendingPackageChanges.entries) {
            when (kind) {
                CHANGE_ADDED -> {
                    added++
                    if (pkg in deltaSyncedPackages) synced++
                }
                CHANGE_UPDATED -> {
                    updated++
                    if (pkg in deltaSyncedPackages) synced++
                }
                CHANGE_REMOVED -> removed++
            }
        }
        return PendingPackageChanges(
            count = pendingPackageChanges.size,
            addedCount = added,
            updatedCount = updated,
            removedCount = removed,
            deltaSyncedCount = synced
        )
    }

    fun getCachedIfFresh(hasUsageNow: Boolean, includeSystemApps: Boolean): AppStorageQueryResult? {
        val cached = cachedResult ?: return null
        if (cached.hasUsageAccess != hasUsageNow || cached.includeSystemApps != includeSystemApps) return null
        val age = System.currentTimeMillis() - cachedTimestampMs
        return if (age in 0L..STORAGE_CACHE_TTL_MS && cached.entries.isNotEmpty()) cached else null
    }

    fun clearCache() {
        synchronized(cacheLock) {
            cachedResult = null
            cachedTimestampMs = 0L
            pendingPackageChanges.clear()
            deltaSyncedPackages.clear()
        }
    }

    val VOLUME_OPTIONS = listOf(
        "ALL" to "ALL",
        "INT" to "INT",
        "SD" to "SD"
    )

    val SORT_OPTIONS = listOf(
        "total" to "TOTAL",
        "app" to "APP",
        "data" to "DATA",
        "cache" to "CACHE",
        "updated" to "UPD",
        "installed" to "INST"
    )

    private data class SizeTier(val label: String, val minBytes: Long, val maxExclusiveBytes: Long)

    private val SIZE_TIERS_DESC = listOf(
        SizeTier("10G+", 10L * GB, Long.MAX_VALUE),
        SizeTier("5G+", 5L * GB, 10L * GB),
        SizeTier("4G", 4L * GB, 5L * GB),
        SizeTier("3G", 3L * GB, 4L * GB),
        SizeTier("2G", 2L * GB, 3L * GB),
        SizeTier("1G", 1L * GB, 2L * GB),
        SizeTier("500M", 500L * MB, 1L * GB),
        SizeTier("100M", 100L * MB, 500L * MB),
        SizeTier("10M", 10L * MB, 100L * MB),
        SizeTier("1M", 1L * MB, 10L * MB),
        SizeTier("KB", 1L * KB, 1L * MB),
        SizeTier("0", 0L, 1L * KB)
    )

    private data class StorageCandidate(
        val packageName: String,
        val appInfo: ApplicationInfo,
        val label: String,
        val apkBytes: Long,
        val firstInstallTime: Long,
        val lastUpdateTime: Long,
        val isExternal: Boolean,
        val isSystemApp: Boolean,
        val isPureSystem: Boolean,
        val priorityTier: Int
    )

    private data class PackageSizeMetrics(
        val totalBytes: Long,
        val appBytes: Long,
        val userDataBytes: Long,
        val cacheBytes: Long,
        val resolved: Boolean
    )

    private fun buildCandidate(
        pm: PackageManager,
        pkgInfo: PackageInfo,
        isLaunchable: Boolean,
        knownLabels: Map<String, String>,
        allowSystemFallback: Boolean = false,
        includeSystemApps: Boolean = false
    ): StorageCandidate? {
        val appInfo = pkgInfo.applicationInfo ?: return null
        val pkg = pkgInfo.packageName ?: return null
        if (pkg == "android" || appInfo.sourceDir.isNullOrBlank()) return null
        val isSystem = (appInfo.flags and ApplicationInfo.FLAG_SYSTEM) != 0
        val isUpdatedSystem = (appInfo.flags and ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) != 0
        val isPureSystem = isSystem && !isUpdatedSystem && !isLaunchable
        if (!allowSystemFallback && !includeSystemApps && isPureSystem) return null

        val label = knownLabels[pkg]?.takeIf { it.isNotBlank() }
            ?: appInfo.nonLocalizedLabel?.toString()?.takeIf { it.isNotBlank() }
            ?: try {
                pm.getApplicationLabel(appInfo)?.toString()?.takeIf { it.isNotBlank() }
                    ?: pkg.substringAfterLast('.')
            } catch (_: Exception) {
                pkg.substringAfterLast('.')
            }

        val isExternal = (appInfo.flags and ApplicationInfo.FLAG_EXTERNAL_STORAGE) != 0 ||
            (appInfo.storageUuid != null && appInfo.storageUuid != StorageManager.UUID_DEFAULT)

        var apkSum = try { File(appInfo.sourceDir).length() } catch (_: Exception) { 0L }
        appInfo.splitPublicSourceDirs?.forEach { splitPath ->
            apkSum += try { File(splitPath).length() } catch (_: Exception) { 0L }
        }
        val apkBytes = apkSum.coerceAtLeast(0L)
        val tier = when {
            !isSystem && isLaunchable -> 0
            isLaunchable -> 1
            !isSystem -> 2
            else -> 3
        }
        return StorageCandidate(
            packageName = pkg,
            appInfo = appInfo,
            label = label,
            apkBytes = apkBytes,
            firstInstallTime = pkgInfo.firstInstallTime.coerceAtLeast(0L),
            lastUpdateTime = pkgInfo.lastUpdateTime.coerceAtLeast(pkgInfo.firstInstallTime),
            isExternal = isExternal,
            isSystemApp = isSystem && !isLaunchable,
            isPureSystem = isPureSystem,
            priorityTier = tier
        )
    }

    private fun queryPackageStats(
        ssm: StorageStatsManager?,
        appInfo: ApplicationInfo,
        packageName: String,
        apkBytes: Long,
        userHandle: UserHandle
    ): PackageSizeMetrics {
        if (ssm == null) {
            return PackageSizeMetrics(apkBytes, apkBytes, -1L, -1L, false)
        }
        return try {
            val uuid = appInfo.storageUuid ?: StorageManager.UUID_DEFAULT
            val stats = ssm.queryStatsForPackage(uuid, packageName, userHandle)
            val cache = stats.cacheBytes.coerceAtLeast(0L)
            val userData = (stats.dataBytes - cache).coerceAtLeast(0L)
            val code = stats.appBytes.coerceAtLeast(0L)
            val total = (code + stats.dataBytes).coerceAtLeast(code + userData + cache)
            PackageSizeMetrics(total, code, userData, cache, true)
        } catch (_: Exception) {
            PackageSizeMetrics(apkBytes, apkBytes, -1L, -1L, true)
        }
    }

    fun queryInstalledAppStorageProgressive(
        context: Context,
        knownLabels: Map<String, String> = emptyMap(),
        includeSystemApps: Boolean = false,
        isCancelled: () -> Boolean,
        onBatchUpdate: (result: AppStorageQueryResult, doneCount: Int, totalCount: Int, isFinished: Boolean) -> Unit
    ) {
        val hasUsage = LightspeedUsageStats.hasUsageAccess(context)
        val ssm = if (hasUsage) {
            context.getSystemService(Context.STORAGE_STATS_SERVICE) as? StorageStatsManager
        } else {
            null
        }
        val pm = context.packageManager
        val userHandle = Process.myUserHandle()

        val launchablePkgs = hashSetOf<String>()
        try {
            val mainIntent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
            pm.queryIntentActivities(mainIntent, 0).forEach { ri ->
                ri.activityInfo?.packageName?.let { launchablePkgs.add(it) }
            }
        } catch (_: Exception) {}

        val packages = try {
            pm.getInstalledPackages(0)
        } catch (_: Exception) {
            emptyList()
        }
        if (isCancelled()) return

        val candidates = ArrayList<StorageCandidate>(packages.size)
        var anyExternal = false
        for (pkgInfo in packages) {
            if (isCancelled()) return
            val pkg = pkgInfo.packageName ?: continue
            val candidate = buildCandidate(pm, pkgInfo, pkg in launchablePkgs, knownLabels, includeSystemApps) ?: continue
            if (candidate.isExternal) anyExternal = true
            candidates.add(candidate)
        }
        if (isCancelled()) return

        candidates.sortWith(compareBy<StorageCandidate> { it.priorityTier }.thenByDescending { it.apkBytes })

        val totalCount = candidates.size
        val resolvedImmediately = (ssm == null || totalCount == 0)
        val entries = Array(totalCount) { idx ->
            val c = candidates[idx]
            AppStorageEntry(
                packageName = c.packageName,
                label = c.label,
                totalBytes = c.apkBytes,
                appBytes = c.apkBytes,
                userDataBytes = -1L,
                cacheBytes = -1L,
                firstInstallTime = c.firstInstallTime,
                lastUpdateTime = c.lastUpdateTime,
                isOnExternalStorage = c.isExternal,
                isStatsResolved = resolvedImmediately,
                isSystemApp = c.isSystemApp
            )
        }

        if (resolvedImmediately) {
            val result = AppStorageQueryResult(
                entries = entries.toList(),
                hasUsageAccess = hasUsage,
                hasExternalApps = anyExternal,
                includeSystemApps = includeSystemApps
            )
            synchronized(cacheLock) {
                cachedResult = result
                cachedTimestampMs = System.currentTimeMillis()
                pendingPackageChanges.clear()
                deltaSyncedPackages.clear()
            }
            onBatchUpdate(result, totalCount, totalCount, true)
            return
        }

        // Stage 1: emit immediate APK baselines, names, and dates.
        onBatchUpdate(
            AppStorageQueryResult(
                entries = entries.toList(),
                hasUsageAccess = hasUsage,
                hasExternalApps = anyExternal,
                includeSystemApps = includeSystemApps
            ),
            0,
            totalCount,
            false
        )

        // Stage 2 (parallel): stream StorageStatsManager sizes in small periodic batches.
        val nextIndex = AtomicInteger(0)
        val completed = AtomicInteger(0)
        val lastEmitMs = AtomicLong(SystemClock.uptimeMillis())
        val emitLock = Any()
        val entriesLock = Any()
        val batchFreshPkgs = java.util.Collections.synchronizedSet(hashSetOf<String>())
        val workerCount = 4.coerceAtMost(totalCount)
        val latch = CountDownLatch(workerCount)
        val pool = Executors.newFixedThreadPool(workerCount)

        try {
            repeat(workerCount) {
                pool.execute {
                    try {
                        while (!isCancelled()) {
                            val idx = nextIndex.getAndIncrement()
                            if (idx >= totalCount) break
                            val c = candidates[idx]
                            val (totalBytes, appBytes, userDataBytes, cacheBytes, _) = queryPackageStats(
                                ssm = ssm,
                                appInfo = c.appInfo,
                                packageName = c.packageName,
                                apkBytes = c.apkBytes,
                                userHandle = userHandle
                            )

                            synchronized(entriesLock) {
                                entries[idx] = entries[idx].copy(
                                    totalBytes = totalBytes,
                                    appBytes = appBytes,
                                    userDataBytes = userDataBytes,
                                    cacheBytes = cacheBytes,
                                    isStatsResolved = true
                                )
                            }
                            batchFreshPkgs.add(c.packageName)

                            val done = completed.incrementAndGet()
                            val now = SystemClock.uptimeMillis()
                            if (!isCancelled() && done < totalCount && (now - lastEmitMs.get() >= 240L || done % 12 == 0)) {
                                synchronized(emitLock) {
                                    val innerNow = SystemClock.uptimeMillis()
                                    if (!isCancelled() && (innerNow - lastEmitMs.get() >= 200L || done % 12 == 0)) {
                                        lastEmitMs.set(innerNow)
                                        val resolvedSnapshot = synchronized(batchFreshPkgs) {
                                            val copy = batchFreshPkgs.toSet()
                                            batchFreshPkgs.clear()
                                            copy
                                        }
                                        val entriesSnapshot = synchronized(entriesLock) { entries.toList() }
                                        onBatchUpdate(
                                            AppStorageQueryResult(
                                                entries = entriesSnapshot,
                                                hasUsageAccess = hasUsage,
                                                hasExternalApps = anyExternal,
                                                resolvedPackages = resolvedSnapshot,
                                                includeSystemApps = includeSystemApps
                                            ),
                                            done,
                                            totalCount,
                                            false
                                        )
                                    }
                                }
                            }
                        }
                    } catch (_: Exception) {
                        // Guard worker pool against transient PackageManager/StorageStats runtime failures
                    } finally {
                        latch.countDown()
                    }
                }
            }
            latch.await()
        } catch (_: InterruptedException) {
            Thread.currentThread().interrupt()
        } finally {
            pool.shutdownNow()
        }

        if (!isCancelled()) {
            val resolvedTail = synchronized(emitLock) {
                synchronized(batchFreshPkgs) {
                    val copy = batchFreshPkgs.toSet()
                    batchFreshPkgs.clear()
                    copy
                }
            }
            val finalEntries = synchronized(entriesLock) { entries.toList() }
            val finalResult = AppStorageQueryResult(
                entries = finalEntries,
                hasUsageAccess = hasUsage,
                hasExternalApps = anyExternal,
                resolvedPackages = resolvedTail,
                includeSystemApps = includeSystemApps
            )
            synchronized(cacheLock) {
                cachedResult = finalResult.copy(resolvedPackages = emptySet())
                cachedTimestampMs = System.currentTimeMillis()
                pendingPackageChanges.clear()
                deltaSyncedPackages.clear()
            }
            onBatchUpdate(finalResult, totalCount, totalCount, true)
        }
    }

    fun filterAndSort(
        entries: List<AppStorageEntry>,
        volumeFilter: String,
        sortKey: String,
        reversed: Boolean
    ): List<AppStorageEntry> {
        val filtered = when (volumeFilter) {
            "INT" -> entries.filter { !it.isOnExternalStorage }
            "SD" -> entries.filter { it.isOnExternalStorage }
            else -> entries
        }
        val unresolvedBottomSort = (sortKey == "data" || sortKey == "cache")
        val finalComparator = Comparator<AppStorageEntry> { a, b ->
            if (unresolvedBottomSort && a.isStatsResolved != b.isStatsResolved) {
                return@Comparator if (a.isStatsResolved) -1 else 1
            }
            val cmp = if (reversed) {
                when (sortKey) {
                    "app" -> a.appBytes.compareTo(b.appBytes)
                    "data" -> a.userDataBytes.compareTo(b.userDataBytes)
                    "cache" -> a.cacheBytes.compareTo(b.cacheBytes)
                    "updated" -> a.lastUpdateTime.compareTo(b.lastUpdateTime)
                    "installed" -> a.firstInstallTime.compareTo(b.firstInstallTime)
                    else -> a.totalBytes.compareTo(b.totalBytes)
                }
            } else {
                when (sortKey) {
                    "app" -> b.appBytes.compareTo(a.appBytes)
                    "data" -> b.userDataBytes.compareTo(a.userDataBytes)
                    "cache" -> b.cacheBytes.compareTo(a.cacheBytes)
                    "updated" -> b.lastUpdateTime.compareTo(a.lastUpdateTime)
                    "installed" -> b.firstInstallTime.compareTo(a.firstInstallTime)
                    else -> b.totalBytes.compareTo(a.totalBytes)
                }
            }
            if (cmp != 0) cmp else a.label.compareTo(b.label, ignoreCase = true)
        }
        return filtered.sortedWith(finalComparator)
    }

    fun buildJumpMarkers(
        entries: List<AppStorageEntry>,
        sortKey: String,
        reversed: Boolean
    ): List<StorageJumpMarker> {
        if (entries.isEmpty()) return emptyList()
        if (sortKey == "updated" || sortKey == "installed") {
            val cal = Calendar.getInstance()
            val out = mutableListOf<StorageJumpMarker>()
            var lastYear = -1
            for ((idx, e) in entries.withIndex()) {
                val ts = if (sortKey == "installed") e.firstInstallTime else e.lastUpdateTime
                if (ts <= 0L) continue
                cal.timeInMillis = ts
                val yr = cal.get(Calendar.YEAR) % 100
                if (yr != lastYear) {
                    lastYear = yr
                    out.add(StorageJumpMarker(label = String.format(Locale.US, "'%02d", yr), rowIndex = idx))
                }
            }
            return out
        }

        val tiers = if (reversed) SIZE_TIERS_DESC.asReversed() else SIZE_TIERS_DESC
        val out = mutableListOf<StorageJumpMarker>()
        for (tier in tiers) {
            val idx = entries.indexOfFirst { e ->
                val v = metricBytesForSort(e, sortKey)
                v >= tier.minBytes && v < tier.maxExclusiveBytes
            }
            if (idx >= 0 && out.none { it.rowIndex == idx }) {
                out.add(StorageJumpMarker(label = tier.label, rowIndex = idx))
            }
        }
        return out
    }

    fun metricBytesForSort(entry: AppStorageEntry, sortKey: String): Long = when (sortKey) {
        "app" -> entry.appBytes.coerceAtLeast(0L)
        "data" -> if (entry.isStatsResolved) entry.userDataBytes.coerceAtLeast(0L) else 0L
        "cache" -> if (entry.isStatsResolved) entry.cacheBytes.coerceAtLeast(0L) else 0L
        else -> entry.totalBytes.coerceAtLeast(0L)
    }

    fun formatBytesCompact(bytes: Long): String {
        if (bytes < 0L) return "--"
        if (bytes < 1024L) return "${bytes}B"
        val kb = bytes / 1024.0
        if (kb < 1024.0) return String.format(Locale.US, "%.0fK", kb)
        val mb = kb / 1024.0
        if (mb < 1024.0) {
            return if (mb >= 100.0) String.format(Locale.US, "%.0fM", mb)
            else String.format(Locale.US, "%.1fM", mb)
        }
        val gb = mb / 1024.0
        return String.format(Locale.US, "%.2fG", gb)
    }

    fun formatDateShort(ms: Long): String {
        if (ms <= 0L) return "--"
        return try {
            SimpleDateFormat("dd MMM yy", Locale.US).format(Date(ms))
        } catch (_: Exception) {
            "--"
        }
    }
}
