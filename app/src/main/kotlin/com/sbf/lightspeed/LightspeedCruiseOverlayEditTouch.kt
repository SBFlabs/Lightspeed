package com.sbf.lightspeed

import android.content.Intent
import android.graphics.RectF
import android.provider.Settings
import com.sbf.lightspeed.system.LightspeedPreferences
import com.sbf.lightspeed.system.LightspeedUsageStats
import com.sbf.lightspeed.system.defaultPrefs

internal fun LightspeedCruiseOverlay.handleEditModeTap(x: Float, y: Float) {
    if (!isEditModeOpen) return

    val dp = resources.displayMetrics.density

    if (isEditModePromptOpen) {
        val promptBounds = getUnsavedPromptBounds()
        if (promptBounds.contains(x, y)) {
            val btnRowTop = promptBounds.bottom - 40f * dp
            if (y >= btnRowTop) {
                val btnW = promptBounds.width() / 3f
                val relX = x - promptBounds.left
                when {
                    relX < btnW -> {
                        // "Save"
                        triggerHardwareHaptic(20, 80)
                        saveAndApplyEditModeOrder()
                        closeEditMode()
                        invalidate()
                    }
                    relX < btnW * 2f -> {
                        // "Discard"
                        triggerHardwareHaptic(20, 80)
                        closeEditMode()
                        invalidate()
                    }
                    else -> {
                        // "Keep editing"
                        triggerHardwareHaptic(20, 80)
                        isEditModePromptOpen = false
                        invalidate()
                    }
                }
            }
        }
        return
    }

    val bounds = getEditModePanelBounds()
    if (!bounds.contains(x, y)) {
        if (editModeCategories != editModeSavedCategories) {
            triggerHardwareHaptic(20, 80)
            isEditModePromptOpen = true
            invalidate()
        } else {
            closeEditMode()
            triggerHardwareHaptic(20, 80)
            invalidate()
        }
        return
    }

    val numCategories = editModeCategories.size
    val totalRows = numCategories + 2
    val actualRowH = bounds.height() / totalRows
    val tabRowBottom = bounds.top + actualRowH
    val btnRowTop = bounds.bottom - actualRowH

    if (y < tabRowBottom) {
        val midX = bounds.left + (bounds.width() / 2f)
        val selectedTab = if (x < midX) EditModeTab.PAGE else EditModeTab.HUD
        if (editModeTab != selectedTab) {
            triggerHardwareHaptic(20, 80)
            editModeTab = selectedTab
            invalidate()
        }
        return
    }

    if (y >= btnRowTop) {
        val btnW = bounds.width() / 4f
        val relX = x - bounds.left
        if (editModeTab == EditModeTab.PAGE) {
            when {
                relX < btnW -> {
                    // "A-Z"
                    triggerHardwareHaptic(25, 120)
                    editModeCategories.sortBy { it.label.lowercase() }
                    invalidate()
                }
                relX < btnW * 2f -> {
                    // "Most used"
                    handleMostUsedSort()
                }
                relX < btnW * 3f -> {
                    // "Default"
                    triggerHardwareHaptic(25, 120)
                    val defaultMap = LightspeedDataBridge.DRAWER_CATEGORIES.associateBy { it.id }
                    val defaultList = LightspeedDataBridge.DRAWER_CATEGORIES.mapNotNull { defaultMap[it.id] }
                    editModeCategories.clear()
                    editModeCategories.addAll(defaultList)
                    invalidate()
                }
                else -> {
                    // "Save"
                    triggerHardwareHaptic(20, 80)
                    saveAndApplyEditModeOrder()
                    closeEditMode()
                    invalidate()
                }
            }
        } else {
            if (relX >= btnW * 3f) {
                // "Save"
                triggerHardwareHaptic(20, 80)
                saveAndApplyEditModeOrder()
                closeEditMode()
                invalidate()
            }
        }
        return
    }

    if (editModeTab == EditModeTab.PAGE) {
        val row = ((y - tabRowBottom) / actualRowH).toInt().coerceIn(0, numCategories - 1)
        val upLeft = bounds.right - 56f * dp
        val upRight = bounds.right - 28f * dp
        val downRight = bounds.right

        if (x in upLeft..upRight) {
            if (row > 0) {
                triggerHardwareHaptic(20, 100)
                val item = editModeCategories.removeAt(row)
                editModeCategories.add(row - 1, item)
                invalidate()
            }
        } else if (x in upRight..downRight) {
            if (row < numCategories - 1) {
                triggerHardwareHaptic(20, 100)
                val item = editModeCategories.removeAt(row)
                editModeCategories.add(row + 1, item)
                invalidate()
            }
        }
    } else if (editModeTab == EditModeTab.HUD) {
        handleHudTabTap(x, y, bounds, tabRowBottom, actualRowH)
    }
}

