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
import android.graphics.Typeface
import android.os.Bundle
import android.view.View
import android.widget.RemoteViews
import com.sbf.lightspeed.R
import com.sbf.lightspeed.settings.DeckThemeManager
import com.sbf.lightspeed.settings.DeckThemeSnapshot
import com.sbf.lightspeed.settings.resolveCanvasPalette
import com.sbf.lightspeed.system.ActionDispatcher
import com.sbf.lightspeed.system.CockpitGearRepository
import com.sbf.lightspeed.system.LightspeedIconManager
import com.sbf.lightspeed.system.LightspeedPreferences
import com.sbf.lightspeed.system.LightspeedShortcutManager
import com.sbf.lightspeed.system.defaultPrefs

/**
 * 12-Theme Resizable Folder Widget (1x1 up to any dimension).
 *
 * Fully decoupled from Cockpit Gear Sets: each folder instance preserves its own apps.
 * Remembers confirmed dimensions per widget ID to prevent reverting to 1x1 on reboot.
 */
class LightspeedFolderWidgetProvider : AppWidgetProvider() {

    private enum class FolderLayout(val layoutRes: Int, val slots: Int, val iconsLaunchDirectly: Boolean) {
        COMPACT(R.layout.widget_folder_1x1, 4, false),
        COLUMN(R.layout.widget_folder_col, 3, true),
        ROW(R.layout.widget_folder_row, 3, true),
        GRID(R.layout.widget_folder_grid, 9, true)
    }

    companion object {
        const val ACTION_LAUNCH_APP = "com.sbf.lightspeed.widget.FOLDER_LAUNCH_APP"
        const val EXTRA_PKG = "extra_pkg"

        private val ICON_IDS = intArrayOf(
            R.id.folder_icon_1, R.id.folder_icon_2, R.id.folder_icon_3,
            R.id.folder_icon_4, R.id.folder_icon_5, R.id.folder_icon_6,
            R.id.folder_icon_7, R.id.folder_icon_8, R.id.folder_icon_9
        )

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

        fun getFolderApps(context: Context, appWidgetId: Int): List<String> {
            val prefs = context.defaultPrefs()
            val saved = prefs.getString("folder_apps_$appWidgetId", null)
                ?: prefs.getString("folder_apps_default", null)
            if (!saved.isNullOrBlank()) {
                val list = saved.split(",").map { it.trim() }.filter { it.isNotEmpty() }
                if (list.isNotEmpty()) return list
            }
            // Initial seed if newly created: fallback to active gear apps or popular defaults
            val activeSet = prefs.getInt(LightspeedPreferences.KEY_LAST_ACTIVE_SET_INDEX, 0)
            val seed = CockpitGearRepository.getAppsForActiveGear(context, false, activeSet, 0)
            return seed.ifEmpty {
                listOf(
                    "package:com.android.chrome",
                    "package:com.google.android.dialer",
                    "package:com.google.android.apps.messaging",
                    "package:com.google.android.camera"
                )
            }
        }

        fun saveFolderApps(context: Context, appWidgetId: Int, apps: List<String>) {
            val prefs = context.defaultPrefs()
            prefs.edit().putString("folder_apps_$appWidgetId", apps.joinToString(",")).apply()
            notifyUpdate(context)
        }

        /** Optical-glass folder surface plate matching active 12-theme tokens, engine mode, and transparency. */
        fun createFolderSurfaceBitmap(context: Context, snapshot: DeckThemeSnapshot, widthPx: Int, heightPx: Int): Bitmap {
            val w = widthPx.coerceAtLeast(100)
            val h = heightPx.coerceAtLeast(100)
            val bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bitmap)
            val paint = Paint(Paint.ANTI_ALIAS_FLAG)

            val prefs = context.defaultPrefs()
            val opacity = prefs.getFloat(LightspeedPreferences.KEY_WIDGET_BACKGROUND_OPACITY, 0.85f).coerceIn(0f, 1f)
            val themeMode = prefs.getString(LightspeedPreferences.KEY_WIDGET_THEME_MODE, "theme_12") ?: "theme_12"
            val palette = snapshot.resolveCanvasPalette(context, themeMode)

            paint.style = Paint.Style.FILL
            paint.color = palette.panelInnerArgb
            paint.alpha = (snapshot.panelAlpha * 255 * opacity).toInt().coerceIn(0, 255)
            val rect = RectF(4f, 4f, w - 4f, h - 4f)
            val radius = (minOf(w, h) * 0.22f).coerceIn(14f, 56f)
            canvas.drawRoundRect(rect, radius, radius, paint)

            // 2. Outer Neon Glow Seam pass
            val glowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.STROKE
                strokeWidth = 6f
                color = palette.secondaryAccentArgb
                alpha = (90 * opacity.coerceAtLeast(0.35f)).toInt().coerceIn(0, 255)
            }
            canvas.drawRoundRect(rect, radius, radius, glowPaint)

            // 3. Crisp Optical Seam Outline pass
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = 2.4f
            paint.color = palette.borderArgb
            paint.alpha = (255 * opacity.coerceAtLeast(0.35f)).toInt().coerceIn(20, 255)
            canvas.drawRoundRect(rect, radius, radius, paint)

