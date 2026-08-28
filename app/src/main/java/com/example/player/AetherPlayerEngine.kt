package com.example.player

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.MediaPlayer
import android.media.audiofx.Equalizer
import android.media.audiofx.LoudnessEnhancer
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
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

class AetherPlayerEngine(
    private val context: Context,
    private val audioRepository: AudioRepository,
    private val settingsManager: SettingsManager
) {
    private val scope = CoroutineScope(Dispatchers.Main + Job())

    private var activePlayer: MediaPlayer? = null
    private var fadingOutPlayer: MediaPlayer? = null

    private var equalizer: Equalizer? = null
    private var loudnessEnhancer: LoudnessEnhancer? = null

    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private var audioFocusRequest: AudioFocusRequest? = null

    private val _playbackState = MutableStateFlow(PlaybackState())
    val playbackState: StateFlow<PlaybackState> = _playbackState.asStateFlow()

    private var progressTrackingJob: Job? = null
    private var visualizerJob: Job? = null
    private var currentSettings: AetherSettings = AetherSettings()

    // Noisy Receiver (unplugging headphones)
    private val noisyReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == AudioManager.ACTION_AUDIO_BECOMING_NOISY) {
                pause()
            }
        }
    }

    init {
        val filter = IntentFilter(AudioManager.ACTION_AUDIO_BECOMING_NOISY)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.registerReceiver(noisyReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            context.registerReceiver(noisyReceiver, filter)
        }

        scope.launch {
            settingsManager.settingsFlow.collect { settings ->
                currentSettings = settings
                applySettings(settings)
            }
        }

        // Restore last playback state with validation
        scope.launch {
            restoreLastPlaybackSession()
        }
    }

    private fun isTrackAccessible(track: Track): Boolean {
        return try {
            val file = java.io.File(track.path)
            if (file.exists() && file.length() > 0) return true
            context.contentResolver.openAssetFileDescriptor(track.uri, "r")?.use { true } ?: false
        } catch (e: Exception) {
            false
        }
    }

    private suspend fun restoreLastPlaybackSession() {
        val settings = settingsManager.settingsFlow.first()
        if (settings.lastTrackId != -1L) {
            val track = audioRepository.getTrackById(settings.lastTrackId)
            if (track != null && isTrackAccessible(track)) {
                val queueIds = if (settings.lastQueueIds.isNotEmpty()) {
                    settings.lastQueueIds.split(",").mapNotNull { it.toLongOrNull() }
                } else emptyList()

                val rawQueue = if (queueIds.isNotEmpty()) {
                    audioRepository.getTracksByIds(queueIds)
                } else {
                    listOf(track)
                }
                val validQueue = rawQueue.filter { isTrackAccessible(it) }.ifEmpty { listOf(track) }

                val validIndex = settings.lastQueueIndex.coerceIn(0, (validQueue.size - 1).coerceAtLeast(0))

                _playbackState.value = _playbackState.value.copy(
                    currentTrack = track,
                    durationMs = track.durationMs,
                    currentPositionMs = if (settings.autoResumePosition) settings.lastPositionMs.coerceIn(0L, track.durationMs) else 0L,
                    queue = validQueue,
                    currentQueueIndex = validIndex,
                    isShuffle = settings.shuffleEnabled,
                    repeatMode = settings.repeatMode
                )
            } else {
                // Track no longer exists or file was moved/deleted - clean up session
                settingsManager.resetAllPlaybackData()
            }
        }
    }

    fun playTrackList(tracks: List<Track>, startIndex: Int = 0, autoPlay: Boolean = true) {
        if (tracks.isEmpty()) return
        val validIndex = startIndex.coerceIn(0, tracks.lastIndex)
        val selectedTrack = tracks[validIndex]

        _playbackState.value = _playbackState.value.copy(
            queue = tracks,
            currentQueueIndex = validIndex,
            currentTrack = selectedTrack,
            durationMs = selectedTrack.durationMs,
            currentPositionMs = 0L
        )

        if (autoPlay) {
            play(selectedTrack, 0L)
        }
        saveSessionState()
    }

    fun playTrack(track: Track, autoPlay: Boolean = true) {
        val currentQueue = _playbackState.value.queue.toMutableList()
        val index = currentQueue.indexOfFirst { it.id == track.id }
        val newIndex = if (index != -1) index else {
            currentQueue.add(track)
            currentQueue.lastIndex
        }

        _playbackState.value = _playbackState.value.copy(
            queue = currentQueue,
            currentQueueIndex = newIndex,
            currentTrack = track,
            durationMs = track.durationMs,
            currentPositionMs = 0L
        )

        if (autoPlay) {
            play(track, 0L)
        }
        saveSessionState()
    }

    fun playQueueIndex(index: Int) {
        val queue = _playbackState.value.queue
        if (index in queue.indices) {
            val track = queue[index]
            _playbackState.value = _playbackState.value.copy(
                currentTrack = track,
                currentQueueIndex = index,
                durationMs = track.durationMs,
                currentPositionMs = 0L
            )
            play(track, 0L)
        }
    }

    fun addToQueue(track: Track) {
        val currentQueue = _playbackState.value.queue.toMutableList()
        currentQueue.add(track)
        _playbackState.value = _playbackState.value.copy(queue = currentQueue)
        saveSessionState()
    }

    fun playNext(track: Track) {
        val currentQueue = _playbackState.value.queue.toMutableList()
        val insertIndex = (_playbackState.value.currentQueueIndex + 1).coerceAtMost(currentQueue.size)
        currentQueue.add(insertIndex, track)
        _playbackState.value = _playbackState.value.copy(queue = currentQueue)
        saveSessionState()
    }

    fun play(track: Track? = _playbackState.value.currentTrack, fromPositionMs: Long? = null) {
        val targetTrack = track ?: _playbackState.value.currentTrack ?: return
        val startPosition = fromPositionMs ?: _playbackState.value.currentPositionMs

        if (!requestAudioFocus()) return

        val crossfadeEnabled = currentSettings.crossfadeEnabled
        val crossfadeDuration = (currentSettings.crossfadeDuration * 1000).toInt()

        if (crossfadeEnabled && activePlayer != null && activePlayer?.isPlaying == true && crossfadeDuration > 0) {
            performCrossfade(targetTrack, startPosition, crossfadeDuration)
        } else {
            startFreshPlayback(targetTrack, startPosition)
        }

        _playbackState.value = _playbackState.value.copy(
            currentTrack = targetTrack,
            isPlaying = true,
            durationMs = targetTrack.durationMs
        )

        startProgressTracking()
        startVisualizerSimulation()
        saveSessionState()

        scope.launch {
            audioRepository.recordPlaybackHistory(targetTrack.id, startPosition)
        }
    }

    private fun startFreshPlayback(track: Track, positionMs: Long) {
        releasePlayer(activePlayer)
        try {
            val player = MediaPlayer().apply {
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .build()
                )
                setDataSource(context, track.uri)
                prepare()
                if (positionMs > 0 && positionMs < duration) {
                    seekTo(positionMs.toInt())
                }
                setVolume(1.0f, 1.0f)
                start()
                setOnCompletionListener {
                    handleTrackCompletion()
                }
            }
            activePlayer = player
            attachAudioEffects(player.audioSessionId)
        } catch (e: Exception) {
            e.printStackTrace()
            _playbackState.value = _playbackState.value.copy(isPlaying = false)
        }
    }

    private fun performCrossfade(newTrack: Track, positionMs: Long, durationMs: Int) {
        val oldPlayer = activePlayer
        fadingOutPlayer = oldPlayer

        try {
            val newPlayer = MediaPlayer().apply {
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .build()
                )
                setDataSource(context, newTrack.uri)
                prepare()
                if (positionMs > 0 && positionMs < duration) {
                    seekTo(positionMs.toInt())
                }
                setVolume(0.0f, 0.0f)
                start()
                setOnCompletionListener {
                    handleTrackCompletion()
                }
            }
            activePlayer = newPlayer
            attachAudioEffects(newPlayer.audioSessionId)

            // Crossfade volume ramping
            scope.launch {
                val steps = 30
                val intervalMs = (durationMs / steps).toLong().coerceAtLeast(10L)
                for (i in 0..steps) {
                    val progress = i.toFloat() / steps.toFloat()
                    val inVolume = sin(progress * (Math.PI / 2)).toFloat()
                    val outVolume = cos(progress * (Math.PI / 2)).toFloat()

                    newPlayer.setVolume(inVolume, inVolume)
                    oldPlayer?.setVolume(outVolume, outVolume)
                    delay(intervalMs)
                }
                releasePlayer(oldPlayer)
                fadingOutPlayer = null
            }
        } catch (e: Exception) {
            e.printStackTrace()
            startFreshPlayback(newTrack, positionMs)
        }
    }

    fun pause() {
        activePlayer?.let { player ->
            try {
                if (player.isPlaying) {
                    player.pause()
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
        _playbackState.value = _playbackState.value.copy(isPlaying = false)
        stopProgressTracking()
        stopVisualizerSimulation()
        saveSessionState()
    }

    fun togglePlayPause() {
        if (_playbackState.value.isPlaying) {
            pause()
        } else {
            play()
        }
    }

    fun next() {
        val state = _playbackState.value
        val queue = state.queue
        if (queue.isEmpty()) return

        val nextIndex = if (state.isShuffle) {
            val unplayedIndices = queue.indices.filter { it != state.currentQueueIndex }
            if (unplayedIndices.isNotEmpty()) unplayedIndices.random() else 0
        } else {
            (state.currentQueueIndex + 1) % queue.size
        }

        val track = queue[nextIndex]
        _playbackState.value = state.copy(
            currentTrack = track,
            currentQueueIndex = nextIndex,
            durationMs = track.durationMs,
            currentPositionMs = 0L
        )
        play(track, 0L)
    }

    fun previous() {
        val state = _playbackState.value
        val queue = state.queue
        if (queue.isEmpty()) return

        if (state.currentPositionMs > 3000) {
            seekTo(0L)
            return
        }

        val prevIndex = if (state.currentQueueIndex > 0) {
            state.currentQueueIndex - 1
        } else {
            queue.lastIndex
        }

        val track = queue[prevIndex]
        _playbackState.value = state.copy(
            currentTrack = track,
            currentQueueIndex = prevIndex,
            durationMs = track.durationMs,
            currentPositionMs = 0L
        )
        play(track, 0L)
    }

    fun seekTo(positionMs: Long) {
        val boundedPos = positionMs.coerceIn(0L, _playbackState.value.durationMs)
        _playbackState.value = _playbackState.value.copy(currentPositionMs = boundedPos)
        activePlayer?.let { player ->
            try {
                player.seekTo(boundedPos.toInt())
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
        saveSessionState()
    }

    fun setShuffle(enabled: Boolean) {
        _playbackState.value = _playbackState.value.copy(isShuffle = enabled)
        saveSessionState()
    }

    fun toggleRepeatMode() {
        val current = _playbackState.value.repeatMode
        val next = when (current) {
            RepeatMode.OFF -> RepeatMode.ALL
            RepeatMode.ALL -> RepeatMode.ONE
            else -> RepeatMode.OFF
        }
        _playbackState.value = _playbackState.value.copy(repeatMode = next)
        saveSessionState()
    }

    fun setPlaybackSpeed(speed: Float) {
        _playbackState.value = _playbackState.value.copy(playbackSpeed = speed)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            try {
                activePlayer?.let { player ->
                    val params = player.playbackParams
                    params.speed = speed
                    player.playbackParams = params
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun setQueue(newQueue: List<Track>, currentIdx: Int) {
        val validIdx = currentIdx.coerceIn(0, (newQueue.size - 1).coerceAtLeast(0))
        val currentTrack = if (newQueue.isNotEmpty()) newQueue[validIdx] else null
        _playbackState.value = _playbackState.value.copy(
            queue = newQueue,
            currentQueueIndex = validIdx,
            currentTrack = currentTrack
        )
        saveSessionState()
    }

    fun removeQueueItem(index: Int) {
        val currentQueue = _playbackState.value.queue.toMutableList()
        if (index in currentQueue.indices) {
            currentQueue.removeAt(index)
            val currentIdx = _playbackState.value.currentQueueIndex
            val newIdx = when {
                index < currentIdx -> currentIdx - 1
                index == currentIdx -> currentIdx.coerceAtMost(currentQueue.lastIndex)
                else -> currentIdx
            }
            _playbackState.value = _playbackState.value.copy(
                queue = currentQueue,
                currentQueueIndex = newIdx
            )
            saveSessionState()
        }
    }

    private fun handleTrackCompletion() {
        val state = _playbackState.value
        when (state.repeatMode) {
            RepeatMode.ONE -> {
                seekTo(0L)
                play()
            }
            RepeatMode.ALL -> {
                next()
            }
            else -> {
                if (state.currentQueueIndex < state.queue.lastIndex) {
                    next()
                } else {
                    pause()
                    seekTo(0L)
                }
            }
        }
    }

    private fun startProgressTracking() {
        progressTrackingJob?.cancel()
        progressTrackingJob = scope.launch {
            while (isActive) {
                activePlayer?.let { player ->
                    try {
                        if (player.isPlaying) {
                            _playbackState.value = _playbackState.value.copy(
                                currentPositionMs = player.currentPosition.toLong(),
                                durationMs = player.duration.toLong()
                            )
                        }
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }
                delay(200L)
            }
        }
    }

    private fun stopProgressTracking() {
        progressTrackingJob?.cancel()
        progressTrackingJob = null
    }

    private fun startVisualizerSimulation() {
        visualizerJob?.cancel()
        visualizerJob = scope.launch {
            var phase = 0f
            while (isActive) {
                if (_playbackState.value.isPlaying) {
                    val freqs = FloatArray(32) { i ->
                        val base = (sin(phase + i * 0.4f) + 1f) / 2f
                        val harmonic = (cos(phase * 1.5f + i * 0.8f) + 1f) / 4f
                        ((base + harmonic) * 0.8f).coerceIn(0.05f, 1.0f)
                    }
                    _playbackState.value = _playbackState.value.copy(visualizerFrequencies = freqs)
                    phase += 0.2f
                } else {
                    _playbackState.value = _playbackState.value.copy(visualizerFrequencies = FloatArray(32) { 0f })
                }
                delay(60L)
            }
        }
    }

    private fun stopVisualizerSimulation() {
        visualizerJob?.cancel()
        visualizerJob = null
        _playbackState.value = _playbackState.value.copy(visualizerFrequencies = FloatArray(32) { 0f })
    }

    private fun attachAudioEffects(audioSessionId: Int) {
        try {
            equalizer?.release()
            equalizer = Equalizer(0, audioSessionId).apply {
                enabled = true
            }
            loudnessEnhancer?.release()
            loudnessEnhancer = LoudnessEnhancer(audioSessionId).apply {
                enabled = currentSettings.loudnessNormalized
                if (enabled) {
                    setTargetGain((currentSettings.loudnessGain * 100).toInt())
                }
            }
            applyEqualizerPreset(currentSettings.equalizerPreset)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun applyEqualizerPreset(presetIndex: Int) {
        val eq = equalizer ?: return
        try {
            val numBands = eq.numberOfBands.toInt()
            val minBandLevel = eq.bandLevelRange[0]
            val maxBandLevel = eq.bandLevelRange[1]
            val midLevel = 0.toShort()

            val gains: List<Short> = when (presetIndex) {
                1 -> listOf((maxBandLevel * 0.8f).toInt().toShort(), (maxBandLevel * 0.4f).toInt().toShort(), midLevel, midLevel, midLevel) // Bass Boost
                2 -> listOf((maxBandLevel * 0.6f).toInt().toShort(), midLevel, (maxBandLevel * 0.5f).toInt().toShort(), midLevel, (maxBandLevel * 0.7f).toInt().toShort()) // Cyber Synth / Electronic
                3 -> listOf(midLevel, midLevel, (maxBandLevel * 0.7f).toInt().toShort(), (maxBandLevel * 0.4f).toInt().toShort(), midLevel) // Vocal
                4 -> listOf((maxBandLevel * 0.6f).toInt().toShort(), midLevel, (maxBandLevel * 0.5f).toInt().toShort(), (maxBandLevel * 0.6f).toInt().toShort(), (maxBandLevel * 0.4f).toInt().toShort()) // Rock
                else -> listOf(midLevel, midLevel, midLevel, midLevel, midLevel) // Flat
            }

            for (i in 0 until numBands) {
                val gain = if (i < gains.size) gains[i] else midLevel
                eq.setBandLevel(i.toShort(), gain)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun applySettings(settings: AetherSettings) {
        applyEqualizerPreset(settings.equalizerPreset)
        loudnessEnhancer?.apply {
            enabled = settings.loudnessNormalized
            if (enabled) {
                setTargetGain((settings.loudnessGain * 100).toInt())
            }
        }
    }

    private fun requestAudioFocus(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val request = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .build()
                )
                .setOnAudioFocusChangeListener { focusChange ->
                    when (focusChange) {
                        AudioManager.AUDIOFOCUS_LOSS -> pause()
                        AudioManager.AUDIOFOCUS_LOSS_TRANSIENT -> pause()
                        AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK -> activePlayer?.setVolume(0.2f, 0.2f)
                        AudioManager.AUDIOFOCUS_GAIN -> {
                            activePlayer?.setVolume(1.0f, 1.0f)
                            play()
                        }
                    }
                }
                .build()
            audioFocusRequest = request
            audioManager.requestAudioFocus(request) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
        } else {
            @Suppress("DEPRECATION")
            audioManager.requestAudioFocus(
                { focusChange ->
                    if (focusChange == AudioManager.AUDIOFOCUS_LOSS) pause()
                },
                AudioManager.STREAM_MUSIC,
                AudioManager.AUDIOFOCUS_GAIN
            ) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
        }
    }

    private fun releasePlayer(player: MediaPlayer?) {
        try {
            player?.stop()
            player?.release()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun saveSessionState() {
        val state = _playbackState.value
        val track = state.currentTrack
        val queueIds = state.queue.joinToString(",") { it.id.toString() }

        scope.launch {
            settingsManager.updatePlaybackSession(
                trackId = track?.id ?: -1L,
                positionMs = state.currentPositionMs,
                queueIds = queueIds,
                queueIndex = state.currentQueueIndex,
                shuffle = state.isShuffle,
                repeat = state.repeatMode
            )
        }
    }
}
