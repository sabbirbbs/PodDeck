package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Headphones
import androidx.compose.material.icons.rounded.Link
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.OfflinePin
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.PlaylistAdd
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.data.model.EpisodeWithPlaylist
import com.example.data.model.PlaylistEntity
import com.example.data.model.PlaylistWithEpisodes
import com.example.ui.components.ConfirmDeleteDialog
import com.example.ui.components.CreateCustomPlaylistDialog
import com.example.ui.components.EditPlaylistDialog
import com.example.ui.components.ImportPlaylistDialog
import com.example.ui.components.PodcastArtwork
import com.example.ui.viewmodel.LibraryFilter
import com.example.util.TimeFormatters

@Composable
fun LibraryScreen(
    playlists: List<PlaylistWithEpisodes>,
    lastPlayed: EpisodeWithPlaylist?,
    isImporting: Boolean,
    bottomContentPadding: DpPadding = DpPadding(bottom = 96.dp),
    onPlaylistClick: (Long) -> Unit,
    onImportPlaylist: (String, (Long) -> Unit) -> Unit,
    onCreateCustomPlaylist: (String, String, (Long) -> Unit) -> Unit,
    onSyncPlaylist: (Long) -> Unit,
    onDownloadAll: (PlaylistWithEpisodes) -> Unit,
    onEditPlaylist: (Long, String, String) -> Unit,
    onDeletePlaylist: (Long) -> Unit,
    onResumeLastPlayed: () -> Unit,
    onDismissLastPlayed: (Long) -> Unit,
    modifier: Modifier = Modifier
) {
    var searchVisible by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    var selectedFilter by remember { mutableStateOf(LibraryFilter.ALL) }

    var showImportDialog by remember { mutableStateOf(false) }
    var showCreateCustomDialog by remember { mutableStateOf(false) }
    var showAddMenu by remember { mutableStateOf(false) }
    var playlistToEdit by remember { mutableStateOf<PlaylistEntity?>(null) }
    var playlistToDelete by remember { mutableStateOf<PlaylistEntity?>(null) }

    val filteredPlaylists = remember(playlists, searchQuery, selectedFilter) {
        playlists.filter { item ->
            val matchesQuery = searchQuery.isBlank() ||
                item.playlist.title.contains(searchQuery, ignoreCase = true) ||
                item.playlist.author.contains(searchQuery, ignoreCase = true)
            val matchesFilter = when (selectedFilter) {
                LibraryFilter.ALL -> true
                LibraryFilter.IN_PROGRESS -> item.listenedDurationMs > 0L && item.completedCount < item.episodeCount
                LibraryFilter.OFFLINE -> item.downloadedCount > 0
                LibraryFilter.COMPLETED -> item.episodeCount > 0 && item.completedCount == item.episodeCount
            }
            matchesQuery && matchesFilter
        }
    }

    val totalLibraryHours = remember(playlists) {
        playlists.sumOf { it.totalDurationMs }
    }
    val totalOfflineEpisodes = remember(playlists) {
        playlists.sumOf { it.downloadedCount }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
        contentAlignment = Alignment.TopCenter
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .widthIn(max = 720.dp),
            contentPadding = PaddingValues(
                start = 16.dp,
                end = 16.dp,
                top = 12.dp,
                bottom = bottomContentPadding.bottom
            ),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Top Brand Header
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "PodDeck",
                            style = MaterialTheme.typography.headlineMedium,
                            color = MaterialTheme.colorScheme.onBackground
                        )
                        Text(
                            text = if (playlists.isEmpty()) {
                                "Turn YouTube playlists into offline podcasts"
                            } else {
                                "${playlists.size} playlists • ${TimeFormatters.formatHoursMinutes(totalLibraryHours)} total • $totalOfflineEpisodes offline"
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(
                            onClick = {
                                searchVisible = !searchVisible
                                if (!searchVisible) searchQuery = ""
                            },
                            modifier = Modifier
                                .minimumInteractiveComponentSize()
                                .testTag("toggle_search_button")
                        ) {
                            Icon(
                                imageVector = if (searchVisible) Icons.Rounded.Close else Icons.Rounded.Search,
                                contentDescription = "Search playlists"
                            )
                        }
                        Box {
                            IconButton(
                                onClick = { showAddMenu = true },
                                modifier = Modifier
                                    .minimumInteractiveComponentSize()
                                    .testTag("header_add_menu_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Add,
                                    contentDescription = "Add playlist",
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            }
                            DropdownMenu(
                                expanded = showAddMenu,
                                onDismissRequest = { showAddMenu = false }
                            ) {
                                DropdownMenuItem(
                                    text = { Text("Import YouTube Playlist") },
                                    leadingIcon = { Icon(Icons.Rounded.Link, contentDescription = null) },
                                    onClick = {
                                        showAddMenu = false
                                        showImportDialog = true
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text("New Custom Playlist") },
                                    leadingIcon = { Icon(Icons.Rounded.PlaylistAdd, contentDescription = null) },
                                    onClick = {
                                        showAddMenu = false
                                        showCreateCustomDialog = true
                                    }
                                )
                            }
                        }
                    }
                }
            }

            // Collapsible Search Bar
            item {
                AnimatedVisibility(visible = searchVisible) {
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        placeholder = { Text("Search playlists or creators...") },
                        leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
                        singleLine = true,
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("library_search_input")
                    )
                }
            }

            // Continue Listening Hero Card
            if (lastPlayed != null && searchQuery.isBlank()) {
                item {
                    ContinueListeningCard(
                        item = lastPlayed,
                        onResume = onResumeLastPlayed,
                        onOpenPlaylist = {
                            lastPlayed.playlist?.id?.let(onPlaylistClick)
                        },
                        onDismiss = {
                            onDismissLastPlayed(lastPlayed.episode.id)
                        }
                    )
                }
            }

            // Filter Chips
            if (playlists.isNotEmpty()) {
                item {
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(LibraryFilter.entries) { filter ->
                            FilterChip(
                                selected = selectedFilter == filter,
                                onClick = { selectedFilter = filter },
                                label = { Text(filter.label) },
                                modifier = Modifier.testTag("filter_chip_${filter.name.lowercase()}")
                            )
                        }
                    }
                }
            }

            // Empty State
            if (playlists.isEmpty()) {
                item {
                    EmptyLibraryCard(
                        onImportClick = { showImportDialog = true },
                        onCreateCustomClick = { showCreateCustomDialog = true }
                    )
                }
            } else if (filteredPlaylists.isEmpty()) {
                item {
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(20.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                    ) {
                        Column(
                            modifier = Modifier.padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Headphones,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(32.dp)
                            )
                            Text(
                                text = "No playlists match your filter",
                                style = MaterialTheme.typography.titleMedium
                            )
                            Text(
                                text = "Try clearing the search query or switching filter tabs.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            } else {
                items(
                    items = filteredPlaylists,
                    key = { it.playlist.id }
                ) { playlistWithEpisodes ->
                    PlaylistCard(
                        item = playlistWithEpisodes,
                        onClick = { onPlaylistClick(playlistWithEpisodes.playlist.id) },
                        onSync = { onSyncPlaylist(playlistWithEpisodes.playlist.id) },
                        onDownloadAll = { onDownloadAll(playlistWithEpisodes) },
                        onEdit = { playlistToEdit = playlistWithEpisodes.playlist },
                        onDelete = { playlistToDelete = playlistWithEpisodes.playlist }
                    )
                }
            }
        }

        // Primary Floating Action Button for Quick YouTube Playlist Import
        FloatingActionButton(
            onClick = { showImportDialog = true },
            containerColor = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = 20.dp, bottom = bottomContentPadding.bottom + 12.dp)
                .testTag("import_playlist_fab")
        ) {
            Icon(
                imageVector = Icons.Rounded.Link,
                contentDescription = "Import YouTube Playlist"
            )
        }
    }

    if (showImportDialog) {
        ImportPlaylistDialog(
            isLoading = isImporting,
            onDismiss = { showImportDialog = false },
            onImport = { url ->
                onImportPlaylist(url) { newId ->
                    showImportDialog = false
                    onPlaylistClick(newId)
                }
            }
        )
    }

    if (showCreateCustomDialog) {
        CreateCustomPlaylistDialog(
            onDismiss = { showCreateCustomDialog = false },
            onCreate = { title, desc ->
                onCreateCustomPlaylist(title, desc) { newId ->
                    showCreateCustomDialog = false
                    onPlaylistClick(newId)
                }
            }
        )
    }

    playlistToEdit?.let { editing ->
        EditPlaylistDialog(
            initialTitle = editing.title,
            initialDescription = editing.description,
            onDismiss = { playlistToEdit = null },
            onSave = { newTitle, newDesc ->
                onEditPlaylist(editing.id, newTitle, newDesc)
                playlistToEdit = null
            }
        )
    }

    playlistToDelete?.let { deleting ->
        ConfirmDeleteDialog(
            title = "Delete Playlist?",
            message = "This will delete \"${deleting.title}\" and remove all downloaded offline audio files for this playlist.",
            onDismiss = { playlistToDelete = null },
            onConfirm = {
                onDeletePlaylist(deleting.id)
                playlistToDelete = null
            }
        )
    }
}

