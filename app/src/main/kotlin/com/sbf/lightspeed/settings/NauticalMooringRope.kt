package com.sbf.lightspeed.settings

import android.content.Context
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sbf.lightspeed.system.LightspeedHapticEngine
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.cos
import kotlin.math.sin

// ---------------------------------------------------------------------------
// File-level cached Path objects — reset() before every draw use.
// Compose Canvas rendering runs on the main thread so these are safe.
// Eliminates all per-frame Path() allocations across all DrawScope functions.
// ---------------------------------------------------------------------------
private val _strandPath    = Path()   // helical strand ridges (per-segment, per-iteration)
private val _leftArrowPath = Path()   // directional arrow (left)
private val _rightArrowPath = Path()  // directional arrow (right)
private val _leftTailPath  = Path()   // M3RowRope: outer flank left dangling tail
private val _rightTailPath = Path()   // M3RowRope: outer flank right dangling tail
private val _leftDroopPath = Path()   // M3RowRope: left catenary droop
private val _rightDroopPath = Path()  // M3RowRope: right catenary droop
private val _leftCordPath  = Path()   // SeveredMooringPairCard: left hanging cord
private val _rightCordPath = Path()   // SeveredMooringPairCard: right hanging cord

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
    modifier: Modifier = Modifier,
    onMirror: (() -> Unit)? = null
) {
    val coroutineScope = rememberCoroutineScope()
    val severFraction by animateFloatAsState(
        targetValue = if (isTied) 0f else 1f,
        animationSpec = tween(durationMillis = 350, easing = FastOutSlowInEasing),
        label = "m3RowRopeSever"
    )

    var isGolden by remember { mutableStateOf(false) }
    val goldenAnim by animateFloatAsState(
        targetValue = if (isGolden) 1f else 0f,
        animationSpec = tween(durationMillis = 400),
        label = "m3GoldenRopeAnim"
    )

    val colorScheme = MaterialTheme.colorScheme
    val goldHighlight = Color(0xFFFFD700).copy(alpha = 0.85f)
    val goldBase = Color(0xFFFFA000).copy(alpha = 0.55f)
    val goldShadow = Color(0xFF6D4C41).copy(alpha = 0.25f)

    val highlightColor = androidx.compose.ui.graphics.lerp(
        colorScheme.primary.copy(alpha = 0.45f),
        goldHighlight,
        goldenAnim
    )
    val baseColor = androidx.compose.ui.graphics.lerp(
        colorScheme.primary.copy(alpha = 0.22f),
        goldBase,
        goldenAnim
    )
    val shadowColor = androidx.compose.ui.graphics.lerp(
        colorScheme.primary.copy(alpha = 0.08f),
        goldShadow,
        goldenAnim
    )
    val severedColor = androidx.compose.ui.graphics.lerp(
        colorScheme.error.copy(alpha = 0.40f),
        goldBase.copy(alpha = 0.60f),
        goldenAnim
    )

    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(6.dp))
            .pointerInput(isTied, onMirror) {
                detectTapGestures(
                    onTap = {
                        LightspeedHapticEngine.tick(context)
                        onToggle(!isTied)
                    },
                    onLongPress = {
                        if (onMirror != null) {
                            isGolden = true
                            coroutineScope.launch {
                                delay(1200)
                                isGolden = false
                            }
                            LightspeedHapticEngine.heavyClick(context)
                            onMirror()
                        }
                    }
                )
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
        _leftTailPath.reset()
        _leftTailPath.moveTo(2.dp.toPx(), centerY)
        _leftTailPath.quadraticTo(0.5.dp.toPx(), centerY + sideTailLength * 0.5f, 3.dp.toPx(), centerY + sideTailLength)
        drawPath(_leftTailPath, color = currentBase, style = Stroke(width = ropeThickness * 0.75f))
        drawLine(frayColor, Offset(3.dp.toPx(), centerY + sideTailLength), Offset(2.dp.toPx(), centerY + sideTailLength + 3.dp.toPx()), strokeWidth = 1.dp.toPx())
        drawLine(frayColor, Offset(3.dp.toPx(), centerY + sideTailLength), Offset(4.5.dp.toPx(), centerY + sideTailLength + 2.5.dp.toPx()), strokeWidth = 0.8.dp.toPx())

        // Right flank dangling tail
        _rightTailPath.reset()
        _rightTailPath.moveTo(width - 2.dp.toPx(), centerY)
        _rightTailPath.quadraticTo(width - 0.5.dp.toPx(), centerY + sideTailLength * 0.5f, width - 3.dp.toPx(), centerY + sideTailLength)
        drawPath(_rightTailPath, color = currentBase, style = Stroke(width = ropeThickness * 0.75f))
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
        val droopStartXL = (leftRopeEndX * 0.5f).coerceAtLeast(0f)
        _leftDroopPath.reset()
        _leftDroopPath.moveTo(0f, centerY)
        _leftDroopPath.lineTo(droopStartXL, centerY)
        _leftDroopPath.cubicTo(
            droopStartXL + (leftRopeEndX - droopStartXL) * 0.5f, centerY,
            leftRopeEndX - 2.dp.toPx(), centerY + dangleY * 0.8f,
            leftRopeEndX, centerY + dangleY
        )
        drawPath(_leftDroopPath, color = currentBase, style = Stroke(width = ropeThickness))
        // Highlight along curve
        drawPath(_leftDroopPath, color = currentHighlight.copy(alpha = currentHighlight.alpha * 0.4f), style = Stroke(width = 1.2.dp.toPx()))

        // Right drooping segment
        val droopStartXR = (width - (width - rightRopeStartX) * 0.5f).coerceAtMost(width)
        _rightDroopPath.reset()
        _rightDroopPath.moveTo(width, centerY)
        _rightDroopPath.lineTo(droopStartXR, centerY)
        _rightDroopPath.cubicTo(
            droopStartXR - (droopStartXR - rightRopeStartX) * 0.5f, centerY,
            rightRopeStartX + 2.dp.toPx(), centerY + dangleY * 0.8f,
            rightRopeStartX, centerY + dangleY
        )
        drawPath(_rightDroopPath, color = currentBase, style = Stroke(width = ropeThickness))
        drawPath(_rightDroopPath, color = currentHighlight.copy(alpha = currentHighlight.alpha * 0.4f), style = Stroke(width = 1.2.dp.toPx()))
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
    _leftArrowPath.reset()
    if (pointsInward) {
        _leftArrowPath.moveTo(leftArrowX - arrowSize, centerY - arrowSize)
        _leftArrowPath.lineTo(leftArrowX + arrowSize, centerY)
        _leftArrowPath.lineTo(leftArrowX - arrowSize, centerY + arrowSize)
    } else {
        _leftArrowPath.moveTo(leftArrowX + arrowSize, centerY - arrowSize)
        _leftArrowPath.lineTo(leftArrowX - arrowSize, centerY)
        _leftArrowPath.lineTo(leftArrowX + arrowSize, centerY + arrowSize)
    }
    drawPath(_leftArrowPath, arrowColor, style = Stroke(width = 1.2.dp.toPx()))

    // Right Arrow
    _rightArrowPath.reset()
    if (pointsInward) {
        _rightArrowPath.moveTo(rightArrowX + arrowSize, centerY - arrowSize)
        _rightArrowPath.lineTo(rightArrowX - arrowSize, centerY)
        _rightArrowPath.lineTo(rightArrowX + arrowSize, centerY + arrowSize)
    } else {
        _rightArrowPath.moveTo(rightArrowX - arrowSize, centerY - arrowSize)
        _rightArrowPath.lineTo(rightArrowX + arrowSize, centerY)
        _rightArrowPath.lineTo(rightArrowX - arrowSize, centerY + arrowSize)
    }
    drawPath(_rightArrowPath, arrowColor, style = Stroke(width = 1.2.dp.toPx()))
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
    onMirror: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    val coroutineScope = rememberCoroutineScope()
    var isGolden by remember { mutableStateOf(false) }
    val goldenAnim by animateFloatAsState(
        targetValue = if (isGolden) 1f else 0f,
        animationSpec = tween(durationMillis = 400),
        label = "severedGoldenAnim"
    )

    val colorScheme = MaterialTheme.colorScheme
    val goldHighlight = Color(0xFFFFD700)
    val goldBase = Color(0xFFFFA000)
    val highlightColor = androidx.compose.ui.graphics.lerp(colorScheme.primary, goldHighlight, goldenAnim)
    val baseColor = androidx.compose.ui.graphics.lerp(colorScheme.tertiary.copy(alpha = 0.32f), goldBase.copy(alpha = 0.5f), goldenAnim)
    val frayColor = androidx.compose.ui.graphics.lerp(colorScheme.tertiary.copy(alpha = 0.70f), goldBase.copy(alpha = 0.8f), goldenAnim)

    Box(
        modifier = modifier.fillMaxWidth()
    ) {
        // 1. Tactical Canvas drawing the corner-anchored natural hanging severed mooring cords
        Canvas(
            modifier = Modifier
                .matchParentSize()
                .pointerInput(onTie, onMirror) {
                    detectTapGestures(
                        onTap = {
                            LightspeedHapticEngine.tick(context)
                            onTie()
                        },
                        onLongPress = {
                            if (onMirror != null) {
                                isGolden = true
                                coroutineScope.launch {
                                    delay(1200)
                                    isGolden = false
                                }
                                LightspeedHapticEngine.heavyClick(context)
                                onMirror()
                            }
                        }
                    )
                }
        ) {
            val width = size.width
            val height = size.height
            val ropeThickness = 2.6.dp.toPx()
            val flankLeftX = 5.dp.toPx()
            val flankRightX = width - 5.dp.toPx()
            val anchorY = 8.dp.toPx()
            val eyeletRadius = 3.2.dp.toPx()
            val dangleBottomY = (height * 0.72f).coerceAtLeast(anchorY + 40.dp.toPx())
            val fiberWidth = 1.dp.toPx()

            // --- LEFT FLANK CORNER ANCHOR & HANGING CORD ---
            // Eyelet ring at top-left corner
            drawCircle(
                color = highlightColor.copy(alpha = 0.50f),
                radius = eyeletRadius,
                center = Offset(flankLeftX, anchorY),
                style = Stroke(width = 1.3.dp.toPx())
            )
            // Hitch knot loop over the eyelet
            drawOval(
                color = baseColor,
                topLeft = Offset(flankLeftX - eyeletRadius * 1.1f, anchorY - eyeletRadius * 0.6f),
                size = Size(eyeletRadius * 2.2f, eyeletRadius * 1.2f),
                style = Stroke(width = ropeThickness)
            )
            // Small frayed severed stub pointing inward from left anchor (where line was cut/untied)
            drawLine(
                color = frayColor,
                start = Offset(flankLeftX + eyeletRadius, anchorY),
                end = Offset(flankLeftX + eyeletRadius + 4.dp.toPx(), anchorY - 1.dp.toPx()),
                strokeWidth = fiberWidth
            )
            drawLine(
                color = frayColor,
                start = Offset(flankLeftX + eyeletRadius, anchorY),
                end = Offset(flankLeftX + eyeletRadius + 3.dp.toPx(), anchorY + 2.dp.toPx()),
                strokeWidth = fiberWidth
            )

            // Left hanging cord running down the flank past Row 1 & Row 2
            _leftCordPath.reset()
            _leftCordPath.moveTo(flankLeftX, anchorY + eyeletRadius)
            _leftCordPath.quadraticTo(
                flankLeftX - 0.5.dp.toPx(), anchorY + (dangleBottomY - anchorY) * 0.45f,
                flankLeftX, dangleBottomY
            )
            drawPath(_leftCordPath, color = baseColor, style = Stroke(width = ropeThickness))
            drawPath(_leftCordPath, color = highlightColor.copy(alpha = 0.35f), style = Stroke(width = 1.2.dp.toPx()))

            // Frayed fibers at bottom tip of left cord
            drawLine(frayColor, Offset(flankLeftX, dangleBottomY), Offset(flankLeftX - 1.5.dp.toPx(), dangleBottomY + 5.dp.toPx()), strokeWidth = fiberWidth)
            drawLine(frayColor, Offset(flankLeftX, dangleBottomY), Offset(flankLeftX + 0.5.dp.toPx(), dangleBottomY + 6.5.dp.toPx()), strokeWidth = fiberWidth)
            drawLine(frayColor, Offset(flankLeftX, dangleBottomY), Offset(flankLeftX + 2.dp.toPx(), dangleBottomY + 4.dp.toPx()), strokeWidth = fiberWidth)

            // --- RIGHT FLANK CORNER ANCHOR & HANGING CORD ---
            // Eyelet ring at top-right corner
            drawCircle(
                color = highlightColor.copy(alpha = 0.50f),
                radius = eyeletRadius,
                center = Offset(flankRightX, anchorY),
                style = Stroke(width = 1.3.dp.toPx())
            )
            // Hitch knot loop over the eyelet
            drawOval(
                color = baseColor,
                topLeft = Offset(flankRightX - eyeletRadius * 1.1f, anchorY - eyeletRadius * 0.6f),
                size = Size(eyeletRadius * 2.2f, eyeletRadius * 1.2f),
                style = Stroke(width = ropeThickness)
            )
            // Small frayed severed stub pointing inward from right anchor (where line was cut/untied)
            drawLine(
                color = frayColor,
                start = Offset(flankRightX - eyeletRadius, anchorY),
                end = Offset(flankRightX - eyeletRadius - 4.dp.toPx(), anchorY - 1.dp.toPx()),
                strokeWidth = fiberWidth
            )
            drawLine(
                color = frayColor,
                start = Offset(flankRightX - eyeletRadius, anchorY),
                end = Offset(flankRightX - eyeletRadius - 3.dp.toPx(), anchorY + 2.dp.toPx()),
                strokeWidth = fiberWidth
            )

            // Right hanging cord running down the flank past Row 1 & Row 2
            _rightCordPath.reset()
            _rightCordPath.moveTo(flankRightX, anchorY + eyeletRadius)
            _rightCordPath.quadraticTo(
                flankRightX + 0.5.dp.toPx(), anchorY + (dangleBottomY - anchorY) * 0.45f,
                flankRightX, dangleBottomY
            )
            drawPath(_rightCordPath, color = baseColor, style = Stroke(width = ropeThickness))
            drawPath(_rightCordPath, color = highlightColor.copy(alpha = 0.35f), style = Stroke(width = 1.2.dp.toPx()))

            // Frayed fibers at bottom tip of right cord
            drawLine(frayColor, Offset(flankRightX, dangleBottomY), Offset(flankRightX - 1.5.dp.toPx(), dangleBottomY + 4.dp.toPx()), strokeWidth = fiberWidth)
            drawLine(frayColor, Offset(flankRightX, dangleBottomY), Offset(flankRightX - 0.5.dp.toPx(), dangleBottomY + 6.5.dp.toPx()), strokeWidth = fiberWidth)
            drawLine(frayColor, Offset(flankRightX, dangleBottomY), Offset(flankRightX + 2.dp.toPx(), dangleBottomY + 5.dp.toPx()), strokeWidth = fiberWidth)
        }

        // 2. Child Sector Rows with lateral margins for the flanking ropes
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 11.dp, end = 11.dp, top = 4.dp, bottom = 2.dp),
            content = content
        )
    }
}

