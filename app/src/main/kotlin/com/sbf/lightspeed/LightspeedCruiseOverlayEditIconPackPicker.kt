package com.sbf.lightspeed

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.drawable.Drawable
import com.sbf.lightspeed.settings.DeckThemeManager
import com.sbf.lightspeed.system.LightspeedIconManager
import com.sbf.lightspeed.system.LightspeedPreferences

internal data class CategoryIconPackOption(
    val pkg: String,
    val label: String,
    val icon: Drawable?,
    val isDefault: Boolean
)

internal fun LightspeedCruiseOverlay.openCategoryIconPackPicker(category: LightspeedDataBridge.CategoryNode) {
    if (!isEditModeOpen || isEditModePromptOpen) return
    isCategoryIconPickerOpen = true
    categoryIconPickerTarget = category
    triggerHardwareHaptic(25, 120)
    invalidate()
}

internal fun LightspeedCruiseOverlay.closeCategoryIconPackPicker() {
    isCategoryIconPickerOpen = false
    categoryIconPickerTarget = null
    invalidate()
}

internal fun LightspeedCruiseOverlay.getAvailableCategoryIconPackOptions(): List<CategoryIconPackOption> {
    val list = mutableListOf<CategoryIconPackOption>()
    list.add(
        CategoryIconPackOption(
            pkg = "default",
            label = "System Default",
            icon = null,
            isDefault = true
        )
    )
    val pm = context.packageManager
    val availablePacks = LightspeedIconManager.getAvailableIconPacks(context)
    for (pack in availablePacks) {
        if (!pack.isSystem && pack.packageName.isNotBlank()) {
            val icon = try {
                pm.getApplicationIcon(pack.packageName)
            } catch (_: Exception) {
                null
            }
            list.add(
                CategoryIconPackOption(
                    pkg = pack.packageName,
                    label = pack.label,
                    icon = icon,
                    isDefault = false
                )
            )
        }
    }
    return list
}

internal fun LightspeedCruiseOverlay.getCategoryIconPickerBounds(): RectF {
    val dp = resources.displayMetrics.density
    val screenW = width.toFloat()
    val screenH = height.toFloat()
    val options = getAvailableCategoryIconPackOptions()
    val headerH = 56f * dp
    val rowH = 44f * dp
    val contentH = if (options.size == 1) {
        headerH + rowH + (46f * dp) + (12f * dp)
    } else {
        headerH + (options.size * rowH) + (12f * dp)
    }
    val dialogH = contentH.coerceAtMost(screenH * 0.75f)
    val dialogW = (300f * dp).coerceIn(260f * dp, screenW - 32f * dp)
    val left = (screenW - dialogW) / 2f
    val top = (screenH - dialogH) / 2f
    return RectF(left, top, left + dialogW, top + dialogH)
}

internal fun LightspeedCruiseOverlay.selectCategoryIconPack(packPkg: String) {
    val cat = categoryIconPickerTarget ?: return
    val savePkg = if (packPkg == "default" || packPkg == "system") null else packPkg
    LightspeedPreferences.setCategoryIconPack(context, cat.id, savePkg)

    categoryGridCache.remove(cat.id)
    categoryAppsCache.remove(cat.id)
    applicationIconCache.clear()
    lastLoadedCategoryId = null

    triggerHardwareHaptic(25, 120)
    closeCategoryIconPackPicker()
}

internal fun LightspeedCruiseOverlay.handleCategoryIconPickerTap(x: Float, y: Float) {
    val bounds = getCategoryIconPickerBounds()
    if (!bounds.contains(x, y)) {
        triggerHardwareHaptic(15, 60)
        closeCategoryIconPackPicker()
        return
    }

    val dp = resources.displayMetrics.density
    val closeRect = RectF(bounds.right - 44f * dp, bounds.top, bounds.right, bounds.top + 44f * dp)
    if (closeRect.contains(x, y)) {
        triggerHardwareHaptic(15, 60)
        closeCategoryIconPackPicker()
        return
    }

    val headerH = 56f * dp
    val rowH = 44f * dp
    val options = getAvailableCategoryIconPackOptions()
    val relY = y - (bounds.top + headerH)
    if (relY >= 0f) {
        val index = (relY / rowH).toInt()
        if (index in options.indices) {
            selectCategoryIconPack(options[index].pkg)
        }
    }
}

