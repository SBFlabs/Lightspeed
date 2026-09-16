package com.sbf.lightspeed.settings

import android.content.Context
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sbf.lightspeed.system.LightspeedHapticEngine
import kotlin.math.cos
import kotlin.math.sin

/**
 * Tactical Nautical Mooring Rope: Skeuomorphic naval braided cord component.
 * Visualizes and controls the Linking & Unlinking of dual deflector sectors.
 *
 * States:
 * - Tied (Knotted): A continuous, taut braided marine rope with an interlocking reef knot.
 * - Cut (Severed): The rope is severed with frayed fiber tassels on both parted ends.
 */
@Composable
fun NauticalMooringRope(
    context: Context,
    isLinked: Boolean,
    onToggleLink: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    title: String = "DUAL SECTOR MOORING ROPE",
    linkedSubtitle: String = "Tied · Upper & Lower sectors scrub in lockstep",
    unlinkedSubtitle: String = "Severed · Upper & Lower scrubbers operate independently"
) {
    val interactionSource = remember { MutableInteractionSource() }

    // Animated severance gap fraction (0f = fully knotted/tied, 1f = fully severed/cut)
    val severFraction by animateFloatAsState(
        targetValue = if (isLinked) 0f else 1f,
        animationSpec = tween(durationMillis = 400, easing = FastOutSlowInEasing),
        label = "ropeSeverFraction"
    )

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .border(
                width = 1.dp,
                color = if (isLinked) Color(0xFFC89B5C).copy(alpha = 0.45f) else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f),
                shape = RoundedCornerShape(14.dp)
            )
            .clickable(
                interactionSource = interactionSource,
                indication = null
            ) {
                LightspeedHapticEngine.tick(context)
                onToggleLink(!isLinked)
            },
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.22f),
        shape = RoundedCornerShape(14.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp)
        ) {
            // Header: Tactical Label + Knotted/Cut Monospace Status Badge
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = title,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace,
                        color = if (isLinked) Color(0xFFE4C38E) else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = if (isLinked) linkedSubtitle else unlinkedSubtitle,
                        fontSize = 11.sp,
                        color = Color.White.copy(alpha = 0.75f)
                    )
                }

                Spacer(modifier = Modifier.width(8.dp))

                // Tactical Knot / Blade Badge
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(
                            if (isLinked) Color(0xFFC89B5C).copy(alpha = 0.20f)
                            else MaterialTheme.colorScheme.error.copy(alpha = 0.15f)
                        )
                        .border(
                            width = 1.dp,
                            color = if (isLinked) Color(0xFFE4C38E).copy(alpha = 0.6f)
                            else MaterialTheme.colorScheme.error.copy(alpha = 0.5f),
                            shape = RoundedCornerShape(8.dp)
                        )
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = if (isLinked) "⚓ KNOTTED // LINKED" else "✂️ CUT // UNLINKED",
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                        fontSize = 10.sp,
                        color = if (isLinked) Color(0xFFE4C38E) else MaterialTheme.colorScheme.error
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Canvas: Interactive Braided Navy Mooring Rope
            Canvas(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(38.dp)
            ) {
                drawNauticalRope(severFraction)
            }
        }
    }
}

/**
 * Custom Canvas drawing routine for braided nautical cord with knot / severed states.
 */
