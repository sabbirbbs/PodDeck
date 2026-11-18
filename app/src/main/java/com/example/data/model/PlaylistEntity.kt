package com.example.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "playlists")
data class PlaylistEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val sourceUrl: String,
    val youtubePlaylistId: String? = null,
    val title: String,
    val author: String = "",
    val description: String = "",
    val thumbnailUrl: String? = null,
    val isCustom: Boolean = false,
    val playbackSpeed: Float = 1.0f,
    val createdAt: Long = System.currentTimeMillis(),
    val lastSyncedAt: Long? = null
)
