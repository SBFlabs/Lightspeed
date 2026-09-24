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
import androidx.compose.material.icons.filled.PriorityHigh
import androidx.compose.material.icons.filled.UnfoldMore
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import android.content.Intent
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
import com.sbf.lightspeed.settings.DragOnlySlider
import com.sbf.lightspeed.settings.SliderCalibrationFlyoutDialog
import com.sbf.lightspeed.settings.LightspeedActionRegistry
import com.sbf.lightspeed.system.LightspeedHapticEngine
import com.sbf.lightspeed.system.LightspeedIconManager
import com.sbf.lightspeed.system.LightspeedPreferences
import com.sbf.lightspeed.system.LightspeedShortcutManager
import com.sbf.lightspeed.system.defaultPrefs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

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
        val title = com.sbf.lightspeed.system.LightspeedLanguageEngine.resolve(com.sbf.lightspeed.system.LightspeedVocabulary.Key.SYSTEM_ACTIONS)
        Text(
            text = "⚡ $title (${item.count})",
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
    val context = LocalContext.current
    var showTransientInfoDialog by remember { mutableStateOf(false) }

    if (showTransientInfoDialog) {
        val cautionAmber = Color(0xFFFFB300)
        AlertDialog(
            onDismissRequest = { showTransientInfoDialog = false },
            icon = {
                Icon(
                    imageVector = Icons.Default.PriorityHigh,
                    contentDescription = null,
                    tint = cautionAmber,
                    modifier = Modifier.size(28.dp)
                )
            },
            title = {
                Text(
                    text = "TRANSIENT GRAVITY OVERRIDES",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "• Why Transient Override?\nActions under Synthetic Gravity (such as 'Force Transient Sensor Portrait', 'Force Transient 360° Gyro', etc.) are manual overrides that yield automatically back to the Synthetic Gravity automation rules upon app switch or screen off.",
                        fontSize = 12.5.sp,
                        color = Color.LightGray.copy(alpha = 0.9f),
                        lineHeight = 17.sp
                    )
                    Text(
                        text = "• Yields to Automation:\nThis transient behavior exists so manual triggers yield automatically back to the per-app automation buckets configured in Central Command upon app switch or screen-off, preventing your device from being permanently hard-locked into a forced orientation.",
                        fontSize = 12.sp,
                        color = Color.LightGray.copy(alpha = 0.75f),
                        lineHeight = 16.sp
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        showTransientInfoDialog = false
                        val intent = Intent(context, com.sbf.lightspeed.settings.CentralCommandActivity::class.java).apply {
                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                            putExtra("target_section", "synthetic_gravity")
                            putExtra("target_tab", 1)
                        }
                        context.startActivity(intent)
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = dynamicPrimary)
                ) {
                    Text("OPEN GRAVITY ENGINE", color = Color.Black, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                }
            },
            dismissButton = {
                TextButton(onClick = { showTransientInfoDialog = false }) {
                    Text("DISMISS", color = Color.LightGray, fontSize = 12.sp)
                }
            },
            containerColor = Color(0xFF1B1F2B),
            shape = RoundedCornerShape(16.dp)
        )
    }

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
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "${item.title} (${item.count})",
                    color = dynamicPrimary,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold
                )
                if (item.categoryKey == "sys_orient") {
                    Spacer(modifier = Modifier.width(6.dp))
                    Box(
                        modifier = Modifier
                            .size(18.dp)
                            .clip(CircleShape)
                            .background(Color(0xFFFFB300).copy(alpha = 0.2f))
                            .border(0.8.dp, Color(0xFFFFB300).copy(alpha = 0.5f), CircleShape)
                            .clickable { showTransientInfoDialog = true },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.PriorityHigh,
                            contentDescription = "Transient Explanation",
                            tint = Color(0xFFFFB300),
                            modifier = Modifier.size(12.dp)
                        )
                    }
                }
            }
            if (item.subtitle != null) {
                Text(
                    text = item.subtitle,
                    color = dynamicPrimary.copy(alpha = 0.75f),
                    fontSize = 10.sp
                )
            }
        }
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
        if (item.optionKey.startsWith("gravity_bucket_assign:")) {
            Spacer(modifier = Modifier.width(6.dp))
            Box(
                modifier = Modifier
                    .background(if (item.isSelected) dynamicSecondary else Color.White.copy(alpha = 0.12f), RoundedCornerShape(6.dp))
                    .padding(horizontal = 7.dp, vertical = 3.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = if (item.isSelected) "CONFIGURED" else "CHOOSE APPS",
                    color = if (item.isSelected) Color.Black else Color.White,
                    fontSize = 8.5.sp,
                    fontWeight = FontWeight.ExtraBold
                )
            }
        } else if (item.isSelected) {
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
    val context = LocalContext.current
    val prefs = remember(context) { context.defaultPrefs() }
    var isFlyoutOpen by remember { mutableStateOf(false) }
    var isTapToJumpEnabled by remember {
        mutableStateOf(prefs.getBoolean("pref_slider_tap_to_jump_${item.prefKey}", false))
    }

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
                    .clickable {
                        LightspeedHapticEngine.tick(context)
                        isFlyoutOpen = true
                    }
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
        DragOnlySlider(
            value = item.value,
            onValueChange = onValueChange,
            valueRange = item.range,
            steps = item.steps,
            tapToJump = isTapToJumpEnabled,
            modifier = Modifier.fillMaxWidth()
        )
    }

    if (isFlyoutOpen) {
        val defaultValStr = when (item.prefKey) {
            LightspeedPreferences.KEY_BRIGHTNESS_SCRUB_RESOLUTION -> "32"
            LightspeedPreferences.KEY_VOLUME_SCRUB_RESOLUTION -> "100"
            else -> item.range.start.toInt().toString()
        }
        SliderCalibrationFlyoutDialog(
            title = item.title,
            sliderKey = item.prefKey,
            currentValueStr = item.value.toInt().toString(),
            defaultValueStr = defaultValStr,
            onValueTyped = { typed ->
                typed.split(" ").firstOrNull()?.filter { it.isDigit() }?.toFloatOrNull()?.let { f ->
                    onValueChange(f.coerceIn(item.range))
                }
            },
            onResetToDefault = {
                defaultValStr.toFloatOrNull()?.let { f ->
                    onValueChange(f.coerceIn(item.range))
                }
            },
            onSelectProfile = { profileValueStr ->
                profileValueStr.split(" ").firstOrNull()?.filter { it.isDigit() }?.toFloatOrNull()?.let { f ->
                    onValueChange(f.coerceIn(item.range))
                }
            },
            onDismiss = { isFlyoutOpen = false },
            prefs = prefs,
            context = context,
            isTapToJumpEnabled = isTapToJumpEnabled,
            onTapToJumpChanged = { enabled ->
                isTapToJumpEnabled = enabled
                prefs.edit().putBoolean("pref_slider_tap_to_jump_${item.prefKey}", enabled).apply()
            }
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
                        .background(Color.White.copy(alpha = 0.12f), RoundedCornerShape(6.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = item.appName.firstOrNull()?.uppercase() ?: "?",
                        color = Color.White.copy(alpha = 0.6f),
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
                    .background(Color.White.copy(alpha = 0.10f), RoundedCornerShape(4.dp))
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
