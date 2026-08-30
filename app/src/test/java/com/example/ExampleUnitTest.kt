package com.example

import com.example.playback.PlaybackState
import com.example.playback.RepeatMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test

class ExampleUnitTest {
    @Test
    fun defaultPlaybackState_isClean() {
        val state = PlaybackState()
        assertNull(state.currentTrack)
        assertFalse(state.isPlaying)
        assertFalse(state.isShuffle)
        assertEquals(RepeatMode.OFF, state.repeatMode)
        assertEquals(0L, state.currentPositionMs)
        assertEquals(1.0f, state.playbackSpeed, 0.001f)
    }

    @Test
    fun repeatMode_cyclesCorrectly() {
        val modes = listOf(RepeatMode.OFF, RepeatMode.ALL, RepeatMode.ONE)
        assertEquals(3, modes.size)
    }
}

