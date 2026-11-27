package com.sbf.lightspeed.system.display

import com.sbf.lightspeed.system.lsString

import com.sbf.lightspeed.R

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.graphics.PixelFormat
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sbf.lightspeed.system.LightspeedPreferences
import com.sbf.lightspeed.system.LightspeedUsageStats
import com.sbf.lightspeed.system.OverlayLifecycleOwner
import com.sbf.lightspeed.system.TopAppCloser
import com.sbf.lightspeed.system.defaultPrefs
import com.sbf.lightspeed.LightspeedAppCategorizer
import com.sbf.lightspeed.LightspeedDataBridge
import com.sbf.lightspeed.settings.resolveCanvasPalette
import com.sbf.lightspeed.ui.theme.LightspeedStyle
import com.sbf.lightspeed.ui.theme.LightspeedTheme
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.sin

/**
 * Conscious 5-Second Delay ("Mindful Airlock") for Addictive Apps.
 *
 * Full-bleed edge-to-edge overlay covering the entire physical display (including
 * the status bar and camera punch-hole cutout area in fullscreen games).
 * Styled with the Deep Space Category Cruise starry astrogation field and a
 * responsive Portrait + Landscape layout so action buttons never clip off-screen.
 */
object LightspeedConsciousAirlockOverlay {

    private const val GRACE_PERIOD_MS = 10 * 60 * 1000L
    private const val GRACE_PERSIST_INTERVAL_MS = 15_000L
    private const val RETRIGGER_COOLDOWN_MS = 1800L
    private const val SHOW_DEBOUNCE_MS = 85L
    private const val TRANSIENT_DISMISS_DEBOUNCE_MS = 220L
    private const val TRANSIENT_DISMISS_RECHECK_MS = 350L
    private const val MAX_TRANSIENT_DISMISS_RECHECKS = 3
    private const val LOG_TAG = "LightspeedAirlock"
    private const val PREFS_AIRLOCK_RUNTIME = "lightspeed_airlock_runtime"
    private const val KEY_GRACE_PREFIX = "grace_"
    private val sessionGraceMap = ConcurrentHashMap<String, Long>()
    private val mainHandler = Handler(Looper.getMainLooper())

    private fun runtimePrefs(context: Context) =
        context.getSharedPreferences(PREFS_AIRLOCK_RUNTIME, Context.MODE_PRIVATE)

    private var abortCooldownPackage: String? = null
    private var abortCooldownTimestamp: Long = 0L
    private var lastShownTargetPkg: String? = null
    private var lastShownElapsedMs: Long = 0L
    private var activeAuthorizedSessionPkg: String? = null

    private var composeView: ComposeView? = null
    private var windowManager: WindowManager? = null
    private var lifecycleOwner: OverlayLifecycleOwner? = null
    private var pendingShowRunnable: Runnable? = null
    private var pendingDismissRunnable: Runnable? = null
    private var pendingPackage: String? = null
    private var showingPackage: String? = null

    val isShowing: Boolean
        get() = composeView != null

    /**
     * Cancels a queued transient dismiss when the gated app remains the active foreground target.
     */
    fun cancelTransientDismiss(activePkg: String? = null) {
        if (activePkg == null || activePkg == showingPackage || activePkg == pendingPackage) {
            pendingDismissRunnable?.let { mainHandler.removeCallbacks(it) }
            pendingDismissRunnable = null
        }
    }

    /**
     * Clears anti-flap memory and ends the continuous authorized session when the user
     * genuinely switches away to a different app, Launcher, or lock screen.
     */
    fun noteForegroundApp(packageName: String?) {
        if (!packageName.isNullOrBlank() && packageName != lastShownTargetPkg && packageName != showingPackage) {
            lastShownTargetPkg = null
            lastShownElapsedMs = 0L
        }
        if (activeAuthorizedSessionPkg != null && packageName != activeAuthorizedSessionPkg) {
            activeAuthorizedSessionPkg = null
        }
    }

    fun clearAuthorizedSession() {
        activeAuthorizedSessionPkg = null
    }

