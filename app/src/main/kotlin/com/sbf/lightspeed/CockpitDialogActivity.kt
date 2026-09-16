package com.sbf.lightspeed

import com.sbf.lightspeed.system.defaultPrefs
import android.content.Context
import android.content.Intent
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sbf.lightspeed.system.IconPackInfo
import com.sbf.lightspeed.system.LightspeedIconManager
import com.sbf.lightspeed.system.LightspeedShortcutManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import com.sbf.lightspeed.ui.theme.LightspeedTheme

class CockpitDialogActivity : ComponentActivity() {

    companion object {
        const val ACTION_RENAME_GEAR = "com.sbf.lightspeed.action.RENAME_GEAR"
        const val ACTION_EDIT_ITEM = "com.sbf.lightspeed.action.EDIT_ITEM"
        const val ACTION_CONFIRM_DEEP_ACTIVITY = "com.sbf.lightspeed.action.CONFIRM_DEEP_ACTIVITY"

        const val EXTRA_SET_ID = "EXTRA_SET_ID"
        const val EXTRA_SET_INDEX = "EXTRA_SET_INDEX"
        const val EXTRA_CURRENT_NAME = "EXTRA_CURRENT_NAME"
        const val EXTRA_TOKEN = "EXTRA_TOKEN"
    }

    override fun finish() {
        finishAndRemoveTask()
        super.finish()
        com.sbf.lightspeed.system.overrideZeroTransition()
    }

