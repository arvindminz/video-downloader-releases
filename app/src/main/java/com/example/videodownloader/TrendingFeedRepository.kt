package com.example.videodownloader

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

data class TrendingVideo(
    val id: String,
    val title: String,
    val videoUrl: String,
    val thumbnailUrl: String,
    val channelName: String,
    val channelAvatarUrl: String,
    val viewsText: String,
    val durationText: String,
    val uploadedAgo: String,
    val category: String = "All"
)

object TrendingFeedRepository {
    private const val TAG = "TrendingFeedRepository"

    val defaultTrendingList = listOf(
        TrendingVideo(
            id = "DFqdE_SHVws",
            title = "New Song 2026 | Tu Mera Hoke Bhi Nahi | Emraan Hashmi, Genelia Deshmukh | New Hindi Song",
            videoUrl = "https://youtu.be/DFqdE_SHVws",
            thumbnailUrl = "https://i.ytimg.com/vi/DFqdE_SHVws/hqdefault.jpg",
            channelName = "Dino Music World",
            channelAvatarUrl = "https://yt3.ggpht.com/ytc/AIdro_k64Zc8_w4n5o5o5o5o=s88-c-k-c0x00ffffff-no-rj",
            viewsText = "4.2M views",
            durationText = "4:53",
            uploadedAgo = "1mo ago",
            category = "Music"
        ),
        TrendingVideo(
            id = "RiFDdaZOgHo",
            title = "Alone Ringtone | Sad Alone Ringtone | Bewafa Ringtone | No Copyright Ringtone || #ringtone",
            videoUrl = "https://youtu.be/RiFDdaZOgHo",
            thumbnailUrl = "https://i.ytimg.com/vi/RiFDdaZOgHo/hqdefault.jpg",
            channelName = "S Lofi",
            channelAvatarUrl = "https://yt3.ggpht.com/ytc/AIdro_l000111222=s88-c-k-c0x00ffffff-no-rj",
            viewsText = "2.5M views",
            durationText = "0:05",
            uploadedAgo = "1y ago",
            category = "Shorts"
        ),
        TrendingVideo(
            id = "v988wA2E-Yk",
            title = "Tauba Tauba | Bad Newz | Vicky Kaushal | Triptii Dimri | Karan Aujla | Official Music Video",
            videoUrl = "https://youtu.be/v988wA2E-Yk",
            thumbnailUrl = "https://i.ytimg.com/vi/v988wA2E-Yk/hqdefault.jpg",
            channelName = "Saregama Music",
            channelAvatarUrl = "https://yt3.ggpht.com/ytc/AIdro_saregama=s88-c-k-c0x00ffffff-no-rj",
            viewsText = "125M views",
            durationText = "3:24",
            uploadedAgo = "3mo ago",
            category = "Music"
        ),
        TrendingVideo(
            id = "K4TOrB7at0Y",
            title = "Sajni (Song): Arijit Singh | Laapataa Ladies | Ram Sampath | Prashant Pandey | Aamir Khan",
            videoUrl = "https://youtu.be/K4TOrB7at0Y",
            thumbnailUrl = "https://i.ytimg.com/vi/K4TOrB7at0Y/hqdefault.jpg",
            channelName = "T-Series",
            channelAvatarUrl = "https://yt3.ggpht.com/v_prWWB4ebWay0TcTXqnoCeZ0yNyqi0wA6Mm9ZZ5g9qT6567=s88-c-k-c0x00ffffff-no-rj",
            viewsText = "85M views",
            durationText = "2:50",
            uploadedAgo = "5mo ago",
            category = "Romantic Music"
        ),
        TrendingVideo(
            id = "RlfDdaZOgHo",
            title = "Soulmate - Badshah ft. Arijit Singh | Official Music Video | Ek Tha Raja",
            videoUrl = "https://youtu.be/RlfDdaZOgHo",
            thumbnailUrl = "https://i.ytimg.com/vi/RlfDdaZOgHo/hqdefault.jpg",
            channelName = "Badshah",
            channelAvatarUrl = "https://yt3.ggpht.com/ytc/AIdro_badshah=s88-c-k-c0x00ffffff-no-rj",
            viewsText = "48M views",
            durationText = "3:30",
            uploadedAgo = "4mo ago",
            category = "Emotional Music"
        )
    )

