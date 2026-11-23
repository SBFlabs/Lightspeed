package com.sbf.lightspeed.system

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.provider.Settings
import android.widget.Toast

/**
 * Opens native Android / OEM Settings screens (Wi-Fi, Bluetooth, Battery Usage, Digital Wellbeing)
 * using ordered `resolveActivity` fallback chains so no shortcut ever dead-ends.
 */
object NativeSettingsLauncher {

    fun openWifiSettings(context: Context): Boolean = launchFirstResolvable(
        context = context,
        candidates = listOf(
            Intent(Settings.ACTION_WIFI_SETTINGS),
            Intent(Settings.ACTION_WIRELESS_SETTINGS),
            Intent(Settings.ACTION_SETTINGS)
        )
    )

    fun openBluetoothSettings(context: Context): Boolean = launchFirstResolvable(
        context = context,
        candidates = listOf(
            Intent(Settings.ACTION_BLUETOOTH_SETTINGS),
            Intent(Settings.ACTION_WIRELESS_SETTINGS),
            Intent(Settings.ACTION_SETTINGS)
        )
    )

    fun openBatteryUsage(context: Context): Boolean = launchFirstResolvable(
        context = context,
        candidates = listOf(
            Intent(Intent.ACTION_POWER_USAGE_SUMMARY),
            Intent().setComponent(ComponentName("com.samsung.android.lool", "com.samsung.android.sm.battery.ui.BatteryActivity")),
            Intent().setComponent(ComponentName("com.miui.securitycenter", "com.miui.powercenter.PowerMainActivity")),
            Intent(Settings.ACTION_BATTERY_SAVER_SETTINGS),
            Intent(Settings.ACTION_SETTINGS)
        )
    )

    fun openDigitalWellbeing(context: Context): Boolean {
        val pm = context.packageManager
        val explicitIntents = mutableListOf<Intent>(
            Intent("android.settings.DIGITAL_WELLBEING_SETTINGS"),
            Intent("com.android.settings.DIGITAL_WELLBEING_SETTINGS"),
            Intent().setComponent(
                ComponentName(
                    "com.google.android.apps.wellbeing",
                    "com.google.android.apps.wellbeing.settings.TopLevelSettingsActivity"
                )
            ),
            Intent().setComponent(
                ComponentName(
                    "com.samsung.android.forest",
                    "com.samsung.android.forest.launcher.LauncherActivity"
                )
            )
        )

        val wellbeingPackages = listOf(
            "com.google.android.apps.wellbeing",
            "com.samsung.android.forest",
            "com.transsion.digitalwellbeing",
            "com.transsion.healthlife",
            "com.xiaomi.misettings",
            "com.oplus.digitalwellbeing",
            "com.coloros.digitalwellbeing",
            "com.huawei.parentcontrol",
            "com.hihonor.parentcontrol",
            "com.vivo.familycare.local"
        )
        for (pkg in wellbeingPackages) {
            try {
                pm.getLaunchIntentForPackage(pkg)?.let { explicitIntents.add(it) }
            } catch (_: Exception) {}
        }

        explicitIntents.add(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))
        explicitIntents.add(Intent(Settings.ACTION_SETTINGS))

        return launchFirstResolvable(context, explicitIntents)
    }

    private fun launchFirstResolvable(context: Context, candidates: List<Intent>): Boolean {
        val pm = context.packageManager
        val launchBundle = LightspeedShortcutManager.createLaunchBundle(context)
        for (raw in candidates) {
            val intent = Intent(raw).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            }
            val canResolve = try {
                intent.resolveActivity(pm) != null
            } catch (_: Exception) {
                false
            }
            if (!canResolve) continue
            val launched = try {
                if (launchBundle != null) {
                    context.startActivity(intent, launchBundle)
                } else {
                    context.startActivity(intent)
                }
                true
            } catch (_: Exception) {
                false
            }
            if (launched) return true
        }
        try {
            Toast.makeText(context, "Unable to open Android Settings", Toast.LENGTH_SHORT).show()
        } catch (_: Exception) {}
        return false
    }
}
