package com.example.data.model

import android.net.Uri
import androidx.compose.runtime.Immutable

@Immutable
data class Track(
    val id: Long,
    val title: String,
    val artist: String,
    val album: String,
    val albumId: Long,
    val durationMs: Long,
    val path: String,
    val uri: Uri,
    val dateAdded: Long,
    val folderName: String,
    val mimeType: String,
    val sizeBytes: Long,
    val isExcluded: Boolean = false,
    val bitRate: Int = 320,
    val sampleRate: Int = 44100
) {
    val durationFormatted: String
        get() {
            val totalSeconds = durationMs / 1000
            val minutes = totalSeconds / 60
            val remainingSeconds = totalSeconds % 60
            return "%d:%02d".format(minutes, remainingSeconds)
        }

    val albumArtUri: Uri
        get() = Uri.parse("content://media/external/audio/albumart/$albumId")
}
