package com.sbf.lightspeed

import android.content.Intent
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.net.Uri
import android.provider.Settings
import android.util.Log
import com.sbf.lightspeed.system.ElevatedTaskCloser

internal enum class PinnedMenuItem {
    OPEN,
    APP_INFO,
    SPLIT_SCREEN,
    SPLIT_WITH,
    POPUP_WINDOW,
    CHANGE_CATEGORY,
    NONE
}

internal fun LightspeedCruiseOverlay.closePinnedAppMenu() {
    stickyPinLongPressRunnable?.let { removeCallbacks(it) }
    stickyPinLongPressRunnable = null
    stickyPinLongPressFired = false
    pinnedMenuApp = null
    pinnedMenuCategoryMode = false
}

internal fun LightspeedCruiseOverlay.isPinnedAppMenuOpen(): Boolean = pinnedMenuApp != null

internal fun LightspeedCruiseOverlay.getPinnedAppMenuBounds(): RectF {
    val dp = resources.displayMetrics.density
    val menuW = 168f * dp
    val rowH = 44f * dp
    val menuH = rowH * 6f
    val margin = 12f * dp
    val screenW = width.toFloat()
    val screenH = height.toFloat()

    var left = pinnedMenuX + 16f * dp
    if (left + menuW > screenW - margin) {
        left = pinnedMenuX - menuW - 16f * dp
    }
    left = left.coerceIn(margin, (screenW - menuW - margin).coerceAtLeast(margin))

    var top = pinnedMenuY - (menuH / 2f)
    top = top.coerceIn(margin, (screenH - menuH - margin).coerceAtLeast(margin))

    return RectF(left, top, left + menuW, top + menuH)
}

internal fun LightspeedCruiseOverlay.getPinnedAppMenuCategoryBounds(): RectF {
    val dp = resources.displayMetrics.density
    val catRowH = 40f * dp
    val menuH = catRowH * 7f
    val margin = 12f * dp
    val screenW = width.toFloat()
    val screenH = height.toFloat()
    val menuW = (280f * dp).coerceAtMost(screenW - margin * 2f)

    var left = pinnedMenuX + 16f * dp
    if (left + menuW > screenW - margin) {
        left = pinnedMenuX - menuW - 16f * dp
    }
    left = left.coerceIn(margin, (screenW - menuW - margin).coerceAtLeast(margin))

    var top = pinnedMenuY - (menuH / 2f)
    top = top.coerceIn(margin, (screenH - menuH - margin).coerceAtLeast(margin))

    return RectF(left, top, left + menuW, top + menuH)
}

internal fun LightspeedCruiseOverlay.hitTestPinnedAppMenu(x: Float, y: Float): PinnedMenuItem {
    if (pinnedMenuApp == null) return PinnedMenuItem.NONE
    val bounds = getPinnedAppMenuBounds()
    if (!bounds.contains(x, y)) return PinnedMenuItem.NONE
    val rowH = bounds.height() / 6f
    val offset = y - bounds.top
    return when {
        offset < rowH -> PinnedMenuItem.OPEN
        offset < rowH * 2f -> PinnedMenuItem.APP_INFO
        offset < rowH * 3f -> PinnedMenuItem.SPLIT_WITH
        offset < rowH * 4f -> PinnedMenuItem.SPLIT_SCREEN
        offset < rowH * 5f -> PinnedMenuItem.POPUP_WINDOW
        else -> PinnedMenuItem.CHANGE_CATEGORY
    }
}

internal fun LightspeedCruiseOverlay.hitTestCategoryMenu(x: Float, y: Float): LightspeedDataBridge.CategoryNode? {
    if (pinnedMenuApp == null || !pinnedMenuCategoryMode) return null
    val bounds = getPinnedAppMenuCategoryBounds()
    if (!bounds.contains(x, y)) return null
    val catRowH = bounds.height() / 7f
    val colW = bounds.width() / 2f
    val col = if (x < bounds.left + colW) 0 else 1
    val row = ((y - bounds.top) / catRowH).toInt().coerceIn(0, 6)
    val index = col * 7 + row
    val categories = LightspeedDataBridge.DRAWER_CATEGORIES
    return if (index in categories.indices) categories[index] else null
}

