package com.example.videodownloader

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.lifecycleScope
import coil.compose.AsyncImage
import com.example.videodownloader.ui.theme.VideoDownloaderTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Floating dialog activity that:
 *  1. Receives a shared URL via ACTION_SEND.
 *  2. Extracts the clean URL from any surrounding text using regex.
 *  3. Calls youtubedl-android to fetch available formats (video + audio).
 *  4. Shows a Compose UI floating dialog with thumbnail, title, duration, and two tabs.
 *  5. Enqueues a DownloadWorker when the user picks a format.
 *  6. Finishes immediately so the user returns to the source app.
 */
class SharePopupActivity : ComponentActivity() {

    companion object {
        private const val TAG = "SharePopupActivity"
        private val URL_REGEX = Regex(
            """(?:https?://|www\.)[^\s"']+""",
            RegexOption.IGNORE_CASE
        )
        const val WORK_NAME = "video_download"
    }

    private var videoInfoState by mutableStateOf<VideoInfo?>(null)
    private var isLoadingState by mutableStateOf(true)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val sharedUrl = extractUrlFromIntent(intent)
        if (sharedUrl == null) {
            Toast.makeText(this, "No valid URL found in shared text", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        val isYouTube = PresetFormats.isYouTubeUrl(sharedUrl)
        val isPlaylist = PresetFormats.isDedicatedPlaylistUrl(sharedUrl)

        if (isPlaylist) {
            // Playlist / Radio Mix -> Open main downloader app directly
            val mainIntent = Intent(this, MainActivity::class.java).apply {
                action = Intent.ACTION_SEND
                type = "text/plain"
                putExtra(Intent.EXTRA_TEXT, sharedUrl)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            }
            startActivity(mainIntent)
            finish()
            return
        }

        // Single Video -> Show floating popup with 4K to 144p options directly
        val initialInfo = PresetFormats.getPresetVideoInfo(sharedUrl)
        videoInfoState = initialInfo
        isLoadingState = false

        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val parsed = YoutubeDLUtils.fetchVideoOrPlaylistInfo(applicationContext, sharedUrl)
                withContext(Dispatchers.Main) {
                    videoInfoState = parsed
                }
            } catch (e: Exception) {
                Log.w(TAG, "Failed to fetch video details in popup", e)
            }
        }

        setContent {
            VideoDownloaderTheme {
                val currentInfo = videoInfoState ?: initialInfo
                SharePopupContent(
                    videoInfo = currentInfo,
                    isLoading = false,
                    onFormatSelected = { format -> startDownload(sharedUrl, format, isYouTube) },
                    onDismiss = { finish() }
                )
            }
        }
    }

    private fun extractUrlFromIntent(intent: Intent?): String? {
        if (intent?.action != Intent.ACTION_SEND) return null
        if (intent.type != "text/plain") return null
        val sharedText = intent.getStringExtra(Intent.EXTRA_TEXT) ?: return null
        return URL_REGEX.find(sharedText)?.value
    }

    private fun startDownload(url: String, format: MediaFormat, isYouTube: Boolean) {
        val title = videoInfoState?.title ?: if (isYouTube) "YouTube Video" else "${PresetFormats.parseDomain(url)} Video"
        DownloadRepository.startDownload(applicationContext, url, title, format, durationSeconds = videoInfoState?.durationSeconds ?: 0L)
        Toast.makeText(this, "Added to download queue", Toast.LENGTH_SHORT).show()
        finish()
    }

    private fun startPlaylistDownloads(selectedItems: List<PlaylistItem>, format: MediaFormat) {
        DownloadRepository.startBatchDownloads(applicationContext, selectedItems, format)
        Toast.makeText(this, "Added ${selectedItems.size} items to download queue", Toast.LENGTH_SHORT).show()
        finish()
    }
}

// ── Data classes ───────────────────────────────────────────────────

