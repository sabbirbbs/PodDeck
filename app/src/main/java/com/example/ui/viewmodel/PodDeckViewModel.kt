package com.example.ui.viewmodel

import android.content.Intent
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.data.model.BookmarkEntity
import com.example.data.model.DownloadStatus
import com.example.data.model.EpisodeEntity
import com.example.data.model.EpisodeWithPlaylist
import com.example.data.model.PlaylistEntity
import com.example.data.model.PlaylistWithEpisodes
import com.example.data.repository.PodcastRepository
import com.example.download.ExportResult
import com.example.player.PlaybackUiState
import com.example.player.PlayerController
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

enum class LibraryFilter(val label: String) {
    ALL("All"),
    IN_PROGRESS("In Progress"),
    OFFLINE("Offline Ready"),
    COMPLETED("Completed")
}

enum class EpisodeFilter(val label: String) {
    ALL("All"),
    UNFINISHED("Unfinished"),
    DOWNLOADED("Offline"),
    COMPLETED("Completed")
}

enum class EpisodeSortOrder(val label: String) {
    PLAYLIST_ORDER("Playlist Order"),
    UNPLAYED_FIRST("Unplayed First"),
    SHORTEST_FIRST("Shortest First"),
    LONGEST_FIRST("Longest First")
}

class PodDeckViewModel(
    private val repository: PodcastRepository,
    val playerController: PlayerController
) : ViewModel() {

    val playlists: StateFlow<List<PlaylistWithEpisodes>> = repository.allPlaylists
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val downloads: StateFlow<List<EpisodeWithPlaylist>> = repository.downloadQueueAndCompleted
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val recentlyPlayed: StateFlow<List<EpisodeWithPlaylist>> = repository.recentlyPlayed
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val lastPlayedEpisode: StateFlow<EpisodeWithPlaylist?> = repository.lastPlayedEpisode
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val playbackState: StateFlow<PlaybackUiState> = playerController.uiState

    private val _isImporting = MutableStateFlow(false)
    val isImporting: StateFlow<Boolean> = _isImporting.asStateFlow()

    private val _isSyncingPlaylist = MutableStateFlow(false)
    val isSyncingPlaylist: StateFlow<Boolean> = _isSyncingPlaylist.asStateFlow()

    private val _isAddingVideo = MutableStateFlow(false)
    val isAddingVideo: StateFlow<Boolean> = _isAddingVideo.asStateFlow()

    private val _messages = MutableSharedFlow<String>(extraBufferCapacity = 16)
    val messages = _messages.asSharedFlow()

    init {
        viewModelScope.launch {
            repository.downloadEvents.collect { event ->
                _messages.tryEmit(event.message)
            }
        }
    }

    fun observePlaylist(playlistId: Long): Flow<PlaylistWithEpisodes?> {
        return repository.observePlaylist(playlistId)
    }

    fun observeBookmarksForEpisode(episodeId: Long): Flow<List<BookmarkEntity>> {
        return repository.observeBookmarksForEpisode(episodeId)
    }

    fun addBookmark(episodeId: Long, positionMs: Long, note: String) {
        viewModelScope.launch {
            repository.addBookmark(episodeId, positionMs, note)
            _messages.tryEmit("Bookmark saved")
        }
    }

    fun deleteBookmark(bookmarkId: Long) {
        viewModelScope.launch {
            repository.deleteBookmark(bookmarkId)
            _messages.tryEmit("Bookmark deleted")
        }
    }

    fun importYouTubePlaylist(url: String, onSuccess: (Long) -> Unit) {
        if (_isImporting.value) return
        viewModelScope.launch {
            _isImporting.value = true
            try {
                val id = repository.importYouTubePlaylist(url)
                _messages.tryEmit("Playlist imported successfully")
                onSuccess(id)
            } catch (e: Exception) {
                _messages.tryEmit(e.message ?: "Failed to load YouTube playlist")
            } finally {
                _isImporting.value = false
            }
        }
    }

    fun createCustomPlaylist(title: String, description: String, onSuccess: (Long) -> Unit) {
        viewModelScope.launch {
            try {
                val id = repository.createCustomPlaylist(title, description)
                _messages.tryEmit("Created custom playlist")
                onSuccess(id)
            } catch (e: Exception) {
                _messages.tryEmit(e.message ?: "Failed to create playlist")
            }
        }
    }

    fun addVideoToPlaylist(playlistId: Long, videoUrl: String, onSuccess: () -> Unit = {}) {
        if (_isAddingVideo.value) return
        viewModelScope.launch {
            _isAddingVideo.value = true
            try {
                repository.addVideoToPlaylist(playlistId, videoUrl)
                _messages.tryEmit("Episode added to playlist")
                onSuccess()
            } catch (e: Exception) {
                _messages.tryEmit(e.message ?: "Failed to add YouTube video")
            } finally {
                _isAddingVideo.value = false
            }
        }
    }

    fun syncPlaylist(playlistId: Long) {
        if (_isSyncingPlaylist.value) return
        viewModelScope.launch {
            _isSyncingPlaylist.value = true
            try {
                val added = repository.syncPlaylist(playlistId)
                val msg = if (added > 0) "Synced $added new episode(s)" else "Playlist is up to date"
                _messages.tryEmit(msg)
            } catch (e: Exception) {
                _messages.tryEmit(e.message ?: "Sync failed")
            } finally {
                _isSyncingPlaylist.value = false
            }
        }
    }

    fun updatePlaylistDetails(playlistId: Long, title: String, description: String) {
        viewModelScope.launch {
            repository.updatePlaylistDetails(playlistId, title, description)
            _messages.tryEmit("Playlist updated")
        }
    }

    fun deletePlaylist(playlistId: Long, onDeleted: () -> Unit = {}) {
        viewModelScope.launch {
            playerController.stopIfPlayingPlaylist(playlistId)
            repository.deletePlaylist(playlistId)
            _messages.tryEmit("Playlist and offline files deleted")
            onDeleted()
        }
    }

    fun renameEpisode(episodeId: Long, newTitle: String) {
        viewModelScope.launch {
            repository.renameEpisode(episodeId, newTitle)
            _messages.tryEmit("Episode renamed")
        }
    }

    fun deleteEpisode(episode: EpisodeEntity) {
        viewModelScope.launch {
            playerController.stopIfPlayingEpisode(episode.id)
            repository.deleteEpisode(episode)
            _messages.tryEmit("Episode removed")
        }
    }

    fun toggleEpisodeCompleted(episode: EpisodeEntity) {
        viewModelScope.launch {
            repository.toggleEpisodeCompleted(episode)
        }
    }

    fun resetEpisodeProgress(episodeId: Long) {
        viewModelScope.launch {
            repository.resetEpisodeProgress(episodeId)
            _messages.tryEmit("Episode progress & recent status cleared")
        }
    }

    fun removeFromHistory(episodeId: Long) {
        viewModelScope.launch {
            repository.removeFromHistory(episodeId)
            _messages.tryEmit("Removed from recent history")
        }
    }

    fun clearAllHistory() {
        viewModelScope.launch {
            repository.clearAllHistory()
            _messages.tryEmit("Listening history cleared")
        }
    }

    fun downloadEpisode(episode: EpisodeEntity) {
        repository.downloadEpisode(episode)
    }

    fun downloadAllInPlaylist(episodes: List<EpisodeEntity>) {
        val pendingCount = episodes.count {
            !it.isDownloaded &&
                it.downloadStatus != DownloadStatus.QUEUED &&
                it.downloadStatus != DownloadStatus.DOWNLOADING
        }
        if (pendingCount == 0) {
            _messages.tryEmit("All episodes are already offline or in queue")
            return
        }
        repository.downloadAllInPlaylist(episodes)
        _messages.tryEmit("Queued $pendingCount episode(s) for offline download")
    }

    fun downloadSelectedEpisodes(episodes: List<EpisodeEntity>) {
        val queued = repository.downloadSelectedEpisodes(episodes)
        if (queued == 0) {
            _messages.tryEmit("Selected episodes are already offline or downloading")
        }
    }

    fun downloadNextUnplayed(episodes: List<EpisodeEntity>, limit: Int = 5) {
        val pending = episodes.count {
            !it.isCompleted &&
                !it.isDownloaded &&
                it.downloadStatus != DownloadStatus.QUEUED &&
                it.downloadStatus != DownloadStatus.DOWNLOADING
        }
        if (pending == 0) {
            _messages.tryEmit("No unplayed episodes left to download")
            return
        }
        val queued = pending.coerceAtMost(limit)
        repository.downloadNextUnplayed(episodes, limit)
        _messages.tryEmit("Downloading next $queued unplayed episode(s)")
    }

    fun pauseDownload(episodeId: Long) {
        repository.pauseDownload(episodeId)
    }

    fun pauseDownloadsForEpisodes(episodes: List<EpisodeEntity>) {
        repository.pauseDownloadsForEpisodes(episodes)
    }

    fun pauseAllActiveDownloads() {
        repository.pauseAllActiveDownloads()
    }

    fun resumePausedDownloads(episodes: List<EpisodeEntity>? = null) {
        repository.resumePausedDownloads(episodes)
    }

    fun stopDownloadsForEpisodes(episodes: List<EpisodeEntity>) {
        repository.stopDownloadsForEpisodes(episodes)
    }

    fun stopAllActiveDownloads() {
        repository.stopAllActiveDownloads()
    }

    fun cleanCompletedOfflineAudio(playlistId: Long) {
        viewModelScope.launch {
            val cleaned = repository.cleanCompletedOfflineAudio(playlistId)
            if (cleaned > 0) {
                _messages.tryEmit("Freed space from $cleaned completed episode(s)")
            } else {
                _messages.tryEmit("No completed offline files to clean")
            }
        }
    }

    fun cancelDownload(episodeId: Long) {
        repository.cancelDownload(episodeId)
        _messages.tryEmit("Download stopped")
    }

    fun removeDownloadedFile(episode: EpisodeEntity) {
        viewModelScope.launch {
            playerController.stopIfPlayingOfflineFile(episode.id)
            repository.removeDownloadedFile(episode)
            _messages.tryEmit("Removed offline audio file")
        }
    }

    fun clearAllDownloads() {
        viewModelScope.launch {
            playerController.stopAllOfflinePlayback()
            repository.deleteAllDownloadedFiles()
            _messages.tryEmit("Cleared all offline audio files")
        }
    }

    fun exportEpisodeToDownloads(episode: EpisodeEntity, onShareIntent: (Intent) -> Unit) {
        viewModelScope.launch {
            try {
                when (val result = repository.exportEpisodeToDownloads(episode)) {
                    is ExportResult.SavedToDownloads -> {
                        _messages.tryEmit("Saved copy to ${result.displayName}")
                    }
                    is ExportResult.NeedsShareSheet -> {
                        val intent = Intent(Intent.ACTION_SEND).apply {
                            type = result.mimeType
                            putExtra(Intent.EXTRA_STREAM, result.uri)
                            putExtra(Intent.EXTRA_TITLE, result.fileName)
                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        }
                        onShareIntent(Intent.createChooser(intent, "Save or Share Audio"))
                    }
                }
            } catch (e: Exception) {
                _messages.tryEmit(e.message ?: "Could not export audio file")
            }
        }
    }

    fun playEpisode(
        episode: EpisodeEntity,
        playlist: PlaylistEntity?,
        queue: List<EpisodeEntity>,
        restartFromBeginning: Boolean = false
    ) {
        playerController.playEpisode(episode, playlist, queue, restartFromBeginning)
    }

    fun enqueuePlayNext(episode: EpisodeEntity) {
        playerController.enqueuePlayNext(episode)
        _messages.tryEmit("Set to play next")
    }

    fun addToQueue(episode: EpisodeEntity) {
        playerController.addToQueue(episode)
        _messages.tryEmit("Added to playback queue")
    }

    fun resumeLastPlayed() {
        val last = lastPlayedEpisode.value ?: return
        viewModelScope.launch {
            val queue = repository.getEpisodesForPlaylist(last.episode.playlistId)
            playerController.playEpisode(last.episode, last.playlist, queue)
        }
    }

    class Factory(
        private val repository: PodcastRepository,
        private val playerController: PlayerController
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return PodDeckViewModel(repository, playerController) as T
        }
    }
}
