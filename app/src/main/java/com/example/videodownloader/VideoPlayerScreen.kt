package com.example.videodownloader

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import android.widget.Toast
import androidx.annotation.OptIn
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Image
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
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import coil.compose.AsyncImage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(UnstableApi::class)
@Composable
fun VideoPlayerScreen(
    video: TrendingVideo? = null,
    localFilePath: String? = null,
    videoTitle: String? = null,
    onBack: () -> Unit,
    onDownloadVideo: (String) -> Unit = {}
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var activeVideo by remember(video) { mutableStateOf(video) }
    var streamUrl by remember { mutableStateOf<String?>(localFilePath) }
    var isExtractingStream by remember { mutableStateOf(localFilePath.isNullOrBlank() && activeVideo != null) }
    
    // Player controls states
    var isMuted by remember { mutableStateOf(false) }
    var isLooping by remember { mutableStateOf(false) }
    var isLocked by remember { mutableStateOf(false) }
    var currentSpeed by remember { mutableFloatStateOf(1.0f) }
    var currentResizeMode by remember { mutableIntStateOf(AspectRatioFrameLayout.RESIZE_MODE_FIT) }
    var showSpeedMenu by remember { mutableStateOf(false) }
    var isLiked by remember { mutableStateOf(false) }
    var isSaved by remember { mutableStateOf(false) }

    val displayTitle = remember(activeVideo, videoTitle, localFilePath) {
        localFilePath?.substringAfterLast("/") ?: videoTitle ?: activeVideo?.title ?: "Video Player"
    }

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

    // If local file path provided, play immediately
    LaunchedEffect(localFilePath) {
        if (!localFilePath.isNullOrBlank()) {
            isExtractingStream = false
            val uri = if (localFilePath.startsWith("content://") || localFilePath.startsWith("http")) {
                Uri.parse(localFilePath)
            } else {
                Uri.fromFile(java.io.File(localFilePath))
            }
            exoPlayer.setMediaItem(MediaItem.fromUri(uri))
            exoPlayer.prepare()
            exoPlayer.play()
        }
    }

    // Extract direct stream URL for online TrendingVideo using yt-dlp in background
    LaunchedEffect(activeVideo) {
        if (localFilePath.isNullOrBlank() && activeVideo != null) {
            isExtractingStream = true
            streamUrl = null
            withContext(Dispatchers.IO) {
                try {
                    val request = com.yausername.youtubedl_android.YoutubeDLRequest(activeVideo!!.videoUrl).apply {
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
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onBack) {
                        Text("∨", fontSize = 22.sp, color = Color.White, fontWeight = FontWeight.Bold)
                    }
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = displayTitle,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.widthIn(max = 160.dp)
                    )
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Lock Toggle 🔒
                    IconButton(
                        onClick = {
                            isLocked = !isLocked
                            Toast.makeText(context, if (isLocked) "Controls Locked 🔒" else "Controls Unlocked 🔓", Toast.LENGTH_SHORT).show()
                        },
                        modifier = Modifier.size(32.dp)
                    ) {
                        Text(if (isLocked) "🔒" else "🔓", fontSize = 15.sp)
                    }

                    // Loop Toggle 🔁
                    IconButton(
                        onClick = {
                            isLooping = !isLooping
                            exoPlayer.repeatMode = if (isLooping) Player.REPEAT_MODE_ONE else Player.REPEAT_MODE_OFF
                            Toast.makeText(context, if (isLooping) "Loop Enabled 🔁" else "Loop Off", Toast.LENGTH_SHORT).show()
                        },
                        modifier = Modifier.size(32.dp)
                    ) {
                        Text(if (isLooping) "🔁" else "🔂", fontSize = 15.sp)
                    }

                    // Aspect Ratio Resize Mode Toggle (Fit, Zoom, Stretch)
                    IconButton(
                        onClick = {
                            currentResizeMode = when (currentResizeMode) {
                                AspectRatioFrameLayout.RESIZE_MODE_FIT -> AspectRatioFrameLayout.RESIZE_MODE_ZOOM
                                AspectRatioFrameLayout.RESIZE_MODE_ZOOM -> AspectRatioFrameLayout.RESIZE_MODE_FILL
                                else -> AspectRatioFrameLayout.RESIZE_MODE_FIT
                            }
                            val modeLabel = when (currentResizeMode) {
                                AspectRatioFrameLayout.RESIZE_MODE_FIT -> "Fit to Screen"
                                AspectRatioFrameLayout.RESIZE_MODE_ZOOM -> "Crop / Fill"
                                else -> "Stretch Screen"
                            }
                            Toast.makeText(context, modeLabel, Toast.LENGTH_SHORT).show()
                        },
                        modifier = Modifier.size(32.dp)
                    ) {
                        Text("📺", fontSize = 15.sp)
                    }

                    // Speed Selector Menu Dropdown
                    Box {
                        Surface(
                            shape = RoundedCornerShape(4.dp),
                            color = Color.White.copy(alpha = 0.25f),
                            modifier = Modifier.clickable { showSpeedMenu = true }
                        ) {
                            Text(
                                text = "${currentSpeed}x",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }

                        DropdownMenu(
                            expanded = showSpeedMenu,
                            onDismissRequest = { showSpeedMenu = false }
                        ) {
                            listOf(0.25f, 0.5f, 0.75f, 1.0f, 1.25f, 1.5f, 2.0f).forEach { speed ->
                                DropdownMenuItem(
                                    text = { Text("${speed}x", fontWeight = if (speed == currentSpeed) FontWeight.Bold else FontWeight.Normal) },
                                    onClick = {
                                        currentSpeed = speed
                                        exoPlayer.playbackParameters = PlaybackParameters(speed)
                                        showSpeedMenu = false
                                    }
                                )
                            }
                        }
                    }

                    // Mute / Unmute Volume Toggle 🔇🔊
                    IconButton(
                        onClick = {
                            isMuted = !isMuted
                            exoPlayer.volume = if (isMuted) 0f else 1f
                        },
                        modifier = Modifier.size(32.dp)
                    ) {
                        Text(if (isMuted) "🔇" else "🔊", fontSize = 15.sp)
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
                            useController = !isLocked
                            resizeMode = currentResizeMode
                            setShowNextButton(false)
                            setShowPreviousButton(false)
                        }
                    },
                    update = { view ->
                        view.player = exoPlayer
                        view.useController = !isLocked
                        view.resizeMode = currentResizeMode
                    },
                    modifier = Modifier.fillMaxSize()
                )
            }
        }

        // ── Quick Seek Actions Row (-10s Rewind, Play/Pause, +10s Forward) ──
        Surface(
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = {
                    exoPlayer.seekTo((exoPlayer.currentPosition - 10000).coerceAtLeast(0))
                }) {
                    Text("⏪ -10s", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }

                IconButton(onClick = {
                    if (exoPlayer.isPlaying) exoPlayer.pause() else exoPlayer.play()
                }) {
                    Text(if (exoPlayer.isPlaying) "⏸ Pause" else "▶ Play", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                }

                IconButton(onClick = {
                    exoPlayer.seekTo((exoPlayer.currentPosition + 10000).coerceAtMost(exoPlayer.duration))
                }) {
                    Text("+10s ⏩", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
            }
        }

        // ── Related Videos & Info Section (If Online Video) ──
        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (activeVideo != null) {
                item {
                    Column {
                        Text(
                            text = activeVideo!!.title,
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "${activeVideo!!.channelName} • ${activeVideo!!.viewsText} • ${activeVideo!!.uploadedAgo}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 12.sp
                        )
                    }
                }

                // Action Buttons Row (Save, Share, Download)
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
                            iconRes = R.drawable.share,
                            label = "Share",
                            onClick = {
                                val intent = Intent(Intent.ACTION_SEND).apply {
                                    type = "text/plain"
                                    putExtra(Intent.EXTRA_TEXT, activeVideo!!.videoUrl)
                                }
                                context.startActivity(Intent.createChooser(intent, "Share Video"))
                            }
                        )
                        PlayerActionButton(
                            iconText = "📥",
                            label = "Download",
                            iconColor = Color(0xFFFF2A4B),
                            onClick = {
                                onDownloadVideo(activeVideo!!.videoUrl)
                            }
                        )
                    }
                }
            } else {
                item {
                    Card(
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text("🎬 Local Media File Playing", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(displayTitle, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun PlayerActionButton(
    iconText: String? = null,
    iconRes: Int? = null,
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
            if (iconRes != null) {
                Image(
                    painter = painterResource(id = iconRes),
                    contentDescription = label,
                    modifier = Modifier.size(20.dp)
                )
            } else if (iconText != null) {
                Text(iconText, fontSize = 18.sp, color = iconColor)
            }
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
