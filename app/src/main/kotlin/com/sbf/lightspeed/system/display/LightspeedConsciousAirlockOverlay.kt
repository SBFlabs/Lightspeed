package com.sbf.lightspeed.system.display

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.content.res.Configuration
import android.graphics.PixelFormat
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
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
import com.sbf.lightspeed.system.defaultPrefs
import com.sbf.lightspeed.LightspeedAppCategorizer
import com.sbf.lightspeed.LightspeedDataBridge
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
    private val sessionGraceMap = ConcurrentHashMap<String, Long>()
    private val mainHandler = Handler(Looper.getMainLooper())

    private var composeView: ComposeView? = null
    private var windowManager: WindowManager? = null
    private var lifecycleOwner: OverlayLifecycleOwner? = null

    fun checkAndShowAirlock(service: AccessibilityService, packageName: String): Boolean {
        val prefs = service.defaultPrefs()
        if (!prefs.getBoolean(LightspeedPreferences.KEY_CONSCIOUS_AIRLOCK_ENABLED, false)) return false

        val targetApps = prefs.getStringSet(LightspeedPreferences.KEY_CONSCIOUS_DELAY_PACKAGES, emptySet()) ?: emptySet()
        if (!targetApps.contains(packageName)) return false

        val lastAllowed = sessionGraceMap[packageName] ?: 0L
        if (SystemClock.elapsedRealtime() - lastAllowed < GRACE_PERIOD_MS) {
            return false
        }

        mainHandler.post {
            show(service, packageName)
        }
        return true
    }

    @Suppress("DEPRECATION")
    private fun show(service: AccessibilityService, targetPkg: String) {
        if (composeView != null) return

        val wm = service.getSystemService(Context.WINDOW_SERVICE) as? WindowManager ?: return
        windowManager = wm

        val lifecycle = OverlayLifecycleOwner().apply {
            onCreate()
        }
        lifecycleOwner = lifecycle

        val view = ComposeView(service).apply {
            systemUiVisibility = (View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                    or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                    or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                    or View.SYSTEM_UI_FLAG_FULLSCREEN)
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnDetachedFromWindow)
            setContent {
                LightspeedTheme {
                    AirlockContent(
                        service = service,
                        packageName = targetPkg,
                        onProceed = {
                            sessionGraceMap[targetPkg] = SystemClock.elapsedRealtime()
                            dismiss()
                        },
                        onAbort = {
                            service.performGlobalAction(AccessibilityService.GLOBAL_ACTION_HOME)
                            dismiss()
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
            gravity = Gravity.TOP or Gravity.START
            x = 0
            y = 0
        }

        try {
            wm.addView(view, params)
            composeView = view
        } catch (_: Exception) {}
    }

    fun dismiss() {
        mainHandler.post {
            val v = composeView ?: return@post
            try {
                windowManager?.removeView(v)
            } catch (_: Exception) {}
            composeView = null
            lifecycleOwner?.onDestroy()
            lifecycleOwner = null
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
        val isLandscape = LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE
        var secondsLeft by remember { mutableIntStateOf(5) }
        val appLabel = remember {
            try {
                val pm = service.packageManager
                val info = pm.getApplicationInfo(packageName, 0)
                pm.getApplicationLabel(info).toString()
            } catch (_: Exception) { packageName.substringAfterLast('.') }
        }

        val (categoryTitle, appTimeText, sectorTimeText) = remember {
            val usageMap = LightspeedUsageStats.usageMillisByPackage(service, days = 1)
            val appMs = usageMap[packageName] ?: 0L
            val appMins = (appMs / 60000L).toInt()
            val appHrs = appMins / 60
            val appRemMins = appMins % 60
            val appFormatted = if (appHrs > 0) "${appHrs}h ${appRemMins}m today" else "${appMins}m today"

            // Resolve Category (Sector)
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
            val catLabel = categoryNode?.label ?: "Other"

            // Compute total time across all packages in this sector
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
            val sectorFormatted = if (secHrs > 0) "${secHrs}h ${secRemMins}m today" else "${secMins}m today"

            Triple(catLabel, appFormatted, sectorFormatted)
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
                .background(Color(0xF604060C)),
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
                            lsColors.accentInfo.copy(alpha = 0.16f),
                            lsColors.accentInfo.copy(alpha = 0.05f),
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
                    .background(Color(0xE80C1220))
                    .border(1.2.dp, lsColors.accentInfo.copy(alpha = 0.45f), RoundedCornerShape(22.dp))
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 22.dp, vertical = if (isLandscape) 14.dp else 22.dp)
            ) {
                Text(
                    text = "◈ ASTROGATION AIRLOCK ◈",
                    color = lsColors.accentInfo,
                    fontWeight = FontWeight.Bold,
                    fontSize = 11.sp,
                    letterSpacing = 1.8.sp
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
                            Text(
                                text = "App Time: $appTimeText",
                                color = lsColors.accentWarn,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                text = "Sector Time ($categoryTitle): $sectorTimeText",
                                color = lsColors.accentInfo,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                text = if (secondsLeft > 0) "Take a conscious breath... Are you sure you want to open this app?" else "Mindful pause complete. Ready to engage.",
                                color = lsColors.textSecondary,
                                fontSize = 10.5.sp
                            )
                        }

                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(14.dp))
                                .background(Color.White.copy(alpha = 0.06f))
                                .border(1.dp, lsColors.accentInfo.copy(alpha = 0.55f), RoundedCornerShape(14.dp))
                                .padding(horizontal = 18.dp, vertical = 10.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = if (secondsLeft > 0) "0${secondsLeft}s" else "✦ READY",
                                color = lsColors.accentInfo,
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

                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        Text(
                            text = "App Time: $appTimeText",
                            color = lsColors.accentWarn,
                            fontSize = 11.5.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = "Sector Time ($categoryTitle): $sectorTimeText",
                            color = lsColors.accentInfo,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }

                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(16.dp))
                            .background(Color.White.copy(alpha = 0.06f))
                            .border(1.2.dp, lsColors.accentInfo.copy(alpha = 0.55f), RoundedCornerShape(16.dp))
                            .padding(horizontal = 28.dp, vertical = 14.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = if (secondsLeft > 0) "✦  0${secondsLeft}s  ✦" else "✦  READY  ✦",
                            color = lsColors.accentInfo,
                            fontSize = 22.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.5.sp
                        )
                    }

                    Text(
                        text = if (secondsLeft > 0) "Take a conscious breath... Are you sure you want to open this app?" else "Mindful pause complete. Ready to engage.",
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
                        Text("✕ Abort", color = Color(0xFFFF5252), fontSize = 11.5.sp, fontWeight = FontWeight.Bold)
                    }

                    Button(
                        onClick = onProceed,
                        enabled = secondsLeft == 0,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = lsColors.accentInfo,
                            disabledContainerColor = Color.White.copy(alpha = 0.08f)
                        ),
                        modifier = Modifier.weight(1f).height(40.dp)
                    ) {
                        Text(
                            text = if (secondsLeft > 0) "Wait (${secondsLeft}s)" else "✦ Proceed",
                            color = if (secondsLeft > 0) Color.Gray else Color.Black,
                            fontSize = 11.5.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }
}
