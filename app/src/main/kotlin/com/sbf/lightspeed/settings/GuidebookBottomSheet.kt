package com.sbf.lightspeed.settings

import androidx.annotation.StringRes
import com.sbf.lightspeed.R
import com.sbf.lightspeed.system.LightspeedLocaleEngine
import com.sbf.lightspeed.system.lsString

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
import androidx.compose.ui.platform.LocalDensity
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
    @StringRes val vesselTitleRes: Int,
    @StringRes val androidTitleRes: Int,
    val icon: androidx.compose.ui.graphics.vector.ImageVector,
    @StringRes val vesselLoreRes: Int,
    @StringRes val androidUtilityRes: Int,
    val tabIndex: Int,
    val sectionKey: String
)

val GUIDEBOOK_ENTRIES = listOf(
    GuidebookEntry(
        id = "canopy",
        vesselTitleRes = R.string.gb_canopy_vt,
        androidTitleRes = R.string.gb_canopy_at,
        icon = Icons.Default.TouchApp,
        vesselLoreRes = R.string.gb_canopy_vl,
        androidUtilityRes = R.string.gb_canopy_au,
        tabIndex = 1,
        sectionKey = "statusbar"
    ),
    GuidebookEntry(
        id = "synthetic_gravity",
        vesselTitleRes = R.string.gb_gravity_vt,
        androidTitleRes = R.string.gb_gravity_at,
        icon = Icons.Default.Rotate90DegreesCw,
        vesselLoreRes = R.string.gb_gravity_vl,
        androidUtilityRes = R.string.gb_gravity_au,
        tabIndex = 1,
        sectionKey = "gravity"
    ),
    GuidebookEntry(
        id = "deflectors",
        vesselTitleRes = R.string.gb_deflectors_vt,
        androidTitleRes = R.string.gb_deflectors_at,
        icon = Icons.Default.SwapHoriz,
        vesselLoreRes = R.string.gb_deflectors_vl,
        androidUtilityRes = R.string.gb_deflectors_au,
        tabIndex = 0,
        sectionKey = "wings"
    ),
    GuidebookEntry(
        id = "notch_beacon",
        vesselTitleRes = R.string.gb_beacon_vt,
        androidTitleRes = R.string.gb_beacon_at,
        icon = Icons.Default.Sensors,
        vesselLoreRes = R.string.gb_beacon_vl,
        androidUtilityRes = R.string.gb_beacon_au,
        tabIndex = 1,
        sectionKey = "telemetry"
    ),
    GuidebookEntry(
        id = "hardware_keys",
        vesselTitleRes = R.string.gb_hwkeys_vt,
        androidTitleRes = R.string.gb_hwkeys_at,
        icon = Icons.Default.Tune,
        vesselLoreRes = R.string.gb_hwkeys_vl,
        androidUtilityRes = R.string.gb_hwkeys_au,
        tabIndex = 1,
        sectionKey = "volumekeys"
    ),
    GuidebookEntry(
        id = "back_tap",
        vesselTitleRes = R.string.gb_backtap_vt,
        androidTitleRes = R.string.gb_backtap_at,
        icon = Icons.Default.TouchApp,
        vesselLoreRes = R.string.gb_backtap_vl,
        androidUtilityRes = R.string.gb_backtap_au,
        tabIndex = 1,
        sectionKey = "backtap"
    ),
    GuidebookEntry(
        id = "refueling_bay",
        vesselTitleRes = R.string.gb_refuel_vt,
        androidTitleRes = R.string.gb_refuel_at,
        icon = Icons.Default.BatteryChargingFull,
        vesselLoreRes = R.string.gb_refuel_vl,
        androidUtilityRes = R.string.gb_refuel_au,
        tabIndex = 1,
        sectionKey = "refueling"
    ),
    GuidebookEntry(
        id = "shizuku_jettison",
        vesselTitleRes = R.string.gb_jettison_vt,
        androidTitleRes = R.string.gb_jettison_at,
        icon = Icons.Default.Close,
        vesselLoreRes = R.string.gb_jettison_vl,
        androidUtilityRes = R.string.gb_jettison_au,
        tabIndex = -1,
        sectionKey = ""
    ),
    GuidebookEntry(
        id = "system_overrides",
        vesselTitleRes = R.string.gb_overrides_vt,
        androidTitleRes = R.string.gb_overrides_at,
        icon = Icons.Default.VerticalDistribute,
        vesselLoreRes = R.string.gb_overrides_vl,
        androidUtilityRes = R.string.gb_overrides_au,
        tabIndex = 1,
        sectionKey = "system_overrides"
    ),
    GuidebookEntry(
        id = "gesture_scrubbers",
        vesselTitleRes = R.string.gb_scrubbers_vt,
        androidTitleRes = R.string.gb_scrubbers_at,
        icon = Icons.Default.Tune,
        vesselLoreRes = R.string.gb_scrubbers_vl,
        androidUtilityRes = R.string.gb_scrubbers_au,
        tabIndex = 1,
        sectionKey = "scrubbers"
    ),
    GuidebookEntry(
        id = "vault",
        vesselTitleRes = R.string.gb_vault_vt,
        androidTitleRes = R.string.gb_vault_at,
        icon = Icons.Default.Storage,
        vesselLoreRes = R.string.gb_vault_vl,
        androidUtilityRes = R.string.gb_vault_au,
        tabIndex = 1,
        sectionKey = "backup"
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

    val localeState = LightspeedLocaleEngine.state
    val filteredEntries = remember(searchQuery, localeState) {
        if (searchQuery.isBlank()) {
            GUIDEBOOK_ENTRIES
        } else {
            val q = searchQuery.lowercase(Locale.getDefault())
            GUIDEBOOK_ENTRIES.filter {
                lsString(it.vesselTitleRes).lowercase(Locale.getDefault()).contains(q) ||
                    lsString(it.androidTitleRes).lowercase(Locale.getDefault()).contains(q) ||
                    lsString(it.vesselLoreRes).lowercase(Locale.getDefault()).contains(q) ||
                    lsString(it.androidUtilityRes).lowercase(Locale.getDefault()).contains(q)
            }
        }
    }

    val activeDensity = LocalDensity.current
    val activeSnapshot = LocalDeckTheme.current
    val cornerDp = activeSnapshot.cornerRadiusDp.dp
    // Top two corners curved to theme radius; bottom two corners razor-sharp (0.dp) to feel rooted behind the bottom screen edge
    val sheetShape = RoundedCornerShape(topStart = cornerDp, topEnd = cornerDp, bottomEnd = 0.dp, bottomStart = 0.dp)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = isCompactLandscape),
        sheetMaxWidth = if (isWideLayout) 900.dp else BottomSheetDefaults.SheetMaxWidth,
        containerColor = Color.Transparent,
        scrimColor = Color.Black.copy(alpha = 0.55f),
        tonalElevation = 0.dp,
        shape = sheetShape,
        dragHandle = null,
        contentWindowInsets = { WindowInsets(0, 0, 0, 0) }
    ) {
        CompositionLocalProvider(LocalDensity provides activeDensity) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.94f)
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
                    .fillMaxSize()
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
                            BilingualTitleText(
                                title = com.sbf.lightspeed.system.LightspeedLanguageEngine.resolve(
                                    com.sbf.lightspeed.system.LightspeedVocabulary.Key.GUIDEBOOK,
                                    viewMode
                                ),
                                fontSize = if (isCompactLandscape) 14.5.sp else 16.sp,
                                fontWeight = FontWeight.Bold,
                                color = lsColors.textPrimary,
                                preferStacked = true,
                                maxLines = 1,
                                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                            )
                            if (!isCompactLandscape) {
                                Text(
                                    text = if (viewMode == com.sbf.lightspeed.system.LightspeedLanguageEngine.LanguageMode.CLEAR_COMMS) {
                                        lsString(R.string.ls_guidebook_sub_clear)
                                    } else {
                                        lsString(R.string.ls_guidebook_sub_lore)
                                    },
                                    fontSize = 11.sp,
                                    lineHeight = 14.sp,
                                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.85f),
                                    maxLines = 2
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
                        Icon(Icons.Default.Close, contentDescription = lsString(R.string.ls_close), tint = lsColors.textPrimary, modifier = Modifier.size(16.dp))
                    }
                }

                GuidebookLanguageSlider()

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
                val guidebookListState = androidx.compose.foundation.lazy.rememberLazyListState()
                LazyColumn(
                    state = guidebookListState,
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .verticalEdgeFade(guidebookListState),
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
                                    val uri = android.net.Uri.parse(com.sbf.lightspeed.system.LightspeedLocaleEngine.readmeUrl())
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
                                text = lsString(R.string.gb_readme_title),
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = lsString(R.string.gb_readme_sub),
                                fontSize = 10.5.sp,
                                lineHeight = 14.sp,
                                color = lsColors.textPrimary.copy(alpha = 0.8f)
                            )
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = lsString(R.string.gb_btn_open),
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
                                    text = lsString(R.string.gb_replay_title),
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = lsColors.accent
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = lsString(R.string.gb_replay_sub),
                                    fontSize = 10.5.sp,
                                    lineHeight = 14.sp,
                                    color = lsColors.textPrimary.copy(alpha = 0.8f)
                                )
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = lsString(R.string.gb_btn_replay),
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
                            text = lsString(R.string.wel_community_title),
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = lsColors.textPrimary
                        )
                        Text(
                            text = lsString(R.string.gb_comm_bullets),
                            fontSize = 11.8.sp,
                            lineHeight = 16.5.sp,
                            color = lsColors.textPrimary.copy(alpha = 0.84f)
                        )
                        GuidebookSupportLinksRow(context = context)
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
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun GuidebookSupportLinksRow(context: android.content.Context) {
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        listOf(
            "GITHUB ISSUES" to "https://github.com/SBFlabs/Lightspeed/issues",
            lsString(R.string.gb_xda_chip) to "https://xdaforums.com/t/4804419/",
            "YOUTUBE" to "https://www.youtube.com/@SBFlabs"
        ).forEach { (label, url) ->
            Text(
                text = label,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                softWrap = false,
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
            com.sbf.lightspeed.system.LightspeedLanguageEngine.LanguageMode.CO_PILOT to lsString(R.string.ls_vocab_bilingual),
            com.sbf.lightspeed.system.LightspeedLanguageEngine.LanguageMode.VESSEL_LORE to lsString(R.string.ls_vocab_space_lore),
            com.sbf.lightspeed.system.LightspeedLanguageEngine.LanguageMode.CLEAR_COMMS to lsString(R.string.ls_vocab_clear_comms)
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
                lsString(R.string.ls_guidebook_search),
                fontSize = 12.sp,
                color = lsColors.textPrimary.copy(alpha = 0.45f),
                maxLines = 1,
                softWrap = false
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
                    val vesselTitle = lsString(entry.vesselTitleRes)
                    val androidTitle = lsString(entry.androidTitleRes)
                    Text(
                        text = when (viewMode) {
                            com.sbf.lightspeed.system.LightspeedLanguageEngine.LanguageMode.VESSEL_LORE -> vesselTitle
                            com.sbf.lightspeed.system.LightspeedLanguageEngine.LanguageMode.CLEAR_COMMS -> androidTitle
                            com.sbf.lightspeed.system.LightspeedLanguageEngine.LanguageMode.CO_PILOT -> {
                                if (vesselTitle.equals(androidTitle, ignoreCase = true)) {
                                    vesselTitle
                                } else {
                                    "$vesselTitle  ⇄  $androidTitle"
                                }
                            }
                        },
                        fontSize = 13.5.sp,
                        fontWeight = FontWeight.Bold,
                        color = lsColors.textPrimary
                    )
                }

                if (entry.tabIndex >= 0 && entry.sectionKey.isNotEmpty()) {
                    Spacer(modifier = Modifier.width(8.dp))
                    DeckButton(
                        onClick = {
                            onDismiss()
                            onNavigateToSection(entry.tabIndex, entry.sectionKey)
                        },
                        isActive = true,
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 5.dp)
                    ) {
                        val contentColor = LocalContentColor.current
                        Text(
                            text = lsString(R.string.gb_jump),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = contentColor
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                            contentDescription = null,
                            tint = contentColor,
                            modifier = Modifier.size(12.dp)
                        )
                    }
                }
            }

            if (entry.tabIndex < 0 || entry.sectionKey.isEmpty()) {
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
                    border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.4f))
                ) {
                    Text(
                        text = if (viewMode == com.sbf.lightspeed.system.LightspeedLanguageEngine.LanguageMode.VESSEL_LORE) {
                            lsString(R.string.gb_inward_hold_lore)
                        } else {
                            lsString(R.string.gb_inward_hold_clear)
                        },
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp)
                    )
                }
            }

            if (viewMode == com.sbf.lightspeed.system.LightspeedLanguageEngine.LanguageMode.CO_PILOT || viewMode == com.sbf.lightspeed.system.LightspeedLanguageEngine.LanguageMode.VESSEL_LORE) {
                Text(
                    text = lsString(R.string.gb_prefix_vessel, lsString(entry.vesselLoreRes)),
                    fontSize = 12.sp,
                    color = lsColors.textPrimary.copy(alpha = 0.85f),
                    lineHeight = 16.sp
                )
            }

            if (viewMode == com.sbf.lightspeed.system.LightspeedLanguageEngine.LanguageMode.CO_PILOT || viewMode == com.sbf.lightspeed.system.LightspeedLanguageEngine.LanguageMode.CLEAR_COMMS) {
                val androidUtility = lsString(entry.androidUtilityRes)
                Text(
                    text = if (viewMode == com.sbf.lightspeed.system.LightspeedLanguageEngine.LanguageMode.CO_PILOT) {
                        lsString(R.string.gb_prefix_android, androidUtility)
                    } else {
                        androidUtility
                    },
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.secondary.copy(alpha = 0.9f),
                    lineHeight = 16.sp
                )
            }
        }
    }
}
