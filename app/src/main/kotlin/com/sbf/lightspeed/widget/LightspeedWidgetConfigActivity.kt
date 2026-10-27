package com.sbf.lightspeed.widget

import android.app.Activity
import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Category
import androidx.compose.material.icons.filled.Photo
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.pm.ShortcutInfoCompat
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.core.graphics.drawable.IconCompat
import com.sbf.lightspeed.CockpitGearPickerActivity
import com.sbf.lightspeed.IconPackBrowserModal
import com.sbf.lightspeed.R
import com.sbf.lightspeed.settings.DeckButton
import com.sbf.lightspeed.settings.DeckCard
import com.sbf.lightspeed.settings.DeckDialog
import com.sbf.lightspeed.settings.DeckThemeManager
import com.sbf.lightspeed.settings.SpacePodShortcutActivity
import com.sbf.lightspeed.system.*
import com.sbf.lightspeed.ui.theme.LightspeedStyle
import com.sbf.lightspeed.ui.theme.LocalDeckTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Native AppWidget and Home Screen Shortcut Configuration Activity (< 280 lines).
 * Configures Pod 1-4 gestures (Single & Double Click), custom icons (Gallery/Pack), and labels.
 */
class LightspeedWidgetConfigActivity : ComponentActivity() {

    companion object {
        const val EXTRA_POD_INDEX = "extra_pod_index"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setResult(Activity.RESULT_CANCELED)

        val appWidgetId = intent?.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID) ?: AppWidgetManager.INVALID_APPWIDGET_ID
        if (appWidgetId != AppWidgetManager.INVALID_APPWIDGET_ID) {
            val provider = AppWidgetManager.getInstance(this).getAppWidgetInfo(appWidgetId)?.provider?.className ?: ""
            val forwardCls = when {
                provider.contains("Folder") -> LightspeedFolderModalActivity::class.java
                provider.contains("Stack") -> LightspeedHomeStackOverlayActivity::class.java
                else -> null
            }
            if (forwardCls != null) {
                startActivity(Intent(this, forwardCls).apply {
                    putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
                    flags = Intent.FLAG_ACTIVITY_FORWARD_RESULT
                })
                finish(); return
            }
        }

        setContent {
            val snapshot = remember { DeckThemeManager.getSnapshot(this) }
            val lsColors = LightspeedStyle.colors
            val prefs = remember { defaultPrefs() }
            val scope = rememberCoroutineScope()
            var selectedPod by remember { mutableIntStateOf(intent?.getIntExtra(EXTRA_POD_INDEX, 1)?.coerceIn(1, 4) ?: 1) }
            var reloadTrigger by remember { mutableIntStateOf(0) }
            var showIconPacks by remember { mutableStateOf(false) }

            val clickPref = SpacePodGestureDispatcher.getActionPrefKey(selectedPod, SpacePodGestureDispatcher.GESTURE_CLICK)
            val dblClickPref = SpacePodGestureDispatcher.getActionPrefKey(selectedPod, SpacePodGestureDispatcher.GESTURE_DOUBLE_CLICK)
            val clickToken = remember(selectedPod, reloadTrigger) { prefs.getString(clickPref, "") ?: "" }
            val dblClickToken = remember(selectedPod, reloadTrigger) { prefs.getString(dblClickPref, "") ?: "" }
            val customLabel = remember(selectedPod, reloadTrigger) { prefs.getString("pref_space_pod_${selectedPod}_custom_label", "") ?: "" }
            var labelInput by remember(selectedPod, reloadTrigger) { mutableStateOf(customLabel) }
            var customBmp by remember(selectedPod, reloadTrigger) {
                mutableStateOf<Bitmap?>(LightspeedIconManager.getCustomShortcutBitmap(this@LightspeedWidgetConfigActivity, "space_pod_$selectedPod"))
            }

            val pickerLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { res ->
                if (res.resultCode == Activity.RESULT_OK) reloadTrigger++
            }
            val galleryLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
                if (uri != null) scope.launch(Dispatchers.IO) {
                    try {
                        contentResolver.openInputStream(uri)?.use { stream ->
                            val bmp = BitmapFactory.decodeStream(stream) ?: return@use
                            val scaled = Bitmap.createScaledBitmap(bmp, 128, 128, true)
                            LightspeedIconManager.saveCustomShortcutBitmap(this@LightspeedWidgetConfigActivity, "space_pod_$selectedPod", scaled)
                            withContext(Dispatchers.Main) { customBmp = scaled; reloadTrigger++ }
                        }
                    } catch (_: Exception) {}
                }
            }

