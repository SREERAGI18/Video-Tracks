package com.example.videoexif

import kotlinx.serialization.Serializable

@Serializable
data class VideoMetadata(
    val startTime: Long,
    val points: List<LocationPoint>
)
