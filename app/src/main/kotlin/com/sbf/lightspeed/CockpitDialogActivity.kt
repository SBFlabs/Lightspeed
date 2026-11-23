package com.sbf.lightspeed

import com.sbf.lightspeed.system.logSwallowed
import com.sbf.lightspeed.system.safeReloadPreferences

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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sbf.lightspeed.system.IconPackInfo
import com.sbf.lightspeed.system.LightspeedIconManager
import com.sbf.lightspeed.system.LightspeedPreferences
import com.sbf.lightspeed.system.LightspeedShortcutManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.sbf.lightspeed.settings.DeckGlassCardSurface
import com.sbf.lightspeed.settings.DeckThemeManager
import com.sbf.lightspeed.settings.LightspeedInfinityDialog
import com.sbf.lightspeed.settings.blockScrimPassthrough
import com.sbf.lightspeed.settings.overlayScrimDismiss
import com.sbf.lightspeed.ui.theme.LightspeedStyle
import com.sbf.lightspeed.ui.theme.LightspeedTheme
import com.sbf.lightspeed.ui.theme.CockpitPalette
import com.sbf.lightspeed.ui.theme.LocalDeckTheme
import com.sbf.lightspeed.ui.theme.LocalDomainPalette

class CockpitDialogActivity : ComponentActivity() {

    companion object {
        const val ACTION_RENAME_GEAR = "com.sbf.lightspeed.action.RENAME_GEAR"
        const val ACTION_EDIT_ITEM = "com.sbf.lightspeed.action.EDIT_ITEM"
        const val ACTION_CONFIRM_DEEP_ACTIVITY = "com.sbf.lightspeed.action.CONFIRM_DEEP_ACTIVITY"
        const val ACTION_SHOW_INFINITY = "com.sbf.lightspeed.action.SHOW_INFINITY"
        const val ACTION_DEEP_SHORTCUTS = "com.sbf.lightspeed.action.DEEP_SHORTCUTS"
        const val ACTION_PICK_CATEGORY_HUD_WIDGET = "com.sbf.lightspeed.action.PICK_CATEGORY_HUD_WIDGET"
        const val ACTION_CONFIGURE_CATEGORY_HUD_WIDGET = "com.sbf.lightspeed.action.CONFIGURE_CATEGORY_HUD_WIDGET"
        const val ACTION_TIMELINE_COUNTDOWN = "com.sbf.lightspeed.action.TIMELINE_COUNTDOWN"

        const val EXTRA_SET_ID = "EXTRA_SET_ID"
        const val EXTRA_SET_INDEX = "EXTRA_SET_INDEX"
        const val EXTRA_CURRENT_NAME = "EXTRA_CURRENT_NAME"
        const val EXTRA_TOKEN = "EXTRA_TOKEN"
        const val EXTRA_PACKAGE_NAME = "EXTRA_PACKAGE_NAME"
        const val EXTRA_CATEGORY_ID = "EXTRA_CATEGORY_ID"
        const val EXTRA_CATEGORY_LABEL = "EXTRA_CATEGORY_LABEL"
        const val EXTRA_RETURN_CATEGORY_ID = "EXTRA_RETURN_CATEGORY_ID"
        const val EXTRA_HUD_SLOT_INDEX = "EXTRA_HUD_SLOT_INDEX"
        const val EXTRA_WIDGET_ID = "EXTRA_WIDGET_ID"
        const val EXTRA_OPEN_ICON_PACK_BROWSER = "EXTRA_OPEN_ICON_PACK_BROWSER"

        private const val REQUEST_BIND_HUD_WIDGET = 7050
        private const val REQUEST_CONFIG_HUD_WIDGET = 7051
        private const val REQUEST_CONFIG_EXISTING_HUD_WIDGET = 7052

        @Volatile
        var isActive: Boolean = false
    }

    private var appWidgetHost: android.appwidget.AppWidgetHost? = null
    private var pendingHudWidgetId: Int = android.appwidget.AppWidgetManager.INVALID_APPWIDGET_ID
    private var pendingHudCategoryId: String? = null
    private var pendingHudCategoryLabel: String? = null
    private var pendingHudCandidate: InstalledWidgetCandidate? = null
    private var pendingHudSlotIndex: Int = 1

    override fun onResume() {
        super.onResume()
        isActive = true
        DeckThemeManager.getSnapshot(this)
        LightspeedAccessibilityService.instance?.updateOverlaysVisibility()
    }

    override fun onPause() {
        super.onPause()
        isActive = false
        LightspeedAccessibilityService.instance?.updateOverlaysVisibility()
    }

