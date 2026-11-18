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
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.OfflinePin
import androidx.compose.material.icons.rounded.PlayArrow
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
import com.example.data.model.EpisodeEntity
import com.example.data.model.EpisodeWithPlaylist
import com.example.data.model.PlaylistEntity
import com.example.data.model.PlaylistWithEpisodes
import com.example.ui.components.ConfirmDeleteDialog
import com.example.ui.components.PodcastArtwork
import com.example.util.TimeFormatters

@Composable
fun HistoryScreen(
    playlists: List<PlaylistWithEpisodes>,
    recentlyPlayed: List<EpisodeWithPlaylist>,
    bottomContentPadding: Dp,
    onResumeEpisode: (EpisodeEntity, PlaylistEntity?) -> Unit,
    onOpenPlaylist: (Long) -> Unit,
    onRemoveHistoryItem: (Long) -> Unit,
    onClearAllHistory: () -> Unit,
    modifier: Modifier = Modifier
) {
    var showClearDialog by remember { mutableStateOf(false) }

    val totalListenedMs = remember(playlists) { playlists.sumOf { it.listenedDurationMs } }
    val totalCompleted = remember(playlists) { playlists.sumOf { it.completedCount } }
    val totalEpisodes = remember(playlists) { playlists.sumOf { it.episodeCount } }

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
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Listening History",
                        style = MaterialTheme.typography.headlineMedium
                    )
                    Text(
                        text = "Resume any episode or remove items from recent history",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                if (recentlyPlayed.isNotEmpty()) {
                    OutlinedButton(
                        onClick = { showClearDialog = true },
                        modifier = Modifier.testTag("clear_all_history_button")
                    ) {
                        Text("Clear All")
                    }
                }
            }
        }

        if (playlists.isNotEmpty()) {
            item {
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = MaterialTheme.colorScheme.surface,
                    tonalElevation = 2.dp,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column {
                            Text(
                                text = "LISTENED",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = TimeFormatters.formatHoursMinutes(totalListenedMs),
                                style = MaterialTheme.typography.titleLarge,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                        Column {
                            Text(
                                text = "COMPLETED",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = "$totalCompleted / $totalEpisodes",
                                style = MaterialTheme.typography.titleLarge,
                                color = MaterialTheme.colorScheme.secondary
                            )
                        }
                        Column {
                            Text(
                                text = "PLAYLISTS",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = "${playlists.size}",
                                style = MaterialTheme.typography.titleLarge
                            )
                        }
                    }
                }
            }
        }

        if (recentlyPlayed.isEmpty()) {
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
                            imageVector = Icons.Rounded.History,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(36.dp)
                        )
                        Text(
                            text = "No playback history yet",
                            style = MaterialTheme.typography.titleMedium
                        )
                        Text(
                            text = "Episodes you play will appear here with saved resume positions.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        } else {
            items(recentlyPlayed, key = { it.episode.id }) { item ->
                val ep = item.episode
                val remaining = (ep.durationMs - ep.lastPositionMs).coerceAtLeast(0L)

                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.surface,
                    tonalElevation = 1.dp,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .clickable { onResumeEpisode(ep, item.playlist) }
                        .testTag("history_item_${ep.id}")
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            PodcastArtwork(
                                imageUrl = ep.thumbnailUrl ?: item.playlist?.thumbnailUrl,
                                contentDescription = ep.title,
                                modifier = Modifier.size(54.dp),
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
                                    text = item.playlist?.title ?: ep.uploader,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.primary,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.clickable {
                                        item.playlist?.id?.let(onOpenPlaylist)
                                    }
                                )
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    if (ep.isDownloaded) {
                                        Icon(
                                            imageVector = Icons.Rounded.OfflinePin,
                                            contentDescription = "Offline ready",
                                            tint = MaterialTheme.colorScheme.secondary,
                                            modifier = Modifier.size(13.dp)
                                        )
                                    }
                                    if (ep.isCompleted) {
                                        Icon(
                                            imageVector = Icons.Rounded.CheckCircle,
                                            contentDescription = "Completed",
                                            tint = MaterialTheme.colorScheme.secondary,
                                            modifier = Modifier.size(13.dp)
                                        )
                                        Text(
                                            text = "Completed",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.secondary
                                        )
                                    } else {
                                        Text(
                                            text = "At ${TimeFormatters.formatDurationTimestamp(ep.lastPositionMs)} • ${
                                                TimeFormatters.formatDurationTimestamp(remaining)
                                            } left",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }
                            IconButton(
                                onClick = { onResumeEpisode(ep, item.playlist) },
                                modifier = Modifier.minimumInteractiveComponentSize()
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.PlayArrow,
                                    contentDescription = "Resume episode",
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            }
                            IconButton(
                                onClick = { onRemoveHistoryItem(ep.id) },
                                modifier = Modifier
                                    .minimumInteractiveComponentSize()
                                    .testTag("remove_history_${ep.id}")
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Close,
                                    contentDescription = "Remove from recent history",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        LinearProgressIndicator(
                            progress = { ep.progressFraction },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(3.dp)
                                .clip(RoundedCornerShape(2.dp)),
                            color = if (ep.isCompleted) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.primary,
                            trackColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)
                        )
                    }
                }
            }
        }
    }

    if (showClearDialog) {
        ConfirmDeleteDialog(
            title = "Clear Recent History?",
            message = "This removes all episodes from Last Played & Listening History while keeping your playlists and offline files.",
            confirmLabel = "Clear All",
            onDismiss = { showClearDialog = false },
            onConfirm = {
                showClearDialog = false
                onClearAllHistory()
            }
        )
    }
}
