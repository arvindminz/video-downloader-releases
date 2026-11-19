package com.example.videodownloader

import android.content.ContentValues
import android.content.Context
import android.os.Build
import android.os.Environment
import android.os.PowerManager
import android.os.StatFs
import android.provider.MediaStore
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.yausername.youtubedl_android.YoutubeDLRequest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.net.HttpURLConnection
import java.net.URL

/**
 * CoroutineWorker that:
 *  1. Acquires a Partial WakeLock to stay alive when screen is off.
 *  2. Promotes itself to a foreground service using NotificationHelper.
 *  3. Downloads using chunked parallel streams (-N 8) if file size > 300MB or unknown.
 *  4. Publishes real-time speed/progress to DownloadRepository for the Queue UI.
 *  5. Saves the final merged output file to MediaStore Downloads folder.
 */
class DownloadWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    companion object {
        private const val TAG = "DownloadWorker"
        const val KEY_DOWNLOAD_ID = "download_id"
        const val KEY_URL = "url"
        const val KEY_FORMAT_ID = "format_id"
        const val KEY_EXTENSION = "extension"
        const val KEY_TITLE = "title"
        const val KEY_IS_AUDIO_ONLY = "is_audio_only"
        const val KEY_IS_COMBINED = "is_combined"
        const val KEY_FILE_SIZE = "file_size"
        const val KEY_DURATION = "duration"

