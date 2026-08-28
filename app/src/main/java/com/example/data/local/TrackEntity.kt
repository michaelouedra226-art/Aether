package com.example.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.example.data.model.Track
import android.net.Uri

@Entity(tableName = "tracks")
data class TrackEntity(
    @PrimaryKey val id: Long,
    val title: String,
    val artist: String,
    val album: String,
    val albumId: Long,
    val durationMs: Long,
    val path: String,
    val dateAdded: Long,
    val folderName: String,
    val mimeType: String,
    val sizeBytes: Long,
    val isExcluded: Boolean = false,
    val bitRate: Int = 320,
    val sampleRate: Int = 44100
) {
    fun toTrack(): Track {
        return Track(
            id = id,
            title = title,
            artist = artist,
            album = album,
            albumId = albumId,
            durationMs = durationMs,
            path = path,
            uri = Uri.parse("content://media/external/audio/media/$id"),
            dateAdded = dateAdded,
            folderName = folderName,
            mimeType = mimeType,
            sizeBytes = sizeBytes,
            isExcluded = isExcluded,
            bitRate = bitRate,
            sampleRate = sampleRate
        )
    }

    companion object {
        fun fromTrack(track: Track): TrackEntity {
            return TrackEntity(
                id = track.id,
                title = track.title,
                artist = track.artist,
                album = track.album,
                albumId = track.albumId,
                durationMs = track.durationMs,
                path = track.path,
                dateAdded = track.dateAdded,
                folderName = track.folderName,
                mimeType = track.mimeType,
                sizeBytes = track.sizeBytes,
                isExcluded = track.isExcluded,
                bitRate = track.bitRate,
                sampleRate = track.sampleRate
            )
        }
    }
}
