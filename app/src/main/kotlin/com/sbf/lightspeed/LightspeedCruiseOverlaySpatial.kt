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
    val verticalComfortScope = hF * 0.40f
    val verticalStartAnchor = hF * 0.30f
    val normalizedVerticalProgress = ((rawY - verticalStartAnchor) / verticalComfortScope).coerceIn(0f, 1f)

    if (currentLayer == CruiseLayer.CATEGORY) {
        activeItem = null
        viewportScrollOffset = 0f
        val horizontalPull = if (isOpenedFromLeftFlank) (rawX - touchDownRawX) else (touchDownRawX - rawX)
        if (categoryScrubbingEngaged && horizontalPull > (28f * density)) {
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
            } else if (normalizedVerticalProgress < 1.0f) {
                overScrollBoundaryAccumulator = (overScrollBoundaryAccumulator - 12f).coerceAtLeast(0f)
            }

            val resistanceFactor = if (overScrollBoundaryAccumulator > 0f) (1.0f / (1.0f + (overScrollBoundaryAccumulator * 0.008f))) else 1.0f
            val shapedProgress = shapeCategoryProgress(normalizedVerticalProgress)
            val adjustedProgress = if (isAtBottom && isDraggingDown) {
                shapedProgress + (overScrollBoundaryAccumulator * 0.0005f * resistanceFactor)
            } else {
                shapedProgress
            }

            categoryVisualOffset = (hF / 2f) - (startYArea + (catLineH / 2f) + (adjustedProgress * ((cachedCategories.size - 1) * catLineH)))

            var closestIndex = activeCatIndex
            var minDistance = Float.MAX_VALUE
            for (index in cachedCategories.indices) {
                val d = abs((startYArea + (index * catLineH) + (catLineH / 2f) + categoryVisualOffset) - (hF / 2f))
                if (d < minDistance) {
                    minDistance = d
                    closestIndex = index
                }
            }
            if (closestIndex != activeCatIndex) {
                activeCatIndex = closestIndex
                triggerCruiseCategoryHaptic()
            }
        }
        invalidate()
    } else {
        val horizontalPull = if (isOpenedFromLeftFlank) (rawX - touchDownRawX) else (touchDownRawX - rawX)
        val nextLayerState = when {
            horizontalPull <= (6f * density) -> CruiseLayer.CATEGORY
            (if (isOpenedFromLeftFlank) localX else (wF - localX)) <= (wF * 0.42f) -> CruiseLayer.GRID
            else -> CruiseLayer.STICKY_PIN
        }

        if (currentLayer == CruiseLayer.GRID && nextLayerState == CruiseLayer.CATEGORY) {
            currentLayer = CruiseLayer.CATEGORY
            triggerCruiseLayerJumpHaptic()
            touchDownRawX = rawX
            touchDownRawY = rawY
            lastTouchRawY = rawY
            placedAppsList.clear()
            cachedApps = emptyList()
            lastLoadedCategoryId = null
            if (cachedCategories.isNotEmpty()) {
                val startYArea = hF * 0.22f
                val endYArea = hF * 0.78f
                val catLineH = ((endYArea - startYArea) / cachedCategories.size) * categorySpacingScale()

                val maxScroll = totalGridContentHeight - (hF * 0.79f)
                val ratio = if (maxScroll > 0f) (viewportScrollOffset / maxScroll).coerceIn(0f, 1f) else normalizedVerticalProgress
                categoryVisualOffset = (hF / 2f) - (startYArea + (catLineH / 2f) + (ratio * ((cachedCategories.size - 1) * catLineH)))
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
            placedAppsList.clear()
            cachedApps = emptyList()
            lastLoadedCategoryId = null
            invalidate()
            return
        }

        val pullDistance = if (isOpenedFromLeftFlank) localX.coerceAtLeast(0f) else (wF - localX).coerceAtLeast(0f)
        val cursorProgress = ((pullDistance - (10f * density)) / (wF * 0.28f)).coerceIn(0f, 1f)
        virtualCursorX = if (isOpenedFromLeftFlank) ((wF * 0.06f) + (cursorProgress * (wF * 0.88f))) else ((wF * 0.94f) - (cursorProgress * (wF * 0.88f)))

        val gTop = hF * 0.16f
        val gBot = hF * 0.94f
        val gScope = gBot - gTop
        virtualCursorY = gTop + (normalizedVerticalProgress * gScope)
        if (totalGridContentHeight <= gScope) {
            viewportScrollOffset = 0f
        } else {
            viewportScrollOffset = normalizedVerticalProgress * (totalGridContentHeight - gScope)
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
                                (wF * 0.04f) + (col * cellW),
                                (hF * 0.16f) + (row * cellH),
                                (wF * 0.04f) + ((col + sW) * cellW),
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
