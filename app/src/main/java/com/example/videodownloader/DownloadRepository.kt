package com.example.videodownloader

import android.content.Context
import android.util.Log
import android.widget.Toast
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.OutOfQuotaPolicy
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.yausername.youtubedl_android.YoutubeDL
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

enum class DownloadStatus {
    PENDING,     // Waiting in queue (not processed)
    PROCESSING,  // Processed and ready to download
    QUEUED,      // Queued/Ready (compatibility alias)
    DOWNLOADING, // Actively downloading
    MERGING,     // Merging audio/video chunks
    PAUSED,      // Paused
    COMPLETED,   // Completed
    FAILED,      // Failed
    CANCELLED    // Cancelled
}

data class DownloadItem(
    val id: String,
    val url: String,
    val title: String,
    val formatId: String,
    val extension: String,
    val isAudioOnly: Boolean,
    val isCombined: Boolean,
    val fileSizeBytes: Long = 0L,
    val downloadedBytes: Long = 0L,
    val durationSeconds: Long = 0L,
    val progress: Int = 0,
    val downloadSpeed: String = "Pending",
    val status: DownloadStatus = DownloadStatus.PENDING,
    val errorMessage: String? = null,
    val timestamp: Long = System.currentTimeMillis(),
    val savedUri: String? = null,
    val savedPath: String? = null,
    val thumbnailUrl: String = "",
    val resolutionLabel: String = "",
    val chunkProgresses: List<Float> = emptyList(),
    val downloadSubtitles: Boolean = false,
    val hasSubtitles: Boolean = false
)

object DownloadRepository {
    private const val TAG = "DownloadRepository"
    private const val PREFS_NAME = "download_repository_prefs"
    private const val KEY_ITEMS_JSON = "items_json"

    private const val MAX_CONCURRENT_DOWNLOADS = 5
    private const val MAX_CONCURRENT_PROCESSING = 5

    private val _downloadItems = MutableStateFlow<List<DownloadItem>>(emptyList())
    val downloadItems: StateFlow<List<DownloadItem>> = _downloadItems.asStateFlow()

    @Volatile
    private var initialized = false

    fun init(context: Context) {
        if (initialized) return
        synchronized(this) {
            if (initialized) return
            val appContext = context.applicationContext
            val prefs = appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val jsonString = prefs.getString(KEY_ITEMS_JSON, null)
            if (!jsonString.isNullOrEmpty()) {
                try {
                    val list = mutableListOf<DownloadItem>()
                    val array = JSONArray(jsonString)
                    for (i in 0 until array.length()) {
                        val obj = array.getJSONObject(i)
                        val statusStr = obj.optString("status", DownloadStatus.FAILED.name)
                        var status = try {
                            DownloadStatus.valueOf(statusStr)
                        } catch (_: Exception) {
                            DownloadStatus.FAILED
                        }
                        // Mark active downloads/processing as PAUSED on app startup/restart
                        if (status == DownloadStatus.DOWNLOADING ||
                            status == DownloadStatus.MERGING ||
                            status == DownloadStatus.PROCESSING ||
                            status == DownloadStatus.QUEUED ||
                            status == DownloadStatus.PENDING
                        ) {
                            status = DownloadStatus.PAUSED
                        }
                        list.add(
                            DownloadItem(
                                id = obj.getString("id"),
                                url = obj.getString("url"),
                                title = obj.getString("title"),
                                formatId = obj.getString("formatId"),
                                extension = obj.getString("extension"),
                                isAudioOnly = obj.getBoolean("isAudioOnly"),
                                isCombined = obj.getBoolean("isCombined"),
                                fileSizeBytes = obj.optLong("fileSizeBytes", 0L),
                                downloadedBytes = obj.optLong("downloadedBytes", 0L),
                                durationSeconds = obj.optLong("durationSeconds", 0L),
                                progress = obj.optInt("progress", 0),
                                downloadSpeed = if (status == DownloadStatus.PAUSED) "Paused" else obj.optString("downloadSpeed", "Pending"),
                                status = status,
                                errorMessage = obj.optString("errorMessage", "").takeIf { it != "null" && !it.isNullOrBlank() },
                                timestamp = obj.optLong("timestamp", System.currentTimeMillis()),
                                savedUri = obj.optString("savedUri", "").takeIf { !it.isNullOrBlank() },
                                savedPath = obj.optString("savedPath", "").takeIf { !it.isNullOrBlank() },
                                thumbnailUrl = obj.optString("thumbnailUrl", ""),
                                resolutionLabel = obj.optString("resolutionLabel", "")
                            )
                        )
                    }
                    _downloadItems.value = list.sortedByDescending { it.timestamp }
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to load saved downloads", e)
                }
            }
            initialized = true
        }
    }

