package com.sbf.lightspeed.settings

import android.app.Activity
import android.content.Context
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.MenuBook
import androidx.compose.material.icons.outlined.UnfoldLess
import androidx.compose.material.icons.outlined.UnfoldMore
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import android.widget.Toast
import com.sbf.lightspeed.LightspeedAccessibilityService
import com.sbf.lightspeed.system.LightspeedFlightNotificationManager
import com.sbf.lightspeed.system.LightspeedPreferences
import com.sbf.lightspeed.system.defaultPrefs
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Top-level floating settings coordinator for Lightspeed.
 * Manages presentation animations, expand/collapse toggles, and matrix config views.
 */
@Composable
fun MainSettingsScreen() {
    val context = LocalContext.current
    val prefs = remember { context.defaultPrefs() }
    val colorScheme = MaterialTheme.colorScheme
    val scope = rememberCoroutineScope()

    var isVisible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        isVisible = true
    }

    var showGuidebook by remember { mutableStateOf(false) }
    var showCentralCommandDeck by rememberSaveable { mutableStateOf(false) }
    var targetJumpTab by remember { mutableIntStateOf(-1) }
    var targetJumpSection by remember { mutableStateOf<String?>(null) }
    var isAllExpanded by remember { mutableStateOf(false) }

    val dismissAction = {
        isVisible = false
        prefs.edit()
            .putBoolean("pref_statusbar_preview", false)
            .putBoolean("pref_sidebar_preview", false)
            .putBoolean("pref_sidebar_left_preview", false)
            .putBoolean(com.sbf.lightspeed.system.LightspeedPreferences.KEY_NOTCH_TEST_BEACON, false)
            .putBoolean(com.sbf.lightspeed.system.LightspeedPreferences.KEY_HORIZON_RAIL_PREVIEW, false)
            .putBoolean("pref_section_statusbar_expanded", false)
            .putBoolean("pref_section_telemetry_expanded", false)
            .putBoolean("pref_sub_horizon_rail_geom", false)
            .putBoolean("pref_sub_horizon_rail_color", false)
            .putBoolean("pref_sub_horizon_rail_text", false)
            .putBoolean("pref_sub_horizon_rail_custom", false)
            .putBoolean("pref_section_center_expanded", false)
            .putBoolean("pref_section_top_expanded", false)
            .putBoolean("pref_section_bottom_expanded", false)
            .putBoolean("pref_section_left_center_expanded", false)
            .putBoolean("pref_section_left_top_expanded", false)
            .putBoolean("pref_section_left_bottom_expanded", false)
            .apply()
        try { com.sbf.lightspeed.LightspeedAccessibilityService.instance?.reloadPreferences() } catch (_: Exception) {}
        scope.launch {
            delay(300)
            (context as? Activity)?.finishAndRemoveTask()
        }
    }

    BackHandler {
        dismissAction()
    }

    val glassVisuals = rememberDeckGlassVisuals(context)

    val animatedScrimAlpha by animateFloatAsState(
        targetValue = if (isVisible) glassVisuals.scrimAlpha else 0f,
        animationSpec = tween(durationMillis = 300),
        label = "scrim_alpha"
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = animatedScrimAlpha))
            .clickable { dismissAction() }
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp, vertical = 20.dp)
                .statusBarsPadding()
                .navigationBarsPadding(),
            contentAlignment = Alignment.Center
        ) {
            AnimatedVisibility(
                visible = isVisible,
                enter = slideInVertically(
                    initialOffsetY = { fullHeight -> fullHeight },
                    animationSpec = tween(durationMillis = 380, easing = FastOutSlowInEasing)
                ) + fadeIn(animationSpec = tween(durationMillis = 300)),
                exit = slideOutVertically(
                    targetOffsetY = { fullHeight -> fullHeight },
                    animationSpec = tween(durationMillis = 300, easing = FastOutLinearInEasing)
                ) + fadeOut(animationSpec = tween(durationMillis = 250))
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(0.96f)
                        .fillMaxHeight(0.94f)
                        .clickable(enabled = false) {}
                ) {
                    var refreshKey by remember { mutableStateOf(0) }
                    var toggleAllTrigger by remember { mutableStateOf(0) }

                    FloatingOverlayContainer(
                        title = "Central Command",
                        onDismiss = { dismissAction() },
                        onTitleClick = {
                            showCentralCommandDeck = true
                        },
                        onTitleLongClick = {
                            val action = LightspeedPreferences.getCentralCommandLongPressAction(context)
                            when (action) {
                                "toggle_master_flight" -> {
                                    val current = LightspeedPreferences.isMasterFlightArmed(context)
                                    val next = !current
                                    LightspeedPreferences.setMasterFlightArmed(context, next)
                                    LightspeedAccessibilityService.instance?.updateOverlaysVisibility()
                                    LightspeedFlightNotificationManager.update(context)
                                    Toast.makeText(context, if (next) "Flight Mode: ARMED" else "Flight Mode: STANDBY", Toast.LENGTH_SHORT).show()
                                }
                                "toggle_all_deflectors" -> {
                                    val leftOn = LightspeedPreferences.isLeftDeflectorEnabled(context)
                                    val rightOn = LightspeedPreferences.isRightDeflectorEnabled(context)
                                    val next = !(leftOn || rightOn)
                                    LightspeedPreferences.setLeftDeflectorEnabled(context, next)
                                    LightspeedPreferences.setRightDeflectorEnabled(context, next)
                                    LightspeedAccessibilityService.instance?.updateOverlaysVisibility()
                                    LightspeedFlightNotificationManager.update(context)
                                    Toast.makeText(context, if (next) "All Deflectors: ACTIVE" else "All Deflectors: MUTED", Toast.LENGTH_SHORT).show()
                                }
                                "toggle_left_deflector" -> {
                                    val next = !LightspeedPreferences.isLeftDeflectorEnabled(context)
                                    LightspeedPreferences.setLeftDeflectorEnabled(context, next)
                                    LightspeedAccessibilityService.instance?.updateOverlaysVisibility()
                                    LightspeedFlightNotificationManager.update(context)
                                    Toast.makeText(context, if (next) "Left Flank: ACTIVE" else "Left Flank: MUTED", Toast.LENGTH_SHORT).show()
                                }
                                "toggle_right_deflector" -> {
                                    val next = !LightspeedPreferences.isRightDeflectorEnabled(context)
                                    LightspeedPreferences.setRightDeflectorEnabled(context, next)
                                    LightspeedAccessibilityService.instance?.updateOverlaysVisibility()
                                    LightspeedFlightNotificationManager.update(context)
                                    Toast.makeText(context, if (next) "Right Flank: ACTIVE" else "Right Flank: MUTED", Toast.LENGTH_SHORT).show()
                                }
                                else -> {
                                    showCentralCommandDeck = true
                                }
                            }
                        },
                        headerControl = {
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                IconButton(
                                    onClick = { showGuidebook = true },
                                    colors = IconButtonDefaults.iconButtonColors(
                                        containerColor = colorScheme.surfaceVariant.copy(alpha = 0.5f)
                                    )
                                ) {
                                    Icon(
                                        Icons.AutoMirrored.Outlined.MenuBook,
                                        contentDescription = "The Stranded in Space Guidebook",
                                        tint = colorScheme.primary,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }

                                IconButton(
                                    onClick = {
                                        isAllExpanded = !isAllExpanded
                                        toggleAllTrigger++
                                    },
                                    colors = IconButtonDefaults.iconButtonColors(
                                        containerColor = colorScheme.surfaceVariant.copy(alpha = 0.5f)
                                    )
                                ) {
                                    Icon(
                                        imageVector = if (isAllExpanded) Icons.Outlined.UnfoldLess else Icons.Outlined.UnfoldMore,
                                        contentDescription = "Expand or Collapse All",
                                        tint = colorScheme.primary,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            }
                        }
                    ) {
                        SidebarMatrixConfigurationFields(
                            context = context,
                            prefs = prefs,
                            toggleAllTrigger = toggleAllTrigger,
                            jumpTargetTab = targetJumpTab,
                            jumpTargetSection = targetJumpSection,
                            onRefreshNeeded = { /* Local state reacts immediately */ }
                        )
                    }

                    if (showCentralCommandDeck) {
                        CentralCommandDeckDialog(
                            context = context,
                            prefs = prefs,
                            onDismiss = { showCentralCommandDeck = false },
                            onRefreshNeeded = { /* Local state reacts immediately */ }
                        )
                    }

                    if (showGuidebook) {
                        GuidebookBottomSheet(
                            onDismiss = { showGuidebook = false },
                            onNavigateToSection = { tabIdx, secKey ->
                                targetJumpTab = tabIdx
                                targetJumpSection = secKey
                                showGuidebook = false
                            }
                        )
                    }
                }
            }
        }
    }
}
