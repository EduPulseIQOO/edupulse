package com.edupulse.ui.canvas

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import kotlin.math.*

@Composable
fun ProjectileCanvas(
    velocity: Float,
    angleDegrees: Float,
    modifier: Modifier = Modifier,
    g: Float = 9.8f
) {
    Canvas(modifier = modifier) {
        val width = size.width
        val height = size.height

        val groundY = height * 0.85f
        val startX = 40.dp.toPx()
        val availableWidth = width - 80.dp.toPx()
        val availableHeight = groundY - 40.dp.toPx()

        // Physics calculations
        val thetaRad = Math.toRadians(angleDegrees.toDouble()).toFloat()
        val cosTheta = cos(thetaRad)
        val sinTheta = sin(thetaRad)

        // R = u² * sin(2θ) / g
        val range = (velocity * velocity * sin(2 * thetaRad)) / g
        // H = u² * sin²(θ) / (2g)
        val maxHeight = (velocity * velocity * sinTheta * sinTheta) / (2 * g)

        // Ground line
        drawLine(
            color = Color(0xFF64748B),
            start = Offset(20.dp.toPx(), groundY),
            end = Offset(width - 20.dp.toPx(), groundY),
            strokeWidth = 2.dp.toPx()
        )

        // Scale factors to fit within the canvas nicely
        val maxSimRange = 250f
        val maxSimHeight = 120f
        val scaleX = (availableWidth / maxSimRange).coerceAtLeast(1f)
        val scaleY = (availableHeight / maxSimHeight).coerceAtLeast(1f)

        // Draw Trajectory curve: y(x) = x * tan(θ) - (g * x²) / (2 * u² * cos²θ)
        val path = Path()
        path.moveTo(startX, groundY)

        val steps = 60
        val simRange = range.coerceAtMost(maxSimRange)
        val stepX = simRange / steps

        for (i in 1..steps) {
            val xPhys = i * stepX
            val yPhys = (xPhys * tan(thetaRad) - (g * xPhys * xPhys) / (2 * velocity * velocity * cosTheta * cosTheta)).coerceAtLeast(0f)
            val px = startX + xPhys * scaleX
            val py = groundY - yPhys * scaleY
            path.lineTo(px, py)
        }

        // Draw parabolic flight path
        drawPath(
            path = path,
            color = Color(0xFF38BDF8),
            style = Stroke(
                width = 3.dp.toPx(),
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(14f, 10f))
            )
        )

        // Draw Launch Velocity Vector
        val vectorLen = (velocity * 2f).coerceIn(30f, 90f)
        val arrowEndX = startX + vectorLen * cosTheta
        val arrowEndY = groundY - vectorLen * sinTheta

        drawLine(
            color = Color(0xFFF43F5E),
            start = Offset(startX, groundY),
            end = Offset(arrowEndX, arrowEndY),
            strokeWidth = 3.dp.toPx()
        )

        // Draw Launch point marker
        drawCircle(
            color = Color(0xFF38BDF8),
            radius = 6.dp.toPx(),
            center = Offset(startX, groundY)
        )

        // Draw Landing point marker
        val landingPx = (startX + range * scaleX).coerceAtMost(width - 40.dp.toPx())
        drawCircle(
            color = Color(0xFF10B981),
            radius = 6.dp.toPx(),
            center = Offset(landingPx, groundY)
        )
    }
}