private fun LightspeedCruiseOverlay.handleHudTabTap(
    x: Float,
    y: Float,
    bounds: RectF,
    tabRowBottom: Float,
    actualRowH: Float
) {
    val dp = resources.displayMetrics.density
    val gearSectionY = tabRowBottom + (16f * dp)
    val gearPillH = 26f * dp
    val gearPillY = gearSectionY + (10f * dp)

    if (y in (gearPillY - 4f * dp)..(gearPillY + gearPillH + 4f * dp)) {
        val pillSpacing = 4f * dp
        val margin = 10f * dp
        val gearTotalPillW = bounds.width() - (margin * 2f)
        val gearPillW = (gearTotalPillW - pillSpacing) / 2f

        val options = listOf(false, true)
        for (i in options.indices) {
            val pLeft = bounds.left + margin + (i * (gearPillW + pillSpacing))
            val pRight = pLeft + gearPillW
            if (x in pLeft..pRight) {
                val selected = options[i]
                triggerHardwareHaptic(20, 100)
                context.defaultPrefs().edit()
                    .putBoolean(LightspeedPreferences.KEY_PINNED_GEAR_LEFT, selected)
                    .apply()
                invalidate()
                return
            }
        }
    }

    // Category Cruise name spacing pills
    val gearNoteY = gearPillY + gearPillH + (18f * dp)
    val spacingPillY = gearNoteY + (22f * dp) + (10f * dp)
    if (y in (spacingPillY - 4f * dp)..(spacingPillY + gearPillH + 4f * dp)) {
        val pillSpacing = 4f * dp
        val margin = 10f * dp
        val count = CATEGORY_SPACING_OPTIONS.size
        val pillW = (bounds.width() - (margin * 2f) - pillSpacing * (count - 1)) / count
        for (i in 0 until count) {
            val pLeft = bounds.left + margin + (i * (pillW + pillSpacing))
            if (x in pLeft..(pLeft + pillW)) {
                triggerHardwareHaptic(20, 100)
                context.defaultPrefs().edit()
                    .putInt(LightspeedPreferences.KEY_CATEGORY_CRUISE_SPACING_PERCENT, CATEGORY_SPACING_OPTIONS[i].first)
                    .apply()
                invalidate()
                return
            }
        }
    }

    // Category slider feel pills
    val feelPillY = spacingPillY + gearPillH + (22f * dp) + (10f * dp)
    if (y in (feelPillY - 4f * dp)..(feelPillY + gearPillH + 4f * dp)) {
        val pillSpacing = 4f * dp
        val margin = 10f * dp
        val count = CATEGORY_PHYSICS_OPTIONS.size
        val pillW = (bounds.width() - (margin * 2f) - pillSpacing * (count - 1)) / count
        for (i in 0 until count) {
            val pLeft = bounds.left + margin + (i * (pillW + pillSpacing))
            if (x in pLeft..(pLeft + pillW)) {
                triggerHardwareHaptic(20, 100)
                context.defaultPrefs().edit()
                    .putString(LightspeedPreferences.KEY_CATEGORY_CRUISE_PHYSICS_PROFILE, CATEGORY_PHYSICS_OPTIONS[i].first)
                    .apply()
                invalidate()
                return
            }
        }
    }
}

private fun LightspeedCruiseOverlay.handleMostUsedSort() {
    if (!LightspeedUsageStats.hasUsageAccess(context)) {
        triggerHardwareHaptic(20, 80)
        try {
            val intent = Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        } catch (_: Exception) {}
        dismissOverlay()
        return
    }

    triggerHardwareHaptic(25, 120)
    Thread {
        val usageMap = LightspeedUsageStats.usageMillisByPackage(context, 30)
        val defaultIndexMap = LightspeedDataBridge.DRAWER_CATEGORIES.withIndex().associate { it.value.id to it.index }

        val currentCategories = synchronized(editModeCategories) { editModeCategories.toList() }
        val categoryUsage = currentCategories.associate { cat ->
            val totalUsage = dataBridge.getAppsForCategory(cat.id).sumOf { app ->
                usageMap[app.packageName] ?: 0L
            }
            cat.id to totalUsage
        }

        val withUsage = currentCategories.filter { (categoryUsage[it.id] ?: 0L) > 0L }
            .sortedWith(compareByDescending<LightspeedDataBridge.CategoryNode> { categoryUsage[it.id] ?: 0L }
                .thenBy { defaultIndexMap[it.id] ?: Int.MAX_VALUE })

        val zeroUsage = currentCategories.filter { (categoryUsage[it.id] ?: 0L) == 0L }
            .sortedBy { defaultIndexMap[it.id] ?: Int.MAX_VALUE }

        val sortedCategories = withUsage + zeroUsage

        uiHandler.post {
            editModeCategories.clear()
            editModeCategories.addAll(sortedCategories)
            invalidate()
        }
    }.start()
}
