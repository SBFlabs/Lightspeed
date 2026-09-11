package com.sbf.lightspeed.settings

import kotlin.math.roundToInt
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.geometry.Offset
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import kotlinx.coroutines.launch
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.IntOffset
import com.sbf.lightspeed.system.LightspeedHapticEngine
import kotlin.math.roundToInt
import androidx.compose.foundation.Canvas



@OptIn(ExperimentalFoundationApi::class)
@Composable
fun FloatingOverlayContainer(
    title: String,
    onDismiss: () -> Unit,
    headerControl: @Composable (RowScope.() -> Unit) = {},
    onTitleClick: (() -> Unit)? = null,
    onTitleLongClick: (() -> Unit)? = null,
    titleBadge: (@Composable () -> Unit)? = null,
    content: @Composable () -> Unit
) {
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    val density = LocalDensity.current
    val coroutineScope = rememberCoroutineScope()

    val thresholdPx = with(density) { 90.dp.toPx() }
    val dragOffsetY = remember { Animatable(0f) }
    var hasCrossedThreshold by remember { mutableStateOf(false) }

    val glassVisuals = rememberDeckGlassVisuals(context)

    Card(
        modifier = Modifier
            .fillMaxSize()
            .offset { IntOffset(0, dragOffsetY.value.roundToInt()) }
            .clip(RoundedCornerShape(glassVisuals.shapeCornerRadius))
            .background(glassVisuals.backgroundBrush)
            .border(glassVisuals.borderWidth, glassVisuals.borderBrush, RoundedCornerShape(glassVisuals.shapeCornerRadius)),
        colors = CardDefaults.cardColors(containerColor = Color.Transparent),
        shape = RoundedCornerShape(glassVisuals.shapeCornerRadius)
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            if (glassVisuals.showTopGlare) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(160.dp)
                        .background(
                            Brush.radialGradient(
                                colors = listOf(
                                    Color.White.copy(alpha = glassVisuals.topGlareAlpha),
                                    Color.White.copy(alpha = glassVisuals.topGlareAlpha * 0.45f),
                                    Color(0xFF80D8FF).copy(alpha = glassVisuals.topGlareAlpha * 0.15f),
                                    Color.Transparent
                                ),
                                center = Offset(x = 350f, y = 0f),
                                radius = 650f
                            )
                        )
                )
            }
            if (glassVisuals.showBottomCaustic) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(70.dp)
                        .align(Alignment.BottomCenter)
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(
                                    Color.Transparent,
                                    Color(0xFF00E5FF).copy(alpha = 0.08f),
                                    Color.White.copy(alpha = 0.14f)
                                )
                            )
                        )
                )
            }
            if (glassVisuals.showRefractiveRim) {
                Box(
                    modifier = Modifier
                        .matchParentSize()
                        .padding(2.5.dp)
                        .border(
                            1.dp,
                            Brush.linearGradient(
                                colors = listOf(
                                    Color(0xFF00E5FF).copy(alpha = 0.50f),
                                    Color.White.copy(alpha = 0.65f),
                                    Color(0xFFE040FB).copy(alpha = 0.45f),
                                    Color.Transparent
                                ),
                                start = Offset(0f, 0f),
                                end = Offset(Float.POSITIVE_INFINITY, Float.POSITIVE_INFINITY)
                            ),
                            RoundedCornerShape(glassVisuals.shapeCornerRadius - 2.5.dp)
                        )
                )
            }
            if (glassVisuals.innerChamferAlpha > 0f) {
                Box(
                    modifier = Modifier
                        .matchParentSize()
                        .padding(1.dp)
                        .border(
                            0.8.dp,
                            Brush.verticalGradient(
                                colors = listOf(
                                    Color.White.copy(alpha = glassVisuals.innerChamferAlpha),
                                    Color.Transparent
                                )
                            ),
                            RoundedCornerShape(glassVisuals.shapeCornerRadius - 1.dp)
                        )
                )
            }
            if (glassVisuals.showNoiseGrain) {
                Canvas(modifier = Modifier.matchParentSize()) {
                    drawRect(
                        brush = GlassNoiseTexture.getBrush(),
                        alpha = glassVisuals.noiseAlpha
                    )
                }
            }
            Column(modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp, vertical = 10.dp)) {
            // Scoped Drag Handle + Header Region (drag detection strictly scoped to header/handle)
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .pointerInput(Unit) {
                        detectVerticalDragGestures(
                            onDragStart = {
                                hasCrossedThreshold = false
                            },
                            onDragEnd = {
                                if (dragOffsetY.value >= thresholdPx) {
                                    onDismiss()
                                } else {
                                    coroutineScope.launch {
                                        dragOffsetY.animateTo(
                                            0f,
                                            animationSpec = spring(
                                                dampingRatio = Spring.DampingRatioMediumBouncy,
                                                stiffness = Spring.StiffnessMediumLow
                                            )
                                        )
                                    }
                                }
                            },
                            onDragCancel = {
                                coroutineScope.launch {
                                    dragOffsetY.animateTo(
                                        0f,
                                        animationSpec = spring(
                                            dampingRatio = Spring.DampingRatioMediumBouncy,
                                            stiffness = Spring.StiffnessMediumLow
                                        )
                                    )
                                }
                            },
                            onVerticalDrag = { change, dragAmount ->
                                change.consume()
                                val current = dragOffsetY.value + dragAmount
                                val newOffset = if (current <= 0f) {
                                    0f
                                } else if (current <= thresholdPx) {
                                    current
                                } else {
                                    thresholdPx + (current - thresholdPx) * 0.35f
                                }

                                if (!hasCrossedThreshold && newOffset >= thresholdPx) {
                                    hasCrossedThreshold = true
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    LightspeedHapticEngine.tick(context)
                                } else if (hasCrossedThreshold && newOffset < thresholdPx) {
                                    hasCrossedThreshold = false
                                }

                                coroutineScope.launch {
                                    dragOffsetY.snapTo(newOffset)
                                }
                            }
                        )
                    }
            ) {
                // Sleek Drag Handle Pill
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 2.dp, bottom = 8.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Box(
                        modifier = Modifier
                            .size(width = 38.dp, height = 4.dp)
                            .clip(CircleShape)
                            .background(Color.White.copy(alpha = 0.35f))
                    )
                }

                // Header Row (Title + strictly 2 action buttons)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .clip(RoundedCornerShape(10.dp))
                            .combinedClickable(
                                enabled = onTitleClick != null || onTitleLongClick != null,
                                onClick = {
                                    onTitleClick?.invoke()
                                },
                                onLongClick = {
                                    LightspeedHapticEngine.heavyClick(context)
                                    onTitleLongClick?.invoke()
                                }
                            )
                            .padding(vertical = 2.dp, horizontal = 4.dp)
                    ) {
                        Text(
                            text = title,
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                        if (onTitleClick != null || onTitleLongClick != null) {
                            Spacer(modifier = Modifier.width(3.dp))
                            Icon(
                                imageVector = Icons.Default.ExpandMore,
                                contentDescription = null,
                                tint = Color.White.copy(alpha = 0.45f),
                                modifier = Modifier.size(18.dp)
                            )
                        }
                        if (titleBadge != null) {
                            Spacer(modifier = Modifier.width(8.dp))
                            titleBadge()
                        }
                    }
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        headerControl()
                    }
                }
            }

            HorizontalDivider(color = Color.White.copy(alpha = 0.08f), modifier = Modifier.padding(bottom = 12.dp))
            Box(modifier = Modifier.weight(1f)) {
                content()
            }
        }
    }
}
}

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun CompactAccordionSection(
    title: String,
    isExpanded: Boolean,
    onToggle: () -> Unit,
    onLongToggle: (() -> Unit)? = null,
    icon: (@Composable () -> Unit)? = null,
    headerTrailing: @Composable (() -> Unit)? = null,
    content: @Composable () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f))
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .combinedClickable(
                        onClick = { onToggle() },
                        onLongClick = { onLongToggle?.invoke() }
                    )
                    .padding(horizontal = 16.dp, vertical = 14.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (icon != null) {
                    icon()
                    Spacer(modifier = Modifier.width(10.dp))
                }
                Text(
                    text = title,
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp,
                    color = Color.White,
                    modifier = Modifier.weight(1f).padding(end = 8.dp)
                )
                if (headerTrailing != null) {
                    headerTrailing()
                    Spacer(modifier = Modifier.width(6.dp))
                }
                Icon(
                    imageVector = if (isExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(24.dp)
                )
            }
            AnimatedVisibility(visible = isExpanded) {
                Column(modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, bottom = 16.dp)) {
                    content()
                }
            }
        }
    }
}

