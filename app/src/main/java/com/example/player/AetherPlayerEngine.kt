package com.example.player

import android.content.Context
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackException
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import com.example.data.model.Track
import com.example.data.preferences.AetherSettings
import com.example.data.preferences.SettingsManager
import com.example.data.repository.AudioRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.File
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

object RepeatMode {
    const val OFF = "OFF"
    const val ALL = "ALL"
    const val ONE = "ONE"
}

data class PlaybackState(
    val currentTrack: Track? = null,
    val isPlaying: Boolean = false,
    val currentPositionMs: Long = 0L,
    val durationMs: Long = 0L,
    val queue: List<Track> = emptyList(),
    val currentQueueIndex: Int = -1,
    val isShuffle: Boolean = false,
    val repeatMode: String = RepeatMode.OFF, // OFF, ALL, ONE
    val playbackSpeed: Float = 1.0f,
    val visualizerFrequencies: FloatArray = FloatArray(32) { 0f }
) {
    val currentIndex: Int
        get() = currentQueueIndex

    val isShuffleEnabled: Boolean
        get() = isShuffle

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        other as PlaybackState
        return currentTrack == other.currentTrack &&
                isPlaying == other.isPlaying &&
                currentPositionMs == other.currentPositionMs &&
                durationMs == other.durationMs &&
                currentQueueIndex == other.currentQueueIndex &&
                isShuffle == other.isShuffle &&
                repeatMode == other.repeatMode &&
                playbackSpeed == other.playbackSpeed &&
                queue == other.queue
    }

    override fun hashCode(): Int {
        var result = currentTrack?.hashCode() ?: 0
        result = 31 * result + isPlaying.hashCode()
        result = 31 * result + currentPositionMs.hashCode()
        result = 31 * result + durationMs.hashCode()
        result = 31 * result + currentQueueIndex
        result = 31 * result + isShuffle.hashCode()
        result = 31 * result + repeatMode.hashCode()
        result = 31 * result + playbackSpeed.hashCode()
        result = 31 * result + queue.hashCode()
        return result
    }
}

/**
 * AetherPlayerEngine - Modern 100% Local Media3 ExoPlayer Engine.
 * Ultra-stable, handles playlist transitions, audio focus, and session persistence natively.
 */
