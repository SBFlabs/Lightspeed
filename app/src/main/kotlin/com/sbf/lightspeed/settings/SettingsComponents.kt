package com.sbf.lightspeed.settings

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sbf.lightspeed.GearPickerActivity
import com.sbf.lightspeed.LightspeedAccessibilityService
import com.sbf.lightspeed.system.LightspeedPreferences
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.ui.draw.scale
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import com.sbf.lightspeed.system.LightspeedHapticEngine
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

@Composable
fun FloatingOverlayContainer(
    title: String,
    onDismiss: () -> Unit,
    headerControl: @Composable (RowScope.() -> Unit) = {},
    content: @Composable () -> Unit
) {
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    val density = LocalDensity.current
    val coroutineScope = rememberCoroutineScope()

    val thresholdPx = with(density) { 90.dp.toPx() }
    val dragOffsetY = remember { Animatable(0f) }
    var hasCrossedThreshold by remember { mutableStateOf(false) }

    val glassBorder = Brush.verticalGradient(
        colors = listOf(
            Color.White.copy(alpha = 0.28f),
            Color.White.copy(alpha = 0.08f),
            Color.White.copy(alpha = 0.03f)
        )
    )

    Card(
        modifier = Modifier
            .fillMaxSize()
            .offset { IntOffset(0, dragOffsetY.value.roundToInt()) }
            .clip(RoundedCornerShape(32.dp))
            .background(
                Brush.radialGradient(
                    colors = listOf(
                        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.88f),
                        MaterialTheme.colorScheme.surface.copy(alpha = 0.94f)
                    ),
                    radius = 1200f
                )
            )
            .border(1.2.dp, glassBorder, RoundedCornerShape(32.dp)),
        colors = CardDefaults.cardColors(containerColor = Color.Transparent),
        shape = RoundedCornerShape(32.dp)
    ) {
        Column(modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp, vertical = 10.dp)) {
            // Scoped Drag Handle + Header Region (drag detection strictly scoped to header/handle)
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .pointerInput(Unit) {
                        detectVerticalDragGestures(
                            onDragStart = {
                                hasCrossedThreshold = false
                            },
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
                                    dragOffsetY.animateTo(
                                        0f,
                                        animationSpec = spring(
                                            dampingRatio = Spring.DampingRatioMediumBouncy,
                                            stiffness = Spring.StiffnessMediumLow
                                        )
                                    )
                                }
                            },
                            onVerticalDrag = { change, dragAmount ->
                                change.consume()
                                val current = dragOffsetY.value + dragAmount
                                val newOffset = if (current <= 0f) {
                                    0f
                                } else if (current <= thresholdPx) {
                                    current
                                } else {
                                    thresholdPx + (current - thresholdPx) * 0.35f
                                }

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

                // Header Row (Title + strictly 2 action buttons)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = title,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        headerControl()
                    }
                }
            }

            HorizontalDivider(color = Color.White.copy(alpha = 0.08f), modifier = Modifier.padding(bottom = 12.dp))
            Box(modifier = Modifier.weight(1f)) {
                content()
            }
        }
    }
}

@Composable
fun CompactAccordionSection(
    title: String,
    isExpanded: Boolean,
    onToggle: () -> Unit,
    icon: (@Composable () -> Unit)? = null,
    headerTrailing: @Composable (() -> Unit)? = null,
    content: @Composable () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f))
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onToggle() }
                    .padding(horizontal = 16.dp, vertical = 14.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (icon != null) {
                    icon()
                    Spacer(modifier = Modifier.width(10.dp))
                }
                Text(
                    text = title,
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp,
                    color = Color.White,
                    modifier = Modifier.weight(1f).padding(end = 8.dp)
                )
                if (headerTrailing != null) {
                    headerTrailing()
                    Spacer(modifier = Modifier.width(6.dp))
                }
                Icon(
                    imageVector = if (isExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(24.dp)
                )
            }
            AnimatedVisibility(visible = isExpanded) {
                Column(modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, bottom = 16.dp)) {
                    content()
                }
            }
        }
    }
}

@Composable
fun CollapsibleSubSection(
    title: String,
    subtitle: String? = null,
    isExpanded: Boolean,
    onToggle: () -> Unit,
    icon: @Composable (() -> Unit)? = null,
    trailingBadge: @Composable (() -> Unit)? = null,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.45f)),
        border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.08f))
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onToggle() }
                    .padding(horizontal = 14.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    if (icon != null) {
                        icon()
                    }
                    Column {
                        Text(
                            text = title,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 13.5.sp,
                            color = Color.White
                        )
                        if (!subtitle.isNullOrBlank()) {
                            Text(
                                text = subtitle,
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.85f)
                            )
                        }
                    }
                }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (trailingBadge != null) {
                        trailingBadge()
                    }
                    Icon(
                        imageVector = if (isExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
            AnimatedVisibility(visible = isExpanded) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 12.dp, end = 12.dp, bottom = 12.dp, top = 4.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    content()
                }
            }
        }
    }
}


@Composable
fun UnifyFlankActionsCard(
    isUnified: Boolean,
    onToggle: (Boolean) -> Unit,
    onInfoClick: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.35f)),
        border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.08f))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                Icons.Default.Link,
                contentDescription = null,
                tint = if (isUnified) MaterialTheme.colorScheme.primary else Color.White.copy(alpha = 0.6f),
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    "Unify Upper & Lower Actions",
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.5.sp,
                    color = Color.White
                )
                Text(
                    if (isUnified) "Single unified gesture set • Dual scrubbers active" else "Independent upper & lower gesture sets",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f)
                )
            }
            IconButton(
                onClick = onInfoClick,
                modifier = Modifier.size(28.dp)
            ) {
                Icon(
                    Icons.Default.Info,
                    contentDescription = "Info",
                    tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.8f),
                    modifier = Modifier.size(18.dp)
                )
            }
            Spacer(modifier = Modifier.width(4.dp))
            Switch(
                checked = isUnified,
                onCheckedChange = { onToggle(it) },
                colors = SwitchDefaults.colors(
                    checkedThumbColor = Color.White,
                    checkedTrackColor = MaterialTheme.colorScheme.primary,
                    checkedBorderColor = Color.Transparent,
                    uncheckedThumbColor = Color.White.copy(alpha = 0.75f),
                    uncheckedTrackColor = Color.White.copy(alpha = 0.12f),
                    uncheckedBorderColor = Color.White.copy(alpha = 0.25f)
                )
            )
        }
    }
}

@Composable
fun DeflectorMasterCard(
    flankName: String,
    isEnabled: Boolean,
    onToggle: (Boolean) -> Unit,
    startupMode: String,
    onStartupModeChange: (String) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isEnabled) MaterialTheme.colorScheme.surface.copy(alpha = 0.45f)
            else MaterialTheme.colorScheme.surface.copy(alpha = 0.2f)
        ),
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            if (isEnabled) MaterialTheme.colorScheme.primary.copy(alpha = 0.35f)
            else Color.White.copy(alpha = 0.08f)
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(
                            if (isEnabled) MaterialTheme.colorScheme.primary.copy(alpha = 0.18f)
                            else Color.White.copy(alpha = 0.08f)
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (isEnabled) Icons.Default.Shield else Icons.Default.Security,
                        contentDescription = null,
                        tint = if (isEnabled) MaterialTheme.colorScheme.primary else Color.White.copy(alpha = 0.5f),
                        modifier = Modifier.size(20.dp)
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            flankName,
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.5.sp,
                            color = Color.White
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(
                                    if (isEnabled) Color(0xFF00E676).copy(alpha = 0.18f)
                                    else Color.White.copy(alpha = 0.1f)
                                )
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Text(
                                if (isEnabled) "ACTIVE" else "OFF",
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Black,
                                color = if (isEnabled) Color(0xFF00E676) else Color.White.copy(alpha = 0.6f),
                                letterSpacing = 0.5.sp
                            )
                        }
                    }
                    Text(
                        if (isEnabled) "Flank touch sensors armed and responsive" else "Touch capture muted along screen edge",
                        fontSize = 11.5.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f)
                    )
                }
                Switch(
                    checked = isEnabled,
                    onCheckedChange = { onToggle(it) },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = Color.White,
                        checkedTrackColor = MaterialTheme.colorScheme.primary,
                        checkedBorderColor = Color.Transparent,
                        uncheckedThumbColor = Color.White.copy(alpha = 0.75f),
                        uncheckedTrackColor = Color.White.copy(alpha = 0.12f),
                        uncheckedBorderColor = Color.White.copy(alpha = 0.25f)
                    )
                )
            }

            // Startup state preference block (Full width description with selectable chips underneath)
            HorizontalDivider(color = Color.White.copy(alpha = 0.08f), thickness = 0.8.dp)

            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    "Startup Default State",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Color.White
                )
                Text(
                    if (startupMode == "standby_by_default")
                        "Starts in Standby (Muted) on service boot. Stays dormant until manually armed."
                    else
                        "Automatically armed and responsive when service boots.",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f),
                    lineHeight = 15.sp
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    val isAlwaysArmed = startupMode != "standby_by_default"
                    FilterChip(
                        selected = isAlwaysArmed,
                        onClick = { onStartupModeChange("always_armed") },
                        label = {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.Center,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("⚡ Armed on Boot", fontSize = 11.5.sp, fontWeight = if (isAlwaysArmed) FontWeight.Bold else FontWeight.Normal)
                            }
                        },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.25f),
                            selectedLabelColor = MaterialTheme.colorScheme.primary
                        ),
                        modifier = Modifier.weight(1f)
                    )
                    FilterChip(
                        selected = !isAlwaysArmed,
                        onClick = { onStartupModeChange("standby_by_default") },
                        label = {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.Center,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("⏸ Off by Default", fontSize = 11.5.sp, fontWeight = if (!isAlwaysArmed) FontWeight.Bold else FontWeight.Normal)
                            }
                        },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.25f),
                            selectedLabelColor = MaterialTheme.colorScheme.primary
                        ),
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
    }
}

