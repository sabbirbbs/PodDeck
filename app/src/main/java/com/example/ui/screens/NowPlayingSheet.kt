package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Bedtime
import androidx.compose.material.icons.rounded.BookmarkAdd
import androidx.compose.material.icons.rounded.Bookmarks
import androidx.compose.material.icons.rounded.CloudDownload
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Forward30
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.OfflinePin
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Replay10
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.model.BookmarkEntity
import com.example.data.model.DownloadStatus
import com.example.data.model.EpisodeEntity
import com.example.player.PlaybackUiState
import com.example.ui.components.PodcastArtwork
import com.example.util.TimeFormatters

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun NowPlayingSheet(
    playbackState: PlaybackUiState,
    bookmarks: List<BookmarkEntity>,
    onDismiss: () -> Unit,
    onTogglePlayPause: () -> Unit,
    onSeekTo: (Long) -> Unit,
    onSkipBackward: () -> Unit,
    onSkipForward: () -> Unit,
    onSkipPrevious: () -> Unit,
    onSkipNext: () -> Unit,
    onSpeedChange: (Float) -> Unit,
    onToggleVolumeBoost: () -> Unit,
    onSleepTimerMinutes: (Int?) -> Unit,
    onSleepAtEndOfEpisode: () -> Unit,
    onDownloadEpisode: (EpisodeEntity) -> Unit,
    onPlayQueueItem: (EpisodeEntity) -> Unit,
    onAddBookmark: (Long, Long, String) -> Unit,
    onDeleteBookmark: (Long) -> Unit
) {
    val episode = playbackState.currentEpisode ?: return
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    var showSpeedMenu by remember { mutableStateOf(false) }
    var showSleepMenu by remember { mutableStateOf(false) }
    var showAddBookmarkDialog by remember { mutableStateOf(false) }

    var isSeeking by remember { mutableStateOf(false) }
    var seekSliderFraction by remember { mutableFloatStateOf(0f) }

    val durationMs = playbackState.durationMs.coerceAtLeast(1L)
    val liveFraction = (playbackState.positionMs.toFloat() / durationMs.toFloat()).coerceIn(0f, 1f)
    val displayedFraction = if (isSeeking) seekSliderFraction else liveFraction
    val displayedPositionMs = if (isSeeking) {
        (seekSliderFraction * durationMs).toLong()
    } else {
        playbackState.positionMs
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        dragHandle = null,
        containerColor = MaterialTheme.colorScheme.background
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.25f),
                            MaterialTheme.colorScheme.background
                        )
                    )
                )
                .statusBarsPadding()
                .navigationBarsPadding(),
            contentAlignment = Alignment.TopCenter
        ) {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .widthIn(max = 600.dp)
                    .padding(horizontal = 20.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                item {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(
                            onClick = onDismiss,
                            modifier = Modifier
                                .minimumInteractiveComponentSize()
                                .testTag("now_playing_collapse")
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.KeyboardArrowDown,
                                contentDescription = "Collapse player",
                                modifier = Modifier.size(30.dp)
                            )
                        }
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.weight(1f)
                        ) {
                            Text(
                                text = playbackState.currentPlaylist?.title ?: "NOW PLAYING",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = if (playbackState.isOfflineSource) "OFFLINE AUDIO" else "STREAMING",
                                style = MaterialTheme.typography.labelSmall,
                                color = if (playbackState.isOfflineSource) {
                                    MaterialTheme.colorScheme.secondary
                                } else {
                                    MaterialTheme.colorScheme.primary
                                }
                            )
                        }
                        if (episode.downloadStatus == DownloadStatus.COMPLETED) {
                            Icon(
                                imageVector = Icons.Rounded.OfflinePin,
                                contentDescription = "Offline ready",
                                tint = MaterialTheme.colorScheme.secondary,
                                modifier = Modifier.padding(12.dp)
                            )
                        } else {
                            IconButton(
                                onClick = { onDownloadEpisode(episode) },
                                modifier = Modifier.minimumInteractiveComponentSize()
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.CloudDownload,
                                    contentDescription = "Download offline",
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    }
                }

                item {
                    PodcastArtwork(
                        imageUrl = episode.thumbnailUrl ?: playbackState.currentPlaylist?.thumbnailUrl,
                        contentDescription = episode.title,
                        modifier = Modifier
                            .fillMaxWidth(0.75f)
                            .aspectRatio(1f),
                        shape = RoundedCornerShape(24.dp)
                    )
                }

                item {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = episode.title,
                            style = MaterialTheme.typography.headlineSmall,
                            textAlign = TextAlign.Center,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                        if (episode.uploader.isNotBlank()) {
                            Text(
                                text = episode.uploader,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }

                item {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        Slider(
                            value = displayedFraction,
                            onValueChange = {
                                isSeeking = true
                                seekSliderFraction = it
                            },
                            onValueChangeFinished = {
                                onSeekTo((seekSliderFraction * durationMs).toLong())
                                isSeeking = false
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("now_playing_slider")
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = TimeFormatters.formatDurationTimestamp(displayedPositionMs),
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            val remaining = (durationMs - displayedPositionMs).coerceAtLeast(0L)
                            Text(
                                text = "-${TimeFormatters.formatDurationTimestamp(remaining)}",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(
                            onClick = onSkipPrevious,
                            modifier = Modifier
                                .minimumInteractiveComponentSize()
                                .testTag("now_playing_prev")
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.SkipPrevious,
                                contentDescription = "Previous episode",
                                modifier = Modifier.size(30.dp)
                            )
                        }
                        IconButton(
                            onClick = onSkipBackward,
                            modifier = Modifier
                                .minimumInteractiveComponentSize()
                                .testTag("now_playing_rewind")
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Replay10,
                                contentDescription = "Skip back 15 seconds",
                                modifier = Modifier.size(32.dp)
                            )
                        }
                        Box(
                            modifier = Modifier
                                .size(72.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.primary)
                                .clickable(onClick = onTogglePlayPause)
                                .testTag("now_playing_play_pause"),
                            contentAlignment = Alignment.Center
                        ) {
                            if (playbackState.isBuffering || playbackState.isResolvingStream) {
                                CircularProgressIndicator(
                                    color = MaterialTheme.colorScheme.onPrimary,
                                    modifier = Modifier.size(30.dp),
                                    strokeWidth = 3.dp
                                )
                            } else {
                                Icon(
                                    imageVector = if (playbackState.isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                                    contentDescription = if (playbackState.isPlaying) "Pause" else "Play",
                                    tint = MaterialTheme.colorScheme.onPrimary,
                                    modifier = Modifier.size(38.dp)
                                )
                            }
                        }
                        IconButton(
                            onClick = onSkipForward,
                            modifier = Modifier
                                .minimumInteractiveComponentSize()
                                .testTag("now_playing_forward")
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Forward30,
                                contentDescription = "Skip forward 30 seconds",
                                modifier = Modifier.size(32.dp)
                            )
                        }
                        IconButton(
                            onClick = onSkipNext,
                            modifier = Modifier
                                .minimumInteractiveComponentSize()
                                .testTag("now_playing_next")
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.SkipNext,
                                contentDescription = "Next episode",
                                modifier = Modifier.size(30.dp)
                            )
                        }
                    }
                }

                // Speed, Volume Boost, Sleep Timer, Bookmark
                item {
                    FlowRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Box {
                            AssistChip(
                                onClick = { showSpeedMenu = true },
                                leadingIcon = {
                                    Icon(
                                        Icons.Rounded.Speed,
                                        contentDescription = null,
                                        modifier = Modifier.size(18.dp)
                                    )
                                },
                                label = { Text("${playbackState.playbackSpeed}x") },
                                modifier = Modifier.testTag("speed_chip")
                            )
                            DropdownMenu(
                                expanded = showSpeedMenu,
                                onDismissRequest = { showSpeedMenu = false }
                            ) {
                                listOf(0.5f, 0.75f, 1.0f, 1.25f, 1.5f, 1.75f, 2.0f, 2.5f).forEach { speed ->
                                    DropdownMenuItem(
                                        text = { Text("${speed}x") },
                                        onClick = {
                                            showSpeedMenu = false
                                            onSpeedChange(speed)
                                        }
                                    )
                                }
                            }
                        }

                        AssistChip(
                            onClick = onToggleVolumeBoost,
                            leadingIcon = {
                                Icon(
                                    Icons.Rounded.GraphicEq,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp)
                                )
                            },
                            label = { Text(if (playbackState.volumeBoostEnabled) "Boost On" else "Boost") },
                            colors = if (playbackState.volumeBoostEnabled) {
                                AssistChipDefaults.assistChipColors(
                                    containerColor = MaterialTheme.colorScheme.secondaryContainer,
                                    labelColor = MaterialTheme.colorScheme.onSecondaryContainer,
                                    leadingIconContentColor = MaterialTheme.colorScheme.onSecondaryContainer
                                )
                            } else {
                                AssistChipDefaults.assistChipColors()
                            },
                            modifier = Modifier.testTag("volume_boost_chip")
                        )

                        Box {
                            val sleepLabel = when {
                                playbackState.sleepAtEndOfEpisode -> "End of Ep"
                                playbackState.sleepTimerRemainingMs != null ->
                                    TimeFormatters.formatDurationTimestamp(playbackState.sleepTimerRemainingMs)
                                else -> "Sleep"
                            }
                            AssistChip(
                                onClick = { showSleepMenu = true },
                                leadingIcon = {
                                    Icon(
                                        Icons.Rounded.Bedtime,
                                        contentDescription = null,
                                        modifier = Modifier.size(18.dp)
                                    )
                                },
                                label = { Text(sleepLabel) },
                                modifier = Modifier.testTag("sleep_timer_chip")
                            )
                            DropdownMenu(
                                expanded = showSleepMenu,
                                onDismissRequest = { showSleepMenu = false }
                            ) {
                                DropdownMenuItem(
                                    text = { Text("Off") },
                                    onClick = {
                                        showSleepMenu = false
                                        onSleepTimerMinutes(null)
                                    }
                                )
                                listOf(15, 30, 45, 60).forEach { mins ->
                                    DropdownMenuItem(
                                        text = { Text("$mins minutes") },
                                        onClick = {
                                            showSleepMenu = false
                                            onSleepTimerMinutes(mins)
                                        }
                                    )
                                }
                                DropdownMenuItem(
                                    text = { Text("End of episode") },
                                    onClick = {
                                        showSleepMenu = false
                                        onSleepAtEndOfEpisode()
                                    }
                                )
                            }
                        }

                        AssistChip(
                            onClick = { showAddBookmarkDialog = true },
                            leadingIcon = {
                                Icon(
                                    Icons.Rounded.BookmarkAdd,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp)
                                )
                            },
                            label = { Text("Bookmark") },
                            modifier = Modifier.testTag("add_bookmark_chip")
                        )
                    }
                }

                // Saved Timestamp Bookmarks
                if (bookmarks.isNotEmpty()) {
                    item {
                        HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Bookmarks,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(16.dp)
                            )
                            Text(
                                text = "Timestamp Bookmarks (${bookmarks.size})",
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    items(bookmarks, key = { "bm_${it.id}" }) { bm ->
                        Surface(
                            shape = RoundedCornerShape(14.dp),
                            color = MaterialTheme.colorScheme.surface,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(14.dp))
                                .clickable { onSeekTo(bm.positionMs) }
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = TimeFormatters.formatDurationTimestamp(bm.positionMs),
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Spacer(modifier = Modifier.width(12.dp))
                                Text(
                                    text = bm.note.ifBlank { "Saved moment" },
                                    style = MaterialTheme.typography.bodyMedium,
                                    modifier = Modifier.weight(1f),
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis
                                )
                                IconButton(
                                    onClick = { onDeleteBookmark(bm.id) },
                                    modifier = Modifier.minimumInteractiveComponentSize()
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.DeleteOutline,
                                        contentDescription = "Delete bookmark",
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                }

                // Up Next Queue
                if (playbackState.queue.size > 1) {
                    item {
                        HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                        Text(
                            text = "Up Next in Queue (${playbackState.queue.size})",
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                    items(playbackState.queue, key = { "q_${it.id}" }) { qItem ->
                        val active = qItem.id == episode.id
                        Surface(
                            shape = RoundedCornerShape(14.dp),
                            color = if (active) {
                                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f)
                            } else {
                                MaterialTheme.colorScheme.surface
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(14.dp))
                                .clickable { onPlayQueueItem(qItem) }
                        ) {
                            Row(
                                modifier = Modifier.padding(10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                PodcastArtwork(
                                    imageUrl = qItem.thumbnailUrl,
                                    contentDescription = qItem.title,
                                    modifier = Modifier.size(42.dp),
                                    shape = RoundedCornerShape(8.dp)
                                )
                                Spacer(modifier = Modifier.width(10.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = qItem.title,
                                        style = MaterialTheme.typography.bodyMedium,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = TimeFormatters.formatDurationTimestamp(qItem.durationMs),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                if (qItem.isDownloaded) {
                                    Icon(
                                        imageVector = Icons.Rounded.OfflinePin,
                                        contentDescription = "Offline ready",
                                        tint = MaterialTheme.colorScheme.secondary,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }
                        }
                    }
                }

                item { Spacer(modifier = Modifier.height(24.dp)) }
            }
        }
    }

    if (showAddBookmarkDialog) {
        AddBookmarkDialog(
            timestampMs = playbackState.positionMs,
            onDismiss = { showAddBookmarkDialog = false },
            onSave = { note ->
                onAddBookmark(episode.id, playbackState.positionMs, note)
                showAddBookmarkDialog = false
            }
        )
    }
}

@Composable
private fun AddBookmarkDialog(
    timestampMs: Long,
    onDismiss: () -> Unit,
    onSave: (String) -> Unit
) {
    var note by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "Bookmark at ${TimeFormatters.formatDurationTimestamp(timestampMs)}",
                style = MaterialTheme.typography.titleLarge
            )
        },
        text = {
            OutlinedTextField(
                value = note,
                onValueChange = { note = it },
                label = { Text("Note (optional)") },
                placeholder = { Text("e.g., Key concept / example") },
                maxLines = 3,
                modifier = Modifier.fillMaxWidth()
            )
        },
        confirmButton = {
            Button(onClick = { onSave(note) }) {
                Text("Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}
