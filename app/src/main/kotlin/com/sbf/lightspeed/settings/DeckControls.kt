package com.sbf.lightspeed.settings

import android.graphics.drawable.ColorDrawable
import android.os.Build
import android.view.WindowManager
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.SwitchColors
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import com.sbf.lightspeed.ui.theme.LightspeedStyle
import com.sbf.lightspeed.ui.theme.LocalDeckTheme

@Composable
fun DeckDropdownAnchorBox(
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.(anchorWidth: Dp) -> Unit
) {
    var widthDp by remember { mutableStateOf(0.dp) }
    val density = LocalDensity.current
    Box(
        modifier = modifier
            .fillMaxWidth()
            .onSizeChanged { size ->
                widthDp = with(density) { size.width.toDp() }
            }
    ) {
        content(widthDp)
    }
}

/**
 * Central Command Shared Themed Controls (Task 1.5E).
 * Unifies modal dialogs, cards, and buttons with the active 12-theme optical engine.
 */

@Composable
fun DeckDialog(
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    title: (@Composable () -> Unit)? = null,
    text: (@Composable () -> Unit)? = null,
    icon: (@Composable () -> Unit)? = null,
    confirmButton: (@Composable () -> Unit)? = null,
    dismissButton: (@Composable () -> Unit)? = null,
    scrollable: Boolean = true,
    properties: DialogProperties = DialogProperties(usePlatformDefaultWidth = false),
    content: (@Composable ColumnScope.() -> Unit)? = null
) {
    // Compose Dialog creates a separate window whose LocalDensity/LocalLayoutDirection/LocalTextStyle
    // reset to Android OS system values. Re-provide active Lightspeed locals inside the dialog.
    val activeDensity = LocalDensity.current
    val activeLayoutDirection = LocalLayoutDirection.current
    val activeTextStyle = LocalTextStyle.current.merge(MaterialTheme.typography.bodyMedium)
    val targetAndroidDir = if (activeLayoutDirection == LayoutDirection.Rtl) {
        android.view.View.LAYOUT_DIRECTION_RTL
    } else {
        android.view.View.LAYOUT_DIRECTION_LTR
    }
    val maxDialogH = (LocalConfiguration.current.screenHeightDp.dp * 0.86f).coerceAtLeast(240.dp)
    Dialog(onDismissRequest = onDismissRequest, properties = properties) {
        CompositionLocalProvider(
            LocalDensity provides activeDensity,
            LocalLayoutDirection provides activeLayoutDirection,
            LocalTextStyle provides activeTextStyle
        ) {
            val snapshot = LocalDeckTheme.current
            val view = LocalView.current
            SideEffect {
                if (view.layoutDirection != targetAndroidDir) {
                    view.layoutDirection = targetAndroidDir
                }
                view.rootView?.let { root ->
                    if (root.layoutDirection != targetAndroidDir) {
                        root.layoutDirection = targetAndroidDir
                    }
                }
                val dialogWindow = (view.parent as? DialogWindowProvider)?.window
                dialogWindow?.setLayout(WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.MATCH_PARENT)
                dialogWindow?.setBackgroundDrawable(ColorDrawable(android.graphics.Color.TRANSPARENT))
                dialogWindow?.setDimAmount(snapshot.dimAmount)
                dialogWindow?.clearFlags(WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_ALT_FOCUSABLE_IM)
                dialogWindow?.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
            }

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .imePadding(),
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .overlayScrimDismiss(
                            context = view.context,
                            onTapOutside = {
                                if (properties.dismissOnClickOutside) {
                                    onDismissRequest()
                                }
                            }
                        )
                )
                Box(
                    modifier = Modifier
                        .fillMaxWidth(0.92f)
                        .widthIn(max = 480.dp)
                        .padding(vertical = 16.dp)
                        .blockScrimPassthrough(),
                    contentAlignment = Alignment.Center
                ) {
                    DeckGlassCardSurface(
                        modifier = modifier
                            .fillMaxWidth()
                            .heightIn(max = maxDialogH)
                            .wrapContentHeight(),
                        isFlyout = true
                    ) {
                    Box(modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.fillMaxWidth().padding(20.dp)) {
                            if (icon != null) {
                                Box(modifier = Modifier.align(Alignment.CenterHorizontally).padding(bottom = 12.dp, end = 24.dp)) {
                                    icon()
                                }
                            }
                            if (title != null) {
                                Box(modifier = Modifier.fillMaxWidth().padding(end = 30.dp)) {
                                    CompositionLocalProvider(
                                        LocalTextStyle provides MaterialTheme.typography.titleMedium.copy(
                                            fontWeight = FontWeight.Bold,
                                            color = LightspeedStyle.colors.textPrimary
                                        )
                                    ) { title() }
                                }
                                Spacer(modifier = Modifier.height(10.dp))
                            }
                            val bodyModifier = if (scrollable) {
                                Modifier
                                    .fillMaxWidth()
                                    .weight(1f, fill = false)
                                    .verticalScrollWithEdgeFade()
                            } else {
                                Modifier
                                    .fillMaxWidth()
                                    .weight(1f, fill = false)
                            }
                            Column(modifier = bodyModifier) {
                                if (text != null) {
                                    CompositionLocalProvider(
                                        LocalTextStyle provides MaterialTheme.typography.bodyMedium.copy(
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    ) { text() }
                                }
                                content?.invoke(this)
                            }
                            if (confirmButton != null || dismissButton != null) {
                                Spacer(modifier = Modifier.height(16.dp))
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.End,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    if (dismissButton != null) {
                                        dismissButton()
                                        Spacer(modifier = Modifier.width(8.dp))
                                    }
                                    confirmButton?.invoke()
                                }
                            }
                        }

                        IconButton(
                            onClick = onDismissRequest,
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .padding(6.dp)
                                .size(30.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Close",
                                tint = LightspeedStyle.colors.textSecondary,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }
                }
            }
        }
    }
}

