package com.example.download

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.example.MainActivity
import com.example.R

class DownloadForegroundService : Service() {

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        ensureChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
                return START_NOT_STICKY
            }
            else -> {
                val title = intent?.getStringExtra(EXTRA_TITLE) ?: "Downloading audio..."
                val progress = intent?.getIntExtra(EXTRA_PROGRESS, 0) ?: 0
                val activeCount = intent?.getIntExtra(EXTRA_ACTIVE_COUNT, 1) ?: 1
                val notification = buildNotification(title, progress, activeCount)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    startForeground(
                        NOTIFICATION_ID,
                        notification,
                        ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
                    )
                } else {
                    startForeground(NOTIFICATION_ID, notification)
                }
            }
        }
        return START_NOT_STICKY
    }

    private fun buildNotification(title: String, progress: Int, activeCount: Int): Notification {
        val openAppIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val contentText = if (activeCount > 1) {
            "$title (+${activeCount - 1} queued)"
        } else if (progress in 1..99) {
            "Downloading $progress%"
        } else {
            "Preparing offline audio..."
        }

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setContentTitle(title)
            .setContentText(contentText)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setContentIntent(openAppIntent)
            .setProgress(100, progress.coerceIn(0, 100), progress <= 0)
            .build()
    }

    private fun ensureChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val nm = getSystemService(NotificationManager::class.java) ?: return
            if (nm.getNotificationChannel(CHANNEL_ID) == null) {
                val channel = NotificationChannel(
                    CHANNEL_ID,
                    getString(R.string.notification_channel_downloads),
                    NotificationManager.IMPORTANCE_LOW
                ).apply {
                    description = "Shows progress when saving podcast episodes for offline playback"
                }
                nm.createNotificationChannel(channel)
            }
        }
    }

    companion object {
        private const val CHANNEL_ID = "poddeck_downloads"
        private const val NOTIFICATION_ID = 4102
        private const val ACTION_START_OR_UPDATE = "com.example.poddeck.DOWNLOAD_UPDATE"
        private const val ACTION_STOP = "com.example.poddeck.DOWNLOAD_STOP"
        private const val EXTRA_TITLE = "extra_title"
        private const val EXTRA_PROGRESS = "extra_progress"
        private const val EXTRA_ACTIVE_COUNT = "extra_active_count"

        fun updateProgress(context: Context, title: String, progress: Int, activeCount: Int) {
            runCatching {
                val intent = Intent(context, DownloadForegroundService::class.java).apply {
                    action = ACTION_START_OR_UPDATE
                    putExtra(EXTRA_TITLE, title)
                    putExtra(EXTRA_PROGRESS, progress)
                    putExtra(EXTRA_ACTIVE_COUNT, activeCount)
                }
                ContextCompat.startForegroundService(context, intent)
            }
        }

        fun stop(context: Context) {
            runCatching {
                val intent = Intent(context, DownloadForegroundService::class.java).apply {
                    action = ACTION_STOP
                }
                context.startService(intent)
            }
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
                ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
                PackageManager.PERMISSION_GRANTED
            ) {
                runCatching {
                    val nm = context.getSystemService(NotificationManager::class.java)
                    nm?.cancel(NOTIFICATION_ID)
                }
            }
        }
    }
}
