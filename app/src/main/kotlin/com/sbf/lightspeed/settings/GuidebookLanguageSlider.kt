package com.sbf.lightspeed.settings

import android.view.HapticFeedbackConstants
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.filled.Translate
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sbf.lightspeed.R
import com.sbf.lightspeed.system.LightspeedHapticEngine
import com.sbf.lightspeed.system.LightspeedLocaleEngine
import com.sbf.lightspeed.system.LightspeedLocaleEngine.AppLanguage
import com.sbf.lightspeed.system.lsString
import com.sbf.lightspeed.ui.theme.LightspeedStyle
import kotlinx.coroutines.delay
import kotlin.math.abs

/**
 * Compact horizontal rolling language wheel + Bilingual toggle button with morphing
 * second-language letters at its center.
 *
 *  • Left:  Swipe horizontally left/right to roll through EN · PT · ES like a horizontal
 *           thumbwheel / counter with `CLOCK_TICK` haptics on every detent.
 *  • Right: Compact Bilingual toggle button; tap to open [BilingualPairPickerDialog]. When
 *           Bilingual is active, distinctive letters of the second language morph at its center.
 */
@Composable
fun GuidebookLanguageSlider(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val state = LightspeedLocaleEngine.state
    var showLanguageDialog by remember { mutableStateOf(false) }
    var showBilingualDialog by remember { mutableStateOf(false) }

    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        HorizontalLanguageWheel(
            state = state,
            onTapOpenPicker = {
                LightspeedHapticEngine.tick(context)
                showLanguageDialog = true
            },
            onStepLanguage = { next ->
                if (state.bilingual) {
                    LightspeedLocaleEngine.setPrimary(context, next)
                } else {
                    LightspeedLocaleEngine.selectSingle(context, next)
                }
            },
            modifier = Modifier.weight(1f)
        )

        BilingualMorphToggleButton(
            state = state,
            onToggleClick = {
                if (state.bilingual) {
                    LightspeedHapticEngine.click(context)
                    LightspeedLocaleEngine.disableBilingual(context)
                } else {
                    LightspeedHapticEngine.tick(context)
                    LightspeedLocaleEngine.selectBilingual(context)
                    showBilingualDialog = true
                }
            },
            onLongPressEditPair = {
                LightspeedHapticEngine.heavyClick(context)
                if (!state.bilingual) {
                    LightspeedLocaleEngine.selectBilingual(context)
                }
                showBilingualDialog = true
            }
        )

        RtlLayoutPillButton(
            rtlActive = state.rtlLayout,
            onClick = {
                LightspeedHapticEngine.tick(context)
                LightspeedLocaleEngine.toggleRtlLayout(context)
            }
        )
    }

    if (showLanguageDialog) {
        LanguagePickerDialog(
            onDismiss = { showLanguageDialog = false },
            onOpenBilingualPair = { showBilingualDialog = true }
        )
    }

    if (showBilingualDialog) {
        BilingualPairPickerDialog(onDismiss = { showBilingualDialog = false })
    }
}

/**
 * Horizontal rolling counter wheel:
 *  • Swipe horizontally (or long-press & drag up/down or left/right) to click through languages
 *    with `CLOCK_TICK` haptics.
 *  • Tap the center pill to open the full [LanguagePickerDialog].
 */
