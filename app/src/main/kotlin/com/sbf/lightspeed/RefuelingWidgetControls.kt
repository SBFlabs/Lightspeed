package com.sbf.lightspeed

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun WidgetEngineToolbar(
    widgetCount: Int,
    widgetLayoutMode: String,
    isEditMode: Boolean,
    onToggleLayoutMode: () -> Unit,
    onToggleEditMode: () -> Unit,
    onAddWidget: () -> Unit,
    isLandscape: Boolean = false,
    isStackMemoryEnabled: Boolean = false,
    onToggleStackMemory: () -> Unit = {}
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 2.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Left Cockpit Telemetry Chip
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(6.dp))
                .background(Color(0xFF070A10).copy(alpha = 0.85f))
                .border(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.35f), RoundedCornerShape(6.dp))
                .padding(horizontal = 8.dp, vertical = 4.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(6.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary)
                )
                val modeLabel = if (widgetLayoutMode == "smart_stack") {
                    "STACK // ${widgetCount.toString().padStart(2, '0')}"
                } else {
                    val orientTag = if (isLandscape) "LAND" else "PORT"
                    "GRID [$orientTag] // ${widgetCount.toString().padStart(2, '0')}"
                }
                Text(
                    text = modeLabel,
                    fontSize = 10.sp,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.2.sp,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }

        // Right Cockpit Action Modules (NO [X] EXIT BUTTON)
        Row(
            modifier = Modifier.wrapContentSize(),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Mode Switcher (Stack vs Grid)
            Row(
                modifier = Modifier
                    .height(30.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(Color(0xFF0E131C).copy(alpha = 0.85f))
                    .border(1.dp, Color.White.copy(alpha = 0.15f), RoundedCornerShape(6.dp))
                    .clickable { onToggleLayoutMode() }
                    .padding(horizontal = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Icon(
                    imageVector = if (widgetLayoutMode == "smart_stack") Icons.Default.GridView else Icons.Default.ViewCarousel,
                    contentDescription = "Switch Layout",
                    tint = Color.White.copy(alpha = 0.85f),
                    modifier = Modifier.size(13.dp)
                )
                Text(
                    text = if (widgetLayoutMode == "smart_stack") "GRID" else "STACK",
                    fontSize = 9.5.sp,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    color = Color.White.copy(alpha = 0.85f)
                )
            }

            // Smart Stack Page Memory Toggle [MEM: ON / OFF]
            if (widgetLayoutMode == "smart_stack") {
                Row(
                    modifier = Modifier
                        .height(30.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(
                            if (isStackMemoryEnabled) MaterialTheme.colorScheme.primary.copy(alpha = 0.22f)
                            else Color(0xFF0E131C).copy(alpha = 0.85f)
                        )
                        .border(
                            1.dp,
                            if (isStackMemoryEnabled) MaterialTheme.colorScheme.primary
                            else Color.White.copy(alpha = 0.15f),
                            RoundedCornerShape(6.dp)
                        )
                        .clickable { onToggleStackMemory() }
                        .padding(horizontal = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Memory,
                        contentDescription = "Toggle Stack Memory",
                        tint = if (isStackMemoryEnabled) MaterialTheme.colorScheme.primary else Color.White.copy(alpha = 0.65f),
                        modifier = Modifier.size(13.dp)
                    )
                    Text(
                        text = if (isStackMemoryEnabled) "MEM: ON" else "MEM: OFF",
                        fontSize = 9.5.sp,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                        color = if (isStackMemoryEnabled) MaterialTheme.colorScheme.primary else Color.White.copy(alpha = 0.65f)
                    )
                }
            }

            // Edit / Configure Mode Toggle
            Row(
                modifier = Modifier
                    .height(30.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(
                        if (isEditMode) MaterialTheme.colorScheme.primary.copy(alpha = 0.22f)
                        else Color(0xFF0E131C).copy(alpha = 0.85f)
                    )
                    .border(
                        1.dp,
                        if (isEditMode) MaterialTheme.colorScheme.primary
                        else Color.White.copy(alpha = 0.15f),
                        RoundedCornerShape(6.dp)
                    )
                    .clickable { onToggleEditMode() }
                    .padding(horizontal = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Icon(
                    imageVector = if (isEditMode) Icons.Default.Done else Icons.Default.Tune,
                    contentDescription = "Edit Widgets",
                    tint = if (isEditMode) MaterialTheme.colorScheme.primary else Color.White.copy(alpha = 0.85f),
                    modifier = Modifier.size(13.dp)
                )
                Text(
                    text = if (isEditMode) "LOCK" else "CONFIG",
                    fontSize = 9.5.sp,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    color = if (isEditMode) MaterialTheme.colorScheme.primary else Color.White.copy(alpha = 0.85f)
                )
            }

            // Add Widget Module [+ MODULE]
            Row(
                modifier = Modifier
                    .height(30.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.18f))
                    .border(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.55f), RoundedCornerShape(6.dp))
                    .clickable { onAddWidget() }
                    .padding(horizontal = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(3.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = "Add Widget",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(13.dp)
                )
                Text(
                    text = "MODULE",
                    fontSize = 9.5.sp,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}

/**
 * Integrated Tactical HUD badge for reordering and ejecting widget modules in Edit Mode.
 */
@Composable
internal fun TacticalWidgetEditControls(
    canMoveBack: Boolean,
    canMoveForward: Boolean,
    onMoveBack: () -> Unit,
    onMoveForward: () -> Unit,
    onRemove: () -> Unit,
    onConfigure: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
    isCompact: Boolean = false
) {
    val btnSize = if (isCompact) 20.dp else 26.dp
    val iconSize = if (isCompact) 11.dp else 14.dp

    Row(
        modifier = modifier
            .clip(RoundedCornerShape(6.dp))
            .background(Color(0xFF070B12).copy(alpha = 0.96f))
            .border(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.50f), RoundedCornerShape(6.dp))
            .padding(horizontal = if (isCompact) 3.dp else 5.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(if (isCompact) 2.dp else 4.dp)
    ) {
        if (canMoveBack) {
            Box(
                modifier = Modifier
                    .size(btnSize)
                    .clip(RoundedCornerShape(4.dp))
                    .clickable { onMoveBack() },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Shift Prev",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(iconSize)
                )
            }
        }
        if (canMoveForward) {
            Box(
                modifier = Modifier
                    .size(btnSize)
                    .clip(RoundedCornerShape(4.dp))
                    .clickable { onMoveForward() },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                    contentDescription = "Shift Next",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(iconSize)
                )
            }
        }

        if (onConfigure != null) {
            Box(
                modifier = Modifier
                    .size(btnSize)
                    .clip(RoundedCornerShape(4.dp))
                    .background(Color.White.copy(alpha = 0.08f))
                    .border(0.8.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.40f), RoundedCornerShape(4.dp))
                    .clickable { onConfigure() },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Tune,
                    contentDescription = "Configure Widget",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(iconSize)
                )
            }
        }

        Box(
            modifier = Modifier
                .height(if (isCompact) 12.dp else 14.dp)
                .width(1.dp)
                .background(Color.White.copy(alpha = 0.25f))
        )

        // Eject / Remove Button
        Row(
            modifier = Modifier
                .height(btnSize)
                .clip(RoundedCornerShape(4.dp))
                .background(Color(0xFFFF3B30).copy(alpha = 0.22f))
                .border(0.8.dp, Color(0xFFFF3B30).copy(alpha = 0.6f), RoundedCornerShape(4.dp))
                .clickable { onRemove() }
                .padding(horizontal = if (isCompact) 5.dp else 7.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(3.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Close,
                contentDescription = "Eject",
                tint = Color(0xFFFF453A),
                modifier = Modifier.size(iconSize)
            )
            if (!isCompact) {
                Text(
                    text = "EJECT",
                    fontSize = 9.sp,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFFFF453A)
                )
            }
        }
    }
}

@Composable
fun InfinixStandbyWarningBanner(
    onDismiss: () -> Unit
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 4.dp),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B).copy(alpha = 0.75f)),
        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFFFB300).copy(alpha = 0.5f))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.WarningAmber,
                contentDescription = null,
                tint = Color(0xFFFFB300),
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "OEM Standby Conflict",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
                Text(
                    text = "Infinix XOS Standby Style may conflict with Refueling Bay. Disable in System Settings -> Special Function -> Standby Style.",
                    fontSize = 10.5.sp,
                    color = Color.LightGray.copy(alpha = 0.85f),
                    lineHeight = 13.sp
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "OPEN SETTINGS",
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier
                        .clip(RoundedCornerShape(4.dp))
                        .clickable {
                            try {
                                val intent = android.content.Intent("com.transsion.specialfunction.ACTION_STANDBY").apply {
                                    addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                                }
                                context.startActivity(intent)
                            } catch (_: Exception) {
                                try {
                                    context.startActivity(android.content.Intent(android.provider.Settings.ACTION_SETTINGS).apply {
                                        addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                                    })
                                } catch (_: Exception) {}
                            }
                        }
                        .padding(vertical = 2.dp, horizontal = 4.dp)
                )
            }
            IconButton(
                onClick = onDismiss,
                modifier = Modifier.size(24.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "Dismiss",
                    tint = Color.White.copy(alpha = 0.6f),
                    modifier = Modifier.size(14.dp)
                )
            }
        }
    }
}

@Composable
fun EmptyWidgetSlot(onPickWidget: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .clickable { onPickWidget() }
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = Icons.Default.AddCircleOutline,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(32.dp)
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "[ MOUNT AVIONICS MODULE ]",
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Monospace,
            letterSpacing = 1.2.sp,
            color = Color.White
        )
        Spacer(modifier = Modifier.height(3.dp))
        Text(
            text = "Tap to browse tactical widget catalog",
            fontSize = 11.sp,
            fontFamily = FontFamily.Monospace,
            color = Color.LightGray.copy(alpha = 0.65f)
        )
    }
}