    private fun persist(context: Context) {
        try {
            val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val array = JSONArray()
            _downloadItems.value.forEach { item ->
                val obj = JSONObject().apply {
                    put("id", item.id)
                    put("url", item.url)
                    put("title", item.title)
                    put("formatId", item.formatId)
                    put("extension", item.extension)
                    put("isAudioOnly", item.isAudioOnly)
                    put("isCombined", item.isCombined)
                    put("fileSizeBytes", item.fileSizeBytes)
                    put("downloadedBytes", item.downloadedBytes)
                    put("durationSeconds", item.durationSeconds)
                    put("progress", item.progress)
                    put("downloadSpeed", item.downloadSpeed)
                    put("status", item.status.name)
                    put("errorMessage", item.errorMessage ?: "")
                    put("timestamp", item.timestamp)
                    put("savedUri", item.savedUri ?: "")
                    put("savedPath", item.savedPath ?: "")
                    put("thumbnailUrl", item.thumbnailUrl)
                    put("resolutionLabel", item.resolutionLabel)
                }
                array.put(obj)
            }
            prefs.edit().putString(KEY_ITEMS_JSON, array.toString()).apply()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to persist downloads", e)
        }
    }

    @Synchronized
    fun processQueue(context: Context) {
        init(context)

        val currentList = _downloadItems.value.toMutableList()
        var modified = false

        var downloadingCount = currentList.count {
            it.status == DownloadStatus.DOWNLOADING || it.status == DownloadStatus.MERGING
        }
        var processingCount = currentList.count {
            it.status == DownloadStatus.PROCESSING || it.status == DownloadStatus.QUEUED
        }

        var availableDownloadSlots = (MAX_CONCURRENT_DOWNLOADS - downloadingCount).coerceAtLeast(0)

        // 1. Promote items from PROCESSING/QUEUED to DOWNLOADING first
        if (availableDownloadSlots > 0) {
            for (i in currentList.indices) {
                if (availableDownloadSlots <= 0) break
                val item = currentList[i]
                if (item.status == DownloadStatus.PROCESSING || item.status == DownloadStatus.QUEUED) {
                    val updatedItem = item.copy(
                        status = DownloadStatus.DOWNLOADING,
                        downloadSpeed = "Connecting..."
                    )
                    currentList[i] = updatedItem
                    enqueueWork(context, updatedItem)
                    availableDownloadSlots--
                    modified = true
                }
            }
        }

        // Recalculate downloading count and slots
        downloadingCount = currentList.count {
            it.status == DownloadStatus.DOWNLOADING || it.status == DownloadStatus.MERGING
        }
        availableDownloadSlots = (MAX_CONCURRENT_DOWNLOADS - downloadingCount).coerceAtLeast(0)

        // 2. If download slots are still open and no PROCESSING items left, promote PENDING directly to DOWNLOADING
        if (availableDownloadSlots > 0) {
            for (i in currentList.indices) {
                if (availableDownloadSlots <= 0) break
                val item = currentList[i]
                if (item.status == DownloadStatus.PENDING) {
                    val updatedItem = item.copy(
                        status = DownloadStatus.DOWNLOADING,
                        downloadSpeed = "Connecting..."
                    )
                    currentList[i] = updatedItem
                    enqueueWork(context, updatedItem)
                    availableDownloadSlots--
                    modified = true
                }
            }
        }

        // 3. Fill remaining PROCESSING slots (up to 5) from PENDING items
        processingCount = currentList.count {
            it.status == DownloadStatus.PROCESSING || it.status == DownloadStatus.QUEUED
        }
        var availableProcessingSlots = (MAX_CONCURRENT_PROCESSING - processingCount).coerceAtLeast(0)

        if (availableProcessingSlots > 0) {
            for (i in currentList.indices) {
                if (availableProcessingSlots <= 0) break
                val item = currentList[i]
                if (item.status == DownloadStatus.PENDING) {
                    val updatedItem = item.copy(
                        status = DownloadStatus.PROCESSING,
                        downloadSpeed = "Ready"
                    )
                    currentList[i] = updatedItem
                    availableProcessingSlots--
                    modified = true
                }
            }
        }

        if (modified) {
            _downloadItems.value = currentList
            persist(context)
            NotificationHelper.updateNotification(context)
        }
    }

