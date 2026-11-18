package com.example.data.local

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import com.example.data.model.DownloadStatus
import com.example.data.model.EpisodeEntity
import com.example.data.model.EpisodeWithPlaylist
import kotlinx.coroutines.flow.Flow

@Dao
interface EpisodeDao {
    @Query("SELECT * FROM episodes WHERE playlistId = :playlistId ORDER BY sortOrder ASC, id ASC")
    fun observeEpisodesForPlaylist(playlistId: Long): Flow<List<EpisodeEntity>>

    @Query("SELECT * FROM episodes WHERE playlistId = :playlistId ORDER BY sortOrder ASC, id ASC")
    suspend fun getEpisodesForPlaylist(playlistId: Long): List<EpisodeEntity>

    @Query("SELECT * FROM episodes WHERE id = :episodeId")
    suspend fun getEpisodeById(episodeId: Long): EpisodeEntity?

    @Query("SELECT * FROM episodes WHERE id = :episodeId")
    fun observeEpisodeById(episodeId: Long): Flow<EpisodeEntity?>

    @Transaction
    @Query("SELECT * FROM episodes WHERE downloadStatus IN ('QUEUED', 'DOWNLOADING', 'PAUSED', 'COMPLETED', 'FAILED') ORDER BY addedAt DESC")
    fun observeDownloadQueueAndCompleted(): Flow<List<EpisodeWithPlaylist>>

    @Transaction
    @Query("SELECT * FROM episodes WHERE lastPlayedAt IS NOT NULL ORDER BY lastPlayedAt DESC LIMIT 30")
    fun observeRecentlyPlayed(): Flow<List<EpisodeWithPlaylist>>

    @Transaction
    @Query("SELECT * FROM episodes WHERE lastPlayedAt IS NOT NULL ORDER BY lastPlayedAt DESC LIMIT 1")
    fun observeLastPlayedEpisode(): Flow<EpisodeWithPlaylist?>

    @Query("SELECT MAX(sortOrder) FROM episodes WHERE playlistId = :playlistId")
    suspend fun getMaxSortOrder(playlistId: Long): Int?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertEpisode(episode: EpisodeEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertEpisodes(episodes: List<EpisodeEntity>): List<Long>

    @Update
    suspend fun updateEpisode(episode: EpisodeEntity)

    @Delete
    suspend fun deleteEpisode(episode: EpisodeEntity)

    @Query("DELETE FROM episodes WHERE id = :episodeId")
    suspend fun deleteEpisodeById(episodeId: Long)

    @Query("UPDATE episodes SET lastPositionMs = :positionMs, lastPlayedAt = :playedAt, isCompleted = :isCompleted WHERE id = :episodeId")
    suspend fun updatePlaybackProgress(
        episodeId: Long,
        positionMs: Long,
        playedAt: Long,
        isCompleted: Boolean
    )

    @Query("UPDATE episodes SET durationMs = :durationMs WHERE id = :episodeId AND durationMs <= 0")
    suspend fun updateDurationIfMissing(episodeId: Long, durationMs: Long)

    @Query("UPDATE episodes SET lastPositionMs = 0, lastPlayedAt = NULL, isCompleted = 0 WHERE id = :episodeId")
    suspend fun resetEpisodeProgress(episodeId: Long)

    @Query("UPDATE episodes SET lastPlayedAt = NULL WHERE id = :episodeId")
    suspend fun removeFromHistory(episodeId: Long)

    @Query("UPDATE episodes SET lastPlayedAt = NULL")
    suspend fun clearAllHistory()

    @Query("UPDATE episodes SET isCompleted = :isCompleted, lastPositionMs = CASE WHEN :isCompleted THEN durationMs ELSE 0 END WHERE id = :episodeId")
    suspend fun setEpisodeCompleted(episodeId: Long, isCompleted: Boolean)

    @Query("UPDATE episodes SET downloadStatus = :status, downloadProgress = :progress, downloadError = :error WHERE id = :episodeId")
    suspend fun updateDownloadStatus(
        episodeId: Long,
        status: DownloadStatus,
        progress: Int,
        error: String?
    )

    @Query("UPDATE episodes SET downloadStatus = 'COMPLETED', downloadProgress = 100, localFilePath = :filePath, fileSizeBytes = :fileSizeBytes, downloadError = NULL WHERE id = :episodeId")
    suspend fun markDownloadCompleted(
        episodeId: Long,
        filePath: String,
        fileSizeBytes: Long
    )

    @Query("UPDATE episodes SET downloadStatus = 'NONE', downloadProgress = 0, localFilePath = NULL, fileSizeBytes = 0, downloadError = NULL WHERE id = :episodeId")
    suspend fun clearDownloadMetadata(episodeId: Long)

    @Query("UPDATE episodes SET downloadStatus = CASE WHEN downloadProgress > 0 THEN 'PAUSED' ELSE 'NONE' END, downloadError = NULL WHERE downloadStatus IN ('QUEUED', 'DOWNLOADING')")
    suspend fun resetInterruptedDownloads()

    @Query("SELECT * FROM episodes WHERE downloadStatus = 'COMPLETED'")
    suspend fun getDownloadedEpisodes(): List<EpisodeEntity>

    @Query("SELECT * FROM episodes WHERE downloadStatus IN ('QUEUED', 'DOWNLOADING')")
    suspend fun getActiveDownloadingEpisodes(): List<EpisodeEntity>

    @Query("SELECT * FROM episodes WHERE downloadStatus = 'PAUSED'")
    suspend fun getPausedEpisodes(): List<EpisodeEntity>
}
