package com.sbf.lightspeed.settings

import android.content.Context
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import com.sbf.lightspeed.system.LightspeedOverlayDismissCoordinator

/**
 * Attaches tap-to-close-top-layer and long-press-to-dismiss-all-stacked-overlays
 * behavior to a full-screen sibling dimmed scrim behind a modal card.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun Modifier.overlayScrimDismiss(
    context: Context,
    enabled: Boolean = true,
    onTapOutside: () -> Unit,
    onLongPressOutside: () -> Unit = {
        LightspeedOverlayDismissCoordinator.dismissAll(context)
    }
): Modifier = this.combinedClickable(
    enabled = enabled,
    interactionSource = remember { MutableInteractionSource() },
    indication = null,
    onClick = onTapOutside,
    onLongClick = onLongPressOutside
)

/**
 * Shields a sibling modal card in front of [overlayScrimDismiss] so taps or
 * long-presses on non-interactive card whitespace never fall through to the scrim.
 */
fun Modifier.blockScrimPassthrough(): Modifier = this.pointerInput(Unit) {
    awaitPointerEventScope {
        while (true) {
            awaitPointerEvent(PointerEventPass.Final)
        }
    }
}
