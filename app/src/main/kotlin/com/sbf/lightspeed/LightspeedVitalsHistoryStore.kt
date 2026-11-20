package com.sbf.lightspeed

import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import android.widget.Toast
import com.sbf.lightspeed.system.ElevatedTaskCloser
import com.sbf.lightspeed.system.ShellArgGuard
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader
import java.util.concurrent.CopyOnWriteArrayList

internal data class VitalsSample(
    val timestampMs: Long,
    val tempC: Int,
    val ramUsedMb: Int,
    val swapUsedMb: Int,
    val foregroundPkg: String
)

internal data class AppMemoryEntry(
    val packageName: String,
    val label: String,
    val ramMb: Float,
    val swapMb: Float,
    val subtitleOverride: String? = null,
    val isHighRisk: Boolean = false,
    val cpuPct: Float = 0f,
    val cpuTimeSec: Long = 0L,
    val activeLocks: Int = 0,
    val heldMinutes: Float = 0f
)

internal object LightspeedVitalsHistoryStore {
    private const val FILE_NAME = "vitals_history.log"
    private const val SAMPLE_INTERVAL_MS = 60_000L
    private const val MAX_AGE_MS = 30L * 24L * 3_600_000L
    private const val MAX_SAMPLES = 4500

    private val samples = CopyOnWriteArrayList<VitalsSample>()
    @Volatile private var loaded = false
    @Volatile private var lastSampleMs = 0L

    fun ensureLoaded(context: Context) {
        if (loaded) return
        synchronized(this) {
            if (loaded) return
            try {
                val file = File(context.filesDir, FILE_NAME)
                if (file.exists()) {
                    val cutoff = System.currentTimeMillis() - MAX_AGE_MS
                    file.forEachLine { line ->
                        val parts = line.split("|")
                        if (parts.size >= 5) {
                            val ts = parts[0].toLongOrNull() ?: return@forEachLine
                            if (ts >= cutoff) {
                                samples.add(
                                    VitalsSample(
                                        timestampMs = ts,
                                        tempC = parts[1].toIntOrNull() ?: 30,
                                        ramUsedMb = parts[2].toIntOrNull() ?: 0,
                                        swapUsedMb = parts[3].toIntOrNull() ?: 0,
                                        foregroundPkg = parts[4]
                                    )
                                )
                                if (ts > lastSampleMs) lastSampleMs = ts
                            }
                        }
                    }
                }
            } catch (_: Exception) {}
            loaded = true
        }
    }

    fun recordSampleIfDue(
        context: Context,
        tempC: Int,
        ramUsedMb: Int,
        swapUsedMb: Int,
        foregroundPkg: String = LightspeedAccessibilityService.lastAppPackage.orEmpty(),
        force: Boolean = false
    ) {
        ensureLoaded(context)
        val now = System.currentTimeMillis()
        if (!force && (now - lastSampleMs) < SAMPLE_INTERVAL_MS) return
        lastSampleMs = now
        val safePkg = foregroundPkg.replace("|", "").replace("\n", "")
        val entry = VitalsSample(now, tempC, ramUsedMb, swapUsedMb, safePkg)
        samples.add(entry)
        while (samples.size > MAX_SAMPLES) {
            samples.removeAt(0)
        }
        try {
            val file = File(context.filesDir, FILE_NAME)
            file.appendText("${entry.timestampMs}|${entry.tempC}|${entry.ramUsedMb}|${entry.swapUsedMb}|${entry.foregroundPkg}\n")
        } catch (_: Exception) {}
    }

    fun getSamplesInWindow(context: Context, windowMs: Long): List<VitalsSample> {
        ensureLoaded(context)
        val cutoff = System.currentTimeMillis() - windowMs
        return samples.filter { it.timestampMs >= cutoff }
    }

    /**
     * Returns the elapsed time span (ms) covered by the oldest recorded sample in `vitals_history.log`.
     * Used by the Vitals Modal to display `!` on span pills (e.g. `7D !`, `30D !`) when full history
     * has not yet accumulated.
     */
    fun getAvailableHistorySpanMs(context: Context): Long {
        ensureLoaded(context)
        val oldest = samples.minOfOrNull { it.timestampMs } ?: return 0L
        return (System.currentTimeMillis() - oldest).coerceAtLeast(0L)
    }