internal fun LightspeedCruiseOverlay.drawCategoryIconPackPicker(canvas: Canvas, paint: Paint) {
    val dp = resources.displayMetrics.density
    val screenW = width.toFloat()
    val screenH = height.toFloat()

    // 1. Backdrop Dimming
    paint.style = Paint.Style.FILL
    paint.color = Color.argb(140, 0, 0, 0)
    canvas.drawRect(0f, 0f, screenW, screenH, paint)

    val bounds = getCategoryIconPickerBounds()
    val snapshot = DeckThemeManager.getSnapshot(context)
    val cornerRadius = (snapshot.cornerRadiusDp * 0.65f).coerceIn(12f, 24f) * dp
    val tintColor = categoryAccentOverride() ?: snapshot.tintArgb.toInt()
    val m3Accent = ensureHighContrastColor(tintColor)

    // 2. Optical 12-Theme Surface
    drawDeckThemedFlyoutSurface(canvas, bounds, snapshot, cornerRadius, dp, paint)

    // 3. Close Button '✕'
    paint.style = Paint.Style.FILL
    paint.color = Color.argb(170, 200, 220, 255)
    paint.textSize = 14f * dp
    paint.textAlign = Paint.Align.CENTER
    paint.typeface = Typeface.DEFAULT_BOLD
    canvas.drawText("✕", bounds.right - 20f * dp, bounds.top + 26f * dp, paint)

    // 4. Header Titles
    val targetCat = categoryIconPickerTarget
    val title = "${targetCat?.label ?: "Category"} Icon Pack"
    paint.textAlign = Paint.Align.LEFT
    paint.color = Color.WHITE
    paint.textSize = 13.5f * dp
    paint.typeface = Typeface.DEFAULT_BOLD
    canvas.drawText(title, bounds.left + 16f * dp, bounds.top + 25f * dp, paint)

    paint.textSize = 9.5f * dp
    paint.typeface = Typeface.DEFAULT
    paint.color = Color.argb(180, 200, 220, 255)
    canvas.drawText("Choose icon pack for this category", bounds.left + 16f * dp, bounds.top + 43f * dp, paint)

    // Divider
    val headerH = 56f * dp
    val headerBottom = bounds.top + headerH
    paint.style = Paint.Style.STROKE
    paint.strokeWidth = 1f * dp
    paint.color = Color.argb(50, 200, 220, 255)
    canvas.drawLine(bounds.left, headerBottom, bounds.right, headerBottom, paint)

    // 5. Options
    val rowH = 44f * dp
    val currentPack = if (targetCat != null) {
        LightspeedPreferences.getCategoryIconPack(context, targetCat.id) ?: "default"
    } else {
        "default"
    }

    val options = getAvailableCategoryIconPackOptions()
    val fontMetrics = paint.fontMetrics
    val textYOffset = (fontMetrics.descent + fontMetrics.ascent) / 2f

    for (i in options.indices) {
        val opt = options[i]
        val rowTop = headerBottom + (i * rowH)
        val rowRect = RectF(bounds.left + 8f * dp, rowTop + 2f * dp, bounds.right - 8f * dp, rowTop + rowH - 2f * dp)
        val isSelected = (opt.pkg == currentPack) || (opt.isDefault && (currentPack == "default" || currentPack == "system"))

        if (isSelected) {
            paint.style = Paint.Style.FILL
            paint.color = Color.argb(45, Color.red(m3Accent), Color.green(m3Accent), Color.blue(m3Accent))
            canvas.drawRoundRect(rowRect, 8f * dp, 8f * dp, paint)

            paint.style = Paint.Style.STROKE
            paint.strokeWidth = 1f * dp
            paint.color = Color.argb(140, Color.red(m3Accent), Color.green(m3Accent), Color.blue(m3Accent))
            canvas.drawRoundRect(rowRect, 8f * dp, 8f * dp, paint)
        }

        // Radio indicator
        val radioCx = rowRect.left + 16f * dp
        val radioCy = rowRect.centerY()
        if (isSelected) {
            paint.style = Paint.Style.FILL
            paint.color = m3Accent
            canvas.drawCircle(radioCx, radioCy, 5.5f * dp, paint)
        } else {
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = 1.2f * dp
            paint.color = Color.argb(100, 200, 220, 255)
            canvas.drawCircle(radioCx, radioCy, 5.5f * dp, paint)
        }

        // Optional Pack Icon
        var textLeft = radioCx + 14f * dp
        if (opt.icon != null) {
            val iconSz = (24f * dp).toInt()
            val iL = (radioCx + 12f * dp).toInt()
            val iT = (radioCy - (iconSz / 2f)).toInt()
            opt.icon.setBounds(iL, iT, iL + iconSz, iT + iconSz)
            opt.icon.draw(canvas)
            textLeft = (iL + iconSz + 10f * dp)
        }

        // Label
        paint.style = Paint.Style.FILL
        paint.textAlign = Paint.Align.LEFT
        paint.textSize = 11.5f * dp
        paint.typeface = if (isSelected) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
        paint.color = if (isSelected) Color.WHITE else Color.argb(230, 220, 230, 245)
        canvas.drawText(opt.label, textLeft, radioCy - textYOffset, paint)

        // Selected Badge
        if (isSelected) {
            val badgeRight = rowRect.right - 10f * dp
            val badgeW = 46f * dp
            val badgeH = 18f * dp
            val badgeRect = RectF(badgeRight - badgeW, radioCy - (badgeH / 2f), badgeRight, radioCy + (badgeH / 2f))

            paint.style = Paint.Style.FILL
            paint.color = Color.argb(50, Color.red(m3Accent), Color.green(m3Accent), Color.blue(m3Accent))
            canvas.drawRoundRect(badgeRect, 4f * dp, 4f * dp, paint)

            paint.style = Paint.Style.STROKE
            paint.strokeWidth = 0.8f * dp
            paint.color = m3Accent
            canvas.drawRoundRect(badgeRect, 4f * dp, 4f * dp, paint)

            paint.style = Paint.Style.FILL
            paint.textAlign = Paint.Align.CENTER
            paint.textSize = 8.5f * dp
            paint.typeface = Typeface.DEFAULT_BOLD
            paint.color = m3Accent
            val bfm = paint.fontMetrics
            val bTextY = badgeRect.centerY() - ((bfm.descent + bfm.ascent) / 2f)
            canvas.drawText("ACTIVE", badgeRect.centerX(), bTextY, paint)
        }
    }

    // 6. Helpful Note if only System Default is available
    if (options.size == 1) {
        val noteY = headerBottom + rowH + 16f * dp
        paint.style = Paint.Style.FILL
        paint.textAlign = Paint.Align.LEFT
        paint.textSize = 9.5f * dp
        paint.typeface = Typeface.DEFAULT
        paint.color = Color.argb(150, 180, 205, 235)
        canvas.drawText("No third-party icon packs installed.", bounds.left + 16f * dp, noteY, paint)
        canvas.drawText("Install icon packs to customize per category.", bounds.left + 16f * dp, noteY + 16f * dp, paint)
    }
}