private fun DrawScope.drawNauticalRope(severFraction: Float) {
    val width = size.width
    val height = size.height
    val centerY = height / 2f
    val centerX = width / 2f

    val ropeThickness = 12.dp.toPx()
    val gapWidth = severFraction * 44.dp.toPx()

    // Rope color palette (Hawser-laid Manila hemp)
    val colorBase = Color(0xFFB88648)
    val colorHighlight = Color(0xFFECC995)
    val colorShadow = Color(0xFF5E3A15)
    val colorCore = Color(0xFF3B230B)

    val leftRopeEndX = (centerX - gapWidth / 2f).coerceAtLeast(0f)
    val rightRopeStartX = (centerX + gapWidth / 2f).coerceAtMost(width)

    // Helper to draw braided strands on a segment
    fun drawRopeSegment(startX: Float, endX: Float) {
        if (startX >= endX) return

        // 1. Ambient drop shadow under rope
        drawLine(
            color = Color.Black.copy(alpha = 0.35f),
            start = Offset(startX, centerY + ropeThickness * 0.45f),
            end = Offset(endX, centerY + ropeThickness * 0.45f),
            strokeWidth = ropeThickness * 0.5f
        )

        // 2. Base braided cord core
        val segmentBrush = Brush.verticalGradient(
            colors = listOf(colorHighlight, colorBase, colorShadow, colorCore),
            startY = centerY - ropeThickness / 2f,
            endY = centerY + ropeThickness / 2f
        )
        drawLine(
            brush = segmentBrush,
            start = Offset(startX, centerY),
            end = Offset(endX, centerY),
            strokeWidth = ropeThickness
        )

        // 3. Diagonal helical braided strand ridges
        val strandPitch = 10.dp.toPx()
        var curX = startX
        while (curX < endX) {
            val nextX = (curX + strandPitch).coerceAtMost(endX)
            val path = Path().apply {
                moveTo(curX, centerY - ropeThickness / 2f)
                lineTo(nextX, centerY + ropeThickness / 2f)
            }
            drawPath(
                path = path,
                color = colorShadow.copy(alpha = 0.7f),
                style = Stroke(width = 2.dp.toPx())
            )
            // Highlight ridge crest
            drawLine(
                color = colorHighlight.copy(alpha = 0.5f),
                start = Offset(curX + 1.5.dp.toPx(), centerY - ropeThickness / 2f),
                end = Offset(nextX + 1.5.dp.toPx(), centerY + ropeThickness / 2f),
                strokeWidth = 1.dp.toPx()
            )
            curX += strandPitch
        }
    }

    // Draw left and right segments
    drawRopeSegment(0f, leftRopeEndX)
    drawRopeSegment(rightRopeStartX, width)

    // Draw animated tactical directional arrows along cord
    val arrowColor = if (severFraction < 0.5f) colorHighlight.copy(alpha = 0.85f) else Color(0xFFFF8A65).copy(alpha = 0.85f)
    val leftArrowX = (leftRopeEndX * 0.55f).coerceAtLeast(16.dp.toPx())
    val rightArrowX = (rightRopeStartX + (width - rightRopeStartX) * 0.45f).coerceAtMost(width - 16.dp.toPx())
    val arrowSize = 5.dp.toPx()

    // Left Arrow: Inward (-->) when knotted, Outward (<--) when severed
    val leftPointsInward = severFraction < 0.5f
    val leftArrowPath = Path().apply {
        if (leftPointsInward) {
            moveTo(leftArrowX - arrowSize, centerY - arrowSize)
            lineTo(leftArrowX + arrowSize, centerY)
            lineTo(leftArrowX - arrowSize, centerY + arrowSize)
        } else {
            moveTo(leftArrowX + arrowSize, centerY - arrowSize)
            lineTo(leftArrowX - arrowSize, centerY)
            lineTo(leftArrowX + arrowSize, centerY + arrowSize)
        }
    }
    drawPath(path = leftArrowPath, color = arrowColor, style = Stroke(width = 2.dp.toPx()))

    // Right Arrow: Inward (<--) when knotted, Outward (-->) when severed
    val rightArrowPath = Path().apply {
        if (leftPointsInward) {
            moveTo(rightArrowX + arrowSize, centerY - arrowSize)
            lineTo(rightArrowX - arrowSize, centerY)
            lineTo(rightArrowX + arrowSize, centerY + arrowSize)
        } else {
            moveTo(rightArrowX - arrowSize, centerY - arrowSize)
            lineTo(rightArrowX + arrowSize, centerY)
            lineTo(rightArrowX - arrowSize, centerY + arrowSize)
        }
    }
    drawPath(path = rightArrowPath, color = arrowColor, style = Stroke(width = 2.dp.toPx()))

    if (severFraction < 0.15f) {
        // --- KNOTTED STATE (Reef Knot / Interlocking Nautical Loops) ---
        val knotAlpha = 1f - (severFraction / 0.15f)
        val knotRadius = 14.dp.toPx()

        // Knot shadow
        drawCircle(
            color = Color.Black.copy(alpha = 0.45f * knotAlpha),
            radius = knotRadius * 1.15f,
            center = Offset(centerX, centerY + 2.dp.toPx())
        )

        // Left interlocking loop
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(colorHighlight, colorBase, colorShadow),
                center = Offset(centerX - 6.dp.toPx(), centerY),
                radius = knotRadius
            ),
            radius = knotRadius * 0.85f,
            center = Offset(centerX - 6.dp.toPx(), centerY)
        )

        // Right interlocking loop
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(colorHighlight, colorBase, colorShadow),
                center = Offset(centerX + 6.dp.toPx(), centerY),
                radius = knotRadius
            ),
            radius = knotRadius * 0.85f,
            center = Offset(centerX + 6.dp.toPx(), centerY)
        )

        // Center nautical cross-binding cleat
        drawRoundRect(
            color = colorHighlight.copy(alpha = knotAlpha),
            topLeft = Offset(centerX - 4.dp.toPx(), centerY - knotRadius * 0.85f),
            size = androidx.compose.ui.geometry.Size(8.dp.toPx(), knotRadius * 1.7f),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(4.dp.toPx())
        )
        drawRoundRect(
            color = colorShadow.copy(alpha = 0.8f * knotAlpha),
            topLeft = Offset(centerX - 4.dp.toPx(), centerY - knotRadius * 0.85f),
            size = androidx.compose.ui.geometry.Size(8.dp.toPx(), knotRadius * 1.7f),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(4.dp.toPx()),
            style = Stroke(width = 1.dp.toPx())
        )
    } else {
        // --- CUT / SEVERED STATE (Frayed Fiber Tassels) ---
        val frayAlpha = ((severFraction - 0.15f) / 0.85f).coerceIn(0f, 1f)

        // Draw left frayed tassel ends
        drawFrayedTassel(
            tipX = leftRopeEndX,
            centerY = centerY,
            thickness = ropeThickness,
            direction = -1f,
            alpha = frayAlpha,
            highlight = colorHighlight,
            base = colorBase,
            shadow = colorShadow
        )

        // Draw right frayed tassel ends
        drawFrayedTassel(
            tipX = rightRopeStartX,
            centerY = centerY,
            thickness = ropeThickness,
            direction = 1f,
            alpha = frayAlpha,
            highlight = colorHighlight,
            base = colorBase,
            shadow = colorShadow
        )
    }
}

