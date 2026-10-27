@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class, androidx.compose.foundation.ExperimentalFoundationApi::class)
package com.sbf.lightspeed.settings

import com.sbf.lightspeed.ui.theme.LocalDeckTheme
import com.sbf.lightspeed.ui.theme.LightspeedTokens



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
import com.sbf.lightspeed.system.logSwallowed
import com.sbf.lightspeed.ui.theme.LightspeedStyle
import java.util.Locale
import kotlinx.coroutines.launch


@Composable
fun AttitudeAppAssignmentSheet(
    context: Context,
    bucket: LightspeedOrientationEngine.AttitudeBucket,
    onDismiss: () -> Unit,
    onUpdated: () -> Unit
) {
    AppAssignmentSheet(
        context = context,
        title = "Assign Apps: ${bucket.title}",
        subtitle = bucket.subtitle,
        icon = when (bucket) {
            LightspeedOrientationEngine.AttitudeBucket.STRICT_PORTRAIT -> Icons.Default.StayCurrentPortrait
            LightspeedOrientationEngine.AttitudeBucket.SENSOR_PORTRAIT -> Icons.Default.ScreenRotationAlt
            LightspeedOrientationEngine.AttitudeBucket.SENSOR_LANDSCAPE -> Icons.Default.StayCurrentLandscape
            else -> Icons.Default.ScreenRotation
        },
        initialAssigned = remember(bucket) { LightspeedOrientationEngine.getAssignedPackages(context, bucket) },
        onSave = { pkgs -> LightspeedOrientationEngine.setAssignedPackages(context, bucket, pkgs) },
        onDismiss = onDismiss,
        onUpdated = onUpdated,
        bucket = bucket
    )
}

