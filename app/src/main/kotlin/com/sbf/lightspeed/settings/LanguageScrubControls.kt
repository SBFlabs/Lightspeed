package com.sbf.lightspeed.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.MenuBook
import androidx.compose.material.icons.filled.Translate
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import com.sbf.lightspeed.R
import com.sbf.lightspeed.system.LightspeedHapticEngine
import com.sbf.lightspeed.system.LightspeedLanguageEngine
import com.sbf.lightspeed.system.LightspeedLanguageEngine.LanguageMode
import com.sbf.lightspeed.system.LightspeedLocaleEngine
import com.sbf.lightspeed.system.LightspeedLocaleEngine.AppLanguage
import com.sbf.lightspeed.system.LightspeedLocaleEngine.BadgeTone
import com.sbf.lightspeed.system.lsString
import com.sbf.lightspeed.ui.theme.LightspeedStyle
import com.sbf.lightspeed.ui.theme.LightspeedTokens
import kotlin.math.abs

/**
 * Compact, uniform language badge (no country flags): `EN`, `PT`, `ES`, … in a rounded chip.
 * [language] == null renders the pinned **Bilingual** badge. New scripts (e.g. a green `ع`) just add an
 * [AppLanguage] entry — the badge picks up `badge` and `tone` automatically.
 */
