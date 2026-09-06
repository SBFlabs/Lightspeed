package com.sbf.lightspeed.settings

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.provider.Settings
import android.widget.Toast
import android.accessibilityservice.AccessibilityServiceInfo
import android.os.Build
import android.view.WindowManager
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.material.icons.outlined.Security
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import kotlin.math.roundToInt
import androidx.core.graphics.drawable.toBitmap
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.sbf.lightspeed.system.ElevatedTaskCloser
import com.sbf.lightspeed.system.LightspeedHapticEngine
import com.sbf.lightspeed.system.LightspeedPreferences
import com.sbf.lightspeed.system.LightspeedWatchdogEngine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Perimeter Accessibility Sentinels Deck Dialog:
 * Complete replacement for system accessibility service toggles with sub-100ms privileged switches,
 * autonomous auto-revive shields, and 1-tap battery exemptions.
 */
@Composable
fun PerimeterServicesDeckDialog(
    context: Context,
    prefs: SharedPreferences,
    onDismiss: () -> Unit,
    onRefreshNeeded: () -> Unit = {}
) {
    val coroutineScope = rememberCoroutineScope()
    val cautionAmber = Color(0xFFFFB300)
    val successGreen = Color(0xFF00E676)
    val pm = context.packageManager
    val a11yManager = remember { context.getSystemService(Context.ACCESSIBILITY_SERVICE) as? android.view.accessibility.AccessibilityManager }
    val isShizukuActive = ElevatedTaskCloser.isShizukuActive

    var refreshTrigger by remember { mutableIntStateOf(0) }

    val installedA11y = remember(refreshTrigger) {
        try {
            a11yManager?.installedAccessibilityServiceList ?: emptyList()
        } catch (_: Exception) {
            emptyList()
        }
    }

    val enabledComponents = remember(refreshTrigger) {
        LightspeedWatchdogEngine.getEnabledAccessibilityServices(context)
    }

    var protectedServicesSet by remember {
        mutableStateOf(LightspeedPreferences.getPerimeterProtectedServices(context))
    }

    var pinnedServicesSet by remember {
        mutableStateOf(LightspeedPreferences.getPinnedAccessibilityServices(context))
    }

    val thirdPartyServices = remember(installedA11y) {
        installedA11y.filter { sInfo ->
            val sPkg = sInfo.resolveInfo?.serviceInfo?.packageName ?: sInfo.id.substringBefore("/")
            sPkg != context.packageName
        }
    }

    val activeCount = remember(thirdPartyServices, enabledComponents) {
        thirdPartyServices.count { sInfo ->
            val sPkg = sInfo.resolveInfo?.serviceInfo?.packageName ?: sInfo.id.substringBefore("/")
            val sCls = sInfo.resolveInfo?.serviceInfo?.name ?: sInfo.id.substringAfter("/")
            enabledComponents.contains(ComponentName(sPkg, sCls))
        }
    }

    val haptic = LocalHapticFeedback.current
    val density = LocalDensity.current

    val thresholdPx = with(density) { 90.dp.toPx() }
    val dragOffsetY = remember { Animatable(0f) }
    var hasCrossedThreshold by remember { mutableStateOf(false) }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        val dialogWindow = (LocalView.current.parent as? DialogWindowProvider)?.window
        SideEffect {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                dialogWindow?.addFlags(WindowManager.LayoutParams.FLAG_BLUR_BEHIND)
                dialogWindow?.attributes?.blurBehindRadius = 60
            }
        }

        val glassStyle = remember(LightspeedPreferences.getDeckGlassStyle(context)) {
            LightspeedPreferences.getDeckGlassStyle(context)
        }
        val glassVisuals = DeckGlassTheme.resolve(glassStyle)

        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp, vertical = 20.dp),
            contentAlignment = Alignment.Center
        ) {
            Card(
                modifier = Modifier
                    .fillMaxWidth(0.96f)
                    .fillMaxHeight(0.86f)
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
                                .height(140.dp)
                                .background(
                                    Brush.verticalGradient(
                                        colors = listOf(
                                            Color.White.copy(alpha = glassVisuals.topGlareAlpha),
                                            Color.White.copy(alpha = glassVisuals.topGlareAlpha * 0.35f),
                                            Color.Transparent
                                        )
                                    )
                                )
                        )
                    }
                    if (glassVisuals.innerChamferAlpha > 0f) {
                        Box(
                            modifier = Modifier
                                .matchParentSize()
                                .padding(1.dp)
                                .border(
                                    0.8.dp,
                                    Brush.verticalGradient(
                                        colors = listOf(
                                            Color.White.copy(alpha = glassVisuals.innerChamferAlpha),
                                            Color.Transparent
                                        )
                                    ),
                                    RoundedCornerShape(glassVisuals.shapeCornerRadius - 1.dp)
                                )
                        )
                    }
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 16.dp, vertical = 10.dp)
                    ) {
                    // Header Drag Region (drag gesture scoped to drag handle & header row)
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
                                        coroutineScope.launch {
                                            dragOffsetY.animateTo(0f)
                                        }
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
                        // Sleek Drag Handle Pill
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

                        // Header Row
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
                                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        Icons.Outlined.Security,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                                Spacer(modifier = Modifier.width(10.dp))
                                Column {
                                    Text(
                                        "PERIMETER SENTINELS",
                                        fontSize = 15.sp,
                                        fontWeight = FontWeight.Black,
                                        color = Color.White,
                                        letterSpacing = 0.5.sp
                                    )
                                    Text(
                                        "Universal accessibility manager & privileged controls",
                                        fontSize = 11.sp,
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

                // Scrollable Content
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    // Shizuku Bridge Status Banner
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = if (isShizukuActive) successGreen.copy(alpha = 0.12f) else cautionAmber.copy(alpha = 0.12f)
                        ),
                        border = BorderStroke(
                            1.dp,
                            if (isShizukuActive) successGreen.copy(alpha = 0.4f) else cautionAmber.copy(alpha = 0.4f)
                        )
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = if (isShizukuActive) Icons.Default.CheckCircle else Icons.Default.Info,
                                contentDescription = null,
                                tint = if (isShizukuActive) successGreen else cautionAmber,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Column {
                                Text(
                                    text = if (isShizukuActive) "SHIZUKU PRIVILEGED BRIDGE: ACTIVE" else "SHIZUKU PRIVILEGED BRIDGE: STANDBY",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 11.sp,
                                    color = if (isShizukuActive) successGreen else cautionAmber
                                )
                                Text(
                                    text = if (isShizukuActive) "Sub-100ms instant toggles armed without opening system settings" else "Shizuku standby. Toggles will launch system settings page",
                                    fontSize = 10.sp,
                                    color = Color.LightGray.copy(alpha = 0.75f)
                                )
                            }
                        }
                    }

                    // Summary Telemetry & Master Sentinel Pulse
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)),
                        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.08f))
                    ) {
                        Column(modifier = Modifier.fillMaxWidth().padding(10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Min),
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Surface(
                                    modifier = Modifier.weight(1f).fillMaxHeight(),
                                    shape = RoundedCornerShape(8.dp),
                                    color = Color.White.copy(alpha = 0.05f),
                                    border = BorderStroke(1.dp, Color.White.copy(alpha = 0.12f))
                                ) {
                                    Column(modifier = Modifier.padding(horizontal = 6.dp, vertical = 6.dp), verticalArrangement = Arrangement.Center) {
                                        Text("INSTALLED", fontSize = 8.sp, color = Color.LightGray, fontWeight = FontWeight.SemiBold, maxLines = 1)
                                        Text("${thirdPartyServices.size}", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color.White, maxLines = 1)
                                    }
                                }

                                Surface(
                                    modifier = Modifier.weight(1f).fillMaxHeight(),
                                    shape = RoundedCornerShape(8.dp),
                                    color = successGreen.copy(alpha = 0.12f),
                                    border = BorderStroke(1.dp, successGreen.copy(alpha = 0.35f))
                                ) {
                                    Column(modifier = Modifier.padding(horizontal = 6.dp, vertical = 6.dp), verticalArrangement = Arrangement.Center) {
                                        Text("ACTIVE", fontSize = 8.sp, color = Color.LightGray, fontWeight = FontWeight.SemiBold, maxLines = 1)
                                        Text("$activeCount", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = successGreen, maxLines = 1)
                                    }
                                }

                                Surface(
                                    modifier = Modifier.weight(1f).fillMaxHeight(),
                                    shape = RoundedCornerShape(8.dp),
                                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.35f))
                                ) {
                                    Column(modifier = Modifier.padding(horizontal = 6.dp, vertical = 6.dp), verticalArrangement = Arrangement.Center) {
                                        Text("SHIELDED", fontSize = 8.sp, color = Color.LightGray, fontWeight = FontWeight.SemiBold, maxLines = 1)
                                        Text("${protectedServicesSet.size}", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary, maxLines = 1)
                                    }
                                }

                                Surface(
                                    modifier = Modifier.weight(1f).fillMaxHeight(),
                                    shape = RoundedCornerShape(8.dp),
                                    color = Color(0xFFFFD54F).copy(alpha = 0.12f),
                                    border = BorderStroke(1.dp, Color(0xFFFFD54F).copy(alpha = 0.35f))
                                ) {
                                    Column(modifier = Modifier.padding(horizontal = 6.dp, vertical = 6.dp), verticalArrangement = Arrangement.Center) {
                                        Text("PINNED", fontSize = 8.sp, color = Color.LightGray, fontWeight = FontWeight.SemiBold, maxLines = 1)
                                        Text("${pinnedServicesSet.size}", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color(0xFFFFD54F), maxLines = 1)
                                    }
                                }
                            }

                            OutlinedButton(
                                onClick = {
                                    coroutineScope.launch(Dispatchers.IO) {
                                        val count = LightspeedWatchdogEngine.pulsePerimeterServices(context)
                                        withContext(Dispatchers.Main) {
                                            LightspeedHapticEngine.heavyClick(context)
                                            Toast.makeText(context, "Perimeter pulse: $count sentinels verified & revivified", Toast.LENGTH_SHORT).show()
                                            refreshTrigger++
                                            onRefreshNeeded()
                                        }
                                    }
                                },
                                modifier = Modifier.fillMaxWidth().height(36.dp),
                                shape = RoundedCornerShape(10.dp),
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.6f)),
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp)
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
                                    Icon(Icons.Default.Security, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(15.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Pulse Sentinels (Autonomous Health Check)", fontWeight = FontWeight.Bold, fontSize = 11.sp, color = MaterialTheme.colorScheme.primary, maxLines = 1)
                                }
                            }
                        }
                    }

                    // Search Bar Filter
                    var searchQuery by rememberSaveable { mutableStateOf("") }

                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        placeholder = { Text("Filter accessibility services...", fontSize = 12.sp, color = Color.Gray) },
                        modifier = Modifier.fillMaxWidth().height(50.dp),
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
                        textStyle = LocalTextStyle.current.copy(fontSize = 12.sp, color = Color.White),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = MaterialTheme.colorScheme.primary,
                            unfocusedBorderColor = Color.White.copy(alpha = 0.15f),
                            focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                            unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f)
                        )
                    )

                    // Services List
                    val filteredServices = remember(thirdPartyServices, searchQuery, pinnedServicesSet) {
                        val baseList = if (searchQuery.isBlank()) thirdPartyServices
                        else {
                            val query = searchQuery.trim().lowercase()
                            thirdPartyServices.filter { sInfo ->
                                val sPkg = (sInfo.resolveInfo?.serviceInfo?.packageName ?: sInfo.id.substringBefore("/")).lowercase()
                                val sLabel = try {
                                    sInfo.resolveInfo?.loadLabel(pm)?.toString()?.lowercase() ?: ""
                                } catch (_: Exception) { "" }
                                sPkg.contains(query) || sLabel.contains(query)
                            }
                        }
                        baseList.sortedWith(
                            compareByDescending<AccessibilityServiceInfo> { sInfo ->
                                val sPkg = sInfo.resolveInfo?.serviceInfo?.packageName ?: sInfo.id.substringBefore("/")
                                val sCls = sInfo.resolveInfo?.serviceInfo?.name ?: sInfo.id.substringAfter("/")
                                val compId = ComponentName(sPkg, sCls).flattenToString()
                                pinnedServicesSet.contains(compId)
                            }.thenBy { sInfo ->
                                try {
                                    sInfo.resolveInfo?.loadLabel(pm)?.toString()?.lowercase() ?: ""
                                } catch (_: Exception) { "" }
                            }
                        )
                    }

                    if (filteredServices.isEmpty()) {
                        Text(
                            text = if (searchQuery.isBlank()) "No external accessibility services installed." else "No matching services found.",
                            fontSize = 11.5.sp,
                            color = Color.LightGray.copy(alpha = 0.6f),
                            modifier = Modifier.padding(vertical = 8.dp)
                        )
                    } else {
                        Column(
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.padding(top = 4.dp)
                        ) {
                            filteredServices.forEach { sInfo ->
                                val sPkg = sInfo.resolveInfo?.serviceInfo?.packageName ?: sInfo.id.substringBefore("/")
                                val sCls = sInfo.resolveInfo?.serviceInfo?.name ?: sInfo.id.substringAfter("/")
                                val component = ComponentName(sPkg, sCls)
                                val componentId = component.flattenToString()

                                val sLabel = remember(componentId) {
                                    try {
                                        sInfo.resolveInfo?.loadLabel(pm)?.toString() ?: sPkg
                                    } catch (_: Exception) { sPkg }
                                }

                                val iconBitmap = remember(sPkg) {
                                    try {
                                        val d = sInfo.resolveInfo?.loadIcon(pm) ?: pm.getApplicationIcon(sPkg)
                                        d.toBitmap(width = 64, height = 64).asImageBitmap()
                                    } catch (_: Throwable) {
                                        null
                                    }
                                }

                                var isServiceEnabled by remember(componentId, enabledComponents) {
                                    mutableStateOf(enabledComponents.contains(component))
                                }

                                var isProtected by remember(componentId, protectedServicesSet) {
                                    mutableStateOf(protectedServicesSet.contains(componentId))
                                }

                                var isBatteryWhitelisted by remember(sPkg, refreshTrigger) {
                                    mutableStateOf(LightspeedWatchdogEngine.isPackageBatteryWhitelisted(context, sPkg))
                                }

                                val isPinned = pinnedServicesSet.contains(componentId)

                                Surface(
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(14.dp),
                                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.28f),
                                    border = BorderStroke(
                                        1.dp,
                                        if (isPinned) Color(0xFFFFD54F).copy(alpha = 0.45f)
                                        else if (isServiceEnabled) successGreen.copy(alpha = 0.25f)
                                        else Color.White.copy(alpha = 0.08f)
                                    )
                                ) {
                                    Column(modifier = Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                        // Top row: Icon + Label/Package + Pin Button + Instant Toggle Switch
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            if (iconBitmap != null) {
                                                Image(
                                                    bitmap = iconBitmap,
                                                    contentDescription = null,
                                                    modifier = Modifier.size(34.dp).clip(RoundedCornerShape(8.dp))
                                                )
                                            } else {
                                                Box(
                                                    modifier = Modifier
                                                        .size(34.dp)
                                                        .clip(RoundedCornerShape(8.dp))
                                                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)),
                                                    contentAlignment = Alignment.Center
                                                ) {
                                                    Icon(Icons.Default.Widgets, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                                                }
                                            }

                                            Spacer(modifier = Modifier.width(10.dp))

                                            Column(modifier = Modifier.weight(1f).padding(end = 4.dp)) {
                                                Row(verticalAlignment = Alignment.CenterVertically) {
                                                    if (isPinned) {
                                                        Icon(
                                                            imageVector = Icons.Default.PushPin,
                                                            contentDescription = "Pinned",
                                                            tint = Color(0xFFFFD54F),
                                                            modifier = Modifier.size(12.dp).padding(end = 3.dp)
                                                        )
                                                    }
                                                    Text(
                                                        text = sLabel,
                                                        fontWeight = FontWeight.Bold,
                                                        fontSize = 13.sp,
                                                        color = Color.White,
                                                        maxLines = 1,
                                                        overflow = TextOverflow.Ellipsis
                                                    )
                                                }
                                                Text(
                                                    text = sPkg,
                                                    fontSize = 10.sp,
                                                    color = Color.Gray,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                            }

                                            // 1-Tap Pin To Top Button
                                            IconButton(
                                                onClick = {
                                                    val nowPinned = LightspeedPreferences.togglePinnedAccessibilityService(context, componentId)
                                                    pinnedServicesSet = LightspeedPreferences.getPinnedAccessibilityServices(context)
                                                    LightspeedHapticEngine.tick(context)
                                                    Toast.makeText(
                                                        context,
                                                        if (nowPinned) "Pinned $sLabel to top" else "Unpinned $sLabel",
                                                        Toast.LENGTH_SHORT
                                                    ).show()
                                                },
                                                modifier = Modifier.size(36.dp)
                                            ) {
                                                Icon(
                                                    imageVector = if (isPinned) Icons.Default.PushPin else Icons.Outlined.PushPin,
                                                    contentDescription = if (isPinned) "Unpin from top" else "Pin to top",
                                                    tint = if (isPinned) Color(0xFFFFD54F) else Color.White.copy(alpha = 0.35f),
                                                    modifier = Modifier.size(18.dp)
                                                )
                                            }

                                            Spacer(modifier = Modifier.width(2.dp))

                                            // Sub-100ms Instant Privileged Switch
                                            Switch(
                                                checked = isServiceEnabled,
                                                onCheckedChange = { checked ->
                                                    coroutineScope.launch(Dispatchers.IO) {
                                                        val ok = LightspeedWatchdogEngine.toggleAccessibilityService(context, componentId, checked)
                                                        withContext(Dispatchers.Main) {
                                                            if (ok) {
                                                                isServiceEnabled = checked
                                                                LightspeedHapticEngine.tick(context)
                                                                val act = if (checked) "enabled" else "disabled"
                                                                Toast.makeText(context, "$sLabel $act", Toast.LENGTH_SHORT).show()
                                                                refreshTrigger++
                                                            } else {
                                                                try {
                                                                    val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).apply {
                                                                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                                                    }
                                                                    context.startActivity(intent)
                                                                } catch (_: Exception) {}
                                                            }
                                                            onRefreshNeeded()
                                                        }
                                                    }
                                                },
                                                colors = SwitchDefaults.colors(
                                                    checkedThumbColor = Color.White,
                                                    checkedTrackColor = successGreen,
                                                    uncheckedThumbColor = Color.White.copy(alpha = 0.7f),
                                                    uncheckedTrackColor = Color.White.copy(alpha = 0.12f)
                                                )
                                            )
                                        }

                                        HorizontalDivider(color = Color.White.copy(alpha = 0.06f), thickness = 0.8.dp)

                                        // Bottom Tactical Defense Actions Row
                                        Row(
                                            modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Min),
                                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            // 1. Auto-Revive Shield Guard Toggle
                                            Surface(
                                                modifier = Modifier
                                                    .weight(1f)
                                                    .fillMaxHeight()
                                                    .clickable {
                                                        val nowProtected = LightspeedPreferences.togglePerimeterProtectedService(context, componentId)
                                                        protectedServicesSet = LightspeedPreferences.getPerimeterProtectedServices(context)
                                                        isProtected = nowProtected
                                                        LightspeedHapticEngine.tick(context)
                                                        Toast.makeText(
                                                            context,
                                                            if (nowProtected) "Shield armed: Sentinel will auto-revive $sLabel" else "Shield disarmed",
                                                            Toast.LENGTH_SHORT
                                                        ).show()
                                                        onRefreshNeeded()
                                                    },
                                                shape = RoundedCornerShape(8.dp),
                                                color = if (isProtected) successGreen.copy(alpha = 0.14f) else Color.White.copy(alpha = 0.05f),
                                                border = BorderStroke(1.dp, if (isProtected) successGreen.copy(alpha = 0.45f) else Color.White.copy(alpha = 0.12f))
                                            ) {
                                                Row(
                                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    horizontalArrangement = Arrangement.Center
                                                ) {
                                                    Icon(
                                                        imageVector = if (isProtected) Icons.Default.Shield else Icons.Outlined.Shield,
                                                        contentDescription = null,
                                                        tint = if (isProtected) successGreen else Color.LightGray,
                                                        modifier = Modifier.size(13.dp)
                                                    )
                                                    Spacer(modifier = Modifier.width(5.dp))
                                                    Text(
                                                        text = if (isProtected) "SHIELD: ARMED" else "SHIELD: OFF",
                                                        fontSize = 9.5.sp,
                                                        fontWeight = FontWeight.Bold,
                                                        color = if (isProtected) successGreen else Color.LightGray,
                                                        maxLines = 1,
                                                        softWrap = false
                                                    )
                                                }
                                            }

                                            // 2. Battery Whitelist Pill / 1-Tap Whitelist Button
                                            Surface(
                                                modifier = Modifier
                                                    .weight(1f)
                                                    .fillMaxHeight()
                                                    .clickable {
                                                        if (isBatteryWhitelisted) {
                                                            Toast.makeText(context, "$sLabel battery is already unrestricted", Toast.LENGTH_SHORT).show()
                                                        } else {
                                                            coroutineScope.launch(Dispatchers.IO) {
                                                                val ok = LightspeedWatchdogEngine.whitelistBattery(context, sPkg)
                                                                withContext(Dispatchers.Main) {
                                                                    if (ok) {
                                                                        isBatteryWhitelisted = true
                                                                        LightspeedHapticEngine.tick(context)
                                                                        Toast.makeText(context, "Battery whitelist granted for $sLabel", Toast.LENGTH_SHORT).show()
                                                                        refreshTrigger++
                                                                    } else {
                                                                        Toast.makeText(context, "Shizuku required for 1-tap battery whitelist", Toast.LENGTH_SHORT).show()
                                                                    }
                                                                    onRefreshNeeded()
                                                                }
                                                            }
                                                        }
                                                    },
                                                shape = RoundedCornerShape(8.dp),
                                                color = if (isBatteryWhitelisted) successGreen.copy(alpha = 0.14f) else cautionAmber.copy(alpha = 0.14f),
                                                border = BorderStroke(1.dp, if (isBatteryWhitelisted) successGreen.copy(alpha = 0.45f) else cautionAmber.copy(alpha = 0.45f))
                                            ) {
                                                Row(
                                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    horizontalArrangement = Arrangement.Center
                                                ) {
                                                    Icon(
                                                        imageVector = if (isBatteryWhitelisted) Icons.Default.CheckCircle else Icons.Default.BatteryChargingFull,
                                                        contentDescription = null,
                                                        tint = if (isBatteryWhitelisted) successGreen else cautionAmber,
                                                        modifier = Modifier.size(13.dp)
                                                    )
                                                    Spacer(modifier = Modifier.width(5.dp))
                                                    Text(
                                                        text = if (isBatteryWhitelisted) "BATTERY EXEMPT" else "FIX BATTERY",
                                                        fontSize = 9.5.sp,
                                                        fontWeight = FontWeight.Bold,
                                                        color = if (isBatteryWhitelisted) successGreen else cautionAmber,
                                                        maxLines = 1,
                                                        softWrap = false
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
            }
        }
    }
}
}
}