data class PlaylistItem(
    val id: String,
    val title: String,
    val url: String,
    val thumbnailUrl: String,
    val durationSeconds: Long,
    val isSelected: Boolean = true
)

data class VideoInfo(
    val title: String,
    val thumbnailUrl: String,
    val durationSeconds: Long,
    val videoFormats: List<MediaFormat>,
    val audioFormats: List<MediaFormat>,
    val isPlaylist: Boolean = false,
    val isRadio: Boolean = false,
    val playlistItems: List<PlaylistItem> = emptyList()
)

data class MediaFormat(
    val formatId: String,
    val extension: String,
    val fileSizeBytes: Long,
    val resolution: String,
    val formatNote: String,
    val bitrate: Double,
    val hasVideo: Boolean,
    val hasAudio: Boolean,
    val isCombined: Boolean = false
)

// ── Compose UI ─────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SharePopupContent(
    videoInfo: VideoInfo,
    isLoading: Boolean,
    onFormatSelected: (MediaFormat) -> Unit,
    onPlaylistDownloadSelected: (List<PlaylistItem>, MediaFormat) -> Unit = { _, _ -> },
    onDismiss: () -> Unit
) {
    var selectedTab by remember { mutableIntStateOf(0) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onDismiss
            )
            .padding(16.dp),
        contentAlignment = Alignment.Center
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .wrapContentHeight()
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = {}
                ),
            elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Video Downloader",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = if (isLoading && videoInfo.playlistItems.isEmpty()) "Loading video/playlist details..." else videoInfo.title,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    TextButton(onClick = onDismiss) {
                        Text("Cancel")
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                if (isLoading && videoInfo.playlistItems.isEmpty() && (videoInfo.title == "YouTube Video" || videoInfo.title.endsWith("Video"))) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(16.dp),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                        Spacer(modifier = Modifier.width(10.dp))
                        Text("Fetching video/playlist...", fontSize = 13.sp)
                    }
                } else if (videoInfo.isPlaylist || videoInfo.isRadio || videoInfo.playlistItems.isNotEmpty()) {
                    PlaylistDetailsContent(
                        videoInfo = videoInfo,
                        onDownloadSelected = onPlaylistDownloadSelected,
                        modifier = Modifier.fillMaxWidth().heightIn(max = 420.dp)
                    )
                } else {
                    TabRow(selectedTabIndex = selectedTab) {
                        Tab(
                            selected = selectedTab == 0,
                            onClick = { selectedTab = 0 },
                            text = { Text("Video", fontWeight = FontWeight.SemiBold) }
                        )
                        Tab(
                            selected = selectedTab == 1,
                            onClick = { selectedTab = 1 },
                            text = { Text("Audio", fontWeight = FontWeight.SemiBold) }
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    val formats = if (selectedTab == 0) videoInfo.videoFormats else videoInfo.audioFormats

                    LazyColumn(
                        modifier = Modifier.fillMaxWidth().heightIn(max = 300.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        items(formats) { format ->
                            FormatRow(format = format, onSelect = onFormatSelected)
                        }
                    }
                }
            }
        }
    }
}

