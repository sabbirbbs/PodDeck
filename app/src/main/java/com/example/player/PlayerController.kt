package com.example.player

import android.content.ComponentName
import android.content.Context
import android.net.Uri
import android.os.Bundle
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackException
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionToken
import com.example.data.model.EpisodeEntity
import com.example.data.model.PlaylistEntity
import com.example.data.repository.PodcastRepository
import com.example.util.NetworkUtils
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.MoreExecutors
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.File

data class PlaybackUiState(
    val currentEpisode: EpisodeEntity? = null,
    val currentPlaylist: PlaylistEntity? = null,
    val queue: List<EpisodeEntity> = emptyList(),
    val isPlaying: Boolean = false,
    val isBuffering: Boolean = false,
    val isResolvingStream: Boolean = false,
    val isOfflineSource: Boolean = false,
    val positionMs: Long = 0L,
    val durationMs: Long = 0L,
    val playbackSpeed: Float = 1.0f,
    val volumeBoostEnabled: Boolean = false,
    val sleepTimerRemainingMs: Long? = null,
    val sleepAtEndOfEpisode: Boolean = false,
    val errorMessage: String? = null
)

class PlayerController(
    private val context: Context,
    private val repository: PodcastRepository
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private val _uiState = MutableStateFlow(PlaybackUiState())
    val uiState: StateFlow<PlaybackUiState> = _uiState.asStateFlow()

    private var controllerFuture: ListenableFuture<MediaController>? = null
    private var mediaController: MediaController? = null

    private var tickerJob: Job? = null
    private var sleepTimerJob: Job? = null
    private var playRequestJob: Job? = null
    private var lastSavedDbMs = 0L

    private val playerListener = object : Player.Listener {
        override fun onIsPlayingChanged(isPlaying: Boolean) {
            _uiState.update { it.copy(isPlaying = isPlaying) }
            if (isPlaying) {
                startTicker()
            } else {
                stopTicker()
                persistCurrentPositionNow()
            }
        }

        override fun onPlaybackStateChanged(playbackState: Int) {
            val buffering = playbackState == Player.STATE_BUFFERING
            _uiState.update { state ->
                val playerDur = mediaController?.duration?.takeIf { it > 0L } ?: state.durationMs
                state.copy(
                    isBuffering = buffering,
                    durationMs = playerDur
                )
            }
            if (playbackState == Player.STATE_ENDED) {
                handleEpisodeEnded()
            }
        }

        override fun onPlayerError(error: PlaybackException) {
            _uiState.update {
                it.copy(
                    isPlaying = false,
                    isBuffering = false,
                    isResolvingStream = false,
                    errorMessage = error.localizedMessage ?: "Audio playback failed"
                )
            }
        }
    }

    init {
        connectToService()
    }

    private fun connectToService(onConnected: ((MediaController) -> Unit)? = null) {
        val existing = mediaController
        if (existing != null && existing.isConnected) {
            onConnected?.invoke(existing)
            return
        }
        val sessionToken = SessionToken(context, ComponentName(context, PlaybackService::class.java))
        val future = MediaController.Builder(context, sessionToken).buildAsync()
        controllerFuture = future
        future.addListener(
            {
                runCatching {
                    val controller = future.get()
                    mediaController = controller
                    controller.removeListener(playerListener)
                    controller.addListener(playerListener)
                    onConnected?.invoke(controller)
                }
            },
            MoreExecutors.directExecutor()
        )
    }

    fun playEpisode(
        episode: EpisodeEntity,
        playlist: PlaylistEntity?,
        playlistQueue: List<EpisodeEntity> = emptyList(),
        restartFromBeginning: Boolean = false
    ) {
        playRequestJob?.cancel()
        playRequestJob = scope.launch {
            persistCurrentPositionNow()

            val freshEpisode = repository.getEpisodeById(episode.id) ?: episode
            val resolvedPlaylist = playlist ?: repository.getPlaylistById(freshEpisode.playlistId)
            val queue = if (playlistQueue.isNotEmpty()) {
                playlistQueue
            } else {
                repository.getEpisodesForPlaylist(freshEpisode.playlistId).sortedBy { it.sortOrder }
            }

            val speed = resolvedPlaylist?.playbackSpeed ?: _uiState.value.playbackSpeed
            val startPos = if (restartFromBeginning || freshEpisode.isCompleted) {
                0L
            } else {
                freshEpisode.lastPositionMs.coerceAtLeast(0L)
            }

            val localFile = freshEpisode.localFilePath
                ?.let { File(it) }
                ?.takeIf { it.exists() && it.length() >= 4_096L }

            if (localFile == null && freshEpisode.isDownloaded) {
                repository.clearDownloadMetadataIfMissing(freshEpisode.id)
            }

            if (localFile == null && !NetworkUtils.isNetworkAvailable(context)) {
                _uiState.update {
                    it.copy(
                        isResolvingStream = false,
                        isPlaying = false,
                        errorMessage = "You're offline. Download this episode while online to listen without internet."
                    )
                }
                return@launch
            }

            _uiState.update {
                it.copy(
                    currentEpisode = freshEpisode,
                    currentPlaylist = resolvedPlaylist,
                    queue = queue,
                    isResolvingStream = localFile == null,
                    isOfflineSource = localFile != null,
                    positionMs = startPos,
                    durationMs = freshEpisode.durationMs,
                    playbackSpeed = speed,
                    errorMessage = null
                )
            }

            val mediaUri: Uri = if (localFile != null) {
                Uri.fromFile(localFile)
            } else {
                try {
                    val streamUrl = repository.resolveStreamUrlForOnlinePlayback(freshEpisode.videoUrl)
                    Uri.parse(streamUrl)
                } catch (e: Exception) {
                    _uiState.update {
                        it.copy(
                            isResolvingStream = false,
                            isPlaying = false,
                            errorMessage = e.message ?: "Stream unavailable — try downloading for offline playback"
                        )
                    }
                    return@launch
                }
            }

            val metadata = MediaMetadata.Builder()
                .setTitle(freshEpisode.title)
                .setArtist(freshEpisode.uploader.ifBlank { resolvedPlaylist?.title ?: "PodDeck" })
                .setAlbumTitle(resolvedPlaylist?.title ?: "PodDeck")
                .apply {
                    val thumb = freshEpisode.thumbnailUrl ?: resolvedPlaylist?.thumbnailUrl
                    if (!thumb.isNullOrBlank()) {
                        setArtworkUri(Uri.parse(thumb))
                    }
                }
                .build()

            val mediaItem = MediaItem.Builder()
                .setMediaId(freshEpisode.id.toString())
                .setUri(mediaUri)
                .setMediaMetadata(metadata)
                .build()

            connectToService { controller ->
                controller.setMediaItem(mediaItem, startPos)
                controller.playbackParameters = PlaybackParameters(speed)
                controller.prepare()
                controller.play()
                sendVolumeBoostToService(controller, _uiState.value.volumeBoostEnabled)
                _uiState.update {
                    it.copy(
                        isResolvingStream = false,
                        isPlaying = true
                    )
                }
                scope.launch {
                    repository.savePlaybackProgress(
                        freshEpisode.id,
                        startPos,
                        freshEpisode.durationMs
                    )
                }
            }
        }
    }

    fun enqueuePlayNext(episode: EpisodeEntity) {
        val state = _uiState.value
        val current = state.currentEpisode
        if (current == null) {
            playEpisode(episode, state.currentPlaylist, listOf(episode))
            return
        }
        val filtered = state.queue.filter { it.id != episode.id }
        val curIdx = filtered.indexOfFirst { it.id == current.id }.coerceAtLeast(0)
        val mutable = filtered.toMutableList()
        mutable.add((curIdx + 1).coerceAtMost(mutable.size), episode)
        _uiState.update { it.copy(queue = mutable) }
    }

    fun addToQueue(episode: EpisodeEntity) {
        val state = _uiState.value
        if (state.currentEpisode == null) {
            playEpisode(episode, state.currentPlaylist, listOf(episode))
            return
        }
        if (state.queue.any { it.id == episode.id }) return
        _uiState.update { it.copy(queue = it.queue + episode) }
    }

    fun toggleVolumeBoost() {
        val next = !_uiState.value.volumeBoostEnabled
        _uiState.update { it.copy(volumeBoostEnabled = next) }
        mediaController?.let { sendVolumeBoostToService(it, next) }
    }

    private fun sendVolumeBoostToService(controller: MediaController, enabled: Boolean) {
        runCatching {
            val args = Bundle().apply { putBoolean(PlaybackService.ARG_BOOST_ENABLED, enabled) }
            controller.sendCustomCommand(
                SessionCommand(PlaybackService.COMMAND_SET_BOOST, Bundle.EMPTY),
                args
            )
        }
    }

    fun togglePlayPause() {
        val controller = mediaController
        val currentEp = _uiState.value.currentEpisode
        if (controller == null || controller.mediaItemCount == 0) {
            if (currentEp != null) {
                playEpisode(currentEp, _uiState.value.currentPlaylist, _uiState.value.queue)
            }
            return
        }
        if (controller.isPlaying) {
            controller.pause()
        } else {
            controller.play()
        }
    }

    fun seekTo(positionMs: Long) {
        val clamped = positionMs.coerceAtLeast(0L)
        mediaController?.seekTo(clamped)
        _uiState.update { it.copy(positionMs = clamped) }
        persistCurrentPositionNow()
    }

    fun skipForward(deltaMs: Long = 30_000L) {
        val controller = mediaController ?: return
        val maxDur = controller.duration.takeIf { it > 0L } ?: _uiState.value.durationMs
        val target = if (maxDur > 0L) {
            (controller.currentPosition + deltaMs).coerceAtMost(maxDur)
        } else {
            controller.currentPosition + deltaMs
        }
        seekTo(target)
    }

    fun skipBackward(deltaMs: Long = 15_000L) {
        val controller = mediaController ?: return
        val target = (controller.currentPosition - deltaMs).coerceAtLeast(0L)
        seekTo(target)
    }

    fun playNextInQueue() {
        val state = _uiState.value
        val current = state.currentEpisode ?: return
        val idx = state.queue.indexOfFirst { it.id == current.id }
        if (idx >= 0 && idx + 1 < state.queue.size) {
            playEpisode(state.queue[idx + 1], state.currentPlaylist, state.queue)
        }
    }

    fun playPreviousInQueue() {
        val state = _uiState.value
        val current = state.currentEpisode ?: return
        if ((mediaController?.currentPosition ?: state.positionMs) > 5_000L) {
            seekTo(0L)
            return
        }
        val idx = state.queue.indexOfFirst { it.id == current.id }
        if (idx > 0) {
            playEpisode(state.queue[idx - 1], state.currentPlaylist, state.queue)
        } else {
            seekTo(0L)
        }
    }

    fun setPlaybackSpeed(speed: Float) {
        val clamped = speed.coerceIn(0.5f, 3.0f)
        mediaController?.playbackParameters = PlaybackParameters(clamped)
        _uiState.update { it.copy(playbackSpeed = clamped) }
        val playlistId = _uiState.value.currentPlaylist?.id
        scope.launch {
            if (playlistId != null) {
                repository.updatePlaylistSpeed(playlistId, clamped)
            }
            repository.userPreferences.setDefaultSpeed(clamped)
        }
    }

    fun setSleepTimerMinutes(minutes: Int?) {
        sleepTimerJob?.cancel()
        if (minutes == null || minutes <= 0) {
            _uiState.update { it.copy(sleepTimerRemainingMs = null, sleepAtEndOfEpisode = false) }
            return
        }
        var remaining = minutes * 60_000L
        _uiState.update { it.copy(sleepTimerRemainingMs = remaining, sleepAtEndOfEpisode = false) }
        sleepTimerJob = scope.launch {
            while (remaining > 0L && isActive) {
                delay(1000L)
                remaining -= 1000L
                _uiState.update { it.copy(sleepTimerRemainingMs = remaining.coerceAtLeast(0L)) }
            }
            if (isActive) {
                mediaController?.pause()
                _uiState.update { it.copy(sleepTimerRemainingMs = null) }
            }
        }
    }

    fun toggleSleepAtEndOfEpisode() {
        sleepTimerJob?.cancel()
        val next = !_uiState.value.sleepAtEndOfEpisode
        _uiState.update {
            it.copy(
                sleepAtEndOfEpisode = next,
                sleepTimerRemainingMs = null
            )
        }
    }

    fun clearError() {
        _uiState.update { it.copy(errorMessage = null) }
    }

    fun stopIfPlayingEpisode(episodeId: Long) {
        val state = _uiState.value
        if (state.currentEpisode?.id == episodeId) {
            stopAndResetPlayback()
        } else if (state.queue.any { it.id == episodeId }) {
            _uiState.update { it.copy(queue = it.queue.filterNot { ep -> ep.id == episodeId }) }
        }
    }

    fun stopIfPlayingPlaylist(playlistId: Long) {
        val state = _uiState.value
        if (state.currentEpisode?.playlistId == playlistId || state.currentPlaylist?.id == playlistId) {
            stopAndResetPlayback()
        }
    }

    fun stopIfPlayingOfflineFile(episodeId: Long) {
        val state = _uiState.value
        if (state.currentEpisode?.id == episodeId && state.isOfflineSource) {
            stopAndResetPlayback()
        }
    }

    fun stopAllOfflinePlayback() {
        if (_uiState.value.isOfflineSource) {
            stopAndResetPlayback()
        }
    }

    private fun stopAndResetPlayback() {
        playRequestJob?.cancel()
        stopTicker()
        sleepTimerJob?.cancel()
        mediaController?.stop()
        mediaController?.clearMediaItems()
        _uiState.value = PlaybackUiState()
    }

    private fun startTicker() {
        tickerJob?.cancel()
        tickerJob = scope.launch {
            while (isActive) {
                val controller = mediaController
                if (controller != null) {
                    val pos = controller.currentPosition.coerceAtLeast(0L)
                    val dur = controller.duration.takeIf { it > 0L } ?: _uiState.value.durationMs
                    _uiState.update { it.copy(positionMs = pos, durationMs = dur) }

                    val now = System.currentTimeMillis()
                    if (now - lastSavedDbMs >= 2000L) {
                        lastSavedDbMs = now
                        val epId = _uiState.value.currentEpisode?.id
                        if (epId != null) {
                            repository.savePlaybackProgress(epId, pos, dur)
                        }
                    }
                }
                delay(500L)
            }
        }
    }

    private fun stopTicker() {
        tickerJob?.cancel()
        tickerJob = null
    }

    private fun persistCurrentPositionNow() {
        val state = _uiState.value
        val ep = state.currentEpisode ?: return
        val pos = mediaController?.currentPosition?.coerceAtLeast(0L) ?: state.positionMs
        val dur = mediaController?.duration?.takeIf { it > 0L } ?: state.durationMs
        scope.launch {
            repository.savePlaybackProgress(ep.id, pos, dur)
        }
    }

    private fun handleEpisodeEnded() {
        val state = _uiState.value
        val ep = state.currentEpisode ?: return
        val finalDur = mediaController?.duration?.takeIf { it > 0L } ?: state.durationMs
        scope.launch {
            repository.savePlaybackProgress(ep.id, finalDur, finalDur)
            if (state.sleepAtEndOfEpisode) {
                _uiState.update { it.copy(sleepAtEndOfEpisode = false, isPlaying = false) }
                return@launch
            }
            val idx = state.queue.indexOfFirst { it.id == ep.id }
            if (idx >= 0 && idx + 1 < state.queue.size) {
                val nextEp = state.queue[idx + 1]
                playEpisode(nextEp, state.currentPlaylist, state.queue)
            } else {
                _uiState.update { it.copy(isPlaying = false, positionMs = finalDur) }
            }
        }
    }

    fun release() {
        persistCurrentPositionNow()
        stopTicker()
        sleepTimerJob?.cancel()
        controllerFuture?.let { MediaController.releaseFuture(it) }
    }
}
