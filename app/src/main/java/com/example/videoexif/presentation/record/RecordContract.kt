package com.example.videoexif.presentation.record

import android.graphics.SurfaceTexture
import com.example.videoexif.domain.model.LocationPoint

sealed class RecordIntent {
    data class StartPreview(val surfaceTexture: SurfaceTexture) : RecordIntent()
    data class ToggleRecording(val surfaceTexture: SurfaceTexture) : RecordIntent()
    object StopPreview : RecordIntent()
}

data class RecordState(
    val isRecording: Boolean = false,
    val locationPoints: List<LocationPoint> = emptyList(),
    val recordingStartTime: Long = 0L,
    val error: String? = null
)

sealed class RecordEffect {
    data class ShowToast(val message: String) : RecordEffect()
}