@Composable
fun LanguageBadge(
    language: AppLanguage?,
    selected: Boolean,
    modifier: Modifier = Modifier,
    onPrimaryRow: Boolean = false
) {
    val lsColors = LightspeedStyle.colors
    val scheme = MaterialTheme.colorScheme
    val tone = language?.tone ?: BadgeTone.NEUTRAL
    val accent = when (tone) {
        BadgeTone.NEUTRAL -> lsColors.textPrimary
        BadgeTone.GREEN -> LightspeedTokens.StatusOk
    }
    val container = when {
        selected && onPrimaryRow -> scheme.onPrimary.copy(alpha = 0.22f)
        selected -> scheme.primary
        else -> accent.copy(alpha = 0.14f)
    }
    val content = if (selected) scheme.onPrimary else accent.copy(alpha = 0.9f)
    Box(
        modifier = modifier
            .size(width = 34.dp, height = 24.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(container),
        contentAlignment = Alignment.Center
    ) {
        if (language == null) {
            Icon(Icons.Default.Translate, contentDescription = null, tint = content, modifier = Modifier.size(15.dp))
        } else {
            Text(text = language.badge, color = content, fontSize = 11.sp, fontWeight = FontWeight.Bold, maxLines = 1)
        }
    }
}

private const val AXIS_NONE = 0
private const val AXIS_HORIZONTAL = 1
private const val AXIS_VERTICAL = 2
private const val AXIS_LOCK_DP = 14f
private const val DROPDOWN_ROW_DP = 38f

/**
 * Guidebook icon in the Central Command header.
 *
 * Tap → opens the Guidebook. Long-press (after the haptic locks) then scrub:
 *  • horizontal → Vocabulary mode (Space Lore / Bilingual / Clear Comms),
 *  • vertical drop-down → Real-World Language picker (Bilingual pinned on top, then EN / PT / ES).
 *
 * The scrub is gated behind the long-press, so the header's swipe-down dismiss stays intact.
 */
@Composable
fun GuidebookLanguageButton(onOpenGuidebook: () -> Unit) {
    val context = LocalContext.current
    val density = LocalDensity.current
    val colorScheme = MaterialTheme.colorScheme
    var showHud by remember { mutableStateOf(false) }
    var showBilingualPairDialog by remember { mutableStateOf(false) }
    var vocabIndex by remember { mutableIntStateOf(-1) }
    var languageIndex by remember { mutableIntStateOf(-1) }
    var axis by remember { mutableIntStateOf(AXIS_NONE) }

    Box {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(colorScheme.surfaceVariant.copy(alpha = 0.5f))
                .clickable { onOpenGuidebook() }
                .pointerInput(Unit) {
                    var startX = 0f
                    var startY = 0f
                    detectDragGesturesAfterLongPress(
                        onDragStart = { offset ->
                            startX = offset.x
                            startY = offset.y
                            LightspeedHapticEngine.heavyClick(context) // long-press lock
                            axis = AXIS_NONE
                            vocabIndex = vocabIndexOf(LightspeedLanguageEngine.mode)
                            languageIndex = LightspeedLocaleEngine.pickerIndex()
                            showHud = true
                        },
                        onDragEnd = {
                            showHud = false
                            when (axis) {
                                AXIS_HORIZONTAL -> {
                                    val mode = vocabModeAt(vocabIndex)
                                    if (mode != null && LightspeedLanguageEngine.mode != mode) {
                                        LightspeedLanguageEngine.setMode(context, mode)
                                        LightspeedHapticEngine.click(context)
                                    }
                                }
                                AXIS_VERTICAL -> {
                                    if (languageIndex == 0) {
                                        LightspeedLocaleEngine.selectBilingual(context)
                                        LightspeedHapticEngine.click(context)
                                        showBilingualPairDialog = true
                                    } else if (languageIndex > 0 && languageIndex != LightspeedLocaleEngine.pickerIndex()) {
                                        LightspeedLocaleEngine.selectPickerIndex(context, languageIndex)
                                        LightspeedHapticEngine.click(context)
                                    }
                                }
                            }
                        },
                        onDragCancel = { showHud = false },
                        onDrag = { change, _ ->
                            val d = density.density
                            val dx = (change.position.x - startX) / d
                            val dy = (change.position.y - startY) / d
                            if (axis == AXIS_NONE && maxOf(abs(dx), abs(dy)) > AXIS_LOCK_DP) {
                                // Downward-dominant drag = language drop-down; anything else = vocabulary scrub.
                                axis = if (dy > 0f && abs(dy) > abs(dx)) AXIS_VERTICAL else AXIS_HORIZONTAL
                            }
                            when (axis) {
                                AXIS_HORIZONTAL -> {
                                    val xDp = change.position.x / d
                                    val newIndex = when {
                                        xDp < -15 -> 0
                                        xDp > 55 -> 2
                                        else -> 1
                                    }
                                    if (newIndex != vocabIndex) {
                                        vocabIndex = newIndex
                                        LightspeedHapticEngine.tick(context)
                                    }
                                }
                                AXIS_VERTICAL -> {
                                    val last = LightspeedLocaleEngine.pickerCount - 1
                                    val newIndex = ((dy - AXIS_LOCK_DP) / DROPDOWN_ROW_DP + 0.5f).toInt().coerceIn(0, last)
                                    if (newIndex != languageIndex) {
                                        languageIndex = newIndex
                                        LightspeedHapticEngine.tick(context)
                                    }
                                }
                            }
                        }
                    )
                },
            contentAlignment = Alignment.Center
        ) {
            Icon(
                Icons.AutoMirrored.Outlined.MenuBook,
                contentDescription = lsString(R.string.ls_cd_guidebook),
                tint = colorScheme.primary,
                modifier = Modifier.size(20.dp)
            )
        }

        if (showHud) {
            VocabularyScrubHud(activeIndex = vocabIndex, dimmed = axis == AXIS_VERTICAL)
            // Anchored slightly to the LEFT of the icon so a right thumb never covers the labels.
            Popup(
                alignment = Alignment.TopEnd,
                offset = IntOffset(with(density) { (-30).dp.roundToPx() }, with(density) { 46.dp.roundToPx() })
            ) {
                LanguageDropdownHud(activeIndex = languageIndex, dimmed = axis == AXIS_HORIZONTAL)
            }
        }

        if (showBilingualPairDialog) {
            BilingualPairPickerDialog(onDismiss = { showBilingualPairDialog = false })
        }
    }
}

/**
 * Pop-up dialog shown immediately when the user chooses Real-World Bilingual from the Guidebook
 * drop-down scrub, letting them pick their Main Language and Second Language (faint subtext)
 * with a live header preview.
 */
