package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.OfflinePin
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.SaveAlt
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material.icons.rounded.Storage
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.data.model.DownloadStatus
import com.example.data.model.EpisodeEntity
import com.example.data.model.EpisodeWithPlaylist
import com.example.data.model.PlaylistEntity
import com.example.ui.components.ConfirmDeleteDialog
import com.example.ui.components.PodcastArtwork
import com.example.util.TimeFormatters

@Composable
fun OfflineScreen(
    downloads: List<EpisodeWithPlaylist>,
    bottomContentPadding: Dp,
    onPlayEpisode: (EpisodeEntity, PlaylistEntity?) -> Unit,
    onPauseDownload: (Long) -> Unit,
    onPauseAllDownloads: () -> Unit,
    onResumePausedDownloads: () -> Unit,
    onStopAllDownloads: () -> Unit,
    onCancelDownload: (Long) -> Unit,
    onRetryDownload: (EpisodeEntity) -> Unit,
    onDismissFailedDownload: (EpisodeEntity) -> Unit,
    onExportOfflineFile: (EpisodeEntity) -> Unit,
    onDeleteOfflineFile: (EpisodeEntity) -> Unit,
    onClearAllDownloads: () -> Unit,
    modifier: Modifier = Modifier
) {
    var showClearAllDialog by remember { mutableStateOf(false) }

    val completedDownloads = remember(downloads) {
        downloads.filter { it.episode.downloadStatus == DownloadStatus.COMPLETED }
    }
    val activeDownloads = remember(downloads) {
        downloads.filter {
            it.episode.downloadStatus == DownloadStatus.DOWNLOADING ||
                it.episode.downloadStatus == DownloadStatus.QUEUED ||
                it.episode.downloadStatus == DownloadStatus.PAUSED ||
                it.episode.downloadStatus == DownloadStatus.FAILED
        }
    }
    val downloadingOrQueuedCount = remember(activeDownloads) {
        activeDownloads.count {
            it.episode.downloadStatus == DownloadStatus.DOWNLOADING ||
                it.episode.downloadStatus == DownloadStatus.QUEUED
        }
    }
    val pausedCount = remember(activeDownloads) {
        activeDownloads.count { it.episode.downloadStatus == DownloadStatus.PAUSED }
    }
    val totalStorageBytes = remember(completedDownloads) {
        completedDownloads.sumOf { it.episode.fileSizeBytes }
    }
    val totalOfflineDurationMs = remember(completedDownloads) {
        completedDownloads.sumOf { it.episode.durationMs }
    }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .widthIn(max = 720.dp),
        contentPadding = PaddingValues(
            start = 16.dp,
            end = 16.dp,
            top = 12.dp,
            bottom = bottomContentPadding
        ),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "Offline Audio",
                        style = MaterialTheme.typography.headlineMedium
                    )
                    Text(
                        text = "${completedDownloads.size} saved • ${TimeFormatters.formatHoursMinutes(totalOfflineDurationMs)} offline",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                if (completedDownloads.isNotEmpty()) {
                    OutlinedButton(
                        onClick = { showClearAllDialog = true },
                        modifier = Modifier.testTag("clear_all_offline_button")
                    ) {
                        Text("Clear All")
                    }
                }
            }
        }

        // Storage Summary Card
        item {
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 2.dp,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Storage,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.secondary,
                        modifier = Modifier.size(32.dp)
                    )
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = TimeFormatters.formatFileSize(totalStorageBytes),
                            style = MaterialTheme.typography.titleLarge
                        )
                        Text(
                            text = "Stored privately in PodDeck app storage (no file permission needed). Tap the save icon on any track to export a copy to Downloads/PodDeck.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }

        if (activeDownloads.isNotEmpty()) {
            item {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "Download Queue",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                    if (downloadingOrQueuedCount > 0 || pausedCount > 0) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            if (downloadingOrQueuedCount > 0) {
                                FilledTonalButton(
                                    onClick = onPauseAllDownloads,
                                    modifier = Modifier
                                        .weight(1f)
                                        .testTag("offline_pause_all_button")
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.Pause,
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Pause All ($downloadingOrQueuedCount)")
                                }
                            }
                            if (pausedCount > 0) {
                                FilledTonalButton(
                                    onClick = onResumePausedDownloads,
                                    modifier = Modifier
                                        .weight(1f)
                                        .testTag("offline_resume_all_button")
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.PlayArrow,
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Resume ($pausedCount)")
                                }
                            }
                            OutlinedButton(
                                onClick = onStopAllDownloads,
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("offline_stop_all_button")
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
            items(activeDownloads, key = { "active_${it.episode.id}" }) { item ->
                val ep = item.episode
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.surface,
                    tonalElevation = 1.dp,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            PodcastArtwork(
                                imageUrl = ep.thumbnailUrl,
                                contentDescription = ep.title,
                                modifier = Modifier.size(48.dp),
                                shape = RoundedCornerShape(10.dp)
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = ep.title,
                                    style = MaterialTheme.typography.titleSmall,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = when (ep.downloadStatus) {
                                        DownloadStatus.QUEUED -> "Queued..."
                                        DownloadStatus.DOWNLOADING -> "Downloading ${ep.downloadProgress}%"
                                        DownloadStatus.PAUSED -> "Paused at ${ep.downloadProgress}%"
                                        DownloadStatus.FAILED -> ep.downloadError ?: "Download failed"
                                        else -> ""
                                    },
                                    style = MaterialTheme.typography.labelSmall,
                                    color = when (ep.downloadStatus) {
                                        DownloadStatus.FAILED -> MaterialTheme.colorScheme.error
                                        DownloadStatus.PAUSED -> MaterialTheme.colorScheme.tertiary
                                        else -> MaterialTheme.colorScheme.primary
                                    },
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            when (ep.downloadStatus) {
                                DownloadStatus.FAILED -> {
                                    IconButton(
                                        onClick = { onRetryDownload(ep) },
                                        modifier = Modifier.minimumInteractiveComponentSize()
                                    ) {
                                        Icon(Icons.Rounded.Refresh, contentDescription = "Retry download")
                                    }
                                    IconButton(
                                        onClick = { onDismissFailedDownload(ep) },
                                        modifier = Modifier.minimumInteractiveComponentSize()
                                    ) {
                                        Icon(Icons.Rounded.Close, contentDescription = "Dismiss failed download")
                                    }
                                }
                                DownloadStatus.PAUSED -> {
                                    IconButton(
                                        onClick = { onRetryDownload(ep) },
                                        modifier = Modifier.minimumInteractiveComponentSize()
                                    ) {
                                        Icon(
                                            Icons.Rounded.PlayArrow,
                                            contentDescription = "Resume download",
                                            tint = MaterialTheme.colorScheme.primary
                                        )
                                    }
                                    IconButton(
                                        onClick = { onCancelDownload(ep.id) },
                                        modifier = Modifier.minimumInteractiveComponentSize()
                                    ) {
                                        Icon(Icons.Rounded.Close, contentDescription = "Stop download")
                                    }
                                }
                                else -> {
                                    IconButton(
                                        onClick = { onPauseDownload(ep.id) },
                                        modifier = Modifier.minimumInteractiveComponentSize()
                                    ) {
                                        Icon(Icons.Rounded.Pause, contentDescription = "Pause download")
                                    }
                                    IconButton(
                                        onClick = { onCancelDownload(ep.id) },
                                        modifier = Modifier.minimumInteractiveComponentSize()
                                    ) {
                                        Icon(Icons.Rounded.Close, contentDescription = "Stop download")
                                    }
                                }
                            }
                        }
                        if (ep.downloadStatus == DownloadStatus.DOWNLOADING) {
                            LinearProgressIndicator(
                                progress = { (ep.downloadProgress / 100f).coerceIn(0f, 1f) },
                                modifier = Modifier.fillMaxWidth()
                            )
                        } else if (ep.downloadStatus == DownloadStatus.PAUSED) {
                            LinearProgressIndicator(
                                progress = { (ep.downloadProgress / 100f).coerceIn(0.02f, 1f) },
                                modifier = Modifier.fillMaxWidth(),
                                color = MaterialTheme.colorScheme.tertiary,
                                trackColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)
                            )
                        } else if (ep.downloadStatus == DownloadStatus.QUEUED) {
                            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                        }
                    }
                }
            }
        }

        if (completedDownloads.isEmpty() && activeDownloads.isEmpty()) {
            item {
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(28.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.OfflinePin,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.secondary,
                            modifier = Modifier.size(36.dp)
                        )
                        Text(
                            text = "No offline episodes yet",
                            style = MaterialTheme.typography.titleMedium
                        )
                        Text(
                            text = "Open any playlist and tap the download icon on an episode, select multiple episodes, or tap \"Download All\" to listen offline.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        } else if (completedDownloads.isNotEmpty()) {
            item {
                Text(
                    text = "Downloaded Episodes",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            items(completedDownloads, key = { "done_${it.episode.id}" }) { item ->
                val ep = item.episode
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.surface,
                    tonalElevation = 1.dp,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .clickable { onPlayEpisode(ep, item.playlist) }
                        .testTag("offline_episode_${ep.id}")
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        PodcastArtwork(
                            imageUrl = ep.thumbnailUrl ?: item.playlist?.thumbnailUrl,
                            contentDescription = ep.title,
                            modifier = Modifier.size(52.dp),
                            shape = RoundedCornerShape(12.dp)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(3.dp)
                        ) {
                            Text(
                                text = ep.title,
                                style = MaterialTheme.typography.titleSmall,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = "${item.playlist?.title ?: ep.uploader} • ${
                                    TimeFormatters.formatDurationTimestamp(ep.durationMs)
                                } • ${TimeFormatters.formatFileSize(ep.fileSizeBytes)}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        IconButton(
                            onClick = { onExportOfflineFile(ep) },
                            modifier = Modifier
                                .minimumInteractiveComponentSize()
                                .testTag("export_offline_file_${ep.id}")
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.SaveAlt,
                                contentDescription = "Save copy to Downloads",
                                tint = MaterialTheme.colorScheme.secondary
                            )
                        }
                        IconButton(
                            onClick = { onPlayEpisode(ep, item.playlist) },
                            modifier = Modifier.minimumInteractiveComponentSize()
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.PlayArrow,
                                contentDescription = "Play offline",
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                        IconButton(
                            onClick = { onDeleteOfflineFile(ep) },
                            modifier = Modifier
                                .minimumInteractiveComponentSize()
                                .testTag("delete_offline_file_${ep.id}")
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.DeleteOutline,
                                contentDescription = "Delete downloaded audio",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }
    }

    if (showClearAllDialog) {
        ConfirmDeleteDialog(
            title = "Clear All Offline Audio?",
            message = "This will delete all ${completedDownloads.size} downloaded audio files (${
                TimeFormatters.formatFileSize(totalStorageBytes)
            }) while keeping your playlists and listening progress.",
            confirmLabel = "Clear All",
            onDismiss = { showClearAllDialog = false },
            onConfirm = {
                showClearAllDialog = false
                onClearAllDownloads()
            }
        )
    }
}
