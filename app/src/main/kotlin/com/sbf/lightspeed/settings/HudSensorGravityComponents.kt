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
                onToggle = onToggleStatusBarGeo
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

            var isEngineEnabled by remember { mutableStateOf(prefs.getBoolean("pref_synthetic_gravity_enabled", true)) }
            PrefToggleRow(
                title = "Enable Synthetic Gravity Engine",
                subtitle = "Master switch to enable or disable all custom per-app rotation rules and bucket logic.",
                isChecked = isEngineEnabled,
                onCheckedChange = { checked ->
                    isEngineEnabled = checked
                    prefs.edit().putBoolean("pref_synthetic_gravity_enabled", checked).apply()
                    LightspeedOrientationManager.evaluateGravityCascade(context)
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
                            onCheckedChange = {
                                val ok = LightspeedOrientationEngine.setFaceRotateEnabled(context, it)
                                if (ok) {
                                    isFaceRotateActive = it
                                } else {
                                    Toast.makeText(context, "Elevated permission needed. Opening system settings...", Toast.LENGTH_SHORT).show()
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
