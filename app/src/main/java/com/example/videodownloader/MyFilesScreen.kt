package com.example.videodownloader

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch

@Composable
fun MyFilesScreen(
    initialSubTab: Int = 0,
    onOpenUrlAndFetchFormats: (String) -> Unit = {},
    onPlayLocalVideo: (DownloadItem) -> Unit = {}
) {
    val downloadItems by DownloadRepository.downloadItems.collectAsState()
    val scope = rememberCoroutineScope()

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

    val pagerState = rememberPagerState(
        initialPage = initialSubTab.coerceIn(0, 1),
        pageCount = { 2 }
    )

    LaunchedEffect(initialSubTab) {
        if (pagerState.currentPage != initialSubTab) {
            pagerState.animateScrollToPage(initialSubTab.coerceIn(0, 1))
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        // ── Tab Bar (Queue & Downloads) ──
        TabRow(selectedTabIndex = pagerState.currentPage) {
            Tab(
                selected = pagerState.currentPage == 0,
                onClick = {
                    scope.launch {
                        pagerState.animateScrollToPage(0)
                    }
                },
                text = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Queue", fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
                        if (activeCount > 0) {
                            Spacer(modifier = Modifier.width(6.dp))
                            Badge { Text("$activeCount") }
                        }
                    }
                }
            )
            Tab(
                selected = pagerState.currentPage == 1,
                onClick = {
                    scope.launch {
                        pagerState.animateScrollToPage(1)
                    }
                },
                text = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Downloads", fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
                        if (completedCount > 0) {
                            Spacer(modifier = Modifier.width(6.dp))
                            Badge { Text("$completedCount") }
                        }
                    }
                }
            )
        }

        // ── Horizontal Pager (Slide Left or Right to Switch Tabs) ──
        HorizontalPager(
            state = pagerState,
            modifier = Modifier.fillMaxSize().weight(1f)
        ) { page ->
            when (page) {
                0 -> QueueScreen(onNavigateToDownloads = { scope.launch { pagerState.animateScrollToPage(1) } })
                1 -> DownloadsScreen(
                    onOpenUrlAndFetchFormats = onOpenUrlAndFetchFormats,
                    onPlayLocalVideo = onPlayLocalVideo
                )
            }
        }
    }
}
