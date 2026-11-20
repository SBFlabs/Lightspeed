package com.sbf.lightspeed.settings

import com.sbf.lightspeed.R
import com.sbf.lightspeed.system.lsString

import android.content.Context
import android.widget.Toast
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.sbf.lightspeed.system.LightspeedHapticEngine
import com.sbf.lightspeed.ui.theme.LightspeedStyle

private val STYLE_CAPTIONS: Map<String, String> = mapOf(
    "material3" to lsString(R.string.slab_desc_m3),
    "prism" to lsString(R.string.slab_desc_lava),
    "jelly" to lsString(R.string.slab_desc_jelly),
    "frosted" to lsString(R.string.slab_desc_frosted),
    "blur" to lsString(R.string.slab_desc_dark),
    "progressive" to lsString(R.string.slab_desc_prog),
    "glassmorphism" to lsString(R.string.slab_desc_glass),
    "neon" to lsString(R.string.slab_desc_neon),
    "adaptive" to lsString(R.string.slab_desc_adapt),
    "acrylic" to lsString(R.string.slab_desc_acrylic),
    "gaussian" to lsString(R.string.slab_desc_gauss),
    "depth" to lsString(R.string.slab_desc_depth),
    "dark" to lsString(R.string.slab_desc_prism)
)

internal fun captionForStyle(styleId: String): String {
    return STYLE_CAPTIONS[styleId] ?: ""
}

