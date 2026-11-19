package com.example.videodownloader

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import coil.compose.AsyncImage

@Composable
fun QueueScreen(
    onNavigateToDownloads: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val downloadItems by DownloadRepository.downloadItems.collectAsState()

    val queueItems = remember(downloadItems) {
        downloadItems
            .filter { it.status != DownloadStatus.COMPLETED }
            .sortedWith(
                compareBy<DownloadItem> { item ->
                    when (item.status) {
                        DownloadStatus.DOWNLOADING, DownloadStatus.MERGING -> 0
                        DownloadStatus.PROCESSING, DownloadStatus.QUEUED -> 1
                        DownloadStatus.PENDING -> 2
                        DownloadStatus.PAUSED -> 3
                        DownloadStatus.FAILED -> 4
                        DownloadStatus.CANCELLED -> 5
                        DownloadStatus.COMPLETED -> 6
                    }
                }.thenByDescending { it.timestamp }
            )
    }

    val downloadingCount = queueItems.count {
        it.status == DownloadStatus.DOWNLOADING || it.status == DownloadStatus.MERGING
    }
    val readyCount = queueItems.count {
        it.status == DownloadStatus.PROCESSING || it.status == DownloadStatus.QUEUED
    }
    val pendingCount = queueItems.count {
        it.status == DownloadStatus.PENDING
    }

    val subtitleText = when {
        queueItems.isNotEmpty() -> "$downloadingCount downloading, $readyCount ready, $pendingCount waiting"
        else -> "No items in queue"
    }

    val isAnyActive = downloadingCount > 0 || readyCount > 0 || pendingCount > 0
    val isAnyPausable = queueItems.any { it.status == DownloadStatus.PAUSED }

    val isAnyCancellable = queueItems.any {
        it.status == DownloadStatus.DOWNLOADING ||
                it.status == DownloadStatus.MERGING ||
                it.status == DownloadStatus.PROCESSING ||
                it.status == DownloadStatus.QUEUED ||
                it.status == DownloadStatus.PENDING ||
                it.status == DownloadStatus.PAUSED
    }

    val hasCancelledItems = queueItems.any {
        it.status == DownloadStatus.CANCELLED || it.status == DownloadStatus.FAILED
    }

    Column(modifier = modifier.fillMaxSize().padding(horizontal = 10.dp, vertical = 6.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Download Queue",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp
                )
                Text(
                    text = subtitleText,
                    style = MaterialTheme.typography.bodySmall,
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        Spacer(modifier = Modifier.height(6.dp))

        if (isAnyActive || isAnyPausable || isAnyCancellable || hasCancelledItems) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (isAnyActive) {
                        FilledTonalButton(
                            onClick = { DownloadRepository.pauseAll(context) },
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                            modifier = Modifier.height(28.dp)
                        ) {
                            Text("⏸ Pause", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                    } else if (isAnyPausable) {
                        Button(
                            onClick = { DownloadRepository.resumeAll(context) },
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                            modifier = Modifier.height(28.dp)
                        ) {
                            Text("▶ Resume", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                    }

                    if (isAnyCancellable) {
                        OutlinedButton(
                            onClick = { DownloadRepository.cancelAll(context) },
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                            modifier = Modifier.height(28.dp),
                            colors = ButtonDefaults.outlinedButtonColors(
                                contentColor = MaterialTheme.colorScheme.error
                            )
                        ) {
                            Text("⏹ Cancel All", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                    }

                    if (hasCancelledItems) {
                        TextButton(
                            onClick = { DownloadRepository.clearCancelledAndFailed(context) },
                            contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp),
                            modifier = Modifier.height(28.dp)
                        ) {
                            Text("Clear", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
            Spacer(modifier = Modifier.height(6.dp))
        }

        if (queueItems.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "No items in active queue",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Fetch a video link in the Home tab to start downloading.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Button(
                        onClick = onNavigateToDownloads,
                        shape = RoundedCornerShape(20.dp)
                    ) {
                        Text("Go to downloads", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    }
                }
            }
        } else {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                items(queueItems, key = { it.id }) { item ->
                    QueueItemCard(
                        item = item,
                        onPause = { DownloadRepository.pauseDownload(context, item.id) },
                        onResume = { DownloadRepository.resumeDownload(context, item.id) },
                        onCancel = { DownloadRepository.cancelDownload(context, item.id) },
                        onRetry = { DownloadRepository.retryDownload(context, item.id) },
                        onRemove = { DownloadRepository.removeItem(context, item.id) }
                    )
                }
            }
        }
    }
}

@Composable
fun ItemThumbnailPreview(
    item: DownloadItem,
    modifier: Modifier = Modifier
) {
    val isApk = remember(item) { item.extension.equals("apk", ignoreCase = true) }

    if (isApk) {
        ApkThumbnailLogo(modifier = modifier.width(100.dp).height(60.dp))
        return
    }

    val thumbnailUrl = remember(item) {
        if (item.thumbnailUrl.isNotBlank()) item.thumbnailUrl else deriveYouTubeThumbnail(item.url)
    }

    val resolutionText = remember(item) {
        if (item.resolutionLabel.isNotBlank()) item.resolutionLabel else {
            if (item.isAudioOnly) "AUDIO"
            else if (item.formatId.contains("2160") || item.formatId.contains("4k", ignoreCase = true)) "2K HD"
            else if (item.formatId.contains("1440") || item.formatId.contains("2k", ignoreCase = true)) "2K HD"
            else if (item.formatId.contains("1080")) "1080P HD"
            else if (item.formatId.contains("720")) "720P HD"
            else if (item.formatId.contains("480")) "480P"
            else if (item.formatId.contains("360")) "360P"
            else if (item.formatId.contains("240")) "240P"
            else if (item.formatId.contains("144")) "144P"
            else "HD"
        }
    }

    val durationText = remember(item) {
        if (item.durationSeconds > 0) formatDuration(item.durationSeconds) else "00:30"
    }

    val isYouTube = remember(item) { item.url.contains("youtu", ignoreCase = true) }

    Box(
        modifier = modifier
            .width(100.dp)
            .height(60.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
    ) {
        if (thumbnailUrl.isNotBlank()) {
            AsyncImage(
                model = thumbnailUrl,
                contentDescription = item.title,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        } else {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = if (item.isAudioOnly) "🎵" else "🎬",
                    fontSize = 20.sp
                )
            }
        }

        // Top-Right: CC Subtitle Badge (if YouTube video)
        if (isYouTube && !item.isAudioOnly) {
            Surface(
                shape = RoundedCornerShape(topEnd = 8.dp, bottomStart = 5.dp),
                color = Color(0xFFAB47BC), // Purple CC badge
                modifier = Modifier.align(Alignment.TopEnd)
            ) {
                Text(
                    text = "CC",
                    color = Color.White,
                    fontSize = 8.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 3.dp, vertical = 0.5.dp)
                )
            }
        }

        // Bottom-Left: Quality Resolution Badge (2K HD, 1080P HD, 360P, etc.)
        if (resolutionText.isNotBlank()) {
            Surface(
                shape = RoundedCornerShape(bottomStart = 8.dp, topEnd = 5.dp),
                color = Color.Black.copy(alpha = 0.85f),
                modifier = Modifier.align(Alignment.BottomStart)
            ) {
                Text(
                    text = resolutionText,
                    color = if (resolutionText.contains("HD")) Color(0xFFFFD700) else Color.White,
                    fontSize = 8.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 0.5.dp)
                )
            }
        }

        // Bottom-Right: Duration Badge (Only for Videos/Audio, HIDDEN for APK!)
        if (!isApk && durationText.isNotBlank()) {
            Surface(
                shape = RoundedCornerShape(bottomEnd = 8.dp, topStart = 5.dp),
                color = Color.Black.copy(alpha = 0.85f),
                modifier = Modifier.align(Alignment.BottomEnd)
            ) {
                Text(
                    text = durationText,
                    color = Color.White,
                    fontSize = 8.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 3.dp, vertical = 0.5.dp)
                )
            }
        }
    }
}

@Composable
fun QueueItemCard(
    item: DownloadItem,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onCancel: () -> Unit,
    onRetry: () -> Unit,
    onRemove: () -> Unit
) {
    var showMenu by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Left: Thumbnail Container (100.dp x 60.dp)
                ItemThumbnailPreview(item = item)

                Spacer(modifier = Modifier.width(8.dp))

                // Right: Title + Details + Speed & Status Line
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = item.title,
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )

                    Spacer(modifier = Modifier.height(3.dp))

                    // Line 2: Details Line (▶/📦 4.3 MB / 39.3 MB | MP4 | 2026-11-18)
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(3.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = when {
                                item.extension.equals("apk", ignoreCase = true) -> "📦"
                                item.isAudioOnly -> "🎵"
                                else -> "▶"
                            },
                            fontSize = 10.sp,
                            color = Color.Gray
                        )

                        val sizeText = when {
                            item.fileSizeBytes > 0 && item.downloadedBytes > 0 -> {
                                "${formatFileSize(item.downloadedBytes)} / ${formatFileSize(item.fileSizeBytes)}"
                            }
                            item.fileSizeBytes > 0 -> formatFileSize(item.fileSizeBytes)
                            else -> "0 MB"
                        }
                        Text(
                            text = sizeText,
                            fontSize = 10.sp,
                            color = Color.Gray,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )

                        Text("|", fontSize = 10.sp, color = Color.LightGray)

                        Text(
                            text = item.extension.uppercase(),
                            fontSize = 10.sp,
                            color = Color.Gray,
                            fontWeight = FontWeight.Bold
                        )

                        Text("|", fontSize = 10.sp, color = Color.LightGray)

                        Text(
                            text = formatItemDate(item.timestamp),
                            fontSize = 10.sp,
                            color = Color.Gray,
                            maxLines = 1
                        )
                    }

                    Spacer(modifier = Modifier.height(3.dp))

                    // Line 3: Compact Status Badge & Speed Metric Inline
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        StatusBadge(status = item.status)

                        val speedStatusText = when (item.status) {
                            DownloadStatus.DOWNLOADING, DownloadStatus.MERGING -> "${item.downloadSpeed} • ${item.progress}%"
                            DownloadStatus.PAUSED -> "Paused"
                            DownloadStatus.FAILED -> item.errorMessage ?: "Failed"
                            else -> item.downloadSpeed
                        }
                        Text(
                            text = speedStatusText,
                            fontSize = 10.sp,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                Spacer(modifier = Modifier.width(4.dp))

                // 3-dots Menu Button
                Box {
                    IconButton(onClick = { showMenu = true }, modifier = Modifier.size(28.dp)) {
                        Text("⋮", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Color.Gray)
                    }
                    DropdownMenu(
                        expanded = showMenu,
                        onDismissRequest = { showMenu = false }
                    ) {
                        if (item.status == DownloadStatus.DOWNLOADING || item.status == DownloadStatus.MERGING) {
                            DropdownMenuItem(
                                text = { Text("Pause Download") },
                                onClick = { showMenu = false; onPause() }
                            )
                        } else if (item.status == DownloadStatus.PAUSED) {
                            DropdownMenuItem(
                                text = { Text("Resume Download") },
                                onClick = { showMenu = false; onResume() }
                            )
                        } else if (item.status == DownloadStatus.FAILED) {
                            DropdownMenuItem(
                                text = { Text("Retry Download") },
                                onClick = { showMenu = false; onRetry() }
                            )
                        }
                        DropdownMenuItem(
                            text = { Text("Cancel Download") },
                            onClick = { showMenu = false; onCancel() }
                        )
                        DropdownMenuItem(
                            text = { Text("Remove from List") },
                            onClick = { showMenu = false; onRemove() }
                        )
                    }
                }
            }

            // Chunk-wise Segmented Progress Indicator at bottom of card
            if (item.status == DownloadStatus.DOWNLOADING || item.status == DownloadStatus.MERGING || item.status == DownloadStatus.PAUSED) {
                Spacer(modifier = Modifier.height(4.dp))
                val context = LocalContext.current
                val chunksCount = SettingsRepository.getChunkCount(context)
                ChunkedProgressBar(
                    progressPercent = item.progress,
                    status = item.status,
                    numChunks = chunksCount,
                    chunkProgresses = item.chunkProgresses,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}

@Composable
fun ChunkedProgressBar(
    progressPercent: Int,
    status: DownloadStatus,
    numChunks: Int,
    chunkProgresses: List<Float> = emptyList(),
    modifier: Modifier = Modifier
) {
    val activeColor = when (status) {
        DownloadStatus.DOWNLOADING -> Color(0xFF0284C7)
        DownloadStatus.MERGING -> Color(0xFF8B5CF6)
        DownloadStatus.PAUSED -> Color.Gray
        DownloadStatus.FAILED -> Color(0xFFDC2626)
        else -> Color(0xFF0284C7)
    }

    val trackColor = MaterialTheme.colorScheme.surfaceVariant
    val safeChunks = numChunks.coerceIn(1, 16)

    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        val totalProgressFraction = (progressPercent / 100f).coerceIn(0f, 1f)

        for (i in 0 until safeChunks) {
            val chunkFraction = if (chunkProgresses.size == safeChunks) {
                chunkProgresses[i].coerceIn(0f, 1f)
            } else {
                val chunkStart = i.toFloat() / safeChunks
                val chunkEnd = (i + 1).toFloat() / safeChunks
                when {
                    totalProgressFraction >= chunkEnd -> 1f
                    totalProgressFraction <= chunkStart -> 0f
                    else -> (totalProgressFraction - chunkStart) / (chunkEnd - chunkStart)
                }
            }

            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(5.dp)
                    .clip(RoundedCornerShape(2.5.dp))
                    .background(trackColor)
            ) {
                if (chunkFraction > 0f) {
                    Box(
                        modifier = Modifier
                            .fillMaxHeight()
                            .fillMaxWidth(chunkFraction)
                            .background(activeColor)
                    )
                }
            }
        }
    }
}

@Composable
fun StatusBadge(status: DownloadStatus) {
    val (label, bg, fg) = when (status) {
        DownloadStatus.DOWNLOADING -> Triple("Downloading", Color(0xFF0284C7), Color.White)
        DownloadStatus.MERGING -> Triple("Merging", Color(0xFF8B5CF6), Color.White)
        DownloadStatus.PROCESSING -> Triple("Processing", Color(0xFFD97706), Color.White)
        DownloadStatus.QUEUED, DownloadStatus.PENDING -> Triple("Queued", Color(0xFF475569), Color.White)
        DownloadStatus.PAUSED -> Triple("Paused", Color(0xFF64748B), Color.White)
        DownloadStatus.COMPLETED -> Triple("Done", Color(0xFF16A34A), Color.White)
        DownloadStatus.FAILED -> Triple("Failed", Color(0xFFDC2626), Color.White)
        DownloadStatus.CANCELLED -> Triple("Cancelled", Color(0xFF475569), Color.White)
    }

    Surface(
        shape = RoundedCornerShape(3.dp),
        color = bg
    ) {
        Text(
            text = label,
            color = fg,
            fontSize = 8.5.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
        )
    }
}

fun formatItemDate(timestamp: Long): String {
    return try {
        val sdf = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault())
        sdf.format(java.util.Date(timestamp))
    } catch (_: Exception) {
        "2026-11-18"
    }
}
