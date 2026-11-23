package com.sbf.lightspeed

import android.app.ActivityOptions
import android.appwidget.AppWidgetHost
import android.appwidget.AppWidgetHostView
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProviderInfo
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.IntentSender
import android.os.Build
import android.os.Bundle
import android.view.View
import android.widget.FrameLayout
import com.sbf.lightspeed.system.LightspeedPreferences
import com.sbf.lightspeed.system.NightlyFeatures
import com.sbf.lightspeed.system.defaultPrefs
import com.sbf.lightspeed.system.logSwallowed

/** Must stay 2050: this ID owns every already-bound Cruise HUD appWidgetId in AppWidgetService. Never change. */
const val CRUISE_HUD_HOST_ID = 2050

private val widgetReportedSizes = mutableMapOf<Int, Pair<Int, Int>>()

internal class CruiseWidgetContext(
    base: Context,
    private val onPendingActivityLaunch: () -> Unit,
    private val onDismissOverlay: () -> Unit
) : ContextWrapper(base) {

    override fun startIntentSender(
        intent: IntentSender,
        fillInIntent: Intent?,
        flagsMask: Int,
        flagsValues: Int,
        extraFlags: Int
    ) {
        startIntentSender(intent, fillInIntent, flagsMask, flagsValues, extraFlags, null)
    }

    override fun startIntentSender(
        intent: IntentSender,
        fillInIntent: Intent?,
        flagsMask: Int,
        flagsValues: Int,
        extraFlags: Int,
        options: Bundle?
    ) {
        val fill = (fillInIntent ?: Intent()).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)
        }
        val opts = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            val basic = ActivityOptions.makeBasic()
            basic.setPendingIntentBackgroundActivityStartMode(
                ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_ALLOWED
            )
            val bundle = basic.toBundle()
            if (options != null) {
                bundle.putAll(options)
            }
            bundle
        } else {
            options
        }

        try {
            onPendingActivityLaunch()
            super.startIntentSender(intent, fill, flagsMask, flagsValues, extraFlags, opts)
        } catch (e: Exception) {
            try {
                baseContext.startIntentSender(intent, fillInIntent, flagsMask, flagsValues, extraFlags, options)
            } catch (e2: Exception) {
                logSwallowed("CruiseWidgetContext", "startIntentSender", e2)
            }
        }
    }

    override fun startActivity(intent: Intent) {
        startActivity(intent, null)
    }

    override fun startActivity(intent: Intent, options: Bundle?) {
        val launchIntent = Intent(intent).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)
        }
        val opts = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            val basic = ActivityOptions.makeBasic()
            basic.setPendingIntentBackgroundActivityStartMode(
                ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_ALLOWED
            )
            val bundle = basic.toBundle()
            if (options != null) {
                bundle.putAll(options)
            }
            bundle
        } else {
            options
        }
        try {
            super.startActivity(launchIntent, opts)
            onDismissOverlay()
        } catch (e: Exception) {
            logSwallowed("CruiseWidgetContext", "startActivity", e)
        }
    }
}

internal fun LightspeedCruiseOverlay.initAppWidgetHost() {
    try {
        if (cruiseAppWidgetHost == null) {
            cruiseAppWidgetHost = AppWidgetHost(context.applicationContext, CRUISE_HUD_HOST_ID)
            cruiseAppWidgetHost?.startListening()
        }
    } catch (_: Exception) {}
}

/** Re-registers the host with AppWidgetService; call whenever the overlay becomes visible. */
internal fun LightspeedCruiseOverlay.ensureAppWidgetHostListening() {
    initAppWidgetHost()
    try { cruiseAppWidgetHost?.startListening() } catch (_: Exception) {}
}

/**
 * Returns the visible widget container under (x, y) in overlay coordinates, honoring
 * translationY (HUD scroll) and clipBounds, or null.
 */
