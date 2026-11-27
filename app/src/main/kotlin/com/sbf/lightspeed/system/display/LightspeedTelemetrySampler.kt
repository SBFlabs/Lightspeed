package com.sbf.lightspeed.system.display

import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import com.sbf.lightspeed.system.LightspeedPreferences
import com.sbf.lightspeed.system.defaultPrefs
import java.io.File
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Hardware telemetry sampler for the Per-App Telemetry Micro-HUD (FPS, CPU, Temp, RAM, Battery, Watts).
 */
internal object LightspeedTelemetrySampler {

    fun collectMetrics(context: Context, currentFpsProvider: (Context) -> Int): List<TelemetryMetricItem> {
        val prefs = context.defaultPrefs()
        val items = ArrayList<TelemetryMetricItem>(6)

        val isCompact = prefs.getBoolean(LightspeedPreferences.KEY_TELEMETRY_HUD_COMPACT, false)
        val showFps = prefs.getBoolean(LightspeedPreferences.KEY_TELEMETRY_HUD_SHOW_FPS, true)
        val showCpu = prefs.getBoolean(LightspeedPreferences.KEY_TELEMETRY_HUD_SHOW_CPU, true)
        val showTemp = prefs.getBoolean(LightspeedPreferences.KEY_TELEMETRY_HUD_SHOW_TEMP, true)
        val showRam = prefs.getBoolean(LightspeedPreferences.KEY_TELEMETRY_HUD_SHOW_RAM, false)
        val showBat = prefs.getBoolean(LightspeedPreferences.KEY_TELEMETRY_HUD_SHOW_BAT, true)
        val showWatt = prefs.getBoolean(LightspeedPreferences.KEY_TELEMETRY_HUD_SHOW_WATT, true)

        val maxHz = LightspeedRefreshRateEngine.getLiveDisplayHz(context).coerceAtLeast(60)
        val fps = currentFpsProvider(context)
        val fpsFraction = (fps.toFloat() / maxHz.toFloat()).coerceIn(0f, 1f)

        if (isCompact) {
            items.add(TelemetryMetricItem(TelemetryMetricKind.FPS, "FPS", "$fps", fpsFraction, TelemetrySeverity.ACCENT))
            return items
        }

        if (showFps) {
            items.add(TelemetryMetricItem(TelemetryMetricKind.FPS, "FPS", "$fps", fpsFraction, TelemetrySeverity.ACCENT))
        }

        if (showCpu) {
            val cpuPct = readCpuClockLoadPercent()
            val cpuSeverity = when {
                cpuPct >= 88 -> TelemetrySeverity.DANGER
                cpuPct >= 72 -> TelemetrySeverity.WARN
                else -> TelemetrySeverity.NORMAL
            }
            items.add(TelemetryMetricItem(TelemetryMetricKind.CPU, "CPU", "$cpuPct%", (cpuPct / 100f).coerceIn(0f, 1f), cpuSeverity))
        }

        val batteryIntent = try {
            context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        } catch (_: Exception) { null }

        if (showTemp && batteryIntent != null) {
            val rawTemp = batteryIntent.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, 0)
            if (rawTemp > 0) {
                val tempC = (rawTemp / 10f).roundToInt()
                val tempSeverity = when {
                    tempC >= 44 -> TelemetrySeverity.DANGER
                    tempC >= 40 -> TelemetrySeverity.WARN
                    else -> TelemetrySeverity.NORMAL
                }
                items.add(TelemetryMetricItem(TelemetryMetricKind.TEMP, "TMP", "${tempC}°C", ((tempC - 24f) / 24f).coerceIn(0f, 1f), tempSeverity))
            }
        }

        if (showRam) {
            val am = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
            if (am != null) {
                val memInfo = ActivityManager.MemoryInfo()
                am.getMemoryInfo(memInfo)
                if (memInfo.totalMem > 0) {
                    val usedPct = (((memInfo.totalMem - memInfo.availMem) * 100L) / memInfo.totalMem).toInt().coerceIn(1, 99)
                    val ramSeverity = if (usedPct >= 85) TelemetrySeverity.WARN else TelemetrySeverity.NORMAL
                    items.add(TelemetryMetricItem(TelemetryMetricKind.RAM, "RAM", "$usedPct%", (usedPct / 100f).coerceIn(0f, 1f), ramSeverity))
                }
            }
        }

