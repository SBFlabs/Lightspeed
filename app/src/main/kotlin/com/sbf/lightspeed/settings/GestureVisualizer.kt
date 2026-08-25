package com.sbf.lightspeed.settings

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
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

/**
 * Ultra-fast static tactical gesture visualizer.
 * Renders directional trajectories, start nodes, and vector arrowheads with zero continuous recomposition.
 */
@Composable
fun GestureTrailTracer(
    direction: ArrowDirection,
    isHold: Boolean,
    color: Color,
    modifier: Modifier = Modifier
) {
    Canvas(modifier = modifier.size(36.dp)) {
        val w = size.width
        val h = size.height
        val trackStroke = 2.2.dp.toPx()
        val arrowStroke = 2.4.dp.toPx()
        val path = Path()
        var startNode = Offset(w * 0.5f, h * 0.5f)
        var endNode = Offset(w * 0.5f, h * 0.5f)

        when (direction) {
            ArrowDirection.SWIPE_UP -> {
                startNode = Offset(w * 0.5f, h * 0.8f)
                endNode = Offset(w * 0.5f, h * 0.2f)
                path.moveTo(startNode.x, startNode.y)
                path.lineTo(endNode.x, endNode.y)
            }
            ArrowDirection.SWIPE_DOWN -> {
                startNode = Offset(w * 0.5f, h * 0.2f)
                endNode = Offset(w * 0.5f, h * 0.8f)
                path.moveTo(startNode.x, startNode.y)
                path.lineTo(endNode.x, endNode.y)
            }
            ArrowDirection.SWIPE_RIGHT -> {
                startNode = Offset(w * 0.2f, h * 0.5f)
                endNode = Offset(w * 0.8f, h * 0.5f)
                path.moveTo(startNode.x, startNode.y)
                path.lineTo(endNode.x, endNode.y)
            }
            ArrowDirection.SWIPE_RIGHT_BACK, ArrowDirection.RIGHT_BACK -> {
                startNode = Offset(w * 0.2f, h * 0.5f)
                endNode = Offset(w * 0.4f, h * 0.5f)
                path.moveTo(w * 0.2f, h * 0.5f)
                path.lineTo(w * 0.8f, h * 0.5f)
                path.lineTo(w * 0.4f, h * 0.5f)
            }
            ArrowDirection.SWIPE_LEFT -> {
                startNode = Offset(w * 0.8f, h * 0.5f)
                endNode = Offset(w * 0.2f, h * 0.5f)
                path.moveTo(startNode.x, startNode.y)
                path.lineTo(endNode.x, endNode.y)
            }
            ArrowDirection.LEFT_BACK -> {
                startNode = Offset(w * 0.8f, h * 0.5f)
                endNode = Offset(w * 0.6f, h * 0.5f)
                path.moveTo(w * 0.8f, h * 0.5f)
                path.lineTo(w * 0.2f, h * 0.5f)
                path.lineTo(w * 0.6f, h * 0.5f)
            }
            ArrowDirection.SWIPE_UP_DOWN -> {
                startNode = Offset(w * 0.5f, h * 0.75f)
                endNode = Offset(w * 0.5f, h * 0.6f)
                path.moveTo(w * 0.5f, h * 0.75f)
                path.lineTo(w * 0.5f, h * 0.25f)
                path.lineTo(w * 0.5f, h * 0.6f)
            }
            ArrowDirection.SWIPE_DOWN_UP -> {
                startNode = Offset(w * 0.5f, h * 0.25f)
                endNode = Offset(w * 0.5f, h * 0.4f)
                path.moveTo(w * 0.5f, h * 0.25f)
                path.lineTo(w * 0.5f, h * 0.75f)
                path.lineTo(w * 0.5f, h * 0.4f)
            }
            ArrowDirection.SWIPE_UP_LEFT -> {
                startNode = Offset(w * 0.75f, h * 0.75f)
                endNode = Offset(w * 0.25f, h * 0.25f)
                path.moveTo(startNode.x, startNode.y)
                path.lineTo(w * 0.75f, h * 0.25f)
                path.lineTo(endNode.x, endNode.y)
            }
            ArrowDirection.SWIPE_DOWN_LEFT -> {
                startNode = Offset(w * 0.75f, h * 0.25f)
                endNode = Offset(w * 0.25f, h * 0.75f)
                path.moveTo(startNode.x, startNode.y)
                path.lineTo(w * 0.75f, h * 0.75f)
                path.lineTo(endNode.x, endNode.y)
            }
            ArrowDirection.SWIPE_UP_RIGHT -> {
                startNode = Offset(w * 0.25f, h * 0.75f)
                endNode = Offset(w * 0.75f, h * 0.25f)
                path.moveTo(startNode.x, startNode.y)
                path.lineTo(w * 0.25f, h * 0.25f)
                path.lineTo(endNode.x, endNode.y)
            }
            ArrowDirection.SWIPE_DOWN_RIGHT -> {
                startNode = Offset(w * 0.25f, h * 0.25f)
                endNode = Offset(w * 0.75f, h * 0.75f)
                path.moveTo(startNode.x, startNode.y)
                path.lineTo(w * 0.25f, h * 0.75f)
                path.lineTo(endNode.x, endNode.y)
            }
            ArrowDirection.LEFT_UP -> {
                startNode = Offset(w * 0.75f, h * 0.65f)
                endNode = Offset(w * 0.3f, h * 0.25f)
                path.moveTo(startNode.x, startNode.y)
                path.lineTo(w * 0.3f, h * 0.65f)
                path.lineTo(endNode.x, endNode.y)
            }
            ArrowDirection.LEFT_DOWN -> {
                startNode = Offset(w * 0.75f, h * 0.35f)
                endNode = Offset(w * 0.3f, h * 0.75f)
                path.moveTo(startNode.x, startNode.y)
                path.lineTo(w * 0.3f, h * 0.35f)
                path.lineTo(endNode.x, endNode.y)
            }
            ArrowDirection.RIGHT_UP -> {
                startNode = Offset(w * 0.25f, h * 0.65f)
                endNode = Offset(w * 0.7f, h * 0.25f)
                path.moveTo(startNode.x, startNode.y)
                path.lineTo(w * 0.7f, h * 0.65f)
                path.lineTo(endNode.x, endNode.y)
            }
            ArrowDirection.RIGHT_DOWN -> {
                startNode = Offset(w * 0.25f, h * 0.35f)
                endNode = Offset(w * 0.7f, h * 0.75f)
                path.moveTo(startNode.x, startNode.y)
                path.lineTo(w * 0.7f, h * 0.35f)
                path.lineTo(endNode.x, endNode.y)
            }
            ArrowDirection.TAP -> {
                startNode = Offset(w * 0.5f, h * 0.5f)
                endNode = Offset(w * 0.5f, h * 0.5f)
            }
            ArrowDirection.DOUBLE_TAP -> {
                startNode = Offset(w * 0.4f, h * 0.5f)
                endNode = Offset(w * 0.6f, h * 0.5f)
            }
            ArrowDirection.SCRUB -> {
                startNode = Offset(w * 0.2f, h * 0.5f)
                endNode = Offset(w * 0.8f, h * 0.5f)
                path.moveTo(w * 0.2f, h * 0.5f)
                path.lineTo(w * 0.8f, h * 0.5f)
            }
        }

        // Draw track
        if (direction != ArrowDirection.TAP && direction != ArrowDirection.DOUBLE_TAP) {
            drawPath(
                path = path,
                color = color.copy(alpha = 0.7f),
                style = Stroke(width = trackStroke, cap = StrokeCap.Round, join = StrokeJoin.Round)
            )
        }

        // Draw arrowheads
        val arrowheadPath = Path()
        val arrowSize = w * 0.16f
        var drawArrow = true

        when (direction) {
            ArrowDirection.SWIPE_RIGHT, ArrowDirection.RIGHT_UP, ArrowDirection.SWIPE_UP_RIGHT -> {
                arrowheadPath.moveTo(endNode.x - arrowSize, endNode.y - arrowSize)
                arrowheadPath.lineTo(endNode.x, endNode.y)
                arrowheadPath.lineTo(endNode.x - arrowSize, endNode.y + arrowSize)
            }
            ArrowDirection.SWIPE_LEFT, ArrowDirection.LEFT_UP, ArrowDirection.SWIPE_UP_LEFT -> {
                arrowheadPath.moveTo(endNode.x + arrowSize, endNode.y - arrowSize)
                arrowheadPath.lineTo(endNode.x, endNode.y)
                arrowheadPath.lineTo(endNode.x + arrowSize, endNode.y + arrowSize)
            }
            ArrowDirection.SWIPE_UP -> {
                arrowheadPath.moveTo(endNode.x - arrowSize, endNode.y + arrowSize)
                arrowheadPath.lineTo(endNode.x, endNode.y)
                arrowheadPath.lineTo(endNode.x + arrowSize, endNode.y + arrowSize)
            }
            ArrowDirection.SWIPE_DOWN -> {
                arrowheadPath.moveTo(endNode.x - arrowSize, endNode.y - arrowSize)
                arrowheadPath.lineTo(endNode.x, endNode.y)
                arrowheadPath.lineTo(endNode.x + arrowSize, endNode.y - arrowSize)
            }
            ArrowDirection.SWIPE_DOWN_LEFT, ArrowDirection.LEFT_DOWN -> {
                arrowheadPath.moveTo(endNode.x + arrowSize, endNode.y - arrowSize)
                arrowheadPath.lineTo(endNode.x, endNode.y)
                arrowheadPath.lineTo(endNode.x + arrowSize, endNode.y + arrowSize)
            }
            ArrowDirection.SWIPE_DOWN_RIGHT, ArrowDirection.RIGHT_DOWN -> {
                arrowheadPath.moveTo(endNode.x - arrowSize, endNode.y - arrowSize)
                arrowheadPath.lineTo(endNode.x, endNode.y)
                arrowheadPath.lineTo(endNode.x - arrowSize, endNode.y + arrowSize)
            }
            ArrowDirection.LEFT_BACK -> {
                arrowheadPath.moveTo(endNode.x - arrowSize, endNode.y - arrowSize)
                arrowheadPath.lineTo(endNode.x, endNode.y)
                arrowheadPath.lineTo(endNode.x - arrowSize, endNode.y + arrowSize)
            }
            ArrowDirection.RIGHT_BACK, ArrowDirection.SWIPE_RIGHT_BACK -> {
                arrowheadPath.moveTo(endNode.x + arrowSize, endNode.y - arrowSize)
                arrowheadPath.lineTo(endNode.x, endNode.y)
                arrowheadPath.lineTo(endNode.x + arrowSize, endNode.y + arrowSize)
            }
            ArrowDirection.SWIPE_UP_DOWN -> {
                arrowheadPath.moveTo(endNode.x - arrowSize, endNode.y - arrowSize)
                arrowheadPath.lineTo(endNode.x, endNode.y)
                arrowheadPath.lineTo(endNode.x + arrowSize, endNode.y - arrowSize)
            }
            ArrowDirection.SWIPE_DOWN_UP -> {
                arrowheadPath.moveTo(endNode.x - arrowSize, endNode.y + arrowSize)
                arrowheadPath.lineTo(endNode.x, endNode.y)
                arrowheadPath.lineTo(endNode.x + arrowSize, endNode.y + arrowSize)
            }
            ArrowDirection.SCRUB -> {
                arrowheadPath.moveTo(w * 0.2f + arrowSize, h * 0.5f - arrowSize)
                arrowheadPath.lineTo(w * 0.2f, h * 0.5f)
                arrowheadPath.lineTo(w * 0.2f + arrowSize, h * 0.5f + arrowSize)
                arrowheadPath.moveTo(w * 0.8f - arrowSize, h * 0.5f - arrowSize)
                arrowheadPath.lineTo(w * 0.8f, h * 0.5f)
                arrowheadPath.lineTo(w * 0.8f - arrowSize, h * 0.5f + arrowSize)
            }
            else -> {
                drawArrow = false
            }
        }

        if (drawArrow) {
            drawPath(
                path = arrowheadPath,
                color = color,
                style = Stroke(width = arrowStroke, cap = StrokeCap.Round, join = StrokeJoin.Round)
            )
        }

        // Draw start origin node / tap ring
        if (direction == ArrowDirection.TAP) {
            drawCircle(color = color, radius = 5.dp.toPx(), center = startNode)
            drawCircle(color = color.copy(alpha = 0.35f), radius = 10.dp.toPx(), center = startNode, style = Stroke(width = 1.5.dp.toPx()))
        } else if (direction == ArrowDirection.DOUBLE_TAP) {
            drawCircle(color = color, radius = 4.dp.toPx(), center = startNode)
            drawCircle(color = color, radius = 4.dp.toPx(), center = endNode)
        } else {
            drawCircle(color = color, radius = 3.5.dp.toPx(), center = startNode)
        }

        // Draw Hold indicator halo
        if (isHold) {
            drawCircle(
                color = color.copy(alpha = 0.6f),
                radius = 12.dp.toPx(),
                center = endNode,
                style = Stroke(width = 1.2.dp.toPx())
            )
        }
    }
}
