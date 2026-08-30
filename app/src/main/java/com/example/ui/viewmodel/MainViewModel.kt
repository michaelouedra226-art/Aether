package com.example.ui.viewmodel

import android.app.Application
import android.content.Context
import android.widget.Toast
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.AetherApp
import com.example.data.model.Track
import com.example.data.preferences.AetherSettings
import com.example.data.repository.AlbumGroup
import com.example.data.repository.ArtistGroup
import com.example.data.repository.FolderGroup
import com.example.playback.PlaybackState
import com.example.playback.PlayerConnector
import com.example.playback.RepeatMode
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val app = application as AetherApp
    private val audioRepository = app.audioRepository
    private val playerConnector: PlayerConnector = app.playerConnector
    private val settingsManager = app.settingsManager

    val playbackState: StateFlow<PlaybackState> = playerConnector.playbackState

    val settings: StateFlow<AetherSettings> = settingsManager.settingsFlow.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = AetherSettings()
    )

    val allTracks: StateFlow<List<Track>> = audioRepository.allTracks.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    val recentTracks: StateFlow<List<Track>> = audioRepository.recentTracks.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    val tracksByDuration: StateFlow<List<Track>> = audioRepository.tracksByDuration.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    val artistGroups: StateFlow<List<ArtistGroup>> = audioRepository.artistGroups.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    val albumGroups: StateFlow<List<AlbumGroup>> = audioRepository.albumGroups.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    val folderGroups: StateFlow<List<FolderGroup>> = audioRepository.folderGroups.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    val playbackHistory: StateFlow<List<Track>> = audioRepository.playbackHistory.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    val trackCount: StateFlow<Int> = audioRepository.trackCount.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = 0
    )

    val artistCount: StateFlow<Int> = audioRepository.artistCount.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = 0
    )

    val albumCount: StateFlow<Int> = audioRepository.albumCount.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = 0
    )

    val folderCount: StateFlow<Int> = audioRepository.folderCount.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = 0
    )

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    val searchResults: StateFlow<List<Track>> = _searchQuery.flatMapLatest { query ->
        if (query.isBlank()) {
            flowOf(emptyList())
        } else {
            audioRepository.searchTracks(query)
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    private val _isScanning = MutableStateFlow(false)
    val isScanning: StateFlow<Boolean> = _isScanning.asStateFlow()

    private val _scanMessage = MutableStateFlow<String?>(null)
    val scanMessage: StateFlow<String?> = _scanMessage.asStateFlow()

    // Double back-press tracking
    private var lastBackPressTime = 0L

    fun onSearchQueryChanged(query: String) {
        _searchQuery.value = query
    }

    fun clearSearch() {
        _searchQuery.value = ""
    }

    fun playTrack(track: Track, tracks: List<Track> = emptyList()) {
        if (tracks.isNotEmpty()) {
            val index = tracks.indexOfFirst { it.id == track.id }.coerceAtLeast(0)
            playerConnector.playQueue(tracks, index)
        } else {
            playerConnector.playTrack(track)
        }
    }

    fun playTrackList(tracks: List<Track>, index: Int = 0) {
        playerConnector.playQueue(tracks, index)
    }

    fun playTrackAtIndex(index: Int) {
        playerConnector.playQueueIndex(index)
    }

    fun playNext(track: Track) {
        playerConnector.playNext(track)
    }

    fun addToQueue(track: Track) {
        playerConnector.addToQueue(track)
    }

    fun moveQueueItem(fromIndex: Int, toIndex: Int) {
        playerConnector.reorderQueue(fromIndex, toIndex)
    }

    fun removeFromQueue(index: Int) {
        playerConnector.removeQueueItem(index)
    }

    fun clearQueue() {
        playerConnector.clearQueue()
    }

    fun shuffleAll(tracks: List<Track>) {
        if (tracks.isEmpty()) return
        playerConnector.setShuffle(true)
        playerConnector.playQueue(tracks.shuffled(), 0)
    }

    fun togglePlayPause() {
        playerConnector.togglePlayPause()
    }

    fun next() {
        playerConnector.next()
    }

    fun previous() {
        playerConnector.previous()
    }

    fun seekTo(positionMs: Long) {
        playerConnector.seekTo(positionMs)
    }

    fun setShuffle(enabled: Boolean) {
        playerConnector.setShuffle(enabled)
    }

    fun toggleShuffle() {
        playerConnector.toggleShuffle()
    }

    fun toggleRepeat() {
        playerConnector.toggleRepeatMode()
    }

    fun setPlaybackSpeed(speed: Float) {
        playerConnector.setPlaybackSpeed(speed)
    }

    fun rescanLibrary(context: Context) {
        viewModelScope.launch {
            _isScanning.value = true
            _scanMessage.value = "Indexation des fichiers audio locaux..."
            val count = audioRepository.rescanLibrary()
            _isScanning.value = false
            val msg = if (count > 0) {
                "$count pistes indexées (WhatsApp exclu)"
            } else {
                "Aucune piste trouvée sur le stockage (WhatsApp exclu)"
            }
            _scanMessage.value = msg
            Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
        }
    }

    fun clearScanMessage() {
        _scanMessage.value = null
    }

    // Settings actions
    fun updateCrossfadeEnabled(enabled: Boolean) {
        viewModelScope.launch {
            val current = settings.value
            settingsManager.updateCrossfade(enabled, current.crossfadeDuration, current.crossfadeCurve)
        }
    }

    fun updateCrossfadeDuration(durationSec: Int) {
        viewModelScope.launch {
            val current = settings.value
            settingsManager.updateCrossfade(current.crossfadeEnabled, durationSec.toFloat(), current.crossfadeCurve)
        }
    }

    fun updateGaplessPlayback(enabled: Boolean) {
        viewModelScope.launch { settingsManager.updateGapless(enabled) }
    }

    fun updateAutoResumePosition(enabled: Boolean) {
        viewModelScope.launch { settingsManager.updateAutoResume(enabled) }
    }

    fun updateEndOfQueueAction(action: String) {
        viewModelScope.launch { settingsManager.updateEndOfQueueAction(action) }
    }

    fun updateExcludeWhatsApp(enabled: Boolean) {
        viewModelScope.launch { settingsManager.updateWhatsAppExclusion(enabled) }
    }

    fun updateRescanFrequency(frequency: String) {
        viewModelScope.launch { settingsManager.updateRescanFrequency(frequency) }
    }

    fun updateAnimationLevel(level: String) {
        viewModelScope.launch { settingsManager.updateAnimationLevel(level) }
    }

    fun updateUiDensity(density: String) {
        viewModelScope.launch { settingsManager.updateUiDensity(density) }
    }

    fun updateBatterySaverMode(enabled: Boolean) {
        viewModelScope.launch { settingsManager.updateBatterySaver(enabled) }
    }

    fun updateVisualizerEnabled(enabled: Boolean) {
        viewModelScope.launch { settingsManager.updateVisualizerEnabled(enabled) }
    }

    fun resetPlaybackSession(context: Context) {
        viewModelScope.launch {
            settingsManager.resetAllPlaybackData()
            Toast.makeText(context, "Session de lecture réinitialisée", Toast.LENGTH_SHORT).show()
        }
    }

    fun resetAllData(context: Context) {
        viewModelScope.launch {
            playerConnector.stop()
            audioRepository.clearAllData()
            Toast.makeText(context, "Bibliothèque réinitialisée", Toast.LENGTH_SHORT).show()
        }
    }

    /**
     * Handles double back-press from Home screen.
     * Returns true if application should close immediately and stop playback notification.
     */
    fun handleHomeBackPress(context: Context, onExitApp: () -> Unit): Boolean {
        val currentTime = System.currentTimeMillis()
        val delay = settings.value.doubleBackExitDelayMs

        if (currentTime - lastBackPressTime < delay) {
            // Second press within delay window -> Clean stop and exit!
            playerConnector.stop()
            onExitApp()
            return true
        } else {
            // First press -> show toast prompt
            lastBackPressTime = currentTime
            Toast.makeText(context, "Appuyez à nouveau pour quitter", Toast.LENGTH_SHORT).show()
            return false
        }
    }
}