@Composable
fun FlightControlDeckCard(
    context: Context,
    prefs: SharedPreferences,
    onStateChanged: () -> Unit = {}
) {
    var isArmed by remember { mutableStateOf(LightspeedPreferences.isMasterFlightArmed(context)) }
    var isNotifEnabled by remember { mutableStateOf(LightspeedPreferences.isFlightNotificationEnabled(context)) }
    var isAutomationDocsExpanded by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.4f)),
        border = androidx.compose.foundation.BorderStroke(1.dp, if (isArmed) MaterialTheme.colorScheme.primary.copy(alpha = 0.35f) else Color(0xFFFF9800).copy(alpha = 0.35f))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Row 1: Master Flight Switch Banner
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(42.dp)
                        .clip(CircleShape)
                        .background(
                            if (isArmed) Color(0xFF00E676).copy(alpha = 0.18f)
                            else Color(0xFFFF9800).copy(alpha = 0.18f)
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (isArmed) Icons.Default.Bolt else Icons.Default.PowerSettingsNew,
                        contentDescription = null,
                        tint = if (isArmed) Color(0xFF00E676) else Color(0xFFFF9800),
                        modifier = Modifier.size(24.dp)
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            "MASTER FLIGHT DECK",
                            fontWeight = FontWeight.Black,
                            fontSize = 14.5.sp,
                            color = Color.White,
                            letterSpacing = 0.5.sp
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(
                                    if (isArmed) Color(0xFF00E676).copy(alpha = 0.2f)
                                    else Color(0xFFFF9800).copy(alpha = 0.2f)
                                )
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Text(
                                if (isArmed) "ARMED" else "STANDBY",
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Black,
                                color = if (isArmed) Color(0xFF00E676) else Color(0xFFFF9800),
                                letterSpacing = 0.5.sp
                            )
                        }
                    }
                    Text(
                        if (isArmed) "All gesture deflectors, cockpit hangars, and telemetry active"
                        else "Flight Standby: All touch overlays and gestures completely detached",
                        fontSize = 11.5.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
                    )
                }
                Switch(
                    checked = isArmed,
                    onCheckedChange = { armed ->
                        isArmed = armed
                        LightspeedPreferences.setMasterFlightArmed(context, armed)
                        LightspeedAccessibilityService.instance?.updateOverlaysVisibility()
                        com.sbf.lightspeed.system.LightspeedFlightNotificationManager.update(context)
                        onStateChanged()
                    },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = Color.White,
                        checkedTrackColor = Color(0xFF00E676),
                        checkedBorderColor = Color.Transparent,
                        uncheckedThumbColor = Color.White.copy(alpha = 0.75f),
                        uncheckedTrackColor = Color.White.copy(alpha = 0.12f),
                        uncheckedBorderColor = Color.White.copy(alpha = 0.25f)
                    )
                )
            }

            HorizontalDivider(color = Color.White.copy(alpha = 0.08f), thickness = 0.8.dp)

            // Row 2: Persistent Flight Control Notification Switch
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.NotificationsActive,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(10.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        "Flight Control Notification in Shade",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                    Text(
                        "Ongoing tactical notification with 1-tap buttons to Arm/Standby and toggle deflectors",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f)
                    )
                }
                Switch(
                    checked = isNotifEnabled,
                    onCheckedChange = { enabled ->
                        isNotifEnabled = enabled
                        LightspeedPreferences.setFlightNotificationEnabled(context, enabled)
                        com.sbf.lightspeed.system.LightspeedFlightNotificationManager.update(context)
                    },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = Color.White,
                        checkedTrackColor = MaterialTheme.colorScheme.primary,
                        checkedBorderColor = Color.Transparent,
                        uncheckedThumbColor = Color.White.copy(alpha = 0.75f),
                        uncheckedTrackColor = Color.White.copy(alpha = 0.12f),
                        uncheckedBorderColor = Color.White.copy(alpha = 0.25f)
                    )
                )
            }

            HorizontalDivider(color = Color.White.copy(alpha = 0.08f), thickness = 0.8.dp)

            // Row 3: Home Screen 1-Tap Shortcut Buttons
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    "HOME SCREEN 1-TAP SHORTCUTS",
                    fontSize = 10.5.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                    letterSpacing = 1.sp
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = { com.sbf.lightspeed.LightspeedToggleActivity.pinMasterToggleShortcut(context) },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp),
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 8.dp)
                    ) {
                        Icon(Icons.Default.AddCircleOutline, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Pin Flight Mode", fontSize = 11.5.sp)
                    }

                    OutlinedButton(
                        onClick = { com.sbf.lightspeed.LightspeedToggleActivity.pinDeflectorsToggleShortcut(context) },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp),
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 8.dp)
                    ) {
                        Icon(Icons.Default.AddCircleOutline, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Pin Deflectors", fontSize = 11.5.sp)
                    }
                }
            }

            HorizontalDivider(color = Color.White.copy(alpha = 0.08f), thickness = 0.8.dp)

            // Row 4: Quick Settings Tiles Notice
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color.White.copy(alpha = 0.04f))
                    .padding(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    Icons.Default.DashboardCustomize,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.secondary,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    "Quick Settings: Two interactive tiles are available in your Android QS shade: 'Lightspeed' (Master Toggle) and 'Deflectors' (Flanks Toggle).",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.85f),
                    lineHeight = 15.sp
                )
            }

            // Row 5: Automation & Tasker / MacroDroid Accordion
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = Color.Black.copy(alpha = 0.25f)),
                border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.06f))
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { isAutomationDocsExpanded = !isAutomationDocsExpanded },
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Default.Code,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                "MacroDroid / Tasker / Termux API",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                        }
                        Icon(
                            if (isAutomationDocsExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                            contentDescription = null,
                            tint = Color.White.copy(alpha = 0.7f),
                            modifier = Modifier.size(18.dp)
                        )
                    }

                    if (isAutomationDocsExpanded) {
                        Spacer(modifier = Modifier.height(10.dp))
                        Text(
                            "Send Broadcast Intents from Tasker, MacroDroid, or ADB shell to control Lightspeed programmatically:",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
                        )
                        Spacer(modifier = Modifier.height(8.dp))

                        val clipboardManager = context.getSystemService(Context.CLIPBOARD_SERVICE) as? android.content.ClipboardManager

                        listOf(
                            Triple("Toggle Master Flight Mode", "com.sbf.lightspeed.action.TOGGLE_FLIGHT_MODE", "am broadcast -a com.sbf.lightspeed.action.TOGGLE_FLIGHT_MODE"),
                            Triple("Toggle All Deflectors", "com.sbf.lightspeed.action.TOGGLE_DEFLECTORS", "am broadcast -a com.sbf.lightspeed.action.TOGGLE_DEFLECTORS"),
                            Triple("Toggle Left Deflector", "com.sbf.lightspeed.action.TOGGLE_LEFT_DEFLECTOR", "am broadcast -a com.sbf.lightspeed.action.TOGGLE_LEFT_DEFLECTOR"),
                            Triple("Toggle Right Deflector", "com.sbf.lightspeed.action.TOGGLE_RIGHT_DEFLECTOR", "am broadcast -a com.sbf.lightspeed.action.TOGGLE_RIGHT_DEFLECTOR")
                        ).forEach { (label, actionStr, adbCmd) ->
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(Color.White.copy(alpha = 0.05f))
                                    .padding(8.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(label, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                                    IconButton(
                                        onClick = {
                                            clipboardManager?.setPrimaryClip(android.content.ClipData.newPlainText("Intent Action", actionStr))
                                            Toast.makeText(context, "Action copied to clipboard", Toast.LENGTH_SHORT).show()
                                        },
                                        modifier = Modifier.size(24.dp)
                                    ) {
                                        Icon(Icons.Default.ContentCopy, contentDescription = "Copy", tint = Color.White.copy(alpha = 0.7f), modifier = Modifier.size(14.dp))
                                    }
                                }
                                Text(
                                    actionStr,
                                    fontSize = 10.sp,
                                    fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                                    color = Color.White.copy(alpha = 0.9f)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun SymmetryCouplingCard(
    context: Context,
    prefs: SharedPreferences,
    onModeChanged: () -> Unit = {},
    onInfoClick: () -> Unit = {}
) {
    var geomMode by remember { mutableStateOf(prefs.getString("pref_symmetry_geometry_mode", "independent") ?: "independent") }
    var gestMode by remember { mutableStateOf(prefs.getString("pref_symmetry_gesture_mode", "independent") ?: "independent") }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.35f)),
        border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.08f))
    ) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Default.SyncAlt, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                }
                Spacer(modifier = Modifier.width(10.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text("Deflector Symmetry & Coupling", fontWeight = FontWeight.Bold, fontSize = 15.sp, color = Color.White)
                    Text("Synchronize wings or maintain bilateral independence", fontSize = 11.5.sp, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f))
                }
                IconButton(
                    onClick = onInfoClick,
                    modifier = Modifier.size(28.dp)
                ) {
                    Icon(
                        Icons.Default.Info,
                        contentDescription = "Info",
                        tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.8f),
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            HorizontalDivider(color = Color.White.copy(alpha = 0.08f))

            // 1. Physical Geometry Switch
            ThreeWayTacticalSelector(
                title = "Physical Geometry (Span, Reach, Offset, Glow)",
                subtitle = when (geomMode) {
                    "right" -> "Right Deflector master — Left Deflector mirrors right geometry"
                    "left" -> "Left Deflector master — Right Deflector mirrors left geometry"
                    else -> "Independent — Each deflector has custom geometry"
                },
                selectedMode = geomMode,
                onSelect = { mode ->
                    geomMode = mode
                    prefs.edit().putString("pref_symmetry_geometry_mode", mode).apply()
                    try {
                        LightspeedAccessibilityService.instance?.reloadPreferences()
                    } catch (_: Exception) {}
                    onModeChanged()
                }
            )

            // 2. Astrogation & Gestures Switch
            ThreeWayTacticalSelector(
                title = "Astrogation & Gestures (Cockpit & Macros)",
                subtitle = when (gestMode) {
                    "right" -> "Right Deflector master — Left Deflector inverts & executes right actions"
                    "left" -> "Left Deflector master — Right Deflector inverts & executes left actions"
                    else -> "Independent — Each deflector has dedicated gesture maps"
                },
                selectedMode = gestMode,
                onSelect = { mode ->
                    gestMode = mode
                    prefs.edit().putString("pref_symmetry_gesture_mode", mode).apply()
                    try {
                        LightspeedAccessibilityService.instance?.reloadPreferences()
                    } catch (_: Exception) {}
                    onModeChanged()
                }
            )
        }
    }
}

