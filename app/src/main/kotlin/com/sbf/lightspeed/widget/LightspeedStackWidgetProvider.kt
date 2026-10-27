package com.sbf.lightspeed.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.view.View
import android.widget.RemoteViews
import com.sbf.lightspeed.LightspeedAccessibilityService
import com.sbf.lightspeed.R
import com.sbf.lightspeed.settings.DeckThemeManager
import com.sbf.lightspeed.settings.DeckThemeSnapshot
import com.sbf.lightspeed.system.*
import com.sbf.lightspeed.system.display.LightspeedEyeCareEngine
import com.sbf.lightspeed.system.display.LightspeedSmartPixelsOverlay
import com.sbf.lightspeed.system.sonic.SonicVoiceIsolationEngine

/**
 * 12-Theme Interactive Tactical Stack Widget Provider (< 320 lines).
 * Renders real interactive cards directly on the home screen over wallpaper-blurred glass:
 * - Page 0: Tactical App & Shortcut Grid (1-tap launch)
 * - Page 1: Quick System Deck (Vocal Boost, Smart Pixels, Extra Dim, Vista Deck)
 * - Page 2: Cockpit Telemetry (Active Gear Set, Battery, Immunity, Cycle Gear)
 * - Page 3+: External Hosted Widget Cards
 */
class LightspeedStackWidgetProvider : AppWidgetProvider() {

