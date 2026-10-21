package com.sbf.lightspeed.system

import android.app.AppOpsManager
import android.app.usage.UsageStatsManager
import android.content.Context
import android.os.Process

object LightspeedUsageStats {

    fun hasUsageAccess(context: Context): Boolean {
        return try {
            val appOps = context.getSystemService(Context.APP_OPS_SERVICE) as? AppOpsManager ?: return false
            val uid = Process.myUid()
            @Suppress("DEPRECATION")
            val mode = appOps.checkOpNoThrow(
                AppOpsManager.OPSTR_GET_USAGE_STATS,
                uid,
                context.packageName
            )
            mode == AppOpsManager.MODE_ALLOWED
        } catch (_: Throwable) {
            false
        }
    }

    fun usageMillisByPackage(context: Context, days: Int = 30): Map<String, Long> {
        return try {
            val usm = context.getSystemService(Context.USAGE_STATS_SERVICE) as? UsageStatsManager ?: return emptyMap()
            val now = System.currentTimeMillis()
            val start = now - (days.toLong() * 24L * 60L * 60L * 1000L)
            val statsList = usm.queryUsageStats(UsageStatsManager.INTERVAL_BEST, start, now) ?: return emptyMap()
            val resultMap = mutableMapOf<String, Long>()
            for (stat in statsList) {
                val pkg = stat.packageName ?: continue
                val time = stat.totalTimeInForeground
                if (time > 0L) {
                    resultMap[pkg] = (resultMap[pkg] ?: 0L) + time
                }
            }
            resultMap
        } catch (_: Throwable) {
            emptyMap()
        }
    }
}

fun hasUsageAccess(context: Context): Boolean = LightspeedUsageStats.hasUsageAccess(context)
fun usageMillisByPackage(context: Context, days: Int = 30): Map<String, Long> = LightspeedUsageStats.usageMillisByPackage(context, days)
