package com.example.videoexif.presentation.record

import android.graphics.SurfaceTexture
import android.view.TextureView
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

@Composable
fun RecordScreen(viewModel: RecordViewModel) {
    val state by viewModel.state.collectAsState()
    val context = LocalContext.current
    var surfaceTexture by remember { mutableStateOf<SurfaceTexture?>(null) }

    LaunchedEffect(viewModel.effect) {
        viewModel.effect.collect { effect ->
            when (effect) {
                is RecordEffect.ShowToast -> {
                    Toast.makeText(context, effect.message, Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        AndroidView(
            factory = { ctx ->
                TextureView(ctx).apply {
                    layoutParams = ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                    )
                    surfaceTextureListener = object : TextureView.SurfaceTextureListener {
                        override fun onSurfaceTextureAvailable(st: SurfaceTexture, width: Int, height: Int) {
                            surfaceTexture = st
                            viewModel.onIntent(RecordIntent.StartPreview(st))
                        }
                        override fun onSurfaceTextureSizeChanged(st: SurfaceTexture, width: Int, height: Int) {}
                        override fun onSurfaceTextureDestroyed(st: SurfaceTexture): Boolean {
                            viewModel.onIntent(RecordIntent.StopPreview)
                            return true
                        }
                        override fun onSurfaceTextureUpdated(st: SurfaceTexture) {}
                    }
                }
            },
            modifier = Modifier.weight(1f)
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.Center
        ) {
            Button(
                onClick = {
                    surfaceTexture?.let {
                        viewModel.onIntent(RecordIntent.ToggleRecording(it))
                    }
                },
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (state.isRecording) Color.Red else MaterialTheme.colorScheme.primary
                )
            ) {
                Text(if (state.isRecording) "Stop Recording" else "Start Recording")
            }
        }
    }
}