internal fun LightspeedCruiseOverlay.handlePinnedAppMenuTap(x: Float, y: Float) {
    val target = pinnedMenuApp ?: run {
        closePinnedAppMenu()
        return
    }

    if (pinnedMenuCategoryMode) {
        val selectedCat = hitTestCategoryMenu(x, y)
        if (selectedCat != null) {
            com.sbf.lightspeed.system.LightspeedPreferences.setAppCategoryOverride(context, target.packageName, selectedCat.id)
            dataBridge.moveAppCategory(target.packageName, selectedCat.id)
            dataBridge.refreshCacheAsync()
            categoryAppsCache.clear()
            categoryGridCache.clear()
            categoryHeightCache.clear()
            lastLoadedCategoryId = null
            closePinnedAppMenu()
            loadActiveCategoryGrid()
        } else {
            closePinnedAppMenu()
        }
    } else {
        val hitItem = hitTestPinnedAppMenu(x, y)
        when (hitItem) {
            PinnedMenuItem.OPEN -> {
                closePinnedAppMenu()
                executeLaunch(target)
                dismissOverlay()
            }
            PinnedMenuItem.APP_INFO -> {
                closePinnedAppMenu()
                openAppDetailsSettings(target.packageName)
                dismissOverlay()
            }
            PinnedMenuItem.SPLIT_SCREEN -> {
                closePinnedAppMenu()
                val component = if (target.activityName.isNotBlank()) {
                    "${target.packageName}/${target.activityName}"
                } else {
                    context.packageManager.getLaunchIntentForPackage(target.packageName)?.component?.flattenToString()
                        ?: "${target.packageName}/${target.activityName}"
                }
                ElevatedTaskCloser.splitWithApp(context, component, allowRecents = false)
                dismissOverlay()
            }
            PinnedMenuItem.SPLIT_WITH -> {
                closePinnedAppMenu()
                val component = if (target.activityName.isNotBlank()) {
                    "${target.packageName}/${target.activityName}"
                } else {
                    context.packageManager.getLaunchIntentForPackage(target.packageName)?.component?.flattenToString()
                        ?: "${target.packageName}/${target.activityName}"
                }
                dismissOverlay()
                ElevatedTaskCloser.splitPairWithApp(context, component)
            }
            PinnedMenuItem.POPUP_WINDOW -> {
                closePinnedAppMenu()
                val component = if (target.activityName.isNotBlank()) {
                    "${target.packageName}/${target.activityName}"
                } else {
                    context.packageManager.getLaunchIntentForPackage(target.packageName)?.component?.flattenToString()
                        ?: "${target.packageName}/${target.activityName}"
                }
                ElevatedTaskCloser.launchAppInPopup(context, component, allowRecents = false)
                dismissOverlay()
            }
            PinnedMenuItem.CHANGE_CATEGORY -> {
                pinnedMenuCategoryMode = true
                triggerHardwareHaptic(25, 120)
            }
            PinnedMenuItem.NONE -> {
                closePinnedAppMenu()
            }
        }
    }
}

