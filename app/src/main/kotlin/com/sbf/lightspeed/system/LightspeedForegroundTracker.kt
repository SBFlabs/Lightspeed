package com.sbf.lightspeed.system

import android.app.KeyguardManager
import android.content.Context
import android.content.Intent
import android.os.SystemClock
import android.view.accessibility.AccessibilityEvent
import android.view.inputmethod.InputMethodManager
import com.sbf.lightspeed.LightspeedAccessibilityService
import com.sbf.lightspeed.system.display.LightspeedConsciousAirlockOverlay
import com.sbf.lightspeed.system.display.LightspeedSmartPixelsOverlay

object LightspeedForegroundTracker {
    @Volatile
    private var cachedLauncherPackages: Set<String> = emptySet()
    @Volatile
    private var lastLauncherPackagesQueryTimeMs: Long = 0L

    fun getLauncherPackagesCached(context: Context): Set<String> {
        val now = SystemClock.uptimeMillis()
        if (now - lastLauncherPackagesQueryTimeMs > 60_000L || cachedLauncherPackages.isEmpty()) {
            val intent = Intent(Intent.ACTION_MAIN).apply { addCategory(Intent.CATEGORY_HOME) }
            val resolveInfos = try {
                context.packageManager.queryIntentActivities(intent, 0)
            } catch (_: Exception) {
                emptyList()
            }
            cachedLauncherPackages = resolveInfos.mapNotNull { it.activityInfo?.packageName }.toSet()
            lastLauncherPackagesQueryTimeMs = now
        }
        return cachedLauncherPackages
    }

    fun isInputMethodPackage(context: Context, pkg: String): Boolean {
        return try {
            val imm = context.getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
            imm?.enabledInputMethodList?.any { it.packageName == pkg } == true
        } catch (_: Exception) {
            false
        }
    }

    fun trackForegroundPackage(context: Context, pkg: String) {
        if (pkg == context.packageName || pkg == "com.android.systemui") return
        if (isInputMethodPackage(context, pkg)) return

        val launcherPkgs = getLauncherPackagesCached(context)
        if (launcherPkgs.contains(pkg)) {
            LightspeedAccessibilityService.lastAppPackage = null
            LightspeedAccessibilityService.lastAppUptimeMs = SystemClock.uptimeMillis()
        } else {
            LightspeedAccessibilityService.lastAppPackage = pkg
            LightspeedAccessibilityService.lastAppUptimeMs = SystemClock.uptimeMillis()
        }
    }

    fun handleWindowStateChanged(service: LightspeedAccessibilityService, event: AccessibilityEvent) {
        val currentPkg = event.packageName?.toString()
        val keyguardManager = service.getSystemService(Context.KEYGUARD_SERVICE) as? KeyguardManager
        val isLocked = keyguardManager?.isKeyguardLocked == true

        val className = event.className?.toString() ?: ""
        val isSysUi = currentPkg != null && (currentPkg == "com.android.systemui" || currentPkg.contains("systemui"))
        val isRecents = className.contains("recents", ignoreCase = true) || className.contains("overview", ignoreCase = true) || className.contains("quickstep", ignoreCase = true)
        service.isNotificationShadeActive = (isSysUi && !isLocked && !isRecents)

        service.updateOverlaysVisibility(isLocked, currentPkg)

        val isFullScreen = event.isFullScreen
        val isLauncher = LightspeedOrientationEngine.isLauncherPackage(service, currentPkg)
        val isLikelyActivity = isFullScreen || isLauncher || (className.endsWith("Activity") || className.endsWith("Launcher") || className.contains("Workspace") || className.contains("Launcher") || className.contains("quickstep", ignoreCase = true))
        if (isLikelyActivity) {
            LightspeedOrientationManager.evaluateContextGuardrails(service, isLocked, currentPkg)
        }

        if (!currentPkg.isNullOrEmpty()) {
            trackForegroundPackage(service, currentPkg)
            LightspeedSmartPixelsOverlay.onForegroundPackageChanged(currentPkg)
            LightspeedConsciousAirlockOverlay.checkAndShowAirlock(service, currentPkg)
        }
    }
}
