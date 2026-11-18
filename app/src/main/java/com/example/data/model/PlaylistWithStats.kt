package com.example.data.model

import androidx.room.Embedded
import androidx.room.Relation

data class PlaylistWithEpisodes(
    @Embedded val playlist: PlaylistEntity,
    @Relation(
        parentColumn = "id",
        entityColumn = "playlistId"
    )
    val episodes: List<EpisodeEntity>
) {
    val sortedEpisodes: List<EpisodeEntity>
        get() = episodes.sortedBy { it.sortOrder }

    val episodeCount: Int
        get() = episodes.size

    val downloadedCount: Int
        get() = episodes.count { it.isDownloaded }

    val completedCount: Int
        get() = episodes.count { it.isCompleted }

    val totalDurationMs: Long
        get() = episodes.sumOf { it.durationMs.coerceAtLeast(0L) }

    val listenedDurationMs: Long
        get() = episodes.sumOf { it.effectiveListenedMs }

    val remainingDurationMs: Long
        get() = (totalDurationMs - listenedDurationMs).coerceAtLeast(0L)

    val progressFraction: Float
        get() = if (totalDurationMs > 0L) {
            (listenedDurationMs.toFloat() / totalDurationMs.toFloat()).coerceIn(0f, 1f)
        } else if (episodeCount > 0) {
            completedCount.toFloat() / episodeCount.toFloat()
        } else {
            0f
        }

    val totalStorageBytes: Long
        get() = episodes.sumOf { if (it.isDownloaded) it.fileSizeBytes else 0L }
}

data class EpisodeWithPlaylist(
    @Embedded val episode: EpisodeEntity,
    @Relation(
        parentColumn = "playlistId",
        entityColumn = "id"
    )
    val playlist: PlaylistEntity?
)