    fun queryRunningAppMemory(context: Context): List<AppMemoryEntry> {
        val pm = context.packageManager
        val byPkg = linkedMapOf<String, Pair<Float, Float>>()

        if (ElevatedTaskCloser.isShizukuActive) {
            try {
                val proc = ElevatedTaskCloser.execShizuku("dumpsys meminfo 2>&1")
                if (proc != null) {
                    val reader = BufferedReader(InputStreamReader(proc.inputStream))
                    var inProcessSection = false
                    var line: String?
                    val lineRegex = Regex("""^\s*([\d,]+)K:\s+([a-zA-Z0-9._]+)(?::[^\s]+)?\s+\(pid\s+(\d+).*$""")
                    val swapRegex = Regex("""\(([\d,]+)K in swap\)""")
                    while (reader.readLine().also { line = it } != null) {
                        val l = line ?: continue
                        if (l.contains("Total PSS by process:", ignoreCase = true)) {
                            inProcessSection = true
                            continue
                        }
                        if (inProcessSection) {
                            if (l.isBlank() || l.contains("Total PSS by OOM", ignoreCase = true)) {
                                break
                            }
                            val m = lineRegex.find(l)
                            if (m != null) {
                                val totalPssKb = m.groupValues[1].replace(",", "").toFloatOrNull() ?: 0f
                                val pkg = m.groupValues[2]
                                val pid = m.groupValues[3].toIntOrNull() ?: 0
                                val swapInLineKb = swapRegex.find(l)?.groupValues?.getOrNull(1)?.replace(",", "")?.toFloatOrNull()
                                val swapKb = swapInLineKb ?: readPidVmSwapKb(pid)
                                // Separate cold zRAM SwapPss from active resident RAM PSS so RAM isn't inflated
                                val activeRamKb = (totalPssKb - swapKb).coerceAtLeast(totalPssKb * 0.35f)
                                if (pkg.contains(".")) {
                                    val prev = byPkg[pkg] ?: (0f to 0f)
                                    byPkg[pkg] = (prev.first + activeRamKb / 1024f) to (prev.second + swapKb / 1024f)
                                }
                            }
                        }
                    }
                    proc.waitFor()
                }
            } catch (_: Exception) {}
        }

        if (byPkg.isEmpty()) {
            try {
                val am = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
                val procs = am?.runningAppProcesses ?: emptyList()
                if (procs.isNotEmpty()) {
                    val pids = procs.map { it.pid }.toIntArray()
                    val memInfos = am?.getProcessMemoryInfo(pids)
                    for (i in procs.indices) {
                        val pkg = procs[i].pkgList?.firstOrNull() ?: continue
                        val info = memInfos?.getOrNull(i)
                        val totalPssMb = (info?.totalPss ?: 0) / 1024f
                        val swapMb = (info?.totalSwappablePss ?: 0) / 1024f
                        val prev = byPkg[pkg] ?: (0f to 0f)
                        byPkg[pkg] = (prev.first + totalPssMb) to (prev.second + swapMb)
                    }
                }
            } catch (_: Exception) {}
        }

        return byPkg.entries
            .mapNotNull { (pkg, mem) ->
                val label = try {
                    val ai = pm.getApplicationInfo(pkg, 0)
                    pm.getApplicationLabel(ai)?.toString() ?: pkg
                } catch (_: Exception) {
                    pkg.substringAfterLast('.')
                }
                AppMemoryEntry(pkg, label, mem.first, mem.second)
            }
            .sortedByDescending { it.ramMb }
    }

