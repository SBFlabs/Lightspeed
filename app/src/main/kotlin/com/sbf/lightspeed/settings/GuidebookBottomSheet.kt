package com.sbf.lightspeed.settings

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
        vesselTitle = "HUD Strip & Horizon Rail Sensor Deck",
        androidTitle = "Top Status Bar Overlay & Sensor Scrubbers",
        icon = Icons.Default.Speed,
        vesselLore = "The vessel's forward transparent HUD strip projects real-time atmospheric density and warp reactor telemetry along the horizon rail of the bridge.",
        androidUtility = "A non-intrusive top-edge overlay supporting pull-down scrubbers for instant brightness, volume, and screen timeout control without opening notification shades.",
        tabIndex = 1,
        sectionKey = "statusbar"
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
        vesselTitle = "Sub-Space Beacon & Orbital Capsule",
        androidTitle = "Dynamic Camera Cutout HUD & Mini-Player",
        icon = Icons.Default.Sensors,
        vesselLore = "A localized quantum orbital capsule that wraps around the optical sensor pod. Displays incoming subspace audio transmissions and data download streams.",
        androidUtility = "Dynamic orbital capsule with dual-wing layout, zero optical lens clipping, title marquee engine, and tap-to-expand liquid-glass media card with transport buttons.",
        tabIndex = 1,
        sectionKey = "telemetry"
    ),
    GuidebookEntry(
        id = "hardware_keys",
        vesselTitle = "Sub-Light Impulse Thrusters",
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
        vesselTitle = "Ship Data Log Vault",
        androidTitle = "Offline JSON Backup & Restore Engine",
        icon = Icons.Default.Storage,
        vesselLore = "An air-gapped crystalline datacore archiving all bridge ergonomics, flight trajectories, and customized telemetry calibrations in standalone JSON format.",
        androidUtility = "100% offline configuration engine that exports and restores all gesture matrices, haptic profiles, and embedded Base64 custom icons with instant hot reload.",
        tabIndex = 1,
        sectionKey = "backup"
    ),
    GuidebookEntry(
        id = "shizuku_jettison",
        vesselTitle = "Emergency Shizuku Jettison",
        androidTitle = "Elevated Task Closer & Subspace Watchdog",
        icon = Icons.Default.Close,
        vesselLore = "High-priority sub-space purge protocol that forcibly vents unresponsive background subroutines and memory leaks into deep vacuum.",
        androidUtility = "Uses Shizuku or Root to execute elevated task termination, instantly removing frozen games or heavy apps completely from the Android Recents overview.",
        tabIndex = 1,
        sectionKey = "watchdog"
    )
)

enum class GuidebookViewMode {
    BILINGUAL,
    VESSEL_LORE_ONLY,
    TACTICAL_ANDROID_ONLY
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GuidebookBottomSheet(
    onDismiss: () -> Unit,
    onNavigateToSection: (tabIndex: Int, sectionKey: String) -> Unit
) {
    var searchQuery by remember { mutableStateOf("") }
    var viewMode by remember { mutableStateOf(GuidebookViewMode.BILINGUAL) }

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

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = Color(0xFF10141E),
        tonalElevation = 16.dp,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        dragHandle = {
            Box(
                modifier = Modifier
                    .padding(vertical = 12.dp)
                    .width(40.dp)
                    .height(4.5.dp)
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = 0.25f))
            )
        }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 18.dp)
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(38.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.MenuBook,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            text = "The Stranded in Space Guidebook",
                            fontSize = 17.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                        Text(
                            text = "Standard Operating Procedures for Vessel Systems",
                            fontSize = 11.5.sp,
                            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.85f)
                        )
                    }
                }

                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier
                        .size(32.dp)
                        .clip(CircleShape)
                        .background(Color.White.copy(alpha = 0.08f))
                ) {
                    Icon(Icons.Default.Close, contentDescription = "Close", tint = Color.White, modifier = Modifier.size(16.dp))
                }
            }

            // Segmented 3-Way Mode Selector
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color.White.copy(alpha = 0.06f))
                    .padding(3.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                listOf(
                    GuidebookViewMode.BILINGUAL to "Bilingual",
                    GuidebookViewMode.VESSEL_LORE_ONLY to "Vessel Lore",
                    GuidebookViewMode.TACTICAL_ANDROID_ONLY to "Tactical Android"
                ).forEach { (mode, label) ->
                    val isSelected = viewMode == mode
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(9.dp))
                            .background(if (isSelected) MaterialTheme.colorScheme.primary else Color.Transparent)
                            .clickable { viewMode = mode }
                            .padding(vertical = 8.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = label,
                            fontSize = 11.sp,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                            color = if (isSelected) MaterialTheme.colorScheme.onPrimary else Color.White.copy(alpha = 0.75f),
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }

            // Search Bar
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("Search bridge terminology or Android utilities…", fontSize = 12.sp, color = Color.White.copy(alpha = 0.45f)) },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp)) },
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) {
                        IconButton(onClick = { searchQuery = "" }) {
                            Icon(Icons.Default.Clear, contentDescription = "Clear", tint = Color.White.copy(alpha = 0.6f), modifier = Modifier.size(16.dp))
                        }
                    }
                },
                singleLine = true,
                shape = RoundedCornerShape(14.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                    unfocusedBorderColor = Color.White.copy(alpha = 0.12f),
                    focusedContainerColor = Color.White.copy(alpha = 0.04f),
                    unfocusedContainerColor = Color.White.copy(alpha = 0.04f),
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White
                )
            )

            // Entries List
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 440.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(filteredEntries, key = { it.id }) { entry ->
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(16.dp))
                            .border(1.dp, Color.White.copy(alpha = 0.08f), RoundedCornerShape(16.dp)),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f))
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            // Entry Header
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
                                            GuidebookViewMode.VESSEL_LORE_ONLY -> entry.vesselTitle
                                            GuidebookViewMode.TACTICAL_ANDROID_ONLY -> entry.androidTitle
                                            GuidebookViewMode.BILINGUAL -> "${entry.vesselTitle}  ⇄  ${entry.androidTitle}"
                                        },
                                        fontSize = 13.5.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color.White
                                    )
                                }

                                // Quick Jump Button
                                Button(
                                    onClick = {
                                        onDismiss()
                                        onNavigateToSection(entry.tabIndex, entry.sectionKey)
                                    },
                                    modifier = Modifier.height(30.dp),
                                    contentPadding = PaddingValues(horizontal = 10.dp),
                                    shape = RoundedCornerShape(8.dp),
                                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.2f))
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(
                                            text = "Jump",
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Icon(
                                            imageVector = Icons.Default.ArrowForward,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(12.dp)
                                        )
                                    }
                                }
                            }

                            // Body Content based on View Mode
                            if (viewMode == GuidebookViewMode.BILINGUAL || viewMode == GuidebookViewMode.VESSEL_LORE_ONLY) {
                                Text(
                                    text = "Vessel Protocol: ${entry.vesselLore}",
                                    fontSize = 12.sp,
                                    color = Color.White.copy(alpha = 0.85f),
                                    lineHeight = 16.sp
                                )
                            }

                            if (viewMode == GuidebookViewMode.BILINGUAL || viewMode == GuidebookViewMode.TACTICAL_ANDROID_ONLY) {
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
            }
        }
    }
}