/**
 * Draws realistic frayed hemp fibers protruding from a severed cord end.
 */
private fun DrawScope.drawFrayedTassel(
    tipX: Float,
    centerY: Float,
    thickness: Float,
    direction: Float, // -1f for left rope cut, +1f for right rope cut
    alpha: Float,
    highlight: Color,
    base: Color,
    shadow: Color
) {
    val fiberCount = 7
    val maxFrayLength = 12.dp.toPx()

    for (i in 0 until fiberCount) {
        val yOffset = ((i - fiberCount / 2f) / (fiberCount / 2f)) * (thickness * 0.45f)
        val angle = (i * 0.35f) - 1.0f
        val length = maxFrayLength * (0.6f + 0.4f * sin(i.toDouble()).toFloat().coerceAtLeast(0f))

        val endX = tipX + (direction * -1f * length * cos(angle.toDouble()).toFloat())
        val endY = centerY + yOffset + (sin(angle.toDouble()).toFloat() * (thickness * 0.3f))

        val fiberColor = when (i % 3) {
            0 -> highlight
            1 -> base
            else -> shadow
        }.copy(alpha = alpha)

        drawLine(
            color = fiberColor,
            start = Offset(tipX, centerY + yOffset),
            end = Offset(endX, endY),
            strokeWidth = 1.5.dp.toPx()
        )
    }
}

/**
 * Compact Material 3 Expressive braided cord spanning across the top of any gesture row.
 * Directly toggles between Unified (Tied/Knotted) and Dual Control (Cut/Severed).
 */