@Composable
fun ThreeWayTacticalSelector(
    title: String,
    subtitle: String,
    selectedMode: String,
    onSelect: (String) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(title, fontWeight = FontWeight.SemiBold, fontSize = 13.sp, color = Color.White)
        Text(subtitle, fontSize = 11.sp, color = MaterialTheme.colorScheme.primary.copy(alpha = 0.9f))

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(Color.White.copy(alpha = 0.06f))
                .padding(3.dp),
            horizontalArrangement = Arrangement.spacedBy(3.dp)
        ) {
            val options = listOf(
                "left" to "◂ CLONE LEFT",
                "independent" to "◈ INDEPENDENT",
                "right" to "CLONE RIGHT ▸"
            )

            options.forEach { (modeKey, modeTitle) ->
                val isSelected = selectedMode == modeKey
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(9.dp))
                        .background(if (isSelected) MaterialTheme.colorScheme.primary else Color.Transparent)
                        .clickable { onSelect(modeKey) }
                        .padding(vertical = 8.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = modeTitle,
                        fontSize = 10.sp,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                        color = if (isSelected) MaterialTheme.colorScheme.onPrimary else Color.White.copy(alpha = 0.7f),
                        maxLines = 1
                    )
                }
            }
        }
    }
}

@Composable
fun GestureMappingRow(
    context: Context,
    prefs: SharedPreferences,
    direction: ArrowDirection,
    isHold: Boolean,
    keyResName: String,
    defaultTitle: String,
    options: List<String>,
    labelCache: Map<String, String>,
    showMediaQuickAccess: Boolean = false,
    badgeText: String? = null,
    customLeading: (@Composable () -> Unit)? = null
) {
    val key = remember(keyResName) { resKey(context, keyResName) }
    var currentRawValue by remember { mutableStateOf(prefs.getString(key, "none") ?: "none") }
    var showScrubMenu by remember { mutableStateOf(false) }

    val pickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            currentRawValue = prefs.getString(key, "none") ?: "none"
        }
    }

    val activeLabel = remember(currentRawValue, labelCache[currentRawValue]) {
        if (currentRawValue.startsWith("shortcut:")) {
            val raw = currentRawValue.substringAfter("shortcut:")
            if (raw.contains(";pkg=")) {
                val pkg = raw.substringAfter(";pkg=").substringBefore(";")
                val appLabel = labelCache["app:$pkg"] ?: pkg
                val label = if (raw.contains(";label=")) {
                    val rawL = raw.substringAfter(";label=").substringBefore(";")
                    try { android.net.Uri.decode(rawL) } catch (_: Exception) { rawL }
                } else ""
                if (label.isNotEmpty()) "$appLabel ($label)" else "$appLabel (Pinned)"
            } else if (raw.contains("intent:") || raw.contains("b64uri=")) {
                val appLabel = com.sbf.lightspeed.system.LightspeedShortcutManager.resolveLabel(context, currentRawValue)
                if (appLabel.isNotBlank()) appLabel else "Shortcut Action"
            } else {
                labelCache[currentRawValue] ?: currentRawValue
            }
        } else {
            labelCache[currentRawValue] ?: currentRawValue
        }
    }

    val hasControls = showMediaQuickAccess ||
            (currentRawValue == "system:screen_timeout") ||
            (currentRawValue == "system:media_skip_forward" || currentRawValue == "system:media_skip_backward")

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .wrapContentHeight()
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.3f))
            .clickable {
                if (direction == ArrowDirection.SCRUB) {
                    showScrubMenu = true
                } else {
                    val intent = Intent(context, GearPickerActivity::class.java).apply {
                        putExtra("SINGLE_SELECT_PREF_KEY", key)
                        putExtra("SINGLE_SELECT_TITLE", "$defaultTitle Action")
                    }
                    pickerLauncher.launch(intent)
                }
            }
            .padding(12.dp)
    ) {
        // Line 1 & Line 2: Gesture Tracer Icon / Monospace Badge + Full-Width Title & Subtitle
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (customLeading != null) {
                Box(modifier = Modifier.wrapContentWidth()) {
                    customLeading()
                }
                Spacer(modifier = Modifier.width(10.dp))
            } else if (!badgeText.isNullOrBlank()) {
                Surface(
                    modifier = Modifier.wrapContentWidth(),
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.16f),
                    border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.5f))
                ) {
                    Text(
                        text = badgeText,
                        fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                        maxLines = 1,
                        softWrap = false
                    )
                }
                Spacer(modifier = Modifier.width(10.dp))
            } else {
                GestureTrailTracer(direction, isHold, MaterialTheme.colorScheme.primary, Modifier.size(32.dp))
                Spacer(modifier = Modifier.width(10.dp))
            }
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(start = 2.dp)
            ) {
                Text(
                    text = defaultTitle,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 13.5.sp,
                    color = Color.White,
                    maxLines = 1,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = "Active Map: $activeLabel",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.primary,
                    maxLines = 1,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                )
            }
        }

        // Line 3: Dedicated Action Chips & Dropdowns (No cramping / full horizontal space)
        if (hasControls) {
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 46.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // 1. Quick Media Actions Dropdown Menu (Hardware buttons only)
                if (showMediaQuickAccess) {
                    var showMediaQuickMenu by remember { mutableStateOf(false) }

                    Box {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = if (currentRawValue.startsWith("system:media_")) {
                                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.85f)
                            } else {
                                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f)
                            },
                            modifier = Modifier.clickable { showMediaQuickMenu = true }
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.5.dp)
                            ) {
                                Icon(
                                    Icons.Default.PlayArrow,
                                    contentDescription = "Media Actions",
                                    tint = if (currentRawValue.startsWith("system:media_")) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(13.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = "Media ▾",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (currentRawValue.startsWith("system:media_")) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        DropdownMenu(
                            expanded = showMediaQuickMenu,
                            onDismissRequest = { showMediaQuickMenu = false },
                            modifier = Modifier
                                .background(Color(0xF012141A))
                                .border(1.dp, Color(0x33FFFFFF), RoundedCornerShape(16.dp)),
                            shape = RoundedCornerShape(16.dp),
                            containerColor = Color(0xF012141A)
                        ) {
                            val skipSec = prefs.getInt(com.sbf.lightspeed.system.LightspeedPreferences.KEY_MEDIA_SKIP_SECONDS, 10)
                            val mediaItems = listOf(
                                "system:media_play_pause" to "Play / Pause",
                                "system:media_next" to "Next Track",
                                "system:media_prev" to "Previous Track",
                                "system:media_skip_forward" to "Skip Forward (${skipSec}s)",
                                "system:media_skip_backward" to "Skip Backward (${skipSec}s)",
                                "system:media_scrubber" to "Media Timeline Scrubber (HUD)",
                                "system:media_stop" to "Stop Playback"
                            )

                            mediaItems.forEach { (token, title) ->
                                val isSelected = currentRawValue == token
                                DropdownMenuItem(
                                    modifier = Modifier.heightIn(min = 48.dp),
                                    text = {
                                        Text(
                                            text = if (isSelected) "✓ $title" else title,
                                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                            color = if (isSelected) MaterialTheme.colorScheme.primary else Color.Unspecified
                                        )
                                    },
                                    onClick = {
                                        currentRawValue = token
                                        prefs.edit().putString(key, token).apply()
                                        try { LightspeedAccessibilityService.instance?.reloadPreferences() } catch (_: Exception) {}
                                        showMediaQuickMenu = false
                                    }
                                )
                            }
                        }
                    }
                }

                // 2. HUD Style Selector for Screen Timeout
                if (currentRawValue == "system:screen_timeout") {
                    val hudKey = key.replace("pref_macro_action_", "pref_macro_hud_style_")
                    var hudStyle by remember(currentRawValue) {
                        mutableStateOf(prefs.getString(hudKey, null) ?: prefs.getString("pref_macro_hud_style_default", "canopy_droppod") ?: "canopy_droppod")
                    }
                    var showHudMenu by remember { mutableStateOf(false) }

                    Box {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.7f),
                            modifier = Modifier.clickable { showHudMenu = true }
                        ) {
                            val hudName = when (hudStyle) {
                                "canopy_droppod" -> "Drop-Pod"
                                "cockpit_reticle" -> "Reticle"
                                "edge_blade" -> "Blade"
                                else -> "Drop-Pod"
                            }
                            Text(
                                text = "HUD: $hudName ▾",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSecondaryContainer,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.5.dp)
                            )
                        }
                        DropdownMenu(
                            expanded = showHudMenu,
                            onDismissRequest = { showHudMenu = false },
                            modifier = Modifier
                                .background(Color(0xF012141A))
                                .border(1.dp, Color(0x33FFFFFF), RoundedCornerShape(16.dp)),
                            shape = RoundedCornerShape(16.dp),
                            containerColor = Color(0xF012141A)
                        ) {
                            listOf(
                                "canopy_droppod" to "Tactical Canopy Drop-Pod",
                                "cockpit_reticle" to "Holographic Cockpit Reticle",
                                "edge_blade" to "Dynamic Edge Blade"
                            ).forEach { (styleKey, styleTitle) ->
                                DropdownMenuItem(
                                    modifier = Modifier.heightIn(min = 48.dp),
                                    text = {
                                        Text(
                                            text = if (styleKey == hudStyle) "✓ $styleTitle" else styleTitle,
                                            fontWeight = if (styleKey == hudStyle) FontWeight.Bold else FontWeight.Normal
                                        )
                                    },
                                    onClick = {
                                        hudStyle = styleKey
                                        prefs.edit()
                                            .putString(hudKey, styleKey)
                                            .putString("pref_macro_hud_style_default", styleKey)
                                            .apply()
                                        try { LightspeedAccessibilityService.instance?.reloadPreferences() } catch (_: Exception) {}
                                        showHudMenu = false
                                    }
                                )
                            }
                        }
                    }
                }

                // 3. Skip Duration Selector for Media Skip Actions
                if (currentRawValue == "system:media_skip_forward" || currentRawValue == "system:media_skip_backward") {
                    var currentSkipSec by remember(currentRawValue) {
                        mutableStateOf(prefs.getInt(com.sbf.lightspeed.system.LightspeedPreferences.KEY_MEDIA_SKIP_SECONDS, 10))
                    }
                    var showSkipMenu by remember { mutableStateOf(false) }

                    Box {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.7f),
                            modifier = Modifier.clickable { showSkipMenu = true }
                        ) {
                            Text(
                                text = "Skip: ${currentSkipSec}s ▾",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSecondaryContainer,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.5.dp)
                            )
                        }
                        DropdownMenu(
                            expanded = showSkipMenu,
                            onDismissRequest = { showSkipMenu = false },
                            modifier = Modifier
                                .background(Color(0xF012141A))
                                .border(1.dp, Color(0x33FFFFFF), RoundedCornerShape(16.dp)),
                            shape = RoundedCornerShape(16.dp),
                            containerColor = Color(0xF012141A)
                        ) {
                            listOf(5, 10, 15, 30, 60).forEach { sec ->
                                DropdownMenuItem(
                                    modifier = Modifier.heightIn(min = 48.dp),
                                    text = {
                                        Text(
                                            text = if (sec == currentSkipSec) "✓ ${sec}s Interval" else "${sec}s Interval",
                                            fontWeight = if (sec == currentSkipSec) FontWeight.Bold else FontWeight.Normal
                                        )
                                    },
                                    onClick = {
                                        currentSkipSec = sec
                                        prefs.edit().putInt(com.sbf.lightspeed.system.LightspeedPreferences.KEY_MEDIA_SKIP_SECONDS, sec).apply()
                                        try { LightspeedAccessibilityService.instance?.reloadPreferences() } catch (_: Exception) {}
                                        showSkipMenu = false
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }

        if (direction == ArrowDirection.SCRUB) {
            Box {
                DropdownMenu(
                    expanded = showScrubMenu,
                    onDismissRequest = { showScrubMenu = false },
                    modifier = Modifier
                        .background(Color(0xF012141A))
                        .border(1.dp, Color(0x33FFFFFF), RoundedCornerShape(16.dp)),
                    shape = RoundedCornerShape(16.dp),
                    containerColor = Color(0xF012141A)
                ) {
                    options.forEach { opt ->
                        val optLabel = when (opt) {
                            "none" -> "None"
                            "system:screen_timeout" -> "Screen Timeout (Ship Goes Dark)"
                            "system:volume" -> "Volume (Media Stream)"
                            "system:brightness" -> "Screen Brightness"
                            "system:scroll_to_top" -> "Scroll to Top"
                            else -> labelCache[opt] ?: opt
                        }
                        DropdownMenuItem(
                            modifier = Modifier.heightIn(min = 48.dp),
                            text = { Text(optLabel) },
                            onClick = {
                                currentRawValue = opt
                                prefs.edit().putString(key, opt).apply()
                                try { LightspeedAccessibilityService.instance?.reloadPreferences() } catch (_: Exception) {}
                                showScrubMenu = false
                            }
                        )
                    }
                }
            }
        }
    }
}


@Composable
fun PrefToggleRow(
    title: String,
    subtitle: String = "",
    isChecked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.4f))
            .clickable { onCheckedChange(!isChecked) }
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
            Text(title, fontWeight = FontWeight.SemiBold, fontSize = 15.sp, color = Color.White)
            if (subtitle.isNotEmpty()) {
                Spacer(modifier = Modifier.height(2.dp))
                Text(subtitle, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f))
            }
        }
        Switch(
            checked = isChecked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color.White,
                checkedTrackColor = MaterialTheme.colorScheme.primary,
                checkedBorderColor = Color.Transparent,
                uncheckedThumbColor = Color.White.copy(alpha = 0.75f),
                uncheckedTrackColor = Color.White.copy(alpha = 0.12f),
                uncheckedBorderColor = Color.White.copy(alpha = 0.25f)
            )
        )
    }
}

