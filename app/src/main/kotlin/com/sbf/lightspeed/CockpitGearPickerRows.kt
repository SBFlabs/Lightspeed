package com.sbf.lightspeed

import android.content.Context
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
import androidx.compose.material.icons.filled.UnfoldMore
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
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
import com.sbf.lightspeed.system.LightspeedShortcutManager

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun PickerSystemHeaderRow(
    item: PickerRowItem.SystemHeader,
    dynamicSecondary: Color,
    onClick: () -> Unit,
    onToggleAll: () -> Unit,
    onToggleAllLongClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp)
            .background(Color.White.copy(alpha = 0.07f), RoundedCornerShape(10.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = "⚡ System Actions (${item.count})",
            color = Color.White,
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.weight(1f)
        )
        if (item.isExpanded) {
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = 0.1f))
                    .combinedClickable(
                        onClick = onToggleAll,
                        onLongClick = onToggleAllLongClick
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Default.UnfoldMore,
                    contentDescription = "Expand/Collapse All",
                    tint = Color.White,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
        Spacer(modifier = Modifier.width(8.dp))
        Icon(
            androidx.compose.material.icons.Icons.Default.ArrowDropDown,
            contentDescription = null,
            tint = dynamicSecondary,
            modifier = Modifier
                .size(20.dp)
                .rotate(if (item.isExpanded) 180f else 0f)
        )
    }
}

@Composable
fun PickerSystemCategoryHeaderRow(
    item: PickerRowItem.SystemCategoryHeader,
    dynamicPrimary: Color,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 6.dp, end = 2.dp, top = 6.dp, bottom = 2.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(dynamicPrimary.copy(alpha = 0.08f))
            .border(1.dp, dynamicPrimary.copy(alpha = 0.20f), RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = "${item.title} (${item.count})",
            color = dynamicPrimary,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.weight(1f)
        )
        Icon(
            Icons.Default.ArrowDropDown,
            contentDescription = null,
            tint = dynamicPrimary,
            modifier = Modifier
                .size(18.dp)
                .rotate(if (item.isExpanded) 180f else 0f)
        )
    }
}

@Composable
fun PickerSystemActionRow(
    item: PickerRowItem.SystemAction,
    isChecked: Boolean,
    dynamicPrimary: Color,
    dynamicSecondary: Color,
    onSelect: () -> Unit,
    onCustomizeToggle: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(48.dp)
            .padding(start = 14.dp, top = 2.dp, bottom = 2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Primary Action Board (Left)
        Row(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight()
                .clip(
                    RoundedCornerShape(
                        topStart = 10.dp,
                        bottomStart = 10.dp,
                        topEnd = if (item.isCustomizable) 4.dp else 10.dp,
                        bottomEnd = if (item.isCustomizable) 4.dp else 10.dp
                    )
                )
                .background(
                    color = if (isChecked) dynamicPrimary.copy(alpha = 0.28f) else Color.White.copy(alpha = 0.03f)
                )
                .border(
                    width = 1.dp,
                    color = if (isChecked) dynamicPrimary.copy(alpha = 0.65f) else Color.White.copy(alpha = 0.05f),
                    shape = RoundedCornerShape(
                        topStart = 10.dp,
                        bottomStart = 10.dp,
                        topEnd = if (item.isCustomizable) 4.dp else 10.dp,
                        bottomEnd = if (item.isCustomizable) 4.dp else 10.dp
                    )
                )
                .clickable(onClick = onSelect)
                .padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight(),
                verticalArrangement = Arrangement.spacedBy(1.dp, Alignment.CenterVertically)
            ) {
                Text(
                    text = item.label,
                    color = if (isChecked) dynamicPrimary else Color.White,
                    fontSize = 13.sp,
                    fontWeight = if (isChecked) FontWeight.Bold else FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = TextStyle(
                        platformStyle = PlatformTextStyle(includeFontPadding = false),
                        lineHeight = 16.sp
                    )
                )
                Text(
                    text = item.token,
                    color = Color.LightGray.copy(alpha = 0.40f),
                    fontSize = 10.sp,
                    maxLines = 1,
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
                        .padding(horizontal = 8.dp, vertical = 2.5.dp),
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

        // Customization Screwdriver Button (Right)
        if (item.isCustomizable) {
            Spacer(modifier = Modifier.width(3.dp))
            Box(
                modifier = Modifier
                    .width(42.dp)
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(topStart = 4.dp, bottomStart = 4.dp, topEnd = 10.dp, bottomEnd = 10.dp))
                    .background(
                        if (item.isExpanded) dynamicSecondary.copy(alpha = 0.35f)
                        else dynamicSecondary.copy(alpha = 0.12f)
                    )
                    .border(
                        width = 1.dp,
                        color = if (item.isExpanded) dynamicSecondary.copy(alpha = 0.65f) else dynamicSecondary.copy(alpha = 0.20f),
                        shape = RoundedCornerShape(topStart = 4.dp, bottomStart = 4.dp, topEnd = 10.dp, bottomEnd = 10.dp)
                    )
                    .clickable(onClick = onCustomizeToggle),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = ScrewdriverVector,
                    contentDescription = "Customize",
                    tint = if (item.isExpanded) dynamicSecondary else dynamicSecondary.copy(alpha = 0.9f),
                    modifier = Modifier.size(17.dp)
                )
            }
        }
    }
}