        if ((showBat || showWatt) && batteryIntent != null) {
            val level = batteryIntent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
            val scale = batteryIntent.getIntExtra(BatteryManager.EXTRA_SCALE, 100).coerceAtLeast(1)
            val pct = ((level * 100f) / scale).roundToInt().coerceIn(0, 100)
            val status = batteryIntent.getIntExtra(BatteryManager.EXTRA_STATUS, BatteryManager.BATTERY_STATUS_UNKNOWN)
            val isCharging = status == BatteryManager.BATTERY_STATUS_CHARGING || status == BatteryManager.BATTERY_STATUS_FULL
            val voltageMv = batteryIntent.getIntExtra(BatteryManager.EXTRA_VOLTAGE, 3850).let {
                if (it in 1..20) it * 1000 else if (it <= 0) 3850 else it
            }

            if (showBat && level >= 0) {
                val batSeverity = when {
                    isCharging -> TelemetrySeverity.OK
                    pct <= 10 -> TelemetrySeverity.DANGER
                    pct <= 20 -> TelemetrySeverity.WARN
                    else -> TelemetrySeverity.NORMAL
                }
                items.add(
                    TelemetryMetricItem(
                        kind = TelemetryMetricKind.BAT,
                        label = if (isCharging) "CHG" else "BAT",
                        value = "$pct%",
                        fraction = (pct / 100f).coerceIn(0f, 1f),
                        severity = batSeverity
                    )
                )
            }

            if (showWatt || isCharging) {
                val watts = readBatteryWatts(context, voltageMv)
                if (watts > 0.05f) {
                    val prefix = if (isCharging) "+" else ""
                    val formatted = String.format(Locale.US, "%s%.1fW", prefix, watts)
                    val wattSeverity = if (isCharging) TelemetrySeverity.OK else TelemetrySeverity.NORMAL
                    items.add(
                        TelemetryMetricItem(
                            kind = TelemetryMetricKind.WATT,
                            label = "PWR",
                            value = formatted,
                            fraction = (watts / 35f).coerceIn(0.06f, 1f),
                            severity = wattSeverity
                        )
                    )
                }
            }
        }

        if (items.isEmpty()) {
            items.add(TelemetryMetricItem(TelemetryMetricKind.FPS, "HZ", "$maxHz", 1f, TelemetrySeverity.ACCENT))
        }
        return items
    }

    private fun readBatteryWatts(context: Context, voltageMv: Int): Float {
        return try {
            val bm = context.getSystemService(Context.BATTERY_SERVICE) as? BatteryManager ?: return 0f
            val rawCurrent = bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CURRENT_NOW)
            if (rawCurrent == 0 || rawCurrent == Int.MIN_VALUE) return 0f
            val absCurrent = abs(rawCurrent).toFloat()
            val amps = if (absCurrent > 15_000f) absCurrent / 1_000_000f else absCurrent / 1_000f
            val volts = voltageMv / 1000f
            (amps * volts).coerceIn(0f, 240f)
        } catch (_: Exception) {
            0f
        }
    }

    private fun readCpuClockLoadPercent(): Int {
        return try {
            var weightedLoadSum = 0.0
            var totalCapacityKhz = 0.0
            for (i in 0 until 12) {
                val curFile = File("/sys/devices/system/cpu/cpu$i/cpufreq/scaling_cur_freq")
                val maxFile = File("/sys/devices/system/cpu/cpu$i/cpufreq/cpuinfo_max_freq")
                val minFile = File("/sys/devices/system/cpu/cpu$i/cpufreq/cpuinfo_min_freq")
                if (!curFile.exists() || !maxFile.exists()) continue
                val curKhz = curFile.readText().trim().toLongOrNull() ?: continue
                val maxKhz = maxFile.readText().trim().toLongOrNull()?.coerceAtLeast(1L) ?: continue
                val minKhz = if (minFile.exists()) {
                    minFile.readText().trim().toLongOrNull()?.coerceIn(0L, maxKhz - 1L) ?: 0L
                } else 0L
                val spanKhz = (maxKhz - minKhz).coerceAtLeast(1L)
                val normRatio = ((curKhz - minKhz).toDouble() / spanKhz.toDouble()).coerceIn(0.0, 1.0)
                weightedLoadSum += normRatio * maxKhz.toDouble()
                totalCapacityKhz += maxKhz.toDouble()
            }
            if (totalCapacityKhz > 0.0) {
                ((weightedLoadSum / totalCapacityKhz) * 100.0).roundToInt().coerceIn(0, 100)
            } else 0
        } catch (_: Exception) {
            0
        }
    }
}
