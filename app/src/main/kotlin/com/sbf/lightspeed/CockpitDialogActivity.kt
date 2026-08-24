package com.sbf.lightspeed

import android.app.Activity
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.drawable.BitmapDrawable
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
            MaterialTheme(
                colorScheme = darkColorScheme(
                    primary = Color(0xFF90CAF9),
                    secondary = Color(0xFFCE93D8),
                    surface = Color(0xFF161B26),
                    surfaceVariant = Color(0xFF212836)
                )
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.6f))
                        .clickable(onClick = { finish() }),
                    contentAlignment = Alignment.Center
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(0.92f)
                            .clip(RoundedCornerShape(20.dp))
                            .background(Color(0xFF141923))
                            .border(1.2.dp, Color(0xFF90CAF9).copy(alpha = 0.35f), RoundedCornerShape(20.dp))
                            .clickable(enabled = false, onClick = {})
                            .padding(20.dp)
                    ) {
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
            Spacer(modifier = Modifier.width(10.dp))
            Text(
                "Rename Gear Set",
                fontWeight = FontWeight.Bold,
                fontSize = 18.sp,
                color = Color.White
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        OutlinedTextField(
            value = gearNameText,
            onValueChange = { gearNameText = it },
            label = { Text("Gear Set Name") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
            textStyle = androidx.compose.ui.text.TextStyle(color = Color.White, fontSize = 15.sp)
        )

        Spacer(modifier = Modifier.height(20.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End
        ) {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = Color.White.copy(alpha = 0.7f))
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
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
            ) {
                Text("Save", color = Color(0xFF0F141C), fontWeight = FontWeight.Bold)
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
            Spacer(modifier = Modifier.width(10.dp))
            Column {
                Text(
                    "Customize Item",
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp,
                    color = Color.White
                )
                Text(
                    "Change display label and custom icon",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        // Icon Preview Card with Edit Badge
        Box(
            modifier = Modifier
                .size(80.dp)
                .clip(RoundedCornerShape(20.dp))
                .background(Color(0xFF1E2536))
                .border(1.5.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.5f), RoundedCornerShape(20.dp))
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
                    modifier = Modifier.size(54.dp)
                )
            }

            // Edit overlay icon
            Box(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(4.dp)
                    .size(22.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Default.Image,
                    contentDescription = "Change Icon",
                    tint = Color(0xFF0F141C),
                    modifier = Modifier.size(13.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(6.dp))
        Text(
            "Tap icon to pick from gallery",
            fontSize = 11.sp,
            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.8f)
        )

        Spacer(modifier = Modifier.height(16.dp))

        OutlinedTextField(
            value = customLabelText,
            onValueChange = { customLabelText = it },
            label = { Text("Display Name") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
            textStyle = androidx.compose.ui.text.TextStyle(color = Color.White, fontSize = 15.sp)
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
            Icon(Icons.Default.Refresh, contentDescription = null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(16.dp))
            Spacer(modifier = Modifier.width(6.dp))
            Text("Reset to Default", color = MaterialTheme.colorScheme.error, fontSize = 12.sp)
        }

        Spacer(modifier = Modifier.height(12.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End
        ) {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = Color.White.copy(alpha = 0.7f))
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
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
            ) {
                Text("Save", color = Color(0xFF0F141C), fontWeight = FontWeight.Bold)
            }
        }
    }
}
