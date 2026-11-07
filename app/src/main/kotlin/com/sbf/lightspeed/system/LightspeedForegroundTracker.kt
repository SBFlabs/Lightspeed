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

    @Volatile
    var currentActivityClass: String? = null
        internal set

    fun isRealComponentClass(className: String): Boolean {
        if (className.isBlank()) return false
        if (className.startsWith("android.widget.") ||
            className.startsWith("android.view.") ||
            className.startsWith("com.android.internal.") ||
            className.startsWith("android.inputmethodservice.")
        ) return false
        if (className.startsWith("com.sbf.lightspeed")) return false
        if (className.contains("FrameLayout") ||
            className.contains("LinearLayout") ||
            className.contains("RelativeLayout") ||
            className.contains("DecorView") ||
            className.contains("PopupWindow") ||
            className.contains("Popup")
        ) return false
        return true
    }

    fun resolveActiveWindowId(service: android.accessibilityservice.AccessibilityService?): String =
        com.sbf.lightspeed.system.bot.LightspeedBotScreenScanner.resolveActiveWindowId(service)

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
            currentActivityClass = null
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
            val underlyingWin = com.sbf.lightspeed.system.bot.LightspeedBotScreenScanner.resolveUnderlyingAppWindow(service)
            val underlyingPkg = underlyingWin?.root?.packageName?.toString()
            val isOverlayEvent = !underlyingPkg.isNullOrBlank() && underlyingPkg != currentPkg && !event.isFullScreen
            val effectivePkg = if (isOverlayEvent) underlyingPkg else currentPkg

            trackForegroundPackage(service, effectivePkg)
            val fgPkg = LightspeedAccessibilityService.lastAppPackage
            val isNotSysOrLauncher = effectivePkg != service.packageName &&
                    effectivePkg != "com.android.systemui" &&
                    !isInputMethodPackage(service, effectivePkg) &&
                    !getLauncherPackagesCached(service).contains(effectivePkg)
            if (isNotSysOrLauncher && (fgPkg == null || effectivePkg == fgPkg) && !isOverlayEvent && isRealComponentClass(className)) {
                currentActivityClass = className
            }
            LightspeedSmartPixelsOverlay.onForegroundPackageChanged(effectivePkg)
            LightspeedConsciousAirlockOverlay.checkAndShowAirlock(service, effectivePkg)
            com.sbf.lightspeed.system.bot.LightspeedBotTrackpadOverlay.refreshMacroButtonsState()
        }

        if (com.sbf.lightspeed.system.bot.LightspeedBotRecorderOverlay.isShowing) {
            com.sbf.lightspeed.system.bot.LightspeedBotRecorderOverlay.onWindowStateChanged(service)
        }
    }
}
