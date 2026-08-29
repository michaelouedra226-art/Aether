package com.example.ui.screens.settings

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BatteryChargingFull
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRowDefaults.SecondaryIndicator
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.ui.components.AetherTopAppBar
import com.example.ui.components.FuturisticBadge
import com.example.ui.components.FuturisticChoiceRow
import com.example.ui.components.FuturisticSliderRow
import com.example.ui.components.FuturisticSwitchRow
import com.example.ui.components.GlassCard
import com.example.ui.theme.SpotifyBlack
import com.example.ui.theme.SpotifyGreen
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import com.example.ui.viewmodel.MainViewModel

@Composable
fun SettingsScreen(
    viewModel: MainViewModel,
    onBackClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val settings by viewModel.settings.collectAsStateWithLifecycle()

    var selectedTabIndex by remember { mutableIntStateOf(0) }
    val tabs = listOf("Lecture", "Bibliothèque", "Interface", "Performance", "Avancé")

    var showResetDbDialog by remember { mutableStateOf(false) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(SpotifyBlack)
    ) {
        AetherTopAppBar(
            title = "Paramètres",
            onBackClick = onBackClick,
            actions = {
                FuturisticBadge(text = "v2.0", color = SpotifyGreen)
            }
        )

        ScrollableTabRow(
            selectedTabIndex = selectedTabIndex,
            containerColor = SpotifyBlack,
            contentColor = SpotifyGreen,
            edgePadding = 16.dp,
            indicator = { tabPositions ->
                if (selectedTabIndex < tabPositions.size) {
                    SecondaryIndicator(
                        modifier = Modifier.tabIndicatorOffset(tabPositions[selectedTabIndex]),
                        height = 3.dp,
                        color = SpotifyGreen
                    )
                }
            },
            divider = {}
        ) {
            tabs.forEachIndexed { index, title ->
                val isSelected = selectedTabIndex == index
                Tab(
                    selected = isSelected,
                    onClick = { selectedTabIndex = index },
                    text = {
                        Text(
                            text = title,
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                            ),
                            color = if (isSelected) SpotifyGreen else TextSecondary
                        )
                    }
                )
            }
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            when (selectedTabIndex) {
                0 -> {
                    // 10.1 LECTURE
                    item {
                        GlassCard {
                            Column {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    androidx.compose.material3.Icon(
                                        Icons.Default.PlayCircle,
                                        contentDescription = null,
                                        tint = SpotifyGreen
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = "Moteur Audio Media3",
                                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                        color = TextPrimary
                                    )
                                }

                                Spacer(modifier = Modifier.height(16.dp))

                                FuturisticSwitchRow(
                                    title = "Reprise de position",
                                    subtitle = "Mémorise et reprend le morceau et la position exacte au redémarrage",
                                    checked = settings.autoResumePosition,
                                    onCheckedChange = { viewModel.updateAutoResumePosition(it) }
                                )

                                FuturisticSwitchRow(
                                    title = "Lecture sans blanc (Gapless)",
                                    subtitle = "Enchaînement instantané et transparent entre les pistes",
                                    checked = settings.gaplessPlayback,
                                    onCheckedChange = { viewModel.updateGaplessPlayback(it) }
                                )

                                FuturisticSwitchRow(
                                    title = "Fondu enchaîné (Crossfade)",
                                    subtitle = "Transition progressive du volume entre pistes consécutives",
                                    checked = settings.crossfadeEnabled,
                                    onCheckedChange = { viewModel.updateCrossfadeEnabled(it) }
                                )

                                if (settings.crossfadeEnabled) {
                                    FuturisticSliderRow(
                                        title = "Durée du fondu",
                                        subtitle = "Durée du chevauchement sonore",
                                        value = settings.crossfadeDuration,
                                        valueRange = 1f..12f,
                                        steps = 10,
                                        displayValue = "${settings.crossfadeDuration.toInt()}s",
                                        onValueChange = { viewModel.updateCrossfadeDuration(it.toInt()) }
                                    )
                                }

                                val endQueueOptions = listOf("Arrêter", "Recommencer")
                                val endQueueIndex = if (settings.endOfQueueAction == "REPEAT") 1 else 0
                                FuturisticChoiceRow(
                                    title = "Action de fin de file",
                                    subtitle = "Comportement quand le dernier morceau de la liste se termine",
                                    options = endQueueOptions,
                                    selectedIndex = endQueueIndex,
                                    onOptionSelected = { index ->
                                        viewModel.updateEndOfQueueAction(if (index == 1) "REPEAT" else "STOP")
                                    }
                                )
                            }
                        }
                    }
                }

                1 -> {
                    // 10.2 BIBLIOTHÈQUE
                    item {
                        GlassCard {
                            Column {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    androidx.compose.material3.Icon(
                                        Icons.Default.Security,
                                        contentDescription = null,
                                        tint = SpotifyGreen
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = "Indexation & Filtres",
                                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                        color = TextPrimary
                                    )
                                }

                                Spacer(modifier = Modifier.height(16.dp))

                                FuturisticSwitchRow(
                                    title = "Exclure WhatsApp & Vocaux",
                                    subtitle = "Filtre automatiquement les notes vocales WhatsApp et fichiers mémos courts",
                                    checked = settings.whatsAppExclusion,
                                    onCheckedChange = { viewModel.updateExcludeWhatsApp(it) }
                                )

                                val rescanOptions = listOf("Au démarrage", "Manuel")
                                val rescanIndex = if (settings.rescanFrequency == "MANUAL") 1 else 0
                                FuturisticChoiceRow(
                                    title = "Fréquence d'indexation",
                                    subtitle = "Moment où Aether analyse le stockage local pour les nouvelles musiques",
                                    options = rescanOptions,
                                    selectedIndex = rescanIndex,
                                    onOptionSelected = { index ->
                                        viewModel.updateRescanFrequency(if (index == 1) "MANUAL" else "ON_START")
                                    }
                                )

                                Spacer(modifier = Modifier.height(12.dp))

                                Button(
                                    onClick = { viewModel.rescanLibrary(context) },
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = SpotifyGreen,
                                        contentColor = androidx.compose.ui.graphics.Color.Black
                                    ),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    androidx.compose.material3.Icon(
                                        Icons.Default.Refresh,
                                        contentDescription = null,
                                        modifier = Modifier.padding(end = 8.dp)
                                    )
                                    Text(
                                        text = "Scanner la musique maintenant",
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }
                    }
                }

                2 -> {
                    // 10.3 INTERFACE
                    item {
                        GlassCard {
                            Column {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    androidx.compose.material3.Icon(
                                        Icons.Default.Palette,
                                        contentDescription = null,
                                        tint = SpotifyGreen
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = "Affichage & Fluidité",
                                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                        color = TextPrimary
                                    )
                                }

                                Spacer(modifier = Modifier.height(16.dp))

                                val animOptions = listOf("Minimal", "Équilibré", "Maximum")
                                val animIndex = when (settings.animationLevel) {
                                    "MINIMAL" -> 0
                                    "BALANCED" -> 1
                                    else -> 2
                                }
                                FuturisticChoiceRow(
                                    title = "Niveau d'animations",
                                    subtitle = "Transitions de pochettes et pulsations dynamiques",
                                    options = animOptions,
                                    selectedIndex = animIndex,
                                    onOptionSelected = { index ->
                                        val level = when (index) {
                                            0 -> "MINIMAL"
                                            1 -> "BALANCED"
                                            else -> "MAXIMUM"
                                        }
                                        viewModel.updateAnimationLevel(level)
                                    }
                                )

                                val densityOptions = listOf("Compact", "Confortable", "Spacieux")
                                val densityIndex = when (settings.uiDensity) {
                                    "COMPACT" -> 0
                                    "SPACIOUS" -> 2
                                    else -> 1
                                }
                                FuturisticChoiceRow(
                                    title = "Densité d'affichage",
                                    subtitle = "Espacement des listes et taille des lignes de morceaux",
                                    options = densityOptions,
                                    selectedIndex = densityIndex,
                                    onOptionSelected = { index ->
                                        val density = when (index) {
                                            0 -> "COMPACT"
                                            2 -> "SPACIOUS"
                                            else -> "COMFORTABLE"
                                        }
                                        viewModel.updateUiDensity(density)
                                    }
                                )
                            }
                        }
                    }
                }

                3 -> {
                    // 10.4 PERFORMANCE
                    item {
                        GlassCard {
                            Column {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    androidx.compose.material3.Icon(
                                        Icons.Default.BatteryChargingFull,
                                        contentDescription = null,
                                        tint = SpotifyGreen
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = "Optimisation & Batterie",
                                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                        color = TextPrimary
                                    )
                                }

                                Spacer(modifier = Modifier.height(16.dp))

                                FuturisticSwitchRow(
                                    title = "Mode économie d'énergie",
                                    subtitle = "Désactive les effets visuels et réduit l'utilisation processeur",
                                    checked = settings.batterySaverMode,
                                    onCheckedChange = { viewModel.updateBatterySaverMode(it) }
                                )

                                FuturisticSwitchRow(
                                    title = "Visualiseur décoratif",
                                    subtitle = "Affiche les barres spectrales réactives sur le lecteur plein écran",
                                    checked = settings.visualizerEnabled,
                                    onCheckedChange = { viewModel.updateVisualizerEnabled(it) }
                                )
                            }
                        }
                    }
                }

                4 -> {
                    // 10.5 AVANCÉ
                    item {
                        GlassCard {
                            Column {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    androidx.compose.material3.Icon(
                                        Icons.Default.Storage,
                                        contentDescription = null,
                                        tint = SpotifyGreen
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = "Données & Réinitialisation",
                                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                        color = TextPrimary
                                    )
                                }

                                Spacer(modifier = Modifier.height(16.dp))

                                Button(
                                    onClick = { viewModel.resetPlaybackSession(context) },
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = androidx.compose.ui.graphics.Color(0xFF27273A),
                                        contentColor = TextPrimary
                                    ),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text("Réinitialiser la session de lecture")
                                }

                                Spacer(modifier = Modifier.height(8.dp))

                                Button(
                                    onClick = { showResetDbDialog = true },
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = androidx.compose.ui.graphics.Color(0xFF7F1D1D),
                                        contentColor = androidx.compose.ui.graphics.Color.White
                                    ),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text("Effacer la base de données locale")
                                }
                            }
                        }
                    }

                    item {
                        GlassCard {
                            Column {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    androidx.compose.material3.Icon(
                                        Icons.Default.Info,
                                        contentDescription = null,
                                        tint = SpotifyGreen
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = "À propos d'Aether",
                                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                        color = TextPrimary
                                    )
                                }

                                Spacer(modifier = Modifier.height(12.dp))

                                Text(
                                    text = "Version 2.0 (Build 2)\nLecteur 100% Local • AndroidX Media3 ExoPlayer\nAucune IA • Aucune télémétrie distante",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = TextSecondary,
                                    lineHeight = 20.sp
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    if (showResetDbDialog) {
        AlertDialog(
            onDismissRequest = { showResetDbDialog = false },
            title = { Text("Effacer la bibliothèque ?", color = TextPrimary) },
            text = {
                Text(
                    "Cette action supprime la base locale et l'historique d'écoute. Vos fichiers audio sur le disque ne seront pas effacés.",
                    color = TextSecondary
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showResetDbDialog = false
                        viewModel.resetAllData(context)
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = androidx.compose.ui.graphics.Color(0xFFDC2626),
                        contentColor = androidx.compose.ui.graphics.Color.White
                    )
                ) {
                    Text("Effacer")
                }
            },
            dismissButton = {
                TextButton(onClick = { showResetDbDialog = false }) {
                    Text("Annuler", color = TextSecondary)
                }
            },
            containerColor = SpotifyBlack
        )
    }
}
