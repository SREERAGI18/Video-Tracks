package com.example.videoexif.domain.repository

import android.graphics.SurfaceTexture
import com.example.videoexif.domain.model.LocationPoint
import com.example.videoexif.domain.model.VideoData
import kotlinx.coroutines.flow.Flow

interface VideoRepository {
    fun getVideos(): Flow<List<VideoData>>
    fun getLocationUpdates(intervalMillis: Long): Flow<LocationPoint>
    suspend fun startRecording(surfaceTexture: SurfaceTexture)
    suspend fun stopRecording(startTime: Long, points: List<LocationPoint>)
    fun openCamera(onOpened: () -> Unit)
    fun startPreview(surfaceTexture: SurfaceTexture)
    fun closeCamera()
    fun startBackgroundThread()
    fun stopBackgroundThread()
}