    fun startDownload(
        context: Context,
        url: String,
        title: String,
        format: MediaFormat,
        durationSeconds: Long = 0L,
        thumbnailUrl: String = "",
        downloadSubtitles: Boolean = false
    ): String {
        init(context)
        val downloadId = UUID.randomUUID().toString()
        val item = DownloadItem(
            id = downloadId,
            url = url,
            title = title,
            formatId = format.formatId,
            extension = format.extension,
            isAudioOnly = !format.hasVideo,
            isCombined = format.isCombined,
            fileSizeBytes = format.fileSizeBytes,
            durationSeconds = durationSeconds,
            status = DownloadStatus.PENDING,
            downloadSpeed = "Pending",
            thumbnailUrl = thumbnailUrl.ifBlank { deriveYouTubeThumbnail(url) },
            resolutionLabel = deriveResolutionLabel(format),
            downloadSubtitles = downloadSubtitles
        )

        _downloadItems.value = listOf(item) + _downloadItems.value
        persist(context)

        processQueue(context)
        NotificationHelper.updateNotification(context)
        return downloadId
    }

    fun startBatchDownloads(
        context: Context,
        items: List<PlaylistItem>,
        format: MediaFormat,
        downloadSubtitles: Boolean = false
    ) {
        init(context)
        if (items.isEmpty()) return

        val newDownloadItems = mutableListOf<DownloadItem>()

        items.forEach { item ->
            val downloadId = UUID.randomUUID().toString()

            val downloadItem = DownloadItem(
                id = downloadId,
                url = item.url,
                title = item.title,
                formatId = format.formatId,
                extension = format.extension,
                isAudioOnly = !format.hasVideo,
                isCombined = format.isCombined,
                fileSizeBytes = format.fileSizeBytes,
                durationSeconds = item.durationSeconds,
                status = DownloadStatus.PENDING,
                downloadSpeed = "Pending",
                thumbnailUrl = item.thumbnailUrl.ifBlank { deriveYouTubeThumbnail(item.url) },
                resolutionLabel = deriveResolutionLabel(format),
                downloadSubtitles = downloadSubtitles
            )
            newDownloadItems.add(downloadItem)
        }

        _downloadItems.value = newDownloadItems + _downloadItems.value
        persist(context)

        processQueue(context)
        NotificationHelper.updateNotification(context)
    }

    private fun enqueueWork(context: Context, item: DownloadItem) {
        val workData = workDataOf(
            DownloadWorker.KEY_DOWNLOAD_ID to item.id,
            DownloadWorker.KEY_URL to item.url,
            DownloadWorker.KEY_FORMAT_ID to item.formatId,
            DownloadWorker.KEY_EXTENSION to item.extension,
            DownloadWorker.KEY_TITLE to item.title,
            DownloadWorker.KEY_IS_AUDIO_ONLY to item.isAudioOnly,
            DownloadWorker.KEY_IS_COMBINED to item.isCombined,
            DownloadWorker.KEY_FILE_SIZE to item.fileSizeBytes,
            DownloadWorker.KEY_DURATION to item.durationSeconds
        )

        val request = OneTimeWorkRequestBuilder<DownloadWorker>()
            .setInputData(workData)
            .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
            .addTag(item.id)
            .addTag(SharePopupActivity.WORK_NAME)
            .build()

        WorkManager.getInstance(context.applicationContext).enqueue(request)
    }

