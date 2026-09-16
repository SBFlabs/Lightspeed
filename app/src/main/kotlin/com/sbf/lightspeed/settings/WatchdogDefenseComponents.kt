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
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import kotlin.math.roundToInt
import androidx.core.graphics.drawable.toBitmap
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
                    if (glassVisuals.showRefractiveRim) {
                        Box(
                            modifier = Modifier
                                .matchParentSize()
                                .padding(2.5.dp)
                                .border(
                                    1.dp,
                                    Brush.linearGradient(
                                        colors = listOf(
                                            Color(0xFF00E5FF).copy(alpha = 0.50f),
                                            Color.White.copy(alpha = 0.65f),
                                            Color(0xFFE040FB).copy(alpha = 0.45f),
                                            Color.Transparent
                                        ),
                                        start = Offset(0f, 0f),
                                        end = Offset(Float.POSITIVE_INFINITY, Float.POSITIVE_INFINITY)
                                    ),
                                    RoundedCornerShape(glassVisuals.shapeCornerRadius - 2.5.dp)
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
                    // Header Drag Region
                    PerimeterDialogHeader(
                        context = context,
                        haptic = haptic,
                        coroutineScope = coroutineScope,
                        dragOffsetY = dragOffsetY,
                        thresholdPx = thresholdPx,
                        onDismiss = onDismiss
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                // Search Bar Filter State
                var searchQuery by rememberSaveable { mutableStateOf("") }
                val listState = rememberLazyListState()

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

                // Scrollable Content
                LazyColumn(
                    state = listState,
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    contentPadding = PaddingValues(bottom = 20.dp)
                ) {
                    // 1. Shizuku Bridge Status Banner
                    item(key = "shizuku_banner") {
                        PerimeterShizukuBanner(
                            isShizukuActive = isShizukuActive,
                            successGreen = successGreen,
                            cautionAmber = cautionAmber
                        )
                    }

                    // 2. Summary Telemetry & Master Sentinel Pulse
                    item(key = "summary_telemetry") {
                        PerimeterTelemetrySummaryCard(
                            context = context,
                            totalCount = thirdPartyServices.size,
                            activeCount = activeCount,
                            shieldedCount = protectedServicesSet.size,
                            pinnedCount = pinnedServicesSet.size,
                            successGreen = successGreen,
                            coroutineScope = coroutineScope,
                            onRefreshTrigger = { refreshTrigger++ },
                            onRefreshNeeded = onRefreshNeeded
                        )
                    }

                    // 3. Search Bar Filter
                    item(key = "search_bar") {
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
                    }

                    // 4. Services List
                    if (filteredServices.isEmpty()) {
                        item(key = "empty_state") {
                            Text(
                                text = if (searchQuery.isBlank()) "No external accessibility services installed." else "No matching services found.",
                                fontSize = 11.5.sp,
                                color = Color.LightGray.copy(alpha = 0.6f),
                                modifier = Modifier.padding(vertical = 8.dp)
                            )
                        }
                    } else {
                        items(
                            items = filteredServices,
                            key = { sInfo ->
                                val sPkg = sInfo.resolveInfo?.serviceInfo?.packageName ?: sInfo.id.substringBefore("/")
                                val sCls = sInfo.resolveInfo?.serviceInfo?.name ?: sInfo.id.substringAfter("/")
                                ComponentName(sPkg, sCls).flattenToString()
                            }
                        ) { sInfo ->
                            val sPkg = sInfo.resolveInfo?.serviceInfo?.packageName ?: sInfo.id.substringBefore("/")
                            val sCls = sInfo.resolveInfo?.serviceInfo?.name ?: sInfo.id.substringAfter("/")
                            val component = ComponentName(sPkg, sCls)
                            val componentId = component.flattenToString()

                            PerimeterServiceCard(
                                context = context,
                                pm = pm,
                                sInfo = sInfo,
                                componentId = componentId,
                                isPinned = pinnedServicesSet.contains(componentId),
                                isServiceEnabled = enabledComponents.contains(component),
                                isProtected = protectedServicesSet.contains(componentId),
                                isBatteryWhitelisted = LightspeedWatchdogEngine.isPackageBatteryWhitelisted(context, sPkg),
                                successGreen = successGreen,
                                cautionAmber = cautionAmber,
                                onTogglePin = {
                                    val nowPinned = LightspeedPreferences.togglePinnedAccessibilityService(context, componentId)
                                    pinnedServicesSet = LightspeedPreferences.getPinnedAccessibilityServices(context)
                                    LightspeedHapticEngine.tick(context)
                                    val sLabel = try {
                                        sInfo.resolveInfo?.loadLabel(pm)?.toString() ?: sPkg
                                    } catch (_: Exception) { sPkg }
                                    Toast.makeText(
                                        context,
                                        if (nowPinned) "Pinned $sLabel to top" else "Unpinned $sLabel",
                                        Toast.LENGTH_SHORT
                                    ).show()
                                },
                                onToggleService = { checked ->
                                    val sLabel = try {
                                        sInfo.resolveInfo?.loadLabel(pm)?.toString() ?: sPkg
                                    } catch (_: Exception) { sPkg }
                                    coroutineScope.launch(Dispatchers.IO) {
                                        val ok = LightspeedWatchdogEngine.toggleAccessibilityService(context, componentId, checked)
                                        withContext(Dispatchers.Main) {
                                            if (ok) {
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
                                onToggleProtected = {
                                    val nowProtected = LightspeedPreferences.togglePerimeterProtectedService(context, componentId)
                                    protectedServicesSet = LightspeedPreferences.getPerimeterProtectedServices(context)
                                    LightspeedHapticEngine.tick(context)
                                    val sLabel = try {
                                        sInfo.resolveInfo?.loadLabel(pm)?.toString() ?: sPkg
                                    } catch (_: Exception) { sPkg }
                                    Toast.makeText(
                                        context,
                                        if (nowProtected) "Shield armed: Sentinel will auto-revive $sLabel" else "Shield disarmed",
                                        Toast.LENGTH_SHORT
                                    ).show()
                                    onRefreshNeeded()
                                },
                                onWhitelistBattery = {
                                    val sLabel = try {
                                        sInfo.resolveInfo?.loadLabel(pm)?.toString() ?: sPkg
                                    } catch (_: Exception) { sPkg }
                                    if (LightspeedWatchdogEngine.isPackageBatteryWhitelisted(context, sPkg)) {
                                        Toast.makeText(context, "$sLabel battery is already unrestricted", Toast.LENGTH_SHORT).show()
                                    } else {
                                        coroutineScope.launch(Dispatchers.IO) {
                                            val ok = LightspeedWatchdogEngine.whitelistBattery(context, sPkg)
                                            withContext(Dispatchers.Main) {
                                                if (ok) {
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

