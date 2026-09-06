package com.sbf.lightspeed

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.core.content.pm.ShortcutInfoCompat
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.core.graphics.drawable.IconCompat
import com.sbf.lightspeed.settings.SidebarSettingsActivity
import com.sbf.lightspeed.system.LightspeedAutomationReceiver

/**
 * Trampoline activity for 1-tap Home Screen shortcuts and launcher App Shortcuts.
 * Executes immediate toggle without displaying any visible UI window.
 */
class LightspeedToggleActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        @Suppress("DEPRECATION")
        overridePendingTransition(0, 0)

        val target = intent.getStringExtra(EXTRA_TARGET) ?: TARGET_MASTER
        val broadcastIntent = Intent(this, LightspeedAutomationReceiver::class.java).apply {
            when (target) {
                TARGET_DEFLECTORS -> action = LightspeedAutomationReceiver.ACTION_TOGGLE_DEFLECTORS
                TARGET_LEFT -> action = LightspeedAutomationReceiver.ACTION_TOGGLE_LEFT_DEFLECTOR
                TARGET_RIGHT -> action = LightspeedAutomationReceiver.ACTION_TOGGLE_RIGHT_DEFLECTOR
                else -> action = LightspeedAutomationReceiver.ACTION_TOGGLE_FLIGHT_MODE
            }
        }
        sendBroadcast(broadcastIntent)

        finish()
        @Suppress("DEPRECATION")
        overridePendingTransition(0, 0)
    }

    companion object {
        const val EXTRA_TARGET = "target"
        const val TARGET_MASTER = "master"
        const val TARGET_DEFLECTORS = "deflectors"
        const val TARGET_LEFT = "left_deflector"
        const val TARGET_RIGHT = "right_deflector"

        fun pinMasterToggleShortcut(context: Context) {
            if (!ShortcutManagerCompat.isRequestPinShortcutSupported(context)) {
                Toast.makeText(context, "Launcher does not support pinning shortcuts", Toast.LENGTH_SHORT).show()
                return
            }

            val launchIntent = Intent(context, LightspeedToggleActivity::class.java).apply {
                action = Intent.ACTION_VIEW
                putExtra(EXTRA_TARGET, TARGET_MASTER)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
            }

            val pinShortcutInfo = ShortcutInfoCompat.Builder(context, "pin_toggle_flight")
                .setShortLabel("Toggle Flight")
                .setLongLabel("Lightspeed Flight Mode (Arm / Standby)")
                .setIcon(IconCompat.createWithResource(context, R.mipmap.ic_launcher))
                .setIntent(launchIntent)
                .build()

            ShortcutManagerCompat.requestPinShortcut(context, pinShortcutInfo, null)
            Toast.makeText(context, "Requested Home Screen pin for Flight Toggle", Toast.LENGTH_SHORT).show()
        }

        fun pinDeflectorsToggleShortcut(context: Context) {
            if (!ShortcutManagerCompat.isRequestPinShortcutSupported(context)) {
                Toast.makeText(context, "Launcher does not support pinning shortcuts", Toast.LENGTH_SHORT).show()
                return
            }

            val launchIntent = Intent(context, LightspeedToggleActivity::class.java).apply {
                action = Intent.ACTION_VIEW
                putExtra(EXTRA_TARGET, TARGET_DEFLECTORS)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
            }

            val pinShortcutInfo = ShortcutInfoCompat.Builder(context, "pin_toggle_deflectors")
                .setShortLabel("Toggle Deflectors")
                .setLongLabel("Mute / Unmute Flank Deflectors")
                .setIcon(IconCompat.createWithResource(context, R.mipmap.ic_launcher))
                .setIntent(launchIntent)
                .build()

            ShortcutManagerCompat.requestPinShortcut(context, pinShortcutInfo, null)
            Toast.makeText(context, "Requested Home Screen pin for Deflectors Toggle", Toast.LENGTH_SHORT).show()
        }

        fun updateDynamicShortcuts(context: Context) {
            try {
                val flightIntent = Intent(context, LightspeedToggleActivity::class.java).apply {
                    action = Intent.ACTION_VIEW
                    putExtra(EXTRA_TARGET, TARGET_MASTER)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
                }
                val deflectorsIntent = Intent(context, LightspeedToggleActivity::class.java).apply {
                    action = Intent.ACTION_VIEW
                    putExtra(EXTRA_TARGET, TARGET_DEFLECTORS)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
                }
                val configIntent = Intent(context, SidebarSettingsActivity::class.java).apply {
                    action = Intent.ACTION_VIEW
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
                }

                val s1 = ShortcutInfoCompat.Builder(context, "dynamic_toggle_flight")
                    .setShortLabel("Flight Deck")
                    .setLongLabel("Arm / Standby Master Flight Mode")
                    .setIcon(IconCompat.createWithResource(context, R.mipmap.ic_launcher))
                    .setIntent(flightIntent)
                    .build()

                val s2 = ShortcutInfoCompat.Builder(context, "dynamic_toggle_deflectors")
                    .setShortLabel("Deflectors")
                    .setLongLabel("Mute / Unmute Kinetic Flanks")
                    .setIcon(IconCompat.createWithResource(context, R.mipmap.ic_launcher))
                    .setIntent(deflectorsIntent)
                    .build()

                val s3 = ShortcutInfoCompat.Builder(context, "dynamic_cockpit_matrix")
                    .setShortLabel("Cockpit Config")
                    .setLongLabel("Open Cockpit Matrix Settings")
                    .setIcon(IconCompat.createWithResource(context, R.mipmap.ic_launcher))
                    .setIntent(configIntent)
                    .build()

                ShortcutManagerCompat.setDynamicShortcuts(context, listOf(s1, s2, s3))
            } catch (_: Exception) {}
        }
    }
}
