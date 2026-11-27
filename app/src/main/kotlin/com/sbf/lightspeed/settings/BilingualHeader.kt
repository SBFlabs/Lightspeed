package com.sbf.lightspeed.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp
import com.sbf.lightspeed.system.LightspeedLocaleEngine

/**
 * Header title that honours Real-World Bilingual mode.
 *
 * Use ONLY for main titles, accordion headers and section headers — never for compact buttons,
 * chips or sliders.
 *
 *  • Bilingual off → plain [title].
 *  • Bilingual on  → [title] on the main line, with the secondary language rendered cleanly as
 *    faint subtext underneath (matching Vocabulary Bilingual's subtext style, never inline brackets).
 */
@Composable
fun BilingualTitleText(
    title: String,
    fontSize: TextUnit,
    fontWeight: FontWeight,
    color: Color,
    modifier: Modifier = Modifier,
    @Suppress("UNUSED_PARAMETER") preferStacked: Boolean = true,
    maxLines: Int = Int.MAX_VALUE,
    overflow: TextOverflow = TextOverflow.Clip,
    minFontSize: TextUnit? = null
) {
    val secondary = LightspeedLocaleEngine.secondaryFor(title)
    var effectiveSize by remember(title, fontSize, minFontSize) { mutableStateOf(fontSize) }
    val onPrimaryLayout: ((androidx.compose.ui.text.TextLayoutResult) -> Unit) = { res ->
        if (minFontSize != null && maxLines == 1 && res.didOverflowWidth && effectiveSize.value > minFontSize.value) {
            effectiveSize = (effectiveSize.value - 1f).coerceAtLeast(minFontSize.value).sp
        }
    }
    if (secondary == null) {
        Text(
            text = title,
            fontWeight = fontWeight,
            fontSize = effectiveSize,
            lineHeight = effectiveSize * 1.28f,
            color = color,
            maxLines = maxLines,
            overflow = overflow,
            onTextLayout = onPrimaryLayout,
            modifier = modifier
        )
        return
    }
    Column(modifier = modifier) {
        Text(
            text = title,
            fontWeight = fontWeight,
            fontSize = effectiveSize,
            lineHeight = effectiveSize * 1.28f,
            color = color,
            maxLines = maxLines,
            overflow = overflow,
            onTextLayout = onPrimaryLayout
        )
        Text(
            text = secondary,
            fontSize = effectiveSize * 0.72f,
            lineHeight = effectiveSize * 0.96f,
            fontWeight = FontWeight.Normal,
            color = color.copy(alpha = 0.56f),
            maxLines = maxLines,
            overflow = overflow
        )
    }
}
