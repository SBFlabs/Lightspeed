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
import android.os.Build
import androidx.compose.foundation.Image
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import com.sbf.lightspeed.system.LightspeedIconManager
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
data class AttitudeTargetItem(
    val id: String,
    val label: String,
    val subtitle: String,
    val isPinned: Boolean,
    val badge: String? = null
)

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

    val (pinnedTargets, regularTargets) = remember {
        val pm = context.packageManager
        val pinned = mutableListOf<AttitudeTargetItem>()
        val regular = mutableListOf<AttitudeTargetItem>()
        val seenPackages = mutableSetOf<String>()

        // 1. Virtual Core: Lock Screen
        pinned.add(
            AttitudeTargetItem(
                id = "keyguard:lockscreen",
                label = "Lock Screen (Keyguard)",
                subtitle = "System Lock Screen & Ambient Display",
                isPinned = true,
                badge = "SYSTEM"
            )
        )
        seenPackages.add("keyguard:lockscreen")

        // 2. All Launcher Apps (CATEGORY_HOME)
        val homeIntent = Intent(Intent.ACTION_MAIN).apply { addCategory(Intent.CATEGORY_HOME) }
        val homeResolves = pm.queryIntentActivities(homeIntent, 0)
        homeResolves.forEach { ri ->
            val pkg = ri.activityInfo.packageName
            if (pkg !in seenPackages && pkg != "android") {
                seenPackages.add(pkg)
                val label = ri.loadLabel(pm).toString().ifBlank { pkg }
                pinned.add(
                    AttitudeTargetItem(
                        id = pkg,
                        label = label,
                        subtitle = "Home Launcher ($pkg)",
                        isPinned = true,
                        badge = "LAUNCHER"
                    )
                )
            }
        }

        // 3. Phone / Dialer
        val dialerIntent = Intent(Intent.ACTION_DIAL)
        val dialerResolves = pm.queryIntentActivities(dialerIntent, 0)
        dialerResolves.forEach { ri ->
            val pkg = ri.activityInfo.packageName
            if (pkg !in seenPackages && pkg != "android") {
                seenPackages.add(pkg)
                val label = ri.loadLabel(pm).toString().ifBlank { pkg }
                pinned.add(
                    AttitudeTargetItem(
                        id = pkg,
                        label = label,
                        subtitle = "Phone & Dialer ($pkg)",
                        isPinned = true,
                        badge = "PHONE"
                    )
                )
            }
        }

        // 4. SMS / Messaging
        val smsIntent = Intent(Intent.ACTION_SENDTO).apply { data = android.net.Uri.parse("smsto:") }
        val smsResolves = pm.queryIntentActivities(smsIntent, 0)
        smsResolves.forEach { ri ->
            val pkg = ri.activityInfo.packageName
            if (pkg !in seenPackages && pkg != "android") {
                seenPackages.add(pkg)
                val label = ri.loadLabel(pm).toString().ifBlank { pkg }
                pinned.add(
                    AttitudeTargetItem(
                        id = pkg,
                        label = label,
                        subtitle = "Text & SMS ($pkg)",
                        isPinned = true,
                        badge = "SMS"
                    )
                )
            }
        }

        // 5. Popular Messengers & Derivatives (WhatsApp, Telegram, Messenger)
        val popularMessengers = listOf(
            Triple(listOf("com.whatsapp", "com.whatsapp.w4b", "com.gbwhatsapp", "com.yowhatsapp"), "WhatsApp", "WHATSAPP"),
            Triple(listOf("org.telegram.messenger", "org.thunderdog.challegram", "org.telegram.plus", "org.telegram.messenger.web"), "Telegram", "TELEGRAM"),
            Triple(listOf("com.facebook.orca", "com.facebook.mlite"), "Messenger", "MESSENGER")
        )
        popularMessengers.forEach { (pkgList, fallbackName, badgeName) ->
            pkgList.forEach { candidatePkg ->
                try {
                    val appInfo = pm.getApplicationInfo(candidatePkg, 0)
                    if (candidatePkg !in seenPackages) {
                        seenPackages.add(candidatePkg)
                        val label = pm.getApplicationLabel(appInfo).toString().ifBlank { fallbackName }
                        pinned.add(
                            AttitudeTargetItem(
                                id = candidatePkg,
                                label = label,
                                subtitle = "$fallbackName ($candidatePkg)",
                                isPinned = true,
                                badge = badgeName
                            )
                        )
                    }
                } catch (_: Exception) {}
            }
        }

        // 6. Settings App
        val settingsPkg = "com.android.settings"
        try {
            val appInfo = pm.getApplicationInfo(settingsPkg, 0)
            if (settingsPkg !in seenPackages) {
                seenPackages.add(settingsPkg)
                val label = pm.getApplicationLabel(appInfo).toString().ifBlank { "Settings" }
                pinned.add(
                    AttitudeTargetItem(
                        id = settingsPkg,
                        label = label,
                        subtitle = "System Settings ($settingsPkg)",
                        isPinned = true,
                        badge = "SETTINGS"
                    )
                )
            }
        } catch (_: Exception) {}

        // 7. All other installed launcher apps (standard user apps)
        val launcherIntent = Intent(Intent.ACTION_MAIN, null).apply { addCategory(Intent.CATEGORY_LAUNCHER) }
        val launcherResolves = pm.queryIntentActivities(launcherIntent, 0)
        launcherResolves.forEach { ri ->
            val pkg = ri.activityInfo.packageName
            if (pkg !in seenPackages) {
                seenPackages.add(pkg)
                val label = ri.loadLabel(pm).toString().ifBlank { pkg }
                regular.add(
                    AttitudeTargetItem(
                        id = pkg,
                        label = label,
                        subtitle = pkg,
                        isPinned = false,
                        badge = null
                    )
                )
            }
        }

        regular.sortBy { it.label.lowercase(Locale.ROOT) }
        Pair(pinned, regular)
    }

    val allTargets = remember(pinnedTargets, regularTargets) { pinnedTargets + regularTargets }

    val filteredApps = remember(searchQuery, allTargets) {
        if (searchQuery.isBlank()) allTargets
        else allTargets.filter {
            it.label.contains(searchQuery, ignoreCase = true) ||
            it.id.contains(searchQuery, ignoreCase = true) ||
            (it.badge?.contains(searchQuery, ignoreCase = true) == true)
        }
    }

    val letterIndices = remember(regularTargets) {
        val map = mutableMapOf<Char, Int>()
        regularTargets.forEachIndexed { index, item ->
            val firstChar = item.label.firstOrNull()?.uppercaseChar() ?: '#'
            val key = if (firstChar in 'A'..'Z') firstChar else '#'
            if (key != '#' && !map.containsKey(key)) {
                map[key] = index
            }
        }
        map
    }

    val primaryAccent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        context.getColor(android.R.color.system_accent1_600)
    } else {
        0xFF6750A4.toInt()
    }
    val secondaryAccent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        context.getColor(android.R.color.system_accent1_300)
    } else {
        0xFFD0BCFF.toInt()
    }
    val dynamicPrimary = Color(primaryAccent)
    val dynamicSecondary = Color(secondaryAccent)

    val listState = rememberLazyListState()
    val coroutineScope = rememberCoroutineScope()
    var sideBarHeight by remember { mutableStateOf(1f) }
    var isDragging by remember { mutableStateOf(false) }
    var hudLetter by remember { mutableStateOf("") }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = Color(0xFF10121C),
        dragHandle = { BottomSheetDefaults.DragHandle() }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .padding(bottom = 16.dp)
        ) {
            // Header Row
            Row(
                modifier = Modifier.fillMaxWidth().padding(bottom = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                    Box(
                        modifier = Modifier
                            .size(38.dp)
                            .background(dynamicPrimary.copy(alpha = 0.16f), RoundedCornerShape(10.dp))
                            .border(1.dp, dynamicPrimary.copy(alpha = 0.40f), RoundedCornerShape(10.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = when (bucket) {
                                LightspeedOrientationEngine.AttitudeBucket.STRICT_PORTRAIT -> Icons.Default.StayCurrentPortrait
                                LightspeedOrientationEngine.AttitudeBucket.SENSOR_PORTRAIT -> Icons.Default.ScreenRotationAlt
                                LightspeedOrientationEngine.AttitudeBucket.SENSOR_LANDSCAPE -> Icons.Default.StayCurrentLandscape
                                else -> Icons.Default.ScreenRotation
                            },
                            contentDescription = null,
                            tint = dynamicPrimary,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text(
                            text = "Assign Apps: ${bucket.title}",
                            fontSize = 15.5.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                        Text(
                            text = "${bucket.subtitle} (${assignedPackages.size} assigned)",
                            fontSize = 11.5.sp,
                            color = dynamicSecondary
                        )
                    }
                }
                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier.size(32.dp).background(Color.White.copy(alpha = 0.08f), CircleShape)
                ) {
                    Icon(Icons.Default.Close, contentDescription = "Close", tint = Color.White, modifier = Modifier.size(18.dp))
                }
            }

            // Search Field
            TextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                placeholder = { Text("Search apps, messengers, launchers...", color = Color.White.copy(alpha = 0.45f), fontSize = 13.sp) },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = Color.White.copy(alpha = 0.6f), modifier = Modifier.size(18.dp)) },
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) {
                        IconButton(onClick = { searchQuery = "" }) {
                            Icon(Icons.Default.Close, contentDescription = "Clear", tint = Color.White.copy(alpha = 0.6f), modifier = Modifier.size(16.dp))
                        }
                    }
                },
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = Color.White.copy(alpha = 0.08f),
                    unfocusedContainerColor = Color.White.copy(alpha = 0.05f),
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White,
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent,
                    disabledIndicatorColor = Color.Transparent
                ),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth().padding(bottom = 6.dp),
                singleLine = true
            )

            // Assigned Targets Tray
            if (assignedPackages.isNotEmpty()) {
                Text(
                    text = "ASSIGNED TARGETS (${assignedPackages.size}) — TAP ✕ TO REMOVE:",
                    fontSize = 9.5.sp,
                    fontWeight = FontWeight.Bold,
                    color = dynamicSecondary,
                    modifier = Modifier.padding(top = 2.dp, bottom = 4.dp)
                )
                LazyRow(
                    modifier = Modifier.fillMaxWidth().padding(bottom = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    items(assignedPackages.size, key = { idx -> "assigned_${assignedPackages[idx]}" }) { idx ->
                        val pkg = assignedPackages[idx]
                        val targetItem = allTargets.firstOrNull { it.id == pkg }
                        val label = targetItem?.label ?: pkg
                        val iconBmp = remember(pkg) {
                            if (pkg == "keyguard:lockscreen") null
                            else LightspeedIconManager.getIconBitmap(context, pkg)
                        }

                        Row(
                            modifier = Modifier
                                .background(Color.White.copy(alpha = 0.08f), RoundedCornerShape(10.dp))
                                .border(1.dp, dynamicPrimary.copy(alpha = 0.5f), RoundedCornerShape(10.dp))
                                .padding(horizontal = 8.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            if (iconBmp != null) {
                                Image(
                                    bitmap = iconBmp.asImageBitmap(),
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp).clip(RoundedCornerShape(4.dp)).padding(end = 2.dp)
                                )
                            } else {
                                Icon(
                                    imageVector = if (pkg == "keyguard:lockscreen") Icons.Default.Lock else Icons.Default.Apps,
                                    contentDescription = null,
                                    tint = dynamicPrimary,
                                    modifier = Modifier.size(16.dp).padding(end = 2.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = label.take(15),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = Color.White
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            IconButton(
                                onClick = {
                                    assignedPackages.remove(pkg)
                                    LightspeedOrientationEngine.setAssignedPackages(context, bucket, assignedPackages.toSet())
                                    onUpdated()
                                },
                                modifier = Modifier.size(18.dp)
                            ) {
                                Icon(
                                    Icons.Default.Close,
                                    contentDescription = "Remove",
                                    tint = Color(0xFFFF6B6B),
                                    modifier = Modifier.size(13.dp)
                                )
                            }
                        }
                    }
                }
            }

            // Main List + A-Z Scrubber
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 260.dp, max = 460.dp)
            ) {
                Row(modifier = Modifier.fillMaxSize()) {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier.weight(1f).fillMaxHeight(),
                        verticalArrangement = Arrangement.spacedBy(3.dp)
                    ) {
                        if (searchQuery.isBlank()) {
                            // Section 1: Pinned Targets
                            item(key = "header_pinned") {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(top = 4.dp, bottom = 4.dp)
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(dynamicPrimary.copy(alpha = 0.08f))
                                        .border(1.dp, dynamicPrimary.copy(alpha = 0.20f), RoundedCornerShape(8.dp))
                                        .padding(horizontal = 10.dp, vertical = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "⚡ PINNED & CORE TARGETS (${pinnedTargets.size})",
                                        color = dynamicPrimary,
                                        fontSize = 11.5.sp,
                                        fontWeight = FontWeight.Bold,
                                        letterSpacing = 0.8.sp,
                                        modifier = Modifier.weight(1f)
                                    )
                                }
                            }

                            items(items = pinnedTargets, key = { it.id }) { target ->
                                val isAssigned = assignedPackages.contains(target.id)
                                AttitudeTargetRow(
                                    item = target,
                                    isAssigned = isAssigned,
                                    context = context,
                                    dynamicPrimary = dynamicPrimary,
                                    dynamicSecondary = dynamicSecondary,
                                    onToggle = {
                                        if (isAssigned) assignedPackages.remove(target.id)
                                        else assignedPackages.add(target.id)
                                        LightspeedOrientationEngine.setAssignedPackages(context, bucket, assignedPackages.toSet())
                                        onUpdated()
                                    }
                                )
                            }

                            // Section 2: All Installed Apps
                            item(key = "header_regular") {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(top = 10.dp, bottom = 4.dp)
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(Color.White.copy(alpha = 0.05f))
                                        .border(1.dp, Color.White.copy(alpha = 0.12f), RoundedCornerShape(8.dp))
                                        .padding(horizontal = 10.dp, vertical = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "📱 ALL INSTALLED APPS (${regularTargets.size})",
                                        color = Color.White.copy(alpha = 0.85f),
                                        fontSize = 11.5.sp,
                                        fontWeight = FontWeight.Bold,
                                        letterSpacing = 0.8.sp,
                                        modifier = Modifier.weight(1f)
                                    )
                                }
                            }

                            items(items = regularTargets, key = { it.id }) { target ->
                                val isAssigned = assignedPackages.contains(target.id)
                                AttitudeTargetRow(
                                    item = target,
                                    isAssigned = isAssigned,
                                    context = context,
                                    dynamicPrimary = dynamicPrimary,
                                    dynamicSecondary = dynamicSecondary,
                                    onToggle = {
                                        if (isAssigned) assignedPackages.remove(target.id)
                                        else assignedPackages.add(target.id)
                                        LightspeedOrientationEngine.setAssignedPackages(context, bucket, assignedPackages.toSet())
                                        onUpdated()
                                    }
                                )
                            }
                        } else {
                            // Search Results
                            item(key = "header_search") {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(top = 4.dp, bottom = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "SEARCH RESULTS (${filteredApps.size} MATCHES)",
                                        color = dynamicSecondary,
                                        fontSize = 10.5.sp,
                                        fontWeight = FontWeight.Bold,
                                        letterSpacing = 0.8.sp
                                    )
                                }
                            }

                            items(items = filteredApps, key = { it.id }) { target ->
                                val isAssigned = assignedPackages.contains(target.id)
                                AttitudeTargetRow(
                                    item = target,
                                    isAssigned = isAssigned,
                                    context = context,
                                    dynamicPrimary = dynamicPrimary,
                                    dynamicSecondary = dynamicSecondary,
                                    onToggle = {
                                        if (isAssigned) assignedPackages.remove(target.id)
                                        else assignedPackages.add(target.id)
                                        LightspeedOrientationEngine.setAssignedPackages(context, bucket, assignedPackages.toSet())
                                        onUpdated()
                                    }
                                )
                            }
                        }
                    }

                    // A-Z Scrubber Sidebar
                    if (searchQuery.isBlank() && regularTargets.isNotEmpty()) {
                        Column(
                            modifier = Modifier
                                .fillMaxHeight()
                                .width(28.dp)
                                .padding(start = 4.dp)
                                .onGloballyPositioned { sideBarHeight = it.size.height.toFloat().coerceAtLeast(1f) }
                                .pointerInput(regularTargets) {
                                    awaitPointerEventScope {
                                        while (true) {
                                            val down = awaitFirstDown(requireUnconsumed = false)
                                            isDragging = true
                                            val alphabet = "#ABCDEFGHIJKLMNOPQRSTUVWXYZ"
                                            val currentY = down.position.y.coerceIn(0f, sideBarHeight)
                                            val ratio = (currentY / sideBarHeight).coerceIn(0f, 1f)
                                            val letterIdx = (ratio * 26).toInt().coerceIn(0, 26)
                                            val letter = alphabet[letterIdx]
                                            hudLetter = letter.toString()

                                            if (letter == '#') {
                                                coroutineScope.launch { listState.scrollToItem(0) }
                                            } else if (letterIndices.containsKey(letter)) {
                                                val targetIndex = letterIndices[letter]!!
                                                coroutineScope.launch { listState.scrollToItem(1 + pinnedTargets.size + 1 + targetIndex) }
                                            }
                                            down.consume()

                                            while (true) {
                                                val event = awaitPointerEvent()
                                                val dragChange = event.changes.firstOrNull()
                                                if (dragChange != null && dragChange.pressed) {
                                                    val dragY = dragChange.position.y.coerceIn(0f, sideBarHeight)
                                                    val dragRatio = (dragY / sideBarHeight).coerceIn(0f, 1f)
                                                    val dragLetterIdx = (dragRatio * 26).toInt().coerceIn(0, 26)
                                                    val dragLetter = alphabet[dragLetterIdx]
                                                    hudLetter = dragLetter.toString()

                                                    if (dragLetter == '#') {
                                                        coroutineScope.launch { listState.scrollToItem(0) }
                                                    } else if (letterIndices.containsKey(dragLetter)) {
                                                        val targetIndex = letterIndices[dragLetter]!!
                                                        coroutineScope.launch { listState.scrollToItem(1 + pinnedTargets.size + 1 + targetIndex) }
                                                    }
                                                    dragChange.consume()
                                                } else {
                                                    isDragging = false
                                                    break
                                                }
                                            }
                                        }
                                    }
                                }
                            },
                            verticalArrangement = Arrangement.SpaceEvenly,
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            "#ABCDEFGHIJKLMNOPQRSTUVWXYZ".forEach { letter ->
                                val hasApps = (letter == '#' || letterIndices.containsKey(letter))
                                Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) {
                                    Text(
                                        text = letter.toString(),
                                        fontSize = 8.5.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = if (hasApps) dynamicSecondary else Color.White.copy(alpha = 0.2f)
                                    )
                                }
                            }
                        }
                    }
                }

                // Dragging HUD Letter Overlay
                if (isDragging && hudLetter.isNotEmpty()) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.Center)
                            .size(72.dp)
                            .background(Color(0xFF1E1E28).copy(alpha = 0.94f), shape = RoundedCornerShape(16.dp))
                            .border(2.dp, dynamicPrimary, shape = RoundedCornerShape(16.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(text = hudLetter, fontSize = 28.sp, fontWeight = FontWeight.ExtraBold, color = Color.White)
                    }
                }
            }

            // Bottom Save/Clear Bar
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                if (assignedPackages.isNotEmpty()) {
                    OutlinedButton(
                        onClick = {
                            assignedPackages.clear()
                            LightspeedOrientationEngine.setAssignedPackages(context, bucket, emptySet())
                            onUpdated()
                        },
                        modifier = Modifier.weight(1f).height(46.dp),
                        shape = RoundedCornerShape(12.dp),
                        border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.20f))
                    ) {
                        Text("CLEAR ALL", color = Color(0xFFFF6B6B), fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                    }
                }

                Button(
                    onClick = onDismiss,
                    colors = ButtonDefaults.buttonColors(containerColor = dynamicPrimary),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.weight(if (assignedPackages.isNotEmpty()) 1.2f else 1f).height(46.dp)
                ) {
                    Text("DONE (${assignedPackages.size})", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                }
            }
        }
    }
}