@Composable
private fun HorizontalLanguageWheel(
    state: LightspeedLocaleEngine.LocaleState,
    onTapOpenPicker: () -> Unit,
    onStepLanguage: (AppLanguage) -> Unit,
    modifier: Modifier = Modifier
) {
    val view = LocalView.current
    val density = LocalDensity.current
    val lsColors = LightspeedStyle.colors
    val colorScheme = MaterialTheme.colorScheme
    val languages = AppLanguage.entries
    val currentIndex = state.primary.ordinal
    val prevLang = languages[(currentIndex - 1 + languages.size) % languages.size]
    val nextLang = languages[(currentIndex + 1) % languages.size]
    var rollDirection by remember { mutableIntStateOf(1) }

    val stepBy: (Int) -> Unit = { delta ->
        if (delta != 0) {
            rollDirection = if (delta > 0) 1 else -1
            val nextIdx = (LightspeedLocaleEngine.state.primary.ordinal + delta + languages.size) % languages.size
            view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
            onStepLanguage(languages[nextIdx])
        }
    }

    val shape = RoundedCornerShape(12.dp)
    val detentPx = with(density) { 42.dp.toPx() }
    val longPressDetentPx = with(density) { 28.dp.toPx() }

    Box(
        modifier = modifier
            .height(44.dp)
            .clip(shape)
            .background(lsColors.textPrimary.copy(alpha = 0.06f))
            .border(0.9.dp, lsColors.textPrimary.copy(alpha = 0.14f), shape)
            .pointerInput(Unit) {
                var accum = 0f
                detectHorizontalDragGestures(
                    onDragStart = { accum = 0f },
                    onDragEnd = { accum = 0f },
                    onDragCancel = { accum = 0f },
                    onHorizontalDrag = { change, dragAmount ->
                        change.consume()
                        accum -= dragAmount
                        while (abs(accum) >= detentPx) {
                            val dir = if (accum > 0f) 1 else -1
                            accum -= dir * detentPx
                            stepBy(dir)
                        }
                    }
                )
            }
            .pointerInput(Unit) {
                var accum = 0f
                detectDragGesturesAfterLongPress(
                    onDragStart = {
                        accum = 0f
                        view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                    },
                    onDragEnd = { accum = 0f },
                    onDragCancel = { accum = 0f },
                    onDrag = { change, dragAmount ->
                        change.consume()
                        val primaryDelta = if (abs(dragAmount.y) >= abs(dragAmount.x)) -dragAmount.y else -dragAmount.x
                        accum += primaryDelta
                        while (abs(accum) >= longPressDetentPx) {
                            val dir = if (accum > 0f) 1 else -1
                            accum -= dir * longPressDetentPx
                            stepBy(dir)
                        }
                    }
                )
            }
    ) {
        // Horizontal ticks + peeking prev/next badges flanking the active centered language
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight()
                .padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            // Left peek (tap to step backward)
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .clickable { stepBy(-1) }
                    .padding(horizontal = 6.dp, vertical = 6.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = prevLang.badge,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = lsColors.textPrimary.copy(alpha = 0.32f),
                    maxLines = 1
                )
            }

            // Center active rolling slot (tap to open full Language Picker dialog)
            Box(
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 4.dp)
                    .clip(RoundedCornerShape(9.dp))
                    .background(colorScheme.primary.copy(alpha = 0.14f))
                    .border(0.8.dp, colorScheme.primary.copy(alpha = 0.42f), RoundedCornerShape(9.dp))
                    .clickable { onTapOpenPicker() }
                    .padding(horizontal = 8.dp, vertical = 5.dp),
                contentAlignment = Alignment.Center
            ) {
                AnimatedContent(
                    targetState = state.primary,
                    transitionSpec = {
                        val slide = if (rollDirection >= 0) 1 else -1
                        (slideInHorizontally(tween(150)) { it * slide / 2 } + fadeIn(tween(130))) togetherWith
                            (slideOutHorizontally(tween(130)) { -it * slide / 2 } + fadeOut(tween(110)))
                    },
                    label = "horizontal_language_wheel"
                ) { lang ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(7.dp)
                    ) {
                        LanguageBadge(language = lang, selected = true)
                        Text(
                            text = lang.nativeName ?: lang.englishName,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = lsColors.textPrimary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }

            // Right peek (tap to step forward)
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .clickable { stepBy(1) }
                    .padding(horizontal = 6.dp, vertical = 6.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = nextLang.badge,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = lsColors.textPrimary.copy(alpha = 0.32f),
                    maxLines = 1
                )
            }
        }

        // Subtle left & right horizontal drum shading
        Box(
            modifier = Modifier
                .width(16.dp)
                .fillMaxHeight()
                .align(Alignment.CenterStart)
                .background(Brush.horizontalGradient(listOf(Color.Black.copy(alpha = 0.24f), Color.Transparent)))
        )
        Box(
            modifier = Modifier
                .width(16.dp)
                .fillMaxHeight()
                .align(Alignment.CenterEnd)
                .background(Brush.horizontalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.24f))))
        )
    }
}