@Composable
fun PrefToggleRow(
    prefs: SharedPreferences,
    prefKey: String,
    defaultVal: Boolean,
    title: String,
    subtitle: String = "",
    onChanged: ((Boolean) -> Unit)? = null
) {
    var checked by remember(prefKey) { mutableStateOf(prefs.getBoolean(prefKey, defaultVal)) }

    DisposableEffect(prefs, prefKey) {
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            if (key == prefKey) {
                checked = prefs.getBoolean(prefKey, defaultVal)
            }
        }
        prefs.registerOnSharedPreferenceChangeListener(listener)
        onDispose {
            prefs.unregisterOnSharedPreferenceChangeListener(listener)
        }
    }

    PrefToggleRow(
        title = title,
        subtitle = subtitle,
        isChecked = checked,
        onCheckedChange = { newValue ->
            checked = newValue
            prefs.edit().putBoolean(prefKey, newValue).apply()
            try {
                com.sbf.lightspeed.LightspeedAccessibilityService.instance?.reloadPreferences()
            } catch (_: Exception) {}
            onChanged?.invoke(newValue)
        }
    )
}

@Composable
fun PrefToggleRow(
    context: Context,
    prefs: SharedPreferences,
    keyResName: String,
    titleResName: String,
    summaryResName: String,
    defaultTitle: String,
    defaultSummary: String,
    defaultVal: Boolean = false
) {
    val key = remember(keyResName) { resKey(context, keyResName) }
    val title = remember(titleResName) { resStr(context, titleResName, defaultTitle) }
    val summary = remember(summaryResName) { resStr(context, summaryResName, defaultSummary) }

    PrefToggleRow(
        prefs = prefs,
        prefKey = key,
        defaultVal = defaultVal,
        title = title,
        subtitle = summary
    )
}

object SliderPresetManager {
    data class PresetItem(
        val raw: String,
        val label: String?,
        val valueStr: String
    ) {
        val displayTitle: String
            get() = if (!label.isNullOrBlank()) label else valueStr
    }

    fun getPresets(prefs: SharedPreferences, sliderKey: String): List<PresetItem> {
        val raw = prefs.getString("pref_slider_presets_$sliderKey", null) ?: return emptyList()
        return raw.split(";;").mapNotNull { entry ->
            val clean = entry.trim()
            if (clean.isEmpty()) null
            else {
                val parts = clean.split("::")
                if (parts.size >= 2) {
                    PresetItem(clean, parts[0].trim(), parts[1].trim())
                } else {
                    PresetItem(clean, null, clean)
                }
            }
        }
    }

    fun addPreset(prefs: SharedPreferences, sliderKey: String, label: String?, valueStr: String): Boolean {
        val current = getPresets(prefs, sliderKey).toMutableList()
        if (current.any { it.valueStr.trim() == valueStr.trim() }) {
            return false
        }
        val formatted = if (label.isNullOrBlank()) valueStr.trim() else "${label.trim()}::${valueStr.trim()}"
        val newRawList = mutableListOf(formatted)
        newRawList.addAll(current.map { it.raw })
        prefs.edit().putString("pref_slider_presets_$sliderKey", newRawList.joinToString(";;")).apply()
        return true
    }

    fun updatePresetLabel(prefs: SharedPreferences, sliderKey: String, oldItem: PresetItem, newLabel: String) {
        val current = getPresets(prefs, sliderKey).toMutableList()
        val index = current.indexOfFirst { it.raw == oldItem.raw }
        val formatted = if (newLabel.isBlank()) oldItem.valueStr else "${newLabel.trim()}::${oldItem.valueStr}"
        if (index != -1) {
            current[index] = PresetItem(formatted, if (newLabel.isBlank()) null else newLabel.trim(), oldItem.valueStr)
        }
        prefs.edit().putString("pref_slider_presets_$sliderKey", current.map { it.raw }.joinToString(";;")).apply()
    }

    fun removePreset(prefs: SharedPreferences, sliderKey: String, item: PresetItem) {
        val current = getPresets(prefs, sliderKey).toMutableList()
        current.removeAll { it.raw == item.raw }
        prefs.edit().putString("pref_slider_presets_$sliderKey", current.map { it.raw }.joinToString(";;")).apply()
    }
}

@Composable
fun ProfileNamingDialog(
    initialName: String,
    valueStr: String,
    dialogTitle: String = "Name Slider Profile",
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var name by remember { mutableStateOf(initialName) }

    androidx.compose.ui.window.Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = Color(0xFF161B26),
            border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.4f)),
            modifier = Modifier
                .fillMaxWidth(0.95f)
                .wrapContentHeight()
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Bookmark,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                    Text(
                        text = dialogTitle,
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp,
                        color = Color.White
                    )
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Calibrated Value: $valueStr",
                    fontSize = 12.5.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.height(14.dp))
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Profile Label / Context", fontSize = 12.sp) },
                    placeholder = { Text("e.g. Rainy days, Gaming, Desk mode...", fontSize = 12.sp, color = Color.Gray) },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                        unfocusedBorderColor = Color.White.copy(alpha = 0.2f),
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        focusedLabelColor = MaterialTheme.colorScheme.primary,
                        unfocusedLabelColor = Color.LightGray
                    ),
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(16.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(onClick = onDismiss) {
                        Text("Cancel", color = Color.LightGray)
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = { onConfirm(name.trim()) },
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                    ) {
                        Text("Save Profile", color = Color.Black, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    }
                }
            }
        }
    }
}

