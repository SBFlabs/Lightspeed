package com.sbf.lightspeed.settings

import android.content.Context
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sbf.lightspeed.R
import com.sbf.lightspeed.system.LightspeedHapticEngine
import com.sbf.lightspeed.system.LightspeedLocaleEngine
import com.sbf.lightspeed.system.lsString
import com.sbf.lightspeed.ui.theme.LightspeedStyle
import kotlinx.coroutines.delay

/** Active Central Command window / tab scope used to tailor rotating contextual tips. */
enum class CentralCommandTipWindow {
    LEFT_TAB,
    HUD_STRIP_TAB,
    RIGHT_TAB,
    BLUEPRINT,
    TYPOGRAPHY,
    FLIGHT_DECK
}

private fun tipResIdsFor(window: CentralCommandTipWindow): List<Int> = when (window) {
    CentralCommandTipWindow.LEFT_TAB -> listOf(
        R.string.cc_tip_left_deflector,
        R.string.cc_tip_tab_blueprint,
        R.string.cc_tip_guidebook_hold,
        R.string.cc_tip_tap_outside,
        R.string.cc_tip_header_deck
    )
    CentralCommandTipWindow.HUD_STRIP_TAB -> listOf(
        R.string.cc_tip_guidebook_hold,
        R.string.cc_tip_tab_blueprint,
        R.string.cc_tip_hud_strip,
        R.string.cc_tip_tap_outside,
        R.string.cc_tip_header_deck
    )
    CentralCommandTipWindow.RIGHT_TAB -> listOf(
        R.string.cc_tip_right_deflector,
        R.string.cc_tip_tab_blueprint,
        R.string.cc_tip_tap_outside,
        R.string.cc_tip_guidebook_hold,
        R.string.cc_tip_header_deck
    )
    CentralCommandTipWindow.BLUEPRINT -> listOf(
        R.string.cc_tip_window_blueprint,
        R.string.cc_tip_tap_outside
    )
    CentralCommandTipWindow.TYPOGRAPHY -> listOf(
        R.string.cc_tip_window_typography,
        R.string.cc_tip_tap_outside
    )
    CentralCommandTipWindow.FLIGHT_DECK -> listOf(
        R.string.cc_tip_window_deck,
        R.string.cc_tip_tap_outside
    )
}

/**
 * Lightweight, window-aware & tab-aware rotating tip strip for Central Command.
 * Tap to open all tips for the current window; long-press to step to the next tip manually.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun CentralCommandContextualTipBar(
    context: Context,
    windowContext: CentralCommandTipWindow,
    modifier: Modifier = Modifier
) {
    val lsColors = LightspeedStyle.colors
    val primary = MaterialTheme.colorScheme.primary
    val localeState = LightspeedLocaleEngine.state
    val isArabicScript = localeState.primary == LightspeedLocaleEngine.AppLanguage.AR
    val activeLayoutDir = LocalLayoutDirection.current
    val tipStripDir = if (isArabicScript) LayoutDirection.Rtl else activeLayoutDir
    val tipResIds = remember(windowContext) { tipResIdsFor(windowContext) }
    val resolvedTips = remember(tipResIds, localeState) { tipResIds.map { lsString(it) } }
    var tipIndex by remember(windowContext) { mutableIntStateOf(0) }
    var showAllTipsDialog by remember { mutableStateOf(false) }

    LaunchedEffect(windowContext, tipResIds.size) {
        while (true) {
            delay(6500L)
            tipIndex = (tipIndex + 1) % tipResIds.size
        }
    }

    val safeIndex = tipIndex % resolvedTips.size
    val shape = RoundedCornerShape(8.dp)

    CompositionLocalProvider(LocalLayoutDirection provides tipStripDir) {
        Row(
            modifier = modifier
                .fillMaxWidth()
                .clip(shape)
                .background(lsColors.textPrimary.copy(alpha = 0.04f))
                .border(0.6.dp, lsColors.textPrimary.copy(alpha = 0.08f), shape)
                .combinedClickable(
                    onClick = {
                        LightspeedHapticEngine.tick(context)
                        showAllTipsDialog = true
                    },
                    onLongClick = {
                        LightspeedHapticEngine.tick(context)
                        tipIndex = (safeIndex + 1) % resolvedTips.size
                    }
                )
                .padding(horizontal = 8.dp, vertical = 5.dp)
                .heightIn(min = 24.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(
                text = "✦",
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                color = primary
            )
            AnimatedContent(
                targetState = resolvedTips[safeIndex],
                transitionSpec = { fadeIn(tween(220)) togetherWith fadeOut(tween(160)) },
                modifier = Modifier.weight(1f),
                label = "cc_contextual_tip"
            ) { tipText ->
                Text(
                    text = tipText,
                    fontSize = 10.5.sp,
                    lineHeight = 14.sp,
                    color = lsColors.textSecondary.copy(alpha = 0.88f),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Text(
                text = "${safeIndex + 1}/${resolvedTips.size}",
                fontSize = 9.5.sp,
                fontWeight = FontWeight.SemiBold,
                color = lsColors.textSecondary.copy(alpha = 0.55f),
                maxLines = 1
            )
        }
    }

    if (showAllTipsDialog) {
        DeckDialog(
            onDismissRequest = { showAllTipsDialog = false },
            scrollable = false,
            title = {
                Text(
                    text = lsString(R.string.cc_tip_all_title),
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                CompositionLocalProvider(LocalLayoutDirection provides tipStripDir) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 320.dp)
                            .verticalScrollWithEdgeFade(topFeatherDp = 16.dp, bottomFeatherDp = 18.dp)
                            .padding(vertical = 4.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        resolvedTips.forEach { tip ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(lsColors.textPrimary.copy(alpha = 0.05f))
                                    .border(0.7.dp, primary.copy(alpha = 0.20f), RoundedCornerShape(10.dp))
                                    .padding(horizontal = 10.dp, vertical = 8.dp),
                                horizontalArrangement = Arrangement.spacedBy(7.dp),
                                verticalAlignment = Alignment.Top
                            ) {
                                Text(
                                    text = "✦",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = primary
                                )
                                Text(
                                    text = tip,
                                    fontSize = 11.5.sp,
                                    lineHeight = 16.sp,
                                    color = lsColors.textPrimary.copy(alpha = 0.90f),
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = {
                DeckButton(
                    text = lsString(R.string.ls_close),
                    onClick = { showAllTipsDialog = false },
                    isActive = true
                )
            }
        )
    }
}
