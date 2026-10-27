package com.sbf.lightspeed
import com.sbf.lightspeed.system.logSwallowed
import com.sbf.lightspeed.system.LightspeedPreferences
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
import com.sbf.lightspeed.settings.LightspeedActionRegistry
import com.sbf.lightspeed.system.ActionDispatcher
import com.sbf.lightspeed.system.ElevatedTaskCloser
import com.sbf.lightspeed.system.LightspeedHapticEngine
import com.sbf.lightspeed.system.LightspeedHudRenderer
import com.sbf.lightspeed.system.LightspeedTimeoutEngine
import com.sbf.lightspeed.system.defaultPrefs
import java.net.URISyntaxException
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.floor
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.sin
import com.sbf.lightspeed.settings.resolveCanvasPalette

private val cachedHudHeaderRect = RectF()

internal fun LightspeedCruiseOverlay.handleDraw(canvas: Canvas, superCall: () -> Unit) {
        if (activeGearSetIndex >= totalGearSetsCount) { activeGearSetIndex = 0 }
        // No per-frame prefs I/O — use render cache updated by prefChangeListener

        val snapshot = com.sbf.lightspeed.settings.DeckThemeManager.getSnapshot(context)
        val palette = snapshot.resolveCanvasPalette(context)
        val m3Primary = palette.primaryAccentArgb
        val m3Secondary = palette.secondaryAccentArgb

        superCall()
        if (currentLayer == CruiseLayer.COCKPIT_HANGAR) {
            val setsList = getGearSetsOrder(isOpenedFromLeftFlank)
            deepSpaceRenderer.drawDeepSpace(
                HangarRenderState(
                    canvas = canvas,
                    context = context,
                    screenW = width.toFloat(),
                    screenH = height.toFloat(),
                    density = resources.displayMetrics.density,
                    m3Primary = m3Primary,
                    m3Secondary = m3Secondary,
                    isOpenedFromLeftFlank = isOpenedFromLeftFlank,
                    activeGearSetIndex = activeGearSetIndex,
                    activeHangarRing = activeHangarRing,
                    isHangarEjectArmed = isHangarEjectArmed,
                    gearRingRotations = gearRingRotations,
                    hangarBayScrollOffset = hangarBayScrollOffset,
                    setsList = setsList,
                    resolveCleanAppLabel = ::resolveCleanAppLabel,
                    getAppsForActiveGear = ::getAppsForActiveGear,
                    getFlankLaunchBehavior = ::getFlankLaunchBehavior,
                    onIconResolved = ::postInvalidate
                )
            )
            drawCruiseCentralPill(canvas, m3Primary)
            return
        }
        val w = width.toFloat(); val h = height.toFloat()
        if (currentLayer == CruiseLayer.HIDDEN || currentLayer == CruiseLayer.NEUTRAL) {
            val d = resources.displayMetrics.density

            val isRightFlankUnified = renderCacheRightFlankUnified
            val isRightUnifiedExpanded = renderCacheRightUnifiedExpanded
            val isTopExpanded = renderCacheTopExpanded
            val isCenterExpanded = renderCacheCenterExpanded
            val isBottomExpanded = renderCacheBottomExpanded
            val isSidebarPreview = renderCacheSidebarPreview

            val centerTransparency = renderCacheCenterTransparency
            val topTransparency = renderCacheTopTransparency
            val bottomTransparency = renderCacheBottomTransparency

            val glowStyle = renderCacheGlowStyle

            com.sbf.lightspeed.system.LightspeedDeflectorRenderer.drawDeflectorWing(
                canvas = canvas,
                isLeft = false,
                density = d,
                w = w,
                h = h,
                topTouchBounds = topTouchBounds,
                centerTouchBounds = centerTouchBounds,
                bottomTouchBounds = bottomTouchBounds,
                isCurrentlyTouched = isCurrentlyTouched,
                activeZoneIsCenter = currentActiveZone == TouchZone.CENTER_CRUISE,
                activeZoneIsTop = currentActiveZone == TouchZone.TOP_EDGE,
                activeZoneIsBottom = currentActiveZone == TouchZone.BOTTOM_EDGE,
                glowFraction = glowFraction,
                centerTransparency = centerTransparency,
                topTransparency = topTransparency,
                bottomTransparency = bottomTransparency,
                isReview = isSidebarPreview && (isTopExpanded || isCenterExpanded || isBottomExpanded),
                m3Primary = m3Primary,
                glowStyle = glowStyle,
                isGlowEnabled = renderCacheGlowEnabled,
                useM3Color = renderCacheUseM3Color,
                pillStyle = com.sbf.lightspeed.system.LightspeedPreferences.getDeflectorPillStyle(context, false),
                touchY = null,
                visualWidthPx = centerVisualWidthPx
            )
            if (currentLayer == CruiseLayer.NEUTRAL) return
            return
        }

        if (currentLayer == CruiseLayer.FAVORITES_GEARS) {
            drawFavoritesGearsLayer(canvas, m3Primary, m3Secondary)
            return
        }

        if (currentLayer == CruiseLayer.CATEGORY) {
            val density = resources.displayMetrics.density
            val decelerationFactor = sin(((System.currentTimeMillis() - entranceStartTime) / 220f).coerceIn(0f, 1f) * PI / 2.0).toFloat()
            
            // 1. Deep Space Astrogation Void Backdrop
            canvas.drawColor(Color.argb((205 * decelerationFactor).toInt(), 4, 6, 12))
            
            // 2. Cosmic Stardust Particle Field
            drawCosmicStarfield(canvas, w, h, density, decelerationFactor)

            // 3. Top Flight Telemetry Header (Sector Cruise HUD with Auto-Fit)
            val topBadgeY = 54f * density
            val currentSector = (activeCatIndex + 1).toString().padStart(2, '0')
            val totalSectors = cachedCategories.size.toString().padStart(2, '0')
            val activeCatName = cachedCategories.getOrNull(activeCatIndex)?.label?.uppercase() ?: "CRUISE"
            val hudHeader = "◈ SECTOR $currentSector / $totalSectors  ✦  $activeCatName ◈"

            textPaint.typeface = android.graphics.Typeface.DEFAULT_BOLD
            textPaint.textAlign = Paint.Align.CENTER
            var hudTextSize = 10.5f * density
            textPaint.textSize = hudTextSize
            val maxHudW = w * 0.85f
            while (textPaint.measureText(hudHeader) > maxHudW && hudTextSize > 7.5f * density) {
                hudTextSize -= 0.5f * density
                textPaint.textSize = hudTextSize
            }
            val hudWidth = textPaint.measureText(hudHeader)

            highlightPaint.style = Paint.Style.FILL
            highlightPaint.color = Color.argb((150 * decelerationFactor).toInt(), 10, 14, 26)
            cachedHudHeaderRect.set(w / 2f - hudWidth / 2f - 14f * density, topBadgeY - 13f * density, w / 2f + hudWidth / 2f + 14f * density, topBadgeY + 13f * density)
            canvas.drawRoundRect(cachedHudHeaderRect, 9f * density, 9f * density, highlightPaint)

            textPaint.color = Color.WHITE
            textPaint.alpha = (255 * decelerationFactor).toInt()
            canvas.drawText(hudHeader, w / 2f, topBadgeY + 3.5f * density, textPaint)

            // Subtitle Guidance Hint for Category Cruise
            textPaint.textSize = 8.5f * density
            textPaint.typeface = android.graphics.Typeface.DEFAULT
            textPaint.color = Color.argb((160 * decelerationFactor).toInt(), 180, 210, 245)
            canvas.drawText(if (isOpenedFromLeftFlank) "PULL RIGHT >> ENTER STAR SYSTEM  •  SLIDE LEFT << CANCEL" else "PULL LEFT >> ENTER STAR SYSTEM  •  SLIDE RIGHT << CANCEL", w / 2f, topBadgeY + 26f * density, textPaint)

            // 4. Galactic Horizon: Cruising Through Nebulae & Star Systems
            if (cachedCategories.isNotEmpty()) {
                val startYArea = h * 0.25f; val endYArea = h * 0.75f
                val catLineH = ((endYArea - startYArea) / cachedCategories.size) * categorySpacingScale()

                cachedCategories.forEachIndexed { idx, cat ->
                    val centerY = startYArea + (idx * catLineH) + (catLineH / 2f) + categoryVisualOffset
                    val relativeDistanceFromCenter = centerY - (h / 2f)
                    val sweepAngleRad = (relativeDistanceFromCenter / ((endYArea - startYArea) / 2f)).coerceIn(-1.2f, 1.2f) * (PI / 2.6f)
                    
                    // Symmetrical anchor based on flank origin
                    val targetTextX = if (isOpenedFromLeftFlank) {
                        (28f * density) + (cos(sweepAngleRad).toFloat() * 100f * density)
                    } else {
                        w - (28f * density) - (cos(sweepAngleRad).toFloat() * 100f * density)
                    }
                    val distanceRatio = (abs(relativeDistanceFromCenter) / ((endYArea - startYArea) / 2f)).coerceIn(0f, 1f)
                    val zoom = 0.70f + Math.pow(1.0 - distanceRatio, 2.5).toFloat() * 1.35f

                    // If active category, draw its luminous chromatic Nebula Cloud behind it
                    if (idx == activeCatIndex) {
                        drawGalacticNebula(
                            canvas = canvas,
                            cx = if (isOpenedFromLeftFlank) targetTextX + (100f * density) else targetTextX - (100f * density),
                            cy = centerY,
                            radius = 185f * density,
                            m3Primary = m3Primary,
                            alphaFactor = decelerationFactor
                        )
                    }

                    canvas.save()
                    projectionCamera3D.save()
                    projectionCamera3D.setLocation(0f, 0f, -8f); projectionCamera3D.translate(0f, 0f, cylinderRadius)
                    projectionCamera3D.rotateX(-Math.toDegrees(sweepAngleRad.toDouble()).toFloat()); projectionCamera3D.translate(0f, 0f, -cylinderRadius)
                    projectionCamera3D.getMatrix(transformMatrixPipeline); projectionCamera3D.restore()
                    transformMatrixPipeline.preTranslate(-targetTextX, -centerY)
                    transformMatrixPipeline.postScale(zoom, zoom); transformMatrixPipeline.postTranslate(targetTextX, centerY)
                    canvas.concat(transformMatrixPipeline)

                    val labelText = cat.label.uppercase()
                    val focusFactor = Math.pow((1.0 - distanceRatio).coerceIn(0.0, 1.0), 3.0).toFloat() * decelerationFactor
                    
                    // 1. Stable, constant category font size based on text length (Zero per-frame shivering)
                    val baseTargetSize = when {
                        labelText.length <= 8 -> 18.5f * density
                        labelText.length <= 13 -> 14.5f * density
                        labelText.length <= 18 -> 12f * density
                        else -> 10.5f * density
                    }

                    // 2. Smoothly interpolate font size, alpha, and color with distance from center
                    val currentTextSize = 13f * density + (baseTargetSize - 13f * density) * focusFactor
                    catTextPaint.textSize = currentTextSize
                    catTextPaint.textAlign = if (isOpenedFromLeftFlank) Paint.Align.LEFT else Paint.Align.RIGHT
                    catTextPaint.typeface = if (focusFactor > 0.6f) android.graphics.Typeface.DEFAULT_BOLD else android.graphics.Typeface.DEFAULT

                    // Color interpolation: Dim starlight (#9EA7B8) to blazing white
                    val rCol = (158 + (255 - 158) * focusFactor).toInt()
                    val gCol = (167 + (255 - 167) * focusFactor).toInt()
                    val bCol = (184 + (255 - 184) * focusFactor).toInt()
                    val alpha = ((35 + 220 * focusFactor) * decelerationFactor).toInt().coerceIn(0, 255)
                    catTextPaint.color = Color.argb(alpha, rCol, gCol, bCol)

                    // Draw smoothly gliding Category Star System Typography
                    canvas.drawText(labelText, targetTextX, centerY + (5f + 1f * focusFactor) * density, catTextPaint)

                    // 3. Glowing Celestial Star Beacon (Fades in smoothly as category enters focus)
                    if (focusFactor > 0.25f) {
                        val measuredLabelW = catTextPaint.measureText(labelText)
                        val beaconAlpha = ((focusFactor - 0.25f) / 0.75f).coerceIn(0f, 1f)
                        textPaint.textAlign = if (isOpenedFromLeftFlank) Paint.Align.LEFT else Paint.Align.RIGHT
                        textPaint.typeface = android.graphics.Typeface.DEFAULT_BOLD
                        textPaint.textSize = (currentTextSize * 0.75f).coerceAtLeast(8.5f * density)
                        textPaint.color = m3Primary
                        textPaint.alpha = (255 * beaconAlpha * decelerationFactor).toInt()
                        val beaconX = if (isOpenedFromLeftFlank) targetTextX + measuredLabelW + 6f * density else targetTextX - measuredLabelW - 6f * density
                        canvas.drawText("✦", beaconX, centerY + 5.5f * density, textPaint)
                    }
                    canvas.restore()
                }
            }
            if (decelerationFactor < 1f) postInvalidateOnAnimation()
        } else if (currentLayer == CruiseLayer.GRID || isStickyPinned || currentLayer == CruiseLayer.STICKY_PIN) {
            drawCruiseGridLayer(canvas, w, h)
        }

        drawHyperdriveWarpSurge(canvas, m3Primary, resources.displayMetrics.density)
        drawCruiseCentralPill(canvas, m3Primary)
}

