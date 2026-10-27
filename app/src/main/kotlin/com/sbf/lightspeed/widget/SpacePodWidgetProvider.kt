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
import android.widget.RemoteViews
import com.sbf.lightspeed.R
import com.sbf.lightspeed.settings.DeckThemeManager
import com.sbf.lightspeed.settings.DeckThemeSnapshot
import com.sbf.lightspeed.settings.SpacePodShortcutActivity
import com.sbf.lightspeed.system.LightspeedIconManager
import com.sbf.lightspeed.system.LightspeedShortcutManager
import com.sbf.lightspeed.system.SpacePodGestureDispatcher
import com.sbf.lightspeed.system.defaultPrefs

/**
 * 12-Theme 1x1 Space Pod Widget Provider.
 * Renders an edge-to-edge optical glass surface matching the active theme
 * and dispatches multi-gesture actions on tap.
 * Automatically redirects to configuration if unconfigured.
 */
class SpacePodWidgetProvider : AppWidgetProvider() {

    companion object {
        const val ACTION_POD_TAP = "com.sbf.lightspeed.widget.ACTION_POD_TAP"
        const val EXTRA_POD_INDEX = "extra_pod_index"

        fun notifyUpdate(context: Context) {
            val appWidgetManager = AppWidgetManager.getInstance(context)
            val component = ComponentName(context, SpacePodWidgetProvider::class.java)
            val ids = appWidgetManager.getAppWidgetIds(component)
            if (ids.isNotEmpty()) {
                val intent = Intent(context, SpacePodWidgetProvider::class.java).apply {
                    action = AppWidgetManager.ACTION_APPWIDGET_UPDATE
                    putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, ids)
                }
                context.sendBroadcast(intent)
            }
        }

        fun createPodSurfaceBitmap(snapshot: DeckThemeSnapshot, sizePx: Int = 180): Bitmap {
            val bitmap = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bitmap)
            val paint = Paint(Paint.ANTI_ALIAS_FLAG)

            // Panel glass base
            paint.style = Paint.Style.FILL
            paint.color = snapshot.panelColorArgb.toInt()
            paint.alpha = (snapshot.panelAlpha * 255).toInt().coerceIn(30, 255)
            val rect = RectF(6f, 6f, sizePx - 6f, sizePx - 6f)
            val radius = sizePx / 2f
            canvas.drawRoundRect(rect, radius, radius, paint)

            // Inner glass glow / border
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
        val snapshot = DeckThemeManager.getSnapshot(context)
        val bgBitmap = createPodSurfaceBitmap(snapshot)
        val prefs = context.defaultPrefs()

        for (appWidgetId in appWidgetIds) {
            val podIndex = prefs.getInt("widget_pod_index_$appWidgetId", 1).coerceIn(1, 4)
            val isConfigured = SpacePodGestureDispatcher.isPodConfigured(context, podIndex)
            val clickToken = SpacePodGestureDispatcher.getActionToken(context, podIndex, SpacePodGestureDispatcher.GESTURE_CLICK)
            val customLabel = prefs.getString("pref_space_pod_${podIndex}_custom_label", null)
            val label = if (!customLabel.isNullOrBlank()) {
                customLabel
            } else if (isConfigured && !clickToken.isNullOrBlank()) {
                LightspeedShortcutManager.resolveLabel(context, clickToken)
            } else {
                "POD $podIndex"
            }

            val customBmp = LightspeedIconManager.getCustomShortcutBitmap(context, "space_pod_$podIndex")
            val iconBmp = customBmp ?: if (!clickToken.isNullOrBlank()) {
                LightspeedIconManager.getIconBitmap(context, clickToken, useCache = true)
                    ?: LightspeedShortcutManager.resolveIconBitmap(context, clickToken, useCache = true)
            } else null

            val views = RemoteViews(context.packageName, R.layout.widget_space_pod_layout).apply {
                setImageViewBitmap(R.id.pod_widget_bg, bgBitmap)
                setTextColor(R.id.pod_widget_label, snapshot.borderColorArgb.toInt())
                setTextViewText(R.id.pod_widget_label, label.take(10).uppercase())

                if (iconBmp != null) {
                    setImageViewBitmap(R.id.pod_widget_icon, iconBmp)
                } else {
                    setImageViewResource(R.id.pod_widget_icon, R.drawable.ic_qs_flight_armed)
                }

                // Forward tap to SpacePodShortcutActivity trampoline
                val clickIntent = Intent(context, SpacePodShortcutActivity::class.java).apply {
                    putExtra(SpacePodShortcutActivity.EXTRA_POD_INDEX, podIndex)
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                }
                val pendingIntent = PendingIntent.getActivity(
                    context,
                    appWidgetId,
                    clickIntent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
                )
                setOnClickPendingIntent(R.id.pod_widget_root, pendingIntent)
            }

            appWidgetManager.updateAppWidget(appWidgetId, views)
        }
        super.onUpdate(context, appWidgetManager, appWidgetIds)
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        if (intent.action == ACTION_POD_TAP) {
            val podIndex = intent.getIntExtra(EXTRA_POD_INDEX, 1).coerceIn(1, 4)
            if (!SpacePodGestureDispatcher.isPodConfigured(context, podIndex)) {
                val configIntent = Intent(context, LightspeedWidgetConfigActivity::class.java).apply {
                    putExtra(LightspeedWidgetConfigActivity.EXTRA_POD_INDEX, podIndex)
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                }
                context.startActivity(configIntent)
            } else {
                SpacePodGestureDispatcher.onPodTapped(context, podIndex)
            }
        }
    }
}
