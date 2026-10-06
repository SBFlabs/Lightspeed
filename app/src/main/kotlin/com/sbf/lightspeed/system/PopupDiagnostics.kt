package com.sbf.lightspeed.system

import android.app.ActivityOptions
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.util.Log
import android.widget.Toast
import java.io.BufferedReader
import java.io.InputStreamReader
import java.util.concurrent.ConcurrentLinkedQueue

object PopupDiagnostics {
    private const val TAG = "PopupDiagnostics"

    @Volatile
    var lastSplitPerformGlobalResult: Boolean? = null

    @Volatile
    var lastSplitForegroundPackage: String? = null

    @Volatile
    var lastTopTaskLine: String? = null

    data class StepRecord(
        val action: String,
        val step: String,
        val ran: Boolean,
        val returned: String,
        @Volatile var verified: Boolean,
        val timestamp: Long = System.currentTimeMillis()
    )

    private val stepRecords = ConcurrentLinkedQueue<StepRecord>()
    private val lastResults = ConcurrentLinkedQueue<String>()
    private val swallowedLogs = ConcurrentLinkedQueue<String>()

    fun recordSwallowed(tag: String, where: String, e: Throwable) {
        val entry = "[${System.currentTimeMillis()}] $tag: $where: ${e.javaClass.simpleName}: ${e.message}"
        swallowedLogs.add(entry)
        while (swallowedLogs.size > 20) {
            swallowedLogs.poll()
        }
    }

    fun recordSplitAttempt(performGlobalResult: Boolean?, foregroundPackage: String?) {
        lastSplitPerformGlobalResult = performGlobalResult
        lastSplitForegroundPackage = foregroundPackage
        recordLastResult("split:performGlobalAction", "returned=$performGlobalResult, fgPackage=$foregroundPackage")
    }

    fun recordStep(
        action: String,
        step: String,
        ran: Boolean,
        returned: String,
        verified: Boolean
    ) {
        val record = StepRecord(action, step, ran, returned, verified)
        stepRecords.add(record)
        while (stepRecords.size > 25) {
            stepRecords.poll()
        }
        recordLastResult(action, "$step: ran=$ran, returned=$returned, verified=$verified")
    }

    fun updateStepVerification(action: String, step: String, verified: Boolean) {
        stepRecords.filter { it.action == action && it.step == step }.lastOrNull()?.verified = verified
        recordLastResult(action, "$step: verified=$verified")
    }

    fun recordLastResult(action: String, result: String) {
        val entry = "[${System.currentTimeMillis()}] $action: $result"
        lastResults.add(entry)
        while (lastResults.size > 25) {
            lastResults.poll()
        }
    }

