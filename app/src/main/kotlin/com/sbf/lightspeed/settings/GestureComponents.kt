package com.sbf.lightspeed.settings

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import kotlin.math.roundToInt
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sbf.lightspeed.CockpitGearPickerActivity
import com.sbf.lightspeed.LightspeedAccessibilityService
import com.sbf.lightspeed.system.LightspeedPreferences
import kotlinx.coroutines.launch
import androidx.compose.ui.text.style.TextOverflow
import com.sbf.lightspeed.system.LightspeedHapticEngine
import kotlin.math.roundToInt



@Composable
fun ThreeWayTacticalSelector(
    title: String,
    subtitle: String,
    selectedMode: String,
    onSelect: (String) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(title, fontWeight = FontWeight.SemiBold, fontSize = 13.sp, color = Color.White)
        Text(subtitle, fontSize = 11.sp, color = MaterialTheme.colorScheme.primary.copy(alpha = 0.9f))

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(Color.White.copy(alpha = 0.06f))
                .padding(3.dp),
            horizontalArrangement = Arrangement.spacedBy(3.dp)
        ) {
            val options = listOf(
                "left" to "◂ CLONE LEFT",
                "independent" to "◈ INDEPENDENT",
                "right" to "CLONE RIGHT ▸"
            )

            options.forEach { (modeKey, modeTitle) ->
                val isSelected = selectedMode == modeKey
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(9.dp))
                        .background(if (isSelected) MaterialTheme.colorScheme.primary else Color.Transparent)
                        .clickable { onSelect(modeKey) }
                        .padding(vertical = 8.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = modeTitle,
                        fontSize = 10.sp,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                        color = if (isSelected) MaterialTheme.colorScheme.onPrimary else Color.White.copy(alpha = 0.7f),
                        maxLines = 1
                    )
                }
            }
        }
    }
}