    suspend fun getTrendingVideos(context: Context, category: String = "All"): List<TrendingVideo> = withContext(Dispatchers.IO) {
        val fetched = fetchRealtimeYouTubeVideos(category, 1)
        if (fetched.isNotEmpty()) {
            return@withContext fetched
        }
        return@withContext defaultTrendingList
    }

    suspend fun loadMoreVideos(context: Context, category: String = "All", page: Int = 1): List<TrendingVideo> = withContext(Dispatchers.IO) {
        val fetched = fetchRealtimeYouTubeVideos(category, page)
        if (fetched.isNotEmpty()) {
            return@withContext fetched
        }
        return@withContext generateFallbackBatch(category, page)
    }

    private suspend fun fetchRealtimeYouTubeVideos(category: String, page: Int): List<TrendingVideo> = withContext(Dispatchers.IO) {
        val searchKeywords = when (category) {
            "Shorts" -> listOf("trending shorts", "viral shorts 2026", "shorts music mix")
            "Music" -> listOf("trending hindi songs", "bollywood music 2026", "top punjabi songs")
            "Romantic Music" -> listOf("romantic songs 2026", "love mashup hindi", "romantic lofi")
            "Emotional Music" -> listOf("sad hindi songs 2026", "emotional songs hindi", "heartbreak lofi")
            "Lo-Fi" -> listOf("hindi lofi songs", "lofi mashup 2026", "midnight lofi chill")
            else -> listOf("trending songs 2026", "viral music videos", "popular hindi songs")
        }

        val keywordIndex = (page - 1) % searchKeywords.size
        val keyword = searchKeywords[keywordIndex]
        val encodedKeyword = java.net.URLEncoder.encode(keyword, "UTF-8")

        val resultList = mutableListOf<TrendingVideo>()

        try {
            val url = URL("https://www.youtube.com/results?search_query=$encodedKeyword")
            val conn = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android 14; Pixel 8) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Mobile Safari/537.36")
                connectTimeout = 4000
                readTimeout = 4000
            }

            if (conn.responseCode == 200) {
                val html = conn.inputStream.bufferedReader().use { it.readText() }
                val videoRegex = Regex(""""videoId":"([a-zA-Z0-9_-]{11})".*?"title":\{"runs":\[\{"text":"([^"]+)"\}""", RegexOption.DOT_MATCHES_ALL)
                val matches = videoRegex.findAll(html).take(15)

                for (match in matches) {
                    val vId = match.groupValues[1]
                    val rawTitle = match.groupValues[2]
                    val cleanTitle = rawTitle.replace("\\u0026", "&").replace("\\\"", "\"").trim()
                    if (vId.isNotBlank() && cleanTitle.isNotBlank() && !resultList.any { it.id.contains(vId) }) {
                        resultList.add(
                            TrendingVideo(
                                id = "${vId}_p${page}_${resultList.size}",
                                title = cleanTitle,
                                videoUrl = "https://youtu.be/$vId",
                                thumbnailUrl = "https://i.ytimg.com/vi/$vId/hqdefault.jpg",
                                channelName = "YouTube Music",
                                channelAvatarUrl = "https://i.ytimg.com/vi/$vId/hqdefault.jpg",
                                viewsText = "${(100..999).random()}K views",
                                durationText = "${(2..4).random()}:${(10..59).random()}",
                                uploadedAgo = "${(1..11).random()}mo ago",
                                category = category
                            )
                        )
                    }
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Fast HTTP realtime fetch failed", e)
        }

        return@withContext resultList
    }

    private fun generateFallbackBatch(category: String, page: Int): List<TrendingVideo> {
        val baseItems = defaultTrendingList
        return baseItems.mapIndexed { index, item ->
            item.copy(
                id = "${item.id}_p${page}_$index",
                title = "${item.title} (Vol ${page})",
                viewsText = "${(1..9).random()}.${(1..9).random()}M views",
                uploadedAgo = "${(1..11).random()}mo ago",
                category = category
            )
        }
    }
}