class AetherPlayerEngine(
    private val context: Context,
    private val audioRepository: AudioRepository,
    private val settingsManager: SettingsManager
) {
    private val scope = CoroutineScope(Dispatchers.Main + Job())

    val exoPlayer: ExoPlayer by lazy {
        val audioAttributes = AudioAttributes.Builder()
            .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
            .setUsage(C.USAGE_MEDIA)
            .build()

        ExoPlayer.Builder(context)
            .setAudioAttributes(audioAttributes, /* handleAudioFocus = */ true)
            .setHandleAudioBecomingNoisy(true)
            .setWakeMode(C.WAKE_MODE_LOCAL)
            .build().apply {
                addListener(playerListener)
            }
    }

    private val _playbackState = MutableStateFlow(PlaybackState())
    val playbackState: StateFlow<PlaybackState> = _playbackState.asStateFlow()

    private var currentQueueList: List<Track> = emptyList()
    private var progressTrackingJob: Job? = null
    private var visualizerJob: Job? = null
    private var currentSettings: AetherSettings = AetherSettings()

    private val playerListener = object : Player.Listener {
        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
            val index = exoPlayer.currentMediaItemIndex
            val track = if (index in currentQueueList.indices) {
                currentQueueList[index]
            } else {
                val mediaId = mediaItem?.mediaId?.toLongOrNull()
                if (mediaId != null) currentQueueList.find { it.id == mediaId } else null
            }

            val duration = if (track != null && track.durationMs > 0) {
                track.durationMs
            } else {
                exoPlayer.duration.coerceAtLeast(0L)
            }

            _playbackState.value = _playbackState.value.copy(
                currentTrack = track,
                currentQueueIndex = index,
                durationMs = duration,
                currentPositionMs = exoPlayer.currentPosition.coerceAtLeast(0L)
            )

            if (track != null) {
                scope.launch {
                    audioRepository.recordPlaybackHistory(track.id, 0L)
                }
            }
            saveSessionState()
        }

        override fun onIsPlayingChanged(isPlaying: Boolean) {
            _playbackState.value = _playbackState.value.copy(
                isPlaying = isPlaying,
                currentPositionMs = exoPlayer.currentPosition.coerceAtLeast(0L),
                durationMs = if (exoPlayer.duration > 0) exoPlayer.duration else _playbackState.value.durationMs
            )

            if (isPlaying) {
                startProgressTracking()
                startVisualizerSimulation()
            } else {
                stopProgressTracking()
                stopVisualizerSimulation()
            }
            saveSessionState()
        }

        override fun onPlaybackStateChanged(state: Int) {
            when (state) {
                Player.STATE_READY -> {
                    val dur = exoPlayer.duration
                    if (dur > 0) {
                        _playbackState.value = _playbackState.value.copy(durationMs = dur)
                    }
                }
                Player.STATE_ENDED -> {
                    if (currentSettings.endOfQueueAction == "REPEAT" && currentQueueList.isNotEmpty()) {
                        exoPlayer.seekTo(0, 0L)
                        exoPlayer.play()
                    } else {
                        exoPlayer.pause()
                        exoPlayer.seekTo(0, 0L)
                        _playbackState.value = _playbackState.value.copy(
                            isPlaying = false,
                            currentPositionMs = 0L
                        )
                    }
                }
                Player.STATE_IDLE -> {
                    _playbackState.value = _playbackState.value.copy(isPlaying = false)
                }
                Player.STATE_BUFFERING -> {}
            }
        }

        override fun onShuffleModeEnabledChanged(shuffleModeEnabled: Boolean) {
            _playbackState.value = _playbackState.value.copy(isShuffle = shuffleModeEnabled)
            saveSessionState()
        }

        override fun onRepeatModeChanged(repeatMode: Int) {
            val modeStr = when (repeatMode) {
                Player.REPEAT_MODE_ONE -> RepeatMode.ONE
                Player.REPEAT_MODE_ALL -> RepeatMode.ALL
                else -> RepeatMode.OFF
            }
            _playbackState.value = _playbackState.value.copy(repeatMode = modeStr)
            saveSessionState()
        }

        override fun onPlayerError(error: PlaybackException) {
            // Gracefully recover from unreadable/corrupted files without crashing
            _playbackState.value = _playbackState.value.copy(isPlaying = false)
            if (exoPlayer.hasNextMediaItem()) {
                exoPlayer.seekToNextMediaItem()
                exoPlayer.play()
            }
        }
    }

    init {
        scope.launch {
            settingsManager.settingsFlow.collect { settings ->
                currentSettings = settings
            }
        }

        // Restore last playback session safely
        scope.launch {
            restoreLastPlaybackSession()
        }
    }

    private fun isTrackAccessible(track: Track): Boolean {
        return try {
            val file = File(track.path)
            if (file.exists() && file.length() > 0) return true
            context.contentResolver.openAssetFileDescriptor(track.uri, "r")?.use { true } ?: false
        } catch (_: Exception) {
            false
        }
    }

    private suspend fun restoreLastPlaybackSession() {
        val settings = settingsManager.settingsFlow.first()
        if (!settings.autoResumePosition) return
        if (settings.lastTrackId == -1L) return

        val track = audioRepository.getTrackById(settings.lastTrackId)
        if (track != null && isTrackAccessible(track)) {
            val queueTracks = if (settings.lastQueueIds.isNotBlank()) {
                val ids = settings.lastQueueIds.split(",").mapNotNull { it.toLongOrNull() }
                audioRepository.getTracksByIds(ids).filter { isTrackAccessible(it) }
            } else {
                listOf(track)
            }

            val finalQueue = if (queueTracks.isNotEmpty()) queueTracks else listOf(track)
            val index = settings.lastQueueIndex.coerceIn(0, finalQueue.lastIndex)
            val position = settings.lastPositionMs.coerceAtLeast(0L)

            currentQueueList = finalQueue
            val mediaItems = finalQueue.map { createMediaItem(it) }

            exoPlayer.setMediaItems(mediaItems, index, position)
            exoPlayer.repeatMode = when (settings.repeatMode) {
                RepeatMode.ONE -> Player.REPEAT_MODE_ONE
                RepeatMode.ALL -> Player.REPEAT_MODE_ALL
                else -> Player.REPEAT_MODE_OFF
            }
            exoPlayer.shuffleModeEnabled = settings.shuffleEnabled
            exoPlayer.prepare()

            _playbackState.value = PlaybackState(
                currentTrack = finalQueue.getOrNull(index) ?: track,
                isPlaying = false,
                currentPositionMs = position,
                durationMs = track.durationMs,
                queue = finalQueue,
                currentQueueIndex = index,
                isShuffle = settings.shuffleEnabled,
                repeatMode = settings.repeatMode
            )
        }
    }

    private fun createMediaItem(track: Track): MediaItem {
        val metadata = MediaMetadata.Builder()
            .setTitle(track.title)
            .setArtist(track.artist)
            .setAlbumTitle(track.album)
            .setArtworkUri(track.albumArtUri)
            .build()

        return MediaItem.Builder()
            .setMediaId(track.id.toString())
            .setUri(track.uri)
            .setMediaMetadata(metadata)
            .build()
    }

    fun playTrackList(tracks: List<Track>, startIndex: Int = 0, startPositionMs: Long = 0L) {
        if (tracks.isEmpty()) return
        val validIndex = startIndex.coerceIn(0, tracks.lastIndex)
        val targetTrack = tracks[validIndex]

        currentQueueList = tracks
        val mediaItems = tracks.map { createMediaItem(it) }

        exoPlayer.setMediaItems(mediaItems, validIndex, startPositionMs)
        exoPlayer.prepare()
        exoPlayer.play()

        _playbackState.value = _playbackState.value.copy(
            currentTrack = targetTrack,
            isPlaying = true,
            currentPositionMs = startPositionMs,
            durationMs = targetTrack.durationMs,
            queue = tracks,
            currentQueueIndex = validIndex
        )

        startProgressTracking()
        startVisualizerSimulation()
        saveSessionState()

        scope.launch {
            audioRepository.recordPlaybackHistory(targetTrack.id, startPositionMs)
        }
    }

    fun playTrack(track: Track) {
        val index = currentQueueList.indexOfFirst { it.id == track.id }
        if (index >= 0) {
            playQueueIndex(index)
        } else {
            playTrackList(listOf(track), 0)
        }
    }

    fun playQueueIndex(index: Int) {
        if (index in 0 until exoPlayer.mediaItemCount) {
            exoPlayer.seekTo(index, 0L)
            exoPlayer.play()
        }
    }

    fun play() {
        if (exoPlayer.playbackState == Player.STATE_IDLE && currentQueueList.isNotEmpty()) {
            val index = _playbackState.value.currentQueueIndex.coerceIn(0, currentQueueList.lastIndex)
            playTrackList(currentQueueList, index, _playbackState.value.currentPositionMs)
        } else {
            exoPlayer.play()
        }
    }

    fun pause() {
        exoPlayer.pause()
        _playbackState.value = _playbackState.value.copy(
            isPlaying = false,
            currentPositionMs = exoPlayer.currentPosition.coerceAtLeast(0L)
        )
        stopProgressTracking()
        stopVisualizerSimulation()
        saveSessionState()
    }

    fun togglePlayPause() {
        if (exoPlayer.isPlaying) {
            pause()
        } else {
            play()
        }
    }

    fun next() {
        if (exoPlayer.hasNextMediaItem()) {
            exoPlayer.seekToNextMediaItem()
            exoPlayer.play()
        } else if (_playbackState.value.repeatMode == RepeatMode.ALL && currentQueueList.isNotEmpty()) {
            exoPlayer.seekTo(0, 0L)
            exoPlayer.play()
        }
    }

    fun previous() {
        if (exoPlayer.currentPosition > 3000L) {
            exoPlayer.seekTo(0L)
        } else if (exoPlayer.hasPreviousMediaItem()) {
            exoPlayer.seekToPreviousMediaItem()
            exoPlayer.play()
        } else if (currentQueueList.isNotEmpty()) {
            exoPlayer.seekTo(0L)
        }
    }

    fun seekTo(positionMs: Long) {
        exoPlayer.seekTo(positionMs)
        _playbackState.value = _playbackState.value.copy(currentPositionMs = positionMs)
    }

    fun setShuffle(enabled: Boolean) {
        exoPlayer.shuffleModeEnabled = enabled
        _playbackState.value = _playbackState.value.copy(isShuffle = enabled)
        saveSessionState()
    }

    fun setRepeatMode(mode: String) {
        val exoMode = when (mode) {
            RepeatMode.ONE -> Player.REPEAT_MODE_ONE
            RepeatMode.ALL -> Player.REPEAT_MODE_ALL
            else -> Player.REPEAT_MODE_OFF
        }
        exoPlayer.repeatMode = exoMode
        _playbackState.value = _playbackState.value.copy(repeatMode = mode)
        saveSessionState()
    }

    fun toggleRepeatMode() {
        val nextMode = when (_playbackState.value.repeatMode) {
            RepeatMode.OFF -> RepeatMode.ALL
            RepeatMode.ALL -> RepeatMode.ONE
            else -> RepeatMode.OFF
        }
        setRepeatMode(nextMode)
    }

    fun setPlaybackSpeed(speed: Float) {
        val params = PlaybackParameters(speed)
        exoPlayer.playbackParameters = params
        _playbackState.value = _playbackState.value.copy(playbackSpeed = speed)
    }

    fun playNext(track: Track) {
        val currentIndex = exoPlayer.currentMediaItemIndex
        val insertIndex = (currentIndex + 1).coerceAtMost(currentQueueList.size)
        val mutableQueue = currentQueueList.toMutableList()
        mutableQueue.add(insertIndex, track)
        currentQueueList = mutableQueue

        exoPlayer.addMediaItem(insertIndex, createMediaItem(track))
        _playbackState.value = _playbackState.value.copy(queue = mutableQueue)
        saveSessionState()
    }

    fun addToQueue(track: Track) {
        val mutableQueue = currentQueueList.toMutableList()
        mutableQueue.add(track)
        currentQueueList = mutableQueue

        exoPlayer.addMediaItem(createMediaItem(track))
        _playbackState.value = _playbackState.value.copy(queue = mutableQueue)
        saveSessionState()
    }

    fun removeQueueItem(index: Int) {
        if (index in currentQueueList.indices) {
            val mutableQueue = currentQueueList.toMutableList()
            mutableQueue.removeAt(index)
            currentQueueList = mutableQueue

            exoPlayer.removeMediaItem(index)
            val newIndex = exoPlayer.currentMediaItemIndex
            _playbackState.value = _playbackState.value.copy(
                queue = mutableQueue,
                currentQueueIndex = newIndex
            )
            saveSessionState()
        }
    }

    fun setQueue(newQueue: List<Track>, newIndex: Int) {
        currentQueueList = newQueue
        val mediaItems = newQueue.map { createMediaItem(it) }
        val safeIndex = newIndex.coerceIn(0, (newQueue.size - 1).coerceAtLeast(0))
        val currentPos = exoPlayer.currentPosition

        val wasPlaying = exoPlayer.isPlaying
        exoPlayer.setMediaItems(mediaItems, safeIndex, currentPos)
        exoPlayer.prepare()
        if (wasPlaying) exoPlayer.play()

        _playbackState.value = _playbackState.value.copy(
            queue = newQueue,
            currentQueueIndex = safeIndex,
            currentTrack = newQueue.getOrNull(safeIndex)
        )
        saveSessionState()
    }

    private fun startProgressTracking() {
        progressTrackingJob?.cancel()
        progressTrackingJob = scope.launch {
            while (isActive) {
                if (exoPlayer.isPlaying) {
                    val pos = exoPlayer.currentPosition.coerceAtLeast(0L)
                    val dur = if (exoPlayer.duration > 0) exoPlayer.duration else _playbackState.value.durationMs
                    _playbackState.value = _playbackState.value.copy(
                        currentPositionMs = pos,
                        durationMs = dur
                    )
                }
                delay(400)
            }
        }
    }

    private fun stopProgressTracking() {
        progressTrackingJob?.cancel()
        progressTrackingJob = null
    }

    private fun startVisualizerSimulation() {
        visualizerJob?.cancel()
        if (currentSettings.batterySaverMode) return

        visualizerJob = scope.launch {
            var phase = 0.0
            val bars = FloatArray(32)
            while (isActive) {
                if (exoPlayer.isPlaying) {
                    phase += 0.18
                    for (i in 0 until 32) {
                        val base = sin(phase + i * 0.35) * 0.45 + 0.5
                        val harmonic = cos(phase * 1.6 + i * 0.7) * 0.3
                        val randomSpark = (sin(phase * 3.1 + i * 1.9) * 0.15).toFloat()
                        val rawValue = (base + harmonic).toFloat() + randomSpark
                        bars[i] = (sqrt(rawValue.coerceIn(0.08f, 0.98f)) * 0.92f).coerceIn(0.05f, 1.0f)
                    }
                    _playbackState.value = _playbackState.value.copy(visualizerFrequencies = bars.clone())
                }
                delay(70)
            }
        }
    }

    private fun stopVisualizerSimulation() {
        visualizerJob?.cancel()
        visualizerJob = null
        _playbackState.value = _playbackState.value.copy(visualizerFrequencies = FloatArray(32) { 0f })
    }

    fun release() {
        stopProgressTracking()
        stopVisualizerSimulation()
        try {
            exoPlayer.removeListener(playerListener)
            exoPlayer.release()
        } catch (_: Exception) {}
    }

    private fun saveSessionState() {
        val state = _playbackState.value
        val track = state.currentTrack
        if (track != null) {
            scope.launch {
                settingsManager.savePlaybackState(
                    trackId = track.id,
                    positionMs = state.currentPositionMs,
                    queueIds = state.queue.map { it.id },
                    queueIndex = state.currentQueueIndex,
                    shuffle = state.isShuffle,
                    repeat = state.repeatMode
                )
            }
        }
    }
}
