package com.sbf.lightspeed

import android.content.res.Configuration
import android.graphics.RectF
import kotlin.math.abs
import kotlin.math.hypot

internal fun LightspeedCruiseOverlay.evaluateSpatialMetrics(rawX: Float, rawY: Float, localX: Float, localY: Float) {
    val screenW = resources.displayMetrics.widthPixels.toFloat()
    val screenH = resources.displayMetrics.heightPixels.toFloat()
    val wF = if (width > screenW * 0.8f) width.toFloat() else screenW
    val hF = if (height > screenH * 0.8f) height.toFloat() else screenH
    if (wF <= 0f || hF <= 0f) return
    val density = resources.displayMetrics.density
    depthPercentage = if (isOpenedFromLeftFlank) (rawX / wF).coerceIn(0f, 1f) else ((wF - rawX) / wF).coerceIn(0f, 1f)

    // Vertical Comfort Reach Anchor & Scope
    val verticalComfortScope = hF * 0.45f
    val verticalStartAnchor = hF * 0.28f
    val normY = ((rawY - verticalStartAnchor) / verticalComfortScope).coerceIn(0f, 1f)

    val flankDistance = if (isOpenedFromLeftFlank) rawX else (wF - rawX)
    val entryThreshold = 28f * density
    val exitThreshold = 14f * density

    if (currentLayer == CruiseLayer.CATEGORY) {
        activeItem = null
        viewportScrollOffset = 0f
        val horizontalPull = flankDistance - (if (isOpenedFromLeftFlank) touchDownRawX else (wF - touchDownRawX)).coerceAtLeast(0f)
        
        // Inward direction lock right before crossing into GRID
        if (horizontalPull > (18f * density)) {
            isCategoryVectorLocked = true
        }
        if (categoryScrubbingEngaged && (horizontalPull > entryThreshold || flankDistance > (36f * density))) {
            currentLayer = CruiseLayer.GRID
            triggerCruiseLayerJumpHaptic()
            viewportScrollOffset = 0f
            loadActiveCategoryGrid()
            invalidate()
            return
        }
        if (cachedCategories.isNotEmpty()) {
            val startYArea = hF * 0.22f
            val endYArea = hF * 0.78f
            val catLineH = ((endYArea - startYArea) / cachedCategories.size) * categorySpacingScale()

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
                        mutableCats.add(LightspeedDataBridge.CategoryNode("launcher_settings_virtual_id", "⚙ Central Command"))
                        cachedCategories = mutableCats
                    }
                }
            } else if (normY < 1.0f) {
                overScrollBoundaryAccumulator = (overScrollBoundaryAccumulator - 12f).coerceAtLeast(0f)
            }

            val resistanceFactor = if (overScrollBoundaryAccumulator > 0f) (1.0f / (1.0f + (overScrollBoundaryAccumulator * 0.008f))) else 1.0f
            val shapedProgress = shapeCategoryProgress(normY)
            val adjustedProgress = if (isAtBottom && isDraggingDown) {
                shapedProgress + (overScrollBoundaryAccumulator * 0.0005f * resistanceFactor)
            } else {
                shapedProgress
            }

            categoryVisualOffset = (hF / 2f) - (startYArea + (catLineH / 2f) + (adjustedProgress * ((cachedCategories.size - 1) * catLineH)))

            // Vertical 12.dp hysteresis detent around the active category slot
            val detentPx = 12f * density
            var closestIndex = activeCatIndex
            var minDistance = Float.MAX_VALUE
            for (index in cachedCategories.indices) {
                val rawD = abs((startYArea + (index * catLineH) + (catLineH / 2f) + categoryVisualOffset) - (hF / 2f))
                val effectiveDistance = if (index == activeCatIndex) (rawD - detentPx).coerceAtLeast(0f) else rawD
                if (effectiveDistance < minDistance) {
                    minDistance = effectiveDistance
                    closestIndex = index
                }
            }
            if (!isCategoryVectorLocked && closestIndex != activeCatIndex) {
                activeCatIndex = closestIndex
                triggerCruiseCategoryHaptic()
            }
        }
        invalidate()
    } else {
        val nextLayerState = when {
            flankDistance <= exitThreshold -> CruiseLayer.CATEGORY
            flankDistance <= (wF * 0.42f) -> CruiseLayer.GRID
            else -> CruiseLayer.STICKY_PIN
        }

        if (currentLayer == CruiseLayer.GRID && nextLayerState == CruiseLayer.CATEGORY) {
            currentLayer = CruiseLayer.CATEGORY
            triggerCruiseLayerJumpHaptic()
            touchDownRawX = rawX
            touchDownRawY = rawY
            lastTouchRawY = rawY
            gridDeadZoneLatched = false
            isCategoryVectorLocked = false
            placedAppsList.clear()
            cachedApps = emptyList()
            lastLoadedCategoryId = null
            if (cachedCategories.isNotEmpty()) {
                val startYArea = hF * 0.22f
                val endYArea = hF * 0.78f
                val catLineH = ((endYArea - startYArea) / cachedCategories.size) * categorySpacingScale()

                val shaped = shapeCategoryProgress(normY)
                categoryVisualOffset = (hF / 2f) - (startYArea + (catLineH / 2f) + (shaped * ((cachedCategories.size - 1) * catLineH)))
                var closestIndex = 0
                var minDistance = Float.MAX_VALUE
                for (index in cachedCategories.indices) {
                    val d = abs((startYArea + (index * catLineH) + (catLineH / 2f) + categoryVisualOffset) - (hF / 2f))
                    if (d < minDistance) {
                        minDistance = d
                        closestIndex = index
                    }
                }
                activeCatIndex = closestIndex
            }
            invalidate()
            return
        }

        val layerChanged = (currentLayer != nextLayerState)
        currentLayer = nextLayerState
        if (layerChanged) {
            triggerCruiseLayerJumpHaptic()
        }
        if (currentLayer == CruiseLayer.CATEGORY) {
            touchDownRawX = rawX
            touchDownRawY = rawY
            lastTouchRawY = rawY
            gridDeadZoneLatched = false
            isCategoryVectorLocked = false
            placedAppsList.clear()
            cachedApps = emptyList()
            lastLoadedCategoryId = null
            invalidate()
            return
        }

        val cursorProgress = ((flankDistance - exitThreshold) / (wF * 0.28f)).coerceIn(0f, 1f)

        val columns = querySystemColumnPreference(resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE)
        val cellW = (wF * 0.92f) / columns
        val cellH = cellW * 1.1f
        val firstColCenterX = (wF * 0.04f) + (cellW * 0.5f)
        val lastColCenterX = (wF * 0.96f) - (cellW * 0.5f)

        virtualCursorX = if (isOpenedFromLeftFlank) {
            firstColCenterX + (cursorProgress * (lastColCenterX - firstColCenterX))
        } else {
            lastColCenterX - (cursorProgress * (lastColCenterX - firstColCenterX))
        }

        val gTop = hF * 0.16f
        val gBot = hF * 0.94f
        val gScope = gBot - gTop
        val rowCount = if (cellH > 0f) (totalGridContentHeight / cellH).toInt().coerceAtLeast(1) else 1

        if (totalGridContentHeight <= gScope || rowCount <= 1) {
            val actualContentHeight = rowCount * cellH
            val contentTop = gTop
            val contentBottom = contentTop + actualContentHeight
            val cursorProgressY = ((rawY - contentTop) / actualContentHeight).coerceIn(0f, 1f)
            virtualCursorY = contentTop + (cursorProgressY * actualContentHeight)
            viewportScrollOffset = 0f
        } else {
            val maxScroll = totalGridContentHeight - gScope
            viewportScrollOffset = normY * maxScroll
            val cursorSpan = (gScope - cellH).coerceAtLeast(0f)
            virtualCursorY = gTop + (cellH * 0.5f) + (normY * cursorSpan)
        }

        var closestMatchItem: LightspeedDataBridge.LaunchTarget? = null
        var minD = Float.MAX_VALUE
        for (item in placedAppsList) {
            val d = hypot(
                (virtualCursorX - item.bounds.centerX()).toDouble(),
                ((virtualCursorY + viewportScrollOffset) - item.bounds.centerY()).toDouble()
            ).toFloat()
            if (d < minD) {
                minD = d
                closestMatchItem = item.app
            }
        }
        val prevActive = activeItem
        activeItem = if (closestMatchItem != null && placedAppsList.isNotEmpty() && currentLayer != CruiseLayer.STICKY_PIN) closestMatchItem else null
        if (activeItem != null && activeItem != prevActive) {
            triggerCruiseAppFocusHaptic()
        }
    }
    invalidate()
}

