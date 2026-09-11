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


internal fun LightspeedCruiseOverlay.handleDraw(canvas: Canvas, superCall: () -> Unit) {
        if (activeGearSetIndex >= totalGearSetsCount) { activeGearSetIndex = 0 }
        // No per-frame prefs I/O — use render cache updated by prefChangeListener

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

        superCall()
        if (currentLayer == CruiseLayer.COCKPIT_HANGAR) {
            val setsList = getGearSetsOrder(isOpenedFromLeftFlank)
            deepSpaceRenderer.drawDeepSpace(
                canvas, context, width.toFloat(), height.toFloat(), resources.displayMetrics.density,
                m3Primary, m3Secondary, isOpenedFromLeftFlank, activeGearSetIndex, activeHangarRing,
                isHangarEjectArmed, gearRingRotations, hangarBayScrollOffset, setsList,
                ::resolveCleanAppLabel, ::getAppsForActiveGear, ::getFlankLaunchBehavior
            )
            return
        }
        val w = width.toFloat(); val h = height.toFloat()
        if (currentLayer == CruiseLayer.HIDDEN) {
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
                glowStyle = glowStyle
            )
            return
        }

        if (currentLayer == CruiseLayer.NEUTRAL) {
            // Keep background neutral during intent decision gate (eliminates category ghost frames before Gears)
            return
        }

        if (currentLayer == CruiseLayer.FAVORITES_GEARS) {
            val density = resources.displayMetrics.density
            val cx = width / 2f
            val cy = height / 2f
            
            // 1. Deep Space Astrogation Backdrop with subtle radial vignette
            val spaceGrad = android.graphics.RadialGradient(
                cx, cy, (width.toFloat().coerceAtLeast(height.toFloat()) * 0.75f),
                intArrayOf(Color.argb(210, 10, 14, 22), Color.argb(245, 4, 6, 10)),
                floatArrayOf(0.0f, 1.0f),
                android.graphics.Shader.TileMode.CLAMP
            )
            elementPaint.style = Paint.Style.FILL
            elementPaint.shader = spaceGrad
            canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), elementPaint)
            elementPaint.shader = null

            // 2. Flight Telemetry Grid & Attitude Axis Crosshairs
            elementPaint.style = Paint.Style.STROKE
            elementPaint.strokeWidth = 1f * density
            elementPaint.color = Color.argb(24, 200, 220, 255)
            canvas.drawLine(cx - (380f * density / 2.6f), cy, cx + (380f * density / 2.6f), cy, elementPaint)
            canvas.drawLine(cx, cy - (380f * density / 2.6f), cx, cy + (380f * density / 2.6f), elementPaint)
            
            // Scaled Radii for Outer, Inner, and Core Hub
            val rad0 = 310f * (density / 2.6f).coerceAtLeast(0.9f)
            val rad1 = 190f * (density / 2.6f).coerceAtLeast(0.9f)
            val radHub = 64f * (density / 2.6f).coerceAtLeast(0.9f)
            
            // Draw Outer Orbital Gimbal Ring (Ring 0, 16 Magnetic Cogs)
            drawSpaceshipGimbalRing(
                canvas = canvas,
                cx = cx,
                cy = cy,
                trackRadius = rad0,
                trackWidth = 48f * density / 2.6f,
                teethCount = 16,
                toothDepth = 16f * density / 2.6f,
                rotationDeg = gearRingRotations[0],
                isActive = (activeGearRing == 0),
                m3Primary = m3Primary
            )

            // Draw Inner Orbital Gimbal Ring (Ring 1, 12 Magnetic Cogs)
            drawSpaceshipGimbalRing(
                canvas = canvas,
                cx = cx,
                cy = cy,
                trackRadius = rad1,
                trackWidth = 42f * density / 2.6f,
                teethCount = 12,
                toothDepth = 14f * density / 2.6f,
                rotationDeg = gearRingRotations[1],
                isActive = (activeGearRing == 1),
                m3Primary = m3Primary
            )

            // Draw Central Holographic Warp Core / Cockpit Command Node (Ring 2)
            drawHolographicReactorCore(
                canvas = canvas,
                cx = cx,
                cy = cy,
                coreRadius = radHub,
                isActive = (activeGearRing == 2),
                m3Primary = m3Primary,
                density = density
            )

            // --- ORBITAL APP FLIGHT PODS & ICONS PASS ---
            val pm = context.packageManager
            val sizeRaw = (42f * density).toInt()

            var activeHighlightedCX = 0f
            var activeHighlightedCY = 0f
            var activeHighlightedBracketSize = 0f
            var activeHighlightedLabel: String? = null

            for (r in 0..1) {
                val radius = if (r == 0) rad0 else rad1
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
                    
                    // Dynamic Focus Interpolation: Target angle is 180 deg (straight left, pointing to center)
                    val normalizedDeg = if (angleDeg < 0) angleDeg + 360f else angleDeg
                    val isHighlighted = isRingFocused && abs(normalizedDeg - 180f) < (180f / count)
                    val currentScale = if (isHighlighted) 1.28f else 1.0f
                    val currentSize = (sizeRaw * currentScale).toInt()
                    
                    val itemToken = ringApps[i]
                    val appLabel = resolveCleanAppLabel(itemToken)

                    if (isHighlighted) {
                        activeHighlightedCX = iconCX
                        activeHighlightedCY = iconCY
                        activeHighlightedBracketSize = currentSize + 22f * density
                        activeHighlightedLabel = appLabel
                    }

                    // 1. Orbital Flight Pod Socket Behind Icon
                    highlightPaint.style = Paint.Style.FILL
                    highlightPaint.color = if (isHighlighted) Color.argb(200, 22, 28, 44) else Color.argb(90, 16, 20, 30)
                    canvas.drawCircle(iconCX, iconCY, (currentSize / 2f) + (6f * density), highlightPaint)

                    highlightPaint.style = Paint.Style.STROKE
                    highlightPaint.strokeWidth = if (isHighlighted) 2.4f * density else 1.2f * density
                    highlightPaint.color = if (isHighlighted) m3Primary else Color.argb(55, 200, 220, 255)
                    canvas.drawCircle(iconCX, iconCY, (currentSize / 2f) + (6f * density), highlightPaint)

                    // 2. Draw Icon Drawable or Action Glyph
                    try {
                        val iconDrawable = com.sbf.lightspeed.system.LightspeedIconManager.getIconDrawable(context, itemToken)
                        if (iconDrawable != null) {
                            iconDrawable.alpha = if (isRingFocused) (if (isHighlighted) 255 else 210) else 110
                            iconDrawable.setBounds(
                                (iconCX - currentSize / 2).toInt(),
                                (iconCY - currentSize / 2).toInt(),
                                (iconCX + currentSize / 2).toInt(),
                                (iconCY + currentSize / 2).toInt()
                            )
                            iconDrawable.draw(canvas)
                        } else {
                            elementPaint.style = Paint.Style.FILL
                            elementPaint.color = if (isHighlighted) Color.WHITE else Color.GRAY
                            canvas.drawCircle(iconCX, iconCY, currentSize / 3f, elementPaint)
                        }
                    } catch (e: Exception) {
                        elementPaint.style = Paint.Style.FILL
                        elementPaint.color = if (isHighlighted) Color.WHITE else Color.GRAY
                        canvas.drawCircle(iconCX, iconCY, currentSize / 3f, elementPaint)
                    }
                }
            }

            // 3. Draw Flight Lock Targeting Reticle & Holographic Badge AFTER all rings & pods are drawn (Top of Z-Stack)
            if (activeHighlightedLabel != null) {
                drawFlightLockReticle(
                    canvas = canvas,
                    targetCX = activeHighlightedCX,
                    targetCY = activeHighlightedCY,
                    bracketSize = activeHighlightedBracketSize,
                    m3Primary = m3Primary,
                    m3Secondary = m3Secondary,
                    appName = activeHighlightedLabel,
                    density = density,
                    reticleStyle = renderCacheReticleStyle
                )
            }
            
            // --- TOP FLIGHT TELEMETRY HUD HEADER (PROFILE HUD) ---
            val topBadgeY = 54f * density
            val profileName = when(activeGearSetIndex) {
                0 -> getGearSetNameByIndex(0)
                1 -> getGearSetNameByIndex(1)
                2 -> getGearSetNameByIndex(2)
                else -> getGearSetNameByIndex(3)
            }
            
            val headerText = "◈ ASTROGATION // PROFILE $activeGearSetIndex: $profileName ◈"
            textPaint.textSize = 12f * density
            textPaint.typeface = android.graphics.Typeface.DEFAULT_BOLD
            textPaint.textAlign = Paint.Align.CENTER
            val headerWidth = textPaint.measureText(headerText)

            highlightPaint.style = Paint.Style.FILL
            highlightPaint.color = Color.argb(175, 12, 16, 28)
            val headerRect = RectF(cx - headerWidth / 2f - 16f * density, topBadgeY - 15f * density, cx + headerWidth / 2f + 16f * density, topBadgeY + 15f * density)
            canvas.drawRoundRect(headerRect, 10f * density, 10f * density, highlightPaint)

            highlightPaint.style = Paint.Style.STROKE
            highlightPaint.strokeWidth = 1.2f * density
            highlightPaint.color = Color.argb(80, Color.red(m3Primary), Color.green(m3Primary), Color.blue(m3Primary))
            canvas.drawRoundRect(headerRect, 10f * density, 10f * density, highlightPaint)

            textPaint.color = Color.WHITE
            canvas.drawText(headerText, cx, topBadgeY + 4f * density, textPaint)

            // Subtitle Guidance Hint (cleanly separated with zero gear collision)
            textPaint.textSize = 8.5f * density
            textPaint.typeface = android.graphics.Typeface.DEFAULT
            textPaint.color = Color.argb(160, 180, 210, 245)
            canvas.drawText(if (isOpenedFromLeftFlank) "PULL RIGHT >> SWITCH PROFILE  •  SLIDE LEFT << CANCEL" else "PULL LEFT >> SWITCH PROFILE  •  SLIDE RIGHT << CANCEL", cx, topBadgeY + 28f * density, textPaint)
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
            val headerRect = RectF(w / 2f - hudWidth / 2f - 14f * density, topBadgeY - 13f * density, w / 2f + hudWidth / 2f + 14f * density, topBadgeY + 13f * density)
            canvas.drawRoundRect(headerRect, 9f * density, 9f * density, highlightPaint)

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
                val startYArea = h * 0.22f; val endYArea = h * 0.78f
                val catLineH = (endYArea - startYArea) / cachedCategories.size

                cachedCategories.forEachIndexed { idx, cat ->
                    val centerY = startYArea + (idx * catLineH) + (catLineH / 2f) + categoryVisualOffset
                    val relativeDistanceFromCenter = centerY - (h / 2f)
                    val sweepAngleRad = (relativeDistanceFromCenter / ((endYArea - startYArea) / 2f)).coerceIn(-1.2f, 1.2f) * (PI / 2.6f)
                    
                    // Symmetrical anchor based on flank origin
                    val targetTextX = if (isOpenedFromLeftFlank) {
                        (28f * density) + (cos(sweepAngleRad).toFloat() * 160f * density)
                    } else {
                        w - (28f * density) - (cos(sweepAngleRad).toFloat() * 160f * density)
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
            val density = resources.displayMetrics.density
            canvas.drawColor(Color.argb(195, 4, 6, 12))
            drawCosmicStarfield(canvas, w, h, density, 0.7f)

            textPaint.textAlign = Paint.Align.CENTER
            canvas.save(); canvas.clipRect(0f, 0f, w, h)
            canvas.translate(0f, -(if (totalGridContentHeight <= (h * 0.79f)) 0f else viewportScrollOffset))
            for (item in placedAppsList) {
                val b = item.bounds; val isSel = activeItem == item.app
                if (isSel) {
                    // Starlight Pod Active Docking Ring
                    highlightPaint.style = Paint.Style.FILL
                    highlightPaint.color = Color.argb(160, 24, 32, 52)
                    canvas.drawRoundRect(b.left + 4f, b.top + 4f, b.right - 4f, b.bottom - 4f, 18f, 18f, highlightPaint)
                    
                    highlightPaint.style = Paint.Style.STROKE
                    highlightPaint.strokeWidth = 2.2f * density
                    highlightPaint.color = m3Primary
                    canvas.drawRoundRect(b.left + 4f, b.top + 4f, b.right - 4f, b.bottom - 4f, 18f, 18f, highlightPaint)
                    
                    textPaint.color = Color.WHITE
                    textPaint.typeface = android.graphics.Typeface.DEFAULT_BOLD
                } else {
                    elementPaint.style = Paint.Style.FILL
                    elementPaint.color = Color.argb(if (item.app.isWidget) 30 else 18, 200, 220, 255)
                    canvas.drawRoundRect(b.left + 4f, b.top + 4f, b.right - 4f, b.bottom - 4f, 16f, 16f, elementPaint)
                    
                    elementPaint.style = Paint.Style.STROKE
                    elementPaint.strokeWidth = 1f * density
                    elementPaint.color = Color.argb(35, 200, 220, 255)
                    canvas.drawRoundRect(b.left + 4f, b.top + 4f, b.right - 4f, b.bottom - 4f, 16f, 16f, elementPaint)
                    
                    textPaint.color = Color.parseColor("#E0E5F0")
                    textPaint.typeface = android.graphics.Typeface.DEFAULT
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

        drawHyperdriveWarpSurge(canvas, m3Primary, resources.displayMetrics.density)
}
