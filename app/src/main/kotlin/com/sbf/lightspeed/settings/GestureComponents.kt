package com.sbf.lightspeed.settings

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sbf.lightspeed.CockpitGearPickerActivity
import com.sbf.lightspeed.system.ActionMethods
import com.sbf.lightspeed.system.LightspeedOrientationEngine
import com.sbf.lightspeed.system.LightspeedShortcutManager
import com.sbf.lightspeed.system.TacticalToolItem
import com.sbf.lightspeed.ui.theme.LightspeedStyle
import com.sbf.lightspeed.ui.theme.LocalDeckTheme

/**
 * Tactical gesture mapping row integrating optical glass badges, gesture tracers,
 * and contextual micro-controls for gestures across the avionics deck.
 */
@OptIn(ExperimentalFoundationApi::class)
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
    showToolsQuickAccess: Boolean = false,
    installedTools: List<TacticalToolItem> = emptyList(),
    badgeText: String? = null,
    customLeading: (@Composable () -> Unit)? = null,
    showMooringRope: Boolean = false,
    isMooringTied: Boolean = true,
    onToggleMooring: ((Boolean) -> Unit)? = null,
    onMirrorMooring: (() -> Unit)? = null
) {
    val lsColors = LightspeedStyle.colors
    val activeSnapshot = LocalDeckTheme.current
    val opticalColors = activeSnapshot.resolveOpticalColors()
    val readableTint = opticalColors.readableTintColor
    val key = remember(keyResName) { resKey(context, keyResName) }
    val defaultAction = remember(key) { LightspeedActionRegistry.getDefaultActionForGestureKey(key) }
    var currentRawValue by remember { mutableStateOf(prefs.getString(key, defaultAction) ?: defaultAction) }
    var showScrubMenu by remember { mutableStateOf(false) }

    val pickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            currentRawValue = prefs.getString(key, defaultAction) ?: defaultAction
        }
    }

    val activeLabel = remember(currentRawValue, labelCache[currentRawValue]) {
        if (currentRawValue.isBlank() || currentRawValue == "none") {
            "None"
        } else if (currentRawValue.startsWith("shortcut:")) {
            val resolved = LightspeedShortcutManager.resolveLabel(context, currentRawValue)
            if (resolved.isNotBlank()) resolved else (labelCache[currentRawValue] ?: currentRawValue)
        } else {
            labelCache[currentRawValue] ?: currentRawValue
        }
    }

    val gravityBucket = when (currentRawValue) {
        "system:gravity_override_portrait", "system:orientation_portrait" ->
            LightspeedOrientationEngine.AttitudeBucket.STRICT_PORTRAIT
        "system:gravity_override_sensor_portrait", "system:orientation_sensor_portrait" ->
            LightspeedOrientationEngine.AttitudeBucket.SENSOR_PORTRAIT
        "system:gravity_override_landscape" ->
            LightspeedOrientationEngine.AttitudeBucket.SENSOR_LANDSCAPE
        "system:gravity_override_360", "system:orientation_sensor_360" ->
            LightspeedOrientationEngine.AttitudeBucket.SENSOR_360
        else -> null
    }

    val isOrientationAction = currentRawValue == "system:auto_rotate_toggle" ||
            currentRawValue == "system:gravity_override_360" ||
            currentRawValue == "system:gravity_override_portrait" ||
            currentRawValue == "system:gravity_override_sensor_portrait" ||
            currentRawValue == "system:gravity_override_landscape"

    val isMethodAction = ActionMethods.isMethodAction(currentRawValue)

    val hasControls = showMediaQuickAccess ||
            showToolsQuickAccess ||
            isHold ||
            isOrientationAction ||
            isMethodAction ||
            (gravityBucket != null) ||
            (currentRawValue == "system:screen_timeout") ||
            (currentRawValue == "system:brightness") ||
            (currentRawValue == "system:volume") ||
            (currentRawValue == "system:media_skip_forward" || currentRawValue == "system:media_skip_backward")

    val isScrubAction = direction == ArrowDirection.SCRUB ||
            direction == ArrowDirection.SCRUB_LEFT ||
            direction == ArrowDirection.SCRUB_RIGHT

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
                    .padding(horizontal = 14.dp),
                onMirror = onMirrorMooring
            )
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clickable {
                    if (isScrubAction) {
                        showScrubMenu = true
                    } else {
                        val intent = Intent(context, CockpitGearPickerActivity::class.java).apply {
                            putExtra("SINGLE_SELECT_PREF_KEY", key)
                            putExtra("SINGLE_SELECT_TITLE", "$defaultTitle Action")
                            putExtra("IS_HOLD_GESTURE", isHold || isScrubAction)
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
                    val badgeShape = RoundedCornerShape((activeSnapshot.cornerRadiusDp * 0.28f).coerceIn(6f, 8f).dp)
                    Box(
                        modifier = Modifier
                            .wrapContentWidth()
                            .clip(badgeShape)
                            .border(
                                BorderStroke(
                                    (activeSnapshot.borderWidthDp * 0.75f).coerceIn(0.8f, 1.2f).dp,
                                    opticalColors.borderColor.copy(alpha = 0.5f)
                                ),
                                badgeShape
                            )
                            .background(readableTint.copy(alpha = 0.16f))
                    ) {
                        Text(
                            text = badgeText,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold,
                            fontSize = 11.sp,
                            color = readableTint,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                            maxLines = 1,
                            softWrap = false
                        )
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                } else {
                    val resolvedDirection = if (direction == ArrowDirection.SCRUB) {
                        if (keyResName.contains("LEFT", ignoreCase = true)) ArrowDirection.SCRUB_RIGHT else ArrowDirection.SCRUB_LEFT
                    } else {
                        direction
                    }
                    GestureTrailTracer(resolvedDirection, isHold, readableTint, Modifier.size(32.dp))
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
                        color = lsColors.textPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = activeLabel,
                        fontSize = 11.sp,
                        color = readableTint,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            // Line 3: Dedicated Action Chips & Dropdowns (Horizontally scrollable)
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
                    // Hold Duration Customization Chip
                    if (isHold) {
                        GestureHoldDurationControl(context, prefs, key, activeSnapshot, opticalColors, readableTint)
                    }

                    // Synthetic Gravity Mode Sub-Chip
                    if (isOrientationAction) {
                        GestureOrientationModeControl(context, currentRawValue, activeSnapshot)
                    }

                    // Action Method Sub-Chip (Split Screen / Close App / Scroll)
                    if (isMethodAction) {
                        GestureActionMethodControl(context, currentRawValue, activeSnapshot, opticalColors, readableTint)
                    }

                    // Synthetic Gravity Assigned Apps Chip
                    if (gravityBucket != null) {
                        GestureGravityAppControl(context, prefs, gravityBucket, activeSnapshot)
                    }

                    // Quick Media Actions Dropdown Menu
                    if (showMediaQuickAccess) {
                        GestureMediaQuickAccessControl(context, prefs, key, currentRawValue, { currentRawValue = it }, activeSnapshot)
                    }

                    // Quick Actions Dropdown Menu (Tactical Tools / Shortcuts)
                    if (showToolsQuickAccess && installedTools.isNotEmpty()) {
                        GestureToolsQuickAccessControl(context, prefs, key, currentRawValue, installedTools, { currentRawValue = it }, activeSnapshot)
                    }

                    // HUD Style & Controls for Scrubbers
                    if (currentRawValue == "system:screen_timeout" || currentRawValue == "system:brightness" || currentRawValue == "system:volume") {
                        if (currentRawValue == "system:brightness") {
                            GestureBrightnessScrubControls(context, prefs, activeSnapshot, opticalColors, readableTint)
                        }
                        if (currentRawValue == "system:volume") {
                            GestureVolumeScrubControls(context, prefs, activeSnapshot, opticalColors, readableTint)
                        }
                        GestureHudStyleDropdown(context, prefs, key, currentRawValue, activeSnapshot)
                    }

                    // Skip Duration Selector for Media Skip Actions
                    if (currentRawValue == "system:media_skip_forward" || currentRawValue == "system:media_skip_backward") {
                        GestureMediaSkipDropdown(context, prefs, currentRawValue, activeSnapshot)
                    }
                }
            }

            if (isScrubAction) {
                GestureScrubActionMenu(
                    context = context,
                    prefs = prefs,
                    key = key,
                    showMenu = showScrubMenu,
                    options = options,
                    labelCache = labelCache,
                    onDismiss = { showScrubMenu = false },
                    onOptionSelected = { currentRawValue = it }
                )
            }
        }
    }
}