    /**
     * Hunts rogue background apps burning CPU cycles and causing thermal charging throttling
     * (even when hidden from Android's foreground screen-time battery list).
     */
    fun queryRogueCpuApps(context: Context): List<AppMemoryEntry> {
        val pm = context.packageManager
        val fgMinByPkg = queryRecentForegroundMinutes(context)
        data class CpuStat(var cpuPct: Float = 0f, var cpuTimeSec: Long = 0L)
        val statsByPkg = linkedMapOf<String, CpuStat>()

        if (ElevatedTaskCloser.isShizukuActive) {
            try {
                val proc = ElevatedTaskCloser.execShizuku("dumpsys cpuinfo 2>/dev/null; echo '===PS==='; ps -A -o %CPU,TIME,NAME 2>/dev/null")
                if (proc != null) {
                    val reader = BufferedReader(InputStreamReader(proc.inputStream))
                    var inPs = false
                    var line: String?
                    val dumpRegex = Regex("""^\s*([\d.]+)%\s+\d+/([a-zA-Z0-9._]+)(?::[^\s:]+)?.*$""")
                    val psRegex = Regex("""^\s*([\d.]+)\s+(?:(\d+)-)?(?:(\d+):)?(\d+):(\d+)\s+([a-zA-Z0-9._]+)(?::[^\s]+)?\s*$""")
                    while (reader.readLine().also { line = it } != null) {
                        val l = line ?: continue
                        if (l.contains("===PS===")) {
                            inPs = true
                            continue
                        }
                        if (!inPs) {
                            val m = dumpRegex.find(l) ?: continue
                            val pct = m.groupValues[1].toFloatOrNull() ?: 0f
                            val pkg = m.groupValues[2]
                            if (isTrackableAppPackage(pkg)) {
                                val st = statsByPkg.getOrPut(pkg) { CpuStat() }
                                st.cpuPct += pct
                            }
                        } else {
                            val m = psRegex.find(l) ?: continue
                            val pct = m.groupValues[1].toFloatOrNull() ?: 0f
                            val days = m.groupValues[2].toLongOrNull() ?: 0L
                            val hours = m.groupValues[3].toLongOrNull() ?: 0L
                            val mins = m.groupValues[4].toLongOrNull() ?: 0L
                            val secs = m.groupValues[5].toLongOrNull() ?: 0L
                            val pkg = m.groupValues[6]
                            val totalSec = days * 86400L + hours * 3600L + mins * 60L + secs
                            if (isTrackableAppPackage(pkg) && (pct > 0.1f || totalSec >= 15L)) {
                                val st = statsByPkg.getOrPut(pkg) { CpuStat() }
                                if (pct > st.cpuPct) st.cpuPct = pct
                                st.cpuTimeSec += totalSec
                            }
                        }
                    }
                    proc.waitFor()
                }
            } catch (_: Exception) {}
        }

        if (statsByPkg.isEmpty()) {
            // Non-Shizuku fallback: combine running processes + usage stats background activity
            return queryRunningAppMemory(context).take(30)
                .filter { isTrackableAppPackage(it.packageName) }
                .map { mem ->
                    val fgMin = fgMinByPkg[mem.packageName] ?: 0L
                    val isBgSuspect = fgMin <= 2L && mem.ramMb >= 140f
                    val estPct = if (isBgSuspect) 5.0f else 1.0f
                    val sub = if (isBgSuspect) {
                        "BG ACTIVE · ${mem.ramMb.toInt()}MB RAM · 0m FG (HEAT RISK)"
                    } else {
                        "RAM: ${mem.ramMb.toInt()}MB · FG TODAY: ${fgMin}m"
                    }
                    AppMemoryEntry(
                        packageName = mem.packageName,
                        label = mem.label,
                        ramMb = mem.ramMb + if (isBgSuspect) 500f else 0f,
                        swapMb = estPct,
                        subtitleOverride = sub,
                        isHighRisk = isBgSuspect,
                        cpuPct = estPct,
                        cpuTimeSec = 0L
                    )
                }
        }

        return statsByPkg.entries
            .map { (pkg, st) ->
                val label = resolveAppLabel(pm, pkg)
                val fgMin = fgMinByPkg[pkg] ?: 0L
                val cpuMin = st.cpuTimeSec / 60L
                val cpuSecRem = st.cpuTimeSec % 60L
                val timeStr = if (cpuMin > 0L) "${cpuMin}m ${cpuSecRem}s" else "${cpuSecRem}s"
                val isHiddenHog = (st.cpuPct >= 2.0f || cpuMin >= 8L) && fgMin <= maxOf(2L, cpuMin / 4L)
                val sub = buildString {
                    append(String.format(java.util.Locale.US, "CPU: %.1f%% · TIME: %s", st.cpuPct, timeStr))
                    if (isHiddenHog) {
                        append(" · BG HEAT HOG")
                    } else {
                        append(" · FG: ${fgMin}m")
                    }
                }
                val score = (st.cpuPct * 120f) + (st.cpuTimeSec * 0.25f) + (if (isHiddenHog) 500f else 0f)
                AppMemoryEntry(
                    packageName = pkg,
                    label = label,
                    ramMb = score,
                    swapMb = st.cpuPct,
                    subtitleOverride = sub,
                    isHighRisk = isHiddenHog,
                    cpuPct = st.cpuPct,
                    cpuTimeSec = st.cpuTimeSec
                )
            }
            .sortedByDescending { it.cpuPct }
            .take(40)
    }

