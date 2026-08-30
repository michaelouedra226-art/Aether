package com.example.playback

import android.content.Context
import android.content.Intent
import android.os.Build
import com.example.data.model.Track
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * PlayerConnector acts as the single, lightweight bridge between UI/ViewModel and MusicPlaybackService.
 * Strictly adheres to rule: UI never talks directly to ExoPlayer, and only the Service owns ExoPlayer.
 */
class PlayerConnector(private val context: Context) {

    private val _playbackState = MutableStateFlow(PlaybackState())
    val playbackState: StateFlow<PlaybackState> = _playbackState.asStateFlow()

    private fun sendServiceCommand(action: String, extrasBuilder: (Intent.() -> Unit)? = null) {
        val intent = Intent(context, MusicPlaybackService::class.java).apply {
            this.action = action
            extrasBuilder?.invoke(this)
        }
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun playQueue(tracks: List<Track>, startIndex: Int = 0, startPositionMs: Long = 0L) {
        if (tracks.isEmpty()) return
        val trackIds = tracks.map { it.id }.toLongArray()
        
        // Optimistically set queue in state
        _playbackState.value = _playbackState.value.copy(
            queue = tracks,
            currentQueueIndex = startIndex.coerceIn(0, tracks.lastIndex),
            currentTrack = tracks.getOrNull(startIndex),
            isLoading = true
        )

        sendServiceCommand(PlayerCommands.ACTION_PLAY_QUEUE) {
            putExtra(PlayerCommands.EXTRA_TRACK_IDS, trackIds)
            putExtra(PlayerCommands.EXTRA_START_INDEX, startIndex)
            putExtra(PlayerCommands.EXTRA_POSITION_MS, startPositionMs)
        }
    }

    fun playTrack(track: Track, allTracks: List<Track> = emptyList()) {
        val list = if (allTracks.isNotEmpty()) allTracks else listOf(track)
        val index = list.indexOfFirst { it.id == track.id }.coerceAtLeast(0)
        playQueue(list, index)
    }

    fun playQueueIndex(index: Int) {
        sendServiceCommand(PlayerCommands.ACTION_SEEK_TO_INDEX) {
            putExtra(PlayerCommands.EXTRA_START_INDEX, index)
        }
    }

    fun play() {
        sendServiceCommand(PlayerCommands.ACTION_PLAY)
    }

    fun pause() {
        sendServiceCommand(PlayerCommands.ACTION_PAUSE)
    }

    fun togglePlayPause() {
        sendServiceCommand(PlayerCommands.ACTION_TOGGLE_PLAY_PAUSE)
    }

    fun next() {
        sendServiceCommand(PlayerCommands.ACTION_NEXT)
    }

    fun previous() {
        sendServiceCommand(PlayerCommands.ACTION_PREV)
    }

    fun seekTo(positionMs: Long) {
        _playbackState.value = _playbackState.value.copy(currentPositionMs = positionMs)
        sendServiceCommand(PlayerCommands.ACTION_SEEK) {
            putExtra(PlayerCommands.EXTRA_POSITION_MS, positionMs)
        }
    }

    fun setShuffle(enabled: Boolean) {
        sendServiceCommand(PlayerCommands.ACTION_SET_SHUFFLE) {
            putExtra(PlayerCommands.EXTRA_SHUFFLE_ENABLED, enabled)
        }
    }

    fun toggleShuffle() {
        setShuffle(!_playbackState.value.isShuffle)
    }

    fun setRepeatMode(mode: RepeatMode) {
        sendServiceCommand(PlayerCommands.ACTION_SET_REPEAT) {
            putExtra(PlayerCommands.EXTRA_REPEAT_MODE, mode.name)
        }
    }

    fun toggleRepeatMode() {
        val nextMode = when (_playbackState.value.repeatMode) {
            RepeatMode.OFF -> RepeatMode.ALL
            RepeatMode.ALL -> RepeatMode.ONE
            RepeatMode.ONE -> RepeatMode.OFF
        }
        setRepeatMode(nextMode)
    }

    fun setPlaybackSpeed(speed: Float) {
        sendServiceCommand(PlayerCommands.ACTION_SET_SPEED) {
            putExtra(PlayerCommands.EXTRA_SPEED, speed)
        }
    }

    fun addToQueue(track: Track) {
        val currentQueue = _playbackState.value.queue
        val updatedQueue = currentQueue + track
        _playbackState.value = _playbackState.value.copy(queue = updatedQueue)

        sendServiceCommand(PlayerCommands.ACTION_ADD_TO_QUEUE) {
            putExtra(PlayerCommands.EXTRA_TRACK_ID, track.id)
        }
    }

    fun playNext(track: Track) {
        val currentQueue = _playbackState.value.queue.toMutableList()
        val currentIndex = _playbackState.value.currentQueueIndex
        val insertIndex = (currentIndex + 1).coerceAtMost(currentQueue.size)
        currentQueue.add(insertIndex, track)
        _playbackState.value = _playbackState.value.copy(queue = currentQueue)

        sendServiceCommand(PlayerCommands.ACTION_PLAY_NEXT) {
            putExtra(PlayerCommands.EXTRA_TRACK_ID, track.id)
        }
    }

    fun removeQueueItem(index: Int) {
        val currentQueue = _playbackState.value.queue.toMutableList()
        if (index in currentQueue.indices) {
            currentQueue.removeAt(index)
            _playbackState.value = _playbackState.value.copy(queue = currentQueue)
            sendServiceCommand(PlayerCommands.ACTION_REMOVE_QUEUE_INDEX) {
                putExtra(PlayerCommands.EXTRA_REMOVE_INDEX, index)
            }
        }
    }

    fun reorderQueue(fromIndex: Int, toIndex: Int) {
        val currentQueue = _playbackState.value.queue.toMutableList()
        if (fromIndex in currentQueue.indices && toIndex in currentQueue.indices) {
            val item = currentQueue.removeAt(fromIndex)
            currentQueue.add(toIndex, item)
            _playbackState.value = _playbackState.value.copy(queue = currentQueue)
            sendServiceCommand(PlayerCommands.ACTION_REORDER_QUEUE) {
                putExtra(PlayerCommands.EXTRA_FROM_INDEX, fromIndex)
                putExtra(PlayerCommands.EXTRA_TO_INDEX, toIndex)
            }
        }
    }

    fun clearQueue() {
        _playbackState.value = PlaybackState()
        sendServiceCommand(PlayerCommands.ACTION_CLEAR_QUEUE)
    }

    fun stop() {
        sendServiceCommand(PlayerCommands.ACTION_STOP)
    }

    /**
     * Internal state updater called ONLY by MusicPlaybackService when ExoPlayer state changes.
     */
    internal fun updateServiceState(newState: PlaybackState) {
        _playbackState.value = newState
    }

    internal fun updatePosition(positionMs: Long, durationMs: Long) {
        _playbackState.value = _playbackState.value.copy(
            currentPositionMs = positionMs,
            durationMs = if (durationMs > 0) durationMs else _playbackState.value.durationMs
        )
    }

    internal fun updateFrequencies(frequencies: List<Float>) {
        _playbackState.value = _playbackState.value.copy(
            visualizerFrequencies = frequencies
        )
    }

    internal fun setErrorMessage(message: String?) {
        _playbackState.value = _playbackState.value.copy(
            errorMessage = message
        )
    }
}