internal fun LightspeedCruiseOverlay.drawPinnedAppMenu(canvas: Canvas, paint: Paint) {
    if (pinnedMenuApp == null) return
    if (pinnedMenuCategoryMode) {
        drawPinnedAppCategoryMenu(canvas, paint)
        return
    }

    val dp = resources.displayMetrics.density
    val bounds = getPinnedAppMenuBounds()
    val rowH = bounds.height() / 6f

    // Theme-driven optical styling
    val snapshot = com.sbf.lightspeed.settings.DeckThemeManager.getSnapshot(context)
    val cornerRadius = (snapshot.cornerRadiusDp * 0.65f).coerceIn(10f, 24f) * dp
    val borderColor = snapshot.borderColorArgb.toInt()

    // 1-4. Full 12-theme optical flyout surface (shadow, dense substrate, light deflection, specular cap & border)
    drawDeckThemedFlyoutSurface(canvas, bounds, snapshot, cornerRadius, dp, paint)

    // 5. Divider lines between rows
    paint.strokeWidth = 1f * dp
    paint.color = Color.argb(
        45,
        Color.red(borderColor),
        Color.green(borderColor),
        Color.blue(borderColor)
    )
    for (i in 1 until 6) {
        val divY = bounds.top + (rowH * i)
        canvas.drawLine(bounds.left + 16f * dp, divY, bounds.right - 16f * dp, divY, paint)
    }

    // Row texts
    paint.style = Paint.Style.FILL
    paint.color = Color.argb(245, 240, 245, 255)
    paint.textSize = 14.5f * dp
    paint.textAlign = Paint.Align.LEFT
    paint.typeface = android.graphics.Typeface.DEFAULT_BOLD

    val textX = bounds.left + 18f * dp
    val fontMetrics = paint.fontMetrics
    val textYOffset = (fontMetrics.descent + fontMetrics.ascent) / 2f

    val menuItems = listOf("Open", "App info", "Split with...", "Split screen", "Pop-up window", "Change category")
    menuItems.forEachIndexed { i, label ->
        val centerY = bounds.top + (rowH * i) + (rowH / 2f)
        canvas.drawText(label, textX, centerY - textYOffset, paint)
    }
}

internal fun LightspeedCruiseOverlay.drawPinnedAppCategoryMenu(canvas: Canvas, paint: Paint) {
    val dp = resources.displayMetrics.density
    val bounds = getPinnedAppMenuCategoryBounds()
    val catRowH = bounds.height() / 7f
    val colW = bounds.width() / 2f

    // Theme-driven optical styling
    val snapshot = com.sbf.lightspeed.settings.DeckThemeManager.getSnapshot(context)
    val cornerRadius = (snapshot.cornerRadiusDp * 0.65f).coerceIn(10f, 24f) * dp
    val borderColor = snapshot.borderColorArgb.toInt()

    // 1-4. Full 12-theme optical flyout surface (shadow, dense substrate, light deflection, specular cap & border)
    drawDeckThemedFlyoutSurface(canvas, bounds, snapshot, cornerRadius, dp, paint)

    // 5. Vertical divider line between columns
    paint.strokeWidth = 1f * dp
    paint.color = Color.argb(
        45,
        Color.red(borderColor),
        Color.green(borderColor),
        Color.blue(borderColor)
    )
    val midX = bounds.left + colW
    canvas.drawLine(midX, bounds.top + 8f * dp, midX, bounds.bottom - 8f * dp, paint)

    // 6. Horizontal divider lines between rows
    for (r in 1 until 7) {
        val divY = bounds.top + (r * catRowH)
        canvas.drawLine(bounds.left + 12f * dp, divY, midX - 8f * dp, divY, paint)
        canvas.drawLine(midX + 8f * dp, divY, bounds.right - 12f * dp, divY, paint)
    }

    // Category display names
    paint.style = Paint.Style.FILL
    paint.textSize = 12.5f * dp
    paint.textAlign = Paint.Align.LEFT
    paint.typeface = android.graphics.Typeface.DEFAULT_BOLD

    val fontMetrics = paint.fontMetrics
    val textYOffset = (fontMetrics.descent + fontMetrics.ascent) / 2f

    val categories = LightspeedDataBridge.DRAWER_CATEGORIES
    for (i in categories.indices) {
        val cat = categories[i]
        val col = i / 7
        val row = i % 7
        val cellLeft = bounds.left + (col * colW)
        val cellTop = bounds.top + (row * catRowH)
        val centerY = cellTop + (catRowH / 2f)
        val textX = cellLeft + 14f * dp

        paint.color = Color.argb(245, 240, 245, 255)
        canvas.drawText(cat.label, textX, centerY - textYOffset, paint)
    }
}

