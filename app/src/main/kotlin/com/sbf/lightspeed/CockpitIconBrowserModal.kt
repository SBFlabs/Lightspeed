package com.sbf.lightspeed

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.BitmapDrawable
import android.widget.Toast
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Category
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DeleteOutline
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
import com.sbf.lightspeed.settings.DeckButton
import com.sbf.lightspeed.settings.DeckDialog
import com.sbf.lightspeed.system.CustomIconEntry
import com.sbf.lightspeed.system.CustomIconList
import com.sbf.lightspeed.system.CustomIconListRepository
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

private data class PendingCustomListIcon(
    val bitmap: Bitmap,
    val defaultLabel: String,
    val sourcePack: String,
    val sourceDrawable: String
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
    val baseIconPacks = remember {
        val list = mutableListOf<IconPackInfo>()
        list.add(IconPackInfo("installed_apps", "Installed Apps", isSystem = true))
        list.add(IconPackInfo("lightspeed_vectors", "Lightspeed Vectors", isSystem = true))
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
                        "Icon Browser",
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp,
                        color = lsColors.textPrimary
                    )
                    Text(
                        "Tap to apply · Hold any icon to save to a custom list",
                        fontSize = 10.5.sp,
                        color = lsColors.textSecondary
                    )
                }
            }
            IconButton(onClick = onDismiss) {
                Icon(Icons.Default.Close, contentDescription = "Close", tint = lsColors.textSecondary)
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
                            activeCustomList != null -> "Search ${activeCustomList.name} ($itemCount)"
                            selectedPackPkg == "installed_apps" -> "Search installed apps ($itemCount)"
                            selectedPackPkg == "lightspeed_vectors" -> "Search vector icons ($itemCount)"
                            else -> "Search icon pack ($itemCount)"
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
                        Toast.makeText(context, "Deleted list: $deletedName", Toast.LENGTH_SHORT).show()
                    }
                ) {
                    Icon(
                        Icons.Default.DeleteOutline,
                        contentDescription = "Delete custom list",
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
                        "No icons in '${activeCustomList.name}' yet. Long-press any icon in Installed Apps or an Icon Pack to add it here.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 12.5.sp,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                        modifier = Modifier.padding(horizontal = 16.dp)
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
                                            "Removed '${entry.label}' from ${activeCustomList.name}",
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
                        text = "No matching icons found",
                        color = lsColors.textSecondary,
                        fontSize = 13.sp
                    )
                }
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(minSize = 66.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f, fill = false),
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
        DeckDialog(
            onDismissRequest = { showCreateListDialog = false },
            title = { Text("Create Custom Icon List", fontWeight = FontWeight.Bold, color = lsColors.textPrimary) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        "Create a personal icon collection (e.g. Favorite Icons, Tech Icons). Long-press any icon in any pack to add it to this list.",
                        fontSize = 12.5.sp,
                        color = lsColors.textSecondary
                    )
                    OutlinedTextField(
                        value = newListNameText,
                        onValueChange = { newListNameText = it },
                        label = { Text("List Name") },
                        placeholder = { Text("Favorite Icons") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        colors = fieldColors
                    )
                }
            },
            confirmButton = {
                DeckButton(
                    onClick = {
                        val name = newListNameText.trim().ifBlank { "Favorite Icons" }
                        val created = CustomIconListRepository.createCustomList(context, name)
                        customListsVersion++
                        selectedPackPkg = "custom_list:${created.id}"
                        showCreateListDialog = false
                        Toast.makeText(context, "Created list: ${created.name}", Toast.LENGTH_SHORT).show()
                    },
                    isActive = true
                ) {
                    Text("Create", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                DeckButton(onClick = { showCreateListDialog = false }) {
                    Text("Cancel", color = lsColors.textSecondary)
                }
            }
        )
    }

    pendingAddIcon?.let { pending ->
        var labelInput by remember(pending) { mutableStateOf(pending.defaultLabel) }
        var targetListId by remember(pending, customLists) {
            mutableStateOf(customLists.firstOrNull()?.id ?: "")
        }
        var creatingInlineList by remember(pending, customLists) {
            mutableStateOf(customLists.isEmpty())
        }
        var inlineNewListName by remember(pending) { mutableStateOf("Favorite Icons") }

        DeckDialog(
            onDismissRequest = { pendingAddIcon = null },
            title = { Text("Add to Custom Icon List", fontWeight = FontWeight.Bold, color = lsColors.textPrimary) },
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
                            label = { Text("Searchable Icon Label") },
                            singleLine = true,
                            modifier = Modifier.weight(1f),
                            colors = fieldColors
                        )
                    }

                    if (customLists.isNotEmpty() && !creatingInlineList) {
                        Text(
                            "Select Target List:",
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
                            Text("Create New List", fontSize = 12.sp)
                        }
                    } else {
                        OutlinedTextField(
                            value = inlineNewListName,
                            onValueChange = { inlineNewListName = it },
                            label = { Text("New List Name") },
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
                        val finalListId = if (creatingInlineList || targetListId.isBlank()) {
                            val created = CustomIconListRepository.createCustomList(
                                context,
                                inlineNewListName.trim().ifBlank { "Favorite Icons" }
                            )
                            created.id
                        } else {
                            targetListId
                        }
                        CustomIconListRepository.addIconToCustomList(
                            context = context,
                            listId = finalListId,
                            label = labelInput.trim().ifBlank { pending.defaultLabel },
                            bitmap = pending.bitmap
                        )
                        customListsVersion++
                        val listName = CustomIconListRepository.getCustomLists(context)
                            .firstOrNull { it.id == finalListId }?.name ?: "Custom List"
                        Toast.makeText(
                            context,
                            "Added '${labelInput.trim().ifBlank { pending.defaultLabel }}' to $listName",
                            Toast.LENGTH_SHORT
                        ).show()
                        pendingAddIcon = null
                    },
                    isActive = true
                ) {
                    Text("Save Icon", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                DeckButton(onClick = { pendingAddIcon = null }) {
                    Text("Cancel", color = lsColors.textSecondary)
                }
            }
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
            text = "Deep Activity Warning: Launching internal activities directly may cause instability or unexpected behavior.",
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
            DeckButton(onClick = onDismiss) {
                Text("Cancel", color = LightspeedStyle.colors.textSecondary, fontWeight = FontWeight.SemiBold)
            }
            Spacer(modifier = Modifier.width(8.dp))
            DeckButton(
                onClick = {
                    if (doNotShowAgain) {
                        prefs.edit().putBoolean(com.sbf.lightspeed.system.LightspeedPreferences.KEY_SUPPRESS_DEEP_ACTIVITY_WARNING, true).apply()
                    }
                    try {
                        com.sbf.lightspeed.system.ActionDispatcher.executeDirect(context, token)
                    } catch (e: Exception) { logSwallowed("CockpitDialogActivity", "DeepActivityWarningContent:875", e) }
                    onDismiss()
                },
                isActive = true
            ) {
                Text("Launch Anyway", fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun WireframeIconGridLoader(modifier: Modifier = Modifier) {
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
