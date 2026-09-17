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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
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
 * 100% text-free and emoji-free: purely visual braided rope with directional arrows and knot state.
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
    val highlightColor = colorScheme.primary.copy(alpha = 0.45f)
    val baseColor = colorScheme.primary.copy(alpha = 0.22f)
    val shadowColor = colorScheme.primary.copy(alpha = 0.08f)
    val severedColor = colorScheme.error.copy(alpha = 0.40f)

    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(6.dp))
            .clickable(
                interactionSource = interactionSource,
                indication = null
            ) {
                LightspeedHapticEngine.tick(context)
                onToggle(!isTied)
            }
            .padding(vertical = 1.dp),
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
    val centerY = height * 0.38f
    val centerX = width / 2f

    val ropeThickness = 5.dp.toPx()
    val gapWidth = severFraction * 56.dp.toPx()
    val dangleY = 6.5.dp.toPx() * severFraction

    val leftRopeEndX = (centerX - gapWidth / 2f).coerceAtLeast(0f)
    val rightRopeStartX = (centerX + gapWidth / 2f).coerceAtMost(width)

    val currentHighlight = if (severFraction < 0.5f) highlight else severedColor.copy(alpha = severedColor.alpha * 1.1f)
    val currentBase = if (severFraction < 0.5f) base else severedColor.copy(alpha = severedColor.alpha * 0.6f)
    val currentShadow = if (severFraction < 0.5f) shadow else severedColor.copy(alpha = 0.06f)
    val frayColor = severedColor.copy(alpha = severedColor.alpha * severFraction.coerceIn(0f, 1f))

    // 1. Draw outer flank dangling tails (on both sides when severed)
    if (severFraction > 0.05f) {
        val sideTailLength = 7.dp.toPx() * severFraction
        // Left flank dangling tail
        val leftTailPath = Path().apply {
            moveTo(2.dp.toPx(), centerY)
            quadraticTo(0.5.dp.toPx(), centerY + sideTailLength * 0.5f, 3.dp.toPx(), centerY + sideTailLength)
        }
        drawPath(leftTailPath, color = currentBase, style = Stroke(width = ropeThickness * 0.75f))
        drawLine(frayColor, Offset(3.dp.toPx(), centerY + sideTailLength), Offset(2.dp.toPx(), centerY + sideTailLength + 3.dp.toPx()), strokeWidth = 1.dp.toPx())
        drawLine(frayColor, Offset(3.dp.toPx(), centerY + sideTailLength), Offset(4.5.dp.toPx(), centerY + sideTailLength + 2.5.dp.toPx()), strokeWidth = 0.8.dp.toPx())

        // Right flank dangling tail
        val rightTailPath = Path().apply {
            moveTo(width - 2.dp.toPx(), centerY)
            quadraticTo(width - 0.5.dp.toPx(), centerY + sideTailLength * 0.5f, width - 3.dp.toPx(), centerY + sideTailLength)
        }
        drawPath(rightTailPath, color = currentBase, style = Stroke(width = ropeThickness * 0.75f))
        drawLine(frayColor, Offset(width - 3.dp.toPx(), centerY + sideTailLength), Offset(width - 2.dp.toPx(), centerY + sideTailLength + 3.dp.toPx()), strokeWidth = 1.dp.toPx())
        drawLine(frayColor, Offset(width - 3.dp.toPx(), centerY + sideTailLength), Offset(width - 4.5.dp.toPx(), centerY + sideTailLength + 2.5.dp.toPx()), strokeWidth = 0.8.dp.toPx())
    }

    // 2. Draw Left and Right Main Rope Segments (Drooping downwards when severed)
    if (severFraction < 0.05f) {
        // Taut straight cord
        fun drawStraightSegment(startX: Float, endX: Float) {
            if (startX >= endX) return
            val brush = Brush.verticalGradient(
                colors = listOf(currentHighlight, currentBase, currentShadow),
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
            val pitch = 6.dp.toPx()
            var curX = startX
            while (curX < endX) {
                val nextX = (curX + pitch).coerceAtMost(endX)
                drawLine(
                    color = currentHighlight.copy(alpha = currentHighlight.alpha * 0.6f),
                    start = Offset(curX, centerY - ropeThickness / 2f),
                    end = Offset(nextX, centerY + ropeThickness / 2f),
                    strokeWidth = 1.dp.toPx()
                )
                curX += pitch
            }
        }
        drawStraightSegment(0f, leftRopeEndX)
        drawStraightSegment(rightRopeStartX, width)
    } else {
        // Drooping / Slack cord with downward catenary curves toward severed tips
        // Left drooping segment
        val leftDroopPath = Path().apply {
            moveTo(0f, centerY)
            val droopStartX = (leftRopeEndX * 0.5f).coerceAtLeast(0f)
            lineTo(droopStartX, centerY)
            cubicTo(
                droopStartX + (leftRopeEndX - droopStartX) * 0.5f, centerY,
                leftRopeEndX - 2.dp.toPx(), centerY + dangleY * 0.8f,
                leftRopeEndX, centerY + dangleY
            )
        }
        drawPath(leftDroopPath, color = currentBase, style = Stroke(width = ropeThickness))
        // Highlight along curve
        drawPath(leftDroopPath, color = currentHighlight.copy(alpha = currentHighlight.alpha * 0.4f), style = Stroke(width = 1.2.dp.toPx()))

        // Right drooping segment
        val rightDroopPath = Path().apply {
            moveTo(width, centerY)
            val droopStartX = (width - (width - rightRopeStartX) * 0.5f).coerceAtMost(width)
            lineTo(droopStartX, centerY)
            cubicTo(
                droopStartX - (droopStartX - rightRopeStartX) * 0.5f, centerY,
                rightRopeStartX + 2.dp.toPx(), centerY + dangleY * 0.8f,
                rightRopeStartX, centerY + dangleY
            )
        }
        drawPath(rightDroopPath, color = currentBase, style = Stroke(width = ropeThickness))
        drawPath(rightDroopPath, color = currentHighlight.copy(alpha = currentHighlight.alpha * 0.4f), style = Stroke(width = 1.2.dp.toPx()))
    }

    // 3. Central reef knot interlock (when tied)
    val knotAlpha = (1f - severFraction * 4f).coerceIn(0f, 1f)
    if (knotAlpha > 0f) {
        val knotWidth = 11.dp.toPx()
        val knotHeight = 6.dp.toPx()
        val loopStroke = 1.4.dp.toPx()
        val loopColor = currentHighlight.copy(alpha = currentHighlight.alpha * knotAlpha)
        // Left eye loop
        drawOval(
            color = loopColor,
            topLeft = Offset(centerX - knotWidth * 0.5f, centerY - knotHeight * 0.5f),
            size = Size(knotWidth * 0.55f, knotHeight),
            style = Stroke(width = loopStroke)
        )
        // Right eye loop
        drawOval(
            color = loopColor,
            topLeft = Offset(centerX - knotWidth * 0.05f, centerY - knotHeight * 0.5f),
            size = Size(knotWidth * 0.55f, knotHeight),
            style = Stroke(width = loopStroke)
        )
    }

    // 4. Frayed fibers dangling from severed tips (when cut)
    if (severFraction > 0.08f) {
        val fiberWidth = 1.dp.toPx()
        val leftTipY = centerY + dangleY
        val rightTipY = centerY + dangleY

        // Left severed tip dangling fibers
        drawLine(frayColor, Offset(leftRopeEndX, leftTipY), Offset(leftRopeEndX - 4.dp.toPx(), leftTipY + 4.dp.toPx()), strokeWidth = fiberWidth)
        drawLine(frayColor, Offset(leftRopeEndX, leftTipY), Offset(leftRopeEndX - 1.dp.toPx(), leftTipY + 5.5.dp.toPx()), strokeWidth = fiberWidth)
        drawLine(frayColor, Offset(leftRopeEndX, leftTipY), Offset(leftRopeEndX - 5.dp.toPx(), leftTipY + 2.dp.toPx()), strokeWidth = fiberWidth)

        // Right severed tip dangling fibers
        drawLine(frayColor, Offset(rightRopeStartX, rightTipY), Offset(rightRopeStartX + 4.dp.toPx(), rightTipY + 4.dp.toPx()), strokeWidth = fiberWidth)
        drawLine(frayColor, Offset(rightRopeStartX, rightTipY), Offset(rightRopeStartX + 1.dp.toPx(), rightTipY + 5.5.dp.toPx()), strokeWidth = fiberWidth)
        drawLine(frayColor, Offset(rightRopeStartX, rightTipY), Offset(rightRopeStartX + 5.dp.toPx(), rightTipY + 2.dp.toPx()), strokeWidth = fiberWidth)
    }

    // 5. Directional arrows along cord
    val arrowColor = if (severFraction < 0.5f) currentHighlight.copy(alpha = 0.60f) else severedColor.copy(alpha = 0.65f)
    val leftArrowX = (leftRopeEndX * 0.45f).coerceAtLeast(14.dp.toPx())
    val rightArrowX = (rightRopeStartX + (width - rightRopeStartX) * 0.55f).coerceAtMost(width - 14.dp.toPx())
    val arrowSize = 3.5.dp.toPx()
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
    drawPath(leftPath, arrowColor, style = Stroke(width = 1.2.dp.toPx()))

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
    drawPath(rightPath, arrowColor, style = Stroke(width = 1.2.dp.toPx()))
}