internal fun LightspeedCruiseOverlay.openAppDetailsSettings(packageName: String) {
    try {
        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$packageName")).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    } catch (e: Exception) {
        Log.e("PinnedAppMenu", "Failed to open application details for $packageName", e)
    }
}

/**
 * Renders the full 12-theme optical flyout surface on the Canvas overlay, featuring:
 * 1. Deep ambient shadow elevation separation from underlying app icons
 * 2. High-contrast protective substrate backing (guaranteeing text contrast)
 * 3. Authentic light deflection / refraction pockets (Liquid Jelly electric cyan/iris radial gradient,
 *    Glass Lava molten ember caustics + cool spectral dispersion, Cyber Neon radiant bloom)
 * 4. Crisp glossy specular glare highlight along the top rim
 * 5. Optical cushion bevel and themed border stroke
 */
internal fun drawDeckThemedFlyoutSurface(
    canvas: Canvas,
    bounds: RectF,
    snapshot: com.sbf.lightspeed.settings.DeckThemeSnapshot,
    cornerRadius: Float,
    dp: Float,
    paint: Paint
) {
    val styleId = snapshot.styleId
    val panelColor = snapshot.panelColorArgb.toInt()
    val tintColor = snapshot.tintArgb.toInt()
    val borderColor = snapshot.borderColorArgb.toInt()
    val w = bounds.width().coerceAtLeast(1f)
    val h = bounds.height().coerceAtLeast(1f)

    // 1-2. Translucent optical glass substrate with ambient drop shadow (no black interior mask)
    val flyoutAlpha = when (styleId) {
        "prism" -> 0.38f
        "jelly" -> 0.46f
        "glassmorphism" -> 0.45f
        "frosted" -> 0.52f
        "acrylic" -> 0.55f
        "gaussian" -> 0.54f
        "progressive" -> 0.55f
        "neon" -> 0.65f
        "cyberdeck" -> 0.75f
        "stealth" -> 0.85f
        "oled" -> 0.94f
        "dark" -> 0.92f
        "depth" -> 0.88f
        "liquid_flow" -> 0.50f
        else -> 0.58f
    }
    val baseAlpha = (flyoutAlpha * 255f).toInt().coerceIn(40, 255)

    paint.style = Paint.Style.FILL
    paint.color = Color.argb(
        baseAlpha,
        Color.red(panelColor),
        Color.green(panelColor),
        Color.blue(panelColor)
    )
    paint.setShadowLayer(12f * dp, 0f, 3f * dp, Color.argb(140, 0, 0, 0))
    canvas.drawRoundRect(bounds, cornerRadius, cornerRadius, paint)
    paint.clearShadowLayer()

    // 3. Authentic light deflection & themed refraction caustics
    when (styleId) {
        "jelly" -> {
            // Liquid Jelly: Electric sky-blue to soft iris violet liquid diagonal infusion
            val jellyShader = android.graphics.LinearGradient(
                bounds.left,
                bounds.top,
                bounds.right,
                bounds.bottom,
                intArrayOf(
                    Color.argb(45, 0x38, 0xBD, 0xF8),
                    Color.argb(28, 0x81, 0x8C, 0xF8),
                    Color.argb(16, 0x31, 0x2E, 0x81)
                ),
                floatArrayOf(0f, 0.50f, 1f),
                android.graphics.Shader.TileMode.CLAMP
            )
            paint.shader = jellyShader
            canvas.drawRoundRect(bounds, cornerRadius, cornerRadius, paint)
            paint.shader = null
        }
        "prism" -> {
            // Glass Lava: Subtle diagonal chromatic dispersion (warm coral glint top-left -> cool spectral cyan bottom-right)
            val prismShader = android.graphics.LinearGradient(
                bounds.left,
                bounds.top,
                bounds.right,
                bounds.bottom,
                intArrayOf(
                    Color.argb(32, 0xFF, 0x8A, 0x65),
                    Color.argb(12, 0xFF, 0x52, 0x52),
                    Color.TRANSPARENT,
                    Color.argb(10, 0x3D, 0x5A, 0xFE),
                    Color.argb(28, 0x00, 0xE5, 0xFF)
                ),
                floatArrayOf(0f, 0.22f, 0.52f, 0.78f, 1f),
                android.graphics.Shader.TileMode.CLAMP
            )
            paint.shader = prismShader
            canvas.drawRoundRect(bounds, cornerRadius, cornerRadius, paint)
            paint.shader = null
        }
        "neon" -> {
            // Cyber Neon: Electric radiant cyan flare
            val neonShader = android.graphics.LinearGradient(
                bounds.left,
                bounds.top,
                bounds.right,
                bounds.bottom,
                intArrayOf(
                    Color.argb(35, 0x00, 0xE5, 0xFF),
                    Color.TRANSPARENT,
                    Color.argb(30, 0xE0, 0x40, 0xFB)
                ),
                floatArrayOf(0f, 0.55f, 1f),
                android.graphics.Shader.TileMode.CLAMP
            )
            paint.shader = neonShader
            canvas.drawRoundRect(bounds, cornerRadius, cornerRadius, paint)
            paint.shader = null
        }
        else -> {
            // Chromatic tint infusion wash
            val tintAlpha = (snapshot.panelAlpha * 40f).toInt().coerceIn(20, 70)
            paint.color = Color.argb(
                tintAlpha,
                Color.red(tintColor),
                Color.green(tintColor),
                Color.blue(tintColor)
            )
            canvas.drawRoundRect(bounds, cornerRadius, cornerRadius, paint)
        }
    }

    // 4. Smooth top rim specular gleam (fades out at both sides, no cut-off half line)
    val hl = snapshot.highlightStrength.coerceAtLeast(0.35f)
    val glareAlpha = (hl * 130f).toInt().coerceIn(30, 150)
    val glareShader = android.graphics.LinearGradient(
        bounds.left + cornerRadius,
        bounds.top + 2f * dp,
        bounds.right - cornerRadius,
        bounds.top + 2f * dp,
        intArrayOf(
            Color.TRANSPARENT,
            Color.argb(glareAlpha, 255, 255, 255),
            Color.argb((glareAlpha * 0.4f).toInt(), 255, 255, 255),
            Color.TRANSPARENT
        ),
        floatArrayOf(0f, 0.35f, 0.70f, 1f),
        android.graphics.Shader.TileMode.CLAMP
    )
    paint.style = Paint.Style.STROKE
    paint.strokeWidth = 2.0f * dp
    paint.shader = glareShader
    canvas.drawLine(
        bounds.left + cornerRadius,
        bounds.top + 2f * dp,
        bounds.right - cornerRadius,
        bounds.top + 2f * dp,
        paint
    )
    paint.shader = null

    // 5. Bevel inner cushion rim
    paint.style = Paint.Style.STROKE
    paint.strokeWidth = 1.2f * dp
    val cushionAlpha = when (styleId) {
        "jelly" -> 160
        "prism" -> 150
        "frosted" -> 140
        else -> 90
    }
    paint.color = Color.argb(cushionAlpha, 255, 255, 255)
    val innerBounds = RectF(bounds).apply { inset(1.2f * dp, 1.2f * dp) }
    canvas.drawRoundRect(innerBounds, (cornerRadius - 1.2f * dp).coerceAtLeast(1f), (cornerRadius - 1.2f * dp).coerceAtLeast(1f), paint)

    // 6. Outer border stroke
    val strokeWidth = (snapshot.borderWidthDp * dp).coerceIn(1.2f * dp, 3f * dp)
    paint.strokeWidth = strokeWidth
    paint.color = Color.argb(
        190,
        Color.red(borderColor),
        Color.green(borderColor),
        Color.blue(borderColor)
    )
    canvas.drawRoundRect(bounds, cornerRadius, cornerRadius, paint)
}