    fun updateProgress(
        context: Context,
        id: String,
        progress: Int,
        speed: String,
        status: DownloadStatus = DownloadStatus.DOWNLOADING,
        title: String? = null,
        downloadedBytes: Long = 0L,
        fileSizeBytes: Long = 0L,
        chunkProgresses: List<Float> = emptyList()
    ) {
        var statusChanged = false
        var isTerminalOrPaused = false

        _downloadItems.value = _downloadItems.value.map { item ->
            if (item.id == id) {
                if (item.status != status) {
                    statusChanged = true
                    if (status == DownloadStatus.FAILED || status == DownloadStatus.CANCELLED || status == DownloadStatus.PAUSED) {
                        isTerminalOrPaused = true
                    }
                }
                val newTotalSize = if (fileSizeBytes > 0L) fileSizeBytes else item.fileSizeBytes
                val newDownloadedBytes = when {
                    downloadedBytes > 0L -> downloadedBytes
                    newTotalSize > 0L -> (newTotalSize * progress / 100)
                    else -> item.downloadedBytes
                }
                item.copy(
                    title = if (!title.isNullOrBlank()) title else item.title,
                    fileSizeBytes = newTotalSize,
                    downloadedBytes = newDownloadedBytes,
                    progress = progress,
                    downloadSpeed = speed,
                    status = status,
                    chunkProgresses = if (chunkProgresses.isNotEmpty()) chunkProgresses else item.chunkProgresses
                )
            } else {
                item
            }
        }

        if (statusChanged || progress == 100) {
            persist(context)
        }
        NotificationHelper.updateNotification(context)
        if (isTerminalOrPaused) {
            processQueue(context)
        }
    }

    fun markCompleted(
        context: Context,
        id: String,
        actualSizeBytes: Long = 0L,
        savedUri: String? = null,
        savedPath: String? = null,
        actualTitle: String? = null,
        actualDurationSeconds: Long = 0L,
        hasSubtitles: Boolean = false
    ) {
        _downloadItems.value = _downloadItems.value.map { item ->
            if (item.id == id) {
                val finalSize = if (actualSizeBytes > 0) actualSizeBytes else item.fileSizeBytes
                val finalDuration = if (actualDurationSeconds > 0L) actualDurationSeconds else item.durationSeconds
                item.copy(
                    title = if (!actualTitle.isNullOrBlank()) actualTitle else item.title,
                    progress = 100,
                    downloadSpeed = "Done",
                    status = DownloadStatus.COMPLETED,
                    fileSizeBytes = finalSize,
                    downloadedBytes = finalSize,
                    durationSeconds = finalDuration,
                    hasSubtitles = hasSubtitles || item.downloadSubtitles,
                    savedUri = savedUri ?: item.savedUri,
                    savedPath = savedPath ?: item.savedPath
                )
            } else {
                item
            }
        }
        persist(context)
        NotificationHelper.updateNotification(context)
        processQueue(context)
    }

    fun markFailed(context: Context, id: String, error: String) {
        _downloadItems.value = _downloadItems.value.map { item ->
            if (item.id == id) {
                item.copy(
                    status = DownloadStatus.FAILED,
                    errorMessage = error,
                    downloadSpeed = "Failed"
                )
            } else {
                item
            }
        }
        persist(context)
        NotificationHelper.updateNotification(context)
        processQueue(context)
    }