internal fun LightspeedCruiseOverlay.findWidgetContainerAt(x: Float, y: Float): ScrollableAppWidgetContainer? {
    for ((_, c) in cruiseAppWidgetHostContainers) {
        if (c.visibility != View.VISIBLE || c.parent !== this) continue
        val top = c.top + c.translationY
        val clip = c.clipBounds
        val visTop = if (clip != null) top + clip.top else top
        val visBottom = if (clip != null) top + clip.bottom else top + c.height
        if (x >= c.left && x <= c.left + c.width && y >= visTop && y <= visBottom) return c
    }
    return null
}

internal fun LightspeedCruiseOverlay.destroyAppWidgetHost() {
    try {
        for ((_, container) in cruiseAppWidgetHostContainers) {
            removeView(container)
        }
        cruiseAppWidgetHostContainers.clear()

        cruiseAppWidgetHostView?.let { removeView(it) }
        cruiseAppWidgetHostView = null
        currentBoundWidgetId = -1

        cruiseAppWidgetHostView2?.let { removeView(it) }
        cruiseAppWidgetHostView2 = null
        currentBoundWidgetId2 = -1

        for ((_, view) in cruiseAppWidgetHostViews) {
            removeView(view)
        }
        cruiseAppWidgetHostViews.clear()
        widgetReportedSizes.clear()

        cruiseAppWidgetHost?.stopListening()
        cruiseAppWidgetHost = null
    } catch (_: Exception) {}
}

internal fun LightspeedCruiseOverlay.isAppWidgetReadyAndVisible(widgetId: Int): Boolean {
    if (widgetSettlePending || widgetId <= 0) return false
    val container = cruiseAppWidgetHostContainers[widgetId] ?: return false
    val hostView = cruiseAppWidgetHostViews[widgetId] ?: return false
    return container.visibility == View.VISIBLE && hostView.visibility == View.VISIBLE
}

internal fun LightspeedCruiseOverlay.scheduleDebouncedWidgetAttach(delayMs: Long = 160L) {
    widgetSettlePending = true
    lastWidgetCategorySwitchMs = android.os.SystemClock.uptimeMillis()
    cruiseAppWidgetHostView?.visibility = View.GONE
    cruiseAppWidgetHostView2?.visibility = View.GONE
    for ((_, view) in cruiseAppWidgetHostViews) {
        view.visibility = View.GONE
    }
    for ((_, container) in cruiseAppWidgetHostContainers) {
        container.visibility = View.GONE
    }
    uiHandler.removeCallbacks(widgetSettleRunnable)
    uiHandler.postDelayed(widgetSettleRunnable, delayMs)
}

