package com.sbf.lightspeed.settings

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sbf.lightspeed.ui.theme.LightspeedStyle

/**
 * Modern High-Contrast Single-Line Pill Button for Trackpad style & orientation selectors (< 36dp).
 * Guarantees zero text wrapping, zero black text, and crisp Material 3 contrast.
 * Displays golden lock indicator for options locked behind Lightspeed Infinity.
 */
@Composable
fun TrackpadPillButton(
    text: String,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    isLocked: Boolean = false
) {
    val lsColors = LightspeedStyle.colors
    val containerColor = when {
        isLocked -> lsColors.accentGold.copy(alpha = 0.08f)
        isSelected -> MaterialTheme.colorScheme.primary.copy(alpha = 0.28f)
        else -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
    }
    val borderColor = when {
        isLocked -> lsColors.accentGold.copy(alpha = 0.45f)
        isSelected -> MaterialTheme.colorScheme.primary
        else -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.16f)
    }
    val textColor = when {
        isLocked -> lsColors.accentGold
        isSelected -> MaterialTheme.colorScheme.primary
        else -> Color.White
    }

    Surface(
        modifier = modifier
            .height(34.dp)
            .clickable { onClick() },
        shape = RoundedCornerShape(8.dp),
        color = containerColor,
        border = BorderStroke(if (isSelected || isLocked) 1.2.dp else 0.8.dp, borderColor)
    ) {
        Box(
            modifier = Modifier.fillMaxSize().padding(horizontal = 4.dp),
            contentAlignment = Alignment.Center
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                if (isLocked) {
                    Icon(
                        imageVector = Icons.Filled.Lock,
                        contentDescription = "Locked behind Infinity",
                        tint = lsColors.accentGold,
                        modifier = Modifier.size(10.dp)
                    )
                    Spacer(modifier = Modifier.width(3.dp))
                }
                Text(
                    text = text,
                    color = textColor,
                    fontSize = 10.sp,
                    fontWeight = if (isSelected || isLocked) FontWeight.ExtraBold else FontWeight.Bold,
                    maxLines = 1,
                    softWrap = false
                )
            }
        }
    }
}