    fun pauseDownload(context: Context, id: String) {
        try {
            YoutubeDL.getInstance().destroyProcessById(id)
        } catch (e: Exception) {
            Log.w(TAG, "Error destroying process $id", e)
        }
        try {
            WorkManager.getInstance(context.applicationContext).cancelAllWorkByTag(id)
        } catch (e: Exception) {
            Log.e(TAG, "Error cancelling work $id", e)
        }
        _downloadItems.value = _downloadItems.value.map { item ->
            if (item.id == id) {
                item.copy(
                    status = DownloadStatus.PAUSED,
                    downloadSpeed = "Paused"
                )
            } else {
                item
            }
        }
        persist(context)
        NotificationHelper.updateNotification(context)
        processQueue(context)
    }

    fun resumeDownload(context: Context, id: String) {
        _downloadItems.value = _downloadItems.value.map { item ->
            if (item.id == id) {
                item.copy(
                    status = DownloadStatus.PENDING,
                    downloadSpeed = "Pending",
                    errorMessage = null
                )
            } else {
                item
            }
        }
        persist(context)
        NotificationHelper.updateNotification(context)
        processQueue(context)
    }

    fun pauseAll(context: Context) {
        val activeIds = _downloadItems.value.filter {
            it.status == DownloadStatus.DOWNLOADING ||
                    it.status == DownloadStatus.MERGING ||
                    it.status == DownloadStatus.PROCESSING ||
                    it.status == DownloadStatus.QUEUED ||
                    it.status == DownloadStatus.PENDING
        }.map { it.id }

        activeIds.forEach { id ->
            try {
                YoutubeDL.getInstance().destroyProcessById(id)
            } catch (_: Exception) {}
            try {
                WorkManager.getInstance(context.applicationContext).cancelAllWorkByTag(id)
            } catch (_: Exception) {}
        }

        _downloadItems.value = _downloadItems.value.map { item ->
            if (activeIds.contains(item.id)) {
                item.copy(
                    status = DownloadStatus.PAUSED,
                    downloadSpeed = "Paused"
                )
            } else {
                item
            }
        }
        persist(context)
        NotificationHelper.updateNotification(context)
        processQueue(context)
    }

    fun resumeAll(context: Context) {
        _downloadItems.value = _downloadItems.value.map { item ->
            if (item.status == DownloadStatus.PAUSED ||
                item.status == DownloadStatus.FAILED ||
                item.status == DownloadStatus.CANCELLED
            ) {
                item.copy(
                    status = DownloadStatus.PENDING,
                    downloadSpeed = "Pending",
                    errorMessage = null
                )
            } else {
                item
            }
        }
        persist(context)
        NotificationHelper.updateNotification(context)
        processQueue(context)
    }

    fun cancelAll(context: Context) {
        val cancellableIds = _downloadItems.value.filter {
            it.status == DownloadStatus.DOWNLOADING ||
                    it.status == DownloadStatus.MERGING ||
                    it.status == DownloadStatus.PROCESSING ||
                    it.status == DownloadStatus.QUEUED ||
                    it.status == DownloadStatus.PENDING ||
                    it.status == DownloadStatus.PAUSED
        }.map { it.id }

        cancellableIds.forEach { id ->
            try {
                YoutubeDL.getInstance().destroyProcessById(id)
            } catch (_: Exception) {}
            try {
                WorkManager.getInstance(context.applicationContext).cancelAllWorkByTag(id)
            } catch (_: Exception) {}
        }

        _downloadItems.value = _downloadItems.value.map { item ->
            if (cancellableIds.contains(item.id)) {
                item.copy(
                    status = DownloadStatus.CANCELLED,
                    downloadSpeed = "Cancelled"
                )
            } else {
                item
            }
        }
        persist(context)
        NotificationHelper.updateNotification(context)
        processQueue(context)
    }

    fun cancelDownload(context: Context, id: String) {
        try {
            YoutubeDL.getInstance().destroyProcessById(id)
        } catch (e: Exception) {
            Log.w(TAG, "Error destroying process $id", e)
        }
        try {
            WorkManager.getInstance(context.applicationContext).cancelAllWorkByTag(id)
        } catch (e: Exception) {
            Log.e(TAG, "Error cancelling work $id", e)
        }
        _downloadItems.value = _downloadItems.value.map { item ->
            if (item.id == id) {
                item.copy(
                    status = DownloadStatus.CANCELLED,
                    downloadSpeed = "Cancelled"
                )
            } else {
                item
            }
        }
        persist(context)
        NotificationHelper.updateNotification(context)
        processQueue(context)
    }

