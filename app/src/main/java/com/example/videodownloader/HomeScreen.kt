package com.example.videodownloader

import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreenContent(
    currentUrl: String,
    onUrlChange: (String) -> Unit,
    videoInfo: VideoInfo?,
    isLoading: Boolean,
    errorMessage: String?,
    onFetchFormats: (String) -> Unit,
    onFormatSelected: (MediaFormat) -> Unit,
    onPlaylistDownloadSelected: (List<PlaylistItem>, MediaFormat, Boolean) -> Unit,
    onReset: () -> Unit,
    onOpenBrowser: (String) -> Unit,
    onPlayVideo: (TrendingVideo) -> Unit = {},
    onOpenSearchOverlay: () -> Unit = {}
) {
    val context = LocalContext.current
    val keyboardController = LocalSoftwareKeyboardController.current
    val focusManager = LocalFocusManager.current

    var selectedCategory by remember { mutableStateOf("All") }
    var feedVideos by remember { mutableStateOf(TrendingFeedRepository.defaultTrendingList) }
    var isFetchingMore by remember { mutableStateOf(false) }
    var pageCount by remember { mutableIntStateOf(1) }

    val lazyListState = rememberLazyListState()

    LaunchedEffect(selectedCategory) {
        pageCount = 1
        val list = TrendingFeedRepository.getTrendingVideos(context, selectedCategory)
        feedVideos = list
    }

    val shouldLoadMore by remember {
        derivedStateOf {
            val totalItems = lazyListState.layoutInfo.totalItemsCount
            val lastVisibleItem = lazyListState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
            totalItems > 0 && lastVisibleItem >= totalItems - 2
        }
    }

    LaunchedEffect(shouldLoadMore) {
        if (shouldLoadMore && !isFetchingMore && !isLoading && errorMessage == null && videoInfo == null) {
            isFetchingMore = true
            val nextPage = pageCount + 1
            val newBatch = TrendingFeedRepository.loadMoreVideos(context, selectedCategory, nextPage)
            if (newBatch.isNotEmpty()) {
                val existingIds = feedVideos.map { it.id }.toSet()
                val distinctNew = newBatch.filterNot { existingIds.contains(it.id) }
                feedVideos = feedVideos + distinctNew
                pageCount = nextPage
            }
            isFetchingMore = false
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        // ── Top Search Bar (matching reference layout) ──
        Surface(
            tonalElevation = 2.dp,
            color = MaterialTheme.colorScheme.surface,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
                Box(modifier = Modifier.fillMaxWidth()) {
                    OutlinedTextField(
                        value = currentUrl,
                        onValueChange = onUrlChange,
                        placeholder = { Text("Search or enter url", fontSize = 14.sp) },
                        singleLine = true,
                        leadingIcon = {
                            Text("🔍", fontSize = 16.sp)
                        },
                        trailingIcon = {
                            IconButton(
                                onClick = {
                                    keyboardController?.hide()
                                    focusManager.clearFocus()
                                    if (currentUrl.isNotBlank()) {
                                        if (currentUrl.startsWith("http://") || currentUrl.startsWith("https://") || currentUrl.contains(".")) {
                                            onFetchFormats(currentUrl)
                                        } else {
                                            onOpenBrowser("https://www.google.com/search?q=${java.net.URLEncoder.encode(currentUrl, "UTF-8")}")
                                        }
                                    } else {
                                        onOpenSearchOverlay()
                                    }
                                }
                            ) {
                                Text("📥", fontSize = 20.sp)
                            }
                        },
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Uri,
                            imeAction = ImeAction.Go
                        ),
                        keyboardActions = KeyboardActions(
                            onGo = {
                                keyboardController?.hide()
                                focusManager.clearFocus()
                                if (currentUrl.isNotBlank()) {
                                    onFetchFormats(currentUrl)
                                }
                            }
                        ),
                        shape = RoundedCornerShape(24.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                            focusedContainerColor = MaterialTheme.colorScheme.surface
                        ),
                        modifier = Modifier.fillMaxWidth().height(50.dp)
                    )

                    // Clickable Overlay Box to launch Search Overlay on click
                    Box(
                        modifier = Modifier
                            .matchParentSize()
                            .padding(end = 48.dp)
                            .clickable { onOpenSearchOverlay() }
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))

                // ── Category Chips Row (Shorts, All, Music, Romantic Music, etc.) ──
                val categories = listOf(
                    "Shorts" to true,
                    "All" to false,
                    "Music" to false,
                    "Romantic Music" to false,
                    "Emotional Music" to false,
                    "Lo-Fi" to false
                )

                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    items(categories) { (cat, hasBadge) ->
                        val isSelected = cat == selectedCategory
                        Surface(
                            shape = RoundedCornerShape(18.dp),
                            color = if (isSelected) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                            modifier = Modifier.clickable { selectedCategory = cat }
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                if (cat == "Shorts") {
                                    Text("⚡ ", fontSize = 13.sp)
                                }
                                Text(
                                    text = cat,
                                    fontSize = 13.sp,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                    color = if (isSelected) MaterialTheme.colorScheme.surface else MaterialTheme.colorScheme.onSurface
                                )
                                if (hasBadge) {
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Box(
                                        modifier = Modifier
                                            .size(6.dp)
                                            .clip(CircleShape)
                                            .background(Color.Red)
                                    )
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // ── Quick Site Shortcuts Row (YouTube, WhatsApp, Instagram, Facebook, Sites) ──
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    SiteShortcutItem(
                        iconText = "▶️",
                        label = "YouTube",
                        bgColor = Color(0xFFFF0000),
                        onClick = { onOpenBrowser("https://m.youtube.com") }
                    )
                    SiteShortcutItem(
                        iconText = "💬",
                        label = "WhatsApp",
                        bgColor = Color(0xFF25D366),
                        onClick = { onOpenBrowser("https://web.whatsapp.com") }
                    )
                    SiteShortcutItem(
                        iconText = "📷",
                        label = "Instagram",
                        bgColor = Color(0xFFE1306C),
                        onClick = { onOpenBrowser("https://www.instagram.com") }
                    )
                    SiteShortcutItem(
                        iconText = "f",
                        label = "Facebook",
                        bgColor = Color(0xFF1877F2),
                        onClick = { onOpenBrowser("https://m.facebook.com") }
                    )
                    SiteShortcutItem(
                        iconText = "🌐",
                        label = "Sites",
                        bgColor = Color(0xFF3B82F6),
                        onClick = { onOpenBrowser("https://www.google.com") }
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(4.dp))

        // ── Main Content Area: Show Fetched Formats OR Trending Video Feed ──
        when {
            isLoading -> {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator()
                        Spacer(modifier = Modifier.height(12.dp))
                        Text("Fetching video details…")
                    }
                }
            }

            errorMessage != null -> {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(text = errorMessage, color = MaterialTheme.colorScheme.error)
                        Spacer(modifier = Modifier.height(8.dp))
                        Button(onClick = onReset) {
                            Text("Clear")
                        }
                    }
                }
            }

            videoInfo != null -> {
                Column(modifier = Modifier.fillMaxSize().padding(12.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Download Options",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        OutlinedButton(onClick = onReset) {
                            Text("Clear")
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    if (videoInfo.isPlaylist || videoInfo.isRadio || videoInfo.playlistItems.isNotEmpty()) {
                        PlaylistDetailsContent(
                            videoInfo = videoInfo,
                            onDownloadSelected = onPlaylistDownloadSelected,
                            modifier = Modifier.fillMaxSize()
                        )
                    } else {
                        VideoDetailsContent(
                            videoInfo = videoInfo,
                            onFormatSelected = onFormatSelected,
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                }
            }

            else -> {
                // ── Vertical Trending Feed with Infinite Scrolling ──
                LazyColumn(
                    state = lazyListState,
                    contentPadding = PaddingValues(12.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    items(
                        items = feedVideos,
                        key = { it.id }
                    ) { video ->
                        TrendingVideoCard(
                            video = video,
                            onVideoClick = { onPlayVideo(video) },
                            onDirectDownload = { onFetchFormats(video.videoUrl) },
                            onShare = {
                                val intent = Intent(Intent.ACTION_SEND).apply {
                                    type = "text/plain"
                                    putExtra(Intent.EXTRA_TEXT, video.videoUrl)
                                }
                                context.startActivity(Intent.createChooser(intent, "Share Video"))
                            }
                        )
                    }

                    if (isFetchingMore) {
                        item {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp),
                                horizontalArrangement = Arrangement.Center,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                                Spacer(modifier = Modifier.width(10.dp))
                                Text(
                                    text = "Loading more trending videos...",
                                    fontSize = 13.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun SiteShortcutItem(
    iconText: String,
    label: String,
    bgColor: Color,
    onClick: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.clickable { onClick() }
    ) {
        Box(
            modifier = Modifier
                .size(44.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(bgColor),
            contentAlignment = Alignment.Center
        ) {
            Text(iconText, fontSize = 20.sp, color = Color.White, fontWeight = FontWeight.Bold)
        }
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = label,
            fontSize = 11.sp,
            color = MaterialTheme.colorScheme.onSurface,
            fontWeight = FontWeight.Medium
        )
    }
}

@Composable
fun TrendingVideoCard(
    video: TrendingVideo,
    onVideoClick: () -> Unit,
    onDirectDownload: () -> Unit,
    onShare: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onVideoClick() },
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 3.dp)
    ) {
        Column {
            // ── Big Thumbnail Image with Badges ──
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(210.dp)
            ) {
                AsyncImage(
                    model = video.thumbnailUrl,
                    contentDescription = video.title,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )

                // Dark Gradient overlay
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.7f)),
                                startY = 300f
                            )
                        )
                )

                // Play Button Overlay in center
                Surface(
                    shape = CircleShape,
                    color = Color.Black.copy(alpha = 0.5f),
                    modifier = Modifier
                        .size(48.dp)
                        .align(Alignment.Center)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text("▶", fontSize = 20.sp, color = Color.White)
                    }
                }

                // Bottom Left: Views count
                Surface(
                    shape = RoundedCornerShape(4.dp),
                    color = Color.Black.copy(alpha = 0.7f),
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(10.dp)
                ) {
                    Text(
                        text = video.viewsText,
                        color = Color.White,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }

                // Bottom Right: Duration badge
                Surface(
                    shape = RoundedCornerShape(4.dp),
                    color = Color.Black.copy(alpha = 0.85f),
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(10.dp)
                ) {
                    Text(
                        text = video.durationText,
                        color = Color.White,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
            }

            // ── Card Details Row (Avatar, Title, Subtitle, Download Button) ──
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp),
                verticalAlignment = Alignment.Top
            ) {
                // Channel Avatar
                AsyncImage(
                    model = video.channelAvatarUrl,
                    contentDescription = video.channelName,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .size(38.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                )

                Spacer(modifier = Modifier.width(10.dp))

                // Title + Channel Info
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = video.title,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        lineHeight = 18.sp
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "${video.channelName} · ${video.uploadedAgo}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 12.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                // Direct Download Action Button 📥
                IconButton(onClick = onDirectDownload) {
                    Text("📥", fontSize = 20.sp)
                }

                // 3-dots Menu Button
                IconButton(onClick = onShare) {
                    Text("⋮", fontSize = 18.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}