data class DpPadding(val bottom: androidx.compose.ui.unit.Dp)

@Composable
private fun ContinueListeningCard(
    item: EpisodeWithPlaylist,
    onResume: () -> Unit,
    onOpenPlaylist: () -> Unit,
    onDismiss: () -> Unit
) {
    val ep = item.episode
    val remaining = (ep.durationMs - ep.lastPositionMs).coerceAtLeast(0L)

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(22.dp))
            .background(
                Brush.linearGradient(
                    colors = listOf(
                        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.85f),
                        MaterialTheme.colorScheme.surfaceContainerHigh
                    )
                )
            )
            .clickable(onClick = onOpenPlaylist)
            .padding(start = 16.dp, end = 12.dp, top = 12.dp, bottom = 16.dp)
            .testTag("continue_listening_card")
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Headphones,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(16.dp)
                    )
                    Text(
                        text = "CONTINUE LISTENING",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    if (ep.isDownloaded) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.OfflinePin,
                                contentDescription = "Offline ready",
                                tint = MaterialTheme.colorScheme.secondary,
                                modifier = Modifier.size(14.dp)
                            )
                            Text(
                                text = "OFFLINE",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.secondary
                            )
                        }
                    }
                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("dismiss_continue_listening")
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Close,
                            contentDescription = "Remove from last played",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                PodcastArtwork(
                    imageUrl = ep.thumbnailUrl ?: item.playlist?.thumbnailUrl,
                    contentDescription = ep.title,
                    modifier = Modifier.size(64.dp),
                    shape = RoundedCornerShape(14.dp)
                )
                Spacer(modifier = Modifier.width(14.dp))
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = ep.title,
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = item.playlist?.title ?: ep.uploader,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = if (ep.isCompleted) {
                            "Completed"
                        } else if (remaining > 0L) {
                            "${TimeFormatters.formatDurationTimestamp(remaining)} left • at ${TimeFormatters.formatDurationTimestamp(ep.lastPositionMs)}"
                        } else {
                            TimeFormatters.formatDurationTimestamp(ep.durationMs)
                        },
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Spacer(modifier = Modifier.width(10.dp))
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary)
                        .clickable(onClick = onResume)
                        .testTag("continue_listening_play_button"),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Rounded.PlayArrow,
                        contentDescription = "Resume episode",
                        tint = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.size(28.dp)
                    )
                }
            }

            LinearProgressIndicator(
                progress = { ep.progressFraction },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(4.dp)
                    .clip(RoundedCornerShape(4.dp)),
                color = MaterialTheme.colorScheme.primary,
                trackColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.14f)
            )
        }
    }
}