    fun collect(context: Context): String {
        val sb = StringBuilder()
        sb.appendLine("=== LIGHTSPEED POPUP & SPLIT DIAGNOSTICS ===")
        sb.appendLine("Device: ${Build.MANUFACTURER} ${Build.BRAND} ${Build.MODEL} (SDK ${Build.VERSION.SDK_INT})")
        sb.appendLine()

        // 1. Settings.Global & Freeform Feature Status
        sb.appendLine("--- Freeform Configuration & Features ---")
        val enableFreeform = try {
            Settings.Global.getInt(context.contentResolver, "enable_freeform_support", -1)
        } catch (e: Exception) {
            -1
        }
        val forceResizable = try {
            Settings.Global.getInt(context.contentResolver, "force_resizable_activities", -1)
        } catch (e: Exception) {
            -1
        }
        val hasFreeformFeature = try {
            context.packageManager.hasSystemFeature("android.software.freeform_window_management")
        } catch (e: Exception) {
            false
        }

        sb.appendLine("Settings.Global enable_freeform_support = $enableFreeform")
        sb.appendLine("Settings.Global force_resizable_activities = $forceResizable")
        sb.appendLine("Feature android.software.freeform_window_management = $hasFreeformFeature")
        sb.appendLine()

        // 2. Raw Task{...} Line from dumpsys
        sb.appendLine("--- Raw Top Task Line (dumpsys) ---")
        sb.appendLine(lastTopTaskLine ?: "None recorded yet")
        sb.appendLine()

        // 3. Last Split Screen Attempt
        sb.appendLine("--- Last Split Screen Attempt ---")
        sb.appendLine("performGlobalAction return value: ${lastSplitPerformGlobalResult ?: "None recorded"}")
        sb.appendLine("Foreground package: ${lastSplitForegroundPackage ?: "None recorded"}")
        sb.appendLine()

        // 4. Per-Step Results (ran / returned / verified)
        sb.appendLine("--- Per-Step Results (Last Pop-up & Split) ---")
        if (stepRecords.isEmpty()) {
            sb.appendLine("No per-step records available")
        } else {
            stepRecords.forEach { rec ->
                sb.appendLine("• [${rec.action.uppercase()}] ${rec.step}")
                sb.appendLine("    ran=${rec.ran} | returned=${rec.returned} | verified=${rec.verified}")
            }
        }
        sb.appendLine()

        // 5. System Features Matching
        sb.appendLine("--- Other Windowing Features ---")
        try {
            val featureRegex = Regex("""(?i)(freeform|multi_window|floating|pip)""")
            val pm = context.packageManager
            val matchingFeatures = pm.systemAvailableFeatures
                .mapNotNull { it.name }
                .filter { featureRegex.containsMatchIn(it) }
            if (matchingFeatures.isEmpty()) {
                sb.appendLine("None found")
            } else {
                matchingFeatures.forEach { sb.appendLine("• $it") }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed reading system features", e)
            sb.appendLine("Error reading features: ${e.message}")
        }
        sb.appendLine()

        // 6. getprop lines matching multiwindow/freeform/floating/popup
        sb.appendLine("--- Relevant getprop entries ---")
        try {
            val propRegex = Regex("""(?i)(multiwindow|freeform|floating|popup)""")
            val proc = Runtime.getRuntime().exec("getprop")
            val reader = BufferedReader(InputStreamReader(proc.inputStream))
            var count = 0
            reader.useLines { lines ->
                for (line in lines) {
                    if (propRegex.containsMatchIn(line)) {
                        sb.appendLine(line.trim())
                        count++
                    }
                }
            }
            proc.waitFor()
            if (count == 0) {
                sb.appendLine("No matching getprop properties found")
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed reading getprop", e)
            sb.appendLine("Error reading getprop: ${e.message}")
        }
        sb.appendLine()

        // 7. ActivityOptions methods matching regex
        sb.appendLine("--- ActivityOptions Reflected Methods ---")
        try {
            val methodRegex = Regex("""(?i)(freeform|floating|popup|pop_up|multiwindow|minimize)""")
            val methods = ActivityOptions::class.java.declaredMethods
                .filter { methodRegex.containsMatchIn(it.name) }
            if (methods.isEmpty()) {
                sb.appendLine("No matching ActivityOptions methods")
            } else {
                for (m in methods) {
                    val params = m.parameterTypes.joinToString(", ") { it.simpleName }
                    sb.appendLine("• ${m.name}($params) : ${m.returnType.simpleName}")
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed inspecting ActivityOptions methods", e)
            sb.appendLine("Error inspecting ActivityOptions: ${e.message}")
        }
        sb.appendLine()

        // 8. Recent Strategy History
        sb.appendLine("--- Recent Strategy Log Entries ---")
        if (lastResults.isEmpty()) {
            sb.appendLine("No recent actions recorded")
        } else {
            for (res in lastResults) {
                sb.appendLine(res)
            }
        }
        sb.appendLine()

        // 9. Swallowed Exceptions Log (Last 20)
        sb.appendLine("--- Swallowed Exceptions Log (Last 20) ---")
        if (swallowedLogs.isEmpty()) {
            sb.appendLine("No swallowed exceptions recorded")
        } else {
            for (sw in swallowedLogs) {
                sb.appendLine(sw)
            }
        }

        return sb.toString()
    }

    fun copyToClipboard(context: Context) {
        try {
            val text = collect(context)
            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
            val clip = ClipData.newPlainText("Popup Diagnostics", text)
            clipboard?.setPrimaryClip(clip)
            Handler(Looper.getMainLooper()).post {
                Toast.makeText(context, "Diagnostics copied", Toast.LENGTH_SHORT).show()
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to copy diagnostics to clipboard", e)
            Handler(Looper.getMainLooper()).post {
                Toast.makeText(context, "Failed to copy diagnostics: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }
}
