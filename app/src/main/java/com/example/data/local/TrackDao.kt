package com.example.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface TrackDao {
    @Query("SELECT * FROM tracks WHERE isExcluded = 0 ORDER BY title ASC")
    fun getAllTracks(): Flow<List<TrackEntity>>

    @Query("SELECT * FROM tracks WHERE isExcluded = 0 ORDER BY dateAdded DESC")
    fun getRecentTracks(): Flow<List<TrackEntity>>

    @Query("SELECT * FROM tracks WHERE isExcluded = 0 ORDER BY durationMs DESC")
    fun getTracksByDuration(): Flow<List<TrackEntity>>

    @Query("SELECT * FROM tracks WHERE isExcluded = 0 AND artist = :artist ORDER BY title ASC")
    fun getTracksByArtist(artist: String): Flow<List<TrackEntity>>

    @Query("SELECT * FROM tracks WHERE isExcluded = 0 AND album = :album ORDER BY title ASC")
    fun getTracksByAlbum(album: String): Flow<List<TrackEntity>>

    @Query("SELECT * FROM tracks WHERE isExcluded = 0 AND folderName = :folderName ORDER BY title ASC")
    fun getTracksByFolder(folderName: String): Flow<List<TrackEntity>>

    @Query("SELECT * FROM tracks WHERE id = :id LIMIT 1")
    suspend fun getTrackById(id: Long): TrackEntity?

    @Query("SELECT * FROM tracks WHERE id IN (:ids)")
    suspend fun getTracksByIds(ids: List<Long>): List<TrackEntity>

    @Query("SELECT * FROM tracks WHERE isExcluded = 0 AND (title LIKE '%' || :query || '%' OR artist LIKE '%' || :query || '%' OR album LIKE '%' || :query || '%' OR folderName LIKE '%' || :query || '%') ORDER BY title ASC")
    fun searchTracks(query: String): Flow<List<TrackEntity>>

    @Query("SELECT COUNT(*) FROM tracks WHERE isExcluded = 0")
    fun getTrackCount(): Flow<Int>

    @Query("SELECT COUNT(DISTINCT artist) FROM tracks WHERE isExcluded = 0")
    fun getArtistCount(): Flow<Int>

    @Query("SELECT COUNT(DISTINCT album) FROM tracks WHERE isExcluded = 0")
    fun getAlbumCount(): Flow<Int>

    @Query("SELECT COUNT(DISTINCT folderName) FROM tracks WHERE isExcluded = 0")
    fun getFolderCount(): Flow<Int>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTracks(tracks: List<TrackEntity>)

    @Query("DELETE FROM tracks")
    suspend fun clearAllTracks()

    // History DAO methods
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertHistory(history: PlaybackHistoryEntity)

    @Query("SELECT t.* FROM tracks t INNER JOIN playback_history h ON t.id = h.trackId WHERE t.isExcluded = 0 ORDER BY h.timestamp DESC LIMIT 30")
    fun getPlaybackHistory(): Flow<List<TrackEntity>>
}