internal fun LightspeedCruiseOverlay.updateCategoryAppWidgetView() {
    val cat = cachedCategories.getOrNull(activeCatIndex)
    val catId = cat?.id ?: return

    val isVisibleLayer = (isHudEditMode || isStickyPinned || currentLayer == CruiseLayer.CATEGORY || currentLayer == CruiseLayer.GRID || currentLayer == CruiseLayer.STICKY_PIN)

    if (!isVisibleLayer || !NightlyFeatures.isCategoryCruiseHudWidgetsEnabled || (widgetSettlePending && !isHudEditMode && !isStickyPinned)) {
        cruiseAppWidgetHostView?.visibility = View.GONE
        cruiseAppWidgetHostView2?.visibility = View.GONE
        for ((_, view) in cruiseAppWidgetHostViews) {
            view.visibility = View.GONE
        }
        for ((_, container) in cruiseAppWidgetHostContainers) {
            container.visibility = View.GONE
        }
        return
    }

    computeHudSlotBounds()
    val activeWidgetItems = activeCategoryHudItems.filter { it.type == LightspeedCruiseHudPods.MODE_ANDROID_WIDGET && it.widgetId > 0 }
    val activeWidgetIds = activeWidgetItems.map { it.widgetId }.toSet()

    // Hide off-category containers while keeping up to 2 most recent widgets warm; evict excess cleanly
    val inactiveIds = cruiseAppWidgetHostContainers.keys.filter { it !in activeWidgetIds }
    for (id in inactiveIds) {
        cruiseAppWidgetHostContainers[id]?.visibility = View.GONE
        cruiseAppWidgetHostViews[id]?.visibility = View.GONE
    }
    val excessCount = cruiseAppWidgetHostContainers.size - 2
    if (excessCount > 0) {
        for (id in inactiveIds.take(excessCount)) {
            cruiseAppWidgetHostContainers.remove(id)?.let {
                it.visibility = View.GONE
                removeView(it)
            }
            cruiseAppWidgetHostViews.remove(id)
        }
    }

    if (activeWidgetItems.isEmpty()) {
        cruiseAppWidgetHostView?.visibility = View.GONE
        cruiseAppWidgetHostView2?.visibility = View.GONE
        for ((_, view) in cruiseAppWidgetHostViews) {
            view.visibility = View.GONE
        }
        for ((_, container) in cruiseAppWidgetHostContainers) {
            container.visibility = View.GONE
        }
        return
    }

    ensureAppWidgetHostListening()
    val host = cruiseAppWidgetHost ?: return
    val awm = AppWidgetManager.getInstance(context)

    val seenWidgetIds = mutableSetOf<Int>()
    for ((index, item) in activeWidgetItems.withIndex()) {
        val widgetId = item.widgetId
        if (widgetId in seenWidgetIds) continue
        seenWidgetIds.add(widgetId)

        val bounds = item.bounds
        if (bounds.width() <= 10f || bounds.height() <= 10f) {
            cruiseAppWidgetHostContainers[widgetId]?.visibility = View.GONE
            cruiseAppWidgetHostViews[widgetId]?.visibility = View.GONE
            continue
        }

        val providerInfo: AppWidgetProviderInfo? = try {
            awm.getAppWidgetInfo(widgetId)
        } catch (_: Exception) {
            null
        }

        if (providerInfo == null) {
            cruiseAppWidgetHostContainers[widgetId]?.visibility = View.GONE
            cruiseAppWidgetHostViews[widgetId]?.visibility = View.GONE
            continue
        }

        var container = cruiseAppWidgetHostContainers[widgetId]
        var hostView = cruiseAppWidgetHostViews[widgetId]
        val dp = resources.displayMetrics.density
        val padL = item.padLeftDp * dp
        val padT = item.padTopDp * dp
        val padR = item.padRightDp * dp
        val padB = item.padBottomDp * dp

        val baseL = (bounds.left + padL).toInt()
        val baseT = (bounds.top + padT).toInt()
        val targetW = (bounds.width() - padL - padR).toInt().coerceAtLeast(10)
        val targetH = (bounds.height() - padT - padB).toInt().coerceAtLeast(10)

        val isFarSplit = isFarHalfSplitMode()
        val hudTopLimit = getSafeHudTopOffset() + if (isHudEditMode) 36f * dp else 0f
        val rawHudBottomLimit = if (isFarSplit || isHudEditMode) {
            height.toFloat() * 0.94f
        } else if (hudMaxScroll > 0f) {
            getTopHudStripBounds().bottom + 2f * dp
        } else {
            getTopHudStripBounds().bottom + 10f * dp
        }
        val hudBottomLimit = if (shouldShowCategoryCruiseRotatingTip()) {
            minOf(rawHudBottomLimit, getCategoryCruiseTipReserveTopY() - 4f * dp)
        } else {
            rawHudBottomLimit
        }
        val scrolledT = baseT - hudScrollOffset

        if ((scrolledT + targetH) <= hudTopLimit || scrolledT >= hudBottomLimit) {
            container?.visibility = View.GONE
            hostView?.visibility = View.GONE
            continue
        }

        val cornerRadiusPx = if (item.cornerRadiusDp >= 0) item.cornerRadiusDp * dp else categoryHudCornerRadiusPx()
        val itemAlpha = (item.opacity.coerceIn(10, 100) / 100f).coerceIn(0.05f, 1.0f)
        val wDp = (targetW / dp).toInt()
        val hDp = (targetH / dp).toInt()

        if (container == null || hostView == null) {
            try {
                val widgetContext = CruiseWidgetContext(
                    base = context,
                    onPendingActivityLaunch = {
                        service?.pendingWidgetLaunchDismissUntilMs = android.os.SystemClock.uptimeMillis() + 1800L
                    },
                    onDismissOverlay = { dismissOverlay() }
                )
                hostView = host.createView(widgetContext, widgetId, providerInfo)
                if (hostView != null) {
                    hostView.setAppWidget(widgetId, providerInfo)
                    hostView.minimumWidth = 0
                    hostView.minimumHeight = 0
                    hostView.setPadding(0, 0, 0, 0)

                    val newContainer = ScrollableAppWidgetContainer(widgetContext, isStackMode = false)
                    newContainer.alpha = itemAlpha
                    if (cornerRadiusPx > 0f) {
                        newContainer.outlineProvider = object : android.view.ViewOutlineProvider() {
                            override fun getOutline(view: View, outline: android.graphics.Outline) {
                                outline.setRoundRect(0, 0, view.width, view.height, cornerRadiusPx)
                            }
                        }
                        newContainer.clipToOutline = true
                    } else {
                        newContainer.outlineProvider = null
                        newContainer.clipToOutline = false
                    }

                    val hostLp = FrameLayout.LayoutParams(
                        FrameLayout.LayoutParams.MATCH_PARENT,
                        FrameLayout.LayoutParams.MATCH_PARENT
                    )
                    newContainer.addView(hostView, hostLp)

                    val lp = FrameLayout.LayoutParams(targetW, targetH).apply {
                        leftMargin = baseL
                        topMargin = baseT
                    }
                    addView(newContainer, lp)

                    container = newContainer
                    cruiseAppWidgetHostContainers[widgetId] = newContainer
                    cruiseAppWidgetHostViews[widgetId] = hostView
                    widgetReportedSizes[widgetId] = Pair(wDp, hDp)
                    try {
                        hostView.updateAppWidgetSize(null, wDp, hDp, wDp, hDp)
                    } catch (_: Exception) {}
                    if (index == 0) {
                        cruiseAppWidgetHostView = hostView
                        currentBoundWidgetId = widgetId
                    } else if (index == 1) {
                        cruiseAppWidgetHostView2 = hostView
                        currentBoundWidgetId2 = widgetId
                    }
                }
            } catch (_: Exception) {}
        } else {
            container.alpha = itemAlpha
            if (cornerRadiusPx > 0f) {
                container.outlineProvider = object : android.view.ViewOutlineProvider() {
                    override fun getOutline(view: View, outline: android.graphics.Outline) {
                        outline.setRoundRect(0, 0, view.width, view.height, cornerRadiusPx)
                    }
                }
                container.clipToOutline = true
            } else {
                container.outlineProvider = null
                container.clipToOutline = false
            }

            val lp = container.layoutParams as? FrameLayout.LayoutParams
            if (lp != null && (lp.width != targetW || lp.height != targetH ||
                lp.leftMargin != baseL || lp.topMargin != baseT)) {
                lp.width = targetW
                lp.height = targetH
                lp.leftMargin = baseL
                lp.topMargin = baseT
                container.layoutParams = lp
                container.requestLayout()

                val lastReported = widgetReportedSizes[widgetId]
                if (lastReported == null || lastReported.first != wDp || lastReported.second != hDp) {
                    widgetReportedSizes[widgetId] = Pair(wDp, hDp)
                    try {
                        hostView?.updateAppWidgetSize(null, wDp, hDp, wDp, hDp)
                    } catch (_: Exception) {}
                }
            }
        }

        container?.translationY = -hudScrollOffset

        val clipTop = maxOf(0, (hudTopLimit - scrolledT).toInt())
        val clipBottom = minOf(targetH, (hudBottomLimit - scrolledT).toInt())
        if (clipTop > 0 || clipBottom < targetH) {
            container?.clipBounds = android.graphics.Rect(0, clipTop, targetW, clipBottom)
        } else {
            container?.clipBounds = null
        }

        container?.visibility = View.VISIBLE
        hostView?.visibility = View.VISIBLE
    }
}
