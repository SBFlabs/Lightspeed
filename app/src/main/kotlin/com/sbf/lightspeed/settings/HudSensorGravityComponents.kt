package com.sbf.lightspeed.settings

import android.content.Context
import android.content.SharedPreferences
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sbf.lightspeed.LightspeedAccessibilityService
import com.sbf.lightspeed.system.*

@Composable
fun HudSensorDeckSection(
    context: Context,
    prefs: SharedPreferences,
    isExpanded: Boolean,
    onToggle: () -> Unit,
    isStatusBarGeoExpanded: Boolean,
    onToggleStatusBarGeo: () -> Unit,
    isStatusBarGesturesExpanded: Boolean,
    onToggleStatusBarGestures: () -> Unit,
    dynamicActionTokens: List<String>,
    tokenLabelCache: Map<String, String>,
    onRefreshNeeded: () -> Unit
) {
    CompactAccordionSection(
        title = LightspeedLanguageEngine.resolve(LightspeedVocabulary.Key.SENSOR_AREA),
        icon = {
            Icon(
                imageVector = Icons.Outlined.TouchApp,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(20.dp)
            )
        },
        isExpanded = isExpanded,
        onToggle = onToggle
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            PrefToggleRow(
                prefs = prefs,
                prefKey = LightspeedPreferences.KEY_STATUSBAR_ENABLED,
                defaultVal = false,
                title = "Enable Sensor Area Gestures",
                subtitle = "Top-edge gesture detection",
                onChanged = {
                    val isEnabled = prefs.getBoolean(LightspeedPreferences.KEY_STATUSBAR_ENABLED, false)
                    if (isStatusBarGeoExpanded) {
                        prefs.edit().putBoolean(LightspeedPreferences.KEY_STATUSBAR_PREVIEW, isEnabled).apply()
                    }
                    safeReloadPreferences()
                    onRefreshNeeded()
                }
            )

            val isLandscape = androidx.compose.ui.platform.LocalConfiguration.current.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE
            val spanKey = if (isLandscape) LightspeedPreferences.KEY_STATUSBAR_SPAN_LANDSCAPE else LightspeedPreferences.KEY_STATUSBAR_SPAN
            val defaultSpan = if (isLandscape) 150 else 200
            val thicknessKey = if (isLandscape) LightspeedPreferences.KEY_STATUSBAR_THICKNESS_LANDSCAPE else LightspeedPreferences.KEY_STATUSBAR_THICKNESS
            val defaultThickness = 35
            val offsetXKey = if (isLandscape) LightspeedPreferences.KEY_STATUSBAR_OFFSET_X_LANDSCAPE else LightspeedPreferences.KEY_STATUSBAR_OFFSET_X
            val defaultOffsetX = if (isLandscape) 300 else 105

            // 1. Geometry & Sensitivity
            CollapsibleSubSection(
                title = "Geometry & Sensitivity",
                subtitle = "Span, thickness, offsets & idle glow",
                isExpanded = isStatusBarGeoExpanded,
                onToggle = onToggleStatusBarGeo
            ) {
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                    border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.3f)),
                    modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)
                ) {
                    Column(modifier = Modifier.padding(10.dp)) {
                        Text(
                            text = if (isLandscape) "✦ Active Sensor Calibration: LANDSCAPE (Tablet Mode)" else "✦ Active Sensor Calibration: PORTRAIT (Phone Mode)",
                            fontWeight = FontWeight.Bold,
                            fontSize = 11.5.sp,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = if (isLandscape) {
                                "Calibrating landscape sensor deck dimensions directly (Span: 150dp, Thickness: 35dp, Offset X: Max)."
                            } else {
                                "Calibrating portrait phone dimensions directly (Span: 200dp, Thickness: 35dp, Offset X: 105dp). Rotate to landscape to calibrate tablet mode."
                            },
                            fontSize = 10.5.sp,
                            color = Color.White.copy(alpha = 0.8f)
                        )
                    }
                }
                PrefDottedSliderRow(context, prefs, spanKey, "", "Span (≥1000 = Full Width)", 50, 1080, 10, defaultSpan)
                PrefDottedSliderRow(context, prefs, thicknessKey, "", "Thickness", 10, 52, 2, defaultThickness)
                PrefDottedSliderRow(context, prefs, offsetXKey, "", "Horizontal Offset (X Axis)", -300, 300, 5, defaultOffsetX)
                PrefDottedSliderRow(context, prefs, LightspeedPreferences.KEY_STATUSBAR_OFFSET_Y, "", "Vertical Offset (Y Axis)", -100, 200, 5, 0)
                PrefDottedSliderRow(context, prefs, LightspeedPreferences.KEY_STATUSBAR_SENSITIVITY, "", "Touch Sensitivity", 10, 100, 5, 25)
                PrefDottedSliderRow(context, prefs, LightspeedPreferences.KEY_STATUSBAR_TRANSPARENCY, "", "Stealth Idle Glow", 0, 100, 5, 0)
            }

            // 2. Gestures
            CollapsibleSubSection(
                title = "Gestures & Macros",
                subtitle = "Tap, double-tap, left & right swipes with Hold Modifiers",
                isExpanded = isStatusBarGesturesExpanded,
                onToggle = onToggleStatusBarGestures
            ) {
                val statusVectors = listOf(
                    "TAP" to ("Tap" to ArrowDirection.TAP),
                    "DOUBLE_TAP" to ("Tap (Double)" to ArrowDirection.DOUBLE_TAP),
                    "SWIPE_LEFT" to ("Swipe Left" to ArrowDirection.SWIPE_LEFT),
                    "SWIPE_RIGHT" to ("Swipe Right" to ArrowDirection.SWIPE_RIGHT),
                    "SWIPE_LEFT_BACK" to ("Rebound Left" to ArrowDirection.LEFT_BACK),
                    "SWIPE_RIGHT_BACK" to ("Rebound Right" to ArrowDirection.RIGHT_BACK)
                )

                statusVectors.forEach { (vectorKey, pairInfo) ->
                    val (vectorTitle, arrowEnum) = pairInfo
                    GestureMappingRow(context, prefs, arrowEnum, false, "pref_macro_action_STATUSBAR_${vectorKey}", vectorTitle, dynamicActionTokens, tokenLabelCache)
                    GestureMappingRow(context, prefs, arrowEnum, true, "pref_macro_action_STATUSBAR_${vectorKey}_HOLD", "$vectorTitle + Hold Modifier", dynamicActionTokens, tokenLabelCache)
                }
            }
        }
    }
}

