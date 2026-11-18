package com.example.ui.navigation

import kotlinx.serialization.Serializable

@Serializable
data object LibraryRoute

@Serializable
data object OfflineRoute

@Serializable
data object HistoryRoute

@Serializable
data class PlaylistDetailRoute(val playlistId: Long)
