package com.sbf.lightspeed

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.drawable.BitmapDrawable
import android.net.Uri
import android.os.Build
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DriveFileRenameOutline
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sbf.lightspeed.system.LightspeedIconManager
import com.sbf.lightspeed.system.LightspeedShortcutManager
import java.io.File

class CockpitDialogActivity : ComponentActivity() {

    companion object {
        const val ACTION_RENAME_GEAR = "com.sbf.lightspeed.action.RENAME_GEAR"
        const val ACTION_EDIT_ITEM = "com.sbf.lightspeed.action.EDIT_ITEM"

        const val EXTRA_SET_ID = "EXTRA_SET_ID"
        const val EXTRA_CURRENT_NAME = "EXTRA_CURRENT_NAME"
        const val EXTRA_TOKEN = "EXTRA_TOKEN"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val action = intent.action ?: ACTION_RENAME_GEAR

        setContent {
            val context = LocalContext.current
            val colorScheme = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                dynamicDarkColorScheme(context)
            } else {
                darkColorScheme(
                    primary = Color(0xFF90CAF9),
                    secondary = Color(0xFFCE93D8),
                    surface = Color(0xFF161B26),
                    surfaceVariant = Color(0xFF212836)
                )
            }

            MaterialTheme(colorScheme = colorScheme) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.55f))
                        .clickable(onClick = { finish() }),
                    contentAlignment = Alignment.Center
                ) {
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth(0.92f)
                            .clickable(enabled = false, onClick = {}),
                        shape = RoundedCornerShape(24.dp),
                        color = MaterialTheme.colorScheme.surfaceContainerHigh,
                        tonalElevation = 6.dp,
                        border = androidx.compose.foundation.BorderStroke(
                            1.dp,
                            MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                        )
                    ) {
                        Box(modifier = Modifier.padding(22.dp)) {
                            when (action) {
                                ACTION_RENAME_GEAR -> RenameGearContent(
                                    setId = intent.getStringExtra(EXTRA_SET_ID) ?: "0",
                                    currentName = intent.getStringExtra(EXTRA_CURRENT_NAME) ?: "SET 1",
                                    onDismiss = { finish() }
                                )
                                ACTION_EDIT_ITEM -> EditItemContent(
                                    token = intent.getStringExtra(EXTRA_TOKEN) ?: "",
                                    onDismiss = { finish() }
                                )
                                else -> finish()
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun RenameGearContent(
    setId: String,
    currentName: String,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("default", Context.MODE_PRIVATE) }
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
                "Rename Gear Set",
                fontWeight = FontWeight.Bold,
                fontSize = 19.sp,
                color = MaterialTheme.colorScheme.onSurface
            )
        }

        Spacer(modifier = Modifier.height(18.dp))

        OutlinedTextField(
            value = gearNameText,
            onValueChange = { gearNameText = it },
            label = { Text("Gear Set Name") },
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
                Text("Cancel", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Spacer(modifier = Modifier.width(8.dp))
            Button(
                onClick = {
                    val clean = gearNameText.trim()
                    if (clean.isNotBlank()) {
                        prefs.edit().putString("gear_set_${setId}_name", clean).apply()
                        try {
                            LightspeedAccessibilityService.instance?.reloadPreferences()
                        } catch (_: Exception) {}
                        Toast.makeText(context, "Renamed to $clean", Toast.LENGTH_SHORT).show()
                    }
                    onDismiss()
                },
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary
                ),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text("Save", fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
fun EditItemContent(
    token: String,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("default", Context.MODE_PRIVATE) }
    val safeKey = remember(token) { token.hashCode().toString() }

    val initialName = remember(token) {
        prefs.getString("custom_label_$safeKey", null) ?: LightspeedShortcutManager.resolveLabel(context, token)
    }
    var customLabelText by remember { mutableStateOf(initialName) }
    var selectedBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var iconKeyTrigger by remember { mutableStateOf(0) }

    val currentDrawable = remember(token, selectedBitmap, iconKeyTrigger) {
        if (selectedBitmap != null) {
            BitmapDrawable(context.resources, selectedBitmap)
        } else {
            LightspeedIconManager.getIconDrawable(context, token)
        }
    }

    val imagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            try {
                context.contentResolver.openInputStream(uri)?.use { stream ->
                    val bmp = BitmapFactory.decodeStream(stream)
                    if (bmp != null) {
                        val size = 128
                        val scaled = Bitmap.createScaledBitmap(bmp, size, size, true)
                        selectedBitmap = scaled
                    }
                }
            } catch (e: Exception) {
                Toast.makeText(context, "Failed to load image: ${e.message}", Toast.LENGTH_SHORT).show()
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
                    "Customize Item",
                    fontWeight = FontWeight.Bold,
                    fontSize = 19.sp,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    "Change display label & custom icon",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        // Icon Preview Card with Edit Badge
        Box(
            modifier = Modifier
                .size(84.dp)
                .clip(RoundedCornerShape(22.dp))
                .background(MaterialTheme.colorScheme.surfaceContainerHighest)
                .border(
                    1.5.dp,
                    MaterialTheme.colorScheme.primary.copy(alpha = 0.6f),
                    RoundedCornerShape(22.dp)
                )
                .clickable { imagePickerLauncher.launch("image/*") },
            contentAlignment = Alignment.Center
        ) {
            val bmp = remember(currentDrawable) {
                if (currentDrawable is BitmapDrawable) {
                    currentDrawable.bitmap
                } else if (currentDrawable != null) {
                    val w = currentDrawable.intrinsicWidth.coerceIn(48, 128)
                    val h = currentDrawable.intrinsicHeight.coerceIn(48, 128)
                    val b = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
                    val canvas = Canvas(b)
                    currentDrawable.setBounds(0, 0, w, h)
                    currentDrawable.draw(canvas)
                    b
                } else null
            }

            if (bmp != null) {
                Image(
                    bitmap = bmp.asImageBitmap(),
                    contentDescription = "Item Icon",
                    modifier = Modifier.size(56.dp)
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
        Text(
            "Tap icon to pick from gallery",
            fontSize = 11.5.sp,
            color = MaterialTheme.colorScheme.primary
        )

        Spacer(modifier = Modifier.height(18.dp))

        OutlinedTextField(
            value = customLabelText,
            onValueChange = { customLabelText = it },
            label = { Text("Display Name") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = MaterialTheme.colorScheme.primary,
                unfocusedBorderColor = MaterialTheme.colorScheme.outline
            )
        )

        Spacer(modifier = Modifier.height(12.dp))

        // Reset to Default Button
        TextButton(
            onClick = {
                prefs.edit().remove("custom_label_$safeKey").apply()
                val iconFile = File(context.filesDir, "shortcut_icons/$safeKey.png")
                if (iconFile.exists()) iconFile.delete()
                LightspeedShortcutManager.clearMemoryCache()
                selectedBitmap = null
                customLabelText = LightspeedShortcutManager.resolveLabel(context, token)
                iconKeyTrigger++
                try {
                    LightspeedAccessibilityService.instance?.reloadPreferences()
                } catch (_: Exception) {}
                Toast.makeText(context, "Reset to default", Toast.LENGTH_SHORT).show()
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
            Text("Reset to Default", color = MaterialTheme.colorScheme.error, fontSize = 12.5.sp)
        }

        Spacer(modifier = Modifier.height(12.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End
        ) {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Spacer(modifier = Modifier.width(8.dp))
            Button(
                onClick = {
                    val cleanLabel = customLabelText.trim()
                    if (cleanLabel.isNotBlank()) {
                        prefs.edit().putString("custom_label_$safeKey", cleanLabel).apply()
                    }
                    if (selectedBitmap != null) {
                        LightspeedShortcutManager.saveShortcutBitmap(context, token, selectedBitmap!!)
                        LightspeedIconManager.saveCustomShortcutBitmap(context, token, selectedBitmap!!)
                    }
                    try {
                        LightspeedAccessibilityService.instance?.reloadPreferences()
                    } catch (_: Exception) {}
                    Toast.makeText(context, "Item customized successfully!", Toast.LENGTH_SHORT).show()
                    onDismiss()
                },
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary
                ),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text("Save", fontWeight = FontWeight.Bold)
            }
        }
    }
}
