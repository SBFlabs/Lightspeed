package com.sbf.lightspeed.settings

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.content.SharedPreferences
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sbf.lightspeed.LightspeedAccessibilityService
import com.sbf.lightspeed.system.LightspeedHapticEngine
import com.sbf.lightspeed.system.LightspeedPreferences
import com.sbf.lightspeed.system.SonicDeckManager
import com.sbf.lightspeed.system.VistaDeckManager
import com.sbf.lightspeed.system.logSwallowed
import com.sbf.lightspeed.ui.theme.LightspeedStyle

/**
 * Dedicated sub-chips for Volume (Sonic Deck), Brightness (Wellness & Vista Deck),
 * and Bottom Sensor Area quick actions.
 */
@Composable
fun GestureVolumeDualRowControls(
    context: Context,
    prefs: SharedPreferences,
    key: String,
    currentRawValue: String,
    activeSnapshot: DeckThemeSnapshot,
    opticalColors: OpticalSurfaceColors,
    readableTint: Color,
    extraRow1Content: (@Composable RowScope.() -> Unit)? = null
) {
    val row1Scroll = rememberScrollState()
    val row2Scroll = rememberScrollState()

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        // Row 1: Core Scrubber Controls (HUD Toggle, Native Toggle, Resolution, HUD Style + any contextual gesture chips)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(row1Scroll),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            extraRow1Content?.invoke(this)
            GestureVolumeScrubControls(context, prefs, activeSnapshot, opticalColors, readableTint)
            GestureHudStyleDropdown(context, prefs, key, currentRawValue, activeSnapshot)
        }

        // Row 2: Color Engine dropdown, Haptics selector, and Sonic Deck launcher
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(row2Scroll),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            GestureHudThemeToggleChip(
                context = context,
                prefs = prefs,
                key = key,
                currentRawValue = currentRawValue,
                activeSnapshot = activeSnapshot
            )
            GestureScrubHapticsSelector(context, prefs, activeSnapshot, readableTint)
            DeckSubChip(
                text = "Sonic Deck ⚙",
                icon = Icons.Default.GraphicEq,
                isActive = false,
                snapshot = activeSnapshot,
                onClick = {
                    val a11y = (context as? AccessibilityService) ?: LightspeedAccessibilityService.instance
                    if (a11y != null) {
                        SonicDeckManager.show(a11y)
                    }
                }
            )
        }
    }
}

@Composable
fun GestureBrightnessDualRowControls(
    context: Context,
    prefs: SharedPreferences,
    key: String,
    currentRawValue: String,
    activeSnapshot: DeckThemeSnapshot,
    opticalColors: OpticalSurfaceColors,
    readableTint: Color,
    extraRow1Content: (@Composable RowScope.() -> Unit)? = null
) {
    val row1Scroll = rememberScrollState()
    val row2Scroll = rememberScrollState()

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        // Row 1: Core Scrubber Controls (HUD Toggle, Resolution, HUD Style + any contextual gesture chips)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(row1Scroll),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            extraRow1Content?.invoke(this)
            GestureBrightnessScrubControls(context, prefs, activeSnapshot, opticalColors, readableTint)
            GestureHudStyleDropdown(context, prefs, key, currentRawValue, activeSnapshot)
        }

        // Row 2: Color Engine dropdown, Haptics selector, and Vista Deck launcher
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(row2Scroll),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            GestureHudThemeToggleChip(
                context = context,
                prefs = prefs,
                key = key,
                currentRawValue = currentRawValue,
                activeSnapshot = activeSnapshot
            )
            GestureScrubHapticsSelector(context, prefs, activeSnapshot, readableTint)
            DeckSubChip(
                text = "Vista Deck ⚙",
                icon = Icons.Default.Brightness4,
                isActive = false,
                snapshot = activeSnapshot,
                onClick = {
                    val a11y = (context as? AccessibilityService) ?: LightspeedAccessibilityService.instance
                    if (a11y != null) {
                        VistaDeckManager.show(a11y)
                    }
                }
            )
        }
    }
}

@Composable
fun GestureGenericScrubDualRowControls(
    context: Context,
    prefs: SharedPreferences,
    key: String,
    currentRawValue: String,
    activeSnapshot: DeckThemeSnapshot,
    readableTint: Color,
    extraRow1Content: (@Composable RowScope.() -> Unit)? = null
) {
    val row1Scroll = rememberScrollState()
    val row2Scroll = rememberScrollState()

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        // Row 1: HUD Style + any contextual gesture chips (Hold, Method, Navigation Bar, etc.)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(row1Scroll),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            extraRow1Content?.invoke(this)
            GestureHudStyleDropdown(context, prefs, key, currentRawValue, activeSnapshot)
        }

        // Row 2: Color Engine dropdown and Haptics selector
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(row2Scroll),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            GestureHudThemeToggleChip(
                context = context,
                prefs = prefs,
                key = key,
                currentRawValue = currentRawValue,
                activeSnapshot = activeSnapshot
            )
            GestureScrubHapticsSelector(context, prefs, activeSnapshot, readableTint)
        }
    }
}

