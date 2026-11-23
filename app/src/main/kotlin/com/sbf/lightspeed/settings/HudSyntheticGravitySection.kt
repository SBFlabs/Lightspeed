package com.sbf.lightspeed.settings

import com.sbf.lightspeed.R

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
    onRefreshNeeded: () -> Unit,
    languageMode: LightspeedLanguageEngine.LanguageMode = LightspeedLanguageEngine.modeFlow.value
) {
    val lsColors = LightspeedStyle.colors
    val cautionAmber = lsColors.accentWarn
    var selectedAttitudeBucketForAppPicker by remember { mutableStateOf<LightspeedOrientationEngine.AttitudeBucket?>(null) }

    if (selectedAttitudeBucketForAppPicker != null) {
        AttitudeAppAssignmentSheet(
            context = context,
            bucket = selectedAttitudeBucketForAppPicker!!,
            onDismiss = { selectedAttitudeBucketForAppPicker = null },
            onUpdated = { onRefreshNeeded() }
        )
    }

    val (gravityTitle, gravitySub) = LightspeedLanguageEngine.resolvePair(LightspeedVocabulary.Key.GRAVITY_ENGINE, languageMode)
    var showGravityInfoDialog by remember { mutableStateOf(false) }

    if (showGravityInfoDialog) {
        DeckDialog(
            onDismissRequest = { showGravityInfoDialog = false },
            icon = {
                Icon(
                    imageVector = Icons.Default.Info,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(24.dp)
                )
            },
            title = {
                Text(
                    text = lsString(R.string.grav_title),
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    color = lsColors.textPrimary,
                    textAlign = TextAlign.Center
                )
            },
            text = {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        text = lsString(R.string.grav_b1),
                        fontSize = 11.5.sp,
                        color = lsColors.textSecondary.copy(alpha = 0.95f),
                        lineHeight = 15.5.sp
                    )
                    Text(
                        text = lsString(R.string.grav_b2),
                        fontSize = 11.5.sp,
                        color = lsColors.textSecondary.copy(alpha = 0.95f),
                        lineHeight = 15.5.sp
                    )
                    Text(
                        text = lsString(R.string.grav_b3),
                        fontSize = 11.5.sp,
                        color = lsColors.textSecondary.copy(alpha = 0.95f),
                        lineHeight = 15.5.sp
                    )
                }
            },
            confirmButton = {
                DeckButton(
                    text = lsString(R.string.cc_dlg_got_it_caps),
                    onClick = { showGravityInfoDialog = false },
                    isActive = true
                )
            }
        )
    }

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
        headerTrailing = {
            IconButton(
                onClick = { showGravityInfoDialog = true },
                modifier = Modifier.size(28.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Info,
                    contentDescription = "Gravity Information",
                    tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.85f),
                    modifier = Modifier.size(18.dp)
                )
            }
        },
        isExpanded = isExpanded,
        onToggle = onToggle
    ) {
        var isAutoRotateActive by remember { mutableStateOf(LightspeedOrientationEngine.isAutoRotateEnabled(context)) }
        var isFaceRotateActive by remember { mutableStateOf(LightspeedOrientationEngine.isFaceRotateEnabled(context)) }
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
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
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
                LightspeedPreferences.GRAVITY_ENGINE_DUAL_HYBRID to lsString(R.string.grav_dual),
                LightspeedPreferences.GRAVITY_ENGINE_SYSTEM_SETTINGS to lsString(R.string.grav_sys_only),
                LightspeedPreferences.GRAVITY_ENGINE_WINDOW_ANCHOR to lsString(R.string.grav_win_only)
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
                                        .clickable { showGravityInfoDialog = true },
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
                                lsString(R.string.grav_master_sub),
                                fontSize = 11.5.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f)
                            )
                        }
                        Switch(
                            checked = isEngineEnabled,
                            onCheckedChange = null,
                            colors = deckSwitchColors()
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
                            lsString(R.string.grav_driver_hdr),
                            fontSize = 10.5.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary,
                            letterSpacing = 0.8.sp
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = lsString(R.string.grav_driver_desc),
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
                                        text = engineOptions.firstOrNull { it.first == currentEngineMode }?.second ?: lsString(R.string.grav_dual_short),
                                        color = lsColors.textPrimary,
                                        fontSize = 11.5.sp,
                                        lineHeight = 14.5.sp,
                                        maxLines = 2,
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
                                        Text(lsString(R.string.grav_sys_short), fontWeight = FontWeight.Bold, fontSize = 11.5.sp, color = MaterialTheme.colorScheme.primary)
                                        Spacer(modifier = Modifier.height(3.dp))
                                        Text("Mechanism: Directly toggles Android OS user_rotation & accelerometer_rotation. Zero overlay windows.", fontSize = 10.sp, color = lsColors.textSecondary)
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Text("✓ Pros: Instantaneous, zero overlay allocations, 100% token leak-free.", fontSize = 10.sp, color = LightspeedTokens.StatusOkSoft)
                                        Text("• Cons: Cannot rotate 180° inverted portrait (blocked by Android OS). Ignored by locked launchers (e.g. Infinix).", fontSize = 10.sp, color = LightspeedTokens.StatusWarningOrange)
                                    }
                                    LightspeedPreferences.GRAVITY_ENGINE_WINDOW_ANCHOR -> {
                                        Text(lsString(R.string.grav_win_short), fontWeight = FontWeight.Bold, fontSize = 11.5.sp, color = MaterialTheme.colorScheme.primary)
                                        Spacer(modifier = Modifier.height(3.dp))
                                        Text("Mechanism: Uses a 1x1 hardware accessibility overlay (screenOrientation) without touching system settings.", fontSize = 10.sp, color = lsColors.textSecondary)
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Text("✓ Pros: Overrides hardcoded portrait manifests in locked launchers and games. Supports full 360° and 180°.", fontSize = 10.sp, color = LightspeedTokens.StatusOkSoft)
                                        Text("• Cons: WindowManager surface transitions can feel laggy during rapid app switching.", fontSize = 10.sp, color = LightspeedTokens.StatusWarningOrange)
                                    }
                                    else -> { // Dual Hybrid
                                        Text(lsString(R.string.grav_dual_def), fontWeight = FontWeight.Bold, fontSize = 11.5.sp, color = MaterialTheme.colorScheme.primary)
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
                                text = lsString(R.string.grav_face_hdr),
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
                            Toast.makeText(context, lsString(R.string.grav_perm_toast), Toast.LENGTH_SHORT).show()
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
                                Text(lsString(R.string.grav_face_title), modifier = Modifier.weight(1f, fill = false), fontWeight = FontWeight.SemiBold, fontSize = 13.sp, color = lsColors.textPrimary)
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
                            Text(lsString(R.string.grav_face_sub), fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f))
                        }
                        Switch(
                            checked = isFaceRotateActive,
                            onCheckedChange = null,
                            colors = deckSwitchColors()
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
