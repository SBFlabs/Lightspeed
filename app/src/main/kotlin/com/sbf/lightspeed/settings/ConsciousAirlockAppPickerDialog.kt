package com.sbf.lightspeed.settings

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sbf.lightspeed.system.LightspeedHapticEngine
import com.sbf.lightspeed.system.LightspeedIconManager
import com.sbf.lightspeed.system.LightspeedPreferences
import com.sbf.lightspeed.ui.theme.LightspeedStyle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class InstalledAppEntry(
    val packageName: String,
    val label: String
)

@Composable
fun ConsciousAirlockAppPickerDialog(
    context: Context,
    prefs: SharedPreferences,
    onDismissRequest: () -> Unit
) {
    DeckDialog(onDismissRequest = onDismissRequest) {
        ConsciousAirlockAppPickerContent(
            context = context,
            prefs = prefs,
            onDismissRequest = onDismissRequest
        )
    }
}

@Composable
fun ConsciousAirlockAppPickerContent(
    context: Context,
    prefs: SharedPreferences,
    onDismissRequest: () -> Unit
) {
    val lsColors = LightspeedStyle.colors
    var selectedApps by remember {
        mutableStateOf(prefs.getStringSet(LightspeedPreferences.KEY_CONSCIOUS_DELAY_PACKAGES, emptySet<String>()) ?: emptySet<String>())
    }
    var searchQuery by remember { mutableStateOf("") }
    var appsList by remember { mutableStateOf<List<InstalledAppEntry>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }

    LaunchedEffect(Unit) {
        val loaded = withContext(Dispatchers.IO) {
            val pm = context.packageManager
            val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
            val activities = pm.queryIntentActivities(intent, 0)
            activities.mapNotNull { resolveInfo ->
                val pkg = resolveInfo.activityInfo?.packageName ?: return@mapNotNull null
                val label = resolveInfo.loadLabel(pm)?.toString() ?: pkg
                InstalledAppEntry(pkg, label)
            }.distinctBy { it.packageName }
                .sortedBy { it.label.lowercase() }
        }
        appsList = loaded
        isLoading = false
    }

    val filteredApps = remember(appsList, searchQuery) {
        if (searchQuery.isBlank()) appsList
        else {
            val q = searchQuery.trim().lowercase()
            appsList.filter { it.label.lowercase().contains(q) || it.packageName.lowercase().contains(q) }
        }
    }

    Column(
        modifier = Modifier
            .padding(18.dp)
            .fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text("Select Gated Applications", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = lsColors.textPrimary)
                    Text("Selected: ${selectedApps.size} apps gated with 5s airlock", fontSize = 10.sp, color = lsColors.textSecondary)
                }
            }

            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                placeholder = { Text("Search installed apps...", fontSize = 11.sp, color = lsColors.textDisabled) },
                modifier = Modifier.fillMaxWidth().height(48.dp),
                leadingIcon = {
                    Icon(Icons.Default.Search, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp))
                },
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) {
                        IconButton(onClick = { searchQuery = "" }, modifier = Modifier.size(20.dp)) {
                            Icon(Icons.Default.Close, contentDescription = "Clear", tint = lsColors.textDisabled, modifier = Modifier.size(14.dp))
                        }
                    }
                },
                singleLine = true,
                shape = RoundedCornerShape(10.dp),
                textStyle = LocalTextStyle.current.copy(fontSize = 11.5.sp, color = lsColors.textPrimary),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                    unfocusedBorderColor = lsColors.textPrimary.copy(alpha = 0.15f),
                    focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                    unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f)
                )
            )

            if (isLoading) {
                Box(modifier = Modifier.fillMaxWidth().height(160.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(modifier = Modifier.size(28.dp), strokeWidth = 2.dp)
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 280.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    items(filteredApps, key = { it.packageName }) { app ->
                        val isSelected = selectedApps.contains(app.packageName)
                        AirlockAppRow(
                            context = context,
                            app = app,
                            isSelected = isSelected,
                            onToggle = {
                                LightspeedHapticEngine.tick(context)
                                val next = if (isSelected) selectedApps - app.packageName else selectedApps + app.packageName
                                selectedApps = next
                                prefs.edit().putStringSet(LightspeedPreferences.KEY_CONSCIOUS_DELAY_PACKAGES, next).apply()
                            }
                        )
                    }
                }
            }

            Spacer(Modifier.height(4.dp))
            DeckButton(
                text = "Done",
                onClick = onDismissRequest,
                modifier = Modifier.fillMaxWidth()
            )
        }
}

@Composable
private fun AirlockAppRow(
    context: Context,
    app: InstalledAppEntry,
    isSelected: Boolean,
    onToggle: () -> Unit
) {
    val lsColors = LightspeedStyle.colors
    var iconBitmap by remember { mutableStateOf<Bitmap?>(null) }

    LaunchedEffect(app.packageName) {
        val bmp = withContext(Dispatchers.IO) {
            LightspeedIconManager.getIconBitmap(context, app.packageName, useCache = true)
        }
        iconBitmap = bmp
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .clickable(onClick = onToggle)
            .padding(vertical = 5.dp, horizontal = 6.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.weight(1f)
        ) {
            if (iconBitmap != null) {
                Image(
                    bitmap = iconBitmap!!.asImageBitmap(),
                    contentDescription = null,
                    modifier = Modifier.size(24.dp)
                )
            } else {
                Box(
                    modifier = Modifier
                        .size(24.dp)
                        .clip(RoundedCornerShape(6.dp))
                )
            }
            Spacer(Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = app.label,
                    color = lsColors.textPrimary,
                    fontSize = 11.5.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = app.packageName,
                    color = lsColors.textSecondary.copy(alpha = 0.7f),
                    fontSize = 9.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
        Checkbox(checked = isSelected, onCheckedChange = null)
    }
}
