package com.example.videoexif.domain.repository

import android.graphics.SurfaceTexture
import com.example.videoexif.domain.model.LocationPoint
import com.example.videoexif.domain.model.VideoData
import kotlinx.coroutines.flow.Flow
import java.io.File

interface VideoRepository {
    fun getVideos(): Flow<List<VideoData>>
    fun getLocationUpdates(intervalMillis: Long): Flow<LocationPoint>
    suspend fun startRecording(surfaceTexture: SurfaceTexture)
    suspend fun stopRecording(startTime: Long, points: List<LocationPoint>): Boolean
    fun openCamera(onOpened: () -> Unit)
    fun startPreview(surfaceTexture: SurfaceTexture)
    fun closeCamera()
    fun startBackgroundThread()
    fun stopBackgroundThread()
    
    fun setStabilizationEnabled(enabled: Boolean)
    
    suspend fun uploadGpx(file: File): Result<Unit>
    suspend fun uploadSrt(file: File): Result<Unit>
    suspend fun uploadMp4(file: File): Result<Unit>
    suspend fun uploadAll(gpxFile: File, srtFile: File, mp4File: File): Result<Unit>
    
    suspend fun markAsSynced(videoPath: File)
    fun isSynced(videoName: String): Boolean
}
