@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class, androidx.compose.foundation.ExperimentalFoundationApi::class)
package com.sbf.lightspeed.settings



import android.content.Context
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material.icons.automirrored.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
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
import com.sbf.lightspeed.system.LightspeedOrientationEngine
import java.util.Locale
import kotlinx.coroutines.launch


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

            var activeInfoDialog by remember { mutableStateOf<Pair<String, String>?>(null) }
            if (activeInfoDialog != null) {
                val (infoTitle, infoMessage) = activeInfoDialog!!
                AlertDialog(
                    onDismissRequest = { activeInfoDialog = null },
                    icon = {
                        Icon(
                            imageVector = if (infoTitle.contains("LOCK SCREEN") || infoTitle.contains("ADVISORY")) Icons.Default.PriorityHigh else Icons.Default.Info,
                            contentDescription = null,
                            tint = if (infoTitle.contains("LOCK SCREEN") || infoTitle.contains("ADVISORY")) Color(0xFFFFB300) else dynamicPrimary,
                            modifier = Modifier.size(28.dp)
                        )
                    },
                    title = {
                        Text(
                            text = infoTitle,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                            textAlign = TextAlign.Center
                        )
                    },
                    text = {
                        Text(
                            text = infoMessage,
                            fontSize = 12.5.sp,
                            color = Color.LightGray.copy(alpha = 0.9f),
                            lineHeight = 18.sp,
                            textAlign = TextAlign.Start
                        )
                    },
                    confirmButton = {
                        TextButton(onClick = { activeInfoDialog = null }) {
                            Text("UNDERSTOOD", color = dynamicPrimary, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        }
                    },
                    containerColor = Color(0xFF1B1F2B),
                    shape = RoundedCornerShape(16.dp)
                )
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
                            else LightspeedIconManager.getIconBitmap(context, pkg, useCache = false)
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
                                    bucket = bucket,
                                    onShowDetailInfo = { title, msg -> activeInfoDialog = Pair(title, msg) },
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
                                    bucket = bucket,
                                    onShowDetailInfo = { title, msg -> activeInfoDialog = Pair(title, msg) },
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
                                    bucket = bucket,
                                    onShowDetailInfo = { title, msg -> activeInfoDialog = Pair(title, msg) },
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
                                },
                            verticalArrangement = Arrangement.SpaceEvenly,
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            "#ABCDEFGHIJKLMNOPQRSTUVWXYZ".forEach { letter ->
                                val hasApps = (letter == '#' || letterIndices.containsKey(letter))
                                Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) {
                                    Text(
                                        text = letter.toString(),
                                        color = if (hasApps) Color.White.copy(alpha = 0.85f) else Color.White.copy(alpha = 0.18f),
                                        fontSize = 8.5.sp,
                                        fontWeight = if (hasApps) FontWeight.Bold else FontWeight.Normal
                                    )
                                }
                            }
                        }
                    }
                }

                // HUD Magnifier Preview
                if (isDragging && hudLetter.isNotBlank()) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.Center)
                            .size(72.dp)
                            .clip(RoundedCornerShape(16.dp))
                            .background(Color(0xE610121C))
                            .border(1.5.dp, dynamicPrimary, RoundedCornerShape(16.dp)),
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
    bucket: LightspeedOrientationEngine.AttitudeBucket,
    onShowDetailInfo: ((title: String, message: String) -> Unit)? = null,
    onToggle: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 52.dp)
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
                    LightspeedIconManager.getIconBitmap(context, item.id, useCache = false)
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
                        "SMS" -> Icons.AutoMirrored.Filled.Chat
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
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    if (item.badge != null) {
                        Spacer(modifier = Modifier.width(6.dp))
                        Surface(
                            shape = RoundedCornerShape(4.dp),
                            color = if (item.badge == "SYSTEM") Color(0xFFFFD700).copy(alpha = 0.2f) else dynamicPrimary.copy(alpha = 0.2f),
                            border = androidx.compose.foundation.BorderStroke(
                                0.5.dp,
                                if (item.badge == "SYSTEM") Color(0xFFFFD700).copy(alpha = 0.6f) else dynamicPrimary.copy(alpha = 0.5f)
                            ),
                            modifier = Modifier.wrapContentWidth()
                        ) {
                            Text(
                                text = item.badge,
                                fontSize = 8.5.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = if (item.badge == "SYSTEM") Color(0xFFFFD700) else dynamicPrimary,
                                modifier = Modifier.padding(horizontal = 4.5.dp, vertical = 1.dp),
                                softWrap = false,
                                maxLines = 1
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

            if (item.id == "keyguard:lockscreen" && bucket != LightspeedOrientationEngine.AttitudeBucket.STRICT_PORTRAIT) {
                Box(
                    modifier = Modifier
                        .padding(end = 6.dp)
                        .size(26.dp)
                        .clip(CircleShape)
                        .background(Color(0xFFFFB300).copy(alpha = 0.15f))
                        .border(1.dp, Color(0xFFFFB300).copy(alpha = 0.45f), CircleShape)
                        .clickable {
                            onShowDetailInfo?.invoke(
                                "LOCK SCREEN INTERLOCK",
                                "Under-display optical or ultrasonic fingerprint sensors (UDFPS) lock the Keyguard to 0° portrait at the Android OS level to keep the physical optical lens aligned with the OLED display.\n\nDevices with side power-button sensors, rear sensors, or face unlock will rotate normally."
                            )
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.PriorityHigh,
                        contentDescription = "Lockscreen Interlock Info",
                        tint = Color(0xFFFFB300),
                        modifier = Modifier.size(15.dp)
                    )
                }
            } else if (item.badge == "LAUNCHER" && bucket == LightspeedOrientationEngine.AttitudeBucket.SENSOR_PORTRAIT) {
                Box(
                    modifier = Modifier
                        .padding(end = 6.dp)
                        .size(26.dp)
                        .clip(CircleShape)
                        .background(dynamicPrimary.copy(alpha = 0.15f))
                        .border(1.dp, dynamicPrimary.copy(alpha = 0.45f), CircleShape)
                        .clickable {
                            onShowDetailInfo?.invoke(
                                "ACTIVE GYRO DRIVER (PHONES VS TABLETS)",
                                "• Phone vs Tablet/Fold Discrepancy:\nTablets and large foldables (sw600dp+) feature adaptive multi-column grids that rotate cleanly into landscape. Standard phone launchers are designed solely for vertical portrait grids and glitch/squish when forced into landscape.\n\n• Lightspeed Sensor Portrait Solution:\nThe active 5 Hz gyro driver dynamically flips your phone launcher between 0° upright and 180° inverted portrait based on tilt, bypassing launcher NOSENSOR locks while strictly locking out 90° and 270° landscape.\n\n• Zero Glitches:\nGives you full upside-down usability (charging from top or reading in bed) with zero home screen overlapping."
                            )
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Info,
                        contentDescription = "Active Gyro Info",
                        tint = dynamicPrimary,
                        modifier = Modifier.size(15.dp)
                    )
                }
            } else if (item.badge == "LAUNCHER" && (bucket == LightspeedOrientationEngine.AttitudeBucket.SENSOR_360 || bucket == LightspeedOrientationEngine.AttitudeBucket.SENSOR_LANDSCAPE)) {
                Box(
                    modifier = Modifier
                        .padding(end = 6.dp)
                        .size(26.dp)
                        .clip(CircleShape)
                        .background(Color(0xFFFFB300).copy(alpha = 0.15f))
                        .border(1.dp, Color(0xFFFFB300).copy(alpha = 0.45f), CircleShape)
                        .clickable {
                            onShowDetailInfo?.invoke(
                                "LAUNCHER LANDSCAPE ADVISORY",
                                "• Form Factor Discrepancy:\nTablets and foldables (sw600dp+) handle landscape home screens natively with wide multi-column layouts. Standard phone launchers lack adaptive landscape grids.\n\n• Phone Launcher Glitching:\nForcing landscape on a phone launcher compresses widgets, overlaps search bars, and misaligns icon grids.\n\n• Recommended Setting:\nFor phones, assign the Launcher to 'Sensor Portrait' instead to enjoy 180° inverted portrait freedom without landscape glitches."
                            )
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.PriorityHigh,
                        contentDescription = "Launcher Landscape Advisory",
                        tint = Color(0xFFFFB300),
                        modifier = Modifier.size(15.dp)
                    )
                }
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

