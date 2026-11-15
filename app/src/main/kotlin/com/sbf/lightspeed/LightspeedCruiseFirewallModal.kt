package com.sbf.lightspeed

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.view.MotionEvent
import com.sbf.lightspeed.system.ElevatedTaskCloser
import kotlin.math.hypot
import kotlin.math.max

internal data class FirewallAppEntry(
    val packageName: String,
    val label: String,
    val hasInternet: Boolean
)

internal fun LightspeedCruiseOverlay.openFirewallManagerModal() {
    isFirewallModalOpen = true
    firewallModalScrollOffset = 0f
    firewallModalDraggingRail = false
    val pm = context.packageManager
    val allApps = dataBridge.getAllApps().distinctBy { it.packageName }.sortedBy { it.label.lowercase() }
    firewallCachedApps = allApps.map { app ->
        val hasNet = try {
            pm.checkPermission(Manifest.permission.INTERNET, app.packageName) == PackageManager.PERMISSION_GRANTED
        } catch (_: Exception) {
            true
        }
        FirewallAppEntry(
            packageName = app.packageName,
            label = app.label,
            hasInternet = hasNet
        )
    }
    invalidate()
}

internal fun LightspeedCruiseOverlay.closeFirewallManagerModal() {
    isFirewallModalOpen = false
    firewallModalDraggingRail = false
    invalidate()
}

