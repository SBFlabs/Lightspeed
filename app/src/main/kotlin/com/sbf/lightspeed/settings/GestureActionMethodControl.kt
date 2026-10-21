package com.sbf.lightspeed.settings

import android.content.Context
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.SwapVert
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sbf.lightspeed.LightspeedAccessibilityService
import com.sbf.lightspeed.system.ActionMethods
import com.sbf.lightspeed.system.logSwallowed
import com.sbf.lightspeed.ui.theme.LightspeedStyle

/**
 * Tactical Action Method selector (Split Screen, Close App, Scroll To Top/Bottom)
 * supporting per-method execution engines and app targeting via [AppAssignmentSheet].
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun GestureActionMethodControl(
    context: Context,
    currentRawValue: String,
    activeSnapshot: DeckThemeSnapshot,
    opticalColors: OpticalSurfaceColors,
    readableTint: Color
) {
    val lsColors = LightspeedStyle.colors
    var selectedMethods by remember(currentRawValue) {
        mutableStateOf(ActionMethods.getSelected(context, currentRawValue))
    }
    var showMethodMenu by remember { mutableStateOf(false) }
    var selectedActionMethodForAppPicker by remember { mutableStateOf<Pair<String, String>?>(null) }
    var methodUpdateTrigger by remember { mutableIntStateOf(0) }

    Box {
        DeckSubChip(
            text = "${ActionMethods.summaryLabel(currentRawValue, selectedMethods)} ▾",
            icon = Icons.Default.Tune,
            isActive = true,
            snapshot = activeSnapshot,
            onClick = { showMethodMenu = true }
        )

        DeckThemedDropdownMenu(
            expanded = showMethodMenu,
            onDismissRequest = { showMethodMenu = false },
            snapshotOverride = activeSnapshot
        ) {
            val options = ActionMethods.options(currentRawValue)
            options.forEach { opt ->
                val isSelected = selectedMethods.contains(opt.id)
                val isEnabled = ActionMethods.isOptionEnabled(opt.id)
                val assignedCount = run {
                    val dummy = methodUpdateTrigger
                    ActionMethods.getMethodApps(context, currentRawValue, opt.id).size
                }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .then(
                            if (isSelected) {
                                Modifier.background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f))
                            } else Modifier
                        )
                        .combinedClickable(
                            enabled = isEnabled,
                            onClick = {
                                if (isEnabled) {
                                    selectedMethods = ActionMethods.toggle(context, currentRawValue, opt.id)
                                    LightspeedActionRegistry.labelCache[currentRawValue] = resolveDynamicTokenLabel(context, currentRawValue)
                                    try { LightspeedAccessibilityService.instance?.reloadPreferences() } catch (e: Exception) { logSwallowed("GestureActionMethodControl", "ToggleMethod", e) }
                                    showMethodMenu = false
                                }
                            },
                            onLongClick = if (isEnabled) {
                                {
                                    showMethodMenu = false
                                    selectedActionMethodForAppPicker = Pair(currentRawValue, opt.id)
                                }
                            } else null
                        )
                        .padding(horizontal = 10.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = if (isSelected) "✓ ${opt.title}" else opt.title,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                            color = if (!isEnabled) MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                            else if (isSelected) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.onSurface
                        )
                        val subText = if (!isEnabled && opt.id == ActionMethods.SHIZUKU) {
                            "Off in Accessibility Service only mode"
                        } else if (assignedCount > 0) {
                            if (opt.subtitle.isNotEmpty()) "${opt.subtitle} • $assignedCount app(s) assigned"
                            else "$assignedCount app(s) assigned"
                        } else if (isEnabled) {
                            if (opt.subtitle.isNotEmpty()) "${opt.subtitle} • Long press to assign apps"
                            else "Long press to assign apps"
                        } else {
                            opt.subtitle
                        }
                        if (subText.isNotEmpty()) {
                            Text(
                                text = subText,
                                fontSize = 10.sp,
                                color = if (!isEnabled) MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.38f)
                                else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    if (assignedCount > 0) {
                        Spacer(modifier = Modifier.width(6.dp))
                        val countChipShape = RoundedCornerShape((activeSnapshot.cornerRadiusDp * 0.25f).coerceIn(4f, 6f).dp)
                        Box(
                            modifier = Modifier
                                .clip(countChipShape)
                                .border(
                                    BorderStroke(
                                        (activeSnapshot.borderWidthDp * 0.65f).coerceIn(0.7f, 1.dp.value).dp,
                                        opticalColors.borderColor.copy(alpha = 0.40f)
                                    ),
                                    countChipShape
                                )
                                .background(readableTint.copy(alpha = 0.16f))
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = "$assignedCount APPS",
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold,
                                color = readableTint
                            )
                        }
                    }
                }
            }
        }
    }

    if (selectedActionMethodForAppPicker != null) {
        val (actionToken, methodId) = selectedActionMethodForAppPicker!!
        val optTitle = ActionMethods.options(actionToken).firstOrNull { it.id == methodId }?.title ?: methodId
        val actionTitle = when (actionToken) {
            "system:scroll_to_bottom" -> "Scroll to Bottom"
            "system:scroll_to_top" -> "Scroll to Top"
            "system:split_screen" -> "Split Screen"
            "system:popup_window", "system:freeform" -> "Pop-up Window"
            "system:close_app" -> "Close App"
            else -> resolveDynamicTokenLabel(context, actionToken)
        }
        AppAssignmentSheet(
            context = context,
            title = "$actionTitle: $optTitle",
            subtitle = "Run $optTitle exclusively for selected apps",
            icon = Icons.Default.SwapVert,
            initialAssigned = remember(actionToken, methodId) { ActionMethods.getMethodApps(context, actionToken, methodId) },
            onSave = { pkgs ->
                ActionMethods.setMethodApps(context, actionToken, methodId, pkgs)
            },
            onDismiss = { selectedActionMethodForAppPicker = null },
            onUpdated = {
                methodUpdateTrigger++
                try { LightspeedAccessibilityService.instance?.reloadPreferences() } catch (e: Exception) { logSwallowed("GestureActionMethodControl", "AppAssignmentUpdated", e) }
            }
        )
    }
}
