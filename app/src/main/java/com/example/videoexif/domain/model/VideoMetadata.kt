package com.example.videoexif.domain.model

import kotlinx.serialization.Serializable

@Serializable
data class VideoMetadata(
    val startTime: Long,
    val points: List<LocationPoint>
)