@Composable
fun AppAssignmentSheet(
    context: Context,
    title: String,
    subtitle: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector = Icons.Default.Apps,
    initialAssigned: Set<String>,
    onSave: (Set<String>) -> Unit,
    onDismiss: () -> Unit,
    onUpdated: () -> Unit,
    bucket: LightspeedOrientationEngine.AttitudeBucket? = null
) {
    var searchQuery by remember { mutableStateOf("") }
    val assignedPackages = remember(initialAssigned) {
        mutableStateListOf<String>().apply {
            addAll(initialAssigned)
        }
    }

    val (pinnedTargets, regularTargets) = rememberAttitudeTargets(context)

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
    val activeSnapshot = LocalDeckTheme.current
    val opticalColors = activeSnapshot.resolveOpticalColors()
    val cornerDp = activeSnapshot.cornerRadiusDp.dp
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = Color.Transparent,
        tonalElevation = 0.dp,
        shape = RoundedCornerShape(topStart = cornerDp, topEnd = cornerDp),
        dragHandle = null
    ) {
        val sheetShape = RoundedCornerShape(topStart = cornerDp, topEnd = cornerDp)
        Box(
            modifier = Modifier
                .fillMaxWidth()
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
                    .deckGlassSurface(snapshot = activeSnapshot, isBlurAvailable = true)
            )
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 12.dp, start = 16.dp, end = 16.dp, bottom = 16.dp)
            ) {
            // Drag handle pill at the top
            Box(
                modifier = Modifier.fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .width(40.dp)
                        .height(4.5.dp)
                        .clip(CircleShape)
                        .background(LightspeedStyle.colors.textPrimary.copy(alpha = 0.28f))
                )
            }
            Spacer(modifier = Modifier.height(10.dp))

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
                            .background(opticalColors.readableTintColor.copy(alpha = 0.16f), RoundedCornerShape(10.dp))
                            .border(1.dp, opticalColors.borderColor.copy(alpha = 0.40f), RoundedCornerShape(10.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = icon,
                            contentDescription = null,
                            tint = opticalColors.readableTintColor,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text(
                            text = title,
                            fontSize = 15.5.sp,
                            fontWeight = FontWeight.Bold,
                            color = LightspeedStyle.colors.textPrimary
                        )
                        Text(
                            text = if (subtitle.isNotEmpty()) "$subtitle (${assignedPackages.size} assigned)" else "${assignedPackages.size} assigned",
                            fontSize = 11.5.sp,
                            color = dynamicSecondary
                        )
                    }
                }
                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier.size(32.dp).background(LightspeedStyle.colors.textPrimary.copy(alpha = 0.08f), CircleShape)
                ) {
                    Icon(Icons.Default.Close, contentDescription = "Close", tint = LightspeedStyle.colors.textPrimary, modifier = Modifier.size(18.dp))
                }
            }

            var activeInfoDialog by remember { mutableStateOf<Pair<String, String>?>(null) }
            if (activeInfoDialog != null) {
                val (infoTitle, infoMessage) = activeInfoDialog!!
                DeckDialog(
                    onDismissRequest = { activeInfoDialog = null },
                    icon = {
                        Icon(
                            imageVector = if (infoTitle.contains("LOCK SCREEN") || infoTitle.contains("ADVISORY")) Icons.Default.PriorityHigh else Icons.Default.Info,
                            contentDescription = null,
                            tint = if (infoTitle.contains("LOCK SCREEN") || infoTitle.contains("ADVISORY")) LightspeedStyle.colors.accentWarn else dynamicPrimary,
                            modifier = Modifier.size(28.dp)
                        )
                    },
                    title = {
                        Text(
                            text = infoTitle,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            color = LightspeedStyle.colors.textPrimary,
                            textAlign = TextAlign.Center
                        )
                    },
                    text = {
                        Text(
                            text = infoMessage,
                            fontSize = 12.5.sp,
                            color = LightspeedStyle.colors.textSecondary.copy(alpha = 0.9f),
                            lineHeight = 18.sp,
                            textAlign = TextAlign.Start
                        )
                    },
                    confirmButton = {
                        TextButton(onClick = { activeInfoDialog = null }) {
                            Text("UNDERSTOOD", color = dynamicPrimary, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        }
                    }
                )
            }

            // Search Field
            TextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                placeholder = { Text("Search apps, messengers, launchers...", color = LightspeedStyle.colors.textPrimary.copy(alpha = 0.45f), fontSize = 13.sp) },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = LightspeedStyle.colors.textPrimary.copy(alpha = 0.6f), modifier = Modifier.size(18.dp)) },
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) {
                        IconButton(onClick = { searchQuery = "" }) {
                            Icon(Icons.Default.Close, contentDescription = "Clear", tint = LightspeedStyle.colors.textPrimary.copy(alpha = 0.6f), modifier = Modifier.size(16.dp))
                        }
                    }
                },
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = LightspeedStyle.colors.textPrimary.copy(alpha = 0.08f),
                    unfocusedContainerColor = LightspeedStyle.colors.textPrimary.copy(alpha = 0.05f),
                    focusedTextColor = LightspeedStyle.colors.textPrimary,
                    unfocusedTextColor = LightspeedStyle.colors.textPrimary,
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent,
                    disabledIndicatorColor = Color.Transparent
                ),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth().padding(bottom = 6.dp),
                singleLine = true
            )

            // Assigned Targets Tray
            SidebarAttitudeAssignedTray(
                context = context,
                assignedPackages = assignedPackages,
                allTargets = allTargets,
                dynamicPrimary = dynamicPrimary,
                dynamicSecondary = dynamicSecondary,
                onSave = onSave,
                onUpdated = onUpdated
            )

            val togglePackage: (String) -> Unit = { pkg ->
                if (assignedPackages.contains(pkg)) assignedPackages.remove(pkg)
                else assignedPackages.add(pkg)
                onSave(assignedPackages.toSet())
                onUpdated()
            }

            // Main List + A-Z Scrubber
            SidebarAttitudeAppListSection(
                context = context,
                searchQuery = searchQuery,
                pinnedTargets = pinnedTargets,
                regularTargets = regularTargets,
                filteredApps = filteredApps,
                assignedPackages = assignedPackages,
                letterIndices = letterIndices,
                listState = listState,
                dynamicPrimary = dynamicPrimary,
                dynamicSecondary = dynamicSecondary,
                bucket = bucket,
                onShowDetailInfo = { infoTitle, msg -> activeInfoDialog = Pair(infoTitle, msg) },
                onTogglePackage = togglePackage
            )

            // Bottom Save/Clear Bar
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                if (assignedPackages.isNotEmpty()) {
                    OutlinedButton(
                        onClick = {
                            assignedPackages.clear()
                            onSave(emptySet())
                            onUpdated()
                        },
                        modifier = Modifier.weight(1f).height(46.dp),
                        shape = RoundedCornerShape(12.dp),
                        border = androidx.compose.foundation.BorderStroke(1.dp, LightspeedStyle.colors.textPrimary.copy(alpha = 0.20f))
                    ) {
                        Text("CLEAR ALL", color = LightspeedTokens.StatusErrorSoft, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                    }
                }

                Button(
                    onClick = onDismiss,
                    colors = ButtonDefaults.buttonColors(containerColor = dynamicPrimary),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.weight(if (assignedPackages.isNotEmpty()) 1.2f else 1f).height(46.dp)
                ) {
                    Text("DONE (${assignedPackages.size})", color = LightspeedStyle.colors.textPrimary, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                }
            }
        }
        }
    }
}

