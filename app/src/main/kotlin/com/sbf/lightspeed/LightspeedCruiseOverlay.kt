package com.sbf.lightspeed

import android.accessibilityservice.AccessibilityService
import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.res.Configuration
import android.graphics.Camera
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.RenderEffect
import android.graphics.RuntimeShader
import android.graphics.drawable.Drawable
import android.media.AudioManager
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.util.AttributeSet
import android.util.Log
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.WindowManager
import android.view.animation.DecelerateInterpolator
import android.widget.Toast
import com.sbf.lightspeed.defaultPrefs
import com.sbf.lightspeed.system.ActionDispatcher
import com.sbf.lightspeed.system.ElevatedTaskCloser
import java.net.URISyntaxException
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.sin

class LightspeedCruiseOverlay @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {
    private val totalGearSetsCount: Int
        get() {
            val prefs = context.getSharedPreferences("default", Context.MODE_PRIVATE)
            val setsString = prefs.getString("gear_sets_order", "0,1,2,3") ?: "0,1,2,3"
            val count = setsString.split(",").filter { it.isNotEmpty() }.size
            return if (count > 0) count else 1
        }


    enum class CruiseLayer { HIDDEN, CATEGORY, GRID, STICKY_PIN, FAVORITES_GEARS, COCKPIT_HANGAR }
    enum class TouchZone { NONE, TOP_EDGE, CENTER_CRUISE, BOTTOM_EDGE }
    
    enum class MacroGesture {
        NONE,
        SWIPE_UP, SWIPE_UP_HOLD,
        SWIPE_DOWN, SWIPE_DOWN_HOLD,
        SWIPE_LEFT, SWIPE_LEFT_HOLD,
        SWIPE_UP_DOWN, SWIPE_UP_DOWN_HOLD,
        SWIPE_DOWN_UP, SWIPE_DOWN_UP_HOLD,
        SWIPE_UP_LEFT, SWIPE_UP_LEFT_HOLD,
        SWIPE_DOWN_LEFT, SWIPE_DOWN_LEFT_HOLD,
        SWIPE_LEFT_BACK, SWIPE_LEFT_BACK_HOLD,
        SWIPE_LEFT_UP, SWIPE_LEFT_UP_HOLD,
        SWIPE_LEFT_DOWN, SWIPE_LEFT_DOWN_HOLD,
        SCRUBBING
    }

    data class PlacedItem(val app: LightspeedDataBridge.LaunchTarget, val bounds: RectF)

    private val service = context as? LightspeedAccessibilityService
    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    
    private var isCruising = false
    private var isStickyPinned = false
    private var currentLayer = CruiseLayer.HIDDEN
    private var currentActiveZone = TouchZone.NONE

    private var centerHeightPx = 400f
    private var centerVisualWidthPx = 12f
    private var centerTouchWidthPx = 45f
    private var centerYOffsetPx = 0f

    private var topHeightPx = 300f
    private var topVisualWidthPx = 2f
    private var topTouchWidthPx = 32f

    private var bottomHeightPx = 300f
    private var bottomVisualWidthPx = 2f
    private var bottomTouchWidthPx = 32f

    private var linkEdges = false

    private val topTouchBounds = RectF()
    private val centerTouchBounds = RectF()
    private val bottomTouchBounds = RectF()

    private val topVisualBounds = RectF()
    private val centerVisualBounds = RectF()
    private val bottomVisualBounds = RectF()

    private val uiHandler = Handler(Looper.getMainLooper())
    private var touchDownTime = 0L
    private var gestureStartX = 0f
    private var gestureStartY = 0f
    private var macroTrackingActive = false
    private var currentDetectedGesture = MacroGesture.NONE

    private var trackingStateLocked = false
    private var initialLeftSweepDistance = 0f
    private var lowestXReached = 0f
    private var highestYReached = 0f
    private var lowestYReached = 0f
    
    private var aggregateScrubAccumulator = 0f
    private var isScrubEntranceHapticFired = false