            return bitmap
        }

        /** Themed "+N" badge tile used when folder contains more apps than visible slots. */
        fun createMoreBadgeBitmap(snapshot: DeckThemeSnapshot, remaining: Int, sizePx: Int = 96): Bitmap {
            val bitmap = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bitmap)
            val paint = Paint(Paint.ANTI_ALIAS_FLAG)
            val rect = RectF(6f, 6f, sizePx - 6f, sizePx - 6f)
            val radius = sizePx * 0.26f

            paint.style = Paint.Style.FILL
            paint.color = snapshot.borderColorArgb.toInt()
            paint.alpha = 56
            canvas.drawRoundRect(rect, radius, radius, paint)

            paint.style = Paint.Style.STROKE
            paint.strokeWidth = 3f
            paint.alpha = 255
            paint.color = snapshot.borderColorArgb.toInt()
            canvas.drawRoundRect(rect, radius, radius, paint)

            paint.style = Paint.Style.FILL
            paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            paint.textAlign = Paint.Align.CENTER
            paint.textSize = sizePx * 0.34f
            val baseline = sizePx / 2f - (paint.descent() + paint.ascent()) / 2f
            canvas.drawText("+$remaining", sizePx / 2f, baseline, paint)
            return bitmap
        }
    }

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        if (!com.sbf.lightspeed.system.NightlyFeatures.isFolderWidgetEnabled) return
        for (appWidgetId in appWidgetIds) {
            updateWidgetView(context, appWidgetManager, appWidgetId, appWidgetManager.getAppWidgetOptions(appWidgetId))
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

    private fun cellsFor(sizeDp: Int): Int = ((sizeDp + 30) / 70).coerceIn(1, 2)

    private fun layoutFor(widthDp: Int, heightDp: Int): FolderLayout {
        val cols = cellsFor(widthDp)
        val rows = cellsFor(heightDp)
        return when {
            cols == 1 && rows == 1 -> FolderLayout.COMPACT
            cols == 1 -> FolderLayout.COLUMN
            rows == 1 -> FolderLayout.ROW
            else -> FolderLayout.GRID
        }
    }

    private fun updateWidgetView(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetId: Int,
        options: Bundle
    ) {
        val prefs = context.defaultPrefs()
        var widthDp = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, 0)
        var heightDp = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, 0)

        // Persistent size lock: prevents reverting to 1x1 on reboot / update when options are uninitialized
        if (widthDp <= 40 || heightDp <= 40) {
            widthDp = prefs.getInt("pref_folder_w_$appWidgetId", widthDp.coerceAtLeast(40))
            heightDp = prefs.getInt("pref_folder_h_$appWidgetId", heightDp.coerceAtLeast(40))
        } else {
            prefs.edit()
                .putInt("pref_folder_w_$appWidgetId", widthDp)
                .putInt("pref_folder_h_$appWidgetId", heightDp)
                .apply()
        }

        val density = context.resources.displayMetrics.density
        val folderLayout = layoutFor(widthDp, heightDp)

        val snapshot = DeckThemeManager.getSnapshot(context)
        val bgBitmap = createFolderSurfaceBitmap(context, snapshot, (widthDp * density).toInt(), (heightDp * density).toInt())

        // Load folder apps independently from Cockpit Gear Sets
        val apps = getFolderApps(context, appWidgetId)

        val openFolder = PendingIntent.getActivity(
            context,
            appWidgetId + 999,
            Intent(context, LightspeedFolderModalActivity::class.java).apply {
                putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val views = RemoteViews(context.packageName, folderLayout.layoutRes).apply {
            setImageViewBitmap(R.id.folder_widget_bg, bgBitmap)
            setOnClickPendingIntent(R.id.folder_root, openFolder)

            val slots = folderLayout.slots
            val showMoreBadge = folderLayout.iconsLaunchDirectly && apps.size > slots
            for (i in 0 until slots) {
                val iconId = ICON_IDS[i]
                when {
                    showMoreBadge && i == slots - 1 -> {
                        setViewVisibility(iconId, View.VISIBLE)
                        setImageViewBitmap(iconId, createMoreBadgeBitmap(snapshot, apps.size - (slots - 1)))
                        setOnClickPendingIntent(iconId, openFolder)
                    }
                    i < apps.size -> {
                        val token = apps[i]
                        setViewVisibility(iconId, View.VISIBLE)
                        val bmp = LightspeedIconManager.getIconBitmap(context, token, useCache = true)
                            ?: LightspeedShortcutManager.resolveIconBitmap(context, token, useCache = true)
                        if (bmp != null) setImageViewBitmap(iconId, bmp)
                        if (folderLayout.iconsLaunchDirectly) {
                            val launchIntent = Intent(context, LightspeedFolderWidgetProvider::class.java).apply {
                                action = ACTION_LAUNCH_APP
                                putExtra(EXTRA_PKG, token)
                            }
                            setOnClickPendingIntent(
                                iconId,
                                PendingIntent.getBroadcast(
                                    context,
                                    appWidgetId * 16 + i,
                                    launchIntent,
                                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                                )
                            )
                        }
                    }
                    else -> setViewVisibility(iconId, View.INVISIBLE)
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
