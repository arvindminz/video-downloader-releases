package com.example.videodownloader

import android.content.Context
import android.content.Intent
import android.util.Log
import android.widget.Toast
import androidx.annotation.OptIn
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import coil.compose.AsyncImage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(UnstableApi::class)
@Composable
fun VideoPlayerScreen(
    video: TrendingVideo,
    onBack: () -> Unit,
    onDownloadVideo: (String) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var activeVideo by remember(video) { mutableStateOf(video) }
    var streamUrl by remember(activeVideo) { mutableStateOf<String?>(null) }
    var isExtractingStream by remember(activeVideo) { mutableStateOf(true) }
    var isLiked by remember { mutableStateOf(false) }
    var isSaved by remember { mutableStateOf(false) }

    // ExoPlayer Instance
    val exoPlayer = remember(context) {
        ExoPlayer.Builder(context).build().apply {
            playWhenReady = true
            repeatMode = Player.REPEAT_MODE_OFF
        }
    }

    DisposableEffect(exoPlayer) {
        onDispose {
            exoPlayer.release()
        }
    }

    // Extract direct stream URL using yt-dlp in background
    LaunchedEffect(activeVideo) {
        isExtractingStream = true
        streamUrl = null
        withContext(Dispatchers.IO) {
            try {
                val request = com.yausername.youtubedl_android.YoutubeDLRequest(activeVideo.videoUrl).apply {
                    addOption("--dump-json")
                    addOption("--format", "best[ext=mp4]/best")
                    addOption("--no-warnings")
                    addOption("--socket-timeout", "6")
                    addOption("--extractor-args", "youtube:player_client=visionos,web")
                }
                val response = YoutubeDLUtils.execute(context, request)
                val firstLine = response.out.lineSequence().firstOrNull { it.contains("{") }
                if (firstLine != null) {
                    val json = org.json.JSONObject(firstLine)
                    val directUrl = json.optString("url", "").ifBlank {
                        val formatsArr = json.optJSONArray("formats")
                        if (formatsArr != null && formatsArr.length() > 0) {
                            formatsArr.getJSONObject(formatsArr.length() - 1).optString("url", "")
                        } else ""
                    }
                    if (directUrl.isNotBlank()) {
                        withContext(Dispatchers.Main) {
                            streamUrl = directUrl
                            isExtractingStream = false
                            exoPlayer.setMediaItem(MediaItem.fromUri(directUrl))
                            exoPlayer.prepare()
                            exoPlayer.play()
                        }
                    } else {
                        withContext(Dispatchers.Main) { isExtractingStream = false }
                    }
                } else {
                    withContext(Dispatchers.Main) { isExtractingStream = false }
                }
            } catch (e: Exception) {
                Log.w("VideoPlayerScreen", "Failed to extract playback stream URL", e)
                withContext(Dispatchers.Main) { isExtractingStream = false }
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        // ── Top Player Control Bar ──
        Surface(
            color = Color.Black,
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                IconButton(onClick = onBack) {
                    Text("∨", fontSize = 22.sp, color = Color.White, fontWeight = FontWeight.Bold)
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    // Auto-play Badge
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = Color.White.copy(alpha = 0.2f)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("🔵▶", fontSize = 10.sp, color = Color.White)
                        }
                    }

                    // CC Subtitles Badge
                    Surface(
                        shape = RoundedCornerShape(4.dp),
                        color = Color.White.copy(alpha = 0.25f)
                    ) {
                        Text(
                            text = "CC",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                        )
                    }

                    // Resolution Badge
                    Surface(
                        shape = RoundedCornerShape(4.dp),
                        color = Color.White.copy(alpha = 0.25f)
                    ) {
                        Text(
                            text = "360p",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium,
                            color = Color.White,
                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                        )
                    }

                    // Speed Badge
                    Surface(
                        shape = RoundedCornerShape(4.dp),
                        color = Color.White.copy(alpha = 0.25f)
                    ) {
                        Text(
                            text = "1x",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium,
                            color = Color.White,
                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                        )
                    }

                    // Audio Mode Icon 🎧
                    IconButton(
                        onClick = {
                            Toast.makeText(context, "Audio playback mode enabled", Toast.LENGTH_SHORT).show()
                        },
                        modifier = Modifier.size(32.dp)
                    ) {
                        Text("🎧", fontSize = 16.sp)
                    }
                }
            }
        }

        // ── Video Player Area (ExoPlayer) ──
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(230.dp)
                .background(Color.Black),
            contentAlignment = Alignment.Center
        ) {
            if (isExtractingStream) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator(color = Color.Red, strokeWidth = 3.dp)
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("Loading video stream...", color = Color.White, fontSize = 12.sp)
                }
            } else {
                AndroidView(
                    factory = { ctx ->
                        PlayerView(ctx).apply {
                            player = exoPlayer
                            useController = true
                            setShowNextButton(true)
                            setShowPreviousButton(false)
                        }
                    },
                    modifier = Modifier.fillMaxSize()
                )
            }
        }

        // ── Video Info & Creator Section ──
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(14.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.Top
                ) {
                    AsyncImage(
                        model = activeVideo.channelAvatarUrl,
                        contentDescription = activeVideo.channelName,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .size(42.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.surfaceVariant)
                    )

                    Spacer(modifier = Modifier.width(10.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = activeVideo.title,
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "${activeVideo.channelName} • ${activeVideo.viewsText} • ${activeVideo.uploadedAgo}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 12.sp
                        )
                    }

                    // Like / Dislike Pills
                    Surface(
                        shape = RoundedCornerShape(20.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                        modifier = Modifier.clickable { isLiked = !isLiked }
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(if (isLiked) "👍" else "👍", fontSize = 13.sp)
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("1119", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("|", fontSize = 12.sp, color = Color.Gray)
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("👎", fontSize = 13.sp)
                        }
                    }
                }
            }

            // ── Action Buttons Row (Save, Popup, Play Audio, Share, Download) ──
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    PlayerActionButton(
                        iconText = "➕",
                        label = if (isSaved) "Saved" else "Save",
                        iconColor = if (isSaved) Color.Red else MaterialTheme.colorScheme.onSurface,
                        onClick = {
                            isSaved = !isSaved
                            Toast.makeText(context, if (isSaved) "Saved to collection" else "Removed", Toast.LENGTH_SHORT).show()
                        }
                    )
                    PlayerActionButton(
                        iconText = "📺",
                        label = "Popup",
                        onClick = {
                            Toast.makeText(context, "Picture-in-Picture mode", Toast.LENGTH_SHORT).show()
                        }
                    )
                    PlayerActionButton(
                        iconText = "🎧",
                        label = "Play Audio",
                        onClick = {
                            Toast.makeText(context, "Playing audio in background", Toast.LENGTH_SHORT).show()
                        }
                    )
                    PlayerActionButton(
                        iconText = "💬",
                        label = "Share",
                        iconColor = Color(0xFF25D366),
                        onClick = {
                            val intent = Intent(Intent.ACTION_SEND).apply {
                                type = "text/plain"
                                putExtra(Intent.EXTRA_TEXT, activeVideo.videoUrl)
                            }
                            context.startActivity(Intent.createChooser(intent, "Share Video"))
                        }
                    )
                    PlayerActionButton(
                        iconText = "📥",
                        label = "Download",
                        iconColor = Color(0xFFFF2A4B),
                        onClick = {
                            onDownloadVideo(activeVideo.videoUrl)
                        }
                    )
                }
            }

            // ── Comments Card Section ──
            item {
                Card(
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text("Comments", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("· 13", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Text("⌥", fontSize = 14.sp, color = Color.Gray)
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Surface(
                                shape = CircleShape,
                                color = Color(0xFFE91E63),
                                modifier = Modifier.size(26.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Text("M", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                }
                            }
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(
                                text = "Singer ka naam kya hai yaar",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurface,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }

            // ── Suggested / Related Videos Header ──
            item {
                Text(
                    text = "Related Videos",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }

            // ── Suggested Videos List ──
            items(TrendingFeedRepository.defaultTrendingList) { related ->
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { activeVideo = related },
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .width(120.dp)
                                .height(68.dp)
                                .clip(RoundedCornerShape(8.dp))
                        ) {
                            AsyncImage(
                                model = related.thumbnailUrl,
                                contentDescription = related.title,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize()
                            )
                            Surface(
                                shape = RoundedCornerShape(4.dp),
                                color = Color.Black.copy(alpha = 0.85f),
                                modifier = Modifier
                                    .align(Alignment.BottomEnd)
                                    .padding(4.dp)
                            ) {
                                Text(
                                    text = related.durationText,
                                    color = Color.White,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.width(10.dp))

                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = related.title,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "${related.channelName} • ${related.viewsText}",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        IconButton(onClick = { onDownloadVideo(related.videoUrl) }) {
                            Text("📥", fontSize = 18.sp)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun PlayerActionButton(
    iconText: String,
    label: String,
    iconColor: Color = MaterialTheme.colorScheme.onSurface,
    onClick: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.clickable { onClick() }
    ) {
        Box(
            modifier = Modifier
                .size(42.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
            contentAlignment = Alignment.Center
        ) {
            Text(iconText, fontSize = 18.sp, color = iconColor)
        }
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = label,
            fontSize = 11.sp,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}
