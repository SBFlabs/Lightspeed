package com.sbf.lightspeed

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sbf.lightspeed.settings.DeckButton
import com.sbf.lightspeed.settings.deckGlassSurface
import com.sbf.lightspeed.settings.resolveOpticalColors
import com.sbf.lightspeed.system.LightspeedLocaleEngine
import com.sbf.lightspeed.system.lsString
import com.sbf.lightspeed.ui.theme.LightspeedStyle
import com.sbf.lightspeed.ui.theme.LightspeedTokens
import com.sbf.lightspeed.ui.theme.LocalDeckTheme

/**
 * Central-Command-aligned 12-Theme toolbar for switching Refueling Bay Stack/Grid modes,
 * toggling edit controls, mounting widgets, and toggling Smart Stack page memory.
 */
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
    val snapshot = LocalDeckTheme.current
    val opticalColors = snapshot.resolveOpticalColors()
    val cornerRadius = (snapshot.cornerRadiusDp * 0.40f).coerceIn(8f, 14f).dp
    val statusShape = RoundedCornerShape(cornerRadius)
    val countLabel = widgetCount.toString().padStart(2, '0')
    val stackStr = lsString(R.string.ref_bay_mode_stack)
    val gridStr = lsString(R.string.ref_bay_mode_grid)
    val modeLabel = if (widgetLayoutMode == "smart_stack") stackStr else gridStr

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 2.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Left interactive telemetry badge: quick layout mode status & one-tap Stack <-> Grid switch
        Row(
            modifier = Modifier
                .height(30.dp)
                .clip(statusShape)
                .background(
                    Brush.verticalGradient(
                        listOf(
                            opticalColors.panelColor.copy(alpha = 0.72f),
                            opticalColors.panelColor.copy(alpha = 0.52f)
                        )
                    )
                )
                .border(1.dp, opticalColors.borderColor.copy(alpha = 0.36f), statusShape)
                .clickable { onToggleLayoutMode() }
                .padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Icon(
                imageVector = if (widgetLayoutMode == "smart_stack") Icons.Default.ViewCarousel else Icons.Default.GridView,
                contentDescription = "Active Layout",
                tint = opticalColors.readableTintColor,
                modifier = Modifier.size(12.dp)
            )
            Text(
                text = "$modeLabel · $countLabel",
                fontSize = 9.5.sp,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold,
                color = Color.White.copy(alpha = 0.90f),
                maxLines = 1,
                softWrap = false
            )
        }

        // Right Central-Command DeckButtons
        Row(
            modifier = Modifier.wrapContentSize(),
            horizontalArrangement = Arrangement.spacedBy(5.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (widgetLayoutMode == "smart_stack") {
                ToolbarDeckPillButton(
                    icon = Icons.Default.Memory,
                    label = if (isStackMemoryEnabled) lsString(R.string.ref_bay_mem_on) else lsString(R.string.ref_bay_mem_off),
                    isActive = isStackMemoryEnabled,
                    onClick = onToggleStackMemory
                )
            }

            ToolbarDeckPillButton(
                icon = if (isEditMode) Icons.Default.Done else Icons.Default.Tune,
                label = if (isEditMode) lsString(R.string.ref_bay_btn_done) else lsString(R.string.ref_bay_btn_edit),
                isActive = isEditMode,
                onClick = onToggleEditMode
            )

            ToolbarDeckPillButton(
                icon = Icons.Default.Add,
                label = lsString(R.string.ref_bay_btn_add),
                isActive = true,
                onClick = onAddWidget
            )

            ToolbarDeckPillButton(
                icon = if (widgetLayoutMode == "smart_stack") Icons.Default.GridView else Icons.Default.ViewCarousel,
                label = if (widgetLayoutMode == "smart_stack") gridStr else stackStr,
                isActive = false,
                onClick = onToggleLayoutMode
            )
        }
    }
}

@Composable
private fun ToolbarDeckPillButton(
    icon: ImageVector,
    label: String,
    isActive: Boolean,
    onClick: () -> Unit
) {
    val snapshot = LocalDeckTheme.current
    val opticalColors = snapshot.resolveOpticalColors()
    val tint = if (isActive) opticalColors.readableTintColor else Color.White.copy(alpha = 0.90f)
    DeckButton(
        onClick = onClick,
        isActive = isActive,
        snapshot = snapshot,
        modifier = Modifier.height(30.dp),
        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = tint,
            modifier = Modifier.size(12.dp)
        )
        Spacer(modifier = Modifier.width(4.dp))
        Text(
            text = label,
            fontSize = 9.5.sp,
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.Bold,
            color = tint,
            maxLines = 1,
            softWrap = false
        )
    }
}

/**
 * Integrated 12-Theme glass pill for reordering, configuring, and removing widget panels in Edit Mode.
 */
