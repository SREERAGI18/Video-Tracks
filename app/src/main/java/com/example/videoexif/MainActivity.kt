package com.example.videoexif

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.videoexif.data.datasource.LocationTracker
import com.example.videoexif.data.datasource.VideoRecorder
import com.example.videoexif.data.repository.VideoRepositoryImpl
import com.example.videoexif.domain.model.VideoData
import com.example.videoexif.presentation.gallery.GalleryScreen
import com.example.videoexif.presentation.gallery.GalleryViewModel
import com.example.videoexif.presentation.playback.PlaybackViewModel
import com.example.videoexif.presentation.playback.VideoPlaybackScreen
import com.example.videoexif.presentation.record.RecordScreen
import com.example.videoexif.presentation.record.RecordViewModel
import com.example.videoexif.ui.theme.VideoExifTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        
        val locationTracker = LocationTracker(applicationContext)
        val videoRecorder = VideoRecorder(applicationContext)
        val videoRepository = VideoRepositoryImpl(applicationContext, videoRecorder, locationTracker)
        
        val factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                return when {
                    modelClass.isAssignableFrom(RecordViewModel::class.java) -> RecordViewModel(videoRepository) as T
                    modelClass.isAssignableFrom(GalleryViewModel::class.java) -> GalleryViewModel(videoRepository) as T
                    modelClass.isAssignableFrom(PlaybackViewModel::class.java) -> PlaybackViewModel() as T
                    else -> throw IllegalArgumentException("Unknown ViewModel class")
                }
            }
        }

        setContent {
            VideoExifTheme {
                MainScreen(factory)
            }
        }
    }
}

@Composable
fun MainScreen(factory: ViewModelProvider.Factory) {
    val context = LocalContext.current
    var hasPermissions by remember {
        mutableStateOf(
            REQUIRED_PERMISSIONS.all {
                ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
            }
        )
    }

    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        hasPermissions = permissions.values.all { it }
    }

    LaunchedEffect(Unit) {
        if (!hasPermissions) {
            launcher.launch(REQUIRED_PERMISSIONS)
        }
    }

    if (hasPermissions) {
        var currentTab by remember { mutableIntStateOf(0) }
        var selectedVideo by remember { mutableStateOf<VideoData?>(null) }

        Scaffold(
            bottomBar = {
                NavigationBar {
                    NavigationBarItem(
                        selected = currentTab == 0 && selectedVideo == null,
                        onClick = { 
                            currentTab = 0
                            selectedVideo = null 
                        },
                        icon = { Icon(Icons.Default.PlayArrow, contentDescription = "Record") },
                        label = { Text("Record") }
                    )
                    NavigationBarItem(
                        selected = (currentTab == 1 || selectedVideo != null),
                        onClick = { 
                            currentTab = 1
                            selectedVideo = null 
                        },
                        icon = { Icon(Icons.AutoMirrored.Filled.List, contentDescription = "Gallery") },
                        label = { Text("Gallery") }
                    )
                }
            }
        ) { padding ->
            Box(modifier = Modifier.padding(padding)) {
                if (selectedVideo != null) {
                    val playbackViewModel: PlaybackViewModel = viewModel(factory = factory, key = selectedVideo?.videoFile?.path)
                    VideoPlaybackScreen(
                        videoData = selectedVideo!!,
                        viewModel = playbackViewModel,
                        onBack = { selectedVideo = null }
                    )
                } else {
                    when (currentTab) {
                        0 -> {
                            val recordViewModel: RecordViewModel = viewModel(factory = factory)
                            RecordScreen(viewModel = recordViewModel)
                        }
                        1 -> {
                            val galleryViewModel: GalleryViewModel = viewModel(factory = factory)
                            GalleryScreen(
                                viewModel = galleryViewModel,
                                onVideoSelected = { video -> selectedVideo = video }
                            )
                        }
                    }
                }
            }
        }
    } else {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Button(onClick = { launcher.launch(REQUIRED_PERMISSIONS) }) {
                Text("Grant Permissions")
            }
        }
    }
}

private val REQUIRED_PERMISSIONS = buildList {
    add(Manifest.permission.CAMERA)
    add(Manifest.permission.RECORD_AUDIO)
    add(Manifest.permission.ACCESS_FINE_LOCATION)
    add(Manifest.permission.ACCESS_COARSE_LOCATION)
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        add(Manifest.permission.READ_MEDIA_VIDEO)
    } else {
        add(Manifest.permission.READ_EXTERNAL_STORAGE)
        if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.P) {
            add(Manifest.permission.WRITE_EXTERNAL_STORAGE)
        }
    }
}.toTypedArray()
