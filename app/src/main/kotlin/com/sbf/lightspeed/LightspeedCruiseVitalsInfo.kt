package com.sbf.lightspeed

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface

/**
 * "!" explainer for Culprit Hunter (CPU & HEAT, WAKELOCK, RAM & SWAP tabs).
 *
 * Header "!" button toggles a card that covers the sort bar + list and explains, in plain
 * language, what each sort pill / metric means and how heat slows charging. Tap the card or
 * the "!" again to close it. Storage has its own UI and does not show the button.
 */

private data class VitalsInfoLine(val text: String, val isTerm: Boolean, val gapBefore: Boolean)

private class VitalsInfoEntry(val term: String, val body: String)

private val VITALS_INFO_CPU = listOf(
    VitalsInfoEntry(
        "CPU %",
        "How much of the processor an app is using right now. More CPU means more heat and faster battery drain."
    ),
    VitalsInfoEntry(
        "TIME",
        "Total CPU time the app has used. A big TIME on an app you rarely open is a red flag."
    ),
    VitalsInfoEntry(
        "BG HOG",
        "Apps burning a lot of CPU while hardly ever on screen (amber rows). Prime suspects when your phone feels warm in your pocket."
    ),
    VitalsInfoEntry(
        "HEAT & CHARGING",
        "When the battery gets warm (roughly 40 C and above; it varies by phone), Android and the charger cut the charging current to protect the battery. A hot phone charges slower. Stopping CPU hogs before you plug in keeps it cool so it can charge at full speed."
    ),
    VitalsInfoEntry(
        "CHART",
        "The orange line is temperature and the cyan line is RAM use. PEAK TEMP is the hottest point in the selected span. Without Shizuku, CPU values are estimates."
    )
)

private val VITALS_INFO_WAKELOCK = listOf(
    VitalsInfoEntry(
        "WAKELOCK",
        "A request an app makes to keep the CPU awake so it cannot sleep. Fine while music plays or a download runs, but if an app forgets to let go, your phone stays awake and drains the battery."
    ),
    VitalsInfoEntry(
        "LOCKS",
        "How many wakelocks the app holds right now (3 LOCKS means three separate reasons the CPU cannot sleep). Sorted most locks first."
    ),
    VitalsInfoEntry(
        "AWAKE",
        "How long the app has kept the CPU awake. Sorted longest first. Handy for finding slow drain while the screen is off."
    ),
    VitalsInfoEntry(
        "EST ROWS",
        "Apps with no live lock but heavy background activity. Their awake time is an estimate based on background CPU."
    ),
    VitalsInfoEntry(
        "HEAT",
        "A CPU that cannot sleep also makes heat, and a warm battery charges slower."
    ),
    VitalsInfoEntry(
        "STOP",
        "Force-stops the app. Skip apps you rely on (music, navigation, messaging); they stay stopped until you open them again."
    )
)

private val VITALS_INFO_RAM = listOf(
    VitalsInfoEntry(
        "RAM",
        "Memory an app is using right now. When RAM runs low, Android closes background apps to make room. Sorted biggest first."
    ),
    VitalsInfoEntry(
        "SWAP",
        "Overflow memory the system uses when RAM is full. A lot of SWAP means the phone is under memory pressure and can feel slow. Sorted biggest first."
    ),
    VitalsInfoEntry(
        "STOP",
        "Force-stops the app. Skip apps you rely on (music, navigation, messaging); they stay stopped until you open them again."
    )
)

private var vitalsInfoCacheKey: String = ""
private var vitalsInfoCacheLines: List<VitalsInfoLine> = emptyList()

private fun wrapVitalsInfoText(text: String, paint: Paint, maxW: Float): List<String> {
    val out = ArrayList<String>()
    var line = StringBuilder()
    for (word in text.split(' ')) {
        val candidate = if (line.isEmpty()) word else "$line $word"
        if (paint.measureText(candidate) <= maxW || line.isEmpty()) {
            line = StringBuilder(candidate)
        } else {
            out.add(line.toString())
            line = StringBuilder(word)
        }
    }
    if (line.isNotEmpty()) out.add(line.toString())
    return out
}

/** Draws the round "!" button; also records its hit rect for [LightspeedCruiseOverlay.vitalsInfoButtonRect]. */
internal fun LightspeedCruiseOverlay.drawVitalsInfoButton(
    canvas: Canvas,
    rect: RectF,
    accent: Int,
    dp: Float
) {
    vitalsInfoButtonRect.set(rect)
    highlightPaint.style = Paint.Style.FILL
    highlightPaint.color = if (vitalsInfoOpen) {
        Color.argb(205, Color.red(accent), Color.green(accent), Color.blue(accent))
    } else {
        Color.argb(55, Color.red(accent), Color.green(accent), Color.blue(accent))
    }
    canvas.drawRoundRect(rect, 6f * dp, 6f * dp, highlightPaint)
    textPaint.textAlign = Paint.Align.CENTER
    textPaint.typeface = Typeface.DEFAULT_BOLD
    textPaint.textSize = 13f * dp
    textPaint.color = if (vitalsInfoOpen) Color.BLACK else accent
    val fm = textPaint.fontMetrics
    canvas.drawText("!", rect.centerX(), rect.centerY() - ((fm.descent + fm.ascent) / 2f), textPaint)
    textPaint.textAlign = Paint.Align.LEFT
}

