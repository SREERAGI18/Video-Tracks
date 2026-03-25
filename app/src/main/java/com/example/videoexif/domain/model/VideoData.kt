package com.example.videoexif.domain.model

import java.io.File

data class VideoData(
    val videoFile: File,
    val gpxFile: File,
    val srtFile: File? = null
)