@Composable
fun StyleLabPreviewDialog(
    context: Context,
    onDismiss: () -> Unit,
    onLockedClick: (() -> Unit)? = null,
    onCustomizeStyle: ((styleId: String) -> Unit)? = null
) {
    val lsColors = LightspeedStyle.colors
    val basePresets = DeckStylePresets.ALL
    val activeStyleId by DeckThemeManager.styleIdFlow.collectAsState()
    val customizations by ThemeCustomizationManager.customizationsFlow.collectAsState()
    val presets = remember(basePresets, customizations, activeStyleId) {
        basePresets.map { base ->
            ThemeCustomizationManager.getCustomization(context, base.styleId).toSnapshot(base)
        }
    }
    val isInfinityUnlocked = com.sbf.lightspeed.system.LightspeedInfinityManager.isUnlocked(context)
    val activeDensity = LocalDensity.current

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        CompositionLocalProvider(LocalDensity provides activeDensity) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = Color(0xFF070913)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 12.dp, vertical = 14.dp)
            ) {
                // Header bar with title and Close button
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 4.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(34.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.18f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.AutoAwesome,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(
                                text = lsString(R.string.slab_title),
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Black,
                                color = Color.White,
                                letterSpacing = 0.6.sp
                            )
                            Text(
                                text = lsString(R.string.slab_sub),
                                fontSize = 10.5.sp,
                                color = lsColors.textSecondary.copy(alpha = 0.80f)
                            )
                        }
                    }

                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier.size(34.dp),
                        colors = IconButtonDefaults.iconButtonColors(
                            containerColor = Color.White.copy(alpha = 0.08f)
                        )
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Close",
                            tint = Color.White,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }

                HorizontalDivider(
                    color = Color.White.copy(alpha = 0.08f),
                    modifier = Modifier.padding(vertical = 8.dp)
                )

                // Scrollable 2-column grid of the 12 style preview tiles
                val styleGridState = androidx.compose.foundation.lazy.grid.rememberLazyGridState()
                LazyVerticalGrid(
                    state = styleGridState,
                    columns = GridCells.Fixed(2),
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .verticalEdgeFade(styleGridState),
                    contentPadding = PaddingValues(bottom = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    itemsIndexed(
                        items = presets,
                        key = { _, preset -> preset.styleId }
                    ) { index, preset ->
                        val isSelected = activeStyleId == preset.styleId
                        val isLocked = !preset.freeTier && !isInfinityUnlocked
                        StyleLabTileCard(
                            index = index,
                            preset = preset,
                            isSelected = isSelected,
                            isLocked = isLocked,
                            onClick = {
                                if (isLocked) {
                                    LightspeedHapticEngine.triggerWarning(context)
                                    if (onLockedClick != null) {
                                        onDismiss()
                                        onLockedClick()
                                    } else {
                                        Toast.makeText(context, lsString(R.string.slab_req_inf), Toast.LENGTH_SHORT).show()
                                    }
                                } else if (isSelected) {
                                    LightspeedHapticEngine.tick(context)
                                    if (onCustomizeStyle != null) {
                                        onDismiss()
                                        onCustomizeStyle(preset.styleId)
                                    }
                                } else {
                                    DeckThemeManager.setStyle(context, preset.styleId)
                                    LightspeedHapticEngine.tick(context)
                                    Toast.makeText(context, "Theme applied: ${preset.displayName}", Toast.LENGTH_SHORT).show()
                                }
                            },
                            onLongClick = {
                                if (isLocked) {
                                    LightspeedHapticEngine.triggerWarning(context)
                                    if (onLockedClick != null) {
                                        onDismiss()
                                        onLockedClick()
                                    } else {
                                        Toast.makeText(context, lsString(R.string.slab_req_inf), Toast.LENGTH_SHORT).show()
                                    }
                                } else {
                                    LightspeedHapticEngine.tick(context)
                                    if (onCustomizeStyle != null) {
                                        onDismiss()
                                        onCustomizeStyle(preset.styleId)
                                    }
                                }
                            }
                        )
                    }
                }
            }
        }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun StyleLabTileCard(
    index: Int,
    preset: DeckThemeSnapshot,
    isSelected: Boolean,
    isLocked: Boolean,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)? = null
) {
    val lsColors = LightspeedStyle.colors
    val primaryColor = MaterialTheme.colorScheme.primary
    val titleText = "${index + 1} ${preset.displayName}"
    val captionText = captionForStyle(preset.styleId)

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick
            ),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) Color(0xFF161D36) else Color(0xFF101426)
        ),
        border = BorderStroke(
            width = if (isSelected) 1.6.dp else 1.dp,
            color = if (isSelected) primaryColor else Color.White.copy(alpha = 0.10f)
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(10.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            // Title Row + Active / Free / Lock badge
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = titleText,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (isSelected) primaryColor else Color.White,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                Spacer(modifier = Modifier.width(4.dp))
                if (isSelected) {
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = primaryColor.copy(alpha = 0.22f),
                        border = BorderStroke(0.8.dp, primaryColor.copy(alpha = 0.75f))
                    ) {
                        Text(
                            text = lsString(R.string.slab_active),
                            fontSize = 8.5.sp,
                            fontWeight = FontWeight.Bold,
                            color = primaryColor,
                            modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.5.dp)
                        )
                    }
                } else if (preset.freeTier) {
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = lsColors.accentOk.copy(alpha = 0.16f),
                        border = BorderStroke(0.7.dp, lsColors.accentOk.copy(alpha = 0.55f))
                    ) {
                        Text(
                            text = lsString(R.string.slab_free),
                            fontSize = 8.5.sp,
                            fontWeight = FontWeight.Bold,
                            color = lsColors.accentOk,
                            modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.5.dp)
                        )
                    }
                } else if (isLocked) {
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = lsColors.accentGold.copy(alpha = 0.14f),
                        border = BorderStroke(0.7.dp, lsColors.accentGold.copy(alpha = 0.45f))
                    ) {
                        Box(
                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Lock,
                                contentDescription = "Locked",
                                tint = lsColors.accentGold,
                                modifier = Modifier.size(10.dp)
                            )
                        }
                    }
                }
            }

            // Caption
            Text(
                text = captionText,
                fontSize = 9.5.sp,
                color = Color.White.copy(alpha = 0.72f),
                lineHeight = 12.sp,
                minLines = 2,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )

            // Preview Area
            StyleLabCardPreview(
                preset = preset,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}