@Composable
fun DeckCard(
    modifier: Modifier = Modifier,
    shape: Shape? = null,
    isFlyout: Boolean = false,
    onClick: (() -> Unit)? = null,
    contentPadding: PaddingValues = PaddingValues(0.dp),
    tint: Color = Color.Unspecified,
    content: @Composable ColumnScope.() -> Unit
) {
    val snapshot = LocalDeckTheme.current
    val cardCornerDp = (snapshot.cornerRadiusDp * 0.68f).coerceAtLeast(0f).dp
    val effectiveShape = shape ?: RoundedCornerShape(cardCornerDp)
    val isBlurAvailable = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
        (CentralCommandActivity.isCrossWindowBlurActive || CentralCommandBackdropBlur.backdropBitmap != null)

    val clickableMod = if (onClick != null) {
        Modifier.clickable(onClick = onClick)
    } else Modifier
    val tintMod = if (tint != Color.Unspecified) Modifier.background(tint) else Modifier

    Box(
        modifier = modifier
            .graphicsLayer {
                this.shape = effectiveShape
                this.clip = true
            }
            .deckGlassSurface(
                snapshot = snapshot,
                isBlurAvailable = isBlurAvailable,
                isFlyout = isFlyout,
                isCard = true,
                withNoise = false,
                shapeOverride = effectiveShape
            )
            .then(tintMod)
            .then(clickableMod)
            .padding(contentPadding)
    ) {
        Column(content = content)
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun DeckButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    isActive: Boolean = false,
    isDestructive: Boolean = false,
    isEnabled: Boolean = true,
    snapshot: DeckThemeSnapshot = LocalDeckTheme.current,
    contentPadding: PaddingValues = PaddingValues(horizontal = 14.dp, vertical = 9.dp),
    content: @Composable RowScope.() -> Unit
) {
    val isM3 = snapshot.styleId == DeckStylePresets.MATERIAL_3.styleId
    // Sharp tactical rectangle (8..12.dp) for glass decks; clean Material You 14.dp tonal container in M3
    val cornerRadius = if (isM3) 14.dp else (snapshot.cornerRadiusDp * 0.34f).coerceIn(8f, 12f).dp
    val shape = RoundedCornerShape(cornerRadius)
    val borderWidth = if (isM3) 0.dp else (snapshot.borderWidthDp * 0.75f).coerceIn(0.9f, 1.3f).dp

    val opticalColors = snapshot.resolveOpticalColors()
    val colorScheme = MaterialTheme.colorScheme
    val tintColor = if (isDestructive) colorScheme.error else opticalColors.readableTintColor
    val panelColor = opticalColors.panelColor
    val borderColor = if (isDestructive) colorScheme.error.copy(alpha = 0.55f) else opticalColors.borderColor
    val isLightPanel = panelColor.luminance() > 0.5f

    val contentColor = when {
        !isEnabled -> colorScheme.onSurface.copy(alpha = 0.38f)
        isM3 && isDestructive -> colorScheme.onErrorContainer
        isM3 && isActive -> colorScheme.onSecondaryContainer
        isM3 -> colorScheme.onSurface
        isDestructive -> colorScheme.error
        isActive -> if (isLightPanel) colorScheme.primary else tintColor
        isLightPanel -> colorScheme.onSurface.copy(alpha = 0.92f)
        else -> Color.White.copy(alpha = 0.94f)
    }

    val containerColor = when {
        !isEnabled -> colorScheme.onSurface.copy(alpha = 0.08f)
        isM3 && isDestructive -> colorScheme.errorContainer
        isM3 && isActive -> colorScheme.secondaryContainer
        isM3 -> colorScheme.surfaceContainerHigh
        isDestructive -> colorScheme.error.copy(alpha = 0.22f)
        isActive -> tintColor.copy(alpha = if (isLightPanel) 0.16f else 0.20f)
        else -> Color.White.copy(alpha = 0.10f)
    }

    val currentBorder: BorderStroke? = when {
        isM3 -> null
        !isEnabled -> BorderStroke(borderWidth, borderColor.copy(alpha = 0.12f))
        isDestructive -> BorderStroke(borderWidth, colorScheme.error.copy(alpha = 0.55f))
        isActive -> BorderStroke(borderWidth, tintColor.copy(alpha = 0.58f))
        else -> BorderStroke(borderWidth, borderColor.copy(alpha = 0.30f))
    }

    CompositionLocalProvider(LocalContentColor provides contentColor) {
        Surface(
            modifier = modifier
                .clip(shape)
                .then(if (currentBorder != null) Modifier.border(currentBorder, shape) else Modifier)
                .background(containerColor)
                .combinedClickable(
                    enabled = isEnabled,
                    onClick = onClick
                ),
            shape = shape,
            color = Color.Transparent
        ) {
            Row(
                modifier = Modifier.padding(contentPadding),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center,
                content = content
            )
        }
    }
}

@Composable
fun DeckButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    isActive: Boolean = false,
    isDestructive: Boolean = false,
    isEnabled: Boolean = true,
    snapshot: DeckThemeSnapshot = LocalDeckTheme.current,
    contentPadding: PaddingValues = PaddingValues(horizontal = 14.dp, vertical = 9.dp),
    maxLines: Int = Int.MAX_VALUE
) {
    DeckButton(
        onClick = onClick,
        modifier = modifier,
        isActive = isActive,
        isDestructive = isDestructive,
        isEnabled = isEnabled,
        snapshot = snapshot,
        contentPadding = contentPadding
    ) {
        val contentColor = LocalContentColor.current
        if (icon != null) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = contentColor,
                modifier = Modifier.size(16.dp)
            )
            Spacer(modifier = Modifier.width(6.dp))
        }
        Text(
            text = text,
            fontSize = 12.5.sp,
            fontWeight = FontWeight.Bold,
            color = contentColor,
            letterSpacing = 0.4.sp,
            maxLines = maxLines,
            softWrap = maxLines > 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

/**
 * Tactical Deck Slider matching the active Lightspeed 12-theme accent color.
 * Eliminates default Material3 purple tint.
 */
@Composable
fun DeckSlider(
    value: Float,
    onValueChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
    valueRange: ClosedFloatingPointRange<Float> = 0f..1f,
    steps: Int = 0,
    enabled: Boolean = true,
    onValueChangeFinished: (() -> Unit)? = null
) {
    val lsColors = LightspeedStyle.colors
    androidx.compose.material3.Slider(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier,
        enabled = enabled,
        valueRange = valueRange,
        steps = steps,
        onValueChangeFinished = onValueChangeFinished,
        colors = androidx.compose.material3.SliderDefaults.colors(
            thumbColor = lsColors.accent,
            activeTrackColor = lsColors.accent,
            inactiveTrackColor = lsColors.accent.copy(alpha = 0.22f),
            activeTickColor = Color.Transparent,
            inactiveTickColor = Color.Transparent,
            disabledThumbColor = lsColors.textDisabled,
            disabledActiveTrackColor = lsColors.textDisabled.copy(alpha = 0.3f),
            disabledInactiveTrackColor = lsColors.surface.copy(alpha = 0.3f)
        )
    )
}

/**
 * High-contrast Switch colors that adapt cleanly to bright 12-theme accents (e.g. Glass Lava /
 * Glassmorphism pure white tint) so checked toggles never become white-on-white.
 */
@Composable
fun deckSwitchColors(
    checkedTrackColor: Color = MaterialTheme.colorScheme.primary
): SwitchColors {
    val lsColors = LightspeedStyle.colors
    val isBrightTrack = checkedTrackColor.luminance() > 0.45f
    val checkedThumb = if (isBrightTrack) Color.Black else Color.White
    val checkedBorder = if (isBrightTrack) {
        Color.Black.copy(alpha = 0.30f)
    } else {
        checkedTrackColor
    }
    return SwitchDefaults.colors(
        checkedThumbColor = checkedThumb,
        checkedTrackColor = checkedTrackColor,
        checkedBorderColor = checkedBorder,
        uncheckedThumbColor = lsColors.textSecondary,
        uncheckedTrackColor = lsColors.textPrimary.copy(alpha = 0.14f),
        uncheckedBorderColor = lsColors.textSecondary.copy(alpha = 0.35f)
    )
}

/**
 * Tactical Deck Switch matching the active Lightspeed theme colors.
 */
@Composable
fun DeckSwitch(
    checked: Boolean,
    onCheckedChange: ((Boolean) -> Unit)?,
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
    val lsColors = LightspeedStyle.colors
    androidx.compose.material3.Switch(
        checked = checked,
        onCheckedChange = onCheckedChange,
        modifier = modifier,
        enabled = enabled,
        colors = deckSwitchColors(checkedTrackColor = lsColors.accent)
    )
}