/**
 * Tactical Confirmation Data Model for Mooring Line linking/unlinking operations.
 */
data class MooringConfirmData(
    val title: String,
    val message: String,
    val confirmLabel: String,
    val isSever: Boolean,
    val onConfirm: () -> Unit
)

/**
 * Compact Tactical Confirmation Micro-Dialog preventing accidental mooring severance or coupling.
 */
@Composable
fun MooringConfirmDialog(
    confirmData: MooringConfirmData?,
    onDismiss: () -> Unit
) {
    if (confirmData == null) return

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = confirmData.title,
                fontWeight = FontWeight.Bold,
                fontSize = 15.sp,
                color = Color.White
            )
        },
        text = {
            Text(
                text = confirmData.message,
                fontSize = 12.5.sp,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.85f),
                lineHeight = 17.sp
            )
        },
        confirmButton = {
            TextButton(
                onClick = {
                    onDismiss()
                    confirmData.onConfirm()
                }
            ) {
                Text(
                    text = confirmData.confirmLabel,
                    fontWeight = FontWeight.Bold,
                    color = if (confirmData.isSever) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = Color.LightGray)
            }
        },
        containerColor = Color(0xF0161922),
        shape = RoundedCornerShape(16.dp)
    )
}

/**
 * Flanking Nautical Mooring Harness for Separate Deflector Controls.
 * Wraps an unlinked Upper Sector and Lower Sector row pair in a continuous severed mooring rope:
 * - Anchored at the top corners above the Upper row.
 * - Central severed gap with frayed fibers and drooping catenary curves.
 * - Continuous rope runs along the top, curves around the outer shoulders, and dangles
 *   down both the left and right flanks across the gap into the Lower Sector row.
 * - At the bottom of the Lower row, the cord wraps inward with dangling frayed hemp fibers.
 * - Tapping anywhere on the rope harness triggers [onTie].
 */
