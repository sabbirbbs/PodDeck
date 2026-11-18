package com.example.util

import android.net.Uri

object YouTubeUrlParser {

    fun extractPlaylistId(input: String): String? {
        val trimmed = input.trim()
        if (trimmed.isEmpty()) return null

        // Bare playlist ID like PL..., UU..., OL..., RD...
        if (Regex("^[A-Za-z0-9_-]{12,64}$").matches(trimmed) && !trimmed.contains(".")) {
            return trimmed
        }

        return try {
            val uri = Uri.parse(if (trimmed.startsWith("http")) trimmed else "https://$trimmed")
            uri.getQueryParameter("list")?.takeIf { it.isNotBlank() }
        } catch (_: Exception) {
            val match = Regex("[?&]list=([A-Za-z0-9_-]+)").find(trimmed)
            match?.groupValues?.getOrNull(1)
        }
    }

    fun normalizePlaylistUrl(input: String): String? {
        val playlistId = extractPlaylistId(input) ?: return null
        return "https://www.youtube.com/playlist?list=$playlistId"
    }

    fun extractVideoId(input: String): String? {
        val trimmed = input.trim()
        if (trimmed.isEmpty()) return null
        if (Regex("^[A-Za-z0-9_-]{11}$").matches(trimmed)) {
            return trimmed
        }
        return try {
            val uri = Uri.parse(if (trimmed.startsWith("http")) trimmed else "https://$trimmed")
            val host = uri.host?.lowercase() ?: ""
            when {
                host.contains("youtu.be") -> uri.pathSegments.firstOrNull()?.take(11)
                uri.path?.startsWith("/shorts/") == true -> uri.pathSegments.getOrNull(1)?.take(11)
                uri.path?.startsWith("/live/") == true -> uri.pathSegments.getOrNull(1)?.take(11)
                else -> uri.getQueryParameter("v")?.take(11)
            }
        } catch (_: Exception) {
            null
        }
    }

    fun normalizeVideoUrl(input: String): String? {
        val videoId = extractVideoId(input) ?: return null
        return "https://www.youtube.com/watch?v=$videoId"
    }
}
