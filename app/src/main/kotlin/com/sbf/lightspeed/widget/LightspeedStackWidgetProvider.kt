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
 * 12-Theme Interactive Widget Stack Provider.
 * Powered by a robust ViewFlipper engine (adapted from Essentials, MIT License):
 * - Page 0: Tactical Apps & Shortcuts (1-tap launch)
 * - Page 1: System Deck (Vocal Boost, Smart Pixels, Extra Dim, Vista Deck)
 * - Page 2: Cockpit Telemetry (Gear Set, Battery, Immunity, Cycle Gear)
 * - Page 3+: Live embedded 3rd-party widgets (AnkiDroid, Clock, Spotify, Weather)
 *
 * Supports fast 1-tap left/right edge transitions and bottom pagination,
 * and maintains 100% stability across OEM launchers (including Transsion XOS and One UI).
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
        const val ACTION_OPEN_DECK = "com.sbf.lightspeed.widget.ACTION_OPEN_DECK"
        const val ACTION_CYCLE_GEAR = "com.sbf.lightspeed.widget.ACTION_CYCLE_GEAR"
        const val EXTRA_TOKEN = "extra_token"

        fun notifyUpdate(context: Context) {
            LightspeedWidgetHostEngine.sync(context)
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

        fun notifyStackDataChanged(context: Context) = notifyUpdate(context)
    }

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        if (!com.sbf.lightspeed.system.NightlyFeatures.isWidgetStackEnabled) return
        LightspeedWidgetHostEngine.startIfNeeded(context)
        for (appWidgetId in appWidgetIds) {
            val options = appWidgetManager.getAppWidgetOptions(appWidgetId)
            renderStack(context, appWidgetManager, appWidgetId, options)
        }
        super.onUpdate(context, appWidgetManager, appWidgetIds)
    }

    override fun onAppWidgetOptionsChanged(context: Context, appWidgetManager: AppWidgetManager, appWidgetId: Int, newOptions: Bundle) {
        renderStack(context, appWidgetManager, appWidgetId, newOptions)
        super.onAppWidgetOptionsChanged(context, appWidgetManager, appWidgetId, newOptions)
    }

    override fun onDeleted(context: Context, appWidgetIds: IntArray) {
        LightspeedWidgetHostEngine.sync(context)
        super.onDeleted(context, appWidgetIds)
    }

    private fun renderStack(context: Context, appWidgetManager: AppWidgetManager, appWidgetId: Int, options: Bundle) {
        LightspeedLocaleEngine.init(context)
        val minWidthDp = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, 260)
        val minHeightDp = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, 180)
        val density = context.resources.displayMetrics.density
        val widthPx = (minWidthDp * density).toInt().coerceAtLeast(180)
        val heightPx = (minHeightDp * density).toInt().coerceAtLeast(130)

        val prefs = context.defaultPrefs()
        val snapshot = DeckThemeManager.getSnapshot(context)
        val bgBitmap = WallpaperBlurHelper.createBlurredWallpaperGlassCard(context, widthPx, heightPx, snapshot)

        val externalWidgetIds = LightspeedPreferences.getRefuelingStackWidgetIds(context)
        val totalPages = 3 + externalWidgetIds.size
        val currentPage = prefs.getInt("stack_widget_page_${appWidgetId}", 0).mod(totalPages)

        val views = RemoteViews(context.packageName, R.layout.widget_stack_layout).apply {
            setImageViewBitmap(R.id.stack_card_bg, bgBitmap)
            removeAllViews(R.id.stack_flipper)

            // 1. Internal Page 0: Tactical Apps
            addView(R.id.stack_flipper, createAppsPage(context, snapshot, appWidgetId))

            // 2. Internal Page 1: System Deck
            addView(R.id.stack_flipper, createSystemPage(context, snapshot, appWidgetId))

            // 3. Internal Page 2: Cockpit Telemetry
            addView(R.id.stack_flipper, createTelemetryPage(context, snapshot, appWidgetId))

            // 4. External Pages 3+: Hosted Widgets (Live RemoteViews or Fallback)
            for (hostedId in externalWidgetIds) {
                val liveRv = LightspeedWidgetHostEngine.contents[hostedId]
                val pageRv = RemoteViews(context.packageName, R.layout.widget_stack_page)
                if (liveRv != null) {
                    pageRv.addView(R.id.stack_page, liveRv)
                } else {
                    pageRv.addView(R.id.stack_page, createHostedPlaceholder(context, snapshot, appWidgetId, hostedId))
                }
                addView(R.id.stack_flipper, pageRv)
            }

            // Display current page
            setDisplayedChild(R.id.stack_flipper, currentPage)

            // Bottom Navigation Strip & Edge Touch Targets
            val borderColor = snapshot.borderColorArgb.toInt()
            setTextColor(R.id.btn_stack_prev, borderColor)
            setTextColor(R.id.btn_stack_next, borderColor)
            setTextColor(R.id.stack_page_indicator, borderColor)
            val nightlySuffix = if (com.sbf.lightspeed.BuildConfig.DEBUG) " · [NIGHTLY]" else ""
            setTextViewText(R.id.stack_page_indicator, "${currentPage + 1} / $totalPages$nightlySuffix")

            val prevIntent = Intent(context, LightspeedStackWidgetProvider::class.java).apply {
                action = ACTION_PREV_PAGE
                putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
            }
            val prevPending = PendingIntent.getBroadcast(
                context,
                appWidgetId * 10 + 1,
                prevIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            setOnClickPendingIntent(R.id.btn_stack_prev, prevPending)
            setOnClickPendingIntent(R.id.stack_edge_prev, prevPending)

            val nextIntent = Intent(context, LightspeedStackWidgetProvider::class.java).apply {
                action = ACTION_NEXT_PAGE
                putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
            }
            val nextPending = PendingIntent.getBroadcast(
                context,
                appWidgetId * 10 + 2,
                nextIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            setOnClickPendingIntent(R.id.btn_stack_next, nextPending)
            setOnClickPendingIntent(R.id.stack_edge_next, nextPending)
        }

        appWidgetManager.updateAppWidget(appWidgetId, views)
    }

    private fun createAppsPage(context: Context, snapshot: DeckThemeSnapshot, appWidgetId: Int): RemoteViews {
        val views = RemoteViews(context.packageName, R.layout.stack_item_tiles)
        val badge = if (com.sbf.lightspeed.BuildConfig.DEBUG) " [NIGHTLY]" else ""
        views.setTextViewText(R.id.stack_item_title, "${lsString(R.string.stack_wid_apps)}$badge")
        views.setTextColor(R.id.stack_item_title, snapshot.borderColorArgb.toInt())

        val activeSet = context.defaultPrefs().getInt(LightspeedPreferences.KEY_LAST_ACTIVE_SET_INDEX, 0)
        val apps = CockpitGearRepository.getAppsForActiveGear(context, false, activeSet, 0)
        val slotIds = intArrayOf(R.id.slot_1, R.id.slot_2, R.id.slot_3, R.id.slot_4)
        val iconIds = intArrayOf(R.id.slot_1_icon, R.id.slot_2_icon, R.id.slot_3_icon, R.id.slot_4_icon)
        val textIds = intArrayOf(R.id.slot_1_text, R.id.slot_2_text, R.id.slot_3_text, R.id.slot_4_text)

        for (i in 0 until 4) {
            if (i < apps.size) {
                val token = apps[i]
                val bmp = LightspeedShortcutManager.resolveIconBitmap(context, token, useCache = true)
                    ?: LightspeedIconManager.getIconBitmap(context, token, useCache = true)
                views.setViewVisibility(slotIds[i], View.VISIBLE)
                views.setTextViewText(textIds[i], LightspeedShortcutManager.resolveLabel(context, token).take(12))
                if (bmp != null) views.setImageViewBitmap(iconIds[i], bmp)

                val launchIntent = Intent(context, LightspeedStackWidgetProvider::class.java).apply {
                    action = ACTION_LAUNCH_TOKEN
                    putExtra(EXTRA_TOKEN, token)
                }
                views.setOnClickPendingIntent(
                    slotIds[i],
                    PendingIntent.getBroadcast(
                        context,
                        appWidgetId * 100 + i,
                        launchIntent,
                        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                    )
                )
            } else {
                views.setViewVisibility(slotIds[i], View.INVISIBLE)
            }
        }
        return views
    }

    private fun createSystemPage(context: Context, snapshot: DeckThemeSnapshot, appWidgetId: Int): RemoteViews {
        val views = RemoteViews(context.packageName, R.layout.stack_item_tiles)
        val badge = if (com.sbf.lightspeed.BuildConfig.DEBUG) " [NIGHTLY]" else ""
        views.setTextViewText(R.id.stack_item_title, "${lsString(R.string.stack_wid_sys_controls)}$badge")
        views.setTextColor(R.id.stack_item_title, snapshot.borderColorArgb.toInt())

        val prefs = context.defaultPrefs()
        val tint = snapshot.tintArgb.toInt()
        val activeTxt = lsString(R.string.stack_status_active)
        val offTxt = lsString(R.string.stack_status_off)

        fun tile(slot: Int, icon: Int, text: Int, status: Int, label: String, statusText: String, active: Boolean, drawable: Int, actionStr: String, reqCode: Int) {
            views.setViewVisibility(slot, View.VISIBLE)
            views.setTextViewText(text, label)
            views.setTextViewText(status, statusText)
            views.setTextColor(status, if (active) tint else Color.GRAY)
            views.setViewVisibility(status, View.VISIBLE)
            views.setImageViewBitmap(icon, LightspeedStackTileHelper.createThemedTileBitmap(context, drawable, active, tint))
            val intent = Intent(context, LightspeedStackWidgetProvider::class.java).apply { action = actionStr }
            views.setOnClickPendingIntent(slot, PendingIntent.getBroadcast(context, appWidgetId * 100 + reqCode, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE))
        }

        val vocal = SonicVoiceIsolationEngine.isEnabled
        tile(R.id.slot_1, R.id.slot_1_icon, R.id.slot_1_text, R.id.slot_1_status, lsString(R.string.stack_tile_vocal), if (vocal) activeTxt else offTxt, vocal, R.drawable.ic_music_note, ACTION_TOGGLE_VOCAL_BOOST, 11)

        val pixels = prefs.getBoolean(LightspeedPreferences.KEY_SMART_PIXELS_ENABLED, false)
        tile(R.id.slot_2, R.id.slot_2_icon, R.id.slot_2_text, R.id.slot_2_status, lsString(R.string.stack_tile_pixels), if (pixels) activeTxt else offTxt, pixels, R.drawable.ic_widgets, ACTION_TOGGLE_SMART_PIXELS, 12)

        val dim = prefs.getBoolean(LightspeedPreferences.KEY_EXTRA_DIM_ENABLED, false)
        tile(R.id.slot_3, R.id.slot_3_icon, R.id.slot_3_text, R.id.slot_3_status, lsString(R.string.stack_tile_dim), if (dim) activeTxt else offTxt, dim, R.drawable.ic_flashlight, ACTION_TOGGLE_EXTRA_DIM, 13)

        tile(R.id.slot_4, R.id.slot_4_icon, R.id.slot_4_text, R.id.slot_4_status, lsString(R.string.stack_tile_vista), lsString(R.string.stack_status_open), true, R.drawable.ic_qs_flight_armed, ACTION_OPEN_VISTA_DECK, 14)

        return views
    }

    private fun createTelemetryPage(context: Context, snapshot: DeckThemeSnapshot, appWidgetId: Int): RemoteViews {
        val views = RemoteViews(context.packageName, R.layout.stack_item_telemetry)
        val badge = if (com.sbf.lightspeed.BuildConfig.DEBUG) " [NIGHTLY]" else ""
        views.setTextViewText(R.id.stack_item_title, "${lsString(R.string.stack_wid_telemetry)}$badge")
        views.setTextColor(R.id.stack_item_title, snapshot.borderColorArgb.toInt())

        val activeSet = context.defaultPrefs().getInt(LightspeedPreferences.KEY_LAST_ACTIVE_SET_INDEX, 0)
        val gearName = CockpitGearRepository.getGearSetNameByIndex(context, false, activeSet)
        val totalSets = CockpitGearRepository.getGearSetsOrder(context, false).size.coerceAtLeast(1)

        views.setTextViewText(R.id.telemetry_gear_title, lsString(R.string.stack_gear_fmt, gearName).uppercase())
        views.setTextViewText(R.id.telemetry_gear_sub, lsString(R.string.stack_set_of_fmt, activeSet + 1, totalSets))
        views.setTextViewText(
            R.id.telemetry_battery_text,
            lsString(
                R.string.stack_bat_imm_fmt,
                LightspeedStackTileHelper.getBatteryTelemetry(context),
                LightspeedStackTileHelper.getImmunityTelemetry(context)
            )
        )
        views.setTextColor(R.id.telemetry_battery_text, snapshot.tintArgb.toInt())
        views.setTextViewText(R.id.btn_telemetry_cycle_gear, lsString(R.string.stack_btn_cycle_gear))
        views.setTextViewText(R.id.btn_telemetry_open_deck, lsString(R.string.stack_btn_manage_stack))
        views.setTextColor(R.id.btn_telemetry_cycle_gear, snapshot.borderColorArgb.toInt())
        views.setTextColor(R.id.btn_telemetry_open_deck, snapshot.borderColorArgb.toInt())

        val cycleIntent = Intent(context, LightspeedStackWidgetProvider::class.java).apply { action = ACTION_CYCLE_GEAR }
        views.setOnClickPendingIntent(R.id.btn_telemetry_cycle_gear, PendingIntent.getBroadcast(context, appWidgetId * 100 + 21, cycleIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE))

        val deckIntent = Intent(context, LightspeedHomeStackOverlayActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        views.setOnClickPendingIntent(R.id.btn_telemetry_open_deck, PendingIntent.getActivity(context, appWidgetId * 100 + 22, deckIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE))

        return views
    }

    private fun createHostedPlaceholder(context: Context, snapshot: DeckThemeSnapshot, appWidgetId: Int, hostedWidgetId: Int): RemoteViews {
        val views = RemoteViews(context.packageName, R.layout.stack_item_hosted)
        val info = AppWidgetManager.getInstance(context).getAppWidgetInfo(hostedWidgetId)
        views.setTextViewText(R.id.hosted_widget_title, info?.loadLabel(context.packageManager) ?: lsString(R.string.stack_hosted_fallback))
        val isUnsupported = hostedWidgetId in LightspeedWidgetHostEngine.unsupported
        views.setTextViewText(R.id.hosted_widget_sub, if (isUnsupported) lsString(R.string.stack_hosted_tap) else lsString(R.string.stack_hosted_sync))
        views.setTextColor(R.id.hosted_widget_title, Color.WHITE)

        val icon = try { info?.loadIcon(context, context.resources.displayMetrics.densityDpi) } catch (_: Exception) { null }
        if (icon != null) LightspeedIconManager.convertDrawableToBitmap(icon, 96)?.let { views.setImageViewBitmap(R.id.hosted_widget_icon, it) }

        val deckIntent = Intent(context, LightspeedHomeStackOverlayActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        views.setOnClickPendingIntent(R.id.stack_item_root, PendingIntent.getActivity(context, appWidgetId * 100 + 31, deckIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE))
        return views
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        val prefs = context.defaultPrefs()
        val appWidgetManager = AppWidgetManager.getInstance(context)
        val appWidgetId = intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID)
        val externalWidgetIds = LightspeedPreferences.getRefuelingStackWidgetIds(context)
        val totalPages = 3 + externalWidgetIds.size

        when (intent.action) {
            ACTION_REFRESH_THEME -> notifyUpdate(context)
            ACTION_PREV_PAGE -> {
                if (appWidgetId != AppWidgetManager.INVALID_APPWIDGET_ID) {
                    val cur = prefs.getInt("stack_widget_page_${appWidgetId}", 0)
                    val prev = (cur - 1 + totalPages).mod(totalPages)
                    prefs.edit().putInt("stack_widget_page_${appWidgetId}", prev).apply()
                    val options = appWidgetManager.getAppWidgetOptions(appWidgetId)
                    renderStack(context, appWidgetManager, appWidgetId, options)
                }
            }
            ACTION_NEXT_PAGE -> {
                if (appWidgetId != AppWidgetManager.INVALID_APPWIDGET_ID) {
                    val cur = prefs.getInt("stack_widget_page_${appWidgetId}", 0)
                    val next = (cur + 1).mod(totalPages)
                    prefs.edit().putInt("stack_widget_page_${appWidgetId}", next).apply()
                    val options = appWidgetManager.getAppWidgetOptions(appWidgetId)
                    renderStack(context, appWidgetManager, appWidgetId, options)
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
                val level = prefs.getInt(LightspeedPreferences.KEY_EXTRA_DIM_LEVEL, 40).let { if (it in 10..90) it else 40 }
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
