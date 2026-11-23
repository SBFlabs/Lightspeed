package com.sbf.lightspeed.widget

import android.app.Activity
import android.appwidget.AppWidgetHost
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProviderInfo
import android.content.Intent
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import com.sbf.lightspeed.MultiWidgetContainer
import com.sbf.lightspeed.TacticalWidgetPickerModal
import com.sbf.lightspeed.settings.DeckGlassCardSurface
import com.sbf.lightspeed.settings.blockScrimPassthrough
import com.sbf.lightspeed.settings.overlayScrimDismiss
import com.sbf.lightspeed.settings.DeckThemeManager
import com.sbf.lightspeed.system.LightspeedPreferences
import com.sbf.lightspeed.system.defaultPrefs
import com.sbf.lightspeed.ui.theme.LightspeedStyle
import com.sbf.lightspeed.ui.theme.LightspeedTheme
import com.sbf.lightspeed.ui.theme.LocalDeckTheme

/**
 * 12-Theme Refueling-Bay-Style Stack Overlay Modal Activity.
 * Displays interactive widget stack floating over blurred 12-theme optical glass card.
 */
class LightspeedHomeStackOverlayActivity : ComponentActivity() {

    private var appWidgetHost: AppWidgetHost? = null
    private var appWidgetManager: AppWidgetManager? = null

    companion object {
        private const val STACK_HOST_ID = 2049
        private const val REQUEST_PICK_WIDGET = 701
        private const val REQUEST_CONFIG_WIDGET = 702

        @Volatile
        var isActive: Boolean = false
    }

    override fun onResume() {
        super.onResume()
        isActive = true
        com.sbf.lightspeed.LightspeedAccessibilityService.instance?.updateOverlaysVisibility()
    }

    override fun onPause() {
        super.onPause()
        isActive = false
        com.sbf.lightspeed.LightspeedAccessibilityService.instance?.updateOverlaysVisibility()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        // Make window completely transparent & edge-to-edge with zero black status/nav bars
        WindowCompat.setDecorFitsSystemWindows(window, false)
        window.statusBarColor = android.graphics.Color.TRANSPARENT
        window.navigationBarColor = android.graphics.Color.TRANSPARENT
        window.clearFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)

        appWidgetHost = AppWidgetHost(applicationContext, STACK_HOST_ID)
        appWidgetManager = AppWidgetManager.getInstance(applicationContext)

        try {
            appWidgetHost?.startListening()
        } catch (_: Exception) {}

        setContent {
            val snapshot = remember { DeckThemeManager.getSnapshot(this) }
            val lsColors = LightspeedStyle.colors
            val prefs = remember { defaultPrefs() }

            CompositionLocalProvider(LocalDeckTheme provides snapshot) {
                LightspeedTheme(forceDark = true) {
                    var widgetIds by remember {
                        val raw = prefs.getString(LightspeedPreferences.KEY_REFUELING_WIDGET_IDS, "") ?: ""
                        mutableStateOf(raw.split(",").filter { it.isNotBlank() }.mapNotNull { it.toIntOrNull() })
                    }
                    var showPicker by remember { mutableStateOf(false) }

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
                                    context = this@LightspeedHomeStackOverlayActivity,
                                    onTapOutside = { finish() }
                                )
                        )
                        DeckGlassCardSurface(
                            modifier = Modifier
                                .fillMaxWidth(0.92f)
                                .heightIn(min = 320.dp, max = 560.dp)
                                .blockScrimPassthrough()
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(14.dp),
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                // Header Row
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column {
                                        Text(
                                            text = "TACTICAL STACK",
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = lsColors.textPrimary
                                        )
                                        Text(
                                            text = "${widgetIds.size} Active Stack Modules",
                                            fontSize = 9.sp,
                                            color = lsColors.textSecondary
                                        )
                                    }

                                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                        IconButton(onClick = { showPicker = true }) {
                                            Icon(Icons.Default.Add, contentDescription = "Add Module", tint = lsColors.accentInfo)
                                        }
                                        IconButton(onClick = { finish() }) {
                                            Icon(Icons.Default.Close, contentDescription = "Close", tint = lsColors.textSecondary)
                                        }
                                    }
                                }

