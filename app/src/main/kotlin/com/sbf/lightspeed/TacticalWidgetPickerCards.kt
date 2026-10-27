package com.sbf.lightspeed

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sbf.lightspeed.ui.theme.LightspeedStyle
import com.sbf.lightspeed.ui.theme.LightspeedTokens

/**
 * Individual Tactical Widget Card with Preview Image, Cell Dimensions, and Deploy Action.
 */
@Composable
internal fun TacticalWidgetItemCard(
    item: TacticalWidgetItem,
    onClick: () -> Unit
) {
    val lsColors = LightspeedStyle.colors
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .clickable { onClick() },
        colors = CardDefaults.cardColors(containerColor = LightspeedTokens.TacticalSurfaceContainer),
        shape = RoundedCornerShape(8.dp),
        border = BorderStroke(1.dp, LightspeedTokens.TacticalSurfaceBorderAlt)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Widget Preview Box
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 90.dp, max = 150.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(LightspeedTokens.TacticalSurfaceBase)
                    .border(1.dp, LightspeedTokens.TacticalSurfaceBorder, RoundedCornerShape(6.dp)),
                contentAlignment = Alignment.Center
            ) {
                if (item.previewBitmap != null) {
                    Image(
                        bitmap = item.previewBitmap.asImageBitmap(),
                        contentDescription = item.label,
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(4.dp),
                        contentScale = ContentScale.Fit
                    )
                } else {
                    // Blueprint Grid Fallback
                    TacticalBlueprintPlaceholder(item = item)
                }
            }

            // Widget Info Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = item.label,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        fontFamily = FontFamily.Monospace,
                        color = lsColors.textPrimary
                    )
                    if (!item.description.isNullOrBlank()) {
                        Text(
                            text = item.description,
                            fontSize = 10.sp,
                            color = lsColors.textSecondary.copy(alpha = 0.6f),
                            maxLines = 2
                        )
                    }
                }

                Spacer(modifier = Modifier.width(8.dp))

                // Dimensions & Deploy Action
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    // Grid Dimension Badge
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .background(lsColors.textPrimary.copy(alpha = 0.06f))
                            .border(0.8.dp, lsColors.textPrimary.copy(alpha = 0.15f), RoundedCornerShape(4.dp))
                            .padding(horizontal = 6.dp, vertical = 3.dp)
                    ) {
                        Text(
                            text = "${item.cellWidth} × ${item.cellHeight}",
                            fontSize = 9.5.sp,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold,
                            color = lsColors.textPrimary.copy(alpha = 0.8f)
                        )
                    }

                    // Deploy Button
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.2f))
                            .border(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.6f), RoundedCornerShape(4.dp))
                            .padding(horizontal = 8.dp, vertical = 3.dp)
                    ) {
                        Text(
                            text = "+ DEPLOY",
                            fontSize = 9.5.sp,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }
        }
    }
}

/**
 * High-tech tactical blueprint placeholder when a widget does not provide a preview image.
 */
@Composable
internal fun TacticalBlueprintPlaceholder(item: TacticalWidgetItem) {
    val lsColors = LightspeedStyle.colors
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val step = 16.dp.toPx()
            val gridColor = lsColors.accentInfo.copy(alpha = 0.04f)
            var x = 0f
            while (x < size.width) {
                drawLine(gridColor, Offset(x, 0f), Offset(x, size.height), strokeWidth = 1f)
                x += step
            }
            var y = 0f
            while (y < size.height) {
                drawLine(gridColor, Offset(0f, y), Offset(size.width, y), strokeWidth = 1f)
                y += step
            }
        }

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (item.appIcon != null) {
                Image(
                    bitmap = item.appIcon.asImageBitmap(),
                    contentDescription = null,
                    modifier = Modifier.size(24.dp)
                )
            }
            Column {
                Text(
                    text = "[ ${item.cellWidth} × ${item.cellHeight} GRID CELLS ]",
                    fontSize = 10.sp,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.85f),
                    letterSpacing = 1.sp
                )
                Text(
                    text = "STANDALONE AVIONICS MODULE",
                    fontSize = 8.5.sp,
                    fontFamily = FontFamily.Monospace,
                    color = lsColors.textPrimary.copy(alpha = 0.35f)
                )
            }
        }
    }
}

/**
 * Lightweight custom text field for the search input.
 */
@Composable
internal fun BasicTextSearchField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier
) {
    val lsColors = LightspeedStyle.colors
    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier,
        singleLine = true,
        textStyle = TextStyle(
            color = lsColors.textPrimary,
            fontSize = 12.sp,
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.Medium
        ),
        cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
        decorationBox = { innerTextField ->
            if (value.isEmpty()) {
                Text(
                    text = placeholder,
                    color = lsColors.textPrimary.copy(alpha = 0.35f),
                    fontSize = 11.5.sp,
                    fontFamily = FontFamily.Monospace
                )
            }
            innerTextField()
        }
    )
}
