package com.sbf.lightspeed

import com.sbf.lightspeed.settings.verticalEdgeFade

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.BitmapDrawable
import android.widget.Toast
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Category
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Search
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
import com.sbf.lightspeed.system.CustomIconEntry
import com.sbf.lightspeed.system.CustomIconList
import com.sbf.lightspeed.system.CustomIconListRepository
import com.sbf.lightspeed.system.IconPackInfo
import com.sbf.lightspeed.system.LightspeedIconManager
import com.sbf.lightspeed.system.LightspeedLocaleEngine
import com.sbf.lightspeed.system.lsString
import com.sbf.lightspeed.ui.theme.LightspeedStyle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class InstalledAppIconEntry(
    val packageName: String,
    val label: String,
    val bitmap: Bitmap?
)

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun IconPackBrowserModal(
    onDismiss: () -> Unit,
    onIconSelected: (Bitmap) -> Unit
) {
    val context = LocalContext.current
    var customListsVersion by remember { mutableIntStateOf(0) }
    val customLists = remember(customListsVersion) {
        CustomIconListRepository.getCustomLists(context)
    }
    val localeState = LightspeedLocaleEngine.state
    val baseIconPacks = remember(localeState) {
        val list = mutableListOf<IconPackInfo>()
        list.add(IconPackInfo("installed_apps", lsString(R.string.icon_br_tab_apps), isSystem = true))
        list.add(IconPackInfo("lightspeed_vectors", lsString(R.string.icon_br_tab_vectors), isSystem = true))
        list.addAll(LightspeedIconManager.getAvailableIconPacks(context).filter { !it.isSystem })
        list
    }
    val iconPacks = remember(baseIconPacks, customLists) {
        val list = ArrayList<IconPackInfo>(baseIconPacks.size + customLists.size + 1)
        list.addAll(baseIconPacks)
        customLists.forEach { cl ->
            list.add(IconPackInfo("custom_list:${cl.id}", cl.name, isSystem = false))
        }
        list.add(IconPackInfo("add_custom_list", "+", isSystem = false))
        list
    }
    var selectedPackPkg by remember {
        mutableStateOf("installed_apps")
    }
    var searchQuery by remember { mutableStateOf("") }
    var drawablesList by remember { mutableStateOf<List<String>>(emptyList()) }
    var installedAppsList by remember { mutableStateOf<List<InstalledAppIconEntry>>(emptyList()) }
    var isLoading by remember { mutableStateOf(false) }

    var showCreateListDialog by remember { mutableStateOf(false) }
    var newListNameText by remember { mutableStateOf("") }
    var pendingAddIcon by remember { mutableStateOf<PendingCustomListIcon?>(null) }

    val activeCustomList: CustomIconList? = remember(selectedPackPkg, customLists) {
        if (selectedPackPkg.startsWith("custom_list:")) {
            val id = selectedPackPkg.removePrefix("custom_list:")
            customLists.firstOrNull { it.id == id }
        } else null
    }

    LaunchedEffect(selectedPackPkg) {
        if (selectedPackPkg.startsWith("custom_list:") || selectedPackPkg == "add_custom_list") {
            isLoading = false
            return@LaunchedEffect
        }
        isLoading = true
        if (selectedPackPkg == "lightspeed_vectors") {
            installedAppsList = emptyList()
            isLoading = false
            return@LaunchedEffect
        } else if (selectedPackPkg == "installed_apps") {
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

    val filteredVectorEntries = remember(searchQuery) {
        CockpitVectorPackCatalog.searchEntries(searchQuery)
    }

    val filteredCustomEntries: List<CustomIconEntry> = remember(activeCustomList, searchQuery) {
        val entries = activeCustomList?.items ?: emptyList()
        if (searchQuery.isBlank()) {
            entries
        } else {
            val q = searchQuery.lowercase().trim()
            entries.filter {
                it.label.lowercase().contains(q)
            }
        }
    }

    val lsColors = LightspeedStyle.colors
    val fieldColors = OutlinedTextFieldDefaults.colors(
        focusedBorderColor = lsColors.accent,
        unfocusedBorderColor = lsColors.borderSubtle,
        focusedLabelColor = lsColors.accent,
        unfocusedLabelColor = lsColors.textSecondary,
        focusedTextColor = lsColors.textPrimary,
        unfocusedTextColor = lsColors.textPrimary,
        focusedLeadingIconColor = lsColors.accent,
        unfocusedLeadingIconColor = lsColors.textSecondary,
        cursorColor = lsColors.accent
    )

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
                    tint = lsColors.accent,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(10.dp))
                Column {
                    Text(
                        lsString(R.string.icon_br_title),
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp,
                        color = lsColors.textPrimary
                    )
                    Text(
                        lsString(R.string.icon_br_sub),
                        fontSize = 10.5.sp,
                        color = lsColors.textSecondary
                    )
                }
            }
            IconButton(onClick = onDismiss) {
                Icon(Icons.Default.Close, contentDescription = lsString(R.string.icon_br_close), tint = lsColors.textSecondary)
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
                val isAddTab = pack.packageName == "add_custom_list"
                val isSelected = (pack.packageName == selectedPackPkg)
                Tab(
                    selected = isSelected,
                    onClick = {
                        if (isAddTab) {
                            newListNameText = ""
                            showCreateListDialog = true
                        } else {
                            selectedPackPkg = pack.packageName
                            searchQuery = ""
                        }
                    },
                    text = {
                        Text(
                            pack.label,
                            fontWeight = if (isSelected || isAddTab) FontWeight.Bold else FontWeight.Normal,
                            fontSize = if (isAddTab) 16.sp else 13.sp,
                            color = if (isSelected || isAddTab) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                )
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        val itemCount = when {
            activeCustomList != null -> filteredCustomEntries.size
            selectedPackPkg == "lightspeed_vectors" -> filteredVectorEntries.size
            selectedPackPkg == "installed_apps" -> filteredApps.size
            else -> filteredDrawables.size
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                label = {
                    Text(
                        when {
                            activeCustomList != null -> lsString(R.string.icon_br_search_list, activeCustomList.name, itemCount)
                            selectedPackPkg == "installed_apps" -> lsString(R.string.icon_br_search_apps, itemCount)
                            selectedPackPkg == "lightspeed_vectors" -> lsString(R.string.icon_br_search_vec, itemCount)
                            else -> lsString(R.string.icon_br_search_pack, itemCount)
                        }
                    )
                },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                singleLine = true,
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(12.dp),
                colors = fieldColors
            )
            if (activeCustomList != null) {
                IconButton(
                    onClick = {
                        val deletedName = activeCustomList.name
                        CustomIconListRepository.deleteCustomList(context, activeCustomList.id)
                        customListsVersion++
                        selectedPackPkg = "installed_apps"
                        Toast.makeText(context, lsString(R.string.icon_br_del_list_toast, deletedName), Toast.LENGTH_SHORT).show()
                    }
                ) {
                    Icon(
                        Icons.Default.DeleteOutline,
                        contentDescription = lsString(R.string.icon_br_del_list_cd),
                        tint = MaterialTheme.colorScheme.error
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        if (isLoading) {
            WireframeIconGridLoader(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(240.dp)
            )
        } else if (activeCustomList != null) {
            if (filteredCustomEntries.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(200.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        lsString(R.string.icon_br_empty_list, activeCustomList.name),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 12.5.sp,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                        modifier = Modifier.padding(horizontal = 16.dp)
                    )
                }
            } else {
                val customGridState = androidx.compose.foundation.lazy.grid.rememberLazyGridState()
                LazyVerticalGrid(
                    state = customGridState,
                    columns = GridCells.Adaptive(minSize = 64.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f, fill = false)
                        .heightIn(max = 320.dp)
                        .verticalEdgeFade(customGridState),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(filteredCustomEntries, key = { it.id }) { entry ->
                        val bmp = remember(entry.id) {
                            CustomIconListRepository.loadCustomEntryBitmap(context, entry.id)
                        }
                        Column(
                            modifier = Modifier
                                .clip(RoundedCornerShape(12.dp))
                                .background(MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.6f))
                                .combinedClickable(
                                    onClick = {
                                        if (bmp != null) onIconSelected(bmp)
                                    },
                                    onLongClick = {
                                        CustomIconListRepository.removeIconFromCustomList(
                                            context,
                                            activeCustomList.id,
                                            entry.id
                                        )
                                        customListsVersion++
                                        Toast.makeText(
                                            context,
                                            lsString(R.string.icon_br_removed_fmt, entry.label, activeCustomList.name),
                                            Toast.LENGTH_SHORT
                                        ).show()
                                    }
                                )
                                .padding(horizontal = 4.dp, vertical = 8.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            if (bmp != null) {
                                Image(
                                    bitmap = bmp.asImageBitmap(),
                                    contentDescription = entry.label,
                                    modifier = Modifier.size(40.dp)
                                )
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = entry.label,
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
        } else if (selectedPackPkg == "lightspeed_vectors") {
            if (filteredVectorEntries.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(200.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = lsString(R.string.icon_br_empty_vec),
                        color = lsColors.textSecondary,
                        fontSize = 13.sp
                    )
                }
            } else {
                val vectorGridState = androidx.compose.foundation.lazy.grid.rememberLazyGridState()
                LazyVerticalGrid(
                    state = vectorGridState,
                    columns = GridCells.Adaptive(minSize = 66.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f, fill = false)
                        .verticalEdgeFade(vectorGridState),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(filteredVectorEntries, key = { it.id }) { vecEntry ->
                        CockpitVectorPackTile(
                            entry = vecEntry,
                            onClick = {
                                val bmp = CockpitVectorPackCatalog.renderEntryToBitmap(vecEntry, 128)
                                onIconSelected(bmp)
                            },
                            onLongClick = {
                                val bmp = CockpitVectorPackCatalog.renderEntryToBitmap(vecEntry, 128)
                                pendingAddIcon = PendingCustomListIcon(
                                    bitmap = bmp,
                                    defaultLabel = vecEntry.label,
                                    sourcePack = "lightspeed_vectors",
                                    sourceDrawable = "vector:${vecEntry.id}"
                                )
                            }
                        )
                    }
                }
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
                        lsString(R.string.icon_br_empty_apps),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 13.sp
                    )
                }
            } else {
                val customGridState = androidx.compose.foundation.lazy.grid.rememberLazyGridState()
                LazyVerticalGrid(
                    state = customGridState,
                    columns = GridCells.Adaptive(minSize = 64.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f, fill = false)
                        .heightIn(max = 320.dp)
                        .verticalEdgeFade(customGridState),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(filteredApps) { appEntry ->
                        val bmp = appEntry.bitmap
                        Column(
                            modifier = Modifier
                                .clip(RoundedCornerShape(12.dp))
                                .background(MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.6f))
                                .combinedClickable(
                                    onClick = {
                                        if (bmp != null) {
                                            onIconSelected(bmp)
                                        }
                                    },
                                    onLongClick = {
                                        if (bmp != null) {
                                            pendingAddIcon = PendingCustomListIcon(
                                                bitmap = bmp,
                                                defaultLabel = appEntry.label,
                                                sourcePack = selectedPackPkg,
                                                sourceDrawable = appEntry.packageName
                                            )
                                        }
                                    }
                                )
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
                        lsString(R.string.icon_br_empty_pack),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 13.sp
                    )
                }
            } else {
                val packGridState = androidx.compose.foundation.lazy.grid.rememberLazyGridState()
                LazyVerticalGrid(
                    state = packGridState,
                    columns = GridCells.Adaptive(minSize = 52.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f, fill = false)
                        .heightIn(max = 320.dp)
                        .verticalEdgeFade(packGridState),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(filteredDrawables.take(300)) { drawableName ->
                        IconPackItemCell(
                            context = context,
                            selectedPackPkg = selectedPackPkg,
                            drawableName = drawableName,
                            onIconSelected = onIconSelected,
                            onIconLongPressed = { bmp ->
                                pendingAddIcon = PendingCustomListIcon(
                                    bitmap = bmp,
                                    defaultLabel = drawableName.replace('_', ' ').trim().ifBlank { drawableName },
                                    sourcePack = selectedPackPkg,
                                    sourceDrawable = drawableName
                                )
                            }
                        )
                    }
                }
            }
        }
    }

    if (showCreateListDialog) {
        CreateCustomIconListDialog(
            context = context,
            fieldColors = fieldColors,
            initialName = newListNameText,
            onCreated = { createdId ->
                customListsVersion++
                selectedPackPkg = "custom_list:$createdId"
                showCreateListDialog = false
            },
            onDismiss = { showCreateListDialog = false }
        )
    }

    pendingAddIcon?.let { pending ->
        AddToCustomIconListDialog(
            context = context,
            pending = pending,
            customLists = customLists,
            fieldColors = fieldColors,
            onSaved = {
                customListsVersion++
                pendingAddIcon = null
            },
            onDismiss = { pendingAddIcon = null }
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun IconPackItemCell(
    context: Context,
    selectedPackPkg: String,
    drawableName: String,
    onIconSelected: (Bitmap) -> Unit,
    onIconLongPressed: (Bitmap) -> Unit
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
            .combinedClickable(
                enabled = bmp != null,
                onClick = {
                    bmp?.let { onIconSelected(it) }
                },
                onLongClick = {
                    bmp?.let { onIconLongPressed(it) }
                }
            ),
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
