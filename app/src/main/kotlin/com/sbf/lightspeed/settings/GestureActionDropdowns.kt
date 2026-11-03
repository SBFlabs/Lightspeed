package com.sbf.lightspeed.settings

import android.content.Context
import android.content.SharedPreferences
import android.widget.Toast
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sbf.lightspeed.LightspeedAccessibilityService
import com.sbf.lightspeed.system.*
import com.sbf.lightspeed.ui.theme.LightspeedStyle

/**
 * Tactical dropdown controls for Orientation mode, Synthetic gravity app assignments,
 * Tools quick-access, and Scrub action menus.
 */
@Composable
fun GestureOrientationModeControl(
    context: Context,
    currentRawValue: String,
    activeSnapshot: DeckThemeSnapshot
) {
    val lsColors = LightspeedStyle.colors
    var currentBehavior by remember(currentRawValue) {
        mutableStateOf(LightspeedPreferences.getActionBehavior(context, currentRawValue, "toggle"))
    }
    var showModeMenu by remember { mutableStateOf(false) }

    Box {
        val modeLabel = when (currentBehavior) {
            "button" -> if (currentRawValue == "system:auto_rotate_toggle") "One-Way ON ▾" else "One-Way ▾"
            "button_off" -> "One-Way OFF ▾"
            else -> "Toggle ▾"
        }
        DeckSubChip(
            text = modeLabel,
            icon = Icons.Default.Tune,
            isActive = true,
            snapshot = activeSnapshot,
            onClick = { showModeMenu = true }
        )

        DeckThemedDropdownMenu(
            expanded = showModeMenu,
            onDismissRequest = { showModeMenu = false },
            snapshotOverride = activeSnapshot
        ) {
            val options = mutableListOf(
                "toggle" to "Interactive Toggle (Tap to engage/disengage)"
            )
            if (currentRawValue == "system:auto_rotate_toggle") {
                options.add("button" to "One-Way ON (Always turns ON)")
                options.add("button_off" to "One-Way OFF (Always turns OFF)")
            } else {
                options.add("button" to "One-Way Button (Reaffirm & never disengage)")
            }

            options.forEach { (modeKey, modeTitle) ->
                val isSelected = currentBehavior == modeKey
                DropdownMenuItem(
                    modifier = Modifier.heightIn(min = 48.dp),
                    text = {
                        Text(
                            text = if (isSelected) "✓ $modeTitle" else modeTitle,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                            color = if (isSelected) MaterialTheme.colorScheme.primary else Color.Unspecified
                        )
                    },
                    onClick = {
                        currentBehavior = modeKey
                        LightspeedPreferences.setActionBehavior(context, currentRawValue, modeKey)
                        LightspeedActionRegistry.labelCache[currentRawValue] = resolveDynamicTokenLabel(context, currentRawValue)
                        try { LightspeedAccessibilityService.instance?.reloadPreferences() } catch (e: Exception) { logSwallowed("GestureActionDropdowns", "SetActionBehavior", e) }
                        showModeMenu = false
                    }
                )
            }
        }
    }
}

@Composable
fun GestureGravityAppControl(
    context: Context,
    prefs: SharedPreferences,
    gravityBucket: LightspeedOrientationEngine.AttitudeBucket,
    activeSnapshot: DeckThemeSnapshot
) {
    val isGravityEnabled = remember {
        prefs.getBoolean(LightspeedPreferences.KEY_SYNTHETIC_GRAVITY_ENABLED, false)
    }
    var showAttitudeSheet by remember { mutableStateOf(false) }
    var assignedAppsCount by remember(gravityBucket, prefs.getStringSet(gravityBucket.prefKey, null)) {
        mutableIntStateOf(prefs.getStringSet(gravityBucket.prefKey, emptySet())?.size ?: 0)
    }

    DeckSubChip(
        text = if (!isGravityEnabled) "Apps (Off) ▾" else "Apps: $assignedAppsCount ▾",
        icon = when (gravityBucket) {
            LightspeedOrientationEngine.AttitudeBucket.STRICT_PORTRAIT -> Icons.Default.StayCurrentPortrait
            LightspeedOrientationEngine.AttitudeBucket.SENSOR_PORTRAIT -> Icons.Default.ScreenRotationAlt
            LightspeedOrientationEngine.AttitudeBucket.SENSOR_LANDSCAPE -> Icons.Default.StayCurrentLandscape
            LightspeedOrientationEngine.AttitudeBucket.SENSOR_360 -> Icons.Default.ScreenRotation
        },
        isActive = isGravityEnabled && assignedAppsCount > 0,
        isEnabled = isGravityEnabled,
        snapshot = activeSnapshot,
        onClick = {
            if (!isGravityEnabled) {
                Toast.makeText(context, "Synthetic Gravity Engine is disabled", Toast.LENGTH_SHORT).show()
            } else {
                showAttitudeSheet = true
            }
        }
    )

    if (showAttitudeSheet) {
        AttitudeAppAssignmentSheet(
            context = context,
            bucket = gravityBucket,
            onDismiss = { showAttitudeSheet = false },
            onUpdated = {
                assignedAppsCount = prefs.getStringSet(gravityBucket.prefKey, emptySet())?.size ?: 0
                try { LightspeedAccessibilityService.instance?.reloadPreferences() } catch (e: Exception) { logSwallowed("GestureActionDropdowns", "AttitudeUpdated", e) }
            }
        )
    }
}

