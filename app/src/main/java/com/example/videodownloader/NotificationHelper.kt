package com.example.videodownloader

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.work.ForegroundInfo

object NotificationHelper {
    const val CHANNEL_ID = "download_channel"
    const val NOTIFICATION_ID = 1001

    fun createNotificationChannel(context: Context) {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Video Downloads",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "Shows progress for video downloads"
            setShowBadge(false)
        }
        val manager = context.applicationContext
            .getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.createNotificationChannel(channel)
    }

    fun buildSummaryNotification(context: Context): android.app.Notification {
        createNotificationChannel(context)
        val items = DownloadRepository.downloadItems.value
        val activeItems = items.filter {
            (it.status == DownloadStatus.DOWNLOADING ||
                    it.status == DownloadStatus.MERGING ||
                    it.status == DownloadStatus.PROCESSING ||
                    it.status == DownloadStatus.QUEUED ||
                    it.status == DownloadStatus.PENDING)
        }

        val totalActive = activeItems.size

        if (totalActive == 0) {
            return NotificationCompat.Builder(context.applicationContext, CHANNEL_ID)
                .setContentTitle("Video Downloader")
                .setContentText("No active downloads")
                .setSmallIcon(android.R.drawable.stat_sys_download_done)
                .setOngoing(false)
                .setAutoCancel(true)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .build()
        }

        val downloadingCount = items.count { it.status == DownloadStatus.DOWNLOADING || it.status == DownloadStatus.MERGING }

        // Collapsed view (dropdown closed): shows total number of items being downloaded
        val collapsedTitle = "Video Downloader"
        val collapsedText = if (downloadingCount == 1) {
            val item = activeItems.firstOrNull { it.status == DownloadStatus.DOWNLOADING || it.status == DownloadStatus.MERGING } ?: activeItems.first()
            "${item.title}: ${item.progress}% • ${item.downloadSpeed}"
        } else {
            "$downloadingCount downloading ($totalActive in queue)"
        }

        // Expanded view (dropdown open): list all downloading/queued items in detail
        val inboxStyle = NotificationCompat.InboxStyle()
            .setBigContentTitle("Queue ($totalActive items)")

        activeItems.forEach { item ->
            val sizeInfo = if (item.fileSizeBytes > 0 && item.downloadedBytes > 0) {
                " (${formatFileSize(item.downloadedBytes)}/${formatFileSize(item.fileSizeBytes)})"
            } else if (item.fileSizeBytes > 0) {
                " (${formatFileSize(item.fileSizeBytes)})"
            } else ""

            val statusStr = when (item.status) {
                DownloadStatus.DOWNLOADING -> "${item.progress}%$sizeInfo • ${item.downloadSpeed}"
                DownloadStatus.MERGING -> "Merging chunks..."
                DownloadStatus.PROCESSING -> "Ready"
                DownloadStatus.PENDING -> "Pending"
                DownloadStatus.QUEUED -> "Queued"
                else -> ""
            }
            inboxStyle.addLine("• ${item.title} — $statusStr")
        }

        val avgProgress = activeItems.sumOf { it.progress } / totalActive

        val intent = Intent(context.applicationContext, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("SHOW_TAB", "queue")
        }

        val pendingIntent = PendingIntent.getActivity(
            context.applicationContext,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(context.applicationContext, CHANNEL_ID)
            .setContentTitle(collapsedTitle)
            .setContentText(collapsedText)
            .setSubText("$totalActive downloading")
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setProgress(100, avgProgress, false)
            .setStyle(inboxStyle)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOnlyAlertOnce(true)
            .build()
    }

    fun createForegroundInfo(context: Context): ForegroundInfo {
        val notification = buildSummaryNotification(context)
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ForegroundInfo(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
            )
        } else {
            ForegroundInfo(NOTIFICATION_ID, notification)
        }
    }

    fun updateNotification(context: Context) {
        val manager = context.applicationContext
            .getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.notify(NOTIFICATION_ID, buildSummaryNotification(context))
    }
}
