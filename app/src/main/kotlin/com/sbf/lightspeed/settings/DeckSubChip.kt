package com.sbf.lightspeed.settings

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sbf.lightspeed.ui.theme.LightspeedStyle
import com.sbf.lightspeed.ui.theme.LocalDeckTheme

/**
 * Modern tonal capsule chip for gesture controls, dropdown triggers, and contextual actions.
 * Styled flat and clean from [DeckThemeSnapshot] and Material 3 color tokens.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun DeckSubChip(
    text: String,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    trailingIcon: ImageVector? = null,
    onTrailingIconClick: (() -> Unit)? = null,
    isActive: Boolean = false,
    isEnabled: Boolean = true,
    snapshot: DeckThemeSnapshot = LocalDeckTheme.current,
    onClick: (() -> Unit)? = null,
    onLongClick: (() -> Unit)? = null
) {
    val shape = CircleShape
    val lsColors = LightspeedStyle.colors
    val colorScheme = MaterialTheme.colorScheme
    val opticalColors = snapshot.resolveOpticalColors()
    val isM3 = snapshot.styleId == DeckStylePresets.MATERIAL_3.styleId
    val tintColor = opticalColors.readableTintColor
    val isLightPanel = opticalColors.panelColor.luminance() > 0.5f

    val containerColor = when {
        !isEnabled && isM3 -> colorScheme.onSurface.copy(alpha = 0.05f)
        !isEnabled -> lsColors.textPrimary.copy(alpha = 0.03f)
        isActive && isM3 -> colorScheme.secondaryContainer
        isActive -> tintColor.copy(alpha = if (isLightPanel) 0.16f else 0.20f)
        isM3 -> colorScheme.surfaceContainerHigh
        else -> lsColors.textPrimary.copy(alpha = 0.06f)
    }

    val currentBorder: BorderStroke? = when {
        isM3 -> null
        !isEnabled -> BorderStroke(0.75.dp, lsColors.textPrimary.copy(alpha = 0.06f))
        isActive -> BorderStroke(0.9.dp, tintColor.copy(alpha = 0.42f))
        else -> BorderStroke(0.75.dp, lsColors.textPrimary.copy(alpha = 0.10f))
    }

    val contentColor = when {
        !isEnabled && isM3 -> colorScheme.onSurface.copy(alpha = 0.36f)
        !isEnabled -> lsColors.textPrimary.copy(alpha = 0.36f)
        isActive && isM3 -> colorScheme.onSecondaryContainer
        isActive -> if (isLightPanel) colorScheme.primary else tintColor
        isM3 -> colorScheme.onSurfaceVariant
        else -> lsColors.textPrimary.copy(alpha = 0.85f)
    }

    val clickableModifier = if (onClick != null || onLongClick != null) {
        Modifier.combinedClickable(
            enabled = isEnabled,
            onClick = { onClick?.invoke() },
            onLongClick = onLongClick
        )
    } else Modifier

    Box(
        modifier = modifier
            .clip(shape)
            .background(containerColor)
            .then(if (currentBorder != null) Modifier.border(currentBorder, shape) else Modifier)
            .then(clickableModifier)
            .padding(horizontal = 11.dp, vertical = 6.dp)
            .defaultMinSize(minHeight = 32.dp),
        contentAlignment = Alignment.Center
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            if (icon != null) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = contentColor,
                    modifier = Modifier.size(14.dp)
                )
                Spacer(modifier = Modifier.width(5.dp))
            }
            Text(
                text = text,
                fontSize = 11.5.sp,
                lineHeight = 15.sp,
                fontWeight = if (isActive) FontWeight.SemiBold else FontWeight.Medium,
                color = contentColor,
                maxLines = 1,
                softWrap = false,
                overflow = TextOverflow.Ellipsis
            )
            if (trailingIcon != null) {
                Spacer(modifier = Modifier.width(6.dp))
                Box(
                    modifier = Modifier
                        .size(18.dp)
                        .clip(CircleShape)
                        .background(contentColor.copy(alpha = 0.18f))
                        .clickable(enabled = isEnabled) { onTrailingIconClick?.invoke() },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = trailingIcon,
                        contentDescription = "Information",
                        tint = contentColor,
                        modifier = Modifier.size(11.dp)
                    )
                }
            }
        }
    }
}

/**
 * Unified single-select segmented pill track for exclusive mode selectors
 * (e.g. Abort Action, Color Engine, Render Mode).
 */
@Composable
fun DeckSegmentedRow(
    options: List<Pair<String, String>>,
    selectedKey: String,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier,
    snapshot: DeckThemeSnapshot = LocalDeckTheme.current
) {
    val lsColors = LightspeedStyle.colors
    val colorScheme = MaterialTheme.colorScheme
    val opticalColors = snapshot.resolveOpticalColors()
    val isM3 = snapshot.styleId == DeckStylePresets.MATERIAL_3.styleId
    val tintColor = opticalColors.readableTintColor
    val isLightPanel = opticalColors.panelColor.luminance() > 0.5f

    val trackShape = RoundedCornerShape(14.dp)
    val segmentShape = RoundedCornerShape(10.dp)
    val trackBg = if (isM3) colorScheme.surfaceContainerHigh else lsColors.textPrimary.copy(alpha = 0.06f)
    val trackBorderMod = if (isM3) {
        Modifier
    } else {
        Modifier.border(0.75.dp, lsColors.textPrimary.copy(alpha = 0.10f), trackShape)
    }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(trackShape)
            .background(trackBg)
            .then(trackBorderMod)
            .padding(3.dp),
        horizontalArrangement = Arrangement.spacedBy(3.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        options.forEach { (key, label) ->
            val isSelected = selectedKey == key
            val segmentBg = when {
                isSelected && isM3 -> colorScheme.secondaryContainer
                isSelected -> tintColor.copy(alpha = if (isLightPanel) 0.18f else 0.24f)
                else -> lsColors.textPrimary.copy(alpha = 0f)
            }
            val segmentTextColor = when {
                isSelected && isM3 -> colorScheme.onSecondaryContainer
                isSelected -> if (isLightPanel) colorScheme.primary else tintColor
                isM3 -> colorScheme.onSurfaceVariant
                else -> lsColors.textSecondary
            }
            val borderMod = if (isSelected && !isM3) {
                Modifier.border(1.dp, tintColor.copy(alpha = 0.48f), segmentShape)
            } else {
                Modifier
            }
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(segmentShape)
                    .background(segmentBg)
                    .then(borderMod)
                    .clickable { onSelect(key) }
                    .padding(horizontal = 8.dp, vertical = 7.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = label,
                    fontSize = 11.5.sp,
                    fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Medium,
                    color = segmentTextColor,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}
