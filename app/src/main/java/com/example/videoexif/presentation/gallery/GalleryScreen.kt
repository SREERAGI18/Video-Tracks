package com.example.videoexif.presentation.gallery

import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.material.icons.filled.ClosedCaption
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import com.example.videoexif.domain.model.VideoData
import java.io.File

@Composable
fun GalleryScreen(
    viewModel: GalleryViewModel,
    onVideoSelected: (VideoData) -> Unit
) {
    val state by viewModel.state.collectAsState()
    val context = LocalContext.current

    if (state.isLoading) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
    } else if (state.videos.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("No recordings found")
        }
    } else {
        LazyColumn(modifier = Modifier.fillMaxSize()) {
            items(state.videos) { video ->
                VideoItem(
                    video = video,
                    onViewVideo = { onVideoSelected(video) },
                    onOpenInMaps = { openGpxInMaps(context, video.gpxFile) },
                    onShareGps = { shareGpxFile(context, video.gpxFile, video.videoFile) },
                    onShareSrt = { video.srtFile?.let { shareSrtFile(context, it, video.videoFile) } }
                )
            }
        }
    }
}

@Composable
fun VideoItem(
    video: VideoData,
    onViewVideo: () -> Unit,
    onOpenInMaps: () -> Unit,
    onShareGps: () -> Unit,
    onShareSrt: () -> Unit
) {
    Card(
        modifier = Modifier.padding(8.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null
                ) {
                    onViewVideo()
                }
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = video.videoFile.nameWithoutExtension,
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.weight(1f)
            )
            
            Row(
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                IconButton(onClick = onOpenInMaps) {
                    Icon(
                        imageVector = Icons.Default.LocationOn,
                        contentDescription = "Open in Maps",
                        tint = MaterialTheme.colorScheme.secondary
                    )
                }
                IconButton(onClick = onShareGps) {
                    Icon(
                        imageVector = Icons.Default.Share,
                        contentDescription = "Share GPS",
                        tint = MaterialTheme.colorScheme.tertiary
                    )
                }
                if (video.srtFile != null) {
                    IconButton(onClick = onShareSrt) {
                        Icon(
                            imageVector = Icons.Default.ClosedCaption,
                            contentDescription = "Share Subtitles (SRT)",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }
        }
    }
}

private fun shareGpxFile(context: Context, gpxFile: File, videoFile: File) {
    try {
        val contentUri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            gpxFile
        )
        
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "application/gpx+xml"
            putExtra(Intent.EXTRA_STREAM, contentUri)
            putExtra(Intent.EXTRA_SUBJECT, "Video Recording GPS Track")
            putExtra(Intent.EXTRA_TEXT, "GPS track for video: ${videoFile.name}")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        
        val chooser = Intent.createChooser(intent, "Open GPS Track in Map App")
        context.startActivity(chooser)
    } catch (e: Exception) {
        Toast.makeText(context, "Error sharing GPX file", Toast.LENGTH_SHORT).show()
    }
}

private fun shareSrtFile(context: Context, srtFile: File, videoFile: File) {
    try {
        val contentUri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            srtFile
        )
        
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_STREAM, contentUri)
            putExtra(Intent.EXTRA_SUBJECT, "Video Recording Subtitles")
            putExtra(Intent.EXTRA_TEXT, "Subtitles for video: ${videoFile.name}")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        
        val chooser = Intent.createChooser(intent, "Share Subtitles (SRT)")
        context.startActivity(chooser)
    } catch (e: Exception) {
        Toast.makeText(context, "Error sharing SRT file", Toast.LENGTH_SHORT).show()
    }
}

private fun openGpxInMaps(context: Context, gpxFile: File) {
    try {
        val contentUri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            gpxFile
        )
        
        val mapsIntent = Intent(Intent.ACTION_VIEW).apply {
            data = contentUri
            type = "application/gpx+xml"
            setPackage("com.google.android.apps.maps")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        
        if (mapsIntent.resolveActivity(context.packageManager) != null) {
            context.startActivity(mapsIntent)
        } else {
            val genericIntent = Intent(Intent.ACTION_VIEW).apply {
                data = contentUri
                type = "application/gpx+xml"
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            
            if (genericIntent.resolveActivity(context.packageManager) != null) {
                context.startActivity(genericIntent)
            } else {
                Toast.makeText(context, "No app found to open GPX files", Toast.LENGTH_SHORT).show()
            }
        }
    } catch (e: Exception) {
        Toast.makeText(context, "Error opening GPX file", Toast.LENGTH_SHORT).show()
    }
}