@Composable
fun SliderCalibrationFlyoutDialog(
    title: String,
    sliderKey: String,
    currentValueStr: String,
    defaultValueStr: String,
    onValueTyped: (String) -> Unit,
    onResetToDefault: () -> Unit,
    onSelectProfile: (String) -> Unit,
    onDismiss: () -> Unit,
    prefs: SharedPreferences,
    context: Context,
    isTapToJumpEnabled: Boolean,
    onTapToJumpChanged: (Boolean) -> Unit
) {
    var presets by remember { mutableStateOf(SliderPresetManager.getPresets(prefs, sliderKey)) }
    var isProfilesExpanded by remember { mutableStateOf(true) }
    var itemToRename by remember { mutableStateOf<SliderPresetManager.PresetItem?>(null) }
    var isAddingNewProfile by remember { mutableStateOf(false) }
    val isCurrentValueAlreadySaved = remember(presets, currentValueStr) {
        presets.any { it.valueStr.trim() == currentValueStr.trim() }
    }

    if (isAddingNewProfile) {
        ProfileNamingDialog(
            initialName = "",
            valueStr = currentValueStr,
            dialogTitle = "Save New Profile",
            onConfirm = { customName ->
                SliderPresetManager.addPreset(prefs, sliderKey, customName, currentValueStr)
                presets = SliderPresetManager.getPresets(prefs, sliderKey)
                isAddingNewProfile = false
            },
            onDismiss = { isAddingNewProfile = false }
        )
    }

    itemToRename?.let { targetItem ->
        ProfileNamingDialog(
            initialName = targetItem.label ?: "",
            valueStr = targetItem.valueStr,
            dialogTitle = "Rename Profile",
            onConfirm = { newName ->
                SliderPresetManager.updatePresetLabel(prefs, sliderKey, targetItem, newName)
                presets = SliderPresetManager.getPresets(prefs, sliderKey)
                itemToRename = null
            },
            onDismiss = { itemToRename = null }
        )
    }

    androidx.compose.ui.window.Dialog(onDismissRequest = onDismiss) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.95f)
                .wrapContentHeight()
                .clip(RoundedCornerShape(22.dp))
                .border(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.35f), RoundedCornerShape(22.dp)),
            shape = RoundedCornerShape(22.dp),
            color = Color(0xF0141822)
        ) {
            Column(
                modifier = Modifier
                    .padding(18.dp)
                    .fillMaxWidth()
            ) {
                // 1. Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Surface(
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.2f),
                            modifier = Modifier.size(32.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Default.Tune,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                        Column {
                            Text(
                                text = title.ifEmpty { "Slider Calibration" },
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.5.sp,
                                color = Color.White,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = "Precision Control & Presets",
                                fontSize = 11.sp,
                                color = Color.Gray
                            )
                        }
                    }

                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier.size(28.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Close",
                            tint = Color.LightGray,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }

                HorizontalDivider(
                    modifier = Modifier.padding(vertical = 12.dp),
                    color = Color.White.copy(alpha = 0.1f)
                )

                // 2. Current & Default Value Chips (with direct numeric editing)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    var directValueText by remember(currentValueStr) { mutableStateOf(currentValueStr.filter { it.isDigit() || it == '.' || it == '-' }) }

                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
                        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.35f)),
                        modifier = Modifier.weight(1f)
                    ) {
                        Column(modifier = Modifier.padding(8.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("Type Value", fontSize = 10.sp, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold)
                                Icon(
                                    imageVector = Icons.Default.Edit,
                                    contentDescription = "Type value",
                                    tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.7f),
                                    modifier = Modifier.size(12.dp)
                                )
                            }
                            Spacer(modifier = Modifier.height(2.dp))
                            BasicTextField(
                                value = directValueText,
                                onValueChange = { newTxt ->
                                    directValueText = newTxt
                                    onValueTyped(newTxt)
                                },
                                textStyle = TextStyle(
                                    color = Color.White,
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Bold
                                ),
                                keyboardOptions = KeyboardOptions(
                                    keyboardType = KeyboardType.Number,
                                    imeAction = ImeAction.Done
                                ),
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }

                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.25f),
                        border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.1f)),
                        modifier = Modifier.weight(1f)
                    ) {
                        Column(modifier = Modifier.padding(8.dp)) {
                            Text("Factory Default", fontSize = 10.sp, color = Color.Gray)
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(defaultValueStr, fontSize = 15.sp, fontWeight = FontWeight.Bold, color = Color.LightGray)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // 3. Toggles Section (Tap to Jump)
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.08f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onTapToJumpChanged(!isTapToJumpEnabled) }
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Tap-to-Jump on Track",
                                fontSize = 12.5.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = Color.White
                            )
                            Text(
                                text = "Instantly snap thumb to tap position on rail",
                                fontSize = 10.5.sp,
                                color = Color.Gray
                            )
                        }
                        Switch(
                            checked = isTapToJumpEnabled,
                            onCheckedChange = onTapToJumpChanged,
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = MaterialTheme.colorScheme.primary,
                                checkedTrackColor = MaterialTheme.colorScheme.primaryContainer
                            ),
                            modifier = Modifier.scale(0.8f)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // 4. Reset to Default Button
                Button(
                    onClick = {
                        LightspeedHapticEngine.heavyClick(context)
                        onResetToDefault()
                    },
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.35f),
                        contentColor = MaterialTheme.colorScheme.error
                    ),
                    border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.4f)),
                    modifier = Modifier.fillMaxWidth().height(40.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.RestartAlt,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Reset to Default ($defaultValueStr)",
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp
                        )
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // 5. User Saved Profiles Dropdown Menu Section
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.25f),
                    border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(10.dp)) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { isProfilesExpanded = !isProfilesExpanded },
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Bookmarks,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(16.dp)
                                )
                                Text(
                                    text = "Saved Profiles (${presets.size})",
                                    fontSize = 12.5.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White
                                )
                            }
                            Icon(
                                imageVector = if (isProfilesExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                                contentDescription = null,
                                tint = Color.LightGray,
                                modifier = Modifier.size(18.dp)
                            )
                        }

                        if (isProfilesExpanded) {
                            Spacer(modifier = Modifier.height(8.dp))

                            // Add Current Profile Row ("Add +")
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = if (isCurrentValueAlreadySaved) Color.White.copy(alpha = 0.06f) else MaterialTheme.colorScheme.primary.copy(alpha = 0.18f),
                                border = androidx.compose.foundation.BorderStroke(
                                    1.dp,
                                    if (isCurrentValueAlreadySaved) Color.White.copy(alpha = 0.1f) else MaterialTheme.colorScheme.primary.copy(alpha = 0.35f)
                                ),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        if (!isCurrentValueAlreadySaved) {
                                            LightspeedHapticEngine.click(context)
                                            isAddingNewProfile = true
                                        } else {
                                            LightspeedHapticEngine.tick(context)
                                        }
                                    }
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 10.dp, vertical = 7.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.Center
                                ) {
                                    Icon(
                                        imageVector = if (isCurrentValueAlreadySaved) Icons.Default.Check else Icons.Default.Add,
                                        contentDescription = "Add",
                                        tint = if (isCurrentValueAlreadySaved) Color.LightGray else MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = if (isCurrentValueAlreadySaved) "Current Value '$currentValueStr' Already Saved" else "Add + (Save '$currentValueStr' as Profile)",
                                        fontSize = 11.5.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = if (isCurrentValueAlreadySaved) Color.LightGray else MaterialTheme.colorScheme.primary
                                    )
                                }
                            }

                            // Profiles List
                            if (presets.isEmpty()) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 8.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = "No saved profiles for this slider yet.",
                                        fontSize = 11.sp,
                                        color = Color.Gray
                                    )
                                }
                            } else {
                                Spacer(modifier = Modifier.height(6.dp))
                                presets.forEach { profileItem ->
                                    Surface(
                                        shape = RoundedCornerShape(8.dp),
                                        color = Color(0x22FFFFFF),
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(vertical = 2.dp)
                                            .clickable {
                                                LightspeedHapticEngine.click(context)
                                                onSelectProfile(profileItem.valueStr)
                                            }
                                    ) {
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(horizontal = 10.dp, vertical = 6.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.SpaceBetween
                                        ) {
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                                modifier = Modifier.weight(1f)
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Default.Bookmark,
                                                    contentDescription = null,
                                                    tint = MaterialTheme.colorScheme.primary,
                                                    modifier = Modifier.size(14.dp)
                                                )
                                                Row(
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                                ) {
                                                    // Value first in bold primary color
                                                    Text(
                                                        text = profileItem.valueStr,
                                                        fontSize = 12.5.sp,
                                                        fontWeight = FontWeight.Bold,
                                                        fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                                                        color = MaterialTheme.colorScheme.primary
                                                    )
                                                    // Followed by Name in distinct tertiary color on the same line
                                                    if (!profileItem.label.isNullOrBlank()) {
                                                        Text(
                                                            text = "• ${profileItem.label}",
                                                            fontSize = 12.sp,
                                                            fontWeight = FontWeight.Medium,
                                                            color = MaterialTheme.colorScheme.tertiary.copy(alpha = 0.95f),
                                                            maxLines = 1,
                                                            overflow = TextOverflow.Ellipsis
                                                        )
                                                    }
                                                }
                                            }

                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                // Pencil Edit Icon for Renaming
                                                IconButton(
                                                    onClick = {
                                                        LightspeedHapticEngine.tick(context)
                                                        itemToRename = profileItem
                                                    },
                                                    modifier = Modifier.size(26.dp)
                                                ) {
                                                    Icon(
                                                        imageVector = Icons.Default.Edit,
                                                        contentDescription = "Rename Profile",
                                                        tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.85f),
                                                        modifier = Modifier.size(14.dp)
                                                    )
                                                }

                                                // Delete Icon at most right edge
                                                IconButton(
                                                    onClick = {
                                                        LightspeedHapticEngine.tick(context)
                                                        SliderPresetManager.removePreset(prefs, sliderKey, profileItem)
                                                        presets = SliderPresetManager.getPresets(prefs, sliderKey)
                                                    },
                                                    modifier = Modifier.size(26.dp)
                                                ) {
                                                    Icon(
                                                        imageVector = Icons.Default.Close,
                                                        contentDescription = "Delete Profile",
                                                        tint = MaterialTheme.colorScheme.error.copy(alpha = 0.8f),
                                                        modifier = Modifier.size(15.dp)
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

@Composable
fun DragOnlySlider(
    value: Float,
    onValueChange: (Float) -> Unit,
    valueRange: ClosedFloatingPointRange<Float> = 0f..1f,
    steps: Int = 0,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    tapToJump: Boolean = false
) {
    val context = LocalContext.current
    val range = valueRange.endInclusive - valueRange.start
    var isDragging by remember { mutableStateOf(false) }
    var dragProgress by remember { mutableFloatStateOf(0f) }
    var lastHapticSteppedVal by remember { mutableFloatStateOf(value) }

    LaunchedEffect(value) {
        if (!isDragging) {
            dragProgress = if (range > 0f) ((value - valueRange.start) / range).coerceIn(0f, 1f) else 0f
            lastHapticSteppedVal = value
        }
    }

    BoxWithConstraints(
        modifier = modifier
            .fillMaxWidth()
            .height(44.dp)
            .pointerInput(enabled, valueRange, steps, tapToJump) {
                if (!enabled || !tapToJump) return@pointerInput
                detectTapGestures(
                    onTap = { offset ->
                        val totalW = size.width.toFloat()
                        val thumbRadiusPx = 14.dp.toPx()
                        val usableWidth = (totalW - thumbRadiusPx * 2f).coerceAtLeast(1f)
                        val clickProgress = ((offset.x - thumbRadiusPx) / usableWidth).coerceIn(0f, 1f)
                        val rawValue = valueRange.start + clickProgress * range
                        val steppedValue = if (steps > 0) {
                            val stepSize = range / (steps + 1)
                            (kotlin.math.round((rawValue - valueRange.start) / stepSize) * stepSize + valueRange.start).coerceIn(valueRange.start, valueRange.endInclusive)
                        } else {
                            rawValue.coerceIn(valueRange.start, valueRange.endInclusive)
                        }
                        if (steppedValue != lastHapticSteppedVal) {
                            LightspeedHapticEngine.scrubTick(context)
                            lastHapticSteppedVal = steppedValue
                        }
                        onValueChange(steppedValue)
                    }
                )
            }
            .pointerInput(enabled, valueRange, steps) {
                if (!enabled) return@pointerInput
                detectHorizontalDragGestures(
                    onDragStart = { _ ->
                        isDragging = true
                        dragProgress = if (range > 0f) ((value - valueRange.start) / range).coerceIn(0f, 1f) else 0f
                        lastHapticSteppedVal = value
                    },
                    onDragEnd = {
                        isDragging = false
                    },
                    onDragCancel = {
                        isDragging = false
                    },
                    onHorizontalDrag = { change, dragAmount ->
                        change.consume()
                        val totalW = size.width.toFloat()
                        val thumbRadiusPx = 14.dp.toPx()
                        val usableWidth = (totalW - thumbRadiusPx * 2f).coerceAtLeast(1f)
                        val deltaProgress = dragAmount / usableWidth
                        dragProgress = (dragProgress + deltaProgress).coerceIn(0f, 1f)
                        val rawValue = valueRange.start + dragProgress * range
                        val steppedValue = if (steps > 0) {
                            val stepSize = range / (steps + 1)
                            (kotlin.math.round((rawValue - valueRange.start) / stepSize) * stepSize + valueRange.start).coerceIn(valueRange.start, valueRange.endInclusive)
                        } else {
                            rawValue.coerceIn(valueRange.start, valueRange.endInclusive)
                        }
                        if (steppedValue != lastHapticSteppedVal) {
                            LightspeedHapticEngine.scrubTick(context)
                            lastHapticSteppedVal = steppedValue
                        }
                        onValueChange(steppedValue)
                    }
                )
            },
        contentAlignment = Alignment.CenterStart
    ) {
        val currentProgress = if (isDragging) dragProgress else {
            if (range > 0f) ((value - valueRange.start) / range).coerceIn(0f, 1f) else 0f
        }

        val primaryColor = MaterialTheme.colorScheme.primary
        val inactiveColor = primaryColor.copy(alpha = 0.18f)

        // 1. Inactive Track (Full Width)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(6.dp)
                .clip(CircleShape)
                .background(inactiveColor)
        )

        // 2. Active Track
        Box(
            modifier = Modifier
                .fillMaxWidth(currentProgress.coerceIn(0.005f, 1f))
                .height(6.dp)
                .clip(CircleShape)
                .background(primaryColor)
        )

        // 3. Ticks
        if (steps in 1..30) {
            val stepFraction = 1f / (steps + 1)
            for (i in 1..steps) {
                val tickPos = i * stepFraction
                Box(
                    modifier = Modifier
                        .fillMaxWidth(tickPos)
                        .wrapContentWidth(Alignment.End)
                ) {
                    Box(
                        modifier = Modifier
                            .size(4.dp)
                            .clip(CircleShape)
                            .background(
                                if (tickPos <= currentProgress) Color.White.copy(alpha = 0.8f)
                                else primaryColor.copy(alpha = 0.4f)
                            )
                    )
                }
            }
        }

        // 4. M3 Liquid-Glass Reactive Thumb Indicator
        val thumbWidth by animateDpAsState(
            targetValue = if (isDragging) 28.dp else 18.dp,
            animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
            label = "thumbWidth"
        )
        val thumbHeight by animateDpAsState(
            targetValue = if (isDragging) 20.dp else 18.dp,
            animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
            label = "thumbHeight"
        )
        val thumbFill by animateColorAsState(
            targetValue = if (isDragging) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.85f),
            label = "thumbFill"
        )
        val thumbBorderColor by animateColorAsState(
            targetValue = if (isDragging) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f),
            label = "thumbBorder"
        )

        Box(
            modifier = Modifier
                .fillMaxWidth(currentProgress.coerceIn(0f, 1f))
                .wrapContentWidth(Alignment.End)
        ) {
            Surface(
                modifier = Modifier
                    .size(width = thumbWidth, height = thumbHeight)
                    .clip(RoundedCornerShape(10.dp)),
                shape = RoundedCornerShape(10.dp),
                color = thumbFill,
                shadowElevation = if (isDragging) 8.dp else 2.dp,
                border = androidx.compose.foundation.BorderStroke(1.5.dp, thumbBorderColor)
            ) {
                if (isDragging) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.20f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Box(
                            modifier = Modifier
                                .size(width = 4.dp, height = 10.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.primary)
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun PrefDottedSliderRow(
    context: Context,
    prefs: SharedPreferences,
    keyResName: String,
    titleResName: String,
    defaultTitle: String,
    minVal: Int,
    maxVal: Int,
    step: Int = 1,
    defaultVal: Int
) {
    val key = remember(keyResName) { resKey(context, keyResName) }
    val title = remember(titleResName) { resStr(context, titleResName, defaultTitle) }
    var value by remember { mutableStateOf(prefs.getInt(key, defaultVal)) }
    val steps = remember(minVal, maxVal, step) { ((maxVal - minVal) / step) - 1 }
    var isFlyoutOpen by remember { mutableStateOf(false) }
    var isTapToJumpEnabled by remember { mutableStateOf(prefs.getBoolean("pref_slider_tap_to_jump_$key", false)) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.4f))
            .padding(14.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(title, fontWeight = FontWeight.SemiBold, fontSize = 15.sp, color = Color.White, modifier = Modifier.weight(1f), maxLines = 1)

            // Interactive Tactile Value Pill
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
                border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.35f)),
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .combinedClickable(
                        onClick = {
                            LightspeedHapticEngine.tick(context)
                            isFlyoutOpen = true
                        },
                        onLongClick = {
                            LightspeedHapticEngine.heavyClick(context)
                            isFlyoutOpen = true
                        }
                    )
            ) {
                Text(
                    text = value.toString(),
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.5.sp,
                    fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                )
            }
        }
        Spacer(modifier = Modifier.height(6.dp))
        DragOnlySlider(
            value = value.toFloat(),
            onValueChange = {
                val near = (it.roundToInt() / step) * step
                value = near.coerceIn(minVal, maxVal)
                prefs.edit().putInt(key, value).apply()
            },
            valueRange = minVal.toFloat()..maxVal.toFloat(),
            steps = steps,
            tapToJump = isTapToJumpEnabled,
            modifier = Modifier.fillMaxWidth()
        )
    }

    if (isFlyoutOpen) {
        SliderCalibrationFlyoutDialog(
            title = title,
            sliderKey = key,
            currentValueStr = value.toString(),
            defaultValueStr = defaultVal.toString(),
            onValueTyped = { typedStr ->
                val parsed = typedStr.filter { it.isDigit() || it == '-' }.toIntOrNull()
                if (parsed != null) {
                    value = parsed.coerceIn(minVal, maxVal)
                    prefs.edit().putInt(key, value).apply()
                }
            },
            onResetToDefault = {
                value = defaultVal
                prefs.edit().putInt(key, defaultVal).apply()
            },
            onSelectProfile = { profileStr ->
                val parsed = profileStr.filter { it.isDigit() || it == '-' }.toIntOrNull()
                if (parsed != null) {
                    value = parsed.coerceIn(minVal, maxVal)
                    prefs.edit().putInt(key, value).apply()
                }
            },
            onDismiss = { isFlyoutOpen = false },
            prefs = prefs,
            context = context,
            isTapToJumpEnabled = isTapToJumpEnabled,
            onTapToJumpChanged = { enabled ->
                isTapToJumpEnabled = enabled
                prefs.edit().putBoolean("pref_slider_tap_to_jump_$key", enabled).apply()
            }
        )
    }
}