@Composable
fun GestureToolsQuickAccessControl(
    context: Context,
    prefs: SharedPreferences,
    key: String,
    currentRawValue: String,
    installedTools: List<TacticalToolItem>,
    onActionSelected: (String) -> Unit,
    activeSnapshot: DeckThemeSnapshot
) {
    val lsColors = LightspeedStyle.colors
    var showToolsQuickMenu by remember { mutableStateOf(false) }

    Box {
        DeckSubChip(
            text = "Actions ▾",
            icon = Icons.Default.Bolt,
            isActive = currentRawValue.startsWith("system:") || currentRawValue.startsWith("app:") || currentRawValue.startsWith("shortcut:"),
            snapshot = activeSnapshot,
            onClick = { showToolsQuickMenu = true }
        )

        DeckThemedDropdownMenu(
            expanded = showToolsQuickMenu,
            onDismissRequest = { showToolsQuickMenu = false },
            snapshotOverride = activeSnapshot
        ) {
            var currentCategory = ""
            installedTools.forEach { tool ->
                if (tool.category != currentCategory) {
                    currentCategory = tool.category
                    DropdownMenuItem(
                        modifier = Modifier.heightIn(min = 36.dp),
                        text = {
                            Text(
                                text = currentCategory.uppercase(java.util.Locale.US),
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                        },
                        onClick = {},
                        enabled = false
                    )
                }
                val isSelected = (currentRawValue == tool.token)
                DropdownMenuItem(
                    modifier = Modifier.heightIn(min = 48.dp),
                    leadingIcon = {
                        val icon = when (tool.token) {
                            "system:torch", "system:flashlight" -> Icons.Default.FlashlightOn
                            "system:tactical_flyout" -> Icons.Default.Dashboard
                            LightspeedPreferences.ACTION_CHATGPT -> Icons.Default.AutoAwesome
                            LightspeedPreferences.ACTION_CLAUDE -> Icons.Default.Psychology
                            LightspeedPreferences.ACTION_GEMINI -> Icons.Default.Stars
                            LightspeedPreferences.ACTION_FOLAX -> Icons.Default.Assistant
                            LightspeedPreferences.ACTION_LENS -> Icons.Default.CenterFocusStrong
                            LightspeedPreferences.ACTION_QR_SCANNER -> Icons.Default.QrCodeScanner
                            LightspeedPreferences.ACTION_CAMERA_PHOTO -> Icons.Default.CameraAlt
                            LightspeedPreferences.ACTION_CAMERA_VIDEO -> Icons.Default.Videocam
                            else -> Icons.Default.Widgets
                        }
                        Icon(
                            imageVector = icon,
                            contentDescription = null,
                            tint = if (isSelected) MaterialTheme.colorScheme.primary else lsColors.textSecondary,
                            modifier = Modifier.size(20.dp)
                        )
                    },
                    text = {
                        Text(
                            text = tool.label,
                            fontSize = 12.5.sp,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                            color = if (isSelected) MaterialTheme.colorScheme.primary else lsColors.textPrimary
                        )
                    },
                    onClick = {
                        showToolsQuickMenu = false
                        onActionSelected(tool.token)
                        prefs.edit().putString(key, tool.token).apply()
                        LightspeedHapticEngine.tick(context)
                        try { LightspeedAccessibilityService.instance?.reloadPreferences() } catch (e: Exception) { logSwallowed("GestureActionDropdowns", "ToolsSelect", e) }
                    }
                )
            }
        }
    }
}

@Composable
fun GestureScrubActionMenu(
    context: Context,
    prefs: SharedPreferences,
    key: String,
    showMenu: Boolean,
    options: List<String>,
    labelCache: Map<String, String>,
    onDismiss: () -> Unit,
    onOptionSelected: (String) -> Unit
) {
    val lsColors = LightspeedStyle.colors
    Box {
        DeckThemedDropdownMenu(
            expanded = showMenu,
            onDismissRequest = onDismiss
        ) {
            options.forEach { opt ->
                val optLabel = when (opt) {
                    "none" -> "None"
                    "system:screen_timeout" -> "Screen Timeout (Ship Goes Dark)"
                    "system:volume" -> "Volume (Media Stream)"
                    "system:brightness" -> "Screen Brightness"
                    "system:scroll_scrub" -> "Scroll Scrubbing"
                    "system:media_scrubber" -> "Media Timeline Scrubber (HUD)"
                    "system:scroll_to_top" -> "Scroll to Top"
                    "system:scroll_to_bottom" -> "Scroll to Bottom"
                    else -> labelCache[opt] ?: opt
                }
                DropdownMenuItem(
                    modifier = Modifier.heightIn(min = 48.dp),
                    text = { Text(optLabel) },
                    onClick = {
                        onOptionSelected(opt)
                        prefs.edit().putString(key, opt).apply()
                        try { LightspeedAccessibilityService.instance?.reloadPreferences() } catch (e: Exception) { logSwallowed("GestureActionDropdowns", "ScrubActionSelected", e) }
                        onDismiss()
                    }
                )
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun GestureHudThemeToggleChip(
    context: Context? = null,
    prefs: SharedPreferences,
    key: String? = null,
    currentRawValue: String? = null,
    activeSnapshot: DeckThemeSnapshot
) {
    val actualContext = context ?: LocalContext.current
    val lsColors = LightspeedStyle.colors
    var colorMode by remember(key, currentRawValue) {
        mutableStateOf(LightspeedPreferences.resolveHudColorMode(prefs, key, currentRawValue))
    }
    var showColorEngineMenu by remember { mutableStateOf(false) }

    // Live reactivity: listen to SharedPreferences changes immediately
    DisposableEffect(prefs, key, currentRawValue) {
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, prefKey ->
            if (prefKey == null ||
                prefKey == LightspeedPreferences.KEY_HUD_COLOR_MODE ||
                prefKey == LightspeedPreferences.KEY_HUD_USE_CENTRAL_THEME ||
                prefKey.startsWith("pref_macro_hud_color_mode_") ||
                prefKey.startsWith("pref_hud_color_mode_")
            ) {
                colorMode = LightspeedPreferences.resolveHudColorMode(prefs, key, currentRawValue)
            }
        }
        prefs.registerOnSharedPreferenceChangeListener(listener)
        onDispose {
            prefs.unregisterOnSharedPreferenceChangeListener(listener)
        }
    }

    val modeShort = when (colorMode) {
        LightspeedPreferences.COCKPIT_COLOR_MODE_M3_DYNAMIC -> "Material 3"
        LightspeedPreferences.COCKPIT_COLOR_MODE_HYBRID_FUSION -> "Hybrid"
        else -> "12-Theme"
    }

    val colorModes = listOf(
        Triple(LightspeedPreferences.COCKPIT_COLOR_MODE_THEME_12, "◈ 12-Theme", "12-Theme"),
        Triple(LightspeedPreferences.COCKPIT_COLOR_MODE_M3_DYNAMIC, "🎨 Material 3", "Material 3"),
        Triple(LightspeedPreferences.COCKPIT_COLOR_MODE_HYBRID_FUSION, "⚡ Hybrid Fusion", "Hybrid Fusion")
    )

    Box {
        DeckSubChip(
            text = "Color Engine: $modeShort ▾",
            icon = Icons.Default.Palette,
            isActive = true,
            snapshot = activeSnapshot,
            onClick = {
                colorMode = LightspeedPreferences.resolveHudColorMode(prefs, key, currentRawValue)
                showColorEngineMenu = true
            }
        )

        DeckThemedDropdownMenu(
            expanded = showColorEngineMenu,
            onDismissRequest = { showColorEngineMenu = false },
            snapshotOverride = activeSnapshot
        ) {
            colorModes.forEachIndexed { index, (modeId, modeLabel, modeSimpleTitle) ->
                val isSelected = colorMode == modeId
                if (index > 0) {
                    HorizontalDivider(
                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                        modifier = Modifier.padding(horizontal = 10.dp)
                    )
                }

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .combinedClickable(
                            onClick = {
                                colorMode = modeId
                                LightspeedPreferences.saveHudColorMode(prefs, key, currentRawValue, modeId)
                                LightspeedHapticEngine.tick(actualContext)
                                try {
                                    LightspeedAccessibilityService.instance?.reloadPreferences()
                                } catch (e: Exception) {
                                    logSwallowed("GestureActionDropdowns", "HudColorEngineSelect", e)
                                }
                                showColorEngineMenu = false
                            },
                            onLongClick = {
                                colorMode = modeId
                                LightspeedPreferences.applyColorModeToAllSurfaces(actualContext, prefs, modeId)
                                LightspeedHapticEngine.heavyClick(actualContext)
                                Toast.makeText(
                                    actualContext,
                                    "✦ Applied $modeSimpleTitle to all flight surfaces",
                                    Toast.LENGTH_SHORT
                                ).show()
                                showColorEngineMenu = false
                            }
                        )
                        .padding(horizontal = 14.dp, vertical = 9.dp)
                ) {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.Center
                    ) {
                        Text(
                            text = if (isSelected) "✓ $modeLabel" else modeLabel,
                            fontSize = 12.5.sp,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                            color = if (isSelected) MaterialTheme.colorScheme.primary else lsColors.textPrimary
                        )
                        Spacer(modifier = Modifier.height(3.dp))
                        Text(
                            text = "HOLD TO APPLY TO ALL ↗",
                            fontSize = 8.sp,
                            fontWeight = FontWeight.Bold,
                            color = (if (isSelected) MaterialTheme.colorScheme.primary else lsColors.textSecondary).copy(alpha = 0.75f),
                            letterSpacing = 0.4.sp
                        )
                    }
                }
            }
        }
    }
}
