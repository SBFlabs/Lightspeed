package com.sbf.lightspeed

import android.app.Activity
import android.appwidget.AppWidgetHost
import android.appwidget.AppWidgetManager
import android.content.Context
import android.view.MotionEvent
import android.view.VelocityTracker
import android.view.View
import android.view.ViewConfiguration
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import kotlin.math.abs

/**
 * Custom FrameLayout host container for AppWidgetHostView.
 * Intercepts touch direction with smart X/Y axis discrimination:
 * - In Stack mode (HorizontalPager): allows horizontal swipes to pass through to the stack
 *   pager if the widget does not support X-axis scrolling, while reserving Y-axis gestures
 *   for vertical widget collection scrolling (e.g., Calendar events, email lists, tasks).
 * - In Grid mode: allows vertical scrolls to pass through to the dashboard column if the widget
 *   does not support Y-axis scrolling.
 */
class ScrollableAppWidgetContainer(
    context: Context,
    var isStackMode: Boolean = true,
    var onSwipeDelta: ((Float) -> Unit)? = null,
    var onSwipeEnd: ((Float, Float) -> Unit)? = null
) : FrameLayout(context) {
    private var startX = 0f
    private var startY = 0f
    private var lastX = 0f
    private var isDraggingStack = false
    private var isVerticalScroll = false
    private var velocityTracker: VelocityTracker? = null
    private val touchSlop = ViewConfiguration.get(context).scaledTouchSlop

    private fun hasHorizontalScrollableChild(v: View): Boolean {
        // Only consider scrollable if the view actually has horizontal overflow
        if (v.canScrollHorizontally(1) || v.canScrollHorizontally(-1)) return true
        if (v is ViewGroup) {
            for (i in 0 until v.childCount) {
                if (hasHorizontalScrollableChild(v.getChildAt(i))) return true
            }
        }
        return false
    }

    private fun hasVerticalScrollableChild(v: View): Boolean {
        // Dynamically checks if view actually has scrollable content.
        // If Anki or Calendar has only 1-2 items that all fit on screen, this returns false!
        if (v.canScrollVertically(1) || v.canScrollVertically(-1)) return true
        if (v is ViewGroup) {
            for (i in 0 until v.childCount) {
                if (hasVerticalScrollableChild(v.getChildAt(i))) return true
            }
        }
        return false
    }

    override fun dispatchTouchEvent(ev: MotionEvent): Boolean {
        if (!isStackMode || onSwipeDelta == null) {
            return handleGridModeDispatch(ev)
        }

        if (velocityTracker == null) {
            velocityTracker = VelocityTracker.obtain()
        }
        velocityTracker?.addMovement(ev)

        when (ev.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                startX = ev.x
                startY = ev.y
                lastX = ev.x
                isDraggingStack = false
                isVerticalScroll = false
            }
            MotionEvent.ACTION_MOVE -> {
                val dx = abs(ev.x - startX)
                val dy = abs(ev.y - startY)

                if (!isDraggingStack && !isVerticalScroll) {
                    if (dx > dy && dx > touchSlop) {
                        // Horizontal Drag Detected across widget
                        if (!hasHorizontalScrollableChild(this)) {
                            isDraggingStack = true
                            parent?.requestDisallowInterceptTouchEvent(true)

                            // Cancel touch in child views (like ListView) to release pressed states
                            val cancelEvent = MotionEvent.obtain(ev).apply { action = MotionEvent.ACTION_CANCEL }
                            super.dispatchTouchEvent(cancelEvent)
                            cancelEvent.recycle()
                        }
                    } else if (dy > dx && dy > touchSlop) {
                        isVerticalScroll = true
                        if (hasVerticalScrollableChild(this)) {
                            parent?.requestDisallowInterceptTouchEvent(true)
                        }
                    }
                }

                if (isDraggingStack) {
                    val deltaX = ev.x - lastX
                    lastX = ev.x
                    onSwipeDelta?.invoke(deltaX)
                    return true
                }
            }
            MotionEvent.ACTION_UP -> {
                if (isDraggingStack) {
                    velocityTracker?.computeCurrentVelocity(1000)
                    val xVel = velocityTracker?.xVelocity ?: 0f
                    val totalDx = ev.x - startX
                    onSwipeEnd?.invoke(totalDx, xVel)
                    isDraggingStack = false
                    velocityTracker?.recycle()
                    velocityTracker = null
                    return true
                }
                velocityTracker?.recycle()
                velocityTracker = null
            }
            MotionEvent.ACTION_CANCEL -> {
                if (isDraggingStack) {
                    onSwipeEnd?.invoke(0f, 0f)
                    isDraggingStack = false
                }
                velocityTracker?.recycle()
                velocityTracker = null
            }
        }

        return super.dispatchTouchEvent(ev)
    }

    private fun handleGridModeDispatch(ev: MotionEvent): Boolean {
        when (ev.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                startX = ev.x
                startY = ev.y
                isVerticalScroll = false
                parent?.requestDisallowInterceptTouchEvent(true)
            }
            MotionEvent.ACTION_MOVE -> {
                val dx = abs(ev.x - startX)
                val dy = abs(ev.y - startY)
                if (!isVerticalScroll) {
                    if (dy > dx && dy > touchSlop) {
                        isVerticalScroll = true
                        val canScrollY = hasVerticalScrollableChild(this)
                        parent?.requestDisallowInterceptTouchEvent(canScrollY)
                    } else if (dx > dy && dx > touchSlop) {
                        val canScrollX = hasHorizontalScrollableChild(this)
                        parent?.requestDisallowInterceptTouchEvent(canScrollX)
                    }
                }
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                isVerticalScroll = false
                parent?.requestDisallowInterceptTouchEvent(false)
            }
        }
        return super.dispatchTouchEvent(ev)
    }
}

