package com.example.data.repository

import android.net.Uri
import com.example.data.local.AppDatabase
import com.example.data.local.PlaybackHistoryEntity
import com.example.data.local.TrackEntity
import com.example.data.model.Track
import com.example.data.preferences.SettingsManager
import com.example.data.scanner.MediaScanner
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

data class ArtistGroup(
    val artistName: String,
    val trackCount: Int,
    val tracks: List<Track>
) {
    val albumArtUri: Uri?
        get() = tracks.firstOrNull()?.albumArtUri
}

data class AlbumGroup(
    val albumName: String,
    val artistName: String,
    val albumId: Long,
    val trackCount: Int,
    val tracks: List<Track>
) {
    val artist: String
        get() = artistName

    val albumArtUri: Uri
        get() = Uri.parse("content://media/external/audio/albumart/$albumId")
}

data class FolderGroup(
    val folderName: String,
    val trackCount: Int,
    val tracks: List<Track>
) {
    val folderPath: String
        get() = tracks.firstOrNull()?.path ?: ""
}

class AudioRepository(
    private val database: AppDatabase,
    private val mediaScanner: MediaScanner,
    private val settingsManager: SettingsManager
) {
    private val trackDao = database.trackDao()

    val allTracks: Flow<List<Track>> = trackDao.getAllTracks().map { list ->
        list.map { it.toTrack() }
    }

    val recentTracks: Flow<List<Track>> = trackDao.getRecentTracks().map { list ->
        list.map { it.toTrack() }
    }

    val tracksByDuration: Flow<List<Track>> = trackDao.getTracksByDuration().map { list ->
        list.map { it.toTrack() }
    }

    val playbackHistory: Flow<List<Track>> = trackDao.getPlaybackHistory().map { list ->
        list.map { it.toTrack() }
    }

    val trackCount: Flow<Int> = trackDao.getTrackCount()
    val artistCount: Flow<Int> = trackDao.getArtistCount()
    val albumCount: Flow<Int> = trackDao.getAlbumCount()
    val folderCount: Flow<Int> = trackDao.getFolderCount()

    val artistGroups: Flow<List<ArtistGroup>> = allTracks.map { tracks ->
        tracks.groupBy { it.artist }.map { (artist, list) ->
            ArtistGroup(artistName = artist, trackCount = list.size, tracks = list)
        }.sortedBy { it.artistName.lowercase() }
    }

    val albumGroups: Flow<List<AlbumGroup>> = allTracks.map { tracks ->
        tracks.groupBy { it.album }.map { (album, list) ->
            val first = list.first()
            AlbumGroup(
                albumName = album,
                artistName = first.artist,
                albumId = first.albumId,
                trackCount = list.size,
                tracks = list
            )
        }.sortedBy { it.albumName.lowercase() }
    }

    val folderGroups: Flow<List<FolderGroup>> = allTracks.map { tracks ->
        tracks.groupBy { it.folderName }.map { (folder, list) ->
            FolderGroup(folderName = folder, trackCount = list.size, tracks = list)
        }.sortedBy { it.folderName.lowercase() }
    }

    fun searchTracks(query: String): Flow<List<Track>> {
        return trackDao.searchTracks(query).map { list ->
            list.map { it.toTrack() }
        }
    }

    suspend fun getTrackById(id: Long): Track? {
        return trackDao.getTrackById(id)?.toTrack()
    }

    suspend fun getTracksByIds(ids: List<Long>): List<Track> {
        val entities = trackDao.getTracksByIds(ids)
        val entityMap = entities.associateBy { it.id }
        return ids.mapNotNull { id -> entityMap[id]?.toTrack() }
    }

    suspend fun rescanLibrary(): Int {
        val settings = settingsManager.settingsFlow.first()
        val scanned = mediaScanner.scanAudioFiles(whatsAppExclusion = settings.whatsAppExclusion)
        trackDao.clearAllTracks()
        if (scanned.isNotEmpty()) {
            trackDao.insertTracks(scanned)
        }
        return scanned.size
    }

    suspend fun recordPlaybackHistory(trackId: Long, positionMs: Long) {
        trackDao.insertHistory(
            PlaybackHistoryEntity(
                trackId = trackId,
                timestamp = System.currentTimeMillis(),
                positionMs = positionMs
            )
        )
    }

    suspend fun clearAllData() {
        trackDao.clearAllTracks()
        settingsManager.resetAllPlaybackData()
    }
}
