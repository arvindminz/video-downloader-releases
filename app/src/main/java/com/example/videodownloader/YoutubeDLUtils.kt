package com.example.videodownloader

import android.content.Context
import android.util.Log
import com.yausername.ffmpeg.FFmpeg
import com.yausername.youtubedl_android.YoutubeDL
import com.yausername.youtubedl_android.YoutubeDLRequest
import com.yausername.youtubedl_android.YoutubeDLResponse
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.net.URL

object YoutubeDLUtils {
    private const val TAG = "YoutubeDLUtils"

    @Volatile
    private var isUpdated = false

    @Volatile
    private var isInitialised = false

    /**
     * Initialises both youtubedl-android AND the bundled FFmpeg.
     *
     * FFmpeg is REQUIRED for merging separate video + audio streams,
     * which is how YouTube delivers every resolution ≥ 720p. Without
     * this init call, yt-dlp silently skips the audio stream and you
     * end up with a silent video.
     */
    suspend fun init(context: Context) = withContext(Dispatchers.IO) {
        if (isInitialised) return@withContext
        try {
            val appContext = context.applicationContext
            YoutubeDL.getInstance().init(appContext)
            // ── CRITICAL: initialise FFmpeg for stream merging ──
            FFmpeg.getInstance().init(appContext)
            isInitialised = true
            Log.d(TAG, "YoutubeDL + FFmpeg initialised")
        } catch (e: Exception) {
            Log.e(TAG, "YoutubeDL / FFmpeg init failed", e)
        }
    }

    /**
     * Updates yt-dlp executable to the latest release from GitHub.
     * Falls back to a direct download if the built-in updater fails
     * (e.g. due to GitHub API rate limiting).
     */
    suspend fun updateYtDlp(context: Context, force: Boolean = false): Boolean =
        withContext(Dispatchers.IO) {
            if (isUpdated && !force) return@withContext true

            val appContext = context.applicationContext
            try {
                init(appContext)
                val status = YoutubeDL.getInstance().updateYoutubeDL(appContext)
                Log.d(TAG, "YoutubeDL update status: $status")
                isUpdated = true
                return@withContext true
            } catch (e: Exception) {
                Log.w(TAG, "updateYoutubeDL failed, attempting direct download fallback", e)
                val fallbackSuccess = downloadLatestYtDlpDirect(appContext)
                if (fallbackSuccess) isUpdated = true
                return@withContext fallbackSuccess
            }
        }

