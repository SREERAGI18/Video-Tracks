package com.example.videoexif.presentation.playback

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.view.ViewGroup
import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.FileProvider
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import com.example.videoexif.domain.model.VideoData
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.maps.android.compose.*
import kotlinx.coroutines.delay
import java.io.File

@Composable
fun VideoPlaybackScreen(
    videoData: VideoData,
    viewModel: PlaybackViewModel,
    onBack: () -> Unit
) {
    val state by viewModel.state.collectAsState()
    val context = LocalContext.current

    val exoPlayer = remember {
        ExoPlayer.Builder(context).build().apply {
            setMediaItem(MediaItem.fromUri(Uri.fromFile(videoData.videoFile)))
            prepare()
        }
    }

    DisposableEffect(videoData) {
        viewModel.init(videoData)
        onDispose {
            exoPlayer.release()
        }
    }

    LaunchedEffect(exoPlayer) {
        while (true) {
            viewModel.onIntent(PlaybackIntent.UpdatePosition(exoPlayer.currentPosition))
            delay(100)
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Button(onClick = onBack) { Text("Back to Gallery") }
            Button(
                onClick = { openGpxInMaps(context, videoData.gpxFile) },
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.secondary
                )
            ) {
                Text("Open in Maps")
            }
            Button(
                onClick = { shareGpxFile(context, videoData.gpxFile, videoData.videoFile) },
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.tertiary
                )
            ) {
                Text("Share GPS")
            }
        }
        
        AndroidView(
            factory = { ctx ->
                PlayerView(ctx).apply {
                    player = exoPlayer
                    layoutParams = ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        (250 * ctx.resources.displayMetrics.density).toInt()
                    )
                }
            },
            modifier = Modifier
                .fillMaxWidth()
                .height(250.dp)
        )

        Box(modifier = Modifier.weight(1f)) {
            val cameraPositionState = rememberCameraPositionState()
            
            LaunchedEffect(state.currentLocation) {
                state.currentLocation?.let {
                    cameraPositionState.position = CameraPosition.fromLatLngZoom(
                        LatLng(it.latitude, it.longitude), 16f
                    )
                }
            }

            GoogleMap(
                modifier = Modifier.fillMaxSize(),
                cameraPositionState = cameraPositionState
            ) {
                state.currentLocation?.let {
                    Marker(
                        state = MarkerState(position = LatLng(it.latitude, it.longitude)),
                        title = "Current Position"
                    )
                }
                
                Polyline(
                    points = state.locationPoints.map { LatLng(it.latitude, it.longitude) },
                    color = Color.Blue,
                    width = 5f
                )
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
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(intent, "Open GPS Track"))
    } catch (e: Exception) {
        Toast.makeText(context, "Error sharing GPX", Toast.LENGTH_SHORT).show()
    }
}

private fun openGpxInMaps(context: Context, gpxFile: File) {
    try {
        val contentUri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            gpxFile
        )
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(contentUri, "application/gpx+xml")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(intent)
    } catch (e: Exception) {
        Toast.makeText(context, "Error opening GPX", Toast.LENGTH_SHORT).show()
    }
}