    companion object {
        const val ACTION_PREV_PAGE = "com.sbf.lightspeed.widget.ACTION_PREV_PAGE"
        const val ACTION_NEXT_PAGE = "com.sbf.lightspeed.widget.ACTION_NEXT_PAGE"
        const val ACTION_REFRESH_THEME = "com.sbf.lightspeed.widget.ACTION_REFRESH_THEME"
        const val ACTION_LAUNCH_TOKEN = "com.sbf.lightspeed.widget.ACTION_LAUNCH_TOKEN"
        const val ACTION_TOGGLE_VOCAL_BOOST = "com.sbf.lightspeed.widget.ACTION_TOGGLE_VOCAL_BOOST"
        const val ACTION_TOGGLE_SMART_PIXELS = "com.sbf.lightspeed.widget.ACTION_TOGGLE_SMART_PIXELS"
        const val ACTION_TOGGLE_EXTRA_DIM = "com.sbf.lightspeed.widget.ACTION_TOGGLE_EXTRA_DIM"
        const val ACTION_OPEN_VISTA_DECK = "com.sbf.lightspeed.widget.ACTION_OPEN_VISTA_DECK"
        const val ACTION_CYCLE_GEAR = "com.sbf.lightspeed.widget.ACTION_CYCLE_GEAR"
        const val EXTRA_TOKEN = "extra_token"

        fun notifyUpdate(context: Context) {
            val appWidgetManager = AppWidgetManager.getInstance(context)
            val component = ComponentName(context, LightspeedStackWidgetProvider::class.java)
            val ids = appWidgetManager.getAppWidgetIds(component)
            if (ids.isNotEmpty()) {
                val intent = Intent(context, LightspeedStackWidgetProvider::class.java).apply {
                    action = AppWidgetManager.ACTION_APPWIDGET_UPDATE
                    putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, ids)
                }
                context.sendBroadcast(intent)
            }
        }
    }

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        for (appWidgetId in appWidgetIds) {
            val options = appWidgetManager.getAppWidgetOptions(appWidgetId)
            updateStackWidget(context, appWidgetManager, appWidgetId, options)
        }
        super.onUpdate(context, appWidgetManager, appWidgetIds)
    }

    override fun onAppWidgetOptionsChanged(context: Context, appWidgetManager: AppWidgetManager, appWidgetId: Int, newOptions: Bundle) {
        updateStackWidget(context, appWidgetManager, appWidgetId, newOptions)
        super.onAppWidgetOptionsChanged(context, appWidgetManager, appWidgetId, newOptions)
    }

    private fun updateStackWidget(context: Context, appWidgetManager: AppWidgetManager, appWidgetId: Int, options: Bundle) {
        val minWidthDp = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, 260)
        val minHeightDp = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, 160)
        val density = context.resources.displayMetrics.density
        val widthPx = (minWidthDp * density).toInt().coerceAtLeast(200)
        val heightPx = (minHeightDp * density).toInt().coerceAtLeast(140)

        val prefs = context.defaultPrefs()
        val snapshot = DeckThemeManager.getSnapshot(context)
        val bgBitmap = WallpaperBlurHelper.createBlurredWallpaperGlassCard(context, widthPx, heightPx, snapshot)

        val externalWidgetIds = LightspeedPreferences.getRefuelingStackWidgetIds(context).ifEmpty {
            val raw = prefs.getString(LightspeedPreferences.KEY_REFUELING_WIDGET_IDS, "") ?: ""
            raw.split(",").mapNotNull { it.trim().toIntOrNull() }
        }
        val totalPages = 3 + externalWidgetIds.size
        val currentPage = (prefs.getInt("stack_widget_page_${appWidgetId}", 0)).mod(totalPages)

        val views = RemoteViews(context.packageName, R.layout.widget_stack_layout).apply {
            setImageViewBitmap(R.id.stack_card_bg, bgBitmap)
            setTextColor(R.id.btn_stack_prev, snapshot.borderColorArgb.toInt())
            setTextColor(R.id.btn_stack_next, snapshot.borderColorArgb.toInt())
            setTextColor(R.id.btn_stack_edit, snapshot.borderColorArgb.toInt())
            setTextColor(R.id.stack_header_title, snapshot.borderColorArgb.toInt())

            wireNavigationHeader(context, this, appWidgetId)

            when (currentPage) {
                0 -> renderTacticalAppsPage(context, this, snapshot, appWidgetId)
                1 -> renderSystemDeckPage(context, this, snapshot, appWidgetId)
                2 -> renderCockpitTelemetryPage(context, this, snapshot, appWidgetId)
                else -> renderHostedWidgetPage(context, this, snapshot, appWidgetId, externalWidgetIds[currentPage - 3], currentPage, totalPages)
            }
        }

        appWidgetManager.updateAppWidget(appWidgetId, views)
    }

    private fun wireNavigationHeader(context: Context, views: RemoteViews, appWidgetId: Int) {
        val prevIntent = Intent(context, LightspeedStackWidgetProvider::class.java).apply {
            action = ACTION_PREV_PAGE
            putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
        }
        views.setOnClickPendingIntent(R.id.btn_stack_prev, PendingIntent.getBroadcast(context, appWidgetId * 10 + 1, prevIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE))

        val nextIntent = Intent(context, LightspeedStackWidgetProvider::class.java).apply {
            action = ACTION_NEXT_PAGE
            putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
        }
        views.setOnClickPendingIntent(R.id.btn_stack_next, PendingIntent.getBroadcast(context, appWidgetId * 10 + 2, nextIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE))

        val editIntent = Intent(context, LightspeedWidgetConfigActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
        }
        views.setOnClickPendingIntent(R.id.btn_stack_edit, PendingIntent.getActivity(context, appWidgetId * 10 + 3, editIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE))
    }

    private fun renderTacticalAppsPage(context: Context, views: RemoteViews, snapshot: DeckThemeSnapshot, appWidgetId: Int) {
        views.setTextViewText(R.id.stack_header_title, "TACTICAL APPS // ${snapshot.displayName.uppercase()}")
        views.setViewVisibility(R.id.page_grid_container, View.VISIBLE)
        views.setViewVisibility(R.id.page_telemetry_container, View.GONE)
        views.setViewVisibility(R.id.page_hosted_container, View.GONE)

        val activeSet = context.defaultPrefs().getInt(LightspeedPreferences.KEY_LAST_ACTIVE_SET_INDEX, 0)
        val apps = CockpitGearRepository.getAppsForActiveGear(context, false, activeSet, 0)

        val slotIds = intArrayOf(R.id.slot_1, R.id.slot_2, R.id.slot_3, R.id.slot_4)
        val iconIds = intArrayOf(R.id.slot_1_icon, R.id.slot_2_icon, R.id.slot_3_icon, R.id.slot_4_icon)
        val textIds = intArrayOf(R.id.slot_1_text, R.id.slot_2_text, R.id.slot_3_text, R.id.slot_4_text)
        val statusIds = intArrayOf(R.id.slot_1_status, R.id.slot_2_status, R.id.slot_3_status, R.id.slot_4_status)

        for (i in 0 until 4) {
            views.setViewVisibility(statusIds[i], View.GONE)
            if (i < apps.size) {
                val token = apps[i]
                val label = LightspeedShortcutManager.resolveLabel(context, token)
                val bmp = LightspeedShortcutManager.resolveIconBitmap(context, token, useCache = true)
                    ?: LightspeedIconManager.getIconBitmap(context, token, useCache = true)

                views.setViewVisibility(slotIds[i], View.VISIBLE)
                views.setTextViewText(textIds[i], label.take(12))
                if (bmp != null) views.setImageViewBitmap(iconIds[i], bmp)

                val launchIntent = Intent(context, LightspeedStackWidgetProvider::class.java).apply {
                    action = ACTION_LAUNCH_TOKEN
                    putExtra(EXTRA_TOKEN, token)
                }
                views.setOnClickPendingIntent(slotIds[i], PendingIntent.getBroadcast(context, appWidgetId * 100 + i, launchIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE))
            } else {
                views.setViewVisibility(slotIds[i], View.INVISIBLE)
            }
        }
    }

    private fun renderSystemDeckPage(context: Context, views: RemoteViews, snapshot: DeckThemeSnapshot, appWidgetId: Int) {
        views.setTextViewText(R.id.stack_header_title, "SYSTEM DECK // ${snapshot.displayName.uppercase()}")
        views.setViewVisibility(R.id.page_grid_container, View.VISIBLE)
        views.setViewVisibility(R.id.page_telemetry_container, View.GONE)
        views.setViewVisibility(R.id.page_hosted_container, View.GONE)

        val prefs = context.defaultPrefs()
        val tintArgb = snapshot.tintArgb.toInt()

        // 1. Vocal Boost
        val vocalActive = SonicVoiceIsolationEngine.isEnabled
        views.setViewVisibility(R.id.slot_1, View.VISIBLE)
        views.setTextViewText(R.id.slot_1_text, "Vocal Boost")
        views.setTextViewText(R.id.slot_1_status, if (vocalActive) "ACTIVE" else "OFF")
        views.setTextColor(R.id.slot_1_status, if (vocalActive) tintArgb else Color.GRAY)
        views.setViewVisibility(R.id.slot_1_status, View.VISIBLE)
        views.setImageViewBitmap(R.id.slot_1_icon, LightspeedStackTileHelper.createThemedTileBitmap(context, R.drawable.ic_music_note, vocalActive, tintArgb))
        views.setOnClickPendingIntent(R.id.slot_1, PendingIntent.getBroadcast(context, appWidgetId * 100 + 11, Intent(context, LightspeedStackWidgetProvider::class.java).apply { action = ACTION_TOGGLE_VOCAL_BOOST }, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE))

        // 2. Smart Pixels
        val pixelsActive = prefs.getBoolean(LightspeedPreferences.KEY_SMART_PIXELS_ENABLED, false)
        views.setViewVisibility(R.id.slot_2, View.VISIBLE)
        views.setTextViewText(R.id.slot_2_text, "Smart Pixels")
        views.setTextViewText(R.id.slot_2_status, if (pixelsActive) "ACTIVE" else "OFF")
        views.setTextColor(R.id.slot_2_status, if (pixelsActive) tintArgb else Color.GRAY)
        views.setViewVisibility(R.id.slot_2_status, View.VISIBLE)
        views.setImageViewBitmap(R.id.slot_2_icon, LightspeedStackTileHelper.createThemedTileBitmap(context, R.drawable.ic_widgets, pixelsActive, tintArgb))
        views.setOnClickPendingIntent(R.id.slot_2, PendingIntent.getBroadcast(context, appWidgetId * 100 + 12, Intent(context, LightspeedStackWidgetProvider::class.java).apply { action = ACTION_TOGGLE_SMART_PIXELS }, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE))

        // 3. Extra Dim
        val dimActive = prefs.getBoolean(LightspeedPreferences.KEY_EXTRA_DIM_ENABLED, false)
        views.setViewVisibility(R.id.slot_3, View.VISIBLE)
        views.setTextViewText(R.id.slot_3_text, "Extra Dim")
        views.setTextViewText(R.id.slot_3_status, if (dimActive) "ACTIVE" else "OFF")
        views.setTextColor(R.id.slot_3_status, if (dimActive) tintArgb else Color.GRAY)
        views.setViewVisibility(R.id.slot_3_status, View.VISIBLE)
        views.setImageViewBitmap(R.id.slot_3_icon, LightspeedStackTileHelper.createThemedTileBitmap(context, R.drawable.ic_flashlight, dimActive, tintArgb))
        views.setOnClickPendingIntent(R.id.slot_3, PendingIntent.getBroadcast(context, appWidgetId * 100 + 13, Intent(context, LightspeedStackWidgetProvider::class.java).apply { action = ACTION_TOGGLE_EXTRA_DIM }, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE))

        // 4. Vista Deck
        views.setViewVisibility(R.id.slot_4, View.VISIBLE)
        views.setTextViewText(R.id.slot_4_text, "Vista Deck")
        views.setTextViewText(R.id.slot_4_status, "OPEN ↗")
        views.setTextColor(R.id.slot_4_status, tintArgb)
        views.setViewVisibility(R.id.slot_4_status, View.VISIBLE)
        views.setImageViewBitmap(R.id.slot_4_icon, LightspeedStackTileHelper.createThemedTileBitmap(context, R.drawable.ic_qs_flight_armed, true, tintArgb))
        views.setOnClickPendingIntent(R.id.slot_4, PendingIntent.getBroadcast(context, appWidgetId * 100 + 14, Intent(context, LightspeedStackWidgetProvider::class.java).apply { action = ACTION_OPEN_VISTA_DECK }, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE))
    }

    private fun renderCockpitTelemetryPage(context: Context, views: RemoteViews, snapshot: DeckThemeSnapshot, appWidgetId: Int) {
        views.setTextViewText(R.id.stack_header_title, "COCKPIT TELEMETRY // ${snapshot.displayName.uppercase()}")
        views.setViewVisibility(R.id.page_grid_container, View.GONE)
        views.setViewVisibility(R.id.page_telemetry_container, View.VISIBLE)
        views.setViewVisibility(R.id.page_hosted_container, View.GONE)

        val prefs = context.defaultPrefs()
        val activeSetIndex = prefs.getInt(LightspeedPreferences.KEY_LAST_ACTIVE_SET_INDEX, 0)
        val gearName = CockpitGearRepository.getGearSetNameByIndex(context, false, activeSetIndex)
        val setsList = CockpitGearRepository.getGearSetsOrder(context, false)
        val totalSets = setsList.size.coerceAtLeast(1)

        views.setTextViewText(R.id.telemetry_gear_title, "GEAR: $gearName".uppercase())
        views.setTextViewText(R.id.telemetry_gear_sub, "SET ${activeSetIndex + 1} OF $totalSets • 4 APPS ARMED")
        views.setTextViewText(R.id.telemetry_battery_text, "BATTERY: ${LightspeedStackTileHelper.getBatteryTelemetry(context)} • IMMUNITY: ${LightspeedStackTileHelper.getImmunityTelemetry(context)}")
        views.setTextColor(R.id.telemetry_battery_text, snapshot.tintArgb.toInt())

        views.setOnClickPendingIntent(R.id.btn_telemetry_cycle_gear, PendingIntent.getBroadcast(context, appWidgetId * 100 + 21, Intent(context, LightspeedStackWidgetProvider::class.java).apply { action = ACTION_CYCLE_GEAR }, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE))
        val deckIntent = Intent(context, LightspeedHomeStackOverlayActivity::class.java).apply { flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP }
        views.setOnClickPendingIntent(R.id.btn_telemetry_open_deck, PendingIntent.getActivity(context, appWidgetId * 100 + 22, deckIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE))
    }

    private fun renderHostedWidgetPage(context: Context, views: RemoteViews, snapshot: DeckThemeSnapshot, appWidgetId: Int, hostedWidgetId: Int, pageIndex: Int, totalPages: Int) {
        views.setTextViewText(R.id.stack_header_title, "REFUELING BAY // MODULE $pageIndex OF $totalPages")
        views.setViewVisibility(R.id.page_grid_container, View.GONE)
        views.setViewVisibility(R.id.page_telemetry_container, View.GONE)
        views.setViewVisibility(R.id.page_hosted_container, View.VISIBLE)

        val appWidgetManager = AppWidgetManager.getInstance(context)
        val info = appWidgetManager.getAppWidgetInfo(hostedWidgetId)
        val title = info?.loadLabel(context.packageManager) ?: "Android Widget"
        val iconDrawable = try { info?.loadIcon(context, context.resources.displayMetrics.densityDpi) } catch (_: Exception) { null }

        views.setTextViewText(R.id.hosted_widget_title, title)
        views.setTextViewText(R.id.hosted_widget_sub, "TAP TO EXPAND INTERACTIVE WIDGET")
        if (iconDrawable != null) {
            val bmp = LightspeedIconManager.convertDrawableToBitmap(iconDrawable, 96)
            if (bmp != null) views.setImageViewBitmap(R.id.hosted_widget_icon, bmp)
        }

        val deckIntent = Intent(context, LightspeedHomeStackOverlayActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        views.setOnClickPendingIntent(R.id.page_hosted_container, PendingIntent.getActivity(context, appWidgetId * 100 + 31, deckIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE))
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        val prefs = context.defaultPrefs()
        val appWidgetId = intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID)
        val externalWidgetIds = LightspeedPreferences.getRefuelingStackWidgetIds(context)
        val totalPages = 3 + externalWidgetIds.size

        when (intent.action) {
            ACTION_REFRESH_THEME, AppWidgetManager.ACTION_APPWIDGET_UPDATE -> notifyUpdate(context)
            ACTION_PREV_PAGE -> {
                if (appWidgetId != AppWidgetManager.INVALID_APPWIDGET_ID) {
                    val cur = prefs.getInt("stack_widget_page_${appWidgetId}", 0)
                    val next = (cur - 1 + totalPages).mod(totalPages)
                    prefs.edit().putInt("stack_widget_page_${appWidgetId}", next).apply()
                    notifyUpdate(context)
                }
            }
            ACTION_NEXT_PAGE -> {
                if (appWidgetId != AppWidgetManager.INVALID_APPWIDGET_ID) {
                    val cur = prefs.getInt("stack_widget_page_${appWidgetId}", 0)
                    val next = (cur + 1).mod(totalPages)
                    prefs.edit().putInt("stack_widget_page_${appWidgetId}", next).apply()
                    notifyUpdate(context)
                }
            }
            ACTION_LAUNCH_TOKEN -> {
                val token = intent.getStringExtra(EXTRA_TOKEN)
                ActionDispatcher.executeDirect(context, token)
            }
            ACTION_TOGGLE_VOCAL_BOOST -> {
                val cur = SonicVoiceIsolationEngine.isEnabled
                SonicVoiceIsolationEngine.setEnabled(context, !cur)
                notifyUpdate(context)
            }
            ACTION_TOGGLE_SMART_PIXELS -> {
                val cur = prefs.getBoolean(LightspeedPreferences.KEY_SMART_PIXELS_ENABLED, false)
                val pattern = prefs.getInt(LightspeedPreferences.KEY_SMART_PIXELS_PATTERN, 25)
                LightspeedSmartPixelsOverlay.setEnabled(context, !cur, pattern)
                notifyUpdate(context)
            }
            ACTION_TOGGLE_EXTRA_DIM -> {
                val cur = prefs.getBoolean(LightspeedPreferences.KEY_EXTRA_DIM_ENABLED, false)
                val level = prefs.getInt(LightspeedPreferences.KEY_EXTRA_DIM_LEVEL, 40)
                LightspeedEyeCareEngine.applyExtraDim(context, !cur, level)
                notifyUpdate(context)
            }
            ACTION_OPEN_VISTA_DECK -> {
                val svc = LightspeedAccessibilityService.instance
                if (svc != null) {
                    VistaDeckManager.show(svc)
                } else {
                    context.startActivity(Intent(context, com.sbf.lightspeed.MainActivity::class.java).apply {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                    })
                }
            }
            ACTION_CYCLE_GEAR -> {
                val setsList = CockpitGearRepository.getGearSetsOrder(context, false)
                val count = setsList.size.coerceAtLeast(1)
                val cur = prefs.getInt(LightspeedPreferences.KEY_LAST_ACTIVE_SET_INDEX, 0)
                val next = (cur + 1).mod(count)
                prefs.edit().putInt(LightspeedPreferences.KEY_LAST_ACTIVE_SET_INDEX, next).apply()
                CockpitGearRepository.clearCache()
                notifyUpdate(context)
            }
        }
    }
}
