package com.example.videodownloader

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class YoutubeDLUtilsTest {

    @Test
    fun testYouTubeFormatSelection_highestTbrMp4AndAudio140() {
        val sampleJson = JSONObject().apply {
            put("title", "Test YouTube Video")
            put("duration", 200)
            put("extractor", "youtube")
            put("webpage_url", "https://www.youtube.com/watch?v=RiFDdaZOgHo")

            val formatsArray = JSONArray()

            // Audio Formats
            formatsArray.put(JSONObject().apply {
                put("format_id", "139")
                put("ext", "m4a")
                put("vcodec", "none")
                put("acodec", "mp4a.40.5")
                put("tbr", 49.0)
                put("filesize", 1866465L)
            })
            formatsArray.put(JSONObject().apply {
                put("format_id", "249")
                put("ext", "webm")
                put("vcodec", "none")
                put("acodec", "opus")
                put("tbr", 54.0)
                put("filesize", 2055208L)
            })
            formatsArray.put(JSONObject().apply {
                put("format_id", "250")
                put("ext", "webm")
                put("vcodec", "none")
                put("acodec", "opus")
                put("tbr", 71.0)
                put("filesize", 2726297L)
            })
            formatsArray.put(JSONObject().apply {
                put("format_id", "140")
                put("ext", "m4a")
                put("vcodec", "none")
                put("acodec", "mp4a.40.2")
                put("tbr", 129.0)
                put("filesize", 4960000L)
            })
            formatsArray.put(JSONObject().apply {
                put("format_id", "251")
                put("ext", "webm")
                put("vcodec", "none")
                put("acodec", "opus")
                put("tbr", 139.0)
                put("filesize", 5326766L)
            })

            // Video Formats for 256x136
            formatsArray.put(JSONObject().apply {
                put("format_id", "602")
                put("ext", "mp4")
                put("vcodec", "vp09.00.10.08")
                put("acodec", "none")
                put("width", 256)
                put("height", 136)
                put("tbr", 100.0)
                put("filesize_approx", 3848294L)
            })
            formatsArray.put(JSONObject().apply {
                put("format_id", "269")
                put("ext", "mp4")
                put("vcodec", "avc1.4D400C")
                put("acodec", "none")
                put("width", 256)
                put("height", 136)
                put("tbr", 172.0)
                put("filesize_approx", 6585057L)
            })
            formatsArray.put(JSONObject().apply {
                put("format_id", "160")
                put("ext", "mp4")
                put("vcodec", "avc1.4D400D")
                put("acodec", "none")
                put("width", 256)
                put("height", 136)
                put("tbr", 103.0)
                put("filesize", 3932160L)
            })
            formatsArray.put(JSONObject().apply {
                put("format_id", "603")
                put("ext", "mp4")
                put("vcodec", "vp09.00.11.08")
                put("acodec", "none")
                put("width", 256)
                put("height", 136)
                put("tbr", 295.0)
                put("filesize_approx", 11314135L)
            })
            formatsArray.put(JSONObject().apply {
                put("format_id", "278")
                put("ext", "webm")
                put("vcodec", "vp9")
                put("acodec", "none")
                put("width", 256)
                put("height", 136)
                put("tbr", 95.0)
                put("filesize", 3649044L)
            })
            formatsArray.put(JSONObject().apply {
                put("format_id", "394")
                put("ext", "mp4")
                put("vcodec", "av01.0.00M.08")
                put("acodec", "none")
                put("width", 256)
                put("height", 136)
                put("tbr", 77.0)
                put("filesize", 2967470L)
            })

            // Video Formats for 1920x1012 (1080p)
            formatsArray.put(JSONObject().apply {
                put("format_id", "137")
                put("ext", "mp4")
                put("vcodec", "avc1.640028")
                put("acodec", "none")
                put("width", 1920)
                put("height", 1012)
                put("tbr", 3342.0)
                put("filesize", 128071000L)
            })
            formatsArray.put(JSONObject().apply {
                put("format_id", "614")
                put("ext", "mp4")
                put("vcodec", "vp09.00.40.08")
                put("acodec", "none")
                put("width", 1920)
                put("height", 1012)
                put("tbr", 3319.0)
                put("filesize_approx", 127370000L)
            })

            put("formats", formatsArray)
        }

        val videoInfo = YoutubeDLUtils.parseResponseToVideoInfo("https://youtu.be/RiFDdaZOgHo", sampleJson.toString())

        assertEquals("Test YouTube Video", videoInfo.title)
        assertFalse(videoInfo.isPlaylist)

        // Verify Video Formats
        val videoFormats = videoInfo.videoFormats
        assertTrue(videoFormats.isNotEmpty())

        // Find resolution 256x136
        val fmt256x136 = videoFormats.find { it.resolution.contains("256x136") || it.formatNote.contains("256x136") }
        assertNotNull("Format for 256x136 should exist", fmt256x136)

        // Rule check 1: Highest TBR mp4 for 256x136 is 603 (295k)
        // Rule check 2: Merged audio ID is 140 -> formatId should be "603+140"
        assertEquals("603+140", fmt256x136!!.formatId)
        assertEquals("mp4", fmt256x136.extension)
        assertEquals(295.0, fmt256x136.bitrate, 0.01)

        // Expected combined size = video 603 (11314135) + audio 140 (4960000) = 16274135
        assertEquals(16274135L, fmt256x136.fileSizeBytes)

        // Find resolution 1080p (1920x1012)
        val fmt1080p = videoFormats.find { it.resolution.contains("1080p") || it.formatNote.contains("1920x1012") }
        assertNotNull("Format for 1080p / 1920x1012 should exist", fmt1080p)
        // Highest TBR for 1920x1012 is 137 (3342.0) vs 614 (3319.0)
        assertEquals("137+140", fmt1080p!!.formatId)
        assertEquals("mp4", fmt1080p.extension)
        assertEquals(3342.0, fmt1080p.bitrate, 0.01)

        // Verify Audio Formats
        val audioFormats = videoInfo.audioFormats
        assertTrue("Audio formats list should contain all available audio streams", audioFormats.size >= 5)

        val audio140 = audioFormats.find { it.formatId == "140" }
        assertNotNull("Audio format 140 should be listed", audio140)
        assertEquals("m4a", audio140!!.extension)
        assertEquals(129.0, audio140.bitrate, 0.01)
        assertEquals(4960000L, audio140.fileSizeBytes)

        val audio251 = audioFormats.find { it.formatId == "251" }
        assertNotNull("Audio format 251 should be listed", audio251)
        assertEquals("webm", audio251!!.extension)
        assertEquals(139.0, audio251.bitrate, 0.01)
        assertEquals(5326766L, audio251.fileSizeBytes)
    }

    @Test
    fun testYouTube4KAnd2KFormatExtraction() {
        val sampleJson = JSONObject().apply {
            put("title", "4K Video Test")
            put("duration", 307)
            put("extractor", "youtube")
            put("webpage_url", "https://www.youtube.com/watch?v=RlfDdaZOgHo")

            val formatsArray = JSONArray()

            // Audio format 140
            formatsArray.put(JSONObject().apply {
                put("format_id", "140")
                put("ext", "m4a")
                put("vcodec", "none")
                put("acodec", "mp4a.40.2")
                put("tbr", 128.0)
                put("filesize", 4915200L)
            })

            // 4K video (3840x2160) - VP9 format 313
            formatsArray.put(JSONObject().apply {
                put("format_id", "313")
                put("ext", "webm")
                put("vcodec", "vp9")
                put("acodec", "none")
                put("width", 3840)
                put("height", 2160)
                put("tbr", 11500.0)
                put("filesize", 441000000L)
            })

            // 2K video (2560x1440) - VP9 format 271
            formatsArray.put(JSONObject().apply {
                put("format_id", "271")
                put("ext", "webm")
                put("vcodec", "vp9")
                put("acodec", "none")
                put("width", 2560)
                put("height", 1440)
                put("tbr", 5800.0)
                put("filesize", 222000000L)
            })

            // 1080p video (1920x1080) - AVC format 137
            formatsArray.put(JSONObject().apply {
                put("format_id", "137")
                put("ext", "mp4")
                put("vcodec", "avc1.640028")
                put("acodec", "none")
                put("width", 1920)
                put("height", 1080)
                put("tbr", 3000.0)
                put("filesize", 115000000L)
            })

            put("formats", formatsArray)
        }

        val videoInfo = YoutubeDLUtils.parseResponseToVideoInfo("https://youtu.be/RlfDdaZOgHo", sampleJson.toString())
        val videoFormats = videoInfo.videoFormats

        val fmt4k = videoFormats.find { it.resolution == "2160p (4K)" }
        assertNotNull("4K format should exist", fmt4k)
        assertEquals("313+140", fmt4k!!.formatId)
        assertTrue("4K size should reflect total video + audio size (~445MB)", fmt4k.fileSizeBytes > 440_000_000L)

        val fmt2k = videoFormats.find { it.resolution == "1440p (2K)" }
        assertNotNull("2K format should exist", fmt2k)
        assertEquals("271+140", fmt2k!!.formatId)

        val fmt1080p = videoFormats.find { it.resolution == "1080p" }
        assertNotNull("1080p format should exist", fmt1080p)
        assertEquals("137+140", fmt1080p!!.formatId)
    }

    @Test
    fun testStandardTiersFallbackSelectors() {
        val emptyFormatsJson = JSONObject().apply {
            put("title", "Fallback Test")
            put("duration", 300)
            put("extractor", "youtube")
            put("webpage_url", "https://www.youtube.com/watch?v=RlfDdaZOgHo")
            put("formats", JSONArray())
        }

        val videoInfo = YoutubeDLUtils.parseResponseToVideoInfo("https://youtu.be/RlfDdaZOgHo", emptyFormatsJson.toString())
        val videoFormats = videoInfo.videoFormats

        val fmt4k = videoFormats.find { it.resolution == "2160p (4K)" }
        assertNotNull("Standard 4K tier should be generated", fmt4k)
        assertTrue("Selector should include bestvideo[height<=2160]", fmt4k!!.formatId.contains("bestvideo[height<=2160]"))
        assertFalse("Selector should NOT contain rigid min height constraint [height>", fmt4k.formatId.contains("[height>"))
    }
}