@Composable
fun SeveredMooringPairCard(
    context: Context,
    onTie: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    val highlightColor = MaterialTheme.colorScheme.primary
    val baseColor = MaterialTheme.colorScheme.tertiary.copy(alpha = 0.28f)
    val frayColor = MaterialTheme.colorScheme.tertiary.copy(alpha = 0.65f)

    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.08f))
    ) {
        // 1. Tactical Canvas drawing the continuous flanking mooring lines
        Canvas(
            modifier = Modifier
                .matchParentSize()
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null
                ) {
                    LightspeedHapticEngine.tick(context)
                    onTie()
                }
        ) {
            val width = size.width
            val height = size.height
            val ropeThickness = 3.dp.toPx()
            val topY = 9.dp.toPx()
            val centerX = width / 2f
            val gapHalf = 28.dp.toPx()
            val leftRopeCutX = centerX - gapHalf
            val rightRopeCutX = centerX + gapHalf
            val cornerRadius = 10.dp.toPx()
            val flankLeftX = 5.dp.toPx()
            val flankRightX = width - 5.dp.toPx()
            val bottomY = height - 10.dp.toPx()

            // Left Severed Mooring Line:
            // Starts at severed center gap with drooping cut tip, runs to top-left corner,
            // rounds the corner, dangles down entire left flank, swoops inward at bottom.
            val leftPath = Path().apply {
                val cutDroopY = topY + 5.dp.toPx()
                moveTo(leftRopeCutX, cutDroopY)
                cubicTo(
                    leftRopeCutX - 12.dp.toPx(), cutDroopY + 1.dp.toPx(),
                    flankLeftX + cornerRadius + 8.dp.toPx(), topY,
                    flankLeftX + cornerRadius, topY
                )
                // Curve around top-left corner
                quadraticTo(flankLeftX, topY, flankLeftX, topY + cornerRadius)
                // Dangle down the entire left flank
                lineTo(flankLeftX, bottomY - cornerRadius)
                // Swoop inward under lower card
                quadraticTo(flankLeftX, bottomY, flankLeftX + 12.dp.toPx(), bottomY)
            }
            drawPath(leftPath, color = baseColor, style = Stroke(width = ropeThickness))
            drawPath(leftPath, color = highlightColor.copy(alpha = 0.35f), style = Stroke(width = 1.2.dp.toPx()))

            // Right Severed Mooring Line:
            // Starts at severed center gap with drooping cut tip, runs to top-right corner,
            // rounds the corner, dangles down entire right flank, swoops inward at bottom.
            val rightPath = Path().apply {
                val cutDroopY = topY + 5.dp.toPx()
                moveTo(rightRopeCutX, cutDroopY)
                cubicTo(
                    rightRopeCutX + 12.dp.toPx(), cutDroopY + 1.dp.toPx(),
                    flankRightX - cornerRadius - 8.dp.toPx(), topY,
                    flankRightX - cornerRadius, topY
                )
                // Curve around top-right corner
                quadraticTo(flankRightX, topY, flankRightX, topY + cornerRadius)
                // Dangle down the entire right flank
                lineTo(flankRightX, bottomY - cornerRadius)
                // Swoop inward under lower card
                quadraticTo(flankRightX, bottomY, flankRightX - 12.dp.toPx(), bottomY)
            }
            drawPath(rightPath, color = baseColor, style = Stroke(width = ropeThickness))
            drawPath(rightPath, color = highlightColor.copy(alpha = 0.35f), style = Stroke(width = 1.2.dp.toPx()))

            // Frayed fibers at top cut ends
            val fiberWidth = 1.dp.toPx()
            val cutDroopY = topY + 5.dp.toPx()
            // Left top cut tip
            drawLine(frayColor, Offset(leftRopeCutX, cutDroopY), Offset(leftRopeCutX - 4.dp.toPx(), cutDroopY + 4.dp.toPx()), strokeWidth = fiberWidth)
            drawLine(frayColor, Offset(leftRopeCutX, cutDroopY), Offset(leftRopeCutX - 1.dp.toPx(), cutDroopY + 5.5.dp.toPx()), strokeWidth = fiberWidth)
            drawLine(frayColor, Offset(leftRopeCutX, cutDroopY), Offset(leftRopeCutX - 5.dp.toPx(), cutDroopY + 2.dp.toPx()), strokeWidth = fiberWidth)
            // Right top cut tip
            drawLine(frayColor, Offset(rightRopeCutX, cutDroopY), Offset(rightRopeCutX + 4.dp.toPx(), cutDroopY + 4.dp.toPx()), strokeWidth = fiberWidth)
            drawLine(frayColor, Offset(rightRopeCutX, cutDroopY), Offset(rightRopeCutX + 1.dp.toPx(), cutDroopY + 5.5.dp.toPx()), strokeWidth = fiberWidth)
            drawLine(frayColor, Offset(rightRopeCutX, cutDroopY), Offset(rightRopeCutX + 5.dp.toPx(), cutDroopY + 2.dp.toPx()), strokeWidth = fiberWidth)

            // Frayed fibers at bottom dangling ends (covering lower row)
            val leftBottomEnd = Offset(flankLeftX + 12.dp.toPx(), bottomY)
            drawLine(frayColor, leftBottomEnd, Offset(leftBottomEnd.x + 4.dp.toPx(), bottomY + 1.dp.toPx()), strokeWidth = fiberWidth)
            drawLine(frayColor, leftBottomEnd, Offset(leftBottomEnd.x + 3.dp.toPx(), bottomY - 3.dp.toPx()), strokeWidth = fiberWidth)
            drawLine(frayColor, leftBottomEnd, Offset(leftBottomEnd.x + 5.dp.toPx(), bottomY + 3.dp.toPx()), strokeWidth = fiberWidth)

            val rightBottomEnd = Offset(flankRightX - 12.dp.toPx(), bottomY)
            drawLine(frayColor, rightBottomEnd, Offset(rightBottomEnd.x - 4.dp.toPx(), bottomY + 1.dp.toPx()), strokeWidth = fiberWidth)
            drawLine(frayColor, rightBottomEnd, Offset(rightBottomEnd.x - 3.dp.toPx(), bottomY - 3.dp.toPx()), strokeWidth = fiberWidth)
            drawLine(frayColor, rightBottomEnd, Offset(rightBottomEnd.x - 5.dp.toPx(), bottomY + 3.dp.toPx()), strokeWidth = fiberWidth)

            // Subtle chevron indicators pointing in re-coupling direction
            val arrowColor = highlightColor.copy(alpha = 0.45f)
            val arrowSize = 3.dp.toPx()
            // Top left arrow (pointing right toward center)
            val leftArrowX = (leftRopeCutX * 0.5f).coerceAtLeast(flankLeftX + 16.dp.toPx())
            val leftArrowPath = Path().apply {
                moveTo(leftArrowX - arrowSize, topY - arrowSize)
                lineTo(leftArrowX + arrowSize, topY)
                lineTo(leftArrowX - arrowSize, topY + arrowSize)
            }
            drawPath(leftArrowPath, arrowColor, style = Stroke(width = 1.dp.toPx()))

            // Top right arrow (pointing left toward center)
            val rightArrowX = (rightRopeCutX + (width - rightRopeCutX) * 0.5f).coerceAtMost(flankRightX - 16.dp.toPx())
            val rightArrowPath = Path().apply {
                moveTo(rightArrowX + arrowSize, topY - arrowSize)
                lineTo(rightArrowX - arrowSize, topY)
                lineTo(rightArrowX + arrowSize, topY + arrowSize)
            }
            drawPath(rightArrowPath, arrowColor, style = Stroke(width = 1.dp.toPx()))
        }

        // 2. Child Sector Rows with lateral margins for the flanking ropes
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 13.dp, end = 13.dp, top = 16.dp, bottom = 6.dp),
            content = content
        )
    }
}