internal fun LightspeedCruiseOverlay.drawFirewallManagerModal(
    canvas: Canvas,
    density: Float,
    accent: Int
) {
    val dp = resources.displayMetrics.density
    val screenW = width.toFloat()
    val screenH = height.toFloat()

    // Scrim
    highlightPaint.style = Paint.Style.FILL
    highlightPaint.color = Color.argb(180, 0, 0, 0)
    canvas.drawRect(0f, 0f, screenW, screenH, highlightPaint)

    val modalW = (screenW - 24f * dp).coerceAtMost(420f * dp)
    val modalH = (screenH * 0.80f).coerceAtMost(screenH - 44f * dp)
    val left = (screenW - modalW) * 0.5f
    val top = (screenH - modalH) * 0.5f
    firewallModalRect.set(left, top, left + modalW, top + modalH)

    // Modal surface
    highlightPaint.style = Paint.Style.FILL
    highlightPaint.color = Color.argb(248, 11, 15, 24)
    canvas.drawRoundRect(firewallModalRect, 14f * dp, 14f * dp, highlightPaint)
    highlightPaint.style = Paint.Style.STROKE
    highlightPaint.strokeWidth = 1.4f * dp
    highlightPaint.color = Color.argb(185, Color.red(accent), Color.green(accent), Color.blue(accent))
    canvas.drawRoundRect(firewallModalRect, 14f * dp, 14f * dp, highlightPaint)

    val padX = 12f * dp
    val fm = textPaint.fontMetrics

    // Header + Shield Vector Icon + Close [X]
    LightspeedHudVectorIcons.drawVectorShield(canvas, left + padX + 7f * dp, top + 20f * dp, 6.5f * dp, accent, 1.5f * dp, slashed = false)
    textPaint.textAlign = Paint.Align.LEFT
    textPaint.typeface = Typeface.DEFAULT_BOLD
    textPaint.textSize = 11f * dp
    textPaint.color = Color.WHITE
    canvas.drawText("FIREWALL MANAGER", left + padX + 19f * dp, top + 24f * dp, textPaint)

    // Shizuku status badge
    val shizukuOk = ElevatedTaskCloser.isShizukuActive
    textPaint.typeface = Typeface.MONOSPACE
    textPaint.textSize = 7.6f * dp
    textPaint.color = if (shizukuOk) Color.rgb(34, 197, 94) else Color.rgb(245, 158, 11)
    canvas.drawText(if (shizukuOk) "SHIZUKU ACTIVE" else "SHIZUKU INACTIVE (TRACKING)", left + padX + 19f * dp, top + 36f * dp, textPaint)

    val closeSize = 22f * dp
    firewallCloseRect.set(
        firewallModalRect.right - closeSize - 10f * dp,
        top + 9f * dp,
        firewallModalRect.right - 10f * dp,
        top + 9f * dp + closeSize
    )
    highlightPaint.style = Paint.Style.FILL
    highlightPaint.color = Color.argb(120, 40, 50, 70)
    canvas.drawRoundRect(firewallCloseRect, 5f * dp, 5f * dp, highlightPaint)
    textPaint.textAlign = Paint.Align.CENTER
    textPaint.textSize = 10.5f * dp
    textPaint.color = Color.WHITE
    canvas.drawText("X", firewallCloseRect.centerX(), firewallCloseRect.centerY() - ((fm.descent + fm.ascent) / 2f), textPaint)

    // Partition apps into 3 accordions
    val blockedList = mutableListOf<FirewallAppEntry>()
    val allowedList = mutableListOf<FirewallAppEntry>()
    val offlineList = mutableListOf<FirewallAppEntry>()
    for (entry in firewallCachedApps) {
        when {
            LightspeedCruiseHudPods.isAppFirewallBlocked(context, entry.packageName) -> blockedList.add(entry)
            entry.hasInternet -> allowedList.add(entry)
            else -> offlineList.add(entry)
        }
    }

    val railW = 14f * dp
    val listLeft = left + padX
    val listRight = firewallModalRect.right - padX - railW - 4f * dp
    val listTop = top + 46f * dp
    val listBottom = firewallModalRect.bottom - 12f * dp
    val listH = (listBottom - listTop).coerceAtLeast(40f * dp)

    val hdrH = 28f * dp
    val rowH = 38f * dp
    val gapH = 6f * dp

    val totalContentH = (hdrH + gapH) * 3f +
        (if (firewallBlockedAccordionOpen) blockedList.size * rowH else 0f) +
        (if (firewallAllowedAccordionOpen) allowedList.size * rowH else 0f) +
        (if (firewallOfflineAccordionOpen) offlineList.size * rowH else 0f)

    firewallModalMaxScroll = max(0f, totalContentH - listH)
    firewallModalScrollOffset = firewallModalScrollOffset.coerceIn(0f, firewallModalMaxScroll)

    // Fast-Scroller Rail on right edge
    firewallFastRailRect.set(listRight + 4f * dp, listTop, firewallModalRect.right - padX, listBottom)
    highlightPaint.style = Paint.Style.FILL
    highlightPaint.color = Color.argb(35, 255, 255, 255)
    canvas.drawRoundRect(firewallFastRailRect, 4f * dp, 4f * dp, highlightPaint)
    if (firewallModalMaxScroll > 0f) {
        val thumbH = (listH * (listH / totalContentH)).coerceIn(22f * dp, listH * 0.6f)
        val thumbFrac = (firewallModalScrollOffset / firewallModalMaxScroll).coerceIn(0f, 1f)
        val thumbTop = listTop + thumbFrac * (listH - thumbH)
        val thumbRect = RectF(firewallFastRailRect.left + 1.5f * dp, thumbTop, firewallFastRailRect.right - 1.5f * dp, thumbTop + thumbH)
        highlightPaint.color = accent
        canvas.drawRoundRect(thumbRect, 3f * dp, 3f * dp, highlightPaint)
    }

    firewallAccordionHeaderRects.clear()
    firewallAppRowRects.clear()

    canvas.save()
    canvas.clipRect(listLeft, listTop, listRight, listBottom)
    var curY = listTop - firewallModalScrollOffset

    fun drawAccordionSection(
        key: String,
        title: String,
        isOpen: Boolean,
        entries: List<FirewallAppEntry>,
        sectionColor: Int,
        actionBadge: String
    ) {
        val hRect = RectF(listLeft, curY, listRight, curY + hdrH)
        if (hRect.bottom >= listTop && hRect.top <= listBottom) {
            firewallAccordionHeaderRects[key] = RectF(hRect)
            highlightPaint.style = Paint.Style.FILL
            highlightPaint.color = Color.argb(45, Color.red(sectionColor), Color.green(sectionColor), Color.blue(sectionColor))
            canvas.drawRoundRect(hRect, 6f * dp, 6f * dp, highlightPaint)
            highlightPaint.style = Paint.Style.STROKE
            highlightPaint.strokeWidth = 0.9f * dp
            highlightPaint.color = Color.argb(120, Color.red(sectionColor), Color.green(sectionColor), Color.blue(sectionColor))
            canvas.drawRoundRect(hRect, 6f * dp, 6f * dp, highlightPaint)

            textPaint.textAlign = Paint.Align.LEFT
            textPaint.typeface = Typeface.DEFAULT_BOLD
            textPaint.textSize = 9.0f * dp
            textPaint.color = sectionColor
            val chevron = if (isOpen) "▼" else "▶"
            canvas.drawText("$chevron  $title (${entries.size})", hRect.left + 10f * dp, hRect.centerY() - ((fm.descent + fm.ascent) / 2f), textPaint)
        }
        curY += hdrH + gapH

        if (!isOpen) return

        for (entry in entries) {
            val rRect = RectF(listLeft, curY, listRight, curY + rowH - 4f * dp)
            if (rRect.bottom >= listTop && rRect.top <= listBottom) {
                if (key != "offline") {
                    firewallAppRowRects[entry.packageName] = RectF(rRect)
                }
                highlightPaint.style = Paint.Style.FILL
                highlightPaint.color = Color.argb(24, 255, 255, 255)
                canvas.drawRoundRect(rRect, 6f * dp, 6f * dp, highlightPaint)

                // App icon
                val iconSize = 22f * dp
                val iconTop = rRect.centerY() - iconSize * 0.5f
                val iconLeft = rRect.left + 8f * dp
                val icon = applicationIconCache[entry.packageName] ?: try {
                    context.packageManager.getApplicationIcon(entry.packageName).also { applicationIconCache[entry.packageName] = it }
                } catch (_: Exception) {
                    null
                }
                if (icon != null) {
                    icon.setBounds(iconLeft.toInt(), iconTop.toInt(), (iconLeft + iconSize).toInt(), (iconTop + iconSize).toInt())
                    icon.draw(canvas)
                }

                // Action pill on right
                textPaint.typeface = Typeface.DEFAULT_BOLD
                textPaint.textSize = 7.4f * dp
                val pillW = max(50f * dp, textPaint.measureText(actionBadge) + 14f * dp)
                val pillH = 18f * dp
                val pillRect = RectF(rRect.right - pillW - 8f * dp, rRect.centerY() - pillH * 0.5f, rRect.right - 8f * dp, rRect.centerY() + pillH * 0.5f)
                highlightPaint.style = Paint.Style.FILL
                highlightPaint.color = Color.argb(42, Color.red(sectionColor), Color.green(sectionColor), Color.blue(sectionColor))
                canvas.drawRoundRect(pillRect, 4.5f * dp, 4.5f * dp, highlightPaint)
                highlightPaint.style = Paint.Style.STROKE
                highlightPaint.strokeWidth = 0.8f * dp
                highlightPaint.color = sectionColor
                canvas.drawRoundRect(pillRect, 4.5f * dp, 4.5f * dp, highlightPaint)
                textPaint.textAlign = Paint.Align.CENTER
                textPaint.color = sectionColor
                canvas.drawText(actionBadge, pillRect.centerX(), pillRect.centerY() - ((fm.descent + fm.ascent) / 2f), textPaint)

                // App name & package
                val txtLeft = iconLeft + iconSize + 8f * dp
                val maxTxtW = (pillRect.left - txtLeft - 6f * dp).coerceAtLeast(40f * dp)
                textPaint.textAlign = Paint.Align.LEFT
                textPaint.typeface = Typeface.DEFAULT_BOLD
                textPaint.textSize = 9.0f * dp
                textPaint.color = Color.WHITE
                val labelStr = if (textPaint.measureText(entry.label) > maxTxtW) {
                    entry.label.take(18) + ".."
                } else entry.label
                canvas.drawText(labelStr, txtLeft, rRect.top + 14f * dp, textPaint)

                textPaint.typeface = Typeface.MONOSPACE
                textPaint.textSize = 7.0f * dp
                textPaint.color = Color.argb(175, 190, 205, 225)
                val pkgStr = if (textPaint.measureText(entry.packageName) > maxTxtW) {
                    entry.packageName.take(24) + ".."
                } else entry.packageName
                canvas.drawText(pkgStr, txtLeft, rRect.top + 26f * dp, textPaint)
            }
            curY += rowH
        }
    }

    drawAccordionSection("blocked", "BLOCKED APPS", firewallBlockedAccordionOpen, blockedList, Color.rgb(239, 68, 68), "UNBLOCK")
    drawAccordionSection("allowed", "ALLOWED INTERNET APPS", firewallAllowedAccordionOpen, allowedList, accent, "BLOCK")
    drawAccordionSection("offline", "OFFLINE APPS (NO NET PERM)", firewallOfflineAccordionOpen, offlineList, Color.rgb(34, 197, 94), "OFFLINE")

    canvas.restore()
}

