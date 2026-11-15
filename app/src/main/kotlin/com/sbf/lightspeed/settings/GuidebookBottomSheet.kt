package com.sbf.lightspeed.settings

import android.os.Build
import android.view.WindowManager
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import com.sbf.lightspeed.ui.theme.LightspeedStyle
import com.sbf.lightspeed.ui.theme.LocalDeckTheme
import java.util.Locale

data class GuidebookEntry(
    val id: String,
    val vesselTitle: String,
    val androidTitle: String,
    val icon: androidx.compose.ui.graphics.vector.ImageVector,
    val vesselLore: String,
    val androidUtility: String,
    val tabIndex: Int,
    val sectionKey: String
)

val GUIDEBOOK_ENTRIES = listOf(
    GuidebookEntry(
        id = "canopy",
        vesselTitle = "Sensor Area",
        androidTitle = "Touch Strip Matrix",
        icon = Icons.Default.TouchApp,
        vesselLore = "The vessel's overhead touch zone calibrated for sub-second macro sweeps, taps, and rebound maneuvers.",
        androidUtility = "A non-intrusive top-edge gesture area supporting taps, horizontal swipes, and hold modifiers without conflicting with Android's notification shade.",
        tabIndex = 1,
        sectionKey = "statusbar"
    ),
    GuidebookEntry(
        id = "synthetic_gravity",
        vesselTitle = "Synthetic Gravity Engine",
        androidTitle = "Orientation Preferences & Attitude Buckets",
        icon = Icons.Default.Rotate90DegreesCw,
        vesselLore = "Artificial gravity generator modulating the ship's 3D spatial attitude, active gyro stabilization, face posture tracking, and per-app orientation lock.",
        androidUtility = "Controls system auto-rotate, face posture auto-rotate, active 5Hz gyro sensor-portrait driver, and per-app attitude buckets (Portrait, Landscape, 360°).",
        tabIndex = 1,
        sectionKey = "gravity"
    ),
    GuidebookEntry(
        id = "deflectors",
        vesselTitle = "Left & Right Deflectors",
        androidTitle = "Left & Right Flank Gesture Sidebars",
        icon = Icons.Default.SwapHoriz,
        vesselLore = "Flank-mounted kinetic deflector arrays calibrated for sub-second impulse sweeps, multi-axis vector rebound maneuvers, and rapid macro execution.",
        androidUtility = "Customizable left and right edge touch zones supporting single/double taps, directional swipes, rebounds, two-step gestures, and hold modifiers.",
        tabIndex = 0,
        sectionKey = "wings"
    ),
    GuidebookEntry(
        id = "notch_beacon",
        vesselTitle = "Info Beacons",
        androidTitle = "Telemetry & Indicators",
        icon = Icons.Default.Sensors,
        vesselLore = "A dual-channel telemetry matrix comprising the horizon rail (top line progress) and orbital capsule (floating dynamic cutout HUD).",
        androidUtility = "Real-time download and media progress streamed along the top-edge horizon rail and/or the front camera punch-hole capsule with marquee scrolling and mini-player controls.",
        tabIndex = 1,
        sectionKey = "telemetry"
    ),
    GuidebookEntry(
        id = "hardware_keys",
        vesselTitle = "Hull & Ship Maneuvers",
        androidTitle = "Hardware Volume Button Matrix & Chords",
        icon = Icons.Default.Tune,
        vesselLore = "Emergency analog thruster triggers wired directly to primary propulsion. Enables blind cockpit firing through multi-switch sequence chords.",
        androidUtility = "Intercepts hardware volume key long presses, chords, and sequence combinations to trigger flashlight, screenshot, app kills, or previous app switching.",
        tabIndex = 1,
        sectionKey = "volumekeys"
    ),
    GuidebookEntry(
        id = "back_tap",
        vesselTitle = "Hull Kinetic Acoustic Resonator",
        androidTitle = "Back-of-Device Hull Tap Detection",
        icon = Icons.Default.TouchApp,
        vesselLore = "Micro-gravimetric sensors bonded to the external hull armor. Detects physical kinetic impacts on the ship's skin to trigger rapid defensive measures.",
        androidUtility = "Leverages the phone's accelerometer to register double and triple taps on the back chassis with real-time m/s² strike force threshold calibration.",
        tabIndex = 1,
        sectionKey = "backtap"
    ),
    GuidebookEntry(
        id = "refueling_bay",
        vesselTitle = "Refueling Bay & Cryo Stasis",
        androidTitle = "Ambient Charging Screen & OLED Dashboard",
        icon = Icons.Default.BatteryChargingFull,
        vesselLore = "Docks the ship in cryogenic low-power mode during plasma reactor refueling. Displays real-time wattage, fuel capacity, and mission widgets.",
        androidUtility = "Standalone fullscreen charging dashboard and DreamService screensaver featuring live wattage, time to full, burn-in pixel drift, and 3rd-party widget hosting.",
        tabIndex = 1,
        sectionKey = "refueling"
    ),
    GuidebookEntry(
        id = "vault",
        vesselTitle = "Config Vault",
        androidTitle = "Offline JSON Backup & Restore Engine",
        icon = Icons.Default.Storage,
        vesselLore = "An air-gapped crystalline datacore archiving all bridge ergonomics, flight trajectories, and customized telemetry calibrations in standalone JSON format.",
        androidUtility = "100% offline configuration engine that exports and restores all gesture matrices, haptic profiles, and embedded Base64 custom icons with instant hot reload.",
        tabIndex = 1,
        sectionKey = "backup"
    ),
    GuidebookEntry(
        id = "shizuku_jettison",
        vesselTitle = "Sub-Space Cloaked Egress",
        androidTitle = "Elevated Task Dismissal (OEM Kill-Bypass)",
        icon = Icons.Default.Close,
        vesselLore = "Low-signature orbital egress maneuver via privileged Binder. Quietly decouples the active viewport and clears its trace from the tactical radar without alerting planetary OEM battery sentinels, shielding background engines (like Termux) from aggressive purging.\n\n⚡ Default Vector: Deflector Inward Hold (Left & Right Flanks).",
        androidUtility = "Gracefully dismisses the active app window and removes it from the Recents stack via Shizuku Binder without triggering OEM aggressive force-stop scripts, keeping background daemons (like Termux) running safely.\n\n⚡ Default Gesture: Swipe Inward & Hold on either Deflector.",
        tabIndex = -1,
        sectionKey = ""
    ),
    GuidebookEntry(
        id = "system_overrides",
        vesselTitle = "System Overrides & Edge Sovereignty",
        androidTitle = "Native Edge Back Gesture Neutralization & Display Tuning",
        icon = Icons.Default.VerticalDistribute,
        vesselLore = "Direct command bypass overriding planetary OS telemetry. Calibrates border sensor sovereignty, animation speed acceleration, and DPI scaling.",
        androidUtility = "Zeroes out Android's native back gesture sensitivity (back_gesture_inset_scale_left/right = 0) via Shizuku/ADB to eliminate edge touch conflicts, and configures global OS animation and display DPI scales.",
        tabIndex = 1,
        sectionKey = "system_overrides"
    ),
    GuidebookEntry(
        id = "gesture_scrubbers",
        vesselTitle = "Scrubbers & Precision Calibration",
        androidTitle = "Sliders, Tactile Value Pills & Preset Profiles",
        icon = Icons.Default.Tune,
        vesselLore = "Sub-system scrubbers engineered with Prism thumb tracking and dual-mode gearing. Vertical drag activates micro-precision 1-step gearing. Tapping a tactile value pill deploys the Precision Control & Presets flyout matrix; long-pressing resets the setting to factory default.",
        androidUtility = "Interactive slider controls with vertical drag precision gearing. Tapping any numeric value box/pill opens Precision Control & Presets to type exact numbers, save custom preset profiles, or toggle tap-to-jump. Long-pressing the value pill resets it to factory default.",
        tabIndex = 1,
        sectionKey = "scrubbers"
    )
)


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GuidebookBottomSheet(
    onDismiss: () -> Unit,
    onNavigateToSection: (tabIndex: Int, sectionKey: String) -> Unit,
    onReplayWelcome: (() -> Unit)? = null
) {
    val lsColors = LightspeedStyle.colors
    var searchQuery by remember { mutableStateOf("") }
    val context = androidx.compose.ui.platform.LocalContext.current
    val configuration = androidx.compose.ui.platform.LocalConfiguration.current
    val isLandscape = configuration.screenWidthDp > configuration.screenHeightDp
    val isWideLayout = isLandscape || configuration.screenWidthDp >= 600
    val isCompactLandscape = isLandscape && configuration.screenHeightDp < 460
    val viewMode by com.sbf.lightspeed.system.LightspeedLanguageEngine.modeFlow.collectAsState()

    val filteredEntries = remember(searchQuery) {
        if (searchQuery.isBlank()) {
            GUIDEBOOK_ENTRIES
        } else {
            val q = searchQuery.lowercase(Locale.US)
            GUIDEBOOK_ENTRIES.filter {
                it.vesselTitle.lowercase(Locale.US).contains(q) ||
                        it.androidTitle.lowercase(Locale.US).contains(q) ||
                        it.vesselLore.lowercase(Locale.US).contains(q) ||
                        it.androidUtility.lowercase(Locale.US).contains(q)
            }
        }
    }

    val activeSnapshot = LocalDeckTheme.current
    val cornerDp = activeSnapshot.cornerRadiusDp.dp
    // Top two corners curved to theme radius; bottom two corners razor-sharp (0.dp) to feel rooted behind the bottom screen edge
    val sheetShape = RoundedCornerShape(topStart = cornerDp, topEnd = cornerDp, bottomEnd = 0.dp, bottomStart = 0.dp)
    val sheetMaxHeight = (configuration.screenHeightDp.dp * if (isLandscape) 0.92f else 0.88f).coerceAtLeast(240.dp)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = isWideLayout),
        sheetMaxWidth = if (isWideLayout) 900.dp else BottomSheetDefaults.SheetMaxWidth,
        containerColor = Color.Transparent,
        scrimColor = Color.Black.copy(alpha = 0.55f),
        tonalElevation = 0.dp,
        shape = sheetShape,
        dragHandle = null,
        contentWindowInsets = { WindowInsets(0, 0, 0, 0) }
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = sheetMaxHeight)
                .clip(sheetShape)
        ) {
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .clip(sheetShape)
            ) {
                ClippedFallbackBlurLayer(snapshot = activeSnapshot)
            }
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .deckGlassSurface(
                        snapshot = activeSnapshot,
                        isBlurAvailable = true,
                        shapeOverride = sheetShape
                    )
            )
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .windowInsetsPadding(WindowInsets.navigationBars)
                    .displayCutoutPadding()
                    .padding(
                        top = if (isCompactLandscape) 8.dp else 12.dp,
                        start = 18.dp,
                        end = 18.dp,
                        bottom = if (isCompactLandscape) 10.dp else 16.dp
                    ),
                verticalArrangement = Arrangement.spacedBy(if (isCompactLandscape) 8.dp else 12.dp)
            ) {
                Box(
                    modifier = Modifier.fillMaxWidth(),
                    contentAlignment = Alignment.Center
                ) {
                    Box(
                        modifier = Modifier
                            .width(42.dp)
                            .height(4.dp)
                            .clip(CircleShape)
                            .background(lsColors.textPrimary.copy(alpha = 0.32f))
                    )
                }

                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.weight(1f)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(if (isCompactLandscape) 32.dp else 38.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.MenuBook,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(if (isCompactLandscape) 17.dp else 20.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(
                                text = "The Stranded in Space Guidebook",
                                fontSize = if (isCompactLandscape) 14.5.sp else 16.sp,
                                fontWeight = FontWeight.Bold,
                                color = lsColors.textPrimary,
                                maxLines = 1
                            )
                            if (!isCompactLandscape) {
                                Text(
                                    text = "Standard Operating Procedures for Vessel Systems",
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.85f),
                                    maxLines = 1
                                )
                            }
                        }
                    }

                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier
                            .size(32.dp)
                            .clip(CircleShape)
                            .background(lsColors.textPrimary.copy(alpha = 0.08f))
                    ) {
                        Icon(Icons.Default.Close, contentDescription = "Close", tint = lsColors.textPrimary, modifier = Modifier.size(16.dp))
                    }
                }

                if (isWideLayout) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        GuidebookModeSelector(
                            viewMode = viewMode,
                            onSelectMode = { com.sbf.lightspeed.system.LightspeedLanguageEngine.setMode(context, it) },
                            modifier = Modifier.weight(1f)
                        )
                        GuidebookSearchField(
                            searchQuery = searchQuery,
                            onQueryChange = { searchQuery = it },
                            modifier = Modifier.weight(1.1f)
                        )
                    }
                } else {
                    GuidebookModeSelector(
                        viewMode = viewMode,
                        onSelectMode = { com.sbf.lightspeed.system.LightspeedLanguageEngine.setMode(context, it) },
                        modifier = Modifier.fillMaxWidth()
                    )
                    GuidebookSearchField(
                        searchQuery = searchQuery,
                        onQueryChange = { searchQuery = it },
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                // Entries List (2-column grid on tablets & wide landscape; full single column on portrait phones)
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f, fill = false),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    contentPadding = PaddingValues(bottom = 8.dp)
                ) {
                item(key = "github_readme_banner") {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.10f))
                            .border(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.35f), RoundedCornerShape(14.dp))
                            .clickable {
                                try {
                                    val uri = android.net.Uri.parse("https://github.com/SBFlabs/Lightspeed#readme")
                                    context.startActivity(
                                        android.content.Intent(android.content.Intent.ACTION_VIEW, uri)
                                            .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                                    )
                                } catch (_: Exception) { }
                            }
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Main Guidebook (GitHub README)",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = "Indexed Table of Contents with back links, screenshots, and showcase videos directly in the README.",
                                fontSize = 10.5.sp,
                                lineHeight = 14.sp,
                                color = lsColors.textPrimary.copy(alpha = 0.8f)
                            )
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "OPEN",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimary,
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(MaterialTheme.colorScheme.primary)
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                }
                if (onReplayWelcome != null) {
                    item(key = "replay_welcome_banner") {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(14.dp))
                                .background(lsColors.accent.copy(alpha = 0.10f))
                                .border(1.dp, lsColors.accent.copy(alpha = 0.35f), RoundedCornerShape(14.dp))
                                .clickable { onReplayWelcome() }
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Replay Welcome Aboard",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = lsColors.accent
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = "Revisit what Lightspeed is, Your Launch Pad, and quick-start links.",
                                    fontSize = 10.5.sp,
                                    lineHeight = 14.sp,
                                    color = lsColors.textPrimary.copy(alpha = 0.8f)
                                )
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "REPLAY",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = lsColors.accent,
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(lsColors.accent.copy(alpha = 0.18f))
                                    .border(0.8.dp, lsColors.accent.copy(alpha = 0.45f), RoundedCornerShape(8.dp))
                                    .padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }
                    }
                }
                item(key = "community_support_banner") {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f))
                            .border(1.dp, lsColors.textPrimary.copy(alpha = 0.10f), RoundedCornerShape(14.dp))
                            .padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = "Community, Issues & Q&A",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = lsColors.textPrimary
                        )
                        Text(
                            text = "• GitHub Issues: Bugs, feature feedback, language fixes & Q&A\n• XDA Forums: Open discussions & setup tips with users\n• YouTube & Reddit: Video clips & updates for convenience",
                            fontSize = 11.8.sp,
                            lineHeight = 16.5.sp,
                            color = lsColors.textPrimary.copy(alpha = 0.84f)
                        )
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            listOf(
                                "GITHUB ISSUES" to "https://github.com/SBFlabs/Lightspeed/issues",
                                "XDA THREAD" to "https://xdaforums.com/t/4804419/",
                                "YOUTUBE" to "https://www.youtube.com/@SBFlabs"
                            ).forEach { (label, url) ->
                                Text(
                                    text = label,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.14f))
                                        .border(0.8.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.35f), RoundedCornerShape(8.dp))
                                        .clickable { openExternalUrl(context, url) }
                                        .padding(horizontal = 9.dp, vertical = 4.dp)
                                )
                            }
                        }
                    }
                }
                if (isWideLayout) {
                    val rows = filteredEntries.chunked(2)
                    items(rows, key = { row -> row.joinToString("_") { it.id } }) { rowEntries ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            verticalAlignment = Alignment.Top
                        ) {
                            rowEntries.forEach { entry ->
                                GuidebookEntryCard(
                                    entry = entry,
                                    viewMode = viewMode,
                                    onDismiss = onDismiss,
                                    onNavigateToSection = onNavigateToSection,
                                    modifier = Modifier.weight(1f)
                                )
                            }
                            if (rowEntries.size == 1) {
                                Spacer(modifier = Modifier.weight(1f))
                            }
                        }
                    }
                } else {
                    items(filteredEntries, key = { it.id }) { entry ->
                        GuidebookEntryCard(
                            entry = entry,
                            viewMode = viewMode,
                            onDismiss = onDismiss,
                            onNavigateToSection = onNavigateToSection,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }
        }
    }
}
}

