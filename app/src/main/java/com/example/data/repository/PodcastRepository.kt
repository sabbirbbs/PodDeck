package com.example.data.repository

import com.example.data.local.BookmarkDao
import com.example.data.local.EpisodeDao
import com.example.data.local.PlaylistDao
import com.example.data.local.UserPreferences
import com.example.data.model.BookmarkEntity
import com.example.data.model.EpisodeEntity
import com.example.data.model.EpisodeWithPlaylist
import com.example.data.model.PlaylistEntity
import com.example.data.model.PlaylistWithEpisodes
import com.example.download.DownloadEvent
import com.example.download.EpisodeDownloadManager
import com.example.download.ExportResult
import com.example.youtube.YouTubeExtractorService
import com.example.util.YouTubeUrlParser
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.first
import java.io.File
import java.util.UUID

class PodcastRepository(
    private val playlistDao: PlaylistDao,
    private val episodeDao: EpisodeDao,
    private val bookmarkDao: BookmarkDao,
    private val extractorService: YouTubeExtractorService,
    private val downloadManager: EpisodeDownloadManager,
    val userPreferences: UserPreferences
) {
    val allPlaylists: Flow<List<PlaylistWithEpisodes>> = playlistDao.observeAllPlaylistsWithEpisodes()
    val downloadQueueAndCompleted: Flow<List<EpisodeWithPlaylist>> = episodeDao.observeDownloadQueueAndCompleted()
    val recentlyPlayed: Flow<List<EpisodeWithPlaylist>> = episodeDao.observeRecentlyPlayed()
    val lastPlayedEpisode: Flow<EpisodeWithPlaylist?> = episodeDao.observeLastPlayedEpisode()
    val downloadEvents: SharedFlow<DownloadEvent> = downloadManager.events

    fun observePlaylist(playlistId: Long): Flow<PlaylistWithEpisodes?> =
        playlistDao.observePlaylistWithEpisodes(playlistId)

    fun observeBookmarksForEpisode(episodeId: Long): Flow<List<BookmarkEntity>> =
        bookmarkDao.observeBookmarksForEpisode(episodeId)

    suspend fun addBookmark(episodeId: Long, positionMs: Long, note: String): Long {
        return bookmarkDao.insertBookmark(
            BookmarkEntity(
                episodeId = episodeId,
                positionMs = positionMs.coerceAtLeast(0L),
                note = note.trim()
            )
        )
    }

    suspend fun deleteBookmark(bookmarkId: Long) {
        bookmarkDao.deleteBookmarkById(bookmarkId)
    }

    suspend fun importYouTubePlaylist(rawUrl: String): Long {
        val playlistIdParam = YouTubeUrlParser.extractPlaylistId(rawUrl)
        if (playlistIdParam == null) {
            val singleVideoId = YouTubeUrlParser.extractVideoId(rawUrl)
            if (singleVideoId != null) {
                return importSingleVideoToSavedPlaylist(rawUrl)
            }
        }

        val extracted = extractorService.fetchPlaylist(rawUrl)
        if (extracted.items.isEmpty()) {
            throw IllegalStateException("No playable episodes found in this playlist. Check that it is public or unlisted.")
        }
        val existing = playlistDao.getPlaylistByYouTubeId(extracted.youtubePlaylistId)

        val playlistId = if (existing != null) {
            playlistDao.updatePlaylist(
                existing.copy(
                    title = extracted.title,
                    author = extracted.author,
                    description = extracted.description,
                    thumbnailUrl = extracted.thumbnailUrl ?: existing.thumbnailUrl,
                    lastSyncedAt = System.currentTimeMillis()
                )
            )
            existing.id
        } else {
            playlistDao.insertPlaylist(
                PlaylistEntity(
                    sourceUrl = extracted.sourceUrl,
                    youtubePlaylistId = extracted.youtubePlaylistId,
                    title = extracted.title,
                    author = extracted.author,
                    description = extracted.description,
                    thumbnailUrl = extracted.thumbnailUrl,
                    isCustom = false,
                    lastSyncedAt = System.currentTimeMillis()
                )
            )
        }

        val existingEpisodes = episodeDao.getEpisodesForPlaylist(playlistId)
        val existingByVideoId = existingEpisodes.associateBy { it.youtubeVideoId }
        val maxSort = existingEpisodes.maxOfOrNull { it.sortOrder } ?: -1

        val newEpisodes = mutableListOf<EpisodeEntity>()
        var nextSort = maxSort + 1
        extracted.items.forEachIndexed { index, item ->
            val old = existingByVideoId[item.videoId]
            if (old == null) {
                newEpisodes += EpisodeEntity(
                    playlistId = playlistId,
                    youtubeVideoId = item.videoId,
                    videoUrl = item.videoUrl,
                    title = item.title,
                    uploader = item.uploader,
                    thumbnailUrl = item.thumbnailUrl,
                    durationMs = item.durationMs,
                    sortOrder = if (existingEpisodes.isEmpty()) index else nextSort++
                )
            } else {
                episodeDao.updateEpisode(
                    old.copy(
                        title = item.title,
                        uploader = item.uploader.ifBlank { old.uploader },
                        thumbnailUrl = item.thumbnailUrl ?: old.thumbnailUrl,
                        durationMs = if (item.durationMs > 0L) item.durationMs else old.durationMs
                    )
                )
            }
        }
        if (newEpisodes.isNotEmpty()) {
            episodeDao.insertEpisodes(newEpisodes)
        }
        return playlistId
    }

    private suspend fun importSingleVideoToSavedPlaylist(rawVideoUrl: String): Long {
        val all = playlistDao.observeAllPlaylistsWithEpisodes().first()
        val existingSaved = all.firstOrNull {
            it.playlist.isCustom && it.playlist.title.equals("Saved Videos", ignoreCase = true)
        }?.playlist
        val targetPlaylistId = existingSaved?.id ?: createCustomPlaylist(
            title = "Saved Videos",
            description = "Individual YouTube episodes added directly by link"
        )
        val item = extractorService.fetchSingleVideoMetadata(rawVideoUrl)
        val existing = episodeDao.getEpisodesForPlaylist(targetPlaylistId)
        if (existing.none { it.youtubeVideoId == item.videoId }) {
            val nextSort = (episodeDao.getMaxSortOrder(targetPlaylistId) ?: -1) + 1
            episodeDao.insertEpisode(
                EpisodeEntity(
                    playlistId = targetPlaylistId,
                    youtubeVideoId = item.videoId,
                    videoUrl = item.videoUrl,
                    title = item.title,
                    uploader = item.uploader,
                    thumbnailUrl = item.thumbnailUrl,
                    durationMs = item.durationMs,
                    sortOrder = nextSort
                )
            )
            val playlist = playlistDao.getPlaylistById(targetPlaylistId)
            if (playlist != null && playlist.thumbnailUrl == null && item.thumbnailUrl != null) {
                playlistDao.updatePlaylist(playlist.copy(thumbnailUrl = item.thumbnailUrl))
            }
        }
        return targetPlaylistId
    }

    suspend fun createCustomPlaylist(title: String, description: String): Long {
        val cleanTitle = title.trim().ifBlank { "My Custom Playlist" }
        return playlistDao.insertPlaylist(
            PlaylistEntity(
                sourceUrl = "local://${UUID.randomUUID()}",
                youtubePlaylistId = null,
                title = cleanTitle,
                author = "Personal Collection",
                description = description.trim(),
                isCustom = true,
                lastSyncedAt = System.currentTimeMillis()
            )
        )
    }

    suspend fun addVideoToPlaylist(playlistId: Long, rawVideoUrl: String) {
        val item = extractorService.fetchSingleVideoMetadata(rawVideoUrl)
        val existing = episodeDao.getEpisodesForPlaylist(playlistId)
        if (existing.any { it.youtubeVideoId == item.videoId }) {
            throw IllegalStateException("This episode is already in the playlist.")
        }
        val nextSort = (episodeDao.getMaxSortOrder(playlistId) ?: -1) + 1
        val entity = EpisodeEntity(
            playlistId = playlistId,
            youtubeVideoId = item.videoId,
            videoUrl = item.videoUrl,
            title = item.title,
            uploader = item.uploader,
            thumbnailUrl = item.thumbnailUrl,
            durationMs = item.durationMs,
            sortOrder = nextSort
        )
        episodeDao.insertEpisode(entity)
        val playlist = playlistDao.getPlaylistById(playlistId)
        if (playlist != null && playlist.thumbnailUrl == null && item.thumbnailUrl != null) {
            playlistDao.updatePlaylist(playlist.copy(thumbnailUrl = item.thumbnailUrl))
        }
    }

    suspend fun syncPlaylist(playlistId: Long): Int {
        val playlist = playlistDao.getPlaylistById(playlistId)
            ?: throw IllegalStateException("Playlist not found")
        if (playlist.isCustom || playlist.youtubePlaylistId == null) {
            return 0
        }
        val beforeCount = episodeDao.getEpisodesForPlaylist(playlistId).size
        importYouTubePlaylist(playlist.sourceUrl)
        val afterCount = episodeDao.getEpisodesForPlaylist(playlistId).size
        return (afterCount - beforeCount).coerceAtLeast(0)
    }

    suspend fun updatePlaylistDetails(playlistId: Long, newTitle: String, newDescription: String) {
        val playlist = playlistDao.getPlaylistById(playlistId) ?: return
        playlistDao.updatePlaylist(
            playlist.copy(
                title = newTitle.trim().ifBlank { playlist.title },
                description = newDescription.trim()
            )
        )
    }

    suspend fun updatePlaylistSpeed(playlistId: Long, speed: Float) {
        playlistDao.updatePlaybackSpeed(playlistId, speed)
    }

    suspend fun deletePlaylist(playlistId: Long) {
        val episodes = episodeDao.getEpisodesForPlaylist(playlistId)
        episodes.forEach { downloadManager.cancelDownload(it.id) }
        downloadManager.deletePlaylistFolder(playlistId)
        playlistDao.deletePlaylistById(playlistId)
    }

    suspend fun renameEpisode(episodeId: Long, newTitle: String) {
        val ep = episodeDao.getEpisodeById(episodeId) ?: return
        episodeDao.updateEpisode(ep.copy(title = newTitle.trim().ifBlank { ep.title }))
    }

    suspend fun deleteEpisode(episode: EpisodeEntity) {
        downloadManager.deleteLocalAudio(episode)
        episodeDao.deleteEpisodeById(episode.id)
    }

    suspend fun toggleEpisodeCompleted(episode: EpisodeEntity) {
        episodeDao.setEpisodeCompleted(episode.id, !episode.isCompleted)
    }

    suspend fun resetEpisodeProgress(episodeId: Long) {
        episodeDao.resetEpisodeProgress(episodeId)
    }

    suspend fun removeFromHistory(episodeId: Long) {
        episodeDao.removeFromHistory(episodeId)
    }

    suspend fun clearAllHistory() {
        episodeDao.clearAllHistory()
    }

    fun downloadEpisode(episode: EpisodeEntity) {
        downloadManager.enqueueDownload(episode)
    }

    fun downloadAllInPlaylist(episodes: List<EpisodeEntity>) {
        downloadManager.enqueuePlaylistDownload(episodes)
    }

    fun downloadSelectedEpisodes(episodes: List<EpisodeEntity>): Int {
        return downloadManager.enqueueSelectedEpisodes(episodes)
    }

    fun downloadNextUnplayed(episodes: List<EpisodeEntity>, limit: Int = 5) {
        downloadManager.enqueueNextUnplayed(episodes, limit)
    }

    fun pauseDownload(episodeId: Long) {
        downloadManager.pauseDownload(episodeId)
    }

    fun pauseDownloadsForEpisodes(episodes: List<EpisodeEntity>) {
        downloadManager.pauseDownloadsForEpisodes(episodes)
    }

    fun pauseAllActiveDownloads() {
        downloadManager.pauseAllActiveDownloads()
    }

    fun resumePausedDownloads(episodes: List<EpisodeEntity>? = null) {
        downloadManager.resumePausedDownloads(episodes)
    }

    fun stopDownloadsForEpisodes(episodes: List<EpisodeEntity>) {
        downloadManager.stopDownloadsForEpisodes(episodes)
    }

    fun stopAllActiveDownloads() {
        downloadManager.stopAllActiveDownloads()
    }

    fun cancelDownload(episodeId: Long) {
        downloadManager.cancelDownload(episodeId)
    }

    suspend fun removeDownloadedFile(episode: EpisodeEntity) {
        downloadManager.deleteLocalAudio(episode)
    }

    suspend fun clearDownloadMetadataIfMissing(episodeId: Long) {
        episodeDao.clearDownloadMetadata(episodeId)
    }

    suspend fun exportEpisodeToDownloads(episode: EpisodeEntity): ExportResult {
        return downloadManager.exportToDeviceDownloads(episode)
    }

    suspend fun cleanCompletedOfflineAudio(playlistId: Long): Int {
        val completedDownloaded = episodeDao.getEpisodesForPlaylist(playlistId)
            .filter { it.isCompleted && it.isDownloaded }
        completedDownloaded.forEach { downloadManager.deleteLocalAudio(it) }
        return completedDownloaded.size
    }

    suspend fun deleteAllDownloadedFiles() {
        episodeDao.getDownloadedEpisodes().forEach { episode ->
            downloadManager.deleteLocalAudio(episode)
        }
    }

    suspend fun getEpisodeById(episodeId: Long): EpisodeEntity? = episodeDao.getEpisodeById(episodeId)

    suspend fun getPlaylistById(playlistId: Long): PlaylistEntity? = playlistDao.getPlaylistById(playlistId)

    suspend fun getEpisodesForPlaylist(playlistId: Long): List<EpisodeEntity> =
        episodeDao.getEpisodesForPlaylist(playlistId)

    suspend fun savePlaybackProgress(episodeId: Long, positionMs: Long, durationMs: Long) {
        val ep = episodeDao.getEpisodeById(episodeId) ?: return
        val effectiveDuration = if (durationMs > 0L) durationMs else ep.durationMs
        if (durationMs > 0L && ep.durationMs <= 0L) {
            episodeDao.updateDurationIfMissing(episodeId, durationMs)
        }
        val completed = effectiveDuration > 0L && positionMs >= (effectiveDuration * 0.96f).toLong()
        episodeDao.updatePlaybackProgress(
            episodeId = episodeId,
            positionMs = positionMs.coerceAtLeast(0L),
            playedAt = System.currentTimeMillis(),
            isCompleted = completed
        )
        userPreferences.setLastEpisodeId(episodeId)
    }

    suspend fun resolveStreamUrlForOnlinePlayback(videoUrl: String): String {
        return extractorService.resolveAudioStream(videoUrl).streamUrl
    }

    fun isLocalFilePresent(episode: EpisodeEntity): Boolean {
        val path = episode.localFilePath ?: return false
        val file = File(path)
        return file.exists() && file.length() >= 4_096L
    }
}