@Composable
fun GestureBottomSensorSubChips(
    context: Context,
    prefs: SharedPreferences,
    key: String,
    currentRawValue: String,
    onActionSelected: (String) -> Unit,
    activeSnapshot: DeckThemeSnapshot
) {
    val lsColors = LightspeedStyle.colors
    var showNavMenu by remember { mutableStateOf(false) }

    val navGroups: List<List<Triple<String, String, androidx.compose.ui.graphics.vector.ImageVector>>> = listOf(
        // Group 0: None
        listOf(
            Triple("None", "none", Icons.Default.Block)
        ),
        // Group 1: Tactical Flyout
        listOf(
            Triple("Tactical Flyout", LightspeedPreferences.ACTION_TACTICAL_FLYOUT, Icons.Default.Dashboard)
        ),
        // Group 2: Core Navigation cluster (Home, Back, Recents, Previous App, Close Current App)
        listOf(
            Triple("Home", "system:home", Icons.Default.Home),
            Triple("Back", "system:back", Icons.AutoMirrored.Filled.ArrowBack),
            Triple("Recents", "system:recents", Icons.Default.History),
            Triple("Previous App", "system:previous_app", Icons.Default.SwapHoriz),
            Triple("Close Current App", "system:close_app", Icons.Default.Close)
        ),
        // Group 3: Search & Assistant
        listOf(
            Triple("Circle to Search", LightspeedPreferences.ACTION_CIRCLE_TO_SEARCH, Icons.Default.Search),
            Triple("Assistant", LightspeedPreferences.ACTION_ASSISTANT, Icons.Default.Assistant)
        ),
        // Group 4: Multitasking & Windows
        listOf(
            Triple("Split Screen", "system:split_screen", Icons.Default.VerticalSplit),
            Triple("Pop-Up Window", "system:popup_window", Icons.Default.PictureInPicture)
        )
    )

    val isNavActive = navGroups.flatten().any { it.second == currentRawValue && it.second != "none" }

    Box {
        DeckSubChip(
            text = "Navigation Bar ▾",
            icon = Icons.Default.Navigation,
            isActive = isNavActive,
            snapshot = activeSnapshot,
            onClick = { showNavMenu = true }
        )

        DeckThemedDropdownMenu(
            expanded = showNavMenu,
            onDismissRequest = { showNavMenu = false },
            snapshotOverride = activeSnapshot
        ) {
            DropdownMenuItem(
                modifier = Modifier.heightIn(min = 34.dp),
                text = {
                    Text(
                        text = "NAVIGATION BAR ACTIONS",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                },
                onClick = {},
                enabled = false
            )

            navGroups.forEachIndexed { groupIndex, groupItems ->
                if (groupIndex > 0) {
                    HorizontalDivider(
                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
                        modifier = Modifier.padding(vertical = 4.dp, horizontal = 12.dp)
                    )
                }

                groupItems.forEach { (label, token, icon) ->
                    val isSelected = currentRawValue == token || (token == "none" && (currentRawValue.isBlank() || currentRawValue == "none"))
                    DropdownMenuItem(
                        modifier = Modifier.heightIn(min = 40.dp),
                        leadingIcon = {
                            Icon(
                                imageVector = icon,
                                contentDescription = null,
                                tint = if (isSelected) MaterialTheme.colorScheme.primary else lsColors.textSecondary,
                                modifier = Modifier.size(18.dp)
                            )
                        },
                        text = {
                            Text(
                                text = label,
                                fontSize = 12.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                color = if (isSelected) MaterialTheme.colorScheme.primary else lsColors.textPrimary
                            )
                        },
                        onClick = {
                            showNavMenu = false
                            onActionSelected(token)
                            prefs.edit().putString(key, token).apply()
                            LightspeedHapticEngine.tick(context)
                            try {
                                LightspeedAccessibilityService.instance?.reloadPreferences()
                            } catch (e: Exception) {
                                logSwallowed("GestureActionSubChips", "NavActionSelect", e)
                            }
                        }
                    )
                }
            }
        }
    }
}