@Composable
internal fun TacticalWidgetEditControls(
    canMoveBack: Boolean,
    canMoveForward: Boolean,
    onMoveBack: () -> Unit,
    onMoveForward: () -> Unit,
    onRemove: () -> Unit,
    onConfigure: (() -> Unit)? = null,
    onTune: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
    isCompact: Boolean = false
) {
    val snapshot = LocalDeckTheme.current
    val opticalColors = snapshot.resolveOpticalColors()
    val pillCorner = (snapshot.cornerRadiusDp * 0.42f).coerceIn(8f, 14f).dp
    val pillShape = RoundedCornerShape(pillCorner)
    val btnSize = if (isCompact) 22.dp else 26.dp
    val iconSize = if (isCompact) 12.dp else 14.dp

    Row(
        modifier = modifier
            .clip(pillShape)
            .deckGlassSurface(
                snapshot = snapshot,
                isBlurAvailable = false,
                isCard = true,
                withNoise = false,
                shapeOverride = pillShape
            )
            .padding(horizontal = if (isCompact) 4.dp else 6.dp, vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(if (isCompact) 3.dp else 4.dp)
    ) {
        if (canMoveBack) {
            CompactGlassActionIcon(
                icon = Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = "Move Earlier",
                tint = opticalColors.readableTintColor,
                size = btnSize,
                iconSize = iconSize,
                onClick = onMoveBack
            )
        }
        if (canMoveForward) {
            CompactGlassActionIcon(
                icon = Icons.AutoMirrored.Filled.ArrowForward,
                contentDescription = "Move Later",
                tint = opticalColors.readableTintColor,
                size = btnSize,
                iconSize = iconSize,
                onClick = onMoveForward
            )
        }
        if (onConfigure != null) {
            CompactGlassActionIcon(
                icon = Icons.Default.Edit,
                contentDescription = "Configure Widget",
                tint = opticalColors.readableTintColor,
                size = btnSize,
                iconSize = iconSize,
                onClick = onConfigure
            )
        }
        if (onTune != null) {
            CompactGlassActionIcon(
                icon = Icons.Default.Tune,
                contentDescription = "Tune Widget Layout",
                tint = opticalColors.readableTintColor,
                size = btnSize,
                iconSize = iconSize,
                onClick = onTune
            )
        }

        Box(
            modifier = Modifier
                .height(if (isCompact) 12.dp else 14.dp)
                .width(1.dp)
                .background(opticalColors.borderColor.copy(alpha = 0.35f))
        )

        val removeCorner = (snapshot.cornerRadiusDp * 0.28f).coerceIn(5f, 9f).dp
        val removeShape = RoundedCornerShape(removeCorner)
        Surface(
            modifier = Modifier
                .size(btnSize)
                .clip(removeShape)
                .clickable { onRemove() },
            shape = removeShape,
            color = MaterialTheme.colorScheme.error.copy(alpha = 0.26f),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.72f))
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "Remove Widget",
                    tint = LightspeedTokens.StatusDangerFlash,
                    modifier = Modifier.size(iconSize)
                )
            }
        }
    }
}

@Composable
private fun CompactGlassActionIcon(
    icon: ImageVector,
    contentDescription: String,
    tint: Color,
    size: androidx.compose.ui.unit.Dp,
    iconSize: androidx.compose.ui.unit.Dp,
    onClick: () -> Unit
) {
    val snapshot = LocalDeckTheme.current
    val corner = (snapshot.cornerRadiusDp * 0.28f).coerceIn(5f, 9f).dp
    val shape = RoundedCornerShape(corner)
    Box(
        modifier = Modifier
            .size(size)
            .clip(shape)
            .background(Color.White.copy(alpha = 0.08f))
            .clickable { onClick() },
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = tint,
            modifier = Modifier.size(iconSize)
        )
    }
}

@Composable
fun EmptyWidgetSlot(onPickWidget: () -> Unit) {
    val lsColors = LightspeedStyle.colors
    val snapshot = LocalDeckTheme.current
    val opticalColors = snapshot.resolveOpticalColors()
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
            tint = opticalColors.readableTintColor,
            modifier = Modifier.size(32.dp)
        )
        val isArabic = LightspeedLocaleEngine.state.primary == LightspeedLocaleEngine.AppLanguage.AR
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = lsString(R.string.ref_bay_empty_slot_title),
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Monospace,
            letterSpacing = if (isArabic) 0.sp else 1.sp,
            color = lsColors.textPrimary
        )
        Spacer(modifier = Modifier.height(3.dp))
        Text(
            text = lsString(R.string.ref_bay_empty_slot_sub),
            fontSize = 11.sp,
            fontFamily = FontFamily.Monospace,
            color = lsColors.textSecondary.copy(alpha = 0.72f)
        )
    }
}
