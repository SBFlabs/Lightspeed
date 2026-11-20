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
        val palette = if (currentLayer == CruiseLayer.COCKPIT_HANGAR || currentLayer == CruiseLayer.FAVORITES_GEARS) {
            com.sbf.lightspeed.system.CockpitGimbalConfigCache.resolveCockpitPalette(context)
        } else {
            snapshot.resolveCanvasPalette(context)
        }
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
                isReview = isSidebarPreview,
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
            val catAccent = categoryFocusAccentColor(m3Primary)
            val decelerationFactor = sin(((System.currentTimeMillis() - entranceStartTime) / 220f).coerceIn(0f, 1f) * PI / 2.0).toFloat()
            
            // 1. Deep Space Astrogation Void Backdrop
            canvas.drawColor(Color.argb((205 * decelerationFactor).toInt(), 4, 6, 12))
            
            // 2. Cosmic Stardust Particle Field
            drawCosmicStarfield(canvas, w, h, density, decelerationFactor)

            // 3. Top Flight Telemetry Header (Per-Category Configurable HUD Strip / Pods; filtered per-widget by scope)
            drawCategoryTopHudStrip(canvas, w, density, decelerationFactor)

            // 4. Galactic Horizon: Cruising Through Nebulae & Star Systems
            if (cachedCategories.isNotEmpty()) {
                val startYArea = h * 0.25f; val endYArea = h * 0.75f
                val baseCatCount = cachedCategories.count { it.id != "launcher_settings_virtual_id" }.coerceAtLeast(1)
                val catLineH = ((endYArea - startYArea) / baseCatCount) * categorySpacingScale()

                cachedCategories.forEachIndexed { idx, cat ->
                    val centerY = startYArea + (idx * catLineH) + (catLineH / 2f) + categoryVisualOffset
                    val relativeDistanceFromCenter = centerY - (h / 2f)
                    val sweepAngleRad = (relativeDistanceFromCenter / ((endYArea - startYArea) / 2f)).coerceIn(-1.2f, 1.2f) * (PI / 2.6f)
                    
                    // Symmetrical anchor based on flank origin (closer to thumb while preserving 3D arc)
                    val targetTextX = if (isOpenedFromLeftFlank) {
                        (20f * density) + (cos(sweepAngleRad).toFloat() * 58f * density)
                    } else {
                        w - (20f * density) - (cos(sweepAngleRad).toFloat() * 58f * density)
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
                            m3Primary = catAccent,
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
                    catTextPaint.typeface = overlayTypeface(isBold = focusFactor > 0.6f)

                    // Color interpolation: Dim starlight (#9EA7B8) to white for unselected categories,
                    // or shared chromatic accent on the focused category across all color engines.
                    val isFocusedCat = (idx == activeCatIndex)
                    val alpha = ((35 + 220 * focusFactor) * decelerationFactor).toInt().coerceIn(0, 255)
                    if (isFocusedCat) {
                        val m3R = Color.red(catAccent); val m3G = Color.green(catAccent); val m3B = Color.blue(catAccent)
                        val rCol = (158 + (m3R - 158) * focusFactor).toInt()
                        val gCol = (167 + (m3G - 167) * focusFactor).toInt()
                        val bCol = (184 + (m3B - 184) * focusFactor).toInt()
                        catTextPaint.color = Color.argb(alpha, rCol, gCol, bCol)
                    } else {
                        val rCol = (158 + (255 - 158) * focusFactor).toInt()
                        val gCol = (167 + (255 - 167) * focusFactor).toInt()
                        val bCol = (184 + (255 - 184) * focusFactor).toInt()
                        catTextPaint.color = Color.argb(alpha, rCol, gCol, bCol)
                    }

                    // Draw smoothly gliding Category Star System Typography
                    canvas.drawText(labelText, targetTextX, centerY + (5f + 1f * focusFactor) * density, catTextPaint)

                    // 3. Glowing Celestial Star Beacon (Fades in smoothly as category enters focus)
                    if (focusFactor > 0.25f) {
                        val measuredLabelW = catTextPaint.measureText(labelText)
                        val beaconAlpha = ((focusFactor - 0.25f) / 0.75f).coerceIn(0f, 1f)
                        textPaint.textAlign = if (isOpenedFromLeftFlank) Paint.Align.LEFT else Paint.Align.RIGHT
                        textPaint.typeface = overlayTypeface(isBold = true)
                        textPaint.textSize = (currentTextSize * 0.75f).coerceAtLeast(8.5f * density)
                        // In Hybrid or M3 mode: Only the focused category's star receives the accent color.
                        // Non-focused stars keep standard 12-theme white.
                        textPaint.color = if (isFocusedCat) catAccent else Color.WHITE
                        textPaint.alpha = (255 * beaconAlpha * decelerationFactor).toInt()
                        val beaconX = if (isOpenedFromLeftFlank) targetTextX + measuredLabelW + 10f * density else targetTextX - measuredLabelW - 10f * density
                        val beaconColor = if (isFocusedCat) catAccent else Color.WHITE
                        val beaconA = (255 * beaconAlpha * decelerationFactor).toInt().coerceIn(0, 255)
                        LightspeedHudVectorIcons.drawCategoryVectorIcon(
                            canvas = canvas,
                            categoryId = cat.id,
                            cx = beaconX,
                            cy = centerY + 1f * density,
                            r = (currentTextSize * 0.36f).coerceAtLeast(4.5f * density),
                            color = beaconColor,
                            alpha = beaconA
                        )
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
        if (deepSpaceRenderer.isWarpLaunching) {
            postInvalidateOnAnimation()
        }
}