            CompositionLocalProvider(LocalDeckTheme provides snapshot) {
                if (showIconPacks) {
                    IconPackBrowserModal(onDismiss = { showIconPacks = false }, onIconSelected = { bmp ->
                        LightspeedIconManager.saveCustomShortcutBitmap(this, "space_pod_$selectedPod", bmp)
                        customBmp = bmp; showIconPacks = false; reloadTrigger++
                    })
                } else {
                    DeckDialog(onDismissRequest = { finish() }, title = {
                        Text(if (appWidgetId != AppWidgetManager.INVALID_APPWIDGET_ID) "CONFIGURE SPACE POD WIDGET" else "SPACE POD STUDIO",
                            fontSize = 12.sp, fontWeight = FontWeight.Bold, color = lsColors.textPrimary)
                    }) {
                        Row(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            for (pod in 1..4) {
                                DeckButton(onClick = { selectedPod = pod }, modifier = Modifier.weight(1f), isActive = selectedPod == pod) {
                                    Text("POD $pod", fontSize = 11.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(vertical = 4.dp))
                                }
                            }
                        }
                        Spacer(modifier = Modifier.height(10.dp))
                        PodActionRow("SINGLE CLICK", clickToken) {
                            pickerLauncher.launch(Intent(this@LightspeedWidgetConfigActivity, CockpitGearPickerActivity::class.java).apply {
                                putExtra("SINGLE_SELECT_PREF_KEY", clickPref); putExtra("SINGLE_SELECT_TITLE", "Pod $selectedPod Single Click")
                            })
                        }
                        Spacer(modifier = Modifier.height(6.dp))
                        PodActionRow("DOUBLE CLICK", dblClickToken) {
                            pickerLauncher.launch(Intent(this@LightspeedWidgetConfigActivity, CockpitGearPickerActivity::class.java).apply {
                                putExtra("SINGLE_SELECT_PREF_KEY", dblClickPref); putExtra("SINGLE_SELECT_TITLE", "Pod $selectedPod Double Click")
                            })
                        }
                        Spacer(modifier = Modifier.height(12.dp))
                        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            Box(modifier = Modifier.size(46.dp).clip(CircleShape).border(1.dp, lsColors.borderSubtle, CircleShape), contentAlignment = Alignment.Center) {
                                val resolvedBmp = customBmp ?: LightspeedIconManager.getIconBitmap(this@LightspeedWidgetConfigActivity, clickToken.ifBlank { "system:flashlight" }, true)
                                if (resolvedBmp != null) Image(bitmap = resolvedBmp.asImageBitmap(), contentDescription = null, modifier = Modifier.size(32.dp))
                                else Icon(painterResource(R.drawable.ic_qs_flight_armed), null, tint = lsColors.accentInfo, modifier = Modifier.size(24.dp))
                            }
                            OutlinedTextField(value = labelInput, onValueChange = {
                                labelInput = it; prefs.edit().putString("pref_space_pod_${selectedPod}_custom_label", it.trim()).apply()
                            }, label = { Text("Display Name", fontSize = 11.sp) }, singleLine = true, modifier = Modifier.weight(1f), shape = RoundedCornerShape(10.dp))
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            OutlinedButton(onClick = { galleryLauncher.launch("image/*") }, modifier = Modifier.weight(1f), shape = RoundedCornerShape(8.dp)) {
                                Icon(Icons.Default.Photo, null, modifier = Modifier.size(14.dp)); Spacer(Modifier.width(4.dp)); Text("Gallery", fontSize = 11.sp)
                            }
                            FilledTonalButton(onClick = { showIconPacks = true }, modifier = Modifier.weight(1f), shape = RoundedCornerShape(8.dp)) {
                                Icon(Icons.Default.Category, null, modifier = Modifier.size(14.dp)); Spacer(Modifier.width(4.dp)); Text("Packs", fontSize = 11.sp)
                            }
                            IconButton(onClick = {
                                LightspeedIconManager.clearManualOverride(this@LightspeedWidgetConfigActivity, "space_pod_$selectedPod")
                                prefs.edit().remove("pref_space_pod_${selectedPod}_custom_label").apply()
                                customBmp = null; labelInput = ""; reloadTrigger++
                            }) { Icon(Icons.Default.Refresh, contentDescription = "Reset", tint = MaterialTheme.colorScheme.error) }
                        }
                        Spacer(modifier = Modifier.height(14.dp))
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            if (appWidgetId != AppWidgetManager.INVALID_APPWIDGET_ID) {
                                Button(onClick = {
                                    prefs.edit().putInt("widget_pod_index_$appWidgetId", selectedPod).apply()
                                    SpacePodWidgetProvider.notifyUpdate(this@LightspeedWidgetConfigActivity)
                                    setResult(Activity.RESULT_OK, Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId))
                                    finish()
                                }, modifier = Modifier.weight(1f), shape = RoundedCornerShape(10.dp)) {
                                    Text("Save Widget", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                }
                            }
                            DeckButton(onClick = {
                                pinShortcut(this@LightspeedWidgetConfigActivity, selectedPod, labelInput, customBmp, clickToken)
                                if (appWidgetId == AppWidgetManager.INVALID_APPWIDGET_ID) finish()
                            }, modifier = Modifier.weight(1f)) {
                                Text("Pin Shortcut", fontWeight = FontWeight.Bold, fontSize = 12.sp, modifier = Modifier.padding(vertical = 4.dp))
                            }
                        }
                    }
                }
            }
        }
    }

    private fun pinShortcut(ctx: Context, pod: Int, label: String, bmp: Bitmap?, token: String) {
        if (!ShortcutManagerCompat.isRequestPinShortcutSupported(ctx)) {
            Toast.makeText(ctx, "Launcher does not support pinning", Toast.LENGTH_SHORT).show(); return
        }
        val targetIntent = Intent(ctx, SpacePodShortcutActivity::class.java).apply {
            action = Intent.ACTION_VIEW
            putExtra(SpacePodShortcutActivity.EXTRA_POD_INDEX, pod)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val icon = bmp?.let { IconCompat.createWithBitmap(it) } ?: IconCompat.createWithResource(ctx, R.drawable.ic_qs_flight_armed)
        val title = label.ifBlank { if (token.isNotBlank()) LightspeedShortcutManager.resolveLabel(ctx, token) else "Pod $pod" }
        val info = ShortcutInfoCompat.Builder(ctx, "space_pod_shortcut_$pod").setShortLabel(title.take(12)).setIcon(icon).setIntent(targetIntent).build()
        ShortcutManagerCompat.requestPinShortcut(ctx, info, null)
        Toast.makeText(ctx, "Requested Pin for $title", Toast.LENGTH_SHORT).show()
    }
}

@Composable
private fun PodActionRow(title: String, token: String, onClick: () -> Unit) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val lsColors = LightspeedStyle.colors
    val label = if (token.isNotBlank()) LightspeedShortcutManager.resolveLabel(context, token) else "Tap to configure"
    DeckCard(modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).clickable { onClick() }) {
        Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text(title, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = lsColors.textPrimary)
            Text(label.take(18), fontSize = 10.sp, color = lsColors.accentInfo)
        }
    }
}
