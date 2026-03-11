package com.example.videoexif.data.repository

import android.content.ContentValues
import android.content.Context
import android.graphics.SurfaceTexture
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Environment
import android.os.ParcelFileDescriptor
import android.provider.MediaStore
import android.util.Log
import com.example.videoexif.data.datasource.LocationTracker
import com.example.videoexif.data.datasource.VideoRecorder
import com.example.videoexif.domain.model.LocationPoint
import com.example.videoexif.domain.model.VideoData
import com.example.videoexif.domain.repository.VideoRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import java.io.File

class VideoRepositoryImpl(
    private val context: Context,
    private val videoRecorder: VideoRecorder,
    private val locationTracker: LocationTracker
) : VideoRepository {

    private var currentParcelFd: ParcelFileDescriptor? = null
    private var currentBaseName: String? = null

    override fun getVideos(): Flow<List<VideoData>> = flow {
        val moviesDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MOVIES)
        val appDataDir = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS), "VideoExif")
        
        if (moviesDir?.exists() == true) {
            val videos = moviesDir.listFiles { _, name -> name.endsWith(".mp4") }?.mapNotNull { videoFile ->
                val gpxFile = File(appDataDir, videoFile.nameWithoutExtension + ".gpx")
                if (gpxFile.exists()) VideoData(videoFile, gpxFile) else null
            }?.sortedByDescending { it.videoFile.lastModified() } ?: emptyList()
            emit(videos)
        } else {
            emit(emptyList())
        }
    }

    override fun getLocationUpdates(intervalMillis: Long): Flow<LocationPoint> {
        return locationTracker.getLocationUpdates(intervalMillis).map { location ->
            LocationPoint(
                timestamp = System.currentTimeMillis(),
                latitude = location.latitude,
                longitude = location.longitude
            )
        }
    }

    override suspend fun startRecording(surfaceTexture: SurfaceTexture) {
        val baseName = "video_${System.currentTimeMillis()}"
        currentBaseName = baseName

        val videoUri = createVideoUri() ?: throw Exception("Failed to create MediaStore URI")
        val parcelFd = context.contentResolver.openFileDescriptor(videoUri, "rw") ?: throw Exception("Failed to open file descriptor")
        
        currentParcelFd = parcelFd
        videoRecorder.startRecording(parcelFd.fileDescriptor, surfaceTexture)
    }

    override suspend fun stopRecording(startTime: Long, points: List<LocationPoint>) {
        try {
            videoRecorder.stopRecording()
        } finally {
            currentParcelFd?.close()
            currentParcelFd = null
        }

        currentBaseName?.let { baseName ->
            saveGpxMetadata(baseName, startTime, points)
        }
    }

    override fun openCamera(onOpened: () -> Unit) {
        videoRecorder.openCamera(onOpened)
    }

    override fun startPreview(surfaceTexture: SurfaceTexture) {
        videoRecorder.startPreview(surfaceTexture)
    }

    override fun closeCamera() {
        videoRecorder.closeCamera()
    }

    override fun startBackgroundThread() {
        videoRecorder.startBackgroundThread()
    }

    override fun stopBackgroundThread() {
        videoRecorder.stopBackgroundThread()
    }

    private fun createVideoUri(): Uri? {
        val contentValues = ContentValues().apply {
            put(MediaStore.Video.Media.DISPLAY_NAME, "video_${System.currentTimeMillis()}.mp4")
            put(MediaStore.Video.Media.MIME_TYPE, "video/mp4")
            put(MediaStore.Video.Media.RELATIVE_PATH, Environment.DIRECTORY_MOVIES)
        }

        return context.contentResolver.insert(
            MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
            contentValues
        )
    }

    private fun saveGpxMetadata(baseName: String, startTime: Long, points: List<LocationPoint>) {
        try {
            val documentsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS)
            val appDataDir = File(documentsDir, "VideoExif")
            if (!appDataDir.exists()) {
                appDataDir.mkdirs()
            }
            
            val gpxFile = File(appDataDir, "$baseName.gpx")
            gpxFile.writeText(createGpxContent(startTime, points))
            
            MediaScannerConnection.scanFile(
                context,
                arrayOf(gpxFile.absolutePath),
                arrayOf("application/gpx+xml"),
                null
            )
        } catch (e: Exception) {
            Log.e("VideoRepository", "Error saving GPX metadata", e)
        }
    }

    private fun createGpxContent(startTime: Long, points: List<LocationPoint>): String {
        val dateFormat = java.text.SimpleDateFormat(
            "yyyy-MM-dd'T'HH:mm:ss'Z'",
            java.util.Locale.US
        ).apply { timeZone = java.util.TimeZone.getTimeZone("UTC") }

        return buildString {
            appendLine("<?xml version=\"1.0\" encoding=\"UTF-8\"?>")
            appendLine("<gpx version=\"1.1\" creator=\"VideoExif App\" xmlns=\"http://www.topografix.com/GPX/1/1\" xmlns:video=\"http://videoexif.app/schema/video\">")
            appendLine("<trk><name>Video GPS Track</name><trkseg>")
            points.forEach { point ->
                val time = dateFormat.format(java.util.Date(point.timestamp))
                val offset = point.timestamp - startTime
                appendLine("<trkpt lat=\"${"%.6f".format(java.util.Locale.US, point.latitude)}\" lon=\"${"%.6f".format(java.util.Locale.US, point.longitude)}\">")
                appendLine("<time>$time</time>")
                appendLine("<extensions><video:offset>$offset</video:offset></extensions>")
                appendLine("</trkpt>")
            }
            appendLine("</trkseg></trk></gpx>")
        }
    }
}
