package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.PlaylistAdd
import androidx.compose.material.icons.automirrored.rounded.Sort
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Checklist
import androidx.compose.material.icons.rounded.CleaningServices
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.CloudDownload
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Downloading
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.OfflinePin
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PauseCircleOutline
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.QueueMusic
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.RestartAlt
import androidx.compose.material.icons.rounded.SaveAlt
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.SelectAll
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.data.model.DownloadStatus
import com.example.data.model.EpisodeEntity
import com.example.data.model.PlaylistWithEpisodes
import com.example.player.PlaybackUiState
import com.example.ui.components.AddVideoDialog
import com.example.ui.components.ConfirmDeleteDialog
import com.example.ui.components.EditPlaylistDialog
import com.example.ui.components.PodcastArtwork
import com.example.ui.components.RenameEpisodeDialog
import com.example.ui.viewmodel.EpisodeFilter
import com.example.ui.viewmodel.EpisodeSortOrder
import com.example.util.TimeFormatters

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlaylistDetailScreen(
    playlistWithEpisodes: PlaylistWithEpisodes?,
    playbackState: PlaybackUiState,
    isSyncing: Boolean,
    isAddingVideo: Boolean,
    bottomContentPadding: Dp,
    onBack: () -> Unit,
    onSyncPlaylist: (Long) -> Unit,
    onAddVideo: (Long, String, () -> Unit) -> Unit,
    onEditPlaylist: (Long, String, String) -> Unit,
    onDeletePlaylist: (Long) -> Unit,
    onPlayEpisode: (EpisodeEntity, List<EpisodeEntity>, Boolean) -> Unit,
    onEnqueuePlayNext: (EpisodeEntity) -> Unit,
    onAddToQueue: (EpisodeEntity) -> Unit,
    onTogglePlayPause: () -> Unit,
    onDownloadEpisode: (EpisodeEntity) -> Unit,
    onDownloadAll: (List<EpisodeEntity>) -> Unit,
    onDownloadSelected: (List<EpisodeEntity>) -> Unit,
    onDownloadNextUnplayed: (List<EpisodeEntity>) -> Unit,
    onCleanCompletedOffline: (Long) -> Unit,
    onPauseDownload: (Long) -> Unit,
    onPausePlaylistDownloads: (List<EpisodeEntity>) -> Unit,
    onResumePlaylistDownloads: (List<EpisodeEntity>) -> Unit,
    onStopPlaylistDownloads: (List<EpisodeEntity>) -> Unit,
    onCancelDownload: (Long) -> Unit,
    onRemoveDownloadedFile: (EpisodeEntity) -> Unit,
    onExportOfflineFile: (EpisodeEntity) -> Unit,
    onToggleEpisodeCompleted: (EpisodeEntity) -> Unit,
    onResetEpisodeProgress: (Long) -> Unit,
    onRenameEpisode: (Long, String) -> Unit,
    onDeleteEpisode: (EpisodeEntity) -> Unit,
    modifier: Modifier = Modifier
) {
    var selectedFilter by remember { mutableStateOf(EpisodeFilter.ALL) }
    var sortOrder by remember { mutableStateOf(EpisodeSortOrder.PLAYLIST_ORDER) }
    var searchVisible by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    var showSortMenu by remember { mutableStateOf(false) }
    var showTopMenu by remember { mutableStateOf(false) }
    var showAddVideoDialog by remember { mutableStateOf(false) }
    var showEditPlaylistDialog by remember { mutableStateOf(false) }
    var showDeletePlaylistDialog by remember { mutableStateOf(false) }
    var episodeToRename by remember { mutableStateOf<EpisodeEntity?>(null) }
    var episodeToDelete by remember { mutableStateOf<EpisodeEntity?>(null) }

    var isSelectionMode by remember { mutableStateOf(false) }
    var selectedEpisodeIds by remember { mutableStateOf(emptySet<Long>()) }

    LaunchedEffect(playlistWithEpisodes) {
        val validIds = playlistWithEpisodes?.episodes?.mapTo(mutableSetOf()) { it.id }.orEmpty()
        if (validIds.isEmpty()) {
            isSelectionMode = false
            selectedEpisodeIds = emptySet()
        } else {
            val pruned = selectedEpisodeIds.intersect(validIds)
            if (pruned != selectedEpisodeIds) {
                selectedEpisodeIds = pruned
            }
        }
    }

    BackHandler(enabled = isSelectionMode) {
        isSelectionMode = false
        selectedEpisodeIds = emptySet()
    }

    val sortedEpisodes = remember(playlistWithEpisodes, sortOrder) {
        val base = playlistWithEpisodes?.sortedEpisodes.orEmpty()
        when (sortOrder) {
            EpisodeSortOrder.PLAYLIST_ORDER -> base
            EpisodeSortOrder.UNPLAYED_FIRST -> base.sortedWith(compareBy<EpisodeEntity> { it.isCompleted }.thenBy { it.sortOrder })
            EpisodeSortOrder.SHORTEST_FIRST -> base.sortedBy { if (it.durationMs > 0L) it.durationMs else Long.MAX_VALUE }
            EpisodeSortOrder.LONGEST_FIRST -> base.sortedByDescending { it.durationMs }
        }
    }

    val filteredEpisodes = remember(sortedEpisodes, selectedFilter, searchQuery) {
        sortedEpisodes.filter { ep ->
            val matchesFilter = when (selectedFilter) {
                EpisodeFilter.ALL -> true
                EpisodeFilter.UNFINISHED -> !ep.isCompleted
                EpisodeFilter.DOWNLOADED -> ep.isDownloaded
                EpisodeFilter.COMPLETED -> ep.isCompleted
            }
            val matchesQuery = searchQuery.isBlank() ||
                ep.title.contains(searchQuery, ignoreCase = true) ||
                ep.uploader.contains(searchQuery, ignoreCase = true)
            matchesFilter && matchesQuery
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        TopAppBar(
            title = {
                Text(
                    text = if (isSelectionMode) {
                        "${selectedEpisodeIds.size} selected"
                    } else {
                        playlistWithEpisodes?.playlist?.title ?: "Playlist"
                    },
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            },
            navigationIcon = {
                if (isSelectionMode) {
                    IconButton(
                        onClick = {
                            isSelectionMode = false
                            selectedEpisodeIds = emptySet()
                        },
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("exit_selection_mode_button")
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Close,
                            contentDescription = "Exit selection mode"
                        )
                    }
                } else {
                    IconButton(
                        onClick = onBack,
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("detail_back_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                }
            },
            actions = {
                if (isSelectionMode) {
                    val visibleIds = filteredEpisodes.map { it.id }.toSet()
                    val allVisibleSelected = visibleIds.isNotEmpty() && visibleIds.all { it in selectedEpisodeIds }
                    val unplayedIds = filteredEpisodes
                        .filter { !it.isCompleted && !it.isDownloaded }
                        .map { it.id }
                        .toSet()

                    if (unplayedIds.isNotEmpty()) {
                        TextButton(
                            onClick = { selectedEpisodeIds = unplayedIds },
                            modifier = Modifier.testTag("select_unplayed_episodes_button")
                        ) {
                            Text("Unplayed")
                        }
                    }
                    IconButton(
                        onClick = {
                            selectedEpisodeIds = if (allVisibleSelected) {
                                emptySet()
                            } else {
                                visibleIds
                            }
                        },
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("select_all_episodes_button")
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.SelectAll,
                            contentDescription = if (allVisibleSelected) "Deselect all" else "Select all"
                        )
                    }
                } else {
                    IconButton(
                        onClick = {
                            searchVisible = !searchVisible
                            if (!searchVisible) searchQuery = ""
                        },
                        modifier = Modifier.minimumInteractiveComponentSize()
                    ) {
                        Icon(
                            imageVector = if (searchVisible) Icons.Rounded.Close else Icons.Rounded.Search,
                            contentDescription = "Search episodes"
                        )
                    }
                    if (isSyncing) {
                        Box(
                            modifier = Modifier.minimumInteractiveComponentSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                strokeWidth = 2.dp
                            )
                        }
                    }
                    IconButton(
                        onClick = { showAddVideoDialog = true },
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("add_video_to_playlist_button")
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Add,
                            contentDescription = "Add video URL"
                        )
                    }
                    Box {
                        IconButton(
                            onClick = { showTopMenu = true },
                            modifier = Modifier
                                .minimumInteractiveComponentSize()
                                .testTag("detail_more_menu_button")
                        ) {
                            Icon(Icons.Rounded.MoreVert, contentDescription = "More actions")
                        }
                        DropdownMenu(
                            expanded = showTopMenu,
                            onDismissRequest = { showTopMenu = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text("Select Episodes to Download") },
                                leadingIcon = { Icon(Icons.Rounded.Checklist, contentDescription = null) },
                                onClick = {
                                    showTopMenu = false
                                    isSelectionMode = true
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Download Next 5 Unplayed") },
                                leadingIcon = { Icon(Icons.Rounded.Download, contentDescription = null) },
                                onClick = {
                                    showTopMenu = false
                                    onDownloadNextUnplayed(sortedEpisodes)
                                }
                            )
                            if (playlistWithEpisodes != null && !playlistWithEpisodes.playlist.isCustom) {
                                DropdownMenuItem(
                                    text = {
                                        Column {
                                            Text(if (isSyncing) "Syncing New Videos..." else "Sync New Videos")
                                            Text(
                                                text = TimeFormatters.formatRelativeSyncTime(
                                                    playlistWithEpisodes.playlist.lastSyncedAt ?: 0L
                                                ),
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    },
                                    leadingIcon = { Icon(Icons.Rounded.Refresh, contentDescription = null) },
                                    enabled = !isSyncing,
                                    onClick = {
                                        showTopMenu = false
                                        onSyncPlaylist(playlistWithEpisodes.playlist.id)
                                    },
                                    modifier = Modifier.testTag("sync_playlist_menu_item")
                                )
                            }
                            if (playlistWithEpisodes != null) {
                                DropdownMenuItem(
                                    text = { Text("Clean Completed Offline Audio") },
                                    leadingIcon = { Icon(Icons.Rounded.CleaningServices, contentDescription = null) },
                                    onClick = {
                                        showTopMenu = false
                                        onCleanCompletedOffline(playlistWithEpisodes.playlist.id)
                                    }
                                )
                            }
                            DropdownMenuItem(
                                text = { Text("Edit Playlist Info") },
                                leadingIcon = { Icon(Icons.Rounded.Edit, contentDescription = null) },
                                onClick = {
                                    showTopMenu = false
                                    showEditPlaylistDialog = true
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Delete Playlist") },
                                leadingIcon = {
                                    Icon(
                                        Icons.Rounded.Delete,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.error
                                    )
                                },
                                onClick = {
                                    showTopMenu = false
                                    showDeletePlaylistDialog = true
                                }
                            )
                        }
                    }
                }
            },
            colors = TopAppBarDefaults.topAppBarColors(
                containerColor = MaterialTheme.colorScheme.background
            )
        )

        if (playlistWithEpisodes == null) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("Playlist not found", style = MaterialTheme.typography.bodyLarge)
            }
            return
        }

        val playlist = playlistWithEpisodes.playlist
        val pct = (playlistWithEpisodes.progressFraction * 100).toInt().coerceIn(0, 100)
        val resumeCandidate = remember(playlistWithEpisodes) {
            val orig = playlistWithEpisodes.sortedEpisodes
            orig.filter { !it.isCompleted && it.lastPositionMs > 0L }
                .maxByOrNull { it.lastPlayedAt ?: 0L }
                ?: orig.firstOrNull { !it.isCompleted }
                ?: orig.firstOrNull()
        }

        val allEpisodes = playlistWithEpisodes.sortedEpisodes
        val activeDownloadsCount = remember(allEpisodes) {
            allEpisodes.count {
                it.downloadStatus == DownloadStatus.DOWNLOADING || it.downloadStatus == DownloadStatus.QUEUED
            }
        }
        val pausedDownloadsCount = remember(allEpisodes) {
            allEpisodes.count { it.downloadStatus == DownloadStatus.PAUSED }
        }
        val downloadablePendingCount = remember(allEpisodes) {
            allEpisodes.count {
                !it.isDownloaded &&
                    it.downloadStatus != DownloadStatus.DOWNLOADING &&
                    it.downloadStatus != DownloadStatus.QUEUED
            }
        }

        val selectedEpisodes = remember(allEpisodes, selectedEpisodeIds) {
            allEpisodes.filter { it.id in selectedEpisodeIds }
        }
        val selectedDownloadable = remember(selectedEpisodes) {
            selectedEpisodes.filter {
                !it.isDownloaded &&
                    it.downloadStatus != DownloadStatus.DOWNLOADING &&
                    it.downloadStatus != DownloadStatus.QUEUED
            }
        }
        val selectedActive = remember(selectedEpisodes) {
            selectedEpisodes.filter {
                it.downloadStatus == DownloadStatus.DOWNLOADING || it.downloadStatus == DownloadStatus.QUEUED
            }
        }
        val selectedStoppable = remember(selectedEpisodes) {
            selectedEpisodes.filter {
                it.downloadStatus == DownloadStatus.DOWNLOADING ||
                    it.downloadStatus == DownloadStatus.QUEUED ||
                    it.downloadStatus == DownloadStatus.PAUSED ||
                    it.downloadStatus == DownloadStatus.FAILED
            }
        }

        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
        ) {
            PullToRefreshBox(
                isRefreshing = isSyncing,
                onRefresh = {
                    if (!playlist.isCustom) {
                        onSyncPlaylist(playlist.id)
                    }
                },
                modifier = Modifier.fillMaxSize()
            ) {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .widthIn(max = 720.dp)
                        .align(Alignment.TopCenter),
                    contentPadding = PaddingValues(
                        start = 16.dp,
                        end = 16.dp,
                        top = 8.dp,
                        bottom = if (isSelectionMode) bottomContentPadding + 88.dp else bottomContentPadding
                    ),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    item {
                        androidx.compose.animation.AnimatedVisibility(visible = searchVisible) {
                            OutlinedTextField(
                                value = searchQuery,
                                onValueChange = { searchQuery = it },
                                placeholder = { Text("Search episodes in this playlist...") },
                                leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
                                singleLine = true,
                                shape = RoundedCornerShape(16.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(bottom = 4.dp)
                                    .testTag("episode_search_input")
                            )
                        }
                    }

                    // Playlist Summary Header Card
                    item {
                        Surface(
                            shape = RoundedCornerShape(22.dp),
                            color = MaterialTheme.colorScheme.surface,
                            tonalElevation = 2.dp,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier.padding(16.dp),
                                verticalArrangement = Arrangement.spacedBy(14.dp)
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    PodcastArtwork(
                                        imageUrl = playlist.thumbnailUrl
                                            ?: playlistWithEpisodes.sortedEpisodes.firstOrNull()?.thumbnailUrl,
                                        contentDescription = playlist.title,
                                        modifier = Modifier.size(84.dp),
                                        shape = RoundedCornerShape(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(14.dp))
                                    Column(
                                        modifier = Modifier.weight(1f),
                                        verticalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        Text(
                                            text = playlist.title,
                                            style = MaterialTheme.typography.titleLarge,
                                            maxLines = 2,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        if (playlist.author.isNotBlank()) {
                                            Text(
                                                text = playlist.author,
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                        Text(
                                            text = "${playlistWithEpisodes.episodeCount} episodes • ${playlistWithEpisodes.downloadedCount} offline (${
                                                TimeFormatters.formatFileSize(playlistWithEpisodes.totalStorageBytes)
                                            })",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.secondary
                                        )
                                    }
                                }

                                if (playlist.description.isNotBlank()) {
                                    Text(
                                        text = playlist.description,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 3,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    StatPill(
                                        label = "Total",
                                        value = TimeFormatters.formatHoursMinutes(playlistWithEpisodes.totalDurationMs)
                                    )
                                    StatPill(
                                        label = "Listened",
                                        value = TimeFormatters.formatHoursMinutes(playlistWithEpisodes.listenedDurationMs)
                                    )
                                    StatPill(
                                        label = "Remaining",
                                        value = TimeFormatters.formatHoursMinutes(playlistWithEpisodes.remainingDurationMs)
                                    )
                                    StatPill(
                                        label = "Progress",
                                        value = "$pct%"
                                    )
                                }

                                LinearProgressIndicator(
                                    progress = { playlistWithEpisodes.progressFraction },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(6.dp)
                                        .clip(RoundedCornerShape(4.dp)),
                                    color = if (pct == 100) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.primary,
                                    trackColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.25f)
                                )

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    Button(
                                        onClick = {
                                            resumeCandidate?.let { ep ->
                                                onPlayEpisode(ep, playlistWithEpisodes.sortedEpisodes, false)
                                            }
                                        },
                                        enabled = resumeCandidate != null,
                                        modifier = Modifier
                                            .weight(1f)
                                            .testTag("detail_resume_play_button")
                                    ) {
                                        Icon(
                                            imageVector = Icons.Rounded.PlayArrow,
                                            contentDescription = null,
                                            modifier = Modifier.size(18.dp)
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(
                                            text = if (resumeCandidate != null && resumeCandidate.lastPositionMs > 0L && !resumeCandidate.isCompleted) {
                                                "Resume"
                                            } else {
                                                "Play Next"
                                            }
                                        )
                                    }
                                    OutlinedButton(
                                        onClick = { onDownloadAll(playlistWithEpisodes.sortedEpisodes) },
                                        enabled = downloadablePendingCount > 0,
                                        modifier = Modifier
                                            .weight(1f)
                                            .testTag("detail_download_all_button")
                                    ) {
                                        Icon(
                                            imageVector = Icons.Rounded.Download,
                                            contentDescription = null,
                                            modifier = Modifier.size(18.dp)
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(
                                            when {
                                                downloadablePendingCount > 0 -> "Download All ($downloadablePendingCount)"
                                                activeDownloadsCount > 0 -> "Downloading ($activeDownloadsCount)"
                                                else -> "All Offline"
                                            },
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                }

                                // Pause All / Resume / Stop All control strip when downloads are active or paused
                                if (activeDownloadsCount > 0 || pausedDownloadsCount > 0) {
                                    Surface(
                                        shape = RoundedCornerShape(14.dp),
                                        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Column(
                                            modifier = Modifier.padding(12.dp),
                                            verticalArrangement = Arrangement.spacedBy(10.dp)
                                        ) {
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                                            ) {
                                                Icon(
                                                    imageVector = if (activeDownloadsCount > 0) {
                                                        Icons.Rounded.Downloading
                                                    } else {
                                                        Icons.Rounded.PauseCircleOutline
                                                    },
                                                    contentDescription = null,
                                                    tint = MaterialTheme.colorScheme.primary,
                                                    modifier = Modifier.size(18.dp)
                                                )
                                                Text(
                                                    text = when {
                                                        activeDownloadsCount > 0 && pausedDownloadsCount > 0 ->
                                                            "Downloading $activeDownloadsCount • $pausedDownloadsCount paused"
                                                        activeDownloadsCount > 0 ->
                                                            "Downloading $activeDownloadsCount episode(s)"
                                                        else ->
                                                            "$pausedDownloadsCount download(s) paused"
                                                    },
                                                    style = MaterialTheme.typography.labelLarge,
                                                    color = MaterialTheme.colorScheme.onSurface
                                                )
                                            }
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                                            ) {
                                                if (activeDownloadsCount > 0) {
                                                    FilledTonalButton(
                                                        onClick = { onPausePlaylistDownloads(allEpisodes) },
                                                        modifier = Modifier
                                                            .weight(1f)
                                                            .testTag("pause_all_downloads_button")
                                                    ) {
                                                        Icon(
                                                            imageVector = Icons.Rounded.Pause,
                                                            contentDescription = null,
                                                            modifier = Modifier.size(16.dp)
                                                        )
                                                        Spacer(modifier = Modifier.width(6.dp))
                                                        Text("Pause All")
                                                    }
                                                }
                                                if (pausedDownloadsCount > 0) {
                                                    FilledTonalButton(
                                                        onClick = { onResumePlaylistDownloads(allEpisodes) },
                                                        modifier = Modifier
                                                            .weight(1f)
                                                            .testTag("resume_all_downloads_button")
                                                    ) {
                                                        Icon(
                                                            imageVector = Icons.Rounded.PlayArrow,
                                                            contentDescription = null,
                                                            modifier = Modifier.size(16.dp)
                                                        )
                                                        Spacer(modifier = Modifier.width(6.dp))
                                                        Text("Resume ($pausedDownloadsCount)")
                                                    }
                                                }
                                                OutlinedButton(
                                                    onClick = { onStopPlaylistDownloads(allEpisodes) },
                                                    modifier = Modifier
                                                        .weight(1f)
                                                        .testTag("stop_all_downloads_button")
                                                ) {
                                                    Icon(
                                                        imageVector = Icons.Rounded.Stop,
                                                        contentDescription = null,
                                                        modifier = Modifier.size(16.dp)
                                                    )
                                                    Spacer(modifier = Modifier.width(6.dp))
                                                    Text("Stop All")
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // Filter, Multi-Select Toggle & Sort Row
                    item {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            LazyRow(
                                modifier = Modifier.weight(1f),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                items(EpisodeFilter.entries) { filter ->
                                    FilterChip(
                                        selected = selectedFilter == filter,
                                        onClick = { selectedFilter = filter },
                                        label = { Text(filter.label) }
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.width(4.dp))
                            FilterChip(
                                selected = isSelectionMode,
                                onClick = {
                                    isSelectionMode = !isSelectionMode
                                    if (!isSelectionMode) selectedEpisodeIds = emptySet()
                                },
                                leadingIcon = {
                                    Icon(
                                        imageVector = Icons.Rounded.Checklist,
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp)
                                    )
                                },
                                label = { Text(if (isSelectionMode) "Done" else "Select") },
                                modifier = Modifier.testTag("toggle_select_mode_button")
                            )
                            Box {
                                IconButton(
                                    onClick = { showSortMenu = true },
                                    modifier = Modifier
                                        .minimumInteractiveComponentSize()
                                        .testTag("sort_episodes_button")
                                ) {
                                    Icon(
                                        imageVector = Icons.AutoMirrored.Rounded.Sort,
                                        contentDescription = "Sort episodes"
                                    )
                                }
                                DropdownMenu(
                                    expanded = showSortMenu,
                                    onDismissRequest = { showSortMenu = false }
                                ) {
                                    EpisodeSortOrder.entries.forEach { order ->
                                        DropdownMenuItem(
                                            text = { Text(order.label) },
                                            leadingIcon = if (sortOrder == order) {
                                                { Icon(Icons.Rounded.CheckCircle, contentDescription = null) }
                                            } else {
                                                null
                                            },
                                            onClick = {
                                                sortOrder = order
                                                showSortMenu = false
                                            }
                                        )
                                    }
                                }
                            }
                        }
                    }

                    if (filteredEpisodes.isEmpty()) {
                        item {
                            Surface(
                                shape = RoundedCornerShape(18.dp),
                                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(
                                    modifier = Modifier.padding(24.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Text(
                                        text = if (playlistWithEpisodes.sortedEpisodes.isEmpty()) "No episodes yet" else "No matching episodes",
                                        style = MaterialTheme.typography.titleMedium
                                    )
                                    Text(
                                        text = if (playlistWithEpisodes.sortedEpisodes.isEmpty()) {
                                            "Tap + at the top right to add a YouTube video link, or tap Sync New Videos."
                                        } else {
                                            "Switch filters or clear search to see other episodes."
                                        },
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    } else {
                        items(
                            items = filteredEpisodes,
                            key = { it.id }
                        ) { episode ->
                            val isCurrent = playbackState.currentEpisode?.id == episode.id
                            val isSelected = episode.id in selectedEpisodeIds
                            EpisodeRowCard(
                                episode = episode,
                                isCurrent = isCurrent,
                                isPlaying = isCurrent && playbackState.isPlaying,
                                isSelectionMode = isSelectionMode,
                                isSelected = isSelected,
                                onToggleSelect = {
                                    selectedEpisodeIds = if (isSelected) {
                                        selectedEpisodeIds - episode.id
                                    } else {
                                        selectedEpisodeIds + episode.id
                                    }
                                },
                                onEnterSelectionMode = {
                                    isSelectionMode = true
                                    selectedEpisodeIds = selectedEpisodeIds + episode.id
                                },
                                onPlay = {
                                    if (isCurrent) {
                                        onTogglePlayPause()
                                    } else {
                                        onPlayEpisode(episode, sortedEpisodes, false)
                                    }
                                },
                                onPlayFromStart = { onPlayEpisode(episode, sortedEpisodes, true) },
                                onEnqueuePlayNext = { onEnqueuePlayNext(episode) },
                                onAddToQueue = { onAddToQueue(episode) },
                                onDownload = { onDownloadEpisode(episode) },
                                onPauseDownload = { onPauseDownload(episode.id) },
                                onCancelDownload = { onCancelDownload(episode.id) },
                                onRemoveDownload = { onRemoveDownloadedFile(episode) },
                                onExportOffline = { onExportOfflineFile(episode) },
                                onToggleCompleted = { onToggleEpisodeCompleted(episode) },
                                onResetProgress = { onResetEpisodeProgress(episode.id) },
                                onRename = { episodeToRename = episode },
                                onDelete = { episodeToDelete = episode }
                            )
                        }
                    }
                }
            }

            // Floating Multi-Select Action Bar
            androidx.compose.animation.AnimatedVisibility(
                visible = isSelectionMode,
                enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
                exit = slideOutVertically(targetOffsetY = { it }) + fadeOut(),
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(start = 16.dp, end = 16.dp, bottom = bottomContentPadding + 8.dp)
            ) {
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                    tonalElevation = 8.dp,
                    shadowElevation = 6.dp,
                    modifier = Modifier
                        .widthIn(max = 680.dp)
                        .fillMaxWidth()
                        .testTag("selection_action_bar")
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Button(
                            onClick = {
                                onDownloadSelected(selectedDownloadable)
                                isSelectionMode = false
                                selectedEpisodeIds = emptySet()
                            },
                            enabled = selectedDownloadable.isNotEmpty(),
                            modifier = Modifier
                                .weight(1f)
                                .testTag("download_selected_button")
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Download,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Download (${selectedDownloadable.size})")
                        }
                        if (selectedActive.isNotEmpty()) {
                            FilledTonalButton(
                                onClick = {
                                    onPausePlaylistDownloads(selectedActive)
                                    isSelectionMode = false
                                    selectedEpisodeIds = emptySet()
                                },
                                modifier = Modifier.testTag("pause_selected_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Pause,
                                    contentDescription = "Pause selected",
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Pause (${selectedActive.size})")
                            }
                        }
                        if (selectedStoppable.isNotEmpty()) {
                            OutlinedButton(
                                onClick = {
                                    onStopPlaylistDownloads(selectedStoppable)
                                    isSelectionMode = false
                                    selectedEpisodeIds = emptySet()
                                },
                                modifier = Modifier.testTag("stop_selected_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Stop,
                                    contentDescription = "Stop selected",
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Stop (${selectedStoppable.size})")
                            }
                        }
                    }
                }
            }
        }
    }

    if (showAddVideoDialog && playlistWithEpisodes != null) {
        AddVideoDialog(
            isLoading = isAddingVideo,
            onDismiss = { showAddVideoDialog = false },
            onAddVideo = { url ->
                onAddVideo(playlistWithEpisodes.playlist.id, url) {
                    showAddVideoDialog = false
                }
            }
        )
    }

    if (showEditPlaylistDialog && playlistWithEpisodes != null) {
        EditPlaylistDialog(
            initialTitle = playlistWithEpisodes.playlist.title,
            initialDescription = playlistWithEpisodes.playlist.description,
            onDismiss = { showEditPlaylistDialog = false },
            onSave = { title, desc ->
                onEditPlaylist(playlistWithEpisodes.playlist.id, title, desc)
                showEditPlaylistDialog = false
            }
        )
    }

    if (showDeletePlaylistDialog && playlistWithEpisodes != null) {
        ConfirmDeleteDialog(
            title = "Delete Playlist?",
            message = "Delete \"${playlistWithEpisodes.playlist.title}\" and all downloaded audio files?",
            onDismiss = { showDeletePlaylistDialog = false },
            onConfirm = {
                showDeletePlaylistDialog = false
                onDeletePlaylist(playlistWithEpisodes.playlist.id)
            }
        )
    }

    episodeToRename?.let { ep ->
        RenameEpisodeDialog(
            initialTitle = ep.title,
            onDismiss = { episodeToRename = null },
            onSave = { newTitle ->
                onRenameEpisode(ep.id, newTitle)
                episodeToRename = null
            }
        )
    }

    episodeToDelete?.let { ep ->
        ConfirmDeleteDialog(
            title = "Remove Episode?",
            message = "Remove \"${ep.title}\" from this playlist and delete its downloaded audio if present?",
            confirmLabel = "Remove",
            onDismiss = { episodeToDelete = null },
            onConfirm = {
                onDeleteEpisode(ep)
                episodeToDelete = null
            }
        )
    }
}

@Composable
private fun StatPill(label: String, value: String) {
    Column(horizontalAlignment = Alignment.Start) {
        Text(
            text = label.uppercase(),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value,
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun EpisodeRowCard(
    episode: EpisodeEntity,
    isCurrent: Boolean,
    isPlaying: Boolean,
    isSelectionMode: Boolean,
    isSelected: Boolean,
    onToggleSelect: () -> Unit,
    onEnterSelectionMode: () -> Unit,
    onPlay: () -> Unit,
    onPlayFromStart: () -> Unit,
    onEnqueuePlayNext: () -> Unit,
    onAddToQueue: () -> Unit,
    onDownload: () -> Unit,
    onPauseDownload: () -> Unit,
    onCancelDownload: () -> Unit,
    onRemoveDownload: () -> Unit,
    onExportOffline: () -> Unit,
    onToggleCompleted: () -> Unit,
    onResetProgress: () -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit
) {
    var menuExpanded by remember { mutableStateOf(false) }

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .combinedClickable(
                onClick = {
                    if (isSelectionMode) onToggleSelect() else onPlay()
                },
                onLongClick = {
                    if (isSelectionMode) onToggleSelect() else onEnterSelectionMode()
                }
            )
            .testTag("episode_card_${episode.id}"),
        shape = RoundedCornerShape(16.dp),
        color = when {
            isSelected -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f)
            isCurrent -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f)
            else -> MaterialTheme.colorScheme.surface
        },
        tonalElevation = if (isCurrent || isSelected) 4.dp else 1.dp
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (isSelectionMode) {
                    Checkbox(
                        checked = isSelected,
                        onCheckedChange = { onToggleSelect() },
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("select_episode_checkbox_${episode.id}")
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                }

                Box(contentAlignment = Alignment.Center) {
                    PodcastArtwork(
                        imageUrl = episode.thumbnailUrl,
                        contentDescription = episode.title,
                        modifier = Modifier.size(56.dp),
                        shape = RoundedCornerShape(12.dp)
                    )
                    if (isCurrent) {
                        Box(
                            modifier = Modifier
                                .size(56.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(MaterialTheme.colorScheme.scrim.copy(alpha = 0.5f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = if (isPlaying) Icons.Rounded.GraphicEq else Icons.Rounded.Pause,
                                contentDescription = if (isPlaying) "Playing" else "Paused",
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                    } else if (episode.isCompleted) {
                        Box(
                            modifier = Modifier
                                .align(Alignment.BottomEnd)
                                .padding(3.dp)
                                .size(18.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.secondary),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.CheckCircle,
                                contentDescription = "Completed",
                                tint = MaterialTheme.colorScheme.onSecondary,
                                modifier = Modifier.size(14.dp)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.width(12.dp))

                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(3.dp)
                ) {
                    Text(
                        text = episode.title,
                        style = MaterialTheme.typography.titleSmall,
                        color = if (episode.isCompleted && !isCurrent) {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        } else {
                            MaterialTheme.colorScheme.onSurface
                        },
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = if (episode.durationMs > 0L) {
                                TimeFormatters.formatDurationTimestamp(episode.durationMs)
                            } else {
                                "Audio"
                            },
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        if (episode.lastPositionMs > 0L && !episode.isCompleted) {
                            val left = (episode.durationMs - episode.lastPositionMs).coerceAtLeast(0L)
                            Text(
                                text = if (left > 0L) "• ${TimeFormatters.formatDurationTimestamp(left)} left" else "• In progress",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary
                            )
                        } else if (episode.isCompleted) {
                            Text(
                                text = "• Done",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.secondary
                            )
                        }
                        if (episode.isDownloaded) {
                            Text(
                                text = "• ${TimeFormatters.formatFileSize(episode.fileSizeBytes)}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.secondary
                            )
                        }
                    }
                }

                if (!isSelectionMode) {
                    when (episode.downloadStatus) {
                        DownloadStatus.NONE -> {
                            IconButton(
                                onClick = onDownload,
                                modifier = Modifier
                                    .minimumInteractiveComponentSize()
                                    .testTag("download_episode_${episode.id}")
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.CloudDownload,
                                    contentDescription = "Download audio offline",
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                        DownloadStatus.QUEUED, DownloadStatus.DOWNLOADING -> {
                            Box(
                                modifier = Modifier
                                    .size(48.dp)
                                    .clip(CircleShape)
                                    .clickable(onClick = onPauseDownload)
                                    .testTag("pause_download_${episode.id}"),
                                contentAlignment = Alignment.Center
                            ) {
                                if (episode.downloadStatus == DownloadStatus.QUEUED) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(24.dp),
                                        strokeWidth = 2.5.dp
                                    )
                                } else {
                                    CircularProgressIndicator(
                                        progress = { (episode.downloadProgress / 100f).coerceIn(0.05f, 1f) },
                                        modifier = Modifier.size(28.dp),
                                        strokeWidth = 2.5.dp
                                    )
                                }
                                Icon(
                                    imageVector = Icons.Rounded.Pause,
                                    contentDescription = "Pause download",
                                    modifier = Modifier.size(13.dp)
                                )
                            }
                        }
                        DownloadStatus.PAUSED -> {
                            IconButton(
                                onClick = onDownload,
                                modifier = Modifier
                                    .minimumInteractiveComponentSize()
                                    .testTag("resume_download_${episode.id}")
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.CloudDownload,
                                    contentDescription = "Resume download",
                                    tint = MaterialTheme.colorScheme.tertiary
                                )
                            }
                        }
                        DownloadStatus.COMPLETED -> {
                            IconButton(
                                onClick = onPlay,
                                modifier = Modifier
                                    .minimumInteractiveComponentSize()
                                    .testTag("offline_ready_${episode.id}")
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.OfflinePin,
                                    contentDescription = "Offline ready",
                                    tint = MaterialTheme.colorScheme.secondary
                                )
                            }
                        }
                        DownloadStatus.FAILED -> {
                            IconButton(
                                onClick = onDownload,
                                modifier = Modifier
                                    .minimumInteractiveComponentSize()
                                    .testTag("retry_download_${episode.id}")
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.ErrorOutline,
                                    contentDescription = "Retry download",
                                    tint = MaterialTheme.colorScheme.error
                                )
                            }
                        }
                    }

                    Box {
                        IconButton(
                            onClick = { menuExpanded = true },
                            modifier = Modifier.minimumInteractiveComponentSize()
                        ) {
                            Icon(Icons.Rounded.MoreVert, contentDescription = "Episode options")
                        }
                        DropdownMenu(
                            expanded = menuExpanded,
                            onDismissRequest = { menuExpanded = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text("Play from Beginning") },
                                leadingIcon = { Icon(Icons.Rounded.PlayArrow, contentDescription = null) },
                                onClick = {
                                    menuExpanded = false
                                    onPlayFromStart()
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Play Next") },
                                leadingIcon = { Icon(Icons.Rounded.QueueMusic, contentDescription = null) },
                                onClick = {
                                    menuExpanded = false
                                    onEnqueuePlayNext()
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Add to Queue") },
                                leadingIcon = { Icon(Icons.AutoMirrored.Rounded.PlaylistAdd, contentDescription = null) },
                                onClick = {
                                    menuExpanded = false
                                    onAddToQueue()
                                }
                            )
                            if (episode.downloadStatus == DownloadStatus.DOWNLOADING || episode.downloadStatus == DownloadStatus.QUEUED) {
                                DropdownMenuItem(
                                    text = { Text("Pause Download") },
                                    leadingIcon = { Icon(Icons.Rounded.Pause, contentDescription = null) },
                                    onClick = {
                                        menuExpanded = false
                                        onPauseDownload()
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text("Stop & Discard Download") },
                                    leadingIcon = { Icon(Icons.Rounded.Stop, contentDescription = null) },
                                    onClick = {
                                        menuExpanded = false
                                        onCancelDownload()
                                    }
                                )
                            } else if (episode.downloadStatus == DownloadStatus.PAUSED) {
                                DropdownMenuItem(
                                    text = { Text("Resume Download (${episode.downloadProgress}%)") },
                                    leadingIcon = { Icon(Icons.Rounded.CloudDownload, contentDescription = null) },
                                    onClick = {
                                        menuExpanded = false
                                        onDownload()
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text("Discard Partial Download") },
                                    leadingIcon = { Icon(Icons.Rounded.Stop, contentDescription = null) },
                                    onClick = {
                                        menuExpanded = false
                                        onCancelDownload()
                                    }
                                )
                            }
                            DropdownMenuItem(
                                text = { Text("Select Multiple") },
                                leadingIcon = { Icon(Icons.Rounded.Checklist, contentDescription = null) },
                                onClick = {
                                    menuExpanded = false
                                    onEnterSelectionMode()
                                }
                            )
                            DropdownMenuItem(
                                text = { Text(if (episode.isCompleted) "Mark Unfinished" else "Mark Completed") },
                                leadingIcon = { Icon(Icons.Rounded.CheckCircle, contentDescription = null) },
                                onClick = {
                                    menuExpanded = false
                                    onToggleCompleted()
                                }
                            )
                            if (episode.lastPositionMs > 0L) {
                                DropdownMenuItem(
                                    text = { Text("Reset Progress") },
                                    leadingIcon = { Icon(Icons.Rounded.RestartAlt, contentDescription = null) },
                                    onClick = {
                                        menuExpanded = false
                                        onResetProgress()
                                    }
                                )
                            }
                            DropdownMenuItem(
                                text = { Text("Rename Episode") },
                                leadingIcon = { Icon(Icons.Rounded.Edit, contentDescription = null) },
                                onClick = {
                                    menuExpanded = false
                                    onRename()
                                }
                            )
                            if (episode.isDownloaded) {
                                DropdownMenuItem(
                                    text = { Text("Save Copy to Downloads") },
                                    leadingIcon = { Icon(Icons.Rounded.SaveAlt, contentDescription = null) },
                                    onClick = {
                                        menuExpanded = false
                                        onExportOffline()
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text("Delete Offline Audio") },
                                    leadingIcon = { Icon(Icons.Rounded.DeleteOutline, contentDescription = null) },
                                    onClick = {
                                        menuExpanded = false
                                        onRemoveDownload()
                                    }
                                )
                            }
                            DropdownMenuItem(
                                text = { Text("Remove from Playlist") },
                                leadingIcon = {
                                    Icon(
                                        Icons.Rounded.Delete,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.error
                                    )
                                },
                                onClick = {
                                    menuExpanded = false
                                    onDelete()
                                }
                            )
                        }
                    }
                }
            }

            if (episode.downloadStatus == DownloadStatus.DOWNLOADING) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    LinearProgressIndicator(
                        progress = { (episode.downloadProgress / 100f).coerceIn(0.02f, 1f) },
                        modifier = Modifier
                            .weight(1f)
                            .height(3.dp)
                            .clip(RoundedCornerShape(2.dp))
                    )
                    Text(
                        text = "${episode.downloadProgress}%",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            } else if (episode.downloadStatus == DownloadStatus.PAUSED) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    LinearProgressIndicator(
                        progress = { (episode.downloadProgress / 100f).coerceIn(0.02f, 1f) },
                        modifier = Modifier
                            .weight(1f)
                            .height(3.dp)
                            .clip(RoundedCornerShape(2.dp)),
                        color = MaterialTheme.colorScheme.tertiary,
                        trackColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)
                    )
                    Text(
                        text = "Paused ${episode.downloadProgress}%",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.tertiary
                    )
                }
            } else if (episode.downloadStatus == DownloadStatus.QUEUED) {
                Text(
                    text = "Queued for offline download...",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary
                )
            } else if (episode.downloadStatus == DownloadStatus.FAILED) {
                Text(
                    text = "Download failed: ${episode.downloadError ?: "Tap red icon to retry"}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.error,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }

            if (episode.lastPositionMs > 0L || episode.isCompleted) {
                LinearProgressIndicator(
                    progress = { episode.progressFraction },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(3.dp)
                        .clip(RoundedCornerShape(2.dp)),
                    color = if (episode.isCompleted) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.primary,
                    trackColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)
                )
            }
        }
    }
}
