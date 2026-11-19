package com.example.videodownloader

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LongFetchPresetDialog(
    url: String,
    onFormatSelected: (MediaFormat) -> Unit,
    onDismiss: () -> Unit
) {
    val presetInfo = remember(url) { PresetFormats.getPresetVideoInfo(url) }
    var selectedTab by remember { mutableIntStateOf(0) }

    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Keep Waiting", fontWeight = FontWeight.Bold)
            }
        },
        title = {
            Column {
                Text(
                    text = "⏱️ Fetching is taking longer...",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = "Select a quality preset below to start downloading immediately, or tap Keep Waiting.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 11.sp
                )
            }
        },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                TabRow(selectedTabIndex = selectedTab) {
                    Tab(
                        selected = selectedTab == 0,
                        onClick = { selectedTab = 0 },
                        text = { Text("Video", fontWeight = FontWeight.SemiBold, fontSize = 12.sp) }
                    )
                    Tab(
                        selected = selectedTab == 1,
                        onClick = { selectedTab = 1 },
                        text = { Text("Audio", fontWeight = FontWeight.SemiBold, fontSize = 12.sp) }
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                val formats = if (selectedTab == 0) presetInfo.videoFormats else presetInfo.audioFormats

                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 280.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    items(formats) { format ->
                        FormatRow(
                            format = format,
                            onSelect = { selectedFormat ->
                                onFormatSelected(selectedFormat)
                            }
                        )
                    }
                }
            }
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FullDetailPage(
    type: String,
    onBack: () -> Unit
) {
    BackHandler(onBack = onBack)

    val titleText = when (type) {
        "app" -> "ℹ️ About the App"
        "developer" -> "👨‍💻 About the Developer"
        "privacy" -> "🔒 Privacy Policy"
        "version" -> "🏷️ App Version"
        "settings" -> "⚙️ App Settings"
        else -> "Details"
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = titleText,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Text("←", fontSize = 24.sp, fontWeight = FontWeight.Bold)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        },
        modifier = Modifier.fillMaxSize()
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp, vertical = 12.dp)
                .verticalScroll(rememberScrollState())
        ) {
            when (type) {
                "app" -> AboutAppContent()
                "developer" -> AboutDeveloperContent()
                "privacy" -> PrivacyPolicyContent()
                "version" -> AppVersionContent()
                "settings" -> SettingsPageContent()
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsPageContent() {
    val context = LocalContext.current
    var selectedChunkCount by remember { mutableIntStateOf(SettingsRepository.getChunkCount(context)) }
    var selectedLocation by remember { mutableStateOf(SettingsRepository.getDownloadLocation(context)) }

    var expandedChunkDropdown by remember { mutableStateOf(false) }

    val chunkOptions = listOf(
        1 to "1 Chunk (Single Stream)",
        2 to "2 Chunks",
        4 to "4 Chunks (Default - Recommended)",
        8 to "8 Chunks (High Speed)",
        16 to "16 Chunks (Maximum Acceleration)"
    )

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // ── Chunk Count Setting Dropdown ──
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(
                text = "Parallel Download Chunks",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
            Text(
                text = "Number of concurrent stream chunks downloading simultaneously.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(4.dp))

            ExposedDropdownMenuBox(
                expanded = expandedChunkDropdown,
                onExpandedChange = { expandedChunkDropdown = !expandedChunkDropdown },
                modifier = Modifier.fillMaxWidth()
            ) {
                val currentOptionLabel = chunkOptions.find { it.first == selectedChunkCount }?.second
                    ?: "$selectedChunkCount Chunks"

                OutlinedTextField(
                    value = currentOptionLabel,
                    onValueChange = {},
                    readOnly = true,
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expandedChunkDropdown) },
                    colors = ExposedDropdownMenuDefaults.outlinedTextFieldColors(),
                    modifier = Modifier
                        .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable)
                        .fillMaxWidth()
                )

                ExposedDropdownMenu(
                    expanded = expandedChunkDropdown,
                    onDismissRequest = { expandedChunkDropdown = false }
                ) {
                    chunkOptions.forEach { (count, label) ->
                        DropdownMenuItem(
                            text = {
                                Text(
                                    text = label,
                                    fontWeight = if (selectedChunkCount == count) FontWeight.Bold else FontWeight.Normal,
                                    color = if (selectedChunkCount == count) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                                )
                            },
                            onClick = {
                                selectedChunkCount = count
                                SettingsRepository.setChunkCount(context, count)
                                expandedChunkDropdown = false
                            }
                        )
                    }
                }
            }
        }

        HorizontalDivider()

        // ── Download Storage Location Setting ──
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(
                text = "Download Storage Location",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
            Text(
                text = "Choose where to save completed video and audio downloads.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(4.dp))

            Card(
                onClick = {
                    selectedLocation = "internal"
                    SettingsRepository.setDownloadLocation(context, "internal")
                },
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = if (selectedLocation == "internal") MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f) else MaterialTheme.colorScheme.surface
                )
            ) {
                Row(
                    modifier = Modifier.padding(14.dp).fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    RadioButton(
                        selected = selectedLocation == "internal",
                        onClick = {
                            selectedLocation = "internal"
                            SettingsRepository.setDownloadLocation(context, "internal")
                        }
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text(
                            text = "📱 Internal Storage",
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp
                        )
                        Text(
                            text = "Downloads/VideoDownloader folder",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            Card(
                onClick = {
                    selectedLocation = "sdcard"
                    SettingsRepository.setDownloadLocation(context, "sdcard")
                },
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = if (selectedLocation == "sdcard") MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f) else MaterialTheme.colorScheme.surface
                )
            ) {
                Row(
                    modifier = Modifier.padding(14.dp).fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    RadioButton(
                        selected = selectedLocation == "sdcard",
                        onClick = {
                            selectedLocation = "sdcard"
                            SettingsRepository.setDownloadLocation(context, "sdcard")
                        }
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text(
                            text = "💾 SD Card / External Storage",
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp
                        )
                        Text(
                            text = "Saves to SD card if available (fallback to Internal)",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}



@Composable
private fun AboutAppContent() {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(
            text = "Video Downloader is a high-performance native Android application designed to download videos and audio effortlessly from YouTube and over 1,000 supported websites directly to your device.",
            style = MaterialTheme.typography.bodyMedium,
            fontSize = 13.sp
        )

        Text(
            text = "Key Features:",
            fontWeight = FontWeight.Bold,
            fontSize = 14.sp,
            color = MaterialTheme.colorScheme.primary
        )

        BulletPoint("⚡ Parallel Chunk Streams: Download with multi-threaded fragment acceleration (up to 8 concurrent connections) for maximum speed.")
        BulletPoint("🎥 Full Quality Range: Support for 4K (2160p), 1440p (2K), 1080p, 720p, 480p, 360p, 240p, and 144p resolutions.")
        BulletPoint("🎵 Audio Extraction: Extract and convert audio streams directly into MP3 or M4A format.")
        BulletPoint("📋 Batch & Playlist Support: Select individual items or download entire playlists and radio mixes seamlessly.")
        BulletPoint("⏳ Background Downloads: Expedited background workers with WakeLocks ensure downloads complete even when the screen is locked.")
        BulletPoint("📂 Gallery Integration: Direct saving into your public Downloads folder for immediate playability in any media player.")
    }
}

@Composable
private fun AboutDeveloperContent() {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(
            text = "Developed with ❤️ by Arvind Minz & the VideoDownloader Team.",
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
            fontSize = 13.sp
        )

        Text(
            text = "Developer Mission:",
            fontWeight = FontWeight.Bold,
            fontSize = 14.sp,
            color = MaterialTheme.colorScheme.primary
        )

        Text(
            text = "Our goal is to build clean, responsive, and privacy-respecting Android utility applications. We focus on native performance, modern Jetpack Compose UI/UX, and minimal memory footprint.",
            style = MaterialTheme.typography.bodySmall,
            fontSize = 12.sp
        )

        Text(
            text = "Tech Stack:",
            fontWeight = FontWeight.Bold,
            fontSize = 14.sp,
            color = MaterialTheme.colorScheme.primary
        )

        BulletPoint("Language: Kotlin 2.2.10")
        BulletPoint("UI Framework: Jetpack Compose & Material 3")
        BulletPoint("Core Engine: yt-dlp & FFmpeg Native Binary Suite")
        BulletPoint("Background Architecture: Android WorkManager & Coroutines")

        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = "Thank you for using Video Downloader!",
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.secondary
        )
    }
}

@Composable
private fun PrivacyPolicyContent() {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(
            text = "Your privacy is fully guaranteed when using Video Downloader.",
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
            fontSize = 13.sp
        )

        BulletPoint("🔒 100% Local Processing: The app operates entirely on your device. We do not collect, store, or transmit any user data.")
        BulletPoint("🚫 Zero Analytics & Tracking: No third-party trackers, no advertising identifiers, and no telemetry data.")
        BulletPoint("🌐 Direct Connections: Downloads connect directly from your device to the content hosting servers without intermediate proxy servers.")
        BulletPoint("💾 Local Storage: All downloaded media files are stored locally in your device's public Downloads directory.")
        BulletPoint("🛡️ Permission Integrity: Storage and notification permissions are strictly used for file saving and progress display.")
    }
}

@Composable
private fun AppVersionContent() {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Card(
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f)
            ),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                Text(
                    text = "Video Downloader",
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp
                )
                Text(
                    text = "Version 1.0.0 (Build 100)",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }

        Spacer(modifier = Modifier.height(4.dp))

        Text(
            text = "Components & Libraries:",
            fontWeight = FontWeight.Bold,
            fontSize = 13.sp
        )

        BulletPoint("yt-dlp Core Engine: Latest Release")
        BulletPoint("FFmpeg Audio/Video Merger: v0.19.0")
        BulletPoint("Jetpack Compose Material3: 2026.02")
        BulletPoint("Kotlin Coroutines: 1.11.0")
        BulletPoint("Coil Image Loading: 2.7.0")

        Spacer(modifier = Modifier.height(4.dp))

        Text(
            text = "Status: Up to date • All systems operational",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun BulletPoint(text: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.Top
    ) {
        Text(
            text = "• ",
            fontWeight = FontWeight.Bold,
            fontSize = 13.sp,
            color = MaterialTheme.colorScheme.primary
        )
        Text(
            text = text,
            style = MaterialTheme.typography.bodySmall,
            fontSize = 12.sp
        )
    }
}