    /**
     * Direct download fallback for the yt-dlp binary, bypassing the
     * GitHub API rate limit that often breaks the built-in updater.
     */
    private fun downloadLatestYtDlpDirect(context: Context): Boolean {
        return try {
            val baseDir = File(context.noBackupFilesDir, "youtubedl-android")
            val ytdlpDir = File(baseDir, "yt-dlp")
            val binary = File(ytdlpDir, "yt-dlp")

            val downloadUrl = URL("https://github.com/yt-dlp/yt-dlp/releases/latest/download/yt-dlp")
            val tempFile = File.createTempFile("yt-dlp", null, context.cacheDir)

            try {
                downloadUrl.openStream().use { input ->
                    tempFile.outputStream().use { output ->
                        input.copyTo(output)
                    }
                }
                if (tempFile.length() > 0) {
                    if (ytdlpDir.exists()) ytdlpDir.deleteRecursively()
                    ytdlpDir.mkdirs()
                    tempFile.copyTo(binary, overwrite = true)
                    binary.setExecutable(true, false)
                    Log.d(TAG, "Direct yt-dlp download successful")
                    true
                } else false
            } finally {
                tempFile.delete()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Direct yt-dlp download failed", e)
            false
        }
    }

    /**
     * Executes a YoutubeDLRequest safely, updating yt-dlp and retrying
     * if a `--js-runtimes` or incompatible-option error is encountered.
     */
    suspend fun execute(
        context: Context,
        request: YoutubeDLRequest,
        processId: String? = null,
        callback: ((Float, Long, String) -> Unit)? = null
    ): YoutubeDLResponse = withContext(Dispatchers.IO) {
        init(context)

        try {
            YoutubeDL.getInstance().execute(request, processId, callback)
        } catch (e: Exception) {
            val msg = e.message ?: ""
            if (msg.contains("js-runtimes", ignoreCase = true) || !isUpdated) {
                Log.w(TAG, "Execution failed, attempting yt-dlp force update and retry", e)
                val updated = updateYtDlp(context, force = true)
                if (updated) {
                    return@withContext YoutubeDL.getInstance().execute(request, processId, callback)
                }
            }
            throw e
        }
    }

    suspend fun fetchVideoOrPlaylistInfo(context: Context, rawUrl: String): VideoInfo = withContext(Dispatchers.IO) {
        val urlRegex = Regex("""(?:https?://|www\.)[^\s"']+""", RegexOption.IGNORE_CASE)
        val cleanUrl = urlRegex.find(rawUrl)?.value ?: rawUrl.trim()
        if (cleanUrl.isBlank()) {
            throw IllegalArgumentException("Invalid URL")
        }

        val lowerUrl = cleanUrl.lowercase()
        val isYouTube = lowerUrl.contains("youtube.com") || lowerUrl.contains("youtu.be")
        val isPlaylistUrl = PresetFormats.isDedicatedPlaylistUrl(cleanUrl)

        val fastRequest = YoutubeDLRequest(cleanUrl).apply {
            addOption("--dump-json")
            addOption("--skip-download")
            addOption("--no-warnings")
            addOption("--no-check-certificates")
            addOption("--no-write-comments")
            addOption("--socket-timeout", "5")
            addOption("--retries", "1")
            addOption("--extractor-retries", "1")

            if (isPlaylistUrl) {
                addOption("--flat-playlist")
                addOption("--playlist-end", "2000")
            } else {
                addOption("--no-playlist")
            }

            if (isYouTube) {
                addOption("--extractor-args", "youtube:player_client=visionos,web")
            }
        }

        val response = try {
            execute(context, fastRequest)
        } catch (e: Exception) {
            Log.w(TAG, "Fast format fetch failed, attempting standard fallback fetch", e)
            val fallbackRequest = YoutubeDLRequest(cleanUrl).apply {
                addOption("--dump-json")
                addOption("--skip-download")
                if (isPlaylistUrl) {
                    addOption("--flat-playlist")
                    addOption("--playlist-end", "2000")
                } else {
                    addOption("--no-playlist")
                }
                addOption("--no-warnings")
                addOption("--no-check-certificates")
                addOption("--socket-timeout", "10")
                if (isYouTube) {
                    addOption("--extractor-args", "youtube:player_client=visionos,web")
                }
            }
            execute(context, fallbackRequest)
        }

        parseResponseToVideoInfo(cleanUrl, response.out)
    }

    suspend fun fetchVideoOrPlaylistInfoStreamed(
        context: Context,
        rawUrl: String,
        onRealtimeItemFetched: (PlaylistItem, String?) -> Unit
    ): VideoInfo = withContext(Dispatchers.IO) {
        val urlRegex = Regex("""(?:https?://|www\.)[^\s"']+""", RegexOption.IGNORE_CASE)
        val cleanUrl = urlRegex.find(rawUrl)?.value ?: rawUrl.trim()
        if (cleanUrl.isBlank()) {
            throw IllegalArgumentException("Invalid URL")
        }

        val lowerUrl = cleanUrl.lowercase()
        val isYouTube = lowerUrl.contains("youtube.com") || lowerUrl.contains("youtu.be")
        val isPlaylistUrl = PresetFormats.isDedicatedPlaylistUrl(cleanUrl)

        var detectedPlaylistTitle: String? = null

        val fastRequest = YoutubeDLRequest(cleanUrl).apply {
            addOption("--dump-json")
            addOption("--skip-download")
            addOption("--no-warnings")
            addOption("--no-check-certificates")
            addOption("--no-write-comments")
            addOption("--socket-timeout", "6")
            if (isPlaylistUrl) {
                addOption("--flat-playlist")
                addOption("--playlist-end", "2000")
            } else {
                addOption("--no-playlist")
            }
            if (isYouTube) {
                addOption("--extractor-args", "youtube:player_client=visionos,web")
            }
        }

        val response = try {
            execute(context, fastRequest) { _, _, line ->
                if (line.isNotBlank() && line.contains("{")) {
                    try {
                        val obj = JSONObject(line)
                        val type = obj.optString("_type", "")
                        if (type == "playlist" && obj.has("title")) {
                            detectedPlaylistTitle = obj.optString("title", "")
                        }
                        val id = obj.optString("id", "")
                        val title = obj.optString("title", "")
                        if (id.isNotBlank() && title.isNotBlank() && type != "playlist") {
                            val duration = obj.optLong("duration", 0L)
                            val webpageUrl = obj.optString("url", "").ifBlank { "https://youtu.be/$id" }
                            val fullUrl = if (webpageUrl.startsWith("http")) webpageUrl else "https://youtu.be/$id"
                            val thumb = obj.optString("thumbnail", "").ifBlank { "https://i.ytimg.com/vi/$id/hqdefault.jpg" }

                            val item = PlaylistItem(
                                id = id,
                                title = title,
                                url = fullUrl,
                                thumbnailUrl = thumb,
                                durationSeconds = duration
                            )
                            onRealtimeItemFetched(item, detectedPlaylistTitle)
                        }
                    } catch (_: Exception) {}
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Streamed fetch failed, fallback executing", e)
            val fallbackRequest = YoutubeDLRequest(cleanUrl).apply {
                addOption("--dump-json")
                addOption("--skip-download")
                if (isPlaylistUrl) {
                    addOption("--flat-playlist")
                    addOption("--playlist-end", "2000")
                } else {
                    addOption("--no-playlist")
                }
                addOption("--no-warnings")
                addOption("--no-check-certificates")
                addOption("--socket-timeout", "10")
                if (isYouTube) {
                    addOption("--extractor-args", "youtube:player_client=visionos,web")
                }
            }
            execute(context, fallbackRequest)
        }

        return@withContext parseResponseToVideoInfo(cleanUrl, response.out)
    }

    fun parseResponseToVideoInfo(rawUrl: String, responseOut: String): VideoInfo {
        val jsonObjects = mutableListOf<JSONObject>()
        responseOut.lineSequence()
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .forEach { line ->
                try {
                    jsonObjects.add(JSONObject(line))
                } catch (_: Exception) {}
            }

        if (jsonObjects.isEmpty()) {
            throw Exception("No valid metadata returned from yt-dlp")
        }

        val playlistEntries = mutableListOf<PlaylistItem>()
        var playlistTitle = ""
        var isPlaylist = false
        var isRadio = rawUrl.contains("list=RD", ignoreCase = true) || rawUrl.contains("radio", ignoreCase = true)

        if (jsonObjects.size > 1) {
            isPlaylist = true
            jsonObjects.forEachIndexed { index, obj ->
                val type = obj.optString("_type", "")
                if (type == "playlist") {
                    if (playlistTitle.isBlank()) {
                        playlistTitle = obj.optString("title", "")
                    }
                    if (obj.has("entries")) {
                        val entriesArr = obj.optJSONArray("entries")
                        if (entriesArr != null) {
                            for (i in 0 until entriesArr.length()) {
                                val entryObj = entriesArr.getJSONObject(i)
                                val item = parsePlaylistItem(entryObj, playlistEntries.size + 1)
                                if (item != null) playlistEntries.add(item)
                            }
                        }
                    }
                } else {
                    val item = parsePlaylistItem(obj, playlistEntries.size + 1)
                    if (item != null) {
                        playlistEntries.add(item)
                    }
                }
            }
        } else {
            val singleObj = jsonObjects[0]
            val type = singleObj.optString("_type", "")
            if (type == "playlist" || singleObj.has("entries")) {
                isPlaylist = true
                playlistTitle = singleObj.optString("title", "")
                val entriesArr = singleObj.optJSONArray("entries")
                if (entriesArr != null) {
                    for (i in 0 until entriesArr.length()) {
                        val entryObj = entriesArr.getJSONObject(i)
                        val item = parsePlaylistItem(entryObj, playlistEntries.size + 1)
                        if (item != null) playlistEntries.add(item)
                    }
                }
            } else {
                val singleItem = parsePlaylistItem(singleObj, 1)
                if (rawUrl.contains("list=", ignoreCase = true) && singleItem != null) {
                    isPlaylist = true
                    playlistEntries.add(singleItem)
                } else {
                    return parseSingleVideoInfo(singleObj)
                }
            }
        }

        if (rawUrl.contains("list=RD", ignoreCase = true) ||
            playlistTitle.contains("mix", ignoreCase = true) ||
            playlistTitle.contains("radio", ignoreCase = true)
        ) {
            isRadio = true
        }

        if (playlistTitle.isBlank()) {
            playlistTitle = if (isRadio) "Radio Mix" else "Playlist"
        }

        val repThumbnail = playlistEntries.firstOrNull()?.thumbnailUrl ?: ""
        val totalDuration = playlistEntries.sumOf { it.durationSeconds }

        val videoFormats = generateStandardVideoFormats(totalDuration)
        val audioFormats = generateStandardAudioFormats(totalDuration)

        return VideoInfo(
            title = playlistTitle,
            thumbnailUrl = repThumbnail,
            durationSeconds = totalDuration,
            videoFormats = videoFormats,
            audioFormats = audioFormats,
            isPlaylist = isPlaylist || isRadio || playlistEntries.size > 1,
            isRadio = isRadio,
            playlistItems = playlistEntries
        )
    }

    private fun parsePlaylistItem(json: JSONObject, defaultIndex: Int): PlaylistItem? {
        val title = json.optString("title", "").ifBlank {
            json.optString("name", "Video $defaultIndex")
        }
        val id = json.optString("id", "")
        var url = json.optString("webpage_url", "")
        if (url.isBlank()) {
            url = json.optString("url", "")
        }
        if (url.isBlank() || !url.startsWith("http")) {
            if (id.isNotBlank()) {
                url = "https://www.youtube.com/watch?v=$id"
            }
        }
        if (url.isBlank() && title == "Video $defaultIndex") {
            return null
        }

        var thumbnail = json.optString("thumbnail", "")
        if (thumbnail.isBlank()) {
            val thumbnailsArr = json.optJSONArray("thumbnails")
            if (thumbnailsArr != null && thumbnailsArr.length() > 0) {
                thumbnail = thumbnailsArr.getJSONObject(thumbnailsArr.length() - 1).optString("url", "")
            }
        }

        val duration = json.optLong("duration", 0L)

        return PlaylistItem(
            id = if (id.isNotBlank()) id else url.ifBlank { "item_$defaultIndex" },
            title = title,
            url = url,
            thumbnailUrl = thumbnail,
            durationSeconds = duration,
            isSelected = true
        )
    }

    private fun parseSingleVideoInfo(json: JSONObject): VideoInfo {
        val title = json.optString("title", "Unknown title")
        var thumbnail = json.optString("thumbnail", "")
        if (thumbnail.isBlank()) {
            val thumbnailsArr = json.optJSONArray("thumbnails")
            if (thumbnailsArr != null && thumbnailsArr.length() > 0) {
                thumbnail = thumbnailsArr.getJSONObject(thumbnailsArr.length() - 1).optString("url", "")
            }
        }
        val duration = json.optLong("duration", 0L)
        val extractor = json.optString("extractor", "").lowercase()
        val extractorKey = json.optString("extractor_key", "").lowercase()
        val webpageUrl = json.optString("webpage_url", "").lowercase()
        val rawOriginalUrl = json.optString("url", "").lowercase()
        val isYouTube = extractor.contains("youtube") || extractorKey.contains("youtube") || webpageUrl.contains("youtu") || rawOriginalUrl.contains("youtu")

        val formatsArray = json.optJSONArray("formats")
        val availableFormats = mutableListOf<JSONObject>()

        if (formatsArray != null) {
            for (i in 0 until formatsArray.length()) {
                availableFormats.add(formatsArray.getJSONObject(i))
            }
        }

        val videoHeights = availableFormats
            .mapNotNull { fmt ->
                val vcodec = fmt.optString("vcodec", "none")
                val h = fmt.optInt("height", 0)
                if (vcodec != "none" && vcodec != "null" && h > 0) h else null
            }
            .distinct()
            .sortedDescending()

        val videoFormats = mutableListOf<MediaFormat>()
        val audioFormats = mutableListOf<MediaFormat>()

        if (isYouTube) {
            // ── YouTube-specific format processing ──
            // 1. Locate audio format ID 140 (or best m4a audio fallback)
            val format140 = availableFormats.find { fmt ->
                val fmtId = fmt.optString("format_id", "")
                fmtId == "140"
            } ?: availableFormats.find { fmt ->
                val vcodec = fmt.optString("vcodec", "none")
                val acodec = fmt.optString("acodec", "none")
                val ext = fmt.optString("ext", "").lowercase()
                (vcodec == "none" || vcodec == "null") && acodec != "none" && acodec != "null" && ext == "m4a"
            } ?: availableFormats.find { fmt ->
                val vcodec = fmt.optString("vcodec", "none")
                val acodec = fmt.optString("acodec", "none")
                (vcodec == "none" || vcodec == "null") && acodec != "none" && acodec != "null"
            }

            val targetAudioId = format140?.optString("format_id", "140")?.ifBlank { "140" } ?: "140"

            val audioSize = if (format140 != null) {
                val fs = format140.optLong("filesize", 0L)
                if (fs > 0) fs else format140.optLong("filesize_approx", 0L)
            } else 0L

            val audioBitrate = if (format140 != null) {
                val tbr = format140.optDouble("tbr", 0.0)
                if (tbr > 0) tbr else format140.optDouble("abr", 0.0)
            } else 128.0

            val calculatedAudioSize = if (audioSize > 0) {
                audioSize
            } else if (duration > 0 && audioBitrate > 0) {
                (audioBitrate * 1000.0 / 8.0 * duration).toLong()
            } else 0L

            // 2. Filter video streams and group by resolution dimension
            val allVideoFormats = availableFormats.filter { fmt ->
                val vcodec = fmt.optString("vcodec", "none")
                vcodec != "none" && vcodec != "null" && vcodec.isNotBlank()
            }

            // Group video formats by resolution dimension (e.g., "3840x2026", "2560x1350", "1920x1012", "1280x676", "854x450", "640x338", "426x224", "256x136")
            val groupedByResolution = allVideoFormats.groupBy { fmt ->
                val w = fmt.optInt("width", 0)
                val h = fmt.optInt("height", 0)
                if (w > 0 && h > 0) {
                    "${w}x${h}"
                } else {
                    val resStr = fmt.optString("resolution", "")
                    if (resStr.isNotBlank() && resStr != "audio only") resStr else if (h > 0) "${h}p" else "Unknown"
                }
            }

            // For each resolution group, prefer MP4 with highest TBR, or best video format with highest TBR
            groupedByResolution.forEach { (resKey, formatsInGroup) ->
                val bestMp4 = formatsInGroup.filter { it.optString("ext", "").lowercase() == "mp4" }
                    .maxByOrNull { fmt ->
                        val tbr = fmt.optDouble("tbr", 0.0)
                        if (tbr > 0) tbr else fmt.optDouble("vbr", 0.0)
                    }

                val bestFmt = bestMp4 ?: formatsInGroup.maxByOrNull { fmt ->
                    val tbr = fmt.optDouble("tbr", 0.0)
                    if (tbr > 0) tbr else fmt.optDouble("vbr", 0.0)
                }

                if (bestFmt != null) {
                    val fmtId = bestFmt.optString("format_id", "")
                    val h = bestFmt.optInt("height", 0)
                    val acodec = bestFmt.optString("acodec", "none")
                    val isVideoOnly = acodec == "none" || acodec == "null" || acodec.isBlank()
                    val tbr = bestFmt.optDouble("tbr", 0.0).let { if (it > 0) it else bestFmt.optDouble("vbr", 0.0) }

                    val vSize = bestFmt.optLong("filesize", 0L).let { if (it > 0) it else bestFmt.optLong("filesize_approx", 0L) }
                    val calculatedVSize = if (vSize > 0) {
                        vSize
                    } else if (duration > 0 && tbr > 0) {
                        (tbr * 1000.0 / 8.0 * duration).toLong()
                    } else 0L

                    val totalSizeBytes = if (isVideoOnly && calculatedVSize > 0 && calculatedAudioSize > 0) {
                        calculatedVSize + calculatedAudioSize
                    } else if (calculatedVSize > 0) {
                        calculatedVSize
                    } else {
                        0L
                    }

                    val resLabel = when {
                        h >= 1800 -> "2160p (4K)"
                        h >= 1200 -> "1440p (2K)"
                        h >= 900  -> "1080p"
                        h >= 600  -> "720p"
                        h >= 400  -> "480p"
                        h >= 300  -> "360p"
                        h >= 200  -> "240p"
                        h >= 100  -> "144p"
                        else -> resKey
                    }

                    val formatSelector = if (isVideoOnly) "$fmtId+$targetAudioId" else fmtId

                    videoFormats.add(
                        MediaFormat(
                            formatId = formatSelector,
                            extension = "mp4",
                            fileSizeBytes = totalSizeBytes,
                            resolution = resLabel,
                            formatNote = "MP4 Video ($resKey)",
                            bitrate = tbr,
                            hasVideo = true,
                            hasAudio = true,
                            isCombined = true
                        )
                    )
                }
            }

            // Ensure all standard quality tiers (144p to 4K) are present for YouTube videos
            val standardTiers = listOf(
                2160 to "2160p (4K)",
                1440 to "1440p (2K)",
                1080 to "1080p",
                720  to "720p",
                480  to "480p",
                360  to "360p",
                240  to "240p",
                144  to "144p"
            )

            standardTiers.forEach { (heightTier, label) ->
                val alreadyHasTier = videoFormats.any { fmt ->
                    val match = Regex("""(\d+)p|(\d+)x(\d+)""").find(fmt.resolution)
                    val h = match?.groupValues?.get(1)?.toIntOrNull()
                        ?: match?.groupValues?.get(3)?.toIntOrNull()
                        ?: 0
                    Math.abs(h - heightTier) <= 60
                }

                if (!alreadyHasTier) {
                    val selector = "bestvideo[height<=$heightTier]+bestaudio[ext=m4a]/bestvideo[height<=$heightTier]+$targetAudioId/bestvideo[height<=$heightTier]+bestaudio/best[height<=$heightTier]/best"

                    val matchingFmt = availableFormats.filter { fmt ->
                        val h = fmt.optInt("height", 0)
                        val vcodec = fmt.optString("vcodec", "none")
                        vcodec != "none" && vcodec != "null" && h in (heightTier - 120)..(heightTier + 120)
                    }.maxByOrNull { fmt ->
                        val tbr = fmt.optDouble("tbr", 0.0)
                        if (tbr > 0) tbr else fmt.optDouble("vbr", 0.0)
                    }

                    val vSize = if (matchingFmt != null) {
                        val fs = matchingFmt.optLong("filesize", 0L)
                        if (fs > 0) fs else matchingFmt.optLong("filesize_approx", 0L)
                    } else 0L

                    val defaultBitrate = when (heightTier) {
                        2160 -> 5000.0
                        1440 -> 2800.0
                        1080 -> 1600.0
                        720  -> 900.0
                        480  -> 500.0
                        360  -> 300.0
                        240  -> 180.0
                        else -> 100.0
                    }

                    val realTbr = matchingFmt?.optDouble("tbr", 0.0)?.let { if (it > 0) it else matchingFmt.optDouble("vbr", 0.0) } ?: 0.0
                    val estBitrate = if (realTbr > 0) realTbr else defaultBitrate

                    val calculatedVSize = if (vSize > 0) {
                        vSize
                    } else if (duration > 0) {
                        (estBitrate * 1000.0 / 8.0 * duration).toLong()
                    } else 0L

                    val estBytes = if (calculatedVSize > 0 && calculatedAudioSize > 0) {
                        calculatedVSize + calculatedAudioSize
                    } else if (calculatedVSize > 0) {
                        calculatedVSize
                    } else {
                        0L
                    }

                    videoFormats.add(
                        MediaFormat(
                            formatId = selector,
                            extension = "mp4",
                            fileSizeBytes = estBytes,
                            resolution = label,
                            formatNote = "MP4 Video ($label)",
                            bitrate = estBitrate,
                            hasVideo = true,
                            hasAudio = true,
                            isCombined = true
                        )
                    )
                }
            }

            // Sort video formats from highest height/resolution to lowest
            videoFormats.sortByDescending { fmt ->
                val match = Regex("""(\d+)p|(\d+)x(\d+)""").find(fmt.resolution)
                val h = match?.groupValues?.get(1)?.toIntOrNull()
                    ?: match?.groupValues?.get(3)?.toIntOrNull()
                    ?: 0
                h
            }

            // 3. Audio Formats: list all available audio formats
            val audioStreams = availableFormats.filter { fmt ->
                val vcodec = fmt.optString("vcodec", "none")
                val acodec = fmt.optString("acodec", "none")
                val ext = fmt.optString("ext", "").lowercase()
                val fmtId = fmt.optString("format_id", "").lowercase()
                (vcodec == "none" || vcodec == "null" || vcodec.isBlank()) &&
                        (acodec != "none" && acodec != "null" && acodec.isNotBlank()) &&
                        ext != "mhtml" && !fmtId.startsWith("sb")
            }

            audioStreams.forEach { fmt ->
                val fmtId = fmt.optString("format_id", "")
                val ext = fmt.optString("ext", "m4a")
                val abr = fmt.optDouble("tbr", 0.0).let { if (it > 0) it else fmt.optDouble("abr", 0.0) }

                var size = fmt.optLong("filesize", 0L).let { if (it > 0) it else fmt.optLong("filesize_approx", 0L) }
                if (size == 0L && duration > 0 && abr > 0) {
                    size = (abr * 1000.0 / 8.0 * duration).toLong()
                }

                val formatNote = fmt.optString("format_note", "").ifBlank { fmt.optString("acodec", "") }
                val label = "${ext.uppercase()} Audio ($fmtId)"

                audioFormats.add(
                    MediaFormat(
                        formatId = fmtId,
                        extension = ext,
                        fileSizeBytes = size,
                        resolution = label,
                        formatNote = if (formatNote.isNotBlank()) "$formatNote (${ext.uppercase()})" else "${ext.uppercase()} Audio",
                        bitrate = abr,
                        hasVideo = false,
                        hasAudio = true,
                        isCombined = false
                    )
                )
            }

            // Sort audio formats by bitrate descending
            audioFormats.sortByDescending { it.bitrate }
        } else if (videoHeights.size >= 2) {
            // Non-YouTube site with multiple video qualities
            val qualitySpecs = listOf(
                2160 to "4k",
                1440 to "2k",
                1080 to "1080p",
                720  to "720p",
                480  to "480p",
                360  to "360p",
                240  to "240p",
                144  to "144p"
            )

            // Audio stream size lookup for non-YouTube
            val audioFormat = availableFormats.find { fmt ->
                val vcodec = fmt.optString("vcodec", "none")
                val acodec = fmt.optString("acodec", "none")
                val fmtId = fmt.optString("format_id", "")
                fmtId == "140" || (vcodec == "none" && acodec != "none" && acodec != "null")
            }
            val audioSize = if (audioFormat != null) {
                val fs = audioFormat.optLong("filesize", 0L)
                if (fs > 0) fs else audioFormat.optLong("filesize_approx", 0L)
            } else 0L

            qualitySpecs.forEach { (height, label) ->
                val matchingHeights = videoHeights.filter { h ->
                    when (height) {
                        2160 -> h >= 1800
                        1440 -> h in 1200..1799
                        1080 -> h in 900..1199
                        720  -> h in 600..899
                        480  -> h in 400..599
                        360  -> h in 300..399
                        240  -> h in 200..299
                        144  -> h < 200
                        else -> false
                    }
                }

                if (matchingHeights.isNotEmpty()) {
                    val matchingFmt = availableFormats.find { fmt ->
                        val h = fmt.optInt("height", 0)
                        val vcodec = fmt.optString("vcodec", "none")
                        vcodec != "none" && vcodec != "null" && h in matchingHeights
                    }

                    val videoSize = if (matchingFmt != null) {
                        val fs = matchingFmt.optLong("filesize", 0L)
                        if (fs > 0) fs else matchingFmt.optLong("filesize_approx", 0L)
                    } else 0L

                    val totalSize = if (videoSize > 0 && audioSize > 0) {
                        videoSize + audioSize
                    } else if (videoSize > 0) {
                        videoSize
                    } else {
                        0L
                    }

                    val formatSelector = "bestvideo[height<=$height]+bestaudio/best[height<=$height]/best"

                    videoFormats.add(
                        MediaFormat(
                            formatId = formatSelector,
                            extension = "mp4",
                            fileSizeBytes = totalSize,
                            resolution = label,
                            formatNote = "MP4 Video ($label)",
                            bitrate = 0.0,
                            hasVideo = true,
                            hasAudio = true,
                            isCombined = true
                        )
                    )
                }
            }
        }

        // If no multi-resolution formats matched or single quality (e.g., non-YouTube, Instagram, TikTok, Twitter, Reddit)
        if (videoFormats.isEmpty()) {
            val bestVideoFmt = availableFormats.firstOrNull { fmt ->
                val vcodec = fmt.optString("vcodec", "none")
                vcodec != "none" && vcodec != "null"
            }
            val bestVideoSize = if (bestVideoFmt != null) {
                val fs = bestVideoFmt.optLong("filesize", 0L)
                if (fs > 0) fs else bestVideoFmt.optLong("filesize_approx", 0L)
            } else 0L

            val singleSize = if (bestVideoSize > 0) bestVideoSize else 0L

            videoFormats.add(
                MediaFormat(
                    formatId = "bestvideo+bestaudio/best",
                    extension = "mp4",
                    fileSizeBytes = singleSize,
                    resolution = "Original Quality",
                    formatNote = "MP4 Video (Original Quality)",
                    bitrate = 0.0,
                    hasVideo = true,
                    hasAudio = true,
                    isCombined = true
                )
            )
        }

        if (!isYouTube) {
            val audioFormat = availableFormats.find { fmt ->
                val vcodec = fmt.optString("vcodec", "none")
                val acodec = fmt.optString("acodec", "none")
                (vcodec == "none" || vcodec == "null") && acodec != "none" && acodec != "null"
            }
            val audioSize = if (audioFormat != null) {
                val fs = audioFormat.optLong("filesize", 0L)
                if (fs > 0) fs else audioFormat.optLong("filesize_approx", 0L)
            } else 0L

            audioFormats.add(
                MediaFormat(
                    formatId = "140/bestaudio[ext=m4a]/bestaudio/best",
                    extension = "m4a",
                    fileSizeBytes = audioSize,
                    resolution = "Audio (M4A)",
                    formatNote = "M4A Audio",
                    bitrate = 128.0,
                    hasVideo = false,
                    hasAudio = true,
                    isCombined = true
                )
            )

            audioFormats.add(
                MediaFormat(
                    formatId = "bestaudio/best",
                    extension = "mp3",
                    fileSizeBytes = audioSize,
                    resolution = "Audio (MP3)",
                    formatNote = "MP3 Audio",
                    bitrate = 160.0,
                    hasVideo = false,
                    hasAudio = true,
                    isCombined = true
                )
            )
        } else if (audioFormats.isEmpty()) {
            audioFormats.add(
                MediaFormat(
                    formatId = "140/bestaudio[ext=m4a]/bestaudio/best",
                    extension = "m4a",
                    fileSizeBytes = 0L,
                    resolution = "Audio (M4A)",
                    formatNote = "M4A Audio",
                    bitrate = 128.0,
                    hasVideo = false,
                    hasAudio = true,
                    isCombined = true
                )
            )
        }

        return VideoInfo(
            title = title,
            thumbnailUrl = thumbnail,
            durationSeconds = duration,
            videoFormats = videoFormats,
            audioFormats = audioFormats,
            isPlaylist = false,
            isRadio = false,
            playlistItems = emptyList()
        )
    }

    private fun generateStandardVideoFormats(duration: Long): List<MediaFormat> {
        val standardQualities = listOf(
            "2160p (4K)" to 2160,
            "1440p (2K)" to 1440,
            "1080p"      to 1080,
            "720p"       to 720,
            "480p"       to 480,
            "360p"       to 360,
            "240p"       to 240,
            "144p"       to 144
        )
        return standardQualities.map { (label, height) ->
            MediaFormat(
                formatId = "bestvideo[height=$height]+bestaudio[ext=m4a]/bestvideo[height<=$height]+bestaudio[ext=m4a]/bestvideo[height<=$height]+140/bestvideo[height<=$height]+bestaudio/best[height<=$height]/best",
                extension = "mp4",
                fileSizeBytes = 0L,
                resolution = label,
                formatNote = "MP4 Video ($label)",
                bitrate = 0.0,
                hasVideo = true,
                hasAudio = true,
                isCombined = true
            )
        }
    }

    private fun generateStandardAudioFormats(duration: Long): List<MediaFormat> {
        return listOf(
            MediaFormat(
                formatId = "140/bestaudio[ext=m4a]/bestaudio/best",
                extension = "m4a",
                fileSizeBytes = 0L,
                resolution = "Audio (M4A)",
                formatNote = "M4A Audio",
                bitrate = 128.0,
                hasVideo = false,
                hasAudio = true,
                isCombined = true
            ),
            MediaFormat(
                formatId = "bestaudio/best",
                extension = "mp3",
                fileSizeBytes = 0L,
                resolution = "Audio (MP3)",
                formatNote = "MP3 Audio",
                bitrate = 160.0,
                hasVideo = false,
                hasAudio = true,
                isCombined = true
            )
        )
    }
}