@Composable
fun GestureMappingRow(
    context: Context,
    prefs: SharedPreferences,
    direction: ArrowDirection,
    isHold: Boolean,
    keyResName: String,
    defaultTitle: String,
    options: List<String>,
    labelCache: Map<String, String>,
    showMediaQuickAccess: Boolean = false,
    badgeText: String? = null,
    customLeading: (@Composable () -> Unit)? = null,
    showMooringRope: Boolean = false,
    isMooringTied: Boolean = true,
    onToggleMooring: ((Boolean) -> Unit)? = null
) {
    val key = remember(keyResName) { resKey(context, keyResName) }
    var currentRawValue by remember { mutableStateOf(prefs.getString(key, "none") ?: "none") }
    var showScrubMenu by remember { mutableStateOf(false) }

    val pickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            currentRawValue = prefs.getString(key, "none") ?: "none"
        }
    }

    val activeLabel = remember(currentRawValue, labelCache[currentRawValue]) {
        if (currentRawValue.isBlank() || currentRawValue == "none") {
            "None"
        } else if (currentRawValue.startsWith("shortcut:")) {
            val resolved = com.sbf.lightspeed.system.LightspeedShortcutManager.resolveLabel(context, currentRawValue)
            if (resolved.isNotBlank()) resolved else (labelCache[currentRawValue] ?: currentRawValue)
        } else {
            labelCache[currentRawValue] ?: currentRawValue
        }
    }

    val hasControls = showMediaQuickAccess ||
            (currentRawValue == "system:screen_timeout") ||
            (currentRawValue == "system:brightness") ||
            (currentRawValue == "system:volume") ||
            (currentRawValue == "system:media_skip_forward" || currentRawValue == "system:media_skip_backward")

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .wrapContentHeight()
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.3f))
    ) {
        if (showMooringRope && onToggleMooring != null) {
            Spacer(modifier = Modifier.height(5.dp))
            M3RowMooringRope(
                context = context,
                isTied = isMooringTied,
                onToggle = onToggleMooring,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp)
            )
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clickable {
                    if (direction == ArrowDirection.SCRUB) {
                        showScrubMenu = true
                    } else {
                        val intent = Intent(context, CockpitGearPickerActivity::class.java).apply {
                            putExtra("SINGLE_SELECT_PREF_KEY", key)
                            putExtra("SINGLE_SELECT_TITLE", "$defaultTitle Action")
                            putExtra("IS_HOLD_GESTURE", isHold || direction == ArrowDirection.SCRUB)
                        }
                        pickerLauncher.launch(intent)
                    }
                }
                .padding(
                    start = 12.dp,
                    end = 12.dp,
                    top = if (showMooringRope) 3.dp else 12.dp,
                    bottom = 12.dp
                )
        ) {
            // Line 1 & Line 2: Gesture Tracer Icon / Monospace Badge + Full-Width Title & Subtitle
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (customLeading != null) {
                Box(modifier = Modifier.wrapContentWidth()) {
                    customLeading()
                }
                Spacer(modifier = Modifier.width(10.dp))
            } else if (!badgeText.isNullOrBlank()) {
                Surface(
                    modifier = Modifier.wrapContentWidth(),
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.16f),
                    border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.5f))
                ) {
                    Text(
                        text = badgeText,
                        fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                        maxLines = 1,
                        softWrap = false
                    )
                }
                Spacer(modifier = Modifier.width(10.dp))
            } else {
                GestureTrailTracer(direction, isHold, MaterialTheme.colorScheme.primary, Modifier.size(32.dp))
                Spacer(modifier = Modifier.width(10.dp))
            }
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(start = 2.dp)
            ) {
                Text(
                    text = defaultTitle,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 13.5.sp,
                    color = Color.White,
                    maxLines = 1,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = "Active Map: $activeLabel",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.primary,
                    maxLines = 1,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                )
            }
        }

        // Line 3: Dedicated Action Chips & Dropdowns (Horizontally scrollable, crisp layout)
        if (hasControls) {
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 46.dp)
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // 1. Quick Media Actions Dropdown Menu (Hardware buttons only)
                if (showMediaQuickAccess) {
                    var showMediaQuickMenu by remember { mutableStateOf(false) }

                    Box {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = if (currentRawValue.startsWith("system:media_")) {
                                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.85f)
                            } else {
                                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f)
                            },
                            modifier = Modifier.clickable { showMediaQuickMenu = true }
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.5.dp)
                            ) {
                                Icon(
                                    Icons.Default.PlayArrow,
                                    contentDescription = "Media Actions",
                                    tint = if (currentRawValue.startsWith("system:media_")) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(13.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = "Media ▾",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (currentRawValue.startsWith("system:media_")) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        DropdownMenu(
                            expanded = showMediaQuickMenu,
                            onDismissRequest = { showMediaQuickMenu = false },
                            modifier = Modifier
                                .background(Color(0xF012141A))
                                .border(1.dp, Color(0x33FFFFFF), RoundedCornerShape(16.dp)),
                            shape = RoundedCornerShape(16.dp),
                            containerColor = Color(0xF012141A)
                        ) {
                            val skipSec = prefs.getInt(com.sbf.lightspeed.system.LightspeedPreferences.KEY_MEDIA_SKIP_SECONDS, 10)
                            val mediaItems = listOf(
                                "system:media_play_pause" to "Play / Pause",
                                "system:media_next" to "Next Track",
                                "system:media_prev" to "Previous Track",
                                "system:media_skip_forward" to "Skip Forward (${skipSec}s)",
                                "system:media_skip_backward" to "Skip Backward (${skipSec}s)",
                                "system:media_scrubber" to "Media Timeline Scrubber (HUD)",
                                "system:media_stop" to "Stop Playback"
                            )

                            mediaItems.forEach { (token, title) ->
                                val isSelected = currentRawValue == token
                                DropdownMenuItem(
                                    modifier = Modifier.heightIn(min = 48.dp),
                                    text = {
                                        Text(
                                            text = if (isSelected) "✓ $title" else title,
                                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                            color = if (isSelected) MaterialTheme.colorScheme.primary else Color.Unspecified
                                        )
                                    },
                                    onClick = {
                                        currentRawValue = token
                                        prefs.edit().putString(key, token).apply()
                                        try { LightspeedAccessibilityService.instance?.reloadPreferences() } catch (_: Exception) {}
                                        showMediaQuickMenu = false
                                    }
                                )
                            }
                        }
                    }
                }

                // 2. HUD Style & Controls for Scrubbers (Screen Timeout, Brightness, Volume)
                if (currentRawValue == "system:screen_timeout" || currentRawValue == "system:brightness" || currentRawValue == "system:volume") {
                    var hudStyle by remember(currentRawValue, key) {
                        mutableStateOf(com.sbf.lightspeed.system.LightspeedPreferences.resolveHudStyle(prefs, key, currentRawValue))
                    }
                    var showHudMenu by remember { mutableStateOf(false) }

                    if (currentRawValue == "system:brightness") {
                        var hudEnabled by remember(currentRawValue) {
                            mutableStateOf(prefs.getBoolean(com.sbf.lightspeed.system.LightspeedPreferences.KEY_HUD_BRIGHTNESS_ENABLED, true))
                        }
                        var brightRes by remember(currentRawValue) {
                            mutableIntStateOf(prefs.getInt(com.sbf.lightspeed.system.LightspeedPreferences.KEY_BRIGHTNESS_SCRUB_RESOLUTION, 32))
                        }
                        var showBrightSliderDialog by remember { mutableStateOf(false) }

                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = if (hudEnabled) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.7f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                            modifier = Modifier.clickable {
                                hudEnabled = !hudEnabled
                                prefs.edit().putBoolean(com.sbf.lightspeed.system.LightspeedPreferences.KEY_HUD_BRIGHTNESS_ENABLED, hudEnabled).apply()
                                try { com.sbf.lightspeed.LightspeedAccessibilityService.instance?.reloadPreferences() } catch (_: Exception) {}
                            }
                        ) {
                            Text(
                                text = if (hudEnabled) "HUD: ON" else "HUD: OFF",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (hudEnabled) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.5.dp)
                            )
                        }

                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.65f),
                            modifier = Modifier.clickable { showBrightSliderDialog = true }
                        ) {
                            Text(
                                text = "Steps: $brightRes ▾",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.5.dp)
                            )
                        }

                        if (showBrightSliderDialog) {
                            var tempRes by remember { mutableFloatStateOf(brightRes.toFloat()) }
                            AlertDialog(
                                onDismissRequest = { showBrightSliderDialog = false },
                                containerColor = Color(0xF012141A),
                                shape = RoundedCornerShape(18.dp),
                                title = {
                                    Text(
                                        text = "Brightness Scrub Resolution",
                                        fontSize = 15.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color.White
                                    )
                                },
                                text = {
                                    Column(
                                        modifier = Modifier.fillMaxWidth(),
                                        verticalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Text(
                                            text = "Control total tactile graduation notches across the full 0–100% brightness range.",
                                            fontSize = 12.sp,
                                            color = Color.LightGray.copy(alpha = 0.75f)
                                        )
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text(
                                                text = "Graduation Steps",
                                                fontSize = 12.sp,
                                                fontWeight = FontWeight.SemiBold,
                                                color = Color.White
                                            )
                                            val curSteps = tempRes.toInt().coerceIn(10, 254)
                                            Surface(
                                                shape = RoundedCornerShape(6.dp),
                                                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f)
                                            ) {
                                                Text(
                                                    text = "$curSteps Steps (~${String.format(java.util.Locale.US, "%.1f", 100f / curSteps)}%)",
                                                    fontSize = 11.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                                                    modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp)
                                                )
                                            }
                                        }
                                        Slider(
                                            value = tempRes,
                                            onValueChange = { tempRes = it },
                                            valueRange = 10f..254f,
                                            steps = 243,
                                            colors = SliderDefaults.colors(
                                                thumbColor = MaterialTheme.colorScheme.primary,
                                                activeTrackColor = MaterialTheme.colorScheme.primary,
                                                inactiveTrackColor = Color.White.copy(alpha = 0.15f)
                                            ),
                                            modifier = Modifier.fillMaxWidth()
                                        )
                                    }
                                },
                                confirmButton = {
                                    TextButton(
                                        onClick = {
                                            val finalRes = tempRes.toInt().coerceIn(10, 254)
                                            brightRes = finalRes
                                            val derivedStep = (255f / finalRes).toInt().coerceIn(1, 32)
                                            prefs.edit()
                                                .putInt(com.sbf.lightspeed.system.LightspeedPreferences.KEY_BRIGHTNESS_SCRUB_RESOLUTION, finalRes)
                                                .putInt(com.sbf.lightspeed.system.LightspeedPreferences.KEY_BRIGHTNESS_SCRUB_STEP, derivedStep)
                                                .apply()
                                            try { com.sbf.lightspeed.LightspeedAccessibilityService.instance?.reloadPreferences() } catch (_: Exception) {}
                                            showBrightSliderDialog = false
                                        }
                                    ) {
                                        Text("APPLY", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                                    }
                                },
                                dismissButton = {
                                    TextButton(onClick = { showBrightSliderDialog = false }) {
                                        Text("CANCEL", color = Color.LightGray)
                                    }
                                }
                            )
                        }
                    }

                    if (currentRawValue == "system:volume") {
                        var hudEnabled by remember(currentRawValue) {
                            mutableStateOf(prefs.getBoolean(com.sbf.lightspeed.system.LightspeedPreferences.KEY_HUD_VOLUME_ENABLED, true))
                        }
                        var nativeSliderEnabled by remember(currentRawValue) {
                            mutableStateOf(prefs.getBoolean(com.sbf.lightspeed.system.LightspeedPreferences.KEY_VOLUME_SHOW_NATIVE_SLIDER, false))
                        }
                        var volResolution by remember(currentRawValue) {
                            mutableIntStateOf(prefs.getInt(com.sbf.lightspeed.system.LightspeedPreferences.KEY_VOLUME_SCRUB_RESOLUTION, 100))
                        }
                        var showVolSliderDialog by remember { mutableStateOf(false) }

                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = if (hudEnabled) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.7f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                            modifier = Modifier.clickable {
                                hudEnabled = !hudEnabled
                                prefs.edit().putBoolean(com.sbf.lightspeed.system.LightspeedPreferences.KEY_HUD_VOLUME_ENABLED, hudEnabled).apply()
                                try { com.sbf.lightspeed.LightspeedAccessibilityService.instance?.reloadPreferences() } catch (_: Exception) {}
                            }
                        ) {
                            Text(
                                text = if (hudEnabled) "HUD: ON" else "HUD: OFF",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (hudEnabled) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.5.dp)
                            )
                        }

                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = if (nativeSliderEnabled) MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.8f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                            modifier = Modifier.clickable {
                                nativeSliderEnabled = !nativeSliderEnabled
                                prefs.edit().putBoolean(com.sbf.lightspeed.system.LightspeedPreferences.KEY_VOLUME_SHOW_NATIVE_SLIDER, nativeSliderEnabled).apply()
                                try { com.sbf.lightspeed.LightspeedAccessibilityService.instance?.reloadPreferences() } catch (_: Exception) {}
                            }
                        ) {
                            Text(
                                text = if (nativeSliderEnabled) "Native: ON" else "Native: OFF",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (nativeSliderEnabled) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.5.dp)
                            )
                        }

                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.65f),
                            modifier = Modifier.clickable { showVolSliderDialog = true }
                        ) {
                            Text(
                                text = "Res: ${volResolution} ▾",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.5.dp)
                            )
                        }

                        if (showVolSliderDialog) {
                            var tempRes by remember { mutableFloatStateOf(volResolution.toFloat()) }
                            AlertDialog(
                                onDismissRequest = { showVolSliderDialog = false },
                                containerColor = Color(0xF012141A),
                                shape = RoundedCornerShape(18.dp),
                                title = {
                                    Text(
                                        text = "Volume Scrub Resolution",
                                        fontSize = 15.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color.White
                                    )
                                },
                                text = {
                                    Column(
                                        modifier = Modifier.fillMaxWidth(),
                                        verticalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Text(
                                            text = "Graduation steps mapped across the audio volume range (5 to 100 steps / 100th precision).",
                                            fontSize = 12.sp,
                                            color = Color.LightGray.copy(alpha = 0.75f)
                                        )
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text(
                                                text = "Scrub Steps",
                                                fontSize = 12.sp,
                                                fontWeight = FontWeight.SemiBold,
                                                color = Color.White
                                            )
                                            val curRes = tempRes.toInt().coerceIn(5, 100)
                                            val pct = String.format(java.util.Locale.US, "%.1f", 100f / curRes.coerceAtLeast(1))
                                            Surface(
                                                shape = RoundedCornerShape(6.dp),
                                                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f)
                                            ) {
                                                Text(
                                                    text = "$curRes Steps (~$pct%)",
                                                    fontSize = 11.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                                                    modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp)
                                                )
                                            }
                                        }
                                        Slider(
                                            value = tempRes,
                                            onValueChange = { tempRes = it },
                                            valueRange = 5f..100f,
                                            steps = 94,
                                            colors = SliderDefaults.colors(
                                                thumbColor = MaterialTheme.colorScheme.primary,
                                                activeTrackColor = MaterialTheme.colorScheme.primary,
                                                inactiveTrackColor = Color.White.copy(alpha = 0.15f)
                                            ),
                                            modifier = Modifier.fillMaxWidth()
                                        )
                                    }
                                },
                                confirmButton = {
                                    TextButton(
                                        onClick = {
                                            val finalRes = tempRes.toInt().coerceIn(5, 100)
                                            val derivedStep = (100f / finalRes).roundToInt().coerceIn(1, 20)
                                            volResolution = finalRes
                                            prefs.edit()
                                                .putInt(com.sbf.lightspeed.system.LightspeedPreferences.KEY_VOLUME_SCRUB_RESOLUTION, finalRes)
                                                .putInt(com.sbf.lightspeed.system.LightspeedPreferences.KEY_VOLUME_SCRUB_STEP, derivedStep)
                                                .apply()
                                            try { com.sbf.lightspeed.LightspeedAccessibilityService.instance?.reloadPreferences() } catch (_: Exception) {}
                                            showVolSliderDialog = false
                                        }
                                    ) {
                                        Text("APPLY", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                                    }
                                },
                                dismissButton = {
                                    TextButton(onClick = { showVolSliderDialog = false }) {
                                        Text("CANCEL", color = Color.LightGray)
                                    }
                                }
                            )
                        }
                    }

                    Box {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.7f),
                            modifier = Modifier.clickable {
                                hudStyle = com.sbf.lightspeed.system.LightspeedPreferences.resolveHudStyle(prefs, key, currentRawValue)
                                showHudMenu = true
                            }
                        ) {
                            val hudName = when (hudStyle) {
                                "canopy_droppod" -> "Drop-Pod"
                                "cockpit_reticle" -> "Reticle"
                                "edge_blade" -> "Blade"
                                "quantum_horizon" -> "Horizon"
                                "tachyon_dial" -> "Radar"
                                else -> "Drop-Pod"
                            }
                            Text(
                                text = "Style: $hudName ▾",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSecondaryContainer,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.5.dp)
                            )
                        }
                        DropdownMenu(
                            expanded = showHudMenu,
                            onDismissRequest = { showHudMenu = false },
                            modifier = Modifier
                                .background(Color(0xF012141A))
                                .border(1.dp, Color(0x33FFFFFF), RoundedCornerShape(16.dp)),
                            shape = RoundedCornerShape(16.dp),
                            containerColor = Color(0xF012141A)
                        ) {
                            listOf(
                                "canopy_droppod" to "Tactical Canopy Drop-Pod",
                                "cockpit_reticle" to "Holographic Cockpit Reticle",
                                "edge_blade" to "Dynamic Edge Blade",
                                "quantum_horizon" to "Quantum Synthetic Horizon",
                                "tachyon_dial" to "Tachyon Orbital Radar"
                            ).forEach { (styleKey, styleTitle) ->
                                DropdownMenuItem(
                                    modifier = Modifier.heightIn(min = 48.dp),
                                    text = {
                                        Text(
                                            text = if (styleKey == hudStyle) "✓ $styleTitle" else styleTitle,
                                            fontWeight = if (styleKey == hudStyle) FontWeight.Bold else FontWeight.Normal
                                        )
                                    },
                                    onClick = {
                                        hudStyle = styleKey
                                        com.sbf.lightspeed.system.LightspeedPreferences.saveHudStyle(prefs, key, currentRawValue, styleKey)
                                        try { com.sbf.lightspeed.LightspeedAccessibilityService.instance?.reloadPreferences() } catch (_: Exception) {}
                                        showHudMenu = false
                                    }
                                )
                            }
                        }
                    }
                }

                // 3. Skip Duration Selector for Media Skip Actions
                if (currentRawValue == "system:media_skip_forward" || currentRawValue == "system:media_skip_backward") {
                    var currentSkipSec by remember(currentRawValue) {
                        mutableStateOf(prefs.getInt(com.sbf.lightspeed.system.LightspeedPreferences.KEY_MEDIA_SKIP_SECONDS, 10))
                    }
                    var showSkipMenu by remember { mutableStateOf(false) }

                    Box {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.7f),
                            modifier = Modifier.clickable { showSkipMenu = true }
                        ) {
                            Text(
                                text = "Skip: ${currentSkipSec}s ▾",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSecondaryContainer,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.5.dp)
                            )
                        }
                        DropdownMenu(
                            expanded = showSkipMenu,
                            onDismissRequest = { showSkipMenu = false },
                            modifier = Modifier
                                .background(Color(0xF012141A))
                                .border(1.dp, Color(0x33FFFFFF), RoundedCornerShape(16.dp)),
                            shape = RoundedCornerShape(16.dp),
                            containerColor = Color(0xF012141A)
                        ) {
                            listOf(5, 10, 15, 30, 60).forEach { sec ->
                                DropdownMenuItem(
                                    modifier = Modifier.heightIn(min = 48.dp),
                                    text = {
                                        Text(
                                            text = if (sec == currentSkipSec) "✓ ${sec}s Interval" else "${sec}s Interval",
                                            fontWeight = if (sec == currentSkipSec) FontWeight.Bold else FontWeight.Normal
                                        )
                                    },
                                    onClick = {
                                        currentSkipSec = sec
                                        prefs.edit().putInt(com.sbf.lightspeed.system.LightspeedPreferences.KEY_MEDIA_SKIP_SECONDS, sec).apply()
                                        try { LightspeedAccessibilityService.instance?.reloadPreferences() } catch (_: Exception) {}
                                        showSkipMenu = false
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }

        if (direction == ArrowDirection.SCRUB) {
            Box {
                DropdownMenu(
                    expanded = showScrubMenu,
                    onDismissRequest = { showScrubMenu = false },
                    modifier = Modifier
                        .background(Color(0xF012141A))
                        .border(1.dp, Color(0x33FFFFFF), RoundedCornerShape(16.dp)),
                    shape = RoundedCornerShape(16.dp),
                    containerColor = Color(0xF012141A)
                ) {
                    options.forEach { opt ->
                        val optLabel = when (opt) {
                            "none" -> "None"
                            "system:screen_timeout" -> "Screen Timeout (Ship Goes Dark)"
                            "system:volume" -> "Volume (Media Stream)"
                            "system:brightness" -> "Screen Brightness"
                            "system:scroll_to_top" -> "Scroll to Top"
                            else -> labelCache[opt] ?: opt
                        }
                        DropdownMenuItem(
                            modifier = Modifier.heightIn(min = 48.dp),
                            text = { Text(optLabel) },
                            onClick = {
                                currentRawValue = opt
                                prefs.edit().putString(key, opt).apply()
                                try { LightspeedAccessibilityService.instance?.reloadPreferences() } catch (_: Exception) {}
                                showScrubMenu = false
                            }
                        )
                    }
                }
            }
        }
    }
}
}