/**
 * Reusable playlist details & format selector.
 * Allows picking video or audio format and applying it across all items in the playlist/radio.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlaylistDetailsContent(
    videoInfo: VideoInfo,
    isLoadingRealtime: Boolean = false,
    listState: androidx.compose.foundation.lazy.LazyListState = rememberLazyListState(),
    onDownloadSelected: (List<PlaylistItem>, MediaFormat) -> Unit,
    modifier: Modifier = Modifier
) {
    var selectedTab by remember { mutableIntStateOf(0) } // 0 = Video, 1 = Audio
    val formats = if (selectedTab == 0) videoInfo.videoFormats else videoInfo.audioFormats

    var selectedFormat by remember(selectedTab, videoInfo) {
        mutableStateOf(formats.firstOrNull() ?: videoInfo.videoFormats.first())
    }

    var itemsState by remember(videoInfo.playlistItems.size) {
        mutableStateOf(videoInfo.playlistItems)
    }

    val selectedCount = itemsState.count { it.isSelected }
    val allSelected = itemsState.isNotEmpty() && itemsState.all { it.isSelected }

    Column(modifier = modifier) {
        // ── Live Streaming Progress Banner (Shows while items are being fetched line-by-line) ──
        if (isLoadingRealtime || itemsState.isEmpty()) {
            Surface(
                color = MaterialTheme.colorScheme.primaryContainer,
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.fillMaxWidth().padding(bottom = 6.dp)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = if (itemsState.isEmpty()) "Connecting to playlist stream..." else "Fetching live playlist videos: ${itemsState.size} loaded...",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
            }
        }
        // ── Compact Playlist / Radio Header Banner ──
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f)
            )
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp).fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = if (videoInfo.isRadio) MaterialTheme.colorScheme.tertiaryContainer else MaterialTheme.colorScheme.secondaryContainer
                    ) {
                        Text(
                            text = if (videoInfo.isRadio) "📻 Radio" else "📋 Playlist",
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = if (videoInfo.isRadio) MaterialTheme.colorScheme.onTertiaryContainer else MaterialTheme.colorScheme.onSecondaryContainer
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = videoInfo.title,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "$selectedCount/${itemsState.size}",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }

        Spacer(modifier = Modifier.height(4.dp))

        // ── Compact Quality Selector ──
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            TabRow(
                selectedTabIndex = selectedTab,
                modifier = Modifier.width(160.dp).height(36.dp)
            ) {
                Tab(
                    selected = selectedTab == 0,
                    onClick = {
                        selectedTab = 0
                        videoInfo.videoFormats.firstOrNull()?.let { selectedFormat = it }
                    },
                    text = { Text("Video", fontSize = 12.sp) }
                )
                Tab(
                    selected = selectedTab == 1,
                    onClick = {
                        selectedTab = 1
                        videoInfo.audioFormats.firstOrNull()?.let { selectedFormat = it }
                    },
                    text = { Text("Audio", fontSize = 12.sp) }
                )
            }

            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                modifier = Modifier.weight(1f).padding(start = 8.dp)
            ) {
                items(formats) { format ->
                    val isSelected = format == selectedFormat
                    FilterChip(
                        selected = isSelected,
                        onClick = { selectedFormat = format },
                        label = {
                            Text(
                                text = if (format.hasVideo) format.resolution else format.formatNote,
                                fontSize = 11.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                            )
                        },
                        modifier = Modifier.height(32.dp)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(4.dp))

        // ── Select All / Deselect All Controls ──
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.clickable {
                    val newState = !allSelected
                    itemsState = itemsState.map { it.copy(isSelected = newState) }
                }
            ) {
                Checkbox(
                    checked = allSelected,
                    onCheckedChange = { checked ->
                        itemsState = itemsState.map { it.copy(isSelected = checked) }
                    },
                    modifier = Modifier.size(28.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = if (allSelected) "Deselect All" else "Select All",
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.SemiBold
                )
            }

            Text(
                text = "Applied: ${selectedFormat.resolution.ifEmpty { selectedFormat.formatNote }}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Bold
            )
        }

        Spacer(modifier = Modifier.height(2.dp))

        // ── Playlist Items LazyColumn with Draggable & Auto-Shrink Scrollbar on Right Edge ──
        LazyColumn(
            state = listState,
            verticalArrangement = Arrangement.spacedBy(4.dp),
            modifier = Modifier
                .weight(1f)
                .draggableVerticalScrollbar(listState)
        ) {
            itemsIndexed(
                items = itemsState,
                key = { index, item -> "${item.id}_$index" }
            ) { _, item ->
                PlaylistItemRow(
                    item = item,
                    selectedFormatLabel = selectedFormat.resolution.ifEmpty { selectedFormat.extension.uppercase() },
                    onToggleSelect = {
                        itemsState = itemsState.map { current ->
                            if (current.id == item.id) current.copy(isSelected = !current.isSelected)
                            else current
                        }
                    }
                )
            }
        }

        Spacer(modifier = Modifier.height(6.dp))

        // ── Download Action Button ──
        Button(
            onClick = {
                val selectedItems = itemsState.filter { it.isSelected }
                if (selectedItems.isNotEmpty()) {
                    onDownloadSelected(selectedItems, selectedFormat)
                }
            },
            enabled = selectedCount > 0,
            modifier = Modifier.fillMaxWidth().height(44.dp)
        ) {
            Text(
                text = "Download $selectedCount ${if (selectedFormat.hasVideo) "Video" else "Audio"}${if (selectedCount > 1) "s" else ""} (${selectedFormat.resolution.ifEmpty { selectedFormat.extension.uppercase() }})",
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp
            )
        }
    }
}

@Composable
fun PlaylistItemRow(
    item: PlaylistItem,
    selectedFormatLabel: String,
    onToggleSelect: () -> Unit
) {
    Card(
        onClick = onToggleSelect,
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (item.isSelected) MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f) else MaterialTheme.colorScheme.surface
        )
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 4.dp).fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Checkbox(
                checked = item.isSelected,
                onCheckedChange = { onToggleSelect() },
                modifier = Modifier.size(28.dp)
            )

            Spacer(modifier = Modifier.width(4.dp))

            AsyncImage(
                model = item.thumbnailUrl,
                contentDescription = null,
                modifier = Modifier
                    .width(72.dp)
                    .height(42.dp)
                    .clip(RoundedCornerShape(4.dp)),
                contentScale = ContentScale.Crop
            )

            Spacer(modifier = Modifier.width(8.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = item.title,
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 12.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(2.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    if (item.durationSeconds > 0) {
                        Text(
                            text = formatDuration(item.durationSeconds),
                            style = MaterialTheme.typography.labelSmall,
                            fontSize = 10.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    Surface(
                        shape = RoundedCornerShape(3.dp),
                        color = MaterialTheme.colorScheme.primaryContainer
                    ) {
                        Text(
                            text = selectedFormatLabel,
                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            fontSize = 9.sp
                        )
                    }
                }
            }
        }
    }
}

fun Modifier.draggableVerticalScrollbar(
    state: androidx.compose.foundation.lazy.LazyListState,
    color: androidx.compose.ui.graphics.Color = androidx.compose.ui.graphics.Color.Red,
    width: androidx.compose.ui.unit.Dp = 6.dp
): Modifier = composed {
    val coroutineScope = rememberCoroutineScope()
    var isDragging by remember { mutableStateOf(false) }
    val isScrolling = state.isScrollInProgress || isDragging

    val targetWidth = if (isScrolling) width else 2.dp
    val targetAlpha = if (isScrolling) 0.85f else 0.35f

    val animatedWidth by animateDpAsState(targetValue = targetWidth, label = "scrollbarWidth")
    val animatedAlpha by animateFloatAsState(targetValue = targetAlpha, label = "scrollbarAlpha")

    this
        .drawWithContent {
            drawContent()
            val visibleItemsInfo = state.layoutInfo.visibleItemsInfo
            val totalItemsCount = state.layoutInfo.totalItemsCount
            val firstVisibleIndex = visibleItemsInfo.firstOrNull()?.index ?: 0

            if (visibleItemsInfo.isNotEmpty() && totalItemsCount > 0) {
                val elementHeight = this.size.height / totalItemsCount
                val scrollbarOffsetY = firstVisibleIndex * elementHeight
                val scrollbarHeight = visibleItemsInfo.size * elementHeight

                drawRoundRect(
                    color = color.copy(alpha = animatedAlpha),
                    topLeft = androidx.compose.ui.geometry.Offset(this.size.width - animatedWidth.toPx(), scrollbarOffsetY),
                    size = androidx.compose.ui.geometry.Size(animatedWidth.toPx(), scrollbarHeight.coerceAtLeast(28.dp.toPx())),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(animatedWidth.toPx() / 2, animatedWidth.toPx() / 2)
                )
            }
        }
        .pointerInput(state) {
            detectVerticalDragGestures(
                onDragStart = { isDragging = true },
                onDragEnd = { isDragging = false },
                onDragCancel = { isDragging = false },
                onVerticalDrag = { change, dragAmount ->
                    change.consume()
                    val totalItems = state.layoutInfo.totalItemsCount
                    if (totalItems > 0 && this.size.height > 0) {
                        val deltaFraction = dragAmount / this.size.height
                        val deltaItems = (deltaFraction * totalItems).toInt()
                        val newIndex = (state.firstVisibleItemIndex + deltaItems).coerceIn(0, totalItems - 1)
                        coroutineScope.launch {
                            state.scrollToItem(newIndex)
                        }
                    }
                }
            )
        }
}

/**
 * Reusable video details & format selector.
 * Rendered inline in MainActivity, or wrapped in a floating Card in
 * SharePopupActivity.
 */