@Composable
fun CollapsibleSubSection(
    title: String,
    subtitle: String? = null,
    isExpanded: Boolean,
    onToggle: () -> Unit,
    icon: @Composable (() -> Unit)? = null,
    trailingBadge: @Composable (() -> Unit)? = null,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.45f)),
        border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.08f))
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onToggle() }
                    .padding(horizontal = 14.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.weight(1f).padding(end = 8.dp)
                ) {
                    if (icon != null) {
                        icon()
                    }
                    Column(modifier = Modifier.weight(1f, fill = false)) {
                        Text(
                            text = title,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 13.5.sp,
                            color = Color.White
                        )
                        if (!subtitle.isNullOrBlank()) {
                            Text(
                                text = subtitle,
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.85f)
                            )
                        }
                    }
                }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (trailingBadge != null) {
                        trailingBadge()
                    }
                    Icon(
                        imageVector = if (isExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(22.dp)
                    )
                }
            }
            AnimatedVisibility(visible = isExpanded) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 12.dp, end = 12.dp, bottom = 12.dp, top = 4.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    content()
                }
            }
        }
    }
}

@Composable
fun HazardAccordionSection(
    title: String,
    isExpanded: Boolean,
    onToggle: () -> Unit,
    subtitle: String? = null,
    headerTrailing: @Composable (() -> Unit)? = null,
    content: @Composable () -> Unit
) {
    val cautionAmber = Color(0xFFFFB300)
    val deepBlack = Color(0xFF0F1115)

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .border(
                1.5.dp,
                Brush.horizontalGradient(
                    listOf(
                        cautionAmber.copy(alpha = 0.85f),
                        deepBlack,
                        cautionAmber.copy(alpha = 0.85f)
                    )
                ),
                RoundedCornerShape(18.dp)
            ),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f))
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            // 45° Alternating Industrial Caution Amber and Deep Black Diagonal Hazard Hatch Stripe
            androidx.compose.foundation.Canvas(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(6.dp)
                    .clip(RoundedCornerShape(topStart = 18.dp, topEnd = 18.dp))
            ) {
                val w = size.width
                val h = size.height
                val stripeWidth = 10.dp.toPx()
                var x = -h
                var isAmber = true
                while (x < w + h) {
                    val path = androidx.compose.ui.graphics.Path().apply {
                        moveTo(x, 0f)
                        lineTo(x + stripeWidth, 0f)
                        lineTo(x + stripeWidth + h, h)
                        lineTo(x + h, h)
                        close()
                    }
                    drawPath(path, if (isAmber) cautionAmber else deepBlack)
                    x += stripeWidth
                    isAmber = !isAmber
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onToggle() }
                    .padding(horizontal = 16.dp, vertical = 14.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(
                    modifier = Modifier.weight(1f).padding(end = 8.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Science,
                            contentDescription = null,
                            tint = cautionAmber,
                            modifier = Modifier.size(18.dp)
                        )
                        Text(
                            text = title,
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp,
                            color = cautionAmber
                        )
                    }
                    if (!subtitle.isNullOrBlank()) {
                        Spacer(modifier = Modifier.height(3.dp))
                        Text(
                            text = subtitle,
                            fontSize = 11.5.sp,
                            color = Color.LightGray.copy(alpha = 0.85f),
                            lineHeight = 15.sp
                        )
                    }
                }
                if (headerTrailing != null) {
                    headerTrailing()
                    Spacer(modifier = Modifier.width(6.dp))
                }
                Icon(
                    imageVector = if (isExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                    contentDescription = null,
                    tint = cautionAmber,
                    modifier = Modifier.size(24.dp)
                )
            }
            AnimatedVisibility(visible = isExpanded) {
                Column(modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, bottom = 16.dp)) {
                    content()
                }
            }
        }
    }
}

/**
 * In-place morphing button with triple-lock safety confirmation before executing reboot.
 */
