package com.sbf.lightspeed.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.os.Bundle
import android.view.View
import android.widget.RemoteViews
import com.sbf.lightspeed.R
import com.sbf.lightspeed.settings.DeckThemeManager
import com.sbf.lightspeed.settings.DeckThemeSnapshot
import com.sbf.lightspeed.system.ActionDispatcher
import com.sbf.lightspeed.system.CockpitGearRepository
import com.sbf.lightspeed.system.LightspeedIconManager
import com.sbf.lightspeed.system.LightspeedPreferences
import com.sbf.lightspeed.system.LightspeedShortcutManager
import com.sbf.lightspeed.system.defaultPrefs

/**
 * 12-Theme Resizable Home Screen Folder Widget Provider.
 * Adapts layout between compact 1x1 pod, 2x2 grid, and expanded views based on widget dimensions.
 */
class LightspeedFolderWidgetProvider : AppWidgetProvider() {

    companion object {
        const val ACTION_LAUNCH_APP = "com.sbf.lightspeed.widget.FOLDER_LAUNCH_APP"
        const val EXTRA_PKG = "extra_pkg"

        fun notifyUpdate(context: Context) {
            val appWidgetManager = AppWidgetManager.getInstance(context)
            val component = ComponentName(context, LightspeedFolderWidgetProvider::class.java)
            val ids = appWidgetManager.getAppWidgetIds(component)
            if (ids.isNotEmpty()) {
                val intent = Intent(context, LightspeedFolderWidgetProvider::class.java).apply {
                    action = AppWidgetManager.ACTION_APPWIDGET_UPDATE
                    putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, ids)
                }
                context.sendBroadcast(intent)
            }
        }

        fun createFolderSurfaceBitmap(snapshot: DeckThemeSnapshot, widthPx: Int, heightPx: Int): Bitmap {
            val w = widthPx.coerceAtLeast(100)
            val h = heightPx.coerceAtLeast(100)
            val bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bitmap)
            val paint = Paint(Paint.ANTI_ALIAS_FLAG)

            // Panel glass background
            paint.style = Paint.Style.FILL
            paint.color = snapshot.panelColorArgb.toInt()
            paint.alpha = (snapshot.panelAlpha * 255).toInt().coerceIn(30, 255)
            val rect = RectF(6f, 6f, w - 6f, h - 6f)
            val radius = (snapshot.cornerRadiusDp * 2.5f).coerceIn(12f, 48f)
            canvas.drawRoundRect(rect, radius, radius, paint)

            // 12-theme border
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = 3f
            paint.color = snapshot.borderColorArgb.toInt()
            canvas.drawRoundRect(rect, radius, radius, paint)

            return bitmap
        }
    }

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        for (appWidgetId in appWidgetIds) {
            val options = appWidgetManager.getAppWidgetOptions(appWidgetId)
            updateWidgetView(context, appWidgetManager, appWidgetId, options)
        }
        super.onUpdate(context, appWidgetManager, appWidgetIds)
    }

    override fun onAppWidgetOptionsChanged(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetId: Int,
        newOptions: Bundle
    ) {
        updateWidgetView(context, appWidgetManager, appWidgetId, newOptions)
        super.onAppWidgetOptionsChanged(context, appWidgetManager, appWidgetId, newOptions)
    }

    private fun updateWidgetView(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetId: Int,
        options: Bundle
    ) {
        val minWidthDp = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, 140)
        val minHeightDp = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, 140)
        val density = context.resources.displayMetrics.density
        val widthPx = (minWidthDp * density).toInt()
        val heightPx = (minHeightDp * density).toInt()

        val snapshot = DeckThemeManager.getSnapshot(context)
        val bgBitmap = createFolderSurfaceBitmap(snapshot, widthPx, heightPx)

        val views = RemoteViews(context.packageName, R.layout.widget_folder_layout).apply {
            setImageViewBitmap(R.id.folder_widget_bg, bgBitmap)
            setTextColor(R.id.folder_title, snapshot.borderColorArgb.toInt())

            // Folder Header tap -> opens Central Command or folder modal
            val headerIntent = Intent(context, LightspeedFolderModalActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
            val headerPending = PendingIntent.getActivity(
                context,
                appWidgetId + 999,
                headerIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
            )
            setOnClickPendingIntent(R.id.folder_header, headerPending)

            // Populate up to 4 apps from active gear or custom folder set
            val activeSet = context.defaultPrefs().getInt(LightspeedPreferences.KEY_LAST_ACTIVE_SET_INDEX, 0)
            val apps = CockpitGearRepository.getAppsForActiveGear(context, false, activeSet, 0)

            val slotIds = intArrayOf(R.id.slot_1, R.id.slot_2, R.id.slot_3, R.id.slot_4)
            val iconIds = intArrayOf(R.id.slot_1_icon, R.id.slot_2_icon, R.id.slot_3_icon, R.id.slot_4_icon)
            val textIds = intArrayOf(R.id.slot_1_text, R.id.slot_2_text, R.id.slot_3_text, R.id.slot_4_text)

            // Dynamic grid layout adjustments based on size
            val isCompact = minWidthDp < 120 || minHeightDp < 120
            val maxSlots = if (isCompact) 1 else 4

            for (i in 0 until 4) {
                val slot = slotIds[i]
                val icon = iconIds[i]
                val text = textIds[i]

                if (i < maxSlots && i < apps.size) {
                    val token = apps[i]
                    val label = LightspeedShortcutManager.resolveLabel(context, token)

                    setViewVisibility(slot, View.VISIBLE)
                    setTextViewText(text, label.take(10))
                    val bmp = LightspeedIconManager.getIconBitmap(context, token, useCache = true)
                        ?: LightspeedShortcutManager.resolveIconBitmap(context, token, useCache = true)
                    if (bmp != null) {
                        setImageViewBitmap(icon, bmp)
                    }

                    val launchIntent = Intent(context, LightspeedFolderWidgetProvider::class.java).apply {
                        action = ACTION_LAUNCH_APP
                        putExtra(EXTRA_PKG, token)
                    }
                    val pendingLaunch = PendingIntent.getBroadcast(
                        context,
                        appWidgetId * 10 + i,
                        launchIntent,
                        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
                    )
                    setOnClickPendingIntent(slot, pendingLaunch)
                } else {
                    setViewVisibility(slot, if (i < maxSlots) View.INVISIBLE else View.GONE)
                }
            }
        }

        appWidgetManager.updateAppWidget(appWidgetId, views)
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        if (intent.action == ACTION_LAUNCH_APP) {
            val token = intent.getStringExtra(EXTRA_PKG) ?: return
            ActionDispatcher.executeDirect(context, token)
        }
    }
}
