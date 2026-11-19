package com.example.videodownloader

import android.Manifest
import android.app.DownloadManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.DocumentsContract
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.painterResource
import androidx.core.content.FileProvider
import java.io.File
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.example.videodownloader.ui.theme.VideoDownloaderTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Main full-screen activity.
 *
 * Responsibilities:
 *  1. Fast URL format fetcher (< 4 seconds).
 *  2. Queue tab showing downloads in progress with download speed and pause/resume controls.
 *  3. Request POST_NOTIFICATIONS permission on Android 13+.
 */
class MainActivity : ComponentActivity() {

    companion object {
        private const val TAG = "MainActivity"
        private val URL_REGEX = Regex(
            """(?:https?://|www\.)[^\s"']+""",
            RegexOption.IGNORE_CASE
        )
    }

    private val requestNotificationPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (!granted) {
                Toast.makeText(
                    this,
                    "Notifications disabled — download progress won't be visible",
                    Toast.LENGTH_LONG
                ).show()
            }
        }

    private var videoInfo by mutableStateOf<VideoInfo?>(null)
    private var isLoading by mutableStateOf(false)
    private var errorMessage by mutableStateOf<String?>(null)
    private var currentUrl by mutableStateOf("")
    private var initialTabState by mutableIntStateOf(0)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        DownloadRepository.init(applicationContext)
        askNotificationPermissionIfNeeded()

        handleIncomingIntent(intent)

        setContent {
            val context = LocalContext.current
            var currentThemeMode by remember { mutableStateOf(SettingsRepository.getThemeMode(context)) }

            VideoDownloaderTheme(themeMode = currentThemeMode) {
                MainAppScreen(
                    initialTab = initialTabState,
                    currentUrl = currentUrl,
                    onUrlChange = { currentUrl = it },
                    videoInfo = videoInfo,
                    isLoading = isLoading,
                    errorMessage = errorMessage,
                    onFetchFormats = { url -> fetchFormats(url) },
                    onFormatSelected = { format -> startDownload(currentUrl, videoInfo?.title ?: "video", format) },
                    onPlaylistDownloadSelected = { items, format, withSubs -> startPlaylistDownloads(items, format, withSubs) },
                    onReset = { resetState() },
                    themeMode = currentThemeMode,
                    onThemeModeChange = { newMode ->
                        currentThemeMode = newMode
                        SettingsRepository.setThemeMode(context, newMode)
                    }
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIncomingIntent(intent)
    }

    private fun handleIncomingIntent(intent: Intent?) {
        if (intent == null) return
        handleIntentTab(intent)

        val extractedUrl = extractUrlFromIntent(intent)
        if (!extractedUrl.isNullOrBlank() && extractedUrl != currentUrl) {
            currentUrl = extractedUrl
            fetchFormats(extractedUrl)
        }
    }

    private fun extractUrlFromIntent(intent: Intent?): String? {
        if (intent == null) return null
        val action = intent.action

        if (action == Intent.ACTION_SEND && intent.type == "text/plain") {
            val sharedText = intent.getStringExtra(Intent.EXTRA_TEXT) ?: return null
            return URL_REGEX.find(sharedText)?.value
        }

        if (action == Intent.ACTION_VIEW) {
            val dataUri = intent.dataString ?: return null
            return URL_REGEX.find(dataUri)?.value
        }

        val extraUrl = intent.getStringExtra("EXTRA_URL") ?: intent.getStringExtra("SHARED_URL")
        if (!extraUrl.isNullOrBlank()) {
            return URL_REGEX.find(extraUrl)?.value
        }

        return null
    }

    private fun handleIntentTab(intent: Intent?) {
        val tab = intent?.getStringExtra("SHOW_TAB")
        if (tab == "queue" || tab == "1") {
            initialTabState = 1
        } else if (tab == "downloads" || tab == "2") {
            initialTabState = 2
        }
    }

    private fun askNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val granted = ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
            if (!granted) {
                requestNotificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }

    private fun fetchFormats(rawInput: String) {
        val url = URL_REGEX.find(rawInput)?.value ?: rawInput.trim()
        if (url.isBlank()) {
            Toast.makeText(this, "Please enter a valid URL", Toast.LENGTH_SHORT).show()
            return
        }

        currentUrl = url
        errorMessage = null

        val isPlaylistUrl = PresetFormats.isDedicatedPlaylistUrl(url)
        if (isPlaylistUrl) {
            videoInfo = VideoInfo(
                title = "Fetching Playlist...",
                thumbnailUrl = "",
                durationSeconds = 0L,
                videoFormats = PresetFormats.getPresetVideoInfo(url).videoFormats,
                audioFormats = PresetFormats.getPresetVideoInfo(url).audioFormats,
                isPlaylist = true,
                playlistItems = emptyList()
            )
            isLoading = true
        } else {
            videoInfo = PresetFormats.getPresetVideoInfo(url)
            isLoading = false
        }

        // 2. Asynchronously stream metadata & playlist items line-by-line in real time
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val parsed = YoutubeDLUtils.fetchVideoOrPlaylistInfoStreamed(
                    context = applicationContext,
                    rawUrl = url,
                    onRealtimeItemFetched = { item, playlistTitle ->
                        lifecycleScope.launch(Dispatchers.Main) {
                            if (currentUrl == url) {
                                val currentItems = videoInfo?.playlistItems ?: emptyList()
                                if (!currentItems.any { it.id == item.id }) {
                                    val updatedItems = currentItems + item
                                    val currentInfo = videoInfo
                                    videoInfo = VideoInfo(
                                        title = playlistTitle ?: currentInfo?.title ?: "Playlist",
                                        thumbnailUrl = currentInfo?.thumbnailUrl ?: item.thumbnailUrl,
                                        durationSeconds = currentInfo?.durationSeconds ?: 0L,
                                        videoFormats = currentInfo?.videoFormats ?: PresetFormats.getPresetVideoInfo(url).videoFormats,
                                        audioFormats = currentInfo?.audioFormats ?: PresetFormats.getPresetVideoInfo(url).audioFormats,
                                        isPlaylist = true,
                                        playlistItems = updatedItems
                                    )
                                }
                            }
                        }
                    }
                )
                withContext(Dispatchers.Main) {
                    if (currentUrl == url) {
                        videoInfo = parsed
                        isLoading = false
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "Background metadata enrichment failed", e)
                withContext(Dispatchers.Main) {
                    if (currentUrl == url) {
                        if (isPlaylistUrl && (videoInfo == null || videoInfo?.playlistItems.isNullOrEmpty())) {
                            errorMessage = "Failed to load playlist. Please check your connection or link."
                        }
                        isLoading = false
                    }
                }
            }
        }
    }

    private fun startDownload(url: String, title: String, format: MediaFormat, downloadSubtitles: Boolean = false) {
        val duration = videoInfo?.durationSeconds ?: 0L
        val thumbnail = videoInfo?.thumbnailUrl ?: ""
        DownloadRepository.startDownload(
            context = applicationContext,
            url = url,
            title = title,
            format = format,
            durationSeconds = duration,
            thumbnailUrl = thumbnail,
            downloadSubtitles = downloadSubtitles
        )
        Toast.makeText(this, "Added to download queue", Toast.LENGTH_SHORT).show()
    }

    private fun startPlaylistDownloads(selectedItems: List<PlaylistItem>, format: MediaFormat, downloadSubtitles: Boolean = false) {
        DownloadRepository.startBatchDownloads(applicationContext, selectedItems, format, downloadSubtitles)
        Toast.makeText(this, "Added ${selectedItems.size} items to download queue", Toast.LENGTH_SHORT).show()
    }

    private fun resetState() {
        currentUrl = ""
        videoInfo = null
        isLoading = false
        errorMessage = null
    }
}