    override fun onDestroy() {
        super.onDestroy()
        val setIndex = intent.getIntExtra(EXTRA_SET_INDEX, -1)
        if (setIndex >= 0) {
            try {
                LightspeedAccessibilityService.instance?.reopenCockpitHangar(setIndex)
            } catch (_: Exception) {}
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val action = intent.action ?: ACTION_RENAME_GEAR

        setContent {
            LightspeedTheme(forceDark = true) {
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
                        color = Color(0xF012141A),
                        tonalElevation = 6.dp,
                        border = androidx.compose.foundation.BorderStroke(
                            1.dp,
                            Color(0x33FFFFFF)
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
                                ACTION_CONFIRM_DEEP_ACTIVITY -> DeepActivityWarningContent(
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
    val prefs = remember { context.defaultPrefs() }
    val safeKey = remember(token) { token.hashCode().toString() }

    val initialName = remember(token) {
        prefs.getString("custom_label_$safeKey", null) ?: LightspeedShortcutManager.resolveLabel(context, token)
    }
    var customLabelText by remember { mutableStateOf(initialName) }
    var selectedBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var iconKeyTrigger by remember { mutableStateOf(0) }
    var showSourceSelector by remember { mutableStateOf(false) }
    var showIconPackBrowser by remember { mutableStateOf(false) }

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

    if (showIconPackBrowser) {
        IconPackBrowserModal(
            onDismiss = { showIconPackBrowser = false },
            onIconSelected = { bmp ->
                selectedBitmap = bmp
                showIconPackBrowser = false
            }
        )
        return
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
                Text("Gallery", fontSize = 12.sp)
            }
            FilledTonalButton(
                onClick = { showIconPackBrowser = true },
                shape = RoundedCornerShape(10.dp),
                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp)
            ) {
                Icon(Icons.Default.Category, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("Icon Packs", fontSize = 12.sp)
            }
        }

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
                // clearManualOverride removes the override flag, clears all IconManager in-memory
                // caches for this token, and deletes the disk file — fully symmetric with Save.
                LightspeedIconManager.clearManualOverride(context, token)
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
                        // saveCustomShortcutBitmap is the canonical write path:
                        // it updates all in-memory caches, persists to disk, and records the backup override flag.
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

    if (showSourceSelector) {
        AlertDialog(
            onDismissRequest = { showSourceSelector = false },
            title = { Text("Choose Icon Source") },
            text = { Text("Select where to pick your custom icon from:") },
            confirmButton = {
                TextButton(onClick = {
                    showSourceSelector = false
                    showIconPackBrowser = true
                }) {
                    Text("Apps & Icon Packs")
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    showSourceSelector = false
                    imagePickerLauncher.launch("image/*")
                }) {
                    Text("Gallery / Photos")
                }
            }
        )
    }
}

data class InstalledAppIconEntry(
    val packageName: String,
    val label: String,
    val drawable: Drawable
)

@Composable
fun IconPackBrowserModal(
    onDismiss: () -> Unit,
    onIconSelected: (Bitmap) -> Unit
) {
    val context = LocalContext.current
    val iconPacks = remember {
        val list = mutableListOf<IconPackInfo>()
        list.add(IconPackInfo("installed_apps", "Installed Apps", isSystem = true))
        list.addAll(LightspeedIconManager.getAvailableIconPacks(context).filter { !it.isSystem })
        list
    }
    var selectedPackPkg by remember {
        mutableStateOf("installed_apps")
    }
    var searchQuery by remember { mutableStateOf("") }
    var drawablesList by remember { mutableStateOf<List<String>>(emptyList()) }
    var installedAppsList by remember { mutableStateOf<List<InstalledAppIconEntry>>(emptyList()) }
    var isLoading by remember { mutableStateOf(false) }

    LaunchedEffect(selectedPackPkg) {
        isLoading = true
        if (selectedPackPkg == "installed_apps") {
            installedAppsList = withContext(Dispatchers.IO) {
                try {
                    val pm = context.packageManager
                    val intent = Intent(Intent.ACTION_MAIN, null).apply {
                        addCategory(Intent.CATEGORY_LAUNCHER)
                    }
                    val activities = pm.queryIntentActivities(intent, 0)
                    activities.mapNotNull { ri ->
                        try {
                            val pkg = ri.activityInfo.packageName
                            val label = ri.loadLabel(pm)?.toString() ?: pkg
                            val dr = ri.loadIcon(pm)
                            InstalledAppIconEntry(packageName = pkg, label = label, drawable = dr)
                        } catch (_: Exception) { null }
                    }.sortedBy { it.label.lowercase() }
                } catch (_: Exception) {
                    emptyList()
                }
            }
        } else if (selectedPackPkg.isNotBlank()) {
            drawablesList = withContext(Dispatchers.IO) {
                LightspeedIconManager.getIconPackDrawableNames(context, selectedPackPkg)
            }
        }
        isLoading = false
    }

    val filteredApps = remember(installedAppsList, searchQuery) {
        if (searchQuery.isBlank()) {
            installedAppsList
        } else {
            val q = searchQuery.lowercase().trim()
            installedAppsList.filter {
                it.label.lowercase().contains(q) || it.packageName.lowercase().contains(q)
            }
        }
    }

    val filteredDrawables = remember(drawablesList, searchQuery) {
        if (searchQuery.isBlank()) {
            drawablesList
        } else {
            val q = searchQuery.lowercase().trim()
            drawablesList.filter { it.lowercase().contains(q) }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(max = 540.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Default.Category,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    "Icon Browser",
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
            IconButton(onClick = onDismiss) {
                Icon(Icons.Default.Close, contentDescription = "Close", tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Tab Selector Chips
        ScrollableTabRow(
            selectedTabIndex = iconPacks.indexOfFirst { it.packageName == selectedPackPkg }.coerceAtLeast(0),
            edgePadding = 0.dp,
            divider = {},
            containerColor = Color.Transparent
        ) {
            iconPacks.forEach { pack ->
                val isSelected = (pack.packageName == selectedPackPkg)
                Tab(
                    selected = isSelected,
                    onClick = {
                        selectedPackPkg = pack.packageName
                        searchQuery = ""
                    },
                    text = {
                        Text(
                            pack.label,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                            color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                )
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        val itemCount = if (selectedPackPkg == "installed_apps") filteredApps.size else filteredDrawables.size
        OutlinedTextField(
            value = searchQuery,
            onValueChange = { searchQuery = it },
            label = {
                Text(
                    if (selectedPackPkg == "installed_apps") "Search installed apps ($itemCount)"
                    else "Search icon pack ($itemCount)"
                )
            },
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp)
        )

        Spacer(modifier = Modifier.height(12.dp))

        if (isLoading) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(240.dp),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
            }
        } else if (selectedPackPkg == "installed_apps") {
            // Render Installed Apps Grid
            if (filteredApps.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(200.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        "No matching apps found.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 13.sp
                    )
                }
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(minSize = 64.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f, fill = false)
                        .heightIn(max = 320.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(filteredApps) { appEntry ->
                        val bmp = remember(appEntry.packageName) {
                            val dr = appEntry.drawable
                            if (dr is BitmapDrawable && dr.bitmap != null) {
                                dr.bitmap
                            } else {
                                val size = 128
                                val b = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
                                val canvas = Canvas(b)
                                dr.setBounds(0, 0, size, size)
                                dr.draw(canvas)
                                b
                            }
                        }

                        Column(
                            modifier = Modifier
                                .clip(RoundedCornerShape(12.dp))
                                .background(MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.6f))
                                .clickable {
                                    if (bmp != null) {
                                        onIconSelected(bmp)
                                    }
                                }
                                .padding(horizontal = 4.dp, vertical = 8.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            if (bmp != null) {
                                Image(
                                    bitmap = bmp.asImageBitmap(),
                                    contentDescription = appEntry.label,
                                    modifier = Modifier.size(40.dp)
                                )
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = appEntry.label,
                                fontSize = 10.5.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                color = MaterialTheme.colorScheme.onSurface,
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center
                            )
                        }
                    }
                }
            }
        } else {
            // Render Third-Party Icon Pack Grid
            if (filteredDrawables.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(200.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        "No matching icons in this pack.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 13.sp
                    )
                }
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(minSize = 52.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f, fill = false)
                        .heightIn(max = 320.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(filteredDrawables.take(300)) { drawableName ->
                        val drawable = remember(drawableName, selectedPackPkg) {
                            LightspeedIconManager.getDrawableFromPack(context, selectedPackPkg, drawableName)
                        }
                        val bmp = remember(drawable) {
                            if (drawable is BitmapDrawable && drawable.bitmap != null) {
                                drawable.bitmap
                            } else if (drawable != null) {
                                val size = 128
                                val b = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
                                val canvas = Canvas(b)
                                drawable.setBounds(0, 0, size, size)
                                drawable.draw(canvas)
                                b
                            } else null
                        }

                        if (bmp != null) {
                            Box(
                                modifier = Modifier
                                    .size(52.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.6f))
                                    .clickable {
                                        onIconSelected(bmp)
                                    },
                                contentAlignment = Alignment.Center
                            ) {
                                Image(
                                    bitmap = bmp.asImageBitmap(),
                                    contentDescription = drawableName,
                                    modifier = Modifier.size(36.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
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
                tint = Color(0xFFFFB300),
                modifier = Modifier.size(26.dp)
            )
            Text(
                text = "Deep Activity Warning",
                fontWeight = FontWeight.Bold,
                fontSize = 18.sp,
                color = Color.White
            )
        }

        Spacer(modifier = Modifier.height(14.dp))

        Text(
            text = "⚠️ Deep Activity Warning: Launching internal activities directly may cause instability or unexpected behavior.",
            fontSize = 13.5.sp,
            color = Color.LightGray.copy(alpha = 0.9f),
            lineHeight = 19.sp
        )

        Spacer(modifier = Modifier.height(16.dp))

        // Checkbox: "Do not show again"
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
                    uncheckedColor = Color.Gray
                )
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = "Do not show again",
                fontSize = 13.sp,
                color = Color.White
            )
        }

        Spacer(modifier = Modifier.height(20.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End
        ) {
            TextButton(
                onClick = onDismiss,
                shape = RoundedCornerShape(12.dp)
            ) {
                Text("Cancel", color = MaterialTheme.colorScheme.onSurfaceVariant, fontWeight = FontWeight.SemiBold)
            }
            Spacer(modifier = Modifier.width(8.dp))
            Button(
                onClick = {
                    if (doNotShowAgain) {
                        prefs.edit().putBoolean(com.sbf.lightspeed.system.LightspeedPreferences.KEY_SUPPRESS_DEEP_ACTIVITY_WARNING, true).apply()
                    }
                    try {
                        com.sbf.lightspeed.system.ActionDispatcher.executeDirect(context, token)
                    } catch (_: Exception) {}
                    onDismiss()
                },
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary
                ),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text("Launch Anyway", fontWeight = FontWeight.Bold)
            }
        }
    }
}

