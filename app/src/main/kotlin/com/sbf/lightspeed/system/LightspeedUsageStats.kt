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
            if (days == 1) {
                val calendar = java.util.Calendar.getInstance().apply {
                    set(java.util.Calendar.HOUR_OF_DAY, 0)
                    set(java.util.Calendar.MINUTE, 0)
                    set(java.util.Calendar.SECOND, 0)
                    set(java.util.Calendar.MILLISECOND, 0)
                }
                val startOfToday = calendar.timeInMillis
                val events = usm.queryEvents(startOfToday, now)
                val resultMap = mutableMapOf<String, Long>()
                val lastResumeTime = mutableMapOf<String, Long>()
                val event = android.app.usage.UsageEvents.Event()
                while (events != null && events.hasNextEvent()) {
                    events.getNextEvent(event)
                    val pkg = event.packageName ?: continue
                    when (event.eventType) {
                        android.app.usage.UsageEvents.Event.ACTIVITY_RESUMED -> {
                            lastResumeTime[pkg] = event.timeStamp
                        }
                        android.app.usage.UsageEvents.Event.ACTIVITY_PAUSED,
                        android.app.usage.UsageEvents.Event.ACTIVITY_STOPPED -> {
                            val startResume = lastResumeTime.remove(pkg)
                            if (startResume != null && event.timeStamp > startResume) {
                                val duration = event.timeStamp - startResume
                                resultMap[pkg] = (resultMap[pkg] ?: 0L) + duration
                            }
                        }
                    }
                }
                for ((pkg, startResume) in lastResumeTime) {
                    if (now > startResume) {
                        resultMap[pkg] = (resultMap[pkg] ?: 0L) + (now - startResume)
                    }
                }
                if (resultMap.isEmpty()) {
                    val statsList = usm.queryUsageStats(UsageStatsManager.INTERVAL_DAILY, startOfToday, now) ?: emptyList()
                    for (stat in statsList) {
                        val pkg = stat.packageName ?: continue
                        val time = stat.totalTimeInForeground
                        if (time > 0L) {
                            resultMap[pkg] = (resultMap[pkg] ?: 0L) + time
                        }
                    }
                }
                return resultMap
            }

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