internal fun LightspeedCruiseOverlay.handleFirewallManagerModalTouch(event: MotionEvent): Boolean {
    val x = event.x
    val y = event.y

    fun scrollFromRailY(touchY: Float) {
        if (firewallModalMaxScroll <= 0f || firewallFastRailRect.height() <= 1f) return
        val frac = ((touchY - firewallFastRailRect.top) / firewallFastRailRect.height()).coerceIn(0f, 1f)
        firewallModalScrollOffset = frac * firewallModalMaxScroll
        invalidate()
    }

    when (event.actionMasked) {
        MotionEvent.ACTION_DOWN -> {
            touchDownX = x; touchDownY = y; lastTouchY = y
            if (firewallCloseRect.contains(x, y) || !firewallModalRect.contains(x, y)) {
                triggerHardwareHaptic(18, 80)
                closeFirewallManagerModal()
                return true
            }
            if (firewallFastRailRect.contains(x, y)) {
                firewallModalDraggingRail = true
                triggerHardwareHaptic(14, 70)
                scrollFromRailY(y)
                return true
            }
            return true
        }
        MotionEvent.ACTION_MOVE -> {
            if (firewallModalDraggingRail) {
                scrollFromRailY(y)
                return true
            }
            val dy = y - lastTouchY
            lastTouchY = y
            if (firewallModalMaxScroll > 0f) {
                firewallModalScrollOffset = (firewallModalScrollOffset - dy).coerceIn(0f, firewallModalMaxScroll)
                invalidate()
            }
            return true
        }
        MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
            if (firewallModalDraggingRail) {
                firewallModalDraggingRail = false
                invalidate()
                return true
            }
            if (hypot((x - touchDownX).toDouble(), (y - touchDownY).toDouble()) < 16f) {
                for ((key, rect) in firewallAccordionHeaderRects) {
                    if (rect.contains(x, y)) {
                        triggerHardwareHaptic(18, 90)
                        when (key) {
                            "blocked" -> firewallBlockedAccordionOpen = !firewallBlockedAccordionOpen
                            "allowed" -> firewallAllowedAccordionOpen = !firewallAllowedAccordionOpen
                            "offline" -> firewallOfflineAccordionOpen = !firewallOfflineAccordionOpen
                        }
                        invalidate()
                        return true
                    }
                }
                for ((pkg, rect) in firewallAppRowRects) {
                    if (rect.contains(x, y)) {
                        val label = firewallCachedApps.firstOrNull { it.packageName == pkg }?.label ?: pkg
                        triggerHardwareHaptic(22, 110)
                        LightspeedCruiseHudPods.toggleAppFirewall(context, pkg, label)
                        invalidate()
                        return true
                    }
                }
            }
            return true
        }
    }
    return true
}