    fun retryDownload(context: Context, id: String) {
        val target = _downloadItems.value.find { it.id == id } ?: return
        val updated = target.copy(
            status = DownloadStatus.PENDING,
            downloadSpeed = "Pending",
            errorMessage = null,
            timestamp = System.currentTimeMillis()
        )
        _downloadItems.value = _downloadItems.value.map { if (it.id == id) updated else it }
            .sortedByDescending { it.timestamp }
        persist(context)
        NotificationHelper.updateNotification(context)
        processQueue(context)
    }

    fun removeItem(context: Context, id: String) {
        try {
            YoutubeDL.getInstance().destroyProcessById(id)
        } catch (_: Exception) {}
        try {
            WorkManager.getInstance(context.applicationContext).cancelAllWorkByTag(id)
        } catch (_: Exception) {}
        _downloadItems.value = _downloadItems.value.filterNot { it.id == id }
        persist(context)
        NotificationHelper.updateNotification(context)
        processQueue(context)
    }

    fun clearCancelledAndFailed(context: Context) {
        _downloadItems.value = _downloadItems.value.filterNot {
            it.status == DownloadStatus.CANCELLED || it.status == DownloadStatus.FAILED
        }
        persist(context)
        NotificationHelper.updateNotification(context)
        processQueue(context)
    }

    fun clearCompleted(context: Context) {
        _downloadItems.value = _downloadItems.value.filterNot {
            it.status == DownloadStatus.COMPLETED
        }
        persist(context)
        NotificationHelper.updateNotification(context)
        processQueue(context)
    }

    fun renameItem(context: Context, id: String, newTitle: String) {
        _downloadItems.value = _downloadItems.value.map { item ->
            if (item.id == id) {
                item.copy(title = newTitle)
            } else {
                item
            }
        }
        persist(context)
        Toast.makeText(context, "Renamed to '$newTitle'", Toast.LENGTH_SHORT).show()
    }

    fun convertToMusic(context: Context, item: DownloadItem) {
        Toast.makeText(context, "Converting '${item.title}' to MP3...", Toast.LENGTH_SHORT).show()
        val audioFormat = MediaFormat(
            formatId = "bestaudio[ext=m4a]/bestaudio/140",
            extension = "mp3",
            fileSizeBytes = 0L,
            resolution = "Audio (MP3)",
            formatNote = "Converted MP3 Audio",
            bitrate = 128.0,
            hasVideo = false,
            hasAudio = true,
            isCombined = false
        )
        startDownload(
            context = context,
            url = item.url,
            title = "${item.title} (Audio)",
            format = audioFormat,
            durationSeconds = item.durationSeconds,
            thumbnailUrl = item.thumbnailUrl
        )
    }
}

fun deriveYouTubeThumbnail(url: String): String {
    val vIdMatch = Regex("""(?:v=|\/|youtu\.be\/)([a-zA-Z0-9_-]{11})""").find(url)
    val vId = vIdMatch?.groupValues?.get(1)
    return if (!vId.isNullOrBlank()) "https://i.ytimg.com/vi/$vId/hqdefault.jpg" else ""
}

fun deriveResolutionLabel(format: MediaFormat): String {
    if (!format.hasVideo) return "AUDIO"
    val res = format.resolution
    return when {
        res.contains("2160") || res.contains("4K") -> "2K HD"
        res.contains("1440") || res.contains("2K") -> "2K HD"
        res.contains("1080") -> "1080P HD"
        res.contains("720") -> "720P HD"
        res.contains("480") -> "480P"
        res.contains("360") -> "360P"
        res.contains("240") -> "240P"
        res.contains("144") -> "144P"
        else -> res.ifEmpty { "HD" }
    }
}