    private fun triggerHardwareHaptic(durationMs: Long, amplitude: Int) {
        try {
            val vibrator = context.getSystemService(Context.VIBRATOR_SERVICE) as? android.os.Vibrator
            if (vibrator != null && vibrator.hasVibrator()) {
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                    vibrator.vibrate(android.os.VibrationEffect.createOneShot(durationMs, amplitude))
                } else {
                    @Suppress("DEPRECATION")
                    vibrator.vibrate(durationMs)
                }
            }
        } catch (e: Exception) {
            android.util.Log.e("GestureEngine", "Hardware vibration failed", e)
        }
    }
    private var lastPermissionToastTime = 0L
    private var overScrollBoundaryAccumulator = 0f
    private var settingsCategoryAppended = false

    private val holdTimerRunnable = Runnable {
        if (macroTrackingActive) {
            val hostGesture = currentDetectedGesture
            val holdEquivalent = when(hostGesture) {
                MacroGesture.SWIPE_UP -> MacroGesture.SWIPE_UP_HOLD
                MacroGesture.SWIPE_DOWN -> MacroGesture.SWIPE_DOWN_HOLD
                MacroGesture.SWIPE_LEFT -> MacroGesture.SWIPE_LEFT_HOLD
                MacroGesture.SWIPE_UP_DOWN -> MacroGesture.SWIPE_UP_DOWN_HOLD
                MacroGesture.SWIPE_DOWN_UP -> MacroGesture.SWIPE_DOWN_UP_HOLD
                MacroGesture.SWIPE_UP_LEFT -> MacroGesture.SWIPE_UP_LEFT_HOLD
                MacroGesture.SWIPE_DOWN_LEFT -> MacroGesture.SWIPE_DOWN_LEFT_HOLD
                MacroGesture.SWIPE_LEFT_BACK -> MacroGesture.SWIPE_LEFT_BACK_HOLD
                MacroGesture.SWIPE_LEFT_UP -> MacroGesture.SWIPE_LEFT_UP_HOLD
                MacroGesture.SWIPE_LEFT_DOWN -> MacroGesture.SWIPE_LEFT_DOWN_HOLD
                else -> hostGesture
            }
            if (holdEquivalent != hostGesture) {
                currentDetectedGesture = holdEquivalent
                executeMacroAction(currentActiveZone, holdEquivalent)
                triggerHardwareHaptic(35, 160)
                macroTrackingActive = false
            }
        }
    }

    private val prefChangeListener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
        if (key != null && key.startsWith("pref_sidebar_")) {
            post { updateMetricsDimensions() }
        }
    }

    private var cachedRenderEffect: RenderEffect? = null

    private var cachedCategories = listOf<LightspeedDataBridge.CategoryNode>()
    private var activeCatIndex = -1
    private var initialCatIndex = 0
    private var categoryVisualOffset = 0f
    private var entranceStartTime = 0L
    private var snapAnimator: ValueAnimator? = null

    private var cachedApps = listOf<LightspeedDataBridge.LaunchTarget>()
    private var placedAppsList = mutableListOf<PlacedItem>()
    private var totalGridContentHeight = 0f
    private var viewportScrollOffset = 0f
    private var activeItem: LightspeedDataBridge.LaunchTarget? = null

    private val categoryAppsCache = mutableMapOf<String, List<LightspeedDataBridge.LaunchTarget>>()
    private val categoryGridCache = mutableMapOf<String, List<PlacedItem>>()
    private val categoryHeightCache = mutableMapOf<String, Float>()
    private val applicationIconCache = mutableMapOf<String, Drawable>()
    private var lastLoadedCategoryId: String? = null

    // --- GYROSCOPE CORE WORKSPACE STATES ---
    private var activeGearRing = 0          // 0 = Outer Ring, 1 = Inner Ring, 2 = Center Control Hub
    private var activeGearSetIndex = 0      // Active profile face (Set A, B, C, D)
    private var gearRingRotations = FloatArray(3) { 0f }
    private var rawHorizontalXAccumulator = 0f
    private var isCubeRotationFired = false

    private var touchDownRawX = 0f
    private var touchDownRawY = 0f
    private var lastTouchRawX = 0f
    private var lastTouchRawY = 0f
    private var depthPercentage = 0f
    private var virtualCursorX = 0f
    private var virtualCursorY = 0f

    private var touchDownX = 0f
    private var touchDownY = 0f
    private var lastTouchY = 0f

    private val launchpadPillBounds = RectF() 
    private val gestureEngine = if (context is AccessibilityService) LightspeedGestureEngine(context) else null
    private val dataBridge = LightspeedDataBridge(context)

    private val projectionCamera3D = Camera()
    private val transformMatrixPipeline = Matrix()
    private val cylinderRadius = 500f

    private val textPaint = Paint().apply {
        style = Paint.Style.FILL; color = Color.WHITE; textSize = 24f
        isAntiAlias = true; textAlign = Paint.Align.CENTER; typeface = android.graphics.Typeface.DEFAULT_BOLD
    }
    private val catTextPaint = Paint().apply {
        style = Paint.Style.FILL; color = Color.WHITE; textSize = 52f
        isAntiAlias = true; textAlign = Paint.Align.RIGHT; typeface = android.graphics.Typeface.create("sans-serif-condensed", android.graphics.Typeface.BOLD)
    }
    private val elementPaint = Paint().apply { isAntiAlias = true; style = Paint.Style.FILL }
    private val highlightPaint = Paint().apply { isAntiAlias = true; style = Paint.Style.FILL }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        val prefs = context.getSharedPreferences("default", Context.MODE_PRIVATE)
        prefs.registerOnSharedPreferenceChangeListener(prefChangeListener)
        updateMetricsDimensions()
    }

    override fun onDetachedFromWindow() {
        val prefs = context.getSharedPreferences("default", Context.MODE_PRIVATE)
        prefs.unregisterOnSharedPreferenceChangeListener(prefChangeListener)
        super.onDetachedFromWindow()
    }

    private fun updateMetricsDimensions() {
        val wm = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
        val lp = layoutParams as? WindowManager.LayoutParams ?: return

        val displayMetrics = resources.displayMetrics
        val screenW = displayMetrics.widthPixels.toFloat()
        val screenH = displayMetrics.heightPixels.toFloat()
        val density = displayMetrics.density

        val prefs = context.getSharedPreferences("default", Context.MODE_PRIVATE)
        
        centerHeightPx = prefs.getInt("pref_sidebar_center_height", 280).toFloat() * density
        centerYOffsetPx = prefs.getInt("pref_sidebar_center_y_offset", 0).toFloat() * density
        centerVisualWidthPx = prefs.getInt("pref_sidebar_center_visual_width", 4).toFloat() * density
        centerTouchWidthPx = prefs.getInt("pref_sidebar_center_touch_width", 24).toFloat() * density

        linkEdges = prefs.getBoolean("pref_sidebar_link_edges", false)

        topHeightPx = prefs.getInt("pref_sidebar_top_height", 300).toFloat() * density
        topVisualWidthPx = prefs.getInt("pref_sidebar_top_visual_width", 2).toFloat() * density
        topTouchWidthPx = prefs.getInt("pref_sidebar_top_touch_width", 32).toFloat() * density

        bottomHeightPx = if (linkEdges) topHeightPx else prefs.getInt("pref_sidebar_bottom_height", 300).toFloat() * density
        bottomVisualWidthPx = if (linkEdges) topVisualWidthPx else prefs.getInt("pref_sidebar_bottom_visual_width", 2).toFloat() * density
        bottomTouchWidthPx = if (linkEdges) topTouchWidthPx else prefs.getInt("pref_sidebar_bottom_touch_width", 32).toFloat() * density

        if (currentLayer == CruiseLayer.HIDDEN) {
            val centerY = (screenH / 2f) + centerYOffsetPx
            val centerTop = centerY - (centerHeightPx / 2f)
            val centerBottom = centerY + (centerHeightPx / 2f)

            val topLimit = (centerTop - topHeightPx).coerceAtLeast(0f)
            val bottomLimit = (centerBottom + bottomHeightPx).coerceAtMost(screenH)

            val winHeight = (bottomLimit - topLimit).toInt()
            val winWidth = maxOf(centerTouchWidthPx, topTouchWidthPx, bottomTouchWidthPx).toInt()
            val w = winWidth.toFloat()
            
            topTouchBounds.set(w - topTouchWidthPx, 0f, w, topHeightPx)
            centerTouchBounds.set(w - centerTouchWidthPx, topHeightPx, w, topHeightPx + centerHeightPx)
            bottomTouchBounds.set(w - bottomTouchWidthPx, topHeightPx + centerHeightPx, w, topHeightPx + centerHeightPx + bottomHeightPx)

            topVisualBounds.set(w - topVisualWidthPx, 0f, w, topHeightPx)
            centerVisualBounds.set(w - centerVisualWidthPx, topHeightPx, w, topHeightPx + centerHeightPx)
            bottomVisualBounds.set(w - bottomVisualWidthPx, topHeightPx + centerHeightPx, w, topHeightPx + centerHeightPx + bottomHeightPx)

            launchpadPillBounds.set(centerVisualBounds)

            if (lp.height != winHeight || lp.width != winWidth || lp.y != topLimit.toInt() || lp.gravity != (Gravity.TOP or Gravity.END)) {
                lp.gravity = Gravity.TOP or Gravity.END
                lp.x = 0; lp.y = topLimit.toInt()
                lp.width = winWidth; lp.height = winHeight
                wm.updateViewLayout(this, lp)
            }
        } else {
            val winWidth = WindowManager.LayoutParams.MATCH_PARENT
            val winHeight = WindowManager.LayoutParams.MATCH_PARENT
            val w = screenW; val h = screenH

            val centerY = (h / 2f) + centerYOffsetPx
            val centerTop = centerY - (centerHeightPx / 2f)
            val centerBottom = centerY + (centerHeightPx / 2f)

            val topLimit = (centerTop - topHeightPx).coerceAtLeast(0f)
            val bottomLimit = (centerBottom + bottomHeightPx).coerceAtMost(h)

            topTouchBounds.set(w - topTouchWidthPx, topLimit, w, centerTop)
            centerTouchBounds.set(w - centerTouchWidthPx, centerTop, w, centerBottom)
            bottomTouchBounds.set(w - bottomTouchWidthPx, centerBottom, w, bottomLimit)

            topVisualBounds.set(w - topVisualWidthPx, topLimit, w, centerTop)
            centerVisualBounds.set(w - centerVisualWidthPx, centerTop, w, centerBottom)
            bottomVisualBounds.set(w - bottomVisualWidthPx, centerBottom, w, bottomLimit)

            launchpadPillBounds.set(centerVisualBounds)

            if (lp.height != winHeight || lp.width != winWidth || lp.y != 0) {
                lp.gravity = Gravity.FILL
                lp.x = 0; lp.y = 0
                lp.width = winWidth; lp.height = winHeight
                wm.updateViewLayout(this, lp)
            }
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            try {
                val agslSource = """
                    uniform shader inputTexture;
                    uniform float2 viewSize;
                    uniform float topBlurHeight;
                    uniform float bottomBlurHeight;
                    half4 main(float2 fragCoord) {
                        float blurAlpha = 0.0;
                        if (fragCoord.y < topBlurHeight) {
                            blurAlpha = 1.0 - (fragCoord.y / topBlurHeight);
                        } else if (fragCoord.y > viewSize.y - bottomBlurHeight) {
                            blurAlpha = (fragCoord.y - (viewSize.y - bottomBlurHeight)) / bottomBlurHeight;
                        }
                        blurAlpha = clamp(blurAlpha, 0.0, 1.0);
                        float fadeProgress = pow(blurAlpha, 1.4);
                        float step = blurAlpha * 24.0;
                        half4 color = inputTexture.eval(fragCoord);
                        if (step > 0.1) {
                            color += inputTexture.eval(fragCoord + float2(0.0, step * 0.4));
                            color += inputTexture.eval(fragCoord - float2(0.0, step * 0.4));
                            color += inputTexture.eval(fragCoord + float2(step * 0.4, 0.0));
                            color += inputTexture.eval(fragCoord - float2(step * 0.4, 0.0));
                            color /= 5.0;
                        }
                        half4 deepSpaceVoid = half4(0.015, 0.015, 0.023, 1.0);
                        return mix(color, deepSpaceVoid, fadeProgress);
                    }
                """.trimIndent()
                val progressiveShader = RuntimeShader(agslSource).apply {
                    setFloatUniform("viewSize", screenW, screenH)
                    setFloatUniform("topBlurHeight", 130.0f * density)
                    setFloatUniform("bottomBlurHeight", 160.0f * density)
                }
                cachedRenderEffect = RenderEffect.createRuntimeShaderEffect(progressiveShader, "inputTexture")
            } catch (e: Exception) { Log.e("LightspeedBlur", "AGSL fault", e) }
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val systemExclusions = mutableListOf<android.graphics.Rect>()
            systemExclusions.add(android.graphics.Rect(topTouchBounds.left.toInt(), topTouchBounds.top.toInt(), topTouchBounds.right.toInt(), topTouchBounds.bottom.toInt()))
            systemExclusions.add(android.graphics.Rect(centerTouchBounds.left.toInt(), centerTouchBounds.top.toInt(), centerTouchBounds.right.toInt(), centerTouchBounds.bottom.toInt()))
            systemExclusions.add(android.graphics.Rect(bottomTouchBounds.left.toInt(), bottomTouchBounds.top.toInt(), bottomTouchBounds.right.toInt(), bottomTouchBounds.bottom.toInt()))
            this.systemGestureExclusionRects = systemExclusions
        }

        refreshActiveRenderEffect()
        invalidate()
    }

    private fun refreshActiveRenderEffect() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            setRenderEffect(if (currentLayer != CruiseLayer.HIDDEN) cachedRenderEffect else null)
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        val x = event.x; val y = event.y
        val rawX = event.rawX; val rawY = event.rawY
        if (currentLayer == CruiseLayer.COCKPIT_HANGAR) {
            if (event.action == MotionEvent.ACTION_DOWN) {
                val d = resources.displayMetrics.density; val cx = width / 2f; val cy = height / 2f
                if (x >= cx + 115f * d && x <= cx + 175f * d && y >= cy - 373f * d && y <= cy - 351f * d) {
                    val intent = android.content.Intent(context, CockpitSettingsActivity::class.java).apply { addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK) }
                    context.startActivity(intent)
                    dismissOverlay()
                    return true
                }
                if (y < (80f * d) || y > (height - 80f * d)) { dismissOverlay(); return true }
                val prefs = context.getSharedPreferences("default", Context.MODE_PRIVATE)
                val setsString = prefs.getString("gear_sets_order", "0,1,2,3") ?: "0,1,2,3"
                val setsList = setsString.split(",").filter { it.isNotEmpty() }
                if (activeGearSetIndex >= setsList.size) { activeGearSetIndex = 0 }
                val currentSetId = if (activeGearSetIndex in setsList.indices) setsList[activeGearSetIndex] else "0"
                val itemW = 76f * d
                val startX = cx - ((setsList.size - 1) * itemW / 2f)
                if (y >= cy - 330f && y <= cy - 290f) {
                    for (gIndex in setsList.indices) {
                        val btnX = startX + gIndex * itemW
                        if (x >= btnX - (32f * d) && x <= btnX + (32f * d)) {
                            activeGearSetIndex = gIndex
                            if (prefs.getString("cockpit_launch_behavior", "default") == "last") {
                                prefs.edit().putInt("last_active_set_index", gIndex).apply()
                            }
                            invalidate(); return true
                        }
                    }
                }
                if (x >= cx - 40f * d && x <= cx + 40f * d) {
                    if (y >= cy - 95f * d && y <= cy - 75f * d) {
                        val intent = android.content.Intent(context, GearPickerActivity::class.java).apply {
                            putExtra("SET_ID", currentSetId)
                            putExtra("RING_INDEX", 0)
                            addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                        }
                        context.startActivity(intent)
                        dismissOverlay()
                        return true
                    }
                    if (y >= cy + 65f * d && y <= cy + 85f * d) {
                        val intent = android.content.Intent(context, GearPickerActivity::class.java).apply {
                            putExtra("SET_ID", currentSetId)
                            putExtra("RING_INDEX", 1)
                            addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                        }
                        context.startActivity(intent)
                        dismissOverlay()
                        return true
                    }
                }
                if (x >= cx - 20f * d && x <= cx + 20f * d && y >= cy - 35f * d && y <= cy + 5f * d) {
                    prefs.edit().putString("gear_set_${currentSetId}_ring_0_packages", "").putString("gear_set_${currentSetId}_ring_1_packages", "").apply()
                    invalidate(); return true
                }
            }
            return true
        }

        if (isStickyPinned) {
            val hF = height.toFloat()
            val gridTopLimit = hF * 0.15f
            val gridBottomLimit = hF * 0.94f
            val gridHeightScope = gridBottomLimit - gridTopLimit
            val maxScroll = max(0f, totalGridContentHeight - gridHeightScope)

            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    touchDownX = x; touchDownY = y; lastTouchY = y
                    activeItem = null
                    if (y in gridTopLimit..gridBottomLimit) {
                        val absoluteY = y + viewportScrollOffset
                        for (placedItem in placedAppsList) {
                            if (placedItem.bounds.contains(x, absoluteY)) { activeItem = placedItem.app; break }
                        }
                    }
                    invalidate(); return true
                }
                MotionEvent.ACTION_MOVE -> {
                    val deltaY = y - lastTouchY
                    lastTouchY = y
                    if (abs(y - touchDownY) > 16f || abs(x - touchDownX) > 16f) activeItem = null
                    if (maxScroll > 0f) {
                        viewportScrollOffset = (viewportScrollOffset - deltaY).coerceIn(0f, maxScroll)
                        invalidate()
                    }
                    return true
                }
                MotionEvent.ACTION_UP -> {
                    if (hypot((x - touchDownX).toDouble(), (y - touchDownY).toDouble()) < 16f) {
                        if (y in gridTopLimit..gridBottomLimit) activeItem?.let { executeLaunch(it); dismissOverlay() } ?: dismissOverlay()
                        else dismissOverlay()
                    }
                    activeItem = null; invalidate(); return true
                }
                MotionEvent.ACTION_CANCEL -> { activeItem = null; invalidate(); return true }
            }
            return true
        }

        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                touchDownRawX = rawX; touchDownRawY = rawY
                lastTouchRawX = rawX; lastTouchRawY = rawY
                touchDownTime = System.currentTimeMillis()
                gestureStartX = rawX; gestureStartY = rawY

                trackingStateLocked = false
                initialLeftSweepDistance = 0f
                lowestXReached = rawX
                highestYReached = rawY
                lowestYReached = rawY
                aggregateScrubAccumulator = 0f
                isScrubEntranceHapticFired = false
                currentDetectedGesture = MacroGesture.NONE
                overScrollBoundaryAccumulator = 0f
                settingsCategoryAppended = false

                currentActiveZone = when {
                    centerTouchBounds.contains(x, y) -> TouchZone.CENTER_CRUISE
                    topTouchBounds.contains(x, y) -> TouchZone.TOP_EDGE
                    bottomTouchBounds.contains(x, y) -> TouchZone.BOTTOM_EDGE
                    else -> TouchZone.NONE
                }

                if (currentActiveZone == TouchZone.CENTER_CRUISE) {
                    isCruising = true
                    categoryVisualOffset = 0f
                    entranceStartTime = System.currentTimeMillis()
                    lastLoadedCategoryId = null
                    snapAnimator?.cancel()
                    categoryAppsCache.clear(); categoryGridCache.clear()
                    categoryHeightCache.clear(); applicationIconCache.clear()
                    currentLayer = CruiseLayer.CATEGORY
                    updateMetricsDimensions()
                    placedAppsList.clear(); cachedApps = emptyList(); cachedCategories = emptyList()
                    service?.updateWindowLayout(true)
                    invalidate()

                    Thread {
                        val categoriesList = dataBridge.getLiveCategories()
                        post {
                            if (isCruising) {
                                cachedCategories = categoriesList
                                if (cachedCategories.isNotEmpty()) {
                                    val hF = if (height > 0) height.toFloat() else 2400f
                                    val startYArea = hF * 0.25f
                                    val endYArea = hF * 0.75f
                                    val usableHeight = endYArea - startYArea
                                    val normY = ((y - startYArea) / usableHeight).coerceIn(0f, 1f)
                                    initialCatIndex = floor(normY * cachedCategories.size).toInt().coerceIn(0, cachedCategories.size - 1)
                                    activeCatIndex = initialCatIndex
                                    val catLineH = usableHeight / cachedCategories.size
                                    categoryVisualOffset = (hF / 2f) - (startYArea + (activeCatIndex * catLineH) + (catLineH / 2f))
                                }
                                invalidate()
                            }
                        }
                    }.start()
                } else if (currentActiveZone != TouchZone.NONE) {
                    macroTrackingActive = true
                    uiHandler.postDelayed(holdTimerRunnable, ViewConfiguration.getLongPressTimeout().toLong())
                }
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                val density = resources.displayMetrics.density // Convert hardcoded pixels to device-agnostic DP
                if (isCruising) {
                    val density = resources.displayMetrics.density
                    val deltaX = touchDownRawX - rawX
                    val deltaY = abs(rawY - touchDownRawY)

                    // Immediate Horizontal Breakthrough: Trigger Gyroscope before category loop engages
                    if (currentLayer == CruiseLayer.CATEGORY && deltaX > (24f * density) && deltaY < (14f * density)) {
                        currentLayer = CruiseLayer.FAVORITES_GEARS
                        triggerHardwareHaptic(30, 180)
                    }

                    if (currentLayer == CruiseLayer.FAVORITES_GEARS) {
                        processGyroscopeTouchPhysics(rawX, rawY)
                    } else {
                        evaluateSpatialMetrics(rawX, rawY, x, y)
                    }
                } else if (macroTrackingActive) {
                    val previousGesture = currentDetectedGesture
                    val deltaX = rawX - gestureStartX
                    val deltaY = rawY - gestureStartY

                    if (rawX < lowestXReached) lowestXReached = rawX
                    if (rawY > highestYReached) highestYReached = rawY
                    if (rawY < lowestYReached) lowestYReached = rawY

                    val screenW = resources.displayMetrics.widthPixels.toFloat()
                    val thresholdX_Scrub = screenW * 0.333f 
                    val thresholdY_Compound = 60f

                    if (currentDetectedGesture == MacroGesture.NONE) {
                        if (abs(deltaX) > (22f * density) && abs(deltaX) > abs(deltaY)) {
                            currentDetectedGesture = MacroGesture.SWIPE_LEFT
                            initialLeftSweepDistance = abs(deltaX)
                        } else if (deltaY < (-25f * density) && abs(deltaY) > abs(deltaX)) {
                            currentDetectedGesture = MacroGesture.SWIPE_UP
                        } else if (deltaY > (25f * density) && abs(deltaY) > abs(deltaX)) {
                            currentDetectedGesture = MacroGesture.SWIPE_DOWN
                        }
                    }

                    when (currentDetectedGesture) {
                        MacroGesture.SWIPE_LEFT -> {
                            val zoneName = if (currentActiveZone == TouchZone.TOP_EDGE) "TOP" else "BOTTOM"
                            val dynamicZone = if (context.getSharedPreferences("default", Context.MODE_PRIVATE).getBoolean("pref_sidebar_link_gestures", false)) "TOP" else zoneName
                            val assignedScrub = context.getSharedPreferences("default", Context.MODE_PRIVATE).getString("pref_macro_action_${dynamicZone}_SCRUBBING", "none")

                            if (assignedScrub != "none" && assignedScrub != null && abs(deltaX) > thresholdX_Scrub) {
                                currentDetectedGesture = MacroGesture.SCRUBBING
                                uiHandler.removeCallbacks(holdTimerRunnable)
                            } else if (deltaY < -thresholdY_Compound) {
                                currentDetectedGesture = MacroGesture.SWIPE_LEFT_UP
                            } else if (deltaY > thresholdY_Compound) {
                                currentDetectedGesture = MacroGesture.SWIPE_LEFT_DOWN
                            } else {
                                val currentReturnRightDistance = rawX - lowestXReached
                                if (currentReturnRightDistance > (30f * density)) {
                                    currentDetectedGesture = MacroGesture.SWIPE_LEFT_BACK
                                }
                            }
                        }
                        MacroGesture.SWIPE_UP -> {
                            if ((rawY - lowestYReached) > 60f) {
                                currentDetectedGesture = MacroGesture.SWIPE_UP_DOWN
                            } else if (deltaX < -50f) {
                                currentDetectedGesture = MacroGesture.SWIPE_UP_LEFT
                            }
                        }
                        MacroGesture.SWIPE_DOWN -> {
                            if ((highestYReached - rawY) > 60f) {
                                currentDetectedGesture = MacroGesture.SWIPE_DOWN_UP
                            } else if (deltaX < -50f) {
                                currentDetectedGesture = MacroGesture.SWIPE_DOWN_LEFT
                            }
                        }
                        MacroGesture.SCRUBBING -> {
                            uiHandler.removeCallbacks(holdTimerRunnable) // PORTAL LINE LOCK: Suppress any hold actions immediately
                            if (!isScrubEntranceHapticFired) {
                                triggerHardwareHaptic(65, 255) // Chunky high-inertia hardware pop (50ms)
                                isScrubEntranceHapticFired = true
                            }
                            val pixelYDelta = rawY - lastTouchRawY
                            executeLinearScrubTrack(currentActiveZone, pixelYDelta)
                        }
                        else -> {}
                    }

                    if (currentDetectedGesture != MacroGesture.SCRUBBING && !currentDetectedGesture.name.endsWith("_HOLD")) {
                        val moveDelta = hypot(rawX - lastTouchRawX, rawY - lastTouchRawY)
                        if (currentDetectedGesture != previousGesture || moveDelta > (3f * density)) {
                            resetHoldTimer()
                        }
                    }

                    lastTouchRawX = rawX
                    lastTouchRawY = rawY
                }
                return true
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                isScrubEntranceHapticFired = false
                uiHandler.removeCallbacks(holdTimerRunnable)
                if (isCruising) {
                    if (currentLayer == CruiseLayer.FAVORITES_GEARS) {
                        val packages = getAppsForActiveGear(activeGearSetIndex, activeGearRing)
                        if (packages.isNotEmpty() && activeGearRing in 0..1) {
                            val itemCount = packages.size
                            val currentRotation = gearRingRotations[activeGearRing]
                            
                            // Find which app icon rotated closest to the selection zone (180 degrees / straight left)
                            var targetedPackage: String? = null
                            var minAngleDiff = Float.MAX_VALUE
                            
                            for (i in packages.indices) {
                                val itemAngle = (currentRotation + i * (360f / itemCount)) % 360f
                                val normalizedAngle = if (itemAngle < 0) itemAngle + 360f else itemAngle
                                val diff = abs(normalizedAngle - 180f)
                                if (diff < minAngleDiff) {
                                    minAngleDiff = diff
                                    targetedPackage = packages[i]
                                }
                            }
                            
                            if (targetedPackage != null) {
                                triggerHardwareHaptic(40, 200)
                                try {
                                    val launchIntent = context.packageManager.getLaunchIntentForPackage(targetedPackage)
                                    if (launchIntent != null) context.startActivity(launchIntent)
                                } catch (e: Exception) { e.printStackTrace() }
                            }
   } else if (activeGearRing == 2) {
                            triggerHardwareHaptic(50, 220)
                            val cPrefs = context.getSharedPreferences("default", Context.MODE_PRIVATE)
                            if (cPrefs.getString("cockpit_launch_behavior", "default") == "last") {
                                activeGearSetIndex = cPrefs.getInt("last_active_set_index", 0)
                            } else {
                                activeGearSetIndex = 0
                            }
                            currentLayer = CruiseLayer.COCKPIT_HANGAR
                            invalidate()
                            return true
                        }
                        currentLayer = CruiseLayer.HIDDEN
                        isCruising = false
                        service?.updateWindowLayout(false) // CRITICAL DIRECTIVE: Disengage overlay window touch-trap immediately
                        updateMetricsDimensions()
                        invalidate()
                        return true
                    }

                    if (currentLayer == CruiseLayer.STICKY_PIN) {
                        isStickyPinned = true; currentLayer = CruiseLayer.GRID
                        updateMetricsDimensions(); invalidate()
                    } else if (currentLayer == CruiseLayer.CATEGORY && activeCatIndex in cachedCategories.indices && cachedCategories[activeCatIndex].id == "launcher_settings_virtual_id") {
                        launchLauncherSettings()
                    } else {
                        activeItem?.let { executeLaunch(it); dismissOverlay() } ?: dismissOverlay()
                    }
                    isCruising = false
                } else if (macroTrackingActive) {
                    macroTrackingActive = false
                    if (currentDetectedGesture != MacroGesture.NONE && currentDetectedGesture != MacroGesture.SCRUBBING) {
                        executeMacroAction(currentActiveZone, currentDetectedGesture)
                    }
                }
                currentActiveZone = TouchZone.NONE
                return true
            }
        }
        return super.onTouchEvent(event)
    }

    private fun resetHoldTimer() {
        uiHandler.removeCallbacks(holdTimerRunnable)
        uiHandler.postDelayed(holdTimerRunnable, ViewConfiguration.getLongPressTimeout().toLong())
    }

    private fun executeLinearScrubTrack(zone: TouchZone, pixelDelta: Float) {
        val zoneName = if (zone == TouchZone.TOP_EDGE) "TOP" else "BOTTOM"
        val prefs = context.getSharedPreferences("default", Context.MODE_PRIVATE)
        val dynamicZone = if (prefs.getBoolean("pref_sidebar_link_gestures", false)) "TOP" else zoneName
        val assignedScrub = prefs.getString("pref_macro_action_${dynamicZone}_SCRUBBING", "none") ?: "none"

        if (assignedScrub == "none") return

        aggregateScrubAccumulator += pixelDelta
        val sensitivityThreshold = 35f

        if (abs(aggregateScrubAccumulator) >= sensitivityThreshold) {
            val steps = (aggregateScrubAccumulator / sensitivityThreshold).toInt()
            aggregateScrubAccumulator %= sensitivityThreshold
            if (steps != 0) {
                triggerHardwareHaptic(20, 120) // Noticeable micro-tick (25ms) that vibrates even during slow pulls
            }

            if (assignedScrub == "scrub:volume" || assignedScrub == "system:volume") {
                val currentVol = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
                val maxVol = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
                val targetVol = (currentVol - steps).coerceIn(0, maxVol)
                audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, targetVol, AudioManager.FLAG_SHOW_UI)
            } else if (assignedScrub == "scrub:brightness" || assignedScrub == "system:brightness") {
                if (!Settings.System.canWrite(context)) {
                    val currentTimestamp = System.currentTimeMillis()
                    if (currentTimestamp - lastPermissionToastTime > 5000) {
                        lastPermissionToastTime = currentTimestamp
                        Handler(Looper.getMainLooper()).post {
                            Toast.makeText(context, "LaunchTime requires Write Settings permission for brightness control", Toast.LENGTH_LONG).show()
                            val grantIntent = Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS).apply {
                                data = Uri.parse("package:" + context.packageName)
                                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            }
                            context.startActivity(grantIntent)
                        }
                    }
                    return
                }
                try {
                    val currentBrightness = Settings.System.getInt(context.contentResolver, Settings.System.SCREEN_BRIGHTNESS)
                    val targetBrightness = (currentBrightness - (steps * 4)).coerceIn(0, 255)
                    Settings.System.putInt(context.contentResolver, Settings.System.SCREEN_BRIGHTNESS, targetBrightness)
                } catch (e: Exception) {
                    Log.e("GestureEngine", "System write failure", e)
                }
            }
        }
    }

    private fun executeMacroAction(zone: TouchZone, gesture: MacroGesture) {
        val zoneName = if (zone == TouchZone.TOP_EDGE) "TOP" else "BOTTOM"
        val prefs = context.getSharedPreferences("default", Context.MODE_PRIVATE)
        val dynamicZone = if (prefs.getBoolean("pref_sidebar_link_gestures", false)) "TOP" else zoneName
        val actionKey = "pref_macro_action_${dynamicZone}_${gesture.name}"
        val actionValue = prefs.getString(actionKey, "none") ?: "none"

        Log.d("GestureEngine", "Target Vector: [$dynamicZone] -> Action Value: $actionValue")
        triggerHardwareHaptic(35, 160)

        ActionDispatcher.execute(service ?: context, actionValue)
    }

    private fun loadActiveCategoryGrid() {
        if (activeCatIndex !in cachedCategories.indices) return
        val targetId = cachedCategories[activeCatIndex].id
        if (targetId == "launcher_settings_virtual_id") {
            launchLauncherSettings()
            return
        }
        if (categoryGridCache.containsKey(targetId)) {
            placedAppsList = categoryGridCache[targetId]!!.toMutableList()
            totalGridContentHeight = categoryHeightCache[targetId] ?: 0f
            cachedApps = categoryAppsCache[targetId] ?: emptyList()
            preloadActiveCategoryIcons()
        } else {
            val appsList = dataBridge.getAppsForCategory(targetId)
            categoryAppsCache[targetId] = appsList
            cachedApps = appsList
            buildPackedGridLayout()
            categoryGridCache[targetId] = ArrayList(placedAppsList)
            categoryHeightCache[targetId] = totalGridContentHeight
            preloadActiveCategoryIcons()
        }
    }

    private fun preloadActiveCategoryIcons() {
        try {
            applicationIconCache.clear()
            val pm = context.packageManager
            for (target in cachedApps) {
                if (!target.isWidget && !applicationIconCache.containsKey(target.packageName)) {
                    val intent = pm.getLaunchIntentForPackage(target.packageName)
                    val drawable = if (intent != null) pm.getActivityIcon(intent) else pm.getApplicationIcon(target.packageName)
                    applicationIconCache[target.packageName] = drawable
                }
            }
        } catch (e: Exception) {}
    }

    private fun evaluateSpatialMetrics(rawX: Float, rawY: Float, localX: Float, localY: Float) {
        val wF = width.toFloat(); val hF = height.toFloat()
        if (wF <= 0f || hF <= 0f) return
        depthPercentage = ((wF - localX) / wF).coerceIn(0f, 1f)
        val verticalComfortScope = hF * 0.40f
        val verticalStartAnchor = hF * 0.30f
        val normalizedVerticalProgress = ((localY - verticalStartAnchor) / verticalComfortScope).coerceIn(0f, 1f)

        if (currentLayer == CruiseLayer.CATEGORY) {
            activeItem = null; viewportScrollOffset = 0f
            if (wF >= 500f && (touchDownRawX - rawX) > (wF * 0.12f) && (System.currentTimeMillis() - entranceStartTime) > 150L) {
                currentLayer = CruiseLayer.GRID; loadActiveCategoryGrid(); invalidate(); return
            }
            if (cachedCategories.isNotEmpty()) {
                val startYArea = hF * 0.25f; val endYArea = hF * 0.75f
                val catLineH = (endYArea - startYArea) / cachedCategories.size

                val isAtBottom = (activeCatIndex == cachedCategories.size - 1)
                val isDraggingDown = (rawY > lastTouchRawY)

                if (isAtBottom && isDraggingDown) {
                    val stepDistance = abs(rawY - lastTouchRawY)
                    if (stepDistance > 2f) {
                        overScrollBoundaryAccumulator += stepDistance * 0.45f
                    }
                    if (overScrollBoundaryAccumulator > 120f && !settingsCategoryAppended) {
                        settingsCategoryAppended = true
                        triggerHardwareHaptic(35, 160)
                        val mutableCats = cachedCategories.toMutableList()
                        val hasSettings = mutableCats.any { it.id == "launcher_settings_virtual_id" }
                        if (!hasSettings) {
                            mutableCats.add(LightspeedDataBridge.CategoryNode("launcher_settings_virtual_id", "⚙ Settings"))
                            cachedCategories = mutableCats
                        }
                    }
                } else if (normalizedVerticalProgress < 1.0f) {
                    overScrollBoundaryAccumulator = (overScrollBoundaryAccumulator - 12f).coerceAtLeast(0f)
                }

                val resistanceFactor = if (overScrollBoundaryAccumulator > 0f) (1.0f / (1.0f + (overScrollBoundaryAccumulator * 0.008f))) else 1.0f
                val adjustedProgress = if (isAtBottom && isDraggingDown) {
                    normalizedVerticalProgress + (overScrollBoundaryAccumulator * 0.0005f * resistanceFactor)
                } else {
                    normalizedVerticalProgress
                }

                categoryVisualOffset = (hF / 2f) - (startYArea + (catLineH / 2f) + (adjustedProgress * ((cachedCategories.size - 1) * catLineH)))

                var closestIndex = activeCatIndex; var minDistance = Float.MAX_VALUE
                for (index in cachedCategories.indices) {
                    val d = abs((startYArea + (index * catLineH) + (catLineH / 2f) + categoryVisualOffset) - (hF / 2f))
                    if (d < minDistance) { minDistance = d; closestIndex = index }
                }
                if (closestIndex != activeCatIndex) activeCatIndex = closestIndex
            }
        } else {
            val nextLayerState = when {
                (wF - localX) <= (wF * 0.05f) -> CruiseLayer.CATEGORY
                (wF - localX) <= (wF * 0.40f) -> CruiseLayer.GRID
                else -> CruiseLayer.STICKY_PIN
            }
            if (currentLayer == CruiseLayer.GRID && nextLayerState == CruiseLayer.CATEGORY) {
                currentLayer = CruiseLayer.CATEGORY
                touchDownRawX = rawX; touchDownRawY = rawY; lastTouchRawY = rawY
                placedAppsList.clear(); cachedApps = emptyList(); lastLoadedCategoryId = null
                if (cachedCategories.isNotEmpty()) {
                    val startYArea = hF * 0.25f; val endYArea = hF * 0.75f
                    val catLineH = (endYArea - startYArea) / cachedCategories.size

                    val maxScroll = totalGridContentHeight - (hF * 0.79f)
                    val ratio = if (maxScroll > 0f) (viewportScrollOffset / maxScroll).coerceIn(0f, 1f) else normalizedVerticalProgress
                    categoryVisualOffset = (hF / 2f) - (startYArea + (catLineH / 2f) + (ratio * ((cachedCategories.size - 1) * catLineH)))
                    var closestIndex = 0; var minDistance = Float.MAX_VALUE
                    for (index in cachedCategories.indices) {
                        val d = abs((startYArea + (index * catLineH) + (catLineH / 2f) + categoryVisualOffset) - (hF / 2f))
                        if (d < minDistance) { minDistance = d; closestIndex = index }
                    }
                    activeCatIndex = closestIndex
                }
                invalidate(); return
            }
            currentLayer = nextLayerState
            if (currentLayer == CruiseLayer.CATEGORY) {
                touchDownRawX = rawX; touchDownRawY = rawY; lastTouchRawY = rawY
                placedAppsList.clear(); cachedApps = emptyList(); lastLoadedCategoryId = null
                invalidate(); return
            }
            virtualCursorX = (wF * 0.94f) - (((wF - localX).coerceAtLeast(0f) / (wF * 0.30f)).coerceIn(0f, 1f) * (wF * 0.88f))
            val gTop = hF * 0.16f; val gBot = hF * 0.94f; val gScope = gBot - gTop
            virtualCursorY = gTop + (normalizedVerticalProgress * gScope)
            if (totalGridContentHeight <= gScope) { viewportScrollOffset = 0f } 
            else { viewportScrollOffset = normalizedVerticalProgress * (totalGridContentHeight - gScope) }

            var closestMatchItem: LightspeedDataBridge.LaunchTarget? = null; var minD = Float.MAX_VALUE
            for (item in placedAppsList) {
                val d = hypot((virtualCursorX - item.bounds.centerX()).toDouble(), ((virtualCursorY + viewportScrollOffset) - item.bounds.centerY()).toDouble()).toFloat()
                if (d < minD) { minD = d; closestMatchItem = item.app }
            }
            activeItem = if (closestMatchItem != null && placedAppsList.isNotEmpty() && currentLayer != CruiseLayer.STICKY_PIN) closestMatchItem else null
        }
        invalidate()
    }

    private fun buildPackedGridLayout() {
        val wF = width.toFloat(); val hF = height.toFloat()
        if (wF <= 0f) return
        val columns = querySystemColumnPreference(resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE)
        val cellW = (wF * 0.92f) / columns; val cellH = cellW * 1.1f
        placedAppsList.clear()
        val occupied = HashSet<Pair<Int, Int>>()
        for (app in cachedApps.distinctBy { "${it.packageName}_${it.activityName}_${it.instanceId}" }) {
            var sW = 1; var sH = 1
            if (app.isWidget) { sW = if (app.spanX > 0) app.spanX.coerceIn(1, columns) else columns / 2; sH = if (app.spanY > 0) app.spanY.coerceIn(1, 12) else 2 }
            var row = 0; var found = false
            while (!found) {
                for (col in 0..columns - sW) {
                    var clear = true
                    for (dr in 0 until sH) { for (dc in 0 until sW) { if (occupied.contains(Pair(row + dr, col + dc))) { clear = false; break } }; if (!clear) break }
                    if (clear) {
                        placedAppsList.add(PlacedItem(app, RectF((wF * 0.04f) + (col * cellW), (hF * 0.16f) + (row * cellH), (wF * 0.04f) + ((col + sW) * cellW), (hF * 0.16f) + ((row + sH) * cellH))))
                        for (dr in 0 until sH) { for (dc in 0 until sW) { occupied.add(Pair(row + dr, col + dc)) } }
                        found = true; break
                    }
                }
                if (!found) row++
            }
        }
        totalGridContentHeight = if (occupied.isEmpty()) 0f else ((occupied.maxOf { it.first }) + 1) * cellH
    }

    private fun querySystemColumnPreference(isLandscape: Boolean): Int {
        val prefs = context.getSharedPreferences("default", Context.MODE_PRIVATE)
        return try { val v = prefs.all[if (isLandscape) "pref_numcolsland" else "pref_numcolspor"]; if (v is Int) v else v?.toString()?.toInt() ?: 4 } catch (e: Exception) { 4 }
    }

    private fun executeLaunch(target: LightspeedDataBridge.LaunchTarget) {
        val launchIntent = context.packageManager.getLaunchIntentForPackage(target.packageName)
        if (launchIntent != null) {
            launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            try { context.startActivity(launchIntent) } catch (e: Exception) {}
        }
    }

    private fun launchLauncherSettings() {
        dismissOverlay()
        try {
            val settingsIntent = Intent(context, Class.forName("com.sbf.lightspeed.SettingsActivity")).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            }
            context.startActivity(settingsIntent)
        } catch (e: Exception) {
            val alternativeIntent = context.packageManager.getLaunchIntentForPackage(context.packageName)?.apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            }
            alternativeIntent?.let { context.startActivity(it) }
        }
    }

    private fun dismissOverlay() {
        snapAnimator?.cancel()
        isStickyPinned = false; isCruising = false; currentLayer = CruiseLayer.HIDDEN
        activeItem = null; activeCatIndex = -1; viewportScrollOffset = 0f; categoryVisualOffset = 0f
        placedAppsList.clear(); cachedApps = emptyList(); cachedCategories = emptyList()
        service?.updateWindowLayout(false)
        updateMetricsDimensions()
        invalidate()
    }

    private fun getAppsForActiveGear(setIndex: Int, ringIndex: Int): List<String> {
        val prefs = context.getSharedPreferences("default", Context.MODE_PRIVATE)
        val setsString = prefs.getString("gear_sets_order", "0,1,2,3") ?: "0,1,2,3"
        val setsList = setsString.split(",").filter { it.isNotEmpty() }
        val setId = if (setIndex in setsList.indices) setsList[setIndex] else setIndex.toString()
        val csvString = prefs.getString("gear_set_${setId}_ring_${ringIndex}_packages", null)
        if (!csvString.isNullOrEmpty()) {
            return csvString.split(",").map { it.trim() }.filter { it.isNotEmpty() }
        }
        return when (setId) {
            "0" -> if (ringIndex == 0) listOf("com.android.vending", "org.fdroid.fdroid") else listOf("com.termux", "com.android.settings")
            else -> emptyList()
        }
    }

    private fun getGearSetNameById(setId: String): String {
        val prefs = context.getSharedPreferences("default", Context.MODE_PRIVATE)
        val defaultName = when(setId) {
            "0" -> "POWER USER ANDROID"
            "1" -> "MY APP STORES"
            "2" -> "UTILITIES SECTOR"
            "3" -> "ENTERTAINMENT DECK"
            else -> "CUSTOM SET"
        }
        val saved = prefs.getString("gear_set_${setId}_name", defaultName) ?: defaultName
        return if (saved == "SET A" || saved == "SET B" || saved == "SET C" || saved == "SET D" || saved == "SET" || saved.isEmpty()) defaultName else saved
    }

    private fun getGearSetNameByIndex(index: Int): String {
        val prefs = context.getSharedPreferences("default", Context.MODE_PRIVATE)
        val setsString = prefs.getString("gear_sets_order", "0,1,2,3") ?: "0,1,2,3"
        val setsList = setsString.split(",").filter { it.isNotEmpty() }
        if (index in setsList.indices) {
            return getGearSetNameById(setsList[index])
        }
        val alphabetLabel = if (index in 0..25) "${'A' + index}" else index.toString()
        return "SET " + alphabetLabel
    }


    private fun buildMechanicalGearPath(cx: Float, cy: Float, innerR: Float, outerR: Float, teeth: Int, rotationDeg: Float): android.graphics.Path {
        val path = android.graphics.Path()
        val angleStep = 2.0 * Math.PI / teeth
        val radOffset = Math.toRadians(rotationDeg.toDouble())
        
        for (i in 0 until teeth) {
            val angle = i * angleStep + radOffset
            
            // Tooth Tip Point A
            var x = cx + outerR * Math.cos(angle).toFloat()
            var y = cy + outerR * Math.sin(angle).toFloat()
            if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
            
            // Tooth Tip Point B (Flat top cogs)
            x = cx + outerR * Math.cos(angle + angleStep * 0.4).toFloat()
            y = cy + outerR * Math.sin(angle + angleStep * 0.4).toFloat()
            path.lineTo(x, y)
            
            // Tooth Root Valley Point A
            x = cx + innerR * Math.cos(angle + angleStep * 0.5).toFloat()
            y = cy + innerR * Math.sin(angle + angleStep * 0.5).toFloat()
            path.lineTo(x, y)
            
            // Tooth Root Valley Point B
            x = cx + innerR * Math.cos(angle + angleStep * 0.9).toFloat()
            y = cy + innerR * Math.sin(angle + angleStep * 0.9).toFloat()
            path.lineTo(x, y)
        }
        path.close()
        return path
    }

    private fun processGyroscopeTouchPhysics(rawX: Float, rawY: Float) {
        val density = resources.displayMetrics.density
        val deltaX = touchDownRawX - rawX
        val deltaY = rawY - lastTouchRawY

        // Abort Track: Closing down gear layer if thumb slides back to bezel origin
        if (deltaX < (10f * density)) {
            currentLayer = CruiseLayer.HIDDEN
            isCruising = false
            service?.updateWindowLayout(false) // CRITICAL DIRECTIVE: Disengage overlay window touch-trap immediately
            updateMetricsDimensions()
            triggerHardwareHaptic(15, 80)
            invalidate()
            return
        }

        // Depth Axis Calibration (X-Axis Ring Selection)
        val outerThreshold = 50f * density
        val innerThreshold = 120f * density
        val cubeFlipThreshold = 220f * density

        val previousRing = activeGearRing
        activeGearRing = when {
            deltaX < outerThreshold -> 0
            deltaX < innerThreshold -> 1
            else -> 2
        }

        if (previousRing != activeGearRing && activeGearRing != 2) {
            triggerHardwareHaptic(20, 120) // Micro haptic tick on ring crossing
        }

        // Modular 3D Cube Rotation Gate (Over-Swipe Carousel)
        if (deltaX > cubeFlipThreshold) {
            if (!isCubeRotationFired) {
                isCubeRotationFired = true
                activeGearSetIndex = (activeGearSetIndex + 1) % totalGearSetsCount
                triggerHardwareHaptic(55, 255) // Solid mechanical locking thud
            }
        } else if (deltaX < innerThreshold + (25f * density)) {
            isCubeRotationFired = false
        }

        // Rotational Axis Crank Math: Scale angular velocity inversely by item density
        val itemDensity = if (activeGearRing == 0) 6f else 12f // Baseline densities
        val scalingConstant = 45f
        if (activeGearRing in 0..1) {
            gearRingRotations[activeGearRing] += (deltaY / density) * (scalingConstant / itemDensity)
        }

        lastTouchRawX = rawX
        lastTouchRawY = rawY
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        if (activeGearSetIndex >= totalGearSetsCount) { activeGearSetIndex = 0 }

        val m3Primary = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
            context.resources.getColor(android.R.color.system_accent1_600, context.theme)
        } else {
            Color.parseColor("#6750A4")
        }
        val m3Secondary = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
            context.resources.getColor(android.R.color.system_accent1_300, context.theme)
        } else {
            Color.parseColor("#D0BCFF")
        }

        super.onDraw(canvas)
        if (currentLayer == CruiseLayer.COCKPIT_HANGAR) {
            canvas.drawColor(Color.argb(245, 12, 12, 16))
            val cx = width / 2f; val cy = height / 2f; val d = resources.displayMetrics.density

            textPaint.color = Color.WHITE; textPaint.textSize = 15f * d; textPaint.textAlign = Paint.Align.CENTER
            canvas.drawText("GEAR ASSEMBLY COCKPIT MANAGER", cx, cy - 360f, textPaint)

            elementPaint.style = Paint.Style.STROKE; elementPaint.strokeWidth = 1f * d; elementPaint.color = Color.argb(100, 255, 255, 255)
            canvas.drawRoundRect(cx + 115f * d, cy - 373f * d, cx + 175f * d, cy - 351f * d, 4f * d, 4f * d, elementPaint)
            textPaint.color = Color.WHITE; textPaint.textSize = 10f * d
            canvas.drawText("MANAGE", cx + 145f * d, cy - 358f * d, textPaint)

            val prefs = context.getSharedPreferences("default", Context.MODE_PRIVATE)
            val setsString = prefs.getString("gear_sets_order", "0,1,2,3") ?: "0,1,2,3"
            val setsList = setsString.split(",").filter { it.isNotEmpty() }
            if (activeGearSetIndex >= setsList.size) { activeGearSetIndex = 0 }

            val itemW = 76f * d
            val startX = cx - ((setsList.size - 1) * itemW / 2f)
            textPaint.textSize = 11f * d
            for (g in setsList.indices) {
                val setId = setsList[g]
                val defaultName = when(setId) { "0" -> "SET A"; "1" -> "SET B"; "2" -> "SET C"; "3" -> "SET D"; else -> "SET" }
                val setName = prefs.getString("gear_set_${setId}_name", defaultName) ?: defaultName
                val btnX = startX + g * itemW; val btnY = cy - 310f
                elementPaint.style = Paint.Style.FILL
                elementPaint.color = if (activeGearSetIndex == g) m3Primary else Color.argb(40, 255, 255, 255)
                canvas.drawRoundRect(btnX - (32f * d), btnY - (14f * d), btnX + (32f * d), btnY + (14f * d), 6f * d, 6f * d, elementPaint)
                textPaint.color = if (activeGearSetIndex == g) Color.WHITE else Color.argb(180, 255, 255, 255)
                canvas.drawText(setName, btnX, btnY + (4f * d), textPaint)
            }

            elementPaint.style = Paint.Style.STROKE; elementPaint.strokeWidth = 2f
            elementPaint.color = Color.argb(60, Color.red(m3Primary), Color.green(m3Primary), Color.blue(m3Primary))
            canvas.drawCircle(cx, cy, 240f, elementPaint); canvas.drawCircle(cx, cy, 150f, elementPaint)

            val r0 = getAppsForActiveGear(activeGearSetIndex, 0)
            val r1 = getAppsForActiveGear(activeGearSetIndex, 1)

            textPaint.textAlign = Paint.Align.CENTER; textPaint.color = m3Secondary
            canvas.drawText("GEAR 1 (OUTER): ${r0.size} ITEMS", cx, cy - 110f, textPaint)
            canvas.drawText("GEAR 2 (INNER): ${r1.size} ITEMS", cx, cy + 50f, textPaint)

            elementPaint.style = Paint.Style.FILL; elementPaint.color = m3Primary
            canvas.drawRoundRect(cx - 36f * d, cy - 95f * d, cx + 36f * d, cy - 75f * d, 4f * d, 4f * d, elementPaint)
            canvas.drawRoundRect(cx - 36f * d, cy + 65f * d, cx + 36f * d, cy + 85f * d, 4f * d, 4f * d, elementPaint)

            textPaint.color = Color.WHITE; canvas.drawText("+ ADD", cx, cy - 81f * d, textPaint); canvas.drawText("+ ADD", cx, cy + 79f * d, textPaint)

            elementPaint.style = Paint.Style.FILL; elementPaint.color = Color.argb(45, 230, 80, 80)
            canvas.drawCircle(cx, cy - 15f * d, 20f * d, elementPaint)
            textPaint.color = Color.parseColor("#F2B8B5"); canvas.drawText("WIPE", cx, cy - 11f * d, textPaint)
            return
        }
        val w = width.toFloat(); val h = height.toFloat()
        if (currentLayer == CruiseLayer.HIDDEN) {
            highlightPaint.color = Color.argb(40, 255, 255, 255)
            canvas.drawRect(topVisualBounds, highlightPaint)
            canvas.drawRoundRect(centerVisualBounds, 6f, 6f, highlightPaint)
            canvas.drawRect(bottomVisualBounds, highlightPaint)
            return
        }

        if (currentLayer == CruiseLayer.FAVORITES_GEARS) {
            // Apply canvas darkening backdrop for progressive blur alignment
            canvas.drawColor(Color.argb(160, 10, 10, 14))
            
            val cx = width / 2f
            val cy = height / 2f
            
            elementPaint.style = Paint.Style.STROKE
            elementPaint.strokeWidth = 3.5f
            
            // Draw Industrial Machine Gear 1 (Outer Ring, 16 Heavy Teeth Cogs)
            elementPaint.color = if (activeGearRing == 0) Color.argb(220, 240, 240, 245) else Color.argb(50, 120, 120, 130)
            val gearPath1 = buildMechanicalGearPath(cx, cy, 290f, 335f, 16, gearRingRotations[0])
            canvas.drawPath(gearPath1, elementPaint)
            
            // Draw Industrial Machine Gear 2 (Inner Interlocking Ring, 12 Cogs)
            elementPaint.color = if (activeGearRing == 1) Color.argb(220, 240, 240, 245) else Color.argb(50, 120, 120, 130)
            val gearPath2 = buildMechanicalGearPath(cx, cy, 175f, 215f, 12, gearRingRotations[1])
            canvas.drawPath(gearPath2, elementPaint)
            
            // Render Central Command Hub Centerpiece
            elementPaint.style = Paint.Style.FILL
            elementPaint.color = if (activeGearRing == 2) m3Primary else Color.argb(35, 255, 255, 255)
            canvas.drawCircle(cx, cy, 65f, elementPaint)

            // --- DYNAMIC GEOMETRIC APP ICON PRESENTATION PASS ---
            val pm = context.packageManager
            val sizeRaw = (42f * resources.displayMetrics.density).toInt()

            for (r in 0..1) {
                val radius = if (r == 0) 320f else 200f
                val ringApps = getAppsForActiveGear(activeGearSetIndex, r)
                if (ringApps.isEmpty()) continue
                
                val count = ringApps.size
                val baseRotation = gearRingRotations[r]
                val isRingFocused = (activeGearRing == r)
                
                for (i in ringApps.indices) {
                    val angleDeg = (baseRotation + i * (360f / count)) % 360f
                    val angleRad = Math.toRadians(angleDeg.toDouble())
                    
                    // Core Polar-to-Cartesian projection mapping coordinates
                    val iconCX = cx + radius * Math.cos(angleRad).toFloat()
                    val iconCY = cy + radius * Math.sin(angleRad).toFloat()
                    
                    // Dynamic Focus Interpolation: Scale highlighted icon closest to targeting vector zone (180 deg)
                    val normalizedDeg = if (angleDeg < 0) angleDeg + 360f else angleDeg
                    val isHighlighted = isRingFocused && abs(normalizedDeg - 180f) < (180f / count)
                    val currentScale = if (isHighlighted) 1.25f else 1.0f
                    val currentSize = (sizeRaw * currentScale).toInt()
                    
                    try {
                        val iconDrawable = pm.getApplicationIcon(ringApps[i])
                        iconDrawable.alpha = if (isRingFocused) (if (isHighlighted) 255 else 200) else 90
                        iconDrawable.setBounds(
                            (iconCX - currentSize / 2).toInt(),
                            (iconCY - currentSize / 2).toInt(),
                            (iconCX + currentSize / 2).toInt(),
                            (iconCY + currentSize / 2).toInt()
                        )
                        iconDrawable.draw(canvas)
                    } catch (e: Exception) {
                        // Resilient Fallback: Render structural vector placeholder if asset bundle loading faults
                        elementPaint.style = Paint.Style.FILL
                        elementPaint.color = if (isHighlighted) Color.WHITE else Color.GRAY
                        canvas.drawCircle(iconCX, iconCY, currentSize / 3f, elementPaint)
                    }
                }
            }
            
            // Render Profile Header Label
            textPaint.color = Color.WHITE
            val profileName = when(activeGearSetIndex) {
                0 -> getGearSetNameByIndex(0)
                1 -> getGearSetNameByIndex(1)
                2 -> getGearSetNameByIndex(2)
                else -> getGearSetNameByIndex(3)
            }
            canvas.drawText(profileName, cx, cy - 380f, textPaint)
            return
        }

        if (currentLayer == CruiseLayer.CATEGORY) {
            val decelerationFactor = sin(((System.currentTimeMillis() - entranceStartTime) / 220f).coerceIn(0f, 1f) * PI / 2.0).toFloat()
            canvas.drawColor(Color.argb((135 * decelerationFactor).toInt(), 4, 4, 6))
            if (cachedCategories.isNotEmpty()) {
                val startYArea = h * 0.25f; val endYArea = h * 0.75f
                val catLineH = (endYArea - startYArea) / cachedCategories.size
                cachedCategories.forEachIndexed { idx, cat ->
                    val centerY = startYArea + (idx * catLineH) + (catLineH / 2f) + categoryVisualOffset
                    val relativeDistanceFromCenter = centerY - (h / 2f)
                    val sweepAngleRad = (relativeDistanceFromCenter / ((endYArea - startYArea) / 2f)).coerceIn(-1.2f, 1.2f) * (PI / 2.6f)
                    
                    val targetTextX = w - 75f - (cos(sweepAngleRad).toFloat() * 260f) - ((1f - decelerationFactor) * (w * 0.45f))
                    val distanceRatio = (abs(relativeDistanceFromCenter) / ((endYArea - startYArea) / 2f)).coerceIn(0f, 1f)
                    val zoom = (0.6f + Math.pow(1.0 - distanceRatio, 2.5).toFloat() * 1.6f) * decelerationFactor

                    canvas.save()
                    projectionCamera3D.save()
                    projectionCamera3D.setLocation(0f, 0f, -8f); projectionCamera3D.translate(0f, 0f, cylinderRadius)
                    projectionCamera3D.rotateX(-Math.toDegrees(sweepAngleRad.toDouble()).toFloat()); projectionCamera3D.translate(0f, 0f, -cylinderRadius)
                    projectionCamera3D.getMatrix(transformMatrixPipeline); projectionCamera3D.restore()
                    transformMatrixPipeline.preTranslate(-targetTextX, -centerY)
                    transformMatrixPipeline.postScale(zoom, zoom); transformMatrixPipeline.postTranslate(targetTextX, centerY)
                    canvas.concat(transformMatrixPipeline)

                    if (idx == activeCatIndex) {
                        highlightPaint.color = Color.argb((45 * decelerationFactor).toInt(), 255, 255, 255)
                        canvas.drawRoundRect(w * 0.35f, centerY - (catLineH / 2f) + 4f, w + 150f, centerY + (catLineH / 2f) - 4f, 16f, 16f, highlightPaint)
                        catTextPaint.color = Color.WHITE; catTextPaint.alpha = (255 * decelerationFactor).toInt()
                    } else {
                        catTextPaint.color = Color.parseColor("#8A8A93"); catTextPaint.alpha = (((1.0f - (abs(relativeDistanceFromCenter) / ((endYArea - startYArea) / 2f)).coerceIn(0f, 1f)) * 160).toInt().coerceAtLeast(40) * decelerationFactor).toInt()
                    }
                    canvas.drawText(cat.label, targetTextX, centerY + 18f, catTextPaint)
                    canvas.restore()
                }
            }
            if (decelerationFactor < 1f) postInvalidateOnAnimation()
        } else if (currentLayer == CruiseLayer.GRID || isStickyPinned || currentLayer == CruiseLayer.STICKY_PIN) {
            canvas.drawColor(Color.argb(150, 4, 4, 6))
            canvas.save(); canvas.clipRect(0f, 0f, w, h)
            canvas.translate(0f, -(if (totalGridContentHeight <= (h * 0.79f)) 0f else viewportScrollOffset))
            for (item in placedAppsList) {
                val b = item.bounds; val isSel = activeItem == item.app
                if (isSel) {
                    highlightPaint.color = Color.argb(60, 255, 255, 255)
                    canvas.drawRoundRect(b.left + 5f, b.top + 5f, b.right - 5f, b.bottom - 5f, 16f, 16f, highlightPaint)
                    textPaint.color = Color.WHITE
                } else {
                    elementPaint.color = Color.argb(if (item.app.isWidget) 20 else 12, 255, 255, 255)
                    canvas.drawRoundRect(b.left + 5f, b.top + 5f, b.right - 5f, b.bottom - 5f, 16f, 16f, elementPaint)
                    textPaint.color = Color.parseColor("#E5E5EA")
                }
                if (item.app.isWidget) {
                    canvas.drawText(if (item.app.label.length > 16) item.app.label.take(14) + ".." else item.app.label, b.centerX(), b.centerY() + 8f, textPaint)
                } else {
                    val icon = applicationIconCache[item.app.packageName]
                    if (icon != null) {
                        val sz = minOf(b.width() * 0.48f, b.height() * 0.48f)
                        icon.setBounds((b.centerX() - sz / 2f).toInt(), (b.centerY() - sz * 0.62f).toInt(), (b.centerX() + sz / 2f).toInt(), (b.centerY() - sz * 0.62f + sz).toInt())
                        icon.draw(canvas)
                        canvas.drawText(if (item.app.label.length > 12) item.app.label.take(10) + ".." else item.app.label, b.centerX(), b.centerY() + (sz * 0.55f) + 14f, textPaint)
                    } else {
                        canvas.drawText(if (item.app.label.length > 14) item.app.label.take(12) + ".." else item.app.label, b.centerX(), b.centerY() + 8f, textPaint)
                    }
                }
            }
            canvas.restore()
        }
    }
}

fun Context.defaultPrefs(): android.content.SharedPreferences = getSharedPreferences("default", Context.MODE_PRIVATE)
