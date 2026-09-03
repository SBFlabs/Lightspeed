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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import android.content.Intent
import com.sbf.lightspeed.GearPickerActivity
import com.sbf.lightspeed.LightspeedAccessibilityService
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import com.sbf.lightspeed.system.LightspeedBackTapEngine
import com.sbf.lightspeed.system.LightspeedBackupEngine
import com.sbf.lightspeed.system.LightspeedHapticEngine
import com.sbf.lightspeed.system.LightspeedKeyEngine
import com.sbf.lightspeed.system.LightspeedOrientationEngine
import com.sbf.lightspeed.system.LightspeedPreferences
import com.sbf.lightspeed.system.LightspeedWatchdogEngine
import com.sbf.lightspeed.system.ElevatedTaskCloser
import com.sbf.lightspeed.system.OemNotchDetector
import com.sbf.lightspeed.system.TacticalFlyoutLauncher
import com.sbf.lightspeed.system.TacticalAudioEngine
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun TabAccordionPopover(
    tabIndex: Int,
    tabTitle: String,
    currentMode: String,
    pinnedSectionId: String?,
    sectionTitles: Map<String, String>,
    onSelectMode: (String) -> Unit,
    onToggleBlueprintMode: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Tune, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text("$tabTitle Blueprint", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = Color.White)
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    "Select default accordion display behavior for this tab:",
                    fontSize = 12.sp,
                    color = Color.LightGray.copy(alpha = 0.9f)
                )

                val modes = listOf(
                    Triple("sticky", "Remember Last State (Sticky)", "Preserve the exact open and collapsed states of each section across app restarts."),
                    Triple("custom_pinned", "Anchored Solo", "Designated anchor card stays open; non-pinned cards swap in Solo focus mode."),
                    Triple("solo", "Focus / Solo Mode", "Expanding any card automatically snaps all other cards shut."),
                    Triple("all_expanded", "All Expanded", "All accordion cards default open on tab entry."),
                    Triple("all_collapsed", "All Collapsed", "All accordion cards default closed on tab entry.")
                )

                modes.forEach { (modeKey, title, desc) ->
                    val isSelected = currentMode == modeKey
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .clickable {
                                onSelectMode(modeKey)
                            },
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.2f) else MaterialTheme.colorScheme.surface.copy(alpha = 0.35f)
                        ),
                        border = androidx.compose.foundation.BorderStroke(
                            1.dp,
                            if (isSelected) MaterialTheme.colorScheme.primary else Color.White.copy(alpha = 0.08f)
                        )
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = isSelected,
                                onClick = { onSelectMode(modeKey) },
                                colors = RadioButtonDefaults.colors(selectedColor = MaterialTheme.colorScheme.primary)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Column {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(title, fontWeight = FontWeight.SemiBold, fontSize = 13.sp, color = Color.White)
                                    if (modeKey == "custom_pinned" && pinnedSectionId != null) {
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Surface(
                                            shape = RoundedCornerShape(6.dp),
                                            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
                                            border = androidx.compose.foundation.BorderStroke(0.8.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.4f))
                                        ) {
                                            Text(
                                                text = "Anchor: ${sectionTitles[pinnedSectionId] ?: pinnedSectionId}",
                                                fontSize = 10.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = MaterialTheme.colorScheme.primary,
                                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                            )
                                        }
                                    }
                                }
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(desc, fontSize = 10.5.sp, color = Color.LightGray.copy(alpha = 0.75f), lineHeight = 13.sp)
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(4.dp))
                OutlinedButton(
                    onClick = {
                        onToggleBlueprintMode()
                        onDismiss()
                    },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.secondary.copy(alpha = 0.6f))
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
                        Icon(Icons.Default.DashboardCustomize, contentDescription = null, tint = MaterialTheme.colorScheme.secondary, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Edit Section Blueprint & Reorder", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = MaterialTheme.colorScheme.secondary)
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Close", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
            }
        },
        shape = RoundedCornerShape(20.dp),
        containerColor = Color(0xFF10121C)
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AttitudeAppAssignmentSheet(
    context: Context,
    bucket: LightspeedOrientationEngine.AttitudeBucket,
    onDismiss: () -> Unit,
    onUpdated: () -> Unit
) {
    var searchQuery by remember { mutableStateOf("") }
    val assignedPackages = remember(bucket) {
        mutableStateListOf<String>().apply {
            addAll(LightspeedOrientationEngine.getAssignedPackages(context, bucket))
        }
    }

    val installedApps = remember {
        val pm = context.packageManager
        val intent = Intent(Intent.ACTION_MAIN, null).apply { addCategory(Intent.CATEGORY_LAUNCHER) }
        pm.queryIntentActivities(intent, 0).map {
            val pkg = it.activityInfo.packageName
            val label = it.loadLabel(pm).toString()
            Pair(pkg, label)
        }.distinctBy { it.first }.sortedBy { it.second.lowercase(Locale.ROOT) }
    }

    val filteredApps = remember(searchQuery, installedApps) {
        if (searchQuery.isBlank()) installedApps
        else installedApps.filter { it.second.contains(searchQuery, ignoreCase = true) || it.first.contains(searchQuery, ignoreCase = true) }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = Color(0xFF10121C),
        dragHandle = { BottomSheetDefaults.DragHandle() }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .padding(bottom = 24.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = when (bucket) {
                        LightspeedOrientationEngine.AttitudeBucket.STRICT_PORTRAIT -> Icons.Default.StayCurrentPortrait
                        LightspeedOrientationEngine.AttitudeBucket.SENSOR_PORTRAIT -> Icons.Default.ScreenRotationAlt
                        LightspeedOrientationEngine.AttitudeBucket.SENSOR_LANDSCAPE -> Icons.Default.StayCurrentLandscape
                        else -> Icons.Default.ScreenRotation
                    },
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(22.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Column {
                    Text(
                        text = "Assign Apps: ${bucket.title}",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                    Text(
                        text = bucket.subtitle,
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
            Spacer(modifier = Modifier.height(10.dp))

            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                placeholder = { Text("Search installed apps...", fontSize = 13.sp) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                shape = RoundedCornerShape(12.dp)
            )
            Spacer(modifier = Modifier.height(10.dp))

            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 380.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                items(count = filteredApps.size, key = { filteredApps[it].first }) { index ->
                    val app = filteredApps[index]
                    val pkg = app.first
                    val label = app.second
                    val isAssigned = assignedPackages.contains(pkg)
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .clickable {
                                if (isAssigned) assignedPackages.remove(pkg)
                                else assignedPackages.add(pkg)
                                LightspeedOrientationEngine.setAssignedPackages(context, bucket, assignedPackages.toSet())
                                onUpdated()
                            },
                        shape = RoundedCornerShape(10.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = if (isAssigned) MaterialTheme.colorScheme.primary.copy(alpha = 0.18f) else MaterialTheme.colorScheme.surface.copy(alpha = 0.35f)
                        )
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Checkbox(
                                checked = isAssigned,
                                onCheckedChange = { checked ->
                                    if (checked) assignedPackages.add(pkg)
                                    else assignedPackages.remove(pkg)
                                    LightspeedOrientationEngine.setAssignedPackages(context, bucket, assignedPackages.toSet())
                                    onUpdated()
                                }
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(label, fontWeight = FontWeight.SemiBold, fontSize = 13.sp, color = Color.White)
                                Text(pkg, fontSize = 10.5.sp, color = Color.LightGray.copy(alpha = 0.6f))
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun BlueprintWireframeView(
    tabTitle: String,
    sectionIds: List<String>,
    pinnedSectionId: String?,
    sectionTitles: Map<String, String>,
    onMoveUp: (Int) -> Unit,
    onMoveDown: (Int) -> Unit,
    onPinSection: (String) -> Unit,
    onExitBlueprint: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)),
            border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.4f))
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Default.ViewAgenda, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                Spacer(modifier = Modifier.width(10.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text("$tabTitle — Blueprint Reorder Mode", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = Color.White)
                    Text("Use ▲ / ▼ to reorder sections. Tap 📌 to designate the anchor open section.", fontSize = 11.sp, color = Color.LightGray.copy(alpha = 0.85f))
                }
            }
        }

        sectionIds.forEachIndexed { index, secId ->
            val isPinned = (secId == pinnedSectionId)
            val title = sectionTitles[secId] ?: secId

            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(
                    containerColor = if (isPinned) MaterialTheme.colorScheme.primary.copy(alpha = 0.18f) else Color(0xFF141724).copy(alpha = 0.8f)
                ),
                border = androidx.compose.foundation.BorderStroke(
                    1.2.dp,
                    if (isPinned) MaterialTheme.colorScheme.primary else Color.White.copy(alpha = 0.12f)
                )
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Default.DragHandle,
                        contentDescription = "Drag Handle",
                        tint = Color.White.copy(alpha = 0.45f),
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = title,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 13.sp,
                            color = Color.White
                        )
                        if (isPinned) {
                            Text(
                                "📌 Default Pinned Section",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }

                    // Pin Anchor Button
                    IconButton(
                        onClick = { onPinSection(secId) },
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.PushPin,
                            contentDescription = "Pin",
                            tint = if (isPinned) MaterialTheme.colorScheme.primary else Color.White.copy(alpha = 0.35f),
                            modifier = Modifier.size(18.dp)
                        )
                    }

                    // Move Up
                    IconButton(
                        onClick = { onMoveUp(index) },
                        enabled = index > 0,
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            Icons.Default.KeyboardArrowUp,
                            contentDescription = "Up",
                            tint = if (index > 0) MaterialTheme.colorScheme.secondary else Color.DarkGray,
                            modifier = Modifier.size(18.dp)
                        )
                    }

                    // Move Down
                    IconButton(
                        onClick = { onMoveDown(index) },
                        enabled = index < sectionIds.size - 1,
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            Icons.Default.KeyboardArrowDown,
                            contentDescription = "Down",
                            tint = if (index < sectionIds.size - 1) MaterialTheme.colorScheme.secondary else Color.DarkGray,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }
        }

        Button(
            onClick = onExitBlueprint,
            modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
            shape = RoundedCornerShape(12.dp),
            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
        ) {
            Text("Done (Exit Blueprint)", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = MaterialTheme.colorScheme.onPrimary)
        }
    }
}