    /**
     * Debounced auto-dismiss used on Launcher/Recents transitions so brief system overlays or
     * in-app game update reloads never flicker Airlock off and back on repeatedly.
     *
     * Before actually dismissing, the gated app's window is re-checked: if it is still the topmost
     * app window (quickstep / recents / launcher events fire briefly while the app is opening),
     * the dismiss is skipped so the Airlock is never removed (and the app left unguarded) just
     * because a transient system window event arrived. Bounded retries keep a genuine Home /
     * Recents exit from ever leaving the Airlock stuck on screen.
     */
    fun scheduleTransientDismiss(attempt: Int = 0, source: String? = null) {
        if (composeView == null && pendingShowRunnable == null) return
        pendingDismissRunnable?.let { mainHandler.removeCallbacks(it) }
        val r = Runnable {
            pendingDismissRunnable = null
            val guardedPkg = showingPackage ?: pendingPackage
            val svc = com.sbf.lightspeed.LightspeedAccessibilityService.instance
            val topPkg = if (svc != null && guardedPkg != null) {
                try {
                    com.sbf.lightspeed.system.bot.LightspeedBotScreenScanner
                        .resolveUnderlyingAppWindow(svc)?.root?.packageName?.toString()
                } catch (_: Exception) { null }
            } else null
            if (guardedPkg != null && topPkg == guardedPkg) {
                // The gated app is still the topmost app window, so a Launcher/Recents/quickstep event
                // is just system chrome flicker (gesture handle, floating overlays, animations). The
                // Airlock belongs on screen: re-check a few times to follow animations, then keep it
                // (never dismiss while the gated app is genuinely in front).
                if (attempt < MAX_TRANSIENT_DISMISS_RECHECKS) {
                    trace("transient dismiss deferred: $guardedPkg still top app window (attempt=$attempt)")
                    scheduleTransientDismiss(attempt + 1, source)
                } else {
                    trace("transient dismiss dropped: $guardedPkg stayed top app window; keeping Airlock")
                }
            } else {
                trace("transient dismiss executing: guarded=$guardedPkg top=$topPkg attempt=$attempt source=$source")
                dismiss()
            }
        }
        pendingDismissRunnable = r
        mainHandler.postDelayed(r, if (attempt == 0) TRANSIENT_DISMISS_DEBOUNCE_MS else TRANSIENT_DISMISS_RECHECK_MS)
    }

    internal fun trace(msg: String) {
        android.util.Log.d(LOG_TAG, msg)
    }

    private fun isInteractiveAndUnlocked(context: Context): Boolean {
        val pm = context.getSystemService(Context.POWER_SERVICE) as? android.os.PowerManager
        if (pm != null && !pm.isInteractive) return false
        val km = context.getSystemService(Context.KEYGUARD_SERVICE) as? android.app.KeyguardManager
        if (km != null && km.isKeyguardLocked) return false
        return true
    }

