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
            val startYArea = hF * 0.25f
            val endYArea = hF * 0.75f
            val baseCatCount = cachedCategories.count { it.id != "launcher_settings_virtual_id" }.coerceAtLeast(1)
            val catLineH = ((endYArea - startYArea) / baseCatCount) * categorySpacingScale()

            // Continuous 1:1 rawY drives category scroll for smooth native-refresh motion;
            // apply hysteresisPx only to the bottom overscroll reveal detent.
            val stabilizedNormY = ((rawY - verticalStartAnchor) / verticalComfortScope).coerceIn(0f, 1f)
            val hysteresisPx = 5.5f * density
            val effectiveRawY = if (categoryFilteredAnchorY < 0f) {
                categoryFilteredAnchorY = rawY
                rawY
            } else if (abs(rawY - categoryFilteredAnchorY) >= hysteresisPx) {
                val dir = if (rawY > categoryFilteredAnchorY) 1f else -1f
                categoryFilteredAnchorY = rawY - dir * (hysteresisPx * 0.5f)
                categoryFilteredAnchorY
            } else {
                categoryFilteredAnchorY
            }

            val bottomThresholdY = verticalStartAnchor + verticalComfortScope
            val effectiveBottomAnchorY = maxOf(bottomThresholdY, touchDownRawY)
            // Scale swipe-down reveal threshold to available space below the rail so landscape & low pills stay reachable
            val availableBottomSpace = (hF - effectiveBottomAnchorY).coerceAtLeast(1f)
            val revealThresholdPx = minOf(36f * density, availableBottomSpace * 0.48f).coerceAtLeast(10f * density)
            val isInBottomZone = (activeCatIndex >= baseCatCount - 1) || (stabilizedNormY >= 0.96f) || settingsCategoryAppended

            fun fireSettingsRevealHapticOnce() {
                if (!settingsRevealHapticFired) {
                    settingsRevealHapticFired = true
                    triggerCruiseSettingsRevealHaptic()
                }
            }

            fun revealHiddenCentralCommand() {
                if (!settingsCategoryAppended) {
                    settingsCategoryAppended = true
                    val mutableCats = cachedCategories.toMutableList()
                    val hasSettings = mutableCats.any { it.id == "launcher_settings_virtual_id" }
                    if (!hasSettings) {
                        val cmdLabel = com.sbf.lightspeed.system.LightspeedLanguageEngine.resolve(
                            com.sbf.lightspeed.system.LightspeedVocabulary.Key.CENTRAL_COMMAND
                        )
                        mutableCats.add(LightspeedDataBridge.CategoryNode("launcher_settings_virtual_id", "⚙ $cmdLabel"))
                        cachedCategories = mutableCats
                    }
                    fireSettingsRevealHapticOnce()
                }
                val settingsIdx = cachedCategories.indexOfLast { it.id == "launcher_settings_virtual_id" }
                if (settingsIdx >= 0 && !isCategoryVectorLocked && activeCatIndex != settingsIdx) {
                    activeCatIndex = settingsIdx
                    hudScrollOffset = 0f
                    computeHudSlotBounds()
                    scheduleDebouncedWidgetAttach()
                }
            }

            if (isInBottomZone && rawY >= effectiveBottomAnchorY - hysteresisPx) {
                if (overScrollAnchorRawY < 0f) {
                    overScrollAnchorRawY = effectiveBottomAnchorY
                }
                val pullDownPx = (effectiveRawY - effectiveBottomAnchorY).coerceAtLeast(0f)
                if (pullDownPx > overScrollBoundaryAccumulator) {
                    overScrollBoundaryAccumulator = pullDownPx
                } else if (overScrollBoundaryAccumulator - pullDownPx > hysteresisPx) {
                    overScrollBoundaryAccumulator = pullDownPx
                }

                if (!isCategoryVectorLocked && overScrollBoundaryAccumulator >= revealThresholdPx) {
                    if (!settingsCategoryAppended) {
                        revealHiddenCentralCommand()
                    } else {
                        // User left the bottom zone and swiped down again in the same touch session: re-fire strong haptic
                        fireSettingsRevealHapticOnce()
                    }
                }
            } else if (stabilizedNormY < 0.92f || rawY < effectiveBottomAnchorY - hysteresisPx) {
                overScrollBoundaryAccumulator = 0f
                overScrollAnchorRawY = -1f
                if (activeCatIndex < baseCatCount) {
                    settingsRevealHapticFired = false
                }
            }

            if (settingsCategoryAppended && overScrollBoundaryAccumulator <= revealThresholdPx * 0.25f && activeCatIndex < baseCatCount) {
                settingsRevealHapticFired = false
            }

            val shapedProgress = shapeCategoryProgress(stabilizedNormY)
            val baseSlotSpan = (baseCatCount - 1).coerceAtLeast(0).toFloat()
            val extraSettingsSlotOffset = if (settingsCategoryAppended) {
                if (overScrollBoundaryAccumulator >= revealThresholdPx) {
                    1f
                } else {
                    val glideStart = revealThresholdPx * 0.35f
                    ((overScrollBoundaryAccumulator - glideStart) / (revealThresholdPx - glideStart).coerceAtLeast(1f)).coerceIn(0f, 1f)
                }
            } else if (overScrollBoundaryAccumulator > 0f) {
                // Subtle damped rubber-band tension before Central Command reveals on deliberate swipe-down
                ((overScrollBoundaryAccumulator / revealThresholdPx).coerceIn(0f, 1f)) * 0.24f
            } else {
                0f
            }

            val continuousSlotIndex = (shapedProgress * baseSlotSpan) + extraSettingsSlotOffset
            categoryVisualOffset = (hF / 2f) - (startYArea + (catLineH / 2f) + (continuousSlotIndex * catLineH))

            // Vertical hysteresis detent clamped to catLineH so small landscape rows never trap Central Command
            val isBottomOrSettingsActive = activeCatIndex >= baseCatCount - 1
            val rawDetentPx = (if (isBottomOrSettingsActive) 16f else 14f) * density
            val detentPx = minOf(rawDetentPx, catLineH * 0.42f)
            var closestIndex = activeCatIndex.coerceIn(0, cachedCategories.lastIndex)
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
                hudScrollOffset = 0f
                val isLandingOnSettings = cachedCategories.getOrNull(closestIndex)?.id == "launcher_settings_virtual_id"
                if (!(isLandingOnSettings && settingsRevealHapticFired)) {
                    triggerCruiseCategoryHaptic()
                }
                computeHudSlotBounds()
                scheduleDebouncedWidgetAttach()
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
                val startYArea = hF * 0.25f
                val endYArea = hF * 0.75f
                val baseCatCount = cachedCategories.count { it.id != "launcher_settings_virtual_id" }.coerceAtLeast(1)
                val catLineH = ((endYArea - startYArea) / baseCatCount) * categorySpacingScale()
                categoryFilteredAnchorY = rawY

                val shaped = shapeCategoryProgress(normY)
                categoryVisualOffset = (hF / 2f) - (startYArea + (catLineH / 2f) + (shaped * ((baseCatCount - 1).coerceAtLeast(0) * catLineH)))
                var closestIndex = 0
                var minDistance = Float.MAX_VALUE
                for (index in cachedCategories.indices) {
                    val d = abs((startYArea + (index * catLineH) + (catLineH / 2f) + categoryVisualOffset) - (hF / 2f))
                    if (d < minDistance) {
                        minDistance = d
                        closestIndex = index
                    }
                }
                if (activeCatIndex != closestIndex) {
                    activeCatIndex = closestIndex
                    computeHudSlotBounds()
                    scheduleDebouncedWidgetAttach()
                }
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

        val isFarSplit = isFarHalfSplitMode()
        val halfW = wF * 0.5f
        val baseMargin = if (isFarSplit) halfW * 0.04f else wF * 0.04f
        val gridStartX = if (isFarSplit) {
            // Nearest half to finger / triggering flank:
            if (isOpenedFromLeftFlank) baseMargin else halfW + baseMargin
        } else {
            baseMargin
        }
        val gridWidth = if (isFarSplit) halfW * 0.92f else wF * 0.92f

        val columns = querySystemColumnPreference(resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE)
        val cellW = gridWidth / columns
        val cellH = cellW * 1.1f
        val firstColCenterX = gridStartX + (cellW * 0.5f)
        val lastColCenterX = (gridStartX + gridWidth) - (cellW * 0.5f)

        virtualCursorX = if (isOpenedFromLeftFlank) {
            firstColCenterX + (cursorProgress * (lastColCenterX - firstColCenterX))
        } else {
            lastColCenterX - (cursorProgress * (lastColCenterX - firstColCenterX))
        }

        val catId = cachedCategories.getOrNull(activeCatIndex)?.id ?: "comm"
        val gTop = getCategoryGridTop(catId)
        val gBot = if (shouldShowCategoryCruiseRotatingTip()) {
            getCategoryCruiseTipReserveTopY().coerceAtMost(hF * 0.98f)
        } else {
            hF * 0.98f
        }
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
    val displayMetrics = resources.displayMetrics
    val screenW = displayMetrics.widthPixels.toFloat()
    val screenH = displayMetrics.heightPixels.toFloat()
    val wF = if (width > 200) width.toFloat() else screenW
    val hF = if (height > 200) height.toFloat() else screenH
    if (wF <= 0f) return
    val isFarSplit = isFarHalfSplitMode()
    val halfW = wF * 0.5f
    val baseMargin = if (isFarSplit) halfW * 0.04f else wF * 0.04f
    val gridStartX = if (isFarSplit) {
        // Nearest half to finger / triggering flank:
        if (isOpenedFromLeftFlank) baseMargin else halfW + baseMargin
    } else {
        baseMargin
    }
    val gridWidth = if (isFarSplit) halfW * 0.92f else wF * 0.92f

    val columns = querySystemColumnPreference(resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE)
    val cellW = gridWidth / columns
    val cellH = cellW * 1.1f
    val catId = cachedCategories.getOrNull(activeCatIndex)?.id ?: "comm"
    val gTop = getCategoryGridTop(catId)
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
                                gTop + (row * cellH),
                                gridStartX + ((col + sW) * cellW),
                                gTop + ((row + sH) * cellH)
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
