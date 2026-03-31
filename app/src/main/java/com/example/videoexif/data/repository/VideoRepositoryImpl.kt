package com.example.videoexif.data.repository

import android.content.Context
import android.graphics.SurfaceTexture
import android.location.Location
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.util.Log
import com.example.videoexif.data.datasource.LocationTracker
import com.example.videoexif.data.datasource.VideoRecorder
import com.example.videoexif.data.local.VideoDatabase
import com.example.videoexif.data.local.entity.VideoEntity
import com.example.videoexif.data.remote.RetrofitClient
import com.example.videoexif.domain.model.LocationPoint
import com.example.videoexif.domain.model.VideoData
import com.example.videoexif.domain.repository.VideoRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.asRequestBody
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

    private val videoDao = VideoDatabase.getDatabase(context).videoDao()
    private var currentParcelFd: ParcelFileDescriptor? = null
    private var currentBaseName: String? = null
    private val fileNameFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss")
    private val apiService = RetrofitClient.videoApiService

    private val internalVideosDir: File by lazy {
        File(context.filesDir, "videos").apply { if (!exists()) mkdirs() }
    }
    
    private val internalMetadataDir: File by lazy {
        File(context.filesDir, "metadata").apply { if (!exists()) mkdirs() }
    }

    override fun getVideos(): Flow<List<VideoData>> {
        return videoDao.getAllVideos().map { entities ->
            entities.map { entity ->
                VideoData(
                    videoFile = File(entity.videoPath),
                    gpxFile = File(entity.gpxPath),
                    srtFile = entity.srtPath?.let { File(it) },
                    isSynced = entity.isSynced
                )
            }
        }
    }

    override fun getLocationUpdates(intervalMillis: Long): Flow<LocationPoint> {
        return locationTracker.getLocationUpdates(intervalMillis).map { location ->
            LocationPoint(
                timestamp = System.currentTimeMillis(),
                latitude = location.latitude,
                longitude = location.longitude,
                altitude = if (location.hasAltitude()) location.altitude else null,
                speed = if (location.hasSpeed()) location.speed else null
            )
        }
    }

    override suspend fun startRecording(surfaceTexture: SurfaceTexture) {
        val timeStamp = LocalDateTime.now().format(fileNameFormatter)
        val baseName = "video_$timeStamp"
        currentBaseName = baseName

        val videoFile = File(internalVideosDir, "$baseName.mp4")
        
        val parcelFd = ParcelFileDescriptor.open(videoFile, ParcelFileDescriptor.MODE_READ_WRITE or ParcelFileDescriptor.MODE_CREATE)
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
        currentBaseName?.let { baseName ->
            val gpxFile = saveGpxMetadata(baseName, startTime, points)
            val srtFile = saveSrtMetadata(baseName, startTime, points)
            val videoFile = File(internalVideosDir, "$baseName.mp4")

            if (gpxFile != null) {
                videoDao.insertVideo(
                    VideoEntity(
                        videoPath = videoFile.absolutePath,
                        videoName = videoFile.name,
                        gpxPath = gpxFile.absolutePath,
                        srtPath = srtFile?.absolutePath
                    )
                )
            }
        }
        
        currentBaseName = null
        return moved
    }

    override fun setStabilizationEnabled(enabled: Boolean) {
        videoRecorder.setStabilizationEnabled(enabled)
    }

    private fun hasMotion(points: List<LocationPoint>): Boolean {
        if (points.size < 2) return false
        
        val firstPoint = points.first()
        val thresholdMeters = 10.0
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

    override suspend fun uploadGpx(file: File): Result<Unit> {
        return try {
            val requestFile = file.asRequestBody("application/gpx+xml".toMediaTypeOrNull())
            val body = MultipartBody.Part.createFormData("file", file.name, requestFile)
            val response = apiService.uploadGpx(body)
            if (response.isSuccessful) Result.success(Unit)
            else Result.failure(Exception("Upload failed: ${response.code()}"))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun uploadSrt(file: File): Result<Unit> {
        return try {
            val requestFile = file.asRequestBody("text/plain".toMediaTypeOrNull())
            val body = MultipartBody.Part.createFormData("file", file.name, requestFile)
            val response = apiService.uploadSrt(body)
            if (response.isSuccessful) Result.success(Unit)
            else Result.failure(Exception("Upload failed: ${response.code()}"))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun uploadMp4(file: File): Result<Unit> {
        return try {
            val requestFile = file.asRequestBody("video/mp4".toMediaTypeOrNull())
            val body = MultipartBody.Part.createFormData("file", file.name, requestFile)
            val response = apiService.uploadMp4(body)
            if (response.isSuccessful) Result.success(Unit)
            else Result.failure(Exception("Upload failed: ${response.code()}"))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun uploadAll(gpxFile: File, srtFile: File, mp4File: File): Result<Unit> {
        return try {
            val gpxRequest = gpxFile.asRequestBody("application/gpx+xml".toMediaTypeOrNull())
            val gpxPart = MultipartBody.Part.createFormData("gpx_file", gpxFile.name, gpxRequest)
            
            val srtRequest = srtFile.asRequestBody("text/plain".toMediaTypeOrNull())
            val srtPart = MultipartBody.Part.createFormData("srt_file", srtFile.name, srtRequest)
            
            val mp4Request = mp4File.asRequestBody("video/mp4".toMediaTypeOrNull())
            val mp4Part = MultipartBody.Part.createFormData("mp4_file", mp4File.name, mp4Request)
            
            val response = apiService.uploadAll(gpxPart, srtPart, mp4Part)
            if (response.isSuccessful) {
                markAsSynced(mp4File)
                Result.success(Unit)
            }
            else Result.failure(Exception("Upload failed: ${response.code()}"))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun markAsSynced(videoPath: File) {
        videoDao.updateSyncStatus(videoPath = videoPath.absolutePath, isSynced = true)
    }

    override fun isSynced(videoName: String): Boolean {
        return false
    }

    private fun saveGpxMetadata(baseName: String, startTime: Long, points: List<LocationPoint>): File? {
        return try {
            val gpxFile = File(internalMetadataDir, "$baseName.gpx")
            gpxFile.writeText(createGpxContent(startTime, points))
            gpxFile
        } catch (e: Exception) {
            Log.e("VideoRepository", "Error saving GPX metadata", e)
            null
        }
    }

    private fun saveSrtMetadata(baseName: String, startTime: Long, points: List<LocationPoint>): File? {
        return try {
            val srtFile = File(internalMetadataDir, "$baseName.srt")
            srtFile.writeText(createSrtContent(startTime, points))
            srtFile
        } catch (e: Exception) {
            Log.e("VideoRepository", "Error saving SRT metadata", e)
            null
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
                append("<trkpt lat=\"${"%.6f".format(Locale.US, point.latitude)}\" lon=\"${"%.6f".format(Locale.US, point.longitude)}\">")
                if (point.altitude != null) {
                    append("<ele>${"%.2f".format(Locale.US, point.altitude)}</ele>")
                }
                appendLine("<time>$time</time>")
                appendLine("<extensions>")
                appendLine("<video:offset>$offset</video:offset>")
                if (point.speed != null) {
                    appendLine("<video:speed>${"%.2f".format(Locale.US, point.speed)}</video:speed>")
                }
                appendLine("</extensions>")
                appendLine("</trkpt>")
            }
            appendLine("</trkseg></trk></gpx>")
        }
    }

    private fun createSrtContent(startTime: Long, points: List<LocationPoint>): String {
        return buildString {
            points.forEachIndexed { index, point ->
                val startOffset = point.timestamp - startTime
                val endOffset = if (index < points.size - 1) {
                    points[index + 1].timestamp - startTime
                } else {
                    startOffset + 1000
                }

                appendLine("${index + 1}")
                appendLine("${formatSrtTime(startOffset)} --> ${formatSrtTime(endOffset)}")
                append("Lat: ${"%.6f".format(Locale.US, point.latitude)}, Lon: ${"%.6f".format(Locale.US, point.longitude)}")
                if (point.altitude != null) {
                    append(", Alt: ${"%.1f".format(Locale.US, point.altitude)}m")
                }
                if (point.speed != null) {
                    val speedKmh = point.speed * 3.6f
                    append(", Speed: ${"%.1f".format(Locale.US, speedKmh)} km/h")
                }
                appendLine()
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
