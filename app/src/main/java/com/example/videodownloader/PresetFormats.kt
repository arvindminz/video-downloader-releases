package com.example.videodownloader

object PresetFormats {
    fun isYouTubeUrl(url: String): Boolean {
        val lower = url.lowercase()
        return lower.contains("youtube.com") || lower.contains("youtu.be")
    }

    fun isDedicatedPlaylistUrl(url: String): Boolean {
        val lower = url.lowercase()
        val containsList = lower.contains("list=") || lower.contains("playlist")
        if (!containsList) return false

        val isPlaylistPath = lower.contains("/playlist")
        val hasVideoId = lower.contains("v=") || Regex("""youtu\.be/[a-zA-Z0-9_-]{11}""", RegexOption.IGNORE_CASE).containsMatchIn(url)

        return isPlaylistPath || !hasVideoId
    }

    fun parseDomain(url: String): String {
        val lower = url.lowercase()
        return when {
            lower.contains("instagram.com") || lower.contains("instagr.am") -> "Instagram"
            lower.contains("tiktok.com") -> "TikTok"
            lower.contains("twitter.com") || lower.contains("x.com") -> "Twitter"
            lower.contains("facebook.com") || lower.contains("fb.watch") -> "Facebook"
            lower.contains("reddit.com") || lower.contains("redd.it") -> "Reddit"
            lower.contains("pinterest.com") -> "Pinterest"
            lower.contains("vimeo.com") -> "Vimeo"
            else -> "Shared"
        }
    }

    fun getPresetVideoInfo(url: String): VideoInfo {
        val isYouTube = isYouTubeUrl(url)
        val videoFormats = if (isYouTube) {
            listOf(
                MediaFormat("bestvideo[height=2160]+bestaudio[ext=m4a]/bestvideo[height<=2160]+bestaudio[ext=m4a]/bestvideo[height<=2160]+140/best[height<=2160]/best", "mp4", 0L, "2160p (4K)", "MP4 Video (4K)", 0.0, hasVideo = true, hasAudio = true, isCombined = true),
                MediaFormat("bestvideo[height=1440]+bestaudio[ext=m4a]/bestvideo[height<=1440]+bestaudio[ext=m4a]/bestvideo[height<=1440]+140/best[height<=1440]/best", "mp4", 0L, "1440p (2K)", "MP4 Video (2K)", 0.0, hasVideo = true, hasAudio = true, isCombined = true),
                MediaFormat("bestvideo[height=1080]+bestaudio[ext=m4a]/bestvideo[height<=1080]+bestaudio[ext=m4a]/bestvideo[height<=1080]+140/best[height<=1080]/best", "mp4", 0L, "1080p", "MP4 Video (1080p)", 0.0, hasVideo = true, hasAudio = true, isCombined = true),
                MediaFormat("bestvideo[height=720]+bestaudio[ext=m4a]/bestvideo[height<=720]+bestaudio[ext=m4a]/bestvideo[height<=720]+140/best[height<=720]/best", "mp4", 0L, "720p", "MP4 Video (720p)", 0.0, hasVideo = true, hasAudio = true, isCombined = true),
                MediaFormat("bestvideo[height=480]+bestaudio[ext=m4a]/bestvideo[height<=480]+bestaudio[ext=m4a]/bestvideo[height<=480]+140/best[height<=480]/best", "mp4", 0L, "480p", "MP4 Video (480p)", 0.0, hasVideo = true, hasAudio = true, isCombined = true),
                MediaFormat("bestvideo[height=360]+bestaudio[ext=m4a]/bestvideo[height<=360]+bestaudio[ext=m4a]/bestvideo[height<=360]+140/best[height<=360]/best", "mp4", 0L, "360p", "MP4 Video (360p)", 0.0, hasVideo = true, hasAudio = true, isCombined = true),
                MediaFormat("bestvideo[height=240]+bestaudio[ext=m4a]/bestvideo[height<=240]+bestaudio[ext=m4a]/bestvideo[height<=240]+140/best[height<=240]/best", "mp4", 0L, "240p", "MP4 Video (240p)", 0.0, hasVideo = true, hasAudio = true, isCombined = true),
                MediaFormat("bestvideo[height=144]+bestaudio[ext=m4a]/bestvideo[height<=144]+bestaudio[ext=m4a]/bestvideo[height<=144]+140/best[height<=144]/best", "mp4", 0L, "144p", "MP4 Video (144p)", 0.0, hasVideo = true, hasAudio = true, isCombined = true)
            )
        } else {
            listOf(
                MediaFormat("bestvideo+bestaudio/best", "mp4", 0L, "Best Quality", "MP4 Video (Best)", 0.0, hasVideo = true, hasAudio = true, isCombined = true),
                MediaFormat("bestvideo[height<=720]+bestaudio/best[height<=720]/best", "mp4", 0L, "Medium Quality (720p)", "MP4 Video (720p)", 0.0, hasVideo = true, hasAudio = true, isCombined = true),
                MediaFormat("bestvideo[height<=480]+bestaudio/best[height<=480]/best", "mp4", 0L, "Low Quality (480p)", "MP4 Video (480p)", 0.0, hasVideo = true, hasAudio = true, isCombined = true)
            )
        }

        val audioFormats = listOf(
            MediaFormat("140/bestaudio[ext=m4a]/bestaudio/best", "m4a", 0L, "Audio (M4A)", "M4A Audio", 128.0, hasVideo = false, hasAudio = true, isCombined = true),
            MediaFormat("bestaudio/best", "mp3", 0L, "Audio (MP3)", "MP3 Audio", 160.0, hasVideo = false, hasAudio = true, isCombined = true)
        )

        val siteLabel = if (isYouTube) "YouTube Video" else "${parseDomain(url)} Video"

        return VideoInfo(
            title = siteLabel,
            thumbnailUrl = "",
            durationSeconds = 0L,
            videoFormats = videoFormats,
            audioFormats = audioFormats
        )
    }
}
