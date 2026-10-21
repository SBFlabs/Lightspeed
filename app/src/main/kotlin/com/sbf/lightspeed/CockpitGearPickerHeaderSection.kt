package com.sbf.lightspeed

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
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sbf.lightspeed.ui.theme.LightspeedStyle
import com.sbf.lightspeed.ui.theme.LightspeedTokens

/**
 * Top Header and Profile info for Cockpit Gear Picker.
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
    val lsColors = LightspeedStyle.colors
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(bottom = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = if (isSingleSelect) singleSelectTitle else "Assign to Gear ${ringIndex + 1} (${if (ringIndex == 0) "Outer" else "Inner"})",
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                color = lsColors.textPrimary
            )
            Text(
                text = if (isSingleSelect) {
                    if (isSplitMode) "Tap any app or shortcut" else "Tap any app, deep shortcut or system action to assign"
                } else "Profile: $profileName ($selectedCount selected)",
                fontSize = 12.sp,
                color = dynamicSecondary
            )
        }
        IconButton(
            onClick = onCloseClick,
            modifier = Modifier
                .size(32.dp)
                .background(lsColors.textPrimary.copy(alpha = 0.08f), CircleShape)
        ) {
            Icon(Icons.Default.Close, contentDescription = "Close", tint = lsColors.textPrimary, modifier = Modifier.size(18.dp))
        }
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
        placeholder = { Text("Search apps, shortcuts & actions...", color = lsColors.textPrimary.copy(alpha = 0.45f), fontSize = 13.sp) },
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
            Text("None (No Action)", color = lsColors.textPrimary, fontSize = 13.5.sp, fontWeight = FontWeight.SemiBold)
            Text("Clear and disable action for this gesture", color = lsColors.textSecondary.copy(alpha = 0.5f), fontSize = 10.5.sp)
        }
        if (isSelected) {
            Box(
                modifier = Modifier
                    .background(dynamicPrimary, CircleShape)
                    .padding(horizontal = 8.dp, vertical = 3.dp),
                contentAlignment = Alignment.Center
            ) {
                Text("ACTIVE", color = Color.Black, fontSize = 9.sp, fontWeight = FontWeight.ExtraBold)
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
    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(bottom = 8.dp),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = lsColors.accentWarn.copy(alpha = 0.12f)),
        border = BorderStroke(1.dp, lsColors.accentWarn.copy(alpha = 0.35f))
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
                text = "Deep activities bypass standard app entry points and may produce unpredictable results.",
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
            Text("CANCEL", color = lsColors.textPrimary.copy(alpha = 0.8f), fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
        }

        Button(
            onClick = onSave,
            colors = ButtonDefaults.buttonColors(containerColor = dynamicPrimary),
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier
                .weight(1f)
                .height(46.dp)
        ) {
            Text("SAVE ($selectedCount)", color = lsColors.textPrimary, fontWeight = FontWeight.Bold, fontSize = 13.sp)
        }
    }
}
