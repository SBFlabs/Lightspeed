package com.sbf.lightspeed.settings

import androidx.compose.ui.text.style.TextAlign
import android.content.Context
import android.content.SharedPreferences
import android.os.Build
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sbf.lightspeed.LightspeedAccessibilityService
import com.sbf.lightspeed.system.LightspeedPreferences
import com.sbf.lightspeed.system.LightspeedWatchdogEngine
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material.icons.outlined.Security
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.compose.ui.text.style.TextOverflow
import com.sbf.lightspeed.system.LightspeedHapticEngine
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun WatchdogQuickTelemetryCard(
    context: Context,
    prefs: SharedPreferences,
    onStateChanged: () -> Unit = {}
) {
    val coroutineScope = rememberCoroutineScope()
    val isServiceRunning = LightspeedAccessibilityService.instance != null
    val isShizukuActive = com.sbf.lightspeed.system.ElevatedTaskCloser.isShizukuActive
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

    val enabledSet = remember { LightspeedWatchdogEngine.getEnabledAccessibilityServices(context) }
    val protectedSet = remember { LightspeedPreferences.getPerimeterProtectedServices(context) }
    val cautionAmber = Color(0xFFFFB300)
    var showPerimeterDialog by rememberSaveable { mutableStateOf(false) }
    var showBatteryExemptionDialog by rememberSaveable { mutableStateOf(false) }

    var lastCrashTimestamp by remember(prefs) {
        mutableStateOf(prefs.getLong("key_last_crash_timestamp", 0L))
    }
    var lastCrashMessage by remember(prefs) {
        mutableStateOf(prefs.getString("key_last_crash_message", null))
    }
    var lastCrashStack by remember(prefs) {
        mutableStateOf(prefs.getString("key_last_crash_stack", null))
    }
    var showCrashDetailDialog by rememberSaveable { mutableStateOf(false) }

    var flightLogEntries by remember {
        mutableStateOf(com.sbf.lightspeed.system.FlightLog.getEntriesNewestFirst())
    }
    var showFlightLogDialog by rememberSaveable { mutableStateOf(false) }

    val blackboxTitle = com.sbf.lightspeed.system.LightspeedLanguageEngine.resolve(
        com.sbf.lightspeed.system.LightspeedVocabulary.Key.FLIGHT_BLACKBOX
    )

    val copyReportToClipboard: () -> Unit = {
        val report = com.sbf.lightspeed.system.FlightLog.generateTextReport(context)
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? android.content.ClipboardManager
        val clip = android.content.ClipData.newPlainText("Lightspeed Flight Blackbox Report", report)
        clipboard?.setPrimaryClip(clip)
        com.sbf.lightspeed.system.LightspeedHapticEngine.tick(context)
        Toast.makeText(context, "Blackbox report copied to clipboard", Toast.LENGTH_SHORT).show()
    }

    val clearActionLogOnly: () -> Unit = {
        com.sbf.lightspeed.system.FlightLog.clearLog()
        flightLogEntries = emptyList()
        com.sbf.lightspeed.system.LightspeedHapticEngine.tick(context)
        Toast.makeText(context, "Action log cleared", Toast.LENGTH_SHORT).show()
    }

    val copyTelemetryToClipboard: () -> Unit = {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? android.content.ClipboardManager
        val timeStr = if (lastCrashTimestamp > 0L) {
            java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.getDefault()).format(java.util.Date(lastCrashTimestamp))
        } else "Unknown"
        val payload = """
LIGHTSPEED BLACKBOX TELEMETRY
Timestamp: $timeStr
Device: ${Build.MANUFACTURER} ${Build.MODEL} (Android ${Build.VERSION.RELEASE}, API ${Build.VERSION.SDK_INT})
Exception: ${lastCrashMessage ?: "Unknown Anomaly"}

STACKTRACE:
${lastCrashStack ?: "No stacktrace recorded"}
        """.trimIndent()
        val clip = android.content.ClipData.newPlainText("Lightspeed Blackbox Telemetry", payload)
        clipboard?.setPrimaryClip(clip)
        com.sbf.lightspeed.system.LightspeedHapticEngine.tick(context)
        Toast.makeText(context, "Blackbox telemetry copied to clipboard", Toast.LENGTH_SHORT).show()
    }

    val clearTelemetry: () -> Unit = {
        LightspeedWatchdogEngine.clearCrashTelemetry(context)
        lastCrashTimestamp = 0L
        lastCrashMessage = null
        lastCrashStack = null
        showCrashDetailDialog = false
        com.sbf.lightspeed.system.LightspeedHapticEngine.tick(context)
        Toast.makeText(context, "Blackbox telemetry cleared", Toast.LENGTH_SHORT).show()
        onStateChanged()
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.4f)),
        border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.08f))
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Outlined.Shield,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        "SYSTEM IMMUNITY & WATCHDOGS",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                        letterSpacing = 0.8.sp
                    )
                }
                val statusColor = when {
                    isServiceRunning && isShizukuActive -> Color(0xFF00E676)
                    isServiceRunning -> MaterialTheme.colorScheme.primary
                    else -> cautionAmber
                }
                val statusText = when {
                    isServiceRunning && isShizukuActive -> "FORTIFIED"
                    isServiceRunning -> "ONLINE"
                    else -> "STANDBY"
                }
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = statusColor.copy(alpha = 0.12f),
                    border = androidx.compose.foundation.BorderStroke(1.dp, statusColor.copy(alpha = 0.35f))
                ) {
                    Text(
                        text = statusText,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        color = statusColor,
                        letterSpacing = 0.6.sp,
                        modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp)
                    )
                }
            }

            // Status Pills Row
            Row(
                modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Min),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                // Pill 1: Core Service
                Surface(
                    modifier = Modifier.weight(1f).fillMaxHeight(),
                    shape = RoundedCornerShape(8.dp),
                    color = if (isServiceRunning) Color(0xFF00E676).copy(alpha = 0.12f) else Color(0xFFFF3D00).copy(alpha = 0.15f),
                    border = androidx.compose.foundation.BorderStroke(1.dp, if (isServiceRunning) Color(0xFF00E676).copy(alpha = 0.35f) else Color(0xFFFF3D00).copy(alpha = 0.35f))
                ) {
                    Column(
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 6.dp),
                        verticalArrangement = Arrangement.Center
                    ) {
                        Text("CORE", fontSize = 8.sp, color = Color.LightGray, fontWeight = FontWeight.SemiBold, maxLines = 1, softWrap = false)
                        Text(
                            text = if (isServiceRunning) "ONLINE" else "OFFLINE",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (isServiceRunning) Color(0xFF00E676) else Color(0xFFFF3D00),
                            maxLines = 1,
                            softWrap = false
                        )
                    }
                }

                // Pill 2: Shizuku Bridge
                Surface(
                    modifier = Modifier.weight(1f).fillMaxHeight(),
                    shape = RoundedCornerShape(8.dp),
                    color = if (isShizukuActive) Color(0xFF00E676).copy(alpha = 0.12f) else Color(0xFFFF9800).copy(alpha = 0.15f),
                    border = androidx.compose.foundation.BorderStroke(1.dp, if (isShizukuActive) Color(0xFF00E676).copy(alpha = 0.35f) else Color(0xFFFF9800).copy(alpha = 0.35f))
                ) {
                    Column(
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 6.dp),
                        verticalArrangement = Arrangement.Center
                    ) {
                        Text("SHIZUKU", fontSize = 8.sp, color = Color.LightGray, fontWeight = FontWeight.SemiBold, maxLines = 1, softWrap = false)
                        Text(
                            text = if (isShizukuActive) "ACTIVE" else "STANDBY",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (isShizukuActive) Color(0xFF00E676) else Color(0xFFFF9800),
                            maxLines = 1,
                            softWrap = false
                        )
                    }
                }

                // Pill 3: Battery Exemption
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .clip(RoundedCornerShape(8.dp))
                        .background(if (isBatteryIgnored) Color(0xFF00E676).copy(alpha = 0.12f) else Color(0xFFFF9800).copy(alpha = 0.15f))
                        .border(
                            1.dp,
                            if (isBatteryIgnored) Color(0xFF00E676).copy(alpha = 0.35f) else Color(0xFFFF9800).copy(alpha = 0.35f),
                            RoundedCornerShape(8.dp)
                        )
                        .combinedClickable(
                            onClick = {
                                coroutineScope.launch(Dispatchers.IO) {
                                    val currentlyIgnored = isBatteryIgnored
                                    val ok = if (currentlyIgnored) {
                                        LightspeedWatchdogEngine.removeBatteryWhitelist(context, context.packageName)
                                    } else {
                                        LightspeedWatchdogEngine.whitelistBattery(context, context.packageName)
                                    }
                                    withContext(Dispatchers.Main) {
                                        if (ok) {
                                            isBatteryIgnored = !currentlyIgnored
                                            com.sbf.lightspeed.system.LightspeedHapticEngine.tick(context)
                                            val msg = if (currentlyIgnored) "Battery exemption removed for Lightspeed" else "Battery whitelist granted via Shizuku"
                                            Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                                            onStateChanged()
                                        } else {
                                            LightspeedWatchdogEngine.requestIgnoreBatteryOptimization(context)
                                        }
                                    }
                                }
                            },
                            onLongClick = {
                                com.sbf.lightspeed.system.LightspeedHapticEngine.tick(context)
                                showBatteryExemptionDialog = true
                            }
                        )
                ) {
                    Column(
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 6.dp),
                        verticalArrangement = Arrangement.Center
                    ) {
                        Text("BATTERY", fontSize = 8.sp, color = Color.LightGray, fontWeight = FontWeight.SemiBold, maxLines = 1, softWrap = false)
                        Text(
                            text = if (isBatteryIgnored) "EXEMPT" else "LIMITED",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (isBatteryIgnored) Color(0xFF00E676) else Color(0xFFFF9800),
                            maxLines = 1,
                            softWrap = false
                        )
                    }
                }

                // Pill 4: Perimeter / Accessibility Services (The 4th one, on the right)
                Surface(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .clickable { showPerimeterDialog = true },
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                    border = androidx.compose.foundation.BorderStroke(1.2.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.45f))
                ) {
                    Column(
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 6.dp),
                        verticalArrangement = Arrangement.Center
                    ) {
                        Text("SERVICES", fontSize = 8.sp, color = Color.LightGray, fontWeight = FontWeight.SemiBold, maxLines = 1, softWrap = false)
                        Text(
                            text = "${enabledSet.size} ACTIVE",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary,
                            maxLines = 1,
                            softWrap = false
                        )
                        Text(
                            text = "MANAGE ↗",
                            fontSize = 7.5.sp,
                            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.85f),
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            softWrap = false
                        )
                    }
                }
            }

            // Self-Reviving Watchdog & Sentinel Controls
            var sentinelEnabled by rememberSaveable {
                mutableStateOf(prefs.getBoolean(LightspeedPreferences.KEY_ACCESSIBILITY_SENTINEL_ENABLED, false))
            }
            var crashSentinelEnabled by rememberSaveable {
                mutableStateOf(prefs.getBoolean(LightspeedPreferences.KEY_CRASH_SENTINEL_ENABLED, true))
            }

            PrefToggleRow(
                title = "Proactive OEM Sentinel",
                subtitle = "Background sentinel thread polls Lightspeed & shielded sentinels every 20s, reviving via Shizuku if killed by battery management.",
                isChecked = sentinelEnabled,
                onCheckedChange = { checked ->
                    sentinelEnabled = checked
                    prefs.edit().putBoolean(LightspeedPreferences.KEY_ACCESSIBILITY_SENTINEL_ENABLED, checked).apply()
                    if (checked) {
                        LightspeedWatchdogEngine.initSentinel(context)
                    } else {
                        LightspeedWatchdogEngine.stopSentinel()
                    }
                    onStateChanged()
                }
            )

            PrefToggleRow(
                title = "Autonomous Crash Resuscitation",
                subtitle = "Catches fatal JVM or shader exceptions and arms an OS alarm to resuscitate the service via Shizuku within 1s.",
                isChecked = crashSentinelEnabled,
                onCheckedChange = { checked ->
                    crashSentinelEnabled = checked
                    prefs.edit().putBoolean(LightspeedPreferences.KEY_CRASH_SENTINEL_ENABLED, checked).apply()
                    onStateChanged()
                }
            )

            // Tactical Pulse Actions Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(
                    onClick = {
                        coroutineScope.launch(Dispatchers.IO) {
                            val ok = LightspeedWatchdogEngine.reviveAccessibilityService(context)
                            withContext(Dispatchers.Main) {
                                com.sbf.lightspeed.system.LightspeedHapticEngine.heavyClick(context)
                                if (ok) {
                                    Toast.makeText(context, "Core pulse sent: Online", Toast.LENGTH_SHORT).show()
                                } else {
                                    Toast.makeText(context, "Opening Accessibility Settings...", Toast.LENGTH_SHORT).show()
                                    LightspeedWatchdogEngine.openAccessibilitySettings(context)
                                }
                                onStateChanged()
                            }
                        }
                    },
                    modifier = Modifier.weight(1f).height(36.dp),
                    shape = RoundedCornerShape(10.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, cautionAmber.copy(alpha = 0.6f)),
                    contentPadding = PaddingValues(horizontal = 4.dp, vertical = 0.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
                        Icon(Icons.Default.HealthAndSafety, contentDescription = null, tint = cautionAmber, modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Pulse Core", fontWeight = FontWeight.Bold, fontSize = 10.5.sp, color = cautionAmber, maxLines = 1, softWrap = false)
                    }
                }

                OutlinedButton(
                    onClick = {
                        coroutineScope.launch(Dispatchers.IO) {
                            val count = LightspeedWatchdogEngine.pulsePerimeterServices(context)
                            withContext(Dispatchers.Main) {
                                com.sbf.lightspeed.system.LightspeedHapticEngine.heavyClick(context)
                                Toast.makeText(context, "Perimeter pulse: $count sentinels verified", Toast.LENGTH_SHORT).show()
                                onStateChanged()
                            }
                        }
                    },
                    modifier = Modifier.weight(1f).height(36.dp),
                    shape = RoundedCornerShape(10.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.6f)),
                    contentPadding = PaddingValues(horizontal = 4.dp, vertical = 0.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
                        Icon(Icons.Default.Security, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Pulse Perimeter", fontWeight = FontWeight.Bold, fontSize = 10.5.sp, color = MaterialTheme.colorScheme.primary, maxLines = 1, softWrap = false)
                    }
                }
            }

            // Direct Launcher for Accessibility Services & Sentinels Manager
            OutlinedButton(
                onClick = { showPerimeterDialog = true },
                modifier = Modifier.fillMaxWidth().height(38.dp),
                shape = RoundedCornerShape(10.dp),
                border = androidx.compose.foundation.BorderStroke(1.2.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)),
                colors = ButtonDefaults.outlinedButtonColors(containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.08f)),
                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
                    Icon(Icons.Outlined.Security, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        "Manage Accessibility Services (${enabledSet.size} Active) ↗",
                        fontWeight = FontWeight.Bold,
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.primary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            // Flight Recorder Telemetry Anomaly Notice (if recorded crash exists)
            if (lastCrashTimestamp > 0L && !lastCrashMessage.isNullOrBlank()) {
                val timeStr = remember(lastCrashTimestamp) {
                    java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.getDefault()).format(java.util.Date(lastCrashTimestamp))
                }
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .clickable { showFlightLogDialog = true },
                    shape = RoundedCornerShape(10.dp),
                    color = cautionAmber.copy(alpha = 0.12f),
                    border = androidx.compose.foundation.BorderStroke(1.dp, cautionAmber.copy(alpha = 0.4f))
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(10.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(modifier = Modifier.weight(1f).padding(end = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Warning, contentDescription = null, tint = cautionAmber, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "BLACKBOX ANOMALY",
                                    fontSize = 10.5.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = cautionAmber,
                                    letterSpacing = 0.6.sp
                                )
                            }
                            Text(
                                text = timeStr,
                                fontSize = 9.5.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = cautionAmber.copy(alpha = 0.8f)
                            )
                        }

                        Spacer(modifier = Modifier.height(4.dp))

                        Text(
                            text = lastCrashMessage ?: "Core exception recorded",
                            fontSize = 11.sp,
                            color = Color.White.copy(alpha = 0.9f),
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            lineHeight = 15.sp
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.End,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // Copy button
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = Color.White.copy(alpha = 0.08f),
                                border = androidx.compose.foundation.BorderStroke(0.8.dp, Color.White.copy(alpha = 0.2f)),
                                modifier = Modifier.clickable { copyReportToClipboard() }
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(Icons.Default.ContentCopy, contentDescription = "Copy", tint = Color.White.copy(alpha = 0.9f), modifier = Modifier.size(12.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("COPY", fontSize = 9.5.sp, fontWeight = FontWeight.Bold, color = Color.White.copy(alpha = 0.9f))
                                }
                            }

                            Spacer(modifier = Modifier.width(6.dp))

                            // View Log button
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = cautionAmber.copy(alpha = 0.18f),
                                border = androidx.compose.foundation.BorderStroke(0.8.dp, cautionAmber.copy(alpha = 0.45f)),
                                modifier = Modifier.clickable {
                                    flightLogEntries = com.sbf.lightspeed.system.FlightLog.getEntriesNewestFirst()
                                    showFlightLogDialog = true
                                }
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(Icons.Default.Code, contentDescription = "View Log", tint = cautionAmber, modifier = Modifier.size(12.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("VIEW LOG", fontSize = 9.5.sp, fontWeight = FontWeight.Bold, color = cautionAmber)
                                }
                            }

                            Spacer(modifier = Modifier.width(6.dp))

                            // Clear button
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = Color(0xFFFF5252).copy(alpha = 0.15f),
                                border = androidx.compose.foundation.BorderStroke(0.8.dp, Color(0xFFFF5252).copy(alpha = 0.4f)),
                                modifier = Modifier.clickable { clearTelemetry() }
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(Icons.Default.DeleteOutline, contentDescription = "Clear", tint = Color(0xFFFF5252), modifier = Modifier.size(12.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("CLEAR", fontSize = 9.5.sp, fontWeight = FontWeight.Bold, color = Color(0xFFFF5252))
                                }
                            }
                        }
                    }
                }
            }

            // Always-Visible Flight Blackbox Action Log Card
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .clickable {
                        flightLogEntries = com.sbf.lightspeed.system.FlightLog.getEntriesNewestFirst()
                        showFlightLogDialog = true
                    },
                shape = RoundedCornerShape(10.dp),
                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.08f),
                border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.3f))
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(10.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(modifier = Modifier.weight(1f).padding(end = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Dvr, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = blackboxTitle.uppercase(),
                                fontSize = 10.5.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary,
                                letterSpacing = 0.6.sp
                            )
                        }
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
                            border = androidx.compose.foundation.BorderStroke(0.8.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.4f))
                        ) {
                            Text(
                                text = "${flightLogEntries.size} ENTRIES",
                                fontSize = 8.5.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(6.dp))

                    val newestEntry = flightLogEntries.firstOrNull()
                    if (newestEntry != null) {
                        val statusColor = if (newestEntry.ok) Color(0xFF00E676) else Color(0xFFFF5252)
                        val statusText = if (newestEntry.ok) "OK" else "FAIL"
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "[${newestEntry.formatTimestamp()}] [${newestEntry.tag}] ",
                                fontSize = 10.sp,
                                fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                                color = Color.LightGray
                            )
                            Text(
                                text = "[$statusText] ",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                                color = statusColor
                            )
                            Text(
                                text = newestEntry.text,
                                fontSize = 10.sp,
                                color = Color.White.copy(alpha = 0.9f),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    } else {
                        Text(
                            text = "No action entries logged",
                            fontSize = 10.5.sp,
                            color = Color.White.copy(alpha = 0.5f)
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // COPY button
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = Color.White.copy(alpha = 0.08f),
                            border = androidx.compose.foundation.BorderStroke(0.8.dp, Color.White.copy(alpha = 0.2f)),
                            modifier = Modifier.clickable { copyReportToClipboard() }
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Default.ContentCopy, contentDescription = "Copy", tint = Color.White.copy(alpha = 0.9f), modifier = Modifier.size(12.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("COPY", fontSize = 9.5.sp, fontWeight = FontWeight.Bold, color = Color.White.copy(alpha = 0.9f))
                            }
                        }

                        Spacer(modifier = Modifier.width(6.dp))

                        // VIEW LOG button
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.18f),
                            border = androidx.compose.foundation.BorderStroke(0.8.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.45f)),
                            modifier = Modifier.clickable {
                                flightLogEntries = com.sbf.lightspeed.system.FlightLog.getEntriesNewestFirst()
                                showFlightLogDialog = true
                            }
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Default.Code, contentDescription = "View Log", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(12.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("VIEW LOG", fontSize = 9.5.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                            }
                        }

                        Spacer(modifier = Modifier.width(6.dp))

                        // CLEAR button
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = Color(0xFFFF5252).copy(alpha = 0.15f),
                            border = androidx.compose.foundation.BorderStroke(0.8.dp, Color(0xFFFF5252).copy(alpha = 0.4f)),
                            modifier = Modifier.clickable { clearActionLogOnly() }
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Default.DeleteOutline, contentDescription = "Clear", tint = Color(0xFFFF5252), modifier = Modifier.size(12.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("CLEAR", fontSize = 9.5.sp, fontWeight = FontWeight.Bold, color = Color(0xFFFF5252))
                            }
                        }
                    }
                }
            }
        }
    }

    if (showFlightLogDialog || showCrashDetailDialog) {
        AlertDialog(
            onDismissRequest = {
                showFlightLogDialog = false
                showCrashDetailDialog = false
            },
            icon = {
                Icon(
                    imageVector = if (lastCrashTimestamp > 0L && !lastCrashMessage.isNullOrBlank()) Icons.Default.Warning else Icons.Default.Dvr,
                    contentDescription = null,
                    tint = if (lastCrashTimestamp > 0L && !lastCrashMessage.isNullOrBlank()) cautionAmber else MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(28.dp)
                )
            },
            title = {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = blackboxTitle.uppercase(),
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        textAlign = TextAlign.Center
                    )
                }
            },
            text = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 380.dp)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    if (lastCrashTimestamp > 0L && !lastCrashMessage.isNullOrBlank()) {
                        val crashTimeStr = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.getDefault()).format(java.util.Date(lastCrashTimestamp))
                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(8.dp),
                            color = cautionAmber.copy(alpha = 0.1f),
                            border = androidx.compose.foundation.BorderStroke(1.dp, cautionAmber.copy(alpha = 0.4f))
                        ) {
                            Column(modifier = Modifier.padding(10.dp)) {
                                Text(
                                    text = "LAST CRASH ANOMALY ($crashTimeStr)",
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = cautionAmber
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = lastCrashMessage ?: "Unknown Anomaly",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = Color.White
                                )
                                Spacer(modifier = Modifier.height(6.dp))
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .heightIn(max = 140.dp)
                                        .background(Color(0xFF0D1117), shape = RoundedCornerShape(6.dp))
                                        .padding(6.dp)
                                        .verticalScroll(rememberScrollState())
                                ) {
                                    Text(
                                        text = lastCrashStack ?: "No stacktrace recorded",
                                        fontSize = 9.sp,
                                        fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                                        color = Color(0xFF81D4FA),
                                        lineHeight = 12.sp
                                    )
                                }
                            }
                        }
                    }

                    Text(
                        text = "ACTION LOG (${flightLogEntries.size}/50)",
                        fontSize = 10.5.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                        letterSpacing = 0.6.sp
                    )

                    if (flightLogEntries.isEmpty()) {
                        Text(
                            text = "No action entries recorded.",
                            fontSize = 11.sp,
                            color = Color.Gray,
                            modifier = Modifier.padding(vertical = 8.dp)
                        )
                    } else {
                        flightLogEntries.forEach { entry ->
                            val statusColor = if (entry.ok) Color(0xFF00E676) else Color(0xFFFF5252)
                            val statusText = if (entry.ok) "OK" else "FAIL"
                            Surface(
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(6.dp),
                                color = Color.White.copy(alpha = 0.04f),
                                border = androidx.compose.foundation.BorderStroke(0.8.dp, Color.White.copy(alpha = 0.08f))
                            ) {
                                Column(modifier = Modifier.padding(8.dp)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = "${entry.formatFullTimestamp()} [${entry.tag}]",
                                            fontSize = 9.5.sp,
                                            fontWeight = FontWeight.Bold,
                                            fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                                            color = Color.LightGray
                                        )
                                        Surface(
                                            shape = RoundedCornerShape(4.dp),
                                            color = statusColor.copy(alpha = 0.15f),
                                            border = androidx.compose.foundation.BorderStroke(0.6.dp, statusColor.copy(alpha = 0.4f))
                                        ) {
                                            Text(
                                                text = statusText,
                                                fontSize = 8.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = statusColor,
                                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                                            )
                                        }
                                    }
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = entry.text,
                                        fontSize = 9.5.sp,
                                        fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                                        color = Color.White.copy(alpha = 0.9f),
                                        lineHeight = 13.sp
                                    )
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = copyReportToClipboard) {
                    Icon(Icons.Default.ContentCopy, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(15.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("COPY REPORT", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold, fontSize = 11.5.sp)
                }
            },
            dismissButton = {
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    TextButton(onClick = clearActionLogOnly) {
                        Icon(Icons.Default.DeleteOutline, contentDescription = null, tint = Color(0xFFFF6B6B), modifier = Modifier.size(15.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("CLEAR LOG", color = Color(0xFFFF6B6B), fontWeight = FontWeight.Bold, fontSize = 11.5.sp)
                    }
                    TextButton(onClick = {
                        showFlightLogDialog = false
                        showCrashDetailDialog = false
                    }) {
                        Text("CLOSE", color = Color.White.copy(alpha = 0.7f), fontSize = 11.5.sp)
                    }
                }
            },
            containerColor = Color(0xFF1B1F2B),
            shape = RoundedCornerShape(16.dp)
        )
    }

    if (showPerimeterDialog) {
        PerimeterServicesDeckDialog(
            context = context,
            prefs = prefs,
            onDismiss = { showPerimeterDialog = false },
            onRefreshNeeded = onStateChanged
        )
    }

    if (showBatteryExemptionDialog) {
        BatteryExemptionDeckDialog(
            context = context,
            onDismiss = { showBatteryExemptionDialog = false },
            onExemptionChanged = {
                isBatteryIgnored = LightspeedWatchdogEngine.isBatteryOptimizationIgnored(context)
                onStateChanged()
            }
        )
    }
}