/** Draws the explainer card for the active tab over the sort bar + list area. */
internal fun LightspeedCruiseOverlay.drawVitalsInfoCard(
    canvas: Canvas,
    left: Float,
    top: Float,
    right: Float,
    bottom: Float,
    tabKey: String,
    accent: Int,
    dp: Float
) {
    vitalsInfoCardRect.set(left, top, right, bottom)
    highlightPaint.style = Paint.Style.FILL
    highlightPaint.color = Color.argb(250, 12, 18, 30)
    canvas.drawRoundRect(vitalsInfoCardRect, 10f * dp, 10f * dp, highlightPaint)
    highlightPaint.style = Paint.Style.STROKE
    highlightPaint.strokeWidth = 1f * dp
    highlightPaint.color = Color.argb(150, Color.red(accent), Color.green(accent), Color.blue(accent))
    canvas.drawRoundRect(vitalsInfoCardRect, 10f * dp, 10f * dp, highlightPaint)

    val entries = when (tabKey) {
        "wakelocks" -> VITALS_INFO_WAKELOCK
        "ram" -> VITALS_INFO_RAM
        else -> VITALS_INFO_CPU
    }
    val title = when (tabKey) {
        "wakelocks" -> "WAKELOCK GUIDE"
        "ram" -> "RAM & SWAP GUIDE"
        else -> "CPU & HEAT GUIDE"
    }

    val pad = 11f * dp
    val maxW = (right - left) - pad * 2f
    var chosenLines: List<VitalsInfoLine> = emptyList()
    var chosenSize = 8.2f * dp
    var chosenLineH = 11.6f * dp
    for (sizeDp in floatArrayOf(10f, 9.2f, 8.6f, 8.2f)) {
        val sizePx = sizeDp * dp
        val lineH = sizePx * 1.34f
        val cacheKey = "$tabKey|${sizePx.toInt()}|${maxW.toInt()}"
        val lines = if (cacheKey == vitalsInfoCacheKey) {
            vitalsInfoCacheLines
        } else {
            textPaint.typeface = Typeface.DEFAULT
            textPaint.textSize = sizePx
            val built = ArrayList<VitalsInfoLine>()
            for ((idx, e) in entries.withIndex()) {
                built.add(VitalsInfoLine(e.term, isTerm = true, gapBefore = idx > 0))
                for (w in wrapVitalsInfoText(e.body, textPaint, maxW)) {
                    built.add(VitalsInfoLine(w, isTerm = false, gapBefore = false))
                }
            }
            vitalsInfoCacheKey = cacheKey
            vitalsInfoCacheLines = built
            built
        }
        val gaps = lines.count { it.gapBefore } * 5f * dp
        val needed = pad + 14f * dp + 8f * dp + lines.size * lineH + gaps + 16f * dp
        chosenLines = lines
        chosenSize = sizePx
        chosenLineH = lineH
        if (needed <= (bottom - top)) break
    }

    canvas.save()
    canvas.clipRect(vitalsInfoCardRect)
    textPaint.textAlign = Paint.Align.LEFT
    textPaint.typeface = Typeface.DEFAULT_BOLD
    textPaint.textSize = 10.4f * dp
    textPaint.color = Color.WHITE
    var y = top + pad + 10f * dp
    canvas.drawText(title, left + pad, y, textPaint)
    y += 8f * dp
    for (ln in chosenLines) {
        if (ln.gapBefore) y += 5f * dp
        y += chosenLineH
        if (ln.isTerm) {
            textPaint.typeface = Typeface.DEFAULT_BOLD
            textPaint.color = accent
        } else {
            textPaint.typeface = Typeface.DEFAULT
            textPaint.color = Color.argb(225, 214, 226, 242)
        }
        textPaint.textSize = chosenSize
        canvas.drawText(ln.text, left + pad, y, textPaint)
    }
    canvas.restore()

    textPaint.textAlign = Paint.Align.RIGHT
    textPaint.typeface = Typeface.MONOSPACE
    textPaint.textSize = 7.8f * dp
    textPaint.color = Color.argb(150, 190, 205, 225)
    canvas.drawText("TAP TO CLOSE", right - pad, bottom - 6f * dp, textPaint)
    textPaint.textAlign = Paint.Align.LEFT
}
