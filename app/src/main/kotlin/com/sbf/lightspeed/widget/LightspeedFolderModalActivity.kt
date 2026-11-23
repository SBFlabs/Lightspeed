package com.sbf.lightspeed.widget

import android.app.Activity
import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
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
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import com.sbf.lightspeed.CockpitGearPickerActivity
import com.sbf.lightspeed.ui.theme.LightspeedTokens
import com.sbf.lightspeed.settings.DeckCard
import com.sbf.lightspeed.settings.DeckGlassCardSurface
import com.sbf.lightspeed.settings.DeckThemeManager
import com.sbf.lightspeed.settings.blockScrimPassthrough
import com.sbf.lightspeed.settings.overlayScrimDismiss
import com.sbf.lightspeed.system.*
import com.sbf.lightspeed.ui.theme.LightspeedStyle
import com.sbf.lightspeed.ui.theme.LightspeedTheme
import com.sbf.lightspeed.ui.theme.LocalDeckTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 12-Theme Expanding Tactical Folder Modal Activity.
 * Displays a clean, resizable launcher grid with 1-tap app launch,
 * independent app storage per widget ID, and seamless edit mode for adding/removing apps.
 */
class LightspeedFolderModalActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        // Make window completely transparent & edge-to-edge with zero black status/nav bars
        WindowCompat.setDecorFitsSystemWindows(window, false)
        window.statusBarColor = android.graphics.Color.TRANSPARENT
        window.navigationBarColor = android.graphics.Color.TRANSPARENT
        window.clearFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)

        val appWidgetId = intent?.getIntExtra(
            AppWidgetManager.EXTRA_APPWIDGET_ID,
            AppWidgetManager.INVALID_APPWIDGET_ID
        ) ?: AppWidgetManager.INVALID_APPWIDGET_ID

        val folderPrefKey = if (appWidgetId != AppWidgetManager.INVALID_APPWIDGET_ID) {
            "folder_apps_$appWidgetId"
        } else {
            "folder_apps_default"
        }

        setContent {
            val snapshot = remember { DeckThemeManager.getSnapshot(this) }
            val lsColors = LightspeedStyle.colors
            var isEditMode by remember { mutableStateOf(false) }

            var apps by remember {
                mutableStateOf(LightspeedFolderWidgetProvider.getFolderApps(this, appWidgetId))
            }

            val pickerLauncher = rememberLauncherForActivityResult(
                ActivityResultContracts.StartActivityForResult()
            ) { result ->
                if (result.resultCode == Activity.RESULT_OK) {
                    apps = LightspeedFolderWidgetProvider.getFolderApps(this, appWidgetId)
                    LightspeedFolderWidgetProvider.notifyUpdate(this)
                }
            }

            CompositionLocalProvider(LocalDeckTheme provides snapshot) {
                LightspeedTheme(forceDark = true) {
                    // True full-screen floating overlay with zero black scrims or status/navigation bars
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color.Transparent),
                        contentAlignment = Alignment.Center
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .overlayScrimDismiss(
                                    context = this@LightspeedFolderModalActivity,
                                    onTapOutside = { finish() }
                                )
                        )
                        DeckGlassCardSurface(
                            modifier = Modifier
                                .padding(horizontal = 24.dp, vertical = 48.dp)
                                .fillMaxWidth()
                                .widthIn(max = 420.dp)
                                .wrapContentHeight()
                                .blockScrimPassthrough(),
                            isFlyout = true
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp),
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                // Header Row
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "FOLDER (${apps.size})",
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = lsColors.textPrimary
                                    )
                                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                        IconButton(
                                            onClick = { isEditMode = !isEditMode },
                                            modifier = Modifier.size(28.dp)
                                        ) {
                                            Icon(
                                                imageVector = if (isEditMode) Icons.Default.Close else Icons.Default.Edit,
                                                contentDescription = if (isEditMode) "Done" else "Edit",
                                                tint = if (isEditMode) lsColors.accentWarn else lsColors.accentInfo,
                                                modifier = Modifier.size(16.dp)
                                            )
                                        }
                                        IconButton(
                                            onClick = { finish() },
                                            modifier = Modifier.size(28.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Close,
                                                contentDescription = "Close",
                                                tint = lsColors.textSecondary,
                                                modifier = Modifier.size(16.dp)
                                            )
                                        }
                                    }
                                }

                                if (apps.isEmpty() && !isEditMode) {
                                    Text(
                                        text = "No items in this folder yet. Tap edit icon above to add apps.",
                                        fontSize = 11.sp,
                                        color = lsColors.textSecondary,
                                        modifier = Modifier.padding(vertical = 16.dp)
                                    )
                                } else {
                                    val displayItems = if (isEditMode) apps + "__ADD_ACTION__" else apps
                                    LazyVerticalGrid(
                                        columns = GridCells.Fixed(4),
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .heightIn(min = 100.dp, max = 360.dp),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                        verticalArrangement = Arrangement.spacedBy(12.dp),
                                        contentPadding = PaddingValues(vertical = 4.dp)
                                    ) {
                                        items(displayItems, key = { it }) { token ->
                                            if (token == "__ADD_ACTION__") {
                                                AddAppTile(
                                                    onClick = {
                                                        val intent = Intent(this@LightspeedFolderModalActivity, CockpitGearPickerActivity::class.java).apply {
                                                            putExtra("CUSTOM_LIST_PREF_KEY", folderPrefKey)
                                                            putExtra("CUSTOM_LIST_TITLE", "Folder Items")
                                                            putExtra("SLOT_INDEX", apps.size)
                                                        }
                                                        pickerLauncher.launch(intent)
                                                    }
                                                )
                                            } else {
                                                FolderGridItem(
                                                    context = this@LightspeedFolderModalActivity,
                                                    token = token,
                                                    isEditMode = isEditMode,
                                                    onLaunch = {
                                                        ActionDispatcher.executeDirect(this@LightspeedFolderModalActivity, token)
                                                        finish()
                                                    },
                                                    onRemove = {
                                                        val updated = apps.filter { it != token }
                                                        apps = updated
                                                        LightspeedFolderWidgetProvider.saveFolderApps(this@LightspeedFolderModalActivity, appWidgetId, updated)
                                                    }
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    @Composable
    private fun FolderGridItem(
        context: Context,
        token: String,
        isEditMode: Boolean,
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

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .clickable {
                    if (isEditMode) onRemove() else onLaunch()
                }
                .padding(vertical = 4.dp),
            contentAlignment = Alignment.TopCenter
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(4.dp),
                modifier = Modifier.padding(horizontal = 2.dp)
            ) {
                val bmp = iconBitmap
                if (bmp != null) {
                    Image(
                        bitmap = bmp.asImageBitmap(),
                        contentDescription = label,
                        modifier = Modifier
                            .size(46.dp)
                            .clip(CircleShape)
                    )
                } else {
                    Box(
                        modifier = Modifier
                            .size(46.dp)
                            .clip(CircleShape)
                            .background(lsColors.surface),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Default.Apps,
                            contentDescription = label,
                            tint = lsColors.accentInfo,
                            modifier = Modifier.size(28.dp)
                        )
                    }
                }

                Text(
                    text = label,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Medium,
                    color = lsColors.textPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
            }

            if (isEditMode) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .size(18.dp)
                        .clip(CircleShape)
                        .background(LightspeedTokens.StatusCrimson)
                        .clickable { onRemove() },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Default.Close,
                        contentDescription = "Remove",
                        tint = Color.White,
                        modifier = Modifier.size(12.dp)
                    )
                }
            }
        }
    }

    @Composable
    private fun AddAppTile(onClick: () -> Unit) {
        val lsColors = LightspeedStyle.colors
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(4.dp),
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .clickable { onClick() }
                .padding(vertical = 4.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(46.dp)
                    .clip(CircleShape)
                    .border(1.5.dp, lsColors.accentInfo, CircleShape)
                    .background(lsColors.surface),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Default.Add,
                    contentDescription = "Add App",
                    tint = lsColors.accentInfo,
                    modifier = Modifier.size(24.dp)
                )
            }

            Text(
                text = "Add",
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                color = lsColors.accentInfo,
                textAlign = TextAlign.Center
            )
        }
    }
}