@Composable
fun M3RowMooringRope(
    context: Context,
    isTied: Boolean,
    onToggle: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    val interactionSource = remember { MutableInteractionSource() }
    val severFraction by animateFloatAsState(
        targetValue = if (isTied) 0f else 1f,
        animationSpec = tween(durationMillis = 350, easing = FastOutSlowInEasing),
        label = "m3RowRopeSever"
    )

    val colorScheme = MaterialTheme.colorScheme
    val highlightColor = colorScheme.primary
    val baseColor = colorScheme.primary.copy(alpha = 0.65f)
    val shadowColor = colorScheme.primary.copy(alpha = 0.20f)
    val severedColor = colorScheme.error.copy(alpha = 0.85f)

    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .clickable(
                interactionSource = interactionSource,
                indication = null
            ) {
                LightspeedHapticEngine.tick(context)
                onToggle(!isTied)
            }
            .padding(vertical = 2.dp),
        contentAlignment = Alignment.Center
    ) {
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(18.dp)
        ) {
            drawM3RowRope(
                severFraction = severFraction,
                highlight = highlightColor,
                base = baseColor,
                shadow = shadowColor,
                severedColor = severedColor
            )
        }

        // Center status pip
        Surface(
            shape = RoundedCornerShape(8.dp),
            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.88f),
            border = androidx.compose.foundation.BorderStroke(
                1.dp,
                if (isTied) highlightColor.copy(alpha = 0.5f) else severedColor.copy(alpha = 0.6f)
            )
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = if (isTied) "⚓ UNIFIED (TIED)" else "✂️ DUAL (CUT)",
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    fontSize = 9.sp,
                    color = if (isTied) highlightColor else severedColor
                )
            }
        }
    }
}

private fun DrawScope.drawM3RowRope(
    severFraction: Float,
    highlight: Color,
    base: Color,
    shadow: Color,
    severedColor: Color
) {
    val width = size.width
    val height = size.height
    val centerY = height / 2f
    val centerX = width / 2f

    val ropeThickness = 7.dp.toPx()
    val gapWidth = severFraction * 70.dp.toPx()

    val leftRopeEndX = (centerX - gapWidth / 2f).coerceAtLeast(0f)
    val rightRopeStartX = (centerX + gapWidth / 2f).coerceAtMost(width)

    fun drawSegment(startX: Float, endX: Float) {
        if (startX >= endX) return
        val brush = Brush.verticalGradient(
            colors = listOf(highlight, base, shadow),
            startY = centerY - ropeThickness / 2f,
            endY = centerY + ropeThickness / 2f
        )
        drawLine(
            brush = brush,
            start = Offset(startX, centerY),
            end = Offset(endX, centerY),
            strokeWidth = ropeThickness
        )

        // Helical braided strand lines
        val pitch = 7.dp.toPx()
        var curX = startX
        while (curX < endX) {
            val nextX = (curX + pitch).coerceAtMost(endX)
            drawLine(
                color = shadow.copy(alpha = 0.8f),
                start = Offset(curX, centerY - ropeThickness / 2f),
                end = Offset(nextX, centerY + ropeThickness / 2f),
                strokeWidth = 1.2.dp.toPx()
            )
            curX += pitch
        }
    }

    drawSegment(0f, leftRopeEndX)
    drawSegment(rightRopeStartX, width)

    // Directional arrows
    val arrowColor = if (severFraction < 0.5f) highlight.copy(alpha = 0.8f) else severedColor
    val leftArrowX = (leftRopeEndX * 0.45f).coerceAtLeast(14.dp.toPx())
    val rightArrowX = (rightRopeStartX + (width - rightRopeStartX) * 0.55f).coerceAtMost(width - 14.dp.toPx())
    val arrowSize = 4.dp.toPx()
    val pointsInward = severFraction < 0.5f

    // Left Arrow
    val leftPath = Path().apply {
        if (pointsInward) {
            moveTo(leftArrowX - arrowSize, centerY - arrowSize)
            lineTo(leftArrowX + arrowSize, centerY)
            lineTo(leftArrowX - arrowSize, centerY + arrowSize)
        } else {
            moveTo(leftArrowX + arrowSize, centerY - arrowSize)
            lineTo(leftArrowX - arrowSize, centerY)
            lineTo(leftArrowX + arrowSize, centerY + arrowSize)
        }
    }
    drawPath(leftPath, arrowColor, style = Stroke(width = 1.5.dp.toPx()))

    // Right Arrow
    val rightPath = Path().apply {
        if (pointsInward) {
            moveTo(rightArrowX + arrowSize, centerY - arrowSize)
            lineTo(rightArrowX - arrowSize, centerY)
            lineTo(rightArrowX + arrowSize, centerY + arrowSize)
        } else {
            moveTo(rightArrowX - arrowSize, centerY - arrowSize)
            lineTo(rightArrowX + arrowSize, centerY)
            lineTo(rightArrowX - arrowSize, centerY + arrowSize)
        }
    }
    drawPath(rightPath, arrowColor, style = Stroke(width = 1.5.dp.toPx()))
}