    fun checkAndShowAirlock(service: AccessibilityService, packageName: String): Boolean {
        if (!isInteractiveAndUnlocked(service)) return false
        val prefs = service.defaultPrefs()
        if (!prefs.getBoolean(LightspeedPreferences.KEY_CONSCIOUS_AIRLOCK_ENABLED, false)) return false

        val targetApps = prefs.getStringSet(LightspeedPreferences.KEY_CONSCIOUS_DELAY_PACKAGES, emptySet()) ?: emptySet()
        if (!targetApps.contains(packageName)) {
            noteForegroundApp(packageName)
            return false
        }

        if (composeView != null && showingPackage == packageName) {
            cancelTransientDismiss(packageName)
            return false
        }

        val now = SystemClock.elapsedRealtime()
        if (packageName == abortCooldownPackage && (now - abortCooldownTimestamp) < 5000L) {
            trace("skip $packageName: abort cooldown")
            return false
        }
        if (packageName == lastShownTargetPkg && (now - lastShownElapsedMs) < RETRIGGER_COOLDOWN_MS) {
            trace("skip $packageName: retrigger cooldown")
            cancelTransientDismiss(packageName)
            return false
        }
        if (pendingShowRunnable != null && pendingPackage == packageName) {
            cancelTransientDismiss(packageName)
            return true
        }

        val persisted = runtimePrefs(service).getLong("$KEY_GRACE_PREFIX$packageName", -1L)
        val lastAllowed = sessionGraceMap[packageName] ?: persisted
        // If user stayed continuously inside this already-authorized app (e.g. exiting YouTube fullscreen after >10m),
        // refresh their grace silently instead of interrupting them mid-session.
        if (activeAuthorizedSessionPkg == packageName) {
            sessionGraceMap[packageName] = now
            if (persisted < 0L || (now - persisted) >= GRACE_PERSIST_INTERVAL_MS) {
                runtimePrefs(service).edit().putLong("$KEY_GRACE_PREFIX$packageName", now).apply()
            }
            trace("skip $packageName: continuous authorized session active")
            return false
        }
        if (lastAllowed > now) {
            // Device rebooted (elapsedRealtime reset): clear stale persisted grace timestamp
            sessionGraceMap.remove(packageName)
            runtimePrefs(service).edit().remove("$KEY_GRACE_PREFIX$packageName").apply()
        } else if (lastAllowed >= 0L && (now - lastAllowed) < GRACE_PERIOD_MS) {
            activeAuthorizedSessionPkg = packageName
            sessionGraceMap[packageName] = now
            if (persisted < 0L || (now - persisted) >= GRACE_PERSIST_INTERVAL_MS) {
                runtimePrefs(service).edit().putLong("$KEY_GRACE_PREFIX$packageName", now).apply()
            }
            trace("skip $packageName: inside 10-min grace (${(now - lastAllowed) / 1000L}s since last allowed)")
            return false
        }
        trace("scheduling Airlock for $packageName (no active grace; lastAllowed=$lastAllowed)")

        pendingShowRunnable?.let { mainHandler.removeCallbacks(it) }
        pendingDismissRunnable?.let { mainHandler.removeCallbacks(it) }
        pendingDismissRunnable = null
        pendingPackage = packageName
        val runnable = Runnable {
            pendingShowRunnable = null
            show(service, packageName)
        }
        pendingShowRunnable = runnable
        mainHandler.postDelayed(runnable, SHOW_DEBOUNCE_MS)
        return true
    }

