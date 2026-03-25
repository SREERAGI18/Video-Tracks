package com.example.videoexif.data.repository

import android.content.ContentValues
import android.content.Context
import android.graphics.SurfaceTexture
import android.location.Location
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
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.util.concurrent.TimeUnit

class VideoRepositoryImpl(
    private val context: Context,
    private val videoRecorder: VideoRecorder,
    private val locationTracker: LocationTracker
) : VideoRepository {

    private var currentParcelFd: ParcelFileDescriptor? = null
    private var currentBaseName: String? = null
    private var currentVideoUri: Uri? = null
    private val fileNameFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd_HH:mm:ss")

    override fun getVideos(): Flow<List<VideoData>> = flow {
        val moviesDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MOVIES)
        val appDataDir = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS), "VideoExif")
        
        if (moviesDir?.exists() == true) {
            val videos = moviesDir.listFiles { _, name -> name.endsWith(".mp4") }?.mapNotNull { videoFile ->
                val gpxFile = File(appDataDir, videoFile.nameWithoutExtension + ".gpx")
                val srtFile = File(appDataDir, videoFile.nameWithoutExtension + ".srt")
                if (gpxFile.exists()) {
                    VideoData(
                        videoFile = videoFile,
                        gpxFile = gpxFile,
                        srtFile = if (srtFile.exists()) srtFile else null
                    )
                } else null
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
        val timeStamp = LocalDateTime.now().format(fileNameFormatter)
        val baseName = "video_$timeStamp"
        currentBaseName = baseName

        val videoUri = createVideoUri(baseName) ?: throw Exception("Failed to create MediaStore URI")
        currentVideoUri = videoUri
        
        val parcelFd = context.contentResolver.openFileDescriptor(videoUri, "rw") ?: throw Exception("Failed to open file descriptor")
        
        currentParcelFd = parcelFd
        videoRecorder.startRecording(parcelFd.fileDescriptor, surfaceTexture)
    }

    override suspend fun stopRecording(startTime: Long, points: List<LocationPoint>): Boolean {
        try {
            videoRecorder.stopRecording()
        } finally {
            currentParcelFd?.close()
            currentParcelFd = null
        }

        val moved = hasMotion(points)
        if (moved) {
            currentBaseName?.let { baseName ->
                saveGpxMetadata(baseName, startTime, points)
                saveSrtMetadata(baseName, startTime, points)
            }
        } else {
            // No motion detected, discard the video and don't save GPX/SRT
            currentVideoUri?.let { uri ->
                try {
                    context.contentResolver.delete(uri, null, null)
                    Log.d("VideoRepository", "No motion detected. Video discarded.")
                } catch (e: Exception) {
                    Log.e("VideoRepository", "Error deleting video after no motion", e)
                }
            }
        }
        
        val result = moved
        currentBaseName = null
        currentVideoUri = null
        return result
    }

    private fun hasMotion(points: List<LocationPoint>): Boolean {
        if (points.size < 2) return false
        
        val firstPoint = points.first()
        val thresholdMeters = 10.0 // Motion threshold
        val results = FloatArray(1)
        
        return points.any { point ->
            Location.distanceBetween(
                firstPoint.latitude, firstPoint.longitude,
                point.latitude, point.longitude,
                results
            )
            results[0] > thresholdMeters
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

    private fun createVideoUri(baseName: String): Uri? {
        val contentValues = ContentValues().apply {
            put(MediaStore.Video.Media.DISPLAY_NAME, "$baseName.mp4")
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

    private fun saveSrtMetadata(baseName: String, startTime: Long, points: List<LocationPoint>) {
        try {
            val documentsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS)
            val appDataDir = File(documentsDir, "VideoExif")
            if (!appDataDir.exists()) {
                appDataDir.mkdirs()
            }
            
            val srtFile = File(appDataDir, "$baseName.srt")
            srtFile.writeText(createSrtContent(startTime, points))
            
            MediaScannerConnection.scanFile(
                context,
                arrayOf(srtFile.absolutePath),
                arrayOf("text/plain"),
                null
            )
        } catch (e: Exception) {
            Log.e("VideoRepository", "Error saving SRT metadata", e)
        }
    }

    private fun createGpxContent(startTime: Long, points: List<LocationPoint>): String {
        val dateFormat = java.text.SimpleDateFormat(
            "yyyy-MM-dd'T'HH:mm:ss'Z'",
            Locale.US
        ).apply { timeZone = java.util.TimeZone.getTimeZone("UTC") }

        return buildString {
            appendLine("<?xml version=\"1.0\" encoding=\"UTF-8\"?>")
            appendLine("<gpx version=\"1.1\" creator=\"VideoExif App\" xmlns=\"http://www.topografix.com/GPX/1/1\" xmlns:video=\"http://videoexif.app/schema/video\">")
            appendLine("<trk><name>Video GPS Track</name><trkseg>")
            points.forEach { point ->
                val time = dateFormat.format(java.util.Date(point.timestamp))
                val offset = point.timestamp - startTime
                appendLine("<trkpt lat=\"${"%.6f".format(Locale.US, point.latitude)}\" lon=\"${"%.6f".format(Locale.US, point.longitude)}\">")
                appendLine("<time>$time</time>")
                appendLine("<extensions><video:offset>$offset</video:offset></extensions>")
                appendLine("</trkpt>")
            }
            appendLine("</trkseg></trk></gpx>")
        }
    }

    private fun createSrtContent(startTime: Long, points: List<LocationPoint>): String {
        return buildString {
            points.forEachIndexed { index, point ->
                val startOffset = point.timestamp - startTime
                // If there's a next point, the duration is the gap. Otherwise, default to 1s.
                val endOffset = if (index < points.size - 1) {
                    points[index + 1].timestamp - startTime
                } else {
                    startOffset + 1000
                }

                appendLine("${index + 1}")
                appendLine("${formatSrtTime(startOffset)} --> ${formatSrtTime(endOffset)}")
                appendLine("Lat: ${"%.6f".format(Locale.US, point.latitude)}, Lon: ${"%.6f".format(Locale.US, point.longitude)}")
                appendLine()
            }
        }
    }

    private fun formatSrtTime(millis: Long): String {
        val hours = TimeUnit.MILLISECONDS.toHours(millis)
        val minutes = TimeUnit.MILLISECONDS.toMinutes(millis) % 60
        val seconds = TimeUnit.MILLISECONDS.toSeconds(millis) % 60
        val ms = millis % 1000
        return "%02d:%02d:%02d,%03d".format(Locale.US, hours, minutes, seconds, ms)
    }
}