internal fun LightspeedCruiseOverlay.buildPackedGridLayout() {
    val wF = width.toFloat()
    val hF = height.toFloat()
    if (wF <= 0f) return
    val baseFlankMargin = wF * 0.04f
    val gridStartX = baseFlankMargin

    val columns = querySystemColumnPreference(resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE)
    val cellW = (wF * 0.92f) / columns
    val cellH = cellW * 1.1f
    placedAppsList.clear()
    val occupied = HashSet<Pair<Int, Int>>()
    for (app in cachedApps.distinctBy { "${it.packageName}_${it.activityName}_${it.instanceId}" }) {
        var sW = 1
        var sH = 1
        if (app.isWidget) {
            sW = if (app.spanX > 0) app.spanX.coerceIn(1, columns) else columns / 2
            sH = if (app.spanY > 0) app.spanY.coerceIn(1, 12) else 2
        }
        var row = 0
        var found = false
        while (!found) {
            for (col in 0..columns - sW) {
                var clear = true
                for (dr in 0 until sH) {
                    for (dc in 0 until sW) {
                        if (occupied.contains(Pair(row + dr, col + dc))) {
                            clear = false
                            break
                        }
                    }
                    if (!clear) break
                }
                if (clear) {
                    placedAppsList.add(
                        PlacedItem(
                            app,
                            RectF(
                                gridStartX + (col * cellW),
                                (hF * 0.16f) + (row * cellH),
                                gridStartX + ((col + sW) * cellW),
                                (hF * 0.16f) + ((row + sH) * cellH)
                            )
                        )
                    )
                    for (dr in 0 until sH) {
                        for (dc in 0 until sW) {
                            occupied.add(Pair(row + dr, col + dc))
                        }
                    }
                    found = true
                    break
                }
            }
            if (!found) row++
        }
    }
    totalGridContentHeight = if (occupied.isEmpty()) 0f else ((occupied.maxOf { it.first }) + 1) * cellH
}

internal fun LightspeedCruiseOverlay.querySystemColumnPreference(isLandscape: Boolean): Int {
    val prefs = prefs()
    return try {
        val v = prefs.all[if (isLandscape) "pref_numcolsland" else "pref_numcolspor"]
        if (v is Int) v else v?.toString()?.toInt() ?: 4
    } catch (_: Exception) {
        4
    }
}
