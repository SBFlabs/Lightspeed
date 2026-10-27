package com.sbf.lightspeed

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.BitmapDrawable
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Category
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.WarningAmber
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
import com.sbf.lightspeed.system.defaultPrefs
import com.sbf.lightspeed.system.logSwallowed
import com.sbf.lightspeed.ui.theme.LightspeedStyle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class InstalledAppIconEntry(
    val packageName: String,
    val label: String,
    val bitmap: Bitmap?
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
                            val bmp = if (dr is BitmapDrawable && dr.bitmap != null) {
                                dr.bitmap
                            } else {
                                val size = 128
                                val b = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
                                val canvas = Canvas(b)
                                dr.setBounds(0, 0, size, size)
                                dr.draw(canvas)
                                b
                            }
                            InstalledAppIconEntry(packageName = pkg, label = label, bitmap = bmp)
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
                        val bmp = appEntry.bitmap
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
                        IconPackItemCell(
                            context = context,
                            selectedPackPkg = selectedPackPkg,
                            drawableName = drawableName,
                            onIconSelected = onIconSelected
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun IconPackItemCell(
    context: Context,
    selectedPackPkg: String,
    drawableName: String,
    onIconSelected: (Bitmap) -> Unit
) {
    val bmpState = produceState<Bitmap?>(initialValue = null, key1 = selectedPackPkg, key2 = drawableName) {
        value = withContext(Dispatchers.IO) {
            val drawable = LightspeedIconManager.getDrawableFromPack(context, selectedPackPkg, drawableName)
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
    }

    val bmp = bmpState.value
    Box(
        modifier = Modifier
            .size(52.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.6f))
            .clickable(enabled = bmp != null) {
                bmp?.let { onIconSelected(it) }
            },
        contentAlignment = Alignment.Center
    ) {
        if (bmp != null) {
            Image(
                bitmap = bmp.asImageBitmap(),
                contentDescription = drawableName,
                modifier = Modifier.size(36.dp)
            )
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
                tint = LightspeedStyle.colors.accentWarn,
                modifier = Modifier.size(26.dp)
            )
            Text(
                text = "Deep Activity Warning",
                fontWeight = FontWeight.Bold,
                fontSize = 18.sp,
                color = LightspeedStyle.colors.textPrimary
            )
        }

        Spacer(modifier = Modifier.height(14.dp))

        Text(
            text = "⚠️ Deep Activity Warning: Launching internal activities directly may cause instability or unexpected behavior.",
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
                text = "Do not show again",
                fontSize = 13.sp,
                color = LightspeedStyle.colors.textPrimary
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
                    } catch (e: Exception) { logSwallowed("CockpitDialogActivity", "DeepActivityWarningContent:875", e) }
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
