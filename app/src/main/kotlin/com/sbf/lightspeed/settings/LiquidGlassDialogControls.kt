package com.sbf.lightspeed.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import com.sbf.lightspeed.ui.theme.LocalDeckTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sbf.lightspeed.ui.theme.LightspeedStyle

@Composable
internal fun CalibrationSliderRow(
    title: String,
    subtitle: String,
    displayValue: String,
    value: Float,
    valueRange: ClosedFloatingPointRange<Float>,
    steps: Int = 0,
    onValueChange: (Float) -> Unit
) {
    val colorScheme = MaterialTheme.colorScheme

    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                Text(
                    text = title,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
                Text(
                    text = subtitle,
                    fontSize = 10.sp,
                    color = colorScheme.onSurfaceVariant.copy(alpha = 0.70f)
                )
            }
            Surface(
                shape = RoundedCornerShape(6.dp),
                color = colorScheme.primary.copy(alpha = 0.15f),
                border = androidx.compose.foundation.BorderStroke(1.dp, colorScheme.primary.copy(alpha = 0.35f))
            ) {
                Text(
                    text = displayValue,
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    color = colorScheme.primary,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                )
            }
        }

        DragOnlySlider(
            value = value,
            onValueChange = onValueChange,
            valueRange = valueRange,
            steps = steps,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

/**
 * Header Bar for the Theme Customization Dialog.
 */
@Composable
internal fun ThemeCustomizationHeader(
    displayName: String,
    onCloseClick: () -> Unit
) {
    Column {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Tune,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp)
                )
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = displayName,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = LightspeedStyle.colors.textPrimary
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "LIVE LAB",
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.sp,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier
                                .background(
                                    MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
                                    RoundedCornerShape(4.dp)
                                )
                                .padding(horizontal = 4.dp, vertical = 1.dp)
                        )
                    }
                    Text(
                        text = "Card surface reflects optical sliders in real-time",
                        fontSize = 10.5.sp,
                        color = LightspeedStyle.colors.textSecondary
                    )
                }
            }

            IconButton(
                onClick = onCloseClick,
                modifier = Modifier.size(32.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "Close",
                    tint = LightspeedStyle.colors.textPrimary.copy(alpha = 0.8f),
                    modifier = Modifier.size(20.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(10.dp))
        HorizontalDivider(color = LightspeedStyle.colors.textPrimary.copy(alpha = 0.1f), thickness = 0.8.dp)
        Spacer(modifier = Modifier.height(8.dp))
    }
}

/**
 * Bottom Action Bar for Defaults & Save/Apply buttons.
 */
@Composable
internal fun ThemeCustomizationBottomBar(
    hasUnsavedChanges: Boolean,
    onRestoreDefaults: () -> Unit,
    onSaveAndApply: () -> Unit
) {
    Column {
        Spacer(modifier = Modifier.height(8.dp))
        HorizontalDivider(color = LightspeedStyle.colors.textPrimary.copy(alpha = 0.1f), thickness = 0.8.dp)
        Spacer(modifier = Modifier.height(10.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedButton(
                onClick = onRestoreDefaults,
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(12.dp),
                contentPadding = PaddingValues(vertical = 11.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.RestartAlt,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text("Defaults", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
            }

            Button(
                onClick = onSaveAndApply,
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary
                ),
                contentPadding = PaddingValues(vertical = 11.dp)
            ) {
                Text(
                    text = if (hasUnsavedChanges) "Save & Apply" else "Apply",
                    fontSize = 12.5.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

/**
 * Discard Unsaved Changes Warning Dialog.
 */
@Composable
internal fun ThemeDiscardChangesDialog(
    presetDisplayName: String,
    onConfirmDiscard: () -> Unit,
    onDismissRequest: () -> Unit
) {
    val lsColors = LightspeedStyle.colors
    val snapshot = LocalDeckTheme.current
    val cornerShape = RoundedCornerShape((snapshot.cornerRadiusDp * 0.7f).coerceIn(14f, 28f).dp)

    AlertDialog(
        onDismissRequest = onDismissRequest,
        shape = cornerShape,
        containerColor = lsColors.dialogSurface,
        icon = {
            Icon(
                imageVector = Icons.Default.Warning,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.error
            )
        },
        title = {
            Text("Discard Changes?", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = lsColors.textPrimary)
        },
        text = {
            Text(
                "You have unsaved optical tuning adjustments for $presetDisplayName. Discard all modifications and exit?",
                fontSize = 13.sp,
                color = lsColors.textSecondary
            )
        },
        confirmButton = {
            TextButton(onClick = onConfirmDiscard) {
                Text("Discard", color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismissRequest) {
                Text("Keep Editing", color = MaterialTheme.colorScheme.primary)
            }
        }
    )
}

/**
 * Modal naming dialog for saving or renaming a theme profile.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ThemeProfileNamingDialog(
    title: String,
    initialName: String,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var name by remember { mutableStateOf(initialName) }
    val lsColors = LightspeedStyle.colors
    val snapshot = LocalDeckTheme.current
    val cornerShape = RoundedCornerShape((snapshot.cornerRadiusDp * 0.7f).coerceIn(14f, 28f).dp)

    AlertDialog(
        onDismissRequest = onDismiss,
        shape = cornerShape,
        containerColor = lsColors.dialogSurface,
        title = {
            Text(text = title, fontWeight = FontWeight.Bold, fontSize = 15.sp, color = lsColors.textPrimary)
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "Enter a memorable name for this profile look:",
                    fontSize = 12.sp,
                    color = lsColors.textSecondary
                )
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    placeholder = { Text("e.g. Ultra Gloss, Obsidian Tint") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (name.isNotBlank()) onConfirm(name.trim())
                },
                enabled = name.isNotBlank()
            ) {
                Text("Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = lsColors.textSecondary)
            }
        }
    )
}
