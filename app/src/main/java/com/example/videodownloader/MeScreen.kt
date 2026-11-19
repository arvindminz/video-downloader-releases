package com.example.videodownloader

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.util.Log
import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

@Composable
fun MeScreen(
    onNavigateToFiles: () -> Unit,
    onOpenDialog: (String) -> Unit,
    onFetchFormats: (String) -> Unit,
    onOpenBrowser: (String) -> Unit,
    activeBadgeCount: Int = 0,
    themeMode: ThemeMode = ThemeMode.SYSTEM,
    onThemeModeChange: (ThemeMode) -> Unit = {}
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    // Installed Package Info
    val packageInfo = remember(context) {
        try {
            @Suppress("DEPRECATION")
            context.packageManager.getPackageInfo(context.packageName, 0)
        } catch (_: Exception) { null }
    }

    val installedVersionName = packageInfo?.versionName ?: "v1.0.0"
    val installedVersionCode = remember(packageInfo) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            packageInfo?.longVersionCode ?: 1L
        } else {
            @Suppress("DEPRECATION")
            packageInfo?.versionCode?.toLong() ?: 1L
        }
    }

    // In-App Update State
    var showUpdateDialog by remember { mutableStateOf(false) }
    var showRatingDialog by remember { mutableStateOf(false) }
    var showFeedbackDialog by remember { mutableStateOf(false) }

    var isCheckingUpdate by remember { mutableStateOf(false) }
    var hasUpdate by remember { mutableStateOf(false) }
    var updateVersion by remember { mutableStateOf("v1.1.1") }
    var updateDownloadUrl by remember { mutableStateOf("https://github.com/arvindminz/video-downloader-releases/releases/download/Downloader/VideoDownloader.v1.1.1.apk") }
    var updateReleaseNotes by remember { mutableStateOf("New version available!") }

    var isDownloadingUpdate by remember { mutableStateOf(false) }
    var updateProgressPercent by remember { mutableIntStateOf(0) }

    fun triggerInAppUpdate() {
        Toast.makeText(context, "Downloading APK update to Downloads section...", Toast.LENGTH_SHORT).show()
        val apkFormat = MediaFormat(
            formatId = "apk",
            extension = "apk",
            fileSizeBytes = 0L,
            resolution = "APK Update",
            formatNote = "App Update Package",
            bitrate = 0.0,
            hasVideo = false,
            hasAudio = false,
            isCombined = false
        )
        DownloadRepository.startDownload(
            context = context,
            url = updateDownloadUrl,
            title = "VideoDownloader Update ($updateVersion)",
            format = apkFormat,
            durationSeconds = 0L
        )
        onNavigateToFiles()
    }

    suspend fun performUpdateCheck() {
        isCheckingUpdate = true
        withContext(Dispatchers.IO) {
            try {
                val url = URL("https://video-downloader-telemetry.arvindminz158.workers.dev/api/check-update?version_code=$installedVersionCode")
                val conn = (url.openConnection() as HttpURLConnection).apply {
                    requestMethod = "GET"
                    connectTimeout = 4000
                    readTimeout = 4000
                }
                if (conn.responseCode == 200) {
                    val jsonStr = conn.inputStream.bufferedReader().use { it.readText() }
                    val json = JSONObject(jsonStr)
                    val updateAvailable = json.optBoolean("has_update", false)
                    withContext(Dispatchers.Main) {
                        hasUpdate = updateAvailable
                        if (updateAvailable) {
                            updateVersion = json.optString("latest_version", "v1.1.0")
                            updateDownloadUrl = json.optString("download_url", "https://github.com/arvindminz/video-downloader-releases/releases/download/Downloader/app-release.apk")
                            updateReleaseNotes = json.optString("release_notes", "New version available!")
                        }
                        isCheckingUpdate = false
                    }
                } else {
                    withContext(Dispatchers.Main) { isCheckingUpdate = false }
                }
            } catch (e: Exception) {
                Log.w("MeScreen", "Update check failed", e)
                withContext(Dispatchers.Main) { isCheckingUpdate = false }
            }
        }
    }

    fun submitRating(stars: Int) {
        RatingPromptHelper.markUserHasRated(context)
        coroutineScope.launch(Dispatchers.IO) {
            try {
                val installId = TelemetryHelper.getOrCreateInstallId(context)
                val deviceModel = "${Build.MANUFACTURER.uppercase()} ${Build.MODEL}"
                val json = JSONObject().apply {
                    put("install_id", installId)
                    put("stars", stars)
                    put("device_model", deviceModel)
                }

                val url = URL("https://video-downloader-telemetry.arvindminz158.workers.dev/api/rating")
                val conn = (url.openConnection() as HttpURLConnection).apply {
                    requestMethod = "POST"
                    setRequestProperty("Content-Type", "application/json; charset=UTF-8")
                    doOutput = true
                    connectTimeout = 4000
                    readTimeout = 4000
                }

                conn.outputStream.use { os ->
                    os.write(json.toString().toByteArray(Charsets.UTF_8))
                }

                val code = conn.responseCode
                Log.d("MeScreen", "Rating submitted response code: $code")

                withContext(Dispatchers.Main) {
                    Toast.makeText(context, "Thank you for rating us $stars stars! ⭐", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                Log.w("MeScreen", "Rating submit failed", e)
            }
        }
    }

    fun submitFeedback(review: String) {
        coroutineScope.launch(Dispatchers.IO) {
            try {
                val installId = TelemetryHelper.getOrCreateInstallId(context)
                val deviceModel = "${Build.MANUFACTURER.uppercase()} ${Build.MODEL}"
                val json = JSONObject().apply {
                    put("install_id", installId)
                    put("review", review)
                    put("device_model", deviceModel)
                }

                val url = URL("https://video-downloader-telemetry.arvindminz158.workers.dev/api/feedback")
                val conn = (url.openConnection() as HttpURLConnection).apply {
                    requestMethod = "POST"
                    setRequestProperty("Content-Type", "application/json; charset=UTF-8")
                    doOutput = true
                    connectTimeout = 4000
                    readTimeout = 4000
                }

                conn.outputStream.use { os ->
                    os.write(json.toString().toByteArray(Charsets.UTF_8))
                }

                val code = conn.responseCode
                Log.d("MeScreen", "Feedback submitted response code: $code")

                withContext(Dispatchers.Main) {
                    Toast.makeText(context, "Thank you for your feedback!", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                Log.w("MeScreen", "Feedback submit failed", e)
            }
        }
    }

    // Initial background check for Cloudflare Worker API updates
    LaunchedEffect(Unit) {
        performUpdateCheck()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // ── Top Header Card (Title, 3-Option Theme Toggle & Top-Right Download Button) ──
        Card(
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(14.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Image(
                        painter = painterResource(id = R.drawable.user),
                        contentDescription = "User Profile",
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = "Video Downloader",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    // 3-Option Theme Toggle Switch (SYSTEM -> DARK -> LIGHT)
                    val (themeIcon, themeLabel) = when (themeMode) {
                        ThemeMode.SYSTEM -> "⚙️" to "System"
                        ThemeMode.DARK -> "🌙" to "Dark"
                        ThemeMode.LIGHT -> "☀️" to "Light"
                    }

                    Surface(
                        shape = RoundedCornerShape(20.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                        modifier = Modifier.clickable {
                            val nextMode = when (themeMode) {
                                ThemeMode.SYSTEM -> ThemeMode.DARK
                                ThemeMode.DARK -> ThemeMode.LIGHT
                                ThemeMode.LIGHT -> ThemeMode.SYSTEM
                            }
                            onThemeModeChange(nextMode)
                        }
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(themeIcon, fontSize = 13.sp)
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(themeLabel, fontSize = 12.sp, fontWeight = FontWeight.Medium)
                        }
                    }

                    Spacer(modifier = Modifier.width(6.dp))

                    // Top Right SVG Download Button
                    TopRightDownloadButton(
                        badgeCount = activeBadgeCount,
                        onClick = onNavigateToFiles
                    )
                }
            }
        }

        // ── Prominent In-App Update Banner (Shows if new version is available) ──
        if (hasUpdate) {
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
                modifier = Modifier.fillMaxWidth().clickable { triggerInAppUpdate() }
            ) {
                Row(
                    modifier = Modifier.padding(14.dp).fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                        Text("🚀", fontSize = 24.sp)
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(
                                text = "New Update Available! ($updateVersion)",
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                            Text(
                                text = if (isDownloadingUpdate) "Downloading update... $updateProgressPercent%" else updateReleaseNotes,
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = { triggerInAppUpdate() },
                        enabled = !isDownloadingUpdate,
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                    ) {
                        Text(
                            text = if (isDownloadingUpdate) "$updateProgressPercent%" else "Update",
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp
                        )
                    }
                }
            }
        }

        // ── Kept 7 Items Menu Grid (My Files, Share, Like us, Update, Feedback, Settings, Privacy) ──
        Card(
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
        ) {
            Column(modifier = Modifier.padding(vertical = 20.dp, horizontal = 12.dp)) {
                // Row 1: My Files, Share, Like us, Update
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    MeMenuItem(
                        iconRes = R.drawable.folder,
                        label = "My Files",
                        onClick = { openDownloadFolder(context) }
                    )
                    MeMenuItem(
                        iconRes = R.drawable.share,
                        label = "Share",
                        onClick = { shareAppLink(context, updateDownloadUrl, updateVersion) }
                    )
                    MeMenuItem(
                        iconText = "👍",
                        label = "Like us",
                        onClick = { showRatingDialog = true }
                    )
                    MeMenuItem(
                        iconRes = R.drawable.refresh,
                        label = if (isDownloadingUpdate) "$updateProgressPercent%" else "Update",
                        hasBadge = hasUpdate,
                        onClick = {
                            showUpdateDialog = true
                            coroutineScope.launch {
                                performUpdateCheck()
                            }
                        }
                    )
                }

                Spacer(modifier = Modifier.height(24.dp))

                // Row 2: Feedback, Settings, Privacy
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    MeMenuItem(
                        iconRes = R.drawable.feedback,
                        label = "Feedback",
                        onClick = { showFeedbackDialog = true }
                    )
                    MeMenuItem(
                        iconRes = R.drawable.settings,
                        label = "Settings",
                        onClick = { onOpenDialog("settings") }
                    )
                    MeMenuItem(
                        iconRes = R.drawable.privacy,
                        label = "Privacy",
                        onClick = { onOpenDialog("privacy") }
                    )
                }
            }
        }
    }

    if (showUpdateDialog) {
        AppUpdateDialog(
            installedVersionName = installedVersionName,
            installedVersionCode = installedVersionCode,
            isChecking = isCheckingUpdate,
            hasUpdate = hasUpdate,
            updateVersion = updateVersion,
            updateReleaseNotes = updateReleaseNotes,
            onUpdateNow = { triggerInAppUpdate() },
            onDismiss = { showUpdateDialog = false }
        )
    }

    if (showRatingDialog) {
        RatingDialog(
            onRatingSubmit = { stars -> submitRating(stars) },
            onDismiss = { showRatingDialog = false }
        )
    }

    if (showFeedbackDialog) {
        FeedbackDialog(
            onFeedbackSubmit = { review -> submitFeedback(review) },
            onDismiss = { showFeedbackDialog = false }
        )
    }
}

@Composable
fun RatingDialog(
    onRatingSubmit: (Int) -> Unit,
    onDismiss: () -> Unit
) {
    var selectedStars by remember { mutableIntStateOf(5) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("👍", fontSize = 22.sp)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Rate Video Downloader", fontWeight = FontWeight.Bold, fontSize = 16.sp)
            }
        },
        text = {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)
            ) {
                Text(
                    text = "Tap a star to rate your experience:",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(modifier = Modifier.height(14.dp))

                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    (1..5).forEach { star ->
                        Text(
                            text = if (star <= selectedStars) "⭐" else "☆",
                            fontSize = 32.sp,
                            modifier = Modifier.clickable { selectedStars = star }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                Text(
                    text = when (selectedStars) {
                        5 -> "😍 Excellent!"
                        4 -> "😊 Very Good!"
                        3 -> "😐 Good"
                        2 -> "🙁 Fair"
                        else -> "😢 Poor"
                    },
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onRatingSubmit(selectedStars)
                    onDismiss()
                }
            ) {
                Text("Submit Rating", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            OutlinedButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@Composable
fun FeedbackDialog(
    onFeedbackSubmit: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var reviewText by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("💬", fontSize = 22.sp)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Submit Feedback & Review", fontWeight = FontWeight.Bold, fontSize = 16.sp)
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "Tell us what features or improvements you'd like to see:",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                OutlinedTextField(
                    value = reviewText,
                    onValueChange = { reviewText = it },
                    placeholder = { Text("Write your review or feedback here...", fontSize = 13.sp) },
                    modifier = Modifier.fillMaxWidth().height(120.dp),
                    maxLines = 5
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (reviewText.isNotBlank()) {
                        onFeedbackSubmit(reviewText.trim())
                    }
                    onDismiss()
                },
                enabled = reviewText.isNotBlank()
            ) {
                Text("Submit", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            OutlinedButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@Composable
fun AppUpdateDialog(
    installedVersionName: String,
    installedVersionCode: Long,
    isChecking: Boolean,
    hasUpdate: Boolean,
    updateVersion: String,
    updateReleaseNotes: String,
    onUpdateNow: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("🚀", fontSize = 22.sp)
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "App Update Check",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text(
                            text = "Installed App Info:",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Version: $installedVersionName",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Version Code: $installedVersionCode",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                HorizontalDivider()

                if (isChecking) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center,
                        modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp)
                    ) {
                        CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                        Spacer(modifier = Modifier.width(10.dp))
                        Text("Checking for updates...", fontSize = 13.sp)
                    }
                } else if (hasUpdate) {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = MaterialTheme.colorScheme.primaryContainer
                        ) {
                            Column(modifier = Modifier.padding(10.dp)) {
                                Text(
                                    text = "🎉 New Update Available: $updateVersion",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.sp,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = updateReleaseNotes,
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.85f)
                                )
                            }
                        }
                    }
                } else {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(vertical = 8.dp)
                    ) {
                        Text("✅ ", fontSize = 16.sp)
                        Text(
                            text = "Your app is up to date!",
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }
        },
        confirmButton = {
            if (hasUpdate && !isChecking) {
                Button(
                    onClick = {
                        onDismiss()
                        onUpdateNow()
                    }
                ) {
                    Text("Update Now", fontWeight = FontWeight.Bold)
                }
            } else {
                Button(onClick = onDismiss) {
                    Text("OK", fontWeight = FontWeight.Bold)
                }
            }
        },
        dismissButton = {
            if (hasUpdate && !isChecking) {
                OutlinedButton(onClick = onDismiss) {
                    Text("Later")
                }
            }
        }
    )
}

@Composable
fun MeMenuItem(
    iconText: String? = null,
    iconRes: Int? = null,
    label: String,
    hasBadge: Boolean = false,
    onClick: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.width(75.dp).clickable { onClick() }
    ) {
        Box(
            modifier = Modifier
                .size(44.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
            contentAlignment = Alignment.Center
        ) {
            if (iconRes != null) {
                Image(
                    painter = painterResource(id = iconRes),
                    contentDescription = label,
                    modifier = Modifier.size(22.dp)
                )
            } else if (iconText != null) {
                Text(iconText, fontSize = 20.sp)
            }
            if (hasBadge) {
                Box(
                    modifier = Modifier
                        .size(10.dp)
                        .clip(CircleShape)
                        .background(Color.Red)
                        .align(Alignment.TopEnd)
                )
            }
        }
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = label,
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}