/**
 * Compact Bilingual toggle button beside the horizontal language wheel.
 * When Bilingual is active, distinctive letters of the chosen second language smoothly morph
 * at the center of the button.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun BilingualMorphToggleButton(
    state: LightspeedLocaleEngine.LocaleState,
    onToggleClick: () -> Unit,
    onLongPressEditPair: () -> Unit
) {
    val lsColors = LightspeedStyle.colors
    val colorScheme = MaterialTheme.colorScheme
    val active = state.bilingual
    val glyphs = state.secondary.morphGlyphs
    var glyphIndex by remember(state.secondary) { mutableIntStateOf(0) }

    LaunchedEffect(active, state.secondary) {
        if (!active) return@LaunchedEffect
        while (true) {
            delay(1500L)
            glyphIndex = (glyphIndex + 1) % glyphs.size
        }
    }

    val shape = RoundedCornerShape(12.dp)
    Box(
        modifier = Modifier
            .height(44.dp)
            .width(68.dp)
            .clip(shape)
            .background(
                if (active) colorScheme.primary.copy(alpha = 0.20f)
                else lsColors.textPrimary.copy(alpha = 0.06f)
            )
            .border(
                width = if (active) 1.1.dp else 0.9.dp,
                color = if (active) colorScheme.primary.copy(alpha = 0.75f)
                else lsColors.textPrimary.copy(alpha = 0.14f),
                shape = shape
            )
            .combinedClickable(
                onClick = onToggleClick,
                onLongClick = onLongPressEditPair
            )
            .padding(horizontal = 4.dp, vertical = 2.dp),
        contentAlignment = Alignment.Center
    ) {
        if (active) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                AnimatedContent(
                    targetState = glyphs[glyphIndex % glyphs.size],
                    transitionSpec = {
                        (fadeIn(tween(320)) + scaleIn(initialScale = 0.72f, animationSpec = tween(320))) togetherWith
                            (fadeOut(tween(240)) + scaleOut(targetScale = 1.18f, animationSpec = tween(240)))
                    },
                    label = "bilingual_morph_letter"
                ) { glyph ->
                    Text(
                        text = glyph,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = colorScheme.primary,
                        maxLines = 1
                    )
                }
                Text(
                    text = "${state.primary.badge}+${state.secondary.badge}",
                    fontSize = 8.sp,
                    fontWeight = FontWeight.Bold,
                    color = lsColors.textPrimary.copy(alpha = 0.75f),
                    maxLines = 1
                )
            }
        } else {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Translate,
                    contentDescription = lsString(R.string.ls_lang_bilingual),
                    tint = lsColors.textPrimary.copy(alpha = 0.75f),
                    modifier = Modifier.size(16.dp)
                )
                Text(
                    text = lsString(R.string.ls_lang_bilingual),
                    fontSize = 8.5.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = lsColors.textPrimary.copy(alpha = 0.7f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
private fun RtlLayoutPillButton(
    rtlActive: Boolean,
    onClick: () -> Unit
) {
    val lsColors = LightspeedStyle.colors
    val colorScheme = MaterialTheme.colorScheme
    val shape = RoundedCornerShape(12.dp)
    Box(
        modifier = Modifier
            .height(44.dp)
            .width(48.dp)
            .clip(shape)
            .background(
                if (rtlActive) colorScheme.primary.copy(alpha = 0.20f)
                else lsColors.textPrimary.copy(alpha = 0.06f)
            )
            .border(
                width = if (rtlActive) 1.1.dp else 0.9.dp,
                color = if (rtlActive) colorScheme.primary.copy(alpha = 0.75f)
                else lsColors.textPrimary.copy(alpha = 0.14f),
                shape = shape
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 4.dp, vertical = 2.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = Icons.Default.SwapHoriz,
                contentDescription = lsString(R.string.ls_rtl_layout_title),
                tint = if (rtlActive) colorScheme.primary else lsColors.textPrimary.copy(alpha = 0.75f),
                modifier = Modifier.size(16.dp)
            )
            Text(
                text = lsString(R.string.ls_rtl_chip_short),
                fontSize = 8.5.sp,
                fontWeight = if (rtlActive) FontWeight.Bold else FontWeight.SemiBold,
                color = if (rtlActive) colorScheme.primary else lsColors.textPrimary.copy(alpha = 0.7f),
                maxLines = 1
            )
        }
    }
}
