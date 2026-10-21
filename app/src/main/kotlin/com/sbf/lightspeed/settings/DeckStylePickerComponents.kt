package com.sbf.lightspeed.settings

import android.content.Context
import android.os.Build
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import com.sbf.lightspeed.ui.theme.LocalDeckTheme
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sbf.lightspeed.system.LightspeedHapticEngine
import com.sbf.lightspeed.ui.theme.LightspeedStyle

/**
 * Resolved surface and border visuals derived from [DeckThemeSnapshot] for the Central Command window.
 */
data class CentralCommandSnapshotVisuals(
    val snapshot: DeckThemeSnapshot,
    val panelColor: Color,
    val tintColor: Color,
    val borderColor: Color,
    val backgroundBrush: Brush,
    val borderBrush: Brush,
    val borderWidth: Dp,
    val cornerRadius: Dp
)

/**
 * Observes [DeckThemeManager.snapshotFlow] (initializing from preferences on first composition)
 * and resolves the panel fill, alpha, tint, border colour/width, and corner radius for the
 * Central Command window.
 *
 * On API 31+ with [DeckStylePresets.ADAPTIVE], system dynamic colours are used
 * (`surfaceContainerHigh` for panel, `primary` for tint and border); `prism` and `progressive`
 * use their fixed preset colours.
 */
@Composable
fun rememberCentralCommandSnapshotVisuals(
    context: Context,
    colorScheme: ColorScheme = MaterialTheme.colorScheme
): CentralCommandSnapshotVisuals {
    val snapshot = LocalDeckTheme.current
    val isApi31Plus = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S

    return remember(snapshot, colorScheme, isApi31Plus) {
        val isAdaptive = snapshot.styleId == DeckStylePresets.ADAPTIVE.styleId
        val useDynamic = snapshot.usesSystemDynamicColor && isApi31Plus && isAdaptive

        val panelColor = if (useDynamic) colorScheme.surfaceContainerHigh else Color(snapshot.panelColorArgb)
        val tintColor = if (useDynamic) colorScheme.primary else Color(snapshot.tintArgb)
        val borderColor = if (useDynamic) colorScheme.primary.copy(alpha = 0.80f) else Color(snapshot.borderColorArgb)

        val alpha = snapshot.panelAlpha.coerceIn(0.15f, 0.98f)
        val topPanel = panelColor.copy(alpha = (alpha * 0.90f).coerceIn(0.12f, 0.98f))
        val bottomPanel = panelColor.copy(alpha = alpha)
        val tintInfusionAlpha = when (snapshot.styleId) {
            DeckStylePresets.JELLY.styleId -> 0.24f
            DeckStylePresets.ADAPTIVE.styleId -> 0.18f
            else -> 0.12f
        }
        val blendedTop = Color(
            red = (topPanel.red * (1f - tintInfusionAlpha) + tintColor.red * tintInfusionAlpha).coerceIn(0f, 1f),
            green = (topPanel.green * (1f - tintInfusionAlpha) + tintColor.green * tintInfusionAlpha).coerceIn(0f, 1f),
            blue = (topPanel.blue * (1f - tintInfusionAlpha) + tintColor.blue * tintInfusionAlpha).coerceIn(0f, 1f),
            alpha = topPanel.alpha
        )

        val backgroundBrush = Brush.verticalGradient(
            colors = listOf(blendedTop, bottomPanel)
        )
        val borderBrush = Brush.verticalGradient(
            colors = listOf(
                borderColor,
                borderColor.copy(alpha = (borderColor.alpha * 0.45f).coerceIn(0f, 1f))
            )
        )

        CentralCommandSnapshotVisuals(
            snapshot = snapshot,
            panelColor = panelColor,
            tintColor = tintColor,
            borderColor = borderColor,
            backgroundBrush = backgroundBrush,
            borderBrush = borderBrush,
            borderWidth = snapshot.borderWidthDp.dp,
            cornerRadius = snapshot.cornerRadiusDp.dp
        )
    }
}

