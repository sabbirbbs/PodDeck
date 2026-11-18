package com.example.data.model

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

enum class DownloadStatus {
    NONE,
    QUEUED,
    DOWNLOADING,
    PAUSED,
    COMPLETED,
    FAILED
}

@Entity(
    tableName = "episodes",
    foreignKeys = [
        ForeignKey(
            entity = PlaylistEntity::class,
            parentColumns = ["id"],
            childColumns = ["playlistId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["playlistId"]),
        Index(value = ["youtubeVideoId"])
    ]
)
data class EpisodeEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val playlistId: Long,
    val youtubeVideoId: String,
    val videoUrl: String,
    val title: String,
    val uploader: String = "",
    val thumbnailUrl: String? = null,
    val durationMs: Long = 0L,
    val lastPositionMs: Long = 0L,
    val isCompleted: Boolean = false,
    val sortOrder: Int = 0,
    val downloadStatus: DownloadStatus = DownloadStatus.NONE,
    val downloadProgress: Int = 0,
    val localFilePath: String? = null,
    val fileSizeBytes: Long = 0L,
    val downloadError: String? = null,
    val lastPlayedAt: Long? = null,
    val addedAt: Long = System.currentTimeMillis()
) {
    val isDownloaded: Boolean
        get() = downloadStatus == DownloadStatus.COMPLETED && !localFilePath.isNullOrBlank()

    val effectiveListenedMs: Long
        get() = when {
            durationMs <= 0L -> 0L
            isCompleted -> durationMs
            else -> lastPositionMs.coerceIn(0L, durationMs)
        }

    val progressFraction: Float
        get() = when {
            isCompleted -> 1f
            durationMs <= 0L -> 0f
            else -> (lastPositionMs.toFloat() / durationMs.toFloat()).coerceIn(0f, 1f)
        }
}
