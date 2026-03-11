package com.example.videoexif.domain.model

import kotlinx.serialization.Serializable

@Serializable
data class LocationPoint(
    val timestamp: Long,
    val latitude: Double,
    val longitude: Double,
    val altitude: Double? = null
)