@Composable
fun BilingualPairPickerDialog(onDismiss: () -> Unit) {
    val context = LocalContext.current
    val lsColors = LightspeedStyle.colors
    val colorScheme = MaterialTheme.colorScheme
    val state = LightspeedLocaleEngine.state

    DeckDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                LanguageBadge(language = null, selected = true)
                Text(
                    text = lsString(R.string.ls_bilingual_dialog_title),
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = lsColors.textPrimary
                )
            }
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = lsString(R.string.ls_bilingual_dialog_sub),
                    fontSize = 12.sp,
                    lineHeight = 16.sp,
                    color = lsColors.textSecondary
                )

                @Composable
                fun LanguageChoiceGroup(
                    header: String,
                    selected: AppLanguage,
                    onPick: (AppLanguage) -> Unit
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                        Text(
                            text = header.uppercase(),
                            fontSize = 10.5.sp,
                            fontWeight = FontWeight.Bold,
                            color = colorScheme.primary,
                            letterSpacing = 0.8.sp
                        )
                        AppLanguage.entries.forEach { lang ->
                            val isSelected = lang == selected
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(
                                        if (isSelected) colorScheme.primary.copy(alpha = 0.18f)
                                        else lsColors.textPrimary.copy(alpha = 0.05f)
                                    )
                                    .border(
                                        width = if (isSelected) 1.dp else 0.7.dp,
                                        color = if (isSelected) colorScheme.primary.copy(alpha = 0.65f)
                                        else lsColors.textPrimary.copy(alpha = 0.10f),
                                        shape = RoundedCornerShape(12.dp)
                                    )
                                    .clickable {
                                        LightspeedHapticEngine.tick(context)
                                        onPick(lang)
                                    }
                                    .padding(horizontal = 10.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                LanguageBadge(language = lang, selected = isSelected)
                                Text(
                                    text = lang.label,
                                    fontSize = 13.sp,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                    color = lsColors.textPrimary,
                                    maxLines = 1
                                )
                            }
                        }
                    }
                }

                LanguageChoiceGroup(
                    header = lsString(R.string.ls_lang_pair_primary),
                    selected = state.primary,
                    onPick = { LightspeedLocaleEngine.setPrimary(context, it) }
                )

                LanguageChoiceGroup(
                    header = lsString(R.string.ls_lang_pair_secondary),
                    selected = state.secondary,
                    onPick = { LightspeedLocaleEngine.setSecondary(context, it) }
                )

                // Live preview of how main titles & accordion headers will look
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(lsColors.textPrimary.copy(alpha = 0.05f))
                        .border(0.8.dp, lsColors.textPrimary.copy(alpha = 0.12f), RoundedCornerShape(12.dp))
                        .padding(horizontal = 12.dp, vertical = 9.dp),
                    verticalArrangement = Arrangement.spacedBy(3.dp)
                ) {
                    Text(
                        text = lsString(R.string.ls_bilingual_preview_label),
                        fontSize = 9.5.sp,
                        fontWeight = FontWeight.Bold,
                        color = lsColors.textSecondary,
                        letterSpacing = 0.8.sp
                    )
                    BilingualTitleText(
                        title = lsString(R.string.vocab_vessel_central_command),
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = lsColors.textPrimary
                    )
                }
            }
        },
        confirmButton = {
            DeckButton(
                text = lsString(R.string.ls_done),
                isActive = true,
                onClick = {
                    LightspeedHapticEngine.click(context)
                    onDismiss()
                }
            )
        },
        dismissButton = {
            if (state.bilingual) {
                DeckButton(
                    text = lsString(R.string.ls_bilingual_turn_off),
                    onClick = {
                        LightspeedHapticEngine.tick(context)
                        LightspeedLocaleEngine.disableBilingual(context)
                        onDismiss()
                    }
                )
            }
        }
    )
}

/**
 * Clean full Language Picker dialog opened when tapping the Guidebook language wheel.
 * Lets the user pick any real-world language directly or jump straight into Bilingual mode.
 */
