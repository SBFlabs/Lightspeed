package com.sbf.lightspeed

import com.sbf.lightspeed.system.logSwallowed
import com.sbf.lightspeed.system.safeReloadPreferences

import com.sbf.lightspeed.system.defaultPrefs
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sbf.lightspeed.system.IconPackInfo
import com.sbf.lightspeed.system.LightspeedIconManager
import com.sbf.lightspeed.system.LightspeedPreferences
import com.sbf.lightspeed.system.LightspeedShortcutManager
import com.sbf.lightspeed.system.lsString
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.sbf.lightspeed.settings.DeckDialog
import com.sbf.lightspeed.settings.LightspeedInfinityDialog
import com.sbf.lightspeed.ui.theme.LightspeedStyle
import com.sbf.lightspeed.ui.theme.LightspeedTheme

/** Cockpit dialog content: rename-gear and edit-item composables (split from CockpitDialogActivity.kt). */
@Composable
fun RenameGearContent(
    setId: String,
    currentName: String,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val prefs = remember { context.defaultPrefs() }
    var gearNameText by remember { mutableStateOf(currentName) }

    Column(modifier = Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                Icons.Default.DriveFileRenameOutline,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(24.dp)
            )
            Spacer(modifier = Modifier.width(12.dp))
            Text(
                lsString(R.string.edit_rename_gear_title),
                fontWeight = FontWeight.Bold,
                fontSize = 19.sp,
                color = MaterialTheme.colorScheme.onSurface
            )
        }

        Spacer(modifier = Modifier.height(18.dp))

        OutlinedTextField(
            value = gearNameText,
            onValueChange = { gearNameText = it },
            label = { Text(lsString(R.string.edit_gear_name_lbl)) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = MaterialTheme.colorScheme.primary,
                unfocusedBorderColor = MaterialTheme.colorScheme.outline
            )
        )

        Spacer(modifier = Modifier.height(24.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End
        ) {
            TextButton(onClick = onDismiss) {
                Text(lsString(R.string.common_cancel), color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Spacer(modifier = Modifier.width(8.dp))
            Button(
                onClick = {
                    val clean = gearNameText.trim()
                    if (clean.isNotBlank()) {
                        prefs.edit().putString("gear_set_${setId}_name", clean).apply()
                        safeReloadPreferences()
                        Toast.makeText(context, lsString(R.string.edit_renamed_toast, clean), Toast.LENGTH_SHORT).show()
                    }
                    onDismiss()
                },
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary
                ),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text(lsString(R.string.ed_btn_save), fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
fun EditItemContent(
    token: String,
    openIconBrowserImmediately: Boolean = false,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val prefs = remember { context.defaultPrefs() }
    val safeKey = remember(token) { token.hashCode().toString() }

    val initialName = remember(token) {
        prefs.getString("custom_label_$safeKey", null) ?: LightspeedShortcutManager.resolveLabel(context, token)
    }
    var customLabelText by remember { mutableStateOf(initialName) }
    var selectedBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var iconKeyTrigger by remember { mutableStateOf(0) }
    var showSourceSelector by remember { mutableStateOf(false) }
    var showIconPackBrowser by remember { mutableStateOf(openIconBrowserImmediately) }

    val currentDrawable = remember(token, selectedBitmap, iconKeyTrigger) {
        if (selectedBitmap != null) {
            BitmapDrawable(context.resources, selectedBitmap)
        } else {
            LightspeedIconManager.getIconDrawable(context, token)
        }
    }

    val coroutineScope = rememberCoroutineScope()

    val imagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            coroutineScope.launch(Dispatchers.IO) {
                try {
                    context.contentResolver.openInputStream(uri)?.use { stream ->
                        val bmp = BitmapFactory.decodeStream(stream)
                        if (bmp != null) {
                            val size = 128
                            val scaled = Bitmap.createScaledBitmap(bmp, size, size, true)
                            withContext(Dispatchers.Main) {
                                selectedBitmap = scaled
                            }
                        }
                    }
                } catch (e: Exception) {
                    withContext(Dispatchers.Main) {
                        Toast.makeText(context, lsString(R.string.edit_load_img_err, e.message ?: ""), Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }
    }

    if (showIconPackBrowser) {
        IconPackBrowserModal(
            onDismiss = { showIconPackBrowser = false },
            onIconSelected = { bmp ->
                selectedBitmap = bmp
                showIconPackBrowser = false
                if (openIconBrowserImmediately) {
                    LightspeedIconManager.saveCustomShortcutBitmap(context, token, bmp)
                    safeReloadPreferences()
                    Toast.makeText(context, lsString(R.string.edit_icon_updated), Toast.LENGTH_SHORT).show()
                    onDismiss()
                }
            }
        )
        return
    }

    val isLandscape = LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE

    val iconPreviewAndPickers: @Composable () -> Unit = {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Icon Preview Card with Edit Badge
            Box(
                modifier = Modifier
                    .size(if (isLandscape) 72.dp else 84.dp)
                    .clip(RoundedCornerShape(22.dp))
                    .background(MaterialTheme.colorScheme.surfaceContainerHighest)
                    .border(
                        1.5.dp,
                        MaterialTheme.colorScheme.primary.copy(alpha = 0.6f),
                        RoundedCornerShape(22.dp)
                    )
                    .clickable { showSourceSelector = true },
                contentAlignment = Alignment.Center
            ) {
                val bmp = remember(currentDrawable) {
                    currentDrawable?.let { LightspeedIconManager.convertDrawableToBitmap(it) }
                }

                if (bmp != null) {
                    Image(
                        bitmap = bmp.asImageBitmap(),
                        contentDescription = "Item Icon",
                        modifier = Modifier.size(if (isLandscape) 48.dp else 56.dp)
                    )
                }

                // Edit overlay badge
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(4.dp)
                        .size(24.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Default.Image,
                        contentDescription = "Change Icon",
                        tint = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.size(14.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.padding(top = 4.dp)
            ) {
                OutlinedButton(
                    onClick = { imagePickerLauncher.launch("image/*") },
                    shape = RoundedCornerShape(10.dp),
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp)
                ) {
                    Icon(Icons.Default.Photo, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(lsString(R.string.edit_btn_gallery), fontSize = 12.sp)
                }
                FilledTonalButton(
                    onClick = { showIconPackBrowser = true },
                    shape = RoundedCornerShape(10.dp),
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp)
                ) {
                    Icon(Icons.Default.Category, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(lsString(R.string.edit_btn_icon_packs), fontSize = 12.sp)
                }
            }
        }
    }

    val labelAndActions: @Composable () -> Unit = {
        Column(modifier = Modifier.fillMaxWidth()) {
            OutlinedTextField(
                value = customLabelText,
                onValueChange = { customLabelText = it },
                label = { Text(lsString(R.string.edit_display_name)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                    unfocusedBorderColor = MaterialTheme.colorScheme.outline
                )
            )

            Spacer(modifier = Modifier.height(if (isLandscape) 8.dp else 12.dp))

            // Reset to Default Button
            TextButton(
                onClick = {
                    prefs.edit().remove("custom_label_$safeKey").apply()
                    // clearManualOverride removes the override flag, clears all IconManager in-memory
                    // caches for this token, and deletes the disk file — fully symmetric with Save.
                    LightspeedIconManager.clearManualOverride(context, token)
                    LightspeedShortcutManager.clearMemoryCache()
                    selectedBitmap = null
                    customLabelText = LightspeedShortcutManager.resolveLabel(context, token)
                    iconKeyTrigger++
                    safeReloadPreferences()
                    Toast.makeText(context, lsString(R.string.edit_reset_toast), Toast.LENGTH_SHORT).show()
                },
                modifier = Modifier.align(Alignment.Start)
            ) {
                Icon(
                    Icons.Default.Refresh,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(lsString(R.string.edit_reset_default), color = MaterialTheme.colorScheme.error, fontSize = 12.5.sp)
            }

            Spacer(modifier = Modifier.height(if (isLandscape) 6.dp else 12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                TextButton(onClick = onDismiss) {
                    Text(lsString(R.string.common_cancel), color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Spacer(modifier = Modifier.width(8.dp))
                Button(
                    onClick = {
                        val cleanLabel = customLabelText.trim()
                        if (cleanLabel.isNotBlank()) {
                            prefs.edit().putString("custom_label_$safeKey", cleanLabel).apply()
                        }
                        if (selectedBitmap != null) {
                            // saveCustomShortcutBitmap is the canonical write path:
                            // it updates all in-memory caches, persists to disk, and records the backup override flag.
                            LightspeedIconManager.saveCustomShortcutBitmap(context, token, selectedBitmap!!)
                        }
                        safeReloadPreferences()
                        Toast.makeText(context, lsString(R.string.edit_item_saved), Toast.LENGTH_SHORT).show()
                        onDismiss()
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary
                    ),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text(lsString(R.string.ed_btn_save), fontWeight = FontWeight.Bold)
                }
            }
        }
    }

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                Icons.Default.Edit,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(24.dp)
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column {
                Text(
                    lsString(R.string.edit_customize_title),
                    fontWeight = FontWeight.Bold,
                    fontSize = 19.sp,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    lsString(R.string.edit_customize_sub),
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        Spacer(modifier = Modifier.height(if (isLandscape) 12.dp else 20.dp))

        if (isLandscape) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(20.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                iconPreviewAndPickers()
                Box(modifier = Modifier.weight(1f)) {
                    labelAndActions()
                }
            }
        } else {
            iconPreviewAndPickers()
            Spacer(modifier = Modifier.height(18.dp))
            labelAndActions()
        }
    }

    if (showSourceSelector) {
        DeckDialog(
            onDismissRequest = { showSourceSelector = false },
            title = { Text(lsString(R.string.edit_icon_source_title)) },
            text = { Text(lsString(R.string.edit_icon_source_msg)) },
            confirmButton = {
                TextButton(onClick = {
                    showSourceSelector = false
                    showIconPackBrowser = true
                }) {
                    Text(lsString(R.string.edit_icon_src_packs))
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    showSourceSelector = false
                    imagePickerLauncher.launch("image/*")
                }) {
                    Text(lsString(R.string.edit_icon_src_gallery))
                }
            }
        )
    }
}
