package com.sbf.lightspeed.settings

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import com.sbf.lightspeed.ui.theme.LocalDeckTheme
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Unified optical sub-chip for gesture controls and contextual action triggers.
 * Styled dynamically based on [DeckThemeSnapshot] from the 12-theme optical engine.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun DeckSubChip(
    text: String,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    isActive: Boolean = false,
    isEnabled: Boolean = true,
    snapshot: DeckThemeSnapshot = LocalDeckTheme.current,
    onClick: (() -> Unit)? = null,
    onLongClick: (() -> Unit)? = null
) {
    val cornerRadius = (snapshot.cornerRadiusDp * 0.32f).coerceIn(6f, 10f).dp
    val shape = RoundedCornerShape(cornerRadius)
    val borderWidth = (snapshot.borderWidthDp * 0.75f).coerceIn(0.8f, 1.4f).dp

    val opticalColors = snapshot.resolveOpticalColors()
    val tintColor = opticalColors.readableTintColor
    val panelColor = opticalColors.panelColor
    val borderColor = opticalColors.borderColor

    val containerBrush = when {
        !isEnabled -> Brush.verticalGradient(
            listOf(
                panelColor.copy(alpha = 0.25f),
                panelColor.copy(alpha = 0.20f)
            )
        )
        isActive -> Brush.verticalGradient(
            listOf(
                tintColor.copy(alpha = 0.26f),
                panelColor.copy(alpha = 0.75f)
            )
        )
        else -> Brush.verticalGradient(
            listOf(
                panelColor.copy(alpha = 0.65f),
                panelColor.copy(alpha = 0.50f)
            )
        )
    }

    val currentBorder = when {
        !isEnabled -> BorderStroke(borderWidth, borderColor.copy(alpha = 0.12f))
        isActive -> BorderStroke(borderWidth, borderColor.copy(alpha = 0.55f))
        else -> BorderStroke(borderWidth, borderColor.copy(alpha = 0.22f))
    }

    val contentColor = when {
        !isEnabled -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
        isActive -> tintColor.copy(alpha = 1f)
        else -> Color.White.copy(alpha = 0.88f)
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
            .border(currentBorder, shape)
            .background(containerBrush)
            .then(clickableModifier)
            .padding(horizontal = 8.dp, vertical = 4.5.dp)
            .defaultMinSize(minHeight = 26.dp),
        contentAlignment = Alignment.Center
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (icon != null) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = contentColor,
                    modifier = Modifier.size(13.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
            }
            Text(
                text = text,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = contentColor,
                maxLines = 1
            )
        }
    }
}
