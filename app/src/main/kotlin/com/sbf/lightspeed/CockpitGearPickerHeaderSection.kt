package com.sbf.lightspeed

import com.sbf.lightspeed.system.lsString

import com.sbf.lightspeed.R

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sbf.lightspeed.settings.DeckButton
import com.sbf.lightspeed.settings.DeckDialog
import com.sbf.lightspeed.settings.verticalScrollWithEdgeFade
import com.sbf.lightspeed.system.LightspeedHapticEngine
import com.sbf.lightspeed.system.LightspeedLocaleEngine
import com.sbf.lightspeed.ui.theme.LightspeedStyle
import com.sbf.lightspeed.ui.theme.LightspeedTokens
import kotlinx.coroutines.delay

/**
 * Top Header and Profile info for Cockpit Gear Picker.
 * Header subtitle rotates contextual Action Selection Menu tips; tapping opens the full tips list.
 */
@Composable
internal fun CockpitGearPickerHeaderRow(
    isSingleSelect: Boolean,
    singleSelectTitle: String,
    ringIndex: Int,
    isSplitMode: Boolean,
    profileName: String,
    selectedCount: Int,
    dynamicSecondary: Color,
    onCloseClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val lsColors = LightspeedStyle.colors
    val localeState = LightspeedLocaleEngine.state
    val isArabicScript = localeState.primary == LightspeedLocaleEngine.AppLanguage.AR
    val activeLayoutDir = LocalLayoutDirection.current
    val tipLayoutDir = if (isArabicScript) LayoutDirection.Rtl else activeLayoutDir

    val baseSubtitle = remember(isSingleSelect, isSplitMode, profileName, selectedCount, localeState) {
        if (isSingleSelect) {
            if (isSplitMode) lsString(R.string.picker_gear_tap_short) else lsString(R.string.picker_gear_tap_full)
        } else {
            lsString(R.string.picker_gear_profile, profileName, selectedCount)
        }
    }
    val resolvedTips = remember(baseSubtitle, localeState) {
        listOf(
            baseSubtitle,
            lsString(R.string.picker_tip_pin),
            lsString(R.string.picker_tip_activities),
            lsString(R.string.picker_tip_direct_call),
            lsString(R.string.picker_tip_accordion_mode),
            lsString(R.string.picker_tip_fast_scroll)
        )
    }

    var tipIndex by remember { mutableIntStateOf(0) }
    var showAllPickerTipsDialog by remember { mutableStateOf(false) }

    LaunchedEffect(resolvedTips.size) {
        while (true) {
            delay(5200L)
            tipIndex = (tipIndex + 1) % resolvedTips.size
        }
    }

    val safeTipIndex = tipIndex % resolvedTips.size

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(bottom = 12.dp),
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = if (isSingleSelect) singleSelectTitle else "Assign to Gear ${ringIndex + 1} (${if (ringIndex == 0) "Outer" else "Inner"})",
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                color = lsColors.textPrimary,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            CompositionLocalProvider(LocalLayoutDirection provides tipLayoutDir) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 2.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .clickable {
                            LightspeedHapticEngine.tick(context)
                            showAllPickerTipsDialog = true
                        }
                        .padding(vertical = 2.dp),
                    verticalAlignment = Alignment.Top,
                    horizontalArrangement = Arrangement.spacedBy(5.dp)
                ) {
                    Text(
                        text = "✦",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = dynamicSecondary
                    )
                    AnimatedContent(
                        targetState = resolvedTips[safeTipIndex],
                        transitionSpec = { fadeIn(tween(220)) togetherWith fadeOut(tween(160)) },
                        modifier = Modifier.weight(1f),
                        label = "picker_header_tip"
                    ) { tipText ->
                        Text(
                            text = tipText,
                            fontSize = 11.5.sp,
                            lineHeight = 14.5.sp,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            color = dynamicSecondary
                        )
                    }
                }
            }
        }
        IconButton(
            onClick = onCloseClick,
            modifier = Modifier
                .padding(top = 2.dp)
                .size(32.dp)
                .background(lsColors.textPrimary.copy(alpha = 0.08f), CircleShape)
        ) {
            Icon(Icons.Default.Close, contentDescription = "Close", tint = lsColors.textPrimary, modifier = Modifier.size(18.dp))
        }
    }

    if (showAllPickerTipsDialog) {
        DeckDialog(
            onDismissRequest = { showAllPickerTipsDialog = false },
            scrollable = false,
            title = {
                Text(
                    text = lsString(R.string.picker_tip_all_title),
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                CompositionLocalProvider(LocalLayoutDirection provides tipLayoutDir) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 320.dp)
                            .verticalScrollWithEdgeFade(topFeatherDp = 16.dp, bottomFeatherDp = 18.dp)
                            .padding(vertical = 4.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        resolvedTips.forEach { tip ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(lsColors.textPrimary.copy(alpha = 0.05f))
                                    .border(0.7.dp, dynamicSecondary.copy(alpha = 0.24f), RoundedCornerShape(10.dp))
                                    .padding(horizontal = 10.dp, vertical = 8.dp),
                                horizontalArrangement = Arrangement.spacedBy(7.dp),
                                verticalAlignment = Alignment.Top
                            ) {
                                Text(
                                    text = "✦",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = dynamicSecondary
                                )
                                Text(
                                    text = tip,
                                    fontSize = 11.5.sp,
                                    lineHeight = 16.sp,
                                    color = lsColors.textPrimary.copy(alpha = 0.90f),
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = {
                DeckButton(
                    text = lsString(R.string.ls_close),
                    onClick = { showAllPickerTipsDialog = false },
                    isActive = true
                )
            }
        )
    }
}

/**
 * Tactical search text field.
 */
@Composable
internal fun CockpitGearPickerSearchBar(
    query: String,
    onQueryChange: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val lsColors = LightspeedStyle.colors
    TextField(
        value = query,
        onValueChange = onQueryChange,
        placeholder = { Text(lsString(R.string.picker_gear_search), color = lsColors.textPrimary.copy(alpha = 0.45f), fontSize = 13.sp) },
        leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = lsColors.textPrimary.copy(alpha = 0.6f), modifier = Modifier.size(18.dp)) },
        trailingIcon = {
            if (query.isNotEmpty()) {
                IconButton(onClick = { onQueryChange("") }) {
                    Icon(Icons.Default.Close, contentDescription = "Clear", tint = lsColors.textPrimary.copy(alpha = 0.6f), modifier = Modifier.size(16.dp))
                }
            }
        },
        colors = TextFieldDefaults.colors(
            focusedContainerColor = lsColors.textPrimary.copy(alpha = 0.08f),
            unfocusedContainerColor = lsColors.textPrimary.copy(alpha = 0.05f),
            focusedTextColor = lsColors.textPrimary,
            unfocusedTextColor = lsColors.textPrimary,
            focusedIndicatorColor = Color.Transparent,
            unfocusedIndicatorColor = Color.Transparent,
            disabledIndicatorColor = Color.Transparent
        ),
        shape = RoundedCornerShape(16.dp),
        modifier = modifier
            .fillMaxWidth()
            .padding(bottom = 8.dp),
        singleLine = true
    )
}

/**
 * None / Clear action row for single-select mode.
 */
@Composable
internal fun CockpitGearPickerNoneRow(
    isSelected: Boolean,
    dynamicPrimary: Color,
    onSelectNone: () -> Unit,
    modifier: Modifier = Modifier
) {
    val lsColors = LightspeedStyle.colors
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(bottom = 8.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(if (isSelected) dynamicPrimary.copy(alpha = 0.28f) else lsColors.textPrimary.copy(alpha = 0.04f))
            .border(1.dp, if (isSelected) dynamicPrimary.copy(alpha = 0.65f) else lsColors.textPrimary.copy(alpha = 0.08f), RoundedCornerShape(12.dp))
            .clickable { onSelectNone() }
            .padding(horizontal = 12.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(Icons.Default.Close, contentDescription = null, tint = LightspeedTokens.StatusErrorSoft, modifier = Modifier.size(20.dp))
        Spacer(modifier = Modifier.width(10.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(lsString(R.string.picker_none_action), color = lsColors.textPrimary, fontSize = 13.5.sp, fontWeight = FontWeight.SemiBold)
            Text(lsString(R.string.picker_none_action_sub), color = lsColors.textSecondary.copy(alpha = 0.5f), fontSize = 10.5.sp)
        }
        if (isSelected) {
            Box(
                modifier = Modifier
                    .background(dynamicPrimary, CircleShape)
                    .padding(horizontal = 8.dp, vertical = 3.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(lsString(R.string.cc_common_active), color = Color.Black, fontSize = 9.sp, fontWeight = FontWeight.ExtraBold)
            }
        }
    }
}

/**
 * Advisory Warning Banner for Deep Activities.
 */
@Composable
internal fun CockpitGearPickerWarningBanner(modifier: Modifier = Modifier) {
    val lsColors = LightspeedStyle.colors
    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(bottom = 8.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(lsColors.accentWarn.copy(alpha = 0.12f))
            .border(1.dp, lsColors.accentWarn.copy(alpha = 0.35f), RoundedCornerShape(12.dp))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 7.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(imageVector = Icons.Default.WarningAmber, contentDescription = null, tint = lsColors.accentWarn, modifier = Modifier.size(16.dp))
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = lsString(R.string.picker_deep_warn),
                fontSize = 11.sp,
                color = LightspeedTokens.StatusGoldBadge,
                lineHeight = 14.sp
            )
        }
    }
}

/**
 * Bottom Save / Cancel action bar for multi-select gear ring payload configuration.
 */
@Composable
internal fun CockpitGearPickerBottomBar(
    selectedCount: Int,
    dynamicPrimary: Color,
    onCancel: () -> Unit,
    onSave: () -> Unit,
    modifier: Modifier = Modifier
) {
    val lsColors = LightspeedStyle.colors
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(top = 14.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        OutlinedButton(
            onClick = onCancel,
            modifier = Modifier
                .weight(1f)
                .height(46.dp),
            shape = RoundedCornerShape(12.dp),
            border = BorderStroke(1.dp, lsColors.textPrimary.copy(alpha = 0.20f))
        ) {
            Text(lsString(R.string.cc_common_cancel), color = lsColors.textPrimary.copy(alpha = 0.8f), fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
        }

        Button(
            onClick = onSave,
            colors = ButtonDefaults.buttonColors(containerColor = dynamicPrimary),
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier
                .weight(1f)
                .height(46.dp)
        ) {
            Text(lsString(R.string.picker_save_cnt, selectedCount), color = lsColors.textPrimary, fontWeight = FontWeight.Bold, fontSize = 13.sp)
        }
    }
}
