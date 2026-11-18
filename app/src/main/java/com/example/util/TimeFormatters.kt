package com.example.util

import java.util.Locale

object TimeFormatters {

    fun formatDurationTimestamp(durationMs: Long): String {
        if (durationMs <= 0L) return "0:00"
        val totalSeconds = durationMs / 1000L
        val hours = totalSeconds / 3600L
        val minutes = (totalSeconds % 3600L) / 60L
        val seconds = totalSeconds % 60L
        return if (hours > 0L) {
            String.format(Locale.US, "%d:%02d:%02d", hours, minutes, seconds)
        } else {
            String.format(Locale.US, "%d:%02d", minutes, seconds)
        }
    }

    fun formatHoursMinutes(durationMs: Long): String {
        if (durationMs <= 0L) return "0m"
        val totalMinutes = durationMs / 60_000L
        val hours = totalMinutes / 60L
        val minutes = totalMinutes % 60L
        return when {
            hours > 0L && minutes > 0L -> "${hours}h ${minutes}m"
            hours > 0L -> "${hours}h"
            totalMinutes > 0L -> "${minutes}m"
            else -> "${(durationMs / 1000L).coerceAtLeast(1L)}s"
        }
    }

    fun formatFileSize(bytes: Long): String {
        if (bytes <= 0L) return "0 MB"
        val kb = bytes / 1024.0
        val mb = kb / 1024.0
        val gb = mb / 1024.0
        return when {
            gb >= 1.0 -> String.format(Locale.US, "%.2f GB", gb)
            mb >= 1.0 -> String.format(Locale.US, "%.1f MB", mb)
            else -> String.format(Locale.US, "%.0f KB", kb.coerceAtLeast(1.0))
        }
    }

    fun formatRelativeSyncTime(timestampMs: Long, nowMs: Long = System.currentTimeMillis()): String {
        if (timestampMs <= 0L) return "Never synced"
        val diffSec = ((nowMs - timestampMs) / 1000L).coerceAtLeast(0L)
        return when {
            diffSec < 60L -> "Synced just now"
            diffSec < 3600L -> "Synced ${diffSec / 60L}m ago"
            diffSec < 86_400L -> "Synced ${diffSec / 3600L}h ago"
            else -> "Synced ${diffSec / 86_400L}d ago"
        }
    }
}
