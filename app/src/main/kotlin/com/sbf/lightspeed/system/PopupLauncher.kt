package com.sbf.lightspeed.system

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Rect
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.util.Log
import android.widget.Toast
import java.lang.reflect.Method
import java.util.Locale

internal object PopupLauncher {
    private const val TAG = "PopupLauncher"

    @Deprecated("Use launchInPopup(context, style) instead")
    fun launchInFreeform(context: Context) {
        val style = context.defaultPrefs().getString(
            LightspeedPreferences.KEY_POPUP_STYLE,
            LightspeedPreferences.POPUP_STYLE_NATIVE
        ) ?: LightspeedPreferences.POPUP_STYLE_NATIVE
        launchInPopup(context, style)
    }

    fun launchInPopup(context: Context, style: String = LightspeedPreferences.POPUP_STYLE_NATIVE) {
        Log.w(TAG, "launchInPopup() invoked with style: $style")
        ElevatedTaskCloser.exemptHiddenApis()

        // Run all Shizuku/shell work off the main thread
        ElevatedTaskCloser.executor.execute {
            try {
                val taskInfo = ElevatedTaskCloser.getTopForegroundTaskInfo(context)
                val targetTaskId = taskInfo.taskId
                val targetPkg = taskInfo.packageName
                val topComponent = taskInfo.componentName

                if (targetTaskId == null && targetPkg == null && topComponent == null) {
                    Log.w(TAG, "No foreground task found to launch in Pop-up view")
                    PopupDiagnostics.recordLastResult("launchInPopup", "No foreground task detected")
                    Handler(Looper.getMainLooper()).post {
                        Toast.makeText(context, "Pop-up failed: no active app found", Toast.LENGTH_SHORT).show()
                    }
                    return@execute
                }

                if (style == LightspeedPreferences.POPUP_STYLE_OEM) {
                    val oemResult = executeOemPopup(context, targetTaskId, targetPkg, topComponent)
                    if (oemResult.success) {
                        Handler(Looper.getMainLooper()).post {
                            Toast.makeText(context, "Pop-up: OK", Toast.LENGTH_SHORT).show()
                        }
                    } else {
                        Log.w(TAG, "OEM Pop-up failed or unverified (${oemResult.step}); falling back to Native")
                        PopupDiagnostics.recordLastResult(
                            "launchInPopup:oem",
                            "OEM unverified (${oemResult.step}), falling back to native"
                        )
                        val nativeResult = executeNativePopup(context, targetTaskId, targetPkg, topComponent)
                        Handler(Looper.getMainLooper()).post {
                            if (nativeResult.success) {
                                Toast.makeText(context, "Pop-up: OK", Toast.LENGTH_SHORT).show()
                            } else {
                                Toast.makeText(context, "Pop-up failed: ${nativeResult.step}", Toast.LENGTH_SHORT).show()
                            }
                        }
                    }
                } else {
                    val nativeResult = executeNativePopup(context, targetTaskId, targetPkg, topComponent)
                    Handler(Looper.getMainLooper()).post {
                        if (nativeResult.success) {
                            Toast.makeText(context, "Pop-up: OK", Toast.LENGTH_SHORT).show()
                        } else {
                            Toast.makeText(context, "Pop-up failed: ${nativeResult.step}", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            } catch (t: Throwable) {
                Log.e(TAG, "launchInPopup error", t)
                Handler(Looper.getMainLooper()).post {
                    Toast.makeText(context, "Pop-up failed: unexpected error", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun executeNativePopup(
        context: Context,
        targetTaskId: Int?,
        targetPkg: String?,
        topComponent: String?
    ): PopupLaunchResult {
        // a. Shizuku shell: "am start --windowingMode 5 --activity-reuse-task -n <topComponent>"
        if (topComponent != null && ElevatedTaskCloser.isShizukuActive) {
            val cmd = "am start --windowingMode 5 --activity-reuse-task -n $topComponent"
            val process = ElevatedTaskCloser.execShizuku(cmd)
            val exitCode = process.waitForOrKill()
            PopupDiagnostics.recordStep(
                "popup",
                "native:shizuku_am_start",
                ran = true,
                returned = "exit $exitCode",
                verified = false
            )
            if (exitCode == 0) {
                Log.i(TAG, "Native freeform started via Shizuku shell: $cmd, waiting ~500ms to verify...")
                val (verified, line) = ElevatedTaskCloser.verifyWindowModePolling(targetTaskId, targetPkg, "freeform")
                if (verified) {
                    PopupDiagnostics.updateStepVerification("popup", "native:shizuku_am_start", true)
                    Log.w(TAG, "Native freeform verified via Shizuku shell: $line")
                    return PopupLaunchResult(success = true, step = "shizuku_am_start")
                } else {
                    Log.w(TAG, "Native freeform unverified after Shizuku shell: $line")
                }
            } else {
                Log.w(TAG, "Shizuku am start failed with exit code $exitCode: $cmd")
            }
        }

        // b. Hidden ActivityOptions.setLaunchWindowingMode(5) plus setLaunchBounds(centered ~70% of screen)
        val launchIntent = when {
            topComponent != null -> {
                val cn = ComponentName.unflattenFromString(topComponent)
                if (cn != null) Intent().setComponent(cn) else null
            }
            targetPkg != null -> context.packageManager.getLaunchIntentForPackage(targetPkg)
            else -> null
        }

        if (launchIntent != null) {
            launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            val options = android.app.ActivityOptions.makeBasic()
            var modeSet = false
            try {
                val setLaunchWindowingModeMethod =
                    options.javaClass.getMethod("setLaunchWindowingMode", Int::class.javaPrimitiveType)
                setLaunchWindowingModeMethod.invoke(options, 5) // 5 = WINDOWING_MODE_FREEFORM
                modeSet = true
            } catch (e: Exception) {
                Log.w(TAG, "ActivityOptions.setLaunchWindowingMode failed", e)
            }

            val metrics = context.resources.displayMetrics
            val screenW = metrics.widthPixels
            val screenH = metrics.heightPixels
            val targetW = (screenW * 0.70f).toInt()
            val targetH = (screenH * 0.70f).toInt()
            val left = (screenW - targetW) / 2
            val top = (screenH - targetH) / 2
            val bounds = Rect(left, top, left + targetW, top + targetH)

            try {
                val setLaunchBoundsMethod = options.javaClass.getMethod("setLaunchBounds", Rect::class.java)
                setLaunchBoundsMethod.invoke(options, bounds)
            } catch (e: Exception) {
                Log.w(TAG, "ActivityOptions.setLaunchBounds failed", e)
            }

            if (modeSet) {
                try {
                    context.startActivity(launchIntent, options.toBundle())
                    PopupDiagnostics.recordStep(
                        "popup",
                        "native:activity_options",
                        ran = true,
                        returned = "startActivity called",
                        verified = false
                    )
                    Log.i(TAG, "Native freeform started via startActivity, waiting ~500ms to verify...")
                    val (verified, line) = ElevatedTaskCloser.verifyWindowModePolling(targetTaskId, targetPkg, "freeform")
                    if (verified) {
                        PopupDiagnostics.updateStepVerification("popup", "native:activity_options", true)
                        Log.w(TAG, "Native freeform verified via ActivityOptions: $line")
                        return PopupLaunchResult(success = true, step = "activity_options")
                    } else {
                        Log.w(TAG, "Native freeform unverified after ActivityOptions: $line")
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "startActivity native freeform failed", e)
                    PopupDiagnostics.recordStep(
                        "popup",
                        "native:activity_options",
                        ran = true,
                        returned = "error: ${e.message}",
                        verified = false
                    )
                }
            }
        }

        // c. Check Settings.Global enable_freeform_support / force_resizable_activities
        val enableFreeform = try {
            Settings.Global.getInt(context.contentResolver, "enable_freeform_support", 0)
        } catch (e: Exception) {
            Log.w(TAG, "Failed reading enable_freeform_support", e)
            0
        }
        val forceResizable = try {
            Settings.Global.getInt(context.contentResolver, "force_resizable_activities", 0)
        } catch (e: Exception) {
            Log.w(TAG, "Failed reading force_resizable_activities", e)
            0
        }

        if (enableFreeform == 0 || forceResizable == 0) {
            Handler(Looper.getMainLooper()).post {
                Toast.makeText(context, "Freeform is off. Enable it in Developer options", Toast.LENGTH_LONG).show()
            }
            if (ElevatedTaskCloser.isShizukuActive) {
                val p1 = ElevatedTaskCloser.execShizuku("settings put global enable_freeform_support 1")
                val c1 = p1.waitForOrKill()
                val p2 = ElevatedTaskCloser.execShizuku("settings put global force_resizable_activities 1")
                val c2 = p2.waitForOrKill()
                if (c1 == 0 && c2 == 0) {
                    Log.i(TAG, "Enabled freeform Settings.Global keys via Shizuku")
                    PopupDiagnostics.recordLastResult("launchInPopup:native", "Enabled freeform keys via Shizuku")
                    Handler(Looper.getMainLooper()).post {
                        Toast.makeText(
                            context,
                            "Freeform enabled via Shizuku. Reboot required to take effect",
                            Toast.LENGTH_LONG
                        ).show()
                    }
                } else {
                    Log.w(TAG, "Failed enabling freeform keys via Shizuku: c1=$c1, c2=$c2")
                }
            }
            return PopupLaunchResult(success = false, step = "developer options freeform off")
        }

        return PopupLaunchResult(success = false, step = "mode=freeform not confirmed")
    }

    private fun executeOemPopup(
        context: Context,
        targetTaskId: Int?,
        targetPkg: String?,
        topComponent: String?
    ): PopupLaunchResult {
        val brand = (Build.BRAND ?: "").lowercase(Locale.US)
        val manufacturer = (Build.MANUFACTURER ?: "").lowercase(Locale.US)
        val combined = "$brand $manufacturer"

        Log.w(TAG, "Executing OEM pop-up check for signature: $combined")

        if (brand.contains("infinix") || brand.contains("tecno") || brand.contains("itel") || manufacturer.contains("transsion")) {
            Log.w(TAG, "OEM pop-up on Transsion is handled by internal com.transsion.multiwindow service (signature-only) without shell/intent API. Falling back to native.")
            PopupDiagnostics.recordLastResult("launchInPopup:oem", "Transsion OEM pop-up internal; falling back to native")
            return PopupLaunchResult(success = false, step = "oem pop-up unsupported on Transsion")
        }

        val launchIntent = when {
            topComponent != null -> {
                val cn = ComponentName.unflattenFromString(topComponent)
                if (cn != null) Intent().setComponent(cn) else null
            }
            targetPkg != null -> context.packageManager.getLaunchIntentForPackage(targetPkg)
            else -> null
        }

        if (launchIntent == null) {
            Log.w(TAG, "Could not resolve launch intent for OEM pop-up")
            PopupDiagnostics.recordLastResult("launchInPopup:oem", "No launch intent resolved")
            return PopupLaunchResult(success = false, step = "no launch intent")
        }

        launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

        val options = android.app.ActivityOptions.makeBasic()
        val metrics = context.resources.displayMetrics
        val screenW = metrics.widthPixels
        val screenH = metrics.heightPixels
        val targetW = (screenW * 0.70f).toInt()
        val targetH = (screenH * 0.70f).toInt()
        val left = (screenW - targetW) / 2
        val top = (screenH - targetH) / 2
        val bounds = Rect(left, top, left + targetW, top + targetH)

        val methodRegex = Regex("""(?i)(freeform|floating|popup|pop_up|multiwindow|minimize)""")
        var anyMatchInvoked = false

        val declaredMethods = try {
            android.app.ActivityOptions::class.java.declaredMethods
        } catch (e: Exception) {
            Log.w(TAG, "Failed reading declaredMethods of ActivityOptions", e)
            emptyArray<Method>()
        }

        for (m in declaredMethods) {
            if (methodRegex.containsMatchIn(m.name)) {
                val pTypes = m.parameterTypes
                Log.i(TAG, "OEM candidate ActivityOptions match: ${m.name}(${pTypes.joinToString { it.simpleName }})")
                if (pTypes.size == 1) {
                    val p0 = pTypes[0]
                    try {
                        m.isAccessible = true
                        when {
                            p0 == Boolean::class.javaPrimitiveType || p0 == java.lang.Boolean::class.java -> {
                                m.invoke(options, true)
                                anyMatchInvoked = true
                                Log.i(TAG, "Invoked OEM ActivityOptions method: ${m.name}(true)")
                                PopupDiagnostics.recordLastResult("launchInPopup:oem", "Invoked ${m.name}(true)")
                            }
                            p0 == Int::class.javaPrimitiveType || p0 == java.lang.Integer::class.java -> {
                                m.invoke(options, 5) // 5 = WINDOWING_MODE_FREEFORM
                                anyMatchInvoked = true
                                Log.i(TAG, "Invoked OEM ActivityOptions method: ${m.name}(5)")
                                PopupDiagnostics.recordLastResult("launchInPopup:oem", "Invoked ${m.name}(5)")
                            }
                            Rect::class.java.isAssignableFrom(p0) -> {
                                m.invoke(options, bounds)
                                anyMatchInvoked = true
                                Log.i(TAG, "Invoked OEM ActivityOptions method: ${m.name}(bounds)")
                                PopupDiagnostics.recordLastResult("launchInPopup:oem", "Invoked ${m.name}(Rect)")
                            }
                        }
                    } catch (e: Exception) {
                        Log.w(TAG, "Failed invoking OEM method ${m.name}", e)
                        PopupDiagnostics.recordLastResult("launchInPopup:oem", "Failed invoking ${m.name}: ${e.message}")
                    }
                }
            }
        }

        // 1. Try starting with mutated ActivityOptions (ensuring options is the exact object passed)
        if (anyMatchInvoked) {
            try {
                context.startActivity(launchIntent, options.toBundle())
                PopupDiagnostics.recordStep(
                    "popup",
                    "oem:activity_options",
                    ran = true,
                    returned = "startActivity called",
                    verified = false
                )
                Log.i(TAG, "OEM Pop-up started via startActivity, waiting ~500ms to verify...")
                val (verified, line) = ElevatedTaskCloser.verifyWindowModePolling(targetTaskId, targetPkg, "freeform")
                if (verified) {
                    PopupDiagnostics.updateStepVerification("popup", "oem:activity_options", true)
                    Log.w(TAG, "OEM Pop-up verified via ActivityOptions: $line")
                    return PopupLaunchResult(success = true, step = "oem_activity_options")
                } else {
                    Log.w(TAG, "OEM Pop-up unverified after ActivityOptions: $line")
                }
            } catch (e: Exception) {
                Log.w(TAG, "startActivity failed with OEM options", e)
                PopupDiagnostics.recordStep(
                    "popup",
                    "oem:activity_options",
                    ran = true,
                    returned = "error: ${e.message}",
                    verified = false
                )
            }
        } else {
            Log.w(TAG, "No matching OEM ActivityOptions methods found")
            PopupDiagnostics.recordStep(
                "popup",
                "oem:activity_options",
                ran = false,
                returned = "no methods found",
                verified = false
            )
        }

        // 2. Also try the same launch through Shizuku (am start --windowingMode 5 -n <component>)
        //    since non-system apps have windowing-mode options ignored
        if (topComponent != null && ElevatedTaskCloser.isShizukuActive) {
            val cmd = "am start --windowingMode 5 -n $topComponent"
            val process = ElevatedTaskCloser.execShizuku(cmd)
            val exitCode = process.waitForOrKill()
            PopupDiagnostics.recordStep(
                "popup",
                "oem:shizuku_am_start",
                ran = true,
                returned = "exit $exitCode",
                verified = false
            )
            if (exitCode == 0) {
                Log.i(TAG, "OEM pop-up started via Shizuku shell: $cmd, waiting ~500ms to verify...")
                val (verified, line) = ElevatedTaskCloser.verifyWindowModePolling(targetTaskId, targetPkg, "freeform")
                if (verified) {
                    PopupDiagnostics.updateStepVerification("popup", "oem:shizuku_am_start", true)
                    Log.w(TAG, "OEM pop-up verified via Shizuku shell: $line")
                    return PopupLaunchResult(success = true, step = "oem_shizuku_am_start")
                } else {
                    Log.w(TAG, "OEM pop-up unverified after Shizuku shell: $line")
                }
            } else {
                Log.w(TAG, "OEM Shizuku am start failed with exit code $exitCode: $cmd")
            }
        }

        return PopupLaunchResult(success = false, step = "oem unverified")
    }

    private data class PopupLaunchResult(
        val success: Boolean,
        val step: String
    )
}