    /**
     * Hunts apps holding PARTIAL_WAKE_LOCK or keeping the CPU awake in the background
     * (preventing deep sleep and draining battery while screen is off).
     */
    fun queryWakelockApps(context: Context): List<AppMemoryEntry> {
        val pm = context.packageManager
        data class WakeStat(var activeLocks: Int = 0, var lockNames: MutableSet<String> = linkedSetOf(), var heldMinutes: Float = 0f)
        val byPkg = linkedMapOf<String, WakeStat>()

        if (ElevatedTaskCloser.isShizukuActive) {
            try {
                val proc = ElevatedTaskCloser.execShizuku("dumpsys power 2>/dev/null")
                if (proc != null) {
                    val reader = BufferedReader(InputStreamReader(proc.inputStream))
                    var line: String?
                    val pkgRegex = Regex("""([a-zA-Z0-9._]+\.[a-zA-Z0-9._]+)""")
                    val uidRegex = Regex("""(?:uid=|WorkSource\{)(\d{4,6})""")
                    val tagRegex = Regex("""WAKE_LOCK\s+'([^']+)'""")
                    val durRegex = Regex("""\((\d+)m\s*(\d+)s|\((\d+)s""")
                    while (reader.readLine().also { line = it } != null) {
                        val l = line ?: continue
                        if (l.contains("_WAKE_LOCK", ignoreCase = true)) {
                            val pkgs = pkgRegex.findAll(l).map { it.value }.filter { isTrackableAppPackage(it) }.toMutableList()
                            if (pkgs.isEmpty()) {
                                val uid = uidRegex.find(l)?.groupValues?.getOrNull(1)?.toIntOrNull()
                                if (uid != null && uid >= 10000) {
                                    val uidPkgs = try { pm.getPackagesForUid(uid) } catch (_: Exception) { null }
                                    uidPkgs?.filter { isTrackableAppPackage(it) }?.let { pkgs.addAll(it) }
                                }
                            }
                            val pkg = pkgs.lastOrNull() ?: continue
                            val tag = tagRegex.find(l)?.groupValues?.getOrNull(1)?.take(18) ?: "WakeLock"
                            val st = byPkg.getOrPut(pkg) { WakeStat() }
                            st.activeLocks += 1
                            st.lockNames.add(tag)
                            val dMatch = durRegex.find(l)
                            if (dMatch != null) {
                                val m = dMatch.groupValues[1].toFloatOrNull() ?: 0f
                                val s = (dMatch.groupValues[2].toFloatOrNull() ?: dMatch.groupValues[3].toFloatOrNull() ?: 0f) / 60f
                                st.heldMinutes += (m + s)
                            }
                        }
                    }
                    proc.waitFor()
                }
            } catch (_: Exception) {}
        }

        // Supplement with background CPU/service holders so list is always actionable even when no instant lock is active
        for (cpuEntry in queryRogueCpuApps(context).take(20)) {
            if (!byPkg.containsKey(cpuEntry.packageName)) {
                val estMins = if (cpuEntry.cpuTimeSec > 0L) {
                    (cpuEntry.cpuTimeSec / 60f).coerceAtLeast(0.5f)
                } else {
                    (cpuEntry.cpuPct * 2f).coerceAtLeast(0.5f)
                }
                byPkg[cpuEntry.packageName] = WakeStat(
                    activeLocks = 0,
                    heldMinutes = estMins
                )
            }
        }

        return byPkg.entries
            .map { (pkg, st) ->
                val label = resolveAppLabel(pm, pkg)
                val isLiveLock = st.activeLocks > 0
                val sub = if (isLiveLock) {
                    val tags = st.lockNames.take(2).joinToString(",")
                    val durTag = if (st.heldMinutes > 0f) String.format(java.util.Locale.US, " · HELD %.1fM", st.heldMinutes) else ""
                    "ACTIVE WAKELOCK (${st.activeLocks})$durTag · $tags"
                } else {
                    String.format(java.util.Locale.US, "BG AWAKE ACTIVITY · EST %.1f MIN", st.heldMinutes)
                }
                val sortScore = (st.activeLocks * 1000f) + st.heldMinutes
                AppMemoryEntry(
                    packageName = pkg,
                    label = label,
                    ramMb = sortScore,
                    swapMb = st.activeLocks.toFloat(),
                    subtitleOverride = sub,
                    isHighRisk = isLiveLock,
                    activeLocks = st.activeLocks,
                    heldMinutes = st.heldMinutes
                )
            }
            .sortedWith(
                compareByDescending<AppMemoryEntry> { it.activeLocks }
                    .thenByDescending { it.heldMinutes }
            )
            .take(40)
    }

