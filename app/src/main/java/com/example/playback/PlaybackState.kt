package com.example.playback

import com.example.data.model.Track

enum class RepeatMode {
    OFF, ALL, ONE
}

data class PlaybackState(
    val currentTrack: Track? = null,
    val isPlaying: Boolean = false,
    val isLoading: Boolean = false,
    val currentPositionMs: Long = 0L,
    val durationMs: Long = 0L,
    val isShuffle: Boolean = false,
    val repeatMode: RepeatMode = RepeatMode.OFF,
    val playbackSpeed: Float = 1.0f,
    val queue: List<Track> = emptyList(),
    val currentQueueIndex: Int = 0,
    val visualizerFrequencies: List<Float> = emptyList(),
    val errorMessage: String? = null
)