@Composable
private fun GuidebookModeSelector(
    viewMode: com.sbf.lightspeed.system.LightspeedLanguageEngine.LanguageMode,
    onSelectMode: (com.sbf.lightspeed.system.LightspeedLanguageEngine.LanguageMode) -> Unit,
    modifier: Modifier = Modifier
) {
    val lsColors = LightspeedStyle.colors
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(lsColors.textPrimary.copy(alpha = 0.06f))
            .padding(3.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        listOf(
            com.sbf.lightspeed.system.LightspeedLanguageEngine.LanguageMode.CO_PILOT to "Bilingual",
            com.sbf.lightspeed.system.LightspeedLanguageEngine.LanguageMode.VESSEL_LORE to "Space Lore",
            com.sbf.lightspeed.system.LightspeedLanguageEngine.LanguageMode.CLEAR_COMMS to "Clear Comms"
        ).forEach { (mode, label) ->
            val isSelected = viewMode == mode
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(9.dp))
                    .background(if (isSelected) MaterialTheme.colorScheme.primary else Color.Transparent)
                    .clickable { onSelectMode(mode) }
                    .padding(vertical = 8.dp, horizontal = 4.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = label,
                    fontSize = 11.sp,
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                    color = if (isSelected) MaterialTheme.colorScheme.onPrimary else lsColors.textPrimary.copy(alpha = 0.75f),
                    textAlign = TextAlign.Center,
                    maxLines = 1
                )
            }
        }
    }
}

