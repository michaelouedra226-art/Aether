package com.example.ui.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.ui.theme.NeonCyan
import com.example.ui.theme.NeonMagenta
import com.example.ui.theme.NeonViolet

@Composable
fun CyberVisualizerWave(
    frequencies: FloatArray,
    isPlaying: Boolean,
    modifier: Modifier = Modifier,
    height: Dp = 60.dp,
    barCount: Int = 28
) {
    val infiniteTransition = rememberInfiniteTransition(label = "wave_pulse")
    val pulsePhase by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulsePhase"
    )

    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(height)
    ) {
        val totalWidth = size.width
        val canvasHeight = size.height
        val barSpacing = 3.dp.toPx()
        val totalSpacing = barSpacing * (barCount - 1)
        val barWidth = ((totalWidth - totalSpacing) / barCount).coerceAtLeast(2.dp.toPx())

        for (i in 0 until barCount) {
            val freqIndex = (i * frequencies.size / barCount).coerceIn(0, frequencies.lastIndex)
            val baseEnergy = frequencies[freqIndex]
            val animatedEnergy = if (isPlaying) {
                (baseEnergy * (0.8f + 0.2f * pulsePhase)).coerceIn(0.08f, 1.0f)
            } else {
                0.05f
            }

            val barHeight = (canvasHeight * animatedEnergy).coerceAtLeast(4.dp.toPx())
            val x = i * (barWidth + barSpacing)
            val y = canvasHeight - barHeight

            val gradientBrush = Brush.verticalGradient(
                colors = listOf(
                    NeonCyan,
                    NeonViolet,
                    NeonMagenta.copy(alpha = 0.8f)
                ),
                startY = y,
                endY = canvasHeight
            )

            // Draw glowing bar
            drawRoundRect(
                brush = gradientBrush,
                topLeft = Offset(x, y),
                size = Size(barWidth, barHeight),
                cornerRadius = CornerRadius(barWidth / 2, barWidth / 2)
            )

            // Draw cyber reflection
            if (barHeight > 10.dp.toPx()) {
                drawCircle(
                    color = Color.White.copy(alpha = 0.9f),
                    radius = (barWidth / 2.5f).coerceAtMost(3.dp.toPx()),
                    center = Offset(x + barWidth / 2, y + 2.dp.toPx())
                )
            }
        }
    }
}
