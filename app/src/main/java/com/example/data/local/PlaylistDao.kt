package com.example.data.local

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import com.example.data.model.PlaylistEntity
import com.example.data.model.PlaylistWithEpisodes
import kotlinx.coroutines.flow.Flow

@Dao
interface PlaylistDao {
    @Transaction
    @Query("SELECT * FROM playlists ORDER BY createdAt DESC")
    fun observeAllPlaylistsWithEpisodes(): Flow<List<PlaylistWithEpisodes>>

    @Transaction
    @Query("SELECT * FROM playlists WHERE id = :playlistId")
    fun observePlaylistWithEpisodes(playlistId: Long): Flow<PlaylistWithEpisodes?>

    @Transaction
    @Query("SELECT * FROM playlists WHERE id = :playlistId")
    suspend fun getPlaylistWithEpisodes(playlistId: Long): PlaylistWithEpisodes?

    @Query("SELECT * FROM playlists WHERE id = :playlistId")
    suspend fun getPlaylistById(playlistId: Long): PlaylistEntity?

    @Query("SELECT * FROM playlists WHERE youtubePlaylistId = :youtubePlaylistId LIMIT 1")
    suspend fun getPlaylistByYouTubeId(youtubePlaylistId: String): PlaylistEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPlaylist(playlist: PlaylistEntity): Long

    @Update
    suspend fun updatePlaylist(playlist: PlaylistEntity)

    @Delete
    suspend fun deletePlaylist(playlist: PlaylistEntity)

    @Query("DELETE FROM playlists WHERE id = :playlistId")
    suspend fun deletePlaylistById(playlistId: Long)

    @Query("UPDATE playlists SET playbackSpeed = :speed WHERE id = :playlistId")
    suspend fun updatePlaybackSpeed(playlistId: Long, speed: Float)
}
