package com.example.videodownloader

import android.app.Application
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Custom Application class that initialises youtubedl-android + FFmpeg
 * once at process start.
 */
class VideoDownloaderApp : Application() {
    override fun onCreate() {
        super.onCreate()
        DownloadRepository.init(this)
        NotificationHelper.createNotificationChannel(this)
        TelemetryHelper.checkAndReportInstall(this)
        CoroutineScope(Dispatchers.IO).launch {
            try {
                // Initialise bundled YoutubeDL and FFmpeg native binaries immediately
                YoutubeDLUtils.init(this@VideoDownloaderApp)
            } catch (e: Exception) {
                Log.e("VideoDownloaderApp", "Failed to initialise YoutubeDL", e)
            }
        }
    }
}