@Composable
fun PickerCustomizationOptionRow(
    item: PickerRowItem.SystemCustomizationOption,
    dynamicSecondary: Color,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 24.dp, end = 2.dp, top = 2.dp, bottom = 2.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(
                if (item.isSelected) dynamicSecondary.copy(alpha = 0.22f)
                else Color.White.copy(alpha = 0.04f)
            )
            .border(
                width = 1.dp,
                color = if (item.isSelected) dynamicSecondary.copy(alpha = 0.65f) else Color.White.copy(alpha = 0.06f),
                shape = RoundedCornerShape(8.dp)
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(1.dp, Alignment.CenterVertically)
        ) {
            Text(
                text = item.title,
                color = if (item.isSelected) dynamicSecondary else Color.White,
                fontSize = 12.5.sp,
                fontWeight = if (item.isSelected) FontWeight.Bold else FontWeight.Medium,
                style = TextStyle(
                    platformStyle = PlatformTextStyle(includeFontPadding = false),
                    lineHeight = 15.sp
                )
            )
            Text(
                text = item.subtitle,
                color = Color.LightGray.copy(alpha = 0.55f),
                fontSize = 10.sp,
                style = TextStyle(
                    platformStyle = PlatformTextStyle(includeFontPadding = false),
                    lineHeight = 12.sp
                )
            )
        }
        if (item.isSelected) {
            Box(
                modifier = Modifier
                    .background(dynamicSecondary, CircleShape)
                    .padding(horizontal = 7.dp, vertical = 2.5.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "ACTIVE",
                    color = Color.Black,
                    fontSize = 8.sp,
                    fontWeight = FontWeight.ExtraBold
                )
            }
        }
    }
}