    @Suppress("DEPRECATION")
    private fun show(service: AccessibilityService, targetPkg: String) {
        if (composeView != null) return
        if (pendingPackage != targetPkg) return
        if (!isInteractiveAndUnlocked(service)) {
            pendingPackage = null
            return
        }
        val currentFg = com.sbf.lightspeed.LightspeedAccessibilityService.lastAppPackage
        if (!currentFg.isNullOrBlank() && currentFg != targetPkg) {
            pendingPackage = null
            return
        }

        val wm = service.getSystemService(Context.WINDOW_SERVICE) as? WindowManager ?: return
        windowManager = wm

        val lifecycle = OverlayLifecycleOwner().apply {
            onCreate()
        }
        lifecycleOwner = lifecycle

        val view = ComposeView(service).apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnDetachedFromWindow)
            setContent {
                LightspeedTheme {
                    AirlockContent(
                        service = service,
                        packageName = targetPkg,
                        onProceed = {
                            val now = SystemClock.elapsedRealtime()
                            activeAuthorizedSessionPkg = targetPkg
                            sessionGraceMap[targetPkg] = now
                            runtimePrefs(service).edit().putLong("$KEY_GRACE_PREFIX$targetPkg", now).apply()
                            lastShownTargetPkg = null
                            lastShownElapsedMs = 0L
                            dismiss()
                        },
                        onAbort = {
                            if (activeAuthorizedSessionPkg == targetPkg) activeAuthorizedSessionPkg = null
                            sessionGraceMap.remove(targetPkg)
                            runtimePrefs(service).edit().remove("$KEY_GRACE_PREFIX$targetPkg").apply()
                            abortCooldownPackage = targetPkg
                            abortCooldownTimestamp = SystemClock.elapsedRealtime()
                            lastShownTargetPkg = targetPkg
                            lastShownElapsedMs = abortCooldownTimestamp
                            val abortMode = service.defaultPrefs().getString(
                                LightspeedPreferences.KEY_CONSCIOUS_AIRLOCK_ABORT_MODE,
                                LightspeedPreferences.AIRLOCK_ABORT_MODE_CLOSE_APP
                            ) ?: LightspeedPreferences.AIRLOCK_ABORT_MODE_CLOSE_APP
                            dismiss()
                            if (abortMode == LightspeedPreferences.AIRLOCK_ABORT_MODE_GO_HOME) {
                                service.performGlobalAction(AccessibilityService.GLOBAL_ACTION_HOME)
                            } else {
                                TopAppCloser.closePackageWithReturn(
                                    context = service,
                                    targetPkg = targetPkg,
                                    onFallbackHome = {
                                        service.performGlobalAction(AccessibilityService.GLOBAL_ACTION_HOME)
                                    }
                                )
                            }
                        }
                    )
                }
            }
        }
        lifecycle.attachToView(view)
        lifecycle.onStart()
        lifecycle.onResume()

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                    WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
                    WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS,
            PixelFormat.TRANSLUCENT
        ).apply {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
            }
            @Suppress("RtlHardcoded")
            gravity = Gravity.TOP or Gravity.LEFT
            x = 0
            y = 0
        }

        try {
            wm.addView(view, params)
            composeView = view
            showingPackage = targetPkg
            pendingPackage = null
            lastShownTargetPkg = targetPkg
            lastShownElapsedMs = SystemClock.elapsedRealtime()
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                view.post {
                    try {
                        view.windowInsetsController?.let { controller ->
                            controller.hide(android.view.WindowInsets.Type.statusBars() or android.view.WindowInsets.Type.navigationBars())
                            controller.systemBarsBehavior = android.view.WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                        }
                    } catch (_: Exception) {}
                }
            }
        } catch (_: Exception) {
            dismiss()
        }
    }

    fun dismiss() {
        if (composeView != null || pendingShowRunnable != null) {
            trace("dismiss() showing=$showingPackage pending=$pendingPackage")
        }
        pendingShowRunnable?.let { mainHandler.removeCallbacks(it) }
        pendingShowRunnable = null
        pendingDismissRunnable?.let { mainHandler.removeCallbacks(it) }
        pendingDismissRunnable = null
        pendingPackage = null
        val removeNow = {
            val v = composeView
            if (v != null) {
                try {
                    windowManager?.removeView(v)
                } catch (_: Exception) {}
                composeView = null
            }
            showingPackage = null
            lifecycleOwner?.onDestroy()
            lifecycleOwner = null
            windowManager = null
        }
        if (Looper.myLooper() == Looper.getMainLooper()) {
            removeNow()
        } else {
            mainHandler.post(removeNow)
        }
    }

    @Composable
    private fun AirlockContent(
        service: Context,
        packageName: String,
        onProceed: () -> Unit,
        onAbort: () -> Unit
    ) {
        val lsColors = LightspeedStyle.colors
        val colorMode = remember {
            service.defaultPrefs().getString(
                LightspeedPreferences.KEY_CONSCIOUS_AIRLOCK_COLOR_MODE,
                LightspeedPreferences.COCKPIT_COLOR_MODE_THEME_12
            ) ?: LightspeedPreferences.COCKPIT_COLOR_MODE_THEME_12
        }
        val deckSnapshot = remember { com.sbf.lightspeed.settings.DeckThemeManager.getSnapshot(service) }
        val resolvedPalette = remember(deckSnapshot, colorMode) {
            deckSnapshot.resolveCanvasPalette(service, colorMode)
        }
        val airlockAccent = Color(resolvedPalette.primaryAccentArgb)
        val airlockSecondary = Color(resolvedPalette.secondaryAccentArgb)
        val airlockBorder = Color(resolvedPalette.borderArgb)
        val airlockVoidBg = androidx.compose.ui.graphics.lerp(
            Color(0xFF04060C),
            Color(resolvedPalette.panelOuterArgb).copy(alpha = 1f),
            0.35f
        ).copy(alpha = 0.96f)
        val airlockCardBg = androidx.compose.ui.graphics.lerp(
            Color(0xFF0C1220),
            Color(resolvedPalette.panelInnerArgb).copy(alpha = 1f),
            0.55f
        ).copy(alpha = 0.92f)
        val proceedTextColor = if (airlockAccent.luminance() > 0.5f) Color.Black else Color.White
        val isLandscape = LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE
        var secondsLeft by remember { mutableIntStateOf(5) }
        val appLabel = remember(packageName) {
            try {
                val pm = service.packageManager
                val info = pm.getApplicationInfo(packageName, 0)
                pm.getApplicationLabel(info).toString()
            } catch (_: Exception) { packageName.substringAfterLast('.') }
        }

        val localeState = com.sbf.lightspeed.system.LightspeedLocaleEngine.state
        val isArabic = localeState.primary == com.sbf.lightspeed.system.LightspeedLocaleEngine.AppLanguage.AR
        val hasUsageAccess = remember { LightspeedUsageStats.hasUsageAccess(service) }
        var isTelemetryLoaded by remember(packageName) { mutableStateOf(false) }
        var flipTick by remember { mutableIntStateOf(0) }
        var isFlipPhaseActive by remember(packageName) { mutableStateOf(true) }

        val telemetryTriple by produceState(
            initialValue = Triple(lsString(R.string.airlock_other_sector), lsString(R.string.airlock_time_m_today, 0), lsString(R.string.airlock_time_m_today, 0)),
            key1 = packageName,
            key2 = hasUsageAccess,
            key3 = localeState
        ) {
            val loaded = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                val usageMap = if (hasUsageAccess) LightspeedUsageStats.usageMillisByPackage(service, days = 1) else emptyMap()
                val appMs = usageMap[packageName] ?: 0L
                val appMins = (appMs / 60000L).toInt()
                val appHrs = appMins / 60
                val appRemMins = appMins % 60
                val appFormatted = if (appHrs > 0) lsString(R.string.airlock_time_hm_today, appHrs, appRemMins) else lsString(R.string.airlock_time_m_today, appMins)

                val pm = service.packageManager
                val appInfo = try { pm.getApplicationInfo(packageName, 0) } catch (_: Exception) { null }
                val overrideCat = LightspeedPreferences.getAppCategoryOverride(service, packageName)
                val catId = if (overrideCat != null && LightspeedDataBridge.DRAWER_CATEGORIES.any { it.id == overrideCat }) {
                    overrideCat
                } else if (appInfo != null) {
                    LightspeedAppCategorizer.categorize(appInfo, packageName, appLabel)
                } else {
                    "other"
                }

                val categoryNode = LightspeedDataBridge.DRAWER_CATEGORIES.firstOrNull { it.id == catId }
                val catLabel = LightspeedDataBridge.localizedCategoryLabel(
                    catId,
                    categoryNode?.label ?: lsString(R.string.airlock_other_sector)
                )

                var sectorMs = 0L
                for ((pkg, ms) in usageMap) {
                    val pkgAppInfo = try { pm.getApplicationInfo(pkg, 0) } catch (_: Exception) { null }
                    val pkgOverride = LightspeedPreferences.getAppCategoryOverride(service, pkg)
                    val targetCat = if (pkgOverride != null && LightspeedDataBridge.DRAWER_CATEGORIES.any { it.id == pkgOverride }) {
                        pkgOverride
                    } else if (pkgAppInfo != null) {
                        val lbl = try { pm.getApplicationLabel(pkgAppInfo).toString() } catch (_: Exception) { pkg }
                        LightspeedAppCategorizer.categorize(pkgAppInfo, pkg, lbl)
                    } else {
                        "other"
                    }

                    if (targetCat == catId) {
                        sectorMs += ms
                    }
                }

                val secMins = (sectorMs / 60000L).toInt()
                val secHrs = secMins / 60
                val secRemMins = secMins % 60
                val sectorFormatted = if (secHrs > 0) lsString(R.string.airlock_time_hm_today, secHrs, secRemMins) else lsString(R.string.airlock_time_m_today, secMins)

                Triple(catLabel, appFormatted, sectorFormatted)
            }
            value = loaded
            isTelemetryLoaded = true
        }

        LaunchedEffect(packageName) {
            val startUptime = SystemClock.uptimeMillis()
            while (!isTelemetryLoaded || (SystemClock.uptimeMillis() - startUptime) < 380L) {
                kotlinx.coroutines.delay(16L)
                flipTick++
            }
            isFlipPhaseActive = false
        }

        val categoryTitle = telemetryTriple.first
        val appTimeText = if (isFlipPhaseActive && flipTick >= 0) {
            com.sbf.lightspeed.LightspeedCruiseHudPods.scrambleDigits(telemetryTriple.second, seedOffset = 1)
        } else {
            telemetryTriple.second
        }
        val sectorTimeText = if (isFlipPhaseActive && flipTick >= 0) {
            com.sbf.lightspeed.LightspeedCruiseHudPods.scrambleDigits(telemetryTriple.third, seedOffset = 2)
        } else {
            telemetryTriple.third
        }

        LaunchedEffect(Unit) {
            while (secondsLeft > 0) {
                kotlinx.coroutines.delay(1000L)
                secondsLeft -= 1
            }
        }

        val infiniteTransition = rememberInfiniteTransition(label = "starfield")
        val starPhase by infiniteTransition.animateFloat(
            initialValue = 0f,
            targetValue = 6.283185f,
            animationSpec = infiniteRepeatable(
                animation = tween(4200, easing = LinearEasing),
                repeatMode = RepeatMode.Restart
            ),
            label = "starPhase"
        )

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(airlockVoidBg),
            contentAlignment = Alignment.Center
        ) {
            // 1. Full-Bleed Category Cruise Starfield & Subtle Astrogation Nebula (Covers Cutout & Status Bar)
            Canvas(modifier = Modifier.fillMaxSize()) {
                val w = size.width
                val h = size.height
                val cx = w / 2f
                val cy = h / 2f
                val nebRadius = w.coerceAtLeast(h) * 0.45f

                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            airlockAccent.copy(alpha = 0.16f),
                            airlockSecondary.copy(alpha = 0.05f),
                            Color.Transparent
                        ),
                        center = Offset(cx, cy),
                        radius = nebRadius
                    ),
                    radius = nebRadius,
                    center = Offset(cx, cy)
                )

                val starCount = 54
                for (i in 0 until starCount) {
                    val sx = ((i * 137.5f) % w)
                    val sy = ((i * 269.3f) % h)
                    val twinkle = (sin(starPhase + i * 0.75f) * 0.35f + 0.65f).coerceIn(0.15f, 1f)
                    val r = ((i % 3) + 1.1f) * density * (0.75f + 0.25f * twinkle) / 2f
                    val starColor = when (i % 3) {
                        0 -> Color.White.copy(alpha = 0.75f * twinkle)
                        1 -> Color(0xFFC8E4FF).copy(alpha = 0.68f * twinkle)
                        else -> Color(0xFFBAE6FD).copy(alpha = 0.55f * twinkle)
                    }
                    drawCircle(color = starColor, radius = r, center = Offset(sx, sy))
                }
            }

            // 2. Responsive Astrogation Interlock Card (Landscape & Portrait friendly)
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(if (isLandscape) 10.dp else 14.dp),
                modifier = Modifier
                    .padding(horizontal = 20.dp, vertical = 12.dp)
                    .widthIn(max = if (isLandscape) 520.dp else 380.dp)
                    .fillMaxWidth(if (isLandscape) 0.82f else 0.90f)
                    .clip(RoundedCornerShape(22.dp))
                    .background(airlockCardBg)
                    .border(1.2.dp, airlockBorder.copy(alpha = 0.45f), RoundedCornerShape(22.dp))
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 22.dp, vertical = if (isLandscape) 14.dp else 22.dp)
            ) {
                Text(
                    text = lsString(R.string.airlock_banner),
                    color = airlockAccent,
                    fontWeight = FontWeight.Bold,
                    fontSize = 11.sp,
                    letterSpacing = if (isArabic) 0.sp else 1.8.sp
                )

                if (isLandscape) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = appLabel.uppercase(),
                                color = Color.White,
                                fontWeight = FontWeight.ExtraBold,
                                fontSize = 16.sp
                            )
                            if (!hasUsageAccess) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(6.dp))
                                        .background(lsColors.accentDanger.copy(alpha = 0.15f))
                                        .border(1.dp, lsColors.accentDanger.copy(alpha = 0.65f), RoundedCornerShape(6.dp))
                                        .clickable {
                                            try {
                                                val intent = Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS).apply {
                                                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                                }
                                                service.startActivity(intent)
                                            } catch (_: Exception) {}
                                        }
                                        .padding(horizontal = 8.dp, vertical = 4.dp)
                                ) {
                                    Text(
                                        text = lsString(R.string.airlock_usage_needed),
                                        color = lsColors.accentDanger,
                                        fontSize = 10.5.sp,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.weight(1f, fill = false)
                                    )
                                    Icon(
                                        imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                                        contentDescription = null,
                                        tint = lsColors.accentDanger,
                                        modifier = Modifier.size(13.dp)
                                    )
                                }
                            } else {
                                Text(
                                    text = lsString(R.string.airlock_app_time, appTimeText),
                                    color = lsColors.accentWarn,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Text(
                                    text = lsString(R.string.airlock_sector_time, categoryTitle, sectorTimeText),
                                    color = airlockAccent,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                            Text(
                                text = if (secondsLeft > 0) lsString(R.string.airlock_prompt_wait) else lsString(R.string.airlock_prompt_ready),
                                color = lsColors.textSecondary,
                                fontSize = 10.5.sp
                            )
                        }

                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(14.dp))
                                .background(Color.White.copy(alpha = 0.06f))
                                .border(1.dp, airlockBorder.copy(alpha = 0.55f), RoundedCornerShape(14.dp))
                                .padding(horizontal = 18.dp, vertical = 10.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = if (secondsLeft > 0) "0${secondsLeft}s" else lsString(R.string.airlock_ready),
                                color = airlockAccent,
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                } else {
                    Text(
                        text = appLabel.uppercase(),
                        color = Color.White,
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 18.sp,
                        textAlign = TextAlign.Center
                    )

                    if (!hasUsageAccess) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center,
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(lsColors.accentDanger.copy(alpha = 0.15f))
                                .border(1.dp, lsColors.accentDanger.copy(alpha = 0.65f), RoundedCornerShape(6.dp))
                                .clickable {
                                    try {
                                        val intent = Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS).apply {
                                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                        }
                                        service.startActivity(intent)
                                    } catch (_: Exception) {}
                                }
                                .padding(horizontal = 10.dp, vertical = 5.dp)
                        ) {
                            Text(
                                text = lsString(R.string.airlock_usage_needed),
                                color = lsColors.accentDanger,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                                contentDescription = null,
                                tint = lsColors.accentDanger,
                                modifier = Modifier.size(14.dp)
                            )
                        }
                    } else {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(2.dp)
                        ) {
                            Text(
                                text = lsString(R.string.airlock_app_time, appTimeText),
                                color = lsColors.accentWarn,
                                fontSize = 11.5.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                text = lsString(R.string.airlock_sector_time, categoryTitle, sectorTimeText),
                                color = airlockAccent,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }

                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(16.dp))
                            .background(Color.White.copy(alpha = 0.06f))
                            .border(1.2.dp, airlockBorder.copy(alpha = 0.55f), RoundedCornerShape(16.dp))
                            .padding(horizontal = 28.dp, vertical = 14.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = if (secondsLeft > 0) "✦  0${secondsLeft}s  ✦" else lsString(R.string.airlock_ready_wide),
                            color = airlockAccent,
                            fontSize = 22.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = if (isArabic) 0.sp else 1.5.sp
                        )
                    }

                    Text(
                        text = if (secondsLeft > 0) lsString(R.string.airlock_prompt_wait) else lsString(R.string.airlock_prompt_ready),
                        color = lsColors.textSecondary,
                        fontSize = 11.sp,
                        textAlign = TextAlign.Center
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Button(
                        onClick = onAbort,
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0x33FF5252)),
                        modifier = Modifier.weight(1f).height(40.dp)
                    ) {
                        Text(lsString(R.string.airlock_abort), color = Color(0xFFFF5252), fontSize = 11.5.sp, fontWeight = FontWeight.Bold)
                    }

                    Button(
                        onClick = onProceed,
                        enabled = secondsLeft == 0,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = airlockAccent,
                            disabledContainerColor = Color.White.copy(alpha = 0.08f)
                        ),
                        modifier = Modifier.weight(1f).height(40.dp)
                    ) {
                        Text(
                            text = if (secondsLeft > 0) lsString(R.string.airlock_wait_btn, secondsLeft) else lsString(R.string.airlock_proceed_btn),
                            color = if (secondsLeft > 0) Color.Gray else proceedTextColor,
                            fontSize = 11.5.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }
}