    override fun finish() {
        finishAndRemoveTask()
        super.finish()
        overrideZeroTransition()
    }

    override fun onDestroy() {
        isActive = false
        LightspeedAccessibilityService.instance?.updateOverlaysVisibility()
        super.onDestroy()
        try {
            appWidgetHost?.stopListening()
        } catch (_: Exception) {}
        if (!isChangingConfigurations) {
            TacticalWidgetPreviewCache.clear()
            LightspeedIconManager.temporaryPickerCache.clear()
            LightspeedShortcutManager.temporaryPickerCache.clear()
        }
        val setIndex = intent.getIntExtra(EXTRA_SET_INDEX, -1)
        val returnCatId = intent.getStringExtra(EXTRA_RETURN_CATEGORY_ID)
            ?.ifBlank { null }
            ?: intent.getStringExtra(EXTRA_CATEGORY_ID)?.ifBlank { null }
        if (!com.sbf.lightspeed.system.LightspeedOverlayDismissCoordinator.shouldSuppressReopen()) {
            if (setIndex >= 0) {
                try {
                    LightspeedAccessibilityService.instance?.reopenCockpitHangar(setIndex)
                } catch (e: Exception) { logSwallowed("CockpitDialogActivity", "onDestroy:77", e) }
            } else if (!returnCatId.isNullOrBlank()) {
                try {
                    LightspeedAccessibilityService.instance?.reopenCategoryCruise(returnCatId)
                } catch (e: Exception) { logSwallowed("CockpitDialogActivity", "onDestroy:reopenCategoryCruise", e) }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val action = intent.action ?: ACTION_RENAME_GEAR

        if (action == ACTION_CONFIGURE_CATEGORY_HUD_WIDGET) {
            val widgetId = intent.getIntExtra(EXTRA_WIDGET_ID, android.appwidget.AppWidgetManager.INVALID_APPWIDGET_ID)
            if (widgetId > 0) {
                try {
                    val host = appWidgetHost ?: android.appwidget.AppWidgetHost(applicationContext, CRUISE_HUD_HOST_ID).also { appWidgetHost = it }
                    host.startAppWidgetConfigureActivityForResult(this, widgetId, 0, REQUEST_CONFIG_EXISTING_HUD_WIDGET, null)
                    return
                } catch (e: Exception) {
                    logSwallowed("CockpitDialogActivity", "configureWidget", e)
                }
            }
            finish()
            return
        }

        setContent {
            val isWidgetPicker = (action == ACTION_PICK_CATEGORY_HUD_WIDGET)
            LightspeedTheme(forceDark = true) {
                val activeSnapshot = LocalDeckTheme.current
                val pickerBackdropAlpha = activeSnapshot.dimAmount.coerceIn(0.32f, 0.72f)
                CompositionLocalProvider(LocalDomainPalette provides CockpitPalette) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(Color.Black.copy(alpha = if (isWidgetPicker) pickerBackdropAlpha else 0.55f))
                                .overlayScrimDismiss(
                                    context = this@CockpitDialogActivity,
                                    onTapOutside = { finish() }
                                )
                        )
                        if (isWidgetPicker) {
                            DeckGlassCardSurface(
                                modifier = Modifier
                                    .fillMaxWidth(0.95f)
                                    .fillMaxHeight(0.90f)
                                    .blockScrimPassthrough()
                            ) {
                                Box(modifier = Modifier.padding(14.dp)) {
                                    CategoryHudWidgetPickerContent(
                                        categoryId = intent.getStringExtra(EXTRA_CATEGORY_ID) ?: "comm",
                                        categoryLabel = intent.getStringExtra(EXTRA_CATEGORY_LABEL) ?: "Category",
                                        slotIndex = intent.getIntExtra(EXTRA_HUD_SLOT_INDEX, 1),
                                        onDismiss = { finish() }
                                    )
                                }
                            }
                        } else {
                            val maxDialogH = (androidx.compose.ui.platform.LocalConfiguration.current.screenHeightDp * 0.92f).dp
                            DeckGlassCardSurface(
                                modifier = Modifier
                                    .fillMaxWidth(0.92f)
                                    .widthIn(max = 560.dp)
                                    .heightIn(max = maxDialogH)
                                    .blockScrimPassthrough(),
                                isFlyout = true
                            ) {
                                Box(
                                    modifier = Modifier
                                        .verticalScroll(androidx.compose.foundation.rememberScrollState())
                                        .padding(20.dp)
                                ) {
                                    when (action) {
                                        ACTION_RENAME_GEAR -> RenameGearContent(
                                            setId = intent.getStringExtra(EXTRA_SET_ID) ?: "0",
                                            currentName = intent.getStringExtra(EXTRA_CURRENT_NAME) ?: "SET 1",
                                            onDismiss = { finish() }
                                        )
                                        ACTION_EDIT_ITEM -> EditItemContent(
                                            token = intent.getStringExtra(EXTRA_TOKEN) ?: "",
                                            openIconBrowserImmediately = intent.getBooleanExtra(EXTRA_OPEN_ICON_PACK_BROWSER, false),
                                            onDismiss = { finish() }
                                        )
                                        ACTION_CONFIRM_DEEP_ACTIVITY -> DeepActivityWarningContent(
                                            token = intent.getStringExtra(EXTRA_TOKEN) ?: "",
                                            onDismiss = { finish() }
                                        )
                                        ACTION_SHOW_INFINITY -> LightspeedInfinityDialog(
                                            context = this@CockpitDialogActivity,
                                            onDismiss = { finish() },
                                            onUnlocked = {}
                                        )
                                        ACTION_DEEP_SHORTCUTS -> com.sbf.lightspeed.settings.DeepShortcutsPinDialogContent(
                                            context = this@CockpitDialogActivity,
                                            packageName = intent.getStringExtra(EXTRA_PACKAGE_NAME) ?: "",
                                            onDismiss = { finish() }
                                        )
                                        ACTION_TIMELINE_COUNTDOWN -> TimelineCountdownContent(
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
    }

    internal fun allocateAndBindHudWidget(
        categoryId: String,
        categoryLabel: String,
        candidate: InstalledWidgetCandidate,
        slotIndex: Int = 1
    ) {
        val host = appWidgetHost ?: android.appwidget.AppWidgetHost(applicationContext, CRUISE_HUD_HOST_ID).also { appWidgetHost = it }
        val awm = android.appwidget.AppWidgetManager.getInstance(this)
        val newWidgetId = host.allocateAppWidgetId()
        val providerInfo = awm.installedProviders.firstOrNull { it.provider.flattenToShortString() == candidate.providerComponent }
        if (providerInfo == null) {
            host.deleteAppWidgetId(newWidgetId)
            return
        }

        pendingHudWidgetId = newWidgetId
        pendingHudCategoryId = categoryId
        pendingHudCategoryLabel = categoryLabel
        pendingHudCandidate = candidate
        pendingHudSlotIndex = slotIndex

        val canBind = awm.bindAppWidgetIdIfAllowed(newWidgetId, providerInfo.provider)
        if (canBind) {
            checkConfigureOrSaveHudWidget(newWidgetId, providerInfo, categoryId, candidate)
        } else {
            val bindIntent = Intent(android.appwidget.AppWidgetManager.ACTION_APPWIDGET_BIND).apply {
                putExtra(android.appwidget.AppWidgetManager.EXTRA_APPWIDGET_ID, newWidgetId)
                putExtra(android.appwidget.AppWidgetManager.EXTRA_APPWIDGET_PROVIDER, providerInfo.provider)
            }
            try {
                startActivityForResult(bindIntent, REQUEST_BIND_HUD_WIDGET)
            } catch (_: Exception) {
                checkConfigureOrSaveHudWidget(newWidgetId, providerInfo, categoryId, candidate)
            }
        }
    }

    private fun checkConfigureOrSaveHudWidget(
        widgetId: Int,
        provider: android.appwidget.AppWidgetProviderInfo,
        categoryId: String,
        candidate: InstalledWidgetCandidate
    ) {
        if (provider.configure != null) {
            try {
                val host = appWidgetHost ?: android.appwidget.AppWidgetHost(applicationContext, CRUISE_HUD_HOST_ID).also { appWidgetHost = it }
                host.startAppWidgetConfigureActivityForResult(this, widgetId, 0, REQUEST_CONFIG_HUD_WIDGET, null)
            } catch (_: Exception) {
                saveBoundHudWidget(widgetId, categoryId, candidate)
            }
        } else {
            saveBoundHudWidget(widgetId, categoryId, candidate)
        }
    }

    private fun saveBoundHudWidget(
        widgetId: Int,
        categoryId: String,
        candidate: InstalledWidgetCandidate
    ) {
        val isLandscape = resources.configuration.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE
        if (pendingHudSlotIndex <= 0) {
            // Append as new widget or bind first unbound placeholder without overwriting Slot 1/2
            CategoryHudManager.bindOrAddHudWidget(this, categoryId, widgetId, isLandscape, targetSlotIndex = -1)
        } else {
            // Explicitly binding specific slot index (e.g. from Slot 1 or Slot 2 editor)
            val isSlot2 = (pendingHudSlotIndex == 2)
            val modeKey = if (isSlot2) com.sbf.lightspeed.system.LightspeedPreferences.KEY_CATEGORY_HUD_SLOT2_MODE_PREFIX + categoryId else com.sbf.lightspeed.system.LightspeedPreferences.KEY_CATEGORY_HUD_MODE_PREFIX + categoryId
            val idKey = if (isSlot2) com.sbf.lightspeed.system.LightspeedPreferences.KEY_CATEGORY_HUD_SLOT2_WIDGET_ID_PREFIX + categoryId else com.sbf.lightspeed.system.LightspeedPreferences.KEY_CATEGORY_HUD_WIDGET_ID_PREFIX + categoryId
            val labelKey = if (isSlot2) com.sbf.lightspeed.system.LightspeedPreferences.KEY_CATEGORY_HUD_SLOT2_WIDGET_LABEL_PREFIX + categoryId else com.sbf.lightspeed.system.LightspeedPreferences.KEY_CATEGORY_HUD_WIDGET_LABEL_PREFIX + categoryId
            val pkgKey = if (isSlot2) com.sbf.lightspeed.system.LightspeedPreferences.KEY_CATEGORY_HUD_SLOT2_WIDGET_PKG_PREFIX + categoryId else com.sbf.lightspeed.system.LightspeedPreferences.KEY_CATEGORY_HUD_WIDGET_PKG_PREFIX + categoryId

            defaultPrefs().edit()
                .putString(modeKey, LightspeedCruiseHudPods.MODE_ANDROID_WIDGET)
                .putInt(idKey, widgetId)
                .putString(labelKey, "${candidate.appLabel} · ${candidate.widgetLabel}")
                .putString(pkgKey, candidate.packageName)
                .apply()

            CategoryHudManager.bindOrAddHudWidget(this, categoryId, widgetId, isLandscape, targetSlotIndex = pendingHudSlotIndex)
        }
        LightspeedCruiseOverlay.instance?.categoryGridCache?.clear()

        safeReloadPreferences()
        val slotLabel = if (pendingHudSlotIndex == 2) "Slot 2" else if (pendingHudSlotIndex == 1) "Slot 1" else "HUD"
        Toast.makeText(this, "Bound ${candidate.widgetLabel} to category $slotLabel", Toast.LENGTH_SHORT).show()
        finish()
    }

    @Suppress("DEPRECATION")
    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQUEST_BIND_HUD_WIDGET) {
            if (resultCode == RESULT_OK && pendingHudWidgetId > 0 && pendingHudCandidate != null) {
                val awm = android.appwidget.AppWidgetManager.getInstance(this)
                val providerInfo = awm.installedProviders.firstOrNull { it.provider.flattenToShortString() == pendingHudCandidate!!.providerComponent }
                if (providerInfo != null) {
                    checkConfigureOrSaveHudWidget(pendingHudWidgetId, providerInfo, pendingHudCategoryId ?: "comm", pendingHudCandidate!!)
                } else {
                    saveBoundHudWidget(pendingHudWidgetId, pendingHudCategoryId ?: "comm", pendingHudCandidate!!)
                }
            } else {
                if (pendingHudWidgetId > 0) {
                    try { appWidgetHost?.deleteAppWidgetId(pendingHudWidgetId) } catch (_: Exception) {}
                }
                pendingHudWidgetId = android.appwidget.AppWidgetManager.INVALID_APPWIDGET_ID
            }
        } else if (requestCode == REQUEST_CONFIG_HUD_WIDGET) {
            if (resultCode == RESULT_OK && pendingHudWidgetId > 0 && pendingHudCandidate != null) {
                saveBoundHudWidget(pendingHudWidgetId, pendingHudCategoryId ?: "comm", pendingHudCandidate!!)
            } else {
                if (pendingHudWidgetId > 0) {
                    try { appWidgetHost?.deleteAppWidgetId(pendingHudWidgetId) } catch (_: Exception) {}
                }
                pendingHudWidgetId = android.appwidget.AppWidgetManager.INVALID_APPWIDGET_ID
            }
        } else if (requestCode == REQUEST_CONFIG_EXISTING_HUD_WIDGET) {
            LightspeedCruiseOverlay.instance?.updateCategoryAppWidgetView()
            finish()
        }
    }
}
