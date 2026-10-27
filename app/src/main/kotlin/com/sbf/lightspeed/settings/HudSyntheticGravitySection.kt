package com.sbf.lightspeed.settings

import android.content.Context
import com.sbf.lightspeed.system.logSwallowed
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
import com.sbf.lightspeed.system.*
import com.sbf.lightspeed.ui.theme.LightspeedStyle
import com.sbf.lightspeed.ui.theme.LightspeedTokens

@Composable
fun HudSyntheticGravitySection(
    context: Context,
    prefs: SharedPreferences,
    isExpanded: Boolean,
    onToggle: () -> Unit,
    onRefreshNeeded: () -> Unit
) {
    val lsColors = LightspeedStyle.colors
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
            try { context.contentResolver.unregisterContentObserver(observer) } catch (e: Exception) { logSwallowed("HudSensorGravityComponents", "HudSyntheticGravitySection:206", e) }
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

    val currentLanguageMode by LightspeedLanguageEngine.modeFlow.collectAsState()
    val (gravityTitle, gravitySub) = LightspeedLanguageEngine.resolvePair(LightspeedVocabulary.Key.GRAVITY_ENGINE, currentLanguageMode)

    CompactAccordionSection(
        title = gravityTitle,
        subtitle = gravitySub,
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
                        color = lsColors.textSecondary.copy(alpha = 0.9f),
                        lineHeight = 14.5.sp
                    )
                }
            }

            // 0. Permission Warning Interlock
            val hasPermission = remember(context) {
                LightspeedOrientationEngine.hasPermission(context)
            }
            if (!hasPermission) {
                DeckCard(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                    onClick = {
                        LightspeedOrientationEngine.requestWriteSettingsPermission(context)
                    }
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
                                color = lsColors.textPrimary
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                "Tap to allow Lightspeed to modify system settings so it can control device rotation and synthetic gravity.",
                                fontSize = 10.5.sp,
                                color = lsColors.textSecondary.copy(alpha = 0.85f),
                                lineHeight = 13.sp
                            )
                        }
                    }
                }
            }

            val cautionAmber = lsColors.accentWarn
            var showActionsIndependenceDialog by remember { mutableStateOf(false) }
            if (showActionsIndependenceDialog) {
                DeckDialog(
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
                            color = lsColors.textPrimary,
                            textAlign = TextAlign.Center
                        )
                    },
                    text = {
                        Text(
                            text = "• Transient Actions: Actions from the Action Selection Menu (such as 'Transient Sensor Portrait', 'Transient Landscape', 'Transient Portrait', and '360° Gyro') operate completely independently from this toggle.\n\n• Yields to Automation: They act as transient manual overrides that yield automatically back to the Synthetic Gravity Engine rules upon switching apps or screen-off, preventing hard-locks.\n\n• Toggle Scope: Turning this Synthetic Gravity Engine toggle OFF disables per-app automation buckets only. Manual gestures and action menu triggers will STILL work on demand at all times.",
                            fontSize = 12.5.sp,
                            color = lsColors.textSecondary.copy(alpha = 0.9f),
                            lineHeight = 18.sp,
                            textAlign = TextAlign.Start
                        )
                    },
                    confirmButton = {
                        DeckButton(
                            text = "UNDERSTOOD",
                            onClick = { showActionsIndependenceDialog = false },
                            isActive = true
                        )
                    }
                )
            }

            // Operational Recommendation: Manual Actions Preferred
            DeckCard(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp)
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
                            color = lsColors.textPrimary
                        )
                        Spacer(modifier = Modifier.height(3.dp))
                        Text(
                            text = "Keep this automatic engine toggled OFF for 0% battery usage and clean transitions. We recommend mapping the orientation actions ('Force Landscape', 'Force Portrait', '360° Gyro') directly to Deflector gestures or launching them from the System Actions menu on demand.",
                            fontSize = 11.sp,
                            color = lsColors.textSecondary.copy(alpha = 0.9f),
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

            DeckCard(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 2.dp)
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
                                    "Synthetic Gravity Engine",
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 14.5.sp,
                                    color = lsColors.textPrimary
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
                                checkedThumbColor = lsColors.textPrimary,
                                checkedTrackColor = MaterialTheme.colorScheme.primary,
                                checkedBorderColor = Color.Transparent,
                                uncheckedThumbColor = lsColors.textPrimary.copy(alpha = 0.75f),
                                uncheckedTrackColor = lsColors.textPrimary.copy(alpha = 0.12f),
                                uncheckedBorderColor = lsColors.textPrimary.copy(alpha = 0.25f)
                            )
                        )
                    }

                    // Divider and Enforcement Engine Driver directly inside the same card
                    HorizontalDivider(
                        color = lsColors.textPrimary.copy(alpha = 0.08f),
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
                            color = lsColors.textSecondary.copy(alpha = 0.75f),
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
                                        color = lsColors.textPrimary,
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
                            DeckThemedDropdownMenu(
                                expanded = isEngineDropdownOpen,
                                onDismissRequest = { isEngineDropdownOpen = false }
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
                            border = BorderStroke(0.8.dp, lsColors.textPrimary.copy(alpha = 0.10f))
                        ) {
                            Column(modifier = Modifier.padding(10.dp)) {
                                when (currentEngineMode) {
                                    LightspeedPreferences.GRAVITY_ENGINE_SYSTEM_SETTINGS -> {
                                        Text("Settings.System Only", fontWeight = FontWeight.Bold, fontSize = 11.5.sp, color = MaterialTheme.colorScheme.primary)
                                        Spacer(modifier = Modifier.height(3.dp))
                                        Text("Mechanism: Directly toggles Android OS user_rotation & accelerometer_rotation. Zero overlay windows.", fontSize = 10.sp, color = lsColors.textSecondary)
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Text("✓ Pros: Instantaneous, zero overlay allocations, 100% token leak-free.", fontSize = 10.sp, color = LightspeedTokens.StatusOkSoft)
                                        Text("• Cons: Cannot rotate 180° inverted portrait (blocked by Android OS). Ignored by locked launchers (e.g. Infinix).", fontSize = 10.sp, color = LightspeedTokens.StatusWarningOrange)
                                    }
                                    LightspeedPreferences.GRAVITY_ENGINE_WINDOW_ANCHOR -> {
                                        Text("Window Anchor Only", fontWeight = FontWeight.Bold, fontSize = 11.5.sp, color = MaterialTheme.colorScheme.primary)
                                        Spacer(modifier = Modifier.height(3.dp))
                                        Text("Mechanism: Uses a 1x1 hardware accessibility overlay (screenOrientation) without touching system settings.", fontSize = 10.sp, color = lsColors.textSecondary)
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Text("✓ Pros: Overrides hardcoded portrait manifests in locked launchers and games. Supports full 360° and 180°.", fontSize = 10.sp, color = LightspeedTokens.StatusOkSoft)
                                        Text("• Cons: WindowManager surface transitions can feel laggy during rapid app switching.", fontSize = 10.sp, color = LightspeedTokens.StatusWarningOrange)
                                    }
                                    else -> { // Dual Hybrid
                                        Text("Dual Hybrid (Default)", fontWeight = FontWeight.Bold, fontSize = 11.5.sp, color = MaterialTheme.colorScheme.primary)
                                        Spacer(modifier = Modifier.height(3.dp))
                                        Text("Mechanism: Writes to Settings.System AND maintains the 1x1 hardware overlay window simultaneously.", fontSize = 10.sp, color = lsColors.textSecondary)
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Text("✓ Pros: Maximum compatibility across stubborn Android OEM ROMs.", fontSize = 10.sp, color = LightspeedTokens.StatusOkSoft)
                                        Text("• Cons: Higher transition overhead; two competing rotation signals can cause visible stutter.", fontSize = 10.sp, color = LightspeedTokens.StatusWarningOrange)
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
                    DeckDialog(
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
                                color = lsColors.textPrimary,
                                textAlign = TextAlign.Center
                            )
                        },
                        text = {
                            Text(
                                text = "• Native Android OS Feature (API 31+):\nManaged directly by Android's on-device Private Compute Core sensor subsystem.\n\n• Zero Camera Permissions:\nLightspeed does NOT request or hold camera permission (android.permission.CAMERA is not even declared in the app). Lightspeed only toggles the system setting (Settings.Secure.camera_autorotate).\n\n• 100% Offline & Private:\nZero photos, video feeds, or biometric data are ever accessed, captured, or transmitted. 100% offline.",
                                fontSize = 12.5.sp,
                                color = lsColors.textSecondary.copy(alpha = 0.9f),
                                lineHeight = 18.sp,
                                textAlign = TextAlign.Start
                            )
                        },
                        confirmButton = {
                            DeckButton(
                                text = "UNDERSTOOD",
                                onClick = { showFaceRotatePrivacyDialog = false },
                                isActive = true
                            )
                        }
                    )
                }

                DeckCard(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 2.dp),
                    onClick = {
                        val ok = LightspeedOrientationEngine.setFaceRotateEnabled(context, !isFaceRotateActive)
                        if (ok) {
                            isFaceRotateActive = !isFaceRotateActive
                        } else {
                            Toast.makeText(context, "Elevated permission needed. Opening system settings...", Toast.LENGTH_SHORT).show()
                            LightspeedOrientationEngine.openAutoRotateSettings(context)
                        }
                        onRefreshNeeded()
                    }
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
                                Text("Face-Oriented Auto-Rotate", modifier = Modifier.weight(1f, fill = false), fontWeight = FontWeight.SemiBold, fontSize = 13.sp, color = lsColors.textPrimary)
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
                                checkedThumbColor = lsColors.textPrimary,
                                checkedTrackColor = MaterialTheme.colorScheme.primary,
                                checkedBorderColor = Color.Transparent,
                                uncheckedThumbColor = lsColors.textPrimary.copy(alpha = 0.75f),
                                uncheckedTrackColor = lsColors.textPrimary.copy(alpha = 0.12f),
                                uncheckedBorderColor = lsColors.textPrimary.copy(alpha = 0.25f)
                            )
                        )
                    }
                }
            }

            // 2. Attitude Mode Buckets (Per-App Rules)
            HudGravityBucketsSection(
                context = context,
                prefs = prefs,
                onBucketSelected = { selectedAttitudeBucketForAppPicker = it }
            )

            // 3. Orientation Policy, Override Duration & Manual Native Rotation Policy
            HudGravityPoliciesSection(
                prefs = prefs,
                onRefreshNeeded = onRefreshNeeded
            )
        }
    }
}
