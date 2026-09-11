package com.sbf.lightspeed.settings

import android.app.Activity
import android.content.Context
import android.content.SharedPreferences
import android.widget.Toast
import kotlinx.coroutines.withContext
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material.icons.automirrored.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.Image
import androidx.compose.ui.text.style.TextOverflow
import android.content.Intent
import com.sbf.lightspeed.LightspeedAccessibilityService
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import com.sbf.lightspeed.system.LightspeedBackTapEngine
import com.sbf.lightspeed.system.LightspeedBackupEngine
import com.sbf.lightspeed.system.LightspeedHapticEngine
import com.sbf.lightspeed.system.LightspeedKeyEngine
import com.sbf.lightspeed.system.LightspeedOrientationEngine
import com.sbf.lightspeed.system.LightspeedPreferences
import com.sbf.lightspeed.system.OemNotchDetector
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch

@Composable
fun HudStripTabContent(
    blueprintTabTargetState: MutableState<Int?>,
    context: android.content.Context,
    currentThresholdState: MutableFloatState,
    currentZImpulseState: MutableFloatState,
    dynamicActionTokens: List<String>,
    isConfigVaultExpandedState: MutableState<Boolean>,
    isExperimentalLabsExpandedState: MutableState<Boolean>,
    isHorizonRailColorExpandedState: MutableState<Boolean>,
    isHorizonRailGeomExpandedState: MutableState<Boolean>,
    isHorizonRailTextExpandedState: MutableState<Boolean>,
    isMarqueeSubSectionExpandedState: MutableState<Boolean>,
    isNotchCalibExpandedState: MutableState<Boolean>,
    isOemNoticeDemotedState: MutableState<Boolean>,
    isRefuelingExpandedState: MutableState<Boolean>,
    isSensorDeckExpandedState: MutableState<Boolean>,
    isSinglePressUnlockedState: MutableState<Boolean>,
    isStatusBarGeoExpandedState: MutableState<Boolean>,
    isStatusBarGesturesExpandedState: MutableState<Boolean>,
    isSubHullTapExpandedState: MutableState<Boolean>,
    isSubPowerExpandedState: MutableState<Boolean>,
    isSubVolumeExpandedState: MutableState<Boolean>,
    isSyntheticGravityExpandedState: MutableState<Boolean>,
    isTacticalHardwareExpandedState: MutableState<Boolean>,
    isTelemetryExpandedState: MutableState<Boolean>,
    listState1: androidx.compose.foundation.lazy.LazyListState,
    onRefreshNeeded: () -> Unit,
    pendingBackTapScopeState: MutableState<String>,
    pinnedSection1State: MutableState<String>,
    prefs: android.content.SharedPreferences,
    sectionOrder1StrState: MutableState<String>,
    sectionTitles1: Map<String, String>,
    showAmoledWarningDialogState: MutableState<Boolean>,
    showBatteryWarningDialogState: MutableState<Boolean>,
    showImportOptionsDialogState: MutableState<Boolean>,
    showNotificationAccessDialogState: MutableState<Boolean>,
    showOemShieldDialogState: MutableState<Boolean>,
    showResetConfirmDialogState: MutableState<Boolean>,
    singlePressTapCountState: MutableIntState,
    thresholdCrossedFlashState: MutableState<Boolean>,
    toggleSection: (Int, String, Boolean, (Boolean) -> Unit) -> Unit,
    tokenLabelCache: Map<String, String>
) {
    var blueprintTabTarget by blueprintTabTargetState
    var currentThreshold by currentThresholdState
    var currentZImpulse by currentZImpulseState
    var isConfigVaultExpanded by isConfigVaultExpandedState
    var isExperimentalLabsExpanded by isExperimentalLabsExpandedState
    var isHorizonRailColorExpanded by isHorizonRailColorExpandedState
    var isHorizonRailGeomExpanded by isHorizonRailGeomExpandedState
    var isHorizonRailTextExpanded by isHorizonRailTextExpandedState
    var isMarqueeSubSectionExpanded by isMarqueeSubSectionExpandedState
    var isNotchCalibExpanded by isNotchCalibExpandedState
    var isOemNoticeDemoted by isOemNoticeDemotedState
    var isRefuelingExpanded by isRefuelingExpandedState
    var isSensorDeckExpanded by isSensorDeckExpandedState
    var isSinglePressUnlocked by isSinglePressUnlockedState
    var isStatusBarGeoExpanded by isStatusBarGeoExpandedState
    var isStatusBarGesturesExpanded by isStatusBarGesturesExpandedState
    var isSubHullTapExpanded by isSubHullTapExpandedState
    var isSubPowerExpanded by isSubPowerExpandedState
    var isSubVolumeExpanded by isSubVolumeExpandedState
    var isSyntheticGravityExpanded by isSyntheticGravityExpandedState
    var isTacticalHardwareExpanded by isTacticalHardwareExpandedState
    var isTelemetryExpanded by isTelemetryExpandedState
    var pendingBackTapScope by pendingBackTapScopeState
    var pinnedSection1 by pinnedSection1State
    var sectionOrder1Str by sectionOrder1StrState
    var showAmoledWarningDialog by showAmoledWarningDialogState
    var showBatteryWarningDialog by showBatteryWarningDialogState
    var showImportOptionsDialog by showImportOptionsDialogState
    var showNotificationAccessDialog by showNotificationAccessDialogState
    var showOemShieldDialog by showOemShieldDialogState
    var showResetConfirmDialog by showResetConfirmDialogState
    var singlePressTapCount by singlePressTapCountState
    var thresholdCrossedFlash by thresholdCrossedFlashState

    val oemFeatureName = remember { com.sbf.lightspeed.system.OemNotchDetector.getDetectedFeatureName() }
    val installedTacticalTools by produceState(initialValue = emptyList<com.sbf.lightspeed.system.TacticalToolItem>()) {
        value = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            com.sbf.lightspeed.system.InstalledTacticalToolsScanner.scan(context)
        }
    }
    val scope = rememberCoroutineScope()
    val exportLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        contract = androidx.activity.result.contract.ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        if (uri != null) {
            scope.launch {
                val result = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                    com.sbf.lightspeed.system.LightspeedBackupEngine.exportToFile(context, uri)
                }
                result.onSuccess { count ->
                    android.widget.Toast.makeText(context, "Successfully exported $count settings to backup!", android.widget.Toast.LENGTH_SHORT).show()
                }.onFailure { err ->
                    android.widget.Toast.makeText(context, "Failed to export backup: ${err.message ?: err.javaClass.simpleName}", android.widget.Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

                    val defaultOrder1 = listOf("sensor_deck", "synthetic_gravity", "telemetry_indicators", "tactical_hardware", "refueling_bay", "config_vault", "experimental_labs")
                    val currentOrder1 = sectionOrder1Str.split(",").map { it.trim() }.filter { it in defaultOrder1 }.distinct().let { list ->
                        list + (defaultOrder1 - list.toSet())
                    }

                    if (blueprintTabTarget == 1) {
                        BlueprintWireframeView(
                            tabTitle = "HUD STRIP",
                            sectionIds = currentOrder1,
                            pinnedSectionId = pinnedSection1,
                            sectionTitles = sectionTitles1,
                            onMoveUp = { idx: Int ->
                                if (idx > 0) {
                                    val mutable = currentOrder1.toMutableList()
                                    val item = mutable.removeAt(idx)
                                    mutable.add(idx - 1, item)
                                    val newStr = mutable.joinToString(",")
                                    sectionOrder1Str = newStr
                                    prefs.edit().putString(LightspeedPreferences.KEY_TAB_SECTION_ORDER_1, newStr).apply()
                                }
                            },
                            onMoveDown = { idx: Int ->
                                if (idx < currentOrder1.size - 1) {
                                    val mutable = currentOrder1.toMutableList()
                                    val item = mutable.removeAt(idx)
                                    mutable.add(idx + 1, item)
                                    val newStr = mutable.joinToString(",")
                                    sectionOrder1Str = newStr
                                    prefs.edit().putString(LightspeedPreferences.KEY_TAB_SECTION_ORDER_1, newStr).apply()
                                }
                            },
                            onPinSection = { secId: String ->
                                pinnedSection1 = secId
                                prefs.edit().putString(LightspeedPreferences.KEY_TAB_PINNED_ACCORDION_1, secId).apply()
                            },
                            onExitBlueprint = {
                                blueprintTabTarget = null
                            }
                        )
                    } else {
                        LazyColumn(
                            state = listState1,
                            modifier = Modifier.fillMaxSize(),
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                            contentPadding = PaddingValues(bottom = 20.dp)
                        ) {
                            currentOrder1.forEach { sectionId ->
                                when (sectionId) {
                                    "sensor_deck" -> {
                                        item(key = "sensor_deck") {
                                            CompactAccordionSection(
                                                title = com.sbf.lightspeed.system.LightspeedLanguageEngine.resolve(com.sbf.lightspeed.system.LightspeedVocabulary.Key.SENSOR_AREA),
                                                icon = {
                                                    Icon(
                                                        imageVector = Icons.Outlined.TouchApp,
                                                        contentDescription = null,
                                                        tint = MaterialTheme.colorScheme.primary,
                                                        modifier = Modifier.size(20.dp)
                                                    )
                                                },
                                                isExpanded = isSensorDeckExpanded,
                                                onToggle = {
                                                    toggleSection(1, "sensor_deck", isSensorDeckExpanded) { isSensorDeckExpanded = it }
                                                    prefs.edit()
                                                        .putBoolean("pref_section_statusbar_expanded", isSensorDeckExpanded)
                                                        .putBoolean("pref_statusbar_preview", isSensorDeckExpanded && isStatusBarGeoExpanded)
                                                        .apply()
                                                    try { LightspeedAccessibilityService.instance?.reloadPreferences() } catch (_: Exception) {}
                                                }
                                            ) {
                                                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                                    PrefToggleRow(
                                                        prefs = prefs,
                                                        prefKey = "pref_statusbar_enabled",
                                                        defaultVal = true,
                                                        title = "Enable Sensor Area Gestures",
                                                        subtitle = "Top-edge gesture detection",
                                                        onChanged = { onRefreshNeeded() }
                                                    )

                                                    // 1. Geometry & Sensitivity
                                                    CollapsibleSubSection(
                                                        title = "Geometry & Sensitivity",
                                                        subtitle = "Span, thickness, offsets & idle glow",
                                                        isExpanded = isStatusBarGeoExpanded,
                                                        onToggle = {
                                                            isStatusBarGeoExpanded = !isStatusBarGeoExpanded
                                                            prefs.edit()
                                                                .putBoolean("pref_sub_geo_statusbar", isStatusBarGeoExpanded)
                                                                .putBoolean("pref_statusbar_preview", isStatusBarGeoExpanded)
                                                                .apply()
                                                            try { LightspeedAccessibilityService.instance?.reloadPreferences() } catch (_: Exception) {}
                                                            onRefreshNeeded()
                                                        }
                                                    ) {
                                                        PrefDottedSliderRow(context, prefs, "pref_statusbar_span", "", "Span (≥1000 = Full Width)", 50, 1080, 10, 1080)
                                                        PrefDottedSliderRow(context, prefs, "pref_statusbar_thickness", "", "Thickness", 20, 52, 2, 48)
                                                        PrefDottedSliderRow(context, prefs, "pref_statusbar_offset_x", "", "Horizontal Offset (X Axis)", -300, 300, 5, 0)
                                                        PrefDottedSliderRow(context, prefs, "pref_statusbar_offset_y", "", "Vertical Offset (Y Axis)", -100, 200, 5, 0)
                                                        PrefDottedSliderRow(context, prefs, "pref_statusbar_sensitivity", "", "Touch Sensitivity", 10, 100, 5, 40)
                                                        PrefDottedSliderRow(context, prefs, "pref_statusbar_transparency", "", "Stealth Idle Glow", 0, 100, 5, 0)
                                                    }

                                                    // 2. Gestures
                                                    CollapsibleSubSection(
                                                        title = "Gestures & Macros",
                                                        subtitle = "Tap, double-tap, left & right swipes with Hold Modifiers",
                                                        isExpanded = isStatusBarGesturesExpanded,
                                                        onToggle = {
                                                            isStatusBarGesturesExpanded = !isStatusBarGesturesExpanded
                                                            prefs.edit().putBoolean("pref_sub_gestures_statusbar", isStatusBarGesturesExpanded).apply()
                                                        }
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
                                                        GestureMappingRow(
                                                            context, prefs, ArrowDirection.SCRUB, false,
                                                            "pref_macro_action_STATUSBAR_SCRUBBING",
                                                            "Sensor Deck Long Sweep (Scrubbing)",
                                                            listOf("none", "system:screen_timeout", "system:volume", "system:brightness"),
                                                            tokenLabelCache
                                                        )
                                                    }
                                                }
                                            }
                                        }
                                    }
                                    "synthetic_gravity" -> {
                                        item(key = "synthetic_gravity") {
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
                                                title = com.sbf.lightspeed.system.LightspeedLanguageEngine.resolve(com.sbf.lightspeed.system.LightspeedVocabulary.Key.GRAVITY_ENGINE),
                                                icon = {
                                                    Icon(
                                                        imageVector = Icons.Outlined.Rotate90DegreesCw,
                                                        contentDescription = null,
                                                        tint = MaterialTheme.colorScheme.primary,
                                                        modifier = Modifier.size(20.dp)
                                                    )
                                                },
                                                isExpanded = isSyntheticGravityExpanded,
                                                onToggle = {
                                                    toggleSection(1, "synthetic_gravity", isSyntheticGravityExpanded) { isSyntheticGravityExpanded = it }
                                                    prefs.edit()
                                                        .putBoolean(LightspeedPreferences.KEY_SECTION_SYNTHETIC_GRAVITY_EXPANDED, isSyntheticGravityExpanded)
                                                        .apply()
                                                    try { LightspeedAccessibilityService.instance?.reloadPreferences() } catch (_: Exception) {}
                                                }
                                            ) {
                                                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
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
                                                            border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.5f))
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

                                                    // Master Auto-Rotate Toggle
                                                    PrefToggleRow(
                                                        title = "Auto-Rotate Master Switch",
                                                        subtitle = "Global Android display rotation controller",
                                                        isChecked = isAutoRotateActive,
                                                        onCheckedChange = { checked ->
                                                            val ok = LightspeedOrientationEngine.setAutoRotateEnabled(context, checked)
                                                            if (ok) {
                                                                isAutoRotateActive = checked
                                                            } else {
                                                                android.widget.Toast.makeText(context, "Elevated permission needed. Opening system settings...", android.widget.Toast.LENGTH_SHORT).show()
                                                                LightspeedOrientationEngine.openAutoRotateSettings(context)
                                                            }
                                                            onRefreshNeeded()
                                                        }
                                                    )

                                                    // 1. Face-Oriented Auto-Rotate (CAMERA_AUTOROTATE)
                                                    if (LightspeedOrientationEngine.isFaceRotateSupported(context)) {
                                                        val cautionAmber = Color(0xFFFFB300)
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
                                                                        android.widget.Toast.makeText(context, "Elevated permission needed. Opening system settings...", android.widget.Toast.LENGTH_SHORT).show()
                                                                        LightspeedOrientationEngine.openAutoRotateSettings(context)
                                                                    }
                                                                    onRefreshNeeded()
                                                                },
                                                            shape = RoundedCornerShape(12.dp),
                                                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)),
                                                            border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.3f))
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
                                                                        Text("Face-Oriented Auto-Rotate", fontWeight = FontWeight.SemiBold, fontSize = 13.sp, color = Color.White)
                                                                        Spacer(modifier = Modifier.width(6.dp))
                                                                        Box(
                                                                            modifier = Modifier
                                                                                .size(18.dp)
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
                                                                                modifier = Modifier.size(12.dp)
                                                                            )
                                                                        }
                                                                    }
                                                                    Spacer(modifier = Modifier.height(2.dp))
                                                                    Text("Native OS sensor posture check. Lightspeed requires 0 camera permissions (100% offline & private).", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f))
                                                                }
                                                                Switch(
                                                                    checked = isFaceRotateActive,
                                                                    onCheckedChange = {
                                                                        val ok = LightspeedOrientationEngine.setFaceRotateEnabled(context, it)
                                                                        if (ok) {
                                                                            isFaceRotateActive = it
                                                                        } else {
                                                                            android.widget.Toast.makeText(context, "Elevated permission needed. Opening system settings...", android.widget.Toast.LENGTH_SHORT).show()
                                                                            LightspeedOrientationEngine.openAutoRotateSettings(context)
                                                                        }
                                                                        onRefreshNeeded()
                                                                    },
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
                                                        Spacer(modifier = Modifier.height(4.dp))
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
                                                                            try { LightspeedAccessibilityService.instance?.reloadPreferences() } catch (_: Exception) {}
                                                                            onRefreshNeeded()
                                                                        }
                                                                    )
                                                                }
                                                            }
                                                        }
                                                    }

                                                    // 4. Action Override Lifetime (Manual Gesture vs App Bucket)
                                                    val currentOverrideExpiration = prefs.getString(LightspeedPreferences.KEY_ORIENTATION_OVERRIDE_EXPIRATION, "until_app_switch") ?: "until_app_switch"
                                                    var isOverrideExpirationDropdownOpen by remember { mutableStateOf(false) }
                                                    val overrideExpirationOptions = listOf(
                                                        "until_app_switch" to "Until App Switch (Temporary)",
                                                        "until_screen_off" to "Until Screen Off / Lock",
                                                        "persistent" to "Persistent (Won't Reset / Manual Only)",
                                                        "disabled" to "Disabled (Action Won't Work / Buckets Only)"
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
                                                                        text = overrideExpirationOptions.firstOrNull { it.first == currentOverrideExpiration }?.second ?: "Until App Switch",
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
                                    }
                                    "telemetry_indicators" -> {
                                        item(key = "telemetry_indicators") {
                                            CompactAccordionSection(
                                                title = com.sbf.lightspeed.system.LightspeedLanguageEngine.resolve(com.sbf.lightspeed.system.LightspeedVocabulary.Key.TELEMETRY_AND_INDICATORS),
                                                icon = {
                                                    Icon(
                                                        imageVector = Icons.Outlined.Speed,
                                                        contentDescription = null,
                                                        tint = MaterialTheme.colorScheme.primary,
                                                        modifier = Modifier.size(20.dp)
                                                    )
                                                },
                                                isExpanded = isTelemetryExpanded,
                                                onToggle = {
                                                    toggleSection(1, "telemetry_indicators", isTelemetryExpanded) { isTelemetryExpanded = it }
                                                    val anyRail = isHorizonRailGeomExpanded || isHorizonRailColorExpanded || isHorizonRailTextExpanded
                                                    val railActive = isTelemetryExpanded && anyRail
                                                    val notchActive = isTelemetryExpanded && isNotchCalibExpanded
                                                    prefs.edit()
                                                        .putBoolean("pref_section_telemetry_expanded", isTelemetryExpanded)
                                                        .putBoolean(LightspeedPreferences.KEY_HORIZON_RAIL_PREVIEW, railActive)
                                                        .putBoolean(LightspeedPreferences.KEY_NOTCH_TEST_BEACON, notchActive)
                                                        .apply()
                                                    try { LightspeedAccessibilityService.instance?.reloadPreferences() } catch (_: Exception) {}
                                                }
                                            ) {
                                                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                                    val isNotifAccessGranted = remember(isTelemetryExpanded) {
                                                        val pkgName = context.packageName
                                                        val flat = android.provider.Settings.Secure.getString(context.contentResolver, "enabled_notification_listeners")
                                                        flat?.contains(pkgName) == true || com.sbf.lightspeed.system.LightspeedNotificationListener.instance != null
                                                    }

                                                    // Visual Dual-Channel HUD Guide Badge Card
                                                    Card(
                                                        modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
                                                        shape = RoundedCornerShape(12.dp),
                                                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)),
                                                        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.3f))
                                                    ) {
                                                        Column(modifier = Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                                Icon(Icons.Default.Speed, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp))
                                                                Spacer(modifier = Modifier.width(6.dp))
                                                                Text("DUAL-CHANNEL TELEMETRY HUD", fontWeight = FontWeight.Bold, fontSize = 11.5.sp, color = MaterialTheme.colorScheme.primary, letterSpacing = 0.8.sp)
                                                            }
                                                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                                                Column(modifier = Modifier.weight(1f)) {
                                                                    Text("📏 Horizon Rail", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = Color.White)
                                                                    Text("Ultra-thin progress line on display top edge", fontSize = 10.5.sp, color = Color.LightGray.copy(alpha = 0.85f))
                                                                }
                                                                Column(modifier = Modifier.weight(1f)) {
                                                                    Text("💊 Orbital Capsule", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = Color.White)
                                                                    Text("Dynamic liquid-glass island on camera cutout", fontSize = 10.5.sp, color = Color.LightGray.copy(alpha = 0.85f))
                                                                }
                                                            }
                                                        }
                                                    }

                                                    PrefToggleRow(
                                                        prefs = prefs,
                                                        prefKey = LightspeedPreferences.KEY_HIDE_ON_LOCKSCREEN_AND_DOCK,
                                                        defaultVal = true,
                                                        title = "Suppress on Lock Screen & OEM Screensavers",
                                                        subtitle = "Automatically hides Orbital Capsule and HUD Strip when device is locked or running OEM ambient dock.",
                                                        onChanged = { onRefreshNeeded() }
                                                    )

                                                    if (!isNotifAccessGranted) {
                                                        Card(
                                                            modifier = Modifier
                                                                .fillMaxWidth()
                                                                .clip(RoundedCornerShape(12.dp))
                                                                .clickable { showNotificationAccessDialog = true }
                                                                .padding(vertical = 2.dp),
                                                            shape = RoundedCornerShape(12.dp),
                                                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.35f)),
                                                            border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.5f))
                                                        ) {
                                                            Row(
                                                                modifier = Modifier.fillMaxWidth().padding(12.dp),
                                                                verticalAlignment = Alignment.CenterVertically
                                                            ) {
                                                                Icon(Icons.Default.NotificationsActive, contentDescription = null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(22.dp))
                                                                Spacer(modifier = Modifier.width(10.dp))
                                                                Column(modifier = Modifier.weight(1f)) {
                                                                    Text("Notification Access Required", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = Color.White)
                                                                    Text("Tap here to grant permission in Android Settings so Lightspeed can read download progress and media metadata.", fontSize = 11.sp, color = Color.White.copy(alpha = 0.85f))
                                                                }
                                                                Icon(Icons.Default.ChevronRight, contentDescription = null, tint = Color.White.copy(alpha = 0.6f))
                                                            }
                                                        }
                                                    }

                                                    // Downloads Telemetry Selector
                                                    val currentDl = prefs.getString(LightspeedPreferences.KEY_RAIL_DOWNLOADS_ROUTING, "notch_pill") ?: "notch_pill"
                                                    var isDlDropdownOpen by remember { mutableStateOf(false) }
                                                    val routingOptions = listOf(
                                                        "none" to "None (Disabled)",
                                                        "top_line" to "Horizon Rail (Top-Edge Line)",
                                                        "notch_pill" to "Orbital Capsule (Camera Cutout)",
                                                        "both" to "Both (Horizon Rail & Orbital Capsule)"
                                                    )

                                                    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                                                        Text("DOWNLOADS TELEMETRY ROUTING", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary, letterSpacing = 1.sp)
                                                        Spacer(modifier = Modifier.height(4.dp))
                                                        Box {
                                                            OutlinedButton(
                                                                onClick = { isDlDropdownOpen = true },
                                                                modifier = Modifier.fillMaxWidth(),
                                                                shape = RoundedCornerShape(12.dp)
                                                            ) {
                                                                Row(
                                                                    modifier = Modifier.fillMaxWidth(),
                                                                    horizontalArrangement = Arrangement.SpaceBetween,
                                                                    verticalAlignment = Alignment.CenterVertically
                                                                ) {
                                                                    Text(routingOptions.firstOrNull { it.first == currentDl }?.second ?: "Orbital Capsule (Camera Cutout)", color = Color.White)
                                                                    Icon(Icons.Default.ArrowDropDown, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                                                                }
                                                            }
                                                            DropdownMenu(
                                                                expanded = isDlDropdownOpen,
                                                                onDismissRequest = { isDlDropdownOpen = false },
                                                                modifier = Modifier
                                                                    .background(Color(0xF012141A))
                                                                    .border(1.dp, Color(0x33FFFFFF), RoundedCornerShape(16.dp)),
                                                                shape = RoundedCornerShape(16.dp),
                                                                containerColor = Color(0xF012141A)
                                                            ) {
                                                                routingOptions.forEach { (key, label) ->
                                                                    DropdownMenuItem(
                                                                        modifier = Modifier.heightIn(min = 48.dp),
                                                                        text = { Text(label) },
                                                                        onClick = {
                                                                            isDlDropdownOpen = false
                                                                            prefs.edit().putString(LightspeedPreferences.KEY_RAIL_DOWNLOADS_ROUTING, key).apply()
                                                                            if (key != "none" && !isNotifAccessGranted) {
                                                                                showNotificationAccessDialog = true
                                                                            }
                                                                            onRefreshNeeded()
                                                                        }
                                                                    )
                                                                }
                                                            }
                                                        }
                                                    }

                                                    // Media Telemetry Selector
                                                    val currentMedia = prefs.getString(LightspeedPreferences.KEY_RAIL_MEDIA_ROUTING, "none") ?: "none"
                                                    var isMediaDropdownOpen by remember { mutableStateOf(false) }

                                                    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                                                        Text("MEDIA PLAYBACK TELEMETRY ROUTING", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary, letterSpacing = 1.sp)
                                                        Spacer(modifier = Modifier.height(4.dp))
                                                        Box {
                                                            OutlinedButton(
                                                                onClick = { isMediaDropdownOpen = true },
                                                                modifier = Modifier.fillMaxWidth(),
                                                                shape = RoundedCornerShape(12.dp)
                                                            ) {
                                                                Row(
                                                                    modifier = Modifier.fillMaxWidth(),
                                                                    horizontalArrangement = Arrangement.SpaceBetween,
                                                                    verticalAlignment = Alignment.CenterVertically
                                                                ) {
                                                                    Text(routingOptions.firstOrNull { it.first == currentMedia }?.second ?: "None (Disabled)", color = Color.White)
                                                                    Icon(Icons.Default.ArrowDropDown, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                                                                }
                                                            }
                                                            DropdownMenu(
                                                                expanded = isMediaDropdownOpen,
                                                                onDismissRequest = { isMediaDropdownOpen = false },
                                                                modifier = Modifier
                                                                    .background(Color(0xF012141A))
                                                                    .border(1.dp, Color(0x33FFFFFF), RoundedCornerShape(16.dp)),
                                                                shape = RoundedCornerShape(16.dp),
                                                                containerColor = Color(0xF012141A)
                                                            ) {
                                                                routingOptions.forEach { (key, label) ->
                                                                    DropdownMenuItem(
                                                                        modifier = Modifier.heightIn(min = 48.dp),
                                                                        text = { Text(label) },
                                                                        onClick = {
                                                                            isMediaDropdownOpen = false
                                                                            prefs.edit().putString(LightspeedPreferences.KEY_RAIL_MEDIA_ROUTING, key).apply()
                                                                            if (key != "none" && !isNotifAccessGranted) {
                                                                                showNotificationAccessDialog = true
                                                                            }
                                                                            onRefreshNeeded()
                                                                        }
                                                                    )
                                                                }
                                                            }
                                                        }
                                                    }

                                                    // 1. Horizon Rail Customization
                                                    val screenWidthDp = remember { (context.resources.displayMetrics.widthPixels / context.resources.displayMetrics.density).toInt() }
                                                    val currentColorMode = prefs.getString("pref_horizon_rail_color_mode", "cover_art") ?: "cover_art"
                                                    var isColorModeDropdownOpen by remember { mutableStateOf(false) }
                                                    val colorModeOptions = listOf(
                                                        "cover_art" to "🖼 Follow Media Cover Art (Auto Fallback)",
                                                        "app_icon" to "🎨 Notification App Icon Color",
                                                        "material3" to "🌈 Material 3 Dynamic Accent",
                                                        "inverted" to "☯ Inverted Screen Contrast",
                                                        "custom" to "🎯 Custom Matrix Cyber Chip Palette"
                                                    )

                                                    val currentRailOrientMode = prefs.getString(com.sbf.lightspeed.system.LightspeedPreferences.KEY_HORIZON_RAIL_ORIENTATION_MODE, "both") ?: "both"
                                                    var isRailOrientDropdownOpen by remember { mutableStateOf(false) }
                                                    val railOrientOptions = listOf(
                                                        "both" to "🔄 Both Orientations (Always Active)",
                                                        "landscape_only" to "📐 Landscape Only (Horizontal Deck)",
                                                        "portrait_only" to "📱 Portrait Only"
                                                    )

                                                    val currentRailAlign = prefs.getString("pref_horizon_rail_align", "center") ?: "center"
                                                    var isRailAlignDropdownOpen by remember { mutableStateOf(false) }
                                                    val railAlignOptions = listOf(
                                                        "center" to "Center Aligned",
                                                        "left" to "Left Aligned",
                                                        "right" to "Right Aligned"
                                                    )

                                                    val currentTextPos = prefs.getString("pref_horizon_rail_text_position", "below") ?: "below"
                                                    var isTextPosDropdownOpen by remember { mutableStateOf(false) }
                                                    val textPosOptions = listOf(
                                                        "below" to "Below Rail Line (Recommended)",
                                                        "above" to "Above Rail Line",
                                                        "embedded" to "Centered Inside Track",
                                                        "below_statusbar" to "Below Entire Status Bar Deck"
                                                    )

                                                    val currentPriority = prefs.getString(com.sbf.lightspeed.system.LightspeedPreferences.KEY_HORIZON_RAIL_PRIORITY, "downloads_top") ?: "downloads_top"
                                                    var isPriorityDropdownOpen by remember { mutableStateOf(false) }
                                                    val priorityOptions = listOf(
                                                        "downloads_top" to "⬇ Pin Downloads on Top (Shows Text Ticker)",
                                                        "media_top" to "♫ Pin Media on Top (Shows Text Ticker)",
                                                        "most_recent" to "⏱ Most Recent Stream on Top"
                                                    )

                                                    // 1. Sub-Accordion: Horizon Rail Geometry & Stacking
                                                    CollapsibleSubSection(
                                                        title = "Horizon Rail Geometry & Stacking",
                                                        subtitle = "Span, orientation display rules, safe-margins, glow & offsets",
                                                        isExpanded = isHorizonRailGeomExpanded,
                                                        onToggle = {
                                                            isHorizonRailGeomExpanded = !isHorizonRailGeomExpanded
                                                            prefs.edit().putBoolean("pref_sub_horizon_rail_geom", isHorizonRailGeomExpanded).apply()
                                                            val anyActive = isTelemetryExpanded && (isHorizonRailGeomExpanded || isHorizonRailColorExpanded || isHorizonRailTextExpanded)
                                                            prefs.edit().putBoolean(com.sbf.lightspeed.system.LightspeedPreferences.KEY_HORIZON_RAIL_PREVIEW, anyActive).apply()
                                                            try { LightspeedAccessibilityService.instance?.reloadPreferences() } catch (_: Exception) {}
                                                            onRefreshNeeded()
                                                        }
                                                    ) {
                                                        Column(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                                                            Text("RAIL ORIENTATION DISPLAY RULE", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary, letterSpacing = 1.sp)
                                                            Spacer(modifier = Modifier.height(4.dp))
                                                            Box {
                                                                OutlinedButton(
                                                                    onClick = { isRailOrientDropdownOpen = true },
                                                                    modifier = Modifier.fillMaxWidth(),
                                                                    shape = RoundedCornerShape(12.dp)
                                                                ) {
                                                                    Row(
                                                                        modifier = Modifier.fillMaxWidth(),
                                                                        horizontalArrangement = Arrangement.SpaceBetween,
                                                                        verticalAlignment = Alignment.CenterVertically
                                                                    ) {
                                                                        Text(railOrientOptions.firstOrNull { it.first == currentRailOrientMode }?.second ?: "🔄 Both Orientations", color = Color.White, fontSize = 12.sp)
                                                                        Icon(Icons.Default.ArrowDropDown, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                                                                    }
                                                                }
                                                                DropdownMenu(
                                                                    expanded = isRailOrientDropdownOpen,
                                                                    onDismissRequest = { isRailOrientDropdownOpen = false },
                                                                    modifier = Modifier
                                                                        .background(Color(0xF012141A))
                                                                        .border(1.dp, Color(0x33FFFFFF), RoundedCornerShape(16.dp)),
                                                                    shape = RoundedCornerShape(16.dp),
                                                                    containerColor = Color(0xF012141A)
                                                                ) {
                                                                    railOrientOptions.forEach { (key, label) ->
                                                                        DropdownMenuItem(
                                                                            modifier = Modifier.heightIn(min = 48.dp),
                                                                            text = { Text(label) },
                                                                            onClick = {
                                                                                isRailOrientDropdownOpen = false
                                                                                prefs.edit().putString(com.sbf.lightspeed.system.LightspeedPreferences.KEY_HORIZON_RAIL_ORIENTATION_MODE, key).apply()
                                                                                try { LightspeedAccessibilityService.instance?.reloadPreferences() } catch (_: Exception) {}
                                                                                onRefreshNeeded()
                                                                            }
                                                                        )
                                                                    }
                                                                }
                                                            }
                                                        }

                                                        PrefDottedSliderRow(context, prefs, "pref_horizon_rail_span", "", "Span (Max: ${screenWidthDp}dp)", 50, screenWidthDp, 10, screenWidthDp)

                                                        Column(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                                                            Text("RAIL ALIGNMENT", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary, letterSpacing = 1.sp)
                                                            Spacer(modifier = Modifier.height(4.dp))
                                                            Box {
                                                                OutlinedButton(
                                                                    onClick = { isRailAlignDropdownOpen = true },
                                                                    modifier = Modifier.fillMaxWidth(),
                                                                    shape = RoundedCornerShape(12.dp)
                                                                ) {
                                                                    Row(
                                                                        modifier = Modifier.fillMaxWidth(),
                                                                        horizontalArrangement = Arrangement.SpaceBetween,
                                                                        verticalAlignment = Alignment.CenterVertically
                                                                    ) {
                                                                        Text(railAlignOptions.firstOrNull { it.first == currentRailAlign }?.second ?: "Center Aligned", color = Color.White, fontSize = 12.sp)
                                                                        Icon(Icons.Default.ArrowDropDown, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                                                                    }
                                                                }
                                                                DropdownMenu(
                                                                    expanded = isRailAlignDropdownOpen,
                                                                    onDismissRequest = { isRailAlignDropdownOpen = false },
                                                                    modifier = Modifier
                                                                        .background(Color(0xF012141A))
                                                                        .border(1.dp, Color(0x33FFFFFF), RoundedCornerShape(16.dp)),
                                                                    shape = RoundedCornerShape(16.dp),
                                                                    containerColor = Color(0xF012141A)
                                                                ) {
                                                                    railAlignOptions.forEach { (key, label) ->
                                                                        DropdownMenuItem(
                                                                            modifier = Modifier.heightIn(min = 48.dp),
                                                                            text = { Text(label) },
                                                                            onClick = {
                                                                                isRailAlignDropdownOpen = false
                                                                                prefs.edit().putString("pref_horizon_rail_align", key).apply()
                                                                                try { LightspeedAccessibilityService.instance?.reloadPreferences() } catch (_: Exception) {}
                                                                                onRefreshNeeded()
                                                                            }
                                                                        )
                                                                    }
                                                                }
                                                            }
                                                        }

                                                        PrefDottedSliderRow(context, prefs, "pref_horizon_rail_offset_x", "", "Horizontal Offset (X Axis)", -100, 100, 5, 0)
                                                        PrefDottedSliderRow(context, prefs, com.sbf.lightspeed.system.LightspeedPreferences.KEY_HORIZON_RAIL_OFFSET_Y, "", "Vertical Offset Y (0 to 40dp)", 0, 40, 1, 0)
                                                        PrefDottedSliderRow(context, prefs, "pref_horizon_rail_thickness", "", "Line Thickness (dp)", 1, 6, 1, 2)
                                                        PrefDottedSliderRow(context, prefs, com.sbf.lightspeed.system.LightspeedPreferences.KEY_HORIZON_RAIL_STACK_SPACING, "", "Inter-Rail Stack Spacing (0 to 6dp)", 0, 6, 1, 0)
                                                        PrefDottedSliderRow(context, prefs, "pref_horizon_rail_glow", "", "Glow Radiance Intensity (%)", 0, 100, 5, 60)
                                                        PrefDottedSliderRow(context, prefs, "pref_horizon_rail_track_opacity", "", "Inactive Track Opacity (%)", 0, 100, 5, 15)
                                                        PrefDottedSliderRow(context, prefs, com.sbf.lightspeed.system.LightspeedPreferences.KEY_HORIZON_RAIL_MAX_COUNT, "", "Max Concurrent Rails (1 to 3)", 1, 3, 1, 2)

                                                        PrefToggleRow(
                                                            prefs = prefs,
                                                            prefKey = com.sbf.lightspeed.system.LightspeedPreferences.KEY_HORIZON_RAIL_DROP_SHADOW,
                                                            defaultVal = true,
                                                            title = "Ambient Drop Shadow & Contrast Trench",
                                                            subtitle = "Renders a dark ambient occlusion shadow underneath the rails to maintain crisp separation from matching wallpapers and light backgrounds.",
                                                            onChanged = { onRefreshNeeded() }
                                                        )

                                                        PrefToggleRow(
                                                            prefs = prefs,
                                                            prefKey = com.sbf.lightspeed.system.LightspeedPreferences.KEY_HORIZON_RAIL_TERMINAL_CAPS,
                                                            defaultVal = true,
                                                            title = "Tactical Head Caps & End Markers",
                                                            subtitle = "Renders high-contrast specular notches at the progress head of each rail for pinpoint completion readout.",
                                                            onChanged = { onRefreshNeeded() }
                                                        )

                                                        PrefToggleRow(
                                                            prefs = prefs,
                                                            prefKey = com.sbf.lightspeed.system.LightspeedPreferences.KEY_HORIZON_RAIL_STATUS_BAR_GUARD,
                                                            defaultVal = true,
                                                            title = "Landscape Status Bar Safe Guard",
                                                            subtitle = "Automatically prevents long telemetry text or rails from occluding system status bar items (clock and network/battery icons).",
                                                            onChanged = { onRefreshNeeded() }
                                                        )

                                                        PrefDottedSliderRow(context, prefs, com.sbf.lightspeed.system.LightspeedPreferences.KEY_HORIZON_RAIL_SAFE_PADDING_LEFT, "", "Left Safe Margin Inset (dp)", 0, 80, 2, 0)
                                                        PrefDottedSliderRow(context, prefs, com.sbf.lightspeed.system.LightspeedPreferences.KEY_HORIZON_RAIL_SAFE_PADDING_RIGHT, "", "Right Safe Margin Inset (dp)", 0, 80, 2, 0)

                                                        Column(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                                                            Text("MULTI-RAIL PINNING & PRIORITY", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary, letterSpacing = 1.sp)
                                                            Spacer(modifier = Modifier.height(2.dp))
                                                            Text("Select which active stream is pinned at the top and displays the typography ticker.", fontSize = 11.sp, color = Color.Gray, lineHeight = 14.sp)
                                                            Spacer(modifier = Modifier.height(4.dp))
                                                            Box {
                                                                OutlinedButton(
                                                                    onClick = { isPriorityDropdownOpen = true },
                                                                    modifier = Modifier.fillMaxWidth(),
                                                                    shape = RoundedCornerShape(12.dp)
                                                                ) {
                                                                    Row(
                                                                        modifier = Modifier.fillMaxWidth(),
                                                                        horizontalArrangement = Arrangement.SpaceBetween,
                                                                        verticalAlignment = Alignment.CenterVertically
                                                                    ) {
                                                                        Text(priorityOptions.firstOrNull { it.first == currentPriority }?.second ?: "⬇ Pin Downloads on Top", color = Color.White, fontSize = 12.sp)
                                                                        Icon(Icons.Default.ArrowDropDown, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                                                                    }
                                                                }
                                                                DropdownMenu(
                                                                    expanded = isPriorityDropdownOpen,
                                                                    onDismissRequest = { isPriorityDropdownOpen = false },
                                                                    modifier = Modifier
                                                                        .background(Color(0xF012141A))
                                                                        .border(1.dp, Color(0x33FFFFFF), RoundedCornerShape(16.dp)),
                                                                    shape = RoundedCornerShape(16.dp),
                                                                    containerColor = Color(0xF012141A)
                                                                ) {
                                                                    priorityOptions.forEach { (key, label) ->
                                                                        DropdownMenuItem(
                                                                            modifier = Modifier.heightIn(min = 48.dp),
                                                                            text = { Text(label) },
                                                                            onClick = {
                                                                                isPriorityDropdownOpen = false
                                                                                prefs.edit().putString(com.sbf.lightspeed.system.LightspeedPreferences.KEY_HORIZON_RAIL_PRIORITY, key).apply()
                                                                                try { LightspeedAccessibilityService.instance?.reloadPreferences() } catch (_: Exception) {}
                                                                                onRefreshNeeded()
                                                                            }
                                                                        )
                                                                    }
                                                                }
                                                            }
                                                        }
                                                    }

                                                    // 2. Sub-Accordion: Horizon Rail Color & Styling
                                                    CollapsibleSubSection(
                                                        title = "Horizon Rail Colors & Contrast",
                                                        subtitle = "Cover art dynamic sampling, palette presets & ambient outline",
                                                        isExpanded = isHorizonRailColorExpanded,
                                                        onToggle = {
                                                            isHorizonRailColorExpanded = !isHorizonRailColorExpanded
                                                            prefs.edit().putBoolean("pref_sub_horizon_rail_color", isHorizonRailColorExpanded).apply()
                                                            val anyActive = isTelemetryExpanded && (isHorizonRailGeomExpanded || isHorizonRailColorExpanded || isHorizonRailTextExpanded)
                                                            prefs.edit().putBoolean(com.sbf.lightspeed.system.LightspeedPreferences.KEY_HORIZON_RAIL_PREVIEW, anyActive).apply()
                                                            try { LightspeedAccessibilityService.instance?.reloadPreferences() } catch (_: Exception) {}
                                                            onRefreshNeeded()
                                                        }
                                                    ) {
                                                        Column(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                                                            Text("HORIZON RAIL COLOR MODE", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary, letterSpacing = 1.sp)
                                                            Spacer(modifier = Modifier.height(4.dp))
                                                            Box {
                                                                OutlinedButton(
                                                                    onClick = { isColorModeDropdownOpen = true },
                                                                    modifier = Modifier.fillMaxWidth(),
                                                                    shape = RoundedCornerShape(12.dp)
                                                                ) {
                                                                    Row(
                                                                        modifier = Modifier.fillMaxWidth(),
                                                                        horizontalArrangement = Arrangement.SpaceBetween,
                                                                        verticalAlignment = Alignment.CenterVertically
                                                                    ) {
                                                                        Text(colorModeOptions.firstOrNull { it.first == currentColorMode }?.second ?: "🖼 Follow Media Cover Art", color = Color.White, fontSize = 12.sp)
                                                                        Icon(Icons.Default.ArrowDropDown, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                                                                    }
                                                                }
                                                                DropdownMenu(
                                                                    expanded = isColorModeDropdownOpen,
                                                                    onDismissRequest = { isColorModeDropdownOpen = false },
                                                                    modifier = Modifier
                                                                        .background(Color(0xF012141A))
                                                                        .border(1.dp, Color(0x33FFFFFF), RoundedCornerShape(16.dp)),
                                                                    shape = RoundedCornerShape(16.dp),
                                                                    containerColor = Color(0xF012141A)
                                                                ) {
                                                                    colorModeOptions.forEach { (key, label) ->
                                                                        DropdownMenuItem(
                                                                            modifier = Modifier.heightIn(min = 48.dp),
                                                                            text = { Text(label) },
                                                                            onClick = {
                                                                                isColorModeDropdownOpen = false
                                                                                prefs.edit().putString("pref_horizon_rail_color_mode", key).apply()
                                                                                try { LightspeedAccessibilityService.instance?.reloadPreferences() } catch (_: Exception) {}
                                                                                onRefreshNeeded()
                                                                            }
                                                                        )
                                                                    }
                                                                }
                                                            }
                                                        }

                                                        // Custom Color Preset Palette (shown when "custom" is selected)
                                                        if (currentColorMode == "custom") {
                                                            val customHex = prefs.getString("pref_horizon_rail_custom_color", "#00E5FF") ?: "#00E5FF"
                                                            val presetColors = listOf(
                                                                "#00E5FF" to "Cyan",
                                                                "#00E676" to "Green",
                                                                "#FFD600" to "Amber",
                                                                "#FF6D00" to "Orange",
                                                                "#FF1744" to "Crimson",
                                                                "#FF007F" to "Pink",
                                                                "#D500F9" to "Purple",
                                                                "#FFFFFF" to "White"
                                                            )
                                                            Column(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                                                Text("CUSTOM PALETTE PRESETS", fontSize = 10.5.sp, fontWeight = FontWeight.SemiBold, color = Color.LightGray)
                                                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                                                    presetColors.forEach { (hex, _) ->
                                                                        val col = try { Color(android.graphics.Color.parseColor(hex)) } catch (_: Exception) { Color.Cyan }
                                                                        val isSelected = customHex.equals(hex, ignoreCase = true)
                                                                        Box(
                                                                            modifier = Modifier
                                                                                .size(32.dp)
                                                                                .clip(CircleShape)
                                                                                .background(col)
                                                                                .border(
                                                                                    width = if (isSelected) 2.5.dp else 1.dp,
                                                                                    color = if (isSelected) Color.White else Color.White.copy(alpha = 0.3f),
                                                                                    shape = CircleShape
                                                                                )
                                                                                .clickable {
                                                                                    prefs.edit().putString("pref_horizon_rail_custom_color", hex).apply()
                                                                                    try { LightspeedAccessibilityService.instance?.reloadPreferences() } catch (_: Exception) {}
                                                                                    onRefreshNeeded()
                                                                                },
                                                                            contentAlignment = Alignment.Center
                                                                        ) {
                                                                            if (isSelected) {
                                                                                Icon(Icons.Default.Check, contentDescription = null, tint = if (hex == "#FFFFFF") Color.Black else Color.White, modifier = Modifier.size(16.dp))
                                                                            }
                                                                        }
                                                                    }
                                                                }
                                                            }
                                                        }

                                                        PrefToggleRow(
                                                            prefs = prefs,
                                                            prefKey = com.sbf.lightspeed.system.LightspeedPreferences.KEY_HORIZON_RAIL_CONTRAST_SHIELD,
                                                            defaultVal = true,
                                                            title = "High-Contrast Ambient Outline",
                                                            subtitle = "Renders a dark semi-transparent outline halo around glyphs to guarantee 100% legibility on pure white backgrounds.",
                                                            onChanged = { onRefreshNeeded() }
                                                        )
                                                    }

                                                    // 3. Sub-Accordion: Horizon Rail Micro-Text Ticker
                                                    CollapsibleSubSection(
                                                        title = "Horizon Rail Micro-Text Ticker",
                                                        subtitle = "Typography, dual-wing marquee, bounce velocity & orientation rules",
                                                        isExpanded = isHorizonRailTextExpanded,
                                                        onToggle = {
                                                            isHorizonRailTextExpanded = !isHorizonRailTextExpanded
                                                            prefs.edit().putBoolean("pref_sub_horizon_rail_text", isHorizonRailTextExpanded).apply()
                                                            val anyActive = isTelemetryExpanded && (isHorizonRailGeomExpanded || isHorizonRailColorExpanded || isHorizonRailTextExpanded)
                                                            prefs.edit().putBoolean(com.sbf.lightspeed.system.LightspeedPreferences.KEY_HORIZON_RAIL_PREVIEW, anyActive).apply()
                                                            try { LightspeedAccessibilityService.instance?.reloadPreferences() } catch (_: Exception) {}
                                                            onRefreshNeeded()
                                                        }
                                                    ) {
                                                        var isRailTextEnabled by rememberSaveable { mutableStateOf(prefs.getBoolean("pref_horizon_rail_text_enabled", true)) }
                                                        PrefToggleRow(
                                                            title = "Micro-Text Telemetry Ticker",
                                                            subtitle = "Streams active download filenames, song titles, episode numbers, and live track progress along the rail.",
                                                            isChecked = isRailTextEnabled,
                                                            onCheckedChange = {
                                                                isRailTextEnabled = it
                                                                prefs.edit().putBoolean("pref_horizon_rail_text_enabled", it).apply()
                                                                try { LightspeedAccessibilityService.instance?.reloadPreferences() } catch (_: Exception) {}
                                                                onRefreshNeeded()
                                                            }
                                                        )

                                                        if (isRailTextEnabled) {
                                                            val currentTextOrientMode = prefs.getString(com.sbf.lightspeed.system.LightspeedPreferences.KEY_HORIZON_RAIL_TEXT_ORIENTATION_MODE, "both") ?: "both"
                                                            var isTextOrientDropdownOpen by remember { mutableStateOf(false) }
                                                            val textOrientOptions = listOf(
                                                                "both" to "🔄 Both Orientations",
                                                                "landscape_only" to "📐 Landscape Only",
                                                                "portrait_only" to "📱 Portrait Only"
                                                            )

                                                            Column(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                                                                Text("MICRO-TEXT ORIENTATION FILTER", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary, letterSpacing = 1.sp)
                                                                Spacer(modifier = Modifier.height(4.dp))
                                                                Box {
                                                                    OutlinedButton(
                                                                        onClick = { isTextOrientDropdownOpen = true },
                                                                        modifier = Modifier.fillMaxWidth(),
                                                                        shape = RoundedCornerShape(12.dp)
                                                                    ) {
                                                                        Row(
                                                                            modifier = Modifier.fillMaxWidth(),
                                                                            horizontalArrangement = Arrangement.SpaceBetween,
                                                                            verticalAlignment = Alignment.CenterVertically
                                                                        ) {
                                                                            Text(textOrientOptions.firstOrNull { it.first == currentTextOrientMode }?.second ?: "🔄 Both Orientations", color = Color.White, fontSize = 12.sp)
                                                                            Icon(Icons.Default.ArrowDropDown, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                                                                        }
                                                                    }
                                                                    DropdownMenu(
                                                                        expanded = isTextOrientDropdownOpen,
                                                                        onDismissRequest = { isTextOrientDropdownOpen = false },
                                                                        modifier = Modifier
                                                                            .background(Color(0xF012141A))
                                                                            .border(1.dp, Color(0x33FFFFFF), RoundedCornerShape(16.dp)),
                                                                        shape = RoundedCornerShape(16.dp),
                                                                        containerColor = Color(0xF012141A)
                                                                    ) {
                                                                        textOrientOptions.forEach { (key, label) ->
                                                                            DropdownMenuItem(
                                                                                modifier = Modifier.heightIn(min = 48.dp),
                                                                                text = { Text(label) },
                                                                                onClick = {
                                                                                    isTextOrientDropdownOpen = false
                                                                                    prefs.edit().putString(com.sbf.lightspeed.system.LightspeedPreferences.KEY_HORIZON_RAIL_TEXT_ORIENTATION_MODE, key).apply()
                                                                                    try { LightspeedAccessibilityService.instance?.reloadPreferences() } catch (_: Exception) {}
                                                                                    onRefreshNeeded()
                                                                                }
                                                                            )
                                                                        }
                                                                    }
                                                                }
                                                            }

                                                            val currentMetadataMode = prefs.getString(com.sbf.lightspeed.system.LightspeedPreferences.KEY_HORIZON_RAIL_TEXT_METADATA_MODE, "adaptive") ?: "adaptive"
                                                            var isMetadataDropdownOpen by remember { mutableStateOf(false) }
                                                            val metadataOptions = listOf(
                                                                "adaptive" to "⚡ Adaptive (Title in Portrait, Full Metadata in Landscape)",
                                                                "full" to "📜 Full Metadata Always (Title + Artist + Episode/Album)",
                                                                "title_only" to "🏷 Title Only Always"
                                                            )

                                                            Column(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                                                                Text("METADATA DENSITY & ORIENTATION MODE", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary, letterSpacing = 1.sp)
                                                                Spacer(modifier = Modifier.height(4.dp))
                                                                Box {
                                                                    OutlinedButton(
                                                                        onClick = { isMetadataDropdownOpen = true },
                                                                        modifier = Modifier.fillMaxWidth(),
                                                                        shape = RoundedCornerShape(12.dp)
                                                                    ) {
                                                                        Row(
                                                                            modifier = Modifier.fillMaxWidth(),
                                                                            horizontalArrangement = Arrangement.SpaceBetween,
                                                                            verticalAlignment = Alignment.CenterVertically
                                                                        ) {
                                                                            Text(metadataOptions.firstOrNull { it.first == currentMetadataMode }?.second ?: "⚡ Adaptive", color = Color.White, fontSize = 12.sp)
                                                                            Icon(Icons.Default.ArrowDropDown, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                                                                        }
                                                                    }
                                                                    DropdownMenu(
                                                                        expanded = isMetadataDropdownOpen,
                                                                        onDismissRequest = { isMetadataDropdownOpen = false },
                                                                        modifier = Modifier
                                                                            .background(Color(0xF012141A))
                                                                            .border(1.dp, Color(0x33FFFFFF), RoundedCornerShape(16.dp)),
                                                                        shape = RoundedCornerShape(16.dp),
                                                                        containerColor = Color(0xF012141A)
                                                                    ) {
                                                                        metadataOptions.forEach { (key, label) ->
                                                                            DropdownMenuItem(
                                                                                modifier = Modifier.heightIn(min = 48.dp),
                                                                                text = { Text(label) },
                                                                                onClick = {
                                                                                    isMetadataDropdownOpen = false
                                                                                    prefs.edit().putString(com.sbf.lightspeed.system.LightspeedPreferences.KEY_HORIZON_RAIL_TEXT_METADATA_MODE, key).apply()
                                                                                    try { LightspeedAccessibilityService.instance?.reloadPreferences() } catch (_: Exception) {}
                                                                                    onRefreshNeeded()
                                                                                }
                                                                            )
                                                                        }
                                                                    }
                                                                }
                                                            }

                                                            PrefToggleRow(
                                                                prefs = prefs,
                                                                prefKey = com.sbf.lightspeed.system.LightspeedPreferences.KEY_HORIZON_RAIL_TEXT_SHOW_TIMESTAMP,
                                                                defaultVal = false,
                                                                title = "Include Playback Progress Timestamp",
                                                                subtitle = "Appends live track position and duration (e.g. 02:45 / 05:10) to the micro-text ticker.",
                                                                onChanged = { onRefreshNeeded() }
                                                            )

                                                             // Letter Casing Format Selector
                                                             val currentCasing = prefs.getString(com.sbf.lightspeed.system.LightspeedPreferences.KEY_HORIZON_RAIL_TEXT_CASING, "natural") ?: "natural"
                                                             var isCasingDropdownOpen by remember { mutableStateOf(false) }
                                                             val casingOptions = listOf(
                                                                 "natural" to "✨ Original / Natural Casing (Title & Artist)",
                                                                 "all_caps" to "🔤 ALL CAPS (Aviation HUD Avionics)",
                                                                 "title_case" to "🔠 Title Case (Capitalize Every Word)"
                                                             )

                                                             Column(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                                                                 Text("LETTER CASING FORMAT", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary, letterSpacing = 1.sp)
                                                                 Spacer(modifier = Modifier.height(4.dp))
                                                                 Box {
                                                                     OutlinedButton(
                                                                         onClick = { isCasingDropdownOpen = true },
                                                                         modifier = Modifier.fillMaxWidth(),
                                                                         shape = RoundedCornerShape(12.dp)
                                                                     ) {
                                                                         Row(
                                                                             modifier = Modifier.fillMaxWidth(),
                                                                             horizontalArrangement = Arrangement.SpaceBetween,
                                                                             verticalAlignment = Alignment.CenterVertically
                                                                         ) {
                                                                             Text(casingOptions.firstOrNull { it.first == currentCasing }?.second ?: "✨ Original / Natural Casing", color = Color.White, fontSize = 12.sp)
                                                                             Icon(Icons.Default.ArrowDropDown, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                                                                         }
                                                                     }
                                                                     DropdownMenu(
                                                                         expanded = isCasingDropdownOpen,
                                                                         onDismissRequest = { isCasingDropdownOpen = false },
                                                                         modifier = Modifier
                                                                             .background(Color(0xF012141A))
                                                                             .border(1.dp, Color(0x33FFFFFF), RoundedCornerShape(16.dp)),
                                                                         shape = RoundedCornerShape(16.dp),
                                                                         containerColor = Color(0xF012141A)
                                                                     ) {
                                                                         casingOptions.forEach { (key, label) ->
                                                                             DropdownMenuItem(
                                                                                 modifier = Modifier.heightIn(min = 48.dp),
                                                                                 text = { Text(label) },
                                                                                 onClick = {
                                                                                     isCasingDropdownOpen = false
                                                                                     prefs.edit().putString(com.sbf.lightspeed.system.LightspeedPreferences.KEY_HORIZON_RAIL_TEXT_CASING, key).apply()
                                                                                     try { LightspeedAccessibilityService.instance?.reloadPreferences() } catch (_: Exception) {}
                                                                                     onRefreshNeeded()
                                                                                 }
                                                                             )
                                                                         }
                                                                     }
                                                                 }
                                                             }

                                                             // Device Installed Font Family Selector
                                                             val currentFont = prefs.getString(com.sbf.lightspeed.system.LightspeedPreferences.KEY_HORIZON_RAIL_TEXT_FONT, "system_default") ?: "system_default"
                                                             var isFontDropdownOpen by remember { mutableStateOf(false) }
                                                             val availableFonts = remember { com.sbf.lightspeed.system.DeviceFontScanner.getInstalledFonts() }

                                                             Column(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                                                                 Text("MICRO-TEXT FONT FAMILY", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary, letterSpacing = 1.sp)
                                                                 Spacer(modifier = Modifier.height(4.dp))
                                                                 Box {
                                                                     OutlinedButton(
                                                                         onClick = { isFontDropdownOpen = true },
                                                                         modifier = Modifier.fillMaxWidth(),
                                                                         shape = RoundedCornerShape(12.dp)
                                                                     ) {
                                                                         Row(
                                                                             modifier = Modifier.fillMaxWidth(),
                                                                             horizontalArrangement = Arrangement.SpaceBetween,
                                                                             verticalAlignment = Alignment.CenterVertically
                                                                         ) {
                                                                             Text(availableFonts.firstOrNull { it.first == currentFont }?.second ?: "📱 Follow Device (System Default)", color = Color.White, fontSize = 12.sp)
                                                                             Icon(Icons.Default.ArrowDropDown, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                                                                         }
                                                                     }
                                                                     DropdownMenu(
                                                                         expanded = isFontDropdownOpen,
                                                                         onDismissRequest = { isFontDropdownOpen = false },
                                                                         modifier = Modifier
                                                                             .background(Color(0xF012141A))
                                                                             .border(1.dp, Color(0x33FFFFFF), RoundedCornerShape(16.dp)),
                                                                         shape = RoundedCornerShape(16.dp),
                                                                         containerColor = Color(0xF012141A)
                                                                     ) {
                                                                         availableFonts.forEach { (key, label) ->
                                                                             DropdownMenuItem(
                                                                                 modifier = Modifier.heightIn(min = 48.dp),
                                                                                 text = { Text(label) },
                                                                                 onClick = {
                                                                                     isFontDropdownOpen = false
                                                                                     prefs.edit().putString(com.sbf.lightspeed.system.LightspeedPreferences.KEY_HORIZON_RAIL_TEXT_FONT, key).apply()
                                                                                     try { LightspeedAccessibilityService.instance?.reloadPreferences() } catch (_: Exception) {}
                                                                                     onRefreshNeeded()
                                                                                 }
                                                                             )
                                                                         }
                                                                     }
                                                                 }
                                                             }

                                                            PrefDottedSliderRow(context, prefs, "pref_horizon_rail_text_size", "", "Micro-Font Size (dp)", 7, 16, 1, 9)

                                                            Column(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                                                                Text("MICRO-TEXT POSITION", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary, letterSpacing = 1.sp)
                                                                Spacer(modifier = Modifier.height(4.dp))
                                                                Box {
                                                                    OutlinedButton(
                                                                        onClick = { isTextPosDropdownOpen = true },
                                                                        modifier = Modifier.fillMaxWidth(),
                                                                        shape = RoundedCornerShape(12.dp)
                                                                    ) {
                                                                        Row(
                                                                            modifier = Modifier.fillMaxWidth(),
                                                                            horizontalArrangement = Arrangement.SpaceBetween,
                                                                            verticalAlignment = Alignment.CenterVertically
                                                                        ) {
                                                                            Text(textPosOptions.firstOrNull { it.first == currentTextPos }?.second ?: "Below Rail Line (Recommended)", color = Color.White, fontSize = 12.sp)
                                                                            Icon(Icons.Default.ArrowDropDown, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                                                                        }
                                                                    }
                                                                    DropdownMenu(
                                                                        expanded = isTextPosDropdownOpen,
                                                                        onDismissRequest = { isTextPosDropdownOpen = false },
                                                                        modifier = Modifier
                                                                            .background(Color(0xF012141A))
                                                                            .border(1.dp, Color(0x33FFFFFF), RoundedCornerShape(16.dp)),
                                                                        shape = RoundedCornerShape(16.dp),
                                                                        containerColor = Color(0xF012141A)
                                                                    ) {
                                                                        textPosOptions.forEach { (key, label) ->
                                                                            DropdownMenuItem(
                                                                                modifier = Modifier.heightIn(min = 48.dp),
                                                                                text = { Text(label) },
                                                                                onClick = {
                                                                                    isTextPosDropdownOpen = false
                                                                                    prefs.edit().putString("pref_horizon_rail_text_position", key).apply()
                                                                                    try { LightspeedAccessibilityService.instance?.reloadPreferences() } catch (_: Exception) {}
                                                                                    onRefreshNeeded()
                                                                                }
                                                                            )
                                                                        }
                                                                    }
                                                                }
                                                            }

                                                            PrefDottedSliderRow(context, prefs, com.sbf.lightspeed.system.LightspeedPreferences.KEY_HORIZON_RAIL_TEXT_OFFSET_Y, "", "Vertical Fine Y-Offset (dp)", -20, 40, 1, 0)

                                                            // Marquee Scope & Sider Controls
                                                            val currentMarqueeScope = prefs.getString(com.sbf.lightspeed.system.LightspeedPreferences.KEY_HORIZON_RAIL_MARQUEE_SCOPE, "both_wings") ?: "both_wings"
                                                            var isMarqueeScopeDropdownOpen by remember { mutableStateOf(false) }
                                                            val marqueeScopeOptions = listOf(
                                                                "both_wings" to "🔀 Dual-Wing Independent (Marquee Overflowing Side)",
                                                                "right_wing_only" to "👉 Right Wing Only (Artist / Episode / Sider)",
                                                                "left_wing_only" to "👈 Left Wing Only (Title)",
                                                                "unified" to "🔗 Unified Stream (Split Across Wings & Marquee)"
                                                            )

                                                            Column(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                                                                Text("MARQUEE SCOPE & SIDER TARGET", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary, letterSpacing = 1.sp)
                                                                Spacer(modifier = Modifier.height(4.dp))
                                                                Box {
                                                                    OutlinedButton(
                                                                        onClick = { isMarqueeScopeDropdownOpen = true },
                                                                        modifier = Modifier.fillMaxWidth(),
                                                                        shape = RoundedCornerShape(12.dp)
                                                                    ) {
                                                                        Row(
                                                                            modifier = Modifier.fillMaxWidth(),
                                                                            horizontalArrangement = Arrangement.SpaceBetween,
                                                                            verticalAlignment = Alignment.CenterVertically
                                                                        ) {
                                                                            Text(marqueeScopeOptions.firstOrNull { it.first == currentMarqueeScope }?.second ?: "🔀 Dual-Wing Independent", color = Color.White, fontSize = 12.sp)
                                                                            Icon(Icons.Default.ArrowDropDown, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                                                                        }
                                                                    }
                                                                    DropdownMenu(
                                                                        expanded = isMarqueeScopeDropdownOpen,
                                                                        onDismissRequest = { isMarqueeScopeDropdownOpen = false },
                                                                        modifier = Modifier
                                                                            .background(Color(0xF012141A))
                                                                            .border(1.dp, Color(0x33FFFFFF), RoundedCornerShape(16.dp)),
                                                                        shape = RoundedCornerShape(16.dp),
                                                                        containerColor = Color(0xF012141A)
                                                                    ) {
                                                                        marqueeScopeOptions.forEach { (key, label) ->
                                                                            DropdownMenuItem(
                                                                                modifier = Modifier.heightIn(min = 48.dp),
                                                                                text = { Text(label) },
                                                                                onClick = {
                                                                                    isMarqueeScopeDropdownOpen = false
                                                                                    prefs.edit().putString(com.sbf.lightspeed.system.LightspeedPreferences.KEY_HORIZON_RAIL_MARQUEE_SCOPE, key).apply()
                                                                                    try { LightspeedAccessibilityService.instance?.reloadPreferences() } catch (_: Exception) {}
                                                                                    onRefreshNeeded()
                                                                                }
                                                                            )
                                                                        }
                                                                    }
                                                                }
                                                            }

                                                            // Marquee Animation Style: Bounce vs Continuous Loop
                                                            val currentMarqueeAnim = prefs.getString(com.sbf.lightspeed.system.LightspeedPreferences.KEY_HORIZON_RAIL_MARQUEE_ANIM_MODE, "continuous_wrap") ?: "continuous_wrap"
                                                            var isMarqueeAnimDropdownOpen by remember { mutableStateOf(false) }
                                                            val marqueeAnimOptions = listOf(
                                                                "continuous_wrap" to "♾️ Continuous Loop (Seamless Wrap Across Edges)",
                                                                "bounce" to "🏓 Bounce / Ping-Pong (Back & Forth with Edge Pauses)"
                                                            )

                                                            Column(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                                                                Text("MARQUEE ANIMATION STYLE", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary, letterSpacing = 1.sp)
                                                                Spacer(modifier = Modifier.height(4.dp))
                                                                Box {
                                                                    OutlinedButton(
                                                                        onClick = { isMarqueeAnimDropdownOpen = true },
                                                                        modifier = Modifier.fillMaxWidth(),
                                                                        shape = RoundedCornerShape(12.dp)
                                                                    ) {
                                                                        Row(
                                                                            modifier = Modifier.fillMaxWidth(),
                                                                            horizontalArrangement = Arrangement.SpaceBetween,
                                                                            verticalAlignment = Alignment.CenterVertically
                                                                        ) {
                                                                            Text(marqueeAnimOptions.firstOrNull { it.first == currentMarqueeAnim }?.second ?: "♾️ Continuous Loop", color = Color.White, fontSize = 12.sp)
                                                                            Icon(Icons.Default.ArrowDropDown, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                                                                        }
                                                                    }
                                                                    DropdownMenu(
                                                                        expanded = isMarqueeAnimDropdownOpen,
                                                                        onDismissRequest = { isMarqueeAnimDropdownOpen = false },
                                                                        modifier = Modifier
                                                                            .background(Color(0xF012141A))
                                                                            .border(1.dp, Color(0x33FFFFFF), RoundedCornerShape(16.dp)),
                                                                        shape = RoundedCornerShape(16.dp),
                                                                        containerColor = Color(0xF012141A)
                                                                    ) {
                                                                        marqueeAnimOptions.forEach { (key, label) ->
                                                                            DropdownMenuItem(
                                                                                modifier = Modifier.heightIn(min = 48.dp),
                                                                                text = { Text(label) },
                                                                                onClick = {
                                                                                    isMarqueeAnimDropdownOpen = false
                                                                                    prefs.edit().putString(com.sbf.lightspeed.system.LightspeedPreferences.KEY_HORIZON_RAIL_MARQUEE_ANIM_MODE, key).apply()
                                                                                    try { LightspeedAccessibilityService.instance?.reloadPreferences() } catch (_: Exception) {}
                                                                                    onRefreshNeeded()
                                                                                }
                                                                            )
                                                                        }
                                                                    }
                                                                }
                                                            }

                                                            // Marquee Direction
                                                            val currentMarqueeDir = prefs.getString(com.sbf.lightspeed.system.LightspeedPreferences.KEY_HORIZON_RAIL_MARQUEE_DIRECTION, "rtl") ?: "rtl"
                                                            var isMarqueeDirDropdownOpen by remember { mutableStateOf(false) }
                                                            val marqueeDirOptions = listOf(
                                                                "rtl" to "⬅️ Right-to-Left (Standard RTL)",
                                                                "ltr" to "➡️ Left-to-Right (LTR)"
                                                            )

                                                            Column(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                                                                Text("MARQUEE SCROLL DIRECTION", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary, letterSpacing = 1.sp)
                                                                Spacer(modifier = Modifier.height(4.dp))
                                                                Box {
                                                                    OutlinedButton(
                                                                        onClick = { isMarqueeDirDropdownOpen = true },
                                                                        modifier = Modifier.fillMaxWidth(),
                                                                        shape = RoundedCornerShape(12.dp)
                                                                    ) {
                                                                        Row(
                                                                            modifier = Modifier.fillMaxWidth(),
                                                                            horizontalArrangement = Arrangement.SpaceBetween,
                                                                            verticalAlignment = Alignment.CenterVertically
                                                                        ) {
                                                                            Text(marqueeDirOptions.firstOrNull { it.first == currentMarqueeDir }?.second ?: "⬅️ Right-to-Left (Standard RTL)", color = Color.White, fontSize = 12.sp)
                                                                            Icon(Icons.Default.ArrowDropDown, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                                                                        }
                                                                    }
                                                                    DropdownMenu(
                                                                        expanded = isMarqueeDirDropdownOpen,
                                                                        onDismissRequest = { isMarqueeDirDropdownOpen = false },
                                                                        modifier = Modifier
                                                                            .background(Color(0xF012141A))
                                                                            .border(1.dp, Color(0x33FFFFFF), RoundedCornerShape(16.dp)),
                                                                        shape = RoundedCornerShape(16.dp),
                                                                        containerColor = Color(0xF012141A)
                                                                    ) {
                                                                        marqueeDirOptions.forEach { (key, label) ->
                                                                            DropdownMenuItem(
                                                                                modifier = Modifier.heightIn(min = 48.dp),
                                                                                text = { Text(label) },
                                                                                onClick = {
                                                                                    isMarqueeDirDropdownOpen = false
                                                                                    prefs.edit().putString(com.sbf.lightspeed.system.LightspeedPreferences.KEY_HORIZON_RAIL_MARQUEE_DIRECTION, key).apply()
                                                                                    try { LightspeedAccessibilityService.instance?.reloadPreferences() } catch (_: Exception) {}
                                                                                    onRefreshNeeded()
                                                                                }
                                                                            )
                                                                        }
                                                                    }
                                                                }
                                                            }

                                                            PrefDottedSliderRow(context, prefs, "pref_horizon_rail_text_speed", "", "Scroll Velocity (px/sec)", 10, 80, 5, 20)

                                                            var isAvoidCutout by rememberSaveable { mutableStateOf(prefs.getBoolean(com.sbf.lightspeed.system.LightspeedPreferences.KEY_HORIZON_RAIL_AVOID_CUTOUT, false)) }
                                                            PrefToggleRow(
                                                                title = "Hardware Cutout & Punch-Hole Avoidance",
                                                                subtitle = "Splits title and subtitle into dual symmetrical wings around the camera cutout.",
                                                                isChecked = isAvoidCutout,
                                                                onCheckedChange = {
                                                                    isAvoidCutout = it
                                                                    prefs.edit().putBoolean(com.sbf.lightspeed.system.LightspeedPreferences.KEY_HORIZON_RAIL_AVOID_CUTOUT, it).apply()
                                                                    try { LightspeedAccessibilityService.instance?.reloadPreferences() } catch (_: Exception) {}
                                                                    onRefreshNeeded()
                                                                }
                                                            )

                                                            if (isAvoidCutout) {
                                                                PrefDottedSliderRow(context, prefs, com.sbf.lightspeed.system.LightspeedPreferences.KEY_HORIZON_RAIL_CUTOUT_PADDING, "", "Wing Clearance Breathing Margin (0 to 16dp)", 0, 16, 1, 2)
                                                            }
                                                        }
                                                    }

                                                    // 4. Shared Hardware Cutout & Punch-Hole Calibration
                                                    var isCutoutCalibExpanded by rememberSaveable { mutableStateOf(prefs.getBoolean("pref_sub_cutout_calib", false)) }
                                                    CollapsibleSubSection(
                                                        title = "Hardware Cutout & Punch-Hole Calibration",
                                                        subtitle = "Physical camera hole diameter & alignment shared across Horizon Rail and Orbital Capsule",
                                                        isExpanded = isCutoutCalibExpanded,
                                                        onToggle = {
                                                            isCutoutCalibExpanded = !isCutoutCalibExpanded
                                                            prefs.edit()
                                                                .putBoolean("pref_sub_cutout_calib", isCutoutCalibExpanded)
                                                                .putBoolean(LightspeedPreferences.KEY_NOTCH_TEST_BEACON, isCutoutCalibExpanded)
                                                                .apply()
                                                            try { LightspeedAccessibilityService.instance?.reloadPreferences() } catch (_: Exception) {}
                                                            onRefreshNeeded()
                                                        }
                                                    ) {
                                                        PrefDottedSliderRow(context, prefs, LightspeedPreferences.KEY_HARDWARE_CUTOUT_WIDTH, "", "Camera Lens Punch-Hole Diameter (0 to 60dp)", 0, 60, 1, 20)
                                                        PrefDottedSliderRow(context, prefs, LightspeedPreferences.KEY_HARDWARE_CUTOUT_OFFSET_X, "", "Horizontal Center Offset X (-30 to +30dp)", -30, 30, 1, 0)
                                                        PrefDottedSliderRow(context, prefs, LightspeedPreferences.KEY_HARDWARE_CUTOUT_OFFSET_Y, "", "Vertical Center Offset Y (-30 to +30dp)", -30, 30, 1, 0)

                                                        PrefToggleRow(
                                                            prefs = prefs,
                                                            prefKey = LightspeedPreferences.KEY_NOTCH_TEST_BEACON,
                                                            defaultVal = false,
                                                            title = "Live Alignment Test Beacon",
                                                            subtitle = "Renders a live HUD calibration reticle over the camera hole while calibrating.",
                                                            onChanged = { onRefreshNeeded() }
                                                        )
                                                    }

                                                    // 5. Orbital Capsule Calibration
                                                    CollapsibleSubSection(
                                                        title = "Orbital Capsule Calibration (Dynamic Cutout HUD)",
                                                        subtitle = "Layout modes, vertical snugness height, expansion width & orientation",
                                                        isExpanded = isNotchCalibExpanded,
                                                        onToggle = {
                                                            isNotchCalibExpanded = !isNotchCalibExpanded
                                                            prefs.edit()
                                                                .putBoolean("pref_sub_notch_calib", isNotchCalibExpanded)
                                                                .putBoolean(LightspeedPreferences.KEY_NOTCH_TEST_BEACON, isNotchCalibExpanded)
                                                                .apply()
                                                            try { LightspeedAccessibilityService.instance?.reloadPreferences() } catch (_: Exception) {}
                                                            onRefreshNeeded()
                                                        }
                                                    ) {
                                                        // OEM Dynamic Notch / Dynamic Bar Advisory Glass Callout
                                                        if (!isOemNoticeDemoted && !oemFeatureName.isNullOrBlank()) {
                                                            Card(
                                                                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                                                                shape = RoundedCornerShape(12.dp),
                                                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)),
                                                                border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.35f))
                                                            ) {
                                                                Column(modifier = Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                                                    Row(
                                                                        modifier = Modifier.fillMaxWidth(),
                                                                        verticalAlignment = Alignment.CenterVertically
                                                                    ) {
                                                                        Icon(
                                                                            imageVector = Icons.Default.Info,
                                                                            contentDescription = null,
                                                                            tint = MaterialTheme.colorScheme.primary,
                                                                            modifier = Modifier.size(20.dp)
                                                                        )
                                                                        Spacer(modifier = Modifier.width(8.dp))
                                                                        Column(modifier = Modifier.weight(1f)) {
                                                                            Text(
                                                                                text = "OEM Camera Cutout Conflicts",
                                                                                fontSize = 12.sp,
                                                                                fontWeight = FontWeight.Bold,
                                                                                color = Color.White
                                                                            )
                                                                            Text(
                                                                                text = "Your device may have $oemFeatureName enabled. Disable it in system settings to prevent overlapping indicators.",
                                                                                fontSize = 11.sp,
                                                                                color = Color.LightGray.copy(alpha = 0.85f),
                                                                                lineHeight = 14.sp
                                                                            )
                                                                        }
                                                                        IconButton(
                                                                            onClick = {
                                                                                isOemNoticeDemoted = true
                                                                                prefs.edit().putBoolean("pref_oem_notch_notice_demoted", true).apply()
                                                                            },
                                                                            modifier = Modifier.size(24.dp)
                                                                        ) {
                                                                            Icon(Icons.Outlined.VerticalAlignBottom, contentDescription = "Demote to Footnote", tint = Color.LightGray, modifier = Modifier.size(16.dp))
                                                                        }
                                                                    }
                                                                    Button(
                                                                        onClick = { OemNotchDetector.openSearch(context) },
                                                                        modifier = Modifier.fillMaxWidth(),
                                                                        shape = RoundedCornerShape(8.dp),
                                                                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.2f))
                                                                    ) {
                                                                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
                                                                            Icon(Icons.Default.Search, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(14.dp))
                                                                            Spacer(modifier = Modifier.width(6.dp))
                                                                            Text("Open $oemFeatureName Settings", fontWeight = FontWeight.Bold, fontSize = 11.5.sp, color = MaterialTheme.colorScheme.primary)
                                                                        }
                                                                    }
                                                                }
                                                            }
                                                        }

                                                        val currentCapsuleOrientMode = prefs.getString(LightspeedPreferences.KEY_NOTCH_CAPSULE_ORIENTATION_MODE, "both") ?: "both"
                                                        var isCapsuleOrientDropdownOpen by remember { mutableStateOf(false) }
                                                        val capsuleOrientOptions = listOf(
                                                            "both" to "🔄 Both Orientations",
                                                            "portrait_only" to "📱 Portrait Only (Recommended for Cutout HUD)",
                                                            "landscape_only" to "📐 Landscape Only"
                                                        )

                                                        Column(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                                                            Text("CAPSULE ORIENTATION DISPLAY RULE", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary, letterSpacing = 1.sp)
                                                            Spacer(modifier = Modifier.height(4.dp))
                                                            Box {
                                                                OutlinedButton(
                                                                    onClick = { isCapsuleOrientDropdownOpen = true },
                                                                    modifier = Modifier.fillMaxWidth(),
                                                                    shape = RoundedCornerShape(12.dp)
                                                                ) {
                                                                    Row(
                                                                        modifier = Modifier.fillMaxWidth(),
                                                                        horizontalArrangement = Arrangement.SpaceBetween,
                                                                        verticalAlignment = Alignment.CenterVertically
                                                                    ) {
                                                                        Text(capsuleOrientOptions.firstOrNull { it.first == currentCapsuleOrientMode }?.second ?: "🔄 Both Orientations", color = Color.White, fontSize = 12.sp)
                                                                        Icon(Icons.Default.ArrowDropDown, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                                                                    }
                                                                }
                                                                DropdownMenu(
                                                                    expanded = isCapsuleOrientDropdownOpen,
                                                                    onDismissRequest = { isCapsuleOrientDropdownOpen = false },
                                                                    modifier = Modifier
                                                                        .background(Color(0xF012141A))
                                                                        .border(1.dp, Color(0x33FFFFFF), RoundedCornerShape(16.dp)),
                                                                    shape = RoundedCornerShape(16.dp),
                                                                    containerColor = Color(0xF012141A)
                                                                ) {
                                                                    capsuleOrientOptions.forEach { (key, label) ->
                                                                        DropdownMenuItem(
                                                                            modifier = Modifier.heightIn(min = 48.dp),
                                                                            text = { Text(label) },
                                                                            onClick = {
                                                                                isCapsuleOrientDropdownOpen = false
                                                                                prefs.edit().putString(LightspeedPreferences.KEY_NOTCH_CAPSULE_ORIENTATION_MODE, key).apply()
                                                                                try { LightspeedAccessibilityService.instance?.reloadPreferences() } catch (_: Exception) {}
                                                                                onRefreshNeeded()
                                                                            }
                                                                        )
                                                                    }
                                                                }
                                                            }
                                                        }

                                                        val currentCapsuleLayout = prefs.getString(LightspeedPreferences.KEY_NOTCH_CAPSULE_LAYOUT, "unified_right") ?: "unified_right"
                                                        var isCapsuleDropdownOpen by remember { mutableStateOf(false) }
                                                        val capsuleOptions = listOf(
                                                            "unified_right" to "Unified Right (Compact)",
                                                            "dual_wing" to "Dual-Wing Bridge",
                                                            "unified_left" to "Unified Left"
                                                        )

                                                        Column(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                                                            Text("ORBITAL CAPSULE LAYOUT MODE", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary, letterSpacing = 1.sp)
                                                            Spacer(modifier = Modifier.height(4.dp))
                                                            Box {
                                                                OutlinedButton(
                                                                    onClick = { isCapsuleDropdownOpen = true },
                                                                    modifier = Modifier.fillMaxWidth(),
                                                                    shape = RoundedCornerShape(12.dp)
                                                                ) {
                                                                    Row(
                                                                        modifier = Modifier.fillMaxWidth(),
                                                                        horizontalArrangement = Arrangement.SpaceBetween,
                                                                        verticalAlignment = Alignment.CenterVertically
                                                                    ) {
                                                                        Text(capsuleOptions.firstOrNull { it.first == currentCapsuleLayout }?.second ?: "Unified Right (Compact)", color = Color.White, fontSize = 12.sp)
                                                                        Icon(Icons.Default.ArrowDropDown, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                                                                    }
                                                                }
                                                                DropdownMenu(
                                                                    expanded = isCapsuleDropdownOpen,
                                                                    onDismissRequest = { isCapsuleDropdownOpen = false },
                                                                    modifier = Modifier
                                                                        .background(Color(0xF012141A))
                                                                        .border(1.dp, Color(0x33FFFFFF), RoundedCornerShape(16.dp)),
                                                                    shape = RoundedCornerShape(16.dp),
                                                                    containerColor = Color(0xF012141A)
                                                                ) {
                                                                    capsuleOptions.forEach { (key, label) ->
                                                                        DropdownMenuItem(
                                                                            modifier = Modifier.heightIn(min = 48.dp),
                                                                            text = { Text(label) },
                                                                            onClick = {
                                                                                isCapsuleDropdownOpen = false
                                                                                prefs.edit().putString(LightspeedPreferences.KEY_NOTCH_CAPSULE_LAYOUT, key).apply()
                                                                                try { LightspeedAccessibilityService.instance?.reloadPreferences() } catch (_: Exception) {}
                                                                                onRefreshNeeded()
                                                                            }
                                                                        )
                                                                    }
                                                                }
                                                            }
                                                        }

                                                        PrefDottedSliderRow(context, prefs, LightspeedPreferences.KEY_NOTCH_PADDING_SNUGNESS, "", "Capsule Vertical Snugness Padding (0 to 8dp)", 0, 8, 1, 2)
                                                        PrefDottedSliderRow(context, prefs, LightspeedPreferences.KEY_NOTCH_EXPANSION_WIDTH, "", "Capsule Expansion Width (0 to 80dp)", 0, 80, 2, 0)
                                                    }

                                                    // 2. Title Overflow & Marquee Engine
                                                    CollapsibleSubSection(
                                                        title = "Orbital Capsule Marquee Engine",
                                                        subtitle = "Text scroll velocity, pause delays & maximum width",
                                                        isExpanded = isMarqueeSubSectionExpanded,
                                                        onToggle = {
                                                            isMarqueeSubSectionExpanded = !isMarqueeSubSectionExpanded
                                                            prefs.edit().putBoolean("pref_sub_notch_marquee", isMarqueeSubSectionExpanded).apply()
                                                        }
                                                    ) {
                                                        PrefToggleRow(
                                                            prefs = prefs,
                                                            prefKey = LightspeedPreferences.KEY_NOTCH_MARQUEE_ENABLED,
                                                            defaultVal = true,
                                                            title = "Enable Text Marquee Animation",
                                                            subtitle = "Smoothly scrolls overflowing download filenames and song titles across the HUD capsule.",
                                                            onChanged = { onRefreshNeeded() }
                                                        )
                                                        PrefDottedSliderRow(context, prefs, LightspeedPreferences.KEY_NOTCH_MARQUEE_SPEED, "", "Scroll Velocity (px/sec)", 15, 80, 5, 30)
                                                        PrefDottedSliderRow(context, prefs, LightspeedPreferences.KEY_NOTCH_MARQUEE_INITIAL_DELAY, "", "Initial Pause Delay (ms)", 500, 3000, 250, 1500)
                                                        PrefDottedSliderRow(context, prefs, LightspeedPreferences.KEY_NOTCH_MAX_CAPSULE_WIDTH, "", "Max HUD Capsule Width (dp)", 120, 320, 10, 200)
                                                    }

                                                    // OEM Advisory Footnote: OEM Camera Cutout Conflicts (Demoted)
                                                    if (isOemNoticeDemoted && !oemFeatureName.isNullOrBlank()) {
                                                        Card(
                                                            modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
                                                            shape = RoundedCornerShape(12.dp),
                                                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.25f)),
                                                            border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.08f))
                                                        ) {
                                                            Column(
                                                                modifier = Modifier.fillMaxWidth().padding(10.dp),
                                                                verticalArrangement = Arrangement.spacedBy(8.dp)
                                                            ) {
                                                                Row(
                                                                    modifier = Modifier.fillMaxWidth(),
                                                                    verticalAlignment = Alignment.CenterVertically
                                                                ) {
                                                                    Icon(Icons.Outlined.Info, contentDescription = null, tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.7f), modifier = Modifier.size(16.dp))
                                                                    Spacer(modifier = Modifier.width(8.dp))
                                                                    Column(modifier = Modifier.weight(1f)) {
                                                                        Text("OEM Advisory Footnote: OEM Camera Cutout Conflicts", fontWeight = FontWeight.Bold, fontSize = 10.5.sp, color = MaterialTheme.colorScheme.primary.copy(alpha = 0.85f))
                                                                        Text("Your device may have $oemFeatureName enabled. Tap to manage settings if indicators overlap.", fontSize = 9.5.sp, color = Color.LightGray.copy(alpha = 0.65f), lineHeight = 13.sp)
                                                                    }
                                                                    IconButton(
                                                                        onClick = {
                                                                            isOemNoticeDemoted = false
                                                                            prefs.edit().putBoolean("pref_oem_notch_notice_demoted", false).apply()
                                                                        },
                                                                        modifier = Modifier.size(24.dp)
                                                                    ) {
                                                                        Icon(Icons.Outlined.VerticalAlignTop, contentDescription = "Move Upward", tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.85f), modifier = Modifier.size(16.dp))
                                                                    }
                                                                }
                                                                Button(
                                                                    onClick = { OemNotchDetector.openSearch(context) },
                                                                    modifier = Modifier.fillMaxWidth(),
                                                                    shape = RoundedCornerShape(8.dp),
                                                                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.18f)),
                                                                    contentPadding = PaddingValues(vertical = 6.dp)
                                                                ) {
                                                                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
                                                                        Icon(Icons.Default.Settings, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(14.dp))
                                                                        Spacer(modifier = Modifier.width(6.dp))
                                                                        Text("Settings", fontWeight = FontWeight.Bold, fontSize = 11.5.sp, color = MaterialTheme.colorScheme.primary)
                                                                    }
                                                                }
                                                            }
                                                        }
                                                    }
                                                    }
                                                }
                                            }
                                        }
                                    "tactical_hardware" -> {
                                        item(key = "tactical_hardware") {
                                            CompactAccordionSection(
                                                title = com.sbf.lightspeed.system.LightspeedLanguageEngine.resolve(com.sbf.lightspeed.system.LightspeedVocabulary.Key.TACTICAL_HARDWARE),
                                                icon = {
                                                    Icon(
                                                        imageVector = Icons.Outlined.Tune,
                                                        contentDescription = null,
                                                        tint = MaterialTheme.colorScheme.primary,
                                                        modifier = Modifier.size(20.dp)
                                                    )
                                                },
                                                isExpanded = isTacticalHardwareExpanded,
                                                onToggle = {
                                                    toggleSection(1, "tactical_hardware", isTacticalHardwareExpanded) { isTacticalHardwareExpanded = it }
                                                    prefs.edit().putBoolean("pref_section_tactical_hardware_expanded", isTacticalHardwareExpanded).apply()
                                                    if (isTacticalHardwareExpanded && !prefs.getBoolean(LightspeedPreferences.KEY_OEM_SHIELD_COMPLETED, false)) {
                                                        showOemShieldDialog = true
                                                    }
                                                },
                                                headerTrailing = {
                                                    IconButton(
                                                        onClick = { showOemShieldDialog = true },
                                                        modifier = Modifier.size(28.dp)
                                                    ) {
                                                        Icon(
                                                            imageVector = Icons.Default.Security,
                                                            contentDescription = "OEM Compatibility Shield",
                                                            tint = MaterialTheme.colorScheme.primary,
                                                            modifier = Modifier.size(18.dp)
                                                        )
                                                    }
                                                }
                                            ) {
                                                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                                    // Volume Key Matrix (Combos & Chords)
                                                    CollapsibleSubSection(
                                                        title = "Volume Key Matrix",
                                                        subtitle = "Hardware chording, sequences, hold auto-repeat & suppression",
                                                        icon = {
                                                            Icon(
                                                                imageVector = Icons.Outlined.Tune,
                                                                contentDescription = null,
                                                                tint = MaterialTheme.colorScheme.primary,
                                                                modifier = Modifier.size(16.dp)
                                                            )
                                                        },
                                                        isExpanded = isSubVolumeExpanded,
                                                        onToggle = {
                                                            isSubVolumeExpanded = !isSubVolumeExpanded
                                                            prefs.edit().putBoolean("pref_sub_volume_expanded", isSubVolumeExpanded).apply()
                                                        }
                                                    ) {
                                                        PrefToggleRow(
                                                            prefs = prefs,
                                                            prefKey = LightspeedPreferences.KEY_VOL_GESTURES_ENABLED,
                                                            defaultVal = true,
                                                            title = "Enable Volume Key Gestures",
                                                            subtitle = "Low-latency hardware chording, sequences & hold triggers",
                                                            onChanged = { onRefreshNeeded() }
                                                        )

                                                        // 3-Stage Volume Suppression Profile Selector
                                                        val currentProfile = remember(prefs.getString(LightspeedPreferences.KEY_VOLUME_SUPPRESSION_PROFILE, null)) {
                                                            LightspeedKeyEngine.getSuppressionProfile(context)
                                                        }
                                                        var selectedProfile by remember { mutableStateOf(currentProfile) }

                                                        Column(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                                            Text("VOLUME SUPPRESSION PROFILE", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary, letterSpacing = 1.sp)

                                                            val profileOptions = listOf(
                                                                Triple("instant_reflex", "Instant Reflex (0ms)", "Raw pass-through. Volume steps on ACTION_DOWN; chords execute immediately with potential single volume tick leak."),
                                                                Triple("balanced_holds", "Balanced Holds", "Suppresses volume jumps during long-press holds; chords execute immediately."),
                                                                Triple("total_clean", "Total Clean (150ms Buffer)", "Consumes first key event and waits up to 150ms. If second chord key is pressed, fires chord with 0 volume changes. If released without chord/hold, steps volume manually.")
                                                            )

                                                            profileOptions.forEach { (profileKey, title, desc) ->
                                                                val isSelected = selectedProfile == profileKey
                                                                Card(
                                                                    modifier = Modifier
                                                                        .fillMaxWidth()
                                                                        .clip(RoundedCornerShape(12.dp))
                                                                        .clickable {
                                                                            selectedProfile = profileKey
                                                                            prefs.edit().putString(LightspeedPreferences.KEY_VOLUME_SUPPRESSION_PROFILE, profileKey).apply()
                                                                            onRefreshNeeded()
                                                                        },
                                                                    shape = RoundedCornerShape(12.dp),
                                                                    colors = CardDefaults.cardColors(
                                                                        containerColor = if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.18f) else MaterialTheme.colorScheme.surface.copy(alpha = 0.35f)
                                                                    ),
                                                                    border = androidx.compose.foundation.BorderStroke(
                                                                        1.dp,
                                                                        if (isSelected) MaterialTheme.colorScheme.primary else Color.White.copy(alpha = 0.08f)
                                                                    )
                                                                ) {
                                                                    Row(
                                                                        modifier = Modifier.fillMaxWidth().padding(12.dp),
                                                                        verticalAlignment = Alignment.CenterVertically
                                                                    ) {
                                                                        RadioButton(
                                                                            selected = isSelected,
                                                                            onClick = {
                                                                                selectedProfile = profileKey
                                                                                prefs.edit().putString(LightspeedPreferences.KEY_VOLUME_SUPPRESSION_PROFILE, profileKey).apply()
                                                                                onRefreshNeeded()
                                                                            },
                                                                            colors = RadioButtonDefaults.colors(selectedColor = MaterialTheme.colorScheme.primary)
                                                                        )
                                                                        Spacer(modifier = Modifier.width(8.dp))
                                                                        Column(modifier = Modifier.weight(1f)) {
                                                                            Text(title, fontWeight = FontWeight.SemiBold, fontSize = 13.sp, color = Color.White)
                                                                            Spacer(modifier = Modifier.height(2.dp))
                                                                            Text(desc, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f), lineHeight = 14.sp)
                                                                        }
                                                                    }
                                                                }
                                                            }

                                                            if (selectedProfile == "total_clean") {
                                                                Card(
                                                                    modifier = Modifier.fillMaxWidth().padding(top = 2.dp),
                                                                    shape = RoundedCornerShape(10.dp),
                                                                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.3f)),
                                                                    border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.secondary.copy(alpha = 0.4f))
                                                                ) {
                                                                    Row(modifier = Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                                                                        Icon(Icons.Default.Info, contentDescription = null, tint = MaterialTheme.colorScheme.secondary, modifier = Modifier.size(16.dp))
                                                                        Spacer(modifier = Modifier.width(8.dp))
                                                                        Text(
                                                                            "Adds a 150ms buffer window before single volume taps register to guarantee zero volume leaks on chords.",
                                                                            fontSize = 11.sp,
                                                                            color = Color.White.copy(alpha = 0.9f),
                                                                            lineHeight = 14.sp
                                                                        )
                                                                    }
                                                                }
                                                            }
                                                        }

                                                        // Key Hold Auto-Repeat
                                                        var autoRepeatEnabled by rememberSaveable { mutableStateOf(prefs.getBoolean(LightspeedPreferences.KEY_KEY_HOLD_AUTO_REPEAT, false)) }
                                                        PrefToggleRow(
                                                            title = "Hardware Key Hold Auto-Repeat",
                                                            subtitle = "Continuous auto-repeat for hold actions while volume buttons remain pressed",
                                                            isChecked = autoRepeatEnabled,
                                                            onCheckedChange = {
                                                                autoRepeatEnabled = it
                                                                prefs.edit().putBoolean(LightspeedPreferences.KEY_KEY_HOLD_AUTO_REPEAT, it).apply()
                                                                onRefreshNeeded()
                                                            }
                                                        )

                                                        if (autoRepeatEnabled) {
                                                            PrefDottedSliderRow(context, prefs, LightspeedPreferences.KEY_KEY_REPEAT_INTERVAL_MS, "", "Auto-Repeat Interval (ms)", 100, 500, 25, 200)
                                                        }

                                                        // Rocker Advisory Banner
                                                        Card(
                                                            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                                                            shape = RoundedCornerShape(12.dp),
                                                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)),
                                                            border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.35f))
                                                        ) {
                                                            Row(
                                                                modifier = Modifier.fillMaxWidth().padding(12.dp),
                                                                verticalAlignment = Alignment.CenterVertically
                                                            ) {
                                                                Icon(
                                                                    imageVector = Icons.Default.Info,
                                                                    contentDescription = null,
                                                                    tint = MaterialTheme.colorScheme.primary,
                                                                    modifier = Modifier.size(22.dp)
                                                                )
                                                                Spacer(modifier = Modifier.width(10.dp))
                                                                Text(
                                                                    text = "Rocker Notice: Devices with a single physical rocker bar may mechanically lever switches when pressing both ends. If chords misfire, Sequential gestures are recommended for 100% reliability.",
                                                                    fontSize = 11.5.sp,
                                                                    color = Color.White.copy(alpha = 0.9f),
                                                                    lineHeight = 15.sp
                                                                )
                                                            }
                                                        }

                                                        val volumeGestures = listOf(
                                                            Triple(LightspeedPreferences.KEY_VOL_UP_LONG_PRESS, "Volume Up Long Press (~400ms)", "VOL ▲ (HOLD)"),
                                                            Triple(LightspeedPreferences.KEY_VOL_DOWN_LONG_PRESS, "Volume Down Long Press (~400ms)", "VOL ▼ (HOLD)"),
                                                            Triple(LightspeedPreferences.KEY_CHORD_DOWN_HOLD_UP_TAP, "Hold Vol Down + Tap Vol Up", "VOL ▼ + VOL ▲"),
                                                            Triple(LightspeedPreferences.KEY_CHORD_UP_HOLD_DOWN_TAP, "Hold Vol Up + Tap Vol Down", "VOL ▲ + VOL ▼"),
                                                            Triple(LightspeedPreferences.KEY_SEQ_UP_THEN_DOWN, "Sequence: Vol Up → Vol Down (<300ms)", "VOL ▲ ➔ VOL ▼"),
                                                            Triple(LightspeedPreferences.KEY_SEQ_DOWN_THEN_UP, "Sequence: Vol Down → Vol Up (<300ms)", "VOL ▼ ➔ VOL ▲"),
                                                            Triple(LightspeedPreferences.KEY_SEQ_DOWN_TAP_THEN_UP_HOLD, "Tap Vol Down → Hold Vol Up (~400ms)", "VOL ▼ ➔ VOL ▲ (HOLD)"),
                                                            Triple(LightspeedPreferences.KEY_SEQ_UP_TAP_THEN_DOWN_HOLD, "Tap Vol Up → Hold Vol Down (~400ms)", "VOL ▲ ➔ VOL ▼ (HOLD)")
                                                        )

                                                        volumeGestures.forEach { (prefKey, title, badge) ->
                                                            GestureMappingRow(
                                                                context = context,
                                                                prefs = prefs,
                                                                direction = ArrowDirection.TAP,
                                                                isHold = badge.contains("HOLD"),
                                                                keyResName = prefKey,
                                                                defaultTitle = title,
                                                                options = dynamicActionTokens,
                                                                labelCache = tokenLabelCache,
                                                                badgeText = badge,
                                                                showMediaQuickAccess = true
                                                            )
                                                        }
                                                    }

                                                    // Tactical Shortcut to Experimental Labs for Power Button Remapping
                                                    Card(
                                                        modifier = Modifier
                                                            .fillMaxWidth()
                                                            .clip(RoundedCornerShape(12.dp))
                                                            .clickable {
                                                                isExperimentalLabsExpanded = true
                                                                isSubPowerExpanded = true
                                                                onRefreshNeeded()
                                                            }
                                                            .padding(vertical = 4.dp),
                                                        shape = RoundedCornerShape(12.dp),
                                                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.25f)),
                                                        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFFFB300).copy(alpha = 0.35f))
                                                    ) {
                                                        Row(
                                                            modifier = Modifier.fillMaxWidth().padding(12.dp),
                                                            verticalAlignment = Alignment.CenterVertically
                                                        ) {
                                                            Icon(
                                                                imageVector = Icons.Outlined.PowerSettingsNew,
                                                                contentDescription = null,
                                                                tint = Color(0xFFFFB300),
                                                                modifier = Modifier.size(20.dp)
                                                            )
                                                            Spacer(modifier = Modifier.width(10.dp))
                                                            Column(modifier = Modifier.weight(1f)) {
                                                                Text(
                                                                    text = "Power Button Remapping",
                                                                    fontWeight = FontWeight.Bold,
                                                                    fontSize = 12.sp,
                                                                    color = Color.White
                                                                )
                                                                Text(
                                                                    text = "Single, double, hold and chord triggers have moved to the Experimental Labs deck below. Tap to configure.",
                                                                    fontSize = 10.5.sp,
                                                                    color = Color.LightGray.copy(alpha = 0.85f),
                                                                    lineHeight = 14.sp
                                                                )
                                                            }
                                                            Icon(
                                                                imageVector = Icons.Default.ChevronRight,
                                                                contentDescription = null,
                                                                tint = Color(0xFFFFB300).copy(alpha = 0.7f),
                                                                modifier = Modifier.size(18.dp)
                                                            )
                                                        }
                                                    }

                                                    // Hull Tap Sensors (Back Tap)
                                                    CollapsibleSubSection(
                                                        title = "Hull Tap Sensors",
                                                        subtitle = "Accelerometer Z-axis impulse detection for double & triple back taps",
                                                        icon = {
                                                            Icon(
                                                                imageVector = Icons.Outlined.TouchApp,
                                                                contentDescription = null,
                                                                tint = MaterialTheme.colorScheme.primary,
                                                                modifier = Modifier.size(16.dp)
                                                            )
                                                        },
                                                        isExpanded = isSubHullTapExpanded,
                                                        onToggle = {
                                                            isSubHullTapExpanded = !isSubHullTapExpanded
                                                            prefs.edit().putBoolean("pref_sub_hulltap_expanded", isSubHullTapExpanded).apply()
                                                        }
                                                    ) {
                                                        var isBackTapActive by rememberSaveable { mutableStateOf(prefs.getBoolean(LightspeedPreferences.KEY_BACK_TAP_ENABLED, false)) }
                                                        PrefToggleRow(
                                                            title = "Enable Back Tap Gestures",
                                                            subtitle = "Detect double and triple taps on the back of your device",
                                                            isChecked = isBackTapActive,
                                                            onCheckedChange = { checked ->
                                                                isBackTapActive = checked
                                                                prefs.edit().putBoolean(LightspeedPreferences.KEY_BACK_TAP_ENABLED, checked).apply()
                                                                if (!checked) {
                                                                    currentZImpulse = 0f
                                                                    thresholdCrossedFlash = false
                                                                }
                                                                try { LightspeedAccessibilityService.instance?.reloadPreferences() } catch (_: Exception) {}
                                                                onRefreshNeeded()
                                                            }
                                                        )

                                                        val currentScope = prefs.getString(LightspeedPreferences.KEY_BACK_TAP_SCOPE, "screen_on") ?: "screen_on"
                                                        var isScopeDropdownOpen by remember { mutableStateOf(false) }
                                                        val scopeOptions = listOf(
                                                            "screen_on" to "Screen On Only",
                                                            "screen_off" to "Screen Off (High Drain)",
                                                            "always" to "Always (High Drain)"
                                                        )

                                                        Column(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                                                            Text("ACTIVATION SCOPE", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary, letterSpacing = 1.sp)
                                                            Spacer(modifier = Modifier.height(4.dp))
                                                            Box {
                                                                OutlinedButton(
                                                                    onClick = { isScopeDropdownOpen = true },
                                                                    modifier = Modifier.fillMaxWidth(),
                                                                    shape = RoundedCornerShape(12.dp)
                                                                ) {
                                                                    Row(
                                                                        modifier = Modifier.fillMaxWidth(),
                                                                        horizontalArrangement = Arrangement.SpaceBetween,
                                                                        verticalAlignment = Alignment.CenterVertically
                                                                    ) {
                                                                        Text(scopeOptions.firstOrNull { it.first == currentScope }?.second ?: "Screen On Only", color = Color.White)
                                                                        Icon(Icons.Default.ArrowDropDown, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                                                                    }
                                                                }
                                                                DropdownMenu(
                                                                    expanded = isScopeDropdownOpen,
                                                                    onDismissRequest = { isScopeDropdownOpen = false },
                                                                    modifier = Modifier
                                                                        .background(Color(0xF012141A))
                                                                        .border(1.dp, Color(0x33FFFFFF), RoundedCornerShape(16.dp)),
                                                                    shape = RoundedCornerShape(16.dp),
                                                                    containerColor = Color(0xF012141A)
                                                                ) {
                                                                    scopeOptions.forEach { (key, label) ->
                                                                        DropdownMenuItem(
                                                                            modifier = Modifier.heightIn(min = 48.dp),
                                                                            text = { Text(label) },
                                                                            onClick = {
                                                                                isScopeDropdownOpen = false
                                                                                if (key == "screen_off" || key == "always") {
                                                                                    pendingBackTapScope = key
                                                                                    showBatteryWarningDialog = true
                                                                                } else {
                                                                                    prefs.edit().putString(LightspeedPreferences.KEY_BACK_TAP_SCOPE, key).apply()
                                                                                    onRefreshNeeded()
                                                                                }
                                                                            }
                                                                        )
                                                                    }
                                                                }
                                                            }
                                                        }

                                                        // Smart Battery Failsafe Banner
                                                        Card(
                                                            modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
                                                            shape = RoundedCornerShape(12.dp),
                                                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f))
                                                        ) {
                                                            Row(
                                                                modifier = Modifier.fillMaxWidth().padding(10.dp),
                                                                verticalAlignment = Alignment.CenterVertically
                                                            ) {
                                                                Icon(Icons.Default.BatteryChargingFull, contentDescription = null, tint = MaterialTheme.colorScheme.secondary, modifier = Modifier.size(20.dp))
                                                                Spacer(modifier = Modifier.width(8.dp))
                                                                Text(
                                                                    text = "⚡ Smart Battery Failsafe Active: Screen-off sensor is automatically paused and wake lock released if battery drops ≤ 20% or Battery Saver is on.",
                                                                    fontSize = 11.sp,
                                                                    color = Color.LightGray.copy(alpha = 0.85f),
                                                                    lineHeight = 14.sp
                                                                )
                                                            }
                                                        }

                                                        // Live Impulse Calibration Meter (Active strictly when this section is expanded)
                                                        Card(
                                                            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                                                            shape = RoundedCornerShape(14.dp),
                                                            colors = CardDefaults.cardColors(
                                                                containerColor = if (thresholdCrossedFlash) Color(0xFF00E676).copy(alpha = 0.25f) else MaterialTheme.colorScheme.surface.copy(alpha = 0.45f)
                                                            ),
                                                            border = androidx.compose.foundation.BorderStroke(
                                                                1.2.dp,
                                                                if (thresholdCrossedFlash) Color(0xFF00E676) else MaterialTheme.colorScheme.primary.copy(alpha = 0.35f)
                                                            )
                                                        ) {
                                                            Column(modifier = Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                                                Row(
                                                                    modifier = Modifier.fillMaxWidth(),
                                                                    horizontalArrangement = Arrangement.SpaceBetween,
                                                                    verticalAlignment = Alignment.CenterVertically
                                                                ) {
                                                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                                                        Icon(
                                                                            Icons.Default.Speed,
                                                                            contentDescription = null,
                                                                            tint = if (thresholdCrossedFlash) Color(0xFF00E676) else MaterialTheme.colorScheme.primary,
                                                                            modifier = Modifier.size(18.dp)
                                                                        )
                                                                        Spacer(modifier = Modifier.width(6.dp))
                                                                        Text(
                                                                            "LIVE IMPULSE METER",
                                                                            fontSize = 11.sp,
                                                                            fontWeight = FontWeight.Bold,
                                                                            color = if (thresholdCrossedFlash) Color(0xFF00E676) else MaterialTheme.colorScheme.primary,
                                                                            letterSpacing = 1.sp
                                                                        )
                                                                    }
                                                                    Text(
                                                                        text = String.format(Locale.US, "%.1f m/s²", currentZImpulse),
                                                                        fontSize = 14.sp,
                                                                        fontWeight = FontWeight.Bold,
                                                                        color = if (thresholdCrossedFlash) Color(0xFF00E676) else Color.White
                                                                    )
                                                                }

                                                                Box(
                                                                    modifier = Modifier
                                                                        .fillMaxWidth()
                                                                        .height(18.dp)
                                                                        .clip(RoundedCornerShape(9.dp))
                                                                        .background(Color.Black.copy(alpha = 0.5f))
                                                                ) {
                                                                    val impulseFraction = (currentZImpulse / 20.0f).coerceIn(0f, 1f)
                                                                    val threshFraction = (currentThreshold / 20.0f).coerceIn(0f, 1f)

                                                                    Box(
                                                                        modifier = Modifier
                                                                            .fillMaxHeight()
                                                                            .fillMaxWidth(impulseFraction)
                                                                            .background(if (thresholdCrossedFlash) Color(0xFF00E676) else MaterialTheme.colorScheme.primary)
                                                                    )

                                                                    Box(
                                                                        modifier = Modifier
                                                                            .fillMaxHeight()
                                                                            .fillMaxWidth(threshFraction)
                                                                    ) {
                                                                        Box(
                                                                            modifier = Modifier
                                                                                .align(Alignment.CenterEnd)
                                                                                .width(2.5.dp)
                                                                                .fillMaxHeight()
                                                                                .background(Color.White)
                                                                        )
                                                                    }
                                                                }

                                                                Row(
                                                                    modifier = Modifier.fillMaxWidth(),
                                                                    horizontalArrangement = Arrangement.SpaceBetween
                                                                ) {
                                                                    Text("0.0", fontSize = 10.sp, color = Color.White.copy(alpha = 0.5f))
                                                                    Text(
                                                                        if (thresholdCrossedFlash) "✓ THRESHOLD BREACHED" else String.format(Locale.US, "Threshold: %.1f m/s²", currentThreshold),
                                                                        fontSize = 10.5.sp,
                                                                        fontWeight = if (thresholdCrossedFlash) FontWeight.Bold else FontWeight.Normal,
                                                                        color = if (thresholdCrossedFlash) Color(0xFF00E676) else MaterialTheme.colorScheme.primary
                                                                    )
                                                                    Text("20.0 m/s²", fontSize = 10.sp, color = Color.White.copy(alpha = 0.5f))
                                                                }
                                                            }
                                                        }

                                                        PrefFloatDottedSliderRow(
                                                            context = context,
                                                            prefs = prefs,
                                                            keyResName = LightspeedPreferences.KEY_BACK_TAP_THRESHOLD,
                                                            title = "Strike Force Threshold",
                                                            minVal = 3.0f,
                                                            maxVal = 18.0f,
                                                            step = 0.5f,
                                                            defaultVal = 7.5f,
                                                            unit = "m/s²",
                                                            onValueChanged = {
                                                                currentThreshold = it
                                                                try { LightspeedAccessibilityService.instance?.reloadPreferences() } catch (_: Exception) {}
                                                            }
                                                        )

                                                        GestureMappingRow(
                                                            context = context,
                                                            prefs = prefs,
                                                            direction = ArrowDirection.DOUBLE_TAP,
                                                            isHold = false,
                                                            keyResName = LightspeedPreferences.KEY_BACK_TAP_DOUBLE,
                                                            defaultTitle = "Double Back Tap (250-450ms)",
                                                            options = dynamicActionTokens,
                                                            labelCache = tokenLabelCache,
                                                            showMediaQuickAccess = true
                                                        )

                                                        GestureMappingRow(
                                                            context = context,
                                                            prefs = prefs,
                                                            direction = ArrowDirection.TAP,
                                                            isHold = true,
                                                            keyResName = LightspeedPreferences.KEY_BACK_TAP_TRIPLE,
                                                            defaultTitle = "Triple Back Tap (≤ 700ms)",
                                                            options = dynamicActionTokens,
                                                            labelCache = tokenLabelCache,
                                                            showMediaQuickAccess = true
                                                        )
                                                    }
                                                }
                                            }
                                        }
                                    }
                                    "refueling_bay" -> {
                                        item(key = "refueling_bay") {
                                            CompactAccordionSection(
                                                title = com.sbf.lightspeed.system.LightspeedLanguageEngine.resolve(com.sbf.lightspeed.system.LightspeedVocabulary.Key.REFUELING_BAY),
                                                icon = {
                                                    Icon(
                                                        imageVector = Icons.Outlined.BatteryChargingFull,
                                                        contentDescription = null,
                                                        tint = MaterialTheme.colorScheme.primary,
                                                        modifier = Modifier.size(20.dp)
                                                    )
                                                },
                                                isExpanded = isRefuelingExpanded,
                                                onToggle = {
                                                    toggleSection(1, "refueling_bay", isRefuelingExpanded) { isRefuelingExpanded = it }
                                                    prefs.edit().putBoolean("pref_section_refueling_expanded", isRefuelingExpanded).apply()
                                                }
                                            ) {
                                                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                                    val isInfinixOrTranssion = remember {
                                                        val m = android.os.Build.MANUFACTURER.lowercase()
                                                        val b = android.os.Build.BRAND.lowercase()
                                                        m.contains("infinix") || m.contains("transsion") || m.contains("tecno") || m.contains("itel") ||
                                                        b.contains("infinix") || b.contains("transsion") || b.contains("tecno") || b.contains("itel")
                                                    }
                                                    var isWarningDemoted by remember { mutableStateOf(prefs.getBoolean("pref_infinix_standby_warning_demoted", false)) }

                                                    if (isInfinixOrTranssion && !isWarningDemoted) {
                                                        Card(
                                                            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                                                            shape = RoundedCornerShape(12.dp),
                                                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)),
                                                            border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.35f))
                                                        ) {
                                                            Column(modifier = Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                                                Row(
                                                                    modifier = Modifier.fillMaxWidth(),
                                                                    verticalAlignment = Alignment.CenterVertically
                                                                ) {
                                                                    Icon(
                                                                        imageVector = Icons.Default.Info,
                                                                        contentDescription = null,
                                                                        tint = MaterialTheme.colorScheme.primary,
                                                                        modifier = Modifier.size(20.dp)
                                                                    )
                                                                    Spacer(modifier = Modifier.width(8.dp))
                                                                    Column(modifier = Modifier.weight(1f)) {
                                                                        Text(
                                                                            text = "OEM Ambient Display / Dock Conflicts",
                                                                            fontSize = 12.sp,
                                                                            fontWeight = FontWeight.Bold,
                                                                            color = Color.White
                                                                        )
                                                                        Text(
                                                                            text = "OEM standby display style may overlap with Refueling Bay. Disable it in system settings to prevent screen collisions.",
                                                                            fontSize = 11.sp,
                                                                            color = Color.LightGray.copy(alpha = 0.85f),
                                                                            lineHeight = 14.sp
                                                                        )
                                                                    }
                                                                    IconButton(
                                                                        onClick = {
                                                                            isWarningDemoted = true
                                                                            prefs.edit().putBoolean("pref_infinix_standby_warning_demoted", true).apply()
                                                                        },
                                                                        modifier = Modifier.size(24.dp)
                                                                    ) {
                                                                        Icon(
                                                                            imageVector = Icons.Outlined.VerticalAlignBottom,
                                                                            contentDescription = "Demote to Footnote",
                                                                            tint = Color.LightGray,
                                                                            modifier = Modifier.size(16.dp)
                                                                        )
                                                                    }
                                                                }
                                                                Button(
                                                                    onClick = {
                                                                        try {
                                                                            val intent = Intent("com.transsion.specialfunction.ACTION_STANDBY").apply {
                                                                                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                                                            }
                                                                            context.startActivity(intent)
                                                                        } catch (_: Exception) {
                                                                            try {
                                                                                context.startActivity(Intent(android.provider.Settings.ACTION_SETTINGS).apply {
                                                                                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                                                                })
                                                                            } catch (_: Exception) {}
                                                                        }
                                                                    },
                                                                    modifier = Modifier.fillMaxWidth(),
                                                                    shape = RoundedCornerShape(8.dp),
                                                                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.2f))
                                                                ) {
                                                                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
                                                                        Icon(Icons.Default.Settings, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(14.dp))
                                                                        Spacer(modifier = Modifier.width(6.dp))
                                                                        Text("Open OEM Standby Settings", fontWeight = FontWeight.Bold, fontSize = 11.5.sp, color = MaterialTheme.colorScheme.primary)
                                                                    }
                                                                }
                                                            }
                                                        }
                                                    }

                                                    val currentTrigger = prefs.getString(LightspeedPreferences.KEY_REFUELING_BAY_TRIGGER, "disabled") ?: "disabled"
                                                    var isTriggerDropdownOpen by remember { mutableStateOf(false) }
                                                    val triggerOptions = listOf(
                                                        "disabled" to "Disabled (Manual Launch Only)",
                                                        "charging_screen_off" to "Screen-Off While Charging / Plugged While Locked",
                                                        "charging_dock_landscape" to "Charging in Landscape Dock Orientation",
                                                        "screen_timeout" to "Screen-Off / Standby (Even When Not Charging)",
                                                        "screensaver_only" to "Android Screensaver (DreamService Only)"
                                                    )

                                                    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                                                        Text("AUTO-LAUNCH CHARGING TRIGGER", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary, letterSpacing = 1.sp)
                                                        Spacer(modifier = Modifier.height(4.dp))
                                                        Box {
                                                            OutlinedButton(
                                                                onClick = { isTriggerDropdownOpen = true },
                                                                modifier = Modifier.fillMaxWidth(),
                                                                shape = RoundedCornerShape(12.dp)
                                                            ) {
                                                                Row(
                                                                    modifier = Modifier.fillMaxWidth(),
                                                                    horizontalArrangement = Arrangement.SpaceBetween,
                                                                    verticalAlignment = Alignment.CenterVertically
                                                                ) {
                                                                    Text(
                                                                        triggerOptions.firstOrNull {
                                                                            it.first == currentTrigger ||
                                                                            (it.first == "charging_dock_landscape" && currentTrigger == "landscape_charging") ||
                                                                            (it.first == "charging_screen_off" && currentTrigger == "always_charging")
                                                                        }?.second ?: "Disabled",
                                                                        color = Color.White,
                                                                        fontSize = 12.sp
                                                                    )
                                                                    Icon(Icons.Default.ArrowDropDown, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                                                                }
                                                            }
                                                            DropdownMenu(
                                                                expanded = isTriggerDropdownOpen,
                                                                onDismissRequest = { isTriggerDropdownOpen = false },
                                                                modifier = Modifier
                                                                    .background(Color(0xF012141A))
                                                                    .border(1.dp, Color(0x33FFFFFF), RoundedCornerShape(16.dp)),
                                                                shape = RoundedCornerShape(16.dp),
                                                                containerColor = Color(0xF012141A)
                                                            ) {
                                                                triggerOptions.forEach { (key, label) ->
                                                                    DropdownMenuItem(
                                                                        modifier = Modifier.heightIn(min = 48.dp),
                                                                        text = { Text(label) },
                                                                        onClick = {
                                                                            isTriggerDropdownOpen = false
                                                                            prefs.edit().putString(LightspeedPreferences.KEY_REFUELING_BAY_TRIGGER, key).apply()
                                                                            onRefreshNeeded()
                                                                        }
                                                                    )
                                                                }
                                                            }
                                                        }
                                                    }

                                                    val currentTimeout = prefs.getString(LightspeedPreferences.KEY_REFUELING_SLEEP_TIMEOUT, "60s") ?: "60s"
                                                    var isTimeoutDropdownOpen by remember { mutableStateOf(false) }
                                                    val timeoutOptions = listOf(
                                                        "5s" to "5 Seconds (Rapid Sleep Test)",
                                                        "15s" to "15 Seconds",
                                                        "30s" to "30 Seconds",
                                                        "60s" to "60 Seconds (Recommended)",
                                                        "120s" to "2 Minutes",
                                                        "300s" to "5 Minutes",
                                                        "never" to "Never (⚠️ AMOLED Risk)"
                                                    )

                                                    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                                                        Text("INACTIVITY SLEEP TIMEOUT", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary, letterSpacing = 1.sp)
                                                        Spacer(modifier = Modifier.height(4.dp))
                                                        Box {
                                                            OutlinedButton(
                                                                onClick = { isTimeoutDropdownOpen = true },
                                                                modifier = Modifier.fillMaxWidth(),
                                                                shape = RoundedCornerShape(12.dp)
                                                            ) {
                                                                Row(
                                                                    modifier = Modifier.fillMaxWidth(),
                                                                    horizontalArrangement = Arrangement.SpaceBetween,
                                                                    verticalAlignment = Alignment.CenterVertically
                                                                ) {
                                                                    Text(timeoutOptions.firstOrNull { it.first == currentTimeout }?.second ?: "60 Seconds", color = Color.White, fontSize = 12.sp)
                                                                    Icon(Icons.Default.ArrowDropDown, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                                                                }
                                                            }
                                                            DropdownMenu(
                                                                expanded = isTimeoutDropdownOpen,
                                                                onDismissRequest = { isTimeoutDropdownOpen = false },
                                                                modifier = Modifier
                                                                    .background(Color(0xF012141A))
                                                                    .border(1.dp, Color(0x33FFFFFF), RoundedCornerShape(16.dp)),
                                                                shape = RoundedCornerShape(16.dp),
                                                                containerColor = Color(0xF012141A)
                                                            ) {
                                                                timeoutOptions.forEach { (key, label) ->
                                                                    DropdownMenuItem(
                                                                        modifier = Modifier.heightIn(min = 48.dp),
                                                                        text = { Text(label) },
                                                                        onClick = {
                                                                            isTimeoutDropdownOpen = false
                                                                            if (key == "never") {
                                                                                showAmoledWarningDialog = true
                                                                            } else {
                                                                                prefs.edit().putString(LightspeedPreferences.KEY_REFUELING_SLEEP_TIMEOUT, key).apply()
                                                                                onRefreshNeeded()
                                                                            }
                                                                        }
                                                                    )
                                                                }
                                                            }
                                                        }
                                                    }

                                                    PrefToggleRow(
                                                        prefs = prefs,
                                                        prefKey = LightspeedPreferences.KEY_REFUELING_STACK_REMEMBER_PAGE,
                                                        defaultVal = false,
                                                        title = "Remember Smart Stack Active Page",
                                                        subtitle = "Resume the last active widget in Smart Stack instead of resetting to the first widget on launch.",
                                                        onChanged = { onRefreshNeeded() }
                                                    )

                                                    PrefToggleRow(
                                                        prefs = prefs,
                                                        prefKey = LightspeedPreferences.KEY_REFUELING_PIXEL_SHIFT,
                                                        defaultVal = true,
                                                        title = "Enable Pixel Shift Burn-In Shield",
                                                        subtitle = "Subtly shifts text and indicators by 2-4px every 2 minutes to protect OLED panels.",
                                                        onChanged = { onRefreshNeeded() }
                                                    )

                                                    Button(
                                                        onClick = {
                                                            val intent = Intent(context, com.sbf.lightspeed.LightspeedRefuelingActivity::class.java).apply {
                                                                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS)
                                                            }
                                                            context.startActivity(intent)
                                                        },
                                                        modifier = Modifier.fillMaxWidth(),
                                                        shape = RoundedCornerShape(12.dp),
                                                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.25f))
                                                    ) {
                                                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
                                                            Icon(Icons.Default.PlayArrow, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                                                            Spacer(modifier = Modifier.width(8.dp))
                                                            Text("Launch Refueling Bay Dashboard", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = MaterialTheme.colorScheme.primary)
                                                        }
                                                    }

                                                    // OEM Advisory Footnote: OEM Ambient Display / Dock Conflicts (Demoted)
                                                    if (isWarningDemoted && isInfinixOrTranssion) {
                                                        Card(
                                                            modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
                                                            shape = RoundedCornerShape(12.dp),
                                                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.25f)),
                                                            border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.08f))
                                                        ) {
                                                            Column(
                                                                modifier = Modifier.fillMaxWidth().padding(10.dp),
                                                                verticalArrangement = Arrangement.spacedBy(8.dp)
                                                            ) {
                                                                Row(
                                                                    modifier = Modifier.fillMaxWidth(),
                                                                    verticalAlignment = Alignment.CenterVertically
                                                                ) {
                                                                    Icon(Icons.Outlined.Info, contentDescription = null, tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.7f), modifier = Modifier.size(16.dp))
                                                                    Spacer(modifier = Modifier.width(8.dp))
                                                                    Column(modifier = Modifier.weight(1f)) {
                                                                        Text("OEM Advisory Footnote: OEM Ambient Display / Dock Conflicts", fontWeight = FontWeight.Bold, fontSize = 10.5.sp, color = MaterialTheme.colorScheme.primary.copy(alpha = 0.85f))
                                                                        Text("OEM standby display style may overlap with Refueling Bay. Tap to manage settings.", fontSize = 9.5.sp, color = Color.LightGray.copy(alpha = 0.65f), lineHeight = 13.sp)
                                                                    }
                                                                    IconButton(
                                                                        onClick = {
                                                                            isWarningDemoted = false
                                                                            prefs.edit().putBoolean("pref_infinix_standby_warning_demoted", false).apply()
                                                                        },
                                                                        modifier = Modifier.size(24.dp)
                                                                    ) {
                                                                        Icon(Icons.Outlined.VerticalAlignTop, contentDescription = "Move Upward", tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.85f), modifier = Modifier.size(16.dp))
                                                                    }
                                                                }
                                                                Button(
                                                                    onClick = {
                                                                        try {
                                                                            val intent = Intent("com.transsion.specialfunction.ACTION_STANDBY").apply {
                                                                                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                                                            }
                                                                            context.startActivity(intent)
                                                                        } catch (_: Exception) {
                                                                            try {
                                                                                context.startActivity(Intent(android.provider.Settings.ACTION_SETTINGS).apply {
                                                                                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                                                                })
                                                                            } catch (_: Exception) {}
                                                                        }
                                                                    },
                                                                    modifier = Modifier.fillMaxWidth(),
                                                                    shape = RoundedCornerShape(8.dp),
                                                                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.18f)),
                                                                    contentPadding = PaddingValues(vertical = 6.dp)
                                                                ) {
                                                                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
                                                                        Icon(Icons.Default.Settings, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(14.dp))
                                                                        Spacer(modifier = Modifier.width(6.dp))
                                                                        Text("Settings", fontWeight = FontWeight.Bold, fontSize = 11.5.sp, color = MaterialTheme.colorScheme.primary)
                                                                    }
                                                                }
                                                            }
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                    }
                                    "config_vault" -> {
                                        item(key = "config_vault") {
                                            CompactAccordionSection(
                                                title = com.sbf.lightspeed.system.LightspeedLanguageEngine.resolve(com.sbf.lightspeed.system.LightspeedVocabulary.Key.SHIP_DATA_VAULT),
                                                icon = {
                                                    Icon(
                                                        imageVector = Icons.Outlined.Lock,
                                                        contentDescription = null,
                                                        tint = MaterialTheme.colorScheme.primary,
                                                        modifier = Modifier.size(20.dp)
                                                    )
                                                },
                                                isExpanded = isConfigVaultExpanded,
                                                onToggle = {
                                                    toggleSection(1, "config_vault", isConfigVaultExpanded) { isConfigVaultExpanded = it }
                                                    prefs.edit().putBoolean("pref_section_backup_expanded", isConfigVaultExpanded).apply()
                                                }
                                            ) {
                                                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                                    // Export Card
                                                    Row(
                                                        modifier = Modifier
                                                            .fillMaxWidth()
                                                            .clip(RoundedCornerShape(14.dp))
                                                            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.5f))
                                                            .clickable { exportLauncher.launch("lightspeed-backup.json") }
                                                            .padding(14.dp),
                                                        verticalAlignment = Alignment.CenterVertically
                                                    ) {
                                                        Box(
                                                            modifier = Modifier
                                                                .size(40.dp)
                                                                .clip(CircleShape)
                                                                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)),
                                                            contentAlignment = Alignment.Center
                                                        ) {
                                                            Icon(Icons.Default.CloudUpload, contentDescription = "Export", tint = MaterialTheme.colorScheme.primary)
                                                        }
                                                        Spacer(modifier = Modifier.width(12.dp))
                                                        Column(modifier = Modifier.weight(1f)) {
                                                            Text("Export Configuration (JSON)", fontWeight = FontWeight.SemiBold, fontSize = 15.sp, color = Color.White)
                                                            Text("Save all gesture sets, sliders, physics, and shortcuts to a standalone JSON file", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f))
                                                        }
                                                    }

                                                    // Import Card
                                                    Row(
                                                        modifier = Modifier
                                                            .fillMaxWidth()
                                                            .clip(RoundedCornerShape(14.dp))
                                                            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.5f))
                                                            .clickable { showImportOptionsDialog = true }
                                                            .padding(14.dp),
                                                        verticalAlignment = Alignment.CenterVertically
                                                    ) {
                                                        Box(
                                                            modifier = Modifier
                                                                .size(40.dp)
                                                                .clip(CircleShape)
                                                                .background(MaterialTheme.colorScheme.secondary.copy(alpha = 0.2f)),
                                                            contentAlignment = Alignment.Center
                                                        ) {
                                                            Icon(Icons.Default.CloudDownload, contentDescription = "Import", tint = MaterialTheme.colorScheme.secondary)
                                                        }
                                                        Spacer(modifier = Modifier.width(12.dp))
                                                        Column(modifier = Modifier.weight(1f)) {
                                                            Text("Import Configuration (JSON)", fontWeight = FontWeight.SemiBold, fontSize = 15.sp, color = Color.White)
                                                            Text("Restore complete settings from a previous Lightspeed backup file", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f))
                                                        }
                                                    }

                                                    // Reset Card
                                                    Row(
                                                        modifier = Modifier
                                                            .fillMaxWidth()
                                                            .clip(RoundedCornerShape(14.dp))
                                                            .background(MaterialTheme.colorScheme.error.copy(alpha = 0.12f))
                                                            .clickable { showResetConfirmDialog = true }
                                                            .padding(14.dp),
                                                        verticalAlignment = Alignment.CenterVertically
                                                    ) {
                                                        Box(
                                                            modifier = Modifier
                                                                .size(40.dp)
                                                                .clip(CircleShape)
                                                                .background(MaterialTheme.colorScheme.error.copy(alpha = 0.2f)),
                                                            contentAlignment = Alignment.Center
                                                        ) {
                                                            Icon(Icons.Default.RestartAlt, contentDescription = "Reset", tint = MaterialTheme.colorScheme.error)
                                                        }
                                                        Spacer(modifier = Modifier.width(12.dp))
                                                        Column(modifier = Modifier.weight(1f)) {
                                                            Text("Reset to Factory Defaults", fontWeight = FontWeight.SemiBold, fontSize = 15.sp, color = MaterialTheme.colorScheme.error)
                                                            Text("Wipe custom settings and revert to pristine defaults", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f))
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                    }
                                    "experimental_labs" -> {
                                        item(key = "experimental_labs") {
                                            val cautionAmber = Color(0xFFFFB300)
                                            HazardAccordionSection(
                                                title = com.sbf.lightspeed.system.LightspeedLanguageEngine.resolve(com.sbf.lightspeed.system.LightspeedVocabulary.Key.EXPERIMENTAL_LABS),
                                                subtitle = "Features in this deck are unstable and/or not well tested yet. Use at your own discretion.",
                                                isExpanded = isExperimentalLabsExpanded,
                                                onToggle = {
                                                    toggleSection(1, "experimental_labs", isExperimentalLabsExpanded) { isExperimentalLabsExpanded = it }
                                                    prefs.edit()
                                                        .putBoolean(LightspeedPreferences.KEY_SECTION_EXPERIMENTAL_LABS_EXPANDED, isExperimentalLabsExpanded)
                                                        .apply()
                                                    try { LightspeedAccessibilityService.instance?.reloadPreferences() } catch (_: Exception) {}
                                                }
                                            ) {
                                                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                                    // Maintenance Bay Intro Badge
                                                    Card(
                                                        modifier = Modifier.fillMaxWidth(),
                                                        shape = RoundedCornerShape(12.dp),
                                                        colors = CardDefaults.cardColors(containerColor = cautionAmber.copy(alpha = 0.08f)),
                                                        border = androidx.compose.foundation.BorderStroke(1.dp, cautionAmber.copy(alpha = 0.35f))
                                                    ) {
                                                        Row(
                                                            modifier = Modifier.fillMaxWidth().padding(10.dp),
                                                            verticalAlignment = Alignment.CenterVertically
                                                        ) {
                                                            Icon(Icons.Default.WarningAmber, contentDescription = null, tint = cautionAmber, modifier = Modifier.size(18.dp))
                                                            Spacer(modifier = Modifier.width(8.dp))
                                                            Text(
                                                                text = "Experimental Labs: Features in this deck are unstable and/or not well tested yet. Use at your own discretion.",
                                                                fontSize = 11.sp,
                                                                color = cautionAmber.copy(alpha = 0.95f),
                                                                lineHeight = 14.sp
                                                            )
                                                        }
                                                    }

                                                    // 1. Power Button Remapping
                                                    CollapsibleSubSection(
                                                        title = "Power Button Remapping",
                                                        subtitle = "Single, Double, Hold (~400ms) & Press-then-Hold triggers",
                                                        icon = {
                                                            Icon(
                                                                imageVector = Icons.Outlined.PowerSettingsNew,
                                                                contentDescription = null,
                                                                tint = cautionAmber,
                                                                modifier = Modifier.size(16.dp)
                                                            )
                                                        },
                                                        isExpanded = isSubPowerExpanded,
                                                        onToggle = {
                                                            isSubPowerExpanded = !isSubPowerExpanded
                                                            prefs.edit().putBoolean("pref_sub_power_expanded", isSubPowerExpanded).apply()
                                                        }
                                                    ) {
                                                        // Emergency Reset Hardware Notice Banner
                                                        Card(
                                                            modifier = Modifier.fillMaxWidth().padding(bottom = 6.dp),
                                                            shape = RoundedCornerShape(12.dp),
                                                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)),
                                                            border = androidx.compose.foundation.BorderStroke(1.dp, cautionAmber.copy(alpha = 0.3f))
                                                        ) {
                                                            Row(
                                                                modifier = Modifier.fillMaxWidth().padding(10.dp),
                                                                verticalAlignment = Alignment.CenterVertically
                                                            ) {
                                                                Icon(
                                                                    imageVector = Icons.Default.WarningAmber,
                                                                    contentDescription = null,
                                                                    tint = cautionAmber,
                                                                    modifier = Modifier.size(18.dp)
                                                                )
                                                                Spacer(modifier = Modifier.width(8.dp))
                                                                Text(
                                                                    text = "Emergency Notice: 10s hardware power hold forces device reset. Disabling Accessibility restores system defaults.",
                                                                    fontSize = 11.sp,
                                                                    color = Color.LightGray.copy(alpha = 0.85f),
                                                                    lineHeight = 14.sp
                                                                )
                                                            }
                                                        }

                                                        PrefToggleRow(
                                                            prefs = prefs,
                                                            prefKey = LightspeedPreferences.KEY_POWER_GESTURES_ENABLED,
                                                            defaultVal = true,
                                                            title = "Enable Power Button Gestures",
                                                            subtitle = "Low-latency physical power button gesture interception",
                                                            onChanged = { onRefreshNeeded() }
                                                        )

                                                        val powerGestures = listOf(
                                                            Triple(LightspeedPreferences.KEY_POWER_SINGLE_PRESS, "Single Press", LightspeedKeyEngine.PowerTriggerSlot.POWER_SINGLE_PRESS),
                                                            Triple(LightspeedPreferences.KEY_POWER_DOUBLE_PRESS, "Double Press (<300ms)", LightspeedKeyEngine.PowerTriggerSlot.POWER_DOUBLE_PRESS),
                                                            Triple(LightspeedPreferences.KEY_POWER_HOLD, "Hold (~400ms)", LightspeedKeyEngine.PowerTriggerSlot.POWER_HOLD),
                                                            Triple(LightspeedPreferences.KEY_POWER_PRESS_THEN_HOLD, "Press-then-Hold", LightspeedKeyEngine.PowerTriggerSlot.POWER_PRESS_THEN_HOLD)
                                                        )

                                                        powerGestures.forEach { (prefKey, title, slot) ->
                                                            PowerGestureMappingRow(
                                                                context = context,
                                                                prefs = prefs,
                                                                prefKey = prefKey,
                                                                title = title,
                                                                slot = slot,
                                                                isSinglePress = slot == LightspeedKeyEngine.PowerTriggerSlot.POWER_SINGLE_PRESS,
                                                                isSinglePressUnlocked = isSinglePressUnlocked,
                                                                onSinglePressUnlockStep = {
                                                                    singlePressTapCount++
                                                                    if (singlePressTapCount in 4..6) {
                                                                        android.widget.Toast.makeText(
                                                                            context,
                                                                            "You are ${7 - singlePressTapCount} steps away from unlocking Single Press remap.",
                                                                            android.widget.Toast.LENGTH_SHORT
                                                                        ).show()
                                                                    } else if (singlePressTapCount >= 7) {
                                                                        isSinglePressUnlocked = true
                                                                        prefs.edit().putBoolean(LightspeedPreferences.KEY_POWER_SINGLE_PRESS_UNLOCKED, true).apply()
                                                                        android.widget.Toast.makeText(
                                                                            context,
                                                                            "Single Press Remapping Unlocked",
                                                                            android.widget.Toast.LENGTH_SHORT
                                                                        ).show()
                                                                        com.sbf.lightspeed.system.LightspeedHapticEngine.heavyClick(context)
                                                                        try { LightspeedAccessibilityService.instance?.reloadPreferences() } catch (_: Exception) {}
                                                                        onRefreshNeeded()
                                                                    }
                                                                },
                                                                installedTools = installedTacticalTools,
                                                                options = dynamicActionTokens,
                                                                labelCache = tokenLabelCache,
                                                                onRefreshNeeded = onRefreshNeeded
                                                            )
                                                        }
                                                    }

                                                    // 2. Core Cooling Schedule
                                                    var isCoreCoolingExpanded by rememberSaveable { mutableStateOf(prefs.getBoolean("pref_sub_core_cooling_labs", true)) }
                                                    CollapsibleSubSection(
                                                        title = "Core Cooling Schedule",
                                                        subtitle = "Configurable weekly maintenance reminder",
                                                        icon = {
                                                            Icon(
                                                                imageVector = Icons.Outlined.AcUnit,
                                                                contentDescription = null,
                                                                tint = cautionAmber,
                                                                modifier = Modifier.size(16.dp)
                                                            )
                                                        },
                                                        isExpanded = isCoreCoolingExpanded,
                                                        onToggle = {
                                                            isCoreCoolingExpanded = !isCoreCoolingExpanded
                                                            prefs.edit().putBoolean("pref_sub_core_cooling_labs", isCoreCoolingExpanded).apply()
                                                        }
                                                    ) {
                                                        var coreCoolingEnabled by rememberSaveable { mutableStateOf(prefs.getBoolean(LightspeedPreferences.KEY_CORE_COOLING_ENABLED, false)) }
                                                        var targetDay by rememberSaveable { mutableIntStateOf(prefs.getInt(LightspeedPreferences.KEY_CORE_COOLING_DAY_OF_WEEK, java.util.Calendar.SUNDAY)) }
                                                        var targetHour by rememberSaveable { mutableIntStateOf(prefs.getInt(LightspeedPreferences.KEY_CORE_COOLING_HOUR, 3)) }

                                                        PrefToggleRow(
                                                            title = "Scheduled Core Cooling Reminder",
                                                            subtitle = "Dispatches a gentle reminder when system core cooling is recommended.",
                                                            isChecked = coreCoolingEnabled,
                                                            onCheckedChange = { checked ->
                                                                coreCoolingEnabled = checked
                                                                prefs.edit().putBoolean(LightspeedPreferences.KEY_CORE_COOLING_ENABLED, checked).apply()
                                                                onRefreshNeeded()
                                                            }
                                                        )

                                                        if (coreCoolingEnabled) {
                                                            CoreCoolingRotarySchedulePicker(
                                                                selectedDay = targetDay,
                                                                selectedHour = targetHour,
                                                                onScheduleChanged = { day, hour ->
                                                                    targetDay = day
                                                                    targetHour = hour
                                                                    prefs.edit()
                                                                        .putInt(LightspeedPreferences.KEY_CORE_COOLING_DAY_OF_WEEK, day)
                                                                        .putInt(LightspeedPreferences.KEY_CORE_COOLING_HOUR, hour)
                                                                        .apply()
                                                                    onRefreshNeeded()
                                                                }
                                                            )
                                                        }

                                                        // Strict Cold-Start Policy Notice (Strictly Placed Inside Core Cooling at Bottom)
                                                        Card(
                                                            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                                                            shape = RoundedCornerShape(10.dp),
                                                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.4f)),
                                                            border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.08f))
                                                        ) {
                                                            Text(
                                                                text = "STRICT COLD-START POLICY: Zero automatic reboots. When weekly cooling cycle is reached, dispatches a silent status reminder to the HUD and Orbital Capsule.",
                                                                fontSize = 10.5.sp,
                                                                color = Color.LightGray.copy(alpha = 0.85f),
                                                                modifier = Modifier.padding(10.dp),
                                                                lineHeight = 13.sp
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
