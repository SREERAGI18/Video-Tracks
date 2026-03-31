package com.example.videoexif.presentation.record

import com.example.videoexif.domain.model.LocationPoint

data class RecordState(
    val isRecording: Boolean = false,
    val recordingStartTime: Long = 0,
    val locationPoints: List<LocationPoint> = emptyList(),
    val error: String? = null,
    val isStabilizationEnabled: Boolean = true
)