@Composable
private fun PlaylistCard(
    item: PlaylistWithEpisodes,
    onClick: () -> Unit,
    onSync: () -> Unit,
    onDownloadAll: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    var menuExpanded by remember { mutableStateOf(false) }
    val pct = (item.progressFraction * 100).toInt().coerceIn(0, 100)

    ElevatedCard(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .clickable(onClick = onClick)
            .testTag("playlist_card_${item.playlist.id}"),
        shape = RoundedCornerShape(20.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                PodcastArtwork(
                    imageUrl = item.playlist.thumbnailUrl
                        ?: item.sortedEpisodes.firstOrNull()?.thumbnailUrl,
                    contentDescription = item.playlist.title,
                    modifier = Modifier.size(72.dp),
                    shape = RoundedCornerShape(14.dp)
                )
                Spacer(modifier = Modifier.width(14.dp))
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = item.playlist.title,
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (item.playlist.author.isNotBlank()) {
                        Text(
                            text = item.playlist.author,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(3.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Schedule,
                                contentDescription = null,
                                modifier = Modifier.size(13.dp),
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Text(
                                text = TimeFormatters.formatHoursMinutes(item.totalDurationMs),
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                        Text(
                            text = "•",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = "${item.episodeCount} ep",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        if (item.downloadedCount > 0) {
                            Text(
                                text = "• ${item.downloadedCount} offline",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.secondary
                            )
                        }
                    }
                }
                Box {
                    IconButton(
                        onClick = { menuExpanded = true },
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("playlist_menu_${item.playlist.id}")
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.MoreVert,
                            contentDescription = "Playlist options"
                        )
                    }
                    DropdownMenu(
                        expanded = menuExpanded,
                        onDismissRequest = { menuExpanded = false }
                    ) {
                        if (!item.playlist.isCustom) {
                            DropdownMenuItem(
                                text = { Text("Sync from YouTube") },
                                leadingIcon = { Icon(Icons.Rounded.Refresh, contentDescription = null) },
                                onClick = {
                                    menuExpanded = false
                                    onSync()
                                }
                            )
                        }
                        DropdownMenuItem(
                            text = { Text("Download All Offline") },
                            leadingIcon = { Icon(Icons.Rounded.Download, contentDescription = null) },
                            onClick = {
                                menuExpanded = false
                                onDownloadAll()
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Edit Details") },
                            leadingIcon = { Icon(Icons.Rounded.Edit, contentDescription = null) },
                            onClick = {
                                menuExpanded = false
                                onEdit()
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
                                menuExpanded = false
                                onDelete()
                            }
                        )
                    }
                }
            }

            // Progress Bar & Completion Summary
            Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                LinearProgressIndicator(
                    progress = { item.progressFraction },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(5.dp)
                        .clip(RoundedCornerShape(4.dp)),
                    color = if (pct == 100) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.primary,
                    trackColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = if (item.remainingDurationMs > 0L) {
                            "${TimeFormatters.formatHoursMinutes(item.listenedDurationMs)} done • ${
                                TimeFormatters.formatHoursMinutes(item.remainingDurationMs)
                            } left"
                        } else if (item.episodeCount > 0 && item.completedCount == item.episodeCount) {
                            "All ${item.episodeCount} episodes completed"
                        } else {
                            "Ready to start"
                        },
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        if (pct == 100 && item.episodeCount > 0) {
                            Icon(
                                imageVector = Icons.Rounded.CheckCircle,
                                contentDescription = "Completed",
                                tint = MaterialTheme.colorScheme.secondary,
                                modifier = Modifier.size(13.dp)
                            )
                        }
                        Text(
                            text = "$pct%",
                            style = MaterialTheme.typography.labelMedium,
                            color = if (pct == 100) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun EmptyLibraryCard(
    onImportClick: () -> Unit,
    onCreateCustomClick: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("empty_library_card"),
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 2.dp
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Image(
                painter = painterResource(id = R.drawable.img_podcast_empty),
                contentDescription = "PodDeck headphones and playlist illustration",
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(180.dp)
                    .clip(RoundedCornerShape(18.dp))
            )
            Text(
                text = "Your Offline Podcast Deck",
                style = MaterialTheme.typography.headlineSmall
            )
            Text(
                text = "Paste any YouTube playlist link to automatically list every lecture or talk, track total hours & completion progress, download audio for offline screen-off playback, and resume right where you left off.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Button(
                    onClick = onImportClick,
                    modifier = Modifier
                        .weight(1f)
                        .testTag("empty_import_button")
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Link,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Import Playlist")
                }
                OutlinedButton(
                    onClick = onCreateCustomClick,
                    modifier = Modifier
                        .weight(1f)
                        .testTag("empty_create_custom_button")
                ) {
                    Icon(
                        imageVector = Icons.Rounded.PlaylistAdd,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Custom List")
                }
            }
        }
    }
}