                                // Interactive Widget Container
                                Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                                    MultiWidgetContainer(
                                        activity = this@LightspeedHomeStackOverlayActivity,
                                        widgetIds = widgetIds,
                                        widgetLayoutMode = "smart_stack",
                                        isEditMode = true,
                                        isLandscape = false,
                                        appWidgetHost = appWidgetHost,
                                        appWidgetManager = appWidgetManager,
                                        onPickWidget = { showPicker = true },
                                        onRemoveWidget = { idToRemove ->
                                            val updated = widgetIds.filter { it != idToRemove }
                                            widgetIds = updated
                                            LightspeedPreferences.saveRefuelingStackWidgetIds(this@LightspeedHomeStackOverlayActivity, updated)
                                            try { appWidgetHost?.deleteAppWidgetId(idToRemove) } catch (_: Exception) {}
                                            LightspeedStackWidgetProvider.notifyUpdate(this@LightspeedHomeStackOverlayActivity)
                                        },
                                        onReorderWidget = { from, to ->
                                            val mutable = widgetIds.toMutableList()
                                            if (from in mutable.indices && to in mutable.indices) {
                                                val item = mutable.removeAt(from)
                                                mutable.add(to, item)
                                                widgetIds = mutable
                                                LightspeedPreferences.saveRefuelingStackWidgetIds(this@LightspeedHomeStackOverlayActivity, mutable)
                                                LightspeedStackWidgetProvider.notifyUpdate(this@LightspeedHomeStackOverlayActivity)
                                            }
                                        }
                                    )
                                }
                            }
                        }
                    }

                    if (showPicker) {
                        TacticalWidgetPickerModal(
                            onDismiss = { showPicker = false },
                            onSelectProvider = { providerInfo ->
                                showPicker = false
                                allocateAndBindWidget(providerInfo) { newId ->
                                    val updated = widgetIds + newId
                                    widgetIds = updated
                                    LightspeedPreferences.saveRefuelingStackWidgetIds(this@LightspeedHomeStackOverlayActivity, updated)
                                    LightspeedStackWidgetProvider.notifyUpdate(this@LightspeedHomeStackOverlayActivity)
                                }
                            }
                        )
                    }
                }
            }
        }
    }

    private fun allocateAndBindWidget(provider: AppWidgetProviderInfo, onBound: (Int) -> Unit) {
        val host = appWidgetHost ?: return
        val manager = appWidgetManager ?: return
        val newWidgetId = host.allocateAppWidgetId()

        val canBind = manager.bindAppWidgetIdIfAllowed(newWidgetId, provider.provider)
        if (canBind) {
            checkConfigureOrSave(newWidgetId, provider, onBound)
        } else {
            val bindIntent = Intent(AppWidgetManager.ACTION_APPWIDGET_BIND).apply {
                putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, newWidgetId)
                putExtra(AppWidgetManager.EXTRA_APPWIDGET_PROVIDER, provider.provider)
            }
            startActivityForResult(bindIntent, REQUEST_PICK_WIDGET)
        }
    }

    private fun checkConfigureOrSave(widgetId: Int, provider: AppWidgetProviderInfo, onBound: (Int) -> Unit) {
        if (provider.configure != null) {
            try {
                appWidgetHost?.startAppWidgetConfigureActivityForResult(this, widgetId, 0, REQUEST_CONFIG_WIDGET, null)
            } catch (_: Exception) {
                onBound(widgetId)
            }
        } else {
            onBound(widgetId)
        }
    }

    override fun onDestroy() {
        isActive = false
        com.sbf.lightspeed.LightspeedAccessibilityService.instance?.updateOverlaysVisibility()
        super.onDestroy()
        try {
            appWidgetHost?.stopListening()
        } catch (_: Exception) {}
        if (!isChangingConfigurations) {
            com.sbf.lightspeed.TacticalWidgetPreviewCache.clear()
            com.sbf.lightspeed.system.LightspeedIconManager.temporaryPickerCache.clear()
            com.sbf.lightspeed.system.LightspeedShortcutManager.temporaryPickerCache.clear()
        }
    }
}
