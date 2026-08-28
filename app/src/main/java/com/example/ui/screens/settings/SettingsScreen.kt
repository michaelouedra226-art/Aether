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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BatteryChargingFull
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.PlayCircle
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
import com.example.ui.theme.SpotifyDarkSurface
import com.example.ui.theme.SpotifyGreen
import com.example.ui.theme.SpotifySurfaceHighlight
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

    var showResetDialog by remember { mutableStateOf(false) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(SpotifyBlack)
    ) {
        AetherTopAppBar(
            title = "Paramètres",
            onBackClick = onBackClick,
            actions = {
                FuturisticBadge(text = "V1.0", color = SpotifyGreen)
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
            }
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
                    // LECTURE
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
                                        text = "Moteur Audio & Enchaînement",
                                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                        color = TextPrimary
                                    )
                                }

                                Spacer(modifier = Modifier.height(16.dp))

                                FuturisticSwitchRow(
                                    title = "Lecture sans blanc (Gapless)",
                                    subtitle = "Transition instantanée et sans interruption entre les morceaux",
                                    checked = settings.gaplessPlayback,
                                    onCheckedChange = { viewModel.updateGaplessPlayback(it) }
                                )

                                FuturisticSwitchRow(
                                    title = "Fondu enchaîné (Crossfade)",
                                    subtitle = "Transition fluide entre la fin et le début du morceau suivant",
                                    checked = settings.crossfadeEnabled,
                                    onCheckedChange = { viewModel.updateCrossfadeEnabled(it) }
                                )

                                if (settings.crossfadeEnabled) {
                                    FuturisticSliderRow(
                                        title = "Durée du Crossfade",
                                        subtitle = "Durée du chevauchement sonore",
                                        value = settings.crossfadeDuration,
                                        valueRange = 1f..12f,
                                        steps = 10,
                                        displayValue = "${settings.crossfadeDuration.toInt()}s",
                                        onValueChange = { viewModel.updateCrossfadeDuration(it.toInt()) }
                                    )
                                }

                                FuturisticSwitchRow(
                                    title = "Pause au débranchement",
                                    subtitle = "Arrête automatiquement la lecture lorsque le casque ou Bluetooth est déconnecté",
                                    checked = settings.autoResumePosition,
                                    onCheckedChange = { viewModel.updatePauseOnUnplug(it) }
                                )

                                FuturisticSwitchRow(
                                    title = "Reprise automatique",
                                    subtitle = "Reprend la position de lecture lors du redémarrage",
                                    checked = settings.autoResumePosition,
                                    onCheckedChange = { viewModel.updateResumeOnHeadsetPlug(it) }
                                )
                            }
                        }
                    }

                    item {
                        GlassCard {
                            Column {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    androidx.compose.material3.Icon(
                                        Icons.Default.GraphicEq,
                                        contentDescription = null,
                                        tint = SpotifyGreen
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = "Profil Audio & Normalisation",
                                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                        color = TextPrimary
                                    )
                                }

                                Spacer(modifier = Modifier.height(16.dp))

                                val eqOptions = listOf("Flat", "Bass Boost", "Electronic", "Vocal", "Rock")
                                val eqIndex = settings.equalizerPreset.coerceIn(0, eqOptions.lastIndex)
                                FuturisticChoiceRow(
                                    title = "Préréglage Égaliseur",
                                    subtitle = "Signature sonore de sortie audio",
                                    options = eqOptions,
                                    selectedIndex = eqIndex,
                                    onOptionSelected = { viewModel.updateEqualizerPreset(eqOptions[it]) }
                                )
                            }
                        }
                    }
                }

                1 -> {
                    // BIBLIOTHÈQUE & SÉCURITÉ WHATSAPP
                    item {
                        GlassCard(borderColor = SpotifyGreen.copy(alpha = 0.5f)) {
                            Column {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    androidx.compose.material3.Icon(
                                        Icons.Default.Security,
                                        contentDescription = null,
                                        tint = SpotifyGreen
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = "Bouclier Anti-WhatsApp & Filtres Parasites",
                                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                        color = TextPrimary
                                    )
                                }

                                Spacer(modifier = Modifier.height(16.dp))

                                FuturisticSwitchRow(
                                    title = "Exclusion stricte WhatsApp",
                                    subtitle = "Bloque 100% des notes vocales, mémos audio et dossiers WhatsApp de la bibliothèque",
                                    checked = settings.whatsAppExclusion,
                                    onCheckedChange = {
                                        viewModel.updateExcludeWhatsApp(it)
                                        viewModel.rescanLibrary(context)
                                    }
                                )

                                FuturisticSwitchRow(
                                    title = "Exclusion des enregistrements d'appels",
                                    subtitle = "Filtre les dossiers CallRecorder, Record, VoiceRecorder",
                                    checked = settings.whatsAppExclusion,
                                    onCheckedChange = {
                                        viewModel.updateExcludeRecordings(it)
                                        viewModel.rescanLibrary(context)
                                    }
                                )
                            }
                        }
                    }

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
                                        text = "Indexation & Analyse Système",
                                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                        color = TextPrimary
                                    )
                                }

                                Spacer(modifier = Modifier.height(16.dp))

                                Button(
                                    onClick = {
                                        viewModel.rescanLibrary(context)
                                        Toast.makeText(context, "Analyse de la bibliothèque lancée", Toast.LENGTH_SHORT).show()
                                    },
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = SpotifyGreen,
                                        contentColor = androidx.compose.ui.graphics.Color.Black
                                    ),
                                    shape = RoundedCornerShape(10.dp),
                                    modifier = Modifier.fillMaxWidth().height(48.dp)
                                ) {
                                    Text(
                                        text = "Forcer la réindexation complète",
                                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold)
                                    )
                                }
                            }
                        }
                    }
                }

                2 -> {
                    // INTERFACE
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
                                        text = "Thème & Style Visuel",
                                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                        color = TextPrimary
                                    )
                                }

                                Spacer(modifier = Modifier.height(16.dp))

                                val themeOptions = listOf("Dark", "OLED Noir Absolu")
                                val themeIndex = if (settings.themePreference == "oled") 1 else 0
                                FuturisticChoiceRow(
                                    title = "Palette d'affichage",
                                    subtitle = "Contraste et économie d'énergie écran",
                                    options = themeOptions,
                                    selectedIndex = themeIndex,
                                    onOptionSelected = {
                                        viewModel.updateThemePreference(if (it == 1) "oled" else "dark")
                                    }
                                )

                                FuturisticSwitchRow(
                                    title = "Animations 60/120 FPS",
                                    subtitle = "Fluidité maximale des transitions d'interface",
                                    checked = settings.animationLevel == "MAXIMUM",
                                    onCheckedChange = { viewModel.updateFluidAnimations(it) }
                                )
                            }
                        }
                    }
                }

                3 -> {
                    // PERFORMANCE & BATTERIE
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
                                        text = "Optimisation Énergétique",
                                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                        color = TextPrimary
                                    )
                                }

                                Spacer(modifier = Modifier.height(16.dp))

                                FuturisticSwitchRow(
                                    title = "Mode Économie de Batterie",
                                    subtitle = "Désactive le visualiseur dynamique lorsque la batterie est basse",
                                    checked = settings.batterySaverMode,
                                    onCheckedChange = { viewModel.updateBatterySaverMode(it) }
                                )

                                FuturisticSwitchRow(
                                    title = "Persistance en Arrière-plan",
                                    subtitle = "Maintient le service audio actif pour une reprise instantanée",
                                    checked = settings.autoResumePosition,
                                    onCheckedChange = { viewModel.updateBackgroundPersistence(it) }
                                )
                            }
                        }
                    }
                }

                4 -> {
                    // AVANCÉ & RÉINITIALISATION
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
                                        text = "Architecture & Déclaration",
                                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                        color = TextPrimary
                                    )
                                }

                                Spacer(modifier = Modifier.height(16.dp))

                                Text(
                                    text = "Ce lecteur est 100% autonome, zéro IA générative, zéro fuite de données privées. Aucun fichier audio de démonstration n'est injecté.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = TextSecondary
                                )

                                Spacer(modifier = Modifier.height(16.dp))

                                Button(
                                    onClick = { showResetDialog = true },
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = SpotifySurfaceHighlight,
                                        contentColor = androidx.compose.ui.graphics.Color(0xFFFF5252)
                                    ),
                                    shape = RoundedCornerShape(10.dp),
                                    modifier = Modifier.fillMaxWidth().height(48.dp)
                                ) {
                                    Text(
                                        text = "Réinitialiser tous les paramètres",
                                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showResetDialog) {
        AlertDialog(
            onDismissRequest = { showResetDialog = false },
            title = {
                Text(
                    text = "Réinitialiser les paramètres ?",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    color = TextPrimary
                )
            },
            text = {
                Text(
                    text = "Tous vos réglages personnalisés (égaliseur, crossfade, filtres de dossiers) seront remis à zéro.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextSecondary
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.resetSettings()
                        showResetDialog = false
                        Toast.makeText(context, "Paramètres réinitialisés", Toast.LENGTH_SHORT).show()
                    }
                ) {
                    Text("Réinitialiser", color = androidx.compose.ui.graphics.Color(0xFFFF5252), fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showResetDialog = false }) {
                    Text("Annuler", color = TextSecondary)
                }
            },
            containerColor = SpotifyDarkSurface
        )
    }
}
