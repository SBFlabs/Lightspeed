package com.sbf.lightspeed

import android.content.Context
import android.graphics.Bitmap
import android.widget.Toast
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sbf.lightspeed.settings.DeckButton
import com.sbf.lightspeed.settings.DeckDialog
import com.sbf.lightspeed.system.CustomIconList
import com.sbf.lightspeed.system.CustomIconListRepository
import com.sbf.lightspeed.system.LightspeedPreferences
import com.sbf.lightspeed.system.defaultPrefs
import com.sbf.lightspeed.system.logSwallowed
import com.sbf.lightspeed.system.lsString
import com.sbf.lightspeed.ui.theme.LightspeedStyle

internal data class PendingCustomListIcon(
    val bitmap: Bitmap,
    val defaultLabel: String,
    val sourcePack: String,
    val sourceDrawable: String
)

@Composable
internal fun CreateCustomIconListDialog(
    context: Context,
    fieldColors: TextFieldColors,
    initialName: String = "",
    onCreated: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val lsColors = LightspeedStyle.colors
    var newListNameText by remember(initialName) { mutableStateOf(initialName) }

    DeckDialog(
        onDismissRequest = onDismiss,
        title = { Text(lsString(R.string.icon_br_create_title), fontWeight = FontWeight.Bold, color = lsColors.textPrimary) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    lsString(R.string.icon_br_create_desc),
                    fontSize = 12.5.sp,
                    color = lsColors.textSecondary
                )
                OutlinedTextField(
                    value = newListNameText,
                    onValueChange = { newListNameText = it },
                    label = { Text(lsString(R.string.icon_br_list_name)) },
                    placeholder = { Text(lsString(R.string.icon_br_fav_placeholder)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    colors = fieldColors
                )
            }
        },
        confirmButton = {
            DeckButton(
                onClick = {
                    val name = newListNameText.trim().ifBlank { lsString(R.string.icon_br_fav_placeholder) }
                    val created = CustomIconListRepository.createCustomList(context, name)
                    Toast.makeText(context, lsString(R.string.icon_br_created_toast, created.name), Toast.LENGTH_SHORT).show()
                    onCreated(created.id)
                },
                isActive = true
            ) {
                Text(lsString(R.string.icon_br_btn_create), fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            DeckButton(onClick = onDismiss) {
                Text(lsString(R.string.common_cancel), color = lsColors.textSecondary)
            }
        }
    )
}

@Composable
internal fun AddToCustomIconListDialog(
    context: Context,
    pending: PendingCustomListIcon,
    customLists: List<CustomIconList>,
    fieldColors: TextFieldColors,
    onSaved: () -> Unit,
    onDismiss: () -> Unit
) {
    val lsColors = LightspeedStyle.colors
    var labelInput by remember(pending) { mutableStateOf(pending.defaultLabel) }
    var targetListId by remember(pending, customLists) {
        mutableStateOf(customLists.firstOrNull()?.id ?: "")
    }
    var creatingInlineList by remember(pending, customLists) {
        mutableStateOf(customLists.isEmpty())
    }
    var inlineNewListName by remember(pending) { mutableStateOf(lsString(R.string.icon_br_fav_placeholder)) }

    DeckDialog(
        onDismissRequest = onDismiss,
        title = { Text(lsString(R.string.icon_br_add_title), fontWeight = FontWeight.Bold, color = lsColors.textPrimary) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Image(
                        bitmap = pending.bitmap.asImageBitmap(),
                        contentDescription = pending.defaultLabel,
                        modifier = Modifier.size(44.dp)
                    )
                    OutlinedTextField(
                        value = labelInput,
                        onValueChange = { labelInput = it },
                        label = { Text(lsString(R.string.icon_br_searchable_lbl)) },
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                        colors = fieldColors
                    )
                }

                if (customLists.isNotEmpty() && !creatingInlineList) {
                    Text(
                        lsString(R.string.icon_br_select_target),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = lsColors.textSecondary
                    )
                    customLists.forEach { cl ->
                        val selected = cl.id == targetListId
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .clickable { targetListId = cl.id }
                                .padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = selected,
                                onClick = { targetListId = cl.id },
                                colors = RadioButtonDefaults.colors(
                                    selectedColor = lsColors.accent,
                                    unselectedColor = lsColors.textSecondary
                                )
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("${cl.name} (${cl.items.size})", fontSize = 13.sp, color = lsColors.textPrimary)
                        }
                    }
                    DeckButton(onClick = { creatingInlineList = true }) {
                        Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(lsString(R.string.icon_br_create_new), fontSize = 12.sp)
                    }
                } else {
                    OutlinedTextField(
                        value = inlineNewListName,
                        onValueChange = { inlineNewListName = it },
                        label = { Text(lsString(R.string.icon_br_new_list_name)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        colors = fieldColors
                    )
                }
            }
        },
        confirmButton = {
            DeckButton(
                onClick = {
                    val fallbackName = lsString(R.string.icon_br_fav_placeholder)
                    val finalListId = if (creatingInlineList || targetListId.isBlank()) {
                        val created = CustomIconListRepository.createCustomList(
                            context,
                            inlineNewListName.trim().ifBlank { fallbackName }
                        )
                        created.id
                    } else {
                        targetListId
                    }
                    val finalLabel = labelInput.trim().ifBlank { pending.defaultLabel }
                    CustomIconListRepository.addIconToCustomList(
                        context = context,
                        listId = finalListId,
                        label = finalLabel,
                        bitmap = pending.bitmap
                    )
                    val listName = CustomIconListRepository.getCustomLists(context)
                        .firstOrNull { it.id == finalListId }?.name
                        ?: lsString(R.string.icon_br_custom_list_fallback)
                    Toast.makeText(
                        context,
                        lsString(R.string.icon_br_added_fmt, finalLabel, listName),
                        Toast.LENGTH_SHORT
                    ).show()
                    onSaved()
                },
                isActive = true
            ) {
                Text(lsString(R.string.icon_br_save_icon), fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            DeckButton(onClick = onDismiss) {
                Text(lsString(R.string.common_cancel), color = lsColors.textSecondary)
            }
        }
    )
}

@Composable
fun DeepActivityWarningContent(
    token: String,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val prefs = remember { context.defaultPrefs() }
    var doNotShowAgain by remember { mutableStateOf(false) }

    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Icon(
                imageVector = Icons.Default.WarningAmber,
                contentDescription = null,
                tint = LightspeedStyle.colors.accentWarn,
                modifier = Modifier.size(26.dp)
            )
            Text(
                text = lsString(R.string.deep_warn_title),
                fontWeight = FontWeight.Bold,
                fontSize = 18.sp,
                color = LightspeedStyle.colors.textPrimary
            )
        }

        Spacer(modifier = Modifier.height(14.dp))

        Text(
            text = lsString(R.string.deep_warn_body),
            fontSize = 13.5.sp,
            color = LightspeedStyle.colors.textSecondary.copy(alpha = 0.9f),
            lineHeight = 19.sp
        )

        Spacer(modifier = Modifier.height(16.dp))

        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                .clickable { doNotShowAgain = !doNotShowAgain }
                .padding(vertical = 4.dp)
        ) {
            Checkbox(
                checked = doNotShowAgain,
                onCheckedChange = { doNotShowAgain = it },
                colors = CheckboxDefaults.colors(
                    checkedColor = MaterialTheme.colorScheme.primary,
                    uncheckedColor = LightspeedStyle.colors.textDisabled
                )
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = lsString(R.string.deep_warn_dont_show),
                fontSize = 13.sp,
                color = LightspeedStyle.colors.textPrimary
            )
        }

        Spacer(modifier = Modifier.height(20.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End
        ) {
            DeckButton(onClick = onDismiss) {
                Text(lsString(R.string.common_cancel), color = LightspeedStyle.colors.textSecondary, fontWeight = FontWeight.SemiBold)
            }
            Spacer(modifier = Modifier.width(8.dp))
            DeckButton(
                onClick = {
                    if (doNotShowAgain) {
                        prefs.edit().putBoolean(LightspeedPreferences.KEY_SUPPRESS_DEEP_ACTIVITY_WARNING, true).apply()
                    }
                    try {
                        com.sbf.lightspeed.system.ActionDispatcher.executeDirect(context, token)
                    } catch (e: Exception) { logSwallowed("CockpitDialogActivity", "DeepActivityWarningContent:875", e) }
                    onDismiss()
                },
                isActive = true
            ) {
                Text(lsString(R.string.deep_warn_launch), fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
internal fun WireframeIconGridLoader(modifier: Modifier = Modifier) {
    val transition = rememberInfiniteTransition(label = "iconBrowserWireframe")
    val pulse by transition.animateFloat(
        initialValue = 0.24f,
        targetValue = 0.68f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 780),
            repeatMode = RepeatMode.Reverse
        ),
        label = "iconWireframeAlpha"
    )
    val accent = MaterialTheme.colorScheme.primary
    val tileFill = MaterialTheme.colorScheme.surfaceContainerHighest
    Column(
        modifier = modifier.padding(vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        repeat(3) { rowIdx ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                repeat(4) { colIdx ->
                    val phaseBoost = ((rowIdx + colIdx) % 2) * 0.14f
                    val boxAlpha = (pulse - phaseBoost).coerceIn(0.18f, 0.72f)
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(12.dp))
                            .background(tileFill.copy(alpha = boxAlpha * 0.45f))
                            .border(1.dp, accent.copy(alpha = boxAlpha * 0.55f), RoundedCornerShape(12.dp))
                            .padding(horizontal = 6.dp, vertical = 10.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(38.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .border(1.2.dp, accent.copy(alpha = boxAlpha), RoundedCornerShape(10.dp))
                                .background(accent.copy(alpha = boxAlpha * 0.16f))
                        )
                        Box(
                            modifier = Modifier
                                .fillMaxWidth(0.68f)
                                .height(6.dp)
                                .clip(RoundedCornerShape(3.dp))
                                .background(accent.copy(alpha = boxAlpha * 0.42f))
                        )
                    }
                }
            }
        }
    }
}
