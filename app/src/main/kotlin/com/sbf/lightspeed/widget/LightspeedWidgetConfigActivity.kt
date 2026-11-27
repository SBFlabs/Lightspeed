package com.sbf.lightspeed.widget

import android.app.Activity
import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
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
import androidx.core.view.WindowCompat
import com.sbf.lightspeed.CockpitGearPickerActivity
import com.sbf.lightspeed.IconPackBrowserModal
import com.sbf.lightspeed.R
import com.sbf.lightspeed.settings.DeckButton
import com.sbf.lightspeed.settings.DeckCard
import com.sbf.lightspeed.settings.DeckDialog
import com.sbf.lightspeed.settings.DeckSlider
import com.sbf.lightspeed.settings.DeckThemeManager
import com.sbf.lightspeed.settings.SpacePodShortcutActivity
import com.sbf.lightspeed.system.*
import com.sbf.lightspeed.ui.theme.LightspeedStyle
import com.sbf.lightspeed.ui.theme.LightspeedTheme
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
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        LightspeedLocaleEngine.init(this)
        setResult(Activity.RESULT_CANCELED)

        // Make window completely transparent & edge-to-edge with zero black status/nav bars
        WindowCompat.setDecorFitsSystemWindows(window, false)
        window.statusBarColor = android.graphics.Color.TRANSPARENT
        window.navigationBarColor = android.graphics.Color.TRANSPARENT
        window.clearFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)

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
            remember { DeckThemeManager.getSnapshot(this) }
            LightspeedTheme(forceDark = true) {
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
                var hideLabel by remember(selectedPod, reloadTrigger) { mutableStateOf(prefs.getBoolean("pref_space_pod_${selectedPod}_hide_label", false)) }
                var customBmp by remember(selectedPod, reloadTrigger) {
                    mutableStateOf<Bitmap?>(LightspeedIconManager.getCustomShortcutBitmap(this@LightspeedWidgetConfigActivity, "space_pod_$selectedPod"))
                }

                var widgetThemeMode by remember {
                    mutableStateOf(prefs.getString(LightspeedPreferences.KEY_WIDGET_THEME_MODE, "theme_12") ?: "theme_12")
                }
                var widgetOpacity by remember {
                    mutableFloatStateOf(prefs.getFloat(LightspeedPreferences.KEY_WIDGET_BACKGROUND_OPACITY, 0.85f))
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

                if (showIconPacks) {
                    DeckDialog(onDismissRequest = { showIconPacks = false }) {
                        IconPackBrowserModal(
                            onDismiss = { showIconPacks = false },
                            onIconSelected = { bmp ->
                                LightspeedIconManager.saveCustomShortcutBitmap(this@LightspeedWidgetConfigActivity, "space_pod_$selectedPod", bmp)
                                customBmp = bmp
                                showIconPacks = false
                                reloadTrigger++
                            }
                        )
                    }
                } else {
                    DeckDialog(onDismissRequest = { finish() }, title = {
                        Text(
                            if (appWidgetId != AppWidgetManager.INVALID_APPWIDGET_ID) lsString(R.string.pod_cfg_widget_title) else lsString(R.string.pod_cfg_studio_title),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = lsColors.textPrimary
                        )
                    }) {
                        Row(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            for (pod in 1..4) {
                                DeckButton(onClick = { selectedPod = pod }, modifier = Modifier.weight(1f), isActive = selectedPod == pod) {
                                    Text(lsString(R.string.pod_btn_upper_fmt, pod), fontSize = 11.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(vertical = 4.dp))
                                }
                            }
                        }
                        Spacer(modifier = Modifier.height(10.dp))
                        PodActionRow(lsString(R.string.pod_g_click).uppercase(), clickToken) {
                            pickerLauncher.launch(Intent(this@LightspeedWidgetConfigActivity, CockpitGearPickerActivity::class.java).apply {
                                putExtra("SINGLE_SELECT_PREF_KEY", clickPref); putExtra("SINGLE_SELECT_TITLE", lsString(R.string.pod_picker_title_fmt, selectedPod, lsString(R.string.pod_g_click)))
                            })
                        }
                        Spacer(modifier = Modifier.height(6.dp))
                        PodActionRow(lsString(R.string.pod_g_dclick).uppercase(), dblClickToken) {
                            pickerLauncher.launch(Intent(this@LightspeedWidgetConfigActivity, CockpitGearPickerActivity::class.java).apply {
                                putExtra("SINGLE_SELECT_PREF_KEY", dblClickPref); putExtra("SINGLE_SELECT_TITLE", lsString(R.string.pod_picker_title_fmt, selectedPod, lsString(R.string.pod_g_dclick)))
                            })
                        }
                        Spacer(modifier = Modifier.height(12.dp))
                        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            Box(modifier = Modifier.size(46.dp).clip(CircleShape).border(1.dp, lsColors.borderSubtle, CircleShape), contentAlignment = Alignment.Center) {
                                val resolvedBmp = customBmp ?: LightspeedIconManager.getIconBitmap(this@LightspeedWidgetConfigActivity, clickToken.ifBlank { "system:flashlight" }, true)
                                if (resolvedBmp != null) Image(bitmap = resolvedBmp.asImageBitmap(), contentDescription = null, modifier = Modifier.size(32.dp))
                                else Icon(painterResource(R.drawable.ic_qs_flight_armed), null, tint = lsColors.accentInfo, modifier = Modifier.size(24.dp))
                            }
                            OutlinedTextField(
                                value = labelInput,
                                onValueChange = {
                                    labelInput = it
                                    prefs.edit().putString("pref_space_pod_${selectedPod}_custom_label", it.trim()).apply()
                                    SpacePodWidgetProvider.notifyUpdate(this@LightspeedWidgetConfigActivity)
                                },
                                label = { Text(lsString(R.string.edit_display_name), fontSize = 11.sp) },
                                singleLine = true,
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(10.dp),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = lsColors.accent,
                                    unfocusedBorderColor = lsColors.borderSubtle,
                                    focusedLabelColor = lsColors.accent,
                                    unfocusedLabelColor = lsColors.textSecondary,
                                    focusedTextColor = lsColors.textPrimary,
                                    unfocusedTextColor = lsColors.textPrimary,
                                    cursorColor = lsColors.accent
                                )
                            )
                        }
                        Spacer(modifier = Modifier.height(6.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(lsString(R.string.pod_hide_label), fontSize = 11.sp, color = lsColors.textSecondary)
                            com.sbf.lightspeed.settings.DeckSwitch(
                                checked = hideLabel,
                                onCheckedChange = { checked ->
                                    hideLabel = checked
                                    prefs.edit().putBoolean("pref_space_pod_${selectedPod}_hide_label", checked).apply()
                                    SpacePodWidgetProvider.notifyUpdate(this@LightspeedWidgetConfigActivity)
                                }
                            )
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                            DeckButton(onClick = { galleryLauncher.launch("image/*") }, modifier = Modifier.weight(1f)) {
                                Icon(Icons.Default.Photo, null, modifier = Modifier.size(14.dp))
                                Spacer(Modifier.width(4.dp))
                                Text(lsString(R.string.edit_btn_gallery), fontSize = 11.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(vertical = 3.dp))
                            }
                            DeckButton(onClick = { showIconPacks = true }, modifier = Modifier.weight(1f), isActive = true) {
                                Icon(Icons.Default.Category, null, modifier = Modifier.size(14.dp))
                                Spacer(Modifier.width(4.dp))
                                Text(lsString(R.string.pod_btn_packs), fontSize = 11.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(vertical = 3.dp))
                            }
                            IconButton(onClick = {
                                LightspeedIconManager.clearManualOverride(this@LightspeedWidgetConfigActivity, "space_pod_$selectedPod")
                                prefs.edit().remove("pref_space_pod_${selectedPod}_custom_label").remove("pref_space_pod_${selectedPod}_hide_label").apply()
                                customBmp = null; labelInput = ""; hideLabel = false; reloadTrigger++
                                SpacePodWidgetProvider.notifyUpdate(this@LightspeedWidgetConfigActivity)
                            }) { Icon(Icons.Default.Refresh, contentDescription = lsString(R.string.common_reset), tint = lsColors.accentDanger) }
                        }

                        Spacer(modifier = Modifier.height(10.dp))
                        HorizontalDivider(color = lsColors.borderSubtle.copy(alpha = 0.4f))
                        Spacer(modifier = Modifier.height(8.dp))

                        // Theme Mode & Opacity
                        Text(lsString(R.string.pod_theme_engine), fontSize = 10.sp, fontWeight = FontWeight.Bold, color = lsColors.textSecondary)
                        Row(modifier = Modifier.fillMaxWidth().padding(top = 4.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            val modes = listOf(
                                "theme_12" to lsString(R.string.ed_col_theme12),
                                "m3_dynamic" to lsString(R.string.pod_cfg_theme_m3),
                                "hybrid_fusion" to lsString(R.string.ed_col_hybrid)
                            )
                            for ((modeKey, modeTitle) in modes) {
                                DeckButton(
                                    onClick = {
                                        widgetThemeMode = modeKey
                                        prefs.edit().putString(LightspeedPreferences.KEY_WIDGET_THEME_MODE, modeKey).apply()
                                        SpacePodWidgetProvider.notifyUpdate(this@LightspeedWidgetConfigActivity)
                                        LightspeedFolderWidgetProvider.notifyUpdate(this@LightspeedWidgetConfigActivity)
                                        LightspeedStackWidgetProvider.notifyUpdate(this@LightspeedWidgetConfigActivity)
                                    },
                                    modifier = Modifier.weight(1f),
                                    isActive = widgetThemeMode == modeKey
                                ) {
                                    Text(modeTitle, fontSize = 9.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(vertical = 4.dp))
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(lsString(R.string.pod_bg_opacity), fontSize = 10.sp, fontWeight = FontWeight.Bold, color = lsColors.textSecondary)
                            Text("${(widgetOpacity * 100).toInt()}%", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = lsColors.accentInfo)
                        }
                        DeckSlider(
                            value = widgetOpacity,
                            onValueChange = { widgetOpacity = it },
                            onValueChangeFinished = {
                                prefs.edit().putFloat(LightspeedPreferences.KEY_WIDGET_BACKGROUND_OPACITY, widgetOpacity).apply()
                                SpacePodWidgetProvider.notifyUpdate(this@LightspeedWidgetConfigActivity)
                                LightspeedFolderWidgetProvider.notifyUpdate(this@LightspeedWidgetConfigActivity)
                                LightspeedStackWidgetProvider.notifyUpdate(this@LightspeedWidgetConfigActivity)
                            },
                            valueRange = 0f..1f,
                            modifier = Modifier.fillMaxWidth()
                        )

                        Spacer(modifier = Modifier.height(10.dp))
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            if (appWidgetId != AppWidgetManager.INVALID_APPWIDGET_ID) {
                                DeckButton(
                                    onClick = {
                                        prefs.edit().putInt("widget_pod_index_$appWidgetId", selectedPod).apply()
                                        SpacePodWidgetProvider.notifyUpdate(this@LightspeedWidgetConfigActivity)
                                        setResult(Activity.RESULT_OK, Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId))
                                        finish()
                                    },
                                    modifier = Modifier.weight(1f),
                                    isActive = true
                                ) {
                                    Text(lsString(R.string.pod_btn_save_widget), fontWeight = FontWeight.Bold, fontSize = 12.sp, modifier = Modifier.padding(vertical = 4.dp))
                                }
                            }
                            DeckButton(onClick = {
                                pinShortcut(this@LightspeedWidgetConfigActivity, selectedPod, labelInput, customBmp, clickToken)
                                if (appWidgetId == AppWidgetManager.INVALID_APPWIDGET_ID) finish()
                            }, modifier = Modifier.weight(1f)) {
                                Text(lsString(R.string.pod_btn_pin_shortcut), fontWeight = FontWeight.Bold, fontSize = 12.sp, modifier = Modifier.padding(vertical = 4.dp))
                            }
                        }
                    }
                }
            }
        }
    }

    private fun pinShortcut(ctx: Context, pod: Int, label: String, bmp: Bitmap?, token: String) {
        if (!ShortcutManagerCompat.isRequestPinShortcutSupported(ctx)) {
            Toast.makeText(ctx, lsString(R.string.pod_pin_unsup), Toast.LENGTH_SHORT).show(); return
        }
        val targetIntent = Intent(ctx, SpacePodShortcutActivity::class.java).apply {
            action = Intent.ACTION_VIEW
            putExtra(SpacePodShortcutActivity.EXTRA_POD_INDEX, pod)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val icon = bmp?.let { IconCompat.createWithBitmap(it) } ?: IconCompat.createWithResource(ctx, R.drawable.ic_qs_flight_armed)
        val title = label.ifBlank { if (token.isNotBlank()) LightspeedShortcutManager.resolveLabel(ctx, token) else lsString(R.string.pod_num_fmt, pod) }
        val info = ShortcutInfoCompat.Builder(ctx, "space_pod_shortcut_$pod").setShortLabel(title.take(12)).setIcon(icon).setIntent(targetIntent).build()
        ShortcutManagerCompat.requestPinShortcut(ctx, info, null)
        Toast.makeText(ctx, lsString(R.string.pod_req_pin_toast, title), Toast.LENGTH_SHORT).show()
    }
}

@Composable
private fun PodActionRow(title: String, token: String, onClick: () -> Unit) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val lsColors = LightspeedStyle.colors
    val label = if (token.isNotBlank()) LightspeedShortcutManager.resolveLabel(context, token) else lsString(R.string.pod_tap_to_cfg)
    DeckCard(modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).clickable { onClick() }) {
        Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text(title, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = lsColors.textPrimary)
            Text(label.take(18), fontSize = 10.sp, color = lsColors.accentInfo)
        }
    }
}
