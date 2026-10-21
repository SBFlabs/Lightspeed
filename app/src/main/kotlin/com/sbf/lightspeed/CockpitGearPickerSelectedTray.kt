package com.sbf.lightspeed

import android.content.Context
import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sbf.lightspeed.system.LightspeedIconManager
import com.sbf.lightspeed.system.LightspeedShortcutManager
import com.sbf.lightspeed.ui.theme.LightspeedStyle
import com.sbf.lightspeed.ui.theme.LightspeedTokens
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Selected Tokens Reorder Tray for multi-select gear ring payload configuration.
 */
@Composable
internal fun CockpitGearPickerSelectedTokensTray(
    selectedTokens: MutableList<String>,
    dynamicPrimary: Color,
    dynamicSecondary: Color,
    context: Context,
    modifier: Modifier = Modifier
) {
    val lsColors = LightspeedStyle.colors
    LazyRow(
        modifier = modifier
            .fillMaxWidth()
            .padding(bottom = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        items(selectedTokens.size, key = { idx -> "selected_${idx}_${selectedTokens[idx]}" }) { idx ->
            val token = selectedTokens[idx]
            val label = LightspeedShortcutManager.resolveLabel(context, token)
            val iconBmp by produceState<Bitmap?>(initialValue = LightspeedIconManager.temporaryPickerCache[token], token) {
                if (value == null) {
                    withContext(Dispatchers.IO) {
                        value = LightspeedShortcutManager.resolveIconBitmap(context, token, useCache = false)
                    }
                }
            }
            val isFirst = (idx == 0)
            val isLast = (idx == selectedTokens.size - 1)

            Row(
                modifier = Modifier
                    .background(lsColors.textPrimary.copy(alpha = 0.08f), RoundedCornerShape(10.dp))
                    .border(1.dp, dynamicPrimary.copy(alpha = 0.5f), RoundedCornerShape(10.dp))
                    .padding(horizontal = 6.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (selectedTokens.size > 1) {
                    IconButton(
                        onClick = {
                            if (!isFirst) {
                                val temp = selectedTokens[idx]
                                selectedTokens[idx] = selectedTokens[idx - 1]
                                selectedTokens[idx - 1] = temp
                            }
                        },
                        enabled = !isFirst,
                        modifier = Modifier.size(22.dp)
                    ) {
                        Text(
                            text = "◀",
                            fontSize = 10.sp,
                            color = if (!isFirst) dynamicSecondary else lsColors.textDisabled.copy(alpha = 0.3f),
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                if (iconBmp != null) {
                    Image(
                        bitmap = iconBmp!!.asImageBitmap(),
                        contentDescription = null,
                        modifier = Modifier
                            .size(16.dp)
                            .padding(end = 4.dp)
                    )
                } else {
                    Box(
                        modifier = Modifier
                            .size(16.dp)
                            .padding(end = 4.dp)
                            .background(lsColors.textPrimary.copy(alpha = 0.10f), RoundedCornerShape(3.dp))
                    )
                }

                Text(
                    text = "${idx + 1}. ${label.take(13)}",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = lsColors.textPrimary,
                    modifier = Modifier.padding(horizontal = 4.dp)
                )

                if (selectedTokens.size > 1) {
                    IconButton(
                        onClick = {
                            if (!isLast) {
                                val temp = selectedTokens[idx]
                                selectedTokens[idx] = selectedTokens[idx + 1]
                                selectedTokens[idx + 1] = temp
                            }
                        },
                        enabled = !isLast,
                        modifier = Modifier.size(22.dp)
                    ) {
                        Text(
                            text = "▶",
                            fontSize = 10.sp,
                            color = if (!isLast) dynamicSecondary else lsColors.textDisabled.copy(alpha = 0.3f),
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                IconButton(
                    onClick = { selectedTokens.removeAt(idx) },
                    modifier = Modifier.size(22.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Remove",
                        tint = LightspeedTokens.StatusErrorSoft,
                        modifier = Modifier.size(13.dp)
                    )
                }
            }
        }
    }
}