fun resKey(context: Context, resourceName: String): String {
    return resourceName
}

fun resStr(context: Context, resourceName: String, fallback: String): String {
    return fallback.ifEmpty { resourceName }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun PrefFloatDottedSliderRow(
    context: Context,
    prefs: SharedPreferences,
    keyResName: String,
    title: String,
    minVal: Float,
    maxVal: Float,
    step: Float = 0.5f,
    defaultVal: Float,
    unit: String = "m/s²",
    onValueChanged: (Float) -> Unit = {}
) {
    val key = remember(keyResName) { resKey(context, keyResName) }
    var value by remember {
        val current = try {
            if (prefs.contains(key)) {
                try {
                    prefs.getFloat(key, defaultVal)
                } catch (_: Exception) {
                    prefs.getInt(key, (defaultVal * 10).toInt()) / 10f
                }
            } else defaultVal
        } catch (_: Exception) { defaultVal }
        mutableFloatStateOf(current)
    }
    val steps = remember(minVal, maxVal, step) { (((maxVal - minVal) / step).roundToInt() - 1).coerceAtLeast(0) }
    var isFlyoutOpen by remember { mutableStateOf(false) }
    var isTapToJumpEnabled by remember { mutableStateOf(prefs.getBoolean("pref_slider_tap_to_jump_$key", false)) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.4f))
            .padding(14.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(title, fontWeight = FontWeight.SemiBold, fontSize = 15.sp, color = Color.White, modifier = Modifier.weight(1f), maxLines = 1)

            // Interactive Tactile Value Pill
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
                border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.35f)),
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .combinedClickable(
                        onClick = {
                            LightspeedHapticEngine.tick(context)
                            isFlyoutOpen = true
                        },
                        onLongClick = {
                            LightspeedHapticEngine.heavyClick(context)
                            isFlyoutOpen = true
                        }
                    )
            ) {
                Text(
                    text = String.format(java.util.Locale.US, "%.1f %s", value, unit),
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.5.sp,
                    fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                )
            }
        }
        Spacer(modifier = Modifier.height(6.dp))
        DragOnlySlider(
            value = value,
            onValueChange = {
                val near = ((it / step).roundToInt() * step).coerceIn(minVal, maxVal)
                value = near
                prefs.edit().putFloat(key, value).apply()
                onValueChanged(value)
            },
            valueRange = minVal..maxVal,
            steps = steps,
            tapToJump = isTapToJumpEnabled,
            modifier = Modifier.fillMaxWidth()
        )
    }

    if (isFlyoutOpen) {
        SliderCalibrationFlyoutDialog(
            title = title,
            sliderKey = key,
            currentValueStr = String.format(java.util.Locale.US, "%.1f %s", value, unit),
            defaultValueStr = String.format(java.util.Locale.US, "%.1f %s", defaultVal, unit),
            onValueTyped = { typedStr ->
                val clean = typedStr.replace(unit, "").trim()
                val parsed = clean.toFloatOrNull()
                if (parsed != null) {
                    value = parsed.coerceIn(minVal, maxVal)
                    prefs.edit().putFloat(key, value).apply()
                    onValueChanged(value)
                }
            },
            onResetToDefault = {
                value = defaultVal
                prefs.edit().putFloat(key, defaultVal).apply()
                onValueChanged(defaultVal)
            },
            onSelectProfile = { profileStr ->
                val parsed = profileStr.split(" ").firstOrNull()?.toFloatOrNull()
                if (parsed != null) {
                    value = parsed.coerceIn(minVal, maxVal)
                    prefs.edit().putFloat(key, value).apply()
                    onValueChanged(value)
                }
            },
            onDismiss = { isFlyoutOpen = false },
            prefs = prefs,
            context = context,
            isTapToJumpEnabled = isTapToJumpEnabled,
            onTapToJumpChanged = { enabled ->
                isTapToJumpEnabled = enabled
                prefs.edit().putBoolean("pref_slider_tap_to_jump_$key", enabled).apply()
            }
        )
    }
}

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun <T> RotaryWheelColumn(
    items: List<T>,
    selectedIndex: Int,
    onItemSelected: (Int, T) -> Unit,
    labelProvider: (T) -> String,
    modifier: Modifier = Modifier
) {
    val itemHeight = 40.dp
    val visibleItems = 3
    val totalHeight = itemHeight * visibleItems
    val initialIndex = selectedIndex.coerceIn(0, (items.size - 1).coerceAtLeast(0))

    val listState = rememberLazyListState(initialFirstVisibleItemIndex = initialIndex)
    val snapFlingBehavior = rememberSnapFlingBehavior(lazyListState = listState)

    val centerIndex by remember {
        derivedStateOf {
            val firstIdx = listState.firstVisibleItemIndex
            val offset = listState.firstVisibleItemScrollOffset
            if (offset > itemHeight.value / 2) (firstIdx + 1).coerceIn(0, items.size - 1)
            else firstIdx.coerceIn(0, items.size - 1)
        }
    }

    LaunchedEffect(listState.isScrollInProgress) {
        if (!listState.isScrollInProgress) {
            val target = centerIndex
            if (target in items.indices && target != selectedIndex) {
                onItemSelected(target, items[target])
            }
        }
    }

    Box(
        modifier = modifier
            .height(totalHeight)
            .clip(RoundedCornerShape(14.dp))
            .background(Color(0xFF10131A))
            .border(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.25f), RoundedCornerShape(14.dp)),
        contentAlignment = Alignment.Center
    ) {
        // Optical Center Selection Lens
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(itemHeight)
                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.16f))
                .border(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.60f), RoundedCornerShape(8.dp))
        )

        androidx.compose.foundation.lazy.LazyColumn(
            state = listState,
            flingBehavior = snapFlingBehavior,
            contentPadding = PaddingValues(vertical = itemHeight),
            modifier = Modifier.fillMaxSize()
        ) {
            itemsIndexed(items) { index, item ->
                val isSelected = (index == centerIndex)
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(itemHeight),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = labelProvider(item),
                        fontSize = if (isSelected) 14.5.sp else 12.sp,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                        color = if (isSelected) MaterialTheme.colorScheme.primary else Color.LightGray.copy(alpha = 0.45f)
                    )
                }
            }
        }
    }
}