@Composable
fun VideoDetailsContent(
    videoInfo: VideoInfo,
    onFormatSelected: (MediaFormat) -> Unit,
    modifier: Modifier = Modifier
) {
    var selectedTab by remember { mutableIntStateOf(0) }

    Column(modifier = modifier) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            AsyncImage(
                model = videoInfo.thumbnailUrl,
                contentDescription = "Thumbnail",
                modifier = Modifier.width(120.dp).height(68.dp),
                contentScale = ContentScale.Crop
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = videoInfo.title,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = formatDuration(videoInfo.durationSeconds),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        TabRow(selectedTabIndex = selectedTab) {
            Tab(
                selected = selectedTab == 0,
                onClick = { selectedTab = 0 },
                text = { Text("Video") }
            )
            Tab(
                selected = selectedTab == 1,
                onClick = { selectedTab = 1 },
                text = { Text("Audio") }
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        val formats = if (selectedTab == 0) videoInfo.videoFormats else videoInfo.audioFormats

        if (formats.isEmpty()) {
            Text(
                "No formats available for this category",
                modifier = Modifier.padding(16.dp),
                style = MaterialTheme.typography.bodyMedium
            )
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                items(formats) { format ->
                    FormatRow(format = format, onSelect = onFormatSelected)
                }
            }
        }
    }
}

@Composable
fun FormatRow(format: MediaFormat, onSelect: (MediaFormat) -> Unit) {
    Card(
        onClick = { onSelect(format) },
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                val qualityLabel = if (format.hasVideo) {
                    format.resolution.ifEmpty { format.formatNote }
                } else {
                    format.formatNote.ifEmpty { "Audio" }
                }
                Text(
                    text = qualityLabel,
                    fontWeight = FontWeight.Medium,
                    fontSize = 14.sp
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = buildString {
                        append(format.extension.uppercase())
                        if (format.fileSizeBytes > 0) {
                            append(" • ~${formatFileSize(format.fileSizeBytes)}")
                        }
                    },
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Text(
                "Download",
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.SemiBold,
                fontSize = 14.sp
            )
        }
    }
}

// ── Helpers ────────────────────────────────────────────────────────

fun formatDuration(seconds: Long): String {
    val h = seconds / 3600
    val m = (seconds % 3600) / 60
    val s = seconds % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, s)
    else "%d:%02d".format(m, s)
}

fun formatFileSize(bytes: Long): String {
    if (bytes <= 0) return "Unknown size"
    val kb = bytes / 1024.0
    val mb = kb / 1024.0
    val gb = mb / 1024.0
    return when {
        gb >= 1.0 -> "%.2f GB".format(gb)
        mb >= 1.0 -> "%.1f MB".format(mb)
        else -> "%.0f KB".format(kb)
    }
}