@Composable
fun HudSyntheticGravitySection(
    context: Context,
    prefs: SharedPreferences,
    isExpanded: Boolean,
    onToggle: () -> Unit,
    onRefreshNeeded: () -> Unit
) {
    var isAutoRotateActive by remember { mutableStateOf(LightspeedOrientationEngine.isAutoRotateEnabled(context)) }
    var isFaceRotateActive by remember { mutableStateOf(LightspeedOrientationEngine.isFaceRotateEnabled(context)) }
    var selectedAttitudeBucketForAppPicker by remember { mutableStateOf<LightspeedOrientationEngine.AttitudeBucket?>(null) }

    DisposableEffect(Unit) {
        val observer = LightspeedOrientationEngine.registerObserver(
            context,
            onAutoRotateChanged = { isAutoRotateActive = it },
            onFaceRotateChanged = { isFaceRotateActive = it }
        )
        onDispose {
            try { context.contentResolver.unregisterContentObserver(observer) } catch (_: Exception) {}
        }
    }

    if (selectedAttitudeBucketForAppPicker != null) {
        AttitudeAppAssignmentSheet(
            context = context,
            bucket = selectedAttitudeBucketForAppPicker!!,
            onDismiss = { selectedAttitudeBucketForAppPicker = null },
            onUpdated = { onRefreshNeeded() }
        )
    }

    CompactAccordionSection(
        title = LightspeedLanguageEngine.resolve(LightspeedVocabulary.Key.GRAVITY_ENGINE),
        icon = {
            Icon(
                imageVector = Icons.Outlined.Rotate90DegreesCw,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(20.dp)
            )
        },
        isExpanded = isExpanded,
        onToggle = onToggle
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            // Introductory Clarifying Note
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.08f),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.22f))
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Info,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Automates device orientation per application (Strict Portrait, Sensor Portrait 0°/180°, Landscape, or 360° Gyro). Manual gesture triggers override these rules on demand. Bucket app assignments are synchronized with the customization options in the Action Selection Menu.",
                        fontSize = 11.sp,
                        color = Color.LightGray.copy(alpha = 0.9f),
                        lineHeight = 14.5.sp
                    )
                }
            }

            // 0. Permission Warning Interlock
            val hasPermission = remember(context) {
                LightspeedOrientationEngine.hasPermission(context)
            }
            if (!hasPermission) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .clickable {
                            LightspeedOrientationEngine.requestWriteSettingsPermission(context)
                        },
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.25f)),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.5f))
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Default.Warning,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                "Modify System Settings Permission Required",
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp,
                                color = Color.White
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                "Tap to allow Lightspeed to modify system settings so it can control device rotation and synthetic gravity.",
                                fontSize = 10.5.sp,
                                color = Color.LightGray.copy(alpha = 0.85f),
                                lineHeight = 13.sp
                            )
                        }
                    }
                }
            }

            val cautionAmber = Color(0xFFFFB300)
            var showActionsIndependenceDialog by remember { mutableStateOf(false) }
            if (showActionsIndependenceDialog) {
                AlertDialog(
                    onDismissRequest = { showActionsIndependenceDialog = false },
                    icon = {
                        Icon(
                            imageVector = Icons.Default.PriorityHigh,
                            contentDescription = null,
                            tint = cautionAmber,
                            modifier = Modifier.size(28.dp)
                        )
                    },
                    title = {
                        Text(
                            text = "ACTION INDEPENDENCE",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                            textAlign = TextAlign.Center
                        )
                    },
                    text = {
                        Text(
                            text = "• Transient Actions: Actions from the Action Selection Menu (such as 'Force Transient Sensor Portrait', 'Force Landscape', 'Force Portrait', and '360° Gyro') operate completely independently from this toggle.\n\n• Yields to Automation: They act as transient manual overrides that yield automatically back to the Synthetic Gravity Engine rules upon switching apps or screen-off, preventing hard-locks.\n\n• Toggle Scope: Turning this Synthetic Gravity Engine toggle OFF disables per-app automation buckets only. Manual gestures and action menu triggers will STILL work on demand at all times.",
                            fontSize = 12.5.sp,
                            color = Color.LightGray.copy(alpha = 0.9f),
                            lineHeight = 18.sp,
                            textAlign = TextAlign.Start
                        )
                    },
                    confirmButton = {
                        TextButton(onClick = { showActionsIndependenceDialog = false }) {
                            Text("UNDERSTOOD", color = cautionAmber, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        }
                    },
                    containerColor = Color(0xFF1B1F2B),
                    shape = RoundedCornerShape(16.dp)
                )
            }

            // Operational Recommendation: Manual Actions Preferred
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.08f)),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.35f))
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(12.dp),
                    verticalAlignment = Alignment.Top
                ) {
                    Icon(
                        imageVector = Icons.Default.Lightbulb,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp).padding(top = 2.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Recommended: Manual Actions Preferred",
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.5.sp,
                            color = Color.White
                        )
                        Spacer(modifier = Modifier.height(3.dp))
                        Text(
                            text = "Keep this automatic engine toggled OFF for 0% battery usage and clean transitions. We recommend mapping the orientation actions ('Force Landscape', 'Force Portrait', '360° Gyro') directly to Deflector gestures or launching them from the System Actions menu on demand.",
                            fontSize = 11.sp,
                            color = Color.LightGray.copy(alpha = 0.9f),
                            lineHeight = 14.5.sp
                        )
                    }
                }
            }

            var isEngineEnabled by remember(prefs) {
                mutableStateOf(prefs.getBoolean(LightspeedPreferences.KEY_SYNTHETIC_GRAVITY_ENABLED, false))
            }
            DisposableEffect(prefs) {
                val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
                    if (key == LightspeedPreferences.KEY_SYNTHETIC_GRAVITY_ENABLED) {
                        isEngineEnabled = prefs.getBoolean(LightspeedPreferences.KEY_SYNTHETIC_GRAVITY_ENABLED, false)
                    }
                }
                prefs.registerOnSharedPreferenceChangeListener(listener)
                onDispose { prefs.unregisterOnSharedPreferenceChangeListener(listener) }
            }

            val currentEngineMode = prefs.getString(LightspeedPreferences.KEY_GRAVITY_ENFORCEMENT_ENGINE, LightspeedPreferences.GRAVITY_ENGINE_DUAL_HYBRID) ?: LightspeedPreferences.GRAVITY_ENGINE_DUAL_HYBRID
            var isEngineDropdownOpen by remember { mutableStateOf(false) }
            val engineOptions = listOf(
                LightspeedPreferences.GRAVITY_ENGINE_DUAL_HYBRID to "Dual Hybrid (Instant Settings + Window Anchor)",
                LightspeedPreferences.GRAVITY_ENGINE_SYSTEM_SETTINGS to "Settings.System Only (Instantaneous & 100% Leak-Free)",
                LightspeedPreferences.GRAVITY_ENGINE_WINDOW_ANCHOR to "Window Anchor Only (Strict Manifest Override)"
            )

            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 2.dp)
                    .clip(RoundedCornerShape(14.dp)),
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.4f))
            ) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                val newValue = !isEngineEnabled
                                isEngineEnabled = newValue
                                prefs.edit().putBoolean(LightspeedPreferences.KEY_SYNTHETIC_GRAVITY_ENABLED, newValue).apply()
                                LightspeedOrientationManager.evaluateGravityCascade(context)
                            }
                            .padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    "Enable Synthetic Gravity Engine",
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 14.5.sp,
                                    color = Color.White
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Box(
                                    modifier = Modifier
                                        .requiredSize(22.dp)
                                        .clip(CircleShape)
                                        .background(cautionAmber.copy(alpha = 0.15f))
                                        .border(0.8.dp, cautionAmber.copy(alpha = 0.4f), CircleShape)
                                        .clickable { showActionsIndependenceDialog = true },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        Icons.Default.PriorityHigh,
                                        contentDescription = "Actions Independence",
                                        tint = cautionAmber,
                                        modifier = Modifier.requiredSize(14.dp)
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                "Master switch for per-app automation buckets. Action menu items remain independent.",
                                fontSize = 11.5.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f)
                            )
                        }
                        Switch(
                            checked = isEngineEnabled,
                            onCheckedChange = null,
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = Color.White,
                                checkedTrackColor = MaterialTheme.colorScheme.primary,
                                checkedBorderColor = Color.Transparent,
                                uncheckedThumbColor = Color.White.copy(alpha = 0.75f),
                                uncheckedTrackColor = Color.White.copy(alpha = 0.12f),
                                uncheckedBorderColor = Color.White.copy(alpha = 0.25f)
                            )
                        )
                    }

                    // Divider and Enforcement Engine Driver directly inside the same card
                    HorizontalDivider(
                        color = Color.White.copy(alpha = 0.08f),
                        thickness = 1.dp,
                        modifier = Modifier.padding(horizontal = 14.dp)
                    )

                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = 14.dp, end = 14.dp, top = 10.dp, bottom = 14.dp)
                    ) {
                        Text(
                            "ENFORCEMENT ENGINE DRIVER",
                            fontSize = 10.5.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary,
                            letterSpacing = 0.8.sp
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "Choose how orientation is forced: 'Settings.System Only' writes directly to Android OS rotation (instantaneous, zero window allocations, completely leak-free). 'Dual Hybrid' additionally maintains an invisible 1x1 overlay anchor window to override stubborn locked manifests (like Infinix XOSLauncher).",
                            fontSize = 10.sp,
                            color = Color.LightGray.copy(alpha = 0.75f),
                            lineHeight = 13.sp
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Box {
                            OutlinedButton(
                                onClick = { isEngineDropdownOpen = true },
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(10.dp),
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = engineOptions.firstOrNull { it.first == currentEngineMode }?.second ?: "Dual Hybrid",
                                        color = Color.White,
                                        fontSize = 11.5.sp,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.weight(1f).padding(end = 8.dp)
                                    )
                                    Icon(
                                        imageVector = Icons.Default.ArrowDropDown,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            }
                            DropdownMenu(
                                expanded = isEngineDropdownOpen,
                                onDismissRequest = { isEngineDropdownOpen = false },
                                modifier = Modifier
                                    .background(Color(0xF012141A))
                                    .border(1.dp, Color(0x33FFFFFF), RoundedCornerShape(16.dp)),
                                shape = RoundedCornerShape(16.dp),
                                containerColor = Color(0xF012141A)
                            ) {
                                engineOptions.forEach { (key, label) ->
                                    DropdownMenuItem(
                                        modifier = Modifier.heightIn(min = 48.dp),
                                        text = { Text(label) },
                                        onClick = {
                                            isEngineDropdownOpen = false
                                            prefs.edit().putString(LightspeedPreferences.KEY_GRAVITY_ENFORCEMENT_ENGINE, key).apply()
                                            safeReloadPreferences()
                                            onRefreshNeeded()
                                        }
                                    )
                                }
                            }
                        }

                        // Dynamic Mode Breakdown: Pros & Cons
                        Spacer(modifier = Modifier.height(8.dp))
                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(10.dp),
                            color = Color.Black.copy(alpha = 0.25f),
                            border = BorderStroke(0.8.dp, Color.White.copy(alpha = 0.10f))
                        ) {
                            Column(modifier = Modifier.padding(10.dp)) {
                                when (currentEngineMode) {
                                    LightspeedPreferences.GRAVITY_ENGINE_SYSTEM_SETTINGS -> {
                                        Text("Settings.System Only", fontWeight = FontWeight.Bold, fontSize = 11.5.sp, color = MaterialTheme.colorScheme.primary)
                                        Spacer(modifier = Modifier.height(3.dp))
                                        Text("Mechanism: Directly toggles Android OS user_rotation & accelerometer_rotation. Zero overlay windows.", fontSize = 10.sp, color = Color.LightGray)
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Text("✓ Pros: Instantaneous, zero overlay allocations, 100% token leak-free.", fontSize = 10.sp, color = Color(0xFF81C784))
                                        Text("• Cons: Cannot rotate 180° inverted portrait (blocked by Android OS). Ignored by locked launchers (e.g. Infinix).", fontSize = 10.sp, color = Color(0xFFFFB74D))
                                    }
                                    LightspeedPreferences.GRAVITY_ENGINE_WINDOW_ANCHOR -> {
                                        Text("Window Anchor Only", fontWeight = FontWeight.Bold, fontSize = 11.5.sp, color = MaterialTheme.colorScheme.primary)
                                        Spacer(modifier = Modifier.height(3.dp))
                                        Text("Mechanism: Uses a 1x1 hardware accessibility overlay (screenOrientation) without touching system settings.", fontSize = 10.sp, color = Color.LightGray)
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Text("✓ Pros: Overrides hardcoded portrait manifests in locked launchers and games. Supports full 360° and 180°.", fontSize = 10.sp, color = Color(0xFF81C784))
                                        Text("• Cons: WindowManager surface transitions can feel laggy during rapid app switching.", fontSize = 10.sp, color = Color(0xFFFFB74D))
                                    }
                                    else -> { // Dual Hybrid
                                        Text("Dual Hybrid (Default)", fontWeight = FontWeight.Bold, fontSize = 11.5.sp, color = MaterialTheme.colorScheme.primary)
                                        Spacer(modifier = Modifier.height(3.dp))
                                        Text("Mechanism: Writes to Settings.System AND maintains the 1x1 hardware overlay window simultaneously.", fontSize = 10.sp, color = Color.LightGray)
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Text("✓ Pros: Maximum compatibility across stubborn Android OEM ROMs.", fontSize = 10.sp, color = Color(0xFF81C784))
                                        Text("• Cons: Higher transition overhead; two competing rotation signals can cause visible stutter.", fontSize = 10.sp, color = Color(0xFFFFB74D))
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // 1. Face-Oriented Auto-Rotate (CAMERA_AUTOROTATE)
            if (LightspeedOrientationEngine.isFaceRotateSupported(context)) {
                var showFaceRotatePrivacyDialog by remember { mutableStateOf(false) }
                if (showFaceRotatePrivacyDialog) {
                    AlertDialog(
                        onDismissRequest = { showFaceRotatePrivacyDialog = false },
                        icon = {
                            Icon(
                                imageVector = Icons.Default.Security,
                                contentDescription = null,
                                tint = cautionAmber,
                                modifier = Modifier.size(28.dp)
                            )
                        },
                        title = {
                            Text(
                                text = "FACE ORIENTATION & PRIVACY",
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White,
                                textAlign = TextAlign.Center
                            )
                        },
                        text = {
                            Text(
                                text = "• Native Android OS Feature (API 31+):\nManaged directly by Android's on-device Private Compute Core sensor subsystem.\n\n• Zero Camera Permissions:\nLightspeed does NOT request or hold camera permission (android.permission.CAMERA is not even declared in the app). Lightspeed only toggles the system setting (Settings.Secure.camera_autorotate).\n\n• 100% Offline & Private:\nZero photos, video feeds, or biometric data are ever accessed, captured, or transmitted. 100% offline.",
                                fontSize = 12.5.sp,
                                color = Color.LightGray.copy(alpha = 0.9f),
                                lineHeight = 18.sp,
                                textAlign = TextAlign.Start
                            )
                        },
                        confirmButton = {
                            TextButton(onClick = { showFaceRotatePrivacyDialog = false }) {
                                Text("UNDERSTOOD", color = cautionAmber, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                            }
                        },
                        containerColor = Color(0xFF1B1F2B),
                        shape = RoundedCornerShape(16.dp)
                    )
                }

                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 2.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .clickable {
                            val ok = LightspeedOrientationEngine.setFaceRotateEnabled(context, !isFaceRotateActive)
                            if (ok) {
                                isFaceRotateActive = !isFaceRotateActive
                            } else {
                                Toast.makeText(context, "Elevated permission needed. Opening system settings...", Toast.LENGTH_SHORT).show()
                                LightspeedOrientationEngine.openAutoRotateSettings(context)
                            }
                            onRefreshNeeded()
                        },
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.3f))
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Face, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Face-Oriented Auto-Rotate", modifier = Modifier.weight(1f, fill = false), fontWeight = FontWeight.SemiBold, fontSize = 13.sp, color = Color.White)
                                Spacer(modifier = Modifier.width(8.dp))
                                Box(
                                    modifier = Modifier
                                        .requiredSize(22.dp)
                                        .clip(CircleShape)
                                        .background(cautionAmber.copy(alpha = 0.15f))
                                        .border(0.8.dp, cautionAmber.copy(alpha = 0.4f), CircleShape)
                                        .clickable { showFaceRotatePrivacyDialog = true },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        Icons.Default.Info,
                                        contentDescription = "Privacy Architecture",
                                        tint = cautionAmber,
                                        modifier = Modifier.requiredSize(14.dp)
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.height(2.dp))
                            Text("Native OS sensor posture check. Lightspeed requires 0 camera permissions (100% offline & private).", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f))
                        }
                        Switch(
                            checked = isFaceRotateActive,
                            onCheckedChange = null,
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = Color.White,
                                checkedTrackColor = MaterialTheme.colorScheme.primary,
                                checkedBorderColor = Color.Transparent,
                                uncheckedThumbColor = Color.White.copy(alpha = 0.75f),
                                uncheckedTrackColor = Color.White.copy(alpha = 0.12f),
                                uncheckedBorderColor = Color.White.copy(alpha = 0.25f)
                            )
                        )
                    }
                }
            }

            // 2. Attitude Mode Buckets (Per-App Rules)
            Column(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("ATTITUDE MODE BUCKETS (PER-APP RULES)", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary, letterSpacing = 1.sp)
                Text("All apps default to native Auto-Rotate (on/off) above, unless assigned to a bucket below:", fontSize = 11.sp, color = Color.LightGray.copy(alpha = 0.8f))

                val buckets = listOf(
                    LightspeedOrientationEngine.AttitudeBucket.STRICT_PORTRAIT,
                    LightspeedOrientationEngine.AttitudeBucket.SENSOR_PORTRAIT,
                    LightspeedOrientationEngine.AttitudeBucket.SENSOR_LANDSCAPE,
                    LightspeedOrientationEngine.AttitudeBucket.SENSOR_360
                )

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(buckets[0], buckets[1]).forEach { bucket ->
                        val assignedCount = remember(bucket, prefs.getStringSet(bucket.prefKey, null)) {
                            LightspeedOrientationEngine.getAssignedPackages(context, bucket).size
                        }
                        Card(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(12.dp))
                                .clickable { selectedAttitudeBucketForAppPicker = bucket }
                                .border(1.dp, Color.White.copy(alpha = 0.12f), RoundedCornerShape(12.dp)),
                            shape = RoundedCornerShape(12.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.5f))
                        ) {
                            Column(modifier = Modifier.fillMaxWidth().padding(10.dp)) {
                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = when (bucket) {
                                            LightspeedOrientationEngine.AttitudeBucket.STRICT_PORTRAIT -> Icons.Default.StayCurrentPortrait
                                            else -> Icons.Default.ScreenRotationAlt
                                        },
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Surface(
                                        shape = RoundedCornerShape(6.dp),
                                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
                                    ) {
                                        Text(
                                            text = "$assignedCount apps",
                                            fontSize = 9.5.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
                                        )
                                    }
                                }
                                Spacer(modifier = Modifier.height(6.dp))
                                Text(bucket.title, fontWeight = FontWeight.Bold, fontSize = 12.sp, color = Color.White)
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(bucket.subtitle, fontSize = 9.5.sp, color = Color.LightGray.copy(alpha = 0.7f), lineHeight = 12.sp)
                            }
                        }
                    }
                }

                val landscapeBucket = buckets[2]
                val assignedLandscapeCount = remember(landscapeBucket, prefs.getStringSet(landscapeBucket.prefKey, null)) {
                    LightspeedOrientationEngine.getAssignedPackages(context, landscapeBucket).size
                }
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .clickable { selectedAttitudeBucketForAppPicker = landscapeBucket }
                        .border(1.dp, Color.White.copy(alpha = 0.12f), RoundedCornerShape(12.dp)),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.5f))
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                            Icon(
                                imageVector = Icons.Default.StayCurrentLandscape,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Column {
                                Text(landscapeBucket.title, fontWeight = FontWeight.Bold, fontSize = 12.5.sp, color = Color.White)
                                Text(landscapeBucket.subtitle, fontSize = 10.sp, color = Color.LightGray.copy(alpha = 0.75f))
                            }
                        }
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
                        ) {
                            Text(
                                text = "$assignedLandscapeCount apps",
                                fontSize = 9.5.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
                            )
                        }
                    }
                }

                val sensor360Bucket = buckets[3]
                val assigned360Count = remember(sensor360Bucket, prefs.getStringSet(sensor360Bucket.prefKey, null)) {
                    LightspeedOrientationEngine.getAssignedPackages(context, sensor360Bucket).size
                }
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .clickable { selectedAttitudeBucketForAppPicker = sensor360Bucket }
                        .border(1.dp, Color.White.copy(alpha = 0.12f), RoundedCornerShape(12.dp)),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.5f))
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                            Icon(
                                imageVector = Icons.Default.ScreenRotation,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Column {
                                Text(sensor360Bucket.title, fontWeight = FontWeight.Bold, fontSize = 12.5.sp, color = Color.White)
                                Text(sensor360Bucket.subtitle, fontSize = 10.sp, color = Color.LightGray.copy(alpha = 0.75f))
                            }
                        }
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
                        ) {
                            Text(
                                text = "$assigned360Count apps",
                                fontSize = 9.5.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
                            )
                        }
                    }
                }
            }

            // 3. Orientation Policy & Display Suppression
            val currentOrientationPolicy = prefs.getString(LightspeedPreferences.KEY_ORIENTATION_OVERLAY_POLICY, "adaptive") ?: "adaptive"
            var isOrientationDropdownOpen by remember { mutableStateOf(false) }
            val orientationOptions = listOf(
                "adaptive" to "Adaptive (360° Follows All Rotations)",
                "portrait_only" to "Portrait Only (Auto-Hide in Landscape)"
            )

            Column(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                Text("ORIENTATION OVERLAY POLICY", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary, letterSpacing = 1.sp)
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = "Controls how gesture strips and overlays behave when rotating: 'Adaptive' keeps them visible across all angles, while 'Portrait Only' automatically hides them in landscape to leave video and gaming viewports unobstructed.",
                    fontSize = 11.sp,
                    color = Color.LightGray.copy(alpha = 0.8f),
                    lineHeight = 14.sp
                )
                Spacer(modifier = Modifier.height(6.dp))
                Box {
                    OutlinedButton(
                        onClick = { isOrientationDropdownOpen = true },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 10.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = orientationOptions.firstOrNull { it.first == currentOrientationPolicy }?.second ?: "Adaptive (360°)",
                                color = Color.White,
                                fontSize = 12.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f).padding(end = 8.dp)
                            )
                            Icon(
                                imageVector = Icons.Default.ArrowDropDown,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                    }
                    DropdownMenu(
                        expanded = isOrientationDropdownOpen,
                        onDismissRequest = { isOrientationDropdownOpen = false },
                        modifier = Modifier
                            .background(Color(0xF012141A))
                            .border(1.dp, Color(0x33FFFFFF), RoundedCornerShape(16.dp)),
                        shape = RoundedCornerShape(16.dp),
                        containerColor = Color(0xF012141A)
                    ) {
                        orientationOptions.forEach { (key, label) ->
                            DropdownMenuItem(
                                modifier = Modifier.heightIn(min = 48.dp),
                                text = { Text(label) },
                                onClick = {
                                    isOrientationDropdownOpen = false
                                    prefs.edit().putString(LightspeedPreferences.KEY_ORIENTATION_OVERLAY_POLICY, key).apply()
                                    safeReloadPreferences()
                                    onRefreshNeeded()
                                }
                            )
                        }
                    }
                }
            }

            // 4. Action Override Lifetime (Manual Gesture vs App Bucket)
            val currentOverrideExpiration = prefs.getString(
                LightspeedPreferences.KEY_ORIENTATION_OVERRIDE_EXPIRATION,
                LightspeedPreferences.DEFAULT_ORIENTATION_OVERRIDE_EXPIRATION
            ) ?: LightspeedPreferences.DEFAULT_ORIENTATION_OVERRIDE_EXPIRATION
            var isOverrideExpirationDropdownOpen by remember { mutableStateOf(false) }
            val overrideExpirationOptions = listOf(
                LightspeedPreferences.ORIENTATION_OVERRIDE_EXPIRATION_UNTIL_APP_SWITCH to "Until App Switch (Temporary)",
                LightspeedPreferences.ORIENTATION_OVERRIDE_EXPIRATION_UNTIL_SCREEN_OFF to "Until Screen Off / Lock",
                LightspeedPreferences.ORIENTATION_OVERRIDE_EXPIRATION_PERSISTENT to "Persistent (Won't Reset / Manual Only)",
                LightspeedPreferences.ORIENTATION_OVERRIDE_EXPIRATION_DISABLED to "Disabled (Action Won't Work / Buckets Only)"
            )

            Column(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                Text("MANUAL ACTION OVERRIDE DURATION", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary, letterSpacing = 1.sp)
                Text("When you trigger an on-the-fly orientation action via gestures or deflector, choose how long it stays active before returning to bucket/system defaults:", fontSize = 10.sp, color = Color.LightGray.copy(alpha = 0.75f), lineHeight = 13.sp)
                Spacer(modifier = Modifier.height(4.dp))
                Box {
                    OutlinedButton(
                        onClick = { isOverrideExpirationDropdownOpen = true },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 10.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = overrideExpirationOptions.firstOrNull { it.first == currentOverrideExpiration }?.second ?: "Persistent (Won't Reset / Manual Only)",
                                color = Color.White,
                                fontSize = 12.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f).padding(end = 8.dp)
                            )
                            Icon(
                                imageVector = Icons.Default.ArrowDropDown,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                    }
                    DropdownMenu(
                        expanded = isOverrideExpirationDropdownOpen,
                        onDismissRequest = { isOverrideExpirationDropdownOpen = false },
                        modifier = Modifier
                            .background(Color(0xF012141A))
                            .border(1.dp, Color(0x33FFFFFF), RoundedCornerShape(16.dp)),
                        shape = RoundedCornerShape(16.dp),
                        containerColor = Color(0xF012141A)
                    ) {
                        overrideExpirationOptions.forEach { (key, label) ->
                            DropdownMenuItem(
                                modifier = Modifier.heightIn(min = 48.dp),
                                text = { Text(label) },
                                onClick = {
                                    isOverrideExpirationDropdownOpen = false
                                    prefs.edit().putString(LightspeedPreferences.KEY_ORIENTATION_OVERRIDE_EXPIRATION, key).apply()
                                    onRefreshNeeded()
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}
