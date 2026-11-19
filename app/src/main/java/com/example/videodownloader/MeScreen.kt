package com.example.videodownloader

import android.content.Intent
import android.net.Uri
import android.util.Log
import android.widget.Toast
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
    activeBadgeCount: Int = 0
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    var isDarkMode by remember { mutableStateOf(true) }

    // In-App Update State
    var hasUpdate by remember { mutableStateOf(false) }
    var updateVersion by remember { mutableStateOf("v1.1.0") }
    var updateDownloadUrl by remember { mutableStateOf("https://github.com/arvindminz/video-downloader-releases/releases/download/Downloader/app-release.apk") }
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

    // Check Cloudflare Worker API for App Updates
    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) {
            try {
                val currentVersionCode = try {
                    @Suppress("DEPRECATION")
                    context.packageManager.getPackageInfo(context.packageName, 0).versionCode
                } catch (_: Exception) { 1 }

                val url = URL("https://video-downloader-telemetry.arvindminz158.workers.dev/api/check-update?version_code=$currentVersionCode")
                val conn = (url.openConnection() as HttpURLConnection).apply {
                    requestMethod = "GET"
                    connectTimeout = 4000
                    readTimeout = 4000
                }
                if (conn.responseCode == 200) {
                    val jsonStr = conn.inputStream.bufferedReader().use { it.readText() }
                    val json = JSONObject(jsonStr)
                    val updateAvailable = json.optBoolean("has_update", false)
                    if (updateAvailable) {
                        withContext(Dispatchers.Main) {
                            hasUpdate = true
                            updateVersion = json.optString("latest_version", "v1.1.0")
                            updateDownloadUrl = json.optString("download_url", "https://drive.google.com/drive/folders/19GIJWEZPmPr-IO5W2CxugXzX8rkAjLyq?usp=sharing")
                            updateReleaseNotes = json.optString("release_notes", "New version available!")
                        }
                    }
                }
            } catch (e: Exception) {
                Log.w("MeScreen", "Update check failed", e)
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // ── Top Header Card (Title, Theme Toggle & Top-Right Download Button) ──
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
                    Text("📱", fontSize = 20.sp)
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = "Video Downloader",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    // Theme Toggle Switch ☀️🌙
                    Surface(
                        shape = RoundedCornerShape(20.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                        modifier = Modifier.clickable { isDarkMode = !isDarkMode }
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("☀️", fontSize = 14.sp)
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("🌙", fontSize = 14.sp)
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
                    MeMenuItem(iconText = "📥", label = "My Files", onClick = onNavigateToFiles)
                    MeMenuItem(iconText = "🔻", label = "Share", onClick = { shareAppLink(context) })
                    MeMenuItem(iconText = "👍", label = "Like us", onClick = { shareAppLink(context) })
                    MeMenuItem(
                        iconText = "⬆️",
                        label = if (isDownloadingUpdate) "$updateProgressPercent%" else "Update",
                        hasBadge = hasUpdate,
                        onClick = {
                            if (hasUpdate) {
                                triggerInAppUpdate()
                            } else {
                                Toast.makeText(context, "App is up to date!", Toast.LENGTH_SHORT).show()
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
                    MeMenuItem(iconText = "💬", label = "Feedback", onClick = { onOpenDialog("developer") })
                    MeMenuItem(iconText = "⚙️", label = "Settings", onClick = { onOpenDialog("settings") })
                    MeMenuItem(iconText = "🔒", label = "Privacy", onClick = { onOpenDialog("privacy") })
                }
            }
        }
    }
}

@Composable
fun MeMenuItem(
    iconText: String,
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
            Text(iconText, fontSize = 20.sp)
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
