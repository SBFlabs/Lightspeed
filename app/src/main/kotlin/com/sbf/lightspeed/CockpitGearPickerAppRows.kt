package com.sbf.lightspeed

import android.content.Context
import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sbf.lightspeed.settings.LightspeedActionRegistry
import com.sbf.lightspeed.system.LightspeedIconManager
import com.sbf.lightspeed.system.LightspeedShortcutManager
import com.sbf.lightspeed.ui.theme.LightspeedStyle
import com.sbf.lightspeed.ui.theme.LightspeedTokens
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun PickerAppHeaderRow(
    item: PickerRowItem.AppHeader,
    isAppSelected: Boolean,
    context: Context,
    dynamicPrimary: Color,
    dynamicSecondary: Color,
    onSelect: () -> Unit,
    onLongClick: () -> Unit,
    onExpandToggle: () -> Unit
) {
    val hasShortcuts = item.totalShortcuts > 0

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(50.dp)
            .padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Main Board (Left)
        Row(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight()
                .clip(
                    RoundedCornerShape(
                        topStart = 12.dp,
                        bottomStart = 12.dp,
                        topEnd = if (hasShortcuts) 4.dp else 12.dp,
                        bottomEnd = if (hasShortcuts) 4.dp else 12.dp
                    )
                )
                .background(
                    if (isAppSelected) dynamicPrimary.copy(alpha = 0.28f)
                    else if (item.isPinned) LightspeedTokens.StatusGoldPinSubtle
                    else LightspeedStyle.colors.textPrimary.copy(alpha = 0.06f)
                )
                .border(
                    width = 1.dp,
                    color = if (item.isPinned) LightspeedTokens.StatusGoldPin
                    else if (isAppSelected) dynamicPrimary.copy(alpha = 0.65f)
                    else LightspeedStyle.colors.textPrimary.copy(alpha = 0.07f),
                    shape = RoundedCornerShape(
                        topStart = 12.dp,
                        bottomStart = 12.dp,
                        topEnd = if (hasShortcuts) 4.dp else 12.dp,
                        bottomEnd = if (hasShortcuts) 4.dp else 12.dp
                    )
                )
                .combinedClickable(
                    onClick = onSelect,
                    onLongClick = onLongClick
                )
                .padding(horizontal = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            val iconBmp by produceState<Bitmap?>(initialValue = LightspeedIconManager.temporaryPickerCache[item.packageName], item.packageName) {
                if (value == null) {
                    withContext(Dispatchers.IO) {
                        value = LightspeedActionRegistry.getIconBitmap(context, item.packageName, useCache = false)
                    }
                }
            }
            if (iconBmp != null) {
                Image(
                    bitmap = iconBmp!!.asImageBitmap(),
                    contentDescription = null,
                    modifier = Modifier.size(26.dp).padding(end = 8.dp)
                )
            } else {
                Box(
                    modifier = Modifier
                        .size(26.dp)
                        .padding(end = 8.dp)
                        .background(LightspeedStyle.colors.textPrimary.copy(alpha = 0.12f), RoundedCornerShape(6.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = item.appName.firstOrNull()?.uppercase() ?: "?",
                        color = LightspeedStyle.colors.textPrimary.copy(alpha = 0.6f),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight(),
                verticalArrangement = Arrangement.spacedBy(1.dp, Alignment.CenterVertically)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (item.isPinned) {
                        Icon(
                            imageVector = Icons.Outlined.PushPin,
                            contentDescription = "Pinned",
                            tint = LightspeedStyle.colors.accentGold,
                            modifier = Modifier.size(14.dp).padding(end = 4.dp)
                        )
                    }
                    Text(
                        text = item.appName,
                        color = if (isAppSelected) dynamicPrimary else if (item.isPinned) LightspeedTokens.StatusGoldBright else LightspeedStyle.colors.textPrimary,
                        fontSize = 13.5.sp,
                        fontWeight = if (isAppSelected || item.isPinned) FontWeight.Bold else FontWeight.SemiBold,
                        style = TextStyle(
                            platformStyle = PlatformTextStyle(includeFontPadding = false),
                            lineHeight = 16.sp
                        ),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                if (hasShortcuts) {
                    Text(
                        text = "${item.totalShortcuts} deep action${if (item.totalShortcuts > 1) "s" else ""}",
                        color = dynamicSecondary.copy(alpha = 0.85f),
                        fontSize = 10.sp,
                        style = TextStyle(
                            platformStyle = PlatformTextStyle(includeFontPadding = false),
                            lineHeight = 12.sp
                        ),
                        maxLines = 1
                    )
                }
            }

            if (isAppSelected) {
                Box(
                    modifier = Modifier
                        .padding(start = 6.dp)
                        .background(dynamicPrimary, CircleShape)
                        .padding(horizontal = 7.dp, vertical = 2.5.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "SELECTED",
                        color = Color.Black,
                        fontSize = 8.sp,
                        fontWeight = FontWeight.ExtraBold
                    )
                }
            }
        }

        // Expand Chevron Zone (Right)
        if (hasShortcuts) {
            Spacer(modifier = Modifier.width(3.dp))
            Box(
                modifier = Modifier
                    .width(42.dp)
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(topStart = 4.dp, bottomStart = 4.dp, topEnd = 12.dp, bottomEnd = 12.dp))
                    .background(
                        if (item.isExpanded) dynamicSecondary.copy(alpha = 0.35f)
                        else dynamicSecondary.copy(alpha = 0.12f)
                    )
                    .border(
                        width = 1.dp,
                        color = if (item.isExpanded) dynamicSecondary.copy(alpha = 0.65f) else dynamicSecondary.copy(alpha = 0.20f),
                        shape = RoundedCornerShape(topStart = 4.dp, bottomStart = 4.dp, topEnd = 12.dp, bottomEnd = 12.dp)
                    )
                    .clickable(onClick = onExpandToggle),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Default.ArrowDropDown,
                    contentDescription = if (item.isExpanded) "Collapse" else "Expand",
                    tint = if (item.isExpanded) dynamicSecondary else dynamicSecondary.copy(alpha = 0.9f),
                    modifier = Modifier
                        .size(22.dp)
                        .rotate(if (item.isExpanded) 180f else 0f)
                )
            }
        }
    }
}

@Composable
fun PickerSubHeaderRow(
    item: PickerRowItem.SubHeader,
    dynamicSecondary: Color,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 12.dp, top = 2.dp, bottom = 2.dp)
            .background(LightspeedStyle.colors.textPrimary.copy(alpha = 0.04f), RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = item.label,
            color = dynamicSecondary,
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.weight(1f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Icon(
            Icons.Default.ArrowDropDown,
            contentDescription = null,
            tint = dynamicSecondary,
            modifier = Modifier
                .size(18.dp)
                .rotate(if (item.isExpanded) 180f else 0f)
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun PickerShortcutActionRow(
    item: PickerRowItem.ShortcutAction,
    isChecked: Boolean,
    context: Context,
    dynamicPrimary: Color,
    onSelect: () -> Unit,
    onLaunchWizard: () -> Unit,
    onLongClick: () -> Unit = {}
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(48.dp)
            .padding(start = 18.dp, top = 2.dp, bottom = 2.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(
                color = if (isChecked) dynamicPrimary.copy(alpha = 0.28f) else LightspeedStyle.colors.textPrimary.copy(alpha = 0.03f)
            )
            .border(
                width = 1.dp,
                color = if (isChecked) dynamicPrimary.copy(alpha = 0.65f) else LightspeedStyle.colors.textPrimary.copy(alpha = 0.05f),
                shape = RoundedCornerShape(10.dp)
            )
            .combinedClickable(
                onClick = {
                    if (item.isPlugin && !isChecked) onLaunchWizard() else onSelect()
                },
                onLongClick = onLongClick
            )
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        val shortcutBmp by produceState<Bitmap?>(initialValue = LightspeedIconManager.temporaryPickerCache[item.token], item.token) {
            if (value == null) {
                withContext(Dispatchers.IO) {
                    value = LightspeedShortcutManager.resolveIconBitmap(context, item.token, useCache = false)
                }
            }
        }
        if (shortcutBmp != null) {
            Image(
                bitmap = shortcutBmp!!.asImageBitmap(),
                contentDescription = null,
                modifier = Modifier.size(22.dp).padding(end = 8.dp)
            )
        } else {
            Box(
                modifier = Modifier
                    .size(22.dp)
                    .padding(end = 8.dp)
                    .background(LightspeedStyle.colors.textPrimary.copy(alpha = 0.10f), RoundedCornerShape(4.dp))
            )
        }
        val displayLabel = remember(item.token) {
            LightspeedShortcutManager.resolveLabel(context, item.token)
        }
        Column(
            modifier = Modifier.weight(1f).fillMaxHeight(),
            verticalArrangement = Arrangement.spacedBy(1.dp, Alignment.CenterVertically)
        ) {
            Text(
                text = displayLabel,
                color = if (isChecked) dynamicPrimary else LightspeedStyle.colors.textPrimary,
                fontSize = 13.sp,
                fontWeight = if (isChecked) FontWeight.Bold else FontWeight.Normal,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = TextStyle(
                    platformStyle = PlatformTextStyle(includeFontPadding = false),
                    lineHeight = 16.sp
                )
            )
            Text(
                text = if (item.isPlugin) "Shortcut Creator Wizard" else item.token,
                color = LightspeedStyle.colors.textSecondary.copy(alpha = 0.40f),
                fontSize = 10.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = TextStyle(
                    platformStyle = PlatformTextStyle(includeFontPadding = false),
                    lineHeight = 12.sp
                )
            )
        }

        if (isChecked) {
            Box(
                modifier = Modifier
                    .background(dynamicPrimary, CircleShape)
                    .padding(horizontal = 8.dp, vertical = 3.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "SELECTED",
                    color = Color.Black,
                    fontSize = 9.sp,
                    fontWeight = FontWeight.ExtraBold
                )
            }
        }
    }
}