// ── App Screen with Home & Queue Tabs ───────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainAppScreen(
    initialTab: Int = 0,
    currentUrl: String,
    onUrlChange: (String) -> Unit,
    videoInfo: VideoInfo?,
    isLoading: Boolean,
    errorMessage: String?,
    onFetchFormats: (String) -> Unit,
    onFormatSelected: (MediaFormat) -> Unit,
    onPlaylistDownloadSelected: (List<PlaylistItem>, MediaFormat, Boolean) -> Unit,
    onReset: () -> Unit,
    themeMode: ThemeMode = ThemeMode.SYSTEM,
    onThemeModeChange: (ThemeMode) -> Unit = {}
) {
    var selectedTab by remember(initialTab) { mutableIntStateOf(initialTab) }
    var myFilesSubTab by remember { mutableIntStateOf(0) }
    var activeAboutDialog by remember { mutableStateOf<String?>(null) }
    var activeBrowserUrl by remember { mutableStateOf<String?>(null) }
    var activePlayerVideo by remember { mutableStateOf<TrendingVideo?>(null) }
    var isSearchOverlayOpen by remember { mutableStateOf(false) }

    val downloadItems by DownloadRepository.downloadItems.collectAsState()
    val activeCount = downloadItems.count {
        it.status == DownloadStatus.DOWNLOADING ||
                it.status == DownloadStatus.MERGING ||
                it.status == DownloadStatus.PROCESSING ||
                it.status == DownloadStatus.QUEUED ||
                it.status == DownloadStatus.PENDING
    }
    val completedCount = downloadItems.count {
        it.status == DownloadStatus.COMPLETED
    }

    if (isSearchOverlayOpen) {
        SearchOverlayScreen(
            initialInput = currentUrl,
            onSearchExecute = { query ->
                onUrlChange(query)
                onFetchFormats(query)
                isSearchOverlayOpen = false
                selectedTab = 0
            },
            onBack = { isSearchOverlayOpen = false }
        )
        return
    }

    if (activePlayerVideo != null) {
        VideoPlayerScreen(
            video = activePlayerVideo!!,
            onBack = { activePlayerVideo = null },
            onDownloadVideo = { targetUrl ->
                onUrlChange(targetUrl)
                onFetchFormats(targetUrl)
                activePlayerVideo = null
                selectedTab = 0
            }
        )
        return
    }

    if (activeBrowserUrl != null) {
        BrowserScreen(
            initialUrl = activeBrowserUrl!!,
            onDownloadUrl = { targetUrl ->
                onUrlChange(targetUrl)
                onFetchFormats(targetUrl)
                activeBrowserUrl = null
                selectedTab = 0
            },
            onCloseBrowser = { activeBrowserUrl = null }
        )
        return
    }

    Scaffold(
        bottomBar = {
            NavigationBar {
                NavigationBarItem(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    icon = { Text("🏠", fontSize = 18.sp) },
                    label = { Text("HOME", fontWeight = if (selectedTab == 0) FontWeight.Bold else FontWeight.Normal, color = if (selectedTab == 0) androidx.compose.ui.graphics.Color.Red else androidx.compose.ui.graphics.Color.Unspecified) }
                )
                NavigationBarItem(
                    selected = selectedTab == 1,
                    onClick = {
                        myFilesSubTab = if (activeCount > 0) 0 else 1
                        selectedTab = 1
                    },
                    icon = {
                        BadgedBox(
                            badge = {
                                if (activeCount > 0) {
                                    Badge { Text("$activeCount") }
                                }
                            }
                        ) {
                            Image(
                                painter = painterResource(id = R.drawable.folder),
                                contentDescription = "MY FILES",
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    },
                    label = { Text("MY FILES", fontWeight = if (selectedTab == 1) FontWeight.Bold else FontWeight.Normal, color = if (selectedTab == 1) androidx.compose.ui.graphics.Color.Red else androidx.compose.ui.graphics.Color.Unspecified) }
                )
                NavigationBarItem(
                    selected = selectedTab == 2,
                    onClick = { selectedTab = 2 },
                    icon = {
                        Image(
                            painter = painterResource(id = R.drawable.user),
                            contentDescription = "ME",
                            modifier = Modifier.size(20.dp)
                        )
                    },
                    label = { Text("ME", fontWeight = if (selectedTab == 2) FontWeight.Bold else FontWeight.Normal, color = if (selectedTab == 2) androidx.compose.ui.graphics.Color.Red else androidx.compose.ui.graphics.Color.Unspecified) }
                )
            }
        },
        modifier = Modifier.fillMaxSize()
    ) { innerPadding ->
        Box(modifier = Modifier.padding(innerPadding)) {
            when (selectedTab) {
                0 -> MainScreenContent(
                    currentUrl = currentUrl,
                    onUrlChange = onUrlChange,
                    videoInfo = videoInfo,
                    isLoading = isLoading,
                    errorMessage = errorMessage,
                    onFetchFormats = onFetchFormats,
                    onFormatSelected = { format ->
                        onFormatSelected(format)
                        myFilesSubTab = 0
                        selectedTab = 1 // Switch to MY FILES tab upon downloading
                    },
                    onPlaylistDownloadSelected = { selectedItems, format, withSubs ->
                        onPlaylistDownloadSelected(selectedItems, format, withSubs)
                        myFilesSubTab = 0
                        selectedTab = 1 // Switch to MY FILES tab upon downloading
                    },
                    onReset = onReset,
                    onNavigateToFiles = {
                        myFilesSubTab = 1 // 1 = Downloads sub-tab!
                        selectedTab = 1
                    },
                    activeBadgeCount = activeCount
                )
                1 -> MyFilesScreen(
                    initialSubTab = myFilesSubTab,
                    onOpenUrlAndFetchFormats = { targetUrl ->
                        onUrlChange(targetUrl)
                        onFetchFormats(targetUrl)
                        selectedTab = 0
                    }
                )
                2 -> MeScreen(
                    onNavigateToFiles = {
                        myFilesSubTab = 1 // 1 = Downloads sub-tab!
                        selectedTab = 1
                    },
                    onOpenDialog = { dialog -> activeAboutDialog = dialog },
                    onFetchFormats = { url ->
                        onUrlChange(url)
                        onFetchFormats(url)
                        selectedTab = 0
                    },
                    onOpenBrowser = { url -> activeBrowserUrl = url },
                    activeBadgeCount = activeCount,
                    themeMode = themeMode,
                    onThemeModeChange = onThemeModeChange
                )
            }
        }
    }

    if (activeAboutDialog != null) {
        FullDetailPage(
            type = activeAboutDialog!!,
            onBack = { activeAboutDialog = null }
        )
    }
}

@Composable
fun MainScreenContent(
    currentUrl: String,
    onUrlChange: (String) -> Unit,
    videoInfo: VideoInfo?,
    isLoading: Boolean,
    errorMessage: String?,
    onFetchFormats: (String) -> Unit,
    onFormatSelected: (MediaFormat) -> Unit,
    onPlaylistDownloadSelected: (List<PlaylistItem>, MediaFormat, Boolean) -> Unit,
    onReset: () -> Unit,
    onNavigateToFiles: () -> Unit = {},
    activeBadgeCount: Int = 0
) {
    val keyboardController = LocalSoftwareKeyboardController.current
    val focusManager = LocalFocusManager.current

    val playlistState = rememberLazyListState()
    var isHeaderVisible by remember { mutableStateOf(true) }
    var previousIndex by remember { mutableIntStateOf(0) }
    var previousScrollOffset by remember { mutableIntStateOf(0) }

    val isPlaylist = videoInfo != null && (videoInfo.isPlaylist || videoInfo.isRadio || videoInfo.playlistItems.isNotEmpty())

    LaunchedEffect(playlistState.firstVisibleItemIndex, playlistState.firstVisibleItemScrollOffset) {
        if (isPlaylist) {
            val currentIndex = playlistState.firstVisibleItemIndex
            val currentOffset = playlistState.firstVisibleItemScrollOffset

            if (currentIndex > previousIndex || (currentIndex == previousIndex && currentOffset > previousScrollOffset + 10)) {
                // Scroll UP -> Hide search input & fetch buttons to maximize playlist vertical space
                isHeaderVisible = false
            } else if (currentIndex < previousIndex || (currentIndex == previousIndex && currentOffset < previousScrollOffset - 10)) {
                // Scroll DOWN -> Reveal search input & fetch buttons
                isHeaderVisible = true
            }

            previousIndex = currentIndex
            previousScrollOffset = currentOffset
        } else {
            isHeaderVisible = true
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // ── Top Bar Header (App Title & Top-Right Flat Line SVG Download Button) ──
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Video Downloader",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                fontSize = 17.sp
            )
            TopRightDownloadButton(
                badgeCount = activeBadgeCount,
                onClick = onNavigateToFiles
            )
        }

        AnimatedVisibility(
            visible = !isPlaylist || isHeaderVisible,
            enter = expandVertically() + fadeIn(),
            exit = shrinkVertically() + fadeOut()
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = currentUrl,
                    onValueChange = onUrlChange,
                    label = { Text("Paste video URL") },
                    placeholder = { Text("https://youtu.be/…") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Uri,
                        imeAction = ImeAction.Go
                    ),
                    keyboardActions = KeyboardActions(
                        onGo = {
                            keyboardController?.hide()
                            focusManager.clearFocus()
                            if (currentUrl.isNotBlank() && !isLoading) {
                                onFetchFormats(currentUrl)
                            }
                        }
                    ),
                    modifier = Modifier.fillMaxWidth()
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = {
                            keyboardController?.hide()
                            focusManager.clearFocus()
                            onFetchFormats(currentUrl)
                        },
                        enabled = currentUrl.isNotBlank() && !isLoading,
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(if (isLoading) "Fetching…" else "Fetch formats")
                    }
                    if (videoInfo != null || errorMessage != null || currentUrl.isNotBlank()) {
                        OutlinedButton(onClick = onReset, enabled = !isLoading) {
                            Text("Clear")
                        }
                    }
                }

                HorizontalDivider()
            }
        }

        when {
            isPlaylist -> {
                PlaylistDetailsContent(
                    videoInfo = videoInfo!!,
                    isLoadingRealtime = isLoading,
                    listState = playlistState,
                    onDownloadSelected = onPlaylistDownloadSelected,
                    modifier = Modifier.fillMaxWidth().weight(1f)
                )
            }

            isLoading -> {
                Box(
                    modifier = Modifier.fillMaxWidth().weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator()
                        Spacer(modifier = Modifier.height(12.dp))
                        Text("Fetching formats…")
                    }
                }
            }

            errorMessage != null -> {
                Box(
                    modifier = Modifier.fillMaxWidth().weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    Text(text = errorMessage, color = MaterialTheme.colorScheme.error)
                }
            }

            videoInfo != null -> {
                VideoDetailsContent(
                    videoInfo = videoInfo,
                    onFormatSelected = onFormatSelected,
                    modifier = Modifier.fillMaxWidth().weight(1f)
                )
            }

            else -> {
                Box(
                    modifier = Modifier.fillMaxWidth().weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "Paste a video or playlist URL above and tap “Fetch formats”.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

fun openDownloadFolder(context: Context) {
    val downloadDir = File(
        Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
        "VideoDownloader"
    ).apply { if (!exists()) mkdirs() }

    val intents = mutableListOf<Intent>()

    // Strategy 1: Samsung My Files direct subfolder intent
    try {
        intents.add(Intent("com.sec.android.app.myfiles.PATH_SETTINGS").apply {
            putExtra("mDirPath", downloadDir.absolutePath)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        })
    } catch (_: Exception) {}

    // Strategy 2: Xiaomi / Redmi / Poco File Explorer direct subfolder intent
    try {
        intents.add(Intent(Intent.ACTION_VIEW).apply {
            setClassName("com.mi.android.globalFileexplorer", "com.mi.android.globalFileexplorer.activity.FileActivity")
            putExtra("current_path", downloadDir.absolutePath)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        })
    } catch (_: Exception) {}

    // Strategy 3: System DocumentsUI with INITIAL_URI & DocumentUri pointing to Download/VideoDownloader
    try {
        val docUri = DocumentsContract.buildDocumentUri(
            "com.android.externalstorage.documents",
            "primary:Download/VideoDownloader"
        )
        val treeUri = DocumentsContract.buildTreeDocumentUri(
            "com.android.externalstorage.documents",
            "primary:Download/VideoDownloader"
        )
        intents.add(Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(docUri, "vnd.android.document/directory")
            putExtra("android.provider.extra.INITIAL_URI", treeUri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        })
    } catch (_: Exception) {}

    // Strategy 4: FileProvider Folder URI with vnd.android.document/directory
    try {
        val authority = "${context.packageName}.provider"
        val folderUri = FileProvider.getUriForFile(context, authority, downloadDir)
        intents.add(Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(folderUri, "vnd.android.document/directory")
            putExtra("org.openintents.extra.ABSOLUTE_PATH", downloadDir.absolutePath)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        })
    } catch (_: Exception) {}

    // Strategy 5: DownloadManager ACTION_VIEW_DOWNLOADS
    intents.add(Intent(DownloadManager.ACTION_VIEW_DOWNLOADS).apply {
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    })

    // Strategy 6: Generic Files Picker
    intents.add(Intent(Intent.ACTION_GET_CONTENT).apply {
        type = "*/*"
        addCategory(Intent.CATEGORY_OPENABLE)
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    })

    // Try starting each intent sequentially until one succeeds
    for (intent in intents) {
        try {
            context.startActivity(intent)
            return
        } catch (e: Exception) {
            Log.d("MainActivity", "Folder intent failed: ${intent.action}", e)
        }
    }

    Toast.makeText(context, "Location: Download/VideoDownloader", Toast.LENGTH_LONG).show()
}

fun shareAppLink(context: Context) {
    val driveUrl = "https://drive.google.com/drive/folders/19GIJWEZPmPr-IO5W2CxugXzX8rkAjLyq?usp=sharing"
    val shareMessage = """
        🎥 Video Downloader — Fast & Private Media Downloader!

        Download videos & audio in 4K, 1080p, 720p, MP3/M4A from YouTube, Instagram, TikTok, Facebook, Twitter, and 1000+ sites with multi-threaded parallel speed!

        📥 Download the app from Google Drive:
        $driveUrl
    """.trimIndent()

    try {
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_SUBJECT, "Video Downloader App")
            putExtra(Intent.EXTRA_TEXT, shareMessage)
        }
        context.startActivity(Intent.createChooser(intent, "Share Download Link via"))
    } catch (e: Exception) {
        Log.e("MainActivity", "Failed to share link", e)
    }
}
