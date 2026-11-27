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
import com.sbf.lightspeed.R
import com.sbf.lightspeed.system.lsString
import com.sbf.lightspeed.ui.theme.LightspeedStyle
import kotlinx.coroutines.delay

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
        isSelected -> lsColors.accent.copy(alpha = 0.28f)
        else -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
    }
    val borderColor = when {
        isLocked -> lsColors.accentGold.copy(alpha = 0.45f)
        isSelected -> lsColors.accent
        else -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.16f)
    }
    val textColor = when {
        isLocked -> lsColors.accentGold
        isSelected -> lsColors.accent
        else -> MaterialTheme.colorScheme.onSurface
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

/**
 * Compact rotating tactical tip strip for Cybernetic Trackpad settings surfaces.
 * Cycles automatically every 4.5s and advances immediately when tapped.
 */
@Composable
fun TrackpadRotatingTipBanner(modifier: Modifier = Modifier) {
    val lsColors = LightspeedStyle.colors
    val tipResIds = remember {
        intArrayOf(R.string.tp_tip_1, R.string.tp_tip_2, R.string.tp_tip_3)
    }
    var tipIndex by remember { mutableIntStateOf(0) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(4500L)
            tipIndex = (tipIndex + 1) % tipResIds.size
        }
    }
    val kicker = lsString(R.string.ck_hangar_tip_kicker).ifBlank { "TIP" }
    DeckCard(
        modifier = modifier
            .fillMaxWidth()
            .clickable { tipIndex = (tipIndex + 1) % tipResIds.size },
        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 7.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(
                text = "✦ $kicker:",
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                color = lsColors.accentInfo
            )
            Text(
                text = lsString(tipResIds[tipIndex]),
                fontSize = 10.sp,
                color = lsColors.textSecondary,
                lineHeight = 13.5.sp,
                minLines = 2,
                maxLines = 2,
                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
        }
    }
}