        private val SPEED_REGEX = Regex("""at\s+([\d.]+\s*[KMGT]?i?B/s)""", RegexOption.IGNORE_CASE)
        private val SIZE_REGEX_1 = Regex("""[\[\s]download[\]\s]+\s*([\d.]+)%\s+of\s+~?\s*([\d.]+)\s*([KMGTP]i?B)""", RegexOption.IGNORE_CASE)
        private val SIZE_REGEX_2 = Regex("""[\[\s]download[\]\s]+\s*([\d.]+)\s*([KMGTP]i?B)\s+of\s+~?\s*([\d.]+)\s*([KMGTP]i?B)""", RegexOption.IGNORE_CASE)
    }

    override suspend fun getForegroundInfo(): ForegroundInfo {
        NotificationHelper.createNotificationChannel(applicationContext)
        return NotificationHelper.createForegroundInfo(applicationContext)
    }

    override suspend fun doWork(): Result {
        val downloadId = inputData.getString(KEY_DOWNLOAD_ID) ?: id.toString()
        val url = inputData.getString(KEY_URL) ?: return Result.failure()
        val formatId = inputData.getString(KEY_FORMAT_ID) ?: return Result.failure()
        val extension = inputData.getString(KEY_EXTENSION) ?: "mp4"
        val title = inputData.getString(KEY_TITLE) ?: "video"
        val isAudioOnly = inputData.getBoolean(KEY_IS_AUDIO_ONLY, false)
        val isCombined = inputData.getBoolean(KEY_IS_COMBINED, false)
        val fileSizeBytes = inputData.getLong(KEY_FILE_SIZE, 0L)
        val durationSeconds = inputData.getLong(KEY_DURATION, 0L)

        DownloadRepository.init(applicationContext)
        NotificationHelper.createNotificationChannel(applicationContext)

        try {
            setForeground(NotificationHelper.createForegroundInfo(applicationContext))
        } catch (e: Exception) {
            Log.w(TAG, "Failed to set foreground info", e)
        }

        // ── Acquire WakeLock to prevent CPU sleep when screen is off ──
        val powerManager = applicationContext.getSystemService(Context.POWER_SERVICE) as PowerManager
        val wakeLock = powerManager.newWakeLock(
            PowerManager.PARTIAL_WAKE_LOCK,
            "VideoDownloader:DownloadWorkerWakeLock"
        )
        wakeLock.acquire(60 * 60 * 1000L) // 1 hour max timeout

        var lastPercent = -1
        var lastSpeed = ""

        return try {
            withContext(Dispatchers.IO) {
                DownloadRepository.updateProgress(
                    applicationContext,
                    downloadId,
                    0,
                    "Connecting...",
                    DownloadStatus.DOWNLOADING
                )

                // Format selector directly derived from chosen quality
                val formatSelector = formatId.ifBlank { "bestvideo+bestaudio/best" }

                // ── Storage Pre-Check ──
                val availableBytes = try {
                    val stat = StatFs(applicationContext.cacheDir.absolutePath)
                    stat.availableBlocksLong * stat.blockSizeLong
                } catch (_: Exception) {
                    Long.MAX_VALUE
                }

                val minRequiredBytes = if (fileSizeBytes > 0) (fileSizeBytes * 1.1).toLong() else 100L * 1024L * 1024L
                if (availableBytes < minRequiredBytes) {
                    val formattedAvail = formatFileSize(availableBytes)
                    val msg = "Storage almost full ($formattedAvail free). Free up space and tap Retry."
                    DownloadRepository.markFailed(applicationContext, downloadId, msg)
                    return@withContext Result.failure(workDataOf("error" to msg))
                }

                // Dedicated output folder per download ID (preserves .part files for pause/resume)
                val outputDir = File(applicationContext.cacheDir, "downloads/$downloadId").apply { mkdirs() }
                val outputTemplate = File(outputDir, "%(title)s.%(ext)s").absolutePath

                val isYouTube = url.contains("youtube.com", ignoreCase = true) || url.contains("youtu.be", ignoreCase = true)
                val isDirectFile = extension.equals("apk", ignoreCase = true) ||
                        url.lowercase().contains(".apk") ||
                        url.contains("drive.google.com", ignoreCase = true) ||
                        url.contains("github.com", ignoreCase = true)

                var extractedTitle: String? = null
                var lastStatus = DownloadStatus.DOWNLOADING
                var lastUpdateTime = 0L

                if (isDirectFile) {
                    val targetApkFile = File(outputDir, "${sanitizeFileName(title)}.apk")
                    val success = downloadDirectHttpFile(downloadId, url, targetApkFile, fileSizeBytes, title)
                    if (!success) {
                        val msg = "Direct APK download failed. Check URL or network."
                        DownloadRepository.markFailed(applicationContext, downloadId, msg)
                        return@withContext Result.failure()
                    }
                    extractedTitle = title
                } else {
                    val request = YoutubeDLRequest(url).apply {
                        addOption("--format", formatSelector)
                        addOption("--continue")
                        addOption("--no-playlist")
                        addOption("--no-warnings")
                        addOption("--no-check-certificates")
                        addOption("--no-write-comments")
                        addOption("--socket-timeout", "5")
                        addOption("--geo-bypass")
                        addOption("--merge-output-format", "mp4")

                        val currentItem = DownloadRepository.downloadItems.value.find { it.id == downloadId }
                        if (currentItem?.downloadSubtitles == true) {
                            addOption("--write-subs")
                            addOption("--sub-langs", "en,hi,all")
                        }

                        if (isYouTube) {
                            addOption("--extractor-args", "youtube:player_client=visionos,web")
                        }

                        // ── Chunking rule ──
                        val tenMinutesSec = 600L
                        val threeHundredMb = 300L * 1024L * 1024L

                        val isLessThan10Min = durationSeconds in 1 until tenMinutesSec
                        val isLargeMusic = isAudioOnly && fileSizeBytes > threeHundredMb

                        val userChunks = SettingsRepository.getChunkCount(applicationContext)
                        val useChunks = when {
                            isAudioOnly -> isLargeMusic
                            isLessThan10Min -> false
                            else -> true
                        }

                        if (useChunks) {
                            Log.d(TAG, "Enabling parallel fragment download ($userChunks concurrent streams)")
                            addOption("-N", userChunks.toString()) // User's chunk setting (default 4)
                        }

                        if (isAudioOnly) {
                            addOption("--extract-audio")
                            val audioFmt = when (extension.lowercase()) {
                                "m4a" -> "m4a"
                                "mp3" -> "mp3"
                                else  -> "best"
                            }
                            addOption("--audio-format", audioFmt)
                        }

                        addOption("--output", outputTemplate)
                    }

                    YoutubeDLUtils.execute(applicationContext, request, processId = downloadId) { progress, _, line ->
                        if (line.isNotBlank()) {
                            Log.d(TAG, "yt-dlp [$downloadId]: $line")
                        }
                        if (extractedTitle == null) {
                            val partFile = outputDir.listFiles()?.firstOrNull { it.name.contains(".") }
                            if (partFile != null) {
                                val cleanName = partFile.name
                                    .substringBefore(".f")
                                    .substringBefore(".mp4")
                                    .substringBefore(".webm")
                                    .substringBefore(".m4a")
                                    .substringBefore(".part")
                                    .trim()
                                if (cleanName.isNotBlank() && cleanName != "media") {
                                    extractedTitle = cleanName
                                }
                            }
                        }

                        val percent = if (progress in 0.0f..1.0f) {
                            (progress * 100).toInt()
                        } else {
                            progress.toInt()
                        }.coerceIn(0, 100)

                        val (speedStr, currentStatus) = parseSpeedAndStatus(line, percent)
                        val (parsedDlBytes, parsedTotBytes) = parseSizesFromLine(line, percent)

                        val now = System.currentTimeMillis()
                        val isStatusChanged = currentStatus != lastStatus
                        val isPercentChanged = percent != lastPercent && kotlin.math.abs(percent - lastPercent) >= 1
                        val isTimeElapsed = (now - lastUpdateTime) >= 500L

                        if (isStatusChanged || isPercentChanged || (isTimeElapsed && speedStr != lastSpeed) || percent == 100) {
                            lastPercent = percent
                            lastSpeed = speedStr
                            lastStatus = currentStatus
                            lastUpdateTime = now

                            val numChunks = SettingsRepository.getChunkCount(applicationContext)
                            val chunkProgresses = computeChunkProgresses(percent, numChunks, line)

                            DownloadRepository.updateProgress(
                                applicationContext,
                                downloadId,
                                percent,
                                speedStr,
                                currentStatus,
                                title = extractedTitle,
                                downloadedBytes = parsedDlBytes,
                                fileSizeBytes = parsedTotBytes,
                                chunkProgresses = chunkProgresses
                            )
                            NotificationHelper.updateNotification(applicationContext)
                        }
                    }
                }

                val downloadedFile = outputDir.listFiles()
                    ?.firstOrNull { it.isFile && !it.name.endsWith(".part") && it.length() > 0 }

                if (downloadedFile == null) {
                    Log.e(TAG, "No completed output file produced in ${outputDir.absolutePath}")
                    DownloadRepository.markFailed(applicationContext, downloadId, "File download incomplete")
                    return@withContext Result.failure()
                }

                val finalTitle = downloadedFile.nameWithoutExtension.ifEmpty { extractedTitle ?: title }
                val actualExtension = downloadedFile.extension.ifEmpty { extension }
                val actualSizeBytes = downloadedFile.length()
                Log.d(TAG, "Downloaded: ${downloadedFile.name} ($actualSizeBytes bytes)")

                // Extract actual media file duration using MediaMetadataRetriever
                var actualDurationSec = durationSeconds
                try {
                    val retriever = android.media.MediaMetadataRetriever()
                    retriever.setDataSource(downloadedFile.absolutePath)
                    val durStr = retriever.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_DURATION)
                    val durMillis = durStr?.toLongOrNull() ?: 0L
                    if (durMillis > 0L) {
                        actualDurationSec = durMillis / 1000L
                    }
                    retriever.release()
                } catch (e: Exception) {
                    Log.w(TAG, "Failed to read file duration with MediaMetadataRetriever", e)
                }

                val hasSubFile = outputDir.listFiles()?.any {
                    it.name.endsWith(".srt") || it.name.endsWith(".vtt") || it.name.endsWith(".ass")
                } ?: false

                val (savedUri, savedPath) = saveToMediaStore(downloadedFile, finalTitle, actualExtension)
                downloadedFile.delete()
                outputDir.deleteRecursively()

                DownloadRepository.markCompleted(
                    applicationContext,
                    downloadId,
                    actualSizeBytes = actualSizeBytes,
                    savedUri = savedUri,
                    savedPath = savedPath,
                    actualTitle = finalTitle,
                    actualDurationSeconds = actualDurationSec,
                    hasSubtitles = hasSubFile
                )
                Result.success()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Download failed", e)
            if (isStopped) {
                val currentItem = DownloadRepository.downloadItems.value.find { it.id == downloadId }
                val statusToSet = if (currentItem?.status == DownloadStatus.PAUSED) {
                    DownloadStatus.PAUSED
                } else {
                    DownloadStatus.CANCELLED
                }
                DownloadRepository.updateProgress(
                    applicationContext,
                    downloadId,
                    lastPercent.coerceAtLeast(0),
                    if (statusToSet == DownloadStatus.PAUSED) "Paused" else "Cancelled",
                    statusToSet
                )
            } else {
                val rawMsg = e.message ?: ""
                val formattedError = when {
                    rawMsg.contains("space", ignoreCase = true) ||
                            rawMsg.contains("enospc", ignoreCase = true) ||
                            rawMsg.contains("storage", ignoreCase = true) ||
                            rawMsg.contains("disk full", ignoreCase = true) -> {
                        "Internal storage is full. Clear space and tap Retry."
                    }
                    rawMsg.isNotBlank() -> rawMsg
                    else -> "Download or merge failed"
                }
                DownloadRepository.markFailed(applicationContext, downloadId, formattedError)
            }
            Result.failure(workDataOf("error" to e.message))
        } finally {
            if (wakeLock.isHeld) {
                wakeLock.release()
            }
        }
    }

    private fun parseSpeedAndStatus(
        line: String,
        currentProgress: Int
    ): Pair<String, DownloadStatus> {
        val lowerLine = line.lowercase()
        return when {
            lowerLine.contains("merg") || lowerLine.contains("ffmpeg") || lowerLine.contains("post-process") -> {
                "Merging chunks..." to DownloadStatus.MERGING
            }
            lowerLine.contains("extracting") -> {
                "Extracting..." to DownloadStatus.DOWNLOADING
            }
            else -> {
                val match = SPEED_REGEX.find(line)
                val speed = if (match != null) {
                    match.groupValues[1].replace(" ", "")
                } else if (currentProgress >= 100) {
                    "Merging/Processing..."
                } else {
                    "Downloading..."
                }
                speed to DownloadStatus.DOWNLOADING
            }
        }
    }

    private fun parseSizesFromLine(line: String, percentInt: Int): Pair<Long, Long> {
        var downloadedBytes = 0L
        var totalSizeBytes = 0L

        val match2 = SIZE_REGEX_2.find(line)
        if (match2 != null) {
            val dlVal = match2.groupValues[1]
            val dlUnit = match2.groupValues[2]
            val totVal = match2.groupValues[3]
            val totUnit = match2.groupValues[4]

            downloadedBytes = parseBytes(dlVal, dlUnit)
            totalSizeBytes = parseBytes(totVal, totUnit)
        } else {
            val match1 = SIZE_REGEX_1.find(line)
            if (match1 != null) {
                val totVal = match1.groupValues[2]
                val totUnit = match1.groupValues[3]
                totalSizeBytes = parseBytes(totVal, totUnit)
                if (totalSizeBytes > 0 && percentInt > 0) {
                    downloadedBytes = (totalSizeBytes * percentInt / 100)
                }
            }
        }

        return Pair(downloadedBytes, totalSizeBytes)
    }

    private fun parseBytes(amountStr: String, unitStr: String): Long {
        val amount = amountStr.toDoubleOrNull() ?: return 0L
        val u = unitStr.uppercase()
        val multiplier = when {
            u.startsWith("G") -> 1024L * 1024L * 1024L
            u.startsWith("M") -> 1024L * 1024L
            u.startsWith("K") -> 1024L
            u.startsWith("B") -> 1L
            u.startsWith("T") -> 1024L * 1024L * 1024L * 1024L
            else -> 1024L * 1024L
        }
        return (amount * multiplier).toLong()
    }

    private fun saveToMediaStore(sourceFile: File, title: String, extension: String): Pair<String?, String?> {
        val resolver = applicationContext.contentResolver
        val safeTitle = sanitizeFileName(title)
        val fileName = "$safeTitle.$extension"

        val mimeType = when (extension.lowercase()) {
            "mp4"  -> "video/mp4"
            "webm" -> "video/webm"
            "mkv"  -> "video/x-matroska"
            "m4a"  -> "audio/mp4"
            "mp3"  -> "audio/mpeg"
            "opus" -> "audio/opus"
            else   -> "application/octet-stream"
        }

        val locationSetting = SettingsRepository.getDownloadLocation(applicationContext)

        val targetDir = if (locationSetting == "sdcard") {
            val extDirs = applicationContext.getExternalFilesDirs(null)
            val sdCardDir = extDirs.firstOrNull { it != null && Environment.isExternalStorageRemovable(it) }
                ?: extDirs.getOrNull(1)

            if (sdCardDir != null) {
                File(sdCardDir, "VideoDownloader").apply { mkdirs() }
            } else {
                val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
                File(downloadsDir, "VideoDownloader").apply { mkdirs() }
            }
        } else {
            val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
            File(downloadsDir, "VideoDownloader").apply { mkdirs() }
        }

        val directFile = File(targetDir, fileName)

        var savedUriString: String? = null
        var savedPathString: String? = null

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && locationSetting != "sdcard") {
            // Android 10+ MediaStore Insertion (single file)
            if (directFile.exists()) {
                try { directFile.delete() } catch (_: Exception) {}
            }

            val relativePath = "${Environment.DIRECTORY_DOWNLOADS}/VideoDownloader"
            val values = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
                put(MediaStore.MediaColumns.MIME_TYPE, mimeType)
                put(MediaStore.MediaColumns.RELATIVE_PATH, relativePath)
                put(MediaStore.MediaColumns.IS_PENDING, 1)
            }

            try {
                val contentUri = MediaStore.Downloads.EXTERNAL_CONTENT_URI
                val uri = resolver.insert(contentUri, values)
                if (uri != null) {
                    resolver.openOutputStream(uri)?.use { outputStream ->
                        FileInputStream(sourceFile).use { inputStream ->
                            inputStream.copyTo(outputStream)
                        }
                    }

                    values.clear()
                    values.put(MediaStore.MediaColumns.IS_PENDING, 0)
                    resolver.update(uri, values, null, null)

                    savedUriString = uri.toString()
                    savedPathString = directFile.absolutePath
                    Log.d(TAG, "Saved via MediaStore: $savedUriString")
                }
            } catch (e: Exception) {
                Log.e(TAG, "MediaStore insert failed, falling back to direct copy", e)
            }
        }

        // Fallback or Legacy (Android 9 and lower, or SD card): Direct File Copy
        if (savedUriString == null) {
            try {
                sourceFile.copyTo(directFile, overwrite = true)
                savedPathString = directFile.absolutePath
                Log.d(TAG, "Direct copy successful: $savedPathString")

                try {
                    android.media.MediaScannerConnection.scanFile(
                        applicationContext,
                        arrayOf(directFile.absolutePath),
                        arrayOf(mimeType)
                    ) { _, uri ->
                        if (uri != null) {
                            savedUriString = uri.toString()
                        }
                    }
                } catch (_: Exception) {}
            } catch (e: Exception) {
                Log.e(TAG, "Direct copy failed", e)
            }
        }

        return Pair(savedUriString, savedPathString ?: if (directFile.exists()) directFile.absolutePath else null)
    }

    private fun sanitizeFileName(name: String): String {
        return name.replace(Regex("""[\\/:*?"<>|]"""), "_").trim()
    }

    private fun computeChunkProgresses(totalPercent: Int, numChunks: Int, line: String): List<Float> {
        val safeChunks = numChunks.coerceIn(1, 16)
        val totalFraction = (totalPercent / 100f).coerceIn(0f, 1f)

        val fragMatch = Regex("""fragment\s+(\d+)\s+of\s+(\d+)""", RegexOption.IGNORE_CASE).find(line)
        if (fragMatch != null) {
            val currentFrag = fragMatch.groupValues[1].toIntOrNull() ?: 1
            val totalFrags = fragMatch.groupValues[2].toIntOrNull() ?: safeChunks

            return List(safeChunks) { i ->
                val chunkFragStart = (i * totalFrags).toFloat() / safeChunks
                val chunkFragEnd = ((i + 1) * totalFrags).toFloat() / safeChunks
                when {
                    currentFrag >= chunkFragEnd -> 1f
                    currentFrag < chunkFragStart -> 0f
                    else -> ((currentFrag - chunkFragStart) / (chunkFragEnd - chunkFragStart)).coerceIn(0f, 1f)
                }
            }
        }

        return List(safeChunks) { i ->
            val chunkStart = i.toFloat() / safeChunks
            val chunkEnd = (i + 1).toFloat() / safeChunks
            when {
                totalFraction >= chunkEnd -> 1f
                totalFraction <= chunkStart -> 0f
                else -> ((totalFraction - chunkStart) / (chunkEnd - chunkStart)).coerceIn(0f, 1f)
            }
        }
    }

    private suspend fun downloadDirectHttpFile(
        downloadId: String,
        fileUrl: String,
        outputFile: File,
        totalFileSize: Long,
        extractedTitle: String
    ): Boolean = withContext(Dispatchers.IO) {
        try {
            val url = URL(fileUrl)
            val connection = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Mobile Safari/537.36")
                connectTimeout = 10000
                readTimeout = 10000
                instanceFollowRedirects = true
            }

            val responseCode = connection.responseCode
            if (responseCode !in 200..300 && responseCode != 302) {
                Log.e(TAG, "Direct HTTP download failed with HTTP $responseCode")
                return@withContext false
            }

            val contentLength = if (connection.contentLengthLong > 0) connection.contentLengthLong else totalFileSize
            val inputStream = connection.inputStream
            val outputStream = outputFile.outputStream()

            val buffer = ByteArray(16384)
            var bytesRead: Int
            var totalRead = 0L
            var lastPercent = -1
            var lastTime = System.currentTimeMillis()

            while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                outputStream.write(buffer, 0, bytesRead)
                totalRead += bytesRead

                if (contentLength > 0) {
                    val percent = ((totalRead * 100) / contentLength).toInt().coerceIn(0, 100)
                    val now = System.currentTimeMillis()
                    if (percent != lastPercent && (now - lastTime > 300L || percent == 100)) {
                        lastPercent = percent
                        lastTime = now
                        val numChunks = SettingsRepository.getChunkCount(applicationContext)
                        val chunkProgresses = List(numChunks) { i ->
                            val start = i.toFloat() / numChunks
                            val end = (i + 1).toFloat() / numChunks
                            val p = percent / 100f
                            when {
                                p >= end -> 1f
                                p <= start -> 0f
                                else -> (p - start) / (end - start)
                            }
                        }
                        DownloadRepository.updateProgress(
                            applicationContext,
                            downloadId,
                            percent,
                            "Downloading",
                            DownloadStatus.DOWNLOADING,
                            title = extractedTitle,
                            downloadedBytes = totalRead,
                            fileSizeBytes = contentLength,
                            chunkProgresses = chunkProgresses
                        )
                    }
                }
            }

            outputStream.flush()
            outputStream.close()
            inputStream.close()
            true
        } catch (e: Exception) {
            Log.e(TAG, "Direct HTTP download error", e)
            false
        }
    }
}
