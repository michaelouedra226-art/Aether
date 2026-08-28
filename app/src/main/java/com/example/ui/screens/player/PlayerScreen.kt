package com.example.ui.screens.player

import android.net.Uri
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.QueueMusic
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.RepeatOne
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.SubcomposeAsyncImage
import com.example.player.RepeatMode
import com.example.ui.components.FuturisticBadge
import com.example.ui.components.FuturisticChoiceRow
import com.example.ui.theme.SpotifyBlack
import com.example.ui.theme.SpotifyDarkSurface
import com.example.ui.theme.SpotifyGreen
import com.example.ui.theme.SpotifySurfaceHighlight
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import com.example.ui.viewmodel.MainViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlayerScreen(
    viewModel: MainViewModel,
    onBackClick: () -> Unit,
    onNavigateToQueue: () -> Unit,
    modifier: Modifier = Modifier
) {
    val playbackState by viewModel.playbackState.collectAsStateWithLifecycle()
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val currentTrack = playbackState.currentTrack

    var isDraggingSlider by remember { mutableStateOf(false) }
    var sliderPositionMs by remember { mutableFloatStateOf(0f) }

    var showMenu by remember { mutableStateOf(false) }
    var showSpeedSheet by remember { mutableStateOf(false) }
    var showEqualizerSheet by remember { mutableStateOf(false) }

    val currentPosition = if (isDraggingSlider) {
        sliderPositionMs.toLong()
    } else {
        playbackState.currentPositionMs
    }

    val totalDuration = playbackState.durationMs.coerceAtLeast(1L)
    val progressFraction = (currentPosition.toFloat() / totalDuration.toFloat()).coerceIn(0f, 1f)

    // Swipe down on the player to minimize
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        SpotifyDarkSurface,
                        SpotifyBlack,
                        SpotifyBlack
                    )
                )
            )
            .statusBarsPadding()
            .navigationBarsPadding()
            .pointerInput(Unit) {
                detectVerticalDragGestures { _, dragAmount ->
                    if (dragAmount > 50) {
                        onBackClick()
                    }
                }
            }
            .padding(horizontal = 24.dp, vertical = 12.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // Top Bar (Minimize Chevron, Context Title, Overflow Options)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                IconButton(
                    onClick = onBackClick,
                    modifier = Modifier.size(40.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.KeyboardArrowDown,
                        contentDescription = "Réduire le lecteur",
                        tint = TextPrimary,
                        modifier = Modifier.size(32.dp)
                    )
                }

                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "LECTURE EN COURS",
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.sp,
                            fontSize = 11.sp
                        ),
                        color = TextSecondary
                    )
                    Text(
                        text = currentTrack?.album ?: "Aether Player",
                        style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold),
                        color = TextPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Box {
                    IconButton(
                        onClick = { showMenu = true },
                        modifier = Modifier.size(40.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.MoreVert,
                            contentDescription = "Options de lecture",
                            tint = TextPrimary,
                            modifier = Modifier.size(24.dp)
                        )
                    }

                    DropdownMenu(
                        expanded = showMenu,
                        onDismissRequest = { showMenu = false },
                        modifier = Modifier.background(SpotifyDarkSurface)
                    ) {
                        DropdownMenuItem(
                            text = { Text("Vitesse de lecture", color = TextPrimary) },
                            onClick = {
                                showMenu = false
                                showSpeedSheet = true
                            },
                            leadingIcon = {
                                Icon(Icons.Default.Speed, contentDescription = null, tint = SpotifyGreen)
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Égaliseur & Effets", color = TextPrimary) },
                            onClick = {
                                showMenu = false
                                showEqualizerSheet = true
                            },
                            leadingIcon = {
                                Icon(Icons.Default.GraphicEq, contentDescription = null, tint = SpotifyGreen)
                            }
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Large Hero Album Artwork (Spotify Centered Square)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(1f)
                    .clip(RoundedCornerShape(12.dp))
                    .background(SpotifySurfaceHighlight),
                contentAlignment = Alignment.Center
            ) {
                SubcomposeAsyncImage(
                    model = currentTrack?.albumArtUri,
                    contentDescription = currentTrack?.title,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                    error = {
                        Icon(
                            imageVector = Icons.Default.MusicNote,
                            contentDescription = null,
                            tint = SpotifyGreen,
                            modifier = Modifier.size(96.dp)
                        )
                    },
                    loading = {
                        Icon(
                            imageVector = Icons.Default.MusicNote,
                            contentDescription = null,
                            tint = SpotifyGreen.copy(alpha = 0.5f),
                            modifier = Modifier.size(96.dp)
                        )
                    }
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Track Meta Info (Title, Artist, Format Badge)
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
                    Text(
                        text = currentTrack?.title ?: "Aucun titre sélectionné",
                        style = MaterialTheme.typography.headlineSmall.copy(
                            fontWeight = FontWeight.Bold,
                            fontSize = 22.sp
                        ),
                        color = TextPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = currentTrack?.artist ?: "Sélectionnez une musique",
                        style = MaterialTheme.typography.titleMedium.copy(fontSize = 16.sp),
                        color = TextSecondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                if (currentTrack != null) {
                    FuturisticBadge(
                        text = "${currentTrack.bitRate} kbps",
                        color = SpotifyGreen
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Interactive Progress Bar (Seeker)
            Column(modifier = Modifier.fillMaxWidth()) {
                Slider(
                    value = progressFraction,
                    onValueChange = { fraction ->
                        isDraggingSlider = true
                        sliderPositionMs = fraction * totalDuration
                    },
                    onValueChangeFinished = {
                        viewModel.seekTo(sliderPositionMs.toLong())
                        isDraggingSlider = false
                    },
                    colors = SliderDefaults.colors(
                        thumbColor = Color.White,
                        activeTrackColor = Color.White,
                        inactiveTrackColor = SpotifySurfaceHighlight
                    ),
                    modifier = Modifier.fillMaxWidth()
                )

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = formatTime(currentPosition),
                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp),
                        color = TextSecondary
                    )
                    Text(
                        text = formatTime(totalDuration),
                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp),
                        color = TextSecondary
                    )
                }
            }

            // Playback Control Cluster (Shuffle, Prev, Play/Pause, Next, Repeat)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                IconButton(
                    onClick = { viewModel.toggleShuffle() },
                    modifier = Modifier.size(44.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Shuffle,
                        contentDescription = "Lecture aléatoire",
                        tint = if (playbackState.isShuffle) SpotifyGreen else TextSecondary,
                        modifier = Modifier.size(24.dp)
                    )
                }

                IconButton(
                    onClick = { viewModel.previous() },
                    modifier = Modifier.size(52.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.SkipPrevious,
                        contentDescription = "Titre précédent",
                        tint = TextPrimary,
                        modifier = Modifier.size(38.dp)
                    )
                }

                // Play / Pause Circle (Prominent Spotify White Circle)
                val playInteractionSource = remember { MutableInteractionSource() }
                val isPlayPressed by playInteractionSource.collectIsPressedAsState()
                val playScale by animateFloatAsState(
                    targetValue = if (isPlayPressed) 0.92f else 1.0f,
                    animationSpec = spring(dampingRatio = 0.7f, stiffness = 500f),
                    label = "play_scale"
                )

                Box(
                    modifier = Modifier
                        .size(68.dp)
                        .scale(playScale)
                        .clip(CircleShape)
                        .background(Color.White)
                        .clickable(
                            interactionSource = playInteractionSource,
                            indication = null,
                            onClick = { viewModel.togglePlayPause() }
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (playbackState.isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                        contentDescription = if (playbackState.isPlaying) "Mettre en pause" else "Lire",
                        tint = Color.Black,
                        modifier = Modifier.size(36.dp)
                    )
                }

                IconButton(
                    onClick = { viewModel.next() },
                    modifier = Modifier.size(52.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.SkipNext,
                        contentDescription = "Titre suivant",
                        tint = TextPrimary,
                        modifier = Modifier.size(38.dp)
                    )
                }

                IconButton(
                    onClick = { viewModel.toggleRepeat() },
                    modifier = Modifier.size(44.dp)
                ) {
                    val (icon, tint) = when (playbackState.repeatMode) {
                        RepeatMode.ONE -> Icons.Default.RepeatOne to SpotifyGreen
                        RepeatMode.ALL -> Icons.Default.Repeat to SpotifyGreen
                        else -> Icons.Default.Repeat to TextSecondary
                    }
                    Icon(
                        imageVector = icon,
                        contentDescription = "Mode de répétition",
                        tint = tint,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }

            // Bottom Auxiliary Actions (Speed chip, Queue button)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                // Speed Chip
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(16.dp))
                        .background(SpotifyDarkSurface)
                        .clickable { showSpeedSheet = true }
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Speed,
                        contentDescription = null,
                        tint = SpotifyGreen,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "${playbackState.playbackSpeed}x",
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                        color = TextPrimary
                    )
                }

                // Queue Trigger
                IconButton(
                    onClick = onNavigateToQueue,
                    modifier = Modifier.size(40.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.QueueMusic,
                        contentDescription = "File d'attente",
                        tint = TextSecondary,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }
        }
    }

    // Modal BottomSheet for Playback Speed
    if (showSpeedSheet) {
        ModalBottomSheet(
            onDismissRequest = { showSpeedSheet = false },
            sheetState = rememberModalBottomSheetState(),
            containerColor = SpotifyDarkSurface
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 16.dp)
            ) {
                Text(
                    text = "Vitesse de lecture",
                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                    color = TextPrimary
                )
                Spacer(modifier = Modifier.height(16.dp))

                val speedOptions = listOf("0.5x", "0.75x", "1.0x", "1.25x", "1.5x", "2.0x")
                val speeds = listOf(0.5f, 0.75f, 1.0f, 1.25f, 1.5f, 2.0f)
                val currentIdx = speeds.indexOfFirst { kotlin.math.abs(it - playbackState.playbackSpeed) < 0.05f }.coerceAtLeast(2)

                FuturisticChoiceRow(
                    title = "Ajuster la cadence audio",
                    subtitle = "Vitesse dynamique sans distorsion de pitch",
                    options = speedOptions,
                    selectedIndex = currentIdx,
                    onOptionSelected = {
                        viewModel.updatePlaybackSpeed(speeds[it])
                        showSpeedSheet = false
                    }
                )
                Spacer(modifier = Modifier.height(24.dp))
            }
        }
    }

    // Modal BottomSheet for Equalizer & Audio Profile
    if (showEqualizerSheet) {
        ModalBottomSheet(
            onDismissRequest = { showEqualizerSheet = false },
            sheetState = rememberModalBottomSheetState(),
            containerColor = SpotifyDarkSurface
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 16.dp)
            ) {
                Text(
                    text = "Égaliseur & Signature Audio",
                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                    color = TextPrimary
                )
                Spacer(modifier = Modifier.height(16.dp))

                val eqOptions = listOf("Flat", "Bass Boost", "Electronic", "Vocal", "Rock")
                val currentEq = settings.equalizerPreset.coerceIn(0, eqOptions.lastIndex)

                FuturisticChoiceRow(
                    title = "Profil sonore actif",
                    subtitle = "DSP matériel et égalisation numérique",
                    options = eqOptions,
                    selectedIndex = currentEq,
                    onOptionSelected = {
                        viewModel.updateEqualizerPreset(eqOptions[it])
                        showEqualizerSheet = false
                    }
                )
                Spacer(modifier = Modifier.height(24.dp))
            }
        }
    }
}

private fun formatTime(millis: Long): String {
    val totalSeconds = (millis / 1000).coerceAtLeast(0)
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return "%d:%02d".format(minutes, seconds)
}