@Composable
fun AppWidgetContainerView(
    activity: Activity,
    widgetId: Int,
    appWidgetHost: AppWidgetHost,
    appWidgetManager: AppWidgetManager,
    modifier: Modifier = Modifier,
    isEditMode: Boolean = false,
    isStackMode: Boolean = true,
    onSwipeDelta: ((Float) -> Unit)? = null,
    onSwipeEnd: ((Float, Float) -> Unit)? = null
) {
    key(widgetId) {
        val appWidgetInfo = remember(widgetId) {
            try {
                appWidgetManager.getAppWidgetInfo(widgetId)
            } catch (_: Exception) {
                null
            }
        }

        if (appWidgetInfo != null) {
            Box(modifier = modifier) {
                AndroidView(
                    modifier = Modifier.fillMaxSize(),
                    factory = { ctx ->
                        try {
                            val container = ScrollableAppWidgetContainer(
                                ctx,
                                isStackMode = isStackMode,
                                onSwipeDelta = onSwipeDelta,
                                onSwipeEnd = onSwipeEnd
                            )
                            val hostView = appWidgetHost.createView(ctx, widgetId, appWidgetInfo)
                            hostView.setAppWidget(widgetId, appWidgetInfo)
                            container.addView(
                                hostView,
                                FrameLayout.LayoutParams(
                                    FrameLayout.LayoutParams.MATCH_PARENT,
                                    FrameLayout.LayoutParams.MATCH_PARENT
                                )
                            )
                            container
                        } catch (e: Exception) {
                            android.widget.TextView(ctx).apply {
                                text = "Widget Error"
                                setTextColor(android.graphics.Color.WHITE)
                            }
                        }
                    },
                    update = { view ->
                        if (view is ScrollableAppWidgetContainer) {
                            view.isStackMode = isStackMode
                            view.onSwipeDelta = onSwipeDelta
                            view.onSwipeEnd = onSwipeEnd
                        }
                    }
                )

                if (isEditMode) {
                    // Transparent shield in edit mode so tapping near edit buttons doesn't trigger widget actions
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .clickable(
                                interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                                indication = null
                            ) { /* Consume widget touch in edit mode */ }
                    )
                }
            }
        } else {
            Box(modifier = modifier, contentAlignment = Alignment.Center) {
                Text(
                    text = "Widget Unavailable (ID: $widgetId)",
                    color = Color.LightGray.copy(alpha = 0.6f),
                    fontSize = 11.5.sp
                )
            }
        }
    }
}
