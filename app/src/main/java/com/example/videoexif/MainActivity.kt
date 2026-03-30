package com.example.videoexif

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.CloudSync
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
import com.example.videoexif.presentation.sync.SyncScreen
import com.example.videoexif.presentation.sync.SyncViewModel
import com.example.videoexif.ui.theme.VideoExifTheme
import kotlinx.coroutines.launch

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
                    modelClass.isAssignableFrom(SyncViewModel::class.java) -> SyncViewModel(videoRepository, applicationContext) as T
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
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    
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
        var lastBackPressTime by remember { mutableLongStateOf(0L) }

        BackHandler {
            if (selectedVideo != null) {
                selectedVideo = null
            } else {
                val currentTime = System.currentTimeMillis()
                if (currentTime - lastBackPressTime < 2000) {
                    (context as? ComponentActivity)?.finish()
                } else {
                    lastBackPressTime = currentTime
                    scope.launch {
                        snackbarHostState.showSnackbar(
                            message = "Tap again to exit the app",
                            duration = SnackbarDuration.Short
                        )
                    }
                }
            }
        }

        Scaffold(
            snackbarHost = { SnackbarHost(snackbarHostState) },
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
                        selected = currentTab == 1 && selectedVideo == null,
                        onClick = { 
                            currentTab = 1
                            selectedVideo = null 
                        },
                        icon = { Icon(Icons.AutoMirrored.Filled.List, contentDescription = "Gallery") },
                        label = { Text("Gallery") }
                    )
                    NavigationBarItem(
                        selected = currentTab == 2 && selectedVideo == null,
                        onClick = { 
                            currentTab = 2
                            selectedVideo = null 
                        },
                        icon = { Icon(Icons.Default.CloudSync, contentDescription = "Sync") },
                        label = { Text("Sync") }
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
                        2 -> {
                            val syncViewModel: SyncViewModel = viewModel(factory = factory)
                            SyncScreen(viewModel = syncViewModel)
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

private val REQUIRED_PERMISSIONS = arrayOf(
    Manifest.permission.CAMERA,
    Manifest.permission.RECORD_AUDIO,
    Manifest.permission.ACCESS_FINE_LOCATION,
    Manifest.permission.ACCESS_COARSE_LOCATION
)