@Composable
private fun GuidebookSearchField(
    searchQuery: String,
    onQueryChange: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val lsColors = LightspeedStyle.colors
    OutlinedTextField(
        value = searchQuery,
        onValueChange = onQueryChange,
        modifier = modifier,
        placeholder = {
            Text(
                "Search terminology or Android utilities…",
                fontSize = 12.sp,
                color = lsColors.textPrimary.copy(alpha = 0.45f),
                maxLines = 1
            )
        },
        leadingIcon = {
            Icon(
                Icons.Default.Search,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(18.dp)
            )
        },
        trailingIcon = {
            if (searchQuery.isNotEmpty()) {
                IconButton(onClick = { onQueryChange("") }) {
                    Icon(
                        Icons.Default.Clear,
                        contentDescription = "Clear",
                        tint = lsColors.textPrimary.copy(alpha = 0.6f),
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        },
        singleLine = true,
        shape = RoundedCornerShape(14.dp),
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = MaterialTheme.colorScheme.primary,
            unfocusedBorderColor = lsColors.textPrimary.copy(alpha = 0.12f),
            focusedContainerColor = lsColors.textPrimary.copy(alpha = 0.04f),
            unfocusedContainerColor = lsColors.textPrimary.copy(alpha = 0.04f),
            focusedTextColor = lsColors.textPrimary,
            unfocusedTextColor = lsColors.textPrimary
        )
    )
}

@Composable
private fun GuidebookEntryCard(
    entry: GuidebookEntry,
    viewMode: com.sbf.lightspeed.system.LightspeedLanguageEngine.LanguageMode,
    onDismiss: () -> Unit,
    onNavigateToSection: (tabIndex: Int, sectionKey: String) -> Unit,
    modifier: Modifier = Modifier
) {
    val lsColors = LightspeedStyle.colors
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f))
            .border(1.dp, lsColors.textPrimary.copy(alpha = 0.08f), RoundedCornerShape(16.dp))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(
                        imageVector = entry.icon,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = when (viewMode) {
                            com.sbf.lightspeed.system.LightspeedLanguageEngine.LanguageMode.VESSEL_LORE -> entry.vesselTitle
                            com.sbf.lightspeed.system.LightspeedLanguageEngine.LanguageMode.CLEAR_COMMS -> entry.androidTitle
                            com.sbf.lightspeed.system.LightspeedLanguageEngine.LanguageMode.CO_PILOT -> "${entry.vesselTitle}  ⇄  ${entry.androidTitle}"
                        },
                        fontSize = 13.5.sp,
                        fontWeight = FontWeight.Bold,
                        color = lsColors.textPrimary
                    )
                }

                if (entry.tabIndex >= 0 && entry.sectionKey.isNotEmpty()) {
                    DeckButton(
                        onClick = {
                            onDismiss()
                            onNavigateToSection(entry.tabIndex, entry.sectionKey)
                        },
                        isActive = true,
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 5.dp)
                    ) {
                        Text(
                            text = "Jump",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(12.dp)
                        )
                    }
                } else {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
                        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.4f))
                    ) {
                        Text(
                            text = if (viewMode == com.sbf.lightspeed.system.LightspeedLanguageEngine.LanguageMode.VESSEL_LORE) "INWARD + HOLD" else "SWIPE INWARD & HOLD",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp)
                        )
                    }
                }
            }

            if (viewMode == com.sbf.lightspeed.system.LightspeedLanguageEngine.LanguageMode.CO_PILOT || viewMode == com.sbf.lightspeed.system.LightspeedLanguageEngine.LanguageMode.VESSEL_LORE) {
                Text(
                    text = "Vessel Protocol: ${entry.vesselLore}",
                    fontSize = 12.sp,
                    color = lsColors.textPrimary.copy(alpha = 0.85f),
                    lineHeight = 16.sp
                )
            }

            if (viewMode == com.sbf.lightspeed.system.LightspeedLanguageEngine.LanguageMode.CO_PILOT || viewMode == com.sbf.lightspeed.system.LightspeedLanguageEngine.LanguageMode.CLEAR_COMMS) {
                Text(
                    text = "Android Utility: ${entry.androidUtility}",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.secondary.copy(alpha = 0.9f),
                    lineHeight = 16.sp
                )
            }
        }
    }
}
