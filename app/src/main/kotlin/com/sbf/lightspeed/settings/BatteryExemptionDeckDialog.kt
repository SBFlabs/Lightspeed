package com.sbf.lightspeed.settings

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.os.Build
import android.view.WindowManager
import android.widget.Toast
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BatteryChargingFull
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import com.sbf.lightspeed.system.ElevatedTaskCloser
import com.sbf.lightspeed.system.LightspeedHapticEngine
import com.sbf.lightspeed.system.LightspeedPreferences
import com.sbf.lightspeed.system.LightspeedWatchdogEngine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

@Composable
fun BatteryExemptionDeckDialog(
    context: Context,
    onDismiss: () -> Unit,
    onExemptionChanged: () -> Unit = {}
) {
    val coroutineScope = rememberCoroutineScope()
    val cautionAmber = Color(0xFFFFB300)
    val successGreen = Color(0xFF00E676)
    val pm = context.packageManager

    var isLoading by remember { mutableStateOf(true) }
    var allApps by remember { mutableStateOf<List<BatteryExemptAppItem>>(emptyList()) }
    var pinnedAppsSet by remember { mutableStateOf(LightspeedPreferences.getPinnedBatteryExemptApps(context)) }
    var exemptedPackagesSet by remember { mutableStateOf<Set<String>>(emptySet()) }
    var searchQuery by rememberSaveable { mutableStateOf("") }
    var filterMode by rememberSaveable { mutableIntStateOf(0) } // 0: All, 1: Exempted, 2: Pinned

    val haptic = LocalHapticFeedback.current
    val density = LocalDensity.current
    val thresholdPx = with(density) { 90.dp.toPx() }
    val dragOffsetY = remember { Animatable(0f) }
    var hasCrossedThreshold by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) {
            val packages = pm.getInstalledApplications(PackageManager.GET_META_DATA)
            val items = packages.mapNotNull { appInfo ->
                val isSys = (appInfo.flags and ApplicationInfo.FLAG_SYSTEM) != 0
                val hasLaunch = pm.getLaunchIntentForPackage(appInfo.packageName) != null
                if (!isSys || hasLaunch) {
                    val label = try { pm.getApplicationLabel(appInfo).toString() } catch (_: Exception) { appInfo.packageName }
                    BatteryExemptAppItem(appInfo.packageName, label, isSys, appInfo)
                } else null
            }.sortedBy { it.label.lowercase() }

            val exempted = items.map { it.packageName }
                .filter { LightspeedWatchdogEngine.isPackageBatteryWhitelisted(context, it) }
                .toSet()

            withContext(Dispatchers.Main) {
                allApps = items
                exemptedPackagesSet = exempted
                isLoading = false
            }
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        val glassVisuals = rememberDeckGlassVisuals(context)
        val backdropVisuals = rememberDeckBackdropVisuals(context)
        val dialogWindow = (LocalView.current.parent as? DialogWindowProvider)?.window
        SideEffect {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                dialogWindow?.addFlags(WindowManager.LayoutParams.FLAG_BLUR_BEHIND)
                val lp = dialogWindow?.attributes
                if (lp != null) {
                    lp.blurBehindRadius = backdropVisuals.blurBehindRadius
                    dialogWindow.attributes = lp
                }
            }
            dialogWindow?.setDimAmount(backdropVisuals.dimAmount)
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 8.dp, vertical = 18.dp),
            contentAlignment = Alignment.Center
        ) {
            Card(
                modifier = Modifier
                    .fillMaxWidth(0.98f)
                    .fillMaxHeight(0.88f)
                    .offset { IntOffset(0, dragOffsetY.value.roundToInt()) }
                    .clip(RoundedCornerShape(glassVisuals.shapeCornerRadius))
                    .background(glassVisuals.backgroundBrush)
                    .border(glassVisuals.borderWidth, glassVisuals.borderBrush, RoundedCornerShape(glassVisuals.shapeCornerRadius)),
                colors = CardDefaults.cardColors(containerColor = Color.Transparent),
                shape = RoundedCornerShape(glassVisuals.shapeCornerRadius)
            ) {
                Box(modifier = Modifier.fillMaxSize()) {
                    if (glassVisuals.showTopGlare) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(160.dp)
                                .background(
                                    Brush.radialGradient(
                                        colors = listOf(
                                            Color.White.copy(alpha = glassVisuals.topGlareAlpha),
                                            Color.White.copy(alpha = glassVisuals.topGlareAlpha * 0.45f),
                                            Color(0xFF80D8FF).copy(alpha = glassVisuals.topGlareAlpha * 0.15f),
                                            Color.Transparent
                                        ),
                                        center = Offset(x = 350f, y = 0f),
                                        radius = 650f
                                    )
                                )
                        )
                    }
                    if (glassVisuals.showBottomCaustic) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(70.dp)
                                .align(Alignment.BottomCenter)
                                .background(
                                    Brush.verticalGradient(
                                        colors = listOf(
                                            Color.Transparent,
                                            Color(0xFF00E5FF).copy(alpha = 0.08f),
                                            Color.White.copy(alpha = 0.14f)
                                        )
                                    )
                                )
                        )
                    }
                    if (glassVisuals.showNoiseGrain) {
                        Canvas(modifier = Modifier.matchParentSize()) {
                            drawRect(
                                brush = GlassNoiseTexture.getBrush(),
                                alpha = glassVisuals.noiseAlpha
                            )
                        }
                    }

                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 12.dp, vertical = 10.dp)
                    ) {
                        // Header Drag Handle & Title Region
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .pointerInput(Unit) {
                                    detectVerticalDragGestures(
                                        onDragStart = { hasCrossedThreshold = false },
                                        onDragEnd = {
                                            if (dragOffsetY.value >= thresholdPx) {
                                                onDismiss()
                                            } else {
                                                coroutineScope.launch {
                                                    dragOffsetY.animateTo(
                                                        0f,
                                                        animationSpec = spring(
                                                            dampingRatio = Spring.DampingRatioMediumBouncy,
                                                            stiffness = Spring.StiffnessMediumLow
                                                        )
                                                    )
                                                }
                                            }
                                        },
                                        onDragCancel = {
                                            coroutineScope.launch { dragOffsetY.animateTo(0f) }
                                        },
                                        onVerticalDrag = { change, dragAmount ->
                                            change.consume()
                                            val current = dragOffsetY.value + dragAmount
                                            val newOffset = if (current <= 0f) 0f
                                            else if (current <= thresholdPx) current
                                            else thresholdPx + (current - thresholdPx) * 0.35f

                                            if (!hasCrossedThreshold && newOffset >= thresholdPx) {
                                                hasCrossedThreshold = true
                                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                                LightspeedHapticEngine.tick(context)
                                            } else if (hasCrossedThreshold && newOffset < thresholdPx) {
                                                hasCrossedThreshold = false
                                            }

                                            coroutineScope.launch {
                                                dragOffsetY.snapTo(newOffset)
                                            }
                                        }
                                    )
                                }
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(top = 2.dp, bottom = 8.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(width = 38.dp, height = 4.dp)
                                        .clip(CircleShape)
                                        .background(Color.White.copy(alpha = 0.35f))
                                )
                            }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Box(
                                        modifier = Modifier
                                            .size(34.dp)
                                            .clip(CircleShape)
                                            .background(Color(0xFF00E5FF).copy(alpha = 0.2f)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            Icons.Default.BatteryChargingFull,
                                            contentDescription = null,
                                            tint = Color(0xFF00E5FF),
                                            modifier = Modifier.size(20.dp)
                                        )
                                    }
                                    Spacer(modifier = Modifier.width(10.dp))
                                    Column {
                                        Text(
                                            "WANT TO EXEMPT MORE APPS?",
                                            fontSize = 13.5.sp,
                                            fontWeight = FontWeight.Black,
                                            color = Color.White,
                                            letterSpacing = 0.5.sp
                                        )
                                        Text(
                                            "Universal Battery & Doze Whitelist Manager",
                                            fontSize = 10.sp,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f)
                                        )
                                    }
                                }

                                IconButton(
                                    onClick = onDismiss,
                                    modifier = Modifier.size(32.dp),
                                    colors = IconButtonDefaults.iconButtonColors(containerColor = Color.White.copy(alpha = 0.08f))
                                ) {
                                    Icon(Icons.Default.Close, contentDescription = "Close", tint = Color.White, modifier = Modifier.size(18.dp))
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        // Filter Chips Row
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            FilterChip(
                                selected = filterMode == 0,
                                onClick = { filterMode = 0 },
                                label = { Text("ALL (${allApps.size})", fontSize = 10.sp, fontWeight = FontWeight.Bold) },
                                modifier = Modifier.weight(1f).height(32.dp),
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.25f),
                                    selectedLabelColor = MaterialTheme.colorScheme.primary,
                                    containerColor = Color.White.copy(alpha = 0.05f),
                                    labelColor = Color.LightGray
                                )
                            )
                            FilterChip(
                                selected = filterMode == 1,
                                onClick = { filterMode = 1 },
                                label = { Text("SAFE (${exemptedPackagesSet.size})", fontSize = 10.sp, fontWeight = FontWeight.Bold) },
                                modifier = Modifier.weight(1f).height(32.dp),
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = successGreen.copy(alpha = 0.25f),
                                    selectedLabelColor = successGreen,
                                    containerColor = Color.White.copy(alpha = 0.05f),
                                    labelColor = Color.LightGray
                                )
                            )
                            FilterChip(
                                selected = filterMode == 2,
                                onClick = { filterMode = 2 },
                                label = { Text("PINNED (${pinnedAppsSet.size})", fontSize = 10.sp, fontWeight = FontWeight.Bold) },
                                modifier = Modifier.weight(1f).height(32.dp),
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = Color(0xFFFFD54F).copy(alpha = 0.25f),
                                    selectedLabelColor = Color(0xFFFFD54F),
                                    containerColor = Color.White.copy(alpha = 0.05f),
                                    labelColor = Color.LightGray
                                )
                            )
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        // Search Bar
                        OutlinedTextField(
                            value = searchQuery,
                            onValueChange = { searchQuery = it },
                            placeholder = { Text("Search apps by name or package...", fontSize = 11.5.sp, color = Color.Gray) },
                            modifier = Modifier.fillMaxWidth().height(48.dp),
                            leadingIcon = {
                                Icon(Icons.Default.Search, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp))
                            },
                            trailingIcon = {
                                if (searchQuery.isNotEmpty()) {
                                    IconButton(onClick = { searchQuery = "" }, modifier = Modifier.size(20.dp)) {
                                        Icon(Icons.Default.Close, contentDescription = "Clear", tint = Color.Gray, modifier = Modifier.size(14.dp))
                                    }
                                }
                            },
                            singleLine = true,
                            shape = RoundedCornerShape(10.dp),
                            textStyle = LocalTextStyle.current.copy(fontSize = 11.5.sp, color = Color.White),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = MaterialTheme.colorScheme.primary,
                                unfocusedBorderColor = Color.White.copy(alpha = 0.15f),
                                focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                                unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f)
                            )
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        // Filtered and Sorted App List
                        val filteredApps = remember(allApps, searchQuery, pinnedAppsSet, exemptedPackagesSet, filterMode) {
                            val baseList = allApps.filter { item ->
                                when (filterMode) {
                                    1 -> exemptedPackagesSet.contains(item.packageName)
                                    2 -> pinnedAppsSet.contains(item.packageName)
                                    else -> true
                                }
                            }.filter { item ->
                                if (searchQuery.isBlank()) true
                                else {
                                    val q = searchQuery.trim().lowercase()
                                    item.label.lowercase().contains(q) || item.packageName.lowercase().contains(q)
                                }
                            }

                            baseList.sortedWith(
                                compareByDescending<BatteryExemptAppItem> { pinnedAppsSet.contains(it.packageName) }
                                    .thenByDescending { exemptedPackagesSet.contains(it.packageName) }
                                    .thenBy { it.label.lowercase() }
                            )
                        }

                        if (isLoading) {
                            Box(modifier = Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                                CircularProgressIndicator(color = MaterialTheme.colorScheme.primary, modifier = Modifier.size(28.dp))
                            }
                        } else if (filteredApps.isEmpty()) {
                            Box(modifier = Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                                Text(
                                    text = if (searchQuery.isBlank()) "No apps found in this filter." else "No matching applications found.",
                                    fontSize = 12.sp,
                                    color = Color.LightGray.copy(alpha = 0.6f)
                                )
                            }
                        } else {
                            LazyColumn(
                                modifier = Modifier.fillMaxWidth().weight(1f),
                                verticalArrangement = Arrangement.spacedBy(8.dp),
                                contentPadding = PaddingValues(bottom = 16.dp)
                            ) {
                                items(
                                    items = filteredApps,
                                    key = { it.packageName }
                                ) { appItem ->
                                    val isPinned = pinnedAppsSet.contains(appItem.packageName)
                                    val isWhitelisted = exemptedPackagesSet.contains(appItem.packageName)

                                    BatteryExemptionAppCard(
                                        context = context,
                                        pm = pm,
                                        appItem = appItem,
                                        isPinned = isPinned,
                                        isWhitelisted = isWhitelisted,
                                        successGreen = successGreen,
                                        cautionAmber = cautionAmber,
                                        coroutineScope = coroutineScope,
                                        onTogglePin = {
                                            val nowPinned = LightspeedPreferences.togglePinnedBatteryExemptApp(context, appItem.packageName)
                                            pinnedAppsSet = LightspeedPreferences.getPinnedBatteryExemptApps(context)
                                            LightspeedHapticEngine.tick(context)
                                            Toast.makeText(
                                                context,
                                                if (nowPinned) "Pinned ${appItem.label} to top" else "Unpinned ${appItem.label}",
                                                Toast.LENGTH_SHORT
                                            ).show()
                                        },
                                        onWhitelistChanged = { newStatus ->
                                            exemptedPackagesSet = if (newStatus) {
                                                exemptedPackagesSet + appItem.packageName
                                            } else {
                                                exemptedPackagesSet - appItem.packageName
                                            }
                                            onExemptionChanged()
                                        }
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
