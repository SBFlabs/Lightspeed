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
import com.sbf.lightspeed.ui.theme.LightspeedStyle
import com.sbf.lightspeed.ui.theme.LightspeedTokens

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
    val lsColors = LightspeedStyle.colors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 2.dp),
        horizontalArrangement = Arrangement.End,
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Cockpit Action Modules (Laid out Left-to-Right in code so Right-to-Left on screen is: 1. GRID/STACK, 2. CONFIG, 3. MODULE, 4. MEMORY)
        Row(
            modifier = Modifier.wrapContentSize(),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 4th from Right (Leftmost): Smart Stack Page Memory Toggle [MEM: ON / OFF]
            if (widgetLayoutMode == "smart_stack") {
                Row(
                    modifier = Modifier
                        .height(30.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(
                            if (isStackMemoryEnabled) MaterialTheme.colorScheme.primary.copy(alpha = 0.22f)
                            else LightspeedTokens.TacticalSurfaceCard.copy(alpha = 0.85f)
                        )
                        .border(
                            1.dp,
                            if (isStackMemoryEnabled) MaterialTheme.colorScheme.primary
                            else lsColors.textPrimary.copy(alpha = 0.15f),
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
                        tint = if (isStackMemoryEnabled) MaterialTheme.colorScheme.primary else lsColors.textPrimary.copy(alpha = 0.65f),
                        modifier = Modifier.size(13.dp)
                    )
                    Text(
                        text = if (isStackMemoryEnabled) "MEM: ON" else "MEM: OFF",
                        fontSize = 9.5.sp,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                        color = if (isStackMemoryEnabled) MaterialTheme.colorScheme.primary else lsColors.textPrimary.copy(alpha = 0.65f)
                    )
                }
            }

            // 3rd from Right: Edit / Configure Mode Toggle [CONFIG / LOCK]
            Row(
                modifier = Modifier
                    .height(30.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(
                        if (isEditMode) MaterialTheme.colorScheme.primary.copy(alpha = 0.22f)
                        else LightspeedTokens.TacticalSurfaceCard.copy(alpha = 0.85f)
                    )
                    .border(
                        1.dp,
                        if (isEditMode) MaterialTheme.colorScheme.primary
                        else lsColors.textPrimary.copy(alpha = 0.15f),
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
                    tint = if (isEditMode) MaterialTheme.colorScheme.primary else lsColors.textPrimary.copy(alpha = 0.85f),
                    modifier = Modifier.size(13.dp)
                )
                Text(
                    text = if (isEditMode) "LOCK" else "CONFIG",
                    fontSize = 9.5.sp,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    color = if (isEditMode) MaterialTheme.colorScheme.primary else lsColors.textPrimary.copy(alpha = 0.85f)
                )
            }

            // 2nd from Right: Add Widget Module [+ MODULE]
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

            // 1st from Right (Rightmost): Mode Switcher [GRID / STACK]
            Row(
                modifier = Modifier
                    .height(30.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(LightspeedTokens.TacticalSurfaceCard.copy(alpha = 0.85f))
                    .border(1.dp, lsColors.textPrimary.copy(alpha = 0.15f), RoundedCornerShape(6.dp))
                    .clickable { onToggleLayoutMode() }
                    .padding(horizontal = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Icon(
                    imageVector = if (widgetLayoutMode == "smart_stack") Icons.Default.GridView else Icons.Default.ViewCarousel,
                    contentDescription = "Switch Layout",
                    tint = lsColors.textPrimary.copy(alpha = 0.85f),
                    modifier = Modifier.size(13.dp)
                )
                Text(
                    text = if (widgetLayoutMode == "smart_stack") "GRID" else "STACK",
                    fontSize = 9.5.sp,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    color = lsColors.textPrimary.copy(alpha = 0.85f)
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
    val lsColors = LightspeedStyle.colors
    val btnSize = if (isCompact) 20.dp else 26.dp
    val iconSize = if (isCompact) 11.dp else 14.dp

    Row(
        modifier = modifier
            .clip(RoundedCornerShape(6.dp))
            .background(LightspeedTokens.TacticalSurfaceDeepAlt.copy(alpha = 0.96f))
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
                    .background(lsColors.textPrimary.copy(alpha = 0.08f))
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
                .background(lsColors.textPrimary.copy(alpha = 0.25f))
        )

        // Eject / Remove Button
        Row(
            modifier = Modifier
                .height(btnSize)
                .clip(RoundedCornerShape(4.dp))
                .background(LightspeedTokens.StatusDangerAlt.copy(alpha = 0.22f))
                .border(0.8.dp, LightspeedTokens.StatusDangerAlt.copy(alpha = 0.6f), RoundedCornerShape(4.dp))
                .clickable { onRemove() }
                .padding(horizontal = if (isCompact) 5.dp else 7.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(3.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Close,
                contentDescription = "Eject",
                tint = LightspeedTokens.StatusDangerFlash,
                modifier = Modifier.size(iconSize)
            )
            if (!isCompact) {
                Text(
                    text = "EJECT",
                    fontSize = 9.sp,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    color = LightspeedTokens.StatusDangerFlash
                )
            }
        }
    }
}

@Composable
fun EmptyWidgetSlot(onPickWidget: () -> Unit) {
    val lsColors = LightspeedStyle.colors
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
            color = lsColors.textPrimary
        )
        Spacer(modifier = Modifier.height(3.dp))
        Text(
            text = "Tap to browse tactical widget catalog",
            fontSize = 11.sp,
            fontFamily = FontFamily.Monospace,
            color = lsColors.textSecondary.copy(alpha = 0.65f)
        )
    }
}