@Composable
fun PickerCustomizationSliderRow(
    item: PickerRowItem.SystemCustomizationSlider,
    dynamicSecondary: Color,
    onValueChange: (Float) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 24.dp, end = 2.dp, top = 3.dp, bottom = 3.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(Color.White.copy(alpha = 0.04f))
            .border(
                width = 1.dp,
                color = Color.White.copy(alpha = 0.06f),
                shape = RoundedCornerShape(8.dp)
            )
            .padding(horizontal = 12.dp, vertical = 8.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(1.dp)
            ) {
                Text(
                    text = item.title,
                    color = Color.White,
                    fontSize = 12.5.sp,
                    fontWeight = FontWeight.SemiBold,
                    style = TextStyle(
                        platformStyle = PlatformTextStyle(includeFontPadding = false),
                        lineHeight = 15.sp
                    )
                )
                Text(
                    text = item.subtitle,
                    color = Color.LightGray.copy(alpha = 0.55f),
                    fontSize = 10.sp,
                    style = TextStyle(
                        platformStyle = PlatformTextStyle(includeFontPadding = false),
                        lineHeight = 12.sp
                    )
                )
            }
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .background(dynamicSecondary.copy(alpha = 0.2f))
                    .border(1.dp, dynamicSecondary.copy(alpha = 0.5f), RoundedCornerShape(6.dp))
                    .padding(horizontal = 7.dp, vertical = 2.5.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = item.formatValue(item.value),
                    color = dynamicSecondary,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    style = TextStyle(
                        platformStyle = PlatformTextStyle(includeFontPadding = false)
                    )
                )
            }
        }
        Spacer(modifier = Modifier.height(4.dp))
        Slider(
            value = item.value,
            onValueChange = onValueChange,
            valueRange = item.range,
            steps = item.steps,
            colors = SliderDefaults.colors(
                thumbColor = dynamicSecondary,
                activeTrackColor = dynamicSecondary,
                inactiveTrackColor = Color.White.copy(alpha = 0.15f)
            ),
            modifier = Modifier
                .fillMaxWidth()
                .height(30.dp)
        )
    }
}

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
                    else if (item.isPinned) Color(0x18FFD700)
                    else Color.White.copy(alpha = 0.06f)
                )
                .border(
                    width = 1.dp,
                    color = if (item.isPinned) Color(0x66FFD700)
                    else if (isAppSelected) dynamicPrimary.copy(alpha = 0.65f)
                    else Color.White.copy(alpha = 0.07f),
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
            val iconBmp = LightspeedActionRegistry.getIconBitmap(context, item.packageName)
            if (iconBmp != null) {
                Image(
                    bitmap = iconBmp.asImageBitmap(),
                    contentDescription = null,
                    modifier = Modifier.size(26.dp).padding(end = 8.dp)
                )
            } else {
                Box(
                    modifier = Modifier
                        .size(26.dp)
                        .padding(end = 8.dp)
                        .background(Color.White.copy(alpha = 0.12f), RoundedCornerShape(5.dp))
                )
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
                            tint = Color(0xFFFFD700),
                            modifier = Modifier.size(14.dp).padding(end = 4.dp)
                        )
                    }
                    Text(
                        text = item.appName,
                        color = if (isAppSelected) dynamicPrimary else if (item.isPinned) Color(0xFFFFF176) else Color.White,
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
            .background(Color.White.copy(alpha = 0.04f), RoundedCornerShape(8.dp))
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

@Composable
fun PickerShortcutActionRow(
    item: PickerRowItem.ShortcutAction,
    isChecked: Boolean,
    context: Context,
    dynamicPrimary: Color,
    onSelect: () -> Unit,
    onLaunchWizard: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(48.dp)
            .padding(start = 18.dp, top = 2.dp, bottom = 2.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(
                color = if (isChecked) dynamicPrimary.copy(alpha = 0.28f) else Color.White.copy(alpha = 0.03f)
            )
            .border(
                width = 1.dp,
                color = if (isChecked) dynamicPrimary.copy(alpha = 0.65f) else Color.White.copy(alpha = 0.05f),
                shape = RoundedCornerShape(10.dp)
            )
            .clickable {
                if (item.isPlugin && !isChecked) onLaunchWizard() else onSelect()
            }
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        val shortcutBmp = remember(item.token) {
            LightspeedShortcutManager.resolveIconBitmap(context, item.token)
        }
        if (shortcutBmp != null) {
            Image(
                bitmap = shortcutBmp.asImageBitmap(),
                contentDescription = null,
                modifier = Modifier.size(22.dp).padding(end = 8.dp)
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
                color = if (isChecked) dynamicPrimary else Color.White,
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
                color = Color.LightGray.copy(alpha = 0.40f),
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
