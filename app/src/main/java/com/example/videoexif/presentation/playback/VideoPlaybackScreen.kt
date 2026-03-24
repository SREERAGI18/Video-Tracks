package com.example.videoexif.presentation.playback

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.view.ViewGroup
import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.FileProvider
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import com.example.videoexif.domain.model.VideoData
import kotlinx.coroutines.delay
import org.osmdroid.config.Configuration
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.Polyline
import java.io.File

@Composable
fun VideoPlaybackScreen(
    videoData: VideoData,
    viewModel: PlaybackViewModel,
    onBack: () -> Unit
) {
    val state by viewModel.state.collectAsState()
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    // Initialize OSMdroid configuration
    remember {
        Configuration.getInstance().load(
            context,
            context.getSharedPreferences("osmdroid", Context.MODE_PRIVATE)
        )
        true
    }

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
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
            }
            
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                IconButton(onClick = { openGpxInMaps(context, videoData.gpxFile) }) {
                    Icon(
                        imageVector = Icons.Default.LocationOn,
                        contentDescription = "Open in Maps",
                        tint = MaterialTheme.colorScheme.secondary
                    )
                }
                IconButton(onClick = { shareGpxFile(context, videoData.gpxFile, videoData.videoFile) }) {
                    Icon(
                        imageVector = Icons.Default.Share,
                        contentDescription = "Share GPS",
                        tint = MaterialTheme.colorScheme.tertiary
                    )
                }
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

        Box(
            modifier = Modifier
                .weight(1f)
                .clipToBounds()
        ) {
            val mapView = remember { MapView(context) }
            
            // Handle OSMdroid Lifecycle
            DisposableEffect(lifecycleOwner) {
                val observer = LifecycleEventObserver { _, event ->
                    when (event) {
                        Lifecycle.Event.ON_RESUME -> mapView.onResume()
                        Lifecycle.Event.ON_PAUSE -> mapView.onPause()
                        else -> {}
                    }
                }
                lifecycleOwner.lifecycle.addObserver(observer)
                onDispose {
                    lifecycleOwner.lifecycle.removeObserver(observer)
                }
            }

            AndroidView(
                modifier = Modifier.fillMaxSize().clipToBounds(),
                factory = { 
                    mapView.apply {
                        setMultiTouchControls(true)
                        controller.setZoom(16.0)
                    }
                },
                update = { mv ->
                    mv.overlays.clear()
                    
                    // Add Path Polyline
                    val pathPoints = state.locationPoints.map { GeoPoint(it.latitude, it.longitude) }
                    if (pathPoints.isNotEmpty()) {
                        val polyline = Polyline(mv)
                        polyline.setPoints(pathPoints)
                        polyline.outlinePaint.color = android.graphics.Color.BLUE
                        polyline.outlinePaint.strokeWidth = 5f
                        mv.overlays.add(polyline)
                    }

                    // Add Current Location Marker
                    state.currentLocation?.let { loc ->
                        val currentPoint = GeoPoint(loc.latitude, loc.longitude)
                        val marker = Marker(mv)
                        marker.position = currentPoint
                        marker.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                        marker.title = "Current Position"
                        mv.overlays.add(marker)
                        
                        mv.controller.animateTo(currentPoint)
                    }
                    
                    mv.invalidate()
                }
            )
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
