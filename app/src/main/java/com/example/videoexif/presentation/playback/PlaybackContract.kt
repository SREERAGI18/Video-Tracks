package com.example.videoexif.presentation.playback

import com.example.videoexif.domain.model.LocationPoint

sealed class PlaybackIntent {
    data class UpdatePosition(val position: Long) : PlaybackIntent()
}

data class PlaybackState(
    val startTime: Long = 0L,
    val locationPoints: List<LocationPoint> = emptyList(),
    val currentPosition: Long = 0L,
    val currentLocation: LocationPoint? = null
)