@Composable
fun LanguagePickerDialog(
    onDismiss: () -> Unit,
    onOpenBilingualPair: () -> Unit
) {
    val context = LocalContext.current
    val lsColors = LightspeedStyle.colors
    val colorScheme = MaterialTheme.colorScheme
    val state = LightspeedLocaleEngine.state

    DeckDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = lsString(R.string.ls_lang_picker_title),
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                color = lsColors.textPrimary
            )
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = lsString(R.string.ls_lang_picker_sub),
                    fontSize = 12.sp,
                    lineHeight = 16.sp,
                    color = lsColors.textSecondary
                )

                // Bilingual mode entry pinned at top
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(
                            if (state.bilingual) colorScheme.primary.copy(alpha = 0.18f)
                            else lsColors.textPrimary.copy(alpha = 0.05f)
                        )
                        .border(
                            width = if (state.bilingual) 1.dp else 0.7.dp,
                            color = if (state.bilingual) colorScheme.primary.copy(alpha = 0.65f)
                            else lsColors.textPrimary.copy(alpha = 0.10f),
                            shape = RoundedCornerShape(12.dp)
                        )
                        .clickable {
                            LightspeedHapticEngine.tick(context)
                            LightspeedLocaleEngine.selectBilingual(context)
                            onDismiss()
                            onOpenBilingualPair()
                        }
                        .padding(horizontal = 10.dp, vertical = 9.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    LanguageBadge(language = null, selected = state.bilingual)
                    Text(
                        text = if (state.bilingual) {
                            "${lsString(R.string.ls_lang_bilingual)} (${state.primary.badge} + ${state.secondary.badge})"
                        } else {
                            lsString(R.string.ls_lang_bilingual)
                        },
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = lsColors.textPrimary,
                        maxLines = 1
                    )
                }

                // Single-language choices
                AppLanguage.entries.forEach { lang ->
                    val isSelected = !state.bilingual && lang == state.primary
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(
                                if (isSelected) colorScheme.primary.copy(alpha = 0.18f)
                                else lsColors.textPrimary.copy(alpha = 0.05f)
                            )
                            .border(
                                width = if (isSelected) 1.dp else 0.7.dp,
                                color = if (isSelected) colorScheme.primary.copy(alpha = 0.65f)
                                else lsColors.textPrimary.copy(alpha = 0.10f),
                                shape = RoundedCornerShape(12.dp)
                            )
                            .clickable {
                                LightspeedHapticEngine.click(context)
                                LightspeedLocaleEngine.selectSingle(context, lang)
                                onDismiss()
                            }
                            .padding(horizontal = 10.dp, vertical = 9.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        LanguageBadge(language = lang, selected = isSelected)
                        Text(
                            text = lang.label,
                            fontSize = 13.sp,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                            color = lsColors.textPrimary,
                            maxLines = 1
                        )
                    }
                }
            }
        },
        confirmButton = {
            DeckButton(
                text = lsString(R.string.ls_done),
                isActive = true,
                onClick = onDismiss
            )
        }
    )
}

private fun vocabIndexOf(mode: LanguageMode): Int = when (mode) {
    LanguageMode.VESSEL_LORE -> 0
    LanguageMode.CO_PILOT -> 1
    LanguageMode.CLEAR_COMMS -> 2
}

private fun vocabModeAt(index: Int): LanguageMode? = when (index) {
    0 -> LanguageMode.VESSEL_LORE
    1 -> LanguageMode.CO_PILOT
    2 -> LanguageMode.CLEAR_COMMS
    else -> null
}

@Composable
private fun VocabularyScrubHud(activeIndex: Int, dimmed: Boolean) {
    val colorScheme = MaterialTheme.colorScheme
    Popup(alignment = Alignment.TopCenter, offset = IntOffset(0, -180)) {
        Row(
            modifier = Modifier
                .clip(RoundedCornerShape(24.dp))
                .background(LightspeedTokens.TacticalMenuSurface.copy(alpha = if (dimmed) 0.6f else 0.95f))
                .padding(6.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            listOf(
                lsString(R.string.ls_vocab_space_lore),
                lsString(R.string.ls_vocab_bilingual),
                lsString(R.string.ls_vocab_clear_comms)
            ).forEachIndexed { index, title ->
                val selected = activeIndex == index
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(16.dp))
                        .background(if (selected) colorScheme.primary else Color.Transparent)
                        .padding(horizontal = 16.dp, vertical = 10.dp)
                ) {
                    Text(
                        text = title,
                        color = if (selected) colorScheme.onPrimary else LightspeedStyle.colors.textPrimary.copy(alpha = 0.7f),
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

/** Vertical drop-down: pinned **Bilingual**, then `EN — English`, `PT — Portuguese (Português · BR)`, … */
@Composable
private fun LanguageDropdownHud(activeIndex: Int, dimmed: Boolean) {
    val colorScheme = MaterialTheme.colorScheme
    Column(
        modifier = Modifier
            .width(IntrinsicSize.Max)
            .clip(RoundedCornerShape(20.dp))
            .background(LightspeedTokens.TacticalMenuSurface.copy(alpha = if (dimmed) 0.6f else 0.95f))
            .padding(6.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        val options: List<AppLanguage?> = listOf<AppLanguage?>(null) + AppLanguage.entries
        options.forEachIndexed { index, language ->
            val selected = index == activeIndex
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(if (selected) colorScheme.primary else Color.Transparent)
                    .height(DROPDOWN_ROW_DP.dp - 2.dp)
                    .padding(horizontal = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                LanguageBadge(language = language, selected = selected, onPrimaryRow = true)
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    text = language?.label ?: lsString(R.string.ls_lang_bilingual),
                    color = if (selected) colorScheme.onPrimary else LightspeedStyle.colors.textPrimary.copy(alpha = 0.8f),
                    fontSize = 13.sp,
                    fontWeight = if (selected || language == null) FontWeight.Bold else FontWeight.Medium,
                    maxLines = 1
                )
            }
        }
    }
}
