package com.example.videoexif

import kotlinx.serialization.Serializable

@Serializable
data class LocationPoint(
    val timestamp: Long,
    val latitude: Double,
    val longitude: Double,
    val altitude: Double? = null
)
