package com.sbf.lightspeed.settings

import android.content.ComponentName
import android.content.Context
import com.sbf.lightspeed.system.logSwallowed
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
import com.sbf.lightspeed.system.PerimeterPolicy
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.sbf.lightspeed.ui.theme.LightspeedStyle
import com.sbf.lightspeed.ui.theme.LocalDeckTheme

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
    val lsColors = LightspeedStyle.colors
    val coroutineScope = rememberCoroutineScope()
    val cautionAmber = lsColors.accentWarn
    val successGreen = lsColors.accentOk
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

    var policiesMap by remember(refreshTrigger) {
        mutableStateOf(LightspeedPreferences.getPerimeterServicePolicies(context))
    }

    var pinnedServicesSet by remember {
        mutableStateOf(LightspeedPreferences.getPinnedAccessibilityServices(context))
    }

    var showBatteryExemptionDeck by rememberSaveable { mutableStateOf(false) }

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
        val activeSnapshot = LocalDeckTheme.current
        val dialogWindow = (LocalView.current.parent as? DialogWindowProvider)?.window
        SideEffect {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                dialogWindow?.addFlags(WindowManager.LayoutParams.FLAG_BLUR_BEHIND)
                val lp = dialogWindow?.attributes
                if (lp != null) {
                    lp.blurBehindRadius = activeSnapshot.windowBlurRadiusPx.coerceIn(0, 150)
                    dialogWindow.attributes = lp
                }
            }
            dialogWindow?.setDimAmount(activeSnapshot.dimAmount)
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 8.dp, vertical = 18.dp),
            contentAlignment = Alignment.Center
        ) {
            DeckGlassCardSurface(
                modifier = Modifier
                    .fillMaxWidth(0.98f)
                    .fillMaxHeight(0.88f)
                    .offset { IntOffset(0, dragOffsetY.value.roundToInt()) }
            ) {
                Box(modifier = Modifier.fillMaxSize()) {
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
                            onRefreshNeeded = onRefreshNeeded,
                            onOpenExemptionDeck = { showBatteryExemptionDeck = true }
                        )
                    }

                    // 3. Search Bar Filter
                    item(key = "search_bar") {
                        OutlinedTextField(
                            value = searchQuery,
                            onValueChange = { searchQuery = it },
                            placeholder = { Text("Filter accessibility services...", fontSize = 12.sp, color = lsColors.textDisabled) },
                            modifier = Modifier.fillMaxWidth().height(50.dp),
                            leadingIcon = {
                                Icon(Icons.Default.Search, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp))
                            },
                            trailingIcon = {
                                if (searchQuery.isNotEmpty()) {
                                    IconButton(onClick = { searchQuery = "" }, modifier = Modifier.size(20.dp)) {
                                        Icon(Icons.Default.Close, contentDescription = "Clear", tint = lsColors.textDisabled, modifier = Modifier.size(14.dp))
                                    }
                                }
                            },
                            singleLine = true,
                            shape = RoundedCornerShape(10.dp),
                            textStyle = LocalTextStyle.current.copy(fontSize = 12.sp, color = lsColors.textPrimary),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = MaterialTheme.colorScheme.primary,
                                unfocusedBorderColor = lsColors.textPrimary.copy(alpha = 0.15f),
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
                                color = lsColors.textSecondary.copy(alpha = 0.6f),
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
                                policy = policiesMap[componentId] ?: PerimeterPolicy.NORMAL,
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
                                                    if (android.os.Build.VERSION.SDK_INT >= 33) {
                                                        Toast.makeText(context, com.sbf.lightspeed.R.string.restricted_settings_hint, Toast.LENGTH_LONG).show()
                                                    }
                                                    val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).apply {
                                                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                                    }
                                                    context.startActivity(intent)
                                                } catch (e: Exception) { logSwallowed("WatchdogDefenseComponents", "PerimeterServicesDeckDialog:427", e) }
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
                                onToggleBatteryWhitelist = {
                                    val sLabel = try {
                                        sInfo.resolveInfo?.loadLabel(pm)?.toString() ?: sPkg
                                    } catch (_: Exception) { sPkg }
                                    coroutineScope.launch(Dispatchers.IO) {
                                        val currentlyWhitelisted = LightspeedWatchdogEngine.isPackageBatteryWhitelisted(context, sPkg)
                                        val ok = if (currentlyWhitelisted) {
                                            LightspeedWatchdogEngine.removeBatteryWhitelist(context, sPkg)
                                        } else {
                                            LightspeedWatchdogEngine.whitelistBattery(context, sPkg)
                                        }
                                        withContext(Dispatchers.Main) {
                                            if (ok) {
                                                LightspeedHapticEngine.tick(context)
                                                val msg = if (currentlyWhitelisted) "Battery exemption removed for $sLabel" else "Battery whitelist granted for $sLabel"
                                                Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                                                refreshTrigger++
                                            } else {
                                                Toast.makeText(context, "Shizuku required for 1-tap battery whitelist", Toast.LENGTH_SHORT).show()
                                            }
                                            onRefreshNeeded()
                                        }
                                    }
                                },
                                onCyclePolicy = {
                                    val next = LightspeedPreferences.cyclePerimeterPolicy(context, componentId)
                                    policiesMap = LightspeedPreferences.getPerimeterServicePolicies(context)
                                    LightspeedHapticEngine.tick(context)
                                    val sLabel = try {
                                        sInfo.resolveInfo?.loadLabel(pm)?.toString() ?: sPkg
                                    } catch (_: Exception) { sPkg }
                                    val policyName = when (next) {
                                        PerimeterPolicy.ALWAYS_REVIVE -> "ALWAYS REVIVE"
                                        PerimeterPolicy.KEEP_OFF -> "KEEP OFF"
                                        PerimeterPolicy.NORMAL -> "NORMAL (AUTO)"
                                    }
                                    Toast.makeText(context, "$sLabel: $policyName", Toast.LENGTH_SHORT).show()
                                    onRefreshNeeded()
                                },
                                onOpenExemptionDeck = { showBatteryExemptionDeck = true }
                            )
                        }
                    }
                }
            }
        }
    }
}
}

    if (showBatteryExemptionDeck) {
        BatteryExemptionDeckDialog(
            context = context,
            onDismiss = { showBatteryExemptionDeck = false },
            onExemptionChanged = {
                refreshTrigger++
                onRefreshNeeded()
            }
        )
    }
}
