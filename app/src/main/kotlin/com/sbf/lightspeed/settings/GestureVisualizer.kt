package com.sbf.lightspeed.settings

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp

enum class ArrowDirection {
    SWIPE_UP, SWIPE_DOWN, SWIPE_LEFT, SWIPE_UP_DOWN, SWIPE_DOWN_UP,
    SWIPE_UP_LEFT, SWIPE_DOWN_LEFT, SWIPE_UP_RIGHT, SWIPE_DOWN_RIGHT,
    LEFT_BACK, LEFT_UP, LEFT_DOWN, SCRUB, TAP, DOUBLE_TAP,
    SWIPE_RIGHT, SWIPE_RIGHT_BACK, RIGHT_BACK, RIGHT_UP, RIGHT_DOWN
}

@Composable
fun GestureTrailTracer(
    direction: ArrowDirection,
    isHold: Boolean,
    color: Color,
    modifier: Modifier = Modifier
) {
    val infiniteTransition = rememberInfiniteTransition(label = "gesture_track")
    val progress by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1600, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "progress"
    )

    Canvas(modifier = modifier.size(36.dp)) {
        val w = size.width
        val h = size.height
        val trackStroke = 1.5.dp.toPx()
        val arrowStroke = 2.5.dp.toPx()
        val path = Path()
        var cx = w * 0.5f
        var cy = h * 0.5f

        val isHoldSegment = isHold && progress > 0.65f
        val actionProgress = if (isHold) (if (!isHoldSegment) progress / 0.65f else 1.0f) else progress
        val pulseAlpha = if (isHoldSegment) (1.0f - ((progress - 0.65f) / 0.35f)) else 0.0f

        when (direction) {
            ArrowDirection.SWIPE_UP -> {
                path.moveTo(w * 0.5f, h * 0.8f)
                path.lineTo(w * 0.5f, h * 0.2f)
                cy = h * 0.8f + (h * 0.2f - h * 0.8f) * actionProgress
            }
            ArrowDirection.SWIPE_DOWN -> {
                path.moveTo(w * 0.5f, h * 0.2f)
                path.lineTo(w * 0.5f, h * 0.8f)
                cy = h * 0.2f + (h * 0.8f - h * 0.2f) * actionProgress
            }
            ArrowDirection.SWIPE_RIGHT -> {
                path.moveTo(w * 0.2f, h * 0.5f)
                path.lineTo(w * 0.8f, h * 0.5f)
                cx = w * 0.2f + (w * 0.8f - w * 0.2f) * actionProgress
            }
            ArrowDirection.SWIPE_RIGHT_BACK -> {
                path.moveTo(w * 0.2f, h * 0.5f)
                path.lineTo(w * 0.8f, h * 0.5f)
                path.lineTo(w * 0.5f, h * 0.5f)
                if (actionProgress < 0.6f) {
                    cy = h * 0.5f
                    cx = w * 0.2f + (w * 0.8f - w * 0.2f) * (actionProgress / 0.6f)
                } else {
                    cy = h * 0.5f
                    cx = w * 0.8f + (w * 0.5f - w * 0.8f) * ((actionProgress - 0.6f) / 0.4f)
                }
            }
            ArrowDirection.SWIPE_LEFT -> {
                path.moveTo(w * 0.8f, h * 0.5f)
                path.lineTo(w * 0.2f, h * 0.5f)
                cx = w * 0.8f + (w * 0.2f - w * 0.8f) * actionProgress
            }
            ArrowDirection.SWIPE_UP_DOWN -> {
                path.moveTo(w * 0.5f, h * 0.8f)
                path.lineTo(w * 0.5f, h * 0.3f)
                path.lineTo(w * 0.5f, h * 0.7f)
                cy = if (actionProgress < 0.5f) {
                    h * 0.8f + (h * 0.3f - h * 0.8f) * (actionProgress * 2f)
                } else {
                    h * 0.3f + (h * 0.7f - h * 0.3f) * ((actionProgress - 0.5f) * 2f)
                }
            }
            ArrowDirection.SWIPE_DOWN_UP -> {
                path.moveTo(w * 0.5f, h * 0.2f)
                path.lineTo(w * 0.5f, h * 0.7f)
                path.lineTo(w * 0.5f, h * 0.3f)
                cy = if (actionProgress < 0.5f) {
                    h * 0.2f + (h * 0.7f - h * 0.2f) * (actionProgress * 2f)
                } else {
                    h * 0.7f + (h * 0.3f - h * 0.7f) * ((actionProgress - 0.5f) * 2f)
                }
            }
            ArrowDirection.SWIPE_UP_LEFT -> {
                path.moveTo(w * 0.7f, h * 0.8f)
                path.lineTo(w * 0.7f, h * 0.3f)
                path.lineTo(w * 0.2f, h * 0.3f)
                if (actionProgress < 0.5f) {
                    cy = h * 0.8f + (h * 0.3f - h * 0.8f) * (actionProgress * 2f)
                    cx = w * 0.7f
                } else {
                    cx = w * 0.7f + (w * 0.2f - w * 0.7f) * ((actionProgress - 0.5f) * 2f)
                    cy = h * 0.3f
                }
            }
            ArrowDirection.SWIPE_DOWN_LEFT -> {
                path.moveTo(w * 0.7f, h * 0.2f)
                path.lineTo(w * 0.7f, h * 0.7f)
                path.lineTo(w * 0.2f, h * 0.7f)
                if (actionProgress < 0.5f) {
                    cy = h * 0.2f + (h * 0.7f - h * 0.2f) * (actionProgress * 2f)
                    cx = w * 0.7f
                } else {
                    cx = w * 0.7f + (w * 0.2f - w * 0.7f) * ((actionProgress - 0.5f) * 2f)
                    cy = h * 0.7f
                }
            }
            ArrowDirection.SWIPE_UP_RIGHT -> {
                path.moveTo(w * 0.3f, h * 0.8f)
                path.lineTo(w * 0.3f, h * 0.3f)
                path.lineTo(w * 0.8f, h * 0.3f)
                if (actionProgress < 0.5f) {
                    cy = h * 0.8f + (h * 0.3f - h * 0.8f) * (actionProgress * 2f)
                    cx = w * 0.3f
                } else {
                    cx = w * 0.3f + (w * 0.8f - w * 0.3f) * ((actionProgress - 0.5f) * 2f)
                    cy = h * 0.3f
                }
            }
            ArrowDirection.SWIPE_DOWN_RIGHT -> {
                path.moveTo(w * 0.3f, h * 0.2f)
                path.lineTo(w * 0.3f, h * 0.7f)
                path.lineTo(w * 0.8f, h * 0.7f)
                if (actionProgress < 0.5f) {
                    cy = h * 0.2f + (h * 0.7f - h * 0.2f) * (actionProgress * 2f)
                    cx = w * 0.3f
                } else {
                    cx = w * 0.3f + (w * 0.8f - w * 0.3f) * ((actionProgress - 0.5f) * 2f)
                    cy = h * 0.7f
                }
            }
            ArrowDirection.LEFT_BACK -> {
                path.moveTo(w * 0.8f, h * 0.5f)
                path.lineTo(w * 0.2f, h * 0.5f)
                path.lineTo(w * 0.5f, h * 0.5f)
                if (actionProgress < 0.6f) {
                    cy = h * 0.5f
                    cx = w * 0.8f + (w * 0.2f - w * 0.8f) * (actionProgress / 0.6f)
                } else {
                    cy = h * 0.5f
                    cx = w * 0.2f + (w * 0.5f - w * 0.2f) * ((actionProgress - 0.6f) / 0.4f)
                }
            }
            ArrowDirection.LEFT_UP -> {
                path.moveTo(w * 0.8f, h * 0.7f)
                path.lineTo(w * 0.3f, h * 0.7f)
                path.lineTo(w * 0.3f, h * 0.2f)
                if (actionProgress < 0.5f) {
                    cy = h * 0.7f
                    cx = w * 0.8f + (w * 0.3f - w * 0.8f) * (actionProgress * 2f)
                } else {
                    cx = w * 0.3f
                    cy = h * 0.7f + (h * 0.2f - h * 0.7f) * ((actionProgress - 0.5f) * 2f)
                }
            }
            ArrowDirection.LEFT_DOWN -> {
                path.moveTo(w * 0.8f, h * 0.3f)
                path.lineTo(w * 0.3f, h * 0.3f)
                path.lineTo(w * 0.3f, h * 0.8f)
                if (actionProgress < 0.5f) {
                    cy = h * 0.3f
                    cx = w * 0.8f + (w * 0.3f - w * 0.8f) * (actionProgress * 2f)
                } else {
                    cx = w * 0.3f
                    cy = h * 0.3f + (h * 0.8f - h * 0.3f) * ((actionProgress - 0.5f) * 2f)
                }
            }
            ArrowDirection.RIGHT_BACK -> {
                path.moveTo(w * 0.2f, h * 0.5f)
                path.lineTo(w * 0.8f, h * 0.5f)
                path.lineTo(w * 0.5f, h * 0.5f)
                if (actionProgress < 0.6f) {
                    cy = h * 0.5f
                    cx = w * 0.2f + (w * 0.8f - w * 0.2f) * (actionProgress / 0.6f)
                } else {
                    cy = h * 0.5f
                    cx = w * 0.8f + (w * 0.5f - w * 0.8f) * ((actionProgress - 0.6f) / 0.4f)
                }
            }
            ArrowDirection.RIGHT_UP -> {
                path.moveTo(w * 0.2f, h * 0.7f)
                path.lineTo(w * 0.7f, h * 0.7f)
                path.lineTo(w * 0.7f, h * 0.2f)
                if (actionProgress < 0.5f) {
                    cy = h * 0.7f
                    cx = w * 0.2f + (w * 0.7f - w * 0.2f) * (actionProgress * 2f)
                } else {
                    cx = w * 0.7f
                    cy = h * 0.7f + (h * 0.2f - h * 0.7f) * ((actionProgress - 0.5f) * 2f)
                }
            }
            ArrowDirection.RIGHT_DOWN -> {
                path.moveTo(w * 0.2f, h * 0.3f)
                path.lineTo(w * 0.7f, h * 0.3f)
                path.lineTo(w * 0.7f, h * 0.8f)
                if (actionProgress < 0.5f) {
                    cy = h * 0.3f
                    cx = w * 0.2f + (w * 0.7f - w * 0.2f) * (actionProgress * 2f)
                } else {
                    cx = w * 0.7f
                    cy = h * 0.3f + (h * 0.8f - h * 0.3f) * ((actionProgress - 0.5f) * 2f)
                }
            }
            ArrowDirection.TAP, ArrowDirection.DOUBLE_TAP -> {
                cx = w * 0.5f
                cy = h * 0.5f
            }
            ArrowDirection.SCRUB -> {
                val lp = (actionProgress * 2f)
                val tipOffsetX = if (lp > 1f) {
                    val angle = (lp - 1f) * 2f * kotlin.math.PI
                    (w * 0.12f) * kotlin.math.sin(angle).toFloat()
                } else {
                    0f
                }
                path.moveTo(w * 0.5f, h * 0.1f)
                path.lineTo(w * 0.5f + tipOffsetX, h * 0.9f)

                if (lp <= 1f) {
                    cx = w * 0.5f
                    cy = h * 0.1f + (h * 0.8f) * lp
                } else {
                    cx = w * 0.5f + tipOffsetX
                    cy = h * 0.9f
                }
            }
        }
        drawPath(
            path = path,
            color = color.copy(alpha = 0.15f),
            style = Stroke(width = trackStroke, cap = StrokeCap.Round, join = StrokeJoin.Round)
        )

        val hasArrowhead = direction != ArrowDirection.SWIPE_UP_DOWN &&
                direction != ArrowDirection.SWIPE_DOWN_UP &&
                direction != ArrowDirection.LEFT_BACK &&
                direction != ArrowDirection.SWIPE_RIGHT_BACK &&
                direction != ArrowDirection.SCRUB &&
                direction != ArrowDirection.TAP &&
                direction != ArrowDirection.DOUBLE_TAP

        if (hasArrowhead) {
            val arrowheadPath = Path()
            val arrowSize = w * 0.14f
            when (direction) {
                ArrowDirection.SWIPE_RIGHT -> {
                    arrowheadPath.moveTo(w * 0.8f - arrowSize, h * 0.5f - arrowSize)
                    arrowheadPath.lineTo(w * 0.8f, h * 0.5f)
                    arrowheadPath.lineTo(w * 0.8f - arrowSize, h * 0.5f + arrowSize)
                }
                ArrowDirection.SWIPE_UP -> {
                    arrowheadPath.moveTo(w * 0.5f - arrowSize, h * 0.2f + arrowSize)
                    arrowheadPath.lineTo(w * 0.5f, h * 0.2f)
                    arrowheadPath.lineTo(w * 0.5f + arrowSize, h * 0.2f + arrowSize)
                }
                ArrowDirection.SWIPE_DOWN -> {
                    arrowheadPath.moveTo(w * 0.5f - arrowSize, h * 0.8f - arrowSize)
                    arrowheadPath.lineTo(w * 0.5f, h * 0.8f)
                    arrowheadPath.lineTo(w * 0.5f + arrowSize, h * 0.8f - arrowSize)
                }
                ArrowDirection.SWIPE_LEFT -> {
                    arrowheadPath.moveTo(w * 0.2f + arrowSize, h * 0.5f - arrowSize)
                    arrowheadPath.lineTo(w * 0.2f, h * 0.5f)
                    arrowheadPath.lineTo(w * 0.2f + arrowSize, h * 0.5f + arrowSize)
                }
                ArrowDirection.SWIPE_UP_LEFT -> {
                    arrowheadPath.moveTo(w * 0.2f + arrowSize, h * 0.3f - arrowSize)
                    arrowheadPath.lineTo(w * 0.2f, h * 0.3f)
                    arrowheadPath.lineTo(w * 0.2f + arrowSize, h * 0.3f + arrowSize)
                }
                ArrowDirection.SWIPE_DOWN_LEFT -> {
                    arrowheadPath.moveTo(w * 0.2f + arrowSize, h * 0.7f - arrowSize)
                    arrowheadPath.lineTo(w * 0.2f, h * 0.7f)
                    arrowheadPath.lineTo(w * 0.2f + arrowSize, h * 0.7f + arrowSize)
                }
                ArrowDirection.LEFT_UP -> {
                    arrowheadPath.moveTo(w * 0.3f - arrowSize, h * 0.2f + arrowSize)
                    arrowheadPath.lineTo(w * 0.3f, h * 0.2f)
                    arrowheadPath.lineTo(w * 0.3f + arrowSize, h * 0.2f + arrowSize)
                }
                ArrowDirection.LEFT_DOWN -> {
                    arrowheadPath.moveTo(w * 0.3f - arrowSize, h * 0.8f - arrowSize)
                    arrowheadPath.lineTo(w * 0.3f, h * 0.8f)
                    arrowheadPath.lineTo(w * 0.3f + arrowSize, h * 0.8f - arrowSize)
                }
                ArrowDirection.RIGHT_DOWN -> {
                    arrowheadPath.moveTo(w * 0.7f - arrowSize, h * 0.8f - arrowSize)
                    arrowheadPath.lineTo(w * 0.7f, h * 0.8f)
                    arrowheadPath.lineTo(w * 0.7f + arrowSize, h * 0.8f - arrowSize)
                }
                else -> {}
            }
            drawPath(
                path = arrowheadPath,
                color = color.copy(alpha = 0.4f),
                style = Stroke(width = arrowStroke, cap = StrokeCap.Round, join = StrokeJoin.Round)
            )
        }

        // Collapse calculation: shrinks the dot to 0px and fades alpha down linearly
        val fadeThreshold = 0.85f
        val (dotAlpha, dotRadius) = if (actionProgress > fadeThreshold) {
            val scaleFactor = (1f - actionProgress) / (1f - fadeThreshold)
            Pair(0.45f * scaleFactor, 4.dp.toPx() * scaleFactor)
        } else {
            Pair(0.45f, 4.dp.toPx())
        }

        drawCircle(color = color.copy(alpha = dotAlpha), radius = dotRadius, center = Offset(cx, cy))
        if (isHoldSegment) {
            drawCircle(
                color = color.copy(alpha = pulseAlpha),
                radius = 4.dp.toPx() + (22.dp.toPx() * ((progress - 0.65f) / 0.35f)),
                center = Offset(cx, cy),
                style = Stroke(width = 1.dp.toPx())
            )
        }
    }
}
