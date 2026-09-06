package com.sbf.lightspeed.settings

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.Security
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.drawable.toBitmap
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.sbf.lightspeed.LightspeedAccessibilityService
import com.sbf.lightspeed.system.ElevatedTaskCloser
import com.sbf.lightspeed.system.LightspeedHapticEngine
import com.sbf.lightspeed.system.LightspeedPreferences
import com.sbf.lightspeed.system.LightspeedWatchdogEngine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun WatchdogDefenseDeck(
    context: Context,
    prefs: SharedPreferences,
    onRefreshNeeded: () -> Unit
) {
    val coroutineScope = rememberCoroutineScope()
    val cautionAmber = Color(0xFFFFB300)
    val successGreen = Color(0xFF00E676)
    val alertRed = Color(0xFFFF3D00)

    val isServiceRunning = LightspeedAccessibilityService.instance != null
    val isShizukuActive = ElevatedTaskCloser.isShizukuActive
    val lifecycleOwner = LocalLifecycleOwner.current

    var isBatteryIgnored by remember {
        mutableStateOf(LightspeedWatchdogEngine.isBatteryOptimizationIgnored(context))
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                isBatteryIgnored = LightspeedWatchdogEngine.isBatteryOptimizationIgnored(context)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    var isCoreWatchdogExpanded by rememberSaveable {
        mutableStateOf(prefs.getBoolean("pref_sub_core_watchdog_defense", true))
    }
    var isPerimeterWatchdogExpanded by rememberSaveable {
        mutableStateOf(prefs.getBoolean("pref_sub_perimeter_watchdog_defense", true))
    }

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // =========================================================================
        // SUB-SECTION 1: CORE WATCHDOG
        // =========================================================================
        CollapsibleSubSection(
            title = "Core Watchdog",
            subtitle = "Monitors, protects, and autonomously revives Lightspeed via Shizuku.",
            icon = {
                Icon(
                    imageVector = Icons.Outlined.Shield,
                    contentDescription = null,
                    tint = cautionAmber,
                    modifier = Modifier.size(18.dp)
                )
            },
            trailingBadge = {
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = if (isServiceRunning) successGreen.copy(alpha = 0.18f) else alertRed.copy(alpha = 0.18f),
                    border = BorderStroke(1.dp, if (isServiceRunning) successGreen.copy(alpha = 0.5f) else alertRed.copy(alpha = 0.5f))
                ) {
                    Text(
                        text = if (isServiceRunning) "ONLINE" else "OFFLINE",
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (isServiceRunning) successGreen else alertRed,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
            },
            isExpanded = isCoreWatchdogExpanded,
            onToggle = {
                isCoreWatchdogExpanded = !isCoreWatchdogExpanded
                prefs.edit().putBoolean("pref_sub_core_watchdog_defense", isCoreWatchdogExpanded).apply()
            }
        ) {
            // Live Status Pills Card
            Card(
                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)),
                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.08f))
            ) {
                Column(modifier = Modifier.fillMaxWidth().padding(10.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            "SYSTEM DEFENSE TELEMETRY",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.Gray,
                            letterSpacing = 0.6.sp
                        )
                        Text(
                            text = if (isServiceRunning && isShizukuActive) "PROTECTED" else "ATTENTION NEEDED",
                            fontSize = 9.5.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (isServiceRunning && isShizukuActive) successGreen else cautionAmber
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Min),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        // Service Pill
                        Surface(
                            modifier = Modifier.weight(1f).fillMaxHeight(),
                            shape = RoundedCornerShape(8.dp),
                            color = if (isServiceRunning) successGreen.copy(alpha = 0.12f) else alertRed.copy(alpha = 0.15f),
                            border = BorderStroke(1.dp, if (isServiceRunning) successGreen.copy(alpha = 0.35f) else alertRed.copy(alpha = 0.35f))
                        ) {
                            Column(
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                                verticalArrangement = Arrangement.Center
                            ) {
                                Text("SERVICE", fontSize = 8.5.sp, color = Color.LightGray, fontWeight = FontWeight.SemiBold, maxLines = 1, softWrap = false)
                                Text(
                                    text = if (isServiceRunning) "ONLINE" else "OFFLINE",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (isServiceRunning) successGreen else alertRed,
                                    maxLines = 1,
                                    softWrap = false
                                )
                            }
                        }

                        // Shizuku Pill
                        Surface(
                            modifier = Modifier.weight(1f).fillMaxHeight(),
                            shape = RoundedCornerShape(8.dp),
                            color = if (isShizukuActive) successGreen.copy(alpha = 0.12f) else cautionAmber.copy(alpha = 0.15f),
                            border = BorderStroke(1.dp, if (isShizukuActive) successGreen.copy(alpha = 0.35f) else cautionAmber.copy(alpha = 0.35f))
                        ) {
                            Column(
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                                verticalArrangement = Arrangement.Center
                            ) {
                                Text("SHIZUKU", fontSize = 8.5.sp, color = Color.LightGray, fontWeight = FontWeight.SemiBold, maxLines = 1, softWrap = false)
                                Text(
                                    text = if (isShizukuActive) "ACTIVE" else "STANDBY",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (isShizukuActive) successGreen else cautionAmber,
                                    maxLines = 1,
                                    softWrap = false
                                )
                            }
                        }

                        // Battery Pill (1-Tap Whitelist)
                        Surface(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight()
                                .clickable {
                                    if (isBatteryIgnored) {
                                        Toast.makeText(context, "Battery optimization already disabled", Toast.LENGTH_SHORT).show()
                                    } else {
                                        coroutineScope.launch(Dispatchers.IO) {
                                            val ok = LightspeedWatchdogEngine.whitelistBattery(context, context.packageName)
                                            withContext(Dispatchers.Main) {
                                                if (ok) {
                                                    isBatteryIgnored = true
                                                    LightspeedHapticEngine.tick(context)
                                                    Toast.makeText(context, "Battery whitelist granted via Shizuku", Toast.LENGTH_SHORT).show()
                                                    onRefreshNeeded()
                                                } else {
                                                    LightspeedWatchdogEngine.requestIgnoreBatteryOptimization(context)
                                                }
                                            }
                                        }
                                    }
                                },
                            shape = RoundedCornerShape(8.dp),
                            color = if (isBatteryIgnored) successGreen.copy(alpha = 0.12f) else cautionAmber.copy(alpha = 0.15f),
                            border = BorderStroke(1.dp, if (isBatteryIgnored) successGreen.copy(alpha = 0.35f) else cautionAmber.copy(alpha = 0.35f))
                        ) {
                            Column(
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                                verticalArrangement = Arrangement.Center
                            ) {
                                Text("BATTERY", fontSize = 8.5.sp, color = Color.LightGray, fontWeight = FontWeight.SemiBold, maxLines = 1, softWrap = false)
                                Text(
                                    text = if (isBatteryIgnored) "EXEMPT" else "LIMITED",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (isBatteryIgnored) successGreen else cautionAmber,
                                    maxLines = 1,
                                    softWrap = false
                                )
                            }
                        }
                    }
                }
            }

            // Sentinel Toggles
            var sentinelEnabled by rememberSaveable {
                mutableStateOf(prefs.getBoolean(LightspeedPreferences.KEY_ACCESSIBILITY_SENTINEL_ENABLED, false))
            }
            var crashSentinelEnabled by rememberSaveable {
                mutableStateOf(prefs.getBoolean(LightspeedPreferences.KEY_CRASH_SENTINEL_ENABLED, true))
            }

            PrefToggleRow(
                title = "Proactive OEM Sentinel",
                subtitle = "Background sentinel thread polls Lightspeed and protected sentinels every 20s, reviving via Shizuku if terminated by OEM battery policies.",
                isChecked = sentinelEnabled,
                onCheckedChange = { checked ->
                    sentinelEnabled = checked
                    prefs.edit().putBoolean(LightspeedPreferences.KEY_ACCESSIBILITY_SENTINEL_ENABLED, checked).apply()
                    if (checked) {
                        LightspeedWatchdogEngine.initSentinel(context)
                    } else {
                        LightspeedWatchdogEngine.stopSentinel()
                    }
                    onRefreshNeeded()
                }
            )

            PrefToggleRow(
                title = "Autonomous Crash Resuscitation",
                subtitle = "Catches fatal JVM or shader exceptions, logs telemetry, and arms an OS alarm to resuscitate the service via Shizuku within 1s.",
                isChecked = crashSentinelEnabled,
                onCheckedChange = { checked ->
                    crashSentinelEnabled = checked
                    prefs.edit().putBoolean(LightspeedPreferences.KEY_CRASH_SENTINEL_ENABLED, checked).apply()
                    onRefreshNeeded()
                }
            )

            // Equalized 36.dp Action Buttons Row
            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(
                    onClick = {
                        coroutineScope.launch(Dispatchers.IO) {
                            val ok = LightspeedWatchdogEngine.reviveAccessibilityService(context)
                            withContext(Dispatchers.Main) {
                                LightspeedHapticEngine.heavyClick(context)
                                Toast.makeText(context, if (ok) "Core Watchdog pulse sent via Shizuku" else "Shizuku or Root required for revival", Toast.LENGTH_SHORT).show()
                                onRefreshNeeded()
                            }
                        }
                    },
                    modifier = Modifier.weight(1f).height(36.dp),
                    shape = RoundedCornerShape(10.dp),
                    border = BorderStroke(1.dp, cautionAmber.copy(alpha = 0.6f)),
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
                        Icon(Icons.Default.HealthAndSafety, contentDescription = null, tint = cautionAmber, modifier = Modifier.size(15.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Trigger Pulse", fontWeight = FontWeight.Bold, fontSize = 11.sp, color = cautionAmber, maxLines = 1, softWrap = false)
                    }
                }

                OutlinedButton(
                    onClick = {
                        if (isBatteryIgnored) {
                            Toast.makeText(context, "Battery optimization already disabled", Toast.LENGTH_SHORT).show()
                        } else {
                            coroutineScope.launch(Dispatchers.IO) {
                                val ok = LightspeedWatchdogEngine.whitelistBattery(context, context.packageName)
                                withContext(Dispatchers.Main) {
                                    if (ok) {
                                        isBatteryIgnored = true
                                        LightspeedHapticEngine.tick(context)
                                        Toast.makeText(context, "Battery whitelist granted via Shizuku", Toast.LENGTH_SHORT).show()
                                        onRefreshNeeded()
                                    } else {
                                        LightspeedWatchdogEngine.requestIgnoreBatteryOptimization(context)
                                    }
                                }
                            }
                        }
                    },
                    modifier = Modifier.weight(1f).height(36.dp),
                    shape = RoundedCornerShape(10.dp),
                    border = BorderStroke(1.dp, if (isBatteryIgnored) successGreen.copy(alpha = 0.45f) else cautionAmber.copy(alpha = 0.6f)),
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
                        Icon(
                            imageVector = if (isBatteryIgnored) Icons.Default.CheckCircle else Icons.Default.BatteryChargingFull,
                            contentDescription = null,
                            tint = if (isBatteryIgnored) successGreen else cautionAmber,
                            modifier = Modifier.size(15.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = if (isBatteryIgnored) "Battery: OK" else "Fix Battery",
                            fontWeight = FontWeight.Bold,
                            fontSize = 11.sp,
                            color = if (isBatteryIgnored) successGreen else cautionAmber,
                            maxLines = 1,
                            softWrap = false
                        )
                    }
                }
            }

            // Flight Recorder / Anomaly Telemetry Card
            val lastCrashTimestamp = prefs.getLong("key_last_crash_timestamp", 0L)
            val lastCrashMessage = prefs.getString("key_last_crash_message", null)
            val lastCrashStack = prefs.getString("key_last_crash_stack", null)
            var showFullCrashStack by rememberSaveable { mutableStateOf(false) }

            Card(
                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(
                    containerColor = if (lastCrashTimestamp > 0L && !lastCrashMessage.isNullOrBlank()) {
                        cautionAmber.copy(alpha = 0.1f)
                    } else {
                        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f)
                    }
                ),
                border = BorderStroke(
                    1.dp,
                    if (lastCrashTimestamp > 0L && !lastCrashMessage.isNullOrBlank()) {
                        cautionAmber.copy(alpha = 0.35f)
                    } else {
                        Color.White.copy(alpha = 0.08f)
                    }
                )
            ) {
                Column(modifier = Modifier.fillMaxWidth().padding(10.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = if (lastCrashTimestamp > 0L && !lastCrashMessage.isNullOrBlank()) Icons.Default.Warning else Icons.Default.CheckCircle,
                                contentDescription = null,
                                tint = if (lastCrashTimestamp > 0L && !lastCrashMessage.isNullOrBlank()) cautionAmber else successGreen,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                "FLIGHT RECORDER TELEMETRY",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (lastCrashTimestamp > 0L && !lastCrashMessage.isNullOrBlank()) cautionAmber else successGreen
                            )
                        }

                        if (lastCrashTimestamp > 0L && !lastCrashMessage.isNullOrBlank()) {
                            Text(
                                text = "Clear Log",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.LightGray,
                                modifier = Modifier.clickable {
                                    LightspeedWatchdogEngine.clearCrashTelemetry(context)
                                    onRefreshNeeded()
                                }
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(6.dp))

                    if (lastCrashTimestamp > 0L && !lastCrashMessage.isNullOrBlank()) {
                        val formattedDate = remember(lastCrashTimestamp) {
                            SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date(lastCrashTimestamp))
                        }
                        Text(
                            text = "Last Anomaly: $lastCrashMessage",
                            fontSize = 11.5.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = Color.White
                        )
                        Text(
                            text = "Recorded at $formattedDate",
                            fontSize = 10.sp,
                            color = Color.Gray
                        )

                        if (!lastCrashStack.isNullOrBlank()) {
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = if (showFullCrashStack) "Hide Stacktrace ▲" else "View Stacktrace ▼",
                                fontSize = 10.5.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.clickable {
                                    showFullCrashStack = !showFullCrashStack
                                }
                            )

                            if (showFullCrashStack) {
                                Surface(
                                    modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
                                    shape = RoundedCornerShape(6.dp),
                                    color = Color.Black.copy(alpha = 0.5f),
                                    border = BorderStroke(0.5.dp, Color.White.copy(alpha = 0.15f))
                                ) {
                                    Text(
                                        text = lastCrashStack.take(1500),
                                        fontSize = 9.sp,
                                        fontFamily = FontFamily.Monospace,
                                        color = Color.LightGray,
                                        modifier = Modifier.padding(6.dp)
                                    )
                                }
                            }
                        }
                    } else {
                        Text(
                            text = "Nominal. Zero crash events or unhandled exceptions recorded.",
                            fontSize = 11.sp,
                            color = Color.LightGray.copy(alpha = 0.7f)
                        )
                    }
                }
            }
        }

        // =========================================================================
        // SUB-SECTION 2: PERIMETER WATCHDOG
        // =========================================================================
        val a11yManager = remember { context.getSystemService(Context.ACCESSIBILITY_SERVICE) as? android.view.accessibility.AccessibilityManager }
        val pm = context.packageManager

        var refreshTrigger by remember { mutableIntStateOf(0) }

        val installedA11y = remember(refreshTrigger) {
            try {
                a11yManager?.installedAccessibilityServiceList ?: emptyList()
            } catch (_: Exception) {
                emptyList()
            }
        }

        val enabledComponents = remember(refreshTrigger) {
            LightspeedWatchdogEngine.getEnabledAccessibilityServices(context)
        }

        var protectedServicesSet by remember {
            mutableStateOf(LightspeedPreferences.getPerimeterProtectedServices(context))
        }

        val thirdPartyServices = remember(installedA11y) {
            installedA11y.filter { sInfo ->
                val sPkg = sInfo.resolveInfo?.serviceInfo?.packageName ?: sInfo.id.substringBefore("/")
                sPkg != context.packageName
            }
        }

        val activeCount = remember(thirdPartyServices, enabledComponents) {
            thirdPartyServices.count { sInfo ->
                val sPkg = sInfo.resolveInfo?.serviceInfo?.packageName ?: sInfo.id.substringBefore("/")
                val sCls = sInfo.resolveInfo?.serviceInfo?.name ?: sInfo.id.substringAfter("/")
                enabledComponents.contains(ComponentName(sPkg, sCls))
            }
        }

        CollapsibleSubSection(
            title = "Perimeter Watchdog",
            subtitle = "Universal accessibility manager and autonomous sentinels with sub-100ms privileged toggles.",
            icon = {
                Icon(
                    imageVector = Icons.Outlined.Security,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(18.dp)
                )
            },
            trailingBadge = {
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.18f),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.5f))
                ) {
                    Text(
                        text = "$activeCount ACTIVE",
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
            },
            isExpanded = isPerimeterWatchdogExpanded,
            onToggle = {
                isPerimeterWatchdogExpanded = !isPerimeterWatchdogExpanded
                prefs.edit().putBoolean("pref_sub_perimeter_watchdog_defense", isPerimeterWatchdogExpanded).apply()
            }
        ) {
            // Shizuku Bridge Status Banner
            Card(
                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(
                    containerColor = if (isShizukuActive) successGreen.copy(alpha = 0.12f) else cautionAmber.copy(alpha = 0.12f)
                ),
                border = BorderStroke(
                    1.dp,
                    if (isShizukuActive) successGreen.copy(alpha = 0.4f) else cautionAmber.copy(alpha = 0.4f)
                )
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = if (isShizukuActive) Icons.Default.CheckCircle else Icons.Default.Info,
                        contentDescription = null,
                        tint = if (isShizukuActive) successGreen else cautionAmber,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Column {
                        Text(
                            text = if (isShizukuActive) "SHIZUKU PRIVILEGED BRIDGE: ACTIVE" else "SHIZUKU PRIVILEGED BRIDGE: STANDBY",
                            fontWeight = FontWeight.Bold,
                            fontSize = 11.sp,
                            color = if (isShizukuActive) successGreen else cautionAmber
                        )
                        Text(
                            text = if (isShizukuActive) "Sub-100ms instant toggles armed without opening system settings" else "Shizuku standby. Toggles will launch system settings page",
                            fontSize = 10.sp,
                            color = Color.LightGray.copy(alpha = 0.75f)
                        )
                    }
                }
            }

            // Summary Telemetry & Master Sentinel Pulse Row
            Card(
                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)),
                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.08f))
            ) {
                Column(modifier = Modifier.fillMaxWidth().padding(10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Min),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Surface(
                            modifier = Modifier.weight(1f).fillMaxHeight(),
                            shape = RoundedCornerShape(8.dp),
                            color = Color.White.copy(alpha = 0.05f),
                            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.12f))
                        ) {
                            Column(modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp), verticalArrangement = Arrangement.Center) {
                                Text("INSTALLED", fontSize = 8.sp, color = Color.LightGray, fontWeight = FontWeight.SemiBold, maxLines = 1)
                                Text("${thirdPartyServices.size}", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color.White, maxLines = 1)
                            }
                        }

                        Surface(
                            modifier = Modifier.weight(1f).fillMaxHeight(),
                            shape = RoundedCornerShape(8.dp),
                            color = successGreen.copy(alpha = 0.12f),
                            border = BorderStroke(1.dp, successGreen.copy(alpha = 0.35f))
                        ) {
                            Column(modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp), verticalArrangement = Arrangement.Center) {
                                Text("ACTIVE", fontSize = 8.sp, color = Color.LightGray, fontWeight = FontWeight.SemiBold, maxLines = 1)
                                Text("$activeCount", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = successGreen, maxLines = 1)
                            }
                        }

                        Surface(
                            modifier = Modifier.weight(1f).fillMaxHeight(),
                            shape = RoundedCornerShape(8.dp),
                            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.35f))
                        ) {
                            Column(modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp), verticalArrangement = Arrangement.Center) {
                                Text("SHIELDED", fontSize = 8.sp, color = Color.LightGray, fontWeight = FontWeight.SemiBold, maxLines = 1)
                                Text("${protectedServicesSet.size}", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary, maxLines = 1)
                            }
                        }
                    }

                    OutlinedButton(
                        onClick = {
                            coroutineScope.launch(Dispatchers.IO) {
                                val count = LightspeedWatchdogEngine.pulsePerimeterServices(context)
                                withContext(Dispatchers.Main) {
                                    LightspeedHapticEngine.heavyClick(context)
                                    Toast.makeText(context, "Perimeter pulse: $count sentinels verified & revivified", Toast.LENGTH_SHORT).show()
                                    refreshTrigger++
                                    onRefreshNeeded()
                                }
                            }
                        },
                        modifier = Modifier.fillMaxWidth().height(36.dp),
                        shape = RoundedCornerShape(10.dp),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.6f)),
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
                            Icon(Icons.Default.Security, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(15.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Pulse Sentinels (Autonomous Health Check)", fontWeight = FontWeight.Bold, fontSize = 11.sp, color = MaterialTheme.colorScheme.primary, maxLines = 1)
                        }
                    }
                }
            }

            // Search Bar Filter
            var searchQuery by rememberSaveable { mutableStateOf("") }

            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                placeholder = { Text("Filter accessibility services...", fontSize = 12.sp, color = Color.Gray) },
                modifier = Modifier.fillMaxWidth().height(50.dp),
                leadingIcon = {
                    Icon(Icons.Default.Search, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp))
                },
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) {
                        IconButton(onClick = { searchQuery = "" }, modifier = Modifier.size(20.dp)) {
                            Icon(Icons.Default.Close, contentDescription = "Clear", tint = Color.Gray, modifier = Modifier.size(14.dp))
                        }
                    }
                },
                singleLine = true,
                shape = RoundedCornerShape(10.dp),
                textStyle = LocalTextStyle.current.copy(fontSize = 12.sp, color = Color.White),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                    unfocusedBorderColor = Color.White.copy(alpha = 0.15f),
                    focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                    unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f)
                )
            )

            // Services List
            val filteredServices = remember(thirdPartyServices, searchQuery) {
                if (searchQuery.isBlank()) thirdPartyServices
                else {
                    val query = searchQuery.trim().lowercase()
                    thirdPartyServices.filter { sInfo ->
                        val sPkg = (sInfo.resolveInfo?.serviceInfo?.packageName ?: sInfo.id.substringBefore("/")).lowercase()
                        val sLabel = try {
                            sInfo.resolveInfo?.loadLabel(pm)?.toString()?.lowercase() ?: ""
                        } catch (_: Exception) { "" }
                        sPkg.contains(query) || sLabel.contains(query)
                    }
                }
            }

            if (filteredServices.isEmpty()) {
                Text(
                    text = if (searchQuery.isBlank()) "No external accessibility services installed." else "No matching services found.",
                    fontSize = 11.5.sp,
                    color = Color.LightGray.copy(alpha = 0.6f),
                    modifier = Modifier.padding(vertical = 8.dp)
                )
            } else {
                Column(
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.padding(top = 4.dp)
                ) {
                    filteredServices.forEach { sInfo ->
                        val sPkg = sInfo.resolveInfo?.serviceInfo?.packageName ?: sInfo.id.substringBefore("/")
                        val sCls = sInfo.resolveInfo?.serviceInfo?.name ?: sInfo.id.substringAfter("/")
                        val component = ComponentName(sPkg, sCls)
                        val componentId = component.flattenToString()

                        val sLabel = remember(componentId) {
                            try {
                                sInfo.resolveInfo?.loadLabel(pm)?.toString() ?: sPkg
                            } catch (_: Exception) { sPkg }
                        }

                        val iconBitmap = remember(sPkg) {
                            try {
                                val d = sInfo.resolveInfo?.loadIcon(pm) ?: pm.getApplicationIcon(sPkg)
                                d.toBitmap(width = 64, height = 64).asImageBitmap()
                            } catch (_: Throwable) {
                                null
                            }
                        }

                        var isServiceEnabled by remember(componentId, enabledComponents) {
                            mutableStateOf(enabledComponents.contains(component))
                        }

                        var isProtected by remember(componentId, protectedServicesSet) {
                            mutableStateOf(protectedServicesSet.contains(componentId))
                        }

                        var isBatteryWhitelisted by remember(sPkg, refreshTrigger) {
                            mutableStateOf(LightspeedWatchdogEngine.isPackageBatteryWhitelisted(context, sPkg))
                        }

                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(14.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.28f),
                            border = BorderStroke(1.dp, if (isServiceEnabled) successGreen.copy(alpha = 0.25f) else Color.White.copy(alpha = 0.08f))
                        ) {
                            Column(modifier = Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                // Top row: Icon + Label/Package + Instant Toggle Switch
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    if (iconBitmap != null) {
                                        Image(
                                            bitmap = iconBitmap,
                                            contentDescription = null,
                                            modifier = Modifier.size(34.dp).clip(RoundedCornerShape(8.dp))
                                        )
                                    } else {
                                        Box(
                                            modifier = Modifier
                                                .size(34.dp)
                                                .clip(RoundedCornerShape(8.dp))
                                                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Icon(Icons.Default.Widgets, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                                        }
                                    }

                                    Spacer(modifier = Modifier.width(10.dp))

                                    Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                                        Text(
                                            text = sLabel,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 13.sp,
                                            color = Color.White,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Text(
                                            text = sPkg,
                                            fontSize = 10.sp,
                                            color = Color.Gray,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }

                                    // Sub-100ms Instant Privileged Switch
                                    Switch(
                                        checked = isServiceEnabled,
                                        onCheckedChange = { checked ->
                                            coroutineScope.launch(Dispatchers.IO) {
                                                val ok = LightspeedWatchdogEngine.toggleAccessibilityService(context, componentId, checked)
                                                withContext(Dispatchers.Main) {
                                                    if (ok) {
                                                        isServiceEnabled = checked
                                                        LightspeedHapticEngine.tick(context)
                                                        val act = if (checked) "enabled" else "disabled"
                                                        Toast.makeText(context, "$sLabel $act", Toast.LENGTH_SHORT).show()
                                                        refreshTrigger++
                                                    } else {
                                                        // Fallback to accessibility settings
                                                        try {
                                                            val intent = Intent(android.provider.Settings.ACTION_ACCESSIBILITY_SETTINGS).apply {
                                                                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                                            }
                                                            context.startActivity(intent)
                                                        } catch (_: Exception) {}
                                                    }
                                                    onRefreshNeeded()
                                                }
                                            }
                                        },
                                        colors = SwitchDefaults.colors(
                                            checkedThumbColor = Color.White,
                                            checkedTrackColor = successGreen,
                                            uncheckedThumbColor = Color.White.copy(alpha = 0.7f),
                                            uncheckedTrackColor = Color.White.copy(alpha = 0.12f)
                                        )
                                    )
                                }

                                HorizontalDivider(color = Color.White.copy(alpha = 0.06f), thickness = 0.8.dp)

                                // Bottom Tactical Defense Actions Row
                                Row(
                                    modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Min),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    // 1. Auto-Revive Shield Guard Toggle
                                    Surface(
                                        modifier = Modifier
                                            .weight(1f)
                                            .fillMaxHeight()
                                            .clickable {
                                                LightspeedPreferences.togglePerimeterProtectedService(context, componentId)
                                                protectedServicesSet = LightspeedPreferences.getPerimeterProtectedServices(context)
                                                isProtected = !isProtected
                                                LightspeedHapticEngine.tick(context)
                                                Toast.makeText(
                                                    context,
                                                    if (isProtected) "Shield armed: Sentinel will auto-revive $sLabel" else "Shield disarmed",
                                                    Toast.LENGTH_SHORT
                                                ).show()
                                                onRefreshNeeded()
                                            },
                                        shape = RoundedCornerShape(8.dp),
                                        color = if (isProtected) successGreen.copy(alpha = 0.14f) else Color.White.copy(alpha = 0.05f),
                                        border = BorderStroke(1.dp, if (isProtected) successGreen.copy(alpha = 0.45f) else Color.White.copy(alpha = 0.12f))
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.Center
                                        ) {
                                            Icon(
                                                imageVector = if (isProtected) Icons.Default.Shield else Icons.Outlined.Shield,
                                                contentDescription = null,
                                                tint = if (isProtected) successGreen else Color.LightGray,
                                                modifier = Modifier.size(13.dp)
                                            )
                                            Spacer(modifier = Modifier.width(5.dp))
                                            Text(
                                                text = if (isProtected) "SHIELD: ARMED" else "SHIELD: OFF",
                                                fontSize = 9.5.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = if (isProtected) successGreen else Color.LightGray,
                                                maxLines = 1,
                                                softWrap = false
                                            )
                                        }
                                    }

                                    // 2. Battery Whitelist Pill / 1-Tap Whitelist Button
                                    Surface(
                                        modifier = Modifier
                                            .weight(1f)
                                            .fillMaxHeight()
                                            .clickable {
                                                if (isBatteryWhitelisted) {
                                                    Toast.makeText(context, "$sLabel battery is already unrestricted", Toast.LENGTH_SHORT).show()
                                                } else {
                                                    coroutineScope.launch(Dispatchers.IO) {
                                                        val ok = LightspeedWatchdogEngine.whitelistBattery(context, sPkg)
                                                        withContext(Dispatchers.Main) {
                                                            if (ok) {
                                                                isBatteryWhitelisted = true
                                                                LightspeedHapticEngine.tick(context)
                                                                Toast.makeText(context, "Battery whitelist granted for $sLabel", Toast.LENGTH_SHORT).show()
                                                                refreshTrigger++
                                                            } else {
                                                                Toast.makeText(context, "Shizuku required for 1-tap battery whitelist", Toast.LENGTH_SHORT).show()
                                                            }
                                                            onRefreshNeeded()
                                                        }
                                                    }
                                                }
                                            },
                                        shape = RoundedCornerShape(8.dp),
                                        color = if (isBatteryWhitelisted) successGreen.copy(alpha = 0.14f) else cautionAmber.copy(alpha = 0.14f),
                                        border = BorderStroke(1.dp, if (isBatteryWhitelisted) successGreen.copy(alpha = 0.45f) else cautionAmber.copy(alpha = 0.45f))
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.Center
                                        ) {
                                            Icon(
                                                imageVector = if (isBatteryWhitelisted) Icons.Default.CheckCircle else Icons.Default.BatteryChargingFull,
                                                contentDescription = null,
                                                tint = if (isBatteryWhitelisted) successGreen else cautionAmber,
                                                modifier = Modifier.size(13.dp)
                                            )
                                            Spacer(modifier = Modifier.width(5.dp))
                                            Text(
                                                text = if (isBatteryWhitelisted) "BATTERY EXEMPT" else "FIX BATTERY",
                                                fontSize = 9.5.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = if (isBatteryWhitelisted) successGreen else cautionAmber,
                                                maxLines = 1,
                                                softWrap = false
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
