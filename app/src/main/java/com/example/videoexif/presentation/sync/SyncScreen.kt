package com.example.videoexif.presentation.sync

import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.videoexif.domain.model.VideoData
import kotlinx.coroutines.launch

@Composable
fun SyncScreen(
    viewModel: SyncViewModel
) {
    val state by viewModel.state.collectAsState()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val pagerState = rememberPagerState(pageCount = { 2 })

    LaunchedEffect(Unit) {
        viewModel.effect.collect { effect ->
            when (effect) {
                is SyncEffect.ShowToast -> {
                    Toast.makeText(context, effect.message, Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        TabRow(
            selectedTabIndex = pagerState.currentPage,
            containerColor = MaterialTheme.colorScheme.surface,
            contentColor = MaterialTheme.colorScheme.primary
        ) {
            Tab(
                selected = pagerState.currentPage == 0,
                onClick = { scope.launch { pagerState.animateScrollToPage(0) } },
                text = { Text("Unsynced (${state.unsyncedVideos.size})") }
            )
            Tab(
                selected = pagerState.currentPage == 1,
                onClick = { scope.launch { pagerState.animateScrollToPage(1) } },
                text = { Text("Synced (${state.syncedVideos.size})") }
            )
        }

        Box(modifier = Modifier.weight(1f)) {
            if (state.isLoading) {
                CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
            } else {
                HorizontalPager(
                    state = pagerState,
                    modifier = Modifier.fillMaxSize(),
                    verticalAlignment = Alignment.Top
                ) { page ->
                    when (page) {
                        0 -> VideoList(
                            videos = state.unsyncedVideos,
                            isSynced = false,
                            emptyMessage = "No unsynced videos",
                            onUploadIntent = { viewModel.onIntent(it) }
                        )
                        1 -> VideoList(
                            videos = state.syncedVideos,
                            isSynced = true,
                            emptyMessage = "No synced videos",
                            onUploadIntent = { viewModel.onIntent(it) }
                        )
                    }
                }
            }

            if (state.isUploading) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Card {
                            Row(
                                modifier = Modifier.padding(16.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                CircularProgressIndicator(modifier = Modifier.size(24.dp))
                                Text("Uploading...")
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun VideoList(
    videos: List<VideoData>,
    isSynced: Boolean,
    emptyMessage: String,
    onUploadIntent: (SyncIntent) -> Unit
) {
    if (videos.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(emptyMessage)
        }
    } else {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(videos) { video ->
                SyncVideoItem(
                    video = video,
                    isSynced = isSynced,
                    onUploadIntent = onUploadIntent
                )
            }
        }
    }
}

@Composable
fun SyncVideoItem(
    video: VideoData,
    isSynced: Boolean,
    onUploadIntent: (SyncIntent) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (isSynced) 
                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                else MaterialTheme.colorScheme.surface
        )
    ) {
        Row(
            modifier = Modifier
                .padding(16.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = video.videoFile.nameWithoutExtension,
                    style = MaterialTheme.typography.titleMedium
                )
                Text(
                    text = if (isSynced) "Synced" else "Not Synced",
                    style = MaterialTheme.typography.bodySmall,
                    color = if (isSynced) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
                )
            }

            if (isSynced) {
                Icon(
                    imageVector = Icons.Default.CloudDone,
                    contentDescription = "Synced",
                    tint = MaterialTheme.colorScheme.primary
                )
            } else {
                IconButton(onClick = { onUploadIntent(SyncIntent.UploadAll(video)) }) {
                    Icon(
                        imageVector = Icons.Default.CloudUpload,
                        contentDescription = "Upload all files",
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }
    }
}