/**
 * Compact 12-style grid picker for the Flight Control Deck.
 * Shows each style's display name, a two-tone preset swatch (panel + tint/border),
 * and a lock indicator for non-free styles when Infinity is not unlocked.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun DeckStylePickerGrid(
    context: Context,
    isInfinityUnlocked: Boolean,
    onLockedClick: () -> Unit,
    onCustomizeStyle: (styleId: String) -> Unit = {},
    onPrismLongClick: () -> Unit = {},
    onCustomizationRequested: () -> Unit = onPrismLongClick,
    onStyleSelected: () -> Unit
) {
    val lsColors = LightspeedStyle.colors
    val colorScheme = MaterialTheme.colorScheme
    val isApi31Plus = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S

    LaunchedEffect(context) {
        DeckThemeManager.getStyleId(context)
    }
    val activeStyleId by DeckThemeManager.styleIdFlow.collectAsState()
    val activePreset = remember(activeStyleId) { DeckStylePresets.resolve(activeStyleId) }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                "CENTRAL COMMAND THEME",
                fontSize = 10.5.sp,
                fontWeight = FontWeight.Bold,
                color = colorScheme.primary,
                letterSpacing = 1.sp
            )
            Text(
                activePreset.displayName,
                fontSize = 10.sp,
                color = lsColors.textSecondary.copy(alpha = 0.75f)
            )
        }

        val rows = remember { DeckStylePresets.ALL.chunked(3) }
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            rows.forEach { rowPresets ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    rowPresets.forEach { preset ->
                        val isSelected = activeStyleId == preset.styleId
                        val isLocked = !preset.freeTier && !isInfinityUnlocked
                        val miniCorner = (preset.cornerRadiusDp * 0.42f).dp

                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(miniCorner))
                                .deckGlassSurface(
                                    snapshot = preset,
                                    isBlurAvailable = true,
                                    isMiniTile = true,
                                    colorScheme = colorScheme
                                )
                                .then(
                                    if (isSelected) {
                                        Modifier.border(
                                            1.6.dp,
                                            colorScheme.primary,
                                            RoundedCornerShape(miniCorner)
                                        )
                                    } else Modifier
                                )
                                .combinedClickable(
                                    onClick = {
                                        if (isLocked) {
                                            LightspeedHapticEngine.triggerWarning(context)
                                            onLockedClick()
                                        } else if (isSelected) {
                                            LightspeedHapticEngine.tick(context)
                                            onCustomizeStyle(preset.styleId)
                                            onCustomizationRequested()
                                        } else {
                                            DeckThemeManager.setStyle(context, preset.styleId)
                                            LightspeedHapticEngine.tick(context)
                                            onStyleSelected()
                                        }
                                    },
                                    onLongClick = {
                                        if (isLocked) {
                                            LightspeedHapticEngine.triggerWarning(context)
                                            onLockedClick()
                                        } else {
                                            LightspeedHapticEngine.tick(context)
                                            onCustomizeStyle(preset.styleId)
                                            onCustomizationRequested()
                                        }
                                    }
                                )
                                .padding(vertical = 10.dp, horizontal = 6.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.Center
                            ) {
                                if (isSelected) {
                                    Box(
                                        modifier = Modifier
                                            .size(6.dp)
                                            .clip(CircleShape)
                                            .background(colorScheme.primary)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                } else if (isLocked) {
                                    Icon(
                                        imageVector = Icons.Filled.Lock,
                                        contentDescription = null,
                                        tint = lsColors.accentGold,
                                        modifier = Modifier.size(10.dp)
                                    )
                                    Spacer(modifier = Modifier.width(3.dp))
                                }
                                Text(
                                    text = preset.displayName,
                                    fontSize = 9.8.sp,
                                    fontWeight = if (isSelected) FontWeight.ExtraBold else FontWeight.SemiBold,
                                    color = if (isLocked) lsColors.accentGold else if (isSelected) Color.White else Color.White.copy(alpha = 0.88f),
                                    maxLines = 1,
                                    softWrap = false,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
