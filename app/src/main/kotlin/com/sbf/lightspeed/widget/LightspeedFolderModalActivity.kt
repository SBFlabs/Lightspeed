package com.sbf.lightspeed.widget

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sbf.lightspeed.CockpitGearPickerActivity
import com.sbf.lightspeed.settings.DeckCard
import com.sbf.lightspeed.settings.DeckDialog
import com.sbf.lightspeed.settings.DeckThemeManager
import com.sbf.lightspeed.system.*
import com.sbf.lightspeed.ui.theme.LightspeedStyle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 12-Theme Floating Folder Modal Dialog.
 * Shows resolved icons and human-readable labels for pinned apps and deep shortcuts.
 */
class LightspeedFolderModalActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            val lsColors = LightspeedStyle.colors
            val snapshot = remember { DeckThemeManager.getSnapshot(this) }
            val prefs = remember { defaultPrefs() }
            val activeSet = remember { prefs.getInt(LightspeedPreferences.KEY_LAST_ACTIVE_SET_INDEX, 0) }

            var apps by remember {
                mutableStateOf(CockpitGearRepository.getAppsForActiveGear(this, false, activeSet, 0))
            }

            val pickerLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
                if (result.resultCode == Activity.RESULT_OK) {
                    apps = CockpitGearRepository.getAppsForActiveGear(this, false, activeSet, 0)
                    LightspeedFolderWidgetProvider.notifyUpdate(this)
                }
            }

            DeckDialog(
                title = {
                    Text(
                        text = "TACTICAL FOLDER // ${snapshot.displayName.uppercase()}",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = lsColors.textPrimary
                    )
                },
                onDismissRequest = { finish() },
                content = {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "FOLDER ITEMS (${apps.size})",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = lsColors.textPrimary
                            )

                            IconButton(
                                onClick = {
                                    val intent = Intent(this@LightspeedFolderModalActivity, CockpitGearPickerActivity::class.java).apply {
                                        putExtra("GEAR_SET_INDEX", activeSet)
                                        putExtra("SLOT_INDEX", apps.size)
                                    }
                                    pickerLauncher.launch(intent)
                                }
                            ) {
                                Icon(Icons.Default.Add, contentDescription = "Add Item", tint = lsColors.accentInfo)
                            }
                        }

                        if (apps.isEmpty()) {
                            Text(
                                text = "No items pinned in this tactical gear set yet. Tap + to add apps, deep shortcuts, or actions.",
                                fontSize = 11.sp,
                                color = lsColors.textSecondary,
                                modifier = Modifier.padding(vertical = 12.dp)
                            )
                        } else {
                            LazyColumn(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(max = 380.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                items(apps, key = { it }) { token ->
                                    FolderItemRow(
                                        context = this@LightspeedFolderModalActivity,
                                        token = token,
                                        onLaunch = {
                                            ActionDispatcher.executeDirect(this@LightspeedFolderModalActivity, token)
                                            finish()
                                        },
                                        onRemove = {
                                            val setsList = CockpitGearRepository.getGearSetsOrder(this@LightspeedFolderModalActivity, false)
                                            val setId = if (activeSet in setsList.indices) setsList[activeSet] else activeSet.toString()
                                            val updated = apps.filter { it != token }
                                            prefs.edit().putString("gear_set_${setId}_ring_0_packages", updated.joinToString(",")).apply()
                                            CockpitGearRepository.clearCache()
                                            apps = CockpitGearRepository.getAppsForActiveGear(this@LightspeedFolderModalActivity, false, activeSet, 0)
                                            LightspeedFolderWidgetProvider.notifyUpdate(this@LightspeedFolderModalActivity)
                                        }
                                    )
                                }
                            }
                        }
                    }
                }
            )
        }
    }

    @Composable
    private fun FolderItemRow(
        context: Context,
        token: String,
        onLaunch: () -> Unit,
        onRemove: () -> Unit
    ) {
        val lsColors = LightspeedStyle.colors
        val label = remember(token) { LightspeedShortcutManager.resolveLabel(context, token) }
        val iconBitmap by produceState<Bitmap?>(initialValue = null, key1 = token) {
            value = withContext(Dispatchers.IO) {
                LightspeedIconManager.getIconBitmap(context, token, useCache = true)
                    ?: LightspeedShortcutManager.resolveIconBitmap(context, token, useCache = true)
            }
        }

        DeckCard(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onLaunch() },
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    modifier = Modifier.weight(1f),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    val bmp = iconBitmap
                    if (bmp != null) {
                        Image(
                            bitmap = bmp.asImageBitmap(),
                            contentDescription = label,
                            modifier = Modifier
                                .size(32.dp)
                                .clip(CircleShape)
                        )
                    } else {
                        Icon(
                            Icons.Default.Apps,
                            contentDescription = label,
                            tint = lsColors.accentInfo,
                            modifier = Modifier.size(32.dp)
                        )
                    }

                    Column {
                        Text(
                            text = label,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium,
                            color = lsColors.textPrimary
                        )
                    }
                }

                IconButton(onClick = onRemove) {
                    Icon(Icons.Default.Close, contentDescription = "Remove", tint = lsColors.textSecondary)
                }
            }
        }
    }
}