@Composable
fun CoreCoolingRotarySchedulePicker(
    selectedDay: Int,
    selectedHour: Int,
    onScheduleChanged: (day: Int, hour: Int) -> Unit
) {
    val daysList = remember {
        listOf(
            Pair(java.util.Calendar.MONDAY, "Monday"),
            Pair(java.util.Calendar.TUESDAY, "Tuesday"),
            Pair(java.util.Calendar.WEDNESDAY, "Wednesday"),
            Pair(java.util.Calendar.THURSDAY, "Thursday"),
            Pair(java.util.Calendar.FRIDAY, "Friday"),
            Pair(java.util.Calendar.SATURDAY, "Saturday"),
            Pair(java.util.Calendar.SUNDAY, "Sunday")
        )
    }

    val hoursList = remember {
        (0..23).map { h ->
            val hourStr = String.format(java.util.Locale.US, "%02d:00 (%s)", h, if (h < 12) if (h == 0) "12 AM" else "$h AM" else if (h == 12) "12 PM" else "${h - 12} PM")
            Pair(h, hourStr)
        }
    }

    val currentDayIndex = remember(selectedDay) {
        val idx = daysList.indexOfFirst { it.first == selectedDay }
        if (idx >= 0) idx else 6 // Default Sunday
    }

    val currentHourIndex = remember(selectedHour) {
        selectedHour.coerceIn(0, 23)
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.35f))
            .border(1.dp, Color.White.copy(alpha = 0.08f), RoundedCornerShape(16.dp))
            .padding(12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            RotaryWheelColumn(
                items = daysList,
                selectedIndex = currentDayIndex,
                onItemSelected = { _, item ->
                    onScheduleChanged(item.first, selectedHour)
                },
                labelProvider = { it.second },
                modifier = Modifier.weight(1f)
            )

            RotaryWheelColumn(
                items = hoursList,
                selectedIndex = currentHourIndex,
                onItemSelected = { _, item ->
                    onScheduleChanged(selectedDay, item.first)
                },
                labelProvider = { it.second },
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
fun Material3ExpressiveLoader() {
    CircularProgressIndicator(
        modifier = Modifier.size(72.dp),
        color = MaterialTheme.colorScheme.primary
    )
}

/**
 * Maintenance Bay styled Accordion with 45° alternating electric-blue and black hazard stripe border.
 */
@Composable
fun HazardAccordionSection(
    title: String,
    isExpanded: Boolean,
    onToggle: () -> Unit,
    subtitle: String? = null,
    headerTrailing: @Composable (() -> Unit)? = null,
    content: @Composable () -> Unit
) {
    val cautionAmber = Color(0xFFFFB300)
    val deepBlack = Color(0xFF0F1115)

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .border(
                1.5.dp,
                Brush.horizontalGradient(
                    listOf(
                        cautionAmber.copy(alpha = 0.85f),
                        deepBlack,
                        cautionAmber.copy(alpha = 0.85f)
                    )
                ),
                RoundedCornerShape(18.dp)
            ),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f))
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            // 45° Alternating Industrial Caution Amber and Deep Black Diagonal Hazard Hatch Stripe
            androidx.compose.foundation.Canvas(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(6.dp)
                    .clip(RoundedCornerShape(topStart = 18.dp, topEnd = 18.dp))
            ) {
                val w = size.width
                val h = size.height
                val stripeWidth = 10.dp.toPx()
                var x = -h
                var isAmber = true
                while (x < w + h) {
                    val path = androidx.compose.ui.graphics.Path().apply {
                        moveTo(x, 0f)
                        lineTo(x + stripeWidth, 0f)
                        lineTo(x + stripeWidth + h, h)
                        lineTo(x + h, h)
                        close()
                    }
                    drawPath(path, if (isAmber) cautionAmber else deepBlack)
                    x += stripeWidth
                    isAmber = !isAmber
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onToggle() }
                    .padding(horizontal = 16.dp, vertical = 14.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(
                    modifier = Modifier.weight(1f).padding(end = 8.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Science,
                            contentDescription = null,
                            tint = cautionAmber,
                            modifier = Modifier.size(18.dp)
                        )
                        Text(
                            text = title,
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp,
                            color = cautionAmber
                        )
                    }
                    if (!subtitle.isNullOrBlank()) {
                        Spacer(modifier = Modifier.height(3.dp))
                        Text(
                            text = subtitle,
                            fontSize = 11.5.sp,
                            color = Color.LightGray.copy(alpha = 0.85f),
                            lineHeight = 15.sp
                        )
                    }
                }
                if (headerTrailing != null) {
                    headerTrailing()
                    Spacer(modifier = Modifier.width(6.dp))
                }
                Icon(
                    imageVector = if (isExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                    contentDescription = null,
                    tint = cautionAmber,
                    modifier = Modifier.size(24.dp)
                )
            }
            AnimatedVisibility(visible = isExpanded) {
                Column(modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, bottom = 16.dp)) {
                    content()
                }
            }
        }
    }
}

/**
 * In-place morphing button with triple-lock safety confirmation before executing reboot.
 */
@Composable
fun CoreCoolingTripleLockButton(
    onExecuteReboot: () -> Unit
) {
    var lockState by remember { mutableIntStateOf(0) } // 0: Idle, 1: Are you sure?, 2: Are you sure sure?

    // 5-second inactivity auto-reset timer
    LaunchedEffect(lockState) {
        if (lockState > 0) {
            kotlinx.coroutines.delay(5000L)
            lockState = 0
        }
    }

    val buttonColor = when (lockState) {
        1 -> Color(0xFFFFB300) // Amber
        2 -> Color(0xFFFF3D00) // Red
        else -> Color(0xFF00E5FF) // Electric Blue
    }

    val buttonText = when (lockState) {
        0 -> "Initiate Core Cooling (Reboot)"
        1 -> "Are you sure? (Tap again)"
        2 -> "Are you sure? (Confirm Reboot)"
        else -> "Initiate Core Cooling"
    }

    Button(
        onClick = {
            when (lockState) {
                0 -> lockState = 1
                1 -> lockState = 2
                2 -> {
                    lockState = 0
                    onExecuteReboot()
                }
            }
        },
        modifier = Modifier
            .fillMaxWidth()
            .height(48.dp),
        shape = RoundedCornerShape(12.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = buttonColor.copy(alpha = if (lockState == 0) 0.18f else 0.85f),
            contentColor = if (lockState == 0) buttonColor else Color.Black
        ),
        border = androidx.compose.foundation.BorderStroke(1.5.dp, buttonColor)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(
                imageVector = if (lockState == 0) Icons.Default.RestartAlt else Icons.Default.Warning,
                contentDescription = null,
                tint = if (lockState == 0) buttonColor else Color.Black,
                modifier = Modifier.size(18.dp)
            )
            Text(
                text = buttonText,
                fontWeight = FontWeight.Bold,
                fontSize = 13.sp
            )
        }
    }
}

/**
 * Power Button gesture mapping row with 7-tap safety interlock for single-press
 * and inline runtime installed tools dropdown.
 */
