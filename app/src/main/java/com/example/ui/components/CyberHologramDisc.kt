package com.example.ui.components

import android.net.Uri
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.example.ui.theme.CyberBlack
import com.example.ui.theme.CyberDarkSurface
import com.example.ui.theme.NeonCyan
import com.example.ui.theme.NeonMagenta
import com.example.ui.theme.NeonViolet

@Composable
fun CyberHologramDisc(
    albumArtUri: Uri?,
    isPlaying: Boolean,
    modifier: Modifier = Modifier
) {
    val infiniteTransition = rememberInfiniteTransition(label = "disc_rotation")
    val rotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(12000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "rotation"
    )

    val currentRotation = if (isPlaying) rotation else 0f

    Box(
        modifier = modifier
            .aspectRatio(1f)
            .padding(16.dp),
        contentAlignment = Alignment.Center
    ) {
        // Outer Cyber Glow & Neon Rings Canvas
        Canvas(modifier = Modifier.fillMaxSize()) {
            val center = Offset(size.width / 2, size.height / 2)
            val radius = size.width / 2

            // Neon cyan outer ring
            drawCircle(
                color = NeonCyan.copy(alpha = if (isPlaying) 0.35f else 0.15f),
                radius = radius - 2.dp.toPx(),
                style = Stroke(width = 2.dp.toPx())
            )

            // Neon violet concentric ring
            drawCircle(
                color = NeonViolet.copy(alpha = if (isPlaying) 0.4f else 0.2f),
                radius = radius * 0.88f,
                style = Stroke(width = 1.5f.dp.toPx())
            )

            // Grooves
            drawCircle(
                color = Color.White.copy(alpha = 0.08f),
                radius = radius * 0.72f,
                style = Stroke(width = 1.dp.toPx())
            )
            drawCircle(
                color = Color.White.copy(alpha = 0.06f),
                radius = radius * 0.58f,
                style = Stroke(width = 1.dp.toPx())
            )
        }

        // Rotating Disc Layer
        Box(
            modifier = Modifier
                .fillMaxSize(0.92f)
                .rotate(currentRotation)
                .shadow(24.dp, shape = CircleShape, spotColor = NeonCyan)
                .clip(CircleShape)
                .background(
                    Brush.radialGradient(
                        colors = listOf(
                            CyberDarkSurface,
                            CyberBlack,
                            Color(0xFF0F1420)
                        )
                    )
                )
                .border(
                    2.dp,
                    Brush.sweepGradient(
                        listOf(
                            NeonCyan,
                            NeonViolet,
                            NeonMagenta,
                            NeonCyan
                        )
                    ),
                    CircleShape
                ),
            contentAlignment = Alignment.Center
        ) {
            // Album Artwork Center
            Box(
                modifier = Modifier
                    .fillMaxSize(0.55f)
                    .clip(CircleShape)
                    .border(2.dp, NeonCyan.copy(alpha = 0.7f), CircleShape)
                    .background(CyberDarkSurface),
                contentAlignment = Alignment.Center
            ) {
                if (albumArtUri != null) {
                    AsyncImage(
                        model = albumArtUri,
                        contentDescription = "Pochette d'album",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                }

                // Center Holographic Spindle
                Box(
                    modifier = Modifier
                        .size(24.dp)
                        .clip(CircleShape)
                        .background(
                            Brush.linearGradient(
                                listOf(NeonCyan, NeonViolet)
                            )
                        )
                        .border(1.5.dp, Color.White, CircleShape)
                )
            }
        }
    }
}
