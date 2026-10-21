package com.sbf.lightspeed.settings

import android.graphics.drawable.ColorDrawable
import android.os.Build
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
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
    properties: DialogProperties = DialogProperties(usePlatformDefaultWidth = false),
    content: (@Composable ColumnScope.() -> Unit)? = null
) {
    Dialog(onDismissRequest = onDismissRequest, properties = properties) {
        val snapshot = LocalDeckTheme.current
        val view = LocalView.current
        SideEffect {
            val dialogWindow = (view.parent as? DialogWindowProvider)?.window
            dialogWindow?.setBackgroundDrawable(ColorDrawable(android.graphics.Color.TRANSPARENT))
            dialogWindow?.setDimAmount(snapshot.dimAmount)
        }

        Box(
            modifier = Modifier
                .fillMaxWidth(0.80f)
                .widthIn(max = 350.dp)
                .padding(vertical = 20.dp),
            contentAlignment = Alignment.Center
        ) {
            DeckGlassCardSurface(
                modifier = modifier.fillMaxWidth().wrapContentHeight(),
                isFlyout = true
            ) {
                Column(modifier = Modifier.fillMaxWidth().padding(20.dp)) {
                    if (icon != null) {
                        Box(modifier = Modifier.align(Alignment.CenterHorizontally).padding(bottom = 12.dp)) {
                            icon()
                        }
                    }
                    if (title != null) {
                        CompositionLocalProvider(
                            LocalTextStyle provides MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.Bold,
                                color = LightspeedStyle.colors.textPrimary
                            )
                        ) { title() }
                        Spacer(modifier = Modifier.height(10.dp))
                    }
                    if (text != null) {
                        CompositionLocalProvider(
                            LocalTextStyle provides MaterialTheme.typography.bodyMedium.copy(
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        ) { text() }
                    }
                    content?.invoke(this)
                    if (confirmButton != null || dismissButton != null) {
                        Spacer(modifier = Modifier.height(18.dp))
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
            .clip(effectiveShape)
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
    content: @Composable RowScope.() -> Unit
) {
    val cornerRadius = (snapshot.cornerRadiusDp * 0.40f).coerceIn(8f, 14f).dp
    val shape = RoundedCornerShape(cornerRadius)
    val borderWidth = (snapshot.borderWidthDp * 0.85f).coerceIn(1f, 1.8f).dp

    val opticalColors = snapshot.resolveOpticalColors()
    val tintColor = if (isDestructive) MaterialTheme.colorScheme.error else opticalColors.readableTintColor
    val panelColor = opticalColors.panelColor
    val borderColor = if (isDestructive) MaterialTheme.colorScheme.error.copy(alpha = 0.60f) else opticalColors.borderColor

    val containerBrush = when {
        !isEnabled -> Brush.verticalGradient(listOf(panelColor.copy(alpha = 0.25f), panelColor.copy(alpha = 0.18f)))
        isDestructive -> Brush.verticalGradient(listOf(MaterialTheme.colorScheme.error.copy(alpha = 0.28f), panelColor.copy(alpha = 0.80f)))
        isActive -> Brush.verticalGradient(listOf(tintColor.copy(alpha = 0.32f), panelColor.copy(alpha = 0.80f)))
        else -> Brush.verticalGradient(listOf(panelColor.copy(alpha = 0.70f), panelColor.copy(alpha = 0.55f)))
    }

    val currentBorder = when {
        !isEnabled -> BorderStroke(borderWidth, borderColor.copy(alpha = 0.15f))
        isDestructive -> BorderStroke(borderWidth, MaterialTheme.colorScheme.error.copy(alpha = 0.65f))
        isActive -> BorderStroke(borderWidth, borderColor.copy(alpha = 0.70f))
        else -> BorderStroke(borderWidth, borderColor.copy(alpha = 0.35f))
    }

    Surface(
        modifier = modifier
            .clip(shape)
            .border(currentBorder, shape)
            .background(containerBrush)
            .combinedClickable(
                enabled = isEnabled,
                onClick = onClick
            ),
        shape = shape,
        color = Color.Transparent
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 9.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
            content = content
        )
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
    snapshot: DeckThemeSnapshot = LocalDeckTheme.current
) {
    val opticalColors = snapshot.resolveOpticalColors()
    val tintColor = if (isDestructive) MaterialTheme.colorScheme.error else opticalColors.readableTintColor
    val contentColor = when {
        !isEnabled -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
        isDestructive -> MaterialTheme.colorScheme.error
        isActive -> tintColor
        else -> Color.White.copy(alpha = 0.92f)
    }

    DeckButton(
        onClick = onClick,
        modifier = modifier,
        isActive = isActive,
        isDestructive = isDestructive,
        isEnabled = isEnabled,
        snapshot = snapshot
    ) {
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
            letterSpacing = 0.4.sp
        )
    }
}