@Composable
private fun AttitudeTargetRow(
    item: AttitudeTargetItem,
    isAssigned: Boolean,
    context: Context,
    dynamicPrimary: Color,
    dynamicSecondary: Color,
    onToggle: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(50.dp)
            .padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .clip(RoundedCornerShape(12.dp))
                .background(
                    if (isAssigned) dynamicPrimary.copy(alpha = 0.28f)
                    else if (item.isPinned) Color(0x18FFD700)
                    else Color.White.copy(alpha = 0.06f)
                )
                .border(
                    width = 1.dp,
                    color = if (isAssigned) dynamicPrimary.copy(alpha = 0.65f)
                    else if (item.isPinned) Color(0x66FFD700)
                    else Color.White.copy(alpha = 0.07f),
                    shape = RoundedCornerShape(12.dp)
                )
                .clickable(onClick = onToggle)
                .padding(horizontal = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Icon
            if (item.id == "keyguard:lockscreen") {
                Box(
                    modifier = Modifier
                        .size(28.dp)
                        .background(Color(0xFFFFD700).copy(alpha = 0.18f), RoundedCornerShape(6.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Lock,
                        contentDescription = null,
                        tint = Color(0xFFFFD700),
                        modifier = Modifier.size(16.dp)
                    )
                }
            } else {
                val iconBmp = remember(item.id) {
                    LightspeedIconManager.getIconBitmap(context, item.id)
                }
                if (iconBmp != null) {
                    Image(
                        bitmap = iconBmp.asImageBitmap(),
                        contentDescription = null,
                        modifier = Modifier
                            .size(28.dp)
                            .clip(RoundedCornerShape(6.dp))
                    )
                } else {
                    val iconVector = when (item.badge) {
                        "LAUNCHER" -> Icons.Default.Home
                        "PHONE" -> Icons.Default.Phone
                        "SMS" -> Icons.Default.Chat
                        "SETTINGS" -> Icons.Default.Settings
                        else -> Icons.Default.Apps
                    }
                    Box(
                        modifier = Modifier
                            .size(28.dp)
                            .background(Color.White.copy(alpha = 0.12f), RoundedCornerShape(6.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = iconVector,
                            contentDescription = null,
                            tint = Color.White.copy(alpha = 0.7f),
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.width(10.dp))

            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight(),
                verticalArrangement = Arrangement.spacedBy(1.dp, Alignment.CenterVertically)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (item.isPinned && item.id != "keyguard:lockscreen") {
                        Icon(
                            imageVector = Icons.Outlined.PushPin,
                            contentDescription = "Pinned",
                            tint = Color(0xFFFFD700),
                            modifier = Modifier.size(13.dp).padding(end = 4.dp)
                        )
                    }
                    Text(
                        text = item.label,
                        color = if (isAssigned) dynamicPrimary
                        else if (item.isPinned) Color(0xFFFFF176)
                        else Color.White,
                        fontSize = 13.5.sp,
                        fontWeight = if (isAssigned || item.isPinned) FontWeight.Bold else FontWeight.SemiBold,
                        style = TextStyle(
                            platformStyle = PlatformTextStyle(includeFontPadding = false),
                            lineHeight = 16.sp
                        ),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (item.badge != null) {
                        Spacer(modifier = Modifier.width(6.dp))
                        Surface(
                            shape = RoundedCornerShape(4.dp),
                            color = if (item.badge == "SYSTEM") Color(0xFFFFD700).copy(alpha = 0.2f) else dynamicPrimary.copy(alpha = 0.2f),
                            border = androidx.compose.foundation.BorderStroke(
                                0.5.dp,
                                if (item.badge == "SYSTEM") Color(0xFFFFD700).copy(alpha = 0.6f) else dynamicPrimary.copy(alpha = 0.5f)
                            )
                        ) {
                            Text(
                                text = item.badge,
                                fontSize = 8.5.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = if (item.badge == "SYSTEM") Color(0xFFFFD700) else dynamicPrimary,
                                modifier = Modifier.padding(horizontal = 4.5.dp, vertical = 1.dp)
                            )
                        }
                    }
                }
                Text(
                    text = item.subtitle,
                    color = Color.LightGray.copy(alpha = 0.55f),
                    fontSize = 10.sp,
                    style = TextStyle(
                        platformStyle = PlatformTextStyle(includeFontPadding = false),
                        lineHeight = 12.sp
                    ),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            if (isAssigned) {
                Box(
                    modifier = Modifier
                        .padding(start = 6.dp)
                        .background(dynamicPrimary, CircleShape)
                        .padding(horizontal = 7.dp, vertical = 2.5.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "ASSIGNED",
                        color = Color.Black,
                        fontSize = 8.sp,
                        fontWeight = FontWeight.ExtraBold
                    )
                }
            } else {
                Box(
                    modifier = Modifier
                        .padding(start = 6.dp)
                        .size(18.dp)
                        .border(1.2.dp, Color.White.copy(alpha = 0.22f), CircleShape)
                )
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