    private fun readPidVmSwapKb(pid: Int): Float {
        if (pid <= 0) return 0f
        return try {
            val statusFile = File("/proc/$pid/status")
            if (!statusFile.exists()) return 0f
            var swapKb = 0f
            statusFile.forEachLine { line ->
                if (line.startsWith("VmSwap:")) {
                    val kbStr = line.substringAfter("VmSwap:").replace("kB", "", ignoreCase = true).trim()
                    swapKb = kbStr.toFloatOrNull() ?: 0f
                }
            }
            swapKb
        } catch (_: Exception) {
            0f
        }
    }

    private fun isTrackableAppPackage(pkg: String): Boolean {
        if (!pkg.contains(".")) return false
        if (pkg.startsWith("com.sbf.lightspeed")) return false
        if (pkg == "android" || pkg == "system" || pkg == "com.android.systemui" || pkg == "com.android.phone") return false
        if (pkg.startsWith("android.hardware.") || pkg.startsWith("vendor.") || pkg.startsWith("com.mediatek.")) return false
        return true
    }

    private fun resolveAppLabel(pm: android.content.pm.PackageManager, pkg: String): String {
        return try {
            val ai = pm.getApplicationInfo(pkg, 0)
            pm.getApplicationLabel(ai)?.toString() ?: pkg
        } catch (_: Exception) {
            pkg.substringAfterLast('.')
        }
    }

    private fun queryRecentForegroundMinutes(context: Context): Map<String, Long> {
        val result = hashMapOf<String, Long>()
        try {
            val usm = context.getSystemService(Context.USAGE_STATS_SERVICE) as? android.app.usage.UsageStatsManager ?: return result
            val now = System.currentTimeMillis()
            val stats = usm.queryUsageStats(android.app.usage.UsageStatsManager.INTERVAL_DAILY, now - 24L * 3_600_000L, now)
            if (stats != null) {
                for (u in stats) {
                    val mins = u.totalTimeInForeground / 60_000L
                    if (mins > 0L) {
                        result[u.packageName] = (result[u.packageName] ?: 0L) + mins
                    }
                }
            }
        } catch (_: Exception) {}
        return result
    }

    fun forceStopApp(
        context: Context,
        packageName: String,
        appLabel: String,
        onBeforeStartSettings: (() -> Unit)? = null
    ): Boolean {
        if (!ShellArgGuard.isPackage(packageName)) return false
        val mainHandler = android.os.Handler(android.os.Looper.getMainLooper())
        if (ElevatedTaskCloser.isShizukuActive) {
            return try {
                val proc = ElevatedTaskCloser.execShizuku("am force-stop $packageName")
                val exit = proc?.waitFor() ?: -1
                if (exit == 0) {
                    mainHandler.post {
                        Toast.makeText(context, "✦ Force-stopped $appLabel", Toast.LENGTH_SHORT).show()
                    }
                    true
                } else {
                    mainHandler.post {
                        Toast.makeText(context, "Force-stop failed ($exit)", Toast.LENGTH_SHORT).show()
                    }
                    false
                }
            } catch (_: Exception) {
                false
            }
        } else {
            mainHandler.post {
                try {
                    onBeforeStartSettings?.invoke()
                    val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                        data = Uri.parse("package:$packageName")
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    context.startActivity(intent)
                } catch (_: Exception) {}
            }
            return false
        }
    }
}
