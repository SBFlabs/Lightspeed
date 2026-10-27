@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class, androidx.compose.foundation.ExperimentalFoundationApi::class)
package com.sbf.lightspeed.settings

import android.content.Context
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sbf.lightspeed.system.LightspeedIconManager
import com.sbf.lightspeed.system.LightspeedOrientationEngine
import com.sbf.lightspeed.ui.theme.LightspeedStyle
import com.sbf.lightspeed.ui.theme.LightspeedTokens
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
internal fun SidebarAttitudeAppListSection(
    context: Context,
    searchQuery: String,
    pinnedTargets: List<AttitudeTargetItem>,
    regularTargets: List<AttitudeTargetItem>,
    filteredApps: List<AttitudeTargetItem>,
    assignedPackages: List<String>,
    letterIndices: Map<Char, Int>,
    listState: LazyListState,
    dynamicPrimary: Color,
    dynamicSecondary: Color,
    bucket: LightspeedOrientationEngine.AttitudeBucket?,
    onShowDetailInfo: (String, String) -> Unit,
    onTogglePackage: (String) -> Unit
) {
    val coroutineScope = rememberCoroutineScope()
    var sideBarHeight by remember { mutableStateOf(1f) }
    var isDragging by remember { mutableStateOf(false) }
    var hudLetter by remember { mutableStateOf("") }
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
                                    onShowDetailInfo = onShowDetailInfo,
                                    onToggle = { onTogglePackage(target.id) }
                                )
                            }

                            // Section 2: All Installed Apps
                            item(key = "header_regular") {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(top = 10.dp, bottom = 4.dp)
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(LightspeedStyle.colors.textPrimary.copy(alpha = 0.05f))
                                        .border(1.dp, LightspeedStyle.colors.textPrimary.copy(alpha = 0.12f), RoundedCornerShape(8.dp))
                                        .padding(horizontal = 10.dp, vertical = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "📱 ALL INSTALLED APPS (${regularTargets.size})",
                                        color = LightspeedStyle.colors.textPrimary.copy(alpha = 0.85f),
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
                                    onShowDetailInfo = onShowDetailInfo,
                                    onToggle = { onTogglePackage(target.id) }
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
                                    onShowDetailInfo = onShowDetailInfo,
                                    onToggle = { onTogglePackage(target.id) }
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
                                        color = if (hasApps) LightspeedStyle.colors.textPrimary.copy(alpha = 0.85f) else LightspeedStyle.colors.textPrimary.copy(alpha = 0.18f),
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
                            .background(LightspeedTokens.TacticalDeepSlate.copy(alpha = 0.90f))
                            .border(1.5.dp, dynamicPrimary, RoundedCornerShape(16.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(text = hudLetter, fontSize = 28.sp, fontWeight = FontWeight.ExtraBold, color = LightspeedStyle.colors.textPrimary)
                    }
                }
            }

}