@Composable
fun PowerGestureMappingRow(
    context: Context,
    prefs: SharedPreferences,
    prefKey: String,
    title: String,
    slot: com.sbf.lightspeed.system.LightspeedKeyEngine.PowerTriggerSlot,
    isSinglePress: Boolean,
    isSinglePressUnlocked: Boolean,
    onSinglePressUnlockStep: () -> Unit,
    installedTools: List<com.sbf.lightspeed.system.TacticalToolItem>,
    options: List<String>,
    labelCache: Map<String, String>,
    onRefreshNeeded: () -> Unit
) {
    var currentValue by remember {
        mutableStateOf(
            if (isSinglePress && !isSinglePressUnlocked) "none"
            else prefs.getString(prefKey, if (slot == com.sbf.lightspeed.system.LightspeedKeyEngine.PowerTriggerSlot.POWER_HOLD) "system:tactical_flyout" else "none") ?: "none"
        )
    }

    LaunchedEffect(isSinglePressUnlocked) {
        currentValue = if (isSinglePress && !isSinglePressUnlocked) "none"
        else prefs.getString(prefKey, if (slot == com.sbf.lightspeed.system.LightspeedKeyEngine.PowerTriggerSlot.POWER_HOLD) "system:tactical_flyout" else "none") ?: "none"
    }

    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            currentValue = prefs.getString(prefKey, "none") ?: "none"
            try { LightspeedAccessibilityService.instance?.reloadPreferences() } catch (_: Exception) {}
            onRefreshNeeded()
        }
    }

    var isToolsDropdownOpen by remember { mutableStateOf(false) }

    val isLocked = isSinglePress && !isSinglePressUnlocked

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .alpha(if (isLocked) 0.6f else 1f)
            .clip(RoundedCornerShape(14.dp))
            .clickable {
                if (isLocked) {
                    onSinglePressUnlockStep()
                } else {
                    val intent = Intent(context, GearPickerActivity::class.java).apply {
                        putExtra("SINGLE_SELECT_PREF_KEY", prefKey)
                        putExtra("SINGLE_SELECT_TITLE", "$title Action")
                    }
                    launcher.launch(intent)
                }
            },
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = if (isLocked) 0.15f else 0.35f),
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            if (isLocked) Color.Gray.copy(alpha = 0.3f) else MaterialTheme.colorScheme.primary.copy(alpha = 0.25f)
        )
    ) {
        Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f).padding(end = 8.dp)
                ) {
                    // Custom Leading Badge
                    Surface(
                        modifier = Modifier.wrapContentWidth(),
                        shape = RoundedCornerShape(8.dp),
                        color = (if (isLocked) Color.Gray else MaterialTheme.colorScheme.primary).copy(alpha = 0.16f),
                        border = androidx.compose.foundation.BorderStroke(
                            1.dp,
                            (if (isLocked) Color.Gray else MaterialTheme.colorScheme.primary).copy(alpha = 0.5f)
                        )
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 7.dp, vertical = 5.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(3.dp)
                        ) {
                            Icon(
                                imageVector = if (isLocked) Icons.Default.Lock else Icons.Default.PowerSettingsNew,
                                contentDescription = null,
                                tint = if (isLocked) Color.LightGray else MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(15.dp)
                            )
                            when (slot) {
                                com.sbf.lightspeed.system.LightspeedKeyEngine.PowerTriggerSlot.POWER_SINGLE_PRESS -> {
                                    Text(
                                        text = "1×",
                                        fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 11.sp,
                                        color = if (isLocked) Color.LightGray else MaterialTheme.colorScheme.primary
                                    )
                                }
                                com.sbf.lightspeed.system.LightspeedKeyEngine.PowerTriggerSlot.POWER_DOUBLE_PRESS -> {
                                    Text(
                                        text = "2×",
                                        fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 11.sp,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                                com.sbf.lightspeed.system.LightspeedKeyEngine.PowerTriggerSlot.POWER_HOLD -> {
                                    Icon(
                                        imageVector = Icons.Default.Timer,
                                        contentDescription = "Hold",
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(13.dp)
                                    )
                                }
                                com.sbf.lightspeed.system.LightspeedKeyEngine.PowerTriggerSlot.POWER_PRESS_THEN_HOLD -> {
                                    Text(
                                        text = "➔",
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                    Icon(
                                        imageVector = Icons.Default.Timer,
                                        contentDescription = "Hold",
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(13.dp)
                                    )
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.width(10.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = title,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 13.sp,
                            color = if (isLocked) Color.LightGray else Color.White
                        )
                        if (isLocked) {
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .wrapContentHeight()
                                    .padding(vertical = 2.dp)
                            ) {
                                Text(
                                    text = "Mapped to Default: System Sleep / Wake",
                                    fontSize = 11.sp,
                                    fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                    overflow = TextOverflow.Visible
                                )
                            }
                            Text(
                                text = "Native OS Interlock (Tap 7× to unlock override)",
                                fontSize = 10.5.sp,
                                color = Color.Gray,
                                maxLines = 1
                            )
                        } else {
                            val displayLabel = labelCache[currentValue] ?: resolveDynamicTokenLabel(context, currentValue)
                            Text(
                                text = displayLabel,
                                fontSize = 11.5.sp,
                                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.85f),
                                maxLines = 1
                            )
                        }
                    }
                }

                if (!isLocked) {
                    // Inline Installed Tools Dropdown Button
                    Box {
                        Surface(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .clickable { isToolsDropdownOpen = true },
                            shape = RoundedCornerShape(8.dp),
                            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                            border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.35f))
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Bolt,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(13.dp)
                                )
                                Text(
                                    text = "Tools",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Icon(
                                    imageVector = Icons.Default.ArrowDropDown,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(14.dp)
                                )
                            }
                        }

                        DropdownMenu(
                            expanded = isToolsDropdownOpen,
                            onDismissRequest = { isToolsDropdownOpen = false },
                            modifier = Modifier
                                .background(Color(0xF012141A))
                                .border(1.dp, Color(0x33FFFFFF), RoundedCornerShape(16.dp)),
                            shape = RoundedCornerShape(16.dp),
                            containerColor = Color(0xF012141A)
                        ) {
                            DropdownMenuItem(
                                modifier = Modifier.heightIn(min = 48.dp),
                                leadingIcon = {
                                    Icon(
                                        imageVector = Icons.Default.PowerSettingsNew,
                                        contentDescription = null,
                                        tint = Color.LightGray,
                                        modifier = Modifier.size(20.dp)
                                    )
                                },
                                text = { Text("None (Native / Sleep)", fontWeight = FontWeight.Normal, fontSize = 12.5.sp, color = Color.LightGray) },
                                onClick = {
                                    isToolsDropdownOpen = false
                                    currentValue = "none"
                                    prefs.edit().putString(prefKey, "none").apply()
                                    try { LightspeedAccessibilityService.instance?.reloadPreferences() } catch (_: Exception) {}
                                    onRefreshNeeded()
                                }
                            )

                            HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp), color = Color(0x33FFFFFF))

                            var currentCategory = ""
                            installedTools.forEach { tool ->
                                if (tool.category != currentCategory) {
                                    currentCategory = tool.category
                                    DropdownMenuItem(
                                        modifier = Modifier.heightIn(min = 36.dp),
                                        text = {
                                            Text(
                                                text = currentCategory.uppercase(java.util.Locale.US),
                                                fontSize = 10.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = MaterialTheme.colorScheme.primary
                                            )
                                        },
                                        onClick = {},
                                        enabled = false
                                    )
                                }
                                val isSelected = (currentValue == tool.token)
                                DropdownMenuItem(
                                    modifier = Modifier.heightIn(min = 48.dp),
                                    leadingIcon = {
                                        val icon = when (tool.token) {
                                            "system:torch", "system:flashlight" -> Icons.Default.FlashlightOn
                                            "system:tactical_audio" -> Icons.Default.Mic
                                            "system:tactical_flyout" -> Icons.Default.Dashboard
                                            LightspeedPreferences.ACTION_CHATGPT -> Icons.Default.AutoAwesome
                                            LightspeedPreferences.ACTION_CLAUDE -> Icons.Default.Psychology
                                            LightspeedPreferences.ACTION_GEMINI -> Icons.Default.Stars
                                            LightspeedPreferences.ACTION_FOLAX -> Icons.Default.Assistant
                                            LightspeedPreferences.ACTION_LENS -> Icons.Default.CenterFocusStrong
                                            LightspeedPreferences.ACTION_QR_SCANNER -> Icons.Default.QrCodeScanner
                                            LightspeedPreferences.ACTION_CAMERA_PHOTO -> Icons.Default.CameraAlt
                                            LightspeedPreferences.ACTION_CAMERA_VIDEO -> Icons.Default.Videocam
                                            else -> Icons.Default.Widgets
                                        }
                                        Icon(
                                            imageVector = icon,
                                            contentDescription = null,
                                            tint = if (isSelected) MaterialTheme.colorScheme.primary else Color.LightGray,
                                            modifier = Modifier.size(20.dp)
                                        )
                                    },
                                    text = {
                                        Text(
                                            text = tool.label,
                                            fontSize = 12.5.sp,
                                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                            color = if (isSelected) MaterialTheme.colorScheme.primary else Color.White
                                        )
                                    },
                                    onClick = {
                                        isToolsDropdownOpen = false
                                        currentValue = tool.token
                                        prefs.edit().putString(prefKey, tool.token).apply()
                                        LightspeedHapticEngine.tick(context)
                                        try { LightspeedAccessibilityService.instance?.reloadPreferences() } catch (_: Exception) {}
                                        onRefreshNeeded()
                                    }
                                )
                            }
                        }
                    }
                }
            }

            if (slot == com.sbf.lightspeed.system.LightspeedKeyEngine.PowerTriggerSlot.POWER_DOUBLE_PRESS && currentValue != "none") {
                val isDoublePressDefault = remember(currentValue) {
                    try {
                        val pm = context.packageManager
                        val intent = Intent(android.provider.MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA_SECURE)
                        val resolve = pm.resolveActivity(intent, android.content.pm.PackageManager.MATCH_DEFAULT_ONLY)
                        resolve?.activityInfo?.packageName == context.packageName
                    } catch (_: Exception) { false }
                }

                if (!isDoublePressDefault) {
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 8.dp),
                        shape = RoundedCornerShape(10.dp),
                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
                        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.35f))
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    val intent = Intent(android.provider.MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA_SECURE).apply {
                                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                    }
                                    try { context.startActivity(intent) } catch (_: Exception) {}
                                }
                                .padding(horizontal = 10.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.Bolt,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Tap to authorize double-press hardware trigger (Select 'Always')",
                                fontSize = 10.5.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }
            }
        }
    }
}
