package com.sbf.lightspeed.system

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.provider.MediaStore

data class TacticalToolItem(
    val token: String,
    val label: String,
    val category: String // "AI Assistants" or "Vision & Sensors"
)

/**
 * Dynamically queries PackageManager at runtime to discover installed AI assistants
 * and camera/vision tools. Ensures zero ghost options are shown in hardware triggers.
 */
object InstalledTacticalToolsScanner {

    fun scan(context: Context): List<TacticalToolItem> {
        val pm = context.packageManager
        val tools = mutableListOf<TacticalToolItem>()

        // =========================================================================
        // 1. AI Overlays & Assistants (Dynamic Installed Validation)
        // =========================================================================

        // ChatGPT Voice / Overlay
        if (isPackageInstalled(pm, "com.openai.chatgpt")) {
            tools.add(
                TacticalToolItem(
                    token = LightspeedPreferences.ACTION_CHATGPT,
                    label = "ChatGPT Voice / Overlay",
                    category = "AI Assistants"
                )
            )
        }

        // Google Gemini
        if (isPackageInstalled(pm, "com.google.android.apps.bard") ||
            isIntentResolvable(pm, Intent("android.intent.action.VOICE_COMMAND"))
        ) {
            tools.add(
                TacticalToolItem(
                    token = LightspeedPreferences.ACTION_GEMINI,
                    label = "Google Gemini",
                    category = "AI Assistants"
                )
            )
        }

        // Folax AI (Infinix / Tecno Transsion System Assistant)
        if (isPackageInstalled(pm, "com.transsion.folax") || isPackageInstalled(pm, "com.transsion.folaxclient")) {
            tools.add(
                TacticalToolItem(
                    token = LightspeedPreferences.ACTION_FOLAX,
                    label = "Folax AI Assistant",
                    category = "AI Assistants"
                )
            )
        }

        // Samsung Bixby
        if (isPackageInstalled(pm, "com.samsung.android.bixby.agent")) {
            tools.add(
                TacticalToolItem(
                    token = "app:com.samsung.android.bixby.agent",
                    label = "Samsung Bixby",
                    category = "AI Assistants"
                )
            )
        }

        // Claude AI
        if (isPackageInstalled(pm, "com.anthropic.claude")) {
            tools.add(
                TacticalToolItem(
                    token = LightspeedPreferences.ACTION_CLAUDE,
                    label = "Claude AI",
                    category = "AI Assistants"
                )
            )
        }

        // =========================================================================
        // 2. Camera & Vision Tools (Dynamic System Capability Validation)
        // =========================================================================

        // Google Lens
        if (isPackageInstalled(pm, "com.google.ar.lens") ||
            isIntentResolvable(pm, Intent().setComponent(ComponentName("com.google.android.googlequicksearchbox", "com.google.android.apps.lens.MainActivity")))
        ) {
            tools.add(
                TacticalToolItem(
                    token = LightspeedPreferences.ACTION_LENS,
                    label = "Google Lens",
                    category = "Vision & Sensors"
                )
            )
        }

        // System QR Scanner
        if (isIntentResolvable(pm, Intent("com.google.android.gms.vision.barcode.SCAN")) ||
            isIntentResolvable(pm, Intent("com.google.zxing.client.android.SCAN"))
        ) {
            tools.add(
                TacticalToolItem(
                    token = LightspeedPreferences.ACTION_QR_SCANNER,
                    label = "System QR Scanner",
                    category = "Vision & Sensors"
                )
            )
        }

        // Camera (Photo Mode)
        if (isIntentResolvable(pm, Intent(MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA))) {
            tools.add(
                TacticalToolItem(
                    token = LightspeedPreferences.ACTION_CAMERA_PHOTO,
                    label = "Camera (Photo)",
                    category = "Vision & Sensors"
                )
            )
        }

        // Camera (Video Mode)
        if (isIntentResolvable(pm, Intent(MediaStore.INTENT_ACTION_VIDEO_CAMERA))) {
            tools.add(
                TacticalToolItem(
                    token = LightspeedPreferences.ACTION_CAMERA_VIDEO,
                    label = "Camera (Video)",
                    category = "Vision & Sensors"
                )
            )
        }

        // =========================================================================
        // 3. Cockpit & Hardware Quick Tools
        // =========================================================================

        // Flashlight / Torch (Built-in)
        tools.add(
            TacticalToolItem(
                token = "system:torch",
                label = "Flashlight / Torch",
                category = "Cockpit Tools"
            )
        )


        return tools
    }

    private fun isPackageInstalled(pm: PackageManager, packageName: String): Boolean {
        return try {
            pm.getPackageInfo(packageName, 0)
            true
        } catch (_: Exception) {
            false
        }
    }

    private fun isIntentResolvable(pm: PackageManager, intent: Intent): Boolean {
        return try {
            val list = pm.queryIntentActivities(intent, PackageManager.MATCH_DEFAULT_ONLY)
            list.isNotEmpty()
        } catch (_: Exception) {
            false
        }
    }
}
